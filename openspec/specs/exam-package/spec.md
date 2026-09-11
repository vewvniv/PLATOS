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

### Requirement: O roster obedece ao modo de identificação que a organização declara

Toda organização SHALL declarar em qual modo de identificação de aluno ela opera: **nominal**, em que o roster guarda nome civil e identificadores da instituição, ou **codificado**, em que o aluno é conhecido apenas por número de chamada, código ou apelido escolhido por quem cadastra.

O modo SHALL ser propriedade da organização, e não da prova nem da linha de roster: é a postura de tratamento de dados de uma organização inteira, e uma prova em modo diferente das outras tornaria a postura indeterminável.

O modo codificado SHALL ser o padrão de uma organização recém-criada. Operar em modo nominal SHALL ser uma escolha explícita, e não o que acontece por omissão.

Em modo codificado, o roster SHALL recusar identificador emitido pela instituição — matrícula ou referência externa —, porque esse dado só existe para ligar o aluno ao cadastro escolar e não tem uso pedagógico dentro da plataforma.

O sistema SHALL NOT afirmar que distingue um nome civil de um apelido. Essa distinção não é decidível pelo conteúdo do campo, e o modo declarado é o que registra a intenção de quem cadastrou.

Mudar o modo de uma organização SHALL NOT alterar nenhum pacote publicado nem o hash de qualquer artefato imutável.

#### Scenario: Organização recém-criada nasce em modo codificado

- **WHEN** uma organização é criada
- **THEN** ela opera em modo codificado até que alguém a mude explicitamente

#### Scenario: Matrícula recusada em modo codificado

- **WHEN** uma linha de roster com matrícula ou referência externa é inserida numa organização em modo codificado
- **THEN** a operação é recusada, e a mensagem identifica o modo declarado

#### Scenario: Matrícula aceita em modo nominal

- **WHEN** a mesma linha é inserida numa organização em modo nominal
- **THEN** a operação é aceita

#### Scenario: O modo declarado é recuperável

- **WHEN** o roster de uma prova é consultado
- **THEN** o modo de identificação em que a organização opera é recuperável junto dos dados do roster

#### Scenario: Trocar de modo não toca no artefato publicado

- **WHEN** uma organização com prova já publicada muda de modo de identificação
- **THEN** o hash do pacote continua o mesmo e o pacote segue verificável

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

Um pacote SHALL ser recusado, com erro identificável, quando não for internamente coerente. São incoerências: posição de variante que aponta item inexistente, item sem gabarito, atribuição que aponta variante inexistente, layout que declara questões diferentes das que os itens declaram, **e atribuição sem a folha dela** — isto é, sem o payload do QR que a identifica. A incoerência inversa, layout órfão de atribuição, não entra nesta lista porque ela é irrepresentável pela forma do pacote: ver "Publicar com roster produz uma atribuição e uma folha por aluno".

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

#### Scenario: Atribuição sem folha

- **WHEN** o pacote declara uma atribuição sem o payload do QR que a identifica
- **THEN** a publicação falha com erro que identifica a atribuição e nada é gravado

#### Scenario: Token repetido entre atribuições

- **WHEN** o pacote declara duas atribuições com o mesmo token
- **THEN** a publicação falha com erro que identifica o token e nada é gravado

### Requirement: Publicar com roster produz uma atribuição e uma folha por aluno

Publicar uma prova cujo roster tem alunos SHALL produzir exatamente **uma atribuição por aluno do
roster**, e cada atribuição SHALL ter no pacote um layout **endereçável por ela**.

O pacote SHALL levar de cada atribuição apenas o **token**. Publicar SHALL gravar o roster em
estrutura mutável própria, associada à mesma prova e fora do hash do pacote.

Publicar uma prova **sem** roster SHALL continuar produzindo pacote válido, sem atribuição alguma, e
a folha derivada dele SHALL trazer o campo de aluno **vazio**. Este caso não é erro: é a folha avulsa
do aluno fora da lista, e a atribuição dela é feita depois da captura.

Duas atribuições da mesma prova SHALL NOT compartilhar token, e um token SHALL identificar no máximo
uma atribuição dentro de uma prova.

**O que endereça a folha de uma atribuição é a própria atribuição.** O que distingue a folha de um
aluno — o payload do QR dela e a matriz que dele decorre — SHALL viajar **dentro** da atribuição, e
SHALL NOT viver em uma entrada endereçada por token ao lado dela. A geometria SHALL continuar sendo
uma por variante.

Disso decorre uma garantia, e ela é da **forma** e não de uma conferência: **folha sem atribuição é
irrepresentável**. Não há onde colocar uma — não existe mapa endereçado por token onde uma entrada
órfã pudesse sobrar —, então o pacote não precisa recusar esse estado, porque ele não pode ser
construído. A recusa que resta é a inversa, que **é** representável: atribuição sem a folha dela.

#### Scenario: Roster com alunos produz uma folha para cada um

- **WHEN** uma prova é publicada com um roster de N alunos
- **THEN** o pacote traz N atribuições, cada uma com um layout endereçável por ela, e nenhum nome,
  turma ou matrícula aparece no pacote

#### Scenario: Publicar sem roster continua válido

- **WHEN** uma prova é publicada sem nenhum aluno no roster
- **THEN** o pacote é válido, não traz atribuição, e a folha derivada dele traz o campo de aluno vazio

#### Scenario: Token não se repete dentro da prova

- **WHEN** um roster é submetido com dois alunos que resolveriam para o mesmo token
- **THEN** a publicação falha com erro que identifica o token repetido e nada é gravado

### Requirement: A folha impressa é derivada do pacote

O documento entregue à impressão SHALL ser produzido a partir do layout que o pacote publica, e SHALL NOT ser produzido a partir de uma fonte de geometria paralela.

**Quando o pacote traz atribuições, a impressão SHALL produzir um documento por atribuição**, e cada documento SHALL usar o layout endereçado por aquela atribuição. Nenhum documento SHALL ser produzido a partir de um layout que o pacote não enderece.

A equivalência entre plataformas e a fidelidade dimensional SHALL continuar valendo, agora medidas sobre o documento derivado do pacote.

#### Scenario: PDF vem do pacote

- **WHEN** o documento é gerado para uma prova publicada
- **THEN** a geometria desenhada é a que o pacote declara

#### Scenario: Um documento por aluno

- **WHEN** a impressão é pedida para uma prova publicada com N atribuições
- **THEN** são produzidos N documentos, cada um com o layout da sua atribuição

#### Scenario: Divergência entre plataformas continua barrada

- **WHEN** os documentos das duas plataformas, derivados do mesmo pacote, são comparados
- **THEN** eles coincidem dentro da mesma tolerância exigida hoje
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
