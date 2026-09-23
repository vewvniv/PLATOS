# Cobertura — ETAPA 7.3, `o-fio-preso-nos-dois-lados`

> Este documento tem **duas partes**, no molde de `cobertura-transferencia-entre-aparelhos.md`. A
> **Parte I** é a medição que decidiu abrir a mudança, feita em 2026-09-23 **antes** do
> `/opsx:propose`. A **Parte II** é a da mudança, e ainda não existe. A Parte I não se apaga (P7):
> ela é o estado que a Parte II vai corrigir.

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
fatia-limite, e **não** conserto aqui (P19).

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
