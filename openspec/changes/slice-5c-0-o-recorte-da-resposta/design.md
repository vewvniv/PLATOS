## Context

O motivo está na proposta (Why). Aqui fica o estado atual que molda o desenho, com o tipo de cada
afirmação (P6): **lido** (no código, hoje), **conferido** (por ferramenta, com o instrumento) ou
**suposto**.

- **A região discursiva é retificada só por mínimos quadrados sobre os 8 cantos dos dois
  marcadores.** `RegionDetector.detect` escolhe o caminho pela contagem de marcadores
  (`pelosCentros = ordered.size == 4`); com dois, usa `homographyByCorners` e **não tem teto de erro**
  ("nenhum teto nesse caminho", comentário do próprio arquivo). *Lido.*
- **O `Rectified` não expõe a homografia.** Traz a região, o canvas do QR com sangria, o erro e os
  ids. O segundo ajuste precisa da primeira homografia para levar os cantos do QR de volta à imagem.
  *Lido.*
- **`QrOutcome.Read` carrega só o payload.** `RegionQrReader.read` recebe o canvas do QR, com ROI
  `Rect(0, 0, w, h)` — o canvas inteiro, sem deslocamento —, e descarta tudo do resultado do
  decodificador, menos o texto. *Lido.*
- **O decodificador expõe os cantos do símbolo.** `zxingcpp` 2.3.0: `Result.getPosition()` devolve
  `Position(topLeft, topRight, bottomRight, bottomLeft, orientation)`, com `android.graphics.Point`
  — **inteiros**. *Conferido por `javap` sobre o `android-2.3.0-api.jar`, 2026-09-30.*
- **O que esses quatro pontos significam no mapa é suposto.** A hipótese é que `topLeft`, `topRight` e
  `bottomLeft` são os cantos externos dos três padrões de posição (os três quadrados do QR), e que
  `bottomRight` é estimado — é por isso que o ADR-0018 fala em **três** quadrados. *Suposto até a
  tarefa 2.2 medi-lo sobre o QR renderizado, contra o retângulo que o mapa declara.*
  > **Resultado da tarefa 2.2, 2026-09-30 (P7: a hipótese acima fica).** O critério fixado antes da
  > execução (`topLeft`, `topRight`, `bottomLeft` a ≤ 0,5 mm do canto que o mapa declara, no canvas
  > do QR) **reprovou**, sobre o documento renderizado de frente: região 1 — 0,391 / 0,390 / 0,320 mm
  > (passa); região 2 — 0,472 / **0,610** / **0,532** mm (reprova em `topRight` e `bottomLeft`);
  > `bottomRight` (só registrado) 0,391 e 0,532. **Segundo o que a tarefa manda, a hipótese cai e o
  > trabalho para aqui.** Um diagnóstico posterior (não é critério) mostrou que o desvio não está no
  > decodificador: `topRight`/`topLeft` do ZXing caem a ≤ 1 px (0,1 mm) da borda escura real do
  > símbolo no canvas (`TR=(183,37)` contra topo escuro em 37 e borda direita em 182), enquanto o
  > **símbolo inteiro está deslocado 0,3–0,6 mm em relação ao mapa dentro do canvas** (topo em 36–37
  > contra 40 esperados; direita em 182–184 contra 180). O canvas vem da primeira homografia, só com
  > os dois marcadores da diagonal, que extrapola no canto do QR — o risco (a) do ADR-0018, já
  > presente no documento de frente. **O que isto implica, sem decidir:** (1) o critério mede duas
  > coisas juntas, o significado de `position` e o erro da primeira homografia; (2) a decisão 2 não
  > depende dessa segunda, porque os pontos do QR voltam à imagem pela **inversa da mesma homografia**
  > que fez o canvas (o erro dela se cancela na ida e volta) e só o significado de `position` importa;
  > (3) mudar o critério depois de ver o resultado é P11, e exige decisão do mantenedor.
- **A `answer_area` já é contrato.** `ScannableRegion.answerArea` existe, o motor a emite (largura
  inteira da região, da base do QR até a zona de silêncio do marcador de baixo) e a validação a
  confere contra `[0,1]` e contra o QR. Com a moldura, ela deixa 2 mm de folga acima e 2 mm abaixo
  (`EssayGeometry.TOP_BAND`, `DESCENDER_CLEARANCE`); dos lados a moldura encosta na borda. *Lido em
  `LayoutEngine.emitEssayRegion` e `EssayGeometry`.*
- **O que cerca a região.** Duas colunas de A4 com `gutter` de 6 mm e `marginSide` de 15 mm
  (`LayoutProfile.DEFAULT`). O enunciado da questão fica **acima** do retângulo de referência, e o
  cabeçalho, no topo da página 0. Nada disso está dentro da `answer_area` hoje. *Lido.*
- **A folha renderizada já é fixture.** `FolhaDiscursivaRenderizada` desenha a folha de `tok-a` com o
  `LayoutMapRenderer` de produção num PDF, rasteriza com `PdfRenderer` e devolve uma `Mat` cinza; o
  `RectifierInstrumentedTest` tem um `skew` para pôr a página em perspectiva. *Lido.*
- **`PaperWhite` é "o único lugar do OMR que decide o que é branco"**, e mede o branco por bloco de
  128 px, percentil 90. `InkBudget.decorativeToneMax` é 500‰, e a pauta é 300‰ (ADR-0010, ADR-0016).
  *Lido.*
- **O `divida.mjs` não lê a tabela "Aberto" do §16.** `TITULO_TABELA = '### Ponto de não-retorno'`.
  As duas linhas de fatia-limite 5 estão na tabela seguinte. *Lido, e a saída de hoje confirma: não
  lista nenhuma das duas.*

## Goals / Non-Goals

**Goals:**
- A geometria do recorte é conferida pelo aparelho, contra o documento renderizado e em perspectiva,
  **antes** de existir tela ou armazenamento em cima dela.
- Cada número novo tem dono único e critério fixado antes da primeira execução (ADR-0007).
- A garantia de "sem cabeçalho" é executável em duas camadas que **não** falham juntas.

**Non-Goals:**
- Guardar, mostrar ou enviar o recorte (`slice-5c-1`).
- Medir qualquer coisa em papel. Toda verificação aqui é sobre o documento renderizado, e nenhum
  número novo é calibrado por letra de aluno.
- Mudar `analyze`, a sessão, o `Caderno`, o outbox ou o servidor.

## Decisions

### 1. O recorte é uma chamada própria, e nenhum código de produção a chama ainda

`SheetReader.recortar(gray, map, region)` é uma operação à parte, pedida sobre uma região que o
`analyze` já reconheceu. Ela devolve `RecorteOutcome`: `Recortado(resposta, desvio, residuoMaxMm)` ou
`Recusado(motivo)`. O `analyze` **não** muda, e `RegiaoDiscursivaNoQuadro.Reconhecida` **não** ganha
imagem.

**Por quê.** `analyze` roda a cada quadro, e cada quadro já paga uma retificação supersampleada da
região inteira. Um recorte por quadro, por região, para um consumidor que ainda não existe, seria
custo sem leitor (P18). O recorte é pedido no momento em que alguém precisa dele — a 5c-1.

**O custo aceito, dito sem enfeite:** esta mudança entrega código testado e **sem chamador de
produção**. A razão de fatiar assim é que o recorte é a geometria mais frágil da fatia 5 (o canto
inferior esquerdo é extrapolado — "conhecido, não mitigado", ADR-0018), e a 5c-1 seria a primeira a
usá-lo com uma tela já construída. Descobrir o recorte torto antes da tela custa uma mudança; depois,
custa a tela. A 5c-1 tem de abrir com o chamador, e o `proposal.md` dela SHALL dizer isso.

*Alternativas rejeitadas.* (a) Recortar dentro do `analyze` e carregar a imagem em `Reconhecida`:
custo por quadro, alocação de cerca de 1 MB por região por quadro (87 mm × ~100 mm a 10 px/mm,
*calculado*), e nada a lê. (b) Ligar à sessão e ao
`Caderno`: traz a decisão de onde a imagem mora e por quanto tempo (ADR-0012, classe de retenção de
imagem de manuscrito de menor), que é da 5c-1 e que esta mudança não tem como decidir bem sem o
consumidor.

### 2. O segundo ajuste: onze pontos, na ordem do §8

Depois de `detect` (primeira homografia pelos 8 cantos) e do QR lido, `recortar` monta o ajuste:

| Grupo | Pontos | Onde estão observados | Onde o mapa os declara |
|---|---|---|---|
| Cantos dos 2 marcadores | 8 | `found` (o `detectMarkers` do quadro) | `cornersUvOf(aruco, region)`, como hoje |
| Padrões de posição do QR | 3 | `position.topLeft/topRight/bottomLeft`, no canvas do QR | cantos correspondentes de `region.qr`, normalizados ao quadrilátero |

Os 3 pontos do QR saem do canvas do QR (coordenadas de pixel) para a imagem em dois passos exatos: o
canvas é um recorte linear do quadrado unitário (`u = u0 + x·(u1−u0)/canvasW`, o mesmo `u0`/`u1` que
`rectifyQr` usa), e a inversa da **primeira** homografia leva `(u,v)` à imagem. Só depois disso os 11
pontos entram em `Calib3d.findHomography(..., método 0)` — o mesmo método, sem RANSAC e sem descarte,
que a região já usa: 22 equações, 8 incógnitas, o número do ADR-0018.

**Por que a inversa da primeira homografia, e não ajustar no espaço do canvas.** O ajuste precisa
estar no mesmo espaço dos cantos dos marcadores, que só existem na imagem. Levar tudo para lá é
exato (uma multiplicação de matriz e uma divisão), e não introduz viés: o decodificador mediu no
canvas, e a inversa desfaz o mesmo mapa que o produziu.

**A primeira homografia é recalculada, e não exposta.** `recortar` chama `homographyByCorners` de
novo, em vez de estender `DetectionOutcome.Rectified` com a matriz. Custo: uma chamada barata,
fora do caminho por quadro. Ganho: nenhum contrato do `analyze` muda.

`QrOutcome.Read` ganha `position` (os 4 cantos, em pixels do canvas). É aditivo; as quatro
construções existentes são atualizadas de forma mecânica, e o `analyze` a ignora.

*Alternativa rejeitada:* usar os 4 cantos do símbolo. O quarto é estimado pelo decodificador, e
ancorar o ajuste num ponto que ninguém mediu seria pôr o mesmo canto sem âncora que o ADR-0018
declarou "conhecido" dentro do ajuste que o deveria conferir.

### 3. A conferência é o **maior** resíduo, com teto de 1,0 mm

O resíduo de cada um dos 11 pontos é a distância, em milímetros **da região**, entre onde o ajuste
leva o ponto observado e onde o mapa o declara (`(u,v)` de diferença × `quadWidth`/`quadHeight`). O
teto é sobre o **máximo**.

**Por que o máximo, e não a média do `RegionDetector`.** O defeito a pegar é a dobra num canto
(ADR-0018, decisão 4), e a média sobre onze pontos a dilui: um único canto fora por 3 mm, com os
outros dez no lugar, dá média abaixo de 0,3 mm. A média serve ao gabarito, cujos 16 cantos **não**
entraram no ajuste; aqui todos entraram, e é o pior deles que diz se o ajuste fechou.

**Por que 1,0 mm — a razão, e o estatuto do número.** É o menor folgamento que protege tinta:
metade dos 2 mm de folga acima e abaixo da moldura, e igual ao recuo de 1 mm da pauta em relação à
borda (`PAUTA_INSET`). O canto extrapolado é o inferior esquerdo, e um erro de até 1 mm ali ainda
deixa a escrita do aluno dentro do recorte. *É uma suposição fixada antes da primeira execução, não
uma medição*: nenhuma letra real foi fotografada. Vale a regra da ADR-0007 e a P11: o número **não
é afrouxado** depois de conhecido o resultado; mudar exige ADR novo que registre o resultado obtido.
A linha "O limiar do desvio e o teto do resíduo foram fixados sem letra de aluno" (§16, `6`) mantém
isso à vista.

**O que o resíduo consegue e não consegue pegar é medido, e não afirmado.** A tarefa 3.3 desloca
cada um dos 11 pontos, um por vez, de 1, 2, 3 e 5 mm, e registra o maior resíduo resultante. Os
pontos que o resíduo **não** denuncia — plausivelmente os do QR, que sozinhos ancoram o canto
superior direito e têm alta alavancagem no ajuste — entram no documento de cobertura como lacuna
("não é mitigado, é conhecido", P8), e não como conferência.

### 4. O recorte sai de **um** warp, com a faixa do desvio em volta

Um único `warpPerspective`, com a segunda homografia, sobre um canvas da `answer_area` **mais a faixa
de 3 mm em cada lado**, supersampleado em 3× e reduzido por `INTER_AREA` — a mesma técnica da
retificação de hoje (o comentário do `RegionDetector` explica por que reduzir direto perderia tinta).
A 10 px/mm.

O recorte entregue é o **miolo** desse canvas (a área), copiado para uma `RectifiedRegion` própria. A
faixa fica dentro da função e nunca sai dela: o recorte limpo é a razão de §8 ("evita que o modelo
'responda o enunciado'"), e devolver a faixa daria ao consumidor um jeito de reintroduzir o que o
recorte existe para tirar.

**A faixa (3 mm) tem dono no domínio:** `EssayGeometry.DEVIANT_BAND`, com teste KMP de que ela é
menor que o `gutter` do perfil (3 mm contra 6 mm). Sem isso, a faixa de uma região poderia
alcançar a tinta da coluna vizinha e sinalizar desvio que é da outra questão. É o mesmo raciocínio
da P28: um valor que a captura consome e a geometria decide não pode viver só num literal do
Android.

### 5. O desvio: contagem no aparelho, classificação no domínio

**Tinta é o que a região já declara como não-decoração**: cobertura relativa ao branco local
(`PaperWhite`) igual ou acima de `InkBudget.decorativeToneMax / 1000` (0,5 com o orçamento de hoje).
Assim a pauta a 300‰ nunca conta, e o limiar tem dono — o orçamento de tinta da região, ADR-0010 —
em vez de ser um número novo.

**A tinta impressa é descontada pelo mapa, e não estimada.** A máscara é a união dos retângulos que o
`LayoutMap` declara na página da região — os dois marcadores, o QR e os quatro lados da moldura —,
**dilatados pelo teto do resíduo** (1,0 mm). A dilatação não é folga arbitrária: é o erro de posição
que o próprio ajuste admite. Pixel dentro da máscara não conta nem na área nem na faixa.

O aparelho conta, em mm², a tinta do aluno na área (`dentro`) e na faixa (`fora`). A **classificação**
é uma função pura do domínio, `DesvioDaResposta.classificar(dentro, fora)`:

```
proporcao = fora / (dentro + fora)          (0 se dentro + fora == 0)
sinalizado = proporcao >= 0,05  &&  fora >= 4 mm²
```

Testável na JVM, sem OpenCV, com `NaN`, negativos, zero e infinito cobertos (rigorous §3).

> **Correção de 2026-09-30, na tarefa 1.3 (P7: o texto acima fica).** A guarda D-1.2
> (`IntegerArithmeticGuardTest`) proíbe `Double`/`Float` em todo o `commonMain` do domínio, porque
> ponto flutuante diverge entre JVM e JS. A função acima, escrita com milímetros quadrados
> fracionários e `NaN`/infinito, não compilaria contra a guarda. **Mesma regra, aritmética inteira:**
> a tinta é contada em **centésimos de mm²** (exatamente um pixel a 10 px/mm), a proporção sai em
> partes por milhão, e a comparação é por multiplicação (`fora × 10⁶ ≥ total × 50 000`), sem divisão.
> O piso de 4 mm² é 400. `NaN` e infinito deixam de existir como entrada; o que resta a recusar é
> negativo e acima de um teto (10 m²) que impede o estouro da multiplicação. O comportamento da
> spec não muda.

**Os dois números são suposições** (5% e 4 mm²), fixadas aqui antes da primeira execução. O piso de
4 mm² é aproximadamente um traço de 0,4 × 10 mm — abaixo disso, poeira, sombra na borda ou um ponto
de caneta —, e existe porque sem ele uma resposta curta com um respingo passa de 5% sozinha. *Nenhum
dos dois foi medido em letra.* Mesma regra do teto do resíduo: não se afrouxa depois de ver o
resultado (P11), e a linha do §16 os mantém visíveis.

*Alternativa rejeitada:* medir só a proporção da faixa contra a área da faixa. Ela ignora quanto o
aluno escreveu: uma linha curta e bem-comportada com um borrão na margem daria a mesma nota de
desvio que uma folha inteira escrita para fora.

### 6. A garantia de "sem cabeçalho": duas camadas, e a segunda não depende da primeira

**Camada 1 — o mapa (KMP, `layout-engine`).** `LayoutMapValidation` recusa, dentro da área de
resposta de cada região discursiva, texto, imagem, QR, marcador, círculo ou retângulo preenchido.
A regra é sobre o **mapa**, e por isso roda no momento de publicar, antes de qualquer impressão.

Regras de interseção, todas com o contato de borda **fora** (o QR termina exatamente onde a área
começa, e isso é legítimo):
- QR, marcador, imagem, círculo e retângulo preenchido: interseção de caixas com área positiva;
- texto: **começa dentro da largura da região** e a caixa de linha (`baseline − size … baseline`)
  cruza a altura da área.

**O limite declarado dessa camada:** o `DrawText` não carrega largura, e a validação não mede fonte.
Texto que começa fora da largura da região e a invade lendo para a direita **não é visto**. Não é
mitigado, é conhecido — e é para isso que existe a camada 2.

**Camada 2 — a captura (Android, `capture-omr`).** Sobre a folha renderizada, em perspectiva, o
recorte de uma região sem tinta do aluno tem **zero** pixels de tinta fora da máscara da moldura.
Isso prova a cadeia inteira — mapa, renderizador, homografia, recorte —, não só a intenção do mapa.

**As duas são vistas falhar, e falham por motivos disjuntos** (rigorous §3):
- um texto que começa na largura da região, dentro da área: a camada 1 recusa; a camada 2, com a
  validação contornada, também vê tinta;
- um texto que começa **fora** da largura e a invade: a camada 1 **aceita**; só a camada 2 o vê.

O segundo caso é o que justifica ter as duas, e é o cenário da camada 2 que carrega a
independência. **Guarda de vacuidade (P13):** o teste da camada 2 planta uma tinta conhecida no
recorte e confirma que o contador a conta antes de afirmar zero; sem isso, um contador que devolve 0
sempre passaria.

### 7. Onde cada coisa mora

| Peça | Módulo | Por quê |
|---|---|---|
| Recusa da primitiva estranha na área | `packages/domain` (`LayoutMapValidation`) | É regra do mapa; roda em todos os alvos |
| `EssayGeometry.DEVIANT_BAND` | `packages/domain` | Dono único do valor (P28) |
| `DesvioDaResposta.classificar` | `packages/domain` (`capture`) | Decisão de negócio pura, sem OpenCV (regra 7 do CLAUDE.md) |
| Segundo ajuste, resíduo, warp, contagem de tinta | `apps/android` (`vision/`, `omr/`) | É o único lugar que fala com o OpenCV |
| `QrOutcome.Read.position` | `apps/android` | Onde o decodificador é chamado |

Contrato antes de consumidor (regra 1): o domínio entra primeiro, o Android depois.

## Risks / Trade-offs

- **[A semântica de `position` é suposta]** → a tarefa 2.2 mede, sobre o QR renderizado, a distância
  entre os pontos e os cantos que o mapa declara. Se a hipótese cair, o desenho da decisão 2 muda
  (`design.md` atualizado, sem apagar esta linha — P7), e nenhum código do segundo ajuste é escrito
  antes disso.
- **[O canto inferior esquerdo continua sem âncora]** → o recorte o extrapola, e a folga de 2 mm da
  área é o que o cobre. O teto de 1,0 mm existe para que o erro ali nunca passe da folga; se a
  extrapolação for pior que isso, a conferência não a vê (decisão 3, tarefa 3.3). *Conhecido, não
  mitigado, até o papel.*
- **[Os dois limiares do desvio e o teto do resíduo são suposições]** → fixados antes, sem
  afrouxamento posterior (P11), com linha própria no §16 e a sessão de papel como fatia-limite.
  Consequência de estarem errados: falso desvio (o professor confere à toa) ou desvio não visto
  (corrige-se errado em silêncio, o que §8 diz que o sistema deve evitar). O segundo é o caso
  grave, e por isso o piso e a proporção são conservadores no sentido de sinalizar.
- **[Código sem chamador de produção]** → decisão 1, aceita. Sai da lista quando a 5c-1 abrir.
- **[Quantização de 0,1 mm]** → os cantos do QR chegam em pixels inteiros do canvas a 10 px/mm. É
  uma décima de milímetro, um décimo do teto. *Suposto irrelevante; a tarefa 3.3 registra o resíduo
  de um ajuste sem deslocamento para mostrar o piso do instrumento.*
- **[A máscara pode esconder tinta do aluno]** → a dilatação de 1 mm em volta da moldura e do QR
  esconde tinta do aluno que a encoste. Ela é o preço de não confundir o traço impresso, deslocado
  pelo erro de posição, com tinta de aluno. *Conhecido.*

## Migration Plan

Nada a migrar: sem esquema, sem contrato de fio, sem dado durável. Reverter é reverter os commits. O
`LayoutMap` de hoje passa na regra nova (tarefa 1.2), então nenhum golden é regravado.

## Open Questions

- **Onde o recorte mora e por quanto tempo** — decisão da `slice-5c-1`, com o consumidor. Precisa
  citar a classe de retenção do ADR-0012 antes de escrever em disco.
- **O recorte em cor** (`answer_capture_mode: color`, §8) não entra: o recorte é cinza. Vira pergunta
  quando um item declarar o modo.
