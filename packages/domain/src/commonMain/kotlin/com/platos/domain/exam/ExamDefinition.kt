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

/**
 * Um recurso embutido no meio do enunciado — imagem, ou formula em linha.
 *
 * Continua fora do escopo. Formula em linha exige caixa com alinhamento de linha de base dentro da
 * quebra de linha, e e a fatia 1.6; imagem de enunciado e fatia posterior. Formula em **bloco** nao
 * passa por aqui: ela tem campo proprio em [Question.formula], porque ocupa linha inteira e nunca
 * se mistura ao texto corrido.
 */
@Serializable
data class QuestionAsset(
    val kind: String,
    val reference: String,
)

/**
 * Formula em bloco: uma caixa de dimensao conhecida (D-1.5.3).
 *
 * As dimensoes chegam **ja resolvidas** pela conversao, em micrometros. O Layout Engine nao abre o
 * SVG, nao le o PNG e nao mede nada da formula: ele arredonda a altura para a grade e reserva a
 * caixa. E isso que mantem o calculo do `LayoutMap` como funcao pura sobre inteiros (D-1.2) e
 * preserva a comparacao byte a byte entre alvos — nenhum alvo precisa decodificar imagem para
 * calcular geometria.
 *
 * [reference] aponta o raster que os renderizadores desenham. Quem transforma referencia em bytes
 * e o consumidor, pelo caminho unico de D-1.5.5.
 */
@Serializable
data class BlockFormula(
    val reference: String,
    /** Largura em micrometros, como a conversao a resolveu. */
    val width: Int,
    /** Altura em micrometros, antes do arredondamento a grade. */
    val height: Int,
)

/**
 * Formula em linha: caixa atomica que anda dentro do texto corrido (D-1.6.1).
 *
 * Como a formula em bloco, as dimensoes chegam **ja resolvidas** pela conversao, e o Layout Engine
 * nao abre o SVG nem le o PNG. O que esta forma acrescenta e [baselineOffset], sem o qual a caixa
 * nao tem como se alinhar ao texto que a cerca.
 *
 * [baselineOffset] e quanto da caixa fica **abaixo** da linha de base, em micrometros. Um radical,
 * que nao desce, tem deslocamento zero; uma fracao desce cerca de metade da propria altura. Vale
 * entao que o topo da caixa esta em `baseline - (height - baselineOffset)` e a base em
 * `baseline + baselineOffset` — posicionamento por aritmetica inteira sobre a linha de base, que e
 * a mesma conversao unica estabelecida em D-1.5.9.
 */
@Serializable
data class InlineFormula(
    val reference: String,
    /** Largura em micrometros, como a conversao a resolveu. */
    val width: Int,
    /** Altura total da caixa em micrometros: acima mais abaixo da linha de base. */
    val height: Int,
    /** Quanto da caixa fica abaixo da linha de base, em micrometros. Nunca negativo. */
    @SerialName("baseline_offset")
    val baselineOffset: Int,
)

@Serializable
data class Question(
    val id: String,
    val kind: QuestionKind = QuestionKind.OBJECTIVE,
    val statement: String,
    val options: List<String> = emptyList(),
    val assets: List<QuestionAsset> = emptyList(),
    val formula: BlockFormula? = null,
    /**
     * Formulas em linha citadas pelo [statement] por meio de marcador.
     *
     * Mapa, e nao lista, de proposito: a mesma formula pode ser citada duas vezes no mesmo
     * enunciado, e o recurso continua sendo um so. Quem resolve referencia para bytes segue sendo
     * o consumidor, pelo caminho unico de D-1.5.5.
     */
    val inline: Map<String, InlineFormula> = emptyMap(),
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
 * Recusa tudo que esta capacidade nao desenha, antes de qualquer calculo.
 *
 * A spec e explicita: nada de layout parcial, aproximado ou silenciosamente degradado. Uma questao
 * discursiva que virasse "questao sem moldura" produziria uma folha impressa que parece correta e
 * nao tem onde escrever — o pior resultado possivel.
 *
 * A fatia 1.5 **estreita** esta barreira em vez de remove-la: formula em bloco passa, formula em
 * linha, imagem de enunciado e discursiva continuam recusadas. Sem o estreitamento a barreira
 * viraria letra morta na primeira fatia que precisasse de qualquer coisa nova.
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
            val kinds = question.assets.map { it.kind }.distinct().sorted()
            // Formula em linha ganha mensagem propria: e o caso predominante das exatas e vai
            // chegar aqui com frequencia, entao quem le precisa saber que ela e a fatia 1.6 e nao
            // uma limitacao permanente.
            val inline = kinds.filter { it in INLINE_FORMULA_KINDS }
            if (inline.isNotEmpty()) {
                throw UnsupportedContentException(
                    "questao `${question.id}` traz formula em linha no meio do texto " +
                        "(${inline.joinToString()}); esta capacidade desenha apenas formula em " +
                        "bloco, e formula em linha exige caixa com alinhamento de linha de base",
                )
            }
            throw UnsupportedContentException(
                "questao `${question.id}` traz recurso nao suportado nesta fatia: " +
                    kinds.joinToString(),
            )
        }
        question.requireInlineFormulasResolved()
        question.formula?.let { formula ->
            if (formula.reference.isBlank()) {
                throw UnsupportedContentException(
                    "questao `${question.id}` declara formula sem referencia ao recurso",
                )
            }
            // Dimensao nao positiva nao pode virar caixa de area zero desenhada em silencio: a
            // folha sairia com a questao sem a formula que o enunciado menciona.
            if (formula.width <= 0 || formula.height <= 0) {
                throw UnsupportedContentException(
                    "formula `${formula.reference}` da questao `${question.id}` tem dimensao nao " +
                        "positiva: ${formula.width} x ${formula.height} um",
                )
            }
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

/** Tipos de recurso que significam formula no meio do texto corrido — fatia 1.6. */
private val INLINE_FORMULA_KINDS = setOf("inline_formula", "formula_inline", "math_inline")
