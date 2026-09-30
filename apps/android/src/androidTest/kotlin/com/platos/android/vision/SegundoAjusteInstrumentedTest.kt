package com.platos.android.vision

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.domain.layout.DrawQr
import com.platos.domain.layout.ScannableRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * O segundo ajuste da regiao discursiva e a conferencia pelo maior residuo
 * (`slice-5c-0-o-recorte-da-resposta`, tarefas 3.1 e 3.2; ADR-0018 decisao 3, ADR-0020).
 *
 * Os quadros sao o documento da folha de `tok-a` (o renderizador de producao, rasterizado), de frente
 * e em perspectiva. **Nada aqui e papel.**
 *
 * **O que 3.1 tem de criterio, fixado antes da primeira execucao (ADR-0007):** de frente, o maior
 * residuo fica abaixo de **0,2 mm** — o piso do instrumento, que o ADR-0020 nomeia como a hipotese a
 * medir (o erro da primeira homografia se cancela na ida e volta). Se nao ficar, a hipotese cai e o
 * design e reaberto; o numero nao e afrouxado (P11). Em perspectiva o criterio e o teto de 1,0 mm.
 */
@RunWith(AndroidJUnit4::class)
class SegundoAjusteInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val prova = FolhaDiscursivaRenderizada(instrumentation.context, instrumentation.targetContext)

    @Before
    fun carregaOpenCv() {
        assertTrue("o OpenCV nativo nao carregou", OpenCVLoader.initLocal())
    }

    private fun regiao(indice: Int): ScannableRegion = prova.folha.regions.single { it.index == indice }

    private fun ajustado(resultado: RegionDetector.SegundoAjuste): RegionDetector.SegundoAjuste.Ajustado =
        resultado as? RegionDetector.SegundoAjuste.Ajustado ?: throw AssertionError("esperava o ajuste fechado, veio $resultado")

    private fun recusado(resultado: RegionDetector.SegundoAjuste): String =
        (resultado as? RegionDetector.SegundoAjuste.Recusado
            ?: throw AssertionError("esperava recusa, veio $resultado")).motivo

    /**
     * A pagina de uma foto tirada de canto, moderada: os quatro cantos da pagina vao para dentro de 1 a
     * 4%. Fixada antes de ver qualquer resultado.
     */
    private fun emAngulo(pagina: Mat): Mat {
        val w = pagina.cols().toDouble()
        val h = pagina.rows().toDouble()
        val origem = MatOfPoint2f(Point(0.0, 0.0), Point(w, 0.0), Point(w, h), Point(0.0, h))
        val destino = MatOfPoint2f(
            Point(w * 0.03, h * 0.01), Point(w * 0.98, h * 0.04),
            Point(w * 0.96, h * 0.99), Point(w * 0.01, h * 0.96),
        )
        val saida = Mat()
        Imgproc.warpPerspective(
            pagina, saida, Imgproc.getPerspectiveTransform(origem, destino), Size(w, h), Imgproc.INTER_LINEAR,
            Core.BORDER_CONSTANT, Scalar(255.0),
        )
        return saida
    }

    private fun registra(rotulo: String, r: RegionDetector.SegundoAjuste.Ajustado) {
        Log.i(TAG, "$rotulo: maior residuo %.3f mm; onze: ".format(r.residuoMaxMm) + r.residuosMm.joinToString { "%.3f".format(it) })
    }

    // --- 3.1: o ajuste fecha, e o residuo sem deslocamento e o piso do instrumento ---

    @Test
    fun de_frente_o_maior_residuo_fica_abaixo_de_dois_decimos_de_milimetro() {
        for (indice in listOf(1, 2)) {
            val r = regiao(indice)
            val ajuste = ajustado(RegionDetector.segundoAjuste(prova.pagina(r.page), prova.folha, r))
            registra("frente, regiao $indice", ajuste)

            assertEquals(11, ajuste.residuosMm.size)
            assertTrue(
                "regiao $indice: maior residuo de frente %.3f mm; o criterio fixado e 0,2 mm".format(ajuste.residuoMaxMm),
                ajuste.residuoMaxMm < 0.2,
            )
        }
    }

    @Test
    fun em_perspectiva_o_ajuste_fecha_abaixo_do_teto() {
        for (indice in listOf(1, 2)) {
            val r = regiao(indice)
            val ajuste = ajustado(RegionDetector.segundoAjuste(emAngulo(prova.pagina(r.page)), prova.folha, r))
            registra("perspectiva, regiao $indice", ajuste)

            assertTrue(ajuste.residuoMaxMm <= RegionDetector.MAX_RESIDUAL_MM)
        }
    }

    // --- 3.2: cada recusa, com o motivo, e com a fixture isolando a camada ---

    /** Resíduo acima do teto: um ponto do QR, deslocado 10 mm, sobre pontos reais. O resto e valido. */
    @Test
    fun residuo_acima_do_teto_recusa_com_o_residuo_e_o_teto() {
        val r = regiao(1)
        val pagina = prova.pagina(r.page)
        val (observados, alvos) = pontosReais(r, pagina)
        val deslocados = observados.toMutableList()
        deslocados[9] = Point(deslocados[9].x + 100.0, deslocados[9].y) // topRight do QR, 100 px = 10 mm

        val motivo = recusado(RegionDetector.ajustar(deslocados, alvos, r))

        assertTrue("o motivo devia falar de residuo: $motivo", "residuo de" in motivo)
        assertTrue("o motivo devia trazer o teto de 1.0 mm: $motivo", "teto 1.0 mm" in motivo)
    }

    /** A camada do teto sozinha: `NaN` chega ao residuo e nao passa calado por `NaN > teto`. */
    @Test
    fun residuo_nao_finito_recusa_dizendo_que_nao_e_finito() {
        val motivo = RegionDetector.conferirResiduos(listOf(0.1, Double.NaN, 0.2), 1)
            ?: throw AssertionError("um residuo NaN passou calado pelo teto")

        assertTrue("o motivo devia dizer que nao e finito: $motivo", "nao e finito" in motivo)
        // E o controle: sem o NaN, o mesmo conjunto passa.
        assertEquals(null, RegionDetector.conferirResiduos(listOf(0.1, 0.2), 1))
        // E o infinito nao passa pelo teto por ser "maior que tudo" — recusa pelo mesmo motivo.
        assertTrue("nao e finito" in RegionDetector.conferirResiduos(listOf(Double.POSITIVE_INFINITY), 1)!!)
    }

    /** QR ilegivel: os marcadores estao la (a primeira retificacao passa), o QR foi apagado. */
    @Test
    fun qr_ilegivel_recusa_pelo_motivo_do_qr_e_nao_por_residuo() {
        val r = regiao(1)
        val pagina = prova.pagina(r.page)
        val qr = prova.folha.pages.single { it.index == r.page }.primitives
            .filterIsInstance<DrawQr>().single { it.id == r.qrId }
        // Micrometros para pixels a 10 px/mm: dividir por 100. Margem de 2 px para cobrir o simbolo.
        Imgproc.rectangle(
            pagina,
            Point(qr.x / 100.0 - 2, qr.y / 100.0 - 2),
            Point((qr.x + qr.side) / 100.0 + 2, (qr.y + qr.side) / 100.0 + 2),
            Scalar(255.0),
            -1,
        )

        val motivo = recusado(RegionDetector.segundoAjuste(pagina, prova.folha, r))

        assertTrue("o motivo devia ser o do QR: $motivo", "QR" in motivo)
        assertTrue("nao devia ser recusa por residuo: $motivo", "residuo" !in motivo)
    }

    /** O gabarito nao declara area de resposta. */
    @Test
    fun regiao_sem_area_de_resposta_e_recusada_com_o_motivo() {
        val gabarito = regiao(0)

        val motivo = recusado(RegionDetector.segundoAjuste(prova.pagina(gabarito.page), prova.folha, gabarito))

        assertEquals("a regiao 0 nao declara area de resposta", motivo)
    }

    /** Os onze pontos reais da regiao, como o segundo ajuste os monta (sem deslocar nenhum). */
    private fun pontosReais(r: ScannableRegion, pagina: Mat): Pair<List<Point>, List<Point>> {
        val found = RegionDetector.detectMarkers(pagina)
        val detection = RegionDetector.detect(pagina, prova.folha, r, found) as DetectionOutcome.Rectified
        val qr = RegionQrReader.read(detection.qrCanvas, detection.detectedMarkerIds, prova.folha) as QrOutcome.Read
        val pontos = RegionDetector.pontosDoSegundoAjuste(prova.folha, r, found, qr, detection.qrCanvas)
        val prontos = pontos as? RegionDetector.PontosDoAjuste.Prontos
            ?: throw AssertionError("esperava os onze pontos, veio $pontos")
        assertEquals(11, prontos.observados.size)
        return prontos.observados to prontos.alvos
    }

    private companion object {
        const val TAG = "Medida5c0"
    }
}
