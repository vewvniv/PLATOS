package com.platos.android.vision

import com.platos.android.omr.RectifiedRegion
import com.platos.domain.layout.DrawAruco
import com.platos.domain.layout.LayoutMap
import com.platos.domain.layout.ScannableRegion
import kotlin.math.hypot
import kotlin.math.roundToInt
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.ArucoDetector
import org.opencv.objdetect.Objdetect

/** O que saiu de uma tentativa de achar e desempenar a regiao numa captura. */
sealed interface DetectionOutcome {

    /**
     * A regiao foi achada e retificada.
     *
     * [reprojectionErrorPx] e medido sobre os **cantos** dos marcadores, que nao entraram no ajuste
     * da homografia — ver [RegionDetector].
     */
    data class Rectified(
        val region: RectifiedRegion,
        /**
         * A vizinhanca do QR, retificada a parte e **com sangria**.
         *
         * Existe porque o QR encosta na borda do quadrilatero: `qr.v` e zero no mapa da prova, e
         * [region] comeca exatamente no topo dele. A zona de silencio que o padrao QR exige para
         * ser decodificado fica, entao, fora da imagem — e o decodificador acerta ou erra conforme
         * a homografia deixe um ou dois pixels de folga. Medido no corpus da 3b: decodifica quando
         * a primeira linha escura cai em `y = 2` e falha quando cai em `y = 0`.
         *
         * Este canvas nao participa de medicao nenhuma. [region] continua sendo a fonte unica da
         * cobertura, byte a byte como antes — a sangria existiria para o QR ou nao existiria.
         */
        val qrCanvas: RectifiedRegion,
        val reprojectionErrorPx: Double,
        /** Identificadores realmente achados na captura, para a redundancia de §8. */
        val detectedMarkerIds: List<Int>,
    ) : DetectionOutcome

    data class Failed(val reason: String) : DetectionOutcome
}

/**
 * Acha os quatro ArUcos, monta a homografia e devolve a regiao desempenada (§8, §13).
 *
 * Este e o unico ponto do OMR que fala com o OpenCV, e e o unico que so pode ser verificado no
 * emulador. Tudo que produz **numero** fica em `omr/`, em Kotlin puro, testavel na JVM.
 *
 * Duas decisoes de implementacao que o plano nao previa, ambas registradas aqui porque mudam o que
 * se consegue afirmar:
 *
 * **A homografia sai dos centros; o erro e medido nos cantos.** Com exatamente quatro pares de
 * pontos a homografia fecha exata por construcao — um "erro de reprojecao" calculado sobre os
 * mesmos quatro pontos daria zero sempre, e seria uma verificacao que nunca falha. Os quatro
 * marcadores tem sedecim cantos, e eles **nao** entram no ajuste: mapeados pela homografia, eles
 * dizem se a geometria realmente fecha. Se um marcador foi confundido com outra coisa, ou se a
 * folha esta amassada, e ali que aparece.
 *
 * **A retificacao e feita com folga e depois reduzida por media de area.** `warpPerspective` nao
 * aceita `INTER_AREA`: ele amostra pontos, e reduzir uma foto de celular direto para 10 px/mm
 * descartaria o que cai entre as amostras — numa bolha de 4 mm, o que se descarta e tinta. Entao
 * desempena-se num tamanho maior e reduz-se com `INTER_AREA`, que integra a celula inteira.
 */
object RegionDetector {

    /** Quanto maior que o alvo se desempena antes de reduzir por media de area. */
    private const val SUPERSAMPLE = 3

    /** Resolucao da regiao retificada, em pixels por milimetro. A mesma da fixture versionada. */
    const val PX_PER_MM = 10

    /**
     * Sangria em volta do QR, em milimetros, no canvas que so ele usa.
     *
     * O padrao QR pede zona de silencio de quatro modulos. Na folha da prova o QR tem 14 mm
     * (`CaptureGeometry.QR_SIDE`) com 29 modulos por lado, entao quatro modulos sao cerca de
     * 1,9 mm. Quatro milimetros cobrem isso com folga e continuam dentro do papel: o QR fica
     * centrado entre os dois marcadores de cima, e ha margem de sobra em volta dele na folha
     * impressa.
     */
    private const val QR_BLEED_MM = 4

    /** Partes por milhao: a normalizacao em que o mapa declara posicao dentro do quadrilatero. */
    private const val PPM = 1_000_000L

    /**
     * Teto do erro de reprojecao dos cantos, em pixels da imagem de origem.
     *
     * Vale sobre a captura, e nao sobre a regiao retificada: e la que o erro nasce.
     */
    private const val MAX_REPROJECTION_PX = 6.0

    /** Os quatro ArUcos declarados pela pagina da regiao, por identificador. */
    private fun declaredMarkersOf(map: LayoutMap, region: ScannableRegion): Map<Int, DrawAruco> =
        map.pages
            .firstOrNull { it.index == region.page }
            ?.primitives
            ?.filterIsInstance<DrawAruco>()
            .orEmpty()
            .associateBy { it.markerId }

    /**
     * Os identificadores da regiao, na ordem tl, tr, bl, br.
     *
     * A ordem vem do que o **mapa desenha**, e nao da posicao na imagem: folha de cabeca para baixo
     * tem de falhar na reprojecao, e nao ser aceita girada em silencio.
     */
    private fun orderOf(declared: Map<Int, DrawAruco>, region: ScannableRegion): List<Int> =
        region.markerIds.sortedWith(compareBy({ declared.getValue(it).y }, { declared.getValue(it).x }))

    /**
     * Centros dos quatro marcadores na captura, em pixels da imagem de origem, ordem tl/tr/bl/br.
     *
     * Exposto porque e o ponto de comparacao com `papel.mjs`, que acha os mesmos marcadores por um
     * caminho que nao compartilha nada com o OpenCV.
     */
    fun markerCentersFor(gray: Mat, map: LayoutMap, region: ScannableRegion): List<Point>? {
        val declared = declaredMarkersOf(map, region)
        if (declared.size != 4) return null
        val found = detectMarkers(gray)
        if (region.markerIds.any { it !in found }) return null
        return orderOf(declared, region).map { centerOf(found.getValue(it)) }
    }

    private fun detectMarkers(gray: Mat): Map<Int, List<Point>> {
        val corners = ArrayList<Mat>()
        val ids = Mat()
        ArucoDetector(Objdetect.getPredefinedDictionary(Objdetect.DICT_5X5_100))
            .detectMarkers(gray, corners, ids)
        if (ids.empty()) return emptyMap()

        val found = HashMap<Int, List<Point>>()
        for (index in 0 until ids.rows()) {
            val id = ids.get(index, 0)[0].toInt()
            val quad = corners[index]
            found[id] = (0 until 4).map { Point(quad.get(0, it)[0], quad.get(0, it)[1]) }
        }
        return found
    }

    fun detect(gray: Mat, map: LayoutMap, region: ScannableRegion): DetectionOutcome {
        val declared = declaredMarkersOf(map, region)
        if (declared.size != 4) {
            return DetectionOutcome.Failed(
                "a pagina ${region.page} declara ${declared.size} ArUcos; esperados 4",
            )
        }

        val found = detectMarkers(gray)
        if (found.isEmpty()) {
            return DetectionOutcome.Failed("nenhum marcador ArUco encontrado na captura")
        }

        val missing = region.markerIds.filterNot { it in found }
        if (missing.isNotEmpty()) {
            return DetectionOutcome.Failed(
                "faltam os marcadores $missing na captura; achei ${found.keys.sorted()} e a regiao " +
                    "${region.index} declara ${region.markerIds}",
            )
        }

        val ordered = orderOf(declared, region)
        val centers = ordered.map { id -> centerOf(found.getValue(id)) }

        val target = listOf(
            Point(0.0, 0.0),
            Point(1.0, 0.0),
            Point(0.0, 1.0),
            Point(1.0, 1.0),
        )
        val homography = Imgproc.getPerspectiveTransform(
            MatOfPoint2f(*centers.toTypedArray()),
            MatOfPoint2f(*target.toTypedArray()),
        )

        val error = reprojectionErrorOf(homography, ordered, found, declared, region)
        if (!error.isFinite()) {
            return DetectionOutcome.Failed("o erro de reprojecao nao e finito: $error")
        }
        if (error > MAX_REPROJECTION_PX) {
            return DetectionOutcome.Failed(
                "a geometria nao fecha: erro de reprojecao de ${format(error)} px nos cantos dos " +
                    "marcadores, teto ${format(MAX_REPROJECTION_PX)} px",
            )
        }

        val width = region.quadWidth * PX_PER_MM / 1_000
        val height = region.quadHeight * PX_PER_MM / 1_000
        val big = Mat()
        // A homografia leva ao quadrado unitario; escalar para o tamanho grande e uma multiplicacao.
        val scaled = homography.clone()
        for (col in 0 until 3) {
            scaled.put(0, col, homography.get(0, col)[0] * width * SUPERSAMPLE)
            scaled.put(1, col, homography.get(1, col)[0] * height * SUPERSAMPLE)
        }
        Imgproc.warpPerspective(
            gray,
            big,
            scaled,
            Size((width * SUPERSAMPLE).toDouble(), (height * SUPERSAMPLE).toDouble()),
            Imgproc.INTER_LINEAR,
        )

        val small = Mat()
        Imgproc.resize(big, small, Size(width.toDouble(), height.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)

        val gray8 = Mat()
        small.convertTo(gray8, CvType.CV_8UC1)
        val pixels = ByteArray(width * height)
        gray8.get(0, 0, pixels)

        return DetectionOutcome.Rectified(
            region = RectifiedRegion(width, height, pixels),
            qrCanvas = rectifyQr(gray, homography, region, width, height),
            reprojectionErrorPx = error,
            detectedMarkerIds = ordered,
        )
    }

    /**
     * Retifica so a vizinhanca do QR, com [QR_BLEED_MM] de sangria em volta.
     *
     * A homografia leva a captura ao quadrado unitario do quadrilatero. Recortar um sub-retangulo
     * desse quadrado e escalar para um canvas proprio e multiplicacao de linhas, igual ao que a
     * retificacao principal ja faz — nao ha warp novo sobre a folha inteira, so sobre um retangulo
     * de poucos milimetros.
     *
     * A sangria pode sair do quadrilatero, e **deve**: e exatamente o que estava faltando. O
     * `warpPerspective` amostra fora do quadrilatero sem reclamar, e o que cai fora do papel vira
     * borda preta, que nao atrapalha o decodificador.
     */
    private fun rectifyQr(
        gray: Mat,
        homography: Mat,
        region: ScannableRegion,
        width: Int,
        height: Int,
    ): RectifiedRegion {
        // Sangria expressa na mesma normalizacao do mapa: partes por milhao do lado do quadrilatero.
        val bleedU = (QR_BLEED_MM * PX_PER_MM).toLong() * PPM / width
        val bleedV = (QR_BLEED_MM * PX_PER_MM).toLong() * PPM / height
        val u0 = (region.qr.u - bleedU).toDouble() / PPM
        val v0 = (region.qr.v - bleedV).toDouble() / PPM
        val u1 = (region.qr.u + region.qr.uSize + bleedU).toDouble() / PPM
        val v1 = (region.qr.v + region.qr.vSize + bleedV).toDouble() / PPM

        val canvasW = ((u1 - u0) * width).toInt().coerceAtLeast(1)
        val canvasH = ((v1 - v0) * height).toInt().coerceAtLeast(1)

        val cut = homography.clone()
        for (col in 0 until 3) {
            val h0 = homography.get(0, col)[0]
            val h1 = homography.get(1, col)[0]
            val h2 = homography.get(2, col)[0]
            cut.put(0, col, (h0 - u0 * h2) * canvasW / (u1 - u0))
            cut.put(1, col, (h1 - v0 * h2) * canvasH / (v1 - v0))
        }

        val out = Mat()
        Imgproc.warpPerspective(gray, out, cut, Size(canvasW.toDouble(), canvasH.toDouble()), Imgproc.INTER_LINEAR)
        val gray8 = Mat()
        out.convertTo(gray8, CvType.CV_8UC1)
        val buffer = ByteArray(canvasW * canvasH)
        gray8.get(0, 0, buffer)
        return RectifiedRegion(canvasW, canvasH, buffer)
    }

    private fun centerOf(quad: List<Point>): Point =
        Point(quad.sumOf { it.x } / quad.size, quad.sumOf { it.y } / quad.size)

    /**
     * Erro medio, em pixels, entre onde os cantos dos marcadores estao e onde o mapa diz que eles
     * deviam estar depois da homografia.
     *
     * Sedecim pontos que **nao** entraram no ajuste. E o que da sentido a palavra "erro" aqui.
     */
    private fun reprojectionErrorOf(
        homography: Mat,
        ordered: List<Int>,
        found: Map<Int, List<Point>>,
        declared: Map<Int, DrawAruco>,
        region: ScannableRegion,
    ): Double {
        val inverse = homography.inv()
        var sum = 0.0
        var count = 0
        for (id in ordered) {
            val aruco = declared.getValue(id)
            // Cantos do marcador no espaco do mapa, normalizados ao quadrilatero da regiao.
            val cornersUv = listOf(
                aruco.x to aruco.y,
                aruco.x + aruco.side to aruco.y,
                aruco.x + aruco.side to aruco.y + aruco.side,
                aruco.x to aruco.y + aruco.side,
            ).map { (x, y) ->
                Point(
                    (x - region.quadX).toDouble() / region.quadWidth,
                    (y - region.quadY).toDouble() / region.quadHeight,
                )
            }

            // O OpenCV devolve os cantos em sentido horario a partir do superior esquerdo, que e a
            // mesma ordem construida acima.
            val observed = found.getValue(id)
            for (index in cornersUv.indices) {
                val expected = mapPoint(inverse, cornersUv[index])
                sum += hypot(expected.x - observed[index].x, expected.y - observed[index].y)
                count += 1
            }
        }
        return if (count == 0) Double.NaN else sum / count
    }

    private fun mapPoint(matrix: Mat, point: Point): Point {
        val w = matrix.get(2, 0)[0] * point.x + matrix.get(2, 1)[0] * point.y + matrix.get(2, 2)[0]
        return Point(
            (matrix.get(0, 0)[0] * point.x + matrix.get(0, 1)[0] * point.y + matrix.get(0, 2)[0]) / w,
            (matrix.get(1, 0)[0] * point.x + matrix.get(1, 1)[0] * point.y + matrix.get(1, 2)[0]) / w,
        )
    }

    private fun format(value: Double): String = ((value * 100).roundToInt() / 100.0).toString()
}
