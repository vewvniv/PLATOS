package com.platos.api.exam

import com.platos.api.http.dto.RosterEntryDto
import com.platos.api.support.PostgresSupport
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A consulta do roster, contra Postgres real e sob RLS.
 *
 * O instrumento e o mesmo que a 4a e a 4b usam, e a razao e a de sempre: as duas regras que
 * importam aqui — autorizacao por organizacao e o que a consulta **nao** devolve — vivem no SQL e
 * no schema, e um duble concordaria com qualquer coisa que o `select` fizesse.
 *
 * Estes cenarios param na consulta, e nao na rota: o que se mede aqui e a decisao do `select`.
 * `listPublished` e `findPackage` nao tem cenario direto nenhum hoje — sao exercitadas so pela
 * suite de rota —, entao esta e a primeira consulta da base com cobertura no seu proprio nivel.
 */
class RosterQueryTest {

    private val queries = ExamQueries()

    private lateinit var usuario: UUID
    private lateinit var org: UUID

    @BeforeTest
    fun setUp() {
        PostgresSupport.start()
        PostgresSupport.reset()

        usuario = PostgresSupport.createUser("sub-roster")
        // Modo nominal de proposito: e o unico em que turma e matricula podem ser gravadas, e sem
        // elas no banco o cenario que afirma a ausencia delas na resposta nao provaria nada.
        org = PostgresSupport.createOrganization(name = "Escola", identificationMode = "nominal")
        PostgresSupport.addMembership(usuario, org, "teacher")
    }

    private fun comRoster(shortId: String = "mat-7a-2026-1"): UUID {
        val exame = PostgresSupport.createExam(org, shortId, "Prova de Matematica", usuario)
        PostgresSupport.publishPackage(org, exame, "{}")
        PostgresSupport.addRosterEntry(org, exame, "tok-zrd", "Zoraide Buarque", "9Z-noturno", "2026-MAT-7701")
        PostgresSupport.addRosterEntry(org, exame, "tok-hlm", "Hildemar Peçanha", "9Z-noturno", "2026-MAT-7702")
        return exame
    }

    @Test
    fun `o roster de uma prova vem com token e nome`() {
        comRoster()

        val roster = PostgresSupport.tenancy.asUser(usuario) { ctx ->
            queries.findRoster(ctx, org, "mat-7a-2026-1")
        }

        // Ordem afirmada, e nao so o conjunto: a consulta ordena por token para duas leituras do
        // mesmo roster nao chegarem diferentes ao aparelho.
        assertEquals(listOf("tok-hlm", "tok-zrd"), roster.map { it.studentToken })
        assertEquals(listOf("Hildemar Peçanha", "Zoraide Buarque"), roster.map { it.displayName })
    }

    @Test
    fun `turma e matricula nao saem do servidor`() {
        comRoster()

        val roster = PostgresSupport.tenancy.asUser(usuario) { ctx ->
            queries.findRoster(ctx, org, "mat-7a-2026-1")
        }

        // As linhas existem no banco **com** turma e matricula. Sem esta contagem, a varredura
        // abaixo passaria por ausencia de dado, e nao por minimizacao da consulta — que e o
        // sombreamento de fixture que a 4a pagou para aprender a procurar.
        assertEquals(
            2,
            contarComoAdmin("select count(*) from exam_roster where class_group = '9Z-noturno'"),
            "o cenario precisa que turma e matricula estejam gravadas para provar que nao descem",
        )

        // Varredura sobre o **JSON serializado**, e nao sobre campo por campo: e a mesma forma que
        // a rota vai por no corpo, e um campo acrescentado por engano ao DTO cai aqui sem ninguem
        // ter de lembrar de mencionar o nome dele na asercao.
        //
        // A primeira versao desta varredura montava a string com `joinToString` nomeando os dois
        // campos a mao — e portanto **sobreviveria** a mutacao (A) da tarefa 1.3, que e a unica
        // coisa que ela existe para nao sobreviver. Achado ao declarar o conjunto esperado antes de
        // injetar, que e exatamente para isso que a declaracao vem antes.
        val tudo = Json.encodeToString(roster)
        for (proibido in listOf("9Z-noturno", "2026-MAT-7701", "2026-MAT-7702")) {
            assertTrue(proibido !in tudo, "`$proibido` saiu do servidor: $tudo")
        }
    }

    @Test
    fun `prova publicada sem roster devolve lista vazia`() {
        val exame = PostgresSupport.createExam(org, "sem-roster", "Prova sem turma", usuario)
        PostgresSupport.publishPackage(org, exame, "{}")

        val roster = PostgresSupport.tenancy.asUser(usuario) { ctx ->
            queries.findRoster(ctx, org, "sem-roster")
        }

        // Vazia, e nao nula — e o "nao nula" e do **tipo de retorno**, nao desta asercao: `List`
        // sem `?` ja proibe nulo em tempo de compilacao. O que se mede aqui e que a consulta nao
        // trate "prova sem alunos" como prova ausente; a folha avulsa do §7 depende dessa
        // distincao, e quem decide o que vira 404 e a rota, com o pacote na mao.
        assertEquals(emptyList<RosterEntryDto>(), roster)
    }

    @Test
    fun `roster de outra organizacao nao e devolvido`() {
        comRoster()

        val alheia = PostgresSupport.createOrganization(name = "Escola B", identificationMode = "nominal")
        val outro = PostgresSupport.createUser("sub-alheio")
        PostgresSupport.addMembership(outro, alheia, "teacher")
        val provaAlheia = PostgresSupport.createExam(alheia, "prova-alheia", "Prova Alheia", outro)
        PostgresSupport.publishPackage(alheia, provaAlheia, "{}")
        PostgresSupport.addRosterEntry(alheia, provaAlheia, "tok-alheio", "Aluno Alheio")

        val roster = PostgresSupport.tenancy.asUser(usuario) { ctx ->
            queries.findRoster(ctx, alheia, "prova-alheia")
        }

        assertEquals(emptyList<RosterEntryDto>(), roster)
        // A linha existe; o que se mede e que a consulta nao a devolveu para quem nao pertence.
        assertEquals(
            1,
            contarComoAdmin("select count(*) from exam_roster where student_token = 'tok-alheio'"),
            "o cenario precisa que a linha exista para provar que a consulta a recusou",
        )
    }

    @Test
    fun `roster de prova de outra organizacao do mesmo professor`() {
        val exame = comRoster()

        // O professor pertence as **duas** organizacoes — `membership` e N:N por invariante, e um
        // coordenador em duas escolas e o caso comum, nao o exotico.
        val outra = PostgresSupport.createOrganization(name = "Escola B", identificationMode = "nominal")
        PostgresSupport.addMembership(usuario, outra, "teacher")

        // E pede o roster da prova da primeira organizacao **sob o id da segunda**.
        val roster = PostgresSupport.tenancy.asUser(usuario) { ctx ->
            queries.findRoster(ctx, outra, "mat-7a-2026-1")
        }

        // Vazio: e o predicado da consulta que garante isto, e nao a RLS. A RLS libera, porque o
        // chamador e membro da organizacao dona da prova; o que ela nao sabe e qual organizacao
        // veio no caminho. Sem `EXAM.ORGANIZATION_ID` no `where`, a consulta devolveria o roster de
        // uma organizacao sob o identificador da outra — e este e o unico cenario capaz de ver
        // isso, porque `short_id` e unico globalmente e os outros quatro ficam verdes sem o
        // predicado.
        assertEquals(emptyList<RosterEntryDto>(), roster)
        assertEquals(
            2,
            contarComoAdmin("select count(*) from exam_roster where exam_id = ?", exame),
            "o cenario precisa que o roster exista na organizacao dona para provar que nao vazou",
        )
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
