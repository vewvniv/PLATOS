package com.platos.android.vision

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * A pagina de uma foto tirada de canto, moderada: os quatro cantos da pagina vao para dentro de 1 a 4%.
 *
 * **Fixada antes de ver qualquer resultado** (`slice-5c-0-o-recorte-da-resposta`), e compartilhada
 * pelos testes do segundo ajuste e do recorte para os dois medirem a mesma perspectiva. Nao e papel:
 * e o documento renderizado deformado por uma homografia conhecida.
 */
object FolhaEmAngulo {

    fun de(pagina: Mat): Mat {
        val w = pagina.cols().toDouble()
        val h = pagina.rows().toDouble()
        val origem = MatOfPoint2f(Point(0.0, 0.0), Point(w, 0.0), Point(w, h), Point(0.0, h))
        val destino = MatOfPoint2f(
            Point(w * 0.03, h * 0.01), Point(w * 0.98, h * 0.04),
            Point(w * 0.96, h * 0.99), Point(w * 0.01, h * 0.96),
        )
        val saida = Mat()
        Imgproc.warpPerspective(
            pagina, saida, Imgproc.getPerspectiveTransform(origem, destino), Size(w, h), Imgproc.INTER_LINEAR,
            Core.BORDER_CONSTANT, Scalar(255.0),
        )
        return saida
    }
}
