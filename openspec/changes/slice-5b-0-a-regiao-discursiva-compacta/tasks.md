## 0. Antes do primeiro commit

- [x] 0.1 Medir a linha de base na árvore desta branch, que é a `main` com os ADR-0016 a ADR-0019:
  - `./gradlew build --rerun-tasks`;
  - `./gradlew :apps:android:connectedDebugAndroidTest`, sem filtro, no `platos-atd34`;
  - `npx vitest run` em `apps/web`.

  Anotar em `docs/cobertura-slice-5b-0-a-regiao-discursiva-compacta.md` a contagem de testes por
  suíte, o `timestamp` de cada relatório e as tasks **executadas** (P2, P3). Verificação: os
  relatórios são desta sessão.
- [x] 0.2 Registrar a saída de `node tools/divida/divida.mjs`: "fatia corrente: 5b", e as quatro
  linhas sob "vence nesta fatia". Verificação: a saída na cobertura, com `exit 0`.
- [x] 0.3 **A âncora da guarda da decisão 8.** Anotar na cobertura o `sha256`, calculado pelo
  `crypto` do Node sobre os bytes (P4), destes arquivos:
  - `prova-referencia.layout.json`;
  - `prova-referencia.package.json`;
  - `prova-referencia.turma.package.json`;
  - `prova-2.package.json`;
  - `folha-de-teste.layout.json`.

  Verificação: os cinco valores na cobertura, com o commit de onde foram lidos.

## 1. O registro, antes do código (P27)

- [x] 1.1 Acrescentar ao §16 de `docs/architecture/ARQUITETURA-FINAL-v3.md` a linha "A folha de
  teste de impressão não aprova a região discursiva que a prova imprime" (decisão 9), com:
  - token `5b`, dono mantenedor;
  - o motivo: ela aprova marcador de 14 mm e não tem pauta cinza;
  - por que ela não muda aqui: o filtro da 1.1 da 5b-1 não está na `main`;
  - o que encarece depois.

  Verificação: `divida.mjs` a lista como `em dia`. **Ver falhar:** rodar com `--mudancas` apontando
  para uma cópia de `openspec/changes/` no scratchpad, acrescida de `slice-5c-sonda`. A guarda sai
  com `1` e nomeia as **duas** linhas `5b`: esta e "A região discursiva ainda não passou pelo aparelho
  nem pelo papel". Depois, rodar sobre a árvore real e ver `exit 0`.

## 2. O contrato, sozinho

- [x] 2.1 Commit só de contrato:
  - em `packages/domain`: `Question.answerLines` (`answer_lines`), `Question.answerWidth`
    (`answer_width`) e o enum `AnswerWidth` (`column`, `page`);
  - `DrawLine` (`"line"`: `x1`, `y1`, `x2`, `y2`, `stroke`, `tone`), em `LayoutMap.kt` e no espelho
    `apps/web/src/layoutMap.ts`.

  Nenhum consumidor muda além do mínimo para compilar. O ramo `DrawLine` do renderizador Android
  lança `UnknownPrimitiveException`, e o do web já cai no `default`, que recusa.

  **Previsto antes de rodar:** nenhum teste cai e nenhum golden muda. `Question` não é serializada no
  pacote, e ninguém emite `line` ainda.

  Verificação:
  - `./gradlew :packages:domain:allTests` e a compilação do Android e do web;
  - os cinco `sha256` da 0.3 iguais.

  Anotar o real ao lado do previsto (P12).

## 3. O motor

- [x] 3.1 `requireSupported` recusa os casos novos da spec, e os quatro testes de domínio que montam
  discursiva passam a declarar os dois campos: `ExamDefinitionTest`, `PacoteDiscursivoTest`,
  `LayoutEngineTest` e `RegiaoDiscursivaTest`. O commit diz isso.

  Verificação: um teste por cenário novo de "Recusa de entrada não suportada":
  - "Discursiva sem número de linhas";
  - "Discursiva sem largura";
  - "Largura de página ainda é recusada";
  - "Objetiva com escolha da discursiva".

  Cada teste confere a **mensagem**, com a questão e o motivo, e o de página confere a menção à
  paginação em faixas.

  **Ver falhar:** sem a checagem de largura, cai **só** "Discursiva sem largura". Reverter e rodar.
- [x] 3.2 A região nova (decisões 1 e 2):
  - as constantes em `EssayGeometry` (marcador 11,2 mm, módulo 1,6 mm, zona de silêncio do QR de
    2 mm, folga de baixo de 2 mm, pauta de 7 mm);
  - `QuestionBlocks` lê `answerLines`;
  - `emitEssayRegion` emite o `4k` e o `4k+3`, o QR no canto e a moldura na largura inteira;
  - o retângulo de referência é o externo, e a `answer_area` vai da base do QR até a zona de silêncio
    do `4k+3`;
  - a pauta sai como `DrawLine` a 300‰ com 0,2 mm (decisão 5, provisório).

  Verificação: um teste por cenário:
  - "Dois marcadores na diagonal e o QR no terceiro canto";
  - "Identificadores dos marcadores da região discursiva";
  - "Marcador discursivo dimensionado com folga";
  - "Coordenadas dentro da faixa normalizada";
  - "Zona de silêncio preservada";
  - "O professor dimensiona a moldura";
  - "A rubrica não mexe na moldura";
  - "Moldura maior que a coluna";
  - "Área de resposta com folga fora da moldura";
  - "Pauta abaixo do teto decorativo";
  - "Enunciado e moldura não se separam";
  - "O enunciado fica fora da moldura".

  **Ver falhar**, com dois conjuntos disjuntos previstos:
  - **QR centrado**, como hoje: cai **só** "Dois marcadores na diagonal e o QR no terceiro canto";
  - **moldura lida de `Σ expected_lines`**: caem **só** "A rubrica não mexe na moldura" e "O
    professor dimensiona a moldura".

  Reverter e rodar.
- [x] 3.3 Versão mínima de renderizador por mapa (decisão 4), numa função única ao lado das
  primitivas, usada pelo motor e pela folha de teste. Verificação: os cenários "Mapa com pauta exige
  o renderizador que desenha linha" e "Mapa sem linha continua exigindo a versão 1". **Ver falhar:**
  com a função devolvendo sempre 2, cai **só** o segundo. O `sha256` de `prova-referencia.layout.json`
  regravado nessa condição, no scratchpad, difere do da 0.3, e isso anota que a guarda da 5.2 também o
  pegaria. Reverter e rodar.
- [ ] 3.4 `LayoutMapValidation`:
  - a região discursiva declara exatamente `4k` e `4k+3`, e o gabarito continua com os quatro;
  - o marcador declarado por região discursiva existe entre as primitivas da página dela;
  - a linha dentro da área de resposta tem tom declarado e abaixo do teto decorativo da região;
  - o teto de 80‰ continua só para preenchimento.

  Verificação: um teste por cenário:
  - "Região discursiva com os marcadores errados";
  - "Marcador declarado que não existe";
  - "Pauta preta é recusada";
  - "Pauta acima do teto é recusada";
  - "Linha cinza não é trama".

  Cada teste parte de um mapa **válido** produzido pelo motor e muda **uma** coisa, e a asserção
  confere a mensagem (`rigorous.md` §3).

  **Ver falhar**, com dois conjuntos disjuntos:
  - **sem a checagem de tom da linha:** caem "Pauta preta é recusada" e "Pauta acima do teto é
    recusada";
  - **com o teto de 80‰ aplicado também à linha:** cai **só** "Linha cinza não é trama".

  Reverter e rodar.

## 4. Os renderizadores

- [ ] 4.1 Web: `renderer.ts` desenha `line` entre os dois pontos, com a espessura, o cinza do tom e
  sem arremate, e `RENDERER_VERSION = 2`.

  Verificação: um teste do Vitest desenha um mapa com uma linha cinza e uma sem tom, e confere no
  conteúdo da página o traço e as duas cores declaradas. Um mapa que exige a versão 3 é recusado.

  O cenário "Renderizador anterior recusa mapa com linha" fica coberto por **composição**, e a
  cobertura diz isso (P16):
  - o motor declara 2 para mapa com linha (3.3);
  - a guarda de versão recusa mapa acima da própria versão (teste existente).
- [ ] 4.2 Android: `LayoutMapRenderer` desenha `DrawLine` com `Paint` de traço, cinza do tom e
  `Cap.BUTT`, e `RendererContract.RENDERER_VERSION = 2`.

  Verificação:
  - o teste de JVM do `RendererContract` passa com a versão nova;
  - `LayoutMapRendererInstrumentedTest` gera `android-discursiva.pdf` sem exceção.

  O oráculo do desenho é a 5.3, e não este teste.
- [ ] 4.3 **Acrescentada ao aplicar, com o motivo na decisão 4 (atualização de 2026-09-25).** A guarda
  da versão do renderizador lê o registro novo do domínio:
  - `tools/parity/renderizador.mjs` lê `LayoutMap.LINE_RENDERER_VERSION`, com rótulo e papel que dizem
    "a versão mais alta que o motor pode exigir";
  - o comentário do passo do CI acompanha.

  Verificação: `node tools/parity/renderizador.mjs` sai `0` com os três registros em 2, e o passo "A
  verificação da versão do renderizador continua capaz de falhar" roda localmente: cada `--divergir`
  sai `1` e nomeia só os dois pares do registro forçado.

  **Ver falhar:** sobre a árvore nova, com a guarda ainda lendo `MIN_RENDERER_VERSION`, ela sai `2` e
  nomeia o registro `dominio`.

## 5. Fixtures, goldens, paridade e fidelidade — uma sessão só (P23)

- [ ] 5.1 `fixtures/prova-discursiva.json`: `d1` com `answer_lines: 5` e `d2` com `answer_lines: 7`,
  as mesmas somas de `expected_lines` de hoje, e as duas com `answer_width: column`. A diferença de
  geometria sai só desta mudança. Verificação: a definição passa em `requireSupported`, e o motor
  produz três regiões.

  **A edição da definição foi feita no commit da 3.1, e não aqui.** Com a recusa nova, o
  `GoldenLayoutTest` da discursiva e o `ExamPublicationTest` da API, que leem esta definição, cairiam
  no commit da 3.1. Como o motor ainda lia Σ `expected_lines` até a 3.2, declarar 5 e 7 ali não mudou
  golden nenhum. A verificação desta tarefa continua sendo feita aqui.
- [ ] 5.2 Rodar o `GoldenWriterTest` com `-Dplatos.golden.write=true`.

  **Previsto antes de rodar**, anotado na cobertura: mudam **só** estes três arquivos:
  - `prova-discursiva.layout.json`;
  - `prova-discursiva.package.json`;
  - `prova-discursiva.aluno.layout.json`.

  Verificação:
  - `git status --short fixtures/` mostra exatamente esses três;
  - os cinco `sha256` da 0.3 são iguais, recalculados pelo `crypto` do Node (decisão 8);
  - a fixture nova, lida:
    - regiões com marcadores `[0,1,2,3]`, `[4,7]` e `[8,11]`;
    - `min_renderer_version` 2;
    - região de `d1` com 66 mm e de `d2` com 81 mm, pela fórmula da decisão 1.

  Se sobrar arquivo fora da lista, **pare** (P13).
- [ ] 5.3 `compare.mjs` mede a linha pela tinta esperada (decisão 7), com as tolerâncias de traço que
  já existem, e recusa linha inclinada com erro. **Ver falhar**, cada mutação sozinha:
  - o web não desenha `line`: a paridade da discursiva reprova e nomeia as linhas da pauta;
  - o Android desenha `line` em preto, ignorando o tom: reprova com razão perto de 3,3;
  - um mapa de teste com uma linha inclinada: a ferramenta sai com erro que a nomeia, e não com
    verde.

  Reverter e rodar.
- [ ] 5.4 **Na mesma sessão da 5.2:**
  - gerar os PDFs web e Android: `web.pdf`, `teste-web.pdf`, `discursiva-web.pdf`, e no
    `platos-atd34` `android.pdf`, `android-teste.pdf` e `android-discursiva.pdf`;
  - rodar localmente cada passo de fidelidade, paridade e tinta que o `ci.yml` roda, para as três
    fixtures.

  **Deslocamento deliberado:** com o marcador 8 da discursiva deslocado 0,5 mm no PDF web, a
  fidelidade **reprova** e o nomeia.

  Verificação: tudo verde, e só o deslocado vermelho. As saídas vão na cobertura, com horário.
- [ ] 5.5 Determinismo nos três alvos. `GoldenLayoutTest` bate byte a byte com a golden nova, e a
  guarda de vacuidade dele confere três regiões e a de `d2` fora da página 0. Verificação:
  `./gradlew :packages:domain:allTests --rerun`, com contagem e `timestamp` por alvo.

## 6. O papel (decisão 5)

- [ ] 6.1 **Tarefa do mantenedor.** Imprimir a 100% o `build/parity/discursiva-aluno-web.pdf` novo, a
  folha de `tok-a`, na impressora dele. Aplicar os critérios da decisão 5, **escritos antes desta
  impressão**:
  - pauta contínua, mais clara que a moldura e que o texto, e guiando três linhas escritas à mão;
  - marcadores completos, com a borda fechada e a zona de silêncio livre.

  Digitalizar ou fotografar a folha como registro, em `fixtures/prova-discursiva.digitalizacao.jpg`,
  com o EXIF conferido sem coordenada de GPS.

  Verificação: o resultado **por critério** na cobertura, com o `sha256` da imagem.

  **Se reprovar:** seguir o degrau da decisão 5. Cada degrau é regravação, P23 e impressão nova, e
  fica registrado. **Regra de parada:** nenhum degrau aprova, e a mudança para (ADR-0016).

  **Não é a medição de detecção do marcador de 11,2 mm**, que é da 4.1 retomada da 5b-1.

## 7. Fechamento

- [ ] 7.1 `grep -rn "MUTACAO"` fora de `build/` e de `node_modules/` dá `0`, e toda reversão foi
  rodada (P10).
- [ ] 7.2 Comando cheio local, com contagens e `timestamp` comparados com a 0.1 (P2, P3, P5):
  - `./gradlew build --rerun-tasks` e `connectedDebugAndroidTest`, sem filtro;
  - `vitest` e o `build` do web;
  - as guardas Node.
- [ ] 7.3 `docs/cobertura-slice-5b-0-a-regiao-discursiva-compacta.md`:
  - como cada verificação foi vista falhar, com o previsto e o real;
  - os `sha256` da decisão 8, antes e depois;
  - a impressão da 6.1;
  - a seção "o que ainda não foi verificado". Ela diz, no mínimo:
    - a detecção do marcador de 11,2 mm;
    - a leitura da região de dois ArUcos no aparelho;
    - a pauta numa impressora que não seja a do mantenedor.
- [ ] 7.4 PR contra `main`, e o CI **lido no destino** (P2, P26).
- [ ] 7.5 Preparar a reconciliação do archive (P27):
  - nenhuma linha do §16 paga;
  - a linha nova da 1.1 em dia;
  - a linha `5b` da região discursiva continua devida pela 5b-1;
  - os eventos `migration-da-5-em-producao` e `implantar-api-da-5a` não foram alcançados.

  Depois do archive, a próxima ação é o `/opsx:update` da 5b-1, para a região de dois ArUcos. Ela
  **não** é tarefa desta mudança.
