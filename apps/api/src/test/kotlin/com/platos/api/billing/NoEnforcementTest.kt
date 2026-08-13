package com.platos.api.billing

import com.platos.api.module
import com.platos.api.support.JwtTestFixture
import com.platos.api.support.PostgresSupport
import com.platos.api.support.TestDependencies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.time.OffsetDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Os cenarios de "Nenhum enforcement nesta capacidade ainda" (specs/billing).
 *
 * A inercia desta fatia e comportamento verificado, nao codigo morto silencioso. Quando a fatia 8
 * ligar o enforcement, estes testes sao os que devem ser deliberadamente reescritos — e nao passar
 * despercebidos.
 */
class NoEnforcementTest {

    @BeforeTest
    fun setUp() {
        PostgresSupport.start()
        PostgresSupport.reset()
    }

    @Test
    fun `organizacao sem plano opera normalmente`() = testApplication {
        val dependencias = TestDependencies.create()
        application { module(dependencias, JwtTestFixture.jwkProvider) }

        val resposta = client.get("/me/organizations") {
            header(HttpHeaders.Authorization, "Bearer ${JwtTestFixture.token("sub-sem-plano", "s@escola.br")}")
        }

        // Nao ha assinatura nenhuma no banco e a requisicao ainda assim e atendida.
        assertEquals(HttpStatusCode.OK, resposta.status)
        assertEquals(0, contar("select count(*) from subscription"))
    }

    @Test
    fun `virada de periodo nao movimenta o ledger`() {
        val organizacao = PostgresSupport.createOrganization(kind = "school", name = "Escola")
        PostgresSupport.createSubscription(
            organizacao,
            plan = "pro",
            periodStart = OffsetDateTime.now().minusDays(60),
            periodEnd = OffsetDateTime.now().minusDays(30),
        )

        val usuario = PostgresSupport.createUser("sub-virada", "v@escola.br")
        PostgresSupport.addMembership(usuario, organizacao, "owner")

        // Resolver os direitos e a unica operacao de billing que existe nesta fatia. Ela nao pode
        // conceder credito na virada do periodo (D40 preve isso, mas so na fatia 8).
        val resolver = EntitlementResolver(PlanCatalog.load(TestDependencies.plansDir))
        PostgresSupport.tenancy.asUser(usuario) { ctx -> resolver.resolve(ctx, organizacao) }

        assertEquals(0, contar("select count(*) from credit_ledger"))
    }

    @Test
    fun `resolver direitos nao debita creditos`() {
        val organizacao = PostgresSupport.createOrganization(kind = "school", name = "Escola")
        PostgresSupport.createSubscription(organizacao, plan = "pro")
        PostgresSupport.addLedgerEntry(organizacao, "ai_exam_generation", 10, "concessao inicial")

        val usuario = PostgresSupport.createUser("sub-consulta", "c@escola.br")
        PostgresSupport.addMembership(usuario, organizacao, "owner")

        val resolver = EntitlementResolver(PlanCatalog.load(TestDependencies.plansDir))
        repeat(5) {
            PostgresSupport.tenancy.asUser(usuario) { ctx -> resolver.resolve(ctx, organizacao) }
        }

        assertEquals(1, contar("select count(*) from credit_ledger"))
        assertEquals(10, contar("select coalesce(sum(amount), 0) from credit_ledger"))
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
