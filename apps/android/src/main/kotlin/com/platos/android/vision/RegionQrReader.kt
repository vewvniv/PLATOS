package com.platos.android.vision

import android.graphics.Bitmap
import android.graphics.Rect
import com.platos.android.omr.RectifiedRegion
import com.platos.domain.capture.CapturePayload
import com.platos.domain.capture.PayloadReading
import com.platos.domain.capture.QrPayload
import com.platos.domain.capture.CaptureGeometry
import com.platos.domain.layout.ScannableRegion
import zxingcpp.BarcodeReader

/** O que saiu de uma tentativa de ler o QR da regiao ja retificada. */
sealed interface QrOutcome {

    data class Read(val payload: CapturePayload) : QrOutcome

    data class Failed(val reason: String) : QrOutcome
}

/**
 * Decodifica o QR **na ROI prevista, ja retificada** (§8).
 *
 * A ordem de §8 nao e arbitraria: `ArUcos -> homografia -> QR na ROI retificada`. Duas razoes, e
 * as duas aparecem aqui.
 *
 * A primeira e que decodificar QR sobre imagem desempenada e muito mais facil que sobre foto em
 * perspectiva — o modulo vira quadrado de novo.
 *
 * A segunda e que o mapa **diz onde o QR esta**: `region.qr` traz posicao e tamanho normalizados
 * ao quadrilatero, entao a busca acontece num retangulo conhecido em vez de no quadro inteiro.
 * Procurar QR na foto toda acharia tambem o QR da folha do aluno ao lado, na mesa.
 *
 * A validacao — CRC e formato — nao mora aqui: ela e do dominio, em [QrPayload]. Este arquivo so
 * transforma pixel em texto.
 */
object RegionQrReader {

    /** Folga em volta da ROI declarada, para absorver o residuo da homografia. */
    private const val MARGIN_PX = 8

    private val reader = BarcodeReader(
        BarcodeReader.Options().apply {
            // So QR. Aceitar outros formatos convidaria a decodificar um code-128 impresso na
            // carteirinha do aluno que aparecesse no quadro.
            formats = setOf(BarcodeReader.Format.QR_CODE)
            tryRotate = true
            tryInvert = false
            maxNumberOfSymbols = 1
        },
    )

    /**
     * Le o QR da regiao e devolve os campos de §8, ja com o CRC conferido.
     *
     * [detectedMarkerIds] sao os identificadores que a deteccao realmente achou na captura. Eles
     * existem aqui por causa da redundancia que §8 pede: o `region_idx` do QR e conferido contra
     * eles, para que a atribuicao da folha nao dependa de **um** canal so. Um QR borrado que
     * decodifique errado, ou uma folha de outra regiao na pilha, e pego por essa divergencia.
     */
    fun read(
        image: RectifiedRegion,
        region: ScannableRegion,
        detectedMarkerIds: List<Int>,
    ): QrOutcome {
        val left = (region.qr.u.toLong() * image.width / PPM).toInt() - MARGIN_PX
        val top = (region.qr.v.toLong() * image.height / PPM).toInt() - MARGIN_PX
        val right = ((region.qr.u + region.qr.uSize).toLong() * image.width / PPM).toInt() + MARGIN_PX
        val bottom = ((region.qr.v + region.qr.vSize).toLong() * image.height / PPM).toInt() + MARGIN_PX

        val roi = Rect(
            left.coerceIn(0, image.width - 1),
            top.coerceIn(0, image.height - 1),
            right.coerceIn(1, image.width),
            bottom.coerceIn(1, image.height),
        )
        if (roi.width() < MIN_ROI_PX || roi.height() < MIN_ROI_PX) {
            return QrOutcome.Failed(
                "a ROI do QR saiu em ${roi.width()}x${roi.height()} px, pequena demais para decodificar",
            )
        }

        val results = runCatching { reader.read(bitmapOf(image), roi, 0) }
            .getOrElse { return QrOutcome.Failed("o decodificador falhou: ${it.message}") }
        val text = results.firstOrNull()?.text
            ?: return QrOutcome.Failed("nenhum QR decodificado na ROI que o mapa declara")

        return when (val reading = QrPayload.read(text)) {
            is PayloadReading.Rejected -> QrOutcome.Failed(reading.reason)
            is PayloadReading.Read -> {
                val expected = CaptureGeometry.markerIdsOf(reading.payload.regionIndex)
                if (expected.sorted() != detectedMarkerIds.sorted()) {
                    QrOutcome.Failed(
                        "o QR diz regiao ${reading.payload.regionIndex}, que usa os marcadores " +
                            "$expected, mas a captura tem ${detectedMarkerIds.sorted()}",
                    )
                } else {
                    QrOutcome.Read(reading.payload)
                }
            }
        }
    }

    /**
     * Bitmap a partir do buffer cinza.
     *
     * `ALPHA_8` seria o formato natural e o decodificador o rejeita — ele le luminancia de canais
     * de cor. Entao o cinza e replicado nos tres canais, que e o que qualquer conversao faria de
     * qualquer jeito, so que explicito.
     */
    private fun bitmapOf(image: RectifiedRegion): Bitmap {
        val pixels = IntArray(image.width * image.height)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val value = image.luminanceAt(x, y)
                pixels[y * image.width + x] =
                    (0xFF shl 24) or (value shl 16) or (value shl 8) or value
            }
        }
        return Bitmap.createBitmap(pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
    }

    /** Partes por milhao: a unidade em que o mapa declara a ROI do QR. */
    private const val PPM = 1_000_000L

    /** Abaixo disto nao ha modulo suficiente para um QR significar coisa alguma. */
    private const val MIN_ROI_PX = 24
}
