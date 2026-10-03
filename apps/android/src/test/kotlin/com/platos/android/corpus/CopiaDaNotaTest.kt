package com.platos.android.corpus

import com.platos.android.scan.LinhaDaNota
import com.platos.android.scan.RespostaDoQuadro
import com.platos.android.scan.RespostaGuardada
import com.platos.android.scan.RespostasGuardadas
import com.platos.domain.scoring.PontuacaoDada
import com.platos.domain.scoring.Pontos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A regra da copia da nota (`slice-5d-corpus-de-medicao`, spec `scan-session`: "Com a coleta ligada, a nota
 * confirmada deixa a copia das discursivas"), em JVM, com a coleta e as respostas falsas.
 */
class CopiaDaNotaTest {

    private class ColetaFalsa(var ligadaAgora: Boolean = true) : ColetaDoCorpus {
        val chamadas = mutableListOf<String>()
        val eliminadas = mutableListOf<String>()
        var aoCopiar: (List<AmostraACopiar>) -> ResultadoDaCopia =
            { a -> ResultadoDaCopia(a.map { "id-${it.rotulo}" }, emptyList()) }

        override fun ligada() = ligadaAgora

        override fun copiar(amostras: List<AmostraACopiar>, respostas: RespostasGuardadas): ResultadoDaCopia {
            chamadas += "copiar"
            return aoCopiar(amostras)
        }

        override fun eliminar(ids: List<String>) {
            chamadas += "eliminar"
            eliminadas += ids
        }

        override fun eliminarVencidas(agora: Long, escaneamentoAberto: () -> Boolean) = Unit
        override fun eliminarTodas() = Unit
    }

    private object SemRespostas : RespostasGuardadas {
        override fun gravar(png: ByteArray, capturadaEm: Long, desvioSinalizado: Boolean, foraPpm: Int): RespostaDoQuadro =
            RespostaDoQuadro.Recusada("falso")

        override fun ler(arquivo: String): ByteArray? = null
        override fun existe(arquivo: String) = false
        override fun listar() = emptyList<String>()
        override fun eliminar(arquivo: String) = Unit
    }

    private fun resposta(arquivo: String) = RespostaGuardada(arquivo, capturadaEm = 1_000L, desvioSinalizado = false, foraPpm = 0)

    private val linhas = listOf(
        LinhaDaNota("d1", rotulo = "1", worth = 3, resposta = resposta("a.png")),
        LinhaDaNota("d2", rotulo = "2", worth = 4, resposta = resposta("b.png")),
    )

    private fun amostras() = amostrasDaNota(
        linhas,
        listOf(PontuacaoDada("d1", Pontos.parse("1.5")), PontuacaoDada("d2", Pontos.parse("0"))),
        pacote = "hash-do-pacote",
    )

    // --- amostrasDaNota ---

    @Test
    fun `cada linha casa com a pontuacao dada a ela, e o zero e uma pontuacao`() {
        val dadas = amostras()

        assertEquals(listOf("a.png", "b.png"), dadas.map { it.arquivo })
        assertEquals(listOf("1", "2"), dadas.map { it.rotulo })
        assertEquals(listOf("1.50", "0.00"), dadas.map { it.pontos.toString() })
        assertEquals(listOf(3, 4), dadas.map { it.maximo })
        assertEquals(listOf("d1", "d2"), dadas.map { it.item })
        assertEquals(listOf("hash-do-pacote", "hash-do-pacote"), dadas.map { it.pacote })
    }

    @Test
    fun `discursiva sem foto guardada fica de fora, sem inventar falha`() {
        val semFoto = listOf(linhas[0], LinhaDaNota("d2", rotulo = "2", worth = 4, resposta = null))

        val dadas = amostrasDaNota(
            semFoto,
            listOf(PontuacaoDada("d1", Pontos.parse("1")), PontuacaoDada("d2", Pontos.parse("2"))),
            pacote = "p",
        )

        assertEquals(listOf("1"), dadas.map { it.rotulo })
    }

    // --- gravarNotaComColeta ---

    @Test
    fun `a copia vem antes de gravar a nota`() {
        val coleta = ColetaFalsa()
        var copiouAntes = false

        val r = gravarNotaComColeta(coleta, SemRespostas, ::amostras) {
            copiouAntes = "copiar" in coleta.chamadas
            true
        }

        assertTrue(copiouAntes, "gravarNota rodou antes da copia: a imagem pode ser eliminada na confirmacao")
        assertTrue(r.gravou)
        assertEquals(emptyList<String>(), r.falhas)
    }

    @Test
    fun `coleta desligada nao copia nada, e a nota e gravada`() {
        val coleta = ColetaFalsa(ligadaAgora = false)
        var gravou = false

        val r = gravarNotaComColeta(coleta, SemRespostas, ::amostras) { gravou = true; true }

        assertEquals(emptyList<String>(), coleta.chamadas)
        assertTrue(gravou)
        assertTrue(r.gravou)
    }

    @Test
    fun `a lista de amostras so e calculada com a coleta ligada`() {
        val coleta = ColetaFalsa(ligadaAgora = false)
        var calculou = false

        gravarNotaComColeta(coleta, SemRespostas, { calculou = true; amostras() }) { true }

        assertFalse(calculou, "o hash do pacote foi calculado com a coleta desligada")
    }

    @Test
    fun `falha da copia nao impede a nota e diz qual questao`() {
        val coleta = ColetaFalsa()
        coleta.aoCopiar = { ResultadoDaCopia(listOf("id-1"), falhas = listOf("2")) }

        val r = gravarNotaComColeta(coleta, SemRespostas, ::amostras) { true }

        assertTrue(r.gravou)
        assertEquals(listOf("2"), r.falhas)
        assertEquals(emptyList<String>(), coleta.eliminadas, "a nota foi gravada: as amostras ficam")
    }

    @Test
    fun `excecao da copia nao impede a nota`() {
        val coleta = ColetaFalsa()
        coleta.aoCopiar = { throw IllegalStateException("disco cheio") }

        val r = gravarNotaComColeta(coleta, SemRespostas, ::amostras) { true }

        assertTrue(r.gravou)
        assertEquals(listOf(TODAS), r.falhas)
    }

    @Test
    fun `excecao ao montar as amostras nao impede a nota`() {
        val coleta = ColetaFalsa()

        val r = gravarNotaComColeta(coleta, SemRespostas, { throw IllegalStateException("hash") }) { true }

        assertTrue(r.gravou)
        assertEquals(listOf(TODAS), r.falhas)
    }

    @Test
    fun `nota nao gravada elimina as amostras copiadas por ela`() {
        val coleta = ColetaFalsa()

        val r = gravarNotaComColeta(coleta, SemRespostas, ::amostras) { false }

        assertFalse(r.gravou)
        assertEquals(listOf("id-1", "id-2"), coleta.eliminadas)
    }

    @Test
    fun `nota gravada nao elimina amostra nenhuma`() {
        val coleta = ColetaFalsa()

        gravarNotaComColeta(coleta, SemRespostas, ::amostras) { true }

        assertEquals(emptyList<String>(), coleta.eliminadas)
    }
}
