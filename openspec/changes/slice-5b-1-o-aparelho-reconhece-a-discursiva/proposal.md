## Why

Desde a `slice-5a-regiao-discursiva`, uma prova pode ter regiões discursivas, e o aparelho não sabe
lidar com isso. Por leitura do código, e sem medição (correção P7 da linha `5b` do §16, PR #68), são
dois defeitos em sequência:
- **O aplicativo cai ao abrir a câmera.** `ScanActivity.kt:218` escolhe a região com
  `map.regions.single()`, e a prova com discursiva tem uma região por questão discursiva, além do
  gabarito.
- **Se não caísse, toda captura falharia.** `RegionDetector.declaredMarkersOf` conta os ArUcos da
  **página** inteira, e não os da região. Na página 0 da fixture discursiva são 8, contra os 4
  esperados.

Esta é a primeira das duas mudanças em que o mantenedor dividiu a 5b. **Por que agora:** é ela que
paga a linha `5b` do §16 ("a região discursiva ainda não passou pelo aparelho nem pelo papel"), e a
guarda do registro de dívida reprova essa linha no dia em que a 5c abrir.

## What Changes

- **O aparelho deixa de cair.** A sessão de escaneamento deixa de receber uma região escolhida de
  antemão. Os marcadores são detectados uma vez por quadro, e **cada região cujos marcadores
  declarados aparecem todos é lida** (§8: "detecta ArUcos → identifica região pelos IDs → homografia
  → QR na ROI"). O gabarito declara quatro; a região discursiva declara dois, na diagonal
  (ADR-0018, `slice-5b-0-a-regiao-discursiva-compacta`). Região vista pela metade não é lida, e não é
  erro.
- **Os marcadores esperados de uma região são os dela**, e não os da página. Marcadores de outra
  região no mesmo quadro não atrapalham.
- **O gabarito é lido numa página que tem outras regiões.** A região 0 continua passando por
  retificação, QR, OMR e interpretação, como hoje.
- **A região discursiva é reconhecida, e não medida.** Ela é retificada, e o QR é lido e conferido
  contra os marcadores, como em qualquer região. Não há bolha a medir, e ainda não há recorte, que é
  da 5b-2.
- **Diante de prova com discursiva, a sessão reconhece e explica, e não apura.** Ela diz de qual aluno
  é a folha e quais regiões reconheceu, e diz explicitamente que a correção de prova com discursiva
  ainda não está disponível neste aparelho. **Nada é apurado, nada é gravado, nada vai para a fila de
  envio.** É o "motivo certo" que a linha `5b` pede, no lugar da queda.
- **A prova só objetiva não muda de comportamento.** O caminho de hoje continua o mesmo, e o corpus
  da 3b continua sendo lido igual.
- **A folha discursiva passa pelo papel.** O mantenedor imprime a folha de um aluno da fixture
  discursiva, marca respostas conhecidas nas objetivas, escreve nas molduras e fotografa as duas
  páginas. As fotos entram como fixtures, e um teste instrumentado as lê pelo pipeline de produção.

### Linhas do §16 que esta mudança alcança (P27)

A guarda passa a dizer "fatia corrente: 5b", e quatro linhas vencem nesta fatia:
- **`A região discursiva ainda não passou pelo aparelho nem pelo papel` (`5b`): esta mudança a
  paga**, e o archive a marca como `paga`, com a evidência (a queda consertada, o motivo certo, e as
  fotos lidas pelo pipeline);
- `Acurácia em manuscrito` (`5`), `Modo degradado (§10) não existe` (`5`) e `O limiar do OMR foi
  apurado sobre um aparelho e uma impressora` (`5`) são alcançadas e **não pagas**. Elas seguem em
  dia até a 6 abrir, e cada uma tem veículo na fatia 5.

Os eventos `migration-da-5-em-producao` e `implantar-api-da-5a` **não** são alcançados: esta mudança
não tem migration nem implanta nada.

**As fotos novas entram num repositório público.** Antes do commit, a tarefa confere que o EXIF não
traz coordenada de GPS: as três fotos da 3b tinham bloco de GPS, com latitude e longitude zeradas.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `capture-omr`: os marcadores esperados passam a ser os da região, e não os da página. A captura
  identifica as regiões presentes no quadro pelos marcadores, e a região discursiva é reconhecida, e
  não medida.
- `scan-session`: diante de prova com discursiva, a sessão reconhece as regiões e explica que a
  correção ainda não está disponível, sem apurar nem gravar nada.

## Impact

- **`apps/android` (produção):**
  - `vision/RegionDetector.kt`: marcadores por região, e a detecção de marcadores feita uma vez por
    quadro;
  - `vision/SheetReader.kt` e `vision/FrameOutcome.kt`: o resultado passa a ser por região;
  - `vision/RegionQrReader.kt`: o `region_idx` do QR conferido contra os `marker_ids` que o mapa
    declara para a região (acrescentado ao aplicar, em 2026-09-26, decisão 1 do `design.md`);
  - `scan/CameraFrameAnalyzer.kt` e `scan/ScanActivity.kt`: sem região pré-escolhida, e sem o
    `.single()`;
  - `scan/ScanSession.kt`, `scan/ScanState.kt` e `scan/ScanScreen.kt`: o estado novo e a mensagem.
- **`apps/android` (teste):**
  - `ScanSessionTest`, na JVM;
  - um teste instrumentado que abre a câmera com a prova com discursiva;
  - um teste instrumentado que lê as fotos da folha discursiva impressa;
  - `CorpusInstrumentedTest` continua passando.
- **`fixtures/`:** as fotos da folha discursiva impressa, e um arquivo com as marcações que o
  mantenedor fez, anotadas antes de fotografar.
- **`docs/architecture/ARQUITETURA-FINAL-v3.md` §16:** a linha `5b` marcada como `paga`, no archive.
- **`docs/cobertura-slice-5b-1-o-aparelho-reconhece-a-discursiva.md`:** novo.
- **Ambiente:** o emulador `platos-atd34` para as suítes instrumentadas, e o celular e a impressora do
  mantenedor para as fotos.

### O que NÃO será alterado

- **O domínio (`packages/domain`).** Nem layout, nem pacote, nem `ObjectiveScoring`, que continua
  recusando prova com discursiva, como a 8.1 da 5a fixou. A sessão não chega a chamá-lo para ela.
- **A fila de envio e o servidor.** Nenhum resultado de prova com discursiva é criado. A retenção da
  nota objetiva (decisão 1a do mantenedor) é da **5b-2**.
- **O recorte da resposta, o caderno do aluno e a completude**, que são da 5b-2, e o **deviant**, que
  é da 5e.
- **O gate de pré-voo (`device-session`).** A prova com discursiva continua abrindo a câmera, e é
  isso que exercita o `RegionDetector`.
- **`min_renderer_version`, o limiar do OMR e nenhuma tolerância.**
- **Nenhuma migration**, nem do Supabase nem do Room.
