package com.platos.domain.layout

import com.platos.domain.capture.CaptureGeometry
import com.platos.domain.capture.QrEncoder
import com.platos.domain.exam.ExamDefinition
import com.platos.domain.exam.requireSupported
import com.platos.domain.geometry.Ppm
import com.platos.domain.geometry.Um
import com.platos.domain.text.EmbeddedFont
import com.platos.domain.text.LineRun
import com.platos.domain.text.TextMeasurer
import com.platos.domain.text.TextStyle

/** Como as bolhas do gabarito se distribuem em colunas e linhas. */
private data class BubbleGrid(
    val columns: Int,
    val rows: Int,
    val optionCount: Int,
    val columnWidth: Um,
) {
    val height: Um get() = CaptureGeometry.BUBBLE_PITCH_V * rows
    val width: Um
        get() = columnWidth * columns + CaptureGeometry.BUBBLE_COLUMN_GAP * (columns - 1)
}

/**
 * Calcula o `LayoutMap` de uma prova objetiva.
 *
 * Funcao pura: mesma entrada, mesmo mapa, em qualquer alvo e em qualquer execucao. Nao le relogio,
 * nao sorteia e nao consulta plataforma — e o que permite comparar dois mapas byte a byte.
 */
class LayoutEngine(
    private val measurer: TextMeasurer = TextMeasurer(EmbeddedFont.program),
    private val profile: LayoutProfile = LayoutProfile.DEFAULT,
) {

    private val style: TextStyle get() = profile.style

    fun layout(exam: ExamDefinition): LayoutMap {
        exam.requireSupported()

        val grid = gridFor(exam)
        val regionHeight = profile.snapToGrid(TOP_BAND + grid.height + BOTTOM_CLEARANCE)

        val contents = QuestionBlockBuilder(measurer, profile).build(exam)
        val pagination = Paginator(
            profile = profile,
            reservedOnFirstPage = regionHeight + SPACE_AFTER_REGION,
        ).paginate(contents.map { it.block })

        val byId = contents.associateBy { it.questionId }
        val primitivesByPage = mutableMapOf<Int, MutableList<Primitive>>()
        fun page(index: Int) = primitivesByPage.getOrPut(index) { mutableListOf() }

        val region = buildAnswerBlock(exam, grid, regionHeight, page(0))

        for (placement in pagination.placements) {
            val content = byId.getValue(placement.blockId)
            emitQuestion(content, placement, page(placement.page))
        }

        val pages = (0 until pagination.pageCount).map { index ->
            Page(index = index, primitives = primitivesByPage[index].orEmpty().toList())
        }

        return LayoutMap(
            layoutEngineVersion = LayoutMap.ENGINE_VERSION,
            minRendererVersion = LayoutMap.MIN_RENDERER_VERSION,
            examId = exam.id,
            pageWidth = profile.pageWidth.raw,
            pageHeight = profile.pageHeight.raw,
            fontSha256 = EmbeddedFont.sha256,
            pages = pages,
            regions = listOf(region),
        )
    }

    /**
     * Escolhe o menor numero de colunas de bolhas que cabe na altura maxima da regiao.
     *
     * Menos colunas deixa a regiao mais estreita e as linhas mais longas, que e o que §7 quer para
     * o gabarito consolidado; a altura e que manda, porque ela come a pagina 1.
     */
    private fun gridFor(exam: ExamDefinition): BubbleGrid {
        val questionCount = exam.questions.size
        val optionCount = exam.questions.maxOf { it.options.size }
        val columnWidth = CaptureGeometry.LABEL_WIDTH + CaptureGeometry.BUBBLE_PITCH_H * optionCount

        val availableWidth = profile.contentWidth -
            (CaptureGeometry.MARKER_SIDE + CaptureGeometry.QUIET_ZONE) * 2
        val availableHeight = CaptureGeometry.MAX_REGION_HEIGHT - TOP_BAND - BOTTOM_CLEARANCE

        for (columns in 1..MAX_BUBBLE_COLUMNS) {
            val rows = (questionCount + columns - 1) / columns
            val candidate = BubbleGrid(columns, rows, optionCount, columnWidth)
            if (candidate.height <= availableHeight && candidate.width <= availableWidth) {
                return candidate
            }
        }
        throw LayoutException(
            "gabarito de $questionCount questoes com $optionCount alternativas nao cabe na " +
                "regiao escaneavel; o teto e ${CaptureGeometry.MAX_REGION_HEIGHT}",
        )
    }

    private fun buildAnswerBlock(
        exam: ExamDefinition,
        grid: BubbleGrid,
        regionHeight: Um,
        primitives: MutableList<Primitive>,
    ): ScannableRegion {
        val left = profile.marginSide
        val top = profile.marginTop
        val right = left + profile.contentWidth
        val bottom = top + regionHeight
        val marker = CaptureGeometry.MARKER_SIDE

        // O quadrilatero e formado pelos centros dos quatro marcadores: e o ponto que a deteccao
        // devolve com mais estabilidade, e e a ele que tudo dentro da regiao e normalizado.
        val quadX = left + marker.divFloor(2)
        val quadY = top + marker.divFloor(2)
        val quadWidth = profile.contentWidth - marker
        val quadHeight = regionHeight - marker

        val markerIds = CaptureGeometry.markerIdsOf(REGION_INDEX)
        val corners = listOf(
            left to top,
            (right - marker) to top,
            left to (bottom - marker),
            (right - marker) to (bottom - marker),
        )
        for ((index, corner) in corners.withIndex()) {
            val markerId = markerIds[index]
            primitives += DrawAruco(
                id = "r$REGION_INDEX-m$markerId",
                markerId = markerId,
                x = corner.first.raw,
                y = corner.second.raw,
                side = marker.raw,
                module = CaptureGeometry.MARKER_MODULE.raw,
                modules = CaptureGeometry.markerModules(markerId),
            )
        }

        val payload = qrPayloadOf(exam.id, REGION_INDEX)
        val qrMatrix = QrEncoder.encode(payload)
        val qrSide = CaptureGeometry.QR_SIDE
        val qrX = left + (profile.contentWidth - qrSide).divFloor(2)
        // O QR comeca na linha do quadrilatero, e nao no topo da regiao: o quadrilatero passa pelos
        // *centros* dos marcadores, entao qualquer coisa acima dele normalizaria para v negativo.
        val qrY = quadY
        primitives += DrawQr(
            id = "r$REGION_INDEX-qr",
            x = qrX.raw,
            y = qrY.raw,
            side = qrSide.raw,
            module = qrSide.divFloor(qrMatrix.size).raw,
            payload = payload,
            modules = qrMatrix.modules.map { row -> row.joinToString("") { if (it) "1" else "0" } },
        )

        val bubbleLeft = left + marker + CaptureGeometry.QUIET_ZONE
        val bubbleTop = qrY + qrSide + CaptureGeometry.QUIET_ZONE
        val radius = CaptureGeometry.BUBBLE_DIAMETER.divFloor(2)
        val bubbles = mutableListOf<Bubble>()

        exam.questions.forEachIndexed { index, question ->
            val column = index / grid.rows
            val row = index % grid.rows
            val columnX = bubbleLeft +
                (grid.columnWidth + CaptureGeometry.BUBBLE_COLUMN_GAP) * column
            val rowY = bubbleTop + CaptureGeometry.BUBBLE_PITCH_V * row

            primitives += DrawText(
                id = "r$REGION_INDEX-n${question.id}",
                x = columnX.raw,
                baseline = (rowY + CaptureGeometry.BUBBLE_DIAMETER).raw,
                size = style.size.raw,
                text = "${index + 1}",
            )

            question.options.forEachIndexed { optionIndex, _ ->
                val centerX = columnX + CaptureGeometry.LABEL_WIDTH +
                    CaptureGeometry.BUBBLE_PITCH_H * optionIndex + radius
                val centerY = rowY + radius
                val letter = ('A' + optionIndex).toString()

                primitives += DrawCircle(
                    id = "r$REGION_INDEX-b${question.id}-$letter",
                    centerX = centerX.raw,
                    centerY = centerY.raw,
                    diameter = CaptureGeometry.BUBBLE_DIAMETER.raw,
                    stroke = CaptureGeometry.BUBBLE_STROKE.raw,
                )
                bubbles += Bubble(
                    questionId = question.id,
                    option = letter,
                    u = Ppm.of(centerX - quadX, quadWidth).raw,
                    v = Ppm.of(centerY - quadY, quadHeight).raw,
                )
            }
        }

        return ScannableRegion(
            index = REGION_INDEX,
            kind = "answer_block",
            page = 0,
            quadX = quadX.raw,
            quadY = quadY.raw,
            quadWidth = quadWidth.raw,
            quadHeight = quadHeight.raw,
            markerIds = markerIds,
            qr = NormalizedRect(
                u = Ppm.of(qrX - quadX, quadWidth).raw,
                v = Ppm.of(qrY - quadY, quadHeight).raw,
                uSize = Ppm.of(qrSide, quadWidth).raw,
                vSize = Ppm.of(qrSide, quadHeight).raw,
            ),
            bubbles = bubbles,
        )
    }

    private fun emitQuestion(
        content: QuestionContent,
        placement: Placement,
        primitives: MutableList<Primitive>,
    ) {
        val columnLeft = profile.columnLeft(placement.column)
        val textLeft = columnLeft + QuestionBlockBuilder.NUMBER_GUTTER
        var baseline = placement.top + style.lineHeight

        primitives += DrawText(
            id = "q${content.questionId}-n",
            x = columnLeft.raw,
            baseline = baseline.raw,
            size = style.size.raw,
            text = "${content.number}.",
        )

        // Uma linha pode ter mais de um trecho — texto e caixa — e todos compartilham a **mesma**
        // linha de base (D-1.6.3). O cursor anda pelo topo das linhas; a linha de base de cada uma
        // sai da ascendente dela, e nao de uma entrelinha fixa, porque uma linha com formula e mais
        // alta que as vizinhas.
        var cursor = placement.top
        var lastBaseline = placement.top
        for ((index, line) in content.statement.lines.withIndex()) {
            cursor += line.ascent
            lastBaseline = cursor
            for ((runIndex, run) in line.runs.withIndex()) {
                // Uma linha com trecho unico de texto mantem o identificador de sempre. Sem isso,
                // toda questao da fixture mudaria de identificador e o golden desta fatia
                // misturaria a formula em linha com uma renomeacao em massa.
                val suffix = if (line.runs.size == 1) "$index" else "$index-$runIndex"
                when (run) {
                    is LineRun.Text -> primitives += DrawText(
                        id = "q${content.questionId}-s$suffix",
                        x = (textLeft + run.x).raw,
                        baseline = lastBaseline.raw,
                        size = style.size.raw,
                        text = run.text,
                    )

                    is LineRun.Box -> primitives += DrawImage(
                        id = "q${content.questionId}-si$suffix",
                        x = (textLeft + run.x).raw,
                        // A unica conversao linha-de-base -> topo do desenho em linha: o que fica
                        // acima da linha de base e `height - baselineOffset`.
                        y = (lastBaseline - (run.height - run.baselineOffset)).raw,
                        width = run.width.raw,
                        height = run.height.raw,
                        reference = run.reference,
                    )
                }
            }
            cursor += line.descent
        }
        // `advanceAfterStatement` conta a partir da linha fantasma — uma entrelinha alem da ultima
        // linha de base —, que e o que um texto seguinte consumiria com a ascendente dele.
        baseline = lastBaseline + style.lineHeight
        // A formula fica entre a ultima linha do enunciado e a primeira alternativa. O engine nao
        // sabe o que ha dentro dela: posiciona a caixa que a conversao mediu e segue (D-1.5.3).
        //
        // O avanco ate a primeira alternativa vem de `advanceAfterStatement`, o mesmo calculo que
        // `QuestionBlockBuilder` usa para dimensionar o bloco. Se fossem dois calculos, altura
        // reservada e altura desenhada divergiriam em silencio — e a divergencia so apareceria na
        // folha impressa, que e onde este arquivo nao pode errar.
        val formula = content.formula
        if (formula != null) {
            // Unica conversao linha-de-base -> topo: a formula e posicionada pelo topo, entao a
            // entrelinha que o laco acima ja avancou nao seria consumida por ascendente nenhuma.
            val ultimaBaseline = baseline - style.lineHeight
            val topo = ultimaBaseline + formula.spaceAbove
            primitives += DrawImage(
                id = "q${content.questionId}-f",
                x = textLeft.raw,
                y = topo.raw,
                width = formula.width.raw,
                height = formula.height.raw,
                reference = formula.reference,
            )
        }
        baseline += QuestionBlockBuilder.advanceAfterStatement(formula, style)

        for (option in content.options) {
            primitives += DrawText(
                id = "q${content.questionId}-o${option.letter}",
                x = textLeft.raw,
                baseline = baseline.raw,
                size = style.size.raw,
                text = "(${option.letter})",
            )
            for ((index, line) in option.text.lines.withIndex()) {
                primitives += DrawText(
                    id = "q${content.questionId}-o${option.letter}-$index",
                    x = (textLeft + QuestionBlockBuilder.OPTION_INDENT).raw,
                    baseline = baseline.raw,
                    size = style.size.raw,
                    text = line.text,
                )
                baseline += style.lineHeight
            }
        }
    }

    companion object {
        private const val REGION_INDEX = 0
        private const val MAX_BUBBLE_COLUMNS = 6
        private val SPACE_AFTER_REGION = Um.mm(6)

        /**
         * Do topo da regiao ate a primeira linha de bolhas.
         *
         * Meio marcador desce ate a linha do quadrilatero — tudo acima dela normalizaria negativo —
         * e o QR ocupa a faixa seguinte, com a zona de silencio abaixo.
         */
        private val TOP_BAND = CaptureGeometry.MARKER_SIDE.divFloor(2) +
            CaptureGeometry.QR_SIDE + CaptureGeometry.QUIET_ZONE

        /** Da ultima linha de bolhas ate o fim da regiao, livrando os marcadores de baixo. */
        private val BOTTOM_CLEARANCE = CaptureGeometry.MARKER_SIDE + CaptureGeometry.QUIET_ZONE

        /**
         * Payload do QR da regiao.
         *
         * §8 define `{exam_short_id}.{student_token}.{variant}.{region_idx}.{crc}`. Nesta fatia nao
         * existem `exam_assignment` nem variantes — sao fatia 7 —, entao os dois campos ausentes
         * ficam vazios em vez de inventados, e a forma do payload ja e a definitiva.
         */
        fun qrPayloadOf(examId: String, regionIndex: Int): String {
            val body = "$examId...$regionIndex"
            return "$body.${crc16(body)}"
        }

        /** CRC-16/CCITT-FALSE, para que o QR detecte leitura corrompida (D6). */
        private fun crc16(text: String): String {
            var crc = 0xFFFF
            for (byte in text.encodeToByteArray()) {
                crc = crc xor ((byte.toInt() and 0xFF) shl 8)
                repeat(8) {
                    crc = if (crc and 0x8000 != 0) {
                        ((crc shl 1) xor 0x1021) and 0xFFFF
                    } else {
                        (crc shl 1) and 0xFFFF
                    }
                }
            }
            return crc.toString(16).uppercase().padStart(4, '0')
        }
    }
}
