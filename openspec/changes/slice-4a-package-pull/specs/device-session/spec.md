## ADDED Requirements

### Requirement: A prova a escanear é escolhida entre as que a API apresenta

Com uma organização ativa, o aplicativo SHALL apresentar as provas publicadas dessa organização
como vieram da API, e SHALL exigir que uma seja escolhida antes de qualquer escaneamento.

O aplicativo SHALL NOT oferecer prova que não veio da consulta, nem permitir que o identificador de
uma prova seja digitado ou embutido. Se a consulta falhar, não há de onde inventar a lista — é o
mesmo princípio pelo qual o nome da organização vem da API.

Falha da consulta SHALL ser apresentada com motivo, distinguindo ausência de rede de outras falhas,
e SHALL NOT ser apresentada como lista vazia. Organização que de fato não tem prova publicada SHALL
ser apresentada como tal, e é estado diferente de consulta que falhou.

#### Scenario: Provas apresentadas

- **WHEN** a consulta devolve as provas publicadas da organização ativa
- **THEN** o aplicativo apresenta essas provas, e escolher uma é o que leva ao escaneamento

#### Scenario: Organização sem prova publicada

- **WHEN** a consulta devolve nenhuma prova
- **THEN** o aplicativo diz que a organização não tem prova publicada, e isso é distinguível de
  falha na consulta

#### Scenario: Consulta sem rede

- **WHEN** a consulta das provas não chega ao servidor
- **THEN** o aplicativo diz que está sem rede, e não apresenta lista vazia

### Requirement: O aparelho guarda o pacote puxado, endereçado pelo seu conteúdo e escopado pela organização

Ao escolher uma prova, o aplicativo SHALL obter o pacote dela: do que já guarda, quando o conteúdo
correspondente estiver presente e conferido, e da API caso contrário.

O que o aparelho guarda SHALL ser endereçado pelo `content_hash` do conteúdo e SHALL ser separado
por organização. O escopo por organização não é organização de arquivos: o que o aparelho guarda é
um caminho de leitura que não passa pela fronteira do servidor, e num aparelho compartilhado ele
entregaria a quem entrou depois um pacote que a API recusaria.

A gravação SHALL ser atômica: SHALL NOT existir estado em que o conteúdo guardado sob um hash esteja
incompleto. Queda de energia ou do aplicativo durante a gravação SHALL deixar o aparelho como se ela
não tivesse começado.

Pacote já guardado SHALL ser usado sem rede. Pacote ausente e sem rede SHALL levar à recusa
explicada, e SHALL NOT levar a escaneamento.

#### Scenario: Primeira vez que a prova é escolhida

- **WHEN** uma prova é escolhida e o aparelho não guarda o conteúdo dela
- **THEN** o pacote é puxado da API, conferido e guardado sob o hash do seu conteúdo, dentro do
  escopo da organização ativa

#### Scenario: Segunda vez, sem rede

- **WHEN** a mesma prova é escolhida com o aparelho sem rede, depois de o pacote já ter sido guardado
- **THEN** o pacote guardado é usado, e o escaneamento abre normalmente

#### Scenario: Pacote ausente e sem rede

- **WHEN** uma prova cujo pacote o aparelho não guarda é escolhida sem rede
- **THEN** o aplicativo recusa abrir o escaneamento e diz que a prova precisa ser baixada com rede

#### Scenario: Gravação interrompida

- **WHEN** a gravação do pacote é interrompida antes de terminar
- **THEN** o aparelho não passa a guardar conteúdo parcial sob aquele hash, e a próxima escolha
  refaz o pull

#### Scenario: Pacote de outra organização não é alcançável

- **WHEN** um pacote é guardado sob uma organização e a organização ativa passa a ser outra
- **THEN** aquele pacote não é usado, e escolher prova na organização ativa não o alcança

### Requirement: Todo pacote é conferido antes de ser usado, e reconferido a cada leitura

O aplicativo SHALL recusar usar pacote que não passe por duas conferências, e SHALL fazê-las tanto
sobre o que chegou da API quanto sobre o que foi lido do que ele guarda.

**Integridade do conteúdo:** o hash calculado sobre os bytes obtidos SHALL ser igual ao
`content_hash` declarado — pela API, na entrega; pelo endereço sob o qual o conteúdo estava
guardado. O hash SHALL ser recalculado sobre os bytes lidos, e SHALL NOT ser presumido do endereço:
sem isso a conferência vale uma vez, na gravação, e todo uso seguinte é de conteúdo que ninguém mais
olhou — corrupção em repouso, gravação truncada e adulteração local passariam caladas.

**Fidelidade da interpretação:** reserializar canonicamente o pacote interpretado SHALL produzir os
mesmos bytes que foram conferidos. Esta conferência responde uma pergunta diferente da primeira: o
hash diz "recebi o que foi publicado?", e esta diz "eu entendi o que recebi?". O que ela pega é
desalinhamento de versão — campo que uma versão antiga do aplicativo descarta em silêncio, valor
padrão que ela injeta —, que um pacote íntegro tem e que o hash não acusa.

Conteúdo guardado que falhe qualquer das duas SHALL ser descartado, e o aplicativo SHALL recair no
pull. Conteúdo vindo da API que falhe qualquer das duas SHALL NOT ser guardado, e a recusa SHALL
dizer qual conferência falhou.

Nenhuma das duas conferências é autenticidade: o hash vem do mesmo servidor que os bytes, e quem
controlar a resposta controla os dois. Autenticidade é do transporte cifrado.

#### Scenario: Conteúdo guardado corrompido

- **WHEN** o conteúdo guardado sob um hash é alterado e a prova é escolhida de novo
- **THEN** a conferência de integridade falha, o conteúdo é descartado, e o aplicativo puxa de novo

#### Scenario: Conteúdo entregue com hash divergente

- **WHEN** a API entrega conteúdo cujo hash não bate com o declarado
- **THEN** o pacote é recusado com motivo, nada é guardado, e o escaneamento não abre

#### Scenario: Pacote que a versão do aplicativo não interpreta fielmente

- **WHEN** o pacote é íntegro mas reserializá-lo canonicamente não reproduz os bytes conferidos
- **THEN** o pacote é recusado dizendo que esta versão do aplicativo não o interpreta por inteiro, e
  nenhuma nota é produzida a partir dele

#### Scenario: Recusa é recusa, e não degradação

- **WHEN** qualquer conferência falha
- **THEN** o aplicativo não escaneia com o pacote recusado, e não apresenta resultado parcial

### Requirement: O gate de pré-voo decide, antes da sessão, se ela pode abrir

O aplicativo SHALL decidir antes de abrir o escaneamento se tem pacote conferido para a prova
escolhida, e SHALL NOT abrir a câmera sem essa decisão ter passado.

A decisão SHALL ser binária: com pacote conferido a sessão abre; sem ele, ela não abre e o motivo é
apresentado. ADR-0009 fixa que não existe pacote parcialmente presente para uma prova.

O gate SHALL recusar pacote que exija versão de renderizador maior que a do aplicativo, dizendo que
o aplicativo precisa ser atualizado. Este é o mesmo guarda que já existe na renderização, aplicado
ao caminho de captura — um pacote que o aplicativo não desenha por inteiro também não é um pacote
que ele deva medir.

Nenhum motivo de recusa SHALL ser apresentado como falha genérica: ausência de rede, pacote ausente,
conferência falha e versão insuficiente são quatro estados distintos, com quatro frases distintas.

#### Scenario: Gate passa

- **WHEN** a prova escolhida tem pacote conferido no aparelho
- **THEN** o escaneamento abre contra esse pacote

#### Scenario: Gate barra por versão

- **WHEN** o pacote conferido exige versão de renderizador maior que a do aplicativo
- **THEN** o escaneamento não abre, e o aplicativo diz que precisa ser atualizado para esta prova

#### Scenario: Gate barra por ausência

- **WHEN** não há pacote conferido para a prova escolhida e o pull não pôde ser feito
- **THEN** o escaneamento não abre, e o motivo apresentado distingue ausência de rede de falha de
  conferência

## MODIFIED Requirements

### Requirement: Sessão expirada leva de volta à entrada, seja qual for a tela

Quando uma chamada autenticada é recusada por credencial expirada, o aplicativo SHALL descartar a
credencial guardada e SHALL apresentar a entrada dizendo que a sessão expirou — **qualquer que seja
o estado em que o aparelho esteja**, e não apenas durante a consulta das organizações.

O requisito é escrito assim porque a fatia que criou as chamadas do pacote também criou o primeiro
caso de chamada autenticada feita fora da consulta: listar provas e puxar pacote acontecem com a
organização já ativa. Tratar a expiração só na consulta a descartava em silêncio, e o resultado era
uma tela sem saída — credencial expirada ainda guardada, e um "tentar de novo" que falharia sempre.

Recusa que chega depois de o usuário já ter saído SHALL NOT reescrever o motivo apresentado: quem
saiu por vontade própria não viu a sessão expirar.

#### Scenario: Expiração detectada durante a consulta das organizações
- **WHEN** a consulta das organizações é recusada por credencial expirada
- **THEN** a credencial é descartada e a entrada é apresentada dizendo que a sessão expirou

#### Scenario: Expiração detectada com a organização já ativa
- **WHEN** a listagem das provas ou o pull do pacote é recusado por credencial expirada, com o
  aparelho já operando numa organização
- **THEN** a credencial é descartada e a entrada é apresentada dizendo que a sessão expirou, e o
  aplicativo NÃO apresenta falha de listagem nem oferece repetir a chamada

#### Scenario: Expiração que chega depois de sair
- **WHEN** a recusa por credencial expirada chega depois de o usuário já ter saído
- **THEN** o motivo apresentado continua sendo o da saída, e não o de expiração


### Requirement: Sair apaga a sessão e a organização escolhida

O aplicativo SHALL oferecer sair. Sair SHALL apagar do aparelho a sessão, a organização escolhida
**e os pacotes guardados sob aquela organização**, e SHALL levar de volta à entrada.

Depois de sair, a entrada seguinte SHALL NOT vir com organização pré-selecionada, qualquer que seja
o usuário que entrar, e SHALL NOT alcançar pacote guardado antes da saída — a entrada seguinte refaz
o pull.

O apagamento dos pacotes é nomeado aqui, e não coberto por um requisito genérico de limpar dados
locais, porque requisito genérico é fácil de dar como cumprido sem reler. O aparelho é compartilhado
entre escolas, e o que o usuário anterior baixou sobrevivendo à troca de conta é invisível para quem
entra depois.

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
