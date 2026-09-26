## Purpose

Transformar uma definição de prova em geometria: medir texto de forma idêntica em qualquer plataforma, agrupar conteúdo em blocos indivisíveis, paginar sobre uma grade fixa e produzir o `LayoutMap` — a fonte geométrica única da folha, da qual a impressão é projeção e a leitura óptica é consumidora.

## Requirements

### Requirement: Medição de texto determinística e independente de plataforma

A medição de texto SHALL produzir resultados idênticos em todas as plataformas suportadas para a mesma entrada, sem consultar nenhuma API de medição do sistema operacional, do navegador ou do runtime gráfico.

A medição SHALL usar exclusivamente uma fonte embarcada como recurso versionado junto ao código. O sistema SHALL NOT usar fonte instalada no sistema, nem aceitar substituição de fonte.

A fonte embarcada SHALL ser identificada por uma versão. Ausência do recurso, arquivo corrompido ou versão divergente da esperada SHALL produzir erro explícito e identificável, nunca medição com fonte alternativa.

#### Scenario: Mesma medição em plataformas diferentes

- **WHEN** o mesmo texto, no mesmo corpo e na mesma largura disponível, é medido em plataformas diferentes
- **THEN** a largura, a altura e as quebras de linha resultantes são exatamente iguais

#### Scenario: Fonte do sistema não influencia o resultado

- **WHEN** a medição ocorre em um ambiente onde existe uma fonte instalada com o mesmo nome da fonte embarcada, porém com métricas diferentes
- **THEN** o resultado é o da fonte embarcada e não muda

#### Scenario: Recurso de fonte ausente ou corrompido

- **WHEN** a fonte embarcada não pode ser lida ou não corresponde à versão esperada
- **THEN** a medição falha com erro identificável e nenhum layout é produzido

#### Scenario: Texto com pares de kerning

- **WHEN** um texto contendo pares de caracteres com ajuste de kerning é medido
- **THEN** o ajuste é aplicado e a largura resultante é a mesma em todas as plataformas

### Requirement: Grade vertical de 3 mm

Toda altura de bloco e toda posição vertical de bloco no `LayoutMap` SHALL ser múltiplo inteiro de 3 mm.

Quando o conteúdo medido de um bloco não completar um múltiplo da grade, o bloco SHALL ser arredondado para cima até o múltiplo seguinte. O conteúdo SHALL NOT ser comprimido nem sobreposto para caber na grade.

#### Scenario: Altura arredondada para a grade

- **WHEN** o conteúdo medido de um bloco ocupa uma altura que não é múltiplo de 3 mm
- **THEN** a altura do bloco no mapa é o próximo múltiplo de 3 mm e nenhum conteúdo é cortado

#### Scenario: Posições verticais alinhadas

- **WHEN** um `LayoutMap` é produzido
- **THEN** toda posição vertical de bloco é múltiplo de 3 mm a partir da margem superior

### Requirement: Blocos indivisíveis e paginação determinística

Enunciado e alternativas de uma mesma questão SHALL formar um bloco indivisível. Um bloco indivisível SHALL NOT ser partido entre duas páginas.

A paginação SHALL distribuir os blocos minimizando o somatório do quadrado da sobra de cada página, de modo que o espaço vazio seja distribuído entre as páginas em vez de concentrado na última.

Um bloco cuja altura exceda a área útil de uma página SHALL produzir erro explícito e identificável, e nenhum layout SHALL ser emitido.

#### Scenario: Questão não é partida entre páginas

- **WHEN** uma questão não cabe no espaço restante da página corrente
- **THEN** a questão inteira, com suas alternativas, começa na página seguinte

#### Scenario: Sobra distribuída entre páginas

- **WHEN** um conjunto de blocos pode ser paginado de mais de uma forma válida
- **THEN** a paginação escolhida é a de menor somatório do quadrado da sobra por página

#### Scenario: Bloco maior que a página

- **WHEN** um bloco tem altura maior que a área útil de uma página
- **THEN** o cálculo falha com erro identificável e nenhum `LayoutMap` é produzido

### Requirement: `LayoutMap` determinístico e versionado

O cálculo do `LayoutMap` SHALL ser uma função pura da definição da prova: a mesma entrada SHALL produzir exatamente o mesmo mapa, em qualquer plataforma e em qualquer execução.

O `LayoutMap` SHALL declarar a versão do Layout Engine que o produziu e a versão mínima de renderizador exigida para desenhá-lo.

A versão mínima declarada SHALL ser a menor capaz de desenhar **todas as primitivas que aquele mapa contém**:
- um mapa com primitiva de linha exige renderizador versão 2;
- um mapa sem linha exige a versão 1.

Um mapa SHALL NOT exigir versão por capacidade que não usa.

O `LayoutMap` SHALL ser a fonte geométrica da folha. Nenhum consumidor SHALL derivar geometria do documento impresso.

#### Scenario: Recálculo estável

- **WHEN** o mesmo `LayoutMap` é calculado duas vezes a partir da mesma definição de prova
- **THEN** os dois resultados são idênticos, incluindo a ordem dos elementos

#### Scenario: Cálculo em plataformas diferentes

- **WHEN** a mesma definição de prova é submetida ao cálculo em plataformas diferentes
- **THEN** os `LayoutMap` resultantes são idênticos

#### Scenario: Versões declaradas

- **WHEN** um `LayoutMap` é produzido
- **THEN** ele carrega a versão do Layout Engine e a versão mínima de renderizador exigida

#### Scenario: Mapa com pauta exige o renderizador que desenha linha

- **WHEN** uma prova com questão discursiva é calculada
- **THEN** o mapa declara versão mínima de renderizador 2

#### Scenario: Mapa sem linha continua exigindo a versão 1

- **WHEN** uma prova só com questões objetivas é calculada
- **THEN** o mapa declara versão mínima de renderizador 1, e nenhuma primitiva de linha

### Requirement: Geometria da região escaneável em coordenadas normalizadas

Toda folha SHALL conter exatamente uma região escaneável de gabarito, de índice `0`, posicionada no topo da primeira página, delimitada por quatro marcadores ArUco e acompanhada de um código QR dentro da mesma região. A região de gabarito SHALL conter bolhas apenas das questões objetivas.

Toda folha de prova com questões discursivas SHALL conter, além da região de gabarito, **exatamente uma região discursiva por questão discursiva**. A região discursiva SHALL ter **dois** marcadores ArUco próprios, na diagonal: um no canto superior esquerdo e outro no canto inferior direito. O código QR dela SHALL ficar no canto superior direito, na mesma faixa do marcador de cima (ADR-0018). As regiões discursivas SHALL receber os índices `1` a `N` na ordem em que as questões discursivas aparecem na definição da prova.

Dentro de uma região escaneável, toda coordenada SHALL ser expressa como `(u,v)` no intervalo `[0,1]` relativo ao **retângulo de referência** da região:
- na região de gabarito, o quadrilátero formado pelos centros dos quatro marcadores;
- na região discursiva, o retângulo que vai do canto externo superior esquerdo do marcador de cima ao canto externo inferior direito do marcador de baixo.

Nenhuma coordenada de região SHALL ser expressa em unidade de papel.

A geometria de captura SHALL respeitar:
- bolha com diâmetro de 4,2 mm, passo horizontal de 5,2 mm, passo vertical de 6,0 mm e traço de 0,22 mm;
- marcador ArUco da região de gabarito com lado de no mínimo 12 mm;
- marcador ArUco de região discursiva com lado de no mínimo 10 mm;
- zona de silêncio de no mínimo um módulo em volta de todo marcador.

Os mínimos de lado SHALL valer no pior caso da faixa de reescala de impressão, e não só no valor nominal (ADR-0001).

A região de índice `k` SHALL usar identificadores do intervalo `4k` a `4k+3`. A região de gabarito usa os quatro. A região discursiva usa `4k`, no canto superior esquerdo, e `4k+3`, no canto inferior direito, e `4k+1` e `4k+2` não são impressos.

#### Scenario: Coordenadas dentro da faixa normalizada

- **WHEN** um `LayoutMap` com região escaneável é produzido
- **THEN** toda coordenada de bolha, de moldura, de QR e de área de resposta dentro de cada região está no intervalo `[0,1]` nos dois eixos

#### Scenario: Identificadores dos marcadores

- **WHEN** a região de gabarito de índice `0` é emitida
- **THEN** seus quatro marcadores usam os identificadores `0`, `1`, `2` e `3`

#### Scenario: Identificadores dos marcadores da região discursiva

- **WHEN** uma prova com duas questões discursivas é calculada
- **THEN** a região da primeira discursiva tem índice `1` e marcadores `4` e `7`, e a da segunda tem índice `2` e marcadores `8` e `11`

#### Scenario: Dois marcadores na diagonal e o QR no terceiro canto

- **WHEN** uma região discursiva é emitida
- **THEN** o marcador `4k` ocupa o canto superior esquerdo do retângulo de referência, o marcador `4k+3` ocupa o canto inferior direito, e o QR ocupa o canto superior direito, com o topo na mesma altura do topo do marcador de cima

#### Scenario: Gabarito só com objetivas

- **WHEN** uma prova com questões objetivas e discursivas é calculada
- **THEN** a região de gabarito tem bolhas para cada objetiva e para nenhuma discursiva

#### Scenario: Zona de silêncio preservada

- **WHEN** um marcador ArUco é posicionado, em qualquer região
- **THEN** nenhum outro elemento desenhável ocupa a zona de silêncio de no mínimo um módulo ao redor dele

#### Scenario: Marcador discursivo dimensionado com folga

- **WHEN** o lado do marcador da região discursiva é reduzido em 5%
- **THEN** ele continua com no mínimo 10 mm

#### Scenario: Passo das bolhas alinhado à grade

- **WHEN** uma linha de bolhas é emitida
- **THEN** o passo vertical entre linhas é 6,0 mm, múltiplo da grade de 3 mm

### Requirement: Validação do `LayoutMap` sem renderizar

O sistema SHALL oferecer uma validação do `LayoutMap` que não depende de renderizá-lo, verificando: unicidade dos identificadores de elemento e de região, ausência de sobreposição entre regiões escaneáveis, coordenadas normalizadas dentro da faixa `[0,1]`, presença das versões declaradas, tom e trama dentro da faixa admitida, nenhuma tinta chapada acima de 8%, e tinta chapada decorativa dentro do orçamento declarado da região — a cobertura efetiva de um glifo não é verificável sem rasterizar, e quem a julga é o documento.

A validação SHALL recusar um mapa inválido apontando qual verificação falhou. Um mapa válido SHALL ser aceito sem efeito colateral.

#### Scenario: Identificador duplicado

- **WHEN** um `LayoutMap` contém dois elementos com o mesmo identificador
- **THEN** a validação recusa o mapa e identifica a duplicação

#### Scenario: Regiões sobrepostas

- **WHEN** um `LayoutMap` contém duas regiões escaneáveis cujos quadriláteros se sobrepõem
- **THEN** a validação recusa o mapa e identifica a sobreposição

#### Scenario: Coordenada fora da faixa

- **WHEN** um `LayoutMap` contém uma coordenada de região fora do intervalo `[0,1]`
- **THEN** a validação recusa o mapa e identifica a coordenada

#### Scenario: Trama acima do teto

- **WHEN** um `LayoutMap` declara tinta chapada acima de 8% de preto
- **THEN** a validação recusa o mapa e identifica o elemento e o valor

#### Scenario: Tom fora da faixa

- **WHEN** um `LayoutMap` declara tom ou trama fora da faixa admitida
- **THEN** a validação recusa o mapa e identifica o elemento

#### Scenario: Mapa válido

- **WHEN** um `LayoutMap` produzido pelo Layout Engine é validado
- **THEN** a validação o aceita e não altera o mapa

### Requirement: Fórmula em bloco como caixa de dimensão conhecida

Uma questão SHALL poder declarar uma fórmula em bloco, acompanhada de suas dimensões e do recurso de imagem que a representa na folha.

O Layout Engine SHALL tratar a fórmula como uma caixa opaca: ele posiciona e reserva espaço, e SHALL NOT interpretar, tipografar ou remedir o conteúdo matemático.

A fórmula SHALL ser desenhada exatamente com as dimensões declaradas, sem deformar nem reescalar. O espaço que ela ocupa no bloco SHALL NOT ser arredondado à grade: quem cai na grade de 3 mm é o bloco da questão, e arredondar também a fórmula depositaria o resíduo desse arredondamento de um dos lados dela.

Uma fórmula cuja largura declarada exceda a largura da coluna SHALL produzir erro explícito e identificável, e nenhum layout SHALL ser emitido.

#### Scenario: Fórmula reserva espaço próprio

- **WHEN** uma questão com fórmula em bloco é submetida ao cálculo
- **THEN** o mapa reserva para ela uma caixa com a largura e a altura declaradas, e a altura do bloco cresce em múltiplos de 3 mm

#### Scenario: Fórmula não é reescalada

- **WHEN** a altura declarada da fórmula não é múltiplo da grade
- **THEN** as dimensões desenhadas permanecem exatamente as declaradas, e é a altura do bloco que sobe ao próximo múltiplo da grade

#### Scenario: Fórmula mais larga que a coluna

- **WHEN** a largura declarada de uma fórmula excede a largura da coluna
- **THEN** o cálculo falha com erro identificável e nenhum `LayoutMap` é produzido

#### Scenario: Layout não depende do conteúdo matemático

- **WHEN** duas questões declaram fórmulas de conteúdos diferentes mas dimensões iguais
- **THEN** os blocos resultantes têm a mesma altura e a mesma posição

### Requirement: O branco em volta da fórmula a mantém ligada ao enunciado

A fórmula em bloco é parte do enunciado, e o espaçamento SHALL refletir isso: o branco abaixo da fórmula SHALL ser maior que o branco acima, de modo que a proximidade não a apresente como pertencente às alternativas.

Os dois espaços SHALL ser derivados da mesma grandeza que já governa a transição entre o fim do enunciado e a primeira alternativa, e SHALL NOT ser constantes independentes — dois valores independentes divergem quando um deles é ajustado.

Os dois espaços SHALL ser iguais para todas as fórmulas, independentemente da altura de cada uma.

#### Scenario: Fórmula lê como parte do enunciado

- **WHEN** uma questão com fórmula em bloco é posicionada
- **THEN** o espaço entre a fórmula e a primeira alternativa é maior que o espaço entre a última linha do enunciado e a fórmula

#### Scenario: Espaçamento não varia com a altura da fórmula

- **WHEN** duas questões declaram fórmulas de alturas diferentes
- **THEN** o branco acima e o branco abaixo são os mesmos nas duas

### Requirement: Fórmula pertence ao bloco indivisível da questão

Enunciado, fórmula em bloco e alternativas de uma mesma questão SHALL formar um único bloco indivisível, que SHALL NOT ser partido entre páginas ou colunas.

A fórmula SHALL ser posicionada entre o enunciado e as alternativas.

#### Scenario: Fórmula não se separa do enunciado

- **WHEN** uma questão com fórmula não cabe no espaço restante da coluna corrente
- **THEN** enunciado, fórmula e alternativas começam juntos na coluna seguinte

#### Scenario: Ordem dentro do bloco

- **WHEN** uma questão com fórmula é posicionada
- **THEN** a fórmula aparece abaixo da última linha do enunciado e acima da primeira alternativa

### Requirement: Fórmula em linha é uma caixa atômica alinhada à linha de base

O enunciado de uma questão SHALL poder referenciar fórmulas no meio do texto corrido. Cada fórmula em linha SHALL ser declarada com largura, altura e deslocamento da linha de base, já resolvidos fora do Layout Engine.

Na quebra de linha, uma fórmula em linha SHALL se comportar como uma unidade indivisível: ela SHALL NOT ser partida entre duas linhas, e SHALL ocupar largura como uma palavra ocuparia.

A fórmula SHALL ser posicionada de modo que o deslocamento declarado a alinhe à linha de base do texto que a cerca. O Layout Engine SHALL NOT interpretar, tipografar ou remedir o conteúdo matemático, nem alterar as dimensões declaradas.

Uma fórmula em linha cuja altura exceda o teto declarado para a linha SHALL ser recusada com erro identificável que aponte a forma em bloco como alternativa, e nenhum layout SHALL ser emitido.

O espaço que o enunciado declara entre o texto e a fórmula SHALL ser preservado no layout. Onde o enunciado não declara espaço, a fórmula SHALL ficar encostada no texto vizinho.

Esse espaço SHALL ser maior que o espaço entre duas palavras comuns. O branco que se vê entre duas letras é o avanço do espaço somado às laterais dos dois glifos; entre uma letra e a caixa da fórmula uma dessas laterais não existe, e o mesmo avanço produziria menos branco.

#### Scenario: Fórmula em linha ocupa espaço no meio do texto

- **WHEN** um enunciado referencia uma fórmula no meio de um parágrafo
- **THEN** a fórmula ocupa largura entre as palavras vizinhas e a quebra de linha considera essa largura

#### Scenario: Fórmula não é partida entre linhas

- **WHEN** uma fórmula em linha não cabe no espaço restante da linha corrente
- **THEN** ela inteira passa para a linha seguinte, sem ser dividida

#### Scenario: Alinhamento à linha de base

- **WHEN** uma fórmula em linha é posicionada
- **THEN** a posição vertical dela resulta do deslocamento declarado em relação à linha de base do texto vizinho

#### Scenario: Fórmula em linha alta demais

- **WHEN** um enunciado declara em linha uma fórmula mais alta que o teto de linha
- **THEN** o cálculo falha com erro identificável indicando a forma em bloco, e nenhum `LayoutMap` é produzido

#### Scenario: Espaço do enunciado é preservado em volta da fórmula

- **WHEN** o enunciado declara espaço entre o texto e a fórmula em linha
- **THEN** a fórmula é posicionada depois desse espaço, e o texto seguinte depois de outro, sem que texto e fórmula se toquem

#### Scenario: Sem espaço no enunciado a fórmula fica encostada

- **WHEN** o enunciado não declara espaço entre o texto e a fórmula — pontuação logo após, por exemplo
- **THEN** nenhum espaço é acrescentado, e a fórmula fica encostada no texto vizinho

#### Scenario: Layout não depende do conteúdo matemático em linha

- **WHEN** dois enunciados referenciam fórmulas em linha de conteúdos diferentes mas dimensões iguais
- **THEN** as quebras de linha e as alturas resultantes são iguais

### Requirement: A altura de uma linha acompanha o conteúdo dela

A altura de uma linha de texto SHALL ser suficiente para conter todo o seu conteúdo, incluindo fórmulas em linha mais altas que o texto.

A altura de um bloco de texto SHALL ser a soma das alturas das suas linhas, e SHALL NOT ser calculada como entrelinha multiplicada pelo número de linhas.

Linhas sem fórmula SHALL manter a altura de entrelinha do perfil, para que texto comum não mude de ritmo por existir fórmula em outro ponto do enunciado.

O arredondamento à grade de 3 mm SHALL continuar acontecendo no **bloco**, e SHALL NOT ser aplicado por linha.

#### Scenario: Linha com fórmula cresce

- **WHEN** uma linha contém fórmula mais alta que a entrelinha
- **THEN** aquela linha ocupa altura suficiente para a fórmula e as demais linhas mantêm a entrelinha

#### Scenario: Altura do parágrafo é a soma das linhas

- **WHEN** um parágrafo com linhas de alturas diferentes é medido
- **THEN** a altura do parágrafo é a soma das alturas das suas linhas

#### Scenario: Grade continua no bloco

- **WHEN** um bloco contém linhas de alturas diferentes
- **THEN** a altura do bloco é múltiplo de 3 mm e nenhuma linha é comprimida para caber

### Requirement: Referência de fórmula em linha é resolvida ou recusada

Uma referência a fórmula em linha presente no enunciado SHALL corresponder a um recurso declarado. Referência ausente, duplicada ou malformada SHALL produzir erro identificável, e nenhum layout SHALL ser emitido.

Uma referência não resolvida SHALL NOT ser desenhada como texto na folha.

#### Scenario: Referência inexistente

- **WHEN** um enunciado referencia uma fórmula em linha que não foi declarada
- **THEN** o cálculo falha com erro que identifica a referência e nenhum `LayoutMap` é produzido

#### Scenario: Referência não vira texto impresso

- **WHEN** o enunciado contém uma referência malformada
- **THEN** o cálculo falha, em vez de a folha sair com a referência impressa como texto

### Requirement: Geometria da folha e tipografia vêm de um perfil declarado

A geometria da folha — margens, colunas, medianiz e passo da grade — e a tipografia — corpo e entrelinha — SHALL ser parâmetros de um perfil declarado, e SHALL NOT ser constantes fixas do cálculo.

O perfil padrão SHALL reproduzir exatamente a geometria e a tipografia vigentes, de modo que uma prova calculada sem perfil explícito produza o mesmo `LayoutMap` de antes.

A geometria de captura — diâmetro de bolha, passo e marcadores — SHALL permanecer fora do perfil, porque está dimensionada pelas tolerâncias de impressão e captura.

#### Scenario: Perfil padrão não muda o resultado

- **WHEN** uma prova é calculada sem declarar perfil
- **THEN** o `LayoutMap` é idêntico ao produzido antes de o perfil existir

#### Scenario: Perfil com corpo maior muda a folha

- **WHEN** uma prova é calculada com um perfil de corpo maior
- **THEN** as quebras de linha e as alturas de bloco mudam de acordo, e a geometria de captura permanece a mesma

### Requirement: Recusa de entrada não suportada

Substitui a versão que recusava toda questão discursiva. O escopo mudou: a discursiva com rubrica passou a ser aceita.

A definição de prova aceita nesta capacidade SHALL conter questões objetivas e, opcionalmente, questões discursivas. Qualquer questão pode declarar fórmula em bloco e fórmula em linha.

Uma questão discursiva SHALL declarar:
- uma rubrica analítica com ao menos um critério. Cada critério SHALL ter pontos positivos, ao menos um descritor e `expected_lines` de no mínimo 1;
- **o número de linhas da área de resposta**, de no mínimo 1;
- **a largura dela**, `coluna` ou `página`.

O número de linhas e a largura são escolhas do professor, e **nenhum dos dois tem valor padrão** (ADR-0017). Nenhum dos dois SHALL ser inferido da rubrica.

SHALL ser recusada com erro identificável a definição que contiver:
- questão discursiva sem rubrica;
- questão discursiva sem número de linhas, ou com número de linhas menor que 1;
- questão discursiva sem largura declarada;
- questão discursiva de largura `página`, que ainda não é desenhada. A mensagem SHALL dizer que a largura de página depende da paginação em faixas;
- questão discursiva com alternativas;
- questão objetiva com rubrica, com número de linhas ou com largura;
- rubrica cujos pontos não somam a pontuação da questão;
- critério com pontos ou `expected_lines` não positivos, ou sem descritor;
- nenhuma questão objetiva;
- mais questões discursivas do que o dicionário de marcadores comporta;
- imagem de enunciado.

O sistema SHALL NOT produzir um layout parcial, aproximado ou silenciosamente degradado para entrada não suportada.

#### Scenario: Questão discursiva com rubrica é aceita

- **WHEN** uma definição de prova com questões objetivas e uma discursiva com rubrica válida, número de linhas e largura `coluna` é submetida ao cálculo
- **THEN** o cálculo prossegue e o `LayoutMap` inclui a região discursiva daquela questão

#### Scenario: Questão discursiva na entrada

- **WHEN** uma definição de prova contém questão discursiva sem rubrica
- **THEN** o cálculo falha com erro que identifica a questão e a rubrica ausente, e nenhum `LayoutMap` é produzido

#### Scenario: Discursiva sem número de linhas

- **WHEN** uma questão discursiva não declara o número de linhas, ou declara zero
- **THEN** o cálculo falha com erro que identifica a questão, e nenhum `LayoutMap` é produzido, mesmo que a rubrica declare `expected_lines`

#### Scenario: Discursiva sem largura

- **WHEN** uma questão discursiva não declara a largura
- **THEN** o cálculo falha com erro que identifica a questão, e nenhum `LayoutMap` é produzido

#### Scenario: Largura de página ainda é recusada

- **WHEN** uma questão discursiva declara largura `página`
- **THEN** o cálculo falha com erro que identifica a questão e diz que a largura de página depende da paginação em faixas, e nenhum `LayoutMap` é produzido, em vez de uma folha com a questão na coluna

#### Scenario: Objetiva com escolha da discursiva

- **WHEN** uma questão objetiva declara número de linhas ou largura
- **THEN** o cálculo falha com erro que identifica a questão, e nenhum `LayoutMap` é produzido

#### Scenario: Rubrica que não fecha com a pontuação

- **WHEN** a soma dos pontos dos critérios de uma rubrica difere da pontuação da questão
- **THEN** o cálculo falha com erro que identifica a questão e os dois valores, e nenhum `LayoutMap` é produzido

#### Scenario: Prova sem questão objetiva

- **WHEN** uma definição de prova contém apenas questões discursivas
- **THEN** o cálculo falha com erro identificável, e nenhum `LayoutMap` é produzido

#### Scenario: Mais discursivas do que marcadores

- **WHEN** uma definição de prova contém mais questões discursivas do que o dicionário de marcadores comporta, descontada a região de gabarito
- **THEN** o cálculo falha com erro que identifica o limite, e nenhum `LayoutMap` é produzido

#### Scenario: Conteúdo não suportado não degrada em silêncio

- **WHEN** uma definição de prova contém elemento que a capacidade ainda não desenha
- **THEN** nenhum `LayoutMap` é emitido, em vez de um mapa sem aquele elemento

#### Scenario: Imagem de enunciado ainda é recusada

- **WHEN** uma definição de prova declara imagem embutida no enunciado
- **THEN** o cálculo falha com erro que identifica a imagem como não suportada

#### Scenario: Fórmula em bloco é aceita

- **WHEN** uma definição de prova declara fórmula em bloco com dimensões e recurso de imagem
- **THEN** o cálculo prossegue e o `LayoutMap` inclui a caixa da fórmula

#### Scenario: Fórmula em linha é aceita

- **WHEN** uma definição de prova declara fórmula no meio do texto corrido, com dimensões e deslocamento de linha de base
- **THEN** o cálculo prossegue e o `LayoutMap` inclui a caixa da fórmula na linha correspondente

### Requirement: Cabeçalho, instrução de preenchimento e rodapé

A primeira página SHALL trazer o título da prova e a instrução de como preencher a bolha, posicionados acima da região de gabarito.

A região de gabarito SHALL permanecer na primeira página e acima de qualquer questão, e o cabeçalho SHALL NOT ocupar a faixa reservada ao grampo nem a zona de silêncio de qualquer marcador.

Toda página SHALL trazer sua numeração, com a posição e o total de páginas, dentro da margem inferior. Nenhum elemento de cabeçalho ou de rodapé SHALL invadir a área de conteúdo nem uma região escaneável.

#### Scenario: Instrução de preenchimento na folha

- **WHEN** uma prova é paginada
- **THEN** a primeira página traz o título e a instrução de preenchimento acima da região de gabarito

#### Scenario: A região de gabarito continua no topo

- **WHEN** o cabeçalho é acrescentado à folha
- **THEN** a região de gabarito continua na primeira página, antes de qualquer questão

#### Scenario: Numeração em todas as páginas

- **WHEN** uma prova de mais de uma página é paginada
- **THEN** cada página traz sua posição e o total, dentro da margem inferior

#### Scenario: Faixa do grampo permanece livre

- **WHEN** o cabeçalho é posicionado
- **THEN** nenhum elemento é desenhado dentro da faixa reservada ao grampo

### Requirement: Mitigação do erro de transcrição no gabarito

O gabarito SHALL agrupar suas linhas de questão em grupos de 3 a 5 questões, com faixa de trama aplicada a grupos alternados, de modo que dois grupos vizinhos nunca tenham o mesmo fundo.

A letra da alternativa SHALL ser impressa dentro do círculo correspondente, em tom mais claro que o do texto do corpo.

Essas marcas SHALL ser decorativas: nenhuma delas SHALL alterar a posição normalizada de bolha, moldura ou marcador.

#### Scenario: Grupos de tamanho declarado

- **WHEN** o gabarito de uma prova é emitido
- **THEN** suas linhas estão agrupadas em grupos de 3 a 5 questões

#### Scenario: Faixa alternada entre grupos vizinhos

- **WHEN** dois grupos consecutivos são emitidos
- **THEN** apenas um dos dois recebe a faixa de trama

#### Scenario: Letra dentro do círculo

- **WHEN** uma bolha de alternativa é emitida
- **THEN** a letra correspondente aparece dentro do círculo, em tom mais claro que o do corpo do texto

#### Scenario: Decoração não move geometria

- **WHEN** o mapa com faixa e letras é comparado ao mesmo mapa sem elas
- **THEN** toda coordenada normalizada de bolha, moldura e marcador é idêntica

### Requirement: Tinta declarada como fração de preto

Todo preenchimento e todo tom de texto que o `LayoutMap` declare SHALL ser expresso como fração de preto, e SHALL NOT ser expresso como cor.

A granularidade SHALL permitir declarar exatamente os valores que o design da folha exige, incluindo trama de 4,5%, sem arredondamento na serialização canônica.

Um elemento sem tom declarado SHALL ser desenhado em preto pleno. Nenhum consumidor SHALL escolher tom por conta própria.

#### Scenario: Trama fracionária sobrevive à serialização

- **WHEN** um mapa declara trama de 4,5% e é serializado canonicamente e lido de volta
- **THEN** o valor recuperado é exatamente 4,5%

#### Scenario: Ausência de tom significa preto pleno

- **WHEN** um elemento não declara tom
- **THEN** ele é desenhado em preto pleno, sem que o renderizador arbitre nada

### Requirement: Orçamento de tinta decorativa dentro da região escaneável

O `LayoutMap` SHALL declarar o orçamento de tinta decorativa da região escaneável: a fração máxima da área de uma bolha que pode estar coberta de tinta quando essa bolha não foi respondida, o teto de tom admitido para um elemento decorativo dentro de uma bolha, e o corredor dentro do qual o limiar da leitura óptica poderá ser escolhido.

O orçamento SHALL viajar no artefato publicado, porque quem o consome é a leitura óptica, e não este repositório.

A verificação de que a tinta efetivamente impressa cabe no orçamento SHALL ser feita sobre o documento rasterizado, e não sobre o mapa: a medição sem renderizar não tem como conhecer a tinta de um glifo, e um limite calculado como "a caixa inteira é tinta" recusaria folhas corretas.

A validação sem renderizar SHALL recusar, apontando a bolha, o que ela consegue provar sem rasterizar: tinta chapada dentro de uma bolha acima do orçamento, e elemento decorativo dentro de uma bolha com tom acima do teto declarado ou em preto pleno.

#### Scenario: Orçamento declarado no mapa

- **WHEN** um `LayoutMap` com região escaneável é produzido
- **THEN** ele declara o orçamento de tinta decorativa, o teto de tom e o corredor do limiar

#### Scenario: Trama dentro da bolha acima do orçamento é recusada

- **WHEN** um `LayoutMap` declara tinta chapada sobre uma bolha com cobertura acima do orçamento
- **THEN** a validação recusa o mapa e identifica a bolha e o valor

#### Scenario: Elemento opaco dentro da bolha é recusado

- **WHEN** um `LayoutMap` declara, dentro de uma bolha, um elemento em preto pleno ou com tom acima do teto declarado
- **THEN** a validação recusa o mapa e identifica a bolha e o elemento

#### Scenario: A folha de referência é aceita

- **WHEN** o mapa da prova de referência, com faixa e letra, é validado
- **THEN** ele é aceito — o que a validação prova não recusa decoração que cabe no orçamento

### Requirement: Folha de teste de impressão

O sistema SHALL produzir o layout de uma folha de teste de impressão, destinada a aprovar ou reprovar uma impressora antes de ela imprimir uma turma.

A folha de teste SHALL conter: quatro marcadores no mesmo lado nominal da prova, um código QR de conteúdo conhecido, um vão de referência cujo comprimento esperado está impresso ao lado dele, amostras de trama incluindo a do gabarito e o teto admitido, e uma linha de bolhas na mesma geometria da prova.

A folha de teste SHALL declarar, nela própria, o que aprova e o que reprova cada conferência, e SHALL caber em uma única página.

A folha de teste SHALL ser produzida pelo mesmo cálculo e pelas mesmas primitivas da prova. Ela SHALL NOT ter caminho de geometria próprio — uma folha que aprova a impressora por um caminho que a prova não usa não aprova nada.

#### Scenario: Conteúdo mínimo da folha de teste

- **WHEN** a folha de teste de impressão é produzida
- **THEN** ela traz os quatro marcadores, o QR, o vão de referência com o comprimento esperado impresso, as amostras de trama e a linha de bolhas, em uma única página

#### Scenario: Mesma geometria de captura da prova

- **WHEN** os marcadores e as bolhas da folha de teste são comparados aos da prova
- **THEN** lado do marcador, zona de silêncio, diâmetro e passos das bolhas são os mesmos

#### Scenario: Critério impresso na própria folha

- **WHEN** a folha de teste é lida por quem vai conferi-la
- **THEN** cada conferência traz na folha o valor que aprova e o que reprova

### Requirement: O payload do QR identifica a atribuição da folha

A folha produzida para uma atribuição SHALL trazer, no payload de **cada** código QR das suas regiões, o
**token daquela atribuição** e o **índice da região** em que o QR está. A folha produzida sem atribuição
SHALL trazer o campo de aluno **vazio** em todos os QRs, e SHALL NOT trazer valor inventado, derivado ou
de reserva.

O payload SHALL ser resolvido **no momento em que o layout é produzido**, e SHALL ficar registrado no
próprio `LayoutMap`, junto da matriz de módulos já codificada. Nenhum outro componente SHALL compor
ou recompor o payload depois disso: existe **um escritor só**, e quem lê a captura de volta usa o
mesmo codec.

A ligação entre uma região e o QR dela SHALL estar declarada no `LayoutMap`. SHALL NOT ser inferida da
ordem das primitivas nem do texto do identificador delas.

#### Scenario: A folha da atribuição carrega o token dela

- **WHEN** o layout de uma folha é produzido para uma atribuição
- **THEN** o payload de cada QR daquela folha traz o token daquela atribuição, e ele é lido de volta
  igual a partir da própria folha

#### Scenario: Cada QR declara a sua região

- **WHEN** a folha de uma prova com discursivas é produzida
- **THEN** o payload do QR de cada região traz o índice daquela região, e nenhum dois QRs da folha trazem o mesmo índice

#### Scenario: Folha sem atribuição não inventa aluno

- **WHEN** o layout é produzido sem atribuição
- **THEN** o campo de aluno do payload de todos os QRs está vazio, e nenhum valor de reserva aparece nele

#### Scenario: O payload não é recomposto na impressão

- **WHEN** o documento de uma folha é gerado a partir do pacote
- **THEN** o payload desenhado em cada região é exatamente o que o `LayoutMap` registrou para ela, sem recodificação

### Requirement: Folhas da mesma prova diferem só no QR

Duas folhas da mesma prova e da mesma variante, produzidas para atribuições diferentes, SHALL ter
geometria **idêntica** — mesmas regiões escaneáveis, mesmas posições de bolha, mesmas molduras e áreas
de resposta, mesmos marcadores, mesma paginação — e SHALL diferir apenas no payload dos QRs das suas
regiões e nas matrizes de módulos que deles decorrem.

O `LayoutMap` de cada folha SHALL continuar sendo determinístico e versionado nos mesmos termos já
exigidos: a mesma entrada produz o mesmo mapa.

#### Scenario: Duas atribuições, uma geometria

- **WHEN** os layouts de dois alunos da mesma prova e variante são comparados
- **THEN** todas as primitivas coincidem, exceto o payload dos QRs e as matrizes de módulos deles

#### Scenario: Duas implementações da folha do aluno coincidem

- **WHEN** a folha de um aluno é derivada do mesmo pacote pelas duas implementações que existem da regra
- **THEN** as duas folhas são iguais, QR por QR e região por região

#### Scenario: A medição impressa não muda com o aluno

- **WHEN** a fidelidade dimensional é medida sobre a folha de um aluno e sobre a de outro
- **THEN** as duas medições satisfazem a mesma tolerância, porque a geometria é a mesma

### Requirement: A região discursiva declara a questão e a área de resposta

Toda região discursiva SHALL declarar:
- a questão a que pertence;
- os dois marcadores dela;
- a área de resposta, como retângulo em coordenadas normalizadas ao retângulo de referência da região;
- o retângulo do QR dela.

A área de resposta é o que a captura vai recortar (§8). Ela SHALL ocupar a largura inteira da região, e SHALL ir da base do QR até a zona de silêncio do marcador de baixo. Ela SHALL conter a moldura inteira, **com folga acima e abaixo dela**. A folga de baixo existe para a tinta que desce da última linha escrita.

A área de resposta SHALL NOT sobrepor o QR da região nem a zona de silêncio de nenhum marcador. Uma região discursiva SHALL NOT declarar bolhas.

A validação do `LayoutMap` sem renderizar SHALL recusar, apontando qual verificação falhou:
- região discursiva sem questão;
- área de resposta fora de `[0,1]` ou sobreposta ao QR;
- duas regiões apontando a mesma questão;
- região cujo QR declarado não existe entre as primitivas da página dela;
- região discursiva que não declara exatamente os marcadores `4k` e `4k+3`;
- região discursiva cujo marcador declarado não existe entre as primitivas da página dela.

#### Scenario: Região discursiva completa

- **WHEN** uma prova com uma questão discursiva é calculada
- **THEN** a região discursiva declara a questão, os marcadores `4` e `7`, uma área de resposta dentro de `[0,1]` e um QR que existe na página dela, e nenhuma bolha

#### Scenario: Área de resposta com folga fora da moldura

- **WHEN** a região discursiva de uma questão é emitida
- **THEN** a moldura está inteira dentro da área de resposta, e a área se estende abaixo da moldura antes de chegar à zona de silêncio do marcador de baixo

#### Scenario: Área de resposta sobre o QR

- **WHEN** um `LayoutMap` declara uma região discursiva cuja área de resposta sobrepõe o QR da região
- **THEN** a validação recusa o mapa e identifica a região

#### Scenario: Duas regiões para a mesma questão

- **WHEN** um `LayoutMap` declara duas regiões discursivas que apontam a mesma questão
- **THEN** a validação recusa o mapa e identifica a questão

#### Scenario: QR declarado que não existe

- **WHEN** uma região declara um QR que não está entre as primitivas da página dela
- **THEN** a validação recusa o mapa e identifica a região

#### Scenario: Região discursiva com os marcadores errados

- **WHEN** um `LayoutMap` declara uma região discursiva com os quatro marcadores `4k` a `4k+3`, ou com um par diferente de `4k` e `4k+3`
- **THEN** a validação recusa o mapa e identifica a região e os marcadores declarados

#### Scenario: Marcador declarado que não existe

- **WHEN** uma região discursiva declara um marcador que não está entre as primitivas da página dela
- **THEN** a validação recusa o mapa e identifica a região e o marcador

### Requirement: A questão discursiva é um bloco indivisível, com a moldura que o professor dimensiona

O enunciado de uma questão discursiva e a região discursiva dela SHALL formar um bloco indivisível. Esse bloco SHALL NOT ser partido entre duas páginas nem entre duas colunas. O enunciado SHALL ficar **fora** da região discursiva: a moldura contém apenas a área de resposta.

A altura da moldura SHALL ser o número de linhas declarado pela questão, multiplicado pela pauta de 7 mm (ADR-0016, ADR-0017). Os `expected_lines` da rubrica SHALL NOT interferir na geometria. A altura do bloco SHALL ser múltiplo da grade de 3 mm.

Um bloco discursivo que exceda a área útil de uma coluna SHALL produzir erro explícito que identifique a questão, e nenhum layout SHALL ser emitido.

#### Scenario: Enunciado e moldura não se separam

- **WHEN** uma questão discursiva não cabe inteira no espaço restante da coluna corrente
- **THEN** o enunciado e a moldura começam juntos na coluna seguinte

#### Scenario: O professor dimensiona a moldura

- **WHEN** duas provas diferem só no número de linhas declarado por uma discursiva
- **THEN** a moldura daquela questão difere em altura por exatamente a diferença de linhas vezes 7 mm, e a posição dos marcadores e do QR em relação ao topo da região é a mesma

#### Scenario: A rubrica não mexe na moldura

- **WHEN** duas provas diferem só nos `expected_lines` de um critério de uma discursiva
- **THEN** os dois `LayoutMap` são idênticos

#### Scenario: O enunciado fica fora da moldura

- **WHEN** a região discursiva de uma questão é emitida
- **THEN** nenhum texto do enunciado daquela questão está dentro do retângulo de referência da região

#### Scenario: Moldura maior que a coluna

- **WHEN** uma questão discursiva declara mais linhas do que cabem numa coluna
- **THEN** o cálculo falha com erro que identifica a questão, e nenhum `LayoutMap` é produzido

### Requirement: A pauta é linha decorativa

A pauta da área de resposta SHALL ser desenhada com a primitiva de linha, em linhas horizontais espaçadas de 7 mm dentro da moldura. São uma linha a menos que o número de linhas declarado, porque as bordas da moldura fecham a primeira e a última.

A pauta é guia para o aluno, e não geometria para a captura (ADR-0016). O tom de cada linha da pauta SHALL ser declarado e SHALL ficar **abaixo** do teto de tom decorativo que a região declara (ADR-0010). A moldura SHALL continuar em preto pleno.

O tom de uma linha SHALL NOT ser tratado como trama: o teto de 8% vale para tinta chapada, que é área, e não para linha.

A validação do `LayoutMap` sem renderizar SHALL recusar, apontando a região e a linha, uma linha dentro da área de resposta de uma região discursiva que:
- não declara tom;
- ou declara tom acima do teto decorativo da região.

#### Scenario: Pauta abaixo do teto decorativo

- **WHEN** a região discursiva de uma questão é emitida
- **THEN** a pauta tem uma linha a menos que o número de linhas declarado, espaçadas de 7 mm, cada uma com tom declarado abaixo do teto decorativo da região, e a moldura é preta

#### Scenario: Pauta preta é recusada

- **WHEN** um `LayoutMap` declara, dentro da área de resposta de uma região discursiva, uma linha sem tom
- **THEN** a validação recusa o mapa e identifica a região e a linha

#### Scenario: Pauta acima do teto é recusada

- **WHEN** um `LayoutMap` declara, dentro da área de resposta de uma região discursiva, uma linha com tom acima do teto decorativo da região
- **THEN** a validação recusa o mapa e identifica a região, a linha e o valor

#### Scenario: Linha cinza não é trama

- **WHEN** um `LayoutMap` declara uma linha de pauta com tom acima de 8% e abaixo do teto decorativo da região
- **THEN** a validação aceita a linha, porque o teto de 8% vale para tinta chapada
