package com.platos.android.vision

import com.platos.android.omr.RectifiedRegion
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.imgcodecs.Imgcodecs

/**
 * O PNG, em tons de cinza, de uma regiao retificada (`slice-5c-1-a-resposta-fica-no-aparelho`, design,
 * decisao 2).
 *
 * **Sem perda**: o PNG decodificado tem os mesmos pixels da regiao, e o teste de ida e volta usa isso
 * como oraculo, com o decodificador do Android (outro codigo que nao este). JPEG fica de fora: a
 * imagem e o que a correcao discursiva vai ler, e comprimir manuscrito fino com perda e decisao de
 * acuracia que ninguem tomou.
 */
object PngDaResposta {

    fun codificar(regiao: RectifiedRegion): ByteArray {
        val mat = Mat(regiao.height, regiao.width, CvType.CV_8UC1)
        val saida = MatOfByte()
        try {
            mat.put(0, 0, regiao.paraBytes())
            check(Imgcodecs.imencode(".png", mat, saida)) {
                "o OpenCV nao codificou a regiao ${regiao.width}x${regiao.height} em PNG"
            }
            return saida.toArray()
        } finally {
            mat.release()
            saida.release()
        }
    }
}
