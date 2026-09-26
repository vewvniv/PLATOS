## Context

Ver `proposal.md` (Why) para a motivação. Aqui só o estado atual que molda o desenho.

- **`ScanSession.caderno` é uma variável só, não um mapa** (`ScanSession.kt:246`,
  `private var caderno: Caderno? = null`). A folha de outro aluno descarta o caderno anterior por
  inteiro (`ScanSession.kt:171`, decisão 4 da `5b-2`). Guardar precisa espelhar exatamente essa
  forma — um slot, não uma coleção — e não introduzir memória que a sessão em memória não tem.
  *Conferido por leitura.*
- **A parcial mora dentro do caderno** (`Caderno.parcial`, `ScanState.kt:75`). Guardar o caderno já
  guarda a parcial; não há uma segunda coisa para persistir.
- **O padrão de persistência local do aparelho tem duas formas, e ADR-0013 nomeia a diferença**
  (`ResultadosEmRoom.kt:68-72`): arquivo, para registro lido inteiro e substituído inteiro (roster,
  pacote, visão); Room, para o que é consultado por chave, percorrido em ordem e apagado linha a
  linha (o outbox). O caderno em andamento é um registro só, substituído inteiro a cada atualização
  — a forma de arquivo/registro único, não a do outbox.
- **`ResultadoPendenteEntity.corpo` guarda o JSON exato do envio**, e a KDoc dela explica por quê:
  decompor em colunas e remontar arriscaria uma mudança de serialização reescrever, em silêncio, o
  que já foi congelado. A mesma razão vale aqui.
- **`DeviceSession.sair` apaga uma lista fechada e nomeada**: sessão, organização, visão, pacotes,
  rosters (`device-session/spec.md:168-172`) — deliberadamente não genérica, porque genérico é fácil
  de dar como cumprido sem reler. O caderno não entra nessa lista, e por isso não muda
  `device-session`: ele vive na própria base de `scan-session`, do mesmo jeito que o pendente de
  `result-sync` vive na dele, sem que `sair` precise saber que ela existe.
- **`onFrame` pode reatribuir `caderno` a cada quadro** em que ao menos uma região foi reconhecida
  (`ScanSession.kt:184`), o que em captura contínua de uma folha já enquadrada pode ser várias vezes
  por segundo. Persistir a cada quadro escreveria em disco nessa frequência.

## Goals / Non-Goals

**Goals:**
- O caderno em andamento sobrevive ao encerramento do processo do aplicativo, e a sessão o retoma ao
  reabrir a mesma prova.
- Sair da sessão do aparelho não apaga o caderno guardado.

**Non-Goals:**
- Um mapa de cadernos por aluno. Continua um caderno por vez, como hoje em memória (ver Context).
- Recorte, envio, ou finalizar o caderno com confirmação (`proposal.md`, "Não muda nesta mudança").
- Sobreviver a *crash* no meio de um quadro sem passar pelo ciclo de vida normal do Android — ver
  Risks.

## Decisions

### 1. Guardar é escrever no `onStop` da tela, não a cada quadro

A sessão continua construindo `ScanState` em memória, quadro a quadro, exatamente como hoje.
`ScanActivity` (ou o que corresponder na Compose) passa a escrever o caderno corrente no
armazenamento local no `onStop`, e a lê-lo no início da sessão de escaneamento desta prova, antes do
primeiro quadro.

- **Por que `onStop`, e não a cada quadro:** o Android garante `onStop` antes de a `Activity` poder
  ser encerrada em segundo plano — é exatamente o caminho de "trocar de app" e "a tela apaga", que é
  o cenário que a proposta descreve. Escrever a cada quadro custaria I/O de disco em frequência de
  câmera para um ganho que só importa no momento em que o app sai de primeiro plano.
- **Alternativa descartada: escrever a cada atualização do caderno.** Mais robusto contra um
  encerramento anômalo do processo (o sistema matando o app sem `onStop`, ou o aparelho desligando),
  mas multiplica escritas por segundo durante captura contínua sem que o ganho apareça no caso comum.
  **Não é mitigado, é conhecido (P8):** um encerramento que pule `onStop` perde o que não foi
  escrito ainda, exatamente como perderia hoje, em memória. Isso não piora nada que já existisse.
- **Ler antes do primeiro quadro:** ao abrir o escaneamento desta prova, a sessão consulta o
  armazenamento local por um caderno guardado para este `examId`; havendo um, o estado inicial nasce
  dele em vez de vazio.

### 2. Registro único por prova, substituído inteiro, na forma de `result-sync`

Uma tabela nova, própria de `scan-session` (não o `outbox.db` de `result-sync` — bases distintas,
para não acoplar o ciclo de vida do caderno ao do envio), com uma linha por `examId`, chave primária
`exam_id`, e uma coluna `corpo` com o `Caderno` (e a parcial que ele carrega) serializado
(`kotlinx.serialization`, já na stack), pelo mesmo motivo que `ResultadoPendenteEntity.corpo` guarda
o JSON exato em vez de colunas decompostas: decompor e remontar arrisca uma mudança de serialização
reescrever, em silêncio, um caderno já guardado.

**O que vai dentro do `corpo` é o próprio grafo de domínio, serializado direto — e não um DTO
próprio.** `Caderno`, `RegiaoDoCaderno` e `EstadoDaRegiao` (Android) e `PartialScoringOutcome`,
`PartialScore`, `AwaitingEssay`, `PendingQuestion`, `PendingReason`, `QuestionOutcome` e
`QuestionAnswer` (KMP, `packages/domain`) ganham `@Serializable`. Decisão tomada com o mantenedor
antes desta tarefa, registrada aqui e corrigida em `proposal.md` (Impact): a proposta original dizia
"nenhuma mudança de contrato KMP", e ganhar `@Serializable` é mudança de contrato, ainda que aditiva.

- **Por que não um DTO próprio, como `ResultadoDto` faz para o envio:** aquele DTO é *write-only* —
  nunca volta a ser um `ObjectiveScore`, porque o outbox só envia. Este precisa voltar a ser um
  `Caderno` de verdade, guardas de construção incluídas, para a sessão retomar de onde parou. Um DTO
  duplicaria a forma de `QuestionAnswer` nos dois sentidos, exatamente o que o `KDoc` de
  `QuestionOutcome.answer` já nomeia como risco: "um segundo vocabulário... seria uma cópia que
  envelhece sozinha." Anotar o tipo de domínio evita a cópia, e as guardas do `init` de `PartialScore`
  e `QuestionOutcome` revalidam a cada leitura — proteção a mais, não a menos.
- **O plugin de serialização já está ativo em `packages/domain`:** `ResultSubmissionDto` e
  `AnswerObservationDto`, em `domain.transport`, já são `@Serializable` nesse módulo. Não há
  dependência nova a declarar.
- **Sealed interfaces (`QuestionAnswer`, `PartialScoringOutcome`) ficam polimórficas por padrão**,
  porque todas as subclasses estão no mesmo módulo e ganham `@Serializable` junto — o compilador
  gera o discriminador sem `SerializersModule` manual.

Escrever SHALL substituir a linha inteira (`OnConflictStrategy.REPLACE`), porque um caderno novo
para a mesma prova (outro aluno, ou o mesmo com mais regiões) não emenda o anterior — ele o
substitui, do mesmo jeito que a variável em memória já faz.

- **Por que não o `outbox.db`:** o outbox é percorrido por organização, em ordem, e apagado linha a
  linha após confirmação — é fila. O caderno é um registro só, lido inteiro e substituído inteiro —
  é visão. Compartilhar a base misturaria dois ciclos de vida que não têm nada em comum além de
  serem Room.
- **Alternativa descartada: arquivo, como roster/pacote/visão.** ADR-0013 usa arquivo para registro
  substituído inteiro, e este também é. A diferença é a frequência de leitura/escrita dentro de uma
  mesma sessão de escaneamento (a cada `onStop`, não só ao entrar): Room dá uma escrita transacional
  sem precisar reimplementar "escrever num arquivo temporário e renomear" para evitar corromper o
  arquivo se o processo morrer no meio da escrita. Room já resolve isso.

### 3. `device-session.sair` não muda

O caderno guardado vive na base nova, própria de `scan-session`. `DeviceSession.sair` continua
apagando exatamente a lista que já apaga hoje, e não passa a conhecer esta base — do mesmo jeito que
já não conhece `outbox.db`. É este projeto que garante a sobrevivência ao sair, por omissão
deliberada, e não uma exceção escrita dentro de `sair`.

## Risks / Trade-offs

- **[O app é encerrado à força, sem `onStop`]** → o caderno volta ao que foi escrito na última
  parada normal, e não ao instante exato do encerramento. **Não é mitigado, é conhecido** (P8): é
  estritamente melhor do que hoje (que perde tudo), nunca pior.
- **[Duas sessões de escaneamento da mesma prova em aparelhos diferentes]** → cada aparelho guarda o
  seu próprio caderno local; não há sincronização entre aparelhos nesta mudança, e não deveria haver
  — isto é local, e continua sendo depois desta mudança.
- **[O pacote muda entre o guardar e o retomar]** → fora de escopo nesta fatia: o `examId` é a
  chave, e o pacote publicado é imutável por hash; se o pacote local mudar de versão, é o gate de
  pré-voo que decide, não esta camada.

## Migration Plan

Base nova, sem dado anterior — não há migration a partir de versão existente. Reverter é reverter os
commits; o arquivo da base nova fica no aparelho sem consumidor, inofensivo, até uma limpeza de dados
do aplicativo.
