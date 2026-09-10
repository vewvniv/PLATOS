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
 * De onde veio o que a tela esta apresentando.
 *
 * **Nao e detalhe de tela: e estado.** O requisito diz que todo nome apresentado veio da API, e o que
 * a ausencia de rede muda e a **idade** do nome, nunca a procedencia dele. Com a idade no estado, a
 * tela nao tem como esquecer de dizer de quando e o que mostra — e um `when` exaustivo quebra a
 * compilacao quando alguem acrescentar uma terceira origem.
 */
sealed interface Procedencia {

    /** A consulta respondeu agora. */
    data object Fresca : Procedencia

    /** Veio da visao guardada, vista em [vistaEm] — milissegundos de epoch. */
    data class Cacheada(val vistaEm: Long) : Procedencia
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
     * **O nome nunca e construido aqui**, e continua nao sendo: ele e o que a consulta devolveu — ou
     * agora, ou na ultima vez que ela respondeu, e [procedencia] diz qual dos dois. O que nao existe
     * e nome de reserva; sem consulta e sem visao guardada o estado e [SemOrganizacao].
     */
    data class Ativa(
        val organizacao: Organizacao,
        val procedencia: Procedencia,
    ) : DeviceState

    /** Autenticado e sem saber a organizacao. A tela explica, e nao apresenta nome nenhum. */
    data class SemOrganizacao(val falha: FalhaDaConsulta) : DeviceState
}
