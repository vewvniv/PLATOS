## ADDED Requirements

### Requirement: O payload do QR identifica a atribuição da folha

A folha produzida para uma atribuição SHALL trazer, no payload do seu código QR, o **token daquela
atribuição**. A folha produzida sem atribuição SHALL trazer o campo de aluno **vazio**, e SHALL NOT
trazer valor inventado, derivado ou de reserva.

O payload SHALL ser resolvido **no momento em que o layout é produzido**, e SHALL ficar registrado no
próprio `LayoutMap`, junto da matriz de módulos já codificada. Nenhum outro componente SHALL compor
ou recompor o payload depois disso: existe **um escritor só**, e quem lê a captura de volta usa o
mesmo codec.

#### Scenario: A folha da atribuição carrega o token dela

- **WHEN** o layout de uma folha é produzido para uma atribuição
- **THEN** o payload do QR daquela folha traz o token daquela atribuição, e ele é lido de volta
  igual a partir da própria folha

#### Scenario: Folha sem atribuição não inventa aluno

- **WHEN** o layout é produzido sem atribuição
- **THEN** o campo de aluno do payload está vazio, e nenhum valor de reserva aparece nele

#### Scenario: O payload não é recomposto na impressão

- **WHEN** o documento de uma folha é gerado a partir do pacote
- **THEN** o payload desenhado é exatamente o que o `LayoutMap` registrou, sem recodificação

### Requirement: Folhas da mesma prova diferem só no QR

Duas folhas da mesma prova e da mesma variante, produzidas para atribuições diferentes, SHALL ter
geometria **idêntica** — mesma região escaneável, mesmas posições de bolha, mesmos marcadores, mesma
paginação — e SHALL diferir apenas no payload do QR e na matriz de módulos que dele decorre.

O `LayoutMap` de cada folha SHALL continuar sendo determinístico e versionado nos mesmos termos já
exigidos: a mesma entrada produz o mesmo mapa.

#### Scenario: Duas atribuições, uma geometria

- **WHEN** os layouts de dois alunos da mesma prova e variante são comparados
- **THEN** todas as primitivas coincidem, exceto o payload do QR e a matriz de módulos dele

#### Scenario: A medição impressa não muda com o aluno

- **WHEN** a fidelidade dimensional é medida sobre a folha de um aluno e sobre a de outro
- **THEN** as duas medições satisfazem a mesma tolerância, porque a geometria é a mesma
