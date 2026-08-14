## Purpose

Define quem é o usuário, quais organizações existem, como um usuário pertence a várias organizações simultaneamente e como todo dado de domínio fica isolado por organização. É a fronteira de autorização de todo o sistema: nenhuma outra capacidade decide quem pode ver o quê.

## ADDED Requirements

### Requirement: Identidade do usuário derivada do provedor de autenticação

O sistema SHALL aceitar apenas requisições portando um token de acesso válido emitido pelo provedor de autenticação, verificado contra as chaves públicas publicadas pelo provedor. O sistema SHALL identificar o usuário exclusivamente pelo sujeito (`sub`) do token e SHALL manter um registro local de usuário correspondente a esse sujeito.

O sistema SHALL NOT aceitar identidade de usuário informada em corpo, query string ou cabeçalho fora do token.

#### Scenario: Token válido

- **WHEN** uma requisição chega com um token de acesso válido e não expirado
- **THEN** o sistema resolve o usuário correspondente ao sujeito do token e processa a requisição

#### Scenario: Token ausente

- **WHEN** uma requisição chega sem token de acesso
- **THEN** o sistema responde `401` e não executa nenhuma leitura ou escrita de domínio

#### Scenario: Token inválido, expirado ou com assinatura desconhecida

- **WHEN** uma requisição chega com token expirado, malformado, ou assinado por chave que não consta nas chaves públicas do provedor
- **THEN** o sistema responde `401` e não executa nenhuma leitura ou escrita de domínio

#### Scenario: Identidade forjada no payload

- **WHEN** uma requisição autenticada como usuário A também informa um identificador de usuário B em cabeçalho, query ou corpo
- **THEN** o sistema ignora o valor informado e atua exclusivamente como usuário A

### Requirement: Provisionamento idempotente da organização pessoal

No primeiro acesso autenticado de um usuário que ainda não pertence a nenhuma organização, o sistema SHALL criar uma organização de tipo `personal` e um vínculo desse usuário a ela com papel `owner`.

O provisionamento SHALL ser idempotente: qualquer número de acessos, inclusive concorrentes, SHALL resultar em no máximo uma organização pessoal por usuário.

O sistema SHALL NOT criar organização pessoal para um usuário que já possua um vínculo com qualquer organização.

#### Scenario: Primeiro acesso de usuário novo

- **WHEN** um usuário autenticado sem nenhum vínculo acessa o sistema pela primeira vez
- **THEN** o sistema cria uma organização de tipo `personal` e um vínculo desse usuário a ela com papel `owner`, e a requisição prossegue normalmente

#### Scenario: Acessos subsequentes

- **WHEN** o mesmo usuário acessa o sistema novamente
- **THEN** nenhuma organização adicional é criada e o vínculo existente é reutilizado

#### Scenario: Acessos concorrentes do mesmo usuário novo

- **WHEN** duas requisições autenticadas do mesmo usuário novo chegam simultaneamente
- **THEN** exatamente uma organização pessoal existe ao final, ambas as requisições respondem com sucesso e nenhuma falha por violação de restrição vaza ao cliente

#### Scenario: Usuário que só pertence a uma organização escolar

- **WHEN** um usuário autenticado já possui vínculo apenas com uma organização de tipo `school` e acessa o sistema
- **THEN** nenhuma organização pessoal é criada para ele

### Requirement: Pertencimento N:N entre usuário e organização

Um usuário SHALL poder pertencer simultaneamente a múltiplas organizações, cada uma com seu próprio papel. Um vínculo SHALL ser único por par usuário/organização.

Ganhar acesso a uma organização SHALL ser a inserção de um vínculo, sem mover, reatribuir ou reparentar nenhum dado preexistente do usuário.

O papel do vínculo SHALL ser um entre `owner`, `admin` e `teacher`.

#### Scenario: Usuário em duas organizações

- **WHEN** um usuário com organização pessoal recebe um vínculo com uma organização de tipo `school`
- **THEN** ele passa a pertencer às duas organizações, com papéis independentes em cada uma

#### Scenario: Acervo pessoal preservado ao entrar em escola

- **WHEN** um usuário que já possui dados em sua organização pessoal recebe um vínculo com uma organização de tipo `school`
- **THEN** todo dado preexistente continua atribuído à organização pessoal e permanece acessível por ela, e nenhuma linha tem sua organização alterada

#### Scenario: Vínculo duplicado

- **WHEN** uma segunda tentativa de vincular o mesmo usuário à mesma organização ocorre
- **THEN** o sistema rejeita a duplicação e o estado permanece com exatamente um vínculo para esse par

#### Scenario: Papel inválido

- **WHEN** uma tentativa de criar vínculo informa um papel fora do conjunto `owner`, `admin`, `teacher`
- **THEN** a operação é rejeitada e nenhum vínculo é criado

### Requirement: Isolamento por organização imposto no armazenamento

Todo dado de domínio SHALL ser atribuído a exatamente uma organização. O isolamento entre organizações SHALL ser imposto pelo próprio armazenamento em função dos vínculos do usuário corrente, e não por filtro escrito na aplicação.

Uma consulta emitida sem qualquer filtro de organização na aplicação SHALL retornar apenas linhas de organizações às quais o usuário corrente está vinculado.

Autorização SHALL ser decidida exclusivamente pelo vínculo entre usuário e organização. Metadados de autoria como "criado por" SHALL NOT conceder nem negar acesso.

#### Scenario: Leitura cruzada entre organizações

- **WHEN** um usuário vinculado apenas à organização A emite uma consulta sem filtro de organização sobre uma tabela que contém linhas das organizações A e B
- **THEN** apenas as linhas da organização A são retornadas

#### Scenario: Escrita em organização alheia

- **WHEN** um usuário vinculado apenas à organização A tenta inserir ou atualizar uma linha atribuída à organização B
- **THEN** a operação é recusada e nenhuma linha da organização B é criada ou modificada

#### Scenario: Autoria não concede acesso

- **WHEN** um usuário criou uma linha na organização A, teve seu vínculo com A removido, e consulta essa linha
- **THEN** a linha não é retornada, mesmo constando o usuário como seu criador

#### Scenario: Usuário sem vínculo

- **WHEN** um usuário autenticado sem nenhum vínculo consulta qualquer tabela de domínio
- **THEN** nenhuma linha é retornada

### Requirement: Consulta das organizações do usuário

O sistema SHALL expor ao usuário autenticado a lista das organizações às quais ele pertence, incluindo para cada uma seu identificador, nome, tipo (`personal` ou `school`) e o papel do usuário nela.

A lista SHALL conter exatamente as organizações vinculadas ao usuário do token, e nenhuma outra.

#### Scenario: Usuário recém-criado

- **WHEN** um usuário que acabou de ser provisionado consulta suas organizações
- **THEN** a resposta contém exatamente uma organização, de tipo `personal`, com papel `owner`

#### Scenario: Usuário com múltiplos vínculos

- **WHEN** um usuário vinculado a uma organização pessoal e a duas escolares consulta suas organizações
- **THEN** a resposta contém as três organizações, cada uma com seu tipo e seu papel

#### Scenario: Organizações de terceiros não vazam

- **WHEN** existem outras organizações no sistema às quais o usuário não está vinculado
- **THEN** nenhuma delas aparece na resposta

#### Scenario: Requisição não autenticada

- **WHEN** a lista é solicitada sem token válido
- **THEN** o sistema responde `401` sem revelar existência de organizações
