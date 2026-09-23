package com.platos.android.api

import com.platos.android.net.Retorno
import com.platos.android.net.clienteHttp
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.UnknownHostException

/**
 * Os dois destinos novos do adaptador: a listagem de provas e o pacote.
 *
 * **O corpo do pacote e comparado em bytes, e nunca em `String`.** Comparar texto reintroduziria uma
 * decodificacao entre o que chegou e o que se afirma — que e exatamente o passo que
 * `retornoDeBytes` existe para nao dar. O literal do corpo tem acento de proposito: em ASCII puro,
 * UTF-8 e Latin-1 produzem os mesmos bytes e o cenario nao teria como falhar.
 */
class ApiPlatosPacoteTest {

    private val corpoDeProvas =
        """[{"short_id":"mat-7a-2026-1","title":"Prova de Matematica","content_hash":"$HASH"}]"""

    /** Espelha o que o servidor emite: pacote canonico, sem espaco, com acento. */
    private val corpoDoPacote =
        """{"meta":{"exam_id":"mat-7a-2026-1"},"titulo":"Frações e proporção"}"""

    private sealed interface Resposta {
        data class Corpo(
            val status: Int,
            val corpo: ByteArray,
            val cabecalhos: List<Pair<String, String>> = emptyList(),
        ) : Resposta

        data class Estoura(val erro: IOException) : Resposta
    }

    private val pedidos = mutableListOf<HttpRequestData>()

    private fun api(responder: (HttpRequestData) -> Resposta): ApiPlatos {
        val engine = MockEngine { pedido ->
            pedidos += pedido
            when (val r = responder(pedido)) {
                is Resposta.Corpo -> respond(
                    content = ByteReadChannel(r.corpo),
                    status = HttpStatusCode.fromValue(r.status),
                    headers = headersOf(
                        *(
                            listOf(HttpHeaders.ContentType to "application/json") + r.cabecalhos
                            ).map { (nome, valor) -> nome to listOf(valor) }.toTypedArray(),
                    ),
                )

                is Resposta.Estoura -> throw r.erro
            }
        }
        return ApiPlatos(
            http = clienteHttp(engine),
            urlBase = "https://api.platos.example",
            credencial = { "tok-abc" },
            aoExpirarSessao = {},
        )
    }

    private fun <T> valorDe(retorno: Retorno<T>): T {
        if (retorno !is Retorno.Respondeu) throw AssertionError("esperava Respondeu, veio $retorno")
        return retorno.valor
    }

    // --- A listagem ---

    @Test
    fun `a listagem chega traduzida com identificador titulo e hash`() = runBlocking {
        val provas = valorDe(
            api { Resposta.Corpo(200, corpoDeProvas.toByteArray()) }.provas("org-1"),
        )

        assertEquals(1, provas.size)
        assertEquals("mat-7a-2026-1", provas.single().shortId)
        assertEquals("Prova de Matematica", provas.single().titulo)
        assertEquals(HASH, provas.single().contentHash)
    }

    @Test
    fun `a listagem vai para a rota da organizacao com a credencial`() = runBlocking {
        api { Resposta.Corpo(200, "[]".toByteArray()) }.provas("org-7")

        assertEquals(
            "https://api.platos.example/organizations/org-7/exams",
            pedidos.single().url.toString(),
        )
        assertEquals("Bearer tok-abc", pedidos.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun `listagem sem rede vira SemRede`() = runBlocking<Unit> {
        val retorno = api { Resposta.Estoura(UnknownHostException("api.platos.example")) }
            .provas("org-1")

        assertInstanceOf(Retorno.SemRede::class.java, retorno)
    }

    // --- O pacote ---

    /**
     * Tarefa 2.2: os bytes exatos, e o hash, na mesma peca.
     *
     * A igualdade e sobre `ByteArray`. Se o corpo passasse pelo `ContentNegotiation`, os acentos
     * sobreviveriam mas o JSON seria reserializado, e este `assertArrayEquals` acusaria.
     */
    @Test
    fun `o pacote chega em bytes exatos com o hash declarado`() = runBlocking {
        val recebido = valorDe(
            api {
                Resposta.Corpo(
                    status = 200,
                    corpo = corpoDoPacote.toByteArray(Charsets.UTF_8),
                    cabecalhos = listOf(ApiPlatos.CABECALHO_DO_HASH to HASH),
                )
            }.pacote("org-1", "mat-7a-2026-1"),
        )

        assertArrayEquals(corpoDoPacote.toByteArray(Charsets.UTF_8), recebido.bytes)
        assertEquals(HASH, recebido.hashDeclarado)
    }

    @Test
    fun `o pacote vai para a rota da prova com a credencial`() = runBlocking {
        api { Resposta.Corpo(200, corpoDoPacote.toByteArray()) }.pacote("org-7", "mat-7b")

        assertEquals(
            "https://api.platos.example/organizations/org-7/exams/mat-7b/package",
            pedidos.single().url.toString(),
        )
        assertEquals("Bearer tok-abc", pedidos.single().headers[HttpHeaders.Authorization])
    }

    /**
     * Tarefa 2.4: cabecalho ausente e ausencia, e nao hash vazio.
     *
     * String vazia como hash declarado faria a conferencia comparar contra um valor que nunca bate:
     * a recusa aconteceria, mas pela razao errada e com a mensagem errada. Pior, se alguem um dia
     * escrevesse a comparacao como "vazio significa nao conferir", a camada (a) sairia do ar sem
     * nada na tela dizendo isso.
     */
    @Test
    fun `pacote sem o cabecalho do hash chega com hash nulo e nao vazio`() = runBlocking {
        val recebido = valorDe(
            api { Resposta.Corpo(200, corpoDoPacote.toByteArray()) }.pacote("org-1", "mat-7a-2026-1"),
        )

        assertNull(recebido.hashDeclarado)
    }

    @Test
    fun `pacote recusado pelo servidor chega como Recusou com o status cru`() = runBlocking {
        val retorno = api { Resposta.Corpo(404, "nao ha prova".toByteArray()) }
            .pacote("org-1", "nao-existe")

        assertEquals(Retorno.Recusou(404), retorno)
    }

    @Test
    fun `pacote sem rede vira SemRede`() = runBlocking<Unit> {
        val retorno = api { Resposta.Estoura(UnknownHostException("api.platos.example")) }
            .pacote("org-1", "mat-7a-2026-1")

        assertInstanceOf(Retorno.SemRede::class.java, retorno)
    }

    /**
     * Tarefa 2.3: o corpo do pacote **nao** e JSON valido, e chega inteiro assim mesmo.
     *
     * E o cenario que separa `readRawBytes` de `body<ByteArray>()`. Um pacote truncado por queda de
     * conexao no meio da resposta e bytes que nao parseiam; se o transporte tentasse desserializar,
     * ele estouraria aqui — e a camada (a), que existe justamente para pegar corpo truncado, nunca
     * chegaria a rodar. O transporte entrega o que chegou; quem julga e a conferencia.
     */
    @Test
    fun `corpo que nao e json chega inteiro em vez de estourar no transporte`() = runBlocking {
        val truncado = corpoDoPacote.dropLast(12).toByteArray(Charsets.UTF_8)

        val recebido = valorDe(
            api {
                Resposta.Corpo(
                    status = 200,
                    corpo = truncado,
                    cabecalhos = listOf(ApiPlatos.CABECALHO_DO_HASH to HASH),
                )
            }.pacote("org-1", "mat-7a-2026-1"),
        )

        assertArrayEquals(truncado, recebido.bytes)
        assertEquals(HASH, recebido.hashDeclarado)
    }

    private companion object {
        const val HASH = "5b2e7f1c0a9d4e3b8c6f5a4d3e2b1c0f9e8d7c6b5a4938271605f4e3d2c1b0a9"
    }
}
