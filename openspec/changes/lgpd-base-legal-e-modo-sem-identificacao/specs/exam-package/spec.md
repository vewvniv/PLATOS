## ADDED Requirements

### Requirement: O roster obedece ao modo de identificação que a organização declara

Toda organização SHALL declarar em qual modo de identificação de aluno ela opera: **nominal**, em que o roster guarda nome civil e identificadores da instituição, ou **codificado**, em que o aluno é conhecido apenas por número de chamada, código ou apelido escolhido por quem cadastra.

O modo SHALL ser propriedade da organização, e não da prova nem da linha de roster: é a postura de tratamento de dados de uma organização inteira, e uma prova em modo diferente das outras tornaria a postura indeterminável.

O modo codificado SHALL ser o padrão de uma organização recém-criada. Operar em modo nominal SHALL ser uma escolha explícita, e não o que acontece por omissão.

Em modo codificado, o roster SHALL recusar identificador emitido pela instituição — matrícula ou referência externa —, porque esse dado só existe para ligar o aluno ao cadastro escolar e não tem uso pedagógico dentro da plataforma.

O sistema SHALL NOT afirmar que distingue um nome civil de um apelido. Essa distinção não é decidível pelo conteúdo do campo, e o modo declarado é o que registra a intenção de quem cadastrou.

Mudar o modo de uma organização SHALL NOT alterar nenhum pacote publicado nem o hash de qualquer artefato imutável.

#### Scenario: Organização recém-criada nasce em modo codificado

- **WHEN** uma organização é criada
- **THEN** ela opera em modo codificado até que alguém a mude explicitamente

#### Scenario: Matrícula recusada em modo codificado

- **WHEN** uma linha de roster com matrícula ou referência externa é inserida numa organização em modo codificado
- **THEN** a operação é recusada, e a mensagem identifica o modo declarado

#### Scenario: Matrícula aceita em modo nominal

- **WHEN** a mesma linha é inserida numa organização em modo nominal
- **THEN** a operação é aceita

#### Scenario: O modo declarado é recuperável

- **WHEN** o roster de uma prova é consultado
- **THEN** o modo de identificação em que a organização opera é recuperável junto dos dados do roster

#### Scenario: Trocar de modo não toca no artefato publicado

- **WHEN** uma organização com prova já publicada muda de modo de identificação
- **THEN** o hash do pacote continua o mesmo e o pacote segue verificável
