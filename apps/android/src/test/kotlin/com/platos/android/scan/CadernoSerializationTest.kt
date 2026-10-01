package com.platos.android.scan

import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.scoring.AwaitingEssay
import com.platos.domain.scoring.PartialScore
import com.platos.domain.scoring.PartialScoringOutcome
import com.platos.domain.scoring.PendingQuestion
import com.platos.domain.scoring.PendingReason
import com.platos.domain.scoring.QuestionOutcome
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * O `@Serializable` do caderno (`slice-5b-3-guardar-a-parcial-e-o-caderno`, tarefa 1.1) precisa
 * voltar a ser o mesmo `Caderno`, com os tres estados de regiao e a parcial guardada juntos — e nao
 * so compilar.
 */
class CadernoSerializationTest {

    private fun parcial(): PartialScore {
        val certa = QuestionOutcome("q1", QuestionAnswer.Marcada("q1", "A"), worth = 1, earned = 1)
        val indecisa = QuestionOutcome("q2", QuestionAnswer.Indecisa("q2", listOf("A")), worth = 1, earned = 0)
        return PartialScore(
            packageHash = "hash",
            variantId = "v1",
            objectivePoints = 1,
            objectiveMaxScore = 2,
            maxScore = 6,
            awaiting = listOf(AwaitingEssay("d1", 4)),
            pending = listOf(PendingQuestion("q2", PendingReason.INDECISA, 1)),
            outcomes = listOf(certa, indecisa),
        )
    }

    private fun caderno(parcial: PartialScoringOutcome?) = Caderno(
        aluno = "tok-a",
        regioes = listOf(
            RegiaoDoCaderno(0, gabarito = true, rotulo = Caderno.ROTULO_GABARITO, estado = EstadoDaRegiao.Capturada),
            RegiaoDoCaderno(1, gabarito = false, rotulo = "1", estado = EstadoDaRegiao.ComProblema("QR ilegivel")),
            RegiaoDoCaderno(2, gabarito = false, rotulo = "2", estado = EstadoDaRegiao.NaoVista),
        ),
        parcial = parcial,
    )

    @Test
    fun `o caderno com a parcial apurada volta igual`() {
        val original = caderno(PartialScoringOutcome.Scored(parcial()))

        val json = Json.encodeToString(Caderno.serializer(), original)
        val deVolta = Json.decodeFromString(Caderno.serializer(), json)

        assertEquals(original, deVolta)
    }

    @Test
    fun `o caderno com a parcial recusada volta igual`() {
        val original = caderno(PartialScoringOutcome.Rejected("itens lidos divergem da variante"))

        val json = Json.encodeToString(Caderno.serializer(), original)
        val deVolta = Json.decodeFromString(Caderno.serializer(), json)

        assertEquals(original, deVolta)
    }

    /**
     * Um caderno guardado ANTES da `slice-5c-1-a-resposta-fica-no-aparelho`: JSON literal, com `entregue`
     * e sem a chave `resposta`. O oraculo e o texto, e nao o codificador atual (P4): se o campo novo
     * perdesse o valor-padrao, este decode quebraria, e so ele.
     */
    @Test
    fun `um caderno guardado antes da resposta decodifica e vira o mesmo objeto`() {
        val capturada = """{"type":"com.platos.android.scan.EstadoDaRegiao.Capturada"}"""
        val naoVista = """{"type":"com.platos.android.scan.EstadoDaRegiao.NaoVista"}"""
        val antigo = """
            {"aluno":"tok-a","regioes":[
              {"regionIndex":0,"gabarito":true,"rotulo":"Gabarito","estado":$capturada},
              {"regionIndex":1,"gabarito":false,"rotulo":"1","estado":$capturada},
              {"regionIndex":2,"gabarito":false,"rotulo":"2","estado":$naoVista}
            ],"parcial":null,"entregue":true}
        """.trimIndent()

        val lido = Json.decodeFromString(Caderno.serializer(), antigo)

        assertEquals(
            Caderno(
                aluno = "tok-a",
                regioes = listOf(
                    RegiaoDoCaderno(0, gabarito = true, rotulo = Caderno.ROTULO_GABARITO, estado = EstadoDaRegiao.Capturada),
                    RegiaoDoCaderno(1, gabarito = false, rotulo = "1", estado = EstadoDaRegiao.Capturada),
                    RegiaoDoCaderno(2, gabarito = false, rotulo = "2", estado = EstadoDaRegiao.NaoVista),
                ),
                parcial = null,
                entregue = true,
            ),
            lido,
        )
        assertNull(lido.regioes[1].resposta)
    }

    @Test
    fun `o caderno com a resposta guardada volta igual`() {
        val resposta = RespostaGuardada(
            arquivo = "3f2c9a52-0000-4000-8000-000000000001.png",
            capturadaEm = 1_759_300_000_000L,
            desvioSinalizado = true,
            foraPpm = 61_000,
        )
        val base = caderno(parcial = null)
        val original = base.copy(
            regioes = base.regioes.map {
                if (it.regionIndex == 1) it.copy(estado = EstadoDaRegiao.Capturada, resposta = resposta) else it
            },
        )

        val json = Json.encodeToString(Caderno.serializer(), original)
        val deVolta = Json.decodeFromString(Caderno.serializer(), json)

        assertEquals(original, deVolta)
        assertEquals(resposta, deVolta.regioes[1].resposta)
    }

    @Test
    fun `o caderno sem gabarito lido ainda volta igual`() {
        val original = caderno(parcial = null)

        val json = Json.encodeToString(Caderno.serializer(), original)
        val deVolta = Json.decodeFromString(Caderno.serializer(), json)

        assertEquals(original, deVolta)
    }
}
