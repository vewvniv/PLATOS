package com.platos.domain.layout

import com.platos.domain.capture.CaptureGeometry
import com.platos.domain.capture.QrEncoder
import com.platos.domain.geometry.Ppm
import com.platos.domain.geometry.Um
import com.platos.domain.text.EmbeddedFont
import com.platos.domain.text.TextMeasurer
import com.platos.domain.text.TextStyle

/**
 * A folha de teste de impressao (§16, D-2b.7).
 *
 * Existe para **reprovar uma impressora** antes de ela imprimir uma turma. §16 põe a impressao dos
 * ArUcos como o risco cuja fatia-limite e a 2b, com a razao explicita: depois que ha folha
 * distribuida, corrigir marcador significa reimprimir.
 *
 * Ela e um `LayoutMap` como qualquer outro, com as mesmas primitivas e a mesma `CaptureGeometry` da
 * prova. Nao ha caminho de desenho proprio, e isso e o essencial: uma folha que aprova a impressora
 * por um caminho que a prova nao percorre nao aprova nada.
 *
 * O criterio de cada conferencia e impresso **na propria folha**, porque e la que ele e util —
 * quem confere esta com o papel na mao, e nao com o repositorio aberto.
 */
class PrintTestSheet(
    private val measurer: TextMeasurer = TextMeasurer(EmbeddedFont.program),
    private val profile: LayoutProfile = LayoutProfile.DEFAULT,
) {

    private val style: TextStyle get() = profile.style

    fun layout(): LayoutMap {
        val primitives = mutableListOf<Primitive>()
        val left = profile.marginSide
        var cursor = profile.marginTop

        cursor = emitTitle(primitives, cursor)
        val region = emitMarkerBand(primitives, top = cursor)
        cursor += BAND_HEIGHT + Um.mm(6)
        cursor = emitReferenceSpan(primitives, cursor)
        cursor = emitTints(primitives, cursor)
        emitCriteria(primitives, cursor)

        // O rodape da prova, pelo mesmo motivo: quem confere precisa saber que a folha e uma so.
        val footer = "Folha de teste de impressão — página 1 de 1"
        primitives += DrawText(
            id = "ft-p0",
            x = (left + (profile.contentWidth - measurer.width(footer, style)).divFloor(2)).raw,
            baseline = (profile.pageHeight - profile.marginBottom + Um.mm(6)).raw,
            size = style.size.raw,
            text = footer,
        )

        return LayoutMap(
            layoutEngineVersion = LayoutMap.ENGINE_VERSION,
            minRendererVersion = LayoutMap.MIN_RENDERER_VERSION,
            examId = SHEET_ID,
            pageWidth = profile.pageWidth.raw,
            pageHeight = profile.pageHeight.raw,
            fontSha256 = EmbeddedFont.sha256,
            profile = LayoutProfileRef(
                id = profile.id,
                bodySize = style.size.raw,
                lineHeight = style.lineHeight.raw,
                grid = profile.grid.raw,
            ),
            pages = listOf(Page(index = 0, primitives = primitives.toList())),
            regions = listOf(region),
        )
    }

    private fun emitTitle(primitives: MutableList<Primitive>, top: Um): Um {
        val titleStyle = TextStyle(
            size = (style.size * 3).divFloor(2),
            lineHeight = (style.lineHeight * 3).divFloor(2),
        )
        var baseline = top
        var index = 0
        for (line in measurer.measure(TITLE, titleStyle, profile.contentWidth).lines) {
            baseline += line.ascent
            primitives += DrawText(
                id = "hd-t$index",
                x = profile.marginSide.raw,
                baseline = baseline.raw,
                size = titleStyle.size.raw,
                text = line.text,
            )
            index += 1
        }
        index = 0
        for (line in measurer.measure(SUBTITLE, style, profile.contentWidth).lines) {
            baseline += line.ascent
            primitives += DrawText(
                id = "hd-i$index",
                x = profile.marginSide.raw,
                baseline = baseline.raw,
                size = style.size.raw,
                text = line.text,
            )
            index += 1
        }
        return profile.snapToGrid(baseline - top + CaptureGeometry.QUIET_ZONE + Um.mm(3)) + top
    }

    /**
     * Os quatro marcadores, o QR e uma linha de bolhas — a geometria de captura inteira, nos mesmos
     * numeros da prova.
     */
    private fun emitMarkerBand(primitives: MutableList<Primitive>, top: Um): ScannableRegion {
        val left = profile.marginSide
        val marker = CaptureGeometry.MARKER_SIDE
        val right = left + profile.contentWidth
        val bottom = top + BAND_HEIGHT

        val quadX = left + marker.divFloor(2)
        val quadY = top + marker.divFloor(2)
        val quadWidth = profile.contentWidth - marker
        val quadHeight = BAND_HEIGHT - marker

        val markerIds = CaptureGeometry.markerIdsOf(0)
        val corners = listOf(
            left to top,
            (right - marker) to top,
            left to (bottom - marker),
            (right - marker) to (bottom - marker),
        )
        for ((index, corner) in corners.withIndex()) {
            val markerId = markerIds[index]
            primitives += DrawAruco(
                id = "r0-m$markerId",
                markerId = markerId,
                x = corner.first.raw,
                y = corner.second.raw,
                side = marker.raw,
                module = CaptureGeometry.MARKER_MODULE.raw,
                modules = CaptureGeometry.markerModules(markerId),
            )
        }

        val payload = qrPayload()
        val matrix = QrEncoder.encode(payload)
        val qrSide = CaptureGeometry.QR_SIDE
        val qrX = left + (profile.contentWidth - qrSide).divFloor(2)
        val qrY = quadY
        primitives += DrawQr(
            id = "r0-qr",
            x = qrX.raw,
            y = qrY.raw,
            side = qrSide.raw,
            module = qrSide.divFloor(matrix.size).raw,
            payload = payload,
            modules = matrix.modules.map { row -> row.joinToString("") { if (it) "1" else "0" } },
        )

        // Uma linha de bolhas na geometria da prova: diametro, passo e traco iguais, com a letra
        // dentro do circulo. E o que permite ver no papel se o toner fecha um circulo de 4,2 mm.
        val radius = CaptureGeometry.BUBBLE_DIAMETER.divFloor(2)
        val bubbleTop = qrY + qrSide + CaptureGeometry.QUIET_ZONE
        val bubbleLeft = left + marker + CaptureGeometry.QUIET_ZONE
        val bubbles = mutableListOf<Bubble>()
        for (index in 0 until BUBBLE_COUNT) {
            val letter = ('A' + index).toString()
            val centerX = bubbleLeft + CaptureGeometry.BUBBLE_PITCH_H * index + radius
            val centerY = bubbleTop + radius
            primitives += DrawCircle(
                id = "r0-bteste-$letter",
                centerX = centerX.raw,
                centerY = centerY.raw,
                diameter = CaptureGeometry.BUBBLE_DIAMETER.raw,
                stroke = CaptureGeometry.BUBBLE_STROKE.raw,
            )
            val letterSize = (style.size * 2).divFloor(3)
            val letterWidth = measurer.width(letter, TextStyle(letterSize, letterSize))
            primitives += DrawText(
                id = "r0-lteste-$letter",
                x = (centerX - letterWidth.divFloor(2)).raw,
                baseline = (centerY + (letterSize * 33).divFloor(100)).raw,
                size = letterSize.raw,
                text = letter,
                tone = LETTER_TONE,
            )
            bubbles += Bubble(
                questionId = "teste",
                option = letter,
                u = Ppm.of(centerX - quadX, quadWidth).raw,
                v = Ppm.of(centerY - quadY, quadHeight).raw,
            )
        }

        return ScannableRegion(
            index = 0,
            kind = "print_test",
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

    /**
     * O vao de referencia, com o comprimento esperado impresso ao lado dele.
     *
     * E a maior distancia da folha, que e onde a regua e mais confiavel — o mesmo portao de escala
     * do `docs/protocolo-medicao-impressa.md`. Um retangulo, e nao uma linha: a largura **externa**
     * e o que se mede com regua encostada, sem depender de achar o meio de um traco.
     */
    private fun emitReferenceSpan(primitives: MutableList<Primitive>, top: Um): Um {
        primitives += DrawRect(
            id = "vao-de-referencia",
            x = profile.marginSide.raw,
            y = top.raw,
            width = profile.contentWidth.raw,
            height = SPAN_HEIGHT.raw,
            stroke = CaptureGeometry.BUBBLE_STROKE.raw,
        )
        var baseline = top + SPAN_HEIGHT
        for ((index, line) in measurer.measure(SPAN_LABEL, style, profile.contentWidth)
            .lines.withIndex()) {
            baseline += line.ascent
            primitives += DrawText(
                id = "vao-legenda-$index",
                x = profile.marginSide.raw,
                baseline = baseline.raw,
                size = style.size.raw,
                text = line.text,
            )
        }
        baseline += style.lineHeight
        return profile.snapToGrid(baseline - top) + top
    }

    /** Amostras de trama: a do gabarito e o teto de §7, para ver se a impressora reproduz as duas. */
    private fun emitTints(primitives: MutableList<Primitive>, top: Um): Um {
        val patch = Um.mm(30)
        val height = Um.mm(12)
        for ((index, tint) in TINT_SAMPLES.withIndex()) {
            val x = profile.marginSide + (patch + Um.mm(8)) * index
            primitives += DrawRect(
                id = "trama-$tint",
                x = x.raw,
                y = top.raw,
                width = patch.raw,
                height = height.raw,
                stroke = 0,
                fill = tint,
            )
            primitives += DrawText(
                id = "trama-$tint-legenda",
                x = x.raw,
                baseline = (top + height + style.lineHeight).raw,
                size = style.size.raw,
                text = "${tint / 10},${tint % 10}%",
            )
        }
        return profile.snapToGrid(height + style.lineHeight * 2) + top
    }

    /** O criterio de aprovacao e reprovacao de cada conferencia, impresso na folha. */
    private fun emitCriteria(primitives: MutableList<Primitive>, top: Um) {
        var baseline = top
        var index = 0
        for (item in CRITERIA) {
            for (line in measurer.measure(item, style, profile.contentWidth).lines) {
                baseline += line.ascent
                primitives += DrawText(
                    id = "criterio-$index",
                    x = profile.marginSide.raw,
                    baseline = baseline.raw,
                    size = style.size.raw,
                    text = line.text,
                )
                index += 1
            }
            baseline += style.lineHeight.divFloor(2)
        }
    }

    companion object {
        const val SHEET_ID = "folha-de-teste-de-impressao"

        private val BAND_HEIGHT = Um.mm(45)
        private val SPAN_HEIGHT = Um.mm(5)
        private const val BUBBLE_COUNT = 5
        private const val LETTER_TONE = 400

        /** A trama do gabarito e o teto de §7: as duas precisam aparecer, e distintas. */
        private val TINT_SAMPLES = listOf(45, 80)

        private const val TITLE = "Folha de teste de impressão"

        private const val SUBTITLE =
            "Imprima em A4, escala 100%, sem ajustar à página. Confira as cinco linhas abaixo " +
                "antes de imprimir uma turma."

        /** Vao nominal: a largura util da folha, que e a maior distancia medivel nela. */
        private const val SPAN_LABEL =
            "1. Vão de referência: meça a largura externa do retângulo acima. Esperado 180,0 mm. " +
                "Aprova entre 171,0 e 189,0 mm; fora disso, a impressora está reescalando."

        private val CRITERIA = listOf(
            "2. Marcadores: os quatro precisam ter a borda preta fechada nos quatro lados e os " +
                "módulos internos nítidos, sem borrão que una dois módulos nem falha branca " +
                "dentro de um módulo preto. Nenhum traço pode invadir os 2 mm ao redor de cada um.",
            "3. Código QR: precisa ser lido pela câmera de um celular comum, na primeira tentativa.",
            "4. Tramas: as duas amostras precisam aparecer no papel e ser distinguíveis entre si. " +
                "Trama que some é toner fraco; trama que fecha em cinza chapado é toner pesado.",
            "5. Bolhas: cada círculo precisa estar fechado, e a letra dentro dele precisa ser " +
                "legível sem parecer uma marcação.",
            "Reprovou em qualquer uma das cinco? Esta impressora não está apta a imprimir a prova. " +
                "Troque o toner, desligue qualquer ajuste de escala e repita; se persistir, use " +
                "outra impressora.",
        )

        /**
         * Payload do QR, na forma definitiva de §8.
         *
         * Sem aluno e sem variante, como a prova fixa: os dois campos ficam vazios em vez de
         * inventados.
         */
        fun qrPayload(): String {
            val body = "$SHEET_ID...0"
            return "$body.${crc16(body)}"
        }

        /** CRC-16/CCITT-FALSE, o mesmo do QR da prova (D6). */
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
