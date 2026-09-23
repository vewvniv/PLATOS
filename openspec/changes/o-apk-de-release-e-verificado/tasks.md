## 0. Antes de qualquer commit — o ambiente e a linha de base

- [ ] 0.1 **O ambiente, conferido e registrado** (P22). O mantenedor autorizou **emulador e Docker**
  para esta etapa em 2026-09-23; aparelho físico **não**. Registrar: branch
  `vewvniv/o-apk-de-release-e-verificado`, criada sobre `3e7df3b`; `git status` limpo; `docker info`;
  o AVD usado e `adb devices` mostrando **só** o emulador — se um aparelho físico estiver conectado,
  parar e perguntar antes de qualquer `connected…`.
- [ ] 0.2 **A linha de base desta sessão** (P3), com o `timestamp` de dentro de cada XML:
  - `./gradlew build --continue --rerun-tasks` — previsto **153 suítes, 1446 testes, 0 falhas**, a
    linha de base da 7.1;
  - `./gradlew :apps:android:connectedDebugAndroidTest`, **sem filtro** — previsto **86** testes, o
    número do log da `paridade` da PR #60, 0 falhas;
  - a mesma tarefa só com `SessaoEmRepousoInstrumentedTest` — previsto verde (registro da ETAPA 5).

  Se algum número diferir, parar e explicar antes da tarefa 1.

## 1. A medição de entrada — ver o buraco, antes de qualquer código

Cada defeito plantado carrega `MUTACAO` (no nome ou no conteúdo), é revertido, e a reversão é
**rodada** antes do próximo (P10). O conjunto previsto:

| Buraco | Defeito plantado | Comando | Previsto | **Real** |
|---|---|---|---|---|
| item 1 | `apps/android/src/release/assets/MUTACAO-pacote.json` com `"answer_key"` e `"min_renderer_version"` | `./gradlew :apps:android:verificarApkSemPacote --rerun-tasks` | **verde** — a guarda só abre o debug; e `unzip -l` mostra a entrada **no APK de release**, e não no de debug | |
| item 2 | um `@Test` em `apps/android/src/test` que afirma `BuildConfig.DEBUG` | `./gradlew :apps:android:test --rerun-tasks` | **verde** — `testReleaseUnitTest` não existe; o XML do debug tem o teste, passando | |

- [ ] 1.1 **O buraco do item 1.** Plantar, rodar, conferir o APK de release com `unzip -l` (a entrada
  está lá) e o de debug (não está). Previsto na tabela. Remover o arquivo plantado; `git status` limpo;
  a guarda de novo, verde.
- [ ] 1.2 **O buraco do item 2.** Plantar, rodar, conferir no log que só `testDebugUnitTest` rodou e
  que o XML dele traz o teste plantado passando. Remover; `git status` limpo; o mesmo comando, verde.
- [ ] 1.3 **A reprodução do item 4**, pela ordem da decisão 4 do `design.md`, **parando na primeira que
  cair**: (a) `SegundoMembroInstrumentedTest` e em seguida `SessaoEmRepousoInstrumentedTest`, por
  `-Pandroid.testInstrumentationRunnerArguments.class=…`; (b) a suíte cheia, três vezes; (c) a suíte
  cheia sem as classes instrumentadas que entraram depois de `2026-09-19T00:06Z` (conferidas no `git
  log`). Registrar de cada execução: hora UTC, contagem, e — na que cair — a **mensagem e a pilha
  inteiras** do cenário, lidas do XML. **Se nenhuma cair: parar** (decisão 4) e levar ao mantenedor;
  as tarefas do grupo 7 ficam desmarcadas com o motivo.
- [ ] 1.4 **Registro, antes do código.** `docs/cobertura-o-apk-de-release-e-verificado.md`, Parte I: a
  linha de base, os dois buracos com previsto e real, a reprodução (ou a falta dela) com a pilha, e uma
  Parte II que diz "ainda não existe". Preencher a coluna **Real** acima. Verificar: `grep -rn
  "MUTACAO"` fora de `docs/`, `openspec/`, `build/`, `node_modules/`, `.gradle/` e `.git/` vazio;
  `git status` só com o documento e este `tasks.md`. Commit `registro:`.

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
