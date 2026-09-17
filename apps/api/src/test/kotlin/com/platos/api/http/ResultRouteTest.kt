package com.platos.api.http

import com.platos.api.http.dto.ResultAcceptedDto
import com.platos.api.module
import com.platos.api.support.JwtTestFixture
import com.platos.api.support.PostgresSupport
import com.platos.api.support.TestDependencies
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `POST .../results` — a primeira rota de escrita da API.
 *
 * **As contagens sao feitas no banco, e nunca no retorno da rota.** Uma rota idempotente que
 * respondesse certo e gravasse duas vezes passaria em qualquer asserção sobre o corpo da resposta:
 * e exatamente o defeito que este arquivo existe para pegar, e ele so aparece contando linha.
 *
 * O corpo vai como **JSON literal**, e nao serializado a partir do DTO do servidor. Serializar com o
 * mesmo `@Serializable` que a rota desserializa poria o mesmo codigo dos dois lados: renomear um
 * campo continuaria verde, e o aparelho — que monta o JSON na mao, do outro lado — quebraria. E a
 * mesma razao pela qual `ObtencaoDeRosterTest` prende o literal do roster.
 */
class ResultRouteTest {

    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        PostgresSupport.start()
        PostgresSupport.reset()
    }

    // ------------------------------------------------------------------ 4.1

    @Test
    fun `um resultado enviado fica legivel no banco, com a evidencia por questao`() = comApp { client ->
        val (userId, org) = professorComOrganizacao(client)
        val prova = PostgresSupport.createExam(org, SHORT_ID, "Prova R", userId)
        PostgresSupport.publishPackage(org, prova, CONTEUDO)

        val resposta = client.enviar(org, corpo(captureId = "cap-1", pontos = 1))

        assertEquals(HttpStatusCode.OK, resposta.status, resposta.bodyAsText())
        assertEquals(1, json.decodeFromString<ResultAcceptedDto>(resposta.bodyAsText()).revision)

        assertEquals(1, contar("select count(*) from grading_result"))
        assertEquals(2, contar("select count(*) from answer_observation"))
        assertEquals(1, umInt("select points from grading_result where capture_id = 'cap-1'"))
        assertEquals(2, umInt("select max_score from grading_result where capture_id = 'cap-1'"))
        assertEquals(
            "aluno-1",
            umTexto("select student_token from grading_result where capture_id = 'cap-1'"),
        )
        assertEquals(
            "omr",
            umTexto("select origin from grading_result where capture_id = 'cap-1'"),
        )
        assertEquals(
            listOf("em_branco", "marcada"),
            textos("select answer_kind from answer_observation order by answer_kind"),
        )
    }

    @Test
    fun `evidencia que nao soma a nota e recusada, e nada parcial fica gravado`() = comApp { client ->
        val (userId, org) = professorComOrganizacao(client)
        val prova = PostgresSupport.createExam(org, SHORT_ID, "Prova R", userId)
        PostgresSupport.publishPackage(org, prova, CONTEUDO)

        // A nota diz 2; a evidencia soma 1. E a mesma guarda que rodou no aparelho — nenhuma
        // validacao propria da API foi escrita para isto.
        val resposta = client.enviar(org, corpo(captureId = "cap-incoerente", pontos = 2))

        assertEquals(HttpStatusCode.BadRequest, resposta.status)
        assertTrue(
            resposta.bodyAsText().contains("a evidencia soma"),
            "a recusa precisa dizer o que nao fecha: ${resposta.bodyAsText()}",
        )
        assertEquals(0, contar("select count(*) from grading_result"))
        assertEquals(0, contar("select count(*) from answer_observation"))
    }

    @Test
    fun `nota declarada fechada com pendencia na evidencia e recusada`() = comApp { client ->
        val (userId, org) = professorComOrganizacao(client)
        val prova = PostgresSupport.createExam(org, SHORT_ID, "Prova R", userId)
        PostgresSupport.publishPackage(org, prova, CONTEUDO)

        val corpo = """
            {"capture_id":"cap-mentira","student_token":"aluno-1","package_hash":"$HASH",
             "variant_id":"v1","points":0,"max_score":2,"closed":true,
             "captured_at":"2026-09-17T12:00:00Z",
             "observations":[
               {"item_id":"q01","answer_kind":"indecisa","answer_options":["A","B"],"worth":1,"earned":0},
               {"item_id":"q02","answer_kind":"em_branco","answer_options":[],"worth":1,"earned":0}]}
        """.trimIndent()

        val resposta = client.enviar(org, corpo)

        assertEquals(HttpStatusCode.BadRequest, resposta.status)
        assertTrue(
            resposta.bodyAsText().contains("closed=true"),
            "a recusa precisa nomear a contradicao: ${resposta.bodyAsText()}",
        )
        assertEquals(0, contar("select count(*) from grading_result"))
    }

    // ------------------------------------------------------------------ 4.2

    @Test
    fun `envio sem credencial e recusado, e nada e gravado`() = comApp { client ->
        val (userId, org) = professorComOrganizacao(client)
        val prova = PostgresSupport.createExam(org, SHORT_ID, "Prova R", userId)
        PostgresSupport.publishPackage(org, prova, CONTEUDO)

        val resposta = client.post("/organizations/$org/exams/$SHORT_ID/results") {
            contentType(ContentType.Application.Json)
            setBody(corpo(captureId = "cap-sem-token", pontos = 1))
        }

        assertEquals(HttpStatusCode.Unauthorized, resposta.status)
        assertEquals(0, contar("select count(*) from grading_result"))
    }

    /**
     * Organizacao alheia da 404, e nao 403.
     *
     * A razao e a mesma das rotas de leitura: 403 confirmaria que a prova existe. E o aparelho nao
     * precisa da distincao para agir certo — as duas respostas dizem "nao gravei", e o pendente fica
     * onde esta.
     */
    @Test
    fun `resultado de prova de organizacao alheia e recusado sem revelar que ela existe`() = comApp { client ->
        professorComOrganizacao(client)
        val alheia = organizacaoAlheiaComProva()

        val resposta = client.enviar(alheia, corpo(captureId = "cap-alheio", pontos = 1), shortId = "prova-alheia")

        assertEquals(HttpStatusCode.NotFound, resposta.status)
        assertEquals(0, contar("select count(*) from grading_result"))
    }

    @Test
    fun `prova que nao existe e prova sem pacote dao a mesma resposta`() = comApp { client ->
        val (userId, org) = professorComOrganizacao(client)
        PostgresSupport.createExam(org, "sem-pacote", "Rascunho", userId)

        val inexistente = client.enviar(org, corpo(captureId = "cap-x", pontos = 1), shortId = "nao-existe")
        val semPacote = client.enviar(org, corpo(captureId = "cap-y", pontos = 1), shortId = "sem-pacote")

        assertEquals(HttpStatusCode.NotFound, inexistente.status)
        assertEquals(HttpStatusCode.NotFound, semPacote.status)
        assertEquals(inexistente.bodyAsText(), semPacote.bodyAsText())
        assertEquals(0, contar("select count(*) from grading_result"))
    }

    // ------------------------------------------------------------------ 4.3

    @Test
    fun `reenvio da mesma captura nao cria registro novo e responde igual`() = comApp { client ->
        val (userId, org) = professorComOrganizacao(client)
        val prova = PostgresSupport.createExam(org, SHORT_ID, "Prova R", userId)
        PostgresSupport.publishPackage(org, prova, CONTEUDO)

        val primeira = client.enviar(org, corpo(captureId = "cap-repetida", pontos = 1))
        val segunda = client.enviar(org, corpo(captureId = "cap-repetida", pontos = 1))

        assertEquals(HttpStatusCode.OK, primeira.status)
        assertEquals(HttpStatusCode.OK, segunda.status, segunda.bodyAsText())
        assertEquals(
            primeira.bodyAsText(),
            segunda.bodyAsText(),
            "o reenvio precisa responder o que a primeira gravacao respondeu",
        )
        // A contagem e no banco: uma rota que respondesse certo e gravasse duas vezes passaria em
        // qualquer assercao sobre o corpo.
        assertEquals(1, contar("select count(*) from grading_result"))
        assertEquals(2, contar("select count(*) from answer_observation"))
    }

    // ------------------------------------------------------------------ 4.4

    @Test
    fun `recaptura grava revisao nova, a corrente muda e a anterior continua legivel`() = comApp { client ->
        val (userId, org) = professorComOrganizacao(client)
        val prova = PostgresSupport.createExam(org, SHORT_ID, "Prova R", userId)
        PostgresSupport.publishPackage(org, prova, CONTEUDO)

        val primeira = client.enviar(org, corpo(captureId = "cap-v1", pontos = 1))
        val segunda = client.enviar(org, corpo(captureId = "cap-v2", pontos = 2, segundaCerta = true))

        assertEquals(1, json.decodeFromString<ResultAcceptedDto>(primeira.bodyAsText()).revision)
        assertEquals(2, json.decodeFromString<ResultAcceptedDto>(segunda.bodyAsText()).revision)

        assertEquals(2, contar("select count(*) from grading_result"))
        assertEquals(1, umInt("select points from grading_result where capture_id = 'cap-v1'"))
        assertEquals(2, umInt("select points from grading_result where capture_id = 'cap-v2'"))
        assertEquals(
            2,
            umInt(
                "select points from grading_result where student_token = 'aluno-1' " +
                    "order by revision desc limit 1",
            ),
            "a revisao corrente e a de maior numero",
        )
    }

    @Test
    fun `duas folhas avulsas da mesma prova nao colidem`() = comApp { client ->
        val (userId, org) = professorComOrganizacao(client)
        val prova = PostgresSupport.createExam(org, SHORT_ID, "Prova R", userId)
        PostgresSupport.publishPackage(org, prova, CONTEUDO)

        val a = client.enviar(org, corpo(captureId = "cap-avulsa-1", pontos = 1, token = null))
        val b = client.enviar(org, corpo(captureId = "cap-avulsa-2", pontos = 1, token = null))

        assertEquals(HttpStatusCode.OK, a.status, a.bodyAsText())
        assertEquals(HttpStatusCode.OK, b.status, b.bodyAsText())
        assertEquals(2, contar("select count(*) from grading_result"))
    }

    // ------------------------------------------------------------------ montagem

    /**
     * O corpo, como JSON literal.
     *
     * Duas questoes de um ponto cada: `q01` marcada e `q02` em branco. Com [segundaCerta], `q02`
     * tambem vira marcada e a nota sobe para 2 — e o que faz a recaptura ser uma correcao
     * **diferente**, e nao a mesma nota enviada de novo.
     */
    private fun corpo(
        captureId: String,
        pontos: Int,
        token: String? = "aluno-1",
        segundaCerta: Boolean = false,
    ): String {
        val segunda = if (segundaCerta) {
            """{"item_id":"q02","answer_kind":"marcada","answer_options":["B"],"worth":1,"earned":1}"""
        } else {
            """{"item_id":"q02","answer_kind":"em_branco","answer_options":[],"worth":1,"earned":0}"""
        }
        val tokenJson = if (token == null) "null" else "\"$token\""
        return """
            {"capture_id":"$captureId","student_token":$tokenJson,"package_hash":"$HASH",
             "variant_id":"v1","points":$pontos,"max_score":2,"closed":true,
             "captured_at":"2026-09-17T12:00:00Z",
             "observations":[
               {"item_id":"q01","answer_kind":"marcada","answer_options":["A"],"worth":1,"earned":1},
               $segunda]}
        """.trimIndent()
    }

    private suspend fun HttpClient.enviar(
        organizationId: UUID,
        corpo: String,
        shortId: String = SHORT_ID,
    ): HttpResponse = post("/organizations/$organizationId/exams/$shortId/results") {
        header(HttpHeaders.Authorization, "Bearer ${JwtTestFixture.token(SUB, EMAIL, NOME)}")
        contentType(ContentType.Application.Json)
        setBody(corpo)
    }

    private suspend fun professorComOrganizacao(client: HttpClient): Pair<UUID, UUID> {
        client.get("/me/organizations") {
            header(HttpHeaders.Authorization, "Bearer ${JwtTestFixture.token(SUB, EMAIL, NOME)}")
        }
        return umUuid("select id from app_user where auth_subject = '$SUB'") to
            umUuid(
                "select o.id from organization o join membership m on m.organization_id = o.id " +
                    "join app_user u on u.id = m.user_id where u.auth_subject = '$SUB' " +
                    "and o.kind = 'personal'",
            )
    }

    private fun organizacaoAlheiaComProva(): UUID {
        val outro = PostgresSupport.createUser("sub-outro", "outro@escola.br")
        val alheia = PostgresSupport.createOrganization(kind = "school", name = "Escola Alheia")
        PostgresSupport.addMembership(outro, alheia, "teacher")
        val exame = PostgresSupport.createExam(alheia, "prova-alheia", "Prova Alheia", outro)
        PostgresSupport.publishPackage(alheia, exame, CONTEUDO)
        return alheia
    }

    private fun contar(sql: String): Int = umInt(sql)

    private fun umInt(sql: String): Int = PostgresSupport.adminDataSource.connection.use { c ->
        c.createStatement().use { s -> s.executeQuery(sql).use { it.next(); it.getInt(1) } }
    }

    private fun umTexto(sql: String): String? = PostgresSupport.adminDataSource.connection.use { c ->
        c.createStatement().use { s -> s.executeQuery(sql).use { it.next(); it.getString(1) } }
    }

    private fun textos(sql: String): List<String> = PostgresSupport.adminDataSource.connection.use { c ->
        c.createStatement().use { s ->
            s.executeQuery(sql).use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
        }
    }

    private fun umUuid(sql: String): UUID = PostgresSupport.adminDataSource.connection.use { c ->
        c.createStatement().use { s ->
            s.executeQuery(sql).use { it.next(); it.getObject(1, UUID::class.java) }
        }
    }

    private fun comApp(block: suspend (HttpClient) -> Unit) = testApplication {
        application { module(TestDependencies.create(), JwtTestFixture.jwkProvider) }
        block(createClient { })
    }

    private companion object {
        const val SUB = "sub-resultados"
        const val EMAIL = "resultados@escola.br"
        const val NOME = "Professor dos Resultados"
        const val SHORT_ID = "prova-r"
        const val CONTEUDO = """{"meta":{"exam_id":"prova-r"},"items":[],"answer_key":[]}"""

        /** O mesmo hash que o pacote publicado tem, conferido por `MessageDigest` da JVM. */
        val HASH: String = PostgresSupport.sha256Hex(CONTEUDO)
    }
}
