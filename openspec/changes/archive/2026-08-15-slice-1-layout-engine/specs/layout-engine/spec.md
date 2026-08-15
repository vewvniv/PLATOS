## Purpose

Transformar uma definição de prova em geometria: medir texto de forma idêntica em qualquer plataforma, agrupar conteúdo em blocos indivisíveis, paginar sobre uma grade fixa e produzir o `LayoutMap` — a fonte geométrica única da folha, da qual a impressão é projeção e a leitura óptica é consumidora.

## ADDED Requirements

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

### Requirement: Geometria da região escaneável em coordenadas normalizadas

Toda folha SHALL conter exatamente uma região escaneável de gabarito, posicionada no topo da primeira página, delimitada por quatro marcadores ArUco e acompanhada de um código QR dentro da mesma região.

Dentro de uma região escaneável, toda coordenada SHALL ser expressa como `(u,v)` no intervalo `[0,1]` relativo ao quadrilátero formado pelos quatro marcadores. Nenhuma coordenada de região SHALL ser expressa em unidade de papel.

A geometria de captura SHALL respeitar: bolha com diâmetro de 4,2 mm, passo horizontal de 5,2 mm, passo vertical de 6,0 mm e traço de 0,22 mm; marcador ArUco com lado de no mínimo 12 mm e zona de silêncio de no mínimo um módulo.

Os quatro marcadores da região de índice `k` SHALL usar os identificadores `4k` a `4k+3`.

#### Scenario: Coordenadas dentro da faixa normalizada

- **WHEN** um `LayoutMap` com região escaneável é produzido
- **THEN** toda coordenada de bolha e de moldura dentro da região está no intervalo `[0,1]` nos dois eixos

#### Scenario: Identificadores dos marcadores

- **WHEN** a região de gabarito de índice `0` é emitida
- **THEN** seus quatro marcadores usam os identificadores `0`, `1`, `2` e `3`

#### Scenario: Zona de silêncio preservada

- **WHEN** um marcador ArUco é posicionado
- **THEN** nenhum outro elemento desenhável ocupa a zona de silêncio de no mínimo um módulo ao redor dele

#### Scenario: Passo das bolhas alinhado à grade

- **WHEN** uma linha de bolhas é emitida
- **THEN** o passo vertical entre linhas é 6,0 mm, múltiplo da grade de 3 mm

### Requirement: Validação do `LayoutMap` sem renderizar

O sistema SHALL oferecer uma validação do `LayoutMap` que não depende de renderizá-lo, verificando: unicidade dos identificadores de elemento e de região, ausência de sobreposição entre regiões escaneáveis, coordenadas normalizadas dentro da faixa `[0,1]`, e presença das versões declaradas.

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

#### Scenario: Mapa válido

- **WHEN** um `LayoutMap` produzido pelo Layout Engine é validado
- **THEN** a validação o aceita e não altera o mapa

### Requirement: Entrada não suportada é recusada explicitamente

A definição de prova aceita nesta capacidade SHALL conter apenas questões objetivas. Uma definição contendo questão discursiva, conteúdo matemático a ser tipografado ou imagem SHALL ser recusada com erro identificável.

O sistema SHALL NOT produzir um layout parcial, aproximado ou silenciosamente degradado para entrada não suportada.

#### Scenario: Questão discursiva na entrada

- **WHEN** uma definição de prova contendo questão discursiva é submetida ao cálculo
- **THEN** o cálculo falha com erro que identifica o recurso não suportado e nenhum `LayoutMap` é produzido

#### Scenario: Conteúdo não suportado não degrada em silêncio

- **WHEN** uma definição de prova contém elemento que a capacidade ainda não desenha
- **THEN** nenhum `LayoutMap` é emitido, em vez de um mapa sem aquele elemento
