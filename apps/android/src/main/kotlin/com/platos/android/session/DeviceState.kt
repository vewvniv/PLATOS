package com.platos.android.session

/** Uma organizacao do usuario, como a tela precisa dela: identificador e nome. */
data class Organizacao(val id: String, val nome: String)

/** Por que o aplicativo esta na entrada. `null` e a primeira abertura, que nao precisa de motivo. */
enum class MotivoDeEntrada {
    CREDENCIAL_RECUSADA,
    SEM_REDE,
    SESSAO_EXPIRADA,
    SAIU,
}

/** Por que a consulta das organizacoes nao fechou. */
enum class FalhaDaConsulta {
    SEM_REDE,
    OUTRA,
}

/**
 * O que a tela desenha. Cinco estados, e nenhum deles e ausencia de estado.
 *
 * ```
 * Entrada(motivo?) --autentica--> Consultando --+--> Ativa(organizacao)
 *      ^                               |        |
 *      |                               |        +--> Escolhendo(organizacoes)
 *      |                               |
 *      +---sessao expirada, sair-------+--> SemOrganizacao(falha)
 * ```
 *
 * [SemOrganizacao] existe porque autenticar e saber a organizacao sao dois passos, e o segundo pode
 * falhar sozinho. Sem esse estado a tela teria de escolher entre voltar para a entrada — mentindo
 * que a credencial e o problema — ou apresentar a tela de trabalho sem nome nenhum.
 */
sealed interface DeviceState {

    /** Ninguem entrou. [motivo] diz por que, quando a entrada e consequencia de alguma coisa. */
    data class Entrada(val motivo: MotivoDeEntrada? = null) : DeviceState

    /** Autenticado, e as organizacoes ainda nao chegaram. */
    data object Consultando : DeviceState

    /** Mais de uma organizacao, e nenhuma escolhida. A escolha e de quem segura o aparelho. */
    data class Escolhendo(val organizacoes: List<Organizacao>) : DeviceState

    /**
     * Autenticado, com organizacao ativa. [organizacao] veio da API.
     *
     * **O nome nunca e construido aqui.** Ele e o que a consulta devolveu, e quando ela nao devolve
     * o estado e [SemOrganizacao] — nao esta com nome de reserva.
     */
    data class Ativa(val organizacao: Organizacao) : DeviceState

    /** Autenticado e sem saber a organizacao. A tela explica, e nao apresenta nome nenhum. */
    data class SemOrganizacao(val falha: FalhaDaConsulta) : DeviceState
}
