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
 * A caixa que a formula em bloco ocupa, ja alinhada a grade.
 *
 * [width] e [height] sao exatamente as dimensoes declaradas — a formula **nao** e reescalada nem
 * deformada. Quem sobe para o proximo multiplo da grade e [reserved], o espaco que o bloco gasta;
 * a formula continua desenhada no seu tamanho, com a sobra virando respiro.
 */
data class FormulaContent(
    val reference: String,
    val width: Um,
    val height: Um,
    val reserved: Um,
)

/**
 * Uma questao medida e pronta para posicionar.
 *
 * Enunciado, formula e alternativas viajam juntos: e isto que faz o bloco ser indivisivel (D34).
 * Nenhum consumidor pode posicionar as alternativas sem o enunciado.
 */
data class QuestionContent(
    val questionId: String,
    val number: Int,
    val statement: MeasuredText,
    val formula: FormulaContent?,
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
        val formula = question.formula?.let { declared ->
            val width = Um(declared.width)
            // A largura e o unico limite duro: uma formula mais larga que a coluna nao tem como
            // caber sem reescalar, e reescalar e o que a spec proibe. Falhar aqui e o certo — a
            // alternativa seria uma folha impressa com a formula invadindo a coluna vizinha.
            if (width > TEXT_WIDTH) {
                throw LayoutException(
                    "formula `${declared.reference}` da questao `${question.id}` tem $width de " +
                        "largura e nao cabe na coluna, que oferece $TEXT_WIDTH; a formula nao e " +
                        "reescalada e nenhum layout e emitido",
                )
            }
            val height = Um(declared.height)
            FormulaContent(
                reference = declared.reference,
                width = width,
                height = height,
                // Sobe ao proximo multiplo da grade sem comprimir a formula: o espaco reservado
                // cresce, as dimensoes desenhadas continuam as declaradas.
                reserved = snapToGrid(height + SPACE_AROUND_FORMULA * 2),
            )
        }

        val content = statement.height +
            SPACE_AFTER_STATEMENT +
            (formula?.reserved ?: Um.ZERO) +
            options.fold(Um.ZERO) { total, option -> total + option.text.height }
        val block = Block(
            id = question.id,
            height = snapToGrid(content + SPACE_AFTER_BLOCK),
        )

        return QuestionContent(
            questionId = question.id,
            number = number,
            statement = statement,
            formula = formula,
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

        /** Respiro acima e abaixo da formula, um passo da grade de cada lado. */
        val SPACE_AROUND_FORMULA = Um.mm(3)

        /** Respiro entre questoes, dois passos da grade. */
        val SPACE_AFTER_BLOCK = Um.mm(6)

        val TEXT_WIDTH = Sheet.COLUMN_WIDTH - NUMBER_GUTTER
        val OPTION_WIDTH = TEXT_WIDTH - OPTION_INDENT
    }
}
