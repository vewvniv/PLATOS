package com.platos.android.scan

import java.io.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * O prazo de 30 dias da resposta (`slice-5c-1-a-resposta-fica-no-aparelho`, tarefa 4.1), em JVM: a regra
 * pura e a execucao por arquivo, com [RespostasGuardadas] e [CadernosGuardados] falsos.
 *
 * As fronteiras sao pinadas por teste: 29 dias mantem, **exatamente 30 elimina**, 31 elimina.
 */
class RetencaoDaRespostaTest {

    private val dia = 86_400_000L
    private val agora = 100 * dia

    private fun aosDias(dias: Long) = agora - dias * dia

    // --- a regra pura ---

    @Test
    fun `o prazo e de 30 dias, dono unico`() {
        assertEquals(30, RetencaoDaResposta.PRAZO_DIAS)
    }

    /** Cenario "Resposta com 29 dias e mantida". */
    @Test
    fun `29 dias mantem`() {
        assertEquals(
            emptyList<String>(),
            RetencaoDaResposta.arquivosAEliminar(listOf("a.png"), mapOf("a.png" to aosDias(29)), agora),
        )
    }

    /** Cenario "Resposta com exatamente 30 dias": o dia 30 ja e o limite. */
    @Test
    fun `exatamente 30 dias elimina`() {
        assertEquals(
            listOf("a.png"),
            RetencaoDaResposta.arquivosAEliminar(listOf("a.png"), mapOf("a.png" to aosDias(30)), agora),
        )
    }

    /** Um milissegundo antes do dia 30 ainda mantem: a borda e o instante, e nao o dia inteiro. */
    @Test
    fun `um milissegundo antes dos 30 dias mantem`() {
        val capturada = aosDias(30) + 1
        assertEquals(
            emptyList<String>(),
            RetencaoDaResposta.arquivosAEliminar(listOf("a.png"), mapOf("a.png" to capturada), agora),
        )
    }

    /** Cenario "Resposta com 31 dias". */
    @Test
    fun `31 dias elimina`() {
        assertEquals(
            listOf("a.png"),
            RetencaoDaResposta.arquivosAEliminar(listOf("a.png"), mapOf("a.png" to aosDias(31)), agora),
        )
    }

    /** Cenarios "Arquivo que nenhum caderno referencia" e "Arquivo temporario de uma gravacao interrompida". */
    @Test
    fun `arquivo sem referencia elimina com zero dia, inclusive o temporario`() {
        val noDisco = listOf("orfao.png", "interrompida.png.tmp", "guardada.png")

        val aEliminar = RetencaoDaResposta.arquivosAEliminar(noDisco, mapOf("guardada.png" to agora), agora)

        assertEquals(listOf("orfao.png", "interrompida.png.tmp"), aEliminar)
    }

    @Test
    fun `referencia sem arquivo nao gera eliminacao nem erro`() {
        assertEquals(
            emptyList<String>(),
            RetencaoDaResposta.arquivosAEliminar(emptyList(), mapOf("fantasma.png" to aosDias(99)), agora),
        )
    }

    @Test
    fun `lista vazia nao elimina nada`() {
        assertEquals(emptyList<String>(), RetencaoDaResposta.arquivosAEliminar(emptyList(), emptyMap(), agora))
    }

    /** Relogio do aparelho atrasado: a diferenca negativa nunca alcanca o prazo, e nao estoura. */
    @Test
    fun `relogio anterior a captura mantem`() {
        assertEquals(
            emptyList<String>(),
            RetencaoDaResposta.arquivosAEliminar(listOf("a.png"), mapOf("a.png" to agora + 40 * dia), agora),
        )
    }

    // --- as referencias saem de todos os cadernos ---

    private fun caderno(aluno: String, vararg respostas: RespostaGuardada) = Caderno(
        aluno = aluno,
        regioes = respostas.mapIndexed { i, r ->
            RegiaoDoCaderno(i + 1, gabarito = false, rotulo = "${i + 1}", estado = EstadoDaRegiao.Capturada, resposta = r)
        },
        parcial = null,
    )

    private fun resposta(arquivo: String, dias: Long) =
        RespostaGuardada(arquivo, capturadaEm = aosDias(dias), desvioSinalizado = false, foraPpm = 0)

    @Test
    fun `a mesma resposta referenciada por dois cadernos vale pelo instante mais antigo`() {
        val mapa = RetencaoDaResposta.referenciadasPor(
            listOf(caderno("tok-a", resposta("a.png", 10)), caderno("tok-b", resposta("a.png", 40))),
        )

        assertEquals(mapOf("a.png" to aosDias(40)), mapa)
    }

    // --- a execucao ---

    private class Disco(nomes: List<String>, private val queFalham: Set<String> = emptySet()) : RespostasGuardadas {
        val presentes = nomes.toMutableList()
        val tentativas = mutableListOf<String>()

        override fun gravar(png: ByteArray, capturadaEm: Long, desvioSinalizado: Boolean, foraPpm: Int) =
            error("este teste nao grava")

        override fun ler(arquivo: String): ByteArray? = null
        override fun existe(arquivo: String) = arquivo in presentes
        override fun listar(): List<String> = presentes.toList()
        override fun eliminar(arquivo: String) {
            tentativas += arquivo
            if (arquivo in queFalham) throw IOException("nao foi possivel eliminar $arquivo")
            presentes -= arquivo
        }
    }

    private class Cadernos(private val todos: List<Caderno>, private val quebra: Boolean = false) : CadernosGuardados {
        override fun guardar(organizacao: String, examId: String, caderno: Caderno) = Unit
        override fun ler(organizacao: String, examId: String): Caderno? = null
        override fun todos(): List<Caderno> = if (quebra) throw IllegalStateException("json corrompido") else todos
    }

    /** Cenario "Outra organizacao no mesmo aparelho": vencidas das duas somem, as validas das duas ficam. */
    @Test
    fun `duas organizacoes misturadas, as vencidas somem e as validas e referenciadas ficam`() {
        val cadernos = Cadernos(
            listOf(
                caderno("tok-a", resposta("a-valida.png", 5), resposta("a-vencida.png", 31)),
                caderno("tok-b", resposta("b-valida.png", 29), resposta("b-no-limite.png", 30)),
            ),
        )
        val disco = Disco(listOf("a-valida.png", "a-vencida.png", "b-valida.png", "b-no-limite.png", "orfao.png"))

        val varredura = varrerRespostas(disco, cadernos, agora)

        assertEquals(listOf("a-valida.png", "b-valida.png"), disco.presentes)
        assertEquals(Varredura(eliminados = 3, naoEliminados = 0), varredura)
    }

    /** Cenario "Eliminacao que falha nao impede o escaneamento": os outros seguem, e a falha e contada. */
    @Test
    fun `um arquivo que nao se consegue eliminar nao impede os outros, e e contado`() {
        val disco = Disco(listOf("a.png", "teimoso.png", "c.png"), queFalham = setOf("teimoso.png"))

        val varredura = varrerRespostas(disco, Cadernos(emptyList()), agora)

        assertEquals(listOf("a.png", "teimoso.png", "c.png"), disco.tentativas, "tentou todos, na ordem")
        assertEquals(listOf("teimoso.png"), disco.presentes, "so o que falhou ficou")
        assertEquals(Varredura(eliminados = 2, naoEliminados = 1), varredura)
    }

    /** Sem ler os cadernos nao se sabe o que e orfao: nada e eliminado, e a abertura nao cai. */
    @Test
    fun `se os cadernos nao podem ser lidos nada e eliminado e a varredura nao lanca`() {
        val disco = Disco(listOf("a.png", "b.png"))

        val varredura = varrerRespostas(disco, Cadernos(emptyList(), quebra = true), agora)

        assertEquals(listOf("a.png", "b.png"), disco.presentes)
        assertTrue(varredura.semLeituraDosCadernos)
        assertEquals(0, varredura.eliminados)
    }

    // --- a trava do segundo plano (`slice-5c-3-a-nota-no-aparelho`) ---

    /** Com o escaneamento aberto, a regra "ninguem referencia" nao roda: o caderno em memoria ainda nao foi ao Room. */
    @Test
    fun `com o escaneamento aberto o arquivo sem referencia e mantido`() {
        assertEquals(
            emptyList<String>(),
            RetencaoDaResposta.arquivosAEliminar(listOf("recem-gravada.png"), emptyMap(), agora, escaneamentoAberto = true),
        )
    }

    @Test
    fun `com o escaneamento aberto o teto de 30 dias continua valendo`() {
        assertEquals(
            listOf("velha.png"),
            RetencaoDaResposta.arquivosAEliminar(
                listOf("velha.png", "nova.png"),
                mapOf("velha.png" to aosDias(31), "nova.png" to aosDias(1)),
                agora,
                escaneamentoAberto = true,
            ),
        )
    }

    @Test
    fun `sem o escaneamento aberto o arquivo sem referencia e eliminado, como sempre`() {
        assertEquals(
            listOf("orfao.png"),
            RetencaoDaResposta.arquivosAEliminar(listOf("orfao.png"), emptyMap(), agora),
        )
    }
}
