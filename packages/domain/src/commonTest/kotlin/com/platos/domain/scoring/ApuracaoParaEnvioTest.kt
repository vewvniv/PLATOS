package com.platos.domain.scoring

import com.platos.domain.capture.QuestionAnswer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O tipo que leva `ObjectiveScore` e `PartialScore` para a mesma fila, sem confundir os dois
 * (`slice-5b-4-envio-da-parcial`, design decisao 2).
 *
 * Um `when` exaustivo e a prova que o compilador acusa o caso que faltar: se um terceiro caso
 * entrar em [ApuracaoParaEnvio] sem uma ramificacao aqui, este teste deixa de compilar.
 */
class ApuracaoParaEnvioTest {

    private fun certa(id: String) = QuestionOutcome(id, QuestionAnswer.Marcada(id, "A"), worth = 1, earned = 1)

    private val completa = ObjectiveScore(
        "hash",
        "v1",
        points = 1,
        maxScore = 1,
        pending = emptyList(),
        outcomes = listOf(certa("q1")),
    )

    private val parcial = PartialScore(
        "hash",
        "v1",
        objectivePoints = 1,
        objectiveMaxScore = 1,
        maxScore = 8,
        awaiting = listOf(AwaitingEssay("d1", 3), AwaitingEssay("d2", 4)),
        pending = emptyList(),
        outcomes = listOf(certa("q1")),
    )

    private fun descricao(apuracao: ApuracaoParaEnvio): String = when (apuracao) {
        is ApuracaoParaEnvio.Completa -> "completa:${apuracao.score.points}"
        is ApuracaoParaEnvio.Parcial -> "parcial:${apuracao.score.objectivePoints}"
    }

    @Test
    fun `Completa carrega o ObjectiveScore, e so ele`() {
        val apuracao: ApuracaoParaEnvio = ApuracaoParaEnvio.Completa(completa)

        assertEquals(completa, (apuracao as ApuracaoParaEnvio.Completa).score)
        assertEquals("completa:1", descricao(apuracao))
    }

    @Test
    fun `Parcial carrega a PartialScore, e so ela`() {
        val apuracao: ApuracaoParaEnvio = ApuracaoParaEnvio.Parcial(parcial)

        assertEquals(parcial, (apuracao as ApuracaoParaEnvio.Parcial).score)
        assertEquals("parcial:1", descricao(apuracao))
    }
}
