## Context

O motivo está na proposta (Why). Aqui fica só o estado atual que molda o desenho, com o tipo de cada
afirmação (P6).

- **A discursiva é recusada na entrada.** `ExamDefinition.requireSupported` recusa `kind != OBJECTIVE`
  (`ExamDefinition.kt:158`), e `QuestionKind.ESSAY` já existe no enum. *Conferido por leitura.*
- **A região de gabarito é a única.** `LayoutEngine.layout` devolve `regions = listOf(region)`, e o
  gabarito emite bolhas para **todas** as questões (`LayoutEngine.kt:264`, `exam.questions.forEachIndexed`).
  *Conferido por leitura.*
- **O pacote tem forma para um QR por folha.** `PackageAssignment.qr: AssignmentQr?`, e
  `folhaDaAtribuicao` exige exatamente um `DrawQr` no mapa (`ExamPackage.kt:248`). A KDoc de
  `AssignmentQr` já prevê esta mudança: "QR repetido por região é D23, e quando ele chegar muda as duas
  specs juntas". *Conferido por leitura.*
- **O espelho web de `folhaDaAtribuicao` não tem oráculo.** `apps/web/scripts/examPackage.ts:34` diz
  que "a paridade entre plataformas é quem pega a divergência". A paridade compara **centroides** de
  marcador e bolha, e o `ci.yml` não renderiza folha de aluno: `PLATOS_STUDENT` não aparece nele, e
  `apps/web/test/` só tem `renderer.test.ts`, sem teste da folha do aluno. Um payload errado num QR
  não move centroide nenhum. *Conferido por leitura (`grep`), e não por mutação: a mutação é a tarefa
  5.3.*
- **`fidelidade.mjs` mede só a página 0.** ArUcos e círculos são filtrados de `map.pages[0]`
  (`tools/parity/fidelidade.mjs:179` e `:209`). Um marcador discursivo na página 2 passaria sem ser
  medido. `compare.mjs` percorre todas as páginas (`:141`, `:183`). *Conferido por leitura.*
- **O aparelho não lê a QR da atribuição.** Os usos de `.qr` em `apps/android/src/main` são
  `region.qr`, o retângulo normalizado da região (`RegionDetector.kt:255`), que não muda aqui. Em
  `apps/api/src/main` não há uso. *Conferido por `grep`.*
- **Uma prova com discursiva seria recusada na captura, e pelo motivo errado.** `Publish` põe todas as
  questões em `positions`, e `ObjectiveScoring.score` recusa quando o conjunto lido difere do
  declarado (`ObjectiveScoring.kt:183`). A discursiva não tem bolha, então nunca é lida. *Conferido por
  leitura do domínio.* O caminho do `RegionDetector` diante de marcadores `4…7` no quadro **não** foi
  lido nem exercitado, e é **suposto**.
- **Nenhuma publicação sai de rota HTTP.** `ExamPublication.publish` é chamado só por testes, inclusive
  `PublicarFixturesNoBancoRealTest`. *Conferido por `grep`.*
- **Pagina-se por coluna.** O perfil padrão tem 2 colunas de 87 mm (`LayoutProfile.kt:98-99`; A4 menos
  15 mm de cada lado, menos 6 mm de calha, dividido por dois). O paginador não tem bloco que atravessa
  colunas. *Conferido por leitura.*

## Goals / Non-Goals

**Goals:**
- A cadeia inteira de D35 no artefato: da definição à rubrica, da rubrica à moldura, da moldura ao
  pacote hasheado, e do pacote ao documento nos dois renderizadores, com paridade e fidelidade no CI.
- Uma quebra de `content_hash` só, carregando **todo** o contrato discursivo que a fatia 5 já conhece:
  rubrica, `expected_lines`, modo de captura, região e QR por região. A razão é o R1 do plano de
  correção: o hash quebrar duas vezes.
- O espelho web da folha do aluno sai desta mudança com um oráculo que reprova divergência de payload.

**Non-Goals:**
- Qualquer leitura da região discursiva: recorte, completude e deviant são da 5b.
- Mudar o comportamento do aparelho (decisão do mantenedor).
- Bloco que atravessa colunas, itens (a), (b) e (c), e imagem de enunciado.
- Medir a folha discursiva em papel. Vai para a linha `5b` do §16 (decisão 12).

## Decisions

### 1. A rubrica mora na definição e é copiada para o item do pacote, por critério

`Question` ganha `rubric: Rubric?` e `answerCaptureMode`. O `Rubric` é uma lista de `RubricCriterion`,
cada um com:
- `id`;
- `description`;
- `points`;
- `expected_lines`;
- `descriptors`, uma lista de níveis, cada um com `points` e `text`.

`PackageItem` ganha `kind`, `rubric` e `answer_capture_mode`. Os dois últimos são nulos no item
objetivo.

- **`expected_lines` é por critério, e a moldura mede a soma.** É o §11
  (`item_rubric_criterion(expected_lines)`), que é o modelo de dados canônico. O §5 diz "rubrica
  analítica (critérios · descritores · pontos · expected_lines)" sem dizer em que nível, e o §11 diz.
  Decidido nesta sessão, e exposto ao mantenedor antes de escrever.
- **Descritor é nível com pontos**, porque é o que a correção manual da 5c e a da 8 vão aplicar. A
  validação desta mudança exige só o que a spec diz: ao menos um descritor, com pontos dentro de
  `[0, pontos do critério]`. Regras sobre a escala dos níveis ficam para quem os consome (P18).
- **`answer_capture_mode` entra agora, sem consumidor nesta mudança**, e isso é uma tensão com P18 que
  fica escrita. O §5 lista o campo no item, o §8 o define, e o consumidor é a 5b. Deixá-lo para lá
  faria a 5b quebrar o `content_hash` de novo, que é o R1. O valor é `gray` por padrão, ou `color`, e
  qualquer outro é recusado.
- **Alternativa descartada: uma rubrica solta no pacote, endereçada por item.** Seria uma segunda
  estrutura com o mesmo `item_id` como chave, e a forma com que o pacote já tornou "folha sem
  atribuição" irrepresentável (spec de `exam-package`) vale também aqui.

### 2. A região declara a questão, a área de resposta e o QR dela

`ScannableRegion` ganha três campos:
- `question_id: String?`, nulo no gabarito;
- `answer_area: NormalizedRect?`, nulo no gabarito;
- `qr_id: String`, o `id` da primitiva `DrawQr` daquela região, **em toda região**, inclusive no
  gabarito e na folha de teste.

`kind` passa a ter o valor `essay`, ao lado de `answer_block` e `print_test`.

- **`qr_id` em toda região, e não só na discursiva**, porque é por ele que `folhaDaAtribuicao` troca
  cada QR (decisão 3). Uma regra que valesse para a discursiva e outra para o gabarito seriam duas
  regras. O custo é que a golden da folha de teste também muda, e P23 passa a incluí-la.
- **Alternativa descartada: ligar região e QR pela ordem das primitivas, ou pelo texto do `id`**
  (`r1-qr`). As duas funcionam hoje por coincidência de emissão, e as duas seriam reimplementadas no
  TypeScript. A spec agora proíbe inferir a ligação.
- **Alternativa descartada: um campo `region` em `DrawQr`.** Mudaria uma primitiva, que é o contrato
  do renderizador, para servir à captura. Os renderizadores não precisam saber de região.

### 3. A atribuição passa de `qr` a `qrs`, um por região (D23)

`PackageAssignment.qr: AssignmentQr?` vira `qrs: List<RegionQr>`, com
`RegionQr(region_index, payload, modules)` em ordem de índice. `folhaDaAtribuicao`, nas duas
implementações, percorre as regiões do layout da variante. Para cada uma, acha a primitiva de id
`qr_id` e troca o payload e a matriz pelos de `qrs[region.index]`.

- **Não é renomeação misturada com mudança funcional (P25).** A forma muda porque o conteúdo mudou: de
  um QR para vários. É exatamente o que a KDoc atual de `AssignmentQr` mandava fazer.
- **Coerência:** a atribuição traz um QR para **cada** região da variante, e nenhum a mais. Todos
  carregam o token dela e o índice certo. A recusa é da spec ("Atribuição sem o QR de uma região").
- **O escritor continua um só.** `qrPayloadDaAtribuicao` ganha o índice da região e continua sendo o
  único lugar que compõe payload.

### 4. `min_renderer_version` fica em `1` — decisão do mantenedor, com uma regra de parada

A moldura é um `rect` de traço. Os marcadores são `aruco`, o QR é `qr`, e o número da questão é
`text`. **Nenhuma capacidade de desenho nova.** A previsão do plano de correção (tabela "O que precisa
fechar antes de a fatia 5 abrir", linha da 7.1) supunha uma primitiva nova, e a leitura do código não a
confirmou. Subir a versão sem capacidade nova seria número sem consumidor (P18). E não há cliente
instalado para o D24 proteger: nenhum APK foi entregue.

**A pauta é o ponto frágil, e a forma dela é suposta.** Há duas saídas com primitiva existente:
- **(a) `rect` com traço e altura mínima.** Linha preta. O comportamento de um retângulo quase
  degenerado no `pdf-lib` e no `Canvas` é **suposto** até a paridade medir.
- **(b) `rect` preenchido, de altura igual à espessura.** Fica limitado ao teto de trama de 8%
  (`LayoutMapValidation.kt:83`), e uma linha a 8% de preto pode sumir na impressora de escola.

A escolha se faz na tarefa 3.3, **pela paridade e pela fidelidade**, e não por preferência.

> **Correção de 2026-09-24, ao executar a 3.3 — a frase acima fica (P7), e a premissa dela estava
> errada.** Nem a paridade nem a fidelidade, na forma que tinham, conseguiam escolher a pauta.
> `compare.mjs` mede centroides de ArUco, bolha e fórmula, e trama de retângulo **preenchido**
> (`targetsOf` e `tintTargetsOf`). `fidelidade.mjs` mede ArUcos e círculos. Nenhum dos dois olha
> retângulo de **traço**, que é o que moldura e pauta são. Um renderizador que não as desenhasse
> passaria verde em tudo: o mesmo modo de falha da faixa da 2b, que o Android ignorava enquanto "as 185
> comparações de centroide continuavam verdes" (comentário em `compare.mjs`). *Conferido por leitura.*
> **Decidido pelo mantenedor nesta sessão:** `compare.mjs` passa a medir o traço (decisão 10, e a
> tarefa 6.1b). A escolha da pauta se faz por ele, depois de ele ter sido visto falhar.

**Regra de parada:** se nenhuma das duas desenhar igual nos dois renderizadores, dentro das tolerâncias
que já existem, a mudança **para**. Uma primitiva nova é capacidade de renderizador nova, e aí
`min_renderer_version` sobe, que é a decisão que o mantenedor tomou com a informação contrária. Não se
afrouxa tolerância para a pauta passar (P11).

### 5. Marcador e QR da região discursiva: 14 mm, os mesmos do gabarito

O §7 admite 10 mm para a discursiva. Sob ADR-0001, o mínimo nominal que sobrevive a −5% de reescala é
10,53 mm. **Mas 14 mm é o único tamanho com detecção medida nesta base**: a 3b e a 3c mediram marcador
e QR de 14 mm, com o aparelho e a impressora que o §16 já registra como amostra estreita. Um marcador
menor seria geometria de captura **não medida**, entrando por economia de papel.

O QR tem o mesmo payload, do mesmo tamanho, então sai na mesma versão e com a mesma matriz. As 2 fotos
em 9 que não decodificam o QR (§16, linha do limiar) valem para ele como valem para o gabarito:
herdado, e não resolvido.

**Custo:** 4 marcadores de 14 mm por discursiva. **Alternativa descartada:** 10,5 a 12 mm, que
economiza papel às custas de uma geometria que ninguém mediu. A 5e, que é a fatia do corpus, pode
reabrir o tamanho com número.

### 6. A região discursiva mora numa coluna

O bloco discursivo (enunciado mais região) é um `Block` indivisível do paginador que já existe, com a
largura da coluna: 87 mm. A área de resposta fica dentro do quadrilátero, entre os centros dos
marcadores. Isso dá **uma linha útil de ~73 mm** (87 − 14), **calculado e não medido**: cerca de
metade de uma linha de página inteira.

- **Por que não página inteira agora:** o §7 diz que "blocos largos atravessam", e o paginador não
  sabe fazer isso. A DP distribui blocos por slot de coluna (`Pagination.kt:47`), e ensiná-la a
  ocupar dois slots de uma vez é mudança de algoritmo, com golden e P23 próprios. Juntá-la a esta
  mudança misturaria duas razões de quebra de geometria.
- **O custo é para o aluno**, e o mantenedor deve pesá-lo: mais linhas por resposta, e mais papel. O
  `expected_lines` da rubrica já se escreve contra a largura real da coluna.
- **Uma moldura maior que a coluna é recusada**, e não partida (spec). O §7 diz "nunca maior que uma
  página", e nesta forma o teto é a coluna, pelo mesmo motivo.

### 7. O gabarito tem só objetivas, numeradas pela posição na prova

A região 0 emite bolhas apenas das questões objetivas. Cada linha mantém o número **da questão na
prova**: com a 3 discursiva, o gabarito numera 1, 2, 4, 5. Renumerar 1, 2, 3, 4 faria o aluno marcar a
questão 4 na linha "3", que é exatamente o erro de transcrição que o §7 inteiro existe para mitigar.

**A prova só discursiva é recusada.** O §8 diz "sempre uma região `ANSWER_BLOCK`", e um gabarito sem
bolha é uma região sem consumidor. Liberá-la é decisão para quando houver pedido, e com a forma
decidida então.

### 8. O teto de discursivas vem do dicionário, e não de um número escolhido

`DICT_5X5_100` tem 100 marcadores, que dão 25 regiões. Descontado o gabarito, sobram **24**
discursivas. O limite sai de `ArucoDictionary.SIZE`, e não de uma constante nova: `markerIdsOf` já
recusa região além do dicionário (`CaptureGeometry.kt:45`). O que muda é que a recusa passa a vir
**antes** do cálculo, com mensagem que nomeia a prova, em vez de estourar no meio dele.

### 9. O espelho web ganha conferência cruzada, e ela é vista falhar sozinha

O `GoldenWriterTest` passa a gravar também a folha de um aluno da fixture discursiva, derivada pelo
`folhaDaAtribuicao` do Kotlin (`fixtures/prova-discursiva.aluno.layout.json`). Um teste do Vitest
deriva a mesma folha pelo espelho TypeScript, a partir do pacote gravado, e compara as duas.

- **O oráculo é independente o bastante para P28:** são duas implementações da regra, em duas
  linguagens, comparadas pela saída.
- **A mutação:** o espelho escreve o payload da região 0 em todos os QRs. A conferência nova **deve**
  cair, e a paridade **não** deve, porque centroide não vê payload. Conjuntos disjuntos são a prova de
  que a conferência nova cobre o que a paridade não cobre (`rigorous.md` §3).

### 10. Os oráculos da paridade passam a ver todas as páginas, e a extensão é vista falhar antes

`fidelidade.mjs` passa a medir ArUcos e círculos em **todas** as páginas. A ordem importa:
1. **Antes de estender**, desloca-se um marcador discursivo da fixture nova numa página ≥ 1. A
   `fidelidade.mjs` atual **passa**, e isso prova a lacuna de hoje.
2. **Depois de estender**, a mesma mutação **reprova**.
3. Reverte-se, e a reversão é conferida rodando (P10).

A prova de que a mudança fez alguma coisa é a diferença entre 1 e 2.

`compare.mjs` já percorre as páginas. O que se confere é que ele reprova um marcador discursivo
deslocado 0,5 mm só no Android, com a tolerância de 0,3 mm que já existe.

**`compare.mjs` passa a medir retângulo de traço** (acrescentado em 2026-09-24, por decisão do
mantenedor; ver a correção na decisão 4). Hoje nenhum oráculo olha moldura nem pauta. Para cada
`rect` com `stroke > 0` e sem `fill`, ele mede a tinta ao longo das **quatro bordas** nos dois PDFs,
com o mesmo rasterizador e a mesma janela. Duas asserções, as mesmas que a trama já tem:
- **presença:** a tinta de cada lado fica acima de um piso, e o traço existe;
- **concordância:** web e Android não divergem além de uma tolerância.

O piso e a tolerância são **fixados antes da primeira medição** (ADR-0007, P11) e escritos na tarefa.
A mutação que prova a camada é o renderizador Android **pulando** o traço dos retângulos de uma região
discursiva. A presença deve cair, e os centroides não. Os conjuntos disjuntos provam que a medição
nova vê o que a antiga não via.

### 11. O contrato entra sozinho, e o pacote do contrato atual é congelado antes

É a ETAPA 3, decisões 2, 3 e 4, aplicadas de novo:
- **Primeiro commit:** congela `fixtures/prova-referencia.package.json` como
  `fixtures/pacote-antes-da-discursiva.json`, byte a byte, com o motivo escrito. O
  `GoldenWriterTest` não o escreve.
- **Segundo commit:** os tipos, e nada mais. O build **fica vermelho** nele, porque os literais de
  hash deixam de bater, e a mensagem do commit diz isso.
- **Cenário de aceite da quebra:** o aplicativo atualizado recusa o pacote congelado **pela camada
  (b)**, que é reserializar e comparar. A guarda de vacuidade afirma, no mesmo cenário, que ele
  **passa** na camada (a), `sha256(bytes) == content_hash`. A asserção confere o **motivo**.
- **Guarda de geometria:** depois da regravação, a diferença em `prova-referencia.layout.json` e
  `folha-de-teste.layout.json` é **só** de campos novos (`qr_id`, `question_id: null`,
  `answer_area: null`), e nenhuma coordenada. Se uma coordenada mudar, **pare** (P13): alguma coisa
  alcançou a geometria objetiva. As fixtures derivadas da digitalização (`*.papel.json`,
  `*.recorte.pgm`) não mudam, e o passo "As fixtures da digitalização estão em dia" confere isso no CI.

### 12. O aparelho não muda, e o que ele deve fica no §16 com `5b`

Decisão do mantenedor nesta sessão. O que se faz aqui:
- **Um teste de domínio fixa a recusa atual.** `ObjectiveScoring.score` sobre o pacote da fixture
  discursiva, com as respostas das objetivas, recusa com "itens lidos divergem da variante" e o nome
  da discursiva. Não sai nota.
- **Uma linha nova no §16, com token `5b`**, reúne duas coisas com o mesmo veículo:
  - o aparelho recusa prova com discursiva **pelo motivo errado**, e o caminho do `RegionDetector`
    diante de marcadores discursivos não foi exercitado;
  - a folha discursiva **não foi medida em papel**, só no documento.

  A 5b fotografa folhas discursivas impressas de qualquer forma, e é lá que as duas se pagam. Se a 5c
  abrir sem a 5b ter pago, a guarda reprova.

### 13. A regra de parada vale sobre toda tarefa de verificação

Se o conjunto real de cenários que caem divergir do previsto em qualquer mutação desta mudança — mais,
menos ou outros —, **pare**. Escreva o conjunto real ao lado do previsto e diga o que ele significa
(P7, P12, P14). É a decisão 10 da ETAPA 3, sem mudança.

## Risks / Trade-offs

- **[Pacotes publicados deixam de ser legíveis pelo aplicativo atualizado]** → **aceito e
  registrado**, não mitigado. É o comportamento correto da camada (b), alto e não silencioso, e a
  saída é o ADR-0009. É a janela barata, pela mesma razão da ETAPA 3.
- **[A pauta pode não ter forma que desenhe igual nos dois lados]** → regra de parada da decisão 4.
  Não se afrouxa tolerância.
- **[A linha de 73 mm pode ser estreita para a escrita real]** → **conhecido, não mitigado.** É número
  calculado. A 5b fotografa respostas reais, e a 5e mede. O mantenedor decide se isso pede o bloco que
  atravessa colunas antes da 5b.
- **[`answer_capture_mode` sem consumidor nesta mudança]** → tensão com P18, escrita na decisão 1. O
  consumidor é a 5b. Se a 5b não o consumir, o campo sai com evento de hash próprio.
- **[O espelho TypeScript pode concordar com o Kotlin por construção comum]** → as duas implementações
  leem o mesmo pacote e o mesmo `qr_id`. Um erro no pacote, por exemplo QR trocado de região, passa
  nas duas. Quem pega isso é a coerência do pacote, que confere o `region_idx` de cada payload contra
  o índice da região. Duas camadas, cada uma com a sua mutação.
- **[Regravar golden e declarar paridade sem rodá-la]** → P23: os PDFs dos dois lados são gerados na
  sessão que fecha, e cada `timestamp` vai para a cobertura (P3). Comando cheio antes de publicar
  (P5).
- **[O caminho do `RegionDetector` com marcadores discursivos é suposto]** → linha `5b` do §16
  (decisão 12). **Não é mitigado, é conhecido** (P8).

## Migration Plan

**Não há migração de dados.** Nenhuma tabela muda, e `exam_package` guarda o texto canônico que
recebe (ADR-0008).

**O caminho de saída das provas já publicadas é o do ADR-0009:** publicar prova nova, com `short_id`
próprio. As provas de conferência em produção passam a ser recusadas pelo aplicativo atualizado, e a
cobertura nomeia quais são, lidas no banco no dia do fechamento.

**Ordem de implantação:** nenhuma dependência entre servidor e aparelho. O servidor valida o pacote na
publicação com o mesmo código KMP, e publicar é teste, não rota. A imagem da API sai pelo
`publicar-api.yml` no merge. Implantar no Render continua manual, e só se declara implantado o que for
observado lá (P26).

**Reversão:** reverter os commits devolve o `content_hash` anterior, mas exige regravar as goldens e
refazer P23. É barata em código e cara em verificação, como na ETAPA 3.

## Open Questions

Nenhuma que altere as specs, a abordagem ou as tarefas. A forma da pauta (decisão 4) é escolhida por
medição dentro de uma tarefa, e a regra de parada diz o que acontece se nenhuma servir.
