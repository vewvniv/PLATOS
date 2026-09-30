package com.platos.android.omr

/** Um retangulo de pixels do canvas: `[left, right) x [top, bottom)`. */
class RetanguloPx(val left: Int, val top: Int, val right: Int, val bottom: Int) {

    fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom

    /** O mesmo retangulo, [px] maior para cada lado. */
    fun dilatado(px: Int): RetanguloPx = RetanguloPx(left - px, top - px, right + px, bottom + px)
}

/**
 * Conta a tinta do aluno numa imagem retificada, dentro e fora da area de resposta
 * (`slice-5c-0-o-recorte-da-resposta`, design, decisao 5).
 *
 * **Tinta e o que a regiao ja declara como nao-decoracao.** A cobertura de um pixel e a do
 * [BubbleMeter] — `1 - luminancia / branco local`, com o branco do [PaperWhite], o unico lugar do
 * OMR que decide o que e branco —, e ele e tinta quando a cobertura passa do tom decorativo maximo
 * que o mapa declara (`InkBudget.decorativeToneMax`, 500 por mil hoje). A pauta a 300 por mil nunca
 * conta, e o limiar tem dono: o orcamento de tinta da regiao (ADR-0010), e nao um numero novo.
 *
 * **A tinta impressa entra pela [mascara], e nao por estimativa.** Marcadores, QR e os quatro lados da
 * moldura estao no mapa; quem chama os desenha aqui, ja dilatados pelo erro de posicao que o ajuste
 * admite. Pixel dentro da mascara nao conta nem na area nem na faixa.
 *
 * O canvas cobre a area **mais** a faixa de [faixaPx] em volta; o miolo e a area, o anel e a faixa.
 * A contagem e em pixels; a conversao para a unidade do dominio esta em [centesimosDeMm2].
 */
object TintaDoAluno {

    /** Tinta do aluno, em pixels, dentro da area e na faixa de fora dela. */
    class Contagem(val dentro: Long, val fora: Long)

    /**
     * Nulo quando o papel esta escuro demais para achar o branco (o mesmo criterio do medidor de
     * bolha): sem branco, nenhuma contagem significa coisa alguma.
     */
    fun contar(
        canvas: RectifiedRegion,
        faixaPx: Int,
        mascara: List<RetanguloPx>,
        tomMaximoPorMil: Int,
    ): Contagem? {
        require(faixaPx >= 0) { "faixa negativa: $faixaPx" }
        require(canvas.width > 2 * faixaPx && canvas.height > 2 * faixaPx) {
            "o canvas ${canvas.width}x${canvas.height} nao tem miolo depois de uma faixa de $faixaPx px"
        }
        require(tomMaximoPorMil in 0..1000) { "tom fora da faixa: $tomMaximoPorMil" }

        val branco = PaperWhite.of(canvas) ?: return null
        val limiar = tomMaximoPorMil / 1000.0

        var dentro = 0L
        var fora = 0L
        for (y in 0 until canvas.height) {
            for (x in 0 until canvas.width) {
                if (mascara.any { it.contains(x, y) }) continue
                val cobertura = 1.0 - canvas.luminanceAt(x, y) / branco.at(x.toDouble(), y.toDouble())
                if (cobertura < limiar) continue
                val naArea = x in faixaPx until canvas.width - faixaPx && y in faixaPx until canvas.height - faixaPx
                if (naArea) dentro += 1 else fora += 1
            }
        }
        return Contagem(dentro, fora)
    }

    /**
     * Pixels para centesimos de mm2, a unidade de `DesvioDaResposta`. A 10 px/mm um pixel e
     * 0,01 mm2 e a conversao e a identidade; a divisao so aparece se a resolucao mudar.
     */
    fun centesimosDeMm2(pixels: Long, pxPorMm: Int): Long {
        require(pxPorMm > 0) { "resolucao invalida: $pxPorMm px/mm" }
        return pixels * 100L / (pxPorMm.toLong() * pxPorMm)
    }
}
