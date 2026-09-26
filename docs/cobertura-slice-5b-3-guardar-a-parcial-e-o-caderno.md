# Cobertura — `slice-5b-3-guardar-a-parcial-e-o-caderno`

## 0. Antes do primeiro commit

### 0.1 — linha de base em `origin/main` (9be6356)

Janela: 2026-09-26T21:06Z – 2026-09-26T23:13Z.

| Task | Comando | Resultado | Testes |
|---|---|---|---|
| `./gradlew build --rerun-tasks` | build completo, `packages:domain` incluído | `BUILD SUCCESSFUL` em 3m52s | `packages:domain:testAndroidHostTest`: 399 métodos com `@Test`, todos com resultado no relatório |
| `./gradlew -p buildSrc test --rerun-tasks` | testes do `buildSrc` | `BUILD SUCCESSFUL` em 25s | suíte do `buildSrc` |
| `./gradlew :apps:android:connectedDebugAndroidTest` (sem filtro, `platos-atd34`) | instrumentado no emulador | `BUILD SUCCESSFUL` em 1m01s | 91 testes, 2 pulados propositalmente (`AcumuloDeInstanciasProbe`), 0 falhas |
| `npx vitest run` (`apps/web`) | testes do web | 2 arquivos, `PASSED` | 18 testes |

Nenhuma falha na baseline.

### 0.2 — `node tools/divida/divida.mjs`

```
fatia corrente: 5b, de slice-5b-2-a-nota-objetiva-parcial (ativa), slice-5b-3-guardar-a-parcial-e-o-caderno (ativa), slice-5b-0-a-regiao-discursiva-compacta (arquivada), slice-5b-1-o-aparelho-reconhece-a-discursiva (arquivada)
...
vence nesta fatia (5b):
  Acurácia em manuscrito (`5`)
  Modo degradado (§10) não existe (`5`)
  O limiar do OMR foi apurado sobre um aparelho e uma impressora (`5`)

nenhuma linha vencida: 21 linhas lidas
exit 0
```

**Nota sobre as três linhas `5` (resolvida).** Na medição original, esta árvore era `origin/main` no
commit `9be6356` (merge do PR #74), que ainda não incluía a reconciliação do §16 feita ao arquivar a
`5b-2` — essa reconciliação estava no PR #75, aberto e não mergeado no momento da medição. As três
linhas `5` (`Acurácia em manuscrito`, `Modo degradado`, `O limiar do OMR`) apareciam de novo em
"vence nesta fatia" por isso, e não por dívida nova desta mudança (`exit 0`, "nenhuma linha vencida").
O PR #75 foi mergeado em `30c729d`, e esta branch foi rebaseada sobre o `main` atualizado antes da
task 1.1: `node tools/divida/divida.mjs` volta a dizer "vence nesta fatia (5b): nenhuma", e
`openspec validate --strict` não reporta mais o `MODIFIED` sem cabeçalho correspondente na spec
principal.

## 1. Contrato de persistência

- **1.1 — `@Serializable`.** Achado ao planejar a serialização: a rota mais limpa era anotar os
  tipos de domínio direto (`PartialScoringOutcome`, `PartialScore`, `AwaitingEssay`,
  `PendingQuestion`, `PendingReason`, `QuestionOutcome`, `QuestionAnswer` em `packages/domain`; e
  `Caderno`, `RegiaoDoCaderno`, `EstadoDaRegiao` no app), e não um DTO próprio — decisão tomada com
  o mantenedor, porque um DTO duplicaria `QuestionAnswer` nos dois sentidos (o KDoc de
  `QuestionOutcome.answer` já nomeia isso como risco). Corrigiu a proposta, que dizia "nenhuma
  mudança de contrato KMP". `packages:domain:build`: `testAndroidHostTest` 399 → 402 (+3,
  `PartialScoreSerializationTest`), nenhuma asserção anterior alterada. `apps:android` JVM: +3
  (`CadernoSerializationTest`).
- **1.2/1.3 — Room.** `CadernoEntity`/`CadernoDao`/`BaseDoCaderno` (`caderno.db`) e
  `CadernosGuardados`/`CadernosEmRoom`, no padrão de `ResultadosEmRoom`. `connectedDebugAndroidTest`:
  94 → 96 testes (+3: duas linhas de `CadernoEmRepousoInstrumentedTest` mais a de escopo — ver 1.4).
- **1.4 — correção de escopo, achada ao preparar a 3.1.** A chave era só `examId`. Como o aparelho é
  compartilhado entre escolas e `examId` não tem contrato de unicidade entre organizações (a mesma
  distinção que `ScanActivity.EXTRA_SHORT_ID` já registra para o roster), a chave virou composta
  (`organizacao, exam_id`) — decisão tomada com o mantenedor antes da correção. Teste novo:
  `o_mesmo_examId_em_outra_organizacao_nao_e_o_mesmo_caderno`.

## 2. `ScanSession` retoma e guarda o caderno

- **2.1 — achado ao escrever o teste.** `onPermission(granted = true)` sobrescrevia `state`
  incondicionalmente; como `ScanActivity.onCreate` chama isso logo depois de construir a sessão, o
  caderno retomado por `cadernoInicial` seria apagado na mesma abertura de tela que o retoma.
  Corrigido para respeitar `holdsResult`, no mesmo padrão que `onFrame` já usa para
  `NotRead`/`NoSheet`. O teste que prova isso (`a sessao nova retoma o caderno guardado...`) chama
  `onPermission` depois de retomar, de propósito.
- **2.2 — `onStop`/`onCreate`.** `guardarCadernoEmAndamento` (`Dispatchers.IO` + `NonCancellable`,
  mesmo padrão de `gravarEAgendar`) chamável do fio principal sem `Activity` real.
  `GuardarCadernoNoFioPrincipalInstrumentedTest` confere isso com `runOnMainSync`, no mesmo desenho
  de `GravacaoNoFioPrincipalInstrumentedTest` (P16: suíte vizinha verde não verifica esta camada).
- **2.3 — trocar de aluno.** `cadernoAtual` já reflete só o último aluno, porque `ScanSession.caderno`
  continua sendo a variável única de sempre (decisão 4 da 5b-2); nada precisou mudar para isso valer.

## 3. Sair não apaga o caderno guardado

`ApagamentoLocalInstrumentedTest` ganhou o caderno como quinta coisa conferida lado a lado com
pacote/visão/roster/pendente, nos dois caminhos (`sair` e revogação).

**Ver falhar (M-sair, `rigorous.md` §3, "introduza um erro de propósito").** Parâmetro opcional
`mutacaoApagarCaderno: (() -> Unit)? = null` em `DeviceSession.sair` (default nulo — nenhum dos ~40
outros chamadores, todos em `DeviceSessionTest`, precisou mudar), exercitado só no teste, apagando a
linha via SQL direto na base de teste.

- **Previsto:** cai só `sair_apaga_referencia_do_disco_e_preserva_o_pendente_e_o_caderno`, e nenhum
  outro cenário de `sair` (visão, pacote, roster, pendente) muda.
- **Real:** exatamente isso. `connectedDebugAndroidTest`: 1 falha em 94 (2 pulados
  propositalmente), `AssertionError: sair apagou o caderno em andamento
  expected:<Caderno(aluno=tok-a, ...)> but was:<null>` — na asserção do caderno, com as quatro
  anteriores da mesma execução já verdes.

Revertido (`DeviceSession.kt` para `git checkout --`, a chamada removida do teste). `git grep
MUTACAO` vazio; `connectedDebugAndroidTest` sem filtro voltou a 96 testes, 0 falhas.

## O que ainda não foi verificado

- **Encerramento anômalo do processo, sem `onStop`.** O sistema matando o app em segundo plano sem
  seguir o ciclo de vida normal, ou o aparelho desligando de vez, perde o que não foi escrito desde
  a última parada — exatamente como perderia hoje, em memória (design, riscos). **Não é mitigado, é
  conhecido** (P8): estritamente melhor que antes desta mudança, nunca pior.
- **Leitura em aparelho real.** Toda verificação é emulador (`platos-atd34`) ou JVM; nenhuma foto de
  papel entra nesta mudança (ela não toca captura nem recorte).
- **A tela desenhada não tem teste automático** (decisão herdada da 5b-1/5b-2): o `ScanScreen` não
  muda nesta mudança além de já desenhar o que `state.caderno` e `state.parcial` carregam.

## 4. Fechamento

### 4.1 — `git grep MUTACAO`

Vazio (fora de `build/`, `node_modules/`, `docs/`, `openspec/`, `rigorous.md`). Toda reversão foi
rodada de novo antes deste ponto (P10).

### 4.2 — comando cheio local

| Comando | Resultado | Testes | Comparado com a 0.1 |
|---|---|---|---|
| `./gradlew build --rerun-tasks` | `BUILD SUCCESSFUL` em 1m52s | `testAndroidHostTest`: 402 (`jvmTest`: 411) | 399 → 402 (+3, task 1.1) |
| `./gradlew -p buildSrc test --rerun-tasks` | `BUILD SUCCESSFUL` em 16s | suíte do `buildSrc`, sem mudança | igual |
| `./gradlew :apps:android:testDebugUnitTest --rerun-tasks` | `BUILD SUCCESSFUL` em 24s | 339 métodos, todos com resultado | não medido na 0.1 (só `connectedDebugAndroidTest` e o build completo entravam) |
| `./gradlew :apps:android:connectedDebugAndroidTest` (sem filtro) | `BUILD SUCCESSFUL` em 1m16s | 96 testes, 2 pulados propositalmente, 0 falhas | 91 → 96 (+5: serialização, retomada, `onStop` no fio principal, escopo de organização) |
| `npx vitest run` (`apps/web`) | 2 arquivos, `PASSED` | 18 testes | igual |
| `npm run build` (`apps/web`) | `tsc --noEmit && vite build` | `built in 3.15s` | não medido na 0.1; sem mudança nesta mudança no código do web |

Nenhuma falha no comando cheio.

### 4.3 — esta seção

O restante deste documento (§1–3 e "O que ainda não foi verificado") é a 4.3.

### 4.4 — `node tools/divida/divida.mjs`, comparado com a 0.2

```
fatia corrente: 5b, de slice-5b-3-guardar-a-parcial-e-o-caderno (ativa),
  slice-5b-0-a-regiao-discursiva-compacta (arquivada),
  slice-5b-1-o-aparelho-reconhece-a-discursiva (arquivada),
  slice-5b-2-a-nota-objetiva-parcial (arquivada)

vence nesta fatia (5b): nenhuma

nenhuma linha vencida: 21 linhas lidas
exit 0
```

Nenhuma linha nova vence nesta fatia (P27). As três linhas `5` que apareciam na 0.2, antes do
rebase sobre o `main` com o PR #75 mergeado, já saíram da lista — eram artefato de branch, não
dívida desta mudança (ver nota da 0.2).

### 4.5 — PR e CI

Preenchido depois de abrir o PR contra `main` e ler o CI no destino (P2, P26).
