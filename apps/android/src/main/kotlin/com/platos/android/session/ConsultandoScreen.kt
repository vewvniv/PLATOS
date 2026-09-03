package com.platos.android.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Autenticado, e as organizacoes ainda nao chegaram.
 *
 * Sem acao, porque nao ha o que fazer alem de esperar — e a espera **termina**. Toda consulta acaba
 * num dos quatro resultados, e desde a tarefa 4.7 isso vale tambem para servidor que aceita a
 * conexao e nunca responde: o tempo limite estoura e o caso cai em `SemRede`. Sem aquele plugin,
 * esta tela seria o carregamento sem desfecho que a spec proibe.
 */
@Composable
fun ConsultandoScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(text = "Buscando suas organizacoes", modifier = Modifier.padding(top = 16.dp))
    }
}
