## 0. Antes de qualquer commit — o ambiente e a linha de base

- [ ] 0.1 **Nada a ligar, e isso fica conferido** (P22, regra 0.9 do plano). Esta mudança não pede
  Docker nem emulador (`design.md`, decisões 8 e 10): se alguma tarefa abaixo passar a pedir, parar e
  perguntar ao mantenedor antes, inclusive em modo automático. Verificar e registrar aqui: a branch é
  `vewvniv/versao-do-renderizador-conferida`, criada sobre `5dfeaaa`; `git status` limpo;
  `node --version` (o local é 24, o CI é 22 — decisão 10).
- [ ] 0.2 **A linha de base desta sessão** (P3): sem ela, uma queda sob mutação não teria a quem ser
  atribuída. Dois comandos, e os dois inteiros:
  - `npm test` em `apps/web` — registrar o `Start at` do Vitest, arquivos, testes e falhas;
  - `./gradlew :apps:android:testDebugUnitTest :packages:domain:jvmTest --rerun-tasks` — registrar
    tasks executadas e, pelo atributo `timestamp` de **dentro** de cada `TEST-*.xml` (e não pela data
    do arquivo), suítes, testes, falhas, erros e pulados de cada módulo.

  Verificar: 0 falhas nos dois. `apps/android` tem de dar **308**, como na linha de base da 7.3
  (`openspec/changes/archive/2026-09-23-o-fio-preso-nos-dois-lados/tasks.md`, 0.2): nenhum arquivo
  dele mudou desde então. Se diferir, parar e explicar antes da tarefa 1.

## 1. A medição de entrada — ver o buraco, antes de qualquer código

O acréscimo ao texto do plano, com a razão no `design.md`, decisão 8. Cada mutação é marcada com uma
linha `// MUTACAO: <de> -> <para> (tarefa 1.x)` **acima** da declaração, revertida, e a reversão é
**rodada** antes da próxima (P10). O conjunto previsto, copiado da decisão 8:

| Mutação | Suíte rodada | Previsto | **Real** |
|---|---|---|---|
| `web`: `RENDERER_VERSION` `1` → `2` | `apps/web`: `npm test`, inteira | **0 falhas** — o buraco | |
| `android`: `RENDERER_VERSION` `1` → `2` | `:apps:android:testDebugUnitTest --rerun-tasks`, inteira | **0 falhas** — o buraco | |
| `dominio`: `MIN_RENDERER_VERSION` `1` → `2` | `LayoutEngineTest` e `RendererContractTest`, filtradas | **cai um teste em cada**: `mapa declara as duas versoes` e `renderizador compativel aceita o mapa` — a direção ruidosa, já coberta | |

Se o real divergir — mais, menos ou outros —, **parar** (regra 0.5; `design.md`, decisão 9), escrever
o real ao lado do previsto e decidir com o mantenedor se a mudança segue como está.

- [ ] 1.1 **`web` sobe sozinho.** Em `apps/web/src/layoutMap.ts`, `RENDERER_VERSION = 2`, com a linha
  `MUTACAO` acima. `npm test` em `apps/web`. Verificar: 0 falhas, e a mesma contagem de testes da
  linha de base — contagem menor seria teste que deixou de rodar, e não verde. Reverter;
  `git diff --exit-code apps/web/src/layoutMap.ts` sai `0`; `npm test` de novo, verde, com o
  `Start at` posterior ao da mutação.
- [ ] 1.2 **`android` sobe sozinho.** Em `RendererContract.kt`, `RENDERER_VERSION = 2`, com a linha
  `MUTACAO` acima. `./gradlew :apps:android:testDebugUnitTest --rerun-tasks`. Verificar pelo
  `timestamp` dos XML: 0 falhas, 308 testes. Reverter; `git diff --exit-code` do arquivo sai `0`; o
  mesmo comando de novo, verde, com `timestamp` posterior.
- [ ] 1.3 **`dominio` sobe sozinho.** Em `LayoutMap.kt`, `MIN_RENDERER_VERSION = 2`, com a linha
  `MUTACAO` acima. `./gradlew --continue --rerun-tasks :packages:domain:jvmTest --tests
  "com.platos.domain.layout.LayoutEngineTest" :apps:android:testDebugUnitTest --tests
  "com.platos.android.render.RendererContractTest"` (`--continue`, para a queda do primeiro não
  impedir o segundo de rodar). Verificar **pela mensagem**, e não pela contagem (P12): cai
  **exatamente** `mapa declara as duas versoes` em `LayoutEngineTest` (esperado `1`, obtido `2`) e
  **exatamente** `renderizador compativel aceita o mapa` em `RendererContractTest`; todos os outros
  testes das duas classes passam. Reverter; `git diff --exit-code` sai `0`; o mesmo comando, verde.
  **O que ela não afirma** fica escrito com o resultado: quais outras classes cairiam (decisão 8).
- [ ] 1.4 **Registro da medição, antes do código.** `docs/cobertura-versao-do-renderizador-conferida.md`,
  Parte I: a linha de base (0.2), as três mutações com previsto e real lado a lado, as horas UTC, o
  que não foi rodado e por quê (instrumentada, por leitura; as suítes de um lado sob a mutação do
  outro, por `grep`), e uma Parte II que diz "ainda não existe". Preencher a coluna **Real** da tabela
  acima. Verificar: `grep -rn "MUTACAO" --exclude-dir=build --exclude-dir=node_modules --exclude-dir=.gradle .`
  fora de `docs/` e `openspec/` sai vazio, e `git status` só mostra o documento e este `tasks.md`.
  Commit `registro:` só com esses dois arquivos.

## 2. Commit 1 — o conferidor e os dois passos do CI

- [ ] 2.1 **Escrever `tools/parity/renderizador.mjs`** conforme as decisões 1 a 6 do `design.md`: a
  tabela dos três registros no topo; a leitura pela declaração ancorada na linha, com o tipo opcional
  e sem o comentário de fim de linha; a falha fechada com `2` para arquivo ausente, zero ou mais de
  uma declaração, e valor que não seja literal inteiro; a comparação par a par na ordem
  `dominio`, `android`, `web`, uma linha `::error::os registros <x> e <y> discordam: …` por par; a
  saída `0`/`1`/`2`; e `--divergir <chave>` como único parâmetro. A KDoc do cabeçalho no molde de
  `answer-kind.mjs` — por que ele existe, a direção silenciosa, por que não dono único (decisão 3), o
  que ele **não** prova (decisão 11), uso. Verificar: `node --check tools/parity/renderizador.mjs`
  sai `0`, e o arquivo só importa de `node:`. **Não executá-lo ainda**: o primeiro contato com a
  árvore é o de 2.3.
- [ ] 2.2 **Dois passos no `ci.yml`**, no job `web`, logo depois de "A verificacao do fio continua
  capaz de falhar" (decisão 7): um que confere; outro que, para cada chave em `dominio android web`,
  roda com `--divergir <chave>` e exige saída `1`, as duas linhas dos pares que contêm a chave e a
  ausência da linha do par que não a contém, imprimindo a saída só quando falha. Comentário no tom
  dos vizinhos. Verificar: YAML válido por `yaml.safe_load` do PyYAML, com os dois nomes na ordem
  certa entre os passos do fio e "As fixtures da digitalizacao estao em dia". Nenhuma outra linha do
  `ci.yml` muda — em particular, a `concurrency` (item 7.2.3, outra mudança): `git diff` do arquivo
  mostra só as linhas acrescentadas.
- [ ] 2.3 **A primeira execução, sobre a árvore real, sem nada plantado.** `node tools/parity/renderizador.mjs`
  a partir da raiz **e** a partir de outro diretório (`tools/parity`), para provar que os caminhos
  não dependem de onde ele é chamado (decisão 1). Previsto: saída `0`, os três registros em `1`, com
  rótulo, e `os tres registros concordam`. Registrar a saída inteira, o código e a hora UTC. Qualquer
  outra coisa: parar (decisão 9).
- [ ] 2.4 **O segundo passo do CI, rodado localmente com o texto exato do `ci.yml`**, no Git Bash —
  extraído do arquivo, e não transcrito à mão. Previsto: termina com a linha "acusou … como deve" e
  saída `0`. Commit 1 (`parity:`), com `renderizador.mjs` e o `ci.yml`, e só eles.

## 3. Ver falhar — o conferidor contra a árvore mutada

A tarefa de verificação do plano, copiada como está (ETAPA 7, "Ver falhar"): *"plantar a divergência
de versão e ver `renderizador.mjs` nomear **quais dois** registros discordam"*. Aqui por registro, e
no arquivo real — é o que prova a **leitura**, que o passo do CI não prova (decisão 6). Mesmo
protocolo da tarefa 1: linha `MUTACAO` acima, reversão rodada.

| Mutação no arquivo real | Previsto: saída | Previsto: linhas | **Real** |
|---|---|---|---|
| `dominio` `1` → `2` | `1` | `dominio e android` · `dominio e web` — e **não** `android e web` | |
| `android` `1` → `2` | `1` | `dominio e android` · `android e web` — e **não** `dominio e web` | |
| `web` `1` → `2` | `1` | `dominio e web` · `android e web` — e **não** `dominio e android` | |
| `android` passa a `RENDERER_VERSION = LayoutMap.MIN_RENDERER_VERSION` (o dono único) | `2` | o registro `android`, o arquivo, e "não é literal inteiro" — e nenhuma linha de "discordam" | |
| a declaração de `web` renomeada para `RENDERER_VERSAO` | `2` | o registro `web`, o arquivo, e "zero declarações" | |

- [ ] 3.1 **As três divergências**, uma por registro: cada uma, `node tools/parity/renderizador.mjs`,
  e conferir a saída e as linhas contra a tabela, **linha a linha**. Reverter; `git diff --exit-code`
  do arquivo; o conferidor de novo, saída `0`. Preencher a coluna **Real**.
- [ ] 3.2 **As duas falhas fechadas**: a referência ao domínio (decisão 3) e a declaração ausente
  (decisão 2). Mesmo protocolo. Preencher a coluna **Real**.
- [ ] 3.3 **A reversão, conferida rodando** (P10, regra 0.7 do plano):
  `grep -rn "MUTACAO" --exclude-dir=build --exclude-dir=node_modules --exclude-dir=.gradle .` fora de
  `docs/` e `openspec/` sai vazio; `git diff --exit-code` nos três arquivos de registro sai `0`; e,
  **depois disso**, o conferidor sai `0` e o segundo passo do CI (2.4) termina verde.

## 4. Registro

- [ ] 4.1 **`docs/cobertura-versao-do-renderizador-conferida.md`, Parte II**: a primeira execução
  (2.3), o passo do CI rodado localmente (2.4), as cinco mutações de 3 com previsto e real, a
  reversão (3.3). E a seção do que **não** fica verificado, com, no mínimo: a frase inteira da
  decisão 11; que o passo do CI prova a comparação e não a leitura (decisão 6); a suíte instrumentada
  não rodada; e que `./gradlew build` não foi rodado localmente, com a razão (decisão 10). Verificar
  lendo: cada afirmação carrega o tipo — medido, conferido, herdado ou suposto (P6).
- [ ] 4.2 **A nota onde o achado aponta** (P7: a frase original fica, marcada, com a medição ao lado):
  em `docs/auditoria-2026-09-18-antes-da-fatia-5.md` §4.4, ao lado de "nada os compara", e na 7.1 do
  plano, ao lado de "Os três valem `1` e nada os compara" — uma nota de 2026-09-23 que separa as duas
  direções e aponta para a Parte I da cobertura. **Só com o que 1.1–1.3 mediram**: se a medição
  divergiu da leitura, a nota diz o que foi medido, e não o que o `design.md` previa. Verificar:
  `git diff` dos dois arquivos só acrescenta linhas. Commit `docs(versao-do-renderizador-conferida):`
  com a cobertura e as duas notas.

## 5. Fechamento — o CI lido no destino

- [ ] 5.1 **Os passos locais do job `web` que não dependem de artefato de build**, com o texto exato
  do `ci.yml`, no Git Bash: os quatro conferidores (`limiar`, `answer-kind`, `fio`, `renderizador`) e
  os quatro "continua capaz de falhar". Verificar: os oito verdes, com a hora UTC. Os três primeiros
  não mudaram, e rodá-los prova que o passo novo não quebrou o vizinho no mesmo job.
- [ ] 5.2 **Publicar**: `git push` da branch e a PR **empilhada**, com base
  `vewvniv/o-fio-preso-nos-dois-lados` (PR #59), no molde das anteriores da banda. **Perguntar ao
  mantenedor antes do push** — é ação para fora da máquina.
- [ ] 5.3 **O CI da PR, lido no destino** (P26; o comando cheio desta mudança, decisão 10): os três
  jobs (`build`, `web`, `paridade`) verdes **no commit da ponta da branch**, e no log do job `web` a
  saída dos dois passos novos — os três `1`, `os tres registros concordam`, e a linha "acusou … como
  deve". Registrar o número da execução e as horas na cobertura, e o quadro do §10 do plano
  preenchido, item a item, com o que não se aplica dito como tal. Commit `registro:`.
