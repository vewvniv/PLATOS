package com.platos.android.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.android.outbox.ResultadosEmRoom
import com.platos.android.pacote.PacotesEmArquivo
import com.platos.android.vision.FolhaDiscursivaRenderizada
import com.platos.android.vision.PngDaResposta
import com.platos.android.omr.RectifiedRegion
import com.platos.domain.exam.ExamPackage
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader

/**
 * A fiacao da resposta na `ScanActivity` **aberta de verdade**
 * (`slice-5c-1-a-resposta-fica-no-aparelho`, tarefa 2.4).
 *
 * **O que atravessa.** A Activity real, com o pacote em `filesDir/packages` e o caderno em `caderno.db`.
 * O quadro nao vem da camera (o emulador nao a alimenta com um documento): vem de
 * [FolhaDiscursivaRenderizada], passa por [ScanActivity.analisadorDaCamera] — o mesmo analisador que a
 * camera usa, com o predicado e o diretorio dela — e chega a sessao por [ScanActivity.entregarQuadro],
 * os dois passos do laco da camera. O que fica **sem** atravessar e o `ImageProxy`/`CameraX`.
 */
@RunWith(AndroidJUnit4::class)
class RespostaNaAtividadeInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val contexto = instrumentation.targetContext
    private val organizacao = "01a06ba4-cb43-7d97-842d-165352d010b5"
    private val shortId = "prova-discursiva"
    private val pasta = File(contexto.filesDir, "respostas")
    private val prova = FolhaDiscursivaRenderizada(instrumentation.context, instrumentation.targetContext)

    private var permissaoConcedida = false
    private val permissaoManual =
        InstrumentationRegistry.getArguments().getString("permissaoManual") == "true"

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
        assertTrue("o OpenCV nativo nao carregou", OpenCVLoader.initLocal())
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

    private fun abrir(): ScanActivity {
        val intent = Intent(contexto, ScanActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(ScanActivity.EXTRA_ORGANIZACAO, organizacao)
            .putExtra(ScanActivity.EXTRA_CONTENT_HASH, hash)
            .putExtra(ScanActivity.EXTRA_SHORT_ID, shortId)
        return instrumentation.startActivitySync(intent) as ScanActivity
    }

    /**
     * Abre a Activity e **sempre** a encerra, mesmo quando a asercao falha: uma Activity esquecida de pe
     * guarda o caderno no `onStop` depois de o `@After` fechar o banco do proximo teste (`Database is
     * closed`), e o vermelho de um teste viraria o vermelho do outro.
     */
    private fun <T> comAtividade(bloco: (ScanActivity) -> T): T {
        val atividade = abrir()
        try {
            return bloco(atividade)
        } finally {
            encerrar(atividade)
        }
    }

    private fun encerrar(atividade: ScanActivity) {
        instrumentation.runOnMainSync { atividade.finish() }
        val limite = System.currentTimeMillis() + 5_000
        while (!atividade.isDestroyed && System.currentTimeMillis() < limite) Thread.sleep(100)
        Thread.sleep(1_000)
    }

    private fun esperar(ateMs: Long = 10_000, condicao: () -> Boolean): Boolean {
        val limite = System.currentTimeMillis() + ateMs
        while (System.currentTimeMillis() < limite && !condicao()) Thread.sleep(100)
        return condicao()
    }

    private fun aguardarPermissaoManual() {
        if (temPermissao()) return
        Log.w("AbrirEscaneamentoTeste", ">>> TOQUE EM 'PERMITIR' NO DIALOGO DE CAMERA DO APARELHO <<<")
        val limite = System.currentTimeMillis() + 120_000
        while (!temPermissao() && System.currentTimeMillis() < limite) Thread.sleep(500)
        assertTrue("o toque em Permitir nao veio em 2 minutos", temPermissao())
    }

    /** Um PNG qualquer em `respostas/`, pelo mesmo gravador da producao. */
    private fun umaRespostaEmDisco(): RespostaGuardada {
        val regiao = RectifiedRegion(40, 30, ByteArray(40 * 30) { 0xFF.toByte() })
        val gravada = RespostasEmArquivo(pasta).gravar(PngDaResposta.codificar(regiao), 5_000L, false, 0)
        return (gravada as RespostaDoQuadro.Guardada).resposta
    }

    private fun cadernoComRespostaNaRegiao1(resposta: RespostaGuardada) = Caderno(
        aluno = FolhaDiscursivaRenderizada.TOKEN,
        regioes = listOf(
            RegiaoDoCaderno(0, gabarito = true, rotulo = Caderno.ROTULO_GABARITO, estado = EstadoDaRegiao.Capturada),
            RegiaoDoCaderno(1, gabarito = false, rotulo = "1", estado = EstadoDaRegiao.Capturada, resposta = resposta),
            RegiaoDoCaderno(2, gabarito = false, rotulo = "2", estado = EstadoDaRegiao.NaoVista),
        ),
        parcial = null,
    )

    /**
     * (a) A regiao reconhecida passa a capturada **com o arquivo existindo**, e a fila de envio nao ganha
     * nada enquanto o caderno nao completa. Le a sessao real, e por isso exige a permissao de camera
     * (sem ela a sessao nasce em `NoPermission` e nao aceita quadro): pulado no Xiaomi sem o toque.
     */
    @Test
    fun a_regiao_reconhecida_vira_capturada_com_o_arquivo_e_a_fila_de_envio_nao_ganha_nada() {
        Assume.assumeTrue(
            "permissao de camera nao concedida ao teste; para tocar em Permitir a mao: " +
                "-Pandroid.testInstrumentationRunnerArguments.permissaoManual=true",
            permissaoConcedida || permissaoManual,
        )
        comAtividade { atividade ->
            aguardarPermissaoManual()
            assertTrue(
                "a sessao nao foi montada",
                esperar { atividade.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) },
            )

            val quadro = atividade.analisadorDaCamera().analisar(prova.pagina(0))
            atividade.entregarQuadro(quadro)

            assertTrue(
                "o caderno visivel nao ganhou a resposta",
                esperar { atividade.instantaneoDoCaderno?.regioes?.any { it.resposta != null } == true },
            )
            val d1 = atividade.instantaneoDoCaderno!!.regioes.single { it.regionIndex == 1 }
            assertEquals(EstadoDaRegiao.Capturada, d1.estado)
            val arquivo = requireNotNull(d1.resposta).arquivo
            assertTrue("o arquivo da resposta nao existe em respostas/", File(pasta, arquivo).isFile)
            val fila = ResultadosEmRoom(ResultadosEmRoom.abrir(contexto).pendentes())
            assertEquals("guardar a resposta nao e gravar resultado", 0, fila.quantosPendentes(organizacao))
        }
    }

    /**
     * (b) Activity aberta com caderno guardado que **ja tem** resposta numa regiao: antes do primeiro
     * quadro o instantaneo ja a traz, e um quadro da mesma regiao nao grava arquivo novo. Nao le a tela e
     * nao exige a camera: o instantaneo nasce em `montar`, com ou sem permissao.
     */
    @Test
    fun a_activity_reaberta_ja_enxerga_a_resposta_e_nao_recorta_de_novo() {
        val resposta = umaRespostaEmDisco()
        CadernosEmRoom(CadernosEmRoom.abrir(contexto).cadernos())
            .guardar(organizacao, pacote.meta.examId, cadernoComRespostaNaRegiao1(resposta))
        CadernosEmRoom.reiniciarParaTeste()

        comAtividade { atividade ->
            assertTrue("o instantaneo nao nasceu em `montar`", esperar { atividade.instantaneoDoCaderno != null })
            assertEquals(resposta, atividade.instantaneoDoCaderno!!.regioes.single { it.regionIndex == 1 }.resposta)
            val antes = pasta.list()!!.sorted()
            assertEquals(listOf(resposta.arquivo), antes)

            val quadro = atividade.analisadorDaCamera().analisar(prova.pagina(0))

            assertTrue("guarda de vacuidade: d1 foi reconhecida", quadro.resultado.discursivas.size == 1)
            assertTrue("o quadro recortou de novo: ${quadro.respostas}", quadro.respostas.isEmpty())
            assertEquals(antes, pasta.list()!!.sorted())
            assertNotNull(atividade.instantaneoDoCaderno)
        }
    }
}
