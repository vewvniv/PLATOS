package com.platos.android.session

import com.platos.android.pacote.PacotesGuardados
import com.platos.android.roster.AlunoDoRoster
import com.platos.android.roster.RosterDaProva
import com.platos.android.roster.RostersGuardados
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

    /**
     * Rosters de mentira, com o que foi gravado e o que foi mandado apagar.
     *
     * Como `PacotesFalsos` e `VisoesFalsas`, registra a **lista** de apagadas, e pela mesma razao —
     * um booleano passaria apagando a organizacao errada. Aqui a razao pesa mais: o que apagaria
     * errado e nome de aluno.
     */
    private class RostersFalsos : RostersGuardados {
        val guardados = mutableMapOf<Pair<String, String>, RosterDaProva>()
        val apagadas = mutableListOf<String>()

        override fun ler(organizacao: String, prova: String): RosterDaProva? =
            guardados[organizacao to prova]

        override fun guardar(organizacao: String, prova: String, roster: RosterDaProva) {
            guardados[organizacao to prova] = roster
        }

        override fun apagarDaOrganizacao(organizacao: String) {
            apagadas += organizacao
            guardados.keys.removeAll { it.first == organizacao }
        }
    }

    private val escola = Organizacao("org-1", "Escola Municipal Vila Nova")
    private val pessoal = Organizacao("org-2", "Leon")
    private val prova = ProvaPublicada("mat-7a-2026-1", "Prova de Matematica", "a".repeat(64))



    // --- Os tres estados de falha, distintos (tarefa 3.2) ---

    @Test
    fun credencial_recusada_fica_na_entrada_dizendo_isso() {
        val sessao = DeviceSession(Guardada(), PacotesFalsos(), VisoesFalsas(), RostersFalsos())
        sessao.aoEntrar(ResultadoDaEntrada.CredencialRecusada)

        val estado = sessao.state as DeviceState.Entrada
        assertEquals(MotivoDeEntrada.CREDENCIAL_RECUSADA, estado.motivo)
    }

    @Test
    fun sem_rede_na_entrada_nao_e_apresentado_como_credencial_recusada() {
        val sessao = DeviceSession(Guardada(), PacotesFalsos(), VisoesFalsas(), RostersFalsos())
        sessao.aoEntrar(ResultadoDaEntrada.SemRede)

        val estado = sessao.state as DeviceState.Entrada
        assertEquals(MotivoDeEntrada.SEM_REDE, estado.motivo)
    }

    @Test
    fun sessao_expirada_volta_para_a_entrada_no_momento_em_que_e_detectada() {
        val guardada = Guardada("org-1")
        val sessao = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas(), RostersFalsos())
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
        val sessao = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas(), RostersFalsos())
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
        val sessao = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas(), RostersFalsos())
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
            val sessao = DeviceSession(Guardada(), PacotesFalsos(), VisoesFalsas(), RostersFalsos())
            sessao.aoEntrar(resultado)
            return (sessao.state as DeviceState.Entrada).motivo
        }

        val expirada = DeviceSession(Guardada(), PacotesFalsos(), VisoesFalsas(), RostersFalsos()).let {
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
        val sessao = DeviceSession(Guardada(), PacotesFalsos(), VisoesFalsas(), RostersFalsos())
        sessao.aoEntrar(ResultadoDaEntrada.Autenticado)
        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(escola)))

        val estado = sessao.state as DeviceState.Ativa
        assertEquals(escola.nome, estado.organizacao.nome)
    }

    @Test
    fun consulta_que_falha_nao_produz_nome_nenhum() {
        val sessao = DeviceSession(Guardada(), PacotesFalsos(), VisoesFalsas(), RostersFalsos())
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
        val primeira = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas(), RostersFalsos())
        primeira.aoEntrar(ResultadoDaEntrada.Autenticado)
        primeira.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(escola)))
        val antes = (primeira.state as DeviceState.Ativa).organizacao

        val segunda = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas(), RostersFalsos())
        segunda.abrir(temSessaoGuardada = true)
        segunda.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(escola)))

        assertEquals(antes, (segunda.state as DeviceState.Ativa).organizacao)
    }

    // --- A escolha de organizacao (tarefa 3.5) ---

    @Test
    fun duas_organizacoes_pedem_escolha() {
        val sessao = DeviceSession(Guardada(), PacotesFalsos(), VisoesFalsas(), RostersFalsos())
        sessao.aoEntrar(ResultadoDaEntrada.Autenticado)
        sessao.aoConsultarOrganizacoes(
            ResultadoDasOrganizacoes.Chegaram(listOf(escola, pessoal)),
        )

        val estado = sessao.state as DeviceState.Escolhendo
        assertEquals(listOf(escola, pessoal), estado.organizacoes)
    }

    @Test
    fun uma_organizacao_so_nao_pede_escolha() {
        val sessao = DeviceSession(Guardada(), PacotesFalsos(), VisoesFalsas(), RostersFalsos())
        sessao.aoEntrar(ResultadoDaEntrada.Autenticado)
        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(pessoal)))

        assertEquals(pessoal, (sessao.state as DeviceState.Ativa).organizacao)
    }

    @Test
    fun a_escolha_sobrevive_ao_fechamento_do_aplicativo() {
        val guardada = Guardada()
        val antes = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas(), RostersFalsos())
        antes.aoEntrar(ResultadoDaEntrada.Autenticado)
        antes.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(escola, pessoal)))
        antes.escolher(pessoal)

        val depois = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas(), RostersFalsos())
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
        val sessao = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas(), RostersFalsos())
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
        val sessao = DeviceSession(Guardada(escola.id), PacotesFalsos(), VisoesFalsas(visao), RostersFalsos())
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
        val sessao = DeviceSession(Guardada(escola.id), PacotesFalsos(), VisoesFalsas(), RostersFalsos())
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
        val sessao = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas(visao), RostersFalsos())
        sessao.abrir(temSessaoGuardada = true)

        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(pessoal)))

        assertTrue(sessao.state !is DeviceState.Ativa || (sessao.state as DeviceState.Ativa).organizacao != escola,
            "o aparelho seguiu ativo numa organizacao que a resposta nao trouxe: ${sessao.state}")
    }

    // --- A revogacao observada apaga o que estava guardado (tarefas 5.1 e 5.2) ---

    /**
     * **A visao sai do disco quando o servidor diz que o vinculo caiu.**
     *
     * Separado do teste dos pacotes de proposito, e nao por gosto de granularidade: a tarefa 5.3
     * manda apagar **so** a visao e conferir que o cenario dos pacotes fica vermelho enquanto este
     * continua verde. Com as duas asercoes dentro de um unico teste, a mutacao derrubaria o mesmo
     * teste nas duas direcoes e nao diria qual das duas metades segurou.
     *
     * Duas asercoes, e a segunda nao e redundante: a primeira diz que o apagamento foi pedido para a
     * organizacao **certa** — um booleano passaria apagando a visao da organizacao que o usuario
     * ainda tem —, e a segunda diz que ele **surtiu efeito**.
     */
    @Test
    fun revogacao_observada_apaga_a_visao_da_organizacao() {
        val visao = VisaoDaOrganizacao(escola, listOf(prova), vistaEm = 1_757_000_000_000)
        val visoes = VisoesFalsas(visao)
        val sessao = DeviceSession(Guardada(escola.id), PacotesFalsos(), visoes, RostersFalsos())
        sessao.abrir(temSessaoGuardada = true)

        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(pessoal)))

        assertEquals(
            listOf(escola.id),
            visoes.apagadas,
            "a revogacao nao apagou a visao da organizacao certa",
        )
        assertNull(visoes.ler(escola.id), "a visao continua legivel depois da revogacao")
    }

    /**
     * **O gabarito em cache vai junto**, e e este o resultado que a tarefa 5.1 existe para produzir.
     *
     * A escolha cair sozinha — o que ja acontecia desde a 4a-zero — deixava no aparelho o pacote
     * conferido de uma organizacao que a instituicao ja revogou. Ele nao aparece em tela nenhuma,
     * entao a ausencia deste apagamento seria invisivel para quem usa e para quem le a tela.
     */
    @Test
    fun revogacao_observada_apaga_tambem_os_pacotes_da_organizacao() {
        val visao = VisaoDaOrganizacao(escola, listOf(prova), vistaEm = 1_757_000_000_000)
        val pacotes = PacotesFalsos()
        val sessao = DeviceSession(Guardada(escola.id), pacotes, VisoesFalsas(visao), RostersFalsos())
        sessao.abrir(temSessaoGuardada = true)

        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(pessoal)))

        assertEquals(
            listOf(escola.id),
            pacotes.apagadas,
            "a revogacao deixou o gabarito em cache sob a organizacao revogada",
        )
    }

    /**
     * **O roster vai junto na revogacao, e nao espera um logout que pode nunca vir.**
     *
     * Este e o cenario que a auditoria do planejamento achou faltando, e ele e o mais importante dos
     * dois caminhos de apagamento: quem foi removido da escola **nao vai sair do aplicativo** para
     * que o apagamento aconteca. O aparelho descobre a remocao pela resposta do servidor, e e nesse
     * instante que a copia de nome de aluno deixa de ter base para existir ali.
     *
     * Separado do teste dos pacotes e do da visao pela mesma razao que aqueles dois estao separados
     * entre si: mutacoes precisam cair em conjuntos disjuntos para dizerem qual camada segurou.
     *
     * **O usuario nao sai em nenhum momento deste teste** — e isso e a asercao, tanto quanto o que
     * se le no fim. Um teste que chamasse `sair()` aqui passaria mesmo com o apagamento existindo so
     * no outro caminho.
     */
    @Test
    fun revogacao_observada_apaga_o_roster_sem_o_usuario_sair() {
        val visao = VisaoDaOrganizacao(escola, listOf(prova), vistaEm = 1_757_000_000_000)
        val rosters = RostersFalsos()
        rosters.guardar(
            escola.id,
            prova.shortId,
            RosterDaProva(listOf(AlunoDoRoster("tok-a", "Ana")), puxadoEm = 1_757_000_000_000),
        )
        val sessao = DeviceSession(Guardada(escola.id), PacotesFalsos(), VisoesFalsas(visao), rosters)
        sessao.abrir(temSessaoGuardada = true)

        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(pessoal)))

        assertEquals(
            listOf(escola.id),
            rosters.apagadas,
            "a revogacao nao apagou o roster da organizacao certa",
        )
        assertNull(
            rosters.ler(escola.id, prova.shortId),
            "o nome do aluno sobreviveu a revogacao do vinculo, sem o usuario ter saido",
        )
    }

    /**
     * **Resposta sem organizacao nenhuma tambem e revogacao**, e este teste fixa a decisao.
     *
     * Ate esta fatia a lista vazia terminava em `SemOrganizacao` sem tocar no disco. O desfecho de
     * tela continua o mesmo; o que muda e que a condicao do requisito — "o servidor respondeu e a
     * organizacao nao esta mais entre as do usuario" — e satisfeita por uma lista vazia tanto quanto
     * por uma lista que nao a traz. Sem isto, o gabarito ficaria em cache exatamente na revogacao
     * mais dura, a de quem perdeu **todos** os vinculos.
     *
     * Este e o unico dos tres que afirma as duas metades juntas, porque o que ele mede e o **ramo**,
     * e nao qual das duas metades apaga.
     */
    @Test
    fun resposta_sem_organizacao_nenhuma_tambem_e_revogacao() {
        val visao = VisaoDaOrganizacao(escola, listOf(prova), vistaEm = 1_757_000_000_000)
        val guardada = Guardada(escola.id)
        val visoes = VisoesFalsas(visao)
        val pacotes = PacotesFalsos()
        val sessao = DeviceSession(guardada, pacotes, visoes, RostersFalsos())
        sessao.abrir(temSessaoGuardada = true)

        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(emptyList()))

        assertEquals(listOf(escola.id), visoes.apagadas, "a lista vazia nao apagou a visao")
        assertEquals(listOf(escola.id), pacotes.apagadas, "a lista vazia nao apagou os pacotes")
        assertTrue(guardada.organizacaoApagada, "a lista vazia nao derrubou a escolha guardada")
        assertTrue(
            sessao.state is DeviceState.SemOrganizacao,
            "o desfecho de tela mudou junto, e nao era para mudar: ${sessao.state}",
        )
    }

    /**
     * **O canario, e sem ele os tres de cima passariam com "apagar sempre".**
     *
     * O servidor respondeu **trazendo** a organizacao guardada: nada e apagado. Uma implementacao
     * que apagasse a cada consulta bem-sucedida esvaziaria o cache do professor toda vez que ele
     * abrisse o aplicativo com rede — e os tres cenarios de revogacao continuariam verdes, porque
     * eles afirmam que o apagamento aconteceu, nunca que ele foi seletivo.
     */
    @Test
    fun vinculo_que_continua_valendo_nao_apaga_nem_visao_nem_pacote() {
        val visao = VisaoDaOrganizacao(escola, listOf(prova), vistaEm = 1_757_000_000_000)
        val visoes = VisoesFalsas(visao)
        val pacotes = PacotesFalsos()
        val sessao = DeviceSession(Guardada(escola.id), pacotes, visoes, RostersFalsos())
        sessao.abrir(temSessaoGuardada = true)

        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(escola, pessoal)))

        assertTrue(visoes.apagadas.isEmpty(), "apagou a visao de quem ainda tem o vinculo: ${visoes.apagadas}")
        assertTrue(pacotes.apagadas.isEmpty(), "apagou o pacote de quem ainda tem o vinculo: ${pacotes.apagadas}")
        assertTrue(sessao.state is DeviceState.Ativa, "veio ${sessao.state}")
    }

    // --- Atualizar, e o que ela nao pode fazer com a tela (tarefas 6.1 e 6.3) ---

    /**
     * **A metade "nao esvazia" do requisito, sozinha num teste.**
     *
     * Separada da metade "diz que nao conseguiu" pela razao que a 5.2 registrou e a 4.3 pagou: com as
     * duas asercoes juntas, a mutacao que apaga a tela e a que cala o aviso derrubariam o mesmo
     * teste, e o vermelho nao diria qual das duas protecoes segurou.
     */
    @Test
    fun atualizar_que_nao_chega_ao_servidor_nao_esvazia_a_tela() {
        val sessao = comTelaDeTrabalhoCacheada()

        sessao.atualizar()
        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.SemRede)

        val estado = sessao.state
        assertTrue(estado is DeviceState.Ativa, "a tela esvaziou depois de uma atualizacao: $estado")
        assertEquals(escola, (estado as DeviceState.Ativa).organizacao)
        assertEquals(
            Procedencia.Cacheada(1_757_000_000_000),
            estado.procedencia,
            "a idade do dado mudou sem consulta que respondesse",
        )
    }

    /** A outra metade: a tentativa frustrada aparece, e com a causa. */
    @Test
    fun atualizar_que_nao_chega_ao_servidor_diz_que_nao_conseguiu() {
        val sessao = comTelaDeTrabalhoCacheada()

        sessao.atualizar()
        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.SemRede)

        assertEquals(
            FalhaDaConsulta.SEM_REDE,
            (sessao.state as DeviceState.Ativa).falhaAoAtualizar,
            "a tentativa falhou e a tela nao teria como dizer isso",
        )
    }

    /**
     * Atualizar com rede: o dado passa a ser o novo, e a marca de cache **sai** da tela.
     *
     * O aviso da tentativa anterior sai junto, e a asercao e explicita: um aviso que sobrevive a
     * consulta bem-sucedida diria "nao foi possivel atualizar" sobre dado que acabou de chegar.
     */
    @Test
    fun atualizar_que_chega_troca_o_dado_e_tira_a_marca() {
        val sessao = comTelaDeTrabalhoCacheada()
        sessao.atualizar()
        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.SemRede)

        sessao.atualizar()
        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(escola)))

        val estado = sessao.state as DeviceState.Ativa
        assertEquals(Procedencia.Fresca, estado.procedencia)
        assertNull(estado.falhaAoAtualizar, "o aviso da tentativa anterior ficou na tela")
    }

    /**
     * **A guarda da tarefa 3.8 continua fechada**, e este teste e o que garante que atualizar nao a
     * afrouxou: sem pedido, resultado que chega fora de `Consultando` continua descartado.
     */
    @Test
    fun resultado_atrasado_sem_atualizacao_pedida_continua_descartado() {
        val sessao = comTelaDeTrabalhoCacheada()
        val antes = sessao.state

        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.SemRede)

        assertEquals(antes, sessao.state, "um resultado sem pedido reescreveu a tela")
    }

    /**
     * Um pedido vale por **um** resultado.
     *
     * Sem consumir o pedido, o primeiro toque em "atualizar" autorizaria para sempre qualquer
     * resultado atrasado a reescrever a tela — que e exatamente o defeito que a guarda de 3.8 existe
     * para impedir, reintroduzido pela porta dos fundos.
     */
    @Test
    fun um_pedido_de_atualizacao_vale_por_um_resultado_so() {
        val sessao = comTelaDeTrabalhoCacheada()
        sessao.atualizar()
        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(escola)))
        val depois = sessao.state

        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.SemRede)

        assertEquals(depois, sessao.state, "um pedido antigo autorizou um resultado atrasado")
    }

    /** Fora da tela de trabalho nao ha o que atualizar, e o pedido nao abre a guarda. */
    @Test
    fun atualizar_fora_da_tela_de_trabalho_nao_e_aceito() {
        val sessao = DeviceSession(Guardada(), PacotesFalsos(), VisoesFalsas(), RostersFalsos())
        sessao.aoEntrar(ResultadoDaEntrada.CredencialRecusada)
        val antes = sessao.state

        sessao.atualizar()
        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.SemRede)

        assertEquals(antes, sessao.state, "um pedido fora de Ativa reescreveu a entrada")
    }

    /**
     * **Atualizacao frustrada sobre dado fresco nao o faz parecer cacheado.**
     *
     * Este e o cenario que de fato mede "nao esvazia", e o de cima nao mede — descobri isso pela
     * mutacao da 6.3, e fica escrito porque e o tipo de coisa que passa por revisao: quando o dado ja
     * era cacheado, esvaziar a tela e cair na visao **reconstroi um estado igual**, e nenhuma asercao
     * sobre o estado final ve a diferenca. Partindo de dado fresco, ver: cair na visao trocaria
     * `Fresca` por `Cacheada`, e a asercao pega.
     *
     * **A decisao que o cenario fixa, e ela e do tipo que nao pode ficar implicita:** tentativa
     * frustrada **nao envelhece** o dado. `Procedencia` diz de **onde** o dado veio, e nao ha quanto
     * tempo; o que chegou por resposta do servidor nesta sessao continua tendo vindo por resposta do
     * servidor. Quem conta que a tentativa falhou e o aviso, nao a procedencia.
     */
    @Test
    fun atualizar_que_falha_sobre_dado_fresco_nao_o_faz_parecer_cacheado() {
        val visao = VisaoDaOrganizacao(escola, listOf(prova), vistaEm = 1_757_000_000_000)
        val sessao = DeviceSession(Guardada(escola.id), PacotesFalsos(), VisoesFalsas(visao), RostersFalsos())
        sessao.abrir(temSessaoGuardada = true)
        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(escola)))
        val antes = sessao.state as DeviceState.Ativa

        sessao.atualizar()
        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.SemRede)

        val depois = sessao.state
        assertTrue(depois is DeviceState.Ativa, "a tela esvaziou: $depois")
        assertEquals(
            antes,
            (depois as DeviceState.Ativa).copy(falhaAoAtualizar = null),
            "a atualizacao frustrada mudou mais do que o aviso",
        )
        // **Sem asercao sobre o aviso aqui, de proposito.** Quem afirma que o aviso aparece e
        // `atualizar_que_nao_chega_ao_servidor_diz_que_nao_conseguiu`. Juntar as duas coisas neste
        // cenario faria a mutacao "nao poe o aviso" e a mutacao "esvazia a tela" derrubarem o mesmo
        // teste, e o vermelho deixaria de dizer qual das duas protecoes segurou (tarefa 6.3).
    }

    /** A tela de trabalho apresentando visao guardada, que e o ponto de partida das atualizacoes. */
    private fun comTelaDeTrabalhoCacheada(): DeviceSession {
        val visao = VisaoDaOrganizacao(escola, listOf(prova), vistaEm = 1_757_000_000_000)
        val sessao = DeviceSession(Guardada(escola.id), PacotesFalsos(), VisoesFalsas(visao), RostersFalsos())
        sessao.abrir(temSessaoGuardada = true)
        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.SemRede)
        return sessao
    }

    // --- Sair apaga as quatro coisas (tarefa 3.6 da 4a-zero, 4.8 da 4a, e 5.1 desta) ---

    @Test
    fun sair_apaga_a_credencial_e_a_organizacao_escolhida() {
        val guardada = Guardada()
        val sessao = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas(), RostersFalsos())
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
        val sessao = DeviceSession(guardada, pacotes, VisoesFalsas(), RostersFalsos())
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
        val sessao = DeviceSession(guardada, pacotes, VisoesFalsas(), RostersFalsos())

        sessao.sair()

        assertEquals(listOf(escola.id), pacotes.apagadas)
    }

    /**
     * Sem organizacao nenhuma, sair nao tenta apagar coisa nenhuma.
     *
     * Puxar exige organizacao ativa, entao nao ha pacote nem visao guardados; chamar o apagamento
     * com um identificador vazio ou inventado seria pedir para o cache decidir o que fazer com lixo.
     */
    @Test
    fun sair_sem_organizacao_escolhida_nao_apaga_nada() {
        val pacotes = PacotesFalsos()
        val visoes = VisoesFalsas()
        val sessao = DeviceSession(Guardada(), pacotes, visoes, RostersFalsos())

        sessao.sair()

        assertTrue(pacotes.apagadas.isEmpty(), "apagou pacote de ${pacotes.apagadas}")
        assertTrue(visoes.apagadas.isEmpty(), "apagou visao de ${visoes.apagadas}")
    }

    /**
     * Sair apaga a visao, e ela e a **unica** das quatro coisas que aparece em tela.
     *
     * A credencial e a escolha caiam desde a 4a-zero, e o pacote desde a 4a; a visao entrou na lista
     * nesta fatia, e o defeito que ela produziria e mais visivel que o do pacote, nao menos: quem
     * entrasse depois no mesmo aparelho e abrisse sem rede leria o **nome** da organizacao anterior
     * e a lista de provas dela.
     */
    @Test
    fun sair_apaga_a_visao_junto_com_o_resto() {
        val visao = VisaoDaOrganizacao(escola, listOf(prova), vistaEm = 1_757_000_000_000)
        val visoes = VisoesFalsas(visao)
        val sessao = DeviceSession(Guardada(escola.id), PacotesFalsos(), visoes, RostersFalsos())

        sessao.sair()

        assertEquals(listOf(escola.id), visoes.apagadas, "sair nao apagou a visao da organizacao ativa")
        assertNull(visoes.ler(escola.id), "a visao do usuario anterior continua legivel")
    }

    /**
     * **Sair apaga o roster, e ele e o unico dos cinco que e dado pessoal de aluno.**
     *
     * Separado do teste da visao pela razao que aquele ja registra: com as duas asercoes num teste
     * so, a mutacao que tirasse o roster da lista derrubaria o mesmo teste que a que tirasse a
     * visao, e nenhuma das duas diria qual metade segurou. As mutacoes (C) e (D) da tarefa 1.4
     * dependem de os conjuntos serem **disjuntos**.
     *
     * Duas asercoes, e a segunda nao e redundante, como no teste da visao: a primeira diz que o
     * apagamento foi pedido para a organizacao **certa** — apagar o roster da escola que o usuario
     * ainda tem seria defeito com a mesma cara de sucesso —, e a segunda diz que ele surtiu efeito.
     */
    @Test
    fun sair_apaga_o_roster_junto_com_o_resto() {
        val rosters = RostersFalsos()
        rosters.guardar(
            escola.id,
            prova.shortId,
            RosterDaProva(listOf(AlunoDoRoster("tok-a", "Ana")), puxadoEm = 1_757_000_000_000),
        )
        val sessao = DeviceSession(Guardada(escola.id), PacotesFalsos(), VisoesFalsas(), rosters)

        sessao.sair()

        assertEquals(
            listOf(escola.id),
            rosters.apagadas,
            "sair nao apagou o roster da organizacao ativa",
        )
        assertNull(
            rosters.ler(escola.id, prova.shortId),
            "o nome do aluno continua legivel no aparelho depois de sair",
        )
    }

    @Test
    fun a_escolha_do_usuario_anterior_nao_e_herdada_pelo_seguinte() {
        val guardada = Guardada()
        val anterior = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas(), RostersFalsos())
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

        val seguinte = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas(), RostersFalsos())
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
        val sessao = DeviceSession(Guardada(), PacotesFalsos(), VisoesFalsas(), RostersFalsos())
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
        val sessao = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas(), RostersFalsos())
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

    // --- O pendente do outbox: agora fora do alcance desta classe ---

    /**
     * `DeviceSession` **nao conhece o outbox**, e e isso que esta afirmado aqui.
     *
     * A versao anterior destes testes montava um `PendentesFalsos`, chamava `sair`, e conferia que a
     * fila continuava cheia. Aquilo deixou de medir alguma coisa no momento em que a guarda saiu do
     * construtor: hoje a assercao passaria com qualquer implementacao, porque nao ha caminho daqui
     * ate a fila. **Teste que nao pode falhar nao e teste**, e mante-lo verde seria pior do que nao
     * ter — ele daria a impressao de cobrir o que a conferencia em aparelho encontrou quebrado.
     *
     * Quem afirma a preservacao agora e `ApagamentoLocalInstrumentedTest`, com as guardas de verdade
     * e o SQLite de verdade, e a mutacao que apaga o pendente continua derrubando aquele.
     *
     * O que sobrou para esta classe e o que ela de fato faz: repetir no estado o numero que recebeu.
     */
    @Test
    fun sair_leva_para_a_entrada_o_numero_de_pendentes_que_recebeu() {
        val sessao = DeviceSession(
            Guardada(escola.id), PacotesFalsos(), VisoesFalsas(), RostersFalsos(),
        )

        sessao.sair(pendentes = 2)

        val estado = sessao.state as DeviceState.Entrada
        assertEquals(MotivoDeEntrada.SAIU, estado.motivo)
        assertEquals(2, estado.pendentes)
    }

    @Test
    fun sair_sem_nada_pendente_nao_inventa_aviso() {
        val sessao = DeviceSession(
            Guardada(escola.id), PacotesFalsos(), VisoesFalsas(), RostersFalsos(),
        )

        sessao.sair(pendentes = 0)

        assertEquals(0, (sessao.state as DeviceState.Entrada).pendentes)
    }

    /**
     * Sair continua apagando o dado de referencia — a metade que **nao** mudou.
     *
     * Separado do teste do numero de proposito: uma mutacao que tirasse o roster da lista de
     * apagamento tem de derrubar este, e nao aquele.
     */
    @Test
    fun sair_continua_apagando_a_referencia() {
        val rosters = RostersFalsos()
        rosters.guardar(
            escola.id,
            prova.shortId,
            RosterDaProva(listOf(AlunoDoRoster("tok-a", "Ana")), puxadoEm = 1_757_000_000_000),
        )
        val sessao = DeviceSession(
            Guardada(escola.id), PacotesFalsos(), VisoesFalsas(), rosters,
        )

        sessao.sair(pendentes = 1)

        assertEquals(listOf(escola.id), rosters.apagadas)
        assertNull(rosters.ler(escola.id, prova.shortId))
    }
}
