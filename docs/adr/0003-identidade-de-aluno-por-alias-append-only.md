# ADR-0003 — Identidade de aluno é resolvida por alias append-only, não por chave única

**Status:** aceito · **Data:** 2026-08-19 · **Fatia-limite:** 3 (forma do schema decidida na 2a)
**Referências:** `ARQUITETURA-FINAL-v3.md` §11 (`student`, `enrollment`), §2 (I2), §12 (M3 boletim)

## Contexto

`membership` N:N (D39) resolve a identidade do **professor**: entrar numa escola é inserir uma linha,
não migrar dados. O aluno não tem equivalente. §11 traz `student(external_ref)` e `enrollment`, e
`external_ref` é referência externa da escola.

O caso real: um professor usa a organização pessoal, depois entra numa escola que já tem o mesmo
aluno cadastrado. Duas linhas de `student`, dois históricos, e o boletim consolidado perde parte do
histórico sem avisar.

O que torna isto caro depois é I2: os fatos são **append-only** e já apontam para a chave. Reconciliar
identidade retroativamente sobre fatos imutáveis é o problema mais caro do backlog atual.

## Decisão

A identidade de aluno é **um `student` por organização**, e a unificação entre organizações acontece
por **`student_alias` append-only**, resolvido em **read model** — nunca por reescrita de fatos.

Um alias declara que dois `student_id` são a mesma pessoa, com origem e data. Os fatos continuam
apontando para o `student_id` que existia quando foram gravados. O boletim consolidado lê através do
alias.

A **forma do schema** é decidida na fatia 2a, junto com o contrato do pacote. A **implementação** fica
para a 3, que é quando os fatos passam a existir.

### Atualização de 2026-09-17: o fato nasce chaveado pelo token, porque `student` ainda não existe

A decisão acima diz que "a **implementação** fica para a 3, que é quando os fatos passam a existir".
Os fatos **não** passaram a existir na 3: a 3c não persistiu nada, e eles passam a existir agora, na
mudança `slice-4b-outbox-de-resultado`, que leva a correção do aparelho ao servidor.

Quando esse gatilho disparou, o terreno era este, medido e não lembrado: **não há tabela `student`**,
e `student_id` não aparece em nenhum arquivo do repositório — a busca em `.kt`, `.sql`, `.ts` e
`.tsx` devolvia duas linhas, ambas comentários citando `(exam_id, student_id)` da §10 como coisa
futura. O que o aparelho tem no momento da captura é o `student_token` do QR, e o pacote leva só ele
porque I5 proíbe dado pessoal direto no artefato imutável.

Então `grading_result` nasce chaveado por **`(exam_id, student_token)`**. Dentro de uma prova os dois
são a mesma chave: `exam_roster` impõe `unique (exam_id, student_token)`, e a idempotência que a §10
pede é intra-prova por definição — "recaptura cria nova revisão; a mais recente é a corrente".

**Isto não substitui a decisão deste ADR, e é o contrário de contorná-la.** Ela diz que "os fatos
continuam apontando para o `student_id` que existia quando foram gravados" e que a unificação
acontece em **read model**, nunca por reescrita. Um fato que nasce apontando para o token é esse
mesmo desenho um passo antes: quando `student` existir, `exam_roster` ganha a chave e o caminho
`(exam_id, student_token)` → `exam_roster` → `student_id` resolve o histórico inteiro por junção, sem
tocar em fato nenhum.

**O que continua devendo, e onde:** `student`, `student_alias` e o read model do boletim ficam onde
este ADR os pôs. A fatia do outbox respondeu só a metade que ela conseguia exercitar — criar o
identificador permanente ali seria implementar este ADR inteiro mais a `exam_assignment` da fatia 7,
para produzir uma chave que nenhum critério de aceite daquela fatia poderia reprovar.

## Consequências

- I2 permanece intacta: nenhum fato é reescrito, nada é mesclado destrutivamente.
- Um alias errado é reversível — acrescenta-se o alias que o corrige, e o read model reflete.
- O read model do boletim fica mais caro: precisa resolver alias antes de agregar. É custo de
  consulta, pago só onde há alias.
- Enquanto não houver alias, o comportamento é idêntico ao de hoje.

## Alternativas descartadas

**Chave global de aluno (CPF, matrícula nacional).** Concentraria dado pessoal sensível de menor,
contra ADR-0006, e não existe de forma confiável na educação básica.

**Mesclar registros destrutivamente.** Viola I2 e é irreversível quando a mescla está errada — que é
o caso comum, já que homônimos são frequentes numa escola.
