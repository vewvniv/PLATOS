package com.platos.android.scan

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.platos.android.vision.PngDaResposta
import com.platos.android.vision.RecorteOutcome
import com.platos.android.vision.RegiaoDiscursivaNoQuadro
import com.platos.android.vision.SheetReader
import com.platos.domain.capture.OmrThreshold
import com.platos.domain.layout.LayoutMap
import org.opencv.core.Mat

/**
 * A ponte entre o CameraX e o pipeline: converte o quadro, chama a analise, grava a resposta das
 * regioes discursivas que faltam e entrega o resultado.
 *
 * **Nao decide o que a tela mostra.** A classificacao do quadro e de [SheetReader.analyze], e o que fazer
 * com ela e de [ScanSession]. O que mora aqui e cadencia, conversao e o unico ponto em que o quadro e a
 * regra "falta resposta" existem ao mesmo tempo (`slice-5c-1-a-resposta-fica-no-aparelho`, design,
 * decisao 1): o quadro morre no `finally` de [analyze], e quem corrige a discursiva depois nao o tera.
 *
 * [deveAnalisar] existe porque analisar quadro depois de a leitura fechar e trabalho jogado fora, e
 * trabalho jogado fora aqui esquenta o aparelho: cada quadro custa deteccao de ArUco, homografia,
 * retificacao e decodificacao. Retomar e acao de quem segura o aparelho, e nao algo que a sessao
 * faca sozinha — avanco automatico e da fatia 3d.
 *
 * [jaTemResposta] responde **por aluno e por regiao**: o caderno corrente e do aluno A com a regiao 2
 * guardada, e a folha do aluno B mostra a regiao 2 — pede-se o recorte. Sem o aluno, a folha de B herdaria
 * a imagem de A. E chamado na thread de analise, e quem o passa le um instantaneo imutavel.
 *
 * [relogio] e injetado, como o `captureId` e cunhado por quem chama: em producao e
 * `System.currentTimeMillis`, e e dele que o prazo de 30 dias conta.
 *
 * [entrega] e chamada na **thread de analise**, e nao na principal. Quem recebe leva o resultado
 * para onde o estado vive; a sessao nao e thread-safe e nao precisa ser.
 */
class CameraFrameAnalyzer(
    private val map: LayoutMap,
    private val threshold: OmrThreshold,
    private val deveAnalisar: () -> Boolean,
    private val jaTemResposta: (aluno: String, regionIndex: Int) -> Boolean,
    private val respostas: RespostasGuardadas,
    private val relogio: () -> Long,
    private val entrega: (QuadroAnalisado) -> Unit,
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        image.use {
            if (!deveAnalisar()) return

            val gray = FrameGray.of(it)
            try {
                entrega(analisar(gray))
            } finally {
                gray.release()
            }
        }
    }

    /**
     * Analisa o quadro e grava a resposta de cada regiao discursiva **reconhecida** que ainda nao tem
     * resposta para o aluno do QR dela. [SheetReader.analyze] continua sem efeito e sem imagem: o pedido e
     * a gravacao sao daqui, **depois** da analise, e o `FrameOutcome` e o mesmo que ela devolveria sozinha.
     */
    internal fun analisar(gray: Mat): QuadroAnalisado {
        val resultado = SheetReader.analyze(gray, map, threshold)
        val gravadas = mutableMapOf<Int, RespostaDoQuadro>()
        for (regiao in resultado.discursivas.filterIsInstance<RegiaoDiscursivaNoQuadro.Reconhecida>()) {
            if (jaTemResposta(regiao.payload.studentToken, regiao.regionIndex)) continue
            gravadas[regiao.regionIndex] = recortarEGravar(gray, regiao.regionIndex)
        }
        return QuadroAnalisado(resultado, gravadas)
    }

    /**
     * O recorte, o PNG e o arquivo. **Nunca lanca**: excecao do OpenCV ou do codificador vira recusa com o
     * motivo, e e a sessao quem deixa a regiao "com problema" — o laco da camera nao cai por causa de uma
     * resposta.
     */
    private fun recortarEGravar(gray: Mat, regionIndex: Int): RespostaDoQuadro = try {
        val regiao = map.regions.single { it.index == regionIndex }
        when (val recorte = SheetReader.recortar(gray, map, regiao)) {
            is RecorteOutcome.Recusado -> RespostaDoQuadro.Recusada(recorte.motivo)
            is RecorteOutcome.Recortado -> respostas.gravar(
                png = PngDaResposta.codificar(recorte.resposta),
                capturadaEm = relogio(),
                desvioSinalizado = recorte.desvio.sinalizado,
                foraPpm = recorte.desvio.proporcaoForaPpm,
            )
        }
    } catch (e: Exception) {
        RespostaDoQuadro.Recusada("o recorte da resposta falhou: ${e.message ?: e.javaClass.simpleName}")
    }

    companion object {
        /**
         * O analisador **como a `ScanActivity` o monta**, e so por aqui.
         *
         * Existe para a montagem poder ser exercitada sem abrir a tela. Foi na montagem que o
         * aplicativo caia diante de uma prova com discursiva: a `ScanActivity` escolhia a regiao com
         * `map.regions.single()`, e a prova tem uma regiao por discursiva alem do gabarito
         * (`slice-5b-1-o-aparelho-reconhece-a-discursiva`). A regiao agora sai de cada quadro, e o
         * mapa entra inteiro; um teste chama esta funcao com o mapa da prova com discursiva.
         *
         * O limiar e o apurado na 3b (ADR-0011), e mora aqui porque e parte de como a sessao le.
         */
        fun daSessao(
            map: LayoutMap,
            deveAnalisar: () -> Boolean,
            jaTemResposta: (aluno: String, regionIndex: Int) -> Boolean,
            respostas: RespostasGuardadas,
            relogio: () -> Long = System::currentTimeMillis,
            entrega: (QuadroAnalisado) -> Unit,
        ): CameraFrameAnalyzer = CameraFrameAnalyzer(
            map = map,
            threshold = OmrThreshold.MEDIDO_NA_FATIA_3B,
            deveAnalisar = deveAnalisar,
            jaTemResposta = jaTemResposta,
            respostas = respostas,
            relogio = relogio,
            entrega = entrega,
        )
    }
}
