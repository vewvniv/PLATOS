package com.platos.android.vision

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.domain.layout.DrawText
import com.platos.domain.layout.LayoutMap
import com.platos.domain.layout.ScannableRegion
import com.platos.domain.layout.ValidationResult
import com.platos.domain.layout.validate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/**
 * A garantia de "o recorte nao tem cabecalho", **camada 2**: a captura sobre o documento renderizado
 * (`slice-5c-0-o-recorte-da-resposta`, tarefa 6.1; design, decisao 6).
 *
 * A camada 1 e a validacao do `LayoutMap` (`AreaDeRespostaLimpaTest`, no dominio). Esta prova a cadeia
 * inteira — mapa, renderizador, homografia, recorte —, e existe para ver o que a camada 1 nao ve: o
 * `DrawText` nao carrega largura, e um texto que comeca **fora** da largura da regiao e a invade lendo
 * para a direita passa pela validacao. **As duas camadas falham por motivos disjuntos**, e o caso (c) e
 * o que carrega a independencia.
 *
 * O "cabecalho" e um nome de aluno impresso (o que a fatia 7, "dados impressos", vai fazer): uma linha
 * no topo da pagina e outra colada em cima da regiao. A tinta contada e a do aluno na area — o que a
 * mascara da moldura, do QR e dos marcadores deixa passar —, e o esperado, na folha limpa, e **zero**.
 * Nada aqui e papel.
 */
@RunWith(AndroidJUnit4::class)
class RecorteSemCabecalhoInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val prova = FolhaDiscursivaRenderizada(instrumentation.context, instrumentation.targetContext)

    @Before
    fun carregaOpenCv() {
        assertTrue("o OpenCV nativo nao carregou", OpenCVLoader.initLocal())
    }

    private fun regiao(indice: Int): ScannableRegion = prova.folha.regions.single { it.index == indice }

    private fun LayoutMap.com(r: ScannableRegion, vararg textos: DrawText): LayoutMap =
        copy(pages = pages.map { if (it.index == r.page) it.copy(primitives = it.primitives + textos) else it })

    private fun topoDaAreaUm(r: ScannableRegion): Int =
        (r.quadY + requireNotNull(r.answerArea).v.toLong() * r.quadHeight / 1_000_000L).toInt()

    private fun alturaDaAreaUm(r: ScannableRegion): Int =
        (requireNotNull(r.answerArea).vSize.toLong() * r.quadHeight / 1_000_000L).toInt()

    /** O nome do aluno no topo da pagina e colado em cima da regiao: como a fatia 7 vai imprimir. */
    private fun cabecalho(r: ScannableRegion): List<DrawText> = listOf(
        DrawText("cab-topo", x = 15_000, baseline = 8_000, size = 3_500, text = "Aluno: Fulano de Tal da Silva Sauro"),
        DrawText("cab-colado", x = r.quadX + 2_000, baseline = r.quadY - 1_000, size = 3_500, text = "Aluno: Fulano de Tal da Silva Sauro"),
    )

    private fun renderiza(mapa: LayoutMap, r: ScannableRegion, emAngulo: Boolean, blob: Boolean = false): Mat {
        val pagina = prova.pagina(r.page, mapa)
        if (blob) {
            val cx = r.quadX + r.quadWidth / 2
            val cy = topoDaAreaUm(r) + alturaDaAreaUm(r) / 2
            Imgproc.rectangle(pagina, Point((cx - 5_000) / 100.0, (cy - 5_000) / 100.0), Point((cx + 5_000) / 100.0 - 1, (cy + 5_000) / 100.0 - 1), Scalar(0.0), -1)
        }
        return if (emAngulo) FolhaEmAngulo.de(pagina) else pagina
    }

    /** Pixels escuros (< 128) de uma faixa retangular da pagina, em micrometros. */
    private fun escurosNaFaixa(pagina: Mat, x0: Int, y0: Int, x1: Int, y1: Int): Int {
        val faixa = pagina.submat(y0 / 100, y1 / 100, x0 / 100, x1 / 100)
        val escuros = Mat()
        org.opencv.core.Core.compare(faixa, Scalar(128.0), escuros, org.opencv.core.Core.CMP_LT)
        return org.opencv.core.Core.countNonZero(escuros)
    }

    private fun tintaDoRecorte(mapa: LayoutMap, r: ScannableRegion, pagina: Mat): Long {
        val recorte = SheetReader.recortar(pagina, mapa, r) as? RecorteOutcome.Recortado
            ?: throw AssertionError("esperava o recorte")
        return recorte.desvio.dentro
    }

    /** (a) Folha limpa, com o nome impresso no topo e colado em cima da regiao: zero tinta no recorte. */
    @Test
    fun com_o_nome_impresso_em_volta_o_recorte_tem_zero_de_tinta() {
        for (indice in listOf(1, 2)) {
            val r = regiao(indice)
            val mapa = prova.folha.com(r, *cabecalho(r).toTypedArray())
            assertEquals("a fixture (a) e um mapa valido", ValidationResult.Valid, mapa.validate())

            // Guarda de vacuidade da FIXTURE (P13): as duas linhas do nome foram mesmo desenhadas na pagina.
            val frontal = renderiza(mapa, r, emAngulo = false)
            val noTopo = escurosNaFaixa(frontal, 15_000, 3_000, 120_000, 9_000)
            val colado = escurosNaFaixa(frontal, r.quadX, r.quadY - 5_000, r.quadX + 60_000, r.quadY)
            Log.i("Medida5c0", "cabecalho regiao $indice: pixels escuros no topo=$noTopo colado em cima da regiao=$colado")
            assertTrue("regiao $indice: o nome do topo nao foi desenhado ($noTopo px)", noTopo > 300)
            assertTrue("regiao $indice: o nome colado na regiao nao foi desenhado ($colado px)", colado > 300)

            for (emAngulo in listOf(false, true)) {
                val nome = "regiao $indice ${if (emAngulo) "angulo" else "frente"}"

                // Guarda de vacuidade (P13): antes de afirmar zero, o mesmo pipeline conta uma tinta conhecida.
                val comBlob = tintaDoRecorte(mapa, r, renderiza(mapa, r, emAngulo, blob = true))
                assertTrue("$nome: o contador devia ter visto o quadrado plantado (viu $comBlob)", comBlob > 8_000)

                val limpa = tintaDoRecorte(mapa, r, renderiza(mapa, r, emAngulo))
                assertEquals("$nome: ha tinta no recorte de uma folha sem escrita", 0L, limpa)
            }
        }
    }

    private fun intrusoDentro(r: ScannableRegion) = DrawText(
        "intruso-dentro", x = r.quadX + 5_000, baseline = topoDaAreaUm(r) + alturaDaAreaUm(r) / 2,
        size = 3_500, text = "Aluno: Fulano de Tal da Silva Sauro",
    )

    private fun invasor(r: ScannableRegion) = DrawText(
        "intruso-invasor", x = r.quadX - 8_000, baseline = topoDaAreaUm(r) + alturaDaAreaUm(r) / 2,
        size = 3_500, text = "Aluno: Fulano de Tal da Silva Sauro, turma 7B, matricula 2026-0001, e um texto longo",
    )

    // ---- (b) texto que comeca DENTRO da largura da regiao e dentro da area (mapa montado a mao) ----

    /** Camada 1: a validacao recusa o mapa. Nao depende do recorte. */
    @Test
    fun b_camada_1_recusa_o_mapa_com_o_texto_dentro_da_area() {
        for (indice in listOf(1, 2)) {
            val r = regiao(indice)
            assertTrue("regiao $indice: a camada 1 devia recusar o mapa", prova.folha.com(r, intrusoDentro(r)).validate() is ValidationResult.Invalid)
        }
    }

    /** Camada 2: o recorte tem a tinta do texto. Nao depende da validacao. */
    @Test
    fun b_camada_2_ve_a_tinta_do_texto_dentro_da_area() {
        for (indice in listOf(1, 2)) {
            val r = regiao(indice)
            val mapa = prova.folha.com(r, intrusoDentro(r))
            for (emAngulo in listOf(false, true)) {
                val tinta = tintaDoRecorte(mapa, r, renderiza(mapa, r, emAngulo))
                Log.i("Medida5c0", "intruso dentro regiao $indice ${if (emAngulo) "angulo" else "frente"}: tinta no recorte=$tinta")
                assertTrue("regiao $indice ${if (emAngulo) "angulo" else "frente"}: a camada 2 devia ver o texto (viu $tinta)", tinta > 500)
            }
        }
    }

    // ---- (c) texto que comeca FORA da largura da regiao e a invade lendo para a direita ----

    /** Camada 1: a validacao ACEITA o mapa (limite conhecido, pinado tambem no dominio). */
    @Test
    fun c_camada_1_aceita_o_mapa_com_o_texto_que_invade_de_fora() {
        for (indice in listOf(1, 2)) {
            val r = regiao(indice)
            assertEquals("regiao $indice: a camada 1 nao devia ver o invasor", ValidationResult.Valid, prova.folha.com(r, invasor(r)).validate())
        }
    }

    /** Camada 2: so ela ve a tinta do invasor. */
    @Test
    fun c_camada_2_ve_a_tinta_do_texto_que_invade_de_fora() {
        for (indice in listOf(1, 2)) {
            val r = regiao(indice)
            val mapa = prova.folha.com(r, invasor(r))
            for (emAngulo in listOf(false, true)) {
                val tinta = tintaDoRecorte(mapa, r, renderiza(mapa, r, emAngulo))
                Log.i("Medida5c0", "invasor regiao $indice ${if (emAngulo) "angulo" else "frente"}: tinta no recorte=$tinta")
                assertTrue("regiao $indice ${if (emAngulo) "angulo" else "frente"}: a camada 2 devia ver o invasor (viu $tinta)", tinta > 500)
            }
        }
    }
}
