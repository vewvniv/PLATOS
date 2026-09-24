## 0. Antes de qualquer commit: o ambiente e a linha de base

- [x] 0.1 **Nada a ligar, e isso fica conferido** (P22, regra 0.9 do plano). Esta mudança não pede
  Docker nem emulador (`design.md`, "Migration Plan"). Se alguma tarefa abaixo passar a pedir, parar
  e perguntar ao mantenedor antes, inclusive em modo automático. Verificar e registrar aqui:
  - a branch é `vewvniv/registro-de-divida-executavel`, criada sobre `4c8e473`, e acima dele há só o
    commit da proposta;
  - `git status` está limpo;
  - `node --version` (o local é 24, o CI é 22).

  **Conferido às `2026-09-23T22:24:59Z`** (00:24 de 09-24, hora local `+02:00`). Branch
  `vewvniv/registro-de-divida-executavel`. `4c8e473..HEAD` tem um commit só, `e0e58e2`, que é o da
  proposta. `git status --porcelain` devolve 0 linhas. `node --version` dá `v24.19.0`. Nenhum Docker
  ou emulador foi ligado, e nenhum aparelho foi tocado.
- [x] 0.2 **O Context do `design.md` relido na árvore, e não na memória** (P21). Conferir:
  - a tabela do §16 tem 16 linhas, 4 colunas e 5 `|` por linha, e nenhum `\|`;
  - a maior mudança `slice-*` em `openspec/changes/` e `archive/` é `4b`;
  - nenhum arquivo de `apps/`, `packages/`, `buildSrc/` ou `tools/` lê `ARQUITETURA-FINAL-v3.md`,
    `rigorous.md`, `CLAUDE.md` ou `openspec/changes/` fora de KDoc.

  Verificar: os três batem. Se algum não bater, a decisão que se apoia nele é relida antes da tarefa
  1.

  **Bateu nos três**, entre 0.1 (`22:24:59Z`) e 0.3 (`22:27:05Z`). A seção "Ponto de não-retorno" tem 18 linhas de tabela: cabeçalho,
  separadora e 16 linhas de risco. As 18 têm 5 `|`, e `grep -c -F '\|'` no documento dá 0. As fatias,
  pelos nomes, terminam em `slice-3b` (1), `slice-3c` (1), `slice-4a` (3) e `slice-4b` (4), e a única
  mudança ativa é esta, que não é fatia. Os leitores são seis arquivos, em `apps/android` e `apps/api`,
  e as sete ocorrências neles são todas linhas de KDoc (` * …`), sem leitura de arquivo.
- [x] 0.3 **A linha de base dos conferidores do job `web`** (P3), rodados localmente no Git Bash com o
  **texto exato** do `ci.yml`, e não uma transcrição. São os quatro "concorda/esta preso" e os quatro
  "continua capaz de falhar" (limiar, answer-kind, fio, renderizador). Verificar: oito passos com
  `exit 0`, e o horário registrado. É contra isto que o `ci.yml` editado na tarefa 7 é comparado.

  **Feito, de `22:27:05Z` a `22:27:06Z`: os oito saem com `exit 0`.** Cada um termina com a última
  linha de sucesso dele; o do fio, por exemplo, diz "os 5 contratos estao presos por literal nos dois
  lados". O texto de cada `run:` foi extraído do `ci.yml` pelo nome do passo, por um script no
  scratchpad (`passos.mjs`), fora da árvore. Cada passo rodou com `bash -e`, que é o shell que o log
  do Actions registra (`shell: /usr/bin/bash -e {0}`, correção da 7.1), a partir da raiz do
  repositório. Que o bloco inteiro foi extraído, e não só a primeira linha, se vê pela última linha
  impressa de cada "continua capaz de falhar": é o `echo` final do bloco.

## 1. Commit 1: as duas regras (`rigorous.md`, só texto)

- [x] 1.1 **Cada incidente conferido na hora** (§10: commit, arquivo e linha), com `git log -S` e
  `grep -n`, e não copiado do plano. Duas linhas do plano já derivaram
  (`device-session/spec.md` `:352` → `:395` e `deploy-api.md` `:423` → `:459`). As tabelas de
  incidentes da P27 (cinco) e da P28 (três) estão no `design.md`, decisão 1. Onde a decisão diz
  "o commit se confere na escrita" (ETAPA 5 e ETAPA 6), achar o commit que fechou o achado.
  Verificar: cada linha das duas tabelas tem commit, arquivo e linha que existem na árvore desta
  sessão, registrados nesta tarefa.

  **Conferido entre 0.3 (`22:27:06Z`) e o commit `eb01f44` (`22:29:34Z`).** A linha vem de `grep -n` e o commit de
  `git log -S "<frase>" -- <arquivo> | tail -1`, que dá o commit que introduziu a frase.

  - **Modo degradado:**
    - auditoria §2.3 (`:199`);
    - `device-session/spec.md:395` ("Pacote ausente e sem rede SHALL…"), desde `6f2dfe9`;
    - ADR-0013 `:138` ("são 4b e 4c"), desde `46c4ffb`;
    - `cobertura-slice-4b-outbox-de-resultado.md:379` ("não têm consumidor neste fluxo"), de
      `c0f327a`. A auditoria dizia `:378`.
  - **Variante release:**
    - auditoria §3.1 (`:243`);
    - `cobertura-fatia-4a-cache-referencia.md:220`, com o gatilho "a próxima que mexer em build ou
      variante", desde `60ba7bd`;
    - os dois disparos: `d054e1f` e `6c9356a` (o commit de build da `generatejooq-…`);
    - documentada sem ser paga em `438030a`, e paga em `f761306`.
  - **Migration:**
    - auditoria §4.6 (`:562`);
    - `deploy-api.md:459`, desde `5fb26b7`;
    - o HTTP 500 em `cobertura-slice-4b-outbox-de-resultado.md:302-304` (§5.1.1, item 2).
  - **Limiar:**
    - auditoria §4.7 (`:577`);
    - a obrigação em `cobertura-fatia-3b.md:332`, de `8e4e1b3`;
    - "continua aberta" em `cobertura-fatia-3c.md:266`, de `f40fd9c`.
  - **A linha do APK, o quinto incidente:**
    - a instrução em `plano-de-correcao-antes-da-fatia-5.md:186-187`, de `72e1557`;
    - a nota da ausência em `:189`, de `c7013c4`.
  - **P28:**
    - 4.4: auditoria `:515`; `LayoutMap.kt:237`, `RendererContract.kt:25` e `layoutMap.ts:124`;
      fechado em `54160b9`.
    - 2.1: auditoria `:67`; o declarante único em `1af2460`, e os espelhos removidos em `ea28ad0`.
    - 3.2: auditoria `:286`; uma instância por processo em `75f05ed`.
  - **O precedente:** `limiar.mjs` desde `8e4e1b3`, e o comentário dele em `ci.yml:187`.
- [x] 1.2 **A seção "E. Depois do archive, e entre módulos"**, depois da D, com **P27 [V]** e **P28**
  no texto da ETAPA 8 do plano, sem edição de palavra, e os incidentes de 1.1 abaixo de cada uma. A
  forma é a de `rigorous.md` (`**P27 [V].** **Nunca …**`), e não o bloco de citação do plano.
  Verificar: o texto de cada regra, sem a marcação Markdown, é idêntico ao do plano. A comparação é
  feita com as duas versões normalizadas (sem `>`, `*` e quebras de linha), e não a olho.

  **Idêntico nas duas.** A comparação foi feita por um script no scratchpad (`regras.mjs`). Ele pega o
  parágrafo que começa na regra, nos dois arquivos, e normaliza os dois antes de comparar. Resultado:
  P27 dá `IDENTICO`, com 590 e 590 caracteres; P28 dá `IDENTICO`, com 565 e 565. Os comprimentos são o
  piso: um bloco vazio dos dois lados também daria "idêntico". A seção fica **antes** da nota "Sobre IA
  e custo", que continua fechando o §2. A abertura da seção diz por que são duas regras e não três.
- [x] 1.3 **§4 e §8.** No §4, "São quinze" passa a "São dezesseis", com a P27 na lista. No §8, na lista
  de fechamento de fatia, entra "as linhas do §16 que esta mudança alcançou estão reconciliadas,
  pagas ou reagendadas (P27)" (`design.md`, decisão 1: é cláusula da P27, e não uma terceira regra).
  Verificar: `grep -c '^\*\*P[0-9]* \[V\]' rigorous.md` passa de 15 (conferido em 2026-09-24) para
  16, e os números são os mesmos da lista do §4.

  **Feito.** `grep -c` dá **16**. As regras marcadas são P1, P2, P3, P6, P7, P9, P10, P11, P12, P17,
  P20, P22, P23, P24, P26 e P27, os mesmos números e na mesma ordem da lista do §4, que diz também
  "Eram quinze até 2026-09-24".

  No §8 a linha entrou como **frase depois do parágrafo de fatia**, e não como item da checklist de
  tarefa. A razão é a própria P27: ela vale para o archive de **mudança**, e a checklist é de tarefa. A
  frase é: "uma mudança só se arquiva quando o archive diz, para cada linha do §16 cuja fatia-limite ou
  gatilho ela alcançou, se foi paga ou reagendada com fatia-limite nova e motivo (P27)".
- [x] 1.4 **Commit.** Verificar: `git diff --stat HEAD~1` mostra só `rigorous.md`. A mensagem diz que
  são as duas regras da ETAPA 8, com os incidentes, e que nada foi removido nem afrouxado (§10).

## 2. Commit 2: o formato do §16 (só formato, nenhum limite muda)

- [x] 2.1 **Os tokens**, célula por célula, conforme a tabela da decisão 2 do `design.md`, seguindo as
  três regras da passada de formato:
  - o token é o que a célula já diz;
  - `**N**` sozinho vira `` `N` ``, e nos outros casos o token entra antes da prosa intacta;
  - `paga` só onde a linha já diz que fechou.

  **Feito antes do commit `f60f7db` (`22:30:58Z`)**, por um script no scratchpad (`tokens.mjs`). Ele endereça cada linha pelo
  número e confere três coisas antes de trocar: o começo do `Risco`, o começo da célula e os 5 `|`. Se
  qualquer uma não batesse, abortaria sem escrever. Resultado: "16 celulas trocadas". A regra
  "`**N**` sozinho" foi lida como "o número em negrito que **abre** a célula vira o token", e a prosa
  que o seguia fica. Isso vale para cinco células: `**5** (medir…)`, `**6**`, `**3** (primeiro
  piloto…)`, `**5**` e `**5** (é a fatia do corpus)`. Nas onze demais, o token entra antes da prosa,
  separado por `·`.
- [x] 2.2 **A linha de eventos e o parágrafo.** Acima da tabela entra `**Eventos que já ocorreram:**
  nenhum.` (decisão 3). Entra também o parágrafo curto que diz o formato e aponta para
  `tools/divida/divida.mjs`, incluindo a frase de que quem declara um evento declara também os que ele
  implica (Risks). O parágrafo diz, no mesmo tom da atualização de 2026-09-10, que isto é atualização
  de registro e não abre ADR.
- [x] 2.3 **Conferir que foi só formato.** `git diff --word-diff` no documento: só tokens, marcas, o
  parágrafo e a linha de eventos são acrescentados, e nenhuma palavra de prosa sai. Onde `**N**`
  virou `` `N` ``, o número é o mesmo. Verificar também:
  - as 16 linhas ainda têm 5 `|` cada;
  - `paga` aparece em exatamente três linhas: `Impressão dos ArUcos`, `O roster cacheado…` e a da
    credencial.

  **Conferido antes do commit `f60f7db` (`22:30:58Z`).** `git diff --word-diff=porcelain` mostra **cinco remoções, e são as cinco
  `**N**`**, na ordem das linhas: `**5**`, `**6**`, `**3**`, `**5**`, `**5**`. Cada uma foi trocada
  pelo token com o mesmo número (`` `5` ``, `` `6` ``, `` `3` ``, `` `5` ``, `` `5` ``). Fora isso só
  há acréscimos: os 16 tokens, as três marcas, o parágrafo e a linha de eventos. Nenhuma palavra de
  prosa saiu. Continuam 18 linhas de tabela com 5 `|` cada. As células que começam com
  `` `<token>` `paga` `` são **exatamente três**: `Impressão dos ArUcos`, `O roster cacheado sem regra
  de apagamento` e a da credencial.

  O parágrafo virou uma lista curta, com uma afirmação por item, em vez de um bloco corrido. As
  informações são as da decisão 2 e da decisão 3.
- [x] 2.4 **Commit.** A mensagem diz que é formato (P25), que nenhum limite mudou, e que as três marcas
  `paga` transcrevem o que as linhas já diziam.

## 3. Commit 3: as duas linhas do §9 do plano

- [x] 3.1 **`assessment_fact` (`9`) e `minifyEnabled`/assinatura/`versionCode`
  (`antes-de:lancamento`)**, com as quatro colunas. O texto sai das fontes que a decisão 2 cita (plano
  §9 e `docs/cobertura-slice-4b-outbox-de-resultado.md:378`), e cada linha nomeia a própria origem e
  diz que entrou por decisão do mantenedor em 2026-09-24. Verificar: cada frase de "O que encarece"
  tem fonte citável, e nenhuma afirmação nova sem fonte entra (P6). Se não houver fonte para uma, a
  frase sai e fica dito o que falta.

  **Feito, e cada frase foi conferida contra a fonte que ela cita:**
  - **I2 e o insumo:** a auditoria §6 (`:687-690`) diz "append-only garantido… nas duas tabelas de
    resultado", "derivável por junção com o pacote imutável", "o insumo (`item_id`, `worth`,
    `earned`) está gravado" e "a fatia 9 o consome".
  - **`answer_observation`:** plano §9 e a cobertura da 4b, em `:379` (a auditoria dizia `:378`).
  - **"M3 vira `GROUP BY`":** `ARQUITETURA` I2, `:46`.
  - **As aspas da ETAPA 7:** plano `:791-794`.
  - **"o lançamento":** plano `:186`.
  - **O estado do release:** conferido por `grep` em `apps/android/build.gradle.kts`. Não há
    `buildTypes`, `signingConfig` nem `isMinifyEnabled`, e há `versionCode = 1` em `:127`.

  **Uma frase saiu por não ter fonte (P6).** A primeira redação dizia que, com R8, as guardas da 7.2
  "passam a julgar outro artefato", citando `testReleaseUnitTest`. Isso é suposição: o teste de
  unidade não roda sobre o APK minificado, e ninguém mediu o efeito sobre a guarda do APK. Ficou só o
  que o plano diz, "abre uma frente de verificação inteira". O `git diff` acrescenta duas linhas e não
  remove nenhuma, e as 20 linhas da tabela têm 5 `|`.
- [x] 3.2 **Commit.** Verificar: o diff acrescenta duas linhas à tabela e mais nada. A mensagem cita a
  decisão do mantenedor e a razão: o archive desta mudança apaga o ponteiro para o plano.

## 4. Commit 4: a guarda, e ela nasce vermelha

- [x] 4.1 **Escrever `tools/divida/divida.mjs`** conforme as decisões 3 a 6 do `design.md`. O
  cabeçalho traz, no tom de `renderizador.mjs`:
  - a gramática do token;
  - a convenção `slice-<maior>[-<menor>][<letra>]-<nome>`, e o fato de que ela não é imposta;
  - quando uma linha vence e por que não é `>=`;
  - os três códigos de saída;
  - o que a guarda **não** prova (decisão 12).

  Usa só `node:fs`, `node:path` e `node:url`. Verificar: `node --check tools/divida/divida.mjs` sai
  `0`. Um parâmetro desconhecido sai `2`.

  **Feito.** `node --check` sai `0`. `--xpto` sai `2` ("parametro desconhecido"), e `--ocorrido` sem
  valor também sai `2` ("pede um valor"). Antes da primeira execução entraram duas travas de leitura.
  O `### Ponto de não-retorno` tem de estar **dentro** do `## 16.`, e não em qualquer lugar depois
  dele. E um cabeçalho que não se lê vira lista vazia, que reprova pela coluna ausente.
- [x] 4.2 **A primeira execução, sobre a árvore real, sem nada plantado.** `node tools/divida/divida.mjs`.
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

  **Correção da previsão, escrita antes de a guarda rodar pela primeira vez, que foi às `22:34:15Z` (P7: a lista
  acima fica).** "As cinco com `antes-de`" está errado na contagem, e o erro é desta lista, não da
  tabela. A previsão foi escrita contando só a tabela da decisão 2, que tinha 16 linhas. O commit 3
  acrescentou duas linhas, que a própria lista mandava acrescentar:
  - a do APK de release, com `antes-de:lancamento`, que sobe a contagem de "aguarda evento" para
    **seis**;
  - a de `assessment_fact`, com `9`, que fica "em dia".

  A previsão completa para 18 linhas é:
  - 2 vencidas;
  - 3 pagas;
  - 2 contínuas;
  - 6 aguardando evento;
  - 5 em dia: `5` três vezes, `6` e `9`.

  O conjunto que a previsão existe para testar, **as duas vencidas**, não muda.

  **Real = previsto.** A execução foi às `22:34:15Z`, com saída `1`.
  - **Fatia corrente:** `4b`, derivada das quatro mudanças `slice-4b-*`, todas arquivadas.
  - **Eventos declarados:** nenhum.
  - **As duas vencidas, e só elas:**
    - `LGPD com dados de menores (`3`): a fatia 3 ja passou, e a corrente e 4b`;
    - `Uso offline não fecha ponta a ponta (§10) (`4a`): a fatia 4a ja passou, e a corrente e 4b`.
  - **O resto, na contagem prevista:**
    - 3 pagas: ArUcos, o roster cacheado e a credencial;
    - 2 contínuas;
    - 6 aguardando evento: duas com `piloto-nominal`, e uma com cada um de `primeira-eliminacao`,
      `publicacao-da-politica`, `migration-da-5-em-producao` e `lancamento`;
    - 5 em dia: `5` três vezes, `6` e `9`.
  - "Vence nesta fatia (4b)": nenhuma.

  **O que não entra como verificação** (P16): esta execução prova a **leitura** da árvore real. Ela
  não prova que a guarda reprovaria outra coisa, e isso é o grupo 6.
- [x] 4.3 **Commit, vermelho de propósito.** A guarda entra sozinha, sem o passo do CI. A mensagem diz
  que ela reprova a árvore real, nomeia as duas linhas, e diz que a reconciliação é o commit
  seguinte, como o commit 1 da ETAPA 3 e o da 7.3. Verificar: `git show --stat` mostra só
  `tools/divida/divida.mjs`.

  **`01445b1`**: `git show --stat` mostra `tools/divida/divida.mjs | 325 +` e mais nada.

## 5. Commit 5: a reconciliação das duas linhas vencidas

- [x] 5.1 **Uso offline → `4a` `paga`**, com a frase datada na linha e as duas fontes: o §15 ("foi
  **satisfeita** em 2026-09-10") e `docs/cobertura-fatia-4a-cache-referencia.md:112-124` (a câmera
  aberta sem rede num processo nascido em modo avião). Verificar: as duas fontes, relidas agora, dizem
  o que a frase afirma.

  **Feito, e as duas fontes foram relidas antes de a frase ser escrita.** O §15 traz "A condição 'não
  proponha a 4b antes de `slice-4a-cache-referencia` estar arquivada' foi **satisfeita** em
  2026-09-10". A cobertura, em `:112-124`, traz o processo 13980 morto, o modo avião com `Network is
  unreachable`, e a `ScanActivity` aberta "no mesmo pid 16591", que "nasceu já em modo avião". A
  frase diz que a medição é de 2026-09-10 e que não foi repetida.

  **Uma consequência que a tarefa não previa, e decisão do mantenedor.** A tabela de **cima** do §16
  ("Riscos" → "Avaliação") dizia, para o mesmo risco, "Aberto, descoberto em 2026-09-08". Marcar
  `paga` embaixo sem tocar em cima deixaria as duas se contradizendo. Perguntado, o mantenedor escolheu
  uma nota datada no começo da célula de cima, com o texto antigo mantido (P7) e no mesmo commit, porque
  é a reconciliação que cria a contradição.
- [x] 5.2 **LGPD: a decisão é do mantenedor, e ele é perguntado**, com a leitura na mão (decisão 9):
  - o expurgo foi pago: a classe H na 4b, e a classe B ganhou linha própria;
  - a interface não existe: a coerção do papel no primeiro cadastro (política §3.5) e o bloqueio do
    roster nominal sem contrato de operador (§4);
  - o `3` era o momento esperado do primeiro piloto, e nenhum piloto aconteceu.

  **Sugestão:** `antes-de:primeiro-piloto`. O `3` fica na prosa como limite anterior, e o motivo vai
  escrito. Aplicar o que ele decidir. Verificar: a linha tem token novo, o limite anterior citado e o
  motivo.

  **O mantenedor escolheu `antes-de:primeiro-piloto`**, entre três opções: esta, `piloto-nominal` e
  `5`. A célula passou a `` `antes-de:primeiro-piloto` · ~~`3`~~ (primeiro piloto com turma real) ``, e
  o `3` riscado fica como limite anterior. O motivo abre a coluna "O que encarece": o que foi pago, o
  que não existe, e por que o evento é o **primeiro** piloto, e não só o nominal. O parágrafo do formato
  ganhou o exemplo que agora existe: `piloto-nominal` implica `primeiro-piloto`.
- [x] 5.3 **A guarda de novo.** Verificar:
  - saída `0`;
  - a corrente continua `4b`;
  - nenhuma linha vencida;
  - `Uso offline` aparece como paga;
  - a LGPD aparece no estado que a decisão de 5.2 dá.

  Se a decisão for `antes-de:primeiro-piloto`, `primeiro-piloto` entra no vocabulário, e a linha de
  eventos continua `nenhum`.

  **Real = previsto**, às `22:47:04Z`:
  - saída `0`, com "nenhuma linha vencida: 18 linhas lidas";
  - a corrente é `4b`;
  - `Uso offline` aparece como `paga`;
  - a LGPD aparece como "aguarda evento", com `antes-de:primeiro-piloto`;
  - a contagem é de 4 pagas, 2 contínuas, 7 aguardando evento e 5 em dia.

  `git diff --word-diff=porcelain` mostra duas remoções:
  - o token `` `3` ``, trocado por `` `antes-de:primeiro-piloto` · ~~`3`~~ ``;
  - `implica.`, que ganhou o exemplo.

  Todo o resto é acréscimo.

  **Um efeito colateral, e o que ele mostrou.** O `python` do Windows gravou o documento em CRLF, e o
  Git avisou ("CRLF will be replaced by LF"). A guarda rodou nessa cópia e leu as 18 linhas. A cópia
  foi regravada em LF antes do commit, e `git ls-files --eol` mostra `w/lf`.
- [x] 5.4 **Commit.** A mensagem nomeia as duas linhas, o que foi feito com cada uma, e de quem é a
  decisão da segunda.

## 6. Ver falhar: a guarda contra a árvore mutada

A tabela da decisão 8 do `design.md`, copiada como está. Cada mutação que edita arquivo leva `MUTACAO`
na linha editada: na prosa da célula, ou em `// MUTACAO` acima da linha no script. Cada uma é
**revertida, e a reversão é rodada** antes da próxima (P10). Se o real divergir do previsto, **parar**
(regra 0.5; decisão 11).

| # | Mutação | Previsto | **Real** |
|---|---|---|---|
| M1 | `--mudancas` numa cópia de `openspec/changes/` com um `slice-6-mutacao` a mais | saída `1`, **exatamente** `Acurácia em manuscrito`, `Modo degradado (§10) não existe` e `O limiar do OMR…`, as três com `5` e sem `paga`. Nenhuma linha paga cai | **= previsto** (`22:48:28Z`): saída `1`, corrente `6`, as três e só elas. Nenhuma paga cai. A mais, e como a decisão 5 manda: `Custo de IA` (`6`) em "vence nesta fatia (6)", sem reprovar |
| M2 | a mesma cópia com `slice-5-mutacao` no lugar | saída `0`, e as mesmas três em "vence nesta fatia". É a fronteira: `==` não reprova | **= previsto** (`22:48:37Z`): saída `0`, corrente `5`, 0 vencidas, e as três em "vence nesta fatia (5)" |
| M3 | tirar o `paga` de `Impressão dos ArUcos` (`2b`), no documento real | saída `1`, **só** `Impressão dos ArUcos`. É a comparação pelo número (`2` < `4`) | **= previsto** (`22:48:46Z`): saída `1`, uma linha só, "a fatia 2b ja passou, e a corrente e 4b" |
| M4 | tirar o `paga` de `Uso offline não fecha ponta a ponta` (`4a`), no documento real | saída `1`, **só** `Uso offline…`. É a comparação pela letra (`4a` < `4b`) | **= previsto** (`22:48:47Z`): saída `1`, uma linha só, "a fatia 4a ja passou, e a corrente e 4b" |
| M5 | M4, e no script a derivação passa a ignorar a letra (`4b` vira `4`) | saída **`0`**, com a corrente impressa como `4`. O defeito **esconde** a linha vencida de M4, e é assim que se vê que a derivação lê a letra | **= previsto** (`22:48:56Z`): saída `0`, a corrente é "4, de slice-4a-… slice-4b-…", e `Uso offline` aparece "em dia" |
| M6 | `--ocorrido piloto-nominal` | saída `1`, **exatamente** `A classe H não enumera o roster baixado` e `A regra de extração… abaixo da API 31` | **= previsto** (`22:49:07Z`): saída `1`, as duas e só elas. **A LGPD (`primeiro-piloto`) não cai**, embora um piloto nominal seja também o primeiro piloto: é o risco "evento implicado" do design, visto acontecer. A guarda não infere |
| M7 | declarar `` `piloto-nomial` `` na linha de eventos real | saída `2`, "evento declarado que nenhuma linha usa", com o nome | **= previsto** (`22:49:07Z`): saída `2`, "evento `piloto-nomial` declarado na linha de eventos, que nenhuma linha usa" |
| M8 | apagar o token de `Custo de IA` no documento real | saída `2`, `Custo de IA` nomeada | **= previsto** (`22:49:07Z`): saída `2`, `a linha "Custo de IA" nao comeca com token entre crases` |

- [x] 6.1 **M1 e M2**, a fronteira da fatia. As cópias ficam no scratchpad, fora do checkout.
  Verificar: os dois conjuntos, com a corrente impressa (`6` e `5`).
- [x] 6.2 **M3 e M4**, a marca `paga` e as duas comparações. Verificar: o conjunto de cada uma. Depois
  de cada reversão, `git diff --exit-code docs/architecture/ARQUITETURA-FINAL-v3.md` sai `0` e a
  guarda sai `0`.
- [x] 6.3 **M5**, o limite da derivação. Verificar: a saída `0` e a corrente `4` no log. Registrar,
  como resultado e não como cobertura, que o CI não pega este defeito (decisão 8).
- [x] 6.4 **M6**, o caminho do evento. Verificar: exatamente as duas linhas.
- [x] 6.5 **M7 e M8**, as falhas fechadas. Verificar: saída `2` e o nome no motivo, e não só o código.
- [x] 6.6 **A reversão, conferida rodando** (P10, regra 0.7 do plano). Verificar:
  - `grep -rn "MUTACAO"` fora de `build/` e `node_modules/` devolve **0** linhas;
  - `git status` mostra só o esperado desta tarefa;
  - a guarda sai `0`, com o horário posterior ao da última mutação.

  **Todas as oito = previsto** (a tabela acima). Cada uma foi revertida com `git checkout --` no
  arquivo, e `git diff --exit-code` saiu `0` antes da próxima. A guarda rodou depois de cada reversão
  e saiu `0`. As cópias de M1 e M2 ficaram no scratchpad, fora do checkout. **A reversão, rodada, às
  `22:49:17Z`:**
  - a guarda sai `0`;
  - `git status` mostra só `tasks.md`;
  - **`MUTACAO` aparece em 0 linhas** nos arquivos não-prosa que esta mudança tocou (o §16,
    `rigorous.md` e `divida.mjs`), e em 0 linhas no código-fonte todo (`tools`, `apps`, `packages`,
    `.github` e `buildSrc`, fora de `build/` e `node_modules/`).

  **O `grep -rn "MUTACAO"` literal, sem exclusão, não dá 0, e nunca deu nesta base.** A palavra
  aparece em prosa de 28 documentos, e são os mesmos 28 que `git grep` acha no commit base `4c8e473`.
  Aparece também no `design.md` e neste `tasks.md`, que descrevem o método. A primeira redação desta
  conferência excluía diretórios inteiros de prosa, e foi refeita. Excluir diretório deixaria passar
  uma marca plantada nele.

## 7. Commit 6: os dois passos no `ci.yml`

- [x] 7.1 **Dois passos no job `web`**, logo depois de "A verificacao da versao do renderizador
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

  **Feito.** Os nomes dos passos ficaram em ASCII, como os vizinhos: "O registro de divida do par. 16
  esta em dia" e "A verificacao da divida continua capaz de falhar". O canário é inserido com `awk`
  usando `index()`, e não uma expressão regular com `\|`, porque o `awk` do runner Ubuntu pode ser o
  `mawk`. O YAML parseia, e o job `web` tem 28 passos.
- [x] 7.2 **Os dois passos rodados localmente com o texto exato do `ci.yml`**, no Git Bash. Verificar:
  os dois com `exit 0`, e a linha final de cada um no log.

  **Feito**, com o mesmo `passos.mjs` de 0.3 e `bash -e`:
  - às `22:50:54Z`, "nenhuma linha vencida: 18 linhas lidas";
  - às `22:50:55Z`, "a verificacao da divida acusou o canario vencido, respeitou a marca e o evento,
    e os dois pisos, como deve".
- [x] 7.3 **O segundo passo, visto falhar.** Um defeito na guarda, marcado `// MUTACAO`: a marca
  `paga` deixa de segurar. Rodar o texto do passo. Verificar: o passo sai diferente de `0`, e o motivo
  impresso é o do canário `` `0` `paga` ``, e não outro. Reverter, rodar o passo de novo e ver `exit
  0`. `grep -rn "MUTACAO"` volta a dar 0.

  **Correção da previsão, escrita às `22:51Z`, antes de a mutação rodar (P7: o texto acima fica).**
  "O motivo impresso é o do canário `` `0` `paga` ``, e não outro" está errado. Com `paga` sem segurar,
  as linhas **reais** pagas com fatia anterior à corrente (`2b` e `4a`) também vencem, e isso tem dois
  efeitos. O passo anterior do CI também cairia. E o segundo passo acusa **três** erros:
  - "o canario nao foi a unica linha vencida" (canário `0`, mais `2b` e `4a`);
  - o canário `` `0` `paga` `` sai `1`, e não `0`;
  - o canário de evento **sem** `--ocorrido` sai `1`, e não `0`, porque as reais caem.

  Os dois pisos e o canário de evento **com** `--ocorrido` não aparecem. Como essa mutação não isola
  uma camada, entra uma segunda, que isola: **7.3b**, em que o evento nunca conta como ocorrido. A
  previsão dela é um erro só, o canário de evento com `--ocorrido` saindo `0`, e não `1`. Nada mais.

  **Real = previsto nas duas.**
  - **7.3a** (`22:51:25Z`): o passo sai `1` com os três erros previstos e nenhum outro. Os pisos e o
    canário de evento com `--ocorrido` não aparecem.
  - **7.3b** (`22:51:26Z`): o passo sai `1` com um erro só, o do canário de evento com `--ocorrido`.

  **Ver o passo falhar mostrou uma falha de diagnóstico, e ela foi consertada.** Na 7.3a, dois erros
  saíam com texto idêntico, "com '--arquitetura /tmp/…/doc.md' saiu 1, e nao 0". A cópia é regravada a
  cada canário, então o caminho não distingue os casos. `rodar` passou a receber o nome do caso. Isso
  não muda desfecho nenhum, só a mensagem. As duas mutações foram rodadas de novo depois do conserto:
  - **7.3a** (`22:52:16Z`): "canario 0: nao foi a unica linha vencida; canario 0 paga: saiu 1, e nao
    0; canario de evento, nao ocorrido: saiu 1, e nao 0";
  - **7.3b** (`22:52:16Z`): "canario de evento, ocorrido: saiu 0, e nao 1 com '…ja ocorreu'".

  A guarda foi revertida com `git checkout`, e `git diff --exit-code` saiu `0`. Os dois passos, limpos,
  saíram `0` às `22:52:17Z` e `22:52:18Z`. `grep -rn MUTACAO tools .github` dá 0 linhas.
- [x] 7.4 **Commit.** Verificar: `git show --stat` mostra só `.github/workflows/ci.yml`.

## 8. Commit 7: as duas linhas do `CLAUDE.md`

- [x] 8.1 **No "Workflow OpenSpec", depois do passo 5 e antes do 6**, as duas linhas da decisão 10:
  "No propose…", com o ponteiro para a guarda, e "No archive…", com a P27. A linha temporária da banda
  **não** sai aqui: sai no archive (decisão 10). Verificar: `git diff` mostra só as duas linhas
  acrescentadas.

  **Feito, em `4f5f975`.** São quatro linhas acrescentadas, dois itens com continuação, como
  sub-itens do passo 5. O texto é o do plano, e o único acréscimo é a frase do ponteiro:
  "`node tools/divida/divida.mjs` lista as que vencem na fatia corrente". A linha temporária da banda
  continua no topo do arquivo.
- [x] 8.2 **Commit.** A mensagem diz que é o que o plano manda, e qual é o único acréscimo ao texto
  dele: o ponteiro.

## 9. Registro

- [x] 9.1 **`docs/cobertura-registro-de-divida-executavel.md`**, com:
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

  **Feito.** O documento tem as catorze seções previstas. Na releitura, antes do commit, saíram três
  afirmações que iam além do que rodou, e cada uma foi corrigida:
  - um `git status` que teria sido conferido às `22:52Z`, e não foi;
  - o fim estimado da janela no cabeçalho;
  - "cada uma foi marcada `MUTACAO`", que valia só para as que editam arquivo.

  Os horários citados vêm de execução lida, ou do commit.
- [x] 9.2 **Commit** do documento de cobertura, e o `tasks.md` com o real preenchido.

## 10. Fechamento: o CI lido no destino

- [x] 10.1 **Os passos locais do job `web`, depois de todas as reversões**, com o texto exato do
  `ci.yml`: os oito da linha de base (0.3) e os dois novos. Verificar:
  - dez passos com `exit 0`;
  - `grep -rn "MUTACAO"` fora de `build/` e `node_modules/` com 0 linhas;
  - o horário posterior ao último commit de código.

  **Feito.** Os dez passos rodaram às `22:55:13Z`–`22:55:14Z`, depois do último commit (`783f0c4`,
  `22:55:03Z`), e os dez saíram `exit 0`. Os dois novos terminaram com "nenhuma linha vencida: 18
  linhas lidas" e "…e os dois pisos, como deve". `MUTACAO` aparece em 0 arquivos não-prosa tocados pela
  branch, em 0 linhas de código (`tools`, `apps`, `packages`, `.github` e `buildSrc`), e em 0 linhas do
  §16, do `rigorous.md` e do `CLAUDE.md`. `git status` mostra só este `tasks.md`.
- [x] 10.2 **Publicar**: `git push` da branch, e a PR **empilhada**, com base
  `vewvniv/o-apk-de-release-e-verificado` (PR #61), como as etapas anteriores (plano §2, "Como isso se
  traduz em sessões"). Verificar: a PR mostra só os commits desta mudança.

  **Feito: PR #62**, com base `vewvniv/o-apk-de-release-e-verificado` e ponta `774a797`. Ela mostra
  **10 commits**, exatamente os desta mudança, de `e0e58e2` a `774a797`. O CI disparou em
  `pull_request` às `22:56:03Z`, na execução `35930997915`.
- [x] 10.3 **O CI da PR, lido no destino** (P26; é o comando cheio desta mudança). Verificar:
  - os três jobs estão verdes no commit da ponta;
  - no log do `web`, aparecem a saída da guarda (a corrente `4b` e nenhuma vencida) e a linha final do
    passo "continua capaz de falhar";
  - o Node do CI está registrado.

  Vermelho de CI se lê pelo log e pelo histórico do mesmo job antes de ser chamado de regressão
  (P15).

  **Lido às `23:08Z`.** Execução `35930997915`, na ponta `774a797`, de `22:56:03Z` a `23:07:53Z`,
  terminou em `success`. É a única execução da branch.
  - **`web`** (`22:56:06Z`–`22:56:52Z`): Node `v22.23.2`, `shell: /usr/bin/bash -e {0}`. "O registro
    de divida do par. 16 esta em dia", às `22:56:47Z`, com a mesma saída do local: "fatia corrente:
    4b, de slice-4b-…", as 18 linhas nos mesmos estados, "vence nesta fatia (4b): nenhuma" e "nenhuma
    linha vencida: 18 linhas lidas". "A verificacao da divida continua capaz de falhar", também às
    `22:56:47Z`, imprimiu só a linha final, "…e os dois pisos, como deve", sem nenhum `::error::`,
    porque a saída só é impressa quando o passo falha.
  - **`build`** (`22:56:06Z`–`23:07:52Z`): `buildSrc` com 6 de 6 tasks. "Build e testes" com **183
    tasks, 179 executadas e 4 `up-to-date`** (as do `generateJooq` do passo anterior, como na 7.1), e
    `BUILD SUCCESSFUL`.
  - **`paridade`** (`22:56:55Z`–`23:05:55Z`): "Finished 86 tests on test(AVD)". Os dois defeitos
    deliberados foram acusados: "a paridade acusou a faixa ausente, como deve" e "as duas ferramentas
    acusaram o deslocamento, como devem".

  O commit de registro que traz esta leitura dispara um CI novo, e **esse** não está lido aqui: o que
  se afirma é o CI de `774a797`.

## O archive (não são tarefas desta lista, e sim o que o `/opsx:archive` tem de fazer)

Pela decisão 10 do `design.md`, o archive desta mudança é o primeiro sob a P27:

- a mensagem do archive nomeia as linhas do §16 que a mudança alcançou (as duas reconciliadas no
  commit 5) e diz que nenhuma foi alcançada por fatia, porque a corrente continuou `4b`;
- a linha temporária da banda sai do `CLAUDE.md`, como ela mesma manda;
- entram notas no plano (ETAPA 8 fechada) e na auditoria (§7), sem apagar nada (P7).
