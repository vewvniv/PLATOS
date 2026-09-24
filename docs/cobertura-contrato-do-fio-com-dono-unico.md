# Cobertura — `contrato-do-fio-com-dono-unico` (ETAPA 6)

**Fecha:** achado 2.1 de `docs/auditoria-2026-09-18-antes-da-fatia-5.md` (grave).
**Veículo:** ADR-0015 + mudança OpenSpec sem delta de spec (`skip_specs: true`).
**Percurso:** ETAPA 6 de `docs/plano-de-correcao-antes-da-fatia-5.md`.
**Base:** `bd2934a` · **Branch:** `vewvniv/contrato-do-fio-com-dono-unico`

---

## 1. O que mudou, em uma frase

Quatro contratos de transporte que eram digitados **duas vezes**, e um mapa `QuestionAnswer → string`
que existia **três vezes**, passaram a ter um declarante em `packages/domain`. Nenhum byte mudou no
fio.

---

## 2. A afirmação central, e como ela foi provada

**A afirmação:** existe agora um dono único, e não apenas um arquivo movido.

**O instrumento:** trocar **um** `@SerialName` no contrato do KMP — `capture_id` → `captureId` — e
exigir que caiam **os dois** testes de literal, o do servidor e o do aparelho.

**Por que essa é a asserção certa, e não outra:** antes desta mudança, essa mutação **não tinha onde
ser injetada**. Não havia um `@SerialName` que os dois lados lessem. Editar o espelho do servidor
derrubaria `ResultRouteTest` e deixaria `ResultadoDtoTest` verde; editar o do aparelho faria o
inverso. **Um vermelho seria "movi o arquivo". Os dois vermelhos, a partir de uma linha, são "existe
um dono único".**

### O conjunto previsto e o conjunto real

`./gradlew build --continue`, `2026-09-19T10:38:26Z`–`10:39:34Z`. **154 suítes executadas, 1445
testes, 12 falhas.**

| Suíte | Previsto | **Real** |
|---|---|---|
| `ResultRouteTest` (servidor) | cai | **caiu — 10 de 11** |
| `ResultadoDtoTest` (aparelho) | cai | **caiu — 2 de 4** |
| qualquer outra | 0 | **0 — 152 suítes, nenhuma falha** |

**REAL = PREVISTO. A regra de parada (§0.5 do plano) não disparou.**

**Os sobreviventes são coerentes, e ficam ditos em vez de omitidos.** Em `ResultRouteTest`
sobreviveu `envio sem credencial e recusado, e nada e gravado`: ele é recusado no 401 **antes** de o
corpo ser desserializado, então o nome do campo nunca chega a importar. Em `ResultadoDtoTest`
passaram os dois cenários que afirmam sobre `student_token` e `answer_kind` — campos que a mutação
não tocou. As 152 suítes intactas são a outra metade da afirmação: a mutação atingiu o contrato, e
não o projeto inteiro.

**Reversão:** `MUTACAO` em código (`.kt`, `.mjs`, `.yml`, `.sql`, `.ts`) fora de `build/` → **0**; o
que resta são as ocorrências em prosa nos `docs/cobertura-*` e `tasks.md`, que é onde a palavra deve
aparecer. `./gradlew build` rodado **depois** da reversão, verde (P10).

---

## 3. O oráculo independente, e por que ele é independente (P4)

Os dois testes de literal — `ResultadoDtoTest` e `ResultRouteTest.corpo()` — **não** foram apagados
nem reescritos. É a decisão 4 do ADR-0015, e era o único ponto desta mudança que, se fosse quebrado,
não teria como ser percebido depois.

**Eles são independentes porque não compartilham código com o que julgam.** O literal é JSON escrito
à mão; o servidor monta o corpo à mão e o envia para a rota real. Reescrevê-los contra
`Json.encodeToString` do tipo novo poria o mesmo código dos dois lados da igualdade — que é
exatamente o defeito que a KDoc de `ResultadoDtoTest` já registrava por escrito.

**Conferido por duas vias, no commit que removeu os espelhos:**

- `git diff --cached --name-status | grep "^D.*[Tt]est"` → **vazio**.
- `ResultadoDtoTest.kt` e `ResultRouteTest.kt` comparados contra `bd2934a`, o commit em que a sessão
  começou: **zero linhas de diff**, depois dos quatro commits.

---

## 4. Que o fio não mudou: a comparação byte a byte (P3)

| | Quando | `sha256` |
|---|---|---|
| Linha de base, **antes** do commit 1 | `2026-09-19T10:14:24Z` | `24e1bb4549564e58b37120fb4e4118831fec5e7c4f147d4095c1e8efe3aa5fce` |
| Regerada, **depois** do commit 3 | `2026-09-19T10:31:27Z` | `24e1bb4549564e58b37120fb4e4118831fec5e7c4f147d4095c1e8efe3aa5fce` |

`diff` → **saída vazia, `exit 0`**. Os dois artefatos são da **mesma sessão**.

**A âncora da linha de base foi conferida, e não suposta:** o corpo do primeiro cenário foi comparado
contra o literal que `ResultadoDtoTest` já fixa — iguais byte a byte. Sem isso, a linha de base seria
"o que o código produz", e não "o que o contrato diz", e comparar contra ela não provaria nada.

**A comparação foi provada reativa** (P13): um espaço acrescentado depois de `"points":1` numa cópia
fez o **mesmo** `diff` sair com `exit 1`. Diferença vazia é indistinguível de um comparador que não
compara.

---

## 5. As duas medições que mudaram decisões

### 5.1 `encodeDefaults`, e o campo que sumiria em silêncio

O `design.md` decisão 6 era **previsão**, e previsão não é medição (P6). Os dois lados divergiam em
algo que não é nome de campo: o servidor declarava `student_token: String? = null` e
`answer_options: List<String> = emptyList()`; o aparelho não declarava default nenhum. Os defaults do
servidor são **tolerância de entrada** — um corpo que omita `student_token` é aceito hoje —, e
removê-los transformaria esse corpo num 400.

Mantê-los alcança o codificador do aparelho, que usa `Json { explicitNulls = true }` com
`encodeDefaults` em `false` por omissão. A decisão foi ligar `encodeDefaults = true`. **E a previsão
foi vista falhar** (P9): desligá-lo derruba **2 dos 4** cenários, com o mecanismo visível na
mensagem.

| Cenário | Sem `encodeDefaults` | O que sumiu do corpo |
|---|---|---|
| `o corpo tem os nomes de campo que a rota espera` | **FAILED** | `"answer_options":[]` do item `em_branco` |
| `folha avulsa manda student_token nulo…` | **FAILED** | `"student_token":null`, o campo inteiro |
| `pendencia viaja com o tipo dela…` | PASSED | nada — nenhum campo dele iguala o default |
| `o corpo nao leva nome, turma…` | PASSED | idem |

Os dois que caem são exatamente os dois que exercitam um campo cujo valor **iguala** o default.

### 5.2 Import explícito vence declaração do mesmo pacote

O plano manda remover os espelhos **só** no commit 4. Mas `paraNota` mora no mesmo pacote onde os
espelhos do servidor estavam declarados. A pergunta — um arquivo que importa
`com.platos.domain.transport.ResultSubmissionDto` e convive com um homônimo do próprio pacote resolve
para qual? — foi **medida**, com sonda e canário, em vez de suposta:

- Sonda: dois tipos homônimos de forma **diferente**, e um uso que só compila se resolver para o do
  "domínio". Com o import: `exit 0`.
- Canário: o mesmo arquivo **sem** o import → `error: unresolved reference 'x'`, `exit 1`, provando
  que aí ele havia resolvido para o do mesmo pacote.

Sem a medição, a saída teria sido mover `paraNota` de pacote — refatoração que o plano não pede
(P19). Com ela, o commit 4 ficou como o plano o escreveu: os espelhos dos **dois** lados saem juntos,
no fim, e os commits 2 e 3 continuam revertíveis por si.

---

## 6. A conferência cruzada com o `check` da migration

O `check` continua sendo o terceiro registro, **de propósito** (ADR-0015 decisão 3): guarda de banco
escrita por quem escreve as linhas aceitaria, por construção, tudo o que o código produzisse. O que
entra é `tools/parity/answer-kind.mjs`, no molde de `limiar.mjs`, lendo os **dois arquivos de
origem**.

**Por que isto precisa de guarda própria:** divergir entre domínio e `check` não quebra teste nenhum.
O Kotlin compila, e os testes de literal passam — eles afirmam o que o aparelho **envia**, não o que
o banco **aceita**. O desencontro só apareceria como `violates check constraint` num `INSERT` em
produção, depois de a folha já ter sido corrigida.

**Visto falhar em cinco condições:**

| Condição | Desfecho | O que ele disse |
|---|---|---|
| árvore como está | `exit 0` | os 4 valores concordam |
| `--esperado …,rasurada` | `exit 1` | nomeia `rasurada` a mais **e** `indecisa` a menos |
| `--esperado` com 3 valores | `exit 1` | nomeia `indecisa` como admitida só pelo `check` |
| mesma lista, outra ordem | `exit 1` | nomeia a divergência de ordem |
| **`INDECISA` mutada no `AnswerKind.kt` real** | `exit 1` | **duas** queixas: a contagem `const val` × `TODOS`, e o `indecisa` só no `check` |

**A quinta é a que importa.** `--esperado` só substitui o lado do domínio: um conferidor que lesse o
arquivo errado passaria nas quatro primeiras linhas. Só mutar a fonte de verdade distingue as duas
coisas.

Dois passos no `ci.yml`, no molde do limiar — um que confere, outro que força divergência e falha se
a conferência aceitar —, **rodados localmente exatamente como o CI os escreve**.

---

## 7. A verificação final

| O quê | Comando | Quando | Desfecho |
|---|---|---|---|
| Build cheio, tudo re-executado | `./gradlew build --rerun-tasks` | `2026-09-19T10:45:32Z`–`10:47:45Z` | **176 de 176 tasks**, 153 suítes, **1444 testes, 0 falhas, 0 erros, 0 ignorados** |
| Testes de `buildSrc` | `./gradlew -p buildSrc test --rerun-tasks` | `2026-09-19T10:48Z` | `BUILD SUCCESSFUL` — roda à parte porque o `build` não os alcança, o que a mudança `generatejooq-sem-registro-automatico` mediu |
| Instrumentados | `ANDROID_SERIAL=emulator-5554 ./gradlew :apps:android:connectedDebugAndroidTest` | `2026-09-19T10:43:24Z`–`10:44:15Z` | **83 casos, 0 caídos, 2 pulados** (as duas sondas, por desenho) |
| Conferência do `answer_kind` | `node tools/parity/answer-kind.mjs` | `2026-09-19T10:47Z` | `exit 0` |
| Conferência do limiar | `node tools/parity/limiar.mjs` | `2026-09-19T10:47Z` | `exit 0` — não é desta mudança; rodado para mostrar que ela não o alcançou |

**As contagens saem dos XML, filtradas pelo atributo `timestamp` de dentro de cada relatório**, e não
da linha final do Gradle.

**Por módulo, no build final:** `packages/domain` 971 · `apps/api` 165 · `apps/android` 308.

**A comparação com a linha de base é exata:** `apps/android` tinha **308** em
`docs/cobertura-o-pendente-nao-se-perde-no-aparelho.md` §8, e tem **308** agora. Os instrumentados
tinham **83 casos com 2 pulados**, e têm **83 com 2 pulados**. A diferença total é **+9**, e os 9 são
`AnswerKindTest` — 3 cenários × 3 alvos (`jvm`, `android`, `js`). **Nada além do que esta mudança
acrescentou.**

### Um número registrado errado, e a correção (P7)

A mensagem do **commit 1** diz "1445 testes". **Está errado.** A âncora usada foi `find -newermt`,
que interpreta a data em **hora local**; a máquina é UTC+2 e a janela do build estava em UTC, então o
filtro varreu desde `08:26Z` e recolheu relatórios de fora da execução — inclusive um de
`2026-09-18T11:32` em `buildSrc`. O número correto daquele build é **129 suítes / 1280 testes**.

A mensagem do commit fica como está, e a correção vive aqui e no `tasks.md`: reescrever a história
esconderia o defeito em vez de registrá-lo. **A âncora certa é o atributo `timestamp` de dentro de
cada XML**, que é UTC e descreve a execução que o escreveu — não a hora em que o arquivo foi tocado.

---

## 8. O que **não** foi verificado (P8)

- **O CI não rodou nesta sessão.** Todos os sinais acima são locais, em **Windows**, e o job `build`
  do CI roda em **Linux**. Os dois passos novos do `ci.yml` foram executados localmente com os mesmos
  comandos, mas "o passo funciona" não é "o job passou". É o que o PR desta etapa vai dizer, e é a
  razão pela qual a banda passou a ter um PR por etapa em vez de um merge único.
- **Nenhum servidor real recebeu um corpo produzido por este código.** A igualdade byte a byte prova
  que o corpo não mudou; ela não prova que o servidor de produção o aceita — isso quem prova é
  `ResultRouteTest`, que bate na rota real, mas contra um Postgres de Testcontainers.
- **A rota de roster e a de organizações não têm teste de literal do lado do aparelho.** Elas têm
  `ObtencaoDeRosterTest` e `ApiPlatosTest`, que prendem o JSON, mas a mutação decisiva deste registro
  foi feita sobre `capture_id`, no contrato de resultado. **Os outros três contratos não foram
  submetidos à mesma mutação.** O que se afirma deles é mais fraco: que compilam contra um
  declarante único e que a suíte inteira passa.

  > **Corrigido em 2026-09-23: o item acima olhou para o lado errado da porta (P7).** O aparelho tem
  > literal escrito à mão para os **quatro** contratos — `ApiPlatosTest:38` (organização),
  > `ApiPlatosPacoteTest:32` (prova), `ObtencaoDeRosterTest:53` (roster) e `ResultadoDtoTest`
  > (resultado). O lado que falta é o **servidor**, e em dois dos quatro:
  >
  > | Contrato | Teste do servidor | O fio está preso? |
  > |---|---|---|
  > | resultado | `ResultRouteTest.corpo()`, literal | sim — **medido**, §2 acima |
  > | roster | `ExamPackageRouteTest:281`, literal | sim, por leitura |
  > | organização | `MeOrganizationsTest:112` desserializa com o próprio `OrganizationDto` | **não**. Só `kind`, e por acaso: `AuthenticationTest:131` procura `"kind":"personal"` no corpo, num teste de isolamento, e não de contrato |
  > | prova | `ExamPackageRouteTest:359` desserializa com o próprio `ExamSummaryDto` | **não**, nenhum campo |
  >
  > Desserializar com o tipo que a rota usa para escrever o corpo põe o mesmo código dos dois lados
  > da igualdade — é P4, e é o que a decisão 4 do ADR-0015 proibiu para os testes de literal. Os dois
  > testes ainda leem com `Json { ignoreUnknownKeys = true }`, e por isso também não veem campo a
  > mais. O argumento já estava escrito no mesmo arquivo, 90 linhas acima do `provasDe`
  > (`ExamPackageRouteTest.kt:261-267`), e foi aplicado só ao roster.
  >
  > **Não é defeito desta mudança: já era assim.** Em `bd2934a`, o commit em que esta sessão começou,
  > os dois testes já desserializavam com o `OrganizationDto` e o `ExamSummaryDto` **do próprio
  > servidor** — desde `ac10698` (2026-08-13) e `112c8e6` (2026-09-08). O que a unificação fez foi
  > torná-lo visível: só com um declarante único a mutação da decisão 4 passa a ter onde ser injetada,
  > e nesses dois contratos ela derrubaria **um** lado — que é, com as palavras do ADR-0015, "o outro
  > lado não está preso".
  >
  > **Tipo desta afirmação: conferido por leitura, e não medido (P6).** Nenhuma mutação rodou sobre os
  > três contratos. A medição e a correção são a ETAPA 7.3 de
  > `docs/plano-de-correcao-antes-da-fatia-5.md`, mudança `o-fio-preso-nos-dois-lados`, que bloqueia
  > a fatia 5.
  >
  > **Medido no mesmo dia, horas depois:** `@SerialName` trocado em `name` e em `title` derrubou só o
  > aparelho (6 cenários em `ApiPlatosTest`, 1 em `ApiPlatosPacoteTest`), com `MeOrganizationsTest` e
  > `ExamPackageRouteTest` rodando verdes na janela; em `display_name`, caíram os dois lados. Real =
  > previsto nas três: `docs/cobertura-o-fio-preso-nos-dois-lados.md`, Parte I.
  >
  > **Corrigido no mesmo dia:** com os literais novos do servidor, as três mutações derrubam os dois
  > lados — `docs/cobertura-o-fio-preso-nos-dois-lados.md`, Parte II.
- **`packages/domain` compila os DTOs também para o alvo `js`, que não os consome.** Isso não foi
  medido como custo; é observação de desenho.
- **Os quatro arquivos do aparelho continuam com nome de DTO sem declarar DTO nenhum**
  (`ProvaDto.kt` etc.). Não é defeito e está dito na KDoc de `ProvaDto.kt`; renomear seria
  refatoração fora de escopo (P25). **Item para quem passar por ali**, sem fatia-limite: é higiene,
  não risco.

---

## 9. O que fica para a ETAPA 7

Nada desta etapa. A ETAPA 7 constrói `tools/parity/renderizador.mjs` **no mesmo molde** de
`answer-kind.mjs` e de `limiar.mjs` — três instâncias nomeadas do mesmo instrumento, e não um
conferidor genérico, que é o que a regra 8 do `CLAUDE.md` proíbe e o que a própria ETAPA 7 já veda
por escrito.

> **Corrigido em 2026-09-23 (P7):** "nada desta etapa" deixou de ser verdade. A correção do §8 —
> dois dos quatro contratos sem literal do lado do servidor — virou a **7.3** do plano, mudança
> `o-fio-preso-nos-dois-lados`, que corre antes da 7.1 e bloqueia a fatia 5.
> **Feita em 2026-09-23** — o archive e a leitura do CI da PR ainda pendentes:
> `docs/cobertura-o-fio-preso-nos-dois-lados.md`, Parte II. O CI da PR (#59) foi lido no mesmo dia,
> verde nos três jobs; falta o archive.
