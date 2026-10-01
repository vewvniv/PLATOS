package com.platos.android.scan

import com.platos.android.vision.FrameOutcome
import com.platos.android.vision.RegiaoDiscursivaNoQuadro
import com.platos.domain.capture.CapturePayload
import com.platos.domain.capture.InterpretedReading
import com.platos.domain.exam.ExamPackage
import com.platos.domain.scoring.CorrecaoDoProfessor
import com.platos.domain.scoring.NotaDoProfessor
import com.platos.domain.scoring.NotaDoProfessorOutcome
import com.platos.domain.scoring.ObjectiveScore
import com.platos.domain.scoring.ObjectiveScoring
import com.platos.domain.scoring.PartialScore
import com.platos.domain.scoring.PartialScoringOutcome
import com.platos.domain.scoring.PontuacaoDada
import com.platos.domain.scoring.ScoringOutcome
import java.util.UUID

/**
 * A sessao de escaneamento de uma folha: quadros entram, [ScanState] sai.
 *
 * Kotlin puro. Nao conhece `Mat`, CameraX nem Compose — recebe [FrameOutcome], que e o resultado
 * ja destilado da analise, e decide o que a tela mostra. E a mesma fronteira que a fatia 3a
 * desenhou entre `vision/` e `omr/`, pela mesma razao: o que decide precisa ser testavel sem
 * aparelho.
 *
 * Nesta fatia a sessao e de **uma folha**. Lote, completude e avanco automatico sao a 3d. *Desde a
 * `slice-5b-2-a-nota-objetiva-parcial`, a completude da folha do aluno de uma prova com discursiva e
 * o [Caderno]; lote e avanco automatico continuam fora.*
 *
 * **A conferencia do `exam_short_id` nao e opcional.** Sem ela, uma folha de outra prova seria
 * apurada contra este gabarito e produziria nota plausivel e errada. `ObjectiveScoring` ja recusa
 * por conjunto de itens divergente, mas a recusa por identificador e anterior e diz a coisa certa
 * a quem segura o aparelho.
 *
 * [cadernoInicial] retoma o caderno em andamento guardado antes do fechamento do aplicativo
 * (`slice-5b-3-guardar-a-parcial-e-o-caderno`). A sessao nasce com ele exatamente como estaria se
 * nenhum quadro tivesse sido perdido — nao um caderno "revisitado" por um quadro novo.
 */
class ScanSession(
    private val examPackage: ExamPackage,
    cadernoInicial: Caderno? = null,
    /** O id da captura da parcial, cunhado na transicao que entrega o caderno (design D5); fixo nos testes. */
    private val novoId: () -> String = { UUID.randomUUID().toString() },
) {

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
            state is ScanState.ProvaComDiscursiva || state is ScanState.NotaPorDarDeOutroAluno

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

    /**
     * `granted` respeita [holdsResult] (`slice-5b-3-guardar-a-parcial-e-o-caderno`): sem isto, o
     * caderno retomado por [cadernoInicial] seria apagado no mesmo `onCreate` que o concede, antes
     * de qualquer quadro novo. Negada continua incondicional — sem camera, so a tela de permissao
     * faz sentido, held ou nao.
     */
    fun onPermission(granted: Boolean) {
        state = when {
            !granted -> ScanState.NoPermission
            holdsResult -> state
            else -> ScanState.Searching
        }
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
     *
     * [respostas] e o que o analisador gravou por regiao discursiva reconhecida
     * (`slice-5c-1-a-resposta-fica-no-aparelho`): so com resposta guardada a regiao fica capturada.
     */
    fun onFrame(outcome: FrameOutcome, respostas: Map<Int, RespostaDoQuadro> = emptyMap()): ApuracaoNova? {
        if (state is ScanState.NoPermission) return null
        if (comDiscursiva) {
            // Reconhece e mostra a parcial. So entrega quando o caderno completa nesta passada
            // (`slice-5b-4-envio-da-parcial`, design decisao 1) — o resto continua sem produzir
            // resultado: nada mais desta prova vira fila de envio neste aparelho.
            val (novoEstado, entrega) = estadoDaDiscursiva(outcome, respostas)
            state = novoEstado
            return entrega
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
        return ApuracaoNova.Completa(apuracao.reading, apuracao.score)
    }

    /**
     * O que a tela mostra de uma folha de prova com discursiva, e a entrega quando o caderno
     * completa nesta passada.
     *
     * A folha e identificada pelo QR de qualquer regiao lida no quadro — o gabarito ou uma
     * discursiva —, e a conferencia de prova e a mesma da prova objetiva: QR de outra prova e recusa,
     * com a mesma frase. Nenhuma regiao com QR lido e "achei a folha e nao consegui ler", como hoje.
     */
    private fun estadoDaDiscursiva(
        outcome: FrameOutcome,
        respostas: Map<Int, RespostaDoQuadro>,
    ): Pair<ScanState, ApuracaoNova.DeCaderno?> {
        if (outcome is FrameOutcome.NoSheet) {
            return (if (holdsResult) state else ScanState.Searching) to null
        }

        val reconhecidas = outcome.discursivas.filterIsInstance<RegiaoDiscursivaNoQuadro.Reconhecida>()
        val naoLidas = outcome.discursivas.filterIsInstance<RegiaoDiscursivaNoQuadro.NaoLida>()
        val payloads = buildList {
            if (outcome is FrameOutcome.Read) add(outcome.reading.payload)
            reconhecidas.forEach { add(it.payload) }
        }

        if (payloads.isEmpty()) {
            val semApuracao = when (outcome) {
                // A recusa da interpretacao nao e transitoria, e continua sendo recusa.
                is FrameOutcome.Unreadable -> ScanState.Rejected(outcome.reason)
                else -> {
                    val motivo = (outcome as? FrameOutcome.NotRead)?.reason
                        ?: naoLidas.firstOrNull()?.reason
                        ?: "nenhuma regiao do quadro foi lida"
                    if (holdsResult) state else ScanState.NotRead(motivo)
                }
            }
            return semApuracao to null
        }

        val carregado = examPackage.meta.examId
        payloads.firstOrNull { it.examShortId != carregado }?.let { deOutra ->
            return ScanState.Rejected(
                "a folha e de outra prova: o QR diz ${deOutra.examShortId}, e o aparelho carrega $carregado",
            ) to null
        }
        val alunos = payloads.map { it.studentToken }.distinct()
        if (alunos.size > 1) {
            return ScanState.Rejected("o quadro tem regioes de folhas diferentes: ${alunos.joinToString()}") to null
        }
        val aluno = alunos.single()

        // Caderno completo e sem nota nao e substituido em silencio (`slice-5c-3-a-nota-no-aparelho`): descartar
        // perde as respostas, e quem decide e o professor.
        val corrente = caderno
        if (corrente != null && corrente.aluno != aluno && corrente.aguardaNota) {
            return ScanState.NotaPorDarDeOutroAluno(corrente, aluno) to null
        }

        // O caderno e do aluno: a folha de outro comeca um novo, e nao herda nada dele (decisao 4). O caderno
        // **corrigido** do mesmo aluno tambem nao e reaproveitado: a folha lida de novo e captura nova.
        val anterior = caderno?.takeIf { it.aluno == aluno && !it.corrigido } ?: run {
            val variante = ObjectiveScoring.resolveVariant(examPackage, payloads.first())
            Caderno.novo(aluno, variante, variante?.let { examPackage.layout[it.variantId] })
        }

        // A parcial vem do dominio, e so do gabarito lido. Sem ele no quadro, vale a ultima do mesmo
        // aluno, que o caderno guarda (decisao 6).
        val parcialNova = if (outcome is FrameOutcome.Read) {
            ObjectiveScoring.scorePartial(examPackage, outcome.reading.payload, outcome.reading.answers)
        } else {
            null
        }
        val guardadas = mutableMapOf<Int, RespostaGuardada>()
        val vistas = vistasNoQuadro(anterior, outcome, parcialNova, respostas, guardadas)
        val atualizado = anterior.depoisDe(vistas, parcialNova, guardadas)

        // A entrega dispara na transicao de incompleto para completo, e so nela — nunca de novo a
        // cada quadro seguinte que so confirma um caderno ja completo (design decisao 1). Uma regiao
        // "capturada" nao garante, sozinha, que a parcial atual e utilizavel: "capturada nao volta
        // atras" protege o indicador da regiao, e nao o campo `parcial`, que pode ter regredido para
        // uma recusa num quadro seguinte (`Caderno.depoisDe`) — por isso a condicao exige a parcial
        // atual como `Scored`, e nao so a contagem de regioes.
        val completouAgora = !anterior.entregue &&
            atualizado.capturadas == atualizado.esperadas &&
            atualizado.parcial is PartialScoringOutcome.Scored
        val capturaDaParcial = if (completouAgora) novoId() else null
        val atual = if (completouAgora) {
            atualizado.copy(entregue = true, capturaDaParcial = capturaDaParcial)
        } else {
            atualizado
        }
        caderno = atual

        val entrega = if (completouAgora) {
            ApuracaoNova.DeCaderno(
                aluno,
                (atual.parcial as PartialScoringOutcome.Scored).partial,
                requireNotNull(capturaDaParcial),
            )
        } else {
            null
        }

        val novoEstado = ScanState.ProvaComDiscursiva(
            aluno = aluno,
            gabarito = when (outcome) {
                is FrameOutcome.Read -> "lido"
                is FrameOutcome.NotRead -> "nao lido: ${outcome.reason}"
                is FrameOutcome.Unreadable -> "nao lido: ${outcome.reason}"
                is FrameOutcome.SoDiscursivas, is FrameOutcome.NoSheet -> null
            },
            discursivas = reconhecidas.map { it.questionId },
            discursivasNaoLidas = naoLidas.map { "${it.questionId}: ${it.reason}" },
            caderno = atual,
        )
        return novoEstado to entrega
    }

    /**
     * O que um quadro diz de cada regiao presente nele, por indice de regiao.
     *
     * O gabarito e a regiao do mapa que nao e discursiva, pela mesma regra de `SheetReader.analyze`.
     * Lido e com a parcial apurada, ele e capturado; lido e com a parcial recusada, ou presente e nao
     * lido, e "com problema", com o motivo (decisao 6). Fora do quadro, nao entra.
     */
    private fun vistasNoQuadro(
        caderno: Caderno,
        outcome: FrameOutcome,
        parcialNova: PartialScoringOutcome?,
        respostas: Map<Int, RespostaDoQuadro>,
        guardadas: MutableMap<Int, RespostaGuardada>,
    ): Map<Int, EstadoDaRegiao> {
        val gabarito = caderno.regioes.firstOrNull { it.gabarito }?.regionIndex
        return buildMap {
            if (gabarito != null) {
                when (outcome) {
                    is FrameOutcome.Read -> put(
                        gabarito,
                        when (parcialNova) {
                            is PartialScoringOutcome.Rejected -> EstadoDaRegiao.ComProblema(parcialNova.reason)
                            else -> EstadoDaRegiao.Capturada
                        },
                    )
                    is FrameOutcome.NotRead -> put(gabarito, EstadoDaRegiao.ComProblema(outcome.reason))
                    is FrameOutcome.Unreadable -> put(gabarito, EstadoDaRegiao.ComProblema(outcome.reason))
                    is FrameOutcome.SoDiscursivas, is FrameOutcome.NoSheet -> Unit
                }
            }
            for (regiao in outcome.discursivas) {
                when (regiao) {
                    is RegiaoDiscursivaNoQuadro.Reconhecida ->
                        discursivaReconhecida(caderno, regiao.regionIndex, respostas[regiao.regionIndex], guardadas)
                            ?.let { put(regiao.regionIndex, it) }
                    is RegiaoDiscursivaNoQuadro.NaoLida ->
                        put(regiao.regionIndex, EstadoDaRegiao.ComProblema(regiao.reason))
                }
            }
        }
    }

    /**
     * O que um quadro diz de uma regiao discursiva **reconhecida**, dada a resposta que o analisador
     * entregou para ela (`slice-5c-1-a-resposta-fica-no-aparelho`, design, decisao 3). Reconhecer, sozinho,
     * nao captura: capturada exige resposta guardada.
     *
     * Devolve o estado novo da regiao, ou nulo quando ela fica como estava. A ausencia de entrada para
     * uma regiao sem resposta e "com problema", e nunca capturada em silencio: se a fiacao do analisador
     * se perder, o professor ve o problema, e nao uma regiao capturada sem imagem.
     */
    private fun discursivaReconhecida(
        caderno: Caderno,
        regionIndex: Int,
        entrega: RespostaDoQuadro?,
        guardadas: MutableMap<Int, RespostaGuardada>,
    ): EstadoDaRegiao? {
        val jaTemResposta = caderno.regioes.firstOrNull { it.regionIndex == regionIndex }?.resposta != null
        return when (entrega) {
            // A primeira resposta aceita fica; o arquivo novo e um orfao da eliminacao.
            is RespostaDoQuadro.Guardada -> if (jaTemResposta) {
                null
            } else {
                guardadas[regionIndex] = entrega.resposta
                EstadoDaRegiao.Capturada
            }
            is RespostaDoQuadro.Recusada -> EstadoDaRegiao.ComProblema(entrega.motivo)
            // O analisador nao pediu porque ja havia resposta (decisao 1), ou nao pediu por defeito dele.
            null -> if (jaTemResposta) null else EstadoDaRegiao.ComProblema(RECORTE_NAO_PEDIDO)
        }
    }

    /**
     * Refaz a resposta de uma regiao: a resposta e descartada e a regiao volta a nao vista, de modo que
     * a proxima captura peca o recorte de novo. Devolve o arquivo a eliminar — quem elimina e quem
     * chama, porque a sessao nao toca disco —, ou a recusa com o motivo, sem efeito.
     *
     * As outras regioes, a parcial e a marca de entrega nao mudam: refazer a resposta de um caderno ja
     * entregue **nao** o entrega de novo.
     */
    fun refazer(regionIndex: Int): ResultadoDoRefazer {
        val atual = caderno ?: return ResultadoDoRefazer.Recusado("nao ha caderno em andamento")
        val regiao = atual.regioes.firstOrNull { it.regionIndex == regionIndex }
            ?: return ResultadoDoRefazer.Recusado("a regiao $regionIndex nao existe no caderno")
        val resposta = regiao.resposta
            ?: return ResultadoDoRefazer.Recusado("a regiao $regionIndex nao tem resposta guardada")

        val novo = atual.semResposta(regionIndex)
        caderno = novo
        (state as? ScanState.ProvaComDiscursiva)?.let { state = it.copy(caderno = novo) }
        return ResultadoDoRefazer.Refeita(resposta.arquivo)
    }

    /** Se uma nota ja foi decidida e a gravacao dela ainda nao terminou: o duplo toque nao gera duas notas. */
    private var notaEmCurso = false

    /**
     * Decide a nota do professor sobre o caderno **corrente** — o da tela, ou o que aguarda nota quando outro aluno
     * apareceu. **Nao muda o caderno**: a gravacao pode falhar, e a spec manda deixar a tela aberta com o motivo e
     * nada no caderno. Quem grava chama [confirmarCorrigido] depois, ou [falhouAGravacao].
     */
    fun darNota(pontuacoes: List<PontuacaoDada>): ResultadoDaNota {
        val atual = caderno ?: return ResultadoDaNota.Recusada("nao ha caderno em andamento")
        if (atual.corrigido) return ResultadoDaNota.Recusada("o caderno ja foi corrigido")
        if (notaEmCurso) return ResultadoDaNota.Recusada("a nota ja esta sendo gravada")
        if (!atual.aguardaNota) return ResultadoDaNota.Recusada("o caderno ainda nao esta completo")
        val parcial = (atual.parcial as? PartialScoringOutcome.Scored)?.partial
            ?: return ResultadoDaNota.Recusada("a parcial do caderno nao foi apurada")
        val captura = requireNotNull(atual.capturaDaParcial) // `aguardaNota` ja exige

        return when (val r = CorrecaoDoProfessor.completar(parcial, pontuacoes)) {
            is NotaDoProfessorOutcome.Rejected -> ResultadoDaNota.Recusada(r.reason)
            is NotaDoProfessorOutcome.Scored -> {
                notaEmCurso = true
                ResultadoDaNota.Corrigida(r.nota, captura, atual.aluno, atual.copy(corrigido = true))
            }
        }
    }

    /** A nota foi gravada: o caderno passa a corrigido, e quem estava segurando a troca de aluno a libera. */
    fun confirmarCorrigido() {
        val atual = caderno ?: return
        notaEmCurso = false
        val novo = atual.copy(corrigido = true)
        caderno = novo
        state = when (val s = state) {
            is ScanState.NotaPorDarDeOutroAluno -> ScanState.Searching
            is ScanState.ProvaComDiscursiva -> s.copy(caderno = novo)
            else -> s
        }
    }

    /** A gravacao da nota falhou: nada mudou, e o professor pode tentar de novo. */
    fun falhouAGravacao() {
        notaEmCurso = false
    }

    /**
     * Descarta o caderno completo sem nota e volta a procurar. Devolve os arquivos a eliminar — quem elimina e quem
     * chama, porque a sessao nao toca disco. Nada e gravado nem enviado.
     */
    fun descartarESeguir(): List<String> {
        val arquivos = caderno?.regioes?.mapNotNull { it.resposta?.arquivo }.orEmpty()
        caderno = null
        notaEmCurso = false
        state = ScanState.Searching
        return arquivos
    }

    /**
     * O caderno do aluno corrente (§8), em memoria e nunca gravado.
     *
     * **Memoria entre quadros, e de proposito**: a outra pagina do mesmo aluno nao traz o gabarito, e
     * nem o que ja foi capturado nem a parcial dele podem sumir por isso. E a excecao declarada a "um
     * resultado novo substitui o anterior por inteiro", e ela e por aluno: a folha de outro aluno
     * comeca outro caderno. [resume] nao o limpa: voltar a procurar nao muda de quem e a folha.
     */
    private var caderno: Caderno? = null

    /**
     * O caderno em andamento, para quem guarda o estado ao sair de primeiro plano
     * (`slice-5b-3-guardar-a-parcial-e-o-caderno`, design decisao 1). Nulo antes do primeiro quadro
     * reconhecido, ou numa prova so objetiva.
     */
    val cadernoAtual: Caderno? get() = caderno

    init {
        // So retoma numa prova com discursiva: o caderno e conceito dela, e um cadernoInicial
        // recebido para uma prova so objetiva seria dado de outra prova, ou obsoleto.
        if (comDiscursiva && cadernoInicial != null) {
            caderno = cadernoInicial
            // Nenhum quadro foi analisado ainda nesta instancia: os campos "deste quadro" comecam
            // vazios, e o caderno guardado e quem carrega o que ja foi visto (ScanScreen so desenha
            // aluno, parcial e caderno; gabarito/discursivas/discursivasNaoLidas nao aparecem na
            // tela).
            state = ScanState.ProvaComDiscursiva(
                aluno = cadernoInicial.aluno,
                gabarito = null,
                discursivas = emptyList(),
                discursivasNaoLidas = emptyList(),
                caderno = cadernoInicial,
            )
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

/** O motivo de uma regiao reconhecida sem resposta e sem recusa: o analisador nao pediu o recorte. */
internal const val RECORTE_NAO_PEDIDO = "o recorte nao foi pedido"

/** O que [ScanSession.darNota] decidiu. */
sealed interface ResultadoDaNota {

    /** A nota foi aceita; [cadernoCorrigido] e o que se grava no Room junto do pendente. */
    data class Corrigida(
        val nota: NotaDoProfessor,
        val completaCaptura: String,
        val aluno: String,
        val cadernoCorrigido: Caderno,
    ) : ResultadoDaNota

    /** Nada mudou; [motivo] serve para a tela. */
    data class Recusada(val motivo: String) : ResultadoDaNota
}

/** O que [ScanSession.refazer] fez. */
sealed interface ResultadoDoRefazer {

    /** A resposta foi descartada; [arquivo] e o que quem chama deve eliminar do disco. */
    data class Refeita(val arquivo: String) : ResultadoDoRefazer

    /** Nada mudou. */
    data class Recusado(val motivo: String) : ResultadoDoRefazer
}

/**
 * Uma apuracao que ainda nao foi gravada — de uma prova so objetiva, ou do caderno completo de
 * uma prova com discursiva (`slice-5b-4-envio-da-parcial`).
 */
sealed interface ApuracaoNova {

    /**
     * Leva a leitura junto da nota porque quem grava precisa do **token do QR**, que esta no
     * payload da leitura e nao na nota: a nota diz de qual pacote e de qual variante ela e, e a
     * leitura diz de quem e a folha. Sao as duas metades da linha que sobe.
     */
    data class Completa(
        val reading: InterpretedReading,
        val score: ObjectiveScore,
    ) : ApuracaoNova

    /**
     * O caderno completo de um aluno, de uma prova com discursiva. [aluno] e o token do QR — vazio
     * na folha avulsa —, na mesma forma que [Completa] leva pela leitura.
     */
    data class DeCaderno(
        val aluno: String,
        val score: PartialScore,
        /** A captura da parcial, cunhada na transicao: e o `completes_capture_id` da nota do professor. */
        val captureId: String,
    ) : ApuracaoNova
}
