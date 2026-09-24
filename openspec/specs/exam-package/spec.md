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

Um pacote SHALL ser recusado, com erro identificável, quando não for internamente coerente. São incoerências:
- posição de variante que aponta item inexistente;
- item objetivo sem gabarito;
- item discursivo com gabarito, ou sem rubrica;
- item objetivo com rubrica;
- rubrica cujos pontos não somam a pontuação do item;
- atribuição que aponta variante inexistente;
- layout que declara questões diferentes das que os itens declaram;
- item discursivo que não tem **exatamente uma** região discursiva no layout de cada variante;
- `fully_offline_gradable` que diz o contrário do que os itens declaram;
- **atribuição sem a folha dela** — isto é, sem o payload do QR de **cada** região do layout da variante dela;
- QR de atribuição cujo payload não traz o token daquela atribuição, ou não traz o índice da região a que ele está associado.

A incoerência inversa, layout órfão de atribuição, não entra nesta lista porque ela é irrepresentável pela forma do pacote: ver "Publicar com roster produz uma atribuição e uma folha por aluno".

Nenhum pacote parcial SHALL ser gravado.

#### Scenario: Variante aponta item inexistente

- **WHEN** uma variante mapeia uma posição física para um item que o pacote não declara
- **THEN** a publicação falha com erro que identifica a posição e nada é gravado

#### Scenario: Item sem gabarito

- **WHEN** um item objetivo do pacote não tem alternativa correta declarada
- **THEN** a publicação falha com erro que identifica o item e nada é gravado

#### Scenario: Item discursivo com gabarito

- **WHEN** um item discursivo do pacote tem entrada no gabarito
- **THEN** a publicação falha com erro que identifica o item e nada é gravado

#### Scenario: Item discursivo sem região

- **WHEN** o layout de uma variante não tem região discursiva para um item discursivo, e o pacote é coerente em todo o resto
- **THEN** a publicação falha com erro que identifica o item e a variante, e nada é gravado

#### Scenario: Layout diverge dos itens

- **WHEN** o layout do pacote declara um conjunto de questões diferente do que os itens declaram
- **THEN** a publicação falha com erro que aponta a divergência e nada é gravado

#### Scenario: Correção offline declarada contra os itens

- **WHEN** o pacote declara `fully_offline_gradable` verdadeiro e tem item discursivo
- **THEN** a publicação falha com erro que identifica a contradição e nada é gravado

#### Scenario: Atribuição sem folha

- **WHEN** o pacote declara uma atribuição sem o payload do QR que a identifica
- **THEN** a publicação falha com erro que identifica a atribuição e nada é gravado

#### Scenario: Atribuição sem o QR de uma região

- **WHEN** uma atribuição traz QR para a região de gabarito e não para uma região discursiva da variante dela
- **THEN** a publicação falha com erro que identifica a atribuição e a região, e nada é gravado

#### Scenario: QR de atribuição associado à região errada

- **WHEN** o QR que uma atribuição associa à região `1` traz no payload o índice `2`
- **THEN** a publicação falha com erro que identifica a atribuição e as duas regiões, e nada é gravado

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
aluno SHALL viajar **dentro** da atribuição: **um QR por região** do layout da variante dela, cada um
com o payload e a matriz que dele decorre, identificado pelo índice da região. Isso SHALL NOT viver em
uma entrada endereçada por token ao lado da atribuição. A geometria SHALL continuar sendo uma por
variante.

Disso decorre uma garantia, e ela é da **forma** e não de uma conferência: **folha sem atribuição é
irrepresentável**. Não há onde colocar uma — não existe mapa endereçado por token onde uma entrada
órfã pudesse sobrar —, então o pacote não precisa recusar esse estado, porque ele não pode ser
construído. A recusa que resta é a inversa, que **é** representável: atribuição sem a folha dela, ou
sem o QR de uma das regiões dela.

#### Scenario: Roster com alunos produz uma folha para cada um

- **WHEN** uma prova é publicada com um roster de N alunos
- **THEN** o pacote traz N atribuições, cada uma com um layout endereçável por ela, e nenhum nome,
  turma ou matrícula aparece no pacote

#### Scenario: A atribuição traz um QR por região

- **WHEN** uma prova com uma questão objetiva e duas discursivas é publicada com roster
- **THEN** cada atribuição traz três QRs, um para cada região `0`, `1` e `2`, todos com o token dela

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

### Requirement: O pacote declara a proveniência do que o produziu

O pacote publicado SHALL declarar, nos seus metadados, a **tripla de proveniência** que a invariante
I3 exige: a versão do prompt, o identificador do modelo e o hash dos parâmetros da chamada que
produziu o artefato.

Os três SHALL existir no contrato desde já, e SHALL ser **ausentes** enquanto a prova for fixa —
prova fixa não tem artefato de IA, e não há proveniência a declarar. Quando a geração por IA chegar,
ela preenche os três **sem mexer no contrato**, que é o que I3 existe para garantir.

**Ausente e vazio SHALL significar coisas diferentes.** Um dos três com valor vazio SHALL ser lido
como "houve geração e o valor é vazio", e não como "não houve geração" — é a mesma distinção que a
folha avulsa já fixou para o token de aluno, e pela mesma razão: dois estados diferentes colapsados
num só valor produzem leitura plausível e errada, sem sintoma.

O hash dos parâmetros SHALL cobrir os parâmetros da chamada que produziu o artefato, de modo que
dois artefatos produzidos com parâmetros diferentes sejam distinguíveis por ele sem que seja preciso
inferir a partir do conteúdo.

Acrescentar qualquer um dos três ao contrato SHALL mudar o `content_hash` de todo pacote, porque a
serialização canônica inclui campos com valor padrão. Isso não é defeito: é a razão de os três
nascerem no contrato antes de existir geração. Pacotes publicados sob um contrato anterior SHALL
manter os bytes que têm — o armazenamento é imutável — e SHALL ser **recusados** por um consumidor
que interprete o contrato novo, por fidelidade de interpretação e não por integridade. Corrigir uma
prova nessa condição SHALL ser publicar prova nova, com identificador próprio.

#### Scenario: A tripla é declarada numa prova fixa

- **WHEN** uma prova sem geração por IA é publicada
- **THEN** os metadados do pacote trazem os três campos de proveniência, e os três estão ausentes

#### Scenario: Ausente não é vazio

- **WHEN** um pacote declara um dos três campos com valor vazio
- **THEN** ele é distinguível de um pacote que declara o mesmo campo como ausente

#### Scenario: Parâmetros diferentes são distinguíveis

- **WHEN** dois artefatos são produzidos com parâmetros de chamada diferentes
- **THEN** os dois declaram hashes de parâmetros diferentes, e os pacotes têm `content_hash`
  diferentes

#### Scenario: Pacote do contrato anterior é recusado pelo consumidor atualizado

- **WHEN** um consumidor que interpreta o contrato atual recebe um pacote publicado sob o contrato
  anterior, acompanhado do `content_hash` correto daquele pacote
- **THEN** o pacote é recusado **por fidelidade de interpretação**, e NÃO por integridade — o hash
  declarado confere com os bytes recebidos, e o que falha é a reserialização

### Requirement: O identificador de prova dentro do pacote é o mesmo que a folha carrega

O identificador de prova gravado nos metadados do pacote SHALL ser o mesmo valor que identifica a
prova na definição publicada, **e** o mesmo que viaja no código impresso de cada atribuição.

A igualdade SHALL ser afirmada por verificação, e não sustentada apenas por construção. Ela é hoje
consequência de um único ponto de escrita alimentar os três, e nada a prende: o dia em que os três
divergirem, a folha de uma prova passa a ser aceita contra o pacote de outra, e a camada que julga
**de quem é a folha** deixa de julgar coisa nenhuma.

#### Scenario: Os três identificadores coincidem no pacote publicado

- **WHEN** uma prova é publicada com roster
- **THEN** o identificador nos metadados do pacote, o da definição publicada e o que viaja no código
  impresso de cada atribuição são o mesmo valor

### Requirement: O item discursivo publica a sua rubrica

Todo item do pacote SHALL declarar se é objetivo ou discursivo.

O item discursivo SHALL levar a sua rubrica analítica: os critérios, cada um com descritores, pontos e
`expected_lines`. SHALL levar também o modo de captura da resposta, que por padrão é em cinza e que a
questão pode declarar em cor (§8). O item objetivo SHALL NOT levar rubrica nem modo de captura.

A pontuação máxima da prova SHALL incluir os pontos dos itens discursivos. O gabarito SHALL conter
apenas itens objetivos. `fully_offline_gradable` SHALL ser falso exatamente quando a prova tem item
discursivo.

O conjunto de regiões que a folha de uma variante deve ter SHALL ser lido do layout daquela variante,
e SHALL NOT ser declarado uma segunda vez em outro lugar do pacote.

#### Scenario: A rubrica chega ao pacote

- **WHEN** uma prova com uma discursiva é publicada
- **THEN** o item discursivo do pacote traz os critérios da rubrica, com descritores, pontos e `expected_lines` iguais aos da definição, e o modo de captura

#### Scenario: Prova com discursiva não é corrigível só no aparelho

- **WHEN** uma prova com ao menos uma discursiva é publicada
- **THEN** o pacote declara `fully_offline_gradable` falso, a pontuação máxima inclui os pontos da discursiva, e o gabarito não traz entrada para ela

#### Scenario: Prova só objetiva continua corrigível no aparelho

- **WHEN** uma prova só com objetivas é publicada
- **THEN** o pacote declara `fully_offline_gradable` verdadeiro, e nenhum item traz rubrica
