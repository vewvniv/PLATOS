## Context

O motivo está na proposta (Why). Aqui fica só o estado atual que molda o desenho, com o tipo de cada
afirmação (P6).

- **A região é escolhida antes do quadro.** `ScanActivity.kt:218` constrói o `CameraFrameAnalyzer`
  com `region = map.regions.single()`. Com a prova com discursiva, o `.single()` lança exceção ao abrir
  a câmera. *Conferido por leitura, e não medido* (correção P7 da linha `5b`, PR #68). A outra
  ocorrência, `examPackage.layout.values.single()` (`:131`), é sobre **variantes**, que continuam sendo
  uma só até a fatia 7, e não é afetada.
- **Os marcadores esperados são os da página.** `RegionDetector.declaredMarkersOf` devolve todo
  `DrawAruco` da página da região, e `detect` exige exatamente 4. Na página 0 da fixture discursiva são
  6 (gabarito, 4, mais `d1`, 2), e não 8: a `slice-5b-0-a-regiao-discursiva-compacta` (arquivada)
  reduziu a região discursiva de quatro marcadores para dois, na diagonal. *Conferido por leitura,
  atualizado após o archive da 5b-0.*
- **A região discursiva agora tem dois marcadores, não quatro, e retângulo de referência pelo canto
  externo.** `4k` no canto superior esquerdo, `4k+3` no inferior direito; `4k+1` e `4k+2` não são
  impressos. O retângulo de referência da região vai do canto externo superior esquerdo do `4k` ao
  inferior direito do `4k+3` — não pelo centro, como no gabarito. A decisão 2 da 5b-0 já antecipa esta
  mudança: "a posição de cada canto de marcador vem do `DrawAruco` (`x`, `y`, `side`)... a 5b-1
  atualizada monta a homografia com esses pontos declarados e normaliza pelo retângulo declarado, sem
  saber se ele passa por centro ou por canto." *Conferido por leitura do design.md arquivado da 5b-0.*
- **Correção, em 2026-09-26 (P7): a 5b-0 não está arquivada.** Os dois itens acima dizem "arquivada" e
  "após o archive", e isso está errado. A `slice-5b-0-a-regiao-discursiva-compacta` está completa e
  mergeada na `main` (PR #70), e continua em `openspec/changes/`, e não em `archive/`. *Conferido por
  `openspec list`, que a mostra "✓ Complete", e por `divida.mjs`, que a lê como ativa.* O que os dois
  itens afirmam sobre a geometria continua certo: foi lido na fixture e no `design.md` dela, que estão
  na `main`.
- **A detecção de marcadores roda dentro de `detect`, uma vez por região pedida.** Hoje só uma região
  é pedida por quadro.
- **A sessão conhece uma forma de resultado:** `FrameOutcome.Read(InterpretedReading)`, que vai para
  `ObjectiveScoring`. Para a prova com discursiva, a apuração recusa, porque o conjunto de itens não
  fecha (fixado na 8.1 da 5a).
- **O QR já é conferido contra os marcadores encontrados** (`RegionQrReader.read(qrCanvas,
  detectedMarkerIds)`), para qualquer região.
- **Não há teste instrumentado que abra uma `Activity`** nesta base: nenhum `ActivityScenario` nem
  `UiDevice` em `apps/android/src/androidTest`. *Conferido por `grep`.*
- **O corpus da 3b já é lido por um teste instrumentado** (`CorpusInstrumentedTest`), a partir de fotos
  em `fixtures/`, com `papel.mjs` como oráculo da cobertura. Duas das nove fotos da 3b não decodificam o
  QR, sem causa medida (linha do limiar no §16).

## Goals / Non-Goals

**Goals:**
- O aparelho abre a câmera numa prova com discursiva, lê o que está no quadro e diz o que não sabe
  fazer ainda, pelo motivo certo, sem cair.
- O caminho `RegionDetector` → QR → OMR passa por uma folha discursiva **impressa e fotografada**, com
  o gabarito numa página que tem outra região.
- A prova só objetiva não muda de comportamento.

*Atualizado em 2026-09-26, por decisão do mantenedor:* o segundo Goal, a folha "impressa e
fotografada", sai desta mudança para a **sessão única de papel antes da fatia 6** (decisões 6 e 7).
Aqui, o caminho `RegionDetector` → QR → OMR passa pela folha **renderizada** do documento, com o
gabarito numa página que tem outra região.

**Non-Goals:**
- Guardar qualquer coisa de uma prova com discursiva: nota, leitura ou imagem. É a 5b-2.
- Caderno do aluno, completude e recorte (5b-2). Deviant (5e).
- Mudar o domínio, o gate de pré-voo, a fila de envio ou o servidor.

## Decisions

### 1. Os marcadores são detectados uma vez por quadro, e as regiões saem deles

`SheetReader` passa a receber o mapa, e não uma região. Por quadro:
1. detecta os ArUcos uma vez;
2. para cada região do mapa cujos `marker_ids` declarados foram **todos** encontrados, retifica e lê.
   `detect` recebe os marcadores já encontrados, em vez de detectar de novo. A contagem exigida vem da
   região, e não é uma constante: quatro para o gabarito, dois para a região discursiva (decisão 1 da
   5b-0);
3. se nenhuma região tem os seus declarados por completo, o quadro é "sem folha", com os IDs
   encontrados e os declarados por região.

- **Por que "todos os declarados", e não "os que casarem melhor":** a homografia precisa de todos os
  pontos que a região declara. Para o gabarito são os centros dos quatro marcadores; para a região
  discursiva são os cantos externos dos dois (decisão 2 da 5b-0: "a posição de cada canto de marcador
  vem do `DrawAruco`... a 5b-1 atualizada monta a homografia com esses pontos declarados e normaliza
  pelo retângulo declarado, sem saber se ele passa por centro ou por canto"). Uma região com menos
  marcadores do que declara daria medição parcial, que a spec proíbe.
- **Região pela metade não é erro.** A folha de uma prova com discursiva tem regiões em mais de uma
  página, e um quadro da página 0 tirado de perto pode pegar metade da moldura de `d1`. Tratar isso
  como recusa faria o gabarito, que está inteiro no quadro, não ser lido por causa de outra região.
- **Alternativa descartada: escolher a região pelo QR.** O §8 fixa a ordem: marcadores, depois região
  pelos IDs, depois homografia, e só então o QR na ROI retificada. Ler o QR antes seria ler sobre a
  foto em perspectiva, que é o que a ordem existe para evitar.

**Atualizado ao aplicar, em 2026-09-26, por decisão do mantenedor.** O texto acima tem dois
problemas:
- **"Os cantos externos dos dois" são dois pontos**, e uma homografia precisa de quatro. O ADR-0018
  §3(a) diz o que se queria dizer: a primeira homografia "sai dos cantos dos dois ArUcos".
- **Ele não diz se o passo (c) do ADR-0018 entra aqui.** O passo (c) é o segundo ajuste, com os
  padrões de posição do QR, de onde sai o recorte. O §4 do ADR faz do resíduo desse ajuste a
  conferência da geometria da região discursiva.

A decisão:
- **A região de dois marcadores é retificada pelos oito cantos deles.** A homografia é ajustada por
  mínimos quadrados, com a posição declarada de cada canto (`DrawAruco`: `x`, `y`, `side`)
  normalizada pelo retângulo declarado da região. O caminho escolhe pela **contagem** de marcadores
  declarada, e não pelo tipo (decisão 2 da 5b-0: "a captura não precisa de regra por tipo"). Quatro
  marcadores seguem o caminho de hoje, dos centros exatos com conferência nos cantos, que não muda
  (ADR-0018 §5).
- **A 5b-1 faz os passos (a) e (b) do ADR-0018, e não o (c).** Não há teto de resíduo novo. O que
  guarda o reconhecimento é o QR: a decodificação, o CRC e o `region_idx` conferido contra os
  marcadores. Uma geometria ruim o bastante para enganar o reconhecimento não deixa o QR ler, e a
  região sai não lida.
  - Um teto de 6 px sobre o resíduo desse ajuste foi **descartado**. No gabarito, o `MAX_REPROJECTION_PX`
    mede cantos que ficam fora do ajuste; aqui os cantos entram nele. O mesmo número passaria a
    significar outra coisa, sem medição que o sustente (P11, P18).
  - **O passo (c) e o resíduo dele vão para a 5b-2**, junto com o recorte, que é quem os consome.
    Até lá, a geometria da região discursiva **não tem conferência além do QR**. Não é mitigado, é
    conhecido (P8). O resíduo do ajuste por mínimos quadrados sai em `reprojectionErrorPx` como
    informação, e nada decide por ele.
- **O `region_idx` do QR é conferido contra os `marker_ids` que o mapa declara para aquela região**,
  e não contra `CaptureGeometry.markerIdsOf`, que dá os quatro IDs da alocação `{4k…4k+3}`. Sem isso,
  a região discursiva, que declara `[4k, 4k+3]`, seria recusada sempre (`RegionQrReader.kt`, fora
  da lista do Impact da proposta).

### 2. Os marcadores esperados de uma região são os dela

`declaredMarkersOf` passa a filtrar pelos `marker_ids` da região. É a correção mínima: a posição
declarada de cada marcador continua vindo do `DrawAruco` do mapa, e a região continua sendo quem diz
quais são os dela.

### 3. O resultado do quadro passa a ser por região

`FrameOutcome` passa a carregar o que aconteceu com **cada** região presente:
- **gabarito lido**: a `InterpretedReading` de hoje;
- **região discursiva reconhecida**: o payload do QR e a questão que o mapa declara para ela;
- **região presente e não lida**: com o motivo que o pipeline produziu, pelo estágio em que parou. A
  distinção de hoje entre "não achei" e "achei e não li" continua valendo (spec de `scan-session`).

A forma exata é da implementação. A regra é que a sessão receba **todas** as regiões do quadro, e não
só uma, para a prova com discursiva poder dizer o que reconheceu.

**Para a prova só objetiva, nada muda de fora.** Ela só tem a região 0, e o resultado dela é o de
hoje: os cenários de `ScanSessionTest` que já existem continuam valendo sem mudar de sentido.

### 4. A sessão decide "prova com discursiva" pelo pacote, e não pelo que viu

A sessão consulta `fully_offline_gradable` do pacote, e não a presença de região discursiva no quadro.

- **Por que pelo pacote:** uma foto só da página 0 de uma prova com discursiva traz só o gabarito. Se a
  decisão viesse do quadro, essa foto seria apurada como prova objetiva, e entregaria nota de uma
  prova que tem parte discursiva. É exatamente o que a D4 proíbe.
- **Por que `fully_offline_gradable`:** é o campo que declara isso, e desde a 5a a coerência do pacote
  recusa esse campo em desacordo com os itens. Uma segunda regra ("tem item discursivo?") seria um
  segundo registro do mesmo fato (P28).

Para essa prova, a sessão entra num estado novo, que diz:
- **de quem é a folha**, pelo token do payload;
- **o que foi reconhecido:** "gabarito lido", ou o motivo de não ter lido, e cada região discursiva
  pela questão;
- **a frase fixa:** a correção de prova com discursiva ainda não está disponível neste aparelho, e
  nada foi guardado.

`onFrame` **nunca** devolve apuração para essa prova. O estado segue a regra de hoje: um resultado
novo substitui o anterior por inteiro, e a folha que sai do quadro não apaga o que está na tela.

**A leitura do gabarito acontece, e é descartada.** Ela não vira nota nem é guardada. Ela existe para
a folha passar pelo caminho inteiro, e é o que a 5b-2 vai reter.

### 5. A queda sai por construção, e é vista falhar num teste que monta o analisador

Sem região pré-escolhida, o `CameraFrameAnalyzer` recebe o mapa, e não há mais `.single()` sobre as
regiões. A prova de que a queda sumiu é um teste que monta o analisador **como a `ScanActivity`
monta**, com o mapa da fixture discursiva, e afirma que nada lança exceção. A montagem vai para uma
função que a `Activity` chama e o teste também chama, para o teste não reescrever a montagem.
**Ver falhar:** com o `.single()` restaurado nessa função, o teste cai.

- **Por que não um teste que abre a `Activity`:** a base não tem essa infraestrutura. Introduzi-la aqui
  seria tecnologia de teste nova para um cenário, e a câmera do emulador `aosp_atd` não está garantida.
- **O que fica sem verificação automática** (P8): a tela desenhada pelo Compose com o estado novo. Ela
  é conferida no aparelho do mantenedor (decisão 7), ou fica escrita como lacuna.

### 6. A folha discursiva impressa, e o oráculo é o que o mantenedor marcou

**O mantenedor imprime a folha de `tok-a`** (`build/parity/discursiva-aluno-web.pdf`), e em seguida:
- marca respostas nas quatro objetivas e escreve qualquer coisa nas duas molduras. Nada de dado real
  de aluno: a letra é dele;
- **antes de fotografar**, anota as respostas marcadas em `fixtures/corpus-5b-marcacoes.json`;
- fotografa com o celular a primeira folha impressa (índice 0 no mapa: gabarito e `d1`) e a segunda
  (índice 1: `d2`), cada uma de frente e em ângulo.

**O oráculo é a marcação declarada, e não o próprio pipeline** (P4). O teste instrumentado lê cada
foto pelo caminho de produção e afirma:
- as regiões reconhecidas: `{0, 1}` na primeira folha e `{2}` na segunda;
- as respostas do gabarito iguais às anotadas;
- os QRs das regiões discursivas com `tok-a` e o índice certo.

- **Por que não `papel.mjs`:** a pergunta desta mudança é **reconhecer**, e não a cobertura fina de
  bolha, que o corpus da 3b já mede. Estender `papel.mjs` para páginas com várias regiões seria
  instrumento novo sem a pergunta que ele responde.
- **Regra de parada** (P11, P12): se uma foto não for reconhecida, **ela não é trocada por outra que
  passe**. Ela fica no conjunto, com o motivo do pipeline registrado, e a mudança para. A 3b já tem
  duas fotos cujo QR não decodifica sem causa medida, e é a linha do limiar no §16 que junta esse tipo
  de achado.
- **EXIF:** o repositório é público. Antes do commit, as fotos passam pelo leitor de EXIF usado na
  abertura da fatia 5, e nenhuma pode ter coordenada de GPS. Se tiver, a coordenada é removida antes de
  entrar, e o registro diz isso.

**Movida em 2026-09-26, por decisão do mantenedor, para a sessão única de papel antes da fatia 6.** O
mantenedor não tem impressora. Cada impressão custa um deslocamento, e impressões picadas atrasaram o
desenvolvimento em dias. A conferência em papel passa a ser a **etapa final**, numa ida só, com o
protocolo escrito antes. O texto acima fica como rascunho desse protocolo: o oráculo, a regra de
parada e o EXIF.

A sessão acrescenta uma regra que o texto acima não tinha: **toda regra de parada que possa mandar
trocar algo já imprime a alternativa na mesma ida.** O exemplo é o marcador discursivo de 11,2 mm, que
volta a 14 mm se não for detectado (decisão 1 da 5b-0). A folha com 14 mm vai junto, para a queda não
exigir outra viagem. Nesta mudança, a linha `5b` do §16 é **reagendada para `6`**, e não paga (P27).
Adia-se a conferência, e não a proteção: o código que reconhece a região está aqui, verificado sobre
o documento renderizado.

### 7. O aparelho do mantenedor, de ponta a ponta, se couber

A prova de ponta a ponta tem dois passos:
- publicar a fixture discursiva na organização de conferência;
- puxar a prova no celular com o build desta mudança e escanear a folha impressa.

É a evidência mais forte da tela da decisão 4. **Ela não exige implantar a API**: a rota do pacote
entrega os bytes gravados, e nada é enviado. Por isso ela não dispara o evento
`implantar-api-da-5a`. A tarefa é do mantenedor. Se não couber na sessão, a tela fica escrita como não
conferida, e a linha `5b` se paga pelas decisões 5 e 6.

**Movida em 2026-09-26 com a decisão 6, para a sessão única de papel.** Lá a folha impressa já existe,
e o ponta a ponta no celular é feito na mesma ida. A frase "a linha `5b` se paga pelas decisões 5 e
6" deixa de valer: a decisão 6 saiu desta mudança, e a linha é reagendada para `6`. Até a sessão, a
tela fica como lacuna (P8).

### 8. A regra de parada vale sobre toda mutação

É a mesma da 5a (decisão 13): se o conjunto de cenários que caem divergir do previsto, **pare**,
escreva o real ao lado do previsto e diga o que significa, lendo a mensagem, e não a contagem.

## Risks / Trade-offs

- **[A foto real pode não ser reconhecida]** → a regra de parada da decisão 6. É o risco que a linha
  `5b` existe para expor, e expô-lo é resultado, e não falha da mudança. *Movido em 2026-09-26 para a
  sessão única de papel, com a decisão 6.*
- **[Nenhuma foto da folha discursiva lida antes da fatia 6]** → a linha `5b` do §16 é reagendada para
  `6`, com o motivo, e a guarda a reprova quando a 6 abrir sem a sessão. **Não é mitigado, é
  conhecido** (P8): o marcador de 11,2 mm e o QR lido com dois ArUcos continuam sem medição em papel.
- **[A leitura do gabarito de prova com discursiva é descartada]** → é trabalho perdido para quem
  escaneia agora. É aceito porque nenhuma prova com discursiva está em produção, e a 5b-2 passa a
  retê-lo.
- **[A tela nova sem teste automático]** → decisão 5. **Não é mitigado, é conhecido** (P8), até a
  conferência no aparelho.
- **[A geometria da região discursiva sem conferência além do QR]** → decisão 1, atualização de
  2026-09-26. O segundo ajuste do ADR-0018 é da 5b-2. **Não é mitigado, é conhecido** (P8).
- **[`FrameOutcome` por região mexe no contrato entre `vision/` e `scan/`]** → os dois lados são do
  mesmo módulo, e os cenários antigos de `ScanSessionTest` são a guarda de que a prova objetiva não
  mudou de fora.

## Migration Plan

Não há migração: nenhum dado persistido muda, nem no aparelho nem no servidor. O APK novo substitui o
anterior. Voltar atrás é reverter os commits, e a prova com discursiva volta a derrubar o aplicativo.

## Open Questions

Nenhuma que altere as specs, a abordagem ou as tarefas.
