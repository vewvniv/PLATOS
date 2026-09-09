## Context

Ver `proposal.md` — Why. O que este documento acrescenta é o terreno medido em 2026-09-09, na
conferência em aparelho da `slice-4a-package-pull` e na exploração que a seguiu.

**Três relógios convivem no aparelho, e um deles nunca foi declarado:**

| O que persiste | Onde | Prazo hoje | Quem renova |
|---|---|---|---|
| Credencial (`access_token`) | armazenamento cifrado (decisão 5 da 4a-zero) | **60 min** (`expires_in: 3600`) | ninguém — o `refresh_token` é lido e **descartado**; refresh é Non-Goal declarado da 4a-zero |
| Organização escolhida (só o `id`) | armazenamento comum | sem prazo | a consulta a cada arranque |
| Pacote (`<content_hash>.json`) | `filesDir/packages/<org>/` | sem prazo, e não precisa de um | o próprio hash |

O relógio de 60 min é o menos visível e o mais consequente: **mesmo com rede**, um aparelho parado
por mais de uma hora obriga a digitar a senha de novo. Isso não é defeito desta fatia nem dela para
resolver, mas é o que torna a política de validade offline mais simples do que parecia — qualquer
coisa que o aparelho queira fazer **contra o servidor** já passa por autenticação nova, e
autenticação nova é revalidação real de vínculo.

O que **não** existe no aparelho é a categoria de dado que o nome da organização e o título da prova
habitam: mutável, legível por humano, e hoje exclusiva da API. ADR-0002 decidiu que referência
mutável mora fora do pacote imutável; onde ela mora **no aparelho** é o que esta fatia decide.

## Goals / Non-Goals

**Goals**

- Derrubar as **duas** paredes do arranque sem rede num caminho só, sem criar um segundo arranque.
- Dar à referência mutável um lugar com regra de frescor e de invalidação — antes de existir um
  segundo consumidor dela.
- Manter falsificável a afirmação "todo nome apresentado veio da API".

**Non-Goals**

- **Refresh de credencial.** Fica fora, e a fricção de relogar depois de 60 min continua existindo.
  Só entra se essa fricção virar problema real em uso; nesse dia é fatia própria, com escopo de
  autenticação.
- **Room, outbox, push.** São da 4b. Esta fatia guarda referência, não fato.
- **Modo degradado** (capturar sem pacote). ADR-0009 e §10; continua na 4b.
- **Roster.** É o segundo consumidor desta categoria, e o motivo de esta fatia vir antes — não de ela
  crescer para caber os dois.

## Decisions

### 1. Um cache de referência por organização, e não uma lista derivada do disco

Grava-se, a cada consulta bem-sucedida, a **última visão conhecida** daquela organização: nome,
provas publicadas (`short_id`, título, `content_hash`) e o instante em que aquilo foi visto.

**Rejeitado: derivar a lista dos pacotes guardados** (`packages/<org>/*.json`). É quase de graça e
resolve só metade: o `PackageMeta` carrega `exam_id`, `layout_engine_version`,
`min_renderer_version` e `fully_offline_gradable` — **não carrega título**. O professor veria
`prova-referencia-slice-1` em vez de "Prova de referência — fatias 1 e 1.5", e escolher a prova
errada é exatamente o erro que a camada (c) da 4a existe para tornar impossível. O nome da
organização continuaria faltando de qualquer forma.

**Rejeitado: um arranque alternativo "só escanear"**, que pula a organização. Criaria um segundo
lugar decidindo o que o aparelho sabe — a mesma razão pela qual a decisão 10 da 4a-zero recusou um
`ViewModel`.

**Rejeitado: "preparar para uso offline"** como ação explícita do professor antes da aula. É mais
previsível e é provavelmente o destino final, mas é feature de produto com tela própria, e o caso de
hoje não a exige: o pull já acontece quando a prova é escolhida.

### 2. Sem teto de tempo, e o resíduo é aceito por escrito

A visão guardada **não expira por calendário**. Ela vale enquanto o servidor não disser o contrário,
e quando ele disser — respondeu, e a organização não está mais entre as do usuário — a escolha cai
(decisão 10 da 4a-zero, intacta) e **os pacotes daquela organização são apagados**, pelo caminho que
`DeviceSession.sair()` já usa.

Por que não um teto em dias:

- o que fica exposto numa janela longa é o **gabarito em cache**, e não dado pessoal — I5 garante que
  o artefato imutável não carrega dado pessoal direto;
- quem tem o gabarito já teve acesso legítimo a ele enquanto era membro; um teto não desfaz a cópia,
  só a torna inutilizável **naquele aplicativo**;
- nada feito offline chega ao servidor sem autenticação nova, e ela é recusada para quem foi
  desvinculado — com o token de 60 min e sem refresh, isso acontece **todo dia**, não a cada 30;
- número de dias escolhido agora seria número sem evidência (`rigorous.md` P18).

**O resíduo aceito, explicitamente:** um professor desvinculado que **nunca mais conecte aquele
aparelho à rede** mantém o gabarito cacheado indefinidamente, e o aplicativo continuará abrindo para
ele em modo offline. Isto é consequência conhecida da decisão, e não descuido.

**A saída, se ela for necessária:** se alguma escola cliente considerar o resíduo inaceitável, o teto
em dias volta como **decisão comercial**, com o requisito de quem o pediu — e não como retrofit
técnico descoberto tarde. O ponto de entrada é este documento e o requisito "a visão guardada SHALL
NOT ter prazo próprio de validade": mudá-lo é uma linha de spec e um relógio, não uma reescrita.

### 3. Procedência declarada, e marca visual — não só frase

O requisito antigo dizia que, sem consulta, nenhum nome é apresentado. A razão registrada (tarefa 3.4
da 4a-zero, vista falhar) é que **nome inventado é indistinguível de nome verdadeiro para quem lê**.
Um nome cacheado não é inventado: veio da API. O que muda é a **idade**.

Então a regra passa a ser sobre procedência, e ela fica mais forte do que era: o nome aparece **com a
idade ao lado**, e a marca é **visual** — cor, ícone ou selo —, não uma frase no meio do texto. Frase
some na leitura apressada de quem está numa sala com trinta alunos; marca visual, não.

Junto vem uma **ação explícita de atualizar**, e a regra de que atualização falha **não esvazia a
tela**: a visão anterior continua, ainda marcada.

### 4. O cache de referência mora ao lado da escolha, não no armazenamento cifrado

A decisão 5 da 4a-zero separa os dois armazenamentos por critério explícito: o cifrado é para o que é
**credencial de rede reutilizável**; o comum é para o que é **preferência**. Nome de organização,
título de prova e `content_hash` não autenticam nada e não são dado pessoal — vão no comum, como o
identificador da organização já vai.

A porta é o espelho de `SessaoGuardada` e `PacotesGuardados`: verbos nomeados um a um, adaptador
Android fino, decisão em Kotlin puro. É o que permite que "reabrir sem rede usa a visão guardada" e
"sair apaga a visão" sejam cenários de JVM, e não afirmações que só um aparelho alcança.

**Rejeitado: Room.** É a 4b, e ela entra quando houver fato durável para guardar. Um banco para uma
visão por organização é a abstração prematura que a regra 8 do `CLAUDE.md` proíbe.

### 5. O vocabulário de falha já distingue os dois casos; o que falta é quem o consome

`ResultadoDasOrganizacoes` já separa `SemRede` de `Chegaram` — a decisão 8 da 4a-zero fixou um
vocabulário só de falha, e é ele que permite dizer "não respondeu" e "respondeu e o vínculo não está
lá" sem inventar nada. Esta fatia **não** acrescenta vocabulário: acrescenta um consumidor que trata
os dois de formas diferentes, que é precisamente o buraco que a decisão 11 da 4a nomeou.

## Risks / Trade-offs

**A visão pode estar velha e o professor não perceber** → a marca visual e a idade ao lado existem
para isso, e a ação de atualizar é explícita. O caso que resta — visão velha *e* correta — não é
dano.

**Prova publicada depois da última consulta não aparece offline** → é inerente: o aparelho não pode
saber o que nunca viu. A frase da tela precisa dizer isso sem prometer completude.

**Duas fontes para a mesma verdade** → a visão guardada é **derivada**, nunca autoritativa: toda
consulta bem-sucedida a substitui por inteiro, e nenhum caminho de escrita a produz a não ser a
resposta da API. É a mesma disciplina do cache de pacotes, onde o hash é a autoridade.

**O resíduo da decisão 2** → aceito e registrado acima, com a porta de saída nomeada.

**Sombreamento entre máquina pura e fiação** → foi o que produziu 9b.1 e 9b.2, as duas regressões da
4a. Aqui o risco reaparece com a mesma cara: a decisão mora em Kotlin puro, mas *quando* a
`SessaoActivity` grava e lê a visão é fiação, e nenhum teste desta base alcança `@Composable` nem
ciclo de vida de `Activity`. Mitigação: os cenários de aparelho desta fatia são obrigatórios, e não
"se der tempo" — reabrir sem rede depois de matar o processo é o cenário que a 9.2 já provou que a
JVM não pega.

## Migration Plan

Contrato antes do consumidor, e a ordem reduz risco:

1. **A porta e o adaptador da visão guardada**, com os cenários de JVM. Código novo sem consumidor:
   reversível, não muda comportamento.
2. **A gravação**, na consulta bem-sucedida. A visão passa a existir; ninguém a lê ainda.
3. **A leitura no arranque e na listagem**, mais a invalidação por revogação observada. É o ponto de
   não-retorno da fatia, e é deliberado que seja um commit só.
4. **A marca visual e a ação de atualizar** nas telas.
5. **Conferência em aparelho**: puxar com rede, matar o processo, modo avião, reabrir, escanear — a
   9.2 herdada, que é o critério de aceite desta fatia.

**Dívida herdada da 4a, com gatilho nesta fatia:** os 46 cenários instrumentados fecharam na imagem
do CI (`platos-atd34`, API 34) em 2026-09-10, e **não** no telefone real (`2511FPC34G`, Android 16,
SDK 36) — ele estava desconectado. Na **primeira vez que um aparelho for conectado para qualquer
tarefa desta fatia**, rodar `:apps:android:connectedDebugAndroidTest` sem filtro nele antes de
começar. Divergência entre os dois SDKs é achado a investigar com os dois números lado a lado, e não
motivo para reabrir o archive da 4a.

Reversão: reverter o commit 3 devolve o comportamento atual (arranque exige rede) sem deixar dado
órfão — a visão guardada é derivada e pode ser ignorada ou apagada sem perda.

**Registro fora do change**, já feito: a linha de ponto de não-retorno no §16 da
`ARQUITETURA-FINAL-v3.md`, com gatilho por evento — esta mudança precisa estar **arquivada** antes de
`/opsx:propose` rodar para a 4b — e a nota correspondente na entrada da fatia 4 do §15, porque quem
for começar a 4b olha o roadmap antes da tabela de riscos.

## Open Questions

- **Qual marca visual**, exatamente — selo, cor de fundo, ícone com a idade ao lado. É decisão de
  tela, não de contrato: qualquer uma satisfaz o requisito, e a escolha pode esperar a implementação.
- **Como apresentar a idade** ("visto há 2 h" contra data e hora). Mesma natureza.
