package com.platos.android.corpus

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * O arquivo de dados de uma amostra do corpus (`slice-5d-corpus-de-medicao`, spec `measurement-corpus`).
 *
 * **Contrato digitado uma vez (P28).** Quem le este arquivo depois — a bancada, em outra linguagem — o faz por
 * `tools/corpus/`, e o literal que prende os dois lados e `fixtures/corpus/amostra-exemplo.json`: o teste da coleta
 * compara a serializacao com ele, e `tools/corpus` le as chaves dele. Mudar um campo aqui sem mudar o literal reprova
 * os dois.
 *
 * **Nao tem nome, turma, matricula, token, captura, caminho de arquivo, data nem hora**, e nao ha onde pô-los: a
 * garantia e de tipo. `referencia` e `descartar` nascem vazios e o mantenedor os preenche no computador.
 * `pontos` e a forma canonica de `Pontos` (`"1.50"`), decimal exata como texto.
 */
@Serializable
data class AmostraDoCorpus(
    val versao: Int = VERSAO,
    val pontos: String,
    val maximo: Int,
    val pacote: String,
    val item: String,
    val referencia: String? = null,
    val descartar: Boolean = false,
) {
    companion object {
        const val VERSAO = 1

        /** `encodeDefaults`: sem ele `versao`, `referencia` e `descartar` nao sairiam no arquivo. */
        val json = Json { encodeDefaults = true }
    }
}
