package com.platos.android.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.android.omr.RectifiedRegion
import com.platos.android.pacote.PacotesEmArquivo
import com.platos.android.vision.PngDaResposta
import com.platos.domain.exam.ExamPackage
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A tela da resposta (`RespostaTela`), lida pela **arvore de acessibilidade**
 * (`slice-5c-1-a-resposta-fica-no-aparelho`, tarefa 5.1).
 *
 * **Sem `compose-ui-test`**, que nao esta no catalogo: adiciona-lo seria mudanca de dependencia (P22) e o que
 * ele compraria — tocar e ler nos da tela — a arvore de acessibilidade ja entrega, como em
 * [AbrirEscaneamentoComCadernoInstrumentedTest]. O toque e `AccessibilityNodeInfo.performAction(ACTION_CLICK)`, e
 * a posicao e `boundsInScreen`.
 *
 * O disco e preparado como a producao o deixa: um PNG **real** em `filesDir/respostas/` e o caderno guardado no
 * Room, que referencia o arquivo. A Activity e a real, e le a sessao: exige a permissao de camera (pulado no
 * Xiaomi sem o toque manual, com o motivo).
 */
@RunWith(AndroidJUnit4::class)
class RespostaTelaInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val contexto = instrumentation.targetContext
    private val organizacao = "01a06ba4-cb43-7d97-842d-165352d010b5"
    private val shortId = "prova-discursiva"
    private val pasta = File(contexto.filesDir, "respostas")
    private val larguraPng = 123
    private val alturaPng = 77

    private var permissaoConcedida = false
    private val permissaoManual = InstrumentationRegistry.getArguments().getString("permissaoManual") == "true"

    private fun temPermissao() =
        ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private val bytes: ByteArray by lazy {
        instrumentation.context.assets.open("prova-discursiva.package.json").use { it.readBytes() }
    }
    private val hash: String by lazy {
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
    private val pacote: ExamPackage by lazy {
        Json { ignoreUnknownKeys = false }.decodeFromString(ExamPackage.serializer(), bytes.decodeToString())
    }

    @Before
    fun preparar() {
        assertTrue("o OpenCV nativo nao carregou", org.opencv.android.OpenCVLoader.initLocal())
        CadernosEmRoom.reiniciarParaTeste()
        contexto.deleteDatabase("caderno.db")
        pasta.deleteRecursively()
        permissaoConcedida = try {
            instrumentation.uiAutomation.grantRuntimePermission(contexto.packageName, Manifest.permission.CAMERA)
            true
        } catch (e: SecurityException) {
            temPermissao()
        }
        PacotesEmArquivo(File(contexto.filesDir, "packages")).guardar(organizacao, hash, bytes)
    }

    @After
    fun limpar() {
        CadernosEmRoom.reiniciarParaTeste()
        contexto.deleteDatabase("caderno.db")
        pasta.deleteRecursively()
        PacotesEmArquivo(File(contexto.filesDir, "packages")).apagarDaOrganizacao(organizacao)
    }

    /** Um PNG real, com tons diferentes por pixel, para a imagem na tela ter dimensoes que se conferem. */
    private fun guardarResposta(desvioSinalizado: Boolean): RespostaGuardada {
        val pixels = ByteArray(larguraPng * alturaPng) { (it * 7 % 251).toByte() }
        val png = PngDaResposta.codificar(RectifiedRegion(larguraPng, alturaPng, pixels))
        val gravada = RespostasEmArquivo(pasta).gravar(
            png, System.currentTimeMillis() - 86_400_000L, desvioSinalizado, if (desvioSinalizado) 80_000 else 0,
        )
        return (gravada as RespostaDoQuadro.Guardada).resposta
    }

    private fun prepararCaderno(resposta: RespostaGuardada) {
        val caderno = Caderno(
            aluno = "tok-a",
            regioes = listOf(
                RegiaoDoCaderno(0, gabarito = true, rotulo = Caderno.ROTULO_GABARITO, estado = EstadoDaRegiao.Capturada),
                RegiaoDoCaderno(1, gabarito = false, rotulo = "1", estado = EstadoDaRegiao.Capturada, resposta = resposta),
                RegiaoDoCaderno(2, gabarito = false, rotulo = "2", estado = EstadoDaRegiao.NaoVista),
            ),
            parcial = null,
        )
        CadernosEmRoom(CadernosEmRoom.abrir(contexto).cadernos()).guardar(organizacao, pacote.meta.examId, caderno)
        CadernosEmRoom.reiniciarParaTeste()
    }

    private fun exigirPermissao() = Assume.assumeTrue(
        "permissao de camera nao concedida ao teste; a tela do caderno nao pode ser lida sem ela. Para tocar em " +
            "Permitir a mao: -Pandroid.testInstrumentationRunnerArguments.permissaoManual=true",
        permissaoConcedida || permissaoManual,
    )

    private fun aguardarPermissaoManual() {
        if (temPermissao()) return
        Log.w("AbrirEscaneamentoTeste", ">>> TOQUE EM 'PERMITIR' NO DIALOGO DE CAMERA DO APARELHO <<<")
        val limite = System.currentTimeMillis() + 120_000
        while (!temPermissao() && System.currentTimeMillis() < limite) Thread.sleep(500)
        assertTrue("o toque em Permitir nao veio em 2 minutos", temPermissao())
    }

    private fun <T> comAtividade(bloco: (ScanActivity) -> T): T {
        val intent = Intent(contexto, ScanActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(ScanActivity.EXTRA_ORGANIZACAO, organizacao)
            .putExtra(ScanActivity.EXTRA_CONTENT_HASH, hash)
            .putExtra(ScanActivity.EXTRA_SHORT_ID, shortId)
        val atividade = instrumentation.startActivitySync(intent) as ScanActivity
        try {
            aguardarPermissaoManual()
            return bloco(atividade)
        } finally {
            instrumentation.runOnMainSync { atividade.finish() }
            val limite = System.currentTimeMillis() + 5_000
            while (!atividade.isDestroyed && System.currentTimeMillis() < limite) Thread.sleep(100)
            Thread.sleep(1_000)
        }
    }

    // --- a arvore de acessibilidade ---

    private fun nos(): List<AccessibilityNodeInfo> {
        val raiz = instrumentation.uiAutomation.rootInActiveWindow ?: return emptyList()
        val achados = mutableListOf<AccessibilityNodeInfo>()
        fun percorre(no: AccessibilityNodeInfo) {
            achados += no
            for (i in 0 until no.childCount) no.getChild(i)?.let(::percorre)
        }
        percorre(raiz)
        return achados
    }

    private fun AccessibilityNodeInfo.textoOuDescricao(): String = (text ?: contentDescription ?: "").toString()

    private fun achar(aguardarMs: Long = 10_000, criterio: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val limite = System.currentTimeMillis() + aguardarMs
        while (System.currentTimeMillis() < limite) {
            nos().firstOrNull(criterio)?.let { return it }
            Thread.sleep(200)
        }
        return null
    }

    private fun achouAgora(criterio: (AccessibilityNodeInfo) -> Boolean) = nos().firstOrNull(criterio)

    private fun comTexto(fragmento: String) = { n: AccessibilityNodeInfo -> n.textoOuDescricao().contains(fragmento) }

    private fun tocar(no: AccessibilityNodeInfo) {
        var alvo: AccessibilityNodeInfo? = no
        while (alvo != null && !alvo.isClickable) alvo = alvo.parent
        assertNotNull("nenhum no clicavel a partir de '${no.textoOuDescricao()}'", alvo)
        assertTrue("o toque em '${no.textoOuDescricao()}' foi recusado", alvo!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun limites(no: AccessibilityNodeInfo) = Rect().also { no.getBoundsInScreen(it) }

    private fun abrirARespostaDaQuestao1(): AccessibilityNodeInfo {
        val indicador = achar(criterio = comTexto("1 · ok"))
        assertNotNull("o indicador '1 · ok' nao apareceu; textos: ${nos().map { it.textoOuDescricao() }}", indicador)
        tocar(indicador!!)
        return achar(criterio = comTexto("Resposta da questao 1: imagem de"))
            ?: throw AssertionError("a imagem da resposta nao apareceu; textos: ${nos().map { it.textoOuDescricao() }}")
    }

    /** Dimensoes lidas do arquivo por outro decodificador que o que a tela usa para a descricao: so os limites. */
    private fun dimensoesDoArquivo(arquivo: String): Pair<Int, Int> {
        val bytes = requireNotNull(RespostasEmArquivo(pasta).ler(arquivo))
        val opcoes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opcoes)
        return opcoes.outWidth to opcoes.outHeight
    }

    @Test
    fun a_resposta_sinalizada_mostra_a_imagem_com_as_dimensoes_do_arquivo_e_o_aviso_sem_cobri_la() {
        exigirPermissao()
        val resposta = guardarResposta(desvioSinalizado = true)
        prepararCaderno(resposta)

        comAtividade {
            val imagem = abrirARespostaDaQuestao1()

            val (largura, altura) = dimensoesDoArquivo(resposta.arquivo)
            assertEquals("guarda de vacuidade: o PNG gravado tem o tamanho que o teste pediu", larguraPng to alturaPng, largura to altura)
            assertEquals(
                descricaoDaImagem("1", largura, altura),
                imagem.textoOuDescricao(),
            )
            val aviso = achar(criterio = comTexto(ScanState.ProvaComDiscursiva.AVISO_DE_DESVIO))
            assertNotNull("o aviso de desvio nao apareceu", aviso)
            val naImagem = limites(imagem)
            val noAviso = limites(aviso!!)
            assertFalse("o aviso cobre a imagem: $noAviso x $naImagem", Rect.intersects(naImagem, noAviso))
        }
    }

    @Test
    fun a_resposta_nao_sinalizada_nao_traz_aviso() {
        exigirPermissao()
        prepararCaderno(guardarResposta(desvioSinalizado = false))

        comAtividade {
            abrirARespostaDaQuestao1()

            Thread.sleep(500)
            assertEquals(
                "o aviso apareceu numa resposta que nao foi sinalizada",
                null,
                achouAgora(comTexto(ScanState.ProvaComDiscursiva.AVISO_DE_DESVIO)),
            )
        }
    }

    @Test
    fun refazer_devolve_a_regiao_a_nao_vista_e_o_contador_cai() {
        exigirPermissao()
        val resposta = guardarResposta(desvioSinalizado = false)
        prepararCaderno(resposta)

        comAtividade { atividade ->
            assertNotNull("o contador antes nao apareceu", achar(criterio = comTexto("2 de 3 regioes capturadas")))
            abrirARespostaDaQuestao1()
            val refazer = achar(criterio = { it.textoOuDescricao() == "Refazer" })
            assertNotNull("o botao Refazer nao apareceu", refazer)

            tocar(refazer!!)

            assertNotNull("o contador nao caiu", achar(criterio = comTexto("1 de 3 regioes capturadas")))
            assertNotNull("o indicador nao voltou a 'falta'", achar(criterio = comTexto("1 · falta")))
            assertEquals(null, achar(aguardarMs = 1_000, criterio = comTexto("Resposta da questao 1:")))
            assertTrue("o arquivo nao foi eliminado", esperarArquivoSumir(resposta.arquivo))
            assertEquals(EstadoDaRegiao.NaoVista, atividade.instantaneoDoCaderno!!.regioes.single { it.regionIndex == 1 }.estado)
        }
    }

    @Test
    fun regiao_nao_vista_e_gabarito_nao_abrem_a_tela() {
        exigirPermissao()
        prepararCaderno(guardarResposta(desvioSinalizado = false))

        comAtividade {
            val naoVista = achar(criterio = comTexto("2 · falta"))
            val gabarito = achar(criterio = comTexto("Gabarito · ok"))
            assertNotNull(naoVista)
            assertNotNull(gabarito)
            assertFalse("a regiao nao vista e clicavel", naoVista!!.isClickable)
            assertFalse("o gabarito e clicavel", gabarito!!.isClickable)
            // Mesmo um toque no pai nao abre nada: a tela da resposta nao aparece.
            runCatching { tocar(naoVista) }
            runCatching { tocar(gabarito) }

            assertEquals(
                "uma tela de resposta abriu",
                null,
                achar(aguardarMs = 1_000, criterio = comTexto("Resposta da questao")),
            )
        }
    }

    private fun esperarArquivoSumir(arquivo: String): Boolean {
        val limite = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < limite && File(pasta, arquivo).exists()) Thread.sleep(100)
        return !File(pasta, arquivo).exists()
    }
}
