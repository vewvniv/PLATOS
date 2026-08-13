package com.platos.api.db

import com.platos.api.support.PostgresSupport
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * D-0.2. Este e o teste que impede que todos os outros sejam decorativos.
 *
 * Um superusuario — ou qualquer papel com BYPASSRLS — ignora politica de RLS por completo. Se a
 * suite de isolamento rodasse com um papel desses, ela passaria mesmo com as politicas escritas
 * erradas, ou apagadas. A asserção precisa existir aqui, e nao so na configuracao de producao.
 */
class ConnectionRoleTest {

    @BeforeTest
    fun setUp() = PostgresSupport.start()

    @Test
    fun `o papel de conexao da aplicacao nao e superusuario nem tem bypassrls`() {
        PostgresSupport.appDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    """
                    select current_user, r.rolsuper, r.rolbypassrls
                    from pg_roles r
                    where r.rolname = current_user
                    """.trimIndent(),
                ).use { rows ->
                    check(rows.next())

                    assertEquals(PostgresSupport.APP_ROLE, rows.getString("current_user"))
                    assertFalse(rows.getBoolean("rolsuper"), "app_backend nao pode ser superusuario")
                    assertFalse(rows.getBoolean("rolbypassrls"), "app_backend nao pode ter BYPASSRLS")
                }
            }
        }
    }

    @Test
    fun `o papel de conexao nao e dono das tabelas de dominio`() {
        PostgresSupport.appDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    """
                    select count(*) as proprias
                    from pg_tables
                    where schemaname = 'public'
                      and tableowner = current_user
                    """.trimIndent(),
                ).use { rows ->
                    check(rows.next())
                    assertEquals(
                        0,
                        rows.getInt("proprias"),
                        "app_backend nao pode ser dono de tabela: dono contorna RLS quando FORCE nao esta ativo",
                    )
                }
            }
        }
    }

    @Test
    fun `todas as tabelas de dominio tem RLS habilitada e forcada`() {
        PostgresSupport.adminDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    """
                    select c.relname, c.relrowsecurity, c.relforcerowsecurity
                    from pg_class c
                    join pg_namespace n on n.oid = c.relnamespace
                    where n.nspname = 'public'
                      and c.relkind = 'r'
                      and c.relname in ('app_user', 'organization', 'membership', 'subscription', 'credit_ledger')
                    """.trimIndent(),
                ).use { rows ->
                    var tabelas = 0
                    while (rows.next()) {
                        tabelas++
                        val nome = rows.getString("relname")
                        assertEquals(true, rows.getBoolean("relrowsecurity"), "$nome sem RLS habilitada")
                        assertEquals(true, rows.getBoolean("relforcerowsecurity"), "$nome sem RLS forcada")
                    }
                    assertEquals(5, tabelas, "faltou tabela de dominio na verificacao de RLS")
                }
            }
        }
    }
}
