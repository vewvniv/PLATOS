package com.platos.domain.capture

import com.platos.domain.layout.InkBudget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * O veredito de bolha, nas bordas.
 *
 * O limiar destes testes e **de teste**: 300 com margem 50, escolhido por estar no meio do
 * corredor de ADR-0010 e nao por medicao nenhuma. O numero que o aplicativo usa sai do corpus
 * fotografado, pela regra de ADR-0011, e so entra no codigo na tarefa 8.1 — antes disso qualquer
 * constante aqui seria o limiar escolhido sem dados que ADR-0007 proibe.
 */
class BubbleVerdictTest {

    private val t = 300
    private val m = 50
    private val limiar = OmrThreshold(value = t, margin = m)

    @Test
    fun `as sete bordas caem do lado previsto`() {
        // A tabela inteira num teste so, porque o que importa e a **transicao** entre elas: um
        // `>` trocado por `>=` move exatamente uma destas linhas, e ver as sete juntas mostra qual.
        assertEquals(BubbleVerdict.VAZIA, limiar.verdictFor(t - m - 1), "T-M-1")
        assertEquals(BubbleVerdict.VAZIA, limiar.verdictFor(t - m), "T-M, borda inclusiva do lado decidido")
        assertEquals(BubbleVerdict.INDECISA, limiar.verdictFor(t - 1), "T-1")
        assertEquals(BubbleVerdict.INDECISA, limiar.verdictFor(t), "T, o centro da duvida")
        assertEquals(BubbleVerdict.INDECISA, limiar.verdictFor(t + 1), "T+1")
        assertEquals(BubbleVerdict.MARCADA, limiar.verdictFor(t + m), "T+M, borda inclusiva do lado decidido")
        assertEquals(BubbleVerdict.MARCADA, limiar.verdictFor(t + m + 1), "T+M+1")
    }

    @Test
    fun `as bordas inclusivas sao as mesmas com que ADR-0011 aprova o corpus`() {
        // ADR-0011 aprova quando `V <= T - M` e `C >= T + M`. Se estas duas bordas fossem
        // exclusivas, o corpus que aprovou o limiar seria lido por ele com bolhas indecisas — o
        // criterio afirmaria uma coisa e a leitura faria outra.
        assertEquals(BubbleVerdict.VAZIA, limiar.verdictFor(limiar.undecidedFrom))
        assertEquals(BubbleVerdict.MARCADA, limiar.verdictFor(limiar.undecidedTo))
    }

    @Test
    fun `as pontas da escala sao decididas`() {
        // Papel perfeitamente branco e tinta perfeitamente preta nao podem cair na duvida.
        assertEquals(BubbleVerdict.VAZIA, limiar.verdictFor(0))
        assertEquals(BubbleVerdict.MARCADA, limiar.verdictFor(OmrMeasurement.FULL))
    }

    @Test
    fun `o veredito preserva a cobertura que o produziu`() {
        val julgada = limiar.judge(OmrMeasurement("q22", "A", 516))

        assertEquals(BubbleVerdict.MARCADA, julgada.verdict)
        assertEquals(516, julgada.measurement.coveragePerMille, "a cobertura sobrevive ao veredito")
        assertEquals("q22/A", julgada.id)
    }

    @Test
    fun `margem zero nao deixa nenhuma cobertura indecisa`() {
        // Nao e configuracao que o produto use — e a prova de que a faixa de indecisao e aberta:
        // com M = 0 ela e vazia, e ate o proprio limiar fica decidido.
        val semMargem = OmrThreshold(value = t, margin = 0)

        assertEquals(BubbleVerdict.MARCADA, semMargem.verdictFor(t))
        assertEquals(BubbleVerdict.VAZIA, semMargem.verdictFor(t - 1))
    }

    @Test
    fun `limiar fora da escala e faixa que sai da escala sao recusados`() {
        assertFailsWith<IllegalArgumentException> { OmrThreshold(value = -1, margin = 0) }
        assertFailsWith<IllegalArgumentException> { OmrThreshold(value = 1_001, margin = 0) }
        assertFailsWith<IllegalArgumentException> { OmrThreshold(value = t, margin = -1) }
        assertFailsWith<IllegalArgumentException> { OmrThreshold(value = 20, margin = 50) }
        assertFailsWith<IllegalArgumentException> { OmrThreshold(value = 980, margin = 50) }
    }

    @Test
    fun `folha cujo corredor contem o limiar e legivel`() {
        assertNull(limiar.validateAgainst(InkBudget.DEFAULT))
    }

    @Test
    fun `as duas pontas do corredor sao aceitas`() {
        // O corredor de ADR-0010 e fechado nas duas pontas: 200 e 400 sao valores que ele permite,
        // e uma guarda escrita com `<` recusaria os dois.
        assertNull(OmrThreshold(200, 50).validateAgainst(InkBudget.DEFAULT))
        assertNull(OmrThreshold(400, 50).validateAgainst(InkBudget.DEFAULT))
    }

    @Test
    fun `folha cujo corredor exclui o limiar e recusada, e a mensagem nomeia os dois`() {
        // A folha de um pacote antigo, que reservou outro corredor. Nao e lida com aproximacao.
        val outroCorredor = InkBudget.DEFAULT.copy(thresholdFloor = 100, thresholdCeiling = 180)

        val motivo = limiar.validateAgainst(outroCorredor)

        assertNotNull(motivo)
        assertTrue(motivo.contains("300"), "a mensagem precisa nomear o limiar do aplicativo: $motivo")
        assertTrue(motivo.contains("100"), "a mensagem precisa nomear o corredor da folha: $motivo")
        assertTrue(motivo.contains("180"), "a mensagem precisa nomear o corredor da folha: $motivo")
    }

    @Test
    fun `um passo alem de cada ponta do corredor ja recusa`() {
        val corredor = InkBudget.DEFAULT

        assertNotNull(OmrThreshold(corredor.thresholdFloor - 1, 50).validateAgainst(corredor))
        assertNotNull(OmrThreshold(corredor.thresholdCeiling + 1, 50).validateAgainst(corredor))
    }
}
