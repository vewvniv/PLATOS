## Context

Ver `proposal.md` — Why, e o achado 2.1 da `docs/auditoria-2026-09-18-antes-da-fatia-5.md`. Aqui só
o estado que decide a abordagem.

**O terreno já é favorável, e é isso que torna a mudança barata.** Os dois módulos já declaram
`implementation(project(":packages:domain"))` (`apps/api/build.gradle.kts:60`,
`apps/android/build.gradle.kts:250`), os dois já declaram `kotlinx.serialization.json`, e os dois já
importam `com.platos.domain.capture.QuestionAnswer` para **preencher** os DTOs que estão duplicados.
Nenhuma dependência nova, nenhum módulo novo, nenhum plugin novo.

**O que está duplicado, com o arquivo de cada metade:**

| Contrato | Servidor | Aparelho |
|---|---|---|
| `GET /me/organizations` | `http/dto/OrganizationDto.kt` · `OrganizationDto` | `api/OrganizacaoDto.kt` · `OrganizacaoDto` |
| `GET .../exams` | `http/dto/ExamDto.kt` · `ExamSummaryDto` | `api/ProvaDto.kt` · `ProvaDto` |
| `GET .../roster` | `http/dto/ExamDto.kt` · `RosterEntryDto` | `api/RosterDto.kt` · `RosterEntryDto` |
| `POST .../results` | `http/dto/ResultDto.kt` · `ResultSubmissionDto` + `AnswerObservationDto` | `api/ResultadoDto.kt` · `EnvioDeResultadoDto` + `ObservacaoDto` |

E o mapa `QuestionAnswer → string`, três vezes: `ResultQueries.kt:168` (`tipoGravado`),
`ResultadoDto.kt` (`tipoNoEnvio`), e `supabase/migrations/20260917134500_result_tables.sql:85`.

**A rede que existe hoje**, e que esta mudança não pode danificar:
`apps/android/.../ResultadoDtoTest.kt` prende o corpo do envio contra um literal escrito à mão, e
`apps/api/.../ResultRouteTest.corpo()` monta o mesmo JSON à mão e o envia para a rota real. Os dois
literais precisam concordar; é por isso que eles são **literais**.

## Goals / Non-Goals

**Goals:**

- Dono único, em `packages/domain`, para os quatro DTOs de transporte e para o mapa de `answer_kind`.
- Que `@SerialName` trocado no domínio derrube os **dois** testes de literal — a propriedade que
  distingue "movi o arquivo" de "unifiquei o contrato".
- Que o fio seja **byte a byte** o mesmo antes e depois.
- Que o `check` da migration, que não pode vir de Kotlin, passe a ser **conferido** contra o domínio.

**Non-Goals (nível de desenho, além do que o `proposal.md` já exclui):**

- Não se busca contrato compartilhado com o **web**. `apps/web` não fala com a API.
- Não se busca unificar o tipo de **resposta** (`ResultAcceptedDto`) — ele não tem espelho no
  aparelho, e criar um seria abstração sem consumidor (regra 8 do `CLAUDE.md`, P18).
- Não se busca mover validação. `paraNota()` e as regras de coerência continuam onde estão.

## Decisions

As decisões 1 a 4 são o conteúdo do **ADR-0015**, e a seção 6.0 do plano é a fonte delas. Elas estão
**tomadas**; o que está escrito aqui é o registro, não a deliberação.

### 1. O contrato mora em `packages/domain`

§13 já nomeia `packages/domain` como dono de "tipos de contrato"
(`ARQUITETURA-FINAL-v3.md:414`), e o tipo de que os dois lados dependem — `QuestionAnswer` — já está
lá. Nenhuma tecnologia nova, nenhum módulo novo.

**`packages/contracts` do §14 continua sem existir e continua sem consumidor**, porque não há client
TypeScript falando com a API (P18).

### 2. Move-se o fio, e nada além dele

**Movem-se:** os DTOs de transporte e a tradução `QuestionAnswer → string`.

**Não se movem:**

- **A tradução para colunas de jOOQ** (`ResultQueries.kt` — o `insertInto(...).set(...)` e
  `alternativas(): Array<String?>`). É do servidor. O que muda ali é uma linha: `tipoGravado()` passa
  a chamar o mapa do domínio.
- **As traduções para os tipos de tela do aparelho** — `paraOrganizacao`, `paraProva`, `paraAluno`,
  e a montagem em `corpoDoEnvio`. São do aparelho.

A fronteira é o **fio**. O critério não é "o que os dois lados usam", é "o que atravessa a rede".

### 3. O `check` da migration continua sendo o terceiro registro, e isso fica dito

Ele é a guarda do banco, e guarda do banco não pode vir de Kotlin — um banco com o `check` escrito
por quem escreve as linhas não é guarda de coisa nenhuma.

O que o ADR exige no lugar é **uma conferência cruzada** entre os valores do domínio e os do `check`,
no molde que esta árvore já tem: `tools/parity/limiar.mjs`, cujo comentário descreve esta forma de
defeito palavra por palavra — *"aparece em três registros que não se conhecem … Divergir entre eles
não quebra teste nenhum."* Ele lê **dos arquivos de origem**, e não de uma cópia, e o `ci.yml` o roda
com dois passos: um que confere, outro que força um valor divergente e falha se a conferência
aceitar.

**É o mesmo instrumento que a ETAPA 7.1 constrói para a versão do renderizador.** Isso é deliberado:
duas instâncias do mesmo molde, e não um conferidor genérico — abstração sem necessidade comprovada
é a regra 8 do `CLAUDE.md`.

### 4. Os testes de literal ficam, e esta é a decisão mais frágil da mudança

`ResultadoDtoTest` e `ResultRouteTest.corpo()` **não** são apagados, nem reescritos para comparar
contra `Json.encodeToString` do tipo novo.

Apagá-los porque "agora o tipo é compartilhado" destruiria a única evidência de que a migração foi
neutra. Reescrevê-los contra o serializador poria o mesmo código dos dois lados da igualdade — que é
exatamente o que a KDoc de `ResultadoDtoTest` já registra como o defeito a evitar: *"renomear
`capture_id` para `captureId` continuaria verde aqui, e a rota recusaria todo envio no aparelho de
verdade"*. É P4: oráculo não compartilha código com o que ele julga.

Eles deixam de ser a única garantia e passam a ser o **oráculo independente** desta mudança.

**Este é o único ponto desta etapa que, se for quebrado, não tem como ser percebido depois.**

### 5. O nome do tipo compartilhado é o do servidor, e isto não é renomeação oportunista

Os pares têm nomes diferentes nos dois lados — `ResultSubmissionDto`/`EnvioDeResultadoDto`,
`OrganizationDto`/`OrganizacaoDto`. Unificar obriga a escolher **um**: dois nomes não sobrevivem a um
tipo só. Fica o do servidor, porque os nomes de campo do fio já são em inglês e o nome do tipo passa
a concordar com eles.

**Isto não é o que a proibição do plano veda.** O que P25 e o plano proíbem é "aproveitar para
renomear campos, acertar plurais ou uniformizar português/inglês" — no **fio**. Nenhum `@SerialName`
muda, nenhum nome de campo JSON muda. O nome da classe Kotlin não atravessa a rede, e escolher um
nome é forçado pela unificação, não aproveitado a partir dela.

### 6. Os defaults do servidor ficam no tipo compartilhado, e o aparelho ganha `encodeDefaults = true`

**Esta é a armadilha desta mudança, e ela é a razão de a comparação byte a byte existir.**

Os dois lados divergem hoje em algo que não é nome de campo:

| Campo | Servidor | Aparelho |
|---|---|---|
| `student_token` | `String? = null` | `String?`, sem default |
| `answer_options` | `List<String> = emptyList()` | `List<String>`, sem default |

Os defaults do servidor são **tolerância de entrada**, que é comportamento observável: um corpo que
omita `student_token` é aceito hoje. Removê-los transformaria esse corpo num 400 — mudança de
comportamento, que esta mudança não faz.

Mantê-los, porém, alcança o codificador do aparelho. `ResultadoDto.kt:52` usa
`Json { explicitNulls = true }`, e `encodeDefaults` é `false` por omissão: hoje isso não tem efeito
**porque os DTOs do aparelho não têm default nenhum**. Com os defaults presentes, a previsão é que
`"student_token":null` e `"answer_options":[]` deixem de ser emitidos — e os dois estão no literal de
`ResultadoDtoTest`, inclusive num cenário próprio (*"folha avulsa manda student_token nulo, e nao
omite o campo"*).

**A decisão:** o tipo compartilhado carrega os defaults do servidor, e o `Json` privado de
`corpoDoEnvio` passa a declarar `encodeDefaults = true`. Ele é privado ao arquivo e serve só à
codificação do envio; a decodificação (`ClienteHttp.kt:70`) não é afetada por `encodeDefaults`.

**A previsão acima é previsão, e não medição (P6).** Ela é confirmada ou desmentida pela tarefa que
compara os corpos byte a byte — e se for desmentida, vale a regra de parada da decisão 8.

### 7. A ordem dos commits é a regra 1 do `CLAUDE.md`, e o vermelho de cada um diz o quê

1. **Contrato no KMP**, sem consumidor. Código novo; nada o lê ainda. Build verde.
2. **Consumidor do servidor.** `apps/api` passa a usar o tipo do domínio; os literais de
   `ResultRouteTest` **não** mudam.
3. **Consumidor do aparelho.** `apps/android` idem; os literais de `ResultadoDtoTest` **não** mudam.
4. **Os espelhos antigos são removidos** — e só aqui, quando não há mais quem os leia.

Separar 2 de 3 não é cerimônia: se o corpo do envio mudar, é o commit 3 que diz isso, e um commit
único não diria de que lado veio.

### 8. Regra de parada — o instrumento não se conserta para caber na previsão

Regra 0.5 do `docs/plano-de-correcao-antes-da-fatia-5.md`, e ela vale sobre toda esta mudança:

> Toda etapa prevê **qual conjunto de cenários deve cair** sob a mutação. Se o conjunto real for
> diferente do previsto — **mais, menos, ou outros** —, **pare**. Não conserte o instrumento, não
> afrouxe a asserção, não ajuste a previsão em silêncio. Escreva o conjunto real ao lado do previsto
> e diga o que ele significa (P7, P12, P14).

Em particular, e porque esta é a mudança em que o erro seria mais tentador: **se cair só um dos dois
testes de literal, o fio não está preso nos dois lados e a mudança não entregou o que prometeu.** A
correção certa não é acrescentar uma asserção ao lado que não caiu — é parar e escrever por que ele
não caiu.

O precedente de como se faz isso está na ETAPA 3, tarefa 1.2 da mudança
`params-hash-no-pacote-publicado`: previsto 2, real 14, a regra disparou e os dois conjuntos ficaram
escritos lado a lado.

## Risks / Trade-offs

**O corpo do envio mudar em silêncio** (decisão 6) → A âncora é a comparação byte a byte: guardar o
corpo produzido pelo aparelho **antes** do commit 1 e compará-lo com o produzido depois do commit 3,
com os dois artefatos gerados na **mesma sessão** (P3). Igualdade byte a byte é o que prova que a
unificação foi neutra no fio. Os dois testes de literal são a segunda camada, e são independentes.

**A mutação derrubar só um lado** → É exatamente o desfecho que a regra de parada cobre, e a
distinção que ela protege é a razão de a mudança existir. Ver decisão 8.

**Alguém "limpar" os testes de literal no commit 4** → Decisão 4. O commit 4 remove **espelhos de
DTO**, e não testes. O risco é real porque no commit 4 os testes parecem redundantes — e é
precisamente aí que eles valem mais.

**`packages/domain` declara `kotlinx.serialization.json` como `implementation`**, não `api` → Se os
consumidores não enxergarem o serializador transitivamente, a compilação acusa no commit 2. Os dois
já declaram a biblioteca por conta própria, então a previsão é que não acuse; se acusar, a correção é
de build e fica no commit 1, que é onde o contrato mora.

**`packages/domain` compila também para `js()`** → Os DTOs entram em `commonMain` e não usam nada de
plataforma, então o alvo `js` os compila sem consumidor. Isso é custo de compilação, não risco de
comportamento; criar um source set só para evitá-lo seria abstração prematura.

**O conferidor do `check` virar "um conferidor de enums"** → Ele confere **um** `check` nomeado
contra **um** tipo nomeado. Proibição explícita, mesma razão que o plano dá para `renderizador.mjs`
na ETAPA 7.

## Decisões já tomadas contra — o que é proibido nesta etapa

Esta seção não lista alternativas em aberto. São recusas, com a razão que o plano dá para cada uma.

- **Criar `packages/contracts`, gerar OpenAPI, ou introduzir qualquer geração de código.** Não há
  consumidor (P18), e §14 já registrou que o contrato OpenAPI espera client TypeScript. Recusado.
- **Mover, junto, o espelho do `LayoutMap` em TypeScript** (`apps/web/src/layoutMap.ts`). É categoria
  diferente, e a auditoria o isentou **por escrito**: é contido por oráculo de saída — paridade,
  fidelidade, tinta sobre o documento rasterizado. Espelho com oráculo independente é troca
  defensável; espelho guardado só por literal combinado não é. Mexer nele é refatoração fora de
  escopo (P19). Recusado.
- **"Aproveitar" para renomear campos, acertar plurais ou uniformizar português/inglês.** P25.
  Recusado. (A escolha do nome da **classe** está na decisão 5, e é outra coisa.)
- **Apagar os testes de literal.** Decisão 4, e é o único ponto desta etapa que, se for quebrado, não
  tem como ser percebido depois. Recusado.

## Migration Plan

Não há migração de dado, de schema nem de artefato. O fio é o mesmo, o banco é o mesmo, os pacotes
publicados são os mesmos, nenhum hash muda.

**Reversibilidade:** cada um dos quatro commits é revertível por si. O commit 4 é o único que destrói
informação (os espelhos), e só depois de os commits 2 e 3 terem provado que ninguém os lê.

**Aparelho:** a ETAPA 6 declara **emulador** no cabeçalho. O ambiente não se toca sem perguntar
(P22, regra 0.9 do plano), e isso vale inclusive em modo automático.
