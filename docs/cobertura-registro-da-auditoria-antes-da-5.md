# Cobertura — ETAPA 1, `registro-da-auditoria-antes-da-5`

**Veículo:** commits diretos, sem mudança OpenSpec.
**Plano:** `docs/plano-de-correcao-antes-da-fatia-5.md`, ETAPA 1 · **Entrada:** `docs/auditoria-2026-09-18-antes-da-fatia-5.md`
**Base:** `f643abc` · **Sessão:** 2026-09-18

**O que esta etapa afirma, e o que ela não afirma.** Ela afirma que sete afirmações de registro que
eram falsas hoje passaram a dizer o estado corrente, **com a fonte ao lado de cada uma**, e que três
riscos sem dono entraram na tabela de ponto de não-retorno da §16. Ela **não** afirma ter corrigido
nenhum dos comportamentos que esses registros descrevem: nenhuma linha executável mudou, e o plano
atribui cada correção de comportamento a uma etapa posterior.

**Não há mutação, e a razão é estrutural, não conveniência.** Nenhuma verificação nasce aqui: o
produto desta etapa é texto de registro, e o instrumento que o reprova é a leitura cruzada — cada
afirmação corrigida tem, ao lado, o arquivo e a linha que a sustentam. O `rigorous.md` §8 pede "como
foi visto falhar"; onde não há verificação nova, a resposta honesta é que não há, e a conferência que
substitui é a de §1 abaixo.

---

## 1. A leitura cruzada — cada afirmação contra a fonte que a sustenta

Toda linha desta tabela foi conferida **nesta sessão**, abrindo o arquivo citado.

| Item | Afirmação corrigida | Fonte conferida | Resultado |
|---|---|---|---|
| 1.1 | ADR-0013 estava em `Status: proposto`, e é o único dos treze fora de `aceito` | `docs/adr/0013-...md:3`, antes do commit `025bfa3` | confirmado |
| 1.1 | Cinco mudanças arquivadas foram construídas sobre ele | `openspec/changes/archive/`: `2026-09-04-slice-4a-zero-device-auth`, `2026-09-10-slice-4a-package-pull`, `2026-09-10-slice-4a-cache-referencia`, `2026-09-17-slice-4b-roster-no-aparelho`, `2026-09-18-slice-4b-outbox-de-resultado` | confirmado |
| 1.1 | Nenhum passo do `/opsx:archive` lê o status do ADR que a mudança citou | busca por "adr", sem distinção de caixa, em `.claude/commands/opsx/archive.md` e `.claude/skills/openspec-archive-change/` → **zero ocorrências** | confirmado |
| 1.2 | §15 ainda lista o modo degradado na fatia 4 | `ARQUITETURA-FINAL-v3.md:449` | confirmado |
| 1.2 | §10 promete o modo degradado em prosa categórica | `ARQUITETURA-FINAL-v3.md:354` | confirmado |
| 1.2 | ADR-0013 diz "são 4b e **4c**", e `4c` não existe em §15 | `docs/adr/0013-...md:122`; nenhuma ocorrência de `4c` em §15 | confirmado |
| 1.2 | A cobertura da 4b agrupa o modo degradado com tabelas sem consumidor | `docs/cobertura-slice-4b-outbox-de-resultado.md:378` | confirmado |
| 1.2 | O spec afirma o oposto como comportamento corrente | `openspec/specs/device-session/spec.md:352-353` | confirmado |
| 1.2 | A migration nunca aplicada por pipeline já produziu HTTP 500 em produção | `docs/cobertura-slice-4b-outbox-de-resultado.md` §5.1.1, item 2 | confirmado |
| 1.2 | O registro daquela ausência vivia numa lista de ausências | `docs/deploy-api.md:423`, seção "O que este roteiro não cobre" | confirmado |
| 1.3 | O schema **existe** em produção | `docs/cobertura-slice-4b-outbox-de-resultado.md` §5.1, tabela dos elos: `grading_result` com `capture_id = d671e626-…`, `revision` 1; `answer_observation` com 40 linhas | confirmado |
| 1.3 | São **nove** migrations, e não oito | contagem de `supabase/migrations/*.sql` → **9** | confirmado |
| 1.3 | O "oito" não era erro quando foi escrito | `git ls-tree` em `b1ad242` (2026-09-03) sobre `supabase/migrations/` → **8** arquivos | confirmado |
| 1.3 | São **dez** tabelas com RLS forçada, e não oito | busca por `force row level security` nas migrations → **10**: `app_user`, `organization`, `membership`, `subscription`, `credit_ledger`, `exam`, `exam_package`, `exam_roster`, `grading_result`, `answer_observation` | confirmado |
| 1.3 | Quem garante é a guarda derivada do catálogo, não o número | `ConnectionRoleTest.kt:94-108` lê `pg_class.relrowsecurity` e `relforcerowsecurity` | confirmado |
| 1.4 | `supabase-kt` não está no grafo do Android | `apps/android/build.gradle.kts` e `gradle/libs.versions.toml` trazem apenas `SUPABASE_URL` e `SUPABASE_ANON_KEY`, que são do projeto Supabase e não da biblioteca | confirmado |
| 1.4 | A decisão de dispensá-lo existe, com medição | decisão 9 do `design.md` da `slice-4a-zero-device-auth` (linhas 165-180) e decisão 2 do ADR-0013 | confirmado |
| 1.5 | `ScanActivity` persiste | `ScanActivity.kt:122` abre a fila do Room; `:260` chama `gravarEAgendar`; `:261` agenda o worker | confirmado |
| 1.5 | Duas políticas do arquivo de RLS referenciam o usuário como chave | `20260813223821_rls_policies.sql:56-58` (`app_user_self_select`) e `:71-73` (`membership_self_select`) | confirmado |

**A conferência que mudou o texto que eu ia escrever.** A primeira redação de 1.3 trocaria "oito
migrations" por "nove" e pronto. O `git ls-tree` em `b1ad242` mostrou que havia **oito** arquivos
naquele dia: a frase não era erro, era um certo de ontem passando por certo de hoje. O texto no
runbook diz isso, em vez de fingir que alguém contou errado.

---

## 2. O que foi rodado, e o que o sinal atravessa

Comando cheio, sem filtro de módulo (P1, P5):

```
./gradlew build --console=plain
```

**`BUILD SUCCESSFUL in 1m 10s`** · 176 tarefas acionáveis, 35 executadas, 141 `UP-TO-DATE`.
**Início `2026-09-18T14:56:03Z`, fim `2026-09-18T14:57:14Z`** (UTC; 16:56–16:57 local).

**Por que rodar, se nada executável mudou.** Porque o item 1.5 toca dois arquivos que o build lê, e
"é só comentário" é exatamente a afirmação que ninguém confere.

**Qual passo o sinal atravessa, e é aqui que ele diz algo.** A migration editada é entrada declarada
de `GenerateJooqTask` (`migrationsDir`, `@PathSensitive(RELATIVE)`), então a edição invalidou a
tarefa e ela **re-rodou**: subiu um Postgres 16 efêmero e aplicou as nove migrations, incluindo o
cabeçalho reescrito — `apps/api/build/generated/jooq` com `mtime` 16:56:22 local, dentro da janela do
build. E a saída gerada ficou **idêntica**, o que fez `:apps:api:compileKotlin` e `:apps:api:test`
permanecerem `UP-TO-DATE` (os XMLs de teste da API continuam com `mtime` 13:02:04, de antes da
sessão).

Esse par — a tarefa re-roda, a saída não muda — **é** o oráculo desta etapa para o item 1.5: se a
edição tivesse tocado schema em vez de comentário, as classes jOOQ teriam mudado e a suíte da API
teria sido forçada a rodar. Ele é independente do que eu afirmo porque quem compara é o Gradle
contra o conteúdo gerado, e não uma leitura minha do `diff`.

Os testes de `apps/android` e `packages/domain` **rodaram** nesta sessão (28 e 93 relatórios XML
reescritos dentro da janela do build), cobrindo o outro arquivo tocado por 1.5.

---

## 3. Escopo, conferido em vez de afirmado

- Arquivos sob `openspec/` alterados entre `f643abc` e o fim da etapa: **0**. É a primeira proibição
  da etapa, e foi conferida pela lista de nomes do `git diff`, não pela lembrança de não ter
  editado.
- Ocorrências de `MUTACAO` fora de `build/`: **33**, todas em prosa — documentos `cobertura-*.md` e
  `tasks.md` de mudanças **arquivadas**. Nenhuma em arquivo de código, e esta etapa não introduziu
  nenhuma (regra 0.7 do plano).

Os oito arquivos alterados, e nada além deles:

| Arquivo | Item |
|---|---|
| `docs/auditoria-2026-09-18-antes-da-fatia-5.md`, `docs/plano-de-correcao-antes-da-fatia-5.md` | entrada da banda (ver §5) |
| `docs/adr/0013-pull-de-referencia-imutavel-no-aparelho.md` | 1.1 |
| `docs/architecture/ARQUITETURA-FINAL-v3.md` | 1.2 |
| `docs/deploy-api.md` | 1.3 |
| `CLAUDE.md` | 1.4 e 1.4b |
| `apps/android/.../ScanActivity.kt`, `supabase/migrations/20260813223821_rls_policies.sql` | 1.5 |

Um commit por item, na ordem 1.1 → 1.5: `025bfa3`, `1412995`, `cfd7f35`, `04c676f`, `bb24941`,
`a748ee6`.

---

## 4. O que ficou sem verificação automática, e por quê

- **Nada impede que estes registros envelheçam de novo.** É a causa que o achado 7 nomeia, e o plano
  a trata na ETAPA 8 com uma guarda executável. Enquanto ela não existe, as sete correções desta
  etapa são verdadeiras hoje e não têm nada que as reprove amanhã. **Não é mitigado, é conhecido**
  (P8).
- **As três linhas novas do §16 não são verificadas por nada.** Tabela de prosa não tem oráculo. O
  que elas mudam é quem as lê e quando — o argumento da própria auditoria é que os itens que
  entraram nessa tabela avançaram e os que ficaram fora não, e isso é evidência histórica, não
  garantia.
- **A linha temporária do `CLAUDE.md` (1.4b) depende de alguém apagá-la.** A condição de saída está
  escrita nela; nada a impõe. Ponteiro temporário sem guarda é dívida com data, não dívida coberta.
- **A afirmação "o schema existe em produção" não foi observada por mim nesta sessão** (P26). Ela é
  herdada da conferência da `slice-4b-outbox-de-resultado`, que a observou no destino em 2026-09-18,
  e está citada por arquivo e seção. É registro de evidência alheia, corretamente atribuída — não é
  observação minha.
- **`:apps:api:test` não rodou nesta sessão**, por estar `UP-TO-DATE`. Isso é o esperado e, como §2
  argumenta, é parte do sinal — mas fica dito, e não escondido atrás do `BUILD SUCCESSFUL`.

---

## 5. Uma decisão que o plano não previu, e fica escrita

Os dois documentos de entrada da banda — a auditoria e o plano — estavam **fora do repositório**
(`?? untracked`) no início da sessão. A ETAPA 1 não os lista como item, mas 1.4b aponta o
`CLAUDE.md` para o plano e as três linhas de 1.2 citam a auditoria: ponteiro para arquivo não
versionado não é registro. Eles entraram num commit próprio (`10c4d82`), **antes** da ordem 1.1→1.5,
e o corpo do commit diz que não é item da etapa.

Não foi tratado como achado novo (P19) porque não é achado: é a condição mecânica para que 1.2 e
1.4b possam citar um arquivo.

---

## 6. Achados novos, se houver

**Nenhum.** Nada fora do escopo foi encontrado durante a execução, e portanto não há item a escrever
com dono e fatia-limite. Se houvesse, ele viria aqui e não no código — é o que a regra 0.4 do plano
determina.
