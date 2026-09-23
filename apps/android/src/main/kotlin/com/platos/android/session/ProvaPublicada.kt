package com.platos.android.session

/**
 * Uma prova publicada, como a tela e o preparo precisam dela.
 *
 * **`contentHash` nao e detalhe de transporte vazando para o dominio da tela.** E ele que permite
 * decidir, antes de pedir o pacote, se o aparelho ja tem o conteudo: ADR-0009 fixa prova<->pacote em
 * 1:1 e imutavel, entao hash igual e conteudo igual. Sem ele todo pull seria feito para descobrir
 * que era desnecessario, e a fatia inteira existe para o caso sem rede.
 *
 * Traduz `ExamSummaryDto`, no mesmo padrao de [Organizacao]: a fronteira entre o idioma do
 * contrato e o do aplicativo fica num lugar so.
 *
 * **A palavra era "espelha", e o ADR-0015 a tornou falsa.** O contrato deixou de ser digitado duas
 * vezes: ele tem um declarante, em `com.platos.domain.transport`, e o que existe aqui e traducao
 * para o vocabulario da tela — que continua sendo do aparelho, e so dele.
 */
data class ProvaPublicada(
    val shortId: String,
    val titulo: String,
    val contentHash: String,
)
