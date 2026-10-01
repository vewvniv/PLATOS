package com.platos.android.scan

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.android.vision.FolhaDiscursivaRenderizada
import com.platos.android.vision.RegiaoDiscursivaNoQuadro
import com.platos.domain.exam.folhaDaAtribuicao
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * A medicao da tarefa 6.2 de `slice-5c-1-a-resposta-fica-no-aparelho`: quantas das regioes terminam
 * recusadas, com a **perspectiva** do documento renderizado, e quanto custa o analisador no quadro que recorta.
 *
 * **Os angulos foram fixados antes de qualquer resultado** (P11), e nao mudam depois dele: `FRONTAL` e as tres
 * perspectivas abaixo, cada uma com os quatro cantos da pagina levados para dentro por uma fracao da largura e da
 * altura. A segunda e a de [com.platos.android.vision.FolhaEmAngulo] (a da 5c-0); a terceira e a espelhada; a
 * quarta e mais forte, e e a unica fora da faixa de 1 a 4% que a 5c-0 chamou de "moderada".
 *
 * **O que isto e, e o que nao e (P6, P8).** E o documento renderizado e deformado por homografia conhecida: sem
 * papel, sem letra, sem sombra, sem foto de celular. Registra (`logcat -s Medida5c1`); as unicas asercoes sao
 * guardas de vacuidade (o instrumento recorta o que a 5c-0 ja sabia recortar). O numero que importa e o que
 * aparece no log.
 */
@RunWith(AndroidJUnit4::class)
class MedidaDoRecorteAoVivoInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val prova = FolhaDiscursivaRenderizada(instrumentation.context, instrumentation.targetContext)
    private val diretorio = File(instrumentation.targetContext.cacheDir, "medida-${UUID.randomUUID()}")

    @Before
    fun carregaOpenCv() {
        assertTrue("o OpenCV nativo nao carregou", OpenCVLoader.initLocal())
    }

    @After
    fun limpa() {
        diretorio.deleteRecursively()
    }

    /** Os quatro cantos da pagina (TL, TR, BR, BL), como fracao de (largura, altura), para onde vao. */
    private class Angulo(val nome: String, val cantos: List<Pair<Double, Double>>?)

    private val angulos = listOf(
        Angulo("frontal", null),
        Angulo("moderada-da-5c0", listOf(0.03 to 0.01, 0.98 to 0.04, 0.96 to 0.99, 0.01 to 0.96)),
        Angulo("espelhada", listOf(0.04 to 0.04, 0.97 to 0.01, 0.99 to 0.96, 0.02 to 0.99)),
        Angulo("forte", listOf(0.06 to 0.03, 0.96 to 0.06, 0.93 to 0.98, 0.03 to 0.94)),
    )

    private fun deformada(pagina: Mat, a: Angulo): Mat {
        val cantos = a.cantos ?: return pagina
        val w = pagina.cols().toDouble()
        val h = pagina.rows().toDouble()
        val origem = MatOfPoint2f(Point(0.0, 0.0), Point(w, 0.0), Point(w, h), Point(0.0, h))
        val destino = MatOfPoint2f(*cantos.map { (fx, fy) -> Point(w * fx, h * fy) }.toTypedArray())
        val saida = Mat()
        Imgproc.warpPerspective(
            pagina, saida, Imgproc.getPerspectiveTransform(origem, destino), Size(w, h), Imgproc.INTER_LINEAR,
            Core.BORDER_CONSTANT, Scalar(255.0),
        )
        return saida
    }

    @Test
    fun registra_quantas_regioes_terminam_recusadas_e_o_tempo_do_analisador() {
        val analisador = CameraFrameAnalyzer.daSessao(
            map = prova.folha,
            deveAnalisar = { true },
            jaTemResposta = { _, _ -> false },
            respostas = RespostasEmArquivo(diretorio),
            relogio = { 0L },
            entrega = {},
        )
        var total = 0
        var guardadas = 0
        var recusadas = 0
        var naoLidas = 0
        var frontaisGuardadas = 0
        val tempos = mutableListOf<Long>()
        val temposSoAnalise = mutableListOf<Long>()

        for (token in listOf("tok-a", "tok-b")) {
            val folha = requireNotNull(prova.pacote.folhaDaAtribuicao(token)) { "o pacote nao tem $token" }
            for (pagina in 0..1) {
                val base = prova.pagina(pagina, mapa = folha)
                for (angulo in angulos) {
                    val quadro = deformada(base, angulo)
                    val iniAnalise = System.nanoTime()
                    com.platos.android.vision.SheetReader.analyze(
                        quadro, prova.folha, com.platos.domain.capture.OmrThreshold.MEDIDO_NA_FATIA_3B,
                    )
                    val soAnalise = (System.nanoTime() - iniAnalise) / 1_000_000
                    val ini = System.nanoTime()
                    val analisado = analisador.analisar(quadro)
                    val ms = (System.nanoTime() - ini) / 1_000_000
                    val regiao = analisado.resultado.discursivas.single()
                    total++
                    val situacao = when (regiao) {
                        is RegiaoDiscursivaNoQuadro.NaoLida -> {
                            naoLidas++
                            "NAO-LIDA(${regiao.reason})"
                        }
                        is RegiaoDiscursivaNoQuadro.Reconhecida -> when (val r = analisado.respostas[regiao.regionIndex]) {
                            is RespostaDoQuadro.Guardada -> {
                                guardadas++
                                if (angulo.cantos == null) frontaisGuardadas++
                                "GUARDADA"
                            }
                            is RespostaDoQuadro.Recusada -> {
                                recusadas++
                                "RECUSADA(${r.motivo})"
                            }
                            null -> "SEM-ENTRADA"
                        }
                    }
                    // O primeiro quadro de todos aquece o JIT e as bibliotecas nativas: nao entra na conta.
                    if (total > 1) {
                        tempos += ms
                        temposSoAnalise += soAnalise
                    }
                    Log.i(TAG, "$token pagina=$pagina regiao=${regiao.regionIndex} angulo=${angulo.nome} -> $situacao  (analisar ${ms} ms; so analyze ${soAnalise} ms)")
                }
            }
        }
        Log.i(TAG, "TOTAL regioes=$total guardadas=$guardadas recusadas=$recusadas naoLidas=$naoLidas")
        val ordenados = tempos.sorted()
        Log.i(TAG, "TEMPO analisar() com recorte, n=${ordenados.size} (sem o primeiro quadro): min=${ordenados.first()} ms mediana=${ordenados[ordenados.size / 2]} ms max=${ordenados.last()} ms")
        val soAnalise = temposSoAnalise.sorted()
        Log.i(TAG, "TEMPO so SheetReader.analyze, n=${soAnalise.size}: min=${soAnalise.first()} ms mediana=${soAnalise[soAnalise.size / 2]} ms max=${soAnalise.last()} ms")

        // Guardas de vacuidade, e nada alem delas: o instrumento recorta o que a 5c-0 ja recortava (de frente).
        assertEquals("2 folhas x 2 paginas x 4 angulos", 16, total)
        assertEquals("de frente as 4 regioes devem ser guardadas", 4, frontaisGuardadas)
        assertEquals(total, guardadas + recusadas + naoLidas)
    }

    private companion object {
        const val TAG = "Medida5c1"
    }
}
