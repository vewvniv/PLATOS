package com.platos.domain.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UmTest {

    @Test
    fun `milimetros viram micrometros exatos`() {
        assertEquals(3_000, Um.mm(3).raw)
        assertEquals(4_200, Um.mmTenths(42).raw)
        assertEquals(220, Um.mmHundredths(22).raw)
    }

    @Test
    fun `divisao para baixo trunca em direcao a menos infinito`() {
        assertEquals(Um(3), Um(7).divFloor(2))
        assertEquals(Um(-4), Um(-7).divFloor(2))
    }

    @Test
    fun `divisao para cima trunca em direcao a mais infinito`() {
        assertEquals(Um(4), Um(7).divCeil(2))
        assertEquals(Um(-3), Um(-7).divCeil(2))
    }

    @Test
    fun `arredondamento para a grade sobe ao proximo multiplo`() {
        val grade = Um.mm(3)
        assertEquals(Um(3_000), Um(2_999).ceilToMultipleOf(grade))
        assertEquals(Um(3_000), Um(3_000).ceilToMultipleOf(grade))
        assertEquals(Um(6_000), Um(3_001).ceilToMultipleOf(grade))
    }

    @Test
    fun `multiplo exato e reconhecido`() {
        val grade = Um.mm(3)
        assertTrue(Um(9_000).isMultipleOf(grade))
        assertFalse(Um(9_001).isMultipleOf(grade))
    }

    @Test
    fun `passo nao positivo e recusado`() {
        assertFailsWith<IllegalArgumentException> { Um(10).ceilToMultipleOf(Um.ZERO) }
        assertFailsWith<IllegalArgumentException> { Um(10).isMultipleOf(Um(-1)) }
    }

    @Test
    fun `coordenada normalizada cobre o intervalo unitario`() {
        val lado = Um.mm(100)
        assertEquals(Ppm.ZERO, Ppm.of(Um.ZERO, lado))
        assertEquals(Ppm.ONE, Ppm.of(lado, lado))
        assertEquals(Ppm(500_000), Ppm.of(Um.mm(50), lado))
    }

    @Test
    fun `coordenada normalizada arredonda ao ppm mais proximo`() {
        // 1/3 do lado: 333333,33... ppm arredonda para 333333.
        assertEquals(Ppm(333_333), Ppm.of(Um(1_000), Um(3_000)))
        // Desempate exato sobe.
        assertEquals(Ppm(500_000), Ppm.of(Um(1), Um(2)))
    }

    @Test
    fun `coordenada fora do intervalo unitario e detectavel`() {
        assertTrue(Ppm(0).isInUnitRange)
        assertTrue(Ppm(1_000_000).isInUnitRange)
        assertFalse(Ppm(-1).isInUnitRange)
        assertFalse(Ppm(1_000_001).isInUnitRange)
    }

    @Test
    fun `projecao com extensao nao positiva e recusada`() {
        assertFailsWith<IllegalArgumentException> { Ppm.of(Um(1), Um.ZERO) }
    }
}
