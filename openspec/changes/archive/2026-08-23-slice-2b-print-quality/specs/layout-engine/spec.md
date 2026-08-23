## ADDED Requirements

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

## MODIFIED Requirements

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
