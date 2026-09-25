# Cobertura — `slice-5b-0-a-regiao-discursiva-compacta`

Leva ao motor de layout os ADR-0016, ADR-0017 e ADR-0018: a região discursiva de dois ArUcos, com o QR
no terceiro canto e a pauta cinza de 7 mm. Proposta, specs, design e tarefas em
`openspec/changes/slice-5b-0-a-regiao-discursiva-compacta/`. Este documento registra **como** cada
verificação foi vista falhar, e não que ela passa (`rigorous.md` §8). As datas são UTC.

## 0. Linha de base, antes do primeiro commit de código

**0.1 — os comandos cheios sobre `main` (`8084cb5`) mais o commit da proposta (`7478e82`), que só
acrescenta arquivos em `openspec/changes/`.** O emulador `platos-atd34` já estava de pé
(`emulator-5554`, `adb emu avd name` → `platos-atd34`); nada no ambiente foi mudado.

| Comando | Resultado | Âncora |
|---|---|---|
| `npx vitest run` em `apps/web` | 16 de 16, 2 arquivos, `exit 0` | "Start at 16:10:55" no relógio local (UTC+2), 14:10:55Z |
| `./gradlew build --rerun-tasks` | `BUILD SUCCESSFUL in 3m 49s`, **183 de 183 tasks executadas**; 14:10:11Z–14:14:01Z | relatórios de 14:13:09Z a 14:13:58Z, abaixo |
| `./gradlew -p buildSrc test --rerun-tasks` | `BUILD SUCCESSFUL in 27s`, 6 de 6 executadas | relatório de 14:14:23Z |
| `./gradlew :apps:android:connectedDebugAndroidTest`, sem filtro, no `platos-atd34` | `BUILD SUCCESSFUL in 1m 1s`; 84 testes em 17 suítes, 0 falhas, 2 pulados; 7 tasks executadas e 81 `UP-TO-DATE`, porque a compilação acabara de rodar no `build` | `timestamp` 2026-09-25T14:15:28 no relatório; o comando terminou às 14:15:30Z |

Os relatórios, somados por diretório de task por um script no scratchpad (`relatorios.mjs`), que lê o
`timestamp` de cada XML e marca como **VELHO** o anterior ao início desta sessão (14:10:11Z). Nenhum
saiu marcado:

| Task | Testes | Falhas | `timestamp` |
|---|---|---|---|
| `apps/android` `testDebugUnitTest` | 312 | 0 | 14:13:16Z .. 14:13:20Z |
| `apps/android` `testReleaseUnitTest` | 312 | 0 | 14:13:09Z .. 14:13:12Z |
| `apps/android` `connectedDebugAndroidTest` | 84 (2 pulados) | 0 | 14:15:28Z |
| `apps/api` `test` | 168 | 0 | 14:13:22Z .. 14:13:41Z |
| `packages/domain` `jsNodeTest` | 366 | 0 | 14:13:50Z .. 14:13:51Z |
| `packages/domain` `jvmTest` | 375 | 0 | 14:13:53Z .. 14:13:55Z |
| `packages/domain` `testAndroidHostTest` | 366 | 0 | 14:13:56Z .. 14:13:58Z |
| `buildSrc` `test` | 1 | 0 | 14:14:23Z |

A soma dos oito é 1984.

**0.2 — a guarda do registro de dívida, antes de qualquer edição.** `node tools/divida/divida.mjs`,
`exit 0`:

```
fatia corrente: 5b, de slice-5b-0-a-regiao-discursiva-compacta (ativa)
eventos declarados: nenhum
...
vence nesta fatia (5b):
  Acurácia em manuscrito (`5`)
  Modo degradado (§10) não existe (`5`)
  O limiar do OMR foi apurado sobre um aparelho e uma impressora (`5`)
  A região discursiva ainda não passou pelo aparelho nem pelo papel (`5b`)

nenhuma linha vencida: 20 linhas lidas
```

**0.3 — a âncora da guarda da decisão 8.** O `sha256` dos bytes, calculado pelo `crypto` do Node (P4),
e não por serialização em Kotlin, lidos sobre `7478e82` com `git status --short fixtures/` vazio:

| Arquivo | Bytes | `sha256` |
|---|---|---|
| `prova-referencia.layout.json` | 91.960 | `8c9756a9db45c1d08a97fd0d99f6edcb353338f78894ff5b26438d1e00078451` |
| `prova-referencia.package.json` | 104.091 | `ff2b94ef600101e2c20d5b6b298f7d0612ee0a66beb4d74d7dcd954cfbde40da` |
| `prova-referencia.turma.package.json` | 107.282 | `7282a186d3b644dac6108e7ea39931517c5a0614e8fa570b15ad09324cb14df7` |
| `prova-2.package.json` | 104.108 | `c2098e10e6c70f93a469ce56ff5f0e5796b44792da0b661e01465904a48cb717` |
| `folha-de-teste.layout.json` | 6.743 | `d9f7b08c17a706b02ba21355e2286b71d09792605b1c22adc8b7b95ce566c221` |

## A lacuna do plano achada ao aplicar: a guarda da versão do renderizador

Antes da primeira linha de código, a leitura de `tools/parity/renderizador.mjs` mostrou que ele lê
`LayoutMap.MIN_RENDERER_VERSION` e exige igualdade com os dois renderizadores. A decisão 4 tira essa
constante e sobe os renderizadores a 2, e nenhuma tarefa cobria a guarda: o CI da 7.4 sairia
vermelho. O mantenedor decidiu ajustá-la nesta mudança. A decisão 4 ganhou um parágrafo de
atualização, e a tarefa 4.3 foi acrescentada, com o motivo escrito nela.

## 1. A linha nova no §16 (tarefa 1.1)

A linha "A folha de teste de impressão não aprova a região discursiva que a prova imprime" entrou com
token `5b`, antes de qualquer código (P27). Os três testes instrumentados que ela nomeia foram
conferidos por `grep` em `apps/android/src/androidTest` nesta sessão.

- **Na árvore real:** a guarda a lista `em dia`, lê 21 linhas e sai com `0`. Ela aparece em "vence
  nesta fatia (5b)", ao lado das quatro de antes.
- **Vista falhar:** com `--mudancas` apontando para uma cópia de `openspec/changes/` no scratchpad,
  acrescida de um diretório vazio `slice-5c-sonda`, a guarda deriva "fatia corrente: 5c" e sai com
  `1`. As linhas nomeadas são **as duas** `5b`, e só elas:
  - `::error::linha vencida sem reconciliacao: A região discursiva ainda não passou pelo aparelho nem
    pelo papel (\`5b\`): a fatia 5b ja passou, e a corrente e 5c`;
  - `::error::linha vencida sem reconciliacao: A folha de teste de impressão não aprova a região
    discursiva que a prova imprime (\`5b\`): a fatia 5b ja passou, e a corrente e 5c`.

  As três linhas `5` aparecem em "vence nesta fatia (5c)", e não como vencidas, o que está certo:
  `5` vence na 6.
- **A cópia foi apagada**, e a guarda sobre a árvore real voltou a sair com `0`.
