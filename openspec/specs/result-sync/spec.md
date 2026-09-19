## Purpose

Levar a correção feita no aparelho até o servidor sem perdê-la e sem duplicá-la: o resultado apurado
vira fato durável local, espera a rede numa fila, sobe por rota autenticada, é gravado append-only com
revisão, e só então a cópia local pendente é apagada.

## Requirements

### Requirement: A nota apurada vira resultado durável antes de qualquer rede

Apurada a nota de uma folha, o aparelho SHALL gravar o resultado em armazenamento durável **antes** de
tentar qualquer envio, e SHALL concluir essa gravação sem rede.

O resultado gravado SHALL sobreviver ao fim do processo do aplicativo e ao desligamento do aparelho.

Folha recusada — de outra prova, com conjunto de itens divergente, ou que a apuração recusou por
qualquer motivo — SHALL NOT produzir resultado durável: recusa não é correção.

#### Scenario: A correção sobrevive ao fechamento do aplicativo

- **WHEN** uma folha é apurada e o aplicativo é encerrado antes de qualquer envio
- **THEN** o resultado continua gravado no aparelho na abertura seguinte

#### Scenario: A gravação não depende de rede

- **WHEN** uma folha é apurada com o aparelho sem rede
- **THEN** o resultado é gravado normalmente e fica pendente de envio

#### Scenario: Folha recusada não vira resultado

- **WHEN** a apuração recusa a folha
- **THEN** nenhum resultado durável é gravado

### Requirement: O resultado durável diz de qual folha, de qual pacote e de qual aluno ele é

O resultado SHALL identificar a prova, o pacote publicado e a variante contra os quais a nota foi
apurada, e o aluno pelo **token que o QR da folha carrega**.

O token SHALL vir do payload do QR lido, e SHALL NOT ser derivado do roster, da turma, do nome
apresentado ou de qualquer outro estado do aparelho. Folha cujo token vem vazio — a folha avulsa —
SHALL produzir resultado durável com token vazio, porque a nota é válida e só a atribuição falta.

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
- **THEN** o resultado é gravado com token vazio, e a nota é preservada

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

### Requirement: O resultado nasce pendente e espera a rede numa fila local

Todo resultado gravado SHALL nascer **pendente de sincronização** e entrar numa fila local.

Havendo rede, o aparelho SHALL enviar os pendentes. Não havendo, eles SHALL permanecer na fila, e a
ausência de rede SHALL NOT descartar, alterar nem marcar como enviado qualquer resultado.

O envio de um resultado SHALL NOT depender do sucesso do envio de outro: um resultado recusado pelo
servidor SHALL NOT bloquear a fila.

O aparelho SHALL distinguir a recusa que o servidor atribui **a si mesmo** — falha do servidor, da
qual a classe de status 5xx é o sinal — da recusa em que o servidor **decide sobre o pedido**, cuja
classe é 4xx. A primeira é transitória: o mesmo envio, repetido depois, pode ser aceito sem que nada
no aparelho mude. A segunda não é: repeti-la produz a mesma resposta.

Havendo pendente recusado de forma transitória, o aparelho SHALL agendar nova tentativa por conta
própria, sem depender de escaneamento novo nem de abertura de sessão. Havendo apenas recusa
definitiva, o aparelho SHALL NOT agendar nova tentativa imediata — o pendente continua na fila e
espera o próximo caminho que já existe.

A nova tentativa SHALL ser espaçada, e SHALL NOT repetir o envio em laço contínuo contra o servidor.

Nenhuma classificação de recusa SHALL apagar, alterar ou marcar como enviado um pendente: a distinção
decide **quando se tenta de novo**, e nunca o que acontece com a fila.

O resultado de uma passada pela fila SHALL ser legível de fora do aplicativo, distinguindo quantos
foram confirmados, quantos ficaram por ausência de rede, quantos por recusa transitória e quantos por
recusa definitiva. "Rodou e não drenou" SHALL NOT ser o único sinal disponível.

#### Scenario: Sem rede, a fila espera

- **WHEN** resultados são apurados com o aparelho offline
- **THEN** todos ficam pendentes na fila, e nenhum é perdido ou marcado como enviado

#### Scenario: A rede volta

- **WHEN** o aparelho recupera a rede com resultados pendentes na fila
- **THEN** os pendentes são enviados

#### Scenario: Um resultado recusado não trava os outros

- **WHEN** o servidor recusa um resultado da fila
- **THEN** os demais pendentes continuam sendo enviados

#### Scenario: Falha do servidor é tentada de novo

- **WHEN** o envio de um pendente é recusado por falha do servidor
- **THEN** o pendente continua na fila e o aparelho agenda nova tentativa por conta própria

#### Scenario: Recusa definitiva não vira laço

- **WHEN** o envio de um pendente é recusado por decisão do servidor sobre o pedido
- **THEN** o pendente continua na fila e o aparelho não agenda nova tentativa imediata

#### Scenario: A passada diz por que não drenou

- **WHEN** uma passada pela fila termina sem confirmar nenhum pendente
- **THEN** o desfecho registrado distingue ausência de rede, recusa transitória e recusa definitiva

### Requirement: O envio é autenticado e escopado pela organização

O envio SHALL usar a credencial da sessão do aparelho, e o servidor SHALL recusar envio sem
credencial válida.

O servidor SHALL recusar resultado de prova que não pertença a uma organização de que o usuário
autenticado é membro, e SHALL NOT gravar nada nesse caso.

#### Scenario: Envio sem credencial

- **WHEN** um resultado é enviado sem credencial válida
- **THEN** o servidor recusa e nada é gravado

#### Scenario: Prova de outra organização

- **WHEN** o resultado enviado é de prova de organização de que o usuário não é membro
- **THEN** o servidor recusa e nada é gravado

### Requirement: A gravação no servidor é append-only e idempotente por revisão

O servidor SHALL gravar o resultado como fato **append-only**: nenhum resultado já gravado SHALL ser
alterado nem apagado pela chegada de outro.

Reenvio do **mesmo** resultado SHALL ser seguro: o servidor SHALL reconhecê-lo e SHALL NOT criar
registro novo, respondendo como respondeu à primeira gravação. É o caso da confirmação que se perde no
caminho, e ele é rotineiro no modelo offline.

Recaptura da mesma folha — mesmo par prova e token, com correção diferente — SHALL criar **revisão
nova**, e a mais recente SHALL ser a corrente. As anteriores SHALL permanecer legíveis.

#### Scenario: Reenvio do mesmo resultado

- **WHEN** o mesmo resultado é enviado duas vezes
- **THEN** o servidor não cria registro novo e responde com sucesso nas duas

#### Scenario: Recaptura da mesma folha

- **WHEN** a mesma folha é escaneada de novo e produz correção diferente
- **THEN** o servidor grava uma revisão nova, ela passa a ser a corrente, e a anterior continua legível

#### Scenario: Nada é sobrescrito

- **WHEN** uma revisão nova é gravada
- **THEN** nenhuma revisão anterior é alterada nem apagada

### Requirement: O pendente local só sai depois da confirmação, e sair da sessão não o apaga

O aparelho SHALL apagar o resultado pendente da fila local **somente** após o servidor confirmar a
gravação. Falha de rede, tempo esgotado ou recusa do servidor SHALL NOT apagar o pendente.

Resultado cuja gravação foi confirmada SHALL ser apagado do armazenamento local, e SHALL NOT continuar
disponível no aparelho depois disso.

Sair da sessão SHALL NOT apagar resultado pendente. O apagamento do dado local da organização — roster,
pacote e visão guardada — SHALL continuar acontecendo ao sair, e o pendente SHALL ser preservado até que
o envio dele seja confirmado. Correção já feita não é cópia de referência puxada do servidor: apagá-la
antes do envio destrói trabalho que não existe em nenhum outro lugar.

O aparelho SHALL informar, ao sair, que há resultados ainda não enviados e quantos são.

O resultado pendente preservado SHALL ser escopado pela organização, e SHALL ser enviado na sessão
seguinte de qualquer membro dela — o resultado de leitura óptica é fato sobre a folha, e não sobre quem
a escaneou.

A revogação do vínculo com a organização SHALL apagar o **dado de referência** local daquela organização
— roster, pacote e visão guardada — e SHALL NOT apagar resultado pendente. A regra da classe H é a
mesma nos três caminhos de apagamento: pendente só sai depois de sincronização bem-sucedida, e nem
sair, nem a revogação, nem qualquer outro evento local SHALL antecipá-la.

Resultado pendente preservado após revogação SHALL continuar escopado pela organização, e SHALL ser
enviado quando um membro dela abrir sessão no aparelho. O usuário cujo vínculo foi revogado SHALL NOT
conseguir enviá-lo, porque o servidor já recusa resultado de organização de que o autenticado não é
membro — e essa recusa SHALL NOT apagar o pendente.

#### Scenario: Confirmação recebida

- **WHEN** o servidor confirma a gravação de um resultado
- **THEN** o resultado pendente é apagado do aparelho

#### Scenario: Envio falha

- **WHEN** o envio de um resultado falha por rede, tempo esgotado ou recusa do servidor
- **THEN** o resultado continua pendente no aparelho e nada é apagado

#### Scenario: Sair preserva o pendente

- **WHEN** o usuário sai da sessão com resultados ainda não enviados
- **THEN** os resultados pendentes continuam no aparelho, e roster, pacote e visão guardada são apagados
  como já eram

#### Scenario: Sair avisa do que não subiu

- **WHEN** o usuário pede para sair e há resultados ainda não enviados
- **THEN** o aparelho informa quantos são

#### Scenario: O pendente preservado sobe na sessão seguinte

- **WHEN** um membro da mesma organização abre a sessão com resultados pendentes preservados no aparelho
- **THEN** os pendentes são enviados

#### Scenario: Revogação apaga a referência e preserva o pendente

- **WHEN** o servidor informa que o usuário não pertence mais à organização
- **THEN** roster, pacote e visão guardada daquela organização são apagados, e os resultados pendentes
  dela permanecem no aparelho

#### Scenario: Recusa por vínculo revogado não apaga o pendente

- **WHEN** o envio de um pendente é recusado porque o usuário autenticado não é mais membro da
  organização
- **THEN** o resultado continua pendente no aparelho
