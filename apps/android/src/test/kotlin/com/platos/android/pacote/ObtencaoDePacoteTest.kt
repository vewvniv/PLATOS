package com.platos.android.pacote

import com.platos.android.api.ApiPlatos
import com.platos.android.net.clienteHttp
import com.platos.android.session.ProvaPublicada
import com.platos.android.session.ResultadoDoPacote
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import java.io.File
import java.net.UnknownHostException
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * De onde o pacote vem, com cache de verdade e servidor de mentira.
 *
 * O cache e o `PacotesEmArquivo` sobre um `@TempDir`, e nao um duplo: o que estas tarefas verificam
 * e a **trajetoria** — quando o disco responde e quando a rede e chamada —, e um duplo em memoria
 * afirmaria isso sobre um objeto que nao e o que roda no aparelho.
 *
 * O servidor e `MockEngine`, e **quantas vezes ele foi chamado e parte da assercao**: a diferenca
 * entre "usou o cache" e "puxou de novo" nao aparece no valor devolvido, so na contagem.
 */
class ObtencaoDePacoteTest {

    @TempDir
    lateinit var raiz: File

    private val fixtures = File(
        System.getProperty("platos.fixtures")
            ?: error("propriedade `platos.fixtures` nao definida pelo build"),
    )

    private val bytes: ByteArray by lazy {
        File(fixtures, "prova-referencia.package.json").readBytes()
    }

    private val hash: String by lazy {
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private val prova: ProvaPublicada by lazy {
        ProvaPublicada("prova-referencia-slice-1", "Prova de referencia", hash)
    }

    private val cache: PacotesEmArquivo by lazy { PacotesEmArquivo(raiz) }

    private val pedidos = mutableListOf<HttpRequestData>()

    private sealed interface Resposta {
        data class Corpo(
            val status: Int,
            val corpo: ByteArray,
            val hashDeclarado: String?,
        ) : Resposta

        data object SemRede : Resposta
    }

    private fun api(responder: () -> Resposta): ApiPlatos {
        val engine = MockEngine { pedido ->
            pedidos += pedido
            when (val r = responder()) {
                is Resposta.Corpo -> respond(
                    content = ByteReadChannel(r.corpo),
                    status = HttpStatusCode.fromValue(r.status),
                    headers = headersOf(
                        *listOfNotNull(
                            HttpHeaders.ContentType to listOf("application/json"),
                            r.hashDeclarado?.let { ApiPlatos.CABECALHO_DO_HASH to listOf(it) },
                        ).toTypedArray(),
                    ),
                )

                is Resposta.SemRede -> throw UnknownHostException("api.platos.example")
            }
        }
        return ApiPlatos(
            http = clienteHttp(engine),
            urlBase = "https://api.platos.example",
            credencial = { "tok-abc" },
            aoExpirarSessao = {},
        )
    }

    private fun servidorComPacote() = api { Resposta.Corpo(200, bytes, hash) }
    private fun servidorSemRede() = api { Resposta.SemRede }
    private fun servidorSemPacote() = api { Resposta.Corpo(404, "nao ha".toByteArray(), null) }

    // --- As tres trajetorias (tarefa 5.7) ---

    @Test
    fun primeira_vez_puxa_confere_e_guarda() = runBlocking {
        val resultado = obterPacote(cache, servidorComPacote(), ORG, prova)

        assertInstanceOf(ResultadoDoPacote.Conferido::class.java, resultado)
        assertEquals(1, pedidos.size, "esperava um pull")
        assertNotNull(cache.ler(ORG, hash), "o pacote nao ficou guardado")
    }

    /**
     * **O cenario para o qual a fatia inteira existe.**
     *
     * Segunda vez, sem rede: o servidor estoura se for chamado, entao qualquer pull faz este teste
     * falhar por si. O que se afirma nao e so que devolveu o pacote — e que nao houve rede.
     */
    @Test
    fun segunda_vez_responde_do_disco_sem_tocar_na_rede() = runBlocking {
        obterPacote(cache, servidorComPacote(), ORG, prova)
        pedidos.clear()

        val resultado = obterPacote(cache, servidorSemRede(), ORG, prova)

        assertInstanceOf(ResultadoDoPacote.Conferido::class.java, resultado)
        assertTrue(pedidos.isEmpty(), "houve pull havendo pacote guardado: ${pedidos.size}")
    }

    @Test
    fun ausente_e_sem_rede_devolve_sem_rede() = runBlocking {
        val resultado = obterPacote(cache, servidorSemRede(), ORG, prova)

        assertEquals(ResultadoDoPacote.SemRede, resultado)
        assertNull(cache.ler(ORG, hash))
    }

    @Test
    fun servidor_sem_pacote_devolve_ausente() = runBlocking {
        val resultado = obterPacote(cache, servidorSemPacote(), ORG, prova)

        assertEquals(ResultadoDoPacote.Ausente, resultado)
    }

    // --- O que chega errado nao e guardado ---

    @Test
    fun pacote_cujo_hash_nao_confere_e_recusado_e_nao_e_guardado() = runBlocking {
        val corrompido = bytes.copyOf(bytes.size - 30)
        val resultado = obterPacote(cache, api { Resposta.Corpo(200, corrompido, hash) }, ORG, prova)

        assertInstanceOf(ResultadoDoPacote.Recusado::class.java, resultado)
        assertNull(cache.ler(ORG, hash), "um pacote recusado foi guardado")
        assertTrue(raiz.walkTopDown().none { it.isFile }, "sobrou arquivo no cache")
    }

    @Test
    fun pacote_sem_hash_declarado_e_recusado_e_nao_e_guardado() = runBlocking {
        val resultado = obterPacote(cache, api { Resposta.Corpo(200, bytes, null) }, ORG, prova)

        assertEquals(ResultadoDoPacote.Recusado(MotivoDaRecusa.HASH_NAO_DECLARADO), resultado)
        assertTrue(raiz.walkTopDown().none { it.isFile })
    }

    /**
     * A entrega devolveu um pacote integro que **nao e o que a listagem escolheu**.
     *
     * Os dois lados sao internamente coerentes — os bytes conferem com o hash declarado —, e mesmo
     * assim o pacote e outro. Aceita-lo seria escanear com a prova errada; guarda-lo seria gravar
     * sob um endereco que ninguem vai procurar.
     */
    @Test
    fun entrega_coerente_de_outro_pacote_e_recusada() = runBlocking {
        val outros = bytes.decodeToString()
            .replace("prova-referencia-slice-1", "prova-referencia-slice-2").toByteArray()
        val outroHash = MessageDigest.getInstance("SHA-256").digest(outros)
            .joinToString("") { "%02x".format(it) }

        val resultado = obterPacote(cache, api { Resposta.Corpo(200, outros, outroHash) }, ORG, prova)

        assertInstanceOf(ResultadoDoPacote.Recusado::class.java, resultado)
        assertTrue(raiz.walkTopDown().none { it.isFile }, "guardou o pacote errado")
    }

    /** Conteudo guardado que se corrompe leva ao pull de novo, e nao a recusa. */
    @Test
    fun conteudo_guardado_corrompido_recai_no_pull() = runBlocking {
        obterPacote(cache, servidorComPacote(), ORG, prova)
        File(File(raiz, ORG), "$hash.json").writeBytes("lixo".toByteArray())
        pedidos.clear()

        val resultado = obterPacote(cache, servidorComPacote(), ORG, prova)

        assertInstanceOf(ResultadoDoPacote.Conferido::class.java, resultado)
        assertEquals(1, pedidos.size, "nao houve o pull de recuperacao")
    }

    private companion object {
        const val ORG = "11111111-1111-7111-8111-111111111111"
    }
}
