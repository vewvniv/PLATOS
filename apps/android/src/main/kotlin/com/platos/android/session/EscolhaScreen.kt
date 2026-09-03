package com.platos.android.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A escolha de organizacao, quando ha mais de uma.
 *
 * **Esta tela nao decide que ha escolha a fazer.** Quem decide e `DeviceSession`: uma organizacao so
 * fica ativa sem perguntar, e escolha guardada que saiu da lista volta a ser pedida. Os tres
 * cenarios sao da tarefa 3.5 e rodam na JVM. Aqui so se desenha `DeviceState.Escolhendo`, e chegar
 * a esta tela ja significa que a pergunta faz sentido.
 *
 * Os nomes vem da API, e nao ha ordenacao propria: a ordem e a que o servidor mandou, e reordenar
 * aqui faria a tela discordar do que `ApiPlatosTest` afirma sobre a resposta.
 */
@Composable
fun EscolhaScreen(
    organizacoes: List<Organizacao>,
    onEscolher: (Organizacao) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text(text = "Escolha a organizacao", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "Voce participa de mais de uma. A escolha fica guardada neste aparelho.",
            modifier = Modifier.padding(top = 8.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(organizacoes, key = { it.id }) { organizacao ->
                OutlinedButton(
                    onClick = { onEscolher(organizacao) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(organizacao.nome)
                }
            }
        }
    }
}
