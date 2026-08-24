package com.platos.android.vision

import com.platos.android.omr.BubbleMeter
import com.platos.android.omr.MeterOutcome
import com.platos.domain.capture.OmrReading
import com.platos.domain.layout.LayoutMap
import com.platos.domain.layout.ScannableRegion
import org.opencv.core.Mat

/**
 * O pipeline de §8, inteiro, de uma captura ate as medicoes.
 *
 * ```
 * quadro -> detecta ArUcos -> identifica regiao pelos IDs -> homografia
 *        -> decodifica QR na ROI ja retificada -> mede cobertura das bolhas
 * ```
 *
 * A ordem nao e escolha deste arquivo: ela e a de §8, e cada passo depende do anterior. O QR so e
 * procurado depois da retificacao porque e la que ele fica facil de ler e porque o mapa diz onde
 * ele esta; as bolhas so sao medidas depois de a identidade da folha fechar, porque medir uma
 * folha que nao se sabe de quem e produz numero sem dono.
 *
 * **Nao ha limiar aqui, e nao ha nota.** A saida e cobertura por bolha; quem interpreta e o
 * scoring, depois que a fatia do corpus escolher o limiar dentro do corredor que ADR-0010 reservou.
 *
 * A leitura e local e sem efeito: nada nela toca rede, disco ou estado. Ver `OmrReading`.
 */
object SheetReader {

    fun read(gray: Mat, map: LayoutMap, region: ScannableRegion): OmrReading {
        val detection = RegionDetector.detect(gray, map, region)
        val rectified = when (detection) {
            is DetectionOutcome.Failed -> return OmrReading.Rejected(detection.reason)
            is DetectionOutcome.Rectified -> detection
        }

        val qr = RegionQrReader.read(rectified.region, region, rectified.detectedMarkerIds)
        val payload = when (qr) {
            is QrOutcome.Failed -> return OmrReading.Rejected(qr.reason)
            is QrOutcome.Read -> qr.payload
        }

        return when (val measured = BubbleMeter.measure(map, region, rectified.region)) {
            is MeterOutcome.Failed -> OmrReading.Rejected(measured.reason)
            is MeterOutcome.Measured -> OmrReading.Read(payload, measured.measurements)
        }
    }
}
