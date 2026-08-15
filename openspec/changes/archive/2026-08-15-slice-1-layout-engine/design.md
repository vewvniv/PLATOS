## Context

Ver `proposal.md — Why`. Restrições que moldam o desenho:

- O repositório tem hoje só `apps/api` (Ktor/jOOQ, fatia 0). Não existe módulo KMP, `apps/web` nem `apps/android`. Esta fatia cria os três e vira precedente para as fatias 2–7.
- Requisitos desta fatia: `specs/layout-engine/spec.md` e `specs/print/spec.md`.
- `ARQUITETURA-FINAL-v3.md` §6 fixa: `LayoutMap` como função pura calculada uma vez e persistida, coordenadas normalizadas ao quad, medição própria em Kotlin puro, fonte embarcada (D36), guarda de versão (D24), paridade em CI com tolerância de 0,3 mm. §7 fixa a grade de 3 mm, as margens e a geometria de captura. §13 fixa `packages/domain` em KMP e `pdf-lib` / `PdfDocument` como renderizadores.
- A decisão central desta fatia **não é o algoritmo de paginação** — é **em que aritmética o layout é calculado**. Ponto flutuante em três alvos Kotlin diferentes é a única forma realista de a medição divergir sem ninguém perceber, e é irreversível depois que a fatia 2 congelar pacotes com hash.

## Goals / Non-Goals

**Goals**

- Tornar a divergência entre plataformas **impossível por construção** no cálculo, e apenas **detectável** no desenho — que é onde ela é inevitável.
- Deixar o `LayoutMap` com forma final, para que a fatia 2 só precise persistir e empacotar, sem redesenhar geometria.
- Ter a verificação de paridade rodando em CI desde o primeiro commit do Layout Engine, não depois.
- Produzir papel real medível nesta fatia, porque nenhum teste automatizado captura toner fraco e escala de impressora.

**Non-Goals**

- Desempenho do cálculo. Com N ≤ 60 blocos qualquer implementação razoável é milissegundos.
- UI de autoria, preview ao vivo e contador de páginas. `apps/web` nesta fatia é o mínimo para gerar e baixar um PDF a partir de um `LayoutMap`.
- Persistência. Nenhuma migration, nenhuma tabela, nenhum endpoint. `apps/api` não é tocada.
- Consumo do artefato Kotlin/JS pelo build do Vite. Ver D-1.6.

## Decisions

### D-1.1 — `packages/domain` como único módulo KMP, com alvos JVM, Android e JS

Um módulo só, com praticamente tudo em `commonMain`. Medição, grade, agrupamento, paginação, `LayoutMap`, primitivas e validação não têm nenhuma parte dependente de plataforma — e é exatamente isso que dá a garantia. `expect/actual` fica reservado para o que for genuinamente de plataforma; nesta fatia, nada é.

*Alternativas descartadas:* um módulo KMP por preocupação (`measurement`, `layout`, `contracts`) — abstração prematura, sem consumidor que justifique a fronteira. Biblioteca JVM consumida por Android e um port em TypeScript para a web — é precisamente a divergência que a arquitetura rejeitou em D2.

### D-1.2 — Toda a aritmética de layout em inteiros, em micrômetros

Nenhum `Float` ou `Double` participa do cálculo do `LayoutMap`. Comprimentos são `Int` em micrômetros (µm); a grade de 3 mm é `3000`; a bolha é `4200`; A4 é `210000 × 297000`. Coordenadas normalizadas são `Int` em partes por milhão, `0..1_000_000` — o que satisfaz o intervalo `[0,1]` da spec com representação exata.

A razão é dura: `Float` em Kotlin/JS é emulado sobre o `number` de 64 bits do JavaScript, então a mesma expressão pode arredondar diferente de JVM e Android. Divisões usam divisão inteira com regra de arredondamento explícita, e a serialização não emite nenhum número fracionário — o que também torna o `LayoutMap` comparável byte a byte sem tolerância.

*Alternativas descartadas:* `Double` com épsilon nas comparações — empurra a divergência para dentro do mapa persistido, e a fatia 2 vai tirar hash disso. `BigDecimal` — não existe em `common`, e resolve com custo o que inteiro resolve de graça.

### D-1.3 — Medição por parse do TTF embarcado, com kerning opcional e explícito

O parser lê `head` (unitsPerEm), `cmap` (formato 4, caractere → glifo), `hhea`/`hmtx` (avanço por glifo) e, se presente, `kern` formato 0 (pares). A largura de um texto é a soma dos avanços mais os ajustes de par, convertida de unidades de fonte para µm por aritmética inteira.

A fonte é um TTF serifado sob licença OFL, embarcado em `commonMain/resources/fonts/` e identificado por nome + versão + hash. **Se a fonte escolhida não tiver tabela `kern` legada, o kerning é zero** — determinístico nos três alvos, tipografia levemente pior. Não implementamos GPOS nesta fatia: é o subsistema mais caro do OpenType e o ganho é estético, não geométrico.

*Alternativas descartadas:* usar a API de medição de cada plataforma — é a divergência que §6 chama de keystone. Pré-computar uma tabela de larguras e embarcar como recurso — some com o TTF do PDF e quebra a fonte embarcada exigida por D36.

### D-1.4 — Paginação por DP sobre slots de coluna, com o gabarito reservado antes

Duas colunas fixas por página (D32 adaptativo fica fora desta fatia). A região `ANSWER_BLOCK` é posicionada primeiro, no topo da página 1, atravessando as duas colunas, e reduz a altura útil dos dois slots dessa página.

Os blocos indivisíveis (enunciado + alternativas) são então distribuídos por programação dinâmica sobre a sequência de slots de coluna, minimizando `Σ(sobra_do_slot)²`. Estado é o índice do bloco, transição é "quantos blocos entram neste slot": O(N²) com N ≤ 60.

Sobra é medida por slot de coluna, e não por página, porque com colunas fixas é o slot que tem altura útil — medir por página deixaria uma coluna cheia e outra vazia empatada com duas meias colunas, que é visualmente pior.

### D-1.5 — `LayoutMap` serializado em JSON canônico com `kotlinx.serialization`

Ordem de campos e de coleções fixa, sem números fracionários (D-1.2), sem espaços supérfluos. Isso torna a asserção de paridade de cálculo uma comparação de bytes, e deixa o mapa pronto para a fatia 2 tirar hash sem mudar formato.

O mapa carrega `layout_engine_version` e `min_renderer_version` (D24), ambos inteiros, começando em `1`.

### D-1.6 — `apps/web` consome `LayoutMap` como JSON; a paridade do alvo JS é verificada dentro do KMP

O renderizador web recebe um `LayoutMap` já calculado e o desenha com `pdf-lib`. Nesta fatia ele vem da fixture, gerado pelo alvo JVM em tempo de build. Isso é fiel a §6 — o mapa é calculado uma vez na publicação e persistido; o renderizador é projeção — e evita amarrar o build do Vite ao empacotamento npm do Kotlin/JS logo no primeiro módulo KMP do projeto.

O alvo JS **existe e é testado**: o teste de paridade de cálculo roda o Layout Engine em JVM e em JS sobre a mesma fixture e afirma bytes idênticos. Ou seja, ganhamos a garantia sem pagar a integração de toolchain agora. Quando a autoria precisar de preview ao vivo (fatia 6+), publicar o artefato npm é aditivo e não muda o `LayoutMap`.

*Alternativa descartada:* `apps/web` importando o artefato Kotlin/JS já nesta fatia — mais superfície de build para o mesmo resultado geométrico, no momento em que o projeto ainda não tem nenhuma UI.

### D-1.7 — Paridade por rasterização, com o **mesmo** rasterizador para os dois PDFs

Os dois renderizadores produzem PDF; ambos os PDFs são rasterizados **na máquina de CI, pelo mesmo rasterizador**, a 600 dpi (0,042 mm/pixel, uma ordem de grandeza abaixo da tolerância de 0,3 mm). Os centroides de marcadores ArUco e de bolhas são extraídos das duas imagens e comparados.

Usar o mesmo rasterizador nos dois lados é o ponto: se cada plataforma rasterizasse a sua, o teste mediria a diferença entre rasterizadores, não entre renderizadores — e falharia ou passaria pelo motivo errado.

O PDF do Android é produzido por teste instrumentado em emulador e exportado como artefato; o do web, por Node com `pdf-lib`. A comparação roda em um job separado do build principal, porque o emulador domina o tempo do pipeline.

*Alternativa descartada:* comparar a geometria lendo os content streams dos dois PDFs — é mais preciso e mais rápido, mas §6 especifica rasterização, e a rasterização também pega classes de erro que o content stream esconde (unidade de página, matriz de transformação, clipping).

### D-1.8 — Verificação física como protocolo documentado com resultado registrado

Nenhum teste captura escala de impressora, toner fraco ou deriva de alimentação. A folha é impressa em A4 a 100% sem ajuste, medida com régua nos pontos que a spec fixa (diâmetro e passos das bolhas, lado do ArUco, margens) e o **valor observado** é registrado na tarefa correspondente — mesmo padrão que a fatia 0 usou para fechar a tarefa de CI.

### D-1.10 — Padrões de ArUco e de QR calculados no KMP e transportados no `LayoutMap`

Descoberto na implementação: posicionar um marcador é trivial, mas **desenhá-lo** exige o padrão de bits. Um ArUco é uma matriz 5×5 do `DICT_5X5_100` e um QR é uma matriz calculada a partir do payload. Nenhum dos dois existia no projeto e nenhum estava no recorte original.

Deixar cada renderizador gerar o seu com a biblioteca da plataforma — ZXing no Android, alguma lib JS na web — reintroduziria no desenho exatamente a divergência que esta fatia existe para eliminar: duas implementações podem escolher versão, máscara ou segmentação diferentes para o mesmo texto, e o teste de paridade acusaria uma diferença que é de biblioteca, não de geometria.

Então ambos são calculados em `commonMain` e viajam como matriz de módulos dentro do `LayoutMap`. Os renderizadores continuam burros: desenham quadradinhos onde o mapa manda.

- **ArUco**: o dicionário é dado, não algoritmo — o OpenCV o gera por busca gulosa com semente fixa, irreprodutível a partir da especificação. Os 100 marcadores foram vendorizados de `predefined_dictionaries.hpp` do OpenCV (Apache 2.0), empacotados em um `Int` de 25 bits cada.
- **QR**: codificador próprio, modo byte, nível de correção M, versões 1 a 10, com seleção de máscara pela penalidade da norma. O teto na versão 10 é deliberado: o payload de uma região é curto por construção (§8), e capacidade não usada é só código sem teste.

*Alternativa descartada:* emitir marcadores e QR como quadrados sólidos e adiar o padrão para a fatia 3. A geometria e a paridade de centroides ficariam corretas, mas a folha não seria escaneável — e o risco de impressão de ArUco que §16 levanta só começaria a ser testado uma fatia depois, justamente na fatia que depende dele.

### D-1.9 — Fixture e golden do `LayoutMap` versionados

A prova objetiva de referência é JSON versionado, entrada pura do Layout Engine. O `LayoutMap` esperado é commitado como golden; qualquer mudança de engine que altere geometria falha o teste e exige atualização deliberada do golden no mesmo commit. É o que impede que um refactor mova uma bolha em silêncio.

## Risks / Trade-offs

**Divergência numérica entre alvos Kotlin** → eliminada na origem por D-1.2 (inteiros em µm) e detectada por D-1.6 (JVM vs JS byte a byte). Se algum cálculo futuro precisar de fracionário, a regra é converter na fronteira do desenho, nunca dentro do mapa.

**A4 em pontos não é exato** → `PdfDocument` do Android trabalha em pontos com página inteira: A4 é 595,28 × 841,89 pt, e truncar custa até ~0,1 mm. Isso consome parte do orçamento de 0,3 mm antes de qualquer bug existir. Mitigação: declarar a página pelo tamanho em pontos arredondado uma única vez, posicionar tudo por conversão µm → pt com arredondamento explícito e igual nos dois renderizadores, e medir a folga real assim que o teste de paridade rodar a primeira vez.

**Emulador Android em CI é lento e instável** → job separado, API level fixado, cache do AVD, e falha do job não bloqueando o build do servidor. Se a instabilidade se mostrar crônica, a saída é reduzir a frequência (só em PR que toca `packages/domain` ou renderizadores), nunca remover a asserção — §16 é explícito quanto a isso.

**Fonte sem tabela `kern`** → kerning zero. Determinístico e seguro para o OMR, esteticamente inferior. Reversível: implementar GPOS depois muda o golden, e a fatia 2 ainda não terá congelado pacotes se isso for feito antes dela.

**Primeiro módulo KMP + dois app modules novos de uma vez** → é a maior expansão de superfície de build do projeto até aqui. Mitigação: `apps/web` e `apps/android` nesta fatia são casca fina de renderizador, sem navegação, sem estado e sem rede; toda a lógica testável está no KMP, que roda em JVM sem emulador nem navegador.

## Migration Plan

Puramente aditivo: nenhuma migration, nenhuma tabela, nenhum contrato exposto, nenhum consumidor existente. `apps/api` continua compilando e sua suíte continua verde sem alteração.

Reversão é reverter os commits: `packages/domain`, `apps/web` e `apps/android` saem de `settings.gradle.kts` e do workflow sem deixar estado. `layout_engine_version` nasce em `1`; qualquer mudança de geometria depois desta fatia incrementa a versão e atualiza o golden no mesmo commit.

## Open Questions

- ~~Qual TTF serifado OFL exatamente, e se ele traz tabela `kern` legada.~~ **Resolvido:** Source Serif 4 v4.005 (OFL), 262 KB contra 712 KB do Noto Serif — e a fonte é embarcada em todo PDF, no APK e no bundle web. Nenhuma das duas candidatas tem `kern` legada, só GPOS, então o kerning é zero e idêntico nos três alvos, como D-1.3 previa.
- API level e imagem de sistema do emulador para o job de paridade. Decidível na implementação do job.
