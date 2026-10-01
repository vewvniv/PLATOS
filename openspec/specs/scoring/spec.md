## Purpose

Transformar as respostas lidas de uma folha em nota objetiva, contra o gabarito e a pontuação que o `ExamPackage` publicado declara. A nota é apurada no próprio aparelho, sem rede, e é definitiva quando não há questão discursiva nem questão pendente de revisão.

## Requirements

### Requirement: A nota vem do pacote publicado, e da variante que a folha declara

A apuração SHALL usar o gabarito, a pontuação por item e a pontuação máxima declarados no `ExamPackage` publicado. Ela SHALL casar cada resposta lida com a entrada de gabarito do **item** correspondente, e SHALL recusar item lido que o gabarito não cubra. Ela SHALL NOT usar gabarito de outra origem.

A posição física na folha já foi resolvida em item pelo `LayoutMap` da variante, na publicação: a leitura entrega item, e a apuração não SHALL refazer esse mapeamento.

A apuração SHALL determinar a variante da folha a partir do payload do QR. Quando o payload declara uma variante, ela SHALL existir no pacote, e a apuração SHALL recusar a folha quando não existir. Quando o payload não declara variante, a apuração SHALL prosseguir apenas se o pacote declarar exatamente uma variante, e SHALL recusar a folha quando o pacote declarar mais de uma — escolher uma delas em silêncio atribuiria a folha ao gabarito errado.

A apuração SHALL recusar a folha cujo conjunto de itens lidos não corresponda ao conjunto que a variante declara, informando a divergência.

O pacote é imutável e hasheado: a apuração SHALL registrar contra qual pacote e contra qual variante a nota foi apurada, para que a nota nunca seja lida como se valesse para outra versão da prova.

#### Scenario: Folha de uma variante conhecida

- **WHEN** as respostas de uma folha são apuradas contra o pacote que a gerou
- **THEN** cada item lido é casado com sua entrada de gabarito, e a nota é apurada contra ela

#### Scenario: Variante que o pacote não conhece

- **WHEN** o payload da folha declara uma variante ausente do pacote
- **THEN** a apuração é recusada e a mensagem nomeia a variante encontrada

#### Scenario: Payload sem variante, pacote com uma só

- **WHEN** o payload não declara variante e o pacote declara exatamente uma
- **THEN** a apuração prossegue contra essa variante, e o resultado a identifica

#### Scenario: Payload sem variante, pacote com mais de uma

- **WHEN** o payload não declara variante e o pacote declara mais de uma
- **THEN** a apuração é recusada, e nenhuma variante é escolhida por padrão

#### Scenario: Conjunto de itens divergente

- **WHEN** o conjunto de itens lidos não corresponde ao que a variante declara
- **THEN** a apuração é recusada e a mensagem identifica a divergência

#### Scenario: Item que o gabarito não cobre

- **WHEN** uma resposta lida se refere a um item sem entrada no gabarito
- **THEN** a apuração é recusada e a mensagem nomeia o item

#### Scenario: A nota diz de qual pacote ela é

- **WHEN** uma nota é apurada
- **THEN** o resultado identifica o pacote publicado e a variante contra os quais ela foi apurada

### Requirement: Em branco é erro; ambígua é pendência

Questão em branco SHALL valer zero ponto e ser definitiva: o aluno não marcou, e isso é resultado, não dúvida.

Questão com múltipla marcação ou resultado indeciso SHALL ser relatada como pendente de revisão humana. Ela SHALL NOT valer zero, SHALL NOT ser contada como acerto, e SHALL NOT ser resolvida por nenhuma regra automática — revisão humana vence qualquer resultado automático.

O resultado SHALL declarar se a nota está fechada. Havendo qualquer questão pendente, a nota apurada SHALL vir acompanhada da lista de pendências e do máximo ainda em disputa, e SHALL NOT ser apresentada como final.

#### Scenario: Prova sem pendência

- **WHEN** todas as questões têm alternativa marcada ou estão em branco
- **THEN** a nota é apurada e declarada fechada

#### Scenario: Questão em branco

- **WHEN** uma questão está em branco
- **THEN** ela vale zero ponto, e não entra na lista de pendências

#### Scenario: Questão com múltipla marcação

- **WHEN** uma questão tem múltipla marcação
- **THEN** ela entra na lista de pendências, não recebe ponto nem zero definitivo, e a nota não é declarada fechada

#### Scenario: Questão indecisa

- **WHEN** uma questão tem resultado indeciso
- **THEN** ela entra na lista de pendências, e a nota não é declarada fechada

#### Scenario: O máximo em disputa é declarado

- **WHEN** uma nota é apurada com pendências
- **THEN** o resultado informa a pontuação já apurada, a pontuação máxima da prova, e quanto ainda depende das pendências

### Requirement: A apuração é local, determinística e sem efeito

A apuração SHALL concluir sem rede, contra o pacote em cache. As mesmas respostas apuradas duas vezes contra o mesmo pacote SHALL produzir exatamente o mesmo resultado.

A apuração SHALL NOT alterar a leitura, o pacote nem qualquer estado persistido, e SHALL NOT depender de nenhum estado que não venha da leitura e do pacote.

#### Scenario: Sem rede

- **WHEN** a apuração ocorre com o aparelho offline
- **THEN** ela conclui normalmente, porque nada nela depende de rede

#### Scenario: Apuração repetida

- **WHEN** as mesmas respostas são apuradas duas vezes contra o mesmo pacote
- **THEN** os dois resultados são idênticos

### Requirement: A nota apurada carrega a evidência de cada questão

O resultado da apuração SHALL levar, além do total e das pendências, **o que foi apurado em cada
questão**: qual item, o que a folha respondeu, quantos pontos aquele item valia e quantos pontos ele
rendeu.

Essa evidência SHALL ser derivada da mesma apuração que produziu o total, e SHALL ser coerente com
ele: a soma dos pontos rendidos por questão SHALL ser igual ao total apurado, e os itens relatados
como pendentes SHALL ser exatamente os da lista de pendências.

A evidência SHALL NOT atribuir pontuação a habilidade, e SHALL NOT introduzir qualquer unidade de nota
que não seja a pontuação por questão que o pacote declara. Habilidade é dimensão analítica: o vínculo
item→habilidade continua vivendo no `ExamPackage`, e nada nesta capacidade o converte em nota.

#### Scenario: Cada questão é relatada

- **WHEN** uma nota é apurada
- **THEN** o resultado relata, para cada questão da variante, o item, a resposta lida, quanto ela valia
  e quanto rendeu

#### Scenario: A evidência fecha com o total

- **WHEN** uma nota é apurada
- **THEN** a soma dos pontos rendidos nas questões é igual ao total apurado

#### Scenario: Questão pendente rende zero na evidência e continua pendente

- **WHEN** uma questão tem múltipla marcação ou resultado indeciso
- **THEN** ela aparece na evidência rendendo zero ponto, e continua na lista de pendências como questão
  não decidida

#### Scenario: Questão em branco é erro definitivo na evidência

- **WHEN** uma questão está em branco
- **THEN** ela aparece na evidência rendendo zero ponto, e não aparece entre as pendências

#### Scenario: Nenhuma pontuação por habilidade

- **WHEN** uma nota é apurada para um pacote cujos itens declaram habilidades
- **THEN** o resultado não atribui pontos a habilidade alguma, e a nota continua sendo a soma dos
  pontos por questão

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
