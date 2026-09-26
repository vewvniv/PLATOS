package com.platos.android.vision

import com.platos.android.omr.BubbleMeter
import com.platos.android.omr.MeterOutcome
import com.platos.domain.capture.InterpretationOutcome
import com.platos.domain.capture.OmrReading
import com.platos.domain.capture.OmrThreshold
import com.platos.domain.capture.SheetInterpreter
import com.platos.domain.layout.LayoutEngine
import com.platos.domain.layout.LayoutMap
import com.platos.domain.layout.ScannableRegion
import org.opencv.core.Mat
import org.opencv.core.Point

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
        val qr = RegionQrReader.read(rectified.qrCanvas, rectified.detectedMarkerIds, map)
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
     * O pipeline sobre um quadro inteiro, com o **estagio em que ele parou** preservado (fatia 3c).
     *
     * A diferenca para [readInterpreted] nao esta no que e feito — e a mesma sequencia, nas mesmas
     * funcoes —, e sim no que sobrevive ao retorno. `OmrReading.Rejected` diz que a folha nao foi
     * lida; [FrameOutcome] diz **onde** ela parou, que e o que separa "ainda nao achei folha" de
     * "achei a folha e nao consegui ler". Ver [FrameOutcome].
     *
     * **A regiao sai do quadro, e nao de quem chama** (`slice-5b-1-o-aparelho-reconhece-a-discursiva`).
     * Ate aqui esta funcao recebia a regiao, e a `ScanActivity` a escolhia com `map.regions.single()`
     * — que derrubava o aplicativo diante de uma prova com discursiva, de tres regioes. Agora os
     * marcadores sao detectados uma vez, e cada regiao cujos marcadores declarados aparecem todos e
     * lida (§8: "detecta ArUcos → identifica regiao pelos IDs") — quatro no gabarito, dois na
     * discursiva, desde a `slice-5b-0-a-regiao-discursiva-compacta`. Regiao pela metade nao e lida, e
     * nao e erro: um quadro de perto da pagina 0 pode pegar metade da moldura de uma discursiva, e o gabarito,
     * inteiro no quadro, nao pode deixar de ser lido por causa dela.
     *
     * A decomposicao nao duplica nada: [read] e esta funcao chamam o mesmo [readFrom].
     */
    fun analyze(gray: Mat, map: LayoutMap, threshold: OmrThreshold): FrameOutcome {
        val found = RegionDetector.detectMarkers(gray)
        if (found.isEmpty()) return FrameOutcome.NoSheet("nenhum marcador ArUco encontrado na captura")

        val presentes = map.regions.filter { regiao -> regiao.markerIds.all { it in found } }
        if (presentes.isEmpty()) {
            return FrameOutcome.NoSheet(
                "achei os marcadores ${found.keys.sorted()}, e nenhuma regiao do mapa tem todos os " +
                    "dela: " + map.regions.joinToString { "regiao ${it.index} ${it.markerIds}" },
            )
        }

        val discursivas = presentes
            .filter { it.kind == LayoutEngine.ESSAY_KIND }
            .map { reconhecer(gray, map, it, found) }
        val gabarito = presentes.firstOrNull { it.kind != LayoutEngine.ESSAY_KIND }
            ?: return FrameOutcome.SoDiscursivas(discursivas)

        val rectified = when (val detection = RegionDetector.detect(gray, map, gabarito, found)) {
            // Sem discursiva no quadro, o de sempre: geometria que nao fecha e "ainda nao achei a
            // folha". Com discursiva reconhecida, a folha foi achada, e dizer "procurando" apagaria
            // o que foi reconhecido nela.
            is DetectionOutcome.Failed -> return if (discursivas.isEmpty()) {
                FrameOutcome.NoSheet(detection.reason)
            } else {
                FrameOutcome.NotRead(detection.reason, discursivas)
            }
            is DetectionOutcome.Rectified -> detection
        }

        val reading = when (val lida = readFrom(rectified, map, gabarito)) {
            is OmrReading.Rejected -> return FrameOutcome.NotRead(lida.reason, discursivas)
            is OmrReading.Read -> lida
        }

        return when (val interpretada = SheetInterpreter.interpret(reading, gabarito, threshold)) {
            is InterpretationOutcome.Rejected -> FrameOutcome.Unreadable(interpretada.reason, discursivas)
            is InterpretationOutcome.Interpreted -> FrameOutcome.Read(interpretada.reading, discursivas)
        }
    }

    /**
     * A regiao discursiva: retificada e com o QR conferido contra os marcadores, e mais nada.
     *
     * Nao ha bolha a medir, e o recorte da resposta e da 5b-2. O QR passa pela mesma conferencia de
     * qualquer regiao — `region_idx` contra os marcadores encontrados —, e e ela que recusa o QR de
     * uma regiao dentro dos marcadores de outra.
     */
    private fun reconhecer(
        gray: Mat,
        map: LayoutMap,
        regiao: ScannableRegion,
        found: Map<Int, List<Point>>,
    ): RegiaoDiscursivaNoQuadro {
        // A validacao do mapa recusa regiao discursiva sem questao; vazio nunca chega aqui.
        val questao = regiao.questionId.orEmpty()
        val rectified = when (val detection = RegionDetector.detect(gray, map, regiao, found)) {
            is DetectionOutcome.Failed ->
                return RegiaoDiscursivaNoQuadro.NaoLida(regiao.index, questao, detection.reason)
            is DetectionOutcome.Rectified -> detection
        }
        return when (val qr = RegionQrReader.read(rectified.qrCanvas, rectified.detectedMarkerIds, map)) {
            is QrOutcome.Failed -> RegiaoDiscursivaNoQuadro.NaoLida(regiao.index, questao, qr.reason)
            is QrOutcome.Read -> RegiaoDiscursivaNoQuadro.Reconhecida(regiao.index, questao, qr.payload)
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
