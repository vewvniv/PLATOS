package com.platos.domain.capture

import com.platos.domain.geometry.Um
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cobre "Geometria dimensionada com folga" (ADR-0001).
 *
 * Tres impressoras reescalaram a mesma folha entre -3,4% e +4,7%. Aplicar o minimo de §7 ao valor
 * nominal nao basta: ele precisa valer no pior caso da faixa de ±5%. A 12 mm nominais — o proprio
 * minimo — a impressora que mais encolheu teria produzido 11,6 mm e quebrado a deteccao.
 */
class CaptureGeometryTest {

    /** Reduz [value] em 5% por aritmetica inteira; D-1.2 nao admite fracionario. */
    private fun shrunk(value: Um) = Um(value.raw * 95 / 100)

    @Test
    fun `marcador continua acima do minimo de 12 mm apos encolher 5 por cento`() {
        val minimum = Um.mm(12)
        assertTrue(
            CaptureGeometry.MARKER_SIDE >= minimum,
            "lado nominal ${CaptureGeometry.MARKER_SIDE} ja esta abaixo do minimo",
        )
        assertTrue(
            shrunk(CaptureGeometry.MARKER_SIDE) >= minimum,
            "reduzido 5%, o marcador cai para ${shrunk(CaptureGeometry.MARKER_SIDE)}, " +
                "abaixo do minimo de $minimum",
        )
    }

    @Test
    fun `o lado escolhido fecha em modulos inteiros`() {
        // Sete modulos: cinco de dados mais a borda. 12 mm dariam 1714,28... um por modulo.
        assertEquals(ArucoDictionary.TOTAL_MODULES, 7)
        assertEquals(
            CaptureGeometry.MARKER_SIDE,
            CaptureGeometry.MARKER_MODULE * ArucoDictionary.TOTAL_MODULES,
        )
        assertTrue(CaptureGeometry.MARKER_SIDE.isMultipleOf(CaptureGeometry.MARKER_MODULE))
    }

    @Test
    fun `zona de silencio vale ao menos um modulo tambem encolhida`() {
        assertTrue(CaptureGeometry.QUIET_ZONE >= CaptureGeometry.MARKER_MODULE)
        assertTrue(shrunk(CaptureGeometry.QUIET_ZONE) > Um.ZERO)
    }

    @Test
    fun `geometria das bolhas segue §7`() {
        assertEquals(Um.mmTenths(42), CaptureGeometry.BUBBLE_DIAMETER)
        assertEquals(Um.mmTenths(52), CaptureGeometry.BUBBLE_PITCH_H)
        assertEquals(Um.mm(6), CaptureGeometry.BUBBLE_PITCH_V)
        assertEquals(Um.mmHundredths(22), CaptureGeometry.BUBBLE_STROKE)
        // As bolhas nao podem se tocar: o passo precisa superar o diametro.
        assertTrue(CaptureGeometry.BUBBLE_PITCH_H > CaptureGeometry.BUBBLE_DIAMETER)
    }
}
