package com.platos.domain.text

import com.platos.domain.geometry.Um
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cobre a contagem de code points na medicao, em `commonTest`, ou seja: nos tres alvos.
 *
 * Existe por causa de um defeito de sombreamento. `TextMeasurer` definia uma extensao privada
 * `String.codePoints()`, sombreada em JVM e Android por `java.lang.String.codePoints(): IntStream`
 * — o Kotlin aceita `IntStream` no `for` porque `iterator()` serve de operador, entao compilava e
 * passava com um aviso. Só o alvo JS usava a implementacao escrita aqui.
 *
 * As assercoes abaixo nao dependem de qual implementacao roda; elas afirmam a *consequencia*
 * observavel de contar certo. Um par de surrogates bem formado e um unico code point, entao mede
 * como um unico glifo ausente. Se algum alvo o contasse como dois, mediria o dobro, e o teste
 * falharia naquele alvo e so nele.
 */
class CodePointMeasurementTest {

    private val measurer = TextMeasurer(EmbeddedFont.program)
    private val style = TextStyle.BODY

    /** U+1F600, fora do BMP: a fonte nao cobre, entao cai em `.notdef`. */
    private val parBemFormado = "😀"
    private val surrogateAltoSolto = "\uD83D"
    private val surrogateBaixoSolto = "\uDE00"

    @Test
    fun `par de surrogates conta como um unico code point`() {
        // Um par bem formado e UM code point; um surrogate solto tambem e UM. Ambos caem em
        // `.notdef`, entao precisam medir igual. Se um alvo partisse o par em dois, mediria o
        // dobro.
        assertEquals(
            measurer.width(surrogateAltoSolto, style),
            measurer.width(parBemFormado, style),
            "o par de surrogates nao esta sendo contado como um unico code point",
        )
    }

    // O `.notdef` avanca 640 unidades de fonte; a 3351 um de corpo, um code point ausente mede
    // 2145 um e dois medem 4289 — e nao 4290. A conversao arredonda uma vez sobre o total, entao
    // somar larguras medidas em separado nao e o mesmo que medir o texto inteiro.
    private val umCodePointAusente = Um(2_145)
    private val doisCodePointsAusentes = Um(4_289)

    @Test
    fun `dois code points medem mais que um, arredondando uma vez so`() {
        assertEquals(umCodePointAusente, measurer.width(surrogateAltoSolto, style))
        assertEquals(
            doisCodePointsAusentes,
            measurer.width(surrogateAltoSolto + surrogateAltoSolto, style),
        )
    }

    @Test
    fun `surrogates soltos nao quebram a medicao`() {
        assertTrue(measurer.width(surrogateAltoSolto, style) > Um.ZERO)
        assertTrue(measurer.width(surrogateBaixoSolto, style) > Um.ZERO)
        // Ordem invertida nao forma par: sao dois code points, nao um.
        assertEquals(
            doisCodePointsAusentes,
            measurer.width(surrogateBaixoSolto + surrogateAltoSolto, style),
        )
    }

    @Test
    fun `texto com par de surrogates mede o mesmo em todo alvo`() {
        assertEquals(Um(5_449), measurer.width("AW", style))
        assertEquals(Um(7_593), measurer.width("AW$parBemFormado", style))
    }

    @Test
    fun `code point fora do BMP cai em notdef`() {
        // O `cmap` formato 4 so endereca o BMP, entao qualquer coisa acima de U+FFFF e `.notdef`.
        assertEquals(0, EmbeddedFont.program.glyphOf(0x1F600))
    }
}
