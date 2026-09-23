// O import e obrigatorio: no Kotlin DSL do Gradle, `java` resolve para a extensao do plugin Java e
// sombreia o pacote, entao `java.util.Properties` nao compila.
import com.android.build.api.variant.BuildConfigField
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

// `local.properties` ja e ignorado pelo git, e e de onde sai tudo que nao pode ser versionado.
val propriedadesLocais = Properties().apply {
    val arquivo = rootProject.file("local.properties")
    if (arquivo.exists()) arquivo.inputStream().use { load(it) }
}

/**
 * Configuracao do aplicativo (tarefa 2.2). Tres valores, nenhum versionado, e nenhum opcional.
 *
 * Ordem de resolucao, do mais especifico para o mais geral: propriedade de projeto (`-P`),
 * variavel de ambiente, `local.properties`. A primeira serve a uma rodada avulsa, a segunda e como
 * o CI alimenta, e a terceira e o dia a dia de quem desenvolve.
 *
 * **Ausente falha o build, e nao vira string vazia.** Um valor vazio compila, instala e so quebra
 * na primeira chamada de rede, com erro que fala de rede e nao de configuracao — que e a forma de
 * ambiente errado passar em silencio (decisao 6).
 *
 * **Resolvida por `Provider`, e nao em tempo de configuracao.** O Gradle configura todo projeto do
 * build, entao uma exigencia avaliada aqui derrubava `:apps:api:installDist` e ate
 * `:apps:api:test` — builds que nao produzem APK nenhum. Aconteceu: o workflow que publica a
 * imagem da API quebrou por falta de configuracao do Android. Dentro de um `Provider`, o valor so
 * e lido quando a geracao do `BuildConfig` roda, e ai a exigencia vale para quem realmente monta
 * o aplicativo.
 */
fun valorDeConfig(chave: String, ambiente: String): String? =
    (findProperty(chave) as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv(ambiente)?.takeIf { it.isNotBlank() }
        ?: propriedadesLocais.getProperty(chave)?.takeIf { it.isNotBlank() }

/**
 * Os tres de uma vez, e nao um a um: quem clona o repositorio deve ver a lista inteira do que
 * falta, e nao descobrir mais um a cada tentativa.
 *
 * `https` e obrigatorio porque `targetSdk 35` recusa trafego em claro antes de abrir soquete, e a
 * falha sairia como `UnknownServiceException: CLEARTEXT ... not permitted` — politica de rede
 * disfarcada de falha de transporte, que o classificador apresentaria como "sem rede".
 *
 * A barra final e removida porque quem chama concatena `"$urlBase/auth/v1/..."`. Com ela o pedido
 * viraria `https://projeto//auth/v1/...`, que alguns servidores aceitam e outros nao — defeito que
 * depende do servidor e nao aparece em teste.
 */
fun configuracaoDoAplicativo(): Map<String, String> {
    val faltando = mutableListOf<String>()
    val invalida = mutableListOf<String>()

    fun exigir(chave: String, ambiente: String, url: Boolean): String {
        val bruto = valorDeConfig(chave, ambiente)
        if (bruto == null) {
            faltando += "$chave  (ou a variavel de ambiente $ambiente)"
            return ""
        }
        val valor = bruto.trim()
        // Aspas quebrariam o literal gerado em `BuildConfig`, e o erro sairia como falha de
        // compilacao de codigo gerado, que nao aponta para a causa.
        if (valor.contains('"') || valor.contains('\\')) {
            invalida += "$chave: nao pode conter aspas nem barra invertida"
            return valor
        }
        if (!url) return valor
        if (!valor.startsWith("https://")) {
            invalida += "$chave: precisa comecar com https:// (veio \"$valor\")"
            return valor
        }
        return valor.trimEnd('/')
    }

    val resolvida = mapOf(
        "SUPABASE_URL" to exigir("platos.supabaseUrl", "PLATOS_SUPABASE_URL", url = true),
        "SUPABASE_ANON_KEY" to exigir("platos.supabaseAnonKey", "PLATOS_SUPABASE_ANON_KEY", url = false),
        "API_URL" to exigir("platos.apiUrl", "PLATOS_API_URL", url = true),
    )

    if (faltando.isNotEmpty() || invalida.isNotEmpty()) {
        error(
            buildString {
                appendLine("Configuracao do aplicativo Android incompleta.")
                if (faltando.isNotEmpty()) {
                    appendLine()
                    appendLine("Faltando:")
                    faltando.forEach { appendLine("  - $it") }
                }
                if (invalida.isNotEmpty()) {
                    appendLine()
                    appendLine("Invalido:")
                    invalida.forEach { appendLine("  - $it") }
                }
                appendLine()
                appendLine("Acrescente em `local.properties`, na raiz do repositorio (ja ignorado pelo git):")
                appendLine("  platos.supabaseUrl=https://<projeto>.supabase.co")
                appendLine("  platos.supabaseAnonKey=<a chave anonima do projeto>")
                appendLine("  platos.apiUrl=https://<host da api>")
                appendLine()
                append("A chave anonima e publica por desenho — quem autoriza e RLS mais JWT. ")
                appendLine("Ela nao e versionada porque o ambiente muda, e valor embutido vira o valor")
                append("errado em silencio.")
            },
        )
    }
    return resolvida
}

/** Avaliado uma vez, e so quando alguem pedir. */
val configuracao = providers.provider { configuracaoDoAplicativo() }

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

        // PROBE (secao 6). Canal proprio, e nao `BuildConfig`, de proposito.
        //
        // As duas configuracoes leem o mesmo `local.properties` mas tem semanticas **opostas** de
        // ausencia: a do aplicativo ausente falha o build, porque aplicativo sem destino nao serve
        // para nada; a do probe ausente e um nao-evento, porque sem projeto real nao ha o que
        // medir e o probe se pula. Fosse um canal so, o probe teria de reconhecer o valor de
        // marcador do CI por comparacao de string para saber que nao ha projeto — classificar por
        // texto, que e o que esta fatia recusa em todo lugar.
        //
        // `alvo` fica de fora: vem por `-P` na linha de comando, e o probe imprime o que recebeu na
        // primeira linha do relato — se o `-P` nao pegar, o cabecalho denuncia em vez de a rodada
        // medir a condicao errada em silencio.
        testInstrumentationRunnerArguments["supabaseUrl"] =
            propriedadesLocais.getProperty("probe.supabaseUrl", "")
        testInstrumentationRunnerArguments["anonKey"] =
            propriedadesLocais.getProperty("probe.anonKey", "")
        testInstrumentationRunnerArguments["email"] =
            propriedadesLocais.getProperty("probe.email", "probe@example.invalid")
    }

    buildFeatures {
        compose = true
        buildConfig = true
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

/*
 * A fatia 4a removeu `EmbedPackageTask`, e a remocao e criterio de aceite e nao limpeza.
 *
 * Ate a 3c o pacote de referencia entrava como asset do aplicativo, e o proprio codigo marcava isso
 * como provisorio. Com o pull de referencia imutavel, o asset deixou de existir junto com o
 * `assets.open` que o lia (ADR-0013, decisao 5): um embutimento vivo com o consumidor removido
 * deixaria o pacote dentro do APK sem ninguem para notar, e o portao binario de ADR-0009 morreria em
 * silencio.
 *
 * O `androidTest` continua montando `fixtures/` como assets **de teste**, que sao outro conjunto e
 * nao vao para o APK de producao.
 */

androidComponents {
    onVariants { variant ->
        // `put` com `Provider`: a configuracao so e exigida quando o `BuildConfig` desta
        // variante for gerado, e nao quando o projeto e configurado.
        //
        // O valor vai **com aspas**: `BuildConfigField` recebe o literal Java, e nao o dado. Sem
        // elas as URLs saem como `= https://x;` e o javac recusa -- mas a chave anonima sozinha
        // sairia como identificador valido, e o erro apareceria mais longe da causa.
        //
        // Nulo quando `buildFeatures.buildConfig` esta desligado. `requireNotNull` em vez
        // de `?.`: com a chamada segura, desligar a feature faria os tres campos sumirem
        // em silencio, e o aplicativo compilaria apontando para lugar nenhum.
        val campos = requireNotNull(variant.buildConfigFields) {
            "buildFeatures.buildConfig precisa estar ligado: a configuracao do aplicativo sai por ele"
        }
        campos.put(
            "SUPABASE_URL",
            configuracao.map { BuildConfigField("String", "\"" + it.getValue("SUPABASE_URL") + "\"", null) },
        )
        campos.put(
            "SUPABASE_ANON_KEY",
            configuracao.map { BuildConfigField("String", "\"" + it.getValue("SUPABASE_ANON_KEY") + "\"", null) },
        )
        campos.put(
            "API_URL",
            configuracao.map { BuildConfigField("String", "\"" + it.getValue("API_URL") + "\"", null) },
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

    // Decisao 5: a sessao nao fica em claro no aparelho. O sandbox impede que outro aplicativo
    // leia o armazenamento privado, e isso e tudo o que ele impede — num aparelho compartilhado
    // entre escolas o que sobra e acesso fisico, e ai token em claro e credencial reutilizavel.
    implementation(libs.security.crypto)

    // O outbox de resultado. Room guarda a correcao apurada antes de qualquer rede — ADR-0013 §3
    // adiou Room ate "a 4b, onde o outbox e de fato relacional", e este e o outbox. WorkManager e
    // quem tenta o envio quando ha rede, e quem sobrevive ao fim do processo do aplicativo.
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.workmanager.runtime)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    // `MockEngine` responde no lugar do servidor. E o que permite exercitar a classificacao de
    // falha na JVM: 400 de verdade, `IOException` de verdade, sem rede e sem aparelho.
    testImplementation(libs.ktor.client.mock)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.workmanager.testing)
    // Mesmo artefato que o `testImplementation` ja usa, agora tambem no alvo instrumentado: o teste
    // do segundo membro precisa ler o cabecalho que saiu, e `MockEngine` e quem o entrega.
    androidTestImplementation(libs.ktor.client.mock)
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

/**
 * Nenhum pacote de prova entre os recursos empacotados (fatia 4a, tarefa 7.2).
 *
 * **Confere o APK, e nao o codigo.** A spec afirma que o aplicativo nao contem pacote de prova entre
 * seus recursos, e essa e uma afirmacao sobre o artefato: um `grep` por `assets.open` diria que
 * ninguem le, e nao que ninguem embutiu. O modo de falha que ADR-0013 decisao 5 chama de mais
 * provavel e mais quieto e exatamente o embutimento sobrevivendo ao consumidor — tudo continua
 * funcionando, e o que se perde e a garantia de que o pacote em uso foi conferido.
 *
 * A deteccao nao e por nome de arquivo. Um pacote renomeado continuaria sendo um pacote, entao o que
 * se procura e a **forma**: um asset JSON que declare `answer_key` e `min_renderer_version` e um
 * `ExamPackage`, chame-se como se chamar.
 *
 * **As duas variantes, e cada uma com a sua guarda de vacuidade** (ETAPA 7.2). Ate la a tarefa so abria
 * o debug, e o APK de release — o que vai ao professor — passava sem conferencia: um pacote plantado em
 * `src/release/assets/` chegou ao release com esta tarefa dizendo "0 asset(s) conferido(s)"
 * (`docs/cobertura-o-apk-de-release-e-verificado.md` §2). A vacuidade e por variante porque, sobre a
 * uniao, o debug sozinho a satisfaria — e o release sem conferencia e exatamente o defeito.
 */
abstract class VerificarApkSemPacoteTask : DefaultTask() {
    @get:InputFiles
    abstract val apksDeDebug: ConfigurableFileCollection

    @get:InputFiles
    abstract val apksDeRelease: ConfigurableFileCollection

    @TaskAction
    fun run() {
        val encontrados = mutableListOf<String>()

        for ((variante, colecao) in listOf("debug" to apksDeDebug, "release" to apksDeRelease)) {
            val apks = colecao.files.filter { it.isFile && it.extension == "apk" }

            // Sem APK desta variante, a verificacao passaria por vacuidade e diria que esta tudo certo.
            require(apks.isNotEmpty()) {
                "nenhum APK de $variante para conferir; a tarefa depende de `assemble${variante.replaceFirstChar { it.uppercase() }}`"
            }

            for (apk in apks) {
                var conferidos = 0
                ZipFile(apk).use { zip ->
                    for (entrada in zip.entries()) {
                        if (!entrada.name.startsWith("assets/")) continue
                        if (!entrada.name.endsWith(".json")) continue
                        conferidos++
                        val texto = zip.getInputStream(entrada).use { it.readBytes().decodeToString() }
                        if (texto.contains("\"answer_key\"") && texto.contains("\"min_renderer_version\"")) {
                            encontrados += "${apk.name}!${entrada.name}"
                        }
                    }
                }
                logger.lifecycle("$variante: ${apk.name}, $conferidos asset(s) JSON conferido(s).")
            }
        }

        require(encontrados.isEmpty()) {
            "ha pacote de prova entre os recursos empacotados: ${encontrados.joinToString()}. " +
                "O pull de referencia imutavel e o unico caminho (ADR-0013, decisao 5)."
        }

        logger.lifecycle("APK sem pacote de prova, nas duas variantes.")
    }
}

val verificarApkSemPacote = tasks.register<VerificarApkSemPacoteTask>("verificarApkSemPacote") {
    group = "verification"
    description = "Confere que nenhum pacote de prova foi empacotado no APK, de debug e de release"
    dependsOn("assembleDebug", "assembleRelease")
    // `.asFileTree`: um `ConfigurableFileCollection` alimentado por um diretorio traz o diretorio,
    // e nao o conteudo dele. A guarda de vacuidade foi quem pegou isso.
    apksDeDebug.from(layout.buildDirectory.dir("outputs/apk/debug").map { it.asFileTree })
    apksDeRelease.from(layout.buildDirectory.dir("outputs/apk/release").map { it.asFileTree })
}

tasks.named("check") { dependsOn(verificarApkSemPacote) }
