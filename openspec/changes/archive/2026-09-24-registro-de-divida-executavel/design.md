## Context

A motivação está em `proposal.md` (Why). Aqui fica só o estado que decide a abordagem.

**A tabela que a guarda vai ler, hoje** (2026-09-24, sobre `4c8e473`): `ARQUITETURA-FINAL-v3.md`
§16, "Ponto de não-retorno", linhas 500–517. Tem 16 linhas e 4 colunas (`Risco`, `Fatia-limite`,
`O que encarece depois dela`, `Dono`). Cada linha tem exatamente 5 `|` e nenhum `\|` escapado:
conferido por `awk` e `grep -F`. A coluna "Fatia-limite" é prosa livre. Algumas células são só
`**5**`, outras são frases de três linhas.

**A fatia corrente, lida da árvore.** As 29 mudanças arquivadas se dividem em 16 `slice-*` e 13 sem o
prefixo. A maior fatia é `4b`, com quatro mudanças (`slice-4b-atribuicao-no-papel`,
`-roster-entrega`, `-roster-no-aparelho` e `-outbox-de-resultado`), e `openspec/changes/` não tem
nenhuma ativa além desta. O padrão dos nomes é `slice-<maior>[-<menor>][<letra>]-<nome>`:
`slice-1-5-math-rendering` é a `1.5`, e `slice-1-layout-engine` é a `1`.

**Nada no build lê os arquivos que esta mudança edita.** `ARQUITETURA-FINAL-v3.md`, `rigorous.md`,
`CLAUDE.md` e `openspec/changes/` aparecem em `apps/`, `packages/`, `buildSrc/` e `tools/` só dentro de
KDoc. Conferido por busca, e não por execução. Então nenhuma entrada do Gradle ou do Vitest muda.

**O molde.** `limiar.mjs`, `answer-kind.mjs`, `fio.mjs` e `renderizador.mjs` rodam no job `web` do
`ci.yml`. Cada um tem dois passos: um que confere e outro que prova, a cada CI, que a conferência
continua capaz de falhar. Os dois códigos de saída de falha seguem o contrato de `fio.mjs`: `1` quer
dizer "a árvore está errada", e `2` quer dizer "não sei ler a árvore".

**Duas citações do plano derivaram**, e se conferem na hora e não se copiam (P21):
`device-session/spec.md:352` agora é `:395`, e `deploy-api.md:423` agora é `:459`.

## Goals / Non-Goals

**Goals:**

- A dívida com prazo passa a ter **um** registro, e esse registro passa a ser **reprovável**. Uma
  linha vencida sem reconciliação deixa o CI vermelho e aparece nomeada.
- A guarda nasce vermelha **sobre a árvore real**, nomeando exatamente o que a leitura previu, e esse
  vermelho fica na história.
- As duas regras entram em `rigorous.md` com os incidentes que as pagaram, e o workflow do
  `CLAUDE.md` passa a pedir a reconciliação no propose e no archive.

**Non-Goals:**

- **Verificar que uma linha `paga` foi paga.** O marcador é afirmação humana, e a guarda confere
  prazo, não entrega (decisão 12).
- **Achar dívida que nunca entrou no §16.** A P27 proíbe adiar só em prosa, e a guarda não lê prosa
  (decisões já tomadas).
- **Migrar o passivo das tabelas de débito dos `docs/cobertura-*.md`.**
- **Conferir o motivo de um reagendamento.** A guarda só sabe que a linha não está vencida.

## Decisions

### 1. As duas regras, com o texto do plano, numa seção nova de `rigorous.md`

**O texto de P27 e P28 é o da ETAPA 8 do plano, transcrito sem edição.** O plano é nível 5 até virar
`design.md`, e este parágrafo é o que o faz nível 3. A partir daqui, mudar o texto exige
`/opsx:update`.

**O lugar é uma seção nova, "E. Depois do archive, e entre módulos"**, depois da D. A numeração das
regras segue a ordem de entrada, e as seções A–D estão em sequência (P1–P8, P9–P16, P17–P21,
P22–P26). Pôr a P27 dentro da C, ao lado da P20, quebraria essa sequência. O título diz os dois eixos
que a auditoria isolou: o **tempo**, porque o que vence depois do archive não tem quem o cobre
(auditoria §7), e o **espaço**, porque o que atravessa dois módulos não tem camada que o veja
(diagnóstico da ETAPA 8).

**Os incidentes, com commit, arquivo e linha** (§10), conferidos na hora da escrita com `git log -S`
e `grep -n`, e não copiados do plano. Duas linhas do plano já derivaram. Para a P27 são os quatro do
plano:

| Incidente | Onde (conferido em 2026-09-24) |
|---|---|
| Modo degradado | auditoria 2.3; `openspec/specs/device-session/spec.md:395` (`:352` na auditoria), que entrou na spec principal em `6f2dfe9`; ADR-0013 linha 138, "são 4b e 4c" |
| Variante release | auditoria 3.1; `docs/cobertura-fatia-4a-cache-referencia.md:220`; o gatilho disparou em `d054e1f` e em `generatejooq-sem-registro-automatico`, e a dívida foi documentada sem ser paga em `438030a`; paga na 7.2 |
| Migration em produção | auditoria 4.6; `docs/deploy-api.md:459` (`:423` na auditoria); o HTTP 500 está em `docs/cobertura-slice-4b-outbox-de-resultado.md` §5.1.1, item 2 |
| Reexame do limiar | auditoria 4.7; `docs/cobertura-fatia-3c.md:266`, "continua aberta" |

E há um **quinto incidente**, que o plano não podia listar porque é posterior a ele. A nota de
2026-09-23 no §2 do plano registra que a linha do APK de release, que o próprio plano mandava pôr no
§16 ("Fica na tabela do §16 com essa fatia-limite"), **nunca entrou**. É o padrão da P27 na forma mais
pura: um prazo escrito em prosa, com instrução explícita de ir para a tabela, e ninguém o levou. O
plano diz que a ETAPA 8 espera a 7 "porque é o último incidente que a regra precisa citar", e o
incidente é este.

Para a P28, os incidentes são três, e o precedente é `tools/parity/limiar.mjs` (`8e4e1b3`):

| Incidente | Onde | Fechado em |
|---|---|---|
| A versão do renderizador em três registros | auditoria 4.4 | `54160b9` (7.1) |
| O contrato do fio em quatro espelhos | auditoria 2.1 | ETAPA 6 (o commit se confere na escrita) |
| A instância de Room por três caminhos | auditoria 3.2 | ETAPA 5 (idem) |

**P27 é [V]. P28 não é** (é o que o plano marca). A lista normativa do §4 passa de "São quinze" para
"São dezesseis", com a P27 incluída. O §4 existe para que mexer na zona vermelha seja uma edição
visível, e acrescentar uma regra também é mexer nela.

**O §8 ganha uma linha**, na lista de fechamento de fatia: "as linhas do §16 que esta mudança alcançou
estão reconciliadas, pagas ou reagendadas (P27)". **Não é uma terceira regra.** É a cláusula de
fechamento da própria P27 ("fechar exige reconciliá-lo") posta onde os fechamentos se leem. Hoje ela
só existe no quadro do §10 do plano ("A partir da etapa 8: a reconciliação…"), e esse quadro sai de
circulação com a banda. Sem a linha, o §8 permanente contradiria a P27.

### 2. O formato da célula "Fatia-limite": um token no começo, e a prosa intacta depois

```
célula  := token [ marca ] prosa
token   := `<fatia>` | `antes-de:<evento>` | `continuo`
fatia   := <inteiro>[.<inteiro>][<letra>]        ex.: `5`  `2b`  `1.5`  `4a`
evento  := [a-z0-9]+(-[a-z0-9]+)*                ex.: `piloto-nominal`
marca   := `paga`
```

**Entre crases**, pela mesma razão que o marcador `[retencao:<classe>]` existe (plano, ETAPA 8): a
prosa não é parseável, e o marcador é o que a guarda lê. A crase também separa a parte de máquina da
parte de leitura. Só o token e a marca **do começo** da célula contam. Uma crase no meio da prosa,
como em `` `/opsx:propose` ``, é prosa.

**Quando a linha é paga, o limite fica, e `paga` vem ao lado.** Há duas razões. A primeira é a P7: o
limite que valia continua visível, e a leitura não precisa de histórico. A segunda é que os eventos
das linhas pagas continuam referenciados, e é isso que torna possível a conferência do evento
declarado que nenhuma linha usa (decisão 3).

**A passada de formato não decide nada.** Ela segue três regras, e só elas:

- o token é o que a célula **já diz**. `**5**` sozinho vira `` `5` ``. Nos outros casos o token
  entra antes da prosa, que fica intacta;
- `paga` entra **só** onde a própria linha já diz que fechou ("alcançada", "Fechado em", "fechado
  em");
- nenhum limite muda.

A leitura, linha por linha, que é também a previsão do primeiro vermelho (decisão 9):

| Linha (`Risco`) | A célula diz hoje | Token | Estado com a corrente em `4b` |
|---|---|---|---|
| Divergência entre renderizadores | contínuo, verificado a cada CI | `continuo` | contínuo |
| Acurácia em manuscrito | **5** (medir antes de construir a 8) | `5` | em dia |
| Impressão dos ArUcos | **2b — alcançada** | `2b` `paga` | paga |
| Custo de IA | **6** | `6` | em dia |
| **LGPD com dados de menores** | **3** (primeiro piloto com turma real) | `3` | **vencida** |
| **Uso offline não fecha ponta a ponta** | antes de `/opsx:propose` rodar para a 4b | `4a` | **vencida** |
| A classe H não enumera o roster baixado | antes de qualquer piloto… em modo `nominal` | `antes-de:piloto-nominal` | aguarda evento |
| O roster cacheado sem regra de apagamento | a fatia que puxa o roster… (**Fechado em 2026-09-16**, na coluna ao lado) | `4b` `paga` | paga |
| A retenção executável da classe B | antes do primeiro pedido de eliminação… ou do primeiro vínculo encerrado há 5 anos | `antes-de:primeira-eliminacao` | aguarda evento |
| A política §10.8 diverge do comportamento | antes da publicação da política | `antes-de:publicacao-da-politica` | aguarda evento |
| Modo degradado (§10) não existe | **5** | `5` | em dia |
| Migration não é aplicada por nenhum pipeline | antes de a primeira migration da fatia 5 ir a produção | `antes-de:migration-da-5-em-producao` | aguarda evento |
| O limiar do OMR… um aparelho e uma impressora | **5** (é a fatia do corpus) | `5` | em dia |
| A regra de extração… abaixo da API 31 | antes de qualquer piloto em modo `nominal`… | `antes-de:piloto-nominal` | aguarda evento |
| ~~A afirmação de que a credencial…~~ fechado em 2026-09-23 | antes do lançamento | `antes-de:lancamento` `paga` | paga |
| Um mantenedor, quatro módulos | contínuo | `continuo` | contínuo |

**`4a` para "antes de `/opsx:propose` rodar para a 4b"** é a tradução direta: o que tem de estar
feito antes de a 4b começar tem de estar feito dentro da 4a. **`primeira-eliminacao`** cobre as duas
condições da célula ("pedido de eliminação" **ou** "vínculo encerrado há 5 anos"): as duas são o
primeiro momento em que um dado identificável precisa sair, e a prosa continua nomeando cada uma.

**As duas linhas novas do §9 do plano** (decisão do mantenedor, 2026-09-24) entram em commit
próprio, depois da passada de formato, porque acrescentam conteúdo e não formato (P25):

| Linha | Token | De onde vem o texto |
|---|---|---|
| `assessment_fact` não existe | `9` | plano §9, "Adiamento **correto**: o insumo está preservado em `answer_observation` e a derivação por junção com o pacote imutável continua possível"; e a origem do adiamento, `docs/cobertura-slice-4b-outbox-de-resultado.md:378` |
| `minifyEnabled`, assinatura e `versionCode` | `antes-de:lancamento` | plano §9, "Trabalho de lançamento", com limite "fatia comercial" |

**`antes-de:lancamento`, e não um número, para a segunda**: o §9 diz "fatia comercial", e esse nome
não é linha do §15. O que o item bloqueia é a entrega do APK ao professor, e a 7.2 já chamou esse
evento de "o lançamento" ("não bloqueia a 5. Bloqueia o **lançamento**"). A prosa guarda "fatia
comercial" como estava. Se o mantenedor preferir amarrar a um número (a `8` fecha a monetização no
§15), a troca é de uma célula, na revisão desta proposta.

**Um parágrafo curto acima da tabela** diz o formato e aponta para a guarda. É curto porque a
gramática que vale é a do cabeçalho de `divida.mjs`, que é quem a lê. O parágrafo só evita que quem
lê o §16 pense que o `` `5` `` é tipografia.

### 3. Os eventos que já ocorreram: uma linha acima da tabela, e a única coisa declarada à mão

```
**Eventos que já ocorreram:** nenhum.
```

Evento é fato do mundo, como "o piloto nominal começou" ou "a política foi publicada", e nenhum
arquivo da árvore o registra. Não há de onde derivá-lo. A única forma de a guarda vê-lo é alguém
declará-lo, e o lugar é ao lado da tabela que ele dispara. Quando houver eventos, cada um entra entre
crases, com a data e a prosa que quiser ao lado.

**A linha falha fechada, com saída `2`**, em três casos:

- a linha não existe;
- não tem nenhum código e não diz `nenhum`. Isso pega o evento escrito sem crase, que seria lido
  como "zero eventos" em silêncio;
- declara um evento que **nenhuma** linha da tabela usa, paga ou não. É o erro de digitação:
  `piloto-nomial` declarado deixaria `piloto-nominal` sem disparar, sem nada acusar. Essa conferência
  só existe porque a decisão 2 mantém o token das linhas pagas.

**O que ela não pega, e fica como lacuna (P8):** o evento que aconteceu e ninguém declarou. Nenhuma
guarda vê isso. É a instrução do archive (decisão 10) que faz alguém olhar.

### 4. A fatia corrente é derivada dos nomes das mudanças, e não declarada

**Decisão do mantenedor, 2026-09-24**, entre as duas opções apresentadas. A guarda lê os nomes dos
diretórios de `openspec/changes/` (os ativos, exceto `archive`) e de `openspec/changes/archive/`, sem o
prefixo `AAAA-MM-DD-`. Considera os que seguem `slice-<maior>[-<menor>][<letra>]-<nome>` e toma o
maior. Hoje: `4b`.

**Por que não declarar.** O plano diz "declarada em um lugar só", e o nome da mudança **já é** essa
declaração: `/opsx:propose slice-5-…` é o ato que diz que a fatia 5 começou. Uma linha digitada seria
um segundo registro do mesmo fato, que é o espelho cego da P28. E seria o pior tipo, porque o modo de
falha dela é o silêncio: se ninguém lembrar de atualizar a linha, a guarda segue verde conferindo
contra a fatia errada. É a P13 no eixo do tempo. O valor derivado se move sozinho no primeiro propose
da 5.

**A ordem das fatias.** Compara-se o número (`1` < `1.5` < `1.6` < `2`) e, com números iguais, a
letra (`4a` < `4b`). Uma fatia sem letra é a fatia inteira (decisão 5).

**O custo, e ele fica escrito.** A derivação depende da convenção do §2 do plano: "mudança que não é
fatia não carrega `slice-`". A guarda dá a essa convenção um consumidor, e o cabeçalho dela a
escreve. Mas a guarda **não consegue impô-la**: uma fatia proposta com outro nome não move a fatia
corrente, e nada acusa. É lacuna declarada, e não mitigada.

**Piso:** zero mudanças `slice-*` sai com `2`.

### 5. Quando uma linha vence: quando a fatia seguinte começa, e não durante a própria

Para um token de fatia `L` e a corrente `C`, **a linha está vencida se `C` vem estritamente depois de
`L`**. Isso acontece quando o número de `C` é maior que o de `L`, ou quando os números são iguais, `L`
tem letra, e a letra de `C` vem depois. Um `L` sem letra (`5`) cobre a fatia inteira (`5a`, `5b`, …)
e só vence quando aparece uma fatia de número maior.

**Por que não `C >= L`.** "Fatia-limite 5" quer dizer "paga dentro da 5". Com `>=`, o CI ficaria
vermelho durante toda a fatia 5, que é justamente o trabalho que paga essas linhas, e vermelho
permanente é vermelho que ninguém lê. A guarda dispara **na abertura da fatia seguinte**, que é o
ponto exato onde a auditoria situou a falha: "nenhum dos dois é lido na abertura da fatia seguinte"
(§7).

**`C == L` não reprova, mas aparece.** A guarda lista essas linhas à parte, como "vence nesta
fatia", sem mudar o código de saída. É a lista que a linha do propose no `CLAUDE.md` pede que a
proposta nomeie (decisão 10).

Os outros tokens:

- `antes-de:E` vence quando `E` está declarado (decisão 3) ou forçado (`--ocorrido`);
- `continuo` nunca vence, e `continuo` com `paga` sai com `2`, porque risco contínuo não tem prazo
  para ser pago e a combinação é uma célula malformada;
- qualquer token com `paga` nunca vence.

### 6. O que a guarda lê, o que ela diz, e quando sai com `2`

**`tools/divida/divida.mjs`**, no caminho que o plano nomeia. Não fica em `tools/parity` porque não
confere registros do produto. Confere o registro de **dívida**. É Node, e não teste JVM, pela razão da
7.1: lê um documento Markdown e nomes de diretório, não há Kotlin para importar, e o job `web` já tem
Node. Usa só `node:fs`, `node:path` e `node:url`, com os caminhos resolvidos a partir do próprio
script (`import.meta.url`). Aceita `\n` e `\r\n`.

**Leitura:**

- a seção que começa em `### Ponto de não-retorno`, dentro de `## 16.`;
- a primeira tabela dessa seção, com a coluna achada **pelo nome do cabeçalho** (`Fatia-limite`), e
  não pela posição;
- o rótulo de cada linha é a coluna `Risco` sem `**`, `~~` e crases, e é o que aparece nas mensagens;
- toda linha precisa ter o número de colunas do cabeçalho.

**Saída `2`, falha fechada, nomeando o que não leu:**

- a seção, a tabela ou a coluna não existe;
- uma linha tem outro número de colunas;
- uma linha não começa com token;
- um token está fora da gramática;
- `continuo` aparece com `paga`;
- a linha de eventos está malformada (decisão 3);
- **piso:** zero linhas na tabela, ou zero mudanças `slice-*`;
- o parâmetro é desconhecido.

A regra para o parâmetro desconhecido é a de `renderizador.mjs`: um `--ocorrdo` digitado errado
rodaria a conferência simples e o passo do CI leria o verde como "aceitou".

**Saída `1`:** pelo menos uma linha vencida. Cada uma vira uma linha `::error::`, com o rótulo, o
token e o motivo ("a fatia `3` já passou: a corrente é `4b`, de `slice-4b-…`", ou "o evento
`piloto-nominal` já ocorreu").

**Saída `0`:** nada vencido. Em todos os casos a guarda imprime a fatia corrente e a mudança de onde
ela veio, os eventos declarados, cada linha com token e estado, e à parte as que "vencem nesta
fatia".

**Parâmetros**, os três só para os defeitos plantados:

- `--arquitetura <arquivo>`, para ler uma cópia fora do checkout;
- `--mudancas <dir>`, idem;
- `--ocorrido <evento>`, repetível, para forçar um evento.

### 7. Os dois passos no `ci.yml`, no job `web`, depois dos da versão do renderizador

1. **"O registro de divida do §16 esta em dia"**: `node tools/divida/divida.mjs`.
2. **"A verificacao da divida continua capaz de falhar"**: os defeitos são plantados **fora do
   checkout**, como no passo do fio, numa cópia temporária do documento com uma **linha-canário**
   inserida logo depois da linha separadora da tabela:
   - canário `` `0` ``: saída `1`, o canário nomeado, e **exatamente uma** linha vencida. O passo
     anterior passou, então qualquer outra linha vencida já estaria vermelha lá. Duas aqui quer
     dizer que a guarda lê a cópia de outro jeito;
   - canário `` `0` `paga` ``: saída `0`. A marca é o que segura;
   - canário `` `antes-de:canario-do-ci` `` com `--ocorrido canario-do-ci`: saída `1`, o canário
     nomeado. **Sem** o parâmetro: saída `0`. O evento é o que dispara;
   - uma cópia com as linhas da tabela removidas: saída `2`, com `piso`;
   - `--mudancas` apontando para um diretório vazio: saída `2`, com `piso`.

O motivo é conferido, e não só o código de saída. A saída capturada só é impressa quando o passo
falha, para que os `::error::` do defeito plantado não virem anotação num CI verde (precedente: o
passo da versão do renderizador).

**Por que `0` para o canário.** É anterior a qualquer fatia que um dia será a corrente, então o passo
continua estável enquanto a tabela real muda. **Por que inserir depois da separadora**, e não depois
da última linha: a separadora é a única linha estável da tabela, e o cabeçalho `| Risco |
Fatia-limite |` é único no documento. O risco do §16 acima dela tem outro cabeçalho.

**O que o passo do CI não prova**, e fica na cobertura (P8): a leitura da linha de eventos e a
**derivação** a partir de nomes reais além do piso. As duas se provam **nesta mudança**, por mutação
(decisão 8), e depois ficam protegidas pela falha fechada. É a mesma divisão da 7.1: o CI prova a
comparação, e a mudança prova a leitura.

### 8. Ver falhar: primeiro vermelho, reconciliação e mutações locais

**O primeiro vermelho, sobre a árvore real e sem defeito plantado** (decisão 9). Depois da
reconciliação, a guarda fica verde, e as mutações abaixo se aplicam, cada uma **revertida e rodada**
antes da próxima (P10). As que editam arquivo levam `MUTACAO` na linha. A tabela entra no `tasks.md`
como está:

| # | Mutação | Previsto |
|---|---|---|
| M1 | `--mudancas` numa cópia de `openspec/changes/` com um `slice-6-mutacao` a mais | saída `1`, **exatamente** `Acurácia em manuscrito`, `Modo degradado (§10) não existe` e `O limiar do OMR…`, as três com `5` e sem `paga`. Nenhuma linha paga cai |
| M2 | a mesma cópia com `slice-5-mutacao` no lugar | saída `0`, e as mesmas três em "vence nesta fatia". É a fronteira: `==` não reprova |
| M3 | tirar o `paga` de `Impressão dos ArUcos` (`2b`), no documento real | saída `1`, **só** `Impressão dos ArUcos`. É a comparação pelo número (`2` < `4`) |
| M4 | tirar o `paga` de `Uso offline não fecha ponta a ponta` (`4a`), no documento real | saída `1`, **só** `Uso offline…`. É a comparação pela letra (`4a` < `4b`) |
| M5 | M4, e no script a derivação passa a ignorar a letra (`4b` vira `4`) | saída **`0`**, com a corrente impressa como `4`. O defeito **esconde** a linha vencida de M4, e é assim que se vê que a derivação lê a letra |
| M6 | `--ocorrido piloto-nominal` | saída `1`, **exatamente** `A classe H não enumera o roster baixado` e `A regra de extração… abaixo da API 31` |
| M7 | declarar `` `piloto-nomial` `` na linha de eventos real | saída `2`, "evento declarado que nenhuma linha usa", com o nome |
| M8 | apagar o token de `Custo de IA` no documento real | saída `2`, `Custo de IA` nomeada |

**M5 mostra o limite da derivação, e não só a cobertura dela.** Um defeito na leitura da letra só
muda o veredito de uma linha em aberto cujo limite tem o mesmo número da corrente. Na árvore real,
depois da reconciliação, não existe nenhuma linha assim. Então esse defeito, sozinho, não mudaria
nenhum código de saída, nem aqui nem no CI: só o valor impresso da corrente o denunciaria. É a divisão
da 7.1: a leitura é provada uma vez, por mutação, e depois fica protegida pela falha fechada. Aqui a
proteção é mais fraca, e isso fica escrito na cobertura.

### 9. O primeiro vermelho, e a reconciliação em commit separado

**A guarda entra sozinha**, sem o passo do CI, depois da passada de formato e das duas linhas novas.
A previsão, por leitura da tabela da decisão 2: **saída `1`, exatamente `LGPD com dados de menores`
(`3`) e `Uso offline não fecha ponta a ponta` (`4a`)**, com a corrente em `4b`. O commit diz que a
guarda está vermelha, como o commit 1 da ETAPA 3 e o da 7.3. O vermelho é sobre a árvore real e
reproduzível por quem fizer `checkout` daquele commit.

**Por que a reconciliação não vai na passada de formato:** se fosse, a guarda nasceria verde, e nunca
teria sido vista reprovando a árvore real (P9).

**A reconciliação, em commit próprio:**

- **Uso offline → `4a` `paga`.** A evidência já está registrada: o §15 diz que a condição "foi
  **satisfeita** em 2026-09-10", com o archive de `slice-4a-cache-referencia`, e
  `docs/cobertura-fatia-4a-cache-referencia.md:112-124` mediu a câmera abrindo sem rede num processo
  que nasceu em modo avião. A frase entra na linha, datada e com as duas fontes. Isso não é decisão
  nova: é o registro de uma verificação que já existia e que a linha não dizia.
- **LGPD → decisão do mantenedor**, perguntada na tarefa, com a leitura na mão. Das duas partes que a
  coluna `Dono` atribui, o **expurgo** foi pago: o da classe H saiu na 4b, e o da classe B ganhou
  linha própria. A **interface** não existe: a coerção do papel no primeiro cadastro de aluno
  (política §3.5) e o bloqueio de roster nominal sem contrato de operador (§4). O `3` estava na célula
  como o momento esperado do "primeiro piloto com turma real", que é o parêntese dela, e nenhum
  piloto aconteceu. **Sugestão:** reagendar para `antes-de:primeiro-piloto`, mantendo o `3` na prosa
  como o limite anterior, com o motivo. Isso acrescenta `primeiro-piloto` ao vocabulário.

**Regra de parada:** se o primeiro vermelho nomear outra linha, ou só uma das duas, **pare** (decisão
11).

### 10. As duas linhas do `CLAUDE.md`, e o archive desta mudança

No "Workflow OpenSpec", depois do passo 5 e antes do 6, como o plano manda:

- **No propose**, a proposta nomeia as linhas do §16 cujo gatilho a mudança vai alcançar.
  `node tools/divida/divida.mjs` lista as que vencem na fatia corrente.
- **No archive**, cada uma é reconciliada: paga, ou reagendada com fatia-limite nova e motivo
  (`rigorous.md` P27).

**O ponteiro para a guarda, na primeira linha, é o único acréscimo ao texto do plano.** Ele diz
**onde** achar a lista que a linha pede. Sem ele, quem propõe teria de ler dezesseis células de prosa
para achar três tokens.

**O archive desta mudança é o primeiro sob o regime novo**, e faz o que a P27 diz:

- nomeia as duas linhas reconciliadas, e diz que nenhuma foi alcançada por fatia;
- apaga do `CLAUDE.md` a linha temporária da banda, que manda a si mesma sair "no archive da última
  mudança da banda". `transferencia-entre-aparelhos` já foi arquivada, então esta é a última;
- deixa notas no plano (ETAPA 8 fechada) e na auditoria (§7), sem apagar nada (P7).

### 11. Regra de parada: o instrumento não se conserta para caber na previsão

Regra 0.5 do plano, e ela vale sobre toda esta mudança:

> Toda etapa prevê **qual conjunto de cenários deve cair** sob a mutação. Se o conjunto real for
> diferente do previsto — **mais, menos, ou outros** —, **pare**. Não conserte o instrumento, não
> afrouxe a asserção, não ajuste a previsão em silêncio. Escreva o conjunto real ao lado do previsto
> e diga o que ele significa (P7, P12, P14).

Aplicada aqui:

- **no primeiro vermelho** (decisão 9): o conjunto é `{LGPD, Uso offline}`, e nada mais;
- **na primeira leitura da fatia corrente**: `4b`. Qualquer outro valor quer dizer que a derivação ou
  o Context desta página estão errados;
- **em cada mutação de M1 a M8**: o conjunto da tabela. Se M1 nomear uma linha paga, `paga` não está
  segurando. Se M6 nomear três linhas, há um terceiro uso de `piloto-nominal` que a leitura não viu.

### 12. O que não pode faltar no registro

Vai inteiro para a seção do que não fica verificado:

> **A guarda prova que nenhuma linha do §16 está vencida, e não que a dívida foi paga.** `paga` é
> afirmação humana, e a guarda não sabe se o item foi entregue. Um reagendamento sem motivo passa,
> porque ela lê o token e não a prosa. Um evento que aconteceu e ninguém declarou não dispara nada. E
> a dívida que nunca entrou no §16 continua invisível, que é exatamente o que a P27 proíbe e o que a
> guarda não tem como achar (P16: a guarda é a camada vizinha da reconciliação humana, e não ela).

## Decisões já tomadas contra: o que é proibido na ETAPA 8

Da lista "Proibido nesta etapa" do plano, com as razões que ele dá. Não são alternativas em aberto:

- **Afrouxar qualquer coisa em nome de "menos cerimônia".** A regra é o `rigorous.md` §10: regra
  removida por incômodo, e não por evidência, confirma exatamente o argumento que a criou. Esta
  mudança **não remove nada**; acrescenta uma reconciliação por archive.
- **Criar uma terceira regra.** Três regras de uma vez é sobre-correção, e o §10 exige incidente por
  regra. O artefato de release é **um** incidente, já corrigido na 7.2. A linha do §8 da decisão 1 é
  cláusula da P27, e não regra nova.
- **Duplicar o §16 num YAML "para facilitar o parsing".** Seriam dois registros, que é o defeito que
  a P27 corrige.
- **Fazer a guarda ler `docs/cobertura-*.md`.** Prosa não é parseável, e a regra é justamente que a
  prosa **não** é o registro.

E as que saem das decisões acima:

- **Declarar a fatia corrente à mão** (decisão 4).
- **Mudar qualquer fatia-limite na passada de formato**, ou marcar `paga` onde a linha não diz que
  fechou (decisão 2). Reagendar é ato de reconciliação, com motivo, e acontece só nas duas linhas
  vencidas.
- **Juntar a reconciliação à passada de formato** (decisão 9).
- **Migrar o passivo das tabelas de débito dos `docs/cobertura-*.md`.** A P27 vale daqui para frente.
  As quatro linhas que custaram entraram na ETAPA 1, e as duas do §9 do plano entram aqui por decisão
  do mantenedor. O resto é lacuna declarada.
- **Corrigir `device-session/spec.md:395`.** É delta de spec, e pertence à fatia 5, a fatia-limite
  do modo degradado.
- **Tocar `limiar.mjs`, `answer-kind.mjs`, `fio.mjs` ou `renderizador.mjs`**, ou generalizar
  `divida.mjs` para "conferir prazos" fora do §16 (regra 8 do `CLAUDE.md`).

## Risks / Trade-offs

- **[A derivação depende de uma convenção de nome que a guarda não impõe]** → Uma fatia com outro
  nome não move a corrente. Mitigação: o cabeçalho da guarda escreve a convenção, e o piso reprova a
  árvore sem nenhuma fatia. O resto é lacuna declarada (decisão 4).
- **[Evento implicado por outro]** → `piloto-nominal` implica `primeiro-piloto` (se a sugestão da
  decisão 9 for aceita), e a guarda não sabe disso. Mitigação: o parágrafo do §16 diz que quem declara
  um evento declara também os que ele implica. A guarda não confere.
- **[CI vermelho por dívida, e não por defeito]** → É o objetivo. O vermelho só aparece na abertura
  da fatia seguinte (decisão 5), e a saída é reconciliar, que é uma edição de célula com motivo.
- **[O formato do documento muda: coluna renomeada, tabela partida]** → Falha fechada com saída `2` e
  o motivo. Custa um ajuste na guarda. Falhar aberto custaria a guarda.
- **[Diff grande numa tabela de células enormes]** → A passada de formato só acrescenta token e marca.
  Ela se confere com `git diff --word-diff`, e a mensagem do commit diz que nenhum limite mudou.
- **[Node 24 local, Node 22 no CI]** → Só `node:` estável. A execução em 22 se confere no log do CI da
  PR.
- **[PR empilhada]** → A base é `vewvniv/o-apk-de-release-e-verificado` (PR #61), ainda aberta. É a
  pilha que o plano decidiu em 2026-09-19, e o GitHub re-aponta a base quando a anterior entra.

## Migration Plan

Nada a migrar: nenhum dado, schema, contrato, artefato ou hash muda. A reversão é tirar os dois passos
do `ci.yml` e `tools/divida/`. Os tokens no §16 são inertes sem a guarda, e as regras de `rigorous.md`
só saem pelo §10, com medição.

**O comando cheio desta mudança é o CI da PR**, lido no destino (P26). O `build` roda o Gradle
inteiro em Linux, e o `web` roda os cinco conferidores. Localmente rodam a guarda e os passos do job
`web` que não dependem de artefato de build, com o **texto exato** do `ci.yml`, no Git Bash.
`./gradlew build` não roda localmente, porque nenhuma entrada dele muda (Context). Se o mantenedor
quiser, é pergunta antes de ligar o Docker (P22).

## Open Questions

- **A reconciliação da linha LGPD** (decisão 9). Ela é do mantenedor e é perguntada na tarefa,
  depois do primeiro vermelho, com a sugestão `antes-de:primeiro-piloto`. Qualquer resposta cabe na
  mesma tarefa e no mesmo commit, e não muda a abordagem.
