package com.platos.android.scan

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A leitura do caderno, chamada de onde `ScanActivity.onCreate` a chama: o fio principal
 * (`o-caderno-e-lido-fora-do-fio-principal`, tarefas 1.2 e 2.1).
 *
 * `GuardarCadernoNoFioPrincipalInstrumentedTest` cobre a **escrita** pelo fio principal, e este cobre
 * a **leitura**. A versao anterior deste arquivo chamava `CadernosEmRoom.ler` direto de
 * `runOnMainSync` e **falhou, 2 de 2 em cada aparelho**, com `IllegalStateException: Cannot access
 * database on the main thread` (emulador `platos-atd34`, 2026-09-30T20:05:28; aparelho
 * `2511FPC34G`, 20:05:37) — era a medicao do defeito. Chamar `ler` do fio principal **continua**
 * estourando, por desenho: o que estes testes afirmam e que [lerCadernoEmAndamento], a funcao que a
 * producao usa, nao estoura.
 *
 * **A base e aberta por [CadernosEmRoom.abrir], a mesma funcao que a `Activity` usa**, e sem
 * `allowMainThreadQueries`. A excecao, se houver, e devolvida como valor: uma excecao solta no fio
 * principal derrubaria o processo do instrumento e o relatorio nao diria qual teste a causou.
 *
 * Este teste nao prova que a `Activity` abre: isso e de [AbrirEscaneamentoComCadernoInstrumentedTest],
 * a camada de cima (P16).
 */
@RunWith(AndroidJUnit4::class)
class LerCadernoNoFioPrincipalInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val nomeDaBase = "caderno.db"
    private val organizacao = "01a06ba4-cb43-7d97-842d-165352d010b5"
    private val examId = "prova-referencia-slice-1"

    private lateinit var escopo: CoroutineScope

    @Before
    fun preparar() {
        CadernosEmRoom.reiniciarParaTeste()
        context.deleteDatabase(nomeDaBase)
        escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    @After
    fun limpar() {
        escopo.cancel()
        CadernosEmRoom.reiniciarParaTeste()
        context.deleteDatabase(nomeDaBase)
    }

    /** Chama a funcao de producao **do fio principal**, e espera o resultado fora dele. */
    private fun lerDoFioPrincipal(): Result<Caderno?> {
        var lendo: Deferred<Caderno?>? = null
        var falhaAoPedir: Throwable? = null
        instrumentation.runOnMainSync {
            try {
                val guarda = CadernosEmRoom(CadernosEmRoom.abrir(context).cadernos())
                lendo = escopo.lerCadernoEmAndamento(guarda, organizacao, examId)
            } catch (e: Throwable) {
                falhaAoPedir = e
            }
        }
        falhaAoPedir?.let { return Result.failure(it) }
        return runCatching { runBlocking { requireNotNull(lendo).await() } }
    }

    @Test
    fun ler_do_fio_principal_com_base_vazia_nao_estoura() {
        val resultado = lerDoFioPrincipal()

        assertNull("a leitura estourou: ${resultado.exceptionOrNull()}", resultado.exceptionOrNull())
        assertNull(resultado.getOrNull())
    }

    @Test
    fun ler_do_fio_principal_com_caderno_guardado_nao_estoura_e_devolve_o_caderno() {
        val original = Caderno(
            aluno = "tok-a",
            regioes = listOf(
                RegiaoDoCaderno(0, gabarito = true, rotulo = Caderno.ROTULO_GABARITO, estado = EstadoDaRegiao.Capturada),
            ),
            parcial = null,
        )
        // A escrita e feita fora do fio principal (a thread do instrumento), como a producao a faz.
        CadernosEmRoom(CadernosEmRoom.abrir(context).cadernos()).guardar(organizacao, examId, original)
        CadernosEmRoom.reiniciarParaTeste()

        val resultado = lerDoFioPrincipal()

        assertNull("a leitura estourou: ${resultado.exceptionOrNull()}", resultado.exceptionOrNull())
        assertEquals(original, resultado.getOrNull())
    }

    /**
     * Escopo cancelado no meio da leitura: quem espera recebe `CancellationException`, e nao um
     * resultado nem outra excecao. E o que impede `montar` de rodar sobre uma `Activity` destruida.
     * A leitura fica presa numa trava para o cancelamento acontecer **durante** ela, e nao antes.
     */
    @Test
    fun escopo_cancelado_no_meio_da_leitura_cancela_a_espera() {
        val entrou = CountDownLatch(1)
        val solta = CountDownLatch(1)
        val presa = object : CadernosGuardados {
            override fun guardar(organizacao: String, examId: String, caderno: Caderno) = Unit
            override fun ler(organizacao: String, examId: String): Caderno? {
                entrou.countDown()
                solta.await(10, TimeUnit.SECONDS)
                return null
            }
        }

        val lendo = escopo.lerCadernoEmAndamento(presa, organizacao, examId)
        assertTrue("a leitura nao comecou", entrou.await(10, TimeUnit.SECONDS))

        escopo.cancel()
        solta.countDown()

        val resultado = runCatching { runBlocking { lendo.await() } }
        assertTrue(
            "esperava CancellationException, veio ${resultado.exceptionOrNull()}",
            resultado.exceptionOrNull() is CancellationException,
        )
    }
}
