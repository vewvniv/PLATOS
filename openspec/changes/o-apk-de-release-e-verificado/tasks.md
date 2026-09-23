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

- [ ] 2.1 **`ci.yml`**: tirar o bloco `concurrency` do nível do workflow e declarar um em cada job,
  conforme a decisão 3 — grupo com o nome do job; `true` em `build` e `web`, `false` em `paridade`. Um
  comentário no tom dos vizinhos, citando P15 e a PR #30. Verificar por `yaml.safe_load`: **não** há
  `concurrency` no topo; os três jobs têm `concurrency` com os três grupos **distintos** e os três
  valores; nenhum outro passo mudou (`git diff` do arquivo só mexe nessas linhas). **Tipo: conferido
  por leitura, e não medido** — dizer assim no commit e na cobertura (P6). Commit `ci:`.

## 3. Commit 2 — a guarda do APK sobre o release (item 1)

- [ ] 3.1 **`verificarApkSemPacote` recebe as duas variantes**, com vacuidade por variante (decisão 1).
  Verificar: `./gradlew :apps:android:verificarApkSemPacote --rerun-tasks` verde, e a linha de log
  passa a dizer quantos assets conferiu **em cada** APK — os dois nomeados.
- [ ] 3.2 **Ver falhar**, três defeitos, um de cada vez:

  | Defeito plantado | Previsto | **Real** |
  |---|---|---|
  | o mesmo JSON de 1.1 em `src/release/assets/` | **recusa**, nomeando o APK **de release** e a entrada, e **não** o de debug | |
  | o mesmo JSON em `src/debug/assets/` | **recusa**, nomeando o APK **de debug**, e **não** o de release | |
  | o diretório do release trocado por um que não existe, no registro da tarefa | **recusa** por vacuidade: "nenhum APK de release" | |

  O primeiro é o buraco da 1.1 fechado: mesmo defeito, desfecho oposto. Reverter cada um e rodar.
  Commit `build(android):`.

## 4. Commit 3 — a variante de teste do release (item 2)

- [ ] 4.1 **`beforeVariants` liga a variante** (decisão 2). Verificar: `:apps:android:tasks --all`
  lista `testReleaseUnitTest`; `./gradlew :apps:android:test --rerun-tasks` roda **as duas** tarefas
  (no log), e os XML dão **308 + 308**; se a forma tipada diferir da usada na medição, dizer qual ficou
  e por quê.
- [ ] 4.2 **Ver falhar** — o buraco da 1.2 fechado:

  | Defeito plantado | Previsto | **Real** |
  |---|---|---|
  | o mesmo teste de 1.2 (`BuildConfig.DEBUG`) | `testDebugUnitTest` verde com ele; `testReleaseUnitTest` **cai só nele**; `./gradlew build` vermelho por essa tarefa | |

  Reverter e rodar. Commit `build(android):`.

## 5. Commit 4 — a guarda de testes executados, e ela nasce vermelha (item 5)

- [ ] 5.1 **Conferir antes de escrever** (decisão 6): por `javap -v` sobre uma classe compilada de
  cada suíte (`apps/api`, `apps/android`, `packages/domain` `jvmTest`), qual anotação de tempo de
  execução o `@Test` vira. Previsto: `org.junit.jupiter.api.Test` nas três, inclusive onde o fonte usa
  `kotlin.test.Test`. Se não for, parar: a decisão 6 foi escrita sobre isso.
- [ ] 5.2 **Escrever a tarefa em `buildSrc`** conforme a decisão 6 — declaração por reflexão sobre o
  bytecode, sem inicializar; resultado pelo XML, nome normalizado; piso; falha fechada — e
  registrá-la nos três módulos, uma instância por tarefa `Test`, ligada ao `check` e rodando depois da
  tarefa que julga. Verificar: `./gradlew -p buildSrc test --rerun-tasks` verde (nada quebrou lá) e a
  compilação dos três `build.gradle.kts`.
- [ ] 5.3 **As tarefas cobertas, lidas do grafo** (decisão 7): listar as tarefas `Test` dos três
  módulos e as instâncias da guarda. Previsto: `test` (API), `testDebugUnitTest` e
  `testReleaseUnitTest` (aplicativo), `jvmTest` e `testAndroidHostTest` (domínio) — cinco, cada uma com
  a sua guarda. Outra lista: parar e dizer.
- [ ] 5.4 **O primeiro vermelho, sobre a árvore real, sem nada plantado.** `./gradlew build --continue
  --rerun-tasks`. Previsto:

  | Tarefa | Nomeado | **Real** |
  |---|---|---|
  | `testDebugUnitTest` | `ApiPlatosPacoteTest` · `listagem sem rede vira SemRede` e `pacote sem rede vira SemRede` | |
  | `testReleaseUnitTest` | os mesmos dois | |
  | as outras três | nada | |

  Quatro linhas, e mais nenhuma. Registrar a saída inteira e a hora. **Outro nome, outra tarefa, ou um
  a menos: parar** (decisão 9). Commit `build:` **vermelho**, e dito na mensagem, como o commit 1 da
  7.3.

## 6. Commit 5 — os dois testes passam a rodar (item 5)

- [ ] 6.1 **`= runBlocking<Unit> { … }`** nos dois (decisão 5). Verificar: `./gradlew build --continue
  --rerun-tasks` verde, as cinco guardas verdes, e o XML de `ApiPlatosPacoteTest` com `tests="9"` nas
  duas variantes.
- [ ] 6.2 **Cada um visto falhar pela primeira vez:**

  | Mutação | Previsto | **Real** |
  |---|---|---|
  | `listagem sem rede`: o tipo esperado trocado por outro `Retorno` | cai **só** esse, nas duas variantes, com a mensagem de tipo | |
  | `pacote sem rede`: idem | cai **só** esse, nas duas variantes | |

- [ ] 6.3 **A guarda, com defeito plantado e o piso:**

  | Defeito plantado | Previsto | **Real** |
  |---|---|---|
  | uma classe `MUTACAO` em `apps/api/src/test` com `@Test fun devolve(): Int = 1` e `@org.junit.jupiter.api.Test fun qualificado() = 2` | a guarda da API nomeia **os dois**, e nada mais; as outras quatro, verdes | |
  | a guarda de uma tarefa apontada para um diretório de relatórios vazio | reprova pelo **piso** | |

  Reverter cada um e rodar. Commit `test(android):`.

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

- [ ] 7.1 **O conserto, onde a pilha de 1.3 apontou** — no teste que sonda ou no que deixa escrita
  pendente —, sem afrouxar asserção nem esticar espera (decisão 4). Se apontou para
  `SessaoGuardadaAndroid`: **parar** e decidir com o mantenedor.
- [ ] 7.2 **Ver falhar** — a tabela da decisão 4, copiada:

  | Execução | Sem defeito | Com o token gravado em claro (`MUTACAO` no produto) | **Real** |
  |---|---|---|---|
  | a classe isolada | verde | cai **em** "o token aparece como texto legivel" | |
  | a ordem que reproduziu em 1.3 | verde | cai **em** "o token aparece como texto legivel" | |
  | a suíte cheia | verde, todas | cai **só** esse cenário, **pela mesma mensagem** | |

  Com a mensagem lida do XML em cada uma (P9: o motivo, e não só a queda).
- [ ] 7.3 **A reversão**, rodada: o produto sem a mutação, e as três execuções de novo, verdes. Commit
  `test(android):`.

## 8. Registro

- [ ] 8.1 **`docs/cobertura-o-apk-de-release-e-verificado.md`, Parte II**: o que cada commit fez, os
  primeiros vermelhos, todas as mutações com previsto e real, as reversões, e o que **não** fica
  verificado — no mínimo as três frases da decisão 11, e os três executores fora da guarda (decisão 7).
- [ ] 8.2 **A nota onde cada achado aponta** (P7: a frase original fica, marcada): a auditoria §3.1 e
  §5.3 marcadas como fechadas, no molde de §3.2; a linha da credencial no §16 da arquitetura, com o que
  foi medido e consertado ao lado; `cobertura-o-fio-preso-nos-dois-lados.md` §6 e
  `cobertura-fatia-4a-cache-referencia.md` (a linha da variante release), com o fechamento; e no plano,
  ao lado de "Fica na tabela do §16 com essa fatia-limite" (§2), que essa linha **nunca foi
  acrescentada** — a da credencial foi, a do APK de release não. Verificar: os `git diff` só
  acrescentam. Commit `docs(o-apk-de-release-e-verificado):`.

## 9. Fechamento

- [ ] 9.1 **O comando cheio, depois de todas as reversões** (decisão 10): `grep -rn "MUTACAO"` vazio
  (mesmas exclusões de 1.4); `./gradlew build --continue --rerun-tasks`; `./gradlew -p buildSrc test
  --rerun-tasks`; `./gradlew :apps:android:connectedDebugAndroidTest` sem filtro. Previsto: a linha de
  base de 0.2 **mais** os 308 de `testReleaseUnitTest` e os 2 que passaram a rodar em cada variante —
  as contagens exatas escritas antes de rodar, e o real ao lado. Registrar na cobertura, com o quadro do
  §10 do plano.
- [ ] 9.2 **Publicar**: `git push` e a PR **empilhada**, com base `vewvniv/versao-do-renderizador-conferida`
  (PR #60). **Perguntar ao mantenedor antes do push.**
- [ ] 9.3 **O CI da PR, lido no destino** (P26): os três jobs verdes no commit da ponta; no log do
  `build`, `testReleaseUnitTest`, `verificarApkSemPacote` conferindo os dois APKs e as guardas de
  testes executados; na `paridade`, os 86 instrumentados. Registrar, e commit `registro:`.
