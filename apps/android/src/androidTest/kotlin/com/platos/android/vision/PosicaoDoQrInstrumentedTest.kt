package com.platos.android.vision

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.android.omr.RectifiedRegion
import com.platos.domain.layout.ScannableRegion
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader

/**
 * O que `Result.position` do decodificador significa no canvas do QR
 * (`slice-5c-0-o-recorte-da-resposta`, tarefas 2.1 e 2.2; **ADR-0020**).
 *
 * **O criterio e o do ADR-0020, e nao o primeiro.** O primeiro — os tres cantos a ate 0,5 mm do canto
 * que o **mapa** declara — reprovou (regiao 2: 0,610 e 0,532 mm) porque media, juntos, o significado de
 * `position` e o erro da primeira homografia no canto do QR. O ADR o mantem como reprovado. O de agora:
 * `topLeft`, `topRight` e `bottomLeft` a **ate 0,5 mm** (a mesma tolerancia, nao afrouxada) da **caixa
 * escura do proprio simbolo** no canvas, medida numa janela de +-1,5 mm em volta do QR do mapa para
 * isolar o simbolo da moldura e dos marcadores vizinhos. A caixa e um oraculo de pixel que nao
 * compartilha codigo com o decodificador nem com o mapa (P4).
 *
 * **Este criterio nao e cego:** foi escolhido depois de um diagnostico que ja mostrava os cantos a
 * menos de 1 px do simbolo. Ele vale como oraculo independente e como guarda de regressao, e o ADR
 * diz isso.
 *
 * A distancia ao canto do **mapa** continua sendo registrada (`logcat -s Medida5c0`), rotulada como o
 * erro da primeira homografia — e nao e assercao.
 */
@RunWith(AndroidJUnit4::class)
class PosicaoDoQrInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val prova = FolhaDiscursivaRenderizada(instrumentation.context, instrumentation.targetContext)

    @Before
    fun carregaOpenCv() {
        assertTrue("o OpenCV nativo nao carregou", OpenCVLoader.initLocal())
    }

    private class Leitura(val regiao: ScannableRegion, val canvas: RectifiedRegion, val ids: List<Int>)

    private fun leitura(regiaoIndex: Int): Leitura {
        val regiao = prova.folha.regions.single { it.index == regiaoIndex }
        val pagina = prova.pagina(regiao.page)
        val deteccao = RegionDetector.detect(pagina, prova.folha, regiao)
        val retificada = deteccao as? DetectionOutcome.Rectified
            ?: throw AssertionError("esperava a regiao $regiaoIndex retificada, veio $deteccao")
        return Leitura(regiao, retificada.qrCanvas, retificada.detectedMarkerIds)
    }

    private fun QrOutcome.lido(): QrOutcome.Read =
        this as? QrOutcome.Read ?: throw AssertionError("esperava o QR lido, veio $this")

    /** As bordas do simbolo no canvas, em pixels: `esquerda`/`topo` inclusivas, `direita`/`base` exclusivas. */
    private class Caixa(val esquerda: Int, val topo: Int, val direita: Int, val base: Int)

    /** O lado do QR que o mapa declara, em pixels do canvas. Aritmetica propria, sem as constantes do detector. */
    private fun ladoDoQrPx(regiao: ScannableRegion): Double =
        regiao.qr.uSize.toDouble() * regiao.quadWidth / 1_000_000.0 / 1_000.0 * RegionDetector.PX_PER_MM

    /**
     * A menor caixa de pixels escuros (< metade do branco) dentro de +-1,5 mm da caixa do QR que o mapa
     * declara. O canvas cerca o QR de sangria igual dos quatro lados.
     */
    private fun caixaDoSimbolo(l: Leitura): Caixa {
        val lado = ladoDoQrPx(l.regiao)
        val sangriaX = (l.canvas.width - lado) / 2.0
        val sangriaY = (l.canvas.height - lado) / 2.0
        val folga = (1.5 * RegionDetector.PX_PER_MM).toInt()
        val xa = (sangriaX - folga).toInt().coerceAtLeast(0)
        val xb = (sangriaX + lado + folga).toInt().coerceAtMost(l.canvas.width - 1)
        val ya = (sangriaY - folga).toInt().coerceAtLeast(0)
        val yb = (sangriaY + lado + folga).toInt().coerceAtMost(l.canvas.height - 1)

        var x0 = Int.MAX_VALUE; var y0 = Int.MAX_VALUE; var x1 = -1; var y1 = -1
        for (y in ya..yb) for (x in xa..xb) {
            if (l.canvas.luminanceAt(x, y) < 128) {
                if (x < x0) x0 = x
                if (y < y0) y0 = y
                if (x > x1) x1 = x
                if (y > y1) y1 = y
            }
        }
        check(x1 >= 0) { "nenhum pixel escuro na janela do QR" }
        // Guarda de vacuidade (P13): uma janela que pegasse a moldura, ou nada, nao passa calada.
        val ladoMm = maxOf(x1 + 1 - x0, y1 + 1 - y0) / RegionDetector.PX_PER_MM.toDouble()
        assertTrue("a caixa do simbolo tem %.1f mm de lado; esperava entre 13 e 15".format(ladoMm), ladoMm in 13.0..15.0)
        return Caixa(x0, y0, x1 + 1, y1 + 1)
    }

    private class Medida(val nome: String, val mm: Double)

    /** Distancia de cada ancora ao canto correspondente da caixa do simbolo, em mm. */
    private fun medir(posicao: PosicaoDoQr, caixa: Caixa): List<Medida> {
        val alvo = listOf(
            Triple("topLeft", posicao.topLeft, caixa.esquerda to caixa.topo),
            Triple("topRight", posicao.topRight, caixa.direita to caixa.topo),
            Triple("bottomLeft", posicao.bottomLeft, caixa.esquerda to caixa.base),
        )
        return alvo.map { (nome, ponto, canto) ->
            Medida(nome, hypot((ponto.x - canto.first).toDouble(), (ponto.y - canto.second).toDouble()) / RegionDetector.PX_PER_MM)
        }
    }

    /** Dado, nao criterio: quanto o simbolo esta fora do canto que o mapa declara, dentro do canvas. */
    private fun registraErroDaPrimeiraHomografia(indice: Int, l: Leitura, caixa: Caixa) {
        val lado = ladoDoQrPx(l.regiao)
        val sangriaX = (l.canvas.width - lado) / 2.0
        val sangriaY = (l.canvas.height - lado) / 2.0
        val dx = (caixa.esquerda - sangriaX) / RegionDetector.PX_PER_MM
        val dy = (caixa.topo - sangriaY) / RegionDetector.PX_PER_MM
        Log.i(TAG, "regiao $indice: erro da primeira homografia no canto do QR (TL do simbolo - TL do mapa) = (%.2f, %.2f) mm".format(dx, dy))
    }

    /** Tarefa 2.1: a leitura valida traz os quatro cantos, dentro do canvas. */
    @Test
    fun a_leitura_valida_traz_a_posicao_do_qr_dentro_do_canvas() {
        for (indice in listOf(1, 2)) {
            val l = leitura(indice)
            val lido = RegionQrReader.read(l.canvas, l.ids, prova.folha).lido()

            val cantos = listOf(
                lido.position.topLeft, lido.position.topRight,
                lido.position.bottomRight, lido.position.bottomLeft,
            )
            for (canto in cantos) {
                assertTrue("canto $canto fora do canvas ${l.canvas.width}x${l.canvas.height}", l.canvas.contains(canto.x, canto.y))
            }
            assertEquals(indice, lido.payload.regionIndex)
        }
    }

    /** Tarefa 2.2, criterio do ADR-0020: os tres cantos de ancoragem a ate 0,5 mm do simbolo. */
    @Test
    fun os_tres_cantos_de_ancoragem_ficam_a_meio_milimetro_do_simbolo() {
        for (indice in listOf(1, 2)) {
            val l = leitura(indice)
            val lido = RegionQrReader.read(l.canvas, l.ids, prova.folha).lido()
            val caixa = caixaDoSimbolo(l)
            val medidas = medir(lido.position, caixa)
            Log.i(TAG, "regiao $indice: ancora-a-simbolo " + medidas.joinToString { "${it.nome}=%.3f mm".format(it.mm) })
            registraErroDaPrimeiraHomografia(indice, l, caixa)

            for (medida in medidas) {
                assertTrue(
                    "regiao $indice: ${medida.nome} a %.3f mm do simbolo; o criterio do ADR-0020 e 0,5 mm".format(medida.mm),
                    medida.mm <= TOLERANCIA_MM,
                )
            }
        }
    }

    /**
     * Visto falhar (P13): o mesmo instrumento, com as ancoras deslocadas 2 mm (20 px) para a direita
     * sobre a leitura real, tem de acusar. Sem isto, o teste acima passaria se `medir` devolvesse
     * sempre zero.
     */
    @Test
    fun o_instrumento_acusa_ancoras_deslocadas_dois_milimetros() {
        val l = leitura(1)
        val lido = RegionQrReader.read(l.canvas, l.ids, prova.folha).lido()
        val p = lido.position
        fun PontoDoCanvas.direita() = PontoDoCanvas(x + 20, y)
        val deslocada = PosicaoDoQr(p.topLeft.direita(), p.topRight.direita(), p.bottomRight.direita(), p.bottomLeft.direita())

        val medidas = medir(deslocada, caixaDoSimbolo(l))

        for (medida in medidas) {
            assertTrue("${medida.nome} devia estar a ~2 mm, esta a %.3f".format(medida.mm), medida.mm in 1.5..2.5)
            assertTrue("o criterio de 0,5 mm devia reprovar ${medida.nome}", medida.mm > TOLERANCIA_MM)
        }
    }

    private companion object {
        const val TAG = "Medida5c0"
        const val TOLERANCIA_MM = 0.5
    }
}
