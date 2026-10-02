package com.platos.android.corpus

import com.platos.android.scan.RespostasGuardadas
import com.platos.domain.scoring.Pontos

/**
 * Uma discursiva a copiar para o corpus (`slice-5d-corpus-de-medicao`): o arquivo em `respostas/` (so para ler a
 * imagem; **nunca vai para a amostra**), o numero impresso da questao (so para dizer qual falhou), a pontuacao dada a
 * ela, o que ela vale, o hash do pacote e o item.
 */
data class AmostraACopiar(
    val arquivo: String,
    val rotulo: String,
    val pontos: Pontos,
    val maximo: Int,
    val pacote: String,
    val item: String,
)

/** O que a copia fez: os ids das amostras criadas e o rotulo de cada questao que **nao** foi copiada. */
data class ResultadoDaCopia(val ids: List<String>, val falhas: List<String>) {
    companion object {
        val NADA = ResultadoDaCopia(emptyList(), emptyList())
    }
}

/**
 * A coleta do corpus de medicao. **Existe so para o APK de depuracao funcionar**: a implementacao real vive em
 * `src/debug`, e o release recebe [SemColeta] pela fabrica `coletaDoCorpus` de `src/release`.
 *
 * **Nenhum metodo lanca.** Falha de copia vira [ResultadoDaCopia.falhas]; falha de eliminacao deixa o arquivo para a
 * proxima eliminacao. A coleta nunca derruba a nota, nunca impede o aplicativo de abrir e nunca impede sair.
 */
interface ColetaDoCorpus {
    /** O interruptor, lido agora. Desligado por padrao. */
    fun ligada(): Boolean

    /** Copia cada amostra, completa ou inexistente. Le a imagem de [respostas]. */
    fun copiar(amostras: List<AmostraACopiar>, respostas: RespostasGuardadas): ResultadoDaCopia

    /** Elimina as amostras de [ids] (a nota que nao foi gravada nao deixa amostra). */
    fun eliminar(ids: List<String>)

    /** Elimina o que tem 30 dias ou mais, e o residuo de copia interrompida. Roda com o interruptor ligado ou nao. */
    fun eliminarVencidas(agora: Long)

    /** Elimina a pasta inteira: sair da sessao e a revogacao (a amostra e copia, e nao o unico exemplar). */
    fun eliminarTodas()
}

/** A coleta que nao faz nada: o release, e os testes que nao a exercitam. */
object SemColeta : ColetaDoCorpus {
    override fun ligada() = false
    override fun copiar(amostras: List<AmostraACopiar>, respostas: RespostasGuardadas) = ResultadoDaCopia.NADA
    override fun eliminar(ids: List<String>) = Unit
    override fun eliminarVencidas(agora: Long) = Unit
    override fun eliminarTodas() = Unit
}
