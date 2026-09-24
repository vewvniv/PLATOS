package com.platos.android.scan

import com.platos.android.vision.FrameOutcome
import com.platos.android.vision.RegiaoDiscursivaNoQuadro
import com.platos.domain.capture.CapturePayload
import com.platos.domain.capture.InterpretedReading
import com.platos.domain.exam.ExamPackage
import com.platos.domain.scoring.ObjectiveScore
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
        get() = state is ScanState.Scored || state is ScanState.Rejected ||
            state is ScanState.DiscursivaNaoCorrigivel

    /**
     * Se a prova desta sessao tem parte discursiva — decidido pelo **pacote**, e nao pelo quadro
     * (decisao 4 do `design.md` da `slice-5b-1-o-aparelho-reconhece-a-discursiva`).
     *
     * Um quadro da primeira folha de uma prova com discursiva pode trazer so o gabarito. Decidir pelo
     * quadro apuraria essa folha como prova objetiva, e entregaria nota de uma prova que tem parte
     * discursiva. `fully_offline_gradable` e o campo que declara isso, e desde a 5a a coerencia do
     * pacote o recusa em desacordo com os itens.
     */
    private val comDiscursiva: Boolean = !examPackage.meta.fullyOfflineGradable

    fun onPermission(granted: Boolean) {
        state = if (granted) ScanState.Searching else ScanState.NoPermission
    }

    /**
     * Volta a procurar, descartando o que estiver apresentado. E acao de quem segura o aparelho.
     *
     * **Limpa tambem a marca de folha ja apurada**, e nao so o estado da tela. E o que faz
     * reapresentar a mesma folha de proposito valer como captura nova — o professor que desconfia da
     * leitura e escaneia de novo quer uma segunda correcao, e o servidor a grava como revisao nova.
     * Sem isto, a segunda passada seria confundida com a folha que nunca saiu do quadro.
     */
    fun resume() {
        if (state !is ScanState.NoPermission) state = ScanState.Searching
        apurada = null
    }

    /**
     * A folha que ja foi apurada e entregue para gravar, identificada pelo payload dela.
     *
     * **O payload, e nao a leitura inteira.** Uma folha parada na frente da camera produz um quadro
     * por vez, e cada um deles apura de novo; comparar a leitura faria o menor ruido de OMR — uma
     * bolha que oscila na faixa de decisao entre dois quadros — parecer folha nova e gerar uma
     * segunda captura da mesma folha. O payload e estavel enquanto a folha e a mesma.
     *
     * Limpo por [resume], e e isso que torna a reapresentacao deliberada da mesma folha uma
     * **captura nova** — que o servidor grava como revisao nova, e nao como duplicata.
     */
    private var apurada: CapturePayload? = null

    /**
     * Um quadro analisado. Devolve a apuracao **quando ela e uma captura nova**, e `null` no resto.
     *
     * Um resultado novo **substitui o anterior por inteiro**, e nunca o emenda: o estado e
     * construido so a partir da leitura que chegou. Resultado obsoleto na tela e indistinguivel de
     * resultado correto para quem le, e essa e a forma de falha mais cara desta fatia.
     *
     * **Devolver, e nao gravar.** Gravar daqui poria disco dentro da classe que existe para ser
     * testavel sem aparelho, e o `captureId` e o instante — que sao um identificador novo e um
     * relogio — tornariam a apuracao nao-deterministica. Quem chama cunha os dois e grava; esta
     * decide **se** ha o que gravar.
     */
    fun onFrame(outcome: FrameOutcome): ApuracaoNova? {
        if (state is ScanState.NoPermission) return null
        if (comDiscursiva) {
            // Reconhece e explica, e nunca apura: nada desta prova vira resultado neste aparelho.
            state = estadoDaDiscursiva(outcome)
            return null
        }

        state = when (outcome) {
            is FrameOutcome.Read -> resultOf(outcome.reading)
            is FrameOutcome.Unreadable -> ScanState.Rejected(outcome.reason)
            is FrameOutcome.NotRead -> if (holdsResult) state else ScanState.NotRead(outcome.reason)
            is FrameOutcome.NoSheet -> if (holdsResult) state else ScanState.Searching
            // Prova so objetiva nao tem regiao discursiva no mapa, entao nenhuma pode ter sido achada
            // no quadro: o caso nao acontece, e se acontecer e folha que nao e desta prova — o mesmo
            // que nao ter achado folha nenhuma.
            is FrameOutcome.SoDiscursivas -> if (holdsResult) state else ScanState.Searching
        }

        val apuracao = state as? ScanState.Scored ?: return null
        // Folha recusada nao chega aqui, e e o requisito: recusa nao e correcao, e nao vira
        // resultado duravel. O `as?` acima e quem garante isso — `Rejected` nao e `Scored`.
        if (apuracao.reading.payload == apurada) return null

        apurada = apuracao.reading.payload
        return ApuracaoNova(apuracao.reading, apuracao.score)
    }

    /**
     * O que a tela mostra de uma folha de prova com discursiva.
     *
     * A folha e identificada pelo QR de qualquer regiao lida no quadro — o gabarito ou uma
     * discursiva —, e a conferencia de prova e a mesma da prova objetiva: QR de outra prova e recusa,
     * com a mesma frase. Nenhuma regiao com QR lido e "achei a folha e nao consegui ler", como hoje.
     */
    private fun estadoDaDiscursiva(outcome: FrameOutcome): ScanState {
        if (outcome is FrameOutcome.NoSheet) return if (holdsResult) state else ScanState.Searching

        val reconhecidas = outcome.discursivas.filterIsInstance<RegiaoDiscursivaNoQuadro.Reconhecida>()
        val naoLidas = outcome.discursivas.filterIsInstance<RegiaoDiscursivaNoQuadro.NaoLida>()
        val payloads = buildList {
            if (outcome is FrameOutcome.Read) add(outcome.reading.payload)
            reconhecidas.forEach { add(it.payload) }
        }

        if (payloads.isEmpty()) {
            return when (outcome) {
                // A recusa da interpretacao nao e transitoria, e continua sendo recusa.
                is FrameOutcome.Unreadable -> ScanState.Rejected(outcome.reason)
                else -> {
                    val motivo = (outcome as? FrameOutcome.NotRead)?.reason
                        ?: naoLidas.firstOrNull()?.reason
                        ?: "nenhuma regiao do quadro foi lida"
                    if (holdsResult) state else ScanState.NotRead(motivo)
                }
            }
        }

        val carregado = examPackage.meta.examId
        payloads.firstOrNull { it.examShortId != carregado }?.let { deOutra ->
            return ScanState.Rejected(
                "a folha e de outra prova: o QR diz ${deOutra.examShortId}, e o aparelho carrega $carregado",
            )
        }
        val alunos = payloads.map { it.studentToken }.distinct()
        if (alunos.size > 1) {
            return ScanState.Rejected("o quadro tem regioes de folhas diferentes: ${alunos.joinToString()}")
        }

        return ScanState.DiscursivaNaoCorrigivel(
            aluno = alunos.single(),
            gabarito = when (outcome) {
                is FrameOutcome.Read -> "lido"
                is FrameOutcome.NotRead -> "nao lido: ${outcome.reason}"
                is FrameOutcome.Unreadable -> "nao lido: ${outcome.reason}"
                is FrameOutcome.SoDiscursivas, is FrameOutcome.NoSheet -> null
            },
            discursivas = reconhecidas.map { it.questionId },
            discursivasNaoLidas = naoLidas.map { "${it.questionId}: ${it.reason}" },
        )
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

/**
 * Uma apuracao que ainda nao foi gravada.
 *
 * Leva a leitura junto da nota porque quem grava precisa do **token do QR**, que esta no payload da
 * leitura e nao na nota: a nota diz de qual pacote e de qual variante ela e, e a leitura diz de
 * quem e a folha. Sao as duas metades da linha que sobe.
 */
data class ApuracaoNova(
    val reading: InterpretedReading,
    val score: ObjectiveScore,
)
