package com.platos.android.scan

import com.platos.android.roster.AlunoDoRoster
import com.platos.android.roster.RosterDaProva
import com.platos.android.session.MarcaDeLeitura
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * De qual aluno e a folha, resolvido contra o roster guardado.
 *
 * **Nao confundir com `IdentidadeDaFolhaTest`**, que cobre a camada (c) de ADR-0013 — de qual
 * **prova** a folha e. Esta pressupoe aquela resolvida.
 */
class AlunoDaFolhaTest {

    private val zona = ZoneId.of("America/Sao_Paulo")
    private val puxadoEm = 1_757_000_000_000L

    private val ana = AlunoDoRoster("tok-a", "Ana Ribeiro")
    private val bruno = AlunoDoRoster("tok-b", "Bruno Alves")

    private fun roster(vararg alunos: AlunoDoRoster) = RosterDaProva(alunos.toList(), puxadoEm)

    @Test
    fun token_no_roster_vira_nome() {
        val identidade = alunoDaFolha("tok-b", roster(ana, bruno), zona)

        assertEquals(AlunoDaFolha.Nomeada::class, identidade::class)
        assertEquals("Bruno Alves", (identidade as AlunoDaFolha.Nomeada).nome)
    }

    /**
     * **Folha avulsa: token, frase propria, e a nota continua valida.**
     *
     * O que se afirma aqui e o tipo, e nao a ausencia de excecao: `ForaDoRoster` e o que faz a tela
     * apresentar o token com a explicacao, em vez de tratar a folha como ilegivel. A nota nao entra
     * nesta funcao — ela ja foi apurada por `ObjectiveScoring`, e nada aqui a toca.
     */
    @Test
    fun token_fora_do_roster_vira_o_proprio_token() {
        val identidade = alunoDaFolha("tok-z", roster(ana, bruno), zona)

        assertEquals(AlunoDaFolha.ForaDoRoster::class, identidade::class)
        assertEquals("tok-z", (identidade as AlunoDaFolha.ForaDoRoster).token)
    }

    @Test
    fun roster_vazio_nao_nomeia_ninguem() {
        val identidade = alunoDaFolha("tok-a", roster(), zona)

        assertEquals(AlunoDaFolha.ForaDoRoster::class, identidade::class)
    }

    /**
     * Roster ausente entre o gate e a leitura — vinculo revogado no meio, por exemplo.
     *
     * Apresenta o token, e **nao** estoura nem inventa nome. Sem marca, porque nao ha instante de
     * pull a apresentar: marca sem idade afirmaria menos do que se sabe, e uma idade inventada
     * afirmaria mais.
     */
    @Test
    fun sem_roster_nenhum_apresenta_o_token_sem_marca() {
        val identidade = alunoDaFolha("tok-a", null, zona)

        assertEquals(AlunoDaFolha.ForaDoRoster::class, identidade::class)
        assertNull(identidade.marca, "apareceu marca de cache sem roster de onde tirar a idade")
    }

    /**
     * **O nome vem marcado como cacheado, e a marca diz de quando.**
     *
     * A marca vem sempre que ha roster, e nao so sem rede: o que a tela apresenta aqui saiu do disco
     * por definicao — o escaneamento acontece depois do gate, sobre o que foi puxado. Marcar so
     * quando a rede cai faria a ausencia da marca significar duas coisas diferentes.
     *
     * A asercao confere que a **idade** esta la, e nao so o rotulo: marca sem idade cumpre metade do
     * requisito e parece cumprir ele inteiro na tela.
     */
    @Test
    fun o_nome_vindo_do_roster_guardado_carrega_marca_com_idade() {
        val identidade = alunoDaFolha("tok-a", roster(ana), zona)

        val marca = identidade.marca

        assertNotNull(marca, "o nome foi apresentado sem marca de dado cacheado")
        // A cadeia exata, e nao "contem o ano": tolerancia folgada e o jeito mais comum de um teste
        // de medicao nao medir nada. O instante e o fuso sao os mesmos que `MarcaDeLeituraTest` ja
        // fixa para esta data, e o literal vem de la — nao de reformatar aqui o que a producao
        // formata, que seria oraculo compartilhando codigo com o que ele julga.
        assertEquals(
            MarcaDeLeitura("SEM CONEXAO", "visto em 04/09/2025 as 12:33"),
            marca,
            "a marca nao diz de quando o roster e, ou diz errado",
        )
    }

    /**
     * **A negativa: o resultado nao guarda o nome.**
     *
     * Medida na **estrutura do que e guardado**, e nao na tela. Uma asercao sobre a tela passaria com
     * o nome gravado dentro de [ScanState.Scored] — e e justamente a segunda copia de dado pessoal
     * que esta fatia nao pode criar: uma copia fora da lista que "Sair apaga" enumera, com o
     * apagamento passando a depender de alguem lembrar de enumera-la.
     *
     * **Varredura dos campos, e nao conferencia de um campo chamado `nome`**: conferir pelo nome
     * deixaria passar `aluno`, `displayName` ou qualquer outro rotulo que alguem escolhesse. E a
     * mesma forma da negativa de minimizacao do roster guardado, um nivel acima.
     *
     * `$stable` e filtrado porque e campo gerado pelo compilador do Compose, e nao propriedade
     * declarada — medido com uma sonda antes de esta asercao ser escrita, e nao suposto.
     */
    @Test
    fun o_resultado_apurado_nao_carrega_nome_de_aluno() {
        val declarados = ScanState.Scored::class.java.declaredFields
            .filterNot { it.isSynthetic || it.name.startsWith("$") }
            .map { it.name }
            .toSet()

        // Guarda de vacuidade (P13): sem campo algum, a varredura nao exerce protecao nenhuma.
        assertTrue(declarados.isNotEmpty(), "a varredura rodaria sobre nenhum campo e passaria a toa")

        assertEquals(
            setOf("reading", "score"),
            declarados,
            "o resultado apurado ganhou campo novo; se ele carrega nome de aluno, esta e a segunda " +
                "copia de dado pessoal no aparelho, fora da lista que sair e a revogacao apagam: " +
                declarados,
        )
    }
}
