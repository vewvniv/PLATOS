## ADDED Requirements

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
