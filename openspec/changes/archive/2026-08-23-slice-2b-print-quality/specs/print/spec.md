## ADDED Requirements

### Requirement: Documento monocromático com teto de trama

Um documento gerado SHALL ser monocromático: nenhum elemento SHALL ser desenhado em cor, e o documento rasterizado SHALL NOT conter pixel cromático.

Nenhuma tinta chapada do documento SHALL passar de 8% de preto. A verificação SHALL ser feita sobre o documento rasterizado, e não sobre a intenção declarada no mapa.

A mesma trama SHALL sair equivalente nas duas plataformas: a cobertura medida de uma mesma área tramada SHALL coincidir entre os dois documentos dentro da tolerância declarada.

Essas verificações SHALL ser executadas automaticamente a cada mudança no código do Layout Engine ou dos renderizadores.

#### Scenario: Nenhum pixel cromático

- **WHEN** o documento gerado é rasterizado e inspecionado pixel a pixel
- **THEN** nenhum pixel tem componentes de cor diferentes entre si

#### Scenario: Trama dentro do teto

- **WHEN** a cobertura de tinta de cada área tramada do documento é medida
- **THEN** nenhuma passa de 8% de preto

#### Scenario: Trama equivalente entre plataformas

- **WHEN** a mesma área tramada é medida nos documentos das duas plataformas
- **THEN** as coberturas coincidem dentro da tolerância declarada

#### Scenario: Cor barra a integração

- **WHEN** uma mudança faz um renderizador emitir cor ou trama acima do teto
- **THEN** a verificação automática falha e aponta o elemento e o valor observado

### Requirement: Orçamento de tinta verificado sobre o documento

A tinta decorativa efetivamente impressa dentro de uma bolha não respondida SHALL ficar dentro do orçamento que o `LayoutMap` declara.

A verificação SHALL ser feita sobre o documento rasterizado, medindo a cobertura dentro da área de cada bolha, por um caminho que não compartilhe código com o que produziu essa geometria. Verificar a intenção declarada no mapa não satisfaz este requisito: o que o OMR vai ler é tinta, não declaração.

A verificação SHALL cobrir todas as bolhas do documento, e SHALL ser executada automaticamente a cada mudança no código do Layout Engine ou dos renderizadores.

#### Scenario: Bolha vazia dentro do orçamento

- **WHEN** a cobertura de tinta dentro de cada bolha do documento gerado é medida
- **THEN** nenhuma bolha não respondida passa do orçamento declarado no mapa

#### Scenario: Contraste preservado

- **WHEN** a cobertura de uma bolha não respondida é comparada à de uma bolha preenchida a caneta
- **THEN** a diferença entre as duas permanece maior que a margem declarada

#### Scenario: Decoração excessiva barra a integração

- **WHEN** uma mudança eleva a tinta decorativa dentro das bolhas acima do orçamento
- **THEN** a verificação automática falha e aponta a bolha e a cobertura medida

### Requirement: Folha de teste de impressão reprova uma impressora

O sistema SHALL produzir a folha de teste de impressão como documento próprio, pelos mesmos renderizadores da prova, sujeito às mesmas exigências de equivalência entre plataformas e de fidelidade dimensional.

A folha de teste impressa SHALL ser aprovada apenas quando: o vão de referência medido estiver dentro de ±5% do comprimento impresso ao lado dele, os quatro marcadores estiverem completos e com a borda fechada, a zona de silêncio estiver livre de qualquer traço, o QR for decodificado, e as amostras de trama estiverem visíveis sem borrar.

Reprovar em qualquer uma dessas conferências SHALL significar que aquela impressora não está apta a imprimir a prova, e o motivo SHALL ser identificável na própria folha.

#### Scenario: Folha de teste sujeita às mesmas guardas

- **WHEN** a folha de teste é gerada nas duas plataformas e comparada
- **THEN** ela satisfaz a mesma tolerância de equivalência e a mesma fidelidade dimensional exigidas da prova

#### Scenario: Impressora fora de escala é reprovada

- **WHEN** o vão de referência da folha impressa mede além de ±5% do comprimento impresso ao lado dele
- **THEN** a impressora é reprovada e o motivo é a escala

#### Scenario: Marcador incompleto reprova

- **WHEN** um marcador da folha impressa sai com a borda falhada, com módulos borrados ou com traço na zona de silêncio
- **THEN** a impressora é reprovada e o motivo é o marcador

#### Scenario: Trama que some ou borra reprova

- **WHEN** a amostra de trama da folha impressa sai invisível ou chapada
- **THEN** a impressora é reprovada e o motivo é a reprodução de trama

## MODIFIED Requirements

### Requirement: Renderização derivada exclusivamente do `LayoutMap`

Um renderizador SHALL desenhar a folha exclusivamente a partir das primitivas de desenho derivadas do `LayoutMap`. Um renderizador SHALL NOT medir texto, decidir quebra de linha, decidir paginação, calcular posição de elemento de região escaneável, escolher tom ou trama, nem tipografar conteúdo matemático.

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

#### Scenario: Tom desenhado como declarado

- **WHEN** um `LayoutMap` declara um elemento com tom ou trama
- **THEN** o renderizador desenha exatamente a fração de preto declarada, sem escolher tom, sem substituir por cor e sem arredondar a um valor próprio
