## Why

A decisão 4 do ADR-0015 fixou o critério da unificação do contrato de transporte: um `@SerialName`
trocado no domínio tem de derrubar **os dois** testes de literal, e "se derrubar um, a leitura
correta não é 'o teste pegou': é que o outro lado não está preso". A ETAPA 6 aplicou essa mutação a
**um** dos quatro contratos (`capture_id`, no de resultado). **A medição de entrada desta mudança,
feita em 2026-09-23 antes deste `/opsx:propose`, aplicou-a aos outros três**, e o resultado foi o
previsto (`docs/cobertura-o-fio-preso-nos-dois-lados.md`, Parte I, §2):

| Contrato | Aparelho | Servidor |
|---|---|---|
| `GET /me/organizations` · `OrganizationDto.name` → `"nome"` | `ApiPlatosTest` **caiu**, 6 de 14 | **0 de 165** — o buraco |
| `GET .../exams` · `ExamSummaryDto.title` → `"titulo"` | `ApiPlatosPacoteTest` **caiu**, 1 de 7 | **0 de 165** — o buraco |
| `GET .../roster` · `display_name` → `displayName` | `ObtencaoDeRosterTest` **caiu**, 6 de 10 | `ExamPackageRouteTest`, o cenário do roster, **caiu** |

Em organização e em prova, o teste do servidor julga a rota com o mesmo tipo que ela usa para
escrever o corpo (`MeOrganizationsTest.kt:112`, `ExamPackageRouteTest.kt:359`). A igualdade tem o
mesmo código dos dois lados, que é P4 — e `MeOrganizationsTest` continuou afirmando
`"Nova Professora"` verde com o fio dizendo `"nome"`. É a ETAPA 7.3 do
`docs/plano-de-correcao-antes-da-fatia-5.md`, e ela **bloqueia a fatia 5**.

**Por que consertar os dois testes não basta, e por que agora.** A fatia 5 é o maior acréscimo de
contrato do projeto, e todo contrato novo nasce em `com.platos.domain.transport` com o tipo a um
`import` do teste da rota. `json.decodeFromString<NovoDto>(corpo)` é o caminho mais curto para
escrever esse teste — e foi exatamente ele que abriu os dois buracos de hoje. Os dois literais
fecham o passado; **a guarda é o que fecha a 5**. Sem ela, a decisão 4 do ADR-0015 vale para os
quatro contratos auditados e para nenhum dos novos.

## What Changes

- **Uma guarda que reprova tipo `@Serializable` de `com.platos.domain.transport` sem literal JSON
  escrito à mão nos dois lados** — no servidor e no aparelho —, nomeando o tipo e o lado que falta.
  Ela lê os arquivos de origem de `transport/`, e não uma lista mantida à mão, e tem piso. É um
  conferidor Node em `tools/parity/`, no molde de `answer-kind.mjs`, com dois passos no `ci.yml`:
  um que confere, outro que planta um contrato sem literal e falha se a guarda aceitar. O mecanismo
  está no `design.md`.
- **A guarda entra antes dos literais, e nasce vermelha** sobre a árvore real, sem defeito
  plantado. O primeiro vermelho tem de nomear **exatamente** `OrganizationDto` e `ExamSummaryDto`,
  **do lado do servidor**, e mais nada.
- **Dois cenários novos no servidor**, um em `MeOrganizationsTest` e um em `ExamPackageRouteTest`,
  que comparam o corpo inteiro da rota contra JSON escrito à mão, no molde do cenário do roster
  (`ExamPackageRouteTest.kt:261-285`). Os cenários que desserializam **ficam**.
- **As três mutações da Parte I, repetidas depois dos literais** — e agora cada uma tem de derrubar
  os dois lados.
- **Nenhum comportamento muda.** Nenhum DTO, rota, schema, fixture ou hash é tocado.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

Nenhuma. Nenhum comportamento observável do sistema muda: os mesmos corpos, com os mesmos campos,
pelas mesmas rotas. O que muda é **o que os reprova**. É o caso da ETAPA 6
(`contrato-do-fio-com-dono-unico`) e de `generatejooq-sem-registro-automatico`, arquivadas sem
diretório `specs/`, e por isso esta mudança declara `skip_specs: true`. A tabela "As mudanças, com
nome, spec e ordem" do plano diz o mesmo: **Specs tocadas: nenhuma**.

## Impact

- **`tools/parity/fio.mjs`** — novo. Sem dependência nova: `node:fs` e `node:path`, como
  `answer-kind.mjs`. *(Na implementação, também `node:url` — ver `design.md`, decisão 1. Sem
  dependência de npm, como dito.)*
- **`.github/workflows/ci.yml`** — dois passos no job `web`, logo depois dos de `answer_kind`.
- **`apps/api/src/test/.../http/MeOrganizationsTest.kt`** e
  **`apps/api/src/test/.../http/ExamPackageRouteTest.kt`** — um cenário novo em cada.
- **`docs/cobertura-o-fio-preso-nos-dois-lados.md`** — a Parte II, que hoje diz "ainda não existe".
- **Ambiente:** Docker, para os testes de rota do servidor. **Nenhum emulador**: nenhum arquivo de
  `apps/android` muda.

### O que NÃO será alterado

- **Nenhum arquivo de `packages/domain/.../transport/`.** Nenhum DTO é renomeado, ganha campo, muda
  default ou `@SerialName`. As mutações de medição são revertidas, e nada além delas toca
  `transport/` (P25).
- **Os cenários que desserializam** em `MeOrganizationsTest` e em `ExamPackageRouteTest` não são
  apagados nem reescritos para comparar contra `Json.encodeToString` do tipo. Eles afirmam conteúdo
  (quais organizações, qual papel); deixam de ser os únicos a olhar o fio.
- **`AuthenticationTest:131`** (`"kind":"personal"`) fica. Ele afirma isolamento entre usuários, e
  não contrato.
- **Os testes do aparelho** — `ApiPlatosTest`, `ApiPlatosPacoteTest`, `ObtencaoDeRosterTest`,
  `ResultadoDtoTest` — e **os do contrato de resultado no servidor** (`ResultRouteTest`). O lado
  deles já está preso, e foi medido.
- **Os dois testes de `ApiPlatosPacoteTest` que nunca rodaram** (`… sem rede vira SemRede`). São o
  item **7.2.5** do plano, na mudança `o-apk-de-release-e-verificado`, por decisão do mantenedor
  (Parte I, §6). Não entram aqui.
- **Nada fora de `transport/`** entra na guarda: nem o pacote publicado (artefato hasheado, com as
  camadas do ADR-0013), nem o espelho do `LayoutMap` em TypeScript (isentado por escrito na ETAPA
  6), nem `ResultAcceptedDto`, que mora em `apps/api`.
- **`packages/contracts`, OpenAPI ou geração de código** — recusados pela decisão 1 do ADR-0015.
- **`tools/parity/answer-kind.mjs` e `limiar.mjs`** continuam como estão; a guarda nova é uma
  instância a mais do molde, e não uma generalização deles.
