package com.platos.android.scan

import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import org.opencv.core.CvType
import org.opencv.core.Mat

/**
 * De quadro da camera para `Mat` em cinza, e nada alem disso.
 *
 * Sem decisao nenhuma: e o unico pedaco do encanamento do CameraX que da para verificar sem
 * aparelho apontado para papel, e ele so vale se nao decidir coisa alguma. Quem decide e o
 * `SheetReader`, sobre o `Mat` que sai daqui.
 *
 * **O plano Y ja e a imagem em cinza.** Em `YUV_420_888` a luminancia vem separada da crominancia,
 * e o `pixelStride` do plano Y e garantidamente 1 pelo Android. Converter RGB para cinza depois
 * seria refazer, com perda, o que a camera ja entregou pronto.
 *
 * **O `rowStride` e a armadilha, e por isso ele e parametro.** A camera alinha cada linha a um
 * multiplo que nao precisa ser a largura, e ler o buffer como se fosse continuo produz uma imagem
 * **cisalhada** — cada linha deslocada um pouco mais que a anterior. Ela continua parecendo uma
 * folha; o que ela deixa de ter e geometria. Nenhum tipo acusaria, e o ArUco simplesmente pararia
 * de ser achado, ou pior: seria achado torto.
 *
 * **Rotacao nao e aplicada, e isso e deliberado.** A geometria do OMR nao sai da orientacao da
 * imagem: `RegionDetector` acha os quatro marcadores por identificador e monta a homografia a
 * partir deles, entao a folha de cabeca para baixo produz a mesma regiao retificada. Girar o
 * quadro custaria uma copia por quadro para nao mudar resultado nenhum.
 */
object FrameGray {

    /** O plano de luminancia do quadro, sem copia intermediaria alem da que o `Mat` exige. */
    fun of(image: ImageProxy): Mat {
        val y = image.planes[0]
        return of(y.buffer, y.rowStride, image.width, image.height)
    }

    /**
     * A conversao propriamente dita, dirigivel por buffer conhecido.
     *
     * @param y luminancia, uma linha a cada [rowStride] bytes.
     */
    fun of(y: ByteBuffer, rowStride: Int, width: Int, height: Int): Mat {
        require(width > 0 && height > 0) { "quadro vazio: ${width}x$height" }
        require(rowStride >= width) { "rowStride $rowStride menor que a largura $width" }

        val fonte = y.duplicate()
        val destino = Mat(height, width, CvType.CV_8UC1)

        if (rowStride == width) {
            val bytes = ByteArray(width * height)
            fonte.position(0)
            fonte.get(bytes)
            destino.put(0, 0, bytes)
            return destino
        }

        val linha = ByteArray(width)
        for (l in 0 until height) {
            fonte.position(l * rowStride)
            fonte.get(linha, 0, width)
            destino.put(l, 0, linha)
        }
        return destino
    }
}
