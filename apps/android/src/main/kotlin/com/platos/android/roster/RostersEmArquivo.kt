package com.platos.android.roster

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * O roster guardado em `rosters/<organization_id>/<exam_short_id>.json`.
 *
 * **Recebe um diretorio, e nao um `Context`**, como `PacotesEmArquivo` e `VisoesEmArquivo` — a
 * implementacao inteira e sistema de arquivos, e o unico laco com o Android seria descobrir onde ele
 * fica, que quem sabe e o `Activity`. E o que faz "sair apaga o roster" e "a revogacao apaga o
 * roster" serem cenarios de JVM, sem emulador.
 *
 * **Arquivo, e nao Room.** ADR-0013 nao adiou Room por gosto: adiou com gatilho nomeado — "Room fica
 * para a 4b, onde o **outbox** e de fato relacional". O roster nao e: um registro por prova, lido
 * inteiro, substituido inteiro, sem consulta parcial e sem escrita concorrente. Trazer Room para
 * guardar isso seria abstrair sobre a necessidade de outra fatia (regra 8 do `CLAUDE.md`).
 *
 * **Um diretorio por organizacao, e nao um arquivo por par.** O apagamento que sair e a revogacao
 * fazem e por organizacao, e apagar um diretorio inteiro nao depende de saber quais provas estao
 * dentro — nao existe roster orfao de uma prova que a visao ja esqueceu.
 *
 * **Sobrescrever e o comportamento querido**, como na visao guardada e ao contrario do cache de
 * pacotes: la o nome **e** a afirmacao de qual conteudo esta ali; aqui o conteudo muda a cada pull, e
 * o que se guarda e sempre o mais recente. A gravacao continua atomica pela mesma razao, e por uma a
 * mais: meio roster legivel apresentaria a turma com alunos faltando, e turma incompleta e
 * indistinguivel de turma correta para quem le a tela.
 */
class RostersEmArquivo(private val raiz: File) : RostersGuardados {

    override fun guardar(organizacao: String, prova: String, roster: RosterDaProva) {
        val destino = arquivo(organizacao, prova) ?: return
        destino.parentFile?.mkdirs()

        val texto = JSON.encodeToString(RosterEmDisco.de(roster))

        // Temporario no mesmo diretorio, como no cache de pacotes e na visao: rename so e atomico
        // dentro do mesmo sistema de arquivos.
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
     * Le, e trata **ilegivel como nunca puxado**.
     *
     * Roster truncado por queda de energia, ou escrito por uma versao que gravava outro formato, nao
     * e erro a apresentar: e ausencia de roster, e a resposta certa e a mesma de nunca ter havido um
     * — o gate barra e pede rede uma vez. Mesmo criterio de `VisoesEmArquivo.ler`, e aqui ele
     * importa mais: um roster meio lido abriria a sessao afirmando conhecer uma turma que nao
     * conhece inteira.
     *
     * **O arquivo ilegivel e apagado**, e nao deixado para tras: mantido, ele seria relido a cada
     * escolha da prova para falhar de novo, e o proximo pull o substituiria de qualquer forma.
     */
    override fun ler(organizacao: String, prova: String): RosterDaProva? {
        val arquivo = arquivo(organizacao, prova) ?: return null
        if (!arquivo.isFile) return null

        return try {
            JSON.decodeFromString<RosterEmDisco>(arquivo.readText()).paraRoster()
        } catch (_: java.io.IOException) {
            null
        } catch (_: kotlinx.serialization.SerializationException) {
            arquivo.delete()
            null
        }
    }

    override fun apagarDaOrganizacao(organizacao: String) {
        diretorio(organizacao)?.deleteRecursively()
    }

    /**
     * O caminho, ou `null` quando um dos identificadores nao tem forma de identificador.
     *
     * Mesma guarda de `PacotesEmArquivo` e `VisoesEmArquivo`, e pela mesma razao — os dois vem da
     * API e viram segmento de caminho, e um valor com `..` sairia do diretorio do aplicativo. Aqui
     * sao **dois** segmentos, entao sao duas conferencias: a da prova sozinha deixaria a organizacao
     * escapar, e a da organizacao sozinha deixaria a prova.
     */
    private fun arquivo(organizacao: String, prova: String): File? {
        val pasta = diretorio(organizacao) ?: return null
        if (!FORMATO_DE_IDENTIFICADOR.matches(prova)) return null
        return File(pasta, "$prova.json")
    }

    private fun diretorio(organizacao: String): File? {
        if (!FORMATO_DE_IDENTIFICADOR.matches(organizacao)) return null
        return File(raiz, organizacao)
    }

    /**
     * O formato em disco, separado dos tipos da tela **de proposito**, como `VisaoEmDisco` faz.
     *
     * [AlunoDoRoster] descreve o que a tela usa e muda quando a tela precisar; este DTO descreve o
     * que ja esta gravado em aparelhos. Fossem a mesma classe, renomear um campo da tela invalidaria
     * em silencio todo roster guardado.
     *
     * **[puxadoEm] e o unico campo fora das linhas**, e o requisito diz isso com todas as letras: a
     * clausula "apenas token e nome" vale sobre a linha, e esta vale sobre o que a cerca.
     */
    @Serializable
    private data class RosterEmDisco(
        @SerialName("puxado_em") val puxadoEm: Long,
        val alunos: List<AlunoEmDisco>,
    ) {
        fun paraRoster() = RosterDaProva(
            alunos = alunos.map { AlunoDoRoster(token = it.token, nome = it.nome) },
            puxadoEm = puxadoEm,
        )

        companion object {
            fun de(roster: RosterDaProva) = RosterEmDisco(
                puxadoEm = roster.puxadoEm,
                alunos = roster.alunos.map { AlunoEmDisco(it.token, it.nome) },
            )
        }
    }

    @Serializable
    private data class AlunoEmDisco(
        val token: String,
        val nome: String,
    )

    private companion object {
        /** UUID e `short_id` sao o que a API devolve; a faixa e deliberadamente estreita. */
        val FORMATO_DE_IDENTIFICADOR = Regex("^[0-9a-zA-Z][0-9a-zA-Z._-]{0,63}$")

        val JSON = Json { ignoreUnknownKeys = true }
    }
}
