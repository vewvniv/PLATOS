package com.platos.android.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
 */
@Composable
fun TrabalhoScreen(
    organizacao: Organizacao,
    onEscanear: () -> Unit,
    onSair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = organizacao.nome, fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Button(
            onClick = onEscanear,
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        ) {
            Text("Escanear folha")
        }

        TextButton(
            onClick = onSair,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text("Sair")
        }
    }
}
