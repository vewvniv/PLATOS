## Why

A fatia 1 do roadmap (`ARQUITETURA-FINAL-v3.md` §15) é o Layout Engine porque é onde mora o risco que a rejeição de D2 criou: com a renderização 100% client-side, **dois renderizadores podem divergir e quebrar o OMR em silêncio**. §16 é explícito — "se o teste de paridade não existir, vira o maior risco do projeto".

O momento é agora e não depois porque a geometria é a única coisa desta arquitetura que **entra em artefato imutável**. Assim que a fatia 2 publicar `ExamPackage` com hash, todo pacote já emitido carrega a geometria vigente; descobrir divergência de medição depois disso significa provas impressas que o OMR lê errado, sem caminho de correção retroativo. A medição de texto própria (§6, item 4) é o keystone: é o trabalho mais subestimado do projeto e o único ponto onde cada plataforma faz naturalmente diferente.

Esta mudança recorta a fatia 1 pelo **conteúdo**, não pela camada: percorre o caminho vertical inteiro — medição → LayoutMap → dois renderizadores → paridade em CI → papel medido com régua — restrito à **folha puramente objetiva**. É o menor caminho completo que ainda valida o risco central, e é exatamente a folha que a fatia 3 precisa para corrigir offline.

## What Changes

**Módulo compartilhado KMP** (`packages/domain`, primeiro módulo KMP do projeto, previsto em §13)
- Alvos JVM, Android e JS a partir do mesmo código-fonte.
- **Medição de texto em Kotlin puro** sobre tabelas `cmap`/`hmtx`/`kern` extraídas do TTF embarcado como recurso versionado (D36). Nenhuma API de plataforma envolvida — resultado idêntico nos três alvos por construção.
- **Grade vertical de 3 mm** (§7): toda altura de bloco é múltiplo dela, tornando a paginação um problema de inteiros.
- **Paginação por DP** minimizando `Σ(sobra)² + penalidades`, sobre super-blocos indivisíveis (D34). N ≤ 60 blocos, O(N²).
- **`LayoutMap`** como função pura e determinística: páginas, região `ANSWER_BLOCK`, quads ArUco, coordenadas normalizadas `(u,v) ∈ [0,1]²` ao quadrilátero (§6, item 2).
- **Primitivas de desenho** (`DrawRect`, `DrawCircle`, `DrawText`, `DrawImage`, `DrawAruco`) — o KMP calcula, a plataforma só traduz.
- **Validação do `LayoutMap`** como função pura (§6, "validação server-side sem renderizar"): IDs únicos, regiões sem sobreposição, coordenadas em faixa. Inerte nesta fatia — nenhum endpoint a consome ainda.
- `layout_engine_version` e `min_renderer_version` no `LayoutMap` (D24).

**Dois renderizadores** (~200 linhas cada, tradução de primitiva → API nativa)
- `apps/web`: React + TypeScript + Vite, renderizador sobre `pdf-lib`.
- `apps/android`: módulo Android, renderizador sobre `PdfDocument`/`Canvas`.
- Ambos **recusam renderizar** quando sua versão é inferior ao `min_renderer_version` do mapa (D24).

**Teste de paridade em CI** (§6, guardas complementares)
- Rasteriza o mesmo `LayoutMap` nas duas plataformas e afirma tolerância de **0,3 mm** nos centroides de ArUcos e bolhas.
- Paridade de medição entre alvos KMP: as métricas de texto e o `LayoutMap` resultante são idênticos byte a byte em JVM, Android e JS.

**Fixture e verificação física**
- Definição de prova objetiva versionada no repositório, entrada pura do Layout Engine e corpus dos testes golden e de paridade.
- Protocolo documentado de impressão em A4 a 100% sem escala, com as medidas observadas registradas na tarefa — a única verificação desta fatia que nenhum teste automatiza.

## Capabilities

### New Capabilities
- `layout-engine`: medição de texto determinística, grade de 3 mm, agrupamento em super-blocos, paginação por DP, cálculo do `LayoutMap` com regiões escaneáveis em coordenadas normalizadas, e validação do mapa sem renderizar.
- `print`: tradução do `LayoutMap` em página impressa por renderizadores independentes, guarda de versão de renderizador, equivalência entre renderizadores e fidelidade dimensional do papel a 100%.

### Modified Capabilities
Nenhuma. `identity` e `billing` não são tocadas.

## Impact

**Criado**
- `packages/domain/` — KMP (JVM · Android · JS): medição, grade, paginação, `LayoutMap`, primitivas, validação
- `packages/domain/src/commonMain/resources/fonts/` — TTF embarcado, versionado
- `apps/web/` — React + TypeScript + Vite + renderizador `pdf-lib`
- `apps/android/` — módulo Android + renderizador `PdfDocument`/`Canvas`
- `fixtures/` — prova objetiva de referência
- `docs/` — protocolo de medição com régua e resultados observados

**Alterado**
- `settings.gradle.kts`, `gradle/libs.versions.toml` — módulos e plugins novos
- `.github/workflows/ci.yml` — Android SDK, Node, job de paridade

**Dependências novas**: plugin KMP, Android Gradle Plugin, `pdf-lib`, toolchain Node para `apps/web`. Todas já fixadas por §6 e §13 — nenhuma tecnologia fora da arquitetura. Sem Redis, broker, vector DB, Elasticsearch, GraphQL ou microserviços.

**Contrato exposto**: nenhum. Não há endpoint novo; `apps/api` não é tocada nesta fatia.

**Explicitamente NÃO alterado**
- **Região discursiva** (`ESSAY_REGION`), moldura dimensionada pela rubrica (D35), pauta de 8,6 mm, deviants — a folha desta fatia é puramente objetiva.
- **Colunas adaptativas** (D32): duas colunas fixas; blocos que atravessam e a queda para uma coluna ficam para quando houver conteúdo largo real.
- **Densidade em três níveis** (D37), contador de páginas ao vivo e sugestão de economia de papel.
- **Matemática** (LaTeX/MathML → SVG, D33) — é a fatia 1.5, explicitamente depois desta.
- **`ExamPackage`**, hash, imutabilidade e persistência do layout (`exam_layout`) — fatia 2.
- **Captura, ArUco em campo, homografia, QR e OMR** — fatia 3. Esta fatia produz a geometria que a 3 vai ler; não lê nada.
- **Dados impressos do aluno**, `exam_assignment`, variantes e folha avulsa (D26, D44) — fatia 7. A fixture não tem aluno.
- **Detector de deriva** (D38), banco de itens, currículo BNCC, autoria, IA e qualquer prompt.
- Tabelas de banco: nenhuma migration nesta fatia.
