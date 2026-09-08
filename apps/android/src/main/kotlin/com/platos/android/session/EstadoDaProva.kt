package com.platos.android.session

/** Por que a listagem de provas nao fechou. Espelha [FalhaDaConsulta], e pelo mesmo motivo. */
enum class FalhaDaListagem {
    SEM_REDE,
    OUTRA,
}

/**
 * Por que o escaneamento nao pode abrir. Quatro, e cada um pede uma coisa diferente.
 *
 * A spec exige que ausencia de rede, pacote ausente, conferencia falha e versao insuficiente sejam
 * quatro estados distintos, com quatro frases distintas. Sao enum, e nao texto, porque distinguir
 * causa por mensagem foi o defeito que a fatia 4a-zero registrou: quem le a mensagem passa a
 * depender da palavra escolhida, e trocar a palavra quebra a regra sem quebrar nenhum teste.
 */
enum class MotivoDaBarragem {
    /** Nao havia pacote guardado, e o pull nao chegou ao servidor. */
    SEM_REDE,

    /** O servidor respondeu que nao ha pacote para esta prova. */
    PACOTE_AUSENTE,

    /** O pacote chegou e nao passou nas conferencias de integridade ou interpretacao. */
    CONFERENCIA_FALHOU,

    /** O pacote exige um renderizador mais novo que o deste aplicativo. */
    VERSAO_INSUFICIENTE,
}

/**
 * O que a tela do preparo desenha, entre ter organizacao ativa e abrir a camera.
 *
 * ```
 * Listando --+--> Escolhendo(provas) --escolhe--> Preparando(prova) --+--> Pronta(prova, hash)
 *            |                                                        |
 *            +--> SemProvaPublicada                                   +--> Barrada(prova, motivo)
 *            |
 *            +--> ListagemFalhou(falha)
 * ```
 *
 * [SemProvaPublicada] e [ListagemFalhou] sao estados diferentes de proposito, e nao um so com uma
 * lista vazia: "esta organizacao nao tem prova publicada" e uma afirmacao sobre o mundo, e consulta
 * que falhou nao autoriza faze-la.
 */
sealed interface EstadoDaProva {

    /** A listagem foi pedida e ainda nao chegou. */
    data object Listando : EstadoDaProva

    /** As provas chegaram, e a escolha e de quem segura o aparelho. */
    data class Escolhendo(val provas: List<ProvaPublicada>) : EstadoDaProva

    /** A organizacao ativa nao tem prova publicada. Nao e falha. */
    data object SemProvaPublicada : EstadoDaProva

    /** A listagem nao fechou. A tela explica, e nao apresenta lista nenhuma. */
    data class ListagemFalhou(val falha: FalhaDaListagem) : EstadoDaProva

    /** Uma prova foi escolhida, e o pacote esta sendo obtido — do disco ou da rede. */
    data class Preparando(val prova: ProvaPublicada) : EstadoDaProva

    /**
     * O gate passou. [contentHash] e o endereco do pacote conferido.
     *
     * **Carrega o endereco, e nao o pacote.** E ele que atravessa para a `ScanActivity` pelo
     * `Intent`, onde e relido e reconferido — sao ~100 KB, e uma transacao Binder desse tamanho
     * derruba o aplicativo em vez de falhar com motivo.
     */
    data class Pronta(val prova: ProvaPublicada, val contentHash: String) : EstadoDaProva

    /** O gate barrou. A camera nao abre, e [motivo] diz por que. */
    data class Barrada(val prova: ProvaPublicada, val motivo: MotivoDaBarragem) : EstadoDaProva
}
