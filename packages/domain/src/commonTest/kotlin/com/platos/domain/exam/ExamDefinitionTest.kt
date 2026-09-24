package com.platos.domain.exam

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExamDefinitionTest {

    private fun objetiva(id: String) = Question(
        id = id,
        statement = "Qual e o resultado de 2 + 2?",
        options = listOf("3", "4", "5"),
    )

    private fun prova(vararg questions: Question) =
        ExamDefinition(id = "prova-1", title = "Prova", questions = questions.toList())

    private fun criterio(id: String, pontos: Int, linhas: Int = 3) = RubricCriterion(
        id = id,
        description = "Criterio $id",
        points = pontos,
        expectedLines = linhas,
        descriptors = listOf(
            RubricDescriptor(points = pontos, text = "completo"),
            RubricDescriptor(points = 0, text = "ausente"),
        ),
    )

    /** Discursiva valida: a rubrica soma exatamente a pontuacao da questao. */
    private fun discursiva(id: String, vararg pontos: Int = intArrayOf(2, 1)) = Question(
        id = id,
        kind = QuestionKind.ESSAY,
        statement = "Explique por que a soma dos angulos internos de um triangulo e 180 graus.",
        points = pontos.sum(),
        rubric = Rubric(pontos.mapIndexed { i, p -> criterio("c${i + 1}", p) }),
    )

    private fun recusa(prova: ExamDefinition): String =
        assertFailsWith<UnsupportedContentException> { prova.requireSupported() }.message!!

    @Test
    fun `prova objetiva bem formada e aceita`() {
        prova(objetiva("q1"), objetiva("q2")).requireSupported()
    }

    // --- discursiva (slice-5a-regiao-discursiva) ---
    //
    // Este bloco substitui o cenario `questao discursiva e recusada com erro identificavel`, que
    // afirmava a recusa de TODA discursiva. A spec mudou: a discursiva com rubrica passa, e a recusa
    // que sobra e a da discursiva sem rubrica — que e o cenario "Questao discursiva na entrada" de
    // agora. Toda assercao abaixo confere o MOTIVO, e nao so que houve recusa (`rigorous.md` §3).

    @Test
    fun `questao discursiva com rubrica e aceita`() {
        prova(objetiva("q1"), discursiva("q2")).requireSupported()
    }

    @Test
    fun `questao discursiva na entrada sem rubrica e recusada`() {
        val motivo = recusa(prova(objetiva("q1"), discursiva("q2").copy(rubric = null)))
        assertTrue(motivo.contains("q2") && motivo.contains("nao declara rubrica"), motivo)
    }

    @Test
    fun `discursiva com alternativas e recusada`() {
        val motivo = recusa(prova(objetiva("q1"), discursiva("q2").copy(options = listOf("a", "b"))))
        assertTrue(motivo.contains("q2") && motivo.contains("2 alternativa"), motivo)
    }

    @Test
    fun `discursiva com resposta de gabarito e recusada`() {
        val motivo = recusa(prova(objetiva("q1"), discursiva("q2").copy(answer = "180")))
        assertTrue(motivo.contains("q2") && motivo.contains("resposta de gabarito"), motivo)
    }

    @Test
    fun `objetiva com rubrica e recusada`() {
        val comRubrica = objetiva("q1").copy(rubric = Rubric(listOf(criterio("c1", 1))))
        val motivo = recusa(prova(comRubrica))
        assertTrue(motivo.contains("q1") && motivo.contains("objetiva e declara rubrica"), motivo)
    }

    @Test
    fun `objetiva com modo de captura e recusada`() {
        val comModo = objetiva("q1").copy(answerCaptureMode = AnswerCaptureMode.COLOR)
        val motivo = recusa(prova(comModo))
        assertTrue(motivo.contains("q1") && motivo.contains("modo de captura"), motivo)
    }

    @Test
    fun `rubrica que nao fecha com a pontuacao e recusada, com os dois valores`() {
        val motivo = recusa(prova(objetiva("q1"), discursiva("q2").copy(points = 5)))
        assertTrue(motivo.contains("q2"), motivo)
        assertTrue(motivo.contains("soma 3 ponto") && motivo.contains("vale 5"), motivo)
    }

    @Test
    fun `rubrica sem criterio e recusada`() {
        val motivo = recusa(prova(objetiva("q1"), discursiva("q2").copy(rubric = Rubric(emptyList()))))
        assertTrue(motivo.contains("q2") && motivo.contains("nao tem criterio"), motivo)
    }

    @Test
    fun `criterio com pontos nao positivos e recusado`() {
        val zerado = discursiva("q2", 2, 1).let { q ->
            q.copy(rubric = Rubric(listOf(criterio("c1", 3), criterio("c2", 0))), points = 3)
        }
        val motivo = recusa(prova(objetiva("q1"), zerado))
        assertTrue(motivo.contains("`c2`") && motivo.contains("pontos nao positivos"), motivo)
    }

    @Test
    fun `criterio sem linha esperada e recusado`() {
        val semLinha = discursiva("q2", 2).copy(rubric = Rubric(listOf(criterio("c1", 2, linhas = 0))))
        val motivo = recusa(prova(objetiva("q1"), semLinha))
        assertTrue(motivo.contains("`c1`") && motivo.contains("pede 0 linha"), motivo)
    }

    @Test
    fun `criterio sem descritor e recusado`() {
        val semDescritor = discursiva("q2", 2).copy(
            rubric = Rubric(listOf(criterio("c1", 2).copy(descriptors = emptyList()))),
        )
        val motivo = recusa(prova(objetiva("q1"), semDescritor))
        assertTrue(motivo.contains("`c1`") && motivo.contains("nao tem descritor"), motivo)
    }

    @Test
    fun `descritor fora da escala do criterio e recusado`() {
        val foraDaEscala = discursiva("q2", 2).copy(
            rubric = Rubric(
                listOf(criterio("c1", 2).copy(descriptors = listOf(RubricDescriptor(3, "demais")))),
            ),
        )
        val motivo = recusa(prova(objetiva("q1"), foraDaEscala))
        assertTrue(motivo.contains("`c1`") && motivo.contains("fora de [0, 2]"), motivo)
    }

    @Test
    fun `criterio repetido na rubrica e recusado`() {
        val repetido = discursiva("q2", 1, 1).copy(
            rubric = Rubric(listOf(criterio("c1", 1), criterio("c1", 1))),
        )
        val motivo = recusa(prova(objetiva("q1"), repetido))
        assertTrue(motivo.contains("q2") && motivo.contains("identificador repetido: c1"), motivo)
    }

    @Test
    fun `prova sem questao objetiva e recusada`() {
        val motivo = recusa(prova(discursiva("q1"), discursiva("q2")))
        assertTrue(motivo.contains("prova-1") && motivo.contains("nao tem questao objetiva"), motivo)
    }

    @Test
    fun `mais discursivas do que o dicionario comporta e recusado, e o limite e dito`() {
        // 24 passam e 25 nao: o limite e o dicionario (100 marcadores, 25 regioes) menos o gabarito.
        val vinteEQuatro = (1..24).map { discursiva("d$it") }
        prova(objetiva("q1"), *vinteEQuatro.toTypedArray()).requireSupported()

        val vinteECinco = (1..25).map { discursiva("d$it") }
        val motivo = recusa(prova(objetiva("q1"), *vinteECinco.toTypedArray()))
        assertTrue(motivo.contains("25 questoes discursivas"), motivo)
        assertTrue(motivo.contains("comporta 25 regioes") && motivo.contains("sobram 24"), motivo)
    }

    @Test
    fun `recurso nao suportado no enunciado e recusado`() {
        val comImagem = objetiva("q1").copy(
            assets = listOf(QuestionAsset(kind = "image", reference = "grafico.png")),
        )
        val falha = assertFailsWith<UnsupportedContentException> {
            prova(comImagem).requireSupported()
        }
        assertTrue(falha.message!!.contains("image"), falha.message!!)

        val comFormula = objetiva("q1").copy(
            assets = listOf(QuestionAsset(kind = "formula", reference = "x^2")),
        )
        val outraFalha = assertFailsWith<UnsupportedContentException> {
            prova(comFormula).requireSupported()
        }
        assertTrue(outraFalha.message!!.contains("formula"), outraFalha.message!!)
    }

    @Test
    fun `identificador de questao repetido e recusado`() {
        val falha = assertFailsWith<UnsupportedContentException> {
            prova(objetiva("q1"), objetiva("q1")).requireSupported()
        }
        assertTrue(falha.message!!.contains("q1"), falha.message!!)
    }

    @Test
    fun `numero de alternativas fora da faixa e recusado`() {
        assertFailsWith<UnsupportedContentException> {
            prova(objetiva("q1").copy(options = listOf("unica"))).requireSupported()
        }
        assertFailsWith<UnsupportedContentException> {
            prova(objetiva("q1").copy(options = List(6) { "op$it" })).requireSupported()
        }
    }

    @Test
    fun `enunciado vazio e recusado`() {
        assertFailsWith<UnsupportedContentException> {
            prova(objetiva("q1").copy(statement = "   ")).requireSupported()
        }
    }

    @Test
    fun `prova sem questoes e recusada`() {
        assertFailsWith<UnsupportedContentException> { prova().requireSupported() }
    }
}
