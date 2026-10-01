package com.platos.android.outbox

import com.platos.domain.scoring.NotaDoProfessor

/**
 * Qual rota leva um pendente (`slice-5c-3-a-nota-no-aparelho`, design D2). `resultado` e a rota antiga, que leva a
 * nota objetiva completa e a parcial; `nota` e `POST .../results/graded`, a nota do professor.
 */
enum class RotaDoEnvio(val valor: String) {
    RESULTADO("resultado"),
    NOTA("nota");

    companion object {
        fun deValor(texto: String): RotaDoEnvio =
            entries.firstOrNull { it.valor == texto } ?: error("rota desconhecida no outbox: '$texto'")
    }
}

/**
 * A nota do professor que ainda nao subiu.
 *
 * **E um tipo irmao de [ResultadoPendente], e nao um subtipo de `ApuracaoParaEnvio`**: o servidor faz `when`
 * exaustivo sobre esse tipo, e o ADR-0021 adiou o subtipo ate haver consumidor — o consumidor aqui e so o
 * aparelho (design D6). [captureId] e a chave de idempotencia desta nota, cunhada uma vez; [completaCaptura] e o
 * `capture_id` da parcial que ela completa. Sem nome, turma ou matricula, e sem referencia a arquivo: a ausencia e
 * o requisito (I5).
 */
data class NotaPendente(
    val captureId: String,
    val completaCaptura: String,
    val organizacao: String,
    val prova: String,
    /** Nulo na folha avulsa, e nunca string vazia. */
    val studentToken: String?,
    val apuradoEm: Long,
    val nota: NotaDoProfessor,
)
