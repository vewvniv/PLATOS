package com.platos.android.vision

import com.platos.android.omr.BubbleMeter
import com.platos.android.omr.MeterOutcome
import com.platos.domain.capture.InterpretationOutcome
import com.platos.domain.capture.OmrReading
import com.platos.domain.capture.OmrThreshold
import com.platos.domain.capture.SheetInterpreter
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
 * **[read] mede e nao interpreta**, e continua assim: a saida dele e cobertura por bolha. Quem
 * quer resposta chama [readInterpreted], que acrescenta o veredito sem esconder a medicao —
 * `InterpretedReading` carrega as duas.
 *
 * O limiar nao e escolha deste arquivo nem constante dele: ele **entra por parametro**, apurado
 * sobre o corpus fotografado pela regra de ADR-0011, e a folha declara o corredor em que ele
 * precisa cair.
 *
 * A leitura e local e sem efeito: nada nela toca rede, disco ou estado. Ver `OmrReading`.
 */
object SheetReader {

    fun read(gray: Mat, map: LayoutMap, region: ScannableRegion): OmrReading =
        when (val detection = RegionDetector.detect(gray, map, region)) {
            is DetectionOutcome.Failed -> OmrReading.Rejected(detection.reason)
            is DetectionOutcome.Rectified -> readFrom(detection, map, region)
        }

    /** Do quadrilatero ja desempenado ate as medicoes: QR primeiro, bolhas depois. */
    private fun readFrom(
        rectified: DetectionOutcome.Rectified,
        map: LayoutMap,
        region: ScannableRegion,
    ): OmrReading {
        val qr = RegionQrReader.read(rectified.qrCanvas, rectified.detectedMarkerIds)
        val payload = when (qr) {
            is QrOutcome.Failed -> return OmrReading.Rejected(qr.reason)
            is QrOutcome.Read -> qr.payload
        }

        return when (val measured = BubbleMeter.measure(map, region, rectified.region)) {
            is MeterOutcome.Failed -> OmrReading.Rejected(measured.reason)
            is MeterOutcome.Measured -> OmrReading.Read(payload, measured.measurements)
        }
    }

    /**
     * O mesmo pipeline, com o **estagio em que ele parou** preservado (fatia 3c).
     *
     * A diferenca para [readInterpreted] nao esta no que e feito — e a mesma sequencia, nas mesmas
     * funcoes —, e sim no que sobrevive ao retorno. `OmrReading.Rejected` diz que a folha nao foi
     * lida; [FrameOutcome] diz **onde** ela parou, que e o que separa "ainda nao achei folha" de
     * "achei a folha e nao consegui ler". Ver [FrameOutcome].
     *
     * A decomposicao nao duplica nada: [read] e esta funcao chamam o mesmo [readFrom].
     */
    fun analyze(
        gray: Mat,
        map: LayoutMap,
        region: ScannableRegion,
        threshold: OmrThreshold,
    ): FrameOutcome {
        val detection = RegionDetector.detect(gray, map, region)
        val rectified = when (detection) {
            is DetectionOutcome.Failed -> return FrameOutcome.NoSheet(detection.reason)
            is DetectionOutcome.Rectified -> detection
        }

        val reading = when (val lida = readFrom(rectified, map, region)) {
            is OmrReading.Rejected -> return FrameOutcome.NotRead(lida.reason)
            is OmrReading.Read -> lida
        }

        return when (val interpretada = SheetInterpreter.interpret(reading, region, threshold)) {
            is InterpretationOutcome.Rejected -> FrameOutcome.Unreadable(interpretada.reason)
            is InterpretationOutcome.Interpreted -> FrameOutcome.Read(interpretada.reading)
        }
    }

    /**
     * O mesmo pipeline, seguido da interpretacao: veredito por bolha e resposta por questao.
     *
     * A interpretacao mora no dominio e nao aqui, porque ela nao toca imagem — recebe inteiros e o
     * corredor que o mapa declara. Este metodo e so a composicao dos dois lados da fronteira que a
     * fatia 3a desenhou: o adaptador acha, o nucleo puro decide.
     */
    fun readInterpreted(
        gray: Mat,
        map: LayoutMap,
        region: ScannableRegion,
        threshold: OmrThreshold,
    ): InterpretationOutcome = SheetInterpreter.interpret(read(gray, map, region), region, threshold)
}
