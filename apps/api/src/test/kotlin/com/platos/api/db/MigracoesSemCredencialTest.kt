package com.platos.api.db

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Nenhuma migration versiona credencial.
 *
 * A regra e sobre texto, e por isso a verificacao e sobre texto. A migration de papeis chegou
 * a criar
 * `app_backend` com `password 'app_backend'` -- senha igual ao nome, igual em toda instalacao, e
 * publicada para quem lesse o repositorio. Num Postgres efemero de teste isso era inofensivo, e o
 * mesmo arquivo alimenta o banco de producao, onde o papel tem LOGIN e o banco atende a internet.
 *
 * O defeito nao tem sintoma: o schema fica correto, os testes ficam verdes, e o que muda e so quem
 * consegue entrar. Nenhuma assercao de comportamento o alcanca, entao a guarda tem de olhar o
 * arquivo.
 */
class MigracoesSemCredencialTest {

    private val diretorio = File(
        System.getProperty("platos.migrations.dir")
            ?: error("systemProperty platos.migrations.dir nao definida"),
    )

    /** `password '...'` e `password "..."`, em qualquer caixa. */
    private val senhaLiteral = Regex("""password\s+['"]""", RegexOption.IGNORE_CASE)

    @Test
    fun nenhumaMigrationTrazSenhaLiteral() {
        val migrations = diretorio.listFiles { f -> f.isFile && f.name.endsWith(".sql") }
            ?.sortedBy { it.name }
            .orEmpty()

        assertTrue(migrations.isNotEmpty(), "nenhuma migration encontrada em $diretorio")

        val culpadas = migrations.filter { senhaLiteral.containsMatchIn(semComentarios(it.readText())) }

        if (culpadas.isNotEmpty()) {
            fail(
                "migration nao versiona credencial, e estas trazem senha literal: " +
                    culpadas.joinToString { it.name } +
                    ". O papel e declarado na migration; a senha e dada por quem opera o banco " +
                    "(`alter role ... password`), e nos testes por PostgresSupport, sorteada.",
            )
        }
    }

    /**
     * Comentario que **explica** a regra citaria a sintaxe proibida, e a guarda acusaria o proprio
     * texto que a documenta. Tirar os comentarios antes de olhar e o que mantem as duas coisas
     * possiveis ao mesmo tempo.
     */
    private fun semComentarios(sql: String): String = sql
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("""--[^\n]*"""), " ")
}
