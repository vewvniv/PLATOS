## Purpose

A sessão que vive no aparelho: quem entrou, qual organização está ativa para este aparelho, o que a tela diz em cada estado de falha, e o que sair apaga. É distinta de `identity`, que é a fronteira de autorização do servidor — `identity` decide quem pode ver o quê, e esta capacidade decide o que este aparelho sabe sobre quem o está segurando.

## Requirements

### Requirement: O aparelho autentica antes de qualquer trabalho

O aplicativo SHALL exigir uma sessão autenticada antes de abrir qualquer tela de trabalho, e SHALL NOT partir direto para o escaneamento.

A autenticação SHALL usar e-mail e senha contra o provedor de autenticação, e a sessão obtida SHALL ser apresentada à API como credencial em cada chamada.

#### Scenario: Primeira abertura do aplicativo
- **WHEN** o aplicativo é aberto sem sessão guardada
- **THEN** a tela de entrada é apresentada, e nenhuma tela de trabalho é alcançável

#### Scenario: Sessão guardada
- **WHEN** o aplicativo é aberto e há sessão válida guardada
- **THEN** a entrada é dispensada, e o aplicativo segue para a tela de trabalho

### Requirement: A organização apresentada vem da API, e não do aparelho

Depois de autenticar, o aplicativo SHALL obter as organizações do usuário pela API e SHALL apresentar o nome da organização ativa a partir do que a API devolveu.

O aplicativo SHALL NOT apresentar nome digitado pelo usuário, embutido no aplicativo ou derivado da credencial. Quando a consulta não puder ser feita, o aplicativo SHALL dizer isso, e SHALL NOT apresentar nome nenhum.

#### Scenario: Entrada bem-sucedida
- **WHEN** o usuário autentica com credencial válida e a consulta responde
- **THEN** o nome apresentado é o que a API devolveu para aquela organização

#### Scenario: A consulta falha depois de autenticar
- **WHEN** a autenticação fecha e a consulta das organizações falha
- **THEN** o aplicativo explica que não conseguiu obter a organização, e nenhum nome é apresentado

#### Scenario: Entrar duas vezes não duplica nada
- **WHEN** o mesmo usuário sai e entra de novo no mesmo aparelho
- **THEN** as organizações apresentadas são as mesmas da entrada anterior

### Requirement: Um usuário com mais de uma organização escolhe qual, e a escolha é do aparelho

Quando a API devolver mais de uma organização, o aplicativo SHALL pedir que o usuário escolha uma antes de seguir, e SHALL NOT escolher por ele.

A escolha SHALL sobreviver ao fechamento do aplicativo. Quando a API devolver exatamente uma, o aplicativo SHALL seguir com ela sem perguntar.

#### Scenario: Duas organizações
- **WHEN** a API devolve duas organizações e nenhuma foi escolhida ainda
- **THEN** o aplicativo apresenta as duas e espera a escolha

#### Scenario: A escolha sobrevive ao fechamento
- **WHEN** o usuário escolhe uma organização, fecha o aplicativo e o abre de novo com a sessão válida
- **THEN** a organização escolhida continua ativa, e a escolha não é pedida de novo

#### Scenario: Uma organização só
- **WHEN** a API devolve exatamente uma organização
- **THEN** ela fica ativa sem que a escolha seja pedida

### Requirement: Credencial inválida, ausência de rede e sessão expirada são três estados distintos

O aplicativo SHALL distinguir credencial recusada, ausência de rede e sessão expirada, e SHALL apresentar o motivo de cada uma. Ele SHALL NOT tratar uma como a outra, e SHALL NOT ficar em carregamento sem desfecho.

Sessão expirada SHALL levar de volta à entrada no momento em que for detectada, e SHALL NOT ser deixada para falhar numa chamada posterior com mensagem que não seja sobre a sessão.

#### Scenario: Credencial recusada
- **WHEN** o usuário tenta entrar com credencial inválida
- **THEN** o aplicativo diz que a credencial foi recusada, permanece na entrada, e não avança

#### Scenario: Sem rede na entrada
- **WHEN** o usuário tenta entrar sem rede disponível
- **THEN** o aplicativo diz que precisa de rede, e não apresenta isso como credencial recusada

#### Scenario: Sessão expirada
- **WHEN** a sessão guardada já não é aceita pela API
- **THEN** o aplicativo volta à entrada dizendo que a sessão expirou

### Requirement: A credencial guardada não fica legível no aparelho

A credencial de sessão guardada no aparelho SHALL estar cifrada em repouso, e SHALL NOT ser recuperável pela leitura direta do armazenamento.

A organização escolhida não é credencial e não exige cifragem. Nenhuma das duas SHALL ser incluída em backup automático do aparelho.

#### Scenario: A credencial não está em claro
- **WHEN** a sessão é guardada e o armazenamento do aplicativo é lido diretamente
- **THEN** o token não aparece como texto legível em lugar nenhum do que foi gravado

#### Scenario: A credencial não sai no backup
- **WHEN** o aparelho executa backup automático
- **THEN** nem a credencial nem a organização escolhida são incluídas

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

### Requirement: Voltar do escaneamento devolve à escolha da prova

Quando o escaneamento fecha e a tela do preparo volta, o aplicativo SHALL apresentar de novo as
provas da última consulta bem-sucedida, e SHALL NOT permanecer na tela de preparo.

A volta SHALL NOT depender de rede. A lista apresentada é a que já veio, e escolher de novo a mesma
prova usa o pacote já guardado: escanear é atividade de sala, e sala é onde não há sinal — uma volta
que precisasse consultar de novo devolveria tela de falha a quem acabou de escanear.

Nenhuma tela do preparo SHALL afirmar estado que não é o seu. "Preparando" é o que está sendo obtido
e conferido; o que já passou pelo gate está abrindo a câmera. Dizer a mesma frase nos dois casos
esconde exatamente o estado em que o aplicativo pode ficar parado.

#### Scenario: Voltar do escaneamento

- **WHEN** o escaneamento fecha e a tela do preparo volta
- **THEN** as provas já apresentadas voltam à tela, e o aplicativo não fica na tela de preparo

#### Scenario: Escanear a mesma prova outra vez

- **WHEN** a mesma prova é escolhida de novo depois de voltar do escaneamento
- **THEN** o preparo recomeça por ela, sem nova consulta de provas e usando o pacote já guardado

#### Scenario: Volta que chega fora do escaneamento

- **WHEN** a volta chega com o preparo em outro estado que não o de escaneamento aberto
- **THEN** o estado do preparo não é reescrito

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
