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
 * [pendentes] so muda o texto de [MotivoDeEntrada.SAIU], e o motivo e que so ele vem de uma sessao
 * que pode ter apurado alguma coisa. **A frase diz que o trabalho continua no aparelho**, e nao que
 * ele foi descartado: sair preserva o pendente, e uma mensagem que sugerisse perda faria o professor
 * procurar a correcao onde ela nao esta.
 *
 * `null` e a primeira abertura, e nao tem motivo — a tela nao mostra faixa nenhuma. Nao e o mesmo
 * que texto vazio: texto vazio desenharia uma faixa em branco, que le como defeito.
 */
fun mensagemDeEntrada(motivo: MotivoDeEntrada?, pendentes: Int = 0): String? = when (motivo) {
    null -> null
    MotivoDeEntrada.CREDENCIAL_RECUSADA ->
        "E-mail ou senha nao conferem. Confira os dois e tente de novo."
    MotivoDeEntrada.SEM_REDE ->
        "Nao foi possivel falar com o servidor. Confira a conexao e tente de novo."
    MotivoDeEntrada.SESSAO_EXPIRADA ->
        "Sua sessao expirou. Entre de novo para continuar."
    MotivoDeEntrada.SAIU -> when {
        pendentes <= 0 -> "Voce saiu. Entre para continuar."
        pendentes == 1 ->
            "Voce saiu. 1 correcao ainda nao foi enviada, e ela continua guardada neste aparelho."
        else ->
            "Voce saiu. $pendentes correcoes ainda nao foram enviadas, e elas continuam " +
                "guardadas neste aparelho."
    }
}
