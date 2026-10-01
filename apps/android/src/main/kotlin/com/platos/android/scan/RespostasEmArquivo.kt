package com.platos.android.scan

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * As respostas em `filesDir/respostas/`, um PNG por resposta (`slice-5c-1-a-resposta-fica-no-aparelho`,
 * design, decisao 2).
 *
 * **A gravacao e atomica**: o PNG e escrito num temporario do **mesmo diretorio** e renomeado para
 * `<uuid>.png` so depois de inteiro. Um processo que termina no meio deixa um temporario (`.tmp`), que
 * a varredura trata como orfao; nunca um `.png` truncado. Falha de escrita vira
 * [RespostaDoQuadro.Recusada] e o temporario e apagado — nunca excecao que derrube o laco da camera.
 *
 * [escrever] e a costura que deixa o teste falhar **no meio** da escrita (disco cheio nao se simula de
 * outro jeito). Em producao e `File.writeBytes`.
 */
class RespostasEmArquivo(
    private val diretorio: File,
    private val escrever: (File, ByteArray) -> Unit = { arquivo, bytes -> arquivo.writeBytes(bytes) },
) : RespostasGuardadas {

    override fun gravar(
        png: ByteArray,
        capturadaEm: Long,
        desvioSinalizado: Boolean,
        foraPpm: Int,
    ): RespostaDoQuadro {
        val nome = "${UUID.randomUUID()}$EXTENSAO"
        val temporario = File(diretorio, "$nome$SUFIXO_TEMPORARIO")
        return try {
            if (!diretorio.isDirectory && !diretorio.mkdirs()) {
                throw IOException("nao foi possivel criar o diretorio das respostas")
            }
            escrever(temporario, png)
            Files.move(temporario.toPath(), File(diretorio, nome).toPath(), StandardCopyOption.ATOMIC_MOVE)
            RespostaDoQuadro.Guardada(RespostaGuardada(nome, capturadaEm, desvioSinalizado, foraPpm))
        } catch (e: Exception) {
            temporario.delete()
            RespostaDoQuadro.Recusada("a resposta nao foi gravada: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    override fun ler(arquivo: String): ByteArray? =
        File(diretorio, arquivo).takeIf { it.isFile }?.readBytes()

    override fun existe(arquivo: String): Boolean = File(diretorio, arquivo).isFile

    override fun listar(): List<String> = diretorio.list()?.sorted() ?: emptyList()

    override fun eliminar(arquivo: String) {
        Files.deleteIfExists(File(diretorio, arquivo).toPath())
    }

    companion object {
        /**
         * O diretorio das respostas, **sob `filesDir`**, e dono unico do nome (P28): quem o abre (a
         * `ScanActivity`, a varredura da porta de entrada) e o teste que o confere contra a regra de extracao de
         * dados passam por aqui. Sob `filesDir` ele cai no dominio `file` da regra, sem ser listado
         * (`regras_de_extracao_de_dados.xml`, `path="."`).
         */
        fun diretorioDe(filesDir: File): File = File(filesDir, "respostas")

        const val EXTENSAO = ".png"
        const val SUFIXO_TEMPORARIO = ".tmp"
    }
}
