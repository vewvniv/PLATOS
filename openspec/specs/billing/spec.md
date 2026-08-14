## Purpose

Define a quem pertence a assinatura, como o saldo de créditos é registrado e como os direitos efetivos de uma organização são resolvidos a partir de arquivos de plano versionados. Nesta fatia a capacidade é inerte: ela responde qual é o direito, mas nada ainda é recusado por falta dele.

## Requirements

### Requirement: Assinatura pertence à organização

Uma assinatura SHALL ser atribuída a uma organização, nunca a um usuário. Uma organização SHALL ter no máximo uma assinatura não encerrada por vez.

Uma assinatura SHALL declarar o plano, a periodicidade de cobrança, o estado e o início e fim do período corrente.

Usuários que pertencem a várias organizações SHALL ter direitos independentes em cada uma, sem qualquer combinação, herança ou reconciliação entre assinaturas.

#### Scenario: Direitos independentes por contexto

- **WHEN** um usuário pertence a uma organização com plano `basic` e a outra com plano `pro`
- **THEN** a resolução de direito para a primeira devolve os direitos de `basic` e para a segunda os de `pro`, sem mistura

#### Scenario: Segunda assinatura ativa recusada

- **WHEN** uma segunda assinatura não encerrada é criada para uma organização que já possui uma
- **THEN** a operação é recusada e a assinatura existente permanece intacta

#### Scenario: Assinatura sem organização

- **WHEN** uma assinatura é criada sem organização atribuída
- **THEN** a operação é recusada

### Requirement: Ledger de créditos append-only

Lançamentos de crédito SHALL ser atribuídos a uma organização e SHALL ser somente-inserção: uma vez gravado, um lançamento SHALL NOT ser alterado nem removido.

Cada lançamento SHALL registrar a organização, o tipo de crédito, a quantidade com sinal, o motivo e o instante do lançamento.

O saldo de uma organização SHALL ser a soma dos lançamentos dessa organização para o tipo de crédito consultado, e SHALL NOT ser armazenado como valor mutável.

#### Scenario: Tentativa de alterar lançamento

- **WHEN** uma atualização ou exclusão é tentada sobre um lançamento existente
- **THEN** a operação é recusada e o lançamento permanece exatamente como gravado

#### Scenario: Saldo derivado dos lançamentos

- **WHEN** uma organização possui lançamentos de `+100` e `-30` para o mesmo tipo de crédito
- **THEN** o saldo consultado para esse tipo é `70`

#### Scenario: Correção por lançamento compensatório

- **WHEN** um lançamento precisa ser desfeito
- **THEN** a correção só é possível inserindo um novo lançamento de sinal oposto, e o histórico preserva ambos

#### Scenario: Isolamento do ledger

- **WHEN** um usuário vinculado apenas à organização A consulta lançamentos sem filtro de organização
- **THEN** somente lançamentos da organização A são retornados

### Requirement: Direitos resolvidos a partir de planos versionados

Os direitos de cada plano SHALL ser lidos de arquivos de plano versionados junto ao código, e SHALL NOT ser armazenados como linhas editáveis no banco.

O sistema SHALL expor um único ponto de resolução de direito por organização. Nenhum outro ponto SHALL interpretar plano, quota ou saldo.

A resolução SHALL combinar o plano da assinatura corrente da organização com o saldo do ledger, e SHALL devolver, para cada direito, se ele está habilitado e qual é a quantidade disponível quando houver quota.

Um plano referenciado por uma assinatura que não possua arquivo correspondente SHALL produzir erro explícito na inicialização ou na resolução, nunca um conjunto de direitos silenciosamente vazio.

#### Scenario: Organização com plano

- **WHEN** os direitos de uma organização com assinatura ativa de plano `basic` são resolvidos
- **THEN** o resultado reflete exatamente o conteúdo do arquivo do plano `basic`, acrescido do saldo do ledger da organização

#### Scenario: Organização sem assinatura

- **WHEN** os direitos de uma organização sem assinatura são resolvidos
- **THEN** o resultado é um conjunto de direitos vazio, sem erro, indicando ausência de plano

#### Scenario: Assinatura fora do período ou não ativa

- **WHEN** os direitos de uma organização cuja assinatura está expirada ou em estado não ativo são resolvidos
- **THEN** o resultado é tratado como ausência de plano

#### Scenario: Plano desconhecido

- **WHEN** uma assinatura referencia um plano sem arquivo correspondente
- **THEN** o sistema falha de forma explícita e identificável, em vez de devolver direitos vazios

#### Scenario: Alteração de plano é mudança de código

- **WHEN** os direitos de um plano precisam mudar
- **THEN** a mudança ocorre no arquivo versionado do plano, e nenhuma escrita no banco altera direitos

### Requirement: Nenhum enforcement nesta capacidade ainda

A resolução de direito SHALL ser puramente consultiva nesta fatia. Nenhuma operação do sistema SHALL ser recusada, enfileirada ou degradada em função de plano, quota ou saldo.

O sistema SHALL NOT conceder créditos automaticamente na virada de período, SHALL NOT debitar créditos por uso e SHALL NOT reservar créditos por antecipação.

O sistema SHALL NOT integrar nenhum provedor de pagamento.

#### Scenario: Organização sem plano opera normalmente

- **WHEN** um usuário de uma organização sem assinatura executa qualquer operação disponível no sistema
- **THEN** a operação é executada sem recusa por falta de direito

#### Scenario: Virada de período não movimenta o ledger

- **WHEN** o fim do período corrente de uma assinatura é ultrapassado
- **THEN** nenhum lançamento é criado automaticamente e o saldo permanece inalterado
