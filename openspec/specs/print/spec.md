## Purpose

Transformar o `LayoutMap` em folha impressa por renderizadores independentes em cada plataforma, garantindo que essa independência não produza divergência: o que sai no papel é dimensionalmente fiel ao mapa e equivalente entre plataformas dentro da tolerância que a leitura óptica exige.

## Requirements

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

### Requirement: Guarda de versão do renderizador

Um renderizador SHALL comparar sua própria versão com a versão mínima de renderizador declarada no `LayoutMap` antes de desenhar.

Quando sua versão for inferior à exigida, o renderizador SHALL recusar produzir o documento e SHALL informar que precisa ser atualizado. O sistema SHALL NOT produzir documento parcial, degradado ou com aviso silencioso nesse caso.

#### Scenario: Renderizador desatualizado recusa imprimir

- **WHEN** um `LayoutMap` exige versão mínima de renderizador superior à do renderizador corrente
- **THEN** nenhum documento é produzido e o motivo informado é a versão insuficiente

#### Scenario: Renderizador compatível

- **WHEN** a versão do renderizador é igual ou superior à exigida pelo mapa
- **THEN** o documento é produzido normalmente

### Requirement: Equivalência entre renderizadores

O mesmo `LayoutMap` renderizado em plataformas diferentes SHALL produzir páginas equivalentes: os centroides dos marcadores ArUco e das bolhas SHALL coincidir dentro de uma tolerância de 0,3 mm.

A contagem de páginas, a quantidade de regiões escaneáveis e a quantidade de bolhas por região SHALL ser idênticas entre as plataformas.

A verificação de equivalência SHALL ser executada automaticamente a cada mudança no código do Layout Engine ou dos renderizadores.

#### Scenario: Centroides dentro da tolerância

- **WHEN** o mesmo `LayoutMap` é rasterizado em duas plataformas e os centroides de marcadores e bolhas são comparados
- **THEN** nenhum par de centroides correspondentes diverge mais de 0,3 mm

#### Scenario: Estrutura idêntica das páginas

- **WHEN** o mesmo `LayoutMap` é renderizado em duas plataformas
- **THEN** o número de páginas, de regiões escaneáveis e de bolhas por região é o mesmo

#### Scenario: Divergência barra a integração

- **WHEN** uma mudança faz um renderizador divergir do outro além da tolerância
- **THEN** a verificação automática falha e a divergência é apontada com o elemento e a distância medida

### Requirement: Fidelidade dimensional do documento

Um documento gerado SHALL ser dimensionado para papel A4, com margens de 15 mm nas laterais e na base, 14 mm no topo e 8 mm reservados para grampo.

O documento SHALL apresentar as dimensões declaradas no `LayoutMap` — diâmetro e passos das bolhas, lado dos marcadores ArUco e margens — dentro de uma tolerância de 0,05 mm, medidas sobre o próprio documento.

O documento SHALL NOT depender de ajuste de escala, encaixe à página ou redimensionamento do visualizador para declarar essas dimensões.

#### Scenario: Medição do documento gerado

- **WHEN** o documento é medido diretamente, sem passar por impressora
- **THEN** cada dimensão observada corresponde à declarada no `LayoutMap` dentro de 0,05 mm

#### Scenario: Documento não depende de ajuste do visualizador

- **WHEN** o documento é aberto em visualizadores diferentes
- **THEN** as dimensões declaradas de página e de margens são as mesmas, sem depender de opção de encaixe à página

### Requirement: Tolerância a reescala da impressão

Impressoras reescalam o documento em ambas as direções, e o sistema SHALL NOT depender de medida absoluta na folha impressa. Ver ADR-0001.

Sob reescala de até ±5% em qualquer eixo, a folha impressa SHALL preservar as propriedades que a captura consome: lado do marcador ArUco de no mínimo 12 mm, zona de silêncio livre de qualquer outro traço, e código QR decodificável.

Toda geometria de captura SHALL ser dimensionada de modo que o mínimo exigido continue satisfeito no pior caso dessa faixa, e não apenas no valor nominal.

#### Scenario: Folha reescalada pela impressora

- **WHEN** a folha é impressa por uma impressora que reescala o documento em até 5%
- **THEN** os marcadores continuam com lado de no mínimo 12 mm, a zona de silêncio segue livre e o QR continua sendo decodificado

#### Scenario: Marcadores legíveis após impressão

- **WHEN** a folha impressa é inspecionada
- **THEN** cada marcador ArUco está completo, com a borda fechada, e sua zona de silêncio está livre de qualquer outro traço

#### Scenario: Geometria dimensionada com folga

- **WHEN** uma dimensão de captura com mínimo exigido é definida
- **THEN** o valor nominal escolhido mantém o mínimo satisfeito mesmo reduzido em 5%

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
