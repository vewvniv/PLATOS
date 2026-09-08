package com.platos.api.exam

import com.platos.api.config.DatabaseConfig
import com.platos.api.db.DataSourceFactory
import com.platos.api.db.Tenancy
import com.platos.api.identity.IdentityBootstrap
import com.platos.api.identity.OrganizationQueries
import com.platos.domain.exam.ExamDefinition
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Publica as fixtures da fatia 4a contra um banco **real**, para a conferencia em aparelho.
 *
 * ## Por que isto existe, e o que ele nao e
 *
 * A §14.1 de `docs/protocolo-medicao-impressa.md` exige duas provas publicadas na mesma
 * organizacao antes de qualquer conferencia da secao 9, e o servidor da fatia 4a **nao tem rota de
 * publicacao**: as quatro rotas sao `GET`, e `ExamPublication` nao esta ligada ao `Application`.
 * A fatia 4a e de *pull*. Sem esta peca, quem repetir o roteiro da §14 teria de reescrever
 * exatamente aquilo que a 9.7 escreveu para nao ser reescrito.
 *
 * **Isto nao e o caminho de publicacao do produto, e nao deve virar um.** Publicacao de verdade —
 * professor autenticado, prova propria, cobranca de direito — e escopo da fatia 7. Tres cercas
 * mantem a distincao, e nenhuma delas e disciplina:
 *
 * 1. mora em `src/test`, entao **nao existe** na imagem publicada no GHCR;
 * 2. so age com `-Dplatos.publicar.fixtures=true`, no padrao de `platos.golden.write`;
 * 3. publica **fixture versionada**, lida de `platos.fixtures.dir`, e nao aceita prova arbitraria.
 *
 * ## O oraculo
 *
 * O `content_hash` devolvido por `ExamPublication` e conferido contra o SHA-256 dos bytes do
 * `.package.json` versionado, calculado aqui por `MessageDigest` — que **nao compartilha codigo**
 * com o `Sha256` do dominio que produziu o valor sob julgamento. Se a publicacao divergir da
 * fixture, isto fica vermelho em vez de gravar no banco um pacote que ninguem afirmou.
 *
 * ## Uso
 *
 * ```
 * DATABASE_URL=jdbc:postgresql://<host-do-pooler>:5432/postgres \
 * DATABASE_USER=app_backend.<ref-do-projeto> \
 * DATABASE_PASSWORD=<senha> \
 * PLATOS_PUBLISH_AUTH_SUBJECT=<uuid do `sub` do Supabase> \
 * PLATOS_PUBLISH_ORG_ID=<uuid da organizacao> \
 * ./gradlew :apps:api:test --tests '*PublicarFixturesNoBancoRealTest*' \
 *   -Dplatos.publicar.fixtures=true
 * ```
 *
 * Rodar **sem** `PLATOS_PUBLISH_ORG_ID` lista as organizacoes visiveis e para, sem publicar nada:
 * e assim que se descobre o uuid a usar.
 *
 * `DATABASE_USER` e **obrigatorio aqui**, ao contrario de `AppConfig`, que cai no padrao
 * `app_backend`. O Session Pooler exige o usuario com o sufixo do projeto, e o padrao falharia na
 * autenticacao — erro que nao aponta para a causa.
 *
 * `PLATOS_PUBLISH_EMAIL` e `PLATOS_PUBLISH_DISPLAY_NAME` existem, e normalmente **nao devem ser
 * passados**: `bootstrap_identity` faz `coalesce` deles sobre o registro existente, entao passa-los
 * sobrescreve o cadastro de um usuario real por efeito colateral de uma publicacao de fixture.
 */
class PublicarFixturesNoBancoRealTest {

    private val ligado: Boolean
        get() = System.getProperty("platos.publicar.fixtures") == "true"

    @Test
    fun `publica as duas provas da fatia 4a e confere os hashes`() {
        if (!ligado) return

        val fixtures = File(System.getProperty("platos.fixtures.dir"))
        val dataSource = DataSourceFactory.create(
            DatabaseConfig(
                url = obrigatorio("DATABASE_URL"),
                user = obrigatorio("DATABASE_USER"),
                password = obrigatorio("DATABASE_PASSWORD"),
            ),
        )

        // Idempotente por construcao (D-0.4): no primeiro acesso cria a organizacao pessoal, depois
        // so resolve. E o mesmo caminho que `/me/organizations` percorre, e nao um paralelo.
        val userId = IdentityBootstrap(dataSource).bootstrap(
            authSubject = obrigatorio("PLATOS_PUBLISH_AUTH_SUBJECT"),
            email = System.getenv("PLATOS_PUBLISH_EMAIL"),
            displayName = System.getenv("PLATOS_PUBLISH_DISPLAY_NAME"),
        )

        val tenancy = Tenancy(dataSource)
        val organizationId = resolverOrganizacao(tenancy, userId)
        println("usuario=$userId organizacao=$organizationId")

        val publicacao = ExamPublication(tenancy)
        val publicados = listOf(
            "prova-referencia" to "prova-referencia.package.json",
            "prova-2" to "prova-2.package.json",
        ).map { (definicao, pacote) ->
            val exam = Json { ignoreUnknownKeys = false }.decodeFromString(
                ExamDefinition.serializer(),
                File(fixtures, "$definicao.json").readText(),
            )

            val resultado = publicacao.publish(
                userId = userId,
                organizationId = organizationId,
                definition = exam,
                title = exam.title,
            )

            val esperado = sha256Hex(File(fixtures, pacote).readBytes())
            assertEquals(
                esperado,
                resultado.contentHash,
                "o pacote gravado para '${exam.id}' diverge da fixture versionada",
            )

            println(
                "publicada: short_id=${exam.id} exam_id=${resultado.examId} " +
                    "content_hash=${resultado.contentHash}",
            )
            resultado.contentHash
        }

        // A assercao da tarefa 8.2: duas provas na MESMA organizacao, sob hashes DIFERENTES. Hashes
        // iguais fariam o cache do aparelho guardar um arquivo so, e a 8.3 mediria outra coisa.
        assertTrue(
            publicados[0] != publicados[1],
            "as duas provas foram publicadas sob o mesmo content_hash",
        )
    }

    /**
     * A organizacao e **sempre** explicita. Nao ha caso em que este harness a adivinhe.
     *
     * A versao anterior caia na unica organizacao do usuario quando `PLATOS_PUBLISH_ORG_ID` estava
     * ausente. Parece conveniente e e o defeito: um professor que so tem a organizacao pessoal
     * receberia as provas nela, o aparelho consultaria a escola, e a lista viria **vazia** — sem
     * erro em lugar nenhum, e com a causa a tres camadas de distancia do sintoma.
     *
     * Sem a variavel, isto lista as organizacoes visiveis e para. Listar e barato; publicar na
     * organizacao errada custa uma conferencia inteira em aparelho para ser descoberto.
     */
    private fun resolverOrganizacao(tenancy: Tenancy, userId: UUID): UUID {
        val escolhida = System.getenv("PLATOS_PUBLISH_ORG_ID")
        val organizacoes = tenancy.asUser(userId) { ctx ->
            OrganizationQueries().listForCurrentUser(ctx)
        }
        val visiveis = organizacoes.joinToString("\n") { "  ${it.id}  ${it.kind}  ${it.name}" }

        if (escolhida.isNullOrBlank()) {
            error("PLATOS_PUBLISH_ORG_ID ausente. Organizacoes visiveis:\n$visiveis")
        }
        check(organizacoes.any { it.id == escolhida }) {
            "PLATOS_PUBLISH_ORG_ID=$escolhida nao esta entre as organizacoes visiveis:\n$visiveis"
        }
        return UUID.fromString(escolhida)
    }

    /** Oraculo independente: `MessageDigest`, e nao o `Sha256` que produziu o valor sob julgamento. */
    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun obrigatorio(nome: String): String =
        System.getenv(nome) ?: error("variavel de ambiente obrigatoria ausente: $nome")
}
