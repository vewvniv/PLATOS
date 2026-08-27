package com.platos.api.db

import com.platos.api.support.PostgresSupport
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Toda tabela de `public` declara finalidade e classe de retencao (ADR-0012, ADR-0006).
 *
 * O ADR-0006 recusou fazer disto uma invariante porque "nao e verificavel por teste". A parte
 * verificavel e a **declaracao**; a parte que continua nao sendo e se a finalidade declarada
 * corresponde ao uso real. Esta guarda cobre a primeira e nao promete a segunda.
 *
 * Vale para **todas** as tabelas, e nao so para as com dado pessoal: exigencia que dependa de
 * alguem manter a lista de quais tem deixaria a tabela nova de fora — que e exatamente o defeito
 * que a guarda de RLS ja teve, e cujo registro esta em `ConnectionRoleTest`. Por isso uma das
 * classes admitidas e `nenhum`, e quem cria tabela e obrigado a dizer conscientemente em qual caso
 * esta.
 */
class RetentionDeclarationTest {

    @BeforeTest
    fun setUp() {
        PostgresSupport.start()
    }

    /**
     * As violacoes de um conjunto de tabelas, dado o comentario de catalogo de cada uma.
     *
     * Separada da consulta de proposito: e o que permite exercitar a **logica** contra entradas
     * sinteticas — tabela sem comentario, classe inexistente, finalidade vazia — sem sujar o schema
     * de teste com tabelas que a guarda de RLS veria pela frente.
     */
    private fun violacoes(tabelas: Map<String, String?>): List<String> {
        val problemas = mutableListOf<String>()
        for ((tabela, comentario) in tabelas.entries.sortedBy { it.key }) {
            if (comentario.isNullOrBlank()) {
                problemas += "$tabela sem comentario: falta finalidade e classe de retencao"
                continue
            }
            val marcador = MARCADOR.find(comentario)
            if (marcador == null) {
                problemas += "$tabela sem `[retencao:...]`: a classe nao foi declarada"
                continue
            }
            val classe = marcador.groupValues[1]
            if (classe !in CLASSES) {
                problemas += "$tabela declara classe `$classe`, que nao existe; admitidas: " +
                    CLASSES.sorted().joinToString(", ")
            }
            val finalidade = comentario.replace(MARCADOR, "").trim()
            if (finalidade.length < FINALIDADE_MINIMA) {
                problemas += "$tabela declara a classe mas nao a finalidade"
            }
        }
        return problemas
    }

    private fun comentariosDoCatalogo(): Map<String, String?> {
        val tabelas = mutableMapOf<String, String?>()
        PostgresSupport.asAdmin { conexao ->
            conexao.createStatement().use { comando ->
                comando.executeQuery(
                    """
                    select c.relname, obj_description(c.oid, 'pg_class') as comentario
                    from pg_class c
                    join pg_namespace n on n.oid = c.relnamespace
                    where n.nspname = 'public'
                      and c.relkind = 'r'
                    order by c.relname
                    """.trimIndent(),
                ).use { linhas ->
                    while (linhas.next()) {
                        tabelas[linhas.getString("relname")] = linhas.getString("comentario")
                    }
                }
            }
        }
        return tabelas
    }

    @Test
    fun `toda tabela de public declara finalidade e classe de retencao`() {
        val tabelas = comentariosDoCatalogo()

        // Sem este piso a consulta poderia voltar vazia — por schema errado ou migration nao
        // aplicada — e o teste passaria sem ter olhado nada. E a mesma armadilha que a guarda de
        // RLS documenta ter tido.
        assertTrue(
            tabelas.size >= TABELAS_MINIMAS,
            "esperava ao menos $TABELAS_MINIMAS tabelas, vi ${tabelas.size}",
        )
        assertEquals(emptyList(), violacoes(tabelas), "tabela sem finalidade ou classe declarada")
    }

    @Test
    fun `tabela sem comentario e acusada pelo nome`() {
        val problemas = violacoes(mapOf("tabela_nova" to null))

        assertEquals(1, problemas.size)
        assertTrue(problemas.single().contains("tabela_nova"), problemas.single())
        assertTrue(problemas.single().contains("finalidade"), problemas.single())
    }

    @Test
    fun `classe que a politica nao define e acusada, com as admitidas na mensagem`() {
        val problemas = violacoes(
            mapOf("tabela_nova" to "Alguma finalidade declarada aqui. [retencao:Z]"),
        )

        assertEquals(1, problemas.size)
        assertTrue(problemas.single().contains("`Z`"), problemas.single())
        assertTrue(problemas.single().contains("nenhum"), "as admitidas precisam aparecer: ${problemas.single()}")
    }

    @Test
    fun `classe declarada sem finalidade e acusada`() {
        // Metade da declaracao nao e declaracao: "[retencao:C]" sozinho diz por quanto tempo, e nao
        // para que — e e o "para que" que decide se o prazo faz sentido.
        val problemas = violacoes(mapOf("tabela_nova" to "[retencao:C]"))

        assertEquals(1, problemas.size)
        assertTrue(problemas.single().contains("finalidade"), problemas.single())
    }

    @Test
    fun `tabela sem dado pessoal declara nenhum e passa`() {
        val problemas = violacoes(
            mapOf("tabela_nova" to "Tabela de apoio sem dado pessoal nenhum. [retencao:nenhum]"),
        )

        assertEquals(emptyList(), problemas)
    }

    @Test
    fun `a guarda reage a uma declaracao removida`() {
        // O par: a mesma tabela passa com a declaracao e reprova sem ela. Sem este teste, a guarda
        // poderia estar sempre devolvendo lista vazia e ninguem notaria.
        val comDeclaracao = "Identificacao do aluno para correcao. [retencao:C]"

        assertEquals(emptyList(), violacoes(mapOf("exam_roster" to comDeclaracao)))
        assertEquals(1, violacoes(mapOf("exam_roster" to null)).size)
    }

    @Test
    fun `o piso reprova um catalogo vazio`() {
        // Se a consulta um dia apontar para o schema errado, ela volta vazia e `violacoes` devolve
        // lista vazia — verde por nao ter olhado nada. O piso e o que separa "nada errado" de
        // "nada visto", e este teste prova que ele separa.
        val vazio = emptyMap<String, String?>()

        assertEquals(emptyList(), violacoes(vazio), "catalogo vazio nao tem violacao a apontar")
        assertTrue(vazio.size < TABELAS_MINIMAS, "e por isso a contagem e verificada a parte")
    }

    private companion object {
        val MARCADOR = Regex("""\[retencao:([A-Za-z]+)]""")

        /** As classes do item 10 da politica de privacidade, mais `nenhum`. */
        val CLASSES = setOf("A", "B", "C", "D", "E", "F", "G", "H", "nenhum")

        /** Uma frase curta ainda e finalidade; uma palavra solta nao e. */
        const val FINALIDADE_MINIMA = 20

        /** As oito tabelas que existem hoje. Piso, e nao igualdade: tabela nova nao pode reprovar. */
        const val TABELAS_MINIMAS = 8
    }
}
