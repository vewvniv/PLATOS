package com.platos.domain.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A classificacao do desvio (`slice-5c-0-o-recorte-da-resposta`, tarefa 1.3): proporcao de tinta
 * fora da area >= 5% **e** tinta de fora >= 4 mm2.
 *
 * As quantidades estao em centesimos de mm2 (1 pixel a 10 px/mm): 4 mm2 sao 400.
 */
class DesvioDaRespostaTest {

    // --- a proporcao ---

    @Test
    fun `exatamente 5 por cento com o piso satisfeito e sinalizado`() {
        // 400 de 8000 = 5%, e 400 e o piso: as duas fronteiras juntas.
        val desvio = DesvioDaResposta.classificar(dentro = 7_600, fora = 400)

        assertEquals(50_000, desvio.proporcaoForaPpm)
        assertTrue(desvio.sinalizado)
    }

    @Test
    fun `logo abaixo de 5 por cento nao e sinalizado, ainda que o piso seja superado`() {
        val desvio = DesvioDaResposta.classificar(dentro = 7_601, fora = 400) // 4,99...%

        assertTrue(desvio.fora >= DesvioDaResposta.PISO_FORA, "a fixture devia superar o piso")
        assertFalse(desvio.sinalizado)
    }

    // --- o piso ---

    @Test
    fun `exatamente o piso de 4 mm2 com proporcao alta e sinalizado`() {
        val desvio = DesvioDaResposta.classificar(dentro = 1_000, fora = 400)

        assertTrue(desvio.sinalizado)
    }

    @Test
    fun `logo abaixo do piso nao e sinalizado, ainda que a proporcao passe de 5 por cento`() {
        // Uma resposta curta com um respingo: 50% da tinta esta fora, mas sao 3,99 mm2.
        val desvio = DesvioDaResposta.classificar(dentro = 399, fora = 399)

        assertTrue(desvio.proporcaoForaPpm >= DesvioDaResposta.PROPORCAO_MINIMA_PPM, "devia passar dos 5%")
        assertFalse(desvio.sinalizado)
    }

    @Test
    fun `mancha isolada numa folha sem escrita nao e sinalizada`() {
        // Sem tinta dentro, a proporcao e 100%; so o piso segura.
        val desvio = DesvioDaResposta.classificar(dentro = 0, fora = 100)

        assertEquals(1_000_000, desvio.proporcaoForaPpm)
        assertFalse(desvio.sinalizado)
    }

    // --- o zero e a entrada que nao e folha ---

    @Test
    fun `resposta em branco tem proporcao zero e nao e sinalizada`() {
        val desvio = DesvioDaResposta.classificar(dentro = 0, fora = 0)

        assertEquals(0, desvio.proporcaoForaPpm)
        assertFalse(desvio.sinalizado)
    }

    @Test
    fun `tudo dentro da area nao e sinalizado`() {
        val desvio = DesvioDaResposta.classificar(dentro = 50_000, fora = 0)

        assertEquals(0, desvio.proporcaoForaPpm)
        assertFalse(desvio.sinalizado)
    }

    @Test
    fun `entrada negativa ou acima do teto e recusada, e nao lida como sem desvio`() {
        val invalidos = listOf(-1L, Long.MIN_VALUE, DesvioDaResposta.TETO_DA_ENTRADA + 1, Long.MAX_VALUE)

        for (invalido in invalidos) {
            assertFailsWith<IllegalArgumentException>("dentro = $invalido") {
                DesvioDaResposta.classificar(dentro = invalido, fora = 1_000)
            }
            assertFailsWith<IllegalArgumentException>("fora = $invalido") {
                DesvioDaResposta.classificar(dentro = 1_000, fora = invalido)
            }
        }
    }

    @Test
    fun `no teto da entrada a conta nao estoura`() {
        val desvio = DesvioDaResposta.classificar(
            dentro = DesvioDaResposta.TETO_DA_ENTRADA,
            fora = DesvioDaResposta.TETO_DA_ENTRADA,
        )

        assertEquals(500_000, desvio.proporcaoForaPpm)
        assertTrue(desvio.sinalizado)
    }

    @Test
    fun `os dois numeros sao os do criterio fixado antes da primeira execucao`() {
        assertEquals(50_000, DesvioDaResposta.PROPORCAO_MINIMA_PPM)
        assertEquals(400L, DesvioDaResposta.PISO_FORA)
    }
}
