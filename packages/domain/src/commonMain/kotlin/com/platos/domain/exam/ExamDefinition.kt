package com.platos.domain.exam

import com.platos.domain.capture.CaptureGeometry
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
 * Como a resposta de uma discursiva e recortada da folha (§8, "Modo de cor").
 *
 * Cinza por padrao, e cor so quando a questao declara: resposta com grafico colorido, mapa, lamina.
 * Quem consome e a captura da regiao discursiva, na fatia seguinte a que introduziu este tipo — e
 * ele entrou antes do consumidor para que o `content_hash` quebrasse uma vez so, com todo o contrato
 * discursivo junto (decisao 1 do `design.md` da `slice-5a-regiao-discursiva`).
 */
@Serializable
enum class AnswerCaptureMode {
    @SerialName("gray")
    GRAY,

    @SerialName("color")
    COLOR,
}

/** Um nivel de desempenho de um criterio: quanto ele vale e como se reconhece. */
@Serializable
data class RubricDescriptor(
    val points: Int,
    val text: String,
)

/**
 * Um criterio da rubrica analitica (§5, §11 `item_rubric_criterion`).
 *
 * [expectedLines] e **por criterio**, e a moldura da questao mede a soma deles (D35, §7): a IA — ou o
 * professor — escreve a rubrica, a rubrica define o espaco, e o espaco condiciona a resposta. O §11 e
 * quem diz em que nivel o campo mora; o §5 so o lista.
 */
@Serializable
data class RubricCriterion(
    val id: String,
    val description: String,
    val points: Int,
    @SerialName("expected_lines") val expectedLines: Int,
    val descriptors: List<RubricDescriptor>,
)

/** Rubrica analitica de uma questao discursiva: a lista dos criterios, na ordem declarada. */
@Serializable
data class Rubric(
    val criteria: List<RubricCriterion>,
)

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
     * Lista, e nao mapa indexado pela referencia: a chave duplicaria [InlineFormula.reference] e
     * exigiria uma guarda so para manter as duas em sincronia. `assets` ja e lista na mesma classe,
     * e o `ExamPackage` da fatia 2a publicaria a mesma string duas vezes em todo diff de prova.
     *
     * Uma formula pode ser citada mais de uma vez pelo enunciado e continua declarada uma vez so.
     * Quem resolve referencia para bytes segue sendo o consumidor, pelo caminho unico de D-1.5.5.
     */
    val inline: List<InlineFormula> = emptyList(),
    /**
     * Habilidades do curriculo que a questao cobre (I1).
     *
     * Opcional AQUI e obrigatoria na publicacao, de proposito. O Layout Engine nao sabe o que e
     * habilidade e nao deve saber; quem precisa da barreira e o pacote publicado, porque e dele
     * que sai o fato que M3 vai agregar. Exigir aqui obrigaria toda questao sintetica de teste de
     * geometria a declarar habilidade, sem nada ganhar.
     */
    val skills: List<ItemSkill> = emptyList(),
    /**
     * O TEXTO da alternativa correta, e nao a letra.
     *
     * A letra e derivada de `options.indexOf(answer)` na publicacao. Guardar a letra a mao deixava
     * o gabarito poder divergir do conteudo sem ninguem ver — bastava alguem reordenar as
     * alternativas. Guardando o valor, a divergencia deixa de ser representavel.
     */
    val answer: String? = null,
    /** Como se chega a [answer]. Existe para auditoria humana: gabarito sem procedencia e chute. */
    val why: String? = null,
    /** Pontuacao do item. */
    val points: Int = 1,
    /** Rubrica analitica. Obrigatoria na discursiva e proibida na objetiva. */
    val rubric: Rubric? = null,
    /**
     * Modo de captura da resposta discursiva. Nulo e o padrao, que e cinza; na objetiva nao se
     * declara, porque o gabarito e preto e branco por construcao (§8).
     */
    @SerialName("answer_capture_mode") val answerCaptureMode: AnswerCaptureMode? = null,
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
 *
 * A `slice-5a-regiao-discursiva` a estreita de novo, pela mesma razao: a discursiva **com rubrica**
 * passa, porque agora ela tem moldura, e a moldura sai da rubrica (D35). A discursiva sem rubrica
 * continua recusada — e pelo mesmo motivo que a recusava antes: ela seria uma questao sem onde
 * escrever.
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
        when (question.kind) {
            QuestionKind.OBJECTIVE -> question.requireObjectiveShape()
            QuestionKind.ESSAY -> question.requireEssayShape()
        }
        if (question.assets.isNotEmpty()) {
            val kinds = question.assets.map { it.kind }.distinct().sorted()
            // Formula em linha ganha mensagem propria: e o caso predominante das exatas e vai
            // chegar aqui com frequencia, entao quem le precisa saber que ela e a fatia 1.6 e nao
            // uma limitacao permanente.
            val inline = kinds.filter { it in INLINE_FORMULA_KINDS }
            if (inline.isNotEmpty()) {
                // Formula em linha deixou de ser recusada e passou a ter forma propria: marcador no
                // enunciado mais `Question.inline`. Declara-la como recurso embutido continua sendo
                // recusado, agora por ser o caminho errado e nao por ser fora de escopo — e a
                // mensagem precisa dizer isso, senao manda quem le esperar por uma fatia futura que
                // ja chegou.
                throw UnsupportedContentException(
                    "questao `${question.id}` declara formula em linha como recurso embutido " +
                        "(${inline.joinToString()}); formula em linha se declara com marcador no " +
                        "enunciado e a caixa em `inline`, com largura, altura e deslocamento de " +
                        "linha de base",
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
        if (question.statement.isBlank()) {
            throw UnsupportedContentException("questao `${question.id}` tem enunciado vazio")
        }
    }

    // Toda folha tem uma regiao de gabarito (§8, "sempre uma regiao `ANSWER_BLOCK`"). Sem objetiva
    // ela seria uma regiao sem bolha nenhuma, que nenhum leitor consome — decisao 7 do `design.md`
    // da `slice-5a-regiao-discursiva`. Libera-la e decisao para quando houver pedido.
    if (questions.none { it.kind == QuestionKind.OBJECTIVE }) {
        throw UnsupportedContentException(
            "prova `$id` nao tem questao objetiva; toda folha tem uma regiao de gabarito (§8), e " +
                "sem objetiva ela nao teria bolha nenhuma a medir",
        )
    }

    // O teto vem do dicionario, e nao de um numero escolhido: cada discursiva e uma regiao de quatro
    // marcadores, e a regiao 0 e a do gabarito. Recusar aqui, antes do calculo, e o que faz a
    // mensagem nomear a prova em vez de estourar no meio do layout (decisao 8).
    val discursivas = questions.count { it.kind == QuestionKind.ESSAY }
    val teto = CaptureGeometry.MAX_REGIONS - 1
    if (discursivas > teto) {
        throw UnsupportedContentException(
            "prova `$id` tem $discursivas questoes discursivas; o dicionario de marcadores comporta " +
                "${CaptureGeometry.MAX_REGIONS} regioes, uma delas e a do gabarito, e sobram $teto",
        )
    }
}

/** A objetiva tem alternativas, e nao tem rubrica nem modo de captura. */
private fun Question.requireObjectiveShape() {
    if (rubric != null) {
        throw UnsupportedContentException(
            "questao `$id` e objetiva e declara rubrica; rubrica e da discursiva, e a objetiva e " +
                "corrigida pelo gabarito",
        )
    }
    // Nao ha requisito que nomeie este caso, e ele cai na clausula geral da spec: conteudo que a
    // capacidade nao desenha nao degrada em silencio. Ignorar o campo seria exatamente isso.
    if (answerCaptureMode != null) {
        throw UnsupportedContentException(
            "questao `$id` e objetiva e declara modo de captura; o gabarito e preto e branco por " +
                "construcao (§8), e o modo e da resposta discursiva",
        )
    }
    if (options.size < MIN_OPTIONS) {
        throw UnsupportedContentException(
            "questao `$id` tem ${options.size} alternativa(s); o minimo e $MIN_OPTIONS",
        )
    }
    if (options.size > MAX_OPTIONS) {
        throw UnsupportedContentException(
            "questao `$id` tem ${options.size} alternativas; o maximo e $MAX_OPTIONS",
        )
    }
}

/**
 * A discursiva tem rubrica que fecha com a pontuacao dela, e nao tem alternativas nem gabarito.
 *
 * Cada recusa aqui e uma folha que sairia errada sem ninguem notar: sem rubrica nao ha moldura; com
 * alternativas o aluno veria opcoes que nenhuma bolha le; e uma rubrica que nao soma a pontuacao
 * faria a nota maxima da prova depender de qual dos dois numeros alguem lesse.
 */
private fun Question.requireEssayShape() {
    val rubrica = rubric ?: throw UnsupportedContentException(
        "questao `$id` e discursiva e nao declara rubrica; a moldura e dimensionada pelos " +
            "`expected_lines` da rubrica (D35), e sem ela a folha nao teria onde o aluno escrever",
    )
    if (options.isNotEmpty()) {
        throw UnsupportedContentException(
            "questao `$id` e discursiva e declara ${options.size} alternativa(s); discursiva nao tem " +
                "alternativas, e nenhuma bolha leria a escolha",
        )
    }
    if (answer != null) {
        throw UnsupportedContentException(
            "questao `$id` e discursiva e declara resposta de gabarito; discursiva e corrigida pela " +
                "rubrica, e nao pelo gabarito",
        )
    }
    if (rubrica.criteria.isEmpty()) {
        throw UnsupportedContentException("a rubrica da questao `$id` nao tem criterio")
    }
    val repetidos = rubrica.criteria.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
    if (repetidos.isNotEmpty()) {
        throw UnsupportedContentException(
            "a rubrica da questao `$id` tem criterios com identificador repetido: " +
                repetidos.sorted().joinToString(),
        )
    }
    for (criterio in rubrica.criteria) {
        if (criterio.points <= 0) {
            throw UnsupportedContentException(
                "o criterio `${criterio.id}` da questao `$id` tem pontos nao positivos: " +
                    "${criterio.points}",
            )
        }
        if (criterio.expectedLines < 1) {
            throw UnsupportedContentException(
                "o criterio `${criterio.id}` da questao `$id` pede ${criterio.expectedLines} " +
                    "linha(s); o minimo e 1, porque e dele que sai a altura da moldura",
            )
        }
        if (criterio.descriptors.isEmpty()) {
            throw UnsupportedContentException(
                "o criterio `${criterio.id}` da questao `$id` nao tem descritor",
            )
        }
        val foraDaEscala = criterio.descriptors.filter { it.points !in 0..criterio.points }
        if (foraDaEscala.isNotEmpty()) {
            throw UnsupportedContentException(
                "o criterio `${criterio.id}` da questao `$id` tem descritor fora de " +
                    "[0, ${criterio.points}]: ${foraDaEscala.map { it.points }}",
            )
        }
    }
    val soma = rubrica.criteria.sumOf { it.points }
    if (soma != points) {
        throw UnsupportedContentException(
            "a rubrica da questao `$id` soma $soma ponto(s), e a questao vale $points; os dois " +
                "precisam ser o mesmo numero",
        )
    }
}

/** Alternativas por questao suportadas nesta fatia: de (A) a (E). */
const val MIN_OPTIONS = 2
const val MAX_OPTIONS = 5

/** Tipos de recurso que significam formula no meio do texto corrido — fatia 1.6. */
private val INLINE_FORMULA_KINDS = setOf("inline_formula", "formula_inline", "math_inline")
