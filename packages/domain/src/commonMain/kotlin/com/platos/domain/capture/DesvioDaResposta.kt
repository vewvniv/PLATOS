package com.platos.domain.capture

/**
 * Se a resposta escrita **extrapola a area** (`slice-5c-0-o-recorte-da-resposta`, design, decisao 5;
 * §8, D45: "avisar, em vez de corrigir errado em silencio").
 *
 * A captura conta a tinta do aluno dentro da area de resposta e na faixa de fora dela; esta classe
 * decide. A decisao e do dominio, e nao do Android, porque e regra de negocio pura — nao toca imagem
 * — e porque os dois numeros dela tem dono unico aqui (regra 7 do `CLAUDE.md`, P28).
 *
 * **Aritmetica inteira (D-1.2).** Nenhum ponto flutuante no `commonMain`: a area e contada em
 * **centesimos de mm2**, que e exatamente um pixel a 10 px/mm — a resolucao do recorte —, e a
 * proporcao em partes por milhao. A comparacao e por multiplicacao, sem divisao, e por isso "exatamente
 * 5%" e exatamente 5%.
 *
 * **Os dois numeros sao suposicoes fixadas antes da primeira execucao, e nao medicoes.** Nenhuma
 * letra de aluno foi fotografada. ADR-0007 e P11: nao se afrouxa depois de conhecido o resultado, e
 * mudar exige ADR que registre o resultado obtido. A linha do par. 16 "O limiar do desvio e o teto do
 * residuo foram fixados sem letra de aluno" os mantem a vista ate a sessao de papel.
 */
data class DesvioDaResposta(
    /** Tinta do aluno dentro da area, em centesimos de mm2. */
    val dentro: Long,
    /** Tinta do aluno na faixa fora da area, em centesimos de mm2. */
    val fora: Long,
    /** Parte da tinta do aluno que esta fora da area, em partes por milhao. Zero quando nao ha tinta. */
    val proporcaoForaPpm: Int,
    val sinalizado: Boolean,
) {
    companion object {
        /** A resposta e sinalizada quando ao menos 5% da tinta do aluno esta fora da area... */
        const val PROPORCAO_MINIMA_PPM: Int = 50_000

        /**
         * ... **e** a tinta de fora soma ao menos 4 mm2 (400 centesimos).
         *
         * Cerca de um traco de 0,4 x 10 mm. Abaixo disso e poeira, sombra na borda ou um ponto de
         * caneta; sem o piso, uma resposta curta com um respingo passaria dos 5% sozinha.
         */
        const val PISO_FORA: Long = 400

        /**
         * Acima disto a entrada nao e uma folha: 10 m2. Existe para a multiplicacao abaixo nao
         * estourar, e para um contador quebrado nao virar, calado, um numero enorme e plausivel.
         */
        const val TETO_DA_ENTRADA: Long = 1_000_000_000L

        private const val MILHAO: Long = 1_000_000L

        /**
         * Classifica a tinta contada. Entrada negativa ou acima do [TETO_DA_ENTRADA] e **recusada**, e
         * nao tratada como "sem desvio": um contador quebrado que devolvesse lixo viraria, em silencio,
         * uma resposta que nunca e sinalizada.
         */
        fun classificar(dentro: Long, fora: Long): DesvioDaResposta {
            require(dentro in 0..TETO_DA_ENTRADA) { "tinta dentro da area invalida: $dentro" }
            require(fora in 0..TETO_DA_ENTRADA) { "tinta fora da area invalida: $fora" }

            val total = dentro + fora
            val proporcaoPpm = if (total == 0L) 0 else (fora * MILHAO / total).toInt()
            return DesvioDaResposta(
                dentro = dentro,
                fora = fora,
                proporcaoForaPpm = proporcaoPpm,
                // Por multiplicacao, e nao pela proporcao truncada acima: `fora / total >= 5%` sem divisao.
                sinalizado = total > 0L &&
                    fora * MILHAO >= total * PROPORCAO_MINIMA_PPM &&
                    fora >= PISO_FORA,
            )
        }
    }
}
