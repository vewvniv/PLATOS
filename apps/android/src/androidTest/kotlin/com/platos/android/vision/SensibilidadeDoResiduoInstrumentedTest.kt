package com.platos.android.vision

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.Point

/**
 * O que o maior residuo do segundo ajuste consegue pegar
 * (`slice-5c-0-o-recorte-da-resposta`, tarefa 3.3; design, decisao 3).
 *
 * Cada um dos onze pontos e deslocado, um por vez, de 1, 2, 3 e 5 mm — em `+x` e em `+y` —, e o maior
 * residuo do ajuste resultante e registrado (`logcat -s Medida5c0`). O teto e de 1,0 mm. A pergunta
 * nao e "o ajuste recusa?" e sim **"para quais pontos e tamanhos ele nao recusa"**: o resultado que
 * a tarefa quer e a lista do que o residuo nao denuncia, que entra na cobertura como lacuna
 * (P8: nao e mitigado, e conhecido).
 *
 * Sobre a folha de frente, renderizada: a 10 px/mm o deslocamento em pixels da imagem e o
 * deslocamento em mm da regiao vezes dez, com a folga da escala da pagina. **Nada aqui e papel.**
 */
@RunWith(AndroidJUnit4::class)
class SensibilidadeDoResiduoInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val prova = FolhaDiscursivaRenderizada(instrumentation.context, instrumentation.targetContext)

    @Before
    fun carregaOpenCv() {
        assertTrue("o OpenCV nativo nao carregou", OpenCVLoader.initLocal())
    }

    private val nomes = listOf(
        "TL.c1", "TL.c2", "TL.c3", "TL.c4", // quatro cantos do marcador de cima
        "BR.c1", "BR.c2", "BR.c3", "BR.c4", // quatro cantos do marcador de baixo
        "QR.tl", "QR.tr", "QR.bl", // cantos de ancoragem do QR
    )

    private val deslocamentosMm = listOf(1, 2, 3, 5)

    /** Tabela `[regiao][direcao][ponto][deslocamento]` do maior residuo, em mm. */
    private fun tabela(regiaoIndex: Int): Map<String, List<List<Double>>> {
        val r = prova.folha.regions.single { it.index == regiaoIndex }
        val pagina = prova.pagina(r.page)
        val found = RegionDetector.detectMarkers(pagina)
        val deteccao = RegionDetector.detect(pagina, prova.folha, r, found) as DetectionOutcome.Rectified
        val qr = RegionQrReader.read(deteccao.qrCanvas, deteccao.detectedMarkerIds, prova.folha) as QrOutcome.Read
        val pontos = RegionDetector.pontosDoSegundoAjuste(prova.folha, r, found, qr, deteccao.qrCanvas)
            as RegionDetector.PontosDoAjuste.Prontos

        val resultado = LinkedHashMap<String, List<List<Double>>>()
        for ((direcao, dx, dy) in listOf(Triple("+x", 1.0, 0.0), Triple("+y", 0.0, 1.0))) {
            resultado[direcao] = pontos.observados.indices.map { i ->
                deslocamentosMm.map { mm ->
                    val deslocados = pontos.observados.toMutableList()
                    deslocados[i] = Point(deslocados[i].x + dx * mm * PX_POR_MM, deslocados[i].y + dy * mm * PX_POR_MM)
                    val (_, residuos) = RegionDetector.homografiaEResiduos(deslocados, pontos.alvos, r)
                        ?: throw AssertionError("o ajuste nao fechou com o ponto ${nomes[i]} deslocado $mm mm")
                    residuos.max()
                }
            }
        }
        return resultado
    }

    /**
     * A tabela, registrada, e o que ela mostra ser **pego** (tarefa 3.3, "assere so o que a tabela diz
     * que e pego"). Os valores foram lidos da primeira execucao (2026-09-30); as asserções abaixo
     * pinam o que ela mostrou, e nao um criterio anterior a ela.
     *
     * - A partir de 3 mm, **todo** ponto, nas duas direcoes e nas duas regioes, leva o ajuste a ser recusado.
     * - Com 1 mm, **nenhum** e recusado: o teto de 1,0 mm e sobre o **maior residuo**, e os minimos
     *   quadrados repartem o erro entre os onze pontos — o residuo e uma fracao (0,36 a 0,82) do
     *   deslocamento. Limite conhecido, e nao mitigado.
     * - Os dois pontos que o residuo denuncia mais tarde, e que ainda passam com 2 mm: o canto superior
     *   direito do QR em `+x` e o quarto canto do marcador de baixo em `+y`.
     */
    @Test
    fun a_tabela_de_sensibilidade_do_maior_residuo() {
        val teto = RegionDetector.MAX_RESIDUAL_MM
        for (indice in listOf(1, 2)) {
            val t = tabela(indice)
            for ((direcao, linhas) in t) {
                linhas.forEachIndexed { i, v ->
                    val rotulo = "regiao $indice $direcao ${nomes[i]}"
                    assertTrue("$rotulo: residuo devia crescer com o deslocamento: $v", v == v.sorted())
                    assertTrue("$rotulo: com 1 mm a tabela mostrou que NAO recusa; foi ${v[0]}", v[0] <= teto)
                    assertTrue("$rotulo: com 3 mm a tabela mostrou recusa; foi ${v[2]}", v[2] > teto)
                    assertTrue("$rotulo: com 5 mm a tabela mostrou recusa; foi ${v[3]}", v[3] > teto)
                }
            }
            assertTrue("QR.tr em +x com 2 mm devia passar", t.getValue("+x")[nomes.indexOf("QR.tr")][1] <= teto)
            assertTrue("BR.c4 em +y com 2 mm devia passar", t.getValue("+y")[nomes.indexOf("BR.c4")][1] <= teto)
        }
    }

    @Test
    fun registra_a_tabela_de_sensibilidade_do_maior_residuo() {
        for (indice in listOf(1, 2)) {
            val t = tabela(indice)
            for ((direcao, linhas) in t) {
                Log.i(TAG, "sens regiao $indice $direcao (mm deslocados: ${deslocamentosMm.joinToString("/")}; teto ${RegionDetector.MAX_RESIDUAL_MM})")
                linhas.forEachIndexed { i, valores ->
                    val marcas = valores.map { if (it > RegionDetector.MAX_RESIDUAL_MM) "RECUSA" else "passa" }
                    Log.i(
                        TAG,
                        "sens r$indice $direcao ${nomes[i].padEnd(6)} " +
                            valores.joinToString(" ") { "%6.3f".format(it) } + "  " + marcas.joinToString(" "),
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "Medida5c0"
        const val PX_POR_MM = 10.0
    }
}
