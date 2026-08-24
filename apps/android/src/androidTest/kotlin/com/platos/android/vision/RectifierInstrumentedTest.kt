package com.platos.android.vision

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.android.omr.BubbleMeter
import com.platos.android.omr.MeterOutcome
import com.platos.domain.layout.DrawAruco
import com.platos.domain.layout.DrawCircle
import com.platos.domain.layout.LayoutMap
import kotlin.math.abs
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * O retificador de producao, conferido contra uma resposta conhecida **por construcao**.
 *
 * A fixture da folha real prova a medicao, e nao o retificador: aquele recorte foi desempenado uma
 * vez por `tools/parity/recorte.mjs`, fora do caminho de producao. Aqui a folha e desenhada com
 * fracoes exatas, distorcida em perspectiva e devolvida ao `RegionDetector` — se a fracao
 * sobreviver, `warpPerspective` mais `INTER_AREA` estao preservando tinta.
 *
 * As duas fracoes usadas sao 0,5 e 1,0 de proposito: as duas dispensam qualquer calculo de area.
 * Meia bolha e a corda passando pelo centro; bolha cheia e o disco inteiro. Um oracle que precisa
 * de aritmetica propria e um oracle que pode errar.
 */
@RunWith(AndroidJUnit4::class)
class RectifierInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().context

    private val map: LayoutMap by lazy {
        Json.decodeFromString(
            context.assets.open("prova-referencia.layout.json").use { it.readBytes().decodeToString() },
        )
    }
    private val region by lazy { map.regions.single() }

    @Before
    fun carregaOpenCv() {
        assertTrue("o OpenCV nativo nao carregou", OpenCVLoader.initLocal())
    }

    /**
     * Desenha uma folha sintetica com os quatro ArUcos de verdade e as bolhas na fracao pedida.
     *
     * Os marcadores saem de `DrawAruco.modules`, que e o mesmo dado que o Layout Engine manda o
     * renderizador desenhar. Nao ha atalho: se o dicionario estiver errado, o OpenCV nao acha nada.
     */
    private fun sheet(fraction: Double): Mat {
        val canvas = Mat(HEIGHT_PX, WIDTH_PX, CvType.CV_8UC1)
        canvas.setTo(org.opencv.core.Scalar(PAPER.toDouble()))
        val pixels = ByteArray(WIDTH_PX * HEIGHT_PX)
        canvas.get(0, 0, pixels)

        for (aruco in map.pages[region.page].primitives.filterIsInstance<DrawAruco>()) {
            val modulePx = aruco.module * PX_PER_MM / 1_000
            for (row in aruco.modules.indices) {
                for (col in aruco.modules[row].indices) {
                    // '1' e modulo **branco** no dicionario deste projeto? Nao: `markerModules`
                    // devolve '1' onde o modulo e preto, que e como o renderizador desenha.
                    val preto = aruco.modules[row][col] == '1'
                    val x0 = aruco.x * PX_PER_MM / 1_000 + col * modulePx
                    val y0 = aruco.y * PX_PER_MM / 1_000 + row * modulePx
                    for (y in y0 until y0 + modulePx) {
                        for (x in x0 until x0 + modulePx) {
                            if (x in 0 until WIDTH_PX && y in 0 until HEIGHT_PX) {
                                pixels[y * WIDTH_PX + x] = (if (preto) INK else PAPER).toByte()
                            }
                        }
                    }
                }
            }
        }

        val radiusUm = map.pages[region.page].primitives
            .filterIsInstance<DrawCircle>()
            .minOf { it.diameter / 2 - it.stroke }
        val radiusPx = radiusUm * PX_PER_MM / 1_000.0

        for (bubble in region.bubbles) {
            val cx = (region.quadX + bubble.u.toDouble() / 1_000_000 * region.quadWidth) *
                PX_PER_MM / 1_000
            val cy = (region.quadY + bubble.v.toDouble() / 1_000_000 * region.quadHeight) *
                PX_PER_MM / 1_000
            for (y in (cy - radiusPx).toInt() - 1..(cy + radiusPx).toInt() + 1) {
                for (x in (cx - radiusPx).toInt() - 1..(cx + radiusPx).toInt() + 1) {
                    if (x !in 0 until WIDTH_PX || y !in 0 until HEIGHT_PX) continue
                    // Suavizado, e a razao e a mesma do gerador da JVM: borda dura poe ~20 por mil
                    // de vies, e ai o teste de perspectiva estaria medindo o desenho em vez do
                    // `warpPerspective`. Fracao 0,5 = corda pelo centro; 1,0 = disco inteiro.
                    var dentro = 0
                    for (sy in 0 until SUB) {
                        for (sx in 0 until SUB) {
                            val px = x + (sx + 0.5) / SUB - 0.5
                            val py = y + (sy + 0.5) / SUB - 0.5
                            val dx = px - cx
                            val dy = py - cy
                            if (dx * dx + dy * dy > radiusPx * radiusPx) continue
                            if (fraction >= 1.0 || py < cy) dentro += 1
                        }
                    }
                    if (dentro == 0) continue
                    val tinta = dentro.toDouble() / (SUB * SUB)
                    val valor = PAPER + (INK - PAPER) * tinta
                    pixels[y * WIDTH_PX + x] = Math.round(valor).toInt().toByte()
                }
            }
        }

        canvas.put(0, 0, pixels)
        return canvas
    }

    /** Distorce a folha como uma foto tirada de canto: os quatro cantos vao para dentro. */
    private fun skew(sheet: Mat): Mat {
        val origem = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(WIDTH_PX.toDouble(), 0.0),
            Point(WIDTH_PX.toDouble(), HEIGHT_PX.toDouble()),
            Point(0.0, HEIGHT_PX.toDouble()),
        )
        val destino = MatOfPoint2f(
            Point(WIDTH_PX * 0.06, HEIGHT_PX * 0.02),
            Point(WIDTH_PX * 0.97, HEIGHT_PX * 0.09),
            Point(WIDTH_PX * 0.93, HEIGHT_PX * 0.98),
            Point(WIDTH_PX * 0.02, HEIGHT_PX * 0.91),
        )
        val destinoMat = Mat()
        Imgproc.warpPerspective(
            sheet,
            destinoMat,
            Imgproc.getPerspectiveTransform(origem, destino),
            Size(WIDTH_PX.toDouble(), HEIGHT_PX.toDouble()),
            Imgproc.INTER_LINEAR,
            org.opencv.core.Core.BORDER_CONSTANT,
            org.opencv.core.Scalar(PAPER.toDouble()),
        )
        return destinoMat
    }

    private fun measure(captura: Mat): List<Int> {
        val outcome = RegionDetector.detect(captura, map, region)
        val rectified = outcome as? DetectionOutcome.Rectified
            ?: throw AssertionError("esperava retificacao, veio $outcome")
        val medida = BubbleMeter.measure(map, region, rectified.region) as? MeterOutcome.Measured
            ?: throw AssertionError("a medicao falhou sobre a folha sintetica")
        return medida.measurements.map { it.coveragePerMille }
    }

    @Test
    fun a_fracao_conhecida_sobrevive_a_folha_de_frente() {
        for ((fracao, esperado) in listOf(0.5 to ESPERADO_METADE, 1.0 to ESPERADO_CHEIA)) {
            for (medido in measure(sheet(fracao))) {
                assertTrue(
                    "de frente, fracao $fracao: medido $medido, esperado $esperado",
                    abs(medido - esperado) <= TOLERANCIA_PER_MILLE,
                )
            }
        }
    }

    @Test
    fun a_fracao_conhecida_sobrevive_a_distorcao_em_perspectiva() {
        // O teste que a tarefa 5.4 nao podia fazer na JVM: aqui a retificacao e a de producao.
        for ((fracao, esperado) in listOf(0.5 to ESPERADO_METADE, 1.0 to ESPERADO_CHEIA)) {
            val medidas = measure(skew(sheet(fracao)))
            var pior = 0
            for (medido in medidas) pior = maxOf(pior, abs(medido - esperado))
            assertTrue(
                "em perspectiva, fracao $fracao: pior desvio $pior por mil, esperado $esperado",
                pior <= TOLERANCIA_PERSPECTIVA_PER_MILLE,
            )
        }
    }

    private companion object {
        const val PX_PER_MM = 10
        const val SUB = 8
        const val WIDTH_PX = 210 * PX_PER_MM
        const val HEIGHT_PX = 150 * PX_PER_MM
        const val PAPER = 240
        const val INK = 20

        /** Bolha cheia de [INK] sobre papel [PAPER] mede `1 - INK/PAPER`, e nao 1. */
        const val ESPERADO_CHEIA = 917
        const val ESPERADO_METADE = 458

        /**
         * Desvio de frente, em partes por mil. Os dois casos medidos:
         *
         * - fracao 0,5 -> **8**
         * - fracao 1,0 -> **22**
         *
         * A diferenca e a coroa do disco, e ela cresce com a tinta: o gerador suaviza a borda do
         * circulo e o medidor usa mascara dura, entao quanto mais cheia a bolha, mais tinta
         * parcial fica na coroa que a mascara conta inteira. E o mesmo residuo que o teste de host
         * ja registra em 11 por mil, maior aqui porque a folha passa por mais uma reamostragem.
         *
         * Vinte e seis da folga de quatro passos sobre o pior caso.
         */
        const val TOLERANCIA_PER_MILLE = 26

        /**
         * Desvio em perspectiva: **17** na fracao 0,5 e **30** na 1,0.
         *
         * Comparado com os 8 e 22 de frente, **a distorcao custa cerca de 9 pontos** — e e esse
         * numero que este teste existe para vigiar. Se ele crescer, algo mudou no caminho
         * `warpPerspective` mais `INTER_AREA`.
         *
         * Trinta e seis da folga de seis passos. A primeira versao usava 40 sobre um gerador de
         * borda dura que sozinho ja punha 20 por mil de vies: passava medindo o proprio desenho, e
         * a perspectiva podia ter dobrado de custo sem ninguem ver.
         */
        const val TOLERANCIA_PERSPECTIVA_PER_MILLE = 36
    }
}
