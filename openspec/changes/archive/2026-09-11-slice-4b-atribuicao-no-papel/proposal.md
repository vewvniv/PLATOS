## Why

Nenhum resultado capturado pode ser atribuído a um aluno, porque **a folha não diz de quem ela é**:
`CapturePayload.studentToken` vem vazio por construção enquanto não houver atribuição
(`packages/domain/.../capture/QrPayload.kt:6`), e a fixture de referência publica com
`assignments: 0`. As duas fatias 4a ensinaram o aparelho a puxar, conferir, guardar e escanear sem
rede — e o que ele produz morre sem dono.

Isso trava a fatia seguinte por dependência dura, não por preferência: a idempotência do §10 é
`(exam_id, student_id)`, então sem token na folha o push nasce sem chave. O momento é agora porque
`exam_roster` está **vazio** e sem tela que o preencha, e `ADR-0012` já registrou que o mesmo
trabalho depois do primeiro dado real vira retrofit sobre dado de menor já coletado.

## What Changes

- **A publicação passa a receber roster de verdade.** `ExamPublication` já tem `RosterEntry` e já
  recusa dado que o modo de identificação não admite (`apps/api/.../ExamPublication.kt:22-42`); o que
  muda é passar a exercitá-lo com roster não vazio, gravar `exam_roster` e levar **só o token** para
  `assignments[]`, como ADR-0002 decidiu.
- **O pacote passa a endereçar layout por atribuição.** Hoje `layout` é indexado por variante
  (`apps/web/scripts/examPackage.ts:36`) e o pacote traz um layout só. Folha única por aluno — que
  D44 e §7 (linhas 228-230) já decidiram — exige que o pacote enderece o layout de cada atribuição.
  **BREAKING** para consumidores do pacote: o web renderiza escolhendo por variante e passa a
  escolher por atribuição. Não há pacote publicado em produção com `assignments` não vazio, então
  nenhum artefato existente é invalidado.
- **O `LayoutEngine` emite uma folha por atribuição, com o token no payload do QR.** Hoje ele monta
  `qrPayloadOf(exam.id, REGION_INDEX)` sem aluno (`LayoutEngine.kt:243`). O payload continua sendo
  resolvido **na publicação**, dentro do `LayoutMap`, com **um escritor só** — o que a fatia 2b
  fechou.
- **O publicador de fixtures publica com roster**, para que a conferência contra o banco real e a
  paridade impressa passem a exercitar token não vazio.

**O que NÃO muda, e é deliberado:**

- **Nada no aparelho.** Roster no aparelho, cache dele e o gate de pré-voo conferindo dois artefatos
  (consequência 1 de ADR-0002) são a fatia seguinte.
- **Nada de push.** Outbox, `Room`, `assessment_fact`, `capture_session`, `sync_cursor` e modo
  degradado ficam fora.
- **O formato do payload do QR.** Ele já especifica prova, aluno, variante e região
  (`openspec/specs/capture-omr/spec.md:53`); o que muda é o **valor** do campo, não o contrato.
- **As regras de impressão.** Cada documento continua obedecendo ao que `print` já exige (A4,
  margens, monocromático); o que muda é quantos documentos e qual layout cada um usa.
- **Nenhuma decisão arquitetural.** A atribuição por roster + token no QR é D26; folha por aluno é
  D44; o escritor único do payload é da 2b. Atribuição por código digitado foi **descartada** — por
  produto, porque exigir identificador por aluno a cada escaneamento é atrito incompatível com a
  finalidade; e por arquitetura, porque revisaria D26 e exigiria ADR novo.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `exam-package`: o pacote passa a exigir uma atribuição por aluno do roster e a endereçar o layout
  por atribuição; publicar com roster deixa de ser caminho hipotético e ganha requisito.
- `layout-engine`: o `LayoutMap` de cada folha passa a carregar o token daquela atribuição no payload
  do QR, e a geração passa a produzir uma folha por atribuição.

## Impact

**Código**: `apps/api/.../exam/ExamPublication.kt` (roster não vazio, gravação em `exam_roster`,
`assignments[]`), `packages/domain/.../layout/LayoutEngine.kt` (folha por atribuição, payload com
token), `packages/domain/.../exam/ExamPackage.kt` e `ExamPackageValidation.kt` (endereçamento do
layout, coerência atribuição ↔ layout), `apps/web/scripts/examPackage.ts` (escolher layout por
atribuição), `apps/api/.../PublicarFixturesNoBancoRealTest.kt` e as fixtures.

**Dados**: cria as primeiras linhas reais de `exam_roster` — classe de retenção **C** (§10.3 da
política de privacidade, "enquanto durar o vínculo, acrescido de 12 meses"). **Esta fatia dispara o
ponto de não-retorno nível 3 do §16**: depois do primeiro roster real, retenção executável opera
sobre dado de menor já coletado.

**Custo aceito, com o número**: o pacote cresce com a turma. Hoje são **101.618 bytes** com um layout
(`fixtures/prova-referencia.package.json`); com 30 atribuições fica na ordem de **3 MB** — e é esse
artefato que o aparelho puxa, confere por hash e cacheia. Consequência a medir na fatia do aparelho,
não a decidir aqui.

**Fora de escopo, com dono e fatia-limite** (P19: adiado e registrado, não "depois se der tempo"):
a rota HTTP de publicação com roster e a tela web em que o professor monta a lista. Eles são o
caminho real de produto — nenhum professor vai publicar por teste —, e ficam nomeados no `design.md`
com dono e fatia-limite em vez de entrarem aqui.
