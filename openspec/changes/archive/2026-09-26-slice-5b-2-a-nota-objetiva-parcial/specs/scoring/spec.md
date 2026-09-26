## ADDED Requirements

### Requirement: A parte objetiva de uma prova com discursiva é apurada como parcial

Quando o pacote declara que a prova não é corrigível só no aparelho (`fully_offline_gradable` falso), a apuração SHALL oferecer uma **apuração parcial** da folha, restrita aos itens objetivos da variante. Os itens objetivos são os que o pacote declara como objetivos, e ela SHALL NOT criar um segundo registro desse fato.

A apuração parcial SHALL seguir as regras da apuração completa, restritas a esse conjunto:
- a variante sai do payload;
- o gabarito vem do pacote;
- em branco é erro, e ambígua é pendência;
- cada questão objetiva leva a sua evidência;
- a apuração é local, determinística e sem efeito.

Ela SHALL recusar, informando a divergência, a folha cujo conjunto de itens lidos não corresponda ao conjunto de itens **objetivos** da variante.

O resultado parcial SHALL declarar:
- a pontuação objetiva apurada e a pontuação máxima **da parte objetiva**, que é a soma do gabarito;
- a pontuação máxima **da prova**, como o pacote a declara;
- cada questão discursiva da variante como **aguardando correção**, com a pontuação que o pacote declara para ela;
- as pendências de revisão das objetivas, como na apuração completa;
- o pacote e a variante contra os quais foi apurado.

A pontuação máxima objetiva somada à das discursivas aguardando correção SHALL ser igual à pontuação máxima da prova.

O resultado parcial SHALL NOT ser declarado fechado nem apresentado como nota final, em nenhuma hipótese, nem quando nenhuma objetiva está pendente: a parte discursiva ainda não foi corrigida (D4). Ele SHALL ser um resultado **distinto** da nota da apuração completa, de modo que nenhum consumidor da nota completa o aceite em seu lugar.

A apuração parcial SHALL recusar a prova que é corrigível só no aparelho, porque para ela a nota completa já é a nota. A apuração completa de uma prova com discursiva continua recusada como antes.

#### Scenario: Parcial de uma prova com discursiva

- **WHEN** a folha de uma prova com quatro objetivas de 1 ponto e duas discursivas que somam 7 pontos, com pontuação máxima 11, é apurada parcialmente, com três objetivas certas e uma errada
- **THEN** o resultado declara 3 pontos de 4 na parte objetiva, as duas discursivas aguardando correção com os seus pontos, e a pontuação máxima da prova 11

#### Scenario: A parcial nunca é fechada

- **WHEN** todas as objetivas de uma prova com discursiva estão marcadas ou em branco, sem pendência
- **THEN** o resultado parcial não é declarado fechado, e continua listando as discursivas aguardando correção

#### Scenario: Os máximos fecham com a prova

- **WHEN** uma apuração parcial é produzida
- **THEN** a pontuação máxima objetiva somada à das discursivas aguardando correção é igual à pontuação máxima da prova

#### Scenario: Conjunto objetivo divergente

- **WHEN** a leitura de uma folha de prova com discursiva traz uma objetiva a menos, ou um item que não é objetivo da variante
- **THEN** a apuração parcial é recusada e a mensagem identifica a divergência

#### Scenario: Ambígua continua sendo pendência

- **WHEN** uma objetiva de uma prova com discursiva tem múltipla marcação
- **THEN** ela entra nas pendências da parcial, não rende ponto, e o máximo ainda em disputa a inclui

#### Scenario: Parcial de prova só objetiva é recusada

- **WHEN** a apuração parcial é pedida para uma prova que é corrigível só no aparelho
- **THEN** ela é recusada, e a mensagem diz que essa prova tem nota completa

#### Scenario: A parcial não substitui a nota

- **WHEN** um consumidor que recebe a nota completa de uma folha recebe, em vez dela, um resultado parcial
- **THEN** ele não o aceita como nota, porque são resultados distintos

#### Scenario: A apuração completa de prova com discursiva continua recusada

- **WHEN** a apuração completa é pedida para a folha de uma prova com discursiva
- **THEN** ela é recusada pela divergência de itens, como antes desta mudança
