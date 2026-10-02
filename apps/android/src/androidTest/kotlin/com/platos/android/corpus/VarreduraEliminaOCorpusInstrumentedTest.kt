package com.platos.android.corpus

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.android.scan.CadernosEmRoom
import com.platos.android.scan.varrerAgora
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A varredura da porta de entrada elimina as amostras vencidas do corpus, sem o interruptor ligado
 * (`slice-5d-corpus-de-medicao`). Atravessa `varrerAgora` e o disco de verdade; a regra do prazo ja e provada em JVM.
 */
@RunWith(AndroidJUnit4::class)
class VarreduraEliminaOCorpusInstrumentedTest {

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private val pasta = ColetaDoCorpusEmArquivo.diretorioDe(contexto.filesDir)
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

    private fun amostra(dadosHaDias: Long): String {
        pasta.mkdirs()
        val id = UUID.randomUUID().toString()
        File(pasta, "$id.png").writeBytes(byteArrayOf(1, 2, 3))
        File(pasta, "$id.json").also {
            it.writeText("{}")
            assertTrue(it.setLastModified(System.currentTimeMillis() - dadosHaDias * dia))
        }
        return id
    }

    @Test
    fun a_varredura_elimina_a_amostra_vencida_e_mantem_a_recente() {
        val vencida = amostra(dadosHaDias = 31)
        val recente = amostra(dadosHaDias = 1)
        assertFalse(
            "o interruptor esta ligado e o teste nao diria nada",
            File(contexto.filesDir, ColetaDoCorpusEmArquivo.MARCADOR).isFile,
        )

        varrerAgora(contexto)

        assertFalse(File(pasta, "$vencida.png").exists() || File(pasta, "$vencida.json").exists())
        assertTrue(File(pasta, "$recente.png").isFile && File(pasta, "$recente.json").isFile)
    }
}
