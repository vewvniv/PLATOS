package com.platos.android.scan

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.scoring.AwaitingEssay
import com.platos.domain.scoring.PartialScore
import com.platos.domain.scoring.PartialScoringOutcome
import com.platos.domain.scoring.QuestionOutcome
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O caderno em andamento sobre o SQLite de verdade (`slice-5b-3-guardar-a-parcial-e-o-caderno`,
 * tarefas 1.2 e 1.3).
 *
 * **Base em arquivo, e nao `inMemoryDatabaseBuilder`**, pela mesma razao de
 * `OutboxEmRepousoInstrumentedTest`: "fechei e reabri e o caderno estava la" seria falso por
 * construcao numa base em memoria, e e exatamente a afirmacao que sustenta a task 2.1.
 */
@RunWith(AndroidJUnit4::class)
class CadernoEmRepousoInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val nomeDaBase = "caderno-de-teste.db"

    private lateinit var base: BaseDoCaderno
    private lateinit var guarda: CadernosGuardados

    private val organizacao = "01a06ba4-cb43-7d97-842d-165352d010b5"
    private val outraOrganizacao = "01a06ba4-0000-7d97-842d-165352d010b5"
    private val examId = "prova-referencia-slice-1"

    private fun abrir(): BaseDoCaderno =
        // Sem `allowMainThreadQueries`, pela mesma razao de `OutboxEmRepousoInstrumentedTest`: a
        // producao abre sem ele, e o runner instrumentado nao roda no fio principal.
        Room.databaseBuilder(context, BaseDoCaderno::class.java, nomeDaBase).build()

    private fun caderno(aluno: String, objectivePoints: Int) = Caderno(
        aluno = aluno,
        regioes = listOf(
            RegiaoDoCaderno(0, gabarito = true, rotulo = Caderno.ROTULO_GABARITO, estado = EstadoDaRegiao.Capturada),
            RegiaoDoCaderno(1, gabarito = false, rotulo = "1", estado = EstadoDaRegiao.NaoVista),
        ),
        parcial = PartialScoringOutcome.Scored(
            PartialScore(
                packageHash = "hash",
                variantId = "v1",
                objectivePoints = objectivePoints,
                objectiveMaxScore = 1,
                maxScore = 5,
                awaiting = listOf(AwaitingEssay("d1", 4)),
                pending = emptyList(),
                outcomes = listOf(
                    QuestionOutcome(
                        "q1",
                        QuestionAnswer.Marcada("q1", "A"),
                        worth = 1,
                        earned = objectivePoints,
                    ),
                ),
            ),
        ),
    )

    @Before
    fun abrirBase() {
        context.deleteDatabase(nomeDaBase)
        base = abrir()
        guarda = CadernosEmRoom(base.cadernos())
    }

    @After
    fun limpar() {
        base.close()
        context.deleteDatabase(nomeDaBase)
    }

    @Test
    fun guardar_duas_vezes_para_a_mesma_organizacao_e_prova_deixa_uma_linha_so() {
        guarda.guardar(organizacao, examId, caderno("tok-a", objectivePoints = 0))
        guarda.guardar(organizacao, examId, caderno("tok-b", objectivePoints = 1))

        val linhas = base.query("select count(*) from caderno_em_andamento", null)
        linhas.moveToFirst()
        assertEquals(1, linhas.getInt(0))
        linhas.close()

        assertEquals("tok-b", guarda.ler(organizacao, examId)?.aluno)
    }

    /**
     * A ancora do que foi lido (P3): comparado com o caderno inteiro, e nao so a contagem de linhas.
     */
    @Test
    fun o_caderno_volta_do_disco_identico_ao_que_entrou() {
        val original = caderno("tok-a", objectivePoints = 1)
        guarda.guardar(organizacao, examId, original)
        base.close()

        // Instancia nova sobre o mesmo arquivo: se o caderno estivesse em memoria, aqui viria nulo.
        base = abrir()
        val lido = CadernosEmRoom(base.cadernos()).ler(organizacao, examId)

        assertEquals(original, lido)
    }

    @Test
    fun nenhum_caderno_guardado_para_uma_prova_que_nunca_foi_tocada() {
        assertNull(guarda.ler(organizacao, "outra-prova"))
    }

    /**
     * A organizacao entra na chave porque o aparelho e compartilhado entre escolas
     * (`DeviceSession.sair`), e `examId` nao tem contrato de unicidade entre organizacoes (design,
     * decisao 2). Sem isto, o mesmo `examId` em duas organizacoes misturaria os dois cadernos.
     */
    @Test
    fun o_mesmo_examId_em_outra_organizacao_nao_e_o_mesmo_caderno() {
        guarda.guardar(organizacao, examId, caderno("tok-a", objectivePoints = 1))

        assertNull(guarda.ler(outraOrganizacao, examId))
    }
}
