## ADDED Requirements

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

Substitui "Entrada não suportada é recusada explicitamente", cujo escopo mudou: a recusa de fórmula em linha deixou de valer.

A definição de prova aceita nesta capacidade SHALL conter apenas questões objetivas, que podem declarar fórmula em bloco e fórmula em linha. Uma definição contendo questão discursiva ou imagem de enunciado SHALL ser recusada com erro identificável.

O sistema SHALL NOT produzir um layout parcial, aproximado ou silenciosamente degradado para entrada não suportada.

#### Scenario: Questão discursiva na entrada

- **WHEN** uma definição de prova contendo questão discursiva é submetida ao cálculo
- **THEN** o cálculo falha com erro que identifica o recurso não suportado e nenhum `LayoutMap` é produzido

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

## REMOVED Requirements

### Requirement: Entrada não suportada é recusada explicitamente

**Reason**: O cenário "Fórmula em linha ainda é recusada" deixou de descrever o comportamento — é justamente a barreira que esta fatia levanta. Um requisito não pode carregar um cenário falso, e o cenário não pode ser retirado em silêncio de dentro de um `MODIFIED`: o validador recusa, e com razão.

**Migration**: Substituído por "Recusa de entrada não suportada", acima, que preserva os dois cenários originais — questão discursiva e degradação silenciosa —, mantém a recusa de imagem de enunciado com cenário próprio, e acrescenta a aceitação de fórmula em bloco e em linha. Nenhuma recusa some sem substituto: fórmula em linha alta demais continua recusada, com cenário próprio no requisito da caixa atômica.
