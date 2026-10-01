package com.platos.android.scan

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.android.session.SessaoActivity
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A varredura do prazo na porta de entrada do aplicativo, a `SessaoActivity`
 * (`slice-5c-1-a-resposta-fica-no-aparelho`, tarefa 4.2, "em dois pontos").
 *
 * A `SessaoActivity` **real** e aberta, sem sessao guardada (a tela de entrada), com respostas no disco: uma
 * vencida e referenciada por um caderno, uma orfa, um temporario de gravacao interrompida, e uma valida e
 * referenciada. O Room do caderno e aberto sem `allowMainThreadQueries`, entao uma consulta no fio principal
 * derrubaria o processo. O outro ponto — o escaneamento — e de [RespostaNaAtividadeInstrumentedTest].
 */
@RunWith(AndroidJUnit4::class)
class VarreduraNaAberturaInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val contexto = instrumentation.targetContext
    private val pasta = File(contexto.filesDir, "respostas")
    private val dia = 86_400_000L

    @Before
    fun preparar() {
        CadernosEmRoom.reiniciarParaTeste()
        contexto.deleteDatabase("caderno.db")
        pasta.deleteRecursively()
    }

    @After
    fun limpar() {
        CadernosEmRoom.reiniciarParaTeste()
        contexto.deleteDatabase("caderno.db")
        pasta.deleteRecursively()
    }

    private fun respostaDeHa(diasAtras: Long): RespostaGuardada {
        val gravada = RespostasEmArquivo(pasta).gravar(byteArrayOf(1, 2, 3), System.currentTimeMillis() - diasAtras * dia, false, 0)
        return (gravada as RespostaDoQuadro.Guardada).resposta
    }

    @Test
    fun abrir_o_aplicativo_elimina_a_vencida_a_orfa_e_o_temporario_e_mantem_a_valida() {
        val vencida = respostaDeHa(31)
        val valida = respostaDeHa(2)
        File(pasta, "orfao.png").writeBytes(byteArrayOf(9))
        File(pasta, "interrompida.png.tmp").writeBytes(byteArrayOf(9))
        val caderno = Caderno(
            aluno = "tok-a",
            regioes = listOf(
                RegiaoDoCaderno(1, gabarito = false, rotulo = "1", estado = EstadoDaRegiao.Capturada, resposta = vencida),
                RegiaoDoCaderno(2, gabarito = false, rotulo = "2", estado = EstadoDaRegiao.Capturada, resposta = valida),
            ),
            parcial = null,
        )
        // Outra organizacao e outra prova que a da sessao: o prazo nao depende de a sessao corrente ser a dela.
        CadernosEmRoom(CadernosEmRoom.abrir(contexto).cadernos()).guardar("outra-organizacao", "outra-prova", caderno)
        CadernosEmRoom.reiniciarParaTeste()
        assertEquals("guarda de vacuidade: as quatro estao no disco", 4, pasta.list()!!.size)

        val atividade = instrumentation.startActivitySync(
            Intent(contexto, SessaoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        try {
            val limite = System.currentTimeMillis() + 15_000
            while (System.currentTimeMillis() < limite && pasta.list()!!.size != 1) Thread.sleep(200)

            assertEquals(listOf(valida.arquivo), pasta.list()!!.sorted())
        } finally {
            instrumentation.runOnMainSync { atividade.finish() }
            val fim = System.currentTimeMillis() + 5_000
            while (!atividade.isDestroyed && System.currentTimeMillis() < fim) Thread.sleep(100)
            assertTrue("a Activity nao foi destruida", atividade.isDestroyed)
            Thread.sleep(500)
        }
    }
}
