package com.platos.android.pacote

import java.io.File
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * O cache de pacotes, no sistema de arquivos de verdade e sem aparelho.
 *
 * `@TempDir` da um diretorio real: gravacao, rename, leitura e apagamento acontecem de fato. Um
 * duplo em memoria verificaria a intencao do codigo, e o que estas tarefas precisam verificar e o
 * comportamento sobre arquivos — que e onde a corrupcao em repouso mora.
 */
class PacotesEmArquivoTest {

    @TempDir
    lateinit var raiz: File

    private val fixtures = File(
        System.getProperty("platos.fixtures")
            ?: error("propriedade `platos.fixtures` nao definida pelo build"),
    )

    private val bytes: ByteArray by lazy {
        File(fixtures, "prova-referencia.package.json").readBytes()
    }

    private val hash: String by lazy { hashDe(bytes) }

    private val cache: PacotesEmArquivo by lazy { PacotesEmArquivo(raiz) }

    private fun hashDe(conteudo: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(conteudo)
        .joinToString("") { "%02x".format(it) }

    private fun arquivoDe(organizacao: String, hash: String) =
        File(File(raiz, organizacao), "$hash.json")

    // ------------------------------------------------------------------ ida e volta

    @Test
    fun `o que foi guardado e lido de volta e conferido`() {
        cache.guardar(ORG_A, hash, bytes)

        val pacote = cache.ler(ORG_A, hash)

        assertNotNull(pacote)
        assertEquals("prova-referencia-slice-1", pacote!!.meta.examId)
    }

    @Test
    fun `hash nunca guardado devolve ausencia`() {
        assertNull(cache.ler(ORG_A, hashDe("nunca guardado".toByteArray())))
    }

    @Test
    fun `o arquivo fica sob a organizacao e com o nome do conteudo`() {
        cache.guardar(ORG_A, hash, bytes)

        assertTrue(arquivoDe(ORG_A, hash).isFile, "o arquivo nao esta onde a decisao 3 diz")
    }

    // ------------------------------------------------------------------ a reconferencia

    /**
     * Tarefa 4.3: conteudo corrompido em repouso e recusado.
     *
     * **Este cenario nao prova a reconferencia sozinho**, e a mutacao da 4.4 mostrou isso: com a
     * leitura confiando no nome do arquivo, ele continuou verde. O motivo e que o conteudo truncado
     * tambem nao parseia, entao a camada (b) o recusa por interpretacao e o desfecho na tela e o
     * mesmo. Quem isola a reconferencia e o cenario seguinte — conteudo trocado por outro pacote
     * **valido** —, e e ele que fica vermelho quando o hash deixa de ser recalculado.
     *
     * Os dois ficam. Este afirma o comportamento que a spec pede; o seguinte afirma o mecanismo.
     */
    @Test
    fun `conteudo corrompido em repouso e recusado e apagado`() {
        cache.guardar(ORG_A, hash, bytes)
        val arquivo = arquivoDe(ORG_A, hash)
        arquivo.writeBytes(bytes.copyOf(bytes.size - 40))

        assertNull(cache.ler(ORG_A, hash), "conteudo corrompido foi entregue como se conferisse")
        assertFalse(arquivo.exists(), "o conteudo recusado continuou guardado")
    }

    /**
     * **O cenario que carrega a prova da reconferencia** (tarefas 4.3 e 4.4).
     *
     * O arquivo continua com o nome do hash certo, o conteudo e JSON valido, e um `ExamPackage`
     * legitimo — so nao e o conteudo **daquele** hash. Nenhuma outra camada tem como recusa-lo: (b)
     * o parseia e reserializa sem divergencia. So o hash recalculado sobre os bytes lidos ve.
     *
     * Se a leitura confiasse no nome do arquivo, este pacote seria entregue como conferido e o
     * aparelho escanearia com ele — que e o defeito mais silencioso desta fatia.
     */
    @Test
    fun `conteudo trocado por outro pacote valido tambem e recusado`() {
        cache.guardar(ORG_A, hash, bytes)
        // Integro como JSON e como pacote — o que ele nao e, e o conteudo **daquele hash**. O
        // identificador trocado tem o mesmo comprimento, entao ate o tamanho do arquivo bate.
        val outroPacote = bytes.decodeToString()
            .replace("prova-referencia-slice-1", "prova-referencia-slice-2")
        assertEquals(bytes.size, outroPacote.toByteArray().size, "a troca mudou o tamanho")
        arquivoDe(ORG_A, hash).writeBytes(outroPacote.toByteArray())

        assertNull(cache.ler(ORG_A, hash))
    }

    @Test
    fun `a leitura recusada faz a gravacao seguinte encontrar o lugar vazio`() {
        cache.guardar(ORG_A, hash, bytes)
        arquivoDe(ORG_A, hash).writeBytes("lixo".toByteArray())

        assertNull(cache.ler(ORG_A, hash))
        cache.guardar(ORG_A, hash, bytes)

        assertNotNull(cache.ler(ORG_A, hash), "o pull seguinte nao conseguiu regravar")
    }

    // ------------------------------------------------------------------ a atomicidade

    /**
     * Tarefa 4.5: parcial deixado para tras nao e conteudo.
     *
     * Um arquivo com o nome do temporario nao e alcancavel pela leitura, que so olha o nome exato do
     * hash. E o que faz "gravacao interrompida" ser indistinguivel de "gravacao que nunca comecou".
     */
    @Test
    fun `parcial deixado para tras nao e confundido com conteudo`() {
        val pasta = File(raiz, ORG_A).apply { mkdirs() }
        File(pasta, "$hash.json.parcial-12345").writeBytes(bytes)

        assertNull(cache.ler(ORG_A, hash), "um parcial foi lido como se fosse o conteudo")
    }

    @Test
    fun `a gravacao nao deixa parcial para tras`() {
        cache.guardar(ORG_A, hash, bytes)

        val sobras = File(raiz, ORG_A).listFiles()!!.filter { it.name.contains(".parcial-") }
        assertTrue(sobras.isEmpty(), "sobrou parcial: ${sobras.map { it.name }}")
    }

    @Test
    fun `guardar duas vezes o mesmo conteudo nao quebra nem duplica`() {
        cache.guardar(ORG_A, hash, bytes)
        cache.guardar(ORG_A, hash, bytes)

        assertEquals(1, File(raiz, ORG_A).listFiles()!!.size)
        assertNotNull(cache.ler(ORG_A, hash))
    }

    // ------------------------------------------------------------------ o escopo

    /**
     * Tarefa 4.6: o furo que a decisao 3 de ADR-0013 nomeia.
     *
     * O hash e o mesmo — enderecamento por conteudo e global por natureza. O que separa as duas
     * organizacoes e o diretorio, e sem ele o aparelho compartilhado entregaria da pasta um pacote
     * que a rota recusaria.
     */
    @Test
    fun `o mesmo conteudo guardado numa organizacao nao e alcancavel pela outra`() {
        cache.guardar(ORG_A, hash, bytes)

        assertNotNull(cache.ler(ORG_A, hash))
        assertNull(cache.ler(ORG_B, hash), "o cache atravessou a fronteira de organizacao")
    }

    // ------------------------------------------------------------------ o apagamento

    @Test
    fun `apagar uma organizacao leva o que estava guardado nela`() {
        cache.guardar(ORG_A, hash, bytes)

        cache.apagarDaOrganizacao(ORG_A)

        assertNull(cache.ler(ORG_A, hash))
        assertFalse(File(raiz, ORG_A).exists())
    }

    @Test
    fun `apagar uma organizacao nao toca na outra`() {
        cache.guardar(ORG_A, hash, bytes)
        cache.guardar(ORG_B, hash, bytes)

        cache.apagarDaOrganizacao(ORG_A)

        assertNull(cache.ler(ORG_A, hash))
        assertNotNull(cache.ler(ORG_B, hash), "apagar uma organizacao levou a outra junto")
    }

    @Test
    fun `apagar organizacao sem nada guardado nao estoura`() {
        cache.apagarDaOrganizacao(ORG_A)
    }

    // ------------------------------------------------------------------ entrada de fora

    /**
     * Os dois identificadores viram segmento de caminho, e os dois vem de fora.
     *
     * E o unico ponto desta fatia onde resposta de servidor vira nome de arquivo. Recusar pela forma
     * e mais barato que confiar em quem responde.
     */
    @Test
    fun `identificador com travessia de caminho nao escapa do diretorio`() {
        cache.guardar("../../fora", hash, bytes)

        assertNull(cache.ler("../../fora", hash))
        assertFalse(File(raiz.parentFile, "fora").exists(), "o cache escreveu fora da raiz")
    }

    @Test
    fun `hash com travessia de caminho nao escapa do diretorio`() {
        cache.guardar(ORG_A, "../escapou", bytes)

        assertNull(cache.ler(ORG_A, "../escapou"))
        assertFalse(File(raiz, "escapou").exists())
        assertFalse(File(raiz, "escapou.json").exists())
    }

    @Test
    fun `hash malformado nao vira arquivo`() {
        cache.guardar(ORG_A, "nao-e-hash", bytes)

        assertNull(cache.ler(ORG_A, "nao-e-hash"))
        assertFalse(File(raiz, ORG_A).exists())
    }

    private companion object {
        const val ORG_A = "11111111-1111-7111-8111-111111111111"
        const val ORG_B = "22222222-2222-7222-8222-222222222222"
    }
}
