package com.platos.build

import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.Test
import java.io.File
import java.net.URLClassLoader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Todo metodo declarado com `@Test` tem resultado no relatorio da tarefa que o roda (ETAPA 7.2).
 *
 * **O defeito que isto fecha nao acusa nada.** Dois testes de `ApiPlatosPacoteTest` eram
 * `fun … () = runBlocking { … assertInstanceOf(…) }`, e `assertInstanceOf` devolve o objeto: as duas
 * funcoes devolviam `Retorno$SemRede`, e o JUnit Jupiter nao descobre teste que devolve valor. Nenhuma
 * falha, nenhum pulado, nenhuma linha no log — nasceram assim em `593bf9a` e nunca rodaram
 * (`docs/cobertura-o-fio-preso-nos-dois-lados.md` §6). E o verde com contagem **menor** que a
 * declarada, que nenhuma outra guarda desta base olha.
 *
 * **A declaracao vem do bytecode, e nao do texto do fonte.** A primeira contagem, a da 7.3, lia `@Test`
 * no texto, e deixou passar um `@org.junit.jupiter.api.Test` escrito com o nome qualificado. Compilado,
 * todo `@Test` desta arvore vira a anotacao de tempo de execucao `org.junit.jupiter.api.Test` —
 * inclusive o `kotlin.test.Test` do dominio e da API, conferido por `javap` na ETAPA 7.2 —, e e ela que
 * se procura, por reflexao, com as classes carregadas **sem inicializar**. O tipo de retorno nao
 * filtra: o metodo que devolve valor e justamente o que precisa ser contado.
 *
 * **O resultado vem do XML** que a propria tarefa acabou de escrever, casado pelo nome: `classname`, e o
 * `name` sem os `()` e sem um sufixo `[…]` (o `[jvm]` do KMP). Casar pelo nome, e nao por contagem, e o
 * que deixa a mensagem dizer **qual** teste faltou.
 *
 * **Oraculo independente (P4):** nao usa a descoberta do Jupiter, que e o que ela julga; le a anotacao
 * que o compilador gravou.
 *
 * **Roda como a ultima acao da propria tarefa de teste**, e nao numa tarefa separada: o relatorio e as
 * classes sao exatamente os da execucao que acabou de acontecer, e as tarefas de teste que o AGP registra
 * tarde nao precisam ser achadas pelo nome. Se um teste falha, a tarefa ja cai antes, e esta conferencia
 * nao roda — nao ha o que acrescentar a um vermelho.
 *
 * **Falha fechada.** Classe que nao carrega, relatorio que nao se le, zero declarados, zero relatorios:
 * tudo reprova com o motivo, em vez de ser pulado. Piso (P13): uma suite que trocasse de anotacao inteira
 * passaria em qualquer comparacao.
 *
 * **O que ela NAO prova:** que o resultado verifica alguma coisa. Um teste sem assercao roda e passa.
 */
fun Test.exigirQueTodoTesteDeclaradoRode() {
    val tarefa = path

    doLast {
        // Lidos da tarefa **agora**, e nao capturados quando ela e configurada: os plugins (Kotlin, AGP,
        // `jvm-test-suite`) **substituem** `testClassesDirs` e `classpath` depois deste ponto, e a
        // referencia capturada ficava vazia. Foi o piso que acusou, nas cinco tarefas, na primeira
        // execucao (`docs/cobertura-o-apk-de-release-e-verificado.md`, Parte II).
        val teste = this as Test
        val declarados = declarados(tarefa, teste.testClassesDirs.files, teste.classpath.files)
        val executados = executados(tarefa, teste.reports.junitXml.outputLocation.get().asFile)

        val semResultado = declarados.filter { (classe, metodo) ->
            metodo !in executados[classe].orEmpty()
        }
        if (semResultado.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("$tarefa: ${semResultado.size} metodo(s) declarado(s) com @Test sem resultado no relatorio:")
                    for ((classe, metodo) in semResultado) appendLine("  - $classe > $metodo")
                    append(
                        "O JUnit Jupiter nao descobre metodo de teste que devolve valor, e nao acusa nada. " +
                            "Um corpo por expressao (`= runBlocking { ... }`) devolve o valor da ultima linha.",
                    )
                },
            )
        }
        logger.lifecycle("$tarefa: ${declarados.size} metodo(s) com @Test, todos com resultado no relatorio.")
    }
}

private const val ANOTACAO_DE_TESTE = "org.junit.jupiter.api.Test"

/** Pares (classe, metodo) com a anotacao, lidos do bytecode das classes de teste. */
private fun declarados(tarefa: String, diretorios: Set<File>, classpath: Set<File>): List<Pair<String, String>> {
    val arquivos = diretorios.filter { it.isDirectory }.flatMap { raiz ->
        raiz.walkTopDown().filter { it.isFile && it.extension == "class" }.map { raiz to it }.toList()
    }
    if (arquivos.isEmpty()) throw GradleException("$tarefa: piso — nenhuma classe de teste compilada em $diretorios")

    val urls = (diretorios + classpath).filter { it.exists() }.map { it.toURI().toURL() }.toTypedArray()
    val encontrados = mutableListOf<Pair<String, String>>()
    val naoCarregadas = mutableListOf<String>()

    URLClassLoader(urls, ClassLoader.getPlatformClassLoader()).use { carregador ->
        for ((raiz, arquivo) in arquivos) {
            val nome = arquivo.relativeTo(raiz).invariantSeparatorsPath.removeSuffix(".class").replace('/', '.')
            if (nome.endsWith("module-info") || nome.endsWith("package-info")) continue
            try {
                val classe = Class.forName(nome, false, carregador)
                for (metodo in classe.declaredMethods) {
                    if (metodo.annotations.any { it.annotationClass.java.name == ANOTACAO_DE_TESTE }) {
                        encontrados += classe.name to metodo.name
                    }
                }
            } catch (erro: LinkageError) {
                naoCarregadas += "$nome (${erro.javaClass.simpleName}: ${erro.message})"
            } catch (erro: ClassNotFoundException) {
                naoCarregadas += "$nome (ClassNotFoundException)"
            }
        }
    }

    if (naoCarregadas.isNotEmpty()) {
        throw GradleException(
            "$tarefa: ${naoCarregadas.size} classe(s) de teste nao carregaram, e nao sei dizer se declaram @Test:\n" +
                naoCarregadas.joinToString("\n") { "  - $it" },
        )
    }
    if (encontrados.isEmpty()) {
        throw GradleException("$tarefa: piso — nenhum metodo com $ANOTACAO_DE_TESTE nas classes de teste")
    }
    return encontrados
}

/** Nome de metodo por classe, lidos dos `TEST-*.xml` da tarefa. */
private fun executados(tarefa: String, diretorio: File): Map<String, Set<String>> {
    val arquivos = diretorio.listFiles { f -> f.isFile && f.name.startsWith("TEST-") && f.name.endsWith(".xml") }
        .orEmpty()
    if (arquivos.isEmpty()) throw GradleException("$tarefa: piso — nenhum relatorio TEST-*.xml em $diretorio")

    val fabrica = DocumentBuilderFactory.newInstance()
    val porClasse = mutableMapOf<String, MutableSet<String>>()
    for (arquivo in arquivos) {
        val casos = try {
            fabrica.newDocumentBuilder().parse(arquivo).getElementsByTagName("testcase")
        } catch (erro: Exception) {
            throw GradleException("$tarefa: nao consegui ler o relatorio ${arquivo.name}: ${erro.message}")
        }
        for (i in 0 until casos.length) {
            val caso = casos.item(i).attributes
            val classe = caso.getNamedItem("classname")?.nodeValue ?: continue
            val nome = caso.getNamedItem("name")?.nodeValue ?: continue
            porClasse.getOrPut(classe) { mutableSetOf() } += normalizado(nome)
        }
    }
    return porClasse
}

/** `mapa declara as duas versoes()[jvm]` → `mapa declara as duas versoes`. */
private fun normalizado(nome: String): String =
    nome.replace(Regex("""\[[^\]]*]$"""), "").replace(Regex("""\(.*\)$"""), "")
