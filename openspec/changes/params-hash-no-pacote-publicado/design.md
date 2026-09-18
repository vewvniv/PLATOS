## Context

A motivação está em `proposal.md` — *Why*. O veículo, os commits e as proibições vêm da **ETAPA 3**
de `docs/plano-de-correcao-antes-da-fatia-5.md`, e este documento não os reabre: registra-os como
decididos.

O estado de partida:

- `PackageMeta` tem `prompt_version` e `model_id`, ambos `String? = null`. Não tem `params_hash`.
- `ExamPackage.toCanonicalJson()` usa `encodeDefaults = true`. Campo novo com valor padrão **entra**
  no JSON canônico, logo muda o `content_hash`.
- Três fixtures de pacote e dois literais de hash dependem disso.
- `fixtures/prova-referencia.package.json` está no **caminho da paridade**:
  `apps/web/scripts/examPackage.ts` a lê e alimenta `render-fixture.ts`, que produz
  `build/parity/web.pdf`.
- A conferência do aparelho (`verificarPacote`) tem quatro motivos de recusa distintos, e
  `INTEGRIDADE` e `INTERPRETACAO` são camadas diferentes de ADR-0013 decisão 4.

Três restrições duras:

**R1 — A quebra de hash acontece uma vez só.** Tudo o que entra no artefato hasheado é decidido antes
e executado junto. Fazer isso duas vezes é pagar duas vezes por nada.

**R2 — P23 é zona vermelha.** Regravar fixture exige fechar paridade **e** fidelidade na mesma
sessão, com os artefatos dos dois lados gerados naquela sessão.

**R3 — O ADR vem antes de qualquer linha de código.** As quatro decisões dele precisam estar escritas
antes de o primeiro commit existir, porque o commit 1 já deixa o build vermelho e um build vermelho
sem decisão registrada é ambíguo.

## Goals / Non-Goals

**Goals**

- Fechar I3 no artefato hasheado, com a quebra de `content_hash` acontecendo **uma** vez.
- Que a consequência aceita — pacotes antigos recusados — seja **provada real e alta**, e não
  assumida.
- Que a igualdade `meta.exam_id` ↔ `short_id` deixe de ser verdade por construção e passe a ser
  verdade afirmada.

**Non-Goals**

Ver *Decisions* 5 a 9: são decisões **já tomadas**, e não alternativas em aberto.

## Decisions

### 1. ADR-0014, e ele decide quatro coisas antes do primeiro commit

**§2 vence §5.** A arquitetura se contradiz: I3 exige a tripla; a lista de `meta` no §5 traz só os
dois primeiros. Pela precedência do `rigorous.md` §0, a invariante vence a prosa descritiva do mesmo
documento. O ADR **registra** a contradição — não a apaga (P7) — e corrige a lista do §5.

**A semântica dos três campos.** Nulos enquanto a prova for fixa; preenchidos pela fatia 6. O ADR diz
o que `params_hash` cobre — os parâmetros da chamada que produziu o artefato — e que ele é **nulo, e
não string vazia**, quando não há geração. O argumento não é novo: é o que a folha avulsa já fixou
para `student_token`, e ausência e valor vazio significam coisas diferentes.

**A consequência aceita, e ela é a parte séria.** Acrescentar o campo muda o `content_hash` de todo
pacote. Pacotes já publicados mantêm os bytes que têm — `exam_package` é imutável — e um aplicativo
atualizado passa a **recusá-los** pela camada (b): parsear e reserializar deixa de ser identidade,
porque `encodeDefaults = true` injeta `"params_hash":null` que não estava lá. Essa recusa é o
comportamento **correto**, é o que a camada (b) existe para pegar, e é alta e não silenciosa. O ADR
registra que o caminho para as provas já publicadas é o de ADR-0009 — publicar prova nova, com
`short_id` próprio — e que esta é a **janela barata**: pré-lançamento, com duas provas de conferência
publicadas. Depois da primeira turma real ela não existe mais.

**`meta.exam_id` não é renomeado** — decisão 5 abaixo.

### 2. O contrato muda sozinho, e o build fica vermelho de propósito

O commit 1 acrescenta o campo e **nada mais**. Nenhum consumidor muda. O build **fica vermelho**
nele, porque os dois literais de hash deixam de bater — e isso é esperado, e fica dito na mensagem
do commit.

**Por quê.** É a regra 1 do `CLAUDE.md` levada a sério: contrato antes de consumidor, em commit
separado. Um commit que mudasse o campo e regravasse as fixtures junto esconderia qual das duas
coisas moveu o hash, e o vermelho deixaria de dizer qual.

### 3. A fixture do contrato anterior é congelada **antes** da regravação

`fixtures/pacote-do-contrato-anterior.json`, cópia byte a byte do pacote anterior, com KDoc dizendo
que é artefato do contrato antigo, deliberadamente **não** regerado, e que o `GoldenWriterTest` não
o escreve.

**Por quê.** Sem ela, a consequência que o ADR aceitou não tem como ser exercitada: depois da
regravação não existe mais nenhum pacote do contrato antigo nesta árvore. Congelar é a única janela.

### 4. A mutação que importa não é a óbvia

A mutação óbvia — tirar o campo de novo e ver os literais caírem — **não prova nada interessante**:
ela mede a aritmética do SHA-256. A mutação que importa **isola a camada (b)** e prova que a
consequência aceita pelo ADR é real e alta.

E o cenário novo precisa de uma **guarda de vacuidade**: afirmar, no mesmo cenário, que aquele pacote
**passa** na camada (a) — `sha256(bytes) == content_hash` declarado. Sem isso a recusa poderia ser
por integridade, e o cenário estaria medindo a camada vizinha. É literalmente o sombreamento de
fixture que o `rigorous.md` §3 descreve, e que já aconteceu duas vezes nesta base.

A asserção confere **o motivo** da recusa, e não só que houve recusa.

### 5. `meta.exam_id` **NÃO** é renomeado — decidido e recusado

Renomear agora seria tentador, porque o hash já vai quebrar. **Recusado**, por duas razões:

- `LayoutMap` também tem `exam_id`, e renomear nos dois estenderia a quebra ao **golden do layout**,
  à folha de teste e a toda a cadeia de paridade — um evento P23 muito maior que este.
- Renomeação misturada com mudança funcional é o que **P25** proíbe.

O que entra no lugar é uma **asserção executável** de que os dois são o mesmo valor. O nome continua
errado e passa a estar **preso**.

### 6. Nenhum outro campo entra no `ExamPackage` — decidido

"Já que o hash vai quebrar mesmo" não é razão. Cada campo novo precisa do próprio consumidor (P18).
`params_hash` tem: **I3**.

### 7. Fixture não se regrava à mão — decidido

O caminho é o `GoldenWriterTest` atrás da flag `-Dplatos.golden.write=true`, e ele existe justamente
porque golden que se regrava sozinho não detecta nada.

### 8. Não se fecha sem paridade e fidelidade da mesma sessão — decidido

**P23 é zona vermelha.** Não é opcional porque "só mudou o pacote": a fixture do pacote está no
caminho da paridade. O precedente é a própria 4a, que fechou paridade ao mexer no `PLATOS_PACKAGE`
sem regravar golden nenhum.

### 9. `prova-referencia` não é republicada sobre si mesma em produção — decidido

ADR-0009: prova publicada tem um pacote e um só. Corrigir é publicar prova nova com `short_id`
próprio.

### 10. A regra de parada, e ela vale sobre a tarefa de verificação

**Se o conjunto real de cenários que caem divergir do previsto — mais, menos, ou outros —, pare.**

Não conserte o instrumento, não afrouxe a asserção, não ajuste a previsão em silêncio. Escreva o
conjunto real **ao lado** do previsto e diga o que ele significa (P7, P12, P14). A previsão errada da
6.6 da fatia do outbox é o precedente de como se faz isso.

Em particular: **se os cenários de integridade caírem junto com os de fidelidade, a mutação não
isolou a camada** — e o cenário novo está medindo a camada vizinha, que é o defeito que ele existe
para não ter.

## Risks / Trade-offs

**Pacotes publicados antes desta mudança deixam de ser legíveis pelo aplicativo atualizado** → Não é
mitigado, é **aceito e registrado**. É o comportamento correto da camada (b), é alto e não
silencioso, e o caminho de saída é ADR-0009. A janela é barata agora e não é depois da primeira
turma — é por isso que a mudança corre antes da fatia 5.

**A regravação pode alcançar a geometria sem que ninguém perceba** → A guarda é o `git diff`:
**exatamente três** arquivos mudam, e nenhum deles é `.layout.json`. Se um layout mudou, **pare** —
alguma coisa alcançou a geometria e a mudança trocou de tamanho (P13).

**Regravar golden e declarar paridade sem rodá-la** → P23. Os PDFs dos **dois** lados são gerados na
sessão que fecha, e cada `timestamp` vai para a cobertura (P3).

**O cenário novo pode medir a camada errada** → Guarda de vacuidade na decisão 4, e a regra de parada
na decisão 10.

**`meta.exam_id` continua com o nome errado** → Aceito e preso por asserção (decisão 5). O nome fica
errado; o que não fica é a possibilidade de os dois divergirem em silêncio.

## Migration Plan

Não há migração de dados: nenhuma tabela muda, e `exam_package` é imutável por construção.

**O que existe é um caminho de saída para as provas já publicadas**, e ele é o de ADR-0009: publicar
prova nova, com `short_id` próprio. Há duas provas de conferência publicadas em produção; as duas
passam a ser recusadas pelo aplicativo atualizado, e é isso que a cobertura precisa nomear.

**Reversão:** remover o campo devolve o `content_hash` anterior, mas exige regravar as três fixtures e
os dois literais de novo, e refazer P23. A reversão é barata em código e cara em verificação — o que
é mais uma razão para a decisão 6.

## Open Questions

Nenhuma que altere as specs, a abordagem ou as tarefas.
