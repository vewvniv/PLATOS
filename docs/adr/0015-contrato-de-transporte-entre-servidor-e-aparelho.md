# ADR-0015 — O contrato de transporte entre servidor e aparelho tem dono único, e ele é `packages/domain`

**Status:** aceito · **Data:** 2026-09-19 · **Fatia-limite:** antes da fatia 5
**Referências:** `ARQUITETURA-FINAL-v3.md` §13 (stack compartilhada), §14 regra 1 (contrato antes de
código) · `CLAUDE.md` regra 7 · `docs/auditoria-2026-09-18-antes-da-fatia-5.md` §2.1 ·
`docs/plano-de-correcao-antes-da-fatia-5.md` ETAPA 6 · ADR-0002 · ADR-0013 · `rigorous.md` §0

## Contexto

§13 promete uma coisa específica sobre `packages/domain`:

> `packages/domain` em KMP — medição de texto, Layout Engine, Bloom, distribuição, scoring, **tipos
> de contrato**. Compilado para JVM e Android a partir do mesmo código; **divergência é impossível
> por construção**.

Hoje a divergência é **possível** por construção. Quatro contratos de transporte são digitados duas
vezes:

| Contrato | Servidor | Aparelho |
|---|---|---|
| `GET /me/organizations` | `http/dto/OrganizationDto.kt` · `OrganizationDto` | `api/OrganizacaoDto.kt` · `OrganizacaoDto` |
| `GET .../exams` | `http/dto/ExamDto.kt` · `ExamSummaryDto` | `api/ProvaDto.kt` · `ProvaDto` |
| `GET .../roster` | `http/dto/ExamDto.kt` · `RosterEntryDto` | `api/RosterDto.kt` · `RosterEntryDto` |
| `POST .../results` | `http/dto/ResultDto.kt` · `ResultSubmissionDto` + `AnswerObservationDto` | `api/ResultadoDto.kt` · `EnvioDeResultadoDto` + `ObservacaoDto` |

E o mapa `QuestionAnswer → string` existe **três** vezes: `tipoGravado()` em `ResultQueries.kt:168`,
`tipoNoEnvio()` em `ResultadoDto.kt`, e o `check (answer_kind in (...))` em
`supabase/migrations/20260917134500_result_tables.sql:85`.

O que contém a divergência hoje são **literais JSON escritos à mão nos dois lados** —
`ResultadoDtoTest` no aparelho e `ResultRouteTest.corpo()` no servidor. É uma boa rede: ela de fato
pegaria um `capture_id` renomeado. Mas é categoricamente mais fraca do que a garantia que §13
descreve, e a diferença aparece no **campo novo**, que ninguém pensa em prender nos dois literais.

**A justificativa original é de escopo de fatia, e a própria tarefa que a criou admite o caminho
certo.** `slice-4a-zero-device-auth/tasks.md:27`:

> **"Compartilhado" virou espelho, e não arquivo compartilhado**: os dois módulos dependem de
> `packages:domain`, então **dividir o DTO era possível**, mas a proposal e a 7.4 declaram
> `packages/domain` e `apps/api` intocados.

Os dois módulos **já** declaram `implementation(project(":packages:domain"))`, e os dois **já**
importam `com.platos.domain.capture.QuestionAnswer` para preencher exatamente esses DTOs. Não havia
obstáculo técnico; havia uma fronteira de fatia.

Uma decisão de fatia, legítima como troca pontual, virou **o padrão da casa em quatro fatias** sem
nunca ser reaberta. `rigorous.md` §0 é explícito: o que está no nível 1 muda por **ADR novo**, não
por decisão no nível 3. Não havia ADR, e não havia linha em §16 ou §17 com fatia-limite e dono — o
item **flutuava**, que é o que §16 existe para impedir.

**A janela.** A fatia 5 acrescenta discursiva — rubrica, transcrição, recorte — e é o maior
acréscimo de superfície de contrato do projeto. O custo de convergir cresce com o número de
espelhos, e são quatro. Se a 5 correr antes, nascem três ou quatro espelhos novos e este ADR passa a
legislar sobre um estado pior do que o auditado.

## Decisão

### 1. O contrato mora em `packages/domain`

§13 já nomeia `packages/domain` como dono de "tipos de contrato", e o tipo de que os dois lados
dependem para preencher esses DTOs — `QuestionAnswer` — já está lá. **Nenhuma tecnologia nova,
nenhum módulo novo, nenhum plugin novo.**

**`packages/contracts` do §14 continua sem existir e continua sem consumidor.** §14 registra o
contrato OpenAPI como a via para gerar tipos TypeScript, e não há client TypeScript falando com a
API — `apps/web` não fala com ela. Criá-lo agora seria tecnologia sem consumidor, que é o que P18
proíbe. Quando houver esse client, a decisão se reabre; até lá, o KMP é o dono, porque é ele que os
dois lados compilam.

### 2. Move-se o fio, e nada além dele

**Movem-se:** os quatro DTOs de transporte e a tradução `QuestionAnswer → string` do `answer_kind`.

**Não se move a tradução para colunas de jOOQ.** O `insertInto(...).set(...)` de `ResultQueries.kt` e
o `alternativas(): Array<String?>` que o alimenta são do **servidor** — eles falam com o banco, e o
banco é só dele. O que muda ali é uma linha: `tipoGravado()` passa a chamar o mapa do domínio.

**Não se movem as traduções para os tipos de tela do aparelho** — `paraOrganizacao`, `paraProva`,
`paraAluno`, e a montagem em `corpoDoEnvio`. Elas atravessam a fronteira entre o vocabulário do fio
(inglês) e o vocabulário do aparelho (português), e essa fronteira é do aparelho.

**A fronteira é o fio.** O critério não é "o que os dois lados usam", é "o que atravessa a rede".
Um critério mais largo arrastaria para o domínio código que só um lado executa, e o módulo
compartilhado passaria a ser o lugar onde tudo acaba morando — que é como um domínio compartilhado
deixa de significar coisa alguma.

### 3. O `check` da migration continua sendo o terceiro registro, e isso fica dito

O `check (answer_kind in (...))` **não** passa a ser gerado a partir de Kotlin, e **não** é removido.

Ele é a guarda do banco. Um `check` escrito por quem escreve as linhas não é guarda de coisa
nenhuma: ele aceitaria, por construção, tudo o que o código produzisse, inclusive o que o código
produzisse errado. A independência entre o que grava e o que admite é o valor dele, e gerá-lo
destruiria exatamente esse valor.

**O que este ADR exige no lugar é uma conferência cruzada** entre os valores do domínio e os do
`check` — um programa que lê os dois **dos arquivos de origem**, e não de uma cópia, e reprova se
divergirem.

O instrumento já existe nesta árvore e não se inventa nada: `tools/parity/limiar.mjs`, cujo
comentário descreve esta forma de defeito palavra por palavra — *"aparece em três registros que não
se conhecem … Divergir entre eles não quebra teste nenhum."* Ele roda no `ci.yml` com dois passos:
um que confere, outro que força um valor divergente e falha se a conferência aceitar. O segundo
passo não é zelo: sem ele, "a conferência passou" é indistinguível de "a conferência não confere
nada".

É o **mesmo instrumento** que a ETAPA 7.1 constrói para a versão do renderizador. Duas instâncias
nomeadas do mesmo molde, e **não** um conferidor genérico: abstração sem necessidade comprovada é a
regra 8 do `CLAUDE.md`.

### 4. Os testes de literal ficam

`ResultadoDtoTest` e `ResultRouteTest.corpo()` **não** são apagados nesta mudança, e **não** são
reescritos para comparar contra `Json.encodeToString` do tipo novo.

Apagá-los porque "agora o tipo é compartilhado" destruiria a única evidência de que a migração foi
neutra. Reescrevê-los contra o serializador poria o mesmo código dos dois lados da igualdade — que é
o defeito que a KDoc de `ResultadoDtoTest` já registra:

> renomear `capture_id` para `captureId` continuaria verde aqui, e a rota recusaria todo envio no
> aparelho de verdade.

É P4: oráculo não compartilha código com o que ele julga.

Eles deixam de ser a única garantia e passam a ser o **oráculo independente** da unificação. E são
mais do que isso: são a asserção que distingue "movi o arquivo" de "unifiquei o contrato" — um
`@SerialName` trocado no domínio tem de derrubar **os dois**, e se derrubar só um, o fio não está
preso nos dois lados.

## Consequências

- Os quatro contratos passam a ter **um** declarante. Campo novo nasce nos dois lados ao mesmo
  tempo, ou não compila — que é o que §13 promete e o que §14 regra 1 chama de maior alavanca
  anti-regressão do projeto.
- O mapa `answer_kind` passa a ter **dois** registros em vez de três, e os dois restantes —
  domínio e `check` — passam a ser conferidos um contra o outro. Três registros cegos viram dois
  registros que se conhecem.
- **Nenhum comportamento observável muda.** Os mesmos campos, com os mesmos nomes de JSON, na mesma
  ordem, pelo mesmo fio. É por isso que a mudança OpenSpec que executa este ADR não tem delta de
  spec, e declara `skip_specs: true`.
- `packages/domain` passa a compilar os DTOs também para o alvo `js()`, que não os consome. É custo
  de compilação, não risco de comportamento; um source set só para evitá-lo seria abstração
  prematura.
- Um nome de tipo tem de ser escolhido entre os dois de cada par — dois nomes não sobrevivem a um
  tipo só. Fica o do servidor, porque os nomes de campo do fio já são em inglês. **Isto não é a
  renomeação que P25 proíbe:** nenhum `@SerialName` muda, nenhum nome de campo JSON muda, e o nome
  da classe Kotlin não atravessa a rede.
- A fatia 5 acrescenta contrato **num lugar só**. É a consequência que decidiu o prazo.

## Como isto poderia falhar em silêncio

**A unificação mudar o fio sem que nada acuse.** Os dois lados divergem hoje em algo que não é nome
de campo: o servidor declara `student_token: String? = null` e `answer_options: List<String> =
emptyList()`, e o aparelho não declara default nenhum. Os defaults do servidor são **tolerância de
entrada**, que é comportamento observável — um corpo que omita `student_token` é aceito hoje. Mas o
codificador do aparelho usa `Json { explicitNulls = true }` com `encodeDefaults` em `false` por
omissão, e defaults presentes podem fazer campos deixarem de ser emitidos. A guarda é a **comparação
byte a byte** do corpo produzido antes e depois, com os dois artefatos gerados na mesma sessão (P3),
e os dois testes de literal como segunda camada independente.

**Os testes de literal parecerem redundantes no commit que remove os espelhos.** É exatamente aí que
eles valem mais, e é aí que apagá-los seria mais tentador. A decisão 4 existe porque este é o único
ponto desta mudança que, se for quebrado, não tem como ser percebido depois.

**A conferência do `check` passar sem conferir.** Um conferidor que não ache o padrão que procura e
saia `0` é pior do que nenhum. A guarda é o segundo passo do CI, que força um valor divergente e
falha se a conferência aceitar — o mesmo que `limiar.mjs --esperado` já faz.

**A mutação derrubar só um lado e isso ser lido como sucesso.** Um `@SerialName` trocado tem de
derrubar os dois testes de literal. Se derrubar um, a leitura correta não é "o teste pegou": é que o
outro lado não está preso, e a mudança não entregou o que prometeu.

### Atualização de 2026-09-23: a rede do Contexto valia para dois dos quatro contratos

O Contexto diz que a divergência era "contida por literais JSON escritos à mão nos dois lados", e
cita os dois testes do contrato de resultado. Nos contratos de organização e de prova **não** era:
o teste do servidor desserializa o corpo com o mesmo tipo que a rota usa para escrevê-lo, e só o
aparelho tem literal. Já era assim antes deste ADR, com os tipos do próprio servidor; a unificação
só deu à mutação da decisão 4 um lugar onde ser injetada.

**A decisão não muda.** É o critério dela — os dois testes de literal têm de cair — que expõe o
buraco, e o parágrafo acima já nomeava esse modo de falha. O que muda é o registro: a mutação foi
aplicada a um contrato só, e o que se sabe dos outros três é **conferido por leitura**, não medido.
**Medido no mesmo dia:** a mutação derrubou só o aparelho em organização e em prova, e os dois lados
no roster — `docs/cobertura-o-fio-preso-nos-dois-lados.md`, Parte I.
O detalhe está em `docs/cobertura-contrato-do-fio-com-dono-unico.md` §8; a medição, os dois
literais e a guarda para os contratos que a fatia 5 vai criar são a ETAPA 7.3 do plano de correção,
mudança `o-fio-preso-nos-dois-lados`.
