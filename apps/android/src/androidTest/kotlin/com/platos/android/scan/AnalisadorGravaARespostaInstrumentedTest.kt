package com.platos.android.scan

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.android.vision.FolhaDiscursivaRenderizada
import com.platos.android.vision.FolhaEmAngulo
import com.platos.android.vision.FrameOutcome
import com.platos.android.vision.RegiaoDiscursivaNoQuadro
import com.platos.android.vision.SheetReader
import com.platos.domain.capture.OmrThreshold
import com.platos.domain.exam.folhaDaAtribuicao
import java.io.File
import java.io.IOException
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.Mat

/**
 * O analisador pede o recorte e grava a resposta (`slice-5c-1-a-resposta-fica-no-aparelho`, tarefa 2.2),
 * sobre o documento da folha renderizada pelo renderizador de producao, de frente e em perspectiva.
 *
 * **Nada aqui e papel nem camera**: o quadro e o documento (ou o documento deformado por uma homografia
 * conhecida), e [CameraFrameAnalyzer.analisar] e chamado direto, sem `ImageProxy`. A tinta do aluno nao
 * existe: as folhas estao em branco, e o sinal de desvio, portanto, e falso.
 */
@RunWith(AndroidJUnit4::class)
class AnalisadorGravaARespostaInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val prova = FolhaDiscursivaRenderizada(instrumentation.context, instrumentation.targetContext)
    private val diretorio = File(instrumentation.targetContext.cacheDir, "analisador-${UUID.randomUUID()}")
    private val gravacoes = mutableListOf<String>()

    /** Conta as gravacoes: "zero recortes" e "zero arquivos" sao duas coisas, e as duas sao conferidas. */
    private inner class Contando(private val real: RespostasGuardadas) : RespostasGuardadas by real {
        override fun gravar(png: ByteArray, capturadaEm: Long, desvioSinalizado: Boolean, foraPpm: Int) =
            real.gravar(png, capturadaEm, desvioSinalizado, foraPpm).also {
                gravacoes += (it as? RespostaDoQuadro.Guardada)?.resposta?.arquivo ?: "recusada"
            }
    }

    private val repositorio = RespostasEmArquivo(diretorio)
    private val relogio = { 1_234_567L }

    @Before
    fun carregaOpenCv() {
        assertTrue("o OpenCV nativo nao carregou", OpenCVLoader.initLocal())
    }

    @After
    fun limpa() {
        diretorio.deleteRecursively()
    }

    private fun analisador(jaTemResposta: (String, Int) -> Boolean = { _, _ -> false }) =
        CameraFrameAnalyzer.daSessao(
            map = prova.folha,
            deveAnalisar = { true },
            jaTemResposta = jaTemResposta,
            respostas = Contando(repositorio),
            relogio = relogio,
            entrega = {},
        )

    private fun guardada(r: RespostaDoQuadro?): RespostaGuardada =
        (r as? RespostaDoQuadro.Guardada)?.resposta ?: throw AssertionError("esperava a resposta guardada, veio $r")

    /** Um quadro com as tres regioes: a pagina 0 (gabarito e `d1`) sobre a pagina 1 (`d2`). Sintetico. */
    private fun duasPaginasNumQuadro(): Mat {
        val saida = Mat()
        Core.vconcat(listOf(prova.pagina(0), prova.pagina(1)), saida)
        return saida
    }

    @Test
    fun o_quadro_de_frente_devolve_a_resposta_guardada_com_o_relogio_e_o_desvio() {
        val quadro = analisador().analisar(prova.pagina(0))

        val d1 = guardada(quadro.respostas[1])
        assertEquals(1_234_567L, d1.capturadaEm)
        assertFalse("folha em branco nao tem desvio", d1.desvioSinalizado)
        assertEquals(0, d1.foraPpm)
        assertTrue("o arquivo guardado nao existe", repositorio.existe(d1.arquivo))
        assertEquals(setOf(1), quadro.respostas.keys)
    }

    @Test
    fun o_quadro_em_perspectiva_tambem_devolve_a_resposta_guardada() {
        val quadro = analisador().analisar(FolhaEmAngulo.de(prova.pagina(0)))

        val d1 = guardada(quadro.respostas[1])
        assertTrue(repositorio.existe(d1.arquivo))
    }

    @Test
    fun o_quadro_com_duas_discursivas_devolve_duas_respostas_guardadas() {
        val quadro = analisador().analisar(duasPaginasNumQuadro())

        val reconhecidas = quadro.resultado.discursivas.filterIsInstance<RegiaoDiscursivaNoQuadro.Reconhecida>()
        assertEquals(
            "guarda de vacuidade: o quadro precisa reconhecer as duas",
            listOf(1, 2),
            reconhecidas.map { it.regionIndex },
        )
        val d1 = guardada(quadro.respostas[1])
        val d2 = guardada(quadro.respostas[2])
        assertEquals(1_234_567L, d1.capturadaEm)
        assertEquals(1_234_567L, d2.capturadaEm)
        assertEquals(2, repositorio.listar().size)
        assertTrue(d1.arquivo != d2.arquivo)
    }

    /** Cenario "A analise do gabarito nao muda": o `FrameOutcome` e o de `SheetReader.analyze` sozinho. */
    @Test
    fun o_resultado_da_analise_e_igual_ao_de_analyze_sozinho() {
        for (pagina in listOf(prova.pagina(0), FolhaEmAngulo.de(prova.pagina(0)), prova.pagina(1))) {
            val sozinho = SheetReader.analyze(pagina, prova.folha, OmrThreshold.MEDIDO_NA_FATIA_3B)

            val comRecorte = analisador().analisar(pagina).resultado

            assertEquals(sozinho, comRecorte)
        }
        assertTrue("guarda de vacuidade: houve recorte em algum quadro", gravacoes.isNotEmpty())
    }

    /** Cenario "Regiao nao reconhecida nao e recortada": `d1` pela metade, o gabarito inteiro. */
    @Test
    fun regiao_nao_lida_nao_e_recortada() {
        val d1 = prova.folha.regions.single { it.questionId == "d1" }
        val pagina0 = prova.pagina(0)
        val cortada = pagina0.submat(0, (d1.quadY + d1.quadHeight / 2) / 100, 0, pagina0.cols())

        val quadro = analisador().analisar(cortada)

        assertTrue("guarda de vacuidade: o gabarito foi lido", quadro.resultado is FrameOutcome.Read)
        assertTrue("d1 nao devia estar reconhecida", quadro.resultado.discursivas.isEmpty())
        assertTrue(quadro.respostas.isEmpty())
        assertEquals(emptyList<String>(), repositorio.listar())
        assertEquals(emptyList<String>(), gravacoes)
    }

    /**
     * Cenario "Regiao nao reconhecida nao e recortada", na forma em que a regiao **esta** no quadro e nao e
     * lida: o QR de `d1` coberto de branco, com os marcadores intactos. A regiao chega como `NaoLida`, com o
     * motivo, e nao ganha recorte.
     */
    @Test
    fun regiao_presente_e_nao_lida_nao_e_recortada_e_o_resultado_diz_o_motivo() {
        val d1 = prova.folha.regions.single { it.questionId == "d1" }
        val qr = prova.folha.pages.single { it.index == d1.page }.primitives
            .filterIsInstance<com.platos.domain.layout.DrawQr>().single { it.id == d1.qrId }
        val pagina = prova.pagina(0)
        // 10 px/mm = 100 um por pixel; uma folga de 1 mm em volta do QR.
        pagina.submat(
            (qr.y / 100 - 10), (qr.y + qr.side) / 100 + 10, (qr.x / 100 - 10), (qr.x + qr.side) / 100 + 10,
        ).setTo(org.opencv.core.Scalar(255.0))

        val quadro = analisador().analisar(pagina)

        val naoLida = quadro.resultado.discursivas.single() as? RegiaoDiscursivaNoQuadro.NaoLida
            ?: throw AssertionError("esperava d1 presente e nao lida, veio ${quadro.resultado.discursivas}")
        assertTrue("o resultado precisa dizer o motivo", naoLida.reason.isNotBlank())
        assertTrue(quadro.respostas.isEmpty())
        assertEquals(emptyList<String>(), gravacoes)
        assertEquals(emptyList<String>(), repositorio.listar())
    }

    /** Cenario "Resposta ja guardada nao e pedida de novo": zero recortes, zero arquivos. */
    @Test
    fun com_a_resposta_ja_guardada_do_mesmo_aluno_nenhum_recorte_e_pedido() {
        val quadro = analisador(jaTemResposta = { aluno, regiao -> aluno == "tok-a" && regiao == 1 })
            .analisar(prova.pagina(0))

        assertEquals("guarda de vacuidade: d1 foi reconhecida", 1, quadro.resultado.discursivas.size)
        assertTrue(quadro.respostas.isEmpty())
        assertEquals(emptyList<String>(), gravacoes)
        assertEquals(emptyList<String>(), repositorio.listar())
    }

    /** Cenario "Folha de outro aluno pede de novo": a resposta guardada e de A, e a folha e de B. */
    @Test
    fun a_folha_do_aluno_b_e_recortada_mesmo_com_a_regiao_do_aluno_a_guardada() {
        val folhaDeB = requireNotNull(prova.pacote.folhaDaAtribuicao("tok-b")) { "o pacote nao tem tok-b" }
        val quadro = analisador(jaTemResposta = { aluno, regiao -> aluno == "tok-a" && regiao == 1 })
            .analisar(prova.pagina(0, mapa = folhaDeB))

        val d1 = quadro.resultado.discursivas.single() as RegiaoDiscursivaNoQuadro.Reconhecida
        assertEquals("tok-b", d1.payload.studentToken)
        guardada(quadro.respostas[1])
        assertEquals(1, gravacoes.size)
    }

    /**
     * Tarefa 2.3: com a sessao e o analisador reais, depois de um quadro que reconhece a discursiva o
     * estado nao e analisavel, e um segundo quadro **forcado** no mesmo estado (como se alguem religasse a
     * analise) nao gera arquivo novo para a mesma regiao: o instantaneo do caderno ja a tem.
     */
    @Test
    fun o_estado_depois_do_primeiro_quadro_nao_analisa_e_um_segundo_quadro_nao_grava_de_novo() {
        val sessao = ScanSession(prova.pacote).apply { onPermission(granted = true) }
        val analisador = analisador(jaTemResposta = { aluno, regiao -> sessao.cadernoAtual.jaTemResposta(aluno, regiao) })
        assertTrue("guarda de vacuidade: a sessao nova analisa", deveAnalisar(sessao.state))

        val primeiro = analisador.analisar(prova.pagina(0))
        sessao.onFrame(primeiro.resultado, primeiro.respostas)
        val arquivosDepoisDoPrimeiro = repositorio.listar()
        val segundo = analisador.analisar(prova.pagina(0))
        sessao.onFrame(segundo.resultado, segundo.respostas)

        assertEquals(1, arquivosDepoisDoPrimeiro.size)
        assertFalse("o estado depois de reconhecer a discursiva nao pode analisar", deveAnalisar(sessao.state))
        assertTrue("o segundo quadro gerou resposta nova: ${segundo.respostas}", segundo.respostas.isEmpty())
        assertEquals(arquivosDepoisDoPrimeiro, repositorio.listar())
        val d1 = sessao.cadernoAtual!!.regioes.single { it.regionIndex == 1 }
        assertEquals(arquivosDepoisDoPrimeiro.single(), d1.resposta?.arquivo)
    }

    /** A recusa chega com o motivo, e nenhum arquivo fica: a imagem nao pode ser gravada. */
    @Test
    fun imagem_que_nao_pode_ser_gravada_chega_como_recusa_e_nenhum_arquivo_fica() {
        val quebrado = RespostasEmArquivo(diretorio, escrever = { arquivo, bytes ->
            arquivo.writeBytes(bytes.copyOf(bytes.size / 2))
            throw IOException("sem espaco no dispositivo")
        })
        val analisador = CameraFrameAnalyzer.daSessao(
            map = prova.folha,
            deveAnalisar = { true },
            jaTemResposta = { _, _ -> false },
            respostas = quebrado,
            relogio = relogio,
            entrega = {},
        )

        val quadro = analisador.analisar(prova.pagina(0))

        val recusa = quadro.respostas[1] as? RespostaDoQuadro.Recusada
            ?: throw AssertionError("esperava a recusa, veio ${quadro.respostas[1]}")
        assertTrue(recusa.motivo, recusa.motivo.contains("sem espaco no dispositivo"))
        assertEquals(emptyList<String>(), quebrado.listar())
    }
}
