package com.platos.api.http

import com.platos.api.ApiDependencies
import com.platos.api.auth.SUPABASE_AUTH
import com.platos.api.auth.toAuthenticatedSubject
import com.platos.api.exam.Proveniencia
import com.platos.api.exam.conferirProveniencia
import com.platos.api.http.dto.ResultAcceptedDto
import com.platos.api.http.dto.ResultSubmissionDto
import com.platos.api.http.dto.paraNota
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.request.receive
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.UUID

/**
 * Cabecalho que declara qual build esta servindo.
 *
 * Constante, e nao literal repetido, pela mesma razao de [PACKAGE_CONTENT_HASH_HEADER]: quem le a
 * resposta e quem a escreve precisam concordar sobre o nome, e um erro de digitacao num dos dois
 * lados nao quebraria teste nenhum — ele so faria a conferencia de producao nunca achar o cabecalho.
 */
const val BUILD_HEADER = "X-Platos-Build"

/**
 * A verificacao de saude: **o corpo continua sendo exatamente `ok`**, e o build vai no cabecalho.
 *
 * A forma foi escolhida com os consumidores do corpo na mao: `HealthTest` afirma `ok` por igualdade
 * exata, e `docs/deploy-api.md` manda conferir isso. Cabecalho deixa a mudanca **aditiva** — quem le
 * o corpo nao percebe diferenca, e quem quer saber o build le o cabecalho com
 * `curl -s -D - -o /dev/null`, que e o comando que `docs/deploy-api.md` traz.
 *
 * **Nao e `curl -sI`, e esta linha ja mandou isso.** `-I` manda `HEAD`, a rota so responde
 * `GET`, e a conferencia em producao mediu **405 Method Not Allowed** com o cabecalho
 * invisivel. A instrucao errada fica dita em vez de apagada em silencio (P7).
 *
 * **O cabecalho e emitido sempre, inclusive quando o build e desconhecido.** Omiti-lo naquele caso
 * faria "nao sei" ficar indistinguivel de "um intermediario removeu o cabecalho no caminho", e quem
 * consulta esta rota esta justamente tentando descobrir o que esta no ar.
 *
 * [build] entra por parametro e **sem valor padrao**: a fiacao e obrigacao de quem monta o
 * `routing`, e um padrao faria um esquecimento em `Application` responder "desconhecido" em producao
 * em silencio. Nao ha `ApiDependencies` aqui porque esta rota nao precisa de nenhuma — e e isso que
 * a mantem testavel sem montar banco.
 *
 * Nao afirma alcance de banco de proposito: uma indisponibilidade de banco seria lida como servico
 * fora do ar, e esse elo se observa por outra sonda.
 */
fun Route.healthRoutes(build: String) {
    get("/health") {
        call.response.header(BUILD_HEADER, build)
        call.respondText("ok")
    }
}

fun Route.identityRoutes(deps: ApiDependencies) {
    authenticate(SUPABASE_AUTH) {
        get("/me/organizations") {
            val subject = call.principal<JWTPrincipal>()!!.toAuthenticatedSubject()

            // D-0.4: idempotente. No primeiro acesso cria a organizacao pessoal; depois so resolve.
            val userId = deps.identityBootstrap.bootstrap(
                authSubject = subject.authSubject,
                email = subject.email,
                displayName = subject.displayName,
            )

            val organizations = deps.tenancy.asUser(userId) { ctx ->
                deps.organizationQueries.listForCurrentUser(ctx)
            }

            call.respond(organizations)
        }
    }
}

/** Cabecalho que declara o `content_hash` fora do corpo (ADR-0013, decisao 2). */
const val PACKAGE_CONTENT_HASH_HEADER = "X-Package-Content-Hash"

/**
 * As rotas de prova publicada: escolher qual, puxar o pacote dela, e puxar o roster dela.
 *
 * **A organizacao vem no caminho, e nao do vinculo do chamador.** `short_id` e unique global, entao
 * `/exams/{shortId}/package` funcionaria — mas a organizacao ativa e uma escolha *do aparelho*
 * (fatia 4a-zero), e deixa-la implicita faria o servidor decidir por conta propria qual organizacao
 * o pedido significa quando o usuario tem duas. Com ela no caminho, o cache do aparelho e o pedido
 * usam o **mesmo** identificador.
 *
 * **Ausencia e ausencia, e nunca 403.** Organizacao a que o chamador nao pertence, prova que nao
 * existe e prova sem pacote sao a mesma resposta: 403 confirmaria que a prova existe, que e
 * exatamente o que a spec proibe revelar.
 */
fun Route.examRoutes(deps: ApiDependencies) {
    authenticate(SUPABASE_AUTH) {
        get("/organizations/{organizationId}/exams") {
            val organizationId = call.parameters["organizationId"]?.let(::uuidOrNull)
                ?: return@get call.naoEncontrado()

            val userId = call.resolverUsuario(deps)
            val provas = deps.tenancy.asUser(userId) { ctx ->
                deps.examQueries.listPublished(ctx, organizationId)
            }

            call.respond(provas)
        }

        get("/organizations/{organizationId}/exams/{shortId}/package") {
            val organizationId = call.parameters["organizationId"]?.let(::uuidOrNull)
                ?: return@get call.naoEncontrado()
            val shortId = call.parameters["shortId"] ?: return@get call.naoEncontrado()

            val userId = call.resolverUsuario(deps)
            val pacote = deps.tenancy.asUser(userId) { ctx ->
                deps.examQueries.findPackage(ctx, organizationId, shortId)
            } ?: return@get call.naoEncontrado()

            call.response.header(PACKAGE_CONTENT_HASH_HEADER, pacote.contentHash)

            // `respondBytes`, e nao `respondText`: este ultimo negocia charset, e o `content_hash`
            // foi calculado sobre UTF-8 sem BOM. Qualquer coisa que reabra essa decisao no caminho
            // de saida quebra a conferencia de integridade do aparelho — sem sintoma na tela, com o
            // pacote continuando integro e o hash deixando de bater. E tambem o que passa ao largo
            // do `ContentNegotiation`, que reserializaria o conteudo se ele fosse um objeto.
            call.respondBytes(
                bytes = pacote.content.encodeToByteArray(),
                contentType = ContentType.Application.Json,
            )
        }

        /**
         * O roster da prova: para cada aluno atribuido, o token e o nome de apresentacao.
         *
         * **JSON negociado, e nao `respondBytes`.** A razao que obriga bytes crus na rota do pacote
         * — o `content_hash` foi calculado sobre UTF-8 sem BOM — nao existe aqui: o roster nao e
         * hasheado (ADR-0002 recusou um segundo hash), entao nao ha conferencia de bytes a
         * proteger. Usar `respondBytes` por simetria custaria serializacao manual sem nada a ganhar.
         *
         * **A existencia da prova e decidida pelo `findPackage`, e nao por uma consulta propria.**
         * Isto le o conteudo do pacote so para descartar, o que e desperdicio medivel — e o preco
         * de uma garantia que uma consulta de existencia separada nao daria: "publicada" significa
         * **exatamente** a mesma coisa nas duas rotas. Com dois oraculos, o aparelho poderia receber
         * um roster para uma prova cujo pacote a outra rota diz nao existir, e a divergencia
         * apareceria como pacote faltando no meio da aplicacao da prova. Se a leitura a mais pesar,
         * o veiculo e uma mudanca com o numero na mao, e nao simetria invertida agora.
         *
         * **Sem o pacote, 404; com o pacote e sem roster, 200 com lista vazia.** As duas respostas
         * dizem coisas diferentes sobre o mundo, e a folha avulsa do aluno fora da lista depende
         * dessa diferenca: "esta prova nao tem roster" e afirmacao, "esta prova nao existe" e
         * ausencia. Organizacao alheia cai no primeiro caso, indistinguivel de inexistente.
         */
        get("/organizations/{organizationId}/exams/{shortId}/roster") {
            val organizationId = call.parameters["organizationId"]?.let(::uuidOrNull)
                ?: return@get call.naoEncontrado()
            val shortId = call.parameters["shortId"] ?: return@get call.naoEncontrado()

            val userId = call.resolverUsuario(deps)
            val roster = deps.tenancy.asUser(userId) { ctx ->
                if (deps.examQueries.findPackage(ctx, organizationId, shortId) == null) {
                    null
                } else {
                    deps.examQueries.findRoster(ctx, organizationId, shortId)
                }
            } ?: return@get call.naoEncontrado()

            call.respond(roster)
        }

        /**
         * O push do resultado apurado no aparelho (§10, push append-only).
         *
         * **A primeira rota de escrita da API.** Ate aqui o aparelho so puxava; este e o caminho de
         * volta, e o unico. Ele e append-only do lado do banco tambem, e nao so por convencao da
         * aplicacao: `grading_result` e `answer_observation` tem gatilho que recusa UPDATE e DELETE
         * ate para o dono da tabela.
         *
         * **Ausencia continua sendo ausencia.** Prova inexistente, prova sem pacote e prova de
         * organizacao a que o chamador nao pertence dao 404, exatamente como nas rotas de leitura —
         * inclusive quando o vinculo do chamador com a organizacao foi revogado. 403 confirmaria
         * que a prova existe, e o aparelho revogado nao precisa saber disso para agir certo: ele
         * mantem o pendente e espera um membro da organizacao.
         *
         * **A existencia da prova e decidida pelo mesmo oraculo das outras rotas** — o pacote
         * publicado —, pela razao registrada na rota do roster: com dois oraculos, "publicada"
         * passaria a significar coisas diferentes em rotas diferentes.
         *
         * **Corpo incoerente e 400, e a mensagem diz o que nao fecha.** A conferencia nao e escrita
         * aqui: `paraNota` reconstroi o `ObjectiveScore` do dominio, e sao as guardas dele — as
         * mesmas que rodaram no aparelho — que recusam nota fora da escala, evidencia que nao soma a
         * nota, item repetido e desencontro entre evidencia e pendencias. Uma segunda implementacao
         * da mesma regra divergiria da primeira (regra 7).
         *
         * **A proveniencia declarada e conferida contra o pacote publicado, e tambem da 400.** E a
         * segunda faixa de 400 desta rota, e ela cobre o que `paraNota` nao alcanca: `package_hash`
         * e `variant_id` eram gravados exatamente como o aparelho os enviou, sem oraculo nenhum
         * (achado 2.2). `ObjectiveScore` nao podia conferi-los — ele roda offline no aparelho, onde
         * o pacote publicado do servidor nao existe —, entao isto nao e a mesma regra duas vezes.
         *
         * **A conferencia acontece dentro da transacao e antes de qualquer `insert`.** Nao e
         * preciosismo: `grading_result` e append-only por gatilho, e um `package_hash` errado
         * gravado nao tem conserto. Gravar e desfazer por rollback seria correto por transacao e
         * errado por desenho.
         */
        post("/organizations/{organizationId}/exams/{shortId}/results") {
            val organizationId = call.parameters["organizationId"]?.let(::uuidOrNull)
                ?: return@post call.naoEncontrado()
            val shortId = call.parameters["shortId"] ?: return@post call.naoEncontrado()

            val submission = call.receive<ResultSubmissionDto>()
            val nota = try {
                submission.paraNota()
            } catch (erro: IllegalArgumentException) {
                return@post call.respondText(
                    erro.message ?: "resultado incoerente",
                    status = HttpStatusCode.BadRequest,
                )
            }

            val userId = call.resolverUsuario(deps)
            val desfecho = deps.tenancy.asUser(userId) { ctx ->
                val publicada = deps.resultQueries.findPublishedExamId(ctx, organizationId, shortId)
                if (publicada == null) {
                    null
                } else {
                    when (val proveniencia = conferirProveniencia(publicada.pacote, nota)) {
                        is Proveniencia.NaoConfere -> Desfecho.Recusado(proveniencia.motivo)
                        Proveniencia.Confere -> Desfecho.Gravado(
                            deps.resultQueries.record(
                                ctx,
                                organizationId,
                                publicada.examId,
                                submission,
                                nota,
                            ),
                        )
                    }
                }
            } ?: return@post call.naoEncontrado()

            when (desfecho) {
                is Desfecho.Recusado -> call.respondText(
                    desfecho.motivo,
                    status = HttpStatusCode.BadRequest,
                )
                is Desfecho.Gravado -> call.respond(ResultAcceptedDto(revision = desfecho.revision))
            }
        }
    }
}

/**
 * O `app_user` do token, provisionando a organizacao pessoal se for o primeiro acesso (D-0.4).
 *
 * E a mesma chamada de `/me/organizations`, e ela e idempotente. Sem ela, um token valido cuja
 * primeira requisicao fosse a de provas nao teria linha em `app_user`, e `asUser` poria na sessao um
 * identificador que nao existe — a RLS devolveria vazio, e a resposta seria "nenhuma prova" em vez
 * de "voce ainda nao tem organizacao".
 */
private suspend fun io.ktor.server.application.ApplicationCall.resolverUsuario(
    deps: ApiDependencies,
): UUID {
    val subject = principal<JWTPrincipal>()!!.toAuthenticatedSubject()
    return deps.identityBootstrap.bootstrap(
        authSubject = subject.authSubject,
        email = subject.email,
        displayName = subject.displayName,
    )
}

private suspend fun io.ktor.server.application.ApplicationCall.naoEncontrado() =
    respondText("nao ha prova publicada com esse identificador", status = HttpStatusCode.NotFound)

/**
 * Identificador malformado e ausencia, e nao pedido invalido.
 *
 * 400 diria "voce escreveu errado" para quem pediu uma organizacao que nao existe, e a diferenca
 * entre as duas respostas e informacao sobre o que existe do outro lado.
 */
/**
 * Os dois desfechos que existem **depois** de a prova ter sido encontrada.
 *
 * A ausencia nao esta aqui, e a omissao e o ponto: ela continua sendo o `null` que a transacao
 * devolve, traduzido em 404 por um `?:` que nao mudou. Tres desfechos num tipo so diluiriam a
 * distincao que esta rota existe para manter — **ausencia e incoerencia sao coisas diferentes**, e e
 * o classificador do aparelho que consome a diferenca: 4xx e recusa definitiva, e o pendente para de
 * ser reapresentado sozinho; 5xx seria transitorio, e o aparelho repetiria para sempre um envio que
 * nunca sera aceito.
 */
private sealed interface Desfecho {
    data class Gravado(val revision: Int) : Desfecho
    data class Recusado(val motivo: String) : Desfecho
}

private fun uuidOrNull(texto: String): UUID? = try {
    UUID.fromString(texto)
} catch (_: IllegalArgumentException) {
    null
}
