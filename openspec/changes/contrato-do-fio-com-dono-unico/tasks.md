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

- [ ] 1.1 Criar os DTOs de transporte em `packages/domain/src/commonMain/kotlin/com/platos/domain/`
  — os quatro contratos da tabela do `design.md` — com os nomes de tipo do servidor (decisão 5) e os
  `@SerialName` **idênticos** aos de hoje, campo por campo, na mesma ordem de declaração. Os defaults
  do servidor vêm junto (decisão 6). Verificar com `./gradlew :packages:domain:compileKotlinJvm` —
  compila.
- [ ] 1.2 Criar, no mesmo lugar, a tradução `QuestionAnswer → String` (`answer_kind`) e a de
  alternativas, com os quatro valores que o `check` da migration admite. Verificar com um teste em
  `commonTest` que afirma os quatro pares literalmente — é o valor que o conferidor da tarefa 5.1 vai
  ler do arquivo de origem.
- [ ] 1.3 Rodar `./gradlew build` e verificar que fica **verde**. Este commit não tem consumidor: se
  alguma coisa cair aqui, o contrato novo não é idêntico ao antigo e a mudança não pode seguir.
  Registrar contagem de testes e `timestamp` do relatório (P2, P3).

## 2. Commit 2 — o consumidor do servidor

- [ ] 2.1 `apps/api` passa a usar os tipos do domínio: `http/dto/OrganizationDto.kt`,
  `http/dto/ExamDto.kt` e `http/dto/ResultDto.kt` deixam de **declarar** os DTOs e passam a importá-los.
  `paraNota()` e `paraOutcome()` ficam onde estão — são do servidor. Verificar com
  `./gradlew :apps:api:compileKotlin`.
- [ ] 2.2 `ResultQueries.tipoGravado()` passa a chamar a tradução do domínio (1.2). A tradução para
  colunas de jOOQ — `alternativas(): Array<String?>` e o `insertInto(...).set(...)` — **não** se move
  (decisão 2). Verificar que a única mudança em `ResultQueries.kt` é essa chamada.
- [ ] 2.3 **Os literais de `ResultRouteTest.corpo()` não mudam, nem uma vírgula.** Verificar com
  `git diff` que `apps/api/src/test/.../ResultRouteTest.kt` não aparece no commit — se aparecer, a
  decisão 4 foi quebrada e é preciso parar e dizer por quê.
- [ ] 2.4 Rodar `./gradlew build` e verificar verde, com `ResultRouteTest` entre os que rodaram
  (ele precisa de Postgres/Testcontainers — registrar se o ambiente o alcançou ou não, e não supor
  que alcançou). Registrar contagem e `timestamp`.

## 3. Commit 3 — o consumidor do aparelho

- [ ] 3.1 `apps/android` passa a usar os tipos do domínio: `api/OrganizacaoDto.kt`,
  `api/ProvaDto.kt`, `api/RosterDto.kt` e `api/ResultadoDto.kt` deixam de **declarar** os DTOs. As
  traduções para os tipos de tela — `paraOrganizacao`, `paraProva`, `paraAluno` — ficam onde estão
  (decisão 2). Verificar com `./gradlew :apps:android:compileDebugKotlin`.
- [ ] 3.2 `corpoDoEnvio()` passa a montar o tipo do domínio, e `tipoNoEnvio()`/`alternativasNoEnvio()`
  passam a chamar a tradução de 1.2. O `Json` privado ganha `encodeDefaults = true` (decisão 6).
  Verificar rodando `ResultadoDtoTest`: os quatro cenários passam **sem** que o literal mude.
- [ ] 3.3 **Se algum cenário de `ResultadoDtoTest` cair aqui, a decisão 6 previu errado.** Vale a
  regra de parada (decisão 8): escrever o que caiu, por quê, e o que isso diz sobre `encodeDefaults`
  — **não** ajustar o literal para caber no que o código passou a produzir. O literal é o contrato;
  o código é que tem de caber nele.
- [ ] 3.4 **A comparação byte a byte com a linha de base de 0.2.** Gerar de novo os corpos dos quatro
  cenários, com os dois artefatos da **mesma sessão** (P3), e comparar com `diff`. Verificar que a
  diferença é **vazia**. Se não for, parar e ler a diferença antes de seguir — "os testes passam" não
  é "é o mesmo byte".
- [ ] 3.5 **Provar que a comparação de 3.4 é reativa** (guarda de vacuidade, P13): acrescentar um
  espaço a uma cópia do corpo de base e confirmar que o mesmo `diff` sai com `exit 1`. Sem isso,
  diferença vazia é indistinguível de um comparador que não compara.
- [ ] 3.6 Rodar `./gradlew build` e verificar verde. Registrar contagem e `timestamp`.

## 4. Commit 4 — os espelhos antigos saem

E **só aqui**, quando não há mais quem os leia.

- [ ] 4.1 Remover as declarações espelhadas que sobraram nos oito arquivos das duas pontas, deixando
  no lugar apenas o que é do lado — as traduções e os imports. Verificar com
  `grep -rn "EnvioDeResultadoDto\|ObservacaoDto\|OrganizacaoDto\|ProvaDto" apps/` fora de `build/`:
  nenhuma **declaração** resta, só usos do tipo compartilhado onde o nome foi mantido.
- [ ] 4.2 **Os testes de literal continuam na árvore, inteiros** (decisão 4). Verificar com
  `git diff --stat` que nenhum arquivo de teste foi removido neste commit, e que `ResultadoDtoTest` e
  `ResultRouteTest` continuam com os mesmos literais. É o ponto desta mudança que, se for quebrado,
  não tem como ser percebido depois — a conferência é explícita porque a tentação é maior aqui.
- [ ] 4.3 Rodar `./gradlew build` e verificar verde. Registrar contagem e `timestamp`.

## 5. A conferência cruzada com o `check` da migration (ADR-0015 decisão 3)

O `check` é guarda do banco e **não** passa a vir de Kotlin. O que entra é a conferência entre ele e
o domínio. A seção 6.0.3 do plano a exige; ela não está entre os quatro commits numerados, e por isso
ganha o seu próprio.

- [ ] 5.1 `tools/parity/answer-kind.mjs`, no molde de `tools/parity/limiar.mjs`: lê os quatro valores
  **do arquivo de origem** do domínio (1.2) e do `check` de
  `supabase/migrations/20260917134500_result_tables.sql:85`, e reprova se divergirem, nomeando
  **quais** valores discordam. Verificar rodando `node tools/parity/answer-kind.mjs` — sai `0`.
- [ ] 5.2 Dois passos no `ci.yml`, também no molde do limiar: um que confere, outro que **força um
  valor divergente** (`--esperado`) e falha se a conferência aceitar. Verificar rodando os dois
  comandos localmente: o primeiro passa, o segundo acusa.
- [ ] 5.3 Verificar que o conferidor confere **um** `check` nomeado contra **um** tipo nomeado, e não
  virou um conferidor genérico de enums (regra 8 do `CLAUDE.md`; a mesma proibição que o plano dá para
  `renderizador.mjs` na ETAPA 7).

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

| Suíte | Cenários | Previsto? | Real |
|---|---|---|---|
| `ResultadoDtoTest` (android) — o do aparelho | ≥1 | **sim** | _a preencher_ |
| `ResultRouteTest` (api) — o do servidor | ≥1 | **sim** | _a preencher_ |
| qualquer outra | 0 | **não** | _a preencher_ |

- [ ] 6.1 Injetar a mutação: trocar `capture_id` por `captureId` no `@SerialName` do contrato do KMP,
  marcando a linha com `MUTACAO`. Rodar `./gradlew build --continue` e registrar o conjunto **real**
  de cenários que caiu, com a suíte de cada um e o `timestamp` do relatório (P2, P3).
- [ ] 6.2 **Comparar o real com o previsto, e aplicar a regra de parada** (decisão 8). Se o conjunto
  real for diferente — **mais, menos, ou outros** —, **parar**: não consertar o instrumento, não
  afrouxar a asserção, não ajustar a previsão em silêncio. Escrever o conjunto real ao lado do
  previsto e dizer o que ele significa (P7, P12, P14). Em particular, **se cair só um dos dois**, a
  mudança não entregou o que prometeu, e a correção certa não é acrescentar asserção ao lado que não
  caiu.
- [ ] 6.3 Reverter a mutação e **conferir a reversão rodando** (P10): `grep -rn "MUTACAO"` fora de
  `build/` → `0`, `git status` sem resíduo, e `./gradlew build` verde **depois** da reversão.

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
