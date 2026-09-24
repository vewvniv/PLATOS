package com.platos.android.vision

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
}
