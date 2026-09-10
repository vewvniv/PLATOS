package com.platos.android.session

import com.platos.android.pacote.PacotesGuardados
import com.platos.domain.exam.ExamPackage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A sessao do aparelho, sem rede, sem servidor e sem aparelho.
 *
 * Os resultados que entram sao montados a mao de proposito: eles representam o que o adaptador ja
 * destilou, e produzir um de verdade exigiria um projeto Supabase e uma rede que se possa derrubar
 * no meio de uma chamada. O que este arquivo verifica e a **decisao**, que e onde os cenarios da
 * spec vivem.
 */
class DeviceSessionTest {

    /** Um armazenamento de mentira que registra o que foi apagado, para que sair seja verificavel. */
    private class Guardada(private var organizacao: String? = null) : SessaoGuardada {
        var credencialApagada = false
            private set
        var organizacaoApagada = false
            private set

        override fun organizacaoEscolhida(): String? = organizacao

        override fun guardarOrganizacaoEscolhida(id: String) {
            organizacao = id
        }

        override fun apagarOrganizacaoEscolhida() {
            organizacao = null
            organizacaoApagada = true
        }

        override fun apagarCredencial() {
            credencialApagada = true
        }
    }

    /**
     * Um cache de mentira que registra de quais organizacoes foi mandado apagar.
     *
     * Registra a **lista**, e nao um booleano: a tarefa 4.9 precisa afirmar nao so que apagou, mas
     * que apagou a organizacao certa. Um booleano passaria com o logout apagando o cache de qualquer
     * uma — inclusive da que o usuario seguinte vai usar.
     */
    private class PacotesFalsos : PacotesGuardados {
        val apagadas = mutableListOf<String>()

        override fun guardar(organizacao: String, hash: String, bytes: ByteArray) = Unit
        override fun ler(organizacao: String, hash: String): ExamPackage? = null
        override fun temConteudo(organizacao: String, hash: String): Boolean = false

        override fun apagarDaOrganizacao(organizacao: String) {
            apagadas += organizacao
        }
    }

    /**
     * Visoes de mentira, com o que foi gravado e o que foi mandado apagar.
     *
     * Como `PacotesFalsos`, registra a **lista** de apagadas: a revogacao precisa apagar a
     * organizacao certa, e um booleano passaria apagando a errada.
     */
    private class VisoesFalsas(vararg visoes: VisaoDaOrganizacao) : VisoesGuardadas {
        val guardadas = visoes.associateBy { it.organizacao.id }.toMutableMap()
        val apagadas = mutableListOf<String>()

        override fun ler(organizacao: String): VisaoDaOrganizacao? = guardadas[organizacao]

        override fun guardar(visao: VisaoDaOrganizacao) {
            guardadas[visao.organizacao.id] = visao
        }

        override fun apagarDaOrganizacao(organizacao: String) {
            apagadas += organizacao
            guardadas.remove(organizacao)
        }
    }

    private val escola = Organizacao("org-1", "Escola Municipal Vila Nova")
    private val pessoal = Organizacao("org-2", "Leon")
    private val prova = ProvaPublicada("mat-7a-2026-1", "Prova de Matematica", "a".repeat(64))

    // --- Os tres estados de falha, distintos (tarefa 3.2) ---

    @Test
    fun credencial_recusada_fica_na_entrada_dizendo_isso() {
        val sessao = DeviceSession(Guardada(), PacotesFalsos(), VisoesFalsas())
        sessao.aoEntrar(ResultadoDaEntrada.CredencialRecusada)

        val estado = sessao.state as DeviceState.Entrada
        assertEquals(MotivoDeEntrada.CREDENCIAL_RECUSADA, estado.motivo)
    }

    @Test
    fun sem_rede_na_entrada_nao_e_apresentado_como_credencial_recusada() {
        val sessao = DeviceSession(Guardada(), PacotesFalsos(), VisoesFalsas())
        sessao.aoEntrar(ResultadoDaEntrada.SemRede)

        val estado = sessao.state as DeviceState.Entrada
        assertEquals(MotivoDeEntrada.SEM_REDE, estado.motivo)
    }

    @Test
    fun sessao_expirada_volta_para_a_entrada_no_momento_em_que_e_detectada() {
        val guardada = Guardada("org-1")
        val sessao = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas())
        sessao.abrir(temSessaoGuardada = true)
        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.SessaoExpirada)

        val estado = sessao.state as DeviceState.Entrada
        assertEquals(MotivoDeEntrada.SESSAO_EXPIRADA, estado.motivo)
        assertTrue(guardada.credencialApagada, "sessao expirada tem de descartar a credencial")
    }

    /**
     * Regressao da fatia 4a, encontrada em aparelho em 2026-09-08 (tarefa 9b.1).
     *
     * Ate a 4a-zero, toda chamada autenticada acontecia em [DeviceState.Consultando], e tratar a
     * expiracao sob aquela guarda era seguro. A 4a acrescentou `provas()` e `pacote()`, que rodam
     * com o aparelho em [DeviceState.Ativa] — e para elas o 401 do interceptador era descartado em
     * silencio: a credencial expirada continuava guardada, e a tela dizia "nao foi possivel obter
     * as provas" com um "tentar de novo" que falharia para sempre.
     */
    @Test
    fun sessao_expirada_em_ativa_tambem_volta_para_a_entrada() {
        val guardada = Guardada("org-1")
        val sessao = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas())
        sessao.abrir(temSessaoGuardada = true)
        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(escola)))
        assertTrue(sessao.state is DeviceState.Ativa, "pre-condicao: o aparelho tem de estar ativo")

        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.SessaoExpirada)

        val estado = sessao.state as? DeviceState.Entrada
        assertEquals(
            MotivoDeEntrada.SESSAO_EXPIRADA,
            estado?.motivo,
            "expiracao detectada em Ativa foi descartada; estado ficou ${sessao.state}",
        )
        assertTrue(guardada.credencialApagada, "credencial expirada tem de ser descartada")
    }

    /** A guarda continua valendo para o que ela foi escrita: retorno atrasado depois de sair. */
    @Test
    fun expiracao_que_chega_depois_de_sair_nao_mente_sobre_a_causa() {
        val guardada = Guardada("org-1")
        val sessao = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas())
        sessao.abrir(temSessaoGuardada = true)
        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(escola)))
        sessao.sair()

        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.SessaoExpirada)

        val estado = sessao.state as DeviceState.Entrada
        assertEquals(
            MotivoDeEntrada.SAIU,
            estado.motivo,
            "quem saiu por vontade propria nao viu a sessao expirar",
        )
    }

    @Test
    fun os_tres_motivos_de_falha_sao_distintos_entre_si() {
        fun motivoDe(resultado: ResultadoDaEntrada): MotivoDeEntrada? {
            val sessao = DeviceSession(Guardada(), PacotesFalsos(), VisoesFalsas())
            sessao.aoEntrar(resultado)
            return (sessao.state as DeviceState.Entrada).motivo
        }

        val expirada = DeviceSession(Guardada(), PacotesFalsos(), VisoesFalsas()).let {
            it.abrir(temSessaoGuardada = true)
            it.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.SessaoExpirada)
            (it.state as DeviceState.Entrada).motivo
        }
        val motivos = listOf(
            motivoDe(ResultadoDaEntrada.CredencialRecusada),
            motivoDe(ResultadoDaEntrada.SemRede),
            expirada,
        )

        assertEquals(3, motivos.toSet().size, "os tres motivos colapsaram: $motivos")
    }

    // --- O nome vem da consulta, ou nao existe (tarefa 3.3) ---

    @Test
    fun o_nome_apresentado_e_o_que_a_consulta_devolveu() {
        val sessao = DeviceSession(Guardada(), PacotesFalsos(), VisoesFalsas())
        sessao.aoEntrar(ResultadoDaEntrada.Autenticado)
        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(escola)))

        val estado = sessao.state as DeviceState.Ativa
        assertEquals(escola.nome, estado.organizacao.nome)
    }

    @Test
    fun consulta_que_falha_nao_produz_nome_nenhum() {
        val sessao = DeviceSession(Guardada(), PacotesFalsos(), VisoesFalsas())
        sessao.aoEntrar(ResultadoDaEntrada.Autenticado)
        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.SemRede)

        val estado = sessao.state
        assertTrue(
            estado is DeviceState.SemOrganizacao,
            "esperava SemOrganizacao, veio $estado - nome de reserva e indistinguivel do verdadeiro",
        )
        assertEquals(FalhaDaConsulta.SEM_REDE, (estado as DeviceState.SemOrganizacao).falha)
    }

    @Test
    fun entrar_duas_vezes_nao_muda_o_que_e_apresentado() {
        val guardada = Guardada()
        val primeira = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas())
        primeira.aoEntrar(ResultadoDaEntrada.Autenticado)
        primeira.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(escola)))
        val antes = (primeira.state as DeviceState.Ativa).organizacao

        val segunda = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas())
        segunda.abrir(temSessaoGuardada = true)
        segunda.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(escola)))

        assertEquals(antes, (segunda.state as DeviceState.Ativa).organizacao)
    }

    // --- A escolha de organizacao (tarefa 3.5) ---

    @Test
    fun duas_organizacoes_pedem_escolha() {
        val sessao = DeviceSession(Guardada(), PacotesFalsos(), VisoesFalsas())
        sessao.aoEntrar(ResultadoDaEntrada.Autenticado)
        sessao.aoConsultarOrganizacoes(
            ResultadoDasOrganizacoes.Chegaram(listOf(escola, pessoal)),
        )

        val estado = sessao.state as DeviceState.Escolhendo
        assertEquals(listOf(escola, pessoal), estado.organizacoes)
    }

    @Test
    fun uma_organizacao_so_nao_pede_escolha() {
        val sessao = DeviceSession(Guardada(), PacotesFalsos(), VisoesFalsas())
        sessao.aoEntrar(ResultadoDaEntrada.Autenticado)
        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(pessoal)))

        assertEquals(pessoal, (sessao.state as DeviceState.Ativa).organizacao)
    }

    @Test
    fun a_escolha_sobrevive_ao_fechamento_do_aplicativo() {
        val guardada = Guardada()
        val antes = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas())
        antes.aoEntrar(ResultadoDaEntrada.Autenticado)
        antes.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(escola, pessoal)))
        antes.escolher(pessoal)

        val depois = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas())
        depois.abrir(temSessaoGuardada = true)
        depois.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(escola, pessoal)))

        assertEquals(
            pessoal,
            (depois.state as DeviceState.Ativa).organizacao,
            "a escolha nao sobreviveu, e a escolha voltou a ser pedida",
        )
    }

    @Test
    fun escolha_guardada_que_saiu_da_lista_volta_a_ser_pedida() {
        val guardada = Guardada("org-que-nao-existe-mais")
        val sessao = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas())
        sessao.abrir(temSessaoGuardada = true)
        sessao.aoConsultarOrganizacoes(
            ResultadoDasOrganizacoes.Chegaram(listOf(escola, pessoal)),
        )

        assertTrue(sessao.state is DeviceState.Escolhendo, "veio ${sessao.state}")
    }

    // --- A primeira parede: o arranque sem rede (tarefas 3.1 e 3.2) ---

    /**
     * **O cenario que a 9.2 da fatia 4a nao passava.**
     *
     * O servidor nao respondeu, e o aparelho tem visao guardada: a tela de trabalho abre a partir
     * dela. As tres afirmacoes sao separadas de proposito — que abriu, que o nome e o da visao, e
     * que o estado **declara** de onde veio. Sem a terceira, a tela nao teria como marcar a leitura
     * como cacheada, e o requisito exige que ela marque.
     */
    @Test
    fun sem_rede_com_visao_guardada_abre_a_tela_de_trabalho() {
        val visao = VisaoDaOrganizacao(escola, listOf(prova), vistaEm = 1_757_000_000_000)
        val sessao = DeviceSession(Guardada(escola.id), PacotesFalsos(), VisoesFalsas(visao))
        sessao.abrir(temSessaoGuardada = true)

        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.SemRede)

        val estado = sessao.state
        assertTrue(estado is DeviceState.Ativa, "veio $estado")
        assertEquals(escola, (estado as DeviceState.Ativa).organizacao)
        assertEquals(Procedencia.Cacheada(1_757_000_000_000), estado.procedencia)
    }

    /**
     * Sem visao guardada nao ha o que apresentar, e a tela diz o que resolve.
     *
     * E o caso do aparelho que nunca completou uma consulta: nenhum nome, nenhuma prova, e um pedido
     * de rede **uma vez** — a mesma forma da barragem de pacote ausente, e pela mesma razao. Dizer so
     * "confira a conexao" esconderia que o problema tem solucao anterior e definitiva.
     */
    @Test
    fun sem_rede_sem_visao_guardada_pede_rede_uma_vez() {
        val sessao = DeviceSession(Guardada(escola.id), PacotesFalsos(), VisoesFalsas())
        sessao.abrir(temSessaoGuardada = true)

        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.SemRede)

        val estado = sessao.state
        assertTrue(estado is DeviceState.SemOrganizacao, "veio $estado")
        assertEquals(FalhaDaConsulta.SEM_REDE, (estado as DeviceState.SemOrganizacao).falha)
        assertTrue(
            textoSemOrganizacao(estado.falha).explicacao.contains("uma vez"),
            "a frase nao diz que conectar uma vez resolve",
        )
    }

    /**
     * **A decisao 10 da fatia 4a-zero continua decidida, e este teste e o que a segura.**
     *
     * O servidor **respondeu**, e a organizacao guardada nao esta na lista: a escolha cai, mesmo
     * havendo visao guardada dela. Uma implementacao que consultasse a visao antes de olhar a
     * resposta manteria o aparelho operando sob um vinculo que a instituicao ja revogou — e passaria
     * no teste de cima sem passar neste.
     */
    @Test
    fun servidor_que_responde_sem_a_organizacao_derruba_a_escolha_mesmo_com_visao() {
        val visao = VisaoDaOrganizacao(escola, listOf(prova), vistaEm = 1_757_000_000_000)
        val guardada = Guardada(escola.id)
        val sessao = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas(visao))
        sessao.abrir(temSessaoGuardada = true)

        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(pessoal)))

        assertTrue(sessao.state !is DeviceState.Ativa || (sessao.state as DeviceState.Ativa).organizacao != escola,
            "o aparelho seguiu ativo numa organizacao que a resposta nao trouxe: ${sessao.state}")
    }

    // --- Sair apaga as tres coisas (tarefa 3.6 da 4a-zero, e 4.8 da 4a) ---

    @Test
    fun sair_apaga_a_credencial_e_a_organizacao_escolhida() {
        val guardada = Guardada()
        val sessao = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas())
        sessao.aoEntrar(ResultadoDaEntrada.Autenticado)
        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(escola, pessoal)))
        sessao.escolher(escola)

        sessao.sair()

        assertTrue(guardada.credencialApagada, "sair nao apagou a credencial")
        assertTrue(guardada.organizacaoApagada, "sair nao apagou a organizacao escolhida")
        assertEquals(MotivoDeEntrada.SAIU, (sessao.state as DeviceState.Entrada).motivo)
    }

    /**
     * Tarefa 4.8: sair apaga tambem os pacotes guardados sob a organizacao ativa.
     *
     * E o defeito da 3.7 da fatia 4a-zero um nivel abaixo. La o que sobrevivia a troca de conta era
     * a escolha de organizacao; aqui e o conteudo que o usuario anterior baixou — e ele nao aparece
     * em tela nenhuma, entao ninguem tem como notar que ficou.
     */
    @Test
    fun sair_apaga_os_pacotes_guardados_sob_a_organizacao_ativa() {
        val guardada = Guardada()
        val pacotes = PacotesFalsos()
        val sessao = DeviceSession(guardada, pacotes, VisoesFalsas())
        sessao.aoEntrar(ResultadoDaEntrada.Autenticado)
        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(escola, pessoal)))
        sessao.escolher(escola)

        sessao.sair()

        assertEquals(listOf(escola.id), pacotes.apagadas, "sair nao apagou os pacotes da organizacao ativa")
    }

    /**
     * Sair a partir da escolha tambem apaga, e a organizacao vem do disco.
     *
     * Nao e caso de borda inventado: a escolha guardada sobrevive ao fechamento, entao a abertura
     * seguinte pode cair em `Ativa` sem passar por `escolher`, e sair dali precisa apagar o mesmo
     * tanto. Se a organizacao fosse lida so do estado, este caminho sairia sem limpar nada.
     */
    @Test
    fun sair_com_organizacao_vinda_do_disco_tambem_apaga_os_pacotes() {
        val guardada = Guardada(escola.id)
        val pacotes = PacotesFalsos()
        val sessao = DeviceSession(guardada, pacotes, VisoesFalsas())

        sessao.sair()

        assertEquals(listOf(escola.id), pacotes.apagadas)
    }

    /**
     * Sem organizacao nenhuma, sair nao tenta apagar coisa nenhuma.
     *
     * Puxar exige organizacao ativa, entao nao ha pacote guardado; chamar o apagamento com um
     * identificador vazio ou inventado seria pedir para o cache decidir o que fazer com lixo.
     */
    @Test
    fun sair_sem_organizacao_escolhida_nao_apaga_pacote_nenhum() {
        val pacotes = PacotesFalsos()
        val sessao = DeviceSession(Guardada(), pacotes, VisoesFalsas())

        sessao.sair()

        assertTrue(pacotes.apagadas.isEmpty(), "apagou ${pacotes.apagadas}")
    }

    @Test
    fun a_escolha_do_usuario_anterior_nao_e_herdada_pelo_seguinte() {
        val guardada = Guardada()
        val anterior = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas())
        anterior.aoEntrar(ResultadoDaEntrada.Autenticado)
        anterior.aoConsultarOrganizacoes(
            ResultadoDasOrganizacoes.Chegaram(listOf(escola, pessoal)),
        )
        anterior.escolher(escola)
        anterior.sair()

        assertNull(
            guardada.organizacaoEscolhida(),
            "o segundo usuario herdaria a organizacao do primeiro",
        )

        val seguinte = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas())
        seguinte.aoEntrar(ResultadoDaEntrada.Autenticado)
        seguinte.aoConsultarOrganizacoes(
            ResultadoDasOrganizacoes.Chegaram(listOf(escola, pessoal)),
        )

        assertTrue(
            seguinte.state is DeviceState.Escolhendo,
            "veio ${seguinte.state} - organizacao pre-selecionada para outro usuario",
        )
    }

    // --- Resultado que chega fora de hora (tarefa 3.8) ---

    @Test
    fun retorno_da_mesma_chamada_nao_sobrescreve_a_sessao_ja_expirada() {
        val sessao = DeviceSession(Guardada(), PacotesFalsos(), VisoesFalsas())
        sessao.abrir(temSessaoGuardada = true)

        // O interceptador acusa o 401 durante a chamada.
        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.SessaoExpirada)
        // E a mesma chamada retorna logo depois, sem saber de nada.
        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Falhou)

        val estado = sessao.state
        assertTrue(
            estado is DeviceState.Entrada && estado.motivo == MotivoDeEntrada.SESSAO_EXPIRADA,
            "veio $estado - o retorno tardio apagou o motivo verdadeiro e a tela diria que nao " +
                "foi possivel obter a organizacao, quando o que houve foi a sessao expirar",
        )
    }

    @Test
    fun consulta_que_responde_depois_de_sair_nao_ressuscita_a_tela_de_trabalho() {
        val guardada = Guardada()
        val sessao = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas())
        sessao.abrir(temSessaoGuardada = true)
        sessao.aoConsultarOrganizacoes(
            ResultadoDasOrganizacoes.Chegaram(listOf(Organizacao("org-1", "Escola"))),
        )

        sessao.sair()
        sessao.aoConsultarOrganizacoes(
            ResultadoDasOrganizacoes.Chegaram(listOf(Organizacao("org-1", "Escola"))),
        )

        val estado = sessao.state
        assertTrue(
            estado is DeviceState.Entrada && estado.motivo == MotivoDeEntrada.SAIU,
            "veio $estado - a resposta atrasada devolveu o aparelho a tela de trabalho depois " +
                "de o professor ter saido",
        )
    }
}
