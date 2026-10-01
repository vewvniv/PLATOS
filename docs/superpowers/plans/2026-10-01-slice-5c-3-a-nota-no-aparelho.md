# slice-5c-3-a-nota-no-aparelho — Plano de implementação

> **Para quem executa:** SUB-SKILL OBRIGATÓRIA: `superpowers:subagent-driven-development` (recomendado) ou `superpowers:executing-plans`. Os passos usam caixas (`- [ ]`). **Nunca `/opsx:apply`** (CLAUDE.md). Não marque caixa sem a execução que a fecha, na sessão em que marca (rigorous P1).

**Goal:** o professor dá a nota das discursivas sobre a imagem guardada, a nota entra na fila do outbox e sobe pela rota `graded` da 5c-2, o servidor ao confirmar autoriza eliminar as imagens, e o teto de 30 dias passa a rodar também em segundo plano.

**Architecture:** o contrato e o servidor já existem (5c-2); o aparelho passa a compor `NotaDoProfessor` com o **mesmo** código do domínio. O caderno ganha `capturaDaParcial`, `corrigido` e `questionId` por região; a nota sai pela mesma tabela do outbox (duas colunas e migration real 1→2); `EnvioDeResultados` ganha um gancho `aoConfirmar` que elimina as imagens do caderno que a nota completa; uma `VarreduraPeriodicaWorker` roda o teto de 30 dias com uma marca de processo que a segura enquanto o escaneamento está aberto.

**Tech Stack:** Kotlin (Android: Compose, Room, WorkManager, `ktor-client`), Kotlin Multiplatform (`packages/domain`), JUnit 5, Node (`tools/parity`). **Nenhuma tecnologia nova.**

**Spec:** `docs/superpowers/specs/2026-10-01-slice-5c-3-a-nota-no-aparelho-design.md` (decisões D1–D7). **Requisitos de entrada:** `openspec/changes/slice-5c-3-a-nota-no-aparelho/proposal.md` e `specs/{scan-session,result-sync}/spec.md`. Se uma tarefa revelar mudança de requisito: **pare e use `/opsx:update`**.

## Desvios deliberados do design (decididos lendo o código, e por isso escritos aqui)

1. **`rota` guarda `resultado` | `nota`, e não `parcial` | `graded`.** A rota antiga carrega também a nota objetiva completa, não só a parcial; `resultado` diz o que ela é.
2. **A nota e o caderno corrigido são gravados em sequência, nota primeiro, e não "na mesma operação".** São dois bancos Room (`outbox.db` e `caderno.db`), sem transação entre eles. A ordem torna a janela inofensiva: queda entre as duas deixa nota no outbox e caderno ainda "não corrigido"; o professor pode dar a nota de novo (o servidor aceita revisão nova, `result-sync`: "Nova nota do professor para a mesma folha") e **o gancho de confirmação acha o caderno por `capturaDaParcial`, e não por `corrigido`**.
3. **`RegiaoDoCaderno` ganha `questionId: String?`** (default nulo). `Caderno.novo` já lê `regiao.questionId` do mapa para derivar o rótulo; sem guardá-lo, a tela de nota não sabe qual `AwaitingEssay` casa com qual imagem. Caderno guardado antes (sem o campo) não oferece a nota e segue o caminho de hoje.
4. **`darNota` é a decisão e não muda a sessão; `confirmarCorrigido()` aplica depois da gravação.** A spec exige que falha de gravação deixe a tela aberta e nada vá ao caderno. `darNota` também marca "nota em curso", para o duplo toque não gerar duas notas.
5. **`NotaPorDarDeOutroAluno` não segura o quadro.** O analisador já gravou os arquivos do outro aluno antes de a sessão decidir; eles ficam órfãos e a varredura (que roda ao abrir, com `escaneamentoAberto = false`) os elimina. Depois de "descartar e seguir" ou de dar a nota, o professor escaneia a folha do outro aluno de novo (a sessão volta a `Searching`).
6. **Caderno corrigido da mesma folha lida de novo começa captura nova**: `anterior` deixa de reaproveitar caderno `corrigido` (o requisito "captura nova" da spec).

## Restrições globais

- Tabelas de domínio são autorizadas por `organization_id`; nenhuma tabela nova no servidor, nenhuma rota nova: o servidor não muda (spec, "Não será alterado").
- A nota não leva imagem, nome ou caminho de arquivo, nem nome, turma ou matrícula (I5; `result-sync`).
- Pontuação decimal: **até 2 casas, exata**, `Pontos` em centésimos; no fio, **string JSON** (`"1.75"`), nunca número (ADR-0021).
- O aparelho **nunca** envia imagem (política §6.4).
- **Sem `fallbackToDestructiveMigration`** no outbox: a fila guarda correção que não existe em outro lugar.
- Sem tecnologia nova; sem Redis/broker/vector DB/GraphQL (CLAUDE.md).
- Commits: um por unidade lógica, em português no estilo do histórico, terminando com `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. **Não empurre, não abra PR e não mergeie sem o Leon pedir**; push só com refspec explícito.
- Ambiente: **não instale nem atualize JDK, Node, SDK, emulador, plugin ou dependência sem perguntar** (P22). Docker + emulador + Gradle estouram 16 GB: a ordem que coube está em `suite-cheia-precisa-de-docker-e-memoria`. Testes instrumentados (`androidTest`) só rodam com o Leon liberando o emulador; a câmera do Xiaomi exige o toque dele (`teste-no-xiaomi-exige-toque-manual`).
- `--tests` **reprova** em `:packages:domain:jvmTest` e em `:apps:android` (guardas de `@Test` sem resultado): rode o módulo inteiro. Um verde filtrado não fecha nada (P5).
- Texto de interface **não leva acento**; documentação leva. **`ScanActivity.kt` e `design.md` têm CRLF**: edite com `Edit` e confira `git diff --stat` (uma edição de poucas linhas não pode reescrever o arquivo).
- Toda asserção de teste existente que mudar leva o motivo no commit (P12).

## Foco da revisão (o que as specs implicam e nenhuma tarefa óbvia exercita)

1. **`1,5` (vírgula, como o pt-BR digita)** no campo da nota: recusado com a mensagem do domínio ("ponto decimal"), nunca lido como `15` nem gravado → Task 7 (`EntradaDaNotaTest`).
2. **Duplo toque em "Gravar" antes da gravação terminar**: a segunda chamada é recusada ("ja esta sendo gravada") e não gera segunda nota com chave nova → Task 3.
3. **Queda entre gravar a nota e gravar o caderno**: a nota fica pendente com o caderno ainda "não corrigido"; a confirmação **ainda assim** elimina as imagens (chave é `capturaDaParcial`) → Task 6.
4. **A confirmação chega depois de outro aluno ter substituído o caderno**: o gancho não toca o caderno novo (captura diferente); as imagens antigas são órfãs e a varredura as elimina → Task 6.
5. **Arquivo que falha ao eliminar no gancho**: os outros são eliminados, a falha é contada e **a confirmação não é desfeita** → Task 6.
6. **Nota máxima em todas e zero em todas**, e discursiva com `worth` 0 → total exato e aceito → Task 3.

---

## Mapa de arquivos

| Arquivo | Ação | Responsabilidade |
|---|---|---|
| `packages/domain/src/commonMain/.../scoring/Pontos.kt` | modificar | `lerPontos(texto, de)` |
| `packages/domain/src/commonTest/.../scoring/PontosTest.kt` | modificar | testes de `lerPontos` |
| `apps/api/src/main/kotlin/com/platos/api/http/dto/GradedResultDto.kt` | modificar | passa a importar `lerPontos` |
| `apps/android/src/main/kotlin/com/platos/android/scan/Caderno.kt` | modificar | `capturaDaParcial`, `corrigido`, `questionId`, `aguardaNota`, `corrigidoSemRespostas`, `normalizado` |
| `apps/android/src/main/kotlin/com/platos/android/scan/ScanSession.kt` | modificar | id injetado, `darNota`, `confirmarCorrigido`, `falhouAGravacao`, substituição protegida, `descartarESeguir` |
| `apps/android/src/main/kotlin/com/platos/android/scan/ScanState.kt` | modificar | `NotaPorDarDeOutroAluno` |
| `apps/android/src/main/kotlin/com/platos/android/outbox/NotaPendente.kt` | criar | `NotaPendente`, `RotaDoEnvio` |
| `apps/android/src/main/kotlin/com/platos/android/api/ResultadoDto.kt` | modificar | `NotaPendente.corpoDoEnvio()` |
| `apps/android/src/main/kotlin/com/platos/android/outbox/ResultadoPendente.kt` | modificar | `EnvelopeDeEnvio.rota/completaCaptura`, `guardarNota` |
| `apps/android/src/main/kotlin/com/platos/android/outbox/ResultadosEmRoom.kt` | modificar | colunas, `Migration(1, 2)`, `guardarNota` |
| `apps/android/src/main/kotlin/com/platos/android/api/ApiPlatos.kt` | modificar | `enviarNota` |
| `apps/android/src/main/kotlin/com/platos/android/outbox/EnvioDeResultados.kt` | modificar | `aoConfirmar`, `falhasAoConfirmar` |
| `apps/android/src/main/kotlin/com/platos/android/outbox/EliminarAoConfirmar.kt` | criar | a decisão pura da eliminação |
| `apps/android/src/main/kotlin/com/platos/android/outbox/EnvioDeResultadosWorker.kt` | modificar | despacho por rota, gancho, diagnóstico |
| `apps/android/src/main/kotlin/com/platos/android/scan/EntradaDaNota.kt` | criar | texto → pontuações, puro |
| `apps/android/src/main/kotlin/com/platos/android/scan/NotaTela.kt` | criar | a tela de nota (Compose) |
| `apps/android/src/main/kotlin/com/platos/android/scan/ScanScreen.kt` | modificar | botão, painel do outro aluno, sobreposição da nota |
| `apps/android/src/main/kotlin/com/platos/android/scan/ScanActivity.kt` | modificar (CRLF) | `darNota`, gravação em sequência, marca `EscaneamentoAberto` |
| `apps/android/src/main/kotlin/com/platos/android/scan/RetencaoDaResposta.kt` | modificar | parâmetro `escaneamentoAberto` |
| `apps/android/src/main/kotlin/com/platos/android/scan/EscaneamentoAberto.kt` | criar | a marca e a trava |
| `apps/android/src/main/kotlin/com/platos/android/scan/VarreduraDoAparelho.kt` | modificar | núcleo não suspenso + parâmetro |
| `apps/android/src/main/kotlin/com/platos/android/scan/VarreduraPeriodicaWorker.kt` | criar | o agendamento de 24 h |
| `apps/android/src/main/kotlin/com/platos/android/session/SessaoActivity.kt` | modificar | agenda o worker periódico |
| testes JVM em `apps/android/src/test/...` | criar | um arquivo por tarefa (nomes nas tarefas) |
| testes instrumentados em `apps/android/src/androidTest/...` | criar | migration e nota no outbox |
| `docs/cobertura-slice-5c-3-a-nota-no-aparelho.md` | criar | o que foi e o que não foi verificado |

---

### Task 1: `lerPontos` sobe para o domínio

**Files:**
- Modify: `packages/domain/src/commonMain/kotlin/com/platos/domain/scoring/Pontos.kt`
- Modify: `packages/domain/src/commonTest/kotlin/com/platos/domain/scoring/PontosTest.kt`
- Modify: `apps/api/src/main/kotlin/com/platos/api/http/dto/GradedResultDto.kt`

**Interfaces:**
- Consumes: `Pontos.parse(texto: String): Pontos` (lança `IllegalArgumentException`).
- Produces: `fun lerPontos(texto: String, de: String): Pontos` em `com.platos.domain.scoring`; a mensagem de erro é `"$de: ${erro.message}"`.

- [ ] **Step 1: Escreva os testes que falham** — acrescente a `PontosTest.kt` (dentro da classe, mantendo os imports existentes e acrescentando `kotlin.test.assertFailsWith` se ainda não houver):

```kotlin
    @Test
    fun `lerPontos devolve o valor exato`() {
        assertEquals("1.75", lerPontos("1.75", "a discursiva 'd1'").toString())
    }

    @Test
    fun `lerPontos diz de qual campo veio o erro`() {
        val erro = assertFailsWith<IllegalArgumentException> { lerPontos("1,5", "a discursiva 'd1'") }
        assertEquals(true, erro.message!!.startsWith("a discursiva 'd1': "), erro.message)
        assertEquals(true, erro.message!!.contains("ponto decimal"), erro.message)
    }

    @Test
    fun `lerPontos recusa tres casas e negativo`() {
        assertFailsWith<IllegalArgumentException> { lerPontos("1.333", "x") }
        assertFailsWith<IllegalArgumentException> { lerPontos("-1", "x") }
    }
```

- [ ] **Step 2: Rode e veja falhar**

Run: `./gradlew :packages:domain:jvmTest`
Expected: FAIL (compilação: `lerPontos` não definida).

- [ ] **Step 3: Implemente** — ao fim de `Pontos.kt`, fora do `value class`:

```kotlin
/**
 * Le a pontuacao e, se falhar, diz **de qual campo** veio o texto: a mensagem de recusa nomeia a questao
 * (spec `result-sync`), no servidor, e a tela de nota nomeia a questao no aparelho. Mora no dominio para
 * que os dois leiam com o mesmo codigo (regra 7 do `CLAUDE.md`).
 */
fun lerPontos(texto: String, de: String): Pontos =
    try {
        Pontos.parse(texto)
    } catch (erro: IllegalArgumentException) {
        throw IllegalArgumentException("$de: ${erro.message}")
    }
```

Em `apps/api/.../GradedResultDto.kt`: apague a função privada `lerPontos` (as últimas linhas do arquivo) e acrescente `import com.platos.domain.scoring.lerPontos`.

- [ ] **Step 4: Rode domínio e api** (a api exige Docker de pé; o Leon o inicia)

Run: `./gradlew :packages:domain:jvmTest :apps:api:test`
Expected: PASS. Os testes de `paraNotaSubmetida` passam **sem mudança de asserção** (as mensagens de 400 são as mesmas).

- [ ] **Step 5: Commit**

```bash
git add packages/domain apps/api/src/main/kotlin/com/platos/api/http/dto/GradedResultDto.kt
git commit -m "refactor(domain): lerPontos sobe para o dominio, o aparelho vai usar o mesmo codigo do servidor"
```

---

### Task 2: O caderno guarda a captura da parcial, a região da questão e o estado "corrigido"

**Files:**
- Modify: `apps/android/src/main/kotlin/com/platos/android/scan/Caderno.kt`
- Create: `apps/android/src/test/kotlin/com/platos/android/scan/CadernoCorrigidoTest.kt`
- Modify (se a asserção de literal quebrar): `apps/android/src/test/kotlin/com/platos/android/scan/CadernoSerializationTest.kt`

**Interfaces:**
- Produces:
  - `RegiaoDoCaderno.questionId: String? = null`
  - `Caderno.capturaDaParcial: String? = null`, `Caderno.corrigido: Boolean = false`
  - `Caderno.aguardaNota: Boolean` — completo, entregue, não corrigido, com `capturaDaParcial` e com `questionId` em toda discursiva
  - `internal fun Caderno.corrigidoSemRespostas(): Caderno` — todas as `resposta = null`, `corrigido = true`, estados e `entregue` intocados
  - `Caderno.normalizado` devolve `this` quando `corrigido`

- [ ] **Step 1: Escreva o teste que falha** — `CadernoCorrigidoTest.kt`:

```kotlin
package com.platos.android.scan

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** O que o caderno diz de uma nota por dar, de uma nota dada e do que a eliminacao das imagens nao desfaz. */
class CadernoCorrigidoTest {

    private fun resposta(arquivo: String) = RespostaGuardada(arquivo, 0L, false, 0)

    private fun completo(corrigido: Boolean = false, entregue: Boolean = true, captura: String? = "parcial-1") = Caderno(
        aluno = "tok-a",
        regioes = listOf(
            RegiaoDoCaderno(0, true, "Gabarito", EstadoDaRegiao.Capturada),
            RegiaoDoCaderno(1, false, "3", EstadoDaRegiao.Capturada, resposta("r1.png"), questionId = "d1"),
            RegiaoDoCaderno(2, false, "4", EstadoDaRegiao.Capturada, resposta("r2.png"), questionId = "d2"),
        ),
        parcial = null,
        entregue = entregue,
        capturaDaParcial = captura,
        corrigido = corrigido,
    )

    @Test
    fun `caderno completo, entregue e sem nota aguarda a nota`() {
        assertTrue(completo().aguardaNota)
    }

    @Test
    fun `caderno corrigido, nao entregue ou sem captura da parcial nao aguarda`() {
        assertFalse(completo(corrigido = true).aguardaNota)
        assertFalse(completo(entregue = false).aguardaNota)
        assertFalse(completo(captura = null).aguardaNota)
    }

    @Test
    fun `caderno incompleto nao aguarda a nota`() {
        val incompleto = completo().let { c ->
            c.copy(regioes = c.regioes.map { if (it.regionIndex == 2) it.copy(estado = EstadoDaRegiao.NaoVista, resposta = null) else it })
        }
        assertFalse(incompleto.aguardaNota)
    }

    @Test
    fun `discursiva sem questionId, de caderno guardado antes, nao oferece a nota`() {
        val antigo = completo().let { c -> c.copy(regioes = c.regioes.map { it.copy(questionId = null) }) }
        assertFalse(antigo.aguardaNota)
    }

    @Test
    fun `corrigidoSemRespostas tira so as respostas e marca corrigido`() {
        val depois = completo().corrigidoSemRespostas()

        assertTrue(depois.corrigido)
        assertTrue(depois.entregue)
        assertEquals("parcial-1", depois.capturaDaParcial)
        assertTrue(depois.regioes.all { it.resposta == null })
        assertEquals(3, depois.capturadas, "eliminar as imagens nao devolve regiao a nao vista")
    }

    @Test
    fun `normalizado nao devolve a nao vista a regiao de um caderno corrigido`() {
        val corrigido = completo(corrigido = true).corrigidoSemRespostas()

        assertEquals(corrigido, corrigido.normalizado { false })
    }

    @Test
    fun `normalizado continua devolvendo a nao vista quando o caderno nao esta corrigido`() {
        val lido = completo().normalizado { false }

        assertEquals(1, lido.capturadas, "so o gabarito sobrevive sem os arquivos")
    }

    @Test
    fun `caderno guardado antes dos campos novos continua legivel`() {
        val antigo = """{"aluno":"tok-a","regioes":[],"parcial":null}"""

        val lido = Json.decodeFromString(Caderno.serializer(), antigo)

        assertNull(lido.capturaDaParcial)
        assertFalse(lido.corrigido)
    }
}
```

- [ ] **Step 2: Rode e veja falhar**

Run: `./gradlew :apps:android:testDebugUnitTest`
Expected: FAIL (compilação: campos novos inexistentes).

- [ ] **Step 3: Implemente em `Caderno.kt`**

Em `RegiaoDoCaderno`, depois de `resposta`:

```kotlin
    /**
     * A questao que a regiao discursiva corrige, como o mapa a declara (`slice-5c-3-a-nota-no-aparelho`):
     * a tela de nota casa cada imagem com o `AwaitingEssay` dela por aqui. Nulo no gabarito, e em caderno
     * guardado antes desta mudanca — que entao nao oferece a nota.
     */
    val questionId: String? = null,
```

Em `Caderno`, depois de `entregue`:

```kotlin
    /**
     * A captura da parcial que este caderno entregou (`slice-5c-3-a-nota-no-aparelho`, design D5): e o
     * `completes_capture_id` da nota do professor. Marcada na mesma passada em que [entregue].
     */
    val capturaDaParcial: String? = null,
    /**
     * Se o professor ja deu a nota deste caderno. Caderno corrigido **nao e caderno em andamento**: as
     * imagens dele sao eliminadas quando o servidor confirma a nota, e isso nao o devolve a incompleto.
     */
    val corrigido: Boolean = false,
```

Dentro da classe, depois de `esperadas`:

```kotlin
    /**
     * Se a tela pode oferecer **dar a nota**: caderno completo, entregue (a parcial foi apurada), sem nota, com a
     * captura da parcial na mao e com a questao de cada discursiva conhecida.
     */
    val aguardaNota: Boolean
        get() = !corrigido && entregue && capturaDaParcial != null && esperadas > 0 &&
            capturadas == esperadas && regioes.all { it.gabarito || it.questionId != null }
```

Em `novo`, no `RegiaoDoCaderno(...)` construído por região, acrescente `questionId = if (discursiva) regiao.questionId else null,` depois de `estado = EstadoDaRegiao.NaoVista,`.

No fim do arquivo, acrescente:

```kotlin
/**
 * Este caderno depois de a nota ser confirmada pelo servidor e as imagens eliminadas: sem nenhuma resposta, e
 * `corrigido`. O estado das regioes e [Caderno.entregue] ficam como estavam — eliminar a imagem de caderno
 * corrigido nao o devolve a incompleto (spec `scan-session`).
 */
internal fun Caderno.corrigidoSemRespostas(): Caderno =
    copy(regioes = regioes.map { it.copy(resposta = null) }, corrigido = true)
```

Em `normalizado`, como primeira linha do corpo: transforme `internal fun Caderno.normalizado(existe: (String) -> Boolean): Caderno =\n    copy(` em

```kotlin
internal fun Caderno.normalizado(existe: (String) -> Boolean): Caderno =
    if (corrigido) this else copy(
```

(e feche o `copy(...)` como está; a KDoc ganha a frase "Caderno **corrigido** volta intacto: as respostas dele foram eliminadas de proposito.").

- [ ] **Step 4: Rode a suíte do aparelho**

Run: `./gradlew :apps:android:testDebugUnitTest`
Expected: PASS. Se `CadernoSerializationTest` comparar um literal JSON de caderno construído por `Caderno.novo`, ele agora traz `"questionId":"d1"` nas discursivas: atualize **só esse literal** e diga no commit que o campo é aditivo (P12). Nenhuma outra asserção muda.

- [ ] **Step 5: Commit**

```bash
git add apps/android/src
git commit -m "feat(scan): o caderno guarda a captura da parcial, a questao da regiao e o estado corrigido"
```

---

### Task 3: A sessão dá a nota e protege o caderno completo sem nota

**Files:**
- Modify: `apps/android/src/main/kotlin/com/platos/android/scan/ScanState.kt`
- Modify: `apps/android/src/main/kotlin/com/platos/android/scan/ScanSession.kt`
- Modify: `apps/android/src/main/kotlin/com/platos/android/scan/ScanActivity.kt` (CRLF; só `gravar`)
- Modify: `apps/android/src/main/kotlin/com/platos/android/scan/ScanScreen.kt` (só o ramo novo do `when`, exaustivo)
- Create: `apps/android/src/test/kotlin/com/platos/android/scan/NotaNaSessaoTest.kt`

**Interfaces:**
- Consumes: Task 2 (`aguardaNota`, `capturaDaParcial`, `corrigido`, `questionId`); `CorrecaoDoProfessor.completar(parcial, pontuacoes)`, `PontuacaoDada`, `NotaDoProfessor` do domínio.
- Produces:
  - `ScanSession(examPackage, cadernoInicial = null, novoId: () -> String = { UUID.randomUUID().toString() })`
  - `ApuracaoNova.DeCaderno(aluno: String, score: PartialScore, captureId: String)`
  - `ScanSession.darNota(pontuacoes: List<PontuacaoDada>): ResultadoDaNota`
  - `sealed interface ResultadoDaNota { data class Corrigida(val nota: NotaDoProfessor, val completaCaptura: String, val aluno: String, val cadernoCorrigido: Caderno); data class Recusada(val motivo: String) }`
  - `ScanSession.confirmarCorrigido()`, `ScanSession.falhouAGravacao()`
  - `ScanSession.descartarESeguir(): List<String>` (arquivos a eliminar)
  - `ScanState.NotaPorDarDeOutroAluno(val caderno: Caderno, val alunoNovo: String)`

- [ ] **Step 1: Escreva os testes que falham** — `NotaNaSessaoTest.kt`:

```kotlin
package com.platos.android.scan

import com.platos.android.vision.FrameOutcome
import com.platos.android.vision.RegiaoDiscursivaNoQuadro
import com.platos.domain.capture.CapturePayload
import com.platos.domain.capture.InterpretedReading
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.exam.ExamPackage
import com.platos.domain.scoring.PontuacaoDada
import com.platos.domain.scoring.Pontos
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A nota do professor na sessao (`slice-5c-3-a-nota-no-aparelho`, spec `scan-session`): o que a sessao decide
 * ao dar a nota, ao proteger o caderno completo sem nota e ao comecar captura nova.
 *
 * A fixture e a de `ProvaComDiscursivaNaSessaoTest`: `q1,q2,q4,q5` objetivas (parcial 3 de 4), `d1` vale 3 e
 * `d2` vale 4, a prova vale 11.
 */
class NotaNaSessaoTest {

    private val fixtures = File(
        System.getProperty("platos.fixtures") ?: error("propriedade `platos.fixtures` nao definida pelo build"),
    )
    private val pacote: ExamPackage =
        Json.decodeFromString(File(fixtures, "prova-discursiva.package.json").readText())

    private var proximoId = 0
    private fun sessao() = ScanSession(pacote, novoId = { "parcial-${++proximoId}" }).apply { onPermission(true) }

    private fun payload(regiao: Int, token: String) = CapturePayload(pacote.meta.examId, token, "v1", regiao)
    private fun gabarito(token: String = "tok-a") = InterpretedReading(
        payload(0, token),
        emptyList(),
        listOf(
            QuestionAnswer.Marcada("q1", "A"),
            QuestionAnswer.Marcada("q2", "C"),
            QuestionAnswer.Marcada("q4", "B"),
            QuestionAnswer.Marcada("q5", "B"),
        ),
    )
    private fun d1(token: String = "tok-a") = RegiaoDiscursivaNoQuadro.Reconhecida(1, "d1", payload(1, token))
    private fun d2(token: String = "tok-a") = RegiaoDiscursivaNoQuadro.Reconhecida(2, "d2", payload(2, token))

    private fun ScanSession.quadro(outcome: FrameOutcome): ApuracaoNova? = onFrame(
        outcome,
        outcome.discursivas.filterIsInstance<RegiaoDiscursivaNoQuadro.Reconhecida>().associate {
            it.regionIndex to RespostaDoQuadro.Guardada(RespostaGuardada("r${it.regionIndex}.png", 0L, false, 0))
        },
    )

    private fun completa(token: String = "tok-a"): ScanSession = sessao().also {
        it.quadro(FrameOutcome.Read(gabarito(token), listOf(d1(token))))
        it.quadro(FrameOutcome.SoDiscursivas(listOf(d2(token))))
    }

    private fun notas(d1: String = "2.5", d2: String = "3.75") =
        listOf(PontuacaoDada("d1", Pontos.parse(d1)), PontuacaoDada("d2", Pontos.parse(d2)))

    private fun corrigida(r: ResultadoDaNota) = r as? ResultadoDaNota.Corrigida
        ?: throw AssertionError("esperava a nota aceita, veio $r")

    private fun recusada(r: ResultadoDaNota) = (r as? ResultadoDaNota.Recusada
        ?: throw AssertionError("esperava a recusa, veio $r")).motivo

    // --- a captura da parcial ---

    @Test
    fun `a entrega leva o id cunhado, e o caderno o guarda`() {
        val sessao = sessao()
        sessao.quadro(FrameOutcome.Read(gabarito(), listOf(d1())))

        val entrega = sessao.quadro(FrameOutcome.SoDiscursivas(listOf(d2()))) as ApuracaoNova.DeCaderno

        assertEquals("parcial-1", entrega.captureId)
        assertEquals("parcial-1", sessao.cadernoAtual!!.capturaDaParcial)
    }

    // --- dar a nota ---

    @Test
    fun `a nota aceita traz o total exato, a captura que completa e o caderno corrigido`() {
        val sessao = completa()

        val r = corrigida(sessao.darNota(notas()))

        assertEquals("9.25", r.nota.total.toString()) // 3 objetivos + 2.5 + 3.75
        assertEquals("parcial-1", r.completaCaptura)
        assertEquals("tok-a", r.aluno)
        assertTrue(r.cadernoCorrigido.corrigido)
    }

    @Test
    fun `darNota nao muda o caderno ate a gravacao ser confirmada`() {
        val sessao = completa()

        sessao.darNota(notas())

        assertFalse(sessao.cadernoAtual!!.corrigido)
    }

    @Test
    fun `confirmada a gravacao, o caderno passa a corrigido, e um quadro seguinte nao reentrega a parcial`() {
        val sessao = completa()
        sessao.darNota(notas())

        sessao.confirmarCorrigido()

        assertTrue(sessao.cadernoAtual!!.corrigido)
        assertNull(sessao.quadro(FrameOutcome.SoDiscursivas(listOf(d2()))), "caderno novo, 1 de 3: nada a entregar")
    }

    @Test
    fun `caderno corrigido nao oferece a nota outra vez`() {
        val sessao = completa()
        sessao.darNota(notas())
        sessao.confirmarCorrigido()

        assertTrue(recusada(sessao.darNota(notas())).contains("corrigido"))
    }

    @Test
    fun `o duplo toque nao gera duas notas, e falhar a gravacao libera de novo`() {
        val sessao = completa()
        corrigida(sessao.darNota(notas()))

        assertTrue(recusada(sessao.darNota(notas())).contains("sendo gravada"))

        sessao.falhouAGravacao()
        corrigida(sessao.darNota(notas()))
    }

    @Test
    fun `caderno incompleto nao tem nota`() {
        val sessao = sessao()
        sessao.quadro(FrameOutcome.Read(gabarito(), listOf(d1())))

        assertTrue(recusada(sessao.darNota(notas())).contains("completo"))
    }

    @Test
    fun `sem caderno nao ha nota`() {
        assertTrue(recusada(sessao().darNota(notas())).contains("caderno"))
    }

    @Test
    fun `a faixa do pacote e a do dominio, aqui e no servidor`() {
        val sessao = completa()

        assertTrue(recusada(sessao.darNota(notas(d1 = "3.01"))).contains("d1"))
        assertTrue(recusada(sessao.darNota(listOf(PontuacaoDada("d1", Pontos.parse("1"))))).contains("d2"))
    }

    @Test
    fun `zero em todas e maximo em todas fecham a soma exata`() {
        assertEquals("3.00", corrigida(completa().darNota(notas("0", "0"))).nota.total.toString())
        assertEquals("10.00", corrigida(completa().darNota(notas("3", "4"))).nota.total.toString())
    }

    // --- a mesma folha, depois de corrigida ---

    @Test
    fun `a mesma folha lida de novo depois de corrigida comeca captura nova`() {
        val sessao = completa()
        sessao.darNota(notas())
        sessao.confirmarCorrigido()

        sessao.quadro(FrameOutcome.Read(gabarito(), listOf(d1())))
        val entrega = sessao.quadro(FrameOutcome.SoDiscursivas(listOf(d2()))) as ApuracaoNova.DeCaderno

        assertEquals("parcial-2", entrega.captureId)
        assertFalse(sessao.cadernoAtual!!.corrigido)
    }

    // --- a substituicao protegida ---

    @Test
    fun `outro aluno nao substitui o caderno completo sem nota`() {
        val sessao = completa("tok-a")

        val aoTrocar = sessao.quadro(FrameOutcome.SoDiscursivas(listOf(d2("tok-b"))))

        assertNull(aoTrocar)
        val estado = sessao.state as ScanState.NotaPorDarDeOutroAluno
        assertEquals("tok-a", estado.caderno.aluno)
        assertEquals("tok-b", estado.alunoNovo)
        assertEquals("tok-a", sessao.cadernoAtual!!.aluno, "o caderno de tok-a segue sendo o corrente")
    }

    @Test
    fun `descartar e seguir perde o caderno e devolve os arquivos a eliminar`() {
        val sessao = completa("tok-a")
        sessao.quadro(FrameOutcome.SoDiscursivas(listOf(d2("tok-b"))))

        val arquivos = sessao.descartarESeguir()

        assertEquals(listOf("r1.png", "r2.png"), arquivos)
        assertNull(sessao.cadernoAtual)
        assertEquals(ScanState.Searching, sessao.state)
        sessao.quadro(FrameOutcome.SoDiscursivas(listOf(d2("tok-b"))))
        assertEquals("tok-b", sessao.cadernoAtual!!.aluno)
    }

    @Test
    fun `dar a nota e seguir libera a substituicao`() {
        val sessao = completa("tok-a")
        sessao.quadro(FrameOutcome.SoDiscursivas(listOf(d2("tok-b"))))

        corrigida(sessao.darNota(notas()))
        sessao.confirmarCorrigido()

        assertEquals(ScanState.Searching, sessao.state)
        sessao.quadro(FrameOutcome.SoDiscursivas(listOf(d2("tok-b"))))
        assertEquals("tok-b", sessao.cadernoAtual!!.aluno)
    }

    @Test
    fun `caderno incompleto continua sendo substituido sem confirmacao`() {
        val sessao = sessao()
        sessao.quadro(FrameOutcome.Read(gabarito("tok-a"), listOf(d1("tok-a"))))

        sessao.quadro(FrameOutcome.SoDiscursivas(listOf(d2("tok-b"))))

        assertEquals("tok-b", sessao.cadernoAtual!!.aluno)
        assertTrue(sessao.state is ScanState.ProvaComDiscursiva)
    }

    @Test
    fun `caderno corrigido tambem e substituido sem confirmacao`() {
        val sessao = completa("tok-a")
        sessao.darNota(notas())
        sessao.confirmarCorrigido()

        sessao.quadro(FrameOutcome.SoDiscursivas(listOf(d2("tok-b"))))

        assertEquals("tok-b", sessao.cadernoAtual!!.aluno)
    }
}
```

- [ ] **Step 2: Rode e veja falhar**

Run: `./gradlew :apps:android:testDebugUnitTest`
Expected: FAIL (compilação).

- [ ] **Step 3: Implemente**

`ScanState.kt` — dentro de `sealed interface ScanState`, depois de `ProvaComDiscursiva`:

```kotlin
    /**
     * A folha de **outro** aluno apareceu enquanto o caderno corrente esta completo e sem nota
     * (`slice-5c-3-a-nota-no-aparelho`, spec `scan-session`). A sessao **nao** troca o caderno: oferece dar a
     * nota ou descartar e seguir, porque descartar perde as respostas capturadas. O quadro nao e guardado: depois
     * da escolha, o professor escaneia a folha do outro aluno de novo.
     */
    data class NotaPorDarDeOutroAluno(val caderno: Caderno, val alunoNovo: String) : ScanState
```

`ScanSession.kt`:

1. Imports: `com.platos.domain.scoring.CorrecaoDoProfessor`, `NotaDoProfessor`, `NotaDoProfessorOutcome`, `PontuacaoDada`, `java.util.UUID`.
2. Construtor: `class ScanSession(private val examPackage: ExamPackage, cadernoInicial: Caderno? = null, private val novoId: () -> String = { UUID.randomUUID().toString() })`.
3. `holdsResult` ganha `|| state is ScanState.NotaPorDarDeOutroAluno`.
4. Em `estadoDaDiscursiva`, **antes** de `// O caderno e do aluno...` (o `val anterior`), insira:

```kotlin
        // Caderno completo e sem nota nao e substituido em silencio (`slice-5c-3-a-nota-no-aparelho`): descartar
        // perde as respostas, e quem decide e o professor.
        val corrente = caderno
        if (corrente != null && corrente.aluno != aluno && corrente.aguardaNota) {
            return ScanState.NotaPorDarDeOutroAluno(corrente, aluno) to null
        }
```

e troque `val anterior = caderno?.takeIf { it.aluno == aluno } ?: run {` por `val anterior = caderno?.takeIf { it.aluno == aluno && !it.corrigido } ?: run {` (caderno corrigido da mesma folha lida de novo começa captura nova).

5. Em `completouAgora`: troque

```kotlin
        val atual = if (completouAgora) atualizado.copy(entregue = true) else atualizado
```
por
```kotlin
        val capturaDaParcial = if (completouAgora) novoId() else null
        val atual = if (completouAgora) {
            atualizado.copy(entregue = true, capturaDaParcial = capturaDaParcial)
        } else {
            atualizado
        }
```
e a entrega por `ApuracaoNova.DeCaderno(aluno, (atual.parcial as PartialScoringOutcome.Scored).partial, requireNotNull(capturaDaParcial))`.

6. Antes de `private var caderno`, acrescente os métodos:

```kotlin
    /** Se uma nota ja foi decidida e a gravacao dela ainda nao terminou: o duplo toque nao gera duas notas. */
    private var notaEmCurso = false

    /**
     * Decide a nota do professor sobre o caderno **corrente** — o da tela, ou o que aguarda nota quando outro aluno
     * apareceu. **Nao muda o caderno**: a gravacao pode falhar, e a spec manda deixar a tela aberta com o motivo e
     * nada no caderno. Quem grava chama [confirmarCorrigido] depois, ou [falhouAGravacao].
     */
    fun darNota(pontuacoes: List<PontuacaoDada>): ResultadoDaNota {
        val atual = caderno ?: return ResultadoDaNota.Recusada("nao ha caderno em andamento")
        if (atual.corrigido) return ResultadoDaNota.Recusada("o caderno ja foi corrigido")
        if (notaEmCurso) return ResultadoDaNota.Recusada("a nota ja esta sendo gravada")
        if (!atual.aguardaNota) return ResultadoDaNota.Recusada("o caderno ainda nao esta completo")
        val parcial = (atual.parcial as? PartialScoringOutcome.Scored)?.partial
            ?: return ResultadoDaNota.Recusada("a parcial do caderno nao foi apurada")
        val captura = requireNotNull(atual.capturaDaParcial) // `aguardaNota` ja exige

        return when (val r = CorrecaoDoProfessor.completar(parcial, pontuacoes)) {
            is NotaDoProfessorOutcome.Rejected -> ResultadoDaNota.Recusada(r.reason)
            is NotaDoProfessorOutcome.Scored -> {
                notaEmCurso = true
                ResultadoDaNota.Corrigida(r.nota, captura, atual.aluno, atual.copy(corrigido = true))
            }
        }
    }

    /** A nota foi gravada: o caderno passa a corrigido, e quem estava segurando a troca de aluno a libera. */
    fun confirmarCorrigido() {
        val atual = caderno ?: return
        notaEmCurso = false
        val novo = atual.copy(corrigido = true)
        caderno = novo
        state = when (val s = state) {
            is ScanState.NotaPorDarDeOutroAluno -> ScanState.Searching
            is ScanState.ProvaComDiscursiva -> s.copy(caderno = novo)
            else -> s
        }
    }

    /** A gravacao da nota falhou: nada mudou, e o professor pode tentar de novo. */
    fun falhouAGravacao() {
        notaEmCurso = false
    }

    /**
     * Descarta o caderno completo sem nota e volta a procurar. Devolve os arquivos a eliminar — quem elimina e quem
     * chama, porque a sessao nao toca disco. Nada e gravado nem enviado.
     */
    fun descartarESeguir(): List<String> {
        val arquivos = caderno?.regioes?.mapNotNull { it.resposta?.arquivo }.orEmpty()
        caderno = null
        notaEmCurso = false
        state = ScanState.Searching
        return arquivos
    }
```

7. No fim do arquivo (junto de `ResultadoDoRefazer`):

```kotlin
/** O que [ScanSession.darNota] decidiu. */
sealed interface ResultadoDaNota {

    /** A nota foi aceita; [cadernoCorrigido] e o que se grava no Room junto do pendente. */
    data class Corrigida(
        val nota: NotaDoProfessor,
        val completaCaptura: String,
        val aluno: String,
        val cadernoCorrigido: Caderno,
    ) : ResultadoDaNota

    /** Nada mudou; [motivo] serve para a tela. */
    data class Recusada(val motivo: String) : ResultadoDaNota
}
```

8. `ApuracaoNova.DeCaderno` ganha `val captureId: String` (terceiro parâmetro), com KDoc "a captura da parcial, cunhada na transição: é o `completes_capture_id` da nota do professor".

`ScanActivity.kt` (CRLF — `Edit`, poucas linhas) — em `gravar`, troque o ramo `captureId = UUID.randomUUID().toString(),` por:

```kotlin
            captureId = (apuracao as? ApuracaoNova.DeCaderno)?.captureId ?: UUID.randomUUID().toString(),
```

`ScanScreen.kt` — no `when (state)`, depois do ramo `is ScanState.ProvaComDiscursiva -> ... }` acrescente temporariamente (a Task 7 o substitui pelo painel real):

```kotlin
            is ScanState.NotaPorDarDeOutroAluno -> Faixa("O caderno de outro aluno aguarda a nota")
```

- [ ] **Step 4: Rode a suíte do aparelho**

Run: `./gradlew :apps:android:testDebugUnitTest`
Expected: PASS, incluindo os testes antigos de `ProvaComDiscursivaNaSessaoTest` **sem mudar asserção** (a troca de aluno ali acontece com caderno incompleto).

- [ ] **Step 5: Commit**

```bash
git add apps/android/src
git commit -m "feat(scan): a sessao decide a nota do professor e nao substitui caderno completo sem nota em silencio"
```

---

### Task 4: O corpo da nota que o aparelho monta

**Files:**
- Create: `apps/android/src/main/kotlin/com/platos/android/outbox/NotaPendente.kt`
- Modify: `apps/android/src/main/kotlin/com/platos/android/api/ResultadoDto.kt`
- Create: `apps/android/src/test/kotlin/com/platos/android/api/NotaDoAparelhoTest.kt`

**Interfaces:**
- Consumes: `NotaDoProfessor` (`packageHash`, `variantId`, `total`, `maxScore`, `closed`, `outcomes`, `essays`), `GradedResultSubmissionDto`, `EssayGradeDto`.
- Produces:
  - `enum class RotaDoEnvio(val valor: String) { RESULTADO("resultado"), NOTA("nota") }` com `companion fun deValor(texto: String): RotaDoEnvio`
  - `data class NotaPendente(captureId, completaCaptura, organizacao, prova, studentToken: String?, apuradoEm: Long, nota: NotaDoProfessor)`
  - `fun NotaPendente.corpoDoEnvio(): String`

- [ ] **Step 1: Escreva o teste que falha** — `NotaDoAparelhoTest.kt`. O literal é **o mesmo** de `GradedResultDtoTest` (que o servidor lê em `GradedResultContractTest`): o corpo que o aparelho monta é byte a byte o que o servidor aceita.

```kotlin
package com.platos.android.api

import com.platos.android.outbox.NotaPendente
import com.platos.domain.capture.QuestionAnswer
import com.platos.domain.scoring.DiscursivaCorrigida
import com.platos.domain.scoring.NotaDoProfessor
import com.platos.domain.scoring.Pontos
import com.platos.domain.scoring.QuestionOutcome
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * O corpo da nota que o aparelho **monta** (`slice-5c-3-a-nota-no-aparelho`), contra o literal que o servidor le
 * (`GradedResultContractTest`, e `GradedResultDtoTest` deste lado). Os tres sao o mesmo texto: se o aparelho
 * mudar o que envia, ou o servidor o que aceita, um deles cai (`tools/parity/fio.mjs`).
 */
class NotaDoAparelhoTest {

    private val literal = """
        {"capture_id":"cap-nota-1","completes_capture_id":"cap-parcial-1","student_token":"aluno-1",
        "package_hash":"${"a".repeat(64)}","variant_id":"v1","origin":"teacher","path":"image",
        "points":"2.75","max_score":4,"closed":true,"captured_at":"2026-09-17T12:00:00Z",
        "observations":[{"item_id":"q01","answer_kind":"marcada","answer_options":["A"],"worth":1,"earned":1}],
        "essay_grades":[{"item_id":"d1","earned":"1.75"}]}
    """.trimIndent().replace("\n", "")

    private fun nota() = NotaDoProfessor(
        packageHash = "a".repeat(64),
        variantId = "v1",
        objectivePoints = 1,
        objectiveMaxScore = 1,
        maxScore = 4,
        pending = emptyList(),
        outcomes = listOf(QuestionOutcome("q01", QuestionAnswer.Marcada("q01", "A"), worth = 1, earned = 1)),
        essays = listOf(DiscursivaCorrigida("d1", worth = 3, earned = Pontos.parse("1.75"))),
    )

    private fun pendente(token: String? = "aluno-1") = NotaPendente(
        captureId = "cap-nota-1",
        completaCaptura = "cap-parcial-1",
        organizacao = "org-1",
        prova = "prova-r",
        studentToken = token,
        apuradoEm = Instant.parse("2026-09-17T12:00:00Z").toEpochMilli(),
        nota = nota(),
    )

    @Test
    fun `o corpo e o literal que o servidor le`() {
        assertEquals(literal, pendente().corpoDoEnvio())
    }

    @Test
    fun `a folha avulsa manda student_token nulo, e nao omite o campo`() {
        val corpo = pendente(token = null).corpoDoEnvio()

        assertEquals(true, corpo.contains("\"student_token\":null"), corpo)
    }

    @Test
    fun `a pontuacao viaja como texto decimal, nunca como numero`() {
        val corpo = pendente().corpoDoEnvio()

        assertEquals(true, corpo.contains("\"points\":\"2.75\""), corpo)
        assertEquals(true, corpo.contains("\"earned\":\"1.75\""), corpo)
    }

    @Test
    fun `o corpo nao leva imagem, arquivo, nome, turma nem matricula`() {
        val corpo = pendente().corpoDoEnvio()

        for (proibido in listOf("arquivo", ".png", "nome", "turma", "matricula", "imagem")) {
            assertFalse(corpo.contains(proibido), "o corpo da nota traz '$proibido': $corpo")
        }
    }
}
```

(`QuestionAnswer.Marcada(questionId, option)` é o construtor usado nos testes de sessão; se o literal `answer_options` divergir, `answerOptions()` é a fonte e o literal do servidor manda.)

- [ ] **Step 2: Rode e veja falhar**

Run: `./gradlew :apps:android:testDebugUnitTest`
Expected: FAIL (compilação).

- [ ] **Step 3: Implemente**

`outbox/NotaPendente.kt`:

```kotlin
package com.platos.android.outbox

import com.platos.domain.scoring.NotaDoProfessor

/**
 * Qual rota leva um pendente (`slice-5c-3-a-nota-no-aparelho`, design D2). `resultado` e a rota antiga, que leva a
 * nota objetiva completa e a parcial; `nota` e `POST .../results/graded`, a nota do professor.
 */
enum class RotaDoEnvio(val valor: String) {
    RESULTADO("resultado"),
    NOTA("nota");

    companion object {
        fun deValor(texto: String): RotaDoEnvio =
            entries.firstOrNull { it.valor == texto } ?: error("rota desconhecida no outbox: '$texto'")
    }
}

/**
 * A nota do professor que ainda nao subiu.
 *
 * **E um tipo irmao de [ResultadoPendente], e nao um subtipo de `ApuracaoParaEnvio`**: o servidor faz `when`
 * exaustivo sobre esse tipo, e o ADR-0021 adiou o subtipo ate haver consumidor — o consumidor aqui e so o
 * aparelho (design D6). [captureId] e a chave de idempotencia desta nota, cunhada uma vez; [completaCaptura] e o
 * `capture_id` da parcial que ela completa. Sem nome, turma ou matricula, e sem referencia a arquivo: a ausencia e
 * o requisito (I5).
 */
data class NotaPendente(
    val captureId: String,
    val completaCaptura: String,
    val organizacao: String,
    val prova: String,
    /** Nulo na folha avulsa, e nunca string vazia. */
    val studentToken: String?,
    val apuradoEm: Long,
    val nota: NotaDoProfessor,
)
```

`api/ResultadoDto.kt` — acrescente imports (`com.platos.android.outbox.NotaPendente`, `com.platos.domain.transport.EssayGradeDto`, `com.platos.domain.transport.GradedResultSubmissionDto`) e, depois de `corpoDoEnvio` do `ResultadoPendente`:

```kotlin
/**
 * O corpo da nota do professor, congelado na gravacao como o do resultado (`slice-5c-3-a-nota-no-aparelho`).
 * Mesmo `JSON` (`explicitNulls`, `encodeDefaults`). A parte objetiva sai como `observations`; as discursivas, como
 * `essay_grades` com a pontuacao em texto decimal exato (ADR-0021). Origem `teacher` e caminho `image` sao os
 * unicos que o servidor aceita.
 */
fun NotaPendente.corpoDoEnvio(): String = JSON.encodeToString(
    GradedResultSubmissionDto.serializer(),
    GradedResultSubmissionDto(
        captureId = captureId,
        completesCaptureId = completaCaptura,
        studentToken = studentToken,
        packageHash = nota.packageHash,
        variantId = nota.variantId,
        origin = "teacher",
        path = "image",
        points = nota.total.toString(),
        maxScore = nota.maxScore,
        closed = nota.closed,
        capturedAt = Instant.ofEpochMilli(apuradoEm).toString(),
        observations = nota.outcomes.map {
            AnswerObservationDto(
                itemId = it.questionId,
                answerKind = it.answer.answerKind(),
                answerOptions = it.answer.answerOptions(),
                worth = it.worth,
                earned = it.earned,
            )
        },
        essayGrades = nota.essays.map { EssayGradeDto(it.questionId, it.earned.toString()) },
    ),
)
```

- [ ] **Step 4: Rode a suíte do aparelho e a paridade**

Run: `./gradlew :apps:android:testDebugUnitTest` e `node tools/parity/fio.mjs`
Expected: PASS nos dois.

- [ ] **Step 5: Commit**

```bash
git add apps/android/src
git commit -m "feat(outbox): a nota do professor tem tipo e corpo proprios, byte a byte o que o servidor le"
```

---

### Task 5: O outbox guarda a nota (migration 1→2) e a rota `graded` sobe

**Files:**
- Modify: `apps/android/src/main/kotlin/com/platos/android/outbox/ResultadoPendente.kt`
- Modify: `apps/android/src/main/kotlin/com/platos/android/outbox/ResultadosEmRoom.kt`
- Modify: `apps/android/src/main/kotlin/com/platos/android/api/ApiPlatos.kt`
- Modify: `apps/android/src/main/kotlin/com/platos/android/outbox/EnvioDeResultadosWorker.kt` (só o despacho por rota)
- Modify: `apps/android/src/test/kotlin/com/platos/android/outbox/EnvioDeResultadosTest.kt`, `.../scan/EnvioDaParcialNaoEliminaARespostaTest.kt` (os dois `ResultadosPendentes` falsos ganham `guardarNota`)
- Create: `apps/android/src/test/kotlin/com/platos/android/api/ApiPlatosNotaTest.kt`
- Create: `apps/android/src/androidTest/kotlin/com/platos/android/outbox/MigracaoDoOutboxInstrumentedTest.kt`
- Create: `apps/android/src/androidTest/kotlin/com/platos/android/outbox/NotaPendenteInstrumentedTest.kt`

**Interfaces:**
- Consumes: Task 4 (`RotaDoEnvio`, `NotaPendente.corpoDoEnvio`).
- Produces:
  - `EnvelopeDeEnvio(captureId, organizacao, prova, corpo, rota: RotaDoEnvio = RotaDoEnvio.RESULTADO, completaCaptura: String? = null)`
  - `ResultadosPendentes.guardarNota(nota: NotaPendente)`
  - `ApiPlatos.enviarNota(organizacaoId: String, shortId: String, corpo: String): Retorno<Unit>`
  - `ResultadoPendenteEntity.rota`, `.completaCaptura`; `val MIGRACAO_1_2: Migration`; `BaseDoOutbox` `version = 2`

- [ ] **Step 1: Escreva o teste JVM que falha** — `ApiPlatosNotaTest.kt` (mesmo padrão de `ApiPlatosTest`):

```kotlin
package com.platos.android.api

import com.platos.android.net.Retorno
import com.platos.android.net.clienteHttp
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/** A nota do professor sobe por `POST .../results/graded`, e o 4xx/5xx chega como o aparelho ja os classifica. */
class ApiPlatosNotaTest {

    private val pedidos = mutableListOf<HttpRequestData>()

    private fun api(status: Int): ApiPlatos {
        val engine = MockEngine { pedido ->
            pedidos += pedido
            respond("{}", HttpStatusCode.fromValue(status), headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return ApiPlatos(
            http = clienteHttp(engine),
            urlBase = "https://api.platos.example",
            credencial = { "tok-abc" },
            aoExpirarSessao = {},
        )
    }

    @Test
    fun `a nota vai para a rota graded, com a credencial e o corpo congelado`() = runBlocking {
        val retorno = api(200).enviarNota("org-1", "prova-r", """{"capture_id":"cap-nota-1"}""")

        assertInstanceOf(Retorno.Respondeu::class.java, retorno)
        val pedido = pedidos.single()
        assertEquals(HttpMethod.Post, pedido.method)
        assertEquals("https://api.platos.example/organizations/org-1/exams/prova-r/results/graded", pedido.url.toString())
        assertEquals("Bearer tok-abc", pedido.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `o resultado continua indo para a rota de sempre`() = runBlocking {
        api(200).enviarResultado("org-1", "prova-r", "{}")

        assertEquals("https://api.platos.example/organizations/org-1/exams/prova-r/results", pedidos.single().url.toString())
    }

    @Test
    fun `400 e 500 da nota chegam como recusa com o status`() = runBlocking {
        assertEquals(Retorno.Recusou(400), api(400).enviarNota("o", "p", "{}"))
        assertEquals(Retorno.Recusou(500), api(500).enviarNota("o", "p", "{}"))
    }
}
```

(O corpo não é afirmado aqui: ele é congelado e testado em `NotaDoAparelhoTest`; este teste prende a rota e a credencial.)

- [ ] **Step 2: Rode e veja falhar**

Run: `./gradlew :apps:android:testDebugUnitTest`
Expected: FAIL (`enviarNota` inexistente).

- [ ] **Step 3: Implemente o transporte e o contrato da fila**

`ApiPlatos.kt` — troque `enviarResultado` e `pedirEnvio` por:

```kotlin
    suspend fun enviarResultado(organizacaoId: String, shortId: String, corpo: String): Retorno<Unit> =
        enviar(organizacaoId, shortId, corpo, "results")

    /**
     * Envia a nota do professor (`slice-5c-3-a-nota-no-aparelho`): `POST .../results/graded`. Mesma classificacao de
     * retorno do resultado — o 404 de vinculo revogado, o 4xx definitivo e o 5xx transitorio.
     */
    suspend fun enviarNota(organizacaoId: String, shortId: String, corpo: String): Retorno<Unit> =
        enviar(organizacaoId, shortId, corpo, "results/graded")

    private suspend fun enviar(organizacaoId: String, shortId: String, corpo: String, rota: String): Retorno<Unit> =
        when (val retorno = retornoDe<Unit> { pedirEnvio(organizacaoId, shortId, corpo, rota) }) {
            is Retorno.Respondeu -> Retorno.Respondeu(Unit)
            is Retorno.Recusou -> retorno
            is Retorno.SemRede -> Retorno.SemRede
        }

    private suspend fun pedirEnvio(organizacaoId: String, shortId: String, corpo: String, rota: String): HttpResponse =
        autenticado.post("$urlBase/organizations/$organizacaoId/exams/$shortId/$rota") {
            contentType(ContentType.Application.Json)
            setBody(corpo)
        }
```

(Preserve a KDoc longa que estava sobre `enviarResultado` — mova-a para cima da nova `enviarResultado`.)

`ResultadoPendente.kt` — `EnvelopeDeEnvio` ganha, depois de `corpo`:

```kotlin
    /** Qual rota o leva (`slice-5c-3-a-nota-no-aparelho`). O padrao e a rota antiga: o que nao diz nada e resultado. */
    val rota: RotaDoEnvio = RotaDoEnvio.RESULTADO,
    /** Na nota do professor, o `capture_id` da parcial que ela completa; nulo no resultado. */
    val completaCaptura: String? = null,
```

e a interface `ResultadosPendentes` ganha, depois de `guardar`:

```kotlin
    /** Guarda a nota do professor, pendente, na mesma fila. Substitui a de mesmo [NotaPendente.captureId]. */
    fun guardarNota(nota: NotaPendente)
```

`ResultadosEmRoom.kt`:

```kotlin
// na entidade, depois de `corpo`:
    @ColumnInfo(name = "rota", defaultValue = "'resultado'") val rota: String = "resultado",
    @ColumnInfo(name = "completa_captura") val completaCaptura: String? = null,
```

```kotlin
@Database(entities = [ResultadoPendenteEntity::class], version = 2, exportSchema = false)
```

```kotlin
/**
 * A migration 1 -> 2 (`slice-5c-3-a-nota-no-aparelho`, design D2): a linha antiga e um resultado, e a rota
 * nova e a nota. **Sem `fallbackToDestructiveMigration`**: a fila guarda correcao que nao existe em outro lugar.
 */
val MIGRACAO_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE resultado_pendente ADD COLUMN rota TEXT NOT NULL DEFAULT 'resultado'")
        db.execSQL("ALTER TABLE resultado_pendente ADD COLUMN completa_captura TEXT")
    }
}
```

Em `abrir`: `Room.databaseBuilder(...).addMigrations(MIGRACAO_1_2).build()`. Em `ResultadosEmRoom`:

```kotlin
    override fun guardarNota(nota: NotaPendente) {
        dao.inserir(
            ResultadoPendenteEntity(
                captureId = nota.captureId,
                organizacao = nota.organizacao,
                prova = nota.prova,
                apuradoEm = nota.apuradoEm,
                corpo = nota.corpoDoEnvio(),
                rota = RotaDoEnvio.NOTA.valor,
                completaCaptura = nota.completaCaptura,
            ),
        )
    }
```

e `pendentesDa` passa a mapear `rota = RotaDoEnvio.deValor(it.rota), completaCaptura = it.completaCaptura`. Imports: `androidx.room.migration.Migration`, `androidx.sqlite.db.SupportSQLiteDatabase`, `com.platos.android.api.corpoDoEnvio` (já há; o de `NotaPendente` é a mesma função sobrecarregada).

`EnvioDeResultadosWorker.kt` — em `passadaDeEnvio`, troque a chamada por:

```kotlin
        val retorno = when (envelope.rota) {
            RotaDoEnvio.RESULTADO -> api.enviarResultado(envelope.organizacao, envelope.prova, envelope.corpo)
            RotaDoEnvio.NOTA -> api.enviarNota(envelope.organizacao, envelope.prova, envelope.corpo)
        }
```

Nos dois fakes de `ResultadosPendentes` em teste, acrescente `override fun guardarNota(nota: NotaPendente) = error("nao usado neste teste")`. Rode `grep -rn "ResultadosPendentes {" apps/android/src` e cubra todo implementador que aparecer.

- [ ] **Step 4: Escreva os testes instrumentados** (rodam com o emulador liberado pelo Leon; escritos e compilados aqui)

`MigracaoDoOutboxInstrumentedTest.kt` — cria o banco **v1 à mão** (sem schema exportado não há `MigrationTestHelper`), abre pelo Room e lê:

```kotlin
package com.platos.android.outbox

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A migration 1 -> 2 nao perde pendente: a linha antiga vira `resultado`, e a coluna nova nasce nula. */
@RunWith(AndroidJUnit4::class)
class MigracaoDoOutboxInstrumentedTest {

    private val contexto = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun banco1() {
        ResultadosEmRoom.reiniciarParaTeste()
        contexto.deleteDatabase("outbox.db")
        SQLiteDatabase.openOrCreateDatabase(contexto.getDatabasePath("outbox.db"), null).use { db ->
            db.execSQL(
                "CREATE TABLE resultado_pendente (capture_id TEXT NOT NULL, organizacao TEXT NOT NULL, " +
                    "prova TEXT NOT NULL, apurado_em INTEGER NOT NULL, corpo TEXT NOT NULL, PRIMARY KEY(capture_id))",
            )
            db.execSQL("INSERT INTO resultado_pendente VALUES ('cap-antiga','org-1','prova-r',1,'{\"antigo\":true}')")
            db.version = 1
        }
    }

    @After
    fun limpa() {
        ResultadosEmRoom.reiniciarParaTeste()
        contexto.deleteDatabase("outbox.db")
    }

    @Test
    fun `a linha antiga sobrevive e vira resultado`() {
        val guarda = ResultadosEmRoom(ResultadosEmRoom.abrir(contexto).pendentes())

        val pendentes = guarda.pendentesDa("org-1")

        assertEquals(1, pendentes.size)
        assertEquals("cap-antiga", pendentes.single().captureId)
        assertEquals(RotaDoEnvio.RESULTADO, pendentes.single().rota)
        assertEquals("{\"antigo\":true}", pendentes.single().corpo)
        assertEquals(null, pendentes.single().completaCaptura)
    }
}
```

`NotaPendenteInstrumentedTest.kt` — grava uma `NotaPendente` pelo `ResultadosEmRoom`, reabre o banco (`reiniciarParaTeste()` + `abrir`) e confere que `pendentesDa` devolve `rota = NOTA`, `completaCaptura = "cap-parcial-1"` e o corpo igual a `nota.corpoDoEnvio()`; `quantosPendentes("org-1") == 1` logo depois de `guardarNota` (é o número que a tela de saída informa: a nota conta entre os não enviados), e depois `apagarConfirmado` e `quantosPendentes == 0`. Acrescente também a `ApagamentoLocalInstrumentedTest` um cenário com uma linha `nota`: sair e a revogação do vínculo **não** a apagam (a tabela e o verbo de apagar são os mesmos, mas o requisito é da nota). Use a mesma `nota()` do `NotaDoAparelhoTest` (copie o corpo da função, sem compartilhar fixture entre `test` e `androidTest`).

- [ ] **Step 5: Rode a suíte JVM e compile os instrumentados**

Run: `./gradlew :apps:android:testDebugUnitTest :apps:android:compileDebugAndroidTestKotlin`
Expected: PASS. **Os dois testes instrumentados só são executados com o emulador que o Leon liberar**; até lá, estão compilados e **não verificados** (registre na cobertura, Task 9). Se a execução acusar `Migration didn't properly handle` por causa de `defaultValue`, o `'resultado'` com aspas simples é o primeiro suspeito: ajuste a aspa na entidade e confirme com o teste.

- [ ] **Step 6: Commit**

```bash
git add apps/android/src
git commit -m "feat(outbox): a nota do professor entra na fila (migration 1->2) e sobe pela rota graded"
```

---

### Task 6: A confirmação da nota elimina as imagens do caderno que ela completa

**Files:**
- Create: `apps/android/src/main/kotlin/com/platos/android/outbox/EliminarAoConfirmar.kt`
- Modify: `apps/android/src/main/kotlin/com/platos/android/outbox/EnvioDeResultados.kt`
- Modify: `apps/android/src/main/kotlin/com/platos/android/outbox/EnvioDeResultadosWorker.kt`
- Create: `apps/android/src/test/kotlin/com/platos/android/outbox/EliminarAoConfirmarTest.kt`
- Modify: `apps/android/src/test/kotlin/com/platos/android/outbox/EnvioDeResultadosTest.kt` (cenários do gancho)

**Interfaces:**
- Consumes: Task 2 (`capturaDaParcial`, `corrigidoSemRespostas`), Task 5 (`EnvelopeDeEnvio.rota/completaCaptura`), `CadernosGuardados.ler/guardar`, `RespostasGuardadas.eliminar`.
- Produces:
  - `data class EliminacaoAoConfirmar(val eliminados: Int, val naoEliminados: Int)`
  - `fun eliminarAoConfirmar(envelope: EnvelopeDeEnvio, cadernos: CadernosGuardados, respostas: RespostasGuardadas): EliminacaoAoConfirmar`
  - `EnvioDeResultados(pendentes, enviar, aoConfirmar: suspend (EnvelopeDeEnvio) -> Unit = {})`
  - `ResumoDoEnvio.falhasAoConfirmar: Int = 0` (fora de `pendentesRestantes`)

- [ ] **Step 1: Escreva os testes que falham** — `EliminarAoConfirmarTest.kt`:

```kotlin
package com.platos.android.outbox

import com.platos.android.scan.Caderno
import com.platos.android.scan.CadernosGuardados
import com.platos.android.scan.EstadoDaRegiao
import com.platos.android.scan.RegiaoDoCaderno
import com.platos.android.scan.RespostaDoQuadro
import com.platos.android.scan.RespostaGuardada
import com.platos.android.scan.RespostasGuardadas
import java.io.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * O que a confirmacao da nota elimina (`slice-5c-3-a-nota-no-aparelho`): so as imagens do caderno que a **nota
 * completa**, achado por `capturaDaParcial`, e nunca por a nota ter ou nao marcado o caderno como corrigido.
 */
class EliminarAoConfirmarTest {

    private class Cadernos(var guardado: Caderno?) : CadernosGuardados {
        override fun guardar(organizacao: String, examId: String, caderno: Caderno) { guardado = caderno }
        override fun ler(organizacao: String, examId: String): Caderno? = guardado
        override fun todos(): List<Caderno> = listOfNotNull(guardado)
    }

    private class Arquivos(vararg nomes: String, val quebra: Set<String> = emptySet()) : RespostasGuardadas {
        val no = nomes.toMutableSet()
        override fun gravar(png: ByteArray, capturadaEm: Long, desvioSinalizado: Boolean, foraPpm: Int): RespostaDoQuadro =
            error("nao usado")
        override fun ler(arquivo: String): ByteArray? = null
        override fun existe(arquivo: String) = arquivo in no
        override fun listar() = no.toList()
        override fun eliminar(arquivo: String) {
            if (arquivo in quebra) throw IOException("nao deu")
            no.remove(arquivo)
        }
    }

    private fun caderno(captura: String? = "parcial-1", corrigido: Boolean = false) = Caderno(
        aluno = "tok-a",
        regioes = listOf(
            RegiaoDoCaderno(0, true, "Gabarito", EstadoDaRegiao.Capturada),
            RegiaoDoCaderno(1, false, "3", EstadoDaRegiao.Capturada, RespostaGuardada("r1.png", 0L, false, 0), "d1"),
            RegiaoDoCaderno(2, false, "4", EstadoDaRegiao.Capturada, RespostaGuardada("r2.png", 0L, false, 0), "d2"),
        ),
        parcial = null,
        entregue = true,
        capturaDaParcial = captura,
        corrigido = corrigido,
    )

    private fun envelope(rota: RotaDoEnvio = RotaDoEnvio.NOTA, completa: String? = "parcial-1") =
        EnvelopeDeEnvio("cap-nota-1", "org-1", "prova-r", "{}", rota, completa)

    @Test
    fun `a nota confirmada elimina as imagens do caderno que ela completa`() {
        val cadernos = Cadernos(caderno(corrigido = true))
        val arquivos = Arquivos("r1.png", "r2.png")

        val r = eliminarAoConfirmar(envelope(), cadernos, arquivos)

        assertEquals(EliminacaoAoConfirmar(2, 0), r)
        assertTrue(arquivos.no.isEmpty())
        assertTrue(cadernos.guardado!!.regioes.all { it.resposta == null })
        assertEquals(3, cadernos.guardado!!.capturadas, "eliminar nao devolve regiao a nao vista")
    }

    @Test
    fun `a queda entre gravar a nota e gravar o caderno nao impede a eliminacao`() {
        val cadernos = Cadernos(caderno(corrigido = false))
        val arquivos = Arquivos("r1.png", "r2.png")

        eliminarAoConfirmar(envelope(), cadernos, arquivos)

        assertTrue(arquivos.no.isEmpty())
        assertTrue(cadernos.guardado!!.corrigido, "o gancho fecha o que a queda deixou aberto")
    }

    @Test
    fun `o resultado e a parcial nao eliminam nada`() {
        val cadernos = Cadernos(caderno())
        val arquivos = Arquivos("r1.png", "r2.png")

        val r = eliminarAoConfirmar(envelope(rota = RotaDoEnvio.RESULTADO, completa = null), cadernos, arquivos)

        assertEquals(EliminacaoAoConfirmar(0, 0), r)
        assertEquals(setOf("r1.png", "r2.png"), arquivos.no)
    }

    @Test
    fun `a confirmacao de uma nota antiga nao toca o caderno de outra captura`() {
        val cadernos = Cadernos(caderno(captura = "parcial-9"))
        val arquivos = Arquivos("r1.png", "r2.png")

        val r = eliminarAoConfirmar(envelope(completa = "parcial-1"), cadernos, arquivos)

        assertEquals(EliminacaoAoConfirmar(0, 0), r)
        assertEquals(setOf("r1.png", "r2.png"), arquivos.no)
        assertEquals("parcial-9", cadernos.guardado!!.capturaDaParcial)
    }

    @Test
    fun `sem caderno guardado nao ha o que eliminar`() {
        assertEquals(EliminacaoAoConfirmar(0, 0), eliminarAoConfirmar(envelope(), Cadernos(null), Arquivos("r1.png")))
    }

    @Test
    fun `arquivo que falha nao impede os outros, e e contado`() {
        val cadernos = Cadernos(caderno())
        val arquivos = Arquivos("r1.png", "r2.png", quebra = setOf("r1.png"))

        val r = eliminarAoConfirmar(envelope(), cadernos, arquivos)

        assertEquals(EliminacaoAoConfirmar(1, 1), r)
        assertEquals(setOf("r1.png"), arquivos.no, "o que falhou fica para a varredura, que o acha sem referencia")
        assertFalse(cadernos.guardado!!.regioes.any { it.resposta != null }, "o caderno nao referencia mais nenhum")
    }
}
```

Em `EnvioDeResultadosTest`, acrescente os cenários do gancho (dentro da classe):

```kotlin
    @Test
    fun `o gancho roda depois da confirmacao e so nela`() = runBlocking {
        val guarda = GuardaEmMemoria(listOf(envelope("cap-1"), envelope("cap-2")))
        val vistos = mutableListOf<Pair<String, Int>>()
        val envio = EnvioDeResultados(
            guarda,
            enviar = { if (it.captureId == "cap-1") Retorno.Respondeu(Unit) else Retorno.Recusou(400) },
            aoConfirmar = { vistos += it.captureId to guarda.linhas.size },
        )

        envio.enviarPendentesDa(ORG)

        assertEquals(listOf("cap-1" to 1), vistos, "o gancho rodou depois de o pendente sair da fila")
    }

    @Test
    fun `falha do gancho nao desfaz a confirmacao e e contada`() = runBlocking {
        val guarda = GuardaEmMemoria(listOf(envelope("cap-1")))
        val envio = EnvioDeResultados(guarda, { Retorno.Respondeu(Unit) }, aoConfirmar = { error("disco") })

        val resumo = envio.enviarPendentesDa(ORG)

        assertEquals(1, resumo.confirmados)
        assertEquals(1, resumo.falhasAoConfirmar)
        assertTrue(guarda.linhas.isEmpty(), "a confirmacao do servidor nao se desfaz")
    }

    @Test
    fun `nota nao confirmada nao chama o gancho`() = runBlocking {
        val guarda = GuardaEmMemoria(listOf(envelope("cap-1")))
        var chamado = false
        val envio = EnvioDeResultados(guarda, { Retorno.SemRede }, aoConfirmar = { chamado = true })

        envio.enviarPendentesDa(ORG)

        assertFalse(chamado)
    }
```

(importe `assertFalse`).

- [ ] **Step 2: Rode e veja falhar**

Run: `./gradlew :apps:android:testDebugUnitTest`
Expected: FAIL (compilação).

- [ ] **Step 3: Implemente**

`EliminarAoConfirmar.kt`:

```kotlin
package com.platos.android.outbox

import com.platos.android.scan.CadernosGuardados
import com.platos.android.scan.RespostasGuardadas
import com.platos.android.scan.corrigidoSemRespostas

/** O que a eliminacao depois da confirmacao fez: quantos arquivos saíram e quantos falharam. */
data class EliminacaoAoConfirmar(val eliminados: Int, val naoEliminados: Int)

/**
 * Elimina as imagens do caderno que a nota **completa**, depois de o servidor confirmar a nota
 * (`slice-5c-3-a-nota-no-aparelho`, design D3): o gatilho "apos a sincronizacao" da classe H.
 *
 * **Funcao pura sobre as guardas**, sem `Context` nem `WorkManager`: o worker a liga, e a decisao se exercita na JVM.
 *
 * - So a rota `nota` elimina. O resultado e a parcial **nao** (spec `scan-session`: enviar a parcial nao elimina).
 * - O caderno e achado por `(organizacao, prova)` e **so e tocado se `capturaDaParcial` for a que a nota completa**.
 *   Se outro aluno ja o substituiu, as imagens antigas ficaram sem referencia e a varredura as elimina.
 * - A chave e a captura, e **nao** `corrigido`: a queda entre gravar a nota e gravar o caderno deixa a nota pendente
 *   com o caderno ainda nao corrigido, e a confirmacao precisa eliminar mesmo assim. O caderno e regravado como
 *   `corrigidoSemRespostas`.
 * - Arquivo que falha nao interrompe os outros e e contado; como o caderno regravado nao o referencia mais, a varredura
 *   o elimina na proxima passada.
 */
fun eliminarAoConfirmar(
    envelope: EnvelopeDeEnvio,
    cadernos: CadernosGuardados,
    respostas: RespostasGuardadas,
): EliminacaoAoConfirmar {
    val nada = EliminacaoAoConfirmar(0, 0)
    if (envelope.rota != RotaDoEnvio.NOTA) return nada
    val caderno = cadernos.ler(envelope.organizacao, envelope.prova) ?: return nada
    val captura = envelope.completaCaptura ?: return nada
    if (caderno.capturaDaParcial != captura) return nada

    var eliminados = 0
    var naoEliminados = 0
    for (arquivo in caderno.regioes.mapNotNull { it.resposta?.arquivo }) {
        try {
            respostas.eliminar(arquivo)
            eliminados++
        } catch (e: Exception) {
            naoEliminados++
        }
    }
    cadernos.guardar(envelope.organizacao, envelope.prova, caderno.corrigidoSemRespostas())
    return EliminacaoAoConfirmar(eliminados, naoEliminados)
}
```

`EnvioDeResultados.kt`:
- `ResumoDoEnvio` ganha `val falhasAoConfirmar: Int = 0` (não entra em `pendentesRestantes`).
- Construtor: `private val aoConfirmar: suspend (EnvelopeDeEnvio) -> Unit = {},`.
- Em `enviarPendentesDa`, `var falhasAoConfirmar = 0`; no ramo `Retorno.Respondeu`, **depois** de `confirmados++`:

```kotlin
                    // O gancho roda depois da confirmacao e nunca a desfaz: falhar aqui so deixa trabalho para a
                    // varredura (que acha as imagens sem referencia).
                    try {
                        aoConfirmar(envelope)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        falhasAoConfirmar++
                    }
```

  e `falhasAoConfirmar = falhasAoConfirmar` no `ResumoDoEnvio` devolvido.

`EnvioDeResultadosWorker.kt` — em `passadaDeEnvio`, abra as guardas e ligue o gancho:

```kotlin
    val cadernos = CadernosEmRoom(CadernosEmRoom.abrir(context).cadernos())
    val respostas = RespostasEmArquivo(RespostasEmArquivo.diretorioDe(context.filesDir))
    var eliminadas = 0
    var naoEliminadas = 0
    val envio = EnvioDeResultados(
        pendentes,
        enviar = { envelope -> /* o when por rota da Task 5 */ },
        aoConfirmar = { envelope ->
            val feito = eliminarAoConfirmar(envelope, cadernos, respostas)
            eliminadas += feito.eliminados
            naoEliminadas += feito.naoEliminados
        },
    )
```

`PassadaDeEnvio` ganha `val eliminadas: Int = 0, val naoEliminadas: Int = 0`; `diagnostico(...)` ganha `eliminadas`/`naoEliminadas` (`putInt("eliminadas", …)`, `putInt("nao_eliminadas", …)`); `doWork` os passa. Imports: `CadernosEmRoom`, `RespostasEmArquivo`.

- [ ] **Step 4: Rode a suíte do aparelho**

Run: `./gradlew :apps:android:testDebugUnitTest`
Expected: PASS, incluindo `EnvioDaParcialNaoEliminaARespostaTest` **sem mudança de asserção**.

- [ ] **Step 5: Commit**

```bash
git add apps/android/src
git commit -m "feat(outbox): confirmar a nota elimina as imagens do caderno que ela completa"
```

---

### Task 7: A tela de nota, o painel do outro aluno e a fiação da `ScanActivity`

**Files:**
- Create: `apps/android/src/main/kotlin/com/platos/android/scan/EntradaDaNota.kt`
- Create: `apps/android/src/main/kotlin/com/platos/android/scan/NotaTela.kt`
- Modify: `apps/android/src/main/kotlin/com/platos/android/scan/ScanScreen.kt`
- Modify: `apps/android/src/main/kotlin/com/platos/android/scan/ScanActivity.kt` (CRLF)
- Create: `apps/android/src/test/kotlin/com/platos/android/scan/EntradaDaNotaTest.kt`

**Interfaces:**
- Consumes: Task 1 (`lerPontos`), Task 2 (`aguardaNota`, `questionId`), Task 3 (`darNota`, `confirmarCorrigido`, `falhouAGravacao`, `descartarESeguir`, `NotaPorDarDeOutroAluno`), Task 5 (`guardarNota`).
- Produces:
  - `internal data class LinhaDaNota(questionId: String, rotulo: String, worth: Int, resposta: RespostaGuardada?)`
  - `internal fun linhasDaNota(caderno: Caderno, parcial: PartialScore): List<LinhaDaNota>`
  - `internal sealed interface EntradaDaNota { Valida(pontuacoes, total: Pontos); Invalida(motivo) }`
  - `internal fun interpretarEntrada(linhas, textos: Map<String, String>, objetivos: Int): EntradaDaNota`
  - `@Composable fun NotaTela(caderno, parcial, respostas, erro: String?, onGravar: (List<PontuacaoDada>) -> Unit, onVoltar: () -> Unit)`

- [ ] **Step 1: Escreva o teste que falha** — `EntradaDaNotaTest.kt`:

```kotlin
package com.platos.android.scan

import com.platos.domain.scoring.PontuacaoDada
import com.platos.domain.scoring.Pontos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** O que o professor digita vira pontuacao, ou um motivo que nomeia a questao (`slice-5c-3`, spec `scan-session`). */
class EntradaDaNotaTest {

    private val linhas = listOf(
        LinhaDaNota("d1", "3", worth = 3, resposta = null),
        LinhaDaNota("d2", "4", worth = 4, resposta = null),
    )

    private fun invalida(e: EntradaDaNota) = (e as? EntradaDaNota.Invalida ?: throw AssertionError("veio $e")).motivo

    @Test
    fun `textos validos viram pontuacoes e o total exato`() {
        val e = interpretarEntrada(linhas, mapOf("d1" to "2.5", "d2" to "3.75"), objetivos = 3) as EntradaDaNota.Valida

        assertEquals(listOf(PontuacaoDada("d1", Pontos.parse("2.5")), PontuacaoDada("d2", Pontos.parse("3.75"))), e.pontuacoes)
        assertEquals("9.25", e.total.toString())
    }

    @Test
    fun `virgula decimal e recusada dizendo ponto decimal, nunca lida como 15`() {
        val motivo = invalida(interpretarEntrada(linhas, mapOf("d1" to "1,5", "d2" to "1"), 3))

        assertTrue(motivo.contains("questao 3"), motivo)
        assertTrue(motivo.contains("ponto decimal"), motivo)
    }

    @Test
    fun `campo em branco diz qual questao falta`() {
        val motivo = invalida(interpretarEntrada(linhas, mapOf("d1" to "2", "d2" to "  "), 3))

        assertTrue(motivo.contains("questao 4"), motivo)
    }

    @Test
    fun `tres casas, negativo e texto nao numerico sao recusados nomeando a questao`() {
        for (texto in listOf("1.333", "-1", "abc")) {
            val motivo = invalida(interpretarEntrada(linhas, mapOf("d1" to texto, "d2" to "1"), 3))
            assertTrue(motivo.contains("questao 3"), "$texto: $motivo")
        }
    }

    @Test
    fun `acima do valor da questao e recusado com o maximo`() {
        val motivo = invalida(interpretarEntrada(linhas, mapOf("d1" to "3.01", "d2" to "1"), 3))

        assertTrue(motivo.contains("questao 3") && motivo.contains("3"), motivo)
    }

    @Test
    fun `zero em todas e maximo em todas sao aceitos`() {
        assertEquals("3.00", (interpretarEntrada(linhas, mapOf("d1" to "0", "d2" to "0.00"), 3) as EntradaDaNota.Valida).total.toString())
        assertEquals("10.00", (interpretarEntrada(linhas, mapOf("d1" to "3", "d2" to "4.00"), 3) as EntradaDaNota.Valida).total.toString())
    }
}
```

- [ ] **Step 2: Rode e veja falhar**

Run: `./gradlew :apps:android:testDebugUnitTest`
Expected: FAIL (compilação).

- [ ] **Step 3: Implemente `EntradaDaNota.kt`**

```kotlin
package com.platos.android.scan

import com.platos.domain.scoring.PartialScore
import com.platos.domain.scoring.PontuacaoDada
import com.platos.domain.scoring.Pontos
import com.platos.domain.scoring.lerPontos

/** Uma discursiva na tela de nota: a questao, o numero impresso, o que vale e a resposta guardada. */
internal data class LinhaDaNota(
    val questionId: String,
    val rotulo: String,
    val worth: Int,
    val resposta: RespostaGuardada?,
)

/**
 * As linhas da tela de nota, na ordem do caderno: cada regiao discursiva casa com o `AwaitingEssay` da parcial
 * pela questao que o mapa declara (`RegiaoDoCaderno.questionId`). Regiao cuja questao a parcial nao aguarda fica de
 * fora — `CorrecaoDoProfessor.completar` recusaria a nota, e a tela nao a monta.
 */
internal fun linhasDaNota(caderno: Caderno, parcial: PartialScore): List<LinhaDaNota> {
    val valor = parcial.awaiting.associate { it.questionId to it.points }
    return caderno.regioes.filter { !it.gabarito }.mapNotNull { regiao ->
        val questao = regiao.questionId ?: return@mapNotNull null
        val worth = valor[questao] ?: return@mapNotNull null
        LinhaDaNota(questao, regiao.rotulo, worth, regiao.resposta)
    }
}

/** O que o professor digitou, lido: as pontuacoes e o total exato, ou o motivo que serve para a tela. */
internal sealed interface EntradaDaNota {
    data class Valida(val pontuacoes: List<PontuacaoDada>, val total: Pontos) : EntradaDaNota
    data class Invalida(val motivo: String) : EntradaDaNota
}

/**
 * Le os textos digitados com o **mesmo** `lerPontos` que o servidor usa, e nomeia a questao pelo **numero impresso**.
 * `1,5` (virgula, como o pt-BR digita) e recusado pela mensagem do dominio — que diz "ponto decimal" — e nunca lido
 * como `15`. [objetivos] e a parte objetiva da parcial, para o total.
 */
internal fun interpretarEntrada(
    linhas: List<LinhaDaNota>,
    textos: Map<String, String>,
    objetivos: Int,
): EntradaDaNota {
    val dadas = mutableListOf<PontuacaoDada>()
    var total = Pontos.inteiros(objetivos)
    for (linha in linhas) {
        val de = "a questao ${linha.rotulo}"
        val texto = textos[linha.questionId]?.trim().orEmpty()
        if (texto.isEmpty()) return EntradaDaNota.Invalida("$de ainda nao tem pontuacao")
        val pontos = try {
            lerPontos(texto, de)
        } catch (e: IllegalArgumentException) {
            return EntradaDaNota.Invalida(e.message ?: "$de: pontuacao invalida")
        }
        if (pontos > Pontos.inteiros(linha.worth)) {
            return EntradaDaNota.Invalida("$de vale no maximo ${linha.worth}")
        }
        dadas += PontuacaoDada(linha.questionId, pontos)
        total += pontos
    }
    return EntradaDaNota.Valida(dadas, total)
}
```

- [ ] **Step 4: Implemente `NotaTela.kt`** (sem teste JVM de Compose; a lógica está em `EntradaDaNota`)

```kotlin
package com.platos.android.scan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.platos.domain.scoring.PartialScore
import com.platos.domain.scoring.PontuacaoDada
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A tela de nota (`slice-5c-3-a-nota-no-aparelho`, spec `scan-session`): a imagem de cada discursiva com o numero
 * impresso da questao, o aviso de desvio quando sinalizado, o que ela vale e um campo decimal. O total aparece antes
 * de gravar, e gravar pede confirmacao.
 *
 * Toda a regra de leitura esta em [interpretarEntrada]; a tela so a desenha. [erro] e a recusa que a sessao ou a
 * gravacao devolveram. A tela nao altera a imagem e nao a envia.
 */
@Composable
fun NotaTela(
    caderno: Caderno,
    parcial: PartialScore,
    respostas: RespostasGuardadas,
    erro: String?,
    onGravar: (List<PontuacaoDada>) -> Unit,
    onVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onVoltar)

    val linhas = remember(caderno, parcial) { linhasDaNota(caderno, parcial) }
    val textos = remember { mutableStateMapOf<String, String>() }
    var confirmando by remember { mutableStateOf(false) }
    val entrada = interpretarEntrada(linhas, textos, parcial.objectivePoints)

    Column(
        modifier = modifier.fillMaxSize().background(Color.Black).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Dar a nota", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        for (linha in linhas) {
            Text("Questao ${linha.rotulo} (vale ${linha.worth})", color = Color.White, fontSize = 18.sp)
            val resposta = linha.resposta
            if (resposta != null) {
                ImagemDaResposta(resposta.arquivo, respostas)
                if (resposta.desvioSinalizado) {
                    Text(ScanState.ProvaComDiscursiva.AVISO_DE_DESVIO, color = Color(0xFFF9A825), fontSize = 14.sp)
                }
            } else {
                Text("A resposta nao esta mais neste aparelho.", color = Color.White, fontSize = 14.sp)
            }
            OutlinedTextField(
                value = textos[linha.questionId].orEmpty(),
                onValueChange = { textos[linha.questionId] = it; confirmando = false },
                label = { Text("Pontuacao de 0 a ${linha.worth}, com ponto decimal") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        when (entrada) {
            is EntradaDaNota.Invalida -> Text(entrada.motivo, color = Color.White, fontSize = 14.sp)
            is EntradaDaNota.Valida ->
                Text("Total: ${entrada.total} de ${parcial.maxScore}", color = Color.White, fontSize = 18.sp)
        }
        if (erro != null) Text(erro, color = Color(0xFFF9A825), fontSize = 14.sp)

        if (entrada is EntradaDaNota.Valida && confirmando) {
            Text("Confirmar a nota? Depois de gravada, ela nao se altera neste aparelho.", color = Color.White, fontSize = 16.sp)
            Button(onClick = { onGravar(entrada.pontuacoes) }) { Text("Confirmar e gravar") }
        } else {
            Button(onClick = { confirmando = true }, enabled = entrada is EntradaDaNota.Valida) { Text("Conferir o total") }
        }
        Button(onClick = onVoltar) { Text("Voltar") }
    }
}

@Composable
private fun ImagemDaResposta(arquivo: String, respostas: RespostasGuardadas) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, arquivo) {
        value = withContext(Dispatchers.IO) { carregarResposta(respostas, arquivo) }
    }
    val pronto = bitmap
    if (pronto == null) {
        Text("Abrindo a resposta…", color = Color.White, fontSize = 14.sp)
    } else {
        Image(bitmap = pronto.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth())
    }
}
```

(Confira o `import androidx.compose.foundation.layout.*` que `RespostaTela.kt` usa para `Image`; mantenha os mesmos imports dela, e remova os não usados.)

- [ ] **Step 5: `ScanScreen.kt`** — parâmetros novos com default, no fim da lista: `notaAberta: Boolean = false, erroDaNota: String? = null, onDarNota: () -> Unit = {}, onGravarNota: (List<PontuacaoDada>) -> Unit = {}, onFecharNota: () -> Unit = {}, onDescartarESeguir: () -> Unit = {}`.

- No ramo `ProvaComDiscursiva`, depois de `CadernoDaFolha(...)`:

```kotlin
                if (state.caderno.aguardaNota) {
                    Button(onClick = onDarNota) { Text("Dar a nota") }
                }
                if (state.caderno.corrigido) {
                    Text("Caderno corrigido: a nota foi gravada e sera enviada.", fontSize = 16.sp)
                }
```

- Troque o ramo temporário da Task 3 por:

```kotlin
            is ScanState.NotaPorDarDeOutroAluno -> Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xEE000000)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    Text("O caderno do aluno anterior aguarda a nota", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "A folha de outro aluno apareceu. Descartar o caderno perde as respostas capturadas; " +
                            "nada sera gravado nem enviado.",
                        fontSize = 16.sp,
                    )
                    Button(onClick = onDarNota) { Text("Dar a nota") }
                    Button(onClick = onDescartarESeguir) { Text("Descartar e seguir") }
                }
            }
```

- Depois do bloco da `RespostaTela`, a sobreposição da nota:

```kotlin
        val cadernoDaNota = when (state) {
            is ScanState.ProvaComDiscursiva -> state.caderno
            is ScanState.NotaPorDarDeOutroAluno -> state.caderno
            else -> null
        }
        val parcialDaNota = (cadernoDaNota?.parcial as? PartialScoringOutcome.Scored)?.partial
        if (notaAberta && cadernoDaNota != null && parcialDaNota != null && respostas != null) {
            NotaTela(
                caderno = cadernoDaNota,
                parcial = parcialDaNota,
                respostas = respostas,
                erro = erroDaNota,
                onGravar = onGravarNota,
                onVoltar = onFecharNota,
            )
        }
```

- [ ] **Step 6: `ScanActivity.kt`** (CRLF; edições pequenas com `Edit`)

Estados, junto de `respostaAberta`:

```kotlin
    /** A tela de nota aberta, e a recusa que a sessao ou a gravacao devolveram (`slice-5c-3-a-nota-no-aparelho`). */
    private var notaAberta by mutableStateOf(false)
    private var erroDaNota by mutableStateOf<String?>(null)
```

Em `montar`, no `ScanScreen(...)`, acrescente:

```kotlin
                notaAberta = notaAberta,
                erroDaNota = erroDaNota,
                onDarNota = { erroDaNota = null; notaAberta = true },
                onGravarNota = ::darNota,
                onFecharNota = { notaAberta = false; erroDaNota = null },
                onDescartarESeguir = ::descartarESeguir,
```

Métodos novos, antes de `gravar`:

```kotlin
    /**
     * O professor confirmou o total: a sessao decide, e a gravacao acontece **em sequencia, nota primeiro, caderno
     * depois**, fora do fio principal e sem cancelamento (dois bancos Room, sem transacao entre eles; a ordem torna a
     * queda entre os dois inofensiva — design, desvio 2). So depois da gravacao a sessao passa o caderno a
     * `corrigido`. Falha na gravacao deixa a tela aberta, com o motivo, e nada vai ao caderno.
     */
    internal fun darNota(pontuacoes: List<PontuacaoDada>) {
        when (val r = session.darNota(pontuacoes)) {
            is ResultadoDaNota.Recusada -> erroDaNota = r.motivo
            is ResultadoDaNota.Corrigida -> {
                val pendente = NotaPendente(
                    captureId = UUID.randomUUID().toString(),
                    completaCaptura = r.completaCaptura,
                    organizacao = organizacao,
                    prova = prova,
                    studentToken = r.aluno.ifEmpty { null },
                    apuradoEm = System.currentTimeMillis(),
                    nota = r.nota,
                )
                lifecycleScope.launch {
                    val gravou = withContext(Dispatchers.IO + NonCancellable) {
                        try {
                            pendentes.guardarNota(pendente)
                            cadernos.guardar(organizacao, examPackage.meta.examId, r.cadernoCorrigido)
                            EnvioDeResultadosWorker.agendar(applicationContext, organizacao)
                            true
                        } catch (e: Exception) {
                            false
                        }
                    }
                    if (gravou) {
                        session.confirmarCorrigido()
                        state = session.state
                        cadernoVisivel = session.cadernoAtual
                        notaAberta = false
                        erroDaNota = null
                    } else {
                        session.falhouAGravacao()
                        erroDaNota = "Nao foi possivel gravar a nota neste aparelho. Tente de novo."
                    }
                }
            }
        }
    }

    /** Descarta o caderno completo sem nota (spec: ato explicito do professor) e elimina as imagens na hora. */
    internal fun descartarESeguir() {
        val arquivos = session.descartarESeguir()
        state = session.state
        cadernoVisivel = session.cadernoAtual
        notaAberta = false
        lifecycleScope.launch(Dispatchers.IO + NonCancellable) {
            for (arquivo in arquivos) {
                try {
                    respostas.eliminar(arquivo)
                } catch (e: Exception) {
                    // Fica para a varredura: nenhum caderno o referencia mais.
                }
            }
        }
    }
```

Imports: `NotaPendente`, `PontuacaoDada`, `withContext`.

- [ ] **Step 7: Rode a suíte e compile**

Run: `./gradlew :apps:android:testDebugUnitTest :apps:android:assembleDebug`
Expected: PASS e `BUILD SUCCESSFUL`. `git diff --stat apps/android/src/main/kotlin/com/platos/android/scan/ScanActivity.kt` deve mostrar só as linhas acrescentadas (CRLF preservado).

- [ ] **Step 8: Commit**

```bash
git add apps/android/src
git commit -m "feat(scan): a tela de nota, o painel do outro aluno e a gravacao da nota no aparelho"
```

---

### Task 8: O teto de 30 dias roda em segundo plano, com trava

**Files:**
- Modify: `apps/android/src/main/kotlin/com/platos/android/scan/RetencaoDaResposta.kt`
- Create: `apps/android/src/main/kotlin/com/platos/android/scan/EscaneamentoAberto.kt`
- Modify: `apps/android/src/main/kotlin/com/platos/android/scan/VarreduraDoAparelho.kt`
- Create: `apps/android/src/main/kotlin/com/platos/android/scan/VarreduraPeriodicaWorker.kt`
- Modify: `apps/android/src/main/kotlin/com/platos/android/session/SessaoActivity.kt`
- Modify: `apps/android/src/main/kotlin/com/platos/android/scan/ScanActivity.kt` (CRLF; `onStart`/`onStop`)
- Modify: `apps/android/src/test/kotlin/com/platos/android/scan/RetencaoDaRespostaTest.kt`
- Create: `apps/android/src/test/kotlin/com/platos/android/scan/EscaneamentoAbertoTest.kt`

**Interfaces:**
- Consumes: `RetencaoDaResposta`, `varrerRespostas(respostas, cadernos, agora)`.
- Produces:
  - `RetencaoDaResposta.arquivosAEliminar(noDisco, referenciadas, agora, escaneamentoAberto: Boolean = false)`
  - `varrerRespostas(respostas, cadernos, agora, escaneamentoAberto: Boolean = false)`
  - `object EscaneamentoAberto { fun abrir(); fun fechar(); fun <T> varrer(bloco: (aberto: Boolean) -> T): T }`
  - `class VarreduraPeriodicaWorker` com `companion fun agendar(context)`

- [ ] **Step 1: Escreva os testes que falham**

Em `RetencaoDaRespostaTest` (mesma classe, usando `dia`, `agora`, `aosDias` que ela já tem):

```kotlin
    /** Com o escaneamento aberto, a regra "ninguem referencia" nao roda: o caderno em memoria ainda nao foi ao Room. */
    @Test
    fun `com o escaneamento aberto o arquivo sem referencia e mantido`() {
        assertEquals(
            emptyList<String>(),
            RetencaoDaResposta.arquivosAEliminar(listOf("recem-gravada.png"), emptyMap(), agora, escaneamentoAberto = true),
        )
    }

    @Test
    fun `com o escaneamento aberto o teto de 30 dias continua valendo`() {
        assertEquals(
            listOf("velha.png"),
            RetencaoDaResposta.arquivosAEliminar(
                listOf("velha.png", "nova.png"),
                mapOf("velha.png" to aosDias(31), "nova.png" to aosDias(1)),
                agora,
                escaneamentoAberto = true,
            ),
        )
    }

    @Test
    fun `sem o escaneamento aberto o arquivo sem referencia e eliminado, como sempre`() {
        assertEquals(
            listOf("orfao.png"),
            RetencaoDaResposta.arquivosAEliminar(listOf("orfao.png"), emptyMap(), agora),
        )
    }
```

`EscaneamentoAbertoTest.kt`:

```kotlin
package com.platos.android.scan

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** A marca de processo que segura a varredura em segundo plano enquanto o escaneamento esta aberto. */
class EscaneamentoAbertoTest {

    @AfterEach
    fun fecha() = EscaneamentoAberto.fechar()

    @Test
    fun `fechado por padrao, aberto depois de abrir, fechado depois de fechar`() {
        assertEquals(false, EscaneamentoAberto.varrer { it })
        EscaneamentoAberto.abrir()
        assertEquals(true, EscaneamentoAberto.varrer { it })
        EscaneamentoAberto.fechar()
        assertEquals(false, EscaneamentoAberto.varrer { it })
    }

    @Test
    fun `abrir espera a varredura em curso terminar, e a seguinte ja o ve aberto`() {
        val dentro = java.util.concurrent.CountDownLatch(1)
        val liberar = java.util.concurrent.CountDownLatch(1)
        val vistoPelaVarredura = java.util.concurrent.atomic.AtomicReference<Boolean>()
        val varredura = Thread {
            EscaneamentoAberto.varrer { aberto ->
                vistoPelaVarredura.set(aberto)
                dentro.countDown()
                liberar.await()
            }
        }
        varredura.start()
        dentro.await()

        val abriu = Thread { EscaneamentoAberto.abrir() }
        abriu.start()
        Thread.sleep(100)
        assertEquals(true, abriu.isAlive, "abrir tem de esperar a varredura em curso, que decidiu com o escaneamento fechado")

        liberar.countDown()
        varredura.join()
        abriu.join()
        assertEquals(false, vistoPelaVarredura.get())
        assertEquals(true, EscaneamentoAberto.varrer { it })
    }
}
```

- [ ] **Step 2: Rode e veja falhar**

Run: `./gradlew :apps:android:testDebugUnitTest`
Expected: FAIL (compilação).

- [ ] **Step 3: Implemente**

`RetencaoDaResposta.kt` — `arquivosAEliminar(noDisco, referenciadas, agora, escaneamentoAberto: Boolean = false)`:

```kotlin
        noDisco.filter { arquivo ->
            val capturadaEm = referenciadas[arquivo]
            // Sem referencia: so se elimina com o escaneamento fechado. Aberto, o caderno em memoria ainda nao foi ao
            // Room (so no `onStop`), e a resposta recem-gravada parece orfa (`slice-5c-3`, design D4).
            if (capturadaEm == null) !escaneamentoAberto
            else agora - capturadaEm >= PRAZO_DIAS * MILISSEGUNDOS_POR_DIA
        }
```

(acrescente à KDoc o parágrafo da trava). `varrerRespostas` ganha o mesmo parâmetro, repassado.

`EscaneamentoAberto.kt`:

```kotlin
package com.platos.android.scan

/**
 * A marca de processo "o escaneamento esta aberto" (`slice-5c-3-a-nota-no-aparelho`, design D4).
 *
 * **Vale porque o `WorkManager` roda no processo do aplicativo.** Se um dia houver segundo processo, a marca deixa
 * de valer e o requisito "a eliminacao em segundo plano nao elimina o que o escaneamento esta gravando" deve ser
 * reaberto.
 *
 * [varrer] executa o bloco **dentro da trava**, com o valor da marca naquele instante; [abrir] espera a varredura em
 * curso terminar. Sem a trava, o escaneamento poderia abrir entre a leitura da marca e a eliminacao, e a varredura
 * apagaria a resposta que o analisador acabou de gravar.
 */
object EscaneamentoAberto {
    private val trava = Any()
    private var aberto = false

    fun abrir() = synchronized(trava) { aberto = true }

    fun fechar() = synchronized(trava) { aberto = false }

    fun <T> varrer(bloco: (aberto: Boolean) -> T): T = synchronized(trava) { bloco(aberto) }
}
```

`VarreduraDoAparelho.kt` — extraia o núcleo não suspenso e passe o parâmetro:

```kotlin
fun varrerAgora(context: Context, escaneamentoAberto: Boolean = false): Varredura {
    val varredura = try {
        varrerRespostas(
            respostas = RespostasEmArquivo(RespostasEmArquivo.diretorioDe(context.filesDir)),
            cadernos = CadernosEmRoom(CadernosEmRoom.abrir(context).cadernos()),
            agora = System.currentTimeMillis(),
            escaneamentoAberto = escaneamentoAberto,
        )
    } catch (e: Exception) {
        Varredura(eliminados = 0, naoEliminados = 0, semLeituraDosCadernos = true)
    }
    registrarVarredura(varredura)
    return varredura
}

suspend fun varrerRespostasDoAparelho(context: Context): Varredura =
    withContext(Dispatchers.IO) { varrerAgora(context) }
```

`VarreduraPeriodicaWorker.kt`:

```kotlin
package com.platos.android.scan

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * O teto de 30 dias das respostas em segundo plano (`slice-5c-3-a-nota-no-aparelho`; paga a linha `5c` do §16):
 * aparelho que guarda a resposta e nunca mais abre o aplicativo tambem expurga. Roda ao menos uma vez por dia, e
 * so lida com o que a varredura ja lida; a trava e [EscaneamentoAberto].
 */
class VarreduraPeriodicaWorker(context: Context, parametros: WorkerParameters) : CoroutineWorker(context, parametros) {

    override suspend fun doWork(): Result {
        withContext(Dispatchers.IO) {
            EscaneamentoAberto.varrer { aberto -> varrerAgora(applicationContext, escaneamentoAberto = aberto) }
        }
        return Result.success()
    }

    companion object {
        private const val TRABALHO = "varredura-periodica-das-respostas"

        /** Idempotente: `KEEP` deixa o agendamento que ja existe seguir. */
        fun agendar(context: Context) {
            val pedido = PeriodicWorkRequestBuilder<VarreduraPeriodicaWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(TRABALHO, ExistingPeriodicWorkPolicy.KEEP, pedido)
        }
    }
}
```

`SessaoActivity.kt` — na linha 134 (`lifecycleScope.launch { varrerRespostasDoAparelho(applicationContext) }`), acrescente logo abaixo `VarreduraPeriodicaWorker.agendar(applicationContext)` e o import.

`ScanActivity.kt` (CRLF) — acrescente (o `onStop` já existe; ponha a linha no início do bloco depois de `super.onStop()`):

```kotlin
    override fun onStart() {
        super.onStart()
        EscaneamentoAberto.abrir()
    }
```

e **reescreva o corpo do `onStop`** assim (a marca só cai **depois** de o caderno chegar ao Room, porque `guardarCadernoEmAndamento` devolve um `Job` assíncrono: fechar antes deixaria uma varredura ver o Room desatualizado e eliminar como órfã a resposta que o caderno em memória referencia):

```kotlin
    override fun onStop() {
        super.onStop()
        val guardando = if (::session.isInitialized) {
            session.cadernoAtual?.let { caderno ->
                lifecycleScope.guardarCadernoEmAndamento(cadernos, organizacao, examPackage.meta.examId, caderno)
            }
        } else {
            null
        }
        // A marca so cai depois de o caderno estar no Room: ver `EscaneamentoAberto`.
        if (guardando == null) EscaneamentoAberto.fechar() else guardando.invokeOnCompletion { EscaneamentoAberto.fechar() }
    }
```

- [ ] **Step 4: Rode a suíte e compile**

Run: `./gradlew :apps:android:testDebugUnitTest :apps:android:assembleDebug`
Expected: PASS, `BUILD SUCCESSFUL`. Os testes antigos de varredura passam **sem mudança de asserção** (parâmetro com default `false`).

- [ ] **Step 5: Commit**

```bash
git add apps/android/src
git commit -m "feat(scan): o teto de 30 dias roda em segundo plano, com a trava do escaneamento aberto"
```

---

### Task 9: Verificação final, cobertura e dívida

**Files:**
- Create: `docs/cobertura-slice-5c-3-a-nota-no-aparelho.md`

- [ ] **Step 1: Suíte completa** (Docker de pé, o Leon o inicia; ordem que coube na memória)

Run: `./gradlew :packages:domain:jvmTest :apps:api:test :apps:android:testDebugUnitTest`
Expected: PASS, e **o número de testes de cada módulo cresce** (compare com a contagem antes da mudança: um verde sem teste novo não prova nada, P5). Cole as contagens na cobertura.

- [ ] **Step 2: Paridade e dívida**

Run: `for f in tools/parity/fio.mjs tools/parity/answer-kind.mjs; do node $f || echo FALHOU $f; done` e `node tools/divida/divida.mjs`
Expected: paridade sem `FALHOU`; a guarda dá `exit 0`. A linha "O teto de 30 dias…" **ainda aparece** (é o archive que a retira).

- [ ] **Step 3: Ver falhar o que prende o requisito** (rigorous §3: um teste que nunca falhou não prova nada)

Para cada item, desfaça a mudança de produção, rode o teste correspondente e **veja o vermelho**, depois restaure:
  1. `Caderno.normalizado`: remova o `if (corrigido) this` → `CadernoCorrigidoTest` cai.
  2. `EliminarAoConfirmar`: troque `caderno.capturaDaParcial != captura` por `false` → o teste "nao toca o caderno de outra captura" cai.
  3. `RetencaoDaResposta`: troque o `escaneamentoAberto` por `false` fixo → o teste da marca cai.
  4. `ScanSession.estadoDaDiscursiva`: remova o `corrente.aguardaNota` do `if` → "outro aluno nao substitui…" cai.
Registre na cobertura quais viu cair.

- [ ] **Step 4: Escreva `docs/cobertura-slice-5c-3-a-nota-no-aparelho.md`** no formato de `docs/cobertura-slice-5c-2-a-nota-do-professor.md`: o que foi verificado e por qual teste, as contagens, os "ver falhar" do passo 3, e a seção **"O que esta mudança NÃO verificou"**, que deve dizer, sem eufemismo:
  - os testes instrumentados (`MigracaoDoOutboxInstrumentedTest`, `NotaPendenteInstrumentedTest`) e a rota ponta a ponta **só rodam no aparelho/emulador**; se não rodaram, diga "escritos e compilados, não executados";
  - a tela de nota (Compose) não tem teste automatizado de renderização; a lógica está em `EntradaDaNota` (JVM);
  - a câmera ao vivo com a nota de ponta a ponta contra o servidor é do Leon (toque manual de permissão);
  - o `VarreduraPeriodicaWorker` agendado e executado pelo sistema com o aplicativo fechado não foi observado;
  - a marca de processo supõe processo único;
  - a janela entre gravar a nota e gravar o caderno é inofensiva por construção, mas não foi provada com queda real de processo.
  Inclua a **reconciliação da dívida** que o archive deve fazer (as linhas da proposta): retirar "O teto de 30 dias…", atualizar a da política §10.8, registrar o consumidor das linhas de migration/implantação sem prazo novo, e dizer que a guarda da tabela "Aberto" (`5`) **não foi tomada**.

- [ ] **Step 5: Commit**

```bash
git add docs/cobertura-slice-5c-3-a-nota-no-aparelho.md
git commit -m "docs(cobertura): o que a slice-5c-3 verificou e o que nao verificou"
```

- [ ] **Step 6: Pare e peça a liberação** — não empurre, não abra PR. Pergunte ao Leon, numa só pergunta, "posso empurrar e abrir o PR do código (`vewvniv/slice-5c-3-a-nota-no-aparelho`, título terminando em `(slice-5c-3-a-nota-no-aparelho)`)?". O archive (`/opsx:sync` e `/opsx:archive`, mais o commit `docs(arquitetura)` com o §16) é em **PR separado depois do merge**.
