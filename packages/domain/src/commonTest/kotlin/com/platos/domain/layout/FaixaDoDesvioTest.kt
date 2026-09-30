package com.platos.domain.layout

import com.platos.domain.geometry.Um
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A faixa em que a captura mede o desvio da escrita (`slice-5c-0-o-recorte-da-resposta`, tarefa
 * 1.1; design, decisao 4).
 */
class FaixaDoDesvioTest {

    /**
     * A faixa nao alcanca a tinta da coluna vizinha. Duas colunas ficam a `gutter` uma da outra, e a
     * faixa de uma regiao que passasse do `gutter` mediria a escrita de outra questao como se fosse
     * desta.
     */
    @Test
    fun `a faixa do desvio e menor que o gutter do perfil vigente`() {
        val faixa = EssayGeometry.DEVIANT_BAND
        val gutter = LayoutProfile.DEFAULT.gutter

        assertTrue(
            faixa < gutter,
            "a faixa do desvio (${faixa.raw} um) alcanca a coluna vizinha: o gutter e ${gutter.raw} um",
        )
    }

    /** O valor pinado: mudar a faixa e mudar o criterio do desvio, e isso exige ADR (ADR-0007, P11). */
    @Test
    fun `a faixa do desvio e de tres milimetros`() {
        assertEquals(Um.mm(3), EssayGeometry.DEVIANT_BAND)
    }
}
