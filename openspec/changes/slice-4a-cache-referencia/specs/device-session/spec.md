## ADDED Requirements

### Requirement: O aparelho guarda a última visão conhecida da organização e das provas

A cada consulta bem-sucedida, o aplicativo SHALL guardar, por organização, o nome dela, as provas
publicadas que a API devolveu — identificador, título e `content_hash` — e o instante em que aquilo
foi visto.

A visão guardada SHALL ser substituída **por inteiro** a cada consulta bem-sucedida, e SHALL NOT ser
emendada: visão parcialmente antiga é indistinguível de visão correta para quem lê a tela.

A visão guardada SHALL NOT conter dado pessoal. Ela descreve organização e provas, e nenhuma das duas
é pessoa; roster e identidade de aluno seguem fora daqui.

A visão guardada SHALL NOT ter prazo próprio de validade: enquanto o servidor não disser o contrário,
ela vale. Quando o servidor responder e a organização não estiver mais entre as do usuário, o
aplicativo SHALL apagar a visão **e** os pacotes guardados sob aquela organização.

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
- **THEN** a escolha cai, a visão daquela organização é apagada, e os pacotes guardados sob ela também

### Requirement: Sem rede, o trabalho continua a partir da última visão conhecida

Com credencial e organização guardadas, quando a consulta das organizações **não puder ser feita por
falta de rede**, o aplicativo SHALL seguir para a tela de trabalho apresentando a última visão
conhecida, e SHALL NOT parar antes de qualquer listagem.

O aplicativo SHALL distinguir os dois casos que hoje terminam igual: **o servidor não respondeu** —
segue com a última visão conhecida — e **o servidor respondeu e o vínculo não está lá** — a escolha
cai. Esta segunda regra não muda.

Sem visão conhecida para a organização guardada, o aplicativo SHALL recusar com motivo, dizendo que
precisa de rede uma vez, e SHALL NOT apresentar tela de trabalho vazia.

#### Scenario: Reabrir sem rede, com visão conhecida

- **WHEN** o aplicativo é aberto sem rede, com credencial, organização e visão guardadas
- **THEN** a tela de trabalho é apresentada a partir da visão guardada, e o escaneamento é alcançável
  sem nenhuma chamada de rede

#### Scenario: Reabrir sem rede, sem visão conhecida

- **WHEN** o aplicativo é aberto sem rede e não há visão guardada para a organização
- **THEN** o aplicativo diz que precisa de rede uma vez, e não apresenta tela de trabalho

#### Scenario: A rede volta e o vínculo caiu

- **WHEN** a consulta volta a responder e a organização guardada não está mais entre as do usuário
- **THEN** a escolha guardada cai, como já caía, e o que estava guardado sob ela é apagado

### Requirement: Dado apresentado a partir do cache é visualmente distinto de dado fresco

Toda tela que apresente dado vindo da última visão conhecida SHALL marcá-lo de forma **visualmente
distinta** — e não apenas por uma frase no meio do texto —, e SHALL dizer de quando ele é.

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

## MODIFIED Requirements

### Requirement: Sair apaga a sessão e a organização escolhida

O aplicativo SHALL oferecer sair. Sair SHALL apagar do aparelho a sessão, a organização escolhida,
**a última visão conhecida daquela organização** e os pacotes guardados sob ela, e SHALL levar de
volta à entrada.

Depois de sair, a entrada seguinte SHALL NOT vir com organização pré-selecionada, qualquer que seja
o usuário que entrar, e SHALL NOT alcançar pacote guardado antes da saída — a entrada seguinte refaz
o pull.

O apagamento dos pacotes é nomeado aqui, e não coberto por um requisito genérico de limpar dados
locais, porque requisito genérico é fácil de dar como cumprido sem reler. O aparelho é compartilhado
entre escolas, e o que o usuário anterior baixou sobrevivendo à troca de conta é invisível para quem
entra depois. **A visão entra na mesma lista pela mesma razão, e é a única das quatro que aparece em
tela:** sem apagá-la, quem entrasse depois no mesmo aparelho e abrisse sem rede leria o nome da
organização anterior e a lista de provas dela.

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

### Requirement: A organização apresentada vem da API, e não do aparelho

Depois de autenticar, o aplicativo SHALL obter as organizações do usuário pela API e SHALL apresentar
o nome da organização ativa a partir do que a API devolveu.

O aplicativo SHALL NOT apresentar nome digitado pelo usuário, embutido no aplicativo ou derivado da
credencial.

Quando a consulta não puder ser feita, o aplicativo SHALL apresentar o **último nome que a API
devolveu para aquela organização**, marcado como leitura cacheada e com o instante em que foi visto,
ou — não havendo nenhum — SHALL dizer que não conseguiu obter a organização e SHALL NOT apresentar
nome nenhum.

A regra que não mudou é a que importa: **todo nome apresentado veio da API**. O que a ausência de rede
muda é a **idade** do nome, nunca a sua procedência, e a idade é apresentada junto com ele.

#### Scenario: Entrada bem-sucedida
- **WHEN** o usuário autentica com credencial válida e a consulta responde
- **THEN** o nome apresentado é o que a API devolveu para aquela organização, sem marca de cache

#### Scenario: A consulta falha depois de autenticar, e há visão guardada
- **WHEN** a autenticação fecha, a consulta das organizações falha por falta de rede, e há visão
  guardada para a organização
- **THEN** o nome apresentado é o último que a API devolveu, marcado como leitura cacheada e com o
  instante em que foi visto

#### Scenario: A consulta falha depois de autenticar
- **WHEN** a autenticação fecha e a consulta das organizações falha sem que haja visão guardada
- **THEN** o aplicativo explica que não conseguiu obter a organização, e nenhum nome é apresentado

#### Scenario: Entrar duas vezes não duplica nada
- **WHEN** o mesmo usuário sai e entra de novo no mesmo aparelho
- **THEN** as organizações apresentadas são as mesmas da entrada anterior

### Requirement: A prova a escanear é escolhida entre as que a API apresenta

Com uma organização ativa, o aplicativo SHALL apresentar as provas publicadas dessa organização como
vieram da API, e SHALL exigir que uma seja escolhida antes de qualquer escaneamento.

O aplicativo SHALL NOT oferecer prova que não veio da consulta, nem permitir que o identificador de
uma prova seja digitado ou embutido. Se nunca houve consulta bem-sucedida, não há de onde inventar a
lista — é o mesmo princípio pelo qual o nome da organização vem da API.

Quando a listagem não puder ser feita por falta de rede, o aplicativo SHALL apresentar as provas da
**última listagem conhecida**, marcadas como leitura cacheada, e SHALL distinguir, antes da escolha,
as que já têm pacote guardado das que não têm — escolher uma prova sem pacote guardado e sem rede
termina em recusa, e o professor precisa saber disso antes de tocar.

Falha da consulta sem visão guardada SHALL ser apresentada com motivo, distinguindo ausência de rede
de outras falhas, e SHALL NOT ser apresentada como lista vazia. Organização que de fato não tem prova
publicada SHALL ser apresentada como tal, e é estado diferente de consulta que falhou.

#### Scenario: Provas apresentadas

- **WHEN** a consulta devolve as provas publicadas da organização ativa
- **THEN** o aplicativo apresenta essas provas, e escolher uma é o que leva ao escaneamento

#### Scenario: Organização sem prova publicada

- **WHEN** a consulta devolve nenhuma prova
- **THEN** o aplicativo diz que a organização não tem prova publicada, e isso é distinguível de falha
  na consulta

#### Scenario: Consulta sem rede, com listagem conhecida

- **WHEN** a listagem não chega ao servidor e há uma listagem anterior guardada
- **THEN** as provas da última listagem são apresentadas, marcadas como leitura cacheada, e as que já
  têm pacote guardado são distinguíveis das que não têm

#### Scenario: Consulta sem rede

- **WHEN** a listagem não chega ao servidor e nunca houve listagem bem-sucedida
- **THEN** o aplicativo diz que está sem rede, e não apresenta lista vazia
