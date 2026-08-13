package com.platos.api.billing

import com.platos.api.support.PostgresSupport
import org.jooq.exception.DataAccessException
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Os cenarios de "Ledger de creditos append-only" (specs/billing). */
class CreditLedgerTest {

    private lateinit var usuario: UUID
    private lateinit var organizacao: UUID

    @BeforeTest
    fun setUp() {
        PostgresSupport.start()
        PostgresSupport.reset()

        usuario = PostgresSupport.createUser("sub-ledger", "ledger@escola.br")
        organizacao = PostgresSupport.createOrganization(kind = "school", name = "Escola")
        PostgresSupport.addMembership(usuario, organizacao, "owner")
    }

    @Test
    fun `alteracao de lancamento e recusada`() {
        // D-0.9: append-only que depende de disciplina de codigo nao e append-only. O privilegio
        // de UPDATE simplesmente nao existe para o papel da aplicacao.
        PostgresSupport.addLedgerEntry(organizacao, "ai_exam_generation", 100)

        assertFailsWith<DataAccessException> {
            PostgresSupport.tenancy.asUser(usuario) { ctx ->
                ctx.execute("update credit_ledger set amount = 999 where organization_id = ?", organizacao)
            }
        }

        assertEquals(100L, saldo("ai_exam_generation"))
    }

    @Test
    fun `exclusao de lancamento e recusada`() {
        PostgresSupport.addLedgerEntry(organizacao, "ai_exam_generation", 100)

        assertFailsWith<DataAccessException> {
            PostgresSupport.tenancy.asUser(usuario) { ctx ->
                ctx.execute("delete from credit_ledger where organization_id = ?", organizacao)
            }
        }

        assertEquals(1, quantidadeDeLancamentos())
    }

    @Test
    fun `saldo e derivado da soma dos lancamentos`() {
        PostgresSupport.addLedgerEntry(organizacao, "ai_exam_generation", 100, "concessao")
        PostgresSupport.addLedgerEntry(organizacao, "ai_exam_generation", -30, "uso")

        assertEquals(70L, saldo("ai_exam_generation"))
    }

    @Test
    fun `correcao so e possivel por lancamento compensatorio e o historico preserva ambos`() {
        PostgresSupport.addLedgerEntry(organizacao, "ai_essay_grading", 50, "concessao")

        PostgresSupport.tenancy.asUser(usuario) { ctx ->
            ctx.execute(
                "insert into credit_ledger (organization_id, credit_type, amount, reason) values (?, ?, ?, ?)",
                organizacao,
                "ai_essay_grading",
                -50L,
                "estorno",
            )
        }

        assertEquals(0L, saldo("ai_essay_grading"))
        assertEquals(2, quantidadeDeLancamentos(), "os dois lancamentos precisam permanecer no historico")
    }

    @Test
    fun `lancamento de valor zero e recusado`() {
        assertFailsWith<Exception> {
            PostgresSupport.addLedgerEntry(organizacao, "ai_exam_generation", 0)
        }
    }

    @Test
    fun `lancamentos de outra organizacao nao sao visiveis`() {
        val alheia = PostgresSupport.createOrganization(kind = "school", name = "Escola Alheia")
        PostgresSupport.addLedgerEntry(organizacao, "ai_exam_generation", 10)
        PostgresSupport.addLedgerEntry(alheia, "ai_exam_generation", 999)

        val visiveis = PostgresSupport.tenancy.asUser(usuario) { ctx ->
            ctx.fetch("select organization_id from credit_ledger")
                .map { it.get(0, UUID::class.java) }
        }

        assertEquals(listOf(organizacao), visiveis)
    }

    private fun saldo(creditType: String): Long =
        PostgresSupport.tenancy.asUser(usuario) { ctx ->
            ctx.fetchOne(
                "select coalesce(sum(amount), 0) from credit_ledger where credit_type = ?",
                creditType,
            )!!.get(0, Long::class.java)
        }

    private fun quantidadeDeLancamentos(): Int =
        PostgresSupport.adminDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from credit_ledger").use { rows ->
                    check(rows.next())
                    rows.getInt(1)
                }
            }
        }
}
