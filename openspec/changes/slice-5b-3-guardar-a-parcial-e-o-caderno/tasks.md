## 0. Antes do primeiro commit

- [x] 0.1 Medir a linha de base na árvore de `main`:
  - `./gradlew build --rerun-tasks` e `./gradlew -p buildSrc test --rerun-tasks`;
  - `./gradlew :apps:android:connectedDebugAndroidTest`, sem filtro, no `platos-atd34`;
  - `npx vitest run` em `apps/web`.

  Anotar em `docs/cobertura-slice-5b-3-guardar-a-parcial-e-o-caderno.md` a contagem de testes por
  task, o `timestamp` de cada relatório e o número de tasks **executadas** (P2, P3). Verificação: os
  relatórios são desta sessão, com o `timestamp` dentro da janela da execução.
- [x] 0.2 Registrar a saída de `node tools/divida/divida.mjs`. Verificação: `exit 0`, sem linha
  vencida.

## 1. Contrato de persistência

- [x] 1.1 `@Serializable` em `PartialScoringOutcome`, `PartialScore`, `AwaitingEssay`,
  `PendingQuestion`, `PendingReason`, `QuestionOutcome` e `QuestionAnswer` (`packages/domain`), e em
  `Caderno`, `RegiaoDoCaderno` e `EstadoDaRegiao` (Android) — num commit de contrato próprio, antes
  do consumidor (regra 1 do `CLAUDE.md`; design, decisão 2, sub-decisão sobre DTO). Verificação:
  `./gradlew :packages:domain:build` e a suíte de `scoring` (`jvmTest`, `jsNodeTest`,
  `testAndroidHostTest`) passam sem nenhuma asserção alterada, com a mesma contagem da 0.1; e um
  teste novo que serializa e desserializa uma `PartialScore` e um `Caderno` (com todos os estados de
  região e todos os quatro casos de `QuestionAnswer`) e confere igualdade com o original.
- [x] 1.2 `CadernoEntity`, `CadernoDao` e a `RoomDatabase` nova (`caderno.db`), num commit próprio,
  antes de qualquer consumidor (regra 1 do `CLAUDE.md`). Uma linha por `exam_id`, coluna `corpo` com
  o `Caderno` serializado (`kotlinx.serialization`), gravação com `@Upsert` (design, decisão 2, e a
  guarda substitui a linha inteira do mesmo jeito que `ScanSession.caderno` substitui em memória).
  Verificação: teste de instrumentação — guardar duas vezes para o mesmo `examId` deixa exatamente
  uma linha, com o `corpo` da segunda (`guardar_duas_vezes_para_a_mesma_prova_deixa_uma_linha_so`).
- [x] 1.3 A interface `CadernosGuardados`, com `guardar(examId, caderno)` e `ler(examId): Caderno?`,
  implementada em Room (`CadernosEmRoom`) recebendo o `Dao` — não o `Context` —, no mesmo padrão de
  `ResultadosEmRoom` (design, decisão 2). Verificação: teste de instrumentação sobre base em arquivo,
  fechada e reaberta — `o_caderno_volta_do_disco_identico_ao_que_entrou` — e o caso de nenhum caderno
  guardado ainda. O uso por uma implementação em memória nos testes de `ScanSession` é a 2.1, que
  consome a interface.

## 2. `ScanSession` retoma e guarda o caderno em andamento

- [x] 2.1 Ao abrir o escaneamento de uma prova com discursiva, a sessão consulta o caderno guardado
  para este `examId`, e nasce dele quando existir, em vez de vazio. Verificação: um teste por
  cenário da MODIFIED de `scan-session` — "O aplicativo fecha no meio da leitura de um aluno" e "O
  caderno guardado sobrevive ao fechamento do aplicativo" —, guardando um caderno, criando uma
  `ScanSession` nova para o mesmo pacote e conferindo que o primeiro estado já reflete o aluno, as
  regiões e a parcial guardados. **Achado ao implementar:** `onPermission(granted = true)`
  sobrescrevia `state` incondicionalmente, o que apagaria o caderno retomado na mesma chamada que
  `onCreate` já faz — corrigido para respeitar `holdsResult`, e o teste que prova isso chama
  `onPermission` depois de retomar.
- [ ] 2.2 A tela de escaneamento escreve o caderno corrente no `onStop`, e não a cada quadro (design,
  decisão 1). Verificação: a decisão de o quê escrever é testável isolada do ciclo de vida do
  Android — um teste chama a função de persistência diretamente, sem `Activity` real —, e o build
  compila com a chamada real ligada ao `onStop`.
- [x] 2.3 Trocar de aluno antes do `onStop` guarda o caderno do novo aluno, substituindo a linha
  anterior, sem misturar os dois. Verificação: teste do cenário "Trocar de aluno antes de fechar
  continua substituindo o caderno" — `cadernoAtual` já reflete só o último aluno, porque
  `ScanSession.caderno` continua sendo a variável única de sempre (decisão 4 da 5b-2); quem escreve
  no `onStop` (2.2) nunca vê o caderno do aluno anterior.

## 3. Sair não apaga o caderno guardado

- [ ] 3.1 Teste que guarda um caderno, executa `DeviceSession.sair`, e confere que o caderno
  continua lido depois — o cenário "Sair não apaga o caderno em andamento".
- [ ] 3.2 **Ver falhar** (M-sair, `rigorous.md` §3 decisão 7). Acrescentar, com `// MUTACAO`, uma
  chamada que apaga a base do caderno dentro de `DeviceSession.sair`.
  - **Previsto:** cai só o teste da 3.1, e nenhum outro cenário de `sair` (visão, pacote, roster)
    muda.
  - **Real:** anotado ao lado do previsto.

  Reverter, `grep MUTACAO` e rodar de novo.

## 4. Fechamento

- [ ] 4.1 `git grep MUTACAO`, fora de `build/`, `node_modules/`, `docs/`, `openspec/` e
  `rigorous.md`, dá `0`, e toda reversão foi rodada (P10).
- [ ] 4.2 Comando cheio local, com contagens e `timestamp` comparados com a 0.1 (P2, P3, P5):
  - `./gradlew build --rerun-tasks` e `buildSrc`;
  - `connectedDebugAndroidTest`, sem filtro;
  - `vitest` e o build do web.
- [ ] 4.3 `docs/cobertura-slice-5b-3-guardar-a-parcial-e-o-caderno.md`:
  - como a 3.2 foi vista falhar, com o previsto e o real;
  - a seção "o que ainda não foi verificado", com o encerramento anômalo do processo sem `onStop`
    (design, riscos) e a leitura em aparelho real.
- [ ] 4.4 `node tools/divida/divida.mjs` de novo, comparado com a 0.2 (P27): nenhuma linha nova
  vence nesta fatia.
- [ ] 4.5 PR contra `main`, e o CI **lido no destino**: o run, o `headSha` e os passos (P2, P26).
