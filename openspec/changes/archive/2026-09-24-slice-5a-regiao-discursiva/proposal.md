## Why

A fatia 5 do §15 é "regiões discursivas · completude · deviants · correção manual · corpus de
medição", e tudo nela depende de uma coisa que ainda não existe: **uma questão discursiva publicada,
com uma região que a captura consiga achar**. Hoje a discursiva é recusada na entrada
(`ExamDefinition.requireSupported`, `ExamDefinition.kt:158`), e o pacote tem forma para exatamente um
QR por folha (`AssignmentQr`, cuja KDoc já dizia que "QR repetido por região é D23, e quando ele chegar
muda as duas specs juntas").

Esta é a primeira mudança da fatia, e é **contrato antes de consumidor** (regra 1 do `CLAUDE.md`). A
5b (captura e completude), a 5c (correção manual) e o corpus consomem o que ela publica. **Por que
agora:** é a janela barata de reabrir o `content_hash`. Estamos antes do lançamento, e nenhum APK foi
entregue. Depois da primeira turma real, mudar o contrato do pacote passa a ser retrofit sobre
artefato imutável já distribuído (ADR-0014, "a consequência aceita").

## What Changes

- **A definição de prova passa a aceitar questão discursiva**, com uma rubrica analítica: critérios,
  descritores, pontos e `expected_lines` por critério (§5, §7, §11 `item_rubric_criterion`).
  Continuam recusados, com erro identificável:
  - discursiva sem rubrica, ou com alternativas;
  - objetiva com rubrica;
  - rubrica cujos pontos não somam os da questão;
  - moldura maior que uma coluna. O §7 diz "nunca maior que uma página", e nesta mudança o bloco
    mora numa coluna (decisão 6 do design). Nesse caso a questão vira itens (a), (b), (c), o que é
    autoria e não layout;
  - mais de 24 discursivas (`DICT_5X5_100`, §8);
  - prova **só** discursiva;
  - imagem de enunciado.
- **O Layout Engine emite uma `ESSAY_REGION` por discursiva** (§8):
  - quatro ArUcos `{4k…4k+3}`, um QR dentro do quadrilátero e a área de resposta em coordenadas
    normalizadas;
  - pauta de 8,6 mm, e a altura da moldura sai da soma dos `expected_lines` (D35);
  - o enunciado fica **fora** da moldura, e enunciado e moldura formam um bloco indivisível;
  - o gabarito da região 0 passa a ter bolhas só das objetivas.
- **BREAKING — o pacote muda de forma, e o `content_hash` de todo pacote muda com ela:**
  - o item declara `kind`, e a discursiva leva `rubric` e `answer_capture_mode`;
  - a região declara `question_id`, `answer_area` e o identificador da primitiva do QR dela;
  - a atribuição passa de `qr` a `qrs`, um QR por região (D23);
  - `fully_offline_gradable` sai `false` quando há discursiva, o que já era calculado e passa a ser
    exercitado.

  Pacotes já publicados mantêm os bytes que têm. O aplicativo atualizado passa a **recusá-los** pela
  camada (b), e a saída é a do ADR-0009: publicar prova nova. É o mesmo desenho da ETAPA 3
  (`params-hash-no-pacote-publicado`, decisões 1, 3 e 9).
- **`min_renderer_version` continua em `1`, por decisão do mantenedor nesta sessão.** A região
  discursiva se desenha com primitivas que já existem (`rect`, `aruco`, `qr`, `text`). A previsão do
  plano de correção ("é aí que `min_renderer_version` sobe pela primeira vez") não se confirmou na
  leitura do código, e o `design.md` diz por quê. Se a pauta exigir uma primitiva nova, a mudança
  **para** e a decisão volta ao mantenedor (decisão 4 do design).
- **O espelho web de `folhaDaAtribuicao` ganha conferência cruzada** (P28). Ele passa a trocar um QR
  por região, e hoje nada o confere: a KDoc dele diz que a paridade pega a divergência, mas a paridade
  compara centroides, e o CI não renderiza folha de aluno. Conferido por leitura (`grep` no `ci.yml`).
- **Fixture nova `prova-discursiva`** com layout, pacote e folha de aluno gravados pelo
  `GoldenWriterTest`, e paridade, fidelidade e tinta nos dois renderizadores, no CI. As goldens de
  `prova-referencia` são regravadas, e **P23 fecha na mesma sessão**.
- **O aparelho não muda, por decisão do mantenedor nesta sessão.** Uma prova com discursiva continua
  recusada na captura, pelo motivo "itens lidos divergem da variante". Não sai nota errada, mas o
  motivo engana. A recusa ganha um teste de domínio que a fixa. O motivo certo, e a medição da folha
  discursiva em papel, entram no §16 com fatia-limite `5b` (P27).

### Linhas do §16 que esta mudança alcança (P27)

A guarda passa a dizer "fatia corrente: 5a", e três linhas **vencem nesta fatia**:
- `Acurácia em manuscrito` (`5`);
- `Modo degradado (§10) não existe` (`5`);
- `O limiar do OMR foi apurado sobre um aparelho e uma impressora` (`5`).

**Esta mudança não paga nenhuma delas.** As três continuam em dia até a fatia 6 abrir, e são da 5d,
da 5e e do ADR de critério do corpus que o ADR-0007 exige. O evento `migration-da-5-em-producao`
**não** é alcançado, porque esta mudança não traz migration: o pacote é texto canônico numa coluna que
já existe (ADR-0008).

Esta mudança **acrescenta** uma linha, com token `5b`: a discursiva ainda não passou pelo aparelho
nem pelo papel.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `layout-engine`:
  - a entrada aceita discursiva com rubrica;
  - a folha passa a ter uma região discursiva por questão, com a sua moldura como bloco indivisível;
  - todo QR de região carrega o token da atribuição;
  - folhas da mesma prova diferem só nos QRs;
  - a validação cobre a região discursiva.
- `exam-package`:
  - o item discursivo publica a sua rubrica;
  - a atribuição carrega um QR por região;
  - a coerência passa a ligar item discursivo a região e rubrica a pontuação.

## Impact

- **`packages/domain`:**
  - `ExamDefinition`, com a rubrica e a recusa estreitada;
  - `LayoutEngine` e `QuestionBlocks`, com o bloco discursivo e a região;
  - `LayoutMap`, com os campos de `ScannableRegion`;
  - `LayoutMapValidation`;
  - `ExamPackage`, com `PackageItem`, `AssignmentQr` → `qrs` e `folhaDaAtribuicao`;
  - `Publish` e `ExamPackageValidation`;
  - os testes e o `GoldenWriterTest`.
- **`apps/web`:**
  - `scripts/examPackage.ts`, o espelho de `folhaDaAtribuicao`;
  - um teste novo que o compara à folha de aluno gravada pelo Kotlin;
  - `render-fixture.ts`, para renderizar a fixture nova.
- **`apps/android`:** a suíte instrumentada do renderizador passa a desenhar a fixture nova. Nenhum
  código de produção do aparelho muda.
- **`apps/api`:** um teste de publicação da fixture discursiva sobre o Postgres de teste, e os literais
  de pacote nos testes.
- **`fixtures/`:**
  - `prova-discursiva.json`, novo, com layout, pacote e folha de aluno;
  - regravação das goldens de `prova-referencia` e da folha de teste;
  - uma cópia congelada do pacote do contrato atual, feita antes da regravação.
- **`.github/workflows/ci.yml`:** a fixture nova no job `paridade`.
- **`tools/parity`:** `fidelidade.mjs` e `compare.mjs` precisam ler mais de uma região e mais de uma
  página, se ainda não leem. É **suposto** até a tarefa conferir.
- **`docs/architecture/ARQUITETURA-FINAL-v3.md` §16:** uma linha nova (`5b`). É atualização de
  registro, e não abre ADR.
- **`docs/cobertura-slice-5a-regiao-discursiva.md`:** novo.
- **Produção:** as provas de conferência já publicadas passam a ser recusadas pelo aplicativo
  atualizado (ADR-0009). Nenhuma migration.

### O que NÃO será alterado

- **A captura, a completude, a correção e o aparelho.** Nada em `capture-omr`, `scan-session`,
  `scoring`, `result-sync` ou `device-session` muda de requisito. São da 5b e da 5c.
- **`min_renderer_version` e as três constantes que `renderizador.mjs` confere.**
- **As primitivas de desenho.** Nenhuma nova, e nenhum campo novo em nenhuma delas.
- **O paginador não aprende bloco que atravessa colunas** (§7, "blocos largos atravessam"). A
  região discursiva mora numa coluna, e a decisão 6 do design diz o custo.
- **Itens (a), (b), (c) não são gerados.** Moldura maior que uma página é recusada.
- **Nenhum ADR novo.** D23, D35, §5, §7, §8 e §11 já decidem, e esta mudança os implementa.
- **`meta.exam_id` não é renomeado.** A decisão 5 da ETAPA 3 continua valendo.
- **Nenhuma migration**, e nenhuma tabela nova.
