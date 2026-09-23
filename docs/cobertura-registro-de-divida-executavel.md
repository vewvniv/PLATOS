# Cobertura — ETAPA 8, `registro-de-divida-executavel`

**Plano:** `docs/plano-de-correcao-antes-da-fatia-5.md`, ETAPA 8, a última da banda de correção.
**O que fecha:** a causa comum dos achados 2.3, 3.1, 4.6, 4.7 e 4.4 de
`docs/auditoria-2026-09-18-antes-da-fatia-5.md`, que a auditoria descreve no §7: o que vence depois do
archive não tem quem o cobre, e o que atravessa dois módulos não tem camada que o veja.
**Data:** 2026-09-24 (hora local `+02:00`; os horários abaixo são UTC, de 2026-09-23), a partir de
`22:24:59Z`.
**Ambiente:** nenhum. Nenhum Docker, nenhum emulador, nenhum aparelho (P22).
**Base:** `4c8e473`, na branch `vewvniv/registro-de-divida-executavel`, empilhada sobre a PR #61.

---

## 1. O que a mudança fez

| Commit | O quê |
|---|---|
| `e0e58e2` | a proposta, com as duas decisões do mantenedor: a fatia corrente é derivada, e os dois itens do §9 do plano entram no §16 |
| `eb01f44` | `rigorous.md`: a seção E, com **P27 [V]** e **P28**, e os incidentes de cada uma. O §4 passa a dezesseis regras, e o §8 ganha a cláusula de fechamento da P27 |
| `f60f7db` | §16: a coluna "Fatia-limite" ganha um token em cada célula, a marca `paga` entra em três linhas, e acima da tabela entram o parágrafo do formato e a linha de eventos. **Só formato** |
| `ace0b42` | §16: as duas linhas do §9 do plano (`assessment_fact` e o release de lançamento) |
| `01445b1` | `tools/divida/divida.mjs`. **Vermelho de propósito:** reprova a árvore real, nomeando as duas linhas previstas |
| `41354b0` | §16: a reconciliação das duas vencidas, e a nota datada na tabela de cima |
| `23b3226` | `ci.yml`: os dois passos, com o segundo visto falhar |
| `4f5f975` | `CLAUDE.md`: as duas linhas do workflow |

Nenhum código Kotlin ou TypeScript foi tocado, e nenhum fixture, golden, hash, spec ou migration.

## 2. A linha de base, e o Context relido

**Os oito conferidores do job `web`**, com o texto exato do `ci.yml`, rodaram às
`22:27:05Z`–`22:27:06Z`, e os oito saíram `0`. O texto de cada `run:` foi extraído do `ci.yml` pelo
nome do passo, por um script fora da árvore, e rodou com `bash -e`, o shell que o log do Actions
registra. Que o bloco inteiro foi extraído se vê pela última linha de cada "continua capaz de
falhar", que é o `echo` final do bloco.

**O Context do `design.md`, relido na árvore:**
- a tabela do §16 tinha 16 linhas de risco, com 5 `|` em cada uma, e 0 `\|`;
- a maior fatia pelos nomes era `4b`, com quatro mudanças;
- os seis arquivos de `apps/` que mencionam `rigorous.md`, `CLAUDE.md` ou `ARQUITETURA` o fazem só em
  KDoc. Então nenhuma entrada do Gradle ou do Vitest muda, e é por isso que `./gradlew build` não roda
  localmente nesta mudança (§11).

## 3. As regras

O texto de P27 e P28 em `rigorous.md` é o da ETAPA 8 do plano. A conferência foi feita por
comparação normalizada (sem `>`, `*` e quebras de linha), e não a olho: a P27 deu `IDENTICO` com 590
e 590 caracteres, e a P28 deu `IDENTICO` com 565 e 565. Os comprimentos são o piso, porque dois blocos
vazios também seriam "idênticos".

Os incidentes foram conferidos na hora, com `git log -S` e `grep -n`. Duas linhas citadas pelo plano
já tinham derivado: `device-session/spec.md` passou de `:352` para `:395`, e `deploy-api.md` de `:423`
para `:459`. A auditoria citava `cobertura-slice-4b-outbox-de-resultado.md:378`, e a frase está em
`:379`.

**Um quinto incidente para a P27, que o plano não podia listar:** a linha do APK de release, que o
próprio plano mandava pôr no §16 (`:186-187`, de `72e1557`) e que nunca entrou (`:189`, de `c7013c4`).

Com a P27, a zona vermelha passa de 15 para 16 regras, e `grep -c '^\*\*P[0-9]* \[V\]'` confere com a
lista do §4.

## 4. A passada de formato, e por que ela não decidiu nada

As 16 células foram trocadas por um script que confere três coisas antes de gravar: o começo do
`Risco`, o começo da célula e os 5 `|`. Se qualquer uma não batesse, ele abortava sem gravar.
`git diff --word-diff=porcelain` mostra **cinco remoções, e são as cinco `**N**`**, cada uma trocada
pelo token com o mesmo número. Todo o resto é acréscimo.

A marca `paga` entrou em exatamente três linhas, e só onde a própria linha já dizia que fechou:
ArUcos ("alcançada"), o roster cacheado ("Fechado em 2026-09-16") e a credencial ("fechado em
2026-09-23").

**Uma frase saiu da linha nova do release por não ter fonte (P6):** que, com R8, as guardas da 7.2
"passam a julgar outro artefato". O teste de unidade não roda sobre o APK minificado, e o efeito
sobre a guarda do APK ninguém mediu.

## 5. O primeiro vermelho, sobre a árvore real

A previsão veio da tabela da decisão 2 do `design.md`: **exatamente** `LGPD com dados de menores`
(`3`) e `Uso offline não fecha ponta a ponta` (`4a`).

**Antes de rodar, a previsão foi corrigida (P7).** A contagem de "aguarda evento" estava em cinco, e
são seis. A linha do release, acrescentada no commit 3, também tem `antes-de`, e a previsão tinha
sido escrita contando só as 16 linhas originais. As duas vencidas, que são o que a previsão testa,
não mudaram.

**Real = previsto**, às `22:34:15Z`, com saída `1`:

```
fatia corrente: 4b, de slice-4b-atribuicao-no-papel (arquivada), slice-4b-roster-entrega (arquivada), slice-4b-roster-no-aparelho (arquivada), slice-4b-outbox-de-resultado (arquivada)
eventos declarados: nenhum
...
vence nesta fatia (4b): nenhuma

::error::linha vencida sem reconciliacao: LGPD com dados de menores (`3`): a fatia 3 ja passou, e a corrente e 4b
::error::linha vencida sem reconciliacao: Uso offline não fecha ponta a ponta (§10) (`4a`): a fatia 4a ja passou, e a corrente e 4b
```

As 18 linhas ficaram assim, como previsto: 2 vencidas, 3 pagas, 2 contínuas, 6 aguardando evento e
5 em dia. O commit `01445b1` guarda esse estado: quem fizer `checkout` dele e rodar a guarda vê o
mesmo vermelho.

## 6. A reconciliação: a primeira sob a P27

- **Uso offline → `4a` `paga`.** Estava paga desde 2026-09-10, e a linha não o dizia. O §15 registra a
  condição "satisfeita", e `cobertura-fatia-4a-cache-referencia.md:112-124` mostra a cadeia inteira,
  até a câmera, num processo nascido em modo avião. É medição de 2026-09-10, e não foi repetida.
  **Consequência que a tarefa não previa:** a tabela de **cima** do §16 dizia "Aberto" para o mesmo
  risco. Por decisão do mantenedor, ela ganhou uma nota datada, com o texto antigo mantido, no mesmo
  commit.
- **LGPD → `antes-de:primeiro-piloto`**, por decisão do mantenedor, entre três opções. O expurgo foi
  pago. A interface não existe (política §3.5 e §4). O `3` era o momento esperado do primeiro piloto
  com turma real, e ele não aconteceu. O `3` fica riscado na célula, e o motivo abre a coluna ao lado.

Depois disso, às `22:47:04Z`, a guarda saiu `0`: 18 linhas lidas, nenhuma vencida, 4 pagas, 2
contínuas, 7 aguardando evento e 5 em dia.

## 7. A guarda contra a árvore mutada: conjunto previsto e conjunto real

As mutações da decisão 8 do `design.md`. M1, M2 e M6 são por parâmetro, e não tocam a árvore. As que
editam arquivo (M3, M4, M5, M7 e M8) levaram `MUTACAO` na linha editada. Cada uma foi revertida com
`git checkout`, e M4 e M5 foram revertidas juntas. `git diff --exit-code` deu `0` antes da mutação
seguinte, e a guarda rodou de novo depois de cada reversão, com saída `0`.

| # | Mutação | Previsto | Real |
|---|---|---|---|
| M1 | cópia dos nomes de `openspec/changes/` com `slice-6-mutacao` | saída `1`, exatamente as três linhas com `5` | **=** (`22:48:28Z`). E, como a decisão 5 manda, `Custo de IA` (`6`) em "vence nesta fatia (6)", sem reprovar |
| M2 | a mesma com `slice-5-mutacao` | saída `0`, e as três em "vence nesta fatia" | **=** (`22:48:37Z`) |
| M3 | sem `paga` em ArUcos (`2b`) | saída `1`, só ArUcos: a comparação pelo número | **=** (`22:48:46Z`) |
| M4 | sem `paga` em Uso offline (`4a`) | saída `1`, só Uso offline: a comparação pela letra | **=** (`22:48:47Z`) |
| M5 | M4, com a derivação ignorando a letra | saída **`0`**, e a corrente impressa como `4` | **=** (`22:48:56Z`). O defeito esconde a vencida de M4 |
| M6 | `--ocorrido piloto-nominal` | saída `1`, exatamente a classe H e a regra de extração | **=** (`22:49:07Z`) |
| M7 | `` `piloto-nomial` `` na linha de eventos | saída `2`, "evento declarado que nenhuma linha usa" | **=** (`22:49:07Z`) |
| M8 | sem o token de `Custo de IA` | saída `2`, com `Custo de IA` nomeada | **=** (`22:49:07Z`) |

**M5 mostra o limite, e não a cobertura.** Um defeito na leitura da letra só muda o veredito de uma
linha em aberto cujo limite tem o mesmo número da corrente. Na árvore real não existe nenhuma linha
assim. Esse defeito, sozinho, não mudaria nenhum código de saída, nem aqui nem no CI.

**M6 mostrou, ao vivo, o risco do evento implicado.** A linha LGPD (`primeiro-piloto`) **não** cai,
embora um piloto nominal seja também o primeiro piloto. A guarda não infere implicação. O §16 diz que
quem declara um evento declara também os que ele implica, e a guarda não confere isso.

## 8. O passo "continua capaz de falhar", visto falhar

Os dois passos novos, rodados localmente com o texto exato do `ci.yml` às `22:50:54Z` e `22:50:55Z`,
saíram os dois com `exit 0`.

Depois, o segundo passo contra duas mutações na guarda, **com as previsões corrigidas antes de rodar
(P7)**. A tarefa previa que, com `paga` sem segurar, só o canário `` `0` `paga` `` seria acusado. Está
errado: as linhas **reais** pagas com fatia passada (`2b` e `4a`) também vencem. Por isso entrou uma
segunda mutação, que isola uma camada:

| Mutação | Previsto | Real |
|---|---|---|
| **7.3a**: `paga` deixa de segurar | três erros: o canário `0` não é a única vencida; o canário `0` `paga` sai `1`; o canário de evento sem `--ocorrido` sai `1` | **=** (`22:51:25Z`) |
| **7.3b**: o evento nunca conta como ocorrido | um erro só: o canário de evento com `--ocorrido` sai `0` | **=** (`22:51:26Z`) |

**Ver falhar mostrou uma falha de diagnóstico, e ela foi consertada no mesmo commit.** Na 7.3a, dois
erros saíam com texto idêntico, porque a cópia é regravada a cada canário e o caminho não distingue os
casos. Cada caso passou a ser nomeado. As duas mutações foram rodadas de novo, às `22:52:16Z`, com as
mensagens já distintas. Depois disso, os dois passos limpos saíram `0` às `22:52:17Z` e `22:52:18Z`.

## 9. A reversão, rodada

**Às `22:49:17Z`, depois de M1–M8:**
- a guarda sai `0`;
- `git status` mostra só `tasks.md`;
- **`MUTACAO` aparece em 0 linhas** nos arquivos não-prosa que a mudança tocou até ali (§16,
  `rigorous.md` e `divida.mjs`) e em todo o código-fonte (`tools`, `apps`, `packages`, `.github` e
  `buildSrc`).

**Às `22:52:18Z`, depois de 7.3a e 7.3b:**
- `git diff --exit-code` na guarda sai `0`;
- os dois passos limpos saem `0`;
- `grep -rn MUTACAO tools .github` dá 0 linhas.

O `git status` não foi conferido nesse segundo momento, e a conferência completa da árvore é a da
tarefa 10.1.

**O `grep -rn "MUTACAO"` literal não dá 0, e nunca deu nesta base.** A palavra está na prosa de 28
documentos, e são os mesmos 28 que `git grep` acha em `4c8e473`. Está também no `design.md` e no
`tasks.md` desta mudança, que descrevem o método. A primeira redação desta conferência excluía
diretórios inteiros de prosa, e foi refeita: excluir um diretório deixaria passar uma marca plantada
nele.

## 10. O que o CI prova, e o que só esta mudança provou

O passo "continua capaz de falhar" prova, a cada CI, a **comparação**:
- a fatia passada vence;
- `paga` segura;
- o evento dispara só quando ocorre;
- os dois pisos falham fechados.

Ele **não** prova a leitura da linha de eventos, nem a derivação a partir de nomes reais além do
piso. Essas duas foram provadas **aqui**, por M1, M2, M5 e M7, e daqui em diante ficam protegidas só
pela falha fechada da própria guarda. É a mesma divisão da 7.1.

## 11. O que **não** fica verificado (P8)

- **A frase que não pode faltar** (`design.md`, decisão 12): **a guarda prova que nenhuma linha do §16
  está vencida, e não que a dívida foi paga.**
  - `paga` é afirmação humana, e a guarda não sabe se o item foi entregue.
  - Um reagendamento sem motivo passa, porque ela lê o token e não a prosa.
  - Um evento que aconteceu e ninguém declarou não dispara nada.
  - A dívida que nunca entrou no §16 continua invisível. É exatamente o que a P27 proíbe, e o que a
    guarda não tem como achar (P16: a guarda é a camada vizinha da reconciliação humana, e não ela).
- **O evento implicado não é inferido.** Isso foi visto na M6 (§7). Quem declarar `piloto-nominal`
  precisa declarar `primeiro-piloto` também. O §16 diz isso, e nada o confere.
- **A convenção de nome não é imposta.** Uma fatia proposta sem `slice-` no nome não move a corrente, e
  nada acusa. Um nome que começa com `slice-` e foge da forma sai com `2`, e esse é o limite do que a
  guarda consegue.
- **A leitura da letra da fatia tem proteção fraca** (M5). Um defeito nela só muda código de saída
  quando existe uma linha em aberto com o mesmo número da corrente.
- **O passivo em prosa anterior à P27 não foi migrado.** As tabelas de débito dos `docs/cobertura-*.md`
  (por exemplo `cobertura-fatia-4a-cache-referencia.md`, "a próxima que tocar essa tela") continuam só
  lá. A P27 vale daqui para frente. As quatro linhas que já tinham custado entraram na ETAPA 1, e as
  duas do §9 do plano entram aqui. **Não é mitigado, é conhecido.**
- **Os eventos não têm sinal automático.** `migration-da-5-em-producao` acontece **durante** a fatia 5,
  e só vence na guarda se alguém a declarar. A instrução do archive no `CLAUDE.md` é o que faz alguém
  olhar.
- **Node 22.** O local é 24.19.0. O script usa só `node:` estável, e isso é **suposto** até o log do CI
  da PR.
- **`./gradlew build` não rodou localmente**, porque nenhuma entrada dele muda (§2). O `build` do CI da
  PR roda o Gradle inteiro, e é lido no destino.
- **As falhas fechadas que não foram plantadas:** seção ausente, coluna ausente, número de colunas
  errado, `continuo` com `paga`, e nome `slice-` fora da forma. Todas saem com `2` **por leitura do
  código**. Só o token ausente (M8), o evento não usado (M7), os dois pisos e os parâmetros inválidos
  foram vistos.

## 12. Correções de registro feitas nesta sessão (P7)

- **Horários estimados.** Na 0.2, na 1.1, na 2.1, na 2.3 e na correção da 4.2, a primeira redação do
  `tasks.md` trazia horários escritos por estimativa, e não lidos. Eles foram trocados por âncoras
  verificáveis (o horário de um commit, ou o de uma execução lida), antes de o `tasks.md` ser
  commitado. A estimativa da 4.2 dizia `22:43Z`, para um texto escrito antes de uma execução que
  aconteceu às `22:34:15Z`.
- **Fim de linha.** O `python` do Windows gravou o §16 e o `tasks.md` em CRLF, e o Git avisou. Os dois
  foram regravados em LF antes do commit, e `git ls-files --eol` mostra `w/lf`. A guarda rodou uma vez
  sobre a cópia com CRLF e leu as 18 linhas. O `grep -c $'\r'` do Git Bash dava contagem falsa, e as
  leituras que valeram foram a contagem pelo Node e o `git ls-files --eol`.

## 13. O fechamento

**Local, depois de todas as reversões:** é a tarefa 10.1.

**O CI da PR, lido no destino:** ainda não, é a tarefa 10.3.

## 14. O quadro de fechamento (plano, §10)

- **O comando cheio, e o exato (P5).** Local: os dez passos dos conferidores do job `web`, extraídos
  do `ci.yml` (§13). No destino: os três jobs do CI da PR, que é o comando cheio desta mudança.
- **O `timestamp` (P2, P3).** Cada execução citada aqui traz o horário lido do relógio ou do próprio
  instrumento. Nenhuma contagem vem de relatório de teste, porque esta mudança não roda suíte de teste.
- **O sinal, e o passo que ele atravessa (P2).** A guarda lê a célula **do documento real** e os
  **nomes reais** dos diretórios. O primeiro vermelho (§5) atravessou a leitura e a comparação sobre a
  árvore real. O passo "continua capaz de falhar" atravessa só a comparação (§10).
- **O oráculo, e por que é independente (P4).** Um script Node que lê texto e nomes de diretório, e
  nada do produto. O "previsto" de cada mutação foi escrito por leitura da tabela, antes de a mutação
  rodar.
- **A mutação e o conjunto que caiu (P9).** §5, §7 e §8: real = previsto em todas. Duas previsões
  foram corrigidas **antes** de rodar, e as duas correções ficam escritas.
- **`MUTACAO`, e a reversão rodada (P10).** §9.
- **O que ficou sem verificação automática (P8).** §11.
- **Número ou hash que mudou (P3, P23).** Nenhum.
- **A reconciliação do §16 (P27), a primeira a valer.** Esta mudança não alcançou nenhuma linha por
  fatia: a corrente ficou `4b` do começo ao fim. Duas linhas **já vencidas** foram nomeadas pela guarda
  e reconciliadas (§6): `Uso offline` foi **paga**, e `LGPD` foi **reagendada** para
  `antes-de:primeiro-piloto`, com motivo. Duas linhas **novas** entraram (`assessment_fact` com `9`, e
  o release com `antes-de:lancamento`). Na fatia 5, três linhas vencem nela: `Acurácia em
  manuscrito`, `Modo degradado` e `O limiar do OMR`. É o que o primeiro `/opsx:propose` da 5 tem de
  nomear.
