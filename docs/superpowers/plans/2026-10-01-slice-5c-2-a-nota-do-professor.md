# slice-5c-2-a-nota-do-professor — Plano de implementação

> **Para quem executa:** SUB-SKILL OBRIGATÓRIA: `superpowers:subagent-driven-development` (recomendado) ou `superpowers:executing-plans`. Os passos usam caixas (`- [ ]`). **Nunca `/opsx:apply`** (CLAUDE.md). Não marque caixa sem a execução que a fecha, na sessão em que marca (rigorous P1).

**Goal:** o servidor aceita, valida e grava a nota do professor sobre as discursivas (decimal exata, até 2 casas) como revisão nova da mesma folha, e a revisão humana prevalece sobre a automática da mesma captura em qualquer ordem de chegada.

**Architecture:** contrato novo e rota nova (`POST …/results/graded`), com o contrato e as guardas num tipo de domínio KMP (`Pontos` em centésimos, `NotaDoProfessor`), conferidas no servidor contra o pacote publicado. O fato continua em `grading_result` e `answer_observation` (colunas `path` e `completes_capture_id`, evidência `discursiva_corrigida`), e a regra "humana vence" mora numa view `grading_result_current`. O aparelho não muda (5c-3).

**Tech Stack:** Kotlin Multiplatform (`packages/domain`), Ktor 3 + jOOQ + PostgreSQL (`apps/api`, `supabase/migrations`), Node (`tools/parity`). **Nenhuma tecnologia nova.**

**Spec:** `docs/superpowers/specs/2026-10-01-slice-5c-2-a-nota-do-professor-design.md` (decisões D1–D8). **Requisitos de entrada:** `openspec/changes/slice-5c-2-a-nota-do-professor/proposal.md` e `specs/{scoring,result-sync}/spec.md`. Se uma tarefa revelar mudança de requisito: **pare e use `/opsx:update`**, não edite em silêncio.

## Desvios deliberados do spec (§8 e §2)

1. **Migration e ajuste mecânico do jOOQ saem num commit só** (Task 4), e não em dois. Alargar `points`/`earned` para `numeric` troca o tipo gerado (`Int` → `BigDecimal`), e um commit só da migration deixaria `ResultQueries.kt` sem compilar. O ajuste é de duas linhas em um arquivo; os testes existentes leem por SQL (`getInt`) e não mudam.
2. **A composição mora num objeto novo, `CorrecaoDoProfessor` (arquivo `NotaDoProfessor.kt`)**, e não em `ObjectiveScoring`. O arquivo de `ObjectiveScoring` já tem 407 linhas; arquivo focado.
3. **O DTO leva `points` e `earned` como `String`**, e não como `Pontos` com serializador próprio. Assim o erro de leitura sai com o nome do item (mensagem de 400 das specs), e não como erro genérico do `ContentNegotiation`. `Pontos` não é `@Serializable` (P18: sem consumidor).
4. **A conferência de pacote/variante e a derivação das discursivas são extraídas** de `ProvenienciaDoResultado.kt`, e o miolo de `record()` de `ResultQueries.kt`, num commit de refatoração antes da rota nova (Task 6). Comportamento preservado, provado pelos testes existentes (P19, P25).

## Restrições globais

- Tabelas de domínio são autorizadas por `organization_id`, nunca por `user_id`; `grading_result` e `answer_observation` seguem **append-only por gatilho** e RLS forçada (I2, §3.2).
- Resultado não leva nome, turma, matrícula, imagem nem nome de arquivo (I5; requisito "não leva imagem").
- Correção objetiva local é definitiva quando não há discursivas; a conferência **não** recalcula a parte objetiva a partir do gabarito (spec `result-sync`).
- O pacote publicado é o **único oráculo** de proveniência e das discursivas (spec `result-sync`).
- A pontuação do pacote (`worth`, `max_score`) é **inteira e imutável**; só a nota do professor é decimal.
- Pontuação decimal: **até 2 casas, exata**, de 0 até o valor do pacote; nunca ponto flutuante binário (spec `scoring`).
- Sem tecnologia nova, sem Redis/broker/vector DB/GraphQL (CLAUDE.md).
- Commits: um por unidade lógica; contrato/DB/API separados dos consumidores (P25); mensagem em português, no estilo do histórico, terminando com `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. **Não empurre, não abra PR e não mergeie sem o Leon pedir**; push só com refspec explícito (`git push -u origin ramo:ramo`).
- Ambiente: **não instale nem atualize JDK, Node, SDK, emulador, plugin ou dependência do catálogo sem perguntar** (P22). A suíte do `:apps:api` e o agregado exigem **Docker** de pé; o Leon o inicia.
- `--tests` **reprova** em `:packages:domain:jvmTest` e em `:apps:android` (guardas de `@Test` sem resultado): rode o módulo inteiro. Um verde filtrado não fecha nada (P5).
- Texto de interface não leva acento; documentação leva. `ScanActivity.kt` e `design.md` têm CRLF (não tocados aqui).

## Foco da revisão (o que as specs implicam e nenhuma tarefa de teste obvia exercita)

Cada linha tem o teste que a prende, na tarefa dona do código.

1. **`1,5` (vírgula decimal, como o professor digita em pt-BR)** deve ser recusado com mensagem que diz "ponto decimal", e nunca lido como `15` nem como `1.5` → Task 2.
2. **Valor acima de `999999.99` ou com 7+ dígitos inteiros** estouraria o `numeric(8,2)` do banco e viraria **500** em vez de 400 → recusado no domínio antes do SQL → Task 2.
3. **Bordas da faixa:** `0`, `0.00` e o valor exato do pacote (`3`, `3.00`) aceitos; `3.01` recusado; nota máxima em todas e zero em todas fecham a soma exata → Task 3.
4. **`answer_kind: discursiva_corrigida` contrabandeado nas observações objetivas** de uma parcial ou do corpo do professor deve ser recusado (o `else` de `paraOutcome` já recusa valor desconhecido; a constante nova não pode abrir esse caminho) → Task 7.
5. **Folha avulsa (token nulo)** com nota do professor, e **duas notas do professor para a mesma captura** chegando fora de ordem: a view deve devolver uma só corrente por folha, e a avulsa não pode se misturar com outra avulsa → Task 4.

---

## Mapa de arquivos

| Arquivo | Ação | Responsabilidade |
|---|---|---|
| `docs/adr/0021-a-nota-do-professor-decimal-exata-e-a-revisao-corrente.md` | criar | registra D2–D7 |
| `openspec/changes/slice-5c-2-a-nota-do-professor/proposal.md` | modificar (via `/opsx:update`) | Impact: Android só teste; `GradedResultSubmissionDto` |
| `packages/domain/src/commonMain/kotlin/com/platos/domain/scoring/Pontos.kt` | criar | decimal exato em centésimos |
| `packages/domain/src/commonMain/kotlin/com/platos/domain/scoring/NotaDoProfessor.kt` | criar | `NotaDoProfessor`, `PontuacaoDada`, `DiscursivaCorrigida`, `NotaDoProfessorOutcome`, `CorrecaoDoProfessor` |
| `packages/domain/src/commonMain/kotlin/com/platos/domain/transport/GradedResultDto.kt` | criar | `GradedResultSubmissionDto`, `EssayGradeDto` |
| `packages/domain/src/commonMain/kotlin/com/platos/domain/transport/AnswerKind.kt` | modificar | `DISCURSIVA_CORRIGIDA` |
| `packages/domain/src/commonTest/kotlin/com/platos/domain/scoring/PontosTest.kt` | criar | |
| `packages/domain/src/commonTest/kotlin/com/platos/domain/scoring/NotaDoProfessorTest.kt` | criar | |
| `packages/domain/src/commonTest/kotlin/com/platos/domain/transport/GradedResultSubmissionDtoTest.kt` | criar | literal + ida e volta |
| `supabase/migrations/20261001120000_nota_do_professor.sql` | criar | tipos, colunas, checks, view |
| `apps/api/src/main/kotlin/com/platos/api/exam/ResultQueries.kt` | modificar | `BigDecimal` (Task 4); `gravar()` extraído e `recordGraded` (Tasks 6–7) |
| `apps/api/src/main/kotlin/com/platos/api/exam/ProvenienciaDoResultado.kt` | modificar | helpers extraídos (Task 6); `conferirNotaDoProfessor` (Task 7) |
| `apps/api/src/main/kotlin/com/platos/api/http/dto/ResultDto.kt` | modificar | `paraOutcome`/`paraPendencia` viram `internal` (Task 6) |
| `apps/api/src/main/kotlin/com/platos/api/http/dto/GradedResultDto.kt` | criar | Fase 1 da rota nova |
| `apps/api/src/main/kotlin/com/platos/api/http/Routes.kt` | modificar | a rota nova |
| `apps/api/src/test/kotlin/com/platos/api/db/GradingResultCurrentViewTest.kt` | criar | colunas, checks e a view, em Postgres real |
| `apps/api/src/test/kotlin/com/platos/api/http/GradedResultRouteTest.kt` | criar | a rota, em Postgres real |
| `apps/android/src/test/kotlin/com/platos/android/api/GradedResultDtoTest.kt` | criar | **só teste**: literal do aparelho (`fio.mjs`) |
| `tools/parity/answer-kind.mjs` | modificar | lê o `check` da **última** migration que o declara |
| `docs/cobertura-slice-5c-2-a-nota-do-professor.md` | criar | como cada verificação foi vista falhar; o que não foi verificado |

---

### Task 0: Preflight — estado, veículo dos commits, Docker, linha de base

**Files:** nenhum.

- [ ] **Step 1: Conferir o estado**

Run: `git status --short && git branch --show-current && git log --oneline -4`
Expected: árvore limpa; branch `vewvniv/workflow-openspec-superpowers`; no topo `131ec59` (design), `84e136d` (proposta), `9717e20` (workflow).

- [ ] **Step 2: Perguntar ao Leon o veículo dos commits (decisão dele, não minha)**

O commit `9717e20` é a configuração OpenSpec+Superpowers e **não é código desta fatia**. Pelo fluxo registrado ("o PR do código leva a proposta; o archive vai em PR separado"), há duas saídas: **(a)** a configuração vai num PR próprio e a branch de código sai de `origin/main` com `git cherry-pick 84e136d 131ec59`; **(b)** tudo na mesma branch. Pergunte; **não empurre nada** antes da resposta. Se (a): `git switch -c vewvniv/slice-5c-2-a-nota-do-professor origin/main --no-track && git cherry-pick 84e136d 131ec59`. Se (b): siga na branch atual.

- [ ] **Step 3: Pedir o Docker (P22)**

Peça ao Leon que inicie o Docker Desktop (`:apps:api:generateJooq` e a suíte da API usam Testcontainers). Confirme: `docker info` sai com `exit 0`. Se o Docker acabou de subir: `./gradlew --stop` (o daemon do Gradle guarda a falha "Could not find a valid Docker environment").

- [ ] **Step 4: Linha de base verde, antes de mexer em código**

Run: `./gradlew :packages:domain:jvmTest :apps:api:test :apps:android:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. Anote a data e o `timestamp` de um XML de cada módulo (`packages/domain/build/test-results/jvmTest/*.xml`, `apps/api/build/test-results/test/*.xml`, `apps/android/build/test-results/testDebugUnitTest/*.xml`): é a âncora (P3). Se algum falhar **antes** de eu ter mudado nada, pare e relate: não é desta mudança.

---

### Task 1: ADR-0021 e `/opsx:update` do Impact da proposta

**Files:**
- Create: `docs/adr/0021-a-nota-do-professor-decimal-exata-e-a-revisao-corrente.md`
- Modify (via `/opsx:update`): `openspec/changes/slice-5c-2-a-nota-do-professor/proposal.md`

- [ ] **Step 1: Escrever o ADR**

```markdown
# ADR-0021 — A nota do professor é decimal exata, mora em `grading_result`, e a revisão corrente é derivada

**Status:** aceito · **Data:** 2026-10-01 · **Fatia-limite:** archive da `slice-5c-2-a-nota-do-professor`
**Referências:** `ARQUITETURA-FINAL-v3.md` §10 (correção manual de discursiva: local, entra no outbox), §11
(`grading_result(origin: omr|ai|teacher, path: image|text)`), I2 · ADR-0015 · ADR-0003 ·
`openspec/changes/slice-5c-2-a-nota-do-professor/` · `docs/superpowers/specs/2026-10-01-slice-5c-2-a-nota-do-professor-design.md`

## Contexto

A fatia 5 entrega a parcial objetiva e a resposta guardada, mas nenhuma nota discursiva existe. A nota do
professor é por discursiva e **pode ser fracionária** (1.5, 1.75). Hoje `points` e `earned` são inteiros no
domínio, no contrato e no banco, e o fato é append-only por gatilho, o que impede marcar "a revisão corrente"
com `UPDATE`.

## Decisão

1. **Forma do fato:** a nota do professor é **revisão nova** do mesmo par (prova, token) em `grading_result`,
   com `origin = 'teacher'`, e as colunas novas `path` (`'image'`) e `completes_capture_id` (o `capture_id` da
   parcial que ela completa). **Não há tabela própria**: a §11 já nomeia `grading_result(origin, path)`.
2. **Evidência da discursiva:** linha de `answer_observation` com `answer_kind = 'discursiva_corrigida'`.
3. **Decimal exato:** no domínio, `Pontos` guarda **centésimos em `Long`** (o `commonMain` não tem `BigDecimal`);
   até 2 casas, de 0 a 999999.99. No fio, **string JSON** (`"1.75"`), nunca número JSON (que passa por `Double`).
   No banco, `numeric(8,2)` em `grading_result.points` e `answer_observation.earned`; `max_score` e `worth`
   seguem `int` (o pacote é inteiro e imutável).
4. **Contrato novo, rota nova:** `GradedResultSubmissionDto` e `POST …/results/graded`. O contrato e a rota de
   `ResultSubmissionDto` não mudam (ADR-0015: dono único em `packages/domain`).
5. **A revisão corrente é derivada, não gravada:** a view `grading_result_current` (`security_invoker`) exclui a
   revisão `omr` cuja captura alguma revisão `teacher` da mesma prova declara completar, e escolhe a de maior
   `revision` entre as elegíveis. A regra "revisão humana vence a automática da mesma captura, em qualquer
   ordem de chegada" mora **num lugar só**.

## Consequências

- `numeric(8,2)` **arredonda em silêncio** três casas no `insert`. A recusa de mais de 2 casas é do **domínio**,
  antes do SQL, e há teste de que o valor de 3 casas nunca chega ao banco.
- Um valor novo em `answer_kind` exige os três registros concordarem: domínio (`AnswerKind`), o `check` da
  migration e `tools/parity/answer-kind.mjs`, que passa a ler o `check` da **última** migration que o declara.
- `ApuracaoParaEnvio` **não** ganha subtipo agora: a 5c-3 o estende quando houver consumidor.
- A numeração de `revision` das folhas avulsas (token nulo) segue agrupando todas num contador (limitação
  preexistente, só registrada).

## Como isto poderia falhar em silêncio

- **A view perder a RLS** se não for `security_invoker`: cada organização leria as folhas das outras. Teste com
  usuário de outra organização.
- **A exclusão por `completes_capture_id` não ser exercitada**: a view devolveria a parcial automática depois da
  nota do professor. Mutação: remover a exclusão e ver os quatro cenários de ordem falharem.
- **Soma em `Double`** reintroduzir `0.1 + 0.2 ≠ 0.3`. Teste de igualdade exata e mutação.
```

- [ ] **Step 2: Rodar `/opsx:update` para o Impact da proposta**

Invoque `/opsx:update slice-5c-2-a-nota-do-professor` com este pedido: "No Impact, trocar a linha `- **Nenhum** código Android, nenhuma tecnologia nova.` por `- **Aparelho:** nenhum código de produto; um teste de literal em `apps/android/src/test` (o `tools/parity/fio.mjs` exige literal do contrato novo nos dois lados). Nenhuma tecnologia nova.`; e na linha do domínio KMP acrescentar que o contrato novo é `GradedResultSubmissionDto` (corpo do professor, com `completes_capture_id` identificando a captura da parcial que ela completa) e a nota exata `Pontos`." **Confirme cada edição com o Leon antes de gravar.** Não toque `specs/`: o requisito já diz que o resultado "identifica a captura da parcial que completa".

- [ ] **Step 3: Validar**

Run: `openspec validate slice-5c-2-a-nota-do-professor --strict`
Expected: `Change 'slice-5c-2-a-nota-do-professor' is valid`

- [ ] **Step 4: Commit**

```bash
git add docs/adr/0021-a-nota-do-professor-decimal-exata-e-a-revisao-corrente.md openspec/changes/slice-5c-2-a-nota-do-professor/proposal.md
git commit -m "docs(adr): ADR-0021, a nota do professor é decimal exata e a revisão corrente é derivada" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 2: `Pontos` — decimal exato em centésimos (domínio)

**Files:**
- Create: `packages/domain/src/commonMain/kotlin/com/platos/domain/scoring/Pontos.kt`
- Test: `packages/domain/src/commonTest/kotlin/com/platos/domain/scoring/PontosTest.kt`

**Interfaces:**
- Produces: `Pontos` (`value class`, `centesimos: Long`), `Pontos.parse(String): Pontos` (lança `IllegalArgumentException` com mensagem própria para negativa, mais de 2 casas e não numérica), `Pontos.inteiros(Int): Pontos`, `Pontos.ZERO`, `operator plus`, `Comparable`, `toString()` canônico com 2 casas.

- [ ] **Step 1: Escrever o teste que falha**

```kotlin
package com.platos.domain.scoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `Pontos`: o decimal exato da nota do professor (`slice-5c-2-a-nota-do-professor`, ADR-0021).
 *
 * Cada recusa tem a **sua** mensagem, e cada teste a le: "recusou" nao diz qual guarda segurou
 * (`rigorous.md` §3).
 */
class PontosTest {

    private fun recusa(texto: String): String =
        assertFailsWith<IllegalArgumentException> { Pontos.parse(texto) }.message.orEmpty()

    @Test
    fun `1_5 e 1_75 sao validos e valem 150 e 175 centesimos`() {
        assertEquals(150L, Pontos.parse("1.5").centesimos)
        assertEquals(175L, Pontos.parse("1.75").centesimos)
    }

    @Test
    fun `1_5 e 1_50 sao o mesmo valor`() {
        assertEquals(Pontos.parse("1.5"), Pontos.parse("1.50"))
    }

    @Test
    fun `zero, inteiro e a borda superior do banco sao validos`() {
        assertEquals(0L, Pontos.parse("0").centesimos)
        assertEquals(0L, Pontos.parse("0.00").centesimos)
        assertEquals(300L, Pontos.parse("3").centesimos)
        assertEquals(99_999_999L, Pontos.parse("999999.99").centesimos)
        assertEquals(50L, Pontos.parse("00.5").centesimos)
    }

    @Test
    fun `a forma canonica tem sempre duas casas`() {
        assertEquals("1.50", Pontos.parse("1.5").toString())
        assertEquals("0.05", Pontos.parse("0.05").toString())
        assertEquals("3.00", Pontos.parse("3").toString())
        assertEquals("10.01", Pontos.parse("10.01").toString())
    }

    @Test
    fun `0_1 mais 0_2 e exatamente 0_3`() {
        assertEquals(Pontos.parse("0.3"), Pontos.parse("0.1") + Pontos.parse("0.2"))
    }

    @Test
    fun `tres casas sao recusadas, e nao arredondadas`() {
        val motivo = recusa("1.333")
        assertTrue("mais de 2 casas" in motivo, motivo)
        assertTrue("mais de 2 casas" in recusa("1.500"), "zero a direita nao desfaz as 3 casas")
    }

    @Test
    fun `negativa tem a sua mensagem`() {
        assertTrue("negativa" in recusa("-1"), recusa("-1"))
        assertTrue("negativa" in recusa("-0.5"), recusa("-0.5"))
    }

    @Test
    fun `o que nao e numero tem a sua mensagem`() {
        for (texto in listOf("abc", "", " 1", "1 ", "+1", "1e2", ".5", "1.", "-", "1.2.3")) {
            val motivo = recusa(texto)
            assertTrue("nao e uma pontuacao" in motivo, "'$texto' -> $motivo")
        }
    }

    @Test
    fun `a virgula decimal e recusada e a mensagem pede o ponto`() {
        val motivo = recusa("1,5")
        assertTrue("nao e uma pontuacao" in motivo, motivo)
        assertTrue("ponto decimal" in motivo, motivo)
    }

    @Test
    fun `alem do alcance do banco e recusado no dominio, antes do SQL`() {
        for (texto in listOf("1000000", "1000000.00", "12345678.5")) {
            val motivo = recusa(texto)
            assertTrue("999999.99" in motivo, "'$texto' -> $motivo")
        }
    }

    @Test
    fun `inteiros aceita so o intervalo do banco`() {
        assertEquals(300L, Pontos.inteiros(3).centesimos)
        assertFailsWith<IllegalArgumentException> { Pontos.inteiros(-1) }
        assertFailsWith<IllegalArgumentException> { Pontos.inteiros(1_000_000) }
    }

    @Test
    fun `valores que o ponto flutuante binario erra continuam exatos`() {
        // 0.29 * 100 = 28.999999999999996, 0.57 * 100 = 56.99999999999999, 1.15 * 100 = 114.99999999999999.
        // E o que faz a mutacao "ler via Double" da Task 8 ser vista falhar.
        assertEquals(29L, Pontos.parse("0.29").centesimos)
        assertEquals(57L, Pontos.parse("0.57").centesimos)
        assertEquals(115L, Pontos.parse("1.15").centesimos)
    }

    @Test
    fun `a ordem e a dos centesimos`() {
        assertTrue(Pontos.parse("1.75") > Pontos.parse("1.5"))
        assertTrue(Pontos.ZERO < Pontos.parse("0.01"))
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew :packages:domain:jvmTest`
Expected: FAIL na compilação: `Unresolved reference: Pontos`. (Módulo inteiro: `--tests` reprova nesta suíte.)

- [ ] **Step 3: Implementar**

```kotlin
package com.platos.domain.scoring

import kotlin.jvm.JvmInline

private val NEGATIVA = Regex("""-\d+(\.\d+)?""")
private val MUITAS_CASAS = Regex("""\d+\.\d{3,}""")
private val VALIDA = Regex("""(\d{1,6})(?:\.(\d{1,2}))?""")

/**
 * A pontuacao que o professor da a uma discursiva: decimal **exato**, ate 2 casas, de 0 a 999999.99
 * (`slice-5c-2-a-nota-do-professor`, ADR-0021).
 *
 * **Centesimos em `Long`, e nao `Double`.** Com ponto flutuante binario, `0.1 + 0.2 != 0.3` quebraria
 * "a soma por questao e igual ao total" e a idempotencia do reenvio. O `commonMain` nao tem
 * `BigDecimal` e o dominio compila para JVM, Android e JS: inteiro em centesimos e exato nos tres.
 *
 * **O alcance (999999.99) e o do `numeric(8,2)` do banco.** O `numeric(8,2)` arredonda tres casas e
 * **estoura** acima disto com erro de SQL; as duas coisas teriam de ser recusadas aqui, antes do SQL,
 * para virar 400 com mensagem e nao 500.
 *
 * **Nao e `@Serializable`**: no fio a pontuacao viaja como `String` ("1.75"), e o servidor a le com
 * [parse], que da o motivo com o nome do item. Um serializador sem consumidor seria P18.
 */
@JvmInline
value class Pontos private constructor(val centesimos: Long) : Comparable<Pontos> {

    operator fun plus(outro: Pontos): Pontos = Pontos(centesimos + outro.centesimos)

    override fun compareTo(other: Pontos): Int = centesimos.compareTo(other.centesimos)

    /** A forma canonica: sempre duas casas. `1.5` e `1.50` sao o mesmo valor e saem `1.50`. */
    override fun toString(): String {
        val fracao = (centesimos % 100).toString().padStart(2, '0')
        return "${centesimos / 100}.$fracao"
    }

    companion object {
        val ZERO: Pontos = Pontos(0L)

        /** Uma pontuacao inteira, como as do pacote. */
        fun inteiros(valor: Int): Pontos {
            require(valor in 0..999_999) { "pontuacao inteira $valor fora de 0..999999" }
            return Pontos(valor * 100L)
        }

        /**
         * Le uma pontuacao escrita com **ponto** decimal e ate 2 casas.
         *
         * Tres recusas distintas, para a mensagem dizer o que nao fecha: negativa, mais de 2 casas
         * (recusada, **nunca arredondada**) e "nao e uma pontuacao" (inclui a virgula decimal).
         */
        fun parse(texto: String): Pontos {
            require(!NEGATIVA.matches(texto)) { "pontuacao negativa: '$texto'" }
            require(!MUITAS_CASAS.matches(texto)) { "pontuacao com mais de 2 casas decimais: '$texto'" }
            val partes = VALIDA.matchEntire(texto) ?: throw IllegalArgumentException(
                "'$texto' nao e uma pontuacao: use numero com ponto decimal e ate 2 casas, " +
                    "de 0 ate 999999.99",
            )
            val inteiro = partes.groupValues[1].toLong()
            val fracao = partes.groupValues[2]
            val centesimos = when (fracao.length) {
                0 -> 0
                1 -> fracao.toInt() * 10
                else -> fracao.toInt()
            }
            return Pontos(inteiro * 100 + centesimos)
        }
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew :packages:domain:jvmTest`
Expected: `BUILD SUCCESSFUL`. Confira o `timestamp` do XML de `PontosTest` (`packages/domain/build/test-results/jvmTest/TEST-com.platos.domain.scoring.PontosTest.xml`) e que ele lista os 13 testes (P2, P3).

- [ ] **Step 5: Commit**

```bash
git add packages/domain/src/commonMain/kotlin/com/platos/domain/scoring/Pontos.kt packages/domain/src/commonTest/kotlin/com/platos/domain/scoring/PontosTest.kt
git commit -m "feat(domain): Pontos, o decimal exato da nota do professor" -m "Centésimos em Long; até 2 casas; recusa negativa, 3 casas e não numérica, cada uma com a sua mensagem." -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 3: `NotaDoProfessor` e a composição parcial + notas (domínio)

**Files:**
- Create: `packages/domain/src/commonMain/kotlin/com/platos/domain/scoring/NotaDoProfessor.kt`
- Test: `packages/domain/src/commonTest/kotlin/com/platos/domain/scoring/NotaDoProfessorTest.kt`

**Interfaces:**
- Consumes: `Pontos` (Task 2); `PartialScore`, `AwaitingEssay`, `PendingQuestion`, `QuestionOutcome` (existentes).
- Produces: `PontuacaoDada(questionId: String, earned: Pontos)`; `DiscursivaCorrigida(questionId, worth: Int, earned: Pontos)`; `NotaDoProfessor(packageHash, variantId, objectivePoints: Int, objectiveMaxScore: Int, maxScore: Int, pending, outcomes, essays)` com `total: Pontos`, `closed: Boolean`, `pointsAtStake: Int`; `NotaDoProfessorOutcome.Scored(nota)` / `.Rejected(reason)`; `CorrecaoDoProfessor.completar(parcial: PartialScore, pontuacoes: List<PontuacaoDada>): NotaDoProfessorOutcome`.

- [ ] **Step 1: Escrever o teste que falha**

```kotlin
package com.platos.domain.scoring

import com.platos.domain.capture.QuestionAnswer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A nota do professor completa a parcial (`slice-5c-2-a-nota-do-professor`, spec `scoring`).
 *
 * Um caso por recusa, e cada um **le a mensagem**: a camada que segurou e a que a mensagem nomeia
 * (`rigorous.md` §3). A parcial de partida: quatro objetivas de 1 ponto (tres certas, uma errada),
 * discursivas `d1` (3) e `d2` (4), prova de 11.
 */
class NotaDoProfessorTest {

    private fun certa(id: String) =
        QuestionOutcome(id, QuestionAnswer.Marcada(id, "A"), worth = 1, earned = 1)

    private fun errada(id: String) =
        QuestionOutcome(id, QuestionAnswer.Marcada(id, "B"), worth = 1, earned = 0)

    private fun multipla(id: String) =
        QuestionOutcome(id, QuestionAnswer.MultiplaMarcacao(id, listOf("A", "B")), worth = 1, earned = 0)

    private fun parcial(
        outcomes: List<QuestionOutcome> = listOf(certa("q1"), certa("q2"), certa("q3"), errada("q4")),
        pending: List<PendingQuestion> = emptyList(),
        awaiting: List<AwaitingEssay> = listOf(AwaitingEssay("d1", 3), AwaitingEssay("d2", 4)),
        maxScore: Int = 11,
    ) = PartialScore(
        packageHash = "hash",
        variantId = "v1",
        objectivePoints = outcomes.sumOf { it.earned },
        objectiveMaxScore = 4,
        maxScore = maxScore,
        awaiting = awaiting,
        pending = pending,
        outcomes = outcomes,
    )

    private fun dar(vararg notas: Pair<String, String>) =
        notas.map { PontuacaoDada(it.first, Pontos.parse(it.second)) }

    private fun nota(parcial: PartialScore = parcial(), vararg notas: Pair<String, String>): NotaDoProfessor =
        (CorrecaoDoProfessor.completar(parcial, dar(*notas)) as NotaDoProfessorOutcome.Scored).nota

    private fun recusa(parcial: PartialScore = parcial(), vararg notas: Pair<String, String>): String =
        (CorrecaoDoProfessor.completar(parcial, dar(*notas)) as NotaDoProfessorOutcome.Rejected).reason

    @Test
    fun `completar a parcial soma a objetiva e as discursivas, 3 + 1_5 + 3_75 = 8_25 de 11`() {
        val nota = nota(parcial(), "d1" to "1.5", "d2" to "3.75")

        assertEquals(Pontos.parse("8.25"), nota.total)
        assertEquals(11, nota.maxScore)
        assertEquals(listOf("d1", "d2"), nota.essays.map { it.questionId })
        assertEquals(listOf(Pontos.parse("1.5"), Pontos.parse("3.75")), nota.essays.map { it.earned })
    }

    @Test
    fun `sem pendencia objetiva a nota fecha`() {
        assertTrue(nota(parcial(), "d1" to "3", "d2" to "4").closed)
    }

    @Test
    fun `pendencia objetiva mantem a nota aberta, e o que ela disputa continua contado`() {
        val comPendencia = parcial(
            outcomes = listOf(certa("q1"), certa("q2"), certa("q3"), multipla("q4")),
            pending = listOf(PendingQuestion("q4", PendingReason.MULTIPLA_MARCACAO, 1)),
        )

        val nota = nota(comPendencia, "d1" to "3", "d2" to "4")

        assertFalse(nota.closed)
        assertEquals(1, nota.pointsAtStake)
        assertEquals(listOf("q4"), nota.pending.map { it.questionId })
    }

    @Test
    fun `a nota do professor nao mexe na objetiva`() {
        val base = parcial()

        val nota = nota(base, "d1" to "1", "d2" to "1")

        assertEquals(base.objectivePoints, nota.objectivePoints)
        assertEquals(base.outcomes, nota.outcomes)
        assertEquals(base.pending, nota.pending)
    }

    @Test
    fun `1_5 e 1_50 produzem o mesmo resultado`() {
        assertEquals(nota(parcial(), "d1" to "1.5", "d2" to "2"), nota(parcial(), "d1" to "1.50", "d2" to "2.00"))
    }

    @Test
    fun `a soma e exata, 0_1 e 0_2 dao 0_3 mais a objetiva`() {
        assertEquals(Pontos.parse("3.3"), nota(parcial(), "d1" to "0.1", "d2" to "0.2").total)
    }

    @Test
    fun `zero em todas e o maximo em todas sao validos`() {
        assertEquals(Pontos.parse("3"), nota(parcial(), "d1" to "0", "d2" to "0.00").total)
        assertEquals(Pontos.parse("10"), nota(parcial(), "d1" to "3.00", "d2" to "4").total)
    }

    @Test
    fun `um centesimo acima do valor e recusado e a mensagem diz questao e faixa`() {
        val motivo = recusa(parcial(), "d1" to "3.01", "d2" to "4")

        assertTrue("'d1'" in motivo && "3.01" in motivo && "no maximo 3" in motivo, motivo)
    }

    @Test
    fun `pontuacao em objetiva ou em item que a variante nao tem e recusada`() {
        val objetiva = recusa(parcial(), "d1" to "1", "d2" to "1", "q1" to "1")
        assertTrue("'q1'" in objetiva && "nao e discursiva" in objetiva, objetiva)

        val alheio = recusa(parcial(), "d1" to "1", "d2" to "1", "x9" to "1")
        assertTrue("'x9'" in alheio && "nao e discursiva" in alheio, alheio)
    }

    @Test
    fun `discursiva sem pontuacao e recusada e a mensagem diz qual falta`() {
        val motivo = recusa(parcial(), "d1" to "1")

        assertTrue("'d2'" in motivo && "nao recebeu pontuacao" in motivo, motivo)
    }

    @Test
    fun `discursiva pontuada duas vezes e recusada`() {
        val motivo = recusa(parcial(), "d1" to "1", "d1" to "2", "d2" to "1")

        assertTrue("'d1'" in motivo && "mais de uma vez" in motivo, motivo)
    }

    @Test
    fun `prova so objetiva nao tem o que pontuar`() {
        val soObjetiva = PartialScore(
            "hash", "v1", objectivePoints = 3, objectiveMaxScore = 4, maxScore = 4,
            awaiting = emptyList(), pending = emptyList(),
            outcomes = listOf(certa("q1"), certa("q2"), certa("q3"), errada("q4")),
        )

        val motivo = recusa(soObjetiva)

        assertTrue("nota completa" in motivo, motivo)
    }

    @Test
    fun `os maximos fecham com a prova e a soma por questao fecha com o total`() {
        val nota = nota(parcial(), "d1" to "1.75", "d2" to "2.5")

        assertEquals(
            nota.objectiveMaxScore + nota.essays.sumOf { it.worth },
            nota.maxScore,
        )
        val porQuestao = nota.outcomes.fold(Pontos.ZERO) { a, o -> a + Pontos.inteiros(o.earned) } +
            nota.essays.fold(Pontos.ZERO) { a, e -> a + e.earned }
        assertEquals(nota.total, porQuestao)
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew :packages:domain:jvmTest`
Expected: FAIL na compilação: `Unresolved reference: CorrecaoDoProfessor` (e `PontuacaoDada`, `NotaDoProfessorOutcome`).

- [ ] **Step 3: Implementar**

```kotlin
package com.platos.domain.scoring

/** A pontuacao que o professor deu a uma discursiva, como chegou: o item e a nota, sem o valor dele. */
data class PontuacaoDada(
    val questionId: String,
    val earned: Pontos,
)

/** Uma discursiva ja corrigida: o que valia (inteiro, do pacote) e o que o professor lhe deu. */
data class DiscursivaCorrigida(
    val questionId: String,
    val worth: Int,
    val earned: Pontos,
) {
    init {
        require(worth >= 0) { "a discursiva '$questionId' vale $worth ponto(s), e pontuacao nao e negativa" }
        require(earned.centesimos <= worth * 100L) {
            "a discursiva '$questionId' recebeu $earned e vale no maximo $worth"
        }
    }
}

/**
 * A nota completa de uma prova com discursiva, depois que o professor pontuou todas elas
 * (`slice-5c-2-a-nota-do-professor`, spec `scoring`).
 *
 * **Nao herda de [PartialScore] nem de [ObjectiveScore], e nao os contem** — a mesma protecao que a
 * KDoc de [PartialScore] da: a gravacao e o envio de cada tipo aceitam so aquele tipo, e o compilador
 * recusa a troca. Uma parcial nunca pode subir como a nota do professor, nem o contrario.
 *
 * [total] e **exato** (centesimos). [closed] e derivado: so fecha sem pendencia **objetiva** — a nota
 * do professor e sobre a discursiva, e nao decide a objetiva ambigua.
 *
 * Construir uma [NotaDoProfessor] direto exige que o chamador ja tenha conferido a cobertura das
 * discursivas; o caminho que **confere** e [CorrecaoDoProfessor.completar].
 */
data class NotaDoProfessor(
    val packageHash: String,
    val variantId: String,
    /** Pontuacao objetiva ja apurada, inteira. Nao inclui nada que dependa de revisao. */
    val objectivePoints: Int,
    val objectiveMaxScore: Int,
    /** Pontuacao maxima da prova, como o pacote a declara. */
    val maxScore: Int,
    val pending: List<PendingQuestion>,
    /** A evidencia das questoes **objetivas**, como na parcial. */
    val outcomes: List<QuestionOutcome>,
    /** As discursivas corrigidas, na ordem das posicoes da variante. */
    val essays: List<DiscursivaCorrigida>,
) {

    init {
        require(essays.isNotEmpty()) { "a nota do professor precisa de ao menos uma discursiva corrigida" }
        require(objectivePoints in 0..objectiveMaxScore) {
            "objetiva $objectivePoints fora de 0..$objectiveMaxScore"
        }
        require(outcomes.sumOf { it.earned } == objectivePoints) {
            "a evidencia objetiva soma ${outcomes.sumOf { it.earned }} ponto(s) e a parcial apurada e $objectivePoints"
        }
        val repetidos = essays.groupingBy { it.questionId }.eachCount().filterValues { it > 1 }.keys
        require(repetidos.isEmpty()) {
            "a nota repete a discursiva: " + repetidos.sorted().joinToString(", ")
        }
        require(objectiveMaxScore + essays.sumOf { it.worth } == maxScore) {
            "o maximo objetivo ($objectiveMaxScore) mais as discursivas (${essays.sumOf { it.worth }}) " +
                "somam ${objectiveMaxScore + essays.sumOf { it.worth }}, e a prova vale $maxScore"
        }
    }

    /** O total exato: a parte objetiva mais o que o professor deu, em centesimos. */
    val total: Pontos = essays.fold(Pontos.inteiros(objectivePoints)) { soma, e -> soma + e.earned }

    /** Quanto da parte objetiva ainda depende das pendencias. */
    val pointsAtStake: Int get() = pending.sumOf { it.points }

    /** Fechada quando nada **objetivo** depende de revisao humana. */
    val closed: Boolean get() = pending.isEmpty()
}

/** O que saiu de completar a parcial: ou a nota do professor, ou o motivo da recusa. */
sealed interface NotaDoProfessorOutcome {

    data class Scored(val nota: NotaDoProfessor) : NotaDoProfessorOutcome

    /** A nota nao pode ser composta, e [reason] diz por que numa frase que serve para a tela. */
    data class Rejected(val reason: String) : NotaDoProfessorOutcome
}

/**
 * Completa a [PartialScore] com a pontuacao que o professor deu a cada discursiva.
 *
 * **Local, deterministica e sem efeito**, como as demais apuracoes: o servidor a roda com o **mesmo**
 * codigo que o aparelho usara (regra 7 do `CLAUDE.md`). Nao recalcula a parte objetiva — copia o que a
 * parcial apurou — e cobre **todas** as discursivas da variante de uma vez: nota de parte delas nao e
 * representavel aqui (e do caderno incompleto, outra mudanca).
 */
object CorrecaoDoProfessor {

    fun completar(parcial: PartialScore, pontuacoes: List<PontuacaoDada>): NotaDoProfessorOutcome {
        if (parcial.awaiting.isEmpty()) {
            return NotaDoProfessorOutcome.Rejected(
                "a prova nao tem questao discursiva e tem nota completa; nao ha o que pontuar",
            )
        }

        val discursivas = parcial.awaiting.associateBy { it.questionId }

        val repetidas = pontuacoes.groupingBy { it.questionId }.eachCount().filterValues { it > 1 }.keys
        if (repetidas.isNotEmpty()) {
            return NotaDoProfessorOutcome.Rejected(
                "a discursiva ${nomes(repetidas)} foi pontuada mais de uma vez",
            )
        }

        val alheias = pontuacoes.map { it.questionId }.filter { it !in discursivas }
        if (alheias.isNotEmpty()) {
            return NotaDoProfessorOutcome.Rejected(
                "o item ${nomes(alheias)} nao e discursiva da variante '${parcial.variantId}'",
            )
        }

        val dadas = pontuacoes.associateBy { it.questionId }
        val faltando = parcial.awaiting.map { it.questionId }.filter { it !in dadas }
        if (faltando.isNotEmpty()) {
            return NotaDoProfessorOutcome.Rejected(
                "a discursiva ${nomes(faltando)} da variante '${parcial.variantId}' nao recebeu pontuacao",
            )
        }

        val corrigidas = parcial.awaiting.map { aguardando ->
            val dada = dadas.getValue(aguardando.questionId)
            if (dada.earned.centesimos > aguardando.points * 100L) {
                return NotaDoProfessorOutcome.Rejected(
                    "a discursiva '${aguardando.questionId}' recebeu ${dada.earned} e vale no maximo ${aguardando.points}",
                )
            }
            DiscursivaCorrigida(aguardando.questionId, aguardando.points, dada.earned)
        }

        return NotaDoProfessorOutcome.Scored(
            NotaDoProfessor(
                packageHash = parcial.packageHash,
                variantId = parcial.variantId,
                objectivePoints = parcial.objectivePoints,
                objectiveMaxScore = parcial.objectiveMaxScore,
                maxScore = parcial.maxScore,
                pending = parcial.pending,
                outcomes = parcial.outcomes,
                essays = corrigidas,
            ),
        )
    }

    private fun nomes(ids: Collection<String>): String = ids.sorted().joinToString(", ") { "'$it'" }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew :packages:domain:jvmTest`
Expected: `BUILD SUCCESSFUL`; o XML de `NotaDoProfessorTest` lista os 13 testes, com `timestamp` de agora.

- [ ] **Step 5: Commit**

```bash
git add packages/domain/src/commonMain/kotlin/com/platos/domain/scoring/NotaDoProfessor.kt packages/domain/src/commonTest/kotlin/com/platos/domain/scoring/NotaDoProfessorTest.kt
git commit -m "feat(domain): NotaDoProfessor e a composição parcial + notas" -m "Tipo próprio (não herda de PartialScore nem de ObjectiveScore); soma exata; fecha só sem pendência objetiva; recusa discursiva faltando, repetida, alheia e acima do valor." -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Migration, ajuste mecânico do jOOQ, e o banco medido em Postgres real

**Files:**
- Create: `supabase/migrations/20261001120000_nota_do_professor.sql`
- Modify: `apps/api/src/main/kotlin/com/platos/api/exam/ResultQueries.kt` (duas linhas, em `record`)
- Test: `apps/api/src/test/kotlin/com/platos/api/db/GradingResultCurrentViewTest.kt`

**Interfaces:**
- Produces (banco): `grading_result.points`/`answer_observation.earned` `numeric(8,2)`; `grading_result.path text null`, `grading_result.completes_capture_id text null`; `answer_observation.answer_kind` aceita `'discursiva_corrigida'`; view `public.grading_result_current` (mesmas colunas de `grading_result`, mais `path` e `completes_capture_id`), `security_invoker`.
- Produces (jOOQ, regenerado): `GRADING_RESULT.POINTS` e `ANSWER_OBSERVATION.EARNED` passam a `BigDecimal`; `GRADING_RESULT.PATH` e `GRADING_RESULT.COMPLETES_CAPTURE_ID` passam a existir.

- [ ] **Step 1: Escrever o teste que falha**

```kotlin
package com.platos.api.db

import com.platos.api.support.PostgresSupport
import org.jooq.exception.DataAccessException
import java.math.BigDecimal
import java.sql.SQLException
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A nota do professor no banco (`slice-5c-2-a-nota-do-professor`, ADR-0021), medida contra Postgres
 * real e pelo caminho da aplicacao (`app_backend` + RLS), nunca como dono.
 *
 * Tres coisas: as guardas novas (`check`) recusam o que dizem recusar **pela constraint que a migration
 * nomeia**; `numeric(8,2)` **arredonda em silencio** (e por isso a recusa de 3 casas e do dominio); e a
 * view `grading_result_current` escolhe a revisao certa nas ordens de chegada que o modelo offline produz.
 */
class GradingResultCurrentViewTest {

    private lateinit var usuario: UUID
    private lateinit var org: UUID
    private lateinit var prova: UUID

    private val conteudo = """{"meta":{"exam_id":"prova-n"},"items":[],"answer_key":[]}"""
    private val hashDoPacote = PostgresSupport.sha256Hex(conteudo)

    @BeforeTest
    fun setUp() {
        PostgresSupport.start()
        PostgresSupport.reset()

        usuario = PostgresSupport.createUser("sub-nota")
        org = PostgresSupport.createOrganization(name = "Escola")
        PostgresSupport.addMembership(usuario, org, "teacher")
        prova = PostgresSupport.createExam(org, shortId = "prova-n", title = "Prova N")
        PostgresSupport.publishPackage(org, prova, conteudo, hashDoPacote)
    }

    /** Grava pelo caminho da aplicacao. `caminho` e `completa` so existem na linha `teacher`. */
    private fun gravar(
        capture: String,
        revisao: Int,
        token: String? = "aluno-1",
        origem: String = "omr",
        completa: String? = null,
        caminho: String? = if (origem == "teacher") "image" else null,
        pontos: String = "1.00",
        maximo: Int = 4,
        organizacao: UUID = org,
        comoUsuario: UUID = usuario,
    ): UUID = PostgresSupport.tenancy.asUser(comoUsuario) { ctx ->
        ctx.fetchOne(
            """
            insert into grading_result (
                organization_id, exam_id, student_token, revision, capture_id, origin, path,
                completes_capture_id, package_hash, variant_id, points, max_score, closed, captured_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'v1', ?, ?, false, now())
            returning id
            """.trimIndent(),
            organizacao, prova, token, revisao, capture, origem, caminho, completa,
            hashDoPacote, BigDecimal(pontos), maximo,
        )!!.get(0, UUID::class.java)
    }

    private fun gravarObservacao(
        resultado: UUID,
        item: String,
        tipo: String,
        alternativas: Array<String>,
        vale: Int,
        rendeu: String,
    ) = PostgresSupport.tenancy.asUser(usuario) { ctx ->
        ctx.execute(
            """
            insert into answer_observation (
                organization_id, grading_result_id, item_id, answer_kind, answer_options, worth, earned
            ) values (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            org, resultado, item, tipo, alternativas, vale, BigDecimal(rendeu),
        )
    }

    private fun correntes(): List<String> = PostgresSupport.adminDataSource.connection.use { c ->
        c.prepareStatement(
            "select capture_id from grading_result_current where exam_id = ? order by capture_id",
        ).use { s ->
            s.setObject(1, prova)
            s.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }
    }

    private fun umTexto(sql: String): String = PostgresSupport.adminDataSource.connection.use { c ->
        c.createStatement().use { s -> s.executeQuery(sql).use { it.next(); it.getString(1) } }
    }

    private fun contarResultados(): Int = umTexto("select count(*) from grading_result").toInt()

    private fun causaSql(erro: DataAccessException): SQLException =
        generateSequence(erro.cause) { it.cause }.filterIsInstance<SQLException>().first()

    private fun assertRecusadaPor(constraint: String, bloco: () -> Unit) {
        val erro = assertFailsWith<DataAccessException> { bloco() }
        val causa = causaSql(erro)
        assertEquals("23514", causa.sqlState, "esperava check constraint: ${causa.message}")
        assertTrue(constraint in causa.message.orEmpty(), "esperava '$constraint': ${causa.message}")
    }

    // ------------------------------------------------------------------ as guardas novas

    @Test
    fun `as constraints novas existem no catalogo, pelo nome`() {
        val esperadas = setOf(
            "grading_result_origem_do_professor",
            "grading_result_caminho_conhecido",
            "grading_result_completa_captura_nao_vazia",
            "answer_observation_answer_kind_check",
            "answer_observation_pendente_sem_ponto",
            "answer_observation_discursiva_sem_alternativa",
        )
        val encontradas = PostgresSupport.adminDataSource.connection.use { c ->
            c.prepareStatement(
                """
                select conname from pg_constraint
                where conrelid in ('public.grading_result'::regclass, 'public.answer_observation'::regclass)
                """.trimIndent(),
            ).use { s -> s.executeQuery().use { rs -> buildSet { while (rs.next()) add(rs.getString(1)) } } }
        }
        assertTrue(esperadas.isNotEmpty(), "a lista esperada nao pode estar vazia")
        assertEquals(emptySet(), esperadas - encontradas)
    }

    @Test
    fun `linha teacher sem caminho, ou sem a captura que completa, e recusada`() {
        assertRecusadaPor("grading_result_origem_do_professor") {
            gravar("n1", 1, origem = "teacher", completa = "p1", caminho = null)
        }
        assertRecusadaPor("grading_result_origem_do_professor") {
            gravar("n2", 1, origem = "teacher", completa = null, caminho = "image")
        }
        assertEquals(0, contarResultados())
    }

    @Test
    fun `linha automatica com caminho ou com captura que completa e recusada`() {
        assertRecusadaPor("grading_result_origem_do_professor") {
            gravar("a1", 1, origem = "omr", caminho = "image")
        }
        assertRecusadaPor("grading_result_origem_do_professor") {
            gravar("a2", 1, origem = "omr", completa = "p1")
        }
    }

    @Test
    fun `caminho text e de fatia posterior e o banco o recusa`() {
        assertRecusadaPor("grading_result_caminho_conhecido") {
            gravar("n1", 1, origem = "teacher", completa = "p1", caminho = "text")
        }
    }

    @Test
    fun `numeric 8,2 arredonda 3 casas em silencio, e por isso a recusa e do dominio`() {
        gravar("r1", 1, pontos = "1.333")

        // MEDIDO: o banco nao recusa; ele grava 1.33. Se o dominio deixasse passar `1.333`, a nota
        // gravada nao seria a que o professor digitou, e nada acusaria.
        assertEquals("1.33", umTexto("select points::text from grading_result where capture_id = 'r1'"))
    }

    @Test
    fun `a evidencia da discursiva guarda o decimal exato`() {
        val resultado = gravar("n1", 2, origem = "teacher", completa = "p1", pontos = "2.75")

        gravarObservacao(resultado, "d1", "discursiva_corrigida", arrayOf(), vale = 3, rendeu = "1.75")

        assertEquals(
            "1.75",
            umTexto("select earned::text from answer_observation where item_id = 'd1'"),
        )
    }

    @Test
    fun `discursiva corrigida pode render ponto, e a objetiva pendente continua nao podendo`() {
        val resultado = gravar("n1", 2, origem = "teacher", completa = "p1")

        // A excecao do check `pendente_sem_ponto` e so para o tipo novo.
        gravarObservacao(resultado, "d1", "discursiva_corrigida", arrayOf(), vale = 3, rendeu = "3.00")
        assertRecusadaPor("answer_observation_pendente_sem_ponto") {
            gravarObservacao(resultado, "q4", "indecisa", arrayOf("A"), vale = 1, rendeu = "0.50")
        }
    }

    @Test
    fun `discursiva corrigida nao nomeia alternativa nem passa do valor`() {
        val resultado = gravar("n1", 2, origem = "teacher", completa = "p1")

        assertRecusadaPor("answer_observation_discursiva_sem_alternativa") {
            gravarObservacao(resultado, "d1", "discursiva_corrigida", arrayOf("A"), vale = 3, rendeu = "1.00")
        }
        assertRecusadaPor("answer_observation_na_escala") {
            gravarObservacao(resultado, "d2", "discursiva_corrigida", arrayOf(), vale = 3, rendeu = "3.01")
        }
    }

    // ------------------------------------------------------------------ a revisao corrente

    @Test
    fun `a parcial que chega depois da nota do professor nao e a corrente, e continua legivel`() {
        gravar("nota-1", 1, origem = "teacher", completa = "parcial-1")
        gravar("parcial-1", 2)

        assertEquals(listOf("nota-1"), correntes())
        assertEquals(2, contarResultados(), "as duas continuam gravadas")
    }

    @Test
    fun `a parcial que chega antes continua legivel e a nota do professor e a corrente`() {
        gravar("parcial-1", 1)
        gravar("nota-1", 2, origem = "teacher", completa = "parcial-1")

        assertEquals(listOf("nota-1"), correntes())
        assertEquals(2, contarResultados())
    }

    @Test
    fun `captura nova depois da nota do professor e a corrente, e a nota anterior continua legivel`() {
        gravar("nota-1", 1, origem = "teacher", completa = "parcial-1")
        gravar("parcial-1", 2)
        gravar("parcial-2", 3)

        assertEquals(listOf("parcial-2"), correntes())
        assertEquals(3, contarResultados())
    }

    @Test
    fun `nova nota do professor para a mesma captura e a corrente`() {
        gravar("parcial-1", 1)
        gravar("nota-1", 2, origem = "teacher", completa = "parcial-1")
        gravar("nota-2", 3, origem = "teacher", completa = "parcial-1")

        assertEquals(listOf("nota-2"), correntes())
    }

    @Test
    fun `a nota da captura nova vence a captura nova`() {
        gravar("parcial-1", 1)
        gravar("nota-1", 2, origem = "teacher", completa = "parcial-1")
        gravar("parcial-2", 3)
        gravar("nota-2", 4, origem = "teacher", completa = "parcial-2")

        assertEquals(listOf("nota-2"), correntes())
    }

    @Test
    fun `folha avulsa segue a propria parcial e nao se mistura com outra avulsa`() {
        gravar("avulsa-a", 1, token = null)
        gravar("avulsa-b", 2, token = null)
        gravar("nota-a", 3, token = null, origem = "teacher", completa = "avulsa-a")

        // A avulsa B nao foi corrigida: continua corrente, e a A foi substituida pela nota dela.
        assertEquals(listOf("avulsa-b", "nota-a"), correntes())
    }

    @Test
    fun `folha avulsa, a parcial que chega depois da nota nao a substitui`() {
        gravar("nota-a", 1, token = null, origem = "teacher", completa = "avulsa-a")
        gravar("avulsa-a", 2, token = null)

        assertEquals(listOf("nota-a"), correntes())
    }

    @Test
    fun `a view respeita a organizacao, o forasteiro nao le a folha de outra escola`() {
        gravar("parcial-1", 1)
        val forasteiro = PostgresSupport.createUser("sub-forasteiro")
        val outraOrg = PostgresSupport.createOrganization(kind = "school", name = "Outra Escola")
        PostgresSupport.addMembership(forasteiro, outraOrg, "teacher")

        val visto = { quem: UUID ->
            PostgresSupport.tenancy.asUser(quem) { ctx ->
                ctx.fetch("select capture_id from grading_result_current").size
            }
        }

        assertEquals(1, visto(usuario), "o dono precisa ver a propria folha (piso: sem isto o zero abaixo nao prova nada)")
        assertEquals(0, visto(forasteiro), "a view vazaria a folha de outra organizacao")
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew :apps:api:test`
Expected: FAIL. As mensagens esperadas: `column "path" of relation "grading_result" does not exist` (o `insert` do helper), e `relation "grading_result_current" does not exist`. **Leia as mensagens (P12):** são as duas, e nenhuma é erro de compilação do teste. Os testes antigos continuam verdes.

- [ ] **Step 3: Escrever a migration**

Arquivo `supabase/migrations/20261001120000_nota_do_professor.sql`:

```sql
-- A nota do professor (slice-5c-2-a-nota-do-professor, ADR-0021).
--
-- O fato continua em `grading_result` e `answer_observation` (§11 ja nomeia `grading_result(origin, path)`).
-- Nada aqui relaxa as invariantes das duas tabelas: append-only por gatilho, RLS forcada, organization_id.

-- ---------------------------------------------------------------------------
-- 1. A pontuacao deixa de ser inteira. `numeric(8,2)` cobre 0..999999.99, que e o alcance de `Pontos`
-- no dominio. ATENCAO: `numeric(8,2)` ARREDONDA 3 casas em silencio no insert (medido em
-- GradingResultCurrentViewTest); a recusa de mais de 2 casas e do dominio, antes do SQL.
-- `max_score` e `worth` seguem `int`: o pacote e inteiro e imutavel. O alargamento de int para numeric
-- preserva todo valor existente (3 passa a ser 3.00). Reescrever a tabela nao dispara os gatilhos de
-- UPDATE/DELETE, que sao de linha.
-- ---------------------------------------------------------------------------
alter table public.grading_result
    alter column points type numeric(8,2) using points::numeric(8,2);

alter table public.answer_observation
    alter column earned type numeric(8,2) using earned::numeric(8,2);

-- ---------------------------------------------------------------------------
-- 2. Quem corrigiu pelo olho, e qual captura da parcial a nota do professor completa.
-- Nulos nas linhas automaticas (`omr`); obrigatorios na linha `teacher`. O check e a guarda do banco,
-- escrita independente de quem escreve as linhas (ADR-0015, decisao 3).
-- ---------------------------------------------------------------------------
alter table public.grading_result
    add column path text,
    add column completes_capture_id text;

alter table public.grading_result
    add constraint grading_result_origem_do_professor check (
        (origin = 'teacher' and path is not null and completes_capture_id is not null)
        or (origin <> 'teacher' and path is null and completes_capture_id is null)
    ),
    add constraint grading_result_caminho_conhecido check (path is null or path = 'image'),
    add constraint grading_result_completa_captura_nao_vazia
        check (completes_capture_id is null or length(trim(completes_capture_id)) > 0);

-- ---------------------------------------------------------------------------
-- 3. A evidencia da discursiva corrigida. Quem enumera os valores de answer_kind sao tres registros que
-- se conhecem: o dominio (`AnswerKind`), este check, e tools/parity/answer-kind.mjs, que le o check da
-- ULTIMA migration que o declara.
-- ---------------------------------------------------------------------------
alter table public.answer_observation drop constraint answer_observation_answer_kind_check;
alter table public.answer_observation
    add constraint answer_observation_answer_kind_check
    check (answer_kind in ('marcada', 'em_branco', 'multipla_marcacao', 'indecisa', 'discursiva_corrigida'));

-- A excecao e so para o tipo novo: objetiva que depende de revisao continua nao rendendo ponto.
alter table public.answer_observation drop constraint answer_observation_pendente_sem_ponto;
alter table public.answer_observation
    add constraint answer_observation_pendente_sem_ponto
    check (answer_kind in ('marcada', 'em_branco', 'discursiva_corrigida') or earned = 0);

alter table public.answer_observation
    add constraint answer_observation_discursiva_sem_alternativa
    check (answer_kind <> 'discursiva_corrigida' or cardinality(answer_options) = 0);

-- ---------------------------------------------------------------------------
-- 4. A revisao corrente, derivada: a regra "revisao humana vence a automatica da mesma captura, em
-- qualquer ordem de chegada" mora aqui, num lugar so. Nada e gravado nem atualizado (append-only).
--
-- Uma revisao automatica nao e elegivel se alguma revisao `teacher` da mesma prova declara completar a
-- captura dela. Entre as elegiveis, a de maior `revision` e a corrente. A chave da folha e o token; na
-- folha avulsa (token nulo) e a captura da parcial (`completes_capture_id` na nota do professor, o
-- proprio `capture_id` na automatica), para a nota seguir a propria parcial sem se misturar com outra
-- avulsa.
--
-- `security_invoker`: a view roda com os privilegios de QUEM CONSULTA, e a RLS de `grading_result`
-- continua valendo. Sem isso, cada escola leria as folhas das outras.
-- ---------------------------------------------------------------------------
create view public.grading_result_current
    with (security_invoker = true) as
select distinct on (
           r.exam_id,
           r.student_token,
           case when r.student_token is null then coalesce(r.completes_capture_id, r.capture_id) end
       )
       r.id, r.organization_id, r.exam_id, r.student_token, r.revision, r.capture_id, r.origin,
       r.path, r.completes_capture_id, r.package_hash, r.variant_id, r.points, r.max_score,
       r.closed, r.captured_at, r.created_at
from public.grading_result r
where r.origin = 'teacher'
   or not exists (
        select 1
        from public.grading_result t
        where t.exam_id = r.exam_id
          and t.origin = 'teacher'
          and t.completes_capture_id = r.capture_id
   )
order by r.exam_id,
         r.student_token,
         case when r.student_token is null then coalesce(r.completes_capture_id, r.capture_id) end,
         r.revision desc;

alter view public.grading_result_current owner to app_owner;
grant select on public.grading_result_current to app_backend;

comment on view public.grading_result_current is
    'A revisao corrente de cada folha: a nota do professor vence a revisao automatica da mesma captura, '
    'em qualquer ordem de chegada. Derivada, nao gravada.';
```

- [ ] **Step 4: Ajuste mecânico do jOOQ em `ResultQueries.kt`**

Em `record`, duas linhas (o jOOQ regenerado agora tipa as colunas como `BigDecimal`):

```kotlin
// antes
            .set(GRADING_RESULT.POINTS, campos.points)
// depois
            .set(GRADING_RESULT.POINTS, campos.points.toBigDecimal())
```

```kotlin
// antes
                .set(ANSWER_OBSERVATION.EARNED, outcome.earned)
// depois
                .set(ANSWER_OBSERVATION.EARNED, outcome.earned.toBigDecimal())
```

- [ ] **Step 5: Rodar e ver passar**

Run: `./gradlew :apps:api:test`
Expected: `BUILD SUCCESSFUL`, com a suíte **inteira** da API (a `generateJooq` roda de novo contra a migration; exige Docker). Confira: o XML de `GradingResultCurrentViewTest` com `timestamp` de agora e os 16 testes; `ResultRouteTest` e `GradingResultTableTest` verdes **sem asserção alterada**. Se um guarda de catálogo (`RetentionDeclarationTest`, `ConnectionRoleTest`) reprovar por causa da view, **leia a mensagem e diga qual guarda caiu** antes de agir: ajuste a migration, nunca a guarda (P12).

- [ ] **Step 6: Commit**

```bash
git add supabase/migrations/20261001120000_nota_do_professor.sql apps/api/src/main/kotlin/com/platos/api/exam/ResultQueries.kt apps/api/src/test/kotlin/com/platos/api/db/GradingResultCurrentViewTest.kt
git commit -m "feat(db): a nota do professor em grading_result, e a view da revisão corrente" -m "numeric(8,2) em points/earned; path e completes_capture_id com check de origem; answer_kind discursiva_corrigida; view grading_result_current (security_invoker). Ajuste mecânico do jOOQ (BigDecimal) no mesmo commit para o build compilar." -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 5: O contrato — `AnswerKind`, `GradedResultSubmissionDto`, a guarda do `answer_kind` e os literais dos dois lados

**Files:**
- Modify: `packages/domain/src/commonMain/kotlin/com/platos/domain/transport/AnswerKind.kt`
- Create: `packages/domain/src/commonMain/kotlin/com/platos/domain/transport/GradedResultDto.kt`
- Modify: `tools/parity/answer-kind.mjs`
- Test: `packages/domain/src/commonTest/kotlin/com/platos/domain/transport/GradedResultSubmissionDtoTest.kt`
- Test: `apps/api/src/test/kotlin/com/platos/api/http/GradedResultContractTest.kt`
- Test: `apps/android/src/test/kotlin/com/platos/android/api/GradedResultDtoTest.kt`

**Interfaces:**
- Produces: `AnswerKind.DISCURSIVA_CORRIGIDA` (`"discursiva_corrigida"`, último de `TODOS`); `EssayGradeDto(itemId: String, earned: String)`; `GradedResultSubmissionDto(captureId, completesCaptureId, studentToken: String? = null, packageHash, variantId, origin, path, points: String, maxScore: Int, closed: Boolean, capturedAt, observations: List<AnswerObservationDto>, essayGrades: List<EssayGradeDto>)`.

- [ ] **Step 1: Escrever os testes que falham**

`packages/domain/src/commonTest/kotlin/com/platos/domain/transport/GradedResultSubmissionDtoTest.kt`:

```kotlin
package com.platos.domain.transport

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O corpo da nota do professor, como o literal que o fio carrega (`slice-5c-2-a-nota-do-professor`).
 *
 * O literal e escrito a mao nos tres lados (dominio, servidor, aparelho): `tools/parity/fio.mjs` reprova
 * no CI o tipo de `transport` que nao o tenha nos DOIS lados do fio.
 */
class GradedResultSubmissionDtoTest {

    private val corpo = """
        {"capture_id":"cap-nota-1","completes_capture_id":"cap-parcial-1","student_token":"aluno-1",
        "package_hash":"${"a".repeat(64)}","variant_id":"v1","origin":"teacher","path":"image",
        "points":"2.75","max_score":4,"closed":true,"captured_at":"2026-09-17T12:00:00Z",
        "observations":[{"item_id":"q01","answer_kind":"marcada","answer_options":["A"],"worth":1,"earned":1}],
        "essay_grades":[{"item_id":"d1","earned":"1.75"}]}
    """.trimIndent().replace("\n", "")

    @Test
    fun `o literal decodifica, e a pontuacao viaja como string`() {
        val dto = Json.decodeFromString(GradedResultSubmissionDto.serializer(), corpo)

        assertEquals("cap-parcial-1", dto.completesCaptureId)
        assertEquals("teacher", dto.origin)
        assertEquals("image", dto.path)
        assertEquals("2.75", dto.points)
        assertEquals(listOf(EssayGradeDto("d1", "1.75")), dto.essayGrades)
    }

    @Test
    fun `ida e volta preserva o corpo`() {
        val dto = Json.decodeFromString(GradedResultSubmissionDto.serializer(), corpo)

        assertEquals(dto, Json.decodeFromString(GradedResultSubmissionDto.serializer(), Json.encodeToString(GradedResultSubmissionDto.serializer(), dto)))
    }

    @Test
    fun `answer_kind passa a ter cinco valores, e o novo e o ultimo`() {
        assertEquals(5, AnswerKind.TODOS.size)
        assertEquals("discursiva_corrigida", AnswerKind.TODOS.last())
        assertEquals(AnswerKind.DISCURSIVA_CORRIGIDA, AnswerKind.TODOS.last())
    }
}
```

`apps/api/src/test/kotlin/com/platos/api/http/GradedResultContractTest.kt` (o literal do **servidor**):

```kotlin
package com.platos.api.http

import com.platos.domain.transport.EssayGradeDto
import com.platos.domain.transport.GradedResultSubmissionDto
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O corpo da nota do professor, prendido contra um literal escrito a mao **deste lado do fio**.
 *
 * Desserializar o corpo com o proprio tipo nao prende nada (P4): o que prende o nome de um campo e o JSON
 * com esse nome digitado. O outro lado do par esta em
 * `apps/android/.../GradedResultDtoTest`, e `tools/parity/fio.mjs` exige os dois.
 */
class GradedResultContractTest {

    private val corpo = """
        {"capture_id":"cap-nota-1","completes_capture_id":"cap-parcial-1","student_token":"aluno-1",
        "package_hash":"${"a".repeat(64)}","variant_id":"v1","origin":"teacher","path":"image",
        "points":"2.75","max_score":4,"closed":true,"captured_at":"2026-09-17T12:00:00Z",
        "observations":[{"item_id":"q01","answer_kind":"marcada","answer_options":["A"],"worth":1,"earned":1}],
        "essay_grades":[{"item_id":"d1","earned":"1.75"}]}
    """.trimIndent().replace("\n", "")

    @Test
    fun `o servidor entende o literal da nota do professor`() {
        val dto = Json { ignoreUnknownKeys = true }.decodeFromString<GradedResultSubmissionDto>(corpo)

        assertEquals("cap-nota-1", dto.captureId)
        assertEquals("cap-parcial-1", dto.completesCaptureId)
        assertEquals("2.75", dto.points)
        assertEquals(listOf(EssayGradeDto("d1", "1.75")), dto.essayGrades)
    }
}
```

`apps/android/src/test/kotlin/com/platos/android/api/GradedResultDtoTest.kt` (o literal do **aparelho**, **só teste**):

```kotlin
package com.platos.android.api

import com.platos.domain.transport.EssayGradeDto
import com.platos.domain.transport.GradedResultSubmissionDto
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * O corpo da nota do professor, prendido contra um literal escrito a mao **deste lado do fio**.
 *
 * O aparelho ainda nao envia a nota do professor (e a 5c-3). Este teste existe porque o contrato mora em
 * `packages/domain`, o aparelho compila contra ele, e `tools/parity/fio.mjs` reprova no CI todo tipo de
 * `transport` sem literal nos dois lados. O par esta em
 * `apps/api/.../GradedResultContractTest`.
 */
class GradedResultDtoTest {

    private val corpo = """
        {"capture_id":"cap-nota-1","completes_capture_id":"cap-parcial-1","student_token":"aluno-1",
        "package_hash":"${"a".repeat(64)}","variant_id":"v1","origin":"teacher","path":"image",
        "points":"2.75","max_score":4,"closed":true,"captured_at":"2026-09-17T12:00:00Z",
        "observations":[{"item_id":"q01","answer_kind":"marcada","answer_options":["A"],"worth":1,"earned":1}],
        "essay_grades":[{"item_id":"d1","earned":"1.75"}]}
    """.trimIndent().replace("\n", "")

    @Test
    fun `o aparelho entende o literal da nota do professor`() {
        val dto = Json.decodeFromString(GradedResultSubmissionDto.serializer(), corpo)

        assertEquals("cap-nota-1", dto.captureId)
        assertEquals("2.75", dto.points)
        assertEquals(listOf(EssayGradeDto("d1", "1.75")), dto.essayGrades)
    }
}
```

Run: `./gradlew :packages:domain:jvmTest`
Expected: FAIL na compilação: `Unresolved reference: GradedResultSubmissionDto` (e `DISCURSIVA_CORRIGIDA`).

- [ ] **Step 2: Implementar o contrato**

Em `AnswerKind.kt`:

```kotlin
// acrescentar a constante, depois de INDECISA
    const val DISCURSIVA_CORRIGIDA: String = "discursiva_corrigida"

// e a lista (a ordem e a do check da migration, que o conferidor compara)
    val TODOS: List<String> = listOf(MARCADA, EM_BRANCO, MULTIPLA_MARCACAO, INDECISA, DISCURSIVA_CORRIGIDA)
```

Atualize também o KDoc do objeto: troque "Os quatro valores" por "Os cinco valores" e a frase final "**Nao ha um quinto valor**" por: "`discursiva_corrigida` nao e um `QuestionAnswer`: a resposta discursiva e uma imagem que fica no aparelho, e o que viaja e a nota do professor (`slice-5c-2-a-nota-do-professor`). O `when` sobre `QuestionAnswer` abaixo nao muda."

`packages/domain/src/commonMain/kotlin/com/platos/domain/transport/GradedResultDto.kt`:

```kotlin
package com.platos.domain.transport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A nota que o professor deu a uma discursiva, como viaja: o item e a pontuacao **em string**
 * (`"1.75"`), porque numero JSON passa por `Double` e `0.1 + 0.2 != 0.3` (ADR-0021).
 */
@Serializable
data class EssayGradeDto(
    @SerialName("item_id") val itemId: String,
    val earned: String,
)

/**
 * Contrato de `POST /organizations/{organizationId}/exams/{shortId}/results/graded` — **um** declarante
 * (ADR-0015; `slice-5c-2-a-nota-do-professor`, ADR-0021).
 *
 * **E autocontido**: leva a parte objetiva ([observations]) e a nota de cada discursiva
 * ([essayGrades]), de modo que o servidor o aceita mesmo sem ter recebido antes a parcial da captura.
 * [completesCaptureId] diz **qual captura da parcial** a nota completa; e com ele que a revisao do
 * professor prevalece sobre a automatica da mesma captura, em qualquer ordem de chegada.
 *
 * **[captureId] e a chave de idempotencia desta correcao**, distinta da de [completesCaptureId]: corrigir
 * de novo a mesma captura e outra correcao, logo outro valor, logo revisao nova.
 *
 * [origin] e [path] chegam declarados para que o servidor os recuse quando forem de fatia posterior
 * (`ai`, `text`): hoje so `teacher` e `image`.
 *
 * [points] e o total **declarado**, em string; o servidor o recalcula e compara. [closed] idem.
 *
 * **Nao ha imagem, nome de arquivo, nome, turma nem matricula, e a ausencia e o requisito** (I5; spec
 * `result-sync`). A resposta fica no aparelho (politica §6.4).
 */
@Serializable
data class GradedResultSubmissionDto(
    @SerialName("capture_id") val captureId: String,
    @SerialName("completes_capture_id") val completesCaptureId: String,
    @SerialName("student_token") val studentToken: String? = null,
    @SerialName("package_hash") val packageHash: String,
    @SerialName("variant_id") val variantId: String,
    val origin: String,
    val path: String,
    val points: String,
    @SerialName("max_score") val maxScore: Int,
    val closed: Boolean,
    @SerialName("captured_at") val capturedAt: String,
    val observations: List<AnswerObservationDto>,
    @SerialName("essay_grades") val essayGrades: List<EssayGradeDto>,
)
```

Run: `./gradlew :packages:domain:jvmTest`
Expected: `BUILD SUCCESSFUL` (os três testes de `GradedResultSubmissionDtoTest`).

- [ ] **Step 3: Ver a guarda do `answer_kind` falhar, e entender por quê**

Run: `node tools/parity/answer-kind.mjs; echo "exit=$?"`
Expected: `exit=1`, com `o dominio declara 'discursiva_corrigida' e o check da migration nao o admite`. **Isso é a prova de que a guarda ainda lê só a migration antiga** (que tem quatro valores) — e o motivo do Step 4. (A capacidade de o `fio.mjs` falhar é vista na Task 8, M6.)

- [ ] **Step 4: A guarda do `answer_kind` passa a ler a última migration que o declara**

Em `tools/parity/answer-kind.mjs`:

```js
// linha 1 — antes
import { readFileSync } from 'node:fs';
// depois
import { readdirSync, readFileSync } from 'node:fs';
```

```js
// antes
const FONTE_MIGRATION = 'supabase/migrations/20260917134500_result_tables.sql';
// depois
const DIR_MIGRATIONS = 'supabase/migrations';
```

```js
// antes
const sql = ler(FONTE_MIGRATION);
const check = sql.match(/check \(answer_kind in \(([^)]*)\)\)/);
if (!check) {
  console.error(`nao achei o \`check (answer_kind in (...))\` em ${FONTE_MIGRATION} — a forma mudou`);
  process.exit(2);
}
const doCheck = check[1].split(',').map((s) => s.trim().replace(/^'|'$/g, ''));
dizer(`migration: ${doCheck.join(', ')}`);

// depois
// O `check` e redeclarado por migration nova (drop + add): vale o da ULTIMA migration que o declara,
// que e o que o banco tem depois de aplicar todas em ordem. Ler so a primeira daria a lista antiga.
const migrations = readdirSync(new URL(`${DIR_MIGRATIONS}/`, new URL('../../', import.meta.url)))
  .filter((arquivo) => arquivo.endsWith('.sql'))
  .sort();
let check = null;
let fonteDoCheck = null;
for (const arquivo of migrations) {
  const achado = ler(`${DIR_MIGRATIONS}/${arquivo}`).match(/check \(answer_kind in \(([^)]*)\)\)/);
  if (achado) {
    check = achado;
    fonteDoCheck = arquivo;
  }
}
if (!check) {
  console.error(`nao achei o \`check (answer_kind in (...))\` em nenhuma migration de ${DIR_MIGRATIONS} — a forma mudou`);
  process.exit(2);
}
const doCheck = check[1].split(',').map((s) => s.trim().replace(/^'|'$/g, ''));
dizer(`migration: ${doCheck.join(', ')}  (${fonteDoCheck})`);
```

- [ ] **Step 5: Rodar tudo o que se espera ver verde**

Run: `node tools/parity/fio.mjs; echo "exit=$?"` → `exit=0`; leia a lista impressa e confira que o literal que satisfez cada par é o **de `GradedResultContractTest`** (servidor) e o de **`GradedResultDtoTest`** (aparelho), e não um de outro assunto.
Run: `node tools/parity/answer-kind.mjs; echo "exit=$?"` → `exit=0`, `os 5 valores de answer_kind concordam`.
Run: `node tools/parity/answer-kind.mjs --esperado marcada,em_branco,multipla_marcacao,rasurada; echo "exit=$?"` → `exit=1` (a guarda continua capaz de falhar; é o passo do CI).
Run: `./gradlew :packages:domain:jvmTest :apps:api:test :apps:android:testDebugUnitTest` → `BUILD SUCCESSFUL`. Confira o `timestamp` dos três XMLs novos.

- [ ] **Step 6: Commit**

```bash
git add packages/domain/src/commonMain/kotlin/com/platos/domain/transport/AnswerKind.kt packages/domain/src/commonMain/kotlin/com/platos/domain/transport/GradedResultDto.kt packages/domain/src/commonTest/kotlin/com/platos/domain/transport/GradedResultSubmissionDtoTest.kt apps/api/src/test/kotlin/com/platos/api/http/GradedResultContractTest.kt apps/android/src/test/kotlin/com/platos/android/api/GradedResultDtoTest.kt tools/parity/answer-kind.mjs
git commit -m "feat(contrato): GradedResultSubmissionDto, discursiva_corrigida e a guarda do answer_kind" -m "Contrato novo no domínio (dono único, ADR-0015); literais nos dois lados do fio (só teste no aparelho); answer-kind.mjs lê o check da última migration que o declara." -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Refatoração sem mudança de comportamento (API)

**Files:**
- Modify: `apps/api/src/main/kotlin/com/platos/api/http/dto/ResultDto.kt`
- Modify: `apps/api/src/main/kotlin/com/platos/api/exam/ProvenienciaDoResultado.kt`
- Modify: `apps/api/src/main/kotlin/com/platos/api/exam/ResultQueries.kt`

**Interfaces:**
- Produces (para a Task 7): `internal fun AnswerObservationDto.paraOutcome(): QuestionOutcome` e `internal fun QuestionOutcome.paraPendencia(): PendingQuestion` (em `com.platos.api.http.dto`); em `ProvenienciaDoResultado.kt`, privados: `conferirPacoteEVariante`, `derivarDiscursivas`, `maximoObjetivo`; em `ResultQueries`, privado: `gravar(ctx, organizationId, examId, g: Gravacao): Int`, com `internal class Gravacao` e `internal class Evidencia`.

Esta tarefa **não adiciona teste**: o que a prova é a suíte existente (`ResultRouteTest`, `GradingResultTableTest`) verde **sem asserção mudada**, e o diff sem comportamento novo (P19, P25).

- [ ] **Step 1: Visibilidade em `ResultDto.kt`**

Troque `private fun QuestionOutcome.paraPendencia(): PendingQuestion = PendingQuestion(` por `internal fun QuestionOutcome.paraPendencia(): PendingQuestion = PendingQuestion(` e `private fun AnswerObservationDto.paraOutcome(): QuestionOutcome = QuestionOutcome(` por `internal fun AnswerObservationDto.paraOutcome(): QuestionOutcome = QuestionOutcome(`. Nada mais muda nesse arquivo.

- [ ] **Step 2: Extrair os helpers em `ProvenienciaDoResultado.kt`**

Mantenha **todo o KDoc** das funções públicas. Substitua o corpo de `conferirProveniencia` (a partir de `val packageHash = when ...`) por:

```kotlin
fun conferirProveniencia(pacote: PackageContent, apuracao: ApuracaoSubmetida): Proveniencia {
    val packageHash = when (apuracao) {
        is ApuracaoSubmetida.Completa -> apuracao.nota.packageHash
        is ApuracaoSubmetida.Parcial -> apuracao.parte.packageHash
    }
    val variantId = when (apuracao) {
        is ApuracaoSubmetida.Completa -> apuracao.nota.variantId
        is ApuracaoSubmetida.Parcial -> apuracao.parte.variantId
    }

    val base = conferirPacoteEVariante(pacote, packageHash, variantId)
    if (base is ConferenciaDoPacote.Falha) return Proveniencia.NaoConfere(base.motivo)
    base as ConferenciaDoPacote.Ok

    return when (apuracao) {
        is ApuracaoSubmetida.Completa -> Proveniencia.Confere(ApuracaoParaEnvio.Completa(apuracao.nota))
        is ApuracaoSubmetida.Parcial -> conferirParcial(base.publicado, base.variante, apuracao.parte)
    }
}

/** O pacote publicado decodificado e a variante declarada, ou o motivo de o corpo nao fechar com eles. */
private sealed interface ConferenciaDoPacote {
    data class Ok(val publicado: ExamPackage, val variante: PackageVariant) : ConferenciaDoPacote
    data class Falha(val motivo: String) : ConferenciaDoPacote
}

/**
 * As duas travas de proveniencia, na ordem que o KDoc de [conferirProveniencia] justifica: o hash vem
 * primeiro porque e ele que decide se [pacote] e o artefato certo; so depois faz sentido perguntar o que
 * ele declara. **As mensagens sao as de antes, byte a byte**: os testes de rota as leem.
 */
private fun conferirPacoteEVariante(
    pacote: PackageContent,
    packageHash: String,
    variantId: String,
): ConferenciaDoPacote {
    if (packageHash != pacote.contentHash) {
        return ConferenciaDoPacote.Falha(
            "o resultado diz ter sido apurado contra o pacote `$packageHash`, " +
                "e o pacote publicado desta prova e `${pacote.contentHash}`",
        )
    }

    // A unica lista de variantes desta prova. Uma segunda — coluna, tabela ou constante — seria o
    // segundo oraculo que a decisao 6 do design recusa.
    val publicado = ExamPackage.JSON.decodeFromString(ExamPackage.serializer(), pacote.content)
    val declaradas = publicado.variants.map { it.variantId }
    val variante = publicado.variants.firstOrNull { it.variantId == variantId }
        ?: return ConferenciaDoPacote.Falha(
            "o resultado diz a variante `$variantId`, e o pacote publicado desta prova " +
                "declara ${declaradas.joinToString(", ") { "`$it`" }.ifEmpty { "nenhuma" }}",
        )

    return ConferenciaDoPacote.Ok(publicado, variante)
}

private sealed interface Derivacao {
    data class Ok(val discursivas: List<AwaitingEssay>) : Derivacao
    data class Falha(val motivo: String) : Derivacao
}

/** As discursivas da variante, cada uma com o que vale (a soma da rubrica), lidas do pacote publicado. */
private fun derivarDiscursivas(publicado: ExamPackage, variante: PackageVariant): Derivacao {
    val itens = publicado.items.associateBy { it.id }
    val discursivas = mutableListOf<AwaitingEssay>()
    for (itemId in variante.positions.values) {
        val item = itens[itemId] ?: return Derivacao.Falha(
            "item `$itemId` da variante `${variante.variantId}` nao existe no pacote publicado",
        )
        if (item.kind == QuestionKind.ESSAY) {
            val rubrica = item.rubric ?: return Derivacao.Falha(
                "discursiva `${item.id}` nao tem rubrica no pacote publicado",
            )
            discursivas += AwaitingEssay(item.id, rubrica.criteria.sumOf { it.points })
        }
    }
    return Derivacao.Ok(discursivas)
}

/** O maximo da parte objetiva: o gabarito dos itens que o pacote declara objetivos. */
private fun maximoObjetivo(publicado: ExamPackage): Int {
    val itens = publicado.items.associateBy { it.id }
    return publicado.answerKey
        .filter { entrada -> itens.getValue(entrada.itemId).kind == QuestionKind.OBJECTIVE }
        .sumOf { it.points }
}
```

E reescreva o corpo de `conferirParcial` (o KDoc fica) para usar os helpers:

```kotlin
private fun conferirParcial(
    publicado: ExamPackage,
    variante: PackageVariant,
    parte: ParteObjetivaSubmetida,
): Proveniencia {
    val discursivas = when (val derivacao = derivarDiscursivas(publicado, variante)) {
        is Derivacao.Falha -> return Proveniencia.NaoConfere(derivacao.motivo)
        is Derivacao.Ok -> derivacao.discursivas
    }

    if (discursivas.isEmpty()) {
        return Proveniencia.NaoConfere(
            "o resultado diz ser parcial de discursiva, e a variante `${variante.variantId}` do " +
                "pacote publicado nao declara nenhuma questao discursiva",
        )
    }

    return try {
        val partial = PartialScore(
            packageHash = parte.packageHash,
            variantId = parte.variantId,
            objectivePoints = parte.objectivePoints,
            objectiveMaxScore = maximoObjetivo(publicado),
            maxScore = parte.maxScoreDeclarado,
            awaiting = discursivas,
            pending = parte.pending,
            outcomes = parte.outcomes,
        )
        Proveniencia.Confere(ApuracaoParaEnvio.Parcial(partial))
    } catch (incoerente: IllegalArgumentException) {
        Proveniencia.NaoConfere(incoerente.message ?: "parcial incoerente com o pacote publicado")
    }
}
```

- [ ] **Step 3: Extrair o miolo de `record` em `ResultQueries.kt`**

Acrescente, no mesmo arquivo (depois da classe `ResultQueries`), os dois tipos:

```kotlin
/** Uma linha de evidencia pronta para o banco: o que `answer_observation` guarda. */
internal class Evidencia(
    val itemId: String,
    val answerKind: String,
    val answerOptions: Array<String?>,
    val worth: Int,
    val earned: java.math.BigDecimal,
)

/** Tudo o que `grading_result` e `answer_observation` guardam de **uma** gravacao, qualquer que seja a origem. */
internal class Gravacao(
    val captureId: String,
    val studentToken: String?,
    val capturedAt: String,
    val origin: String,
    val path: String?,
    val completesCaptureId: String?,
    val packageHash: String,
    val variantId: String,
    val points: java.math.BigDecimal,
    val maxScore: Int,
    val closed: Boolean,
    val evidencias: List<Evidencia>,
)
```

Troque o corpo de `record` — da linha `val campos = nota.paraGravacao()` até **a chave que fecha `record`, inclusive**; o KDoc e a assinatura ficam — pelo bloco abaixo, que já traz as chaves de `record` e de `gravar`:

```kotlin
        val campos = nota.paraGravacao()
        return gravar(
            ctx,
            organizationId,
            examId,
            Gravacao(
                captureId = submission.captureId,
                studentToken = submission.studentToken,
                capturedAt = submission.capturedAt,
                origin = "omr",
                path = null,
                completesCaptureId = null,
                packageHash = campos.packageHash,
                variantId = campos.variantId,
                points = campos.points.toBigDecimal(),
                maxScore = campos.maxScore,
                closed = campos.closed,
                evidencias = campos.outcomes.map { outcome ->
                    Evidencia(
                        itemId = outcome.questionId,
                        answerKind = outcome.answer.answerKind(),
                        answerOptions = outcome.answer.alternativas(),
                        worth = outcome.worth,
                        earned = outcome.earned.toBigDecimal(),
                    )
                },
            ),
        )
    }

    /**
     * O miolo da gravacao, **um so** para toda origem: idempotencia por `(exam_id, capture_id)`, revisao
     * por `(exam_id, student_token)` e as linhas de evidencia, na transacao que `asUser` ja abriu.
     * Foi extraido de `record` sem mudar uma linha do que ele fazia (`slice-5c-2-a-nota-do-professor`).
     */
    private fun gravar(ctx: DSLContext, organizationId: UUID, examId: UUID, g: Gravacao): Int {
        val jaGravada = ctx.select(GRADING_RESULT.REVISION)
            .from(GRADING_RESULT)
            .where(GRADING_RESULT.EXAM_ID.eq(examId))
            .and(GRADING_RESULT.CAPTURE_ID.eq(g.captureId))
            .fetchOne { it.value1() }
        if (jaGravada != null) return jaGravada

        // `is not distinct from`, e nao `=`: o token e nulo na folha avulsa, e `coluna = null` nunca
        // e verdadeiro. Com `=`, toda avulsa comecaria na revisao 1 e a segunda colidiria com a
        // primeira no unique — recusada como duplicata de uma folha que nao e a dela.
        val proxima = ctx.select(DSL.coalesce(DSL.max(GRADING_RESULT.REVISION), 0).plus(1))
            .from(GRADING_RESULT)
            .where(GRADING_RESULT.EXAM_ID.eq(examId))
            .and(
                DSL.condition(
                    "{0} is not distinct from {1}",
                    GRADING_RESULT.STUDENT_TOKEN,
                    DSL.value(g.studentToken),
                ),
            )
            .fetchOne { it.value1() } ?: 1

        val resultadoId = ctx.insertInto(GRADING_RESULT)
            .set(GRADING_RESULT.ORGANIZATION_ID, organizationId)
            .set(GRADING_RESULT.EXAM_ID, examId)
            .set(GRADING_RESULT.STUDENT_TOKEN, g.studentToken)
            .set(GRADING_RESULT.REVISION, proxima)
            .set(GRADING_RESULT.CAPTURE_ID, g.captureId)
            // A origem e explicita: o dia em que existir outra nao depende do default da coluna.
            .set(GRADING_RESULT.ORIGIN, g.origin)
            .set(GRADING_RESULT.PATH, g.path)
            .set(GRADING_RESULT.COMPLETES_CAPTURE_ID, g.completesCaptureId)
            .set(GRADING_RESULT.PACKAGE_HASH, g.packageHash)
            .set(GRADING_RESULT.VARIANT_ID, g.variantId)
            .set(GRADING_RESULT.POINTS, g.points)
            .set(GRADING_RESULT.MAX_SCORE, g.maxScore)
            .set(GRADING_RESULT.CLOSED, g.closed)
            .set(GRADING_RESULT.CAPTURED_AT, OffsetDateTime.parse(g.capturedAt))
            .returningResult(GRADING_RESULT.ID)
            .fetchOne { it.value1() }!!

        for (evidencia in g.evidencias) {
            ctx.insertInto(ANSWER_OBSERVATION)
                .set(ANSWER_OBSERVATION.ORGANIZATION_ID, organizationId)
                .set(ANSWER_OBSERVATION.GRADING_RESULT_ID, resultadoId)
                .set(ANSWER_OBSERVATION.ITEM_ID, evidencia.itemId)
                .set(ANSWER_OBSERVATION.ANSWER_KIND, evidencia.answerKind)
                .set(ANSWER_OBSERVATION.ANSWER_OPTIONS, evidencia.answerOptions)
                .set(ANSWER_OBSERVATION.WORTH, evidencia.worth)
                .set(ANSWER_OBSERVATION.EARNED, evidencia.earned)
                .execute()
        }

        return proxima
    }
```

(O `gravar` entra **dentro** da classe `ResultQueries`, logo depois de `record`; as duas classes `Evidencia` e `Gravacao` ficam fora, no fim do arquivo. Mantenha o comentário longo da KDoc de `record` sobre idempotência e corrida; ele continua valendo.)

- [ ] **Step 4: Provar que nada mudou**

Run: `./gradlew :apps:api:test`
Expected: `BUILD SUCCESSFUL`. **Zero asserção alterada** nos testes existentes (`git diff --stat` desta tarefa toca só os três arquivos de `src/main`). Confira os `timestamp`s de `ResultRouteTest` e `GradingResultTableTest`.

- [ ] **Step 5: Commit**

```bash
git add apps/api/src/main/kotlin/com/platos/api/http/dto/ResultDto.kt apps/api/src/main/kotlin/com/platos/api/exam/ProvenienciaDoResultado.kt apps/api/src/main/kotlin/com/platos/api/exam/ResultQueries.kt
git commit -m "refactor(api): extrai a conferência de pacote e o miolo da gravação" -m "Sem mudança de comportamento: mensagens byte a byte, testes existentes verdes sem asserção alterada. Prepara a rota da nota do professor (P19, P25)." -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 7: A rota `POST …/results/graded`

**Files:**
- Create: `apps/api/src/main/kotlin/com/platos/api/http/dto/GradedResultDto.kt`
- Modify: `apps/api/src/main/kotlin/com/platos/api/exam/ProvenienciaDoResultado.kt`
- Modify: `apps/api/src/main/kotlin/com/platos/api/exam/ResultQueries.kt`
- Modify: `apps/api/src/main/kotlin/com/platos/api/http/Routes.kt`
- Test: `apps/api/src/test/kotlin/com/platos/api/http/GradedResultRouteTest.kt`

**Interfaces:**
- Consumes: `Pontos`, `PontuacaoDada`, `CorrecaoDoProfessor`, `NotaDoProfessor` (Tasks 2–3); `GradedResultSubmissionDto` (Task 5); `paraOutcome`/`paraPendencia`, `conferirPacoteEVariante`, `derivarDiscursivas`, `maximoObjetivo`, `gravar`, `Gravacao`, `Evidencia` (Task 6).
- Produces: `NotaDoProfessorSubmetida`; `GradedResultSubmissionDto.paraNotaSubmetida(): NotaDoProfessorSubmetida` (lança `IllegalArgumentException`); `ProvenienciaDaNota.Confere(nota)` / `.NaoConfere(motivo)`; `conferirNotaDoProfessor(pacote: PackageContent, submetida: NotaDoProfessorSubmetida): ProvenienciaDaNota`; `ResultQueries.recordGraded(ctx, organizationId, examId, submission: GradedResultSubmissionDto, nota: NotaDoProfessor): Int`.

- [ ] **Step 1: Escrever o teste que falha**

`apps/api/src/test/kotlin/com/platos/api/http/GradedResultRouteTest.kt`:

```kotlin
package com.platos.api.http

import com.platos.api.http.dto.ResultAcceptedDto
import com.platos.api.module
import com.platos.api.support.JwtTestFixture
import com.platos.api.support.PostgresSupport
import com.platos.api.support.TestDependencies
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `POST .../results/graded` — a nota do professor (`slice-5c-2-a-nota-do-professor`).
 *
 * **As contagens e os valores sao lidos no banco, nunca da resposta da rota**, como em
 * `ResultRouteTest`: uma rota que respondesse certo e gravasse errado passaria em qualquer asserção
 * sobre a resposta. O corpo vai como **JSON literal** pela mesma razao. Cada recusa le a **mensagem**,
 * porque "recusou" nao diz qual camada segurou (`rigorous.md` §3).
 *
 * O pacote com discursiva e o mesmo de `ResultRouteTest` (`q01` objetiva de 1 ponto, `d1` discursiva
 * de 3, prova de 4), repetido aqui de proposito: o literal e escrito a mao.
 */
class GradedResultRouteTest {

    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        PostgresSupport.start()
        PostgresSupport.reset()
    }

    // ------------------------------------------------------------------ a nota vira revisao nova

    @Test
    fun `a nota do professor e revisao nova da mesma folha, e a parcial continua legivel`() = comApp { client ->
        val org = prepararProva(client)

        assertEquals(HttpStatusCode.OK, client.enviarParcial(org, corpoParcial("cap-parcial-1")).status)
        val resposta = client.enviarNota(org, corpoNota("cap-nota-1"))

        assertEquals(HttpStatusCode.OK, resposta.status, resposta.bodyAsText())
        assertEquals(2, json.decodeFromString<ResultAcceptedDto>(resposta.bodyAsText()).revision)
        assertEquals(2, contar("select count(*) from grading_result"))

        assertEquals("2.75", umTexto("select points::text from grading_result where capture_id = 'cap-nota-1'"))
        assertEquals("teacher", umTexto("select origin from grading_result where capture_id = 'cap-nota-1'"))
        assertEquals("image", umTexto("select path from grading_result where capture_id = 'cap-nota-1'"))
        assertEquals("cap-parcial-1", umTexto("select completes_capture_id from grading_result where capture_id = 'cap-nota-1'"))
        assertEquals("true", umTexto("select closed::text from grading_result where capture_id = 'cap-nota-1'"))

        assertEquals("1.00", umTexto("select points::text from grading_result where capture_id = 'cap-parcial-1'"))
        assertEquals("omr", umTexto("select origin from grading_result where capture_id = 'cap-parcial-1'"))

        assertEquals(
            listOf("discursiva_corrigida:3:1.75", "marcada:1:1.00"),
            textos(
                """
                select o.answer_kind || ':' || o.worth || ':' || o.earned::text
                from answer_observation o join grading_result g on g.id = o.grading_result_id
                where g.capture_id = 'cap-nota-1' order by o.item_id
                """.trimIndent(),
            ),
        )
        assertEquals(listOf("cap-nota-1"), textos("select capture_id from grading_result_current"))
    }

    @Test
    fun `a nota e autocontida, e e aceita sem a parcial da captura`() = comApp { client ->
        val org = prepararProva(client)

        val resposta = client.enviarNota(org, corpoNota("cap-nota-1"))

        assertEquals(HttpStatusCode.OK, resposta.status, resposta.bodyAsText())
        assertEquals(1, json.decodeFromString<ResultAcceptedDto>(resposta.bodyAsText()).revision)
        assertEquals(1, contar("select count(*) from grading_result"))
    }

    @Test
    fun `reenvio da mesma nota nao cria registro novo e responde igual`() = comApp { client ->
        val org = prepararProva(client)

        val primeira = client.enviarNota(org, corpoNota("cap-nota-1"))
        val segunda = client.enviarNota(org, corpoNota("cap-nota-1"))

        assertEquals(HttpStatusCode.OK, segunda.status)
        assertEquals(primeira.bodyAsText(), segunda.bodyAsText())
        assertEquals(1, contar("select count(*) from grading_result"))
        assertEquals(2, contar("select count(*) from answer_observation"))
    }

    @Test
    fun `nova nota para a mesma folha e revisao nova, e a mais recente e a corrente`() = comApp { client ->
        val org = prepararProva(client)

        client.enviarNota(org, corpoNota("cap-nota-1", d1 = "1.75", pontos = "2.75"))
        val segunda = client.enviarNota(org, corpoNota("cap-nota-2", d1 = "2.5", pontos = "3.50"))

        assertEquals(2, json.decodeFromString<ResultAcceptedDto>(segunda.bodyAsText()).revision)
        assertEquals(listOf("cap-nota-2"), textos("select capture_id from grading_result_current"))
        assertEquals("2.75", umTexto("select points::text from grading_result where capture_id = 'cap-nota-1'"))
    }

    @Test
    fun `a parcial que chega depois da nota nao e a corrente, e a rota responde sucesso`() = comApp { client ->
        val org = prepararProva(client)

        client.enviarNota(org, corpoNota("cap-nota-1"))
        val parcial = client.enviarParcial(org, corpoParcial("cap-parcial-1"))

        assertEquals(HttpStatusCode.OK, parcial.status, parcial.bodyAsText())
        assertEquals(2, contar("select count(*) from grading_result"))
        assertEquals(listOf("cap-nota-1"), textos("select capture_id from grading_result_current"))
    }

    @Test
    fun `folha avulsa tem a nota do professor, e a corrente e a nota`() = comApp { client ->
        val org = prepararProva(client)

        client.enviarParcial(org, corpoParcial("cap-parcial-1", token = null))
        val nota = client.enviarNota(org, corpoNota("cap-nota-1", token = null))

        assertEquals(HttpStatusCode.OK, nota.status, nota.bodyAsText())
        assertEquals(listOf("cap-nota-1"), textos("select capture_id from grading_result_current"))
    }

    @Test
    fun `as bordas da faixa sao validas, zero e o valor exato do pacote`() = comApp { client ->
        val org = prepararProva(client)

        assertEquals(HttpStatusCode.OK, client.enviarNota(org, corpoNota("cap-zero", d1 = "0", pontos = "1")).status)
        assertEquals(HttpStatusCode.OK, client.enviarNota(org, corpoNota("cap-max", d1 = "3.00", pontos = "4.00")).status)

        assertEquals("1.00", umTexto("select points::text from grading_result where capture_id = 'cap-zero'"))
        assertEquals("4.00", umTexto("select points::text from grading_result where capture_id = 'cap-max'"))
    }

    // ------------------------------------------------------------------ as recusas, uma camada por vez

    @Test
    fun `cada recusa e 400, diz o que nao fecha, e nada e gravado`() = comApp { client ->
        val org = prepararProva(client)

        val casos = listOf(
            "tres casas" to (corpoNota("c1", d1 = "1.333", pontos = "2.33") to "mais de 2 casas"),
            "negativa" to (corpoNota("c2", d1 = "-1", pontos = "0") to "negativa"),
            "nao numerica" to (corpoNota("c3", d1 = "abc") to "nao e uma pontuacao"),
            "virgula decimal" to (corpoNota("c4", d1 = "1,5") to "ponto decimal"),
            "acima do valor" to (corpoNota("c5", d1 = "3.01", pontos = "4.01") to "no maximo 3"),
            "item que nao e discursiva" to (corpoNota("c6", notas = """[{"item_id":"d9","earned":"1"}]""") to "nao e discursiva"),
            "objetiva pontuada" to (corpoNota("c7", notas = """[{"item_id":"q01","earned":"1"}]""") to "nao e discursiva"),
            "discursiva sem nota" to (corpoNota("c8", notas = "[]") to "nao recebeu pontuacao"),
            "discursiva repetida" to (
                corpoNota("c9", notas = """[{"item_id":"d1","earned":"1"},{"item_id":"d1","earned":"2"}]""") to "mais de uma vez"
                ),
            "total divergente" to (corpoNota("c10", pontos = "9") to "declara total"),
            "closed divergente" to (corpoNota("c11", fechada = false) to "closed="),
            "max_score divergente" to (corpoNota("c12", maximo = 5) to "a prova vale 5"),
            "pacote que nao e o da prova" to (corpoNota("c13", hash = "b".repeat(64)) to "contra o pacote"),
            "variante que o pacote nao declara" to (corpoNota("c14", variante = "v9") to "a variante `v9`"),
            "origem de fatia posterior" to (corpoNota("c15", origem = "ai") to "origem 'ai'"),
            "caminho de fatia posterior" to (corpoNota("c16", caminho = "text") to "caminho 'text'"),
            "discursiva_corrigida contrabandeada na objetiva" to (
                corpoNota(
                    "c17",
                    observacoes = """[{"item_id":"q01","answer_kind":"discursiva_corrigida","answer_options":[],"worth":1,"earned":1}]""",
                ) to "que nao existe"
                ),
        )

        for ((nome, caso) in casos) {
            val (corpo, trecho) = caso
            val resposta = client.enviarNota(org, corpo)
            assertEquals(HttpStatusCode.BadRequest, resposta.status, "$nome: ${resposta.bodyAsText()}")
            assertTrue(trecho in resposta.bodyAsText(), "$nome: esperava '$trecho' em: ${resposta.bodyAsText()}")
            assertEquals(0, contar("select count(*) from grading_result"), "$nome gravou resultado")
            assertEquals(0, contar("select count(*) from answer_observation"), "$nome gravou evidencia")
        }
    }

    @Test
    fun `prova so objetiva nao tem o que pontuar`() = comApp { client ->
        val (userId, org) = professorComOrganizacao(client)
        val prova = PostgresSupport.createExam(org, SHORT_ID_OBJETIVA, "Prova O", userId)
        PostgresSupport.publishPackage(org, prova, CONTEUDO_SO_OBJETIVA)

        val resposta = client.enviarNota(
            org,
            corpoNota("cap-nota-1", hash = HASH_SO_OBJETIVA, notas = """[{"item_id":"d1","earned":"1"}]""", maximo = 1, pontos = "2"),
            shortId = SHORT_ID_OBJETIVA,
        )

        assertEquals(HttpStatusCode.BadRequest, resposta.status)
        assertTrue("nota completa" in resposta.bodyAsText(), resposta.bodyAsText())
        assertEquals(0, contar("select count(*) from grading_result"))
    }

    @Test
    fun `sem credencial e 401, prova de outra organizacao e prova inexistente sao 404, e nada e gravado`() = comApp { client ->
        val org = prepararProva(client)
        val alheia = organizacaoAlheiaComProva()

        val semToken = client.post("/organizations/$org/exams/$SHORT_ID/results/graded") {
            contentType(ContentType.Application.Json)
            setBody(corpoNota("cap-1"))
        }
        val deOutraOrg = client.enviarNota(alheia, corpoNota("cap-2"), shortId = SHORT_ID_ALHEIA)
        val inexistente = client.enviarNota(org, corpoNota("cap-3"), shortId = "nao-existe")

        assertEquals(HttpStatusCode.Unauthorized, semToken.status)
        assertEquals(HttpStatusCode.NotFound, deOutraOrg.status)
        assertEquals(HttpStatusCode.NotFound, inexistente.status)
        assertEquals(0, contar("select count(*) from grading_result"))
    }

    @Test
    fun `um corpo que traga imagem nao a grava em lugar nenhum`() = comApp { client ->
        val org = prepararProva(client)

        val resposta = client.enviarNota(org, corpoNota("cap-nota-1", extra = ",\"image\":\"AAAAQUFB\",\"file\":\"resposta-d1.png\""))

        assertTrue(
            resposta.status == HttpStatusCode.OK || resposta.status == HttpStatusCode.BadRequest,
            "esperava aceitar ignorando ou recusar, e veio ${resposta.status}",
        )
        // O que importa: nenhuma coluna de nenhuma das duas tabelas carrega o conteudo nem o nome do arquivo.
        assertEquals(0, contar("select count(*) from grading_result g where g::text like '%AAAAQUFB%' or g::text like '%resposta-d1%'"))
        assertEquals(0, contar("select count(*) from answer_observation o where o::text like '%AAAAQUFB%' or o::text like '%resposta-d1%'"))
    }

    // ------------------------------------------------------------------ montagem

    /** O corpo da nota, como JSON literal. `q01` objetiva certa (1) + `d1` (3) = prova de 4. */
    private fun corpoNota(
        captureId: String,
        completa: String = "cap-parcial-1",
        token: String? = "aluno-1",
        hash: String = HASH_DISCURSIVA,
        variante: String = "v1",
        origem: String = "teacher",
        caminho: String = "image",
        d1: String = "1.75",
        pontos: String = "2.75",
        maximo: Int = 4,
        fechada: Boolean = true,
        observacoes: String = """[{"item_id":"q01","answer_kind":"marcada","answer_options":["A"],"worth":1,"earned":1}]""",
        notas: String = """[{"item_id":"d1","earned":"$d1"}]""",
        extra: String = "",
    ): String {
        val tokenJson = if (token == null) "null" else "\"$token\""
        return """
            {"capture_id":"$captureId","completes_capture_id":"$completa","student_token":$tokenJson,
             "package_hash":"$hash","variant_id":"$variante","origin":"$origem","path":"$caminho",
             "points":"$pontos","max_score":$maximo,"closed":$fechada,"captured_at":"2026-09-17T12:00:00Z",
             "observations":$observacoes,"essay_grades":$notas$extra}
        """.trimIndent()
    }

    /** A parcial que a nota completa: `q01` marcada e certa, `max_score` 4 (a prova inteira). */
    private fun corpoParcial(captureId: String, token: String? = "aluno-1"): String {
        val tokenJson = if (token == null) "null" else "\"$token\""
        return """
            {"capture_id":"$captureId","student_token":$tokenJson,"package_hash":"$HASH_DISCURSIVA",
             "variant_id":"v1","points":1,"max_score":4,"closed":false,
             "captured_at":"2026-09-17T12:00:00Z",
             "observations":[{"item_id":"q01","answer_kind":"marcada","answer_options":["A"],"worth":1,"earned":1}],
             "partial":true}
        """.trimIndent()
    }

    private suspend fun HttpClient.enviarNota(
        organizationId: UUID,
        corpo: String,
        shortId: String = SHORT_ID,
    ): HttpResponse = post("/organizations/$organizationId/exams/$shortId/results/graded") {
        header(HttpHeaders.Authorization, "Bearer ${JwtTestFixture.token(SUB, EMAIL, NOME)}")
        contentType(ContentType.Application.Json)
        setBody(corpo)
    }

    private suspend fun HttpClient.enviarParcial(organizationId: UUID, corpo: String): HttpResponse =
        post("/organizations/$organizationId/exams/$SHORT_ID/results") {
            header(HttpHeaders.Authorization, "Bearer ${JwtTestFixture.token(SUB, EMAIL, NOME)}")
            contentType(ContentType.Application.Json)
            setBody(corpo)
        }

    private suspend fun prepararProva(client: HttpClient): UUID {
        val (userId, org) = professorComOrganizacao(client)
        val prova = PostgresSupport.createExam(org, SHORT_ID, "Prova com discursiva", userId)
        PostgresSupport.publishPackage(org, prova, CONTEUDO_COM_DISCURSIVA)
        return org
    }

    private suspend fun professorComOrganizacao(client: HttpClient): Pair<UUID, UUID> {
        client.get("/me/organizations") {
            header(HttpHeaders.Authorization, "Bearer ${JwtTestFixture.token(SUB, EMAIL, NOME)}")
        }
        return umUuid("select id from app_user where auth_subject = '$SUB'") to
            umUuid(
                "select o.id from organization o join membership m on m.organization_id = o.id " +
                    "join app_user u on u.id = m.user_id where u.auth_subject = '$SUB' " +
                    "and o.kind = 'personal'",
            )
    }

    private fun organizacaoAlheiaComProva(): UUID {
        val outro = PostgresSupport.createUser("sub-outro", "outro@escola.br")
        val alheia = PostgresSupport.createOrganization(kind = "school", name = "Escola Alheia")
        PostgresSupport.addMembership(outro, alheia, "teacher")
        val exame = PostgresSupport.createExam(alheia, SHORT_ID_ALHEIA, "Prova Alheia", outro)
        PostgresSupport.publishPackage(alheia, exame, CONTEUDO_COM_DISCURSIVA)
        return alheia
    }

    private fun contar(sql: String): Int = PostgresSupport.adminDataSource.connection.use { c ->
        c.createStatement().use { s -> s.executeQuery(sql).use { it.next(); it.getInt(1) } }
    }

    private fun umTexto(sql: String): String? = PostgresSupport.adminDataSource.connection.use { c ->
        c.createStatement().use { s -> s.executeQuery(sql).use { it.next(); it.getString(1) } }
    }

    private fun textos(sql: String): List<String> = PostgresSupport.adminDataSource.connection.use { c ->
        c.createStatement().use { s ->
            s.executeQuery(sql).use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
        }
    }

    private fun umUuid(sql: String): UUID = PostgresSupport.adminDataSource.connection.use { c ->
        c.createStatement().use { s ->
            s.executeQuery(sql).use { it.next(); it.getObject(1, UUID::class.java) }
        }
    }

    private fun comApp(block: suspend (HttpClient) -> Unit) = testApplication {
        application { module(TestDependencies.create(), JwtTestFixture.jwkProvider) }
        block(createClient { })
    }

    private companion object {
        const val SUB = "sub-nota"
        const val EMAIL = "nota@escola.br"
        const val NOME = "Professor da Nota"
        const val SHORT_ID = "prova-discursiva-n"
        const val SHORT_ID_OBJETIVA = "prova-objetiva-n"
        const val SHORT_ID_ALHEIA = "prova-alheia-n"

        /** Escrito a mao, como em `ResultRouteTest`: serializar com o tipo do servidor poria o mesmo codigo dos dois lados. */
        const val CONTEUDO_COM_DISCURSIVA =
            """{"meta":{"exam_id":"$SHORT_ID","layout_engine_version":1,"min_renderer_version":1,"fully_offline_gradable":false},"items":[{"id":"q01","statement":"Q1","options":["A","B"],"skills":[{"code":"EM13MAT301","coverage":"anchor"}],"kind":"objective"},{"id":"d1","statement":"D1","options":[],"skills":[{"code":"EM13MAT301","coverage":"anchor"}],"kind":"essay","rubric":{"criteria":[{"id":"c1","description":"C1","points":3,"expected_lines":2,"descriptors":[]}]}}],"variants":[{"variant_id":"v1","positions":{"1":"q01","2":"d1"}}],"assignments":[],"layout":{},"answer_key":[{"item_id":"q01","correct":"A","points":1}],"scoring":{"max_score":4}}"""

        const val CONTEUDO_SO_OBJETIVA =
            """{"meta":{"exam_id":"$SHORT_ID_OBJETIVA","layout_engine_version":1,"min_renderer_version":1,"fully_offline_gradable":true},"items":[{"id":"q01","statement":"Q1","options":["A","B"],"skills":[{"code":"EM13MAT301","coverage":"anchor"}],"kind":"objective"}],"variants":[{"variant_id":"v1","positions":{"1":"q01"}}],"assignments":[],"layout":{},"answer_key":[{"item_id":"q01","correct":"A","points":1}],"scoring":{"max_score":1}}"""

        val HASH_DISCURSIVA: String = PostgresSupport.sha256Hex(CONTEUDO_COM_DISCURSIVA)
        val HASH_SO_OBJETIVA: String = PostgresSupport.sha256Hex(CONTEUDO_SO_OBJETIVA)
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew :apps:api:test`
Expected: FAIL; a compilação passa (o teste só usa HTTP) e **todos** os testes novos falham com `404 Not Found` na rota que ainda não existe (ou `405`). **Leia a mensagem:** é "esperava 200/400 e veio 404", e não erro de fixture. Se algum falhar por outro motivo (por exemplo `ExamPackage` não decodificar o literal), corrija a fixture agora, **não** o código.

- [ ] **Step 3: Implementar a Fase 1 (`GradedResultDto.kt`, módulo da API)**

```kotlin
package com.platos.api.http.dto

import com.platos.domain.scoring.PontuacaoDada
import com.platos.domain.scoring.Pontos
import com.platos.domain.scoring.QuestionOutcome
import com.platos.domain.transport.GradedResultSubmissionDto

private const val ORIGEM_ACEITA = "teacher"
private const val CAMINHO_ACEITO = "image"

/**
 * O que a Fase 1 da nota do professor consegue validar **sem o pacote** (`slice-5c-2-a-nota-do-professor`,
 * design secao 3): forma, origem, caminho e decimais. O que depende do pacote publicado — as discursivas,
 * a faixa, o total — e a Fase 2, em `conferirNotaDoProfessor`.
 */
data class NotaDoProfessorSubmetida(
    val packageHash: String,
    val variantId: String,
    val maxScoreDeclarado: Int,
    val totalDeclarado: Pontos,
    val closedDeclarado: Boolean,
    val outcomes: List<QuestionOutcome>,
    val pontuacoes: List<PontuacaoDada>,
)

/**
 * Lanca [IllegalArgumentException] com a mensagem que a rota devolve em 400.
 *
 * **Recusa a origem e o caminho de fatia posterior** (`ai`, `text`) aqui, antes de qualquer SQL. A parte
 * objetiva vem como `observations`, e `paraOutcome` recusa `answer_kind` que nao exista — inclusive
 * `discursiva_corrigida`, que nao e uma resposta objetiva e nao pode entrar por esse caminho.
 */
fun GradedResultSubmissionDto.paraNotaSubmetida(): NotaDoProfessorSubmetida {
    require(origin == ORIGEM_ACEITA) { "origem '$origin' nao e aceita nesta rota: so '$ORIGEM_ACEITA'" }
    require(path == CAMINHO_ACEITO) { "caminho '$path' nao e aceito nesta rota: so '$CAMINHO_ACEITO'" }
    require(completesCaptureId.isNotBlank()) {
        "completes_capture_id e obrigatorio: diz qual captura da parcial esta nota completa"
    }

    return NotaDoProfessorSubmetida(
        packageHash = packageHash,
        variantId = variantId,
        maxScoreDeclarado = maxScore,
        totalDeclarado = lerPontos(points, "o total declarado"),
        closedDeclarado = closed,
        outcomes = observations.map { it.paraOutcome() },
        pontuacoes = essayGrades.map { PontuacaoDada(it.itemId, lerPontos(it.earned, "a discursiva '${it.itemId}'")) },
    )
}

/** Le a pontuacao e, se falhar, diz **de qual campo** — a mensagem de 400 nomeia a questao. */
private fun lerPontos(texto: String, de: String): Pontos =
    try {
        Pontos.parse(texto)
    } catch (erro: IllegalArgumentException) {
        throw IllegalArgumentException("$de: ${erro.message}")
    }
```

- [ ] **Step 4: Implementar a Fase 2 (`ProvenienciaDoResultado.kt`)**

Acrescente os imports `com.platos.api.http.dto.NotaDoProfessorSubmetida`, `com.platos.api.http.dto.paraPendencia`, `com.platos.domain.scoring.CorrecaoDoProfessor`, `com.platos.domain.scoring.NotaDoProfessor`, `com.platos.domain.scoring.NotaDoProfessorOutcome` e, no fim do arquivo:

```kotlin
/** O desfecho da conferencia da nota do professor: a mesma distincao de [Proveniencia]. */
sealed interface ProvenienciaDaNota {
    data class Confere(val nota: NotaDoProfessor) : ProvenienciaDaNota
    data class NaoConfere(val motivo: String) : ProvenienciaDaNota
}

/**
 * A Fase 2 da nota do professor, contra o **pacote publicado da propria prova** — o unico oraculo
 * (`slice-5c-2-a-nota-do-professor`, spec `result-sync`).
 *
 * Confere o pacote e a variante (as mesmas travas de [conferirProveniencia]), deriva as discursivas do
 * pacote, reconstroi a parte objetiva como [PartialScore] (as guardas dela rodam: evidencia que nao
 * soma, item repetido, maximo que nao fecha com a prova), e **roda o mesmo codigo que o aparelho usara**
 * para completar com as pontuacoes. So entao compara o total e o `closed` **declarados** com os
 * recalculados: e o que produz "a pontuacao nao soma o total".
 *
 * **Nao recalcula a parte objetiva a partir do gabarito**: confere-se proveniencia e coerencia, nao
 * aritmetica objetiva (D4, §10).
 */
fun conferirNotaDoProfessor(pacote: PackageContent, submetida: NotaDoProfessorSubmetida): ProvenienciaDaNota {
    val base = conferirPacoteEVariante(pacote, submetida.packageHash, submetida.variantId)
    if (base is ConferenciaDoPacote.Falha) return ProvenienciaDaNota.NaoConfere(base.motivo)
    base as ConferenciaDoPacote.Ok

    val discursivas = when (val derivacao = derivarDiscursivas(base.publicado, base.variante)) {
        is Derivacao.Falha -> return ProvenienciaDaNota.NaoConfere(derivacao.motivo)
        is Derivacao.Ok -> derivacao.discursivas
    }
    if (discursivas.isEmpty()) {
        return ProvenienciaDaNota.NaoConfere(
            "a variante `${base.variante.variantId}` do pacote publicado nao declara nenhuma questao " +
                "discursiva, e a prova tem nota completa",
        )
    }

    val parcial = try {
        PartialScore(
            packageHash = submetida.packageHash,
            variantId = submetida.variantId,
            objectivePoints = submetida.outcomes.sumOf { it.earned },
            objectiveMaxScore = maximoObjetivo(base.publicado),
            maxScore = submetida.maxScoreDeclarado,
            awaiting = discursivas,
            pending = submetida.outcomes.filter { it.pendente }.map { it.paraPendencia() },
            outcomes = submetida.outcomes,
        )
    } catch (incoerente: IllegalArgumentException) {
        return ProvenienciaDaNota.NaoConfere(
            incoerente.message ?: "parte objetiva incoerente com o pacote publicado",
        )
    }

    val nota = when (val composta = CorrecaoDoProfessor.completar(parcial, submetida.pontuacoes)) {
        is NotaDoProfessorOutcome.Rejected -> return ProvenienciaDaNota.NaoConfere(composta.reason)
        is NotaDoProfessorOutcome.Scored -> composta.nota
    }

    if (nota.total != submetida.totalDeclarado) {
        return ProvenienciaDaNota.NaoConfere(
            "o resultado declara total ${submetida.totalDeclarado} e a apuracao contra o pacote " +
                "publicado soma ${nota.total}",
        )
    }
    if (nota.closed != submetida.closedDeclarado) {
        return ProvenienciaDaNota.NaoConfere(
            "o corpo diz closed=${submetida.closedDeclarado} e a nota apurada tem closed=${nota.closed}",
        )
    }
    return ProvenienciaDaNota.Confere(nota)
}
```

- [ ] **Step 5: `recordGraded` em `ResultQueries.kt`**

Acrescente os imports `com.platos.domain.scoring.NotaDoProfessor`, `com.platos.domain.scoring.Pontos`, `com.platos.domain.transport.AnswerKind`, `com.platos.domain.transport.GradedResultSubmissionDto`, `java.math.BigDecimal`, e dentro da classe, depois de `gravar`:

```kotlin
    /**
     * Grava a nota do professor como **revisao nova** da mesma folha (`slice-5c-2-a-nota-do-professor`).
     *
     * Mesma idempotencia e mesma numeracao de [record], porque e o mesmo [gravar]: reenvio devolve a
     * revisao que ja existe, e nova correcao da mesma captura tem `capture_id` proprio. A evidencia leva
     * as objetivas, como na parcial, **e** uma linha `discursiva_corrigida` por discursiva. Quem decide
     * qual revisao e a corrente e a view `grading_result_current`, e nao este metodo.
     */
    fun recordGraded(
        ctx: DSLContext,
        organizationId: UUID,
        examId: UUID,
        submission: GradedResultSubmissionDto,
        nota: NotaDoProfessor,
    ): Int = gravar(
        ctx,
        organizationId,
        examId,
        Gravacao(
            captureId = submission.captureId,
            studentToken = submission.studentToken,
            capturedAt = submission.capturedAt,
            origin = "teacher",
            path = "image",
            completesCaptureId = submission.completesCaptureId,
            packageHash = nota.packageHash,
            variantId = nota.variantId,
            points = nota.total.paraBigDecimal(),
            maxScore = nota.maxScore,
            closed = nota.closed,
            evidencias = nota.outcomes.map { outcome ->
                Evidencia(
                    itemId = outcome.questionId,
                    answerKind = outcome.answer.answerKind(),
                    answerOptions = outcome.answer.alternativas(),
                    worth = outcome.worth,
                    earned = outcome.earned.toBigDecimal(),
                )
            } + nota.essays.map { essay ->
                Evidencia(
                    itemId = essay.questionId,
                    answerKind = AnswerKind.DISCURSIVA_CORRIGIDA,
                    answerOptions = emptyArray(),
                    worth = essay.worth,
                    earned = essay.earned.paraBigDecimal(),
                )
            },
        ),
    )
```

E, no fim do arquivo, a conversão para o banco (que é do servidor, ADR-0015 decisão 2):

```kotlin
/** Centesimos exatos para o `numeric(8,2)`: sem passar por `Double`. */
private fun Pontos.paraBigDecimal(): BigDecimal = BigDecimal.valueOf(centesimos, 2)
```

- [ ] **Step 6: A rota em `Routes.kt`**

Imports: `com.platos.api.exam.ProvenienciaDaNota`, `com.platos.api.exam.conferirNotaDoProfessor`, `com.platos.api.http.dto.paraNotaSubmetida`, `com.platos.domain.transport.GradedResultSubmissionDto`.

Dentro de `examRoutes`, logo depois do `post("/organizations/{organizationId}/exams/{shortId}/results")` existente (o ponto de ancoragem é o fecho dele: `is Desfecho.Gravado -> call.respond(ResultAcceptedDto(revision = desfecho.revision))` seguido de `}` e `}`), acrescente:

```kotlin
        /**
         * `POST .../results/graded` — a nota do professor sobre as discursivas
         * (`slice-5c-2-a-nota-do-professor`, ADR-0021). Grava **revisao nova** da mesma folha, com origem
         * `teacher`; a corrente e derivada pela view `grading_result_current`.
         *
         * Mesma autenticacao, mesma ausencia (404) e mesma recusa definitiva (400, com a mensagem do que
         * nao fecha) da rota de resultados. **A conferencia contra o pacote publicado acontece dentro da
         * transacao e antes de qualquer `insert`**: o fato e append-only, e o que se grava errado nao tem
         * conserto.
         */
        post("/organizations/{organizationId}/exams/{shortId}/results/graded") {
            val organizationId = call.parameters["organizationId"]?.let(::uuidOrNull)
                ?: return@post call.naoEncontrado()
            val shortId = call.parameters["shortId"] ?: return@post call.naoEncontrado()

            val submission = call.receive<GradedResultSubmissionDto>()
            val submetida = try {
                submission.paraNotaSubmetida()
            } catch (erro: IllegalArgumentException) {
                return@post call.respondText(
                    erro.message ?: "nota do professor incoerente",
                    status = HttpStatusCode.BadRequest,
                )
            }

            val userId = call.resolverUsuario(deps)
            val desfecho = deps.tenancy.asUser(userId) { ctx ->
                val publicada = deps.resultQueries.findPublishedExamId(ctx, organizationId, shortId)
                if (publicada == null) {
                    null
                } else {
                    when (val conferencia = conferirNotaDoProfessor(publicada.pacote, submetida)) {
                        is ProvenienciaDaNota.NaoConfere -> Desfecho.Recusado(conferencia.motivo)
                        is ProvenienciaDaNota.Confere -> Desfecho.Gravado(
                            deps.resultQueries.recordGraded(
                                ctx,
                                organizationId,
                                publicada.examId,
                                submission,
                                conferencia.nota,
                            ),
                        )
                    }
                }
            } ?: return@post call.naoEncontrado()

            when (desfecho) {
                is Desfecho.Recusado -> call.respondText(
                    desfecho.motivo,
                    status = HttpStatusCode.BadRequest,
                )
                is Desfecho.Gravado -> call.respond(ResultAcceptedDto(revision = desfecho.revision))
            }
        }
```

- [ ] **Step 7: Rodar e ver passar**

Run: `./gradlew :apps:api:test`
Expected: `BUILD SUCCESSFUL`. Confira o `timestamp` do XML de `GradedResultRouteTest` (11 testes) e que `ResultRouteTest` continua verde. **Se uma recusa do laço falhar, leia o `nome` na mensagem** (o laço o inclui) e a camada que segurou: não afrouxe o trecho esperado (P12). Se o teste de imagem (`um corpo que traga imagem…`) cair com status diferente de 200/400, leia a mensagem do `ContentNegotiation` antes de agir.

- [ ] **Step 8: Commit**

```bash
git add apps/api/src/main/kotlin/com/platos/api/http/dto/GradedResultDto.kt apps/api/src/main/kotlin/com/platos/api/exam/ProvenienciaDoResultado.kt apps/api/src/main/kotlin/com/platos/api/exam/ResultQueries.kt apps/api/src/main/kotlin/com/platos/api/http/Routes.kt apps/api/src/test/kotlin/com/platos/api/http/GradedResultRouteTest.kt
git commit -m "feat(api): POST results/graded, a nota do professor como revisão nova da mesma folha" -m "Duas fases (forma e decimais; pacote publicado como único oráculo), mesma lógica do aparelho via CorrecaoDoProfessor, gravação append-only e idempotente; a corrente vem da view." -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Ver falhar, suíte cheia, cobertura e o registro para o archive

**Files:**
- Create: `docs/cobertura-slice-5c-2-a-nota-do-professor.md`
- Modify: nada além das mutações **temporárias** abaixo (todas revertidas).

Cada mutação segue o mesmo laço (P9, P10): **(1)** injete **uma** mudança; **(2)** rode o comando; **(3)** anote **quais testes caíram e a mensagem**; **(4)** reverta com `git checkout -- <arquivo>`; **(5)** rode **de novo** o mesmo comando e veja verde; **(6)** `git status --short` limpo. Uma mutação só por vez; se mais de uma camada cair, a fixture não isola a camada — reescreva-a, não a mutação.

- [ ] **Step 1: M1 — a recusa de 3 casas sai do domínio**

Em `Pontos.kt`, comente a linha `require(!MUITAS_CASAS.matches(texto)) { ... }`.
Run: `./gradlew :packages:domain:jvmTest`
Expected: FAIL **só** em `PontosTest.tres casas sao recusadas, e nao arredondadas` — a mensagem esperada ("mais de 2 casas") não aparece, porque a recusa agora vem como "nao e uma pontuacao". É a prova de que a asserção confere o **motivo**. Reverta e rode de novo.

- [ ] **Step 2: M2 — ler a pontuação via `Double`**

Em `Pontos.parse`, troque a última linha por `return Pontos((texto.toDouble() * 100).toLong())` (e o que mais for preciso para compilar).
Run: `./gradlew :packages:domain:jvmTest`
Expected: FAIL em `PontosTest.valores que o ponto flutuante binario erra continuam exatos` (0.29 → 28, 0.57 → 56, 1.15 → 114). Reverta e rode de novo.

- [ ] **Step 3: M3 — a view esquece a exclusão da captura referenciada**

Em `20261001120000_nota_do_professor.sql`, substitua o bloco `where r.origin = 'teacher' or not exists (...)` por nenhum `where`.
Run: `./gradlew :apps:api:test`
Expected: FAIL **exatamente em três**: `GradingResultCurrentViewTest.a parcial que chega depois da nota do professor nao e a corrente…`, `GradingResultCurrentViewTest.folha avulsa, a parcial que chega depois da nota nao a substitui` e `GradedResultRouteTest.a parcial que chega depois da nota nao e a corrente…`. **Os demais passam**, e isso é o esperado e honesto: sem a exclusão, a maior `revision` ainda dá a resposta certa quando a nota chega depois da parcial; só a **chegada tardia da parcial** depende da exclusão. Se mais de três caírem, a fixture não isola a camada. Anote o conjunto exato. Reverta e rode de novo.

- [ ] **Step 4: M4 — a conferência do total sai do servidor**

Em `conferirNotaDoProfessor`, comente o `if (nota.total != submetida.totalDeclarado) { ... }`.
Run: `./gradlew :apps:api:test`
Expected: FAIL **só** no laço de recusas, no caso `total divergente` (a mensagem do teste traz o `nome`): esperava 400, veio 200, e gravou. Reverta e rode de novo.

- [ ] **Step 5: M5 — um `@SerialName` trocado derruba os DOIS lados**

Em `GradedResultDto.kt` (domínio), troque `@SerialName("essay_grades")` por `@SerialName("essay_notes")`.
Run: `./gradlew :packages:domain:jvmTest :apps:api:test :apps:android:testDebugUnitTest`
Expected: FAIL em `GradedResultSubmissionDtoTest` (domínio), em `GradedResultContractTest` e nos testes de rota (servidor) **e** em `GradedResultDtoTest` (aparelho). **Se cair só um lado, o fio não está preso nos dois, e a mudança não entregou o que o ADR-0015 pede** — pare e diga qual lado. Reverta e rode de novo.

- [ ] **Step 6: M6 — as guardas do `answer_kind` e do `fio` continuam capazes de falhar**

Run: `node tools/parity/answer-kind.mjs --esperado marcada,em_branco,multipla_marcacao,indecisa; echo "exit=$?"` → `exit=1` (o conjunto antigo, sem o valor novo).
Em `20261001120000_nota_do_professor.sql`, tire `'discursiva_corrigida'` do `check (answer_kind in (...))`. Run: `node tools/parity/answer-kind.mjs; echo "exit=$?"` → `exit=1`. Reverta.
Run: `node tools/parity/fio.mjs --transport packages/domain/src/commonMain/kotlin/com/platos/domain/transport; echo "exit=$?"` → `exit=0`; renomeie temporariamente `GradedResultContractTest.kt` para `GradedResultContractTest.kt.bak` e rode `node tools/parity/fio.mjs; echo "exit=$?"` → `exit=1` (servidor sem literal). Desfaça o nome.

- [ ] **Step 7: M7 — a view sem `security_invoker`**

Em `20261001120000_nota_do_professor.sql`, remova `with (security_invoker = true)`.
Run: `./gradlew :apps:api:test`
Expected: FAIL em `a view respeita a organizacao…` (o dono deixa de ver a própria folha, porque a view passa a rodar como `app_owner`, que não tem política de RLS; ou o forasteiro passa a ver — **anote qual dos dois**, o que for medido). Reverta e rode de novo.

- [ ] **Step 8: Conferir que nenhuma mutação ficou na árvore (P10)**

Run: `git status --short`
Expected: vazio. Run: `git diff HEAD --stat` → vazio.

- [ ] **Step 9: A suíte cheia (P5)**

Com o Docker de pé (e **sem** emulador rodando; Docker + emulador + Gradle estouram 16 GB — ver memória do projeto):

Run: `./gradlew --stop && ./gradlew build --rerun-tasks`
Expected: `BUILD SUCCESSFUL`. Anote: a data, o comando exato, e o `timestamp` de um XML de cada módulo (`packages/domain`, `apps/api`, `apps/android`) **posterior ao início** do comando (P3).
Run: `./gradlew -p buildSrc test` → `BUILD SUCCESSFUL`.
Run: `node tools/parity/answer-kind.mjs && node tools/parity/fio.mjs && node tools/divida/divida.mjs; echo "exit=$?"` → `exit=0` (a fatia corrente segue `5c`).
Run: `openspec validate slice-5c-2-a-nota-do-professor --strict` → válido.
**O que não roda e por quê (diga na cobertura):** `connectedDebugAndroidTest` não foi rodado — nenhum código de produto, manifesto ou suíte instrumentada do Android mudou; só um teste unitário. Se o Leon quiser a rodada, é pedido dele (emulador = ambiente, P22).

- [ ] **Step 10: Escrever `docs/cobertura-slice-5c-2-a-nota-do-professor.md`**

Estrutura obrigatória (rigorous §8). Preencha cada célula com o que **foi observado nesta sessão**, copiando o nome do teste do XML; o que não foi observado vai para a última seção.

```markdown
# Cobertura — slice-5c-2-a-nota-do-professor (contrato e servidor)

Data da verificação: <data e hora local>. Comando cheio: `./gradlew build --rerun-tasks` (Docker de pé, sem emulador).
Âncoras (P3): `timestamp` dos XMLs — domínio <…>, API <…>, aparelho <…>.

## Como cada verificação foi vista falhar

| # | Camada | Mutação | O que caiu (nomes, do XML) | O que passou | Revertida e rodada de novo? |
|---|---|---|---|---|---|
| M1 | recusa de 3 casas (domínio) | | | | |
| M2 | exatidão (Double) | | | | |
| M3 | view: exclusão da captura referenciada | | | | |
| M4 | servidor: total declarado | | | | |
| M5 | `@SerialName` nos 3 lados | | | | |
| M6 | guardas `answer-kind.mjs` e `fio.mjs` | | | | |
| M7 | view sem `security_invoker` | | | | |

## O que foi medido (e não suposto)
- `numeric(8,2)` arredonda 3 casas em silêncio: `GradingResultCurrentViewTest.numeric 8,2 arredonda…` (1.333 → 1.33).
- Postgres: `supabase/config.toml` 17; container dos testes `postgres:16-alpine`; `security_invoker` aceito nos dois (a view foi criada nos testes).

## Limitações conhecidas (não mitigadas, P8)
- A numeração de `revision` das folhas avulsas agrupa todas num contador (preexistente, não mudou). A view usa a captura da parcial como chave.
- Duas notas do professor para a mesma captura chegando fora de ordem: vale a chegada ("a mais recente é a corrente"), como na recaptura.
- Reescanear depois da nota faz a captura nova virar a corrente; a nota anterior continua legível.
- Ninguém envia a nota do professor ainda: o aparelho é a 5c-3. A rota foi exercida só por teste de servidor.
- Aplicar a migration em produção: **não** foi feito (`antes-de:migration-da-5-em-producao`).

## O que não foi verificado
- `connectedDebugAndroidTest` (nada de Android de produto mudou).
- <qualquer outra coisa que a execução não cobriu>.

## Para o archive (P27) — o que ele diz de cada linha do §16 que esta mudança alcançou
- **`O teto de 30 dias das respostas só roda quando o aplicativo abre` (`5c`)**: não paga aqui; o veículo passa a ser a **5c-3**; o token segue `5c`.
- **`Migration não é aplicada por nenhum pipeline`** e **`Implantar a API da 5a…`**: existe uma migration nova (`20261001120000`) e a rota mudou; nenhuma das duas foi a produção; os eventos seguem por declarar.
- **`A retenção executável da classe B`**: a nota do professor entra entre os fatos da classe B; sem prazo novo.
- **`A guarda de dívida não lê a tabela "Aberto"` (`5`)**: não tomada aqui; segue `5`, sem mudança própria ainda.
- **`A região discursiva ainda não passou pelo aparelho nem pelo papel`** e **`O limiar do desvio…`** (`6`): sem mudança de estado.
```

- [ ] **Step 11: Commit**

```bash
git add docs/cobertura-slice-5c-2-a-nota-do-professor.md
git commit -m "docs(cobertura): slice-5c-2, como cada verificação foi vista falhar" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 12: Parar aqui**

**Não** rode `/opsx:sync` nem `/opsx:archive` agora, e **não** empurre nem abra PR. Pelo fluxo do projeto: o PR do código sai quando o Leon pedir; o `sync` e o `archive` (com a reconciliação da seção "Para o archive") vão em PR separado **depois do merge**. Diga ao Leon que o código está pronto e verificado, com o que não foi verificado, e espere.

---

## Auto-revisão do plano

**Cobertura do spec (§1–§8) e das specs OpenSpec:**
- D1 (DTO e rota novos) → Tasks 5, 7. D2/D3 (`Pontos`, string) → Tasks 2, 5. D4 (`numeric(8,2)`) → Task 4. D5/D6 (colunas, `discursiva_corrigida`) → Task 4. D7 (view) → Task 4. D8 (`ApuracaoParaEnvio` intacta) → nenhuma tarefa a toca (verificável: `git diff` dos commits não lista `ApuracaoParaEnvio.kt`).
- `scoring`: completar/total exato/fecha só sem pendência objetiva/recusas (faixa, 3 casas, negativa, não numérica, alheia, faltando, repetida, prova só objetiva)/continuam inteiras → Tasks 2–3 (e o objetivo/parcial intactos: Task 6 e os testes existentes).
- `result-sync`: revisão nova + parcial legível, autocontida, reenvio, nova nota, recusas com nada gravado, 1.5/1.75 sem perda, origem/caminho, objetivo e parcial como antes → Tasks 4, 7; "humana vence" nas 4 ordens → Task 4 (banco) e Task 7 (rota); sem imagem/dado pessoal → Tasks 5, 7.
- ADR e `/opsx:update` → Task 1. `answer-kind.mjs`/`fio.mjs` → Task 5. Ver falhar, suíte cheia, cobertura, P27 → Task 8.

**Varredura de placeholders:** nenhum "TBD/TODO/implementar depois". Os únicos campos a preencher são os da tabela de cobertura (Task 8, Step 10), que dependem de execução e dizem de onde copiar.

**Consistência de tipos entre tarefas:** `Pontos` (Task 2) → `PontuacaoDada`/`DiscursivaCorrigida`/`NotaDoProfessor.total` (Task 3) → `NotaDoProfessorSubmetida.totalDeclarado: Pontos` e `paraBigDecimal` (Task 7). `CorrecaoDoProfessor.completar(PartialScore, List<PontuacaoDada>)` (Task 3) é o chamado em `conferirNotaDoProfessor` (Task 7). `GradedResultSubmissionDto` (Task 5) tem `points: String`, `essayGrades: List<EssayGradeDto>`, `completesCaptureId` — usados em `paraNotaSubmetida` e `recordGraded` (Task 7). `Gravacao`/`Evidencia`/`gravar` (Task 6) são os de `recordGraded` (Task 7). `AnswerKind.DISCURSIVA_CORRIGIDA` (Task 5) é o valor gravado em `recordGraded` e o do `check` (Task 4).

**Ordem de dependência dos commits (cada um compila e mantém o CI verde):** 1 docs → 2 `Pontos` → 3 `NotaDoProfessor` → 4 migration + jOOQ (a guarda `answer-kind.mjs` ainda lê o `check` antigo e o domínio ainda tem quatro valores: concordam) → 5 contrato (domínio com 5 valores **e** guarda lendo a última migration, no mesmo commit) → 6 refatoração → 7 rota → 8 cobertura.
