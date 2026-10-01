package com.platos.android.scan

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.android.omr.RectifiedRegion
import com.platos.android.vision.PngDaResposta
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader

/**
 * A retomada do caderno, chamada de onde `ScanActivity.onCreate` a chama: o fio principal
 * (`slice-5c-1-a-resposta-fica-no-aparelho`, tarefa 3.1).
 *
 * Mesma forma de [LerCadernoNoFioPrincipalInstrumentedTest]: a base do Room e a que a producao abre, sem
 * `allowMainThreadQueries`, e a excecao volta como valor. A diferenca e a resposta: o caderno e guardado
 * com uma resposta, o **arquivo e apagado**, e a retomada devolve a regiao como nao vista **sem estourar**.
 */
@RunWith(AndroidJUnit4::class)
class RetomarCadernoNoFioPrincipalInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val organizacao = "01a06ba4-cb43-7d97-842d-165352d010b5"
    private val examId = "prova-referencia-slice-1"
    private val pasta = File(context.cacheDir, "retomar-${UUID.randomUUID()}")
    private val respostas = RespostasEmArquivo(pasta)

    private lateinit var escopo: CoroutineScope

    @Before
    fun preparar() {
        OpenCVLoader.initLocal()
        CadernosEmRoom.reiniciarParaTeste()
        context.deleteDatabase("caderno.db")
        escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    @After
    fun limpar() {
        escopo.cancel()
        CadernosEmRoom.reiniciarParaTeste()
        context.deleteDatabase("caderno.db")
        pasta.deleteRecursively()
    }

    private fun umaResposta(): RespostaGuardada {
        val regiao = RectifiedRegion(40, 30, ByteArray(40 * 30) { 0xFF.toByte() })
        return (respostas.gravar(PngDaResposta.codificar(regiao), 1L, false, 0) as RespostaDoQuadro.Guardada).resposta
    }

    private fun caderno(resposta: RespostaGuardada) = Caderno(
        aluno = "tok-a",
        regioes = listOf(
            RegiaoDoCaderno(0, gabarito = true, rotulo = Caderno.ROTULO_GABARITO, estado = EstadoDaRegiao.Capturada),
            RegiaoDoCaderno(1, gabarito = false, rotulo = "1", estado = EstadoDaRegiao.Capturada, resposta = resposta),
        ),
        parcial = null,
    )

    /** Chama a funcao de producao **do fio principal**, e espera o resultado fora dele. */
    private fun retomarDoFioPrincipal(): Result<Caderno?> {
        var lendo: Deferred<Caderno?>? = null
        var falhaAoPedir: Throwable? = null
        instrumentation.runOnMainSync {
            try {
                val guarda = CadernosEmRoom(CadernosEmRoom.abrir(context).cadernos())
                lendo = escopo.retomarCadernoEmAndamento(guarda, respostas, organizacao, examId)
            } catch (e: Throwable) {
                falhaAoPedir = e
            }
        }
        falhaAoPedir?.let { return Result.failure(it) }
        return runCatching { runBlocking { requireNotNull(lendo).await() } }
    }

    private fun guardar(caderno: Caderno) {
        CadernosEmRoom(CadernosEmRoom.abrir(context).cadernos()).guardar(organizacao, examId, caderno)
        CadernosEmRoom.reiniciarParaTeste()
    }

    @Test
    fun retomar_com_a_resposta_em_disco_devolve_o_caderno_inteiro() {
        val original = caderno(umaResposta())
        guardar(original)

        val resultado = retomarDoFioPrincipal()

        assertNull("a retomada estourou: ${resultado.exceptionOrNull()}", resultado.exceptionOrNull())
        assertEquals(original, resultado.getOrNull())
    }

    @Test
    fun retomar_depois_de_apagar_o_arquivo_devolve_a_regiao_nao_vista_e_nao_estoura() {
        val resposta = umaResposta()
        guardar(caderno(resposta))
        respostas.eliminar(resposta.arquivo)

        val resultado = retomarDoFioPrincipal()

        assertNull("a retomada estourou: ${resultado.exceptionOrNull()}", resultado.exceptionOrNull())
        val retomado = requireNotNull(resultado.getOrNull())
        assertEquals(EstadoDaRegiao.NaoVista, retomado.regioes.single { it.regionIndex == 1 }.estado)
        assertNull(retomado.regioes.single { it.regionIndex == 1 }.resposta)
        assertEquals(1, retomado.capturadas)
    }
}
