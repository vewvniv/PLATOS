package com.platos.android.scan

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.platos.domain.scoring.PartialScore
import com.platos.domain.scoring.PontuacaoDada
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A tela de nota (`slice-5c-3-a-nota-no-aparelho`, spec `scan-session`): a imagem de cada discursiva com o numero
 * impresso da questao, o aviso de desvio quando sinalizado, o que ela vale e um campo decimal. O total aparece antes
 * de gravar, e gravar pede confirmacao.
 *
 * Toda a regra de leitura esta em [interpretarEntrada]; a tela so a desenha. [erro] e a recusa que a sessao ou a
 * gravacao devolveram. A tela nao altera a imagem e nao a envia.
 */
@Composable
fun NotaTela(
    caderno: Caderno,
    parcial: PartialScore,
    respostas: RespostasGuardadas,
    erro: String?,
    onGravar: (List<PontuacaoDada>) -> Unit,
    onVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onVoltar)

    val linhas = remember(caderno, parcial) { linhasDaNota(caderno, parcial) }
    val textos = remember { mutableStateMapOf<String, String>() }
    var confirmando by remember { mutableStateOf(false) }
    val entrada = interpretarEntrada(linhas, textos, parcial.objectivePoints)

    Column(
        modifier = modifier.fillMaxSize().background(Color.Black).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Dar a nota", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        for (linha in linhas) {
            Text("Questao ${linha.rotulo} (vale ${linha.worth})", color = Color.White, fontSize = 18.sp)
            val resposta = linha.resposta
            if (resposta != null) {
                ImagemDaResposta(resposta.arquivo, respostas)
                if (resposta.desvioSinalizado) {
                    Text(ScanState.ProvaComDiscursiva.AVISO_DE_DESVIO, color = Color(0xFFF9A825), fontSize = 14.sp)
                }
            } else {
                Text("A resposta nao esta mais neste aparelho.", color = Color.White, fontSize = 14.sp)
            }
            OutlinedTextField(
                value = textos[linha.questionId].orEmpty(),
                onValueChange = {
                    textos[linha.questionId] = it
                    confirmando = false
                },
                label = { Text("Pontuacao de 0 a ${linha.worth}, com ponto decimal") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        when (entrada) {
            is EntradaDaNota.Invalida -> Text(entrada.motivo, color = Color.White, fontSize = 14.sp)
            is EntradaDaNota.Valida ->
                Text("Total: ${entrada.total} de ${parcial.maxScore}", color = Color.White, fontSize = 18.sp)
        }
        if (erro != null) Text(erro, color = Color(0xFFF9A825), fontSize = 14.sp)

        if (entrada is EntradaDaNota.Valida && confirmando) {
            Text(
                "Confirmar a nota? Depois de gravada, ela nao se altera neste aparelho.",
                color = Color.White,
                fontSize = 16.sp,
            )
            Button(onClick = { onGravar(entrada.pontuacoes) }) { Text("Confirmar e gravar") }
        } else {
            Button(onClick = { confirmando = true }, enabled = entrada is EntradaDaNota.Valida) {
                Text("Conferir o total")
            }
        }
        Button(onClick = onVoltar) { Text("Voltar") }
    }
}

@Composable
private fun ImagemDaResposta(arquivo: String, respostas: RespostasGuardadas) {
    val bitmap by produceState<Bitmap?>(null, arquivo) {
        value = withContext(Dispatchers.IO) { carregarResposta(respostas, arquivo) }
    }
    val pronto = bitmap
    if (pronto == null) {
        Text("Abrindo a resposta…", color = Color.White, fontSize = 14.sp)
    } else {
        Image(bitmap = pronto.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth())
    }
}
