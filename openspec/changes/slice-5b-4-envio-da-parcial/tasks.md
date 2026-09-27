## 0. Antes do primeiro commit

- [x] 0.1 Medir a linha de base na árvore de `main`:
  - `./gradlew build --rerun-tasks` e `./gradlew -p buildSrc test --rerun-tasks`;
  - `./gradlew :apps:android:connectedDebugAndroidTest`, sem filtro, num aparelho/emulador real;
  - `npx vitest run` em `apps/web`, se aplicável ao que esta mudança tocar.

  Anotar em `docs/cobertura-slice-5b-4-envio-da-parcial.md` a contagem de testes por task, o
  `timestamp` de cada relatório e o número de tasks **executadas** (P2, P3 do `rigorous.md`).
  Verificação: os relatórios são desta sessão, com o `timestamp` dentro da janela da execução.
- [x] 0.2 Registrar a saída de `node tools/divida/divida.mjs`. Verificação: `exit 0`, sem linha
  vencida na fatia corrente.

## 1. Contrato KMP: `ApuracaoParaEnvio` e o discriminador no fio

- [x] 1.1 Criar `sealed interface ApuracaoParaEnvio` em `packages/domain` (design, decisão 2), com os
  casos `Completa(score: ObjectiveScore)` e `Parcial(score: PartialScore)`. Sem `@Serializable` —
  nem `ResultadoPendente` nem este tipo são serializados como um todo em nenhum ponto do caminho
  (design, decisão 2, nota sobre serialização); nenhum campo de `ObjectiveScore` ou `PartialScore`
  muda. Verificação: `./gradlew :packages:domain:build` e a suíte de `scoring` (`jvmTest`,
  `jsNodeTest`, `testAndroidHostTest`) passam sem nenhuma asserção alterada, com a mesma contagem da
  0.1; teste novo que constrói um `ApuracaoParaEnvio.Completa` e um `.Parcial` e confere, por um
  `when` exaustivo, que cada um expõe o `score` esperado.
- [x] 1.2 Acrescentar `val partial: Boolean = false` a `ResultSubmissionDto`
  (`packages/domain/.../transport/ResultDto.kt`), aditivo e com default (design, decisão 3).
  Verificação: teste que decodifica um corpo **sem** o campo `partial` (o formato de hoje) e confere
  que `partial == false` — nenhum corpo já emitido deixa de decodificar.

## 2. Servidor: as duas fases da validação aceitam parcial

- [x] 2.1 Dividir a reconstrução de `ResultDto.paraNota()` em duas: o caminho `partial == false`
  fica **byte a byte igual ao de hoje** (mesma função, mesmas guardas, nenhuma asserção de teste
  existente muda); o caminho `partial == true` valida os outcomes objetivos com as mesmas guardas
  (soma bate, sem item repetido, sem desencontro pendência/evidência) e exige `closed == false`
  incondicionalmente, sem a guarda `closed == pending.isEmpty()` (design, decisão 4, Fase 1).
  Verificação: teste que um corpo `partial=true, closed=true` é recusado; teste que um corpo
  `partial=true, closed=false` com toda objetiva sem pendência é aceito nesta fase (a fase seguinte é
  quem decide se ele fecha com o pacote).
- [x] 2.2 Estender `conferirProveniencia` (`apps/api/.../ProvenienciaDoResultado.kt`) para, quando
  `partial == true`, conferir que a variante declarada tem ao menos um item `ESSAY`, e então derivar
  `awaiting` e `objectiveMaxScore` do pacote (mesmo filtro `PackageItem.kind == ESSAY` e mesma soma de
  rubrica que `ObjectiveScoring.scorePartial` já faz — design, decisão 4, Fase 2), construindo o
  `PartialScore` real por cima do que as duas fases confirmaram. Verificação: teste que uma parcial
  contra uma variante **sem** discursiva é recusada como proveniência incoerente, com o motivo
  nomeando a variante; teste que uma parcial válida produz um `PartialScore` cujas guardas de `init`
  passam.
- [x] 2.3 `ResultQueries.record()` passa a receber `nota: ApuracaoParaEnvio` (design, decisão 5), com
  um `when` que extrai `packageHash/variantId/points/maxScore/closed/outcomes` — `Completa` como
  hoje, `Parcial` com `points = objectivePoints`, `maxScore` da prova, `closed = false`. Nenhuma
  coluna nova, nenhuma migration. Verificação: teste que grava uma parcial e confere as colunas de
  `grading_result` e `answer_observation` (só as objetivas); teste que grava uma `ObjectiveScore`
  continua produzindo exatamente as mesmas linhas de antes.
- [x] 2.4 Atualizar a rota `POST .../results` para os dois novos ramos de recusa (proveniência
  incoerente de parcial; coerência interna de parcial), com a mesma forma de 400 já usada pelos
  outros dois. Verificação: um cenário por caminho de recusa novo, no nível da rota
  (request/response), ao lado dos já existentes de pacote/variante.

## 3. Aparelho: o outbox aceita `ApuracaoParaEnvio`

- [x] 3.1 `ResultadoPendente.nota` passa de `ObjectiveScore` para `ApuracaoParaEnvio`. `corpoDoEnvio()`
  (`apps/android/.../api/ResultadoDto.kt`) ganha um `when`: `Completa` monta o corpo com os mesmos
  campos de hoje, mais `partial = false` explícito (`encodeDefaults = true` já em vigor emite o
  campo em todo corpo — design, decisão 3, nota sobre o byte); `Parcial` monta `points`, `maxScore`,
  `closed = false`, `observations` só objetivas e `partial = true`. Verificação: atualizar
  `ResultadoDtoTest` (os dois testes que prendem o corpo por igualdade literal e por conjunto de
  chaves) para incluir `partial`, e confirmar que passam; teste novo do corpo produzido para
  `Parcial` contra o schema que o servidor aceita (2.1/2.2).

## 4. Aparelho: o caderno completo dispara a entrega

- [ ] 4.1 `Caderno` ganha `val entregue: Boolean = false` (`@Serializable`, como o resto da classe).
  `depoisDe` preserva a regra de "capturada não volta atrás" e não desliga `entregue` sozinho.
  Verificação: teste de serialização round-trip incluindo o campo novo (estende o teste da 1.1 da
  5b-3); teste que `entregue` permanece `true` num caderno já entregue que recebe mais um quadro.
- [ ] 4.2 `ApuracaoNova` vira `sealed interface` com `Completa(reading, score: ObjectiveScore)` e
  `DeCaderno(aluno: String, score: PartialScore)` (design, decisão 1). `ScanSession.onFrame`, no ramo
  `comDiscursiva`, passa a devolver `DeCaderno` exatamente na transição
  `capturadas == esperadas && !entregue`, marcando `entregue = true` no `Caderno` guardado na mesma
  passada — e `null` em todo o resto, preservando "nada é gravado enquanto incompleto". Verificação:
  um teste por cenário novo de `scan-session` — "O caderno completo é entregue para gravação",
  "Confirmar um caderno já completo não duplica o envio", "Caderno incompleto substituído por outro
  aluno não é entregue" — e os nove cenários existentes da capacidade continuam passando sem
  alteração de asserção.
- [ ] 4.3 `ScanActivity.gravar` (ou o ponto equivalente que chama `pendentes.guardar`) ganha um `when`
  sobre `ApuracaoNova`, construindo `ResultadoPendente(nota = ApuracaoParaEnvio.Completa(...))` ou
  `.Parcial(...)` conforme o caso, com `studentToken` vindo de `reading.payload.studentToken` num
  caso e de `aluno` no outro (mesma regra "vazio vira nulo" dos dois). Verificação: teste de
  instrumentação — caderno completo de uma prova com discursiva entra na fila de `result-sync` e
  aparece em `pendentesDa(organizacao)`; trocar de aluno antes de completar não produz pendente
  nenhum (cenário "Caderno incompleto substituído por outro aluno não é entregue", de ponta a ponta).

## 5. Verificação de ponta a ponta e fechamento

- [ ] 5.1 Cenário completo, num teste de instrumentação: escanear gabarito + todas as discursivas de
  um aluno de uma prova com discursiva, fechar e reabrir o aplicativo no meio (retomando o caderno da
  5b-3), completar, confirmar que exatamente um pendente é criado, enviá-lo (mock do servidor ou API
  de teste) e confirmar que o servidor grava `grading_result` com `closed = false` e as observações
  objetivas, sem nenhuma linha para as discursivas.
- [ ] 5.2 Repetir a suíte completa da 0.1 e comparar contagem de testes e resultado; nenhuma
  regressão em `scoring`, `result-sync`, `scan-session` ou nas capacidades não tocadas por esta
  mudança. Atualizar `docs/cobertura-slice-5b-4-envio-da-parcial.md` com o fechamento.
- [ ] 5.3 Reexecutar `node tools/divida/divida.mjs` e reconciliar qualquer linha que esta mudança
  tenha alcançado (nenhuma esperada, por `proposal.md`; confirmar que continua assim).
