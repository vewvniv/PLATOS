## 0. O ADR e a linha de base — antes de qualquer linha de código

A ordem é a da ETAPA 6: **o ADR vem antes de qualquer código.** É a única decisão de arquitetura
deste plano, e código escrito antes dela seria decisão tomada por implementação.

- [x] 0.1 Escrever `docs/adr/0015-contrato-de-transporte-entre-servidor-e-aparelho.md` com as
  **quatro** decisões da seção 6.0 do plano, que são as decisões 1 a 4 do `design.md`: (1) onde o
  contrato mora — `packages/domain`, e `packages/contracts` continua sem existir; (2) o que se move
  e o que não — o fio, e nada além dele; (3) o `check` da migration continua sendo o terceiro
  registro, com conferência cruzada no molde de `limiar.mjs`; (4) os testes de literal ficam, como
  oráculo independente. Verificar por leitura cruzada: cada decisão com o arquivo e a linha que a
  sustentam, e `Status: aceito` com data.

  **Feito em 2026-09-19.** As quatro citações foram conferidas uma a uma contra a árvore:
  `ARQUITETURA-FINAL-v3.md:414` (a promessa do §13), `ResultQueries.kt:168` (`tipoGravado`),
  `supabase/migrations/20260917134500_result_tables.sql:85` (o `check`), e
  `openspec/changes/archive/2026-09-04-slice-4a-zero-device-auth/tasks.md:27` (a admissão de que
  "dividir o DTO era possível"). `Status: aceito · Data: 2026-09-19`.
- [x] 0.2 **A linha de base do fio, capturada antes do commit 1** (P3). Rodar
  `./gradlew :apps:android:testDebugUnitTest --tests '*ResultadoDtoTest*'` com o corpo produzido por
  `corpoDoEnvio()` **gravado em arquivo** nos quatro cenários do teste, e anotar caminho e
  `timestamp`. Verificar que o arquivo existe e que o corpo do primeiro cenário bate, byte a byte,
  com o literal que `ResultadoDtoTest` já fixa — se não bater, a linha de base está errada e nada
  depois dela vale.

  **Capturada em `2026-09-19T10:14:24Z`.** O instrumento é `LinhaDeBaseDoFioTest`, temporário e
  fora dos commits, que só **escreve** — não afirma nada, porque a afirmação é a comparação de 3.4.
  Três corpos distintos (os quatro cenários de `ResultadoDtoTest` produzem três corpos: o 1º e o 4º
  usam a mesma entrada). Arquivo em
  `…/scratchpad/linha-de-base-do-fio.txt`, `sha256 24e1bb4549564e58b37120fb4e4118831fec5e7c4f147d4095c1e8efe3aa5fce`.

  **A âncora foi conferida, e não suposta:** o corpo do cenário 1 foi comparado com `diff` contra o
  literal que `ResultadoDtoTest` já fixa, com `${"a".repeat(64)}` expandido — **saída vazia,
  `exit 0`**. Sem essa conferência, a linha de base seria "o que o código produz", e não "o que o
  contrato diz", e comparar contra ela não provaria nada.
- [x] 0.3 **Perguntar antes de tocar no ambiente** (P22, regra 0.9 do plano). A ETAPA 6 declara
  **emulador** no cabeçalho. Confirmar com o desenvolvedor qual emulador/AVD usar antes de subir
  qualquer coisa, inclusive em modo automático. Verificar: a confirmação está registrada na sessão.

  **Perguntado e respondido em 2026-09-19.** O AVD `platos-atd34` existe em `~/.android/avd`, mas o
  binário `emulator` **não** está no `ANDROID_HOME` do scoop (`android-clt`, só command-line tools).
  Decisão do desenvolvedor: **procurar o binário fora do `ANDROID_HOME` e subir o `platos-atd34`**;
  se não for encontrado, **parar e dizer** — não instalar nada. A opção "instalar o pacote
  `emulator` do SDK" foi oferecida e **não** foi escolhida.

## 1. Commit 1 — o contrato no KMP, sem consumidor

Código novo; nada o lê ainda. `CLAUDE.md` regra 1.

- [x] 1.1 Criar os DTOs de transporte em `packages/domain/src/commonMain/kotlin/com/platos/domain/`
  — os quatro contratos da tabela do `design.md` — com os nomes de tipo do servidor (decisão 5) e os
  `@SerialName` **idênticos** aos de hoje, campo por campo, na mesma ordem de declaração. Os defaults
  do servidor vêm junto (decisão 6). Verificar com `./gradlew :packages:domain:compileKotlinJvm` —
  compila.

  **Feito.** Pacote novo `com.platos.domain.transport`, quatro arquivos:
  `OrganizationDto.kt` (`OrganizationDto`), `ExamDto.kt` (`ExamSummaryDto`, `RosterEntryDto`),
  `ResultDto.kt` (`ResultSubmissionDto`, `AnswerObservationDto`) e `AnswerKind.kt`. Os
  `@SerialName` e a ordem de declaração foram copiados campo a campo dos dois lados — nada foi
  redigitado de memória. `RosterEntryDto` é o único par em que não foi preciso escolher nome: os
  dois lados já o chamavam assim. `ResultAcceptedDto` **não** subiu: é resposta, não tem espelho no
  aparelho, e criá-lo compartilhado seria abstração sem consumidor (non-goal do `design.md`).
  `./gradlew :packages:domain:compileKotlinJvm` → `BUILD SUCCESSFUL`.
- [x] 1.2 Criar, no mesmo lugar, a tradução `QuestionAnswer → String` (`answer_kind`) e a de
  alternativas, com os quatro valores que o `check` da migration admite. Verificar com um teste em
  `commonTest` que afirma os quatro pares literalmente — é o valor que o conferidor da tarefa 5.1 vai
  ler do arquivo de origem.

  **Feito**, e com duas decisões que o plano não pré-escreve e ficam ditas:

  1. **`answerOptions()` subiu junto com `answerKind()`.** O plano nomeia só a tradução
     `QuestionAnswer → string`. As duas são a mesma tradução — `QuestionAnswer` para os **dois**
     campos do fio (`answer_kind` e `answer_options`) —, as duas estavam duplicadas pela mesma
     razão, e as duas carregam a mesma regra de negócio ("todas as envolvidas, nunca a vencedora").
     Deixar metade compartilhada e metade espelhada seria o pior dos dois. A conversão para o
     `Array<String?>` do jOOQ **não** subiu: essa é a que fala com o banco (ADR-0015 decisão 2).
  2. **Os quatro valores são `const val` em `AnswerKind`, e não só o `when`.** Sem isso, o parse do
     servidor (`paraOutcome`, que ramifica sobre os literais ao traduzir o corpo recebido de volta)
     continuaria sendo um **terceiro** registro Kotlin, e a unificação teria fechado metade do
     defeito. `const val` é o que permite a esse `when` ramificar sobre a constante.

  `AnswerKindTest` afirma os quatro pares **com literal escrito à mão**, e não contra as próprias
  constantes: comparar `Marcada.answerKind()` com `AnswerKind.MARCADA` poria o mesmo código dos dois
  lados da igualdade e aprovaria qualquer valor (P4). Três cenários, `PASSED` nos três alvos.
- [x] 1.3 Rodar `./gradlew build` e verificar que fica **verde**. Este commit não tem consumidor: se
  alguma coisa cair aqui, o contrato novo não é idêntico ao antigo e a mudança não pode seguir.
  Registrar contagem de testes e `timestamp` do relatório (P2, P3).

  **`BUILD SUCCESSFUL in 1m 42s`, 176 de 176 actionable tasks**, janela
  `2026-09-19T10:19:14Z`–`10:20:57Z`. **129 suítes, 1280 testes, 0 falhas, 0 erros, 0 ignorados.**

  **O primeiro número escrito aqui foi 1445 testes em 154 suítes, e ele estava errado (P7, P3).**
  A âncora era `find -newermt`, que interpreta a data em **hora local**; a máquina é UTC+2 e a
  janela do build estava em UTC, então o filtro varreu desde `08:26Z` e recolheu relatórios de
  fora da execução — inclusive um de `2026-09-18T11:32` em `buildSrc`. A mensagem do commit 1
  carrega o número errado; ela fica como está e a correção vive aqui e no `cobertura-*`, porque
  reescrever a história esconderia o defeito em vez de registrá-lo.

  **A âncora certa é o atributo `timestamp` de dentro de cada XML**, que é UTC e descreve a
  execução que o escreveu — não a hora em que o arquivo foi tocado. Por ela: `10:19`→35 relatórios,
  `10:20`→94, total **129 / 1280**. Os **1445 em 154 suítes** são a árvore inteira de hoje, que é
  outra coisa e não é evidência desta execução (P2).

  A contagem inclui **1 cenário que não é da mudança**: `LinhaDeBaseDoFioTest`, o instrumento
  temporário da tarefa 0.2, que está na árvore mas **fora dos commits**. Ele sai na tarefa 8, e a
  saída se confere rodando (P10).

## 2. Commit 2 — o consumidor do servidor

- [x] 2.1 `apps/api` passa a usar os tipos do domínio: `http/dto/OrganizationDto.kt`,
  `http/dto/ExamDto.kt` e `http/dto/ResultDto.kt` deixam de **declarar** os DTOs e passam a importá-los.
  `paraNota()` e `paraOutcome()` ficam onde estão — são do servidor. Verificar com
  `./gradlew :apps:api:compileKotlin`.

  **Feito**, e com uma medição que o `design.md` não previa e que decidiu a forma do commit.

  **A pergunta:** o plano manda remover os espelhos **só** no commit 4. Mas `paraNota` mora em
  `http/dto/ResultDto.kt`, no pacote `com.platos.api.http.dto`, que é **o mesmo pacote** onde os
  espelhos estão declarados. Um arquivo que importa `com.platos.domain.transport.ResultSubmissionDto`
  e convive com um `com.platos.api.http.dto.ResultSubmissionDto` no seu próprio pacote — isso
  resolve para qual dos dois?

  **Medido com sonda e canário, e não suposto (P6, P13).** Sonda: dois tipos homônimos de forma
  **diferente** (`val x: Int` no "domínio", `val campoDoEspelho: String` no "espelho"), e um uso que
  só compila se resolver para o primeiro. Com o import explícito: `exit 0`. Canário — o mesmo
  arquivo **sem** o import: `error: unresolved reference 'x'`, `exit 1`, provando que aí ele
  resolveu para o espelho do mesmo pacote. **Import explícito vence declaração do mesmo pacote.**

  Com isso o commit 4 fica como o plano o escreveu: os espelhos dos **dois** lados saem juntos, no
  fim, e os commits 2 e 3 continuam revertíveis por si. Sem a medição, a saída teria sido mover
  `paraNota` de pacote — refatoração que o plano não pede (P19). A sonda foi removida.

  Sete arquivos trocam **uma linha de import cada** (`ExamQueries`, `ResultQueries`, `Routes`,
  `OrganizationQueries`, e os testes `RosterQueryTest`, `ExamPackageRouteTest`,
  `MeOrganizationsTest`). `ResultAcceptedDto` **fica** em `apps/api`: é resposta, não tem espelho.

  **Uma tentativa de ordenar imports foi revertida** (P25): o script ordenou o bloco inteiro e pôs
  `java.*` antes de `org.*`, contra a convenção que os arquivos já seguiam (`com.platos`, `org`,
  `java`/`kotlin`). Reordenação de import não é parte desta mudança. Refeito com reinserção no
  lugar ordenado **dentro do bloco `com.platos`**, e o diff final é de uma linha por arquivo.

  `paraOutcome` passa a ramificar sobre as constantes de `AnswerKind` em vez dos literais —
  sem isso o **parse** do servidor continuaria sendo um terceiro registro Kotlin dos mesmos quatro
  valores. `./gradlew :apps:api:compileKotlin` → `BUILD SUCCESSFUL`.
- [x] 2.2 `ResultQueries.tipoGravado()` passa a chamar a tradução do domínio (1.2). A tradução para
  colunas de jOOQ — `alternativas(): Array<String?>` e o `insertInto(...).set(...)` — **não** se move
  (decisão 2). Verificar que a única mudança em `ResultQueries.kt` é essa chamada.

  **Feito.** `tipoGravado()` foi **removida** — ela era uma das três cópias do mapa — e a chamada
  passou a `outcome.answer.answerKind()`. `alternativas()` **fica**, e encolheu para o que ela de
  fato é do lado do banco: `answerOptions().toTypedArray<String?>()`. O `when` de quatro ramos que
  ela tinha era a regra de negócio duplicada ("todas as envolvidas, nunca a vencedora"); o que
  sobra é a conversão `List<String>` → `Array<String?>`, que é tradução para a coluna e por isso
  **não** subiu (ADR-0015 decisão 2). O `insertInto(...).set(...)` não foi tocado.
- [x] 2.3 **Os literais de `ResultRouteTest.corpo()` não mudam, nem uma vírgula.** Verificar com
  `git diff` que `apps/api/src/test/.../ResultRouteTest.kt` não aparece no commit — se aparecer, a
  decisão 4 foi quebrada e é preciso parar e dizer por quê.

  **Conferido: `ResultRouteTest.kt` não aparece em `git diff --name-only`.** Os oito arquivos
  modificados são os sete consumidores mais `http/dto/ResultDto.kt`. Os seis literais de
  `answer_kind` continuam no arquivo, intactos.
- [x] 2.4 Rodar `./gradlew build` e verificar verde, com `ResultRouteTest` entre os que rodaram
  (ele precisa de Postgres/Testcontainers — registrar se o ambiente o alcançou ou não, e não supor
  que alcançou). Registrar contagem e `timestamp`.

  **`BUILD SUCCESSFUL in 30s`, 176 tasks (12 executadas, 164 up-to-date)**, janela
  `2026-09-19T10:26:33Z`–`10:27:04Z`. **25 suítes re-executadas, 165 testes, 0 falhas** — e as 25
  são todas de `apps:api`, que é o único módulo que este commit toca. As demais ficaram
  `UP-TO-DATE` com os relatórios de `10:19–10:20`; dizer "1445 testes passaram neste build" seria
  atribuir a esta execução um sinal que ela não produziu (P2).

  **O ambiente alcançou o Postgres, e isso foi conferido e não suposto:** `docker info` respondeu,
  e `TEST-com.platos.api.http.ResultRouteTest.xml` tem `timestamp="2026-09-19T10:27:00.134Z"` com
  **11 testes, 0 falhas**. O relatório existir na janela é o que distingue "passou" de "não rodou".

## 3. Commit 3 — o consumidor do aparelho

- [x] 3.1 `apps/android` passa a usar os tipos do domínio: `api/OrganizacaoDto.kt`,
  `api/ProvaDto.kt`, `api/RosterDto.kt` e `api/ResultadoDto.kt` deixam de **declarar** os DTOs. As
  traduções para os tipos de tela — `paraOrganizacao`, `paraProva`, `paraAluno` — ficam onde estão
  (decisão 2). Verificar com `./gradlew :apps:android:compileDebugKotlin`.

  **Feito.** `ApiPlatos` passa a importar `OrganizationDto`, `ExamSummaryDto` e `RosterEntryDto` do
  domínio, e as três traduções passam a estender esses tipos — `paraOrganizacao`,
  `paraProva`, `paraAluno` continuam **nos mesmos arquivos**, porque são do aparelho.

  `RosterDto.kt` é o caso que a medição de 2.1 tornou possível: os dois lados já chamavam o tipo de
  `RosterEntryDto`, então ali o import do domínio convive com uma declaração **homônima do próprio
  pacote**. O import vence, e por isso o espelho pôde ficar de pé até o commit 4.
  `./gradlew :apps:android:compileDebugKotlin` → `BUILD SUCCESSFUL`.
- [x] 3.2 `corpoDoEnvio()` passa a montar o tipo do domínio, e `tipoNoEnvio()`/`alternativasNoEnvio()`
  passam a chamar a tradução de 1.2. O `Json` privado ganha `encodeDefaults = true` (decisão 6).
  Verificar rodando `ResultadoDtoTest`: os quatro cenários passam **sem** que o literal mude.

  **Os quatro passam, e `ResultadoDtoTest.kt` não aparece em `git diff --name-only`.**
  `tipoNoEnvio()` e `alternativasNoEnvio()` saíram — eram a segunda cópia Kotlin do mapa — e deram
  lugar a `answerKind()`/`answerOptions()` do domínio.
- [x] 3.3 **Se algum cenário de `ResultadoDtoTest` cair aqui, a decisão 6 previu errado.** Vale a
  regra de parada (decisão 8): escrever o que caiu, por quê, e o que isso diz sobre `encodeDefaults`
  — **não** ajustar o literal para caber no que o código passou a produzir. O literal é o contrato;
  o código é que tem de caber nele.

  **Nenhum cenário caiu: a regra de parada não disparou, e a previsão da decisão 6 estava certa.**

  Mas "passou" sozinho não distingue "`encodeDefaults` consertou o problema" de "`encodeDefaults`
  era irrelevante". A decisão 6 era **previsão** (P6), e prova por ausência de falha não é prova.
  Então a previsão foi **vista falhar** (P9, P13): desligar `encodeDefaults` — marcado
  `MUTACAO-decisao6` — derrubou **2 dos 4** cenários, com o mecanismo visível na mensagem:

  | Cenário | Sob a mutação | O que sumiu do corpo |
  |---|---|---|
  | `o corpo tem os nomes de campo que a rota espera` | **FAILED** | `"answer_options":[]` do item `em_branco` |
  | `folha avulsa manda student_token nulo…` | **FAILED** | `"student_token":null`, o campo inteiro |
  | `pendencia viaja com o tipo dela…` | PASSED | nada — nenhum campo dele iguala o default |
  | `o corpo nao leva nome, turma, matricula…` | PASSED | idem |

  Os dois que caem são exatamente os dois que exercitam um campo cujo valor **iguala o default** do
  tipo do servidor. É a regressão que a decisão 6 previu, medida em vez de argumentada.

  Mutação revertida; `grep -rn "MUTACAO"` fora de `build/` → **0**, e os quatro cenários rodados de
  novo **depois** da reversão, verdes (P10).
- [x] 3.4 **A comparação byte a byte com a linha de base de 0.2.** Gerar de novo os corpos dos quatro
  cenários, com os dois artefatos da **mesma sessão** (P3), e comparar com `diff`. Verificar que a
  diferença é **vazia**. Se não for, parar e ler a diferença antes de seguir — "os testes passam" não
  é "é o mesmo byte".

  **Diferença vazia, `exit 0`.** Os três corpos regerados em `2026-09-19T10:31:27Z` têm o
  **mesmo `sha256`** da linha de base de `10:14:24Z`:
  `24e1bb4549564e58b37120fb4e4118831fec5e7c4f147d4095c1e8efe3aa5fce`. Os dois artefatos são desta
  sessão (P3). O fio é byte a byte o mesmo antes e depois da unificação.
- [x] 3.5 **Provar que a comparação de 3.4 é reativa** (guarda de vacuidade, P13): acrescentar um
  espaço a uma cópia do corpo de base e confirmar que o mesmo `diff` sai com `exit 1`. Sem isso,
  diferença vazia é indistinguível de um comparador que não compara.

  **Feito:** um espaço acrescentado depois de `"points":1` numa cópia, e o **mesmo** `diff` saiu com
  `exit 1`. O comparador acusa.
- [x] 3.6 Rodar `./gradlew build` e verificar verde. Registrar contagem e `timestamp`.

  **`BUILD SUCCESSFUL in 33s`, 176 tasks (24 executadas, 152 up-to-date)**, janela
  `2026-09-19T10:31:41Z`–`10:32:16Z`. **30 suítes re-executadas, 309 testes, 0 falhas** — todas de
  `apps/android`, o único módulo que este commit toca.

## 4. Commit 4 — os espelhos antigos saem

E **só aqui**, quando não há mais quem os leia.

- [x] 4.1 Remover as declarações espelhadas que sobraram nos oito arquivos das duas pontas, deixando
  no lugar apenas o que é do lado — as traduções e os imports. Verificar com
  `grep -rn "EnvioDeResultadoDto\|ObservacaoDto\|OrganizacaoDto\|ProvaDto" apps/` fora de `build/`:
  nenhuma **declaração** resta, só usos do tipo compartilhado onde o nome foi mantido.

  **Feito: 261 deleções contra 12 inserções em 8 arquivos.** Três arquivos saíram inteiros
  (`api/http/dto/OrganizationDto.kt`, `api/http/dto/ExamDto.kt`, e as duas `data class` de
  `api/http/dto/ResultDto.kt`, que ficou só com `ResultAcceptedDto` e as traduções do servidor); os
  quatro do aparelho encolheram para a tradução de tela que é deles.
  `grep -rn "^data class (…)" apps/` → **nenhuma**; as cinco declarações vivem só em
  `packages/domain/src/commonMain/kotlin/com/platos/domain/transport/`.

  **Duas KDoc foram corrigidas porque esta mudança as tornou falsas**, e não por limpeza:
  `ProvaPublicada.kt` dizia "Espelha `ProvaDto`" — a palavra "espelha" é exatamente o que o
  ADR-0015 desfez —, e `ProvaDto.kt` apontava `[OrganizacaoDto]`, que deixou de existir. Deixar
  afirmação falsa que o próprio commit criou é pior do que a linha que a corrige.

  **Os quatro arquivos do aparelho continuam com nome de DTO e já não declaram DTO nenhum.** Não
  são renomeados: `git mv` misturado com a remoção dos espelhos tornaria o diff deste commit
  ilegível justo onde ele mais precisa ser conferido (P25). Fica dito na KDoc de `ProvaDto.kt`, no
  molde do que `ResultQueries.findPublishedExamId` já faz com o próprio nome.
- [x] 4.2 **Os testes de literal continuam na árvore, inteiros** (decisão 4). Verificar com
  `git diff --stat` que nenhum arquivo de teste foi removido neste commit, e que `ResultadoDtoTest` e
  `ResultRouteTest` continuam com os mesmos literais. É o ponto desta mudança que, se for quebrado,
  não tem como ser percebido depois — a conferência é explícita porque a tentação é maior aqui.

  **Conferido por duas vias.** `git diff --cached --name-status | grep "^D.*[Tt]est"` → **vazio**:
  nenhum arquivo de teste foi removido. E os dois literais foram comparados contra `bd2934a`, o
  commit em que a sessão começou: `ResultadoDtoTest.kt` e `ResultRouteTest.kt` estão **idênticos**,
  zero linhas de diff, depois dos quatro commits.
- [x] 4.3 Rodar `./gradlew build` e verificar verde. Registrar contagem e `timestamp`.

  **`BUILD SUCCESSFUL in 55s`, 176 tasks (34 executadas, 142 up-to-date)**, janela
  `2026-09-19T10:34:52Z`–`10:35:47Z`. **55 suítes re-executadas, 474 testes, 0 falhas** — os dois
  módulos, `apps/api` e `apps/android`, porque este commit toca os dois.

## 5. A conferência cruzada com o `check` da migration (ADR-0015 decisão 3)

O `check` é guarda do banco e **não** passa a vir de Kotlin. O que entra é a conferência entre ele e
o domínio. A seção 6.0.3 do plano a exige; ela não está entre os quatro commits numerados, e por isso
ganha o seu próprio.

- [x] 5.1 `tools/parity/answer-kind.mjs`, no molde de `tools/parity/limiar.mjs`: lê os quatro valores
  **do arquivo de origem** do domínio (1.2) e do `check` de
  `supabase/migrations/20260917134500_result_tables.sql:85`, e reprova se divergirem, nomeando
  **quais** valores discordam. Verificar rodando `node tools/parity/answer-kind.mjs` — sai `0`.

  **Feito**, e ele lê **dois** registros dos arquivos de origem: as `const val` de `AnswerKind.kt`
  e o `check` da migration. `exit 0`, imprimindo os dois lados.

  **Uma guarda a mais do que o plano pede, e ela ganhou o seu valor na hora:** o conferidor também
  compara as `const val` com a lista `TODOS` do mesmo arquivo. A razão é que ler `TODOS` sozinho
  seria ler uma lista que pode estar incompleta sem nada acusar. Na mutação de 5.2 ela foi a
  primeira a disparar.
- [x] 5.2 Dois passos no `ci.yml`, também no molde do limiar: um que confere, outro que **força um
  valor divergente** (`--esperado`) e falha se a conferência aceitar. Verificar rodando os dois
  comandos localmente: o primeiro passa, o segundo acusa.

  **Os dois passos estão no `ci.yml`, logo depois dos do limiar**, e foram rodados localmente
  **exatamente como o CI os escreve**: o primeiro sai `0`, o segundo acusa `rasurada`. `ci.yml`
  conferido como YAML válido.

  **A capacidade de falhar foi provada em quatro condições, e não numa:**

  | Condição | Desfecho | O que ele disse |
  |---|---|---|
  | árvore como está | `exit 0` | os 4 valores concordam |
  | `--esperado …,rasurada` | `exit 1` | nomeia `rasurada` a mais **e** `indecisa` a menos |
  | `--esperado` com 3 valores | `exit 1` | nomeia `indecisa` como admitida só pelo `check` |
  | mesma lista, outra ordem | `exit 1` | nomeia a divergência de ordem |

  **E a quinta foi a que importou: mutar a fonte de verdade, e não o argumento.** `--esperado` só
  substitui o lado do domínio — um conferidor que lesse o arquivo errado passaria nas quatro linhas
  acima. Com `INDECISA` mutada **no `AnswerKind.kt` real**, ele saiu `1` com **duas** queixas: a
  contagem de `const val` contra `TODOS`, e o `indecisa` que só o `check` admite. Mutação revertida;
  `grep -rn "MUTACAO"` fora de `build/` → **0**, e o conferidor rodado de novo **depois** da
  reversão, `exit 0` (P10).
- [x] 5.3 Verificar que o conferidor confere **um** `check` nomeado contra **um** tipo nomeado, e não
  virou um conferidor genérico de enums (regra 8 do `CLAUDE.md`; a mesma proibição que o plano dá para
  `renderizador.mjs` na ETAPA 7).

  **Conferido por leitura** — e isto é leitura, não medição (P6). Dois arquivos de origem
  **nomeados** em constantes no topo, três expressões que procuram padrões **nomeados**
  (`const val … : String`, `val TODOS`, `check (answer_kind in …)`). Nenhuma varredura de
  diretório, nenhum glob, nenhuma noção de "enum" em geral. 104 linhas — o mesmo tamanho de
  `limiar.mjs`. Uma constante morta (`RAIZ`) que sobrou da primeira redação foi removida.

## 6. Ver falhar (P9) — a mutação, e ela é uma só

**A mutação é decisiva**, e é a única asserção que distingue "movi o arquivo" de "unifiquei o
contrato". O texto da ETAPA 6, copiado:

> **A mutação é uma só, e ela é decisiva:** trocar um `@SerialName` no contrato do KMP — por exemplo
> `capture_id` → `captureId`.
>
> **Conjunto previsto:** caem **os dois** testes de literal, o do servidor e o do aparelho. Se cair
> só um, o fio não está preso nos dois lados e a mudança **não** entregou o que prometeu — **pare**.
> Esse conjunto é a prova de que existe agora um dono único, e é a única asserção que distingue
> "movi o arquivo" de "unifiquei o contrato".

O plano declara o conjunto previsto **em prosa**, e não como tabela. A grade abaixo é o instrumento
de registro, no molde da tarefa 1.2 de `params-hash-no-pacote-publicado` — o previsto vem do texto
acima, sem acréscimo; a coluna "real" é preenchida ao rodar.

| Suíte | Cenários | Previsto? | **Real** |
|---|---|---|---|
| `ResultadoDtoTest` (android) — o do aparelho | ≥1 | **sim** | **caiu — 2 de 4** |
| `ResultRouteTest` (api) — o do servidor | ≥1 | **sim** | **caiu — 10 de 11** |
| qualquer outra | 0 | **não** | **0 — 152 suítes, nenhuma falha** |

**REAL = PREVISTO. A regra de parada não disparou.**

- [x] 6.1 Injetar a mutação: trocar `capture_id` por `captureId` no `@SerialName` do contrato do KMP,
  marcando a linha com `MUTACAO`. Rodar `./gradlew build --continue` e registrar o conjunto **real**
  de cenários que caiu, com a suíte de cada um e o `timestamp` do relatório (P2, P3).

  **`BUILD FAILED`**, janela `2026-09-19T10:38:26Z`–`10:39:34Z`. **154 suítes executadas** (o
  `--continue` levou o build inteiro até o fim), **1445 testes, 12 falhas**, e as 12 estão
  **todas** nas duas suítes previstas:

  **`ResultRouteTest` — 10 de 11 caíram.** Sobreviveu só
  `envio sem credencial e recusado, e nada e gravado`, e a sobrevivência dele é coerente: ele é
  recusado no 401, **antes** de o corpo ser desserializado, então o nome do campo nunca chega a
  importar.

  **`ResultadoDtoTest` — 2 de 4 caíram:** `o corpo tem os nomes de campo que a rota espera` e
  `o corpo nao leva nome, turma, matricula nem habilidade`, que são os dois que afirmam sobre o
  **conjunto de chaves**. Os outros dois passaram porque afirmam sobre `student_token` e
  `answer_kind`, que a mutação não tocou.
- [x] 6.2 **Comparar o real com o previsto, e aplicar a regra de parada** (decisão 8). Se o conjunto
  real for diferente — **mais, menos, ou outros** —, **parar**: não consertar o instrumento, não
  afrouxar a asserção, não ajustar a previsão em silêncio. Escrever o conjunto real ao lado do
  previsto e dizer o que ele significa (P7, P12, P14). Em particular, **se cair só um dos dois**, a
  mudança não entregou o que prometeu, e a correção certa não é acrescentar asserção ao lado que não
  caiu.

  **Os dois caíram, e é isso que esta mudança existe para tornar possível.** Antes dela, esta
  mutação **não tinha onde ser injetada**: não havia um `@SerialName` que os dois lados lessem.
  Editar o espelho do servidor derrubaria `ResultRouteTest` e deixaria `ResultadoDtoTest` verde;
  editar o do aparelho faria o inverso. Um vermelho só seria "movi o arquivo"; os dois vermelhos a
  partir de **uma** linha são "existe um dono único".

  As 152 suítes restantes passarem é a outra metade da afirmação: a mutação atingiu o contrato, e
  não o projeto inteiro.
- [x] 6.3 Reverter a mutação e **conferir a reversão rodando** (P10): `grep -rn "MUTACAO"` fora de
  `build/` → `0`, `git status` sem resíduo, e `./gradlew build` verde **depois** da reversão.

  **Revertida.** `MUTACAO` em código (`.kt`, `.mjs`, `.yml`, `.sql`, `.ts`) fora de `build/` → **0**.
  As ocorrências que restam são prosa nos `docs/cobertura-*` e nos `tasks.md` arquivados, que é
  onde a palavra deve mesmo aparecer. `git status` traz só `LinhaDeBaseDoFioTest`, o instrumento de
  0.2, que sai na tarefa 8. **`./gradlew build` → `BUILD SUCCESSFUL in 57s`, 176 tasks**, rodado
  **depois** da reversão e não antes (P10).

## 7. O aparelho, e o que só ele decide

- [ ] 7.1 Com o emulador confirmado em 0.3, rodar a suíte instrumentada do Android e verificar que
  nada regrediu. Registrar o comando cheio e o `timestamp` (P5, P2). Se o emulador não for
  autorizado ou não subir, **isso fica escrito como lacuna**, e não suposto como verde (P8, P23).

## 8. Fechar

- [ ] 8.1 Rodar o **comando cheio** — `./gradlew build` — e registrar contagem de testes, falhas e
  `timestamp` do relatório, filtrando por `timestamp` (P2, P3, P5). Verde de comando estreito não é
  verde do CI.
- [ ] 8.2 Escrever `docs/cobertura-contrato-do-fio-com-dono-unico.md` com o que o `rigorous.md` §8
  exige: o comando cheio (P5), o `timestamp` (P2, P3), o oráculo independente e por que ele é
  independente (P4 — os dois literais, que não compartilham código com o tipo que julgam), como a
  verificação foi vista falhar e **qual conjunto caiu** (P9), a comparação byte a byte com a âncora
  de 0.2 (P3), e o que ficou **sem** verificação (P8). Nenhuma seção fecha com "passou".
- [ ] 8.3 Verificar que o `design.md` e o `proposal.md` continuam verdadeiros depois da
  implementação — em particular a decisão 6, que carrega uma **previsão**. Se ela foi desmentida, o
  que vale é o registro do que aconteceu ao lado do que foi previsto, e não a previsão reescrita
  (P7).
