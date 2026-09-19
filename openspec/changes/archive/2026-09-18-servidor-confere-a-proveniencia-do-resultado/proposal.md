## Why

**`grading_result.package_hash` e `variant_id` são gravados exatamente como o aparelho os enviou, e
nada os compara com o pacote publicado da prova** (achado 2.2 de
`docs/auditoria-2026-09-18-antes-da-fatia-5.md`). `ResultQueries.record` faz
`.set(GRADING_RESULT.PACKAGE_HASH, nota.packageHash)` e nada mais; o servidor **nunca** o compara com
`exam_package.content_hash`. `ObjectiveScore.paraNota()` confere a **coerência interna** do corpo —
nota dentro da escala, evidência que soma a nota, `closed` contra a lista de pendências — e aceita
qualquer hash de 64 hexadecimais.

**A coluna existe justamente para ser prova.** A própria migration diz: *"Contra qual pacote e qual
variante a nota foi apurada. Nota sem dizer de qual pacote é vira número sem prova no dia em que
existir mais de uma versão da prova."* E o requisito vigente de `result-sync` — "O resultado durável
diz de qual folha, de qual pacote e de qual aluno ele é" — usa **SHALL identificar**. Identificar sem
conferir é declarar.

**O fato é append-only e imutável: `package_hash` errado não tem conserto** — só revisão nova, que
não apaga a anterior. §9 da arquitetura dá a razão de fundo: a rastreabilidade existe para
**auditoria de contestação de nota**, e uma proveniência não verificada não sustenta contestação.
É o único ponto do sistema em que um dado entra no registro imutável sem oráculo.

**Não há justificativa registrada.** O item não está em §5 nem em §6 de
`docs/cobertura-slice-4b-outbox-de-resultado.md`. `ResultRouteTest` só exercita `$HASH` correto — não
existe cenário de hash divergente nem de `variant_id` que o pacote não declara.

**Por que agora, e não na fatia 5.** A fatia 5 traz **correção manual**, que é um segundo escritor de
`grading_result`. Conferir proveniência depois de haver dois escritores é retrofit. E o custo de
conferir hoje é **uma coluna numa consulta que já roda**: `findPublishedExamId` já faz `join` em
`EXAM_PACKAGE`.

**Por que depois da etapa 3, e não antes.** R2 do plano: o oráculo precisa estar parado antes de
alguém medir contra ele. `params-hash-no-pacote-publicado` moveu o `content_hash` de todo pacote; uma
conferência escrita antes dela teria literais de teste que passariam a mentir.

## What Changes

- **Commit 1 — spec antes de código.** O requisito "O resultado durável diz de qual folha, de qual
  pacote e de qual aluno ele é" ganha a **contraparte do servidor**: recusar resultado cujo
  `package_hash` não seja o `content_hash` do pacote publicado daquela prova, e cujo `variant_id` o
  pacote não declare; a recusa distingue-se de ausência e não grava nada. **Dois cenários novos, um
  por campo.**
- **Commit 2 — a conferência.** `findPublishedExamId` passa a devolver também o `content_hash` — a
  consulta **já faz `join` em `EXAM_PACKAGE`**, então é uma coluna a mais e não uma consulta a mais.
  A comparação acontece dentro da mesma transação de `asUser`, **antes** de qualquer `insert`. A
  validação do `variant_id` sai do **pacote publicado**: o `content` está a um `select` de distância
  e `ExamPackage.variants` o declara.
- O status é **400, e não 404**. Ausência continua sendo ausência (prova inexistente, prova sem
  pacote, organização alheia); corpo incoerente é decisão do servidor sobre o pedido — a faixa que o
  aparelho já classifica como **definitiva** e não retentável (`Retorno.Recusou`, `eTransitoria()`).
  Um 5xx aqui faria o aparelho repetir para sempre um envio que nunca será aceito.
- **Duas mutações de conjuntos disjuntos** provam que cada trava segura o que diz segurar.

**Não é BREAKING.** Nenhum contrato de fio muda: `ResultSubmissionDto` continua com os mesmos campos
e os mesmos nomes. O que muda é que um corpo que hoje é aceito por engano passa a ser recusado — e
todo aparelho que envia `package_hash` do pacote que ele de fato conferiu continua sendo aceito, que
é o caminho único hoje (a camada (a)/(b) de ADR-0013 já recusa pacote que não bata antes de existir
captura).

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `result-sync`: o requisito "O resultado durável diz de qual folha, de qual pacote e de qual aluno
  ele é" passa a exigir que o servidor **confira** a proveniência declarada contra o pacote publicado
  da prova, e não apenas que o resultado a identifique. Ganha também a distinção normativa entre
  recusa por **incoerência** e recusa por **ausência**, que é o que o classificador do aparelho
  consome.

**Nenhuma outra capability é tocada.** `exam-package` já diz que o pacote é imutável e identificado
por hash, e nada nela muda: esta mudança **lê** esse oráculo, não o redefine. `scan-session` descreve
a captura no aparelho, e o aparelho não muda em código nem em comportamento.

## Impact

**API (apps/api) — é onde tudo acontece**
- `apps/api/src/main/kotlin/com/platos/api/exam/ResultQueries.kt` — `findPublishedExamId` passa a
  devolver `content_hash` junto com o `id`; a comparação entra antes do `insert` de `record`.
- `apps/api/src/main/kotlin/com/platos/api/http/Routes.kt` — a rota `POST .../results` traduz a
  recusa em **400**, com mensagem que diz **qual** dos dois campos não fecha.

**Testes**
- `apps/api/src/test/kotlin/com/platos/api/http/ResultRouteTest.kt` — dois cenários novos, com a
  guarda de vacuidade descrita no `design.md`: `package_hash` falso **bem formado** (64 hex) e
  `variant_id` do cenário A **válido**.
- As contagens continuam sendo feitas **no banco**, nunca no corpo da resposta — é a regra que o
  cabeçalho do arquivo já registra, e uma rota que responda 400 e grave assim mesmo passa em qualquer
  asserção sobre o corpo.

**Registro**
- `docs/cobertura-servidor-confere-a-proveniencia-do-resultado.md` — novo.
- `docs/auditoria-2026-09-18-antes-da-fatia-5.md` §2.2 deixa de estar aberto, sem apagar o texto
  antigo (P7).

**Não muda**
- **Nenhum schema de banco, nenhuma migration.** A conferência é de aplicação, dentro da transação da
  RLS; a autorização continua sendo da RLS.
- **Nenhum contrato de fio.** `ResultSubmissionDto`, `ResultAcceptedDto` e os nomes dos campos ficam
  como estão.
- **Nenhuma linha de `apps/android` nem de `packages/domain`.** O classificador
  `Retorno.Recusou`/`eTransitoria()` já trata 4xx como definitivo, e é por isso que o status é 400.
- **Nenhum limiar, tolerância ou janela** (regra 0.6 do plano, P11).

**Verificação**
- **Ambiente: Docker**, para os testes de Postgres (`PostgresSupport` sobe Testcontainers). É o mesmo
  ambiente que a suíte da API já usa; nenhuma instalação nova.

**Referências**
- `docs/plano-de-correcao-antes-da-fatia-5.md`, **ETAPA 4** — o veículo, os commits, as proibições e
  a tabela do conjunto previsto.
- `docs/auditoria-2026-09-18-antes-da-fatia-5.md` §2.2 — o achado.
- `ARQUITETURA-FINAL-v3.md` §9 (rastreabilidade para contestação) e §10 (push append-only), D4
  (correção objetiva local é definitiva).
- `openspec/specs/result-sync/spec.md` — o requisito que ganha a contraparte.
