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
| `DATABASE_URL` | sim | — | JDBC do Supabase: `jdbc:postgresql://<host>:<porta>/postgres` |
| `DATABASE_PASSWORD` | sim | — | senha do papel `app_backend` |
| `DATABASE_USER` | não | `app_backend` | deixe em branco |
| `JWT_ISSUER` | sim | — | `https://<projeto>.supabase.co/auth/v1` |
| `JWKS_URL` | sim | — | `https://<projeto>.supabase.co/auth/v1/.well-known/jwks.json` |
| `JWT_AUDIENCE` | não | `authenticated` | deixe em branco |
| `PLANS_DIR` | não | `plans` | deixe em branco; a imagem já traz `plans/` |
| `PORT` | não | `8080` | **o Render injeta**; não defina à mão |

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

A segunda linha é da fatia 4a e saiu de branch **não mergeada** — `latest` aponta para código que a
PR #31 ainda não levou para a `main`. É consequência aceita de publicar por `workflow_dispatch`, e
some quando a PR fechar. `:sha-9d4f3f8` existe para voltar atrás sem reconstruir.

**Em 2026-09-08 14:50Z o serviço ainda respondia com a imagem de 09-04**: controle em 401, as duas
rotas da 4a em 404, três minutos depois do push. O elo do Render não fechou.

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

## O que este roteiro não cobre

- **Sentry**, que §13 também prevê. Não está no código ainda.
- **Migrations automatizadas.** O primeiro `apply` está descrito acima e é manual. Nada neste caminho as roda a cada deploy, e uma migration nova exige repetir o passo à mão.
- **Domínio próprio e TLS.** O Render dá um subdomínio com HTTPS, que basta para o aplicativo — o
  build exige `https://` justamente porque `targetSdk 35` recusa tráfego em claro.
