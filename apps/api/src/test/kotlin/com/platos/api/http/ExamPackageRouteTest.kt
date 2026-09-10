package com.platos.api.http

import com.platos.api.http.dto.ExamSummaryDto
import com.platos.api.module
import com.platos.api.support.JwtTestFixture
import com.platos.api.support.PostgresSupport
import com.platos.api.support.TestDependencies
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Os cenarios de listagem e de entrega da spec de `exam-package` (fatia 4a, ADR-0013).
 *
 * **O oraculo do conteudo e o banco, e nunca uma segunda serializacao em Kotlin.** Comparar a
 * resposta contra `pacote.toCanonicalJson()` poria o mesmo codigo dos dois lados da igualdade, e o
 * teste concordaria com qualquer coisa que aquele caminho produzisse. Aqui o conteudo e lido de
 * `exam_package.content` por SQL cru, e o hash e conferido por `MessageDigest` da JVM — que nao
 * compartilha uma linha com o `Sha256` do dominio.
 */
class ExamPackageRouteTest {

    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        PostgresSupport.start()
        PostgresSupport.reset()
    }

    // ------------------------------------------------------------------ listagem

    @Test
    fun `lista as provas publicadas da organizacao com identificador titulo e hash`() = comApp { client ->
        val (userId, organizationId) = professorComOrganizacao(client)
        val exame = PostgresSupport.createExam(organizationId, "mat-7a-2026-1", "Prova de Matematica", userId)
        val hash = PostgresSupport.sha256Hex(CONTEUDO)
        PostgresSupport.publishPackage(organizationId, exame, CONTEUDO)

        val provas = client.provasDe(organizationId)

        assertEquals(1, provas.size)
        assertEquals("mat-7a-2026-1", provas.single().shortId)
        assertEquals("Prova de Matematica", provas.single().title)
        assertEquals(hash, provas.single().contentHash)
    }

    @Test
    fun `prova sem pacote publicado nao aparece na listagem`() = comApp { client ->
        val (userId, organizationId) = professorComOrganizacao(client)
        PostgresSupport.createExam(organizationId, "sem-pacote", "Rascunho", userId)
        val comPacote = PostgresSupport.createExam(organizationId, "com-pacote", "Publicada", userId)
        PostgresSupport.publishPackage(organizationId, comPacote, CONTEUDO)

        val provas = client.provasDe(organizationId)

        assertEquals(listOf("com-pacote"), provas.map { it.shortId })
    }

    @Test
    fun `organizacao alheia responde como inexistente e nao revela prova nenhuma`() = comApp { client ->
        val (_, propria) = professorComOrganizacao(client)
        val alheia = organizacaoAlheiaComProva()

        val resposta = client.get("/organizations/$alheia/exams") {
            header(HttpHeaders.Authorization, "Bearer ${JwtTestFixture.token(SUB, EMAIL, NOME)}")
        }

        // Mesma resposta que uma organizacao inexistente: lista vazia, e nenhuma pista da prova.
        assertEquals(HttpStatusCode.OK, resposta.status)
        assertFalse(resposta.bodyAsText().contains("prova-alheia"))
        assertFalse(resposta.bodyAsText().contains("Prova Alheia"))
        assertEquals(emptyList(), client.provasDe(alheia))
        assertEquals(emptyList(), client.provasDe(propria))
    }

    @Test
    fun `listagem sem credencial e recusada sem revelar prova`() = comApp { client ->
        val (userId, organizationId) = professorComOrganizacao(client)
        val exame = PostgresSupport.createExam(organizationId, "prova-secreta", "Prova Secreta", userId)
        PostgresSupport.publishPackage(organizationId, exame, CONTEUDO)

        val resposta = client.get("/organizations/$organizationId/exams")

        assertEquals(HttpStatusCode.Unauthorized, resposta.status)
        assertFalse(resposta.bodyAsText().contains("prova-secreta"))
        assertFalse(resposta.bodyAsText().contains("Prova Secreta"))
    }

    // ------------------------------------------------------------------ entrega

    /**
     * Tarefa 1.3: byte a byte contra o banco, e o hash contra um oraculo independente.
     *
     * Tres igualdades, e nenhuma delas fecha o circulo com a outra: o corpo contra o `content`
     * gravado, `MessageDigest(corpo)` contra o `content_hash` gravado, e o cabecalho contra os dois.
     */
    @Test
    fun `o corpo entregue e byte a byte o conteudo gravado e o hash confere`() = comApp { client ->
        val (userId, organizationId) = professorComOrganizacao(client)
        val exame = PostgresSupport.createExam(organizationId, "mat-7a-2026-1", "Prova", userId)
        PostgresSupport.publishPackage(organizationId, exame, CONTEUDO)

        val resposta = client.pacoteDe(organizationId, "mat-7a-2026-1")
        assertEquals(HttpStatusCode.OK, resposta.status)

        val gravado = conteudoGravado(exame)
        val hashGravado = hashGravado(exame)

        assertContentEquals(gravado.toByteArray(Charsets.UTF_8), resposta.bodyAsBytes())
        assertEquals(hashGravado, PostgresSupport.sha256Hex(String(resposta.bodyAsBytes(), Charsets.UTF_8)))
        assertEquals(hashGravado, resposta.headers[PACKAGE_CONTENT_HASH_HEADER])
    }

    /**
     * Tarefa 1.5: a igualdade acima nao pode ser satisfeita por duas coisas erradas iguais.
     *
     * Se o corpo entregue fosse vazio, ou tivesse um byte a mais, o hash calculado sobre ele
     * deixaria de bater com o gravado. Este teste confere o **contrapositivo** da 1.3 usando o mesmo
     * oraculo, sem tocar no servidor: e a guarda contra uma assercao que passa por comparar duas
     * derivacoes do mesmo engano.
     */
    @Test
    fun `hash de corpo alterado nao confere com o gravado`() = comApp { client ->
        val (userId, organizationId) = professorComOrganizacao(client)
        val exame = PostgresSupport.createExam(organizationId, "mat-7a-2026-1", "Prova", userId)
        PostgresSupport.publishPackage(organizationId, exame, CONTEUDO)

        val corpo = String(client.pacoteDe(organizationId, "mat-7a-2026-1").bodyAsBytes(), Charsets.UTF_8)
        val hashGravado = hashGravado(exame)

        assertEquals(hashGravado, PostgresSupport.sha256Hex(corpo))
        assertFalse(hashGravado == PostgresSupport.sha256Hex(corpo + " "))
        assertFalse(hashGravado == PostgresSupport.sha256Hex(""))
        assertFalse(hashGravado == PostgresSupport.sha256Hex(corpo.dropLast(1)))
    }

    /**
     * O corpo nao negocia charset, e **e este teste que faz a decisao 2 poder falhar**.
     *
     * A tarefa 1.4 mandou trocar `respondBytes` por `respondText` e ver o vermelho. Ele nao veio: o
     * `respondText` do Ktor codifica em UTF-8 por padrao, entao com um cliente que nao pede nada os
     * dois caminhos produzem os mesmos bytes, e todo o resto desta classe passava com a mutacao
     * aplicada. A diferenca so existe quando o cliente **pede** outra codificacao — e um cliente
     * assim e comum, porque `Accept-Charset` costuma ser posto por proxy e nao por quem escreveu o
     * aplicativo.
     *
     * Com `respondText`, o Ktor honra o pedido e devolve Latin-1: os acentos viram um byte cada, o
     * corpo deixa de bater com o gravado, e o hash que o proprio servidor declarou no cabecalho
     * deixa de cobrir o que ele mandou. Com `respondBytes`, o cabecalho e ignorado.
     */
    @Test
    fun `o corpo ignora Accept-Charset e continua em UTF-8`() = comApp { client ->
        val (userId, organizationId) = professorComOrganizacao(client)
        val exame = PostgresSupport.createExam(organizationId, "mat-7a-2026-1", "Prova", userId)
        PostgresSupport.publishPackage(organizationId, exame, CONTEUDO)

        val resposta = client.get("/organizations/$organizationId/exams/mat-7a-2026-1/package") {
            header(HttpHeaders.Authorization, "Bearer ${JwtTestFixture.token(SUB, EMAIL, NOME)}")
            header(HttpHeaders.AcceptCharset, "ISO-8859-1")
        }

        val gravado = conteudoGravado(exame)
        assertContentEquals(gravado.toByteArray(Charsets.UTF_8), resposta.bodyAsBytes())
        assertEquals(
            hashGravado(exame),
            PostgresSupport.sha256Hex(String(resposta.bodyAsBytes(), Charsets.UTF_8)),
        )
        assertEquals(hashGravado(exame), resposta.headers[PACKAGE_CONTENT_HASH_HEADER])
    }

    @Test
    fun `o corpo entregue e json valido e nao vem embrulhado`() = comApp { client ->
        val (userId, organizationId) = professorComOrganizacao(client)
        val exame = PostgresSupport.createExam(organizationId, "mat-7a-2026-1", "Prova", userId)
        PostgresSupport.publishPackage(organizationId, exame, CONTEUDO)

        val corpo = String(client.pacoteDe(organizationId, "mat-7a-2026-1").bodyAsBytes(), Charsets.UTF_8)

        // Sem envelope: o primeiro caractere e o do proprio pacote, e nao o de um objeto que o
        // contenha como string escapada.
        assertTrue(corpo.startsWith("{\"meta\""), "o corpo veio embrulhado: ${corpo.take(40)}")
        assertFalse(corpo.contains("\\\""), "o corpo veio escapado dentro de outra string")
    }

    @Test
    fun `pacote de organizacao alheia responde como prova inexistente`() = comApp { client ->
        val (_, propria) = professorComOrganizacao(client)
        val alheia = organizacaoAlheiaComProva()

        val alheio = client.pacoteDe(alheia, "prova-alheia")
        val inexistente = client.pacoteDe(propria, "nunca-existiu")

        assertEquals(HttpStatusCode.NotFound, alheio.status)
        assertEquals(inexistente.status, alheio.status)
        assertEquals(inexistente.bodyAsText(), alheio.bodyAsText())
        assertNull(alheio.headers[PACKAGE_CONTENT_HASH_HEADER])
        assertFalse(alheio.bodyAsText().contains("meta"))
    }

    @Test
    fun `prova inexistente responde 404 e nao falha de servidor`() = comApp { client ->
        val (_, organizationId) = professorComOrganizacao(client)

        val resposta = client.pacoteDe(organizationId, "nao-existe")

        assertEquals(HttpStatusCode.NotFound, resposta.status)
    }

    @Test
    fun `prova sem pacote publicado responde 404`() = comApp { client ->
        val (userId, organizationId) = professorComOrganizacao(client)
        PostgresSupport.createExam(organizationId, "sem-pacote", "Rascunho", userId)

        val resposta = client.pacoteDe(organizationId, "sem-pacote")

        assertEquals(HttpStatusCode.NotFound, resposta.status)
    }

    @Test
    fun `organizacao malformada no caminho responde como inexistente`() = comApp { client ->
        professorComOrganizacao(client)

        val resposta = client.get("/organizations/nao-e-uuid/exams/mat-7a-2026-1/package") {
            header(HttpHeaders.Authorization, "Bearer ${JwtTestFixture.token(SUB, EMAIL, NOME)}")
        }

        assertEquals(HttpStatusCode.NotFound, resposta.status)
    }

    @Test
    fun `entrega sem credencial e recusada sem entregar byte nenhum`() = comApp { client ->
        val (userId, organizationId) = professorComOrganizacao(client)
        val exame = PostgresSupport.createExam(organizationId, "mat-7a-2026-1", "Prova", userId)
        PostgresSupport.publishPackage(organizationId, exame, CONTEUDO)

        val resposta = client.get("/organizations/$organizationId/exams/mat-7a-2026-1/package")

        assertEquals(HttpStatusCode.Unauthorized, resposta.status)
        assertFalse(resposta.bodyAsText().contains("meta"))
        assertNull(resposta.headers[PACKAGE_CONTENT_HASH_HEADER])
    }

    // ------------------------------------------------------------------ ajudantes

    private suspend fun HttpClient.provasDe(organizationId: UUID): List<ExamSummaryDto> {
        val resposta = get("/organizations/$organizationId/exams") {
            header(HttpHeaders.Authorization, "Bearer ${JwtTestFixture.token(SUB, EMAIL, NOME)}")
        }
        assertEquals(HttpStatusCode.OK, resposta.status)
        return json.decodeFromString(resposta.bodyAsText())
    }

    private suspend fun HttpClient.pacoteDe(organizationId: UUID, shortId: String): HttpResponse =
        get("/organizations/$organizationId/exams/$shortId/package") {
            header(HttpHeaders.Authorization, "Bearer ${JwtTestFixture.token(SUB, EMAIL, NOME)}")
        }

    /** Entra uma vez para provisionar, e devolve o par que o resto das montagens precisa. */
    private suspend fun professorComOrganizacao(client: HttpClient): Pair<UUID, UUID> {
        client.get("/me/organizations") {
            header(HttpHeaders.Authorization, "Bearer ${JwtTestFixture.token(SUB, EMAIL, NOME)}")
        }
        return idDoUsuario(SUB) to idDaOrganizacaoPessoal(SUB)
    }

    private fun organizacaoAlheiaComProva(): UUID {
        val outro = PostgresSupport.createUser("sub-outro", "outro@escola.br")
        val alheia = PostgresSupport.createOrganization(kind = "school", name = "Escola Alheia")
        PostgresSupport.addMembership(outro, alheia, "teacher")
        val exame = PostgresSupport.createExam(alheia, "prova-alheia", "Prova Alheia", outro)
        PostgresSupport.publishPackage(alheia, exame, CONTEUDO)
        return alheia
    }

    private fun conteudoGravado(examId: UUID): String =
        umaColuna("select content from exam_package where exam_id = ?", examId)

    private fun hashGravado(examId: UUID): String =
        umaColuna("select content_hash from exam_package where exam_id = ?", examId)

    private fun umaColuna(sql: String, argumento: Any): String =
        PostgresSupport.adminDataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, argumento)
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getString(1)
                }
            }
        }

    private fun idDoUsuario(authSubject: String): UUID =
        PostgresSupport.adminDataSource.connection.use { connection ->
            connection.prepareStatement("select id from app_user where auth_subject = ?").use { statement ->
                statement.setString(1, authSubject)
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getObject(1, UUID::class.java)
                }
            }
        }

    private fun idDaOrganizacaoPessoal(authSubject: String): UUID =
        PostgresSupport.adminDataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                select o.id
                from organization o
                join membership m on m.organization_id = o.id
                join app_user u on u.id = m.user_id
                where u.auth_subject = ? and o.kind = 'personal'
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, authSubject)
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getObject(1, UUID::class.java)
                }
            }
        }

    private fun comApp(block: suspend (HttpClient) -> Unit) = testApplication {
        application { module(TestDependencies.create(), JwtTestFixture.jwkProvider) }
        block(createClient { })
    }

    private companion object {
        const val SUB = "sub-provas"
        const val EMAIL = "provas@escola.br"
        const val NOME = "Professor das Provas"

        /**
         * Conteudo com acento **de proposito**, contra o estilo do resto desta base.
         *
         * E o que separa UTF-8 de qualquer outra codificacao no caminho de saida: em ASCII puro,
         * UTF-8, Latin-1 e ASCII produzem os mesmos bytes, e o teste passaria com o servidor
         * codificando errado. Sao os dois caracteres acentuados abaixo que fazem 1.3 poder falhar.
         */
        const val CONTEUDO =
            """{"meta":{"exam_id":"mat-7a-2026-1"},"titulo":"Frações e proporção","nota":"até 10"}"""
    }
}
