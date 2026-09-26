# Cobertura — `slice-5b-1-o-aparelho-reconhece-a-discursiva`

Primeira das duas mudanças da 5b. Os artefatos estão em
`openspec/changes/archive/2026-09-26-slice-5b-1-o-aparelho-reconhece-a-discursiva/` (arquivada em
2026-09-26). Este documento registra **como**
cada verificação foi vista falhar, e não que ela passa (`rigorous.md` §8). As datas são UTC.

## 0. Linha de base

**0.1** — Sobre `main` (`998558d`) e o commit da proposta (`b8e6692`), que só acrescenta arquivos em
`openspec/changes/`. Do início às 2026-09-24T22:45:06Z até 22:50:35Z:

| Comando | Resultado |
|---|---|
| `./gradlew build --rerun-tasks` | `BUILD SUCCESSFUL in 4m 31s`, **183 de 183 tasks executadas** |
| `./gradlew :apps:android:connectedDebugAndroidTest`, sem filtro, no `platos-atd34` | 84 testes, 0 falhas, 2 pulados, `timestamp` 22:50:32Z |
| `npx vitest run` em `apps/web` | 16 de 16, 22:45:29Z |

Relatórios do `build`, lidos por `relatorios.mjs` (scratchpad):

| Task | Testes | `timestamp` |
|---|---|---|
| `apps/android` `testDebugUnitTest` | 312 | 22:48:38Z .. 22:48:44Z |
| `apps/android` `testReleaseUnitTest` | 312 | 22:48:25Z .. 22:48:32Z |
| `apps/api` `test` | 168 | 22:49:07Z .. 22:49:23Z |
| `packages/domain` `jvmTest` | 375 | 22:49:26Z .. 22:49:28Z |
| `packages/domain` `jsNodeTest` | 366 | 22:49:34Z .. 22:49:36Z |
| `packages/domain` `testAndroidHostTest` | 366 | 22:48:56Z .. 22:48:59Z |

O relatório de `buildSrc` saiu marcado **VELHO** (12:02:36Z): o `build` não roda os testes de
`buildSrc`, e esta mudança não o toca. O passo próprio dele roda no fechamento.

**0.2** — `node tools/divida/divida.mjs`, `exit 0`, com 20 linhas lidas e nenhuma vencida:
"fatia corrente: 5b, de slice-5b-1-o-aparelho-reconhece-a-discursiva (ativa)". Sob "vence nesta fatia
(5b)" aparecem:
- `Acurácia em manuscrito` (`5`);
- `Modo degradado (§10) não existe` (`5`);
- `O limiar do OMR foi apurado sobre um aparelho e uma impressora` (`5`);
- `A região discursiva ainda não passou pelo aparelho nem pelo papel` (`5b`).

## 1. A captura por região

**1.1 — os marcadores esperados são os da região.** `RegionDetector.declaredMarkersOf` passa a filtrar
pelos `marker_ids` da região.

O instrumento é `FolhaDiscursivaRenderizada`, em `androidTest`. Ele desenha a folha de `tok-a` da
prova com discursiva pelo `LayoutMapRenderer` de produção, num PDF, e a rasteriza pelo `PdfRenderer`
da plataforma a 10 px/mm. Não é dependência nova: `PdfRenderer` é do SDK. O cenário é
`RegiaoDiscursivaInstrumentedTest.o_gabarito_e_retificado_numa_pagina_que_tem_outra_regiao`.

**Visto falhar sobre o código real, antes da correção.** O teste foi escrito primeiro e rodado contra
a `main`, às 22:52:06Z: 1 teste, 1 falha, "esperava a regiao 0 retificada, veio Failed(reason=a pagina
0 declara 8 ArUcos; esperados 4)". Isto **mede** o segundo defeito que a correção P7 da linha `5b` (PR
#68) descrevia só por leitura. Depois da correção, às 22:52:39Z: 1 de 1, e os marcadores detectados são
`[0, 1, 2, 3]`.

A mutação que a tarefa pedia ("sem o filtro") é exatamente o código anterior. A execução de antes é o
vermelho dela, e a de depois é a reversão rodada.

**1.2 — as regiões saem do quadro.**
- **`RegionDetector`:** `detectMarkers` passa a ser chamado uma vez por quadro, e `detect` ganha a
  forma que recebe os marcadores já encontrados.
- **`SheetReader.analyze`:** recebe o **mapa**, e não uma região. Cada região cujos quatro
  `marker_ids` estão no quadro é lida. A discursiva é retificada e tem o QR conferido, sem medição de
  bolha.
- **`FrameOutcome`:** as quatro variantes de antes continuam falando do gabarito e carregam a lista das
  regiões discursivas do quadro, vazia por padrão. A variante nova, `SoDiscursivas`, é o quadro que
  só tem moldura.
- **`CameraFrameAnalyzer` e `ScanActivity`:** perdem a região. O `map.regions.single()` sai daqui, e a
  prova de que a queda sumiu, com o "ver falhar" dela, é a 3.1.
- **`ScanSession`:** ganhou só o ramo que a variante nova exige. A lógica da prova com discursiva é a
  2.1.

Testes em `RegiaoDiscursivaInstrumentedTest`, sobre a folha de `tok-a` renderizada, um por cenário:
- a página 0 lê o gabarito e reconhece a região de `d1`, com o `tok-a` e a região 1 no QR;
- a página 1 só tem `d2`, e o gabarito não é exigido;
- a região pela metade: a página 0 cortada no meio de `d1`, com uma guarda de vacuidade que exige o
  gabarito inteiro acima do corte. O gabarito é lido, e `d1` não;
- o QR da região 2 desenhado dentro dos marcadores da região 1 é recusado com "o QR diz regiao 2".

5 de 5, às 22:56:46Z.

**Vista falhar:** a identificação trocada por "só a região 0" (`regiao.index == 0 && …`,
`// MUTACAO`).
- **Previsto:** caem os três cenários que precisam achar uma região discursiva, e ficam verdes o da
  1.1, que chama `detect` direto, e o da região pela metade.
- **Real:** exatamente esses três, com 5 testes e 3 falhas, às 22:57:19Z. A página 1 caiu com "achei
  os marcadores [8, 9, 10, 11], e nenhuma regiao do mapa tem os quatro dela", e as outras duas com a
  lista de discursivas vazia.

Revertida, `grep MUTACAO` deu 0, e rodado de novo: 5 de 5, às 22:57:35Z.

**1.3 — a prova só objetiva não muda de fora.** Os ajustes de assinatura foram só estes, em
`SheetReaderInstrumentedTest`:
- três chamadas de `analyze(x, map, region, limiar)` viraram `analyze(x, map, limiar)`;
- o cenário do corredor que exclui o limiar passa a pôr a região modificada **dentro** do mapa
  (`map.copy(regions = listOf(outroCorredor))`), porque a análise não recebe mais a região. O motivo
  está no próprio teste.

Nenhuma asserção mudou.
- `./gradlew :apps:android:testDebugUnitTest --rerun`: 312 de 312, às 22:57:51Z, com `ScanSessionTest`
  e `IdentidadeDaFolhaTest` inalterados.
- `./gradlew :apps:android:connectedDebugAndroidTest`, sem filtro: **89** testes (os 84 da linha de
  base e os 5 novos), 0 falhas, 2 pulados, `timestamp` 22:58:43Z. Os 10 cenários de
  `CorpusInstrumentedTest` estão entre eles, verdes.

### 1.1 a 1.3 reexecutadas sobre a região de dois ArUcos (2026-09-26)

**Tudo acima, em 1.1 a 1.3, foi medido contra a geometria antiga**, com a região discursiva de quatro
marcadores, e a página 0 declarando 8. A `slice-5b-0-a-regiao-discursiva-compacta` (PR #70) reduziu a
região a dois marcadores na diagonal, e o `/opsx:update` de 2026-09-25 reabriu as três tarefas. O
registro de antes fica, porque é o que foi medido naquele dia (P7).

**O branch não tinha a 5b-0.** `origin/main` estava 22 commits à frente. O `/opsx:update` foi
commitado sozinho (`9ca3d8d`), e a `main` entrou por merge (`728f832`), sem reescrever o histórico.

**O desenho tinha uma lacuna, e o mantenedor a decidiu antes do código.** A decisão 1 dizia que a
homografia da região discursiva sai dos "cantos externos dos dois", que são dois pontos, e não dizia
se o passo (c) do ADR-0018 entra aqui. A decisão está no `design.md`, decisão 1, "Atualizado ao
aplicar, em 2026-09-26":
- a homografia é ajustada por mínimos quadrados sobre os oito cantos, com a posição declarada no
  `DrawAruco` normalizada pelo retângulo declarado. O caminho sai da contagem de marcadores, e não do
  tipo;
- não há teto de resíduo novo;
- o passo (c) e o resíduo dele vão para a 5b-2;
- o `region_idx` do QR é conferido contra os `marker_ids` que o mapa declara.

**Linha de base da reabertura, sobre o código de antes e a fixture nova.** Previsto: caem os três
cenários que precisam retificar uma região de dois marcadores, e ficam verdes o da 1.1 e o da região
pela metade. Real: exatamente esses, com 5 testes e 3 falhas, às 09:04:57Z. Nas duas páginas, o motivo
foi "a pagina N declara 2 ArUcos; esperados 4". É o vermelho, **sobre o código real**, da regra "a
contagem vem da região".

**O código** (`RegionDetector`, `RegionQrReader`, `SheetReader`):
- `detect` exige `region.markerIds.size` marcadores declarados, e não 4;
- quatro marcadores seguem a homografia exata pelos centros, com o teto de 6 px nos cantos, como
  antes. Dois seguem `Calib3d.findHomography`, com o método 0 e sem RANSAC, sobre os oito cantos. O
  resíduo sai em `reprojectionErrorPx`, e nada decide por ele. `Calib3d` é do mesmo AAR
  `org.opencv:opencv` 4.11, e não é dependência nova;
- `RegionQrReader.read` recebe o mapa, e o QR de uma região que o mapa não declara é recusado.

Depois do código: 5 de 5, às 09:07:59Z.

**1.1, vista falhar.** O filtro de `declaredMarkersOf` foi trocado por `true || …` (`// MUTACAO`).
- **Previsto:** caem 4, com "declara 6 ArUcos; esperados 4" no gabarito. A página 1 fica verde,
  porque só tem os dois marcadores de `d2`.
- **Real:** exatamente esses 4, às 09:08:28Z. O motivo foi "a pagina 0 declara 6 ArUcos; esperados 4"
  no gabarito, e "…; esperados 2" em `d1`.
- **Reversão:** `grep MUTACAO` deu 0, e rodado de novo, 5 de 5, às 09:09:22Z.

**1.2, vista falhar.** Duas mutações.

| Mutação | Previsto | Real |
|---|---|---|
| **Só a região 0 é identificada**, a mesma da execução anterior | caem página 0, página 1 e QR trocado; ficam verdes o da 1.1 e o da metade | exatamente esses 3, às 09:09:47Z. A página 1 caiu com "achei os marcadores [8, 11], e nenhuma regiao do mapa tem todos os dela"; as outras duas, com a lista de discursivas vazia ("List is empty") |
| **O QR conferido contra a alocação `{4k…4k+3}`**, que é o código de antes | caem página 0 e página 1; **sobrevive** o do QR trocado, porque a divergência continua e a mensagem continua começando com "o QR diz regiao 2" | exatamente esses 2, às 09:10:53Z. A mensagem saiu "o QR diz regiao 1, que usa os marcadores [4, 7], mas a captura tem [4, 7]": ela imprime o valor do mapa, e a comparação mutada era contra `[4, 5, 6, 7]`. A previsão escrita antes de rodar já dizia isso |

As duas foram revertidas. Depois da primeira, `grep MUTACAO` deu 0 e a classe rodou 5 de 5 às
09:10:20Z. A segunda foi rodada na 1.3, abaixo.

**Guarda nova, na região pela metade:** o teste passou a afirmar, pelo `DrawAruco` do mapa, que o
marcador de cima de `d1` cabe no corte e o de baixo sai. Antes, só o gabarito acima do corte era
conferido, e a "metade" dependia da posição dos marcadores sem que nada a afirmasse (P13).

**1.3, a prova só objetiva não muda de fora.** Os ajustes de assinatura desta reexecução foram só as
três chamadas de `RegionQrReader.read` em `RegionDetectorInstrumentedTest`, que ganharam o `map`.
Nenhuma asserção mudou.
- `./gradlew :apps:android:testDebugUnitTest --rerun`: 320 testes, 0 falhas, de 09:11:19Z a
  09:11:22Z. São os 312 da linha de base, mais os 7 de `ProvaComDiscursivaNaSessaoTest` e o de
  `MontagemDoAnalisadorTest`.
- `./gradlew :apps:android:connectedDebugAndroidTest`, sem filtro: **89** testes, 0 falhas, 2 pulados
  (os dois do `AcumuloDeInstanciasProbe`), `timestamp` 09:12:25Z. Por classe:
  `CorpusInstrumentedTest` 10, `RegionDetectorInstrumentedTest` 9, `SheetReaderInstrumentedTest` 13,
  `RegiaoDiscursivaInstrumentedTest` 5. É também a reversão rodada da segunda mutação da 1.2. O
  console disse "Finished 91 tests", e o XML diz 89. A âncora é o XML (P3), e a divergência não foi
  investigada.

**O que esta reexecução não verifica:**
- **O documento renderizado é plano.** Uma homografia tirada de um marcador só passaria nestes
  cenários, porque não há perspectiva nem dobra. O que o segundo marcador acrescenta só aparece em
  foto, e esse é o trabalho da 4.2.
- **A geometria da região discursiva não tem conferência além do QR**, até o segundo ajuste da 5b-2.
  Não é mitigado, é conhecido (P8).

## 2. A sessão

**2.1 — a prova com discursiva é reconhecida e explicada, e não apurada.** `ScanSession` decide "prova
com discursiva" por `fully_offline_gradable` do **pacote**, e não pelo quadro (decisão 4). Para essa
prova, `onFrame` monta o estado novo `ScanState.DiscursivaNaoCorrigivel`, com:
- o aluno, pelo QR de qualquer região lida;
- o gabarito, como "lido", "não lido" com o motivo, ou ausente;
- as discursivas reconhecidas e as não lidas, com o motivo;
- o `AVISO` fixo: "A correcao de prova com discursiva ainda nao esta disponivel neste aparelho. Nada
  foi guardado."

`onFrame` **sempre devolve `null`**. QR de outra prova continua recusado com a frase de sempre, e
regiões de alunos diferentes no mesmo quadro são recusadas.

Testes em `ProvaComDiscursivaNaSessaoTest`, 7, na JVM:
- a guarda de vacuidade: a fixture é de fato uma prova com discursiva;
- a folha reconhecida, com o aluno, as regiões e o aviso, sem nota;
- a folha só com `d2`, sem gabarito;
- nada é entregue para gravar, com retomada no meio;
- a sessão abre e procura;
- a folha de outra prova;
- a região não lida ao lado da reconhecida.

7 de 7 e `ScanSessionTest` 19 de 19, às 23:01:06Z. A task filtrada fecha `FAILED` pela guarda contra
comando estreito: 293 métodos sem resultado, e nenhuma falha.

**2.2 — "nada é gravado", visto falhar camada por camada.** A tarefa foi corrigida ao executar (P7). A
mutação original, "o estado novo devolve apuração", não pode ser montada: uma prova com discursiva não
tem `ObjectiveScore`. "Nada é gravado" tem **duas** proteções: a sessão não apura, e o domínio recusa
(8.1 da 5a).

| Mutação | Previsto | Real |
|---|---|---|
| **M-a** — `comDiscursiva = false` (a sessão apura como objetiva) | caem "folha reconhecida", "só `d2`" e "não lida"; "nada é gravado" **verde**, porque o domínio recusa | exatamente esses 3, com 7 testes e 3 falhas |
| **M-c** — `ObjectiveScoring` aceita leitura que é subconjunto do declarado | nada cai: a sessão não chama o domínio para esta prova | 0 falhas |
| **M-a e M-c juntas** | cai também "nada é gravado" | 4 falhas: os 3 de M-a e "nada é entregue para gravar" |

As três foram revertidas pela cópia, `grep -rn MUTACAO` deu 0, e rodado de novo: 7 de 7. **Cada camada
sozinha segura o cenário**, e só as duas desligadas o derrubam.

**2.3 — a tela.** `ScanScreen` ganhou o ramo do estado novo: o aluno pelo roster (`DeQuemE`),
"Prova com discursiva", o gabarito, as discursivas reconhecidas e as não lidas, e o aviso. O build
compila. A tela desenhada **não tem teste automático** (decisão 5), e é conferida no aparelho na 4.3.
Se isso não couber, fica como lacuna.

## 3. A queda

**3.1 — a queda sai por construção, e é vista falhar na montagem.** A montagem do analisador saiu da
`ScanActivity` para `CameraFrameAnalyzer.daSessao(map, deveAnalisar, entrega)`, que a `Activity`
chama e o teste também. O mapa entra inteiro, e o limiar da 3b mora na montagem. O teste é
`MontagemDoAnalisadorTest`, na JVM: ele monta o analisador com o mapa da fixture discursiva, e tem
uma guarda de vacuidade que exige 3 regiões.
- **Verde:** 1 de 1, às 23:03:37Z.
- **Visto falhar:** com `map.also { it.regions.single() }` na montagem (`// MUTACAO`), 1 falha às
  23:03:44Z, com **"java.lang.IllegalArgumentException: List has more than one element."** É a queda
  que a correção P7 da linha `5b` (PR #68) descrevia só por leitura, agora **medida** no ponto em que
  ela acontecia.
- **Reversão:** pela cópia; `grep MUTACAO` deu 0, e rodado de novo, 1 de 1, às 23:03:51Z.
- **`grep` de `regions.single()` em `apps/android/src/main`, fora de linha de comentário:** nenhuma
  ocorrência. As duas que sobram são as KDoc que contam por que a montagem mudou.

A tela que abre a câmera numa prova com discursiva não foi aberta por teste nenhum: a base não tem
teste instrumentado de `Activity` (decisão 5). Ela é conferida no aparelho na 4.3, ou fica como lacuna.

## 4. A folha impressa — movida para a sessão única de papel

**4.1 a 4.3 não foram feitas.** Em 2026-09-26 a impressão não coube, por decisão do mantenedor.

**Movidas no mesmo dia** (`/opsx:update`, `c403f61`) para uma **sessão única de papel antes da fatia
6**. O mantenedor não tem impressora, e cada impressão custa um deslocamento. O texto de 4.1 a 4.3
fica no `tasks.md` como rascunho do protocolo daquela sessão. A linha `5b` do §16 é reagendada para
`6` no archive, e não paga. A questão de ordem abaixo foi encerrada pelo mantenedor: o gabarito fica
com 4 ArUcos, e o gabarito compacto fica fora por ora. O resto desta seção é o estado de antes da
decisão (P7).

A folha de `tok-a` foi gerada nesta sessão, às 09:14:32Z, pelo renderizador web a partir da árvore
mesclada: `build/parity/discursiva-aluno-web.pdf`, 211.851 bytes, 2 páginas, `sha256`
`3e1cce42…a69b`. Ela passou por:
- `fidelidade.mjs` contra `prova-discursiva.aluno.layout.json`: 47 verificações, maior desvio de
  0,047 mm, "fidelidade OK". Isso cobre só a página 0, como a 5a registrou;
- `tinta.mjs`: "tinta OK".

A de 25/09 (15:15:49Z, 211.852 bytes, `sha256` `12df0e2a…c843`) difere em 1 byte e no hash. A causa é
*suposta*, e não conferida: metadado de data do PDF.

**Uma questão de ordem está aberta com o mantenedor.** A ordem de 2026-09-25 punha a mudança de
cabeçalho e gabarito compacto antes da impressão da 4.1. O `tasks.md` espera só a 5b-0 e o
`/opsx:update`. Se o gabarito ainda muda de geometria, as fotos testariam um gabarito que vai ser
descartado. A detecção do marcador de 11,2 mm não depende disso.

**O formato de `fixtures/corpus-5b-marcacoes.json` não está definido** em arquivo nenhum. A proposta
entregue ao mantenedor usa os nomes das questões da fixture e a opção como `QuestionAnswer.Marcada.option`
a expressa: `{ "aluno": "tok-a", "q1": "…", "q2": "…", "q4": "…", "q5": "…" | "em branco" }`.

## Ponto de controle de 2026-09-26, sobre o branch mesclado (não é o fechamento)

É o comando cheio da 5.2, rodado antes da 4.x sobre `d92583c` e as edições de registro que vieram
depois dele, que não tocam código. **Ele não fecha a 5.1 nem a 5.2:** a 4.2 acrescenta teste e
mutação, e o fechamento roda de novo depois dela.

| Comando | Resultado |
|---|---|
| `./gradlew build --rerun-tasks` | `BUILD SUCCESSFUL in 3m`, **183 de 183 tasks executadas**, de 09:22:19Z a 09:25:20Z |
| `./gradlew -p buildSrc test --rerun-tasks` | 1 de 1, 09:25:58Z |
| `./gradlew :apps:android:connectedDebugAndroidTest`, sem filtro, no `platos-atd34` | 89 testes, 0 falhas, 2 pulados, `timestamp` 09:26:55Z |
| `npx vitest run` em `apps/web` | 18 de 18, 09:22:29Z |
| `npm run build` em `apps/web` | `exit 0` |
| `limiar.mjs`, `answer-kind.mjs`, `fio.mjs`, `renderizador.mjs`, `divida.mjs` | todas com `exit 0`; o renderizador sai com "versao 2", e a dívida com "nenhuma linha vencida: 21 linhas lidas" |
| `git grep MUTACAO`, fora de `build/`, `node_modules/`, `docs/`, `openspec/` e `rigorous.md` | nenhuma ocorrência |

Relatórios do `build`, com cada `timestamp` dentro da janela dele:

| Task | Testes | Linha de base (0.1) | `timestamp` |
|---|---|---|---|
| `apps/android` `testDebugUnitTest` | 320 | 312 | 09:23:37Z .. 09:23:42Z |
| `apps/android` `testReleaseUnitTest` | 320 | 312 | 09:24:15Z .. 09:24:23Z |
| `apps/api` `test` | 168 | 168 | 09:24:48Z .. 09:25:03Z |
| `packages/domain` `jvmTest` | 393 | 375 | 09:25:06Z .. 09:25:07Z |
| `packages/domain` `jsNodeTest` | 384 | 366 | 09:25:12Z .. 09:25:14Z |
| `packages/domain` `testAndroidHostTest` | 384 | 366 | 09:25:16Z .. 09:25:17Z |

**De onde vêm as diferenças:**
- **Android, +8:** os 7 de `ProvaComDiscursivaNaSessaoTest` e o de `MontagemDoAnalisadorTest`, desta
  mudança.
- **Domínio, +18 e web, +2:** vieram da 5b-0, pelo merge. O `jvmTest` de 393 é o que a cobertura dela
  registra; os outros números *não* foram conferidos contra a cobertura dela.

**Os passos de paridade e fidelidade do CI não foram rodados aqui.** Eles comparam os dois
renderizadores, e esta mudança não toca renderizador nem fixture (a 5b-0 os fechou na PR #70). O CI da
PR desta mudança os roda.

## 5. Fechamento

Sobre `c403f61`, que só acrescenta registro ao ponto de controle: o código é o mesmo de `d92583c`.

**5.1:** `git grep MUTACAO`, fora de `build/`, `node_modules/`, `docs/`, `openspec/` e `rigorous.md`,
não achou nada (`rc=1`). As seis mutações desta mudança foram revertidas e rodadas: três de 2026-09-24
(seções 1 a 3) e três de 2026-09-26 (seção "1.1 a 1.3 reexecutadas").

**5.2, o comando cheio:**

| Comando | Resultado |
|---|---|
| `./gradlew build --rerun-tasks` | `BUILD SUCCESSFUL in 2m 28s`, **183 de 183 tasks executadas**, de 09:38:17Z a 09:40:46Z |
| `./gradlew -p buildSrc test --rerun-tasks` | 1 de 1, 09:41:15Z |
| `./gradlew :apps:android:connectedDebugAndroidTest`, sem filtro, no `platos-atd34` | 89 testes, 0 falhas, 2 pulados, `timestamp` 09:42:16Z |
| `npx vitest run` e `npm run build` em `apps/web` | 18 de 18, às 09:42:34Z; o build com `exit 0` |
| `limiar.mjs`, `answer-kind.mjs`, `fio.mjs`, `renderizador.mjs` e `divida.mjs` | todas com `exit 0`; a dívida diz "nenhuma linha vencida: 21 linhas lidas" |

| Task | Testes | 0.1 | `timestamp` |
|---|---|---|---|
| `apps/android` `testDebugUnitTest` | 320 | 312 | 09:39:35Z .. 09:39:39Z |
| `apps/android` `testReleaseUnitTest` | 320 | 312 | 09:39:26Z .. 09:39:31Z |
| `apps/api` `test` | 168 | 168 | 09:40:13Z .. 09:40:33Z |
| `packages/domain` `jvmTest` | 393 | 375 | 09:40:40Z .. 09:40:42Z |
| `packages/domain` `jsNodeTest` | 384 | 366 | 09:40:37Z .. 09:40:39Z |
| `packages/domain` `testAndroidHostTest` | 384 | 366 | 09:40:08Z .. 09:40:09Z |

As diferenças para a 0.1 são as do ponto de controle: +8 no Android, desta mudança; +18 no domínio e
+2 no web, que vieram da 5b-0 pelo merge. Os passos de paridade e fidelidade ficam com o CI da PR.

**5.4 — o CI lido no destino.** A PR é a #72. O run `36233666853` rodou sobre `headSha` `25f27f9`,
igual ao `HEAD` local na hora da leitura. Os três jobs deram `success`:
- `build` (4m27s): o teste do `buildSrc`, o build com os testes, e o alvo Android do domínio;
- `web` (41s): o build e as fidelidades, a da prova com discursiva incluída, e a guarda de dívida com
  a verificação de que ela continua capaz de falhar;
- `paridade` (5m14s): "Renderizador e captura no emulador". O passo roda
  `./gradlew :apps:android:connectedDebugAndroidTest` **sem filtro**, num `aosp_atd` API 34
  (`ci.yml:477-488`), seguido da paridade e da fidelidade da prova com discursiva.

A PR #71, o archive da 5b-0, também saiu verde nos três jobs (run `36233663491`).

## O que ainda não foi verificado

- **Atualizado em 2026-09-26:** o papel foi movido para a sessão única antes da 6, e a linha `5b` é
  **reagendada para `6`** no archive. O item abaixo continua verdadeiro, com esse destino.
- **Nenhuma foto da folha discursiva impressa foi lida** (4.1 e 4.2). A linha `5b` do §16 ("a região
  discursiva ainda não passou pelo aparelho nem pelo papel") **não está paga**. Até lá, estes itens
  também não foram medidos:
  - a detecção do marcador de 11,2 mm, que tem regra de parada na 5b-0 (volta a 14 mm);
  - o QR lido depois de uma retificação só por dois ArUcos, que é o risco do passo (a) do ADR-0018 e
    um gatilho de reabertura dele.
- **Os testes de captura usam documento renderizado, que é plano.** Neles, uma homografia tirada de um
  marcador só também passaria.
- **A geometria da região discursiva não tem conferência além do QR**, até o segundo ajuste da 5b-2
  (decisão 1, atualização de 2026-09-26). Não é mitigado, é conhecido (P8).
- **A tela do estado novo** (`ScanScreen`) não tem teste automático, e a 4.3 não foi feita.
- **A abertura da câmera numa prova com discursiva** só foi provada pela montagem do analisador (3.1),
  e não por uma `Activity` aberta.
- **A linha `A folha de teste de impressão não aprova a região discursiva…`** (`5b`, da 5b-0) é
  alcançada por esta mudança e não é paga por ela (proposta, "Linhas do §16").

## Archive (2026-09-26)

Depois do merge das PRs #71 e #72. A reconciliação é a da tarefa 5.5, com a atualização de 2026-09-26.

| Linha do §16 | No archive |
|---|---|
| A região discursiva ainda não passou pelo aparelho nem pelo papel | **reagendada de `5b` para `6`**, e não paga. O motivo, o que a 5b-1 pagou sem papel, o que continua aberto e o custo aceito ao adiar estão na própria linha |
| A folha de teste de impressão não aprova a região discursiva que a prova imprime | **reagendada de `5b` para `6`**. O filtro que travava o veículo dela está na `main`; só a aprovação espera o papel |
| Acurácia em manuscrito; Modo degradado (§10) não existe; O limiar do OMR foi apurado sobre um aparelho e uma impressora (`5`) | em dia até a 6 abrir |
| `antes-de:migration-da-5-em-producao` e `antes-de:implantar-api-da-5a` | não alcançados |

**O custo aceito ao adiar, escrito na linha:** a 5b-2 e a 5c passam a vir antes do papel. É o
"retrofit sobre fato append-only" que a própria linha já descrevia, e ele só se materializa se uma
prova com discursiva for a produção antes da sessão.

**As specs principais foram sincronizadas:**
- `capture-omr`: 1 requisito substituído e 2 novos, de 7 para 9;
- `scan-session`: 1 novo, de 7 para 8.

Cada bloco foi conferido, por script, como presente literalmente na principal, e os requisitos fora
da delta ficaram iguais a `HEAD`. `openspec validate --specs --strict` passou nas 11. A guarda depois
do archive lê as duas linhas com `6`, e diz "nenhuma linha vencida: 21 linhas lidas", `exit 0`.
