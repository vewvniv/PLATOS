# Cobertura — `slice-5b-0-a-regiao-discursiva-compacta`

Leva ao motor de layout os ADR-0016, ADR-0017 e ADR-0018: a região discursiva de dois ArUcos, com o QR
no terceiro canto e a pauta cinza de 7 mm. Proposta, specs, design e tarefas em
`openspec/changes/slice-5b-0-a-regiao-discursiva-compacta/`. Este documento registra **como** cada
verificação foi vista falhar, e não que ela passa (`rigorous.md` §8). As datas são UTC.

## 0. Linha de base, antes do primeiro commit de código

**0.1 — os comandos cheios sobre `main` (`8084cb5`) mais o commit da proposta (`7478e82`), que só
acrescenta arquivos em `openspec/changes/`.** O emulador `platos-atd34` já estava de pé
(`emulator-5554`, `adb emu avd name` → `platos-atd34`); nada no ambiente foi mudado.

| Comando | Resultado | Âncora |
|---|---|---|
| `npx vitest run` em `apps/web` | 16 de 16, 2 arquivos, `exit 0` | "Start at 16:10:55" no relógio local (UTC+2), 14:10:55Z |
| `./gradlew build --rerun-tasks` | `BUILD SUCCESSFUL in 3m 49s`, **183 de 183 tasks executadas**; 14:10:11Z–14:14:01Z | relatórios de 14:13:09Z a 14:13:58Z, abaixo |
| `./gradlew -p buildSrc test --rerun-tasks` | `BUILD SUCCESSFUL in 27s`, 6 de 6 executadas | relatório de 14:14:23Z |
| `./gradlew :apps:android:connectedDebugAndroidTest`, sem filtro, no `platos-atd34` | `BUILD SUCCESSFUL in 1m 1s`; 84 testes em 17 suítes, 0 falhas, 2 pulados; 7 tasks executadas e 81 `UP-TO-DATE`, porque a compilação acabara de rodar no `build` | `timestamp` 2026-09-25T14:15:28 no relatório; o comando terminou às 14:15:30Z |

Os relatórios, somados por diretório de task por um script no scratchpad (`relatorios.mjs`), que lê o
`timestamp` de cada XML e marca como **VELHO** o anterior ao início desta sessão (14:10:11Z). Nenhum
saiu marcado:

| Task | Testes | Falhas | `timestamp` |
|---|---|---|---|
| `apps/android` `testDebugUnitTest` | 312 | 0 | 14:13:16Z .. 14:13:20Z |
| `apps/android` `testReleaseUnitTest` | 312 | 0 | 14:13:09Z .. 14:13:12Z |
| `apps/android` `connectedDebugAndroidTest` | 84 (2 pulados) | 0 | 14:15:28Z |
| `apps/api` `test` | 168 | 0 | 14:13:22Z .. 14:13:41Z |
| `packages/domain` `jsNodeTest` | 366 | 0 | 14:13:50Z .. 14:13:51Z |
| `packages/domain` `jvmTest` | 375 | 0 | 14:13:53Z .. 14:13:55Z |
| `packages/domain` `testAndroidHostTest` | 366 | 0 | 14:13:56Z .. 14:13:58Z |
| `buildSrc` `test` | 1 | 0 | 14:14:23Z |

A soma dos oito é 1984.

**0.2 — a guarda do registro de dívida, antes de qualquer edição.** `node tools/divida/divida.mjs`,
`exit 0`:

```
fatia corrente: 5b, de slice-5b-0-a-regiao-discursiva-compacta (ativa)
eventos declarados: nenhum
...
vence nesta fatia (5b):
  Acurácia em manuscrito (`5`)
  Modo degradado (§10) não existe (`5`)
  O limiar do OMR foi apurado sobre um aparelho e uma impressora (`5`)
  A região discursiva ainda não passou pelo aparelho nem pelo papel (`5b`)

nenhuma linha vencida: 20 linhas lidas
```

**0.3 — a âncora da guarda da decisão 8.** O `sha256` dos bytes, calculado pelo `crypto` do Node (P4),
e não por serialização em Kotlin, lidos sobre `7478e82` com `git status --short fixtures/` vazio:

| Arquivo | Bytes | `sha256` |
|---|---|---|
| `prova-referencia.layout.json` | 91.960 | `8c9756a9db45c1d08a97fd0d99f6edcb353338f78894ff5b26438d1e00078451` |
| `prova-referencia.package.json` | 104.091 | `ff2b94ef600101e2c20d5b6b298f7d0612ee0a66beb4d74d7dcd954cfbde40da` |
| `prova-referencia.turma.package.json` | 107.282 | `7282a186d3b644dac6108e7ea39931517c5a0614e8fa570b15ad09324cb14df7` |
| `prova-2.package.json` | 104.108 | `c2098e10e6c70f93a469ce56ff5f0e5796b44792da0b661e01465904a48cb717` |
| `folha-de-teste.layout.json` | 6.743 | `d9f7b08c17a706b02ba21355e2286b71d09792605b1c22adc8b7b95ce566c221` |

## A lacuna do plano achada ao aplicar: a guarda da versão do renderizador

Antes da primeira linha de código, a leitura de `tools/parity/renderizador.mjs` mostrou que ele lê
`LayoutMap.MIN_RENDERER_VERSION` e exige igualdade com os dois renderizadores. A decisão 4 tira essa
constante e sobe os renderizadores a 2, e nenhuma tarefa cobria a guarda: o CI da 7.4 sairia
vermelho. O mantenedor decidiu ajustá-la nesta mudança. A decisão 4 ganhou um parágrafo de
atualização, e a tarefa 4.3 foi acrescentada, com o motivo escrito nela.

## 1. A linha nova no §16 (tarefa 1.1)

A linha "A folha de teste de impressão não aprova a região discursiva que a prova imprime" entrou com
token `5b`, antes de qualquer código (P27). Os três testes instrumentados que ela nomeia foram
conferidos por `grep` em `apps/android/src/androidTest` nesta sessão.

- **Na árvore real:** a guarda a lista `em dia`, lê 21 linhas e sai com `0`. Ela aparece em "vence
  nesta fatia (5b)", ao lado das quatro de antes.
- **Vista falhar:** com `--mudancas` apontando para uma cópia de `openspec/changes/` no scratchpad,
  acrescida de um diretório vazio `slice-5c-sonda`, a guarda deriva "fatia corrente: 5c" e sai com
  `1`. As linhas nomeadas são **as duas** `5b`, e só elas:
  - `::error::linha vencida sem reconciliacao: A região discursiva ainda não passou pelo aparelho nem
    pelo papel (\`5b\`): a fatia 5b ja passou, e a corrente e 5c`;
  - `::error::linha vencida sem reconciliacao: A folha de teste de impressão não aprova a região
    discursiva que a prova imprime (\`5b\`): a fatia 5b ja passou, e a corrente e 5c`.

  As três linhas `5` aparecem em "vence nesta fatia (5c)", e não como vencidas, o que está certo:
  `5` vence na 6.
- **A cópia foi apagada**, e a guarda sobre a árvore real voltou a sair com `0`.

## 2. O contrato, sozinho (tarefa 2.1)

O commit acrescenta `AnswerWidth` (`column`, `page`), `Question.answerLines` (`answer_lines`) e
`Question.answerWidth` (`answer_width`), os dois nulos por padrão, e `DrawLine` (`"line"`: `x1`, `y1`,
`x2`, `y2`, `stroke`, `tone`) no domínio e no espelho `apps/web/src/layoutMap.ts`. O ramo `DrawLine`
do renderizador Android lança `UnknownPrimitiveException`; o do web cai no `default`, que recusa.

| | Previsto antes de rodar | Real |
|---|---|---|
| Testes | nenhum cai | a **compilação** dos testes do domínio caiu antes de qualquer teste rodar, nos três alvos: `InkBoxes.kt:19:79 'when' expression must be exhaustive. Add the 'is DrawLine' branch` |
| Goldens | nenhum muda | nenhum mudou |

**A previsão errou, e o erro é da lista de consumidores, não do código** (P12). `InkBoxes.kt` é o
ajudante de teste que calcula a caixa de tinta de cada primitiva para as guardas de zona de silêncio,
e tem um `when` exaustivo sobre `Primitive`. A tarefa só listava o renderizador Android. Ele ganhou o
ramo da linha: os extremos crescidos de meio traço nos dois eixos, generosos como a caixa do texto.
Esse ramo é o que a guarda "Zona de silêncio preservada" vai usar para a pauta, na 3.2.

Depois do ramo, com o previsto valendo:

- `./gradlew :packages:domain:allTests …`, 14:19:11Z–14:19:31Z, `exit 0`: `testAndroidHostTest` 366,
  `jsNodeTest` 366, `jvmTest` 375, 0 falhas, `timestamp` de 14:19:19Z a 14:19:28Z. As contagens são as
  da linha de base.
- A compilação do Android (`compileDebugKotlin`, `compileDebugUnitTestKotlin`,
  `compileDebugAndroidTestKotlin`) e da API (`compileKotlin`, `compileTestKotlin`) com
  `--rerun-tasks`: **50 de 50 tasks executadas**, `BUILD SUCCESSFUL`. A primeira rodada as deu como
  `UP-TO-DATE`, porque tinham compilado na tentativa que caiu nos testes; e `--rerun` só vale para a
  task imediatamente anterior a ele, então foi preciso `--rerun-tasks` para vê-las executar.
- `npx tsc --noEmit` em `apps/web`, `exit 0`; `npx vitest run`, 16 de 16, "Start at 16:19:59"
  (14:19:59Z).
- Os cinco `sha256` da 0.3, recalculados pelo `crypto` do Node: **iguais** os cinco.

## 3. O motor

### 3.1 — as recusas novas da entrada

`requireSupported` passa a recusar a discursiva sem número de linhas (ou com menos de 1), sem largura,
e de largura `page`, e a objetiva que declara qualquer um dos dois. Os quatro testes do domínio que
montam discursiva passam a declarar `answer_lines` e `answer_width: column`: `ExamDefinitionTest`,
`PacoteDiscursivoTest`, `LayoutEngineTest` e `RegiaoDiscursivaTest`. Neste último, a definição declara
a **mesma soma** dos `expected_lines`, porque o motor ainda os lê até a 3.2.

As mensagens e as KDocs que diziam "a moldura é dimensionada pelos `expected_lines` da rubrica (D35)"
passaram a ser falsas com esta mudança, e foram corrigidas junto: a da discursiva sem rubrica, a do
critério com `expected_lines` < 1, a de `RubricCriterion` e a de `requireSupported`. Nenhum teste
conferia esse trecho das mensagens.

**A definição da fixture foi editada aqui, e não na 5.1.** Com a recusa nova, o `GoldenLayoutTest` da
discursiva e o `ExamPublicationTest` da API, que leem `fixtures/prova-discursiva.json`, cairiam neste
commit. `d1` ganhou `answer_lines: 5` e `d2`, `answer_lines: 7`, as duas com `answer_width: column`.
São as somas dos `expected_lines` de hoje, e o motor ainda as calcula da rubrica: nenhum golden mudou,
e o `GoldenLayoutTest` da discursiva passou byte a byte.

**Um teste por cenário novo, e cada um confere a mensagem**, com a questão e o motivo:

| Cenário | Teste | O que a asserção confere |
|---|---|---|
| Discursiva sem número de linhas | `discursiva sem numero de linhas e recusada, mesmo com expected_lines na rubrica` | `q2` e "nao declara o numero de linhas"; e, com `0`, "declara 0 linha(s)". O teste afirma antes que a rubrica tem `expected_lines` positivos |
| Discursiva sem largura | `discursiva sem largura e recusada` | `q2` e "nao declara a largura" |
| Largura de página ainda é recusada | `largura de pagina ainda e recusada, e a mensagem diz que depende da paginacao em faixas` | `q2`, "largura \`page\`" e "paginacao em faixas" |
| Objetiva com escolha da discursiva | `objetiva com numero de linhas ou largura e recusada` | `q1` e "objetiva e declara numero de linhas (3)"; e "objetiva e declara largura (column)" |

`./gradlew :packages:domain:allTests`, 14:23:58Z, `exit 0`: `jvmTest` 379, `jsNodeTest` 370,
`testAndroidHostTest` 370, 0 falhas, `timestamp` de 14:24:10Z a 14:24:19Z. São quatro a mais por alvo.

`ExamPublicationTest` da API, com a fixture nova: 10 de 10, `timestamp` 14:25:18Z. O `exit 1` daquele
comando é a guarda do build (`592aa88`) que reprova execução filtrada por `--tests`, e não falha de
teste; o comando cheio fica para a 7.2.

**Visto falhar.** Previsto: sem a checagem de largura, cai **só** "Discursiva sem largura".

| Mutação | Previsto | Real |
|---|---|---|
| `if (false && answerWidth == null)`, com `// MUTACAO` acima | cai só `discursiva sem largura e recusada` | `jvmTest` inteiro: **1 de 379**, esse. A mensagem: "Expected an exception of class …UnsupportedContentException to be thrown, but was completed successfully" — a definição passou |

Revertida, `grep -c MUTACAO` no arquivo deu `0`, e a reversão foi rodada: `jvmTest`, 379 de 379,
`timestamp` de 14:25:00Z a 14:25:01Z. Os cinco `sha256` da 0.3 continuam iguais.

### 3.2 — a região nova

`EssayGeometry` ganha as constantes da decisão 1 (marcador de 11,2 mm em módulos de 1,6 mm, zona de
silêncio do QR de 2 mm, folga da escrita de 2 mm, pauta de 7 mm, e a pauta provisória a 300‰ com
0,2 mm). `QuestionBlocks` lê `answer_lines`, e não mais a rubrica. `emitEssayRegion` emite o `4k` no
canto superior esquerdo, o `4k+3` no inferior direito, o QR no canto superior direito, a moldura na
largura inteira e a pauta como `DrawLine`. O retângulo de referência é o externo dos dois marcadores,
e a `answer_area` vai da base do QR até a zona de silêncio do `4k+3`.

**A validação entrou junto, só no que o motor novo exige.** A regra "toda região tem os quatro
marcadores `4k..4k+3`" faria o mapa do motor ser inválido. Por isso a região discursiva passou, neste
commit, a exigir exatamente `[4k, 4k+3]`, e o gabarito e a folha de teste continuam com os quatro. O
resto da 3.4 (o marcador declarado existir na página, a linha com tom) e os testes dela vêm no commit
dela.

**O vermelho previsto, antes de rodar:** cai **só** `GoldenLayoutTest` › "mapa da prova com
discursiva bate byte a byte com o golden", nos três alvos. A golden é regravada na 5.2, depois de os
renderizadores desenharem `line`, e até lá fica vermelha. **Real:** esse, e só esse, nos três alvos —
`jvmTest` 1 de 386, `jsNodeTest` 1 de 377, `testAndroidHostTest` 1 de 377, às 14:31:16Z.

**Um teste por cenário**, em `RegiaoDiscursivaTest`:

| Cenário | Teste |
|---|---|
| Dois marcadores na diagonal e o QR no terceiro canto | `dois marcadores na diagonal e o QR no terceiro canto` — os cantos externos dos dois marcadores são os do retângulo de referência; o QR encosta na borda direita com o topo na altura do `4k`; o QR declarado vai até `u = 1` |
| Identificadores dos marcadores da região discursiva | `cada discursiva tem a sua regiao, com os marcadores 4k e 4k+3` — `[4, 7]` e `[8, 11]`, e os desenhados são esses e só esses |
| Marcador discursivo dimensionado com folga | `marcador discursivo continua com 10 mm mesmo reduzido 5 por cento` — e o lado desenhado é a constante, com 7 × 7 módulos |
| Coordenadas dentro da faixa normalizada | `moldura, pauta, QR e area de resposta ficam dentro do retangulo de referencia` |
| Zona de silêncio preservada | `nenhuma tinta invade a zona de silencio dos marcadores discursivos` — zona de um módulo do próprio marcador; quatro paginações diferentes, com guarda de vacuidade que exige marcador nas **duas** colunas |
| O professor dimensiona a moldura | `o professor dimensiona a moldura pelo numero de linhas` — 5 contra 8 linhas, mesma rubrica: a moldura cresce 21 mm, e marcador de cima, QR e topo da moldura não se movem em relação ao topo |
| A rubrica não mexe na moldura | `a rubrica nao mexe na moldura` — `expected_lines` 3+2 contra 6+3, mesmas linhas: JSON canônico idêntico |
| Moldura maior que a coluna | `moldura maior que a coluna e recusada, nomeando a questao` |
| Área de resposta com folga fora da moldura | `a area de resposta contem a moldura, com folga acima e abaixo dela` — e começa na base do QR e termina na zona de silêncio do `4k+3` |
| Pauta abaixo do teto decorativo | `a pauta e linha cinza de 7 mm abaixo do teto decorativo, e a moldura e preta` |
| Enunciado e moldura não se separam | `enunciado e moldura ficam juntos onde quer que o paginador os ponha` |
| O enunciado fica fora da moldura | `nenhum texto do enunciado cai dentro da regiao` |

O teste da 5a "a rubrica dimensiona a moldura, e so ela" saiu: o cenário dele foi removido da spec
(REMOVED), e os dois cenários da moldura acima o substituem.

**O ajudante `discursiva(...)` separa linhas declaradas de `expected_lines`, e por padrão os iguala.**
Só os dois cenários da moldura os separam. Ao preparar a mutação B, a leitura mostrou que o teste da
pauta usava 6 linhas com a rubrica padrão (Σ 5): a mutação o derrubaria também, pelo número de traços
da pauta, e o conjunto real deixaria de ser o previsto por um motivo que não é o da camada. Os quatro
testes que variam as linhas passaram a declarar rubrica de mesma soma, **antes** de a mutação rodar.

**Visto falhar**, com dois conjuntos disjuntos previstos. Cada mutação com `// MUTACAO` acima, no
`jvmTest` inteiro; a golden da discursiva já estava vermelha e continua:

| Mutação | Previsto, além da golden | Real |
|---|---|---|
| A — QR centrado, como na 5a (`qrX = left + (width - qrSide) / 2`) | só `dois marcadores na diagonal e o QR no terceiro canto` | 2 de 386: a golden e esse. "o QR da regiao 1 nao encosta na borda direita ==> expected: <102000> but was: <65500>" |
| B — moldura lida de Σ `expected_lines`, como na 5a | só `o professor dimensiona a moldura pelo numero de linhas` e `a rubrica nao mexe na moldura` | 3 de 386: a golden e esses dois. "a altura da moldura nao seguiu o numero de linhas declarado ==> expected: <21000> but was: <0>"; e os dois JSON canônicos diferentes |

A reversão de A foi observada na rodada de B (o teste da diagonal passou nela). Depois de reverter B,
`grep MUTACAO` em `packages/`, `apps/web/src`, `apps/android/src` e `tools/` deu `0`, e
`./gradlew :packages:domain:allTests` rodou às 14:33Z: 1 de 386, 1 de 377 e 1 de 377, a golden da
discursiva em cada alvo, como previsto.

### 3.3 — a versão mínima de renderizador, por mapa

`LayoutMap.MIN_RENDERER_VERSION` saiu. No lugar, ao lado das primitivas: `BASE_RENDERER_VERSION = 1`,
`LINE_RENDERER_VERSION = 2` e `minRendererVersionOf(pages)`, a única função da regra, chamada pelo
motor e pela folha de teste (decisão 4 e a atualização dela). Dois testes, em `RegiaoDiscursivaTest`:
`mapa com pauta exige o renderizador que desenha linha` (a prova tem `line` e declara 2) e `mapa sem
linha continua exigindo a versao 1` (a prova objetiva não tem `line` e declara 1).

`./gradlew :packages:domain:allTests`, 14:35:01Z: só a golden da discursiva, vermelha desde a 3.2.

**Visto falhar: a função devolvendo sempre 2.**

- **Previsto na tarefa:** "cai só o segundo" dos dois cenários novos.
- **Previsto corrigido, antes de rodar,** pela leitura dos testes que fixam a versão 1 na prova
  objetiva. Caem também os pinos que já existiam: `LayoutEngineTest` › `mapa declara as duas versoes`,
  `GoldenLayoutTest` › `mapa da fixture bate byte a byte com o golden`, `LayoutProfileTest` › `perfil
  padrao produz exatamente o mapa de antes de o perfil existir`, `PacoteVersionadoTest` › `o pacote
  versionado e o que a publicacao produz hoje` e `o pacote da turma e o que a publicacao produz hoje`,
  `ExamPackageTest` › `o hash da fixture e o mesmo nos tres alvos` e `PrintTestSheetTest` › `folha de
  teste bate byte a byte com a versionada`.
- **Real:** 9 de 388 no `jvmTest`: a golden da discursiva, que já estava vermelha, e exatamente os
  oito previstos. `mapa com pauta exige o renderizador que desenha linha` ficou verde.

**O que isso significa.** A previsão da tarefa só olhava os dois cenários novos, e entre eles ela
vale: só o segundo cai. Os outros sete são guardas independentes da mesma propriedade, e é a decisão 8
vista pelo lado dos testes: a versão global em 2 muda os bytes da prova objetiva, e tudo o que fixa
esses bytes reage.

**A guarda da decisão 8 também pega.** Com a mutação na árvore, o `GoldenWriterTest` regravou os
artefatos, e `prova-referencia.layout.json` foi copiado para o scratchpad: `sha256`
`e1582b603452fa97154656fc364a640fc267b6600f7922b73ae1c19395205115`, 91.960 bytes, com
`"min_renderer_version":2`, contra o `8c9756a9…` da 0.3. Os arquivos que o writer sobrescreveu em
`fixtures/` voltaram ao `HEAD` com `git checkout -- fixtures/`, e os cinco `sha256` da 0.3 foram
conferidos **iguais** logo depois.

A mutação foi revertida, `grep MUTACAO` deu `0`, e a reversão foi rodada: `allTests` às 14:36Z, só a
golden da discursiva em cada alvo (1 de 388, 1 de 379, 1 de 379).

**A guarda da versão do renderizador, sobre esta árvore**, ainda lendo o registro antigo: `node
tools/parity/renderizador.mjs` sai com `2` e nomeia o registro `dominio` — "zero declaracoes de
\`MIN_RENDERER_VERSION\` — a constante mudou de forma ou saiu daqui". É o "ver falhar" da 4.3,
observado aqui porque a constante saiu neste commit; a 4.3 a faz ler o registro novo.

### 3.4 — a validação

*(Previsão da mutação M2, escrita aqui antes de ela rodar; o resultado vem abaixo.)*

**M2 — o teto de 80‰ aplicado também à linha.** A tarefa previa "cai **só** 'Linha cinza não é
trama'". A leitura dos testes, antes de rodar, diz que não: o mapa válido do motor tem a pauta a
300‰, e sob M2 as linhas **não tocadas** de cada teste passam a gerar o problema de 80‰. Então cai
todo teste que valida um mapa discursivo do motor e espera `Valid` ou uma lista exata de problemas.
Previsto corrigido: em `RegiaoDiscursivaTest`, os onze — `o mapa com discursivas que o motor produz e
valido, e a validacao nao o altera`, `area de resposta sobre o QR e recusada`, `area de resposta fora
do quadrilatero e recusada`, `duas regioes para a mesma questao e recusado`, `QR declarado que nao
existe na pagina e recusado`, `regiao discursiva sem questao e recusada`, e os cinco da 3.4. Além da
golden da discursiva, já vermelha. E, lida a mensagem, `pauta sem tom` deve cair **com** o problema
de tom ausente ainda na lista: é isso que mostra a camada de M1 intacta sob M2.

**O que a validação passou a conferir.** A regra de marcadores por tipo (`[4k, 4k+3]` na discursiva,
os quatro no gabarito e na folha de teste) entrou no commit da 3.2. Neste, a região discursiva passa
a exigir que cada marcador declarado exista como `DrawAruco` na página dela, e a linha que alcança a
área de resposta passa a exigir tom declarado e **abaixo** do teto decorativo da região. O tom de
`DrawLine` ganha a conferência de faixa 0 a 1000, como o do texto; o teto de 80‰ continua só para
preenchimento.

**Um teste por cenário**, cada um partindo do mapa válido do motor e mudando uma coisa, e cada
asserção conferindo a lista exata de problemas:

| Cenário | Teste | O que muda | Problema conferido |
|---|---|---|---|
| Região discursiva com os marcadores errados | `regiao discursiva com os quatro marcadores, ou com outro par, e recusada` | a região 1 declara `[4, 5, 6, 7]`; e, à parte, `[8, 11]` | com os quatro, três problemas: a regra por tipo e os marcadores 5 e 6, que não são impressos; com `[8, 11]`, desenhados na mesma página, só a regra por tipo |
| Marcador declarado que não existe | `marcador discursivo declarado que nao esta desenhado e recusado` | o `r1-m7` sai das primitivas | "regiao 1 declara o marcador 7, que nao esta entre as primitivas da pagina 0" |
| Pauta preta é recusada | `pauta sem tom e recusada, nomeando a regiao e a linha` | `r1-p2` sem tom | a região, a linha e "nao declara tom" |
| Pauta acima do teto é recusada | `pauta acima do teto decorativo e recusada, com o valor` | `r1-p2` a 600‰ | a região, a linha, o tom 600 e o teto 500 |
| Linha cinza não é trama | `linha cinza acima de 8 por cento e abaixo do teto decorativo e aceita` | `r1-p2` a 450‰ | `Valid` |

`./gradlew :packages:domain:allTests`, 14:39:45Z: +5 por alvo, e só a golden da discursiva vermelha.

**Visto falhar**, no `jvmTest` inteiro, com `// MUTACAO` acima de cada mutação:

| Mutação | Previsto | Real |
|---|---|---|
| M1 — sem a checagem de tom da linha (`if (false) checkPauta(…)`) | caem só `pauta sem tom` e `pauta acima do teto` | 3 de 393: a golden e esses dois, os dois com "esperava mapa invalido" |
| M2 — o teto de 80‰ aplicado também à linha | na tarefa: só `linha cinza`; **corrigido antes de rodar** (acima): os onze de `RegiaoDiscursivaTest` | 12 de 393: a golden e exatamente os onze |

**O que M2 significa.** Os conjuntos **não** são disjuntos como a tarefa previa: M1 ⊂ M2. A razão é
a composição da fixture, e não uma camada vazando para outra. O mapa válido do motor tem oito linhas de
pauta a 300‰, e sob M2 as linhas que o teste **não** tocou passam a gerar o problema de 80‰; toda
asserção de lista exata reage. A independência das duas camadas se lê pela mensagem, e não pela
contagem:

- sob M1, `linha cinza` continua verde;
- sob M2, `pauta sem tom` cai com a lista contendo, além dos sete problemas de 80‰ das outras linhas,
  **o problema de tom ausente** do `r1-p2`. A camada que M1 removeria continua funcionando sob M2;
- sob M2, `linha cinza` cai com oito problemas "trama de `r…-p…` acima do teto de 80 por mil",
  inclusive "`r1-p2` … 450".

M2 revertida, `grep MUTACAO` deu `0`, e a reversão foi rodada: `allTests` às 14:41Z, só a golden da
discursiva em cada alvo (1 de 393, 1 de 384, 1 de 384).

## 4. Os renderizadores

### 4.1 — o web

`renderer.ts` desenha `line` com `drawLine` do `pdf-lib`, entre os dois pontos, com a espessura e o
cinza do tom, e `RENDERER_VERSION = 2`. **O arremate reto não é um operador escrito:**
`LineCapStyle.Butt` vale `0`, o `pdf-lib` só emite o operador de arremate quando ele é verdadeiro, e
o estado inicial do PDF já é o reto. Conferido lendo `operations.js` do `pdf-lib` e o fluxo gerado,
que é `q / 0.7 0.7 0.7 RG / 0.5669… w / [] 0 d / … m / … l / S / Q`, sem `J`.

Dois testes novos no Vitest, e o ajudante `contentOps` passou a recolher também os fluxos que só têm
cor de traço (`RG`), que é o caso de uma página só com linhas:
- `desenha a linha entre os dois pontos, com o traco e o tom declarados, sem arremate`: uma linha a
  300‰ e uma sem tom; o fluxo tem `0.7 0.7 0.7 RG` e `0 0 0 RG`, as duas larguras iguais a
  `umToPt(200)`, os pontos declarados com o eixo invertido, dois `S`, e nenhum `1 J` nem `2 J`;
- `recusa o mapa que exige a versao seguinte a esta, e desenha o que exige esta`: um mapa que exige 3
  é recusado com `RendererVersionError`, e um que exige 2 é desenhado.

`npx tsc --noEmit` limpo; `npx vitest run`, 18 de 18, "Start at 16:43:26" (14:43:26Z).

**Visto falhar**, mesmo sem a tarefa pedir (P9): com o ramo `line` desenhando sempre em preto, cai
**só** o teste do desenho da linha, 1 de 18: "expected 'q\n0 0 0 RG\n0.5669291338582677 w\n[]…' to
match /0\.7 0\.7 0\.7 RG/". Revertido, `grep -c MUTACAO` `0`, e rodado: 18 de 18, 14:43:42Z.

**O cenário "Renderizador anterior recusa mapa com linha" fica coberto por composição, e não por um
teste próprio** (P16). Ele se apoia em duas coisas: o motor declara 2 para mapa com linha (3.3, visto
falhar), e a guarda de versão de cada renderizador recusa mapa acima da própria versão (os testes que
já existiam, `recusa imprimir quando o mapa exige renderizador mais novo` no web e o de
`RendererContractTest` no Android). Nenhum renderizador na versão 1 existe fora do repositório para
ser testado.

### 4.2 — o Android (código; a verificação instrumentada fica para a 5.4)

`LayoutMapRenderer` desenha `DrawLine` com `canvas.drawLine`, `Paint` de traço na espessura do mapa,
o cinza do tom e `Cap.BUTT`, escrito mesmo sendo o padrão. `RendererContract.RENDERER_VERSION = 2`.
`./gradlew :apps:android:testDebugUnitTest :apps:android:compileDebugAndroidTestKotlin`, 14:44:38Z,
`exit 0`: 312 de 312, com `RendererContractTest` 9 de 9 (`timestamp` 14:44:50Z).

**A tarefa fica aberta aqui.** A outra metade da verificação é o `LayoutMapRendererInstrumentedTest`
gerar `android-discursiva.pdf` sem exceção, e ele lê `fixtures/prova-discursiva.package.json`, que
só passa a ter `line` depois da regravação da 5.2. Rodá-lo agora provaria o desenho de um pacote sem
linha nenhuma.

### 4.3 — a guarda da versão do renderizador lê o registro novo

`tools/parity/renderizador.mjs` passa a ler `LayoutMap.LINE_RENDERER_VERSION` como o registro
`dominio`, com o papel "a versao mais alta que o motor pode exigir num mapa", e a KDoc dela registra
por que o registro mudou de nome. O comentário do passo do CI acompanha.

**Visto falhar, antes da edição:** sobre a árvore com os três registros em 2 (depois do commit da
4.2), a guarda ainda lendo `MIN_RENDERER_VERSION` saiu com `2`: "nao consegui ler o registro dominio
(LayoutMap.MIN_RENDERER_VERSION) em …/LayoutMap.kt: zero declaracoes de \`MIN_RENDERER_VERSION\` — a
constante mudou de forma ou saiu daqui".

**Depois da edição:**
- `node tools/parity/renderizador.mjs`: "os tres registros concordam: versao 2", `exit 0`;
- o passo "A verificacao da versao do renderizador continua capaz de falhar", copiado do `ci.yml` e
  rodado localmente: `--divergir dominio`, `android` e `web` saem cada um com `1`, e cada um nomeia
  **só** os dois pares que contêm o registro forçado — por exemplo, `--divergir dominio`: "os
  registros dominio e android discordam: dominio diz 3, android diz 2" e "os registros dominio e web
  discordam: dominio diz 3, web diz 2", e nada de "android e web".
