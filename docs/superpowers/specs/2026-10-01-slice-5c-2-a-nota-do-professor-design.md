# slice-5c-2-a-nota-do-professor — design (o COMO)

**Requisitos de entrada (o QUÊ, já aprovados):** `openspec/changes/slice-5c-2-a-nota-do-professor/proposal.md` e
`specs/scoring`, `specs/result-sync`. Este documento não os altera. Se uma decisão daqui revelar mudança de
requisito, **pare e use `/opsx:update`**.

**Escopo:** contrato e servidor. O aparelho é a 5c-3. **Caminho do brainstorming:** arquitetural (contrato de
transporte e schema).

## 1. Decisões (aprovadas pelo mantenedor em 2026-10-01)

| # | Decisão | Por quê |
|---|---|---|
| D1 | **DTO e rota novos**: `GradedResultSubmissionDto` em `packages/domain/.../transport` e `POST /organizations/{organizationId}/exams/{shortId}/results/graded`. O contrato e a rota atuais não mudam. | `ResultSubmissionDto.points/earned` são `Int` e o aparelho compila contra ele; alargá-lo quebraria o aparelho e a regra 1 (contrato antes de consumidor) pede aditivo. O KDoc do DTO atual já recusa inferir tipo pela forma. |
| D2 | **`Pontos`**: centésimos em `Long`, no `commonMain`. Leitura estrita `^\d+(\.\d{1,2})?$`; saída canônica com 2 casas. | `commonMain` não tem `BigDecimal` e o domínio compila para JVM, Android e JS. Inteiro em centésimos é exato. |
| D3 | **No fio, string JSON** (`"1.75"`). | Número JSON passa por `Double` (`kotlinx.serialization`, JS), por onde `0.1 + 0.2` entraria. |
| D4 | **Banco: `numeric(8,2)`** em `grading_result.points` e `answer_observation.earned`, por alargamento de `int`. `max_score` e `worth` seguem `int`. | O pacote é inteiro e imutável. O alargamento preserva os valores existentes. |
| D5 | **Forma do fato:** `grading_result` + `answer_observation`, com `path` e `completes_capture_id` novos. **Sem tabela própria.** | A arquitetura §11 já nomeia `grading_result(origin: omr\|ai\|teacher, path: image\|text)`. Tabela própria seria desvio dela. |
| D6 | **Evidência da discursiva:** `answer_observation.answer_kind = 'discursiva_corrigida'`, `answer_options` vazio. | Reaproveita a evidência por questão. Exige ajustar o `check` `pendente_sem_ponto` (hoje zera `earned` para tudo que não é `marcada`/`em_branco`). |
| D7 | **"Humana vence a automática da mesma captura": uma view** `grading_result_current` (`security_invoker`), sem coluna mutável. | O append-only por gatilho recusa `UPDATE`. A regra mora num lugar só. |
| D8 | **`ApuracaoParaEnvio` não ganha subtipo.** | Subtipo novo quebra os `when` exaustivos do aparelho. A 5c-3 o estende quando for consumidor (P18). |

**Postgres:** `supabase/config.toml` declara `major_version = 17`; o container dos testes e da geração do jOOQ é
`postgres:16-alpine`. `security_invoker` existe nos dois (PG 15+). *Conferido por leitura dos arquivos; o teste da
view confirma na execução.*

**ADR:** `docs/adr/0021-a-nota-do-professor-decimal-exata-e-a-revisao-corrente.md` registra D2–D7 (representação exata no fio e no banco, e a regra da corrente). É
decisão duradoura de contrato e de schema, e por isso ADR (a proposta já o prometia). Texto curto, com a fatia-limite.

## 2. Domínio (`packages/domain`, KMP)

- `Pontos` (`scoring`): `@JvmInline value class` sobre centésimos, aritmética inteira, `parse(String)` com três
  recusas distintas (negativa, mais de 2 casas, não numérica) e `toString()` canônico (`"1.50"`). Serializador de
  string próprio. `1.5` e `1.50` são o mesmo valor.
- `NotaDoProfessor` (`scoring`): tipo **próprio**; não herda de `PartialScore` nem de `ObjectiveScore`, e não os
  contém. Campos: pacote, variante, a parte objetiva (pontos `Int`, pendências, evidência), as discursivas
  (`item`, `worth: Int`, `earned: Pontos`), `total: Pontos`, `maxScore: Int`, `closed` **derivado** (sem pendência
  objetiva). Guardas no `init`: cobertura exata das discursivas da parcial (sem faltar, sobrar nem repetir), cada
  nota em `0..worth`, soma exata de objetivas e discursivas igual ao total, e `objectiveMax + sum(worth) = maxScore`.
- `ObjectiveScoring.completarComNotaDoProfessor(parcial, notas)` → `NotaDoProfessorOutcome` (`Scored` / `Rejected`
  com a razão numa frase). Recusa também a prova só objetiva.
- `GradedResultSubmissionDto` (`transport`): `capture_id`, `completes_capture_id`, `student_token?`, `package_hash`,
  `variant_id`, `origin`, `path`, `points`, `max_score`, `closed`, `captured_at`, `observations` (a parte objetiva,
  como hoje), `essay_grades` (`item_id`, `earned` em string). Sem imagem, sem nome de arquivo, sem nome, turma ou
  matrícula (I5; requisito "não leva imagem").
- `AnswerKind` ganha `DISCURSIVA_CORRIGIDA` em `TODOS` (na ordem do `check`). O `when` sobre `QuestionAnswer` não
  muda, porque a discursiva não é um `QuestionAnswer`.

## 3. Servidor (`apps/api`)

**Rota** `POST …/results/graded`, mesma autenticação e `Tenancy.asUser`, em duas fases como a 5b-4:

1. **Sem pacote** (`paraNotaDoProfessor()`): `origin == teacher`, `path == image`, forma e decimais (`Pontos.parse`),
   item repetido, coerência da parte objetiva. Falha → **400** com a mensagem do domínio.
2. **Com o pacote publicado, único oráculo** (`conferirProveniencia`, ramo novo): pacote e variante; as discursivas
   do pacote (`awaiting`) reconstroem a `PartialScore`; `completarComNotaDoProfessor` roda o **mesmo código** que o
   aparelho usará; o total, o máximo e o `closed` **declarados** são comparados com os recalculados. Falha → **400**
   nomeando o que não fecha. A conferência **não** recalcula a parte objetiva a partir do gabarito.
3. **Gravação** (`ResultQueries.recordGraded`) só depois, na mesma transação: mesma idempotência por
   `(exam_id, capture_id)` e mesma numeração de `revision` do `record()` atual; `origin = 'teacher'`,
   `path = 'image'`, `completes_capture_id`; as observações objetivas e as discursivas.
- Ausência (prova inexistente, sem pacote, outra organização) continua **404**. Nada é gravado em nenhuma recusa.

## 4. Banco (`supabase/migrations`, uma migration)

1. `grading_result.points` e `answer_observation.earned` → `numeric(8,2)` (`using`), `check`s de escala mantidos.
2. `grading_result`: `path text`, `completes_capture_id text`, mais um `check` de que **linha `teacher` tem os dois e
   as demais não têm nenhum**, e `check (path is null or path = 'image')`.
3. `answer_observation`: `answer_kind` aceita `'discursiva_corrigida'`; `pendente_sem_ponto` passa a abrir a exceção
   para ele; `em_branco_sem_alternativa` inalterado; um `check` de que o novo tipo tem `answer_options` vazio.
4. View `grading_result_current` (`with (security_invoker = true)`): partição pela **chave da folha** (o token, ou,
   se nulo, `completes_capture_id` para `teacher` e o próprio `capture_id` para `omr`). Uma revisão `omr` **não é
   elegível** se existir revisão `teacher` da mesma prova com `completes_capture_id` igual ao `capture_id` dela; entre
   as elegíveis, a de maior `revision`. `grant select` para `app_backend`.
5. Atenção: `numeric(8,2)` **arredonda em silêncio** 3 casas no `insert`. A recusa é do domínio, antes do SQL, e há
   teste de que o valor de 3 casas nunca chega ao banco.

**Folha avulsa (token nulo):** a numeração de `revision` hoje agrupa todas as avulsas num só contador
(`is not distinct from`). **Não muda** (regra 6); a view usa a chave acima para a nota do professor seguir a parcial
dela. Fica registrado em `docs/cobertura-slice-5c-2-a-nota-do-professor.md` como limitação preexistente.

## 5. Mapa requisito → mecanismo → teste (o que cada cenário das specs exige ver)

| Cenário (spec) | Mecanismo | Teste (ver falhar) |
|---|---|---|
| scoring: completar, total 8.25 de 11 | `completarComNotaDoProfessor` | `commonTest`; mutação: soma em `Double` |
| scoring: 0.1 + 0.2 = 0.3 | `Pontos` em `Long` | igualdade exata; mutação: `Double` |
| scoring: `1.5` = `1.50` | `parse` canônico | os dois produzem o mesmo resultado |
| scoring: 3 casas / negativa / não numérica | `Pontos.parse` | cada uma com **a sua mensagem**; mutação: aceitar 3 casas e arredondar |
| scoring: acima do valor, não discursiva, faltando, repetida, prova só objetiva | guardas do `init` e do cálculo | uma fixture por camada (rigorous §3: isolar a camada, conferir o motivo) |
| scoring: objetiva e parcial continuam inteiras | tipos `ObjectiveScore`/`PartialScore` intactos | testes existentes verdes, sem asserção mudada |
| result-sync: revisão nova, parcial legível | `recordGraded`, `revision` | rota + banco real (Testcontainers) |
| result-sync: autocontida, sem a parcial | a rota não lê a parcial anterior | grava com a tabela vazia |
| result-sync: reenvio e nova nota | `(exam_id, capture_id)` único | duas chamadas, mesma revisão |
| result-sync: recusas por pacote/variante/discursiva/faixa/soma/origem/caminho, nada gravado | `conferirProveniencia` ramo novo | `count(*)` = 0 em `grading_result` **e** `answer_observation`; mutação: gravar antes de conferir |
| result-sync: 1.5/1.75 gravados e relidos sem perda | `numeric(8,2)` | leitura direta da coluna, comparada com o literal do corpo |
| result-sync: humana vence (4 cenários) | view `grading_result_current` | quatro ordens de chegada; mutação: tirar a exclusão da captura referenciada |
| result-sync: sem imagem/dado pessoal | DTO sem o campo | literal nos dois lados + corpo com campo extra não é gravado |
| contrato preso nos dois lados | `fio.mjs` | literal do **servidor** (`apps/api/src/test`) e do **aparelho** (`apps/android/src/test`, **só teste**); mutação de `@SerialName` derruba os dois |
| `answer_kind` com 5 valores | `answer-kind.mjs` | passo do CI que força divergência e exige falha |

## 6. Fora de escopo (e para onde vai)

- Todo o aparelho (tela, outbox, envio, `WorkManager`): **5c-3**.
- Leitura da revisão corrente por `apps/web`: a view existe e é testada; o consumidor é posterior.
- Resolver pendência objetiva (`grade_override`), rubrica por critério, `ai`/`text`, autoria do corretor.
- A numeração de `revision` das folhas avulsas (limitação preexistente, só registrada).
- Aplicar a migration em produção: **não** (§16, `antes-de:migration-da-5-em-producao`; o archive diz isso).

## 7. Riscos

- **Alargar `points`/`earned` regenera o jOOQ** e faz o código e os testes que leem `Int` verem `BigDecimal`. É
  mecânico, sem mudar asserção, mas é a parte que mais toca arquivo existente. Isolar em **commit próprio** (P25).
- **A view `security_invoker` depende de a RLS de `grading_result` valer para o chamador.** Teste com usuário de
  outra organização (deve ver zero linhas).
- **Reescanear depois da nota** faz a captura nova virar a corrente (spec aprovada); a nota do professor continua
  legível. Comportamento desejado, mas é o que mais surpreende.
- **Ordem de chegada de duas notas do professor** para a mesma captura segue "a mais recente é a corrente" por
  chegada, como a recaptura hoje.

## 8. Ordem de entrega (cada item é um commit lógico; regra 1, contrato antes do consumidor)

1. ADR-0021 (e o ajuste da linha de Impact da proposta, por `/opsx:update`).
2. Domínio: `Pontos`, `NotaDoProfessor`, a composição e seus testes `commonTest`.
3. Contrato: `GradedResultSubmissionDto`, `AnswerKind`, os dois literais e os guardas do CI.
4. Migration + regeneração do jOOQ + **ajuste mecânico** dos testes que leem `Int` (commit separado).
5. Servidor: ramo novo de `conferirProveniencia`, `recordGraded`, a rota.
6. Verificação cheia (P5), mutações vistas falhar (P9/P10), `docs/cobertura-slice-5c-2-a-nota-do-professor.md` e a reconciliação das
   linhas do §16 (P27) para o archive.
