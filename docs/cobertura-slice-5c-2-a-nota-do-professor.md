# Cobertura — slice-5c-2-a-nota-do-professor (contrato e servidor)

Data da verificação: 2026-10-01 (hora local +02:00). Execução inline do plano
`docs/superpowers/plans/2026-10-01-slice-5c-2-a-nota-do-professor.md`; a lista de decisões tomadas durante
a execução está no commit do plano e abaixo, em "Decisões de execução".

**Comando cheio:** `./gradlew --stop && ./gradlew build --rerun-tasks`, com o Docker de pé e **sem** emulador.
Resultado e âncoras (P3): ver "A suíte cheia" no fim.

## Como cada verificação foi vista falhar

Cada mutação foi injetada **sozinha**, rodada, revertida (`git checkout -- <arquivo>`) e rodada **de novo**
com o mesmo comando, e `git status --short` ficou vazio depois de cada uma (P10).

| # | Camada | Mutação | O que caiu | O que passou |
|---|---|---|---|---|
| M1 | Recusa de 3 casas (domínio) | `require(!MUITAS_CASAS…)` comentado em `Pontos.parse` | **só** `PontosTest.tres casas sao recusadas, e nao arredondadas` (a asserção lê a mensagem: sem a guarda, a recusa vem como "nao e uma pontuacao", e o texto "mais de 2 casas" não aparece) | os outros 12 de `PontosTest` |
| M2 | Exatidão (ponto flutuante) | `parse` lendo via `texto.toDouble() * 100` | **só** `PontosTest.valores que o ponto flutuante binario erra continuam exatos` (0.29 → 28, 0.57 → 56, 1.15 → 114) | o resto |
| M3 | View: exclusão da captura referenciada | removido o `where … not exists` de `grading_result_current` | **exatamente 3**: `GradingResultCurrentViewTest.a parcial que chega depois da nota do professor nao e a corrente…`, `GradingResultCurrentViewTest.folha avulsa, a parcial que chega depois da nota nao a substitui`, `GradedResultRouteTest.a parcial que chega depois da nota nao e a corrente…` | 196 de 199, inclusive "parcial antes", "captura nova", "nova nota" e "avulsa": a maior `revision` ainda acerta quando a nota chega depois; só a **chegada tardia da parcial** depende da exclusão |
| M4 | Servidor: total declarado | `if (false && nota.total != …)` em `conferirNotaDoProfessor` | **só** `GradedResultRouteTest.cada recusa e 400…`, no caso `total divergente` (esperava 400, veio 200 e gravou) | os outros 10 da rota e toda a suíte antiga |
| M5 | `@SerialName("essay_grades")` → `essay_notes` | no DTO do domínio | **os três lados**: domínio (`GradedResultSubmissionDtoTest`, 2), servidor (`GradedResultContractTest` e 10 de `GradedResultRouteTest`), aparelho (`GradedResultDtoTest`) | — |
| M6a | Guarda `answer-kind.mjs` (domínio) | `--esperado` sem o valor novo | `exit=1` | — |
| M6b | Guarda `answer-kind.mjs` (migration) | `'discursiva_corrigida'` tirado do `check` | `exit=1` ("o dominio declara … e o check … nao o admite") | — |
| M6c | Guarda `fio.mjs` | `GradedResultDtoTest.kt` (único literal do aparelho) renomeado | `exit=1` | — |
| M7 | View sem `security_invoker` | removido o `with (security_invoker = true)` | 12 testes (`GradingResultCurrentViewTest`: 8, incluindo `a view respeita a organizacao…`; `GradedResultRouteTest`: 4) | — |

## O que foi medido (e não suposto)

- **`numeric(8,2)` arredonda 3 casas em silêncio:** `GradingResultCurrentViewTest.numeric 8,2 arredonda 3 casas em silencio…`
  grava `1.333` e lê `1.33`. É a razão de a recusa ser do domínio (`Pontos.parse`) e de o teste de rota conferir que
  `1.333` dá 400 com **zero** linhas gravadas.
- **Sem `security_invoker`, a view devolve 0 linhas para todos** (sonda descartável, medida em 2026-10-01: `dono=0
  forasteiro=0`; com `security_invoker` o dono vê 1 e o forasteiro 0, `reloptions={security_invoker=true}`). Isto é
  porque a view passa a rodar como `app_owner`, sem bypass de RLS (`roles.sql`: `nobypassrls`), e nenhuma política
  se aplica a ele. **Consequência honesta:** neste desenho a falha sem `security_invoker` é **fechada** (a view some
  para todos), e o teste de isolamento a pega pelo piso ("o dono precisa ver a propria folha"), e **não** por um
  vazamento. O vazamento entre escolas que `security_invoker` impede não é produzível nesta configuração de papéis;
  a asserção do forasteiro (0) é protegida pela RLS da tabela base, que o teste exercita.
- **Postgres:** `supabase/config.toml` declara 17; o container dos testes e da geração do jOOQ é `postgres:16-alpine`;
  `security_invoker` (PG 15+) foi aceito nos dois (a view foi criada em todos os testes de banco).
- **O Gradle não via a mudança de uma opção de view:** no M7, a primeira rodada deu `exit=0` com `:apps:api:test
  UP-TO-DATE` (o teste **não executou**; a migration entra por caminho de diretório, não por conteúdo). Foi refeita com
  `:apps:api:test --rerun`. Vale para qualquer mutação futura **só** na migration: use `--rerun`, ou o verde é de antes.

## Decisões de execução (rulings)

- O veículo dos commits ficou na branch `vewvniv/workflow-openspec-superpowers` (sem push, sem PR); o commit de
  configuração `9717e20` se separa por cherry-pick quando o Leon decidir o PR.
- O `Impact` da proposta foi editado direto, com o texto exato do plano (linha de Impact, não requisito).
- Cinco nomes de teste do plano tinham `:` (ilegal em nome de teste Kotlin): trocados por `,`. O plano foi corrigido.
- `AnswerKindTest` (existente) afirmava os **quatro** valores literais e caiu ao entrar o quinto: a asserção passou a
  afirmar os cinco literais, na ordem do `check` — foi atualizada porque a regra mudou (ADR-0021), e **não** afrouxada.
- `short_id` é único no banco: a prova da organização alheia, no teste de rota, usa outro identificador.

## Limitações conhecidas (não mitigadas, P8)

- A numeração de `revision` das folhas avulsas (token nulo) agrupa todas num contador. **Preexistente, não mudou.** A
  view usa a captura da parcial como chave da folha avulsa.
- Duas notas do professor da mesma captura chegando fora de ordem: vale a chegada ("a mais recente é a corrente"),
  como na recaptura. Não há ordenação lógica entre elas.
- Reescanear depois da nota faz a captura nova virar a corrente; a nota anterior continua legível (spec aprovada).
- **Ninguém envia a nota do professor ainda:** o aparelho é a 5c-3. A rota só foi exercida por teste de servidor.
- O compilador do alvo `js()` do domínio só é exercido pelo `build --rerun-tasks` (ver o fim); `jvmTest` não o cobre.
- A migration **não** foi aplicada em produção (`antes-de:migration-da-5-em-producao`).

## O que não foi verificado

- `connectedDebugAndroidTest`: nenhum código de produto, manifesto ou suíte instrumentada do Android mudou (só um teste
  unitário). Rodar o emulador é ambiente e é pedido do Leon (P22).

## Para o archive (P27): o que ele diz de cada linha do §16 que esta mudança alcançou

- **`O teto de 30 dias das respostas só roda quando o aplicativo abre` (`5c`)**: **não é paga aqui**; o veículo passa a ser
  a **5c-3** (ainda não proposta); o token segue `5c`; a 5c-3 a paga ou reagenda.
- **`Migration não é aplicada por nenhum pipeline`** (`antes-de:migration-da-5-em-producao`) e **`Implantar a API da 5a…`**
  (`antes-de:implantar-api-da-5a`): existe uma migration nova (`20261001120000`) e a rota mudou; nenhuma foi a produção;
  os eventos seguem por declarar.
- **`A retenção executável da classe B`**: a nota do professor entra entre os fatos da classe B; sem prazo novo.
- **`A guarda de dívida não lê a tabela "Aberto"` (`5`)**: não tomada aqui; segue `5`, sem mudança própria ainda.
- **`A região discursiva ainda não passou pelo aparelho nem pelo papel`** e **`O limiar do desvio…`** (`6`): sem mudança de
  estado nesta fatia.

## A suíte cheia (P5)

- **Comando:** `./gradlew --stop && ./gradlew build --rerun-tasks` (Docker de pé, sem emulador), iniciado às
  2026-10-01T17:37:39Z. **`BUILD SUCCESSFUL in 3m 19s`, `183 actionable tasks: 183 executed`** (nenhuma `UP-TO-DATE`:
  é execução, e não relatório antigo). Incluiu `:packages:domain:compileKotlinJs`, que o `jvmTest` não cobre.
- **Âncoras (P3), todas posteriores ao início:** `NotaDoProfessorTest` 13/13 às 17:40:56Z, `GradedResultRouteTest` 11/11 às
  17:40:32Z, `GradedResultDtoTest` (aparelho) 1/1 às 17:39:56Z.
- `./gradlew -p buildSrc test --rerun-tasks`: `BUILD SUCCESSFUL`, 6 de 6 tasks executadas.
- `node tools/parity/answer-kind.mjs` (5 valores concordam) e `node tools/parity/fio.mjs` (7 contratos presos nos dois
  lados): `exit 0`. `node tools/divida/divida.mjs`: `exit 0`, fatia corrente `5c`.
- `openspec validate slice-5c-2-a-nota-do-professor --strict`: válido.
