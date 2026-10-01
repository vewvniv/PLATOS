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
 * `POST .../results/graded` — a nota do professor (`slice-5c-2-a-nota-do-professor`).
 *
 * **As contagens e os valores sao lidos no banco, nunca da resposta da rota**, como em
 * `ResultRouteTest`: uma rota que respondesse certo e gravasse errado passaria em qualquer asserção
 * sobre a resposta. O corpo vai como **JSON literal** pela mesma razao. Cada recusa le a **mensagem**,
 * porque "recusou" nao diz qual camada segurou (`rigorous.md` §3).
 *
 * O pacote com discursiva e o mesmo de `ResultRouteTest` (`q01` objetiva de 1 ponto, `d1` discursiva
 * de 3, prova de 4), repetido aqui de proposito: o literal e escrito a mao.
 */
class GradedResultRouteTest {

    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        PostgresSupport.start()
        PostgresSupport.reset()
    }

    // ------------------------------------------------------------------ a nota vira revisao nova

    @Test
    fun `a nota do professor e revisao nova da mesma folha, e a parcial continua legivel`() = comApp { client ->
        val org = prepararProva(client)

        assertEquals(HttpStatusCode.OK, client.enviarParcial(org, corpoParcial("cap-parcial-1")).status)
        val resposta = client.enviarNota(org, corpoNota("cap-nota-1"))

        assertEquals(HttpStatusCode.OK, resposta.status, resposta.bodyAsText())
        assertEquals(2, json.decodeFromString<ResultAcceptedDto>(resposta.bodyAsText()).revision)
        assertEquals(2, contar("select count(*) from grading_result"))

        assertEquals("2.75", umTexto("select points::text from grading_result where capture_id = 'cap-nota-1'"))
        assertEquals("teacher", umTexto("select origin from grading_result where capture_id = 'cap-nota-1'"))
        assertEquals("image", umTexto("select path from grading_result where capture_id = 'cap-nota-1'"))
        assertEquals("cap-parcial-1", umTexto("select completes_capture_id from grading_result where capture_id = 'cap-nota-1'"))
        assertEquals("true", umTexto("select closed::text from grading_result where capture_id = 'cap-nota-1'"))

        assertEquals("1.00", umTexto("select points::text from grading_result where capture_id = 'cap-parcial-1'"))
        assertEquals("omr", umTexto("select origin from grading_result where capture_id = 'cap-parcial-1'"))

        assertEquals(
            listOf("discursiva_corrigida:3:1.75", "marcada:1:1.00"),
            textos(
                """
                select o.answer_kind || ':' || o.worth || ':' || o.earned::text
                from answer_observation o join grading_result g on g.id = o.grading_result_id
                where g.capture_id = 'cap-nota-1' order by o.item_id
                """.trimIndent(),
            ),
        )
        assertEquals(listOf("cap-nota-1"), textos("select capture_id from grading_result_current"))
    }

    @Test
    fun `a nota e autocontida, e e aceita sem a parcial da captura`() = comApp { client ->
        val org = prepararProva(client)

        val resposta = client.enviarNota(org, corpoNota("cap-nota-1"))

        assertEquals(HttpStatusCode.OK, resposta.status, resposta.bodyAsText())
        assertEquals(1, json.decodeFromString<ResultAcceptedDto>(resposta.bodyAsText()).revision)
        assertEquals(1, contar("select count(*) from grading_result"))
    }

    @Test
    fun `reenvio da mesma nota nao cria registro novo e responde igual`() = comApp { client ->
        val org = prepararProva(client)

        val primeira = client.enviarNota(org, corpoNota("cap-nota-1"))
        val segunda = client.enviarNota(org, corpoNota("cap-nota-1"))

        assertEquals(HttpStatusCode.OK, segunda.status)
        assertEquals(primeira.bodyAsText(), segunda.bodyAsText())
        assertEquals(1, contar("select count(*) from grading_result"))
        assertEquals(2, contar("select count(*) from answer_observation"))
    }

    @Test
    fun `nova nota para a mesma folha e revisao nova, e a mais recente e a corrente`() = comApp { client ->
        val org = prepararProva(client)

        client.enviarNota(org, corpoNota("cap-nota-1", d1 = "1.75", pontos = "2.75"))
        val segunda = client.enviarNota(org, corpoNota("cap-nota-2", d1 = "2.5", pontos = "3.50"))

        assertEquals(2, json.decodeFromString<ResultAcceptedDto>(segunda.bodyAsText()).revision)
        assertEquals(listOf("cap-nota-2"), textos("select capture_id from grading_result_current"))
        assertEquals("2.75", umTexto("select points::text from grading_result where capture_id = 'cap-nota-1'"))
    }

    @Test
    fun `a parcial que chega depois da nota nao e a corrente, e a rota responde sucesso`() = comApp { client ->
        val org = prepararProva(client)

        client.enviarNota(org, corpoNota("cap-nota-1"))
        val parcial = client.enviarParcial(org, corpoParcial("cap-parcial-1"))

        assertEquals(HttpStatusCode.OK, parcial.status, parcial.bodyAsText())
        assertEquals(2, contar("select count(*) from grading_result"))
        assertEquals(listOf("cap-nota-1"), textos("select capture_id from grading_result_current"))
    }

    @Test
    fun `folha avulsa tem a nota do professor, e a corrente e a nota`() = comApp { client ->
        val org = prepararProva(client)

        client.enviarParcial(org, corpoParcial("cap-parcial-1", token = null))
        val nota = client.enviarNota(org, corpoNota("cap-nota-1", token = null))

        assertEquals(HttpStatusCode.OK, nota.status, nota.bodyAsText())
        assertEquals(listOf("cap-nota-1"), textos("select capture_id from grading_result_current"))
    }

    @Test
    fun `as bordas da faixa sao validas, zero e o valor exato do pacote`() = comApp { client ->
        val org = prepararProva(client)

        assertEquals(HttpStatusCode.OK, client.enviarNota(org, corpoNota("cap-zero", d1 = "0", pontos = "1")).status)
        assertEquals(HttpStatusCode.OK, client.enviarNota(org, corpoNota("cap-max", d1 = "3.00", pontos = "4.00")).status)

        assertEquals("1.00", umTexto("select points::text from grading_result where capture_id = 'cap-zero'"))
        assertEquals("4.00", umTexto("select points::text from grading_result where capture_id = 'cap-max'"))
    }

    // ------------------------------------------------------------------ as recusas, uma camada por vez

    @Test
    fun `cada recusa e 400, diz o que nao fecha, e nada e gravado`() = comApp { client ->
        val org = prepararProva(client)

        val casos = listOf(
            "tres casas" to (corpoNota("c1", d1 = "1.333", pontos = "2.33") to "mais de 2 casas"),
            "negativa" to (corpoNota("c2", d1 = "-1", pontos = "0") to "negativa"),
            "nao numerica" to (corpoNota("c3", d1 = "abc") to "nao e uma pontuacao"),
            "virgula decimal" to (corpoNota("c4", d1 = "1,5") to "ponto decimal"),
            "acima do valor" to (corpoNota("c5", d1 = "3.01", pontos = "4.01") to "no maximo 3"),
            "item que nao e discursiva" to (corpoNota("c6", notas = """[{"item_id":"d9","earned":"1"}]""") to "nao e discursiva"),
            "objetiva pontuada" to (corpoNota("c7", notas = """[{"item_id":"q01","earned":"1"}]""") to "nao e discursiva"),
            "discursiva sem nota" to (corpoNota("c8", notas = "[]") to "nao recebeu pontuacao"),
            "discursiva repetida" to (
                corpoNota("c9", notas = """[{"item_id":"d1","earned":"1"},{"item_id":"d1","earned":"2"}]""") to "mais de uma vez"
                ),
            "total divergente" to (corpoNota("c10", pontos = "9") to "declara total"),
            "closed divergente" to (corpoNota("c11", fechada = false) to "closed="),
            "max_score divergente" to (corpoNota("c12", maximo = 5) to "a prova vale 5"),
            "pacote que nao e o da prova" to (corpoNota("c13", hash = "b".repeat(64)) to "contra o pacote"),
            "variante que o pacote nao declara" to (corpoNota("c14", variante = "v9") to "a variante `v9`"),
            "origem de fatia posterior" to (corpoNota("c15", origem = "ai") to "origem 'ai'"),
            "caminho de fatia posterior" to (corpoNota("c16", caminho = "text") to "caminho 'text'"),
            "discursiva_corrigida contrabandeada na objetiva" to (
                corpoNota(
                    "c17",
                    observacoes = """[{"item_id":"q01","answer_kind":"discursiva_corrigida","answer_options":[],"worth":1,"earned":1}]""",
                ) to "que nao existe"
                ),
        )

        for ((nome, caso) in casos) {
            val (corpo, trecho) = caso
            val resposta = client.enviarNota(org, corpo)
            assertEquals(HttpStatusCode.BadRequest, resposta.status, "$nome: ${resposta.bodyAsText()}")
            assertTrue(trecho in resposta.bodyAsText(), "$nome: esperava '$trecho' em: ${resposta.bodyAsText()}")
            assertEquals(0, contar("select count(*) from grading_result"), "$nome gravou resultado")
            assertEquals(0, contar("select count(*) from answer_observation"), "$nome gravou evidencia")
        }
    }

    @Test
    fun `prova so objetiva nao tem o que pontuar`() = comApp { client ->
        val (userId, org) = professorComOrganizacao(client)
        val prova = PostgresSupport.createExam(org, SHORT_ID_OBJETIVA, "Prova O", userId)
        PostgresSupport.publishPackage(org, prova, CONTEUDO_SO_OBJETIVA)

        val resposta = client.enviarNota(
            org,
            corpoNota("cap-nota-1", hash = HASH_SO_OBJETIVA, notas = """[{"item_id":"d1","earned":"1"}]""", maximo = 1, pontos = "2"),
            shortId = SHORT_ID_OBJETIVA,
        )

        assertEquals(HttpStatusCode.BadRequest, resposta.status)
        assertTrue("nota completa" in resposta.bodyAsText(), resposta.bodyAsText())
        assertEquals(0, contar("select count(*) from grading_result"))
    }

    @Test
    fun `sem credencial e 401, prova de outra organizacao e prova inexistente sao 404, e nada e gravado`() = comApp { client ->
        val org = prepararProva(client)
        val alheia = organizacaoAlheiaComProva()

        val semToken = client.post("/organizations/$org/exams/$SHORT_ID/results/graded") {
            contentType(ContentType.Application.Json)
            setBody(corpoNota("cap-1"))
        }
        val deOutraOrg = client.enviarNota(alheia, corpoNota("cap-2"), shortId = SHORT_ID_ALHEIA)
        val inexistente = client.enviarNota(org, corpoNota("cap-3"), shortId = "nao-existe")

        assertEquals(HttpStatusCode.Unauthorized, semToken.status)
        assertEquals(HttpStatusCode.NotFound, deOutraOrg.status)
        assertEquals(HttpStatusCode.NotFound, inexistente.status)
        assertEquals(0, contar("select count(*) from grading_result"))
    }

    @Test
    fun `um corpo que traga imagem nao a grava em lugar nenhum`() = comApp { client ->
        val org = prepararProva(client)

        val resposta = client.enviarNota(org, corpoNota("cap-nota-1", extra = ",\"image\":\"AAAAQUFB\",\"file\":\"resposta-d1.png\""))

        assertTrue(
            resposta.status == HttpStatusCode.OK || resposta.status == HttpStatusCode.BadRequest,
            "esperava aceitar ignorando ou recusar, e veio ${resposta.status}",
        )
        // O que importa: nenhuma coluna de nenhuma das duas tabelas carrega o conteudo nem o nome do arquivo.
        assertEquals(0, contar("select count(*) from grading_result g where g::text like '%AAAAQUFB%' or g::text like '%resposta-d1%'"))
        assertEquals(0, contar("select count(*) from answer_observation o where o::text like '%AAAAQUFB%' or o::text like '%resposta-d1%'"))
    }

    // ------------------------------------------------------------------ montagem

    /** O corpo da nota, como JSON literal. `q01` objetiva certa (1) + `d1` (3) = prova de 4. */
    private fun corpoNota(
        captureId: String,
        completa: String = "cap-parcial-1",
        token: String? = "aluno-1",
        hash: String = HASH_DISCURSIVA,
        variante: String = "v1",
        origem: String = "teacher",
        caminho: String = "image",
        d1: String = "1.75",
        pontos: String = "2.75",
        maximo: Int = 4,
        fechada: Boolean = true,
        observacoes: String = """[{"item_id":"q01","answer_kind":"marcada","answer_options":["A"],"worth":1,"earned":1}]""",
        notas: String = """[{"item_id":"d1","earned":"$d1"}]""",
        extra: String = "",
    ): String {
        val tokenJson = if (token == null) "null" else "\"$token\""
        return """
            {"capture_id":"$captureId","completes_capture_id":"$completa","student_token":$tokenJson,
             "package_hash":"$hash","variant_id":"$variante","origin":"$origem","path":"$caminho",
             "points":"$pontos","max_score":$maximo,"closed":$fechada,"captured_at":"2026-09-17T12:00:00Z",
             "observations":$observacoes,"essay_grades":$notas$extra}
        """.trimIndent()
    }

    /** A parcial que a nota completa: `q01` marcada e certa, `max_score` 4 (a prova inteira). */
    private fun corpoParcial(captureId: String, token: String? = "aluno-1"): String {
        val tokenJson = if (token == null) "null" else "\"$token\""
        return """
            {"capture_id":"$captureId","student_token":$tokenJson,"package_hash":"$HASH_DISCURSIVA",
             "variant_id":"v1","points":1,"max_score":4,"closed":false,
             "captured_at":"2026-09-17T12:00:00Z",
             "observations":[{"item_id":"q01","answer_kind":"marcada","answer_options":["A"],"worth":1,"earned":1}],
             "partial":true}
        """.trimIndent()
    }

    private suspend fun HttpClient.enviarNota(
        organizationId: UUID,
        corpo: String,
        shortId: String = SHORT_ID,
    ): HttpResponse = post("/organizations/$organizationId/exams/$shortId/results/graded") {
        header(HttpHeaders.Authorization, "Bearer ${JwtTestFixture.token(SUB, EMAIL, NOME)}")
        contentType(ContentType.Application.Json)
        setBody(corpo)
    }

    private suspend fun HttpClient.enviarParcial(organizationId: UUID, corpo: String): HttpResponse =
        post("/organizations/$organizationId/exams/$SHORT_ID/results") {
            header(HttpHeaders.Authorization, "Bearer ${JwtTestFixture.token(SUB, EMAIL, NOME)}")
            contentType(ContentType.Application.Json)
            setBody(corpo)
        }

    private suspend fun prepararProva(client: HttpClient): UUID {
        val (userId, org) = professorComOrganizacao(client)
        val prova = PostgresSupport.createExam(org, SHORT_ID, "Prova com discursiva", userId)
        PostgresSupport.publishPackage(org, prova, CONTEUDO_COM_DISCURSIVA)
        return org
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
        val exame = PostgresSupport.createExam(alheia, SHORT_ID_ALHEIA, "Prova Alheia", outro)
        PostgresSupport.publishPackage(alheia, exame, CONTEUDO_COM_DISCURSIVA)
        return alheia
    }

    private fun contar(sql: String): Int = PostgresSupport.adminDataSource.connection.use { c ->
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
        const val SUB = "sub-nota"
        const val EMAIL = "nota@escola.br"
        const val NOME = "Professor da Nota"
        const val SHORT_ID = "prova-discursiva-n"
        const val SHORT_ID_OBJETIVA = "prova-objetiva-n"
        const val SHORT_ID_ALHEIA = "prova-alheia-n"

        /** Escrito a mao, como em `ResultRouteTest`: serializar com o tipo do servidor poria o mesmo codigo dos dois lados. */
        const val CONTEUDO_COM_DISCURSIVA =
            """{"meta":{"exam_id":"$SHORT_ID","layout_engine_version":1,"min_renderer_version":1,"fully_offline_gradable":false},"items":[{"id":"q01","statement":"Q1","options":["A","B"],"skills":[{"code":"EM13MAT301","coverage":"anchor"}],"kind":"objective"},{"id":"d1","statement":"D1","options":[],"skills":[{"code":"EM13MAT301","coverage":"anchor"}],"kind":"essay","rubric":{"criteria":[{"id":"c1","description":"C1","points":3,"expected_lines":2,"descriptors":[]}]}}],"variants":[{"variant_id":"v1","positions":{"1":"q01","2":"d1"}}],"assignments":[],"layout":{},"answer_key":[{"item_id":"q01","correct":"A","points":1}],"scoring":{"max_score":4}}"""

        const val CONTEUDO_SO_OBJETIVA =
            """{"meta":{"exam_id":"$SHORT_ID_OBJETIVA","layout_engine_version":1,"min_renderer_version":1,"fully_offline_gradable":true},"items":[{"id":"q01","statement":"Q1","options":["A","B"],"skills":[{"code":"EM13MAT301","coverage":"anchor"}],"kind":"objective"}],"variants":[{"variant_id":"v1","positions":{"1":"q01"}}],"assignments":[],"layout":{},"answer_key":[{"item_id":"q01","correct":"A","points":1}],"scoring":{"max_score":1}}"""

        val HASH_DISCURSIVA: String = PostgresSupport.sha256Hex(CONTEUDO_COM_DISCURSIVA)
        val HASH_SO_OBJETIVA: String = PostgresSupport.sha256Hex(CONTEUDO_SO_OBJETIVA)
    }
}
