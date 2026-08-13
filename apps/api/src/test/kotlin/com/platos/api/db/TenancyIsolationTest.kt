package com.platos.api.db

import com.platos.api.support.PostgresSupport
import org.jooq.exception.DataAccessException
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Os cenarios de "Isolamento por organizacao imposto no armazenamento" (specs/identity).
 *
 * Todas as consultas abaixo sao emitidas SEM filtro de organizacao. Se alguma retornar linha
 * alheia, o modelo de tenancy esta quebrado — e nenhuma quantidade de `where` espalhado pelos
 * repositorios consertaria isso de forma confiavel.
 */
class TenancyIsolationTest {

    private lateinit var usuarioA: UUID
    private lateinit var usuarioSemVinculo: UUID
    private lateinit var orgA: UUID
    private lateinit var orgB: UUID

    @BeforeTest
    fun setUp() {
        PostgresSupport.start()
        PostgresSupport.reset()

        usuarioA = PostgresSupport.createUser("sub-a", "a@escola.br")
        usuarioSemVinculo = PostgresSupport.createUser("sub-sem-vinculo")
        orgA = PostgresSupport.createOrganization(name = "Escola A")
        orgB = PostgresSupport.createOrganization(name = "Escola B")

        PostgresSupport.addMembership(usuarioA, orgA, "teacher")

        PostgresSupport.addLedgerEntry(orgA, "ai_exam_generation", 100)
        PostgresSupport.addLedgerEntry(orgB, "ai_exam_generation", 500)
    }

    @Test
    fun `leitura cruzada entre organizacoes devolve apenas a organizacao do usuario`() {
        val organizacoes = PostgresSupport.tenancy.asUser(usuarioA) { ctx ->
            ctx.fetch("select organization_id from credit_ledger")
                .map { it.get(0, UUID::class.java) }
        }

        assertEquals(listOf(orgA), organizacoes, "vazou lancamento de outra organizacao")
    }

    @Test
    fun `organizacao alheia nao aparece na listagem sem filtro`() {
        val organizacoes = PostgresSupport.tenancy.asUser(usuarioA) { ctx ->
            ctx.fetch("select id from organization").map { it.get(0, UUID::class.java) }
        }

        assertEquals(listOf(orgA), organizacoes)
    }

    @Test
    fun `escrita em organizacao alheia e recusada`() {
        assertFailsWith<DataAccessException> {
            PostgresSupport.tenancy.asUser(usuarioA) { ctx ->
                ctx.execute(
                    "insert into credit_ledger (organization_id, credit_type, amount, reason) values (?, ?, ?, ?)",
                    orgB,
                    "ai_exam_generation",
                    10L,
                    "tentativa cruzada",
                )
            }
        }

        val lancamentosDeB = contarComoAdmin("select count(*) from credit_ledger where organization_id = ?", orgB)
        assertEquals(1, lancamentosDeB, "nenhuma linha nova pode ter sido criada na organizacao B")
    }

    @Test
    fun `escrita na propria organizacao e permitida`() {
        PostgresSupport.tenancy.asUser(usuarioA) { ctx ->
            ctx.execute(
                "insert into credit_ledger (organization_id, credit_type, amount, reason) values (?, ?, ?, ?)",
                orgA,
                "ai_exam_generation",
                -5L,
                "consumo",
            )
        }

        assertEquals(2, contarComoAdmin("select count(*) from credit_ledger where organization_id = ?", orgA))
    }

    @Test
    fun `autoria nao concede acesso apos remocao do vinculo`() {
        // §3.2: created_by_user_id e metadado. Jamais chave de autorizacao.
        val orgCriadaPeloUsuario = PostgresSupport.createOrganization(
            name = "Criada por A",
            createdBy = usuarioA,
        )
        val vinculo = PostgresSupport.addMembership(usuarioA, orgCriadaPeloUsuario, "owner")

        val visivelComVinculo = PostgresSupport.tenancy.asUser(usuarioA) { ctx ->
            ctx.fetch("select id from organization").map { it.get(0, UUID::class.java) }
        }
        assertTrue(orgCriadaPeloUsuario in visivelComVinculo, "com vinculo, deveria enxergar")

        PostgresSupport.asAdmin { connection ->
            connection.prepareStatement("delete from membership where id = ?").use { statement ->
                statement.setObject(1, vinculo)
                statement.executeUpdate()
            }
        }

        val visivelSemVinculo = PostgresSupport.tenancy.asUser(usuarioA) { ctx ->
            ctx.fetch("select id from organization").map { it.get(0, UUID::class.java) }
        }
        assertTrue(
            orgCriadaPeloUsuario !in visivelSemVinculo,
            "sem vinculo, a autoria nao pode conceder acesso",
        )
    }

    @Test
    fun `usuario sem vinculo nao enxerga nenhuma linha de dominio`() {
        PostgresSupport.tenancy.asUser(usuarioSemVinculo) { ctx ->
            assertEquals(0, ctx.fetch("select id from organization").size)
            assertEquals(0, ctx.fetch("select id from membership").size)
            assertEquals(0, ctx.fetch("select id from credit_ledger").size)
            assertEquals(0, ctx.fetch("select id from subscription").size)
        }
    }

    @Test
    fun `consulta fora do contexto de tenancy nao devolve nada`() {
        // Negacao por omissao: sem app.current_user_id, app_current_user_id() e NULL e nenhuma
        // politica casa. Vale mesmo que alguem contorne a API do modulo e use o DataSource direto.
        PostgresSupport.appDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                listOf("organization", "membership", "credit_ledger", "subscription", "app_user").forEach { tabela ->
                    statement.executeQuery("select count(*) from $tabela").use { rows ->
                        check(rows.next())
                        assertEquals(0, rows.getInt(1), "$tabela vazou fora do contexto de tenancy")
                    }
                }
            }
        }
    }

    private fun contarComoAdmin(sql: String, vararg args: Any?): Int =
        PostgresSupport.adminDataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getInt(1)
                }
            }
        }
}
