package com.platos.api.db

import com.platos.api.support.PostgresSupport
import org.jooq.exception.DataAccessException
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Isolamento por organizacao das tres tabelas da fatia 2a.
 *
 * O requisito e o mesmo que `TenancyIsolationTest` cobre para identidade e billing — §3.2: toda
 * tabela de dominio e autorizada por `organization_id`. Tabela nova nao herda a verificacao junto
 * com a politica: `ConnectionRoleTest` afirma que a RLS **existe** e esta forcada, e isso e outra
 * coisa de ela estar **certa**. Uma politica escrita com o `organization_id` da tabela errada
 * satisfaria o catalogo e vazaria prova alheia.
 *
 * Como la, nenhuma consulta abaixo leva filtro de organizacao.
 */
class ExamTenancyIsolationTest {

    private lateinit var usuarioA: UUID
    private lateinit var usuarioSemVinculo: UUID
    private lateinit var orgA: UUID
    private lateinit var orgB: UUID
    private lateinit var provaA: UUID
    private lateinit var provaB: UUID

    @BeforeTest
    fun setUp() {
        PostgresSupport.start()
        PostgresSupport.reset()

        usuarioA = PostgresSupport.createUser("sub-a-exam")
        usuarioSemVinculo = PostgresSupport.createUser("sub-sem-vinculo-exam")
        orgA = PostgresSupport.createOrganization(name = "Escola A")
        orgB = PostgresSupport.createOrganization(name = "Escola B")
        PostgresSupport.addMembership(usuarioA, orgA, "teacher")

        provaA = PostgresSupport.createExam(orgA, "prova-da-escola-a")
        provaB = PostgresSupport.createExam(orgB, "prova-da-escola-b")

        PostgresSupport.publishPackage(orgA, provaA, """{"meta":{"exam_id":"prova-da-escola-a"}}""")
        PostgresSupport.publishPackage(orgB, provaB, """{"meta":{"exam_id":"prova-da-escola-b"}}""")

        PostgresSupport.addRosterEntry(orgA, provaA, "tok-a", "Ana Souza", "3B")
        PostgresSupport.addRosterEntry(orgB, provaB, "tok-b", "Bruno Lima", "2A")
    }

    @Test
    fun `a prova alheia nao aparece na listagem sem filtro`() {
        val provas = PostgresSupport.tenancy.asUser(usuarioA) { ctx ->
            ctx.fetch("select id from exam").map { it.get(0, UUID::class.java) }
        }

        assertEquals(listOf(provaA), provas, "vazou prova de outra organizacao")
    }

    @Test
    fun `o pacote alheio nao aparece na listagem sem filtro`() {
        val organizacoes = PostgresSupport.tenancy.asUser(usuarioA) { ctx ->
            ctx.fetch("select organization_id from exam_package")
                .map { it.get(0, UUID::class.java) }
        }

        assertEquals(listOf(orgA), organizacoes, "vazou pacote de outra organizacao")
    }

    @Test
    fun `o roster alheio nao aparece na listagem sem filtro`() {
        val nomes = PostgresSupport.tenancy.asUser(usuarioA) { ctx ->
            ctx.fetch("select display_name from exam_roster").map { it.get(0, String::class.java) }
        }

        assertEquals(listOf("Ana Souza"), nomes, "vazou dado pessoal de outra organizacao")
    }

    @Test
    fun `publicar em organizacao alheia e recusado`() {
        assertFailsWith<DataAccessException> {
            PostgresSupport.tenancy.asUser(usuarioA) { ctx ->
                ctx.execute(
                    "insert into exam (organization_id, short_id, title) values (?, ?, ?)",
                    orgB,
                    "prova-intrusa",
                    "Prova intrusa",
                )
            }
        }

        assertEquals(1, contarComoAdmin("select count(*) from exam where organization_id = ?", orgB))
    }

    /**
     * A chave estrangeira composta em acao: nao basta a politica olhar `organization_id`.
     *
     * Sem `references exam (id, organization_id)`, um membro da organizacao A poderia gravar uma
     * linha com o **seu** `organization_id` apontando a prova da organizacao B. A politica de RLS
     * aprovaria — ela ve o organization_id da propria linha, que esta correto —, e o vinculo entre
     * as duas organizacoes ficaria feito dentro do banco.
     */
    @Test
    fun `roster com organizacao propria nao pode apontar prova alheia`() {
        assertFailsWith<DataAccessException> {
            PostgresSupport.tenancy.asUser(usuarioA) { ctx ->
                ctx.execute(
                    "insert into exam_roster (organization_id, exam_id, student_token, display_name) " +
                        "values (?, ?, ?, ?)",
                    orgA,
                    provaB,
                    "tok-intruso",
                    "Aluno de outra escola",
                )
            }
        }

        assertEquals(1, contarComoAdmin("select count(*) from exam_roster where organization_id = ?", orgA))
    }

    @Test
    fun `usuario sem vinculo nao enxerga prova, pacote nem roster`() {
        PostgresSupport.tenancy.asUser(usuarioSemVinculo) { ctx ->
            assertEquals(0, ctx.fetch("select id from exam").size)
            assertEquals(0, ctx.fetch("select id from exam_package").size)
            assertEquals(0, ctx.fetch("select id from exam_roster").size)
        }
    }

    @Test
    fun `consulta fora do contexto de tenancy nao devolve nada`() {
        // Negacao por omissao: sem app.current_user_id nenhuma politica casa. Vale mesmo para quem
        // contorne o modulo de tenancy e use o DataSource direto.
        PostgresSupport.appDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                listOf("exam", "exam_package", "exam_roster").forEach { tabela ->
                    statement.executeQuery("select count(*) from $tabela").use { rows ->
                        check(rows.next())
                        assertEquals(0, rows.getInt(1), "$tabela vazou fora do contexto de tenancy")
                    }
                }
            }
        }
    }

    private fun contarComoAdmin(sql: String, vararg args: Any?): Int =
        PostgresSupport.adminDataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getInt(1)
                }
            }
        }
}
