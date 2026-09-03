package com.platos.android.session

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

    private val escola = Organizacao("org-1", "Escola Municipal Vila Nova")
    private val pessoal = Organizacao("org-2", "Leon")

    // --- Os tres estados de falha, distintos (tarefa 3.2) ---

    @Test
    fun credencial_recusada_fica_na_entrada_dizendo_isso() {
        val sessao = DeviceSession(Guardada())
        sessao.aoEntrar(ResultadoDaEntrada.CredencialRecusada)

        val estado = sessao.state as DeviceState.Entrada
        assertEquals(MotivoDeEntrada.CREDENCIAL_RECUSADA, estado.motivo)
    }

    @Test
    fun sem_rede_na_entrada_nao_e_apresentado_como_credencial_recusada() {
        val sessao = DeviceSession(Guardada())
        sessao.aoEntrar(ResultadoDaEntrada.SemRede)

        val estado = sessao.state as DeviceState.Entrada
        assertEquals(MotivoDeEntrada.SEM_REDE, estado.motivo)
    }

    @Test
    fun sessao_expirada_volta_para_a_entrada_no_momento_em_que_e_detectada() {
        val guardada = Guardada("org-1")
        val sessao = DeviceSession(guardada)
        sessao.abrir(temSessaoGuardada = true)
        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.SessaoExpirada)

        val estado = sessao.state as DeviceState.Entrada
        assertEquals(MotivoDeEntrada.SESSAO_EXPIRADA, estado.motivo)
        assertTrue(guardada.credencialApagada, "sessao expirada tem de descartar a credencial")
    }

    @Test
    fun os_tres_motivos_de_falha_sao_distintos_entre_si() {
        fun motivoDe(resultado: ResultadoDaEntrada): MotivoDeEntrada? {
            val sessao = DeviceSession(Guardada())
            sessao.aoEntrar(resultado)
            return (sessao.state as DeviceState.Entrada).motivo
        }

        val expirada = DeviceSession(Guardada()).let {
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
        val sessao = DeviceSession(Guardada())
        sessao.aoEntrar(ResultadoDaEntrada.Autenticado)
        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(escola)))

        val estado = sessao.state as DeviceState.Ativa
        assertEquals(escola.nome, estado.organizacao.nome)
    }

    @Test
    fun consulta_que_falha_nao_produz_nome_nenhum() {
        val sessao = DeviceSession(Guardada())
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
        val primeira = DeviceSession(guardada)
        primeira.aoEntrar(ResultadoDaEntrada.Autenticado)
        primeira.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(escola)))
        val antes = (primeira.state as DeviceState.Ativa).organizacao

        val segunda = DeviceSession(guardada)
        segunda.abrir(temSessaoGuardada = true)
        segunda.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(escola)))

        assertEquals(antes, (segunda.state as DeviceState.Ativa).organizacao)
    }

    // --- A escolha de organizacao (tarefa 3.5) ---

    @Test
    fun duas_organizacoes_pedem_escolha() {
        val sessao = DeviceSession(Guardada())
        sessao.aoEntrar(ResultadoDaEntrada.Autenticado)
        sessao.aoConsultarOrganizacoes(
            ResultadoDasOrganizacoes.Chegaram(listOf(escola, pessoal)),
        )

        val estado = sessao.state as DeviceState.Escolhendo
        assertEquals(listOf(escola, pessoal), estado.organizacoes)
    }

    @Test
    fun uma_organizacao_so_nao_pede_escolha() {
        val sessao = DeviceSession(Guardada())
        sessao.aoEntrar(ResultadoDaEntrada.Autenticado)
        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(pessoal)))

        assertEquals(pessoal, (sessao.state as DeviceState.Ativa).organizacao)
    }

    @Test
    fun a_escolha_sobrevive_ao_fechamento_do_aplicativo() {
        val guardada = Guardada()
        val antes = DeviceSession(guardada)
        antes.aoEntrar(ResultadoDaEntrada.Autenticado)
        antes.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(escola, pessoal)))
        antes.escolher(pessoal)

        val depois = DeviceSession(guardada)
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
        val sessao = DeviceSession(guardada)
        sessao.abrir(temSessaoGuardada = true)
        sessao.aoConsultarOrganizacoes(
            ResultadoDasOrganizacoes.Chegaram(listOf(escola, pessoal)),
        )

        assertTrue(sessao.state is DeviceState.Escolhendo, "veio ${sessao.state}")
    }

    // --- Sair apaga as duas coisas (tarefa 3.6) ---

    @Test
    fun sair_apaga_a_credencial_e_a_organizacao_escolhida() {
        val guardada = Guardada()
        val sessao = DeviceSession(guardada)
        sessao.aoEntrar(ResultadoDaEntrada.Autenticado)
        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(escola, pessoal)))
        sessao.escolher(escola)

        sessao.sair()

        assertTrue(guardada.credencialApagada, "sair nao apagou a credencial")
        assertTrue(guardada.organizacaoApagada, "sair nao apagou a organizacao escolhida")
        assertEquals(MotivoDeEntrada.SAIU, (sessao.state as DeviceState.Entrada).motivo)
    }

    @Test
    fun a_escolha_do_usuario_anterior_nao_e_herdada_pelo_seguinte() {
        val guardada = Guardada()
        val anterior = DeviceSession(guardada)
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

        val seguinte = DeviceSession(guardada)
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
        val sessao = DeviceSession(Guardada())
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
        val sessao = DeviceSession(guardada)
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
