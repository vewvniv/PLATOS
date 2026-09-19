## MODIFIED Requirements

### Requirement: O resultado durável diz de qual folha, de qual pacote e de qual aluno ele é

O resultado SHALL identificar a prova, o pacote publicado e a variante contra os quais a nota foi
apurada, e o aluno pelo **token que o QR da folha carrega**.

O token SHALL vir do payload do QR lido, e SHALL NOT ser derivado do roster, da turma, do nome
apresentado ou de qualquer outro estado do aparelho. Folha cujo token vem vazio — a folha avulsa —
SHALL produzir resultado durável com token **ausente**, porque a nota é válida e só a atribuição
falta.

**Ausente, e não vazio, e a diferença é a decisão.** Token vazio faria todas as folhas avulsas da
mesma prova colidirem entre si na identidade que decide o que é revisão de quê — elas passariam a
ser lidas como recapturas umas das outras. Ausência é o que distingue "esta folha não nomeia
aluno" de "esta folha nomeia um aluno cujo token é a string vazia", e só o primeiro existe.

O resultado SHALL levar o total apurado, a pontuação máxima, se a nota está fechada, a lista de
pendências e quanto ainda está em disputa, e SHALL levar o **resultado de cada questão** como
evidência da correção.

O resultado SHALL NOT levar nome, turma ou matrícula do aluno.

**A proveniência declarada SHALL ser conferida pelo servidor antes de gravar, e não apenas
declarada.** O servidor SHALL recusar resultado cujo pacote declarado não seja o pacote publicado
daquela prova, e resultado cuja variante declarada o pacote publicado não declare. Em qualquer dos
dois casos o servidor SHALL NOT gravar nada — nem o resultado, nem a evidência por questão.

O oráculo da conferência SHALL ser o **pacote publicado da própria prova**, e SHALL NOT ser uma
segunda lista de variantes nem um segundo registro de qual é o pacote daquela prova. Identificar sem
conferir é declarar, e o fato gravado é append-only: proveniência errada não tem conserto, só revisão
nova, que não apaga a anterior. A rastreabilidade existe para sustentar contestação de nota, e
proveniência não verificada não a sustenta.

**A recusa por proveniência incoerente SHALL distinguir-se da recusa por ausência.** Prova
inexistente, prova sem pacote publicado e prova de organização a que o autenticado não pertence
continuam sendo **ausência**, e continuam indistinguíveis entre si. Proveniência que não bate é
**decisão do servidor sobre o pedido apresentado**, e SHALL ser sinalizada como tal — a classe que o
aparelho lê como recusa **definitiva**, e não como falha transitória do servidor. Sinalizá-la como
falha do servidor faria o aparelho repetir para sempre um envio que nunca será aceito; sinalizá-la
como ausência faria o aparelho não distinguir "esta prova não existe para você" de "este corpo não
fecha".

A recusa SHALL dizer **qual** das duas coisas não fecha — o pacote ou a variante.

A conferência de proveniência SHALL NOT recalcular a nota a partir do gabarito: correção objetiva
local é definitiva quando não há discursivas, e o que se confere aqui é **proveniência**, não
aritmética.

#### Scenario: O resultado identifica o pacote e o aluno

- **WHEN** um resultado é gravado
- **THEN** ele identifica prova, pacote, variante e o token lido do QR

#### Scenario: Folha avulsa

- **WHEN** a folha lida traz token de aluno vazio
- **THEN** o resultado é gravado com token **ausente**, e a nota é preservada

#### Scenario: Nota parcial chega como parcial

- **WHEN** um resultado com questões pendentes é gravado
- **THEN** ele registra que a nota não está fechada, quais questões pendem e quanto está em disputa

#### Scenario: Nenhum dado pessoal direto no resultado

- **WHEN** um resultado é gravado ou enviado
- **THEN** ele não contém nome, turma nem matrícula do aluno

#### Scenario: Pacote declarado que não é o da prova

- **WHEN** um resultado é enviado declarando um pacote bem formado que não é o pacote publicado
  daquela prova
- **THEN** o servidor recusa por decisão sobre o pedido, a recusa nomeia o pacote como o que não
  fecha, e nem o resultado nem a evidência por questão são gravados

#### Scenario: Variante que o pacote não declara

- **WHEN** um resultado é enviado declarando o pacote correto e uma variante que o pacote publicado
  não declara
- **THEN** o servidor recusa por decisão sobre o pedido, a recusa nomeia a variante como o que não
  fecha, e nem o resultado nem a evidência por questão são gravados

#### Scenario: Duas folhas avulsas da mesma prova não são a mesma folha

- **WHEN** duas folhas avulsas diferentes da mesma prova são apuradas e gravadas
- **THEN** as duas produzem resultados distintos, e nenhuma é lida como recaptura da outra
