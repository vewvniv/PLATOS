## ADDED Requirements

### Requirement: Fórmula em bloco como caixa de dimensão conhecida

Uma questão SHALL poder declarar uma fórmula em bloco, acompanhada de suas dimensões e do recurso de imagem que a representa na folha.

O Layout Engine SHALL tratar a fórmula como uma caixa opaca: ele posiciona e reserva espaço, e SHALL NOT interpretar, tipografar ou remedir o conteúdo matemático.

A altura reservada para a fórmula SHALL ser múltiplo da grade de 3 mm, arredondada para cima a partir da altura declarada, sem deformar nem reescalar a fórmula.

Uma fórmula cuja largura declarada exceda a largura da coluna SHALL produzir erro explícito e identificável, e nenhum layout SHALL ser emitido.

#### Scenario: Fórmula reserva espaço próprio

- **WHEN** uma questão com fórmula em bloco é submetida ao cálculo
- **THEN** o mapa reserva para ela uma caixa com a largura e a altura declaradas, e a altura do bloco cresce em múltiplos de 3 mm

#### Scenario: Fórmula não é reescalada

- **WHEN** a altura declarada da fórmula não é múltiplo da grade
- **THEN** o espaço reservado sobe ao próximo múltiplo e as dimensões da fórmula permanecem exatamente as declaradas

#### Scenario: Fórmula mais larga que a coluna

- **WHEN** a largura declarada de uma fórmula excede a largura da coluna
- **THEN** o cálculo falha com erro identificável e nenhum `LayoutMap` é produzido

#### Scenario: Layout não depende do conteúdo matemático

- **WHEN** duas questões declaram fórmulas de conteúdos diferentes mas dimensões iguais
- **THEN** os blocos resultantes têm a mesma altura e a mesma posição

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
