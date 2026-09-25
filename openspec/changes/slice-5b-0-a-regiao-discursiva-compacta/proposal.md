## Why

Em 2026-09-25, o mantenedor reprovou a folha discursiva **antes do papel**. A tarefa 4.1 da
`slice-5b-1-o-aparelho-reconhece-a-discursiva` mandava imprimi-la, e ela foi pausada. O motivo:
- marcadores e QR ocupam boa parte de cada questão;
- uma prova com várias discursivas vira mais páginas por aluno, e isso afasta o professor da
  ferramenta.

A revisão virou quatro ADRs. Esta mudança leva ao motor de layout os três que desenham a região:
- **ADR-0016:** a pauta é do aluno, e não da câmera. Tem 7 mm e fica em cinza claro, do lado
  decorativo do ADR-0010.
- **ADR-0017:** o professor declara as linhas e a largura de cada discursiva, sem valor padrão.
- **ADR-0018:** a região tem dois ArUcos na diagonal, e o QR fica no terceiro canto.

**Por que agora:** nenhuma prova com discursiva está em produção. As duas publicadas são objetivas,
*lido no banco em 2026-09-24* (`docs/cobertura-slice-5a-regiao-discursiva.md`). A 4.1 espera esta
geometria para fotografar a folha que fica. Depois da primeira prova publicada, a geometria dela
congela no pacote (ADR-0009).

## What Changes

- **A definição da discursiva declara `answer_lines` e `answer_width`**, e nenhum dos dois tem
  valor padrão (ADR-0017).
  - `answer_width` aceita `column` e `page`. `page` é **recusada com motivo** até a mudança de
    paginação em faixas existir, e nunca é desenhada como coluna em silêncio.
  - A objetiva que declara qualquer um dos dois é recusada.
  - `expected_lines` continua na rubrica, e deixa de dimensionar a moldura.
- **A área de resposta tem `answer_lines × 7 mm`** (ADR-0016), e não mais `Σ expected_lines ×
  8,6 mm`.
- **A região discursiva passa ao desenho do ADR-0018:**
  - ArUco `4k` no canto superior esquerdo, com 11,2 mm (7 módulos de 1,6 mm);
  - QR de 14 mm no canto superior direito, na mesma faixa;
  - moldura logo abaixo, na largura inteira da região;
  - ArUco `4k+3` no canto inferior direito.

  As coordenadas da região passam a ser normalizadas sobre o retângulo externo dos dois
  marcadores. A área de recorte vai da faixa de cima até a de baixo, com folga fora da moldura.
- **A pauta vira uma primitiva nova, `line`, com tom.** Ela é desenhada em cinza, abaixo do teto
  decorativo da região. **BREAKING, só para mapa com discursiva:** esse mapa passa a exigir
  renderizador versão 2. Um mapa sem linha continua exigindo 1 e sai **idêntico byte a byte** ao
  de hoje.
- **Os dois renderizadores desenham `line`.** A paridade passa a medir a linha pela tinta
  esperada, que é a área declarada vezes o tom declarado. Assim, uma linha omitida ou desenhada em
  preto reprova.
- **O tom e a espessura da pauta saem do papel** (ADR-0016). O critério é registrado antes, e o
  mantenedor imprime a folha discursiva nova numa impressora e confere.

### Linhas do §16 que esta mudança alcança (P27)

A guarda diz "fatia corrente: 5b". Quatro linhas vencem nela, e esta mudança **não paga nenhuma**:
- `A região discursiva ainda não passou pelo aparelho nem pelo papel` (`5b`) é paga pela 5b-1
  retomada. Esta mudança produz a geometria que a 4.1 fotografa.
- `Acurácia em manuscrito`, `Modo degradado (§10) não existe` e `O limiar do OMR foi apurado sobre
  um aparelho e uma impressora` (`5`) seguem em dia até a 6 abrir.

**Uma linha nova entra por esta mudança.** A folha de teste de impressão deixa de aprovar o que a
prova imprime:
- ela aprova marcadores de 14 mm, e a discursiva passa a ter 11,2 mm;
- ela não tem pauta cinza.

A folha de teste não é mexida aqui. Na `main`, o `RegionDetector` ainda conta os ArUcos da página
inteira (o filtro por região é a tarefa 1.1 da 5b-1, não mergeada). Um marcador a mais na página
da folha de teste derrubaria os três testes instrumentados que a leem. A linha vence em `5b`, e o
veículo é uma mudança posterior ao filtro.

Os eventos `migration-da-5-em-producao` e `implantar-api-da-5a` **não** são alcançados.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `layout-engine`: nova geometria da região discursiva (dois marcadores, QR no canto, retângulo de
  referência, recorte com folga). Também muda:
  - a definição aceita e recusa `answer_lines` e `answer_width`;
  - a altura da área de resposta;
  - a pauta como linha decorativa;
  - a versão mínima de renderizador calculada por mapa;
  - a validação dos marcadores por tipo de região.
- `print`: o mínimo de lado do marcador sob reescala passa a depender do tipo de região, e a linha
  com tom passa a ser desenhada e medida igual nos dois renderizadores.

## Impact

- **`packages/domain`:**
  - `exam/ExamDefinition.kt`: campos e recusas da discursiva;
  - `layout/EssayGeometry.kt`, `layout/LayoutEngine.kt` e `layout/QuestionBlocks.kt`: a região
    nova;
  - `layout/LayoutMap.kt`: `DrawLine` e a versão mínima por mapa;
  - `layout/LayoutMapValidation.kt`: marcadores por tipo e tom da pauta.
- **`apps/web`:** `src/layoutMap.ts` e `src/renderer.ts`, com `line` e `RENDERER_VERSION = 2`.
- **`apps/android`:** `render/LayoutMapRenderer.kt` e `render/RendererContract.kt`, com `line` e
  versão 2.
- **`tools/parity/compare.mjs`:** a medição da linha com tom.
- **`fixtures/`:** `prova-discursiva.json` declara as linhas e a largura. São regravados:
  - `prova-discursiva.layout.json`;
  - `prova-discursiva.aluno.layout.json`;
  - `prova-discursiva.package.json`.

  Paridade e fidelidade fecham na mesma sessão (P23).
- **`docs/architecture/ARQUITETURA-FINAL-v3.md` §16:** a linha nova da folha de teste.
- **Ambiente:** o emulador `platos-atd34` para o PDF do Android, e a impressora do mantenedor para
  a conferência da pauta.

### O que NÃO será alterado

- **A prova só objetiva.** `prova-referencia.*`, `prova-2.*` e `folha-de-teste.layout.json` saem
  idênticos byte a byte, e isso é verificado.
- **O gabarito e o cabeçalho**, cuja mudança vem depois, nem a folha de teste de impressão (pela
  razão acima).
- **A paginação.** Faixas, largura de página e redistribuição (ADR-0019) são de uma mudança
  própria. As regiões discursivas continuam indexadas pela ordem da definição.
- **A captura.** `RegionDetector` e sessão ficam intocados, e a leitura da região de dois ArUcos é
  da 5b-1 atualizada.
- **O pacote:** nenhum campo novo em `PackageItem`. A geometria da região já carrega o resultado
  das duas escolhas.
- **`layout_engine_version`, que fica em 1.** Nenhum consumidor decide nada por ele: é copiado
  para o pacote e validado como `>= 1`, *conferido por `grep`*. A 5a também não o subiu ao
  acrescentar a região discursiva.
- Nenhuma migration, e nenhum limiar de OMR nem tolerância de paridade é afrouxado.
