package com.platos.android.session

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Keyset corrompido nao derruba o aplicativo: descarta uma vez, e tenta de novo.
 *
 * **Este e o unico modo de falha conhecido da escolha da decisao 5**, e desde que a biblioteca esta
 * depreciada ele e responsabilidade desta base — biblioteca depreciada nao recebe correcao. Um
 * arquivo cifrado ilegivel e sessao invalida, e nao aplicativo que nao abre: pedir a senha outra vez
 * e pior do que nao pedir, e muito melhor do que nao abrir.
 *
 * A funcao nao conhece Android de proposito. E o que permite `SessaoGuardadaTest` forcar a corrupcao
 * na JVM, sem emulador — a alternativa seria a decisao viver dentro do adaptador, onde so um teste
 * instrumentado a alcanca, e onde ela ficaria sem cobertura ate alguem ligar um aparelho.
 *
 * **So estas duas excecoes sao tratadas.** `GeneralSecurityException` cobre keyset e cifragem —
 * `AEADBadTagException` desce dela —, e `IOException` cobre o arquivo. Qualquer outra sobe, e sobe
 * de proposito: engolir o desconhecido aqui transformaria defeito de programacao em "sessao
 * invalida", que e a forma de falha silenciosa desta camada. E o mesmo criterio de `retornoDe`.
 */
internal fun <T> abrindoOuDescartando(abrir: () -> T, descartar: () -> Unit): T? =
    try {
        abrir()
    } catch (corrompido: GeneralSecurityException) {
        descartar()
        segundaTentativa(abrir)
    } catch (corrompido: IOException) {
        descartar()
        segundaTentativa(abrir)
    }

private fun <T> segundaTentativa(abrir: () -> T): T? =
    try {
        abrir()
    } catch (aindaCorrompido: GeneralSecurityException) {
        null
    } catch (aindaCorrompido: IOException) {
        null
    }

/**
 * Uma leitura do armazenamento cifrado, onde falhar significa "nao ha sessao".
 *
 * Separada de [abrindoOuDescartando] porque a corrupcao aparece nos dois momentos e eles pedem
 * respostas diferentes: abrir tem conserto — descartar e recriar —, e ler nao tem. Valor que nao
 * decifra e valor perdido, e a resposta certa e a mesma de nao haver valor nenhum.
 *
 * `SecurityException` entra na lista porque `EncryptedSharedPreferences` embrulha falha de
 * decifragem nela, e ela **nao** desce de `GeneralSecurityException`.
 */
internal fun <T> lendoOuSessaoInvalida(ler: () -> T?): T? =
    try {
        ler()
    } catch (ilegivel: GeneralSecurityException) {
        null
    } catch (ilegivel: SecurityException) {
        null
    } catch (ilegivel: IOException) {
        null
    }

/**
 * O que sobrevive ao fechamento do aplicativo, gravado no aparelho.
 *
 * **Dois armazenamentos, e nao um.** A credencial vai cifrada, com chave no Android Keystore; a
 * organizacao escolhida vai no armazenamento privado comum. A decisao 5 registra por que eles nao
 * recebem o mesmo tratamento: o sandbox do Android impede que **outro aplicativo** leia o
 * armazenamento privado, e isso e tudo o que ele impede. Num aparelho compartilhado entre escolas —
 * o mesmo modelo de ameaca da tarefa 3.7 — o que sobra e acesso fisico, e ai um token em claro e
 * credencial de rede reutilizavel, enquanto o identificador de uma organizacao e preferencia.
 *
 * Separar tambem torna a afirmacao verificavel: o cenario "a credencial nao esta em claro" le o
 * arquivo cifrado e o comum, e so um dos dois pode conter o token. Com um armazenamento so, "esta
 * cifrado" seria afirmacao sobre o arquivo inteiro, e a organizacao escolhida — que nao precisa de
 * cifragem — passaria a esconder o que o teste procura.
 *
 * **Nada aqui decide.** [DeviceSession] continua sendo quem decide, em Kotlin puro; esta classe e o
 * adaptador que a fatia 3a desenhou e a 3c repetiu.
 */
class SessaoGuardadaAndroid(private val context: Context) : SessaoGuardada {

    /**
     * A API esta depreciada, e a **revisao de 2026-09-03 da decisao 5** registra por que ela fica.
     * Em resumo: a depreciacao e a AndroidX preferindo Keystore direto a esta wrapper, e nao falha
     * de seguranca — entao ela propoe justamente a alternativa que a decisao 5 ja havia descartado,
     * e pelo mesmo motivo. O `@Suppress` fica num ponto so, e nao espalhado por chamada.
     *
     * `null` quando nem descartar e recriar resolveu. O aplicativo segue sem sessao guardada, que e
     * a entrada — e nao uma tela que nao abre.
     */
    @Suppress("DEPRECATION")
    private val cifrado: SharedPreferences? = abrindoOuDescartando(
        abrir = {
            EncryptedSharedPreferences.create(
                context,
                ARQUIVO_CIFRADO,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        },
        descartar = { context.deleteSharedPreferences(ARQUIVO_CIFRADO) },
    )

    private val comum: SharedPreferences =
        context.getSharedPreferences(ARQUIVO_COMUM, Context.MODE_PRIVATE)

    // --- A credencial, cifrada ---

    /**
     * O token da sessao, ou `null` quando nao ha sessao guardada — ou quando o que havia nao decifra.
     *
     * Lida a cada chamada, e nao guardada num campo: sair apaga o token, e um valor em memoria
     * continuaria autenticando chamadas depois de o professor ter saido.
     */
    fun credencial(): String? =
        lendoOuSessaoInvalida { cifrado?.getString(CHAVE_CREDENCIAL, null) }

    fun guardarCredencial(token: String) {
        lendoOuSessaoInvalida { cifrado?.edit()?.putString(CHAVE_CREDENCIAL, token)?.apply() }
    }

    override fun apagarCredencial() {
        lendoOuSessaoInvalida { cifrado?.edit()?.remove(CHAVE_CREDENCIAL)?.apply() }
    }

    // --- A organizacao escolhida, em claro e de proposito ---

    override fun organizacaoEscolhida(): String? = comum.getString(CHAVE_ORGANIZACAO, null)

    override fun guardarOrganizacaoEscolhida(id: String) {
        comum.edit().putString(CHAVE_ORGANIZACAO, id).apply()
    }

    override fun apagarOrganizacaoEscolhida() {
        comum.edit().remove(CHAVE_ORGANIZACAO).apply()
    }

    companion object {
        /** Publicos porque o teste instrumentado da 4.6 precisa ler os arquivos pelo nome. */
        const val ARQUIVO_CIFRADO = "platos-sessao-cifrada"
        const val ARQUIVO_COMUM = "platos-sessao"
        const val CHAVE_CREDENCIAL = "credencial"
        const val CHAVE_ORGANIZACAO = "organizacao-escolhida"
    }
}
