package com.platos.domain.layout

import com.platos.domain.capture.CaptureGeometry
import com.platos.domain.capture.QrEncoder
import com.platos.domain.capture.linhasDeModulo
import com.platos.domain.capture.QrPayload
import com.platos.domain.exam.ExamDefinition
import com.platos.domain.exam.Question
import com.platos.domain.exam.QuestionKind
import com.platos.domain.exam.requireSupported
import com.platos.domain.geometry.Ppm
import com.platos.domain.geometry.Um
import com.platos.domain.text.EmbeddedFont
import com.platos.domain.text.LineRun
import com.platos.domain.text.MeasuredText
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
        val header = measureHeader(exam)

        val contents = QuestionBlockBuilder(measurer, profile).build(exam)
        val pagination = Paginator(
            profile = profile,
            reservedOnFirstPage = header.height + regionHeight + SPACE_AFTER_REGION,
        ).paginate(contents.map { it.block })

        val byId = contents.associateBy { it.questionId }
        val primitivesByPage = mutableMapOf<Int, MutableList<Primitive>>()
        fun page(index: Int) = primitivesByPage.getOrPut(index) { mutableListOf() }

        emitHeader(header, page(0))
        val region = buildAnswerBlock(
            exam = exam,
            grid = grid,
            regionHeight = regionHeight,
            top = profile.marginTop + header.height,
            primitives = page(0),
        )

        // O indice da regiao discursiva e a ordem da questao ENTRE AS DISCURSIVAS DA PROVA, e nao a
        // ordem de colocacao: o paginador decide onde o bloco cai, e nao que regiao ele e. `1` e a
        // primeira discursiva; `0` e sempre o gabarito (spec de `layout-engine`).
        val essayIndex = exam.questions
            .filter { it.kind == QuestionKind.ESSAY }
            .withIndex()
            .associate { (i, question) -> question.id to i + 1 }
        val essayRegions = mutableListOf<ScannableRegion>()

        for (placement in pagination.placements) {
            val content = byId.getValue(placement.blockId)
            emitQuestion(content, placement, page(placement.page))
            if (content.essay != null) {
                essayRegions += emitEssayRegion(
                    examId = exam.id,
                    regionIndex = essayIndex.getValue(content.questionId),
                    content = content,
                    placement = placement,
                    primitives = page(placement.page),
                )
            }
        }

        for (index in 0 until pagination.pageCount) {
            emitFooter(index, pagination.pageCount, page(index))
        }

        val pages = (0 until pagination.pageCount).map { index ->
            Page(index = index, primitives = primitivesByPage[index].orEmpty().toList())
        }

        return LayoutMap(
            layoutEngineVersion = LayoutMap.ENGINE_VERSION,
            minRendererVersion = LayoutMap.minRendererVersionOf(pages),
            examId = exam.id,
            pageWidth = profile.pageWidth.raw,
            pageHeight = profile.pageHeight.raw,
            fontSha256 = EmbeddedFont.sha256,
            profile = LayoutProfileRef(
                id = profile.id,
                bodySize = profile.style.size.raw,
                lineHeight = profile.style.lineHeight.raw,
                grid = profile.grid.raw,
            ),
            pages = pages,
            regions = listOf(region) + essayRegions.sortedBy { it.index },
        )
    }

    /**
     * Escolhe o menor numero de colunas de bolhas que cabe na altura maxima da regiao.
     *
     * Menos colunas deixa a regiao mais estreita e as linhas mais longas, que e o que §7 quer para
     * o gabarito consolidado; a altura e que manda, porque ela come a pagina 1.
     */
    private fun gridFor(exam: ExamDefinition): BubbleGrid {
        val objetivas = objetivasDe(exam)
        val questionCount = objetivas.size
        val optionCount = objetivas.maxOf { it.value.options.size }
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

    /**
     * O cabecalho da folha, ja medido (§7).
     *
     * Medir antes de emitir e obrigatorio: a altura do cabecalho entra na reserva da primeira
     * pagina, e reserva calculada por um caminho e desenho feito por outro divergem em silencio —
     * e a divergencia so aparece na folha impressa.
     */
    private class Header(
        val title: MeasuredText,
        val instruction: MeasuredText,
        val titleStyle: TextStyle,
        val height: Um,
    )

    private fun measureHeader(exam: ExamDefinition): Header {
        val titleStyle = TextStyle(
            size = (style.size * 3).divFloor(2),
            lineHeight = (style.lineHeight * 3).divFloor(2),
        )
        val title = measurer.measure(exam.title, titleStyle, profile.contentWidth)
        val instruction = measurer.measure(FILL_INSTRUCTION, style, profile.contentWidth)
        return Header(
            title = title,
            instruction = instruction,
            titleStyle = titleStyle,
            height = profile.snapToGrid(title.height + instruction.height + HEADER_GAP),
        )
    }

    private fun emitHeader(header: Header, primitives: MutableList<Primitive>) {
        val left = profile.marginSide
        var baseline = profile.marginTop

        for ((index, line) in header.title.lines.withIndex()) {
            baseline += line.ascent
            primitives += DrawText(
                id = "hd-t$index",
                x = left.raw,
                baseline = baseline.raw,
                size = header.titleStyle.size.raw,
                text = line.text,
            )
            baseline += line.descent
        }
        for ((index, line) in header.instruction.lines.withIndex()) {
            baseline += line.ascent
            primitives += DrawText(
                id = "hd-i$index",
                x = left.raw,
                baseline = baseline.raw,
                size = style.size.raw,
                text = line.text,
            )
            baseline += line.descent
        }
    }

    /**
     * Rodape com a posicao e o total de paginas, centrado dentro da margem inferior.
     *
     * Fica **abaixo** da area de conteudo, e nao dentro dela: a margem inferior existe e estava
     * vazia. Centrar exige saber a largura do texto, e quem sabe medir e o mesmo medidor que o
     * resto da folha usa — nao o renderizador.
     */
    private fun emitFooter(index: Int, pageCount: Int, primitives: MutableList<Primitive>) {
        val text = "Página ${index + 1} de $pageCount"
        val width = measurer.width(text, style)
        val left = profile.marginSide + (profile.contentWidth - width).divFloor(2)
        val baseline = profile.pageHeight - profile.marginBottom + FOOTER_DROP
        primitives += DrawText(
            id = "ft-p$index",
            x = left.raw,
            baseline = baseline.raw,
            size = style.size.raw,
            text = text,
        )
    }

    private fun buildAnswerBlock(
        exam: ExamDefinition,
        grid: BubbleGrid,
        regionHeight: Um,
        top: Um,
        primitives: MutableList<Primitive>,
    ): ScannableRegion {
        val left = profile.marginSide
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
        val qrId = "r$REGION_INDEX-qr"
        primitives += DrawQr(
            id = qrId,
            x = qrX.raw,
            y = qrY.raw,
            side = qrSide.raw,
            module = qrSide.divFloor(qrMatrix.size).raw,
            payload = payload,
            modules = linhasDeModulo(qrMatrix),
        )

        val bubbleLeft = left + marker + CaptureGeometry.QUIET_ZONE
        val bubbleTop = qrY + qrSide + CaptureGeometry.QUIET_ZONE
        val radius = CaptureGeometry.BUBBLE_DIAMETER.divFloor(2)
        val bubbles = mutableListOf<Bubble>()

        val objetivas = objetivasDe(exam)
        emitBands(objetivas.size, grid, bubbleLeft, bubbleTop, primitives)

        // So objetivas no gabarito, e cada linha com o numero DA QUESTAO NA PROVA, e nao o da linha:
        // com a 3 discursiva, o gabarito numera 1, 2, 4, 5. Renumerar faria o aluno marcar a
        // questao 4 na linha "3", que e o erro de transcricao que §7 inteiro existe para mitigar
        // (decisao 7 do `design.md` da `slice-5a-regiao-discursiva`).
        objetivas.forEachIndexed { index, (posicaoNaProva, question) ->
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
                text = "${posicaoNaProva + 1}",
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
                // A letra dentro do circulo (§7): e ela que impede o aluno de contar colunas para
                // saber onde marcar, que e de onde vem o salto de linha. Cinza porque a bolha e
                // medida por tinta: ela precisa ser legivel sem ser confundida com resposta, e o
                // quanto de tinta ela pode gastar e o orcamento que o mapa declara.
                val letterSize = (style.size * 2).divFloor(3)
                val letterWidth = measurer.width(
                    letter,
                    TextStyle(size = letterSize, lineHeight = letterSize),
                )
                primitives += DrawText(
                    id = "r$REGION_INDEX-l${question.id}-$letter",
                    x = (centerX - letterWidth.divFloor(2)).raw,
                    // Meia altura de maiuscula acima do centro. A fonte nao expoe `capHeight`, e
                    // 33% do corpo e a aproximacao usual para serifada; o que decide se ela esta
                    // centrada e a folha impressa, nao esta constante.
                    baseline = (centerY + (letterSize * 33).divFloor(100)).raw,
                    size = letterSize.raw,
                    text = letter,
                    tone = LETTER_TONE,
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
            qrId = qrId,
        )
    }

    /**
     * Faixa alternada sob grupos de questoes (§7).
     *
     * A mitigacao do erro de transcricao e um conjunto, e este e o pedaco que da ao olho um ponto
     * de retorno: sem grupo visivel, o aluno que perde a linha so descobre no fim. A faixa vai
     * **antes** das bolhas na lista de primitivas, porque quem desenha depois fica por cima.
     */
    private fun emitBands(
        questionCount: Int,
        grid: BubbleGrid,
        bubbleLeft: Um,
        bubbleTop: Um,
        primitives: MutableList<Primitive>,
    ) {
        for (column in 0 until grid.columns) {
            val firstQuestion = column * grid.rows
            val rowsInColumn = minOf(grid.rows, questionCount - firstQuestion)
            if (rowsInColumn <= 0) continue
            // O tamanho do grupo e por **coluna**, e nao da grade: a ultima coluna costuma ter
            // menos linhas, e agrupa-la pelo tamanho das outras deixaria uma sobra de uma ou duas
            // linhas no fim — que nao e grupo, e o inverso do que §7 pede.
            val groupSize = groupSizeFor(rowsInColumn)

            var row = 0
            var group = 0
            while (row < rowsInColumn) {
                val rowsInGroup = minOf(groupSize, rowsInColumn - row)
                if (group % 2 == 1) {
                    primitives += DrawRect(
                        id = "r$REGION_INDEX-f$column-$group",
                        x = (
                            bubbleLeft +
                                (grid.columnWidth + CaptureGeometry.BUBBLE_COLUMN_GAP) * column
                            ).raw,
                        y = (bubbleTop + CaptureGeometry.BUBBLE_PITCH_V * row).raw,
                        width = grid.columnWidth.raw,
                        height = (CaptureGeometry.BUBBLE_PITCH_V * rowsInGroup).raw,
                        stroke = 0,
                        fill = BAND_TONE,
                    )
                }
                row += rowsInGroup
                group += 1
            }
        }
    }

    /**
     * Tamanho de grupo que deixa **todos** os grupos entre 3 e 5 linhas (§7).
     *
     * O maior tamanho que ou divide exato, ou deixa um resto que ainda e grupo legitimo. Sem esta
     * conta, um gabarito de 11 linhas em grupos de 5 terminaria com um grupo de 1 — que nao e
     * agrupamento, e uma sobra.
     */
    private fun groupSizeFor(rows: Int): Int {
        if (rows < MIN_GROUP) return rows
        for (size in MAX_GROUP downTo MIN_GROUP) {
            val rest = rows % size
            if (rest == 0 || rest >= MIN_GROUP) return size
        }
        return MIN_GROUP
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

    /**
     * A regiao discursiva de uma questao, logo abaixo do enunciado dela (§8; ADR-0018).
     *
     * Dois marcadores na diagonal e o QR no terceiro canto: o `4k` no canto superior esquerdo, o QR no
     * superior direito, com o topo na mesma altura, e o `4k+3` no inferior direito. O retangulo de
     * referencia vai do canto externo de um ao canto externo do outro, e e a propria regiao — pelos
     * centros, como no gabarito, o QR encostado na borda direita normalizaria fora de `[0,1]`.
     *
     * Abaixo da faixa de cima, a moldura, na largura inteira, com a pauta dentro — e nada mais: o
     * enunciado ja foi desenhado acima, fora da regiao. A moldura e desenhada **para dentro** (o traco
     * inteiro fica nela), para que a borda de fora da tinta seja a borda declarada.
     *
     * A **area de resposta**, que e o que a captura recorta, vai da base do QR ate a zona de silencio
     * do `4k+3`, na largura inteira: contem a moldura com folga em cima e embaixo, e a folga de baixo e
     * para a tinta que desce da ultima linha escrita.
     *
     * A pauta e `line` cinza (ADR-0016), abaixo do teto decorativo da regiao. Tom e traco sao
     * provisorios ate a impressao (decisao 5).
     */
    private fun emitEssayRegion(
        examId: String,
        regionIndex: Int,
        content: QuestionContent,
        placement: Placement,
        primitives: MutableList<Primitive>,
    ): ScannableRegion {
        val essay = requireNotNull(content.essay)
        val marker = EssayGeometry.MARKER_SIDE
        val left = profile.columnLeft(placement.column)
        val width = profile.columnWidth
        val top = placement.top + essay.statementHeight
        val height = essay.regionHeight

        val (topMarkerId, bottomMarkerId) = EssayGeometry.markerIdsOf(regionIndex)
        val corners = listOf(
            topMarkerId to (left to top),
            bottomMarkerId to ((left + width - marker) to (top + height - marker)),
        )
        for ((markerId, corner) in corners) {
            primitives += DrawAruco(
                id = "r$regionIndex-m$markerId",
                markerId = markerId,
                x = corner.first.raw,
                y = corner.second.raw,
                side = marker.raw,
                module = EssayGeometry.MARKER_MODULE.raw,
                modules = CaptureGeometry.markerModules(markerId),
            )
        }

        val payload = qrPayloadOf(examId, regionIndex)
        val qrMatrix = QrEncoder.encode(payload)
        val qrSide = CaptureGeometry.QR_SIDE
        val qrX = left + width - qrSide
        val qrY = top
        val qrId = "r$regionIndex-qr"
        primitives += DrawQr(
            id = qrId,
            x = qrX.raw,
            y = qrY.raw,
            side = qrSide.raw,
            module = qrSide.divFloor(qrMatrix.size).raw,
            payload = payload,
            modules = linhasDeModulo(qrMatrix),
        )

        val frameTop = top + EssayGeometry.TOP_BAND
        val frameHeight = EssayGeometry.PAUTA * essay.lines
        val frameStroke = EssayGeometry.FRAME_STROKE
        val halfFrame = frameStroke.divFloor(2)
        primitives += DrawRect(
            id = "r$regionIndex-moldura",
            x = (left + halfFrame).raw,
            y = (frameTop + halfFrame).raw,
            width = (width - frameStroke).raw,
            height = (frameHeight - frameStroke).raw,
            stroke = frameStroke.raw,
        )
        // Linhas 1..n-1: a borda de cima e a de baixo da moldura ja fazem o papel da linha 0 e da n.
        for (linha in 1 until essay.lines) {
            val y = (frameTop + EssayGeometry.PAUTA * linha).raw
            primitives += DrawLine(
                id = "r$regionIndex-p$linha",
                x1 = (left + EssayGeometry.PAUTA_INSET).raw,
                y1 = y,
                x2 = (left + width - EssayGeometry.PAUTA_INSET).raw,
                y2 = y,
                stroke = EssayGeometry.PAUTA_STROKE.raw,
                tone = EssayGeometry.PAUTA_TONE,
            )
        }

        val answerTop = qrY + qrSide
        val answerBottom = top + height - marker - EssayGeometry.MARKER_QUIET_ZONE

        return ScannableRegion(
            index = regionIndex,
            kind = ESSAY_KIND,
            page = placement.page,
            quadX = left.raw,
            quadY = top.raw,
            quadWidth = width.raw,
            quadHeight = height.raw,
            markerIds = listOf(topMarkerId, bottomMarkerId),
            qr = NormalizedRect(
                u = Ppm.of(qrX - left, width).raw,
                v = Ppm.of(qrY - top, height).raw,
                uSize = Ppm.of(qrSide, width).raw,
                vSize = Ppm.of(qrSide, height).raw,
            ),
            bubbles = emptyList(),
            qrId = qrId,
            questionId = content.questionId,
            answerArea = NormalizedRect(
                u = 0,
                v = Ppm.of(answerTop - top, height).raw,
                uSize = Ppm.ONE.raw,
                vSize = Ppm.of(answerBottom - answerTop, height).raw,
            ),
        )
    }

    /**
     * As objetivas da prova, cada uma com a posicao dela na prova.
     *
     * Um lugar so para "quais questoes o gabarito tem", porque tres pontos perguntam: a grade, as
     * faixas e as bolhas. Tres filtros escritos a parte concordariam por coincidencia.
     */
    private fun objetivasDe(exam: ExamDefinition): List<IndexedValue<Question>> =
        exam.questions.withIndex().filter { it.value.kind == QuestionKind.OBJECTIVE }

    companion object {
        private const val REGION_INDEX = 0

        /** O `kind` da regiao discursiva, ao lado de `answer_block` e `print_test`. */
        const val ESSAY_KIND = "essay"
        private const val MAX_BUBBLE_COLUMNS = 6
        private val SPACE_AFTER_REGION = Um.mm(6)

        /**
         * Instrucao de preenchimento impressa na folha (§7).
         *
         * Ela nao e enfeite: bolha preenchida pela metade e a resposta que o OMR le como duvida, e
         * lapis reflete diferente de tinta na captura monocromatica. O texto fica aqui, e nao na
         * definicao da prova, porque e propriedade da folha — quem escreve a prova nao escolhe
         * como ela e lida.
         */
        private const val FILL_INSTRUCTION =
            "Preencha todo o círculo da alternativa com caneta preta ou azul escura. " +
                "Não use lápis e não rasure."

        /**
         * Respiro entre a ultima linha do cabecalho e o primeiro marcador.
         *
         * Zona de silencio mais um passo da grade: o minimo de §7 e um modulo, e o passo a mais
         * cobre a descendente da ultima linha, que desce abaixo da linha de base.
         */
        private val HEADER_GAP = CaptureGeometry.QUIET_ZONE + Um.mm(3)

        /** Do fim da area de conteudo ate a linha de base do rodape, dentro da margem inferior. */
        private val FOOTER_DROP = Um.mm(6)

        /** Trama da faixa alternada: 4,5% de preto (§7), em permilagem. */
        private const val BAND_TONE = 45

        /** Tom da letra dentro do circulo. Legivel, e longe do que uma caneta deixa. */
        private const val LETTER_TONE = 400

        /** Grupo de questoes no gabarito: de 3 a 5 linhas (§7). */
        private const val MIN_GROUP = 3
        private const val MAX_GROUP = 5

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
         * Payload do QR da regiao. A regra mora em [QrPayload], junto do leitor dela.
         *
         * Sem identidade por omissao: o mapa que o engine produz e a **geometria da variante**, de
         * que as folhas dos alunos derivam, e ela nao pertence a aluno nenhum. Quem preenche token
         * e variante e a publicacao, uma vez por atribuicao.
         */
        fun qrPayloadOf(
            examId: String,
            regionIndex: Int,
            studentToken: String = "",
            variant: String = "",
        ): String = QrPayload.of(examId, regionIndex, studentToken, variant)

        /**
         * Payload de uma regiao da folha de uma atribuicao.
         *
         * O indice da regiao **nao** entra como numero solto: ele e decisao do engine, e um
         * chamador que o passasse errado produziria folha cujo QR discorda dos marcadores dela — a
         * divergencia que a leitura confere e recusa, descoberta so na captura. Por isso quem chama
         * entrega a **regiao que o engine produziu**, e o indice sai dela. Ate a
         * `slice-5a-regiao-discursiva` a folha tinha uma regiao so e o indice era fixo aqui; com a
         * discursiva ha uma por questao (D23), e a regra continua a mesma.
         */
        fun qrPayloadDaAtribuicao(
            examId: String,
            studentToken: String,
            variant: String,
            regiao: ScannableRegion,
        ): String = qrPayloadOf(examId, regiao.index, studentToken, variant)
    }
}
