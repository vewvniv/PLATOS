package com.platos.android.vision

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.domain.capture.OmrThreshold
import com.platos.domain.layout.DrawQr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader

/**
 * A captura numa folha com mais de uma região (`slice-5b-1-o-aparelho-reconhece-a-discursiva`).
 *
 * Os quadros são o documento da folha de `tok-a` da prova com discursiva, desenhado pelo
 * renderizador de produção e rasterizado ([FolhaDiscursivaRenderizada]). A página 0 traz o gabarito
 * (marcadores 0–3) e a região de `d1` (4–7); a página 1 traz só a região de `d2` (8–11).
 */
@RunWith(AndroidJUnit4::class)
class RegiaoDiscursivaInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val prova = FolhaDiscursivaRenderizada(instrumentation.context, instrumentation.targetContext)

    @Before
    fun carregaOpenCv() {
        assertTrue("o OpenCV nativo nao carregou", OpenCVLoader.initLocal())
    }

    /**
     * Tarefa 1.1: os marcadores esperados são os da região, e não os da página.
     *
     * Antes da mudança, `declaredMarkersOf` devolvia os 8 ArUcos da página 0, e `detect` recusava com
     * "declara 8 ArUcos; esperados 4" — sem nunca chegar à homografia.
     */
    @Test
    fun o_gabarito_e_retificado_numa_pagina_que_tem_outra_regiao() {
        val gabarito = prova.folha.regions.single { it.index == 0 }
        val pagina0 = prova.pagina(0)

        val deteccao = RegionDetector.detect(pagina0, prova.folha, gabarito)

        val retificada = deteccao as? DetectionOutcome.Rectified
            ?: throw AssertionError("esperava a regiao 0 retificada, veio $deteccao")
        assertEquals(listOf(0, 1, 2, 3), retificada.detectedMarkerIds)
    }

    // --- 1.2: as regioes saem do quadro ---

    private val limiar = OmrThreshold.MEDIDO_NA_FATIA_3B

    private fun reconhecida(regiao: RegiaoDiscursivaNoQuadro): RegiaoDiscursivaNoQuadro.Reconhecida =
        regiao as? RegiaoDiscursivaNoQuadro.Reconhecida
            ?: throw AssertionError("esperava a regiao ${regiao.regionIndex} reconhecida, veio $regiao")

    /** Cenario "Duas regioes no mesmo quadro", e "Regiao discursiva reconhecida". */
    @Test
    fun a_pagina_0_le_o_gabarito_e_reconhece_a_regiao_de_d1() {
        val quadro = SheetReader.analyze(prova.pagina(0), prova.folha, limiar)

        val lido = quadro as? FrameOutcome.Read ?: throw AssertionError("esperava o gabarito lido, veio $quadro")
        assertEquals(0, lido.reading.payload.regionIndex)
        assertEquals(FolhaDiscursivaRenderizada.TOKEN, lido.reading.payload.studentToken)

        val d1 = reconhecida(lido.discursivas.single())
        assertEquals(1, d1.regionIndex)
        assertEquals("d1", d1.questionId)
        assertEquals(1, d1.payload.regionIndex)
        assertEquals(FolhaDiscursivaRenderizada.TOKEN, d1.payload.studentToken)
        assertEquals(prova.pacote.meta.examId, d1.payload.examShortId)
    }

    /** Cenario "So a regiao discursiva no quadro". */
    @Test
    fun a_pagina_1_so_tem_a_regiao_de_d2_e_o_gabarito_nao_e_exigido() {
        val quadro = SheetReader.analyze(prova.pagina(1), prova.folha, limiar)

        val so = quadro as? FrameOutcome.SoDiscursivas
            ?: throw AssertionError("esperava so a discursiva, veio $quadro")
        val d2 = reconhecida(so.discursivas.single())
        assertEquals(2, d2.regionIndex)
        assertEquals("d2", d2.questionId)
        assertEquals(2, d2.payload.regionIndex)
    }

    /**
     * Cenario "Regiao pela metade".
     *
     * A pagina 0 cortada na altura do meio da regiao de `d1`: os marcadores de cima dela (4 e 5)
     * ficam no quadro e os de baixo (6 e 7) saem. O gabarito, inteiro acima, continua lido.
     */
    @Test
    fun regiao_pela_metade_nao_e_lida_e_nao_derruba_o_gabarito() {
        val d1 = prova.folha.regions.single { it.questionId == "d1" }
        val gabarito = prova.folha.regions.single { it.index == 0 }
        // Guarda de vacuidade: o corte so prova algo se o gabarito inteiro ficar acima dele.
        val meioDeD1 = d1.quadY + d1.quadHeight / 2
        assertTrue("o gabarito nao esta acima de d1", gabarito.quadY + gabarito.quadHeight < d1.quadY)

        val pagina0 = prova.pagina(0)
        val cortePx = meioDeD1 / 100 // 10 px/mm = 100 um por pixel
        val cortada = pagina0.submat(0, cortePx, 0, pagina0.cols())

        val quadro = SheetReader.analyze(cortada, prova.folha, limiar)

        val lido = quadro as? FrameOutcome.Read ?: throw AssertionError("esperava o gabarito lido, veio $quadro")
        assertTrue("a regiao pela metade foi lida: ${lido.discursivas}", lido.discursivas.isEmpty())
    }

    /** Cenario "QR de uma regiao dentro dos marcadores de outra". */
    @Test
    fun qr_da_regiao_2_dentro_dos_marcadores_da_regiao_1_e_recusado() {
        val d1 = prova.folha.regions.single { it.questionId == "d1" }
        val qrDaRegiao2 = prova.pacote.assignments.single { it.studentToken == FolhaDiscursivaRenderizada.TOKEN }
            .qrs.single { it.regionIndex == 2 }
        val trocada = prova.folha.copy(
            pages = prova.folha.pages.map { pagina ->
                pagina.copy(
                    primitives = pagina.primitives.map {
                        if (it is DrawQr && it.id == d1.qrId) it.copy(payload = qrDaRegiao2.payload, modules = qrDaRegiao2.modules) else it
                    },
                )
            },
        )

        // O mapa com que se le e o verdadeiro; so o documento traz o QR trocado.
        val quadro = SheetReader.analyze(prova.pagina(0, mapa = trocada), prova.folha, limiar)

        val naoLida = quadro.discursivas.single() as? RegiaoDiscursivaNoQuadro.NaoLida
            ?: throw AssertionError("esperava a regiao de d1 recusada, veio ${quadro.discursivas}")
        assertEquals(1, naoLida.regionIndex)
        assertTrue("o motivo nao aponta a divergencia: ${naoLida.reason}", naoLida.reason.contains("o QR diz regiao 2"))
    }
}
