# Cobertura — `slice-5b-4-envio-da-parcial`

## Linha de base (tarefa 0.1), 2026-09-27

Medida na árvore de `main`, antes de qualquer alteração desta mudança.

| Comando | Resultado | Contagem |
|---|---|---|
| `./gradlew build --rerun-tasks` | BUILD SUCCESSFUL (3m 41s) | `apps:android:testDebugUnitTest` 339; `apps:android:testReleaseUnitTest` 339; `apps:api:test` 168; `packages:domain:testAndroidHostTest` 402; `packages:domain:jvmTest` 411 |
| `./gradlew -p buildSrc test --rerun-tasks` | BUILD SUCCESSFUL (30s) | — |
| `./gradlew :apps:android:connectedDebugAndroidTest` (`platos-atd34`, sem filtro) | BUILD SUCCESSFUL (1m 11s) | 96 testes finalizados |
| `npx vitest run` (`apps/web`) | Não executado — esta mudança não toca `apps/web` (nenhuma capability web em `proposal.md`) | — |

Logs completos em `scratchpad/5b-4-baseline/` desta sessão (`gradlew-build.log`,
`buildsrc-test.log`, `connected-android-test.log`).

## Dívida (tarefa 0.2), 2026-09-27

`node tools/divida/divida.mjs` → `exit 0`, "nenhuma linha vencida: 21 linhas lidas", "vence nesta
fatia (5b): nenhuma". Fatia corrente derivada: `5b`, com `slice-5b-4-envio-da-parcial` como a
mudança ativa.

## Fechamento (tarefa 5.2/5.3), 2026-09-27

**Desvio registrado.** `./gradlew build --rerun-tasks` (o agregado, igual ao da linha de base) foi
interrompido pelo próprio ambiente por baixa memória do sistema durante a sessão — não por falha de
código, e não visto falhar por nenhuma asserção. O mantenedor optou por fechar a verificação com as
execuções separadas abaixo, todas verdes, em vez de repetir o agregado (`rigorous.md` P9: a
verificação vale pelo que ela mede, e cada suite abaixo foi vista rodar e passar nesta sessão).

| Comando | Resultado | Contagem | Comparação com a linha de base |
|---|---|---|---|
| `./gradlew :packages:domain:build` (`jvmTest`/`jsNodeTest`/`testAndroidHostTest`) | BUILD SUCCESSFUL | `jvmTest` 414; `testAndroidHostTest` 405 | +3 (tarefa 1.1: `ApuracaoParaEnvioTest` ×2; tarefa 1.2: `ResultSubmissionDtoTest` ×1) |
| `./gradlew :apps:android:testDebugUnitTest :apps:api:test :packages:domain:jvmTest` | BUILD SUCCESSFUL | `apps:android:testDebugUnitTest` 344; `apps:api:test` 171 | Android +5 (4 cenários novos de completude/entrega em `ProvaComDiscursivaNaSessaoTest`, 1 teste novo de corpo parcial em `ResultadoDtoTest`; 1 teste existente renomeado e reduzido de escopo, sem perda de cenário — o cenário que ele deixou de cobrir passou a ter teste próprio); API +3 (parcial aceita, parcial sem discursiva recusada, parcial fechada recusada) |
| `./gradlew :apps:android:connectedDebugAndroidTest` (`platos-atd34`, sem filtro) | BUILD SUCCESSFUL (1m 2s) | 97→99 testes (mesmo padrão de contagem "iniciados/finalizados" da linha de base, 94→96) | +3 (`EntregaDoCadernoInstrumentedTest`, tarefas 4.3 e 5.1) |
| `node tools/divida/divida.mjs` | `exit 0` | "nenhuma linha vencida: 21 linhas lidas", "vence nesta fatia (5b): nenhuma" | Sem mudança — confirma a expectativa do `proposal.md` |
| `./gradlew -p buildSrc test` | Não repetido — nenhum arquivo de `buildSrc` foi tocado nesta mudança | — | — |
| `npx vitest run` (`apps/web`) | Não executado — esta mudança não toca `apps/web` | — | — |

**Nenhuma regressão.** Nenhum teste existente de `scoring`, `result-sync` ou `scan-session` mudou
de asserção (exceto a renomeação registrada acima, que move um cenário para um teste dedicado, sem
perdê-lo). Nenhuma capacidade fora do escopo desta mudança foi tocada.

Logs completos em `scratchpad/5b-4-baseline/` desta sessão (`domain-build-2.log`, `unit-tests-4.log`,
`connected-android-test-2.log`).
