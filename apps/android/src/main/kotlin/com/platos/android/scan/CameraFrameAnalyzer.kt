package com.platos.android.scan

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.platos.android.vision.FrameOutcome
import com.platos.android.vision.SheetReader
import com.platos.domain.capture.OmrThreshold
import com.platos.domain.layout.LayoutMap

/**
 * A ponte entre o CameraX e o pipeline: converte o quadro, chama a analise, entrega o resultado.
 *
 * **Nao decide nada.** A classificacao do quadro e de [SheetReader.analyze], e o que fazer com ela e
 * de [ScanSession]. O que mora aqui e cadencia e conversao — as duas coisas que so existem porque
 * ha uma camera do outro lado.
 *
 * [deveAnalisar] existe porque analisar quadro depois de a leitura fechar e trabalho jogado fora, e
 * trabalho jogado fora aqui esquenta o aparelho: cada quadro custa deteccao de ArUco, homografia,
 * retificacao e decodificacao. Retomar e acao de quem segura o aparelho, e nao algo que a sessao
 * faca sozinha — avanco automatico e da fatia 3d.
 *
 * [entrega] e chamada na **thread de analise**, e nao na principal. Quem recebe leva o resultado
 * para onde o estado vive; a sessao nao e thread-safe e nao precisa ser.
 */
class CameraFrameAnalyzer(
    private val map: LayoutMap,
    private val threshold: OmrThreshold,
    private val deveAnalisar: () -> Boolean,
    private val entrega: (FrameOutcome) -> Unit,
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        image.use {
            if (!deveAnalisar()) return

            val gray = FrameGray.of(it)
            try {
                entrega(SheetReader.analyze(gray, map, threshold))
            } finally {
                gray.release()
            }
        }
    }
}
