# Cobertura — ETAPA 7.3, `o-fio-preso-nos-dois-lados`

> Este documento tem **duas partes**, no molde de `cobertura-transferencia-entre-aparelhos.md`. A
> **Parte I** é a medição que decidiu abrir a mudança, feita em 2026-09-23 **antes** do
> `/opsx:propose`. A **Parte II** é a da mudança, e ainda não existe. A Parte I não se apaga (P7):
> ela é o estado que a Parte II vai corrigir.
>
> **Superado no mesmo dia, ao fechar a mudança:** a Parte II existe, `13:03Z`–`13:28Z`, na branch
> `vewvniv/o-fio-preso-nos-dois-lados`. A frase "ainda não existe" fica, como o estado de quando foi
> escrita.

**Plano:** `docs/plano-de-correcao-antes-da-fatia-5.md`, ETAPA 7.3 · **Achado:** novo, de 2026-09-23 —
`docs/cobertura-contrato-do-fio-com-dono-unico.md` §8, na correção daquela data
**Data:** 2026-09-23, janela `12:01Z`–`12:12Z` · **Ambiente:** Docker ligado pelo mantenedor (P22);
nenhum emulador, nenhum aparelho
**Base:** `460962c`, na branch `vewvniv/contrato-do-fio-com-dono-unico`

---

# Parte I — a medição de entrada

## 0. A pergunta, e por que a previsão vale

O achado foi registrado em `8dda6bb` como **conferido por leitura, e não medido**: em organização e
em prova, o teste do servidor desserializa o corpo com o mesmo tipo que a rota usa para escrevê-lo,
e só o aparelho tem literal. A pergunta que a medição responde é a que a decisão 4 do ADR-0015
faz: trocar um `@SerialName` no domínio derruba os **dois** lados, ou um só?

**A previsão foi fixada antes de qualquer resultado.** A tabela está na 7.3 do plano, no commit
`460962c`, e a primeira execução desta medição começou depois dele. Ninguém pôde ajustá-la depois
do fato (P11).

## 1. A linha de base desta sessão

Sem ela, uma queda sob mutação não teria a quem ser atribuída — o último verde era de 2026-09-19.

`./gradlew build --continue --rerun-tasks`, `12:01:18Z`–`12:04:49Z`, `exit 0`:
**153 suítes, 1444 testes, 0 falhas, 0 erros, 0 pulados.**

| Módulo | Testes |
|---|---|
| `packages/domain` (`jvmTest` 329 · `testAndroidHostTest` 321 · `jsNodeTest` 321) | 971 |
| `apps/api` | 165 |
| `apps/android` (`testDebugUnitTest`) | 308 |

É, número a número, o build final da ETAPA 6 (`cobertura-contrato-do-fio-com-dono-unico.md` §7).

## 2. As três mutações: conjunto previsto e conjunto real

Cada uma com `./gradlew build --continue`, sozinha na árvore, revertida por cópia do original antes
da seguinte. Nas três, **as 153 suítes rodaram dentro da janela** — as tasks de teste dependem todas
de `packages/domain`, e a mutação muda a entrada delas.

| Mutação | Janela | Previsto no aparelho | **Real** | Previsto no servidor | **Real** | Outras |
|---|---|---|---|---|---|---|
| **A** · `OrganizationDto.name` ganha `@SerialName("nome")` | `12:05:15Z`–`12:06:21Z` | `ApiPlatosTest` cai | **caiu — 6 de 14** | nada cai | **0 de 165** | **0** |
| **B** · `ExamSummaryDto.title` ganha `@SerialName("titulo")` | `12:06:44Z`–`12:07:53Z` | `ApiPlatosPacoteTest` cai | **caiu — 1 de 7** | nada cai | **0 de 165** | **0** |
| **C** · `RosterEntryDto`: `display_name` → `displayName` | `12:08:07Z`–`12:09:07Z` | `ObtencaoDeRosterTest` cai | **caiu — 6 de 10** | `ExamPackageRouteTest`, cenário do roster, cai | **caiu — 1 de 18**, exatamente esse | **0** |

**REAL = PREVISTO nas três. A regra de parada (§0.5 do plano) não disparou.**

**O mecanismo está na mensagem, e não só na contagem** (P12). Nas quedas do aparelho:
`JsonConvertException: Illegal input: Field 'nome' is required for type with serial name
'com.platos.domain.transport.OrganizationDto'` — e o mesmo com `'titulo'` e `'displayName'`. No
servidor, sob C: `AssertionFailedError: expected: <[{"student_token":"tok-hlm","display_name":…`,
que é a igualdade literal de `ExamPackageRouteTest:281`.

### O verde do servidor foi conferido como execução, e não suposto

"Nada caiu" só vale se a classe rodou. Lido do XML de cada uma, dentro da janela da mutação:

| Sob | Classe | Testes | Falhas | `timestamp` |
|---|---|---|---|---|
| A | `MeOrganizationsTest` | 5 | 0 | `12:06:02.871Z` |
| A | `AuthenticationTest` | 8 | 0 | `12:05:44.753Z` |
| B | `ExamPackageRouteTest` | 18 | 0 | `12:07:40.559Z` |

**O cenário que torna o buraco visível é o de A.** `MeOrganizationsTest` afirma
`assertEquals("Nova Professora", organizacoes.single().name)` — e continuou verde com o fio dizendo
`"nome"`, porque a rota escreveu `"nome"` e o teste leu `"nome"`, com o mesmo tipo. É P4, visto:
a asserção sobre o nome do campo não tem como falhar, porque não há nome de campo escrito nela.

`AuthenticationTest` também ficou verde sob A, e isso é o esperado: ele prende `kind`, e a mutação
foi sobre `name` de propósito. É o que o plano previu ao escolher o campo — `kind` está preso por um
teste de isolamento, e mutá-lo teria dado "o servidor segura" com a camada vizinha segurando.

### Os sobreviventes, um por um

Um sobrevivente sem explicação numa classe que caiu é sinal de que a mutação mede outra coisa
(`rigorous.md` §3). Todos foram lidos:

- **`ApiPlatosTest`, 8 de 14.** Sem sessão, 401 (dois), falha de transporte (dois), lista vazia,
  "contrato quebrado estoura" — que já espera a falha de decodificação e continua a recebê-la — e
  "resposta vira Chegaram com as organizacoes", que traduz um `Retorno.Respondeu` montado à mão e
  não passa por JSON nenhum (`ApiPlatosTest.kt:194-200`).
- **`ApiPlatosPacoteTest`, 6 de 7.** Cinco são da rota do **pacote**, que não é `ExamSummaryDto`. O
  sexto, "a listagem vai para a rota da organizacao com a credencial", responde `[]` e só confere o
  pedido (`ApiPlatosPacoteTest.kt:95-103`): lista vazia não tem campo a decodificar.
- **`ObtencaoDeRosterTest`, 4 de 10.** Roster vazio, recusa do servidor, sem rede e sem roster, e
  um servidor que nunca responde. Nenhum decodifica um item.
- **`RosterQueryTest`, inteiro, sob C.** Ele serializa com `Json.encodeToString` e procura valores
  proibidos no resultado (`RosterQueryTest.kt:89`): afirma **ausência de dado pessoal**, e não nome
  de campo. Continuar verde é o certo.

## 3. A reversão, rodada

`MUTACAO` em código (`.kt`, `.kts`, `.mjs`, `.yml`, `.sql`, `.ts`) fora de `build/` → **0**.
`git status` → vazio.

`./gradlew build --continue --rerun-tasks`, `12:09:39Z`–`12:11:48Z`, `exit 0`, **176 de 176 tasks
executadas**: **153 suítes, 1444 testes, 0 falhas** — a linha de base, número a número (P10).

## 4. O instrumento de contagem

As contagens saem dos `TEST-*.xml`, filtradas pelo atributo **`timestamp` de dentro de cada
relatório**, que é UTC e descreve a execução que o escreveu — e não pela data do arquivo, que foi o
erro registrado na cobertura da ETAPA 6, §7. O script foi temporário, fora da árvore. Em toda
execução ficou **um** relatório fora da janela: `buildSrc/.../TEST-com.platos.build.AquisicaoDeConexaoTest.xml`,
de 2026-09-1x. É esperado: `./gradlew build` não alcança os testes de `buildSrc`, o que a mudança
`generatejooq-sem-registro-automatico` mediu.

## 5. O que esta medição **não** verificou (P8)

- **A suíte instrumentada não rodou.** Nenhum literal com `"name"`, `"title"` ou `"display_name"`
  existe em `apps/android/src/androidTest` — conferido por busca, e não por execução.
- **O CI não rodou.** Tudo acima é local, em Windows; o job `build` roda em Linux.
- **O contrato de resultado não foi remedido aqui.** Ele foi medido na ETAPA 6, sobre `capture_id`,
  e nada nesta sessão tocou nele.
- **Só um campo por contrato.** A afirmação é sobre o contrato ter ou não literal do lado do
  servidor, e um campo basta para decidi-la — mas os outros campos de organização e de prova não
  foram mutados. Em organização, `kind` é o único que deve segurar no servidor, e **não** foi medido.

## 6. Achado novo, encontrado pela medição: dois testes que nunca rodaram

**Fora do escopo desta mudança.** Pela regra 0.4 do plano, vira item escrito com dono e
fatia-limite, e **não** conserto aqui (P19). **É o item 7.2.5 do plano**, na mudança
`o-apk-de-release-e-verificado`, por decisão do mantenedor em 2026-09-23.

**Como apareceu.** Para explicar os sobreviventes de B (§2), os cenários de `ApiPlatosPacoteTest`
foram contados no fonte e no relatório: **9 `@Test` no fonte, `tests="7"` no XML**, com 0 pulados.
Os dois ausentes são `listagem sem rede vira SemRede` e `pacote sem rede vira SemRede`.

**O mecanismo, conferido no bytecode, e não suposto.** As duas funções são
`fun … () = runBlocking { … assertInstanceOf(Retorno.SemRede::class.java, retorno) }`. No JUnit
Jupiter, `assertInstanceOf` **devolve** o objeto; o corpo por expressão faz a função devolvê-lo
também. `javap -p` sobre a classe compilada:

```
public final void a listagem chega traduzida com identificador titulo e hash();
public final com.platos.android.net.Retorno$SemRede listagem sem rede vira SemRede();
public final com.platos.android.net.Retorno$SemRede pacote sem rede vira SemRede();
```

Os outros sete são `void`. O Jupiter (`junit = "5.12.2"`, `gradle/libs.versions.toml:16`) não
descobre método de teste que devolve valor, e **nada acusa**: nenhuma falha, nenhum pulado, nenhuma
linha no log do build.

**Desde quando.** Os dois nasceram assim, em `593bf9a` (2026-09-08), já com `assertInstanceOf`. Nunca
rodaram. O caminho do **pacote** sem rede é exercitado em outra camada, por `ObtencaoDePacoteTest`
(`:87`, `:124`, `:132`), por leitura; o da **listagem** sem rede, não foi conferido.

**A extensão, medida nas três suítes JVM** (`apps/android` unitário, `apps/api`, `packages/domain`
`jvmTest`), com os relatórios da reversão (§3):

- por classe com relatório: **89 classes, 804 `@Test` no fonte, 802 testes executados** — a única
  divergência é `ApiPlatosPacoteTest`, 9 × 7;
- na direção inversa — arquivo de fonte com `@Test` e **nenhum** relatório, que é como apareceria
  uma classe cujos testes fossem todos invisíveis: **89 arquivos, nenhum sem relatório**.

**O instrumento foi visto falhar antes de ser acreditado** (P13). A primeira versão da direção
inversa acusou os 89 arquivos — o padrão casava `hostname=` em vez de `name=`. Corrigido, um canário
foi posto em `apps/api/src/test/.../canario/CanarioSemRelatorioTest.kt`, com um `@Test` que nunca
rodou: **o primeiro canário também passou sem ser acusado**, porque estava escrito como
`@org.junit.jupiter.api.Test` e a busca procura `@Test`; reescrito na forma que os testes reais
usam, foi acusado pelo nome (90 arquivos, ele entre eles). Removido, `git status` voltou ao que era.

**O que a varredura não cobre:** `androidTest`, os alvos `jsNodeTest` e `testAndroidHostTest` do
domínio (o mesmo fonte que o `jvmTest`, com outro executor), e os testes de `buildSrc`. E ela conta
`@Test` por texto: um `@Test` escrito com o nome qualificado passaria despercebido, que é
exatamente o que o primeiro canário mostrou.

---

# Parte II — a mudança

*Ainda não existe.* O plano (7.3) diz o que ela escreve aqui: o primeiro vermelho da guarda sobre a
árvore real, os dois vermelhos plantados e o do piso, e as três mutações acima repetidas, agora
derrubando os dois lados.

> **Superado:** a Parte II está abaixo. O parágrafo acima fica como o que a Parte I prometeu.

**Mudança:** `openspec/changes/o-fio-preso-nos-dois-lados/` — o `tasks.md` tem o registro tarefa a
tarefa, e esta parte é o resumo que o `rigorous.md` §8 pede.
**Data:** 2026-09-23, `13:03Z`–`13:28Z` · **Ambiente:** Docker confirmado e ligado pelo mantenedor
(P22); nenhum emulador, nenhum aparelho
**Base:** `d7e10f4` · **Branch:** `vewvniv/o-fio-preso-nos-dois-lados`

## 7. O que a mudança fez

| Commit | O que |
|---|---|
| `68c20a0` | proposal, design e tasks; a linha de base (§8) |
| `455d24b` | `tools/parity/fio.mjs` e dois passos no job `web` do `ci.yml` — **nasce vermelho** (§9) |
| `5e41146` | um cenário de literal em `MeOrganizationsTest` e um em `ExamPackageRouteTest` |
| `8f7a1d8` | o registro das mutações, dos defeitos plantados, do piso e da reversão |

**A guarda.** Lê todo tipo `@Serializable` de `com.platos.domain.transport` **da fonte**, e reprova
o par (tipo, lado) que não tenha, num arquivo de teste daquele lado, **uma string Kotlin com todas
as chaves do tipo juntas**. Os lados são `apps/api/src/test` e `apps/android/src/test`. Forma que
ela não sabe ler reprova com `2` (falha fechado), raiz vazia também (piso); o `exit 0` lista quem
prende cada par. O mecanismo e as razões estão no `design.md`, decisões 1 a 7.

**Os literais.** Os dois cenários comparam o corpo inteiro da rota contra JSON escrito à mão, no
molde do roster. Os cenários que desserializam ficaram: **+29 / −0** em cada arquivo.

## 8. A linha de base desta sessão

`./gradlew build --continue --rerun-tasks`, `13:04:18Z`–`13:06:31Z`, `exit 0`, 176 de 176 tasks:
**153 suítes, 1444 testes, 0 falhas** pelo `timestamp` de dentro dos XML — a Parte I §1, número a
número e módulo a módulo.

## 9. O primeiro vermelho da guarda, sobre a árvore real

Às `13:11:05Z`, sem nada plantado (`git status`: só `ci.yml`, `tasks.md` e `fio.mjs`):

```
::error::ExamSummaryDto (…/transport/ExamDto.kt) nao tem literal escrito a mao do lado do servidor: nenhuma string de apps/api/src/test traz juntas as chaves "short_id", "title", "content_hash"
::error::OrganizationDto (…/transport/OrganizationDto.kt) nao tem literal escrito a mao do lado do servidor: nenhuma string de apps/api/src/test traz juntas as chaves "id", "name", "kind", "role"
```

`exit 1`. **Real = previsto** — a previsão estava no plano desde `460962c`: exatamente esses dois,
do lado do servidor, e mais nada. `RosterEntryDto`, `ResultSubmissionDto` e `AnswerObservationDto`
presos nos dois lados; nenhum tipo nomeado do lado do aparelho. A guarda leu 5 tipos em 4 arquivos,
1164 literais em 28 arquivos do servidor e 822 em 30 do aparelho.

**O que a lista disse e o plano não:** o contrato de resultado está preso no servidor pelo primeiro
literal de `ResultRouteTest.kt` (`:105`, o cenário "nota declarada fechada com pendência"), e não
por `corpo()`, que o plano cita. Que `corpo()` também satisfaz é **leitura**: a guarda imprime um
literal por arquivo. E `AnswerObservationDto` é preso pelos mesmos literais do envio, porque as
observações moram dentro dele.

## 10. O instrumento, visto falhar antes de ser acreditado

Na Parte I §6, uma contagem por texto foi enganada **duas vezes** antes de valer. Esta guarda lê
Kotlin, que é mais do que contar texto, e foi posta à prova antes do primeiro commit.

**A leitura — cinco canários e um controle**, plantados em `MeOrganizationsTest` (onde
`OrganizationDto` ainda faltava) ou numa cópia temporária de `transport/`:

| Canário | Previsto | Real |
|---|---|---|
| C1 · as quatro chaves num comentário de linha **e** depois de um comentário de bloco **aninhado**, dentro de um literal cru | continua nomeado | **continua** |
| C1-controle · o mesmo literal cru, fora do comentário | deixa de ser nomeado | **deixou** |
| C2 · as chaves partidas em dois literais unidos por `+` | continua nomeado | **continua** |
| C3 · string comum com aspas escapadas (`\"id\":…`) | deixa de ser nomeado | **deixou** |
| C4 · literal cru com template que contém string (`${"x".repeat(2)}`), e chaves depois dele | deixa de ser nomeado | **deixou** |
| C5 · `@Serializable enum class`; e, à parte, parâmetro com `@Transient` | `exit 2` com o motivo | **`exit 2`, nos dois** |

Cada canário foi montado para que a leitura **quebrada** desse o resultado oposto: se o comentário
aninhado fosse mal lido, o literal cru de C1 viraria código e satisfaria o tipo; se o `"x"` de C4
encerrasse a string de fora, as chaves se separariam. O controle é o que faz C1 medir o
comentário, e não a leitura de literal cru.

**O passo do CI que prova que ela falha — visto reprovar, ramo por ramo.** Um passo "capaz de
falhar" que não reprova nada é o mesmo defeito um nível acima. Com defeitos plantados **na guarda**:

| Defeito na guarda | O passo |
|---|---|
| sai `0` sempre | reprovou, pelo lado do servidor |
| sai `1` sem nomear nada | reprovou — o código certo com o motivo errado não passa |
| sai `2` sempre | reprovou |
| o lado do aparelho sai de `LADOS` | reprovou, **pelo lado do aparelho** |
| o piso da raiz vazia sai `0` | reprovou, **pela raiz vazia aceita** |

Os dois últimos isolam os outros dois ramos do passo, cada um com o resto da guarda funcionando.

## 11. As três mutações, antes e depois

Cada uma numa linha, com `./gradlew build --continue`, as 153 suítes na janela, revertida por
cópia antes da seguinte:

| Mutação | Aparelho | Servidor — **antes** (Parte I) | Servidor — **depois** | Previsto depois |
|---|---|---|---|---|
| **A** · `name` → `nome` | `ApiPlatosTest` 6 de 14 | **0 de 165** | **`MeOrganizationsTest` 1 de 6** — o cenário novo | o cenário novo cai |
| **B** · `title` → `titulo` | `ApiPlatosPacoteTest` 1 de 7 | **0 de 165** | **`ExamPackageRouteTest` 1 de 19** — a listagem nova | o cenário novo cai |
| **C** · `display_name` → `displayName` | `ObtencaoDeRosterTest` 6 de 10 | `ExamPackageRouteTest` 1 de 18 | `ExamPackageRouteTest` 1 de 19 — o roster | o roster cai |

**Real = previsto nas três, e em todas as outras suítes, 0.** As contagens do aparelho são as da
Parte I, uma a uma, porque nenhum teste de lá mudou.

**O mecanismo, na mensagem** (P12). No servidor: `AssertionFailedError: expected: <[{…"name":"Nova
Professora"…}]> but was: <[{…"nome":"Nova Profes…` — e o mesmo com `"title"`/`"titulo"` e
`"display_name"`/`"displayName"`. No aparelho, como na Parte I: `JsonConvertException: Illegal
input: Field 'nome' is required for type with serial name '…OrganizationDto'`.

**Os que desserializam não caíram em nenhuma das três.** Sob A, os cinco outros cenários de
`MeOrganizationsTest` ficaram verdes com o mesmo corpo mutado que derrubou o sexto. É o P4 da
Parte I §2 visto lado a lado com o conserto: a mesma rota, o mesmo corpo, um verde e um vermelho, e
a única diferença é o nome do campo estar **digitado**.

## 12. A guarda com defeito plantado, e o piso

| Defeito | Real |
|---|---|
| `MentiraDto` em `transport/`, sem literal em lado nenhum | nomeado **nos dois lados**, e só ele; `exit 1` |
| o literal do roster sem `"display_name"` no servidor (as duas metades) | `RosterEntryDto` nomeado **só do lado do servidor**; `exit 1` |
| `--transport` vazio · inexistente · sem `@Serializable` | `exit 2`, `piso:` |
| lado inexistente · lado com zero literais (mutação em `LADOS`) | `exit 2`, `piso:` |
| `@Serializable data class Vazio()` numa cópia | `exit 2`, `piso: Vazio … nao tem chave nenhuma` |

O segundo é o que prova que a guarda distingue os lados, e não só a presença do tipo.

## 13. A reversão e o comando cheio

- `MUTACAO` em código fora de `build/` → **0**; `git status` sem resíduo de código; a guarda →
  `exit 0` às `13:25:12Z`.
- `./gradlew build --rerun-tasks`, `13:25:12Z`–`13:27:21Z`, `exit 0`, **176 de 176** tasks: **153
  suítes, 1446 testes, 0 falhas**. A diferença para §8 é **+2**, em `apps/api`, e são os dois
  cenários novos.
- `./gradlew -p buildSrc test --rerun-tasks`, `13:27:51Z`–`13:28:20Z`: 1 suíte, 1 teste, 0 falhas.
- Os dois passos novos do `ci.yml`, extraídos pelo PyYAML e rodados com `bash -e` às `13:28:20Z`:
  os dois `exit 0`. O primeiro era `1` no commit `455d24b`.

## 14. O que **não** fica verificado (P8)

- **A guarda prova que o literal existe, e não que ele prende o fio.** Quem prova isso é a mutação
  de `@SerialName`, e ela só roda quando alguém a roda — a mudança da fatia 5 que criar um contrato
  continua devendo a mutação dele (P16: a guarda é a camada vizinha).
- **A guarda aceita qualquer string que traga as chaves juntas**, inclusive uma de outro assunto.
  `OrganizationDto` tem as chaves mais genéricas da pasta (`id`, `name`, `kind`, `role`), e um tipo
  cujas chaves sejam subconjunto das de outro é satisfeito pelo literal do outro. **Não é mitigado,
  é conhecido.** A lista do `exit 0` diz quem satisfez cada par, e ela só protege quem a lê. Hoje
  ela diz o esperado, sem arquivo a mais (tarefa 2.3).
- **A guarda não roda em `./gradlew build`.** Roda no CI, no job `web`. Quem só roda o build local
  não a vê.
- **O CI não foi observado.** Tudo acima é local, em Windows; o CI roda em Linux, e os dois passos
  novos usam `mktemp -d`, `cp -r` e `<<<`, que aqui rodaram no Git Bash. A tarefa 4.2 fica aberta
  até o CI da PR ser lido no destino, e o push é decisão do mantenedor.
  **Superado no mesmo dia:** PR #59, execução `35877298960` sobre `8771220`, `build`, `web` e
  `paridade` em `success`. No log do `web`, a guarda deu no Linux a mesma saída da execução local, e
  o segundo passo disse "acusou o contrato sem literal nos dois lados, e o piso, como deve". No
  relatório HTML do `build` (`14:58:54`), `apps/api` tem 167 testes, 0 falhas, com os dois cenários
  novos pelo nome. O registro está na tarefa 4.2 do `tasks.md`.
- **`connectedDebugAndroidTest` não rodou.** Nenhum arquivo de `apps/android` mudou, e as mutações
  em `packages/domain` foram revertidas e conferidas. Que o `androidTest` não tem literal de
  contrato é busca (Parte I §5), e não execução.
- **Um campo por contrato**, como na Parte I §5. `kind`, `role`, `id`, `short_id`, `content_hash` e
  `student_token` não foram mutados aqui, e o contrato de resultado não foi remedido — foi medido na
  ETAPA 6, sobre `capture_id`.
- **O que a leitura de Kotlin não cobre, e como cada limite falha:** JSON em `src/test/resources`
  não é lido; objeto partido por `+` no meio das chaves não conta; o `androidTest` não é lado. Os
  três falham **em voz alta**, com o tipo nomeado como solto, e não em silêncio. Forma de contrato
  que não seja `data class` simples falha com `2`.
- **Um literal por arquivo na lista.** A guarda imprime a primeira linha que satisfaz; outras no
  mesmo arquivo não aparecem.

## 15. Três afirmações desta sessão que precisaram de correção

Registradas porque foram feitas, e não porque ficaram (P7):

1. **A caixa da tarefa 0.2 ficou desmarcada no commit `68c20a0`**, cuja mensagem diz que ela
   estava marcada. Marcada no commit seguinte, pela execução daquela mesma sessão.
2. **"`corpo()` também satisfaz"** foi escrito como saída da guarda, e era leitura. Rebaixado antes
   do commit.
3. **"O piso de tipo sem chave é inalcançável"** foi escrito porque `data class Vazio()` não
   compila. A guarda lê texto, e não o que compila: medido às `13:24:56Z`, ele reprova. Corrigido
   antes do commit, com a frase errada dita na própria nota.

**Achado novo, pela regra 0.4 do plano: nenhum.** O único desvio encontrado é de citação: o plano
atribui "o piso reprova um catálogo vazio" à guarda de RLS, e o teste com esse nome está em
`RetentionDeclarationTest.kt:148`. Está registrado no `design.md`, decisão 6, e o plano não foi
editado.
