plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
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

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

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
