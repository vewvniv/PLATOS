package com.platos.android.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A resposta guardada de uma regiao discursiva, em tela cheia
 * (`slice-5c-1-a-resposta-fica-no-aparelho`, design, decisao 4).
 *
 * A imagem e a que o recorte entregou, **sem alteracao**: a tela nao a altera e nao mostra a faixa de fora da
 * area, que nao sai do recorte. O aviso de desvio aparece **so** quando [desvioSinalizado], com o texto unico de
 * [ScanState.ProvaComDiscursiva.AVISO_DE_DESVIO], **abaixo** da imagem e sem cobri-la.
 *
 * A imagem e decodificada fora do fio principal ([carregarResposta]). Enquanto ela nao chega, ou se o arquivo sumiu, a
 * tela diz isso em texto: nunca uma imagem em branco.
 */
@Composable
fun RespostaTela(
    rotulo: String,
    arquivo: String,
    desvioSinalizado: Boolean,
    respostas: RespostasGuardadas,
    onRefazer: () -> Unit,
    onVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onVoltar)

    // Nulo e "ainda nao chegou" ou "nao existe"; o par distingue os dois sem um segundo estado.
    val carregada by produceState<Carga>(Carga.Carregando, arquivo) {
        value = withContext(Dispatchers.IO) { carregarResposta(respostas, arquivo) }?.let { Carga.Pronta(it) }
            ?: Carga.Ausente
    }

    Column(
        modifier = modifier.fillMaxSize().background(Color.Black).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Resposta da questao $rotulo", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        when (val c = carregada) {
            Carga.Carregando -> Text("Abrindo a resposta…", color = Color.White, fontSize = 16.sp)
            Carga.Ausente -> Text("A resposta nao esta mais neste aparelho.", color = Color.White, fontSize = 16.sp)
            is Carga.Pronta -> Image(
                bitmap = c.bitmap.asImageBitmap(),
                // A descricao diz o tamanho: e o que a arvore de acessibilidade mostra (e o teste confere
                // contra o arquivo), e nao altera a imagem.
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .semantics { contentDescription = descricaoDaImagem(rotulo, c.bitmap.width, c.bitmap.height) },
            )
        }

        if (desvioSinalizado) {
            Text(ScanState.ProvaComDiscursiva.AVISO_DE_DESVIO, color = Color(0xFFF9A825), fontSize = 16.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRefazer) { Text("Refazer") }
            Button(onClick = onVoltar) { Text("Voltar") }
        }
    }
}

private sealed interface Carga {
    data object Carregando : Carga
    data object Ausente : Carga
    class Pronta(val bitmap: Bitmap) : Carga
}

/** O que a arvore de acessibilidade diz da imagem: a questao e o tamanho, em pixels, do PNG guardado. */
fun descricaoDaImagem(rotulo: String, largura: Int, altura: Int): String =
    "Resposta da questao $rotulo: imagem de $largura por $altura pixels"

/** Decodifica o PNG guardado. Nulo quando o arquivo nao existe ou nao e uma imagem. */
fun carregarResposta(respostas: RespostasGuardadas, arquivo: String): Bitmap? =
    respostas.ler(arquivo)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
