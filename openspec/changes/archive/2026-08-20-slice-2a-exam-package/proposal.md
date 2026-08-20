## Why

§5 chama o `ExamPackage` de **contrato central**: produzido na publicação, imutável, com hash, copiado para dispositivos offline. Tudo depois dele depende dessa forma — a captura da fatia 3 lê a geometria publicada, o sync da 4 puxa a referência imutável, a correção da 5 e da 8 escreve fatos contra os itens que ele declara.

Nada disso existe. O schema tem identidade e billing; o Layout Engine produz `LayoutMap` a partir de arquivo versionado.

§15 dividiu a fatia 2 em **2a** e **2b**, e o critério do corte é o que a entrega consegue **reprovar**. A 2a entrega o menor pacote publicado de ponta a ponta, e seus dois critérios de aceite são os contratos que esse pacote de fato exercita:

- **Roster mutável separado do pacote imutável** — ADR-0002, invariante I5. Nome, turma e matrícula saem do artefato hasheado e vão para um roster que pode mudar, porque o direito de eliminação não alcança cópia imutável já distribuída.
- **Perfil tipográfico declarado no `LayoutMap`** — ADR-0004. O `LayoutProfile` passou a existir na fatia 1.6; falta o pacote publicado dizer sob qual perfil foi produzido.

Os dois ficam caros exatamente depois desta fatia: uma vez que haja pacote publicado e hasheado, corrigi-los envolve artefato já distribuído.

## What Changes

**Contrato (KMP)**

- `ExamPackage` como tipo do domínio compartilhado, com `meta`, `items`, `variants`, `assignments`, `layout`, `answer_key` e `scoring` (§5). É KMP porque os **dois** renderizadores precisam lê-lo, e D-1.1 põe contrato de domínio no código compartilhado.
- `content_hash` sobre a serialização canônica do pacote. O roster **não** está dentro dele, então fica fora do hash por construção, e não por exclusão declarada.
- O cabeçalho do layout passa a declarar o perfil tipográfico que o produziu.

**Persistência**

- `exam`, `exam_package` e `exam_roster`, chaveadas por `organization_id`, com RLS habilitada e forçada como as da fatia 0.
- `exam_package` é **imutável no armazenamento**: `UPDATE` e `DELETE` recusados pelo próprio banco. Imutabilidade que depende de disciplina da aplicação não é imutabilidade.
- `exam_roster` é mutável e pode ser apagado sem tocar no pacote.

**Publicação (Ktor)**

- Publicar uma prova fixa: montar o pacote, calcular o hash, gravar. Republicar a mesma prova produz o mesmo hash.
- Validação server-side antes de gravar: o pacote precisa ser internamente coerente — toda posição de variante aponta item existente, todo item tem gabarito, o layout declara as questões que os itens declaram.

**Renderização**

- `render-fixture.ts` e o teste instrumentado passam a extrair o `LayoutMap` **do pacote**, em vez de ler o arquivo versionado. É o que faz o pacote ter consumidor: artefato sem consumidor não tem como estar errado.
- Paridade e fidelidade passam a julgar o que saiu do pacote.

## Capabilities

### New Capabilities

- `exam-package`: publicação de prova fixa — montagem, validação, hash, imutabilidade no armazenamento, e a separação entre o pacote e o roster.

### Modified Capabilities

Nenhuma.

`identity` já exige isolamento por organização de **todo dado de domínio**, em termos genéricos: as tabelas novas nascem cobertas pelo requisito existente e pela guarda de RLS derivada do catálogo. `print` também não muda — o renderizador continua desenhando a partir de um `LayoutMap`, e de onde esse mapa vem é problema de quem chama, como já era quando ele vinha de arquivo.

## Impact

**Criado**

- `supabase/migrations/` — `exam`, `exam_package`, `exam_roster`, com RLS
- `packages/domain` — tipo `ExamPackage`, serialização canônica, hash, validação
- `apps/api` — publicação e leitura

**Alterado**

- `apps/web/scripts` e o teste instrumentado do Android — passam a ler do pacote
- `packages/domain` — o cabeçalho do `LayoutMap` ganha o perfil

**Dependências novas**: nenhuma.

**Explicitamente NÃO alterado**

- **Marcadores de captura, ArUco e qualidade de impressão** — fatia 2b, que é o escopo original da 2.
- **Variantes múltiplas, blueprint e randomização** — fatia 7. O pacote declara `variants[]` porque o contrato o exige, e esta fatia publica **uma**.
- **Proveniência e licença de item** (I4, ADR-0005), **identidade de aluno** (ADR-0003) e **classe de retenção** (ADR-0006). §15 registra por que ficaram fora: nenhum é exercitado por publicar uma prova fixa e renderizá-la, então entrariam como declarações que a fatia não pode reprovar.
- **Geração por IA** — `prompt_version` e `model_id` existem no contrato de §5 e ficam vazios aqui, porque prova fixa não tem artefato de IA. Preenchê-los é a fatia 6.
- **Sync, gate de pré-voo e Storage** — fatia 4. O pacote é lido do banco; distribuí-lo é outra fatia.
- **Rubrica discursiva e `answer_capture_mode`** — dependem de região discursiva, fatia 5.
