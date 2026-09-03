package com.platos.android.session

/**
 * O que a tela de entrada diz, por motivo.
 *
 * **Sai da tela de proposito.** O requisito e que credencial recusada, ausencia de rede e sessao
 * expirada sejam tres coisas distintas para quem le — e isso e afirmavel na JVM, enquanto "a faixa
 * aparece" so se afirma com aparelho ou Robolectric. A tela desenha o que esta funcao devolve, e
 * nao decide nada.
 *
 * O `when` e exaustivo sem `else`: motivo novo no enum quebra a compilacao aqui, em vez de cair
 * numa mensagem generica que nao diz o que aconteceu. E a garantia mais barata desta fatia.
 *
 * `null` e a primeira abertura, e nao tem motivo — a tela nao mostra faixa nenhuma. Nao e o mesmo
 * que texto vazio: texto vazio desenharia uma faixa em branco, que le como defeito.
 */
fun mensagemDeEntrada(motivo: MotivoDeEntrada?): String? = when (motivo) {
    null -> null
    MotivoDeEntrada.CREDENCIAL_RECUSADA ->
        "E-mail ou senha nao conferem. Confira os dois e tente de novo."
    MotivoDeEntrada.SEM_REDE ->
        "Nao foi possivel falar com o servidor. Confira a conexao e tente de novo."
    MotivoDeEntrada.SESSAO_EXPIRADA ->
        "Sua sessao expirou. Entre de novo para continuar."
    MotivoDeEntrada.SAIU ->
        "Voce saiu. Entre para continuar."
}
