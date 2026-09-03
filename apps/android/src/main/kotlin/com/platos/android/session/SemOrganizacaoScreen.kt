package com.platos.android.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Autenticado, e sem saber a organizacao.
 *
 * **Ela recebe [texto] pronto, e nao a falha.** A diferenca e a garantia: com a falha na mao, esta
 * funcao escolheria frase — e escolher frase e onde um nome de reserva aparece. Recebendo o texto,
 * ela nao computa nada, e o que ela pode dizer e exatamente o que `textoSemOrganizacao` produz, que
 * e verificavel na JVM.
 *
 * O estado existe para que a tela nao precise escolher entre duas mentiras: voltar a entrada, que
 * diria que a credencial e o problema, ou desenhar trabalho sem nome. A tarefa 3.4 viu falhar o
 * defeito equivalente uma camada abaixo — nome inventado e indistinguivel de nome verdadeiro para
 * quem le, e por isso nenhuma das duas camadas pode inventar.
 */
@Composable
fun SemOrganizacaoScreen(
    texto: TextoSemOrganizacao,
    onTentarDeNovo: () -> Unit,
    onSair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = texto.titulo, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(text = texto.explicacao, modifier = Modifier.padding(top = 12.dp))

        Button(
            onClick = onTentarDeNovo,
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        ) {
            Text("Tentar de novo")
        }

        TextButton(
            onClick = onSair,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text("Sair")
        }
    }
}
