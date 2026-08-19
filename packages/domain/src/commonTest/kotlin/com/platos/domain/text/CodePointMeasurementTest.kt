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

    // Construidos em tempo de execucao, e **nao** como literal de string. O gerador de codigo do
    // Kotlin/JS nao emite surrogate solto num literal: ele o substitui por `?`, que a fonte cobre
    // com avanco 416 em vez dos 640 do `.notdef`. O teste falhava por medir outra coisa, e nao
    // por medir errado. Construir a partir do code point tira o insumo do caminho do gerador.
    /** U+1F600, fora do BMP: a fonte nao cobre, entao cai em `.notdef`. */
    private val parBemFormado = Char(0xD83D).toString() + Char(0xDE00).toString()
    private val surrogateAltoSolto = Char(0xD83D).toString()
    private val surrogateBaixoSolto = Char(0xDE00).toString()

    /**
     * Afirma o **insumo**, antes de medir qualquer coisa.
     *
     * Sem isto, uma corrupcao do insumo pelo gerador de codigo chega disfarcada de divergencia de
     * medicao — foi exatamente o que aconteceu, e o diagnostico registrado acusou o `cmap` por
     * quase uma fatia inteira. Aqui a mesma corrupcao falha dizendo o proprio nome.
     */
    @Test
    fun `o insumo chega intacto ao alvo`() {
        assertEquals(1, surrogateAltoSolto.length, "surrogate alto solto deixou de ser um code unit")
        assertEquals(0xD83D, surrogateAltoSolto[0].code, "surrogate alto solto foi substituido")
        assertEquals(1, surrogateBaixoSolto.length, "surrogate baixo solto deixou de ser um code unit")
        assertEquals(0xDE00, surrogateBaixoSolto[0].code, "surrogate baixo solto foi substituido")
        assertEquals(2, parBemFormado.length, "o par bem formado deixou de ser dois code units")
        assertEquals(0xD83D, parBemFormado[0].code)
        assertEquals(0xDE00, parBemFormado[1].code)
    }

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
