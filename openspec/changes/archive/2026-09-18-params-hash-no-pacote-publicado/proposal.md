## Why

**I3 está implementada pela metade.** A invariante exige que todo artefato gerado por IA carregue
`prompt_version`, `model_id` e `params_hash`. `PackageMeta` carrega os dois primeiros. O terceiro
aparece **duas vezes em toda a árvore** — `CLAUDE.md:38` e `ARQUITETURA-FINAL-v3.md:48` — e zero
vezes em código, schema ou fixture (achado 4.1 da `docs/auditoria-2026-09-18-antes-da-fatia-5.md`).

E a KDoc do próprio `PackageMeta` afirma o contrário:

> "a fatia 6 preenche estes campos **sem mexer no contrato** — que é exatamente o que I3 existe para
> garantir: quando a geração chegar, **não há o que retrofitar**."

**A afirmação é falsa, e falsa no campo que mais custa.** O `ExamPackage` é hasheado sobre a
serialização canônica com `encodeDefaults = true`: acrescentar `params_hash` muda o `content_hash`
de **todo** pacote. É precisamente o custo que motivou pôr os outros dois cedo. Dois de três não é
"não há o que retrofitar".

**Por que agora, e não na fatia 6.** Porque a quebra de hash acontece **uma vez só**, e a fatia 5
reabre o contrato do pacote — rubrica, `expected_lines`, região discursiva. Deixar `params_hash` para
lá faria uma mudança carregar duas razões de contrato não relacionadas (P25) e o hash quebrar duas
vezes. Esta é a janela barata: pré-lançamento, com duas provas de conferência publicadas. Depois da
primeira turma real ela não existe mais.

**A origem é uma contradição da arquitetura, não desleixo.** §5 lista o conteúdo de `meta` **sem**
`params_hash`, enquanto §2 (I3) o exige. A implementação seguiu §5. Pela precedência do
`rigorous.md` §0, é a invariante que vence — e é isso que **ADR-0014** registra.

## What Changes

- **ADR-0014**, antes de qualquer linha de código. Decide quatro coisas: que §2 vence §5 e corrige a
  lista do §5; a semântica dos três campos; a consequência aceita da quebra de hash; e que
  `meta.exam_id` **não** é renomeado.
- `PackageMeta` ganha `@SerialName("params_hash") val paramsHash: String? = null`, ao lado dos outros
  dois, com a KDoc corrigida.
- As três fixtures de pacote são regravadas pelo caminho que já existe, e os dois literais de hash
  acompanham.
- Um cenário novo isola a **camada (b)** de ADR-0013 sobre um pacote do contrato anterior, congelado
  de propósito.
- Uma asserção executável de que `meta.exam_id` e o `short_id` são o mesmo valor — o que entra no
  lugar da renomeação recusada (achado 5.4).

**BREAKING, e o quebrado é o artefato publicado.** Pacotes publicados antes desta mudança mantêm os
bytes que têm (`exam_package` é imutável) e passam a ser **recusados** por um aplicativo atualizado,
na camada (b): parsear e reserializar deixa de ser identidade, porque `encodeDefaults = true` injeta
`"params_hash":null` que não estava lá. **Essa recusa é o comportamento correto** — é exatamente o
que a camada (b) existe para pegar, e é alta e não silenciosa. O caminho para aquelas provas é o que
ADR-0009 já manda: publicar prova nova, com `short_id` próprio.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `exam-package`: ganha um requisito sobre **o que o pacote declara de proveniência**. Hoje a spec
  descreve o perfil que produziu a geometria e a imutabilidade pelo hash, mas não diz que o artefato
  carrega a tripla de I3 nem o que a ausência de um dos três significa. Passa a dizer, e a dizer que
  **nulo e vazio são coisas diferentes**.

**Nenhuma outra capability é tocada.** `scan-session` e `device-session` descrevem a conferência do
pacote no aparelho, e o comportamento delas **não muda**: um pacote que não bate na camada (b)
continua sendo recusado por fidelidade de interpretação, exatamente como o requisito já diz. O que
muda é que passa a existir um pacote real naquela condição — e isso é cenário de teste, não
requisito novo.

## Impact

**Contrato (KMP, e é o que muda primeiro)**
- `packages/domain/.../ExamPackage.kt` — `PackageMeta` ganha o campo; a KDoc sai da afirmação falsa.

**Artefatos versionados — três, e só três**
- `fixtures/prova-referencia.package.json`, `fixtures/prova-2.package.json`,
  `fixtures/prova-referencia.turma.package.json`.
- `fixtures/prova-referencia.layout.json` e `fixtures/folha-de-teste.layout.json` **não podem mudar**:
  `params_hash` está no `ExamPackage`, não no `LayoutMap`. Conferir isso no `git diff` é a guarda de
  vacuidade desta mudança (P13).
- `fixtures/pacote-do-contrato-anterior.json` — **novo**, congelado byte a byte antes da regravação.

**Literais de hash — dois**
- `packages/domain/src/commonTest/.../ExamPackageTest.kt:21` (`HASH_DA_FIXTURE`)
- `apps/api/src/test/.../ExamPublicationTest.kt:32` (`hashDaFixture`)
- `ApiPlatosPacoteTest.HASH` **não** é da fixture — é hash sintético de `MockEngine`. Não se toca.

**Testes**
- `apps/android/.../ConferenciaDePacoteTest` — cenário novo, com guarda de vacuidade.
- Um teste novo no domínio para a igualdade `meta.exam_id` ↔ `short_id` ↔ payload do QR.

**Registro**
- `docs/adr/0014-....md` — novo.
- `ARQUITETURA-FINAL-v3.md` §5 — a lista de `meta` ganha `params_hash`, com a contradição dita e não
  apagada (P7).
- `docs/cobertura-params-hash-no-pacote-publicado.md`.

**Não muda**
- Nenhum schema de banco, nenhuma migration, nenhuma rota.
- `apps/web` não muda em código — mas **está no caminho da verificação**: `examPackage.ts` lê
  `fixtures/prova-referencia.package.json` e alimenta a paridade.
- Nenhum nome de campo. `meta.exam_id` continua se chamando `exam_id` e continua carregando o
  `short_id`, por decisão escrita em ADR-0014.

**Verificação**
- Paridade e fidelidade fechadas **na mesma sessão**, com os artefatos dos dois lados gerados nela
  (P23, P3). Zona vermelha.
- **Ambiente: emulador** para `connectedDebugAndroidTest` — P22 vale, perguntar antes.

**Referências**
- `docs/plano-de-correcao-antes-da-fatia-5.md`, ETAPA 3 — o veículo, os commits e as proibições.
- `docs/auditoria-2026-09-18-antes-da-fatia-5.md` §4.1 e §5.4 — os achados.
- ADR-0008 (texto canônico), ADR-0009 (uma prova, um pacote), ADR-0013 decisão 4 (as três camadas).
