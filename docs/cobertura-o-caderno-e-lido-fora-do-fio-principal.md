# Cobertura — `o-caderno-e-lido-fora-do-fio-principal`

Data: 2026-09-30. Branch `vewvniv/propose-o-caderno-e-lido-fora-do-fio-principal`. Tudo abaixo foi
rodado **nesta sessão**; os logs estão no `scratchpad` da sessão (`base/` e `final/`) e os relatórios
citados são os XML de `apps/android/build/outputs/androidTest-results` e de `build/test-results`, com
o `timestamp` ao lado de cada número (P2, P3).

**Atualização de 2026-09-30, mais tarde:** o aparelho físico voltou e foi exercido (§9). O parágrafo abaixo descreve o estado de antes disso e fica (P7); a **lacuna que sobra** está no §9: no físico, a permissão de câmera não pôde ser concedida ao teste, e os dois testes que leem a tela do caderno retomado foram **pulados** lá.

**Estado da tarefa:** a correção está feita e **vista falhar e passar no emulador** `platos-atd34`
(Android 14). **O aparelho físico `2511FPC34G` (Android 16) saiu do USB durante a sessão** (nenhum
dispositivo Android no `Get-PnpDevice`, `adb devices` sem ele desde que o emulador foi encerrado para
liberar memória) e **não foi reencontrado**. As tarefas que pedem "nos dois aparelhos" (0.1, 1.1,
2.1, 2.2, 3.4) ficam **desmarcadas** por isso, com o que falta escrito nelas.

## 1. Linha de base (tarefa 0.1)

Emulador e daemons encerrados antes (9,3 GB livres, contra 5,1 GB com tudo aberto); Docker Desktop de
pé. Autorizado pelo mantenedor ("encerrar TUDO o que for necessário").

| Comando | Janela | Resultado |
|---|---|---|
| `./gradlew -p buildSrc test --rerun-tasks` | 20:19Z | `exit 0`, 6 de 6 tasks executadas |
| `./gradlew build --rerun-tasks` | 20:19:27Z–20:23:02Z | `BUILD SUCCESSFUL` em 3m08s, **183 de 183 tasks executadas**, `exit 0` do Gradle (`PIPESTATUS`, e não de um `date`) |

| Task | Testes | `timestamp` do relatório |
|---|---|---|
| `:apps:android:testDebugUnitTest` | 353 | 20:22:07Z–20:22:12Z |
| `:apps:android:testReleaseUnitTest` | 353 | 20:22:17Z–20:22:22Z |
| `:apps:api:test` | 171 | 20:22:26Z–20:22:41Z |
| `buildSrc:test` | 1 | 20:19:45Z |
| `:packages:domain:jsNodeTest` | 428 | 20:22:46Z–20:22:48Z |
| `:packages:domain:jvmTest` | 437 | 20:22:57Z–20:22:59Z |
| `:packages:domain:testAndroidHostTest` | 428 | 20:22:54Z–20:22:56Z |

Iguais às da `slice-5c-0` (android 353, api 171, domain 437/428/428, buildSrc 1).

**Instrumentado (emulador, `connectedDebugAndroidTest`, 20:24:27Z–20:26:20Z, relatório 20:26:17):**
**128 casos, 2 pulados, 2 falhas** — as duas são `LerCadernoNoFioPrincipalInstrumentedTest`, **a
medição do defeito** (não rastreada, anterior a esta tarefa). **Nenhuma outra falha.** Rodei com
`notClass=AbrirEscaneamentoComCadernoInstrumentedTest`, **filtrado de propósito**: o teste novo de
Activity derruba o processo sobre o código de hoje e abortaria o resto; por ser filtrado, esta linha
**é a base de comparação, e não fecha a suíte** (P5) — a suíte sem filtro está no §6.

**Uma contagem que não reconcilia, e não explico (P14).** A cobertura da 5c-0 registra "126
executados + 2 ignorados = 128 finalizados", com o código de hoje sem testes novos. Aqui, **128 − 2
(`LerCaderno`) = 126 casos**, dos quais 2 pulados: dois executados a menos do que a 5c-0 registrou.
O que confere: a contagem de `@Test` nos fontes de `androidTest` é **130** (126 da `main` + 2
`LerCaderno` + 2 `Abrir`), e 128 + 2 = 130 − 2 (o `notClass`). Ou seja, **o relatório de hoje bate com
o código de hoje**; é o "128" da 5c-0 que não bate com o código, e eu **não** investiguei qual dos dois
está errado. Não afeta esta mudança: a base é esta execução, ancorada no `timestamp` de 20:26:17.

`sha256` de `fixtures/*.layout.json` e `fixtures/*.package.json` registrados (0.2): `d9f7b08c…`
(folha-de-teste), `8c9756a9…` (prova-referencia), `87a9f331…` (prova-discursiva.package), entre
outros — **iguais** aos da 5c-0 e iguais aos do fim (§6). `node tools/divida/divida.mjs`: `exit 0`, 23
linhas lidas, **nenhuma vencida**; a única que vence na `5c` é a da guarda que não lê a tabela
"Aberto", que esta mudança não toma (0.3).

## 2. O defeito, visto antes de ser consertado (tarefas 1.1 e 1.2)

**Primeira medição (a leitura pelo fio principal, não a Activity).** `LerCadernoNoFioPrincipalInstrumentedTest`
chamava `CadernosEmRoom.ler` de dentro de `runOnMainSync`, pelo mesmo caminho do `onCreate`:

| Aparelho | `timestamp` | Resultado |
|---|---|---|
| Emulador `platos-atd34` (Android 14) | 2026-09-30T20:05:28 | 2 testes, 2 falhas |
| Aparelho físico `2511FPC34G` (Android 16) | 2026-09-30T20:05:37 | 2 testes, 2 falhas |

Mensagem nos dois: `IllegalStateException: Cannot access database on the main thread since it may
potentially lock the UI for a long period of time`, **com base vazia e com caderno guardado**. **O que
isto não provava:** que a `ScanActivity` cai — o teste usa o caminho do `onCreate`, mas não é o
`onCreate`, e o `runCatching` dele envolvia `abrir` e `ler`.

**Segunda medição, a de produção (tarefa 1.1).** `AbrirEscaneamentoComCadernoInstrumentedTest` lança a
`ScanActivity` por `Instrumentation.startActivitySync`, com o pacote de `prova-discursiva.package.json`
em `filesDir/packages` (hash pelo `MessageDigest` da JVM, P4) e permissão de câmera por
`UiAutomation`. Sobre o código de `main`, **no emulador**:

| Teste | Janela | Resultado |
|---|---|---|
| `abrir_…_com_caderno_guardado_…` (rodada das 4) | 20:27:17Z–20:27:36Z | `Instrumentation run failed due to Process crashed` |
| `abrir_…_sem_caderno_guardado_…` (sozinho) | 20:27:50Z–20:28:09Z | idem |

`java.lang.RuntimeException: Unable to start activity ComponentInfo{com.platos.android/com.platos.android.scan.ScanActivity}:
java.lang.IllegalStateException: Cannot access database on the main thread…`, com a **pilha** que o
design pedia registrar:

```
RoomDatabase.assertNotMainThread (RoomDatabase.android.kt:592)
CadernoDao_Impl.ler (CadernoDao_Impl.kt:55)
CadernosEmRoom.ler (CadernosEmRoom.kt:84)
ScanActivity.onCreate (ScanActivity.kt:138)
```

**Conclusão medida:** a exceção nasce em `CadernosEmRoom.ler`, **e não em `abrir`**, e **o
escaneamento cai ao abrir em `main`, com caderno guardado e sem ele** (emulador). O defeito chegou à
`main` na `slice-5b-3`. **Não medido:** a mesma queda no aparelho físico (ver o estado no topo).

## 3. A correção (tarefas 2.1 e 2.2)

- `scan/LerCadernoEmAndamento.kt` (novo): `CoroutineScope.lerCadernoEmAndamento(...)`, em
  `Dispatchers.IO`, sem `NonCancellable`.
- `scan/ScanActivity.kt`: `onCreate` mantém tudo o que não depende do caderno e lança
  `lifecycleScope.launch { montar(lerCadernoEmAndamento(...).await()) }`; `montar` cria a
  `ScanSession`, chama `setContent` e confere a permissão; o callback de permissão **ignora** o
  resultado enquanto `::session` não foi inicializada. `git diff`: 25 inserções, 6 remoções.
- Nenhum arquivo de `ScanSession`, `Caderno`, `CadernosEmRoom`, domínio, API, banco ou fio foi tocado.

**Testes (instrumentados):**
- `LerCadernoNoFioPrincipalInstrumentedTest` (3): base vazia e caderno guardado, pela **função de
  produção** chamada de `runOnMainSync`; e o **cancelamento no meio da leitura**, com a leitura presa
  numa trava (`CancellationException`, e não resultado).
- `AbrirEscaneamentoComCadernoInstrumentedTest` (4): Activity com caderno guardado (a tela mostra
  "Prova com discursiva", `Lifecycle` ≥ `RESUMED`); Activity sem caderno (mostra "Procurando a folha…"
  e o arquivo do banco **existe**: guarda de vacuidade, P13); recusa de abertura (`NaoAbre`) **não
  cria o arquivo do banco**; destruir a Activity logo depois de aberta não derruba o processo.

**Resultado no emulador, depois da correção:** 7 de 7, **0 falhas**, `timestamp` 2026-09-30T20:31:13
(janela 20:30:53Z–20:31:15Z); e de novo, **depois das reversões** das mutações, 7 de 7 às 20:33:31
(janela 20:33:09Z–20:33:33Z).

## 4. Como cada verificação foi vista falhar (P9, P10)

Previsões gravadas **antes** de rodar (`scratchpad/previsoes.md`). Cópias dos dois arquivos de
produção tiradas antes; cada reversão conferida por `cmp` **e** rodando de novo.

| Mutação | Previsão | Resultado | Motivo conferido |
|---|---|---|---|
| **M1** — a função lê na thread de quem chama (`CompletableDeferred(cadernos.ler(...))`), rodando só `LerCaderno…` (20:31:49Z; relatório 20:32:10) | 3 de 3 falham | **3 de 3** | duas com `IllegalStateException: Cannot access database on the main thread`; a do cancelamento com `esperava CancellationException, veio null` |
| **M2** — `onCreate` volta a ler direto (`montar(cadernos.ler(...))`), classe `LerCaderno…` (20:32:27Z) | verde 3/3 | **`BUILD SUCCESSFUL`** | — |
| **M2**, classe `Abrir…` (20:32:40Z) | o processo cai no primeiro teste que abre a Activity | **`Process crashed`**, `Unable to start activity … IllegalStateException: Cannot access database on the main thread` | a mensagem é a do defeito |

**Conjuntos disjuntos (P16):** M2 derruba **só** a camada da Activity e deixa a da função verde; M1
derruba a da função. É a prova de que uma camada não cobre a outra.

**Duas ressalvas (P8):**
- Para M2 na classe `LerCaderno…` só tenho o `BUILD SUCCESSFUL` do Gradle; o XML com a contagem foi
  sobrescrito pela rodada seguinte. A contagem 3 de 3 vem do §3 (mesma base, mesmo código de teste).
- Da reversão do M2 valem o `cmp` (idêntico ao backup) e a rodada das 20:33:31 (7 de 7).

**Sem mutação, e dito:** a **guarda do callback de permissão** (ignorar o resultado antes de a sessão
existir) **não tem teste**. O registro do `registerForActivityResult` entrega o resultado ao iniciar
depois de uma recriação, e não há costura para provocar isso antes de `montar` sem mudar produção
além do escopo. É **conhecido, não mitigado** (P8): a guarda é uma linha, e o que ela evita é
`UninitializedPropertyAccessException`. A tarefa 2.2 pedia três testes novos e este é o terceiro que
não existe: a tarefa foi ajustada com o motivo.

## 5. A janela em branco (tarefa 2.3)

Log temporário (`SystemClock.elapsedRealtimeNanos`) do início do `onCreate` ao fim de `montar`,
**revertido** (`cmp` idêntico, `grep` de `Medida0404` = 0), no emulador, nas três aberturas que chegam
a `montar` durante a classe `Abrir…`:

| Abertura | `onCreate` → fim de `montar` |
|---|---|
| 1ª (processo frio) | **314 ms** |
| 2ª | 118 ms |
| 3ª | 98 ms |

**O que o número é, e não é.** É o tempo **total** até a tela estar montada, e **inclui** o que o
`onCreate` de antes já fazia (`OpenCVLoader.initLocal`, leitura e conferência do pacote, roster), além
da leitura do caderno e da composição; eu **não** separei a parcela da leitura. A atribuição das três
medidas às aberturas (com caderno, sem caderno, destruir cedo) não foi registrada. Emulador x86 com
`swiftshader`, **não** aparelho real. **Dentro de uma fração de segundo** nas três, que era o critério
da tarefa; a frio, 314 ms, é a que merece o aparelho físico.

## 6. Verificação final (tarefa 3.4) — parcial

Emulador e daemons encerrados de novo para o agregado caber; Docker de pé.

| Comando | Janela | Resultado |
|---|---|---|
| `./gradlew -p buildSrc test --rerun-tasks` | 20:35:30Z | `exit 0` |
| `./gradlew build --rerun-tasks` | 20:35:30Z–20:39:00Z | `BUILD SUCCESSFUL` em 3m03s, **183 de 183 tasks executadas**, `exit 0` |

| Task | Testes | `timestamp` |
|---|---|---|
| `testDebugUnitTest` / `testReleaseUnitTest` | 353 / 353 | 20:38:22Z / 20:38:11Z |
| `apps:api:test` | 171 | 20:38:29Z–20:38:45Z |
| `buildSrc:test` | 1 | 20:35:48Z |
| `jsNodeTest` / `jvmTest` / `testAndroidHostTest` | 428 / 437 / 428 | 20:37:27Z / 20:38:48Z / 20:38:55Z |

**Iguais à linha de base** em todas as tasks: a mudança não tem teste JVM novo (os novos são
instrumentados). `sha256` das fixtures **iguais** ao da §1.

`./gradlew :apps:android:connectedDebugAndroidTest` **sem filtro, no emulador** (20:39:41Z–20:41:38Z,
relatório 20:41:35): **133 casos, 0 falhas, 2 pulados** = 128 da base + 4 `Abrir` + 1 a mais em
`LerCaderno` (3 em vez de 2). **No aparelho físico: não rodado.** `npx vitest run`: **não executado**,
a mudança não toca `apps/web`.

## 7. Erros de rumo (P7; todos mantidos)

1. **A minha primeira rodada de 7 testes caiu, e a correção estava certa.** O `@After` fechava o banco
   (`reiniciarParaTeste`) enquanto a `Activity`, ao terminar, guardava o caderno no `onStop` (em
   `Dispatchers.IO` com `NonCancellable`): `Database is closed`, processo morto. Era falha do teste, e
   eu a diagnostiquei pela mensagem e pela pilha (`CadernosEmRoom.guardar ← guardarCadernoEmAndamento`),
   e não pela contagem (P12). O teste agora espera a Activity ser destruída antes de fechar o banco
   (`encerrar`), com uma espera curta de 1 s cobrindo a gravação, que não é observável de fora —
   **uma folga de tempo, e não uma sincronização**; se a gravação passar disso, o teste volta a cair, e
   a queda diz por quê.
2. **A primeira versão do teste de leitura media `ler` direto**, e por desenho continuaria vermelho
   mesmo com o defeito corrigido; foi reescrito para chamar a função que a produção usa. A versão
   anterior é a evidência do §2.
3. **Uma contagem que não reconcilia com a 5c-0** (§1), registrada e não investigada.
4. **O aparelho físico sumiu** depois de eu encerrar o emulador (`adb devices` sem ele, e sem
   dispositivo Android no Windows). Não tenho como distinguir cabo, tela bloqueada ou outra causa.

## 8. O que esta mudança NÃO verificou (P6, P8)

- **O aparelho físico `2511FPC34G`** para tudo o que é novo: nem a queda em `main`, nem a correção, nem
  a suíte sem filtro. A primeira medição (§2) é a única evidência nele (2 falhas, 20:05:37).
- **Um professor abrindo o escaneamento de verdade**: os testes lançam a Activity por
  `Instrumentation`, com permissão concedida por `UiAutomation`; nenhum fluxo com o diálogo de
  permissão foi exercido.
- **A guarda do callback de permissão** (§4): sem teste.
- **O ramo de cancelamento no meio da leitura, pela Activity:** o teste de "destruir cedo" não o força
  (a leitura é rápida); quem o prova é o teste da função, com a trava.
- **Caderno corrompido:** `ler` lança `SerializationException` e a abertura cai do mesmo jeito que
  antes, agora dentro do `await`. **Achado adjacente, não corrigido** (regra 6, P19), e **não
  mitigado**. Decisão de como degradar é do mantenedor e toca a spec.
- **A janela em branco em aparelho real**, e a parcela da leitura dentro dela (§5).
- Android abaixo da 14 ou acima da 16.

## 9. O aparelho físico (2511FPC34G, Android 16, Xiaomi) — acrescentado depois

O aparelho voltou ao USB. Sequência, com o `ScanActivity.kt` de `HEAD` (via `git show HEAD:`) onde a
queda é o objeto, e restaurado por `cmp` depois (as duas peças de produção idênticas aos backups no
fim: `cmp` sobre `ScanActivity.kt` e `LerCadernoEmAndamento.kt`).

| O quê | Emulador (Android 14) | Físico (Android 16) |
|---|---|---|
| **Linha de base**, `ScanActivity` de `HEAD`, sem a classe da Activity (21:01Z) | 129 casos, 0 falhas, 2 pulados, relatório 21:01:12 | 129 casos, 0 falhas, 2 pulados, relatório 21:01:05 |
| **A queda em `HEAD`** (Activity lançada), 21:06:43Z | `Process crashed`, `Unable to start activity … IllegalStateException`, pilha `CadernosEmRoom.ler ← ScanActivity.onCreate:138` | **idem**, mesma mensagem e mesma pilha |
| **Classe da função com a correção** (21:02Z) | 3 de 3, relatório 21:02:20 | 3 de 3, relatório 21:02:33 |
| **M1** (a função lê na thread de quem chama), 21:02:35Z | 3 de 3 falham, 21:02:55 | 3 de 3 falham, 21:03:05 |
| **Depois da correção**, `Abrir…` + `Ler…` (21:06:56Z–21:07:24Z) | 7 de 7, 0 falhas, 21:07:18 | 7 casos: **5 passaram, 0 falhas, 2 pulados**, 21:07:23 |
| **Suíte sem filtro** (21:07:33Z–21:09:01Z) | **133 casos, 0 falhas**, 2 pulados, 21:08:57 | **133 casos, 0 falhas**, 4 pulados, 21:08:44 |

**Motivo das falhas da M1 nos dois aparelhos** (conferido no log): `IllegalStateException: Cannot access
database on the main thread` (as duas de leitura) e `esperava CancellationException, veio null` (a do
cancelamento). **A queda em `HEAD` agora está medida nos dois aparelhos** com a `ScanActivity` real —
antes só no emulador (§2).

**Os 4 pulados do físico, um a um:** `AcumuloDeInstanciasProbe` ×2 (pulados de propósito, como no
emulador) e **dois meus**: `abrir_o_escaneamento_com_caderno_guardado_…` e
`abrir_o_escaneamento_sem_caderno_guardado_…`.

**Por que os meus dois foram pulados, e o que isso custa (P8).** `UiAutomation.grantRuntimePermission`
e `pm grant` foram **recusados** no Xiaomi (`Neither user 2000 nor current process has
android.permission.GRANT_RUNTIME_PERMISSIONS`). O mantenedor informou ter ligado "Depuração USB
(configurações de segurança)" e a recusa **persistiu** (medido em 21:05Z, depois da informação): não sei
se a opção não ficou efetiva — no HyperOS ela costuma exigir conta Mi e reconexão do USB — ou se é outra
causa; isso só se vê no aparelho. Os dois testes que **leem a tela do caderno retomado** ("Prova com
discursiva" / "Procurando a folha…") exigem a permissão e, sem ela, foram **pulados com o motivo**, e
**não contados como verdes**. O que o físico **prova** depois da correção: a Activity abre e é destruída
sem derrubar o processo (`destruir_…`), a recusa de abertura não consulta o Room (`recusar_…`), e as
três de leitura da função. O que o físico **não prova**: que a sessão mostra o caderno retomado na tela.
Essa prova existe só no emulador (§3). **Não é mitigado, é conhecido.**

**Um efeito colateral do teste que escrevi no físico:** o `pm grant` que usei como sonda foi tentado num
aplicativo de terceiros já instalado (`com.instagram.barcelona`) e foi recusado; nada foi concedido.

**Reconciliação com as tarefas.** 0.1, 1.1, 2.1, 2.2 e 3.4 passam a ter execução nos dois aparelhos, com
a ressalva acima escrita em cada uma.

### 9.1 A lacuna do físico, fechada com o toque manual (21:12Z)

O mantenedor propôs tocar ele mesmo em "Permitir" no diálogo de câmera do Android. O teste ganhou um
modo **opt-in**, `-Pandroid.testInstrumentationRunnerArguments.permissaoManual=true`: se a concessão por
`UiAutomation` for recusada, ele **abre a Activity e espera até 2 minutos** o toque, em vez de pular.
Desligado por padrão, para a suíte normal não ficar parada esperando uma pessoa.

| O quê | Emulador | Físico (2511FPC34G) |
|---|---|---|
| Os 2 testes que leem a tela, modo manual (21:12:14Z–21:12:41Z) | 2 de 2, 0 falhas, 0 pulados, relatório 21:12:27 | **2 de 2, 0 falhas, 0 pulados**, relatório **21:12:39** (`com_caderno` 5,1 s, `sem_caderno` 2,9 s) |

**Que o toque aconteceu, e o teste o esperou:** o marcador `>>> TOQUE EM 'PERMITIR' …` está no logcat do
físico às 21:12:32 (23:12:32 local). Ele só é impresso se a permissão **ainda não** estava concedida; o
teste só seguiu depois de `checkSelfPermission` virar `GRANTED`, e depois **leu a tela**: "Prova com
discursiva" (caderno retomado) e "Procurando a folha…" (sem caderno). Ou seja, **no Xiaomi, a sessão
mostra o caderno retomado** — o que o §9 dizia não estar provado lá.

**O que continua verdade:** sem o argumento, a suíte cheia no físico **pula** esses dois testes
(21:13:10Z–21:14:38Z: 133 casos, 0 falhas, 4 pulados no físico, 2 no emulador; relatórios 21:14:21 e
21:14:35). A prova do §9.1 é **manual e pontual**, e não roda sozinha em CI. A trava de
`GRANT_RUNTIME_PERMISSIONS` do aparelho **não foi resolvida**, só contornada com a pessoa.
