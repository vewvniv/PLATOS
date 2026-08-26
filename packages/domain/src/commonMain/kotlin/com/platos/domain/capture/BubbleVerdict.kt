package com.platos.domain.capture

import com.platos.domain.layout.InkBudget

/**
 * O que a leitura conclui sobre uma bolha, depois de comparar a cobertura com o limiar.
 *
 * Sao tres, e nao dois. [INDECISA] existe porque a alternativa e pior: sem ela, uma bolha a um
 * passo do limiar viraria [MARCADA] ou [VAZIA] por arredondamento, e a duvida chegaria a nota como
 * acerto ou erro que ninguem marcou — o modo de falha que ADR-0010 descreve.
 */
enum class BubbleVerdict {
    MARCADA,
    VAZIA,
    INDECISA,
}

/**
 * O limiar da leitura optica e a margem de indecisao em volta dele (ADR-0011).
 *
 * **Permilagem inteira**, a mesma unidade em que [OmrMeasurement] traz a cobertura e em que
 * [InkBudget] declara o corredor. Comparar sem converter e o ponto: conversao entre fracao,
 * porcentagem e permilagem na fronteira de um limiar e exatamente onde mora o erro de um passo.
 *
 * **O limiar mora no aplicativo, e a folha declara o corredor.** ADR-0010 pos o corredor no
 * artefato publicado porque folha impressa e aplicativo deixam de andar juntos; [validateAgainst]
 * e o encontro dos dois. O contrario — congelar o limiar em cada folha — obrigaria a reimprimir
 * prova ja distribuida para melhorar o numero, que e o oposto do que o corredor existe para
 * evitar.
 *
 * @param value o limiar `T`, apurado pela regra de ADR-0011 sobre o corpus fotografado.
 * @param margin a margem `M`. Bolha dentro de `(T - M, T + M)` e [BubbleVerdict.INDECISA].
 */
data class OmrThreshold(val value: Int, val margin: Int) {

    init {
        require(value in 0..OmrMeasurement.FULL) {
            "limiar fora de 0..${OmrMeasurement.FULL}: $value"
        }
        require(margin >= 0) { "margem nao pode ser negativa: $margin" }
        require(value - margin >= 0 && value + margin <= OmrMeasurement.FULL) {
            "a faixa de indecisao $value +- $margin sai da escala 0..${OmrMeasurement.FULL}"
        }
    }

    /** Piso decidido: cobertura ate aqui, inclusive, e [BubbleVerdict.VAZIA]. */
    val undecidedFrom: Int get() = value - margin

    /** Teto decidido: cobertura daqui para cima, inclusive, e [BubbleVerdict.MARCADA]. */
    val undecidedTo: Int get() = value + margin

    /**
     * O veredito de uma cobertura.
     *
     * As bordas sao **inclusivas do lado decidido**, e isso e a mesma escolha que ADR-0011 faz ao
     * aprovar com `V <= T - M` e `C >= T + M`: o corpus que aprova o limiar precisa ser lido por
     * ele do jeito que o aprovou. Cobertura exatamente igual a `T` e [BubbleVerdict.INDECISA] —
     * o limiar e o centro da duvida, e nao um dos lados dela.
     */
    fun verdictFor(coveragePerMille: Int): BubbleVerdict = when {
        coveragePerMille >= undecidedTo -> BubbleVerdict.MARCADA
        coveragePerMille <= undecidedFrom -> BubbleVerdict.VAZIA
        else -> BubbleVerdict.INDECISA
    }

    /** O veredito de uma medicao, com a cobertura preservada ao lado dele. */
    fun judge(measurement: OmrMeasurement): BubbleJudgement =
        BubbleJudgement(measurement, verdictFor(measurement.coveragePerMille))

    /**
     * Confere se este limiar cabe no corredor que a folha declara.
     *
     * Devolve o motivo da recusa, ou `null` quando a folha e legivel por este aplicativo. Uma
     * folha gerada por engine que reservou outro corredor **nao** e lida com aproximacao: ler
     * assim mesmo produziria acerto ou erro que ninguem marcou, sobre artefato imutavel.
     */
    fun validateAgainst(budget: InkBudget): String? =
        if (value in budget.thresholdFloor..budget.thresholdCeiling) {
            null
        } else {
            "limiar $value fora do corredor que a folha declara: " +
                "${budget.thresholdFloor}..${budget.thresholdCeiling}"
        }
}

/**
 * Uma bolha julgada: o veredito, e a cobertura que o produziu.
 *
 * Os dois juntos, e nao so o veredito, porque quem revisa uma folha precisa ver o numero. Uma
 * pendencia que diz apenas "indecisa" obriga o professor a decidir sem o dado que a leitura tinha.
 */
data class BubbleJudgement(
    val measurement: OmrMeasurement,
    val verdict: BubbleVerdict,
) {
    /** Identificador da bolha, na mesma forma que o `LayoutMap` e as ferramentas de medicao usam. */
    val id: String get() = measurement.id
}
