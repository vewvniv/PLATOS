package com.platos.api.identity

import java.util.UUID
import javax.sql.DataSource

/**
 * D-0.4: chama a unica funcao privilegiada do banco, explicitamente, uma vez por requisicao
 * autenticada.
 *
 * O *quando* fica aqui, em Kotlin, testavel — nao numa trigger invisivel ao dominio. O *como*
 * fica na funcao, porque RLS nao consegue autorizar a criacao da primeira organizacao de um
 * usuario que ainda nao tem vinculo nenhum.
 *
 * Usa JDBC direto em vez de um [org.jooq.DSLContext] porque esta chamada acontece antes de existir
 * contexto de tenancy, e D-0.3 mantem `DSLContext` acessivel somente de dentro de
 * [com.platos.api.db.Tenancy.asUser].
 *
 * A chamada roda em autocommit, entao o `pg_advisory_xact_lock` da funcao e mantido pela duracao
 * do statement e liberado ao final — que e exatamente a janela que precisa ser serializada.
 */
class IdentityBootstrap(private val dataSource: DataSource) {

    fun bootstrap(authSubject: String, email: String?, displayName: String?): UUID =
        dataSource.connection.use { connection ->
            connection.prepareStatement(CALL).use { statement ->
                statement.setString(1, authSubject)
                statement.setString(2, email)
                statement.setString(3, displayName)
                statement.executeQuery().use { rows ->
                    check(rows.next()) { "bootstrap_identity nao retornou app_user.id" }
                    rows.getObject(1, UUID::class.java)
                }
            }
        }

    private companion object {
        const val CALL = "select public.bootstrap_identity(?, ?, ?)"
    }
}
