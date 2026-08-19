package com.platos.api.db

import com.platos.api.support.PostgresSupport
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    /**
     * Derivado do catalogo, e **nao** de uma lista escrita a mao.
     *
     * A versao anterior enumerava as cinco tabelas da fatia 0 num `in (...)` e afirmava que eram
     * cinco. Uma tabela nova — a fatia 2 cria varias — nascia fora da verificacao sem derrubar
     * nada: a guarda continuava verde afirmando sobre um universo que tinha parado de crescer.
     * Era o proprio risco que este arquivo existe para cobrir, entrando pela porta dos fundos.
     *
     * Agora o universo e "toda tabela base de `public`". Tabela nova nasce coberta, e quem quiser
     * excluir alguma precisa dizer isso em voz alta aqui.
     */
    @Test
    fun `toda tabela de public tem RLS habilitada e forcada`() {
        PostgresSupport.adminDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    """
                    select c.relname, c.relrowsecurity, c.relforcerowsecurity
                    from pg_class c
                    join pg_namespace n on n.oid = c.relnamespace
                    where n.nspname = 'public'
                      and c.relkind = 'r'
                    order by c.relname
                    """.trimIndent(),
                ).use { rows ->
                    val semRls = mutableListOf<String>()
                    var tabelas = 0
                    while (rows.next()) {
                        tabelas++
                        val nome = rows.getString("relname")
                        if (!rows.getBoolean("relrowsecurity")) semRls += "$nome sem RLS habilitada"
                        if (!rows.getBoolean("relforcerowsecurity")) semRls += "$nome sem RLS forcada"
                    }
                    // Sem este piso a consulta poderia voltar vazia — por schema errado ou migration
                    // nao aplicada — e o teste passaria sem ter olhado nada.
                    assertTrue(tabelas >= 5, "esperava ao menos as 5 tabelas da fatia 0, vi $tabelas")
                    assertEquals(emptyList<String>(), semRls.toList(), "tabela de dominio sem RLS forcada")
                }
            }
        }
    }
}
