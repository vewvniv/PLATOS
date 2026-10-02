package com.platos.android.corpus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.android.outbox.ResultadosEmRoom
import com.platos.android.pacote.PacotesEmArquivo
import com.platos.android.scan.CadernosEmRoom
import com.platos.android.scan.ScanActivity
import com.platos.android.vision.FolhaDiscursivaRenderizada
import com.platos.domain.exam.ExamPackage
import com.platos.domain.scoring.PontuacaoDada
import com.platos.domain.scoring.Pontos
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader

/**
 * A coleta na `ScanActivity` **aberta de verdade** (`slice-5d-corpus-de-medicao`): com o interruptor ligado, dar a
 * nota deixa uma amostra por discursiva, com a pontuacao dada a cada uma e os bytes da foto; desligado, nao deixa nada.
 *
 * Mesmo molde de `RespostaNaAtividadeInstrumentedTest`: o quadro vem da folha renderizada, passa pelo analisador da
 * camera e chega a sessao por `entregarQuadro`. Exige a permissao de camera (sem ela a sessao nao aceita quadro): pulado
 * no Xiaomi sem o toque (`permissaoManual=true`).
 */
@RunWith(AndroidJUnit4::class)
class ColetaNaNotaInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val contexto = instrumentation.targetContext
    private val organizacao = "01a06ba4-cb43-7d97-842d-165352d010b5"
    private val shortId = "prova-discursiva"
    private val respostasDir = File(contexto.filesDir, "respostas")
    private val marcador = File(contexto.filesDir, ColetaDoCorpusEmArquivo.MARCADOR)
    private val corpus = ColetaDoCorpusEmArquivo.diretorioDe(contexto.filesDir)
    private val prova = FolhaDiscursivaRenderizada(instrumentation.context, instrumentation.targetContext)

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
        assertTrue("o OpenCV nativo nao carregou", OpenCVLoader.initLocal())
        CadernosEmRoom.reiniciarParaTeste()
        ResultadosEmRoom.reiniciarParaTeste()
        contexto.deleteDatabase("caderno.db")
        contexto.deleteDatabase("outbox.db")
        respostasDir.deleteRecursively()
        corpus.deleteRecursively()
        marcador.delete()
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
        ResultadosEmRoom.reiniciarParaTeste()
        contexto.deleteDatabase("caderno.db")
        contexto.deleteDatabase("outbox.db")
        respostasDir.deleteRecursively()
        corpus.deleteRecursively()
        marcador.delete()
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

    private fun <T> comAtividade(bloco: (ScanActivity) -> T): T {
        val atividade = abrir()
        try {
            return bloco(atividade)
        } finally {
            instrumentation.runOnMainSync { atividade.finish() }
            val limite = System.currentTimeMillis() + 5_000
            while (!atividade.isDestroyed && System.currentTimeMillis() < limite) Thread.sleep(100)
            Thread.sleep(1_000)
        }
    }

    private fun esperar(ateMs: Long = 10_000, condicao: () -> Boolean): Boolean {
        val limite = System.currentTimeMillis() + ateMs
        while (System.currentTimeMillis() < limite && !condicao()) Thread.sleep(100)
        return condicao()
    }

    private fun aguardarPermissaoManual() {
        if (temPermissao()) return
        Log.w("ColetaNaNotaTeste", ">>> TOQUE EM 'PERMITIR' NO DIALOGO DE CAMERA DO APARELHO <<<")
        val limite = System.currentTimeMillis() + 120_000
        while (!temPermissao() && System.currentTimeMillis() < limite) Thread.sleep(500)
        assertTrue("o toque em Permitir nao veio em 2 minutos", temPermissao())
    }

    /** Entrega as paginas da folha ate o caderno aguardar a nota; falha dizendo ate onde chegou. */
    private fun completarOCaderno(atividade: ScanActivity) {
        for (indice in 0..3) {
            if (atividade.instantaneoDoCaderno?.aguardaNota == true) break
            val quadro = try {
                atividade.analisadorDaCamera().analisar(prova.pagina(indice))
            } catch (e: IndexOutOfBoundsException) {
                break
            }
            atividade.entregarQuadro(quadro)
            esperar(3_000) { atividade.instantaneoDoCaderno?.aguardaNota == true }
        }
        assertTrue(
            "o caderno nao chegou a aguardar a nota: ${atividade.instantaneoDoCaderno}",
            esperar { atividade.instantaneoDoCaderno?.aguardaNota == true },
        )
    }

    private fun exigirPermissao() = Assume.assumeTrue(
        "permissao de camera nao concedida ao teste; para tocar em Permitir a mao: " +
            "-Pandroid.testInstrumentationRunnerArguments.permissaoManual=true",
        permissaoConcedida || permissaoManual,
    )

    private val notas = listOf(PontuacaoDada("d1", Pontos.parse("1.5")), PontuacaoDada("d2", Pontos.parse("0")))

    @Test
    fun com_a_coleta_ligada_dar_a_nota_deixa_uma_amostra_por_discursiva() {
        exigirPermissao()
        marcador.writeText("")
        comAtividade { atividade ->
            aguardarPermissaoManual()
            completarOCaderno(atividade)
            val fotosDeOrigem = respostasDir.listFiles()!!.map { it.readBytes().toList() }.toSet()
            assertEquals("a origem tem uma foto por discursiva", 2, fotosDeOrigem.size)

            atividade.darNota(notas)

            assertTrue(
                "a nota nao deixou duas amostras no corpus: ${corpus.list()?.toList()}",
                esperar { corpus.listFiles { f -> f.extension == "json" }?.size == 2 },
            )
            val dados = corpus.listFiles { f -> f.extension == "json" }!!.map {
                AmostraDoCorpus.json.decodeFromString(AmostraDoCorpus.serializer(), it.readText())
            }
            assertEquals(setOf("1.50", "0.00"), dados.map { it.pontos }.toSet())
            assertEquals(setOf(3, 4), dados.map { it.maximo }.toSet())
            assertEquals(setOf("d1", "d2"), dados.map { it.item }.toSet())
            assertEquals(setOf(pacote.contentHash()), dados.map { it.pacote }.toSet())
            val fotosDaCopia = corpus.listFiles { f -> f.extension == "png" }!!.map { it.readBytes().toList() }.toSet()
            assertEquals("os bytes da foto nao sao os de respostas/", fotosDeOrigem, fotosDaCopia)
            for (arquivo in corpus.listFiles()!!) {
                assertFalse("o token da folha esta em ${arquivo.name}", FolhaDiscursivaRenderizada.TOKEN in arquivo.name)
                if (arquivo.extension == "json") assertFalse(FolhaDiscursivaRenderizada.TOKEN in arquivo.readText())
            }
        }
    }

    @Test
    fun com_a_coleta_desligada_dar_a_nota_nao_deixa_nada() {
        exigirPermissao()
        comAtividade { atividade ->
            aguardarPermissaoManual()
            completarOCaderno(atividade)

            atividade.darNota(notas)

            val gravou = esperar {
                ResultadosEmRoom(ResultadosEmRoom.abrir(contexto).pendentes()).quantosPendentes(organizacao) >= 1
            }
            assertTrue("a nota nao foi gravada: o teste nao diria nada sobre o corpus", gravou)
            assertFalse("o corpus nasceu com a coleta desligada", corpus.exists() && corpus.list().orEmpty().isNotEmpty())
        }
    }
}
