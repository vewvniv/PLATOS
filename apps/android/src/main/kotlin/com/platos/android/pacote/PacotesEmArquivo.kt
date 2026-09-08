package com.platos.android.pacote

import com.platos.domain.exam.ExamPackage
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * O cache de pacotes em `packages/<organization_id>/<content_hash>.json` (ADR-0013, decisao 3).
 *
 * **Recebe um diretorio, e nao um `Context`.** A implementacao inteira e sistema de arquivos, e o
 * unico laco com o Android seria descobrir onde ele fica — que quem sabe e o `Activity`. Com a raiz
 * por parametro, a atomicidade da gravacao, a reconferencia da leitura e o escopo por organizacao
 * ficam verificaveis em teste de JVM, sem emulador. Room fica para a 4b, onde o outbox e de fato
 * relacional: o que se guarda aqui e blob imutavel enderecado por identificador, que e trabalho de
 * sistema de arquivos (regra 8).
 *
 * **O escopo por organizacao corrige um furo de fronteira**, e nao e organizacao de pastas.
 * Enderecamento por conteudo e global por natureza, e o cache e um caminho de leitura que **nao
 * passa pela rota**: num aparelho compartilhado, com troca de usuario, a pasta entregaria um pacote
 * que a rota recusaria.
 */
class PacotesEmArquivo(private val raiz: File) : PacotesGuardados {

    override fun guardar(organizacao: String, hash: String, bytes: ByteArray) {
        val destino = arquivo(organizacao, hash) ?: return

        // Conteudo ja presente e o mesmo conteudo: o nome **e** a afirmacao de qual conteudo e
        // (ADR-0009 — pacote publicado nunca muda). Regravar nao acrescentaria nada, e sobrescrever
        // abriria a janela que o rename existe para fechar. Se o que esta la estiver corrompido,
        // quem o remove e `ler`, e a gravacao seguinte encontra o lugar vazio.
        if (destino.exists()) return

        destino.parentFile?.mkdirs()

        // Temporario **no mesmo diretorio**: rename so e atomico dentro do mesmo sistema de
        // arquivos, e um temporario em `cacheDir` ou em `/tmp` poderia estar noutro.
        val temporario = File(destino.parentFile, "${destino.name}.parcial-${System.nanoTime()}")
        try {
            temporario.writeBytes(bytes)
            Files.move(temporario.toPath(), destino.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally {
            // Interrupcao antes do rename deixa o parcial para tras. Ele nao e legivel como
            // conteudo — `ler` so olha o nome exato do hash —, mas nao ha razao para acumula-lo.
            temporario.delete()
        }
    }

    /**
     * Le, **reconfere**, e descarta o que nao conferir.
     *
     * Nao ha `fsync` na gravacao, e e decisao: uma queda de energia entre a escrita e o rename pode
     * deixar o nome visivel com conteudo truncado. E exatamente o caso que esta reconferencia pega,
     * e pagar `fsync` em toda gravacao para evitar um caso que a leitura ja trata seria custo sem
     * cobertura nova — a reconferencia precisa existir de qualquer forma, porque corrupcao em
     * repouso e adulteracao local nao sao evitadas por sincronizar.
     */
    override fun ler(organizacao: String, hash: String): ExamPackage? {
        val arquivo = arquivo(organizacao, hash) ?: return null
        if (!arquivo.isFile) return null

        val bytes = try {
            arquivo.readBytes()
        } catch (_: java.io.IOException) {
            return null
        }

        // O hash conferido e o **pedido**, e nao o lido do nome do arquivo. Sao o mesmo valor, e
        // escrever assim e o que impede que um dia alguem "otimize" derivando um do outro.
        return when (val conferencia = verificarPacote(bytes, hash)) {
            is Conferencia.Conferido -> conferencia.pacote
            is Conferencia.Recusado -> {
                arquivo.delete()
                null
            }
        }
    }

    override fun apagarDaOrganizacao(organizacao: String) {
        val pasta = pasta(organizacao) ?: return
        pasta.deleteRecursively()
    }

    /**
     * O caminho, ou `null` quando o identificador nao tem forma de identificador.
     *
     * **Nao e paranoia gratuita.** Os dois valores vem de fora — a organizacao da API, o hash de um
     * cabecalho —, e os dois viram segmento de caminho. Um valor com `..` sairia do diretorio do
     * aplicativo, e o cache e o unico lugar desta fatia onde entrada externa vira nome de arquivo.
     * Recusar pela forma e mais barato que confiar em quem responde.
     */
    private fun arquivo(organizacao: String, hash: String): File? {
        if (!FORMATO_DE_HASH.matches(hash.lowercase())) return null
        val pasta = pasta(organizacao) ?: return null
        return File(pasta, "${hash.lowercase()}.json")
    }

    private fun pasta(organizacao: String): File? {
        if (!FORMATO_DE_IDENTIFICADOR.matches(organizacao)) return null
        return File(raiz, organizacao)
    }

    private companion object {
        val FORMATO_DE_HASH = Regex("^[0-9a-f]{64}$")

        /** UUID e o que a API devolve; a faixa e deliberadamente estreita. */
        val FORMATO_DE_IDENTIFICADOR = Regex("^[0-9a-zA-Z][0-9a-zA-Z._-]{0,63}$")
    }
}
