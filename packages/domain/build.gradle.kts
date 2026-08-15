import com.platos.build.EmbedFixturesTask
import com.platos.build.EmbedFontTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kmp.library)
}

// D36 + D-1.1: o TTF versionado vira codigo comum, para que os tres alvos leiam os mesmos bytes
// sem nenhuma API de recurso de plataforma no caminho.
val embedFont = tasks.register<EmbedFontTask>("embedFont") {
    group = "build"
    description = "Gera a fonte Kotlin com os bytes do TTF embarcado"
    fontFile.set(layout.projectDirectory.file("fonts/SourceSerif4-Regular.ttf"))
    packageName.set("com.platos.domain.text")
    outputDir.set(layout.buildDirectory.dir("generated/font"))
}

// A fixture e o golden precisam ser lidos pelos tres alvos: e disso que a paridade de calculo vive.
val embedFixtures = tasks.register<EmbedFixturesTask>("embedFixtures") {
    group = "build"
    description = "Embute a fixture de referencia e o golden do LayoutMap para os testes"
    files.from(
        rootProject.layout.projectDirectory.file("fixtures/prova-referencia.json"),
        rootProject.layout.projectDirectory.file("fixtures/prova-referencia.layout.json"),
    )
    packageName.set("com.platos.domain.fixtures")
    outputDir.set(layout.buildDirectory.dir("generated/fixtures"))
}

// D-1.1: um modulo KMP so, com praticamente tudo em commonMain. Se o calculo do LayoutMap
// dependesse de codigo de plataforma, a garantia de identidade entre alvos morreria na origem.
kotlin {
    jvmToolchain(21)

    jvm()

    android {
        namespace = "com.platos.domain"
        compileSdk = 35
        minSdk = 26
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
            }
        }
    }

    js {
        nodejs()
        binaries.library()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(embedFont.flatMap { it.outputDir })
            dependencies {
                implementation(libs.kotlinx.serialization.json)
            }
        }
        commonTest {
            kotlin.srcDir(embedFixtures.flatMap { it.outputDir })
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmTest.dependencies {
            // A raiz forca useJUnitPlatform() em todo Test; sem o runner JUnit 5 o alvo JVM
            // nao descobre nenhum teste e passa vazio.
            implementation(kotlin("test-junit5"))
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
}

tasks.withType<Test>().configureEach {
    // D-1.2 tem um teste-guarda que varre o proprio codigo-fonte; ele precisa saber onde ele esta.
    systemProperty(
        "platos.domain.commonMain",
        layout.projectDirectory.dir("src/commonMain/kotlin").asFile.absolutePath,
    )
    // Ferramenta de conferencia do QR: a JVM dos testes e forkada, entao o -D da linha de comando
    // precisa ser repassado explicitamente.
    providers.systemProperty("platos.qr.dump").orNull?.let { systemProperty("platos.qr.dump", it) }
}

// Regravacao do golden: `./gradlew :packages:domain:jvmTest -Dplatos.golden.write=true`.
tasks.withType<Test>().configureEach {
    providers.systemProperty("platos.golden.write").orNull?.let {
        systemProperty("platos.golden.write", it)
    }
    systemProperty(
        "platos.golden.path",
        rootProject.layout.projectDirectory.file("fixtures/prova-referencia.layout.json")
            .asFile.absolutePath,
    )
}
