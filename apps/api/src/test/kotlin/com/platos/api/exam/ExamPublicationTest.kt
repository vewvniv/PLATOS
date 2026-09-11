package com.platos.api.exam

import com.platos.api.support.PostgresSupport
import com.platos.domain.exam.ExamDefinition
import com.platos.domain.exam.ExamPackage
import com.platos.domain.exam.ExamPackageException
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A publicacao de ponta a ponta: a fixture versionada vira pacote gravado (D-2a.6).
 *
 * Os cenarios "O pacote nao carrega nome de aluno", "Mudar o roster nao invalida o pacote" e a
 * recusa de republicacao, todos contra Postgres real e sob RLS — a publicacao passa por
 * `Tenancy.asUser` como qualquer outra escrita, e nao por um caminho privilegiado.
 */
class ExamPublicationTest {

    /**
     * O mesmo valor que `ExamPackageTest` afirma nos tres alvos do dominio.
     *
     * Repetir o literal aqui e o ponto: se o servidor gravasse um hash diferente do que JVM, Node e
     * Android calculam, o pacote deixaria de ser verificavel no dispositivo que o consome — e a
     * divergencia apareceria so na fatia 4, com pacote ja distribuido. Regravar junto com a fixture.
     */
    private val hashDaFixture = "26612ad52b0cb967309f49354e9858c501ad7a1b0c7b46db874c05d348e6909a"

    private val definicao: ExamDefinition = Json { ignoreUnknownKeys = false }.decodeFromString(
        ExamDefinition.serializer(),
        File(
            System.getProperty("platos.fixtures.dir")
                ?: error("systemProperty platos.fixtures.dir nao definida"),
            "prova-referencia.json",
        ).readText(),
    )

    private val turma = listOf(
        RosterEntry("tok-zrd", "Zoraide Buarque", "9Z-noturno", "2026-MAT-7701"),
        RosterEntry("tok-hlm", "Hildemar Peçanha", "9Z-noturno", "2026-MAT-7702"),
    )

    private lateinit var usuario: UUID
    private lateinit var org: UUID
    private lateinit var publicacao: ExamPublication

    @BeforeTest
    fun setUp() {
        PostgresSupport.start()
        PostgresSupport.reset()

        usuario = PostgresSupport.createUser("sub-publicacao")
        // Modo nominal: estes testes existem para provar que nome, turma e matricula ficam
        // fora do artefato imutavel, e para isso precisam existir. ADR-0012 faz o padrao ser
        // `coded`, que recusa matricula.
        org = PostgresSupport.createOrganization(name = "Escola", identificationMode = "nominal")
        PostgresSupport.addMembership(usuario, org, "teacher")
        publicacao = ExamPublication(PostgresSupport.tenancy)
    }

    @Test
    fun `publicar a prova versionada grava o pacote com o hash que os tres alvos afirmam`() {
        val publicado = publicacao.publish(usuario, org, definicao, title = "Prova de referencia")

        assertEquals(hashDaFixture, publicado.contentHash, "o servidor gravou outro hash")
        assertEquals(hashDaFixture, coluna("content_hash"))
        assertEquals(definicao.id, coluna("short_id", "exam"))

        // O hash da coluna conferido contra os BYTES da coluna, com o MessageDigest da JVM. E o que
        // o dispositivo da fatia 4 vai fazer ao receber o pacote: se o gravado nao fechar com o
        // declarado, ele recusa. Comparar a coluna com o valor devolvido pela publicacao nao
        // provaria isso — os dois vem da mesma origem.
        assertEquals(PostgresSupport.sha256Hex(coluna("content")), coluna("content_hash"))
    }

    /**
     * "O pacote nao carrega nome de aluno" — I5, varrida sobre o JSON **gravado**.
     *
     * Sobre o JSON, e nao sobre o tipo: um campo acrescentado por engano aparece no artefato antes
     * de aparecer em qualquer revisao de codigo, e e no artefato que ele seria distribuido.
     */
    @Test
    fun `o pacote gravado nao traz nome, turma nem matricula`() {
        publicacao.publish(usuario, org, definicao, title = "Prova de referencia", roster = turma)

        val conteudo = coluna("content")
        for (aluno in turma) {
            assertTrue(aluno.displayName !in conteudo, "nome `${aluno.displayName}` dentro do pacote")
            assertTrue(aluno.classGroup!! !in conteudo, "turma dentro do pacote")
            assertTrue(aluno.enrollmentId!! !in conteudo, "matricula dentro do pacote")
            assertTrue(aluno.studentToken in conteudo, "o token deveria estar no pacote")
        }

        // E o dado pessoal esta onde deve: no roster, que pode mudar e ser apagado.
        assertEquals(2, contar("select count(*) from exam_roster"))
    }

    @Test
    fun `cada aluno do roster ganha uma atribuicao, com a folha dele`() {
        publicacao.publish(usuario, org, definicao, title = "Prova de referencia", roster = turma)

        val pacote = Json.decodeFromString(ExamPackage.serializer(), coluna("content"))

        // Uma por aluno, e nem uma a mais: atribuicao sobrando seria folha impressa sem dono, e
        // faltando seria aluno sem folha no dia da prova.
        assertEquals(turma.size, pacote.assignments.size, "atribuicoes: ${pacote.assignments}")
        assertEquals(
            turma.map { it.studentToken }.sorted(),
            pacote.assignments.map { it.studentToken }.sorted(),
        )

        // E cada uma com a folha DELA. Payload repetido entre alunos e o defeito que a captura nao
        // tem como desfazer: as duas folhas seriam do mesmo aluno.
        val payloads = pacote.assignments.map { requireNotNull(it.qr).payload }
        assertEquals(payloads.size, payloads.toSet().size, "payloads repetidos: $payloads")
        for (atribuicao in pacote.assignments) {
            assertTrue(
                requireNotNull(atribuicao.qr).payload.contains(atribuicao.studentToken),
                "o payload de `${atribuicao.studentToken}` nao carrega o token dele",
            )
        }

        // A geometria NAO se repete: uma por variante, e e ela que as folhas compartilham.
        assertEquals(1, pacote.layout.size, "layouts: ${pacote.layout.keys}")
    }

    @Test
    fun `roster com matricula em organizacao codificada e recusado com frase`() {
        // ADR-0012. A recusa vem do `check` no banco, e a aplicacao a traduz: quem vai escrever a
        // tela precisa saber qual modo esta em vigor e qual campo foi recusado, e nao receber
        // "violates check constraint" para interpretar.
        val codificada = PostgresSupport.createOrganization(name = "Escola B")
        PostgresSupport.addMembership(usuario, codificada, "teacher")

        val erro = assertFailsWith<RosterIdentificationModeException> {
            publicacao.publish(
                usuario,
                codificada,
                definicao,
                title = "Prova de referencia",
                roster = turma,
            )
        }

        assertEquals("coded", erro.mode)
        assertEquals("enrollment_id", erro.field)
        assertTrue(erro.message.orEmpty().contains("coded"), "a frase precisa nomear o modo")
        assertTrue(
            erro.message.orEmpty().contains("enrollment_id"),
            "a frase precisa nomear o campo recusado",
        )
    }

    @Test
    fun `trocar o modo de identificacao nao muda o hash do pacote`() {
        // O roster vive fora do artefato imutavel (ADR-0002), e mudar a postura da organizacao e
        // mexer no roster. Se o hash se movesse aqui, a separacao nao estaria valendo.
        publicacao.publish(usuario, org, definicao, title = "Prova de referencia", roster = turma)
        val hashAntes = coluna("content_hash")

        PostgresSupport.asAdmin { conexao ->
            conexao.prepareStatement(
                "update exam_roster set enrollment_id = null where organization_id = ?",
            ).use { comando ->
                comando.setObject(1, org)
                comando.execute()
            }
            conexao.prepareStatement(
                "update organization set identification_mode = 'coded' where id = ?",
            ).use { comando ->
                comando.setObject(1, org)
                comando.execute()
            }
        }

        assertEquals(hashAntes, coluna("content_hash"), "o hash do pacote se moveu")
    }

    @Test
    fun `corrigir o roster nao muda o hash do pacote`() {
        publicacao.publish(usuario, org, definicao, title = "Prova de referencia", roster = turma)
        val hashAntes = coluna("content_hash")
        val conteudoAntes = coluna("content")

        PostgresSupport.tenancy.asUser(usuario) { ctx ->
            ctx.execute(
                "update exam_roster set display_name = ? where student_token = ?",
                "Zoraide Buarque de Almeida",
                "tok-zrd",
            )
        }

        assertEquals(
            "Zoraide Buarque de Almeida",
            comoAdmin("select display_name from exam_roster where student_token = 'tok-zrd'"),
            "a correcao do roster nao chegou a acontecer",
        )
        assertEquals(hashAntes, coluna("content_hash"), "corrigir o roster mexeu no hash")
        assertEquals(conteudoAntes, coluna("content"), "corrigir o roster mexeu no pacote")
    }

    /** D-2a.3: corrigir prova publicada e publicar prova nova, e nao reescrever a publicada. */
    @Test
    fun `republicar a mesma prova e recusado com erro identificavel`() {
        publicacao.publish(usuario, org, definicao, title = "Prova de referencia")

        val erro = assertFailsWith<ExamAlreadyPublishedException> {
            publicacao.publish(usuario, org, definicao, title = "Prova de referencia (de novo)")
        }

        assertEquals(definicao.id, erro.shortId)
        assertEquals(1, contar("select count(*) from exam"), "a segunda publicacao deixou rastro")
        assertEquals(1, contar("select count(*) from exam_package"))
        assertEquals("Prova de referencia", comoAdmin("select title from exam"))
    }

    /**
     * "Nenhum pacote parcial e gravado".
     *
     * A prova entra com um item sem alternativa correta — a incoerencia que impede correcao
     * offline, que e a proposta de valor inteira da fatia 3. O que se afirma aqui nao e so a
     * excecao: e que **nenhuma das tres tabelas** ficou com linha. A montagem acontece antes de a
     * transacao abrir, entao a garantia nao depende de o rollback ter funcionado.
     */
    @Test
    fun `prova incoerente nao grava linha nenhuma`() {
        val semGabarito = definicao.copy(
            questions = definicao.questions.mapIndexed { indice, questao ->
                if (indice == 0) questao.copy(answer = null) else questao
            },
        )

        assertFailsWith<ExamPackageException> {
            publicacao.publish(usuario, org, semGabarito, title = "Prova incoerente")
        }

        assertEquals(0, contar("select count(*) from exam"))
        assertEquals(0, contar("select count(*) from exam_package"))
        assertEquals(0, contar("select count(*) from exam_roster"))
    }

    /**
     * A publicacao nao tem caminho privilegiado.
     *
     * Ela escreve por `Tenancy.asUser`, como qualquer outra escrita, entao quem nao e membro da
     * organizacao e barrado pela RLS e nao por uma checagem escrita aqui — que seria a segunda
     * fonte de verdade de autorizacao que §3.2 chama de unico erro caro possivel neste desenho.
     */
    @Test
    fun `publicar em organizacao alheia e recusado`() {
        val outraOrg = PostgresSupport.createOrganization(name = "Escola B")

        assertFailsWith<Exception> {
            publicacao.publish(usuario, outraOrg, definicao, title = "Prova intrusa")
        }

        assertEquals(0, contar("select count(*) from exam"))
        assertEquals(0, contar("select count(*) from exam_package"))
    }

    private fun coluna(nome: String, tabela: String = "exam_package"): String =
        comoAdmin("select $nome from $tabela")

    private fun comoAdmin(sql: String): String =
        PostgresSupport.adminDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows ->
                    check(rows.next()) { "consulta sem resultado: $sql" }
                    rows.getString(1)
                }
            }
        }

    private fun contar(sql: String): Int =
        PostgresSupport.adminDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows ->
                    check(rows.next())
                    rows.getInt(1)
                }
            }
        }
}
