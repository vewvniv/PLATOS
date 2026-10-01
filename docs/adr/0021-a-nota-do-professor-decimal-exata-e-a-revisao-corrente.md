# ADR-0021 — A nota do professor é decimal exata, mora em `grading_result`, e a revisão corrente é derivada

**Status:** aceito · **Data:** 2026-10-01 · **Fatia-limite:** archive da `slice-5c-2-a-nota-do-professor`
**Referências:** `ARQUITETURA-FINAL-v3.md` §10 (correção manual de discursiva: local, entra no outbox), §11
(`grading_result(origin: omr|ai|teacher, path: image|text)`), I2 · ADR-0015 · ADR-0003 ·
`openspec/changes/slice-5c-2-a-nota-do-professor/` · `docs/superpowers/specs/2026-10-01-slice-5c-2-a-nota-do-professor-design.md`

## Contexto

A fatia 5 entrega a parcial objetiva e a resposta guardada, mas nenhuma nota discursiva existe. A nota do
professor é por discursiva e **pode ser fracionária** (1.5, 1.75). Hoje `points` e `earned` são inteiros no
domínio, no contrato e no banco, e o fato é append-only por gatilho, o que impede marcar "a revisão corrente"
com `UPDATE`.

## Decisão

1. **Forma do fato:** a nota do professor é **revisão nova** do mesmo par (prova, token) em `grading_result`,
   com `origin = 'teacher'`, e as colunas novas `path` (`'image'`) e `completes_capture_id` (o `capture_id` da
   parcial que ela completa). **Não há tabela própria**: a §11 já nomeia `grading_result(origin, path)`.
2. **Evidência da discursiva:** linha de `answer_observation` com `answer_kind = 'discursiva_corrigida'`.
3. **Decimal exato:** no domínio, `Pontos` guarda **centésimos em `Long`** (o `commonMain` não tem `BigDecimal`);
   até 2 casas, de 0 a 999999.99. No fio, **string JSON** (`"1.75"`), nunca número JSON (que passa por `Double`).
   No banco, `numeric(8,2)` em `grading_result.points` e `answer_observation.earned`; `max_score` e `worth`
   seguem `int` (o pacote é inteiro e imutável).
4. **Contrato novo, rota nova:** `GradedResultSubmissionDto` e `POST …/results/graded`. O contrato e a rota de
   `ResultSubmissionDto` não mudam (ADR-0015: dono único em `packages/domain`).
5. **A revisão corrente é derivada, não gravada:** a view `grading_result_current` (`security_invoker`) exclui a
   revisão `omr` cuja captura alguma revisão `teacher` da mesma prova declara completar, e escolhe a de maior
   `revision` entre as elegíveis. A regra "revisão humana vence a automática da mesma captura, em qualquer
   ordem de chegada" mora **num lugar só**.

## Consequências

- `numeric(8,2)` **arredonda em silêncio** três casas no `insert`. A recusa de mais de 2 casas é do **domínio**,
  antes do SQL, e há teste de que o valor de 3 casas nunca chega ao banco.
- Um valor novo em `answer_kind` exige os três registros concordarem: domínio (`AnswerKind`), o `check` da
  migration e `tools/parity/answer-kind.mjs`, que passa a ler o `check` da **última** migration que o declara.
- `ApuracaoParaEnvio` **não** ganha subtipo agora: a 5c-3 o estende quando houver consumidor.
- A numeração de `revision` das folhas avulsas (token nulo) segue agrupando todas num contador (limitação
  preexistente, só registrada).

## Como isto poderia falhar em silêncio

- **A view perder a RLS** se não for `security_invoker`: ela passa a rodar como `app_owner`, que não tem política de
  RLS, e devolve **zero** linhas para todos (medido: falha fechada, não vazamento). O teste com usuário de outra
  organização pega pelo piso ("o dono vê a própria folha").
- **A exclusão por `completes_capture_id` não ser exercitada**: a view devolveria a parcial automática depois da
  nota do professor. Mutação: remover a exclusão e ver os cenários de chegada tardia falharem.
- **Soma em `Double`** reintroduzir `0.1 + 0.2 ≠ 0.3`. Teste de igualdade exata e mutação.
