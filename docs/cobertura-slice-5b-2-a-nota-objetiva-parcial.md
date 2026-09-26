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

## 1. A parcial no domínio

**1.1 — o julgamento por questão é um só** (`e84264d`, só refatoração). A repetição, a conferência de
conjunto e o laço saem de `score` para `julgar`, que recebe o conjunto de itens que a folha tem de ter.
Nenhum arquivo de teste mudou. `--rerun` nos três alvos, de 11:18:33Z a 11:18:40Z: `jvmTest` 393,
`jsNodeTest` 384, `testAndroidHostTest` 384, as contagens da 0.1, com `ObjectiveScoringTest` 29 de 29
em cada um.

Não há "ver falhar" próprio, porque a refatoração não acrescenta verificação: o que a prende são os 29
cenários de `ObjectiveScoringTest`, vistos falhar nas fatias que os criaram (*herdado*).

**1.2 — o tipo da parcial** (`4edc738`, contrato antes do consumidor). `PartialScore` não é
`ObjectiveScore`, não herda dela nem a contém, e não tem `closed`. As guardas são as da nota, na escala
da parte objetiva, mais a nova. `PartialScoreTest`: 8 testes, um por guarda e um positivo, 8 de 8 nos
três alvos às 11:20:28Z–11:20:36Z. Cada teste parte da parcial válida da fixture e viola **só** a
guarda dele, e a asserção lê a frase:

| Guarda | O caso | A frase conferida |
|---|---|---|
| escala | 5 pontos numa parte objetiva de 4 | "parcial 5 fora de 0..4" |
| disputa | 4 pontos apurados e 1 em disputa | "em disputa passam de 4" |
| soma | evidência de 3 e parcial de 2 | "a evidencia soma 3 ponto(s) e a parcial apurada e 2" |
| repetição, na evidência | `q1` duas vezes | "a parcial repete o item: q1" |
| repetição, entre os dois lados | `q1` objetiva e aguardando correção | "a parcial repete o item: q1" |
| pendências | a evidência diz `q5`, a lista diz `q4` | "dependem de revisao [q5] e a lista de pendencias diz [q4]" |
| **máximo** (a nova) | pacote com um critério de `d1` de 2 para 3, e `max_score` 11 | "o maximo objetivo (4) mais as discursivas aguardando correcao (8) somam 12, e a prova vale 11" |

O caso da escala também passa da guarda de disputa, e é a frase que diz qual das duas segurou: a de
escala vem antes. É a mesma situação do teste equivalente de `ObjectiveScore`.

**1.3 — `ObjectiveScoring.scorePartial`** (`28340b2`). `ParcialObjetivaTest`, um teste por cenário da
ADDED de `scoring`, com o oráculo **fixado** (4 objetivas de 1 ponto, `d1` 3, `d2` 4, máximo 11), e uma
guarda de vacuidade sobre a fixture: 7 de 7 nos três alvos, 11:22:12Z–11:22:20Z. `ObjectiveScoringTest`
29 de 29, sem mudar, incluído o cenário da 8.1 da 5a ("A apuração completa de prova com discursiva
continua recusada"). A frase de divergência da parcial diz "nao objetivos na variante", porque `d1` é
declarado pela variante e não é objetivo. A de `score` continua byte a byte a mesma.

**Vista falhar:** a discursiva contada também como objetiva (`objetivos += itemId`, `// MUTACAO`).
- **Previsto:** caem os quatro cenários que esperam parcial e o da divergência; ficam verdes a guarda da
  fixture e a prova só objetiva.
- **Real:** exatamente esses 5, às 11:22:43Z. Os quatro com "esperava parcial, veio: Rejected(…
  faltando: d1, d2)", e o da divergência com a frase trocada.
- **Reversão:** `git grep MUTACAO` vazio, e 7 de 7 às 11:22:58Z. A task filtrada fecha `FAILED` pela
  guarda contra comando estreito (401 métodos sem resultado), com 0 falhas no XML.

**1.4 — M-guarda: a guarda do máximo desligada** (`require(true || …)`, `// MUTACAO`), com o
`jvmTest` **inteiro**, e não filtrado, para que "só" seja medido.
- **Previsto:** cai só "parcial cujo maximo nao fecha com o da prova nao e representavel".
- **Real:** 408 testes, **1 falha**, e é essa, com "Expected an exception of class
  java.lang.IllegalArgumentException to be thrown, but was completed successfully", às 11:23:30Z. O
  pacote do teste passa em todas as outras guardas, e nenhuma delas o segurou.
- **Reversão:** `git grep MUTACAO` vazio, `git status` limpo, e 408 de 408 às 11:23:50Z.

**1.5 — M-tipo: a proteção de tipo, vista falhar fora da árvore.** Um arquivo temporário,
`apps/android/src/main/kotlin/com/platos/android/outbox/MTipoTemporario.kt`, monta um
`ResultadoPendente` com `nota = parcial`, a parcial saindo de `scorePartial`. Ele nunca foi commitado.
- **Sem mutação**, `./gradlew :apps:android:compileDebugKotlin` às 11:24:10Z, `rc=1`:
  `e: …/MTipoTemporario.kt:12:74 Argument type mismatch: actual type is 'PartialScore', but
  'ObjectiveScore' was expected.`
- **Com a mutação** (`PartialScoringOutcome.Scored` passa a carregar `ObjectiveScore`, e `scorePartial`
  a construir uma, com o máximo objetivo como `maxScore`): o mesmo arquivo compila, `BUILD SUCCESSFUL`
  às 11:24:50Z, com `:apps:android:compileDebugKotlin` executada. É o estado em que uma folha sem
  pendência objetiva sairia `closed` e seria gravada como nota final.
- **Reversão:** o arquivo foi apagado e as duas mutações desfeitas. `git status` limpo, `git grep
  MUTACAO` vazio, e a compilação de novo com `BUILD SUCCESSFUL` às 11:25:10Z.

**Esta proteção não tem teste automático que a guarde** (P8). Quem a guarda é o tipo: um teste que
não compila não pode morar na suíte, e a prova de que ela reage é a execução acima. Se alguém fizer
`scorePartial` devolver `ObjectiveScore`, como a mutação, ou alargar `ResultadoPendente.nota` para um
tipo que aceite as duas, nenhum teste cai.
