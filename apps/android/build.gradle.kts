// PROBE (conferencia em aparelho, secao 6). O import e obrigatorio: no Kotlin DSL do Gradle, `java` resolve para a
// extensao do plugin Java e sombreia o pacote, entao `java.util.Properties` nao compila.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}

// PROBE (secao 6): URL do projeto, chave anonima e e-mail saem de `local.properties`, que
// ja e ignorado pelo git. Vao como argumentos do runner, e nao como `BuildConfig`: assim a chave
// nao entra no APK, e nao vao pela linha de comando, entao tambem nao entram no historico do shell.
// Nada disto e o mecanismo definitivo — esse e a tarefa 2.2, por `BuildConfig`.
val probeProps = Properties().apply {
    val arquivo = rootProject.file("local.properties")
    if (arquivo.exists()) arquivo.inputStream().use { load(it) }
}

android {
    namespace = "com.platos.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.platos.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // PROBE (secao 6). `alvo` fica de fora: vem por `-P` na linha de comando, e o
        // probe imprime o que recebeu na primeira linha do relato — se o `-P` nao pegar, o
        // cabecalho denuncia em vez de a rodada medir a condicao errada em silencio.
        testInstrumentationRunnerArguments["supabaseUrl"] =
            probeProps.getProperty("probe.supabaseUrl", "")
        testInstrumentationRunnerArguments["anonKey"] =
            probeProps.getProperty("probe.anonKey", "")
        testInstrumentationRunnerArguments["email"] =
            probeProps.getProperty("probe.email", "probe@example.invalid")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // §13 poe OpenCV e ZXing-C++ no Android, e os dois sao nativos: o `.so` do OpenCV sozinho pesa
    // de 16,8 MB (armeabi-v7a) a 56,1 MB (x86_64), e um APK universal com as quatro ABIs da 143 MB
    // — acima do que a loja aceita como artefato unico, antes de a fatia 4 acrescentar qualquer
    // coisa.
    //
    // A divisao por ABI e declarada aqui em vez de herdada: ela e o que mantem **todo** aparelho
    // suportado, inclusive ARM de 32 bits, com cada um baixando so a sua biblioteca. Deixar
    // implicito seria confiar num padrao que uma versao futura do AGP pode mudar, e a consequencia
    // apareceria como um download de 143 MB para o professor.
    bundle {
        abi {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
        language {
            // Portugues so, e a folha e impressa: dividir por idioma nao economiza nada e complica
            // a instalacao.
            enableSplit = false
        }
    }

    sourceSets {
        getByName("main").kotlin.directories.add("src/main/kotlin")
        getByName("test").kotlin.directories.add("src/test/kotlin")
        getByName("androidTest").kotlin.directories.add("src/androidTest/kotlin")
        // A fixture, o golden e a fonte sao lidos de onde estao versionados, sem copia paralela.
        getByName("androidTest").assets.directories.add(
            rootProject.layout.projectDirectory.dir("fixtures").asFile.path,
        )
        getByName("androidTest").assets.directories.add(
            rootProject.layout.projectDirectory.dir("packages/domain/fonts").asFile.path,
        )
    }
}

/**
 * O pacote de referencia entra como asset do aplicativo.
 *
 * Copia, e nao `assets.directories.add(fixtures)`: montar `fixtures/` inteiro embutiria os 60 MB do
 * corpus fotografado no APK. O `androidTest` monta a pasta toda porque as fotos sao o oracle dele;
 * o aplicativo precisa de um arquivo.
 *
 * A origem e provisoria e esta dita aqui: ate a fatia 4 trazer o pull de referencia imutavel, o
 * pacote so pode chegar ao aparelho embarcado no proprio APK.
 */
abstract class EmbedPackageTask : DefaultTask() {
    @get:InputFile
    abstract val source: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val destino = outputDir.get().asFile
        destino.mkdirs()
        source.get().asFile.copyTo(destino.resolve("prova-referencia.package.json"), overwrite = true)
    }
}

val embedPackage = tasks.register<EmbedPackageTask>("embedPackage") {
    source.set(rootProject.layout.projectDirectory.file("fixtures/prova-referencia.package.json"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(embedPackage, EmbedPackageTask::outputDir)
    }
}

kotlin {
    // O bytecode Java do AGP sai em 11; o Kotlin precisa acompanhar, senao o build recusa a
    // combinacao antes de compilar qualquer linha.
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":packages:domain"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.opencv)
    implementation(libs.zxingcpp)

    // Fatia 3c: o modulo deixa de ser biblioteca e vira aplicativo.
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // Fatia 4a-zero: o aparelho fala com a rede. Uma pilha HTTP so, para autenticacao e para
    // dados — a decisao 9 registra por que a autenticacao **nao** usa `supabase-kt`.
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    // `MockEngine` responde no lugar do servidor. E o que permite exercitar a classificacao de
    // falha na JVM: 400 de verdade, `IOException` de verdade, sem rede e sem aparelho.
    testImplementation(libs.ktor.client.mock)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // A medicao do OMR e conferida contra a digitalizacao versionada e contra o que `papel.mjs`
    // mediu nela. Os dois sao lidos de onde estao versionados, sem copia paralela — mesma razao
    // do `androidTest`, que ja monta `fixtures/` como assets.
    systemProperty(
        "platos.fixtures",
        rootProject.layout.projectDirectory.dir("fixtures").asFile.absolutePath,
    )
}
