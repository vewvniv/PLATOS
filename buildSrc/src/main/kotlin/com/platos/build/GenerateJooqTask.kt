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
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Target
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager

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

            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            ).use { connection ->
                connection.createStatement().use { statement ->
                    migrations.forEach { migration ->
                        logger.lifecycle("Aplicando ${migration.name}")
                        statement.execute(migration.readText())
                    }
                }
            }

            GenerationTool.generate(
                Configuration()
                    .withJdbc(
                        Jdbc()
                            .withDriver("org.postgresql.Driver")
                            .withUrl(postgres.jdbcUrl)
                            .withUser(postgres.username)
                            .withPassword(postgres.password),
                    )
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
                    ),
            )
        }
    }
}
