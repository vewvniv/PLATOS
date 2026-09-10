package com.platos.android.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * O que a tela pode dizer quando nao ha organizacao — e o que ela nao pode.
 *
 * A tarefa 3.4 provou que `DeviceSession` nao cai num nome de reserva. Isso nao protege a tela:
 * uma sessao que nunca inventa nome nao impede quem desenha de inventar um. Esta e a mesma prova
 * uma camada acima, pela razao da decisao 3 — cada camada precisa da propria.
 */
class TextoSemOrganizacaoTest {

    @Test
    fun `as duas falhas dizem coisas diferentes`() {
        val explicacoes = FalhaDaConsulta.entries.map { textoSemOrganizacao(it).explicacao }

        // Sem rede se resolve tentando de novo; o resto nao. Apresentar as duas com a mesma frase
        // mandaria o professor repetir uma acao que nao tem como funcionar.
        assertEquals(
            FalhaDaConsulta.entries.size,
            explicacoes.toSet().size,
            "duas falhas compartilham a mesma explicacao: $explicacoes",
        )
    }

    @Test
    fun `nenhum texto e vazio`() {
        FalhaDaConsulta.entries.forEach { falha ->
            val texto = textoSemOrganizacao(falha)
            assertTrue(texto.titulo.isNotBlank(), "titulo em branco para $falha")
            assertTrue(texto.explicacao.isNotBlank(), "explicacao em branco para $falha")
        }
    }

    @Test
    fun `o titulo nao varia com a falha`() {
        // Ele diz o que e verdade nos dois casos: nao sabemos a organizacao. Varia-lo abriria a
        // porta para ele passar a descrever outra coisa — um nome, por exemplo.
        assertEquals(1, FalhaDaConsulta.entries.map { textoSemOrganizacao(it).titulo }.toSet().size)
    }

    @Test
    fun `nenhum nome de organizacao e apresentado`() {
        // Afirmacao por igualdade exata, e nao por procurar palavra suspeita. O requisito e sobre
        // **o que pode ser dito**, entao o teste enumera o que pode ser dito: qualquer nome de
        // reserva enfiado no texto muda uma destas strings, e o cenario fica vermelho.
        //
        // Procurar por "organizacao" no texto seria classificar por string — o que esta fatia
        // recusa desde a 3c — e ainda erraria, porque o titulo legitimamente contem a palavra.
        val permitido = setOf(
            "Nao foi possivel obter sua organizacao",
            "Nao foi possivel falar com o servidor, e este aparelho ainda nao guardou nada desta " +
                "organizacao. Conecte-se uma vez; depois disso ele abre sem rede.",
            "O servidor respondeu, mas nao foi possivel usar a resposta. Tente de novo; se " +
                "continuar, saia e entre novamente.",
        )

        val dito = FalhaDaConsulta.entries
            .flatMap { listOf(textoSemOrganizacao(it).titulo, textoSemOrganizacao(it).explicacao) }
            .toSet()

        assertEquals(permitido, dito, "a tela passou a dizer algo que nao esta na lista do requisito")
    }
}
