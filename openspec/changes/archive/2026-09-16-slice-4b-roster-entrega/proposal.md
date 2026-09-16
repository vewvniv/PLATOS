## Why

O roster existe no servidor desde a fatia da LGPD e ganhou **linhas reais** na
`slice-4b-atribuicao-no-papel`, mas **não há como o aparelho obtê-lo**: as quatro rotas de hoje
entregam saúde, organizações, a listagem de provas e os bytes do pacote — nenhuma entrega roster.

Sem ele no aparelho, a folha lida identifica o aluno por **token opaco**. O professor vê `tok-a`
onde precisa ver quem é, e a nota apurada não tem a quem ser mostrada. ADR-0002 decidiu exatamente
isso ao tirar nome, turma e matrícula do pacote imutável: eles passam a um roster mutável
**entregue junto do pacote** — e essa entrega é o que falta existir.

**Esta é a primeira de duas fatias.** A regra 3 cortou aqui: a rota é requisito de `exam-package`, e
o cache no aparelho mais o nome na tela são de `device-session` e `scan-session`. Três capabilities
numa fatia só é o que a regra manda reavaliar, e o corte é o mesmo padrão que a `4b-atribuicao`
usou — servidor primeiro, aparelho depois.

## What Changes

- **Uma rota nova entrega o roster de uma prova publicada**, no mesmo formato de autorização das duas
  que já existem: autenticação exigida, e roster de organização alheia **indistinguível** de
  inexistente.
- **A entrega leva o mínimo que o consumidor usa:** o token e o nome de apresentação. Turma e
  matrícula **ficam no servidor** — o aparelho não tem uso para elas, e dado que não desce não
  precisa ser apagado depois.
- **Prova publicada sem roster entrega lista vazia**, e não 404: "esta prova não tem roster" é
  afirmação sobre o mundo, e é distinta de "esta prova não existe".
- **A entrega SHALL NOT declarar hash do roster.** ADR-0002 recusou um segundo hash explicitamente —
  ou ele trava o roster e a eliminação morre, ou é recalculado a cada mudança e não garante nada.

**O que NÃO muda:**

- **O pacote e a listagem**, byte a byte e campo a campo. O roster sai por rota própria, e não
  acoplado ao corpo do pacote — enfiá-lo ali reabriria a decisão de ADR-0008 e ADR-0013 sobre
  entregar os bytes exatos sobre os quais o `content_hash` foi calculado.
- **Nada no aparelho.** Cache, gate conferindo dois artefatos, apagamento ao sair e o nome na tela
  são a fatia **β**.
- **Nada em `capture-omr`:** a spec dela já diz que do QR saem prova, aluno, variante e região.
- **`packages/domain`, `apps/web`, `apps/android`, `vision/` e `omr/`.**
- **A rota HTTP de publicação com roster e a tela de montagem** seguem adiadas, com dono e
  fatia-limite no `design.md` da `4b-atribuicao-no-papel`.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `exam-package`: ganha requisito para a **entrega do roster** de uma prova publicada — quem pode
  obtê-lo, o que ele traz, o que acontece quando não há roster, e por que ele não declara hash.

## Impact

**Código**: `apps/api/src/main/kotlin/com/platos/api/http/Routes.kt` (a rota),
`apps/api/.../exam/ExamQueries.kt` (a consulta, sob RLS por `asUser`),
`apps/api/.../http/dto/` (o DTO da resposta), e os testes de rota em
`apps/api/src/test/kotlin/com/platos/api/http/`.

**Dados**: nenhuma migração. `exam_roster` já existe, com `student_token`, `display_name`,
`identification_mode` e classe de retenção **C** declarada
(`20260827223416_identification_mode.sql`). Esta fatia **lê**; não escreve.

**Retenção e LGPD**: a fatia **não** cria cópia de dado pessoal fora do servidor — ela abre o caminho
para que a β crie. As duas linhas de ponto de não-retorno registradas no §16 em 2026-09-12 (a classe
H não enumerar o roster baixado, e a regra de apagamento no aparelho) têm fatia-limite na **β**, e não
aqui. O que esta fatia faz a favor delas é **minimizar o que desce**: turma e matrícula não saem do
servidor.

**Compatibilidade**: aditiva. Rota nova, nenhum contrato existente alterado.
