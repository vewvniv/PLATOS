package com.platos.android.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A tela de entrada: e-mail, senha, e a faixa que diz por que estamos aqui.
 *
 * Sem estado de sessao proprio. [motivo] vem de `DeviceState.Entrada`, e o texto da faixa vem de
 * [mensagemDeEntrada] — esta funcao nao escolhe frase nenhuma, so desenha a que recebeu. E a mesma
 * fronteira de `ScanScreen`.
 *
 * **[enviando] nao e estado de sessao, e por isso nao esta em `DeviceState`.** A maquina de estados
 * descreve o que o aparelho sabe; "ha um pedido no ar" e afordancia de tela, e duplica-la la
 * obrigaria a maquina a conhecer o tempo. O que o requisito exige — nao ficar em carregamento sem
 * desfecho — e garantido do outro lado: toda tentativa termina em `aoEntrar`, com um dos tres
 * resultados, e nenhum deles deixa a tela em [enviando].
 *
 * O e-mail sobrevive a rotacao e a senha nao. Redigitar a senha depois de girar o aparelho e chato;
 * guardar senha em `savedInstanceState` a poe num Bundle que o sistema serializa, e essa e a mesma
 * troca da decisao 5 num lugar menor.
 */
@Composable
fun EntradaScreen(
    motivo: MotivoDeEntrada?,
    enviando: Boolean,
    onEntrar: (email: String, senha: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }

    val podeEnviar = !enviando && email.isNotBlank() && senha.isNotBlank()

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "PLATOS", fontSize = 28.sp, fontWeight = FontWeight.Bold)

        mensagemDeEntrada(motivo)?.let { faixa ->
            Text(
                text = faixa,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail") },
            singleLine = true,
            enabled = !enviando,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        )

        OutlinedTextField(
            value = senha,
            onValueChange = { senha = it },
            label = { Text("Senha") },
            singleLine = true,
            enabled = !enviando,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )

        Button(
            onClick = { onEntrar(email.trim(), senha) },
            enabled = podeEnviar,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        ) {
            if (enviando) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            Text(if (enviando) "Entrando..." else "Entrar")
        }
    }
}
