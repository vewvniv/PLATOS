## Why

Hoje a nota some. `ScanSession` apura contra o pacote em cache, `ScanState.Scored` leva o resultado à
tela, e nada o persiste — a fatia 3c registrou isso como escopo ("não persiste nada; Room e outbox são
fatia 4"). O professor que escaneia a turma inteira e fecha o aplicativo perde o trabalho, e o servidor
nunca soube que houve correção.

O `push append-only` da §10 não existe em nenhuma forma: busca em todo o repositório não encontra
`outbox_event`, `capture_session`, `grading_result` nem `assessment_fact` — nem nas oito migrations,
nem na API, nem no domínio. Não há rota de escrita. Esta fatia fecha o menor caminho completo entre a
folha lida e o fato gravado no servidor.

É também o gatilho nomeado de Room. ADR-0013 §3 adiou Room com gatilho escrito — *"Room fica para a
4b, onde o outbox é de fato relacional"* — e é este outbox.

## What Changes

- `ObjectiveScore` passa a carregar o **resultado por questão** ao lado do total. Hoje
  `ObjectiveScoring.score` acumula `points` num laço e só registra as pendências; o acerto e o erro de
  cada item são descartados. **É mudança de contrato em KMP, e vem antes dos consumidores** (regra 1).
- O aparelho ganha **Room**: a correção vira linha durável assim que a nota é apurada.
- O resultado durável nasce **pendente de sincronização** e entra numa fila local.
- Havendo rede, o aparelho **empurra** o resultado por rota autenticada. Sem rede, ele fica na fila.
- A API ganha a **primeira rota de escrita** e as tabelas que a recebem, append-only.
- A gravação é **idempotente por revisão**: reenvio do mesmo resultado não duplica; recaptura da mesma
  folha cria revisão nova, e a mais recente é a corrente (§10).
- Confirmada a gravação, a linha local pendente é **expurgada** — é a cláusula de prazo da classe H,
  que já está escrita: *"eliminados automaticamente após a sincronização bem-sucedida"* (política
  §10.8).
- `Room`, `WorkManager` e o plugin de processamento de anotações entram no catálogo de dependências.

## Capabilities

### New Capabilities

- `result-sync`: o resultado da correção como fato durável — persistência local no aparelho, fila de
  sincronização, envio autenticado, gravação append-only no servidor, idempotência por revisão,
  confirmação e expurgo do pendente local.

### Modified Capabilities

- `scoring`: a nota apurada passa a carregar a evidência por questão, e não só o total e as
  pendências. Nenhuma regra de apuração muda — o que muda é o que o resultado **leva consigo**.

## Impact

**Contrato (KMP, primeiro):** `packages/domain/.../scoring/ObjectiveScoring.kt` — `ObjectiveScore`
ganha o resultado por item; `ObjectiveScoring.score` passa a produzi-lo. Consumidores atuais:
`ScanSession`, `NotaApresentada`.

**Aparelho:** Room (schema, DAO, migration), fila local, cliente de envio sobre o `ClienteApi`
existente — que hoje só faz `GET` —, `WorkManager` para o envio, e o expurgo após confirmação.

**Servidor:** migration nova com as tabelas do resultado, RLS e declaração de finalidade e classe de
retenção em cada uma (a guarda de `RetentionDeclarationTest` reprova a construção sem isso); rota de
escrita autenticada em `Routes.kt`; DTOs.

**Dependências:** `Room` e `WorkManager` no catálogo — ambos já sancionados por §13 e D21, então não é
tecnologia nova. O processador de anotações que o Room exigir entra como ferramenta de build.

## O que NÃO muda

- **Modo degradado fica fora.** Ele guarda imagem bruta de manuscrito — classe A, com regime de
  retenção próprio — e juntá-lo aqui faria uma fatia carregar duas classes novas de dado pessoal de
  uma vez. Ele depende de fato durável no aparelho, que é o que esta fatia introduz, e por isso vem
  depois dela. §15 já o lista separado.
- **`sync_cursor` fica fora.** É escrituração do lado do *pull*, e este fluxo não a consome (P18).
- **`assessment_fact` não é implementado.** Habilidade é dimensão analítica, não unidade de nota. Esta
  fatia preserva o insumo — o resultado por item — sem derivar fato de habilidade nenhum, e sem
  inventar pontuação por habilidade.
- **Nenhuma regra de apuração muda.** Pontuação continua vindo dos pontos por questão que o professor
  declarou no gabarito do pacote.
- **Nenhuma política de retenção nova.** Só se aplica a classe H, que já está escrita.
- **`student`, `student_alias` e `exam_assignment` não são criados.** Nenhum existe hoje, e criá-los é
  implementar ADR-0003 e a fatia 7.
- **O roster não muda.** Ele é dado puxado, não observação pendente de push, e a regra de apagamento
  dele foi fechada pela `slice-4b-roster-no-aparelho`.
