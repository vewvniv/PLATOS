package com.platos.android.session

import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Base64
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Os dois cenarios de credencial em repouso da spec, no aparelho.
 *
 * Eles nao rodam na JVM: `EncryptedSharedPreferences` exige o Keystore, e "esta cifrado" so se
 * afirma lendo o que foi gravado. O tratamento de keyset corrompido, que e decisao, fica em
 * `SessaoGuardadaTest`, na JVM — aqui esta o que so o aparelho responde.
 *
 * **O cuidado que faz este teste valer alguma coisa.** Procurar um token e nao achar passa tambem
 * quando a busca olha no lugar errado, quando nada foi gravado, ou quando o arquivo ainda nao
 * chegou ao disco. Sao tres causas diferentes para o mesmo verde, e cada uma tem sua guarda:
 *
 * - **olhar no lugar errado** — o teste grava tambem o identificador da organizacao, que e
 *   preferencia e fica em claro de proposito, e **exige acha-lo**. A busca que nao acha o que esta
 *   em claro nao pode ser usada para afirmar que o token nao esta;
 * - **nada gravado, ou ainda no ar** — o arquivo cifrado e fotografado antes de a credencial ser
 *   escrita, e o teste espera ate os bytes mudarem. `apply()` grava fora da linha de execucao, e
 *   essa e a unica forma de observar a escrita **da credencial** chegar ao disco.
 *
 * A segunda guarda substitui uma suposicao que a primeira versao deste teste fazia sem dizer. Ela
 * esperava so pelo identificador da organizacao, e funcionava porque o Android serializa toda
 * escrita de `apply()` numa fila FIFO unica (`QueuedWork`) e a credencial era enfileirada primeiro
 * — entao o canario aparecer implicava a outra escrita ter terminado. Detalhe de implementacao, nao
 * declarado, que **trocar duas linhas de lugar destruiria em silencio**: a afirmacao sobre o token
 * viraria passe vazio, sem sintoma nenhum. Agora a espera e sobre o arquivo que interessa, e a
 * ordem das escritas deixou de importar.
 */
@RunWith(AndroidJUnit4::class)
class SessaoEmRepousoInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun estadoZerado() {
        context.deleteSharedPreferences(SessaoGuardadaAndroid.ARQUIVO_CIFRADO)
        context.deleteSharedPreferences(SessaoGuardadaAndroid.ARQUIVO_COMUM)
    }

    @Test
    fun aCredencialNaoEstaEmClaro() {
        // Sorteados: um valor fixo poderia coincidir com qualquer coisa ja presente no diretorio, e
        // o teste passaria a afirmar sobre a coincidencia em vez de sobre o que foi gravado.
        val token = "tok-${UUID.randomUUID()}"
        val organizacao = "org-${UUID.randomUUID()}"

        val guardada = SessaoGuardadaAndroid(context)

        // A construcao ja grava o keyset do Tink neste arquivo. Esperar por ele antes de fotografar
        // e o que garante que a mudanca observada depois seja a credencial, e nao o keyset chegando
        // atrasado.
        //
        // **Espera sem afirmar, de proposito.** Guardar a sessao em claro e um defeito que este
        // teste precisa pegar, e nesse caso o arquivo so nasce na primeira escrita — nao existe
        // nada para esperar aqui. Afirmar a existencia neste ponto faria o defeito ser acusado pela
        // guarda de preparo em vez da afirmacao de seguranca, apontando para o lugar errado.
        val cifrado = File(context.dataDir, "shared_prefs/${SessaoGuardadaAndroid.ARQUIVO_CIFRADO}.xml")
        esperarAte { cifrado.exists() && cifrado.length() > 0 }
        val antesDaCredencial = if (cifrado.exists()) cifrado.readBytes() else ByteArray(0)

        guardada.guardarCredencial(token)
        guardada.guardarOrganizacaoEscolhida(organizacao)

        // Guarda 1: a escrita da credencial chegou ao disco. Depois da construcao, quem escreve
        // neste arquivo e so `guardarCredencial` — entao os bytes mudarem e a propria escrita.
        assertTrue(
            "o arquivo cifrado nao mudou depois de guardar a credencial: a escrita nao chegou ao " +
                "disco, e procurar o token agora nao provaria nada",
            esperarAte {
                cifrado.exists() && !cifrado.readBytes().contentEquals(antesDaCredencial)
            },
        )

        // Guarda 2: a busca alcanca o que foi gravado, e sabe ler o que ha nele.
        assertTrue(
            "a busca nao achou nem o identificador da organizacao, que esta em claro: " +
                "ela nao alcanca o que foi gravado, e nao achar o token nao provaria nada",
            esperarAte { algumArquivoContem(organizacao) },
        )

        // Agora a afirmacao vale: o mesmo instrumento que achou um nao acha o outro.
        assertFalse(
            "o token aparece como texto legivel no armazenamento do aplicativo",
            algumArquivoContem(token),
        )

        // Base64 nao e cifragem. Sem esta linha, guardar o token codificado passaria pela busca
        // acima, e o defeito seria indistinguivel de cifragem para quem le o resultado do teste.
        val comoBase64 = Base64.getEncoder().encodeToString(token.toByteArray()).trimEnd('=')
        assertFalse(
            "o token aparece apenas codificado em Base64, que nao protege nada",
            algumArquivoContem(comoBase64),
        )
    }

    @Test
    fun nemCredencialNemOrganizacaoSaemNoBackup() {
        // O backup e feito pelo sistema, do lado de dentro do sandbox, e levaria a credencial ja
        // decifrada. Desligado no aplicativo inteiro (decisao 4), entao a afirmacao e sobre a
        // flag — que e o que o sistema consulta — e nao sobre uma lista de arquivos.
        assertEquals(
            "FLAG_ALLOW_BACKUP esta ligada: o backup automatico levaria os dois arquivos",
            0,
            context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP,
        )
    }

    // --- O instrumento ---

    /** Todo arquivo sob o diretorio de dados do aplicativo, e nao so `shared_prefs`. */
    private fun arquivosGravados(): List<File> =
        context.dataDir.walkTopDown().filter { it.isFile }.toList()

    /**
     * Le byte a byte, em ISO-8859-1.
     *
     * Nao e detalhe: em UTF-8 um byte invalido vira `U+FFFD` e o texto ao redor se desloca, entao
     * um token colado a dado binario poderia deixar de casar. Em ISO-8859-1 todo byte vira um
     * caractere, e a busca ve exatamente o que esta no disco.
     */
    private fun algumArquivoContem(procurado: String): Boolean =
        arquivosGravados().any { arquivo ->
            runCatching { arquivo.readBytes().toString(Charsets.ISO_8859_1).contains(procurado) }
                .getOrDefault(false)
        }

    /** Ate cinco segundos. Nao e folga: e o tempo de uma escrita de `apply()` que se atrasou. */
    private fun esperarAte(condicao: () -> Boolean): Boolean {
        repeat(50) {
            if (condicao()) return true
            Thread.sleep(100)
        }
        return false
    }
}
