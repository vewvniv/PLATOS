package com.platos.api.db

import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import java.util.UUID
import javax.sql.DataSource

/**
 * D-0.3: o unico jeito de obter um [DSLContext] e passar por [asUser].
 *
 * Nao existe `DSLContext` injetavel em handler. Esquecer o contexto de tenancy vira erro de
 * compilacao, e nao um vazamento silencioso entre organizacoes — que e a unica classe de bug que
 * esta fatia existe para tornar impossivel.
 *
 * O `set_config(..., true)` e local a transacao: expira no commit ou rollback, entao contexto nao
 * vaza entre checkouts do pool.
 */
class Tenancy(private val dataSource: DataSource) {

    fun <T> asUser(userId: UUID, block: (DSLContext) -> T): T =
        DSL.using(dataSource, SQLDialect.POSTGRES).transactionResult { configuration ->
            val ctx = configuration.dsl()
            ctx.execute(SET_CURRENT_USER, userId.toString())
            block(ctx)
        }

    private companion object {
        const val SET_CURRENT_USER = "select set_config('app.current_user_id', ?, true)"
    }
}
