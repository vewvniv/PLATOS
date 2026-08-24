package com.platos.domain.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * O contrato entre a leitura optica e o scoring.
 *
 * Metade destes testes afirma o que o tipo **nao** tem. Isso e incomum e e deliberado: o corte
 * desta fatia — medir sem interpretar — mora numa ausencia, e ausencia nao quebra teste sozinha.
 * Alguem acrescentar `marcada: Boolean` aqui seria uma linha inocente que traz junto um limiar que
 * ADR-0007 manda medir antes de escolher.
 */
class OmrMeasurementTest {

    @Test
    fun `a medicao carrega bolha e cobertura, e o id casa com o do mapa`() {
        // 516 por mil e a bolha mais fraca medida no papel da fatia 2b: `q22/A`, um rabisco que
        // nao fecha o circulo.
        val medicao = OmrMeasurement(questionId = "q22", option = "A", coveragePerMille = 516)

        assertEquals("q22/A", medicao.id)
        assertEquals(516, medicao.coveragePerMille)
    }

    @Test
    fun `cobertura fora da escala e recusada`() {
        assertFailsWith<IllegalArgumentException> { OmrMeasurement("q01", "A", 1_001) }
        assertFailsWith<IllegalArgumentException> { OmrMeasurement("q01", "A", -1) }
    }

    @Test
    fun `as pontas da escala sao aceitas`() {
        // Papel perfeitamente branco e tinta perfeitamente preta sao valores legitimos, e uma
        // guarda escrita com `<` em vez de `<=` recusaria os dois.
        assertEquals(0, OmrMeasurement("q01", "A", 0).coveragePerMille)
        assertEquals(OmrMeasurement.FULL, OmrMeasurement("q01", "A", 1_000).coveragePerMille)
    }

    @Test
    fun `a escala e a mesma em que a regiao declara o orcamento`() {
        // Nao e detalhe de unidade: `ink_budget` viaja no `LayoutMap` em permilagem inteira, e a
        // fatia 3 vai comparar medicao com corredor. Mesma escala e o que dispensa conversao na
        // fronteira de um limiar, que e onde mora o erro de um passo.
        assertEquals(1_000, OmrMeasurement.FULL)
    }

    @Test
    fun `a leitura devolve o payload junto das medicoes`() {
        val leitura = OmrReading.Read(
            payload = CapturePayload("prova", "", "", 0),
            measurements = listOf(OmrMeasurement("q01", "A", 52)),
        )

        assertEquals("prova", leitura.payload.examShortId)
        assertEquals(1, leitura.measurements.size)
    }

    @Test
    fun `a recusa carrega motivo legivel`() {
        val leitura: OmrReading = OmrReading.Rejected("marcadores encontrados: 3, esperados 4")
        assertTrue(leitura is OmrReading.Rejected)
        assertTrue(leitura.reason.isNotBlank())
    }

    @Test
    fun `a medicao tem exatamente tres campos, e nenhum deles e veredito`() {
        // O corte da fatia, afirmado sobre o tipo e nao sobre a prosa. `toString` de uma data
        // class lista todas as propriedades do construtor, entao um campo novo — `marcada`,
        // `limiar`, o que for — derruba este teste. A conversa sobre ADR-0007 acontece antes do
        // commit, e nao depois de o limiar estar em producao.
        assertEquals(
            "OmrMeasurement(questionId=q01, option=A, coveragePerMille=500)",
            OmrMeasurement("q01", "A", 500).toString(),
        )
    }

    @Test
    fun `a leitura bem-sucedida tem exatamente payload e medicoes`() {
        assertEquals(
            "Read(payload=CapturePayload(examShortId=p, studentToken=, variant=, regionIndex=0), " +
                "measurements=[])",
            OmrReading.Read(CapturePayload("p", "", "", 0), emptyList()).toString(),
            "os campos de `OmrReading.Read` ou de `CapturePayload` mudaram",
        )
    }
}
