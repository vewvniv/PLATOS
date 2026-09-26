## Why

Hoje o caderno (as regiões capturadas de cada aluno) e a última parcial objetiva apurada
(`slice-5b-2-a-nota-objetiva-parcial`) vivem só em memória, no `ScanState` da sessão de escaneamento.
Fechar o aplicativo no meio de um lote de folhas — bateria, troca de app, ligação — perde esse
progresso, e o professor precisa escanear de novo as folhas já lidas. O `design.md` da 5b-2 aceitou
isso como lacuna explícita ("o caderno se perde ao sair da tela... e é a 5b-3 que guarda") e listou
"guardar a parcial ou o caderno" como o próprio non-goal que abre esta mudança.

## What Changes

- O caderno **em andamento** — o de um único aluno por vez, exatamente como `ScanSession` já o
  mantém em memória (`caderno: Caderno?`, decisão 4 da 5b-2: a folha de outro aluno começa outro
  caderno, e não há mapa por aluno) — passa a ser guardado em armazenamento durável, e sobrevive ao
  encerramento do processo do aplicativo, e não só ao de um quadro para o outro. A parcial já mora
  dentro do caderno (`Caderno.parcial`) e é guardada com ele, não como algo separado.
- Reabrir o escaneamento da mesma prova retoma o caderno em andamento exatamente como estava antes
  do fechamento. Esta mudança **não** introduz um mapa por aluno nem lembra o caderno de um aluno
  que já foi substituído por outro antes do fechamento — isso já se perde hoje, em memória, e
  continua se perdendo; só o que está em andamento no momento do fechamento é preservado.
- O guardado é **local**, e não entra na fila de `result-sync`: a parcial continua sendo
  `PartialScoringOutcome`, tipo distinto de `ObjectiveScore` por decisão da 5b-2 (design, decisão 1),
  e nada aqui muda essa decisão nem a torna acessível ao envio.
- Sair da sessão (`device-session`) **não apaga** o caderno guardado, pela mesma razão que o
  resultado pendente de `result-sync` não é apagado por sair: é trabalho do professor ainda não
  concluído, e não é referência puxada do servidor. Este requisito nasce e mora inteiro em
  `scan-session`, no mesmo padrão que `result-sync` já usa para o próprio pendente — sem alterar a
  lista fechada de "o que sair apaga" que vive em `device-session`.

**Não muda nesta mudança:**
- **Recorte / segundo ajuste do ADR-0018.** Não há, hoje, nenhum byte de imagem em
  `RegiaoDiscursivaNoQuadro` — o recorte não tem consumidor nesta mudança. Ele entra no escopo da
  `slice-5c` (correção manual), que é quem primeiro precisa ver a imagem.
- **Envio** da parcial ou de qualquer coisa nova — é a `slice-5b-4`.
- **Finalizar o caderno com confirmação e registro do que faltou (§8)** — depende de guardar, mas é
  tarefa própria, fora desta mudança.
- O comportamento em revogação de vínculo segue o mesmo padrão do resultado pendente hoje
  (preservado); nenhum requisito novo dedicado a esse caminho.
- O comportamento de `ObjectiveScoring`, `PartialScoringOutcome` e `Caderno` — nenhum campo, guarda
  ou regra muda. A única adição de contrato é a marca `@Serializable`, que não altera comportamento
  (ver Impact).

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `scan-session`: os requisitos "Prova com discursiva mostra a parcial objetiva, não definitiva, e
  não guarda nada" e "A completude da folha do aluno é mostrada por região" passam a exigir que o
  caderno em andamento sobreviva ao fim do processo do aplicativo, e que sair da sessão não o apague.

## Impact

- Android: `ScanState`, `Caderno` e a sessão de escaneamento ganham uma camada de persistência local
  (Room, no mesmo padrão que `device-session` e `result-sync` já usam), escopada pela prova e pela
  organização.
- **Mudança de contrato KMP, pequena e aditiva:** `PartialScoringOutcome`, `PartialScore`,
  `AwaitingEssay`, `PendingQuestion`, `PendingReason`, `QuestionOutcome` e `QuestionAnswer` (em
  `packages/domain`) ganham a anotação `@Serializable`. Nenhum campo, guarda ou comportamento muda —
  é o que permite reconstruir o `Caderno` (e a `PartialScore` que ele carrega) de volta a um objeto
  de domínio de verdade, com as guardas de construção revalidando na leitura, em vez de duplicar a
  forma de `QuestionAnswer` num DTO próprio (o `KDoc` de `QuestionOutcome.answer` já avisa contra
  essa duplicação: "um segundo vocabulário... seria uma cópia que envelhece sozinha"). O plugin de
  serialização já está ativo neste módulo (`ResultSubmissionDto`, em `domain.transport`, já o usa).
  `Caderno`, `RegiaoDoCaderno` e `EstadoDaRegiao` (Android) recebem a mesma anotação; estes não são
  contrato KMP.
- Nenhuma mudança em `result-sync`, `exam-package`, `scoring` ou `device-session`.
