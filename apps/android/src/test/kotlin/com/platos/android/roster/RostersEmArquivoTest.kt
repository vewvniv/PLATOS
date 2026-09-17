package com.platos.android.roster

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * O roster guardado, no sistema de arquivos de verdade e sem aparelho.
 *
 * `@TempDir` da um diretorio real, pela mesma razao de `PacotesEmArquivoTest` e
 * `VisoesEmArquivoTest`: um duplo em memoria verificaria a intencao do codigo, e o que precisa ser
 * verificado e o comportamento sobre arquivos.
 */
class RostersEmArquivoTest {

    @TempDir
    lateinit var raiz: File

    private val rosters: RostersEmArquivo by lazy { RostersEmArquivo(raiz) }

    private val escola = "01a06ba4-cb43-7d97-842d-165352d010b5"
    private val outra = "01a06ba4-0000-7d97-842d-165352d010b5"
    private val matematica = "mat-7a-2026-1"

    private val ana = AlunoDoRoster("tok-a", "Ana Ribeiro")
    private val bruno = AlunoDoRoster("tok-b", "Bruno Alves")
    private val carla = AlunoDoRoster("tok-c", "Carla Dias")

    private fun roster(
        alunos: List<AlunoDoRoster> = listOf(ana, bruno),
        puxadoEm: Long = 1_757_000_000_000,
    ) = RosterDaProva(alunos, puxadoEm)

    // ------------------------------------------------------------------ ida e volta

    @Test
    fun o_que_foi_gravado_e_o_que_e_lido() {
        val gravado = roster(listOf(ana, bruno, carla))

        rosters.guardar(escola, matematica, gravado)

        assertEquals(gravado, rosters.ler(escola, matematica), "o roster lido nao e o gravado")
    }

    @Test
    fun prova_nunca_puxada_le_nulo() {
        assertNull(
            rosters.ler(escola, matematica),
            "prova sem pull devolveu roster, e o gate depende dessa ausencia",
        )
    }

    /**
     * **Roster vazio e um roster, e nao ausencia.**
     *
     * E a distincao inteira do gate: "nao ha alunos" e afirmacao sobre o mundo, e "nunca puxei" nao
     * e. Se esta ida e volta devolvesse `null`, o gate barraria uma prova publicada sem alunos, que
     * `exam-package` declara caso legitimo.
     */
    @Test
    fun roster_vazio_sobrevive_a_ida_e_volta_como_vazio() {
        rosters.guardar(escola, matematica, roster(alunos = emptyList()))

        val lido = rosters.ler(escola, matematica)

        assertNotNull(lido, "roster vazio foi lido como prova nunca puxada")
        assertTrue(lido!!.alunos.isEmpty(), "roster vazio voltou com aluno")
    }

    // ------------------------------------------------------------------ a atomicidade

    @Test
    fun parcial_deixado_para_tras_nao_e_confundido_com_roster() {
        val pasta = File(raiz, escola).apply { mkdirs() }
        File(pasta, matematica + ".json.parcial-12345").writeText("{\"puxado_em\":1,\"alunos\":[]}")

        assertNull(
            rosters.ler(escola, matematica),
            "um parcial foi lido como se fosse o roster",
        )
    }

    @Test
    fun a_gravacao_nao_deixa_parcial_para_tras() {
        rosters.guardar(escola, matematica, roster())

        val sobras = File(raiz, escola).listFiles()!!.filter { it.name.contains(".parcial-") }
        assertTrue(sobras.isEmpty(), "sobrou parcial: " + sobras.map { it.name })
    }

    /**
     * **Meio roster legivel e pior do que nenhum**, e por isso ilegivel se trata como nunca puxado.
     *
     * Turma com alunos faltando e indistinguivel de turma correta para quem le a tela — o professor
     * nao sabe quantos deveriam estar ali. A resposta certa e a mesma de nunca ter havido pull: o
     * gate barra e pede rede uma vez.
     */
    @Test
    fun roster_truncado_e_lido_como_nunca_puxado() {
        val pasta = File(raiz, escola).apply { mkdirs() }
        File(pasta, matematica + ".json").writeText("{\"puxado_em\":1757000000000,\"alu")

        assertNull(rosters.ler(escola, matematica), "roster truncado foi lido como roster")
    }

    // ------------------------------------------------------------------ substituir, nunca mesclar

    /**
     * **O aluno retirado no servidor some do aparelho.**
     *
     * Mesclar preservaria a linha de quem saiu do roster — uma copia de dado pessoal que o servidor
     * ja nao tem, e que nenhuma correcao la alcancaria. A asercao confere a **lista inteira**, e nao
     * so que Carla sumiu: conferir a ausencia de um nome passaria com a ordem embaralhada ou com um
     * quarto aluno aparecido do nada.
     */
    @Test
    fun substituir_nao_deixa_o_aluno_retirado_para_tras() {
        rosters.guardar(escola, matematica, roster(listOf(ana, bruno, carla)))

        rosters.guardar(escola, matematica, roster(listOf(ana, bruno)))

        assertEquals(
            listOf(ana, bruno),
            rosters.ler(escola, matematica)!!.alunos,
            "o aluno retirado no servidor continua guardado no aparelho",
        )
    }

    // ------------------------------------------------------------------ o escopo

    @Test
    fun roster_de_outra_organizacao_nao_e_alcancavel() {
        rosters.guardar(escola, matematica, roster())

        assertNull(
            rosters.ler(outra, matematica),
            "o roster de uma organizacao foi alcancado pela outra",
        )
    }

    @Test
    fun apagar_uma_organizacao_deixa_a_outra_intacta() {
        rosters.guardar(escola, matematica, roster())
        rosters.guardar(outra, matematica, roster(listOf(carla)))

        rosters.apagarDaOrganizacao(escola)

        assertNull(rosters.ler(escola, matematica), "o roster da organizacao apagada sobreviveu")
        assertNotNull(rosters.ler(outra, matematica), "o roster da outra organizacao foi junto")
    }

    @Test
    fun identificador_com_travessia_de_caminho_nao_grava_nem_le() {
        rosters.guardar("..", matematica, roster())
        rosters.guardar(escola, "../fora", roster())

        assertNull(rosters.ler("..", matematica))
        assertNull(rosters.ler(escola, "../fora"))
        assertTrue(
            !File(raiz, "fora.json").exists(),
            "a travessia de caminho escreveu fora do diretorio do roster",
        )
    }

    // ------------------------------------------------------------------ a minimizacao (1.3b)

    /**
     * **A negativa de minimizacao, medida e nao afirmada.**
     *
     * Varre o **JSON gravado inteiro**, em dois niveis, e nao confere dois campos nomeados: conferir
     * `turma` e `matricula` pelo nome deixaria passar o terceiro campo que ninguem previu, que e
     * exatamente o defeito que a mutacao (A) da fatia anterior encontrou do lado do servidor.
     *
     * **Guarda de vacuidade (P13):** a primeira asercao existe para que "nenhum campo proibido" nao
     * seja verdade por nao haver nada ali. Sem ela, um arquivo sem linha alguma passaria neste teste
     * afirmando uma protecao que nao foi exercida.
     */
    @Test
    fun o_gravado_nao_tem_nada_alem_de_token_nome_e_instante() {
        rosters.guardar(escola, matematica, roster(listOf(ana, bruno)))

        val texto = File(File(raiz, escola), matematica + ".json").readText()
        val json = Json.parseToJsonElement(texto).jsonObject

        // Guarda de vacuidade: sem linhas, a varredura abaixo nao exerce protecao nenhuma.
        val linhas = json["alunos"]!!.jsonArray
        assertEquals(2, linhas.size, "a varredura rodaria sobre nenhuma linha e passaria a toa")

        assertEquals(
            setOf("puxado_em", "alunos"),
            json.keys,
            "o envelope do roster ganhou campo alem do instante do pull: " + json.keys,
        )

        linhas.forEach { linha ->
            val campos = (linha as JsonObject).keys
            assertEquals(
                setOf("token", "nome"),
                campos,
                "uma linha de aluno guardada tem campo alem de token e nome: " + campos,
            )
        }
    }
}
