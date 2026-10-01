package com.platos.android.scan

/**
 * O que o aparelho guarda de resposta discursiva, em arquivo (`slice-5c-1-a-resposta-fica-no-aparelho`).
 *
 * Existe como interface pela mesma razao de [CadernosGuardados]: a normalizacao da leitura do caderno
 * e a varredura do prazo sao testaveis na JVM, sem disco. Os nomes sao os de `respostas/`, um UUID
 * cada, **sem aluno, prova nem organizacao** — o vinculo vive no caderno.
 */
interface RespostasGuardadas {

    /**
     * Grava o PNG inteiro ou nao grava nada: o arquivo so passa a existir sob o nome final depois de
     * completo, e a falha (disco cheio, E/S) devolve [RespostaDoQuadro.Recusada] sem deixar arquivo,
     * nem temporario. Nunca lanca.
     */
    fun gravar(png: ByteArray, capturadaEm: Long, desvioSinalizado: Boolean, foraPpm: Int): RespostaDoQuadro

    /** Os bytes do PNG, ou `null` quando o arquivo nao existe. */
    fun ler(arquivo: String): ByteArray?

    fun existe(arquivo: String): Boolean

    /** Todo nome em `respostas/`, **inclusive o temporario de uma gravacao interrompida**. */
    fun listar(): List<String>

    /**
     * Elimina o arquivo. Arquivo que ja nao existe conta como eliminado. Falha de E/S **lanca**: quem
     * varre decide continuar com os outros (ver [RetencaoDaResposta]).
     */
    fun eliminar(arquivo: String)
}
