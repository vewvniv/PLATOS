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
