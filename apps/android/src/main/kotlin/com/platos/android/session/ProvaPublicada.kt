package com.platos.android.session

/**
 * Uma prova publicada, como a tela e o preparo precisam dela.
 *
 * **`contentHash` nao e detalhe de transporte vazando para o dominio da tela.** E ele que permite
 * decidir, antes de pedir o pacote, se o aparelho ja tem o conteudo: ADR-0009 fixa prova<->pacote em
 * 1:1 e imutavel, entao hash igual e conteudo igual. Sem ele todo pull seria feito para descobrir
 * que era desnecessario, e a fatia inteira existe para o caso sem rede.
 *
 * Espelha `ProvaDto`, no mesmo padrao de [Organizacao] e `OrganizacaoDto`: a fronteira entre o
 * idioma do contrato e o do aplicativo fica num lugar so.
 */
data class ProvaPublicada(
    val shortId: String,
    val titulo: String,
    val contentHash: String,
)
