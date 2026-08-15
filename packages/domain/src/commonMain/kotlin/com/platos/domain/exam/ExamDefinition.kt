package com.platos.domain.exam

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** O que a fatia 1 sabe desenhar. Tudo o mais e recusado explicitamente. */
@Serializable
enum class QuestionKind {
    @SerialName("objective")
    OBJECTIVE,

    @SerialName("essay")
    ESSAY,
}

/** Um recurso embutido no enunciado — imagem ou formula. Nenhum deles entra nesta fatia. */
@Serializable
data class QuestionAsset(
    val kind: String,
    val reference: String,
)

@Serializable
data class Question(
    val id: String,
    val kind: QuestionKind = QuestionKind.OBJECTIVE,
    val statement: String,
    val options: List<String> = emptyList(),
    val assets: List<QuestionAsset> = emptyList(),
)

/**
 * Entrada pura do Layout Engine.
 *
 * Nao ha banco nem rede nesta fatia: a definicao vem de arquivo versionado (D-1.9). A fatia 2
 * troca a origem por `ExamPackage` sem mudar esta forma.
 */
@Serializable
data class ExamDefinition(
    val id: String,
    val title: String,
    val questions: List<Question>,
)

/** A definicao usa um recurso que esta capacidade ainda nao desenha. */
class UnsupportedContentException(message: String) : IllegalArgumentException(message)

/**
 * Recusa tudo que a fatia 1 nao desenha, antes de qualquer calculo.
 *
 * A spec e explicita: nada de layout parcial, aproximado ou silenciosamente degradado. Uma questao
 * discursiva que virasse "questao sem moldura" produziria uma folha impressa que parece correta e
 * nao tem onde escrever — o pior resultado possivel.
 */
fun ExamDefinition.requireSupported() {
    if (questions.isEmpty()) {
        throw UnsupportedContentException("prova `$id` nao tem questoes")
    }

    val duplicated = questions.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
    if (duplicated.isNotEmpty()) {
        throw UnsupportedContentException(
            "questoes com identificador repetido: ${duplicated.sorted().joinToString()}",
        )
    }

    for (question in questions) {
        if (question.kind != QuestionKind.OBJECTIVE) {
            throw UnsupportedContentException(
                "questao `${question.id}` e do tipo `${question.kind}`; esta fatia desenha apenas " +
                    "questoes objetivas, e a regiao discursiva pertence a uma fatia posterior",
            )
        }
        if (question.assets.isNotEmpty()) {
            val kinds = question.assets.map { it.kind }.distinct().sorted().joinToString()
            throw UnsupportedContentException(
                "questao `${question.id}` traz recurso nao suportado nesta fatia: $kinds",
            )
        }
        if (question.options.size < MIN_OPTIONS) {
            throw UnsupportedContentException(
                "questao `${question.id}` tem ${question.options.size} alternativa(s); o minimo e " +
                    "$MIN_OPTIONS",
            )
        }
        if (question.options.size > MAX_OPTIONS) {
            throw UnsupportedContentException(
                "questao `${question.id}` tem ${question.options.size} alternativas; o maximo e " +
                    "$MAX_OPTIONS",
            )
        }
        if (question.statement.isBlank()) {
            throw UnsupportedContentException("questao `${question.id}` tem enunciado vazio")
        }
    }
}

/** Alternativas por questao suportadas nesta fatia: de (A) a (E). */
const val MIN_OPTIONS = 2
const val MAX_OPTIONS = 5
