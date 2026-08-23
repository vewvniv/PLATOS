## 1. Base de comparação

- [x] 1.1 Registrar os números atuais antes de tocar em qualquer coisa: hash e tamanho do golden e do pacote da fixture, paridade, fidelidade nos dois PDFs, e contagem da suíte por alvo. Resultado: tabela anotada aqui — a fatia muda a folha de propósito, e sem a base não há como separar o que ela mudou do que já estava assim.

  Medido em 2026-08-20, com `./gradlew build :packages:domain:testAndroidHostTest`, `npx tsx scripts/render-fixture.ts` e as duas ferramentas de `tools/parity`.

  | Grandeza | Valor |
  |---|---|
  | `fixtures/prova-referencia.layout.json` | sha256 `ceaf67cc…`, 69 945 bytes |
  | `fixtures/prova-referencia.package.json` | sha256 `61c96f4c…`, 79 657 bytes — é o `content_hash` do pacote |
  | `build/parity/web.pdf` | 225 498 bytes, 4 páginas, 21 fórmulas |
  | Paridade web × Android | 185 de 185 elementos, maior divergência **0,042 mm** em `qq37-f`, folga 0,258 mm |
  | Fidelidade web · Android | 116 verificações cada; **0,039 mm** (`marcador 2: borda superior`) e **0,022 mm** (`fórmula qq30-f: altura da tinta`) |

  | Alvo | Testes | Falhas |
  |---|---|---|
  | `packages/domain` JVM | 197 | 0 |
  | `packages/domain` Node/JS | 192 | 0 |
  | `packages/domain` Android (host) | 192 | 0 |
  | `apps/api` (Postgres real) | 89 | 0 |

  Iguais aos números com que a 2a fechou, e nenhuma falha pré-existente. O `android.pdf` usado é o
  da última execução do emulador na 2a, contra este mesmo golden — válido como base porque nada
  mudou desde então; a partir da primeira regravação ele precisa ser gerado de novo.

- [x] 1.2 Confirmar que o Android hoje **ignora** `DrawRect.fill`, e que a paridade não acusa isso. Resultado: a lacuna registrada com a evidência, porque é ela que justifica a tarefa 2.4 existir antes de qualquer faixa ser desenhada.

  As duas metades confirmadas na leitura do código, e as duas são piores do que a proposta supunha:

  - `LayoutMapRenderer.kt` desenha `DrawRect` com `strokePaint(primitive.stroke)` e **nunca lê**
    `primitive.fill`. Todos os `Paint` do arquivo são `Color.BLACK` fixo: não há um só caminho de
    tom no renderizador Android. O web, em `renderer.ts`, passa `color: grayOf(primitive.fill)`.
  - `targetsOf` em `compare.mjs` só produz alvo para `aruco`, `circle` e `image`. **Um `rect` nunca
    é comparado.** A checagem estrutural ao redor compara número de páginas e tamanho do raster, e
    nada mais — uma faixa presente num documento e ausente no outro passaria pelas 185 comparações
    sem mover nenhuma delas.

  É uma lacuna que existe desde a fatia 1 e que ninguém tinha como ver, porque nenhum mapa emitia
  `fill`. A fatia 2b é a primeira a emitir.

## 2. Contrato: a escala de tinta, antes de qualquer consumidor

- [x] 2.1 Passar `DrawRect.fill` de porcentagem para permilagem de preto e dar tom a `DrawText`, na mesma escala (D-2b.1). Resultado: contrato mudado nos três alvos e no TypeScript, com nenhum mapa emitindo tinta ainda; commit próprio, separado dos consumidores.

  `DrawRect.fill` passa a ser permilagem, `DrawText` ganha `tone`, e `LayoutMap` ganha `TONE_FULL`
  e `FLAT_TONE_CEILING`. No web, `grayOf` divide por 1000 em vez de 100.

  **`tone` é escrito como `null` explícito, e isso é decisão.** A serialização canônica declara
  `explicitNulls = true` — "sem valor omitido por padrão" —, e `@EncodeDefault(NEVER)` teria
  deixado o golden intacto ao custo de o artefato publicado passar a ter um campo cujo significado
  só existe em quem lê. É o mesmo argumento de D-2a.3, que preferiu declarar o perfil por
  identificador **e** valores, e o precedente de `prompt_version`/`model_id`, que viajam nulos.

  **Antes de regravar, as guardas foram vistas ficando vermelhas:** `GoldenLayoutTest.mapa da
  fixture bate byte a byte com o golden`, `PacoteVersionadoTest` (duas), `ExamPackageTest.o hash da
  fixture e o mesmo nos tres alvos` e `LayoutProfileTest.perfil padrao produz exatamente o mapa de
  antes de o perfil existir`. Cinco reprovações para uma mudança que não move um micrômetro — é o
  que se espera de um contrato que entra num artefato hasheado.

  Golden e pacote regravados com esta causa só, e a auditoria confere:

  | Grandeza | Antes | Depois |
  |---|---|---|
  | `…layout.json` | `ceaf67cc…`, 69 945 bytes | `8e7cdd17…`, 75 453 bytes |
  | `…package.json` | `61c96f4c…`, 79 657 bytes | `249c58e0…` |
  | Paridade · fidelidade | 0,042 mm · 0,039 mm | **iguais**, como tem de ser |

  A prova de que nada além do campo mudou: o golden novo tem **459** ocorrências de `,"tone":null`
  e **459** primitivas de texto, o crescimento é exatamente `12 × 459 = 5 508` bytes, e remover a
  substring do arquivo novo devolve o antigo **byte a byte**. O literal do hash do pacote foi
  atualizado em `ExamPackageTest` e `ExamPublicationTest`; `docs/cobertura-fatia-2a.md` ficou como
  estava, porque ele registra o que era verdade ao fechar a 2a.
- [x] 2.2 Fazer a validação do `LayoutMap` recusar tom ou trama fora da faixa admitida e tinta chapada acima de 8%. Resultado: cobre os cenários "Trama acima do teto" e "Tom fora da faixa", cada um com o caso positivo ao lado, sem o qual a recusa poderia estar recusando tudo.

  Seis testes novos em `LayoutMapValidationTest`, três de recusa e três de aceite.

  **O teto de 8% vale para trama chapada, e não para o tom de um glifo.** §7 diz "monocromático,
  tramas ≤ 8%", e trama é área chapada: a letra dentro do círculo é texto, e recusá-la pelo teto da
  faixa aplicaria a regra errada ao elemento errado — a letra ficaria clara demais para ser lida
  justamente porque uma regra sobre fundo foi aplicada a um glifo. Os dois casos ficam afirmados
  lado a lado, com `tone = 400` aceito e `fill = 200` recusado.

  **Vista falhar:** removido o ramo do teto, `trama acima do teto de oito por cento e apontada`
  ficou vermelho e **nenhum outro** — inclusive os de faixa, que continuam cobrindo `-1` e `1001`.
  Restaurado, 13 de 13 verdes.
- [x] 2.3 Fazer os dois renderizadores desenharem exatamente o tom e a trama declarados, incluindo preenchimento de `DrawRect` no Android (D-2b.5). Resultado: cobre "Tom desenhado como declarado" nos dois lados.

  No web, dois testes novos leem o **fluxo de conteúdo descomprimido** do PDF e afirmam o operador
  de cinza: `0.55 0.55 0.55 rg` para tom 450‰ e `0.955 0.955 0.955 rg` para trama 45‰. O tom não
  aparece na estrutura do documento — sem descomprimir, um teste de tom estaria afirmando sobre
  bytes opacos. Visto falhar devolvendo o texto para preto fixo: exatamente um teste vermelho.

  No Android, `DrawRect` passou a preencher e `DrawText` a receber tom, com a mesma conversão de
  permilagem. A evidência é a paridade da tarefa 7.4: as tramas coincidem entre plataformas dentro
  de um passo de quantização.

  **Traço zero passou a significar sem traço, nos dois.** Largura 0 em PDF é "a linha mais fina que
  o dispositivo consegue", e `strokeWidth = 0` no Android desenha hairline: a faixa sairia com um
  contorno preto de um pixel em volta de algo que deve ser só trama — e o contorno cairia dentro da
  janela onde o OMR mede.
- [x] 2.4 Acrescentar à paridade a comparação de **cobertura** de cada retângulo preenchido, além dos centroides. Resultado: cobre "Trama equivalente entre plataformas"; visto falhar removendo o preenchimento de um dos renderizadores e confirmando o vermelho — que é exatamente o defeito que sobreviveu até hoje sem ninguém ver.

  A comparação é entre os dois documentos **e** contra o valor declarado no mapa. Sem a segunda
  metade, uma faixa que sumisse dos **dois** lados passaria com diferença zero — que é o modo de
  falha que a lacuna da tarefa 1.2 teria produzido se o web também deixasse de preencher.

  **O nível da trama é a moda dos pixels, não a média.** A primeira execução leu 13% para uma trama
  de 4,5%: a faixa passa por baixo das bolhas, das letras e dos números, e a média da área soma a
  tinta preta desenhada em cima. Média responde "quanta tinta há aqui"; §7 pergunta "qual é o nível
  da trama".

  **Visto falhar**, com `render-inked.ts` produzindo um documento sem as faixas:

  | Verificação | Leu |
  |---|---|
  | Centroides (185 elementos) | maior divergência **0,004 mm** — não viu nada |
  | Comparação de trama | **4,71 pontos** em `r0-f0-1` (web 4,71%, outro lado 0,00%) → vermelho |

  As duas linhas juntas são a tarefa: a faixa ausente é invisível para tudo que media posição, e a
  guarda nova é o único lugar onde ela aparece.

## 3. A folha completa

- [x] 3.1 Emitir o cabeçalho — título da prova e instrução de preenchimento — acima da região de gabarito, com a região continuando na página 1 e a faixa de grampo livre (D-2b.8). Resultado: cobre "Instrução de preenchimento na folha", "A região de gabarito continua no topo" e "Faixa do grampo permanece livre".

  O cabeçalho é medido antes de ser emitido, e a altura medida é a mesma que entra na reserva da
  primeira página. Reserva calculada por um caminho e desenho feito por outro divergem em silêncio,
  e a divergência só apareceria no papel — é a armadilha que `advanceAfterStatement` já evitava na
  fórmula em bloco.

  O corpo do título é **derivado** do perfil (uma vez e meia o corpo do texto), e não um parâmetro
  novo. Um parâmetro novo teria de aparecer no `LayoutProfileRef` para não violar ADR-0004 — dois
  perfis com o mesmo identificador e títulos de tamanhos diferentes seriam indistinguíveis no
  artefato publicado. Derivar mantém o cabeçalho do mapa suficiente para reconstituir a folha.

  **Um teste antigo teve de mudar, e a mudança é de conteúdo, não de acomodação.** `existe
  exatamente uma regiao de gabarito no topo da pagina 1` afirmava `quad_y == marginTop + meio
  marcador`, isto é, que a região encosta na margem superior. Isso deixou de ser verdade de
  propósito. O que §7 quer da posição da região é que a pilha objetiva seja escaneada **sem
  folhear**, e isso é "página 1, antes de qualquer questão" — que é o que o teste passa a afirmar,
  agora contra a posição da primeira questão em vez de contra uma constante.

  **Dois testes novos que a fatia obrigou:**

  - A zona de silêncio deixou de ser conferida só contra bolhas e passa a valer para **qualquer
    tinta**. Antes desta fatia só bolha chegava perto de um marcador; com cabeçalho e faixa, texto
    e trama passaram a poder invadir, e marcador com traço alheio na zona de silêncio é a causa
    mais comum de captura que não fecha (§16).
  - `todo caractere impresso tem glifo na fonte embarcada`. `FontProgram.glyphOf` devolve `.notdef`
    para o que a fonte não cobre **sem reclamar**: o texto sai medido, o mapa sai válido e o
    defeito aparece só no papel. A instrução de preenchimento é o primeiro texto acentuado que o
    engine emite — antes dela, nenhum. Conferido no programa de fonte que `á â ã ç é ê í ó ô õ ú`
    e as maiúsculas correspondentes têm glifo real.

- [x] 3.2 Emitir o rodapé com posição e total de páginas, dentro da margem inferior, sem invadir conteúdo nem região escaneável. Resultado: cobre "Numeração em todas as páginas".

  `Página N de M`, centrado, com a linha de base 6 mm abaixo do fim da área de conteúdo — dentro da
  margem inferior, que existia e estava vazia. Centrar exige largura de texto, e quem mede é o
  mesmo medidor do resto da folha: o renderizador continua sem medir nada.

- [x] 3.3 Regravar golden e pacote com **esta** causa só, e provar que nada além do cabeçalho, do rodapé e do deslocamento que eles produzem mudou. Resultado: auditoria registrada como nas duas regravações da 2a — separando "mudou o que está escrito" de "mudou onde está escrito" —, mais o número de páginas antes e depois.

  | Grandeza | Antes | Depois |
  |---|---|---|
  | `…layout.json` | `8e7cdd17…`, 75 453 bytes | `e2deb45d…`, 76 240 bytes |
  | `…package.json` | `249c58e0…` | `24fcaaa2…` |
  | Páginas | 4 | **4** |
  | Fidelidade web | 0,039 mm | 0,046 mm |

  A auditoria, elemento a elemento, sobre o mapa inteiro:

  - **6 elementos novos**, e exatamente os esperados: `hd-t0`, `hd-i0` e `ft-p0..3`.
  - **Nenhum elemento sumiu**, nenhum mudou de página, e **nenhum mudou de `x`**.
  - **Página 0: todos os 270 elementos desceram exatamente 18,00 mm** — a altura do cabeçalho, seis
    passos da grade. Páginas 1, 2 e 3: deslocamento **zero** nos 375 elementos.
  - As coordenadas normalizadas das bolhas ficaram **idênticas**, com a região 18 mm mais abaixo.
    É §6 funcionando: dentro da região tudo é `(u,v)` do quadrilátero, então mover a região inteira
    não move nada que o OMR consome.

  **Uma folga que encolheu, e vale registrar antes que alguém a descubra vermelha.** A fidelidade
  passou de 0,039 mm para 0,046 mm em `marcador 0: borda superior`, contra tolerância de 0,05 mm.
  Não é geometria: medido no raster, a tinta do marcador começa em 32,0252 mm com valor 153 —
  cobertura parcial de pixel — e a primeira linha abaixo do corte de 128 é a de 32,0463 mm. O
  desvio é o corte binário do detector de borda encontrando outra fase de pixel, e ele muda sempre
  que a geometria se desloca. **A tolerância de 0,05 mm de ADR-0001 tem hoje ~2 px de viés
  sistemático embutido**, e um deslocamento futuro pode empurrá-la para vermelho por
  arredondamento, e não por defeito. Está fora do escopo desta fatia consertar o detector; fica
  registrado e levantado ao fim.

## 4. Legibilidade do gabarito

- [x] 4.1 Agrupar as linhas do gabarito em grupos de 3 a 5 questões e aplicar a faixa de 45‰ a grupos alternados (§7). Resultado: cobre "Grupos de tamanho declarado" e "Faixa alternada entre grupos vizinhos".

  O teste varre **todas** as provas de 6 a 30 questões, e não a fixture: agrupamento é aritmética
  com resto, e resto é onde ele erra. Ele achou o erro na primeira execução.

  **O tamanho do grupo é por coluna, e não da grade.** A primeira versão calculava um tamanho a
  partir de `grid.rows` e o aplicava a todas as colunas. Numa prova de 11 questões em duas colunas
  — 6 linhas na primeira, 5 na segunda — a segunda coluna terminava com um grupo de **2 linhas**,
  que não é grupo, é sobra. Com o tamanho calculado por coluna, toda coluna com 3 linhas ou mais
  fecha em grupos de 3 a 5: 11 vira 4+4+3, 7 vira 4+3, 13 vira 5+5+3.

- [x] 4.2 Imprimir a letra da alternativa dentro do círculo, no tom declarado. Resultado: cobre "Letra dentro do círculo".

  Corpo de dois terços do texto, tom 400‰, centrada pela largura medida — pelo mesmo medidor do
  resto da folha. O teste afirma que a caixa da letra cabe no diâmetro **interno** do círculo,
  descontado o traço, e que o tom é mais claro que o preto pleno do corpo.

- [x] 4.3 Provar que a decoração não moveu geometria: comparar o mapa com decoração ao mesmo mapa sem ela e afirmar que toda coordenada normalizada de bolha, moldura e marcador é idêntica. Resultado: cobre "Decoração não move geometria" — a afirmação que separa mitigação cosmética de mudança de geometria publicada.

  Duas verificações, e a segunda é a que faltava nesta base desde a fatia 1:

  - **A auditoria da regravação** (4.4) mostra `regions` **idêntico** byte a byte antes e depois da
    decoração, e zero elementos preexistentes alterados.
  - **`toda bolha declarada coincide com o circulo desenhado`**, teste novo nos três alvos. O OMR
    lê a coordenada **declarada**; o aluno marca o círculo **desenhado**. Nenhuma verificação desta
    base olhava os dois lados ao mesmo tempo: o golden compara o mapa consigo mesmo, a fidelidade
    compara o documento com o mapa, e a paridade compara os dois documentos. Se a declaração e o
    desenho se separassem, a folha pareceria correta e a leitura sairia deslocada.

- [x] 4.4 Regravar golden e pacote com esta causa só. Resultado: segunda regravação da fatia, auditada como a primeira.

  | Grandeza | Antes | Depois |
  |---|---|---|
  | `…layout.json` | `e2deb45d…`, 76 240 bytes | `a7ade465…`, 91 798 bytes |
  | `…package.json` | `24fcaaa2…` | `2669ac35…` |
  | Páginas · fidelidade | 4 · 0,046 mm | 4 · **0,046 mm** |

  - **164 elementos novos, e exatamente os previstos:** 4 faixas (uma por coluna do gabarito) e 160
    letras — 40 questões × 4 alternativas.
  - **Zero elementos preexistentes alterados**, nenhum sumiu, e a decoração ficou só na página 0.
  - **`regions` idêntico**: nenhuma coordenada normalizada de bolha e nenhum marcador se moveu.
  - O documento inteiro declara **uma** trama (45‰) e **um** tom (400‰).

  A fidelidade não se mexeu, e isso não é sorte: a 1200 dpi a faixa rasteriza em 244 e a letra em
  153, e o detector de borda corta em 128 — as duas ficam do lado claro do corte, que é o que
  D-2b.4 pediu. O corte da **paridade** é outro (escuridão acima de 8, isto é, valor abaixo de
  247), e por isso ela precisa da tarefa 5.4 antes de voltar a valer.

## 5. O orçamento de tinta

- [x] 5.1 Registrar o ADR do orçamento de tinta decorativa **antes** de qualquer medição (ADR-0007, D-2b.3). Resultado: o ADR declara a grandeza medida, o teto da decoração, o piso da caneta, o corredor que a fatia 3 herda e o que cede se a medição reprovar.

  `docs/adr/0010-orcamento-de-tinta-decorativa-na-regiao-escaneavel.md`, escrito **antes** de a
  primeira cobertura ser medida: decoração ≤ 12%, caneta ≥ 50%, corredor de 20% a 40% para o limiar
  da fatia 3, e a decoração cedendo se a medição reprovar — tom da letra, trama da faixa, letra
  fora do círculo, nessa ordem.
- [x] 5.2 Declarar orçamento, teto de tom e corredor do limiar no cabeçalho da região escaneável do `LayoutMap`, e fazer a validação recusar o que ela consegue provar sem rasterizar, identificando a bolha (D-2b.2, D-2b.3.1). Resultado: cobre "Orçamento declarado no mapa", "Trama dentro da bolha acima do orçamento é recusada", "Elemento opaco dentro da bolha é recusado" e "A folha de referência é aceita".

  ```json
  "ink_budget":{"decorative_max":120,"decorative_tone_max":500,"threshold_floor":200,"threshold_ceiling":400}
  ```

  **Esta tarefa mudou de forma no meio, e a causa foi uma medição.** A spec original mandava a
  validação recusar mapa cuja cobertura passasse do orçamento. Implementando, ficou claro que o
  `FontProgram` não lê contorno de glifo — só avanço e espaçamento —, então o único limite que a
  validação consegue **provar** para uma letra é "a caixa inteira é tinta". Medido na folha de
  referência, esse limite dá **20,43%** para bolhas cuja tinta real, medida no raster a 1200 dpi, é
  **7,24%**: um validador honesto recusaria a folha que o raster aprova. O requisito foi reescrito
  (D-2b.3.1), com o consentimento de quem pediu a fatia, e a decisão é a que o próprio ADR-0010 já
  dizia — quem julga tinta é o raster.

  Sete testes novos. Um deles registra uma redundância que vale saber: com o orçamento padrão de
  120‰, **o teto de trama chapada de §7 (80‰) é mais apertado e dispara antes** — o ramo do
  orçamento só deixa de ser redundante quando uma região declara orçamento menor que o teto, que é
  como o teste o exercita. E há o par positivo: texto em preto pleno **longe** das bolhas — o
  rodapé, por exemplo — continua aceito, sem o qual a guarda poderia estar recusando a folha
  inteira.

  Terceira regravação do golden, causa única e auditada: `pages` **idêntico byte a byte**, a região
  ganhou **só** `ink_budget`, e todo o resto dela ficou igual. `…layout.json` `a7ade465…` →
  `3e31c36b…` (+108 bytes); pacote `2669ac35…` → `26612ad5…`.
- [x] 5.3 Medir a cobertura de tinta dentro de **todas** as bolhas do documento rasterizado, por caminho que não compartilhe código com o engine que produziu a geometria. Resultado: cobre "Bolha vazia dentro do orçamento"; cobre explicitamente `NaN`, cobertura vazia e janela fora da página, porque `NaN > teto` é falso e passaria calado.

  `tools/parity/tinta.mjs`, ferramenta nova, em JavaScript sobre o raster — nenhuma linha
  compartilhada com o Layout Engine, que é Kotlin. O mapa entra só como declaração: onde estão as
  bolhas e quanto de tinta elas podem ter.

  Medido na folha de referência, 160 de 160 bolhas:

  | Grandeza | Valor |
  |---|---|
  | Maior cobertura | **7,24%** em `q26/D` — faixa mais letra |
  | Menor cobertura | 2,09% — letra sozinha |
  | Média | 4,71% |
  | Orçamento (ADR-0010) | 12,0% |
  | Piso do corredor do limiar | 20,0% — livre com folga de 12,8 pontos |

  O raio medido é o do círculo **desenhado menos o traço**: o que interessa é a tinta dentro da
  bolha, e não o contorno preto que a delimita. Janela vazia, fora da página ou não finita é falha
  explícita, e não `null` que passa adiante.

- [x] 5.4 Subir o piso de escuridão do centroide da paridade para acima de toda tinta decorativa (D-2b.4), e provar que ela continua acusando um deslocamento deliberado **com** faixa e letra na folha. Resultado: a medição que existe desde a fatia 1 continua medindo traço, e não decoração; visto falhar com o deslocamento de 0,5 mm que a 2a já usou.

  O piso passou de 8 para **128 de 255**, e não é número escolhido no script: ele é derivado do
  `decorative_tone_max` que o mapa declara. É a definição operante de "decorativo" — tinta que a
  paridade enxerga não é decoração, é geometria.

  Sem isso a medição da fatia 1 teria sido corrompida em silêncio: a faixa de 4,5% rasteriza com
  escuridão 11 e a letra com 102, e o piso antigo de 8 punha as duas dentro do centroide da bolha.

  **Visto acusar, com a decoração na folha:** marcador `r0-m0` 0,510 mm, bolha `r0-bq01-A`
  0,411 mm, fórmula em linha `qq01-si0-1` 0,466 mm e fórmula em bloco `qq29-f` 0,466 mm — todas
  acima da tolerância de 0,3 mm, todas apontadas.

- [x] 5.5 Ver a guarda de orçamento falhar: elevar a trama da faixa e o tom da letra até passar do teto e confirmar o vermelho apontando a bolha e a cobertura medida. Resultado: registrado com a mensagem produzida.

  `apps/web/scripts/render-inked.ts`, irmão de `render-shifted.ts`: gera documentos com defeito de
  tinta deliberado. Quatro variantes, e o resultado tem uma surpresa que vale mais que os acertos.

  | Defeito | `tinta.mjs` | Quem acusa |
  |---|---|---|
  | Faixa a 200‰ | **saída 1** — `bolha q06/A tem 21.46% de tinta, acima do orcamento decorativo de 12.0%` | orçamento no raster |
  | Faixa removida | saída 0 | paridade (tarefa 2.4) |
  | Letra a 900‰ | **saída 0** | validação do mapa, e só ela |
  | Fórmula trocada por raster vermelho | **saída 1** — `pagina 0 tem pixel cromatico em (498, 1647): rgb(255, 0, 0)` | monocromia |

  **A letra a 900‰ não estoura o orçamento, e isso está certo.** Medida, ela leva a bolha de 7,24%
  para 10,78% — ainda abaixo dos 12%, e muito abaixo do corredor que começa em 20%. Um glifo é fino:
  escurecê-lo quase ao preto não ameaça a leitura óptica, que mede **cobertura**. Quem recusa esse
  defeito é a validação do mapa, pelo teto de tom de 500‰ — e é por isso que as duas guardas
  existem, cada uma com o defeito que é seu.

  **E a prova de que a tinta era um ponto cego:** a fidelidade dimensional roda **verde** nos dois
  documentos defeituosos — faixa a 200‰ e letras a 900‰ em todas as bolhas. Ela mede geometria, e a
  geometria está intacta. Sem `tinta.mjs`, os dois defeitos chegariam ao papel com a suíte inteira
  no verde.

## 6. Monocromia e teto de trama no documento

- [x] 6.1 Verificar monocromia sobre raster **RGB**, afirmando `R = G = B` em todo pixel (D-2b.6). Resultado: cobre "Nenhum pixel cromático"; visto falhar desenhando um elemento em cor de propósito, e registrado que as guardas em cinza **não** acusaram — que é o motivo de a verificação ter mudado de espaço de cor.

  A 300 dpi: cor não precisa de resolução, precisa existir. A guarda também recusa um raster que
  volte com menos de três componentes — sem isso ela poderia "passar" por não estar medindo nada.

  **Visto falhar:** uma fórmula trocada por um PNG vermelho — o caminho realista, já que D41 guarda
  imagem em cor no Storage e gera a versão print-safe na publicação. A guarda acusou o primeiro
  pixel com coordenada e valor, e contou os 3 999. As guardas em cinza — fidelidade e cobertura de
  tinta — passaram **verdes** no mesmo documento, porque o rasterizador descarta a cor antes de
  elas olharem. É exatamente o motivo de esta verificação ter mudado de espaço de cor.

- [x] 6.2 Medir a cobertura de cada área tramada do documento e afirmar o teto de 8% sobre o que foi impresso, e não sobre o que o mapa declara. Resultado: cobre "Trama dentro do teto" e "Cor barra a integração"; visto falhar elevando uma trama acima do teto.

  Quatro tramas medidas na folha de referência, maior **4,71%** contra teto de 8% — e contra os
  4,5% declarados, distância de 0,21 ponto, dentro da tolerância de 2 pontos. A diferença é
  arredondamento de rasterização: 45‰ vira o nível 243 de 255, que é 4,71%.

  **Visto falhar** com a faixa a 200‰: a trama `r0-f0-1` foi medida em **20,39%** contra teto de
  8%, e a ferramenta saiu com código 1. A linha específica dessa recusa fica fora das 20 primeiras
  que o relatório imprime — as 65 bolhas estouradas vêm antes —, e é por isso que o relatório
  termina com `... e mais 69` em vez de esconder o resto.

## 7. A folha de teste de impressão

- [x] 7.1 Construir o `LayoutMap` da folha de teste no KMP, com as mesmas primitivas e a mesma `CaptureGeometry` da prova (D-2b.7). Resultado: cobre "Conteúdo mínimo da folha de teste" — quatro marcadores, QR, vão de referência, amostras de trama e linha de bolhas, em uma página.

  `PrintTestSheet` no domínio compartilhado, e `fixtures/folha-de-teste.layout.json` como artefato
  versionado — 6 689 bytes, 34 primitivas, uma página. Ela é golden como o da prova: os três alvos
  afirmam byte a byte que produzem a mesma folha, porque a folha que **aprova uma impressora** não
  pode ser diferente em cada plataforma.

- [x] 7.2 Afirmar por teste que lado do marcador, zona de silêncio, diâmetro e passos das bolhas da folha de teste são os mesmos da prova. Resultado: cobre "Mesma geometria de captura da prova" — a afirmação sem a qual a folha aprovaria a impressora por um caminho que a prova não percorre.

  O teste compara os dois mapas **entre si**, e não cada um contra uma constante: lado e módulo do
  marcador, diâmetro e traço da bolha, lado do QR. O passo horizontal é medido nos centros
  desenhados, e não lido da constante — se o desenho e a constante divergissem, ler a constante
  concordaria com o erro.

- [x] 7.3 Imprimir na própria folha o que aprova e o que reprova cada conferência, incluindo o comprimento esperado ao lado do vão de referência. Resultado: cobre "Critério impresso na própria folha".

  Cinco conferências numeradas na folha — vão, marcadores, QR, tramas, bolhas — cada uma com o que
  aprova e o que reprova, mais a linha final: reprovou em qualquer uma, esta impressora não está
  apta. O vão traz `180,0 mm` e a faixa `171,0 a 189,0 mm`, que é o ±5% de ADR-0001 medido no papel.

  O critério mora na folha, e não neste repositório, porque quem confere está com o papel na mão.
  O teste afirma que ele está lá — inclusive a frase de reprovação.
- [x] 7.4 Gerar o documento da folha de teste nos dois renderizadores e submetê-lo à paridade e à fidelidade junto com a prova. Resultado: cobre "Folha de teste sujeita às mesmas guardas".

  `render-test-sheet.ts` no web e um teste instrumentado novo no Android, rodado no emulador
  `platos-atd34` (API 34) — os dois desenham o mesmo artefato versionado, sem raster nenhum, o que
  também afirma que a folha de teste não depende de recurso externo.

  | Documento | Paridade | Fidelidade | Tinta |
  |---|---|---|---|
  | Prova | 185 de 185, **0,048 mm** | web 0,046 mm · Android 0,042 mm | 7,24% · 6,88% |
  | Folha de teste | 9 de 9, **0,044 mm** | web 0,046 mm · Android 0,017 mm | 2,87% nas duas |

  As tramas também foram comparadas entre plataformas: divergência de **0,39 ponto** nas duas
  folhas, que é exatamente **um passo de quantização** de 8 bits — o web resolve 45‰ no nível 243 e
  o Android no 244. Dentro da tolerância de 1 ponto, e pela razão certa.

## 8. O papel

- [x] 8.1 Imprimir a prova e conferir a olho: faixa visível sem competir com o texto, letra legível dentro do círculo sem parecer resposta, marcadores íntegros. Resultado: registrado; se o tom precisar mudar, muda dentro do orçamento e o golden é regravado com essa causa — é o que as fatias 1.5 e 1.6 mostraram que só o papel decide.

  **Conferido no papel em 2026-08-22.** Tudo legível. A faixa aparece e não compete com o texto;
  as letras dentro dos círculos são perfeitamente legíveis e claramente não são marcações — as
  duas condições ao mesmo tempo, que é o que §9 do protocolo pede. Nenhum tom precisou mudar,
  logo **não há regravação de golden por esta tarefa**. Na folha de teste, as amostras de trama de
  4,5% e 8% apareceram ambas (conferência 4, parte da visibilidade).
- [x] 8.2 Executar a folha de teste de verdade na impressora de referência, **e tentar reprová-la**: imprimir uma segunda vez com "ajustar à página" ligado e confirmar que a folha reprova por escala. Resultado: cobre "Impressora fora de escala é reprovada"; uma folha de teste que nunca reprovou ninguém é cerimônia.

  **Primeira metade feita em 2026-08-22; a segunda foi feita por outro caminho, porque o caminho
  escrito não existe.** A folha de teste foi impressa e passou nas cinco linhas: vão de
  referência **175,0 mm** medido com régua, os quatro marcadores íntegros, QR decodificado em
  condições adversas de luz e nitidez (`folha-de-teste-de-impressao...0.9F7D`, e
  `prova-referencia-slice-1...0.05CB` na prova), as duas tramas visíveis e distintas, e os cinco
  círculos fechados com a letra legível. Aprovada.

  **A impressão saiu a ~97%, e isso é aprovação e não reprovação** — ADR-0001 fixa a faixa normal
  de reescala em ±5% justamente porque três medições anteriores deram de −3,4% a +4,7% com o
  driver em 100%. Duas evidências independentes, e elas concordam:

  | Método | Medida | Escala implícita |
  |---|---|---|
  | Régua sobre o vão de referência | 175,0 mm, nominal 180,0 | **97,2%** |
  | Vão dos centros de ArUco ÷ largura do papel, na digitalização da prova | 1646,3 px ÷ 2155 px = 0,7639, nominal 166 ÷ 210 = 0,7905 | **96,6%** |
  | O mesmo, na digitalização da folha de teste | 1658,3 px ÷ 2156 px = 0,7692 | **97,3%** |

  A razão entre os dois vãos **não depende do dpi**, que era o dado que faltava: o papel A4 tem
  210 mm quer o scanner saiba disso ou não. A borda esquerda da folha encosta na moldura nas duas
  imagens, então a largura em pixels é piso e as duas estimativas são teto — o que as põe ainda
  mais perto dos 97,2% da régua. Marcador impresso: 14,0 × 0,972 = **13,6 mm**, acima do mínimo de
  12 mm que §7 exige e que ADR-0001 manda preservar sob reescala.

  **O método prescrito não reprova, e não deveria.** "Ajustar à página" de A4 para A4 encolhe
  justamente esses ~3%, que é *dentro* da faixa que ADR-0001 declara normal: reprovar ali seria
  reprovar a impressão que o ADR aprova. Sair da faixa exigiria escala explícita de 90% ou papel
  Letter — e **não há impressora disponível**, as impressões desta fatia foram terceirizadas sem
  controle sobre o driver. A conferência de papel foi dada por impossível, e a pergunta que ela ia
  responder foi respondida por outro caminho.

  **A pergunta é "esta faixa reprova alguém?", e ela não precisa de impressora.** O que poderia
  estar errado sem ninguém notar não é a régua da pessoa: é a faixa impressa ser larga demais para
  pegar o erro que existe no mundo. Teste novo, `PrintTestSheetTest.a faixa impressa separa
  reescala normal de reescala que reprova`, que **lê a faixa do texto impresso na própria folha** e
  a confronta com a população real de reescala:

  | Caso | Vão impresso | Veredito exigido |
  |---|---|---|
  | −5%, a ponta inferior de ADR-0001 | 171,0 mm | aprova |
  | +5%, a ponta superior de ADR-0001 | 189,0 mm | aprova |
  | A impressão de referência de 2026-08-22 | 175,0 mm | aprova |
  | **A4 em papel Letter com "ajustar à página"** | **169,3 mm** | **reprova** |
  | Escala explícita de 90% | 162,0 mm | reprova |
  | Escala explícita de 110% | 198,0 mm | reprova |

  O caso que importa é o do papel Letter: `min(215,9/210 ; 279,4/297) = 94,07%`, e a altura é quem
  limita. É o erro de escala que de fato acontece — imprimir A4 numa bandeja de Letter — e ele cai
  **fora** da faixa por 1,7 mm. A folha reprova quem merece ser reprovado e aprova o encolhimento
  de 3% que o ADR absorve. Não é cerimônia.

  **Visto falhar:** com a faixa impressa alargada para ±10% (162,0 a 198,0 mm), o teste fica
  vermelho em `A4 em Letter mede 169333um e a folha aprovaria — o erro de papel passaria batido`.
  Revertido em seguida. Os outros dois vermelhos da mesma execução são consequência esperada de
  mexer no texto da folha: a conferência da faixa de ±5% e o golden byte a byte.

  **O que fica sem cobertura, dito com todas as letras:** ninguém viu uma pessoa com uma régua na
  mão rejeitar uma folha ruim. O teste prova a aritmética do critério, não o gesto. É risco baixo —
  a instrução está impressa na folha e a comparação é de dois números — mas é risco, e a primeira
  impressão feita em impressora própria deve repetir a conferência de propósito.

  Uma coisa que a segunda metade *já* cobriu, por máquina: `papel.mjs` leu os 7×7 módulos de cada
  um dos quatro marcadores nas duas folhas digitalizadas e comparou com o declarado no mapa —
  **196 módulos por folha, zero divergências**. É a conferência 2 da folha de teste feita sem
  depender do olho, e é o que a linha "Marcador incompleto reprova" pedia: um borrão que una dois
  módulos, ou uma falha branca dentro de um módulo preto, troca um bit e o bit trocado aparece.
- [x] 8.3 Preencher bolhas a caneta na folha impressa, digitalizar e medir a cobertura, comparando com o piso declarado em 5.1. Resultado: valor observado registrado — e, se ficar abaixo do piso, é a decoração que cede, conforme o ADR.

  **Medido em 2026-08-23**, sobre a prova impressa com 40 questões respondidas a caneta e
  digitalizada, mais a folha de teste com uma bolha preenchida. Ferramenta nova:
  `tools/parity/papel.mjs`.

  ```bash
  node tools/parity/papel.mjs <digitalizacao.jpg> fixtures/prova-referencia.layout.json
  ```

  | Grandeza | Prova (160 bolhas) | Folha de teste (5 bolhas) |
  |---|---|---|
  | Caneta — mínimo | **51,61%** (`q22/A`) | 57,15% |
  | Caneta — mediana · média · máximo | 60,97% · 60,94% · 72,20% | — |
  | Vazia — máximo | **9,20%** (`q29/B`) | 8,51% |
  | Vazia — mediana · média | 5,29% · 5,58% | 7,96% · 7,13% |
  | Piso da caneta (ADR-0010) | 50% | 50% |
  | Orçamento decorativo (ADR-0010) | 12% | 12% |
  | Corredor observado | de 9,20% a **51,61%** — 42,41 pontos livres | 48,64 pontos |
  | Corredor declarado | 20% a 40%, com 10,8 pontos de folga embaixo e 11,6 em cima | idem |

  **O piso se sustenta, e o corredor se sustenta com folga — mas o pior traço encostou no piso.**
  `q22/A` é uma rabisco que não fecha o círculo (conferido na imagem), e ela mede 51,61%: 1,6
  ponto acima do piso que ADR-0010 declarou antes de qualquer medição. Nenhuma decoração precisa
  ceder — a condição do ADR é caneta abaixo de 50%, e ela não ocorreu. O que ficou registrado é
  que a margem no pior caso é fina, **numa digitalização de mesa**, que é condição melhor do que a
  câmera de celular da fatia 3.

  O número que a fatia 3 herda não é o piso, é a **separação**: nenhuma bolha vazia passa de 9,20%
  e nenhuma marcada desce de 51,61%, e o corredor de 20% a 40% cai inteiro no vazio entre as duas
  nuvens. É a garantia que o design.md prometeu — "que exista um corredor largo entre decoração e
  caneta" — agora medida em papel.

  **A folha impressa tem mais tinta decorativa que o PDF, e continua dentro do orçamento.** A
  bolha vazia mais escura mede 7,24% no raster a 1200 dpi (tarefa 5.3) e **9,20%** no papel
  digitalizado: espalhamento de toner mais borrão do scanner engordam o glifo e a trama. A
  diferença é de 2 pontos e o teto é 12% — mas ela mostra que medir só o PDF subestima, e que o
  orçamento precisava mesmo da folga que tem.

  **Uma propriedade da digitalização que a fatia 3 vai reencontrar:** este scanner comprime a
  faixa dinâmica. O papel lê 233 de 255 e o miolo de um módulo preto de ArUco lê **83** — preto
  pleno de toner rende no máximo 64% de cobertura nesta imagem. As canetas passaram disso (até
  72,20%) porque a tinta da esferográfica lê mais escura que o toner. A consequência prática: um
  limiar de OMR fixado em valor absoluto de pixel não sobrevive à troca de scanner; o que
  sobrevive é cobertura normalizada contra o branco local, que é o que `papel.mjs` faz e o que a
  fatia 3 precisa fazer.
- [x] 8.4 Atualizar `docs/protocolo-medicao-impressa.md` com a seção da folha de teste, a inspeção de trama a olho e o que fazer quando uma impressora reprova. Resultado: o protocolo passa a ter um artefato próprio para a pergunta "esta impressora serve?".

  Duas seções novas. A **3.5** vem antes das medições de régua e responde "esta impressora serve?",
  com a tabela do que cada elemento da folha de teste existe para pegar — e sem repetir os
  critérios, que estão impressos na folha: critério que mora no repositório é critério que ninguém
  lê na hora. A **9** separa o que a máquina já garante (cobertura, teto de trama, monocromia, tudo
  no CI) do que só o papel decide: se a faixa aparece numa impressora de toner fraco, se ela
  atrapalha, e se a letra é legível **e** claramente não é uma marcação — as duas ao mesmo tempo.

## 9. Verificação e registro

- [x] 9.1 Rodar a suíte nos três alvos e em `apps/api`, mais paridade e fidelidade nos dois documentos, e comparar com a base da tarefa 1.1. Resultado: tabela final ao lado da inicial, com toda diferença explicada por uma causa desta fatia.

  `./gradlew build :packages:domain:testAndroidHostTest --rerun-tasks`, mais `npm test` no web e as
  três ferramentas de `tools/parity`.

  | Alvo | Base (1.1) | Fim da fatia | Diferença |
  |---|---|---|---|
  | `packages/domain` JVM | 197 | **228** | +31 |
  | `packages/domain` Node/JS | 192 | **222** | +30 |
  | `packages/domain` Android (host) | 192 | **222** | +30 |
  | `apps/api` (Postgres real) | 89 | **89** | — |
  | `apps/android` (host) | — | 9 | — |
  | `apps/web` | 12 | **14** | +2 |

  Falhas: **zero** em todos. Os 30 testes comuns novos rodam nos três alvos; o trigésimo primeiro
  do JVM é o escritor da folha de teste versionada, que só existe lá. O trigésimo comum entrou
  depois desta contagem, ao fechar a tarefa 8.2 sem impressora: `a faixa impressa separa reescala
  normal de reescala que reprova`.

  | Medição | Base (1.1) | Fim da fatia |
  |---|---|---|
  | Paridade da prova | 185 de 185, 0,042 mm | 185 de 185, **0,048 mm** |
  | Fidelidade web · Android | 0,039 · 0,022 mm | **0,046 · 0,042 mm** |
  | Paridade da folha de teste | — | 9 de 9, **0,044 mm** |
  | Tinta na bolha (web · Android) | — | **7,24% · 6,88%**, orçamento 12% |
  | Trama entre plataformas | — | **0,39 ponto**, tolerância 1,00 |
  | `…layout.json` | `ceaf67cc…`, 69 945 B | `3e31c36b…`, 91 896 B |
  | `…package.json` | `61c96f4c…` | `26612ad5…` |
  | `folha-de-teste.layout.json` | — | 6 689 B |

  **Toda diferença tem causa nomeada.** Os desvios de fidelidade subiram porque a geometria desceu
  18 mm e o detector de borda encontrou outra fase de pixel (ver 3.3) — não é geometria, é o corte
  binário do detector, e continua dentro de 0,05 mm. A paridade subiu de 0,042 para 0,048 mm porque
  o piso de escuridão do centroide passou de 8 para 128 (5.4): a medição passou a pesar só o núcleo
  do traço, o que é mais exigente e mais correto.
- [x] 9.2 Escrever `docs/cobertura-fatia-2b.md`: cada cenário das duas specs mapeado para a verificação que o cobre, e cada verificação crítica com **como ela foi vista falhar**. Resultado: nenhum cenário sem verificação, e nenhuma verificação crítica sem vermelho observado.

  29 cenários: **18 de `layout-engine`, todos cobertos**, e **11 de `print`, 10 cobertos por
  máquina e 3 pendentes de papel** (dois cenários caem nas tarefas 8.1–8.3). A tabela de defeitos
  deliberados traz sete linhas, e cada uma registra também **o que as outras verificações
  disseram** — que é onde está a lição da fatia: a faixa a 200‰ e as letras a 900‰ passam verdes na
  fidelidade, e a faixa ausente passa verde nos 185 centroides.

  O documento fecha com a lista do que **não** está coberto, nomeada e com a razão: nenhuma das
  três conferências de papel tem oracle dentro do sistema.
- [x] 9.3 Atualizar a arquitetura: o risco "Impressão dos ArUcos" de §16 alcançou sua fatia-limite e muda de estado, e as decisões novas entram no registro de §17. Resultado: a lista de adiados encolhe quando o prazo chega, em vez de crescer.

  - **§15** — a linha da 2b passa a dizer o que a fatia entregou, e o que ela valida é "a qualidade
    de impressão, provada pelo que ela consegue reprovar".
  - **§16, ponto de não-retorno** — "Impressão dos ArUcos" passa a **2b — alcançada**: a mitigação
    existe e é anterior à folha distribuída, que é o que encarece agora.
  - **§16, tabela de riscos** — a linha do risco registra o que foi entregue: marcador de 14 mm e
    uma folha de teste que é um `LayoutMap` do mesmo engine, com critério impresso nela.
  - **§17** — **D55** (orçamento de tinta decorativa, ADR-0010) e **D56** (tom e trama em
    permilagem; quem julga cobertura é o raster, não a validação sem renderizar).
- [x] 9.4 Rodar `openspec validate --strict` na mudança e conferir que as duas specs delta aplicam sobre as specs vigentes. Resultado: mudança pronta para arquivar.

  `openspec validate --all --strict`: **6 de 6**, as cinco specs vigentes e a mudança. `openspec
  doctor`: raiz ok.

  As duas deltas aplicam. Os dois `MODIFIED` casam com cabeçalho existente e são substituição
  **completa** — trazem todos os cenários que a spec vigente já tinha, mais os novos:

  | Delta | Requisito modificado | Cenários vigentes | Novos |
  |---|---|---|---|
  | `layout-engine` | Validação do `LayoutMap` sem renderizar | 4, todos preservados | Trama acima do teto · Tom fora da faixa |
  | `print` | Renderização derivada exclusivamente do `LayoutMap` | 4, todos preservados | Tom desenhado como declarado |

  Nenhum dos 8 requisitos `ADDED` colide com cabeçalho existente. Contagem de cenários novos: **18
  em `layout-engine`** (16 nos requisitos novos, 2 no modificado) e **12 em `print`** (11 nos novos,
  1 no modificado), que é o que `docs/cobertura-fatia-2b.md` mapeia.

  **Uma incoerência achada e corrigida na conferência.** O resumo do requisito modificado de
  `layout-engine` dizia que a validação sem renderizar verifica "tinta decorativa dentro do
  orçamento declarado da região" — e o requisito novo, três parágrafos acima, diz o contrário com
  todas as letras: cobertura de glifo não é verificável sem rasterizar (D-2b.3.1). Duas afirmações
  opostas na mesma spec são o convite para alguém "consertar" o validador de volta para o limite
  que recusaria a folha correta. O resumo passou a dizer **tinta chapada** decorativa, que é o que
  o código faz e o que o outro requisito permite provar.

  Estado da suíte na conferência final, com `./gradlew build :packages:domain:testAndroidHostTest`
  e `npm test` no web:

  | Alvo | Testes | Falhas |
  |---|---|---|
  | `packages/domain` JVM · Node · Android host | 228 · 222 · 222 | 0 |
  | `apps/api` (Postgres real) | 89 | 0 |
  | `apps/android` (host) | 9 | 0 |
  | `apps/web` | 14 | 0 |

  Paridade, fidelidade e tinta não foram reexecutadas, e a razão é verificável: os três hashes de
  fixture continuam `3e31c36b…`, `26612ad5…` e `81ce134d…`, e nenhuma linha de engine ou de
  renderizador mudou depois da tarefa 9.1 — a única mudança de código desta rodada é um teste em
  `commonTest`.
