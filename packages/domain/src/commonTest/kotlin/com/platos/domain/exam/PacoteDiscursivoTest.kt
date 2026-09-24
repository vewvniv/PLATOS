package com.platos.domain.exam

import com.platos.domain.capture.PayloadReading
import com.platos.domain.capture.QrPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O pacote de uma prova com discursiva (`slice-5a-regiao-discursiva`, spec de `exam-package`).
 *
 * Cada cenario daqui corresponde a um cenario da spec, e o nome diz qual.
 */
class PacoteDiscursivoTest {

    private val habilidade = listOf(ItemSkill("EF07MA01", SkillCoverage.DIRECT))

    private fun objetiva(id: String) = Question(
        id = id,
        statement = "Quanto vale $id?",
        options = listOf("1", "2", "3", "4"),
        answer = "2",
        skills = habilidade,
    )

    private fun discursiva(id: String, modo: AnswerCaptureMode? = null) = Question(
        id = id,
        kind = QuestionKind.ESSAY,
        statement = "Justifique a resposta da questao $id.",
        points = 3,
        skills = habilidade,
        answerCaptureMode = modo,
        rubric = Rubric(
            listOf(
                RubricCriterion(
                    id = "c1",
                    description = "Identifica a propriedade",
                    points = 2,
                    expectedLines = 3,
                    descriptors = listOf(RubricDescriptor(2, "identifica e nomeia"), RubricDescriptor(0, "nao identifica")),
                ),
                RubricCriterion(
                    id = "c2",
                    description = "Conclui corretamente",
                    points = 1,
                    expectedLines = 2,
                    descriptors = listOf(RubricDescriptor(1, "conclui"), RubricDescriptor(0, "nao conclui")),
                ),
            ),
        ),
    )

    private fun prova(vararg questoes: Question) =
        ExamDefinition(id = "prova-disc", title = "Prova", questions = questoes.toList())

    /** Cenario "A rubrica chega ao pacote". */
    @Test
    fun `a rubrica e o modo de captura chegam ao item discursivo`() {
        val definicao = prova(objetiva("q1"), discursiva("q2"), discursiva("q3", AnswerCaptureMode.COLOR))
        val pacote = definicao.buildPackage()
        val porId = pacote.items.associateBy { it.id }

        assertEquals(QuestionKind.ESSAY, porId.getValue("q2").kind)
        assertEquals(definicao.questions[1].rubric, porId.getValue("q2").rubric)
        // Sem declarar, cinza; declarando cor, cor (§8).
        assertEquals(AnswerCaptureMode.GRAY, porId.getValue("q2").answerCaptureMode)
        assertEquals(AnswerCaptureMode.COLOR, porId.getValue("q3").answerCaptureMode)
        // A objetiva nao leva nenhum dos dois.
        assertEquals(QuestionKind.OBJECTIVE, porId.getValue("q1").kind)
        assertEquals(null, porId.getValue("q1").rubric)
        assertEquals(null, porId.getValue("q1").answerCaptureMode)
    }

    /** Cenario "Prova com discursiva nao e corrigivel so no aparelho". */
    @Test
    fun `prova com discursiva declara que nao e corrigivel so no aparelho`() {
        val pacote = prova(objetiva("q1"), discursiva("q2")).buildPackage()

        assertEquals(false, pacote.meta.fullyOfflineGradable)
        assertEquals(1 + 3, pacote.scoring.maxScore, "a nota maxima nao inclui a discursiva")
        assertEquals(listOf("q1"), pacote.answerKey.map { it.itemId }, "o gabarito tem entrada de discursiva")
    }

    /** Cenario "Prova so objetiva continua corrigivel no aparelho". */
    @Test
    fun `prova so objetiva continua corrigivel no aparelho, sem rubrica em item nenhum`() {
        val pacote = prova(objetiva("q1"), objetiva("q2")).buildPackage()

        assertEquals(true, pacote.meta.fullyOfflineGradable)
        assertTrue(pacote.items.all { it.rubric == null && it.answerCaptureMode == null })
    }

    /** Cenario "A atribuicao traz um QR por regiao". */
    @Test
    fun `a atribuicao traz um QR por regiao, todos com o token dela`() {
        val pacote = prova(objetiva("q1"), discursiva("q2"), discursiva("q3"))
            .buildPackage(tokens = listOf("tok-a", "tok-b"))

        for (atribuicao in pacote.assignments) {
            assertEquals(listOf(0, 1, 2), atribuicao.qrs.map { it.regionIndex })
            for (qr in atribuicao.qrs) {
                // Lido pelo mesmo leitor que o aparelho usa, e nao por `split` local.
                val lido = QrPayload.read(qr.payload)
                val payload = (lido as? PayloadReading.Read)?.payload
                    ?: throw AssertionError("QR ilegivel: $lido")
                assertEquals(atribuicao.studentToken, payload.studentToken)
                assertEquals(qr.regionIndex, payload.regionIndex, "o QR diz uma regiao e esta associado a outra")
            }
        }
        // E o pacote inteiro passa na coerencia.
        pacote.requireCoherent()
    }

    // --- 4.2: a coerencia do pacote discursivo ---
    //
    // Cada cenario parte de um pacote VALIDO e muda UMA coisa, e a assercao confere o motivo
    // (`rigorous.md` §3). Onde a mudanca alcancaria outra camada, a fixture e escolhida para nao
    // alcancar, e o comentario diz qual.

    private val comTurma: ExamPackage by lazy {
        prova(objetiva("q1"), discursiva("q2"), discursiva("q3")).buildPackage(tokens = listOf("tok-a"))
    }

    private fun recusa(pacote: ExamPackage): String =
        kotlin.test.assertFailsWith<ExamPackageException> { pacote.requireCoherent() }.message!!

    /** Cenario "Item discursivo com gabarito". */
    @Test
    fun `item discursivo com entrada no gabarito e recusado`() {
        val quebrado = comTurma.copy(answerKey = comTurma.answerKey + AnswerKeyEntry("q2", "A", 3))
        val motivo = recusa(quebrado)
        assertTrue(motivo.startsWith("itens discursivos com entrada no gabarito: q2"), motivo)
    }

    /** Cenario "Item discursivo sem regiao". */
    @Test
    fun `item discursivo sem regiao no layout e recusado`() {
        // Sem roster: com atribuicao, tirar a regiao faria a conferencia de QR por regiao recusar
        // antes, e o cenario mediria a camada vizinha.
        val semTurma = prova(objetiva("q1"), discursiva("q2"), discursiva("q3")).buildPackage()
        val quebrado = semTurma.copy(
            layout = semTurma.layout.mapValues { (_, mapa) ->
                mapa.copy(regions = mapa.regions.filter { it.questionId != "q2" })
            },
        )
        val motivo = recusa(quebrado)
        assertTrue(motivo.contains("`q2` tem 0 regiao(oes)") && motivo.contains("variante `v1`"), motivo)
    }

    /** Cenario "Correcao offline declarada contra os itens". */
    @Test
    fun `pacote com discursiva que se declara corrigivel offline e recusado`() {
        val quebrado = comTurma.copy(meta = comTurma.meta.copy(fullyOfflineGradable = true))
        val motivo = recusa(quebrado)
        assertTrue(motivo.contains("fully_offline_gradable = true") && motivo.contains("2 item(ns) discursivo"), motivo)
    }

    /** Cenario "Atribuicao sem o QR de uma regiao". */
    @Test
    fun `atribuicao sem o QR de uma regiao discursiva e recusada`() {
        val quebrado = comTurma.copy(
            assignments = comTurma.assignments.map { a -> a.copy(qrs = a.qrs.filter { it.regionIndex != 2 }) },
        )
        val motivo = recusa(quebrado)
        assertTrue(motivo.contains("`tok-a` traz QR para as regioes [0, 1]") && motivo.contains("[0, 1, 2]"), motivo)
    }

    /** Cenario "QR de atribuicao associado a regiao errada". */
    @Test
    fun `QR associado a regiao errada e recusado`() {
        val quebrado = comTurma.copy(
            assignments = comTurma.assignments.map { a ->
                val daRegiao2 = a.qrs.single { it.regionIndex == 2 }
                a.copy(qrs = a.qrs.map { if (it.regionIndex == 1) it.copy(payload = daRegiao2.payload) else it })
            },
        )
        val motivo = recusa(quebrado)
        assertTrue(motivo.contains("associa a regiao 1 a um QR que diz regiao 2"), motivo)
    }

    @Test
    fun `objetiva com rubrica no pacote e recusada`() {
        val rubrica = requireNotNull(comTurma.items.single { it.id == "q2" }.rubric)
        val quebrado = comTurma.copy(items = comTurma.items.map { if (it.id == "q1") it.copy(rubric = rubrica) else it })
        assertTrue(recusa(quebrado).startsWith("itens objetivos com rubrica: q1"))
    }

    @Test
    fun `nota maxima que nao fecha com gabarito e rubricas e recusada`() {
        val quebrado = comTurma.copy(scoring = comTurma.scoring.copy(maxScore = 99))
        val motivo = recusa(quebrado)
        assertTrue(motivo.contains("e 99, e gabarito (1) mais rubricas (6) somam 7"), motivo)
    }
}
