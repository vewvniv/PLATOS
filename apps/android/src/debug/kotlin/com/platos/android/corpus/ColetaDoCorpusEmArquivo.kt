package com.platos.android.corpus

import com.platos.android.scan.RespostasGuardadas
import com.platos.android.scan.RetencaoDaResposta
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * A coleta do corpus no disco (`slice-5d-corpus-de-medicao`). **So existe no APK de depuracao.**
 *
 * `filesDir/corpus/<id>.png` e `<id>.json`, com `<id>` um UUID sorteado na copia. A copia e atomica por arquivo, como
 * `RespostasEmArquivo`: temporario no mesmo diretorio e `ATOMIC_MOVE`, a foto **antes** dos dados, de modo que o
 * `.json` so existe com a foto inteira ao lado. Amostra sem o par e residuo.
 *
 * O instante da amostra e o `lastModified` do `.json` — a amostra **nao leva data no conteudo**. O interruptor e o
 * arquivo [MARCADOR] sob `filesDir`: `adb shell run-as com.platos.android touch files/coleta-ligada` liga.
 *
 * [escrever] e a costura que deixa o teste falhar no meio da escrita.
 */
class ColetaDoCorpusEmArquivo(
    private val raiz: File,
    private val escrever: (File, ByteArray) -> Unit = { arquivo, bytes -> arquivo.writeBytes(bytes) },
) : ColetaDoCorpus {

    private val diretorio = diretorioDe(raiz)

    override fun ligada(): Boolean = File(raiz, MARCADOR).isFile

    override fun copiar(amostras: List<AmostraACopiar>, respostas: RespostasGuardadas): ResultadoDaCopia {
        val ids = mutableListOf<String>()
        val falhas = mutableListOf<String>()
        for (amostra in amostras) {
            val id = copiarUma(amostra, respostas)
            if (id != null) ids += id else falhas += amostra.rotulo
        }
        return ResultadoDaCopia(ids, falhas)
    }

    private fun copiarUma(amostra: AmostraACopiar, respostas: RespostasGuardadas): String? {
        val id = UUID.randomUUID().toString()
        val foto = File(diretorio, "$id$FOTO")
        val dados = File(diretorio, "$id$DADOS")
        val fotoTemporaria = File(diretorio, "$id$FOTO$TEMPORARIO")
        val dadosTemporarios = File(diretorio, "$id$DADOS$TEMPORARIO")
        return try {
            val bytes = respostas.ler(amostra.arquivo) ?: return null
            if (!diretorio.isDirectory && !diretorio.mkdirs()) throw IOException("nao foi possivel criar o diretorio do corpus")
            val conteudo = AmostraDoCorpus.json.encodeToString(
                AmostraDoCorpus.serializer(),
                AmostraDoCorpus(
                    pontos = amostra.pontos.toString(),
                    maximo = amostra.maximo,
                    pacote = amostra.pacote,
                    item = amostra.item,
                ),
            ).toByteArray(Charsets.UTF_8)
            escrever(fotoTemporaria, bytes)
            escrever(dadosTemporarios, conteudo)
            Files.move(fotoTemporaria.toPath(), foto.toPath(), StandardCopyOption.ATOMIC_MOVE)
            Files.move(dadosTemporarios.toPath(), dados.toPath(), StandardCopyOption.ATOMIC_MOVE)
            id
        } catch (e: Exception) {
            for (arquivo in listOf(fotoTemporaria, dadosTemporarios, foto, dados)) arquivo.delete()
            null
        }
    }

    override fun eliminar(ids: List<String>) {
        for (id in ids) {
            for (sufixo in listOf(FOTO, DADOS, "$FOTO$TEMPORARIO", "$DADOS$TEMPORARIO")) {
                tentarEliminar(File(diretorio, "$id$sufixo"))
            }
        }
    }

    override fun eliminarVencidas(agora: Long) {
        val arquivos = diretorio.listFiles()?.filter { it.isFile && NOME_DA_AMOSTRA.matches(it.name) } ?: return
        val prazo = RetencaoDaResposta.PRAZO_DIAS * MILISSEGUNDOS_POR_DIA
        for ((id, grupo) in arquivos.groupBy { it.name.substringBefore('.') }) {
            val nomes = grupo.map { it.name }
            val completo = "$id$FOTO" in nomes && "$id$DADOS" in nomes
            val temporario = nomes.any { it.endsWith(TEMPORARIO) }
            val dados = grupo.firstOrNull { it.name == "$id$DADOS" }
            val vencida = dados != null && agora - dados.lastModified() >= prazo
            if (temporario || !completo || vencida) grupo.forEach(::tentarEliminar)
        }
    }

    override fun eliminarTodas() {
        try {
            diretorio.deleteRecursively()
        } catch (e: Exception) {
            // Fica para o prazo.
        }
    }

    /** Falha de E/S num arquivo nao impede os outros: ele fica para a proxima eliminacao. */
    private fun tentarEliminar(arquivo: File) {
        try {
            Files.deleteIfExists(arquivo.toPath())
        } catch (e: Exception) {
            // Proxima eliminacao.
        }
    }

    companion object {
        /** O arquivo cuja existencia liga a coleta. Literal que so o debug contem: o release nao o tem (Tarefa 4). */
        const val MARCADOR = "coleta-ligada"

        /** O diretorio das amostras, **sob `filesDir`**, e dono unico do nome (P28). */
        fun diretorioDe(filesDir: File): File = File(filesDir, "corpus")

        private const val FOTO = ".png"
        private const val DADOS = ".json"
        private const val TEMPORARIO = ".tmp"
        private const val MILISSEGUNDOS_POR_DIA = 86_400_000L

        /** So o que a coleta escreve: um UUID e a extensao (e o temporario). O que mais houver na pasta nao e dela. */
        private val NOME_DA_AMOSTRA =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|json)(\\.tmp)?$")
    }
}
