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
