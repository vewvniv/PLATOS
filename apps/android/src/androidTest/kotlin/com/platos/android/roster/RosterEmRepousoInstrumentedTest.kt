package com.platos.android.roster

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
 * O roster guardado sobre o `filesDir` de verdade.
 *
 * **O que este teste acrescenta ao de JVM**, que ja roda sobre `@TempDir`: que o caminho resolve e
 * que o aplicativo consegue escrever onde ele de fato guarda — `filesDir` so existe em aparelho. E o
 * mesmo par que `VisoesEmArquivo` e `PacotesEmArquivo` ja tem, e a ausencia dele para o roster foi
 * achada lendo a lista de classes instrumentadas depois que a suite passou: 49 cenarios verdes, e
 * nenhum tocava o roster. Suite vizinha verde nao verifica esta camada (P16).
 *
 * **O que ele NAO prova, e fica dito:** sobreviver a morte do processo. Uma instancia nova lendo o
 * que a anterior gravou mostra que o dado esta em disco e nao em memoria, e e ate onde um teste
 * instrumentado alcanca.
 *
 * **O roster vai no armazenamento comum, e a afirmacao e falsificavel** — e aqui ela carrega um peso
 * que a visao nao tem: o que fica legivel em claro sob `filesDir` e **nome de aluno**. E assim por
 * decisao, e nao por descuido: ADR-0013 poe no armazenamento comum o que nao e credencial de rede
 * reutilizavel, e o que protege o nome nao e cifra e sim o apagamento — ao sair, na revogacao e na
 * desinstalacao. Este teste afirma as duas coisas: que esta la em claro, e que o apagamento o tira.
 *
 * **Um diretorio de teste proprio**, e nao `rosters`: a suite instrumentada desinstala o aplicativo
 * ao terminar e leva o `filesDir` junto, mas enquanto ela roda o diretorio de producao e do
 * aplicativo, e escrever nele faria este teste e o aplicativo disputarem o mesmo caminho.
 */
@RunWith(AndroidJUnit4::class)
class RosterEmRepousoInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val raiz = File(context.filesDir, "rosters-de-teste")

    private val organizacao = "01a06ba4-cb43-7d97-842d-165352d010b5"
    private val outra = "01a06ba4-0000-7d97-842d-165352d010b5"
    private val prova = "prova-referencia-slice-1"

    private val roster = RosterDaProva(
        alunos = listOf(
            AlunoDoRoster("tok-a", "Ana Ribeiro"),
            AlunoDoRoster("tok-b", "Bruno Alves"),
        ),
        puxadoEm = 1_757_000_000_000,
    )

    @After
    fun limpar() {
        raiz.deleteRecursively()
    }

    @Test
    fun o_roster_gravado_e_lido_por_outra_instancia_do_mesmo_diretorio() {
        RostersEmArquivo(raiz).guardar(organizacao, prova, roster)

        // Instancia nova de proposito: se a leitura viesse de estado em memoria, este teste passaria
        // sem que nada tivesse chegado ao disco.
        val lido = RostersEmArquivo(raiz).ler(organizacao, prova)

        assertEquals(roster, lido)
    }

    @Test
    fun o_roster_fica_no_armazenamento_comum_do_aplicativo() {
        RostersEmArquivo(raiz).guardar(organizacao, prova, roster)

        val arquivo = File(File(raiz, organizacao), "$prova.json")
        assertTrue("o roster nao foi escrito em filesDir: $arquivo", arquivo.isFile)

        // O canario da afirmacao: o conteudo esta legivel em claro, que e o que "armazenamento
        // comum" significa. Sem isto, "esta em filesDir" passaria tambem para um arquivo vazio.
        val texto = arquivo.readText()
        assertTrue("o nome do aluno nao esta legivel no arquivo", texto.contains("Ana Ribeiro"))
        assertTrue("o token nao esta no arquivo", texto.contains("tok-b"))
    }

    /**
     * O apagamento por organizacao remove o **diretorio inteiro**, e nao um arquivo por vez.
     *
     * E o que sair e a revogacao chamam, e o que o §16 exige que exista. A segunda asercao e o que
     * impede "apagou" de significar "apagou tudo": o roster da outra organizacao continua la.
     */
    @Test
    fun apagar_a_organizacao_remove_o_diretorio_do_disco_e_deixa_o_da_outra() {
        val rosters = RostersEmArquivo(raiz)
        rosters.guardar(organizacao, prova, roster)
        rosters.guardar(outra, prova, roster)

        rosters.apagarDaOrganizacao(organizacao)

        assertFalse(
            "o diretorio do roster continua no disco depois de apagar",
            File(raiz, organizacao).exists(),
        )
        assertTrue(
            "apagar uma organizacao levou o roster da outra junto",
            File(File(raiz, outra), "$prova.json").isFile,
        )
    }
}
