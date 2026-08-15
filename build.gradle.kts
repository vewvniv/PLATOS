plugins {
    // Versao declarada uma vez aqui: `buildSrc` usa `kotlin-dsl`, que poe o plugin do Kotlin no
    // classpath sem versao, e um `alias(...)` direto no subprojeto falha por nao poder verificar
    // compatibilidade.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.android.application) apply false
}

subprojects {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}
