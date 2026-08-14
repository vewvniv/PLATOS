## 1. Esqueleto do monorepo

- [x] 1.1 Criar `settings.gradle.kts`, `build.gradle.kts` da raiz e `gradle/libs.versions.toml` com Kotlin, Ktor 3, jOOQ, `kotlinx.serialization`, kaml, HikariCP, Testcontainers e JUnit 5. Resultado: `./gradlew build` passa em projeto vazio.
- [x] 1.2 Criar o módulo `apps/api` com `Application.kt` servindo `GET /health` e teste que afirma `200`. Resultado: `./gradlew :apps:api:test` verde.
- [x] 1.3 Criar `.gitignore`, `.env.example` (URL do Supabase, credenciais de `app_backend`, issuer JWKS) e `README` curto de como rodar. Resultado: nenhum segredo versionado.

## 2. Schema núcleo e RLS (migrations)

- [x] 2.1 Migration `0001_uuid_v7.sql`: função `uuid_generate_v7()` conforme D-0.6. Resultado: `SELECT uuid_generate_v7()` devolve UUID com versão 7.
- [x] 2.2 Migration `0002_role_app_backend.sql`: papel `app_backend` com `NOSUPERUSER NOBYPASSRLS` e `GRANT CONNECT`. Resultado: `rolsuper` e `rolbypassrls` falsos em `pg_roles`.
- [x] 2.3 Migration `0003_identity_tables.sql`: `app_user` (`id` UUIDv7, `auth_subject` único, `email`, `display_name`), `organization` (`id`, `kind` em `personal|school`, `name`, `created_by_user_id` como metadado), `membership` (`id`, `user_id`, `organization_id`, `role` em `owner|admin|teacher`, único por par usuário/organização). Resultado: migrations aplicam limpas e as restrições de unicidade e de domínio de valor rejeitam o inválido.
- [x] 2.4 Migration `0004_billing_tables.sql`: `subscription` (organização, plano, periodicidade, estado, início/fim do período, com índice único parcial garantindo no máximo uma assinatura não encerrada por organização) e `credit_ledger` (organização, tipo, quantidade com sinal, motivo, instante). Resultado: segunda assinatura ativa na mesma organização é rejeitada pelo banco.
- [x] 2.5 Migration `0005_rls_policies.sql`: `ENABLE ROW LEVEL SECURITY` e `FORCE` nas cinco tabelas; políticas de `SELECT`/`INSERT`/`UPDATE`/`DELETE` derivadas de `membership` sobre `current_setting('app.current_user_id', true)::uuid`; `GRANT` mínimo a `app_backend`; `REVOKE UPDATE, DELETE ON credit_ledger` conforme D-0.9. Resultado: nenhuma política referencia `created_by_user_id`.
- [x] 2.6 Migration `0006_bootstrap_identity.sql`: função `bootstrap_identity(p_auth_subject, p_email, p_display_name) RETURNS uuid`, `SECURITY DEFINER`, `search_path` fixado, com `pg_advisory_xact_lock` e criação de organização pessoal apenas quando o usuário não tem nenhum vínculo (D-0.4). Resultado: chamadas repetidas devolvem o mesmo `app_user.id` e criam no máximo uma organização pessoal.

## 3. Codegen jOOQ

- [x] 3.1 Configurar a task Gradle que sobe Postgres efêmero, aplica `supabase/migrations/*.sql` em ordem, roda o codegen jOOQ e derruba o container (D-0.7). Resultado: classes geradas para as cinco tabelas compilam.
- [x] 3.2 Verificar o gate de drift adicionando uma coluna só em migration e confirmando que o código que ignora a mudança ainda compila mas que o código tipado a enxerga; reverter em seguida. Resultado: divergência schema/código é detectável no build.

## 4. Contexto de tenancy na API

- [x] 4.1 Implementar `DataSourceFactory` conectando como `app_backend` via Hikari. Resultado: aplicação sobe conectada com papel não privilegiado.
- [x] 4.2 Implementar `Tenancy.asUser(userId) { ctx -> ... }` abrindo transação e emitindo `SET LOCAL app.current_user_id`, sem expor `DSLContext` fora dele (D-0.3). Resultado: não existe forma pública de obter `DSLContext` sem contexto.
- [x] 4.3 Teste com Testcontainers afirmando que o papel de conexão dos testes não é superusuário e não tem `bypassrls` (D-0.2). Resultado: falso verde por superusuário fica impossível.
- [x] 4.4 Testes de isolamento cobrindo os cenários de `specs/identity/spec.md` — "Leitura cruzada entre organizações", "Escrita em organização alheia", "Autoria não concede acesso", "Usuário sem vínculo" — mais uma consulta emitida fora de `asUser` afirmando zero linhas. Resultado: todos verdes contra Postgres real.

## 5. Autenticação

- [x] 5.1 Configurar autenticação JWT no Ktor validando por JWKS do Supabase, com issuer e audience de configuração. Resultado: rota protegida responde `200` com token válido.
- [x] 5.2 Testes cobrindo os cenários de "Identidade do usuário derivada do provedor de autenticação": token ausente, expirado, malformado e assinado por chave desconhecida respondem `401` sem tocar o banco. Resultado: quatro cenários verdes.
- [x] 5.3 Teste do cenário "Identidade forjada no payload": requisição do usuário A informando identificador de B em cabeçalho, query e corpo atua como A. Resultado: verde.

## 6. Bootstrap de identidade

- [x] 6.1 Implementar a chamada Kotlin a `bootstrap_identity` no pipeline autenticado, resolvendo `app_user.id` antes de abrir o contexto de tenancy. Resultado: primeiro acesso cria organização pessoal com papel `owner`.
- [x] 6.2 Testes dos cenários de "Provisionamento idempotente da organização pessoal": primeiro acesso, acessos subsequentes, acessos concorrentes do mesmo usuário novo, e usuário que só pertence a organização escolar. Resultado: quatro cenários verdes, incluindo o concorrente sem erro vazado ao cliente.
- [x] 6.3 Testes dos cenários de "Pertencimento N:N": usuário em duas organizações, acervo pessoal preservado ao entrar em escola, vínculo duplicado rejeitado e papel inválido rejeitado. Resultado: quatro cenários verdes.

## 7. Endpoint `GET /me/organizations`

- [x] 7.1 Definir os DTOs de resposta em `kotlinx.serialization` (identificador, nome, tipo, papel). Resultado: contrato serializado estável.
- [x] 7.2 Implementar a consulta dentro de `asUser`, sem filtro de organização escrito na aplicação, e expor a rota autenticada. Resultado: resposta traz as organizações do usuário do token.
- [x] 7.3 Testes dos cenários de "Consulta das organizações do usuário": usuário recém-criado, usuário com múltiplos vínculos, organizações de terceiros não vazam e requisição não autenticada. Resultado: quatro cenários verdes.

## 8. Assinatura inerte

- [x] 8.1 Criar `plans/basic.yaml` e `plans/pro.yaml` refletindo §3.4 (OMR ilimitado nos dois; geração por IA com quotas distintas; correção discursiva por IA apenas no Pro). Resultado: arquivos versionados no Git.
- [x] 8.2 Implementar o carregamento e a validação dos planos na inicialização, falhando o processo em arquivo ausente ou inválido (D-0.8). Resultado: arquivo corrompido derruba o boot com mensagem identificável.
- [x] 8.3 Implementar `EntitlementResolver.resolve(organizationId)` como único ponto que combina plano da assinatura corrente e saldo do ledger. Resultado: nenhum outro ponto do código lê plano ou saldo.
- [x] 8.4 Testes dos cenários de "Direitos resolvidos a partir de planos versionados": organização com plano, sem assinatura, com assinatura expirada ou não ativa, e plano desconhecido. Resultado: quatro cenários verdes, com o plano desconhecido falhando explicitamente.
- [x] 8.5 Testes dos cenários de "Ledger de créditos append-only": alteração e exclusão recusadas, saldo derivado dos lançamentos, correção por lançamento compensatório e isolamento do ledger por organização. Resultado: quatro cenários verdes.
- [x] 8.6 Testes dos cenários de "Assinatura pertence à organização": direitos independentes por contexto, segunda assinatura ativa recusada e assinatura sem organização recusada. Resultado: três cenários verdes.
- [x] 8.7 Testes dos cenários de "Nenhum enforcement nesta capacidade ainda": organização sem plano opera normalmente e virada de período não movimenta o ledger. Resultado: dois cenários verdes; nenhum caminho de código recusa operação por quota.

## 9. CI

- [x] 9.1 Criar `.github/workflows/ci.yml` rodando build, codegen jOOQ e a suíte completa com Testcontainers. Resultado: pipeline verde em push e em pull request.
  - Verde em push: run 31744829416. Dois defeitos que só um runner limpo revela apareceram no caminho — `gradlew` sem bit de execução no índice, e `GenerateJooqTask.kt` engolido pelo padrão `build/` do `.gitignore`, que casava o pacote `com/platos/build`.

## 10. Verificação final

- [x] 10.1 Rodar `./gradlew build` e a suíte completa do zero, com o banco recriado a partir das migrations. Resultado: verde, sem estado residual.
- [x] 10.2 Conferir cada cenário de `specs/identity/spec.md` e `specs/billing/spec.md` contra um teste existente e listar qualquer um sem cobertura. Resultado: cobertura completa ou lacunas explicitamente registradas.
  - Único cenário sem teste direto era "Alteração de plano é mudança de código" (billing); coberto agora em `EntitlementResolverTest`. Os demais 29 cenários já tinham teste correspondente.
- [x] 10.3 Revisar que nenhuma política RLS, consulta ou verificação de autorização usa `user_id` ou `created_by_user_id` como chave de acesso (§3.2, D39). Resultado: revisão registrada no PR.
  - Revisão feita sobre `0005_rls_policies.sql` e `OrganizationQueries.kt`: nenhuma política cita `created_by_user_id`; `organization`, `subscription` e `credit_ledger` autorizam por `membership` sobre `organization_id`. `app_user` e `membership` casam pelo usuário corrente por serem tabelas de identidade, não de domínio. Registro fica aqui enquanto não houver PR.
- [x] 10.4 Rodar `openspec validate slice-0-core-schema-tenancy --strict`. Resultado: sem erros.
