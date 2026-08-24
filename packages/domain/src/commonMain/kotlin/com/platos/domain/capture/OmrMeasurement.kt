package com.platos.domain.capture

/**
 * Quanta tinta ha dentro de uma bolha da folha capturada (ADR-0010).
 *
 * A grandeza e a cobertura: media de escuridao dentro do disco, normalizada, com papel em zero e
 * preto pleno no teto. Aqui ela chega em **permilagem inteira**, de 0 a [FULL], por duas razoes.
 *
 * A primeira e D-1.2: `commonMain` nao tem ponto flutuante, e a guarda que afirma isso varre o
 * modulo inteiro, nao so o layout. Medir com `Double` e arredondar na fronteira custa nada — um
 * passo de permilagem e 0,1 ponto percentual, e a distancia entre caneta e bolha vazia medida no
 * papel da fatia 2b foi de **424 pontos**.
 *
 * A segunda importa mais. O `ink_budget` que a regiao declara ja esta em permilagem inteira:
 * `decorative_max`, `threshold_floor` e `threshold_ceiling`. Medicao e orcamento na mesma unidade
 * significa comparar sem converter — e conversao entre fracao, porcentagem e permilagem na
 * fronteira de um limiar e exatamente onde mora o erro de um passo.
 */
data class OmrMeasurement(
    val questionId: String,
    val option: String,
    val coveragePerMille: Int,
) {
    init {
        require(coveragePerMille in 0..FULL) {
            "cobertura de $questionId/$option fora de 0..$FULL: $coveragePerMille"
        }
    }

    /** Identificador da bolha, na mesma forma que o `LayoutMap` e as ferramentas de medicao usam. */
    val id: String get() = "$questionId/$option"

    companion object {
        /** Preto pleno. A mesma escala de `DrawRect.fill` e `DrawText.tone` (D-2b.1). */
        const val FULL: Int = 1_000
    }
}

/**
 * O que saiu de uma tentativa de ler uma folha capturada.
 *
 * Recusa e um resultado, e nao uma excecao, pela mesma razao de [PayloadReading]: quem chama e o
 * laco de captura, para quem folha ilegivel e ocorrencia normal.
 */
sealed interface OmrReading {

    /**
     * A folha foi lida.
     *
     * [payload] diz de quem e a folha, e [measurements] traz **todas** as bolhas que a regiao
     * declara — nunca um subconjunto. Medicao parcial silenciosa e o modo de falha que ADR-0010
     * descreve: ela chega a nota como acerto ou erro que ninguem marcou.
     */
    data class Read(
        val payload: CapturePayload,
        val measurements: List<OmrMeasurement>,
    ) : OmrReading

    /** A folha nao pode ser lida, e [reason] diz por que numa frase que serve para a tela. */
    data class Rejected(val reason: String) : OmrReading
}
