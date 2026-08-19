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
 * A caixa que a formula em bloco ocupa, e o branco de cada lado dela (D-1.5.9).
 *
 * [width] e [height] sao exatamente as dimensoes declaradas — a formula **nao** e reescalada nem
 * deformada.
 *
 * [spaceAbove] e [spaceBelow] sao assimetricos de proposito, e o argumento e semantico e nao
 * estetico: a formula e parte do **enunciado**, entao precisa ler colada nele e separada das
 * alternativas. Com espacos iguais — ou, como estava antes, com mais branco em cima —, a
 * proximidade diz o contrario do que a estrutura diz, e a folha impressa foi reprovada por isso.
 *
 * Nao ha arredondamento a grade aqui. Quem precisa cair na grade de 3 mm e o **bloco**, e ele ja
 * cai em [QuestionBlockBuilder.build]. Arredondar tambem a formula era redundante, e o residuo
 * desse arredondamento caia todo abaixo dela — era ele que fazia o vao inferior variar 2,2 mm
 * entre as doze formulas da fixture enquanto o superior ficava fixo.
 */
data class FormulaContent(
    val reference: String,
    val width: Um,
    val height: Um,
    val spaceAbove: Um,
    val spaceBelow: Um,
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
            FormulaContent(
                reference = declared.reference,
                width = width,
                height = Um(declared.height),
                spaceAbove = spaceAboveFormula(style),
                spaceBelow = spaceBelowFormula(style),
            )
        }

        val content = statement.height +
            advanceAfterStatement(formula, style) +
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

        /**
         * Branco abaixo da formula: **a mesma transicao** que a folha ja faz entre o fim do
         * enunciado e a primeira alternativa, pelas mesmas duas constantes e na mesma ordem.
         *
         * Nao e um valor novo, e isso e o ponto. A transicao formula -> alternativas e a mesma
         * transicao enunciado -> alternativas; se fossem dois valores, divergiriam com o tempo.
         * Sendo derivado, nao pode divergir.
         */
        fun spaceBelowFormula(style: TextStyle): Um = style.lineHeight + SPACE_AFTER_STATEMENT

        /**
         * Branco acima da formula: 45% do de baixo.
         *
         * Heuristica de proximidade: para dois grupos lerem como grupos distintos, o espaco entre
         * eles precisa ser ao menos o dobro do espaco dentro de cada um. 45% da 2,2x, dentro da
         * faixa segura sem gastar papel. Como e uma **fracao** do de baixo, e nao um valor proprio,
         * um perfil tipografico mais compacto encolhe os dois juntos e a razao sobrevive por
         * construcao — nao e preciso piso para proteger isso.
         */
        fun spaceAboveFormula(style: TextStyle): Um =
            (spaceBelowFormula(style) * 45).divFloor(100)

        /**
         * O avanco entre o fim do enunciado e a linha de base da primeira alternativa.
         *
         * **Este e o unico ponto do layout que converte linha de base em topo**, e existir uma vez
         * so e deliberado: o defeito que D-1.5.9 corrige nasceu de a conversao estar implicita e
         * espalhada. O laco do enunciado deixa o cursor uma entrelinha adiante da ultima linha —
         * avanco que um texto seguinte consome com a ascendente, mas que um elemento posicionado
         * pelo **topo** transforma em branco puro. Descontar [TextStyle.lineHeight] aqui e o que
         * impede isso de voltar quando a fatia 1.6 puser o segundo elemento posicionado por topo
         * na folha.
         */
        fun advanceAfterStatement(formula: FormulaContent?, style: TextStyle): Um =
            if (formula == null) {
                SPACE_AFTER_STATEMENT
            } else {
                formula.spaceAbove - style.lineHeight + formula.height + formula.spaceBelow
            }

        /** Respiro entre questoes, dois passos da grade. */
        val SPACE_AFTER_BLOCK = Um.mm(6)

        val TEXT_WIDTH = Sheet.COLUMN_WIDTH - NUMBER_GUTTER
        val OPTION_WIDTH = TEXT_WIDTH - OPTION_INDENT
    }
}
