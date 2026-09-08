package com.platos.android.session

import com.platos.android.pacote.MotivoDaRecusa
import com.platos.android.render.RendererContract
import com.platos.domain.exam.ExamPackage
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * O preparo e o gate, sem rede, sem disco e sem aparelho.
 *
 * Os resultados que entram sao montados a mao de proposito, como em [DeviceSessionTest]: eles
 * representam o que a obtencao ja destilou. O que este arquivo verifica e a **decisao**.
 *
 * O pacote usado nas passagens pelo gate e o de referencia versionado, e nao um montado a mao: a
 * versao de renderizador conferida e a que o artefato real declara.
 */
class PreparoDaProvaTest {

    private val fixtures = File(
        System.getProperty("platos.fixtures")
            ?: error("propriedade `platos.fixtures` nao definida pelo build"),
    )

    private val pacote: ExamPackage = Json.decodeFromString(
        File(fixtures, "prova-referencia.package.json").readText(),
    )

    private val provaA = ProvaPublicada("mat-7a-2026-1", "Prova de Matematica", "a".repeat(64))
    private val provaB = ProvaPublicada("mat-7b-2026-1", "Prova de Portugues", "b".repeat(64))

    private fun preparoEscolhendo(vararg provas: ProvaPublicada): PreparoDaProva {
        val preparo = PreparoDaProva()
        preparo.aoListar(ResultadoDasProvas.Chegaram(provas.toList()))
        return preparo
    }

    private fun preparoPreparando(prova: ProvaPublicada = provaA): PreparoDaProva {
        val preparo = preparoEscolhendo(prova)
        preparo.escolher(prova)
        return preparo
    }

    // --- A listagem (tarefa 5.2) ---

    @Test
    fun provas_que_chegam_viram_escolha() {
        val preparo = preparoEscolhendo(provaA, provaB)

        assertEquals(EstadoDaProva.Escolhendo(listOf(provaA, provaB)), preparo.state)
    }

    @Test
    fun organizacao_sem_prova_publicada_e_estado_proprio() {
        val preparo = PreparoDaProva()
        preparo.aoListar(ResultadoDasProvas.Chegaram(emptyList()))

        assertEquals(EstadoDaProva.SemProvaPublicada, preparo.state)
    }

    /**
     * **O cenario que a tarefa 5.3 protege.** Sem rede nao e lista vazia.
     *
     * Os dois estados sao afirmados como diferentes um do outro, e nao so cada um contra o esperado:
     * e a comparacao direta que quebra se alguem os unificar.
     */
    @Test
    fun listagem_sem_rede_nao_e_lista_vazia() {
        val semRede = PreparoDaProva().apply { aoListar(ResultadoDasProvas.SemRede) }
        val vazia = PreparoDaProva().apply { aoListar(ResultadoDasProvas.Chegaram(emptyList())) }

        assertEquals(EstadoDaProva.ListagemFalhou(FalhaDaListagem.SEM_REDE), semRede.state)
        assertNotEquals(vazia.state, semRede.state)
    }

    @Test
    fun listagem_que_falha_por_outra_causa_e_distinta_de_sem_rede() {
        val outra = PreparoDaProva().apply { aoListar(ResultadoDasProvas.Falhou) }
        val semRede = PreparoDaProva().apply { aoListar(ResultadoDasProvas.SemRede) }

        assertEquals(EstadoDaProva.ListagemFalhou(FalhaDaListagem.OUTRA), outra.state)
        assertNotEquals(semRede.state, outra.state)
    }

    @Test
    fun resultado_de_listagem_que_chega_fora_de_listando_e_descartado() {
        val preparo = preparoPreparando()

        preparo.aoListar(ResultadoDasProvas.Chegaram(listOf(provaB)))

        assertEquals(EstadoDaProva.Preparando(provaA), preparo.state)
    }

    // --- A escolha ---

    @Test
    fun escolher_uma_prova_apresentada_leva_ao_preparo() {
        val preparo = preparoEscolhendo(provaA, provaB)

        preparo.escolher(provaB)

        assertEquals(EstadoDaProva.Preparando(provaB), preparo.state)
    }

    /**
     * Prova que nao veio da consulta nao e escolhivel.
     *
     * E o mesmo principio pelo qual o nome da organizacao vem da API: a tela nunca opera sobre algo
     * que a rota nao devolveu.
     */
    @Test
    fun escolher_prova_que_nao_foi_apresentada_nao_faz_nada() {
        val preparo = preparoEscolhendo(provaA)

        preparo.escolher(provaB)

        assertEquals(EstadoDaProva.Escolhendo(listOf(provaA)), preparo.state)
    }

    // --- O gate (tarefa 5.4) ---

    @Test
    fun pacote_conferido_abre_o_escaneamento() {
        val preparo = preparoPreparando()

        preparo.aoObterPacote(ResultadoDoPacote.Conferido(pacote, provaA.contentHash))

        assertEquals(EstadoDaProva.Pronta(provaA, provaA.contentHash), preparo.state)
    }

    /**
     * Tarefa 5.5: o gate barra por versao.
     *
     * O pacote e o real, com a versao **subida acima** da que o aplicativo desenha. Ate esta fatia o
     * guarda existia so na renderizacao; esta e a primeira vez que ele tem dente no caminho de
     * captura.
     */
    @Test
    fun pacote_que_exige_renderizador_mais_novo_e_barrado() {
        val exigente = comVersaoDeRenderizador(RendererContract.RENDERER_VERSION + 1)
        val preparo = preparoPreparando()

        preparo.aoObterPacote(ResultadoDoPacote.Conferido(exigente, provaA.contentHash))

        assertEquals(
            EstadoDaProva.Barrada(provaA, MotivoDaBarragem.VERSAO_INSUFICIENTE),
            preparo.state,
        )
    }

    @Test
    fun pacote_que_exige_exatamente_a_versao_deste_aplicativo_passa() {
        val naMedida = comVersaoDeRenderizador(RendererContract.RENDERER_VERSION)
        val preparo = preparoPreparando()

        preparo.aoObterPacote(ResultadoDoPacote.Conferido(naMedida, provaA.contentHash))

        assertTrue(preparo.state is EstadoDaProva.Pronta, "veio ${preparo.state}")
    }

    @Test
    fun ausencia_de_rede_e_ausencia_de_pacote_sao_barragens_distintas() {
        val semRede = preparoPreparando().apply { aoObterPacote(ResultadoDoPacote.SemRede) }
        val ausente = preparoPreparando().apply { aoObterPacote(ResultadoDoPacote.Ausente) }

        assertEquals(EstadoDaProva.Barrada(provaA, MotivoDaBarragem.SEM_REDE), semRede.state)
        assertEquals(EstadoDaProva.Barrada(provaA, MotivoDaBarragem.PACOTE_AUSENTE), ausente.state)
        assertNotEquals(ausente.state, semRede.state)
    }

    @Test
    fun conferencia_que_falha_e_barragem_propria() {
        val preparo = preparoPreparando()

        preparo.aoObterPacote(ResultadoDoPacote.Recusado(MotivoDaRecusa.INTEGRIDADE))

        assertEquals(
            EstadoDaProva.Barrada(provaA, MotivoDaBarragem.CONFERENCIA_FALHOU),
            preparo.state,
        )
    }

    @Test
    fun resultado_de_pacote_que_chega_fora_do_preparo_e_descartado() {
        val preparo = preparoEscolhendo(provaA)

        preparo.aoObterPacote(ResultadoDoPacote.Conferido(pacote, provaA.contentHash))

        assertEquals(EstadoDaProva.Escolhendo(listOf(provaA)), preparo.state)
    }

    // --- As frases (tarefa 5.6) ---

    @Test
    fun toda_barragem_tem_frase_e_nenhuma_se_repete() {
        val frases = MotivoDaBarragem.entries.map { textoDaBarragem(it) }

        assertTrue(frases.none { it.isBlank() }, "ha barragem sem frase")
        assertEquals(frases.size, frases.toSet().size, "duas barragens dizem a mesma coisa")
    }

    @Test
    fun toda_falha_de_listagem_tem_frase_e_nenhuma_se_repete() {
        val frases = FalhaDaListagem.entries.map { textoDaListagem(it) }

        assertTrue(frases.none { it.isBlank() }, "ha falha sem frase")
        assertEquals(frases.size, frases.toSet().size, "duas falhas dizem a mesma coisa")
    }

    /**
     * A frase de rede fala de baixar uma vez, e nao so de "erro".
     *
     * E o unico motivo cuja acao util nao e obvia: as outras tres pedem publicar, tentar de novo ou
     * atualizar, e esta pede conectar **uma vez** para que as seguintes funcionem sem rede. Sem
     * dizer isso, o professor em sala sem sinal nao sabe que o problema tem solucao anterior.
     */
    @Test
    fun a_frase_de_sem_rede_explica_que_baixar_uma_vez_resolve() {
        val frase = textoDaBarragem(MotivoDaBarragem.SEM_REDE)

        assertTrue(frase.contains("sem rede"), frase)
        assertTrue(frase.contains("uma vez"), frase)
    }

    private fun comVersaoDeRenderizador(versao: Int): ExamPackage =
        pacote.copy(
            layout = pacote.layout.mapValues { (_, mapa) -> mapa.copy(minRendererVersion = versao) },
        )
}
