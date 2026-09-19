package com.platos.android.outbox

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.scoring.ObjectiveScore
import com.platos.domain.scoring.QuestionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A topologia da producao, medida como ela e (achado 3.2).
 *
 * **Por que este arquivo existe.** `OutboxEmRepousoInstrumentedTest` e
 * `ApagamentoLocalInstrumentedTest` constroem a base com **nome proprio** e guardam a referencia num
 * campo — eles exercitam uma topologia de **uma instancia, um dono**, que nao e a da producao. Na
 * producao ha tres chamadores sobre o mesmo `outbox.db` e nenhum e dono de nada. Foi por isso que o
 * defeito atravessou: a suite inteira estava verde sobre um sistema que nao existe. E o sombreamento
 * de fixture que `rigorous.md` §3 descreve.
 *
 * **O que so este arquivo afirma:** que o caminho de producao — [ResultadosEmRoom.abrir] — devolve
 * **a mesma instancia**, e que os dois caminhos que a usam ao mesmo tempo na vida real convivem.
 */
@RunWith(AndroidJUnit4::class)
class InstanciaUnicaDoOutboxInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var escopo: CoroutineScope

    private val organizacao = "01a06ba4-cb43-7d97-842d-165352d010b5"

    @Before
    fun preparar() {
        ResultadosEmRoom.reiniciarParaTeste()
        context.deleteDatabase("outbox.db")
        escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun limpar() {
        escopo.cancel()
        ResultadosEmRoom.reiniciarParaTeste()
        context.deleteDatabase("outbox.db")
    }

    /**
     * Duas aberturas, uma instancia.
     *
     * **A assercao e sobre identidade de referencia, e nao sobre o dado — e a diferenca e o teste
     * inteiro.** Duas instancias distintas sobre o mesmo arquivo leem a mesma linha: qualquer
     * assercao sobre conteudo passaria com o defeito presente, em verde, para sempre. `assertSame` e
     * a unica forma de perguntar o que se quer saber.
     */
    @Test
    fun duas_aberturas_devolvem_a_mesma_instancia() {
        val primeira = ResultadosEmRoom.abrir(context)
        val segunda = ResultadosEmRoom.abrir(context)

        assertSame(
            "cada chamada a abrir devolveu uma instancia diferente: sao duas conexoes vivas sobre " +
                "o mesmo arquivo, e e dai que vem SQLiteDatabaseLockedException",
            primeira,
            segunda,
        )
    }

    /**
     * A escrita do worker e a leitura da tela, ao mesmo tempo, pelos caminhos de producao.
     *
     * **A concorrencia e real e nao encenada.** O worker roda **quando ha rede**, inclusive com a
     * camera aberta: as duas coisas acontecem juntas no aparelho de quem corrige. O `CountDownLatch`
     * existe para que as duas de fato se sobreponham, em vez de rodarem em sequencia e passarem sem
     * medir nada.
     *
     * Os dois lados obtem a base por [ResultadosEmRoom.abrir] — nenhum constroi a sua — e fora do
     * fio principal, que e onde os dois caminhos de producao a tocam: `gravarEAgendar` usa
     * `Dispatchers.IO`, e `passadaDeEnvio` roda no fio do `WorkManager`.
     *
     * **Este cenario NAO e guarda da instancia unica, e isso foi medido.** O plano previa que ele
     * caisse sob a mutacao que restaura o `build()` por chamada; ele **nao caiu**
     * (2026-09-19T00:12:09Z, aparelho 2511FPC34G). Vinte escritas e vinte leituras por duas
     * instancias distintas sobre o mesmo arquivo convivem sem estourar: o SQLite do aparelho
     * aguenta duas conexoes benignas, e a contencao que produz `SQLiteDatabaseLockedException` na
     * vida real vem de **acumulo** de instancias sob rede intermitente com a camera aberta, que este
     * teste nao reproduz.
     *
     * Ele continua valendo pelo que de fato afirma — que os dois caminhos de producao nao se
     * bloqueiam —, e **a guarda da unicidade e so** `duas_aberturas_devolvem_a_mesma_instancia`.
     * Fica dito aqui para que ninguem leia este cenario como prova do que ele nao prova (P7, P12).
     */
    @Test
    fun escrita_do_worker_e_leitura_da_tela_convivem() {
        val partida = CountDownLatch(1)
        val terminaram = CountDownLatch(2)
        val falhas = mutableListOf<Throwable>()
        var lidos = -1

        // O caminho do worker: abre por `abrir` e escreve.
        Thread {
            try {
                partida.await(5, TimeUnit.SECONDS)
                val pelaFila = ResultadosEmRoom(ResultadosEmRoom.abrir(context).pendentes())
                repeat(20) { pelaFila.guardar(umResultado("cap-worker-$it")) }
            } catch (erro: Throwable) {
                synchronized(falhas) { falhas += erro }
            } finally {
                terminaram.countDown()
            }
        }.start()

        // O caminho da tela: abre por `abrir` e le, enquanto o outro escreve.
        Thread {
            try {
                partida.await(5, TimeUnit.SECONDS)
                val pelaTela = ResultadosEmRoom(ResultadosEmRoom.abrir(context).pendentes())
                repeat(20) { lidos = pelaTela.quantosPendentes(organizacao) }
            } catch (erro: Throwable) {
                synchronized(falhas) { falhas += erro }
            } finally {
                terminaram.countDown()
            }
        }.start()

        partida.countDown()
        assertTrue(
            "os dois caminhos nao terminaram no tempo: houve bloqueio entre eles",
            terminaram.await(30, TimeUnit.SECONDS),
        )
        assertEquals("os caminhos se atropelaram: $falhas", emptyList<Throwable>(), falhas)

        // E o dado esta la, lido por fora dos dois fios que acabaram de brigar por ele.
        val depois = ResultadosEmRoom(ResultadosEmRoom.abrir(context).pendentes())
        assertEquals(20, depois.quantosPendentes(organizacao))
        assertTrue("a leitura concorrente nunca chegou a rodar", lidos >= 0)
    }

    /**
     * O pendente gravado por um caminho e visto pelo outro **sem reabrir nada**.
     *
     * Com duas instancias, cada uma com a propria conexao, isto tambem passaria — e por isso a
     * assercao de identidade acima e a que carrega o peso. Esta existe para o caso oposto: se um dia
     * a instancia unica for trocada por um cache que devolve instancias por fio, o dado deixa de
     * atravessar e este cenario acusa.
     */
    @Test
    fun o_que_um_caminho_grava_o_outro_le() = runBlocking {
        val pelaTela = ResultadosEmRoom(ResultadosEmRoom.abrir(context).pendentes())
        val job = escopo.gravarEAgendar(pelaTela, umResultado("cap-atravessa")) {}
        job.join()

        val pelaFila = ResultadosEmRoom(ResultadosEmRoom.abrir(context).pendentes())
        val pendentes = pelaFila.pendentesDa(organizacao)

        assertEquals(1, pendentes.size)
        assertEquals("cap-atravessa", pendentes.single().captureId)
    }

    private fun umResultado(captureId: String) = ResultadoPendente(
        captureId = captureId,
        organizacao = organizacao,
        prova = "prova-referencia-slice-1",
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
}
