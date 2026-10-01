# Spec Delta

## ADDED Requirements

### Requirement: A nota do professor completa a parcial sem recalcular a parte objetiva

Dada a apuração parcial de uma folha (requisito "A parte objetiva de uma prova com discursiva é apurada como parcial") e a **pontuação que o professor deu a cada questão discursiva da variante**, a apuração SHALL produzir o **resultado completo** da folha, local, determinístico e sem efeito, como as demais apurações.

A pontuação dada a cada discursiva SHALL ser um **número decimal exato com até 2 casas decimais**, de 0 até a pontuação que o pacote declara para aquela questão, inclusive; 1.5 e 1.75 são pontuações válidas. A pontuação SHALL NOT ser representada nem somada em ponto flutuante binário: valores de mesma magnitude são o mesmo valor (`1.5` e `1.50`), e a aritmética do resultado SHALL ser exata. A pontuação que o pacote declara para a questão continua inteira. O resultado completo SHALL declarar:
- o total: a pontuação objetiva da parcial **somada** à pontuação dada às discursivas;
- a pontuação máxima da prova, como o pacote a declara;
- as pendências de revisão das objetivas, **exatamente** as da parcial: dar nota à discursiva SHALL NOT resolver, alterar nem recalcular nenhuma questão objetiva;
- o resultado de cada questão objetiva, como na parcial, e **o de cada discursiva**: qual item, quanto valia e quantos pontos o professor lhe deu, identificada como corrigida pelo professor.

O resultado SHALL ser **fechado** se, e somente se, não restar pendência objetiva. Pendência objetiva (múltipla marcação, indecisa) mantém o resultado **não fechado** mesmo com todas as discursivas pontuadas, porque a nota do professor é sobre a discursiva e não decide a objetiva.

O total do resultado completo SHALL poder ser fracionário. Os resultados da apuração objetiva e da parcial SHALL NOT mudar: seus pontos continuam inteiros. A soma dos pontos rendidos por questão, objetivas e discursivas, SHALL ser **exatamente** igual ao total, e a pontuação máxima das objetivas somada à das discursivas SHALL ser igual à da prova.

A apuração SHALL recusar, informando o que não fecha:
- pontuação de discursiva fora de 0..valor, negativa, com mais de 2 casas decimais, ou não numérica;
- pontuação dada a questão que **não é discursiva da variante** (inclusive objetiva e item de outra variante);
- discursiva da variante **sem** pontuação, ou pontuada mais de uma vez: a nota do professor cobre todas as discursivas da variante, e nota de parte delas não é representável nesta capacidade;
- prova corrigível só no aparelho: para ela a nota completa já é a nota, e não há discursiva a pontuar.

O resultado completo SHALL ser um resultado **distinto** da parcial, de modo que nenhum consumidor que aceita a parcial o confunda com ela, e SHALL NOT atribuir pontuação a habilidade.

#### Scenario: Completar a parcial

- **WHEN** a parcial de uma prova com quatro objetivas de 1 ponto (três certas, uma errada) e duas discursivas de 3 e 4 pontos, máximo 11, recebe 1.5 e 3.75 pontos do professor
- **THEN** o resultado completo declara total 8.25 de 11, as duas discursivas com seus valores e pontos, e a parte objetiva exatamente como estava

#### Scenario: Sem pendência objetiva, a nota fecha

- **WHEN** a parcial não tem pendência objetiva e todas as discursivas são pontuadas
- **THEN** o resultado completo é declarado fechado

#### Scenario: Pendência objetiva mantém a nota aberta

- **WHEN** a parcial tem uma objetiva com múltipla marcação e todas as discursivas são pontuadas
- **THEN** o resultado completo lista essa pendência, inclui a pontuação dela no quanto está em disputa, e não é declarado fechado

#### Scenario: A nota do professor não mexe na objetiva

- **WHEN** o professor pontua as discursivas de uma parcial
- **THEN** a pontuação, a evidência e as pendências das objetivas são idênticas às da parcial

#### Scenario: Pontuação acima do valor

- **WHEN** o professor dá 5 pontos a uma discursiva que vale 4
- **THEN** a apuração é recusada e a mensagem diz qual questão e qual faixa

#### Scenario: Pontuação fracionária válida

- **WHEN** o professor dá 1.5 a uma discursiva e 1.75 a outra, ambas dentro do valor
- **THEN** a apuração é aceita, e a evidência de cada discursiva traz exatamente 1.5 e 1.75

#### Scenario: A soma é exata

- **WHEN** o professor dá 0.1 a uma discursiva e 0.2 a outra
- **THEN** a soma das duas é exatamente 0.3, e o total e a soma da evidência por questão são iguais sem diferença de arredondamento

#### Scenario: Mesmo valor, escrita diferente

- **WHEN** uma pontuação chega como 1.5 e outra, igual, como 1.50
- **THEN** as duas são o mesmo valor, e produzem o mesmo resultado

#### Scenario: Pontuação com mais de 2 casas

- **WHEN** a pontuação de uma discursiva é 1.333
- **THEN** a apuração é recusada e a mensagem identifica a questão e o limite de 2 casas

#### Scenario: Pontuação negativa

- **WHEN** a pontuação de uma discursiva é negativa
- **THEN** a apuração é recusada e a mensagem identifica a questão

#### Scenario: Pontuação não numérica

- **WHEN** a pontuação de uma discursiva não é um número
- **THEN** a apuração é recusada e a mensagem identifica a questão

#### Scenario: A objetiva e a parcial continuam inteiras

- **WHEN** a apuração objetiva ou a parcial de uma folha é produzida
- **THEN** seus pontos continuam inteiros e idênticos aos de antes desta mudança

#### Scenario: Pontuação em questão que não é discursiva

- **WHEN** uma pontuação do professor aponta uma questão objetiva da variante, ou um item que a variante não tem
- **THEN** a apuração é recusada e a mensagem identifica o item

#### Scenario: Discursiva sem nota

- **WHEN** a variante tem duas discursivas e o professor pontua só uma
- **THEN** a apuração é recusada e a mensagem diz qual discursiva falta

#### Scenario: Discursiva pontuada duas vezes

- **WHEN** a mesma discursiva recebe duas pontuações
- **THEN** a apuração é recusada e a mensagem identifica o item repetido

#### Scenario: Prova só objetiva

- **WHEN** a nota do professor é pedida para uma prova corrigível só no aparelho
- **THEN** a apuração é recusada, e a mensagem diz que essa prova tem nota completa

#### Scenario: Os máximos fecham com a prova

- **WHEN** um resultado completo é produzido
- **THEN** a pontuação máxima das objetivas somada à das discursivas é igual à pontuação máxima da prova, e a soma dos pontos por questão é igual ao total

#### Scenario: A completa não é a parcial

- **WHEN** um consumidor que só aceita a parcial recebe um resultado completo, ou o contrário
- **THEN** ele não o aceita no lugar do outro, porque são resultados distintos
