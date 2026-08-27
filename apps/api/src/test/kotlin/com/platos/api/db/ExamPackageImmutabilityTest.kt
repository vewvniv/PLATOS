package com.platos.api.db

import com.platos.api.support.PostgresSupport
import org.jooq.exception.DataAccessException
import java.sql.SQLException
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Os cenarios "Pacote publicado nao pode ser alterado" e "Eliminacao de dado pessoal nao destroi a
 * prova" (specs/exam-package), medidos contra Postgres real.
 *
 * D-2a.3 diz que a recusa vive no armazenamento. Uma guarda dessas falha em silencio de um jeito
 * so: existindo e nunca sendo exercitada. Por isso cada teste aqui **tenta a alteracao de verdade**,
 * pelos dois caminhos que existem — o da aplicacao, que esbarra no privilegio, e o do dono da
 * tabela, que esbarra no gatilho. Afirmar so o primeiro deixaria passar uma imutabilidade que
 * qualquer migration futura desfaria sem ruido.
 */
class ExamPackageImmutabilityTest {

    private lateinit var usuario: UUID
    private lateinit var org: UUID
    private lateinit var prova: UUID
    private lateinit var pacote: UUID

    /** Chaves fora de ordem alfabetica de proposito. Ver `conteudo volta byte a byte`. */
    private val conteudo = """{"meta":{"exam_id":"prova-x"},"items":[],"answer_key":[]}"""
    private val hash = PostgresSupport.sha256Hex(conteudo)

    @BeforeTest
    fun setUp() {
        PostgresSupport.start()
        PostgresSupport.reset()

        usuario = PostgresSupport.createUser("sub-imutabilidade")
        // Modo nominal: estes testes existem para provar que nome, turma e matricula ficam
        // fora do artefato imutavel, e para isso precisam existir. ADR-0012 faz o padrao ser
        // `coded`, que recusa matricula.
        org = PostgresSupport.createOrganization(name = "Escola", identificationMode = "nominal")
        PostgresSupport.addMembership(usuario, org, "teacher")

        prova = PostgresSupport.createExam(org, shortId = "prova-x", title = "Prova X")
        pacote = PostgresSupport.publishPackage(org, prova, conteudo, hash)
    }

    @Test
    fun `a aplicacao nao consegue alterar um pacote publicado`() {
        val erro = assertFailsWith<DataAccessException> {
            PostgresSupport.tenancy.asUser(usuario) { ctx ->
                ctx.execute("update exam_package set content_hash = ?", "0".repeat(64))
            }
        }

        assertEquals("42501", sqlState(erro), "esperava recusa por privilegio insuficiente")
        assertEquals(hash, hashGravado(), "o pacote foi alterado")
    }

    @Test
    fun `a aplicacao nao consegue apagar um pacote publicado`() {
        val erro = assertFailsWith<DataAccessException> {
            PostgresSupport.tenancy.asUser(usuario) { ctx ->
                ctx.execute("delete from exam_package")
            }
        }

        assertEquals("42501", sqlState(erro), "esperava recusa por privilegio insuficiente")
        assertEquals(1, contarPacotes(), "o pacote foi removido")
    }

    /**
     * O caminho que o privilegio nao cobre.
     *
     * `REVOKE` barra `app_backend` e mais ninguem: o dono da tabela e qualquer superusuario passam
     * direto por ele. Se a imutabilidade fosse so o `REVOKE`, ela valeria enquanto ninguem
     * escrevesse uma migration, um job de manutencao ou um script de suporte — que e precisamente
     * quando um pacote ja distribuido seria alterado. Este teste roda como superusuario de
     * proposito, para afirmar que ate ele e recusado.
     */
    @Test
    fun `nem o superusuario altera ou apaga um pacote publicado`() {
        val doUpdate = assertFailsWith<SQLException> {
            PostgresSupport.adminDataSource.connection.use { connection ->
                connection.createStatement().use { it.executeUpdate("update exam_package set content = '{}'") }
            }
        }
        assertEquals("PT001", doUpdate.sqlState, "o gatilho de imutabilidade nao reagiu ao UPDATE")

        val doDelete = assertFailsWith<SQLException> {
            PostgresSupport.adminDataSource.connection.use { connection ->
                connection.createStatement().use { it.executeUpdate("delete from exam_package") }
            }
        }
        assertEquals("PT001", doDelete.sqlState, "o gatilho de imutabilidade nao reagiu ao DELETE")

        assertEquals(conteudo, conteudoGravado(), "o pacote mudou")
        assertEquals(1, contarPacotes())
    }

    /**
     * DELETE que chega por cascata continua sendo DELETE.
     *
     * Se `exam_package.exam_id` tivesse `on delete cascade`, apagar a prova apagaria o pacote sem
     * que uma unica linha de codigo mencionasse `exam_package` — a imutabilidade contornada pela
     * porta dos fundos, e sem erro nenhum.
     */
    @Test
    fun `apagar a prova e recusado enquanto houver pacote publicado`() {
        val erro = assertFailsWith<SQLException> {
            PostgresSupport.adminDataSource.connection.use { connection ->
                connection.prepareStatement("delete from exam where id = ?").use { statement ->
                    statement.setObject(1, prova)
                    statement.executeUpdate()
                }
            }
        }

        assertEquals("23503", erro.sqlState, "esperava recusa por chave estrangeira")
        assertEquals(1, contarPacotes(), "o pacote sobreviveu ao DELETE da prova?")
    }

    /**
     * "Eliminacao de dado pessoal nao destroi a prova" — a operacionalizacao de I5.
     *
     * O hash e recalculado do conteudo gravado com o `MessageDigest` da JVM, e nao lido de volta da
     * coluna: comparar a coluna consigo mesma passaria mesmo que o conteudo tivesse mudado.
     */
    @Test
    fun `apagar o roster deixa o pacote integro e com o mesmo hash`() {
        PostgresSupport.addRosterEntry(org, prova, "tok-1", "Ana Souza", "3B", "2026-0001")
        PostgresSupport.addRosterEntry(org, prova, "tok-2", "Bruno Lima", "3B", "2026-0002")

        PostgresSupport.tenancy.asUser(usuario) { ctx ->
            assertEquals(2, ctx.fetch("select id from exam_roster").size)
            ctx.execute("delete from exam_roster")
            assertEquals(0, ctx.fetch("select id from exam_roster").size)
        }

        assertEquals(1, contarPacotes(), "apagar o roster levou o pacote junto")
        assertEquals(conteudo, conteudoGravado(), "o conteudo do pacote mudou")
        assertEquals(
            PostgresSupport.sha256Hex(conteudoGravado()),
            hashGravado(),
            "o pacote deixou de conferir com o proprio hash",
        )
        assertTrue("Ana" !in conteudoGravado(), "nome de aluno dentro do artefato imutavel")
    }

    /**
     * A coluna e `text`, e nao `jsonb`, e este teste e quem segura isso.
     *
     * `jsonb` reordena chaves e descarta espacamento. O pacote voltaria semanticamente igual e
     * **byte a byte diferente**, entao o dispositivo da fatia 4 recalcularia um hash que nao bate
     * com o declarado — uma falha que aparece longe daqui, no unico lugar onde o pacote e
     * verificado. As chaves de [conteudo] estao fora de ordem alfabetica justamente para que a
     * troca de tipo derrube este teste em vez de passar despercebida.
     */
    @Test
    fun `o conteudo volta byte a byte, e o hash confere`() {
        val lido = PostgresSupport.tenancy.asUser(usuario) { ctx ->
            ctx.fetch("select content from exam_package").single().get(0, String::class.java)
        }

        assertEquals(conteudo, lido, "a serializacao canonica nao sobreviveu ao armazenamento")
        assertEquals(hash, PostgresSupport.sha256Hex(lido))
    }

    @Test
    fun `pacote com conteudo que nao e json e recusado`() {
        val outraProva = PostgresSupport.createExam(org, "prova-y")

        val erro = assertFailsWith<SQLException> {
            PostgresSupport.adminDataSource.connection.use { connection ->
                connection.prepareStatement(
                    "insert into exam_package (organization_id, exam_id, content, content_hash) " +
                        "values (?, ?, ?, ?)",
                ).use { statement ->
                    statement.setObject(1, org)
                    statement.setObject(2, outraProva)
                    statement.setString(3, "isto nao e json")
                    statement.setString(4, "0".repeat(64))
                    statement.executeUpdate()
                }
            }
        }

        assertTrue(
            erro.sqlState == "23514" || erro.sqlState == "22P02",
            "esperava recusa de conteudo nao-json, veio ${erro.sqlState}",
        )
    }

    private fun sqlState(erro: DataAccessException): String? =
        generateSequence(erro as Throwable) { it.cause }
            .filterIsInstance<SQLException>()
            .firstOrNull()
            ?.sqlState

    private fun contarPacotes(): Int = comoAdmin("select count(*) from exam_package") { it.getInt(1) }

    private fun hashGravado(): String =
        comoAdmin("select content_hash from exam_package") { it.getString(1) }

    private fun conteudoGravado(): String =
        comoAdmin("select content from exam_package") { it.getString(1) }

    private fun <T> comoAdmin(sql: String, extrair: (java.sql.ResultSet) -> T): T =
        PostgresSupport.adminDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows ->
                    check(rows.next()) { "consulta sem resultado: $sql" }
                    extrair(rows)
                }
            }
        }
}
