package com.platos.android.scan

import com.platos.android.vision.FrameOutcome
import com.platos.domain.capture.InterpretedReading
import com.platos.domain.exam.ExamPackage
import com.platos.domain.scoring.ObjectiveScoring
import com.platos.domain.scoring.ScoringOutcome

/**
 * A sessao de escaneamento de uma folha: quadros entram, [ScanState] sai.
 *
 * Kotlin puro. Nao conhece `Mat`, CameraX nem Compose — recebe [FrameOutcome], que e o resultado
 * ja destilado da analise, e decide o que a tela mostra. E a mesma fronteira que a fatia 3a
 * desenhou entre `vision/` e `omr/`, pela mesma razao: o que decide precisa ser testavel sem
 * aparelho.
 *
 * Nesta fatia a sessao e de **uma folha**. Lote, completude e avanco automatico sao a 3d.
 *
 * **A conferencia do `exam_short_id` nao e opcional.** Sem ela, uma folha de outra prova seria
 * apurada contra este gabarito e produziria nota plausivel e errada. `ObjectiveScoring` ja recusa
 * por conjunto de itens divergente, mas a recusa por identificador e anterior e diz a coisa certa
 * a quem segura o aparelho.
 */
class ScanSession(private val examPackage: ExamPackage) {

    var state: ScanState = ScanState.NoPermission
        private set

    /**
     * Se a sessao ja tem uma resposta sobre uma folha na tela.
     *
     * Enquanto tem, quadro que falha **nao apaga o que esta apresentado**: quem acabou de escanear
     * baixa o aparelho, e a folha sai do quadro — isso nao e motivo para o resultado sumir. O que
     * substitui um resultado e outro resultado.
     */
    private val holdsResult: Boolean
        get() = state is ScanState.Scored || state is ScanState.Rejected

    fun onPermission(granted: Boolean) {
        state = if (granted) ScanState.Searching else ScanState.NoPermission
    }

    /** Volta a procurar, descartando o que estiver apresentado. E acao de quem segura o aparelho. */
    fun resume() {
        if (state !is ScanState.NoPermission) state = ScanState.Searching
    }

    /**
     * Um quadro analisado.
     *
     * Um resultado novo **substitui o anterior por inteiro**, e nunca o emenda: o estado e
     * construido so a partir da leitura que chegou. Resultado obsoleto na tela e indistinguivel de
     * resultado correto para quem le, e essa e a forma de falha mais cara desta fatia.
     */
    fun onFrame(outcome: FrameOutcome) {
        if (state is ScanState.NoPermission) return

        state = when (outcome) {
            is FrameOutcome.Read -> resultOf(outcome.reading)
            is FrameOutcome.Unreadable -> ScanState.Rejected(outcome.reason)
            is FrameOutcome.NotRead -> if (holdsResult) state else ScanState.NotRead(outcome.reason)
            is FrameOutcome.NoSheet -> if (holdsResult) state else ScanState.Searching
        }
    }

    private fun resultOf(reading: InterpretedReading): ScanState {
        val carregado = examPackage.meta.examId
        val daFolha = reading.payload.examShortId
        if (daFolha != carregado) {
            return ScanState.Rejected(
                "a folha e de outra prova: o QR diz $daFolha, e o aparelho carrega $carregado",
            )
        }

        return when (val nota = ObjectiveScoring.score(examPackage, reading.payload, reading.answers)) {
            is ScoringOutcome.Rejected -> ScanState.Rejected(nota.reason)
            is ScoringOutcome.Scored -> ScanState.Scored(reading, nota.score)
        }
    }
}
