## Context

Ver `proposal.md` — Why. O que este documento acrescenta é o estado medido que condiciona a forma:

- `layout` do pacote é indexado por variante e traz **um** layout: `layout[v1]` são **91.906 bytes**
  compactos, dos quais a primitiva do QR são **1.058** (29×29 módulos) e a geometria é **90.848**.
  O pacote inteiro tem **101.618 bytes** (`fixtures/prova-referencia.package.json`).
- `exam_roster` existe com `student_token` e `display_name`, amarrado ao modo de identificação da
  organização, e está **vazio**. `ExamPublication` já tem `RosterEntry` e a recusa por modo
  (`ExamPublication.kt:22-42`), e é chamado só por testes: **não há rota de publicação**.
- `LayoutEngine.kt:243` monta o payload com `qrPayloadOf(exam.id, REGION_INDEX)` — sem aluno —, e o
  payload fica registrado no `LayoutMap` junto da matriz já codificada.

## Goals / Non-Goals

**Goals:**

- Escolher a **forma** com que o pacote endereça uma folha por atribuição, e fixá-la no contrato.
- Manter um escritor só do payload, resolvido na publicação.
- Deixar a fatia do aparelho livre de decisão nova: ela recebe um pacote cuja forma já é definitiva.

**Non-Goals:**

- Não desenhar o cache do roster no aparelho, nem o gate conferindo dois artefatos.
- Não desenhar a rota HTTP nem a tela — ver "Adiado, com dono e fatia-limite".
- Não resolver a chave de idempotência do push.

## Decisions

### 1. O pacote compartilha a geometria e carrega um QR por atribuição

`layout[variante]` continua sendo **a** geometria da folha daquela variante. Cada atribuição carrega
apenas o que a distingue: o **payload do seu QR e a matriz de módulos dele**. A folha de um aluno é a
geometria da variante com o QR daquela atribuição no lugar do QR da variante.

**Por que, com o número medido:**

| Forma | 30 alunos | 40 alunos |
|---|---|---|
| N layouts completos, um por atribuição | **2,64 MB** | 3,52 MB |
| **Geometria compartilhada + QR por atribuição** | **129,2 KB** | 139,5 KB |

É esse artefato que o aparelho puxa, confere por hash e cacheia, e a fatia 4a já ensinou que ~100 KB
num `Intent` derruba o aplicativo. Vinte vezes menos não é otimização prematura: é a diferença entre
a fatia seguinte herdar o que já funciona e ter que reabrir o transporte.

**O que licencia o compartilhamento é requisito, não conveniência:** `layout-engine` passa a exigir
que folhas da mesma prova e variante difiram **só** no QR. Sem esse requisito, compartilhar geometria
seria suposição; com ele, é o contrato.

**Alternativas descartadas:**

- **N layouts completos.** Mais simples de escrever e 20× maior. O `content_hash` cobre os dois
  igual, então a simplicidade não compra integridade — compra só bytes.
- **Molde no pacote e payload injetado na impressão.** Reabre o que a fatia 2b fechou: voltaria a
  existir dois escritores do payload, e a impressão passaria a precisar de um codificador de QR,
  porque a matriz vive no `LayoutMap`. É regressão, não alternativa.

### 2. O produtor desta fatia é o caminho programático, e a superfície de produto fica adiada

`ExamPublication` passa a receber roster não vazio, e `PublicarFixturesNoBancoRealTest` publica as
fixtures com roster. Nenhuma rota, nenhuma tela.

Isso é escolha de **menor superfície verificável**: o caminho programático fecha em JVM e contra o
banco real, que é o instrumento que a fatia 4a já usa, sem somar autorização HTTP nem React ao
escopo. A regra 3 do `CLAUDE.md` decidiu isto quando o corte foi feito: duas capabilities, não quatro.

### Adiado, com dono e fatia-limite

Não é "depois se der tempo" — é adiado, registrado e com data, no formato que o §16 usa:

| Item | Por que ele existe | Dono | Fatia-limite |
|---|---|---|---|
| **Rota HTTP de publicação com roster** | Nenhum professor publica por teste. É o caminho real, e sem ela a atribuição não sai do laboratório | mantenedor | a fatia que fizer o professor publicar uma prova pela primeira vez, e **antes** de qualquer piloto com turma real |
| **Tela web de montagem do roster** | O professor precisa colar ou importar a lista, e a experiência final **não pode** depender de digitação por aluno — é a mesma razão de produto que descartou atribuição por código digitado | mantenedor | a mesma da rota; a tela sem a rota não existe |

### 3. Atribuição é roster + token no QR, e isso não é decisão desta fatia

D26 fixa `exam_assignment` na publicação; D44 e o §7 (linhas 228-230) fixam folha única por aluno e a
folha avulsa como exceção; a 2b fixou o escritor único do payload. Esta fatia **aplica**.

**Atribuição por código digitado está descartada**, e as duas razões ficam escritas para não voltarem
como ideia nova: por **produto**, exigir identificador por aluno a cada escaneamento é atrito
incompatível com a finalidade da aplicação; por **arquitetura**, seria segundo mecanismo ao lado de
D26, o que exigiria ADR novo — e não se decide mudança arquitetural dentro do `design.md` de uma
fatia.

### 4. Retenção: classe C aqui, classe H na fatia do aparelho, e a lacuna fica nomeada

`exam_roster` é **classe C** (§10.3 da política: "enquanto durar o vínculo do aluno com a
organização, acrescido de 12 meses"), e já nasce com finalidade e classe declaradas
(`20260827223416_identification_mode.sql:113-117`). Esta fatia cria as **primeiras linhas reais**, e
com elas **dispara o ponto de não-retorno nível 3 do §16**.

O roster cacheado no aparelho é da fatia seguinte, e a classe dele é **H** (§10.8) por analogia. **A
lacuna é de classificação, e fica nomeada aqui porque é aqui que ela aparece:** o §10.8 enumera
"pacotes de prova baixados, imagens capturadas e observações pendentes de sincronização" e **não
lista o roster baixado**; e o prazo dele, `[30]` dias, está entre colchetes porque a linha 12 da
política diz que os prazos do item 10 são padrões propostos. Item para o parecer jurídico, não para
esta fatia resolver — e a analogia com H é a leitura **mais restritiva** disponível, que é a que se
adota enquanto o parecer não vem.

### 5. A chave de idempotência não se resolve aqui, e o porquê fica escrito

O §10 fixa `(exam_id, student_id)` com revisões. O §7 garante capturas em que `student_id` não existe
no momento da captura — a folha avulsa. Esta fatia **não fecha** essa tensão: ela só garante que a
folha *com* atribuição carrega o token, e que a folha *sem* atribuição carrega o campo vazio em vez de
um valor inventado. A pergunta — chave substituta para a avulsa, ou revisão explícita do §10 — é da
fatia do push, e ela chega lá com o caso já isolado por requisito.

## Risks / Trade-offs

- **Regravar a fixture muda o `content_hash`** → A fixture de referência é republicada com roster, e
  o hash `26612ad5…909a` deixa de valer. **P23 se aplica**: paridade e fidelidade fecham na **mesma
  sessão** da regravação, com os artefatos dos dois lados gerados nela, e o hash novo atravessa os
  testes que hoje o fixam.
- **A geometria compartilhada cria um caminho de composição** → Montar "geometria + QR da atribuição"
  é passo novo entre pacote e desenho. Mitigação: o requisito de que as folhas difiram só no QR torna
  a composição conferível — dois layouts compostos da mesma variante têm de coincidir em tudo menos
  no QR, e isso é cenário de teste, não inspeção.
- **O QR pode crescer quando o token deixar de ser vazio** → O payload de hoje tem dois campos vazios
  (`prova-referencia-slice-1...0.05CB`); com token e variante preenchidos ele cresce, e o QR pode
  subir de versão, aumentando os 1.058 bytes por atribuição. É medição de implementação, não número a
  inventar agora — e mesmo dobrando, fica duas ordens abaixo da forma descartada.
- **Primeiro dado pessoal real de menor** → Ponto de não-retorno nível 3. Mitigação disponível e já
  padrão: o modo `coded` da organização, em que o roster é código ou apelido e não nome civil
  (`identification_mode.sql:22-23`, default `coded`).

## Migration Plan

1. **Contrato antes do consumidor** (regra 1): a forma do pacote e a validação de coerência primeiro,
   depois o `LayoutEngine`, depois a publicação com roster, depois o consumidor web.
2. **Nenhum pacote publicado é invalidado**: não existe pacote em produção com `assignments` não
   vazio — a fixture publica com `assignments: 0`. A mudança de forma é aditiva para quem já existe.
3. **Reversibilidade**: reverter os commits devolve o pacote de um layout por variante. O roster
   gravado em `exam_roster` sobrevive à reversão e não fica órfão — ele é tabela própria, mutável, e
   apagá-lo é operação suportada por construção (ADR-0002).

## Open Questions

- **De quanto o QR cresce com o token preenchido**, e se ele sobe de versão. Não muda spec, forma nem
  tarefas: muda um número que a implementação mede e registra.
