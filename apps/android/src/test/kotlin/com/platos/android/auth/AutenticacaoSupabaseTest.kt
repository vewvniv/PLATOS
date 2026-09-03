package com.platos.android.auth

import com.platos.android.net.clienteHttp
import com.platos.android.session.ResultadoDaEntrada
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.JsonConvertException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException

/**
 * O adaptador de entrada, sem rede e sem aparelho.
 *
 * `MockEngine` responde no lugar do servidor, e por isso o que entra aqui e resposta **de verdade**
 * — status, cabecalhos e corpo —, e nao um resultado ja destilado. E o oposto de
 * `DeviceSessionTest`, de proposito: la se verifica a decisao, aqui se verifica a traducao. O corpo
 * de erro usado e copiado verbatim do que o probe da tarefa 1.1 recebeu do projeto real.
 */
class AutenticacaoSupabaseTest {

    /** Verbatim do relato do probe, rodada `alvo=real`, 2026-09-02. */
    private val corpoDeCredencialRecusada =
        """{"code":400,"error_code":"invalid_credentials","msg":"Invalid login credentials"}"""

    private val corpoDeSucesso =
        """{"access_token":"tok-abc","token_type":"bearer","expires_in":3600,""" +
            """"refresh_token":"ref-xyz"}"""

    private sealed interface Resposta {
        data class Corpo(val status: Int, val corpo: String) : Resposta
        data class Estoura(val erro: IOException) : Resposta
    }

    private fun autenticacao(responder: (HttpRequestData) -> Resposta): AutenticacaoSupabase {
        val engine = MockEngine { pedido ->
            when (val r = responder(pedido)) {
                is Resposta.Corpo -> respond(
                    content = r.corpo,
                    status = HttpStatusCode.fromValue(r.status),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                is Resposta.Estoura -> throw r.erro
            }
        }
        return AutenticacaoSupabase(
            http = clienteHttp(engine),
            urlBase = "https://projeto.supabase.co",
            chaveAnonima = "chave-de-teste",
        )
    }

    private fun comStatus(status: Int, corpo: String = "{}") =
        autenticacao { Resposta.Corpo(status, corpo) }

    private fun quebrando(erro: IOException) = autenticacao { Resposta.Estoura(erro) }

    // --- Os tres resultados (decisao 8) ---

    @Test
    fun o400QueOProbeMediuECredencialRecusada() = runBlocking {
        assertEquals(
            ResultadoDaAutenticacao.CredencialRecusada,
            comStatus(400, corpoDeCredencialRecusada).entrar("a@b.c", "errada"),
        )
    }

    @Test
    fun umQuatroZeroUmTambemECredencialRecusada() = runBlocking {
        assertEquals(
            ResultadoDaAutenticacao.CredencialRecusada,
            comStatus(401).entrar("a@b.c", "errada"),
        )
    }

    @Test
    fun dnsQueNaoResolveESemRede() = runBlocking {
        // O tipo que o probe viu no aparelho em modo aviao, pelo engine OkHttp.
        val erro = UnknownHostException("Unable to resolve host")
        assertEquals(ResultadoDaAutenticacao.SemRede, quebrando(erro).entrar("a@b.c", "x"))
    }

    @Test
    fun portaRecusadaESemRede() = runBlocking {
        // O tipo que o probe viu contra a porta morta. Entra diferente do de cima e sai igual, que
        // e a decisao 8: os dois colapsam, e o colapso e deliberado.
        val erro = ConnectException("Failed to connect")
        assertEquals(ResultadoDaAutenticacao.SemRede, quebrando(erro).entrar("a@b.c", "x"))
    }

    @Test
    fun entradaQueFechaDevolveOToken() = runBlocking {
        val resultado = comStatus(200, corpoDeSucesso).entrar("a@b.c", "certa")
        val autenticado = assertInstanceOf(
            ResultadoDaAutenticacao.Autenticado::class.java,
            resultado,
        )
        assertEquals("tok-abc", autenticado.credencial.accessToken)
        assertEquals("ref-xyz", autenticado.credencial.refreshToken)
        assertEquals(3600L, autenticado.credencial.expiresIn)
    }

    // --- O defeito caro desta camada ---

    @Test
    fun servidorComDefeitoNaoEApresentadoComoCredencialRecusada() = runBlocking {
        // 500 nao e culpa da senha de quem esta entrando. Apresenta-lo como credencial recusada
        // manda o professor redigitar uma senha correta ate desistir.
        val resultado = comStatus(500, """{"msg":"internal"}""").entrar("a@b.c", "certa")
        assertNotEquals(ResultadoDaAutenticacao.CredencialRecusada, resultado)
        assertEquals(ResultadoDaAutenticacao.SemRede, resultado)
    }

    @Test
    fun osTresResultadosSaoDistintosEntreSi() = runBlocking {
        // Por conjunto, e nao um a um: um teste por resultado passaria com dois deles mapeados para
        // o mesmo valor. Mesmo formato de `DeviceSessionTest`, pela mesma razao.
        val obtidos = setOf(
            comStatus(200, corpoDeSucesso).entrar("a@b.c", "certa"),
            comStatus(400, corpoDeCredencialRecusada).entrar("a@b.c", "errada"),
            quebrando(UnknownHostException("sem dns")).entrar("a@b.c", "certa"),
        )
        assertEquals(3, obtidos.size, "dois resultados colapsaram num so: $obtidos")
    }

    @Test
    fun corpoSemAccessTokenEstouraEmVezDeProduzirSessaoVazia() {
        // Se isto virasse `SemRede`, contrato quebrado apareceria como problema de conexao e
        // ninguem procuraria no lugar certo. `retornoDe` so captura `IOException` por isso, e o
        // tipo aqui e o do Ktor, e nao o `SerializationException` cru: `JsonConvertException`
        // desce de `Exception`, **nao** de `IOException`, e e por isso que ele escapa.
        assertThrows(JsonConvertException::class.java) {
            runBlocking {
                comStatus(200, """{"token_type":"bearer"}""").entrar("a@b.c", "certa")
            }
        }
    }

    // --- O pedido ---

    @Test
    fun oPedidoLevaAChaveDoProjetoEOCorpoDeEmailESenha() = runBlocking {
        var visto: HttpRequestData? = null
        val auth = autenticacao { pedido ->
            visto = pedido
            Resposta.Corpo(200, corpoDeSucesso)
        }
        auth.entrar("professor@escola.br", "senha-boa")

        val pedido = requireNotNull(visto) { "o pedido nao aconteceu" }
        assertEquals("chave-de-teste", pedido.headers["apikey"])
        assertTrue(
            pedido.url.toString().endsWith("/auth/v1/token?grant_type=password"),
            "o endpoint mudou: ${pedido.url}",
        )
        val corpo = (pedido.body as TextContent).text
        assertTrue(corpo.contains("\"email\":\"professor@escola.br\""), corpo)
        assertTrue(corpo.contains("\"password\":\"senha-boa\""), corpo)
    }

    // --- A traducao para a maquina de estados ---

    @Test
    fun aTraducaoParaASessaoPreservaOsTres() {
        assertEquals(
            listOf(
                ResultadoDaEntrada.Autenticado,
                ResultadoDaEntrada.CredencialRecusada,
                ResultadoDaEntrada.SemRede,
            ),
            listOf(
                ResultadoDaAutenticacao.Autenticado(CredencialDeSessao("t")).paraSessao(),
                ResultadoDaAutenticacao.CredencialRecusada.paraSessao(),
                ResultadoDaAutenticacao.SemRede.paraSessao(),
            ),
        )
    }
}
