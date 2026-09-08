package com.platos.android.pacote

import com.platos.domain.exam.ExamPackage

/**
 * O que o aparelho guarda de pacote, por organizacao.
 *
 * **Tres verbos, nomeados um a um, e nao um `apagarTudo`.** E a mesma escolha de `SessaoGuardada`, e
 * pela mesma razao: com o apagamento nomeado, um teste de JVM afirma que **sair apaga o cache**;
 * escondido dentro de uma limpeza generica, nenhum teste desta camada o alcanca. A fatia 4a-zero
 * pagou esse defeito uma vez, um nivel acima, e o registro dela diz para nao repetir.
 */
interface PacotesGuardados {

    /**
     * Guarda os bytes de um pacote sob o hash do seu conteudo, dentro do escopo da organizacao.
     *
     * A gravacao e atomica: nao existe instante em que o conteudo guardado sob um hash esteja pela
     * metade. Interrupcao deixa o aparelho como se ela nao tivesse comecado.
     */
    fun guardar(organizacao: String, hash: String, bytes: ByteArray)

    /**
     * O pacote guardado sob aquele hash, ou `null` quando nao ha o que devolver.
     *
     * **Reconfere sempre.** O hash e recalculado sobre os bytes lidos, e nunca presumido do nome do
     * arquivo: sem isso a conferencia vale uma vez, na gravacao, e todo uso seguinte e de um arquivo
     * que ninguem mais olhou — corrupcao em repouso, gravacao truncada por queda de energia e
     * adulteracao local passariam caladas.
     *
     * Conteudo que falhe a conferencia e **descartado**, e a leitura devolve ausencia. E o que faz o
     * chamador recair no pull em vez de escanear com o que nao confere.
     */
    fun ler(organizacao: String, hash: String): ExamPackage?

    /**
     * Apaga tudo o que estiver guardado sob uma organizacao.
     *
     * Chamado ao sair. O aparelho e compartilhado entre escolas, e o que o usuario anterior baixou
     * sobrevivendo a troca de conta e invisivel para quem entra depois.
     */
    fun apagarDaOrganizacao(organizacao: String)
}
