## 1. A classificação, na camada sem Android

- [x] 1.1 `ResumoDoEnvio` ganha `transitorios`, e `pendentesRestantes` passa a somar os três que não
  confirmaram. Verificar com `:apps:android:testDebugUnitTest` que os sete cenários existentes de
  `EnvioDeResultadosTest` continuam verdes, e registrar o `timestamp` do relatório.

  **A tarefa está errada em um ponto, e fica dito em vez de absorvido (P7).** O cenário
  `recusa do servidor nao apaga o pendente` usa `Retorno.Recusou(500)` e afirma `recusados == 1`. Sob
  a classificação nova, 500 é transitório, e ele **tem** de ficar vermelho — é o requisito que mudou,
  e não a asserção que enfraqueceu (P12). A asserção de preservação dele, que é o ponto do cenário,
  continua intacta: o pendente segue na fila.
- [x] 1.2 `EnvioDeResultados` classifica `Retorno.Recusou` por faixa: status ≥ 500 conta em
  `transitorios`, o resto em `recusados`. O pendente **continua intacto nos dois casos**. Verificar com
  dois cenários novos — um 503 e um 404 — que cada um incrementa só o seu número e que a fila não
  encolhe em nenhum dos dois.
- [x] 1.3 Cenário de fila mista: um confirmado, um 503 e um 404 na mesma passada. Verificar que os três
  números saem `1/1/1`, que só o confirmado sai da fila e que o 503 **não** bloqueou o 404 que vinha
  depois — é o requisito de fila que já existia, agora sob classificação nova.
- [x] 1.4 **Ver falhar (P9):** mutar a classificação para devolver `transitorios` também no 4xx.
  Confirmar que 1.2 e 1.3 ficam vermelhos, que `recusa por vinculo revogado nao apaga o pendente` e
  `confirmado sai da fila` **não** caem — a mutação tem de isolar a classificação, e não a preservação
  do pendente —, reverter, e conferir a reversão **rodando** (P10). Registrar quais cenários caíram e
  `grep -c "MUTACAO"` depois da reversão.

  **A previsão da tarefa estava errada em um nome, e fica dita (P7).** Ela dizia que
  `recusa por vinculo revogado nao apaga o pendente` **não** cairia. Caiu — porque aquele cenário
  afirma `recusados == 1`, que é asserção de **classificação**, e não só de preservação. Conjunto
  real, sob `status >= 400`:

  | Cenário | Caiu? |
  |---|---|
  | `decisao do servidor conta como definitiva e o pendente fica` | **sim** |
  | `os tres desfechos convivem na mesma passada` | **sim** |
  | `um recusado nao bloqueia o resto da fila` | **sim** |
  | `recusa por vinculo revogado nao apaga o pendente` | **sim** |
  | `confirmado sai da fila` | não |
  | `sem rede o pendente fica intacto` | não |
  | `recusa do servidor nao apaga o pendente` (500) | não |
  | `falha do servidor conta como transitoria e o pendente fica` (503) | não |
  | `a fila de uma organizacao nao leva a de outra` | não |
  | `fila vazia nao inventa confirmacao` | não |

  **O que prova que a mutação isolou a camada não é a contagem, é a mensagem de cada vermelho.** As
  quatro caíram em asserção de **contagem** (`expected: <1> but was: <0>`, e `<1>` contra `<2>`).
  Nenhuma caiu numa asserção de estado da fila — as que dizem `"a linha foi mexida"` e
  `"so o confirmado sai da fila"` ficaram verdes. A classificação e a preservação são camadas
  distinguíveis.

  Reversão: `grep -rl "MUTACAO" apps/android/src` → `0`, conferida **rodando**: 10 testes, 0 falhas,
  relatório `2026-09-18T10:41:17.130Z`.

## 2. A decisão de reagendar

- [x] 2.1 Extrair a decisão de `doWork` para função pura sobre `ResumoDoEnvio`: `retry` quando
  `semRede > 0 || transitorios > 0`, `success` caso contrário. Verificar com teste de JVM que cobre os
  quatro casos — nada pendente, só sem rede, só transitório, só definitivo.
- [x] 2.2 `doWork` passa a usar a função, e o `output` do `WorkSpec` ganha a contagem de transitórios.
  Verificar que `:apps:android:testDebugUnitTest` e `:apps:android:assembleDebug` concluem.
- [x] 2.3 **Ver falhar (P9):** mutar a decisão para `success` quando há transitório — o comportamento
  de hoje. Confirmar que cai **só** o caso do transitório em 2.1, e que o caso do definitivo e o do
  sem-rede continuam verdes. Reverter e conferir rodando.

  **Confirmado, e a previsão bateu desta vez.** Caiu exatamente um: `falha do servidor pede tentativa`.
  `recusa definitiva nao pede tentativa`, `sem rede pede tentativa`, `nada pendente nao pede tentativa`
  e o canário continuaram verdes — `15 tests completed, 1 failed`. Revertida e conferida rodando;
  `assembleDebug` conclui.

## 3. A costura de transporte

- [x] 3.1 `doWork` delega a uma função interna que recebe o `HttpClientEngine`; produção passa o padrão.
  Nada da decisão de enviar-e-apagar se move (decisão 4 do `design.md`). Verificar que a suíte de
  unidade do Android continua verde e que `assembleDebug` conclui.
- [x] 3.2 Guarda de vacuidade da costura (P13): teste instrumentado que roda a passada com a fila
  **vazia** e afirma que o `MockEngine` não recebeu pedido nenhum. Sem ela, um teste que mede cabeçalho
  passa contra um caminho que nunca envia.

## 4. O segundo membro, no que o aparelho decide

- [x] 4.1 Teste instrumentado `SegundoMembroInstrumentedTest`: pendente de uma organização gravado no
  Room real sob a credencial de A; o caminho de saída é chamado; a credencial de B é guardada; a passada
  roda com `MockEngine`. Verificar que o `Authorization` recebido é **o de B**, nomeando qual credencial
  chegou, e que a fila drenou.
- [x] 4.2 Segundo cenário no mesmo teste: com a credencial de A ainda guardada, a rota responde 403 —
  vínculo revogado. Verificar que o pendente **fica**, e que a passada seguinte, já com a credencial de
  B, o envia. É o requisito "o usuário revogado não consegue enviá-lo, e outro membro consegue",
  medido em vez de inferido.
- [x] 4.3 **Ver falhar (P9):** mutar a fiação para capturar a credencial uma vez, antes da passada, em
  vez de a cada chamada — que é a forma que o defeito teria. Confirmar que 4.1 e 4.2 caem e que a guarda
  de vacuidade 3.2 **não** cai. Reverter e conferir rodando.

  **A mutação como escrita não derrubou nada, e isso é o achado (P7).** Capturar uma vez por passada é
  equivalente a ler por chamada: a credencial não muda no meio de uma passada. A tarefa — e a decisão 5
  do `design.md` — descreviam o mecanismo errado.

  A mutação que **isola** a camada é a do cache de processo: a credencial é fixada na primeira passada
  e reusada. Sob ela, no `2511FPC34G`:

  | Cenário | Caiu? | Mensagem |
  |---|---|---|
  | `o_pendente_de_um_membro_sobe_com_a_credencial_de_outro` | **sim** | `expected:<[Bearer …-b]> but was:<[Bearer …-a]>` |
  | `recusa_ao_membro_revogado_preserva_o_pendente_para_o_seguinte` | **sim** | `expected:<[Bearer …-a, Bearer …-b]> but was:<[Bearer …-a, Bearer …-a]>` |
  | `fila_vazia_nao_produz_pedido_nenhum` | não | — |

  As duas caíram na asserção do **cabeçalho**, e nenhuma na de estado da fila. A guarda de vacuidade
  sobreviveu, que é o conjunto disjunto exigido. Reversão: `grep -rl "MUTACAO" apps/android/src` → `0`,
  conferida rodando — 3 testes, 0 falhas.

## 5. O que fica sem verificação, com dono

- [x] 5.1 Escrever em `docs/cobertura-envio-distingue-recusa-transitoria.md` como cada verificação foi
  vista falhar, os conjuntos de cenários que caíram, e a seção "o que ainda não foi verificado".
- [x] 5.2 Registrar ali, com dono e fatia-limite, o que continua fora: **duas contas reais na mesma
  organização, contra o servidor de produção** — o que 4.1 mede é o aparelho, e a recusa por não-membro
  é do servidor, verificada em `:apps:api:test` e nunca com as duas pontas juntas. **Dono:** mantenedor.
  **Fatia-limite:** a primeira que tratar aparelho compartilhado entre professores. Registrar também o
  408/429 fora de escopo (Non-Goals do `design.md`).
- [x] 5.3 Atualizar `docs/cobertura-slice-4b-outbox-de-resultado.md` §5.1.2 **sem apagar o texto
  antigo** (P7): os dois itens ganham a linha do que esta mudança fechou e do que restou, apontando
  para o documento novo.

## 6. Verificação de fechamento

- [x] 6.1 Rodar o comando cheio do CI (P5): `./gradlew build --rerun-tasks` e
  `./gradlew :apps:android:connectedDebugAndroidTest --rerun-tasks`. Registrar a contagem de testes e o
  `timestamp` de cada relatório, filtrando por timestamp e não somando o diretório (P3).

  | Comando | Resultado | Janela do relatório |
  |---|---|---|
  | `./gradlew build --rerun-tasks` | 176 de 176 tasks, **1417 testes, 0 falhas** | `2026-09-18T11:01:38`–`11:02:26` |
  | `./gradlew :apps:android:connectedDebugAndroidTest --rerun-tasks` | **74 testes, 0 falhas**, no `2511FPC34G` | `2026-09-18T11:04:16` |
  | `./gradlew -p buildSrc test --rerun-tasks` | verde | passo novo do `ci.yml` |

  Os 1417 são +8 sobre os 1409 medidos antes desta mudança, e os 8 estão nomeados: 3 cenários novos em
  `EnvioDeResultadosTest` e 5 em `ValeTentarDeNovoTest`. Os 74 instrumentados são +6 sobre os 68 da
  fatia anterior — os 3 desta mudança e os 3 de `GravacaoNoFioPrincipalInstrumentedTest`, que entraram
  depois daquela contagem.
- [x] 6.2 Conferir que nenhuma mutação ficou na árvore: `grep -rn "MUTACAO" apps packages buildSrc`
  vazio, e `git status` sem arquivo inesperado.
