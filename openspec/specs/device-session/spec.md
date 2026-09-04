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

O aplicativo SHALL oferecer sair. Sair SHALL apagar do aparelho a sessão e a organização escolhida, e SHALL levar de volta à entrada.

Depois de sair, a entrada seguinte SHALL NOT vir com organização pré-selecionada, qualquer que seja o usuário que entrar.

#### Scenario: Sair volta à entrada
- **WHEN** o usuário sai
- **THEN** o aplicativo apresenta a entrada, e nenhuma tela de trabalho é alcançável sem autenticar de novo

#### Scenario: A escolha do usuário anterior não é herdada
- **WHEN** um usuário escolhe uma organização, sai, e outro usuário entra no mesmo aparelho
- **THEN** nenhuma organização vem pré-selecionada para o segundo usuário
