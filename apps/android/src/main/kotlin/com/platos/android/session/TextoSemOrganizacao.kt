package com.platos.android.session

/**
 * Tudo o que a tela de "sem organizacao" pode dizer.
 *
 * **Nao ha campo de nome, e isso e a garantia principal.** O requisito e que nenhum nome seja
 * apresentado quando a consulta nao devolveu — e a forma mais barata de garantir isso e nao haver
 * nome para apresentar. `SemOrganizacaoScreen` recebe este tipo pronto, entao ela nao computa nada
 * e nao tem de onde tirar um nome de reserva.
 */
data class TextoSemOrganizacao(
    val titulo: String,
    val explicacao: String,
)

/**
 * O que dizer quando a consulta das organizacoes nao fechou.
 *
 * O titulo **nao varia com a falha**, de proposito: ele diz o que aconteceu do ponto de vista de
 * quem esta olhando — nao sabemos a organizacao —, e isso e verdade nos dois casos. Quem varia e a
 * explicacao, porque a acao de quem le muda: sem rede se tenta de novo, e o resto nao se resolve
 * tentando.
 *
 * `when` exaustivo sem `else`, como em [mensagemDeEntrada]: falha nova quebra a compilacao aqui em
 * vez de cair numa frase generica que nao diz o que houve.
 */
fun textoSemOrganizacao(falha: FalhaDaConsulta): TextoSemOrganizacao = TextoSemOrganizacao(
    titulo = "Nao foi possivel obter sua organizacao",
    explicacao = when (falha) {
        FalhaDaConsulta.SEM_REDE ->
            "Nao foi possivel falar com o servidor. Confira a conexao e tente de novo."
        FalhaDaConsulta.OUTRA ->
            "O servidor respondeu, mas nao foi possivel usar a resposta. Tente de novo; se " +
                "continuar, saia e entre novamente."
    },
)
