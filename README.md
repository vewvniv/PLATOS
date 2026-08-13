# PLATOS

Plataforma de Avaliação Educacional com IA.

Referência arquitetural única: [`docs/architecture/ARQUITETURA-FINAL-v3.md`](docs/architecture/ARQUITETURA-FINAL-v3.md).
Comportamento atual: `openspec/specs/`. Mudanças em andamento: `openspec/changes/`.

## Pré-requisitos

- JDK 17
- Docker (o codegen jOOQ e os testes sobem Postgres efêmero)
- [Supabase CLI](https://supabase.com/docs/guides/local-development) para autorar e aplicar migrations

## Estrutura

```
apps/api/            API Ktor 3
supabase/migrations/ schema e políticas RLS (fonte de verdade do banco)
plans/               entitlements versionados
buildSrc/            task de codegen jOOQ
```

## Rodar

```bash
cp .env.example .env    # preencha as credenciais
./gradlew build         # compila, gera jOOQ a partir das migrations e roda os testes
./gradlew :apps:api:run # sobe a API em http://localhost:8080
```

O codegen jOOQ sobe um Postgres efêmero, aplica `supabase/migrations/*.sql` em ordem e gera as
classes tipadas. Divergência entre migration e código vira erro de compilação, não bug em produção.

## Testes

```bash
./gradlew test
```

Os testes de isolamento rodam contra Postgres real via Testcontainers, conectados como `app_backend`
— papel sem `SUPERUSER` e sem `BYPASSRLS`. Rodar como superusuário desligaria RLS silenciosamente e
deixaria a suíte verde sem provar nada.

## Endpoints

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/health` | Liveness |
| `GET` | `/me/organizations` | Organizações do usuário do token, com seu papel em cada uma |
