package com.platos.api.billing

import com.platos.api.db.generated.tables.references.CREDIT_LEDGER
import com.platos.api.db.generated.tables.references.SUBSCRIPTION
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.util.UUID

data class ResolvedEntitlement(
    val name: String,
    val enabled: Boolean,
    val unlimited: Boolean,
    /** `null` quando ilimitado. Caso contrario, quota do periodo somada ao saldo do ledger. */
    val available: Long?,
)

data class ResolvedEntitlements(
    val plan: String?,
    val entitlements: Map<String, ResolvedEntitlement>,
    val creditBalances: Map<String, Long>,
) {
    val hasPlan: Boolean get() = plan != null

    operator fun get(name: String): ResolvedEntitlement? = entitlements[name]

    companion object {
        /** §3.4: nao ha camada gratuita. Sem assinatura corrente, nao ha direito nenhum. */
        val NO_PLAN = ResolvedEntitlements(plan = null, entitlements = emptyMap(), creditBalances = emptyMap())
    }
}

/**
 * D40 / D-0.8: verificacao de direito em UM UNICO lugar no dominio, nunca espalhada.
 *
 * Nenhum outro ponto do codigo deve ler `subscription` ou `credit_ledger`. Quando a fatia 8 ligar
 * o enforcement, ela muda os chamadores deste ponto — nao reescreve a autorizacao.
 *
 * Nesta fatia o resultado e puramente consultivo: nada e recusado, enfileirado ou degradado em
 * funcao de plano, quota ou saldo.
 *
 * Recebe o [DSLContext] em vez de abrir o proprio porque precisa rodar dentro do contexto de
 * tenancy (D-0.3): a RLS de `subscription` e `credit_ledger` e o que impede uma organizacao de
 * resolver os direitos de outra.
 */
class EntitlementResolver(private val catalog: PlanCatalog) {

    fun resolve(ctx: DSLContext, organizationId: UUID): ResolvedEntitlements {
        val plan = currentPlan(ctx, organizationId) ?: return ResolvedEntitlements.NO_PLAN

        // Lanca UnknownPlanException quando a assinatura aponta para um plano sem arquivo.
        val definition = catalog[plan]
        val balances = creditBalances(ctx, organizationId)

        val entitlements = definition.entitlements.mapValues { (name, entitlement) ->
            val available = when {
                !entitlement.enabled -> 0L
                entitlement.unlimited -> null
                else -> {
                    val quota = entitlement.periodQuota ?: 0L
                    val balance = entitlement.creditType?.let { balances[it] } ?: 0L
                    quota + balance
                }
            }
            ResolvedEntitlement(
                name = name,
                enabled = entitlement.enabled,
                unlimited = entitlement.unlimited,
                available = available,
            )
        }

        return ResolvedEntitlements(plan = plan, entitlements = entitlements, creditBalances = balances)
    }

    /** Assinatura ativa e dentro do periodo corrente. Expirada ou nao ativa vale como ausencia. */
    private fun currentPlan(ctx: DSLContext, organizationId: UUID): String? =
        ctx.select(SUBSCRIPTION.PLAN)
            .from(SUBSCRIPTION)
            .where(SUBSCRIPTION.ORGANIZATION_ID.eq(organizationId))
            .and(SUBSCRIPTION.STATUS.eq(ACTIVE))
            .and(
                DSL.condition(
                    "now() between {0} and {1}",
                    SUBSCRIPTION.CURRENT_PERIOD_START,
                    SUBSCRIPTION.CURRENT_PERIOD_END,
                ),
            )
            .fetchOne(SUBSCRIPTION.PLAN)

    /** Saldo e sempre a soma dos lancamentos (D-0.9), nunca um valor mutavel armazenado. */
    private fun creditBalances(ctx: DSLContext, organizationId: UUID): Map<String, Long> =
        ctx.select(CREDIT_LEDGER.CREDIT_TYPE, DSL.sum(CREDIT_LEDGER.AMOUNT))
            .from(CREDIT_LEDGER)
            .where(CREDIT_LEDGER.ORGANIZATION_ID.eq(organizationId))
            .groupBy(CREDIT_LEDGER.CREDIT_TYPE)
            .fetch()
            .associate { record ->
                (record.value1() ?: "") to (record.value2()?.toLong() ?: 0L)
            }

    private companion object {
        const val ACTIVE = "active"
    }
}
