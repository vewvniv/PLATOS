## 0. Antes de qualquer commit — o ambiente e a linha de base

- [x] 0.1 **Nada a ligar, e isso fica conferido** (P22, regra 0.9 do plano). Esta mudança não pede
  Docker nem emulador (`design.md`, decisões 8 e 10): se alguma tarefa abaixo passar a pedir, parar e
  perguntar ao mantenedor antes, inclusive em modo automático. **Atualização de 2026-09-23:** o
  mantenedor ligou o Docker para o build cheio local (decisão 10) e deu carta branca de ambiente
  para esta etapa; registrar `docker info`. Verificar e registrar aqui: a branch é
  `vewvniv/versao-do-renderizador-conferida`, criada sobre `5dfeaaa`; `git status` limpo;
  `node --version` (o local é 24, o CI é 22 — decisão 10).

  **Conferido às `17:42:37Z`.** Branch `vewvniv/versao-do-renderizador-conferida`; `HEAD~2` é
  `5dfeaaa` (os dois commits acima dele são os da proposta, `ba2a297` e `ab14460`); `git status`
  limpo. `node --version` → `v24.19.0`. `docker info` → `exit 0`, Docker Desktop, servidor `29.7.2`,
  nenhum contêiner rodando — ligado pelo mantenedor, não por esta sessão. Nenhum emulador subido,
  nenhum aparelho tocado.
- [x] 0.2 **A linha de base desta sessão** (P3): sem ela, uma queda sob mutação não teria a quem ser
  atribuída. Dois comandos, e os dois inteiros:
  - `npm test` em `apps/web` — registrar o `Start at` do Vitest, arquivos, testes e falhas;
  - ~~`./gradlew :apps:android:testDebugUnitTest :packages:domain:jvmTest --rerun-tasks`~~ →
    **`./gradlew build --continue --rerun-tasks`**, que contém os dois (atualização de 2026-09-23,
    `design.md` decisão 10: o mantenedor ligou o Docker) — registrar tasks executadas e, pelo
    atributo `timestamp` de **dentro** de cada `TEST-*.xml` (e não pela data do arquivo), suítes,
    testes, falhas, erros e pulados de cada módulo.

  Verificar: 0 falhas nos dois. `apps/android` tem de dar **308**, como na linha de base da 7.3
  (`openspec/changes/archive/2026-09-23-o-fio-preso-nos-dois-lados/tasks.md`, 0.2): nenhum arquivo
  dele mudou desde então. Se diferir, parar e explicar antes da tarefa 1.

  **Bateu.** `npm test` em `apps/web`: `Start at 19:43:05` (hora local, `+02:00` — `17:43:05Z`),
  `exit 0`, **1 arquivo, 14 testes, 0 falhas**. `./gradlew build --continue --rerun-tasks`,
  `17:43:00Z`–`17:46:27Z`, `exit 0`, **176 de 176 tasks executadas**. Pelo `timestamp` de dentro dos
  XML (`17:45:08Z`–`17:46:22Z`): **153 suítes, 1446 testes, 0 falhas, 0 erros, 0 pulados** —
  `apps/android` **308**, `apps/api` 167, `packages/domain` 329 (`jvmTest`) + 321 (`jsNodeTest`) + 321
  (`testAndroidHostTest`). `apps/api` dá 167 e não os 165 da linha de base da 7.3 porque a 7.3
  acrescentou dois cenários de literal (`5e41146`); é a única diferença, e é explicada. Um relatório
  ficou fora da janela, o de `buildSrc` (`AquisicaoDeConexaoTest`, `13:28Z`): o `build` não alcança
  os testes de `buildSrc`, como já registrado. O contador é um script temporário no scratchpad, fora
  da árvore; que ele filtra de fato ficou visto nesse relatório excluído.

## 1. A medição de entrada — ver o buraco, antes de qualquer código

O acréscimo ao texto do plano, com a razão no `design.md`, decisão 8. Cada mutação é marcada com uma
linha `// MUTACAO: <de> -> <para> (tarefa 1.x)` **acima** da declaração, revertida, e a reversão é
**rodada** antes da próxima (P10). O conjunto previsto, copiado da decisão 8:

| Mutação | Suíte rodada | Previsto | **Real** |
|---|---|---|---|
| `web`: `RENDERER_VERSION` `1` → `2` | `apps/web`: `npm test`, inteira | **0 falhas** — o buraco | **0 de 14** — o buraco |
| `android`: `RENDERER_VERSION` `1` → `2` | `:apps:android:testDebugUnitTest --rerun-tasks`, inteira | **0 falhas** — o buraco | **0 de 308** — o buraco |
| `dominio`: `MIN_RENDERER_VERSION` `1` → `2` | `LayoutEngineTest` e `RendererContractTest`, filtradas | **cai um teste em cada**: `mapa declara as duas versoes` e `renderizador compativel aceita o mapa` — a direção ruidosa, já coberta | **1 de 21** e **1 de 9**, exatamente os dois previstos |

Se o real divergir — mais, menos ou outros —, **parar** (regra 0.5; `design.md`, decisão 9), escrever
o real ao lado do previsto e decidir com o mantenedor se a mudança segue como está.

- [x] 1.1 **`web` sobe sozinho.** Em `apps/web/src/layoutMap.ts`, `RENDERER_VERSION = 2`, com a linha
  `MUTACAO` acima. `npm test` em `apps/web`. Verificar: 0 falhas, e a mesma contagem de testes da
  linha de base — contagem menor seria teste que deixou de rodar, e não verde. Reverter;
  `git diff --exit-code apps/web/src/layoutMap.ts` sai `0`; `npm test` de novo, verde, com o
  `Start at` posterior ao da mutação.

  **Real = previsto.** Sob a mutação, `Start at 19:43:39` (`17:43:39Z`): `exit 0`, **14 de 14
  passam, 0 falhas** — a mesma contagem da linha de base. Revertido: `git diff --exit-code` → `0`;
  `npm test` às `19:43:49`, 14 de 14.

  **Um canário, fora da tabela, porque zero pede canário (P13).** "0 falhas" é indistinguível de "o
  Vitest não viu o valor novo". Então, entre a reversão e a 1.2, a direção **oposta**:
  `RENDERER_VERSION = 0`, também marcada `MUTACAO`. Rodado entre `19:43:49` e `19:44:22` — o
  `Start at` exato desta execução não foi guardado: o filtro da saída cortou a linha antes dela, e
  fica dito em vez de reconstruído. `exit 1`, **9 de 14 caem**,
  e o primeiro é o que a leitura aponta: `aceita quando a versao do renderizador basta` — "expected
  1 to be less than or equal to 0". Os outros 8 caem porque todo desenho do golden passa a ser
  recusado pela guarda (`1 > 0`). O Vitest lê o valor do arquivo-fonte, e o "0 falhas" da mutação
  vale. De quebra, isso **mede** a direção ruidosa do lado web, que o `design.md` deixara por leitura
  ("ao menos do lado Android"): ela também é pega. Revertido: `git diff --exit-code` → `0`;
  `npm test` às `19:44:22`, 14 de 14.
- [x] 1.2 **`android` sobe sozinho.** Em `RendererContract.kt`, `RENDERER_VERSION = 2`, com a linha
  `MUTACAO` acima. `./gradlew :apps:android:testDebugUnitTest --rerun-tasks`. Verificar pelo
  `timestamp` dos XML: 0 falhas, 308 testes. Reverter; `git diff --exit-code` do arquivo sai `0`; o
  mesmo comando de novo, verde, com `timestamp` posterior.

  **Real = previsto.** Sob a mutação, `17:47:17Z`–`17:47:53Z`, `exit 0`, **40 de 40 tasks
  executadas**; pelo `timestamp` dos XML (`17:47:48Z`–`17:47:52Z`): **29 suítes, 308 testes, 0
  falhas**. Revertido: `git diff --exit-code` → `0`; o mesmo comando `17:48:06Z`–`17:48:31Z`, 308 de
  308, XML `17:48:25Z`–`17:48:29Z`.

  **O mesmo canário do web, pela mesma razão (P13)**: `RENDERER_VERSION = 0`, marcada `MUTACAO`.
  `17:48:38Z`–`17:49:01Z`, `exit 1`, "308 tests completed, 8 failed"; pelos XML: **8 falhas**, e são
  `RendererContractTest` → `renderizador compativel aceita o mapa` ("expected: <true> but was:
  <false>", o `<=` da leitura) e sete de `PreparoDaProvaTest`, todos porque o gate passa a barrar com
  `VERSAO_INSUFICIENTE` onde esperavam `Pronta` ou outra barragem:
  `roster_ausente_e_barragem_distinta_das_do_pacote`,
  `roster_guardado_sob_outra_organizacao_nao_abre_o_escaneamento`, `roster_vazio_abre_o_escaneamento`,
  `roster_nunca_puxado_barra_mesmo_com_pacote_conferido`,
  `voltar_do_escaneamento_devolve_a_escolha_da_prova`, `pacote_conferido_abre_o_escaneamento`,
  `depois_de_voltar_a_mesma_prova_e_escolhivel_de_novo`. O `const val` recompilado chega aos testes,
  e o "0 falhas" da mutação vale. Revertido: `git diff --exit-code` → `0`; o mesmo comando
  `17:49:27Z`–`17:49:50Z`, 308 de 308, XML `17:49:45Z`–`17:49:48Z`.

  **O contador errou uma vez, e foi visto:** a primeira listagem das falhas do canário imprimiu a
  classe no lugar do nome do teste — a expressão pegava o fim de `classname=`. Corrigida no
  scratchpad e relida sobre os **mesmos** XML (nenhuma execução nova); as contagens não dependiam
  dela.
- [x] 1.3 **`dominio` sobe sozinho.** Em `LayoutMap.kt`, `MIN_RENDERER_VERSION = 2`, com a linha
  `MUTACAO` acima. `./gradlew --continue --rerun-tasks :packages:domain:jvmTest --tests
  "com.platos.domain.layout.LayoutEngineTest" :apps:android:testDebugUnitTest --tests
  "com.platos.android.render.RendererContractTest"` (`--continue`, para a queda do primeiro não
  impedir o segundo de rodar). Verificar **pela mensagem**, e não pela contagem (P12): cai
  **exatamente** `mapa declara as duas versoes` em `LayoutEngineTest` (esperado `1`, obtido `2`) e
  **exatamente** `renderizador compativel aceita o mapa` em `RendererContractTest`; todos os outros
  testes das duas classes passam. Reverter; `git diff --exit-code` sai `0`; o mesmo comando, verde.
  **O que ela não afirma** fica escrito com o resultado: quais outras classes cairiam (decisão 8).

  **Real = previsto.** `17:50:15Z`–`17:50:35Z`, `exit 1`, **46 de 46 tasks executadas**, as duas
  tasks de teste rodaram (`--continue`). `LayoutEngineTest[jvm]`: **1 de 21 cai**, `mapa declara as
  duas versoes` — "expected: <1> but was: <2>". `RendererContractTest`: **1 de 9 cai**, `renderizador
  compativel aceita o mapa` — "expected: <true> but was: <false>". Os outros 28 passam. XML
  `17:50:27Z` e `17:50:33Z`. Revertido: `git diff --exit-code packages apps` → `0`; o mesmo comando
  `17:50:44Z`–`17:51:03Z`, `exit 0`, 21 de 21 e 9 de 9.
- [x] 1.4 **Registro da medição, antes do código.** `docs/cobertura-versao-do-renderizador-conferida.md`,
  Parte I: a linha de base (0.2), as três mutações com previsto e real lado a lado, as horas UTC, o
  que não foi rodado e por quê (instrumentada, por leitura; as suítes de um lado sob a mutação do
  outro, por `grep`), e uma Parte II que diz "ainda não existe". Preencher a coluna **Real** da tabela
  acima. Verificar: `grep -rn "MUTACAO" --exclude-dir=build --exclude-dir=node_modules --exclude-dir=.gradle .`
  fora de `docs/` e `openspec/` sai vazio, e `git status` só mostra o documento e este `tasks.md`.
  Commit `registro:` só com esses dois arquivos.

  **Feito.** A Parte I está escrita, com os dois canários (§2) e a tabela das direções (§3), que não
  estavam previstos aqui e ficam ditos como acréscimo. `grep -rn "MUTACAO"` com `--exclude-dir` para
  `build`, `node_modules`, `.gradle`, `.git`, `docs` e `openspec` → `exit 1`, nada achado.
  `git status --short` → só este `tasks.md` e o documento novo.

## 2. Commit 1 — o conferidor e os dois passos do CI

- [x] 2.1 **Escrever `tools/parity/renderizador.mjs`** conforme as decisões 1 a 6 do `design.md`: a
  tabela dos três registros no topo; a leitura pela declaração ancorada na linha, com o tipo opcional
  e sem o comentário de fim de linha; a falha fechada com `2` para arquivo ausente, zero ou mais de
  uma declaração, e valor que não seja literal inteiro; a comparação par a par na ordem
  `dominio`, `android`, `web`, uma linha `::error::os registros <x> e <y> discordam: …` por par; a
  saída `0`/`1`/`2`; e `--divergir <chave>` como único parâmetro. A KDoc do cabeçalho no molde de
  `answer-kind.mjs` — por que ele existe, a direção silenciosa, por que não dono único (decisão 3), o
  que ele **não** prova (decisão 11), uso. Verificar: `node --check tools/parity/renderizador.mjs`
  sai `0`, e o arquivo só importa de `node:`. **Não executá-lo ainda**: o primeiro contato com a
  árvore é o de 2.3.

  **Feito.** `node --check` → `exit 0`. Três imports, todos de `node:` — `node:fs`, `node:path` e
  `node:url`, os que a proposta listou. **Não executado nesta tarefa.** Dois detalhes que o `design.md`
  não fixava e ficam ditos: a âncora de linha é `[ \t]*`, e não `\s*` — `\s` atravessa quebra de
  linha e deixaria o `^` casar numa linha anterior; e **parâmetro desconhecido sai com `2`**, e não é
  ignorado como em `limiar.mjs`: um `--divergi` digitado errado rodaria a conferência simples e
  passaria, e o passo do CI leria o verde como "aceitou" — a decisão 6 diz "único parâmetro", e esta é
  a forma de ele ser o único. Todas as linhas de valor vão para a saída padrão, e as de `::error::`
  para a de erro, como `answer-kind.mjs`.
- [x] 2.2 **Dois passos no `ci.yml`**, no job `web`, logo depois de "A verificacao do fio continua
  capaz de falhar" (decisão 7): um que confere; outro que, para cada chave em `dominio android web`,
  roda com `--divergir <chave>` e exige saída `1`, as duas linhas dos pares que contêm a chave e a
  ausência da linha do par que não a contém, imprimindo a saída só quando falha. Comentário no tom
  dos vizinhos. Verificar: YAML válido por `yaml.safe_load` do PyYAML, com os dois nomes na ordem
  certa entre os passos do fio e "As fixtures da digitalizacao estao em dia". Nenhuma outra linha do
  `ci.yml` muda — em particular, a `concurrency` (item 7.2.3, outra mudança): `git diff` do arquivo
  mostra só as linhas acrescentadas.

  **Feito.** `yaml.safe_load` do PyYAML leu o arquivo inteiro: no job `web`, "A versao do renderizador
  concorda nos tres registros" é o passo 22 e "A verificacao da versao do renderizador continua capaz
  de falhar" o 23, entre "A verificacao do fio continua capaz de falhar" (21) e "As fixtures da
  digitalizacao estao em dia" (24). `git diff --stat` → **37 inserções, 0 remoções**; a `concurrency`
  lida pelo mesmo `safe_load` continua `cancel-in-progress: True`, no nível do workflow.
- [x] 2.3 **A primeira execução, sobre a árvore real, sem nada plantado.** `node tools/parity/renderizador.mjs`
  a partir da raiz **e** a partir de outro diretório (`tools/parity`), para provar que os caminhos
  não dependem de onde ele é chamado (decisão 1). Previsto: saída `0`, os três registros em `1`, com
  rótulo, e `os tres registros concordam`. Registrar a saída inteira, o código e a hora UTC. Qualquer
  outra coisa: parar (decisão 9).

  **Real = previsto**, às `17:54:00Z`, dos dois diretórios, com a mesma saída e `exit 0`:

  ```
  dominio  LayoutMap.MIN_RENDERER_VERSION = 1  (o que a publicacao escreve no mapa)
  android  RendererContract.RENDERER_VERSION = 1  (o que o renderizador e o gate de captura do aparelho leem)
  web      RENDERER_VERSION de layoutMap.ts = 1  (o que o renderizador web le)

  os tres registros concordam: versao 1
  ```
- [x] 2.4 **O segundo passo do CI, rodado localmente com o texto exato do `ci.yml`**, no Git Bash —
  extraído do arquivo, e não transcrito à mão. Previsto: termina com a linha "acusou … como deve" e
  saída `0`. Commit 1 (`parity:`), com `renderizador.mjs` e o `ci.yml`, e só eles.

  **Real = previsto.** O `run` do passo foi extraído do `ci.yml` por `yaml.safe_load` (script no
  scratchpad, que recusa nome ausente ou repetido) e rodado com `bash --noprofile --norc -eo
  pipefail`, o shell do Actions: `17:54:06Z`, `exit 0`, "a verificacao da versao do renderizador
  acusou cada registro forcado, e so os pares dele, como deve". As três saídas forçadas, uma a uma,
  dão exatamente os dois pares do registro forçado, com `exit 1`.

  **O passo também foi visto falhar, o que esta tarefa não pedia** (P9: a decisão 6 afirma que ele
  pega um conferidor que deixou de comparar um registro, e isso é afirmação até ser visto). Defeito
  plantado no próprio `renderizador.mjs`, marcado `MUTACAO`: o laço interno para em
  `lidos.length - 1`, e a comparação deixa de incluir o `web`. Às `17:54:20Z`: o conferidor, sozinho,
  **aceitou** `--divergir web` — `exit 0`, "os tres registros concordam" —, que é o defeito; e o passo
  do CI **recusou**, `exit 1`: "com dominio forcado: faltou 'os registros dominio e web discordam'".
  Revertido: `grep -c MUTACAO` no arquivo → `0`; às `17:54:31Z` o conferidor `exit 0` e o passo
  `exit 0` de novo.

  **Os parâmetros inválidos saem com `2`**: `--divergir` sem chave, `--divergir foo`, e `--divergi
  web` ("parametro desconhecido"). Commit 1 com os dois arquivos, e só eles; este registro vai no
  commit seguinte.

  **Correção de 2026-09-23, `18:32Z`, lida no log do CI da PR #60 (P7: a frase acima fica).** "`bash
  --noprofile --norc -eo pipefail`, o shell do Actions" está **errado**: o log do job `web` diz
  `shell: /usr/bin/bash -e {0}` — sem `shell:` explícito, o Actions não liga `pipefail`. O local
  rodou mais estrito que o CI. Nenhum dos oito passos tem pipeline, então o desfecho não dependia
  disso; e, para o registro dizer o exato em vez do equivalente, os oito foram rodados de novo com
  `bash -e`, `18:32:00Z`–`18:32:03Z`: os oito `exit 0`, as mesmas últimas linhas.

## 3. Ver falhar — o conferidor contra a árvore mutada

A tarefa de verificação do plano, copiada como está (ETAPA 7, "Ver falhar"): *"plantar a divergência
de versão e ver `renderizador.mjs` nomear **quais dois** registros discordam"*. Aqui por registro, e
no arquivo real — é o que prova a **leitura**, que o passo do CI não prova (decisão 6). Mesmo
protocolo da tarefa 1: linha `MUTACAO` acima, reversão rodada.

| Mutação no arquivo real | Previsto: saída | Previsto: linhas | **Real** |
|---|---|---|---|
| `dominio` `1` → `2` | `1` | `dominio e android` · `dominio e web` — e **não** `android e web` | `1` · exatamente as duas, "dominio diz 2, android diz 1" e "dominio diz 2, web diz 1" |
| `android` `1` → `2` | `1` | `dominio e android` · `android e web` — e **não** `dominio e web` | `1` · exatamente as duas |
| `web` `1` → `2` | `1` | `dominio e web` · `android e web` — e **não** `dominio e android` | `1` · exatamente as duas |
| `android` passa a `RENDERER_VERSION = LayoutMap.MIN_RENDERER_VERSION` (o dono único) | `2` | o registro `android`, o arquivo, e "não é literal inteiro" — e nenhuma linha de "discordam" | `2` · "nao consegui ler o registro android (RendererContract.RENDERER_VERSION) em apps/android/…/RendererContract.kt: o valor `LayoutMap.MIN_RENDERER_VERSION` nao e literal inteiro — um registro que referencia outro seria comparado com ele mesmo (…decisao 3)"; nenhuma de "discordam" |
| a declaração de `web` renomeada para `RENDERER_VERSAO` | `2` | o registro `web`, o arquivo, e "zero declarações" | `2` · "nao consegui ler o registro web (RENDERER_VERSION de layoutMap.ts) em apps/web/src/layoutMap.ts: zero declaracoes de `RENDERER_VERSION` — a constante mudou de forma ou saiu daqui" |

- [x] 3.1 **As três divergências**, uma por registro: cada uma, `node tools/parity/renderizador.mjs`,
  e conferir a saída e as linhas contra a tabela, **linha a linha**. Reverter; `git diff --exit-code`
  do arquivo; o conferidor de novo, saída `0`. Preencher a coluna **Real**.

  **Real = previsto nas três**, às `17:55:41Z`, cada uma com o `git diff -U0` mostrado antes da
  execução (a linha `MUTACAO` acima, o valor trocado embaixo). Revertidas por `git checkout --` do
  arquivo, depois de o diff mostrar que a mutação era a única mudança nele; `git diff --exit-code` →
  `0` e o conferidor `exit 0` depois de cada uma. As mensagens saem sem acento, como as dos outros
  conferidores; a tabela as cita como saíram.
- [x] 3.2 **As duas falhas fechadas**: a referência ao domínio (decisão 3) e a declaração ausente
  (decisão 2). Mesmo protocolo. Preencher a coluna **Real**.

  **Real = previsto nas duas**, às `17:55:49Z`: `exit 2`, a mensagem inteira na tabela, e nenhuma
  linha de "discordam" — a leitura recusou antes de haver o que comparar. Mesma reversão e mesma
  conferência.
- [x] 3.3 **A reversão, conferida rodando** (P10, regra 0.7 do plano):
  `grep -rn "MUTACAO" --exclude-dir=build --exclude-dir=node_modules --exclude-dir=.gradle .` fora de
  `docs/` e `openspec/` sai vazio; `git diff --exit-code` nos três arquivos de registro sai `0`; e,
  **depois disso**, o conferidor sai `0` e o segundo passo do CI (2.4) termina verde.

  **Feito, às `17:55:57Z`.** `grep` → `exit 1`, nada achado; `git diff --exit-code` nos três arquivos
  → `0`; o conferidor → `exit 0`; o passo extraído do `ci.yml` → "acusou cada registro forcado, e so
  os pares dele, como deve", `exit 0`. `git status` só mostra este `tasks.md`. O build cheio depois
  da reversão é o da 5.1.

## 4. Registro

- [x] 4.1 **`docs/cobertura-versao-do-renderizador-conferida.md`, Parte II**: a primeira execução
  (2.3), o passo do CI rodado localmente (2.4), as cinco mutações de 3 com previsto e real, a
  reversão (3.3). E a seção do que **não** fica verificado, com, no mínimo: a frase inteira da
  decisão 11; que o passo do CI prova a comparação e não a leitura (decisão 6); a suíte instrumentada
  não rodada; e que `./gradlew build` não foi rodado localmente, com a razão (decisão 10). Verificar
  lendo: cada afirmação carrega o tipo — medido, conferido, herdado ou suposto (P6).

  **Feito**, §7 a §13 da cobertura. Um item da lista acima **mudou**, e fica dito: "`./gradlew build`
  não foi rodado localmente" deixou de ser verdade com a atualização da decisão 10 — ele rodou na
  linha de base e roda de novo na 5.1 —, então a seção do que não fica verificado não o traz; traz, no
  lugar, o Node 22 como **suposto** até a 5.3 e as formas recusadas que não foram plantadas. Lido
  item a item: cada afirmação diz se é medida (com hora), conferida por leitura ou `grep`, herdada ou
  suposta. O §13, fechamento, fica dito como "ainda não rodado" até a tarefa 5.
- [x] 4.2 **A nota onde o achado aponta** (P7: a frase original fica, marcada, com a medição ao lado):
  em `docs/auditoria-2026-09-18-antes-da-fatia-5.md` §4.4, ao lado de "nada os compara", e na 7.1 do
  plano, ao lado de "Os três valem `1` e nada os compara" — uma nota de 2026-09-23 que separa as duas
  direções e aponta para a Parte I da cobertura. **Só com o que 1.1–1.3 mediram**: se a medição
  divergiu da leitura, a nota diz o que foi medido, e não o que o `design.md` previa. Verificar:
  `git diff` dos dois arquivos só acrescenta linhas. Commit `docs(versao-do-renderizador-conferida):`
  com a cobertura e as duas notas.

  **Feito, com o que 1.1–1.3 mediram** — que bateu com a leitura, e por isso a nota diz o mesmo que o
  `design.md` previa. A da auditoria qualifica também "divergir entre eles não quebra teste nenhum",
  a frase do `limiar` que o achado transpôs "palavra por palavra". `git diff --numstat`: auditoria
  `9 0`, plano `6 0`.

## 5. Fechamento — o CI lido no destino

- [x] 5.1 **Os passos locais do job `web` que não dependem de artefato de build**, com o texto exato
  do `ci.yml`, no Git Bash: os quatro conferidores (`limiar`, `answer-kind`, `fio`, `renderizador`) e
  os quatro "continua capaz de falhar". Verificar: os oito verdes, com a hora UTC. Os três primeiros
  não mudaram, e rodá-los prova que o passo novo não quebrou o vizinho no mesmo job. **E, depois da
  reversão de todas as mutações, `./gradlew build --continue --rerun-tasks` e `npm test` em
  `apps/web`** (atualização de 2026-09-23, `design.md` decisão 10; regra 0.7 do plano): as mesmas
  contagens da linha de base 0.2, com `timestamp` posterior ao da última reversão.

  **Feito.** Os oito passos, extraídos do `ci.yml` pelo mesmo script e rodados com `bash --noprofile
  --norc -eo pipefail`, `17:57:51Z`–`17:57:54Z`: os oito `exit 0`, cada um com a sua última linha de
  sucesso — o do fio diz "os 5 contratos estao presos por literal nos dois lados".
  `npm test` em `apps/web`: `Start at 19:58:10` (`17:58:10Z`), **14 de 14**.
  `./gradlew build --continue --rerun-tasks`, `17:58:02Z`–`17:59:54Z`, `exit 0`, **176 de 176 tasks
  executadas**; pelo `timestamp` dos XML (`17:58:51Z`–`17:59:51Z`): **153 suítes, 1446 testes, 0
  falhas, 0 erros, 0 pulados** — as mesmas contagens da linha de base, módulo a módulo, e todas
  posteriores à última reversão (`17:55:49Z`). E, por ser o primeiro passo do job `build` do CI, que
  o `build` não alcança: `./gradlew -p buildSrc test --rerun-tasks`, `18:00:08Z`–`18:00:35Z`, `exit
  0`, 6 de 6 tasks, **1 suíte, 1 teste, 0 falhas**, XML `18:00:29Z`.

  **Correção de 2026-09-23, `18:32Z`, lida no log do CI da PR #60 (P7: a frase acima fica).** "`bash
  --noprofile --norc -eo pipefail`, o shell do Actions" está **errado**: o log do job `web` diz
  `shell: /usr/bin/bash -e {0}` — sem `shell:` explícito, o Actions não liga `pipefail`. O local
  rodou mais estrito que o CI. Nenhum dos oito passos tem pipeline, então o desfecho não dependia
  disso; e, para o registro dizer o exato em vez do equivalente, os oito foram rodados de novo com
  `bash -e`, `18:32:00Z`–`18:32:03Z`: os oito `exit 0`, as mesmas últimas linhas.
- [x] 5.2 **Publicar**: `git push` da branch e a PR **empilhada**, com base
  `vewvniv/o-fio-preso-nos-dois-lados` (PR #59), no molde das anteriores da banda. **Perguntar ao
  mantenedor antes do push** — é ação para fora da máquina.

  **Perguntado e autorizado** ("Push e PR"), depois da 5.1 — a carta branca de ambiente não cobria
  publicar. `git push -u origin vewvniv/versao-do-renderizador-conferida` → `exit 0`, branch nova no
  remoto; `gh pr create` → **PR #60**. Conferido no destino: base `vewvniv/o-fio-preso-nos-dois-lados`,
  ponta `0e8f613`, igual ao `HEAD` local, 7 commits. **Nenhum push depois disso até o CI terminar**: a
  `concurrency` ainda é `cancel-in-progress: true` no nível do workflow (o item 7.2.3, de outra
  mudança), e um push novo cancelaria a execução em curso, paridade incluída — o incidente de P15.
- [x] 5.3 **O CI da PR, lido no destino** (P26; o comando cheio desta mudança, decisão 10): os três
  jobs (`build`, `web`, `paridade`) verdes **no commit da ponta da branch**, e no log do job `web` a
  saída dos dois passos novos — os três `1`, `os tres registros concordam`, e a linha "acusou … como
  deve". Registrar o número da execução e as horas na cobertura, e o quadro do §10 do plano
  preenchido, item a item, com o que não se aplica dito como tal. Commit `registro:`.

  **Lido no destino.** Execução `35902874603`, evento `pull_request`, `headSha` `0e8f613` — a ponta
  da branch, igual ao `HEAD` local —, `18:29:55Z`–`18:37:48Z`, `conclusion: success`, e é a única
  execução da branch. Os três jobs `success`: `web` `18:29:59Z`–`18:30:45Z`; `build`
  `18:29:59Z`–`18:37:47Z`; `paridade` `18:30:49Z`–`18:37:46Z`. No log do `web`, Node `v22.23.2`, e às
  `18:30:40Z` os dois passos novos: os três registros em `1`, "os tres registros concordam: versao
  1", e "a verificacao da versao do renderizador acusou cada registro forcado, e so os pares dele,
  como deve" — com `shell: /usr/bin/bash -e {0}`, o que corrigiu o registro local (nota acima, 2.4 e
  5.1). No `build`: o teste do `buildSrc` (6 de 6 tasks), o `generateJooq`, e o `./gradlew build` com
  **172 de 176 tasks executadas** (as 4 restantes, `up-to-date`, vêm do `generateJooq` do passo
  anterior). Na `paridade`: `connectedDebugAndroidTest` com "Finished 86 tests" no emulador, e os dois
  passos que acusam defeito deliberado ("a paridade acusou a faixa ausente", "as duas ferramentas
  acusaram o deslocamento"). O quadro do §10 está na cobertura, §14.

  **Os commits de registro que vêm depois deste** (este, e o do archive) só tocam `docs/` e
  `openspec/`; o CI deles é lido também, mas a leitura que fecha o código é esta, sobre `0e8f613`.
