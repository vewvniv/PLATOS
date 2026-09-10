package com.platos.android.session

import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * O que a tela marca, e o que ela diz quando a atualizacao nao deu.
 *
 * Mesma razao de [TextoSemOrganizacaoTest], uma camada acima das maquinas: `PreparoDaProva` e
 * `DeviceSession` provam que a **procedencia** chega ao estado, e isso nao impede quem desenha de
 * ignora-la. O que este arquivo prende e a decisao de texto e de formato; o que ele **nao** alcanca e
 * o `@Composable` desenhar o selo, e isso esta registrado como lacuna na tarefa 6.4.
 *
 * **Os instantes esperados foram calculados fora da JVM**, em Python com `zoneinfo` (P4): conferir o
 * `DateTimeFormatter` contra outra formatacao em Kotlin seria o mesmo relogio julgando a si mesmo.
 * `1757000000000` e 04/09/2025 15:33Z, e 12:33 em Sao Paulo.
 */
class MarcaDeLeituraTest {

    private val saoPaulo = ZoneId.of("America/Sao_Paulo")
    private val utc = ZoneId.of("UTC")
    private val vistaEm = 1_757_000_000_000L

    @Test
    fun `dado fresco nao leva selo`() {
        // Marcar tudo e nao marcar nada: se o selo aparecesse tambem no dado que acabou de chegar,
        // ele deixaria de distinguir o que a fatia existe para distinguir.
        assertNull(marcaDeLeitura(Procedencia.Fresca, saoPaulo))
    }

    @Test
    fun `dado cacheado leva rotulo e a idade em data e hora`() {
        val marca = marcaDeLeitura(Procedencia.Cacheada(vistaEm), saoPaulo)

        assertEquals(MarcaDeLeitura("SEM CONEXAO", "visto em 04/09/2025 as 12:33"), marca)
    }

    @Test
    fun `o fuso do parametro e o que decide a hora apresentada`() {
        // Sem esta asercao, uma implementacao que ignorasse o parametro e usasse
        // `ZoneId.systemDefault()` passaria em todo lugar onde o CI roda em UTC — e apresentaria a
        // hora errada no aparelho do professor, que e o unico lugar que importa.
        val emSaoPaulo = marcaDeLeitura(Procedencia.Cacheada(vistaEm), saoPaulo)
        val emUtc = marcaDeLeitura(Procedencia.Cacheada(vistaEm), utc)

        assertEquals("visto em 04/09/2025 as 12:33", emSaoPaulo?.idade)
        assertEquals("visto em 04/09/2025 as 15:33", emUtc?.idade)
    }

    @Test
    fun `a idade leva o ano`() {
        // A decisao 2 recusou teto de validade, entao visao do ano passado nao e hipotese: e
        // consequencia. Sem o ano, `04/09` de 2024 e indistinguivel de `04/09` de 2025 — e a
        // diferenca entre as duas e todo o motivo de a idade estar na tela.
        val doAnoPassado = marcaDeLeitura(Procedencia.Cacheada(1_725_464_000_000), saoPaulo)
        val deAgora = marcaDeLeitura(Procedencia.Cacheada(vistaEm), saoPaulo)

        assertEquals("visto em 04/09/2024 as 12:33", doAnoPassado?.idade)
        assertNotEquals(deAgora?.idade, doAnoPassado?.idade)
    }

    @Test
    fun `sem tentativa frustrada nao ha aviso`() {
        assertNull(avisoDeAtualizacao(null as FalhaDaConsulta?))
        assertNull(avisoDeAtualizacao(null as FalhaDaListagem?))
    }

    @Test
    fun `as duas causas dizem coisas diferentes, nas duas telas`() {
        // Sem rede se tenta mais tarde; resposta que nao serve se tenta de novo agora. Uma frase so
        // para as duas mandaria o professor repetir uma acao que nao tem como funcionar.
        val daConsulta = FalhaDaConsulta.entries.map { avisoDeAtualizacao(it) }
        val daListagem = FalhaDaListagem.entries.map { avisoDeAtualizacao(it) }

        assertEquals(FalhaDaConsulta.entries.size, daConsulta.toSet().size, "avisos repetidos: $daConsulta")
        assertEquals(FalhaDaListagem.entries.size, daListagem.toSet().size, "avisos repetidos: $daListagem")
        daConsulta.forEach { assertTrue(!it.isNullOrBlank(), "aviso em branco") }
        daListagem.forEach { assertTrue(!it.isNullOrBlank(), "aviso em branco") }
    }

    @Test
    fun `todo aviso diz que o que esta na tela continua valendo`() {
        // Lista branca por igualdade exata, como em `TextoSemOrganizacaoTest`, e nao busca por
        // palavra: o requisito e sobre **o que pode ser dito** quando a atualizacao falha, e a metade
        // que importa e a segunda frase. Um aviso novo que esquecesse dela — "nao foi possivel
        // atualizar", e ponto — deixaria o professor sem saber se a tela ainda vale.
        val permitido = setOf(
            "Nao foi possivel atualizar: o aparelho nao alcancou o servidor. " +
                "O que esta na tela continua valendo.",
            "O servidor respondeu, mas nao foi possivel usar a resposta. " +
                "O que esta na tela continua valendo.",
        )

        val dito = (
            FalhaDaConsulta.entries.mapNotNull { avisoDeAtualizacao(it) } +
                FalhaDaListagem.entries.mapNotNull { avisoDeAtualizacao(it) }
            ).toSet()

        assertEquals(permitido, dito, "a tela passou a dizer algo fora da lista do requisito")
    }
}
