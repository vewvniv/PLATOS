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
SHALL produzir resultado durável com token **ausente**, porque a nota é válida e só a atribuição
falta.

**Ausente, e não vazio, e a diferença é a decisão.** Token vazio faria todas as folhas avulsas da
mesma prova colidirem entre si na identidade que decide o que é revisão de quê — elas passariam a
ser lidas como recapturas umas das outras. Ausência é o que distingue "esta folha não nomeia
aluno" de "esta folha nomeia um aluno cujo token é a string vazia", e só o primeiro existe.

O resultado SHALL levar o total apurado, a pontuação máxima, se a nota está fechada, a lista de
pendências e quanto ainda está em disputa, e SHALL levar o **resultado de cada questão** como
evidência da correção.

Quando o resultado vem da apuração parcial de uma prova com discursiva, cada questão discursiva
**aguardando correção** SHALL contar como pendência do resultado enviado, com um motivo próprio que
a distingue de múltipla marcação e de indecisa, e a pontuação dela SHALL somar ao quanto está em
disputa. A nota SHALL continuar não-fechada enquanto houver ao menos uma discursiva aguardando,
mesmo que nenhuma questão objetiva esteja pendente — quem decide "fechada" é a lista de pendências
estar vazia, e uma discursiva aguardando a mantém não-vazia.

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

#### Scenario: Discursiva aguardando correção conta como pendência

- **WHEN** um resultado de prova com discursiva completa é gravado, com toda questão objetiva sem
  pendência e uma ou mais discursivas aguardando correção
- **THEN** o resultado lista cada discursiva aguardando como pendência, soma seus pontos ao quanto
  está em disputa, e a nota não é apresentada como fechada

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

### Requirement: O servidor grava a nota do professor como revisão nova da mesma folha

O servidor SHALL aceitar o **resultado completo de uma prova com discursiva** (`scoring`: "A nota do professor completa a parcial sem recalcular a parte objetiva") e gravá-lo como **revisão nova** do mesmo par prova e token da parcial, com **origem `teacher`** e **caminho `image`** (o professor viu a imagem da resposta). A parcial anterior SHALL permanecer legível, e a revisão nova SHALL ser a corrente, como em "A gravação no servidor é append-only e idempotente por revisão".

O resultado do professor SHALL ser **autocontido**: leva a parte objetiva (a pontuação, a evidência e as pendências das objetivas) e a pontuação de cada discursiva da variante, de modo que o servidor o aceita **sem ter recebido antes** a parcial da captura. Ele SHALL identificar a **captura da parcial que completa**, além de ter a sua própria chave de idempotência.

O servidor SHALL conferir o resultado com as mesmas regras de coerência do aparelho (`scoring`), e SHALL conferir contra o **pacote publicado da própria prova** — o único oráculo — a proveniência (pacote e variante, como em "O resultado durável diz de qual folha, de qual pacote e de qual aluno ele é") **e** as discursivas: o conjunto de questões discursivas pontuadas SHALL ser exatamente o das discursivas da variante, e a pontuação de cada uma SHALL estar em 0..valor que o pacote declara, com a regra decimal de `scoring` (até 2 casas, exata): a pontuação pode ser fracionária, como 1.5 ou 1.75. A conferência SHALL NOT recalcular a parte objetiva a partir do gabarito.

O servidor SHALL recusar, como **decisão sobre o pedido** (a classe que o aparelho lê como definitiva) e dizendo **o que não fecha**, o resultado do professor cujo pacote ou variante não confere, cuja discursiva falta, sobra, está fora da faixa, tem mais de 2 casas decimais ou não é numérica, cuja pontuação não soma o total, ou cuja origem ou caminho não sejam os aceitos. **Os únicos aceitos neste requisito são `teacher` e `image`**: origem `ai` e caminho `text` pertencem a fatia posterior e SHALL ser recusados. Em qualquer recusa o servidor SHALL NOT gravar nada, nem o resultado nem a evidência por questão.

O resultado gravado SHALL preservar **exatamente** cada pontuação e o total, sem arredondar nem perder precisão entre o que o aparelho enviou, o que o servidor recebeu e o que o banco guarda; e a revisão corrente SHALL devolver o mesmo valor que foi enviado. O resultado objetivo e a parcial SHALL continuar aceitos e gravados exatamente como antes, com pontos inteiros. O resultado gravado SHALL registrar a origem e o caminho, e a evidência de cada discursiva SHALL ser distinguível da de uma objetiva. A nota SHALL ser declarada fechada somente se não restar pendência objetiva. O envio continua autenticado e escopado pela organização, e a ausência continua sendo ausência, nos mesmos termos de "O envio é autenticado e escopado pela organização".

#### Scenario: A nota do professor é uma revisão nova

- **WHEN** o servidor tem a parcial de uma folha como revisão corrente e recebe o resultado do professor para a mesma prova e o mesmo token
- **THEN** ele grava uma revisão nova com origem `teacher` e caminho `image`, ela passa a ser a corrente, e a parcial continua legível e inalterada

#### Scenario: A nota do professor chega sem a parcial

- **WHEN** o servidor recebe o resultado do professor de uma folha cuja parcial nunca recebeu
- **THEN** ele o grava, porque o resultado é autocontido

#### Scenario: Reenvio do mesmo resultado do professor

- **WHEN** o mesmo resultado do professor é enviado duas vezes
- **THEN** o servidor não cria registro novo e responde com sucesso nas duas, com a mesma revisão

#### Scenario: Nova nota do professor para a mesma folha

- **WHEN** o professor muda a pontuação e o novo resultado, com chave própria, chega para a mesma folha
- **THEN** o servidor grava revisão nova, ela é a corrente, e a anterior continua legível

#### Scenario: Discursiva que o pacote não declara

- **WHEN** o resultado do professor pontua um item que o pacote publicado não declara como discursiva da variante
- **THEN** o servidor recusa por decisão sobre o pedido, nomeia o item, e nada é gravado

#### Scenario: Discursiva faltando

- **WHEN** o resultado do professor não traz pontuação para uma discursiva da variante
- **THEN** o servidor recusa por decisão sobre o pedido, nomeia a discursiva que falta, e nada é gravado

#### Scenario: Nota fracionária é gravada e lida sem perda

- **WHEN** o servidor recebe um resultado do professor com pontuações 1.5 e 1.75 e total correspondente
- **THEN** ele o grava, e a revisão corrente devolve exatamente 1.5, 1.75 e o total, sem arredondar

#### Scenario: Mais de 2 casas decimais

- **WHEN** o resultado do professor traz uma pontuação com 3 casas decimais
- **THEN** o servidor recusa por decisão sobre o pedido, nomeia a questão e o limite de 2 casas, e nada é gravado

#### Scenario: Pontuação não numérica

- **WHEN** o resultado do professor traz uma pontuação que não é número
- **THEN** o servidor recusa por decisão sobre o pedido, nomeia a questão, e nada é gravado

#### Scenario: Resultado objetivo e parcial não mudam

- **WHEN** o servidor recebe um resultado objetivo ou uma parcial, com pontos inteiros
- **THEN** ele os aceita e grava exatamente como antes desta mudança

#### Scenario: Pontuação acima do valor do pacote

- **WHEN** o resultado do professor traz, para uma discursiva, pontuação maior que a do pacote publicado
- **THEN** o servidor recusa por decisão sobre o pedido, nomeia a questão e a faixa, e nada é gravado

#### Scenario: Pacote que não é o da prova

- **WHEN** o resultado do professor declara um pacote bem formado que não é o publicado daquela prova
- **THEN** o servidor recusa por decisão sobre o pedido, nomeia o pacote como o que não fecha, e nem o resultado nem a evidência são gravados

#### Scenario: Origem ou caminho de fatia posterior

- **WHEN** um resultado é enviado com origem `ai` ou caminho `text`
- **THEN** o servidor recusa por decisão sobre o pedido e nada é gravado

#### Scenario: A nota fecha só sem pendência objetiva

- **WHEN** o resultado do professor traz uma objetiva pendente, ou nenhuma
- **THEN** ele é gravado não fechado no primeiro caso e fechado no segundo

#### Scenario: Prova de outra organização

- **WHEN** o resultado do professor é de prova de organização de que o usuário autenticado não é membro
- **THEN** o servidor responde como ausência e nada é gravado

### Requirement: A revisão humana prevalece sobre a automática da mesma captura, em qualquer ordem de chegada

Para uma mesma captura da folha, a revisão de origem `teacher` SHALL prevalecer sobre a revisão automática (`omr`) dessa captura, **independentemente da ordem em que o servidor as recebe**: a invariante é que revisão humana vence qualquer resultado automático, e o modelo offline reordena e repete envios.

Quando a parcial de uma captura chega **depois** do resultado do professor que a completa — por falha transitória que a reenviou, ou por reordenação —, o servidor SHALL gravá-la como revisão **anterior legível**, SHALL responder com sucesso (o pendente do aparelho sai da fila), e SHALL NOT torná-la a corrente. Nada é sobrescrito, apagado nem recusado: o fato é append-only, e só a escolha de qual revisão é a corrente se submete a esta regra.

Uma **captura nova** da folha — nova leitura, que o resultado do professor não identifica como a que completou — SHALL seguir a regra de "A gravação no servidor é append-only e idempotente por revisão": é revisão nova e a mais recente é a corrente, e o resultado do professor da captura anterior SHALL permanecer legível. A nova captura traz outras imagens de resposta, e a nota dada sobre as anteriores não é a nota delas.

#### Scenario: A parcial chega depois da nota do professor

- **WHEN** o servidor recebe o resultado do professor de uma captura e, depois, a parcial dessa mesma captura
- **THEN** a parcial é gravada como revisão legível, o servidor responde com sucesso, e a corrente continua sendo a nota do professor

#### Scenario: A parcial chega antes, como de costume

- **WHEN** o servidor recebe a parcial de uma captura e, depois, o resultado do professor que a completa
- **THEN** a corrente é a nota do professor, e a parcial continua legível

#### Scenario: Reenvio da parcial depois da nota do professor

- **WHEN** a confirmação da parcial se perdeu, a nota do professor foi gravada, e a parcial é reenviada
- **THEN** o servidor reconhece o reenvio sem criar registro novo, e a corrente continua sendo a nota do professor

#### Scenario: Captura nova depois da nota do professor

- **WHEN** a mesma folha é lida de novo, e a parcial da nova captura chega depois da nota do professor da captura anterior
- **THEN** a parcial da nova captura é a corrente, e a nota do professor da captura anterior continua legível

### Requirement: O corpo da nota do professor não leva imagem, referência a arquivo nem dado pessoal

O corpo do resultado do professor SHALL NOT conter a imagem da resposta, o nome ou o caminho do arquivo que a guarda no aparelho, nem nome, turma ou matrícula do aluno. O aluno SHALL continuar identificado só pelo token que o QR da folha carrega. O servidor SHALL NOT gravar nenhum desses dados, e o resultado gravado SHALL NOT permitir recuperá-los.

A imagem da resposta fica no aparelho, sob o prazo de `scan-session` ("A resposta guardada tem prazo no aparelho"): gravar a nota do professor no servidor SHALL NOT exigir que a imagem suba, e **o servidor não elimina nem pede que se elimine** nada: quem elimina a imagem é o aparelho, **depois de receber a confirmação da nota**, e não antes.

#### Scenario: O corpo não leva imagem

- **WHEN** o servidor recebe um corpo de resultado do professor que traz, além do contrato, uma imagem ou o nome de um arquivo de resposta
- **THEN** nada disso é gravado, e o resultado gravado não permite recuperá-lo

#### Scenario: Nenhum dado pessoal direto

- **WHEN** o resultado do professor é enviado e gravado
- **THEN** ele não contém nome, turma nem matrícula do aluno, e identifica o aluno só pelo token

### Requirement: A nota do professor vira resultado durável pendente e é enviada pela rota própria

Confirmada a nota do professor sobre um caderno completo (`scan-session`: "O professor dá a nota das discursivas sobre a imagem, na hora"), o aparelho SHALL gravar o **resultado do professor** em armazenamento durável **antes** de tentar qualquer envio, sem rede, e SHALL fazê-lo nascer **pendente** na mesma fila dos demais resultados. O resultado SHALL sobreviver ao fim do processo e ao desligamento, como em "A nota apurada vira resultado durável antes de qualquer rede".

O resultado do professor SHALL ser **autocontido**, como a `5c-2` exige (`result-sync`: "O servidor grava a nota do professor como revisão nova da mesma folha"): leva a parte objetiva da parcial, a pontuação **exata** de cada discursiva e o total, a origem `teacher`, o caminho `image`, uma **chave de idempotência própria** e a **captura da parcial que completa**. Ele SHALL ser gravado e enviado pelo mesmo contrato e pela mesma rota que o servidor aceita, SHALL NOT recalcular a parte objetiva, e SHALL NOT depender de a parcial já ter sido confirmada: a nota pode ser enviada antes, depois ou sem a parcial. Reenvio SHALL repetir a mesma chave, e uma nova nota para a mesma folha SHALL ter chave nova.

O envio SHALL seguir as regras de "O resultado nasce pendente e espera a rede numa fila local" e "O pendente local só sai depois da confirmação, e sair da sessão não o apaga": ausência de rede, falha do servidor e recusa definitiva SHALL NOT apagar, alterar nem marcar como enviado o pendente, e a recusa definitiva SHALL NOT virar laço. A nota pendente SHALL ser escopada pela organização, sobreviver a sair da sessão e à revogação do vínculo, e SHALL ser enviada na sessão seguinte de qualquer membro. O aparelho SHALL contar a nota pendente entre os resultados ainda não enviados que informa ao sair.

O corpo SHALL NOT conter imagem, nome ou caminho de arquivo de resposta, nem nome, turma ou matrícula (`result-sync`: "O corpo da nota do professor não leva imagem, referência a arquivo nem dado pessoal"). A confirmação do servidor SHALL ser o evento que autoriza eliminar as respostas do caderno corrigido no aparelho (`scan-session`: "A resposta guardada tem prazo no aparelho"), e nenhum outro evento de envio o faz.

#### Scenario: A nota sobrevive ao fechamento do aplicativo

- **WHEN** o professor confirma a nota e o aplicativo é encerrado antes de qualquer envio
- **THEN** a nota continua gravada como pendente na abertura seguinte, e as respostas continuam no aparelho

#### Scenario: Sem rede, a nota espera

- **WHEN** a nota é confirmada com o aparelho sem rede
- **THEN** ela é gravada e fica pendente na fila, nada é enviado nem marcado como enviado

#### Scenario: A nota é enviada com o contrato da 5c-2

- **WHEN** há rede e a nota pendente é enviada
- **THEN** o corpo leva a parte objetiva, a pontuação exata de cada discursiva, o total, origem `teacher`, caminho `image`, a chave própria e a captura da parcial que completa

#### Scenario: A nota é fracionária e chega exata

- **WHEN** a nota com 1.5 e 1.75 é enviada e gravada
- **THEN** o servidor devolve exatamente 1.5 e 1.75 na revisão corrente, sem arredondamento

#### Scenario: A nota vai antes da parcial

- **WHEN** a parcial do caderno ainda está pendente e a nota é enviada primeiro
- **THEN** o servidor aceita a nota, e a parcial enviada depois não vira a corrente

#### Scenario: Reenvio da mesma nota

- **WHEN** a confirmação da nota se perde e ela é reenviada
- **THEN** o reenvio leva a mesma chave, o servidor o reconhece sem registro novo, e o pendente sai da fila

#### Scenario: Falha do servidor é tentada de novo

- **WHEN** o envio da nota é recusado por falha do servidor
- **THEN** a nota continua pendente, as respostas continuam no aparelho, e o aparelho agenda nova tentativa por conta própria

#### Scenario: Recusa definitiva não apaga nada

- **WHEN** o servidor recusa a nota por decisão sobre o pedido
- **THEN** a nota continua pendente, as respostas continuam no aparelho, e não há nova tentativa imediata

#### Scenario: Sair avisa da nota não enviada

- **WHEN** o usuário pede para sair com uma nota pendente
- **THEN** o aparelho a conta entre os resultados não enviados, e a nota e as respostas continuam no aparelho

#### Scenario: Confirmação elimina as respostas e só ela

- **WHEN** o servidor confirma a nota
- **THEN** o pendente sai da fila e as respostas do caderno corrigido são eliminadas; antes dessa confirmação nenhuma delas foi eliminada por conta do envio
