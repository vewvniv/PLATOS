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

    companion object {

        /**
         * O limiar e a margem que a fatia 3b mediu, pela regra fixada em ADR-0011.
         *
         * `T = (V + C) / 2`, restrito ao corredor de ADR-0010, com `M = 50` permilagem. Sobre o
         * corpus fotografado — duas folhas da prova e a folha de teste, sete fotos de camera lidas
         * de ponta a ponta, 127 medicoes de bolha preenchida conforme a instrucao e 492 de bolha
         * vazia:
         *
         * | | valor | onde |
         * |---|---|---|
         * | `V`, maior cobertura entre as vazias | **220‰** | `prova1-sombra q36/D` |
         * | `C`, menor cobertura entre as bem preenchidas | **649‰** | `teste-angulo teste/D` |
         * | vao entre as duas nuvens | **429‰** | |
         * | `T = (V + C) / 2` | 435‰, **restrito a 400** | o teto do corredor de ADR-0010 |
         *
         * Aprovou: `V <= T - M` (220 <= 350) e `C >= T + M` (649 <= ... 649 >= 450).
         *
         * **O ponto medio queria 435 e o corredor o puxou para 400.** A folha e a camera separam
         * melhor do que ADR-0010 previu, e o teto e que esta apertando — nao o contrario. Mudar
         * este numero, ou o corredor, exige ADR novo que registre a medicao que motivou, como
         * ADR-0007 determina.
         */
        val MEDIDO_NA_FATIA_3B = OmrThreshold(value = 400, margin = 50)
    }

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
