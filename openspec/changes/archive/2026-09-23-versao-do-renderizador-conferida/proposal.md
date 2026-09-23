## Why

A versão do renderizador (D24) vive em três registros que não se conhecem — é o achado **4.4** da
`docs/auditoria-2026-09-18-antes-da-fatia-5.md`, e a ETAPA **7.1** do
`docs/plano-de-correcao-antes-da-fatia-5.md`:

| Registro | Papel |
|---|---|
| `LayoutMap.MIN_RENDERER_VERSION` (`packages/domain`, `LayoutMap.kt:237`) | o que a publicação **escreve** no mapa |
| `RendererContract.RENDERER_VERSION` (`apps/android`, `RendererContract.kt:25`) | o que o renderizador **e o gate de captura** leem (`PreparoDaProva.kt:271`) |
| `RENDERER_VERSION` (`apps/web/src/layoutMap.ts:124`) | o que o renderizador web lê |

Os três valem `1`. A KDoc do Android diz "Espelha `RENDERER_VERSION` do lado web" — afirmação sem
quem a imponha. A direção silenciosa é a pior: um renderizador que ganhe capacidade e suba a própria
constante sem o mapa subir `MIN` faz clientes antigos desenharem mapas novos, que é o que D24 existe
para impedir.

**Por que agora.** A fatia 5 acrescenta região discursiva ao `LayoutMap`, e é aí que
`min_renderer_version` sobe **pela primeira vez** — o momento exato em que três registros cegos
divergem. Por isso a 7.1 bloqueia a fatia 5 (plano, §2, "O que precisa fechar antes de a fatia 5
abrir").

**O que a leitura acha além do que o plano diz.** "Nada os compara" é exato numa direção e inexato
na outra. **Por leitura** (P6: conferido, não medido), a direção **ruidosa** — `MIN` acima de um
renderizador — já derruba teste hoje: `RendererContractTest.kt:43` afirma
`map.minRendererVersion <= RENDERER_VERSION` sobre um mapa que o `LayoutEngine` acabou de produzir;
`renderer.test.ts:64` afirma o mesmo contra o golden; e `LayoutEngineTest.kt:39` fixa `MIN` em `1`.
A direção **silenciosa** — um renderizador acima de `MIN`, ou Android e web discordando acima dele —
não derruba nada: os testes que a tocam somam `+ 1` à própria constante. É essa, e só essa, que o
conferidor novo fecha sozinho. A leitura vira medição na tarefa 1, **antes** de o conferidor
existir; se ela errar, a regra de parada vale (`design.md`, decisão 9).

## What Changes

- **`tools/parity/renderizador.mjs`**, novo, no molde de `limiar.mjs` e `answer-kind.mjs`: lê os
  três registros **dos arquivos de origem**, e não de uma cópia, e reprova se divergirem, nomeando
  **cada par** de registros que discorda, com o valor de cada um. Recusa ler (saída `2`) quando um
  registro não aparece exatamente uma vez ou não é um literal inteiro — inclusive quando um passa a
  **referenciar** o outro, que tornaria a comparação a de um valor com ele mesmo.
- **Dois passos no `ci.yml`**, no job `web`, também no molde: um que confere, outro que força um
  valor divergente **em cada um dos três registros, um de cada vez**, e falha se a conferência
  aceitar ou se nomear outro par que não os dois do registro forçado.
- **A medição do buraco antes do código** — as duas mutações da direção silenciosa contra as suítes
  que leem cada registro, e a da direção ruidosa contra os dois testes que a leitura aponta. É o
  único acréscimo ao texto do plano, e a razão está acima: sem ela, o "nada os compara" entraria no
  registro com o tipo errado.
- **Nenhum comportamento muda.** Nenhuma constante, renderizador, gate, fixture, golden ou hash é
  tocado.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

Nenhuma. Os requisitos que tratam da versão — "Guarda de versão do renderizador", em
`openspec/specs/print/spec.md`, e o cenário "Versões declaradas", em
`openspec/specs/layout-engine/spec.md` — continuam como estão: o renderizador segue recusando mapa
que exija versão maior que a dele, e aceitando mapa de versão igual **ou inferior**. O que muda é o
que reprova a **árvore** quando os três registros dela discordam. Como a 7.3
(`o-fio-preso-nos-dois-lados`) e `generatejooq-sem-registro-automatico`, esta mudança declara
`skip_specs: true`; a tabela do §2 do plano diz o mesmo: **Specs tocadas: nenhuma**.

## Impact

- **`tools/parity/renderizador.mjs`** — novo. Só `node:fs`, `node:path` e `node:url`, como
  `fio.mjs`. Nenhuma dependência de npm.
- **`.github/workflows/ci.yml`** — dois passos no job `web`, logo depois dos do fio.
- **`docs/cobertura-versao-do-renderizador-conferida.md`** — novo: a medição de entrada, os
  conjuntos vistos cair ao lado dos previstos, e o que fica sem verificação.
- **`docs/auditoria-2026-09-18-antes-da-fatia-5.md` §4.4** — uma nota marcada, com a medição ao lado,
  sobre a direção que já estava coberta (P7: a frase original fica).
- **Ambiente:** nenhum Docker, nenhum emulador. As suítes da medição são a do `apps/web` (Vitest) e
  as de teste local de JVM de `apps/android` e `packages/domain`.

### O que NÃO será alterado

- **Os três registros.** `LayoutMap.kt`, `RendererContract.kt` e `layoutMap.ts` não recebem edição
  que fique na árvore — nem a KDoc "Espelha…", que passa a ser verdade imposta sem precisar mudar. As
  mutações de medição são revertidas e a reversão é rodada (P10).
- **Dono único no lugar da conferência.** O Android poderia escrever
  `RENDERER_VERSION = LayoutMap.MIN_RENDERER_VERSION`, e seria o erro: `MIN` é o que o mapa **exige**,
  `RENDERER_VERSION` é o que o renderizador **sabe fazer**, e amarrar o segundo ao primeiro faria
  toda subida de `MIN` declarar, sozinha, uma capacidade que ninguém implementou. O conferidor recusa
  essa forma (`design.md`, decisão 3).
- **Nada além dos três registros nomeados** entra no conferidor: nem `ENGINE_VERSION`, nem o
  `min_renderer_version` das fixtures, nem os literais dos testes. Proibido pelo plano
  ("virar um conferidor genérico"; regra 8 do `CLAUDE.md`).
- **`limiar.mjs`, `answer-kind.mjs` e `fio.mjs`** ficam como estão; o conferidor novo é uma instância
  a mais do molde, e não uma generalização deles.
- **A `concurrency` do `ci.yml`**, o release, `minifyEnabled`, assinatura e `versionCode`. São da 7.2
  (`o-apk-de-release-e-verificado`), que é outra mudança e não se funde com esta (plano, §2,
  observação 3).
- **Os requisitos de `print` e `layout-engine`**, e o comportamento de qualquer renderizador ou gate.
