package com.platos.android.scan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.platos.domain.capture.OmrThreshold
import com.platos.domain.exam.ExamPackage
import com.platos.domain.layout.LayoutMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.serialization.json.Json
import org.opencv.android.OpenCVLoader

/**
 * A unica tela do aplicativo: aponte a camera para uma folha impressa e veja a nota.
 *
 * **O que esta Activity faz e ligar coisas**, e nada mais: carrega o pacote embutido, pede a
 * permissao, liga o CameraX no analisador e leva o estado da sessao para a tela. Toda decisao mora
 * em [ScanSession] e no pipeline — o que esta aqui e o que so existe porque ha um aparelho.
 *
 * **O pacote vem de asset, e isso e provisorio.** Ate a fatia 4 trazer o pull de referencia
 * imutavel, nao ha de onde ele vir sem rede. A conferencia do `exam_short_id` contra ele nao e
 * opcional: sem ela, uma folha de outra prova produziria nota plausivel e errada.
 *
 * Nada e persistido: a nota e apresentada e some. Room e outbox sao da fatia 4.
 */
class ScanActivity : ComponentActivity() {

    private lateinit var examPackage: ExamPackage
    private lateinit var map: LayoutMap
    private lateinit var session: ScanSession
    private lateinit var analysisExecutor: ExecutorService

    private var state by mutableStateOf<ScanState>(ScanState.NoPermission)
    private var previewView: PreviewView? = null
    private var cameraLigada = false

    private val pedidoDePermissao =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
            session.onPermission(concedida)
            state = session.state
            if (concedida) ligaCamera()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        check(OpenCVLoader.initLocal()) { "o OpenCV nativo nao carregou" }

        examPackage = Json.decodeFromString(
            assets.open(PACOTE).use { it.readBytes().decodeToString() },
        )
        map = examPackage.layout.values.single()
        session = ScanSession(examPackage)
        analysisExecutor = Executors.newSingleThreadExecutor()

        setContent {
            ScanScreen(
                state = state,
                onPedirPermissao = { pedidoDePermissao.launch(Manifest.permission.CAMERA) },
                onRetomar = {
                    session.resume()
                    state = session.state
                },
                onPreviewCriado = { view ->
                    previewView = view
                    if (temPermissao()) ligaCamera()
                },
            )
        }

        if (temPermissao()) {
            session.onPermission(granted = true)
            state = session.state
        } else {
            // A permissao e pedida antes de o preview abrir, e nao depois de ele falhar.
            pedidoDePermissao.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdown()
    }

    private fun temPermissao() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Liga preview e analise.
     *
     * **Contrapressao de quadro mais recente**: o pipeline nao roda a 30 por segundo — ele faz
     * deteccao de ArUco, homografia, retificacao e decodificacao —, e uma fila acumularia atraso
     * ate a tela mostrar o passado. Descartar o acumulado e entregar o mais novo e o unico
     * comportamento que mantem a tela falando do presente.
     */
    private fun ligaCamera() {
        val view = previewView ?: return
        if (cameraLigada) return
        cameraLigada = true

        val futuro = ProcessCameraProvider.getInstance(this)
        futuro.addListener({
            val provider = futuro.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = view.surfaceProvider
            }

            val analise = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                RESOLUCAO_DE_ANALISE,
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                            ),
                        )
                        .setAllowedResolutionMode(
                            ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE,
                        )
                        .build(),
                )
                .build()

            analise.setAnalyzer(
                analysisExecutor,
                CameraFrameAnalyzer(
                    map = map,
                    region = map.regions.single(),
                    threshold = OmrThreshold.MEDIDO_NA_FATIA_3B,
                    // A analise para assim que ha resposta na tela; retomar e acao de quem segura o
                    // aparelho. Ver `design.md`, decisao 4.
                    deveAnalisar = {
                        val atual = state
                        atual is ScanState.Searching || atual is ScanState.NotRead
                    },
                    entrega = { resultado ->
                        // A sessao vive na thread principal, e so nela: ela nao e thread-safe, e
                        // nao precisa ser.
                        ContextCompat.getMainExecutor(this).execute {
                            session.onFrame(resultado)
                            state = session.state
                        }
                    },
                ),
            )

            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analise)
        }, ContextCompat.getMainExecutor(this))
    }

    private companion object {
        const val PACOTE = "prova-referencia.package.json"

        /**
         * Resolucao pedida a analise.
         *
         * **Provisoria, e a tarefa 6.1 e quem a fecha.** O padrao do `ImageAnalysis` e 640x480, que
         * nao chega perto de ler um QR de 14 mm numa folha A4 inteira. O numero aqui e o ponto de
         * partida — quanto maior o quadro, mais lenta a deteccao —, e a fatia 3b nao ajuda a
         * escolhe-lo: nela, resolucao **nao** previu decodificacao. Quem fecha isso e o aparelho.
         */
        val RESOLUCAO_DE_ANALISE = Size(1_920, 1_440)
    }
}
