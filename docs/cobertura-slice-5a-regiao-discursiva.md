# Cobertura — `slice-5a-regiao-discursiva`

Primeira mudança da fatia 5. Proposta, specs, design e tarefas em
`openspec/changes/slice-5a-regiao-discursiva/`. Este documento registra **como** cada verificação foi
vista falhar, e não que ela passa (`rigorous.md` §8). As datas são UTC.

## 0. Linha de base, antes do primeiro commit de código

**0.1 — os comandos cheios sobre `main` (`97aa59f`) mais o commit da proposta (`b59785d`), que só
acrescenta arquivos em `openspec/changes/`.**

| Comando | Resultado | Âncora |
|---|---|---|
| `npm test` em `tools/math` | 17 de 17; `node build.mjs --check`: "conversao em dia: 21 formulas" | 2026-09-24T10:56:52Z (início) |
| `npx vitest run` em `apps/web` | 14 de 14, 1 arquivo | 2026-09-24, início às 12:56:32 no relógio local (UTC+2), 10:56:32Z |
| `./gradlew build --rerun-tasks` | `BUILD SUCCESSFUL in 4m 23s`, **183 de 183 tasks executadas**; fim às 11:00:42Z | relatórios de 10:59:22Z a 11:00:39Z, abaixo |
| `./gradlew -p buildSrc test --rerun-tasks` | `BUILD SUCCESSFUL in 26s`, 6 de 6 executadas | fim às 11:01:45Z |
| `./gradlew :apps:android:connectedDebugAndroidTest` no `platos-atd34` (API 34, `aosp_atd`, x86_64) | 83 testes em 17 suítes, 0 falhas, 2 pulados; 2 tasks executadas e 86 `UP-TO-DATE`, porque a compilação acabara de rodar no `build` | `timestamp` 2026-09-24T11:02:34 no relatório, arquivo gravado às 11:02:34Z |

Os relatórios do `build`, somados por diretório de task por um script no scratchpad
(`relatorios.mjs`), que lê o `timestamp` de cada XML e marca como **VELHO** o anterior ao início desta
sessão:

| Task | Testes | Falhas | `timestamp` |
|---|---|---|---|
| `apps/android` `testDebugUnitTest` | 310 | 0 | 10:59:36Z .. 10:59:40Z |
| `apps/android` `testReleaseUnitTest` | 310 | 0 | 10:59:22Z .. 10:59:28Z |
| `apps/api` `test` | 167 | 0 | 10:59:58Z .. 11:00:19Z |
| `packages/domain` `jsNodeTest` | 321 | 0 | 11:00:26Z .. 11:00:28Z |
| `packages/domain` `jvmTest` | 329 | 0 | 11:00:30Z .. 11:00:32Z |
| `packages/domain` `testAndroidHostTest` | 321 | 0 | 11:00:37Z .. 11:00:39Z |

O relatório de `buildSrc/build/test-results/test` saiu marcado **VELHO** (2026-09-23T21:12:27Z):
`./gradlew build` não roda os testes de `buildSrc`, que o CI roda num passo à parte. Por isso ele foi
rodado separadamente, na linha acima. A soma dos seis diretórios da tabela é 1758, e ela não inclui
`buildSrc` nem a suíte instrumentada.

**0.2 — a guarda do registro de dívida, antes de qualquer edição.** `node tools/divida/divida.mjs`,
`exit 0`:

```
fatia corrente: 5a, de slice-5a-regiao-discursiva (ativa)
eventos declarados: nenhum
...
vence nesta fatia (5a):
  Acurácia em manuscrito (`5`)
  Modo degradado (§10) não existe (`5`)
  O limiar do OMR foi apurado sobre um aparelho e uma impressora (`5`)

nenhuma linha vencida: 18 linhas lidas
```

## 1. A linha `5b` no §16 (tarefa 1.1)

A linha "A região discursiva ainda não passou pelo aparelho nem pelo papel" entrou com token `5b`.

- **Na árvore real:** a guarda a lista `em dia`, lê 19 linhas e sai com `0`.
- **Vista falhar:** com `--mudancas` apontando para uma cópia de `openspec/changes/` no scratchpad,
  acrescida de um diretório vazio `slice-5c-sonda`, a guarda deriva "fatia corrente: 5c" e sai com
  `1`. A única linha nomeada é esta: `::error::linha vencida sem reconciliacao: A região discursiva
  ainda não passou pelo aparelho nem pelo papel (\`5b\`): a fatia 5b ja passou, e a corrente e 5c`.
  As três linhas `5` passam a aparecer em "vence nesta fatia (5c)", e não como vencidas, o que está
  certo: `5` vence na 6.
- **A sonda foi apagada**, e a guarda sobre a árvore real voltou a sair com `0`.

## 2. O contrato, sozinho (tarefas 2.1 e 2.2)

**2.1 — o pacote do contrato anterior, congelado** (`ad24976`).
`fixtures/pacote-antes-da-discursiva.json` é cópia byte a byte de `fixtures/prova-referencia.package.json`
antes de qualquer mudança de contrato: `cmp` igual, 101.637 bytes, sem quebra de linha no fim. O
`sha256` dos bytes é `277d2f8cd0a7a87e6e26e5ecf47d2f5610dd6e173e724ab38a65373000ac391a`, o mesmo literal
que `ExamPackageTest.kt:21` e `ExamPublicationTest.kt:32` fixavam para a prova de referência. É esse
literal, e não um campo dentro do pacote, que ancora o arquivo. A tarefa foi corrigida ao ser
executada, com a razão escrita nela (P7).

**2.2 — os tipos, e o vermelho previsto.** O commit acrescenta `AnswerCaptureMode`, `Rubric`,
`RubricCriterion` e `RubricDescriptor`. `Question` ganha `rubric` e `answer_capture_mode`, e
`PackageItem` ganha `kind`, `rubric` e `answer_capture_mode`. `ScannableRegion` ganha `qr_id`
(obrigatório), `question_id` e `answer_area`. `AssignmentQr` vira `RegionQr`, com `region_index`, e
`PackageAssignment.qr` vira `qrs`. Os consumidores mudam o mínimo para compilar: `Publish` grava
`qrs` só com a região 0, `folhaDaAtribuicao` procura o QR da região 0, a validação recusa `qrs` vazia,
e quatro testes trocam `qr` por `qrs`. A compilação de `:packages:domain`, `:apps:api` e das duas
suítes de teste do Android passou, com 14 tasks de compilação executadas.

**Previsto antes de rodar**, por leitura de quais testes leem golden de layout ou de pacote: caem
`SheetInterpreterTest`, `IdentidadeDaProvaTest`, `PacoteVersionadoTest`, `GoldenLayoutTest`,
`LayoutProfileTest` (os que comparam com a golden), `PrintTestSheetTest` e `ObjectiveScoringTest`,
porque a golden antiga não tem `qr_id`, que é obrigatório, e o pacote da turma tem a chave `qr`. De
`ExamPackageTest` cai **só** o teste do literal de hash.

**Real**, `./gradlew :packages:domain:jvmTest --rerun`: 329 testes, **49 falhas**, relatórios de
2026-09-24T11:08:34Z a 11:08:35Z.

| Classe | Caídos | Motivo, lido na mensagem |
|---|---|---|
| `ExamPackageTest` | 1 | `o hash da fixture e o mesmo nos tres alvos`: esperado `277d2f8c…` |
| `GoldenLayoutTest` | 1 | "o mapa divergiu do golden a partir do caractere 91893" |
| `IdentidadeDaProvaTest` | 3 | `Encountered an unknown key 'qr'` no pacote da turma |
| `LayoutProfileTest` | 1 | "o perfil padrao precisa reproduzir o golden byte a byte" |
| `ObjectiveScoringTest` | 28 | `Field 'qr_id' is required` ao ler o pacote golden |
| `PacoteVersionadoTest` | 4 | dois "envelheceu em relacao a fixture", dois `qr_id` ausente |
| `PrintTestSheetTest` | 1 | a folha de teste diverge da versionada |
| `SheetInterpreterTest` | 10 | `Field 'qr_id' is required` ao ler o layout golden |

**Real igual ao previsto**, classe por classe, e todas as 49 mensagens são de um dos três motivos
previstos. Nenhum teste que monta pacote por `buildPackage` caiu, e `FolhaDaAtribuicaoTest` passou
inteiro.

**O que não rodou neste commit:** as suítes de `apps/api` e `apps/android`. Elas leem as mesmas
goldens e o mesmo pacote congelado, e ficam vermelhas pelas mesmas razões até a regravação da 5.2.
Voltam a rodar no fechamento.
