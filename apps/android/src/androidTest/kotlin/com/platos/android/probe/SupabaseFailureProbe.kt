package com.platos.android.probe

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.android.auth.AutenticacaoSupabase
import com.platos.android.auth.ResultadoDaAutenticacao
import com.platos.android.net.clienteHttp
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

/**
 * PROBE — nao e teste: nao afirma nada, so exercita e relata.
 *
 * Nasceu na tarefa 1.1 para medir o que `supabase-kt` reportava. A medicao esta na decisao 8 do
 * `design.md`, e foi a decisao 9 que dispensou a biblioteca — entao o braco que a exercitava
 * saiu junto com ela. O que sobrou e o braco que a decisao manteve — e ele agora roda o **adaptador
 * de producao**, e nao uma copia dele.
 *
 * Continua sendo teste instrumentado de proposito. `AutenticacaoSupabaseTest` cobre a classificacao
 * na JVM com `MockEngine`, e isso e o que se pode afirmar sem aparelho; o que **nao** se pode e que
 * o engine OkHttp de verdade, contra o servidor de verdade, produza os mesmos tipos. Modo aviao
 * tambem so existe no aparelho. E essa diferenca que justifica este arquivo continuar existindo:
 * ele e o instrumento da secao 6, e nao verificacao automatica.
 */
class SupabaseFailureProbe {

    private val args = InstrumentationRegistry.getArguments()

    /** `real` (padrao) ou `morto`. */
    private val alvo: String = args.getString("alvo") ?: "real"
    private val urlReal: String = args.getString("supabaseUrl").orEmpty()
    private val anonKey: String = args.getString("anonKey").orEmpty()
    private val emailProbe: String = args.getString("email") ?: "probe@example.invalid"

    /**
     * Porta morta em **https**, e nao em http, de proposito.
     *
     * Com `targetSdk 35` o Android recusa trafego em claro antes de abrir soquete, e a excecao seria
     * `UnknownServiceException: CLEARTEXT ... not permitted` — politica de rede do sistema disfarcada
     * de falha de transporte, que e exatamente o tipo de medicao falsa que nao serve. Em https o TCP
     * e recusado antes de qualquer TLS, e o que chega e transporte puro.
     */
    private val urlMorta = "https://127.0.0.1:1"

    private val url: String get() = if (alvo == "morto") urlMorta else urlReal

    private val senhaErrada = "senha-propositalmente-errada-probe"

    /** Montado a mao no diagnostico: ele nao usa o DTO, para nao depender do que esta em teste. */
    private val corpoDeEntrada: String
        get() = "{\"email\":\"" + emailProbe + "\",\"password\":\"" + senhaErrada + "\"}"

    @Test
    fun relatarFalha() {
        check(alvo == "morto" || urlReal.isNotBlank()) {
            "falta `probe.supabaseUrl` em local.properties (ou o arg supabaseUrl)"
        }

        val relato = buildString {
            appendLine("alvo=" + alvo)
            appendLine("url=" + url)
            appendLine("email=" + emailProbe)
            appendLine("engine=okhttp  ktor=o do catalogo, o mesmo do servidor")
            appendLine()
            appendLine("--- AutenticacaoSupabase.entrar(), adaptador de producao ---")
            append(exercitar())
        }

        emitir(relato)
    }

    /**
     * O relato registra o **tipo** do resultado, e nao texto de mensagem.
     *
     * Nenhum token e impresso, nem parte dele: o relato vai para o `logcat` e para um arquivo no
     * aparelho, e credencial nao entra em nenhum dos dois. Da rodada que autentica o que interessa
     * e que ela autenticou, e que os campos que o DTO exige vieram.
     */
    private fun exercitar(): String = runBlocking {
        val http = clienteHttp()
        try {
            val auth = AutenticacaoSupabase(http, url, anonKey)
            when (val r = auth.entrar(emailProbe, senhaErrada)) {
                is ResultadoDaAutenticacao.Autenticado -> buildString {
                    appendLine("resultado: Autenticado")
                    appendLine("So esperado se a credencial estiver certa — o probe manda senha errada.")
                    appendLine("access_token: <presente, " + r.credencial.accessToken.length + " chars>")
                    appendLine("refresh_token presente: " + r.credencial.refreshToken.isNotEmpty())
                    appendLine("expires_in: " + r.credencial.expiresIn)
                    appendLine("token_type: " + r.credencial.tokenType)
                }
                is ResultadoDaAutenticacao.CredencialRecusada ->
                    "resultado: CredencialRecusada\n"
                is ResultadoDaAutenticacao.SemRede -> buildString {
                    appendLine("resultado: SemRede")
                    // O adaptador classificou e descartou a excecao, que e o que ele deve fazer.
                    // Mas `SemRede` contra um servidor que responde e sintoma, e nao resultado,
                    // entao o probe repete a mesma chamada crua so para relatar **qual**
                    // `IOException` foi. Diagnostico, e nao classificacao: nada aqui alimenta
                    // decisao nenhuma do aplicativo.
                    appendLine("--- repetido cru, so para dizer qual foi ---")
                    append(diagnosticar())
                }
            }
        } catch (t: Throwable) {
            // Nada e classificado aqui. Se chegou excecao, o adaptador **nao** a tratou, e isso e o
            // achado — inclusive o `JsonConvertException` que o corpo de sucesso pode produzir,
            // porque ele nunca foi medido contra o servidor de verdade.
            buildString {
                appendLine("EXCECAO ESCAPOU DO ADAPTADOR — e o achado, nao o resultado.")
                var atual: Throwable? = t
                var nivel = 0
                val vistos = mutableSetOf<Throwable>()
                while (atual != null && vistos.add(atual)) {
                    val r = if (nivel == 0) "excecao" else "causa[" + nivel + "]"
                    appendLine(r + ".classe: " + atual.javaClass.name)
                    appendLine(r + ".mensagem: " + atual.message)
                    atual = atual.cause
                    nivel++
                }
            }
        } finally {
            http.close()
        }
    }

    /**
     * Repete a chamada sem adaptador e descreve a cadeia de excecoes, sem interpretar nada.
     *
     * Existe porque `SemRede` e um resultado que nao diz por que. Quando ele aparece contra um
     * servidor que devia responder, a pergunta seguinte e sempre "qual excecao?", e sem isto a
     * resposta exige recompilar o probe.
     */
    private suspend fun diagnosticar(): String {
        val cru = HttpClient(OkHttp)
        return try {
            val resposta = cru.post(url + "/auth/v1/token?grant_type=password") {
                header("apikey", anonKey)
                contentType(ContentType.Application.Json)
                setBody(corpoDeEntrada)
            }
            "sem excecao — status " + resposta.status.value +
                ", corpo: " + resposta.bodyAsText() + "\n"
        } catch (t: Throwable) {
            buildString {
                var atual: Throwable? = t
                var nivel = 0
                val vistos = mutableSetOf<Throwable>()
                while (atual != null && vistos.add(atual)) {
                    val r = if (nivel == 0) "excecao" else "causa[" + nivel + "]"
                    appendLine(r + ".classe: " + atual.javaClass.name)
                    appendLine(r + ".mensagem: " + atual.message)
                    atual = atual.cause
                    nivel++
                }
            }
        } finally {
            cru.close()
        }
    }

    private fun emitir(texto: String) {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        val destino = File(contexto.getExternalFilesDir(null), "probe-" + alvo + ".txt")
        destino.writeText(texto)
        // Linha a linha: `Log` trunca em ~4 KB por entrada, e o arquivo e a copia autoritativa.
        texto.lineSequence().forEach { Log.e(TAG, it) }
        Log.e(TAG, "relatorio completo em: " + destino.absolutePath)
    }

    private companion object {
        const val TAG = "PLATOS_PROBE"
    }
}
