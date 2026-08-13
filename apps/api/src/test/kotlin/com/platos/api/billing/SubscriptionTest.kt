package com.platos.api.billing

import com.platos.api.support.PostgresSupport
import java.sql.SQLException
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Os cenarios de "Assinatura pertence a organizacao" (specs/billing). */
class SubscriptionTest {

    private lateinit var organizacao: UUID

    @BeforeTest
    fun setUp() {
        PostgresSupport.start()
        PostgresSupport.reset()
        organizacao = PostgresSupport.createOrganization(kind = "school", name = "Escola")
    }

    @Test
    fun `segunda assinatura nao encerrada na mesma organizacao e recusada`() {
        PostgresSupport.createSubscription(organizacao, plan = "basic", status = "active")

        assertFailsWith<SQLException> {
            PostgresSupport.createSubscription(organizacao, plan = "pro", status = "active")
        }

        assertEquals(1, quantidadeDeAssinaturas())
    }

    @Test
    fun `assinatura em past_due tambem conta como aberta`() {
        PostgresSupport.createSubscription(organizacao, plan = "basic", status = "past_due")

        assertFailsWith<SQLException> {
            PostgresSupport.createSubscription(organizacao, plan = "pro", status = "active")
        }
    }

    @Test
    fun `nova assinatura e permitida depois que a anterior e encerrada`() {
        PostgresSupport.createSubscription(organizacao, plan = "basic", status = "canceled")
        PostgresSupport.createSubscription(organizacao, plan = "pro", status = "active")

        assertEquals(2, quantidadeDeAssinaturas())
    }

    @Test
    fun `assinatura sem organizacao e recusada`() {
        assertFailsWith<SQLException> {
            PostgresSupport.createSubscription(organizationId = null, plan = "basic")
        }

        assertEquals(0, quantidadeDeAssinaturas())
    }

    @Test
    fun `periodo invertido e recusado`() {
        val agora = java.time.OffsetDateTime.now()

        assertFailsWith<SQLException> {
            PostgresSupport.createSubscription(
                organizacao,
                plan = "basic",
                periodStart = agora,
                periodEnd = agora.minusDays(1),
            )
        }
    }

    @Test
    fun `estado fora do conjunto permitido e recusado`() {
        assertFailsWith<SQLException> {
            PostgresSupport.createSubscription(organizacao, plan = "basic", status = "pendente")
        }
    }

    private fun quantidadeDeAssinaturas(): Int =
        PostgresSupport.adminDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from subscription").use { rows ->
                    check(rows.next())
                    rows.getInt(1)
                }
            }
        }
}
