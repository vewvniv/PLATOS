## Why

O aplicativo Android não tem autenticação nenhuma: `grep` por supabase, jwt, token, auth ou login em `apps/android/src/main` volta vazio. Ele escaneia uma folha e mostra a nota sem nunca saber de quem é o aparelho nem a que organização a prova pertence.

Isso bloqueia a fatia 4a. Puxar o `ExamPackage` exige saber de qual organização, e ADR-0013 decidiu que autenticação é fatia própria e vem antes do pull — o corte alternativo, credencial configurada fora de banda, foi rejeitado porque um critério de aceite que não exercita o caminho real adia o custo e esconde o risco.

Esta fatia é a menor coisa vertical que fecha o caminho: o aparelho autentica contra a mesma identidade que o servidor já reconhece, e mostra a organização que **veio da API**.

## What Changes

- **Login com e-mail e senha** pelo Supabase Auth, com a sessão guardada no aparelho. O contrato do servidor já existe e não muda: Bearer JWT verificado por JWKS com issuer e audience (`JwtAuth.kt`).
- **O aparelho consome `/me/organizations`** e mostra o nome da organização. O nome não é digitado nem embutido — se a rota falhar, não há de onde inventar. A rota já faz o provisionamento idempotente da organização pessoal no primeiro acesso, e esta fatia não mexe nisso.
- **Escolha de organização quando houver mais de uma**, porque `membership` é N:N por invariante. A escolha persiste entre aberturas do aplicativo.
- **Logout**, que apaga a sessão **e a organização escolhida**. Depois dele o aplicativo volta ao login, e a entrada seguinte não pré-seleciona organização nenhuma.
- **Estados de falha explicados**: credencial inválida, ausência de rede e sessão expirada são três coisas distintas na tela, com motivo. Nenhuma delas degrada em silêncio.
- **Uma tela de entrada antes do escaneamento.** A `ScanActivity` deixa de ser o ponto de partida do aplicativo.

**O que esta fatia NÃO faz**, e onde cada coisa vai:

- Não puxa pacote, não cacheia pacote, não implementa o gate de pré-voo e **não remove o `assets.open` provisório**. Os quatro são a 4a, e o asset continua alimentando a tela de escaneamento até lá.
- Não introduz Room. O cache de pacote é da 4a por arquivo, e o outbox relacional é da 4b (ADR-0013, decisão 3).
- Não cria rota nova na API. `/me/organizations` já existe e é suficiente para o critério de aceite desta fatia.
- Não implementa cadastro, recuperação de senha, convite para organização nem troca de senha. São fluxos de conta, e nenhum deles é exercitado por "o aparelho autentica e mostra a organização".
- Não toca em `packages/domain`, `apps/web` nem no pipeline de captura.
- Não muda nenhum requisito de `identity`. O aparelho vira um consumidor novo de uma rota que já existe.

## Capabilities

### New Capabilities

- `device-session`: a sessão que vive no aparelho — quem entrou, qual organização está ativa para este aparelho, o que a tela mostra em cada estado de falha, e o que sair apaga. É distinta de `identity`, que é a fronteira de autorização do servidor: `identity` decide quem pode ver o quê, e `device-session` decide o que este aparelho sabe sobre quem o está segurando.

### Modified Capabilities

Nenhuma. `identity` já especifica a consulta das organizações do usuário, e esta fatia não altera esse requisito — apenas o consome de um cliente novo.

**A 4a vai tocar `identity`, e isso não é surpresa.** O critério A2 daquela fatia — a rota de pacote só devolve pacote da organização do chamador — é fronteira de autorização, e `identity` é onde a fronteira mora. Fica registrado aqui para que a modificação chegue anunciada.

## Impact

- **`apps/android`**: dependência nova de cliente HTTP (`ktor-client`), prevista em §13 da arquitetura; `Activity` de entrada; a `ScanActivity` deixa de ser o launcher. `supabase-kt` foi avaliado e **dispensado** — a decisão 9 do `design.md` registra a medição que fechou isso, e a autenticação entra pelo mesmo cliente HTTP dos dados.
- **`gradle/libs.versions.toml`**: declaração das dependências acima.
- **Configuração**: o aplicativo passa a precisar de URL do projeto Supabase, chave anônima e URL da API. Não são segredos de servidor, e a proveniência de cada um fica escrita no `design.md`.
- **`apps/api`**: nenhuma mudança de código. A rota existente ganha um chamador.
- **ADR-0013**, decisão 1, é a fonte desta fatia; os critérios de cache no logout que ela antecipa são da 4a.
