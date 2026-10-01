package com.platos.android.scan

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** O que o caderno diz de uma nota por dar, de uma nota dada e do que a eliminacao das imagens nao desfaz. */
class CadernoCorrigidoTest {

    private fun resposta(arquivo: String) = RespostaGuardada(arquivo, 0L, false, 0)

    private fun completo(corrigido: Boolean = false, entregue: Boolean = true, captura: String? = "parcial-1") = Caderno(
        aluno = "tok-a",
        regioes = listOf(
            RegiaoDoCaderno(0, true, "Gabarito", EstadoDaRegiao.Capturada),
            RegiaoDoCaderno(1, false, "3", EstadoDaRegiao.Capturada, resposta("r1.png"), questionId = "d1"),
            RegiaoDoCaderno(2, false, "4", EstadoDaRegiao.Capturada, resposta("r2.png"), questionId = "d2"),
        ),
        parcial = null,
        entregue = entregue,
        capturaDaParcial = captura,
        corrigido = corrigido,
    )

    @Test
    fun `caderno completo, entregue e sem nota aguarda a nota`() {
        assertTrue(completo().aguardaNota)
    }

    @Test
    fun `caderno corrigido, nao entregue ou sem captura da parcial nao aguarda`() {
        assertFalse(completo(corrigido = true).aguardaNota)
        assertFalse(completo(entregue = false).aguardaNota)
        assertFalse(completo(captura = null).aguardaNota)
    }

    @Test
    fun `caderno incompleto nao aguarda a nota`() {
        val incompleto = completo().let { c ->
            c.copy(
                regioes = c.regioes.map {
                    if (it.regionIndex == 2) it.copy(estado = EstadoDaRegiao.NaoVista, resposta = null) else it
                },
            )
        }
        assertFalse(incompleto.aguardaNota)
    }

    @Test
    fun `discursiva sem questionId, de caderno guardado antes, nao oferece a nota`() {
        val antigo = completo().let { c -> c.copy(regioes = c.regioes.map { it.copy(questionId = null) }) }
        assertFalse(antigo.aguardaNota)
    }

    @Test
    fun `corrigidoSemRespostas tira so as respostas e marca corrigido`() {
        val depois = completo().corrigidoSemRespostas()

        assertTrue(depois.corrigido)
        assertTrue(depois.entregue)
        assertEquals("parcial-1", depois.capturaDaParcial)
        assertTrue(depois.regioes.all { it.resposta == null })
        assertEquals(3, depois.capturadas, "eliminar as imagens nao devolve regiao a nao vista")
    }

    @Test
    fun `normalizado nao devolve a nao vista a regiao de um caderno corrigido`() {
        val corrigido = completo(corrigido = true).corrigidoSemRespostas()

        assertEquals(corrigido, corrigido.normalizado { false })
    }

    @Test
    fun `normalizado continua devolvendo a nao vista quando o caderno nao esta corrigido`() {
        val lido = completo().normalizado { false }

        assertEquals(1, lido.capturadas, "so o gabarito sobrevive sem os arquivos")
    }

    @Test
    fun `caderno guardado antes dos campos novos continua legivel`() {
        val antigo = """{"aluno":"tok-a","regioes":[],"parcial":null}"""

        val lido = Json.decodeFromString(Caderno.serializer(), antigo)

        assertNull(lido.capturaDaParcial)
        assertFalse(lido.corrigido)
    }
}
