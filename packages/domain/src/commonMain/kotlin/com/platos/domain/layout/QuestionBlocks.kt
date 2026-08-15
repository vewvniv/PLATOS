package com.platos.domain.layout

import com.platos.domain.exam.ExamDefinition
import com.platos.domain.exam.Question
import com.platos.domain.geometry.Um
import com.platos.domain.text.MeasuredText
import com.platos.domain.text.TextMeasurer
import com.platos.domain.text.TextStyle

/** Uma alternativa ja medida. */
data class OptionContent(
    val letter: Char,
    val text: MeasuredText,
)

/**
 * Uma questao medida e pronta para posicionar.
 *
 * Enunciado e alternativas viajam juntos: e isto que faz o bloco ser indivisivel (D34). Nenhum
 * consumidor pode posicionar as alternativas sem o enunciado.
 */
data class QuestionContent(
    val questionId: String,
    val number: Int,
    val statement: MeasuredText,
    val options: List<OptionContent>,
    val block: Block,
)

/**
 * Mede questoes e as agrupa em blocos indivisiveis (§7).
 *
 * O numero fica pendurado na canaleta esquerda; enunciado e alternativas ocupam a largura restante
 * da coluna. A altura do bloco ja sai alinhada a grade de 3 mm, com o respiro entre questoes
 * incluido: assim a paginacao so soma inteiros e nunca precisa saber de tipografia.
 */
class QuestionBlockBuilder(
    private val measurer: TextMeasurer,
    private val style: TextStyle = TextStyle.BODY,
) {

    fun build(exam: ExamDefinition): List<QuestionContent> =
        exam.questions.mapIndexed { index, question -> build(question, index + 1) }

    fun build(question: Question, number: Int): QuestionContent {
        val statement = measurer.measure(question.statement, style, TEXT_WIDTH)
        val options = question.options.mapIndexed { index, text ->
            OptionContent(
                letter = 'A' + index,
                text = measurer.measure(text, style, OPTION_WIDTH),
            )
        }

        val content = statement.height +
            SPACE_AFTER_STATEMENT +
            options.fold(Um.ZERO) { total, option -> total + option.text.height }
        val block = Block(
            id = question.id,
            height = snapToGrid(content + SPACE_AFTER_BLOCK),
        )

        return QuestionContent(
            questionId = question.id,
            number = number,
            statement = statement,
            options = options,
            block = block,
        )
    }

    companion object {
        /** Canaleta esquerda onde o numero da questao fica pendurado (§7). */
        val NUMBER_GUTTER = Um.mm(8)

        /** Recuo extra do texto da alternativa, depois da letra. */
        val OPTION_INDENT = Um.mm(6)

        val SPACE_AFTER_STATEMENT = Um.mm(3)

        /** Respiro entre questoes, dois passos da grade. */
        val SPACE_AFTER_BLOCK = Um.mm(6)

        val TEXT_WIDTH = Sheet.COLUMN_WIDTH - NUMBER_GUTTER
        val OPTION_WIDTH = TEXT_WIDTH - OPTION_INDENT
    }
}
