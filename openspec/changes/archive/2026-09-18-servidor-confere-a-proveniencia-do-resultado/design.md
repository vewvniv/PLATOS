## Context

A motivação está em `proposal.md` — *Why*. O veículo, os commits e as proibições vêm da **ETAPA 4**
de `docs/plano-de-correcao-antes-da-fatia-5.md`, e este documento não os reabre: registra-os como
decididos.

O estado de partida:

- `ResultQueries.findPublishedExamId` devolve `UUID?` e **já faz `join` em `EXAM_PACKAGE`** —
  `EXAM_PACKAGE.EXAM_ID.eq(EXAM.ID)` —, mas seleciona só `EXAM.ID`. O `content_hash` está a uma
  coluna de distância na consulta que já roda.
- `ResultQueries.record` grava `nota.packageHash` e `nota.variantId` sem oráculo nenhum.
- `ResultSubmissionDto.paraNota()` reconstrói o `ObjectiveScore` do domínio e é **o ponto único** de
  validação de coerência interna do corpo. Ele aceita qualquer hash de 64 hexadecimais.
- A rota `POST .../results` já distingue **ausência** (404, pelas três situações indistinguíveis) de
  **corpo incoerente** (400, vindo do `IllegalArgumentException` de `paraNota`).
- Tudo roda dentro da transação que `Tenancy.asUser` abre; a autorização é da RLS, e o
  `where organization_id = ?` é seletor, não fronteira.
- O aparelho já classifica a resposta: `Retorno.Recusou` com `eTransitoria()` separando 5xx
  (transitório, reagendável) de 4xx (definitivo, não reagendável).

Duas restrições duras:

**R1 — O oráculo já está parado.** A etapa 3 (`params-hash-no-pacote-publicado`) moveu o
`content_hash` de todo pacote e está arquivada. É por isso que esta etapa vem depois dela: uma
comparação escrita antes teria literais de teste que a etapa 3 passaria a fazer mentir.

**R2 — O fato é append-only.** `grading_result` e `answer_observation` têm gatilho que recusa UPDATE
e DELETE até para o dono da tabela. Um `package_hash` errado gravado não tem conserto. A conferência
precisa acontecer **antes de qualquer `insert`**, e dentro da mesma transação.

## Goals / Non-Goals

**Goals**

- Que `grading_result.package_hash` e `grading_result.variant_id` passem a ter oráculo, e que o
  oráculo seja o pacote publicado da própria prova.
- Que a recusa por proveniência incoerente seja **definitiva** para o aparelho, e distinta de
  ausência.
- Que cada uma das duas travas seja provada **separadamente**, por mutações de conjuntos disjuntos.

**Non-Goals**

Ver *Decisions* 4 a 7: são decisões **já tomadas**, e não alternativas em aberto.

## Decisions

### 1. O `content_hash` vem da consulta que já existe, e não de uma segunda

`findPublishedExamId` passa a devolver o par `(id, content_hash)`. A consulta **já faz `join` em
`EXAM_PACKAGE`**: é uma coluna a mais, e não uma consulta a mais.

**Alternativa considerada e recusada:** um `select` separado por `content_hash`. Ver decisão 6 — está
na lista de proibições da etapa, e a razão é o segundo oráculo.

O `null` da função continua cobrindo as **mesmas três** situações — prova inexistente, prova sem
pacote publicado e prova de organização a que o chamador não pertence —, e continua indistinguível
entre elas, pela razão que já está escrita: distingui-las revelaria a existência da prova a quem não
pode vê-la.

### 2. A comparação acontece dentro da transação de `asUser`, antes de qualquer `insert`

É onde R2 obriga. A ordem dentro da rota é: `paraNota` (coerência interna, fora da transação) →
`asUser` → `findPublishedExamId` → **conferência** → `record`. Um `insert` que acontecesse antes e
fosse desfeito depois seria correto por transação e errado por desenho: a conferência é barata e o
dado é imutável.

### 3. O `variant_id` é conferido contra o pacote publicado, que já está na mesma tabela

`ExamPackage.variants` declara as variantes, e é ele que decide. O `content` está a um `select` de
distância — na tabela que a `join` da decisão 1 já alcança. **Não** se escreve uma segunda lista de
variantes em lugar nenhum: nem coluna, nem tabela, nem constante.

### 4. O status é 400, e **não** 404 — decidido e recusado

Transformar a recusa em 404 "por simetria" com as três situações de ausência está na lista de
proibições da ETAPA 4. **A distinção entre ausência e incoerência é o que o classificador do aparelho
consome.**

Ausência continua sendo ausência: prova inexistente, prova sem pacote, organização alheia. Um corpo
incoerente é **decisão do servidor sobre o pedido**, que é exatamente a faixa que o aparelho já
classifica como definitiva e não retentável (`Retorno.Recusou`, `eTransitoria()`).

**E não é 5xx.** Um 5xx aqui faria o aparelho repetir para sempre um envio que nunca será aceito.

### 5. Nenhuma segunda validação de coerência ao lado de `ObjectiveScore` — decidido e recusado

Está na lista de proibições da ETAPA 4. **A regra 7 do `CLAUDE.md` já foi aplicada aqui de
propósito**, e `paraNota()` é o ponto único: ele recusa nota fora da escala, evidência que não soma a
nota, item repetido e desencontro entre evidência e pendências, com o **mesmo** código que rodou no
aparelho.

A conferência desta mudança é de **proveniência contra o pacote publicado**, que é informação que
`ObjectiveScore` não tem e não deve ter — ele é do domínio compartilhado e roda offline no aparelho,
onde o pacote publicado do servidor não existe. Não é uma segunda implementação da mesma regra; é uma
regra que o ponto único não podia ter.

### 6. Nenhuma consulta separada por `content_hash` — decidido e recusado

Está na lista de proibições da ETAPA 4. **Dois oráculos para "qual é o pacote desta prova" é o que as
KDoc das rotas de roster e de resultado recusam por escrito**: com dois oráculos, "publicada" passa a
significar coisas diferentes em rotas diferentes.

### 7. O servidor **não** recalcula a nota a partir do gabarito — decidido e recusado

Está na lista de proibições da ETAPA 4. **D4 e §10 são explícitos: correção objetiva local é
definitiva quando não há discursivas.** Esta etapa confere **proveniência**, não aritmética.
Recalcular seria substituir decisão registrada por preferência (P17).

Tentador porque o gabarito está no mesmo `content` que a conferência já vai ler. A tentação é a razão
de a proibição estar escrita.

### 8. Duas mutações, e os conjuntos precisam ser disjuntos

**A tarefa 4.5 da fatia do outbox já estabeleceu o padrão: uma mutação que derrube as duas travas não
diz qual segurou.** São duas mutações independentes — neutralizar a comparação de `package_hash`
(mutação A) e neutralizar a comparação de `variant_id` (mutação B) —, e o conjunto que cai sob A tem
de ser o espelho exato do que cai sob B.

### 9. A guarda de vacuidade (P13) é a **forma do dado** do cenário negativo

O `package_hash` falso precisa ser **64 hexadecimais bem formados**, e o `variant_id` do cenário A
precisa ser **válido** — senão a recusa pode vir do `check` da coluna ou da outra trava, e o cenário
passa a medir a camada vizinha.

E a contagem de linhas se faz **no banco**, nunca no corpo da resposta: uma rota que responda 400 e
grave assim mesmo passa em qualquer asserção sobre o corpo. É a regra que o cabeçalho de
`ResultRouteTest` já registra.

### 10. A regra de parada, e ela vale sobre a tarefa de verificação

É a **regra 0.5** do `docs/plano-de-correcao-antes-da-fatia-5.md`, e vale sobre esta etapa como sobre
todas:

> Toda etapa prevê **qual conjunto de cenários deve cair** sob a mutação. Se o conjunto real for
> diferente do previsto — mais, menos, ou outros —, **pare**. Não conserte o instrumento, não afrouxe
> a asserção, não ajuste a previsão em silêncio. Escreva o conjunto real ao lado do previsto e diga o
> que ele significa (P7, P12, P14).

A previsão errada da 6.6 da fatia do outbox é o precedente de como se faz isso — e a da tarefa 1.2 da
etapa 3, nesta mesma banda, é o precedente mais recente.

Em particular, e a ETAPA 4 o diz com estas palavras: **se os conjuntos das duas mutações não forem
disjuntos, pare.** Uma trava que caia sob a mutação da outra não está segurando o que diz segurar, e
o cenário está medindo a camada vizinha.

## Risks / Trade-offs

**O cenário negativo pode ser recusado pela camada errada** → A guarda de vacuidade da decisão 9: 64
hexadecimais bem formados e `variant_id` válido no cenário A, `package_hash` válido no cenário B. Sem
isso a recusa pode vir do `check` da coluna ou da outra trava.

**Uma rota que responda 400 e grave assim mesmo** → A contagem é feita **no banco**, nunca no corpo
da resposta. É exatamente o defeito que `ResultRouteTest` existe para pegar, e ele só aparece contando
linha.

**As duas travas podem não ser separáveis** → Duas mutações de conjuntos disjuntos (decisão 8). Se
não forem disjuntos, a regra de parada dispara (decisão 10).

**Ler o `content` do pacote a cada envio custa uma leitura** → Aceito. O `content` já está na tabela
da `join` que a consulta faz, e o custo de **não** conferir é um fato imutável sem oráculo. A
alternativa — uma coluna denormalizada com as variantes — seria o segundo oráculo que a decisão 6
recusa.

**Aparelho com pacote de outra versão passa a ser recusado** → É o comportamento correto, e ele não é
novo nesta mudança: as camadas (a) e (b) de ADR-0013 já recusam pacote que não bate **antes** de
existir captura. Esta trava pega o caso em que o aparelho chegou lá assim mesmo, e a recusa é
definitiva e legível.

## Migration Plan

Não há migração de dados: nenhuma tabela muda, nenhuma migration é escrita, nenhum contrato de fio é
alterado.

**Linhas já gravadas com proveniência não conferida permanecem como estão.** `grading_result` é
append-only por gatilho, e esta mudança não as toca nem as reclassifica. O que ela faz é impedir que
a próxima entre sem oráculo. As linhas existentes são as das provas de conferência pré-lançamento, e
a cobertura precisa nomear quantas são.

**Reversão:** remover a comparação devolve o comportamento anterior, e é barata — nenhum dado gravado
depende dela. O que se perde na reversão é exatamente o que o achado 2.2 descreve.

## Open Questions

Nenhuma que altere as specs, a abordagem ou as tarefas.
