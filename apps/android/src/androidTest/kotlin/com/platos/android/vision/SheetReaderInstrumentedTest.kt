package com.platos.android.vision

import android.graphics.BitmapFactory
import android.os.StrictMode
import java.net.InetSocketAddress
import java.net.Socket
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.domain.capture.InterpretationOutcome
import com.platos.domain.capture.InterpretedReading
import com.platos.domain.capture.OmrReading
import com.platos.domain.capture.OmrThreshold
import com.platos.domain.exam.ExamPackage
import com.platos.domain.layout.LayoutMap
import com.platos.domain.scoring.ObjectiveScoring
import com.platos.domain.scoring.ScoringOutcome
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * O pipeline de §8 inteiro, da imagem versionada ate o vetor de cobertura.
 *
 * A folha fisica nao existe mais; esta digitalizacao e a unica evidencia de papel do repositorio.
 */
@RunWith(AndroidJUnit4::class)
class SheetReaderInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().context

    private val map: LayoutMap by lazy {
        Json.decodeFromString(
            context.assets.open("prova-referencia.layout.json").use { it.readBytes().decodeToString() },
        )
    }
    private val region by lazy { map.regions.single() }

    @Before
    fun carregaOpenCv() {
        assertTrue("o OpenCV nativo nao carregou", OpenCVLoader.initLocal())
    }

    private fun capture(): Mat {
        val bytes = context.assets.open(PROVA).use { it.readBytes() }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw AssertionError("nao consegui decodificar $PROVA")
        val colorido = Mat()
        Utils.bitmapToMat(bitmap, colorido)
        val gray = Mat()
        Imgproc.cvtColor(colorido, gray, Imgproc.COLOR_RGBA2GRAY)
        return gray
    }

    private fun readOrFail(gray: Mat): OmrReading.Read {
        val leitura = SheetReader.read(gray, map, region)
        return leitura as? OmrReading.Read
            ?: throw AssertionError("esperava leitura, veio $leitura")
    }

    @Test
    fun le_a_folha_de_ponta_a_ponta() {
        val leitura = readOrFail(capture())

        assertEquals("prova-referencia-slice-1", leitura.payload.examShortId)
        assertEquals(0, leitura.payload.regionIndex)
        assertEquals(160, leitura.measurements.size)
        assertEquals(
            region.bubbles.map { "${it.questionId}/${it.option}" }.toSet(),
            leitura.measurements.map { it.id }.toSet(),
        )
        // A saida nao carrega veredito: e o corte desta fatia, e ele tem de sobreviver a composicao.
        assertTrue(leitura.measurements.all { it.coveragePerMille in 0..1_000 })
    }

    @Test
    fun a_mesma_captura_lida_duas_vezes_da_a_mesma_medicao() {
        val gray = capture()
        val primeira = readOrFail(gray)
        val segunda = readOrFail(gray)

        assertEquals(primeira.payload, segunda.payload)
        assertEquals(primeira.measurements, segunda.measurements)
    }

    @Test
    fun a_leitura_nao_altera_a_captura_nem_o_mapa() {
        val gray = capture()
        val antes = ByteArray(gray.rows() * gray.cols())
        gray.get(0, 0, antes)
        val mapaAntes = map.toCanonicalJson()

        readOrFail(gray)

        val depois = ByteArray(gray.rows() * gray.cols())
        gray.get(0, 0, depois)
        assertTrue("a leitura alterou a captura", antes.contentEquals(depois))
        assertEquals("a leitura alterou o mapa", mapaAntes, map.toCanonicalJson())
    }

    @Test
    fun a_leitura_nao_toca_a_rede() {
        // §10: captura, OMR e nota objetiva sao **totalmente locais**, e a hora de descobrir que
        // algo no caminho adquiriu dependencia de rede nao e no patio da escola, sem sinal, com a
        // turma esperando.
        //
        // A primeira versao deste teste desligava o radio do emulador e conferia a leitura. Nao
        // serve por duas razoes: `svc data disable` nao derrubou a rede neste emulador — a guarda
        // que eu tinha posto recusou passar, e fez bem —, e mesmo que derrubasse ela provaria o
        // proxy, nao a afirmacao. "O radio esta desligado" nao e "este codigo nao usa rede".
        //
        // `StrictMode` com `detectNetwork` e `penaltyDeath` mata a thread no instante em que
        // qualquer socket for aberto. Testa exatamente a afirmacao, e roda igual no CI.
        val anterior = StrictMode.getThreadPolicy()
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectNetwork()
                .penaltyDeath()
                .build(),
        )
        try {
            val leitura = readOrFail(capture())
            assertEquals(160, leitura.measurements.size)
        } finally {
            StrictMode.setThreadPolicy(anterior)
        }
    }

    @Test
    fun a_guarda_de_rede_reconhece_uma_conexao_quando_ela_existe() {
        // Sem isto, `detectNetwork` poderia estar desligado — por versao de API, por politica do
        // aparelho — e o teste acima passaria para sempre sem verificar nada.
        val anterior = StrictMode.getThreadPolicy()
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectNetwork().penaltyDeath().build(),
        )
        try {
            val morreu = runCatching { Socket().connect(InetSocketAddress("127.0.0.1", 9), 50) }
                .exceptionOrNull()
            assertTrue(
                "a guarda de rede nao reagiu a um socket: $morreu",
                morreu != null,
            )
        } finally {
            StrictMode.setThreadPolicy(anterior)
        }
    }

    @Test
    fun captura_de_outra_prova_e_recusada_com_motivo() {
        // A composicao tem de propagar a recusa, e nao devolver medicao parcial.
        val outroMapa: LayoutMap = Json.decodeFromString(
            context.assets.open("folha-de-teste.layout.json").use { it.readBytes().decodeToString() },
        )
        val leitura = SheetReader.read(capture(), outroMapa, outroMapa.regions.single())

        val recusa = leitura as? OmrReading.Rejected
            ?: throw AssertionError("esperava recusa, veio $leitura")
        assertNotEquals("", recusa.reason)
    }

    private fun interpretOrFail(gray: Mat): InterpretedReading {
        val leitura = SheetReader.readInterpreted(gray, map, region, LIMIAR_DE_TESTE)
        return (leitura as? InterpretationOutcome.Interpreted)?.reading
            ?: throw AssertionError("esperava leitura interpretada, veio $leitura")
    }

    @Test
    fun da_imagem_ate_a_nota_sem_tocar_a_rede() {
        // O caminho inteiro num teste so — imagem, cobertura, veredito, resposta, nota — e sob
        // `StrictMode` com `penaltyDeath`, que e o cenario "sem rede" das duas specs. §10 manda a
        // correcao objetiva ser local: a hora de descobrir que algo no caminho adquiriu dependencia
        // de rede nao e no patio da escola, sem sinal, com a turma esperando.
        //
        // O limiar aqui e **de teste**, 300 com margem 50. Nesta digitalizacao ele nao decide nada
        // apertado: a caneta mais fraca desta folha da 516 e a vazia mais escura da 92, entao o
        // veredito e o mesmo em qualquer limiar do corredor de ADR-0010. O numero do aplicativo sai
        // do corpus fotografado (ADR-0011), e esta imagem e de scanner — ela nao serve para escolhe-lo.
        val pacote: ExamPackage = Json.decodeFromString(
            context.assets.open(PACOTE).use { it.readBytes().decodeToString() },
        )
        val gray = capture()

        val anterior = StrictMode.getThreadPolicy()
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectNetwork().penaltyDeath().build(),
        )
        val nota = try {
            val leitura = interpretOrFail(gray)
            val resultado = ObjectiveScoring.score(pacote, leitura.payload, leitura.answers)
            (resultado as? ScoringOutcome.Scored)?.score
                ?: throw AssertionError("esperava nota, veio $resultado")
        } finally {
            StrictMode.setThreadPolicy(anterior)
        }

        // O oracle nao passa por nenhum tipo desta fatia. Ele sai das **medicoes cruas**: em cada
        // questao, a alternativa acima de 500 permilagem e a marcada — as duas nuvens desta folha
        // estao a 424 pontos uma da outra —, e o acerto e a comparacao dessa letra com o gabarito.
        val cruas = readOrFail(gray).measurements
        val esperado = cruas.groupBy { it.questionId }.count { (questao, bolhas) ->
            val acima = bolhas.filter { it.coveragePerMille > 500 }
            acima.size == 1 && acima.single().option ==
                pacote.answerKey.single { it.itemId == questao }.correct
        }

        // O oracle tambem precisa de guarda: se ele mesmo desandasse para 0 ou para 40, a
        // comparacao acima passaria com a apuracao quebrada nos dois sentidos. O 7 vem de fora
        // deste alvo — `fixtures/prova-referencia.papel.json` cruzado com o `answer_key`, pela
        // implementacao em JavaScript. Quem preencheu a folha na fatia 2b marcou sem consultar o
        // gabarito, e acertou 7.
        assertEquals("o oracle desandou: a folha versionada nao acerta mais 7", 7, esperado)
        assertEquals(40, nota.maxScore)
        assertEquals("a nota divergiu do gabarito conferido sobre a medicao crua", esperado, nota.points)
        assertTrue("a folha nao tem rasura: a nota tem de fechar", nota.closed)
        assertEquals(pacote.contentHash(), nota.packageHash)
        assertTrue(
            "toda questao tem de ter uma resposta",
            nota.pending.isEmpty() && cruas.size == 160,
        )
    }

    @Test
    fun a_interpretacao_preserva_a_cobertura_medida() {
        // A medicao crua e a interpretada precisam trazer o mesmo numero para a mesma bolha. Se a
        // composicao passasse a arredondar, normalizar ou reordenar, e aqui que apareceria.
        val gray = capture()
        val cruas = readOrFail(gray).measurements.associateBy { it.id }
        val julgadas = interpretOrFail(gray).judgements

        assertEquals(cruas.size, julgadas.size)
        for (julgada in julgadas) {
            assertEquals(
                "cobertura de ${julgada.id} mudou ao ser interpretada",
                cruas.getValue(julgada.id).coveragePerMille,
                julgada.measurement.coveragePerMille,
            )
        }
    }

    @Test
    fun folha_cujo_corredor_exclui_o_limiar_e_recusada_no_aparelho() {
        val outroCorredor = region.copy(
            inkBudget = region.inkBudget.copy(thresholdFloor = 100, thresholdCeiling = 180),
        )

        val leitura = SheetReader.readInterpreted(capture(), map, outroCorredor, LIMIAR_DE_TESTE)

        val recusa = leitura as? InterpretationOutcome.Rejected
            ?: throw AssertionError("esperava recusa, veio $leitura")
        assertTrue("o motivo precisa nomear o corredor: ${recusa.reason}", recusa.reason.contains("180"))
    }

    private companion object {
        const val PROVA = "prova-referencia.digitalizacao.jpg"
        const val PACOTE = "prova-referencia.package.json"

        /** De teste, no meio do corredor. O do aplicativo sai do corpus (ADR-0011). */
        val LIMIAR_DE_TESTE = OmrThreshold(value = 300, margin = 50)
    }
}
