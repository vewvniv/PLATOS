package com.platos.domain.exam

/** Um pedaço do enunciado, depois de separar texto de referência a fórmula em linha. */
sealed interface StatementSegment {

    /** Texto corrido, já com os escapes resolvidos. */
    data class Text(val text: String) : StatementSegment

    /** Referência a uma fórmula em linha declarada em [Question.inline]. */
    data class Formula(val reference: String) : StatementSegment
}

/** O enunciado traz marcação que esta capacidade não entende. */
class StatementMarkupException(message: String) : IllegalArgumentException(message)

/**
 * Separa o enunciado em texto e referências a fórmula em linha (D-1.6.1).
 *
 * A gramática é deliberadamente pequena:
 *
 * - `{{referencia}}` é uma fórmula em linha. A referência usa o mesmo vocabulário dos recursos:
 *   minúsculas, dígitos e hífen, começando por letra ou dígito.
 * - `\{{` é um `{{` literal, para o enunciado que fala de chaves — um sobre programação, por
 *   exemplo. É o único escape, e a barra só é especial imediatamente antes de `{{`.
 * - **Qualquer outro `{{` é erro.** D-1.6.2: o que não é entendido falha, não é desenhado. Um
 *   parser permissivo imprimiria `{{f-eq1}}` na folha, que é a degradação silenciosa que a spec
 *   proíbe desde a fatia 1.
 *
 * Não resolve a referência: dizer se ela existe é trabalho de [requireSupported], que enxerga a
 * questão inteira. Aqui só se decide o que é texto e o que é marcador.
 */
fun parseStatement(statement: String): List<StatementSegment> {
    val segments = mutableListOf<StatementSegment>()
    val texto = StringBuilder()
    var i = 0

    fun despejarTexto() {
        if (texto.isNotEmpty()) {
            segments += StatementSegment.Text(texto.toString())
            texto.clear()
        }
    }

    while (i < statement.length) {
        val escapou = statement[i] == '\\' && statement.startsWith(ABERTURA, i + 1)
        if (escapou) {
            texto.append(ABERTURA)
            i += 1 + ABERTURA.length
            continue
        }
        if (!statement.startsWith(ABERTURA, i)) {
            texto.append(statement[i])
            i += 1
            continue
        }

        val fim = statement.indexOf(FECHAMENTO, i + ABERTURA.length)
        if (fim < 0) {
            throw StatementMarkupException(
                "enunciado tem `$ABERTURA` sem `$FECHAMENTO` correspondente, na posicao $i; " +
                    "para escrever `$ABERTURA` como texto, use `\\$ABERTURA`",
            )
        }
        val referencia = statement.substring(i + ABERTURA.length, fim)
        if (referencia.contains(ABERTURA)) {
            throw StatementMarkupException(
                "enunciado tem `$ABERTURA` aninhado na posicao $i; marcador nao aninha",
            )
        }
        if (!REFERENCIA.matches(referencia)) {
            throw StatementMarkupException(
                "referencia de formula em linha invalida: `$referencia`; use minusculas, digitos " +
                    "e hifen, comecando por letra ou digito",
            )
        }
        despejarTexto()
        segments += StatementSegment.Formula(referencia)
        i = fim + FECHAMENTO.length
    }

    despejarTexto()
    return segments
}

/** As referências de fórmula em linha citadas por [statement], na ordem em que aparecem. */
fun referencedInlineFormulas(statement: String): List<String> =
    parseStatement(statement).filterIsInstance<StatementSegment.Formula>().map { it.reference }

private const val ABERTURA = "{{"
private const val FECHAMENTO = "}}"
private val REFERENCIA = Regex("[a-z0-9][a-z0-9-]*")

/**
 * Exige que toda referencia do enunciado esteja declarada, e vice-versa (D-1.6.2).
 *
 * As quatro recusas existem pelo mesmo motivo: **o que nao e entendido falha, nao e desenhado.**
 * Uma referencia solta sairia impressa como `{{f-eq1}}` na folha do aluno, e um recurso declarado
 * sem uso e quase sempre um marcador digitado errado no enunciado — os dois sao a degradacao
 * silenciosa que a spec proibe desde a fatia 1.
 */
fun Question.requireInlineFormulasResolved() {
    val citadas = try {
        referencedInlineFormulas(statement)
    } catch (e: StatementMarkupException) {
        throw UnsupportedContentException("questao `$id`: ${e.message}")
    }

    val repetidas = inline.groupingBy { it.reference }.eachCount().filterValues { it > 1 }.keys
    if (repetidas.isNotEmpty()) {
        throw UnsupportedContentException(
            "questao `$id` declara a mesma formula em linha mais de uma vez: " +
                repetidas.sorted().joinToString() + "; a referencia identifica o recurso, e " +
                "duas declaracoes com a mesma referencia nao dizem qual vale",
        )
    }

    val declaradas = inline.associateBy { it.reference }
    val faltando = citadas.toSet() - declaradas.keys
    if (faltando.isNotEmpty()) {
        throw UnsupportedContentException(
            "questao `$id` cita formula em linha nao declarada: " +
                faltando.sorted().joinToString() + "; nenhum layout e emitido, para que a " +
                "referencia nao saia impressa como texto",
        )
    }

    val sobrando = declaradas.keys - citadas.toSet()
    if (sobrando.isNotEmpty()) {
        throw UnsupportedContentException(
            "questao `$id` declara formula em linha que o enunciado nao cita: " +
                sobrando.sorted().joinToString() + "; quase sempre e marcador digitado errado, " +
                "e a folha sairia sem a formula que o enunciado precisa",
        )
    }

    for (formula in inline) {
        if (formula.reference.isBlank()) {
            throw UnsupportedContentException(
                "questao `$id` declara formula em linha sem referencia ao recurso",
            )
        }
        if (formula.width <= 0 || formula.height <= 0) {
            throw UnsupportedContentException(
                "formula em linha `${formula.reference}` da questao `$id` tem dimensao nao " +
                    "positiva: ${formula.width} x ${formula.height} um",
            )
        }
        // Fora deste intervalo a caixa nao tem como se alinhar: um deslocamento maior que a
        // altura poria a formula inteira abaixo da linha de base.
        if (formula.baselineOffset < 0 || formula.baselineOffset > formula.height) {
            throw UnsupportedContentException(
                "formula em linha `${formula.reference}` da questao `$id` tem deslocamento de " +
                    "linha de base fora da caixa: ${formula.baselineOffset} um para altura " +
                    "${formula.height} um",
            )
        }
    }
}
