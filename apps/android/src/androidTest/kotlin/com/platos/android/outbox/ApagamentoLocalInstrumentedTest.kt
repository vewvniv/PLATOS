package com.platos.android.outbox

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.android.pacote.PacotesEmArquivo
import com.platos.android.roster.AlunoDoRoster
import com.platos.android.roster.RosterDaProva
import com.platos.android.roster.RostersEmArquivo
import com.platos.android.session.DeviceSession
import com.platos.android.session.Organizacao
import com.platos.android.session.ProvaPublicada
import com.platos.android.session.ResultadoDasOrganizacoes
import com.platos.android.session.SessaoGuardada
import com.platos.android.session.VisaoDaOrganizacao
import com.platos.android.session.VisoesEmArquivo
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.scoring.ObjectiveScore
import com.platos.domain.scoring.QuestionOutcome
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O que sai e o que fica do `filesDir` quando o usuario sai, e quando o vinculo e revogado.
 *
 * **Teste instrumentado no lugar da conferencia manual por `adb`.** As tarefas 7.1 e 7.4 pediam um
 * `find files -type f` em aparelho. Isto e a mesma conferencia — o `filesDir` de verdade, as
 * implementacoes de verdade, o SQLite de verdade —, com tres coisas que a conferencia manual nao
 * tem: ela e reproduzivel, entra no CI, e **pode ser vista falhar**. Uma inspecao por `adb` que
 * passasse hoje nao reprova a migration de amanha.
 *
 * **As duas metades sao conferidas na mesma execucao**, e isso nao e zelo: uma assercao que so
 * olhasse o pendente nao distinguiria "preservou o pendente" de "nao apagou nada", e passaria numa
 * `sair()` quebrada que tivesse deixado tambem o roster para tras. E a razao de cada teste aqui
 * afirmar **presenca** e **ausencia** lado a lado.
 *
 * **Diretorios de teste proprios**, e nao os de producao: a suite roda com o aplicativo instalado, e
 * escrever nos diretorios dele faria o teste e o aplicativo disputarem o mesmo caminho.
 */
@RunWith(AndroidJUnit4::class)
class ApagamentoLocalInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val raizPacotes = File(context.filesDir, "packages-apagamento-de-teste")
    private val raizVisoes = File(context.filesDir, "visoes-apagamento-de-teste")
    private val raizRosters = File(context.filesDir, "rosters-apagamento-de-teste")
    private val nomeDaBase = "outbox-apagamento-de-teste.db"

    private val escola = Organizacao("01a06ba4-cb43-7d97-842d-165352d010b5", "Escola Municipal")
    private val pessoal = Organizacao("01a06ba4-0000-7d97-842d-165352d010b5", "Leon")
    private val prova = ProvaPublicada("prova-referencia-slice-1", "Prova", "a".repeat(64))

    private lateinit var base: BaseDoOutbox
    private lateinit var pendentes: ResultadosPendentes
    private lateinit var sessao: DeviceSession

    /** A sessao guardada, em memoria: o que esta sob teste e o apagamento de **arquivo**. */
    private class Guardada(private var organizacao: String?) : SessaoGuardada {
        override fun organizacaoEscolhida(): String? = organizacao
        override fun guardarOrganizacaoEscolhida(id: String) { organizacao = id }
        override fun apagarOrganizacaoEscolhida() { organizacao = null }
        override fun apagarCredencial() = Unit
    }

    private fun umPendente(captureId: String, organizacao: String) = ResultadoPendente(
        captureId = captureId,
        organizacao = organizacao,
        prova = prova.shortId,
        studentToken = "tok-a",
        apuradoEm = 1_789_646_400_000L,
        nota = ObjectiveScore(
            packageHash = "a".repeat(64),
            variantId = "v1",
            points = 1,
            maxScore = 1,
            pending = emptyList(),
            outcomes = listOf(
                QuestionOutcome("q01", QuestionAnswer.Marcada("q01", "A"), worth = 1, earned = 1),
            ),
        ),
    )

    /** Deixa no disco as quatro coisas que a organizacao guarda, para haver o que apagar. */
    private fun montarOEstadoLocal(organizacao: String) {
        PacotesEmArquivo(raizPacotes).guardar(organizacao, "b".repeat(64), "{}".toByteArray())
        VisoesEmArquivo(raizVisoes).guardar(
            VisaoDaOrganizacao(
                Organizacao(organizacao, "Escola Municipal"),
                listOf(prova),
                vistaEm = 1_757_000_000_000,
            ),
        )
        RostersEmArquivo(raizRosters).guardar(
            organizacao,
            prova.shortId,
            RosterDaProva(listOf(AlunoDoRoster("tok-a", "Ana Ribeiro")), puxadoEm = 1_757_000_000_000),
        )
        pendentes.guardar(umPendente("cap-1", organizacao))
    }

    private fun arquivosDe(raiz: File): List<String> =
        raiz.walkTopDown().filter { it.isFile }.map { it.name }.toList()

    private fun montarSessao(organizacaoAtiva: String?) {
        sessao = DeviceSession(
            Guardada(organizacaoAtiva),
            PacotesEmArquivo(raizPacotes),
            VisoesEmArquivo(raizVisoes),
            RostersEmArquivo(raizRosters),
            pendentes,
        )
    }

    @Before
    fun preparar() {
        context.deleteDatabase(nomeDaBase)
        base = Room.databaseBuilder(context, BaseDoOutbox::class.java, nomeDaBase)
            .allowMainThreadQueries()
            .build()
        pendentes = ResultadosEmRoom(base.pendentes())
        listOf(raizPacotes, raizVisoes, raizRosters).forEach { it.deleteRecursively() }
    }

    @After
    fun limpar() {
        base.close()
        context.deleteDatabase(nomeDaBase)
        listOf(raizPacotes, raizVisoes, raizRosters).forEach { it.deleteRecursively() }
    }

    // ------------------------------------------------------------------ 7.1

    @Test
    fun sair_apaga_referencia_do_disco_e_preserva_o_pendente() {
        montarOEstadoLocal(escola.id)
        montarSessao(escola.id)

        // O canario (P13): sem as quatro presentes antes, "sumiu" nao distingue apagamento de nunca
        // ter existido, e "ficou" nao distingue preservacao de gravacao que falhou.
        assertEquals(1, arquivosDe(raizPacotes).size)
        assertEquals(1, arquivosDe(raizVisoes).size)
        assertEquals(1, arquivosDe(raizRosters).size)
        assertEquals(1, pendentes.quantosPendentes(escola.id))

        sessao.sair()

        assertEquals("o pacote ficou no disco depois de sair", emptyList<String>(), arquivosDe(raizPacotes))
        assertEquals("a visao ficou no disco depois de sair", emptyList<String>(), arquivosDe(raizVisoes))
        assertEquals("o roster ficou no disco depois de sair", emptyList<String>(), arquivosDe(raizRosters))
        assertEquals(
            "sair apagou correcao que ainda nao subiu",
            1,
            pendentes.quantosPendentes(escola.id),
        )
    }

    /**
     * A base do outbox continua existindo em disco depois de sair.
     *
     * Contar pelo `Dao` prova que a linha esta legivel; olhar o **arquivo** prova que ela esta onde
     * ela deveria estar. As duas coisas podem divergir — uma base recriada vazia responderia zero
     * sem erro nenhum, e o arquivo continuaria la.
     */
    @Test
    fun a_base_do_outbox_continua_em_disco_depois_de_sair() {
        montarOEstadoLocal(escola.id)
        montarSessao(escola.id)

        sessao.sair()

        val arquivo = context.getDatabasePath(nomeDaBase)
        assertTrue("a base do outbox sumiu do disco: ${arquivo.absolutePath}", arquivo.exists())
        assertTrue("a base do outbox ficou vazia", arquivo.length() > 0)
    }

    @Test
    fun sair_diz_quantas_correcoes_ficaram() {
        montarOEstadoLocal(escola.id)
        pendentes.guardar(umPendente("cap-2", escola.id))
        montarSessao(escola.id)

        sessao.sair()

        assertEquals(
            2,
            (sessao.state as com.platos.android.session.DeviceState.Entrada).pendentes,
        )
    }

    // ------------------------------------------------------------------ 7.4

    /**
     * A revogacao apaga a referencia do disco e preserva o pendente, **sem o usuario sair**.
     *
     * O usuario nao chama `sair()` em momento nenhum aqui, e isso e parte da assercao: um teste que
     * o chamasse passaria mesmo com o apagamento existindo so no outro caminho.
     */
    @Test
    fun revogacao_apaga_referencia_do_disco_e_preserva_o_pendente() {
        montarOEstadoLocal(escola.id)
        montarSessao(escola.id)
        sessao.abrir(temSessaoGuardada = true)

        assertEquals(1, arquivosDe(raizRosters).size)

        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(pessoal)))

        assertEquals("o pacote sobreviveu a revogacao", emptyList<String>(), arquivosDe(raizPacotes))
        assertEquals("a visao sobreviveu a revogacao", emptyList<String>(), arquivosDe(raizVisoes))
        assertEquals("o roster sobreviveu a revogacao", emptyList<String>(), arquivosDe(raizRosters))
        assertEquals(
            "a revogacao apagou correcao que ainda nao subiu",
            1,
            pendentes.quantosPendentes(escola.id),
        )
    }

    /** O pendente de outra organizacao nao e tocado por nenhum dos dois caminhos. */
    @Test
    fun o_pendente_de_outra_organizacao_nao_e_tocado() {
        montarOEstadoLocal(escola.id)
        pendentes.guardar(umPendente("cap-de-outra", pessoal.id))
        montarSessao(escola.id)

        sessao.sair()

        assertEquals(1, pendentes.quantosPendentes(escola.id))
        assertEquals(1, pendentes.quantosPendentes(pessoal.id))
        assertFalse(
            "o pendente da outra organizacao foi enviado junto na conta de quem saiu",
            (sessao.state as com.platos.android.session.DeviceState.Entrada).pendentes == 2,
        )
    }
}
