## MODIFIED Requirements

### Requirement: O resultado mostra a nota e o que a sustenta

O resultado SHALL apresentar a pontuação apurada e a pontuação máxima da prova.

O resultado SHALL apresentar **de quem é a folha**, pelo nome de apresentação que o roster guardado
traz para o token lido. Apresentar apenas o token é apresentar uma nota sem dono: quem corrige
precisa saber a quem devolver, e foi para isso que ADR-0002 tirou o nome do pacote imutável e o pôs
num roster entregue ao lado.

Quando o token lido **não tiver linha no roster**, o resultado SHALL apresentar o token e SHALL dizer
que aquela folha não está no roster desta prova. SHALL NOT apresentar a folha como ilegível nem
recusar o resultado: a nota foi apurada e é válida, e a folha avulsa de um aluno fora da lista é o
caso que `exam-package` separa ao entregar roster vazio em vez de negar a prova.

O nome apresentado a partir de roster guardado SHALL seguir a marcação de dado cacheado de
`device-session`, e SHALL NOT ser apresentado como fresco. A regra mora lá, sobre todo dado guardado;
aqui ela só é aplicada — esta fatia estende o alcance daquele requisito para o roster, que a visão
guardada explicitamente não cobre.

Havendo questão pendente de revisão humana, o resultado SHALL apresentá-la como pendência, com o motivo, e SHALL NOT apresentar a nota como fechada.

A cobertura medida de cada bolha SHALL permanecer recuperável a partir do resultado. Quem revisa uma pendência precisa do número que a produziu, e não apenas da palavra "indecisa".

#### Scenario: Folha sem pendência

- **WHEN** uma folha em que toda questão tem alternativa marcada ou está em branco é lida
- **THEN** o resultado apresenta a pontuação, o máximo e o nome do aluno daquele token, e a nota é
  apresentada como fechada

#### Scenario: Folha com pendência

- **WHEN** uma folha com múltipla marcação ou questão indecisa é lida
- **THEN** o resultado lista as questões pendentes com seus motivos, e a nota não é apresentada como fechada

#### Scenario: A cobertura sobrevive até a tela

- **WHEN** um resultado é apresentado
- **THEN** a cobertura medida de cada bolha continua recuperável a partir dele

#### Scenario: Folha de aluno fora do roster

- **WHEN** uma folha cujo token não tem linha no roster guardado desta prova é lida
- **THEN** o resultado apresenta a nota apurada e o token, diz que a folha não está no roster desta
  prova, e não é apresentado como falha de leitura

#### Scenario: O nome vem do cache e é marcado

- **WHEN** o resultado é apresentado com o nome vindo do roster guardado no aparelho
- **THEN** o nome carrega a marca de dado cacheado, e não é apresentado como fresco
