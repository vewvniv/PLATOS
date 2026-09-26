# Cobertura — `slice-5b-2-a-nota-objetiva-parcial`

Os artefatos estão em `openspec/changes/slice-5b-2-a-nota-objetiva-parcial/`. Este documento registra
**como** cada verificação foi vista falhar, e não que ela passa (`rigorous.md` §8). As datas são UTC.

## 0. Linha de base

**0.1** — Sobre `main` (`e8db489`) e o commit da proposta (`7eb31f6`), que só acrescenta arquivos em
`openspec/changes/`. De 2026-09-26T11:11:05Z a 11:15:46Z:

| Comando | Resultado |
|---|---|
| `./gradlew build --rerun-tasks` | `BUILD SUCCESSFUL in 2m 20s`, **183 de 183 tasks executadas**, de 11:11:05Z a 11:13:27Z |
| `./gradlew -p buildSrc test --rerun-tasks` | 1 de 1, `timestamp` 11:14:38Z |
| `./gradlew :apps:android:connectedDebugAndroidTest`, sem filtro, no `platos-atd34` | 89 testes, 0 falhas, 2 pulados, `timestamp` 11:15:44Z. O console diz "Finished 91 tests", como na 5b-1; a âncora é o XML (P3) |
| `npx vitest run` em `apps/web` | 18 de 18, 11:11:13Z |

Relatórios do `build`, lidos pelo XML de cada task (`relatorios.mjs`, no scratchpad), com cada
`timestamp` dentro da janela dele:

| Task | Testes | `timestamp` |
|---|---|---|
| `apps/android` `testDebugUnitTest` | 320 | 11:12:30Z .. 11:12:34Z |
| `apps/android` `testReleaseUnitTest` | 320 | 11:12:16Z .. 11:12:26Z |
| `apps/api` `test` | 168 | 11:13:07Z .. 11:13:22Z |
| `packages/domain` `jvmTest` | 393 | 11:12:58Z .. 11:13:01Z |
| `packages/domain` `jsNodeTest` | 384 | 11:13:03Z .. 11:13:05Z |
| `packages/domain` `testAndroidHostTest` | 384 | 11:12:51Z .. 11:12:53Z |

Os relatórios de `buildSrc` e do instrumentado estavam **velhos** antes dos passos próprios deles
(09:41:15Z e 09:42:16Z, do fechamento da 5b-1), e foram lidos de novo depois.

**0.2** — `node tools/divida/divida.mjs`, `exit 0`, "nenhuma linha vencida: 21 linhas lidas". "fatia
corrente: 5b, de slice-5b-2-a-nota-objetiva-parcial (ativa), …". Sob "vence nesta fatia (5b)":
- `Acurácia em manuscrito` (`5`);
- `Modo degradado (§10) não existe` (`5`);
- `O limiar do OMR foi apurado sobre um aparelho e uma impressora` (`5`).

As duas linhas `6` (`A região discursiva ainda não passou pelo aparelho nem pelo papel` e `A folha de
teste de impressão não aprova a região discursiva que a prova imprime`) aparecem "em dia".
