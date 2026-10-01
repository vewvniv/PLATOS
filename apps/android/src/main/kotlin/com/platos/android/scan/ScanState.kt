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
        /** O caderno deste aluno: as regioes esperadas com o estado de cada uma, e a parcial. */
        val caderno: Caderno,
    ) : ScanState {

        /**
         * A ultima apuracao parcial do gabarito **deste aluno**: a parcial, ou o motivo da recusa.
         * Nula enquanto o gabarito dele nao foi lido em nenhum quadro. Mora no caderno.
         */
        val parcial: PartialScoringOutcome? get() = caderno.parcial

        companion object {
            /**
             * *Dizia "... Nada foi guardado.", e deixou de ser verdade na `slice-5c-1-a-resposta-fica-no-aparelho`:
             * a resposta de cada discursiva capturada passa a ficar no aparelho. O que continua verdade e que
             * nenhum resultado e gravado antes de o caderno completar (P7: dito, e nao so substituido).*
             */
            const val AVISO =
                "A nota nao e definitiva: a correcao das discursivas ainda nao esta disponivel neste " +
                    "aparelho. Nenhum resultado e gravado enquanto o caderno nao completa; as respostas " +
                    "capturadas ficam neste aparelho, e o caderno completo e entregue para envio."

            /**
             * O aviso da tela da resposta quando ela foi sinalizada como desvio
             * (`slice-5c-1-a-resposta-fica-no-aparelho`): texto unico, definido num lugar so. A tela nao
             * corrige o desvio — pede que se confira a folha de papel.
             */
            const val AVISO_DE_DESVIO =
                "O aluno escreveu fora da area de resposta. Confira a folha de papel."
        }
    }

    /**
     * A folha de **outro** aluno apareceu enquanto o caderno corrente esta completo e sem nota
     * (`slice-5c-3-a-nota-no-aparelho`, spec `scan-session`). A sessao **nao** troca o caderno: oferece dar a
     * nota ou descartar e seguir, porque descartar perde as respostas capturadas. O quadro nao e guardado: depois
     * da escolha, o professor escaneia a folha do outro aluno de novo.
     */
    data class NotaPorDarDeOutroAluno(val caderno: Caderno, val alunoNovo: String) : ScanState

    data class Scored(
        val reading: InterpretedReading,
        val score: ObjectiveScore,
    ) : ScanState {

        /** De qual folha e este resultado. */
        val payload: CapturePayload get() = reading.payload
    }
}

/**
 * Se um quadro novo deve ser analisado neste estado: so enquanto se procura a folha ([ScanState.Searching])
 * ou se achou a folha e a leitura nao fechou ([ScanState.NotRead]).
 *
 * **Existe como funcao, e fora da `ScanActivity`, para um teste poder exercita-la** — era uma lambda
 * dentro de `ligaCamera`. O desenho de `slice-5c-1-a-resposta-fica-no-aparelho` depende desta propriedade:
 * depois do primeiro quadro que reconhece algo o estado e [ScanState.ProvaComDiscursiva], que nao e nenhum
 * dos dois, e a analise para sozinha ate o professor tocar em "Escanear outra folha". E isso que faz o
 * recorte acontecer uma vez por regiao e por toque, e nao a cada quadro.
 */
fun deveAnalisar(estado: ScanState): Boolean = estado is ScanState.Searching || estado is ScanState.NotRead
