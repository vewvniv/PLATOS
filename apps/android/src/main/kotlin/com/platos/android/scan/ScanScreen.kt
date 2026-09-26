package com.platos.android.scan

import com.platos.android.roster.RosterDaProva
import com.platos.android.session.SeloDeLeitura
import java.time.ZoneId

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.platos.domain.scoring.ObjectiveScore
import com.platos.domain.scoring.PartialScore
import com.platos.domain.scoring.PartialScoringOutcome

/**
 * A tela, e e uma so: preview ocupando a area, faixa de estado embaixo, resultado por cima.
 *
 * Sem navegacao e sem tema proprio. O que ela desenha e [ScanState] inteiro — nao ha estado da tela
 * ao lado do estado da sessao, porque dois estados que descrevem a mesma coisa divergem no dia em
 * que um dos dois esquecer de mudar.
 */
@Composable
fun ScanScreen(
    state: ScanState,
    roster: RosterDaProva?,
    onPedirPermissao: () -> Unit,
    onRetomar: () -> Unit,
    onPreviewCriado: (PreviewView) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (state !is ScanState.NoPermission) {
            AndroidView(
                factory = { contexto ->
                    PreviewView(contexto).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        onPreviewCriado(this)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        when (state) {
            is ScanState.NoPermission -> SemPermissao(onPedirPermissao)
            is ScanState.Searching -> Faixa("Procurando a folha…")
            is ScanState.NotRead -> Faixa("Achei a folha e nao consegui ler: ${state.reason}")
            is ScanState.Rejected -> Resultado(onRetomar) {
                Text("Folha recusada", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(state.reason, fontSize = 16.sp)
            }
            is ScanState.ProvaComDiscursiva -> Resultado(onRetomar) {
                DeQuemE(idAlunoDaFolha(state.aluno, roster, ZoneId.systemDefault()))
                Text("Prova com discursiva", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                when (val parcial = state.parcial) {
                    is PartialScoringOutcome.Scored -> Parcial(parcial.partial)
                    is PartialScoringOutcome.Rejected ->
                        Text("Parcial nao apurada: ${parcial.reason}", fontSize = 16.sp)
                    null -> Unit
                }
                CadernoDaFolha(state.caderno)
                Text(ScanState.ProvaComDiscursiva.AVISO, fontSize = 16.sp)
            }
            is ScanState.Scored -> Resultado(onRetomar) {
                DeQuemE(idAlunoDaFolha(state.payload.studentToken, roster, ZoneId.systemDefault()))
                Nota(state.score)
            }
        }
    }
}

/** A faixa de estado: uma linha, embaixo, sem cobrir o preview. */
@Composable
private fun BoxScope.Faixa(texto: String) {
    Text(
        texto,
        color = Color.White,
        fontSize = 16.sp,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .padding(16.dp),
    )
}

@Composable
private fun BoxScope.SemPermissao(onPedirPermissao: () -> Unit) {
    Column(
        modifier = Modifier.align(Alignment.Center).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "A camera le a folha de respostas no proprio aparelho: ela e a unica entrada da " +
                "leitura, e nada sai daqui.",
            color = Color.White,
            fontSize = 18.sp,
        )
        Button(onClick = onPedirPermissao) { Text("Permitir o uso da camera") }
    }
}

/** O resultado, sobre o preview. Ocupa a parte de baixo e nao esconde a folha. */
@Composable
private fun BoxScope.Resultado(
    onRetomar: () -> Unit,
    conteudo: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Color(0xEE000000))
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            conteudo()
            Button(onClick = onRetomar) { Text("Escanear outra folha") }
        }
    }
}

/**
 * De quem e a folha, acima da nota.
 *
 * **Acima, e nao abaixo**: a pergunta que o professor faz ao ver o resultado e "de quem e", e a nota
 * sem dono e a situacao que esta fatia existe para acabar.
 *
 * Quem decide o que apresentar e [idAlunoDaFolha]; aqui so se desenha. O selo e o mesmo
 * [SeloDeLeitura] da tela de trabalho — a regra de marcar dado guardado mora em `device-session`, e
 * duas marcas para a mesma regra divergiriam na primeira mudanca.
 */
@Composable
private fun DeQuemE(identidade: IdAlunoDaFolha) {
    when (identidade) {
        is IdAlunoDaFolha.Nomeada ->
            Text(identidade.nome, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        is IdAlunoDaFolha.ForaDoRoster -> {
            Text(identidade.token, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "Esta folha nao esta no roster desta prova. A nota abaixo foi apurada normalmente.",
                fontSize = 14.sp,
            )
        }
    }

    identidade.marca?.let { SeloDeLeitura(it) }
}

/** A nota, e o que ela nao fecha. Quem decide o texto e [apresentar]; aqui so se desenha. */
@Composable
private fun Nota(score: ObjectiveScore) {
    val nota = apresentar(score)

    Text(nota.pontuacao, fontSize = 32.sp, fontWeight = FontWeight.Bold)
    Text(nota.resumo, fontSize = 16.sp)
    for (pendencia in nota.pendencias) {
        Text(pendencia, fontSize = 14.sp)
    }
}

/** A parcial objetiva, e o que ainda falta corrigir. Quem decide o texto e [apresentar]. */
@Composable
private fun Parcial(parcial: PartialScore) {
    val apresentada = apresentar(parcial)

    Text(apresentada.pontuacao, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text(apresentada.resumo, fontSize = 16.sp)
    for (pendencia in apresentada.pendencias) {
        Text(pendencia, fontSize = 14.sp)
    }
}

/**
 * O caderno do aluno (§8): um indicador por regiao esperada, e quantas estao capturadas.
 *
 * Verde e capturada, ambar e com problema, cinza e nao vista. **A cor nao e a unica marca**: cada
 * indicador tambem diz o estado em texto, e cada regiao com problema tem uma linha com o motivo.
 */
@Composable
private fun CadernoDaFolha(caderno: Caderno) {
    if (caderno.esperadas == 0) return

    Text("${caderno.capturadas} de ${caderno.esperadas} regioes capturadas", fontSize = 16.sp)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (regiao in caderno.regioes) {
            val (fundo, estado) = when (regiao.estado) {
                EstadoDaRegiao.Capturada -> Color(0xFF2E7D32) to "ok"
                is EstadoDaRegiao.ComProblema -> Color(0xFFF9A825) to "problema"
                EstadoDaRegiao.NaoVista -> Color(0xFF616161) to "falta"
            }
            Text(
                "${regiao.rotulo} · $estado",
                color = if (regiao.estado is EstadoDaRegiao.ComProblema) Color.Black else Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .background(fundo, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
    for (regiao in caderno.regioes) {
        val estado = regiao.estado
        if (estado is EstadoDaRegiao.ComProblema) {
            Text("${regiao.rotulo}: ${estado.motivo}", fontSize = 14.sp)
        }
    }
}

/**
 * O escaneamento nao abriu porque nao ha pacote conferido para esta prova.
 *
 * Nao ha caminho de reserva a oferecer: sem pacote conferido nao se escaneia (ADR-0013, decisao 5).
 * A unica acao e voltar e escolher de novo, que refaz o gate.
 */
@Composable
fun SemPacoteScreen(onVoltar: () -> Unit, modifier: Modifier = Modifier) =
    EscaneamentoNaoAbreScreen(MotivoDeNaoAbrir.PACOTE_NAO_CONFERIDO, onVoltar, modifier)

/**
 * A recusa de abrir o escaneamento, com **a frase do motivo**.
 *
 * **Duas frases, e nao uma parametrizada por severidade.** Cada motivo pede uma acao diferente:
 * pacote nao conferido se resolve baixando a prova de novo; prova nao identificada **nao** se
 * resolve assim — o pacote pode estar no lugar, e mandar baixar de novo seria sugerir a acao errada.
 * A frase e o que chega a quem segura o aparelho, e ela precisa dizer o que fazer.
 */
@Composable
fun EscaneamentoNaoAbreScreen(
    motivo: MotivoDeNaoAbrir,
    onVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = frasePara(motivo))
        Button(onClick = onVoltar, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            Text("Voltar")
        }
    }
}

/** A frase de cada motivo, num lugar so, para que a tela e o teste concordem sobre qual e qual. */
fun frasePara(motivo: MotivoDeNaoAbrir): String = when (motivo) {
    MotivoDeNaoAbrir.PACOTE_NAO_CONFERIDO ->
        "A prova nao esta mais conferida neste aparelho, entao a camera nao foi aberta. " +
            "Volte e escolha a prova de novo para baixa-la."

    MotivoDeNaoAbrir.PROVA_NAO_IDENTIFICADA ->
        "Este aparelho nao soube dizer de qual prova era este escaneamento, entao a camera nao foi " +
            "aberta e nenhuma folha foi lida. A prova continua baixada; volte e abra o " +
            "escaneamento de novo a partir dela."
}
