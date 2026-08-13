package com.platos.api.billing

import com.platos.api.support.PostgresSupport
import com.platos.api.support.TestDependencies
import java.nio.file.Files
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Os cenarios de "Direitos resolvidos a partir de planos versionados" (specs/billing). */
class EntitlementResolverTest {

    private lateinit var resolver: EntitlementResolver
    private lateinit var usuario: UUID
    private lateinit var organizacao: UUID

    @BeforeTest
    fun setUp() {
        PostgresSupport.start()
        PostgresSupport.reset()
        resolver = EntitlementResolver(PlanCatalog.load(TestDependencies.plansDir))

        usuario = PostgresSupport.createUser("sub-billing", "billing@escola.br")
        organizacao = PostgresSupport.createOrganization(kind = "school", name = "Escola")
        PostgresSupport.addMembership(usuario, organizacao, "owner")
    }

    @Test
    fun `organizacao com plano reflete o arquivo versionado somado ao saldo do ledger`() {
        PostgresSupport.createSubscription(organizacao, plan = "basic")
        PostgresSupport.addLedgerEntry(organizacao, "ai_exam_generation", 15, "pacote avulso")

        val direitos = resolve()

        assertEquals("basic", direitos.plan)
        assertTrue(direitos.hasPlan)
        // 20 do plano + 15 comprados avulso, no mesmo ledger (D25).
        assertEquals(35L, direitos["ai_exam_generation"]!!.available)
        assertEquals(15L, direitos.creditBalances["ai_exam_generation"])
        // Ilimitado nao tem quantidade disponivel.
        assertNull(direitos["omr_grading"]!!.available)
        assertTrue(direitos["omr_grading"]!!.unlimited)
        // Desabilitado no Basic.
        assertEquals(false, direitos["ai_essay_grading"]!!.enabled)
        assertEquals(0L, direitos["ai_essay_grading"]!!.available)
    }

    @Test
    fun `organizacao sem assinatura devolve conjunto vazio sem erro`() {
        val direitos = resolve()

        assertEquals(ResolvedEntitlements.NO_PLAN, direitos)
        assertNull(direitos.plan)
        assertEquals(false, direitos.hasPlan)
        assertTrue(direitos.entitlements.isEmpty())
    }

    @Test
    fun `assinatura expirada vale como ausencia de plano`() {
        PostgresSupport.createSubscription(
            organizacao,
            plan = "pro",
            status = "active",
            periodStart = OffsetDateTime.now().minusDays(60),
            periodEnd = OffsetDateTime.now().minusDays(30),
        )

        assertEquals(ResolvedEntitlements.NO_PLAN, resolve())
    }

    @Test
    fun `assinatura em estado nao ativo vale como ausencia de plano`() {
        PostgresSupport.createSubscription(organizacao, plan = "pro", status = "canceled")

        assertEquals(ResolvedEntitlements.NO_PLAN, resolve())
    }

    @Test
    fun `plano sem arquivo correspondente falha explicitamente`() {
        PostgresSupport.createSubscription(organizacao, plan = "enterprise")

        val erro = assertFailsWith<UnknownPlanException> { resolve() }

        assertEquals("enterprise", erro.plan)
    }

    @Test
    fun `direitos sao independentes por contexto organizacional`() {
        // §3.4 e D40: um professor com Pro pessoal que tambem e membro de uma escola no Basic tem
        // direitos diferentes em cada contexto. Sem conflito, sem merge de assinatura.
        val pessoal = PostgresSupport.createOrganization(kind = "personal", name = "Pessoal")
        PostgresSupport.addMembership(usuario, pessoal, "owner")

        PostgresSupport.createSubscription(pessoal, plan = "pro")
        PostgresSupport.createSubscription(organizacao, plan = "basic")

        val direitosPessoais = resolve(pessoal)
        val direitosDaEscola = resolve(organizacao)

        assertEquals("pro", direitosPessoais.plan)
        assertEquals("basic", direitosDaEscola.plan)
        assertEquals(true, direitosPessoais["ai_essay_grading"]!!.enabled)
        assertEquals(false, direitosDaEscola["ai_essay_grading"]!!.enabled)
        assertEquals(100L, direitosPessoais["ai_exam_generation"]!!.available)
        assertEquals(20L, direitosDaEscola["ai_exam_generation"]!!.available)
    }

    @Test
    fun `organizacao alheia nao tem seus direitos resolvidos`() {
        val alheia = PostgresSupport.createOrganization(kind = "school", name = "Escola Alheia")
        PostgresSupport.createSubscription(alheia, plan = "pro")

        // A RLS de subscription e o que impede isso; o resolver nao escreve filtro de autorizacao.
        assertEquals(ResolvedEntitlements.NO_PLAN, resolve(alheia))
    }

    @Test
    fun `alterar direitos de um plano e mudar arquivo versionado, nao linha no banco`() {
        PostgresSupport.createSubscription(organizacao, plan = "basic")
        assertEquals(20L, resolve()["ai_exam_generation"]!!.available)

        // Mesma assinatura, mesmo banco, arquivo do plano alterado: os direitos mudam. A fonte do
        // direito e o arquivo versionado (D40).
        val planosAlterados = Files.createTempDirectory("plans-alterados")
        Files.list(TestDependencies.plansDir).use { stream ->
            stream.forEach { origem -> Files.copy(origem, planosAlterados.resolve(origem.fileName)) }
        }
        val basic = planosAlterados.resolve("basic.yaml")
        basic.writeText(basic.readText().replace("period_quota: 20", "period_quota: 50"))

        val direitos = PostgresSupport.tenancy.asUser(usuario) { ctx ->
            EntitlementResolver(PlanCatalog.load(planosAlterados)).resolve(ctx, organizacao)
        }
        assertEquals(50L, direitos["ai_exam_generation"]!!.available)

        // E o outro lado: nao ha coluna de direito no schema, entao nenhum UPDATE muda quota.
        val colunas = PostgresSupport.adminDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    """
                    select table_name || '.' || column_name
                    from information_schema.columns
                    where table_schema = 'public'
                    """.trimIndent(),
                ).use { rows ->
                    buildList { while (rows.next()) add(rows.getString(1)) }
                }
            }
        }
        val suspeitas = colunas.filter { coluna ->
            listOf("quota", "entitlement", "unlimited", "enabled").any { coluna.contains(it) }
        }
        assertEquals(
            emptyList(),
            suspeitas,
            "direito de plano nao pode virar linha editavel no banco",
        )
    }

    private fun resolve(organizationId: UUID = organizacao): ResolvedEntitlements =
        PostgresSupport.tenancy.asUser(usuario) { ctx -> resolver.resolve(ctx, organizationId) }
}
