package com.platos.android.scan

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader

/**
 * A conversao de quadro, contra buffer construido.
 *
 * E o unico pedaco do encanamento do CameraX que da para verificar sem apontar a camera para papel,
 * e a razao de ele existir separado esta em `design.md`, decisao 1.
 *
 * O buffer nao e uniforme de proposito: uma imagem de valor unico passa por qualquer conversao,
 * inclusive pela que ignora o `rowStride`. O padrao daqui identifica **cada pixel pela posicao
 * dele**, entao um deslocamento de linha aparece como valor errado, e nao como imagem parecida.
 */
@RunWith(AndroidJUnit4::class)
class FrameGrayInstrumentedTest {

    @Before
    fun carregaOpenCv() {
        assertTrue("o OpenCV nativo nao carregou", OpenCVLoader.initLocal())
    }

    /** Valor de cada pixel: uma funcao da posicao, e nao uma constante. */
    private fun esperado(x: Int, y: Int) = ((x * 7 + y * 31) % 251).toByte()

    private fun buffer(width: Int, height: Int, rowStride: Int): ByteBuffer {
        val bytes = ByteArray(rowStride * height) { 0xEE.toByte() } // enchimento visivel
        for (y in 0 until height) {
            for (x in 0 until width) {
                bytes[y * rowStride + x] = esperado(x, y)
            }
        }
        return ByteBuffer.wrap(bytes)
    }

    private fun confere(width: Int, height: Int, rowStride: Int) {
        val mat = FrameGray.of(buffer(width, height, rowStride), rowStride, width, height)

        assertEquals("largura", width, mat.cols())
        assertEquals("altura", height, mat.rows())

        val lido = ByteArray(1)
        for (y in 0 until height) {
            for (x in 0 until width) {
                mat.get(y, x, lido)
                assertEquals(
                    "pixel ($x, $y) com rowStride $rowStride",
                    esperado(x, y).toInt() and 0xFF,
                    lido[0].toInt() and 0xFF,
                )
            }
        }
    }

    @Test
    fun linha_sem_enchimento_vira_a_mesma_imagem() {
        confere(width = 64, height = 48, rowStride = 64)
    }

    @Test
    fun linha_com_enchimento_nao_cisalha_a_imagem() {
        // O caso que a camera de verdade entrega, e o defeito que ele existe para pegar: lido como
        // continuo, cada linha sai deslocada 16 px a mais que a anterior. A imagem continua
        // parecendo uma folha, e a geometria some.
        confere(width = 64, height = 48, rowStride = 80)
    }

    @Test
    fun quadro_com_uma_linha_so_e_convertido() {
        confere(width = 32, height = 1, rowStride = 40)
    }

    @Test
    fun quadro_invalido_e_recusado_em_vez_de_produzir_imagem_torta() {
        val vazio = ByteBuffer.allocate(16)

        for (caso in listOf(
            Triple(0, 4, 4), // largura zero
            Triple(4, 0, 4), // altura zero
            Triple(8, 4, 4), // rowStride menor que a largura
        )) {
            val (w, h, stride) = caso
            val erro = runCatching { FrameGray.of(vazio, stride, w, h) }.exceptionOrNull()
            assertTrue("esperava recusa para ${w}x$h stride $stride, veio $erro", erro is IllegalArgumentException)
        }
    }
}
