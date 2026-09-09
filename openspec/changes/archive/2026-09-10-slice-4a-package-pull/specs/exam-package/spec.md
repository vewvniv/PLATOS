## ADDED Requirements

### Requirement: As provas publicadas da organização são listáveis por quem pertence a ela

O sistema SHALL oferecer a consulta das provas publicadas de uma organização, devolvendo para cada
uma o identificador da prova, o título e o `content_hash` do pacote que a atende.

A consulta SHALL exigir autenticação e SHALL devolver apenas provas da organização a que o chamador
pertence. Prova de organização alheia SHALL ser indistinguível de prova inexistente.

O `content_hash` faz parte da listagem porque é ele que permite a quem consulta saber, **antes de
pedir o pacote**, se já tem o conteúdo. ADR-0009 fixa prova↔pacote em 1:1 e imutável: não existe
pacote desatualizado para uma prova, existe presente ou ausente.

Prova sem pacote publicado SHALL NOT aparecer na listagem. Uma prova que não pode ser escaneada não
é uma escolha oferecível.

#### Scenario: Provas da própria organização

- **WHEN** um usuário autenticado consulta as provas de uma organização a que pertence
- **THEN** a resposta traz as provas publicadas dessa organização, cada uma com identificador,
  título e o `content_hash` do seu pacote

#### Scenario: Organização a que o chamador não pertence

- **WHEN** um usuário autenticado consulta as provas de uma organização a que não pertence
- **THEN** a resposta é a mesma que para uma organização inexistente, e nenhuma prova é revelada

#### Scenario: Prova sem pacote publicado

- **WHEN** existe uma prova cuja publicação não gravou pacote
- **THEN** ela não aparece na listagem

#### Scenario: Consulta não autenticada

- **WHEN** a consulta chega sem credencial válida
- **THEN** ela é recusada, e nenhuma prova é revelada

### Requirement: O pacote publicado é entregue nos bytes exatos em que foi gravado

O sistema SHALL entregar o pacote de uma prova publicada como o **conteúdo exato** que a publicação
gravou, sem envelope, sem reserialização e sem renormalização. Os bytes que saem SHALL ser os bytes
sobre os quais o `content_hash` foi calculado.

A entrega SHALL declarar o `content_hash` fora do corpo, de modo que quem recebe possa conferir o
que recebeu contra o que foi publicado sem tocar no conteúdo.

A entrega SHALL exigir autenticação e SHALL devolver apenas pacote de prova da organização a que o
chamador pertence. Pacote de organização alheia SHALL ser indistinguível de pacote inexistente — a
distinção entre "não existe" e "existe e não é seu" revelaria a existência da prova.

ADR-0008 escolheu armazenar o pacote como texto canônico exatamente para que este caminho fosse
possível; submeter o conteúdo a mais uma etapa de codificação na entrega anularia aquela decisão.

#### Scenario: Bytes idênticos aos publicados

- **WHEN** o pacote de uma prova publicada é pedido por quem pertence à organização dela
- **THEN** o corpo da resposta é byte a byte igual ao conteúdo gravado na publicação, e o
  `content_hash` declarado fora do corpo é igual ao hash calculado sobre esse corpo

#### Scenario: O hash entregue é o hash do que foi entregue

- **WHEN** o conteúdo devolvido é hasheado por quem recebe
- **THEN** o resultado é igual ao `content_hash` que a entrega declarou, e igual ao gravado na
  publicação

#### Scenario: Pacote de outra organização

- **WHEN** um usuário autenticado pede o pacote de uma prova de organização a que não pertence
- **THEN** a resposta é a mesma que para uma prova inexistente, e nenhum byte do pacote é entregue

#### Scenario: Prova inexistente

- **WHEN** o pacote de uma prova que não existe é pedido
- **THEN** a resposta diz que não há pacote, e não é confundível com falha do servidor

#### Scenario: Entrega não autenticada

- **WHEN** o pedido chega sem credencial válida
- **THEN** ele é recusado, e nenhum byte do pacote é entregue
