## ADDED Requirements

### Requirement: O roster de uma prova publicada é entregue a quem pertence à organização

O sistema SHALL oferecer a obtenção do roster de uma prova publicada, devolvendo para cada aluno
atribuído o **token** e o **nome de apresentação**.

A obtenção SHALL exigir autenticação e SHALL devolver apenas roster de prova da organização a que o
chamador pertence. Roster de organização alheia SHALL ser **indistinguível de inexistente** — a
distinção entre "não existe" e "existe e não é seu" revelaria a existência da prova, e é a mesma
regra que a listagem e a entrega do pacote já seguem.

A entrega SHALL levar **apenas** o que identifica o aluno para quem corrige. Turma, matrícula e
referência externa SHALL NOT ser entregues: quem recebe o roster não tem uso para elas, e dado
pessoal que não sai do servidor não precisa ser apagado de onde nunca chegou.

A entrega SHALL NOT declarar hash do roster, e SHALL NOT participar do `content_hash` do pacote. O
roster é **mutável por construção** (ADR-0002): um hash sobre ele ou o travaria — e a eliminação de
dado pessoal morreria com isso — ou seria recalculado a cada mudança, sem garantir nada.

Prova publicada **sem roster** SHALL ser entregue como roster **vazio**, e SHALL NOT ser tratada como
prova inexistente. "Esta prova não tem roster" é afirmação sobre o mundo, e é distinta de "esta prova
não existe" — a folha avulsa do aluno fora da lista depende dessa distinção.

O nome entregue SHALL ser o que o roster guarda, qualquer que seja o modo de identificação da
organização. O modo decide **o que pode ser gravado** no roster (ADR-0012), e não o que a entrega
filtra: filtrar aqui faria a entrega afirmar sobre o modo algo que o armazenamento já garante, em
dois lugares que podem divergir.

#### Scenario: Roster da própria organização

- **WHEN** um usuário autenticado obtém o roster de uma prova publicada de uma organização a que
  pertence
- **THEN** a resposta traz, para cada aluno atribuído, o token e o nome de apresentação, e **não**
  traz turma, matrícula nem referência externa

#### Scenario: Prova publicada sem roster

- **WHEN** o roster de uma prova publicada que não tem alunos atribuídos é obtido
- **THEN** a resposta é um roster vazio, e é distinguível da resposta para uma prova inexistente

#### Scenario: Roster de organização alheia

- **WHEN** um usuário autenticado obtém o roster de uma prova de organização a que não pertence
- **THEN** a resposta é indistinguível da resposta para uma prova inexistente

#### Scenario: Obtenção sem autenticação

- **WHEN** o roster é pedido sem credencial
- **THEN** a obtenção é recusada por falta de autenticação, e nenhum dado de aluno é devolvido

#### Scenario: A entrega não declara hash

- **WHEN** o roster de uma prova publicada é entregue
- **THEN** nenhum hash do roster é declarado, e o `content_hash` do pacote daquela prova permanece o
  mesmo de antes da entrega

#### Scenario: Corrigir o nome muda o que a entrega devolve

- **WHEN** o nome de um aluno é corrigido no roster e o roster é obtido de novo
- **THEN** a entrega devolve o nome corrigido, e o `content_hash` do pacote continua o mesmo
