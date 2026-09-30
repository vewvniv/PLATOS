package com.platos.android.vision

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.android.omr.RectifiedRegion
import com.platos.domain.capture.DesvioDaResposta
import com.platos.domain.layout.ScannableRegion
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * O desvio da resposta (`slice-5c-0-o-recorte-da-resposta`, tarefas 5.1 e 5.2).
 *
 * Os quadros sao o documento da folha de `tok-a`, de frente e em [FolhaEmAngulo]. **Nada aqui e papel,
 * e a tinta do aluno e sintetica**: retangulos pretos de area conhecida por construcao. As unidades sao
 * as do dominio: centesimos de mm2 (1 pixel a 10 px/mm; 1 mm2 = 100).
 *
 * **A tinta de "fora" fica a mais de 1 mm da moldura**, alem da mascara (que dilata a tinta impressa
 * pelo teto do residuo, 1,0 mm): tinta encostada na moldura e descontada de proposito (design,
 * Riscos: "a mascara pode esconder tinta do aluno").
 *
 * A tolerancia de 10% da contagem (tarefa 5.2) foi fixada antes da primeira execucao.
 */
@RunWith(AndroidJUnit4::class)
class DesvioDaRespostaInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val prova = FolhaDiscursivaRenderizada(instrumentation.context, instrumentation.targetContext)

    @Before
    fun carregaOpenCv() {
        assertTrue("o OpenCV nativo nao carregou", OpenCVLoader.initLocal())
    }

    private fun regiao(indice: Int): ScannableRegion = prova.folha.regions.single { it.index == indice }

    private fun areaEsquerdaUm(r: ScannableRegion): Double =
        r.quadX + requireNotNull(r.answerArea).u.toDouble() * r.quadWidth / 1_000_000.0

    private fun areaTopoUm(r: ScannableRegion): Double =
        r.quadY + requireNotNull(r.answerArea).v.toDouble() * r.quadHeight / 1_000_000.0

    private fun areaAlturaUm(r: ScannableRegion): Double =
        requireNotNull(r.answerArea).vSize.toDouble() * r.quadHeight / 1_000_000.0

    /** Um retangulo de tinta, em micrometros da pagina. */
    private class Tinta(val x0: Double, val y0: Double, val x1: Double, val y1: Double) {
        /** Area em centesimos de mm2: um pixel a 10 px/mm. */
        val centesimos: Long get() = ((x1 - x0) / 100.0 * ((y1 - y0) / 100.0)).toLong()
    }

    private fun desenha(pagina: Mat, t: Tinta) {
        Imgproc.rectangle(pagina, Point(t.x0 / 100.0, t.y0 / 100.0), Point(t.x1 / 100.0 - 1, t.y1 / 100.0 - 1), Scalar(0.0), -1)
    }

    /** 10 x 10 mm no meio da area, dentro da moldura: 100 mm2 = 10000. */
    private fun dentro(r: ScannableRegion, ladoMm: Int = 10): Tinta {
        val cx = areaEsquerdaUm(r) + r.quadWidth.toDouble() * requireNotNull(r.answerArea).uSize / 1_000_000.0 / 2
        val cy = areaTopoUm(r) + areaAlturaUm(r) / 2
        val meio = ladoMm * 500.0
        return Tinta(cx - meio, cy - meio, cx + meio, cy + meio)
    }

    /** 1,4 x 20 mm na faixa da esquerda, entre 1,5 e 2,9 mm fora da area: 28 mm2 = 2800. */
    private fun fora(r: ScannableRegion, alturaMm: Int = 20): Tinta {
        val esq = areaEsquerdaUm(r)
        val cy = areaTopoUm(r) + areaAlturaUm(r) / 2
        val meio = alturaMm * 500.0
        return Tinta(esq - 2_900.0, cy - meio, esq - 1_500.0, cy + meio)
    }

    private class Caso(val nome: String, val regiao: ScannableRegion, val pagina: Mat)

    private fun casos(desenhos: (ScannableRegion) -> List<Tinta>): List<Caso> = listOf(1, 2).flatMap { indice ->
        val r = regiao(indice)
        listOf(false, true).map { emAngulo ->
            val pagina = prova.pagina(r.page)
            desenhos(r).forEach { desenha(pagina, it) }
            Caso("regiao $indice ${if (emAngulo) "angulo" else "frente"}", r, if (emAngulo) FolhaEmAngulo.de(pagina) else pagina)
        }
    }

    private fun recorta(c: Caso): RecorteOutcome.Recortado =
        SheetReader.recortar(c.pagina, prova.folha, c.regiao).let {
            it as? RecorteOutcome.Recortado ?: throw AssertionError("${c.nome}: esperava o recorte, veio $it")
        }

    private fun registra(c: Caso, d: DesvioDaResposta) {
        Log.i(TAG, "desvio ${c.nome}: dentro=${d.dentro} fora=${d.fora} proporcao=${d.proporcaoForaPpm / 10_000.0}% sinalizado=${d.sinalizado}")
    }

    private fun assertDentroDe10Por100(rotulo: String, medido: Long, esperado: Long) {
        assertTrue("$rotulo: contou $medido contra $esperado esperados (tolerancia 10%)", abs(medido - esperado) <= esperado / 10)
    }

    // ---- 5.1: um teste por cenario da spec ----

    /** Cenario "Resposta dentro da area". */
    @Test
    fun resposta_dentro_da_area_nao_e_sinalizada() {
        for (c in casos { listOf(dentro(it)) }) {
            val d = recorta(c).desvio
            registra(c, d)
            assertEquals("${c.nome}: nada devia ter sido contado na faixa", 0L, d.fora)
            assertFalse("${c.nome}: resposta dentro da area sinalizada", d.sinalizado)
            assertDentroDe10Por100("${c.nome}: dentro", d.dentro, dentro(c.regiao).centesimos)
        }
    }

    /** Cenario "Resposta que extrapola a area", e a contagem conhecida da tarefa 5.2. */
    @Test
    fun resposta_que_extrapola_e_sinalizada_com_a_proporcao_medida() {
        for (c in casos { listOf(dentro(it), fora(it)) }) {
            val d = recorta(c).desvio
            registra(c, d)
            assertDentroDe10Por100("${c.nome}: dentro", d.dentro, dentro(c.regiao).centesimos)
            assertDentroDe10Por100("${c.nome}: fora", d.fora, fora(c.regiao).centesimos)
            assertTrue("${c.nome}: devia ser sinalizada", d.sinalizado)
            assertTrue("${c.nome}: proporcao ${d.proporcaoForaPpm} ppm", d.proporcaoForaPpm > DesvioDaResposta.PROPORCAO_MINIMA_PPM)
        }
    }

    /** Cenarios "A tinta impressa nao conta" e "Resposta em branco": a mesma folha, sem escrita. */
    @Test
    fun folha_em_branco_tem_proporcao_zero_e_nao_e_sinalizada() {
        for (c in casos { emptyList() }) {
            val d = recorta(c).desvio
            registra(c, d)
            assertEquals("${c.nome}: tinta impressa contada como do aluno, dentro", 0L, d.dentro)
            assertEquals("${c.nome}: tinta impressa contada como do aluno, fora", 0L, d.fora)
            assertEquals(0, d.proporcaoForaPpm)
            assertFalse(d.sinalizado)
        }
    }

    /** Cenario "Mancha isolada abaixo do piso": a proporcao passa de 5%, so o piso de 4 mm2 segura. */
    @Test
    fun mancha_abaixo_do_piso_nao_e_sinalizada_ainda_que_a_proporcao_passe() {
        for (c in casos { listOf(dentro(it, ladoMm = 4), fora(it, alturaMm = 2)) }) {
            val d = recorta(c).desvio
            registra(c, d)
            assertTrue("${c.nome}: a fixture devia passar dos 5% (${d.proporcaoForaPpm} ppm)", d.proporcaoForaPpm >= DesvioDaResposta.PROPORCAO_MINIMA_PPM)
            assertTrue("${c.nome}: a fixture devia ter tinta fora abaixo do piso (${d.fora})", d.fora in 1 until DesvioDaResposta.PISO_FORA)
            assertFalse("${c.nome}: sinalizada abaixo do piso", d.sinalizado)
        }
    }

    /** Cenario "A faixa nao vaza para o recorte": o desvio sinalizado nao leva pixel da faixa. */
    @Test
    fun a_faixa_nao_vaza_para_o_recorte() {
        for (indice in listOf(1, 2)) {
            val r = regiao(indice)
            for (emAngulo in listOf(false, true)) {
                val so = prova.pagina(r.page).also { desenha(it, dentro(r)) }
                val com = prova.pagina(r.page).also { desenha(it, dentro(r)); desenha(it, fora(r)) }
                val nome = "regiao $indice ${if (emAngulo) "angulo" else "frente"}"
                val a = recorta(Caso(nome, r, if (emAngulo) FolhaEmAngulo.de(so) else so))
                val b = recorta(Caso(nome, r, if (emAngulo) FolhaEmAngulo.de(com) else com))

                assertFalse("$nome: sem a tinta de fora ja estava sinalizada", a.desvio.sinalizado)
                assertTrue("$nome: com a tinta de fora devia estar sinalizada", b.desvio.sinalizado)
                assertTrue("$nome: a tinta da faixa entrou no recorte", iguais(a.resposta, b.resposta))
            }
        }
    }

    /** O recorte nao muda por causa do desvio: o sinal e de conferencia, nao recusa. */
    @Test
    fun o_desvio_e_um_sinal_e_o_recorte_e_entregue() {
        val c = casos { listOf(dentro(it), fora(it)) }.first()
        val r = SheetReader.recortar(c.pagina, prova.folha, c.regiao)

        assertTrue("um recorte com desvio devia ser entregue, veio $r", r is RecorteOutcome.Recortado)
    }

    private fun iguais(a: RectifiedRegion, b: RectifiedRegion): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        for (y in 0 until a.height) for (x in 0 until a.width) if (a.luminanceAt(x, y) != b.luminanceAt(x, y)) return false
        return true
    }

    private companion object {
        const val TAG = "Medida5c0"
    }
}
