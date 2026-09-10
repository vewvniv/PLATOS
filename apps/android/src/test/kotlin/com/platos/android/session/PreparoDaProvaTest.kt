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

    /**
     * Um duplo em memoria. O que a porta faz sobre arquivos ja e conferido em `VisoesEmArquivoTest`;
     * o que este arquivo verifica e **quando** a maquina grava, e nao como o disco responde.
     */
    private class VisoesEmMemoria : VisoesGuardadas {
        val gravadas = mutableMapOf<String, VisaoDaOrganizacao>()
        val apagadas = mutableListOf<String>()

        override fun ler(organizacao: String): VisaoDaOrganizacao? = gravadas[organizacao]

        override fun guardar(visao: VisaoDaOrganizacao) {
            gravadas[visao.organizacao.id] = visao
        }

        override fun apagarDaOrganizacao(organizacao: String) {
            apagadas += organizacao
            gravadas.remove(organizacao)
        }
    }

    private val visoes = VisoesEmMemoria()
    private val escola = Organizacao("01a06ba4-cb43-7d97-842d-165352d010b5", "Escola de Teste")
    private val agora = 1_757_000_000_000L

    private val provaA = ProvaPublicada("mat-7a-2026-1", "Prova de Matematica", "a".repeat(64))
    private val provaB = ProvaPublicada("mat-7b-2026-1", "Prova de Portugues", "b".repeat(64))

    private fun preparoEscolhendo(vararg provas: ProvaPublicada): PreparoDaProva {
        val preparo = PreparoDaProva(visoes, escola)
        preparo.aoListar(ResultadoDasProvas.Chegaram(provas.toList()), agora)
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
        val preparo = PreparoDaProva(visoes, escola)
        preparo.aoListar(ResultadoDasProvas.Chegaram(emptyList()), agora)

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
        val semRede = PreparoDaProva(visoes, escola).apply { aoListar(ResultadoDasProvas.SemRede, agora) }
        val vazia = PreparoDaProva(visoes, escola).apply { aoListar(ResultadoDasProvas.Chegaram(emptyList()), agora) }

        assertEquals(EstadoDaProva.ListagemFalhou(FalhaDaListagem.SEM_REDE), semRede.state)
        assertNotEquals(vazia.state, semRede.state)
    }

    @Test
    fun listagem_que_falha_por_outra_causa_e_distinta_de_sem_rede() {
        val outra = PreparoDaProva(visoes, escola).apply { aoListar(ResultadoDasProvas.Falhou, agora) }
        val semRede = PreparoDaProva(visoes, escola).apply { aoListar(ResultadoDasProvas.SemRede, agora) }

        assertEquals(EstadoDaProva.ListagemFalhou(FalhaDaListagem.OUTRA), outra.state)
        assertNotEquals(semRede.state, outra.state)
    }

    @Test
    fun resultado_de_listagem_que_chega_fora_de_listando_e_descartado() {
        val preparo = preparoPreparando()

        preparo.aoListar(ResultadoDasProvas.Chegaram(listOf(provaB)), agora)

        assertEquals(EstadoDaProva.Preparando(provaA), preparo.state)
    }

    // --- A gravacao da visao (tarefas 2.1 e 2.2) ---

    /**
     * Listagem que chegou grava a visao inteira: nome, provas e o instante.
     *
     * As tres partes sao afirmadas, e nao so a existencia da visao: sem o nome o arranque sem rede
     * nao tem o que apresentar, sem as provas ele tem nome e nada para escanear, e sem o instante a
     * tela nao consegue dizer de quando e o que mostra.
     */
    @Test
    fun listagem_que_chega_grava_a_visao() {
        val preparo = preparoEscolhendo(provaA, provaB)

        val visao = requireNotNull(visoes.ler(escola.id)) { "nada foi gravado" }
        assertEquals(escola, visao.organizacao)
        assertEquals(listOf(provaA, provaB), visao.provas)
        assertEquals(agora, visao.vistaEm)
        assertTrue(preparo.state is EstadoDaProva.Escolhendo, "veio ${preparo.state}")
    }

    /**
     * Organizacao que **de fato** nao tem prova publicada grava visao vazia.
     *
     * "Nao ha prova publicada" e uma afirmacao sobre o mundo, e a consulta que chegou a autoriza. O
     * aparelho pode reproduzi-la sem rede em vez de pedir conexao para redescobrir um vazio.
     */
    @Test
    fun listagem_vazia_que_chegou_grava_visao_vazia() {
        PreparoDaProva(visoes, escola).apply { aoListar(ResultadoDasProvas.Chegaram(emptyList()), agora) }

        val visao = requireNotNull(visoes.ler(escola.id)) { "lista vazia que chegou nao gravou" }
        assertEquals(emptyList<ProvaPublicada>(), visao.provas)
    }

    /**
     * **O cenario que a tarefa 2.2 protege.** Falha nao grava, e a razao nao e economia.
     *
     * A afirmacao forte e a segunda: uma visao boa **sobrevive** a uma listagem que falhou. Gravar
     * no caminho de falha substituiria o que o aparelho sabia por um vazio, e o professor sem rede
     * passaria a nao ver as provas que via um minuto antes.
     */
    @Test
    fun listagem_que_falha_nao_grava_e_nao_apaga_o_que_havia() {
        preparoEscolhendo(provaA, provaB)
        val boa = requireNotNull(visoes.ler(escola.id))

        PreparoDaProva(visoes, escola).apply { aoListar(ResultadoDasProvas.SemRede, agora + 1) }
        PreparoDaProva(visoes, escola).apply { aoListar(ResultadoDasProvas.Falhou, agora + 2) }

        assertEquals(boa, visoes.ler(escola.id), "uma listagem que falhou mexeu na visao guardada")
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

    // --- A volta do escaneamento (tarefa 9b.2) ---

    /**
     * O que a 9b.2 encontrou em aparelho: a camera vai, volta, e o preparo continua em `Pronta`.
     *
     * `Pronta` diz "o gate passou e a camera vai abrir"; ela nao diz se a camera **ja foi**. Sem um
     * evento de volta, a tela continuava desenhando o preparo com a camera fechada, sem nenhum
     * elemento clicavel e com o `back` saindo do aplicativo.
     *
     * A afirmacao e sobre o **estado de destino**, e nao sobre ter mudado: "mudou" seria verdade
     * tambem se a volta jogasse o professor na tela de trabalho, e ali a lista que ele acabou de usar
     * some — ela precisa estar na tela para a folha seguinte ser escaneada sem rede.
     */
    @Test
    fun voltar_do_escaneamento_devolve_a_escolha_da_prova() {
        val preparo = preparoEscolhendo(provaA, provaB)
        preparo.escolher(provaA)
        preparo.aoObterPacote(ResultadoDoPacote.Conferido(pacote, provaA.contentHash))

        preparo.aoVoltarDoEscaneamento(listOf(provaA, provaB))

        assertEquals(
            EstadoDaProva.Escolhendo(listOf(provaA, provaB)),
            preparo.state,
            "a volta do escaneamento nao devolveu a escolha; estado ficou ${preparo.state}",
        )
    }

    /**
     * O que a 8.5 precisa: escanear a mesma prova mais de uma vez, sem consultar de novo.
     *
     * A lista que volta e a que ja tinha sido apresentada, entao escolher a mesma prova recomeca o
     * preparo — e a obtencao seguinte cai no pacote guardado. Sem rede, e o unico caminho que existe.
     */
    @Test
    fun depois_de_voltar_a_mesma_prova_e_escolhivel_de_novo() {
        val preparo = preparoEscolhendo(provaA, provaB)
        preparo.escolher(provaA)
        preparo.aoObterPacote(ResultadoDoPacote.Conferido(pacote, provaA.contentHash))
        preparo.aoVoltarDoEscaneamento(listOf(provaA, provaB))

        preparo.escolher(provaA)

        assertEquals(
            EstadoDaProva.Preparando(provaA),
            preparo.state,
            "a mesma prova nao voltou a ser escolhivel; estado ficou ${preparo.state}",
        )
    }

    /**
     * A guarda, e ela e a mesma dos outros eventos desta maquina.
     *
     * Volta que chegue com o preparo em outro estado nao reescreve o que esta na tela. O caso real e
     * o retorno atrasado: a `Activity` pode ser recriada enquanto a camera esta aberta, e o resultado
     * do escaneamento chega depois de o fluxo ja ter seguido.
     */
    @Test
    fun volta_que_chega_fora_do_escaneamento_nao_reescreve_o_preparo() {
        val preparo = preparoPreparando()

        preparo.aoVoltarDoEscaneamento(listOf(provaA, provaB))

        assertEquals(
            EstadoDaProva.Preparando(provaA),
            preparo.state,
            "a volta reescreveu um preparo em curso; estado ficou ${preparo.state}",
        )
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
