package com.platos.android.corpus

import com.platos.android.scan.RespostaDoQuadro
import com.platos.android.scan.RespostasGuardadas
import com.platos.domain.scoring.Pontos
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * A coleta de depuracao no disco (`slice-5d-corpus-de-medicao`, spec `measurement-corpus`). Roda so no
 * `testDebugUnitTest`: a classe nao existe no release.
 */
class ColetaDoCorpusEmArquivoTest {

    private lateinit var raiz: File
    private val dia = 86_400_000L
    private val agora = 100 * dia
    private val png = byteArrayOf(1, 2, 3, 4, 5)

    @BeforeEach
    fun preparar() {
        raiz = Files.createTempDirectory("coleta").toFile()
    }

    @AfterEach
    fun limpar() {
        raiz.deleteRecursively()
    }

    private class Respostas(private val arquivos: Map<String, ByteArray>) : RespostasGuardadas {
        override fun gravar(png: ByteArray, capturadaEm: Long, desvioSinalizado: Boolean, foraPpm: Int): RespostaDoQuadro =
            RespostaDoQuadro.Recusada("falso")

        override fun ler(arquivo: String): ByteArray? = arquivos[arquivo]
        override fun existe(arquivo: String) = arquivo in arquivos
        override fun listar() = arquivos.keys.sorted()
        override fun eliminar(arquivo: String) = Unit
    }

    private val respostas = Respostas(mapOf("origem-111.png" to byteArrayOf(1, 2, 3, 4, 5), "origem-222.png" to byteArrayOf(9, 9)))

    private fun amostra(arquivo: String = "origem-111.png", rotulo: String = "1", pontos: String = "1.5", maximo: Int = 2) =
        AmostraACopiar(arquivo, rotulo, Pontos.parse(pontos), maximo, "hash-de-exemplo", "d1")

    private fun coleta(escrever: (File, ByteArray) -> Unit = { f, b -> f.writeBytes(b) }) =
        ColetaDoCorpusEmArquivo(raiz, escrever)

    private val corpus get() = File(raiz, "corpus")

    private fun nomes() = corpus.list()?.sorted().orEmpty()

    private fun aosDias(n: Long) = agora - n * dia

    private fun copiarUma(): String = coleta().copiar(listOf(amostra()), respostas).ids.single()

    private fun envelhecer(id: String, ate: Long) {
        assertTrue(File(corpus, "$id.json").setLastModified(ate))
    }

    // --- o interruptor ---

    @Test
    fun `o interruptor vem desligado e liga com o arquivo marcador`() {
        assertFalse(coleta().ligada())

        File(raiz, ColetaDoCorpusEmArquivo.MARCADOR).writeText("")

        assertTrue(coleta().ligada())
    }

    // --- a amostra ---

    @Test
    fun `copiar grava a foto com os mesmos bytes e o arquivo de dados do formato`() {
        val r = coleta().copiar(listOf(amostra()), respostas)

        val id = r.ids.single()
        assertEquals(emptyList<String>(), r.falhas)
        assertEquals(listOf("$id.json", "$id.png"), nomes())
        assertEquals(png.toList(), File(corpus, "$id.png").readBytes().toList())
        assertEquals(
            """{"versao":1,"pontos":"1.50","maximo":2,"pacote":"hash-de-exemplo","item":"d1","referencia":null,"descartar":false}""",
            File(corpus, "$id.json").readText(),
        )
    }

    @Test
    fun `o formato bate com o literal do repositorio`() {
        val esperado = File(
            System.getProperty("platos.fixtures") ?: error("propriedade `platos.fixtures` nao definida pelo build"),
            "corpus/amostra-exemplo.json",
        ).readText().trim()

        val dado = AmostraDoCorpus.json.encodeToString(
            AmostraDoCorpus.serializer(),
            AmostraDoCorpus(pontos = "1.50", maximo = 2, pacote = "hash-de-exemplo", item = "d1"),
        )

        assertEquals(esperado, dado)
    }

    @Test
    fun `a pontuacao zero e copiada como zero`() {
        val id = coleta().copiar(listOf(amostra(pontos = "0")), respostas).ids.single()

        assertTrue("\"pontos\":\"0.00\"" in File(corpus, "$id.json").readText())
    }

    @Test
    fun `o nome nao deriva do arquivo de origem e nada da origem entra na amostra`() {
        val id = coleta().copiar(listOf(amostra(arquivo = "origem-111.png")), respostas).ids.single()

        for (nome in nomes()) assertFalse("origem" in nome, "o nome '$nome' deriva da origem")
        assertFalse("origem" in File(corpus, "$id.json").readText())
        assertFalse("respostas" in File(corpus, "$id.json").readText())
    }

    @Test
    fun `dois identificadores aleatorios e independentes`() {
        val r = coleta().copiar(listOf(amostra(), amostra(arquivo = "origem-222.png", rotulo = "2")), respostas)

        val (a, b) = r.ids
        assertNotEquals(a, b)
        assertEquals(4, UUID.fromString(a).version())
        assertEquals(4, UUID.fromString(b).version())
    }

    @Test
    fun `foto que nao existe vira falha com o rotulo, sem deixar nada`() {
        val r = coleta().copiar(listOf(amostra(arquivo = "sumiu.png", rotulo = "7")), respostas)

        assertEquals(emptyList<String>(), r.ids)
        assertEquals(listOf("7"), r.falhas)
        assertEquals(emptyList<String>(), nomes())
    }

    @Test
    fun `disco cheio no meio nao deixa foto sem dados nem temporario, e as outras seguem`() {
        var escritasDeDados = 0
        val coleta = coleta(escrever = { arquivo, bytes ->
            // Falha na escrita dos dados da SEGUNDA amostra, depois de a foto dela ja ter sido escrita.
            if (arquivo.name.endsWith(".json.tmp") && ++escritasDeDados == 2) throw IOException("disco cheio")
            arquivo.writeBytes(bytes)
        })

        val r = coleta.copiar(listOf(amostra(rotulo = "1"), amostra(arquivo = "origem-222.png", rotulo = "2")), respostas)

        assertEquals(listOf("2"), r.falhas, "so a segunda falha, na hora de escrever os dados")
        assertEquals(1, r.ids.size)
        val sobrou = nomes().filter { it.endsWith(".tmp") || (it.endsWith(".png") && "${it.removeSuffix(".png")}.json" !in nomes()) }
        assertEquals(emptyList<String>(), sobrou, "a falha deixou residuo")
    }

    // --- eliminar ---

    @Test
    fun `eliminar remove a foto e os dados dos ids`() {
        val coleta = coleta()
        val (a, b) = coleta.copiar(listOf(amostra(), amostra(arquivo = "origem-222.png", rotulo = "2")), respostas).ids

        coleta.eliminar(listOf(a))

        assertEquals(listOf("$b.json", "$b.png"), nomes())
    }

    @Test
    fun `eliminar sem pasta nao lanca`() {
        coleta().eliminar(listOf("qualquer"))
        coleta().eliminarVencidas(agora)
        coleta().eliminarTodas()
    }

    // --- o prazo ---

    @Test
    fun `29 dias mantem, exatamente 30 elimina, 31 elimina`() {
        val coleta = coleta()
        val mantida = copiarUma().also { envelhecer(it, aosDias(29)) }
        val noLimite = copiarUma().also { envelhecer(it, aosDias(30)) }
        val vencida = copiarUma().also { envelhecer(it, aosDias(31)) }

        coleta.eliminarVencidas(agora)

        assertEquals(listOf("$mantida.json", "$mantida.png"), nomes())
        assertFalse(File(corpus, "$noLimite.png").exists() || File(corpus, "$noLimite.json").exists())
        assertFalse(File(corpus, "$vencida.png").exists() || File(corpus, "$vencida.json").exists())
    }

    @Test
    fun `o prazo e o de RetencaoDaResposta, dono unico`() {
        assertEquals(30, com.platos.android.scan.RetencaoDaResposta.PRAZO_DIAS)
    }

    @Test
    fun `relogio anterior a criacao mantem`() {
        // 40 dias a frente: o modulo da diferenca (40 d) passaria do prazo; com 5 dias o teste nao distinguiria o modulo.
        val id = copiarUma().also { envelhecer(it, agora + 40 * dia) }

        coleta().eliminarVencidas(agora)

        assertEquals(listOf("$id.json", "$id.png"), nomes())
    }

    @Test
    fun `foto sem dados, dados sem foto e temporario sao residuo e saem`() {
        val inteira = copiarUma()
        val semDados = UUID.randomUUID().toString().also { File(corpus, "$it.png").writeBytes(png) }
        val semFoto = UUID.randomUUID().toString().also { File(corpus, "$it.json").writeText("{}") }
        val temporario = UUID.randomUUID().toString().also { File(corpus, "$it.png.tmp").writeBytes(png) }

        coleta().eliminarVencidas(agora + dia)

        assertEquals(listOf("$inteira.json", "$inteira.png"), nomes())
        assertFalse(File(corpus, "$semDados.png").exists())
        assertFalse(File(corpus, "$semFoto.json").exists())
        assertFalse(File(corpus, "$temporario.png.tmp").exists())
    }

    @Test
    fun `arquivo estranho na pasta nao e tocado pelo prazo`() {
        val id = copiarUma().also { envelhecer(it, aosDias(40)) }
        File(corpus, "anotacoes.txt").writeText("minhas notas")

        coleta().eliminarVencidas(agora)

        assertEquals(listOf("anotacoes.txt"), nomes(), "o prazo comeu o arquivo estranho ou deixou a amostra vencida")
        assertFalse(File(corpus, "$id.png").exists())
    }

    @Test
    fun `com o escaneamento aberto o residuo fica, porque pode ser a copia em curso, e a vencida sai`() {
        // Os dois estados de uma copia EM CURSO: a foto ja no nome final e os dados ainda temporarios (ou ausentes).
        val vencida = copiarUma().also { envelhecer(it, aosDias(31)) }
        val emCurso = UUID.randomUUID().toString().also {
            File(corpus, "$it.png").writeBytes(png)
            File(corpus, "$it.json.tmp").writeText("{}")
        }
        val soAFoto = UUID.randomUUID().toString().also { File(corpus, "$it.png").writeBytes(png) }

        coleta().eliminarVencidas(agora + dia, escaneamentoAberto = { true })

        assertTrue(File(corpus, "$emCurso.png").isFile && File(corpus, "$emCurso.json.tmp").isFile, "a varredura comeu a copia em curso")
        assertTrue(File(corpus, "$soAFoto.png").isFile, "a varredura comeu a foto que espera os dados")
        assertFalse(File(corpus, "$vencida.png").exists() || File(corpus, "$vencida.json").exists(), "a vencida nao e copia em curso")

        coleta().eliminarVencidas(agora + dia, escaneamentoAberto = { false })

        assertEquals(emptyList<String>(), nomes(), "fechado o escaneamento, o residuo sai")
    }

    // --- sair e revogar ---

    @Test
    fun `eliminar todas apaga a pasta inteira, inclusive o estranho`() {
        copiarUma()
        File(corpus, "anotacoes.txt").writeText("minhas notas")

        coleta().eliminarTodas()

        assertFalse(corpus.exists())
    }

    @Test
    fun `a pasta do corpus e a de respostas sao diferentes, e a do corpus cai sob filesDir`() {
        assertEquals(File(raiz, "corpus"), ColetaDoCorpusEmArquivo.diretorioDe(raiz))
        assertNotEquals(File(raiz, "respostas"), ColetaDoCorpusEmArquivo.diretorioDe(raiz))
    }
}
