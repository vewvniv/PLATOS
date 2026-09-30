package com.platos.android.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.android.pacote.PacotesEmArquivo
import com.platos.domain.exam.ExamPackage
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O escaneamento aberto **de verdade**, com um caderno guardado
 * (`o-caderno-e-lido-fora-do-fio-principal`, tarefa 1.1).
 *
 * Nenhum teste lancava a [ScanActivity]: os que a citam usam so as funcoes dela. A leitura do
 * caderno que ela faz no `onCreate` so foi exercitada, ate aqui, por um caminho que nao e o de
 * producao (P16: suite vizinha verde nao verifica esta camada). Este teste e a camada.
 *
 * **O que ele observa, e o que isso atravessa.** A Activity e lancada por
 * `Instrumentation.startActivitySync`, com o pacote guardado em `filesDir/packages` e o caderno
 * guardado em `caderno.db`, como a producao os deixa. O sinal de que o caderno foi **retomado** e o
 * texto "Prova com discursiva" na arvore de acessibilidade da janela: sem caderno guardado, a sessao
 * comeca em `Searching` e a tela diz "Procurando a folha…". O sinal de que a Activity **nao caiu** e
 * o estado do ciclo de vida depois da leitura — e, se o processo morrer, o instrumento o reporta.
 *
 * **Sem `ActivityScenario`**, que nao esta nas dependencias de teste; e sem `GrantPermissionRule`,
 * pela mesma razao: a permissao de camera e concedida por `UiAutomation`.
 */
@RunWith(AndroidJUnit4::class)
class AbrirEscaneamentoComCadernoInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val contexto = instrumentation.targetContext
    private val organizacao = "01a06ba4-cb43-7d97-842d-165352d010b5"
    private val shortId = "prova-discursiva"

    private var permissaoConcedida = false

    /**
     * `-Pandroid.testInstrumentationRunnerArguments.permissaoManual=true`: quando a concessao por
     * `UiAutomation` e recusada (Xiaomi, Android 16), o teste abre a Activity e **espera o professor
     * tocar em "Permitir"** no dialogo do sistema, em vez de pular. Desligado por padrao: a suite
     * normal nao pode ficar parada esperando uma pessoa.
     */
    private val permissaoManual =
        InstrumentationRegistry.getArguments().getString("permissaoManual") == "true"

    private fun temPermissao() =
        ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private val bytes: ByteArray by lazy {
        instrumentation.context.assets.open("prova-discursiva.package.json").use { it.readBytes() }
    }

    // Conferido pelo `MessageDigest` da JVM, e nao por `Sha256` do dominio: o oraculo nao
    // compartilha codigo com o que julga (P4).
    private val hash: String by lazy {
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private val pacote: ExamPackage by lazy {
        Json { ignoreUnknownKeys = false }.decodeFromString(ExamPackage.serializer(), bytes.decodeToString())
    }

    private fun umCaderno() = Caderno(
        aluno = "tok-a",
        regioes = listOf(
            RegiaoDoCaderno(0, gabarito = true, rotulo = Caderno.ROTULO_GABARITO, estado = EstadoDaRegiao.Capturada),
            RegiaoDoCaderno(1, gabarito = false, rotulo = "1", estado = EstadoDaRegiao.NaoVista),
        ),
        parcial = null,
    )

    @Before
    fun preparar() {
        CadernosEmRoom.reiniciarParaTeste()
        contexto.deleteDatabase("caderno.db")
        // Tentada, e nao obrigatoria. Medido em 2026-09-30 no 2511FPC34G (Android 16, Xiaomi): sem a
        // opcao de desenvolvedor "Depuracao USB (configuracoes de seguranca)" efetiva, o sistema
        // recusa a concessao (`pm grant` tambem). O defeito que este teste cobre acontece no
        // `onCreate`, antes de qualquer camera, e por isso os testes que so exigem sobreviver rodam
        // com ou sem permissao; os que leem a tela do caderno retomado exigem a permissao e sao
        // PULADOS sem ela, com o motivo — e nao dados como verdes.
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
     * Termina a Activity e **espera ela ser destruida** antes de o teste fechar o banco. Ao terminar,
     * a Activity guarda o caderno no `onStop` (`Dispatchers.IO`, `NonCancellable`); fechar o banco no
     * `@After` no meio disso derrubou o processo com `Database is closed` — falha do teste, e nao da
     * Activity. A espera curta depois de destruida cobre a gravacao, que nao tem como ser observada
     * daqui.
     */
    private fun encerrar(atividade: ScanActivity) {
        instrumentation.runOnMainSync { atividade.finish() }
        val limite = System.currentTimeMillis() + 5_000
        while (!atividade.isDestroyed && System.currentTimeMillis() < limite) Thread.sleep(100)
        Thread.sleep(1_000)
    }

    /** Pula o teste, com o motivo, quando o aparelho nao deixou conceder a camera. */
    private fun exigirPermissao() = Assume.assumeTrue(
        "permissao de camera nao concedida ao teste (Xiaomi: 'Depuracao USB (configuracoes de " +
            "seguranca)'); a tela do caderno retomado nao pode ser lida sem ela. Para tocar em Permitir " +
            "a mao: -Pandroid.testInstrumentationRunnerArguments.permissaoManual=true",
        permissaoConcedida || permissaoManual,
    )

    /**
     * No modo manual, espera o toque em "Permitir" (ate 2 minutos). Chamado **depois** de abrir a
     * Activity, porque e ela que pede a permissao. Com a permissao ja concedida, nao espera nada.
     */
    private fun aguardarPermissaoManual() {
        if (temPermissao()) return
        Log.w("AbrirEscaneamentoTeste", ">>> TOQUE EM 'PERMITIR' NO DIALOGO DE CAMERA DO APARELHO <<<")
        val limite = System.currentTimeMillis() + 120_000
        while (!temPermissao() && System.currentTimeMillis() < limite) Thread.sleep(500)
        assertTrue("o toque em Permitir nao veio em 2 minutos", temPermissao())
        // A Activity recebe o resultado e liga a camera; o teste espera a tela, nao um tempo fixo.
    }

    /** Todo texto da janela ativa, pela arvore de acessibilidade. Vazio enquanto ela nao existe. */
    private fun textosDaJanela(): List<String> {
        val raiz = instrumentation.uiAutomation.rootInActiveWindow ?: return emptyList()
        val achados = mutableListOf<String>()
        fun percorre(no: AccessibilityNodeInfo) {
            no.text?.let { achados += it.toString() }
            no.contentDescription?.let { achados += it.toString() }
            for (i in 0 until no.childCount) no.getChild(i)?.let(::percorre)
        }
        percorre(raiz)
        return achados
    }

    /**
     * Espera a tela mostrar algum dos textos, ou o prazo acabar. O prazo e de tolerancia do
     * instrumento (a tela aparece depois de uma leitura de banco e de uma composicao), e nao critério
     * de aprovacao: o que reprova e o texto certo **nao** aparecer.
     */
    private fun esperarTexto(vararg fragmentos: String, ateMs: Long = 10_000): List<String> {
        val limite = System.currentTimeMillis() + ateMs
        var vistos = textosDaJanela()
        while (System.currentTimeMillis() < limite && vistos.none { t -> fragmentos.any { t.contains(it) } }) {
            Thread.sleep(200)
            vistos = textosDaJanela()
        }
        return vistos
    }

    @Test
    fun abrir_o_escaneamento_com_caderno_guardado_nao_cai_e_retoma_o_caderno() {
        exigirPermissao()
        // O aluno do caderno e o mesmo de outra leitura do pacote; a chave e a que a Activity le.
        CadernosEmRoom(CadernosEmRoom.abrir(contexto).cadernos())
            .guardar(organizacao, pacote.meta.examId, umCaderno())
        CadernosEmRoom.reiniciarParaTeste()

        val atividade = abrir()
        aguardarPermissaoManual()
        val vistos = esperarTexto("Prova com discursiva", "Procurando a folha")

        assertTrue(
            "a Activity nao chegou a RESUMED / nao sobreviveu a leitura: ${atividade.lifecycle.currentState}",
            atividade.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && !atividade.isFinishing,
        )
        assertTrue(
            "a tela nao mostrou o caderno retomado ('Prova com discursiva'); textos vistos: $vistos",
            vistos.any { it.contains("Prova com discursiva") },
        )

        encerrar(atividade)
    }

    @Test
    fun abrir_o_escaneamento_sem_caderno_guardado_nao_cai_e_procura_a_folha() {
        exigirPermissao()
        val atividade = abrir()
        aguardarPermissaoManual()
        val vistos = esperarTexto("Prova com discursiva", "Procurando a folha")

        assertEquals(Lifecycle.State.RESUMED, atividade.lifecycle.currentState)
        assertTrue(
            "a tela nao mostrou 'Procurando a folha'; textos vistos: $vistos",
            vistos.any { it.contains("Procurando a folha") },
        )
        // Guarda de vacuidade do teste de "recusa nao consulta o Room": aqui a leitura rodou, e o
        // arquivo do banco existe. Se ele nunca existisse, a ausencia conferida la nao provaria nada.
        assertTrue(
            "o banco do caderno deveria existir depois de uma abertura que le o caderno",
            contexto.getDatabasePath("caderno.db").exists(),
        )

        encerrar(atividade)
    }

    /**
     * A recusa de abertura (`NaoAbre`) continua imediata e **sem leitura**: o Room nem chega a ser
     * consultado. O arquivo do banco so nasce na primeira consulta, e a guarda de vacuidade esta no
     * teste sem caderno, onde ele existe depois de uma abertura que le.
     */
    @Test
    fun recusar_a_abertura_nao_consulta_o_room() {
        val intent = Intent(contexto, ScanActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(ScanActivity.EXTRA_ORGANIZACAO, organizacao)
            .putExtra(ScanActivity.EXTRA_CONTENT_HASH, "a".repeat(64))
            .putExtra(ScanActivity.EXTRA_SHORT_ID, shortId)
        val atividade = instrumentation.startActivitySync(intent) as ScanActivity
        val frase = frasePara(MotivoDeNaoAbrir.PACOTE_NAO_CONFERIDO)

        val vistos = esperarTexto(frase)

        assertTrue("a tela nao mostrou a recusa; textos vistos: $vistos", vistos.any { it.contains(frase) })
        assertTrue(
            "a recusa consultou o Room: o banco do caderno existe",
            !contexto.getDatabasePath("caderno.db").exists(),
        )

        encerrar(atividade)
    }

    /**
     * Activity destruida logo depois de aberta: o processo nao cai. **Nao forca** o cancelamento no
     * meio da leitura — a leitura e rapida, e se ela termina antes do `finish` o ramo nao e
     * exercido. O ramo de cancelamento tem a prova propria em
     * `LerCadernoNoFioPrincipalInstrumentedTest.escopo_cancelado_no_meio_da_leitura_cancela_a_espera`;
     * aqui fica o que so a Activity mostra: que destruir cedo nao derruba o processo de teste.
     */
    @Test
    fun destruir_a_activity_logo_depois_de_aberta_nao_derruba_o_processo() {
        val atividade = abrir()
        instrumentation.runOnMainSync { atividade.finish() }

        val limite = System.currentTimeMillis() + 5_000
        while (!atividade.isDestroyed && System.currentTimeMillis() < limite) Thread.sleep(100)

        assertTrue("a Activity nao foi destruida", atividade.isDestroyed)
        // Deixa a leitura, se ainda estiver pendente, terminar contra uma Activity morta.
        Thread.sleep(1_500)
    }
}
