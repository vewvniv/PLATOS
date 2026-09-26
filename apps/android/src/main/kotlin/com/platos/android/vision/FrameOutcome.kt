package com.platos.android.vision

import com.platos.domain.capture.CapturePayload
import com.platos.domain.capture.InterpretedReading

/**
 * O que saiu da analise de um quadro, com o **estagio em que o pipeline parou** preservado.
 *
 * `OmrReading.Rejected` achata numa frase so tudo que falha: ArUco nao achado, QR nao decodificado,
 * medicao recusada, corredor que exclui o limiar. Para o dominio isso basta — folha ilegivel e
 * folha ilegivel, e a frase serve para a tela. Para quem segura o aparelho, nao: enquanto a camera
 * aponta para a mesa, uma frase de recusa piscando e ruido; depois de a folha estar enquadrada, a
 * mesma frase e a informacao que falta.
 *
 * Os quatro casos sao os estagios de §8, e nada alem disso. **Nenhum contrato do dominio muda**:
 * `OmrReading` e `InterpretationOutcome` continuam como estao, e este tipo e do aplicativo — a
 * distincao e sobre o que a tela mostra, e nao sobre o que a leitura conclui.
 *
 * O que este tipo existe para impedir e a alternativa: classificar procurando "marcador" no texto
 * da recusa. Funciona hoje, e no dia em que a frase mudar a tela passa a mentir sem que teste
 * nenhum acuse. Ver `design.md`, decisao 3.
 *
 * **Desde a `slice-5b-1-o-aparelho-reconhece-a-discursiva`, um quadro pode ter mais de uma regiao.**
 * As quatro variantes de antes continuam falando **do gabarito**, com o mesmo sentido, e passam a
 * carregar tambem o que aconteceu com cada regiao discursiva presente no quadro ([discursivas],
 * vazia na prova so objetiva). [SoDiscursivas] e o quadro que tem regiao discursiva e nao tem o
 * gabarito — a pagina de uma prova com discursiva que so tem molduras.
 */
sealed interface FrameOutcome {

    /** As regioes discursivas que estavam inteiras no quadro, cada uma com o que aconteceu com ela. */
    val discursivas: List<RegiaoDiscursivaNoQuadro>

    /**
     * Nenhuma regiao do mapa tem todos os seus marcadores no quadro. Nao ha folha, ou ela nao esta
     * visivel inteira.
     *
     * E o estado normal de quem ainda esta enquadrando, e nao uma falha a ser mostrada.
     */
    data class NoSheet(val reason: String) : FrameOutcome {
        override val discursivas: List<RegiaoDiscursivaNoQuadro> get() = emptyList()
    }

    /**
     * A folha foi achada e o pipeline parou depois disso: QR ilegivel, medicao recusada.
     *
     * Transitorio de proposito — o quadro seguinte pode fechar, com a mesma folha na frente. Quem
     * chama continua analisando.
     */
    data class NotRead(
        val reason: String,
        override val discursivas: List<RegiaoDiscursivaNoQuadro> = emptyList(),
    ) : FrameOutcome

    /**
     * A folha foi lida e a interpretacao a recusou: corredor que exclui o limiar (ADR-0010),
     * medicao repetida, conjunto de bolhas divergente.
     *
     * **Nao e transitorio.** Repetir a foto da o mesmo resultado, porque a recusa e sobre o par
     * folha-leitor e nao sobre o quadro. Insistir com a camera nao resolve, e a tela precisa dizer
     * isso em vez de deixar a pessoa tentando.
     */
    data class Unreadable(
        val reason: String,
        override val discursivas: List<RegiaoDiscursivaNoQuadro> = emptyList(),
    ) : FrameOutcome

    /** A folha fechou: identidade, medicao e interpretacao. */
    data class Read(
        val reading: InterpretedReading,
        override val discursivas: List<RegiaoDiscursivaNoQuadro> = emptyList(),
    ) : FrameOutcome

    /** O quadro tem regiao discursiva inteira e nao tem o gabarito. Nunca vazio. */
    data class SoDiscursivas(
        override val discursivas: List<RegiaoDiscursivaNoQuadro>,
    ) : FrameOutcome {
        init {
            require(discursivas.isNotEmpty()) { "quadro so de discursivas sem discursiva nenhuma" }
        }
    }
}

/**
 * Uma regiao discursiva que estava inteira no quadro (§8).
 *
 * **Reconhecida, e nao medida:** ela nao declara bolha. O que se sabe dela e de qual folha e de qual
 * questao ela e — pelo QR, conferido contra os marcadores, e pela questao que o mapa declara.
 */
sealed interface RegiaoDiscursivaNoQuadro {
    val regionIndex: Int
    val questionId: String

    /** Retificada e com o QR lido e conferido contra os marcadores encontrados. */
    data class Reconhecida(
        override val regionIndex: Int,
        override val questionId: String,
        val payload: CapturePayload,
    ) : RegiaoDiscursivaNoQuadro

    /** Os marcadores dela estavam la, e o pipeline parou depois: geometria, QR, divergencia. */
    data class NaoLida(
        override val regionIndex: Int,
        override val questionId: String,
        val reason: String,
    ) : RegiaoDiscursivaNoQuadro
}
