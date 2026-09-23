## Context

Ver `proposal.md` — Why, e a Parte I de `docs/cobertura-o-fio-preso-nos-dois-lados.md`, que é a
medição que decidiu abrir esta mudança. Aqui só o estado que decide a abordagem.

**O que a guarda vai ler.** `packages/domain/src/commonMain/kotlin/com/platos/domain/transport/`
tem quatro arquivos e, **por leitura**, cinco tipos `@Serializable`, todos na mesma forma —
`data class` com construtor primário de `val`, `@SerialName` opcional por parâmetro, sem corpo:
`OrganizationDto`, `ExamSummaryDto`, `RosterEntryDto`, `ResultSubmissionDto` e
`AnswerObservationDto`. `AnswerKind` é `object` sem `@Serializable`, e não é contrato.

**Onde estão os literais hoje**, por lado (plano, 7.3):

| Contrato | Aparelho (`apps/android/src/test`) | Servidor (`apps/api/src/test`) |
|---|---|---|
| resultado | `ResultadoDtoTest` — literal | `ResultRouteTest.corpo()` — literal |
| roster | `ObtencaoDeRosterTest:52` — literal | `ExamPackageRouteTest:281` — literal |
| organização | `ApiPlatosTest:37` — literal | `MeOrganizationsTest:112` **desserializa com o próprio tipo** |
| prova | `ApiPlatosPacoteTest:31` — literal | `ExamPackageRouteTest:359` **desserializa com o próprio tipo** |

**O sentido do fio não é o mesmo nos quatro, e a guarda não precisa dele.** Em organização, prova e
roster o servidor codifica e o aparelho decodifica: o literal do aparelho é a resposta do
`MockEngine`, e o do servidor é o corpo esperado. No resultado é o contrário. Nos dois casos o que
prende o fio é o mesmo — um JSON escrito à mão, com os nomes de campo digitados, dos dois lados.

**O molde já existe.** `tools/parity/limiar.mjs` e `tools/parity/answer-kind.mjs` rodam no job `web`
do `ci.yml`, cada um com dois passos: um que confere, outro que força a divergência e falha se a
conferência aceitar. `node --test` também já é usado nesta árvore (`tools/math`), mas os
conferidores do molde não têm teste unitário — são vistos falhar pelo passo do CI.

**Arquivos afetados, por camada.** Contrato: nenhum. Domínio: nenhum. UI: nenhuma. Infraestrutura:
`tools/parity/fio.mjs` (novo) e `.github/workflows/ci.yml` (dois passos). Teste:
`MeOrganizationsTest.kt` e `ExamPackageRouteTest.kt` (um cenário cada). **Impacto em KMP:** nenhum —
`packages/domain` só é **lido** pela guarda, e as mutações de medição são revertidas.

## Goals / Non-Goals

**Goals:**

- Que a mutação da decisão 4 do ADR-0015 derrube os dois lados nos quatro contratos, e não em dois.
- Que um contrato novo em `transport/` sem literal nos dois lados reprove **antes** de alguém
  pensar em escrever o teste — que é o momento em que a fatia 5 vai criá-los.
- Que a guarda diga **qual tipo** e **qual lado**, e não só que falta alguma coisa.

**Non-Goals (nível de desenho, além do que o `proposal.md` já exclui):**

- A guarda **não** confere que o literal é comparado contra a rota, nem o sentido, nem os valores.
  Ela prova que o literal existe. Quem prova que ele prende o fio é a mutação de `@SerialName`
  (decisão 11).
- **Não** se busca uma guarda de contagem de testes executados. É o item 7.2.5 do plano, outra
  verificação, em outra mudança.
- **Não** se busca ler JSON de `src/test/resources`. Hoje nenhum literal de contrato mora ali; se a
  fatia 5 puser um, a guarda o acusa como ausente — em voz alta — e a extensão é consciente.

## Decisions

A ETAPA 7.3 do plano deixa **o mecanismo** para este documento e decide três coisas antes dele:
a guarda lê os arquivos de origem, e não uma lista; ser geral sobre o pacote não contradiz 7.1; e
ela tem piso. As decisões 2, 5 e 6 são essas três, registradas. As demais são as que o plano
delegou.

### 1. A guarda é um conferidor Node em `tools/parity/fio.mjs`, e não um teste JVM

**O que ela atravessa decide onde ela mora.** Ela lê um módulo (`packages/domain`) e julga dois
outros (`apps/api`, `apps/android`). Um teste dentro de qualquer um dos três teria de alcançar os
outros por caminho de arquivo, e cada escolha é pior que a anterior:

- **Em `packages/domain`:** o domínio passaria a conhecer a árvore de teste dos apps — dependência
  invertida. E ler os tipos pelo `descriptor` do kotlinx, que daria as chaves com exatidão, exige
  **saber quais tipos ler**: `commonTest` não tem reflexão sobre pacote, então seria uma lista
  mantida à mão — o segundo registro que o plano proíbe.
- **Em `apps/api`:** o servidor seria dono da evidência do aparelho.
- **Uma task de `buildSrc`:** acopla ao grafo do Gradle uma verificação que não depende de
  compilação nenhuma, e os testes de `buildSrc` não rodam em `./gradlew build` — medido na
  `generatejooq-sem-registro-automatico`.

É o caso que a ETAPA 8 descreve: "o rigor desta base é por módulo, porque todo teste vive dentro de
um. O que atravessa dois … não tem camada posicionada para vê-lo." A camada que esta base já
posicionou para isso é o conferidor Node no job `web`: `limiar.mjs`, `answer-kind.mjs`, e o
`renderizador.mjs` da 7.1 e o `divida.mjs` da ETAPA 8, que vêm depois.

**Custo operacional: nenhum novo.** Node 22 já está no job `web`; o conferidor usa só `node:fs` e
`node:path`, como `answer-kind.mjs`. `tools/parity/package.json` não muda.

**A consequência, dita:** a guarda **não** roda em `./gradlew build`. Quem roda o build local e vê
verde não viu a guarda. O CI a roda em toda PR; o fechamento desta mudança a roda explicitamente.

### 2. Contrato é todo tipo `@Serializable` em `transport/`, lido da fonte — e a forma que ela não conhece reprova

**Raiz:** `packages/domain/src/commonMain/kotlin/com/platos/domain/transport/`, **recursiva**. Um
subpacote de `transport` continua sendo transporte, e ler só o primeiro nível seria a porta de saída
mais barata para o contrato novo.

**Comentários saem antes da leitura.** A KDoc desta pasta cita tipos e campos, e prosa não é
declaração.

**A forma que ela sabe ler é a que os cinco tipos têm:** `@Serializable data class Nome(…)`, cada
parâmetro `val` ou `var`, opcionalmente precedido de `@SerialName("…")`, com ou sem default, e
**sem corpo**. A chave de cada parâmetro no fio é o `@SerialName` quando existe, e o nome da
propriedade quando não.

**Qualquer outra forma sob `@Serializable` reprova, nomeando o tipo e o motivo** — `enum`, `object`,
`sealed`, `value class`, classe que não é `data`, `@Serializable(with = …)`, anotação de parâmetro
que não seja `@SerialName` (`@Transient`, `@JsonNames`…), parâmetro sem `val`/`var`, ou corpo de
classe. A alternativa — pular o que não entende — é uma guarda que passa em silêncio justamente no
contrato de forma nova, que é o da fatia 5. **Falhar fechado custa uma extensão consciente da
guarda; pular custa o buraco que esta mudança fecha.** Corpo de classe entra na lista porque
propriedade com campo declarada no corpo também é serializada, e a guarda não a veria.

### 3. "Literal escrito à mão" é uma string Kotlin que traz todas as chaves do tipo juntas, num arquivo de teste daquele lado

**Os lados**, e só eles: **servidor** = `apps/api/src/test`, **aparelho** = `apps/android/src/test`,
recursivos, arquivos `.kt`. São os dois conjuntos de teste unitário que `./gradlew build` roda, e é
neles que moram os quatro literais do aparelho e os dois do servidor. O `androidTest` **não** conta:
nenhum literal de contrato mora lá (Parte I, §5, por busca), e contá-lo alargaria o que satisfaz a
guarda sem caso que o peça.

**Um tipo T está preso no lado L** quando **ao menos uma** string literal de algum arquivo de L
contém, para **cada** chave k de T, o texto `"k"` seguido de espaço opcional e `:`.

**Por literal, e não por arquivo.** `AuthenticationTest:131` tem `"kind":"personal"` num arquivo do
servidor; por arquivo, chaves espalhadas por strings sem relação no mesmo arquivo satisfariam um
tipo — um verde por coincidência, e calado. Por literal, as chaves têm de estar **juntas**, que é o
que um corpo escrito à mão é. O custo é o inverso: um objeto partido no meio por `+` deixa de contar
— e isso é um vermelho **alto**, não um verde calado.

**O que a leitura de strings precisa saber de Kotlin, e nada além:** comentários de linha e de bloco
(os de bloco se aninham em Kotlin); literais de caractere (`'"'` não abre string); strings cruas
`"""…"""`; strings comuns `"…"` com os escapes resolvidos — `\"k\":` conta como `"k":`, que é a forma
de `AuthenticationTest:131`; e templates `${…}`, que podem conter strings (`${"a".repeat(64)}`, em
`ResultadoDtoTest`) sem encerrar a string de fora. O conteúdo do template não é texto do literal —
**valores** podem ser interpolados (`"id":"$id"`); **chaves**, não. Aceita `\r\n`: a primeira
execução é em Windows, e o CI em Linux.

**Nada disto é suposto correto.** Os canários da tarefa 1.4 põem a leitura à prova antes de ela ser
acreditada — o §6 da Parte I registra uma contagem por texto enganada duas vezes antes de valer.

### 4. O que ela diz, e os dois códigos de saída

- **`exit 0`**: imprime, para cada tipo, **quais arquivos** o prendem de cada lado. A lista não é
  enfeite: é o que permite ler se um tipo foi satisfeito pelo literal que devia ou por um de outro
  assunto (riscos, abaixo).
- **`exit 1`**: um `::error::` por par (tipo, lado) sem literal, com o arquivo de origem do tipo, as
  chaves procuradas e a raiz do lado. É o vermelho **sobre o que ela confere**.
- **`exit 2`**: a guarda **não conseguiu conferir** — piso (decisão 6) ou forma que ela não lê
  (decisão 2). É a mesma distinção que `answer-kind.mjs` faz com "a forma mudou". Se houver motivo
  de `2`, a saída é `2`, porque nesse caso ela não pode garantir nada sobre o resto.

A distinção serve ao passo do CI que prova que ela falha: ele exige **o motivo certo** para cada
defeito plantado, e não só um código diferente de zero (`rigorous.md` §3).

### 5. Ser geral sobre o pacote não contradiz a proibição de 7.1

Decidido na 7.3 do plano, e registrado aqui. Lá, `renderizador.mjs` confere três registros
**nomeados**, e generalizá-lo seria abstração sem necessidade (regra 8 do `CLAUDE.md`). Aqui o
conjunto cresce por construção, e o caso que importa é justamente o que ainda não tem nome. A
necessidade está comprovada: dois dos quatro contratos já caíram no buraco. **A guarda é geral
sobre um pacote nomeado e dois lados nomeados**, e nada além disso — nenhum outro pacote, nenhuma
varredura do repositório.

### 6. Piso

Decidido na 7.3 do plano (P13). O plano cita o precedente como "o piso reprova um catálogo vazio",
da guarda de RLS; na árvore, o teste com esse nome está em `RetentionDeclarationTest.kt:148`, e o
piso da guarda de RLS propriamente dita está em `ConnectionRoleTest.kt:110`. Reprova com `2`:

- a raiz de `transport` ausente, ou sem nenhum `.kt`;
- nenhum tipo `@Serializable` encontrado;
- um tipo com zero chaves — satisfeito por qualquer literal, que é a vacuidade por tipo;
- a raiz de um lado ausente, ou com zero literais lidos — um leitor de strings quebrado faria todo
  tipo faltar, o que já é alto; o piso faz o motivo ser o certo.

O único parâmetro é **`--transport <dir>`**, que troca a raiz dos contratos. É ele que permite o
piso ("apontada para um diretório vazio") e o contrato plantado do CI sem tocar o checkout. Os
defeitos plantados **nos lados** são mutações na árvore, revertidas (P10), e não parâmetros.

### 7. Os dois passos no `ci.yml`, no molde

No job `web`, logo depois dos dois de `answer_kind`, com o comentário no mesmo tom dos vizinhos:

1. **"O fio de cada contrato esta preso nos dois lados"** — `node tools/parity/fio.mjs`.
2. **"A verificacao do fio continua capaz de falhar"** — dois defeitos num passo, como o da tinta:
   (a) uma cópia temporária de `transport/` com um `FantasmaDto` a mais e sem literal em lado
   nenhum, e o passo exige `exit 1` **e** o `FantasmaDto` nomeado nos **dois** lados; (b) um
   diretório vazio, e o passo exige `exit 2` com o motivo do piso. O passo **não** planta nada no
   checkout.

### 8. A ordem: a guarda antes dos literais, e o primeiro vermelho é da árvore real

**Commit 1** é a guarda com os dois passos do CI, e nasce vermelho — esperado, e dito na mensagem,
como o commit 1 da ETAPA 3. A razão, do plano: o primeiro vermelho da guarda é sobre a **árvore
real**, sem defeito plantado, e fica na história para quem quiser conferir.

**A previsão**, fixada no plano (`460962c`) antes desta mudança existir: a guarda nomeia
**exatamente** `OrganizationDto` e `ExamSummaryDto`, **do lado do servidor**, e mais nada — nenhum
tipo do lado do aparelho, e `RosterEntryDto`, `ResultSubmissionDto` e `AnswerObservationDto` não
nomeados. **Este desenho acrescenta uma parte, e ela fica dita como acréscimo:** a saída é `1`, e
não `2` — os cinco tipos têm a forma da decisão 2. Se nomear outro tipo, o lado do aparelho, ou
sair `2`, a guarda ou a leitura está errada: pare (decisão 10).

**Commit 2** são os dois literais do servidor, e a guarda fica verde.

### 9. Os literais do servidor, no molde do roster

Cada cenário novo compara **o corpo inteiro** da rota contra JSON escrito à mão, por igualdade —
como `ExamPackageRouteTest.kt:261-285`, cuja KDoc já dá o argumento: desserializar com
`ignoreUnknownKeys` aceitaria campo a mais em silêncio, e a igualdade literal prende nome de campo,
ordem das chaves e ausência de campo a mais de uma vez.

- **`MeOrganizationsTest`**: a organização pessoal do primeiro acesso,
  `[{"id":"<id>","name":"Nova Professora","kind":"personal","role":"owner"}]`. O `id` vem de
  `idDaOrganizacaoPessoal` — SQL cru pelo `adminDataSource`, que não passa pela rota. São os mesmos
  valores do literal do aparelho (`ApiPlatosTest:37`), cuja KDoc diz que os tirou deste teste: o
  par passa a ser legível lado a lado.
- **`ExamPackageRouteTest`**: a listagem,
  `[{"short_id":"mat-7a-2026-1","title":"Prova de Matematica","content_hash":"<hash>"}]`, com o hash
  de `PostgresSupport.sha256Hex` — `MessageDigest` da JVM, o oráculo que a KDoc da classe já nomeia.
  Os mesmos valores de `ApiPlatosPacoteTest:31`.

**As chaves são digitadas; os valores podem ser interpolados.** O que o literal prende é o nome do
campo, e é isso que a mutação vai trocar.

**Os cenários que desserializam ficam como estão**, com `organizacoesDe` e `provasDe`. Eles afirmam
conteúdo — quais organizações, qual papel, qual prova —, e isso continua certo.

### 10. Regra de parada — o instrumento não se conserta para caber na previsão

Regra 0.5 do plano, e ela vale sobre toda esta mudança:

> Toda etapa prevê **qual conjunto de cenários deve cair** sob a mutação. Se o conjunto real for
> diferente do previsto — **mais, menos, ou outros** —, **pare**. Não conserte o instrumento, não
> afrouxe a asserção, não ajuste a previsão em silêncio. Escreva o conjunto real ao lado do previsto
> e diga o que ele significa (P7, P12, P14).

Ela governa **as previsões sobre a árvore**: o primeiro vermelho (decisão 8), as três mutações
repetidas e os defeitos plantados. **Os canários da leitura (tarefa 1.4) são outra coisa**: eles
põem o instrumento à prova contra entradas conhecidas, e não afirmam nada sobre a árvore. Canário
que falha quer dizer que a leitura está errada — então ela se corrige, **e tudo se roda de novo a
partir do primeiro vermelho**, com as duas execuções escritas (P7). O que não se faz é corrigir a
leitura **depois** de ver o primeiro vermelho discordar da previsão, sem canário que o justifique:
isso é ajustar o instrumento ao resultado.

### 11. O limite que não pode faltar no registro

A frase, do plano, e ela vai inteira para a seção do que não fica verificado:

> **A guarda prova que o literal existe, e não que ele prende o fio.** Quem prova isso é a mutação
> de `@SerialName`, e ela só roda quando alguém a roda — a mudança da fatia 5 que criar um contrato
> continua devendo a mutação dele (P16: a guarda é a camada vizinha).

## Decisões já tomadas contra — o que é proibido em 7.3

Não são alternativas em aberto. São recusas, com a razão que o plano dá para cada uma.

- **Reescrever os cenários que desserializam para comparar contra `Json.encodeToString` do tipo, ou
  montar o literal esperado a partir dele.** É o mesmo código dos dois lados de novo: P4, e a
  decisão 4 do ADR-0015. Recusado.
- **Apagar os cenários que desserializam "porque agora há literal".** Eles afirmam outra coisa.
  Recusado.
- **Tirar o `"kind":"personal"` de `AuthenticationTest:131` "porque o literal agora cobre".** Ele
  afirma isolamento entre usuários, e não contrato (P19). Recusado.
- **Mexer nos DTOs** — renomear, acrescentar campo, mudar default ou `@SerialName`. As mutações de
  medição são revertidas, e nada além delas toca `transport/` (P25). Recusado.
- **Manter à mão a lista de contratos que a guarda confere.** Seria um segundo registro, pelo mesmo
  argumento com que a ETAPA 8 proíbe duplicar o §16 num YAML. Recusado — e é a razão central da
  decisão 1.
- **Estender a guarda para fora de `transport`.** O pacote publicado é artefato hasheado, com as
  camadas do ADR-0013; o espelho do `LayoutMap` em TypeScript foi isentado por escrito (ETAPA 6,
  "Proibido"). Recusado.
- **`packages/contracts`, OpenAPI ou geração de código.** Recusados pela decisão 1 do ADR-0015, e
  pela mesma razão: não há consumidor. Recusado.
- **Consertar aqui os dois testes de `ApiPlatosPacoteTest` que nunca rodaram, ou construir aqui a
  guarda de contagem.** São o item 7.2.5, por decisão do mantenedor, em outra mudança. Recusado.

## Risks / Trade-offs

**A leitura de strings ser enganada** — comentário contado como literal, template que encerra a
string, escape não resolvido → Os canários da tarefa 1.4, antes de o instrumento ser acreditado,
com o resultado previsto de cada um. E, a cada CI, o contrato plantado da decisão 7.

**Um tipo ser satisfeito por coincidência** — `OrganizationDto` tem as chaves mais genéricas da
pasta (`id`, `name`, `kind`, `role`), e um JSON de outro assunto que as traga juntas o satisfaz; o
mesmo vale para um tipo cujas chaves sejam subconjunto das de outro → **Não é mitigado, é
conhecido** (P8). A saída do `exit 0` lista quem satisfez cada par, e a leitura dessa lista é
tarefa (2.3); fora dela, a guarda aceita. É a mesma lacuna da decisão 11, vista de outro ângulo.

**A guarda fora do `./gradlew build`** → Quem só roda o build local não a vê. O CI a roda em toda
PR, com o passo que prova que ela falha; o fechamento desta mudança a roda explicitamente e diz
isso.

**Falhar fechado atrapalhar a fatia 5** → É o efeito pretendido. Um contrato de forma nova para a
guarda até alguém ensiná-la a ler aquela forma — e ensinar é uma edição visível de `fio.mjs`, com o
canário da forma nova, e não um verde que passou por cima.

**O commit 1 fica vermelho na história** → Pretendido (decisão 8). Os PRs são empilhados e o CI
roda na cabeça da PR, que é o commit 2 em diante.

## Migration Plan

Nenhuma migração de dado, de schema, de artefato ou de hash. Nenhum comportamento muda.

**Reversibilidade:** o commit 2 reverte sozinho (a guarda volta a nomear os dois tipos); o commit 1
reverte sozinho (os passos do CI e o conferidor saem juntos). As mutações de medição nunca entram
em commit.

**Ambiente:** Docker, para os testes de rota do servidor. Não se liga sem perguntar (P22, regra
0.9 do plano) — na Parte I ele foi ligado pelo mantenedor. **Nenhum emulador:** nenhum arquivo de
`apps/android` muda, e o que não roda fica escrito como não rodado (P8).
