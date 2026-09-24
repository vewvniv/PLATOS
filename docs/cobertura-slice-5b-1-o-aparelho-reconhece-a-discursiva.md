# Cobertura — `slice-5b-1-o-aparelho-reconhece-a-discursiva`

Primeira das duas mudanças da 5b. Os artefatos estão em
`openspec/changes/slice-5b-1-o-aparelho-reconhece-a-discursiva/`. Este documento registra **como**
cada verificação foi vista falhar, e não que ela passa (`rigorous.md` §8). As datas são UTC.

## 0. Linha de base

**0.1** — Sobre `main` (`998558d`) e o commit da proposta (`b8e6692`), que só acrescenta arquivos em
`openspec/changes/`. Do início às 2026-09-24T22:45:06Z até 22:50:35Z:

| Comando | Resultado |
|---|---|
| `./gradlew build --rerun-tasks` | `BUILD SUCCESSFUL in 4m 31s`, **183 de 183 tasks executadas** |
| `./gradlew :apps:android:connectedDebugAndroidTest`, sem filtro, no `platos-atd34` | 84 testes, 0 falhas, 2 pulados, `timestamp` 22:50:32Z |
| `npx vitest run` em `apps/web` | 16 de 16, 22:45:29Z |

Relatórios do `build`, lidos por `relatorios.mjs` (scratchpad):

| Task | Testes | `timestamp` |
|---|---|---|
| `apps/android` `testDebugUnitTest` | 312 | 22:48:38Z .. 22:48:44Z |
| `apps/android` `testReleaseUnitTest` | 312 | 22:48:25Z .. 22:48:32Z |
| `apps/api` `test` | 168 | 22:49:07Z .. 22:49:23Z |
| `packages/domain` `jvmTest` | 375 | 22:49:26Z .. 22:49:28Z |
| `packages/domain` `jsNodeTest` | 366 | 22:49:34Z .. 22:49:36Z |
| `packages/domain` `testAndroidHostTest` | 366 | 22:48:56Z .. 22:48:59Z |

O relatório de `buildSrc` saiu marcado **VELHO** (12:02:36Z): o `build` não roda os testes de
`buildSrc`, e esta mudança não o toca. O passo próprio dele roda no fechamento.

**0.2** — `node tools/divida/divida.mjs`, `exit 0`, com 20 linhas lidas e nenhuma vencida:
"fatia corrente: 5b, de slice-5b-1-o-aparelho-reconhece-a-discursiva (ativa)". Sob "vence nesta fatia
(5b)" aparecem:
- `Acurácia em manuscrito` (`5`);
- `Modo degradado (§10) não existe` (`5`);
- `O limiar do OMR foi apurado sobre um aparelho e uma impressora` (`5`);
- `A região discursiva ainda não passou pelo aparelho nem pelo papel` (`5b`).

## 1. A captura por região

**1.1 — os marcadores esperados são os da região.** `RegionDetector.declaredMarkersOf` passa a filtrar
pelos `marker_ids` da região.

O instrumento é `FolhaDiscursivaRenderizada`, em `androidTest`. Ele desenha a folha de `tok-a` da
prova com discursiva pelo `LayoutMapRenderer` de produção, num PDF, e a rasteriza pelo `PdfRenderer`
da plataforma a 10 px/mm. Não é dependência nova: `PdfRenderer` é do SDK. O cenário é
`RegiaoDiscursivaInstrumentedTest.o_gabarito_e_retificado_numa_pagina_que_tem_outra_regiao`.

**Visto falhar sobre o código real, antes da correção.** O teste foi escrito primeiro e rodado contra
a `main`, às 22:52:06Z: 1 teste, 1 falha, "esperava a regiao 0 retificada, veio Failed(reason=a pagina
0 declara 8 ArUcos; esperados 4)". Isto **mede** o segundo defeito que a correção P7 da linha `5b` (PR
#68) descrevia só por leitura. Depois da correção, às 22:52:39Z: 1 de 1, e os marcadores detectados são
`[0, 1, 2, 3]`.

A mutação que a tarefa pedia ("sem o filtro") é exatamente o código anterior. A execução de antes é o
vermelho dela, e a de depois é a reversão rodada.
