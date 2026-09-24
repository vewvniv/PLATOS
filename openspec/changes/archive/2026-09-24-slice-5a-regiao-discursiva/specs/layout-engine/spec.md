## MODIFIED Requirements

### Requirement: Geometria da região escaneável em coordenadas normalizadas

Toda folha SHALL conter exatamente uma região escaneável de gabarito, de índice `0`, posicionada no topo da primeira página, delimitada por quatro marcadores ArUco e acompanhada de um código QR dentro da mesma região. A região de gabarito SHALL conter bolhas apenas das questões objetivas.

Toda folha de prova com questões discursivas SHALL conter, além da região de gabarito, **exatamente uma região discursiva por questão discursiva**, cada uma delimitada por quatro marcadores ArUco próprios e com um código QR próprio dentro dela. As regiões discursivas SHALL receber os índices `1` a `N` na ordem em que as questões discursivas aparecem na definição da prova.

Dentro de uma região escaneável, toda coordenada SHALL ser expressa como `(u,v)` no intervalo `[0,1]` relativo ao quadrilátero formado pelos quatro marcadores. Nenhuma coordenada de região SHALL ser expressa em unidade de papel.

A geometria de captura SHALL respeitar:
- bolha com diâmetro de 4,2 mm, passo horizontal de 5,2 mm, passo vertical de 6,0 mm e traço de 0,22 mm;
- marcador ArUco da região de gabarito com lado de no mínimo 12 mm;
- marcador ArUco de região discursiva com lado de no mínimo 10 mm;
- zona de silêncio de no mínimo um módulo em volta de todo marcador.

Os mínimos de lado SHALL valer no pior caso da faixa de reescala de impressão, e não só no valor nominal (ADR-0001).

Os quatro marcadores da região de índice `k` SHALL usar os identificadores `4k` a `4k+3`.

#### Scenario: Coordenadas dentro da faixa normalizada

- **WHEN** um `LayoutMap` com região escaneável é produzido
- **THEN** toda coordenada de bolha, de moldura e de área de resposta dentro de cada região está no intervalo `[0,1]` nos dois eixos

#### Scenario: Identificadores dos marcadores

- **WHEN** a região de gabarito de índice `0` é emitida
- **THEN** seus quatro marcadores usam os identificadores `0`, `1`, `2` e `3`

#### Scenario: Identificadores dos marcadores da região discursiva

- **WHEN** uma prova com duas questões discursivas é calculada
- **THEN** a região da primeira discursiva tem índice `1` e marcadores `4` a `7`, e a da segunda tem índice `2` e marcadores `8` a `11`

#### Scenario: Gabarito só com objetivas

- **WHEN** uma prova com questões objetivas e discursivas é calculada
- **THEN** a região de gabarito tem bolhas para cada objetiva e para nenhuma discursiva

#### Scenario: Zona de silêncio preservada

- **WHEN** um marcador ArUco é posicionado, em qualquer região
- **THEN** nenhum outro elemento desenhável ocupa a zona de silêncio de no mínimo um módulo ao redor dele

#### Scenario: Passo das bolhas alinhado à grade

- **WHEN** uma linha de bolhas é emitida
- **THEN** o passo vertical entre linhas é 6,0 mm, múltiplo da grade de 3 mm

### Requirement: Recusa de entrada não suportada

Substitui a versão que recusava toda questão discursiva. O escopo mudou: a discursiva com rubrica passou a ser aceita.

A definição de prova aceita nesta capacidade SHALL conter questões objetivas e, opcionalmente, questões discursivas. Qualquer questão pode declarar fórmula em bloco e fórmula em linha. Uma questão discursiva SHALL declarar uma rubrica analítica com ao menos um critério. Cada critério SHALL ter pontos positivos, ao menos um descritor e `expected_lines` de no mínimo 1.

SHALL ser recusada com erro identificável a definição que contiver:
- questão discursiva sem rubrica;
- questão discursiva com alternativas;
- questão objetiva com rubrica;
- rubrica cujos pontos não somam a pontuação da questão;
- critério com pontos ou `expected_lines` não positivos, ou sem descritor;
- nenhuma questão objetiva;
- mais questões discursivas do que o dicionário de marcadores comporta;
- imagem de enunciado.

O sistema SHALL NOT produzir um layout parcial, aproximado ou silenciosamente degradado para entrada não suportada.

#### Scenario: Questão discursiva com rubrica é aceita

- **WHEN** uma definição de prova com questões objetivas e uma discursiva com rubrica válida é submetida ao cálculo
- **THEN** o cálculo prossegue e o `LayoutMap` inclui a região discursiva daquela questão

#### Scenario: Questão discursiva na entrada

- **WHEN** uma definição de prova contém questão discursiva sem rubrica
- **THEN** o cálculo falha com erro que identifica a questão e a rubrica ausente, e nenhum `LayoutMap` é produzido

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

## ADDED Requirements

### Requirement: A questão discursiva é um bloco indivisível com a sua moldura

O enunciado de uma questão discursiva e a região discursiva dela SHALL formar um bloco indivisível. Esse bloco SHALL NOT ser partido entre duas páginas nem entre duas colunas. O enunciado SHALL ficar **fora** da região discursiva: a moldura contém apenas a área de resposta.

A altura da área de resposta SHALL ser a soma dos `expected_lines` dos critérios da rubrica, multiplicada pela pauta de 8,6 mm. A altura do bloco SHALL ser múltiplo da grade de 3 mm. Um bloco discursivo que exceda a área útil de uma coluna SHALL produzir erro explícito que identifique a questão, e nenhum layout SHALL ser emitido.

#### Scenario: Enunciado e moldura não se separam

- **WHEN** uma questão discursiva não cabe inteira no espaço restante da coluna corrente
- **THEN** o enunciado e a moldura começam juntos na coluna seguinte

#### Scenario: A rubrica dimensiona a moldura

- **WHEN** duas provas diferem só nos `expected_lines` de um critério de uma discursiva
- **THEN** a área de resposta daquela questão difere em altura por exatamente a diferença de linhas vezes 8,6 mm, e o resto da geometria da região é o mesmo

#### Scenario: O enunciado fica fora da moldura

- **WHEN** a região discursiva de uma questão é emitida
- **THEN** nenhum texto do enunciado daquela questão está dentro do quadrilátero da região

#### Scenario: Moldura maior que a coluna

- **WHEN** a rubrica de uma questão discursiva pede mais linhas do que cabem numa coluna
- **THEN** o cálculo falha com erro que identifica a questão, e nenhum `LayoutMap` é produzido

### Requirement: A região discursiva declara a questão e a área de resposta

Toda região discursiva SHALL declarar:
- a questão a que pertence;
- a área de resposta, como retângulo em coordenadas normalizadas ao quadrilátero da região;
- o retângulo do QR dela.

A área de resposta SHALL NOT sobrepor o QR da região nem a zona de silêncio de nenhum marcador. Uma região discursiva SHALL NOT declarar bolhas.

A validação do `LayoutMap` sem renderizar SHALL recusar, apontando qual verificação falhou:
- região discursiva sem questão;
- área de resposta fora de `[0,1]` ou sobreposta ao QR;
- duas regiões apontando a mesma questão;
- região cujo QR declarado não existe entre as primitivas da página dela.

#### Scenario: Região discursiva completa

- **WHEN** uma prova com uma questão discursiva é calculada
- **THEN** a região discursiva declara a questão, uma área de resposta dentro de `[0,1]` e um QR que existe na página dela, e nenhuma bolha

#### Scenario: Área de resposta sobre o QR

- **WHEN** um `LayoutMap` declara uma região discursiva cuja área de resposta sobrepõe o QR da região
- **THEN** a validação recusa o mapa e identifica a região

#### Scenario: Duas regiões para a mesma questão

- **WHEN** um `LayoutMap` declara duas regiões discursivas que apontam a mesma questão
- **THEN** a validação recusa o mapa e identifica a questão

#### Scenario: QR declarado que não existe

- **WHEN** uma região declara um QR que não está entre as primitivas da página dela
- **THEN** a validação recusa o mapa e identifica a região
