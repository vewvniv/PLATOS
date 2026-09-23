## 0. Antes de qualquer commit — o ambiente e a linha de base

- [x] 0.1 **O ambiente, conferido e registrado** (P22). O mantenedor autorizou **emulador e Docker**
  para esta etapa em 2026-09-23; aparelho físico **não**. Registrar: branch
  `vewvniv/o-apk-de-release-e-verificado`, criada sobre `3e7df3b`; `git status` limpo; `docker info`;
  o AVD usado e `adb devices` mostrando **só** o emulador — se um aparelho físico estiver conectado,
  parar e perguntar antes de qualquer `connected…`.

  **Conferido.** `HEAD~1` é `3e7df3b` (o commit acima é o da proposta, `85fa19f`); `git status` limpo.
  `docker info` → `exit 0`, servidor `29.7.2`, nenhum contêiner rodando — ligado pelo mantenedor.
  `adb devices -l` antes de subir qualquer coisa: **lista vazia** (nenhum aparelho físico). AVD
  `platos-atd34`, o único que `emulator -list-avds` mostra, subido por esta sessão sem janela
  (`-no-window -no-audio -no-boot-anim -no-snapshot-save`); `sys.boot_completed=1` em cerca de 30 s;
  às `19:26:02Z`, `adb devices -l` mostra **só** `emulator-5554`, `sdk_slim_x86_64`, API 34.
- [x] 0.2 **A linha de base desta sessão** (P3), com o `timestamp` de dentro de cada XML:
  - `./gradlew build --continue --rerun-tasks` — previsto **153 suítes, 1446 testes, 0 falhas**, a
    linha de base da 7.1;
  - `./gradlew :apps:android:connectedDebugAndroidTest`, **sem filtro** — previsto **86** testes, o
    número do log da `paridade` da PR #60, 0 falhas;
  - a mesma tarefa só com `SessaoEmRepousoInstrumentedTest` — previsto verde (registro da ETAPA 5).

  Se algum número diferir, parar e explicar antes da tarefa 1.

  **Build cheio: bateu.** `19:26:15Z`–`19:29:03Z`, `exit 0`, **176 de 176 tasks executadas**; pelo
  `timestamp` dos XML: **153 suítes, 1446 testes, 0 falhas, 0 erros, 0 pulados** — `apps/android` 308,
  `apps/api` 167, `packages/domain` 329 + 321 + 321. Fora da janela, 30 relatórios: o de `buildSrc`, e
  os 29 de `testReleaseUnitTest` que a medição da proposta (por *init script*, `18:58Z`) deixou em
  `build/` — estado velho no instrumento (P3), e o contador os excluiu pelo `timestamp`.

  **Instrumentada cheia: a previsão estava errada, e a árvore não (P7).** `19:29:23Z`–`19:30:17Z`,
  `exit 0`. O XML (`TEST-platos-atd34(AVD) - 14-_apps_android-.xml`, um envelope `<testsuites>`) dá
  **83 testes, 0 falhas, 2 pulados**. A previsão dizia **86**, lida da linha "Finished 86 tests" do log
  da `paridade` da PR #60 — e essa linha **não é contagem de testes**: as duas execuções dizem
  "Starting **83** tests", e "Finished" soma os pulados (CI: 83 + 3 = 86; aqui: 83 + 2 = 85). A
  diferença de um pulado é `SupabaseFailureProbe.relatarFalha`: no CI ele é pulado por falta de
  `probe.supabaseUrl`; aqui o `local.properties` tem a chave (conferida a presença, e não o valor), e
  ele **rodou e passou**, `2,71 s`. Os dois pulados daqui são os de `AcumuloDeInstanciasProbe`, os
  mesmos do CI. **O instrumento também errou uma vez:** o contador lia só o primeiro `<testsuite>` do
  arquivo e disse "5 testes"; o total foi lido do envelope.

  **A classe da credencial, isolada:** `19:31:16Z`–`19:31:26Z`, `exit 0`, **2 de 2**
  (`aCredencialNaoEstaEmClaro`, `nemCredencialNemOrganizacaoSaemNoBackup`), XML `19:31:24Z`.

## 1. A medição de entrada — ver o buraco, antes de qualquer código

Cada defeito plantado carrega `MUTACAO` (no nome ou no conteúdo), é revertido, e a reversão é
**rodada** antes do próximo (P10). O conjunto previsto:

| Buraco | Defeito plantado | Comando | Previsto | **Real** |
|---|---|---|---|---|
| item 1 | `apps/android/src/release/assets/MUTACAO-pacote.json` com `"answer_key"` e `"min_renderer_version"` | `./gradlew :apps:android:verificarApkSemPacote --rerun-tasks` | **verde** — a guarda só abre o debug; e `unzip -l` mostra a entrada **no APK de release**, e não no de debug | **verde**, "0 asset(s) JSON conferido(s)"; `assets/MUTACAO-pacote.json` **está** em `android-release-unsigned.apk`, e o debug não tem JSON nenhum em `assets/` — o buraco |
| item 2 | um `@Test` em `apps/android/src/test` que afirma `BuildConfig.DEBUG` | `./gradlew :apps:android:test --rerun-tasks` | **verde** — `testReleaseUnitTest` não existe; o XML do debug tem o teste, passando | **verde**; só `testDebugUnitTest` no log; **309** testes, o plantado entre eles, passando — o buraco |

- [x] 1.1 **O buraco do item 1.** Plantar, rodar, conferir o APK de release com `unzip -l` (a entrada
  está lá) e o de debug (não está). Previsto na tabela. Remover o arquivo plantado; `git status` limpo;
  a guarda de novo, verde.

  **Real = previsto.** `./gradlew :apps:android:assembleRelease :apps:android:verificarApkSemPacote
  --rerun` (o `--rerun` força a guarda a executar, em vez de servir `UP-TO-DATE` sobre um APK de debug
  que não mudou), `19:31:51Z`–`19:31:59Z`, `exit 0`: "APK sem pacote de prova: 0 asset(s) JSON
  conferido(s)". Aberto com `zipfile` do Python (não há `unzip` no Git Bash):
  `android-release-unsigned.apk` → `['assets/MUTACAO-pacote.json']`; `android-debug.apk` → nenhum JSON
  em `assets/`. Removido o arquivo e o diretório `src/release/`, criados por esta tarefa; `git status`
  limpo; o mesmo comando `19:32:17Z`, verde, e o release sem a entrada.
- [x] 1.2 **O buraco do item 2.** Plantar, rodar, conferir no log que só `testDebugUnitTest` rodou e
  que o XML dele traz o teste plantado passando. Remover; `git status` limpo; o mesmo comando, verde.

  **Real = previsto.** `MutacaoSoNoReleaseTest`, em `apps/android/src/test/.../android/`, com
  `assertTrue(BuildConfig.DEBUG, …)` e `MUTACAO` no comentário. `./gradlew :apps:android:test
  --rerun-tasks`, `19:32:39Z`–`19:33:11Z`, `exit 0`, 40 de 40 tasks: o log traz **só**
  `:apps:android:testDebugUnitTest`; **30 suítes, 309 testes, 0 falhas**, e o XML tem `so passa no
  debug()` passando. Removido; `git status` limpo; o mesmo comando `19:33:21Z`–`19:33:44Z`, 308 de
  308.
- [x] 1.3 **A reprodução do item 4**, pela ordem da decisão 4 do `design.md`, **parando na primeira que
  cair**: (a) `SegundoMembroInstrumentedTest` e em seguida `SessaoEmRepousoInstrumentedTest`, por
  `-Pandroid.testInstrumentationRunnerArguments.class=…`; (b) a suíte cheia, três vezes; (c) a suíte
  cheia sem as classes instrumentadas que entraram depois de `2026-09-19T00:06Z` (conferidas no `git
  log`). Registrar de cada execução: hora UTC, contagem, e — na que cair — a **mensagem e a pilha
  inteiras** do cenário, lidas do XML. **Se nenhuma cair: parar** (decisão 4) e levar ao mantenedor;
  as tarefas do grupo 7 ficam desmarcadas com o motivo.

  **Nenhuma caiu — e a regra de parada vale** (decisão 4). Feita a reprodução inteira, sem conserto
  nenhum; o item 4 para aqui e vai ao mantenedor. Marcada porque a tarefa é **medir**, e a medição
  está feita; o que ela não achou fica dito:

  | Execução | Hora UTC | Real |
  |---|---|---|
  | (a) `class=SegundoMembroInstrumentedTest,SessaoEmRepousoInstrumentedTest` | `19:34:06Z`–`19:34:17Z` | 5 de 5, nessa ordem no XML; nenhuma falha |
  | (b) suíte cheia, 1ª | `19:34:27Z`–`19:35:09Z` | 83, 2 pulados, 0 falhas |
  | (b) suíte cheia, 2ª | `19:35:09Z`–`19:35:51Z` | 83, 2 pulados, 0 falhas |
  | (b) suíte cheia, 3ª | `19:35:51Z`–`19:36:33Z` | 83, 2 pulados, 0 falhas |
  | (c) `notClass=InstanciaUnicaDoOutboxInstrumentedTest,AcumuloDeInstanciasProbe`, 1ª | `19:36:45Z`–`19:37:27Z` | **78**, 0 falhas |
  | (c) idem, 2ª | `19:37:27Z`–`19:38:10Z` | 78, 0 falhas |
  | (c) idem, 3ª | `19:38:10Z`–`19:38:53Z` | 78, 0 falhas |

  As classes de (c) saíram do `git log` pela data do **autor** — a do *committer* muda no rebase e
  apontava para o dia errado: `InstanciaUnicaDoOutboxInstrumentedTest` foi escrita às `00:14Z` de
  2026-09-19, **entre** as execuções que caíam (`00:03Z`, `00:06Z`) e as que passavam (`00:18Z`); depois
  dela, só `AcumuloDeInstanciasProbe` (`07:49Z`).

  **O que (c) diz, e contradiz.** Ela recompõe a suíte daquela hora — **78 testes**, a mesma contagem
  da linha de base vermelha registrada em `cobertura-o-pendente-nao-se-perde-no-aparelho.md` §1 — e
  passa três vezes. O registro da ETAPA 5 afirma que o cenário "parou de cair **por mudança de ordem**,
  e não por conserto". Tirar a classe acrescentada **não** traz a falha de volta: nesta árvore, a
  hipótese da ordem não se sustenta. O que mudou entre aquela hora e esta — a instância única do Room
  (`75f05ed`, que também mexeu em `SegundoMembroInstrumentedTest`), o próprio emulador, ou o tempo — não
  foi medido. **Nenhuma pilha foi obtida**, porque nada caiu. O grupo 7 fica desmarcado.
- [x] 1.3b **A árvore daquela hora** — acrescentada em 2026-09-23 por decisão do mantenedor, depois
  de 1.3 não reproduzir (`design.md`, decisão 4, atualização). *Worktree* separado em `42e94cd`, com o
  `local.properties` copiado; `./gradlew :apps:android:connectedDebugAndroidTest` sem filtro. Previsto
  pela hipótese: `aCredencialNaoEstaEmClaro` **cai**, e a mensagem do `FileNotFoundException` traz "Too
  many open files". Se cair, o mesmo em `75f05ed`: previsto **verde**. Registrar hora, contagem, e a
  mensagem e a pilha inteiras do XML. **Se não cair em `42e94cd`, a hipótese também cai**: registrar e
  levar ao mantenedor. Remover os *worktrees* ao fim; `git worktree list` só com a árvore principal.

  **Não caiu em `42e94cd`, duas vezes — e a hipótese cai junto.** *Worktree* em
  `scratchpad/wt-42e94cd`, `local.properties` copiado. `19:41:12Z`–`19:42:25Z`: **78 de 78**, 0 falhas,
  0 pulados (o log compilou a árvore antiga inteira antes). De novo `19:42:44Z`–`19:43:28Z`: 78 de 78.
  Como não caiu, `75f05ed` não foi rodado: não havia o que comparar.

  **O que explica, e não estava no design: o aparelho.** A cobertura da ETAPA 5 diz no cabeçalho
  (`cobertura-o-pendente-nao-se-perde-no-aparelho.md:6`): "**Aparelho:** 2511FPC34G, **Android 16**.
  Nenhum emulador foi subido." As execuções vermelhas de `00:03Z` e `00:06Z` rodaram nesse **aparelho
  físico**; todas as desta mudança, no emulador `platos-atd34`, **API 34**. Nesta árvore e na daquela
  hora, no emulador, o cenário não cai. A falha depende do aparelho ou da versão do Android — e isso o
  emulador não mede. O `design.md` (Context, item 4) foi escrito sem esse dado, e a hipótese da ordem,
  que a ETAPA 5 registrou, também: **nenhuma das duas é sustentada pela medição**. Levado ao
  mantenedor; o *worktree* fica até a decisão dele.

  **Superado às `20:05:39Z` (P7: o parágrafo acima fica).** O aparelho foi conectado; esta árvore e
  `42e94cd` passaram nele (`19:53Z`–`19:55Z`) — e a reprodução apareceu depois, na reversão da tabela do
  defeito plantado, **na ordem da hipótese e sem defeito**: `ENOENT` na linha 83, a guarda 1. A causa é a
  sondagem não atômica, e é rara: 1 queda em 19 execuções do cenário no aparelho, 0 em 17 no emulador.
  A cronologia inteira está em `docs/cobertura-o-apk-de-release-e-verificado.md` §4. O *worktree* foi
  removido (o `git worktree remove` falhou por caminho longo depois de desregistrá-lo; o diretório, do
  scratchpad, foi apagado à parte), e `git worktree list` não o traz mais.
- [x] 1.4 **Registro, antes do código.** `docs/cobertura-o-apk-de-release-e-verificado.md`, Parte I: a
  linha de base, os dois buracos com previsto e real, a reprodução (ou a falta dela) com a pilha, e uma
  Parte II que diz "ainda não existe". Preencher a coluna **Real** acima. Verificar: `grep -rn
  "MUTACAO"` fora de `docs/`, `openspec/`, `build/`, `node_modules/`, `.gradle/` e `.git/` vazio;
  `git status` só com o documento e este `tasks.md`. Commit `registro:`.

  **Feito**, com o §4 bem maior que o previsto — as quatro rodadas do item 4. O `git status` traz
  também o `design.md` (as três atualizações da decisão 4, que entram neste commit por serem o registro
  das decisões do mantenedor durante a medição) e o `.github/workflows/ci.yml` (o commit 1, já editado,
  que **não** entra aqui).

## 2. Commit 1 — a `concurrency` por job (item 3)

- [x] 2.1 **`ci.yml`**: tirar o bloco `concurrency` do nível do workflow e declarar um em cada job,
  conforme a decisão 3 — grupo com o nome do job; `true` em `build` e `web`, `false` em `paridade`. Um
  comentário no tom dos vizinhos, citando P15 e a PR #30. Verificar por `yaml.safe_load`: **não** há
  `concurrency` no topo; os três jobs têm `concurrency` com os três grupos **distintos** e os três
  valores; nenhum outro passo mudou (`git diff` do arquivo só mexe nessas linhas). **Tipo: conferido
  por leitura, e não medido** — dizer assim no commit e na cobertura (P6). Commit `ci:`.

  **Feito, `d2457b6`.** `yaml.safe_load`: `'concurrency' in ci` → `False`; `build` e `web` com
  `…-build` / `…-web` e `cancel-in-progress: True`, `paridade` com `…-paridade` e `False`; três grupos
  distintos; os três jobs são os mesmos de antes. `git diff --numstat` do arquivo: `17 4` — as 4 são o
  bloco do topo (três linhas e a linha em branco), e as 17, os três blocos novos e os dois
  comentários. **Conferido por leitura, e não medido**, dito assim no commit.

## 3. Commit 2 — a guarda do APK sobre o release (item 1)

- [x] 3.1 **`verificarApkSemPacote` recebe as duas variantes**, com vacuidade por variante (decisão 1).
  Verificar: `./gradlew :apps:android:verificarApkSemPacote --rerun-tasks` verde, e a linha de log
  passa a dizer quantos assets conferiu **em cada** APK — os dois nomeados.

  **Feito.** Duas entradas, `apksDeDebug` e `apksDeRelease`, e o laço as percorre com a vacuidade de
  cada uma antes de abrir qualquer APK. `--rerun`, `20:14:11Z`–`20:14:39Z`, `exit 0`: "debug:
  android-debug.apk, 0 asset(s) JSON conferido(s)", "release: android-release-unsigned.apk, 0 asset(s)
  JSON conferido(s)", "APK sem pacote de prova, nas duas variantes".
- [x] 3.2 **Ver falhar**, três defeitos, um de cada vez:

  | Defeito plantado | Previsto | **Real** |
  |---|---|---|
  | o mesmo JSON de 1.1 em `src/release/assets/` | **recusa**, nomeando o APK **de release** e a entrada, e **não** o de debug | `20:14:58Z`, `exit 1`: "ha pacote de prova … `android-release-unsigned.apk!assets/MUTACAO-pacote.json`"; o debug com 0 |
  | o mesmo JSON em `src/debug/assets/` | **recusa**, nomeando o APK **de debug**, e **não** o de release | `20:15:12Z`, `exit 1`: "… `android-debug.apk!assets/MUTACAO-pacote.json`"; o release com 0 |
  | o diretório do release trocado por um que não existe, no registro da tarefa | **recusa** por vacuidade: "nenhum APK de release" | `20:15:35Z`, `exit 1`: "nenhum APK de release para conferir; a tarefa depende de `assembleRelease`" |

  O primeiro é o buraco da 1.1 fechado: mesmo defeito, desfecho oposto. Reverter cada um e rodar.
  Commit `build(android):`.

  **Real = previsto nos três.** Cada um revertido e a guarda rodada de novo, verde, com os dois APKs
  nomeados (`20:15:07Z`, `20:15:16Z`, `20:15:42Z`); `grep -c MUTACAO` no `build.gradle.kts` → `0`.
  Commit 2, `build(android):`, só com o `build.gradle.kts`.

## 4. Commit 3 — a variante de teste do release (item 2)

- [x] 4.1 **`beforeVariants` liga a variante** (decisão 2). Verificar: `:apps:android:tasks --all`
  lista `testReleaseUnitTest`; `./gradlew :apps:android:test --rerun-tasks` roda **as duas** tarefas
  (no log), e os XML dão **308 + 308**; se a forma tipada diferir da usada na medição, dizer qual ficou
  e por quê.

  **Feito, e a forma diferiu.** `variante.enableUnitTest = true` não compila no Kotlin DSL: "Unresolved
  reference 'enableUnitTest'" — o *init script* da medição era Groovy e resolvia por despacho dinâmico.
  Ficou `variante.hostTests[HostTestBuilder.UNIT_TEST_TYPE]`, com `requireNotNull` no lugar de `?.`, pela
  razão escrita no próprio arquivo. O jar da API do AGP 9.3.1 não foi achado no cache do Gradle para
  conferir a assinatura por `javap`; quem confirmou foi o compilador. `:apps:android:tasks --all` lista
  `testReleaseUnitTest`, e o log não traz aviso de depreciação para o `build.gradle.kts`.
  `./gradlew :apps:android:test --rerun-tasks`, `20:19:27Z`–`20:20:14Z`, `exit 0`, 68 de 68 tasks: o log
  traz `testDebugUnitTest` **e** `testReleaseUnitTest`; XML **308 + 308**, 0 falhas.
- [x] 4.2 **Ver falhar** — o buraco da 1.2 fechado:

  | Defeito plantado | Previsto | **Real** |
  |---|---|---|
  | o mesmo teste de 1.2 (`BuildConfig.DEBUG`) | `testDebugUnitTest` verde com ele; `testReleaseUnitTest` **cai só nele**; `./gradlew build` vermelho por essa tarefa | `./gradlew build --continue`, `20:20:26Z`–`20:21:09Z`, `exit 1`: debug **309, 0 falhas**; release **309, 1 falha** — `MutacaoSoNoReleaseTest` › `so passa no debug()`, "BuildConfig.DEBUG e falso: esta e a variante release"; a única tarefa `FAILED` é `:apps:android:testReleaseUnitTest` |

  Reverter e rodar. Commit `build(android):`.

  **Real = previsto.** O buraco da 1.2 fechado: o mesmo defeito, e agora o `build` fica vermelho.
  Revertido; `./gradlew :apps:android:test --rerun-tasks`, `20:21:27Z`–`20:22:16Z`, 308 + 308. Commit 3.

## 5. Commit 4 — a guarda de testes executados, e ela nasce vermelha (item 5)

- [x] 5.1 **Conferir antes de escrever** (decisão 6): por `javap -v` sobre uma classe compilada de
  cada suíte (`apps/api`, `apps/android`, `packages/domain` `jvmTest`), qual anotação de tempo de
  execução o `@Test` vira. Previsto: `org.junit.jupiter.api.Test` nas três, inclusive onde o fonte usa
  `kotlin.test.Test`. Se não for, parar: a decisão 6 foi escrita sobre isso.

  **Real = previsto.** `javap -v -p`, contando a anotação na linha seguinte a cada
  `RuntimeVisibleAnnotations`: `LayoutEngineTest` do `jvmTest` → 21 × `org.junit.jupiter.api.Test` (o
  fonte usa `kotlin.test.Test`); a mesma classe do `testAndroidHostTest` → 21 × a mesma; `ResultRouteTest`
  da API → 11 × a mesma (fonte com `kotlin.test.Test`); `ApiPlatosPacoteTest` do aplicativo → **9** × a
  mesma — e `listagem sem rede vira SemRede()` com `descriptor: ()Lcom/platos/android/net/Retorno$SemRede;`,
  o método que devolve valor, anotado como os outros.
- [x] 5.2 **Escrever a tarefa em `buildSrc`** conforme a decisão 6 — declaração por reflexão sobre o
  bytecode, sem inicializar; resultado pelo XML, nome normalizado; piso; falha fechada — e
  registrá-la nos três módulos, uma instância por tarefa `Test`, ligada ao `check` e rodando depois da
  tarefa que julga. Verificar: `./gradlew -p buildSrc test --rerun-tasks` verde (nada quebrou lá) e a
  compilação dos três `build.gradle.kts`.

  **Feito, e o mecanismo de registro mudou em relação à decisão 6 — dito aqui, e não escondido.** A
  decisão fala em "uma classe de tarefa … registrada nos três módulos, uma instância por tarefa de
  teste". Ficou **uma função de extensão**, `Test.exigirQueTodoTesteDeclaradoRode()`, em
  `buildSrc/.../TodoTesteDeclaradoRoda.kt`, que acrescenta a conferência como **última ação** (`doLast`)
  de cada tarefa `Test`; e ela é chamada **uma vez, na raiz**, no bloco `subprojects { tasks.withType<Test>()
  .configureEach { … } }` que já força `useJUnitPlatform()` em todo `Test` dos três módulos. As razões: o
  relatório e as classes conferidos são exatamente os da execução que acabou de acontecer, sem tarefa
  separada para ordenar; as tarefas de teste que o AGP registra tarde não precisam ser achadas pelo
  nome; e o lugar que já configura todo `Test` evita a lista de tarefas que o design proíbe. O que a
  decisão 6 afirma — uma implementação, uma conferência por tarefa de teste, depois dela, dentro do
  `check` — continua valendo. `./gradlew -p buildSrc test --rerun-tasks` → `exit 0`, 6 de 6 tasks; os
  scripts compilam (a 5.3 roda `help` sobre eles).
- [x] 5.3 **As tarefas cobertas, lidas do grafo** (decisão 7): listar as tarefas `Test` dos três
  módulos e as instâncias da guarda. Previsto: `test` (API), `testDebugUnitTest` e
  `testReleaseUnitTest` (aplicativo), `jvmTest` e `testAndroidHostTest` (domínio) — cinco, cada uma com
  a sua guarda. Outra lista: parar e dizer.

  **Real = previsto, depois de uma linha a mais explicada.** Um *init script* no scratchpad imprime
  `tasks.withType(Test)` de cada projeto em `projectsEvaluated`: `:apps:android` → `testDebugUnitTest`,
  `testReleaseUnitTest` (`AndroidUnitTest`); `:apps:api` → `test` (`Test`); `:packages:domain` →
  `jvmTest` (`KotlinJvmTest`), `testAndroidHostTest` (`AndroidUnitTest`). **E uma sexta linha, `:` →
  `test`**, que parou a tarefa até ser explicada: o Gradle roda *init scripts* também na build do
  `buildSrc`, cuja raiz também se chama `:`; um segundo *init script* mostrou que essa tarefa tem as
  fontes em `buildSrc/build/classes/…/test` e plugins de `kotlin-dsl`, e que a raiz da build principal
  **não** tem `test` (`null`). É a build separada que a decisão 7 deixa de fora. A guarda entra pelo
  `subprojects {}`, que alcança as cinco, e só elas.
- [x] 5.4 **O primeiro vermelho, sobre a árvore real, sem nada plantado.** `./gradlew build --continue
  --rerun-tasks`. Previsto:

  | Tarefa | Nomeado | **Real** |
  |---|---|---|
  | `testDebugUnitTest` | `ApiPlatosPacoteTest` · `listagem sem rede vira SemRede` e `pacote sem rede vira SemRede` | **os dois**, e só eles |
  | `testReleaseUnitTest` | os mesmos dois | **os dois**, e só eles |
  | as outras três | nada | verdes: `jvmTest` 329, `testAndroidHostTest` 321, `:apps:api:test` 167 — declarados = resultados |

  Quatro linhas, e mais nenhuma. Registrar a saída inteira e a hora. **Outro nome, outra tarefa, ou um
  a menos: parar** (decisão 9). Commit `build:` **vermelho**, e dito na mensagem, como o commit 1 da
  7.3.

  **Duas execuções, e as duas ficam (P7).** A **primeira**, `20:27:32Z`–`20:32:28Z`, `exit 1`, derrubou
  as **cinco** tarefas — e todas pelo **piso** da própria guarda: "piso — nenhuma classe de teste
  compilada em []". A regra de parada valeu: nada foi mexido antes de ler as cinco mensagens. O
  diagnóstico: a primeira versão capturava `testClassesDirs` e `classpath` quando a tarefa é
  configurada, e os plugins (Kotlin, AGP, `jvm-test-suite`) **substituem** essas coleções depois; a
  referência capturada ficava vazia. Não houve comparação nenhuma — a guarda disse "não sei ler", que
  é o piso fazendo o que P13 pede, **sobre um defeito real do instrumento**, e não um veredito sobre a
  árvore. Pelo precedente do canário da 7.3, a leitura se corrigiu (as duas coleções lidas dentro do
  `doLast`) e o primeiro vermelho foi rodado **de novo desde o início**.

  A **segunda**, `20:33:01Z`–`20:37:09Z`, `exit 1`, 183 de 183 tasks: real = previsto, a tabela acima.
  As mensagens: ":apps:android:testDebugUnitTest: 2 metodo(s) declarado(s) com @Test sem resultado no
  relatorio: - com.platos.android.api.ApiPlatosPacoteTest > pacote sem rede vira SemRede - … > listagem
  sem rede vira SemRede", idem para `testReleaseUnitTest`. Commit 4, `592aa88`, **vermelho**, e dito na
  mensagem, com as duas execuções.

## 6. Commit 5 — os dois testes passam a rodar (item 5)

- [x] 6.1 **`= runBlocking<Unit> { … }`** nos dois (decisão 5). Verificar: `./gradlew build --continue
  --rerun-tasks` verde, as cinco guardas verdes, e o XML de `ApiPlatosPacoteTest` com `tests="9"` nas
  duas variantes.

  **Real = previsto.** `./gradlew build --continue --rerun-tasks`, `20:37:52Z`–`20:40:24Z`, `exit 0`, 183
  de 183 tasks. As cinco guardas: `testDebugUnitTest` **310**, `testReleaseUnitTest` **310**,
  `testAndroidHostTest` 321, `jvmTest` 329, `:apps:api:test` 167 — "todos com resultado no relatorio".
  `ApiPlatosPacoteTest` com `tests="9"` nos dois XML. Pelo `timestamp`: **182 suítes, 1758 testes, 0
  falhas** — a linha de base de 0.2 (1446) mais os 310 do release e os 2 que voltaram no debug.
- [x] 6.2 **Cada um visto falhar pela primeira vez:**

  | Mutação | Previsto | **Real** |
  |---|---|---|
  | `listagem sem rede`: o tipo esperado trocado por outro `Retorno` | cai **só** esse, nas duas variantes, com a mensagem de tipo | `Retorno.Recusou` no lugar de `SemRede`; `20:40:47Z`: 310 + 310, **1 + 1 falha**, só `listagem sem rede vira SemRede()`, "Unexpected type, expected: <…Retorno.Recusou> but was: <…SemRede>" |
  | `pacote sem rede`: idem | cai **só** esse, nas duas variantes | `20:41:34Z`: 1 + 1, só `pacote sem rede vira SemRede()`, a mesma mensagem |

- [x] 6.3 **A guarda, com defeito plantado e o piso:**

  | Defeito plantado | Previsto | **Real** |
  |---|---|---|
  | uma classe `MUTACAO` em `apps/api/src/test` com `@Test fun devolve(): Int = 1` e `@org.junit.jupiter.api.Test fun qualificado() = 2` | a guarda da API nomeia **os dois**, e nada mais; as outras quatro, verdes | `20:43:09Z`, as cinco tarefas: a da API cai com "2 metodo(s) … `MutacaoTestesInvisiveisTest > devolve`, `> qualificado`"; as outras quatro "todos com resultado". **E a classe não tem XML nenhum** — a "direção inversa" da 7.3, uma classe inteira invisível, pega pelo mesmo caminho |
  | a guarda de uma tarefa apontada para um diretório de relatórios vazio | reprova pelo **piso** | a leitura trocada para `…/MUTACAO-vazio` no `buildSrc`; `20:45:54Z`: "piso — nenhum relatorio TEST-*.xml em …\MUTACAO-vazio". **O outro piso** — nenhuma classe compilada — foi visto de verdade na primeira execução da 5.4 |

  Reverter cada um e rodar. Commit `test(android):`.

  **Reversões rodadas:** o `ApiPlatosPacoteTest` igual à cópia da 6.1 (`cmp`), `20:42:17Z`, 310 + 310; a
  classe plantada removida, `20:44:57Z`, API 167; o `buildSrc` igual ao commit 4 (`git diff --exit-code`),
  `20:46:43Z`, API 167. Commit 5.

## 7. Commit 6 — a credencial tem o mesmo desfecho em qualquer ordem (item 4)

Só se 1.3 reproduziu. Se não, as três tarefas ficam desmarcadas, com o motivo escrito.

> **Atualização de 2026-09-23, `19:56Z`, por decisão do mantenedor** (`design.md`, decisão 4, segunda
> atualização). Nada reproduziu — 1.3, 1.3b, e o aparelho físico. O grupo segue **sem conserto**: 7.1
> fica registrada como "não há causa a consertar", e 7.2 roda nos **dois** aparelhos (emulador
> `emulator-5554` e `TOXSR4MR9989MBQW`, escolhidos por `ANDROID_SERIAL`), com a ordem da hipótese no
> lugar da "ordem que reproduziu". O commit deste grupo, se houver, é só de registro.
>
> **Segunda atualização, `20:11Z` — e ela reverte a primeira (P7).** A reprodução apareceu na
> reversão da tabela, no aparelho físico, **sem** defeito plantado: `ENOENT` em
> `SessaoEmRepousoInstrumentedTest.kt:83` (a guarda 1). O mantenedor decidiu **consertar no teste**
> (`design.md`, decisão 4, terceira atualização). 7.1 volta a ser o conserto; 7.2 roda nos dois
> aparelhos, na ordem que caiu; e ao ver falhar se soma uma repetição da ordem que caiu no aparelho —
> dez vezes, como a medição da frequência.

- [x] 7.1 **O conserto, onde a pilha de 1.3 apontou** — no teste que sonda ou no que deixa escrita
  pendente —, sem afrouxar asserção nem esticar espera (decisão 4). Se apontou para
  `SessaoGuardadaAndroid`: **parar** e decidir com o mantenedor.

  **Feito, no teste.** A pilha apontou `SessaoEmRepousoInstrumentedTest.kt:83`, a guarda 1, e não o
  produto. As duas leituras de sondagem — a fotografia `antesDaCredencial` e a guarda 1 — passam por
  `bytesOuNulo`, sem `exists()` antes, e só `FileNotFoundException` vira `null` ("ainda não"). A
  fotografia repete a leitura quando a espera do keyset viu o arquivo; quando não viu, fica vazia,
  como antes (é o caso da sessão em claro). Nenhuma asserção nem espera mudou. **Um ajuste feito antes
  de rodar, dito:** a primeira versão condicionava a fotografia a `cifrado.exists()` — que a mesma
  janela pode ver falso —, e passou a usar o resultado da espera. `git diff --numstat` do arquivo:
  `27 3`. Commit 6, `ca86642`.
- [x] 7.2 **Ver falhar** — a tabela da decisão 4, copiada:

  | Execução | Sem defeito | Com o token gravado em claro (`MUTACAO` no produto) | **Real** |
  |---|---|---|---|
  | a classe isolada | verde | cai **em** "o token aparece como texto legivel" | emulador `20:48:29Z` e aparelho `20:54:11Z`: 1 de 2, a mensagem; sem defeito, `20:57:05Z` e `20:59:06Z`, 2 de 2 |
  | a ordem que reproduziu em 1.3 | verde | cai **em** "o token aparece como texto legivel" | emulador `20:49:13Z` e aparelho `20:54:30Z`: 1 de 5, a mensagem; sem defeito, `20:57:24Z` e `20:59:25Z`, 5 de 5 |
  | a suíte cheia | verde, todas | cai **só** esse cenário, **pela mesma mensagem** | emulador `20:49:35Z` e aparelho `20:54:50Z`: **1 de 83**, a mensagem; sem defeito, `20:57:45Z` e `20:59:45Z`, 83 de 83 |

  Com a mensagem lida do XML em cada uma (P9: o motivo, e não só a queda).

  **Real = previsto nas doze.** A mensagem foi lida do log de cada execução (o XML desta versão do AGP
  a guarda no corpo do `<failure>`). **Um tropeço de infraestrutura no meio, e dito:** a primeira
  rodada no aparelho (`20:50:58Z`–`20:52:38Z`) instalou **zero testes** — `INSTALL_FAILED_USER_RESTRICTED:
  Install canceled by user`, o aparelho pedindo confirmação na tela para instalar via USB. O produto foi
  revertido enquanto se esperava, o mantenedor liberou a instalação, e a mutação foi replantada para as
  três do aparelho.
- [x] 7.3 **A reversão**, rodada: o produto sem a mutação, e as três execuções de novo, verdes. Commit
  `test(android):`.

  **Feito.** O produto revertido (`git diff --exit-code apps/android/src/main` → `0`) antes de cada uma
  das duas rodadas sem defeito, e as seis verdes (acima). **E dez vezes a ordem que caiu, no aparelho**,
  `21:00:29Z`–`21:04:01Z`: 10 de 10, 5 de 5 cada, nenhum problema de instalação. **Isto não prova a
  corrida fechada**: antes do conserto, dez repetições seguidas também deram 0 (`20:07Z`–`20:10Z`). O que
  a fecha é a pilha, que aponta a linha, e a construção — a leitura que tropeçava não existe mais.

## 8. Registro

- [x] 8.1 **`docs/cobertura-o-apk-de-release-e-verificado.md`, Parte II**: o que cada commit fez, os
  primeiros vermelhos, todas as mutações com previsto e real, as reversões, e o que **não** fica
  verificado — no mínimo as três frases da decisão 11, e os três executores fora da guarda (decisão 7).

  **Feito**, §6 a §8. O que **não** fica verificado (§7) traz as três frases da decisão 11, os três
  executores fora da guarda, a forma de teste que ela casa, a corrida que não se força, e uma observação
  **por leitura** que a medição não pediu: a busca da afirmação de segurança tem a mesma forma de
  corrida, na direção perigosa, e a janela não se abre ali porque nenhuma escrita vem depois da guarda 2
  — dita, e não mexida (P19).
- [x] 8.2 **A nota onde cada achado aponta** (P7: a frase original fica, marcada): a auditoria §3.1 e
  §5.3 marcadas como fechadas, no molde de §3.2; a linha da credencial no §16 da arquitetura, com o que
  foi medido e consertado ao lado; `cobertura-o-fio-preso-nos-dois-lados.md` §6 e
  `cobertura-fatia-4a-cache-referencia.md` (a linha da variante release), com o fechamento; e no plano,
  ao lado de "Fica na tabela do §16 com essa fatia-limite" (§2), que essa linha **nunca foi
  acrescentada** — a da credencial foi, a do APK de release não. Verificar: os `git diff` só
  acrescentam. Commit `docs(o-apk-de-release-e-verificado):`.

  **Feito, e a linha do §16 fechou** — decisão do mantenedor, `21:05Z`, depois de a causa ter sido
  medida e consertada: riscada, com "fechado em 2026-09-23", e um parágrafo que diz o que estava errado
  nela (a ordem, e "a mesma classe de defeito que 3.2") ao lado do original. Notas: auditoria §3.1 e §5.3
  (títulos com `~~aberto~~ fechado`, no molde de §3.2, e o bloco "Fechado em"); a cobertura da ETAPA 5,
  §6, com a correção da hipótese da ordem; a da 7.3, §6; a da 4a, a linha da variante release; e o plano,
  §2. `git diff --numstat`: as linhas alteradas são só as dos títulos da auditoria, a linha da tabela do
  §16 e a da tabela da 4a — em todas, o texto antigo continua inteiro dentro da linha nova.

## 9. Fechamento

- [x] 9.1 **O comando cheio, depois de todas as reversões** (decisão 10): `grep -rn "MUTACAO"` vazio
  (mesmas exclusões de 1.4); `./gradlew build --continue --rerun-tasks`; `./gradlew -p buildSrc test
  --rerun-tasks`; `./gradlew :apps:android:connectedDebugAndroidTest` sem filtro. Previsto: a linha de
  base de 0.2 **mais** os 308 de `testReleaseUnitTest` e os 2 que passaram a rodar em cada variante —
  as contagens exatas escritas antes de rodar, e o real ao lado. Registrar na cobertura, com o quadro do
  §10 do plano.

  **Previsto, escrito às `21:08Z`, antes de rodar:** `build` com **182 suítes, 1758 testes, 0 falhas** —
  `testDebugUnitTest` 310, `testReleaseUnitTest` 310, `:apps:api:test` 167, `jvmTest` 329,
  `testAndroidHostTest` 321, `jsNodeTest` 321 —; as cinco guardas "todos com resultado"; a guarda do APK
  nomeando os dois APKs. `buildSrc`: 1 suíte, 1 teste. Instrumentada, sem filtro: **83 testes, 0 falhas,
  2 pulados** no emulador e **83, 0 falhas, 2 pulados** no aparelho (o probe do Supabase roda nos dois,
  pelo `local.properties`).

  **Real = previsto em todos.** `grep` vazio (`21:07Z`); `build` `21:09:22Z`–`21:11:58Z`, `exit 0`, 183 de
  183 tasks, **182 suítes, 1758 testes, 0 falhas**, as cinco guardas e a do APK como previsto;
  `buildSrc` `21:12:09Z`, 1 de 1; instrumentada no emulador `21:12:32Z`–`21:14:26Z` e no aparelho
  `21:14:27Z`–`21:15:13Z`, **83, 0 falhas, 2 pulados** nos dois. Registrado na cobertura, §8, com o
  quadro do §10 no §9.
- [ ] 9.2 **Publicar**: `git push` e a PR **empilhada**, com base `vewvniv/versao-do-renderizador-conferida`
  (PR #60). **Perguntar ao mantenedor antes do push.**
- [ ] 9.3 **O CI da PR, lido no destino** (P26): os três jobs verdes no commit da ponta; no log do
  `build`, `testReleaseUnitTest`, `verificarApkSemPacote` conferindo os dois APKs e as guardas de
  testes executados; na `paridade`, os 86 instrumentados. Registrar, e commit `registro:`.
