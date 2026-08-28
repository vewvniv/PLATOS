package com.platos.android.scan

import com.platos.domain.capture.CapturePayload
import com.platos.domain.capture.InterpretedReading
import com.platos.domain.scoring.ObjectiveScore

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
    data class Scored(
        val reading: InterpretedReading,
        val score: ObjectiveScore,
    ) : ScanState {

        /** De qual folha e este resultado. */
        val payload: CapturePayload get() = reading.payload
    }
}
