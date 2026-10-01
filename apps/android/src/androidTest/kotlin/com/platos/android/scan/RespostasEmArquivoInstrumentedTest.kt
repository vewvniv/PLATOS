package com.platos.android.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.android.omr.RectifiedRegion
import com.platos.android.vision.PngDaResposta
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.random.Random
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader

/**
 * A gravacao da resposta em arquivo (`slice-5c-1-a-resposta-fica-no-aparelho`, tarefa 2.1), no
 * aparelho: o codec PNG do OpenCV existe no artefato do app, a imagem volta bit a bit, e a gravacao e
 * completa ou inexistente.
 *
 * **O oraculo da ida e volta nao e o codificador.** O PNG sai de `Imgcodecs.imencode` (OpenCV) e volta por
 * `BitmapFactory` (Android): outro decodificador, que nao compartilha codigo com o que ele julga (P4).
 */
@RunWith(AndroidJUnit4::class)
class RespostasEmArquivoInstrumentedTest {

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private val diretorio = File(contexto.cacheDir, "respostas-teste-${UUID.randomUUID()}")

    @Before
    fun carregaOpenCv() {
        assertTrue("o OpenCV nativo nao carregou", OpenCVLoader.initLocal())
    }

    @After
    fun limpa() {
        diretorio.setWritable(true)
        diretorio.deleteRecursively()
    }

    /** Pixels pseudo-aleatorios de semente fixa: cobrem 0..255, e nenhuma linha e igual a outra. */
    private fun regiaoComRuido(largura: Int, altura: Int): RectifiedRegion {
        val pixels = ByteArray(largura * altura)
        Random(42).nextBytes(pixels)
        return RectifiedRegion(largura, altura, pixels)
    }

    private fun folhaEmBranco(largura: Int, altura: Int) =
        RectifiedRegion(largura, altura, ByteArray(largura * altura) { 0xFF.toByte() })

    /** Tinta sintetica: tracos pretos sobre papel claro e levemente irregular. Nao e letra. */
    private fun folhaComTinta(largura: Int, altura: Int): RectifiedRegion {
        val azar = Random(7)
        val pixels = ByteArray(largura * altura) { (245 + azar.nextInt(10)).toByte() }
        repeat(40) {
            val y = azar.nextInt(altura - 4)
            val x0 = azar.nextInt(largura / 2)
            val x1 = x0 + azar.nextInt(largura / 2)
            for (x in x0 until x1) for (dy in 0 until 3) pixels[(y + dy) * largura + x] = azar.nextInt(40).toByte()
        }
        return RectifiedRegion(largura, altura, pixels)
    }

    private fun gravar(regiao: RectifiedRegion, repositorio: RespostasEmArquivo = RespostasEmArquivo(diretorio)) =
        repositorio.gravar(PngDaResposta.codificar(regiao), capturadaEm = 1_000L, desvioSinalizado = false, foraPpm = 0)

    private fun guardada(r: RespostaDoQuadro): RespostaGuardada =
        (r as? RespostaDoQuadro.Guardada)?.resposta ?: throw AssertionError("esperava a resposta guardada, veio $r")

    @Test
    fun a_imagem_guardada_volta_bit_a_bit_pelo_decodificador_do_android() {
        val original = regiaoComRuido(870, 1000)
        val distintos = original.paraBytes().map { it.toInt() and 0xFF }.toSet().size
        assertTrue("guarda de vacuidade: o ruido precisa cobrir quase todos os tons ($distintos)", distintos > 250)

        val repositorio = RespostasEmArquivo(diretorio)
        val resposta = guardada(gravar(original, repositorio))
        val bytes = requireNotNull(repositorio.ler(resposta.arquivo)) { "o arquivo guardado nao foi lido" }
        val opcoes = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opcoes)) {
            "o BitmapFactory nao decodificou o PNG guardado"
        }

        assertEquals(original.width, bitmap.width)
        assertEquals(original.height, bitmap.height)
        val linha = IntArray(bitmap.width)
        var diferentes = 0
        var primeiro = ""
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(linha, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in 0 until bitmap.width) {
                val cor = linha[x]
                val cinza = cor and 0xFF
                val iguais = (cor shr 16 and 0xFF) == cinza && (cor shr 8 and 0xFF) == cinza &&
                    cinza == original.luminanceAt(x, y)
                if (!iguais) {
                    if (diferentes == 0) primeiro = "($x,$y): decodificado=${cor.toUInt().toString(16)}, original=${original.luminanceAt(x, y)}"
                    diferentes++
                }
            }
        }
        assertEquals("pixels diferentes depois da ida e volta; o primeiro: $primeiro", 0, diferentes)
    }

    /** Mede, e nao afirma: o numero vai para a cobertura (6.2) e o design registra o estimado como suposto. */
    @Test
    fun registra_o_tamanho_do_arquivo_com_folha_em_branco_e_com_tinta_sintetica() {
        val repositorio = RespostasEmArquivo(diretorio)
        val branco = guardada(gravar(folhaEmBranco(870, 1000), repositorio))
        val tinta = guardada(gravar(folhaComTinta(870, 1000), repositorio))

        val tamanhoBranco = requireNotNull(repositorio.ler(branco.arquivo)).size
        val tamanhoTinta = requireNotNull(repositorio.ler(tinta.arquivo)).size
        Log.i("RespostasEmArquivoTeste", "TAMANHO png 870x1000 (870000 bytes crus): branco=$tamanhoBranco tinta=$tamanhoTinta")
        assertTrue(tamanhoBranco > 0 && tamanhoTinta > 0)
    }

    @Test
    fun o_nome_nao_carrega_aluno_nem_prova_e_o_diretorio_so_tem_o_arquivo() {
        val repositorio = RespostasEmArquivo(diretorio)

        val resposta = guardada(gravar(regiaoComRuido(40, 30), repositorio))

        assertTrue(resposta.arquivo, Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png").matches(resposta.arquivo))
        assertEquals(listOf(resposta.arquivo), repositorio.listar())
        assertEquals(1_000L, resposta.capturadaEm)
    }

    /**
     * A falha **no meio** da escrita: o escritor grava metade e lanca, como o disco que enche. A
     * resposta e a recusa com o motivo, e nem o `.png` nem o `.tmp` ficam.
     */
    @Test
    fun escrita_que_falha_no_meio_devolve_recusada_e_nenhum_arquivo_fica() {
        val quebrado = RespostasEmArquivo(diretorio, escrever = { arquivo, bytes ->
            arquivo.writeBytes(bytes.copyOf(bytes.size / 2))
            throw IOException("sem espaco no dispositivo")
        })

        val resultado = gravar(regiaoComRuido(200, 100), quebrado)

        assertTrue("esperava a recusa, veio $resultado", resultado is RespostaDoQuadro.Recusada)
        assertTrue(
            (resultado as RespostaDoQuadro.Recusada).motivo,
            resultado.motivo.contains("sem espaco no dispositivo"),
        )
        assertEquals("ficou arquivo depois da falha: ${quebrado.listar()}", emptyList<String>(), quebrado.listar())
    }

    /** A mesma recusa pelo caminho real: diretorio somente leitura. */
    @Test
    fun diretorio_somente_leitura_devolve_recusada_e_nenhum_arquivo_fica() {
        assertTrue(diretorio.mkdirs())
        assertTrue("o aparelho nao deixou tornar o diretorio somente leitura", diretorio.setWritable(false))
        val repositorio = RespostasEmArquivo(diretorio)

        val resultado = gravar(regiaoComRuido(200, 100), repositorio)

        assertTrue("esperava a recusa, veio $resultado", resultado is RespostaDoQuadro.Recusada)
        assertEquals(emptyList<String>(), repositorio.listar())
    }

    @Test
    fun eliminar_remove_o_arquivo_e_eliminar_o_que_nao_existe_nao_falha() {
        val repositorio = RespostasEmArquivo(diretorio)
        val resposta = guardada(gravar(regiaoComRuido(40, 30), repositorio))
        assertTrue(repositorio.existe(resposta.arquivo))

        repositorio.eliminar(resposta.arquivo)
        repositorio.eliminar(resposta.arquivo)

        assertFalse(repositorio.existe(resposta.arquivo))
        assertNull(repositorio.ler(resposta.arquivo))
        assertEquals(emptyList<String>(), repositorio.listar())
    }

    @Test
    fun listar_inclui_o_temporario_de_uma_gravacao_interrompida() {
        assertTrue(diretorio.mkdirs())
        File(diretorio, "interrompida.png.tmp").writeBytes(byteArrayOf(1, 2, 3))
        val repositorio = RespostasEmArquivo(diretorio)
        val resposta = guardada(gravar(regiaoComRuido(40, 30), repositorio))

        assertEquals(listOf("interrompida.png.tmp", resposta.arquivo).sorted(), repositorio.listar())
        assertNotNull(repositorio.ler(resposta.arquivo))
    }
}
