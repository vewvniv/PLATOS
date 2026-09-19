package com.platos.android.outbox

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.scoring.ObjectiveScore
import com.platos.domain.scoring.QuestionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A guarda que faltava, e que teria pego o defeito antes do aparelho.
 *
 * **O que ela afirma:** que o caminho que o laco da camera usa pode ser chamado **do fio principal**,
 * com o banco aberto **como a producao o abre**, sem estourar — e que a linha chega ao disco.
 *
 * **Por que ela nao existia.** `OutboxEmRepousoInstrumentedTest` exercitava a guarda por fora, do fio
 * do runner, e abria o banco com `allowMainThreadQueries`. Nenhuma das duas coisas e verdade na
 * producao: la o banco e aberto sem o afrouxamento, e `ScanActivity.gravar` e `DeviceSession.sair`
 * chamavam do principal. O resultado foi `IllegalStateException: Cannot access database on the main
 * thread` em aparelho real, cinco vezes, com a suite inteira verde. Suite vizinha verde nao verifica
 * esta camada (P16), e oraculo afrouxado nao verifica camada nenhuma.
 *
 * **`runOnMainSync` e o ponto inteiro do arquivo.** O runner instrumentado **nao** roda os testes no
 * fio principal; sem forcar, este teste mediria de novo o que o outro ja media. Se alguem o tirar
 * daqui, o arquivo passa a nao afirmar nada — e continua verde.
 */
@RunWith(AndroidJUnit4::class)
class GravacaoNoFioPrincipalInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val nomeDaBase = "outbox.db"

    private lateinit var escopo: CoroutineScope

    private val organizacao = "01a06ba4-cb43-7d97-842d-165352d010b5"

    private fun umResultado(captureId: String) = ResultadoPendente(
        captureId = captureId,
        organizacao = organizacao,
        prova = "prova-referencia-slice-1",
        studentToken = "tok-a",
        apuradoEm = 1_789_646_400_000L,
        nota = ObjectiveScore(
            packageHash = "a".repeat(64),
            variantId = "v1",
            points = 1,
            maxScore = 1,
            pending = emptyList(),
            outcomes = listOf(
                QuestionOutcome("q01", QuestionAnswer.Marcada("q01", "A"), worth = 1, earned = 1),
            ),
        ),
    )

    @Before
    fun preparar() {
        // **Reiniciar antes de apagar, e nao depois.** `abrir` guarda a instancia no processo;
        // apagar o arquivo sem esquecer a referencia deixaria a proxima chamada devolvendo uma
        // instancia que aponta para um arquivo que nao existe mais.
        ResultadosEmRoom.reiniciarParaTeste()
        context.deleteDatabase(nomeDaBase)
        escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    @After
    fun limpar() {
        escopo.cancel()
        ResultadosEmRoom.reiniciarParaTeste()
        context.deleteDatabase(nomeDaBase)
    }

    /**
     * O caminho da camera, chamado de onde a camera o chama.
     *
     * A base e aberta por [ResultadosEmRoom.abrir] — a **mesma** funcao que a `Activity` usa —, e a
     * abertura tambem acontece no fio principal, porque e la que a `Activity` a faz.
     */
    @Test
    fun gravar_a_partir_do_fio_principal_nao_estoura_e_a_linha_chega_ao_disco() {
        lateinit var job: Job
        var agendou = false

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val pendentes = ResultadosEmRoom(ResultadosEmRoom.abrir(context).pendentes())
            job = escopo.gravarEAgendar(pendentes, umResultado("cap-fio-principal")) {
                agendou = true
            }
        }

        runBlocking { job.join() }

        // A leitura e por uma instancia nova, e fora do fio principal: se a gravacao tivesse ficado
        // so em memoria, ou nao tivesse acontecido, aqui viria zero.
        val lidos = ResultadosEmRoom(ResultadosEmRoom.abrir(context).pendentes()).pendentesDa(organizacao)

        assertEquals(1, lidos.size)
        assertEquals("cap-fio-principal", lidos.single().captureId)
        assertTrue("o envio precisa ser agendado depois da gravacao", agendou)
    }

    /**
     * O agendamento acontece **depois** de a linha existir, e nao em paralelo.
     *
     * A spec manda gravar antes de qualquer tentativa de envio. Agendar em paralelo abriria a chance
     * de o trabalho rodar, nao achar a linha, e concluir que a fila esta vazia — uma correcao que
     * nunca sobe, sem erro nenhum.
     */
    @Test
    fun o_envio_e_agendado_so_depois_de_a_linha_estar_em_disco() {
        lateinit var job: Job
        var viaALinhaAoAgendar = false

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val pendentes = ResultadosEmRoom(ResultadosEmRoom.abrir(context).pendentes())
            job = escopo.gravarEAgendar(pendentes, umResultado("cap-ordem")) {
                viaALinhaAoAgendar = pendentes.quantosPendentes(organizacao) == 1
            }
        }

        runBlocking { job.join() }

        assertTrue(
            "o agendamento rodou antes de a gravacao terminar",
            viaALinhaAoAgendar,
        )
    }

    /**
     * Contar pendentes do fio principal tambem estoura — e e por isso que `DeviceSession` nao conta.
     *
     * Este teste afirma a **razao do desenho**, e nao so o desenho: se um dia o Room parar de recusar
     * leitura no fio principal, este cenario fica vermelho e alguem relê a decisao com o fato na mao,
     * em vez de manter uma estrutura cujo motivo evaporou.
     */
    @Test
    fun ler_a_fila_do_fio_principal_ainda_e_recusado_pelo_Room() {
        var motivo: String? = null

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val pendentes = ResultadosEmRoom(ResultadosEmRoom.abrir(context).pendentes())
            motivo = try {
                pendentes.quantosPendentes(organizacao)
                null
            } catch (erro: IllegalStateException) {
                erro.message
            }
        }

        assertTrue(
            "esperava a recusa do Room ao acesso no fio principal, e veio: $motivo",
            motivo.orEmpty().contains("main thread"),
        )
    }
}
