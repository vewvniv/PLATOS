package com.platos.api.db

import com.platos.api.support.PostgresSupport
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * O modo de identificacao de aluno (ADR-0012), medido contra Postgres real.
 *
 * A regra nao mora na aplicacao: uma chave estrangeira composta amarra o modo da linha de roster ao
 * modo da organizacao, e um `check` recusa matricula quando o modo e codificado. Testar pela
 * aplicacao provaria a aplicacao; estes testes escrevem no banco para provar o banco, que e onde a
 * regra precisa valer — a mesma razao pela qual o isolamento por organizacao e RLS e nao filtro.
 */
class IdentificationModeTest {

    private lateinit var usuario: UUID

    @BeforeTest
    fun setUp() {
        PostgresSupport.start()
        PostgresSupport.reset()
        usuario = PostgresSupport.createUser("sub-modo")
    }

    /** SQL cru pelo dono da tabela: estes testes provam o armazenamento, e nao a aplicacao. */
    private fun executar(sql: String, vararg args: Any?) {
        PostgresSupport.asAdmin { conexao ->
            conexao.prepareStatement(sql).use { comando ->
                args.forEachIndexed { indice, valor -> comando.setObject(indice + 1, valor) }
                comando.execute()
            }
        }
    }

    private fun texto(sql: String, vararg args: Any?): String {
        var resultado = ""
        PostgresSupport.asAdmin { conexao ->
            conexao.prepareStatement(sql).use { comando ->
                args.forEachIndexed { indice, valor -> comando.setObject(indice + 1, valor) }
                comando.executeQuery().use { linhas ->
                    check(linhas.next()) { "consulta nao devolveu linha: $sql" }
                    resultado = linhas.getString(1)
                }
            }
        }
        return resultado
    }

    private fun modoDe(organizacao: UUID): String =
        texto("select identification_mode from organization where id = ?", organizacao)

    @Test
    fun `organizacao recem-criada nasce em modo codificado`() {
        // O cenario que mais importa desta fatia. A politica §3.4 diz que o modo sem identificacao
        // nominal e recomendado como padrao; nascer no outro seria a politica descrevendo um sistema
        // diferente do que existe, e a descoberta so viria quando ja houvesse nome de menor no banco.
        executar("insert into organization (kind, name) values ('school', 'Escola sem modo')")

        assertEquals(
            "coded",
            texto("select identification_mode from organization where name = 'Escola sem modo'"),
        )
    }

    @Test
    fun `matricula e recusada em modo codificado`() {
        val org = PostgresSupport.createOrganization(name = "Escola")
        PostgresSupport.addMembership(usuario, org, "teacher")
        val prova = PostgresSupport.createExam(org, shortId = "prova-cod", title = "Prova")

        val erro = assertFailsWith<java.sql.SQLException> {
            PostgresSupport.addRosterEntry(org, prova, "tok-1", "aluno 17", enrollmentId = "2026-0001")
        }

        assertTrue(
            erro.message.orEmpty().contains("matricula_so_em_modo_nominal"),
            "recusou por outro motivo: ${erro.message}",
        )
    }

    @Test
    fun `matricula e aceita em modo nominal`() {
        // O par positivo. Sem ele, o teste acima passaria com uma restricao que recusa tudo.
        val org = PostgresSupport.createOrganization(name = "Escola", identificationMode = "nominal")
        PostgresSupport.addMembership(usuario, org, "teacher")
        val prova = PostgresSupport.createExam(org, shortId = "prova-nom", title = "Prova")

        val linha = PostgresSupport.addRosterEntry(
            org,
            prova,
            "tok-1",
            "Ana Souza",
            classGroup = "3B",
            enrollmentId = "2026-0001",
        )

        assertTrue(linha.toString().isNotBlank(), "esperava linha criada")
    }

    @Test
    fun `turma continua permitida nos dois modos`() {
        // Turma nao identifica um aluno, e e o que organiza a correcao em lote. Uma restricao que a
        // recusasse junto com a matricula estaria protegendo o que nao precisa de protecao.
        val org = PostgresSupport.createOrganization(name = "Escola")
        PostgresSupport.addMembership(usuario, org, "teacher")
        val prova = PostgresSupport.createExam(org, shortId = "prova-turma", title = "Prova")

        PostgresSupport.addRosterEntry(org, prova, "tok-1", "aluno 17", classGroup = "3B")

        assertEquals(
            "3B",
            texto("select class_group from exam_roster where exam_id = ?", prova),
        )
    }

    @Test
    fun `o modo da organizacao e recuperavel junto do roster`() {
        val org = PostgresSupport.createOrganization(name = "Escola", identificationMode = "nominal")
        PostgresSupport.addMembership(usuario, org, "teacher")
        val prova = PostgresSupport.createExam(org, shortId = "prova-rec", title = "Prova")
        PostgresSupport.addRosterEntry(org, prova, "tok-1", "Ana Souza")

        assertEquals(
            "nominal",
            texto("select identification_mode from exam_roster where exam_id = ?", prova),
        )
    }

    @Test
    fun `virar codificado falha enquanto houver matricula no roster`() {
        // A propriedade mais util da chave estrangeira composta, e ela nao foi projetada para isto:
        // a transicao insegura fica impossivel por construcao, em vez de depender de alguem lembrar
        // de limpar o roster antes de mudar a postura da organizacao.
        val org = PostgresSupport.createOrganization(name = "Escola", identificationMode = "nominal")
        PostgresSupport.addMembership(usuario, org, "teacher")
        val prova = PostgresSupport.createExam(org, shortId = "prova-vira", title = "Prova")
        PostgresSupport.addRosterEntry(org, prova, "tok-1", "Ana Souza", enrollmentId = "2026-0001")

        assertFailsWith<java.sql.SQLException> {
            executar("update organization set identification_mode = 'coded' where id = ?", org)
        }

        assertEquals("nominal", modoDe(org), "a organizacao nao pode ter mudado de modo")
    }

    @Test
    fun `virar codificado funciona quando nao ha matricula`() {
        val org = PostgresSupport.createOrganization(name = "Escola", identificationMode = "nominal")
        PostgresSupport.addMembership(usuario, org, "teacher")
        val prova = PostgresSupport.createExam(org, shortId = "prova-limpa", title = "Prova")
        PostgresSupport.addRosterEntry(org, prova, "tok-1", "Ana Souza")

        executar("update organization set identification_mode = 'coded' where id = ?", org)

        assertEquals("coded", modoDe(org))
        assertEquals(
            "coded",
            texto("select identification_mode from exam_roster where exam_id = ?", prova),
            "o `on update cascade` precisa ter propagado para o roster",
        )
    }

    @Test
    fun `modo fora dos dois valores e recusado`() {
        assertFailsWith<java.sql.SQLException> {
            executar(
                "insert into organization (kind, name, identification_mode) " +
                    "values ('school', 'Escola', 'anonimo')",
            )
        }
    }
}
