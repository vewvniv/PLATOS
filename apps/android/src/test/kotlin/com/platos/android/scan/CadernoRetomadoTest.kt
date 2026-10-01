package com.platos.android.scan

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * O caderno lido do disco e normalizado (`slice-5c-1-a-resposta-fica-no-aparelho`, tarefa 3.1), em JVM,
 * com [RespostasGuardadas] falso: nenhum caderno lido referencia imagem que nao existe.
 */
class CadernoRetomadoTest {

    private val resposta1 = RespostaGuardada("um.png", capturadaEm = 10L, desvioSinalizado = true, foraPpm = 70_000)
    private val resposta2 = RespostaGuardada("dois.png", capturadaEm = 20L, desvioSinalizado = false, foraPpm = 0)

    private fun caderno(
        estado1: EstadoDaRegiao = EstadoDaRegiao.Capturada,
        r1: RespostaGuardada? = resposta1,
        estado2: EstadoDaRegiao = EstadoDaRegiao.Capturada,
        r2: RespostaGuardada? = resposta2,
        entregue: Boolean = false,
    ) = Caderno(
        aluno = "tok-a",
        regioes = listOf(
            RegiaoDoCaderno(0, gabarito = true, rotulo = Caderno.ROTULO_GABARITO, estado = EstadoDaRegiao.Capturada),
            RegiaoDoCaderno(1, gabarito = false, rotulo = "1", estado = estado1, resposta = r1),
            RegiaoDoCaderno(2, gabarito = false, rotulo = "2", estado = estado2, resposta = r2),
        ),
        parcial = null,
        entregue = entregue,
    )

    private fun estados(c: Caderno) = c.regioes.map { it.estado }

    /** Cenario "A resposta referenciada nao existe mais". */
    @Test
    fun `a resposta que nao existe mais devolve a regiao a nao vista, e as outras ficam`() {
        val lido = caderno().normalizado { it == "dois.png" }

        assertEquals(
            listOf(EstadoDaRegiao.Capturada, EstadoDaRegiao.NaoVista, EstadoDaRegiao.Capturada),
            estados(lido),
        )
        assertNull(lido.regioes[1].resposta, "a regiao nao pode referenciar um arquivo que nao existe")
        assertEquals(resposta2, lido.regioes[2].resposta)
        assertEquals(2, lido.capturadas)
    }

    /** Cenario "Caderno de antes desta mudanca": discursiva capturada, sem resposta. */
    @Test
    fun `discursiva capturada sem resposta, de antes da mudanca, volta a nao vista`() {
        val antigo = caderno(r1 = null, r2 = resposta2)

        val lido = antigo.normalizado { true }

        assertEquals(
            listOf(EstadoDaRegiao.Capturada, EstadoDaRegiao.NaoVista, EstadoDaRegiao.Capturada),
            estados(lido),
        )
        assertEquals(2, lido.capturadas, "o contador nao soma a regiao sem resposta")
    }

    @Test
    fun `com todas as respostas em disco o caderno volta intacto`() {
        val original = caderno(entregue = true)

        assertEquals(original, original.normalizado { true })
    }

    /** O gabarito nao tem resposta e nunca e rebaixado; regioes com problema ou nao vistas ficam como estao. */
    @Test
    fun `gabarito, regiao com problema e regiao nao vista nao sao tocados`() {
        val original = caderno(
            estado1 = EstadoDaRegiao.ComProblema("o recorte nao foi pedido"),
            r1 = null,
            estado2 = EstadoDaRegiao.NaoVista,
            r2 = null,
        )

        assertEquals(original, original.normalizado { false })
    }

    /** `entregue` e a parcial nao sao limpos: caderno entregue nao entrega de novo por perder uma resposta. */
    @Test
    fun `perder uma resposta nao limpa a marca de entrega`() {
        val lido = caderno(entregue = true).normalizado { false }

        assertEquals(true, lido.entregue)
        assertEquals(1, lido.capturadas, "so o gabarito sobra")
    }

    // --- retomarCadernoEmAndamento: a unica via de leitura ---

    private class RespostasFalsas(private val existentes: Set<String>) : RespostasGuardadas {
        override fun gravar(png: ByteArray, capturadaEm: Long, desvioSinalizado: Boolean, foraPpm: Int) =
            error("este teste nao grava")

        override fun ler(arquivo: String): ByteArray? = null
        override fun existe(arquivo: String) = arquivo in existentes
        override fun listar(): List<String> = existentes.toList()
        override fun eliminar(arquivo: String) = Unit
    }

    private class CadernoUnico(private val guardado: Caderno?) : CadernosGuardados {
        override fun guardar(organizacao: String, examId: String, caderno: Caderno) = Unit
        override fun ler(organizacao: String, examId: String): Caderno? = guardado
        override fun todos(): List<Caderno> = listOfNotNull(guardado)
    }

    @Test
    fun `retomar le o caderno guardado e o devolve normalizado`() = runBlocking {
        val retomado = retomarCadernoEmAndamento(
            CadernoUnico(caderno()), RespostasFalsas(setOf("um.png")), "org", "prova",
        ).await()

        assertEquals(
            listOf(EstadoDaRegiao.Capturada, EstadoDaRegiao.Capturada, EstadoDaRegiao.NaoVista),
            estados(requireNotNull(retomado)),
        )
    }

    @Test
    fun `retomar devolve a discursiva capturada sem resposta, de antes da mudanca, como nao vista`() = runBlocking {
        val retomado = retomarCadernoEmAndamento(
            CadernoUnico(caderno(r1 = null)), RespostasFalsas(setOf("um.png", "dois.png")), "org", "prova",
        ).await()

        assertEquals(
            listOf(EstadoDaRegiao.Capturada, EstadoDaRegiao.NaoVista, EstadoDaRegiao.Capturada),
            estados(requireNotNull(retomado)),
        )
    }

    @Test
    fun `retomar sem caderno guardado devolve nulo`() = runBlocking {
        val retomado = retomarCadernoEmAndamento(CadernoUnico(null), RespostasFalsas(emptySet()), "org", "prova").await()

        assertNull(retomado)
    }
}
