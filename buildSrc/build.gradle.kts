import java.util.Properties

plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.jooq.codegen)
    implementation(libs.jooq.meta)
    implementation(libs.postgresql)
    implementation(libs.testcontainers.postgresql)

    testImplementation(kotlin("test"))
}

/**
 * `dockerApiVersion` mora no `gradle.properties` da raiz, e ele **nao** alcanca daqui: `buildSrc` e
 * uma build separada, e `-p buildSrc` a roda como raiz propria. Lido do arquivo, e nao repetido como
 * literal, para o numero continuar tendo uma fonte so — repeti-lo faria a proxima subida de Docker
 * Engine consertar um lugar e quebrar o outro.
 */
val versaoDaApiDoDocker: String = providers.gradleProperty("dockerApiVersion").orNull
    ?: rootDir.parentFile.resolve("gradle.properties").let { arquivo ->
        require(arquivo.isFile) { "gradle.properties da raiz nao encontrado em $arquivo" }
        Properties().apply { arquivo.inputStream().use { load(it) } }
            .getProperty("dockerApiVersion")
            ?: error("dockerApiVersion ausente em $arquivo")
    }

// `./gradlew build` **nao** alcanca este alvo: medido em 2026-09-18, com uma sonda que chama
// `fail()` por construcao — o build ficou verde e `:buildSrc:` parou em `jar`. Por isso o `ci.yml`
// pede `-p buildSrc test` pelo nome. Sem essa linha la, o teste aqui seria verde sobre nada.
tasks.test {
    useJUnitPlatform()
    // Mesma razao de `apps/api/build.gradle.kts`: o docker-java embutido no Testcontainers anuncia
    // por padrao uma API que o Docker Engine 29 recusa, e a JVM forkada do teste nao herda o
    // `-Dapi.version` que o `org.gradle.jvmargs` da ao daemon.
    systemProperty("api.version", versaoDaApiDoDocker)
}
