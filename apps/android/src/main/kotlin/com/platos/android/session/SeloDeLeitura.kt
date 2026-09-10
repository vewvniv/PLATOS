package com.platos.android.session

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * O selo de leitura cacheada: fundo, borda, rotulo em destaque e a idade ao lado.
 *
 * **Fundo e borda sao o requisito, e nao enfeite.** A regra diz que dado vindo da visao guardada e
 * marcado de forma **visualmente distinta**, e nao apenas por uma frase no meio do texto — frase some
 * na leitura de quem esta em pe numa sala com trinta alunos, e a consequencia de nao perceber e
 * escanear uma turma com a lista de provas de ontem.
 *
 * **Decisao desta tarefa**, que o `design.md` deixou aberta ("qual marca visual, exatamente"): selo
 * com `errorContainer` do tema. Sobre as alternativas: cor sozinha nao serve para quem nao a
 * distingue, entao o rotulo em maiusculas carrega a mesma informacao em texto; e `errorContainer`, e
 * nao `tertiary`, porque o estado **e** um problema para quem trabalha — o aparelho esta sem
 * conexao —, ainda que nao seja uma falha do aplicativo.
 *
 * Um unico selo para as tres telas: a marca precisa ser a **mesma** em todas, senao a tela nova
 * inventa a sua e o professor aprende duas linguagens. Nao e abstracao prematura — sao tres
 * consumidores hoje (trabalho, escolha da prova, organizacao sem prova).
 */
@Composable
fun SeloDeLeitura(marca: MarcaDeLeitura, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.error,
                shape = RoundedCornerShape(8.dp),
            ),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = marca.rotulo,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = marca.idade,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/**
 * O aviso de que a atualizacao pedida nao deu.
 *
 * **Distinto do selo de proposito.** O selo fala do **dado** — de quando ele e —, e este fala da
 * ultima **tentativa**. Junta-los faria a tela dizer "sem conexao desde ontem as 14:20" quando o que
 * houve foi uma tentativa frustrada agora, sobre dado que pode ser de um minuto atras.
 *
 * A frase vem pronta, por [avisoDeAtualizacao]. Escolher texto aqui dentro poria a regra na tela,
 * onde nenhum teste desta base a alcanca — a lacuna que a tarefa 6.4 registra.
 */
@Composable
fun AvisoDeAtualizacao(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.fillMaxWidth(),
    )
}
