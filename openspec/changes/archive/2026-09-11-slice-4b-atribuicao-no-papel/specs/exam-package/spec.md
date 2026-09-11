## ADDED Requirements

### Requirement: Publicar com roster produz uma atribuição e uma folha por aluno

Publicar uma prova cujo roster tem alunos SHALL produzir exatamente **uma atribuição por aluno do
roster**, e cada atribuição SHALL ter no pacote um layout **endereçável por ela**.

O pacote SHALL levar de cada atribuição apenas o **token**. Publicar SHALL gravar o roster em
estrutura mutável própria, associada à mesma prova e fora do hash do pacote.

Publicar uma prova **sem** roster SHALL continuar produzindo pacote válido, sem atribuição alguma, e
a folha derivada dele SHALL trazer o campo de aluno **vazio**. Este caso não é erro: é a folha avulsa
do aluno fora da lista, e a atribuição dela é feita depois da captura.

Duas atribuições da mesma prova SHALL NOT compartilhar token, e um token SHALL identificar no máximo
uma atribuição dentro de uma prova.

**O que endereça a folha de uma atribuição é a própria atribuição.** O que distingue a folha de um
aluno — o payload do QR dela e a matriz que dele decorre — SHALL viajar **dentro** da atribuição, e
SHALL NOT viver em uma entrada endereçada por token ao lado dela. A geometria SHALL continuar sendo
uma por variante.

Disso decorre uma garantia, e ela é da **forma** e não de uma conferência: **folha sem atribuição é
irrepresentável**. Não há onde colocar uma — não existe mapa endereçado por token onde uma entrada
órfã pudesse sobrar —, então o pacote não precisa recusar esse estado, porque ele não pode ser
construído. A recusa que resta é a inversa, que **é** representável: atribuição sem a folha dela.

#### Scenario: Roster com alunos produz uma folha para cada um

- **WHEN** uma prova é publicada com um roster de N alunos
- **THEN** o pacote traz N atribuições, cada uma com um layout endereçável por ela, e nenhum nome,
  turma ou matrícula aparece no pacote

#### Scenario: Publicar sem roster continua válido

- **WHEN** uma prova é publicada sem nenhum aluno no roster
- **THEN** o pacote é válido, não traz atribuição, e a folha derivada dele traz o campo de aluno vazio

#### Scenario: Token não se repete dentro da prova

- **WHEN** um roster é submetido com dois alunos que resolveriam para o mesmo token
- **THEN** a publicação falha com erro que identifica o token repetido e nada é gravado

## MODIFIED Requirements

### Requirement: Pacote incoerente é recusado antes de ser publicado

Um pacote SHALL ser recusado, com erro identificável, quando não for internamente coerente. São incoerências: posição de variante que aponta item inexistente, item sem gabarito, atribuição que aponta variante inexistente, layout que declara questões diferentes das que os itens declaram, **e atribuição sem a folha dela** — isto é, sem o payload do QR que a identifica. A incoerência inversa, layout órfão de atribuição, não entra nesta lista porque ela é irrepresentável pela forma do pacote: ver "Publicar com roster produz uma atribuição e uma folha por aluno".

Nenhum pacote parcial SHALL ser gravado.

#### Scenario: Variante aponta item inexistente

- **WHEN** uma variante mapeia uma posição física para um item que o pacote não declara
- **THEN** a publicação falha com erro que identifica a posição e nada é gravado

#### Scenario: Item sem gabarito

- **WHEN** um item objetivo do pacote não tem alternativa correta declarada
- **THEN** a publicação falha com erro que identifica o item e nada é gravado

#### Scenario: Layout diverge dos itens

- **WHEN** o layout do pacote declara um conjunto de questões diferente do que os itens declaram
- **THEN** a publicação falha com erro que aponta a divergência e nada é gravado

#### Scenario: Atribuição sem folha

- **WHEN** o pacote declara uma atribuição sem o payload do QR que a identifica
- **THEN** a publicação falha com erro que identifica a atribuição e nada é gravado

#### Scenario: Token repetido entre atribuições

- **WHEN** o pacote declara duas atribuições com o mesmo token
- **THEN** a publicação falha com erro que identifica o token e nada é gravado

### Requirement: A folha impressa é derivada do pacote

O documento entregue à impressão SHALL ser produzido a partir do layout que o pacote publica, e SHALL NOT ser produzido a partir de uma fonte de geometria paralela.

**Quando o pacote traz atribuições, a impressão SHALL produzir um documento por atribuição**, e cada documento SHALL usar o layout endereçado por aquela atribuição. Nenhum documento SHALL ser produzido a partir de um layout que o pacote não enderece.

A equivalência entre plataformas e a fidelidade dimensional SHALL continuar valendo, agora medidas sobre o documento derivado do pacote.

#### Scenario: PDF vem do pacote

- **WHEN** o documento é gerado para uma prova publicada
- **THEN** a geometria desenhada é a que o pacote declara

#### Scenario: Um documento por aluno

- **WHEN** a impressão é pedida para uma prova publicada com N atribuições
- **THEN** são produzidos N documentos, cada um com o layout da sua atribuição

#### Scenario: Divergência entre plataformas continua barrada

- **WHEN** os documentos das duas plataformas, derivados do mesmo pacote, são comparados
- **THEN** eles coincidem dentro da mesma tolerância exigida hoje
