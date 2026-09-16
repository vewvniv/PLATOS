## MODIFIED Requirements

### Requirement: Sair apaga a sessão e a organização escolhida

O aplicativo SHALL oferecer sair. Sair SHALL apagar do aparelho a sessão, a organização escolhida,
**a última visão conhecida daquela organização**, os pacotes guardados sob ela e **os rosters
guardados sob ela**, e SHALL levar de volta à entrada.

Depois de sair, a entrada seguinte SHALL NOT vir com organização pré-selecionada, qualquer que seja
o usuário que entrar, e SHALL NOT alcançar pacote nem roster guardado antes da saída — a entrada
seguinte refaz o pull.

O apagamento dos pacotes é nomeado aqui, e não coberto por um requisito genérico de limpar dados
locais, porque requisito genérico é fácil de dar como cumprido sem reler. O aparelho é compartilhado
entre escolas, e o que o usuário anterior baixou sobrevivendo à troca de conta é invisível para quem
entra depois. **A visão entra na mesma lista pela mesma razão, e aparece em tela:** sem apagá-la,
quem entrasse depois no mesmo aparelho e abrisse sem rede leria o nome da organização anterior e a
lista de provas dela.

**O roster entra pela razão mais forte das cinco:** é o único item da lista que é **dado pessoal de
aluno**. Num aparelho compartilhado entre escolas, o nome de um menor sobrevivendo à troca de conta
é um caminho novo pelo qual o direito de eliminação deixaria de alcançar — que é exatamente o que o
§16 registra sob "O roster cacheado sem regra de apagamento". Enquanto o parecer jurídico não fixar
teto de retenção, esta é a leitura restritiva que vale, e nada além dela é assumido.

#### Scenario: Sair volta à entrada
- **WHEN** o usuário sai
- **THEN** o aplicativo apresenta a entrada, e nenhuma tela de trabalho é alcançável sem autenticar
  de novo

#### Scenario: A escolha do usuário anterior não é herdada
- **WHEN** um usuário escolhe uma organização, sai, e outro usuário entra no mesmo aparelho
- **THEN** nenhuma organização vem pré-selecionada para o segundo usuário

#### Scenario: O pacote do usuário anterior não é herdado
- **WHEN** um usuário baixa o pacote de uma prova, sai, e outro usuário entra no mesmo aparelho
- **THEN** nenhum pacote daquela organização continua guardado, e escolher uma prova refaz o pull

#### Scenario: Sair apaga a visão

- **WHEN** o usuário sai
- **THEN** nenhuma visão daquela organização continua guardada

#### Scenario: O roster do usuário anterior não é herdado

- **WHEN** um usuário puxa o roster de uma prova, sai, e outro usuário entra no mesmo aparelho
- **THEN** nenhum roster daquela organização continua guardado, e nenhum nome de aluno dela é
  apresentável sem um pull novo

### Requirement: O gate de pré-voo decide, antes da sessão, se ela pode abrir

O aplicativo SHALL decidir antes de abrir o escaneamento se tem, para a prova escolhida, **pacote
conferido e roster puxado**, e SHALL NOT abrir a câmera sem essa decisão ter passado.

A decisão SHALL ser binária: com os **dois** artefatos presentes a sessão abre; faltando qualquer um
deles, ela não abre e o motivo é apresentado. ADR-0009 fixa que não existe pacote parcialmente
presente para uma prova.

**Roster vazio e roster ausente SHALL ser estados distintos.** Roster puxado que não tem aluno algum
SHALL deixar a sessão abrir: `exam-package` declara que prova publicada sem roster é entregue como
roster vazio e é caso legítimo, e barrá-la tornaria inescaneável uma prova que a publicação declara
válida. Roster **nunca puxado** SHALL barrar: a sessão não abre sobre estado desconhecido, porque
"não há alunos" e "não sei quem são" levam a telas diferentes e só o primeiro é afirmação sobre o
mundo.

O gate SHALL recusar pacote que exija versão de renderizador maior que a do aplicativo, dizendo que
o aplicativo precisa ser atualizado. Este é o mesmo guarda que já existe na renderização, aplicado
ao caminho de captura — um pacote que o aplicativo não desenha por inteiro também não é um pacote
que ele deva medir.

Nenhum motivo de recusa SHALL ser apresentado como falha genérica: ausência de rede, pacote ausente,
conferência falha, versão insuficiente **e roster ausente** são cinco estados distintos, com cinco
frases distintas.

#### Scenario: Gate passa

- **WHEN** a prova escolhida tem pacote conferido e roster puxado no aparelho
- **THEN** o escaneamento abre contra esse pacote e esse roster

#### Scenario: Gate passa com roster vazio

- **WHEN** a prova escolhida tem pacote conferido e um roster puxado que não tem aluno algum
- **THEN** o escaneamento abre, e a ausência de alunos não é apresentada como recusa

#### Scenario: Gate barra por roster nunca puxado

- **WHEN** a prova escolhida tem pacote conferido, mas o roster dela nunca foi puxado neste aparelho
- **THEN** o escaneamento não abre, e o motivo apresentado é o roster ausente — distinto das frases
  de ausência de rede, pacote ausente, conferência falha e versão insuficiente

#### Scenario: Gate barra por versão

- **WHEN** o pacote conferido exige versão de renderizador maior que a do aplicativo
- **THEN** o escaneamento não abre, e o aplicativo diz que precisa ser atualizado para esta prova

#### Scenario: Gate barra por ausência

- **WHEN** não há pacote conferido para a prova escolhida e o pull não pôde ser feito
- **THEN** o escaneamento não abre, e o motivo apresentado distingue ausência de rede de falha de
  conferência

### Requirement: O aparelho guarda a última visão conhecida da organização e das provas

A cada consulta bem-sucedida, o aplicativo SHALL guardar, por organização, o nome dela, as provas
publicadas que a API devolveu — identificador, título e `content_hash` — e o instante em que aquilo
foi visto.

A visão guardada SHALL ser substituída **por inteiro** a cada consulta bem-sucedida, e SHALL NOT ser
emendada: visão parcialmente antiga é indistinguível de visão correta para quem lê a tela.

A visão guardada SHALL NOT conter dado pessoal. Ela descreve organização e provas, e nenhuma das duas
é pessoa; roster e identidade de aluno seguem fora daqui — eles são guardados à parte, pelo requisito
do roster guardado, e é lá que as regras deles valem.

A visão guardada SHALL NOT ter prazo próprio de validade: enquanto o servidor não disser o contrário,
ela vale. Quando o servidor responder e a organização não estiver mais entre as do usuário, o
aplicativo SHALL apagar a visão, os pacotes **e os rosters** guardados sob aquela organização.

O roster entra nesta lista, e não só na de sair, porque **os dois caminhos perdem o acesso e só um
deles é ação do usuário**. Quem foi removido da escola não vai sair para que o apagamento aconteça:
o aparelho descobre a remoção pela resposta do servidor, e é nesse instante que a cópia de nome de
aluno deixa de ter qualquer base para existir ali. Deixá-la esperando um logout seria manter dado
pessoal de menor num aparelho cujo dono já não pertence à organização.

O que sair apaga é dito **uma vez**, no requisito de sair, e a visão entra naquela lista.

#### Scenario: A visão é gravada quando a consulta responde

- **WHEN** a consulta das organizações e a listagem das provas respondem
- **THEN** o aparelho passa a guardar o nome da organização, as provas devolvidas e o instante da
  consulta

#### Scenario: A visão é substituída por inteiro

- **WHEN** uma consulta posterior devolve um conjunto diferente de provas
- **THEN** a visão guardada passa a ser a nova, sem restos da anterior

#### Scenario: Vínculo revogado apaga a visão e os pacotes

- **WHEN** o servidor responde e a organização guardada não está mais entre as do usuário
- **THEN** a escolha cai, e a visão, os pacotes **e os rosters** guardados sob aquela organização são
  apagados

#### Scenario: Revogação não espera logout para apagar o roster

- **WHEN** o vínculo é revogado e o usuário **não** sai do aplicativo
- **THEN** nenhum nome de aluno daquela organização continua guardado no aparelho

### Requirement: Dado apresentado a partir do cache é visualmente distinto de dado fresco

Toda tela que apresente dado vindo do que o aparelho guarda — a última visão conhecida **ou o roster
guardado** — SHALL marcá-lo de forma **visualmente distinta**, e não apenas por uma frase no meio do
texto, e SHALL dizer de quando ele é.

**O roster entra aqui, e não num requisito próprio de `scan-session`.** A regra é uma só — dado
guardado não se apresenta como fresco — e parti-la em dois lugares faria as duas metades divergirem
na primeira mudança. O nome de um aluno lido do roster guardado é exatamente o caso que a regra
existe para cobrir: ele pode ter sido corrigido no servidor depois do último pull, e o roster é
mutável por construção (ADR-0002), então "de quando ele é" diz mais aqui do que na visão.

O aplicativo SHALL oferecer, nessas telas, uma **ação explícita** de atualizar a visão.

Quando a atualização falhar, o aplicativo SHALL continuar apresentando a visão anterior, ainda
marcada, e SHALL NOT esvaziar a tela nem apresentar o dado como fresco.

#### Scenario: Trabalho a partir do cache

- **WHEN** a tela de trabalho é apresentada a partir da visão guardada
- **THEN** a marca de leitura cacheada está visível, o instante da última consulta é apresentado, e há
  uma ação de atualizar

#### Scenario: Atualizar com rede

- **WHEN** a ação de atualizar é acionada e a consulta responde
- **THEN** a visão passa a ser a nova e a marca de leitura cacheada sai da tela

#### Scenario: Atualizar sem rede

- **WHEN** a ação de atualizar é acionada e a consulta não chega ao servidor
- **THEN** a visão anterior continua apresentada e marcada, e o aplicativo diz que não conseguiu
  atualizar

#### Scenario: O nome do aluno vindo do roster guardado

- **WHEN** uma tela apresenta o nome de um aluno lido do roster guardado no aparelho
- **THEN** o nome carrega a marca visualmente distinta e o instante do último pull daquele roster, e
  não é apresentado como fresco

## ADDED Requirements

### Requirement: O aparelho guarda o roster puxado, escopado pela organização e pela prova

Ao escolher uma prova, o aplicativo SHALL obter o roster dela **da API**, e SHALL usar o que já
guarda quando a API não responder ou recusar.

A ordem é **o inverso** da do pacote guardado, e a diferença não é inconsistência: o pacote é imutável
e endereçado por hash, então consultar o guardado primeiro é correto por construção — hash igual é
conteúdo igual (ADR-0009). O roster é **mutável por construção** (ADR-0002): o nome de um aluno pode
ter sido corrigido, e um aluno pode ter entrado ou saído desde o último pull. Preferir o guardado com
a rede disponível apresentaria um nome que o servidor já corrigiu, e nada na tela diria isso.

Falha de rede ou recusa SHALL NOT apagar o roster guardado. O pull que não chega não deixa o aparelho
pior do que estava — é o mesmo critério de "atualizar sem rede não esvazia a tela" que esta capability
já exige da visão.

O que o aparelho guarda SHALL ser separado **por organização e por prova**. O escopo por organização
é o mesmo do pacote guardado e existe pela mesma razão: o que o aparelho guarda é um caminho de
leitura que não passa pela fronteira do servidor, e num aparelho compartilhado ele entregaria a quem
entrou depois um roster que a API recusaria. Aqui a razão é mais forte, porque o que vazaria pela
fronteira é **nome de aluno**.

Cada **linha de aluno** guardada SHALL conter **apenas** o que a entrega traz — token e nome de
apresentação. Turma, matrícula e referência externa SHALL NOT ser guardadas no aparelho, porque não
descem: `exam-package` declara que a entrega não as leva.

O roster guardado SHALL registrar **o instante do pull** que o produziu, e esse instante SHALL ser o
único dado fora das linhas de aluno. Ele não é dado de aluno, e existe porque a marca de dado
cacheado SHALL dizer "de quando ele é" — sem ele gravado, a marca teria de inventar a idade ou lê-la
do sistema de arquivos, que é âncora que muda sem avisar quando o arquivo é copiado ou restaurado.
A cláusula "apenas" acima vale sobre a linha, e esta vale sobre o que a cerca: as duas juntas dizem
que nada além de token, nome e instante é guardado.

A gravação SHALL ser atômica: SHALL NOT existir estado em que o roster guardado de uma prova esteja
pela metade. Queda de energia ou do aplicativo durante a gravação SHALL deixar o aparelho como se ela
não tivesse começado, e a prova SHALL continuar sem roster puxado para efeito do gate.

Roster já guardado SHALL ser usado sem rede. Roster ausente e sem rede SHALL levar à recusa
explicada pelo gate, e SHALL NOT levar a escaneamento.

O roster guardado SHALL ser **substituído por inteiro** a cada pull bem-sucedido, e SHALL NOT ser
mesclado com o que já estava lá. Mesclar preservaria no aparelho a linha de um aluno que saiu do
roster no servidor — uma cópia de dado pessoal que o servidor já não tem, e que nenhuma correção
alcançaria.

#### Scenario: Primeira vez que a prova é escolhida

- **WHEN** uma prova é escolhida e o aparelho não guarda roster dela
- **THEN** o roster é puxado da API e guardado dentro do escopo da organização ativa e daquela prova

#### Scenario: Com rede, o que o servidor diz substitui o guardado

- **WHEN** uma prova cujo roster o aparelho já guarda é escolhida com a API respondendo
- **THEN** o roster guardado passa a ser o que a API devolveu, e um nome corrigido no servidor é o
  que o aparelho passa a apresentar

#### Scenario: Recusa da API preserva o roster guardado

- **WHEN** a obtenção do roster é recusada pelo servidor e o aparelho já guardava um
- **THEN** o roster guardado continua servindo, e não é apagado pela recusa

#### Scenario: Segunda vez, sem rede

- **WHEN** a mesma prova é escolhida com o aparelho sem rede, depois de o roster já ter sido guardado
- **THEN** o roster guardado é usado, e o escaneamento abre normalmente

#### Scenario: O que desce é o que é guardado

- **WHEN** o roster de uma prova é puxado e guardado
- **THEN** cada linha guardada tem token e nome de apresentação e nada mais, o instante do pull é o
  único dado fora das linhas, e turma, matrícula e referência externa não aparecem em lugar nenhum
  do que foi gravado

#### Scenario: Gravação interrompida

- **WHEN** a gravação do roster é interrompida antes de terminar
- **THEN** o aparelho não passa a guardar roster parcial daquela prova, e o gate continua tratando a
  prova como sem roster puxado

#### Scenario: Aluno retirado do roster some do aparelho

- **WHEN** um aluno é retirado do roster no servidor e o roster daquela prova é puxado de novo
- **THEN** o aparelho não guarda mais a linha daquele aluno, e o nome dele não é apresentável a
  partir do que ficou guardado

#### Scenario: Roster de outra organização não é alcançável

- **WHEN** um roster é guardado sob uma organização e a organização ativa passa a ser outra
- **THEN** aquele roster não é usado, e escolher prova na organização ativa não o alcança
