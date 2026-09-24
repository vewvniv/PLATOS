package com.platos.android.scan

import com.platos.android.roster.RosterDaProva
import com.platos.android.roster.RostersEmArquivo

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
import com.platos.android.pacote.PacotesEmArquivo
import com.platos.domain.capture.OmrThreshold
import com.platos.domain.exam.ExamPackage
import com.platos.domain.layout.LayoutMap
import androidx.lifecycle.lifecycleScope
import com.platos.android.outbox.EnvioDeResultadosWorker
import com.platos.android.outbox.gravarEAgendar
import com.platos.android.outbox.ResultadoPendente
import com.platos.android.outbox.ResultadosEmRoom
import com.platos.android.outbox.ResultadosPendentes
import java.io.File
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.opencv.android.OpenCVLoader

/**
 * A unica tela do aplicativo: aponte a camera para uma folha impressa e veja a nota.
 *
 * **O que esta Activity faz e ligar coisas**, e nada mais: carrega o pacote embutido, pede a
 * permissao, liga o CameraX no analisador e leva o estado da sessao para a tela. Toda decisao mora
 * em [ScanSession] e no pipeline — o que esta aqui e o que so existe porque ha um aparelho.
 *
 * **O pacote vem do cache, pelo endereco que o `Intent` trouxe.** Nao ha asset embutido, e nao ha
 * caminho de reserva: sem pacote conferido a camera nao abre. Um pacote embutido que sobrevivesse
 * "por enquanto" viraria caminho permanente de reserva, e com ele o portao binario de ADR-0009
 * morreria em silencio — o aparelho escanearia com um pacote que ninguem puxou nem conferiu, e nada
 * na tela diria isso.
 *
 * **A leitura reconfere**, e nao confia na decisao que o gate ja tomou. Nao e redundancia: e o que
 * faz o requisito "reconferido a cada leitura" valer no caminho real, e o que faz esta `Activity`
 * sobreviver a morte do processo — o `Intent` persiste, o endereco continua valido, e a conferencia
 * acontece de novo em vez de ser herdada de antes de o processo morrer.
 *
 * A conferencia do `exam_short_id` contra o payload do QR — a camada (c) — continua em
 * [ScanSession], e e a unica das tres que julga **de quem e a folha**, e nao os bytes.
 *
 * **A nota e persistida antes de qualquer rede.** `onCreate` abre a fila do Room e [gravar] leva
 * a apuracao para [gravarEAgendar], que grava o pendente fora do fio principal e so entao agenda
 * o [EnvioDeResultadosWorker]. A tela mostra a nota; o que a sustenta e a linha gravada, nao ela.
 *
 * *Esta KDoc dizia "Nada e persistido: a nota e apresentada e some. Room e outbox sao da fatia
 * 4b." Era verdade ate a fatia 4b, e foi esta fatia que a tornou falsa sem reescreve-la: a
 * descricao da classe ficou para tras da propria mudanca que a alterou. Fica dito em vez de
 * apagado (P7), porque quem lesse so a frase antiga concluiria que nao ha dado duravel aqui.*
 */
class ScanActivity : ComponentActivity() {

    private lateinit var examPackage: ExamPackage

    private var roster: RosterDaProva? = null
    private lateinit var map: LayoutMap
    private lateinit var session: ScanSession
    private lateinit var pendentes: ResultadosPendentes
    // **Nao nulaveis, e a ausencia do `?` e o requisito.** Eram `String?`, e `gravar` tinha um
    // `?: return` para cada: folha medida, nota desenhada na tela, nada gravado, nada agendado, sem
    // mensagem (achado 3.3). Quem decide que ha tudo o que precisa e [decidirAbertura], antes de a
    // camera ligar; depois dela o estado silencioso nao e construivel.
    private lateinit var organizacao: String
    private lateinit var prova: String
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

        // **Recusa com motivo, e nunca degradacao.** A decisao mora fora desta classe — ver
        // [decidirAbertura] —, porque decisao dentro de `onCreate` nao tem como ser exercitada, e
        // foi a vinte linhas de uma dessas que a auditoria achou um caminho silencioso.
        val abertura = decidirAbertura(
            organizacao = intent.getStringExtra(EXTRA_ORGANIZACAO),
            contentHash = intent.getStringExtra(EXTRA_CONTENT_HASH),
            shortId = intent.getStringExtra(EXTRA_SHORT_ID),
            lerPacote = PacotesEmArquivo(File(filesDir, "packages"))::ler,
        )

        if (abertura is AberturaDoEscaneamento.NaoAbre) {
            setContent { EscaneamentoNaoAbreScreen(motivo = abertura.motivo, onVoltar = ::finish) }
            return
        }

        val aberta = abertura as AberturaDoEscaneamento.Abre
        val organizacao = aberta.organizacao
        val shortId = aberta.prova

        examPackage = aberta.pacote
        // O roster e lido pela **mesma chave que os escritores usaram** — o `short_id` da prova,
        // que chega pelo `Intent`. A primeira versao lia por `examPackage.meta.examId`, com a
        // justificativa de evitar "dois caminhos para dizer de qual prova se fala"; a justificativa
        // estava certa e a leitura, errada, porque os dois caminhos ja existiam: os escritores usam
        // `prova.shortId`. Fica dito em vez de apagado.
        //
        // Lido uma vez, aqui, e nao a cada quadro: o escaneamento nao muda o roster, e reler a cada
        // folha poria disco no caminho da camera sem nada a ganhar.
        roster = RostersEmArquivo(File(filesDir, "rosters")).ler(organizacao, shortId)
        map = examPackage.layout.values.single()
        session = ScanSession(examPackage)
        // A fila do outbox. Aberta aqui e nao no `Application` porque e aqui que ela e usada, e a
        // organizacao e a prova ja estao resolvidas neste ponto.
        pendentes = ResultadosEmRoom(ResultadosEmRoom.abrir(applicationContext).pendentes())
        this.organizacao = organizacao
        this.prova = shortId
        analysisExecutor = Executors.newSingleThreadExecutor()

        setContent {
            ScanScreen(
                state = state,
                roster = roster,
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
        // A recusa por falta de pacote retorna antes de o executor existir; `isInitialized` evita
        // que o caminho de recusa estoure ao fechar a tela.
        if (::analysisExecutor.isInitialized) analysisExecutor.shutdown()
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
                            val apuracao = session.onFrame(resultado)
                            state = session.state
                            if (apuracao != null) gravar(apuracao)
                        }
                    },
                ),
            )

            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analise)
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Grava a correcao apurada, **antes de qualquer rede**.
     *
     * **A escrita nao acontece aqui, e nao acontece no fio principal.** Ela vai para
     * [gravarEAgendar], fora da `Activity`, porque o Room recusa acesso ao banco no fio principal e
     * este metodo e chamado de dentro do laco da camera. A primeira versao gravava direto, e o
     * aplicativo morria ao escanear uma folha valida — o conserto e a funcao, e nao um
     * `allowMainThreadQueries` que desligaria a trava em vez de respeita-la.
     *
     * **O `captureId` e cunhado aqui, uma vez por captura.** Ele nao e derivado do conteudo: uma
     * recaptura que desse exatamente a mesma nota e recaptura, e nao reenvio, e um identificador
     * derivado do conteudo as confundiria. Cunha-lo aqui, e nao dentro da corrotina, mantem a
     * identidade presa ao instante da captura e nao ao da gravacao.
     *
     * **Token vazio vira nulo.** O QR da folha avulsa traz o campo vazio (§8), e o servidor espera
     * ausencia — vazio faria todas as avulsas da mesma prova colidirem no unique de revisao.
     *
     * **Os dois `?: return` que estavam aqui sairam, e nao foram substituidos por tratamento.** Eles
     * descartavam uma correcao apurada em silencio: folha medida, nota na tela, nada gravado, nada
     * agendado (achado 3.3). Tratar o nulo aqui manteria construivel um estado que nao deveria
     * existir, e a mensagem teria de explicar, com a folha na mao e a nota na tela, algo que so podia
     * ter sido decidido antes de a camera abrir. Quem decide e [decidirAbertura]; aqui os dois
     * valores existem por construcao.
     */
    private fun gravar(apuracao: ApuracaoNova) {
        val resultado = ResultadoPendente(
            captureId = UUID.randomUUID().toString(),
            organizacao = organizacao,
            prova = prova,
            studentToken = apuracao.reading.payload.studentToken.ifEmpty { null },
            apuradoEm = System.currentTimeMillis(),
            nota = apuracao.score,
        )

        lifecycleScope.gravarEAgendar(pendentes, resultado) {
            EnvioDeResultadosWorker.agendar(applicationContext, organizacao)
        }
    }

    companion object {
        /** A organizacao sob a qual o pacote esta guardado. */
        const val EXTRA_ORGANIZACAO = "com.platos.android.scan.ORGANIZACAO"

        /**
         * O `short_id` da prova escolhida, que e a chave sob a qual o roster foi guardado.
         *
         * **Vem no `Intent`, e nao de `examPackage.meta.examId`.** Os dois sao iguais hoje, mas por
         * um contrato implicito entre a publicacao e o pacote que nada nesta base prende: os
         * escritores do roster — o pull e o gate — usam `prova.shortId`, e ler por outro caminho
         * faria o leitor depender de uma igualdade que ninguem afirma. Se ela se rompesse, `ler`
         * devolveria `null` e **toda** folha cairia em silencio no token com "nao esta no roster" —
         * sem erro, sem barragem, e com a fatia inteira desaparecida sem nada acusar.
         */
        const val EXTRA_SHORT_ID = "com.platos.android.scan.SHORT_ID"

        /** O endereco do pacote conferido. **O endereco, e nao o pacote** — ver `design.md`, 6. */
        const val EXTRA_CONTENT_HASH = "com.platos.android.scan.CONTENT_HASH"

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
