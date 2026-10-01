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
import com.platos.android.vision.FrameOutcome
import com.platos.android.vision.RegiaoDiscursivaNoQuadro
import com.platos.domain.capture.CapturePayload
import com.platos.android.vision.PngDaResposta
import com.platos.android.omr.RectifiedRegion
import com.platos.domain.exam.ExamPackage
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        val gravada = RespostasEmArquivo(pasta).gravar(PngDaResposta.codificar(regiao), System.currentTimeMillis() - 86_400_000L, false, 0)
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

    // --- 3.2: a Activity retoma o caderno pela via normalizada, e a retomada atravessa o fechamento ---

    /** Fecha o processo no que importa ao caderno: a Activity se encerra (guarda no `onStop`) e as instancias reiniciam. */
    private fun fecharOProcesso(atividade: ScanActivity) {
        encerrar(atividade)
        CadernosEmRoom.reiniciarParaTeste()
    }

    /** Cenarios "O caderno guardado sobrevive ao fechamento" e "A resposta..." de ponta a ponta, pela Activity real. */
    @Test
    fun reabrir_a_activity_retoma_as_mesmas_regioes_respostas_e_contador() {
        val original = cadernoComRespostaNaRegiao1(umaRespostaEmDisco())
        CadernosEmRoom(CadernosEmRoom.abrir(contexto).cadernos()).guardar(organizacao, pacote.meta.examId, original)
        CadernosEmRoom.reiniciarParaTeste()

        comAtividade { atividade ->
            assertTrue("o caderno nao foi retomado", esperar { atividade.instantaneoDoCaderno != null })
            assertEquals(original, atividade.instantaneoDoCaderno)
            assertEquals(2, atividade.instantaneoDoCaderno!!.capturadas)
        }
    }

    /** Cenario "A resposta referenciada nao existe mais": a Activity abre, e a regiao volta a nao vista. */
    @Test
    fun reabrir_com_a_resposta_apagada_retoma_a_regiao_como_nao_vista_e_a_activity_abre() {
        val resposta = umaRespostaEmDisco()
        CadernosEmRoom(CadernosEmRoom.abrir(contexto).cadernos())
            .guardar(organizacao, pacote.meta.examId, cadernoComRespostaNaRegiao1(resposta))
        CadernosEmRoom.reiniciarParaTeste()
        File(pasta, resposta.arquivo).delete()

        comAtividade { atividade ->
            assertTrue("o caderno nao foi retomado", esperar { atividade.instantaneoDoCaderno != null })
            val d1 = atividade.instantaneoDoCaderno!!.regioes.single { it.regionIndex == 1 }
            assertEquals(EstadoDaRegiao.NaoVista, d1.estado)
            assertEquals(null, d1.resposta)
            assertEquals(1, atividade.instantaneoDoCaderno!!.capturadas)
            // STARTED, e nao RESUMED: sem a permissao de camera o dialogo do sistema fica na frente e a
            // Activity fica pausada (medido no 2511FPC34G: "STARTED"). Sobreviver e o que se afirma.
            assertTrue(
                "a Activity nao sobreviveu: ${atividade.lifecycle.currentState}",
                atividade.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) &&
                    !atividade.isFinishing && !atividade.isDestroyed,
            )
        }
    }

    /**
     * Cenario "Trocar de aluno antes de fechar continua substituindo o caderno": o segundo aluno, so ele,
     * volta. Le a sessao real (a folha so entra com a camera permitida), portanto e pulado sem a permissao.
     */
    @Test
    fun trocar_de_aluno_antes_de_fechar_e_reabrir_retoma_so_o_segundo() {
        Assume.assumeTrue(
            "permissao de camera nao concedida ao teste; para tocar em Permitir a mao: " +
                "-Pandroid.testInstrumentationRunnerArguments.permissaoManual=true",
            permissaoConcedida || permissaoManual,
        )
        val primeiro = cadernoComRespostaNaRegiao1(umaRespostaEmDisco())
        CadernosEmRoom(CadernosEmRoom.abrir(contexto).cadernos()).guardar(organizacao, pacote.meta.examId, primeiro)
        CadernosEmRoom.reiniciarParaTeste()
        lateinit var respostaDeB: RespostaGuardada

        val atividade = abrir()
        try {
            aguardarPermissaoManual()
            assertTrue("o caderno nao foi retomado", esperar { atividade.instantaneoDoCaderno?.aluno == "tok-a" })
            // Gravada **depois** da abertura, como o analisador a grava: antes dela seria um orfao, e a
            // varredura da abertura (corretamente) a eliminaria.
            respostaDeB = umaRespostaEmDisco()
            val d2DeB = RegiaoDiscursivaNoQuadro.Reconhecida(2, "d2", CapturePayload(pacote.meta.examId, "tok-b", "v1", 2))
            atividade.entregarQuadro(
                QuadroAnalisado(FrameOutcome.SoDiscursivas(listOf(d2DeB)), mapOf(2 to RespostaDoQuadro.Guardada(respostaDeB))),
            )
            assertTrue("a folha de tok-b nao trocou o caderno", esperar { atividade.instantaneoDoCaderno?.aluno == "tok-b" })
        } finally {
            fecharOProcesso(atividade)
        }

        comAtividade { reaberta ->
            assertTrue("o caderno nao foi retomado", esperar { reaberta.instantaneoDoCaderno != null })
            val retomado = reaberta.instantaneoDoCaderno!!
            assertEquals("tok-b", retomado.aluno)
            assertNull("a resposta do primeiro aluno nao volta", retomado.regioes.single { it.regionIndex == 1 }.resposta)
            assertEquals(respostaDeB, retomado.regioes.single { it.regionIndex == 2 }.resposta)
        }
    }

    // --- 4.2: a varredura roda antes da leitura do caderno, e antes do primeiro quadro ---

    private val dia = 86_400_000L

    /** Uma resposta de verdade em `respostas/`, capturada ha [diasAtras] dias. */
    private fun respostaDeHa(diasAtras: Long): RespostaGuardada {
        val gravada = RespostasEmArquivo(pasta).gravar(byteArrayOf(1, 2, 3), System.currentTimeMillis() - diasAtras * dia, false, 0)
        return (gravada as RespostaDoQuadro.Guardada).resposta
    }

    private fun cadernoCom(r1: RespostaGuardada, r2: RespostaGuardada) = Caderno(
        aluno = FolhaDiscursivaRenderizada.TOKEN,
        regioes = listOf(
            RegiaoDoCaderno(0, gabarito = true, rotulo = Caderno.ROTULO_GABARITO, estado = EstadoDaRegiao.Capturada),
            RegiaoDoCaderno(1, gabarito = false, rotulo = "1", estado = EstadoDaRegiao.Capturada, resposta = r1),
            RegiaoDoCaderno(2, gabarito = false, rotulo = "2", estado = EstadoDaRegiao.Capturada, resposta = r2),
        ),
        parcial = null,
    )

    private fun guardarCaderno(caderno: Caderno) {
        CadernosEmRoom(CadernosEmRoom.abrir(contexto).cadernos()).guardar(organizacao, pacote.meta.examId, caderno)
        CadernosEmRoom.reiniciarParaTeste()
    }

    /**
     * Cenarios "A eliminacao roda antes da camera", "Resposta com 31 dias", "Arquivo que nenhum caderno
     * referencia" e "...temporario": com vencida, orfa, temporario e valida no disco, abrir o escaneamento
     * elimina as tres primeiras e mantem a valida, e **a regiao da vencida volta nao vista no caderno
     * retomado** — antes de qualquer quadro, que este teste nem chega a mandar.
     */
    @Test
    fun abrir_o_escaneamento_elimina_vencida_orfa_e_temporario_e_mantem_a_valida() {
        val vencida = respostaDeHa(31)
        val valida = respostaDeHa(5)
        File(pasta, "orfao.png").writeBytes(byteArrayOf(9))
        File(pasta, "interrompida.png.tmp").writeBytes(byteArrayOf(9))
        guardarCaderno(cadernoCom(vencida, valida))
        assertEquals("guarda de vacuidade: as quatro estao no disco", 4, pasta.list()!!.size)

        comAtividade { atividade ->
            assertTrue("o caderno nao foi retomado", esperar { atividade.instantaneoDoCaderno != null })

            assertEquals(listOf(valida.arquivo), pasta.list()!!.sorted())
            val retomado = atividade.instantaneoDoCaderno!!
            assertEquals(EstadoDaRegiao.NaoVista, retomado.regioes.single { it.regionIndex == 1 }.estado)
            assertEquals(null, retomado.regioes.single { it.regionIndex == 1 }.resposta)
            assertEquals(EstadoDaRegiao.Capturada, retomado.regioes.single { it.regionIndex == 2 }.estado)
            assertEquals(valida, retomado.regioes.single { it.regionIndex == 2 }.resposta)
        }
    }

    /**
     * Cenario "Eliminacao que falha nao impede o escaneamento": um `.tmp` que e um **diretorio nao vazio**
     * nao se elimina (`DirectoryNotEmptyException`). A Activity abre, o caderno e retomado, os outros
     * vencidos somem, e o teimoso continua no aparelho.
     */
    @Test
    fun uma_eliminacao_que_falha_nao_impede_o_escaneamento_de_abrir() {
        val vencida = respostaDeHa(40)
        val valida = respostaDeHa(1)
        val teimoso = File(pasta, "teimoso.png.tmp").apply { mkdirs(); File(this, "dentro").writeBytes(byteArrayOf(1)) }
        File(pasta, "orfao.png").writeBytes(byteArrayOf(9))
        guardarCaderno(cadernoCom(vencida, valida))

        comAtividade { atividade ->
            assertTrue("o caderno nao foi retomado", esperar { atividade.instantaneoDoCaderno != null })

            assertTrue("a Activity nao abriu", !atividade.isFinishing && !atividade.isDestroyed)
            assertEquals(listOf(teimoso.name, valida.arquivo).sorted(), pasta.list()!!.sorted())
            assertEquals(valida, atividade.instantaneoDoCaderno!!.regioes.single { it.regionIndex == 2 }.resposta)
        }
    }

    // --- 4.3: as duas ordens de queda ---

    /**
     * Queda (a): o processo termina **depois de gravar o arquivo e antes de `onStop` guardar o caderno**. Em
     * disco, o caderno e o anterior e consistente (a regiao 1 nao vista), e o arquivo gravado nao e
     * referenciado por ninguem. Na abertura seguinte o arquivo e orfao e some, e o caderno retomado e o
     * anterior. A queda e simulada pelo estado que ela deixa: o arquivo no disco e o caderno antigo no Room.
     */
    @Test
    fun queda_depois_de_gravar_e_antes_de_guardar_o_caderno_o_arquivo_some_e_o_caderno_e_o_anterior() {
        val anterior = Caderno(
            aluno = FolhaDiscursivaRenderizada.TOKEN,
            regioes = listOf(
                RegiaoDoCaderno(0, gabarito = true, rotulo = Caderno.ROTULO_GABARITO, estado = EstadoDaRegiao.Capturada),
                RegiaoDoCaderno(1, gabarito = false, rotulo = "1", estado = EstadoDaRegiao.NaoVista),
            ),
            parcial = null,
        )
        CadernosEmRoom(CadernosEmRoom.abrir(contexto).cadernos()).guardar(organizacao, pacote.meta.examId, anterior)
        CadernosEmRoom.reiniciarParaTeste()
        val gravadaSemReferencia = umaRespostaEmDisco()
        assertTrue("guarda de vacuidade: o arquivo esta no disco", File(pasta, gravadaSemReferencia.arquivo).isFile)

        comAtividade { atividade ->
            assertTrue("o caderno nao foi retomado", esperar { atividade.instantaneoDoCaderno != null })

            assertEquals(anterior, atividade.instantaneoDoCaderno)
            assertEquals(emptyList<String>(), pasta.list()!!.toList())
        }
    }

    /**
     * Queda (b): o professor refaz, o arquivo e eliminado na hora, e o processo termina **antes de o caderno
     * ser guardado**. Em disco, o caderno ainda referencia o arquivo que nao existe mais; ele e lido com a
     * regiao nao vista. (Mesmo estado que [reabrir_com_a_resposta_apagada_retoma_a_regiao_como_nao_vista_e_a_activity_abre];
     * o nome aqui diz de qual queda se trata.)
     */
    @Test
    fun queda_depois_de_refazer_e_antes_de_guardar_o_caderno_le_a_regiao_como_nao_vista() {
        val resposta = umaRespostaEmDisco()
        CadernosEmRoom(CadernosEmRoom.abrir(contexto).cadernos())
            .guardar(organizacao, pacote.meta.examId, cadernoComRespostaNaRegiao1(resposta))
        CadernosEmRoom.reiniciarParaTeste()
        RespostasEmArquivo(pasta).eliminar(resposta.arquivo)

        comAtividade { atividade ->
            assertTrue("o caderno nao foi retomado", esperar { atividade.instantaneoDoCaderno != null })

            val d1 = atividade.instantaneoDoCaderno!!.regioes.single { it.regionIndex == 1 }
            assertEquals(EstadoDaRegiao.NaoVista, d1.estado)
            assertNull(d1.resposta)
        }
    }

    // --- 4.4: refazer elimina na hora, e a varredura e a rede ---

    /** Cenario "Refazer a resposta", pela Activity real: o arquivo some na hora e a regiao volta a nao vista. */
    @Test
    fun refazer_pela_activity_elimina_o_arquivo_e_devolve_a_regiao_a_nao_vista() {
        val resposta = umaRespostaEmDisco()
        CadernosEmRoom(CadernosEmRoom.abrir(contexto).cadernos())
            .guardar(organizacao, pacote.meta.examId, cadernoComRespostaNaRegiao1(resposta))
        CadernosEmRoom.reiniciarParaTeste()

        comAtividade { atividade ->
            assertTrue("o caderno nao foi retomado", esperar { atividade.instantaneoDoCaderno != null })
            assertTrue("guarda de vacuidade: o arquivo esta no disco", File(pasta, resposta.arquivo).isFile)

            instrumentation.runOnMainSync { atividade.refazerResposta(1) }

            assertTrue("o arquivo nao foi eliminado na hora", esperar { !File(pasta, resposta.arquivo).exists() })
            val d1 = atividade.instantaneoDoCaderno!!.regioes.single { it.regionIndex == 1 }
            assertEquals(EstadoDaRegiao.NaoVista, d1.estado)
            assertNull(d1.resposta)
            assertEquals(1, atividade.instantaneoDoCaderno!!.capturadas)
        }
    }

    /**
     * Cenario "Refazer elimina o arquivo na hora, e a eliminacao e a rede", com a eliminacao imediata
     * **desligada de proposito**: a sessao refaz, o arquivo NAO e eliminado, o caderno e guardado, o processo
     * reinicia. Ninguem mais o referencia, e a abertura seguinte o elimina.
     */
    @Test
    fun com_a_eliminacao_imediata_desligada_a_varredura_da_abertura_elimina_o_arquivo() {
        val resposta = umaRespostaEmDisco()
        val sessao = ScanSession(pacote, cadernoInicial = cadernoComRespostaNaRegiao1(resposta))
        sessao.refazer(1)
        CadernosEmRoom(CadernosEmRoom.abrir(contexto).cadernos())
            .guardar(organizacao, pacote.meta.examId, requireNotNull(sessao.cadernoAtual))
        CadernosEmRoom.reiniciarParaTeste()
        assertTrue("guarda de vacuidade: o arquivo continua no disco", File(pasta, resposta.arquivo).isFile)

        comAtividade { atividade ->
            assertTrue("o caderno nao foi retomado", esperar { atividade.instantaneoDoCaderno != null })

            assertEquals(emptyList<String>(), pasta.list()!!.toList())
            assertEquals(EstadoDaRegiao.NaoVista, atividade.instantaneoDoCaderno!!.regioes.single { it.regionIndex == 1 }.estado)
        }
    }
}
