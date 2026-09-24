## MODIFIED Requirements

### Requirement: Pacote incoerente é recusado antes de ser publicado

Um pacote SHALL ser recusado, com erro identificável, quando não for internamente coerente. São incoerências:
- posição de variante que aponta item inexistente;
- item objetivo sem gabarito;
- item discursivo com gabarito, ou sem rubrica;
- item objetivo com rubrica;
- rubrica cujos pontos não somam a pontuação do item;
- atribuição que aponta variante inexistente;
- layout que declara questões diferentes das que os itens declaram;
- item discursivo que não tem **exatamente uma** região discursiva no layout de cada variante;
- `fully_offline_gradable` que diz o contrário do que os itens declaram;
- **atribuição sem a folha dela** — isto é, sem o payload do QR de **cada** região do layout da variante dela;
- QR de atribuição cujo payload não traz o token daquela atribuição, ou não traz o índice da região a que ele está associado.

A incoerência inversa, layout órfão de atribuição, não entra nesta lista porque ela é irrepresentável pela forma do pacote: ver "Publicar com roster produz uma atribuição e uma folha por aluno".

Nenhum pacote parcial SHALL ser gravado.

#### Scenario: Variante aponta item inexistente

- **WHEN** uma variante mapeia uma posição física para um item que o pacote não declara
- **THEN** a publicação falha com erro que identifica a posição e nada é gravado

#### Scenario: Item sem gabarito

- **WHEN** um item objetivo do pacote não tem alternativa correta declarada
- **THEN** a publicação falha com erro que identifica o item e nada é gravado

#### Scenario: Item discursivo com gabarito

- **WHEN** um item discursivo do pacote tem entrada no gabarito
- **THEN** a publicação falha com erro que identifica o item e nada é gravado

#### Scenario: Item discursivo sem região

- **WHEN** o layout de uma variante não tem região discursiva para um item discursivo, e o pacote é coerente em todo o resto
- **THEN** a publicação falha com erro que identifica o item e a variante, e nada é gravado

#### Scenario: Layout diverge dos itens

- **WHEN** o layout do pacote declara um conjunto de questões diferente do que os itens declaram
- **THEN** a publicação falha com erro que aponta a divergência e nada é gravado

#### Scenario: Correção offline declarada contra os itens

- **WHEN** o pacote declara `fully_offline_gradable` verdadeiro e tem item discursivo
- **THEN** a publicação falha com erro que identifica a contradição e nada é gravado

#### Scenario: Atribuição sem folha

- **WHEN** o pacote declara uma atribuição sem o payload do QR que a identifica
- **THEN** a publicação falha com erro que identifica a atribuição e nada é gravado

#### Scenario: Atribuição sem o QR de uma região

- **WHEN** uma atribuição traz QR para a região de gabarito e não para uma região discursiva da variante dela
- **THEN** a publicação falha com erro que identifica a atribuição e a região, e nada é gravado

#### Scenario: QR de atribuição associado à região errada

- **WHEN** o QR que uma atribuição associa à região `1` traz no payload o índice `2`
- **THEN** a publicação falha com erro que identifica a atribuição e as duas regiões, e nada é gravado

#### Scenario: Token repetido entre atribuições

- **WHEN** o pacote declara duas atribuições com o mesmo token
- **THEN** a publicação falha com erro que identifica o token e nada é gravado

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
aluno SHALL viajar **dentro** da atribuição: **um QR por região** do layout da variante dela, cada um
com o payload e a matriz que dele decorre, identificado pelo índice da região. Isso SHALL NOT viver em
uma entrada endereçada por token ao lado da atribuição. A geometria SHALL continuar sendo uma por
variante.

Disso decorre uma garantia, e ela é da **forma** e não de uma conferência: **folha sem atribuição é
irrepresentável**. Não há onde colocar uma — não existe mapa endereçado por token onde uma entrada
órfã pudesse sobrar —, então o pacote não precisa recusar esse estado, porque ele não pode ser
construído. A recusa que resta é a inversa, que **é** representável: atribuição sem a folha dela, ou
sem o QR de uma das regiões dela.

#### Scenario: Roster com alunos produz uma folha para cada um

- **WHEN** uma prova é publicada com um roster de N alunos
- **THEN** o pacote traz N atribuições, cada uma com um layout endereçável por ela, e nenhum nome,
  turma ou matrícula aparece no pacote

#### Scenario: A atribuição traz um QR por região

- **WHEN** uma prova com uma questão objetiva e duas discursivas é publicada com roster
- **THEN** cada atribuição traz três QRs, um para cada região `0`, `1` e `2`, todos com o token dela

#### Scenario: Publicar sem roster continua válido

- **WHEN** uma prova é publicada sem nenhum aluno no roster
- **THEN** o pacote é válido, não traz atribuição, e a folha derivada dele traz o campo de aluno vazio

#### Scenario: Token não se repete dentro da prova

- **WHEN** um roster é submetido com dois alunos que resolveriam para o mesmo token
- **THEN** a publicação falha com erro que identifica o token repetido e nada é gravado

## ADDED Requirements

### Requirement: O item discursivo publica a sua rubrica

Todo item do pacote SHALL declarar se é objetivo ou discursivo.

O item discursivo SHALL levar a sua rubrica analítica: os critérios, cada um com descritores, pontos e
`expected_lines`. SHALL levar também o modo de captura da resposta, que por padrão é em cinza e que a
questão pode declarar em cor (§8). O item objetivo SHALL NOT levar rubrica nem modo de captura.

A pontuação máxima da prova SHALL incluir os pontos dos itens discursivos. O gabarito SHALL conter
apenas itens objetivos. `fully_offline_gradable` SHALL ser falso exatamente quando a prova tem item
discursivo.

O conjunto de regiões que a folha de uma variante deve ter SHALL ser lido do layout daquela variante,
e SHALL NOT ser declarado uma segunda vez em outro lugar do pacote.

#### Scenario: A rubrica chega ao pacote

- **WHEN** uma prova com uma discursiva é publicada
- **THEN** o item discursivo do pacote traz os critérios da rubrica, com descritores, pontos e `expected_lines` iguais aos da definição, e o modo de captura

#### Scenario: Prova com discursiva não é corrigível só no aparelho

- **WHEN** uma prova com ao menos uma discursiva é publicada
- **THEN** o pacote declara `fully_offline_gradable` falso, a pontuação máxima inclui os pontos da discursiva, e o gabarito não traz entrada para ela

#### Scenario: Prova só objetiva continua corrigível no aparelho

- **WHEN** uma prova só com objetivas é publicada
- **THEN** o pacote declara `fully_offline_gradable` verdadeiro, e nenhum item traz rubrica
