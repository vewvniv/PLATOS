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
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
    }

    private var chamadas = 0

    private fun api(responder: () -> Resposta): ApiPlatos {
        val engine = MockEngine {
            chamadas++
            when (val r = responder()) {
                is Resposta.Corpo -> respond(
                    content = ByteReadChannel(r.corpo),
                    status = HttpStatusCode.fromValue(r.status),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
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

    private fun servidorComRoster() = api { Resposta.Corpo(200, corpoComDois) }
    private fun servidorComRosterVazio() = api { Resposta.Corpo(200, "[]") }
    private fun servidorSemRede() = api { Resposta.SemRede }
    private fun servidorQueRecusa() = api { Resposta.Corpo(404, """{"erro":"nao ha"}""") }

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
}
