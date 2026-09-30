## 0. Antes do primeiro commit

- [x] 0.1 Medir a linha de base na árvore da `main` (com o archive da 5b-4 mergeado, ou sobre o
  branch que o contém):
  - `./gradlew build --rerun-tasks` e `./gradlew -p buildSrc test --rerun-tasks`;
  - `./gradlew :apps:android:connectedDebugAndroidTest`, sem filtro, no emulador que já é o da base
    (`platos-atd34`). **Sem mudar o ambiente local** (P22): se faltar memória de novo, como na
    5b-4, rodar as tasks separadas e registrar o desvio, sem declarar o agregado verde;
  - `npx vitest run` em `apps/web` **só** se `LayoutMapValidation` for espelhada lá (conferir por
    `grep` antes; hoje `apps/web/src/layoutMap.ts` só tem tipos).

  Anotar em `docs/cobertura-slice-5c-0-o-recorte-da-resposta.md` a contagem de testes por task, o
  `timestamp` de cada relatório e o número de tasks **executadas** (P2, P3).
  Verificação: os relatórios são desta sessão, com o `timestamp` dentro da janela da execução.
- [x] 0.2 Registrar `sha256` de `fixtures/*.layout.json` e de `fixtures/*.package.json` (o mapa e o
  pacote da prova com discursiva incluídos: `prova-discursiva.layout.json`,
  `prova-discursiva.aluno.layout.json`, `prova-discursiva.package.json`), e conferir por `grep` se
  `apps/android/src/androidTest/assets` os copia. Esta mudança não regrava nenhum (proposta, Impact); os `sha256` de antes são o que a 1.2 e a 7.2
  comparam. Verificação: a lista com o comando que a produziu.
- [x] 0.3 Registrar a saída de `node tools/divida/divida.mjs`. Verificação: `exit 0`, sem linha
  vencida, e a nota de que a tabela "Aberto" não é lida por ele (proposta, Impact).

## 1. Domínio: a garantia do mapa, o dono da faixa e a classificação do desvio

- [x] 1.1 `EssayGeometry.DEVIANT_BAND = Um.mm(3)` e um teste KMP de que ela é **menor** que o
  `gutter` de `LayoutProfile.DEFAULT` (design, decisão 4). Verificação: `jvmTest`, `jsNodeTest` e
  `testAndroidHostTest` passam; **visto falhar**: trocar a faixa para 6 mm derruba só esse teste, e a
  reversão é conferida rodando de novo (P9, P10).
- [x] 1.2 `LayoutMapValidation` recusa texto, imagem, QR, marcador, círculo ou retângulo preenchido
  dentro da `answer_area` de região discursiva, apontando região e primitiva (design, decisão 6,
  camada 1; spec `layout-engine`). Regras de interseção do design: área positiva, contato de borda
  fora, e texto pela origem dentro da largura da região e caixa de linha cruzando a altura da área.
  Verificação, por cenário da spec: texto dentro; nome do aluno sobre a área; marcador, QR ou
  imagem dentro; encostar na borda (aceita); moldura e pauta (aceitas). Mais dois testes que
  **prendem os limites**: o mapa de hoje da prova com discursiva passa sem alteração de asserção, e
  os `sha256` da 0.2 continuam iguais; um texto que começa **fora** da largura da região e a invade
  é **aceito** (nomeado como o limite conhecido da camada 1, para não virar lacuna silenciosa).
  **Visto falhar:** desligar a regra derruba só os cenários de recusa, e a mensagem confere região
  e primitiva. Rodar nos três alvos.
- [x] 1.3 `DesvioDaResposta.classificar(dentro, fora)` no domínio (`capture`), com a regra do
  design, decisão 5 (`proporcao >= 0,05 && fora >= 4 mm²`), e o tipo que carrega proporção, `fora` e
  o sinal. Verificação: testes de fronteira — 4,99%/5,00%, 3,99/4,00 mm², os dois juntos, zero,
  `dentro + fora == 0`, `NaN`, negativo e infinito (recusa clara ou zero, escolhido e pinado);
  **visto falhar**: trocar `>=` por `>` derruba exatamente os dois testes de fronteira exata, e trocar
  `&&` por `||` derruba o da mancha abaixo do piso. Nos três alvos.
  *Nota de 2026-09-30:* a guarda D-1.2 proíbe ponto flutuante no `commonMain`; a classificação usa
  inteiros (centésimos de mm² e ppm — `design.md`, decisão 5), e "`NaN`, negativo e infinito" virou
  "negativo, acima do teto da entrada e o estouro da multiplicação".

## 2. Android: os cantos do QR chegam ao ajuste

- [x] 2.1 `QrOutcome.Read` ganha `position` (os quatro cantos do símbolo, em pixels do canvas do
  QR); `RegionQrReader` o preenche a partir de `Result.position`. Aditivo: o `analyze` o ignora.
  *(Nota de 2026-09-30: não havia construção de `QrOutcome.Read` a atualizar — os testes só fazem cast.)*
  Verificação: as construções existentes (4) atualizadas sem mudar asserção; `testDebugUnitTest` e a
  contagem da 0.1 iguais, mais o teste novo de que `position` vem preenchida numa leitura válida.
- [x] 2.2 **Medir o que `position` significa**, antes de escrever o ajuste (design, Context: "o que
  esses quatro pontos significam no mapa é suposto"). Teste instrumentado sobre a folha renderizada
  de `tok-a`, região da página 0: para cada um de `topLeft`, `topRight` e `bottomLeft`, a distância
  ao canto correspondente de `region.qr` no canvas do QR, em milímetros; e o mesmo para
  `bottomRight`, só para registrar. **Critério fixado agora, antes da primeira execução (ADR-0007):**
  os três ficam a **≤ 0,5 mm** do canto esperado. Verificação: a tabela dos quatro números no
  documento de cobertura, com o instrumento e a data. **Se qualquer um dos três passar de 0,5 mm, a
  hipótese cai: parar, atualizar o `design.md` (decisão 2) sem apagar a linha antiga (P7), e só então
  continuar.** Visto falhar: o mesmo teste sobre uma imagem com o QR deslocado 2 mm no canvas
  reprova.
  **PARADA (2026-09-30): o critério reprovou** — região 2, `topRight` 0,610 mm e `bottomLeft`
  0,532 mm. Ver `design.md` (Context, "Resultado da tarefa 2.2") e o documento de cobertura. A tarefa
  fica **desmarcada**, e as tarefas 3.x não começam até o mantenedor decidir o que fazer com o
  critério (P11).
  **RETOMADA (2026-09-30), pelo ADR-0020 (decisão do mantenedor):** o critério acima fica registrado
  como **reprovado**, e a tarefa foi refeita com o do ADR — os três cantos a ≤ 0,5 mm (a mesma
  tolerância) da **caixa escura do próprio símbolo**, numa janela de ±1,5 mm, com guarda de
  vacuidade (lado de 13 a 15 mm) e as duas mutações do ADR. Resultado: âncoras a 0,0–0,1 mm do
  símbolo nas duas regiões; as mutações reprovam. **Este critério não é cego** (foi escolhido depois
  do diagnóstico), e o ADR diz isso.

## 3. Android: o segundo ajuste e a conferência pelo resíduo

- [x] 3.1 `RegionDetector` (ou colaborador dele em `vision/`) monta o segundo ajuste: recalcula a
  primeira homografia, leva os três cantos do QR do canvas à imagem pela inversa (design, decisão 2),
  e ajusta os 11 pontos por mínimos quadrados sem descarte. Devolve a homografia e o **maior**
  resíduo, em mm da região. Verificação: sobre a folha renderizada de frente, resíduo abaixo de
  0,2 mm e registrado (o piso do instrumento, design, Riscos); em perspectiva (o `skew` do
  `RectifierInstrumentedTest`), abaixo do teto de 1,0 mm.
- [x] 3.2 As recusas do spec, cada uma com o motivo conferido, e não só a recusa: resíduo acima do
  teto (a mensagem traz o resíduo e o teto), resíduo não finito, QR ilegível (a recusa é a do QR e o
  segundo ajuste nem roda), região sem `answer_area` (gabarito). Verificação: um teste por caminho,
  com fixture que **isola a camada** (passa nas outras conferências); **visto falhar**: cada
  mutação derruba só o seu teste, e `NaN` chega ao ajuste de propósito (`NaN > teto` é falso e passa
  calado — rigorous §3).
- [x] 3.3 **Medir o que o resíduo pega.** Deslocar cada um dos 11 pontos, um por vez, de 1, 2, 3 e
  5 mm no espaço da imagem, e registrar o maior resíduo resultante e se a região foi recusada.
  Verificação: a tabela 11 × 4 no documento de cobertura. O teste assere **só** o que a tabela diz
  que é pego; os pontos e tamanhos que o resíduo não denuncia entram no documento como lacuna,
  "não é mitigado, é conhecido" (P8). Se nenhum ponto do QR for denunciado abaixo de 5 mm, isso é
  achado: registrar, e parar para decidir antes da 4.1 (o desenho pode precisar de outra
  conferência).

## 4. Android: o recorte

- [x] 4.1 `SheetReader.recortar(gray, map, region)` devolve `RecorteOutcome` (design, decisão 1): um
  warp com a segunda homografia sobre a área mais a faixa, supersampleado em 3× e reduzido por
  `INTER_AREA`, a 10 px/mm; o miolo vira o recorte, e a faixa não sai da função. Verificação, em
  perspectiva sobre a folha renderizada: dimensões = área do mapa × 10 (± 1 px); a **moldura** cai a
  ≤ 0,5 mm da posição declarada no mapa, medida por perfil de intensidade no recorte (oráculo que não
  compartilha código com o warp, P4). **Visto falhar:** trocar a homografia do recorte pela da
  primeira retificação, ou deslocar um ponto do QR pela 3.3, tira a moldura da tolerância.
- [x] 4.2 Tinta no canto sem âncora. Desenhar tinta sintética (traços com posição conhecida no mapa)
  no canto inferior esquerdo da moldura, antes de pôr a página em perspectiva. Verificação: o traço
  aparece **inteiro** no recorte, com a caixa dele a ≤ 0,5 mm do esperado. Registrar o resultado
  como *medido sobre o documento renderizado*, e o canto continua "conhecido, não mitigado" até o
  papel.
- [x] 4.3 Tinta de fora não entra e recorte é determinístico. Verificação: uma mancha grande
  desenhada a partir de 1 mm **fora** da área deixa a contagem de pixels de tinta do recorte
  **exatamente** igual à do recorte sem a mancha; duas chamadas sobre a mesma captura dão imagens
  iguais byte a byte; o gabarito é recusado com o motivo "não declara área de resposta".
  **Visto falhar:** ampliar o canvas do miolo em 2 mm derruba o primeiro.

## 5. Android: o desvio

- [x] 5.1 Contar tinta do aluno na área e na faixa (design, decisão 5): tinta = cobertura contra o
  `PaperWhite` local ≥ `decorativeToneMax / 1000`; máscara = marcadores, QR e os quatro lados da
  moldura, dilatados pelo teto do resíduo; converter para mm² e chamar `classificar`. O recorte não
  leva nenhum pixel da faixa. Verificação, um teste por cenário da spec: dentro da área; extrapola;
  folha em branco (proporção **exatamente zero**); mancha abaixo do piso; resposta em branco; faixa
  não vaza. **Visto falhar:** tirar a máscara faz a folha em branco acusar tinta; trocar o limiar por
  um fixo em 255 conta a pauta como tinta; ambos derrubam só o cenário deles.
- [x] 5.2 Guarda de vacuidade (P13) do contador: um teste que planta uma quantidade **conhecida** de
  tinta na faixa (área em mm² calculada do desenho, não do contador) e confere que o contador a
  reporta dentro de 10%. **Critério fixado agora:** 10% — o erro do contador pode ser maior que o
  do ajuste porque a borda do traço é suavizada. Verificação: o teste passa; e um contador
  substituído por `return 0` o reprova.

## 6. A garantia de "sem cabeçalho", camada 2

- [x] 6.1 Sobre a folha renderizada, em perspectiva, um cabeçalho de nome de aluno impresso na página
  da região. Verificação: (a) folha limpa → **zero** pixels de tinta no recorte fora da máscara da
  moldura; (b) texto com origem **dentro** da largura da região e dentro da área (mapa montado à
  mão, com a validação contornada) → o recorte tem tinta fora da máscara; (c) texto com origem
  **fora** da largura da região e que a invade lendo para a direita → a camada 1 **aceita** o mapa
  (teste da 1.2) e o recorte tem tinta fora da máscara: a camada 2 vê o que a 1 não vê. **Guarda de
  vacuidade (P13):** antes de afirmar zero em (a), o teste planta uma tinta conhecida no recorte e
  confirma que o contador a conta. **Visto falhar:** (b) e (c) são as próprias mutações; conjuntos
  de cenários caídos disjuntos entre as duas camadas registrados no documento de cobertura.

## 7. Fechamento

- [x] 7.1 Repetir a suíte completa da 0.1 (P5) e comparar contagem e resultado; nenhuma regressão
  em `capture-omr`, `layout-engine`, `scan-session` ou `result-sync`. Verificação: relatórios com
  `timestamp` desta sessão (P3); se o agregado não couber na memória, as tasks separadas e o desvio
  registrado, sem afirmar o agregado.
- [x] 7.2 Provar o escopo. `git diff --stat main...` não toca `ScanSession`, `ScanActivity`, outbox,
  `apps/api`, `apps/web`, migrations, goldens nem fixtures, e os `sha256` da 0.2 continuam iguais;
  `grep -rn "recortar(" apps/android/src/main` mostra só a definição e o teste (o "sem chamador de
  produção" da decisão 1 é uma afirmação, e vira uma leitura). Verificação: as saídas dos dois
  comandos coladas no documento de cobertura.
- [x] 7.3 Reconciliar o §16 (P27) em `docs/architecture/ARQUITETURA-FINAL-v3.md`:
  - marcar **paga** a linha "Garantia executável de que o recorte discursivo não contém cabeçalho",
    citando os testes 1.2 e 6.1 e o limite declarado da camada 1;
  - **atualizar** "A região discursiva ainda não passou pelo aparelho nem pelo papel" com o que esta
    mudança mediu sobre o documento renderizado e o que o papel ainda precisa medir (a extrapolação do
    canto inferior esquerdo, o resíduo de 1,0 mm, o marcador de 11,2 mm), sem apagar o texto
    anterior (P7);
  - **abrir** na tabela "Ponto de não-retorno", com fatia-limite `6` e o motivo escrito: "O limiar
    do desvio (5% e 4 mm²) e o teto do resíduo (1,0 mm) foram fixados sem letra de aluno";
  - **abrir** na mesma tabela, com fatia-limite `5`: "A guarda de dívida não lê a tabela 'Aberto' do
    §16" — o item de injeção de prompt manuscrita (fatia-limite 5) **não** é tocado aqui e é
    reconciliado no archive da última mudança da fatia 5.
  Verificação: `node tools/divida/divida.mjs` com `exit 0`, o número de linhas lidas aumentado em 2,
  e as duas linhas novas listadas; **visto falhar**: uma linha nova com token malformado
  (`--arquitetura` apontando para uma cópia no scratchpad) faz a guarda sair com `2`.
- [x] 7.4 Escrever `docs/cobertura-slice-5c-0-o-recorte-da-resposta.md`: linha de base; as tabelas
  da 2.2 e da 3.3; cada "visto falhar" com o que caiu e o que não caiu; o que **não** foi verificado
  (papel, letra real, o canto inferior esquerdo fora do documento renderizado, a calibração dos três
  números); o par. 16; e a afirmação explícita de que o recorte não tem chamador de produção.
  Verificação: o documento existe, e cada afirmação carrega o tipo dela (medido, conferido, herdado,
  suposto — P6).
