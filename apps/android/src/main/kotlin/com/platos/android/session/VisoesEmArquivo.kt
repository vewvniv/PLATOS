package com.platos.android.session

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A visao guardada em `visoes/<organization_id>.json`.
 *
 * **Recebe um diretorio, e nao um `Context`**, como `PacotesEmArquivo` — a implementacao inteira e
 * sistema de arquivos, e o unico laco com o Android seria descobrir onde ele fica, que quem sabe e o
 * `Activity`. E o que faz "reabrir sem rede usa a visao guardada" e "a revogacao apaga a visao"
 * serem cenarios de JVM, sem emulador.
 *
 * **Arquivo, e nao `SharedPreferences`.** As duas moram no armazenamento privado comum, que e onde a
 * decisao 5 da 4a-zero manda ficar o que nao e credencial de rede reutilizavel; a diferenca e que
 * `SharedPreferences` so existe no aparelho, e um cenario que so o emulador alcanca e um cenario que
 * fica sem cobertura ate alguem ligar um aparelho. Room continua sendo da 4b: aqui se guarda um
 * registro por organizacao, e isso e trabalho de sistema de arquivos (regra 8).
 *
 * **Sobrescrever e o comportamento querido, ao contrario do cache de pacotes.** La o nome **e** a
 * afirmacao de qual conteudo esta ali, e regravar nao acrescentaria nada; aqui o conteudo muda a cada
 * consulta, e o que se guarda e sempre o mais recente. A gravacao continua atomica pela mesma razao:
 * um leitor nunca pode ver metade de uma visao.
 */
class VisoesEmArquivo(private val raiz: File) : VisoesGuardadas {

    override fun guardar(visao: VisaoDaOrganizacao) {
        val destino = arquivo(visao.organizacao.id) ?: return
        destino.parentFile?.mkdirs()

        val texto = JSON.encodeToString(VisaoEmDisco.de(visao))

        // Temporario no mesmo diretorio, como no cache de pacotes: rename so e atomico dentro do
        // mesmo sistema de arquivos.
        val temporario = File(destino.parentFile, "${destino.name}.parcial-${System.nanoTime()}")
        try {
            temporario.writeText(texto)
            Files.move(
                temporario.toPath(),
                destino.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            temporario.delete()
        }
    }

    /**
     * Le, e trata **ilegivel como inexistente**.
     *
     * Visao truncada por queda de energia, ou escrita por uma versao que gravava outro formato, nao
     * e erro a apresentar: e ausencia de visao, e a resposta certa e a mesma de nunca ter havido
     * uma — o aplicativo pede rede uma vez. E o mesmo criterio de `lendoOuSessaoInvalida`, um nivel
     * acima: valor que nao interpreta e valor perdido.
     *
     * **O arquivo ilegivel e apagado**, e nao deixado para tras: mantido, ele seria relido a cada
     * arranque para falhar de novo, e a proxima gravacao bem-sucedida o substituiria de qualquer
     * forma.
     */
    override fun ler(organizacao: String): VisaoDaOrganizacao? {
        val arquivo = arquivo(organizacao) ?: return null
        if (!arquivo.isFile) return null

        return try {
            JSON.decodeFromString<VisaoEmDisco>(arquivo.readText()).paraVisao()
        } catch (_: java.io.IOException) {
            null
        } catch (_: kotlinx.serialization.SerializationException) {
            arquivo.delete()
            null
        }
    }

    override fun apagarDaOrganizacao(organizacao: String) {
        arquivo(organizacao)?.delete()
    }

    /**
     * O caminho, ou `null` quando o identificador nao tem forma de identificador.
     *
     * Mesma guarda de `PacotesEmArquivo`, e pela mesma razao: a organizacao vem da API e vira
     * segmento de caminho, e um valor com `..` sairia do diretorio do aplicativo.
     */
    private fun arquivo(organizacao: String): File? {
        if (!FORMATO_DE_IDENTIFICADOR.matches(organizacao)) return null
        return File(raiz, "$organizacao.json")
    }

    /**
     * O formato em disco, separado dos tipos da tela **de proposito**.
     *
     * [Organizacao] e [ProvaPublicada] descrevem o que a tela usa, e mudam quando a tela precisar;
     * este DTO descreve o que ja esta gravado em aparelhos. Fossem a mesma classe, renomear um campo
     * da tela invalidaria em silencio toda visao guardada — que e o modo de falha que a camada (b)
     * do pacote existe para pegar, um nivel abaixo.
     */
    @Serializable
    private data class VisaoEmDisco(
        @SerialName("organization_id") val organizacaoId: String,
        @SerialName("organization_name") val organizacaoNome: String,
        val provas: List<ProvaEmDisco>,
        @SerialName("vista_em") val vistaEm: Long,
    ) {
        fun paraVisao() = VisaoDaOrganizacao(
            organizacao = Organizacao(id = organizacaoId, nome = organizacaoNome),
            provas = provas.map { ProvaPublicada(it.shortId, it.titulo, it.contentHash) },
            vistaEm = vistaEm,
        )

        companion object {
            fun de(visao: VisaoDaOrganizacao) = VisaoEmDisco(
                organizacaoId = visao.organizacao.id,
                organizacaoNome = visao.organizacao.nome,
                provas = visao.provas.map { ProvaEmDisco(it.shortId, it.titulo, it.contentHash) },
                vistaEm = visao.vistaEm,
            )
        }
    }

    @Serializable
    private data class ProvaEmDisco(
        @SerialName("short_id") val shortId: String,
        val titulo: String,
        @SerialName("content_hash") val contentHash: String,
    )

    private companion object {
        /** UUID e o que a API devolve; a faixa e deliberadamente estreita. */
        val FORMATO_DE_IDENTIFICADOR = Regex("^[0-9a-zA-Z][0-9a-zA-Z._-]{0,63}$")

        val JSON = Json { ignoreUnknownKeys = true }
    }
}
