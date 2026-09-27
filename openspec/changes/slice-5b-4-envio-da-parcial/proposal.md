## Why

O caderno do aluno e a última parcial objetiva apurada (`slice-5b-2-a-nota-objetiva-parcial`) já
sobrevivem ao fechamento do aplicativo (`slice-5b-3-guardar-a-parcial-e-o-caderno`), mas não têm para
onde ir: `result-sync` só grava e envia `ObjectiveScore`, e a parcial de uma prova com discursiva
nunca produz resultado durável nem entra na fila. Duas fatias seguidas (5b-2 e 5b-3) já nomearam
"envio" como o próprio non-goal que abre esta mudança.

## What Changes

- Quando o caderno do aluno atinge completude — todas as regiões que a variante declara em estado
  **capturada**, o "N de N" já mostrado na tela (`scan-session`, requisito "A completude da folha do
  aluno é mostrada por região") —, a sessão SHALL entregar a apuração parcial para gravação e envio,
  exatamente como já faz hoje para prova só objetiva.
- `ResultadoPendente.nota` (outbox Android) passa a aceitar tanto `ObjectiveScore` quanto `PartialScore`,
  por um tipo próprio que os distingue no nível da fila. A distinção de domínio entre os dois tipos de
  apuração (decisão 1 da 5b-2: "nenhum consumidor da nota completa aceita a parcial em seu lugar")
  **não muda** — o tipo novo só decide qual dos dois foi guardado, e não os torna intercambiáveis.
- O corpo enviado ao servidor para uma parcial usa o **mesmo formato** que `ObjectiveScore` já usa
  hoje (`ResultSubmissionDto`): `closed` sempre falso; `maxScore` é a pontuação máxima **da prova**,
  não só da parte objetiva; cada discursiva "aguardando correção" entra na lista de pendências como
  uma `PendingQuestion`, com um valor novo de `PendingReason` (aditivo ao enum) que a distingue de
  múltipla marcação e de indecisa.
- Gravação append-only, idempotência por revisão, autenticação e escopo por organização, distinção
  entre recusa transitória e definitiva, e a preservação do pendente ao sair da sessão ou ter o
  vínculo revogado — tudo herdado de `result-sync` como já existe, sem fila nova, sem tabela nova, sem
  rota nova.

**Não muda nesta mudança:**
- **Finalizar caderno incompleto com confirmação explícita e registro do que faltou (§8).** Esta
  mudança só cobre o caderno que **completa sozinho**; o caso em que o professor decide encerrar com
  região faltando fica para fatia própria, depois desta.
- **A correção real da discursiva** — por IA ou por revisão humana — é a fatia 8. Esta mudança só leva
  a parcial (com as discursivas "aguardando correção") até o servidor; nenhuma nota discursiva é
  produzida aqui.
- **Recorte / imagem da região discursiva** (ADR-0018, segundo ajuste da homografia). Continua sem
  consumidor; entra na `slice-5c`.
- **`scoring`.** A apuração parcial (`ObjectiveScoring.scorePartial`, `PartialScore`) já existe e não
  muda: esta mudança consome o resultado dela, não recalcula nem redefine o que ela produz.
- **`PendingReason` além do valor novo descrito acima.** Nenhum dos dois valores existentes muda de
  sentido.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `scan-session`: o requisito "Prova com discursiva mostra a parcial objetiva, não definitiva, e não
  guarda nada" passa a ter uma exceção: caderno **completo** produz resultado durável e entra na fila
  de envio, com a nota continuando não-definitiva. O requisito "A completude da folha do aluno é
  mostrada por região" ganha o efeito colateral de completude: disparar a entrega para gravação.
- `result-sync`: os requisitos que hoje só mencionam `ObjectiveScore` passam a aceitar também o
  resultado parcial de prova com discursiva, com `fechada` sempre falsa e as discursivas aguardando
  correção contando como pendência e como "quanto está em disputa".

## Impact

- **Android:** `ResultadoPendente`/`ResultadosEmRoom` (outbox) ganham um tipo que distingue nota
  completa de parcial; o ponto da sessão de escaneamento que hoje só entrega `ObjectiveScore` para
  gravação passa a entregar também a parcial, no momento em que o caderno completa.
- **KMP domain (`packages/domain`):** um tipo aditivo que representa "nota apurada pronta para a fila"
  (envelope de `ObjectiveScore` ou `PartialScore`), sem alterar nenhum dos dois tipos existentes.
- **API (`apps/api`):** `ResultDto.kt` ganha o caminho de tradução de `PartialScore` para o mesmo
  formato de corpo que `ObjectiveScore` já usa; `PendingReason` ganha um valor novo; `ResultQueries.kt`
  grava pelo caminho já existente, sem tabela nova.
- **Sem migration nova.** O formato de corpo e a tabela de gravação são os mesmos; o que muda é quem
  pode produzi-los.
- **Dívida (§16):** `node tools/divida/divida.mjs`, lido em 2026-09-27, não lista nenhuma linha
  vencendo na fatia corrente (`5b`). Esta mudança não alcança gatilho novo de nenhuma linha da tabela
  de ponto de não-retorno.
