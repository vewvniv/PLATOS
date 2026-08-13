package com.platos.api.identity

import com.platos.api.support.PostgresSupport
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Os cenarios de "Provisionamento idempotente da organizacao pessoal" (specs/identity). */
class IdentityBootstrapTest {

    private val bootstrap by lazy { IdentityBootstrap(PostgresSupport.appDataSource) }

    @BeforeTest
    fun setUp() {
        PostgresSupport.start()
        PostgresSupport.reset()
    }

    @Test
    fun `primeiro acesso cria organizacao pessoal com papel owner`() {
        val userId = bootstrap.bootstrap("sub-novo", "maria@escola.br", "Maria Silva")

        val vinculos = vinculosDe(userId)
        assertEquals(1, vinculos.size)
        assertEquals("personal", vinculos.single().kind)
        assertEquals("owner", vinculos.single().role)
        assertEquals("Maria Silva", vinculos.single().organizationName)
    }

    @Test
    fun `acessos subsequentes nao criam organizacao adicional`() {
        val primeiro = bootstrap.bootstrap("sub-repetido", "joao@escola.br", "Joao")
        val segundo = bootstrap.bootstrap("sub-repetido", "joao@escola.br", "Joao")
        val terceiro = bootstrap.bootstrap("sub-repetido", "joao@escola.br", "Joao")

        assertEquals(primeiro, segundo)
        assertEquals(primeiro, terceiro)
        assertEquals(1, vinculosDe(primeiro).size)
        assertEquals(1, contar("select count(*) from organization"))
        assertEquals(1, contar("select count(*) from app_user"))
    }

    @Test
    fun `acessos concorrentes do mesmo usuario novo criam exatamente uma organizacao`() {
        val threads = 8
        val barreira = CyclicBarrier(threads)
        val executor = Executors.newFixedThreadPool(threads)

        val resultados = try {
            executor.invokeAll(
                (1..threads).map {
                    Callable {
                        barreira.await(10, TimeUnit.SECONDS)
                        bootstrap.bootstrap("sub-concorrente", "ana@escola.br", "Ana")
                    }
                },
            ).map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        // Nenhuma chamada estourou: violacao de restricao unica nao pode vazar ao cliente.
        assertEquals(1, resultados.toSet().size, "todas as chamadas devem devolver o mesmo app_user.id")
        assertEquals(1, contar("select count(*) from app_user"))
        assertEquals(1, contar("select count(*) from organization where kind = 'personal'"))
        assertEquals(1, contar("select count(*) from membership"))
    }

    @Test
    fun `usuario que so pertence a organizacao escolar nao ganha organizacao pessoal`() {
        val userId = PostgresSupport.createUser("sub-so-escola", "prof@escola.br")
        val escola = PostgresSupport.createOrganization(kind = "school", name = "Escola Municipal")
        PostgresSupport.addMembership(userId, escola, "teacher")

        val retornado = bootstrap.bootstrap("sub-so-escola", "prof@escola.br", "Professor")

        assertEquals(userId, retornado)
        assertEquals(0, contar("select count(*) from organization where kind = 'personal'"))
        assertEquals(1, vinculosDe(userId).size)
    }

    @Test
    fun `nome da organizacao pessoal cai para a parte local do email quando nao ha nome`() {
        val userId = bootstrap.bootstrap("sub-sem-nome", "carla.souza@escola.br", null)

        assertEquals("carla.souza", vinculosDe(userId).single().organizationName)
    }

    @Test
    fun `auth_subject vazio e recusado`() {
        val erro = runCatching { bootstrap.bootstrap("   ", null, null) }.exceptionOrNull()

        assertNotNull(erro, "auth_subject vazio precisa falhar explicitamente")
        assertEquals(0, contar("select count(*) from app_user"))
    }

    private data class Vinculo(val organizationName: String, val kind: String, val role: String)

    private fun vinculosDe(userId: UUID): List<Vinculo> =
        PostgresSupport.adminDataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                select o.name, o.kind, m.role
                from membership m
                join organization o on o.id = m.organization_id
                where m.user_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            add(Vinculo(rows.getString(1), rows.getString(2), rows.getString(3)))
                        }
                    }
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
