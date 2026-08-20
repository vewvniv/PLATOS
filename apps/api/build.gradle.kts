import com.platos.build.GenerateJooqTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    // jOOQ 3.20 e distribuido em class files da versao 65; abaixo de 21 o runtime nao carrega
    // `org.jooq.DSLContext` e a suite falha ainda na descoberta dos testes.
    jvmToolchain(21)
}

// D-0.7: as classes tipadas saem das migrations, nao de um schema mantido a mao.
val generateJooq = tasks.register<GenerateJooqTask>("generateJooq") {
    group = "build"
    description = "Sobe Postgres efemero, aplica as migrations e gera as classes jOOQ"
    migrationsDir.set(rootProject.layout.projectDirectory.dir("supabase/migrations"))
    outputDir.set(layout.buildDirectory.dir("generated/jooq"))
    packageName.set("com.platos.api.db.generated")
    postgresImage.set("postgres:16-alpine")
}

sourceSets.main {
    kotlin.srcDir(generateJooq.flatMap { it.outputDir })
}

application {
    mainClass.set("com.platos.api.ApplicationKt")
}

tasks.test {
    // Ver `gradle.properties`: a JVM dos testes tambem precisa anunciar uma API aceita pelo engine.
    systemProperty("api.version", providers.gradleProperty("dockerApiVersion").get())
    systemProperty(
        "platos.migrations.dir",
        rootProject.layout.projectDirectory.dir("supabase/migrations").asFile.absolutePath,
    )
    systemProperty(
        "platos.plans.dir",
        rootProject.layout.projectDirectory.dir("plans").asFile.absolutePath,
    )
    // A publicacao entra por arquivo versionado (D-2a.6): a fixture e a definicao de prova, e o
    // teste a le de onde ela ja mora, sem copia paralela que possa envelhecer.
    systemProperty(
        "platos.fixtures.dir",
        rootProject.layout.projectDirectory.dir("fixtures").asFile.absolutePath,
    )
}

dependencies {
    implementation(project(":packages:domain"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kaml)
    implementation(libs.jooq)
    implementation(libs.hikari)
    implementation(libs.postgresql)
    implementation(libs.logback.classic)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
}
