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
- [x] 1.4 **Correção de escopo, achada ao preparar a 3.1.** A 1.2 e a 1.3 chavearam só por `examId`,
  como o design original dizia. Mas o aparelho é compartilhado entre escolas
  (`DeviceSession.sair`), e `examId` não tem contrato de unicidade entre organizações — a mesma
  distinção que `ScanActivity.EXTRA_SHORT_ID` já registra para o roster. A chave passou a ser
  composta (`organizacao, exam_id`), decisão tomada com o mantenedor e registrada em `design.md`
  antes desta correção. Verificação: teste novo
  `o_mesmo_examId_em_outra_organizacao_nao_e_o_mesmo_caderno`; `build` e `connectedDebugAndroidTest`
  sem filtro repetidos, sem regressão.

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
- [x] 2.2 A tela de escaneamento escreve o caderno corrente no `onStop`, e não a cada quadro (design,
  decisão 1). `ScanActivity` lê o caderno guardado no `onCreate`, antes do primeiro quadro, e passa
  como `cadernoInicial`. A escrita mora em `guardarCadernoEmAndamento` (`Dispatchers.IO` +
  `NonCancellable`, mesmo padrão de `gravarEAgendar`), fora da `Activity`, para ser chamável do fio
  principal sem uma `Activity` real. Verificação:
  `GuardarCadernoNoFioPrincipalInstrumentedTest.guardar_a_partir_do_fio_principal_nao_estoura_e_o_caderno_chega_ao_disco`
  (`runOnMainSync`, base aberta como a produção abre); `connectedDebugAndroidTest` sem filtro:
  94 → 95 testes, 0 falhas.
- [x] 2.3 Trocar de aluno antes do `onStop` guarda o caderno do novo aluno, substituindo a linha
  anterior, sem misturar os dois. Verificação: teste do cenário "Trocar de aluno antes de fechar
  continua substituindo o caderno" — `cadernoAtual` já reflete só o último aluno, porque
  `ScanSession.caderno` continua sendo a variável única de sempre (decisão 4 da 5b-2); quem escreve
  no `onStop` (2.2) nunca vê o caderno do aluno anterior.

## 3. Sair não apaga o caderno guardado

- [x] 3.1 Teste que guarda um caderno, executa `DeviceSession.sair`, e confere que o caderno
  continua lido depois — o cenário "Sair não apaga o caderno em andamento". Estendido
  `ApagamentoLocalInstrumentedTest` (`sair_apaga_referencia_do_disco_e_preserva_o_pendente_e_o_caderno`
  e o par de revogação), no mesmo lugar que já confere pacote/visão/roster/pendente lado a lado —
  "uma asserção que só olhasse o pendente não distinguiria preservação de nunca ter apagado nada"
  vale igual para o caderno.
- [x] 3.2 **Ver falhar** (M-sair, `rigorous.md` §3, "introduza um erro de propósito"). Acrescentado,
  com `// MUTACAO`, um parâmetro opcional `mutacaoApagarCaderno: (() -> Unit)? = null` em
  `DeviceSession.sair` (default nulo — nenhum dos ~40 outros chamadores em `DeviceSessionTest`
  precisou mudar), exercitado só no teste, apagando a linha via SQL direto na base de teste.
  - **Previsto:** cai só `sair_apaga_referencia_do_disco_e_preserva_o_pendente_e_o_caderno`, e
    nenhum outro cenário de `sair` (visão, pacote, roster, pendente) muda.
  - **Real:** exatamente isso — 1 falha em 94, `AssertionError: sair apagou o caderno em andamento
    expected:<Caderno(...)> but was:<null>`, na asserção do caderno; as anteriores, na mesma
    execução do mesmo teste, já tinham passado.

  Revertido (`DeviceSession.kt` para `git checkout --`, a linha da chamada removida do teste),
  `git grep MUTACAO` deu vazio, e `connectedDebugAndroidTest` sem filtro voltou a 96 testes, 0
  falhas.

## 4. Fechamento

- [x] 4.1 `git grep MUTACAO`, fora de `build/`, `node_modules/`, `docs/`, `openspec/` e
  `rigorous.md`, dá `0`, e toda reversão foi rodada (P10).
- [x] 4.2 Comando cheio local, com contagens e `timestamp` comparados com a 0.1 (P2, P3, P5):
  - `./gradlew build --rerun-tasks` e `buildSrc`;
  - `connectedDebugAndroidTest`, sem filtro;
  - `vitest` e o build do web.
- [x] 4.3 `docs/cobertura-slice-5b-3-guardar-a-parcial-e-o-caderno.md`:
  - como a 3.2 foi vista falhar, com o previsto e o real;
  - a seção "o que ainda não foi verificado", com o encerramento anômalo do processo sem `onStop`
    (design, riscos) e a leitura em aparelho real.
- [x] 4.4 `node tools/divida/divida.mjs` de novo, comparado com a 0.2 (P27): nenhuma linha nova
  vence nesta fatia.
- [ ] 4.5 PR contra `main`, e o CI **lido no destino**: o run, o `headSha` e os passos (P2, P26).
