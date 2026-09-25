package com.platos.domain.layout

import com.platos.domain.exam.ExamDefinition
import com.platos.domain.exam.StatementSegment
import com.platos.domain.exam.parseStatement
import com.platos.domain.exam.Question
import com.platos.domain.exam.QuestionKind
import com.platos.domain.geometry.Um
import com.platos.domain.text.MeasuredText
import com.platos.domain.text.TextMeasurer
import com.platos.domain.text.TextPiece
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
/**
 * A parte discursiva de um bloco: quanto do bloco e enunciado, e quanto e regiao.
 *
 * **Calculado uma vez, aqui, e so lido por quem desenha.** A altura reservada para o bloco e a altura
 * desenhada da regiao saem destes dois numeros; se o motor recalculasse a regiao a partir da rubrica,
 * reserva e desenho divergiriam em silencio — o mesmo defeito que `advanceAfterStatement` existe para
 * impedir entre enunciado e alternativas.
 */
data class EssayContent(
    /**
     * O numero de linhas que o professor declarou (ADR-0017). Ate a
     * `slice-5b-0-a-regiao-discursiva-compacta` era a soma dos `expected_lines` da rubrica (D35).
     */
    val lines: Int,
    /** Do topo do bloco ate o topo da regiao: o enunciado e o respiro, na grade. */
    val statementHeight: Um,
    /** Altura da regiao, na grade: faixa do QR, moldura, folga da escrita e marcador de baixo. */
    val regionHeight: Um,
)

data class QuestionContent(
    val questionId: String,
    val number: Int,
    val statement: MeasuredText,
    val formula: FormulaContent?,
    val options: List<OptionContent>,
    val block: Block,
    /** Nulo na objetiva. */
    val essay: EssayContent? = null,
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
    private val profile: LayoutProfile = LayoutProfile.DEFAULT,
) {

    private val style: TextStyle get() = profile.style

    /** Largura util do texto dentro da coluna, depois da canaleta do numero. */
    val textWidth: Um get() = textWidth(profile)

    /** Largura util do texto de uma alternativa, depois do recuo da letra. */
    val optionWidth: Um get() = optionWidth(profile)

    fun build(exam: ExamDefinition): List<QuestionContent> =
        exam.questions.mapIndexed { index, question -> build(question, index + 1) }

    fun build(question: Question, number: Int): QuestionContent {
        val statement = measurer.measure(piecesOf(question), style, textWidth)
        val options = question.options.mapIndexed { index, text ->
            OptionContent(
                letter = 'A' + index,
                text = measurer.measure(text, style, optionWidth),
            )
        }
        val formula = question.formula?.let { declared ->
            val width = Um(declared.width)
            // A largura e o unico limite duro: uma formula mais larga que a coluna nao tem como
            // caber sem reescalar, e reescalar e o que a spec proibe. Falhar aqui e o certo — a
            // alternativa seria uma folha impressa com a formula invadindo a coluna vizinha.
            if (width > textWidth) {
                throw LayoutException(
                    "formula `${declared.reference}` da questao `${question.id}` tem $width de " +
                        "largura e nao cabe na coluna, que oferece $textWidth; a formula nao e " +
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

        val essay = essayContentOf(question, statement, formula)
        val content = if (essay != null) {
            essay.statementHeight + essay.regionHeight
        } else {
            statement.height +
                advanceAfterStatement(formula, style) +
                options.fold(Um.ZERO) { total, option -> total + option.text.height }
        }
        val block = Block(
            id = question.id,
            height = profile.snapToGrid(content + SPACE_AFTER_BLOCK),
        )

        return QuestionContent(
            questionId = question.id,
            number = number,
            statement = statement,
            formula = formula,
            options = options,
            block = block,
            essay = essay,
        )
    }

    /**
     * A parte discursiva do bloco, ou nulo na objetiva.
     *
     * A altura da moldura e o numero de linhas declarado vezes a pauta de 7 mm (ADR-0016, ADR-0017),
     * e as duas partes do bloco vao para a grade de 3 mm separadamente: o topo da regiao cai na grade, e com ele os
     * marcadores e o QR — posicoes inteiras de grade sao o que mantem o layout uma funcao pura sobre
     * inteiros (D-1.2).
     */
    private fun essayContentOf(
        question: Question,
        statement: MeasuredText,
        formula: FormulaContent?,
    ): EssayContent? {
        if (question.kind != QuestionKind.ESSAY) return null
        // `requireSupported` ja recusou discursiva sem linhas; aqui elas so sao lidas. A rubrica nao
        // entra: os `expected_lines` dela sao informacao de correcao, e nao geometria (ADR-0017).
        val lines = requireNotNull(question.answerLines) { "discursiva `${question.id}` sem linhas" }
        return EssayContent(
            lines = lines,
            statementHeight = profile.snapToGrid(
                statement.height + advanceAfterStatement(formula, style),
            ),
            regionHeight = profile.snapToGrid(
                EssayGeometry.TOP_BAND + EssayGeometry.PAUTA * lines + EssayGeometry.BOTTOM_CLEARANCE,
            ),
        )
    }

    /**
     * Converte o enunciado em pedacos para a medicao, resolvendo as formulas em linha.
     *
     * Sempre passa pelo parser, mesmo quando a questao nao declara formula nenhuma: e o parser
     * que resolve o escape `\\{{`, e curto-circuitar aqui deixaria a barra na folha de quem
     * escreveu chave como texto. Para as questoes sem marcador o resultado e um unico trecho de
     * texto com a mesma string — que e por que o golden nao muda.
     */
    private fun piecesOf(question: Question): List<TextPiece> {
        val porReferencia = question.inline.associateBy { it.reference }
        return parseStatement(question.statement).map { segment ->
            when (segment) {
                is StatementSegment.Text -> TextPiece.Words(segment.text)
                is StatementSegment.Formula -> {
                    val formula = porReferencia.getValue(segment.reference)
                    val height = Um(formula.height)
                    // D-1.6.4: o teto existe para o autor descobrir agora, e nao na impressao.
                    // Sem ele, uma matriz 3x3 no meio de um paragrafo abriria uma linha de
                    // 15 mm cercada de linhas de 4,7 mm, sem ninguem avisar.
                    if (height > profile.inlineHeightCeiling) {
                        throw LayoutException(
                            "formula em linha `${formula.reference}` da questao " +
                                "`${question.id}` tem $height de altura e excede o teto de " +
                                "linha do perfil `${profile.id}`, que e " +
                                "${profile.inlineHeightCeiling}; use a forma em bloco",
                        )
                    }
                    TextPiece.Box(
                        reference = formula.reference,
                        width = Um(formula.width),
                        height = height,
                        baselineOffset = Um(formula.baselineOffset),
                    )
                }
            }
        }
    }

    companion object {
        /** Canaleta esquerda onde o numero da questao fica pendurado (§7). */
        val NUMBER_GUTTER = Um.mm(8)

        /** Recuo extra do texto da alternativa, depois da letra. */
        val OPTION_INDENT = Um.mm(6)

        val SPACE_AFTER_STATEMENT = Um.mm(3)

        /**
         * A transicao que a folha ja faz entre o fim do enunciado e a primeira alternativa.
         *
         * **Ancoradouro unico dos dois vaos da formula.** Nenhum deles e um valor proprio: sao
         * multiplos deste, entao mexer na entrelinha ou em [SPACE_AFTER_STATEMENT] move os dois
         * juntos e eles nao tem como divergir com o tempo.
         */
        fun textTransition(style: TextStyle): Um = style.lineHeight + SPACE_AFTER_STATEMENT

        /**
         * Branco acima da formula: 45% da transicao de texto.
         *
         * Ancorado direto na base, e **nao** como fracao do vao de baixo. Pendurado no de baixo,
         * qualquer ajuste la arrastava o de cima junto — e os dois respondem a criterios
         * diferentes: o de cima e proximidade com o enunciado, o de baixo e separacao das
         * alternativas.
         */
        fun spaceAboveFormula(style: TextStyle): Um =
            (textTransition(style) * 45).divFloor(100)

        /**
         * Branco abaixo da formula: 4/3 da transicao de texto.
         *
         * A primeira versao de D-1.5.9 usava a transicao **inteira**, com o argumento de que
         * formula -> alternativas e a mesma transicao que enunciado -> alternativas. A segunda
         * impressao pediu mais separacao, e o argumento estava bom demais para ser verdade: uma
         * formula e bloco de exibicao, nao linha de texto, e bloco de exibicao precisa de mais ar
         * embaixo do que uma linha precisa. A igualdade era elegante; o papel discordou.
         *
         * O 4/3 e do vao de **tinta**, que e o que se ve: ele entrega +49,6% de branco visivel.
         * Multiplicar o nominal por 3/2 daria +74%, porque a ascendente da alternativa e uma
         * subtracao fixa e a proporcao nao sobrevive a conversao nominal -> tinta.
         */
        fun spaceBelowFormula(style: TextStyle): Um =
            (textTransition(style) * 4).divFloor(3)

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

        /**
         * Largura util do texto na coluna do perfil.
         *
         * Deixou de ser constante junto com `Sheet` (D-1.6.5): a largura da coluna e do perfil,
         * entao a largura do texto tambem tem de ser.
         */
        fun textWidth(profile: LayoutProfile): Um = profile.columnWidth - NUMBER_GUTTER

        fun optionWidth(profile: LayoutProfile): Um = textWidth(profile) - OPTION_INDENT
    }
}
