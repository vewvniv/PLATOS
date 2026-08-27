package com.platos.domain.capture

/**
 * O que a folha respondeu numa questao.
 *
 * Quatro casos, e nenhum deles e ausencia. Uma questao que sumisse da saida seria lida como "em
 * branco" pelo proximo passo, e "em branco" e uma afirmacao sobre o que o aluno fez — nao sobre o
 * que a leitura conseguiu apurar. As duas coisas so parecem iguais ate a nota.
 */
sealed interface QuestionAnswer {

    val questionId: String

    /** Exatamente uma bolha marcada. E a unica forma que vira acerto ou erro. */
    data class Marcada(override val questionId: String, val option: String) : QuestionAnswer

    /** Nenhuma bolha marcada e nenhuma indecisa. O aluno nao respondeu, e disso a leitura tem certeza. */
    data class EmBranco(override val questionId: String) : QuestionAnswer

    /**
     * Mais de uma bolha marcada.
     *
     * [options] nomeia todas, e nao a "vencedora": desempatar por cobertura transformaria rasura
     * em resposta, e a rasura e justamente o caso em que a folha nao diz o que o aluno quis.
     */
    data class MultiplaMarcacao(
        override val questionId: String,
        val options: List<String>,
    ) : QuestionAnswer

    /** Nenhuma marcada, e ao menos uma na faixa de indecisao. [options] nomeia as duvidosas. */
    data class Indecisa(
        override val questionId: String,
        val options: List<String>,
    ) : QuestionAnswer
}

/**
 * Agrupa bolhas julgadas em respostas por questao.
 *
 * Aritmetica de conjunto, sem imagem e sem limiar: o limiar ja foi aplicado por
 * [OmrThreshold.judge], e o que chega aqui sao vereditos. E o que permite testar os quatro casos
 * com vereditos montados a mao, sem passar por nenhum pixel.
 */
object AnswerSheet {

    /**
     * As respostas, na ordem em que as questoes aparecem no mapa.
     *
     * A ordem importa por uma razao pratica: e nela que a folha esta impressa, e e nela que uma
     * pendencia sera mostrada a quem revisar. `groupBy` do Kotlin preserva a ordem de encontro.
     */
    fun of(judgements: List<BubbleJudgement>): List<QuestionAnswer> =
        judgements.groupBy { it.measurement.questionId }
            .map { (questionId, bubbles) -> answerFor(questionId, bubbles) }

    private fun answerFor(questionId: String, bubbles: List<BubbleJudgement>): QuestionAnswer {
        val marcadas = bubbles.filter { it.verdict == BubbleVerdict.MARCADA }
        return when {
            marcadas.size == 1 -> QuestionAnswer.Marcada(questionId, marcadas.single().measurement.option)
            marcadas.size > 1 -> QuestionAnswer.MultiplaMarcacao(questionId, marcadas.options())
            else -> {
                // Sem nenhuma marcada, a duvida decide entre "nao respondeu" e "nao deu para saber".
                val indecisas = bubbles.filter { it.verdict == BubbleVerdict.INDECISA }
                if (indecisas.isEmpty()) {
                    QuestionAnswer.EmBranco(questionId)
                } else {
                    QuestionAnswer.Indecisa(questionId, indecisas.options())
                }
            }
        }
    }

    private fun List<BubbleJudgement>.options(): List<String> = map { it.measurement.option }
}
