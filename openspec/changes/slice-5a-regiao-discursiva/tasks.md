## 0. Antes do primeiro commit

- [x] 0.1 Medir a linha de base na árvore de `main` (`97aa59f`):
  - `./gradlew build --rerun-tasks` e `./gradlew :apps:android:connectedDebugAndroidTest`, sem
    filtro;
  - `npm test` em `apps/web`.

  Anotar em `docs/cobertura-slice-5a-regiao-discursiva.md` a contagem de testes por suíte, o
  `timestamp` de cada relatório e quantas tasks foram **executadas** (P2, P3). Verificação: os
  relatórios existem, e o `timestamp` é desta sessão.
- [x] 0.2 Registrar a saída de `node tools/divida/divida.mjs`: "fatia corrente: 5a", e as três linhas
  `5` sob "vence nesta fatia". Verificação: a saída colada na cobertura, com `exit 0`.

## 1. O registro, antes do código (P27)

- [x] 1.1 Acrescentar ao §16 de `docs/architecture/ARQUITETURA-FINAL-v3.md` a linha "A região
  discursiva ainda não passou pelo aparelho nem pelo papel", com token `5b`, dono mantenedor e as duas
  partes da decisão 12 do design:
  - o motivo errado da recusa no aparelho;
  - o caminho do `RegionDetector` não exercitado;
  - a folha discursiva não medida em papel.

  Verificação: `divida.mjs` a lista como `em dia`. **Ver falhar:** com `--mudancas` apontando para uma
  cópia de `openspec/changes/` no scratchpad, acrescida de `slice-5c-sonda`, a guarda sai com `1` e
  nomeia **só** esta linha. Depois, rodar de novo sobre a árvore real e ver `exit 0`.

## 2. O contrato, sozinho (decisão 11)

- [x] 2.1 Congelar `fixtures/prova-referencia.package.json` como
  `fixtures/pacote-antes-da-discursiva.json`, byte a byte. O motivo fica escrito na KDoc do
  `GoldenWriterTest`, ao lado da do `pacote-do-contrato-anterior.json`, e não num `README` em
  `fixtures/`. Verificação: `cmp` entre os dois arquivos, e `sha256` dos bytes igual ao hash que
  `ExamPackageTest` e `ExamPublicationTest` fixam para a prova de referência.

  *Corrigido ao executar (P7), em dois pontos. A redação original pedia um `README` ao lado, mas a
  ETAPA 3 documentou a fixture congelada dela na KDoc do `GoldenWriterTest`, e esta segue a casa. E
  pedia comparar com "o `content_hash` declarado dentro dele", que não existe: o pacote não declara o
  próprio hash, e nem poderia. A âncora certa é o literal dos testes.*
- [x] 2.2 Commit só de contrato, em `packages/domain`:
  - `Question`: `rubric` e `answerCaptureMode`;
  - `Rubric`, `RubricCriterion` e `RubricDescriptor`;
  - `PackageItem`: `kind`, `rubric` e `answer_capture_mode`;
  - `ScannableRegion`: `question_id`, `answer_area` e `qr_id`;
  - `PackageAssignment.qr` → `qrs: List<RegionQr>`.

  Nenhum consumidor muda além do mínimo para compilar. Verificação: `./gradlew :packages:domain:jvmTest`
  compila e **fica vermelho** só nos testes que comparam hash ou golden. Anotar na cobertura a lista
  **prevista** antes de rodar e a **real** ao lado (P12). A mensagem do commit diz que o vermelho é
  esperado e qual é.

## 3. O motor

- [x] 3.1 `requireSupported` aceita discursiva com rubrica e recusa os oito casos da spec. Verificação:
  um teste em `ExamDefinitionTest` por cenário de "Recusa de entrada não suportada". Cada teste confere
  a **mensagem** (a questão e o motivo), e não só que houve exceção (P9). O teste antigo "questão
  discursiva é recusada" é **reescrito** para o caso sem rubrica, e o commit diz isso (P12).
- [x] 3.2 O gabarito emite bolhas só das objetivas, numeradas pela posição na prova (decisão 7).
  Verificação: um teste com a discursiva na posição 3 afirma as linhas 1, 2, 4 e 5, e nenhuma bolha
  da 3. **Ver falhar:** com a numeração trocada para contígua, o teste cai. Reverter e rodar.
- [x] 3.3 O bloco discursivo e a região:
  - enunciado fora da moldura;
  - área de resposta com altura igual a Σ `expected_lines` × 8,6 mm, e o bloco na grade de 3 mm;
  - marcadores e QR de 14 mm (decisão 5), com IDs `4k…4k+3`;
  - QR com o payload da região;
  - `answer_area`, `question_id` e `qr_id` preenchidos;
  - a pauta na forma (a) da decisão 4, provisória até a 6.3.

  Verificação: um teste por cenário das duas ADDED de `layout-engine` e do cenário de identificadores
  da região discursiva. **Ver falhar**, com duas mutações de conjuntos disjuntos previstos:
  - moldura de altura fixa: cai só "A rubrica dimensiona a moldura";
  - IDs `4k+4`: cai só o de identificadores, e a validação.
- [x] 3.4 `LayoutMapValidation` recusa os quatro casos da ADDED "A região discursiva declara a questão e
  a área de resposta". Verificação: cada teste parte de um mapa **válido** produzido pelo motor e muda
  **uma** coisa, e a asserção confere a mensagem (`rigorous.md` §3). O teste "Mapa válido" passa sobre
  o mapa da fixture discursiva.
- [x] 3.5 Determinismo: o mapa da fixture discursiva recalculado é idêntico, e o cálculo nos alvos que
  o `GoldenLayoutTest` já compara bate byte a byte com a golden. Verificação: `GoldenLayoutTest` com a
  fixture nova, em todos os alvos que ele roda hoje.

## 4. O pacote

- [x] 4.1 `Publish`:
  - o item leva `kind`, `rubric` e `answer_capture_mode`;
  - o gabarito leva só objetivas, e `max_score` inclui as discursivas;
  - `fully_offline_gradable` é falso com discursiva;
  - cada atribuição traz `qrs`, um por região, com token e índice. `qrPayloadDaAtribuicao` recebe o
    índice e continua sendo o único compositor.

  Verificação: os três cenários da ADDED "O item discursivo publica a sua rubrica" e "A atribuição traz
  um QR por região".
- [x] 4.2 `requireCoherent` recusa as incoerências novas da spec. Verificação: um teste por cenário
  novo, cada um com um pacote **coerente em todo o resto**, e a asserção confere o motivo. **Ver
  falhar:** com a checagem de região por item discursivo desligada, cai **só** "Item discursivo sem
  região". Com a checagem do índice no payload desligada, cai **só** "QR de atribuição associado à
  região errada". Os dois conjuntos são disjuntos. Reverter e rodar.
- [x] 4.3 `folhaDaAtribuicao` troca cada QR pelo `qr_id` da região (decisão 3). Verificação: o cenário
  "Duas atribuições, uma geometria" sobre a fixture discursiva, com três regiões. **Ver falhar:** com
  a troca feita pela ordem das primitivas e as regiões emitidas fora de ordem num mapa de teste, o
  cenário cai.
- [x] 4.4 A quebra aceita é real e alta. Em `ConferenciaDePacoteTest`, o pacote
  `pacote-antes-da-discursiva.json` é recusado **pela camada (b)**. A guarda de vacuidade afirma, no
  mesmo cenário, que ele **passa** na camada (a), e a asserção confere o motivo (decisão 11).
  Verificação: o teste passa. **Ver falhar:** com a camada (b) desligada, o cenário cai, e o do pacote
  da ETAPA 3 (`pacote-do-contrato-anterior.json`) cai junto, porque é a mesma camada. Anotar os dois.

## 5. Fixtures e goldens — a sessão de P23 começa aqui e fecha na 6

- [x] 5.1 `fixtures/prova-discursiva.json`:
  - quatro objetivas e duas discursivas, com rubrica de 2 e de 3 critérios e `expected_lines`
    diferentes;
  - uma discursiva com `answer_capture_mode: color`;
  - habilidades BNCC em todas (I1).

  Verificação: a definição passa em `requireSupported`, e o motor produz 3 regiões.
- [x] 5.2 Rodar o `GoldenWriterTest` com `-Dplatos.golden.write=true`:
  - grava as goldens da fixture nova (`.layout.json`, `.package.json` e `.aluno.layout.json`);
  - regrava as de `prova-referencia`, `prova-2`, a da turma e `folha-de-teste`.

  Verificação da **guarda de geometria**: um script no scratchpad lê cada `.layout.json` regravado e o
  de `HEAD`, remove `qr_id`, `question_id` e `answer_area`, e afirma igualdade. Se sobrar diferença,
  **pare** (P13). E `git status` mostra só os arquivos previstos, listados na cobertura antes de rodar.
- [x] 5.3 Espelho web (decisão 9):
  - `apps/web/scripts/examPackage.ts` passa a `qrs` e `qr_id`;
  - um teste do Vitest deriva a folha do aluno do pacote gravado e a compara a
    `prova-discursiva.aluno.layout.json`.

  Verificação: o teste passa. **Ver falhar:** com o espelho escrevendo o payload da região 0 em todos
  os QRs, o teste novo cai. `compare.mjs` entre o PDF do aluno com a mutação e o PDF sem ela **passa**,
  e isso prova que centroide não vê payload. Reverter e rodar.
- [x] 5.4 `render-fixture.ts` renderiza a fixture discursiva, pela mesma disciplina de
  `PLATOS_PACKAGE`. Verificação: o PDF tem o número de páginas do mapa, e `fidelidade.mjs` roda sobre
  ele.

## 6. Paridade, fidelidade e tinta — nos dois renderizadores, na mesma sessão da 5.2

- [x] 6.1 `fidelidade.mjs` passa a medir ArUcos e círculos em todas as páginas (decisão 10).
  **Antes de estender:** com um marcador discursivo deslocado 0,5 mm numa página ≥ 1 do PDF web, a
  versão atual **passa**, e isso registra a lacuna. **Depois:** a mesma mutação **reprova**, nomeando o
  marcador. Reverter e rodar.
- [x] 6.1b `compare.mjs` passa a medir **retângulo de traço**. Acrescentada em 2026-09-24, por decisão
  do mantenedor: a correção na decisão 4 do design mostra que nenhum oráculo olhava moldura nem pauta.
  Para cada `rect` com `stroke > 0` e sem `fill`, mede-se a tinta na **faixa do contorno**: o
  retângulo expandido de `stroke/2 + 0,2 mm`, menos o retângulo encolhido do mesmo tanto, quando ele
  existe. A tinta é dividida pela área que o traço declarado ocupa, que é o retângulo expandido de
  `stroke/2` menos o encolhido de `stroke/2`.

  **Números fixados antes da primeira medição (P11):**
  - **presença:** a razão fica entre **0,5** e **1,5** em cada PDF. O piso pega o traço que não foi
    desenhado. O teto pega o traço grosso demais **nos dois lados**, que a concordância sozinha
    deixaria passar;
  - **concordância:** web e Android divergem no máximo **0,10**.

  Verificação: `compare.mjs` passa sobre `prova-referencia`, `folha-de-teste` e a fixture discursiva.
  **Ver falhar**, com duas mutações:
  - o renderizador Android **pula** o traço dos retângulos de uma região discursiva: cai a presença
    dos retângulos daquela região, e **nenhum** centroide;
  - o traço desenhado com o dobro da espessura nos dois renderizadores: cai o teto, e não a
    concordância.

  Os conjuntos disjuntos vão para a cobertura. Reverter e rodar (P10).
- [x] 6.2 Gerar o PDF da fixture discursiva nos dois lados **nesta sessão**:
  - web por `render-fixture.ts`;
  - Android por `connectedDebugAndroidTest`, com a suíte instrumentada do renderizador desenhando a
    fixture nova.

  Rodar `fidelidade.mjs` nos dois, `compare.mjs` entre eles e `tinta.mjs` nos dois. Rodar também os
  mesmos passos sobre `prova-referencia` e `folha-de-teste` regravadas. Verificação: todos verdes, com
  a data e o `sha256` de cada PDF na cobertura (P3, P23).
- [x] 6.3 Fechar a forma da pauta (decisão 4) pelo resultado da 6.2, com a medição de traço da 6.1b. Se
  a forma (a) passar, fica. Se não, experimentar a (b). **Se nenhuma passar, parar**: a decisão da
  versão volta ao mantenedor, e não se mexe em tolerância (P11). Verificação: a forma escolhida e o
  número que a decidiu, na cobertura.
- [x] 6.4 **Ver falhar** a paridade sobre a região discursiva: um marcador discursivo deslocado 0,5 mm
  só no renderizador Android faz `compare.mjs` reprovar, nomeando o marcador e a página. Reverter,
  gerar de novo e rodar (P10).
- [x] 6.5 `ci.yml`, job `paridade`: acrescentar a fixture discursiva aos passos de render web,
  fidelidade, tinta, render Android, comparação e tinta Android. Verificação: a PR roda os passos
  novos. Ler no log que cada um processou a fixture nova e não pulou (P2, P15).

## 7. O servidor

- [x] 7.1 Teste de API: publicar `prova-discursiva` com roster por `ExamPublication`, sobre o Postgres
  de teste. Os bytes gravados são o texto canônico, o `content_hash` confere, e cada atribuição tem três
  QRs. Atualizar os literais de pacote dos testes existentes que passam a divergir. Verificação:
  `./gradlew :apps:api:test` verde, com os literais trocados listados na cobertura.

## 8. O aparelho, sem mudança de produção (decisão 12)

- [x] 8.1 Teste de domínio: `ObjectiveScoring.score` sobre o pacote da fixture discursiva, com as
  respostas das quatro objetivas, recusa com "itens lidos divergem da variante" e o identificador das
  discursivas. **Ver falhar:** com a conferência de conjunto trocada por "lido contido no declarado", o
  teste cai porque sai nota. Reverter e rodar.
- [x] 8.2 A suíte do Android compila e passa com a forma nova do pacote: os testes que montam pacote à
  mão passam a usar `qrs`. Verificação: `./gradlew :apps:android:testDebugUnitTest` e
  `connectedDebugAndroidTest` verdes, sem filtro, com `timestamp`.

## 9. Fechamento

- [x] 9.1 `grep -rn "MUTACAO"` fora de `build/` e de `node_modules/` dá `0`, e toda reversão foi
  **rodada** (P10). Verificação: a saída do `grep` na cobertura.
- [x] 9.2 Comando cheio local, nesta sessão:
  - `./gradlew build --rerun-tasks` e `./gradlew :apps:android:connectedDebugAndroidTest`;
  - `npm test` em `apps/web`;
  - `node tools/parity/renderizador.mjs`, `divida.mjs`, `limiar.mjs`, `fio.mjs` e `answer-kind.mjs`.

  Verificação: contagem e `timestamp` de cada relatório, e quantas tasks foram **executadas**,
  comparados com a linha de base da 0.1 (P2, P3, P5).
- [x] 9.3 `docs/cobertura-slice-5a-regiao-discursiva.md`:
  - como cada verificação crítica foi **vista falhar**, com o conjunto previsto e o real;
  - as âncoras dos PDFs;
  - a guarda de geometria;
  - a seção "o que ainda não foi verificado", honesta. No mínimo: o caminho do `RegionDetector`, a
    folha em papel, a largura de 73 mm, e `answer_capture_mode` sem consumidor.

  Verificação: cada item do `rigorous.md` §8 tem resposta escrita.
- [x] 9.4 PR contra `main`, e o CI **lido no destino**: `build`, `web` e `paridade` verdes, com os
  passos novos da 6.5 no log, e a saída de `divida.mjs` com a linha `5b` (P2, P26). Verificação: o link
  do run e as linhas do log na cobertura.
- [ ] 9.5 Nomear as provas publicadas em produção que o aplicativo atualizado passa a recusar
  (ADR-0009). Verificação: a lista lida no banco, com a data. **Se o banco não for alcançável na
  sessão, a tarefa fica desmarcada** e a cobertura diz o que não foi lido.
- [x] 9.6 Preparar a reconciliação do archive (P27):
  - as três linhas `5` seguem **em dia**, alcançadas e não pagas, com a mudança da fatia que paga cada
    uma;
  - a linha `5b` nasceu nesta mudança;
  - o evento `migration-da-5-em-producao` não foi alcançado, porque não houve migration.

  Verificação: o parágrafo escrito na cobertura, pronto para o archive copiar.
