## ADDED Requirements

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

## MODIFIED Requirements

### Requirement: Entrada não suportada é recusada explicitamente

A definição de prova aceita nesta capacidade SHALL conter apenas questões objetivas, que podem declarar fórmula em bloco. Uma definição contendo questão discursiva, fórmula em linha no meio do texto, ou imagem de enunciado SHALL ser recusada com erro identificável.

O sistema SHALL NOT produzir um layout parcial, aproximado ou silenciosamente degradado para entrada não suportada.

#### Scenario: Questão discursiva na entrada

- **WHEN** uma definição de prova contendo questão discursiva é submetida ao cálculo
- **THEN** o cálculo falha com erro que identifica o recurso não suportado e nenhum `LayoutMap` é produzido

#### Scenario: Conteúdo não suportado não degrada em silêncio

- **WHEN** uma definição de prova contém elemento que a capacidade ainda não desenha
- **THEN** nenhum `LayoutMap` é emitido, em vez de um mapa sem aquele elemento

#### Scenario: Fórmula em linha ainda é recusada

- **WHEN** uma definição de prova declara fórmula no meio do texto corrido
- **THEN** o cálculo falha com erro que identifica a fórmula em linha como não suportada

#### Scenario: Fórmula em bloco é aceita

- **WHEN** uma definição de prova declara fórmula em bloco com dimensões e recurso de imagem
- **THEN** o cálculo prossegue e o `LayoutMap` inclui a caixa da fórmula
