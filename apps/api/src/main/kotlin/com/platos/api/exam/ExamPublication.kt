package com.platos.api.exam

import com.platos.api.db.Tenancy
import com.platos.api.db.generated.tables.references.EXAM
import com.platos.api.db.generated.tables.references.EXAM_PACKAGE
import com.platos.api.db.generated.tables.references.EXAM_ROSTER
import com.platos.api.db.generated.tables.references.ORGANIZATION
import com.platos.domain.exam.DEFAULT_VARIANT
import com.platos.domain.exam.ExamDefinition
import com.platos.domain.exam.PackageAssignment
import com.platos.domain.exam.buildPackage
import org.jooq.DSLContext
import org.jooq.exception.IntegrityConstraintViolationException
import java.util.UUID

/**
 * Um aluno do roster: exatamente o que **nao** entra no pacote (ADR-0002, I5).
 *
 * O token e a unica coisa que atravessa a fronteira: ele vai para `assignments[]`, dentro do
 * artefato imutavel, e o resto fica aqui, numa tabela que pode mudar e ser apagada.
 */
data class RosterEntry(
    val studentToken: String,
    val displayName: String,
    val classGroup: String? = null,
    val enrollmentId: String? = null,
)

/**
 * O roster trouxe dado que o modo de identificacao da organizacao nao admite (ADR-0012).
 *
 * Existe para que a recusa chegue ao chamador como frase, e nao como violacao de `check` crua: quem
 * vai escrever a tela precisa saber qual modo esta em vigor e qual campo foi recusado.
 */
class RosterIdentificationModeException(
    val mode: String,
    val field: String,
    cause: Throwable? = null,
) : RuntimeException(
    "a organizacao opera em modo de identificacao '$mode', que nao admite `$field` no roster",
    cause,
)

/** A prova ja tem pacote publicado. Corrigi-la e publicar prova nova, com hash proprio (D-2a.3). */
class ExamAlreadyPublishedException(val shortId: String, cause: Throwable? = null) :
    IllegalStateException(
        "a prova `$shortId` ja tem pacote publicado; corrigir prova publicada e publicar um pacote " +
            "novo, com short_id proprio — pacote publicado nao e reescrito",
        cause,
    )

/** O que a publicacao devolve: onde o pacote ficou e por qual hash ele atende. */
data class PublishedPackage(
    val examId: UUID,
    val packageId: UUID,
    val contentHash: String,
)

/**
 * Publica uma prova fixa: monta, valida, hasheia e grava (D-2a.1).
 *
 * A montagem e do dominio KMP e e funcao pura; aqui so se grava. A divisao nao e estetica: os dois
 * renderizadores precisam ler o mesmo pacote, e duas interpretacoes independentes do mesmo
 * contrato sao a divergencia que a fatia 1 inteira existiu para eliminar.
 */
class ExamPublication(private val tenancy: Tenancy) {

    fun publish(
        userId: UUID,
        organizationId: UUID,
        definition: ExamDefinition,
        title: String,
        roster: List<RosterEntry> = emptyList(),
    ): PublishedPackage {
        // Monta e valida ANTES de abrir a transacao. "Nenhum pacote parcial e gravado" fica assim
        // sem depender de rollback: quando a primeira linha e escrita, o pacote ja passou por
        // `requireCoherent()`. O rollback continua existindo — `asUser` e uma transacao —, mas nao
        // e ele que carrega a garantia.
        // **Passa tokens, e nao atribuicoes prontas.** O QR de cada folha e resolvido dentro de
        // `buildPackage`, pelo mesmo escritor que a folha da variante usa; montar a atribuicao aqui
        // deixaria este ponto fabricar payload, e seriam dois escritores (o que a 2b fechou).
        val pacote = definition.buildPackage(tokens = roster.map { it.studentToken })

        // Estes bytes sao o artefato. Serializar de novo na hora de gravar seria abrir espaco para
        // um segundo caminho de serializacao — e o hash e sobre este, nao sobre aquele.
        val conteudo = pacote.toCanonicalJson()
        val hash = pacote.contentHash()

        return tenancy.asUser(userId) { ctx ->
            val examId = inserirProva(ctx, organizationId, definition.id, title, userId)
            val packageId = inserirPacote(ctx, organizationId, examId, conteudo, hash)
            inserirRoster(ctx, organizationId, examId, roster)
            PublishedPackage(examId = examId, packageId = packageId, contentHash = hash)
        }
    }

    /**
     * Sem consulta previa por `short_id`, de proposito.
     *
     * Um `select` antes do `insert` deixaria uma janela entre a checagem e a gravacao, e a resposta
     * seria mais bonita e menos verdadeira. Quem decide e a restricao do banco; aqui so se traduz a
     * recusa dela para um erro que o chamador consegue distinguir de "deu erro".
     */
    private fun inserirProva(
        ctx: DSLContext,
        organizationId: UUID,
        shortId: String,
        title: String,
        userId: UUID,
    ): UUID = try {
        ctx.insertInto(EXAM)
            .set(EXAM.ORGANIZATION_ID, organizationId)
            .set(EXAM.SHORT_ID, shortId)
            .set(EXAM.TITLE, title)
            .set(EXAM.CREATED_BY_USER_ID, userId)
            .returningResult(EXAM.ID)
            .fetchSingle()
            .value1()!!
    } catch (violacao: IntegrityConstraintViolationException) {
        // So `unique_violation` vira "ja publicada". Traduzir a classe 23 inteira diria "prova ja
        // publicada" para um short_id fora do formato, que e outro defeito e outra correcao.
        if (sqlStateOf(violacao) != UNIQUE_VIOLATION) throw violacao
        throw ExamAlreadyPublishedException(shortId, violacao)
    }

    private fun inserirPacote(
        ctx: DSLContext,
        organizationId: UUID,
        examId: UUID,
        conteudo: String,
        hash: String,
    ): UUID = try {
        ctx.insertInto(EXAM_PACKAGE)
            .set(EXAM_PACKAGE.ORGANIZATION_ID, organizationId)
            .set(EXAM_PACKAGE.EXAM_ID, examId)
            .set(EXAM_PACKAGE.CONTENT, conteudo)
            .set(EXAM_PACKAGE.CONTENT_HASH, hash)
            .returningResult(EXAM_PACKAGE.ID)
            .fetchSingle()
            .value1()!!
    } catch (violacao: IntegrityConstraintViolationException) {
        if (sqlStateOf(violacao) != UNIQUE_VIOLATION) throw violacao
        throw ExamAlreadyPublishedException(examId.toString(), violacao)
    }

    /**
     * Insere o roster carimbando o modo de identificacao da organizacao (ADR-0012).
     *
     * O modo e lido **na mesma transacao** e gravado em cada linha. Nao e conveniencia: a chave
     * estrangeira composta com `organization (id, identification_mode)` so aceita a linha se os dois
     * lados concordarem, entao carimbar aqui e o que torna a regra verificavel pelo armazenamento em
     * vez de por confianca na aplicacao.
     *
     * A traducao do `check` para excecao existe porque o chamador e a tela: violacao de restricao
     * crua nao diz qual modo esta em vigor nem qual campo foi recusado.
     */
    private fun inserirRoster(
        ctx: DSLContext,
        organizationId: UUID,
        examId: UUID,
        roster: List<RosterEntry>,
    ) {
        val modo = ctx.select(ORGANIZATION.IDENTIFICATION_MODE)
            .from(ORGANIZATION)
            .where(ORGANIZATION.ID.eq(organizationId))
            .fetchSingle()
            .value1()!!

        try {
            for (aluno in roster) {
                ctx.insertInto(EXAM_ROSTER)
                    .set(EXAM_ROSTER.ORGANIZATION_ID, organizationId)
                    .set(EXAM_ROSTER.EXAM_ID, examId)
                    .set(EXAM_ROSTER.STUDENT_TOKEN, aluno.studentToken)
                    .set(EXAM_ROSTER.DISPLAY_NAME, aluno.displayName)
                    .set(EXAM_ROSTER.CLASS_GROUP, aluno.classGroup)
                    .set(EXAM_ROSTER.ENROLLMENT_ID, aluno.enrollmentId)
                    .set(EXAM_ROSTER.IDENTIFICATION_MODE, modo)
                    .execute()
            }
        } catch (violacao: IntegrityConstraintViolationException) {
            if (sqlStateOf(violacao) != CHECK_VIOLATION) throw violacao
            throw RosterIdentificationModeException(modo, "enrollment_id", violacao)
        }
    }

    private fun sqlStateOf(erro: Throwable): String? =
        generateSequence(erro) { it.cause }
            .filterIsInstance<java.sql.SQLException>()
            .firstOrNull()
            ?.sqlState

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
        const val CHECK_VIOLATION = "23514"
    }
}
