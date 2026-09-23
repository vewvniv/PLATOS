## 0. Antes de qualquer commit: o ambiente e a linha de base

- [ ] 0.1 **Nada a ligar, e isso fica conferido** (P22, regra 0.9 do plano). Esta mudança não pede
  Docker nem emulador (`design.md`, "Migration Plan"). Se alguma tarefa abaixo passar a pedir, parar
  e perguntar ao mantenedor antes, inclusive em modo automático. Verificar e registrar aqui:
  - a branch é `vewvniv/registro-de-divida-executavel`, criada sobre `4c8e473`, e acima dele há só o
    commit da proposta;
  - `git status` está limpo;
  - `node --version` (o local é 24, o CI é 22).
- [ ] 0.2 **O Context do `design.md` relido na árvore, e não na memória** (P21). Conferir:
  - a tabela do §16 tem 16 linhas, 4 colunas e 5 `|` por linha, e nenhum `\|`;
  - a maior mudança `slice-*` em `openspec/changes/` e `archive/` é `4b`;
  - nenhum arquivo de `apps/`, `packages/`, `buildSrc/` ou `tools/` lê `ARQUITETURA-FINAL-v3.md`,
    `rigorous.md`, `CLAUDE.md` ou `openspec/changes/` fora de KDoc.

  Verificar: os três batem. Se algum não bater, a decisão que se apoia nele é relida antes da tarefa
  1.
- [ ] 0.3 **A linha de base dos conferidores do job `web`** (P3), rodados localmente no Git Bash com o
  **texto exato** do `ci.yml`, e não uma transcrição. São os quatro "concorda/esta preso" e os quatro
  "continua capaz de falhar" (limiar, answer-kind, fio, renderizador). Verificar: oito passos com
  `exit 0`, e o horário registrado. É contra isto que o `ci.yml` editado na tarefa 7 é comparado.

## 1. Commit 1: as duas regras (`rigorous.md`, só texto)

- [ ] 1.1 **Cada incidente conferido na hora** (§10: commit, arquivo e linha), com `git log -S` e
  `grep -n`, e não copiado do plano. Duas linhas do plano já derivaram
  (`device-session/spec.md` `:352` → `:395` e `deploy-api.md` `:423` → `:459`). As tabelas de
  incidentes da P27 (cinco) e da P28 (três) estão no `design.md`, decisão 1. Onde a decisão diz
  "o commit se confere na escrita" (ETAPA 5 e ETAPA 6), achar o commit que fechou o achado.
  Verificar: cada linha das duas tabelas tem commit, arquivo e linha que existem na árvore desta
  sessão, registrados nesta tarefa.
- [ ] 1.2 **A seção "E. Depois do archive, e entre módulos"**, depois da D, com **P27 [V]** e **P28**
  no texto da ETAPA 8 do plano, sem edição de palavra, e os incidentes de 1.1 abaixo de cada uma. A
  forma é a de `rigorous.md` (`**P27 [V].** **Nunca …**`), e não o bloco de citação do plano.
  Verificar: o texto de cada regra, sem a marcação Markdown, é idêntico ao do plano. A comparação é
  feita com as duas versões normalizadas (sem `>`, `*` e quebras de linha), e não a olho.
- [ ] 1.3 **§4 e §8.** No §4, "São quinze" passa a "São dezesseis", com a P27 na lista. No §8, na lista
  de fechamento de fatia, entra "as linhas do §16 que esta mudança alcançou estão reconciliadas,
  pagas ou reagendadas (P27)" (`design.md`, decisão 1: é cláusula da P27, e não uma terceira regra).
  Verificar: `grep -c '^\*\*P[0-9]* \[V\]' rigorous.md` passa de 15 (conferido em 2026-09-24) para
  16, e os números são os mesmos da lista do §4.
- [ ] 1.4 **Commit.** Verificar: `git diff --stat HEAD~1` mostra só `rigorous.md`. A mensagem diz que
  são as duas regras da ETAPA 8, com os incidentes, e que nada foi removido nem afrouxado (§10).

## 2. Commit 2: o formato do §16 (só formato, nenhum limite muda)

- [ ] 2.1 **Os tokens**, célula por célula, conforme a tabela da decisão 2 do `design.md`, seguindo as
  três regras da passada de formato:
  - o token é o que a célula já diz;
  - `**N**` sozinho vira `` `N` ``, e nos outros casos o token entra antes da prosa intacta;
  - `paga` só onde a linha já diz que fechou.
- [ ] 2.2 **A linha de eventos e o parágrafo.** Acima da tabela entra `**Eventos que já ocorreram:**
  nenhum.` (decisão 3). Entra também o parágrafo curto que diz o formato e aponta para
  `tools/divida/divida.mjs`, incluindo a frase de que quem declara um evento declara também os que ele
  implica (Risks). O parágrafo diz, no mesmo tom da atualização de 2026-09-10, que isto é atualização
  de registro e não abre ADR.
- [ ] 2.3 **Conferir que foi só formato.** `git diff --word-diff` no documento: só tokens, marcas, o
  parágrafo e a linha de eventos são acrescentados, e nenhuma palavra de prosa sai. Onde `**N**`
  virou `` `N` ``, o número é o mesmo. Verificar também:
  - as 16 linhas ainda têm 5 `|` cada;
  - `paga` aparece em exatamente três linhas: `Impressão dos ArUcos`, `O roster cacheado…` e a da
    credencial.
- [ ] 2.4 **Commit.** A mensagem diz que é formato (P25), que nenhum limite mudou, e que as três marcas
  `paga` transcrevem o que as linhas já diziam.

## 3. Commit 3: as duas linhas do §9 do plano

- [ ] 3.1 **`assessment_fact` (`9`) e `minifyEnabled`/assinatura/`versionCode`
  (`antes-de:lancamento`)**, com as quatro colunas. O texto sai das fontes que a decisão 2 cita (plano
  §9 e `docs/cobertura-slice-4b-outbox-de-resultado.md:378`), e cada linha nomeia a própria origem e
  diz que entrou por decisão do mantenedor em 2026-09-24. Verificar: cada frase de "O que encarece"
  tem fonte citável, e nenhuma afirmação nova sem fonte entra (P6). Se não houver fonte para uma, a
  frase sai e fica dito o que falta.
- [ ] 3.2 **Commit.** Verificar: o diff acrescenta duas linhas à tabela e mais nada. A mensagem cita a
  decisão do mantenedor e a razão: o archive desta mudança apaga o ponteiro para o plano.

## 4. Commit 4: a guarda, e ela nasce vermelha

- [ ] 4.1 **Escrever `tools/divida/divida.mjs`** conforme as decisões 3 a 6 do `design.md`. O
  cabeçalho traz, no tom de `renderizador.mjs`:
  - a gramática do token;
  - a convenção `slice-<maior>[-<menor>][<letra>]-<nome>`, e o fato de que ela não é imposta;
  - quando uma linha vence e por que não é `>=`;
  - os três códigos de saída;
  - o que a guarda **não** prova (decisão 12).

  Usa só `node:fs`, `node:path` e `node:url`. Verificar: `node --check tools/divida/divida.mjs` sai
  `0`. Um parâmetro desconhecido sai `2`.
- [ ] 4.2 **A primeira execução, sobre a árvore real, sem nada plantado.** `node tools/divida/divida.mjs`.
  O **previsto** (decisão 9):
  - saída `1`;
  - a fatia corrente impressa é `4b`, de uma mudança `slice-4b-*`;
  - **exatamente** duas linhas vencidas: `LGPD com dados de menores` (`3`) e `Uso offline não fecha
    ponta a ponta` (`4a`);
  - as três `paga` ficam como pagas, as duas `continuo` como contínuas, e as cinco com `antes-de`
    como "aguarda evento";
  - "vence nesta fatia" vem vazio.

  Registrar a saída inteira. **Se o real divergir, parar** (regra 0.5; decisão 11): escrever o real ao
  lado do previsto e não mexer na guarda nem na tabela para caber.
- [ ] 4.3 **Commit, vermelho de propósito.** A guarda entra sozinha, sem o passo do CI. A mensagem diz
  que ela reprova a árvore real, nomeia as duas linhas, e diz que a reconciliação é o commit
  seguinte, como o commit 1 da ETAPA 3 e o da 7.3. Verificar: `git show --stat` mostra só
  `tools/divida/divida.mjs`.

## 5. Commit 5: a reconciliação das duas linhas vencidas

- [ ] 5.1 **Uso offline → `4a` `paga`**, com a frase datada na linha e as duas fontes: o §15 ("foi
  **satisfeita** em 2026-09-10") e `docs/cobertura-fatia-4a-cache-referencia.md:112-124` (a câmera
  aberta sem rede num processo nascido em modo avião). Verificar: as duas fontes, relidas agora, dizem
  o que a frase afirma.
- [ ] 5.2 **LGPD: a decisão é do mantenedor, e ele é perguntado**, com a leitura na mão (decisão 9):
  - o expurgo foi pago: a classe H na 4b, e a classe B ganhou linha própria;
  - a interface não existe: a coerção do papel no primeiro cadastro (política §3.5) e o bloqueio do
    roster nominal sem contrato de operador (§4);
  - o `3` era o momento esperado do primeiro piloto, e nenhum piloto aconteceu.

  **Sugestão:** `antes-de:primeiro-piloto`. O `3` fica na prosa como limite anterior, e o motivo vai
  escrito. Aplicar o que ele decidir. Verificar: a linha tem token novo, o limite anterior citado e o
  motivo.
- [ ] 5.3 **A guarda de novo.** Verificar:
  - saída `0`;
  - a corrente continua `4b`;
  - nenhuma linha vencida;
  - `Uso offline` aparece como paga;
  - a LGPD aparece no estado que a decisão de 5.2 dá.

  Se a decisão for `antes-de:primeiro-piloto`, `primeiro-piloto` entra no vocabulário, e a linha de
  eventos continua `nenhum`.
- [ ] 5.4 **Commit.** A mensagem nomeia as duas linhas, o que foi feito com cada uma, e de quem é a
  decisão da segunda.

## 6. Ver falhar: a guarda contra a árvore mutada

A tabela da decisão 8 do `design.md`, copiada como está. Cada mutação que edita arquivo leva `MUTACAO`
na linha editada: na prosa da célula, ou em `// MUTACAO` acima da linha no script. Cada uma é
**revertida, e a reversão é rodada** antes da próxima (P10). Se o real divergir do previsto, **parar**
(regra 0.5; decisão 11).

| # | Mutação | Previsto | **Real** |
|---|---|---|---|
| M1 | `--mudancas` numa cópia de `openspec/changes/` com um `slice-6-mutacao` a mais | saída `1`, **exatamente** `Acurácia em manuscrito`, `Modo degradado (§10) não existe` e `O limiar do OMR…`, as três com `5` e sem `paga`. Nenhuma linha paga cai | |
| M2 | a mesma cópia com `slice-5-mutacao` no lugar | saída `0`, e as mesmas três em "vence nesta fatia". É a fronteira: `==` não reprova | |
| M3 | tirar o `paga` de `Impressão dos ArUcos` (`2b`), no documento real | saída `1`, **só** `Impressão dos ArUcos`. É a comparação pelo número (`2` < `4`) | |
| M4 | tirar o `paga` de `Uso offline não fecha ponta a ponta` (`4a`), no documento real | saída `1`, **só** `Uso offline…`. É a comparação pela letra (`4a` < `4b`) | |
| M5 | M4, e no script a derivação passa a ignorar a letra (`4b` vira `4`) | saída **`0`**, com a corrente impressa como `4`. O defeito **esconde** a linha vencida de M4, e é assim que se vê que a derivação lê a letra | |
| M6 | `--ocorrido piloto-nominal` | saída `1`, **exatamente** `A classe H não enumera o roster baixado` e `A regra de extração… abaixo da API 31` | |
| M7 | declarar `` `piloto-nomial` `` na linha de eventos real | saída `2`, "evento declarado que nenhuma linha usa", com o nome | |
| M8 | apagar o token de `Custo de IA` no documento real | saída `2`, `Custo de IA` nomeada | |

- [ ] 6.1 **M1 e M2**, a fronteira da fatia. As cópias ficam no scratchpad, fora do checkout.
  Verificar: os dois conjuntos, com a corrente impressa (`6` e `5`).
- [ ] 6.2 **M3 e M4**, a marca `paga` e as duas comparações. Verificar: o conjunto de cada uma. Depois
  de cada reversão, `git diff --exit-code docs/architecture/ARQUITETURA-FINAL-v3.md` sai `0` e a
  guarda sai `0`.
- [ ] 6.3 **M5**, o limite da derivação. Verificar: a saída `0` e a corrente `4` no log. Registrar,
  como resultado e não como cobertura, que o CI não pega este defeito (decisão 8).
- [ ] 6.4 **M6**, o caminho do evento. Verificar: exatamente as duas linhas.
- [ ] 6.5 **M7 e M8**, as falhas fechadas. Verificar: saída `2` e o nome no motivo, e não só o código.
- [ ] 6.6 **A reversão, conferida rodando** (P10, regra 0.7 do plano). Verificar:
  - `grep -rn "MUTACAO"` fora de `build/` e `node_modules/` devolve **0** linhas;
  - `git status` mostra só o esperado desta tarefa;
  - a guarda sai `0`, com o horário posterior ao da última mutação.

## 7. Commit 6: os dois passos no `ci.yml`

- [ ] 7.1 **Dois passos no job `web`**, logo depois de "A verificacao da versao do renderizador
  continua capaz de falhar", conforme a decisão 7. O comentário acima deles vai no tom dos vizinhos:
  por que existem, qual o defeito, e o que não provam.
  - **"O registro de divida do §16 esta em dia"**: `node tools/divida/divida.mjs`.
  - **"A verificacao da divida continua capaz de falhar"**, com os cinco defeitos plantados fora do
    checkout:
    - canário `` `0` ``;
    - canário `` `0` `paga` ``;
    - canário de evento, com e sem `--ocorrido`;
    - tabela vazia;
    - `--mudancas` vazio.

    O motivo é conferido, e a saída só é impressa quando o passo falha.
- [ ] 7.2 **Os dois passos rodados localmente com o texto exato do `ci.yml`**, no Git Bash. Verificar:
  os dois com `exit 0`, e a linha final de cada um no log.
- [ ] 7.3 **O segundo passo, visto falhar.** Um defeito na guarda, marcado `// MUTACAO`: a marca
  `paga` deixa de segurar. Rodar o texto do passo. Verificar: o passo sai diferente de `0`, e o motivo
  impresso é o do canário `` `0` `paga` ``, e não outro. Reverter, rodar o passo de novo e ver `exit
  0`. `grep -rn "MUTACAO"` volta a dar 0.
- [ ] 7.4 **Commit.** Verificar: `git show --stat` mostra só `.github/workflows/ci.yml`.

## 8. Commit 7: as duas linhas do `CLAUDE.md`

- [ ] 8.1 **No "Workflow OpenSpec", depois do passo 5 e antes do 6**, as duas linhas da decisão 10:
  "No propose…", com o ponteiro para a guarda, e "No archive…", com a P27. A linha temporária da banda
  **não** sai aqui: sai no archive (decisão 10). Verificar: `git diff` mostra só as duas linhas
  acrescentadas.
- [ ] 8.2 **Commit.** A mensagem diz que é o que o plano manda, e qual é o único acréscimo ao texto
  dele: o ponteiro.

## 9. Registro

- [ ] 9.1 **`docs/cobertura-registro-de-divida-executavel.md`**, com:
  - o Context relido (0.2);
  - o primeiro vermelho sobre a árvore real, com a saída inteira, ao lado do previsto (4.2);
  - a reconciliação, com a decisão do mantenedor (5.2);
  - a tabela de M1 a M8, com o real preenchido;
  - o passo do CI visto falhar (7.3);
  - a divisão entre o que o CI prova e o que só esta mudança provou (decisão 7);
  - a seção do que **não** fica verificado, com a decisão 12 inteira, o limite de M5, o evento não
    declarado, a convenção de nome não imposta, e o passivo das tabelas de débito dos
    `docs/cobertura-*.md` não migrado (P8: lacuna, e não mitigado).

  Verificar: cada número da cobertura tem ao lado o comando e o horário em que foi obtido.
- [ ] 9.2 **Commit** do documento de cobertura, e o `tasks.md` com o real preenchido.

## 10. Fechamento: o CI lido no destino

- [ ] 10.1 **Os passos locais do job `web`, depois de todas as reversões**, com o texto exato do
  `ci.yml`: os oito da linha de base (0.3) e os dois novos. Verificar:
  - dez passos com `exit 0`;
  - `grep -rn "MUTACAO"` fora de `build/` e `node_modules/` com 0 linhas;
  - o horário posterior ao último commit de código.
- [ ] 10.2 **Publicar**: `git push` da branch, e a PR **empilhada**, com base
  `vewvniv/o-apk-de-release-e-verificado` (PR #61), como as etapas anteriores (plano §2, "Como isso se
  traduz em sessões"). Verificar: a PR mostra só os commits desta mudança.
- [ ] 10.3 **O CI da PR, lido no destino** (P26; é o comando cheio desta mudança). Verificar:
  - os três jobs estão verdes no commit da ponta;
  - no log do `web`, aparecem a saída da guarda (a corrente `4b` e nenhuma vencida) e a linha final do
    passo "continua capaz de falhar";
  - o Node do CI está registrado.

  Vermelho de CI se lê pelo log e pelo histórico do mesmo job antes de ser chamado de regressão
  (P15).

## O archive (não são tarefas desta lista, e sim o que o `/opsx:archive` tem de fazer)

Pela decisão 10 do `design.md`, o archive desta mudança é o primeiro sob a P27:

- a mensagem do archive nomeia as linhas do §16 que a mudança alcançou (as duas reconciliadas no
  commit 5) e diz que nenhuma foi alcançada por fatia, porque a corrente continuou `4b`;
- a linha temporária da banda sai do `CLAUDE.md`, como ela mesma manda;
- entram notas no plano (ETAPA 8 fechada) e na auditoria (§7), sem apagar nada (P7).
