package com.platos.android.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A escolha da prova a escanear, entre as que a API apresentou.
 *
 * **Esta tela nao decide nada.** Quem decide e [PreparoDaProva]: lista vazia contra falha de
 * consulta, o que e escolhivel, e o que o gate faz depois. Aqui so se desenha
 * [EstadoDaProva.Escolhendo].
 *
 * **Nao ha campo para digitar identificador de prova**, e a ausencia e o requisito: a tela nunca
 * oferece prova que a consulta nao devolveu. E o mesmo principio pelo qual o nome da organizacao vem
 * da API — se a rota falhar, nao ha de onde inventar.
 *
 * Sem ordenacao propria: a ordem e a que o servidor mandou.
 */
@Composable
fun EscolhaDaProvaScreen(
    provas: List<ProvaPublicada>,
    onEscolher: (ProvaPublicada) -> Unit,
    onSair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text(text = "Escolha a prova", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "O aparelho baixa a prova escolhida e confere antes de abrir a camera.",
            modifier = Modifier.padding(top = 8.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(provas, key = { it.shortId }) { prova ->
                OutlinedButton(
                    onClick = { onEscolher(prova) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("${prova.titulo}  ·  ${prova.shortId}")
                }
            }
        }

        TextButton(onClick = onSair, modifier = Modifier.fillMaxWidth()) {
            Text("Sair")
        }
    }
}

/**
 * A organizacao ativa nao tem prova publicada.
 *
 * **Nao e a mesma tela de consulta que falhou**, e a separacao e o requisito: esta afirma uma coisa
 * sobre o mundo — nao ha prova —, e a outra afirma que nao foi possivel saber.
 */
@Composable
fun SemProvaScreen(
    onTentarDeNovo: () -> Unit,
    onSair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Esta organizacao ainda nao tem prova publicada. " +
                "Publique uma prova antes de escanear folhas.",
        )

        Button(onClick = onTentarDeNovo, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            Text("Procurar de novo")
        }
        TextButton(onClick = onSair, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Sair")
        }
    }
}

/**
 * O escaneamento nao pode abrir, e a tela diz por que.
 *
 * **A frase vem por parametro, ja escolhida** por `textoDaBarragem`/`textoDaListagem`. Escolher
 * texto aqui dentro poria a regra na tela, onde nenhum teste de JVM a alcanca — foi o defeito que a
 * tarefa 5.4b da fatia 4a-zero registrou como lacuna, e a forma de nao repeti-lo e esta.
 */
@Composable
fun BarragemScreen(
    titulo: String,
    texto: String,
    onTentarDeNovo: () -> Unit,
    onVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = titulo, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = texto, modifier = Modifier.padding(top = 12.dp))

        Button(onClick = onTentarDeNovo, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            Text("Tentar de novo")
        }
        TextButton(onClick = onVoltar, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Escolher outra prova")
        }
    }
}

/** O preparo em curso: baixando ou conferindo. Nao fica sem desfecho — sempre vira outro estado. */
@Composable
fun PreparandoScreen(prova: ProvaPublicada, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = prova.titulo, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = "Preparando a prova…", modifier = Modifier.padding(top = 12.dp))
    }
}
