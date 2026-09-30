package com.platos.android.vision

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.domain.layout.DrawRect
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.Point
import kotlin.math.abs
import kotlin.math.max

/**
 * O quanto o recorte anda quando o residuo **aceita** o erro
 * (`slice-5c-0-o-recorte-da-resposta`, achado da mutacao C da tarefa 4.1; P8).
 *
 * A mutacao "canto `QR.tr` deslocado 2 mm" passou pelo teto de 1,0 mm (residuo 0,71 mm, tarefa 3.3) e
 * levou a moldura para fora da janela de +-1,2 mm. Aqui a pergunta e medida, e nao descrita: para cada
 * ponto deslocado de 1 e 2 mm, quanto e o residuo e quanto a **moldura** se afasta do mapa no recorte
 * que esse ajuste produz. O resultado e o tamanho da lacuna do residuo como guarda da qualidade do
 * recorte. Sobre a folha de frente, renderizada. **Nada aqui e papel.**
 *
 * **As asserções fixam o que a primeira execução mostrou (2026-09-30), e nao um criterio anterior a
 * ela.** Com o erro de um ponto aceito pelo residuo: ate 1,0 mm de erro o recorte se move no maximo
 * 0,42 mm; ate 2,0 mm, no maximo 0,89 mm — e os dois casos acima de 0,5 mm sao exatamente os dois
 * pontos que a tarefa 3.3 apontou como os mais fracos (`QR.tr` em `+x`, `BR.c4` em `+y`). Isso e a
 * lacuna do residuo como guarda do recorte, pinada para nao virar silencio (P8).
 *
 * A borda esquerda da moldura nao e medida: ela coincide com a borda do recorte.
 
 */
@RunWith(AndroidJUnit4::class)
class CropSobResiduoAceitoInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val prova = FolhaDiscursivaRenderizada(instrumentation.context, instrumentation.targetContext)

    @Before
    fun carregaOpenCv() {
        assertTrue("o OpenCV nativo nao carregou", OpenCVLoader.initLocal())
    }

    private val nomes = listOf(
        "TL.c1", "TL.c2", "TL.c3", "TL.c4", "BR.c1", "BR.c2", "BR.c3", "BR.c4", "QR.tl", "QR.tr", "QR.bl",
    )

    /** O maior afastamento, em mm, das quatro bordas da moldura em relacao ao mapa, no recorte de [h]. */
    private fun desvioDaMoldura(regiaoIndex: Int, h: org.opencv.core.Mat): Double {
        val r = prova.folha.regions.single { it.index == regiaoIndex }
        val pagina = prova.pagina(r.page)
        val recorte = RecorteDaResposta.canvasDaResposta(pagina, r, h).miolo()
        val m = prova.folha.pages.single { it.index == r.page }.primitives
            .filterIsInstance<DrawRect>().single { it.id == "r${r.index}-moldura" }
        val esqUm = r.quadX + requireNotNull(r.answerArea).u.toDouble() * r.quadWidth / 1_000_000.0
        val topoUm = r.quadY + requireNotNull(r.answerArea).v.toDouble() * r.quadHeight / 1_000_000.0
        val bordas = listOf(
            Perfil.colunaEscura(recorte, (m.x - esqUm) / 100.0, JANELA) to (m.x - esqUm) / 100.0,
            Perfil.colunaEscura(recorte, (m.x + m.width - esqUm) / 100.0, JANELA) to (m.x + m.width - esqUm) / 100.0,
            Perfil.linhaEscura(recorte, (m.y - topoUm) / 100.0, JANELA) to (m.y - topoUm) / 100.0,
            Perfil.linhaEscura(recorte, (m.y + m.height - topoUm) / 100.0, JANELA) to (m.y + m.height - topoUm) / 100.0,
        )
        // A borda esquerda da moldura coincide com a borda do recorte (a area ocupa a largura inteira
        // da regiao): o traco esta metade fora da imagem, e um deslocamento minimo o faz cruzar o limiar
        // de "escuro" nos poucos pixels visiveis. Nao e medida confiavel, e vira NULO sem ser desvio.
        // Medem-se direita, topo e base; o alinhamento horizontal e conferido pela tinta (tarefa 4.2).
        return bordas.drop(1).fold(0.0) { pior, (medido, esperado) ->
            max(pior, if (medido == null) JANELA / 10.0 else abs(medido - esperado) / 10.0)
        }
    }

    @Test
    fun a_moldura_se_afasta_pouco_quando_o_residuo_aceita_o_erro() {
        val indice = 1
        val r = prova.folha.regions.single { it.index == indice }
        val pagina = prova.pagina(r.page)
        val found = RegionDetector.detectMarkers(pagina)
        val deteccao = RegionDetector.detect(pagina, prova.folha, r, found) as DetectionOutcome.Rectified
        val qr = RegionQrReader.read(deteccao.qrCanvas, deteccao.detectedMarkerIds, prova.folha) as QrOutcome.Read
        val pontos = RegionDetector.pontosDoSegundoAjuste(prova.folha, r, found, qr, deteccao.qrCanvas)
            as RegionDetector.PontosDoAjuste.Prontos

        // Controle: sem deslocamento, a moldura fica a menos de 0,1 mm.
        val (h0, _) = RegionDetector.homografiaEResiduos(pontos.observados, pontos.alvos, r)!!
        val base = desvioDaMoldura(indice, h0)
        Log.i(TAG, "crop sem deslocamento: moldura a %.3f mm".format(base))
        assertTrue("o controle devia ficar abaixo de 0,1 mm, ficou %.3f".format(base), base < 0.1)

        // Os dois pontos que a tarefa 3.3 apontou como os mais fracos, com 2 mm: aceitos pelo residuo.
        val fracos = ArrayList<Double>()
        for ((direcao, dx, dy) in listOf(Triple("+x", 1.0, 0.0), Triple("+y", 0.0, 1.0))) {
            for (mm in listOf(0.2, 0.5, 1.0, 2.0)) {
                for (i in pontos.observados.indices) {
                    val d = pontos.observados.toMutableList()
                    d[i] = Point(d[i].x + dx * mm * 10.0, d[i].y + dy * mm * 10.0)
                    val (h, residuos) = RegionDetector.homografiaEResiduos(d, pontos.alvos, r)!!
                    val maxRes = residuos.max()
                    val aceito = maxRes <= RegionDetector.MAX_RESIDUAL_MM
                    val moldura = desvioDaMoldura(indice, h)
                    if (aceito) {
                        val rotulo = "${nomes[i]} $direcao %.1f mm".format(mm)
                        assertTrue("$rotulo: fit aceito e a moldura nao foi achada (%.2f)".format(moldura), moldura < JANELA / 10.0)
                        assertTrue("$rotulo: fit aceito e moldura a %.2f mm; a tabela mostrou <= 0,89".format(moldura), moldura <= 0.89 + 0.005)
                        if (mm <= 1.0) {
                            assertTrue("$rotulo: erro de ate 1 mm aceito e moldura a %.2f mm; a tabela mostrou <= 0,42".format(moldura), moldura <= 0.42 + 0.005)
                        }
                    }
                    if (aceito && mm == 2.0 && ((nomes[i] == "QR.tr" && direcao == "+x") || (nomes[i] == "BR.c4" && direcao == "+y"))) {
                        fracos += moldura
                    }
                    Log.i(
                        TAG,
                        "crop r$indice ${nomes[i].padEnd(6)} $direcao %.1fmm residuo=%.3f %s moldura=%.2f mm".format(
                            mm, maxRes, if (aceito) "ACEITO " else "recusa ", moldura,
                        ),
                    )
                }
            }
        }
        // A lacuna, pinada: os dois pontos fracos sao aceitos com 2 mm e movem a moldura alem de 0,5 mm.
        assertTrue("esperava os dois casos fracos aceitos com 2 mm; vieram $fracos", fracos.size == 2)
        assertTrue("os dois casos fracos deviam passar de 0,5 mm; vieram $fracos", fracos.all { it > 0.5 })
    }

    private companion object {
        const val TAG = "Medida5c0"
        const val JANELA = 120 // 12 mm: larga, para ler o afastamento em vez de so acusar a janela
    }
}
