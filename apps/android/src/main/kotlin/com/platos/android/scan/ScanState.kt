package com.platos.android.scan

import com.platos.domain.capture.CapturePayload
import com.platos.domain.capture.InterpretedReading
import com.platos.domain.scoring.ObjectiveScore
import com.platos.domain.scoring.PartialScoringOutcome

/**
 * O que a tela desenha. Cinco estados, e nenhum deles e ausencia de estado.
 *
 * ```
 * SemPermissao -> Procurando <-> NaoLida(motivo) -> Lida(payload, nota)
 *                      |
 *                      v
 *                 Recusada(motivo)
 * ```
 *
 * [Searching] e [NotRead] sao o mesmo momento com informacao diferente: no segundo, os marcadores
 * foram achados e o pipeline parou depois disso. Os dois continuam analisando quadros. [Rejected]
 * nao: e a folha lida e recusada por identidade ou por corredor, e insistir com a camera nao muda o
 * resultado.
 */
sealed interface ScanState {

    /** A camera nao foi autorizada. Nao ha quadro, e a tela explica para que ela serve. */
    data object NoPermission : ScanState

    /** Nenhuma folha reconhecida no quadro. */
    data object Searching : ScanState

    /** A folha foi achada e a leitura nao fechou. [reason] e a frase que o pipeline produziu. */
    data class NotRead(val reason: String) : ScanState

    /** A folha foi lida e recusada: outra prova, ou corredor que exclui o limiar do aplicativo. */
    data class Rejected(val reason: String) : ScanState

    /**
     * A folha fechou, com a nota apurada contra o pacote carregado.
     *
     * **Carrega a leitura inteira, e nao so a nota**, por duas razoes que sao a mesma. A primeira e
     * [payload]: sem ele, duas folhas diferentes produzem telas indistinguiveis, e a folha B
     * mostrando a nota da folha A e plausivel para quem le. A segunda e a cobertura — quem revisa
     * uma pendencia precisa do numero que a produziu, e nao da palavra "indecisa".
     */
    /**
     * Folha de uma prova com discursiva: reconhecida, com a **parcial objetiva**, e nao gravada
     * (`slice-5b-2-a-nota-objetiva-parcial`).
     *
     * Diz de quem e a folha, o que foi reconhecido nela e, quando o gabarito desse aluno ja foi lido,
     * a parte objetiva apurada como parcial. A parcial **nao e a nota**: a parte discursiva ainda nao
     * foi corrigida, e o offline so e definitivo sem discursiva (§10, D4). Nada e gravado a partir
     * deste estado.
     *
     * *Na `slice-5b-1-o-aparelho-reconhece-a-discursiva`, este estado se chamava
     * `DiscursivaNaoCorrigivel` e nao tinha nota, "nem parcial". A decisao 1a do mantenedor, de
     * 2026-09-26, e mostrar a parcial.*
     */
    data class ProvaComDiscursiva(
        /** O token do aluno, pelo QR: vazio na folha avulsa. */
        val aluno: String,
        /** "lido", o motivo de o gabarito nao ter fechado, ou nulo quando ele nao estava no quadro. */
        val gabarito: String?,
        /** As questoes discursivas reconhecidas no quadro, na ordem das regioes. */
        val discursivas: List<String>,
        /** As regioes discursivas presentes e nao lidas, cada uma com o motivo. */
        val discursivasNaoLidas: List<String>,
        /**
         * A ultima apuracao parcial do gabarito **deste aluno**: a parcial, ou o motivo da recusa.
         * Nula enquanto o gabarito dele nao foi lido em nenhum quadro.
         */
        val parcial: PartialScoringOutcome?,
    ) : ScanState {
        companion object {
            const val AVISO =
                "A nota nao e definitiva: a correcao das discursivas ainda nao esta disponivel neste " +
                    "aparelho. Nada foi guardado."
        }
    }

    data class Scored(
        val reading: InterpretedReading,
        val score: ObjectiveScore,
    ) : ScanState {

        /** De qual folha e este resultado. */
        val payload: CapturePayload get() = reading.payload
    }
}
