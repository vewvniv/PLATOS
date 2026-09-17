## 1. Contrato em KMP, antes dos consumidores

- [x] 1.1 `ObjectiveScore` ganha o resultado por questão — item, resposta lida, pontos do item, pontos
  rendidos —, produzido dentro do mesmo laço que apura o total. Verificar com teste em `commonTest` que
  a lista cobre todas as questões da variante e que a soma dos pontos rendidos é igual a `points`.
- [x] 1.2 Guarda de construção para a coerência entre evidência e total, na mesma forma do
  `require(points in 0..maxScore)` que já existe. Verificar com teste que constrói um `ObjectiveScore`
  incoerente e observa a exceção com a mensagem que nomeia a divergência.
- [x] 1.3 Cenário de questão pendente e de questão em branco na evidência: pendente rende zero e
  continua na lista de pendências, em branco rende zero e **não** aparece entre as pendências. Verificar
  pelos dois testes, com os conjuntos conferidos separadamente.
- [x] 1.4 Ajustar `ScanSession` e `NotaApresentada` ao contrato novo. Verificar que
  `:packages:domain:allTests` e `:apps:android:testDebugUnitTest` passam, e registrar o `timestamp` do
  relatório de cada um.
- [x] 1.5 **Ver falhar (P9):** mutar a apuração para omitir uma questão da evidência mantendo o total
  correto, confirmar que 1.1 fica vermelho e que 1.2 e 1.3 **não** caem — a mutação tem de isolar a
  camada —, reverter, e conferir a reversão **rodando** (P10). Registrar quais cenários caíram.

## 2. Dependências

- [x] 2.1 Acrescentar Room, WorkManager e o processador de anotações ao catálogo de versões. Verificar
  que `./gradlew :apps:android:assembleDebug` conclui e que o APK é produzido.

## 3. Servidor: as tabelas do resultado

- [x] 3.1 Migration com `grading_result` e `answer_observation`, chaveadas por `organization_id`, com
  `(exam_id, student_token, revision)` e com a chave de idempotência da captura. Verificar aplicando a
  migration em banco real e conferindo as colunas e constraints pelo catálogo.
- [x] 3.2 RLS habilitada e forçada nas duas, com política por `membership`. Verificar que
  `ConnectionRoleTest` passa e que um teste novo de isolamento reprova leitura de resultado de outra
  organização.
- [x] 3.3 Declarar finalidade e classe de retenção **B** nas duas tabelas. Verificar que
  `RetentionDeclarationTest` passa, e observá-lo **vermelho** primeiro, removendo a declaração de uma
  delas (P9) — sem isso a guarda não prova nada sobre as tabelas novas.
- [x] 3.4 Trava de append-only: revisão nova nunca altera nem apaga a anterior. Verificar com teste que
  tenta o `update` e o `delete` e observa a recusa **pelo motivo certo**, na forma que
  `ExamPackageImmutabilityTest` já usa.

## 4. Servidor: a rota de escrita

- [x] 4.1 Rota autenticada que recebe o resultado e a evidência por questão e grava as duas tabelas numa
  transação. Verificar com teste de rota que o resultado gravado é legível de volta e que nada parcial
  fica gravado quando a evidência é inválida.
- [x] 4.2 Recusa sem credencial válida e recusa de prova de organização de que o usuário não é membro,
  cada uma com o seu código e o seu motivo. Verificar com dois testes, conferindo o **motivo** e não só
  a recusa (P9).
- [x] 4.3 Idempotência: mesmo identificador de captura não cria registro novo e responde como da
  primeira vez. Verificar com teste que envia duas vezes e conta as linhas — contagem sobre o banco, não
  sobre o retorno da rota (P13).
- [x] 4.4 Revisão: identificador de captura diferente para o mesmo `(exam_id, student_token)` grava
  revisão nova, ela vira a corrente e a anterior continua legível. Verificar com teste que lê as duas.
- [x] 4.5 **Ver falhar (P9):** remover a trava de idempotência e confirmar que 4.3 cai e 4.4 **não** —
  os dois conjuntos têm de ser disjuntos, ou a mutação não diz qual trava segurou. Reverter e rodar.

## 5. Aparelho: o resultado durável

- [x] 5.1 Base Room com `resultado_pendente`, gravada assim que a nota é apurada, antes de qualquer
  rede. Verificar com teste instrumentado que o resultado está no armazenamento depois de apurar com o
  aparelho offline.
- [x] 5.2 Folha recusada não grava nada. Verificar com teste que apura folha de outra prova e confere
  que a tabela continua **vazia** — com canário, para que a asserção não passe por vacuidade (P13).
- [x] 5.3 O resultado gravado leva prova, pacote, variante, o token lido do QR, o total, as pendências e
  a evidência por questão, e **não** leva nome, turma nem matrícula. Verificar lendo a linha de volta e
  conferindo a ausência campo a campo.
- [x] 5.4 O resultado sobrevive ao fim do processo. Verificar com teste instrumentado que fecha e reabre
  a base, conferindo a **âncora** do dado lido e não só a presença de uma linha (P3).

## 6. Aparelho: envio, confirmação e expurgo

- [x] 6.1 Envio dos pendentes por `WorkManager`, sobre o `ClienteApi`, com a credencial da sessão.
  Verificar com teste que o corpo enviado bate com o que a rota do servidor aceita — o literal de JSON
  prendido dos dois lados, como `ObtencaoDeRosterTest` já faz com o roster.
- [x] 6.2 Sem rede, o pendente permanece: nada é descartado, alterado nem marcado como enviado.
  Verificar com teste que falha o envio e confere a linha intacta.
- [x] 6.3 Confirmada a gravação, o pendente é apagado. Verificar **conferindo o armazenamento** depois
  da confirmação, e não o retorno da função que apaga.
- [x] 6.4 Falha de rede, tempo esgotado e recusa do servidor **não** apagam o pendente. Verificar com um
  teste por caso, conferindo o motivo de cada um.
- [x] 6.5 Um resultado recusado não bloqueia a fila. Verificar com teste de dois pendentes em que o
  primeiro é recusado e o segundo sobe.
- [x] 6.6 **Ver falhar (P9):** mutar o expurgo para apagar antes da confirmação, reverter e rodar a
  reversão. **O conjunto previsto nesta tarefa estava errado, e fica dito em vez de corrigido em
  silêncio (P7):** ela dizia "6.3 e 6.4 caem e 6.2 não cai". Apagar antes da confirmação derruba
  todos os cenários de preservação — 6.2, 6.4 e 6.5 —, e **6.3 continua passando**, porque o
  confirmado segue sendo apagado. É esse o conjunto que prova o que a tarefa queria: o expurgo
  legítimo e o prematuro são códigos distinguíveis. Caíram `sem rede o pendente fica intacto`,
  `recusa do servidor nao apaga o pendente`, `recusa por vinculo revogado nao apaga o pendente` e
  `um recusado nao bloqueia o resto da fila`; `confirmado sai da fila` e todo o `DeviceSessionTest`
  ficaram de pé.

## 7. Sair, revogação e o pendente preservado

- [x] 7.1 `DeviceSession.sair` continua apagando roster, pacote e visão guardada, e **não** toca nos
  resultados pendentes. Verificar em aparelho que, depois de sair com pendente na base, `find files -type f`
  mostra a base Room **presente** e os três outros **ausentes** — as duas metades conferidas na mesma
  execução, ou a asserção não distingue "preservou" de "não apagou nada".
- [x] 7.2 Sair informa quantos resultados ainda não subiram. Verificar com teste da decisão, fora do
  `@Composable`, na fronteira que `IdAlunoDaFolha` já usa.
- [x] 7.3 O pendente preservado é escopado pela organização e sobe na sessão seguinte de um membro dela.
  **Fechada em 2026-09-18, em aparelho real.** O envio aconteceu na **abertura de sessão**, sem
  escaneamento nenhum — que é o requisito. Antes disso ele não tinha implementação: o único lugar que
  agendava envio era `ScanActivity`, e a falta foi encontrada aqui. **Uma metade não foi exercitada e
  fica dita:** quem reabriu a sessão foi o **mesmo** usuário, e não um segundo membro da organização.
  O escopo por organização está verificado em JVM (`a fila de uma organizacao nao leva a de outra`) e
  em aparelho (`o_pendente_de_outra_organizacao_nao_e_tocado`), e o caminho de código é o mesmo com
  outra credencial — mas isso é inferência, e não medição.
- [x] 7.4 A revogação do vínculo apaga roster, pacote e visão guardada daquela organização e **preserva**
  os pendentes. Verificar em aparelho, no mesmo caminho de revogação observada que a
  `slice-4b-roster-no-aparelho` já exercita, conferindo as duas metades na mesma execução.
- [x] 7.5 Recusa de envio por vínculo revogado não apaga o pendente. Verificar com teste que confere o
  **motivo** da recusa e, depois dela, a linha ainda na base.
- [x] 7.6 **Ver falhar (P9):** mutar o apagamento para levar também os pendentes, confirmar que 7.1 e 7.4
  caem e que 6.3 — o expurgo após confirmação — **não** cai. A mutação tem de isolar o caminho local: se
  6.3 cair junto, o expurgo legítimo e o apagamento indevido são o mesmo código, e nenhum dos dois está
  provado. Reverter e rodar a reversão.

## 8. Registro e verificação final

- [x] 8.1 Levar ao ADR-0003 a linha da decisão 1 do `design.md`: o fato nasce chaveado pelo token porque
  `student` não existia quando os fatos passaram a existir, e a resolução para `student_id` acontece por
  junção quando ele existir. Atualização ao lado da decisão, não substituição dela.
- [x] 8.2 Atualizar a tabela de ponto de não-retorno do §16 com dois itens (P20). **(a)** A retenção
  executável da classe B — anonimização e pedido de eliminação — **não** entra nesta fatia, e precisa de
  fatia-limite, custo e dono escritos. **(b)** A segunda frase da política §10.8 — "o encerramento de
  sessão ou a desinstalação do aplicativo eliminam a base local" — passa a divergir do comportamento:
  nem sair nem a revogação apagam resultado pendente (decisão 6 do `design.md`). **E o `[30]` entre
  colchetes deixa de ser teto de conveniência:** para o pendente que ficou sem entregador — usuário
  revogado, aparelho pessoal, nenhum outro membro da organização para enviá-lo — ele é o **único** limite
  de dado pessoal que não tem como sair do aparelho por sincronização. Dono: **jurídico externo**, levado
  pelo mantenedor, na mesma linha em que a classe H e o roster baixado já esperam parecer. O expurgo
  após sincronização confirmada entra e fica registrado como entregue. Esta mudança **não** edita a
  política.
- [x] 8.3 Escrever `docs/cobertura-slice-4b-outbox-de-resultado.md` com **como** cada verificação
  crítica foi vista falhar e quais cenários caíram em cada mutação, mais a seção do que **não** foi
  verificado e por quê (§8, P8).
- [x] 8.4 Rodar o comando **cheio** do CI, não o filtrado, incluindo a suíte instrumentada (P5).
  Registrar o comando exato e o `timestamp` do relatório que prova a execução (P2, P3).
- [x] 8.5 Conferir em aparelho real o fluxo de ponta a ponta. **Fechada em 2026-09-18**, cada elo
  observado onde ele termina (P26):
  - **Recusa:** folha da `prova-referencia-slice-1` contra o pacote da `slice-2` — a prova
    adversarial, mesmos itens, posições e gabarito. Recusada nomeando o `short_id`, e `databases/`
    continuou **vazio**: recusa não virou correção.
  - **Captura offline:** modo avião ligado, folha da `slice-2` escaneada, `0 de 40` fechada. Uma linha
    no outbox, com `student_token` nulo, `package_hash` igual ao do arquivo em cache, e **40**
    observações.
  - **Morte de processo:** `am force-stop`, PID confirmado morto, linha intacta.
  - **Envio:** na abertura de sessão, worker `60c6af82` em **3,85 s** de ida e volta; a execução
    seguinte, com a fila vazia, levou 0,06 s.
  - **No destino:** `grading_result` com `capture_id = d671e626-…`, `revision` 1, `origin` `omr`,
    `points` 0 de 40, `closed` verdadeiro; 40 linhas em `answer_observation` somando 0.
  - **A âncora do modelo offline:** `captured_at` 21:26:20 UTC e `created_at` 22:32:06 UTC — **1h06**
    entre apurar sem rede e gravar no servidor.
