package com.platos.android.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A visao guardada sobre o `filesDir` de verdade.
 *
 * **O que este teste acrescenta ao de JVM**, que ja roda sobre `@TempDir`: que o caminho resolve e
 * que o aplicativo consegue escrever onde ele de fato guarda — `filesDir` so existe em aparelho. E o
 * mesmo par que `PacotesEmArquivoTest` tem com a tarefa 9.4 da fatia 4a.
 *
 * **O que ele NAO prova, e fica dito:** sobreviver a morte do processo. Uma instancia nova lendo o
 * que a anterior gravou mostra que o dado esta em disco e nao em memoria, e e ate onde um teste
 * instrumentado alcanca; matar o processo e reabrir e conferencia de aparelho, e ela e a secao 7
 * desta fatia.
 *
 * **A visao vai no armazenamento comum, e a afirmacao e falsificavel:** o arquivo aparece sob
 * `filesDir`, e o diretorio da credencial cifrada nao passa a existir por causa dela.
 */
@RunWith(AndroidJUnit4::class)
class VisaoEmRepousoInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val raiz = File(context.filesDir, "visoes-de-teste")

    private val visao = VisaoDaOrganizacao(
        organizacao = Organizacao(id = "01a06ba4-cb43-7d97-842d-165352d010b5", nome = "Escola de Teste"),
        provas = listOf(
            ProvaPublicada("prova-referencia-slice-1", "Prova de referencia", "a".repeat(64)),
            ProvaPublicada("prova-referencia-slice-2", "Prova adversarial", "b".repeat(64)),
        ),
        vistaEm = 1_757_000_000_000,
    )

    @After
    fun limpar() {
        raiz.deleteRecursively()
    }

    @Test
    fun a_visao_gravada_e_lida_por_outra_instancia_do_mesmo_diretorio() {
        VisoesEmArquivo(raiz).guardar(visao)

        // Instancia nova de proposito: se a leitura viesse de estado em memoria, este teste passaria
        // sem que nada tivesse chegado ao disco.
        val lida = VisoesEmArquivo(raiz).ler(visao.organizacao.id)

        assertEquals(visao, lida)
    }

    @Test
    fun a_visao_fica_no_armazenamento_comum_do_aplicativo() {
        VisoesEmArquivo(raiz).guardar(visao)

        val arquivo = File(raiz, "${visao.organizacao.id}.json")
        assertTrue("a visao nao foi escrita em filesDir: $arquivo", arquivo.isFile)

        // O canario da afirmacao: o conteudo esta legivel em claro, que e o que "armazenamento
        // comum" significa. Sem isto, "esta em filesDir" passaria tambem para um arquivo vazio.
        val texto = arquivo.readText()
        assertTrue("o nome da organizacao nao esta legivel no arquivo", texto.contains("Escola de Teste"))
        assertTrue("as provas nao estao no arquivo", texto.contains("prova-referencia-slice-2"))
    }

    @Test
    fun apagar_a_organizacao_remove_o_arquivo_do_disco() {
        val visoes = VisoesEmArquivo(raiz)
        visoes.guardar(visao)

        visoes.apagarDaOrganizacao(visao.organizacao.id)

        assertFalse(
            "o arquivo continua no disco depois de apagar",
            File(raiz, "${visao.organizacao.id}.json").exists(),
        )
    }
}
