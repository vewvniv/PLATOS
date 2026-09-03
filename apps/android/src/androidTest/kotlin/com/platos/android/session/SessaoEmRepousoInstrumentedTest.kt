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
 * chegou ao disco. Entao o teste grava **duas** coisas: a credencial, que deve sumir, e o
 * identificador da organizacao, que e preferencia e fica em claro de proposito. Achar o segundo e o
 * que prova que a busca alcanca os arquivos e sabe ler o que ha neles; so depois disso nao achar o
 * primeiro significa alguma coisa.
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
        guardada.guardarCredencial(token)
        guardada.guardarOrganizacaoEscolhida(organizacao)

        // `apply()` grava fora da linha de execucao. Esperar pelo identificador em claro e o que
        // sincroniza o teste com o disco — e ja e a primeira metade da prova.
        val gravado = esperarPorTextoGravado(organizacao)
        assertTrue(
            "a busca nao achou nem o identificador da organizacao, que esta em claro: " +
                "ela nao alcanca o que foi gravado, e nao achar o token nao provaria nada",
            gravado,
        )

        val cifrado = File(context.dataDir, "shared_prefs/${SessaoGuardadaAndroid.ARQUIVO_CIFRADO}.xml")
        assertTrue("o arquivo cifrado nem existe", cifrado.exists())
        assertTrue("o arquivo cifrado esta vazio", cifrado.length() > 0)

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

    private fun esperarPorTextoGravado(procurado: String): Boolean {
        repeat(50) {
            if (algumArquivoContem(procurado)) return true
            Thread.sleep(100)
        }
        return false
    }
}
