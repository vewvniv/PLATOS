package com.platos.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Target
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties

/**
 * D-0.7: sobe um Postgres efemero, aplica `supabase/migrations` em ordem, gera as classes jOOQ e
 * derruba o container.
 *
 * O ponto nao e conveniencia: e que migration e codigo tipado nao possam divergir sem quebrar o
 * build (§14 regra 7). Apontar o codegen para um banco vivo, ou versionar o schema a mao em dois
 * lugares, reintroduz exatamente a divergencia que isso existe para impedir.
 *
 * As entradas e saidas sao declaradas para o Gradle, entao o container so sobe quando alguma
 * migration muda — builds normais nao pagam o custo nem exigem Docker.
 */
abstract class GenerateJooqTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val migrationsDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val postgresImage: Property<String>

    @TaskAction
    fun generate() {
        val migrations = migrationsDir.get().asFile
            .listFiles { file -> file.isFile && file.name.endsWith(".sql") }
            ?.sortedBy { it.name }
            ?: emptyList()

        check(migrations.isNotEmpty()) {
            "Nenhuma migration encontrada em ${migrationsDir.get().asFile}"
        }

        val target = outputDir.get().asFile
        target.deleteRecursively()
        target.mkdirs()

        PostgreSQLContainer(postgresImage.get()).use { postgres ->
            postgres.start()

            // Uma conexao so, e ela serve as migrations **e** o codegen. Ver a KDoc de
            // [conectarAoPostgres] para o que estava errado em abrir duas.
            conectarAoPostgres(postgres.jdbcUrl, postgres.username, postgres.password).use { conexao ->
                conexao.createStatement().use { statement ->
                    migrations.forEach { migration ->
                        logger.lifecycle("Aplicando ${migration.name}")
                        statement.execute(migration.readText())
                    }
                }

                GenerationTool().apply { setConnection(conexao) }.run(configuracaoDoCodegen(target))
            }
        }
    }

    private fun configuracaoDoCodegen(target: java.io.File): Configuration =
        Configuration()
            // Sem `withJdbc`: a conexao chega pronta, por [conectarAoPostgres]. Deixa-lo aqui
            // reintroduziria o segundo caminho de aquisicao que esta task tinha.
            .withGenerator(
                Generator()
                    .withName("org.jooq.codegen.KotlinGenerator")
                    .withDatabase(
                        Database()
                            .withName("org.jooq.meta.postgres.PostgresDatabase")
                            .withInputSchema("public")
                            .withIncludes(".*")
                            .withExcludes(""),
                    )
                    .withGenerate(
                        Generate()
                            .withPojos(false)
                            .withDaos(false)
                            .withDeprecated(false)
                            .withComments(true),
                    )
                    .withTarget(
                        Target()
                            .withPackageName(packageName.get())
                            .withDirectory(target.absolutePath),
                    ),
            )
}

/**
 * Abre a conexao **usando** o driver do Postgres, em vez de pedir ao `DriverManager` que o procure.
 *
 * **O que isto conserta, e o que nao.** Esta task falhava de forma intermitente com
 * `java.sql.SQLException: No suitable driver found for jdbc:postgresql://...` — 2 execucoes de 7 num
 * dia, 0 de 4 no outro, sempre a mesma mensagem. Essa mensagem e produzida pela **busca** que
 * `DriverManager.getConnection` faz entre os drivers registrados; ela nao tem como sair de um
 * `Driver.connect` chamado diretamente. A dependencia sai; a **causa da intermitencia continua sem
 * medicao**, e `docs/cobertura-generatejooq-sem-registro-automatico.md` diz isso com essas palavras.
 *
 * `Class.forName` antes do `DriverManager` foi considerado e descartado: e a mesma dependencia com
 * um passo a mais, porque continua exigindo que o registro estatico fique visivel para a busca — e e
 * exatamente essa visibilidade que nunca foi medida. `DriverManager.registerDriver` foi descartado
 * pela mesma razao, mais um efeito global que sobrevive a task dentro do daemon do Gradle.
 *
 * `connect` devolve `null`, e nao lanca, quando o driver nao reconhece a URL. Sem a conferencia
 * abaixo isso viraria `NullPointerException` mais adiante, trocando um erro que se le por um que se
 * investiga.
 */
internal fun conectarAoPostgres(url: String, usuario: String, senha: String): Connection {
    val driver = org.postgresql.Driver()
    val credenciais = Properties().apply {
        setProperty("user", usuario)
        setProperty("password", senha)
    }

    val conexao = try {
        driver.connect(url, credenciais)
    } catch (falha: Exception) {
        throw IllegalStateException(diagnosticoDaConexao(url, falha.toString()), falha)
    }

    return conexao ?: throw IllegalStateException(
        diagnosticoDaConexao(url, "o driver devolveu null: ele nao reconheceu a URL"),
    )
}

/**
 * O que seria preciso saber para diagnosticar uma falha de conexao — e que faltou quando o sintoma
 * apareceu.
 *
 * Vai na **excecao**, e nao em `logger.lifecycle`: log de build some no ruido de um runner, e a
 * mensagem da excecao e o que o CI mostra no passo vermelho. Isto nao e a correcao; e o instrumento
 * que evita que a proxima ocorrencia custe outro bisect de 7 commits.
 */
private fun diagnosticoDaConexao(url: String, causa: String): String {
    val registrados = runCatching {
        DriverManager.getDrivers().toList().joinToString { "${it::class.java.name}@${it::class.java.classLoader}" }
    }.getOrElse { "indisponivel: $it" }

    return buildString {
        appendLine("Falha ao abrir conexao com o Postgres efemero.")
        appendLine("  url: $url")
        appendLine("  causa: $causa")
        appendLine("  drivers registrados no DriverManager: ${registrados.ifBlank { "nenhum" }}")
        appendLine("  carregador da task: ${GenerateJooqTask::class.java.classLoader}")
        appendLine("  carregador do driver: ${org.postgresql.Driver::class.java.classLoader}")
        appendLine("  contexto do fio: ${Thread.currentThread().contextClassLoader}")
    }
}
