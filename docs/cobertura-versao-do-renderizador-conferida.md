# Cobertura — ETAPA 7.1, `versao-do-renderizador-conferida`

> Este documento tem **duas partes**, no molde de `cobertura-o-fio-preso-nos-dois-lados.md`. A
> **Parte I** é a medição de entrada, feita **antes** de qualquer código da mudança — e, ao contrário
> da 7.3, **depois** do `/opsx:propose`: ela é a tarefa 1 do `tasks.md`, e a razão de ela existir está
> no `design.md`, decisão 8. A **Parte II** é a da mudança, e ainda não existe. A Parte I não se apaga
> (P7): ela é o estado que a Parte II vai corrigir.

**Plano:** `docs/plano-de-correcao-antes-da-fatia-5.md`, ETAPA 7.1 · **Achado:** 4.4 de
`docs/auditoria-2026-09-18-antes-da-fatia-5.md`
**Data:** 2026-09-23, janela `17:42Z`–`17:51Z` · **Ambiente:** Docker ligado pelo mantenedor, com carta
branca de ambiente para a etapa (P22); nenhum emulador, nenhum aparelho
**Base:** `ab14460`, na branch `vewvniv/versao-do-renderizador-conferida`, sobre `5dfeaaa`

---

# Parte I — a medição de entrada

## 0. A pergunta, e por que a previsão vale

A auditoria (4.4) e o plano (7.1) dizem que os três registros da versão do renderizador "valem `1`, e
nada os compara". A leitura feita para a proposta achou que isso vale numa direção só: `MIN` acima de
um renderizador já derruba teste, e um renderizador acima de `MIN` não derruba nada. A pergunta desta
medição é essa: **qual direção cada suíte existente pega?**

**A previsão foi fixada antes de qualquer resultado.** A tabela está no `design.md`, decisão 8, no
commit `ba2a297` (`17:42:09Z`); a primeira mutação rodou às `17:43:39Z`. Ninguém pôde ajustá-la depois
do fato (P11).

**Os três registros**, sobre `5dfeaaa`:

| Chave | Declaração |
|---|---|
| `dominio` | `LayoutMap.MIN_RENDERER_VERSION = 1` — `packages/domain/.../layout/LayoutMap.kt:237` |
| `android` | `RendererContract.RENDERER_VERSION = 1` — `apps/android/.../render/RendererContract.kt:25` |
| `web` | `RENDERER_VERSION = 1` — `apps/web/src/layoutMap.ts:124` |

## 1. A linha de base desta sessão

Sem ela, uma queda sob mutação não teria a quem ser atribuída.

- `npm test` em `apps/web`: `Start at 19:43:05` (hora local, `+02:00` — `17:43:05Z`), `exit 0`:
  **1 arquivo, 14 testes, 0 falhas**.
- `./gradlew build --continue --rerun-tasks`, `17:43:00Z`–`17:46:27Z`, `exit 0`, **176 de 176 tasks
  executadas**. Pelo `timestamp` de dentro dos XML (`17:45:08Z`–`17:46:22Z`): **153 suítes, 1446
  testes, 0 falhas, 0 erros, 0 pulados.**

| Módulo | Testes |
|---|---|
| `apps/android` (`testDebugUnitTest`) | 308 |
| `apps/api` (`test`) | 167 |
| `packages/domain` (`jvmTest`) | 329 |
| `packages/domain` (`jsNodeTest`) | 321 |
| `packages/domain` (`testAndroidHostTest`) | 321 |

`apps/android` bate com os 308 da linha de base da 7.3; `apps/api` tem dois a mais, os dois cenários
de literal que a 7.3 acrescentou (`5e41146`). Um relatório ficou fora da janela, o de `buildSrc`, que
o `build` não alcança.

## 2. As três mutações: conjunto previsto e conjunto real

Cada uma marcada com uma linha `// MUTACAO` **acima** da declaração, revertida, e a reversão **rodada**
antes da próxima (§4).

| Mutação | Suíte rodada | Previsto | Real |
|---|---|---|---|
| `web` `1` → `2` | `apps/web`, `npm test`, inteira | **0 falhas** — o buraco | **0 de 14** · `Start at 19:43:39` |
| `android` `1` → `2` | `./gradlew :apps:android:testDebugUnitTest --rerun-tasks`, inteira | **0 falhas** — o buraco | **0 de 308** · `17:47:17Z`–`17:47:53Z`, 40 de 40 tasks |
| `dominio` `1` → `2` | `LayoutEngineTest` (`jvmTest`) e `RendererContractTest`, filtradas, `--continue` | cai **um** em cada: `mapa declara as duas versoes` e `renderizador compativel aceita o mapa` | **1 de 21** ("expected: <1> but was: <2>") e **1 de 9** ("expected: <true> but was: <false>") · `17:50:15Z`–`17:50:35Z`, 46 de 46 tasks |

**Real = previsto nas três.** A direção silenciosa — um renderizador subindo sozinho — **não derruba
nada**, em nenhuma das duas suítes que leem o registro. A direção ruidosa — `MIN` subindo sozinho — é
pega pelo pino do motor e pelo `<=` do Android.

### Por que o verde das duas primeiras é execução, e não cache: dois canários

"0 falhas" é indistinguível de "a suíte não viu o valor novo" (P13). Então, para cada uma das duas, a
direção **oposta** — o registro em `0`, também marcado `MUTACAO` —, onde a leitura prevê queda:

| Canário | Real |
|---|---|
| `web` `1` → `0` | `exit 1`, **9 de 14 caem**. O primeiro é o da leitura: `aceita quando a versao do renderizador basta` — "expected 1 to be less than or equal to 0". Os outros 8 caem porque todo desenho do golden passa a ser recusado pela guarda. Rodado entre `19:43:49` e `19:44:22`; o `Start at` exato não foi guardado (o filtro da saída cortou a linha), e fica dito em vez de reconstruído |
| `android` `1` → `0` | `17:48:38Z`–`17:49:01Z`, `exit 1`, **8 de 308 caem**: `renderizador compativel aceita o mapa`, e sete de `PreparoDaProvaTest`, porque o gate passa a barrar com `VERSAO_INSUFICIENTE` — `roster_ausente_e_barragem_distinta_das_do_pacote`, `roster_guardado_sob_outra_organizacao_nao_abre_o_escaneamento`, `roster_vazio_abre_o_escaneamento`, `roster_nunca_puxado_barra_mesmo_com_pacote_conferido`, `voltar_do_escaneamento_devolve_a_escolha_da_prova`, `pacote_conferido_abre_o_escaneamento`, `depois_de_voltar_a_mesma_prova_e_escolhivel_de_novo` |

As duas suítes leem o valor do arquivo-fonte: o `const val` recompilado chega ao teste, e o Vitest não
serve módulo velho. **E o canário do web mede o que o `design.md` deixara por leitura**: a direção
ruidosa é pega também do lado web, e não só do Android.

## 3. O que a medição mostra

| Direção | Pega hoje? | Por quem |
|---|---|---|
| `MIN` acima do Android | **sim** — medido | `RendererContractTest` · `renderizador compativel aceita o mapa`; `LayoutEngineTest` · `mapa declara as duas versoes` (o pino) |
| `MIN` acima do web | **sim, em dois passos** — por leitura | o pino de `LayoutEngineTest` cai primeiro; regravado o golden, `renderer.test.ts:64` (`golden <= RENDERER_VERSION`) cairia — o mesmo `<=` que o canário do web derrubou |
| web acima de `MIN` | **não** — medido | nada |
| Android acima de `MIN` | **não** — medido | nada |
| Android ≠ web, os dois acima de `MIN` | **não** — segue das duas linhas acima | nada |

**Então "nada os compara" está certo na direção que importa, e errado na outra.** A direção
silenciosa, a que a auditoria chama de pior — um renderizador que ganha capacidade e sobe a própria
constante sem o mapa subir `MIN` —, é exatamente a que nenhuma suíte pega. A frase da auditoria e a
do plano recebem uma nota marcada com isto (tarefa 4.2); elas não se apagam (P7).

## 4. A reversão, rodada

Cada mutação e cada canário foram revertidos, a reversão conferida por `git diff --exit-code` no
arquivo (`0` em todas), e a **mesma** suíte rodada de novo, verde:

| Depois de | Suíte | Real |
|---|---|---|
| `web` `2` | `npm test` | 14 de 14, `Start at 19:43:49` |
| canário `web` `0` | `npm test` | 14 de 14, `Start at 19:44:22` |
| `android` `2` | `testDebugUnitTest` | 308 de 308, `17:48:06Z`–`17:48:31Z` |
| canário `android` `0` | `testDebugUnitTest` | 308 de 308, `17:49:27Z`–`17:49:50Z` |
| `dominio` `2` | as duas classes filtradas | 21 de 21 e 9 de 9, `17:50:44Z`–`17:51:03Z` |

O `grep -rn "MUTACAO"` da árvore fora de `build/`, `node_modules/`, `docs/` e `openspec/` está na
tarefa 1.4, e o build cheio depois de todas as reversões da mudança, na 5.1.

## 5. O instrumento de contagem

Um script temporário no scratchpad, fora da árvore, que lê o atributo `timestamp` de **dentro** de
cada `TEST-*.xml` e soma só o que cai na janela da execução. Que ele filtra de fato ficou visto no
relatório de `buildSrc`, excluído da linha de base por ser de `13:28Z`.

**Ele errou uma vez, e foi visto.** A primeira listagem das falhas do canário do Android imprimiu a
classe no lugar do nome do teste — a expressão pegava o fim de `classname=`. Corrigido e relido sobre
os **mesmos** XML, sem execução nova. As contagens não dependiam da listagem.

## 6. O que esta medição **não** verificou (P8)

- **Quais outras classes cairiam sob a mutação do `dominio`.** A execução foi filtrada de propósito
  (`design.md`, decisão 8): para dizer que a direção ruidosa é pega basta um teste caindo pelo motivo
  certo. Os goldens do domínio, por exemplo, não rodaram sob ela. E só o alvo `jvmTest` do domínio
  rodou — `jsNodeTest` e `testAndroidHostTest` executam o mesmo `commonTest`, e isso é **herdado** da
  estrutura do módulo, não medido aqui.
- **A suíte instrumentada.** `LayoutMapRendererInstrumentedTest.kt:191` também soma `+ 1` à constante,
  e por leitura não cairia sob a mutação do `android`. Pede emulador, e nenhum arquivo de
  `apps/android` fica mudado nesta mudança: fica **por leitura**.
- **A suíte de um lado sob a mutação do outro.** Nenhum `.kt` referencia `apps/web`, e o web só lê de
  `packages/domain` a fonte `.ttf` (`formulaAssets.ts:48`); `apps/api` não referencia `LayoutEngine`
  nem `MIN`. **Conferido por `grep`**, não por execução.
- **"`MIN` acima do web" em dois passos** (§3) é leitura: o pino cai primeiro, e o `<=` do web só seria
  alcançado depois de alguém regravar o golden.

---

# Parte II — a mudança

Ainda não existe.
