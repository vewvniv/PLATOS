## Purpose

Publicar uma prova como artefato imutável e verificável: o `ExamPackage` que a captura, a sincronização e a correção vão consumir depois. Cobre a montagem, a validação, o hash, a imutabilidade no armazenamento e a separação entre o que é imutável e o que precisa poder mudar.

## Requirements

### Requirement: O pacote publicado é imutável e identificado por hash

Publicar uma prova SHALL produzir um `ExamPackage` acompanhado de um hash do próprio conteúdo.

Publicar duas vezes a mesma prova SHALL produzir exatamente o mesmo hash. O hash SHALL ser calculado sobre uma serialização canônica, de modo que ordem de campo, espaçamento ou ordem de iteração não o alterem.

Um pacote já publicado SHALL NOT poder ser alterado nem removido. A recusa SHALL ser imposta pelo armazenamento, e não por disciplina de quem escreve — imutabilidade que depende de o chamador se comportar não é imutabilidade.

Corrigir uma prova publicada SHALL ser publicar um pacote novo, com hash próprio.

#### Scenario: Republicar a mesma prova dá o mesmo hash

- **WHEN** a mesma definição de prova é publicada duas vezes
- **THEN** os dois pacotes têm o mesmo hash

#### Scenario: Mudança no conteúdo muda o hash

- **WHEN** qualquer parte do conteúdo da prova muda e ela é publicada de novo
- **THEN** o hash resultante é diferente do anterior

#### Scenario: Pacote publicado não pode ser alterado

- **WHEN** uma alteração ou remoção é tentada sobre um pacote já publicado
- **THEN** a operação é recusada pelo armazenamento e o pacote permanece como estava

### Requirement: Dado pessoal de aluno fica fora do pacote imutável

O pacote SHALL identificar o aluno apenas por um token opaco por atribuição. Nome, turma, matrícula e qualquer outro dado pessoal direto SHALL NOT estar no pacote.

Esses dados SHALL viver em um roster próprio, mutável, associado à mesma prova, e SHALL NOT participar do hash do pacote.

Apagar o roster SHALL deixar o pacote íntegro e verificável pelo seu hash.

#### Scenario: O pacote não carrega nome de aluno

- **WHEN** uma prova com alunos atribuídos é publicada
- **THEN** o pacote traz apenas os tokens, e nenhum nome, turma ou matrícula aparece nele

#### Scenario: Mudar o roster não invalida o pacote

- **WHEN** o nome de um aluno é corrigido no roster de uma prova já publicada
- **THEN** o hash do pacote continua o mesmo e o pacote segue verificável

#### Scenario: Eliminação de dado pessoal não destrói a prova

- **WHEN** o roster de uma prova publicada é apagado
- **THEN** o pacote continua existindo, íntegro e sem dado pessoal direto

### Requirement: O pacote declara o perfil que produziu sua geometria

O layout dentro do pacote SHALL declarar o perfil tipográfico e de folha sob o qual foi produzido, junto das versões de engine e de renderizador que ele já declara.

Dois pacotes produzidos com perfis diferentes SHALL ser distinguíveis por essa declaração, sem que seja preciso inferir a partir da geometria.

#### Scenario: Perfil declarado no pacote

- **WHEN** uma prova é publicada
- **THEN** o layout do pacote declara o identificador do perfil e os valores que o definem

#### Scenario: Perfis diferentes são distinguíveis

- **WHEN** a mesma prova é publicada sob dois perfis diferentes
- **THEN** os dois pacotes declaram perfis diferentes e têm hashes diferentes

### Requirement: Pacote incoerente é recusado antes de ser publicado

Um pacote SHALL ser recusado, com erro identificável, quando não for internamente coerente. São incoerências: posição de variante que aponta item inexistente, item sem gabarito, atribuição que aponta variante inexistente, e layout que declara questões diferentes das que os itens declaram.

Nenhum pacote parcial SHALL ser gravado.

#### Scenario: Variante aponta item inexistente

- **WHEN** uma variante mapeia uma posição física para um item que o pacote não declara
- **THEN** a publicação falha com erro que identifica a posição e nada é gravado

#### Scenario: Item sem gabarito

- **WHEN** um item objetivo do pacote não tem alternativa correta declarada
- **THEN** a publicação falha com erro que identifica o item e nada é gravado

#### Scenario: Layout diverge dos itens

- **WHEN** o layout do pacote declara um conjunto de questões diferente do que os itens declaram
- **THEN** a publicação falha com erro que aponta a divergência e nada é gravado

### Requirement: A folha impressa é derivada do pacote

O documento entregue à impressão SHALL ser produzido a partir do layout que o pacote publica, e SHALL NOT ser produzido a partir de uma fonte de geometria paralela.

A equivalência entre plataformas e a fidelidade dimensional SHALL continuar valendo, agora medidas sobre o documento derivado do pacote.

#### Scenario: PDF vem do pacote

- **WHEN** o documento é gerado para uma prova publicada
- **THEN** a geometria desenhada é a que o pacote declara

#### Scenario: Divergência entre plataformas continua barrada

- **WHEN** os documentos das duas plataformas, derivados do mesmo pacote, são comparados
- **THEN** eles coincidem dentro da mesma tolerância exigida hoje
