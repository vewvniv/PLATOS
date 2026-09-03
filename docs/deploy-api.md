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
Postgres efêmero, no `generateJooq` e nos testes; não há `supabase/config.toml`, e o projeto nunca
foi vinculado à CLI. No painel, *Database* aparece sem tabela nenhuma — e não é engano de quem
olha, é o estado do projeto. (O Supabase também não lista bancos: cada projeto **é** um banco
`postgres`.)

Nada nas migrations é específico do Supabase: a RLS decide por `current_setting('app.current_user_id')`,
e não por `auth.uid()`. Elas são Postgres comum, e por isso o mesmo SQL que roda nos testes serve
aqui.

### Aplicar

Pelo **SQL Editor** do painel, colando cada arquivo, **na ordem de `0001` a `0008`**. Sem instalar
nada, e é o caminho mais seguro para uma vez só. Ou, com `psql`, usando a conexão **direta** (porta
5432, não o pooler — DDL não deve passar por PgBouncer):

```bash
export PGPASSWORD='<senha do postgres>'
for m in supabase/migrations/*.sql; do
  echo "== $m"
  psql "postgresql://postgres@<host>:5432/postgres" -v ON_ERROR_STOP=1 -f "$m" || break
done
```

`ON_ERROR_STOP=1` não é enfeite: sem ele o `psql` segue depois de um erro, e o schema fica pela
metade sem que nada avise.

### Trocar a senha do `app_backend` na mesma sessão

`0002_roles.sql` cria o papel com `password 'app_backend'` — senha igual ao nome. Isso existe para
o Postgres efêmero dos testes, onde não há o que proteger. **Num banco alcançável pela internet é
exposição**, e o papel tem `LOGIN`. Troque imediatamente após aplicar, antes de qualquer outra
coisa:

```sql
alter role app_backend with login password '<senha forte>';
```

Essa é a senha que vai em `DATABASE_PASSWORD`.

### Conferir que a chave anônima não enxerga dado de domínio

As oito tabelas têm RLS habilitada **e** `force row level security`, e as migrations não concedem
nada a `anon` nem a `authenticated` — esses papéis nem são mencionados. O `force` importa porque
faz a política valer inclusive para o dono da tabela.

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

O papel é criado por `supabase/migrations/0002_roles.sql`. A **senha é sua** — defina uma no
Supabase antes do primeiro deploy:

```sql
alter role app_backend with login password '<senha forte>';
```

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
