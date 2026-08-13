package com.platos.api.identity

import com.platos.api.support.PostgresSupport
import java.sql.SQLException
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Os cenarios de "Pertencimento N:N entre usuario e organizacao" (specs/identity). */
class MembershipTest {

    private val bootstrap by lazy { IdentityBootstrap(PostgresSupport.appDataSource) }

    @BeforeTest
    fun setUp() {
        PostgresSupport.start()
        PostgresSupport.reset()
    }

    @Test
    fun `usuario pertence a duas organizacoes com papeis independentes`() {
        val userId = bootstrap.bootstrap("sub-duplo", "prof@escola.br", "Professor")
        val escola = PostgresSupport.createOrganization(kind = "school", name = "Escola Estadual")
        PostgresSupport.addMembership(userId, escola, "teacher")

        val vinculos = PostgresSupport.tenancy.asUser(userId) { ctx ->
            ctx.fetch(
                """
                select o.kind, m.role
                from organization o
                join membership m on m.organization_id = o.id
                order by o.kind
                """.trimIndent(),
            ).map { it.get(0, String::class.java) to it.get(1, String::class.java) }
        }

        assertEquals(listOf("personal" to "owner", "school" to "teacher"), vinculos)
    }

    @Test
    fun `entrar numa escola preserva o acervo da organizacao pessoal`() {
        // D39: entrar numa escola e inserir uma linha, nao migrar dados. Nenhuma linha existente
        // pode ter sua organizacao alterada.
        val userId = bootstrap.bootstrap("sub-acervo", "prof@escola.br", "Professor")
        val pessoal = organizacaoPessoalDe(userId)
        PostgresSupport.addLedgerEntry(pessoal, "ai_exam_generation", 42, "acervo anterior")

        val escola = PostgresSupport.createOrganization(kind = "school", name = "Escola Nova")
        PostgresSupport.addMembership(userId, escola, "teacher")

        val lancamentos = PostgresSupport.tenancy.asUser(userId) { ctx ->
            ctx.fetch("select organization_id, amount from credit_ledger")
                .map { it.get(0, UUID::class.java) to it.get(1, Long::class.java) }
        }

        assertEquals(listOf(pessoal to 42L), lancamentos, "o acervo continua atribuido a organizacao pessoal")
        assertTrue(
            PostgresSupport.tenancy.asUser(userId) { ctx -> ctx.fetch("select id from organization").size } == 2,
        )
    }

    @Test
    fun `vinculo duplicado e recusado`() {
        val userId = bootstrap.bootstrap("sub-dup", null, "Professor")
        val escola = PostgresSupport.createOrganization(kind = "school", name = "Escola")
        PostgresSupport.addMembership(userId, escola, "teacher")

        assertFailsWith<SQLException> {
            PostgresSupport.addMembership(userId, escola, "admin")
        }

        assertEquals(
            1,
            contar("select count(*) from membership where user_id = '$userId' and organization_id = '$escola'"),
        )
    }

    @Test
    fun `papel fora do conjunto permitido e recusado`() {
        val userId = bootstrap.bootstrap("sub-papel", null, "Professor")
        val escola = PostgresSupport.createOrganization(kind = "school", name = "Escola")

        assertFailsWith<SQLException> {
            PostgresSupport.addMembership(userId, escola, "diretor")
        }

        assertEquals(0, contar("select count(*) from membership where organization_id = '$escola'"))
    }

    @Test
    fun `tipo de organizacao fora do conjunto permitido e recusado`() {
        assertFailsWith<SQLException> {
            PostgresSupport.createOrganization(kind = "empresa", name = "Invalida")
        }
    }

    private fun organizacaoPessoalDe(userId: UUID): UUID =
        PostgresSupport.adminDataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                select o.id
                from organization o
                join membership m on m.organization_id = o.id
                where m.user_id = ? and o.kind = 'personal'
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getObject(1, UUID::class.java)
                }
            }
        }

    private fun contar(sql: String): Int =
        PostgresSupport.adminDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows ->
                    check(rows.next())
                    rows.getInt(1)
                }
            }
        }
}
