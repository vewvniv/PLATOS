package com.platos.domain.layout

import com.platos.domain.geometry.Um
import com.platos.domain.text.TextStyle

/**
 * Geometria da folha e tipografia, como parametro em vez de constante (D-1.6.5).
 *
 * Antes desta fatia isto era um `object Sheet` com constantes e um `TextStyle.BODY` fixo. Prova em
 * fonte ampliada e exigencia real de escola, e a fatia 2a vai congelar geometria em `ExamPackage`
 * hasheado: o que fica caro depois nao e o parametro, e a **ausencia** dele no artefato publicado.
 * ADR-0004 separa as duas metades, e esta e a de ca.
 *
 * Duas leituras de §7 continuam fixadas aqui, como estavam no `Sheet`:
 *
 * - **Grampo.** §7 lista "margens 15 mm laterais e inferior / 14 mm superior, 8 mm reservados para
 *   grampo". Os 8 mm sao faixa de exclusao *dentro* da margem superior de 14 mm, e nao reserva
 *   somada a ela — e a leitura que mantem 8 < 14 coerente e nao consome altura util.
 * - **Medianiz.** §7 fixa duas colunas e nao o espaco entre elas. Adotados 6 mm, dois passos da
 *   grade, para nao quebrar o ritmo vertical.
 *
 * **A geometria de captura nao esta aqui, e isso e decisao.** Diametro de bolha, passo e marcador
 * sao o que o ADR-0001 dimensionou contra reescala de impressora — tres delas medidas entre −3,4%
 * e +4,7%. Torna-los variaveis sem evidencia de captura sob outra escala trocaria uma garantia
 * medida por um parametro nao verificado.
 */
data class LayoutProfile(
    /** Identifica o perfil no `LayoutMap`. A fatia 2a o declara no artefato publicado (ADR-0004). */
    val id: String,
    val pageWidth: Um,
    val pageHeight: Um,
    val marginTop: Um,
    val marginBottom: Um,
    val marginSide: Um,
    /** Faixa superior onde o grampo entra e nada e desenhado. */
    val stapleReserve: Um,
    val gutter: Um,
    val columns: Int,
    /** Passo da grade vertical (§7): toda altura de bloco e multiplo dela. */
    val grid: Um,
    val style: TextStyle,
    /**
     * Altura maxima de uma formula **em linha** (D-1.6.4).
     *
     * Existe para o autor descobrir cedo. Sem teto, uma matriz 3x3 no meio de um paragrafo
     * produziria uma linha de 15 mm cercada de linhas de 4,7 mm, e ninguem avisaria: ele
     * descobriria na impressao, que e o modo de falha mais caro desta base — foi ele que reprovou
     * a folha da fatia 1.5.
     */
    val inlineHeightCeiling: Um,
) {

    init {
        require(columns > 0) { "perfil `$id` precisa de ao menos uma coluna" }
        require(grid > Um.ZERO) { "perfil `$id` precisa de grade positiva" }
        require(stapleReserve <= marginTop) {
            "perfil `$id`: o grampo ($stapleReserve) nao cabe dentro da margem superior ($marginTop)"
        }
        require(style.lineHeight > Um.ZERO) { "perfil `$id` precisa de entrelinha positiva" }
        require(inlineHeightCeiling >= style.lineHeight) {
            "perfil `$id`: o teto de linha ($inlineHeightCeiling) nao pode ser menor que a " +
                "entrelinha (${style.lineHeight}), senao nem texto comum caberia"
        }
    }

    /** Largura util entre as margens laterais. */
    val contentWidth: Um get() = pageWidth - marginSide * 2

    /** Largura de uma coluna. */
    val columnWidth: Um get() = (contentWidth - gutter).divFloor(columns)

    /** Altura util entre as margens, antes de descontar qualquer regiao. */
    val contentHeight: Um get() = pageHeight - marginTop - marginBottom

    /** Deslocamento horizontal do inicio de uma coluna. */
    fun columnLeft(column: Int): Um {
        require(column in 0 until columns) { "coluna fora da folha: $column" }
        return marginSide + (columnWidth + gutter) * column
    }

    /** Sobe [height] ate o proximo multiplo da grade deste perfil, sem comprimir conteudo. */
    fun snapToGrid(height: Um): Um = height.ceilToMultipleOf(grid)

    companion object {

        /**
         * O perfil vigente. Reproduz exatamente a geometria e a tipografia de antes de o perfil
         * existir — e ha teste afirmando que o golden nao muda por causa dele (D-1.6.6).
         */
        val DEFAULT = LayoutProfile(
            id = "a4-2col-9v5pt",
            pageWidth = Um.mm(210),
            pageHeight = Um.mm(297),
            marginTop = Um.mm(14),
            marginBottom = Um.mm(15),
            marginSide = Um.mm(15),
            stapleReserve = Um.mm(8),
            gutter = Um.mm(6),
            columns = 2,
            grid = Um.mm(3),
            style = TextStyle.BODY,
            // Duas entrelinhas aceitam fracao, raiz, expoente e subscrito — o vocabulario real de
            // prova de ensino basico em linha — e recusam matriz e sistema, que a fatia 1.5 pos em
            // bloco justamente por serem altos.
            inlineHeightCeiling = TextStyle.BODY.lineHeight * 2,
        )
    }
}
