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

## Fechamento (tarefa 5.2/5.3)

_A preencher ao final da implementação._
