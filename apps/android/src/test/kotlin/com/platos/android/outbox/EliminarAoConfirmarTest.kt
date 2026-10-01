package com.platos.android.outbox

import com.platos.android.scan.Caderno
import com.platos.android.scan.CadernosGuardados
import com.platos.android.scan.EstadoDaRegiao
import com.platos.android.scan.RegiaoDoCaderno
import com.platos.android.scan.RespostaDoQuadro
import com.platos.android.scan.RespostaGuardada
import com.platos.android.scan.RespostasGuardadas
import java.io.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * O que a confirmacao da nota elimina (`slice-5c-3-a-nota-no-aparelho`): so as imagens do caderno que a **nota
 * completa**, achado por `capturaDaParcial`, e nunca por a nota ter ou nao marcado o caderno como corrigido.
 */
class EliminarAoConfirmarTest {

    private class Cadernos(var guardado: Caderno?) : CadernosGuardados {
        override fun guardar(organizacao: String, examId: String, caderno: Caderno) {
            guardado = caderno
        }
        override fun ler(organizacao: String, examId: String): Caderno? = guardado
        override fun todos(): List<Caderno> = listOfNotNull(guardado)
    }

    private class Arquivos(vararg nomes: String, val quebra: Set<String> = emptySet()) : RespostasGuardadas {
        val no = nomes.toMutableSet()
        override fun gravar(png: ByteArray, capturadaEm: Long, desvioSinalizado: Boolean, foraPpm: Int): RespostaDoQuadro =
            error("nao usado")
        override fun ler(arquivo: String): ByteArray? = null
        override fun existe(arquivo: String) = arquivo in no
        override fun listar() = no.toList()
        override fun eliminar(arquivo: String) {
            if (arquivo in quebra) throw IOException("nao deu")
            no.remove(arquivo)
        }
    }

    private fun caderno(captura: String? = "parcial-1", corrigido: Boolean = false) = Caderno(
        aluno = "tok-a",
        regioes = listOf(
            RegiaoDoCaderno(0, true, "Gabarito", EstadoDaRegiao.Capturada),
            RegiaoDoCaderno(1, false, "3", EstadoDaRegiao.Capturada, RespostaGuardada("r1.png", 0L, false, 0), "d1"),
            RegiaoDoCaderno(2, false, "4", EstadoDaRegiao.Capturada, RespostaGuardada("r2.png", 0L, false, 0), "d2"),
        ),
        parcial = null,
        entregue = true,
        capturaDaParcial = captura,
        corrigido = corrigido,
    )

    private fun envelope(rota: RotaDoEnvio = RotaDoEnvio.NOTA, completa: String? = "parcial-1") =
        EnvelopeDeEnvio("cap-nota-1", "org-1", "prova-r", "{}", rota, completa)

    @Test
    fun `a nota confirmada elimina as imagens do caderno que ela completa`() {
        val cadernos = Cadernos(caderno(corrigido = true))
        val arquivos = Arquivos("r1.png", "r2.png")

        val r = eliminarAoConfirmar(envelope(), cadernos, arquivos)

        assertEquals(EliminacaoAoConfirmar(2, 0), r)
        assertTrue(arquivos.no.isEmpty())
        assertTrue(cadernos.guardado!!.regioes.all { it.resposta == null })
        assertEquals(3, cadernos.guardado!!.capturadas, "eliminar nao devolve regiao a nao vista")
    }

    @Test
    fun `a queda entre gravar a nota e gravar o caderno nao impede a eliminacao`() {
        val cadernos = Cadernos(caderno(corrigido = false))
        val arquivos = Arquivos("r1.png", "r2.png")

        eliminarAoConfirmar(envelope(), cadernos, arquivos)

        assertTrue(arquivos.no.isEmpty())
        assertTrue(cadernos.guardado!!.corrigido, "o gancho fecha o que a queda deixou aberto")
    }

    @Test
    fun `o resultado e a parcial nao eliminam nada`() {
        val cadernos = Cadernos(caderno())
        val arquivos = Arquivos("r1.png", "r2.png")

        val r = eliminarAoConfirmar(envelope(rota = RotaDoEnvio.RESULTADO, completa = null), cadernos, arquivos)

        assertEquals(EliminacaoAoConfirmar(0, 0), r)
        assertEquals(setOf("r1.png", "r2.png"), arquivos.no)
    }

    @Test
    fun `a confirmacao de uma nota antiga nao toca o caderno de outra captura`() {
        val cadernos = Cadernos(caderno(captura = "parcial-9"))
        val arquivos = Arquivos("r1.png", "r2.png")

        val r = eliminarAoConfirmar(envelope(completa = "parcial-1"), cadernos, arquivos)

        assertEquals(EliminacaoAoConfirmar(0, 0), r)
        assertEquals(setOf("r1.png", "r2.png"), arquivos.no)
        assertEquals("parcial-9", cadernos.guardado!!.capturaDaParcial)
    }

    @Test
    fun `sem caderno guardado nao ha o que eliminar`() {
        assertEquals(EliminacaoAoConfirmar(0, 0), eliminarAoConfirmar(envelope(), Cadernos(null), Arquivos("r1.png")))
    }

    @Test
    fun `arquivo que falha nao impede os outros, e e contado`() {
        val cadernos = Cadernos(caderno())
        val arquivos = Arquivos("r1.png", "r2.png", quebra = setOf("r1.png"))

        val r = eliminarAoConfirmar(envelope(), cadernos, arquivos)

        assertEquals(EliminacaoAoConfirmar(1, 1), r)
        assertEquals(setOf("r1.png"), arquivos.no, "o que falhou fica para a varredura, que o acha sem referencia")
        assertFalse(cadernos.guardado!!.regioes.any { it.resposta != null }, "o caderno nao referencia mais nenhum")
    }
}
