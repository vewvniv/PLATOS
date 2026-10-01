package com.platos.domain.scoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `Pontos`: o decimal exato da nota do professor (`slice-5c-2-a-nota-do-professor`, ADR-0021).
 *
 * Cada recusa tem a **sua** mensagem, e cada teste a le: "recusou" nao diz qual guarda segurou
 * (`rigorous.md` §3).
 */
class PontosTest {

    private fun recusa(texto: String): String =
        assertFailsWith<IllegalArgumentException> { Pontos.parse(texto) }.message.orEmpty()

    @Test
    fun `1_5 e 1_75 sao validos e valem 150 e 175 centesimos`() {
        assertEquals(150L, Pontos.parse("1.5").centesimos)
        assertEquals(175L, Pontos.parse("1.75").centesimos)
    }

    @Test
    fun `1_5 e 1_50 sao o mesmo valor`() {
        assertEquals(Pontos.parse("1.5"), Pontos.parse("1.50"))
    }

    @Test
    fun `zero, inteiro e a borda superior do banco sao validos`() {
        assertEquals(0L, Pontos.parse("0").centesimos)
        assertEquals(0L, Pontos.parse("0.00").centesimos)
        assertEquals(300L, Pontos.parse("3").centesimos)
        assertEquals(99_999_999L, Pontos.parse("999999.99").centesimos)
        assertEquals(50L, Pontos.parse("00.5").centesimos)
    }

    @Test
    fun `a forma canonica tem sempre duas casas`() {
        assertEquals("1.50", Pontos.parse("1.5").toString())
        assertEquals("0.05", Pontos.parse("0.05").toString())
        assertEquals("3.00", Pontos.parse("3").toString())
        assertEquals("10.01", Pontos.parse("10.01").toString())
    }

    @Test
    fun `0_1 mais 0_2 e exatamente 0_3`() {
        assertEquals(Pontos.parse("0.3"), Pontos.parse("0.1") + Pontos.parse("0.2"))
    }

    @Test
    fun `tres casas sao recusadas, e nao arredondadas`() {
        val motivo = recusa("1.333")
        assertTrue("mais de 2 casas" in motivo, motivo)
        assertTrue("mais de 2 casas" in recusa("1.500"), "zero a direita nao desfaz as 3 casas")
    }

    @Test
    fun `negativa tem a sua mensagem`() {
        assertTrue("negativa" in recusa("-1"), recusa("-1"))
        assertTrue("negativa" in recusa("-0.5"), recusa("-0.5"))
    }

    @Test
    fun `o que nao e numero tem a sua mensagem`() {
        for (texto in listOf("abc", "", " 1", "1 ", "+1", "1e2", ".5", "1.", "-", "1.2.3")) {
            val motivo = recusa(texto)
            assertTrue("nao e uma pontuacao" in motivo, "'$texto' -> $motivo")
        }
    }

    @Test
    fun `a virgula decimal e recusada e a mensagem pede o ponto`() {
        val motivo = recusa("1,5")
        assertTrue("nao e uma pontuacao" in motivo, motivo)
        assertTrue("ponto decimal" in motivo, motivo)
    }

    @Test
    fun `alem do alcance do banco e recusado no dominio, antes do SQL`() {
        for (texto in listOf("1000000", "1000000.00", "12345678.5")) {
            val motivo = recusa(texto)
            assertTrue("999999.99" in motivo, "'$texto' -> $motivo")
        }
    }

    @Test
    fun `inteiros aceita so o intervalo do banco`() {
        assertEquals(300L, Pontos.inteiros(3).centesimos)
        assertFailsWith<IllegalArgumentException> { Pontos.inteiros(-1) }
        assertFailsWith<IllegalArgumentException> { Pontos.inteiros(1_000_000) }
    }

    @Test
    fun `valores que o ponto flutuante binario erra continuam exatos`() {
        // 0.29 * 100 = 28.999999999999996, 0.57 * 100 = 56.99999999999999, 1.15 * 100 = 114.99999999999999.
        // E o que faz a mutacao "ler via Double" da Task 8 ser vista falhar.
        assertEquals(29L, Pontos.parse("0.29").centesimos)
        assertEquals(57L, Pontos.parse("0.57").centesimos)
        assertEquals(115L, Pontos.parse("1.15").centesimos)
    }

    @Test
    fun `o maximo do dominio e exatamente o que o parse aceita`() {
        assertEquals(Pontos.MAXIMO_CENTESIMOS, Pontos.parse("999999.99").centesimos)
        assertTrue("999999.99" in recusa("1000000.00"))
        assertEquals(Pontos.MAXIMO_CENTESIMOS, Pontos.inteiros(999_999).centesimos + 99)
    }

    @Test
    fun `a ordem e a dos centesimos`() {
        assertTrue(Pontos.parse("1.75") > Pontos.parse("1.5"))
        assertTrue(Pontos.ZERO < Pontos.parse("0.01"))
    }

    @Test
    fun `lerPontos devolve o valor exato`() {
        assertEquals("1.75", lerPontos("1.75", "a discursiva 'd1'").toString())
    }

    @Test
    fun `lerPontos diz de qual campo veio o erro`() {
        val erro = assertFailsWith<IllegalArgumentException> { lerPontos("1,5", "a discursiva 'd1'") }
        assertTrue(erro.message.orEmpty().startsWith("a discursiva 'd1': "), erro.message)
        assertTrue(erro.message.orEmpty().contains("ponto decimal"), erro.message)
    }

    @Test
    fun `lerPontos recusa tres casas e negativo`() {
        assertFailsWith<IllegalArgumentException> { lerPontos("1.333", "x") }
        assertFailsWith<IllegalArgumentException> { lerPontos("-1", "x") }
    }
}
