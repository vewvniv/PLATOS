## Why

O repositório ainda não tem implementação. A fatia 0 do roadmap (`ARQUITETURA-FINAL-v3.md` §15) existe para fixar a **forma de tenancy antes que exista qualquer tabela de domínio** — porque o único erro caro e irreversível desta arquitetura é chavear autorização por `user_id` em vez de `organization_id` (§3.2, D39). Depois que itens, provas e fatos existirem, corrigir isso vira migração de RLS com janela de inconsistência.

A assinatura entra junto e **inerte** (D40): criar `subscription` e `credit_ledger` agora, com entitlements como arquivo versionado no Git e um único ponto de leitura de direito, custa pouco e evita que a fatia 8 tenha que reescrever autorização e cobrança ao mesmo tempo.

## What Changes

**Esqueleto mínimo** (só o que a fatia 0 exige)
- Gradle raiz + `apps/api` (Ktor 3), `supabase/migrations/`, workflow de CI no GitHub Actions.
- **Não** cria `packages/domain` (KMP), `packages/contracts`, `apps/web` nem `apps/android` — pertencem à fatia 1+.

**Schema núcleo e RLS**
- Migrations Supabase para `app_user`, `organization(kind: personal|school)`, `membership(user, organization, role)`, `subscription`, `credit_ledger`.
- IDs em UUIDv7 (D11).
- RLS habilitada em todas as cinco tabelas, com política de leitura/escrita derivada exclusivamente de `membership` do usuário do JWT. `created_by_user_id` existe como metadado e **nunca** aparece em cláusula de autorização.
- Codegen jOOQ a partir do schema, com drift virando erro de compilação (§14 regra 7).

**Caminho vertical mínimo provando a tenancy**
- Autenticação Ktor validando JWT do Supabase Auth por JWKS.
- `GET /me/organizations` retornando as organizações do usuário autenticado com seu papel em cada uma.
- Provisionamento **idempotente** no servidor: no primeiro acesso autenticado, o usuário sem organização ganha `organization(kind: personal)` + `membership(role: owner)`. Sem trigger no banco.

**Assinatura inerte**
- `plans/basic.yaml` e `plans/pro.yaml` versionados no Git, conforme D40.
- Um único ponto no domínio que resolve o entitlement efetivo de uma organização (plano da `subscription` + saldo do `credit_ledger`). Nenhuma chamada bloqueia nada nesta fatia — não há consumidor de quota ainda.
- Nenhum provedor de pagamento, nenhuma concessão automática na virada de período, nenhum `hold`/capture.

**Testes**
- Testes de isolamento contra Postgres real (Testcontainers) afirmando que um membro da organização A não enxerga nem escreve linhas da organização B, por RLS e não por filtro de aplicação.
- Teste de idempotência do provisionamento sob chamadas concorrentes.

## Capabilities

### New Capabilities
- `identity`: usuários, organizações, membership N:N, papéis, isolamento por organização via RLS e resolução do contexto organizacional a partir do JWT.
- `billing`: assinatura por organização, ledger de créditos append-only e resolução de entitlement a partir de arquivos de plano versionados — sem cobrança e sem enforcement nesta fatia.

### Modified Capabilities
Nenhuma. `openspec/specs/` está vazio.

## Impact

**Criado**
- `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`
- `apps/api/` — Ktor 3, jOOQ, `kotlinx.serialization`, autenticação JWKS
- `supabase/migrations/` — schema núcleo + políticas RLS
- `plans/basic.yaml`, `plans/pro.yaml`
- `.github/workflows/ci.yml` — build, codegen jOOQ, testes com Testcontainers

**Contrato exposto**
- `GET /me/organizations` (primeiro endpoint do sistema; sem OpenAPI gerado ainda, porque não há client TypeScript nesta fatia)

**Dependências novas**: nenhuma fora do que §13 já fixa. Sem Redis, broker, vector DB, Elasticsearch, GraphQL ou microserviços.

**Explicitamente NÃO alterado nesta fatia**
- Tabelas escolares (`class_group`, `student`, `enrollment`, `subject`, `term`), currículo BNCC, banco de itens, autoria, avaliação, correção, `assessment_fact` e infra de fila (`job`, `outbox_event`, `llm_call`).
- Layout Engine, medição de texto, KMP, renderizadores — fatia 1.
- `AiGateway` e qualquer prompt — fatia 6.
- Convites de professor para organização escolar, seletor de contexto na UI, cópia de acervo entre organizações (`source_item_id`) — dependem de UI ou de banco de itens, que não existem.
- Base legal e retenção sob LGPD (§16, item aberto) — não é resolvida aqui.
