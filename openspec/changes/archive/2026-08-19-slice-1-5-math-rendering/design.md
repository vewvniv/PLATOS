## Context

Ver `proposal.md — Why`. O que a fatia 1 deixou pronto define quase todo o desenho desta:

- O `LayoutMap` já tem a primitiva `image`, declarada e **recusada** pelos dois renderizadores. Ela foi escrita na fatia 1 sem consumidor, e agora ganha um.
- Paridade a 0,3 mm, fidelidade do documento a 0,05 mm e golden byte a byte entre três alvos já existem e vão julgar esta fatia sem código novo de verificação.
- D-1.10 já resolveu, para ArUco e QR, a pergunta que volta aqui: quando um elemento precisa ser desenhado igual nos dois renderizadores, quem decide os pixels é o KMP, e nunca a biblioteca de cada plataforma.
- A spec de `layout-engine` recusa matemática por escrito. Levantar essa barreira é o núcleo da mudança, e ela se estreita em vez de sumir.

§7 já fixou a estratégia tipográfica: LaTeX/MathML → SVG na publicação, "tratado como caixa de dimensão conhecida", o que converte tipografia matemática num problema de empacotamento. O que §7 não decide é **como o desenho chega ao papel de forma idêntica nas duas plataformas**.

## Goals / Non-Goals

**Goals**

- Fórmula em bloco na folha, com paridade e fidelidade nos mesmos números de hoje.
- Nenhum runtime novo no servidor.
- O Layout Engine continua sem saber o que é matemática: ele empacota caixas.

**Non-Goals**

- Fórmula em linha. Ver "O que esta fatia deixa sem validar", abaixo — não é um recorte confortável.
- Integração com publicação real, `item_asset` e `ExamPackage` — fatia 2.
- Qualidade tipográfica além do que o MathJax entrega por padrão.
- Suporte a todo LaTeX. O que a fixture não exercitar, não está verificado.

## O que esta fatia deixa sem validar

Vale registrar com clareza, porque o recorte escolhido tem um custo real e ele não é óbvio.

§15 justifica a existência da fatia 1.5 em uma linha: "exatas antes de gerar conteúdo de exatas". O conteúdo em questão é o que a fatia 6 vai gerar por IA. Só que, na prova de ensino básico, a matemática é predominantemente **em linha**, e não em bloco. A fixture de referência já demonstra isso: 7 das 28 questões têm matemática, e todas as sete são em linha, escritas hoje como texto simples — `Qual e o valor de x na equacao 3x = 21?`.

Ou seja: esta fatia valida o **mecanismo** — converter, empacotar como caixa, desenhar igual nos dois renderizadores — mas não valida o **caso predominante**. Fórmula em linha exige caixa com alinhamento de linha de base dentro da quebra de linha, mexendo justamente no código cuja identidade entre alvos a fatia 1 acabou de garantir, e por isso não entra aqui.

**Gatilho formal:** matemática em linha é fatia própria — 1.6 — e é **bloqueadora da fatia 6**. Chegar à geração por IA com a tipografia predominante das exatas nunca tendo passado pelo Layout Engine anularia o motivo pelo qual a 1.5 foi posta antes da 2.

**Execução planejada: imediatamente após a 1.5, e antes da 2.** O gatilho formal diz o mais tarde aceitável; esta é a data desejada, e a razão é de contrato. A 1.6 precisa introduzir uma `InlineBox` — caixa atômica com largura, altura e `baseline_offset` —, e esse tipo entra na medição de texto. Resolvê-lo antes de a fatia 2 congelar os contratos do `ExamPackage` evita reabrir a medição do Layout Engine depois, quando o pipeline de OMR já estiver estabilizado sobre geometria publicada e hasheada.

## Decisions

### D-1.5.1 — O raster é derivado na conversão; o SVG segue sendo a forma armazenada

Os dois renderizadores recebem **os mesmos bytes de imagem**, e por isso a fórmula desenhada é idêntica por construção, não por concordância entre bibliotecas.

Desenhar SVG em cada plataforma com a biblioteca dela reintroduziria exatamente a divergência que a fatia 1 existe para eliminar: `pdf-lib` e o `Canvas` do Android interpretariam o mesmo arquivo por caminhos independentes, e o teste de paridade passaria a acusar diferença de biblioteca em vez de diferença de geometria.

Isso não contraria D33. O SVG continua sendo o que se armazena junto ao item; o raster é artefato de impressão derivado. A arquitetura já opera assim em outro ponto: D41 manda a publicação gerar uma versão print-safe em cinza a partir da imagem colorida armazenada. Forma armazenada e forma impressa já são coisas distintas.

O raster é gerado a **600 dpi**. A primeira versão deste desenho dizia 1200 dpi, "mesma resolução em que a fidelidade do documento é medida" — o que confunde duas coisas diferentes. Aquela medição verifica **geometria**, e por isso usa resolução alta; o raster aqui é **saída de impressão**, e 600 dpi é o que impressora doméstica e escolar entrega. As três impressoras medidas no ADR-0001 nem sequer reproduzem escala corretamente. A 1200 dpi o arquivo quadruplica sem ganho visível no papel.

*Alternativa descartada:* converter o SVG em primitivas de caminho no KMP e emitir `DrawPath`. É mais fiel ao vetor e evita resolução fixa, mas exige parser de SVG com transforms e referências de glifo — superfície grande de código novo, justamente na fatia cujo objetivo é não introduzir divergência. Fica registrada como caminho possível se a resolução do raster algum dia incomodar.

### D-1.5.2 — A conversão é ferramenta de build, não runtime de servidor

`tools/math` roda MathJax em Node e produz, por fórmula: o SVG, o raster e as dimensões. Alimenta a fixture, como `tools/parity` e o render do PDF já fazem.

§13 evita um segundo runtime **no servidor**, e é isso que fica preservado — o Ktor não passa a depender de Node. Node já existe no projeto como ferramenta (`apps/web`, `tools/parity`), então a dependência não é nova em natureza, só em uso.

Quando a fatia 2 construir a publicação, ela decide onde essa conversão roda de verdade. Antecipar isso agora seria construir integração para um pipeline que ainda não existe.

*Alternativa descartada:* implementar tipografia matemática em Kotlin, para rodar no KMP. Ordens de grandeza mais caro que tudo que a fatia 1 custou, sem retorno próximo.

### D-1.5.3 — Dimensões vêm declaradas; o Layout Engine não as descobre

A entrada traz largura e altura da fórmula em micrômetros, já resolvidas pela conversão. O engine arredonda a altura para a grade e reserva a caixa.

O engine não abre o SVG, não lê o PNG e não mede nada da fórmula. Isso mantém o cálculo do `LayoutMap` como função pura sobre inteiros (D-1.2) e preserva a comparação byte a byte entre alvos: nenhum alvo precisa decodificar imagem para calcular geometria.

### D-1.5.5 — Os bytes do raster chegam aos renderizadores pelo mesmo caminho da fonte

`DrawImage` referencia um recurso por identificador, e alguém precisa transformar isso em bytes. A fatia 1 já enfrentou exatamente este problema com o TTF embarcado, e a solução dela vale aqui: o asset é versionado em `fixtures/`, entra no KMP por task de build que o transforma em código, e chega ao Android por assets do módulo de teste.

O ponto não é conveniência, é garantia: **os dois renderizadores precisam desenhar os mesmos bytes**, e é disso que D-1.5.1 depende inteiramente. Se cada lado resolvesse a referência por conta própria — um lendo do disco, outro de assets, com caminhos diferentes — a igualdade por construção viraria igualdade por coincidência de configuração.

*Alternativa descartada:* embutir a imagem no próprio `LayoutMap` em base64, como ArUco e QR fazem com suas matrizes. §5 é explícita ao contrário: "imagens nunca entram no JSON — vão para o Storage por referência". A diferença é de tamanho: uma matriz de QR são centenas de bytes, um raster de fórmula são dezenas de milhares.

*Alternativa descartada:* cada renderizador abrindo o arquivo pelo caminho da referência. Funciona para fixture e CI, e morre na fatia 2, quando o pacote vier do Storage.

### D-1.5.4 — O golden muda, e isso é decisão, não efeito colateral

Acrescentar fórmula à fixture altera a geometria da folha de referência, então o golden do `LayoutMap` é regravado deliberadamente, no mesmo commit, como D-1.9 prevê.

É a primeira vez que o golden muda desde que foi criado. Vale registrar o antes e o depois na tarefa, para que a mudança seja auditável — um golden regravado sem justificativa é indistinguível de uma regressão aceita por engano.

## Risks / Trade-offs

**Raster preso a uma resolução** → 600 dpi é o que impressora doméstica e escolar entrega, e as três medidas no ADR-0001 sequer reproduzem escala. Se algum dia incomodar, D-1.5.1 registra o caminho vetorial. A tarefa 6.6 imprime a folha, então a decisão é conferida no papel e não só no argumento.

**Peso do arquivo** → cada fórmula vira um PNG. Numa prova com muitas fórmulas isso cresce, e a fatia 2 vai empacotar isso em `ExamPackage`. §5 já determina que imagens não entram no JSON e vão para o Storage por referência; medir o peso nesta fatia dá números para aquela decisão. A queda de 1200 para 600 dpi já corta isso a um quarto.

**MathJax mudar de saída entre versões** → a versão fica fixada no `package-lock.json` e o golden pega qualquer mudança de dimensão. Uma atualização de MathJax que mexa no traçado sem mexer nas dimensões passaria despercebida pelo golden, mas seria pega pela fidelidade do documento se deslocar a caixa.

**A barreira de entrada não suportada afrouxar demais** → o requisito modificado estreita a recusa em vez de removê-la, e ganha cenário próprio para fórmula em linha. Sem isso, a barreira viraria letra morta na próxima fatia.

## Migration Plan

Aditivo. Nenhuma migration, nenhum contrato exposto, nenhuma mudança em `apps/api`. Reverter é `git revert` mais regravar o golden anterior.

### D-1.5.6 — A fixture cobre a educação básica inteira, do EF ao 3º ano do EM

**Dentro:** aritmética, frações, raízes, potências e subscritos, trigonometria, logaritmos, vetores e módulos, somatórios simples, e as estruturas verticais comuns do EM — matrizes 2×2 e 3×3, e sistemas lineares com `cases`.

**Fora:** macros customizadas, pacotes arbitrários e gráficos por TikZ. Gráfico entra pelo fluxo de assets nativos, como imagem, e não pela compilação de fórmula.

As estruturas verticais não estão na lista por completude: matriz 3×3 e sistema com `cases` são as fórmulas mais altas do currículo, e são elas que exercitam de verdade o arredondamento à grade de 3 mm e o limite de bloco que não cabe na coluna. Uma fixture só com frações e raízes deixaria esses dois caminhos sem prova.

Cobrir a matriz do EM valida o caso de uso real agora, e não custa generalidade futura: como o Layout Engine só posiciona a caixa que o backend gerou (D33), suportar notação de ensino superior depois é configuração do parser no backend, sem tocar no KMP nem no layout.

### D-1.5.7 — Cinza de 8 bits na rasterização de verificação

A rasterização que o CI faz para comparar as duas folhas usa **cinza de 8 bits a 600 dpi, com antialiasing** — que é o que `tools/parity` já faz com `DeviceGray`.

Preto e branco puro seria mais simples e mais errado aqui: 1 bit mascara variação sutil de subpixel e de espessura de traço entre os renderizadores, que é exatamente o sinal que o teste de paridade existe para captar. Quantizar antes de medir jogaria fora a diferença que se quer detectar.

Isso vale para a **verificação**. O que vai ao PDF continua sendo o raster da fórmula definido em D-1.5.1.

### D-1.5.8 — O raster sai do `resvg`, porque o rasterizador do projeto não lê SVG

D-1.5.1 manda derivar o raster do SVG e não diz com o quê. A resposta que não custaria tecnologia nova seria o **mupdf**, que já é dependência de `tools/parity` e é o rasterizador de referência do projeto — o mesmo que julga paridade e fidelidade. Ele não serve, e isso foi medido, não suposto:

```
mupdf.Document.openDocument(svg, 'image/svg+xml')
  -> cannot find document handler for file type: 'image/svg+xml'
```

O build WASM publicado no npm não inclui o handler de SVG. Então a conversão precisa de um rasterizador próprio, e entra **`@resvg/resvg-js`** como segunda ferramenta de build — o `proposal.md` foi corrigido, porque a linha dele dizia "dependências novas: MathJax" no singular.

A escolha não é por conveniência. `resvg` rasteriza em `tiny-skia`, implementação Rust própria: não chama gráfico de plataforma e não consulta fonte do sistema. Como o MathJax roda com `fontCache: 'none'` e emite apenas `<path>`, nenhuma fonte participa do caminho — que é a única forma de o raster ser o mesmo na máquina de quem desenvolve e no runner do CI. É a mesma exigência que fez D-1.5.5 embutir os bytes em vez de deixar cada lado resolver a referência.

*Alternativa descartada:* traduzir o SVG do MathJax em caminhos de PDF com `pdf-lib` e rasterizar o PDF com mupdf, sem dependência nova. É exatamente a superfície que D-1.5.1 recusou — parser de SVG com cadeia de transforms —, e aqui ela ficaria no caminho do artefato que vai à impressora.

**Como isto pode falhar em silêncio:** uma troca de versão de `resvg` que mexa no antialiasing mudaria os bytes do PNG sem mexer em dimensão nenhuma, então o golden do `LayoutMap` não acusaria. Por isso a versão fica fixada no lock, o raster é artefato versionado em `fixtures/` e o manifesto carrega o `sha256` de cada PNG: a mudança aparece no diff do commit, e não na folha impressa.

### D-1.5.9 — O branco em volta da fórmula é derivado, e assimétrico de propósito

A folha impressa da tarefa 6.6 foi aprovada em resolução e **reprovada em espaçamento**: nas 12
fórmulas o branco de cima era grande demais e o de baixo pequeno demais, e por proximidade a fórmula
lia como pertencente às alternativas. A fórmula é parte do **enunciado**, então a leitura estava
invertida.

**A causa não era uma constante errada.** Era misturar posicionamento por linha de base com
posicionamento por topo. O laço do enunciado deixa o cursor uma entrelinha adiante da última linha —
avanço que um texto seguinte consome com a ascendente, mas que a fórmula, posicionada pelo topo,
transformava em branco puro. Somavam-se ainda `SPACE_AFTER_STATEMENT` e um respiro próprio, dando
10,691 mm fixos acima contra 3 mm mais resíduo abaixo.

**Decisão.**

- **Uma base única**: `textTransition = lineHeight + SPACE_AFTER_STATEMENT`, a transição que a folha
  já faz entre o fim do enunciado e a primeira alternativa. Os dois vãos são **múltiplos dela**, e
  nenhum é valor próprio — mexer na entrelinha move os dois juntos, e divergir fica impossível.
- **Acima** = 45% da base. Heurística de proximidade: para dois grupos lerem como distintos, o
  espaço entre eles precisa ser ao menos o dobro do espaço dentro de cada um.
- **Abaixo** = 4/3 da base.

  A primeira versão desta decisão usava a base **inteira**, com o argumento de que fórmula →
  alternativas *é* a mesma transição que enunciado → alternativas. A impressão pediu mais separação,
  e o argumento era elegante demais para ser verdade: uma fórmula é **bloco de exibição**, não linha
  de texto, e bloco de exibição precisa de mais ar embaixo do que uma linha precisa. A igualdade
  agradava no papel do design; o papel de verdade discordou.

  O 4/3 é escolhido pelo vão de **tinta**, que é o que se vê: entrega +49,6% de branco visível.
  Multiplicar o nominal por 3/2 daria **+74%**, porque a ascendente da alternativa é uma subtração
  fixa e a proporção não sobrevive à conversão nominal → tinta. É o mesmo motivo pelo qual a regra
  precisa ser nominal e a tinta precisa ser critério de aceite: as duas escalas não são
  proporcionais entre si.
- **Os dois vãos são ancorados na base, e não um no outro.** Pendurar "acima" como fração de
  "abaixo" fazia qualquer ajuste no de baixo arrastar o de cima junto — e eles respondem a critérios
  diferentes: o de cima é proximidade com o enunciado, o de baixo é separação das alternativas.
- **A conversão linha-de-base → topo acontece num ponto só**, `advanceAfterStatement`, usado tanto
  pelo builder que dimensiona o bloco quanto pelo engine que desenha. Se fossem dois cálculos, altura
  reservada e altura desenhada divergiriam em silêncio, e só a folha impressa acusaria.
- **A fórmula deixa de arredondar à grade.** Quem precisa cair na grade é o bloco, e ele já cai. O
  arredondamento da fórmula era redundante e seu resíduo caía todo abaixo dela — era ele que fazia o
  vão inferior variar 2,159 mm entre as doze.

**Por que a regra é nominal e a tinta é só critério de aceite.** A tentação é escrever a regra em
tinta, já que é tinta que o olho lê. Não dá, e isto foi medido: as métricas de `hhea` da fonte
embarcada são `ascender = 1036` e `descender = −335`, que somam mais que o em e preveem um vão de
**96 µm** entre duas linhas de corpo — onde o papel mostra **1 456 µm**. Métrica de fonte dá caixa de
linha, não tinta; tinta exigiria abrir contornos de glifo (`glyf`/`loca`), superfície que esta fatia
existe para não abrir. A regra é aplicada ao espaçamento nominal, que é o que o engine controla, e a
tinta julga o resultado.

**Resultado medido**, tinta a 600 dpi nas 12 fórmulas:

| | antes | 1ª correção | **aprovado** |
|---|---|---|---|
| branco acima (média) | 10,089 mm | 2,868 mm | **2,865 mm** |
| branco abaixo (média) | 2,565 mm | 5,214 mm | **7,775 mm** |
| amplitude do vão de baixo | 2,159 mm | 0,550 mm | **0,550 mm** |
| razão abaixo/acima | 0,25× (invertida) | 1,82× | **2,71×** |
| fórmula × vão entre linhas do corpo | — | 3,58× | **5,34×** (limiar de proximidade: 2×) |

A coluna do meio é a primeira correção, que inverteu a proximidade e foi aprovada na segunda
impressão com um pedido: mais separação embaixo. A terceira coluna é o resultado do 4/3.

O vão de cima **não se moveu** entre as duas últimas colunas (2,868 → 2,865 mm, três micrômetros de
ruído de rasterização): é a evidência de que ancorar os dois na base, em vez de um no outro,
funcionou como pretendido.

*Consequência aceita:* os blocos com fórmula **encolhem** cerca de 5 mm cada, porque o excesso de
cima era maior que a falta de baixo. Páginas seguem 4; 15 das 40 questões mudaram de coluna ou
página, efeito da regra de distribuir sobra em vez de empurrá-la para o fim.

*Fica em aberto:* fórmula em bloco seguida de **mais enunciado**, e não de alternativas. Aí não há
quebra semântica e o vão de baixo deveria ser o de cima — o vão inferior é escolhido pelo que vem
**depois**, não pela fórmula. Não é especificado aqui porque `Question` não consegue expressar a
estrutura: tem um `statement` e uma `formula`, e a ordem está fixa no engine. Expressá-la exige o
corpo da questão virar sequência de blocos, que é contrato maior que o `InlineBox` da 1.6 e não sai
de graça junto com ele.

## Open Questions

Nenhuma. As três que existiam foram fechadas em D-1.5.6, D-1.5.7 e na seção sobre o que a fatia deixa sem validar. A quarta, aberta na implementação — com que ferramenta rasterizar —, foi fechada em D-1.5.8.
