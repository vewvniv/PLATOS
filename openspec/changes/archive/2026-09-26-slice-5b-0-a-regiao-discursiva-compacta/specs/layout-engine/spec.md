## MODIFIED Requirements

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

## ADDED Requirements

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

## REMOVED Requirements

### Requirement: A questão discursiva é um bloco indivisível com a sua moldura

**Reason**: o D35 foi emendado pelo ADR-0017. A rubrica deixou de dimensionar a moldura, e o cenário
"A rubrica dimensiona a moldura" passou a ser falso. Um `MODIFIED` não descarta cenário, então o
requisito sai inteiro, e o requisito "A questão discursiva é um bloco indivisível, com a moldura que o
professor dimensiona", em `ADDED`, carrega o resto do bloco. A indivisibilidade, o enunciado fora e o
teto da coluna continuam iguais.

**Migration**: toda definição de discursiva passa a declarar o número de linhas e a largura (requisito
"Recusa de entrada não suportada"). Entre as fixtures, `prova-discursiva.json` é a única definição com
discursiva, e ela ganha os dois campos nesta mudança. Quatro testes do domínio também montam discursiva
em código: `ExamDefinitionTest`, `PacoteDiscursivoTest`, `LayoutEngineTest` e `RegiaoDiscursivaTest`.
Esses testes passam a declarar os dois campos.
