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

    /** Um disco que **elimina de verdade**: a ordem varrer-ler-normalizar so aparece se eliminar tiver efeito. */
    private class RespostasFalsas(existentes: Set<String>) : RespostasGuardadas {
        val presentes = existentes.toMutableSet()

        override fun gravar(png: ByteArray, capturadaEm: Long, desvioSinalizado: Boolean, foraPpm: Int) =
            error("este teste nao grava")

        override fun ler(arquivo: String): ByteArray? = null
        override fun existe(arquivo: String) = arquivo in presentes
        override fun listar(): List<String> = presentes.toList()
        override fun eliminar(arquivo: String) {
            presentes -= arquivo
        }
    }

    /** O relogio dos testes que nao falam de prazo: as respostas deles (10 e 20 ms) estao longe de vencer. */
    private val relogioSemPrazo = { 30L }

    private class CadernoUnico(private val guardado: Caderno?) : CadernosGuardados {
        override fun guardar(organizacao: String, examId: String, caderno: Caderno) = Unit
        override fun ler(organizacao: String, examId: String): Caderno? = guardado
        override fun todos(): List<Caderno> = listOfNotNull(guardado)
    }

    @Test
    fun `retomar le o caderno guardado e o devolve normalizado`() = runBlocking {
        val retomado = retomarCadernoEmAndamento(
            CadernoUnico(caderno()), RespostasFalsas(setOf("um.png")), "org", "prova", relogioSemPrazo,
        ).await()

        assertEquals(
            listOf(EstadoDaRegiao.Capturada, EstadoDaRegiao.Capturada, EstadoDaRegiao.NaoVista),
            estados(requireNotNull(retomado)),
        )
    }

    @Test
    fun `retomar devolve a discursiva capturada sem resposta, de antes da mudanca, como nao vista`() = runBlocking {
        val retomado = retomarCadernoEmAndamento(
            CadernoUnico(caderno(r1 = null)), RespostasFalsas(setOf("um.png", "dois.png")), "org", "prova", relogioSemPrazo,
        ).await()

        assertEquals(
            listOf(EstadoDaRegiao.Capturada, EstadoDaRegiao.NaoVista, EstadoDaRegiao.Capturada),
            estados(requireNotNull(retomado)),
        )
    }

    /**
     * Cenario "Resposta com 31 dias": varrer vem **antes** de ler. O caderno referencia uma resposta de 31
     * dias; a varredura a elimina, e a leitura normalizada devolve a regiao como nao vista. Lido antes da
     * varredura, o caderno ainda veria o arquivo, e a regiao voltaria capturada com o arquivo eliminado.
     */
    @Test
    fun `a varredura roda antes da leitura, a resposta de 31 dias some e a regiao volta nao vista`() = runBlocking {
        val dia = 86_400_000L
        val vencida = RespostaGuardada("um.png", capturadaEm = 0L, desvioSinalizado = false, foraPpm = 0)
        val disco = RespostasFalsas(setOf("um.png", "dois.png"))
        val varreduras = mutableListOf<Varredura>()

        val retomado = retomarCadernoEmAndamento(
            CadernoUnico(caderno(r1 = vencida, r2 = resposta2.copy(capturadaEm = 31 * dia - 5))),
            disco, "org", "prova", relogio = { 31 * dia }, aoVarrer = { varreduras += it },
        ).await()

        assertEquals(setOf("dois.png"), disco.presentes, "a vencida foi eliminada, a de 5 ms a menos de 31 dias ficou")
        assertEquals(
            listOf(EstadoDaRegiao.Capturada, EstadoDaRegiao.NaoVista, EstadoDaRegiao.Capturada),
            estados(requireNotNull(retomado)),
        )
        assertEquals(listOf(Varredura(eliminados = 1, naoEliminados = 0)), varreduras)
    }

    @Test
    fun `retomar sem caderno guardado devolve nulo`() = runBlocking {
        val retomado = retomarCadernoEmAndamento(
            CadernoUnico(null), RespostasFalsas(emptySet()), "org", "prova", relogioSemPrazo,
        ).await()

        assertNull(retomado)
    }
}
