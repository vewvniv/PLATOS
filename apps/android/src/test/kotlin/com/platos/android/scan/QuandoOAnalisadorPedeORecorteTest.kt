package com.platos.android.scan

import com.platos.android.vision.FrameOutcome
import com.platos.domain.capture.CapturePayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Quando o analisador pede o recorte (`slice-5c-1-a-resposta-fica-no-aparelho`, tarefas 2.2 e 2.3), nas
 * duas metades que sao Kotlin puro: o predicado "esta regiao ja tem resposta para este aluno" e o
 * predicado "este estado analisa quadro". A parte que mexe em imagem e do teste instrumentado.
 */
class QuandoOAnalisadorPedeORecorteTest {

    private val resposta = RespostaGuardada("a.png", capturadaEm = 1L, desvioSinalizado = false, foraPpm = 0)

    private fun caderno(aluno: String = "tok-a") = Caderno(
        aluno = aluno,
        regioes = listOf(
            RegiaoDoCaderno(0, gabarito = true, rotulo = Caderno.ROTULO_GABARITO, estado = EstadoDaRegiao.Capturada),
            RegiaoDoCaderno(1, gabarito = false, rotulo = "1", estado = EstadoDaRegiao.Capturada, resposta = resposta),
            RegiaoDoCaderno(2, gabarito = false, rotulo = "2", estado = EstadoDaRegiao.NaoVista),
        ),
        parcial = null,
    )

    // --- 2.2: o predicado e por aluno e por regiao ---

    @Test
    fun `a regiao com resposta do mesmo aluno ja tem resposta`() {
        assertTrue(caderno().jaTemResposta("tok-a", 1))
    }

    /** Cenario "Folha de outro aluno pede de novo": a resposta e de A, e para B ainda falta. */
    @Test
    fun `a folha de outro aluno nao herda a resposta do aluno do caderno`() {
        assertFalse(caderno().jaTemResposta("tok-b", 1))
    }

    @Test
    fun `regiao sem resposta, regiao de gabarito, regiao inexistente e caderno ausente nao tem resposta`() {
        assertFalse(caderno().jaTemResposta("tok-a", 2), "nao vista")
        assertFalse(caderno().jaTemResposta("tok-a", 0), "gabarito nao tem resposta")
        assertFalse(caderno().jaTemResposta("tok-a", 99), "regiao que o caderno nao declara")
        assertFalse((null as Caderno?).jaTemResposta("tok-a", 1), "sem caderno nao ha o que ja ter")
    }

    // --- 2.3: a analise para sozinha ---

    private fun daProvaComDiscursiva() = ScanState.ProvaComDiscursiva(
        aluno = "tok-a",
        gabarito = "lido",
        discursivas = listOf("d1"),
        discursivasNaoLidas = emptyList(),
        caderno = caderno(),
    )

    /** So `Searching` e `NotRead` analisam; o estado de uma prova com discursiva reconhecida **nao**. */
    @Test
    fun `so Searching e NotRead analisam quadro`() {
        assertTrue(deveAnalisar(ScanState.Searching))
        assertTrue(deveAnalisar(ScanState.NotRead("qr ilegivel")))

        assertFalse(deveAnalisar(daProvaComDiscursiva()), "depois de reconhecer a discursiva a analise para")
        assertFalse(deveAnalisar(ScanState.NoPermission))
        assertFalse(deveAnalisar(ScanState.Rejected("outra prova")))
    }

    /**
     * Um quadro que reconhece uma discursiva leva a sessao a um estado que nao analisa: a propriedade de que
     * o recorte acontece uma vez por toque. Aqui pelo estado **real** da sessao, e nao por um literal.
     */
    @Test
    fun `depois de um quadro que reconhece a discursiva a sessao esta num estado que nao analisa`() {
        val pacote = pacoteDaFixture()
        val sessao = ScanSession(pacote).apply { onPermission(granted = true) }
        assertTrue(deveAnalisar(sessao.state), "guarda de vacuidade: a sessao nova procura a folha")

        val reconhecida = com.platos.android.vision.RegiaoDiscursivaNoQuadro.Reconhecida(
            1, "d1", CapturePayload(pacote.meta.examId, "tok-a", "v1", 1),
        )
        sessao.onFrame(
            FrameOutcome.SoDiscursivas(listOf(reconhecida)),
            mapOf(1 to RespostaDoQuadro.Guardada(resposta)),
        )

        assertTrue(sessao.state is ScanState.ProvaComDiscursiva)
        assertFalse(deveAnalisar(sessao.state))
        assertEquals(1, (sessao.state as ScanState.ProvaComDiscursiva).discursivas.size)
        sessao.resume()
        assertTrue(deveAnalisar(sessao.state), "escanear outra folha volta a analisar")
    }

    private fun pacoteDaFixture() = kotlinx.serialization.json.Json.decodeFromString<com.platos.domain.exam.ExamPackage>(
        java.io.File(
            System.getProperty("platos.fixtures") ?: error("propriedade `platos.fixtures` nao definida pelo build"),
            "prova-discursiva.package.json",
        ).readText(),
    )
}
