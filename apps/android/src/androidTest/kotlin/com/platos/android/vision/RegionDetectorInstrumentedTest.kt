package com.platos.android.vision

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.android.omr.BubbleMeter
import com.platos.android.omr.MeterOutcome
import com.platos.domain.layout.LayoutMap
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * A metade de **deteccao**, que so existe no emulador (§13).
 *
 * O que da valor a este teste e a independencia: `papel.mjs` acha os marcadores por componentes
 * conexos sobre limiar, e o OpenCV acha por contorno e casamento de dicionario. Sao dois
 * detectores que nao se conhecem, sobre a mesma imagem. Se os quatro cantos coincidirem, a
 * deteccao esta provada duas vezes — e de quebra fica provado que o `ArucoDictionary` que o Layout
 * Engine **desenha** e mesmo o `DICT_5X5_100` que o OpenCV **le**, que ate agora ninguem tinha
 * conferido.
 */
@RunWith(AndroidJUnit4::class)
class RegionDetectorInstrumentedTest {

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

    private fun capture(asset: String): Mat {
        val bytes = context.assets.open(asset).use { it.readBytes() }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw AssertionError("nao consegui decodificar $asset")
        val colorido = Mat()
        Utils.bitmapToMat(bitmap, colorido)
        val gray = Mat()
        Imgproc.cvtColor(colorido, gray, Imgproc.COLOR_RGBA2GRAY)
        return gray
    }

    private fun rectified(asset: String): DetectionOutcome.Rectified {
        val outcome = RegionDetector.detect(capture(asset), map, region)
        return outcome as? DetectionOutcome.Rectified
            ?: throw AssertionError("esperava retificacao, veio $outcome")
    }

    @Test
    fun acha_os_quatro_marcadores_e_desempena_a_regiao() {
        val resultado = rectified(PROVA)

        assertEquals(1_660, resultado.region.width)
        assertEquals(850, resultado.region.height)
        assertTrue(
            "erro de reprojecao de ${resultado.reprojectionErrorPx} px",
            resultado.reprojectionErrorPx < 6.0,
        )
    }

    @Test
    fun os_cantos_do_opencv_coincidem_com_os_de_papel_mjs() {
        // A referencia foi medida por outro detector, em outra linguagem, e esta versionada.
        val referencia = Json.parseToJsonElement(
            context.assets.open("prova-referencia.papel.json").use { it.readBytes().decodeToString() },
        ).jsonObject["cantos"]!!.jsonArray.map {
            val ponto = it.jsonObject
            ponto["x"]!!.jsonPrimitive.content.toDouble() to ponto["y"]!!.jsonPrimitive.content.toDouble()
        }

        val gray = capture(PROVA)
        val meus = RegionDetector.markerCentersFor(gray, map, region)
            ?: throw AssertionError("o detector nao devolveu os quatro centros")

        assertEquals(4, meus.size)
        for (index in meus.indices) {
            val (refX, refY) = referencia[index]
            val distancia = hypot(meus[index].x - refX, meus[index].y - refY)
            assertTrue(
                "canto $index: OpenCV (${meus[index].x}, ${meus[index].y}) contra papel.mjs " +
                    "($refX, $refY) — $distancia px de distancia",
                distancia < TOLERANCIA_CANTO_PX,
            )
        }
    }

    @Test
    fun a_medicao_sobre_a_regiao_desempenada_bate_com_a_referencia() {
        // O caminho inteiro de §8 menos o QR: imagem -> ArUcos -> homografia -> retificacao ->
        // cobertura, contra o que `papel.mjs` mediu na mesma folha.
        val resultado = rectified(PROVA)
        val outcome = BubbleMeter.measure(map, region, resultado.region)
        val medida = outcome as? MeterOutcome.Measured
            ?: throw AssertionError("esperava medicao, veio $outcome")

        assertEquals(160, medida.measurements.size)

        val esperado = Json.parseToJsonElement(
            context.assets.open("prova-referencia.papel.json").use { it.readBytes().decodeToString() },
        ).jsonObject["bolhas"]!!.jsonArray.associate {
            val bolha = it.jsonObject
            bolha["id"]!!.jsonPrimitive.content to
                (bolha["cobertura"]!!.jsonPrimitive.content.toDouble() * 1_000).toInt()
        }

        var maior = 0
        var pior = ""
        for (medicao in medida.measurements) {
            val referencia = esperado.getValue(medicao.id)
            val desvio = abs(medicao.coveragePerMille - referencia)
            if (desvio > maior) {
                maior = desvio
                pior = "${medicao.id}: emulador ${medicao.coveragePerMille}, papel.mjs $referencia"
            }
        }
        assertTrue("divergencia de $maior por mil — $pior", maior <= TOLERANCIA_COBERTURA_PER_MILLE)
    }

    @Test
    fun folha_de_outra_prova_sob_estes_marcadores_e_recusada() {
        // A folha de teste de impressao usa os mesmos quatro identificadores de marcador, mas a
        // regiao dela tem outra altura. Ler uma contra o mapa da outra tem de falhar.
        val outroMapa: LayoutMap = Json.decodeFromString(
            context.assets.open("folha-de-teste.layout.json").use { it.readBytes().decodeToString() },
        )
        val outcome = RegionDetector.detect(capture(PROVA), outroMapa, outroMapa.regions.single())

        val falha = outcome as? DetectionOutcome.Failed
            ?: throw AssertionError("esperava recusa, veio $outcome")
        // Uma recusa que acontece pelo motivo errado e uma guarda que nao guarda o que se pensa:
        // os identificadores sao os mesmos nas duas folhas, entao o que precisa reprovar aqui e a
        // **geometria** — a regiao da folha de teste tem outra altura.
        assertTrue(
            "recusou pelo motivo errado: ${falha.reason}",
            falha.reason.contains("geometria") || falha.reason.contains("reprojecao"),
        )
    }

    @Test
    fun captura_sem_marcador_nenhum_e_recusada() {
        val branco = Mat.ones(1_000, 1_000, org.opencv.core.CvType.CV_8UC1)
        val outcome = RegionDetector.detect(branco, map, region)

        assertTrue("esperava recusa, veio $outcome", outcome is DetectionOutcome.Failed)
        assertTrue(
            (outcome as DetectionOutcome.Failed).reason.contains("marcador"),
            )
    }

    @Test
    fun le_o_qr_da_regiao_retificada() {
        // §8: o QR e decodificado **depois** da homografia, na ROI que o mapa declara. O payload
        // desta folha ja tinha sido lido por um celular na conferencia de papel da fatia 2b, e
        // batia com `prova-referencia-slice-1...0.05CB`.
        val resultado = rectified(PROVA)
        val centros = RegionDetector.markerCentersFor(capture(PROVA), map, region)
        assertTrue("nao achei os marcadores", centros != null)

        val outcome = RegionQrReader.read(resultado.region, region, region.markerIds)
        val lido = outcome as? QrOutcome.Read
            ?: throw AssertionError("esperava payload, veio $outcome")

        assertEquals("prova-referencia-slice-1", lido.payload.examShortId)
        assertEquals(0, lido.payload.regionIndex)
        // Vazios ate a fatia 7 criar exam_assignment e variantes.
        assertEquals("", lido.payload.studentToken)
        assertEquals("", lido.payload.variant)
    }

    @Test
    fun qr_de_outra_regiao_e_recusado_pelos_marcadores() {
        // A redundancia de §8: o `region_idx` do payload confere com os identificadores achados. A
        // regiao 0 usa os marcadores 0..3; fingir que a captura trouxe 4..7 tem de reprovar, e e
        // exatamente o caso da folha de outra regiao na pilha.
        val resultado = rectified(PROVA)
        val outcome = RegionQrReader.read(resultado.region, region, listOf(4, 5, 6, 7))

        val falha = outcome as? QrOutcome.Failed
            ?: throw AssertionError("esperava recusa, veio $outcome")
        assertTrue(
            "recusou pelo motivo errado: ${falha.reason}",
            falha.reason.contains("marcadores"),
        )
    }

    @Test
    fun regiao_sem_qr_na_roi_declarada_e_recusada() {
        // A ROI e apagada: o decodificador tem de dizer que nao achou, e nao devolver o QR de
        // outro lugar do quadro.
        val original = rectified(PROVA).region
        val apagado = ByteArray(original.width * original.height)
        for (y in 0 until original.height) {
            for (x in 0 until original.width) {
                val naRoi = x > original.width * 0.4 && x < original.width * 0.6 &&
                    y < original.height * 0.25
                apagado[y * original.width + x] =
                    (if (naRoi) 255 else original.luminanceAt(x, y)).toByte()
            }
        }

        val outcome = RegionQrReader.read(
            com.platos.android.omr.RectifiedRegion(original.width, original.height, apagado),
            region,
            region.markerIds,
        )
        assertTrue("esperava recusa, veio $outcome", outcome is QrOutcome.Failed)
    }

    private companion object {
        const val PROVA = "prova-referencia.digitalizacao.jpg"

        /**
         * Distancia admitida entre o centro achado pelo OpenCV e o achado por `papel.mjs`.
         *
         * Dois pixels a 9,92 px/mm sao 0,2 mm. Os dois detectores definem "centro" de formas
         * diferentes — o OpenCV pela media dos quatro cantos do contorno, `papel.mjs` pelo centro
         * da caixa envolvente dos pixels escuros —, entao coincidencia exata nao existe nem
         * deveria.
         */
        const val TOLERANCIA_CANTO_PX = 3.0

        /**
         * Divergencia admitida na cobertura, em partes por mil.
         *
         * Maior que os 20 do teste de host porque aqui ha uma diferenca a mais: a retificacao e a
         * de producao, com `warpPerspective` mais `INTER_AREA`, e nao a de `recorte.mjs`.
         */
        const val TOLERANCIA_COBERTURA_PER_MILLE = 30
    }
}
