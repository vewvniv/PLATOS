package com.platos.android.scan

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.scoring.AwaitingEssay
import com.platos.domain.scoring.PartialScore
import com.platos.domain.scoring.PartialScoringOutcome
import com.platos.domain.scoring.QuestionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O caminho do `onStop`, chamado de onde `onStop` o chama: o fio principal
 * (`slice-5b-3-guardar-a-parcial-e-o-caderno`).
 *
 * Mesmo desenho de `GravacaoNoFioPrincipalInstrumentedTest`, e pela mesma razao: suite vizinha verde
 * nao verifica esta camada (P16), e um teste que nao force `runOnMainSync` mediria de novo o que o
 * teste de repouso ja mede.
 */
@RunWith(AndroidJUnit4::class)
class GuardarCadernoNoFioPrincipalInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val nomeDaBase = "caderno.db"
    private val organizacao = "01a06ba4-cb43-7d97-842d-165352d010b5"
    private val examId = "prova-referencia-slice-1"

    private lateinit var escopo: CoroutineScope

    private fun umCaderno() = Caderno(
        aluno = "tok-a",
        regioes = listOf(
            RegiaoDoCaderno(0, gabarito = true, rotulo = Caderno.ROTULO_GABARITO, estado = EstadoDaRegiao.Capturada),
        ),
        parcial = PartialScoringOutcome.Scored(
            PartialScore(
                packageHash = "hash",
                variantId = "v1",
                objectivePoints = 1,
                objectiveMaxScore = 1,
                maxScore = 5,
                awaiting = listOf(AwaitingEssay("d1", 4)),
                pending = emptyList(),
                outcomes = listOf(QuestionOutcome("q1", QuestionAnswer.Marcada("q1", "A"), worth = 1, earned = 1)),
            ),
        ),
    )

    @Before
    fun preparar() {
        CadernosEmRoom.reiniciarParaTeste()
        context.deleteDatabase(nomeDaBase)
        escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    @After
    fun limpar() {
        escopo.cancel()
        CadernosEmRoom.reiniciarParaTeste()
        context.deleteDatabase(nomeDaBase)
    }

    /**
     * A base e aberta por [CadernosEmRoom.abrir] — a mesma funcao que `ScanActivity` usa —, e a
     * abertura tambem acontece no fio principal, porque e la que a `Activity` a faz.
     */
    @Test
    fun guardar_a_partir_do_fio_principal_nao_estoura_e_o_caderno_chega_ao_disco() {
        lateinit var job: Job
        val original = umCaderno()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val guarda = CadernosEmRoom(CadernosEmRoom.abrir(context).cadernos())
            job = escopo.guardarCadernoEmAndamento(guarda, organizacao, examId, original)
        }

        runBlocking { job.join() }

        // A leitura e por uma instancia nova, e fora do fio principal: se a gravacao tivesse ficado
        // so em memoria, ou nao tivesse acontecido, aqui viria nulo.
        val lido = CadernosEmRoom(CadernosEmRoom.abrir(context).cadernos()).ler(organizacao, examId)

        assertEquals(original, lido)
    }
}
