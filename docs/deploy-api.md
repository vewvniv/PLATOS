# Deploy da API no Render

§13 da arquitetura fixa a operação: **Ktor em container no Render**, web estático em CDN, GitHub
Actions. Este documento é o roteiro, e o que ele registra é tanto o caminho quanto o motivo de ele
não ser o caminho óbvio.

## Por que o Render não constrói a partir do repositório

O fluxo natural do Render é apontar para o repositório e deixá-lo compilar. **Aqui isso não
funciona**, e falha de um jeito que não aponta para a causa.

`generateJooq` sobe um Postgres efêmero por Testcontainers, e `sourceSets.main` depende da saída
dele — as classes tipadas saem das migrations, e não de um schema mantido à mão (D-0.7). Então
compilar a API exige um daemon Docker, e o ambiente de build do Render não oferece um.

Por isso a divisão:

| Etapa | Onde | Por quê |
|---|---|---|
| Compilar, incluindo `generateJooq` | GitHub Actions | é onde há Docker, e onde o CI já roda isso verde |
| Empacotar a imagem | GitHub Actions | `apps/api/Dockerfile` só copia a distribuição pronta |
| Publicar a imagem | GHCR | privado, como o repositório |
| Rodar | Render | deploy de imagem existente, sem build |

As duas alternativas foram descartadas por contradizerem D-0.7: apontar o codegen para um banco
vivo geraria classes de um schema que não é o das migrations, e versionar as classes geradas faria
uma migration mudar sem que o build quebrasse.

## O que já existe no repositório

- `apps/api/Dockerfile` — empacota a distribuição; não compila nada.
- `.dockerignore` — nega tudo e reabre só `apps/api/build/install/api` e `plans/`.
- `.github/workflows/publicar-api.yml` — constrói e publica em `ghcr.io/<dono>/platos-api`,
  depois de o CI fechar verde no mesmo commit.

A imagem foi construída e exercitada localmente antes de existir workflow: contra um Postgres com
as oito migrations aplicadas, conectada como `app_backend`, `GET /health` respondeu `200 ok` e
`GET /me/organizations` sem token respondeu `401`. Arranque em 0,4 s, imagem de 136 MB.

## Antes de tudo: o schema ainda não existe no Supabase

As migrations em `supabase/migrations/` **nunca foram aplicadas ao projeto real**. Elas rodam em
Postgres efêmero, no `generateJooq` e nos testes. No painel, *Database* aparece sem tabela nenhuma —
e não é engano de quem olha, é o estado do projeto. (O Supabase também não lista bancos: cada
projeto **é** um banco `postgres`.)

Nada nas migrations é específico do Supabase: a RLS decide por
`current_setting('app.current_user_id')`, e não por `auth.uid()`. São Postgres comum, e por isso o
mesmo SQL que roda nos testes serve aqui.

### Aplicar, pela CLI

O repositório está configurado para a CLI: existe `supabase/config.toml`, e os arquivos usam o
formato de versão que ela espera — `20260813223817_uuid_v7.sql` e assim por diante.

```bash
supabase login
supabase link --project-ref <ref do projeto>
supabase db push
```

O `link` guarda a referência do projeto em `supabase/.temp/`, que já está no `.gitignore` do
diretório — a referência do **seu** projeto não é versionada, e `config.toml` traz só o nome local.

**Não aplique à mão se pretende usar a CLI.** O `db push` decide o que aplicar pela tabela
`supabase_migrations.schema_migrations`; um schema criado pelo SQL Editor deixa essa tabela vazia, e
o `push` seguinte tentaria recriar tudo e falharia com "already exists". Se isso acontecer, o
conserto é `supabase migration repair --status applied <versão>` para cada uma — mais trabalho do
que fazer certo da primeira vez.

### Definir a senha do `app_backend`

A migration cria o papel **sem senha**, de propósito: ela declara o que é permanente e auditável —
que o papel existe, que tem `LOGIN`, e que não tem `SUPERUSER` nem `BYPASSRLS` —, e não a
credencial. Senha literal em migration seria versionada, igual em toda instalação, e conhecida por
quem lesse o repositório.

Papel com `LOGIN` e sem senha não conecta por senha. Então um ambiente onde ninguém definiu uma
falha ao conectar, alto, em vez de ficar acessível com uma senha que qualquer um conhece. Defina:

```sql
alter role app_backend with login password '<senha forte>';
```

Essa é a senha que vai em `DATABASE_PASSWORD`. Nos testes, quem faz esse mesmo `alter` é
`PostgresSupport`, com valor sorteado a cada execução; e `MigracoesSemCredencialTest` falha o build
se alguma migration voltar a trazer senha literal.

### Conferir que a chave anônima não enxerga dado de domínio

As oito tabelas têm RLS habilitada **e** `force row level security`, e as migrations não concedem
nada a `anon` nem a `authenticated` — esses papéis nem são mencionados. O `force` importa porque faz
a política valer inclusive para o dono da tabela.

Vale conferir mesmo assim, porque o Supabase aplica privilégios padrão a tabelas novas em `public`:

```sql
set role anon;
select * from public.organization;   -- espera-se zero linhas, ou permissão negada
reset role;
```

## Variáveis de ambiente

Saem de `AppConfig.fromEnvironment`, e não de suposição. Ausente, a API **recusa subir** nomeando
qual falta — o que aparece no log do Render como `Variavel de ambiente obrigatoria ausente: X`.

| Variável | Obrigatória | Padrão | De onde sai |
|---|---|---|---|
| `DATABASE_URL` | sim | — | JDBC do **Session Pooler**: `jdbc:postgresql://<regiao>.pooler.supabase.com:5432/postgres` — ver abaixo |
| `DATABASE_PASSWORD` | sim | — | senha do papel `app_backend` |
| `DATABASE_USER` | **na prática sim** | `app_backend` | `app_backend.<ref-do-projeto>` — **não** deixe em branco; ver abaixo |
| `JWT_ISSUER` | sim | — | `https://<projeto>.supabase.co/auth/v1` |
| `JWKS_URL` | sim | — | `https://<projeto>.supabase.co/auth/v1/.well-known/jwks.json` |
| `JWT_AUDIENCE` | não | `authenticated` | deixe em branco |
| `PLANS_DIR` | não | `plans` | deixe em branco; a imagem já traz `plans/` |
| `PORT` | não | `8080` | **o Render injeta**; não defina à mão |

### Use o Session Pooler, e preencha `DATABASE_USER`

**A conexão direta (`db.<ref>.supabase.co`) é IPv6-only.** O Supabase deixou de dar IPv4 a ela sem
o add-on pago, e ambientes de execução que não roteiam IPv6 — o Render entre eles — simplesmente
não a alcançam. O sintoma é tempo esgotado na conexão, não erro de credencial, o que manda quem
depura para o lado errado. Por isso a URL da tabela é a do **Session Pooler**, na porta **5432**.

E o pooler traz uma consequência que o padrão do código não cobre: **ele exige o usuário com o
sufixo do projeto**, `app_backend.<ref-do-projeto>`. `AppConfig.fromEnvironment` faz
`DATABASE_USER` cair em `app_backend` puro quando a variável está ausente (`AppConfig.kt:35`), e
contra o pooler isso falha na autenticação. A linha da tabela dizia "deixe em branco" e estava
errada desde que o pooler passou a ser o caminho; um serviço criado seguindo aquela instrução sobe,
responde `/health` com `ok` e devolve 401 nas rotas autenticadas — e **só quebra na primeira
consulta ao banco**, porque `/health` não toca o banco e o 401 vem antes de qualquer query.

Porta **5432** é modo *session*; **6543** é *transaction*. A ressalva do PgBouncer com prepared
statements do JDBC, mais abaixo, vale para 6543 — em 5432 ela não se aplica.

### `app_backend`, e por que não `postgres`

A API conecta como `app_backend`, papel sem `SUPERUSER` e sem `BYPASSRLS` (D-0.2). Conectar como
superusuário desligaria RLS em silêncio, e `ConnectionRoleTest` falha o build justamente para
impedir que isso passe.

O papel é criado pela migration `..._roles.sql`, e a senha é definida por você — ver acima.

## Roteiro

### 1. Publicar a primeira imagem

Mergeie esta mudança na `main`. O CI roda; quando fechar verde, o workflow de publicação dispara
sozinho e a imagem aparece em **Packages**, no GitHub, como `platos-api`. Para republicar sem
commit novo: aba Actions → *Publicar imagem da API* → *Run workflow*.

### 2. Dar ao Render acesso ao registro

O repositório é privado, então a imagem também é. **Não a torne pública** — ela carrega o código
compilado.

Crie um Personal Access Token clássico no GitHub com o escopo **`read:packages`**, e só ele. No
Render, em *Settings → Registry Credentials*, adicione uma credencial do tipo GitHub Container
Registry com o seu usuário e esse token.

### 3. Criar o serviço

*New → Web Service → Deploy an existing image from a registry.*

- **Image URL:** `ghcr.io/vewvniv/platos-api:latest`
- **Credential:** a que você acabou de criar
- **Health check path:** `/health`
- **Region:** a mais próxima do seu projeto Supabase
- **Instance type:** ver a nota sobre hibernação abaixo

Cole as variáveis da tabela acima. Não defina `PORT`.

### 4. Conferir

```
curl https://<seu-servico>.onrender.com/health
```

Deve responder `ok`. Se responder qualquer outra coisa, o log do Render diz o quê — a API recusa
subir com configuração faltando, em vez de subir quebrada.

Depois:

```
curl -i https://<seu-servico>.onrender.com/me/organizations
```

Deve responder **401**. Se responder 200 sem token, pare tudo: a autenticação não está ativa.

### 5. Apontar o aplicativo

Em `local.properties`:

```
platos.apiUrl=https://<seu-servico>.onrender.com
```

Sem barra no fim — o build recusa e explica, mas é mais rápido acertar de primeira.

## Publicar imagem nova NÃO redeploya o Render

O roteiro acima cria o serviço e para por aí, e isso escondia um elo. `latest` mudar no GHCR **não
muda o que está rodando**: o serviço é do tipo *deploy an existing image from a registry*, e ele não
fica observando a tag. O workflow fecha verde, a imagem nova está no registro, e o Render continua
servindo a anterior — sem erro em lugar nenhum.

É o modo de falha que este documento existe para nomear, porque ele **não tem sintoma**: quem olhar
o Actions vê verde, quem olhar o GHCR vê a imagem, e quem chamar a API recebe respostas coerentes —
só que da versão velha.

**Workflow verde não é evidência de que o deploy chegou.** A evidência é bater numa rota que só
existe na versão nova e ver a resposta mudar de forma. O par que separa as duas coisas:

```
# controle: rota que já existia. Prova que o serviço está no ar e a auth ativa.
curl -s -o /dev/null -w "%{http_code}
" https://<seu-servico>.onrender.com/me/organizations

# alvo: rota da versão nova, sem token.
curl -s -o /dev/null -w "%{http_code}
"   https://<seu-servico>.onrender.com/organizations/<uuid>/exams
```

Controle **401** com alvo **404** é o estado "imagem publicada, Render não puxou". Sem o controle,
o 404 do alvo é indistinguível de serviço fora do ar. Os dois em 401 é o deploy no ar.

### A partir de 2026-09-11, a pergunta tem resposta direta

`/health` declara qual build está servindo, no cabeçalho `X-Platos-Build`:

```bash
curl -s -D - -o /dev/null https://<seu-servico>.onrender.com/health | grep -i x-platos-build
# X-Platos-Build: sha-cdd12e8
```

Compare com a tag publicada no GHCR. Iguais: o que está servindo é o que foi publicado. Diferentes:
o Render não puxou, e a tag imutável `sha-<curto>` diz exatamente qual imagem ele ainda serve.

**`curl -I` NÃO serve, e isto foi medido:** `-I` manda `HEAD`, a rota só responde `GET`, e o
resultado é **405 Method Not Allowed** com o cabeçalho invisível. Use `-D -` como acima.

**`X-Platos-Build: desconhecido`** é resposta legítima, e significa que a imagem foi construída fora
do caminho de publicação — `docker build` à mão, sem `--build-arg`. Ausência dita como ausência; o
que a resposta nunca traz é valor inventado.

O par de rotas acima continua valendo para o que ele sempre mediu: que o serviço está no ar e com
autenticação ativa. O que ele **não** distingue são duas imagens do mesmo repositório — e é isso que
o cabeçalho resolve.

Para o Render puxar: *Manual Deploy → Deploy latest reference* no painel, ou um **Deploy Hook**
(*Settings → Deploy Hook*) chamado como último passo de `publicar-api.yml`. O hook fecha o elo e
tira a etapa manual; enquanto ele não existir, publicar é duas ações e não uma.

## Estado publicado

O que está no registro, e quando foi. Não é histórico completo — é a última publicação afirmada,
para que "a imagem é velha" seja uma afirmação conferível em vez de uma suposição.

| Quando | Tag | Commit | Origem |
|---|---|---|---|
| 2026-09-04 13:09Z | `ghcr.io/vewvniv/platos-api:latest` | `09f200b` (`main`) | `workflow_run` após CI |
| 2026-09-08 14:47Z | `ghcr.io/vewvniv/platos-api:latest` e `:sha-9d4f3f8` | `9d4f3f8` (`vewvniv/slice-4a-pull-de-pacote`) | `workflow_dispatch` |
| 2026-09-10 17:36Z | `ghcr.io/vewvniv/platos-api:latest` e `:sha-771bdbd` | `771bdbd0` (`main`) | `workflow_run` após CI |
| 2026-09-11 16:47Z | `ghcr.io/vewvniv/platos-api:latest` e `:sha-cdd12e8` | `cdd12e8` (`main`) | `workflow_run` após CI |

A segunda linha é da fatia 4a e saiu de branch **não mergeada** — `latest` aponta para código que a
PR #31 ainda não levou para a `main`. É consequência aceita de publicar por `workflow_dispatch`, e
some quando a PR fechar. `:sha-9d4f3f8` existe para voltar atrás sem reconstruir.

**Resolvida em 2026-09-10:** a PR #31 fechou às 17:25:16Z (merge commit `771bdbd0`), o `main` passou a
conter aquele código, e a terceira linha da tabela é a publicação que veio dele pelo caminho normal —
`workflow_run` após o CI, sem disparo manual. A nota fica onde está em vez de ser apagada: quem ler o
histórico precisa saber que houve um período em que `latest` apontava para branch não mergeada, e
quanto ele durou (dois dias).

**O elo do Render foi fechado à mão em 2026-09-08, e o par mostrou as duas metades.** Às 14:50Z,
três minutos depois do push, o serviço ainda respondia com a imagem de 09-04: controle em 401 e as
duas rotas da 4a em 404. Depois de *Manual Deploy → Deploy latest reference* no painel, às 16:13Z:
controle em 401 e as duas rotas da 4a **em 401** — mesma forma do controle, que é o que caracteriza
a versão nova no ar.

O painel mostrou `Source: 6b72e80` para esse deploy. **Não é commit deste repositório** (`git
cat-file -t` recusa, e nenhum commit começa com isso) — é identificador do lado do Render, e não
serve para conferir qual código subiu. Quem confere isso é o par de rotas.

### O deploy de 2026-09-11, e o que nele é relatado em vez de conferido

A publicação da linha nova veio pelo caminho normal: a fatia 4b entrou na `main` pelas PRs #34, #32,
#33 e #35, o CI de `cdd12e8` fechou verde, e o `publicar-api.yml` construiu e empurrou as duas tags
no **mesmo digest** — `sha256:84cd51b7a41fa48a704a4dd29572592e89ad93a171c4f566a86051dfb3fe5328`, às
16:47:48Z.

**Dois `Manual Deploy → Deploy latest reference` foram disparados no painel, às ~18:43Z e ~18:46Z, e
isso é RELATADO pelo mantenedor — não conferido por medição.** A distinção não é formalidade: quem
observou o painel foi ele, e o registro diz de quem é a evidência.

**O que a sessão mediu, e é menos do que parece:** o serviço respondeu depois dos dois deploys —
`/health` 200 com corpo `ok`, `/me/organizations` 401, `/organizations/<uuid>/exams` 401, tudo abaixo
de um segundo e sem cold start, medido às 18:43:08Z e às 18:46:24Z. Isso prova que o serviço está no
ar com autenticação ativa e voltou dos reinícios sem quebrar. **Não prova qual digest está rodando.**

**E aqui o par de rotas não serve, por uma razão nova — pior que a de 2026-09-10.** Naquele dia as
duas imagens candidatas eram funcionalmente idênticas porque `apps/api` diferia em um arquivo de
teste. Agora **o código de produção mudou de verdade** (`ExamPublication.kt`), e a imagem nova
continua **indistinguível por HTTP**: conferido rota por rota, não há rota de publicação em
`Routes.kt`, a listagem lê do banco, a entrega do pacote serve os bytes do banco sem reserializar, e
`/health` responde a string `"ok"`. Repetir o deploy e repetir a sonda dá o mesmo resultado — a sonda
é **estruturalmente cega** para esta distinção, e isso ficou visível quando o segundo deploy não
mudou nada.

Conclusão para quem ler depois: **publicado e implantado; "servindo o digest `84cd51b7…`" é relatado,
não medido.** Os dois consertos já nomeados acima seguem sendo o que fecha isso — `/health` carregando
o `sha-<curto>` da imagem, e o Deploy Hook.

**Corrigido em 2026-09-11, e o parágrafo acima fica como está (P7):** o primeiro dos dois consertos
**foi feito**. `/health` passou a declarar o build no cabeçalho `X-Platos-Build`, e a conferência está
na seção "A partir de 2026-09-11, a pergunta tem resposta direta". Então "o terceiro elo é
inobservável de fora" descreve o estado **até** esta data, e não o estado corrente — quem ler o
trecho antigo sem esta nota repetiria a conclusão errada. **O que continua aberto é o Deploy Hook:**
publicar ainda não redeploya, e o elo entre publicação e deploy segue dependendo de alguém disparar.

### A reconciliação de 2026-09-10, e o par de rotas cego

O merge da PR #31 publicou pelo caminho normal: run `34508945084`, evento `workflow_run`,
`head_sha 771bdbd07eb…`, e **as duas tags no mesmo digest** —
`sha256:45a8b4ca5905a481055aff055e29d02edb1ec4d72b2ce15ba0a919aaf079c8b7`. Isso fecha os elos
"construído do commit certo" e "`latest` aponta para ele".

**O par de rotas não fecha o terceiro elo desta vez, e a razão está medida.** Entre o commit da
imagem que já servia (`9d4f3f8`) e o commit mesclado (`771bdbd0`), `git diff --name-only … --
apps/api` devolve **dois** arquivos: `apps/api/build.gradle.kts` e
`apps/api/src/test/kotlin/com/platos/api/exam/PublicarFixturesNoBancoRealTest.kt`. **Nenhuma linha de
`src/main`.** As duas imagens candidatas têm comportamento idêntico, então nenhuma sonda de rota as
distingue: o par separa `main` de `4a`, e não `4a-de-08/09` de `4a-mesclada`.

Medido às 17:53Z: `/health` **200 em 0,170 s** com corpo `ok`, e `/organizations/<uuid>/exams` sem
token **401 em 0,142 s**. O serviço **está no ar e servindo uma imagem com as rotas da 4a** — o que já
era verdade antes do merge. Os cabeçalhos não ajudam: `rndr-id` muda a cada requisição, e nenhum
carrega identidade de build.

**Estado honesto: publicado, e não confirmado servindo o commit mesclado.** Não é hedge — é a
distinção que este documento existe para preservar, e ela não é fechável de fora com o que existe
hoje. As duas formas de fechar:

1. **O digest que o Render puxou**, pelo painel ou pela API de deploys do serviço, comparado com
   `sha256:45a8b4ca5905…`. É o caminho mais curto, e não exige mudar código.
2. **`/health` devolvendo o `sha-<curto>` da imagem** — o conserto durável, porque tira a conferência
   do painel e a põe na própria API. Junto com o **Deploy Hook** já nomeado acima, fecha os dois elos
   que hoje dependem de alguém lembrar.

Enquanto nenhuma das duas existir, a regra é a que a tabela acima implica: **disparo manual ou
publicação nova nascem com data de reconciliação escrita**, e "publicado" nunca se lê como "no ar".

## Duas coisas para decidir com os olhos abertos

**Hibernação.** O plano gratuito do Render suspende o serviço depois de ~15 minutos sem tráfego, e
a primeira chamada seguinte espera o container subir. Para conferir a fatia 4a-zero em aparelho,
tanto faz. Para um professor abrindo o aplicativo em sala, é a diferença entre funcionar e parecer
quebrado. A decisão pode ser adiada, mas não deve ser adiada **sem querer**.

**O pooler do Supabase.** Se você usar a porta do pooler em modo transaction, o driver JDBC com
prepared statements costuma esbarrar no PgBouncer, e o sintoma é erro de statement já preparado, e
não erro de conexão. Não é certeza — é o primeiro suspeito se a conexão falhar de forma estranha.
A conexão direta não tem esse problema, e tem outro: limite menor de conexões simultâneas.
`maxPoolSize` está em 10 (`DatabaseConfig`).

## Notas operacionais

Fatos observados que não têm explicação fechada, registrados porque saber que já aconteceram uma
vez vale mais do que a explicação que falta.

**2026-09-08, ~17:50–18:20Z: degradação transitória do projeto Supabase, sem causa local
identificada.** Os três serviços que compartilham o banco falharam juntos e voltaram sozinhos:

| Alvo | Na degradação | Depois |
|---|---|---|
| `rest/v1/` | 401 em **18,9 s** | 401 em 0,24 s |
| `auth/v1/health` | **000** — sem resposta HTTP | 200 em 0,16 s |
| Session Pooler | conexão devolvida **já fechada** | conecta normalmente |

Do lado do pooler o sintoma **não diz "banco"**: chega como
`HikariPool$PoolInitializationException: Failed to initialize pool: This connection has been
closed.`, com a causa em `PgConnection.setTransactionIsolation` — o Hikari falhando ao detectar o
nível de isolamento numa conexão que já veio morta. Quem ler só essa linha procura defeito no
cliente.

Duas hipóteses foram levantadas e **nenhuma se confirmou**. Esgotamento de conexões era a mais
plausível — o serviço no Render segura até dez (`maxPoolSize`) e as ferramentas locais abriam outras
dez —, mas os logs de Supavisor e Auth da janela não mostram erro de conexão nenhum, mostram
abertura e `shutting down gracefully` emparelhados em cada ciclo, e ficam **em silêncio total** por
~30 min. Vazamento apareceria como abertura sem fechamento correspondente, e não apareceu. A
recuperação foi espontânea, sem nenhuma ação. Fica sem causa.

O que isto muda na prática: **antes de depurar o cliente, bata em `rest/v1/` e `auth/v1/health`.**
Se os dois estiverem lentos ou mudos, o problema não é seu código. E ao interpretar a janela, note
que a ausência de linhas no log é ambígua — pode ser normalidade ou pode ser que as tentativas nem
estejam chegando; só um probe ao vivo decide.

## O que este roteiro não cobre

- **Sentry**, que §13 também prevê. Não está no código ainda.
- **Migrations automatizadas.** O primeiro `apply` está descrito acima e é manual. Nada neste caminho as roda a cada deploy, e uma migration nova exige repetir o passo à mão.
- **Domínio próprio e TLS.** O Render dá um subdomínio com HTTPS, que basta para o aplicativo — o
  build exige `https://` justamente porque `targetSdk 35` recusa tráfego em claro.
