package com.platos.android.session

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * A visao guardada, no sistema de arquivos de verdade e sem aparelho.
 *
 * `@TempDir` da um diretorio real, pela mesma razao de `PacotesEmArquivoTest`: um duplo em memoria
 * verificaria a intencao do codigo, e o que precisa ser verificado e o comportamento sobre arquivos.
 */
class VisoesEmArquivoTest {

    @TempDir
    lateinit var raiz: File

    private val visoes: VisoesEmArquivo by lazy { VisoesEmArquivo(raiz) }

    private val escola = Organizacao(id = "01a06ba4-cb43-7d97-842d-165352d010b5", nome = "Escola de Teste")
    private val outra = Organizacao(id = "01a06ba4-0000-7d97-842d-165352d010b5", nome = "Outra Escola")

    private val matematica = ProvaPublicada("mat-7a-2026-1", "Prova de Matematica", "a".repeat(64))
    private val portugues = ProvaPublicada("por-7a-2026-1", "Prova de Portugues", "b".repeat(64))

    private fun visao(
        organizacao: Organizacao = escola,
        provas: List<ProvaPublicada> = listOf(matematica),
        vistaEm: Long = 1_757_000_000_000,
    ) = VisaoDaOrganizacao(organizacao, provas, vistaEm)

    @Test
    fun o_que_foi_gravado_e_o_que_e_lido() {
        val gravada = visao(provas = listOf(matematica, portugues))

        visoes.guardar(gravada)

        assertEquals(gravada, visoes.ler(escola.id))
    }

    @Test
    fun organizacao_nunca_vista_nao_tem_visao() {
        assertNull(visoes.ler(escola.id))
    }

    /**
     * **O cenario que a spec chama de "substituida por inteiro".**
     *
     * A afirmacao e sobre a prova que **sumiu**, e nao sobre a que chegou: uma implementacao que
     * emendasse as duas listas passaria num teste que so procurasse a prova nova. Prova que a
     * organizacao ja nao publica e indistinguivel de prova que existe, para quem le a tela.
     */
    @Test
    fun gravacao_posterior_substitui_por_inteiro() {
        visoes.guardar(visao(provas = listOf(matematica, portugues)))

        visoes.guardar(visao(provas = listOf(portugues), vistaEm = 1_757_000_099_000))

        val lida = requireNotNull(visoes.ler(escola.id)) { "a visao gravada nao foi lida" }
        assertEquals(listOf(portugues), lida.provas, "sobrou prova da visao anterior: ${lida.provas}")
        assertEquals(1_757_000_099_000, lida.vistaEm, "o instante nao foi substituido junto")
    }

    @Test
    fun apagar_uma_organizacao_nao_toca_nas_outras() {
        visoes.guardar(visao(organizacao = escola))
        visoes.guardar(visao(organizacao = outra))

        visoes.apagarDaOrganizacao(escola.id)

        assertNull(visoes.ler(escola.id), "a visao apagada continua legivel")
        assertNotNull(visoes.ler(outra.id), "apagar uma organizacao levou a visao da outra junto")
    }

    /**
     * Visao ilegivel e ausencia de visao, e nao erro a apresentar.
     *
     * Truncamento por queda de energia e formato de uma versao anterior caem aqui, e o desfecho
     * certo e o mesmo de nunca ter havido visao: pedir rede uma vez. O arquivo sai do caminho para
     * nao ser relido a cada arranque.
     */
    @Test
    fun visao_ilegivel_e_tratada_como_inexistente() {
        visoes.guardar(visao())
        val arquivo = File(raiz, "${escola.id}.json")
        arquivo.writeText("{\"organization_id\": \"nao fecha")

        assertNull(visoes.ler(escola.id))
        assertFalse(arquivo.exists(), "o arquivo ilegivel ficou para tras")
    }

    /**
     * Identificador com forma de caminho nao vira caminho.
     *
     * A organizacao vem da API e vira nome de arquivo; `..` sairia do diretorio do aplicativo. Mesma
     * guarda de `PacotesEmArquivo`, e ela e conferida aqui porque a raiz e outra.
     */
    @Test
    fun identificador_fora_de_forma_nao_escreve_nem_le() {
        val torto = Organizacao(id = "../fora", nome = "Fora")

        visoes.guardar(visao(organizacao = torto))

        assertNull(visoes.ler(torto.id))
        assertEquals(emptyList<String>(), raiz.parentFile.listFiles()?.map { it.name }?.filter { it == "fora.json" })
    }
}
