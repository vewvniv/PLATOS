## Why

Quatro DTOs de transporte são **digitados duas vezes** — `apps/api/.../http/dto/` e
`apps/android/.../api/` — e o mapa `QuestionAnswer → string` existe **três vezes**: `tipoGravado()`
na API (`ResultQueries.kt:168`), `tipoNoEnvio()` no Android (`ResultadoDto.kt`), e o
`check (answer_kind in (...))` da migration (`20260917134500_result_tables.sql:85`). É o achado 2.1
da `docs/auditoria-2026-09-18-antes-da-fatia-5.md`, classificado como **grave**, e a ETAPA 6 do
`docs/plano-de-correcao-antes-da-fatia-5.md` é o veículo dele.

Contra o que isso colide está escrito em três lugares, e os três dizem a mesma coisa:
`CLAUDE.md` regra 7 ("não duplique regra de negócio entre apps; código compartilhado pertence ao
domínio KMP"), §14 regra 1 ("contrato antes de código … maior alavanca anti-regressão do projeto")
e §13, que promete que em `packages/domain` **"divergência é impossível por construção"**. Hoje a
divergência é possível por construção e contida por literais JSON combinados à mão nos dois lados.
É uma boa rede — `ResultadoDtoTest` e `ResultRouteTest.corpo()` de fato pegariam um `capture_id`
renomeado — mas é categoricamente mais fraca do que a garantia que §13 descreve, e a diferença
aparece no campo **novo**, que ninguém pensa em prender nos dois literais.

**A justificativa original não se sustenta, e ela mesma diz por quê.** A origem está em
`slice-4a-zero-device-auth/tasks.md:27`: os dois módulos **já dependem** de `packages:domain` e os
dois já importam `com.platos.domain.capture.QuestionAnswer` para preencher esses DTOs — "dividir o
DTO era possível", e o que decidiu contra foi escopo de uma fatia, não arquitetura. Uma decisão de
fatia, legítima como troca pontual, virou o padrão da casa em quatro fatias sem nunca ser reaberta,
e sem ADR. `rigorous.md` §0 é explícito: o que está no nível 1 muda por ADR novo.

**Por que agora.** A fatia 5 acrescenta discursiva — rubrica, transcrição, recorte — e é o **maior
acréscimo de superfície de contrato do projeto**. O custo de convergir cresce com o número de
espelhos, e são quatro. Se a 5 rodar antes, nascem três ou quatro espelhos novos e o ADR-0015 passa
a legislar sobre um estado pior do que o que foi auditado. É a mudança de **prazo mais curto** da
banda de correção.

## What Changes

- **ADR-0015 — Contrato de transporte entre servidor e aparelho**, escrito **antes da primeira
  linha de código**, com as quatro decisões da seção 6.0 do plano: onde o contrato mora, o que se
  move e o que não, o `check` da migration como terceiro registro com conferência cruzada, e os
  testes de literal que ficam.
- **Os DTOs de transporte passam a ter dono único em `packages/domain`** — os quatro pares
  (organização, prova, roster, resultado) viram **um** tipo cada, em código comum KMP.
- **A tradução `QuestionAnswer → string` passa a ter dono único** no mesmo lugar, e as duas cópias
  Kotlin (`tipoGravado()`, `tipoNoEnvio()`) passam a chamá-la.
- **Uma conferência cruzada** entre os valores do domínio e os do `check` da migration, no molde de
  `tools/parity/limiar.mjs` — porque o `check` é guarda do banco e não pode vir de Kotlin.
- **Os espelhos antigos são removidos**, e só no último commit, quando não há mais quem os leia.
- **Nenhum comportamento muda.** O fio é byte a byte o mesmo antes e depois; é isso que a
  verificação desta mudança afirma.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

Nenhuma. Nenhum comportamento observável do sistema muda: os mesmos campos, com os mesmos nomes de
JSON, na mesma ordem, viajam pelo mesmo fio. O que muda é **quantos arquivos os declaram**. É o
mesmo caso de `generatejooq-sem-registro-automatico`, arquivada sem diretório `specs/`, e por isso
esta mudança declara `skip_specs: true`.

A `.openspec.yaml` diz isso, e a tabela da seção "As mudanças, com nome, spec e ordem" do plano
também: **Specs tocadas: nenhuma**.

## Impact

- **`packages/domain`** — código novo em `commonMain`: os DTOs de transporte e o mapa de
  `answer_kind`. É o único módulo que ganha arquivo.
- **`apps/api`** — `http/dto/OrganizationDto.kt`, `http/dto/ExamDto.kt`, `http/dto/ResultDto.kt` e
  `exam/ResultQueries.kt` passam a usar o tipo do domínio; os espelhos saem.
- **`apps/android`** — `api/OrganizacaoDto.kt`, `api/ProvaDto.kt`, `api/RosterDto.kt` e
  `api/ResultadoDto.kt` idem. As traduções para os tipos de tela (`paraOrganizacao`, `paraProva`,
  `paraAluno`) **ficam onde estão**.
- **`tools/parity/`** — um conferidor novo para o `check` da migration, e dois passos no `ci.yml`
  no molde do limiar (um que confere, outro que força divergência e falha se a conferência aceitar).
- **`docs/adr/0015-*.md`** — novo.

### O que NÃO será alterado

- **Nenhum nome de campo JSON, nenhum `@SerialName`, nenhum tipo de campo.** O fio é o mesmo, e a
  igualdade byte a byte é o critério de aceite.
- **`ResultadoDtoTest` e `ResultRouteTest.corpo()` não são apagados nem reescritos.** Eles deixam de
  ser a única garantia e passam a ser o oráculo independente que prova que o fio não mudou durante
  a mudança (P4). É o único ponto desta mudança que, se for quebrado, não tem como ser percebido
  depois.
- **A tradução para colunas de jOOQ** (`ResultQueries.kt`) — é do servidor, e fica no servidor.
- **As traduções para os tipos de tela do aparelho** (`paraProva`, `paraSessao`, `paraOrganizacao`,
  `paraAluno`) — são do aparelho, e ficam no aparelho. A fronteira é o **fio**, e nada além dele.
- **`packages/contracts`** — continua sem existir e continua sem consumidor. Não há client
  TypeScript falando com a API (P18, §14).
- **O espelho do `LayoutMap` em TypeScript** (`apps/web/src/layoutMap.ts`). A auditoria o isentou
  por escrito: é contido por oráculo de saída — paridade, fidelidade, tinta sobre o documento
  rasterizado. Espelho com oráculo independente é troca defensável.
- **O `check` da migration.** Ele continua sendo o terceiro registro, por decisão; o que entra é a
  conferência entre ele e o domínio, e não a geração dele a partir de Kotlin.
- **Nenhum schema, migration, rota, fixture ou hash.**
