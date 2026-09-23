## 0. Antes de qualquer commit — o ambiente e a linha de base

**O passo "ver o buraco" do plano não é tarefa desta mudança: foi feito antes de ela existir.** As
três mutações rodaram em 2026-09-23, `12:01Z`–`12:12Z`, com real = previsto nas três, e o registro
está em `docs/cobertura-o-fio-preso-nos-dois-lados.md`, Parte I. Ele não é marcado aqui porque não
roda nesta sessão (P1). A tabela do plano fica como previsão registrada, com o real ao lado na
cobertura.

- [x] 0.1 **Perguntar antes de tocar no ambiente** (P22, regra 0.9 do plano). Os testes de rota do
  servidor precisam de Docker, e na Parte I ele foi ligado pelo mantenedor. Confirmar com ele, e
  não ligar sozinho, inclusive em modo automático. **Emulador não é pedido**: nenhum arquivo de
  `apps/android` muda. Verificar: `docker info` responde, e a confirmação fica registrada aqui.

  **Confirmado pelo mantenedor em 2026-09-23** ("Docker ligado"), e ligado por ele, não por esta
  sessão. `docker info` às `13:03:44Z` → `exit 0`, Docker Desktop, servidor `29.7.2`, um contêiner
  rodando. Nenhum emulador foi subido, nenhum aparelho tocado.
- [x] 0.2 **A linha de base desta sessão** (P3): sem ela, uma queda sob mutação não teria a quem ser
  atribuída. `./gradlew build --continue --rerun-tasks`, com a contagem feita pelo atributo
  `timestamp` de **dentro** de cada `TEST-*.xml`, e não pela data do arquivo (o erro registrado na
  1.3 da ETAPA 6). Verificar contra a Parte I, §1: **153 suítes, 1444 testes, 0 falhas**. Se
  diferir, parar e explicar a diferença antes do commit 1.

  **Bateu, número a número.** Na branch `vewvniv/o-fio-preso-nos-dois-lados`, criada sobre
  `d7e10f4`: `./gradlew build --continue --rerun-tasks`, `13:04:18Z`–`13:06:31Z`, `exit 0`,
  `BUILD SUCCESSFUL`, **176 de 176 tasks executadas**. Pelo `timestamp` de dentro dos XML
  (`13:05:25Z`–`13:06:28Z`): **153 suítes, 1444 testes, 0 falhas, 0 erros, 0 pulados** —
  `apps/android` 308, `apps/api` 165, `packages/domain` 329 + 321 + 321. Um relatório ficou fora da
  janela, o de `buildSrc` (`AquisicaoDeConexaoTest`, de 2026-09-19), como na Parte I §4: o `build`
  não alcança os testes de `buildSrc`. O contador é um script temporário no scratchpad, fora da
  árvore; que ele filtra de fato ficou visto nesse relatório excluído.

  **A caixa desta tarefa ficou desmarcada no commit `68c20a0`, e a mensagem dele diz que ela estava
  marcada (P7).** O registro acima entrou, a troca de `[ ]` por `[x]` não, e ninguém releu. Marcada
  no commit 2, pela execução de `13:04Z`, que é desta sessão. A mensagem de `68c20a0` fica como está.

## 1. Commit 1 — a guarda, e ela nasce vermelha

- [x] 1.1 Escrever `tools/parity/fio.mjs` conforme as decisões 1 a 6 do `design.md`: raiz de
  `transport/` recursiva e lida da fonte, com comentários removidos; a forma que ela sabe ler, e
  `exit 2` com o tipo e o motivo para qualquer outra; o literal por string, com os dois lados
  nomeados em constantes no topo; a saída `0`/`1`/`2` da decisão 4, e no `0` a lista de quem prende
  cada par; o piso; e `--transport <dir>` como único parâmetro. A KDoc do cabeçalho no molde de
  `answer-kind.mjs` — por que ela existe, o que ela **não** prova (decisão 11), uso. Verificar:
  `node --check tools/parity/fio.mjs` sai `0`, e o arquivo só importa de `node:`.

  **Feito.** `node --check` → `exit 0`. Três imports, todos de `node:` — `node:fs`, `node:path` e
  **`node:url`**. O terceiro não estava no `design.md` (decisão 1 diz "só `node:fs` e `node:path`,
  como `answer-kind.mjs`"), e fica dito em vez de escondido: `fileURLToPath` resolve a raiz do
  repositório a partir do próprio script, para que a guarda não dependa do diretório de onde é
  chamada — `limiar.mjs` depende, `answer-kind.mjs` não. Continua sem dependência de npm.
  **A guarda não foi executada nesta tarefa**: o primeiro contato dela com a árvore é o de 1.3.

  O que ela lê, além do que a decisão 2 lista: `@kotlinx.serialization.Serializable` qualificado
  conta como `@Serializable` — o primeiro canário da Parte I §6 passou despercebido justamente por
  estar qualificado —, e `import kotlinx.serialization.Serializable as X` reprova com `2`, porque a
  guarda procura o nome. Anotação de classe que não seja `@SerialName`, genérico, construtor com
  modificador e supertipo também reprovam com `2`, pela mesma regra de falhar fechado.
- [x] 1.2 Dois passos no `ci.yml`, no job `web`, logo depois dos de `answer_kind` (decisão 7): um
  que confere; outro que planta, **fora do checkout**, um `FantasmaDto` sem literal numa cópia
  temporária de `transport/` e exige `exit 1` **com** o `FantasmaDto` nomeado nos dois lados, e
  aponta a guarda para um diretório vazio e exige `exit 2` **com** o motivo do piso. O comentário no
  tom dos vizinhos. Verificar: o `ci.yml` continua YAML válido, com o instrumento dito — e os passos
  **não** rodam ainda (1.5): o primeiro contato da guarda com a árvore é o de 1.3.

  **Feito, e não rodado.** Os dois passos estão no job `web`, nas posições 19 e 20, entre "A
  verificacao de answer_kind continua capaz de falhar" e "As fixtures da digitalizacao estao em
  dia". **YAML válido conferido por `yaml.safe_load` do PyYAML**, que leu o arquivo inteiro e achou os
  dois nomes na ordem certa. O segundo passo exige, **para cada lado**, `exit 1` e a linha
  `FantasmaDto (…) nao tem literal escrito a mao do lado do <lado>`; e, para a raiz vazia, `exit 2`
  e `piso:` na saída. A saída capturada só é impressa quando o passo falha, então os `::error::` do
  defeito plantado não viram anotação no CI verde.
- [x] 1.3 **O primeiro vermelho, sobre a árvore real, sem nada plantado.** Rodar
  `node tools/parity/fio.mjs` e registrar a saída inteira, o código e a hora UTC. Conjunto previsto
  (plano, 7.3, commit `460962c`; a linha "saída" é acréscimo do `design.md`, decisão 8):

  | Tipo | Servidor | Aparelho | **Real** |
  |---|---|---|---|
  | `OrganizationDto` | **nomeado** | não | **nomeado no servidor**; aparelho `ApiPlatosTest.kt:38` |
  | `ExamSummaryDto` | **nomeado** | não | **nomeado no servidor**; aparelho `ApiPlatosPacoteTest.kt:32` |
  | `RosterEntryDto` | não | não | servidor `ExamPackageRouteTest.kt:281` · aparelho `ObtencaoDeRosterTest.kt:53` |
  | `ResultSubmissionDto` | não | não | servidor `ResultRouteTest.kt:105` · aparelho `ResultadoDtoTest.kt:65` |
  | `AnswerObservationDto` | não | não | servidor `ResultRouteTest.kt:105` · aparelho `ResultadoDtoTest.kt:65` |
  | saída | `1` | | **`1`** |

  **Regra de parada** (decisão 10): se nomear outro tipo, o lado do aparelho, ou sair `2`, a guarda
  ou a leitura está errada — **parar** e escrever o real ao lado do previsto. Não ajustar a leitura
  para caber na previsão.

  **REAL = PREVISTO. A regra de parada não disparou.** `node tools/parity/fio.mjs` às `13:11:05Z`,
  com `git status` mostrando só `ci.yml`, `tasks.md` e `fio.mjs` — nada plantado. `exit 1`. A guarda
  leu **5 tipos em 4 arquivos** de `transport/` (o que o `design.md` afirmava por leitura), **1164
  literais em 28 arquivos** do servidor e **822 em 30** do aparelho. As duas linhas de erro, como
  saíram:

  ```
  ::error::ExamSummaryDto (packages/domain/.../transport/ExamDto.kt) nao tem literal escrito a mao do lado do servidor: nenhuma string de apps/api/src/test traz juntas as chaves "short_id", "title", "content_hash"
  ::error::OrganizationDto (packages/domain/.../transport/OrganizationDto.kt) nao tem literal escrito a mao do lado do servidor: nenhuma string de apps/api/src/test traz juntas as chaves "id", "name", "kind", "role"
  ```

  **O que a lista dos pares presos diz, e que a tabela do plano não dizia.** A linha é a do primeiro
  literal que satisfaz, um por arquivo. No resultado, o servidor está preso em
  `ResultRouteTest.kt:105` — o corpo à mão do cenário "nota declarada fechada com pendência" —, e
  não em `corpo()`, que o plano cita. É o mesmo arquivo; que `corpo()` (`:349`) também traz as
  nove chaves juntas é **leitura**, e não saída da guarda, porque ela imprime **um** literal por
  arquivo — e isso fica dito. `AnswerObservationDto` está preso
  pelos **mesmos** literais do `ResultSubmissionDto`, porque as observações moram dentro do corpo do
  envio — que é o que o plano chama de "o contrato de resultado".
- [x] 1.4 **Os canários da leitura** (decisão 3), antes de o instrumento ser acreditado — o §6 da
  Parte I registra uma contagem por texto enganada duas vezes. Um por vez, marcado com `MUTACAO`,
  revertido e com a guarda rodada de novo antes do seguinte (P10). C1–C4 em `MeOrganizationsTest`,
  onde `OrganizationDto` ainda falta; C5 por `--transport` sobre uma cópia temporária, sem tocar
  `transport/`:

  | Canário | Previsto | **Real** |
  |---|---|---|
  | C1 · as quatro chaves de `OrganizationDto` juntas, **num comentário** | continua nomeado no servidor | **continua nomeado**, `exit 1` |
  | C2 · as quatro chaves partidas em **dois literais** unidos por `+`, duas em cada | continua nomeado | **continua nomeado**, `exit 1` |
  | C3 · as quatro chaves juntas numa **string comum com aspas escapadas** (`\"id\":…`) | **deixa** de ser nomeado | **deixou** — preso em `MeOrganizationsTest.kt:26` |
  | C4 · as quatro chaves juntas num literal cru com um **template que contém string** (`"id":"${"x".repeat(2)}"`, e chaves depois dele) | **deixa** de ser nomeado | **deixou** — preso em `MeOrganizationsTest.kt:26` |
  | C5 · um tipo `@Serializable` de forma que ela não lê (`enum class`; e, à parte, um parâmetro com `@Transient`) | `exit 2`, nomeando o tipo e o motivo | **`exit 2` nos dois**, com `forma: CanarioEnum … e \`enum class\`` e `forma: CanarioTransient … no parametro \`b\` … @Transient` |

  C1 e C2 provam que ela não aceita o que não devia; C3 e C4, que ela vê o que devia; C5, que ela
  falha fechado. **Canário que falha quer dizer leitura errada**: corrigir, e rodar tudo de novo **a
  partir de 1.3**, com as duas execuções escritas (decisão 10).

  **REAL = PREVISTO nos cinco**, `13:12:18Z`–`13:12:45Z`. **Nenhuma leitura foi corrigida**, e por
  isso o vermelho de 1.3 continua sendo o primeiro, sem segunda execução a registrar.

  **Como cada canário foi montado para poder falhar**, porque um canário que passaria com a leitura
  quebrada não prova nada (P13):
  - **C1 vai em duas linhas.** Uma de comentário de linha com um literal cru `"""…"""` inteiro
    dentro, e outra de comentário de **bloco aninhado**:
    `/* … /* aninhado */ """[{"id":…}]""" */`. Se a leitura não aninhasse, o `*/` de dentro fecharia
    o comentário, e o literal cru que vem depois viraria código — **um literal só, com as quatro
    chaves** —, e `OrganizationDto` deixaria de ser nomeado: um verde falso visível.
  - **C1 tem controle, e ele é um acréscimo à tabela:** o **mesmo** literal cru, fora do comentário
    (`MUTACAO-C1-controle`), **satisfez** (`MeOrganizationsTest.kt:26`). Sem ele, "continua
    nomeado" em C1 seria indistinguível de "a guarda não lê literal cru nenhum". A única diferença
    entre o controle e o C1 é o comentário.
  - **C4** só deixa de ser nomeado se o `"x"` de dentro do template **não** encerrar a string de
    fora. Se encerrasse, `"name"`, `"kind"` e `"role"` cairiam noutro literal, longe de `"id"`, e
    o tipo continuaria nomeado.
  - **C5 correu sobre cópias temporárias** (`--transport`), uma por forma: `transport/` não foi
    tocado (`git diff --stat` vazio). As duas cópias leram os cinco tipos reais e **não**
    contaram o canário como contrato.

  **Procedimento, e a reversão conferida em cada um** (P10): um script temporário no scratchpad
  planta o trecho depois da linha 25 de `MeOrganizationsTest.kt`, roda a guarda, **restaura por
  cópia** do original salvo (`sha256 6b03160c…`), confere `git diff --quiet` no arquivo e roda a
  guarda de novo. Nos cinco: `git diff` vazio e `exit 1` depois da reversão — o estado de 1.3.
  `grep -rn "MUTACAO"` em código fora de `build/` → **0** ao fim.
- [x] 1.5 Rodar localmente, em bash, **exatamente como o `ci.yml` os escreve**, os dois passos de
  1.2. Verificar: o primeiro sai diferente de zero nomeando os dois tipos de 1.3 (o vermelho
  esperado deste commit); o segundo passa, dizendo que a guarda acusou o `FantasmaDto` nos dois
  lados e o piso.

  **Os dois, como previsto.** O `run` de cada passo foi **extraído do `ci.yml` pelo PyYAML** — e não
  copiado à mão — e rodado com `bash -e`, que é o shell padrão do Actions no Ubuntu. Passo 1, às
  `13:13:36Z`: `exit 1`, com as duas linhas de 1.3 (`ExamSummaryDto` e `OrganizationDto`, lado do
  servidor). Passo 2, às `13:13:37Z`: `exit 0`, "a verificacao do fio acusou o contrato sem literal
  nos dois lados, e o piso, como deve".

  **E o passo 2 foi visto reprovar, ramo por ramo** (P9, P13) — porque um passo "capaz de falhar"
  que não reprova nada é o mesmo defeito, um nível acima. Cada defeito plantado **na guarda**,
  marcado com `MUTACAO`, e `fio.mjs` restaurado por cópia (`cmp` igual) antes do seguinte:

  | Defeito plantado na guarda | Passo 2 | A mensagem |
  |---|---|---|
  | sai `0` sempre | **reprovou** | "…nao acusou o contrato sem literal do lado do servidor (saida 0)" |
  | sai `1` sem nomear nada | **reprovou** | "…do lado do servidor (saida 1)" — o código certo com o motivo errado não passa |
  | sai `2` sempre | **reprovou** | "…do lado do servidor (saida 2)" |
  | o lado do aparelho sai de `LADOS` | **reprovou** | "…nao acusou o contrato sem literal **do lado do aparelho** (saida 1)" |
  | o piso da raiz vazia sai `0` | **reprovou** | "…**aceitou uma raiz de contratos vazia** (saida 0)" |

  Os três primeiros caem na primeira conferência do passo. Os dois últimos **isolam** as outras
  duas — o lado do aparelho e o piso —, cada um com o resto da guarda funcionando. Depois da
  reversão: passo 2 `exit 0`, e `MUTACAO` em código fora de `build/` → **0**.
- [x] 1.6 Commit 1. `grep -rn "MUTACAO"` em código (`.kt`, `.kts`, `.mjs`, `.yml`, `.sql`, `.ts`)
  fora de `build/` → `0`, e o diff de código do commit é só `tools/parity/fio.mjs` e `ci.yml`. A
  mensagem diz que o commit **nasce vermelho**, por quê, e traz os dois nomes de 1.3 — como o
  commit 1 da ETAPA 3.

  **`455d24b`.** `MUTACAO` → **0**; `git diff --cached --stat`: `fio.mjs` (+466), `ci.yml` (+43) e
  este `tasks.md`; `git diff --cached --check` sem erro. A mensagem abre com "ESTE COMMIT NASCE
  VERMELHO", o porquê, e as duas linhas de 1.3. O `fio.mjs` tem **466 linhas**, contra ~100 de
  `limiar.mjs` e `answer-kind.mjs`: a diferença é a leitura de Kotlin, e está dita na mensagem.

## 2. Commit 2 — os dois literais do servidor

- [x] 2.1 `MeOrganizationsTest` ganha um cenário que compara o corpo inteiro de `GET
  /me/organizations` do primeiro acesso contra
  `[{"id":"<id>","name":"Nova Professora","kind":"personal","role":"owner"}]`, escrito à mão, por
  igualdade, com o `id` de `idDaOrganizacaoPessoal` (decisão 9). Os cinco cenários existentes e
  `organizacoesDe` não mudam. Verificar:
  `./gradlew :apps:api:test --tests '*MeOrganizationsTest*'` → **6 testes, 0 falhas**, com o
  `timestamp` do relatório.

  **Feito.** Cenário `o corpo tem os nomes de campo que o aparelho le`, com o literal numa string só
  — a KDoc diz por quê: a guarda só conta chaves juntas, e o C2 mediu isso. Rodado junto com o de
  2.2 (`--tests` das duas classes), `13:15:42Z`–`13:16:00Z`, `exit 0`: **6 testes, 0 falhas**,
  `timestamp="2026-09-23T13:15:57.613Z"`, e o cenário novo está no relatório pelo nome. **Verde de
  primeira não prova nada sobre ele** — quem prova é a mutação `nome` de 3.1.
- [x] 2.2 `ExamPackageRouteTest` ganha um cenário que compara o corpo inteiro de `GET
  /organizations/{id}/exams` contra
  `[{"short_id":"mat-7a-2026-1","title":"Prova de Matematica","content_hash":"<hash>"}]`, escrito à
  mão, por igualdade, com o hash de `PostgresSupport.sha256Hex` (decisão 9). Os cenários existentes
  e `provasDe` não mudam. Verificar:
  `./gradlew :apps:api:test --tests '*ExamPackageRouteTest*'` → **19 testes, 0 falhas**, com o
  `timestamp`.

  **Feito.** Cenário `a listagem tem os nomes de campo que o aparelho le, sem envelope e sem campo a
  mais`, no fim da seção de listagem, com o hash por `sha256Hex` antes da publicação, como o
  primeiro cenário da listagem. Mesma execução de 2.1: **19 testes, 0 falhas**,
  `timestamp="2026-09-23T13:15:49.958Z"`, com o cenário novo pelo nome. A prova de que ele prende o
  fio é a mutação `titulo` de 3.1.
- [x] 2.3 **A guarda fica verde, e a lista de quem a satisfez é lida.** `node tools/parity/fio.mjs`
  → `exit 0`. Conferir cada par contra o esperado:

  | Tipo | Servidor — esperado | Aparelho — esperado | **Real** |
  |---|---|---|---|
  | `OrganizationDto` | `MeOrganizationsTest` | `ApiPlatosTest` | **`MeOrganizationsTest.kt:127`** · `ApiPlatosTest.kt:38` |
  | `ExamSummaryDto` | `ExamPackageRouteTest` | `ApiPlatosPacoteTest` | **`ExamPackageRouteTest.kt:129`** · `ApiPlatosPacoteTest.kt:32` |
  | `RosterEntryDto` | `ExamPackageRouteTest` | `ObtencaoDeRosterTest` | `ExamPackageRouteTest.kt:310` · `ObtencaoDeRosterTest.kt:53` |
  | `ResultSubmissionDto` | `ResultRouteTest` | `ResultadoDtoTest` | `ResultRouteTest.kt:105` · `ResultadoDtoTest.kt:65` |
  | `AnswerObservationDto` | `ResultRouteTest` | `ResultadoDtoTest` | `ResultRouteTest.kt:105` · `ResultadoDtoTest.kt:65` |

  Arquivo **a mais** na lista é lido e explicado por escrito — coincidência ou literal legítimo. Par
  preso **só** por arquivo inesperado é a lacuna de coincidência do `design.md` acontecendo: parar e
  escrever.

  **REAL = ESPERADO, par a par, e nenhum arquivo a mais.** `node tools/parity/fio.mjs` às
  `13:16:08Z` → **`exit 0`**, "os 5 contratos estao presos por literal nos dois lados". As linhas
  novas são os dois cenários de 2.1 e 2.2; o roster passou de `:281` para `:310` só porque o cenário
  de 2.2 entrou acima dele.

  **Uma conferência a mais da leitura, que saiu de graça:** o servidor tinha **1164** literais em
  1.3 e tem **1176** agora. Os **12** a mais são exatamente as strings que os dois cenários
  acrescentam, contadas à mão: 7 em `MeOrganizationsTest` (a rota, o `Bearer ${…}` e as três strings
  do template dele, o `"sub-fio"` da consulta, o literal) e 5 em `ExamPackageRouteTest` (as duas do
  `createExam`, a rota, o `Bearer ${…}`, o literal). As KDoc novas, que citam `"nome"`, não entraram
  na conta — comentário não é literal.
- [x] 2.4 **O que não pode ter mudado.** `git diff` do commit: nenhum arquivo de `transport/`; nenhum
  arquivo de `apps/android`; `AuthenticationTest.kt` intocado; em `MeOrganizationsTest.kt` e
  `ExamPackageRouteTest.kt`, só linhas **acrescentadas** — nenhum cenário que desserializa foi
  reescrito ou removido.

  **Conferido no diff, antes do commit.** `git diff --name-only`: os dois testes e este `tasks.md`,
  e nada mais — zero arquivos de `transport/`, de `apps/android` ou `AuthenticationTest`.
  `git diff --numstat`: **+29 / −0** em cada um dos dois testes. Nenhuma linha removida quer dizer
  que nenhum cenário existente foi tocado; e `organizacoesDe` e `provasDe` continuam com o
  `json.decodeFromString(resposta.bodyAsText())` deles.
- [x] 2.5 `./gradlew build` verde, com contagem e `timestamp` (P2, P3): `apps/api` passa de **165**
  para **167**, e nada mais muda. Commit 2.

  **`./gradlew build`, `13:17:16Z`–`13:17:39Z`, `exit 0`, 176 tasks (7 executadas, 169
  `UP-TO-DATE`).** Pelo `timestamp` de dentro dos XML (`13:17:21Z`–`13:17:35Z`): **25 suítes, 167
  testes, 0 falhas**, todas de `apps/api` — o único módulo que este commit toca. Os demais ficaram
  com os relatórios da linha de base de 0.2, **fora da janela**, e não são citados como execução
  deste build (P2). O `--rerun-tasks` de tudo é o da 3.4.

## 3. Ver falhar (P9), depois do commit 2

A regra de parada da decisão 10 vale sobre cada tabela deste grupo: conjunto real diferente do
previsto — **mais, menos, ou outros** — é parar e escrever, e nunca consertar o instrumento, afrouxar
a asserção ou ajustar a previsão em silêncio.

- [x] 3.1 **As três mutações da Parte I, repetidas** — uma por vez, marcada com `MUTACAO`, com
  `./gradlew build --continue`, revertida por cópia do original e rodada antes da seguinte (P10).
  Contagem pelo `timestamp` de dentro de cada XML. O conjunto previsto, copiado do plano como está:

  | Mutação | Aparelho | Servidor |
  |---|---|---|
  | `name` → `nome` | `ApiPlatosTest` cai | o cenário novo de `MeOrganizationsTest` **cai** |
  | `title` → `titulo` | `ApiPlatosPacoteTest` cai | o cenário novo de `ExamPackageRouteTest` **cai** |
  | `display_name` → `displayName` | `ObtencaoDeRosterTest` cai | o cenário do roster cai |
  | qualquer outra suíte | 0 | 0 |

  > Os cenários que desserializam **não** caem em nenhuma das três — mesmo código dos dois lados da
  > igualdade. Se caírem, eles não eram o que este plano diz que são: pare.

  **As contagens que a tabela implica, derivadas e não acrescentadas:** no aparelho, as da Parte I
  (6 de 14, 1 de 7, 6 de 10), porque nenhum teste de lá mudou; no servidor, **exatamente um**
  cenário por mutação — 1 de 6 em `MeOrganizationsTest` sob `nome`, 1 de 19 em
  `ExamPackageRouteTest` sob `titulo` e sob `displayName`, e esses dois são cenários **diferentes**.
  Registrar o real ao lado, com o mecanismo lido na mensagem, e não só na contagem (P12): no
  servidor, a igualdade literal que falha. Sobrevivente numa classe que caiu é lido e explicado.

  **REAL = PREVISTO nas três, e agora cada uma derruba os dois lados.** Cada mutação numa linha só
  (o script aborta se o diff não for de uma linha), com `./gradlew build --continue`, e **as 153
  suítes dentro da janela** nas três — as tasks de teste dependem de `packages/domain`:

  | Mutação | Janela | Aparelho — real | Servidor — real | Outras |
  |---|---|---|---|---|
  | **A** · `name` → `nome` | `13:18:38Z`–`13:20:06Z` | `ApiPlatosTest` **6 de 14** | `MeOrganizationsTest` **1 de 6** — o cenário novo | **0** |
  | **B** · `title` → `titulo` | `13:20:25Z`–`13:21:30Z` | `ApiPlatosPacoteTest` **1 de 7** | `ExamPackageRouteTest` **1 de 19** — o cenário novo da listagem | **0** |
  | **C** · `display_name` → `displayName` | `13:21:39Z`–`13:22:38Z` | `ObtencaoDeRosterTest` **6 de 10** | `ExamPackageRouteTest` **1 de 19** — o cenário do roster | **0** |

  **As contagens derivadas bateram todas:** as do aparelho são as da Parte I, uma a uma; no
  servidor, um cenário por mutação, e os de B e C são **diferentes** (a listagem e o roster).

  **O mecanismo, lido na mensagem** (P12). No aparelho, nas três, `JsonConvertException: Illegal
  input: Field '<nome mutado>' is required for type with serial name
  'com.platos.domain.transport.<Tipo>'` — o decodificador procurando o nome novo num corpo escrito
  com o antigo. No servidor, nas três, `AssertionFailedError: expected: <[{…"name":"Nova
  Professora"…}]> but was: <[{…"nome":"Nova Profes…` (e o mesmo com `"title"`/`"titulo"` e
  `"display_name"`/`"displayName"`) — a igualdade literal, com o nome de campo digitado.

  **Os sobreviventes.** No aparelho são os mesmos da Parte I §2, e pelas mesmas razões — nenhum
  teste de lá mudou. No servidor, **os cenários que desserializam não caíram em nenhuma das três**,
  como o plano previu: sob A, os cinco outros de `MeOrganizationsTest` (quatro leem com o próprio
  `OrganizationDto` e o quinto é o 401, que nem decodifica); sob B, os de listagem que usam
  `provasDe`. É P4 visto de novo, agora ao lado do cenário que cai: a mesma rota, o mesmo corpo
  mutado, um verde e um vermelho — e a diferença é só se o nome do campo está digitado.

  **Reversão por cópia do original, conferida em cada uma** — `cmp` igual e `git diff` vazio no
  arquivo — antes da seguinte, e cada build seguinte rodou sobre a reversão da anterior: B e C
  não mostraram nenhuma queda de A ou de B. `MUTACAO` em código fora de `build/` → **0** ao fim. O
  `build --rerun-tasks` depois das reversões é o da 3.4.
- [x] 3.2 **A guarda, com defeito plantado, um lado de cada vez.** (a) Um contrato de mentira em
  `transport/`, marcado com `MUTACAO`, sem literal em lado nenhum: a guarda o nomeia **nos dois
  lados**, `exit 1`, e nada mais é nomeado. (b) O literal do roster retirado de
  `ExamPackageRouteTest` — basta que o servidor deixe de ter string com as duas chaves juntas: a
  guarda nomeia `RosterEntryDto` **só do lado do servidor**, `exit 1`. A (b) é a que prova que ela
  distingue os lados, e não só a presença do tipo. Cada uma revertida e com a guarda rodada de novo
  antes da seguinte.

  **REAL = PREVISTO nas duas.**

  | Defeito | Hora | Previsto | **Real** |
  |---|---|---|---|
  | (a) `MentiraDto(@SerialName("campo_de_mentira") …)` em `transport/MentiraMutacao.kt` | `13:23:37Z` | nomeado nos dois lados, `exit 1`, nada mais | **`MentiraDto` nomeado no servidor e no aparelho, `exit 1`, e só ele** — 6 tipos lidos em 5 arquivos |
  | (b) `"display_name":` → `"display_nome":` nas **duas** metades do literal do roster (`ExamPackageRouteTest.kt:310-311`) | `13:23:57Z` | `RosterEntryDto` só no servidor, `exit 1` | **`RosterEntryDto` nomeado só no servidor**; aparelho continua preso em `ObtencaoDeRosterTest.kt:53`; `exit 1`, uma linha de erro |

  Em (b) as duas metades precisaram mudar porque o literal do roster é partido **entre** os dois
  objetos da lista, e cada metade traz as duas chaves juntas — qualquer uma delas bastaria para
  prender o tipo. Reversão: (a) o arquivo plantado removido; (b) restaurado por cópia, `cmp` igual
  e `git diff` vazio. Nos dois casos, a guarda rodada depois → `exit 0`.
- [x] 3.3 **O piso.** `node tools/parity/fio.mjs --transport <diretório vazio>` → `exit 2`, com o
  motivo do piso; e o mesmo com um diretório que não existe. Verificar que o motivo impresso é o do
  piso, e não um erro de leitura qualquer.

  **Os dois, e mais os pisos que o `design.md` (decisão 6) lista e o plano não nomeava**, às
  `13:24Z`. Todos com `exit 2` e a linha começando por `piso:`:

  | Condição | Mensagem |
  |---|---|
  | `--transport` num diretório vazio | "…nao tem nenhum .kt — uma raiz vazia passaria em qualquer conferencia" |
  | `--transport` num diretório que não existe | "…nao e um diretorio — …" |
  | `--transport` num diretório com `.kt`, mas **sem** `@Serializable` (`object SemContrato`) | "nenhum tipo @Serializable em … — nada a conferir nao e o mesmo que tudo conferido" |
  | o lado do aparelho apontado para um caminho que não existe (`MUTACAO` em `LADOS`) | "o lado do aparelho (apps/android/src/tset) nao e um diretorio" |
  | o lado do aparelho apontado para um diretório com `.kt` e **zero** strings (`MUTACAO` em `LADOS`) | "o lado do aparelho (…) nao tem nenhum literal lido — a leitura pode estar quebrada" |

  E `--transport` sem valor sai `2` com "--transport pede um diretorio" — **não** é piso, é
  argumento, e fica dito como tal. As duas mutações de `LADOS` foram revertidas por cópia (`cmp`
  igual, `git diff` vazio), a guarda rodada depois → `exit 0`, e `MUTACAO` em código fora de
  `build/` → **0**.

  **O piso "tipo com zero chaves" também, e a primeira redação desta nota dizia o contrário.** Ela
  afirmava que ele era inalcançável, porque `data class` sem parâmetro não compila. Não compila —
  mas a guarda lê **texto**, e não o que compila. Medido às `13:24:56Z`, numa cópia temporária de
  `transport/` com `@Serializable data class Vazio()`: `exit 2`, "piso: Vazio (…) nao tem chave
  nenhuma — qualquer literal o satisfaria". A afirmação errada durou três minutos, e só não entrou
  num commit porque a nota foi relida antes.
- [x] 3.4 **A reversão, rodada, e não lembrada** (P10). `grep -rn "MUTACAO"` em código fora de
  `build/` → `0`; `git status` sem resíduo de código; `node tools/parity/fio.mjs` → `exit 0` **depois**
  da reversão; e `./gradlew build --rerun-tasks` **depois** da reversão, 176 de 176 tasks executadas,
  com a contagem pelo `timestamp`: **153 suítes, 1446 testes, 0 falhas** — a linha de base de 0.2
  mais os dois cenários de 2.1 e 2.2, e nada além.

  **Conferida rodando, depois de todas as mutações de 3.1 a 3.3.** `MUTACAO` em código fora de
  `build/` → **0**; `git status` → só este `tasks.md`, nenhum resíduo de código;
  `node tools/parity/fio.mjs` às `13:25:12Z` → **`exit 0`**. `./gradlew build --rerun-tasks`,
  `13:25:12Z`–`13:27:21Z`, `exit 0`, **176 de 176 tasks executadas**. Pelo `timestamp` de dentro dos
  XML (`13:26:16Z`–`13:27:18Z`): **153 suítes, 1446 testes, 0 falhas, 0 erros, 0 pulados** —
  `apps/android` 308, `apps/api` **167**, `packages/domain` 329 + 321 + 321. A diferença para 0.2 é
  **+2**, em `apps/api`, e os dois são os cenários de 2.1 e 2.2. Fora da janela, só o relatório de
  `buildSrc`, como em 0.2.

## 4. Fechar

- [x] 4.1 **O resto do comando cheio** (P5), e o que ficou de fora. `./gradlew -p buildSrc test
  --rerun-tasks` — rodado à parte porque `build` não alcança os testes de `buildSrc` (medido na
  `generatejooq-sem-registro-automatico`) — e os dois passos novos do `ci.yml` rodados de novo em
  bash, como estão escritos. Registrar como **não rodado**, com o porquê (P8):
  `connectedDebugAndroidTest` (nenhum arquivo de `apps/android` muda; o `androidTest` não tem
  literal de contrato — Parte I, §5, por busca) e o CI (tudo local, em Windows; o CI roda em Linux).

  **Rodado.** `./gradlew -p buildSrc test --rerun-tasks`, `13:27:51Z`–`13:28:20Z`, `exit 0`, 6 de 6
  tasks executadas: **1 suíte, 1 teste, 0 falhas** (`timestamp` `13:28:14Z`) — o
  `AquisicaoDeConexaoTest` que ficava fora da janela em 0.2 e em 3.4. Os dois passos novos do
  `ci.yml`, extraídos de novo pelo PyYAML e rodados com `bash -e` às `13:28:20Z`: o primeiro
  **`exit 0`** ("os 5 contratos estao presos por literal nos dois lados") — era `1` no commit 1 —, e
  o segundo **`exit 0`** ("acusou o contrato sem literal nos dois lados, e o piso, como deve").

  **Não rodado, e por quê (P8):**
  - **`connectedDebugAndroidTest`.** Nenhum arquivo de `apps/android` muda, e as mutações de 3.1
    tocaram `packages/domain` e foram revertidas (`cmp` e `git diff`). Que o `androidTest` não tem
    literal de contrato é **busca** (Parte I §5), e não execução.
  - **O CI.** Tudo acima é local, em Windows; o job `build` e o `web` rodam em Linux. Em
    particular, os dois passos novos usam `mktemp -d`, `cp -r` e `<<<` do bash, e aqui rodaram no
    Git Bash — que é bash, mas não é o Ubuntu do runner. É a 4.2.
  - **A guarda não roda em `./gradlew build`** (decisão 1): nenhum dos builds desta sessão a
    alcançou; ela foi rodada à parte, cada vez, e isso está em cada tarefa.
- [ ] 4.2 **O CI da PR, observado no destino** (P26). Os dois passos novos no job `web`, verdes, com
  o log dizendo o motivo — que a guarda acusou o `FantasmaDto` nos dois lados e o piso —, e o job
  `build` verde. Só existe depois do push, que é decisão do mantenedor: até lá esta tarefa fica
  **desmarcada**, com isso escrito nela (P1).

  **Aberta em 2026-09-23, ao fim da sessão de implementação.** Nada foi empurrado: a branch
  `vewvniv/o-fio-preso-nos-dois-lados` existe só localmente, sobre `d7e10f4`, que também ainda não
  está no remoto. Os dois passos rodaram localmente, extraídos do `ci.yml` (1.5 e 4.1) — isso é
  execução local, e não observação do CI no destino (P26).
- [x] 4.3 **A Parte II de `docs/cobertura-o-fio-preso-nos-dois-lados.md`**, com o que o plano manda
  e o `rigorous.md` §8 exige: o primeiro vermelho da guarda sobre a árvore real, com os nomes e a
  hora; os canários de 1.4; as três mutações **antes** (Parte I) **e depois**, ao lado dos previstos;
  os dois vermelhos plantados e o do piso; a reversão rodada; o comando cheio com `timestamp`. Na
  seção do que **não** fica verificado, a frase que não pode faltar — **"a guarda prova que o
  literal existe, e não que ele prende o fio"**, inteira, com a consequência para a fatia 5
  (decisão 11) —, a lacuna de coincidência, a guarda fora do `./gradlew build`, e o que 4.1 não
  rodou. O parágrafo da Parte II que hoje diz "ainda não existe" fica, marcado como superado (P7).
  Nenhuma seção fecha com "passou".

  **Escrita**, §7 a §15. A frase obrigatória abre o §14, inteira, com a consequência para a fatia 5.
  O "ainda não existe" ficou nos dois lugares em que estava — o cabeçalho e o começo da Parte II —,
  cada um com "Superado" ao lado. O §15 registra as três afirmações desta sessão que precisaram de
  correção, e diz que não houve achado novo pela regra 0.4.
- [x] 4.4 **O fechamento, escrito onde o achado aponta para esta mudança** — e sem apagar o que
  está lá (P7). São três lugares, que hoje dizem que "a medição e a correção são a ETAPA 7.3":
  `docs/auditoria-2026-09-18-antes-da-fatia-5.md:87`, a atualização de 2026-09-23 do ADR-0015
  (`:187`) e `docs/cobertura-contrato-do-fio-com-dono-unico.md:243` e `:269`. Uma linha em cada, com
  a data e o ponteiro para a Parte II. A razão de a tarefa existir está na própria auditoria: o
  archive da ETAPA 6 disse na mensagem do commit que o achado saía da lista, e não o escreveu no
  arquivo.

  **Escrito nos quatro pontos, e nada apagado.** Auditoria: um parágrafo "Corrigido em 2026-09-23"
  logo depois da nota, que diz também que a guarda não prova que o literal prende o fio e que o CI
  ainda não foi observado. ADR-0015: uma frase "Feito no mesmo dia" no fim da atualização de
  2026-09-23 — a decisão não muda, e o ADR continua `aceito`. `cobertura-contrato-…`: uma linha no
  §8 e outra no §9. **A do §9 foi escrita "Fechada" e rebaixada para "Feita"** antes do commit: o
  archive e o CI da PR ainda não aconteceram, e "fechada" afirmaria os dois.
- [x] 4.5 **O `design.md` e o `proposal.md` continuam verdadeiros?** Conferir item a item — em
  particular as afirmações que eram leitura: os cinco tipos e a forma deles (Context), a previsão
  da decisão 8, e as contagens derivadas de 3.1 e 3.4. O que tiver sido desmentido fica ao lado do
  que foi previsto, e não reescrito (P7).

  **Conferidos, item a item.** Uma afirmação desmentida, e ela ganhou a correção ao lado:

  | Afirmação | Estado |
  |---|---|
  | Context: cinco tipos `@Serializable`, todos `data class` simples | **confirmada por medição**: a guarda leu 5 tipos em 4 arquivos, sem nenhum `exit 2` (1.3) |
  | Decisão 1 e Impact: "só `node:fs` e `node:path`" | **desmentida**: também `node:url`. Nota ao lado nas duas, sem reescrever |
  | Decisão 6: o piso | **confirmada**, e com os seis casos exercitados (3.3) |
  | Decisão 7: os dois passos do CI | como escrita, e vista reprovar ramo por ramo (1.5) |
  | Decisão 8: a previsão do primeiro vermelho | **confirmada** (1.3) |
  | Decisão 9: os literais no molde do roster, deserializantes intactos | **confirmada**: +29/−0 em cada arquivo (2.4) |
  | 3.1: contagens derivadas | **confirmadas**, uma a uma |
  | 3.4: 153 suítes, 1446 testes | **confirmada** |
  | Proposal, "O que NÃO será alterado" | **verdadeira**: `git diff d7e10f4 --name-only` traz 12 arquivos, e **0** deles em `transport/`, `apps/android`, `AuthenticationTest`, `answer-kind.mjs` ou `limiar.mjs` |

  O `design.md` também tinha uma citação errada, e ela já estava corrigida antes do apply
  (decisão 6: o teste "o piso reprova um catálogo vazio" está em `RetentionDeclarationTest`).
