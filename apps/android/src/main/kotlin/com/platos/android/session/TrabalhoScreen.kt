package com.platos.android.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A tela de trabalho: a organizacao ativa, o caminho para escanear, e sair.
 *
 * **O nome vem de [Organizacao], que veio da API.** Nao ha nome de reserva nem texto de espera no
 * lugar dele: quando a consulta nao devolve, o estado nao e `Ativa` e esta tela nao e alcancada. A
 * tarefa 3.4 viu falhar exatamente esse defeito — nome inventado e indistinguivel de nome
 * verdadeiro para quem le.
 *
 * Sair apaga a credencial **e** a organizacao escolhida, e quem faz isso e `DeviceSession.sair()`.
 * Esta tela so oferece a acao; a tarefa 3.7 registra por que as duas coisas somem juntas.
 *
 * **[marca] e [aviso] chegam prontos**, por `marcaDeLeitura` e `avisoDeAtualizacao`. Nao ha `if` de
 * regra aqui dentro: nulo nao desenha, nao-nulo desenha. Escolher a frase ou decidir o que conta
 * como cacheado nesta funcao poria a regra onde nenhum teste desta base chega — a lacuna da tarefa
 * 6.4, e o defeito que produziu 9b.1 e 9b.2 na fatia anterior.
 *
 * **A acao de atualizar e explicita**, e nao um gesto escondido: quem trabalha sem rede precisa de um
 * lugar visivel para dizer "tenta de novo agora", e o requisito pede uma acao, nao um recarregamento
 * automatico que o professor nao controla.
 */
@Composable
fun TrabalhoScreen(
    organizacao: Organizacao,
    marca: MarcaDeLeitura?,
    aviso: String?,
    onEscanear: () -> Unit,
    onAtualizar: () -> Unit,
    onSair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // O selo vem **antes** do nome, e nao depois: quem le a tela de cima para baixo precisa
        // saber que o dado e cacheado antes de ler o dado.
        if (marca != null) SeloDeLeitura(marca, modifier = Modifier.padding(bottom = 16.dp))

        Text(text = organizacao.nome, fontSize = 24.sp, fontWeight = FontWeight.Bold)

        if (aviso != null) AvisoDeAtualizacao(aviso, modifier = Modifier.padding(top = 12.dp))

        Button(
            onClick = onEscanear,
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        ) {
            Text("Escanear folha")
        }

        OutlinedButton(
            onClick = onAtualizar,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text("Atualizar")
        }

        TextButton(
            onClick = onSair,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text("Sair")
        }
    }
}
