package com.platos.android.probe

import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.android.outbox.BaseDoOutbox
import com.platos.android.outbox.ResultadoPendente
import com.platos.android.outbox.ResultadosEmRoom
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.scoring.ObjectiveScore
import com.platos.domain.scoring.QuestionOutcome
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * PROBE — nao e teste: nao afirma nada, so exercita e relata.
 *
 * **O que ele mede.** A mutacao da 5.A provou que a guarda da instancia unica segura o que diz
 * segurar. Ela **nao** provou a razao pela qual a instancia unica importa: o achado 3.2 afirma que o
 * acumulo de instancias abertas sobre o mesmo arquivo e de onde nasce
 * `SQLiteDatabaseLockedException`, e isso era **argumento, nao medicao** — tanto que o cenario de
 * convivencia com **duas** instancias nao caiu sob a mutacao, porque duas conexoes benignas
 * convivem.
 *
 * Este arquivo fecha a lacuna: **duas condicoes, a mesma carga, e a unica diferenca e a topologia.**
 * A condicao nova e o controle — sem ela, uma falha na antiga poderia ser da carga.
 *
 * **Por que probe e nao teste, e a razao e a mesma que este projeto acabou de registrar contra si
 * mesmo.** O desfecho depende de **carga e de aparelho**: a 24x60 nenhuma das duas condicoes falha;
 * a 64x150 a antiga falha e a nova nao. Um cenario assim na suite de sempre passa a ficar vermelho
 * por razao que ninguem le, e vermelho ilegivel vira vermelho ignorado — que e exatamente o item que
 * a ETAPA 5 registrou no §16 sobre a afirmacao de seguranca da credencial. O precedente de forma e
 * `SupabaseFailureProbe`, no mesmo pacote.
 *
 * **Como rodar** (ele e pulado sem o argumento):
 * ```
 * adb shell am instrument -w -e acumulo sim \
 *   -e class com.platos.android.probe.AcumuloDeInstanciasProbe \
 *   com.platos.android.test/androidx.test.runner.AndroidJUnitRunner
 * adb logcat -d -s PROBE-ACUMULO
 * ```
 *
 * **O que ele produziu**, e esta e a medicao que a cobertura cita: aparelho **2511FPC34G / Android
 * 16**, 2026-09-19T07:40Z-07:46Z, **sete execucoes**. A topologia antiga estourou
 * `SQLiteDatabaseLockedException (code 5 SQLITE_BUSY)` em **todas as sete**, 1 a 5 ocorrencias por
 * execucao. A nova, sob a mesma carga, **nunca**.
 *
 * **E o numero que importa nao e a excecao: e o que ela leva junto.** Nas tres execucoes que
 * contaram linhas, a topologia antiga gravou **9340, 9297 e 9155 de 9600** — entre **260 e 445
 * pendentes perdidos**, 2,7% a 4,6%. O fio que estoura aborta as escritas que faltavam, e cada uma
 * delas e, pela decisao registrada em `ResultadoPendente`, **o unico exemplar de uma correcao ja
 * feita**. A nova gravou **9600 de 9600** nas tres.
 *
 * **A topologia antiga tambem parecia mais rapida** — ~10s contra ~18,8s — e era, porque desistia:
 * o tempo menor e o das escritas que nao aconteceram.
 */
@RunWith(AndroidJUnit4::class)
class AcumuloDeInstanciasProbe {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val args = InstrumentationRegistry.getArguments()

    private val organizacao = "01a06ba4-cb43-7d97-842d-165352d010b5"

    /** Uma por "rotacao de tela", mais as passadas do worker. */
    private val instancias = 64

    /** Escritas por instancia. Abaixo de ~64x150 nenhuma das duas condicoes falha neste aparelho. */
    private val escritasPorInstancia = 150

    private val abertas = mutableListOf<BaseDoOutbox>()

    @Before
    fun preparar() {
        // Pulado por padrao, como `SupabaseFailureProbe`: ele nao pertence a suite de sempre.
        assumeTrue("probe de acumulo: passe `-e acumulo sim`", args.getString("acumulo") == "sim")
        ResultadosEmRoom.reiniciarParaTeste()
        context.deleteDatabase(NOME)
    }

    @After
    fun limpar() {
        synchronized(abertas) {
            abertas.forEach { runCatching { it.close() } }
            abertas.clear()
        }
        ResultadosEmRoom.reiniciarParaTeste()
        context.deleteDatabase(NOME)
    }

    /**
     * **A topologia antiga**: uma instancia nova por chamada, nenhuma fechada, todas escrevendo ao
     * mesmo tempo. E o que `ScanActivity.onCreate`, `SessaoActivity.onCreate` e `passadaDeEnvio`
     * faziam sobre o mesmo `outbox.db`.
     */
    @Test
    fun topologia_antiga_sob_escrita_concorrente() {
        relatar("ANTIGA", rodarCarga { Room.databaseBuilder(context, BaseDoOutbox::class.java, NOME).build() })
        // **O numero que importa nao e a excecao: e o que ela leva junto.** O fio que estoura aborta
        // as escritas que faltavam, e cada escrita perdida e um pendente — que e, pela decisao
        // registrada em `ResultadoPendente`, o unico exemplar de uma correcao ja feita.
        val gravadas = ResultadosEmRoom(
            Room.databaseBuilder(context, BaseDoOutbox::class.java, NOME).build().pendentes(),
        ).quantosPendentes(organizacao)
        Log.i(TAG, "ANTIGA linhas gravadas=$gravadas de ${instancias * escritasPorInstancia}")
    }

    /** **A topologia nova**, sob a mesma carga: uma instancia so. E o controle do experimento. */
    @Test
    fun topologia_nova_sob_a_mesma_carga() {
        relatar("NOVA", rodarCarga { ResultadosEmRoom.abrir(context) })
        val gravadas = ResultadosEmRoom(ResultadosEmRoom.abrir(context).pendentes())
            .quantosPendentes(organizacao)
        Log.i(TAG, "NOVA linhas gravadas=$gravadas de ${instancias * escritasPorInstancia}")
    }

    private var ultimoTempoMs = 0L

    private fun rodarCarga(obterBase: () -> BaseDoOutbox): List<Throwable> {
        val comecou = System.currentTimeMillis()
        val partida = CountDownLatch(1)
        val terminaram = CountDownLatch(instancias)
        val falhas = mutableListOf<Throwable>()

        repeat(instancias) { indice ->
            Thread {
                try {
                    val base = obterBase()
                    synchronized(abertas) { abertas += base }
                    val guarda = ResultadosEmRoom(base.pendentes())
                    partida.await(10, TimeUnit.SECONDS)
                    repeat(escritasPorInstancia) { n -> guarda.guardar(umResultado("cap-$indice-$n")) }
                } catch (erro: Throwable) {
                    synchronized(falhas) { falhas += erro }
                } finally {
                    terminaram.countDown()
                }
            }.start()
        }

        partida.countDown()
        val terminou = terminaram.await(180, TimeUnit.SECONDS)
        ultimoTempoMs = System.currentTimeMillis() - comecou
        synchronized(falhas) {
            if (!terminou) falhas += IllegalStateException("a carga nao terminou em 180s")
            return falhas.toList()
        }
    }

    private fun relatar(condicao: String, falhas: List<Throwable>) {
        Log.i(
            TAG,
            "$condicao carga=${instancias}x$escritasPorInstancia tempo=${ultimoTempoMs}ms " +
                "falhas=${falhas.size}",
        )
        falhas.groupingBy { it::class.java.name + " | " + (it.message ?: "").take(160) }
            .eachCount()
            .forEach { (tipo, quantas) -> Log.i(TAG, "$condicao   ${quantas}x $tipo") }
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

    private companion object {
        const val NOME = "outbox.db"
        const val TAG = "PROBE-ACUMULO"
    }
}
