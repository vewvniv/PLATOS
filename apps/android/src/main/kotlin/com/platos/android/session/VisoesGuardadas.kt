package com.platos.android.session

/**
 * A ultima visao conhecida de uma organizacao: o que a API devolveu sobre ela, e quando.
 *
 * **E dado derivado, nunca autoritativo.** Toda consulta bem-sucedida a substitui por inteiro, e
 * nenhum caminho de escrita a produz a nao ser a resposta da API — a mesma disciplina do cache de
 * pacotes, onde quem manda e o hash. O que ela permite e o aparelho continuar util quando o servidor
 * nao responde; o que ela nao faz e decidir nada por conta propria.
 *
 * **As duas metades vem juntas de proposito.** [organizacao] sai de `/me/organizations` e [provas]
 * de `/organizations/{id}/exams`, e a visao so e gravada quando as duas respondem na mesma sessao —
 * e o que o cenario "A visao e gravada quando a consulta responde" fixa. Guardar meia visao
 * significaria abrir sem rede numa tela que sabe o nome da organizacao e nao sabe o que escanear,
 * que e a primeira parede sem a segunda.
 *
 * [vistaEm] entra por parametro, em milissegundos de epoch, e nao e lido de um relogio aqui dentro:
 * relogio dentro da estrutura torna a idade impossivel de afirmar num teste, e a idade e o que a
 * tela precisa apresentar ao lado do nome.
 */
data class VisaoDaOrganizacao(
    val organizacao: Organizacao,
    val provas: List<ProvaPublicada>,
    val vistaEm: Long,
)

/**
 * O que o aparelho guarda de referencia mutavel, por organizacao.
 *
 * **Tres verbos, nomeados um a um**, como [SessaoGuardada] e `PacotesGuardados`, e pela mesma razao
 * registrada nos dois: com o apagamento nomeado, um teste de JVM afirma que **a revogacao leva a
 * visao junto**; escondido dentro de uma limpeza generica do adaptador, nenhum teste desta camada o
 * alcanca.
 *
 * O escopo e a organizacao, e nao o aparelho: o mesmo aparelho e compartilhado entre escolas, e uma
 * visao que atravessasse a troca de organizacao apresentaria a quem entrou depois o nome e a lista
 * de quem entrou antes.
 */
interface VisoesGuardadas {

    /** A visao daquela organizacao, ou `null` quando o aparelho nunca a viu. */
    fun ler(organizacao: String): VisaoDaOrganizacao?

    /**
     * Substitui **por inteiro** a visao da organizacao que [visao] descreve.
     *
     * Substituir, e nao emendar: uma lista de provas mesclada com a anterior apresentaria prova que
     * a organizacao ja nao publica, e prova que sumiu e indistinguivel de prova que existe para quem
     * le a tela.
     */
    fun guardar(visao: VisaoDaOrganizacao)

    /** Apaga a visao de uma organizacao. As das outras ficam. */
    fun apagarDaOrganizacao(organizacao: String)
}
