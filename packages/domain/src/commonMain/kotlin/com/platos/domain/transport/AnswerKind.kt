package com.platos.domain.transport

import com.platos.domain.capture.QuestionAnswer

/**
 * Os quatro valores de `answer_kind`, num lugar so — e este e o lugar (ADR-0015 decisao 2).
 *
 * Eles viviam em **tres** registros que nao se conheciam: `tipoGravado()` na API, `tipoNoEnvio()`
 * no aparelho, e o `check (answer_kind in (...))` da migration. Os dois primeiros eram Kotlin e
 * viraram este arquivo; o terceiro **continua existindo**, de proposito.
 *
 * **Por que o `check` nao e gerado a partir daqui** (ADR-0015 decisao 3): ele e a guarda do banco, e
 * uma guarda escrita por quem escreve as linhas aceitaria, por construcao, tudo o que o codigo
 * produzisse — inclusive o que ele produzisse errado. A independencia e o valor dela. O que amarra
 * os dois e `tools/parity/answer-kind.mjs`, que le os dois **dos arquivos de origem** e reprova se
 * divergirem.
 *
 * As constantes sao `const val` para que o `when` de quem **le** o campo (o servidor, ao traduzir o
 * corpo recebido de volta para `QuestionAnswer`) possa ramificar sobre elas em vez de repetir os
 * literais. Sem isso, o parse do servidor seria um terceiro registro Kotlin, e a unificacao teria
 * fechado so metade do defeito.
 *
 * **Nao ha um quinto valor**: "em branco" e afirmacao sobre o que o aluno fez, "indecisa" e
 * afirmacao sobre o que a leitura conseguiu apurar, e as duas so parecem iguais ate a nota.
 */
object AnswerKind {
    const val MARCADA: String = "marcada"
    const val EM_BRANCO: String = "em_branco"
    const val MULTIPLA_MARCACAO: String = "multipla_marcacao"
    const val INDECISA: String = "indecisa"

    /** Na ordem em que o `check` da migration os lista, que e a ordem que o conferidor compara. */
    val TODOS: List<String> = listOf(MARCADA, EM_BRANCO, MULTIPLA_MARCACAO, INDECISA)
}

/** O tipo da resposta, como ele viaja e como ele e gravado. */
fun QuestionAnswer.answerKind(): String = when (this) {
    is QuestionAnswer.Marcada -> AnswerKind.MARCADA
    is QuestionAnswer.EmBranco -> AnswerKind.EM_BRANCO
    is QuestionAnswer.MultiplaMarcacao -> AnswerKind.MULTIPLA_MARCACAO
    is QuestionAnswer.Indecisa -> AnswerKind.INDECISA
}

/**
 * Todas as alternativas envolvidas, e nunca a "vencedora".
 *
 * Em `multipla_marcacao` e `indecisa` sao todas: desempatar por qualquer criterio transformaria
 * rasura em resposta, e a rasura e justamente o caso em que a folha nao diz o que o aluno quis.
 *
 * Esta e a forma do **fio** — `List<String>`. A conversao para o `Array<String?>` que o jOOQ quer
 * fica no servidor, porque ela fala com o banco e o banco e so dele (ADR-0015 decisao 2).
 */
fun QuestionAnswer.answerOptions(): List<String> = when (this) {
    is QuestionAnswer.Marcada -> listOf(option)
    is QuestionAnswer.EmBranco -> emptyList()
    is QuestionAnswer.MultiplaMarcacao -> options
    is QuestionAnswer.Indecisa -> options
}
