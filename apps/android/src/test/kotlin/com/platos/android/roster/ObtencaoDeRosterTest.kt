package com.platos.android.roster

import com.platos.android.api.ApiPlatos
import com.platos.android.net.clienteHttp
import com.platos.android.session.ProvaPublicada
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import java.io.File
import java.net.UnknownHostException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * O ciclo de pull do roster, contra `MockEngine` e o sistema de arquivos de verdade.
 *
 * Os dois cenarios que importam sao **primeira escolha, com rede** e **segunda escolha, sem rede**. O
 * segundo e o que prova que o guardado serve para o que ele existe: sem ele, o cache estaria coberto
 * so pela escrita, e um cache que so se escreve e um cache que nunca foi lido.
 *
 * Alcancavel na JVM porque `obterRoster` e funcao de topo, como `obterPacote` — se o pull vivesse
 * dentro da `Activity`, nenhum destes cenarios teria teste, e a fatia afirmaria sobre o que so o
 * emulador veria.
 */
class ObtencaoDeRosterTest {

    @TempDir
    lateinit var raiz: File

    private val cache: RostersEmArquivo by lazy { RostersEmArquivo(raiz) }

    private val escola = "01a06ba4-cb43-7d97-842d-165352d010b5"
    private val prova = ProvaPublicada("mat-7a-2026-1", "Prova de Matematica", "a".repeat(64))
    private val agora = 1_757_000_000_000L

    private val corpoComDois =
        """[{"student_token":"tok-a","display_name":"Ana Ribeiro"},""" +
            """{"student_token":"tok-b","display_name":"Bruno Alves"}]"""

    private sealed interface Resposta {
        data class Corpo(val status: Int, val corpo: String) : Resposta
        data object SemRede : Resposta
        data object Pendurada : Resposta
    }

    private var chamadas = 0
    private val pedidos = mutableListOf<io.ktor.client.request.HttpRequestData>()

    private fun api(responder: () -> Resposta): ApiPlatos {
        val engine = MockEngine { pedido ->
            chamadas++
            pedidos += pedido
            when (val r = responder()) {
                is Resposta.Corpo -> respond(
                    content = ByteReadChannel(r.corpo),
                    status = HttpStatusCode.fromValue(r.status),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )

                is Resposta.SemRede -> throw UnknownHostException("api.platos.example")

                is Resposta.Pendurada -> {
                    delay(Long.MAX_VALUE)
                    error("inalcancavel")
                }
            }
        }
        return ApiPlatos(
            http = clienteHttp(engine),
            urlBase = "https://api.platos.example",
            credencial = { "tok-abc" },
            aoExpirarSessao = {},
        )
    }

    private fun servidorComRoster() = api { Resposta.Corpo(200, corpoComDois) }
    private fun servidorComRosterVazio() = api { Resposta.Corpo(200, "[]") }
    private fun servidorSemRede() = api { Resposta.SemRede }
    private fun servidorQueRecusa() = api { Resposta.Corpo(404, """{"erro":"nao ha"}""") }

    /**
     * O servidor que **nunca responde**, e nao o que responde devagar.
     *
     * E a rede de escola associada a um ponto sem saida: nao ha falha rapida a esperar, e o pedido
     * fica pendurado ate o tempo limite. Sem este duplo, o cenario da abertura so seria observavel
     * por cronometro.
     */
    private fun servidorQueNuncaResponde() = api { Resposta.Pendurada }

    /**
     * **Primeira escolha da prova: o roster desce e fica guardado.**
     *
     * Duas asercoes, e a segunda nao e redundante: a primeira diz o que a funcao devolveu, e a
     * segunda diz que o aparelho **guardou** — sem ela, uma implementacao que devolvesse sem gravar
     * passaria, e o cenario seguinte (sem rede) e o unico que a pegaria.
     */
    @Test
    fun primeira_escolha_puxa_o_roster_e_o_guarda() = runBlocking {
        val roster = obterRoster(cache, servidorComRoster(), escola, prova, agora)

        assertEquals(
            listOf(AlunoDoRoster("tok-a", "Ana Ribeiro"), AlunoDoRoster("tok-b", "Bruno Alves")),
            roster!!.alunos,
        )
        assertEquals(
            roster,
            cache.ler(escola, prova.shortId),
            "o roster foi devolvido mas nao ficou guardado no aparelho",
        )
    }

    @Test
    fun o_instante_guardado_e_o_que_entrou_por_parametro() = runBlocking {
        obterRoster(cache, servidorComRoster(), escola, prova, agora)

        assertEquals(
            agora,
            cache.ler(escola, prova.shortId)!!.puxadoEm,
            "o instante guardado nao e o que a chamada afirmou, e a marca de cache mentiria a idade",
        )
    }

    /**
     * **Segunda escolha, sem rede: o guardado serve.**
     *
     * E o cenario para o qual o cache existe. `chamadas` faz parte da asercao pela razao que
     * `ObtencaoDePacoteTest` registra: sem ela, uma implementacao que nunca tentasse a rede passaria
     * igual, e o teste nao diria se o guardado foi usado por escolha ou por omissao.
     */
    @Test
    fun segunda_escolha_sem_rede_usa_o_roster_guardado() = runBlocking {
        obterRoster(cache, servidorComRoster(), escola, prova, agora)
        chamadas = 0

        val roster = obterRoster(cache, servidorSemRede(), escola, prova, agora + 86_400_000)

        // A rede E tentada, e e isso que se afirma: o roster e mutavel, entao o guardado so vale
        // depois de o servidor nao responder. Sem esta asercao, uma implementacao que lesse o disco
        // primeiro e nunca tentasse a rede passaria neste teste — e apresentaria em silencio o nome
        // de ontem com o servidor no ar.
        assertEquals(1, chamadas, "o pull nem tentou a rede antes de cair no guardado")
        assertNotNull(roster, "o roster guardado nao foi usado quando a rede caiu")
        assertEquals(
            listOf(AlunoDoRoster("tok-a", "Ana Ribeiro"), AlunoDoRoster("tok-b", "Bruno Alves")),
            roster!!.alunos,
        )
        assertEquals(agora, roster.puxadoEm, "o instante virou o de agora, e o dado e o de ontem")
    }

    /**
     * **A rede ganha do guardado quando ela responde**, ao contrario do pacote.
     *
     * O pacote e imutavel e endereçado por hash, entao o cache primeiro e correto por construcao. O
     * roster e mutavel (ADR-0002): preferir o guardado apresentaria um nome que o servidor ja
     * corrigiu, e nada na tela diria isso.
     */
    @Test
    fun com_rede_o_que_o_servidor_diz_substitui_o_guardado() = runBlocking {
        cache.guardar(
            escola,
            prova.shortId,
            RosterDaProva(listOf(AlunoDoRoster("tok-a", "Ana Ribero")), agora),
        )

        val roster = obterRoster(cache, servidorComRoster(), escola, prova, agora + 1000)

        assertEquals("Ana Ribeiro", roster!!.alunos.first().nome, "o nome corrigido nao chegou")
        assertEquals(agora + 1000, cache.ler(escola, prova.shortId)!!.puxadoEm)
    }

    @Test
    fun roster_vazio_desce_e_e_guardado_como_roster() = runBlocking {
        val roster = obterRoster(cache, servidorComRosterVazio(), escola, prova, agora)

        assertNotNull(roster, "roster vazio voltou como ausencia, e o gate barraria a prova")
        assertEquals(emptyList<AlunoDoRoster>(), roster!!.alunos)
        assertNotNull(
            cache.ler(escola, prova.shortId),
            "roster vazio nao ficou guardado, e a segunda escolha sem rede barraria",
        )
    }

    /**
     * **Recusa nao joga fora o que o aparelho puxou legitimamente antes.**
     *
     * Apagar aqui transformaria um 404 momentaneo — ou uma prova que saiu do ar — em perda do roster
     * numa sala sem sinal, e o professor ficaria sem nome nenhum no meio da aplicacao.
     */
    @Test
    fun recusa_do_servidor_preserva_o_roster_ja_guardado() = runBlocking {
        obterRoster(cache, servidorComRoster(), escola, prova, agora)

        val roster = obterRoster(cache, servidorQueRecusa(), escola, prova, agora + 1000)

        assertNotNull(roster, "a recusa apagou o roster que ja estava guardado")
        assertEquals(2, roster!!.alunos.size)
    }

    @Test
    fun prova_nunca_puxada_e_sem_rede_nao_inventa_roster() = runBlocking {
        val roster = obterRoster(cache, servidorSemRede(), escola, prova, agora)

        assertNull(roster, "sem rede e sem guardado, apareceu um roster do nada")
        assertNull(cache.ler(escola, prova.shortId))
    }

    /**
     * **A rota que o cliente bate, prendida.**
     *
     * Sem esta asercao o `MockEngine` responde **qualquer** URL, e um erro de digitacao em
     * `/roster` — ou a organizacao e o `short_id` trocados de posicao — passaria a suite de JVM
     * inteira e so falharia em aparelho, contra o servidor real. E a forma que `ApiPlatosTest` ja
     * usa para as rotas que existiam antes desta fatia.
     */
    @Test
    fun a_obtencao_bate_na_rota_que_o_servidor_expoe() = runBlocking {
        obterRoster(cache, servidorComRoster(), escola, prova, agora)

        assertEquals(
            "/organizations/$escola/exams/${prova.shortId}/roster",
            pedidos.single().url.encodedPath,
        )
    }

    // ------------------------------------------------- a abertura nao fica refem da rede

    /**
     * **Com roster guardado, a abertura nao espera o pull.**
     *
     * O servidor deste cenario **nunca responde** — e nao "responde devagar": e o unico jeito de
     * falsificar "nao esperou" sem cronometro, que seria medicao que nao mede. Se `prepararRoster`
     * esperasse, `withTimeout` estouraria e o teste ficaria vermelho.
     *
     * O caso real e a rede de escola associada a um ponto sem saida: nao ha `UnknownHostException`
     * rapido, e a espera vai ate o tempo limite de 90 s antes de abrir com o que ja estava em disco.
     */
    @Test
    fun com_roster_guardado_a_abertura_nao_espera_o_pull() = runBlocking {
        cache.guardar(escola, prova.shortId, RosterDaProva(listOf(AlunoDoRoster("tok-a", "Ana Ribeiro")), agora))

        // **Escopo separado para a atualizacao, e a razao e o que este teste mede.** Passando o
        // escopo do proprio `withTimeout`, a atualizacao vira filha dele — e concorrencia estruturada
        // faz o `withTimeout` esperar o filho pendurado, estourando mesmo com `prepararRoster` tendo
        // devolvido na hora. Foi o que aconteceu na primeira redacao deste teste, e o vermelho era do
        // teste e nao do codigo (P12): a mensagem dizia `TimeoutCancellationException`, e nao uma
        // asercao sobre `esperou`.
        val fundo = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val esperou = withTimeout(2_000) {
                prepararRoster(cache, servidorQueNuncaResponde(), escola, prova, agora, fundo)
            }

            assertFalse(esperou, "a abertura esperou o pull mesmo com roster guardado")
        } finally {
            fundo.cancel()
        }
    }

    /**
     * **Sem roster guardado, a abertura espera — e isso tambem e afirmado.**
     *
     * O par do cenario acima, e ele e o que impede o conserto de virar "nunca espera". Sem guardado
     * o gate barra, entao esperar tem significado: abrir para mostrar a barragem pediria a rede duas
     * vezes. Com o servidor que nunca responde, esperar significa estourar o `withTimeout` — e e o
     * estouro que se afirma.
     */
    @Test
    fun sem_roster_guardado_a_abertura_espera_o_pull() = runBlocking {
        var esperou: Boolean? = null

        val estourou = try {
            withTimeout(1_000) {
                esperou = prepararRoster(cache, servidorQueNuncaResponde(), escola, prova, agora, this)
                Unit
            }
            false
        } catch (_: TimeoutCancellationException) {
            true
        }

        assertTrue(estourou, "sem roster guardado a abertura nao esperou o pull; veio $esperou")
    }
}
