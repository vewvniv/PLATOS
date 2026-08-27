## ADDED Requirements

### Requirement: Todo armazenamento de dado pessoal declara finalidade e classe de retenção

Toda tabela que armazena dado pessoal SHALL declarar, no próprio armazenamento, a finalidade do tratamento e a classe de retenção a que pertence. A declaração SHALL viver junto da estrutura, e não em documento paralelo que pode envelhecer sem que nada acuse.

A classe de retenção declarada SHALL ser uma das classes definidas na política de privacidade vigente. Uma tabela SHALL NOT declarar uma classe que a política não define, nem ficar sem classe.

O sistema SHALL recusar a construção quando uma tabela com dado pessoal existir sem essas duas declarações. Sem isso a regra é critério de revisão de código, e critério de revisão é o que se esquece na migration seguinte.

Esta exigência SHALL NOT ser promovida a invariante do sistema. Ela é guarda de construção, verificável sobre o catálogo; as invariantes desta base valem por reprovarem um desenho concreto, e diluí-las com uma regra de processo enfraquece as que existem.

#### Scenario: Tabela com dado pessoal sem declaração

- **WHEN** uma tabela que armazena dado pessoal existe sem finalidade ou sem classe de retenção declarada
- **THEN** a construção é recusada, e a mensagem nomeia a tabela e o que falta nela

#### Scenario: Classe que a política não define

- **WHEN** uma tabela declara uma classe de retenção que não consta da política de privacidade vigente
- **THEN** a construção é recusada, e a mensagem nomeia a classe encontrada e as admitidas

#### Scenario: Tabela sem dado pessoal

- **WHEN** uma tabela que não armazena dado pessoal existe sem essas declarações
- **THEN** a construção prossegue, porque a exigência alcança apenas dado pessoal

#### Scenario: A guarda reage a uma declaração removida

- **WHEN** a declaração de retenção de uma tabela que hoje a tem é removida
- **THEN** a construção passa a ser recusada
