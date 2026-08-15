## Context

Ver `proposal.md — Why`. Restrições que moldam o desenho:

- Não existe código no repositório. Tudo que esta fatia criar vira precedente para as fatias 1–9.
- `ARQUITETURA-FINAL-v3.md` §13 fixa Ktor 3 + jOOQ + Postgres/Supabase com RLS; §3.2 e D39 fixam `organization_id` como única chave de autorização; D11 fixa UUIDv7; D40 fixa entitlements em arquivo versionado.
- Requisitos desta fatia: `specs/identity/spec.md` e `specs/billing/spec.md`.
- A decisão central não é o schema — é **onde o isolamento é imposto**. A spec de identity exige que uma consulta sem filtro de organização na aplicação já devolva apenas linhas permitidas. Isso força RLS real, e RLS real força escolhas sobre conexão, papel de banco e propagação de contexto que são caras de reverter depois.

## Goals / Non-Goals

**Goals**

- Impossibilitar, por construção, que uma futura tabela de domínio vaze entre organizações por esquecimento de `WHERE`.
- Deixar o caminho de autenticação → contexto → consulta pronto e testado, para que fatias seguintes só acrescentem tabelas e políticas.
- Ter uma verificação de tenancy que roda em CI contra Postgres real, não contra mock.
- Dar à assinatura a forma final (organização + ledger append-only + entitlement versionado) sem construir cobrança.

**Non-Goals**

- Desempenho. Nenhuma cache de sessão, nenhuma otimização de round-trip nesta fatia.
- `packages/domain` em KMP. O domínio desta fatia vive dentro de `apps/api` e migra quando a fatia 1 criar o módulo KMP por necessidade real.
- Contrato OpenAPI e tipos gerados para TypeScript — não há client web nesta fatia.
- Convite de membros, troca de papel, remoção de vínculo. A spec descreve o comportamento do vínculo; a fatia só cria o vínculo pessoal.

## Decisions

### D-0.1 — Isolamento por RLS lida de uma variável de sessão, não por filtro de aplicação

Cada requisição autenticada abre uma transação e emite `SET LOCAL app.current_user_id = <uuid>` antes de qualquer consulta. As políticas RLS leem `current_setting('app.current_user_id', true)::uuid` e derivam permissão de `membership`.

`SET LOCAL` expira no commit/rollback, então contexto não vaza entre checkouts do pool.

*Alternativas descartadas:* PostgREST com `auth.uid()` — não se aplica, o servidor é Ktor com pool próprio. Filtro por `organization_id` escrito em cada repositório — é exatamente o erro que §3.2 chama de único erro caro; a spec de identity o proíbe explicitamente.

### D-0.2 — Papel de banco dedicado, sem `SUPERUSER` e sem `BYPASSRLS`

A API conecta como `app_backend`, criado por migration com `NOSUPERUSER NOBYPASSRLS` e `GRANT` mínimo por tabela. Conectar como `postgres` faria toda política RLS ser silenciosamente ignorada — a falha mais perigosa possível aqui, porque os testes passariam se rodassem como superusuário.

Um teste afirma que o papel de conexão usado nos testes não é superusuário nem tem `bypassrls`. Sem essa asserção, toda a suíte de isolamento é decorativa.

### D-0.3 — Nenhum `DSLContext` ambiente

O único jeito de obter um `DSLContext` é `Tenancy.asUser(userId) { ctx -> ... }`, que abre a transação e emite o `SET LOCAL`. Não existe `DSLContext` injetável em handler. Esquecer o contexto vira erro de compilação, não vazamento.

Um teste emite uma consulta fora de `asUser` e afirma zero linhas, confirmando que o *default deny* funciona mesmo se alguém contornar a API do módulo.

### D-0.4 — Bootstrap de identidade por função `SECURITY DEFINER` chamada explicitamente pelo Kotlin

RLS não consegue autorizar a criação da primeira organização de um usuário: no instante do `INSERT` ele ainda não tem vínculo nenhum. As saídas possíveis eram: (a) política de `INSERT` permitindo auto-vínculo em organização sem membros, (b) `created_by_user_id` como chave de bootstrap, (c) função `SECURITY DEFINER`.

(a) deixa reivindicável qualquer organização que fique sem membros. (b) viola §3.2 diretamente — `created_by_user_id` é metadado, jamais chave de autorização.

Escolhido (c): `bootstrap_identity(p_auth_subject, p_email, p_display_name) RETURNS uuid`, `SECURITY DEFINER`, idempotente, que faz upsert de `app_user` por `auth_subject` e, **somente se o usuário não tiver nenhum vínculo**, cria `organization(kind='personal')` + `membership(role='owner')`. Retorna o `app_user.id`.

Isso não é trigger escondida: é chamada explícita, uma por requisição autenticada, com o *quando* decidido em Kotlin e testável. É a única função privilegiada do sistema; toda escrita de domínio permanece sob RLS.

Concorrência: `pg_advisory_xact_lock` derivado do `auth_subject` no início da função, mais unicidade em `app_user(auth_subject)` e em `membership(user_id, organization_id)`. Satisfaz o cenário de acessos simultâneos sem vazar violação de restrição ao cliente.

### D-0.5 — `app_user.id` próprio (UUIDv7), separado do `sub` do provedor

`app_user` guarda `auth_subject` (o `sub` do Supabase Auth, único) e tem `id` UUIDv7 próprio, que é o que aparece em `membership` e no `SET LOCAL`. Trocar de provedor de autenticação, ou existir um usuário sem conta no provedor, não obriga a reescrever chave estrangeira nenhuma.

### D-0.6 — UUIDv7 gerado no banco nesta fatia

Migration define `uuid_generate_v7()` e as colunas `id` usam-na como `DEFAULT`. Nesta fatia toda linha nasce no servidor, então um gerador só é suficiente e não há risco de divergência. Quando o Android precisar gerar IDs offline (fatia 4), o gerador Kotlin/KMP entra como decisão daquela fatia — e só então existirão dois, com teste de formato comum.

### D-0.7 — jOOQ gerado contra um Postgres efêmero que rodou as migrations

Task Gradle sobe um container Postgres, aplica `supabase/migrations/*.sql` em ordem, roda o codegen jOOQ, derruba o container. Nada de apontar codegen para um banco vivo ou versionar SQL de schema à mão em dois lugares. Migration e código tipado não podem divergir sem quebrar o build (§14 regra 7).

Supabase CLI continua sendo a ferramenta de autoria e aplicação das migrations; o container efêmero só existe para o codegen e para os testes.

### D-0.8 — Entitlements em YAML, resolvidos em um único ponto

`plans/basic.yaml` e `plans/pro.yaml` são carregados e validados na inicialização; arquivo ausente ou inválido derruba o processo, satisfazendo o cenário de plano desconhecido. `EntitlementResolver.resolve(organizationId)` é o único ponto que interpreta plano, saldo e período — todo consumidor futuro passa por ele.

*Nova dependência:* `kaml`, formato YAML para `kotlinx.serialization`, que §13 já adota. Justificativa de custo operacional: biblioteca pura em Kotlin, sem serviço, sem processo, sem configuração; a alternativa (JSON) evitaria a dependência mas contraria D40, que nomeia `plans/*.yaml`, e YAML é o formato que um humano vai editar para mudar quota.

### D-0.9 — `credit_ledger` imutável por privilégio, não por convenção

`REVOKE UPDATE, DELETE` no papel `app_backend` para `credit_ledger`. Append-only que depende de disciplina de código não é append-only. `assessment_fact` e `answer_observation` herdarão o mesmo padrão nas fatias 5 e 9 — este é o precedente.

### D-0.10 — Superfície HTTP mínima

Um endpoint: `GET /me/organizations`. Ele existe para provar o caminho autenticação → bootstrap → contexto → RLS → resposta com dado real. Sem versionamento de rota, sem OpenAPI, sem paginação — acrescentar isso agora seria abstração prematura (CLAUDE.md regra 8).

### Arquivos e módulos afetados

| Camada | Caminho | Conteúdo |
|---|---|---|
| Build | `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml` | Raiz do monorepo, catálogo de versões, task de codegen |
| Infra/DB | `supabase/migrations/*.sql` | `uuid_generate_v7`, tabelas, políticas RLS, papel `app_backend`, `bootstrap_identity` |
| Infra/API | `apps/api/.../db/Tenancy.kt`, `.../db/DataSourceFactory.kt` | Transação + `SET LOCAL`, pool |
| Contrato | `apps/api/.../http/dto/*.kt` | DTOs `kotlinx.serialization` da resposta |
| Domínio | `apps/api/.../identity/*.kt`, `.../billing/EntitlementResolver.kt` | Bootstrap, consulta de organizações, resolução de direito |
| Config | `plans/basic.yaml`, `plans/pro.yaml` | Entitlements versionados |
| CI | `.github/workflows/ci.yml` | Build, codegen, testes |

Sem impacto em KMP (não existe) nem em consumidores (não existem).

## Risks / Trade-offs

- **Política RLS escrita errada deixa passar tudo, e o teste feliz continua verde** → Os testes de isolamento são escritos como cenários negativos explícitos (usuário de A tentando ler e escrever em B), rodam contra Postgres real em CI e são bloqueantes. Somado a D-0.2, que impede o falso verde por superusuário.
- **Uma chamada `bootstrap_identity` por requisição é um round-trip a mais** → Aceito nesta fatia. É upsert idempotente e indexado; cache de sessão é otimização com invalidação a projetar, e a fatia 0 não tem carga para justificá-la.
- **`SECURITY DEFINER` é privilégio ampliado no banco** → Contido a uma única função, sem parâmetro que escolha organização, com `search_path` fixado e escopo restrito ao próprio `auth_subject` recebido. Revisar essa função é revisar toda a superfície privilegiada do sistema.
- **O domínio nasce dentro de `apps/api` e vai ter que se mudar para KMP** → Trade-off deliberado. Criar `packages/domain` agora, sem segundo consumidor, é a abstração prematura que a regra 8 proíbe; a mudança futura é mecânica e pequena.
- **Assinatura inerte pode ficar inerte tempo demais e apodrecer** → Os cenários de "nenhum enforcement" em `specs/billing` são testados, então a inércia é comportamento verificado, não código morto silencioso.

## Migration Plan

Não há dados nem ambiente de produção. As migrations são forward-only pelo Supabase CLI; reverter esta fatia é dropar as cinco tabelas, o papel e a função, sem perda. Nenhuma compatibilidade a preservar.

Ordem de aplicação: papel e função utilitária → tabelas → índices e restrições → políticas RLS e `GRANT`/`REVOKE` → `bootstrap_identity`. As políticas vêm depois das tabelas por dependência, e os `REVOKE` de `credit_ledger` no mesmo passo dos `GRANT` para não haver janela com escrita permitida.

## Open Questions

- Nome padrão da organização pessoal. Assumido: `display_name` do token quando presente, senão a parte local do e-mail. Puramente cosmético; mudar não altera spec, desenho nem tarefas.
- Retenção e base legal LGPD para `app_user.email` (§16, item aberto na arquitetura). Nesta fatia guarda-se o mínimo — `auth_subject`, `email`, `display_name`. A política precisa existir antes do primeiro contrato com escola, não antes deste merge.
