# Protocolo de medição da folha impressa

Nenhum teste automatizado captura escala de impressora, toner fraco ou deriva de alimentação de
papel. Esta é a única verificação da fatia 1 que precisa de uma pessoa, uma impressora e uma régua
— e é ela que fecha a distância entre o `LayoutMap` e o papel que o aluno recebe.

Executar sempre que a geometria de captura mudar (`CaptureGeometry`), quando o `LayoutMap` mudar de
versão, ou ao trocar de impressora de referência.

## 1. Gerar o PDF

```bash
cd apps/web && npx tsx scripts/render-fixture.ts
# saída: build/parity/web.pdf
```

## 2. Imprimir

- Papel **A4**, branco, gramatura comum (75–90 g/m²).
- Escala **100%**. Desligue "ajustar à página", "encaixar", "reduzir para caber" e qualquer
  autoescala do visualizador ou do driver.
- Sem frente e verso.
- Impressora monocromática, toner ou tinta em nível normal — não use um cartucho no fim da vida
  para esta medição.

Se o driver não oferecer 100% explícito, imprima por um visualizador que ofereça, e confirme antes
de medir com o passo 3.1.

## 3. Medir

Use régua metálica com escala de milímetro, ou paquímetro se houver. Meça sobre a **página 1**, que
é onde fica a região de gabarito. Anote o valor observado, não o esperado.

**Uma régua lê ±0,5 mm, não ±0,2 mm.** Por isso quase nada aqui é medido uma vez só: as medidas
pequenas são tomadas sobre vários passos e divididas, o que reduz o erro de leitura na mesma
proporção. Uma leitura isolada de uma bolha de 4,4 mm não distingue 4,2 de 5,0 — não tente.

### 3.0 Portão: a escala da impressão

**Meça isto primeiro. Se falhar, pare — nenhuma outra medida significa nada.**

| # | O que medir | Esperado | Aceitável |
|---|---|---|---|
| 3.1 | Largura da área impressa: traço mais à esquerda até o mais à direita | 180,0 mm | 179–181 mm |

180 mm é a maior distância da folha e é onde a régua é mais confiável. Um desvio de poucos
milímetros aqui denuncia impressão fora de 100%: 175 mm significa 97%, e nesse caso **toda** medida
seguinte sai encolhida junto, inclusive as que parecem certas por arredondamento.

Se der fora da faixa: volte ao passo 2, desligue explicitamente qualquer ajuste de escala e
reimprima. Não continue com uma folha escalada.

**Este portão é da sessão de medição, não da impressora.** Ele existe porque 3.2 a 3.7 comparam
régua com valor nominal, e isso exige 100%. A pergunta "esta impressora está apta a imprimir a
prova?" é outra, tem outro critério — ±5%, por ADR-0001 — e quem a responde é a folha de teste de
3.5. Uma folha a 97% está apta a virar prova e é imprópria para as medidas abaixo.

### 3.1 Medidas

| # | O que medir | Esperado | Aceitável |
|---|---|---|---|
| 3.2 | Margem esquerda: borda do papel até o traço mais à esquerda | 15,0 mm | 14,5–15,5 mm |
| 3.3 | Margem superior: borda do papel até o topo do marcador | 14,0 mm | 13,5–14,5 mm |
| 3.4 | Lado de um marcador ArUco (qualquer um dos quatro) | 14,0 mm | 13,5–14,5 mm |
| 3.5 | Borda esquerda da bolha (A) até a borda esquerda da bolha (D) da mesma questão, dividido por 3 | 5,2 mm | 5,0–5,4 mm |
| 3.6 | Borda externa esquerda da bolha (A) até a borda externa direita da bolha (D) da mesma questão | 20,0 mm | 19,5–20,5 mm |
| 3.7 | Distância entre os centros de dez linhas de bolhas, dividida por 10 | 6,0 mm | 5,9–6,1 mm |

O diâmetro da bolha sai de 3.6 menos três passos de 3.5: `20,0 − 3 × 5,2 = 4,4 mm`, que é o
diâmetro externo com o traço de 0,22 mm incluído. Medir a bolha isolada com régua não funciona.

Referência conferida sobre o próprio PDF, rasterizado a 1200 dpi: largura impressa 180,00 mm,
margem esquerda 15,01 mm, margem superior 14,03 mm, lado do ArUco 13,99 mm, diâmetro externo da
bolha 4,42 mm, passo horizontal 5,20 mm, passo vertical 6,00 mm. Divergência acima da faixa
aceitável é da impressão, não do layout.

## 3.5 A folha de teste de impressão (fatia 2b)

Acrescentada ao abrir a fatia 2b. **Esta é a seção que responde "esta impressora serve?", e ela vem
antes de tudo o que está acima.** O restante do protocolo mede o sistema; esta parte mede a
impressora, que é o que §16 põe como risco desta fatia — depois que há folha distribuída, corrigir
marcador significa reimprimir.

```bash
cd apps/web && npx tsx scripts/render-test-sheet.ts
# saída: build/parity/teste-web.pdf — uma página
```

Imprima com os mesmos ajustes do passo 2 e confira as cinco linhas **impressas na própria folha**.
Elas não estão repetidas aqui de propósito: quem confere está com o papel na mão, e um critério que
mora no repositório é um critério que ninguém lê na hora.

O que a folha traz, e por quê:

| Elemento | Para quê |
|---|---|
| Vão de referência de 180,0 mm | Portão de escala, o mesmo do passo 3.1 — a maior distância da folha, onde a régua é mais confiável |
| Quatro ArUcos de 14 mm | A geometria de captura real, nos mesmos números da prova |
| QR | Decodificação sob o toner desta impressora |
| Amostras de trama a 4,5% e 8% | Trama que some é toner fraco; trama chapada é toner pesado — as duas quebram o gabarito de formas diferentes |
| Cinco bolhas com a letra dentro | Círculo de 4,2 mm fechado, e letra legível sem parecer marcação |

**Reprovou em qualquer linha, a impressora não está apta.** Não imprima a turma; troque o toner,
desligue qualquer ajuste de escala e repita.

Sobre a linha 1, que é a que mais confunde: a faixa é de ±5% porque ADR-0001 mediu impressoras
reais errando de −3,4% a +4,7% com o driver em 100%, e a homografia da fatia 3 absorve isso. Ela
**aprova** o encolhimento de ~3% que "ajustar à página" causa de A4 para A4, e **reprova** o erro
que de fato quebra a captura: A4 impresso em bandeja Letter, que encolhe 5,9% e mede 169,3 mm.

Uma propriedade que vale saber: a folha de teste é um `LayoutMap` calculado pelo mesmo engine, com
as mesmas primitivas e a mesma `CaptureGeometry` da prova, e passa pelas mesmas verificações
automáticas — fidelidade, paridade entre plataformas e tinta. Ela não é um desenho paralelo. Uma
folha que aprovasse a impressora por um caminho que a prova não percorre não aprovaria nada.

## 4. Inspecionar os marcadores

- Os quatro marcadores estão completos, com a borda preta fechada nos quatro lados.
- Nenhum traço invade a zona de silêncio de 2 mm ao redor de cada marcador.
- Os módulos internos estão nítidos — sem borrão que una dois módulos vizinhos, sem falha branca
  dentro de um módulo preto.

Marcador com borda falhada é a causa mais comum de captura que não fecha em campo (§16).

## 5. Conferir o QR

Aponte a câmera de um celular para o QR da folha impressa. Ele deve ler:

```
prova-referencia-slice-1...0.05CB
```

Os dois campos vazios entre os pontos são `student_token` e `variant`, que a fatia 7 preenche.

## 6. Conferir em dois visualizadores

Abra `build/parity/web.pdf` em dois visualizadores diferentes (por exemplo, o do navegador e um
leitor de PDF instalado) e confirme em ambos:

- Tamanho de página declarado: **595 × 842 pt** (209,90 × 297,04 mm).
- As margens e as medidas acima não mudam entre visualizadores.

A caixa de página é declarada em pontos inteiros porque o `PdfDocument` do Android só aceita
inteiros, e uma caixa diferente entre as duas plataformas seria divergência gratuita. O conteúdo é
posicionado pela conversão exata, então as medidas de 3.4 a 3.7 não são afetadas pelo arredondamento
da página.

## 7. Registrar

Anote os valores observados na tarefa 10.2 de `openspec/changes/slice-1-layout-engine/tasks.md`,
junto com impressora, driver e data. Valor observado, não valor esperado: o objetivo do registro é
poder comparar a próxima medição com esta.

## 8. Legibilidade da matemática impressa (fatia 1.5)

Acrescentado ao fechar a impressão da tarefa 6.6. Esta seção não usa régua: é inspeção a olho, e
existe porque a resolução do raster (D-1.5.1, 600 dpi) foi escolhida por argumento e precisa ser
conferida no papel.

Imprimir `build/parity/web.pdf` (4 páginas, 12 fórmulas) e conferir:

- traço da fração e da raiz — são os finos, e o primeiro lugar onde 600 dpi falharia;
- subscrito e expoente de `f-potencias`, os menores glifos da folha;
- barras verticais de `f-matriz2` e parênteses altas de `f-matriz3`;
- se a fórmula tem o mesmo peso óptico do texto ao redor, já que foi composta no mesmo corpo;
- **espaçamento**: a fórmula precisa ler como parte do enunciado, e não do bloco de alternativas.

### O que a primeira impressão encontrou

A resolução passou: traço, subscrito, expoente, barras e peso óptico foram aprovados a olho. **O
espaçamento não.** Nas 12 fórmulas, o branco acima era grande demais e o de baixo pequeno demais, e
por proximidade a fórmula se lia como pertencente às alternativas — o inverso do correto, já que a
fórmula é parte do enunciado.

Medido depois, na tinta do PDF a 600 dpi: **10,089 mm de média acima contra 2,565 mm abaixo**, razão
3,9× e até 6,9× em `f-matriz2` e `f-sistema`. O vão de cima era constante nas 12 (amplitude
0,931 mm); o de baixo variava 2,159 mm, porque era ele que absorvia o resíduo do arredondamento à
grade.

A causa não era uma constante errada: era misturar posicionamento por linha de base com
posicionamento por topo. Corrigido em D-1.5.9.

**Por que isto vale registrar aqui.** Nenhuma das verificações automáticas desta fatia podia pegar
isso, e nenhuma delas estava errada. Paridade compara os dois renderizadores entre si — e os dois
erravam igual, com 0,042 mm de divergência. Fidelidade compara o documento com o `LayoutMap` — e o
documento estava fiel ao mapa; o mapa é que estava errado. Golden compara o mapa com ele mesmo. Um
defeito de *julgamento tipográfico* não tem oracle dentro do sistema: o oracle é o olho de quem lê a
folha. É exatamente o que a tarefa 6.6 existe para fazer, e é o argumento para ela nunca ser
substituída por medição.

### O que a segunda impressão pediu

A correção de espaçamento foi aprovada, com um ajuste: mais separação entre a fórmula e as
alternativas. O vão inferior passou de 4/4 para **4/3** da transição de texto — de 5,214 mm para
7,775 mm de branco visível, +49,1%.

Vale registrar a armadilha, porque ela vai reaparecer em qualquer ajuste tipográfico futuro: **o
pedido veio em tinta e a implementação é nominal, e as duas escalas não são proporcionais.**
Multiplicar o espaçamento nominal por 1,5 teria dado +74% de branco visível, não +50%, porque a
ascendente da linha seguinte é uma subtração fixa que não escala junto. Quem ajustar espaçamento a
olho precisa converter, e não multiplicar.

### Resultado

**Aprovada na terceira impressão.** A resolução passou na primeira — os 600 dpi de D-1.5.1 estão
conferidos no papel. O espaçamento levou duas correções: a primeira inverteu a proximidade, a
segunda ajustou a separação das alternativas.

O que fica desta tarefa para as próximas fatias, mais do que o resultado: **a impressão achou o que
nenhuma verificação automática podia achar, e nenhuma delas estava errada.** Paridade compara os
dois renderizadores entre si, e os dois erravam igual; fidelidade compara o documento com o
`LayoutMap`, e o documento estava fiel a um mapa errado; o golden compara o mapa consigo mesmo.
Defeito de julgamento tipográfico não tem oracle dentro do sistema. Enquanto a folha for impressa
por uma pessoa, esta seção precisa continuar existindo.

### O que a impressão da fatia 1.6 encontrou

Matemática **em linha**, e de novo o defeito não era o que se procurava. A folha saiu com

```
Quanto vale12 + 15ao todo?
```

— texto e fórmula colados, sem o espaço que o enunciado tem. A causa: a medição separa o enunciado
em trechos e quebra cada trecho em palavras por espaço; dentro do trecho os espaços voltam porque as
palavras são rejuntadas com `" "`, mas **na fronteira com a caixa não há junção**, e o espaço era
descartado.

**Nenhuma verificação automática podia ter pego, e vale entender por quê.** As três mediam largura,
altura e quebra de linha — e um espaço de 0,78 mm a menos não muda nenhuma das três o bastante para
reprovar. A paridade compara os dois renderizadores, e os dois colavam igual. A fidelidade compara
o documento com o `LayoutMap`, e o mapa já trazia as posições coladas. O golden compara o mapa
consigo mesmo.

É o mesmo padrão da fatia 1.5, onde o espaçamento em volta da fórmula em bloco estava invertido: **o
que o olho julga em tipografia não tem oracle dentro do sistema.** Duas fatias seguidas, dois
defeitos que só a folha impressa achou, ambos de espaçamento.

O teste que agora impede a volta afirma **posição**, e não medida: o `x` da caixa tem de ser o fim do
texto anterior mais um espaço. Junto dele vai o par que impede o conserto de virar "sempre põe
espaço" — quando o enunciado não tem espaço, como em `valor({{f}})`, a caixa continua encostada.

### Resultado da fatia 1.6

**Aprovada na terceira impressão.** O alinhamento à linha de base passou de primeira, incluindo os
dois extremos de deslocamento — a raiz, que desce 7,5% da própria altura, e a fração, que desce
34,5%. As duas reprovações foram de **espaçamento horizontal**: primeiro ausente, depois apertado.

O ajuste final foi um **espaço fino de 1/6 do corpo** somado ao espaço da palavra, e a razão é
tipográfica: entre duas letras o branco visível é o avanço do espaço mais as laterais dos dois
glifos; entre uma letra e a caixa da fórmula uma dessas laterais não existe, e o mesmo avanço
produz menos branco.

> **Duas fatias, dois defeitos de espaçamento, os dois achados só no papel.** É o argumento mais
> forte que este protocolo tem para continuar existindo. As três verificações automáticas medem
> largura, altura e quebra de linha — e nenhuma delas se move o bastante quando falta 0,78 mm entre
> um glifo e uma caixa, ou quando o branco de cima e o de baixo estão trocados. O olho de quem lê a
> folha é o único oracle para julgamento tipográfico, e ele achou nas duas vezes.

Uma consequência que vale registrar: o segundo achado **explicou** uma divergência de paridade que
estava registrada como não explicada, e permitiu reverter 117 linhas de compensação em
`compare.mjs`. Consertar a folha barateou a ferramenta.

## 9. Tinta decorativa e trama (fatia 2b)

A folha ganhou tinta que não é traço: a faixa alternada de 4,5% sob os grupos de questões e a letra
da alternativa em cinza dentro do círculo (§7). As duas existem contra o salto de linha, e as duas
põem tinta **dentro da bolha que o OMR vai medir** — por isso ADR-0010 lhes dá orçamento.

O que a máquina já garante, e não precisa ser conferido a olho: a cobertura de tinta de cada bolha
não respondida (máximo medido: 7,24%, orçamento 12%), o teto de trama de 8%, e a ausência de
qualquer pixel colorido. `node tools/parity/tinta.mjs <pdf> <layout.json>` roda isso em segundos e
está no CI.

O que **só o papel decide**, e é o que se confere aqui:

- A faixa aparece? Trama de 4,5% é clara de propósito; numa impressora com toner fraco ela pode
  sumir, e aí a mitigação do salto de linha deixou de existir sem ninguém avisar.
- A faixa atrapalha? Se ela competir com o texto ou escurecer a bolha a ponto de dar dúvida sobre
  o que está marcado, ela cede — a folha é cosmética, a leitura não é (ADR-0010).
- A letra dentro do círculo é legível **e** claramente não é uma marcação? As duas coisas ao mesmo
  tempo. Legível demais vira resposta; clara demais some.

Ajuste, quando necessário, é no tom ou na trama, dentro do orçamento, com regravação do golden por
essa causa — como as fatias 1.5 e 1.6 fizeram com espaçamento.

## 10. Medir a folha depois de preenchida (fatia 2b)

O passo 9 confere tinta impressa. Este confere a tinta que o **aluno** põe, e é o único jeito de
saber se o piso de 50% de ADR-0010 sobrevive a uma caneta real.

```bash
# preencha bolhas a caneta na folha impressa, digitalize em qualquer resolução
node tools/parity/papel.mjs <digitalizacao.jpg> fixtures/prova-referencia.layout.json
```

**O dpi da digitalização não precisa ser conhecido, e não deve ser informado.** A escala sai dos
quatro ArUcos achados na própria imagem; cobertura é razão. Um scanner que não deixa escolher nem
ver a resolução serve.

O que a ferramenta responde, e o que cada resposta significa:

| Saída | Para quê |
|---|---|
| `caneta: … min X%` | O piso de ADR-0010. Abaixo de 50%, é a decoração que cede — tom da letra, trama da faixa, letra fora do círculo, nessa ordem |
| `vazias: … max Y%` | O orçamento decorativo **no papel**, que é maior que no PDF: toner e scanner engordam glifo e trama |
| `corredor observado` | O vão entre as duas nuvens. É o que a fatia 3 herda para pôr o limiar do OMR, e ele precisa conter o corredor declarado de 20% a 40% |
| `196 modulos conferidos` | Os 7×7 módulos dos quatro marcadores, comparados com o mapa. É a conferência 2 da folha de teste feita por máquina — borrão que una dois módulos, ou falha branca dentro de um preto, troca um bit |

**A escala da impressão sem régua.** Se a folha inteira couber na digitalização, com as bordas do
papel visíveis, a razão entre o vão dos centros de marcador e a largura do papel dá a escala sem
depender de dpi nem de régua: A4 tem 210 mm quer o scanner saiba disso ou não. Na impressão de
referência de 2026-08-22 isso deu 96,6% e 97,3% nas duas folhas, contra 97,2% da régua — dentro do
±5% que ADR-0001 declara normal.

**Cuidado com o §3.0.** Aquele portão pede 179–181 mm porque as medidas de régua de 3.2 a 3.7
comparam com valores nominais, e numa folha a 97% todas saem encolhidas junto. Ele **não** é
critério de aptidão da impressora — esse é o da folha de teste (±5%, ADR-0001). Uma folha a 97%
está apta a virar prova e é imprópria para conferir o lado do marcador com régua.

### As digitalizações versionadas (fatia 3a)

A conferência de papel da fatia 2b foi feita sobre uma folha impressa em **2026-08-22** por
terceiro, a pedido, e digitalizada no equipamento que essa pessoa tinha — sem controle sobre o dpi,
que o aparelho não permitia escolher nem exibir. As 40 questões foram preenchidas a caneta, uma
alternativa por questão. **A folha física não está mais disponível.**

As duas imagens ficaram versionadas em `fixtures/`:

| Arquivo | O que é |
|---|---|
| `prova-referencia.digitalizacao.jpg` | a prova de referência preenchida, 2160×3026 |
| `folha-de-teste.digitalizacao.jpg` | a folha de teste de impressão, 2160×3067, uma bolha preenchida |
| `*.papel.json` | o que `papel.mjs` mediu em cada uma: cantos dos marcadores e cobertura bolha a bolha |

Elas não estão ali por conveniência de teste. Sem elas, os **51,61%** que
`docs/cobertura-fatia-2b.md` afirma seriam um número sem como ser repetido — a folha acabou, e o
dpi nunca foi conhecido. Enquanto não houver corpus fotografado, estas duas imagens são a única
evidência de papel que o repositório tem.

Duas propriedades da impressão que valem saber antes de usá-las como referência: ela saiu a **~97%
de escala** (aprovada por ADR-0001, ver §3.5) e o scanner **comprime a faixa dinâmica** — o papel lê
233 de 255 e o miolo preto de um ArUco lê 83, de modo que toner pleno rende no máximo 64% de
cobertura nesta imagem.

### Duas implementações, e por que as duas continuam existindo (fatia 3a)

Desde a fatia 3a existe uma segunda medição, oficial, em Kotlin: `SheetReader` no `apps/android`.
Ela percorre o pipeline de §8 inteiro — ArUcos, homografia, retificação, QR, cobertura — e é a que
vai para o aparelho do professor. `papel.mjs` **não** foi aposentado, e a razão é que ele é a única
coisa que confere a outra por um caminho independente.

| | `papel.mjs` | `SheetReader` |
|---|---|---|
| Linguagem | JavaScript | Kotlin |
| Acha ArUco por | componentes conexos sobre limiar | contorno e dicionário, no OpenCV |
| Mede sobre | a imagem original, amostrando um círculo | a região retificada |
| Onde roda | linha de comando, sobre um arquivo | aparelho e emulador |

As duas concordam dentro de **20‰** na folha de referência, e a diferença não é ruído: é a distância
entre os dois métodos. Onde mais importa — a bolha `q22/A`, o rabisco mais fraco da folha — elas dão
514‰ e 516‰.

Para medir uma digitalização nova pela ferramenta de linha de comando, o comando continua o de §10.
Para conferir que a implementação oficial concorda com ela, o caminho é o teste instrumentado
`SheetReaderInstrumentedTest`, que roda no emulador.

**Uma propriedade que a fatia 3a acrescentou e que o CI vigia:** as fixtures derivadas da
digitalização — os dois `.papel.json` e o `prova-referencia.recorte.pgm` — são regeneradas a cada
execução e comparadas com as versionadas. Se a imagem e o vetor de referência deixarem de
corresponder, o job falha; sem isso, os testes do OMR continuariam verdes comparando com uma
referência que não descreve mais a folha.

---

## 11. O corpus do limiar do OMR (fatia 3b)

O passo 10 mediu **uma** folha preenchida, num scanner, para saber se o piso de 50% de ADR-0010
sobrevive a uma caneta real. Este passo produz o corpus que **escolhe o limiar** — e o critério que
ele tem de satisfazer está fixado em ADR-0011, escrito antes da primeira foto.

**Leia ADR-0011 antes de fotografar.** Ele diz o que aprova, o que reprova e o que acontece se
reprovar. Fotografar primeiro e ler depois anula o motivo de o critério existir.

### 11.1 Por que câmera de celular, e não scanner

§321 registra a propriedade que desqualifica o scanner para este uso: ele comprime a faixa
dinâmica. Naquelas digitalizações o papel lê 233 de 255 e o miolo preto de um ArUco lê 83, de modo
que toner pleno rende no máximo 64% de cobertura. Os 51,61% da caneta e os 7,24% da decoração
nasceram nessa escala.

O produto lê por câmera de celular. Outra faixa dinâmica, outro branco, sombra própria, e
perspectiva. Repetir o corpus em scanner confirmaria o número no meio em que ele não será usado.

### 11.2 O que imprimir

Duas folhas da prova de referência — 40 questões de 4 alternativas, 320 bolhas por folha.

```bash
cd apps/web && npx tsx scripts/render-fixture.ts   # saída: build/parity/web.pdf
```

Antes, imprima a folha de teste de impressão de §3.5 e confira a escala. Uma folha fora do ±5% de
ADR-0001 não serve de corpus: ela mede a impressora, não o limiar.

### 11.3 Como preencher

**As bolhas que decidem** — uma alternativa por questão, em cada folha, preenchidas *conforme a
instrução impressa na própria folha*. São 80 bolhas, e são elas que formam o `C` de ADR-0011. As
240 restantes ficam vazias e formam o `V`.

**As bolhas que não decidem** — num subconjunto declarado, preencha de propósito de leve: traço que
não fecha o círculo, risco em vez de preenchimento. Anote quais são. Elas são medidas e registradas,
e **ficam fora do critério**, pela razão que ADR-0011 dá.

Anote, para cada folha, qual alternativa foi marcada em cada questão. Esse é o gabarito do corpus, e
é o oracle da nota — ele não passa por nenhum código desta fatia.

**Não há dado de aluno no corpus, nem por acidente:** o payload do QR carrega `student_token`
**vazio** até a fatia 7 (§8), e `assignments` do pacote de referência é uma lista vazia. Não existe
campo onde um aluno real caberia.

### 11.4 Como fotografar

Três condições, cada uma sobre as duas folhas — seis fotos no mínimo:

| Condição | O que ela testa |
|---|---|
| Luz de ambiente frontal, folha plana | O caso bom. Se o limiar não separar aqui, não separa em lugar nenhum |
| Sombra parcial atravessando a região | A normalização contra o branco local, que §8 e a spec de `capture-omr` exigem |
| Ângulo de 20 a 30 graus | A homografia e a retificação, com a bolha longe da câmera |

Os quatro marcadores da região precisam aparecer inteiros em toda foto — sem eles não há geometria,
e a leitura recusa antes de medir. Sem flash: o reflexo especular no papel é branco estourado, que a
normalização lê como papel e a bolha embaixo dele some.

Não informe nem se preocupe com resolução: a escala sai dos ArUcos, e cobertura é razão.

### 11.5 Medir

```bash
# a implementação de referência, independente, em linha de comando
node tools/parity/papel.mjs <foto.jpg> fixtures/prova-referencia.layout.json
```

A implementação oficial — `SheetReader` — mede as mesmas fotos pelo teste instrumentado, no
emulador ou no aparelho. As duas precisam concordar dentro de **20‰**, que é a distância entre os
dois métodos medida na folha de referência (§10, fim). Divergência maior é investigada **antes** de
qualquer número ser aceito: as duas medindo o mesmo errado é o único modo de falha que o oracle
independente não pega, e discordância é o sinal de que ele está funcionando.

### 11.6 Registrar

Em `docs/cobertura-fatia-3b.md`: `V`, `C`, o vão, o `T` calculado pela regra de ADR-0011, quantas
bolhas, quantas fotos, em que condições, com que aparelho e com que impressora. As fotos ficam
versionadas em `fixtures/`, como as digitalizações da 2b — pela mesma razão: sem elas o número não
pode ser reexaminado, e uma folha de papel não sobrevive a duas fatias.

## 12. Conferir a captura ao vivo, em aparelho (fatia 3c)

O que segue não é medição: é o roteiro da parte desta base que **nenhum teste alcança**. O teste
instrumentado não aponta a câmera para papel, e o que sobra — o ciclo de vida do CameraX, o preview
e a tela — só se verifica com o aparelho na mão e a folha impressa na mesa. Escrever o roteiro é o
que impede que "conferi e funcionou" seja a evidência.

### 12.1 Preparar

```bash
./gradlew :apps:android:assembleDebug
adb install -r apps/android/build/outputs/apk/debug/android-debug.apk
```

O aplicativo carrega o pacote da prova de referência embutido como asset. Imprima a folha da prova
de referência pelo mesmo caminho de §11.2. A folha precisa ser **desta** prova — mas não conte com a
recusa por identificador para avisar quando não for: ver §12.5.

### 12.2 A leitura fecha

Aponte para a folha preenchida, à distância em que os quatro marcadores aparecem inteiros. Anote
**quanto tempo até a nota aparecer**. É o número que decide se a fatia 3d pode manter a câmera
aberta por trinta folhas seguidas, e ele não existe em teste nenhum.

### 12.3 Os dois momentos são distinguíveis

Com a câmera longe do papel, a faixa diz que está procurando.

O segundo momento **precisa ser provocado de propósito**, e a fatia 3c descobriu isso na mão: luz
baixa, tremor e ângulo forte não servem: todos falham **cedo**, na detecção dos marcadores ou na
reprojeção, e caem em "procurando" igual. Para chegar em "achei a folha e não consegui ler" a
geometria tem de fechar e a leitura tem de falhar depois dela.

Cole um **post-it sobre o QR** — um quadrado de 14 mm centrado na largura da página, começando a
39 mm da borda de cima — deixando os quatro ArUcos inteiramente visíveis, e aponte a uma distância
que normalmente lê. A faixa tem de dizer que achou a folha e não conseguiu ler, com o motivo que o
pipeline produziu. Tire o post-it sem mexer no enquadramento: a leitura fecha sozinha, o que
confirma que o estado é transitório e a análise não parou.

**Anote a que distância a leitura deixa de fechar neste aparelho.** É dado para quem for
investigar, e não regra: a fatia 3b mediu o corpus inteiro e mostrou que resolução não prevê
decodificação.

### 12.4 A folha B não mostra a nota da folha A

Escaneie uma folha, toque em escanear outra, escaneie a segunda. A nota tem de trocar. É o defeito
mais caro da fatia — plausível na tela, invisível para quem lê — e o teste que o cobre roda na JVM;
este passo confere que o encanamento entre a câmera e a sessão não o reintroduz.

### 12.5 A folha de outra prova é recusada — e a folha de teste **não serve** para conferir isso

Conferido em aparelho na fatia 3c, e o resultado foi outro: a folha de teste de impressão para em
"procurando a folha", e não em recusa. Ela declara um quadrilátero de 31 mm de altura contra os
85 mm da prova; a reprojeção dos cantos dos marcadores estoura o teto de 6 px e a detecção falha
**antes** do QR, então o `exam_short_id` nunca é comparado.

A recusa por identidade existe e está verificada na JVM. O que não existe é oráculo físico para
ela: seria preciso imprimir uma segunda prova com região do mesmo tipo. Enquanto não houver, este
passo não tem como ser executado — e escrever isso é melhor do que executá-lo e ler "procurando"
como se fosse recusa.

### 12.6 Luz e inclinação: a degradação tem de aparecer como pendência

Incline a folha com força, e depois deixe luz bater direto sobre ela — janela, lâmpada, o brilho
que aparece no preview. Escaneie e **confira a nota contra o papel, questão por questão**.

O que se procura aqui não é a nota bater: sob essas condições ela não bate mesmo, porque bolha
marcada perde cobertura. O que se procura é **a nota errada nunca aparecer sozinha**. Toda vez que
o número não bater, tem de haver pendência na tela, e a nota não pode estar apresentada como
fechada. Nota errada e limpa é o defeito; nota errada com pendência é o corredor de ADR-0011
funcionando.

Na fatia 3c, num Poco X8 Pro, nunca apareceu nota errada sem pendência — em dezenas de repetições
sobre seis folhas. Anote quantas pendências por folha: é o custo de revisão à mão, e a fatia 3d
precisa desse número.

### 12.7 Registrar

Em `docs/cobertura-fatia-3c.md`: o que fechou, o que não fechou, o tempo até a leitura, a distância
em que ela deixa de fechar, o aparelho, e **se alguma nota fechou errada**. **Inclusive quando o resultado for ruim** — se a leitura
ao vivo não fechar em condição de sala de aula, isso é resultado da fatia, e não fracasso dela.


## 13. Conferir a entrada e a sessão, em aparelho (fatia 4a-zero)

Como §12, isto não é medição: é o roteiro da parte que nenhum teste alcança. As decisões desta fatia
— os três motivos de falha, a escolha de organização, o que sair apaga — rodam na JVM. O que sobra é
o encanamento contra servidor de verdade, e foi ele que escondeu o defeito mais caro da fatia: um
tempo limite que nunca agia.

### 13.1 Preparar, e o que precisa existir antes

Quatro coisas, e nenhuma delas o aplicativo cria:

1. **API no ar.** `curl https://<servico>/health` responde `ok`, e `/me/organizations` sem token
   responde 401. Se responder 200 sem token, pare tudo.
2. **`local.properties` apontando para ela.** `platos.apiUrl` sem barra final. O valor entra em
   `BuildConfig` em tempo de build: mudar o arquivo **exige recompilar**.
3. **Dois usuários no Supabase**, criados em Authentication → Users → Add user com *Auto Confirm
   User* marcado. Sem o auto-confirm eles existem e não entram, e a tela diz "credencial recusada" —
   indistinguível de senha errada. Ver §13.7.
4. **Uma segunda organização para o primeiro usuário**, criada por SQL depois de ele ter entrado uma
   vez. Não há trigger: `app_user`, a organização pessoal e o `membership` nascem de
   `bootstrap_identity`, que a API chama na primeira requisição autenticada. Antes dessa primeira
   entrada não existe `app_user` para o insert referenciar.

O SQL precisa de `set role app_owner;` antes e `reset role;` depois. As tabelas têm `force row level
security` e as políticas de bootstrap são `to app_owner`; o `postgres` do projeto **não** tem
`BYPASSRLS`, então sem trocar de papel o insert é recusado por política, e não por sintaxe.

```bash
./gradlew :apps:android:installDebug
```

### 13.2 O nome vem da API

Entre com o primeiro usuário. A tela de trabalho tem de mostrar o nome que a API devolveu — para um
usuário sem display name, `bootstrap_identity` usa a parte local do e-mail, então
`professor1@teste.com` vira `professor1`.

Confira contra a resposta crua, e não contra a expectativa:

```bash
curl -s "$API/me/organizations" -H "Authorization: Bearer $TOKEN"
```

### 13.3 Os três estados de falha, e como provocar cada um

**Credencial recusada:** e-mail que não existe no projeto. A tela permanece na entrada.

**Sem rede:** modo avião, com a **mesma** credencial nos campos. A frase tem de mudar. Usar a mesma
credencial é o ponto: é o que separa "diz coisas diferentes" de "diz coisas diferentes porque a
entrada era diferente".

**Sessão expirada:** não tem atalho. Revogar a sessão no painel não invalida um access token já
emitido; apagar o usuário também não, porque a assinatura continua válida e `bootstrap_identity`
recriaria o `app_user`; e baixar o *JWT expiry* no painel não encurta token já emitido — `exp` é
gravado na emissão, então só afetaria emissões futuras, e aproveitá-lo exigiria relogar. **Espere a
expiração real.** O padrão do Supabase é 3600 s; confirme decodificando `exp - iat` do token.

Depois da expiração, reabra o aplicativo mais uma vez: ele tem de cair na entrada **sem faixa**. Com
faixa, a credencial foi apagada mas o motivo sobreviveu ao fechamento; sem faixa, o estado foi
reconstruído do disco e a credencial não está mais lá — que é o que se quer.

### 13.4 A consulta falhando depois de autenticar

Com sessão válida guardada, ligue o modo avião e reabra o aplicativo. A tela tem de explicar que não
conseguiu obter a organização e **não apresentar nome nenhum**. Varra a tela contra os nomes reais
das organizações do projeto, contra `Minha organizacao` e contra qualquer texto de espera — um nome
de reserva é indistinguível de nome verdadeiro para quem lê.

Restaure a rede e toque em "Tentar de novo": tem de recuperar sem pedir a senha.

### 13.5 A escolha, e o que sair apaga

Com o usuário de duas organizações, a tela de escolha só aparece quando **não há escolha guardada**.
Depois da primeira entrada com uma organização só, ela ficou guardada automaticamente — então
acrescentar a segunda organização não faz a tela aparecer. É preciso **Sair** antes, porque sair
apaga a escolha junto com a credencial.

Escolha uma, e feche o aplicativo **de verdade**: `adb shell am force-stop`, conferindo com
`adb shell pidof` que o processo morreu. Reabrir tem de ir direto para a organização escolhida, sem
perguntar.

Depois: sair, entrar com o **segundo** usuário, e conferir que o nome da organização do primeiro não
aparece em lugar nenhum. É o defeito mais caro da fatia — aparelho compartilhado entre escolas, e o
segundo usuário herdando a organização do primeiro sem nada na tela dizendo isso.

### 13.6 O cold start, que é onde o encanamento quebra

**Este passo achou um defeito que nenhum teste desta base pegaria**, e é o motivo de ele estar
escrito. O plano gratuito do Render suspende o serviço depois de ~15 min sem tráfego, e a primeira
chamada seguinte espera o contêiner subir — medido em 43 s, 53 s e 58 s.

Deixe o serviço **em silêncio por 17 minutos** — nenhum `curl`, nenhuma abertura do aplicativo — e
então entre. A consulta tem de sobreviver à espera e terminar na tela certa.

O defeito que apareceu aqui: `requestTimeoutMillis` do Ktor não substitui os tempos limite do
engine, acrescenta um teto por cima. Sem `socketTimeoutMillis`, o OkHttp mantinha os 10 s de leitura
que traz por padrão, e a consulta morria aos ~11 s dizendo "sem rede" com a rede boa. `MockEngine`
não tem soquete, então nenhum teste de JVM alcança isso.

Anote o tempo. Se o pior caso passar de 10 s, o gatilho da decisão 10 do `design.md` disparou e a
reconsulta em toda recriação volta à mesa.

### 13.7 Duas armadilhas do instrumento, e não do aplicativo

Numa conferência dirigida por `adb`, **"a tela mostra X" e "a tela ainda mostra X" são
indistinguíveis**. As duas aconteceram nesta fatia e as duas quase viraram defeito reportado:

- **O botão se move.** Quando a faixa de motivo entra, o layout desce — na fatia 4a-zero, 64 px. Um
  toque em coordenada fixa cai fora e nada acontece, e a faixa antiga continua na tela. Leia a
  posição do elemento no `uiautomator dump` **a cada passo**, nunca uma vez só.
- **O primeiro frame depois de `am start` não é conteúdo vivo.** É o *starting window* que o Android
  desenha com a captura da instância anterior. Espere um estado que só a instância nova produz.

E antes de suspeitar da conta, descarte o `adb`: `input text` pode engolir caractere. Digite a senha
num campo **visível** uma vez e leia de volta. Depois, se a entrada falhar, vá ao endpoint em vez de
acreditar na tela — `invalid_credentials` e `email_not_confirmed` são coisas diferentes, e a tela
apresenta as duas como credencial recusada:

```bash
curl -s -X POST "$SUPABASE/auth/v1/token?grant_type=password" \
  -H "apikey: $ANON" -H "Content-Type: application/json" \
  -d '{"email":"...","password":"..."}'
```

### 13.8 Registrar

Em `docs/cobertura-fatia-4a-zero.md`: o que fechou, o que não fechou, os tempos de cold start, o
aparelho, e **o que falhou pela primeira vez**. A fatia 4a-zero fechou com dois defeitos achados
aqui e em nenhum outro lugar — o tempo limite de soquete e a permissão de rede fora do manifesto
principal —, e os dois estavam sob comentários que diziam estar resolvidos.

---

## 14. Conferir o pull do pacote, em aparelho (fatia 4a)

Como §12 e §13, isto não é medição: é o roteiro da parte que nenhum teste alcança. As decisões desta
fatia — as duas camadas de conferência, o escopo do cache, os quatro motivos de barragem, a
identidade da folha — rodam na JVM, e `docs/cobertura-fatia-4a.md` registra como cada uma foi vista
falhar. O que sobra aqui é o que só existe com servidor de verdade, disco de verdade e papel.

### 14.1 Preparar, e o que precisa existir antes

Vale tudo o que §13.1 pede, mais **duas provas publicadas na mesma organização**:

1. `prova-referencia-slice-1`, a de sempre;
2. `prova-referencia-slice-2`, a adversarial — mesmos itens, mesmas posições, mesmo gabarito, outro
   `short_id`. É essa coincidência que faz a recusa por identidade ser a **única** coisa entre a
   folha errada e uma nota plausível; com provas de estruturas diferentes, `ObjectiveScoring`
   recusaria por divergência de itens e a conferência de identidade ficaria coberta.

Publicar as duas exige `ExamPublication` contra o banco real; as fixtures dos pacotes estão em
`fixtures/prova-referencia.package.json` e `fixtures/prova-2.package.json`, e os `content_hash` são
o SHA-256 dos bytes de cada arquivo.

O executor é `PublicarFixturesNoBancoRealTest` (decisão 10 do `design.md` da 4a). Rodar **sem**
`PLATOS_PUBLISH_ORG_ID` lista as organizações visíveis e para, sem publicar — é assim que se
descobre o uuid da escola:

```bash
DATABASE_URL=jdbc:postgresql://<regiao>.pooler.supabase.com:5432/postgres DATABASE_USER=app_backend.<ref-do-projeto> DATABASE_PASSWORD=<senha> PLATOS_PUBLISH_AUTH_SUBJECT=<uid do professor> PLATOS_PUBLISH_ORG_ID=<uuid da escola> ./gradlew :apps:api:test --tests '*PublicarFixturesNoBancoRealTest*'   -Dplatos.publicar.fixtures=true
```

Publique com a conta que vai **logar no aparelho**, e na organização que ela vai escolher. Publicar
na organização pessoal enquanto o aparelho consulta a escola devolve **lista vazia**, sem erro em
lugar nenhum.

Confira antes de instalar que a rota responde:

```bash
curl -s -o /dev/null -w '%{http_code}\n' \
  -H "Authorization: Bearer $TOKEN" \
  "https://<servico>/organizations/<org>/exams"
```

### 14.2 A lista vem da API

Entre, toque em "Escanear folha". A lista tem de trazer **as duas provas**, com os títulos e os
identificadores que o `curl` acima devolveu. Não há campo para digitar identificador, e não deve
haver: prova que a consulta não devolveu não é escolhível.

### 14.3 O caso para o qual a fatia existe: a segunda vez, sem rede

1. Escolha uma prova **com rede**. Ela baixa, confere e abre a câmera.
2. Feche o aplicativo (`am force-stop`, e confira que o pid zerou).
3. **Modo avião.**
4. Reabra, escolha a mesma prova.

A câmera tem de abrir. Se ela pedir rede, o cache não está sendo consultado — é o defeito da tarefa
5.8, que com rede não aparece em lugar nenhum.

### 14.4 Os quatro motivos de barragem são quatro frases

Ainda em modo avião, escolha uma prova **nunca baixada**. A tela tem de dizer que falta rede e que
baixar uma vez resolve — e não "não foi possível", nem falha de conferência.

Os outros três se provocam assim: **pacote ausente**, publicando uma prova sem pacote (ou apontando
para um `short_id` inexistente); **conferência falha**, corrompendo o arquivo do cache (§14.5);
**versão insuficiente**, publicando um pacote com `min_renderer_version` acima do
`RendererContract.RENDERER_VERSION` do APK instalado.

### 14.5 Corromper o cache à mão

É o par em aparelho da tarefa 4.4, e nenhum teste de JVM alcança o sistema de arquivos real do
aplicativo.

```bash
adb shell run-as com.platos.android ls files/packages/<org>/
adb shell run-as com.platos.android sh -c 'echo lixo > files/packages/<org>/<hash>.json'
```

Reabra e escolha a mesma prova. Com rede, o aplicativo tem de **descartar e puxar de novo**, sem
nada na tela além do preparo normal. Sem rede, tem de barrar dizendo que falta rede — e nunca
escanear com o arquivo corrompido.

### 14.6 Sair leva os pacotes junto

Par em aparelho da tarefa 4.9, e a conferência é no **disco**, não na tela:

```bash
adb shell run-as com.platos.android ls -R files/packages/
```

Depois de sair, o diretório da organização não pode existir. Entre com o **segundo** usuário e
confira de novo antes de ele escolher qualquer prova.

### 14.7 A folha de outra prova, no papel

**Qual arquivo é a `prova-2`, e como sai o PDF.** São dois artefatos versionados:

| Arquivo | O que é |
|---|---|
| `fixtures/prova-2.json` | a definição, adversarial: mesmos itens, posições e gabarito da `prova-referencia`, só o `id` muda |
| `fixtures/prova-2.package.json` | o pacote publicado, 101 635 bytes, `content_hash` `9dccf215…3c8a` |

Para comparar: o pacote da `prova-referencia` tem `content_hash` `26612ad5…909a`. **São essas duas
linhas que distinguem as folhas** — visualmente elas são quase idênticas, e é essa semelhança que
torna o teste válido.

O PDF sai pelo mesmo caminho de §1, com o pacote escolhido por variável de ambiente:

```bash
cd apps/web
PLATOS_PACKAGE=fixtures/prova-2.package.json   npx tsx scripts/render-fixture.ts ../../build/parity/prova-2.pdf
```

A saída tem de dizer `prova prova-referencia-slice-2`. **Se disser `slice-1`, pare**: a variável não
pegou e você está prestes a imprimir a folha errada, que passaria no teste sem provar nada.

Imprima como §2 manda — 100%, sem ajuste à página.


**Fecha a 6.4b, herdada da fatia 3c**, que ficou aberta por falta de oráculo físico — e esta fatia é
quem o cria.

1. Imprima uma folha da `prova-referencia-slice-2`. Ela não precisa ser oráculo de nota; precisa
   carregar um QR que diz outra prova.
2. No aparelho, escolha a `prova-referencia-slice-1`.
3. Escaneie a folha da `slice-2`.

**O texto exato que tem de aparecer**, e é ele que fecha a tarefa — não "recusou":

```
a folha e de outra prova: o QR diz prova-referencia-slice-2,
e o aparelho carrega prova-referencia-slice-1
```

Os **dois** identificadores precisam estar na frase. Anote o texto literal da tela, e não um resumo:
é a única coisa que distingue a recusa por identidade de uma recusa por outra causa, e a fatia
inteira gira em torno dessa distinção.

**Se aparecer outra frase, o teste não passou — ele mudou de assunto.** Duas leituras erradas
comuns:

| Se a tela disser | O que aconteceu |
|---|---|
| algo sobre **itens divergentes** ou variante | a `prova-2` deixou de ser adversarial; alguém a regravou de outra definição e a identidade nunca foi consultada |
| algo sobre **não conseguir ler a folha** | o pipeline parou antes do QR; é problema de captura, e não de identidade — repita com mais luz |
| **uma nota** | a conferência de identidade não rodou. Pare e avise: é o defeito que a tarefa 8.3b existe para impedir, e ele produz 40 de 40 |

**Não pode sair nota**, e é por isso que as duas provas têm os mesmos itens: sem a conferência de
identidade, esta folha seria apurada contra o gabarito errado e produziria exatamente **40 de 40,
sem pendência** — indistinguível de uma folha certa e perfeita.

**Rede:** o pull precisa de rede **uma vez**, no passo 2, para baixar a `slice-1`. O escaneamento do
passo 3 é local e pode ser feito em **modo avião** — e é assim que vale mais, porque a mesma sessão
fecha §14.3 (a segunda vez sem rede) e esta. Anote o horário da primeira listagem: contra o Render,
um serviço frio leva de 40 a 60 s para responder (medido: 43 s em 2026-09-04), e essa espera é
esperada, não defeito.

Vale o aviso de §12.5, agora do outro lado: a folha de teste de impressão **não serve** aqui — ela
não tem região do mesmo tipo, e a recusa viria do estágio errado.

**Pré-requisito, e ele ainda não está satisfeito:** o passo 2 exige que a API **com as rotas desta
fatia** esteja no ar e que as duas provas estejam publicadas (tarefa 8.2). Em 2026-09-04 o serviço
publicado ainda é o anterior a esta branch — `/me/organizations` responde 401 e
`/organizations/{id}/exams` responde **404**, que é como uma rota inexistente responde. Imprimir a
folha (§14.7, passo 1) pode ser feito desde já; escaneá-la, não.

### 14.8 O que ainda não é produzível

Folha cuja **variante** o pacote não declara. `LayoutEngine.qrPayloadOf` recebe `examId` e índice de
região, e nada mais: a variante sai vazia de toda folha impressa até a fatia 7 criar variantes de
verdade. O cenário está coberto em JVM (`IdentidadeDaFolhaTest`), e o que falta é o papel — não o
código.

### 14.9 Registrar

Em `docs/cobertura-fatia-4a.md`, e no formato de §12.7 e §13.8: o que foi conferido, o que falhou na
primeira tentativa, e o que a conferência revelou que nenhum teste de JVM tinha visto. Registre
também o tempo do pull de ~100 KB contra o serviço real, incluindo cold start, ao lado do
`TEMPO_LIMITE_DE_PEDIDO_MS` de 90 s que a 4a-zero fixou — é a primeira chamada desta base grande o
bastante para dizer algo sobre ele.
