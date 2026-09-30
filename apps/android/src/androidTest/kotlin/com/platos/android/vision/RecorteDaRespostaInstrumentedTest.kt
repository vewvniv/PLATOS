package com.platos.android.vision

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.android.omr.RectifiedRegion
import com.platos.domain.layout.DrawRect
import com.platos.domain.layout.ScannableRegion
import kotlin.math.abs
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
 * O recorte da area de resposta (`slice-5c-0-o-recorte-da-resposta`, tarefas 4.1, 4.2 e 4.3).
 *
 * Os quadros sao o documento da folha de `tok-a`, de frente e em [FolhaEmAngulo]. **Nada aqui e papel.**
 * A tinta do aluno e **sintetica**: retangulos pretos desenhados sobre a pagina, com posicao conhecida
 * pelo mapa, e nao letra.
 *
 * **O oraculo da posicao nao e o warp.** A moldura impressa e localizada nos pixels do recorte por
 * perfil de intensidade, e comparada com onde o **mapa** diz que ela esta (aritmetica propria, sem as
 * constantes do detector). Tolerancia de 0,5 mm, fixada antes da primeira execucao.
 */
@RunWith(AndroidJUnit4::class)
class RecorteDaRespostaInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val prova = FolhaDiscursivaRenderizada(instrumentation.context, instrumentation.targetContext)

    @Before
    fun carregaOpenCv() {
        assertTrue("o OpenCV nativo nao carregou", OpenCVLoader.initLocal())
    }

    private fun regiao(indice: Int): ScannableRegion = prova.folha.regions.single { it.index == indice }

    private fun recortado(r: RecorteOutcome): RecorteOutcome.Recortado =
        r as? RecorteOutcome.Recortado ?: throw AssertionError("esperava o recorte, veio $r")

    private fun moldura(r: ScannableRegion): DrawRect =
        prova.folha.pages.single { it.index == r.page }.primitives
            .filterIsInstance<DrawRect>().single { it.id == "r${r.index}-moldura" }

    /** Canto superior esquerdo da area de resposta, em micrometros da pagina. */
    private fun areaEsquerdaUm(r: ScannableRegion): Double =
        r.quadX + requireNotNull(r.answerArea).u.toDouble() * r.quadWidth / 1_000_000.0

    private fun areaTopoUm(r: ScannableRegion): Double =
        r.quadY + requireNotNull(r.answerArea).v.toDouble() * r.quadHeight / 1_000_000.0

    /** Micrometros a pixels do recorte, a 10 px/mm. */
    private fun px(um: Double): Double = um / 100.0

    // ---- os quadros: de frente e em angulo ----

    private class Quadro(val nome: String, val regiao: ScannableRegion, val pagina: Mat)

    private fun quadros(): List<Quadro> = listOf(1, 2).flatMap { indice ->
        val r = regiao(indice)
        listOf(
            Quadro("frente, regiao $indice", r, prova.pagina(r.page)),
            Quadro("angulo, regiao $indice", r, FolhaEmAngulo.de(prova.pagina(r.page))),
        )
    }

    private fun tira(q: Quadro): RectifiedRegion = recortado(SheetReader.recortar(q.pagina, prova.folha, q.regiao)).resposta

    // ---- 4.1: dimensoes e posicao da moldura ----

    @Test
    fun o_recorte_tem_as_dimensoes_da_area_do_mapa() {
        for (q in quadros()) {
            val area = requireNotNull(q.regiao.answerArea)
            val larguraEsperada = area.uSize.toDouble() * q.regiao.quadWidth / 1_000_000.0 / 100.0
            val alturaEsperada = area.vSize.toDouble() * q.regiao.quadHeight / 1_000_000.0 / 100.0
            val recorte = tira(q)

            assertTrue("${q.nome}: largura ${recorte.width} contra ${"%.1f".format(larguraEsperada)} px", abs(recorte.width - larguraEsperada) <= 1.0)
            assertTrue("${q.nome}: altura ${recorte.height} contra ${"%.1f".format(alturaEsperada)} px", abs(recorte.height - alturaEsperada) <= 1.0)
        }
    }

    @Test
    fun a_moldura_cai_a_meio_milimetro_do_mapa() {
        for (q in quadros()) {
            val m = moldura(q.regiao)
            val recorte = tira(q)
            val esq = px(m.x - areaEsquerdaUm(q.regiao))
            val dir = px(m.x + m.width - areaEsquerdaUm(q.regiao))
            val topo = px(m.y - areaTopoUm(q.regiao))
            val base = px(m.y + m.height - areaTopoUm(q.regiao))

            val medidas = listOf(
                "esquerda" to (Perfil.colunaEscura(recorte, esq) to esq),
                "direita" to (Perfil.colunaEscura(recorte, dir) to dir),
                "topo" to (Perfil.linhaEscura(recorte, topo) to topo),
                "base" to (Perfil.linhaEscura(recorte, base) to base),
            )
            Log.i(TAG, "moldura ${q.nome}: " + medidas.joinToString { (n, p) -> "$n medido=%s esperado=%.1f".format(p.first?.let { "%.1f".format(it) }, p.second) })
            for ((lado, par) in medidas) {
                val medido = par.first ?: throw AssertionError("${q.nome}: nao achei a linha ${lado} da moldura perto de %.1f px".format(par.second))
                assertTrue(
                    "${q.nome}: moldura ${lado} a %.2f mm do mapa; o criterio e 0,5 mm".format(abs(medido - par.second) / 10.0),
                    abs(medido - par.second) <= TOLERANCIA_PX,
                )
            }
        }
    }

    // ---- 4.2: a tinta no canto sem ancora ----

    /** Onde o retangulo de tinta sintetica cai, em micrometros da pagina: canto inferior esquerdo da moldura. */
    private class Tinta(val x0: Double, val y0: Double, val x1: Double, val y1: Double)

    private fun tintaNoCantoInferiorEsquerdo(r: ScannableRegion): Tinta {
        val m = moldura(r)
        val x0 = m.x + 2_000.0
        val y1 = m.y + m.height - 2_000.0
        return Tinta(x0, y1 - 3_000.0, x0 + 8_000.0, y1)
    }

    private fun desenha(pagina: Mat, t: Tinta) {
        Imgproc.rectangle(pagina, Point(t.x0 / 100.0, t.y0 / 100.0), Point(t.x1 / 100.0 - 1, t.y1 / 100.0 - 1), Scalar(0.0), -1)
    }

    @Test
    fun a_tinta_no_canto_sem_ancora_esta_inteira_no_recorte() {
        for (indice in listOf(1, 2)) {
            val r = regiao(indice)
            val tinta = tintaNoCantoInferiorEsquerdo(r)
            val m = moldura(r)
            for (emAngulo in listOf(false, true)) {
                val pagina = prova.pagina(r.page)
                desenha(pagina, tinta)
                val quadro = Quadro("regiao $indice ${if (emAngulo) "angulo" else "frente"}", r, if (emAngulo) FolhaEmAngulo.de(pagina) else pagina)
                val recorte = tira(quadro)

                // A janela interna da moldura, a 0,8 mm do traco: nao inclui a moldura nem a pauta.
                val janela = Perfil.Janela(
                    px(m.x - areaEsquerdaUm(r)) + 8, px(m.y - areaTopoUm(r)) + 8,
                    px(m.x + m.width - areaEsquerdaUm(r)) - 8, px(m.y + m.height - areaTopoUm(r)) - 8,
                )
                val caixa = Perfil.caixaEscura(recorte, janela)
                    ?: throw AssertionError("${quadro.nome}: nenhuma tinta no recorte")

                val ex0 = px(tinta.x0 - areaEsquerdaUm(r)); val ex1 = px(tinta.x1 - areaEsquerdaUm(r))
                val ey0 = px(tinta.y0 - areaTopoUm(r)); val ey1 = px(tinta.y1 - areaTopoUm(r))
                Log.i(TAG, "tinta ${quadro.nome}: caixa=(${caixa.x0},${caixa.y0})-(${caixa.x1},${caixa.y1}) esperado=(%.1f,%.1f)-(%.1f,%.1f) pixels=${caixa.pixels} esperados=${((ex1 - ex0) * (ey1 - ey0)).toInt()}".format(ex0, ey0, ex1, ey1))

                for ((nome, medido, esperado) in listOf(
                    Triple("esquerda", caixa.x0.toDouble(), ex0), Triple("direita", caixa.x1.toDouble(), ex1),
                    Triple("topo", caixa.y0.toDouble(), ey0), Triple("base", caixa.y1.toDouble(), ey1),
                )) {
                    assertTrue(
                        "${quadro.nome}: lado $nome da tinta a %.2f mm do esperado".format(abs(medido - esperado) / 10.0),
                        abs(medido - esperado) <= TOLERANCIA_PX,
                    )
                }
                val esperados = (ex1 - ex0) * (ey1 - ey0)
                assertTrue("${quadro.nome}: ${caixa.pixels} px de tinta contra ${esperados.toInt()} esperados", caixa.pixels in (esperados * 0.95).toInt()..(esperados * 1.05).toInt())
            }
        }
    }

    // ---- 4.3: o que esta fora nao entra, o recorte e determinístico, e o gabarito e recusado ----

    @Test
    fun tinta_de_fora_da_area_nao_muda_o_recorte() {
        for (indice in listOf(1, 2)) {
            val r = regiao(indice)
            // Uma mancha grande, comecando 1 mm FORA da borda esquerda da area, no meio da altura.
            val esq = areaEsquerdaUm(r)
            val topo = areaTopoUm(r)
            val alt = requireNotNull(r.answerArea).vSize.toDouble() * r.quadHeight / 1_000_000.0
            val mancha = Tinta(esq - 9_000.0, topo + alt * 0.3, esq - 1_000.0, topo + alt * 0.7)

            for (emAngulo in listOf(false, true)) {
                val limpa = prova.pagina(r.page)
                val suja = prova.pagina(r.page)
                desenha(suja, mancha)
                val nome = "regiao $indice ${if (emAngulo) "angulo" else "frente"}"
                val a = tira(Quadro(nome, r, if (emAngulo) FolhaEmAngulo.de(limpa) else limpa))
                val b = tira(Quadro(nome, r, if (emAngulo) FolhaEmAngulo.de(suja) else suja))

                assertEquals("$nome: largura", a.width, b.width)
                assertEquals("$nome: altura", a.height, b.height)
                assertTrue("$nome: a mancha a 1 mm de fora da area mudou o recorte", iguais(a, b))
                assertTrue("$nome: guarda de vacuidade — a mancha devia ter sido desenhada", escurosNaPagina(suja) > escurosNaPagina(limpa))
            }
        }
    }

    @Test
    fun a_mesma_captura_da_o_mesmo_recorte_byte_a_byte() {
        for (q in quadros()) {
            assertTrue("${q.nome}: dois recortes da mesma captura diferem", iguais(tira(q), tira(q)))
        }
    }

    @Test
    fun o_gabarito_e_recusado_com_o_motivo() {
        val gabarito = regiao(0)

        val r = SheetReader.recortar(prova.pagina(gabarito.page), prova.folha, gabarito)

        assertEquals(RecorteOutcome.Recusado("a regiao 0 nao declara area de resposta"), r)
    }

    /** Pixels escuros (< 128) de uma pagina inteira. */
    private fun escurosNaPagina(pagina: Mat): Int {
        val escuros = Mat()
        org.opencv.core.Core.compare(pagina, Scalar(128.0), escuros, org.opencv.core.Core.CMP_LT)
        return org.opencv.core.Core.countNonZero(escuros)
    }

    private fun iguais(a: RectifiedRegion, b: RectifiedRegion): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        for (y in 0 until a.height) for (x in 0 until a.width) if (a.luminanceAt(x, y) != b.luminanceAt(x, y)) return false
        return true
    }

    private companion object {
        const val TAG = "Medida5c0"
        const val TOLERANCIA_PX = 5.0 // 0,5 mm a 10 px/mm
    }
}

/** Leituras de pixel sobre o recorte. Nao compartilham codigo com o warp (P4). */
internal object Perfil {

    class Janela(val x0: Double, val y0: Double, val x1: Double, val y1: Double)

    class Caixa(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val pixels: Int)

    /**
     * Posicao X, em pixels, do centro de uma linha vertical escura perto de [esperado]: centroide da
     * escuridao na janela de +-12 px, nas linhas do meio da altura. Nulo se nao ha tinta ali.
     */
    fun colunaEscura(c: RectifiedRegion, esperado: Double, janela: Int = 12): Double? {
        var soma = 0.0; var peso = 0.0
        val xa = (esperado - janela).toInt().coerceAtLeast(0); val xb = (esperado + janela).toInt().coerceAtMost(c.width - 1)
        for (y in (c.height * 0.3).toInt()..(c.height * 0.7).toInt()) {
            for (x in xa..xb) {
                val lum = c.luminanceAt(x, y)
                if (lum < 128) { val p = (255 - lum).toDouble(); soma += p * (x + 0.5); peso += p }
            }
        }
        return if (peso == 0.0) null else soma / peso
    }

    fun linhaEscura(c: RectifiedRegion, esperado: Double, janela: Int = 12): Double? {
        var soma = 0.0; var peso = 0.0
        val ya = (esperado - janela).toInt().coerceAtLeast(0); val yb = (esperado + janela).toInt().coerceAtMost(c.height - 1)
        for (y in ya..yb) {
            for (x in (c.width * 0.3).toInt()..(c.width * 0.7).toInt()) {
                val lum = c.luminanceAt(x, y)
                if (lum < 128) { val p = (255 - lum).toDouble(); soma += p * (y + 0.5); peso += p }
            }
        }
        return if (peso == 0.0) null else soma / peso
    }

    /** Menor caixa `[x0, x1)`, `[y0, y1)` com pixels escuros dentro da [janela], e quantos pixels escuros. */
    fun caixaEscura(c: RectifiedRegion, janela: Janela): Caixa? {
        var x0 = Int.MAX_VALUE; var y0 = Int.MAX_VALUE; var x1 = -1; var y1 = -1; var n = 0
        for (y in janela.y0.toInt()..janela.y1.toInt().coerceAtMost(c.height - 1)) {
            for (x in janela.x0.toInt()..janela.x1.toInt().coerceAtMost(c.width - 1)) {
                if (c.luminanceAt(x, y) < 128) {
                    n++
                    if (x < x0) x0 = x
                    if (y < y0) y0 = y
                    if (x + 1 > x1) x1 = x + 1
                    if (y + 1 > y1) y1 = y + 1
                }
            }
        }
        return if (n == 0) null else Caixa(x0, y0, x1, y1, n)
    }
}
