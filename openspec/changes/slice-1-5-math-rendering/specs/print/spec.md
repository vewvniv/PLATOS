## ADDED Requirements

### Requirement: Imagem desenhada a partir dos bytes declarados

Um renderizador SHALL desenhar uma imagem declarada no `LayoutMap` a partir exatamente dos bytes que o mapa referencia, na posição e nas dimensões declaradas.

Um renderizador SHALL NOT reamostrar, recortar, reescalar por conta própria nem substituir a imagem por outra representação. Bytes ausentes ou que não correspondam ao identificador declarado SHALL produzir falha explícita, e nenhum documento parcial SHALL ser entregue.

Como os dois renderizadores recebem os mesmos bytes, a imagem desenhada SHALL ser equivalente entre plataformas dentro da mesma tolerância exigida dos demais elementos.

#### Scenario: Imagem ocupa a caixa declarada

- **WHEN** um `LayoutMap` declara uma imagem com posição e dimensões
- **THEN** a imagem aparece nessa posição, com essas dimensões, em ambos os renderizadores

#### Scenario: Bytes ausentes

- **WHEN** o recurso de imagem referenciado pelo mapa não está disponível
- **THEN** a renderização falha com erro identificável e nenhum documento é entregue

#### Scenario: Fórmula equivalente entre renderizadores

- **WHEN** a mesma folha com fórmula é desenhada nas duas plataformas e comparada
- **THEN** a caixa da fórmula coincide dentro da tolerância de 0,3 mm, como os demais elementos

## MODIFIED Requirements

### Requirement: Renderização derivada exclusivamente do `LayoutMap`

Um renderizador SHALL desenhar a folha exclusivamente a partir das primitivas de desenho derivadas do `LayoutMap`. Um renderizador SHALL NOT medir texto, decidir quebra de linha, decidir paginação, calcular posição de elemento de região escaneável, nem tipografar conteúdo matemático.

Um renderizador SHALL desenhar todos os elementos que o mapa declara. Um elemento que o renderizador não saiba desenhar SHALL produzir falha explícita, nunca uma página com o elemento omitido.

#### Scenario: Renderizador não recalcula geometria

- **WHEN** um `LayoutMap` é renderizado
- **THEN** as posições desenhadas são exatamente as declaradas no mapa, sem medição de texto no renderizador

#### Scenario: Primitiva desconhecida

- **WHEN** um `LayoutMap` declara uma primitiva que o renderizador não implementa
- **THEN** a renderização falha com erro identificável e nenhum documento parcial é entregue

#### Scenario: Fonte embarcada no documento

- **WHEN** um documento é gerado
- **THEN** a fonte embarcada acompanha o documento e nenhuma fonte do sistema é referenciada

#### Scenario: Renderizador não tipografa matemática

- **WHEN** um `LayoutMap` declara uma fórmula
- **THEN** o renderizador desenha o recurso já pronto que o mapa referencia, sem interpretar LaTeX, MathML ou SVG
