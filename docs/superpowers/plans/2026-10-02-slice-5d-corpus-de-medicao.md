# slice-5d-corpus-de-medicao Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Com a coleta ligada num APK de depuração, cada nota confirmada deixa uma cópia local das discursivas (foto + nota), que o mantenedor puxa por cabo para montar o corpus de 60 a 150 respostas, sem que o APK de release contenha esse código.

**Architecture:** Uma interface `ColetaDoCorpus` vive em `src/main` com uma implementação nula (`SemColeta`); a implementação real (`ColetaDoCorpusEmArquivo`) e a fábrica `coletaDoCorpus(filesDir)` existem só em `src/debug`, e `src/release` tem a fábrica que devolve `SemColeta`. A cópia acontece em `ScanActivity.darNota` **antes** de `gravarNota` e fora do caminho que decide o sucesso; o prazo, sair da sessão e a revogação eliminam as cópias. A saída é `adb`/`run-as` por um script em `tools/corpus/`, que confere o par foto+JSON contra um literal único de formato.

**Tech Stack:** Kotlin (Android, `kotlinx.serialization`, JUnit 5, `WorkManager` já existente), Gradle (`buildSrc`, `build.gradle.kts` do módulo), Node (`node:test`) para `tools/corpus/`. **Nenhuma tecnologia nova.**

**Spec:** `docs/superpowers/specs/2026-10-02-slice-5d-corpus-de-medicao-design.md` (o COMO) e `openspec/changes/slice-5d-corpus-de-medicao/` (`proposal.md`, `specs/measurement-corpus`, `specs/scan-session`; o QUÊ). Os dois são lidos juntos: este plano não os altera. Se uma decisão daqui revelar mudança de requisito, **pare e use `/opsx:update`**.

## Global Constraints

Valores copiados do desenho e da spec; valem para toda tarefa.

- **A coleta existe só no APK de depuração.** O release SHALL NOT conter o código; a ausência é de compilação (conjunto de fontes `debug`), e não de configuração.
- **O interruptor vem desligado.** Desligado, nenhuma amostra é criada. (Desvio do desenho, §"Desvios" abaixo: o interruptor é o arquivo `filesDir/coleta-ligada`.)
- **A cópia vem antes de `gravarNota`** e nunca a impede; nota não gravada não deixa amostra.
- **Uma amostra leva:** a foto com os **mesmos bytes** de `respostas/`; `versao`, `pontos` (decimal exata, duas casas, via `Pontos.toString`), `maximo`, `pacote` (`examPackage.contentHash()`), `item` (o `questionId`), `referencia` (nasce `null`), `descartar` (nasce `false`).
- **Uma amostra nunca leva:** nome, turma, matrícula, token do aluno, `captureId`, caderno, nome ou caminho do arquivo de `respostas/`, data ou hora. O nome do arquivo é um UUID sorteado na cópia (`<id>.png`, `<id>.json`).
- **Prazo: 30 dias**, dono único `RetencaoDaResposta.PRAZO_DIAS` (nenhum outro lugar escreve 30). Roda na abertura do app e dentro da varredura periódica da 5c-3. **Sair da sessão e a revogação eliminam toda a pasta** `corpus/`.
- **A pasta `filesDir/corpus/` cai no domínio `file` da regra de extração** (`path="."` nega o domínio inteiro), fora do backup e da transferência. Dono único do nome: `ColetaDoCorpusEmArquivo.diretorioDe(filesDir)`.
- **Nenhum código de rede lê `corpus/`.** `ARespostaNaoSaiDoAparelhoTest` fica intacto.
- **A saída é por cabo (`adb`, `run-as`).** Sem tela de exportação, compartilhar, rota, tabela, declaração, hash de arquivo. Fotos **fora do repositório**; `.gitignore` é a segunda rede.
- **Nenhum número de limiar de leitura é fixado.** O ADR registra composição e limites; cada ADR da bancada fixa o seu antes de rodar (P11, ADR-0007).
- **Testes Android:** nome de teste Kotlin **não aceita `:`**; **`--tests` reprova no módulo android** (guarda `TodoTesteDeclaradoRoda`): rode `:apps:android:testDebugUnitTest` inteiro (~10 s) e leia o XML. Texto de interface do aplicativo **não leva acento**; a documentação leva.
- **`ScanActivity.kt` tem CRLF misto:** use a ferramenta `Edit` (script que casa `\n` falha); `git` avisa "CRLF will be replaced by LF" sem estragar o diff.
- **A suíte de unidade também roda sobre o release** (ETAPA 7.2). Teste que referencie classe de `src/debug` fica em `src/testDebug`, nunca em `src/test`.
- **P22:** Docker, emulador e Gradle juntos estouram 16 GB; **pergunte ao Leon** antes de subir Docker/emulador (Tarefa 7).
- **Commits:** em português, no padrão do repositório; termine com `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Sem push (só com liberação explícita). A edição de `docs/legal/politica-de-privacidade.md` **não** entra em nenhum commit desta mudança.

## Desvios do desenho (declarados, para a revisão)

1. **O interruptor é um arquivo marcador, e não uma tela.** O desenho (§3.1) fala em "a tela do interruptor". Para uso só do mantenedor, que já usa `adb`, uma tela é Compose, teste e superfície para nada. Liga: `adb shell run-as com.platos.android touch files/coleta-ligada`; desliga: `... rm files/coleta-ligada`. A spec diz "interruptor" sem fixar a forma, então **não há mudança de requisito**. O desenho é atualizado na Tarefa 6.
2. **A fábrica é um par de funções de mesmo nome** (`coletaDoCorpus(filesDir)`), uma em `src/debug` e outra em `src/release`, em vez de uma `src/release` com classe nula. Resultado idêntico, menos código no release.

## Review Focus

Entradas e condições que o desenho implica, que nenhuma tarefa poderia deixar de testar, e que mais provavelmente morderiam (cada linha tem o teste na tarefa que a possui):

1. **Pontuação 0** (`0.00`) é nota válida e **é copiada** (não confundir "nada" com "zero"). Tarefas 1 e 2.
2. **Discursiva sem foto guardada** (caderno antigo, `resposta == null`): a linha é ignorada, a nota segue, e nenhuma falha é inventada. Tarefa 1.
3. **Disco cheio no meio da cópia:** não sobra `.png` sem `.json` nem `.tmp`, a nota é gravada, e o aviso nomeia a questão. Tarefas 1 e 2.
4. **Relógio do aparelho anterior ao arquivo** (`agora < lastModified`): mantém, como `RetencaoDaResposta`. Tarefa 2.
5. **Arquivo estranho na pasta do corpus** (uma anotação que o mantenedor deixou): o prazo **não** o toca; sair da sessão apaga a pasta inteira. Tarefa 2.

---

## File Structure

| Arquivo | Responsabilidade |
|---|---|
| `apps/android/src/main/kotlin/com/platos/android/corpus/ColetaDoCorpus.kt` (novo) | A interface, os tipos de entrada e saída e `SemColeta`. É o que o release compila. |
| `apps/android/src/main/kotlin/com/platos/android/corpus/CopiaDaNota.kt` (novo) | `amostrasDaNota` (puro) e `gravarNotaComColeta` (a ordem: copiar, gravar, desfazer). |
| `apps/android/src/debug/kotlin/com/platos/android/corpus/AmostraDoCorpus.kt` (novo) | O formato do JSON da amostra (contrato digitado uma vez). |
| `apps/android/src/debug/kotlin/com/platos/android/corpus/ColetaDoCorpusEmArquivo.kt` (novo) | A implementação real: copiar atômico, prazo, eliminar. |
| `apps/android/src/debug/kotlin/com/platos/android/corpus/FabricaDaColeta.kt` (novo) | `coletaDoCorpus(filesDir)` do debug. |
| `apps/android/src/release/kotlin/com/platos/android/corpus/FabricaDaColeta.kt` (novo) | `coletaDoCorpus(filesDir)` do release: `SemColeta`. |
| `fixtures/corpus/amostra-exemplo.json` (novo) | O literal único do formato, lido pelo Kotlin e pelo Node. |
| `apps/android/src/main/kotlin/com/platos/android/session/DeviceSession.kt` | `sair` e `revogar` eliminam o corpus. |
| `apps/android/src/main/kotlin/com/platos/android/session/SessaoActivity.kt` | Passa a coleta à sessão. |
| `apps/android/src/main/kotlin/com/platos/android/scan/VarreduraDoAparelho.kt` | A varredura elimina também as amostras vencidas. |
| `apps/android/src/main/kotlin/com/platos/android/scan/ScanActivity.kt` | `darNota` copia antes de gravar e avisa a falha. |
| `apps/android/build.gradle.kts` | `VerificarApkSemColetaTask`: o release não contém a coleta. |
| `tools/corpus/formato.mjs`, `puxar.mjs`, `corpus.test.mjs` (novos) | Conferência do formato, puxar por cabo, testes. |
| `.gitignore`, `.github/workflows/ci.yml` | Segunda rede contra commitar foto; os testes de `tools/corpus` no CI. |
| `docs/adr/0022-o-criterio-do-corpus-de-medicao.md`, `docs/protocolo-corpus-de-medicao.md`, `docs/cobertura-slice-5d-corpus-de-medicao.md` (novos) | O critério antes do resultado, o passo a passo, e como cada verificação foi vista falhar. |

---

### Task 1: O contrato e a regra da cópia (JVM, `src/main`)

**Files:**
- Create: `apps/android/src/main/kotlin/com/platos/android/corpus/ColetaDoCorpus.kt`
- Create: `apps/android/src/main/kotlin/com/platos/android/corpus/CopiaDaNota.kt`
- Test: `apps/android/src/test/kotlin/com/platos/android/corpus/CopiaDaNotaTest.kt`

**Interfaces:**
- Produces (todas em `com.platos.android.corpus`):
  - `data class AmostraACopiar(val arquivo: String, val rotulo: String, val pontos: Pontos, val maximo: Int, val pacote: String, val item: String)`
  - `data class ResultadoDaCopia(val ids: List<String>, val falhas: List<String>)` com `ResultadoDaCopia.NADA`
  - `interface ColetaDoCorpus { fun ligada(): Boolean; fun copiar(amostras: List<AmostraACopiar>, respostas: RespostasGuardadas): ResultadoDaCopia; fun eliminar(ids: List<String>); fun eliminarVencidas(agora: Long); fun eliminarTodas() }`. **Nenhum método lança**: falha vira resultado ou fica para a próxima eliminação.
  - `object SemColeta : ColetaDoCorpus`
  - `internal fun amostrasDaNota(linhas: List<LinhaDaNota>, pontuacoes: List<PontuacaoDada>, pacote: String): List<AmostraACopiar>`
  - `data class GravacaoComColeta(val gravou: Boolean, val falhas: List<String>)`
  - `internal fun gravarNotaComColeta(coleta: ColetaDoCorpus, respostas: RespostasGuardadas, amostras: () -> List<AmostraACopiar>, gravar: () -> Boolean): GravacaoComColeta`
- Consumes: `com.platos.android.scan.LinhaDaNota(questionId, rotulo, worth, resposta: RespostaGuardada?)` (já existe, `internal`), `RespostasGuardadas`, `com.platos.domain.scoring.{Pontos, PontuacaoDada(questionId, earned)}`.

- [ ] **Step 1: Escrever o teste que falha**

Criar `apps/android/src/test/kotlin/com/platos/android/corpus/CopiaDaNotaTest.kt`:

```kotlin
package com.platos.android.corpus

import com.platos.android.scan.LinhaDaNota
import com.platos.android.scan.RespostaDoQuadro
import com.platos.android.scan.RespostaGuardada
import com.platos.android.scan.RespostasGuardadas
import com.platos.domain.scoring.PontuacaoDada
import com.platos.domain.scoring.Pontos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A regra da cópia da nota (`slice-5d-corpus-de-medicao`, spec `scan-session`: "Com a coleta ligada, a nota
 * confirmada deixa a cópia das discursivas"), em JVM, com a coleta e as respostas falsas.
 */
class CopiaDaNotaTest {

    private class ColetaFalsa(var ligadaAgora: Boolean = true) : ColetaDoCorpus {
        val chamadas = mutableListOf<String>()
        val eliminadas = mutableListOf<String>()
        var aoCopiar: (List<AmostraACopiar>) -> ResultadoDaCopia =
            { a -> ResultadoDaCopia(a.map { "id-${it.rotulo}" }, emptyList()) }

        override fun ligada() = ligadaAgora

        override fun copiar(amostras: List<AmostraACopiar>, respostas: RespostasGuardadas): ResultadoDaCopia {
            chamadas += "copiar"
            return aoCopiar(amostras)
        }

        override fun eliminar(ids: List<String>) {
            chamadas += "eliminar"
            eliminadas += ids
        }

        override fun eliminarVencidas(agora: Long) = Unit
        override fun eliminarTodas() = Unit
    }

    private object SemRespostas : RespostasGuardadas {
        override fun gravar(png: ByteArray, capturadaEm: Long, desvioSinalizado: Boolean, foraPpm: Int): RespostaDoQuadro =
            RespostaDoQuadro.Recusada("falso")

        override fun ler(arquivo: String): ByteArray? = null
        override fun existe(arquivo: String) = false
        override fun listar() = emptyList<String>()
        override fun eliminar(arquivo: String) = Unit
    }

    private fun resposta(arquivo: String) = RespostaGuardada(arquivo, capturadaEm = 1_000L, desvioSinalizado = false, foraPpm = 0)

    private val linhas = listOf(
        LinhaDaNota("d1", rotulo = "1", worth = 3, resposta = resposta("a.png")),
        LinhaDaNota("d2", rotulo = "2", worth = 4, resposta = resposta("b.png")),
    )

    private fun amostras() = amostrasDaNota(
        linhas,
        listOf(PontuacaoDada("d1", Pontos.parse("1.5")), PontuacaoDada("d2", Pontos.parse("0"))),
        pacote = "hash-do-pacote",
    )

    // --- amostrasDaNota ---

    @Test
    fun `cada linha casa com a pontuacao dada a ela, e o zero e uma pontuacao`() {
        val dadas = amostras()

        assertEquals(listOf("a.png", "b.png"), dadas.map { it.arquivo })
        assertEquals(listOf("1", "2"), dadas.map { it.rotulo })
        assertEquals(listOf("1.50", "0.00"), dadas.map { it.pontos.toString() })
        assertEquals(listOf(3, 4), dadas.map { it.maximo })
        assertEquals(listOf("d1", "d2"), dadas.map { it.item })
        assertEquals(listOf("hash-do-pacote", "hash-do-pacote"), dadas.map { it.pacote })
    }

    @Test
    fun `discursiva sem foto guardada fica de fora, sem inventar falha`() {
        val semFoto = listOf(linhas[0], LinhaDaNota("d2", rotulo = "2", worth = 4, resposta = null))

        val dadas = amostrasDaNota(
            semFoto,
            listOf(PontuacaoDada("d1", Pontos.parse("1")), PontuacaoDada("d2", Pontos.parse("2"))),
            pacote = "p",
        )

        assertEquals(listOf("1"), dadas.map { it.rotulo })
    }

    // --- gravarNotaComColeta ---

    @Test
    fun `a copia vem antes de gravar a nota`() {
        val coleta = ColetaFalsa()
        var copiouAntes = false

        val r = gravarNotaComColeta(coleta, SemRespostas, ::amostras) {
            copiouAntes = "copiar" in coleta.chamadas
            true
        }

        assertTrue(copiouAntes, "gravarNota rodou antes da copia: a imagem pode ser eliminada na confirmacao")
        assertTrue(r.gravou)
        assertEquals(emptyList<String>(), r.falhas)
    }

    @Test
    fun `coleta desligada nao copia nada, e a nota e gravada`() {
        val coleta = ColetaFalsa(ligadaAgora = false)
        var gravou = false

        val r = gravarNotaComColeta(coleta, SemRespostas, ::amostras) { gravou = true; true }

        assertEquals(emptyList<String>(), coleta.chamadas)
        assertTrue(gravou)
        assertTrue(r.gravou)
    }

    @Test
    fun `a lista de amostras so e calculada com a coleta ligada`() {
        val coleta = ColetaFalsa(ligadaAgora = false)
        var calculou = false

        gravarNotaComColeta(coleta, SemRespostas, { calculou = true; amostras() }) { true }

        assertFalse(calculou, "o hash do pacote foi calculado com a coleta desligada")
    }

    @Test
    fun `falha da copia nao impede a nota e diz qual questao`() {
        val coleta = ColetaFalsa()
        coleta.aoCopiar = { ResultadoDaCopia(listOf("id-1"), falhas = listOf("2")) }

        val r = gravarNotaComColeta(coleta, SemRespostas, ::amostras) { true }

        assertTrue(r.gravou)
        assertEquals(listOf("2"), r.falhas)
        assertEquals(emptyList<String>(), coleta.eliminadas, "a nota foi gravada: as amostras ficam")
    }

    @Test
    fun `excecao da copia nao impede a nota`() {
        val coleta = ColetaFalsa()
        coleta.aoCopiar = { throw IllegalStateException("disco cheio") }

        val r = gravarNotaComColeta(coleta, SemRespostas, ::amostras) { true }

        assertTrue(r.gravou)
        assertEquals(listOf(TODAS), r.falhas)
    }

    @Test
    fun `excecao ao montar as amostras nao impede a nota`() {
        val coleta = ColetaFalsa()

        val r = gravarNotaComColeta(coleta, SemRespostas, { throw IllegalStateException("hash") }) { true }

        assertTrue(r.gravou)
        assertEquals(listOf(TODAS), r.falhas)
    }

    @Test
    fun `nota nao gravada elimina as amostras copiadas por ela`() {
        val coleta = ColetaFalsa()

        val r = gravarNotaComColeta(coleta, SemRespostas, ::amostras) { false }

        assertFalse(r.gravou)
        assertEquals(listOf("id-1", "id-2"), coleta.eliminadas)
    }

    @Test
    fun `nota gravada nao elimina amostra nenhuma`() {
        val coleta = ColetaFalsa()

        gravarNotaComColeta(coleta, SemRespostas, ::amostras) { true }

        assertEquals(emptyList<String>(), coleta.eliminadas)
    }
}
```

- [ ] **Step 2: Ver o teste falhar (compilação)**

Run (da raiz do repositório): `./gradlew :apps:android:testDebugUnitTest`
Expected: FAIL na compilação — `Unresolved reference: ColetaDoCorpus`, `amostrasDaNota`, `gravarNotaComColeta`, `TODAS`.

- [ ] **Step 3: Implementar a interface**

Criar `apps/android/src/main/kotlin/com/platos/android/corpus/ColetaDoCorpus.kt`:

```kotlin
package com.platos.android.corpus

import com.platos.android.scan.RespostasGuardadas
import com.platos.domain.scoring.Pontos

/**
 * Uma discursiva a copiar para o corpus (`slice-5d-corpus-de-medicao`): o arquivo em `respostas/` (so para ler a
 * imagem; **nunca vai para a amostra**), o numero impresso da questao (so para dizer qual falhou), a pontuacao dada a
 * ela, o que ela vale, o hash do pacote e o item.
 */
data class AmostraACopiar(
    val arquivo: String,
    val rotulo: String,
    val pontos: Pontos,
    val maximo: Int,
    val pacote: String,
    val item: String,
)

/** O que a copia fez: os ids das amostras criadas e o rotulo de cada questao que **nao** foi copiada. */
data class ResultadoDaCopia(val ids: List<String>, val falhas: List<String>) {
    companion object {
        val NADA = ResultadoDaCopia(emptyList(), emptyList())
    }
}

/**
 * A coleta do corpus de medicao. **Existe so para o APK de depuracao funcionar**: a implementacao real vive em
 * `src/debug`, e o release recebe [SemColeta] pela fabrica `coletaDoCorpus` de `src/release`.
 *
 * **Nenhum metodo lanca.** Falha de copia vira [ResultadoDaCopia.falhas]; falha de eliminacao deixa o arquivo para a
 * proxima eliminacao. A coleta nunca derruba a nota, nunca impede o aplicativo de abrir e nunca impede sair.
 */
interface ColetaDoCorpus {
    /** O interruptor, lido agora. Desligado por padrao. */
    fun ligada(): Boolean

    /** Copia cada amostra, completa ou inexistente. Le a imagem de [respostas]. */
    fun copiar(amostras: List<AmostraACopiar>, respostas: RespostasGuardadas): ResultadoDaCopia

    /** Elimina as amostras de [ids] (a nota que nao foi gravada nao deixa amostra). */
    fun eliminar(ids: List<String>)

    /** Elimina o que tem 30 dias ou mais, e o residuo de copia interrompida. Roda com o interruptor ligado ou nao. */
    fun eliminarVencidas(agora: Long)

    /** Elimina a pasta inteira: sair da sessao e a revogacao (a amostra e copia, e nao o unico exemplar). */
    fun eliminarTodas()
}

/** A coleta que nao faz nada: o release, e os testes que nao a exercitam. */
object SemColeta : ColetaDoCorpus {
    override fun ligada() = false
    override fun copiar(amostras: List<AmostraACopiar>, respostas: RespostasGuardadas) = ResultadoDaCopia.NADA
    override fun eliminar(ids: List<String>) = Unit
    override fun eliminarVencidas(agora: Long) = Unit
    override fun eliminarTodas() = Unit
}
```

- [ ] **Step 4: Implementar a regra**

Criar `apps/android/src/main/kotlin/com/platos/android/corpus/CopiaDaNota.kt`:

```kotlin
package com.platos.android.corpus

import com.platos.android.scan.LinhaDaNota
import com.platos.android.scan.RespostasGuardadas
import com.platos.domain.scoring.PontuacaoDada

/** O aviso quando nem as amostras puderam ser montadas ou a copia inteira falhou. */
internal const val TODAS = "todas"

/**
 * As discursivas da folha como amostras: cada linha casa com a pontuacao dada a **ela** (pela questao). Linha sem foto
 * guardada (caderno anterior a 5c-1) fica de fora, sem inventar falha. A pontuacao **zero** e uma pontuacao.
 */
internal fun amostrasDaNota(
    linhas: List<LinhaDaNota>,
    pontuacoes: List<PontuacaoDada>,
    pacote: String,
): List<AmostraACopiar> {
    val dadas = pontuacoes.associate { it.questionId to it.earned }
    return linhas.mapNotNull { linha ->
        val resposta = linha.resposta ?: return@mapNotNull null
        val pontos = dadas[linha.questionId] ?: return@mapNotNull null
        AmostraACopiar(resposta.arquivo, linha.rotulo, pontos, linha.worth, pacote, linha.questionId)
    }
}

/** O que a gravacao com coleta decidiu: se a nota foi gravada, e quais questoes nao foram copiadas. */
data class GravacaoComColeta(val gravou: Boolean, val falhas: List<String>)

/**
 * A ordem da cooperacao entre a nota e a coleta (`slice-5d-corpus-de-medicao`, spec `scan-session`):
 *
 * 1. **copia primeiro.** A imagem da resposta e eliminada quando o servidor confirma a nota, e o envio em segundo
 *    plano pode confirmar assim que a nota esta duravel; copiar depois perderia essa corrida.
 * 2. **grava a nota**, e e ela que decide o sucesso: a falha da copia vira aviso, nunca falha da nota.
 * 3. **nota nao gravada elimina as amostras que a copia acabou de criar**: nota que nao existe nao deixa amostra.
 *
 * [amostras] e lazy de proposito: calcular o hash do pacote custa, e com a coleta desligada nao ha o que copiar.
 * Bloqueante: quem chama a poe em `Dispatchers.IO`.
 */
internal fun gravarNotaComColeta(
    coleta: ColetaDoCorpus,
    respostas: RespostasGuardadas,
    amostras: () -> List<AmostraACopiar>,
    gravar: () -> Boolean,
): GravacaoComColeta {
    val copia = copiarSeLigada(coleta, respostas, amostras)
    val gravou = gravar()
    if (!gravou && copia.ids.isNotEmpty()) {
        try {
            coleta.eliminar(copia.ids)
        } catch (e: Exception) {
            // Fica para a eliminacao por prazo: a amostra e uma copia, e o prazo a alcanca.
        }
    }
    return GravacaoComColeta(gravou, copia.falhas)
}

private fun copiarSeLigada(
    coleta: ColetaDoCorpus,
    respostas: RespostasGuardadas,
    amostras: () -> List<AmostraACopiar>,
): ResultadoDaCopia = try {
    if (!coleta.ligada()) {
        ResultadoDaCopia.NADA
    } else {
        val aCopiar = amostras()
        if (aCopiar.isEmpty()) ResultadoDaCopia.NADA else coleta.copiar(aCopiar, respostas)
    }
} catch (e: Exception) {
    ResultadoDaCopia(emptyList(), listOf(TODAS))
}
```

- [ ] **Step 5: Ver o teste passar**

Run: `./gradlew :apps:android:testDebugUnitTest`
Expected: PASS. Conferir o `timestamp` de `apps/android/build/test-results/testDebugUnitTest/TEST-com.platos.android.corpus.CopiaDaNotaTest.xml` (P3): é de agora, e a classe tem 9 casos, 0 falhas. Rode também `./gradlew :apps:android:testReleaseUnitTest` (a regra e a interface estão em `src/main`): deve passar com o mesmo `CopiaDaNotaTest`.

- [ ] **Step 6: Ver cada garantia falhar (rigorous.md §3, P9, P10)**

Uma mutação por vez, cada uma revertida e o teste **rodado de novo** antes da próxima:

1. Em `gravarNotaComColeta`, mover `val copia = copiarSeLigada(...)` para **depois** de `val gravou = gravar()`. Esperado: vermelho em `a copia vem antes de gravar a nota`, e **só** nele e nos que dependem da ordem (`nota nao gravada elimina...` continua verde: leia a mensagem).
2. Apagar o bloco `if (!gravou && copia.ids.isNotEmpty()) { ... }`. Esperado: vermelho **só** em `nota nao gravada elimina as amostras copiadas por ela`.
3. Em `copiarSeLigada`, remover o `try/catch` por inteiro (deixar só o corpo do `try`). Esperado: vermelho em `excecao da copia nao impede a nota` e em `excecao ao montar as amostras nao impede a nota`.
4. Em `copiarSeLigada`, remover o `if (!coleta.ligada())`. Esperado: vermelho em `coleta desligada nao copia nada` e em `a lista de amostras so e calculada com a coleta ligada`.

Reverter cada uma (`git checkout -- apps/android/src/main/kotlin/com/platos/android/corpus/CopiaDaNota.kt`) e **rodar de novo** `./gradlew :apps:android:testDebugUnitTest`: tudo verde. Registrar os conjuntos que caíram para a cobertura (Tarefa 7).

- [ ] **Step 7: Commit**

```bash
git add apps/android/src/main/kotlin/com/platos/android/corpus apps/android/src/test/kotlin/com/platos/android/corpus
git commit -m "feat(corpus): o contrato da coleta e a ordem da copia da nota

A interface ColetaDoCorpus (nenhum metodo lanca) e SemColeta ficam em src/main;
gravarNotaComColeta copia antes de gravar a nota, nao deixa a falha da copia
derrubar a nota e elimina as amostras de uma nota nao gravada. So regra pura e
testes de JVM; nada liga isto a ScanActivity ainda.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 2: A coleta de depuração e o formato (`src/debug`)

**Files:**
- Create: `fixtures/corpus/amostra-exemplo.json`
- Create: `apps/android/src/debug/kotlin/com/platos/android/corpus/AmostraDoCorpus.kt`
- Create: `apps/android/src/debug/kotlin/com/platos/android/corpus/ColetaDoCorpusEmArquivo.kt`
- Create: `apps/android/src/debug/kotlin/com/platos/android/corpus/FabricaDaColeta.kt`
- Create: `apps/android/src/release/kotlin/com/platos/android/corpus/FabricaDaColeta.kt`
- Test: `apps/android/src/testDebug/kotlin/com/platos/android/corpus/ColetaDoCorpusEmArquivoTest.kt`

**Interfaces:**
- Consumes: `ColetaDoCorpus`, `AmostraACopiar`, `ResultadoDaCopia` (Tarefa 1); `RespostasGuardadas`; `RetencaoDaResposta.PRAZO_DIAS`.
- Produces:
  - `@Serializable data class AmostraDoCorpus(val versao: Int = 1, val pontos: String, val maximo: Int, val pacote: String, val item: String, val referencia: String? = null, val descartar: Boolean = false)` com `AmostraDoCorpus.json` (`encodeDefaults = true`) e `AmostraDoCorpus.VERSAO`.
  - `class ColetaDoCorpusEmArquivo(raiz: File, escrever: (File, ByteArray) -> Unit = ...) : ColetaDoCorpus` com `companion { const val MARCADOR = "coleta-ligada"; fun diretorioDe(filesDir: File): File }`.
  - `fun coletaDoCorpus(filesDir: File): ColetaDoCorpus` em **dois** arquivos de mesmo pacote: debug devolve a real, release devolve `SemColeta`.

- [ ] **Step 1: Criar o literal do formato**

Criar `fixtures/corpus/amostra-exemplo.json` (uma linha, **sem** quebra de linha no fim). O arquivo é o **dono único** do formato: o teste Kotlin o compara com a serialização, e `tools/corpus` lê as chaves dele.

```json
{"versao":1,"pontos":"1.50","maximo":2,"pacote":"hash-de-exemplo","item":"d1","referencia":null,"descartar":false}
```

Run: `printf '%s' '{"versao":1,"pontos":"1.50","maximo":2,"pacote":"hash-de-exemplo","item":"d1","referencia":null,"descartar":false}' > fixtures/corpus/amostra-exemplo.json` (cria sem newline; a pasta `fixtures/corpus/` precisa existir: `mkdir -p fixtures/corpus` antes).

- [ ] **Step 2: Escrever o teste que falha**

Criar `apps/android/src/testDebug/kotlin/com/platos/android/corpus/ColetaDoCorpusEmArquivoTest.kt`:

```kotlin
package com.platos.android.corpus

import com.platos.android.scan.RespostaDoQuadro
import com.platos.android.scan.RespostasGuardadas
import com.platos.domain.scoring.Pontos
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * A coleta de depuracao no disco (`slice-5d-corpus-de-medicao`, spec `measurement-corpus`). Roda so no
 * `testDebugUnitTest`: a classe nao existe no release.
 */
class ColetaDoCorpusEmArquivoTest {

    private lateinit var raiz: File
    private val dia = 86_400_000L
    private val agora = 100 * dia
    private val png = byteArrayOf(1, 2, 3, 4, 5)

    @BeforeEach
    fun preparar() {
        raiz = Files.createTempDirectory("coleta").toFile()
    }

    @AfterEach
    fun limpar() {
        raiz.deleteRecursively()
    }

    private class Respostas(private val arquivos: Map<String, ByteArray>) : RespostasGuardadas {
        override fun gravar(png: ByteArray, capturadaEm: Long, desvioSinalizado: Boolean, foraPpm: Int): RespostaDoQuadro =
            RespostaDoQuadro.Recusada("falso")

        override fun ler(arquivo: String): ByteArray? = arquivos[arquivo]
        override fun existe(arquivo: String) = arquivo in arquivos
        override fun listar() = arquivos.keys.sorted()
        override fun eliminar(arquivo: String) = Unit
    }

    private val respostas = Respostas(mapOf("origem-111.png" to byteArrayOf(1, 2, 3, 4, 5), "origem-222.png" to byteArrayOf(9, 9)))

    private fun amostra(arquivo: String = "origem-111.png", rotulo: String = "1", pontos: String = "1.5", maximo: Int = 2) =
        AmostraACopiar(arquivo, rotulo, Pontos.parse(pontos), maximo, "hash-de-exemplo", "d1")

    private fun coleta(escrever: (File, ByteArray) -> Unit = { f, b -> f.writeBytes(b) }) =
        ColetaDoCorpusEmArquivo(raiz, escrever)

    private val corpus get() = File(raiz, "corpus")

    private fun nomes() = corpus.list()?.sorted().orEmpty()

    private fun aosDias(n: Long) = agora - n * dia

    private fun copiarUma(): String = coleta().copiar(listOf(amostra()), respostas).ids.single()

    private fun envelhecer(id: String, ate: Long) {
        assertTrue(File(corpus, "$id.json").setLastModified(ate))
    }

    // --- o interruptor ---

    @Test
    fun `o interruptor vem desligado e liga com o arquivo marcador`() {
        assertFalse(coleta().ligada())

        File(raiz, ColetaDoCorpusEmArquivo.MARCADOR).writeText("")

        assertTrue(coleta().ligada())
    }

    // --- a amostra ---

    @Test
    fun `copiar grava a foto com os mesmos bytes e o arquivo de dados do formato`() {
        val r = coleta().copiar(listOf(amostra()), respostas)

        val id = r.ids.single()
        assertEquals(emptyList<String>(), r.falhas)
        assertEquals(listOf("$id.json", "$id.png"), nomes())
        assertEquals(png.toList(), File(corpus, "$id.png").readBytes().toList())
        assertEquals(
            """{"versao":1,"pontos":"1.50","maximo":2,"pacote":"hash-de-exemplo","item":"d1","referencia":null,"descartar":false}""",
            File(corpus, "$id.json").readText(),
        )
    }

    @Test
    fun `o formato bate com o literal do repositorio`() {
        val esperado = File(
            System.getProperty("platos.fixtures") ?: error("propriedade `platos.fixtures` nao definida pelo build"),
            "corpus/amostra-exemplo.json",
        ).readText().trim()

        val dado = AmostraDoCorpus.json.encodeToString(
            AmostraDoCorpus.serializer(),
            AmostraDoCorpus(pontos = "1.50", maximo = 2, pacote = "hash-de-exemplo", item = "d1"),
        )

        assertEquals(esperado, dado)
    }

    @Test
    fun `a pontuacao zero e copiada como zero`() {
        val id = coleta().copiar(listOf(amostra(pontos = "0")), respostas).ids.single()

        assertTrue("\"pontos\":\"0.00\"" in File(corpus, "$id.json").readText())
    }

    @Test
    fun `o nome nao deriva do arquivo de origem e nada da origem entra na amostra`() {
        val id = coleta().copiar(listOf(amostra(arquivo = "origem-111.png")), respostas).ids.single()

        for (nome in nomes()) assertFalse("origem" in nome, "o nome '$nome' deriva da origem")
        assertFalse("origem" in File(corpus, "$id.json").readText())
        assertFalse("respostas" in File(corpus, "$id.json").readText())
    }

    @Test
    fun `dois identificadores aleatorios e independentes`() {
        val r = coleta().copiar(listOf(amostra(), amostra(arquivo = "origem-222.png", rotulo = "2")), respostas)

        val (a, b) = r.ids
        assertNotEquals(a, b)
        assertEquals(4, UUID.fromString(a).version())
        assertEquals(4, UUID.fromString(b).version())
    }

    @Test
    fun `foto que nao existe vira falha com o rotulo, sem deixar nada`() {
        val r = coleta().copiar(listOf(amostra(arquivo = "sumiu.png", rotulo = "7")), respostas)

        assertEquals(emptyList<String>(), r.ids)
        assertEquals(listOf("7"), r.falhas)
        assertEquals(emptyList<String>(), nomes())
    }

    @Test
    fun `disco cheio no meio nao deixa foto sem dados nem temporario, e as outras seguem`() {
        var escritasDeDados = 0
        val coleta = coleta(escrever = { arquivo, bytes ->
            // Falha na escrita dos dados da SEGUNDA amostra, depois de a foto dela ja ter sido escrita.
            if (arquivo.name.endsWith(".json.tmp") && ++escritasDeDados == 2) throw IOException("disco cheio")
            arquivo.writeBytes(bytes)
        })

        val r = coleta.copiar(listOf(amostra(rotulo = "1"), amostra(arquivo = "origem-222.png", rotulo = "2")), respostas)

        assertEquals(listOf("2"), r.falhas, "so a segunda falha, na hora de escrever os dados")
        assertEquals(1, r.ids.size)
        val sobrou = nomes().filter { it.endsWith(".tmp") || (it.endsWith(".png") && "${it.removeSuffix(".png")}.json" !in nomes()) }
        assertEquals(emptyList<String>(), sobrou, "a falha deixou residuo")
    }

    // --- eliminar ---

    @Test
    fun `eliminar remove a foto e os dados dos ids`() {
        val coleta = coleta()
        val (a, b) = coleta.copiar(listOf(amostra(), amostra(arquivo = "origem-222.png", rotulo = "2")), respostas).ids

        coleta.eliminar(listOf(a))

        assertEquals(listOf("$b.json", "$b.png"), nomes())
    }

    @Test
    fun `eliminar sem pasta nao lanca`() {
        coleta().eliminar(listOf("qualquer"))
        coleta().eliminarVencidas(agora)
        coleta().eliminarTodas()
    }

    // --- o prazo ---

    @Test
    fun `29 dias mantem, exatamente 30 elimina, 31 elimina`() {
        val coleta = coleta()
        val mantida = copiarUma().also { envelhecer(it, aosDias(29)) }
        val noLimite = copiarUma().also { envelhecer(it, aosDias(30)) }
        val vencida = copiarUma().also { envelhecer(it, aosDias(31)) }

        coleta.eliminarVencidas(agora)

        assertEquals(listOf("$mantida.json", "$mantida.png"), nomes())
        assertFalse(File(corpus, "$noLimite.png").exists() || File(corpus, "$noLimite.json").exists())
        assertFalse(File(corpus, "$vencida.png").exists() || File(corpus, "$vencida.json").exists())
    }

    @Test
    fun `o prazo e o de RetencaoDaResposta, dono unico`() {
        assertEquals(30, com.platos.android.scan.RetencaoDaResposta.PRAZO_DIAS)
    }

    @Test
    fun `relogio anterior a criacao mantem`() {
        val id = copiarUma().also { envelhecer(it, agora + 5 * dia) }

        coleta().eliminarVencidas(agora)

        assertEquals(listOf("$id.json", "$id.png"), nomes())
    }

    @Test
    fun `foto sem dados, dados sem foto e temporario sao residuo e saem`() {
        val inteira = copiarUma()
        val semDados = UUID.randomUUID().toString().also { File(corpus, "$it.png").writeBytes(png) }
        val semFoto = UUID.randomUUID().toString().also { File(corpus, "$it.json").writeText("{}") }
        val temporario = UUID.randomUUID().toString().also { File(corpus, "$it.png.tmp").writeBytes(png) }

        coleta().eliminarVencidas(agora + dia)

        assertEquals(listOf("$inteira.json", "$inteira.png"), nomes())
        assertFalse(File(corpus, "$semDados.png").exists())
        assertFalse(File(corpus, "$semFoto.json").exists())
        assertFalse(File(corpus, "$temporario.png.tmp").exists())
    }

    @Test
    fun `arquivo estranho na pasta nao e tocado pelo prazo`() {
        val id = copiarUma().also { envelhecer(it, aosDias(40)) }
        File(corpus, "anotacoes.txt").writeText("minhas notas")

        coleta().eliminarVencidas(agora)

        assertEquals(listOf("anotacoes.txt"), nomes(), "o prazo comeu o arquivo estranho ou deixou a amostra vencida")
        assertFalse(File(corpus, "$id.png").exists())
    }

    // --- sair e revogar ---

    @Test
    fun `eliminar todas apaga a pasta inteira, inclusive o estranho`() {
        copiarUma()
        File(corpus, "anotacoes.txt").writeText("minhas notas")

        coleta().eliminarTodas()

        assertFalse(corpus.exists())
    }

    @Test
    fun `a pasta do corpus e a de respostas sao diferentes, e a do corpus cai sob filesDir`() {
        assertEquals(File(raiz, "corpus"), ColetaDoCorpusEmArquivo.diretorioDe(raiz))
        assertNotEquals(File(raiz, "respostas"), ColetaDoCorpusEmArquivo.diretorioDe(raiz))
    }
}
```

- [ ] **Step 3: Ver o teste falhar**

Run: `./gradlew :apps:android:testDebugUnitTest`
Expected: FAIL na compilação — `Unresolved reference: ColetaDoCorpusEmArquivo`, `AmostraDoCorpus`.

- [ ] **Step 4: Implementar o formato e a coleta**

Criar `apps/android/src/debug/kotlin/com/platos/android/corpus/AmostraDoCorpus.kt`:

```kotlin
package com.platos.android.corpus

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * O arquivo de dados de uma amostra do corpus (`slice-5d-corpus-de-medicao`, spec `measurement-corpus`).
 *
 * **Contrato digitado uma vez (P28).** Quem le este arquivo depois — a bancada, em outra linguagem — o faz por
 * `tools/corpus/`, e o literal que prende os dois lados e `fixtures/corpus/amostra-exemplo.json`: o teste da coleta
 * compara a serializacao com ele, e `tools/corpus` le as chaves dele. Mudar um campo aqui sem mudar o literal reprova
 * os dois.
 *
 * **Nao tem nome, turma, matricula, token, captura, caminho de arquivo, data nem hora**, e nao ha onde pô-los: a
 * garantia e de tipo. `referencia` e `descartar` nascem vazios e o mantenedor os preenche no computador.
 * `pontos` e a forma canonica de `Pontos` (`"1.50"`), decimal exata como texto.
 */
@Serializable
data class AmostraDoCorpus(
    val versao: Int = VERSAO,
    val pontos: String,
    val maximo: Int,
    val pacote: String,
    val item: String,
    val referencia: String? = null,
    val descartar: Boolean = false,
) {
    companion object {
        const val VERSAO = 1

        /** `encodeDefaults`: sem ele `versao`, `referencia` e `descartar` nao sairiam no arquivo. */
        val json = Json { encodeDefaults = true }
    }
}
```

Criar `apps/android/src/debug/kotlin/com/platos/android/corpus/ColetaDoCorpusEmArquivo.kt`:

```kotlin
package com.platos.android.corpus

import com.platos.android.scan.RespostasGuardadas
import com.platos.android.scan.RetencaoDaResposta
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * A coleta do corpus no disco (`slice-5d-corpus-de-medicao`). **So existe no APK de depuracao.**
 *
 * `filesDir/corpus/<id>.png` e `<id>.json`, com `<id>` um UUID sorteado na copia. A copia e atomica por arquivo, como
 * `RespostasEmArquivo`: temporario no mesmo diretorio e `ATOMIC_MOVE`, a foto **antes** dos dados, de modo que o
 * `.json` so existe com a foto inteira ao lado. Amostra sem o par e residuo.
 *
 * O instante da amostra e o `lastModified` do `.json` — a amostra **nao leva data no conteudo**. O interruptor e o
 * arquivo [MARCADOR] sob `filesDir`: `adb shell run-as com.platos.android touch files/coleta-ligada` liga.
 *
 * [escrever] e a costura que deixa o teste falhar no meio da escrita.
 */
class ColetaDoCorpusEmArquivo(
    private val raiz: File,
    private val escrever: (File, ByteArray) -> Unit = { arquivo, bytes -> arquivo.writeBytes(bytes) },
) : ColetaDoCorpus {

    private val diretorio = diretorioDe(raiz)

    override fun ligada(): Boolean = File(raiz, MARCADOR).isFile

    override fun copiar(amostras: List<AmostraACopiar>, respostas: RespostasGuardadas): ResultadoDaCopia {
        val ids = mutableListOf<String>()
        val falhas = mutableListOf<String>()
        for (amostra in amostras) {
            val id = copiarUma(amostra, respostas)
            if (id != null) ids += id else falhas += amostra.rotulo
        }
        return ResultadoDaCopia(ids, falhas)
    }

    private fun copiarUma(amostra: AmostraACopiar, respostas: RespostasGuardadas): String? {
        val id = UUID.randomUUID().toString()
        val foto = File(diretorio, "$id$FOTO")
        val dados = File(diretorio, "$id$DADOS")
        val fotoTemporaria = File(diretorio, "$id$FOTO$TEMPORARIO")
        val dadosTemporarios = File(diretorio, "$id$DADOS$TEMPORARIO")
        return try {
            val bytes = respostas.ler(amostra.arquivo) ?: return null
            if (!diretorio.isDirectory && !diretorio.mkdirs()) throw IOException("nao foi possivel criar o diretorio do corpus")
            val conteudo = AmostraDoCorpus.json.encodeToString(
                AmostraDoCorpus.serializer(),
                AmostraDoCorpus(
                    pontos = amostra.pontos.toString(),
                    maximo = amostra.maximo,
                    pacote = amostra.pacote,
                    item = amostra.item,
                ),
            ).toByteArray(Charsets.UTF_8)
            escrever(fotoTemporaria, bytes)
            escrever(dadosTemporarios, conteudo)
            Files.move(fotoTemporaria.toPath(), foto.toPath(), StandardCopyOption.ATOMIC_MOVE)
            Files.move(dadosTemporarios.toPath(), dados.toPath(), StandardCopyOption.ATOMIC_MOVE)
            id
        } catch (e: Exception) {
            for (arquivo in listOf(fotoTemporaria, dadosTemporarios, foto, dados)) arquivo.delete()
            null
        }
    }

    override fun eliminar(ids: List<String>) {
        for (id in ids) {
            for (sufixo in listOf(FOTO, DADOS, "$FOTO$TEMPORARIO", "$DADOS$TEMPORARIO")) {
                tentarEliminar(File(diretorio, "$id$sufixo"))
            }
        }
    }

    override fun eliminarVencidas(agora: Long) {
        val arquivos = diretorio.listFiles()?.filter { it.isFile && NOME_DA_AMOSTRA.matches(it.name) } ?: return
        val prazo = RetencaoDaResposta.PRAZO_DIAS * MILISSEGUNDOS_POR_DIA
        for ((_, grupo) in arquivos.groupBy { it.name.substringBefore('.') }) {
            val nomes = grupo.map { it.name }
            val id = grupo.first().name.substringBefore('.')
            val completo = "$id$FOTO" in nomes && "$id$DADOS" in nomes
            val temporario = nomes.any { it.endsWith(TEMPORARIO) }
            val dados = grupo.firstOrNull { it.name == "$id$DADOS" }
            val vencida = dados != null && agora - dados.lastModified() >= prazo
            if (temporario || !completo || vencida) grupo.forEach(::tentarEliminar)
        }
    }

    override fun eliminarTodas() {
        try {
            diretorio.deleteRecursively()
        } catch (e: Exception) {
            // Fica para o prazo.
        }
    }

    /** Falha de E/S num arquivo nao impede os outros: ele fica para a proxima eliminacao. */
    private fun tentarEliminar(arquivo: File) {
        try {
            Files.deleteIfExists(arquivo.toPath())
        } catch (e: Exception) {
            // Proxima eliminacao.
        }
    }

    companion object {
        /** O arquivo cuja existencia liga a coleta. Literal que so o debug contem: o release nao o tem (Tarefa 4). */
        const val MARCADOR = "coleta-ligada"

        /** O diretorio das amostras, **sob `filesDir`**, e dono unico do nome (P28). */
        fun diretorioDe(filesDir: File): File = File(filesDir, "corpus")

        private const val FOTO = ".png"
        private const val DADOS = ".json"
        private const val TEMPORARIO = ".tmp"
        private const val MILISSEGUNDOS_POR_DIA = 86_400_000L

        /** So o que a coleta escreve: um UUID e a extensao (e o temporario). O que mais houver na pasta nao e dela. */
        private val NOME_DA_AMOSTRA =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|json)(\\.tmp)?$")
    }
}
```

Criar `apps/android/src/debug/kotlin/com/platos/android/corpus/FabricaDaColeta.kt`:

```kotlin
package com.platos.android.corpus

import java.io.File

/** A coleta do aplicativo de depuracao: a real. O release tem a sua em `src/release`, que devolve [SemColeta]. */
fun coletaDoCorpus(filesDir: File): ColetaDoCorpus = ColetaDoCorpusEmArquivo(filesDir)
```

Criar `apps/android/src/release/kotlin/com/platos/android/corpus/FabricaDaColeta.kt`:

```kotlin
package com.platos.android.corpus

import java.io.File

/**
 * A coleta do aplicativo de release: nenhuma. O codigo que copia resposta para o corpus **nao existe** neste APK
 * (`slice-5d-corpus-de-medicao`; verificado por `verificarApkSemColeta`).
 */
@Suppress("UNUSED_PARAMETER")
fun coletaDoCorpus(filesDir: File): ColetaDoCorpus = SemColeta
```

- [ ] **Step 5: Ver o teste passar, nas duas variantes**

Run: `./gradlew :apps:android:testDebugUnitTest :apps:android:testReleaseUnitTest`
Expected: PASS nas duas. O debug roda `CopiaDaNotaTest` **e** `ColetaDoCorpusEmArquivoTest` (a guarda `TodoTesteDeclaradoRoda` imprime "N metodo(s) com @Test, todos com resultado"); o release roda só a primeira, e **compila** sem `ColetaDoCorpusEmArquivo` (prova de que `src/main` não depende da classe real). Conferir o `timestamp` dos XML (P3).

- [ ] **Step 6: Ver cada garantia falhar**

1. Em `eliminarVencidas`, trocar `>= prazo` por `> prazo`. Esperado: vermelho **só** em `29 dias mantem, exatamente 30 elimina, 31 elimina`.
2. Em `copiarUma`, trocar o nome da foto para `File(diretorio, amostra.arquivo)`. Esperado: vermelho em `o nome nao deriva do arquivo de origem...`.
3. Em `eliminarVencidas`, trocar o filtro `NOME_DA_AMOSTRA.matches(it.name)` por `true`. Esperado: vermelho **só** em `arquivo estranho na pasta nao e tocado pelo prazo`.
4. Em `copiarUma`, remover o `for (arquivo in listOf(fotoTemporaria, ...)) arquivo.delete()` do `catch`. Esperado: vermelho em `disco cheio no meio nao deixa foto sem dados nem temporario...`.
5. Em `AmostraDoCorpus`, acrescentar um campo `val extra: String = ""`. Esperado: vermelho em `o formato bate com o literal do repositorio` **e** em `copiar grava a foto com os mesmos bytes...`.
6. Em `eliminarVencidas`, trocar `agora - dados.lastModified() >= prazo` por `Math.abs(agora - dados.lastModified()) >= prazo`. Esperado: vermelho **só** em `relogio anterior a criacao mantem`.

Reverter cada uma e **rodar de novo** `:apps:android:testDebugUnitTest`: tudo verde. Anotar os conjuntos que caíram (Tarefa 7).

- [ ] **Step 7: Commit**

```bash
git add fixtures/corpus apps/android/src/debug apps/android/src/release apps/android/src/testDebug
git commit -m "feat(corpus): a coleta de depuracao copia a resposta com a nota, e o release nao a tem

ColetaDoCorpusEmArquivo (so em src/debug) copia foto e dados de forma atomica,
elimina por prazo de 30 dias, residuo e tudo ao sair. O formato do JSON tem o
literal unico em fixtures/corpus/amostra-exemplo.json. src/release tem a fabrica
que devolve SemColeta. Interruptor: o arquivo filesDir/coleta-ligada.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 3: As ligações (sair, revogar, varredura, `ScanActivity`)

**Files:**
- Modify: `apps/android/src/main/kotlin/com/platos/android/session/DeviceSession.kt` (construtor, `sair`, `revogar`)
- Modify: `apps/android/src/main/kotlin/com/platos/android/session/SessaoActivity.kt:109-111`
- Modify: `apps/android/src/main/kotlin/com/platos/android/scan/VarreduraDoAparelho.kt` (`varrerAgora`)
- Modify: `apps/android/src/main/kotlin/com/platos/android/scan/ScanActivity.kt` (`darNota`, campo `coleta`) — **CRLF: use `Edit`**
- Test: `apps/android/src/test/kotlin/com/platos/android/session/DeviceSessionTest.kt` (3 testes)
- Test: `apps/android/src/test/kotlin/com/platos/android/corpus/NenhumEnvioLeOCorpusTest.kt` (novo)
- Test (instrumentado): `apps/android/src/androidTest/kotlin/com/platos/android/corpus/VarreduraEliminaOCorpusInstrumentedTest.kt`, `ColetaNaNotaInstrumentedTest.kt` (novos)

**Interfaces:**
- Consumes: `ColetaDoCorpus`, `SemColeta`, `coletaDoCorpus(filesDir)`, `amostrasDaNota`, `gravarNotaComColeta`, `GravacaoComColeta` (Tarefas 1 e 2).
- Produces: `DeviceSession(guardada, pacotes, visoes, rosters, corpus: ColetaDoCorpus = SemColeta)`.

- [ ] **Step 1: Escrever os testes de `DeviceSession` que falham**

Em `DeviceSessionTest.kt`, acrescentar, **antes** do `private val escola`, o falso (junto dos outros falsos privados):

```kotlin
    /** O corpus de mentira: registra quantas vezes foi mandado eliminar tudo, e pode lancar. */
    private class CorpusFalso(private val lanca: Boolean = false) : com.platos.android.corpus.ColetaDoCorpus {
        var eliminadasTodas = 0
            private set

        override fun ligada() = false
        override fun copiar(
            amostras: List<com.platos.android.corpus.AmostraACopiar>,
            respostas: com.platos.android.scan.RespostasGuardadas,
        ) = com.platos.android.corpus.ResultadoDaCopia.NADA

        override fun eliminar(ids: List<String>) = Unit
        override fun eliminarVencidas(agora: Long) = Unit
        override fun eliminarTodas() {
            eliminadasTodas++
            if (lanca) throw IllegalStateException("falha ao eliminar")
        }
    }
```

e os testes, ao fim da classe (antes do `}` final):

```kotlin
    // --- slice-5d: o corpus e copia, e some com a sessao ---

    @Test
    fun sair_elimina_as_amostras_do_corpus() {
        val corpus = CorpusFalso()
        val sessao = DeviceSession(Guardada(escola.id), PacotesFalsos(), VisoesFalsas(), RostersFalsos(), corpus)
        sessao.abrir(temSessaoGuardada = true)

        sessao.sair()

        assertEquals(1, corpus.eliminadasTodas, "sair nao eliminou o corpus")
    }

    @Test
    fun revogacao_observada_elimina_as_amostras_do_corpus() {
        val corpus = CorpusFalso()
        val visao = VisaoDaOrganizacao(escola, listOf(prova), vistaEm = 1_757_000_000_000)
        val sessao = DeviceSession(Guardada(escola.id), PacotesFalsos(), VisoesFalsas(visao), RostersFalsos(), corpus)
        sessao.abrir(temSessaoGuardada = true)

        sessao.aoConsultarOrganizacoes(ResultadoDasOrganizacoes.Chegaram(listOf(pessoal)))

        assertEquals(1, corpus.eliminadasTodas, "a revogacao nao eliminou o corpus")
    }

    @Test
    fun sair_nao_para_se_a_eliminacao_do_corpus_falha() {
        val guardada = Guardada(escola.id)
        val corpus = CorpusFalso(lanca = true)
        val sessao = DeviceSession(guardada, PacotesFalsos(), VisoesFalsas(), RostersFalsos(), corpus)
        sessao.abrir(temSessaoGuardada = true)

        sessao.sair()

        assertTrue(guardada.credencialApagada, "a falha do corpus impediu apagar a credencial")
        assertTrue(sessao.state is DeviceState.Entrada)
    }
```

- [ ] **Step 2: Ver falhar**

Run: `./gradlew :apps:android:testDebugUnitTest`
Expected: FAIL na compilação (`DeviceSession` não recebe o quinto argumento).

- [ ] **Step 3: Ligar `DeviceSession`**

Em `DeviceSession.kt`: acrescentar `import com.platos.android.corpus.ColetaDoCorpus` e `import com.platos.android.corpus.SemColeta`; no construtor:

```kotlin
class DeviceSession(
    private val guardada: SessaoGuardada,
    private val pacotes: PacotesGuardados,
    private val visoes: VisoesGuardadas,
    private val rosters: RostersGuardados,
    private val corpus: ColetaDoCorpus = SemColeta,
) {
```

Em `sair`, depois do `if (organizacao != null) { ... }` e **antes** de `state = DeviceState.Entrada(...)`:

```kotlin
        eliminarCorpus()
```

Em `revogar`, depois de `rosters.apagarDaOrganizacao(organizacao)`:

```kotlin
        eliminarCorpus()
```

e, antes do `}` final da classe, o método:

```kotlin
    /**
     * As amostras do corpus (`slice-5d-corpus-de-medicao`) sao **copia**, e nao o unico exemplar de um trabalho: ao
     * contrario do resultado pendente, saem com a sessao. A coleta nunca lanca; o `try` e a rede para que uma
     * implementacao que lance nao impeca sair nem a revogacao, que ja apagaram a credencial e o cache.
     */
    private fun eliminarCorpus() {
        try {
            corpus.eliminarTodas()
        } catch (e: Exception) {
            // Fica para o prazo de 30 dias.
        }
    }
```

- [ ] **Step 4: Ver passar**

Run: `./gradlew :apps:android:testDebugUnitTest`
Expected: PASS. Os 40 usos antigos de `DeviceSession(...)` compilam pelo valor padrão.

- [ ] **Step 5: Ver falhar (mutações) e reverter**

1. Apagar a chamada `eliminarCorpus()` de `sair`. Esperado: vermelho **só** em `sair_elimina_as_amostras_do_corpus`.
2. Apagar a de `revogar`. Esperado: vermelho **só** em `revogacao_observada_elimina_as_amostras_do_corpus`.
3. Tirar o `try/catch` de `eliminarCorpus`. Esperado: vermelho **só** em `sair_nao_para_se_a_eliminacao_do_corpus_falha`.

Reverter e rodar de novo.

- [ ] **Step 6: Ligar `SessaoActivity` e a varredura**

Em `SessaoActivity.kt`, a linha `sessao = DeviceSession(guardada, pacotes, visoes, rosters)` passa a:

```kotlin
        sessao = DeviceSession(guardada, pacotes, visoes, rosters, coletaDoCorpus(filesDir))
```

com `import com.platos.android.corpus.coletaDoCorpus`.

Em `VarreduraDoAparelho.kt`, em `varrerAgora`, **entre** o `val varredura = try { ... } catch ...` e `registrarVarredura(varredura)`:

```kotlin
    // As amostras do corpus (`slice-5d-corpus-de-medicao`) tem o mesmo teto de 30 dias, na abertura e em segundo plano.
    // Nunca lanca, e nao depende de o interruptor estar ligado.
    try {
        coletaDoCorpus(context.filesDir).eliminarVencidas(System.currentTimeMillis())
    } catch (e: Exception) {
        // Proxima varredura.
    }
```

com `import com.platos.android.corpus.coletaDoCorpus`.

- [ ] **Step 7: O teste instrumentado da varredura**

Criar `apps/android/src/androidTest/kotlin/com/platos/android/corpus/VarreduraEliminaOCorpusInstrumentedTest.kt`:

```kotlin
package com.platos.android.corpus

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.android.scan.CadernosEmRoom
import com.platos.android.scan.varrerAgora
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A varredura da porta de entrada elimina as amostras vencidas do corpus, sem o interruptor ligado
 * (`slice-5d-corpus-de-medicao`). Atravessa `varrerAgora` e o disco de verdade; a regra do prazo ja e provada em JVM.
 */
@RunWith(AndroidJUnit4::class)
class VarreduraEliminaOCorpusInstrumentedTest {

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private val pasta = ColetaDoCorpusEmArquivo.diretorioDe(contexto.filesDir)
    private val dia = 86_400_000L

    @Before
    fun preparar() {
        CadernosEmRoom.reiniciarParaTeste()
        contexto.deleteDatabase("caderno.db")
        pasta.deleteRecursively()
    }

    @After
    fun limpar() {
        CadernosEmRoom.reiniciarParaTeste()
        contexto.deleteDatabase("caderno.db")
        pasta.deleteRecursively()
    }

    private fun amostra(dadosHaDias: Long): String {
        pasta.mkdirs()
        val id = UUID.randomUUID().toString()
        File(pasta, "$id.png").writeBytes(byteArrayOf(1, 2, 3))
        File(pasta, "$id.json").also {
            it.writeText("{}")
            assertTrue(it.setLastModified(System.currentTimeMillis() - dadosHaDias * dia))
        }
        return id
    }

    @Test
    fun a_varredura_elimina_a_amostra_vencida_e_mantem_a_recente() {
        val vencida = amostra(dadosHaDias = 31)
        val recente = amostra(dadosHaDias = 1)
        assertFalse("o interruptor esta ligado e o teste nao diria nada", File(contexto.filesDir, ColetaDoCorpusEmArquivo.MARCADOR).isFile)

        varrerAgora(contexto)

        assertFalse(File(pasta, "$vencida.png").exists() || File(pasta, "$vencida.json").exists())
        assertTrue(File(pasta, "$recente.png").isFile && File(pasta, "$recente.json").isFile)
    }
}
```

- [ ] **Step 8: Ligar `ScanActivity.darNota`**

Em `ScanActivity.kt` (use `Edit`, o arquivo tem CRLF misto):

1. Imports: `com.platos.android.corpus.amostrasDaNota`, `com.platos.android.corpus.coletaDoCorpus`, `com.platos.android.corpus.gravarNotaComColeta`, `com.platos.domain.scoring.PartialScoringOutcome`, `android.widget.Toast` (acrescente os que faltarem; confira com `grep -n "^import" ScanActivity.kt`).
2. Depois de `private lateinit var respostas: RespostasGuardadas` (l.86), o campo:

```kotlin
    /** A coleta do corpus (`slice-5d-corpus-de-medicao`): a real no debug, `SemColeta` no release. */
    private val coleta by lazy { coletaDoCorpus(filesDir) }
```

3. Em `darNota`, o trecho que hoje é:

```kotlin
                lifecycleScope.launch {
                    val gravou = withContext(Dispatchers.IO + NonCancellable) {
                        gravarNota(pendentes, cadernos, pendente, r.cadernoCorrigido, examPackage.meta.examId) {
                            EnvioDeResultadosWorker.agendar(applicationContext, organizacao)
                        }
                    }
                    if (gravou) {
```

passa a:

```kotlin
                lifecycleScope.launch {
                    val gravacao = withContext(Dispatchers.IO + NonCancellable) {
                        // A copia para o corpus vem ANTES da nota (a imagem some quando o servidor a confirma) e nao a
                        // decide: a falha dela vira aviso. Com a coleta desligada, e no release, nada acontece.
                        gravarNotaComColeta(
                            coleta,
                            respostas,
                            amostras = {
                                val parcial = (r.cadernoCorrigido.parcial as? PartialScoringOutcome.Scored)?.partial
                                    ?: error("a nota foi dada sobre uma parcial apurada")
                                amostrasDaNota(
                                    linhasDaNota(r.cadernoCorrigido, parcial),
                                    pontuacoes,
                                    examPackage.contentHash(),
                                )
                            },
                        ) {
                            gravarNota(pendentes, cadernos, pendente, r.cadernoCorrigido, examPackage.meta.examId) {
                                EnvioDeResultadosWorker.agendar(applicationContext, organizacao)
                            }
                        }
                    }
                    if (gravacao.falhas.isNotEmpty()) {
                        Toast.makeText(
                            applicationContext,
                            "Coleta: nao copiou a questao ${gravacao.falhas.joinToString(", ")}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    if (gravacao.gravou) {
```

(`else` do `if (gravou)` continua como está, com `gravacao.gravou` no lugar de `gravou`; **não** deixe a variável `gravou` antiga referenciada.)

- [ ] **Step 9: O teste que prova que nenhum código de rede lê o corpus**

Criar `apps/android/src/test/kotlin/com/platos/android/corpus/NenhumEnvioLeOCorpusTest.kt`:

```kotlin
package com.platos.android.corpus

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Nenhum codigo que fala com a rede toca o corpus (`slice-5d-corpus-de-medicao`, spec `measurement-corpus`: "nada
 * passa pelo servidor"). E o `grep` que a 5c-1 registrou para `respostas/`, agora num teste: uma leitura nova escrita
 * amanha reprova na hora, e nao na proxima vez que alguem lembrar de rodar o `grep`.
 *
 * **O que ele nao prova:** que nenhum caminho novo, fora destes quatro pacotes, leia o corpus. `ARespostaNaoSaiDoAparelhoTest`
 * continua afirmando sobre o corpo do envio (P16: esta e a camada vizinha).
 */
class NenhumEnvioLeOCorpusTest {

    private val raiz = File("src/main/kotlin/com/platos/android")
    private val pacotesDeRede = listOf("api", "outbox", "net", "auth")
    private val proibidos = listOf("corpus", "ColetaDoCorpus", "AmostraDoCorpus", "coleta-ligada")

    @Test
    fun `api, outbox, net e auth nao citam o corpus`() {
        val arquivos = pacotesDeRede.flatMap { pacote ->
            File(raiz, pacote).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
        // Piso (P13): varredura que nao abre arquivo nenhum passaria em silencio.
        assertTrue(arquivos.size >= 10, "a varredura abriu so ${arquivos.size} arquivo(s): o caminho relativo mudou?")

        val achados = arquivos.flatMap { arquivo ->
            val texto = arquivo.readText()
            proibidos.filter { it in texto }.map { "${arquivo.path}: $it" }
        }

        assertEquals(emptyList<String>(), achados)
    }
}
```

- [ ] **Step 10: Rodar a suíte de unidade nas duas variantes**

Run: `./gradlew :apps:android:testDebugUnitTest :apps:android:testReleaseUnitTest`
Expected: PASS. Se `NenhumEnvioLeOCorpusTest` reclamar do piso, o diretório de trabalho do teste não é `apps/android`: confira com a mensagem e ajuste `raiz` com `System.getProperty("user.dir")`, **sem** baixar o piso.

- [ ] **Step 11: Ver as ligações falharem**

1. Em `darNota`, mover a chamada para depois de `gravarNota`? A ordem é a de `gravarNotaComColeta` (já provada na Tarefa 1); aqui, mutar a **ligação**: trocar `coleta` por `SemColeta` na chamada de `gravarNotaComColeta`. Esperado: o instrumentado `ColetaNaNotaInstrumentedTest` (Step 12) cai; a suíte de unidade **não** (P16: a camada vizinha não a prova).
2. Em `varrerAgora`, apagar o bloco do corpus. Esperado: vermelho **só** em `VarreduraEliminaOCorpusInstrumentedTest`.
3. Pôr `import com.platos.android.corpus.coletaDoCorpus` + uma chamada em `api/ApiPlatos.kt`. Esperado: vermelho em `NenhumEnvioLeOCorpusTest`, nomeando o arquivo.

Reverter e rodar de novo.

- [ ] **Step 12: O teste instrumentado da `ScanActivity`**

Criar `apps/android/src/androidTest/kotlin/com/platos/android/corpus/ColetaNaNotaInstrumentedTest.kt`:

```kotlin
package com.platos.android.corpus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platos.android.outbox.ResultadosEmRoom
import com.platos.android.pacote.PacotesEmArquivo
import com.platos.android.scan.CadernosEmRoom
import com.platos.android.scan.ScanActivity
import com.platos.android.vision.FolhaDiscursivaRenderizada
import com.platos.domain.exam.ExamPackage
import com.platos.domain.scoring.PontuacaoDada
import com.platos.domain.scoring.Pontos
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader

/**
 * A coleta na `ScanActivity` **aberta de verdade** (`slice-5d-corpus-de-medicao`): com o interruptor ligado, dar a
 * nota deixa uma amostra por discursiva, com a pontuacao dada a cada uma e os bytes da foto; desligado, nao deixa nada.
 *
 * Mesmo molde de `RespostaNaAtividadeInstrumentedTest`: o quadro vem da folha renderizada, passa pelo analisador da
 * camera e chega a sessao por `entregarQuadro`. Exige a permissao de camera (sem ela a sessao nao aceita quadro): pulado
 * no Xiaomi sem o toque (`permissaoManual=true`).
 */
@RunWith(AndroidJUnit4::class)
class ColetaNaNotaInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val contexto = instrumentation.targetContext
    private val organizacao = "01a06ba4-cb43-7d97-842d-165352d010b5"
    private val shortId = "prova-discursiva"
    private val respostasDir = File(contexto.filesDir, "respostas")
    private val marcador = File(contexto.filesDir, ColetaDoCorpusEmArquivo.MARCADOR)
    private val corpus = ColetaDoCorpusEmArquivo.diretorioDe(contexto.filesDir)
    private val prova = FolhaDiscursivaRenderizada(instrumentation.context, instrumentation.targetContext)

    private var permissaoConcedida = false
    private val permissaoManual = InstrumentationRegistry.getArguments().getString("permissaoManual") == "true"

    private fun temPermissao() =
        ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private val bytes: ByteArray by lazy {
        instrumentation.context.assets.open("prova-discursiva.package.json").use { it.readBytes() }
    }
    private val hash: String by lazy {
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
    private val pacote: ExamPackage by lazy {
        Json { ignoreUnknownKeys = false }.decodeFromString(ExamPackage.serializer(), bytes.decodeToString())
    }

    @Before
    fun preparar() {
        assertTrue("o OpenCV nativo nao carregou", OpenCVLoader.initLocal())
        CadernosEmRoom.reiniciarParaTeste()
        ResultadosEmRoom.reiniciarParaTeste()
        contexto.deleteDatabase("caderno.db")
        contexto.deleteDatabase("outbox.db")
        respostasDir.deleteRecursively()
        corpus.deleteRecursively()
        marcador.delete()
        permissaoConcedida = try {
            instrumentation.uiAutomation.grantRuntimePermission(contexto.packageName, Manifest.permission.CAMERA)
            true
        } catch (e: SecurityException) {
            temPermissao()
        }
        PacotesEmArquivo(File(contexto.filesDir, "packages")).guardar(organizacao, hash, bytes)
    }

    @After
    fun limpar() {
        CadernosEmRoom.reiniciarParaTeste()
        ResultadosEmRoom.reiniciarParaTeste()
        contexto.deleteDatabase("caderno.db")
        contexto.deleteDatabase("outbox.db")
        respostasDir.deleteRecursively()
        corpus.deleteRecursively()
        marcador.delete()
        PacotesEmArquivo(File(contexto.filesDir, "packages")).apagarDaOrganizacao(organizacao)
    }

    private fun abrir(): ScanActivity {
        val intent = Intent(contexto, ScanActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(ScanActivity.EXTRA_ORGANIZACAO, organizacao)
            .putExtra(ScanActivity.EXTRA_CONTENT_HASH, hash)
            .putExtra(ScanActivity.EXTRA_SHORT_ID, shortId)
        return instrumentation.startActivitySync(intent) as ScanActivity
    }

    private fun <T> comAtividade(bloco: (ScanActivity) -> T): T {
        val atividade = abrir()
        try {
            return bloco(atividade)
        } finally {
            instrumentation.runOnMainSync { atividade.finish() }
            val limite = System.currentTimeMillis() + 5_000
            while (!atividade.isDestroyed && System.currentTimeMillis() < limite) Thread.sleep(100)
            Thread.sleep(1_000)
        }
    }

    private fun esperar(ateMs: Long = 10_000, condicao: () -> Boolean): Boolean {
        val limite = System.currentTimeMillis() + ateMs
        while (System.currentTimeMillis() < limite && !condicao()) Thread.sleep(100)
        return condicao()
    }

    private fun aguardarPermissaoManual() {
        if (temPermissao()) return
        Log.w("ColetaNaNotaTeste", ">>> TOQUE EM 'PERMITIR' NO DIALOGO DE CAMERA DO APARELHO <<<")
        val limite = System.currentTimeMillis() + 120_000
        while (!temPermissao() && System.currentTimeMillis() < limite) Thread.sleep(500)
        assertTrue("o toque em Permitir nao veio em 2 minutos", temPermissao())
    }

    /** Entrega as paginas da folha ate o caderno aguardar a nota; falha dizendo ate onde chegou. */
    private fun completarOCaderno(atividade: ScanActivity) {
        for (indice in 0..3) {
            if (atividade.instantaneoDoCaderno?.aguardaNota == true) break
            val quadro = try {
                atividade.analisadorDaCamera().analisar(prova.pagina(indice))
            } catch (e: IndexOutOfBoundsException) {
                break
            }
            atividade.entregarQuadro(quadro)
            esperar(3_000) { atividade.instantaneoDoCaderno?.aguardaNota == true }
        }
        assertTrue(
            "o caderno nao chegou a aguardar a nota: ${atividade.instantaneoDoCaderno}",
            esperar { atividade.instantaneoDoCaderno?.aguardaNota == true },
        )
    }

    private fun exigirPermissao() = Assume.assumeTrue(
        "permissao de camera nao concedida ao teste; para tocar em Permitir a mao: " +
            "-Pandroid.testInstrumentationRunnerArguments.permissaoManual=true",
        permissaoConcedida || permissaoManual,
    )

    private val notas = listOf(PontuacaoDada("d1", Pontos.parse("1.5")), PontuacaoDada("d2", Pontos.parse("0")))

    @Test
    fun com_a_coleta_ligada_dar_a_nota_deixa_uma_amostra_por_discursiva() {
        exigirPermissao()
        marcador.writeText("")
        comAtividade { atividade ->
            aguardarPermissaoManual()
            completarOCaderno(atividade)
            val fotosDeOrigem = respostasDir.listFiles()!!.map { it.readBytes().toList() }.toSet()
            assertEquals("a origem tem uma foto por discursiva", 2, fotosDeOrigem.size)

            atividade.darNota(notas)

            assertTrue(
                "a nota nao deixou duas amostras no corpus: ${corpus.list()?.toList()}",
                esperar { corpus.listFiles { f -> f.extension == "json" }?.size == 2 },
            )
            val dados = corpus.listFiles { f -> f.extension == "json" }!!.map {
                AmostraDoCorpus.json.decodeFromString(AmostraDoCorpus.serializer(), it.readText())
            }
            assertEquals(setOf("1.50", "0.00"), dados.map { it.pontos }.toSet())
            assertEquals(setOf(3, 4), dados.map { it.maximo }.toSet())
            assertEquals(setOf("d1", "d2"), dados.map { it.item }.toSet())
            assertEquals(setOf(pacote.contentHash()), dados.map { it.pacote }.toSet())
            val fotosDaCopia = corpus.listFiles { f -> f.extension == "png" }!!.map { it.readBytes().toList() }.toSet()
            assertEquals("os bytes da foto nao sao os de respostas/", fotosDeOrigem, fotosDaCopia)
            for (arquivo in corpus.listFiles()!!) {
                assertFalse("o token da folha esta em ${arquivo.name}", FolhaDiscursivaRenderizada.TOKEN in arquivo.name)
                if (arquivo.extension == "json") assertFalse(FolhaDiscursivaRenderizada.TOKEN in arquivo.readText())
            }
        }
    }

    @Test
    fun com_a_coleta_desligada_dar_a_nota_nao_deixa_nada() {
        exigirPermissao()
        comAtividade { atividade ->
            aguardarPermissaoManual()
            completarOCaderno(atividade)

            atividade.darNota(notas)

            val gravou = esperar {
                ResultadosEmRoom(ResultadosEmRoom.abrir(contexto).pendentes()).quantosPendentes(organizacao) >= 1
            }
            assertTrue("a nota nao foi gravada: o teste nao diria nada sobre o corpus", gravou)
            assertFalse("o corpus nasceu com a coleta desligada", corpus.exists() && corpus.list().orEmpty().isNotEmpty())
        }
    }
}
```

Run (precisa do emulador; **P22: peça ao Leon antes**): `./gradlew :apps:android:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.platos.android.corpus.ColetaNaNotaInstrumentedTest` e depois a mesma para `VarreduraEliminaOCorpusInstrumentedTest`.
Expected: PASS nos dois (o primeiro pode pular no Xiaomi sem toque). Se `completarOCaderno` falhar dizendo "não chegou a aguardar a nota", leia o caderno impresso: provavelmente a folha tem outro número de páginas, e o laço `0..3` é o ajuste; **não** enfraqueça as asserções.

- [ ] **Step 13: Commit**

```bash
git add apps/android/src/main apps/android/src/test apps/android/src/androidTest
git commit -m "feat(corpus): a nota copia as discursivas antes de gravar, e sair, revogar e a varredura eliminam

ScanActivity.darNota passa por gravarNotaComColeta (copia antes, aviso na falha);
DeviceSession.sair e revogar eliminam o corpus sem que a falha o impeca; a
varredura periodica e a da abertura eliminam as amostras vencidas. Um teste de
JVM reprova qualquer codigo de rede que cite o corpus.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 4: O APK de release não contém a coleta

**Files:**
- Modify: `apps/android/build.gradle.kts` (nova task e o `check`)

**Interfaces:**
- Consumes: os APKs de `build/outputs/apk/debug` e `release`; a classe `com/platos/android/corpus/ColetaDoCorpusEmArquivo` e o literal `coleta-ligada`.
- Produces: a task `:apps:android:verificarApkSemColeta`, dependência de `check`.

- [ ] **Step 1: Escrever a task**

Em `apps/android/build.gradle.kts`, **depois** de `tasks.named("check") { dependsOn(verificarApkSemPacote) }`:

```kotlin
/**
 * O APK de release **nao contem** a coleta do corpus (`slice-5d-corpus-de-medicao`, spec `measurement-corpus`: "A coleta
 * existe so no aplicativo de depuracao"). Como `VerificarApkSemPacoteTask`, confere o **artefato**: a garantia e de
 * compilacao, e um `grep` no codigo nao diz que o `src/release` nao a trouxe de volta.
 *
 * **Duas assinaturas, e a segunda e a que importa.** O descritor da classe (`.../ColetaDoCorpusEmArquivo`) some se um
 * dia houver R8; o literal do marcador (`coleta-ligada`) nao: o R8 nao renomeia string, e so o debug a contem. Procuradas
 * nos `.dex`, como texto.
 *
 * **Guarda de vacuidade por variante (P13):** o APK de **debug** precisa conter o literal, senao a busca nao distingue
 * nada e o release "passaria" por nao achar o que nunca acharia. O release sem `.dex` tambem reprova.
 */
abstract class VerificarApkSemColetaTask : DefaultTask() {
    @get:InputFiles
    abstract val apksDeDebug: ConfigurableFileCollection

    @get:InputFiles
    abstract val apksDeRelease: ConfigurableFileCollection

    private val assinaturas = listOf("com/platos/android/corpus/ColetaDoCorpusEmArquivo", "coleta-ligada")

    @TaskAction
    fun run() {
        for ((variante, colecao) in listOf("debug" to apksDeDebug, "release" to apksDeRelease)) {
            val apks = colecao.files.filter { it.isFile && it.extension == "apk" }
            require(apks.isNotEmpty()) {
                "nenhum APK de $variante para conferir; a tarefa depende de `assemble${variante.replaceFirstChar { it.uppercase() }}`"
            }
            for (apk in apks) {
                val achadas = assinaturasNosDex(apk)
                if (variante == "debug") {
                    require("coleta-ligada" in achadas) {
                        "${apk.name} (debug) nao traz o literal da coleta: a verificacao nao distingue o release do debug"
                    }
                } else {
                    require(achadas.isEmpty()) {
                        "${apk.name} (release) contem a coleta do corpus: $achadas. " +
                            "A coleta so pode existir em src/debug (slice-5d-corpus-de-medicao)."
                    }
                }
                logger.lifecycle("$variante: ${apk.name}, assinaturas da coleta: ${achadas.ifEmpty { "nenhuma" }}.")
            }
        }
        logger.lifecycle("A coleta do corpus esta so no APK de debug.")
    }

    private fun assinaturasNosDex(apk: java.io.File): Set<String> {
        val achadas = mutableSetOf<String>()
        var dex = 0
        ZipFile(apk).use { zip ->
            for (entrada in zip.entries()) {
                if (!entrada.name.endsWith(".dex")) continue
                dex++
                val texto = String(zip.getInputStream(entrada).use { it.readBytes() }, Charsets.ISO_8859_1)
                for (assinatura in assinaturas) if (assinatura in texto) achadas += assinatura
            }
        }
        require(dex > 0) { "${apk.name}: nenhum .dex no APK; a busca passaria por vacuidade" }
        return achadas
    }
}

val verificarApkSemColeta = tasks.register<VerificarApkSemColetaTask>("verificarApkSemColeta") {
    group = "verification"
    description = "Confere que o codigo da coleta do corpus esta so no APK de debug"
    dependsOn("assembleDebug", "assembleRelease")
    apksDeDebug.from(layout.buildDirectory.dir("outputs/apk/debug").map { it.asFileTree })
    apksDeRelease.from(layout.buildDirectory.dir("outputs/apk/release").map { it.asFileTree })
}

tasks.named("check") { dependsOn(verificarApkSemColeta) }
```

- [ ] **Step 2: Rodar e ver passar**

Run: `./gradlew :apps:android:verificarApkSemColeta`
Expected: PASS, com as duas linhas `debug: ... assinaturas da coleta: [coleta-ligada, com/platos/...]` e `release: ... nenhuma`. **Leia as linhas**: se o debug disser "nenhuma", a task teria reprovado pela vacuidade; se disser só o literal, o descritor da classe não está no dex (aceitável, o literal basta).

- [ ] **Step 3: Ver falhar (mutação isolada) e reverter**

1. Pôr a implementação real no release: copiar `ColetaDoCorpusEmArquivo.kt` e `AmostraDoCorpus.kt` de `src/debug/kotlin/com/platos/android/corpus/` para `src/release/kotlin/com/platos/android/corpus/` (as duas variantes nunca compilam juntas, então não há redeclaração) e trocar o corpo da fábrica do release para `= ColetaDoCorpusEmArquivo(filesDir)`. Run: `./gradlew :apps:android:verificarApkSemColeta`. Esperado: **FAIL** com `... (release) contem a coleta do corpus: [...]`. **Leia a mensagem**: ela tem de nomear a coleta, e não um erro de compilação.
2. Reverter: `git checkout -- apps/android/src/release/kotlin/com/platos/android/corpus/FabricaDaColeta.kt` e apagar as duas cópias, que são arquivos não rastreados (P24: **olhe antes** com `git status --short apps/android/src/release` e apague só os dois nomes, com `rm`). Rode de novo `./gradlew :apps:android:verificarApkSemColeta`: PASS (P10: a reversão se confere rodando).
3. Outra mutação, a da vacuidade: renomear o literal `MARCADOR = "coleta-ligada"` para `"liga-coleta"` (no `src/debug`). Esperado: FAIL com `(debug) nao traz o literal da coleta`. Reverter e rodar de novo.

- [ ] **Step 4: Commit**

```bash
git add apps/android/build.gradle.kts
git commit -m "build(android): o APK de release e conferido contra a coleta do corpus

verificarApkSemColeta procura a classe e o literal do marcador nos .dex dos dois
APKs, com guarda de vacuidade por variante (o debug precisa contê-los), e entra
no check.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Puxar por cabo e conferir (`tools/corpus`)

**Files:**
- Create: `tools/corpus/formato.mjs`
- Create: `tools/corpus/puxar.mjs`
- Test: `tools/corpus/corpus.test.mjs`
- Modify: `.gitignore`
- Modify: `.github/workflows/ci.yml` (um passo, junto da guarda do fio)

**Interfaces:**
- Consumes: `fixtures/corpus/amostra-exemplo.json` (o literal único).
- Produces: `chavesDoFormato(arquivo?) -> string[]`; `conferirPasta(dir, chaves?) -> { amostras: number, problemas: string[] }`; `contarValidas(dir) -> { amostras, validas, descartadas, semReferencia }` (a grandeza do ADR-0022: válida = `referencia` preenchida e `descartar` falso); `destinoForaDoRepositorio(destino, raiz?) -> boolean`; o CLI `node tools/corpus/puxar.mjs <destino>` (exit `0` íntegro, `1` conferência reprovou, `2` não conseguiu) e o CLI `node tools/corpus/formato.mjs <pasta>` (confere e conta; exit `0` íntegro, `1` com problemas).

- [ ] **Step 1: Escrever o teste que falha**

Criar `tools/corpus/corpus.test.mjs`:

```js
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, writeFileSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { EXEMPLO, chavesDoFormato, conferirPasta, contarValidas } from './formato.mjs';
import { destinoForaDoRepositorio } from './puxar.mjs';

const RAIZ = fileURLToPath(new URL('../../', import.meta.url));
const ID_A = '3f2c9a52-1111-4000-8000-0000000000d1';
const ID_B = '3f2c9a52-2222-4000-8000-0000000000d2';

function pasta() {
  return mkdtempSync(join(tmpdir(), 'corpus-'));
}

function amostra(dir, id, { foto = Buffer.from([1, 2, 3]), dados = readFileSync(EXEMPLO, 'utf8') } = {}) {
  if (foto !== null) writeFileSync(join(dir, `${id}.png`), foto);
  if (dados !== null) writeFileSync(join(dir, `${id}.json`), dados);
}

function com(fn) {
  const dir = pasta();
  try {
    return fn(dir);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

test('as chaves do formato saem do literal do repositorio, na ordem em que o aparelho as escreve', () => {
  assert.deepEqual(chavesDoFormato(), ['versao', 'pontos', 'maximo', 'pacote', 'item', 'referencia', 'descartar']);
});

test('uma pasta integra e aprovada, e conta as amostras', () =>
  com((dir) => {
    amostra(dir, ID_A);
    amostra(dir, ID_B);
    assert.deepEqual(conferirPasta(dir), { amostras: 2, problemas: [] });
  }));

test('foto sem arquivo de dados reprova nomeando a amostra', () =>
  com((dir) => {
    amostra(dir, ID_A, { dados: null });
    const { problemas } = conferirPasta(dir);
    assert.deepEqual(problemas, [`${ID_A}: falta o arquivo de dados`]);
  }));

test('arquivo de dados sem foto reprova nomeando a amostra', () =>
  com((dir) => {
    amostra(dir, ID_A, { foto: null });
    assert.deepEqual(conferirPasta(dir).problemas, [`${ID_A}: falta a foto`]);
  }));

test('chave a mais ou a menos reprova, e a mensagem diz o que difere', () =>
  com((dir) => {
    amostra(dir, ID_A, { dados: '{"versao":1,"pontos":"1.50"}' });
    const { problemas } = conferirPasta(dir);
    assert.equal(problemas.length, 1);
    assert.match(problemas[0], /chaves .* diferem do formato/);
  }));

test('a ordem das chaves nao importa (o mantenedor edita o arquivo)', () =>
  com((dir) => {
    const reordenado = JSON.stringify(Object.fromEntries(Object.entries(JSON.parse(readFileSync(EXEMPLO, 'utf8'))).reverse()));
    amostra(dir, ID_A, { dados: reordenado });
    assert.deepEqual(conferirPasta(dir).problemas, []);
  }));

test('foto vazia reprova', () =>
  com((dir) => {
    amostra(dir, ID_A, { foto: Buffer.alloc(0) });
    assert.deepEqual(conferirPasta(dir).problemas, [`${ID_A}: a foto esta vazia`]);
  }));

test('pasta sem nenhuma amostra reprova (piso)', () =>
  com((dir) => {
    assert.deepEqual(conferirPasta(dir).problemas, ['a pasta nao tem nenhuma amostra']);
  }));

test('pasta que nao existe reprova em vez de passar', () => {
  const { problemas } = conferirPasta(join(tmpdir(), 'nao-existe-corpus-xyz'));
  assert.equal(problemas.length, 1);
  assert.match(problemas[0], /nao consegui ler/);
});

test('arquivo estranho e nome fora do formato reprovam', () =>
  com((dir) => {
    amostra(dir, ID_A);
    writeFileSync(join(dir, 'anotacoes.txt'), 'x');
    writeFileSync(join(dir, 'curto.png'), 'x');
    const { problemas } = conferirPasta(dir);
    assert.ok(problemas.includes('arquivo estranho: anotacoes.txt'), problemas.join('\n'));
    assert.ok(problemas.some((p) => p.includes('curto.png')), problemas.join('\n'));
  }));

test('so e valida a amostra com referencia preenchida e sem descarte (ADR-0022, item 1)', () =>
  com((dir) => {
    const exemplo = JSON.parse(readFileSync(EXEMPLO, 'utf8'));
    const com_ = (extra) => JSON.stringify({ ...exemplo, ...extra });
    amostra(dir, '3f2c9a52-1111-4000-8000-0000000000a1', { dados: com_({ referencia: 'o texto certo' }) });
    amostra(dir, '3f2c9a52-1111-4000-8000-0000000000a2', { dados: com_({ referencia: 'outro texto' }) });
    amostra(dir, '3f2c9a52-1111-4000-8000-0000000000a3', { dados: com_({ referencia: 'tinha nome', descartar: true }) });
    amostra(dir, '3f2c9a52-1111-4000-8000-0000000000a4', { dados: com_({ referencia: '' }) });
    amostra(dir, '3f2c9a52-1111-4000-8000-0000000000a5');
    assert.deepEqual(contarValidas(dir), { amostras: 5, validas: 2, descartadas: 1, semReferencia: 2 });
  }));

test('um destino dentro do repositorio e recusado, o de fora e aceito', () => {
  assert.equal(destinoForaDoRepositorio(join(RAIZ, 'corpus-de-medicao')), false);
  assert.equal(destinoForaDoRepositorio(RAIZ), false);
  assert.equal(destinoForaDoRepositorio(join(RAIZ, '..', 'corpus-de-medicao-fora')), true);
});
```

- [ ] **Step 2: Ver falhar**

Run: `node --test tools/corpus/`
Expected: FAIL — `Cannot find module './formato.mjs'`.

- [ ] **Step 3: Implementar**

Criar `tools/corpus/formato.mjs`:

```js
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * A conferencia de uma pasta de amostras do corpus de medicao (`slice-5d-corpus-de-medicao`).
 *
 * **O formato nao e digitado aqui.** As chaves saem de `fixtures/corpus/amostra-exemplo.json`, o literal que o teste da
 * coleta Kotlin compara com a serializacao (`ColetaDoCorpusEmArquivoTest`): os dois lados prendem o mesmo arquivo, e
 * mudar um campo num sem mudar o literal reprova (P28). A ordem das chaves nao conta; o conjunto, sim.
 */

const RAIZ = fileURLToPath(new URL('../../', import.meta.url));
export const EXEMPLO = join(RAIZ, 'fixtures', 'corpus', 'amostra-exemplo.json');

const NOME = /^(.+)\.(png|json)$/;
const ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

export function chavesDoFormato(arquivo = EXEMPLO) {
  return Object.keys(JSON.parse(readFileSync(arquivo, 'utf8')));
}

const igualAoFormato = (a, b) => JSON.stringify([...a].sort()) === JSON.stringify([...b].sort());

/**
 * Cada foto tem o seu arquivo de dados e vice-versa, com as chaves do formato, e a pasta tem ao menos uma amostra
 * (piso, P13: uma pasta vazia passaria em qualquer conferencia). Devolve os problemas, e nao lanca.
 */
export function conferirPasta(dir, chaves = chavesDoFormato()) {
  let nomes;
  try {
    nomes = readdirSync(dir);
  } catch {
    return { amostras: 0, problemas: [`nao consegui ler ${dir}`] };
  }
  const problemas = [];
  const porId = new Map();
  for (const nome of nomes) {
    const m = NOME.exec(nome);
    if (!m) {
      problemas.push(`arquivo estranho: ${nome}`);
      continue;
    }
    const [, id, extensao] = m;
    if (!ID.test(id)) problemas.push(`nome fora do formato de identificador: ${nome}`);
    porId.set(id, { ...(porId.get(id) ?? {}), [extensao]: true });
  }
  if (porId.size === 0) problemas.push('a pasta nao tem nenhuma amostra');
  for (const [id, partes] of [...porId].sort()) {
    if (!partes.png) problemas.push(`${id}: falta a foto`);
    else if (statSync(join(dir, `${id}.png`)).size === 0) problemas.push(`${id}: a foto esta vazia`);
    if (!partes.json) {
      problemas.push(`${id}: falta o arquivo de dados`);
      continue;
    }
    let dados;
    try {
      dados = JSON.parse(readFileSync(join(dir, `${id}.json`), 'utf8'));
    } catch {
      problemas.push(`${id}: o arquivo de dados nao e JSON`);
      continue;
    }
    const doArquivo = Object.keys(dados);
    if (!igualAoFormato(doArquivo, chaves)) {
      problemas.push(`${id}: chaves ${doArquivo.join(',')} diferem do formato ${chaves.join(',')}`);
    }
  }
  return { amostras: porId.size, problemas };
}

/**
 * A grandeza do ADR-0022 (item 1): amostra **valida** e a de `referencia` preenchida (nao vazia) e `descartar` falso.
 * `descartadas` e `semReferencia` sao disjuntas: a descartada nao conta como "sem referencia" (nao falta transcrever
 * o que nao entra). Arquivo de dados ilegivel conta como sem referencia; `conferirPasta` e quem o acusa.
 */
export function contarValidas(dir) {
  let validas = 0;
  let descartadas = 0;
  let semReferencia = 0;
  let amostras = 0;
  for (const nome of readdirSync(dir)) {
    const m = /^(.+)\.json$/.exec(nome);
    if (!m) continue;
    amostras++;
    let dados = {};
    try {
      dados = JSON.parse(readFileSync(join(dir, nome), 'utf8'));
    } catch {
      /* conta como sem referencia */
    }
    if (dados.descartar === true) descartadas++;
    else if (typeof dados.referencia === 'string' && dados.referencia.trim() !== '') validas++;
    else semReferencia++;
  }
  return { amostras, validas, descartadas, semReferencia };
}

// `node tools/corpus/formato.mjs <pasta>`: confere o formato e conta as validas (o piso do ADR-0022 e 60).
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const pasta = process.argv[2];
  if (!pasta) {
    console.error('uso: node tools/corpus/formato.mjs <pasta com as amostras>');
    process.exit(2);
  }
  const { problemas } = conferirPasta(pasta);
  for (const p of problemas) console.error(`::error::${p}`);
  console.log(JSON.stringify(contarValidas(pasta)));
  process.exit(problemas.length > 0 ? 1 : 0);
}
```

Criar `tools/corpus/puxar.mjs`:

```js
#!/usr/bin/env node
import { spawnSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { isAbsolute, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { conferirPasta } from './formato.mjs';

/**
 * Puxa por cabo a pasta do corpus de medicao do aparelho (`slice-5d-corpus-de-medicao`).
 *
 * Uso: `node tools/corpus/puxar.mjs <destino>` (com `ANDROID_SERIAL` se houver mais de um aparelho).
 *
 * So funciona em APK **de depuracao** (`run-as` exige app depuravel): e a unica saida, e o release nao tem a coleta.
 * Le `files/corpus` por `ls` e cada arquivo por `cat` (`exec-out`, binario seguro), sem `tar`. O destino **nao pode
 * estar dentro do repositorio**: letra de menor commitada e o erro mais caro desta mudanca, e a recusa e a primeira
 * rede (o `.gitignore` e a segunda). Confere o que puxou contra o formato.
 *
 * Saida: `0` integro; `1` a conferencia reprovou; `2` nao consegui puxar ou destino recusado.
 */

const RAIZ = fileURLToPath(new URL('../../', import.meta.url));
const PACOTE = 'com.platos.android';

export function destinoForaDoRepositorio(destino, raiz = RAIZ) {
  const rel = relative(resolve(raiz), resolve(destino));
  return rel.startsWith('..') || isAbsolute(rel);
}

function adb(args) {
  const r = spawnSync('adb', args, { maxBuffer: 256 * 1024 * 1024 });
  if (r.error) throw new Error(`nao consegui rodar o adb: ${r.error.message}`);
  if (r.status !== 0) throw new Error(`adb ${args.join(' ')}: ${r.stderr.toString().trim() || `status ${r.status}`}`);
  return r.stdout;
}

function main() {
  const destino = process.argv[2];
  if (!destino) {
    console.error('uso: node tools/corpus/puxar.mjs <destino fora do repositorio>');
    return 2;
  }
  if (!destinoForaDoRepositorio(destino)) {
    console.error(`::error::${resolve(destino)} esta dentro do repositorio; escolha uma pasta de fora`);
    return 2;
  }
  let nomes;
  try {
    nomes = adb(['exec-out', 'run-as', PACOTE, 'ls', 'files/corpus'])
      .toString()
      .split(/\r?\n/)
      .map((n) => n.trim())
      .filter(Boolean);
  } catch (e) {
    console.error(`::error::${e.message}`);
    return 2;
  }
  if (nomes.length === 0) {
    console.error('::error::a pasta files/corpus do aparelho esta vazia ou nao existe');
    return 2;
  }
  mkdirSync(destino, { recursive: true });
  try {
    for (const nome of nomes) {
      writeFileSync(join(destino, nome), adb(['exec-out', 'run-as', PACOTE, 'cat', `files/corpus/${nome}`]));
    }
  } catch (e) {
    console.error(`::error::${e.message}`);
    return 2;
  }
  const { amostras, problemas } = conferirPasta(destino);
  console.log(`${nomes.length} arquivo(s) puxado(s) para ${resolve(destino)}; ${amostras} amostra(s).`);
  if (problemas.length > 0) {
    for (const p of problemas) console.error(`::error::${p}`);
    return 1;
  }
  console.log('Cada foto tem os seus dados, com as chaves do formato.');
  return 0;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) process.exit(main());
```

- [ ] **Step 4: Ver passar**

Run: `node --test tools/corpus/`
Expected: PASS, 12 testes (conte a saída; P2).

- [ ] **Step 5: Ver falhar**

1. Em `formato.mjs`, trocar `igualAoFormato(doArquivo, chaves)` por `true`. Esperado: vermelho em `chave a mais ou a menos reprova...` (só nele).
2. Apagar a linha `if (porId.size === 0) problemas.push(...)`. Esperado: vermelho em `pasta sem nenhuma amostra reprova (piso)`.
3. Em `destinoForaDoRepositorio`, trocar `rel.startsWith('..') || isAbsolute(rel)` por `true`. Esperado: vermelho em `um destino dentro do repositorio e recusado...`.
4. Em `contarValidas`, trocar `dados.referencia.trim() !== ''` por `true`. Esperado: vermelho em `so e valida a amostra com referencia preenchida...` (a de referência vazia deixa de ser "sem referência").
5. **Ligação com o Kotlin (P28):** editar `fixtures/corpus/amostra-exemplo.json` acrescentando uma chave `"extra":0`. Esperado: `./gradlew :apps:android:testDebugUnitTest` vermelho em `o formato bate com o literal do repositorio` (lado Kotlin) **e** `node --test tools/corpus/` vermelho em `as chaves do formato saem do literal...` (lado Node). Reverter o literal com `git checkout -- fixtures/corpus/amostra-exemplo.json` e rodar os dois de novo.

Reverter cada uma e rodar de novo `node --test tools/corpus/`.

- [ ] **Step 6: `.gitignore` e CI**

Acrescentar ao fim de `.gitignore`:

```
# Letra de aluno puxada do aparelho (slice-5d-corpus-de-medicao). A primeira rede e guarda-la FORA do repositorio
# (tools/corpus/puxar.mjs recusa destino dentro dele); esta e a segunda.
/corpus-de-medicao/
```

Conferir: `mkdir -p corpus-de-medicao && touch corpus-de-medicao/x.png && git check-ignore -v corpus-de-medicao/x.png` imprime a regra; depois `rm -r corpus-de-medicao`. Mutação: tirar a linha e ver `git status` listar a pasta (rodar `mkdir` de novo); reverter.

Em `.github/workflows/ci.yml`, **depois** do passo `A verificacao do fio continua capaz de falhar` (termina na linha com "…acusou o contrato sem literal nos dois lados, e o piso, como deve"), no mesmo job e com o mesmo recuo, acrescentar:

```yaml
      # O corpus de medicao (slice-5d-corpus-de-medicao): a conferencia do formato das amostras e a recusa de destino dentro do
      # repositorio. As chaves saem de fixtures/corpus/amostra-exemplo.json, que o teste Kotlin tambem prende.
      - name: A conferencia do corpus de medicao
        run: node --test tools/corpus/
```

Confira com `sed -n 240,285p .github/workflows/ci.yml` que o passo ficou no job que já tem `node` instalado (o mesmo da guarda do fio).

- [ ] **Step 7: Commit**

```bash
git add tools/corpus .gitignore .github/workflows/ci.yml
git commit -m "feat(corpus): puxar a pasta do aparelho por cabo e conferir o formato

tools/corpus/puxar.mjs copia files/corpus por adb run-as e recusa destino dentro
do repositorio; formato.mjs confere o par foto+dados contra as chaves do literal
unico em fixtures/corpus. O .gitignore e a segunda rede, e o CI roda os testes.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 6: O critério antes do resultado, o protocolo e o desenho atualizado

**Files:**
- Create: `docs/adr/0022-o-criterio-do-corpus-de-medicao.md`
- Create: `docs/protocolo-corpus-de-medicao.md`
- Modify: `docs/superpowers/specs/2026-10-02-slice-5d-corpus-de-medicao-design.md` (§1 D3 e §3.1: o interruptor é um marcador)
- Modify: `docs/protocolo-medicao-impressa.md` (uma linha de referência, na seção que descreve a sessão única)

- [ ] **Step 1: Escrever o ADR**

Criar `docs/adr/0022-o-criterio-do-corpus-de-medicao.md`:

```markdown
# ADR-0022 — O critério do corpus de medição, fixado antes da primeira amostra

**Status:** aceito · **Data:** 2026-10-02 · **Fatia-limite:** 5d (`slice-5d-corpus-de-medicao`)
**Referências:** `ARQUITETURA-FINAL-v3.md` §9.2, §9.3, §15, §16 · ADR-0007 · ADR-0012 · `docs/legal/politica-de-privacidade.md` v2.0 §6.3 e §7 · `docs/superpowers/specs/2026-10-02-slice-5d-corpus-de-medicao-design.md` · `docs/protocolo-corpus-de-medicao.md`

## Contexto

O mantenedor quer saber se um OCR embarcado no aplicativo lê a letra do aluno bem o bastante para que o **texto
transcrito**, e não a imagem, vá à IA na correção discursiva. O §9.2 diz que o corte de fiabilidade "tem que sair de
medição, nunca de intuição", e o §9.3 que a decisão sobre o TexTeller espera "evidência do corpus real". Isso exige
três coisas, em três mudanças: **os dados** (esta), **uma bancada** que roda motores candidatos sobre eles e escolhe o
motor (ADR próprio), e **o motor no aplicativo** com o indicador de fiabilidade.

ADR-0007 exige que toda medição feita para decidir tenha o critério registrado **antes** da primeira execução, com a
grandeza, o que aprova, o que reprova e o que acontece se reprovar. O que esta mudança "mede" é se o corpus **basta**
para a bancada decidir; ela não mede leitura.

## Decisão

1. **Grandeza: amostra válida.** Uma amostra do corpus (foto, pontuação do professor, máximo, item) é **válida** quando
   o campo `referencia` está preenchido e `descartar` é `false`. `referencia` é o texto correto da resposta, digitado
   pelo mantenedor com a foto aberta.
2. **O corpus basta quando** tem **60 ou mais amostras válidas**, de **uma ou duas turmas** do mantenedor, coletadas
   pelo protocolo. 60 é o piso que o mantenedor declarou conseguir; 150 é a meta.
3. **Se não basta:** a bancada pode rodar, mas o resultado **orienta a próxima rodada de coleta e não decide** o motor
   nem o corte. A coleta continua. Nenhum número é reinterpretado depois de visto (P11).
4. **O que o corpus permite decidir:** o motor de OCR candidato (na bancada, ADR próprio) e, depois, o corte de
   fiabilidade (na mudança que integra o motor).
5. **O que o corpus não decide, e o limite que fica escrito:** nada sobre outras turmas, outros alunos, outras
   matérias nem outro instrumento de escrita (lápis, caneta) além do coletado. **Uma ou duas turmas não separam exatas
   de humanas**; qualquer conclusão vale para esse recorte. A cobertura da mudança registra quantas turmas, quais
   matérias e qual instrumento.
6. **Nenhum limiar de leitura é fixado aqui.** Nem taxa de erro de caractere ou de palavra aceitável, nem corte de
   fiabilidade. A bancada fixa o seu, por ADR, **antes** de rodar sobre o corpus (ADR-0007; P11).
7. **A divergência de nota ("a nota não muda", §9.2) não é medida por esta mudança nem pela bancada de leitura:** exige
   corrigir pelos dois caminhos com um LLM, que só existe com o `AiGateway` (fatia 6) e a correção por IA (fatia 8). A
   bancada mede **erro de leitura contra a referência**; a divergência de nota é medida depois, sobre o mesmo corpus.

### Convenções de transcrição (a referência)

- Transcrever **literalmente**, inclusive erros de ortografia, de acentuação e de pontuação do aluno. O motor lê o
  que está escrito; corrigir o aluno mede outra coisa.
- Trecho ilegível: `[ilegivel]`, **nunca** uma adivinhação. Palavra riscada pelo aluno: omitir.
- Quebra de linha da folha: espaço. Parágrafo novo do aluno: linha em branco.
- Fórmula ou símbolo matemático que o teclado não escreve: descrever entre colchetes (`[fracao 3/4]`). A medição de
  fórmula é do TexTeller, que continua **fora de v1** sem evidência (CLAUDE.md).
- Nome ou dado pessoal escrito no corpo da resposta: marcar `descartar: true` e **não** transcrever.

## Consequências

- A coleta existe só no APK de depuração e é do mantenedor; o professor pagante não a vê.
- As fotos ficam fora do repositório. Letra de menor sai do aparelho **por cabo**, pelo mantenedor, em APK de depuração
  (§16, linha "LGPD com dados de menores": reescrita no archive; segue aberta ao jurídico externo).
- A medição de leitura e a escolha do motor ganham veículo próprio (a bancada), com critério próprio escrito antes.
- Reprovar o piso de 60 não invalida nada: adia a decisão e diz o que falta.
```

- [ ] **Step 2: Escrever o protocolo**

Criar `docs/protocolo-corpus-de-medicao.md`:

```markdown
# Protocolo — coleta do corpus de medição

**Critério:** `docs/adr/0022-o-criterio-do-corpus-de-medicao.md` (lido **antes** de começar). **Quem coleta:** só o
mantenedor, nas turmas dele, com o APK de **depuração**. O professor pagante não vê isto, e o APK de release não contém
o código (`./gradlew :apps:android:verificarApkSemColeta`).

## 1. Preparar o aparelho

1. `./gradlew :apps:android:installDebug` (não apaga os dados do aplicativo).
2. Ligar a coleta: `adb shell run-as com.platos.android touch files/coleta-ligada`.
   Desligar: `adb shell run-as com.platos.android rm files/coleta-ligada`. Desligada (o padrão), nada é copiado.

## 2. Corrigir a turma, normalmente

Escaneie as folhas e **dê a nota de cada caderno** na tela de nota. A cada nota confirmada, o aplicativo copia **todas
as discursivas** da folha para `files/corpus/` (foto + um arquivo de dados), **antes** de gravar a nota. Se a cópia
falhar, a nota é gravada do mesmo jeito e aparece um aviso nomeando a questão: anote qual.

## 3. Puxar antes de sair da sessão

**Sair da sessão e a revogação do vínculo apagam `files/corpus/`** (a amostra é cópia), e o prazo apaga o que passa de
30 dias. Puxe antes:

    node tools/corpus/puxar.mjs C:\caminho\fora\do\repositorio\corpus-de-medicao

O script recusa destino dentro do repositório, puxa por `adb`/`run-as`, e confere que cada foto tem o seu arquivo de
dados e vice-versa. `exit 0` é íntegro; `1`, a conferência reprovou (leia as linhas `::error::`); `2`, não puxou.
Com mais de um aparelho: `ANDROID_SERIAL=<serial>`.

## 4. A referência, no computador

Para cada `<id>.png`, com a foto aberta, abra `<id>.json` e preencha:

- `"referencia"`: o texto correto da resposta, pelas **convenções do ADR-0022** (literal, `[ilegivel]`, sem corrigir o
  aluno).
- `"descartar": true` se houver **nome ou dado pessoal escrito** na resposta (e não transcreva).

Depois de editar, confira o formato e conte as válidas (o piso do ADR-0022 é 60):

    node tools/corpus/formato.mjs C:\caminho\fora\do\repositorio\corpus-de-medicao

Imprime `{"amostras":N,"validas":V,"descartadas":D,"semReferencia":S}`; `exit 1` se algum arquivo de dados perdeu uma
chave ou uma foto ficou sem o par.

## 5. Guardar

- **Fora do repositório.** `.gitignore` tem `/corpus-de-medicao/` como segunda rede, e não é a primeira.
- **Não enviar a terceiros nesta etapa.** Nenhuma foto vai a LLM, OCR em nuvem ou serviço de transcrição antes da bancada
  e do ADR dela (política v2.0 §6.3: o envio a provedor exige o contrato que proíbe treino com os dados).
- Registrar, para a cobertura: **quantas turmas, quais matérias, qual instrumento** (lápis ou caneta), **quantas
  amostras válidas** (com referência, sem descarte) e quantas falhas de cópia.

## 6. Quando parar

O corpus basta com **60 ou mais amostras válidas** (ADR-0022, item 2), lidas em `"validas"` da saída de
`node tools/corpus/formato.mjs`; a meta é 150. Abaixo disso a bancada orienta e não decide.

## 7. A sessão única de papel

A coleta de alunos **não depende da impressora** e pode ocorrer à parte. Este protocolo entra na sessão única de papel
(`docs/protocolo-medicao-impressa.md`) só como referência.
```

- [ ] **Step 3: Atualizar o desenho e a referência**

Em `docs/superpowers/specs/2026-10-02-slice-5d-corpus-de-medicao-design.md`:

- Na tabela do §1, a linha `| D3 |` passa a: `| D3 | **Interruptor de coleta, desligado por padrão, e ele é um arquivo marcador** (`filesDir/coleta-ligada`), ligado por `adb`, **sem tela**. Ligado, toda nota confirmada copia todas as discursivas da folha. | Para 60–150 respostas, uma ação por turma. Para uso só do mantenedor, que já usa `adb`, uma tela é Compose e teste para nada. A spec diz "interruptor" sem fixar a forma. (Atualizado em 2026-10-02, no plano; o desenho aprovado dizia "a tela do interruptor".) |`
- No §3.1, a frase `` `src/debug`: `ColetaDoCorpusEmArquivo` (a implementação real) e a tela do interruptor.`` passa a ``` `src/debug`: `ColetaDoCorpusEmArquivo` (a implementação real), `AmostraDoCorpus` (o formato) e a fábrica `coletaDoCorpus`. **`src/release`:** a mesma fábrica, que devolve `SemColeta`. ```

Em `docs/protocolo-medicao-impressa.md`, na seção que trata da sessão única de papel (ache com `grep -n "sessão única\|sessao unica" docs/protocolo-medicao-impressa.md`), acrescentar uma linha: ``A coleta do corpus de alunos (`docs/protocolo-corpus-de-medicao.md`) não depende da impressora e não entra nesta sessão; é só referência.``

- [ ] **Step 4: Conferir**

Run: `grep -n "0022" docs/adr/0022-o-criterio-do-corpus-de-medicao.md | head -2` (o título) e `grep -c "ilegivel" docs/adr/0022-o-criterio-do-corpus-de-medicao.md docs/protocolo-corpus-de-medicao.md` (convenção nos dois). Procure número de limiar de leitura no ADR: `grep -nE "[0-9]+ ?%|CER|WER|taxa de erro" docs/adr/0022-o-criterio-do-corpus-de-medicao.md` deve listar só a menção de que **nenhum** é fixado (item 6).

- [ ] **Step 5: Commit**

```bash
git add docs/adr/0022-o-criterio-do-corpus-de-medicao.md docs/protocolo-corpus-de-medicao.md docs/protocolo-medicao-impressa.md docs/superpowers/specs/2026-10-02-slice-5d-corpus-de-medicao-design.md
git commit -m "docs(corpus): o criterio do corpus antes da primeira amostra e o protocolo da coleta

ADR-0022 fixa a grandeza (amostra valida), o piso de 60, o que o corpus decide e
nao decide e as convencoes de transcricao, sem nenhum limiar de leitura (P11).
O protocolo e o passo a passo; o desenho registra que o interruptor e um arquivo
marcador, e nao uma tela.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Verificação cheia, saída por cabo no emulador e a cobertura

**Files:**
- Create: `docs/cobertura-slice-5d-corpus-de-medicao.md`

**Interfaces:** nenhuma; fecha a mudança com evidência.

- [ ] **Step 1: Pedir autorização ao Leon (P22)**

Antes de qualquer coisa: "Vou precisar do Docker (agregado `./gradlew build --rerun-tasks`) e do emulador `platos-atd34` (instrumentados e a saída por cabo). Docker + emulador + Gradle estouram 16 GB: faço em sequência, com `adb emu kill` e `./gradlew --stop` entre os blocos. Posso subir? O Docker Desktop é você quem inicia." **Espere a resposta.** Não suba nada sem ela.

- [ ] **Step 2: O agregado, com o Docker de pé**

Ordem que coube (memória `suite-cheia-precisa-de-docker-e-memoria`): `adb emu kill` e `./gradlew --stop`; com o Docker iniciado pelo Leon, `./gradlew build --rerun-tasks` (3 min).
Expected: `BUILD SUCCESSFUL`; **conte as tasks executadas** (`N actionable tasks: N executed`, P2) e confira os `timestamp` dos XML de `testDebugUnitTest` e `testReleaseUnitTest` (P3). `verificarApkSemColeta` roda dentro do `check`: anote a linha de log dela. `node --test tools/corpus/` e `node tools/divida/divida.mjs` (exit 0). `openspec validate slice-5d-corpus-de-medicao --strict`.

- [ ] **Step 3: Os instrumentados, sem filtro**

`./gradlew --stop`; subir o emulador sem janela (`C:\Users\Leon\scoop\apps\android-clt\current\emulator\emulator.exe -avd platos-atd34 -no-window -no-audio -no-boot-anim -no-snapshot-save -gpu swiftshader_indirect`, em segundo plano, esperando `adb -e shell getprop sys.boot_completed` = 1); depois `./gradlew :apps:android:connectedDebugAndroidTest` **sem filtro**.
Expected: 0 falhas; os pulados dizem por quê. Confira o `timestamp` dos XML em `apps/android/build/outputs/androidTest-results/` e que `ColetaNaNotaInstrumentedTest` e `VarreduraEliminaOCorpusInstrumentedTest` **aparecem** com casos executados (um teste pulado por falta de permissão no Xiaomi não conta como passado).

- [ ] **Step 4: A saída por cabo, ponta a ponta no emulador**

Os instrumentados limpam `filesDir` ao terminar, então a saída por cabo se prova **à parte**, com amostras sintéticas no aparelho:

```bash
./gradlew :apps:android:installDebug
adb shell run-as com.platos.android mkdir -p files/corpus
adb push fixtures/corpus/amostra-exemplo.json /data/local/tmp/amostra.json
adb push fixtures/corpus/corpus-3b-prova1-frontal.jpg /data/local/tmp/foto.png
adb shell run-as com.platos.android cp /data/local/tmp/amostra.json files/corpus/3f2c9a52-1111-4000-8000-0000000000d1.json
adb shell run-as com.platos.android cp /data/local/tmp/foto.png files/corpus/3f2c9a52-1111-4000-8000-0000000000d1.png
node tools/corpus/puxar.mjs "$HOME/corpus-5d-ensaio"
```

Expected: `2 arquivo(s) puxado(s) ...; 1 amostra(s).` e `exit 0`; `cmp` entre `fixtures/corpus/corpus-3b-prova1-frontal.jpg` e o `.png` puxado não diz diferença (os bytes atravessaram `run-as cat` sem corrupção, que é o risco de `exec-out`). **Ver falhar:** `adb shell run-as com.platos.android rm files/corpus/3f2c9a52-1111-4000-8000-0000000000d1.png` e rodar de novo: `exit 1` com `falta a foto`. `puxar.mjs ./dentro-do-repo` → `exit 2`. Limpar: `adb shell run-as com.platos.android rm -r files/corpus`, `rm -r ~/corpus-5d-ensaio`, `adb shell rm /data/local/tmp/amostra.json /data/local/tmp/foto.png`. Desligar o emulador: `adb -s emulator-5554 emu kill`.

**O que isto não prova (P8):** que a coleta copia no aparelho físico com a câmera real; é a conferência do Leon, no fim, com a prova final (`conferencia-fisica-no-fim-da-fatia-5`).

- [ ] **Step 5: Escrever a cobertura**

Criar `docs/cobertura-slice-5d-corpus-de-medicao.md` com **os números e as saídas desta sessão** (nada aqui é copiado deste plano), nesta estrutura:

1. **Cabeçalho:** data, janela (`HH:MMZ`–`HH:MMZ`), ambiente (emulador, Docker ligado pelo Leon), base (`git rev-parse --short HEAD`).
2. **Linha de base e agregado:** o comando, `N de N tasks executadas`, suítes e testes dos XML (com o `timestamp`), e a linha de log de `verificarApkSemColeta`.
3. **Como cada verificação foi vista falhar:** uma tabela `garantia | mutação | cenários que caíram | revertida e rodada de novo`, com **uma linha por mutação** das Tarefas 1 a 5 (as 4 da 1, as 6 da 2, as 3 da 3, as 3 da 4, as 4 da 5, e a do `.gitignore`), preenchida com os nomes de teste que **realmente** caíram (leia a mensagem, P12).
4. **Saída por cabo:** o resultado do Step 4, com o `cmp` e a mutação do `.png` removido.
5. **Corpus coletado:** `nenhuma amostra coletada ainda` (a coleta é do Leon, no fim), com o campo para turmas, matérias, instrumento e contagem que o archive preenche.
6. **O que ainda não foi verificado:** coleta com a câmera real em aparelho físico; o Xiaomi exige o toque de permissão; o `grep` por leitura do corpus fora de `api/outbox/net/auth` (o teste cobre quatro pacotes); R8 (o literal sobrevive a ele, o descritor da classe não; não há R8 ainda); `ScanActivity.darNota` coberto só pelo instrumentado e por inspeção da ligação (P16).
7. **Dívida (P27):** o que o archive dirá de cada linha do §16 (as cinco da proposta).

- [ ] **Step 6: Commit**

```bash
git add docs/cobertura-slice-5d-corpus-de-medicao.md
git commit -m "docs(cobertura): como cada verificacao da 5d foi vista falhar

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 7: Fechar**

Invocar `superpowers:requesting-code-review` (revisor fresco sobre a branch inteira, modelo mais capaz) e, depois, `superpowers:finishing-a-development-branch`. **Não** marque a mudança como concluída sem o Step 2 a 4 rodados nesta sessão (P1). O push do código e o PR só com liberação do Leon (uma pergunta só: "posso empurrar?"); o archive (`/opsx:sync` e `/opsx:archive`, com o §16 e o `docs(arquitetura)`) vai em PR próprio depois do merge.

---

## Self-Review

**Spec coverage** (`measurement-corpus` e `scan-session`):

| Requisito | Tarefa |
|---|---|
| Amostra: foto com mesmos bytes, pontos, máximo, hash, item, id aleatório, versão, `referencia`/`descartar` | 2 (formato, cópia), 3 (instrumentado compara bytes) |
| Amostra nunca leva identificação; nome ≠ origem; dois ids independentes | 2 (testes de nome e de UUID v4), 3 (instrumentado confere o token) |
| Coleta só no APK debug, desligada por padrão | 2 (interruptor, `src/release`), 4 (`verificarApkSemColeta`) |
| Sai por cabo, nada ao servidor, conferência fora do aparelho, nada no repositório | 3 (`NenhumEnvioLeOCorpusTest`), 5 (`puxar.mjs`, `.gitignore`), 7 (ponta a ponta) |
| Pasta própria, fora do backup (`path="."`), prazo 30 dias, sair/revogação, resíduo, falha de uma eliminação | 2 (prazo, resíduo, estranho), 3 (`DeviceSession`, varredura) |
| scan-session: copia todas, antes de gravar, falha não derruba, nota não gravada não deixa amostra, cancelar não copia, eliminação da resposta não muda | 1 (regra), 3 (ligação e instrumentado) |

"Cancelar não copia" é por construção (a cópia só é chamada dentro de `darNota`, que só roda na confirmação): coberto pela leitura de `ScanActivity` no Step 8 da Tarefa 3 e pelo fato de `gravarNotaComColeta` ser o único chamador; **não tem teste próprio** (P8: conhecido, não mitigado) e está na lista de "não verificado" da cobertura.

**Placeholder scan:** nenhum "TBD/TODO". Os passos que dependem de execução (números, `timestamp`, conjuntos que caíram) dizem **o que** registrar e **onde**; não são lacunas de conteúdo do plano.

**Type consistency:** `AmostraACopiar`, `ResultadoDaCopia(ids, falhas)`, `ColetaDoCorpus` (5 métodos), `GravacaoComColeta(gravou, falhas)`, `gravarNotaComColeta(coleta, respostas, amostras, gravar)`, `coletaDoCorpus(filesDir)`, `ColetaDoCorpusEmArquivo.MARCADOR`/`diretorioDe`, `AmostraDoCorpus.json`/`serializer()` — mesmos nomes nas Tarefas 1, 2, 3 e 4. `TODAS` (internal) é usado em `CopiaDaNotaTest` (mesmo pacote e módulo).

**Review Focus:** as cinco linhas têm teste: zero (T1 `cada linha casa...`, T2 `a pontuacao zero...`), sem foto (T1), disco cheio (T2), relógio anterior (T2), arquivo estranho (T2).

**Riscos que o executor verá primeiro:** (1) `src/debug`/`src/release`/`src/testDebug` não existem hoje: se o Gradle não os compilar, o primeiro `testDebugUnitTest` da Tarefa 2 diz "Unresolved reference", e a causa é o conjunto de fontes, não o teste; (2) o laço de páginas do instrumentado (`0..3`); (3) o diretório de trabalho de `NenhumEnvioLeOCorpusTest`.
