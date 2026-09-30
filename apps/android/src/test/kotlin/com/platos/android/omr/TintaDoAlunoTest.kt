package com.platos.android.omr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * O contador de tinta do aluno (`slice-5c-0-o-recorte-da-resposta`, tarefas 5.1 e 5.2).
 *
 * O oraculo e analitico, como o de [SyntheticRegion]: a quantidade de tinta sai da aritmetica de quem
 * desenhou o retangulo (`largura * altura` em pixels), e nao de outra medicao. Sobre pixels
 * sinteticos, sem interpolacao, a contagem tem de ser **exata**; a tolerancia de 10% da tarefa 5.2 e
 * para o documento renderizado em perspectiva, onde a borda do traco e suavizada.
 */
class TintaDoAlunoTest {

    private val papel = SyntheticRegion.PAPER
    private val tinta = SyntheticRegion.INK

    /** Tom decorativo maximo do orcamento de hoje (`InkBudget.DEFAULT`), por mil. */
    private val tom = 500

    private val largura = 200
    private val altura = 100
    private val faixa = 30

    /** Miolo (a area): x em [30, 170), y em [30, 70). O resto e a faixa. */
    private fun canvas(desenha: (ByteArray) -> Unit = {}): RectifiedRegion {
        val pixels = ByteArray(largura * altura) { papel.toByte() }
        desenha(pixels)
        return RectifiedRegion(largura, altura, pixels)
    }

    private fun ByteArray.retangulo(x0: Int, y0: Int, x1: Int, y1: Int, valor: Int) {
        for (y in y0 until y1) for (x in x0 until x1) this[y * largura + x] = valor.toByte()
    }

    private fun conta(canvas: RectifiedRegion, mascara: List<RetanguloPx> = emptyList()): TintaDoAluno.Contagem =
        TintaDoAluno.contar(canvas, faixa, mascara, tom) ?: throw AssertionError("o papel devia ser medivel")

    // --- guarda de vacuidade (P13): o contador conta uma quantidade conhecida ---

    @Test
    fun `uma quantidade conhecida de tinta na area e na faixa e contada exatamente`() {
        val c = canvas {
            it.retangulo(40, 40, 50, 50, tinta) // 100 px dentro
            it.retangulo(5, 5, 12, 15, tinta) // 70 px na faixa
        }

        val contagem = conta(c)

        assertEquals(100L, contagem.dentro)
        assertEquals(70L, contagem.fora)
    }

    @Test
    fun `folha em branco tem contagem zero nos dois lados`() {
        val contagem = conta(canvas())

        assertEquals(0L, contagem.dentro)
        assertEquals(0L, contagem.fora)
    }

    // --- o que nao e tinta do aluno ---

    @Test
    fun `a mascara tira a tinta impressa da contagem, dentro e fora`() {
        val c = canvas {
            it.retangulo(100, 5, 120, 15, tinta) // 200 px na faixa, sob a mascara
            it.retangulo(30, 30, 33, 70, tinta) // 3 x 40 = 120 px dentro, a moldura sob a mascara
            it.retangulo(60, 40, 70, 50, tinta) // 100 px dentro, do aluno
        }
        val mascara = listOf(RetanguloPx(95, 0, 125, 20), RetanguloPx(28, 28, 36, 72))

        val contagem = conta(c, mascara)

        assertEquals(100L, contagem.dentro)
        assertEquals(0L, contagem.fora)
    }

    @Test
    fun `a mascara dilatada cobre o traco impresso deslocado pelo erro de posicao`() {
        // A moldura cai 1 px fora de onde o mapa a declara: sem dilatar, ela contaria.
        val c = canvas { it.retangulo(28, 30, 29, 70, tinta) } // 40 px, coluna x = 28
        val exata = RetanguloPx(30, 28, 33, 72)

        assertEquals(40L, conta(c, listOf(exata)).fora)
        assertEquals(0L, conta(c, listOf(exata.dilatado(2))).fora)
    }

    @Test
    fun `a pauta cinza abaixo do tom decorativo nao e tinta`() {
        // Pauta a 300 por mil: luminancia = papel * 0,7.
        val pauta = (papel * 0.7).toInt()
        val c = canvas { it.retangulo(35, 50, 165, 51, pauta) }

        assertEquals(0L, conta(c).dentro)
    }

    @Test
    fun `uma sombra suave na metade da foto nao e tinta`() {
        // Metade direita do canvas 17% mais escura: cobertura de 0,17, abaixo dos 0,5 do tom.
        val c = canvas { it.retangulo(100, 0, largura, altura, (papel * 0.83).toInt()) }

        val contagem = conta(c)

        assertEquals(0L, contagem.dentro)
        assertEquals(0L, contagem.fora)
    }

    // --- recusas ---

    @Test
    fun `canvas escuro demais para achar o branco do papel nao tem contagem`() {
        val escuro = RectifiedRegion(largura, altura, ByteArray(largura * altura) { tinta.toByte() })

        assertNull(TintaDoAluno.contar(escuro, faixa, emptyList(), tom))
    }

    @Test
    fun `canvas sem miolo depois da faixa e recusado`() {
        assertThrows(IllegalArgumentException::class.java) {
            TintaDoAluno.contar(canvas(), faixaPx = 50, mascara = emptyList(), tomMaximoPorMil = tom)
        }
    }

    // --- a unidade do dominio ---

    @Test
    fun `a 10 pixels por milimetro um pixel e um centesimo de mm2`() {
        assertEquals(400L, TintaDoAluno.centesimosDeMm2(400, 10))
        assertEquals(100L, TintaDoAluno.centesimosDeMm2(400, 20))
    }
}
