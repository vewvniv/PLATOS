## Context

O motivo está na proposta (Why). Aqui fica só o estado atual que molda o desenho, com o tipo de cada
afirmação (P6).

- **A região discursiva de hoje:**
  - quatro marcadores de `CaptureGeometry.MARKER_SIDE` (14 mm) nos cantos da coluna;
  - QR de 14 mm centrado, começando na linha dos centros;
  - moldura abaixo do QR;
  - quadrilátero de referência pelos centros dos marcadores (`LayoutEngine.emitEssayRegion`).

  A faixa fixa é `TOP_BAND` = 23 mm mais `BOTTOM_CLEARANCE` = 16 mm (`EssayGeometry.kt`). *Conferido
  por leitura.*
- **A moldura mede `Σ expected_lines × 8,6 mm`** (`QuestionBlocks.kt:170`), e a pauta é `DrawRect`
  preto de altura zero e traço 0,15 mm. *Conferido por leitura.*
- **O JSON canônico escreve até os nulos** (`LayoutMap.json`: `encodeDefaults = true`,
  `explicitNulls = true`). Um campo novo numa primitiva existente aparece em **todo** mapa que a use.
  Foi o que o ADR-0014 viveu com `params_hash`. *Conferido por leitura.*
- **A validação recusa preenchimento acima de 80‰** (`FLAT_TONE_CEILING`), e o retângulo de traço
  não tem tom. Nada hoje emite linha cinza. *Conferido por leitura.*
- **A paridade mede traço pela soma da escuridão**, sem piso, na faixa do contorno. Ela exige de 0,5
  a 1,5 vez a área declarada e concordância de 0,10 entre os documentos (`compare.mjs`,
  `strokeInkIn`). Uma linha a 30% renderizada certa daria cerca de 0,3 da área, e **reprovaria** na
  forma atual. *Conferido por leitura; o número é aritmética, e não medição.*
- **`min_renderer_version` é uma constante global** (`LayoutMap.MIN_RENDERER_VERSION = 1`), e os
  dois renderizadores estão em `RENDERER_VERSION = 1`. *Conferido por leitura.*
- **Na `main`, o `RegionDetector` conta os ArUcos da página inteira** (corrigido na tarefa 1.1 da
  5b-1, não mergeada). A folha de teste, as fotos `corpus-3b-teste-*` e três testes instrumentados
  dependem de a página da folha de teste ter só os quatro marcadores dela. *Conferido por leitura e
  por `grep` em `apps/android/src/androidTest`.*
- **Nenhuma prova com discursiva está em produção**, *lido no banco pelo mantenedor em 2026-09-24*,
  e não relido.

## Goals / Non-Goals

**Goals:**
- A região discursiva que o mantenedor aprovou na prévia de 2026-09-25, emitida pelo motor e
  desenhada igual nos dois renderizadores. A paridade vê a pauta cinza.
- A prova só objetiva sai **idêntica byte a byte**, e isso é medido.
- O tom e a espessura da pauta decididos em papel, com critério registrado antes.

**Non-Goals:**
- Ler a região nova no aparelho (5b-1 atualizada).
- Largura de página, faixas e redistribuição (mudança de paginação, ADR-0017 e ADR-0019).
- Cabeçalho, gabarito compacto e folha de teste (decisão 9).
- Imagem de enunciado.

## Decisions

### 1. A geometria da região

Com `L` a borda esquerda, `W` a largura (a coluna, 87 mm), `T` o topo da região e `n` o número de
linhas:

| Elemento | Posição e medida |
|---|---|
| ArUco `4k` | `(L, T)`, lado 11,2 mm = 7 módulos de 1,6 mm |
| QR | `(L + W − 14, T)`, 14 mm, 29 módulos de 0,482 mm, **o mesmo de hoje** |
| Moldura | de `x = L` a `L + W`, topo em `T + 16`, altura `7n`, traço 0,22 mm **para dentro** |
| Pauta | `n − 1` linhas em `T + 16 + 7i`, de `L + 1` a `L + W − 1`, cinza (decisão 5) |
| ArUco `4k+3` | canto inferior direito da região, `(L + W − 11,2, T + H − 11,2)` |
| Altura `H` | `snap(16 + 7n + 2 + 1,6 + 11,2) = snap(30,8 + 7n)` |

- **Os 16 mm do topo** são o QR (14) mais 2 mm de zona de silêncio dele: 4 módulos de 0,482 mm dão
  1,93 mm, e a zona de silêncio do marcador, de 1,6 mm, não basta para o QR. Por isso as duas zonas
  ficam separadas.
- **Os 2 mm abaixo da moldura** são a folga da tinta que desce da última linha: a perna do "g", do
  "p" e do "q".
- **A folga da grade** (o `snap`) cai entre a moldura e o marcador de baixo, e entra no recorte.
- **Marcador de 11,2 mm:** reduzido 5%, dá 10,64 mm, acima do mínimo de 10 mm do §7 (ADR-0001). Os
  10,5 mm dariam 9,98 mm.
- **O ganho, calculado sobre o código e não medido.** A pauta de uma linha na coluna passa de 71 mm
  para 85 mm.

  | Linhas | Hoje | Nova |
  |---|---|---|
  | 4 | 75 mm | 60 mm |
  | 5 | 84 mm | 66 mm |
  | 6 | 93 mm | 75 mm |
  | 8 | 108 mm | 87 mm |

- **A decisão 5 da 5a é atualizada, e não trocada.** Ela escolheu 14 mm por ser "o único tamanho com
  detecção medida" e previu reabrir "com número". O fato novo é a decisão do mantenedor sobre o custo
  em papel. A razão original continua certa, e é por isso que a detecção de 11,2 mm é **medida** na
  4.1 retomada da 5b-1, com regra de parada: se as fotos não o detectarem, o marcador volta a 14 mm e
  o resto desta mudança fica.
- **Alternativa descartada: moldura recuada da borda,** para dar folga horizontal no recorte. O
  esboço do mantenedor alinha a moldura à borda externa dos marcadores. Uma letra que passe da borda
  direita sai da coluna de qualquer jeito.

### 2. O retângulo de referência é o externo dos dois marcadores

`quad_x`, `quad_y`, `quad_width` e `quad_height` da região discursiva vão do canto externo superior
esquerdo do `4k` ao canto externo inferior direito do `4k+3`. Esse retângulo é a própria região.

- **Por que não pelos centros, como no gabarito:** o QR encosta na borda direita, e o centro do
  `4k+3` fica 5,6 mm para dentro dela. O QR ficaria fora de `[0,1]`, e a validação o recusaria.
- **A captura não precisa de regra por tipo.** O retângulo é declarado em papel, e a posição de cada
  canto de marcador vem do `DrawAruco` (`x`, `y`, `side`). A 5b-1 atualizada monta a homografia com
  esses pontos declarados e normaliza pelo retângulo declarado, sem saber se ele passa por centro ou
  por canto. O gabarito continua no caminho dele, dos centros exatos com conferência nos cantos, que
  não muda.

### 3. A pauta é uma primitiva nova, `line`, e não um campo novo no retângulo

`DrawLine` (serializado `"line"`) tem os campos `id`, `x1`, `y1`, `x2`, `y2`, `stroke` e `tone`. O
`tone` é nulo para preto, como no texto. Não há arremate além das extremidades (*butt*).

- **Por que não `stroke_tone` em `DrawRect`:** o JSON canônico escreveria `"stroke_tone":null` em
  todo retângulo de todo mapa. O golden da prova de referência mudaria de bytes, o `content_hash`
  também, e a quebra alcançaria provas que não têm nada a ver com esta mudança. É a lição do
  ADR-0014.
- **Por que não retângulo preenchido fino:** a validação e a spec de `print` tratam preenchimento
  como tinta chapada, com teto de 8%. Abrir exceção "retângulo fino não é chapado" seria uma regra
  por dimensão, que ninguém lê no mapa.
- **Por que dois pontos, e não "linha horizontal":** é a primitiva que os dois renderizadores já têm
  (`drawLine` no `pdf-lib` e no `Canvas`). Restringir a horizontal seria uma regra arbitrária.
  - **A medição da paridade é que se restringe:** ela mede linha alinhada aos eixos, e **recusa**
    com erro uma linha inclinada, em vez de ignorá-la.
  - O motor só emite linha horizontal.
- **A moldura continua `DrawRect` preto.**

### 4. A versão mínima de renderizador passa a ser calculada por mapa

`MIN_RENDERER_VERSION` deixa de ser uma constante aplicada a todo mapa:
- um mapa com `line` declara 2;
- um mapa sem `line` declara 1.

Os dois renderizadores passam a `RENDERER_VERSION = 2`.

- **Por que por mapa:** o spec de versionamento diz "a versão mínima exigida para desenhá-lo", e um
  mapa objetivo não precisa desenhar linha. Subir globalmente mudaria o golden objetivo por um
  número sem consumidor (P18), e quebraria a guarda da decisão 8.
- **A regra mora num lugar só**, junto das primitivas (P28). Motor e folha de teste chamam a mesma
  função.

**Atualizado ao aplicar, em 2026-09-25, por decisão do mantenedor.** O texto acima não previu um
consumidor da constante. `tools/parity/renderizador.mjs`, passo do CI desde a ETAPA 7.1, lê
`LayoutMap.MIN_RENDERER_VERSION` como o registro do domínio e exige **igualdade** com os dois
renderizadores. Sem a constante, a guarda sai com `2`; com ela em 1 e os renderizadores em 2, sai com
`1`. *Conferido por leitura* (`renderizador.mjs`, a lista `REGISTROS`); nenhuma tarefa a cobria, e o
CI da 7.4 ficaria vermelho.
- **O registro do domínio passa a ser a versão mais alta que o motor pode exigir:**
  `LayoutMap.LINE_RENDERER_VERSION = 2`, literal, ao lado de `BASE_RENDERER_VERSION = 1`. A função por
  mapa escolhe entre as duas, e continua sendo o único lugar da regra.
- **A igualdade continua sendo a propriedade certa.** O que o motor pode exigir no máximo é o que cada
  renderizador desenha. Um renderizador que suba sozinho continua reprovando, que é a direção
  silenciosa que a guarda existe para pegar.
- **`renderizador.mjs` passa a ler `LINE_RENDERER_VERSION`.** Quem acrescentar a próxima capacidade
  sobe os renderizadores, e a guarda reprova até ler o registro novo: em voz alta, e não em silêncio.
- **A razão original desta decisão continua certa.** Muda o nome do registro que a guarda lê, e não a
  regra por mapa. Tarefa 4.3.

### 5. O tom e a espessura da pauta: provisórios agora, decididos no papel

**Valor provisório:** tom 300‰, traço 0,2 mm. É o que a prévia de 2026-09-25 usou e o mantenedor
aprovou na tela. **Tela não é papel:** numa impressora laser, cinza é retícula, e uma linha fina e
clara pode sair pontilhada.

**Critério, registrado aqui, antes da impressão (ADR-0007).** Na folha de `tok-a` impressa a 100% na
impressora do mantenedor, a pauta é **aprovada** quando todas estas valem:
1. **Contínua:** cada linha aparece contínua, de ponta a ponta, sem falha visível a olho nu a cerca
   de 30 cm.
2. **Clara:** cada linha é visivelmente mais clara que a moldura e que o texto do enunciado.
3. **Guiando:** o mantenedor escreve três linhas sobre ela, e a letra fica apoiada na pauta, e não
   ao lado dela.

**Os marcadores**, na mesma folha, seguem o critério da folha de teste: completos, com a borda
fechada, sem traço na zona de silêncio.

**Se reprovar:** o tom sobe para 400‰, depois 450‰. Depois, o traço sobe para 0,3 mm a 450‰. Cada
tentativa é uma regravação de golden com paridade e fidelidade na mesma sessão (P23), e uma impressão
nova. **Não se passa de 450‰**, porque "abaixo do teto" de 500‰ é o que faz a pauta ser decoração
(ADR-0010).

**Regra de parada:** se nenhuma tentativa aprovar, a mudança **para**. O gatilho do ADR-0016 diz o
que fazer, e o teto da região não é afrouxado para a pauta caber (P11).

**Resultado, em 2026-09-25:** aprovado na primeira tentativa, a 300‰ com 0,2 mm, nos quatro critérios,
na impressora do mantenedor. Nenhum degrau foi usado. O registro por critério e a digitalização estão
na cobertura, seção 6.1.

### 6. O contrato: `answer_lines` e `answer_width` na `Question`, e não no pacote

A `Question` ganha:
- `answer_lines: Int?`;
- `answer_width: AnswerWidth?`, com os valores `column` e `page`.

Os dois são nulos na objetiva, e obrigatórios na discursiva. `page` é recusada com mensagem própria,
que diz que a largura de página depende da paginação em faixas, até essa mudança existir.

- **Por que o campo de largura entra agora, se só `column` passa:** o ADR-0017 diz que não há valor
  padrão. Sem o campo, toda discursiva seria "coluna" por omissão, que é um padrão silencioso. Com
  ele, a definição declara, e a recusa de `page` é explícita.
- **Por que fora do `PackageItem`:** nenhum consumidor do pacote precisa deles. O resultado das duas
  escolhas já está na geometria da região (P18). Isso também mantém o pacote da prova objetiva
  intocado.
- **`expected_lines` fica na rubrica**, com as recusas de hoje, e deixa de ser lido pelo layout.

### 7. A paridade mede a linha pela tinta esperada

`compare.mjs` ganha `lineTargetsOf`. A faixa de medição vai `stroke/2 + STROKE_SLACK_UM` para cada
lado da linha, e a tinta **esperada** é `comprimento × stroke × tone/1000` (tom nulo conta como
1000).

Presença e concordância usam os mesmos `STROKE_PRESENCE_MIN` (0,5), `STROKE_PRESENCE_MAX` (1,5) e
`STROKE_TOLERANCE` (0,10). **Nenhum número novo é escolhido** (P11): esses três foram fixados na 5a
antes da primeira medição.

- **Por que isso pega os dois defeitos:**
  - linha omitida mede cerca de 0;
  - linha preta no lugar de 300‰ mede cerca de 3,3 vezes o esperado, e no pior caso admitido (450‰)
    cerca de 2,2. As duas passam do teto de 1,5.

  *Aritmética, e não medição.* A medição é o "ver falhar" da tarefa.
- **`strokeTargetsOf` continua como está**, para a moldura.

### 8. A prova só objetiva sai idêntica byte a byte

`prova-referencia.layout.json`, `prova-referencia.package.json`,
`prova-referencia.turma.package.json`, `prova-2.package.json` e `folha-de-teste.layout.json` têm o
`sha256` anotado antes do primeiro commit de código, e **o mesmo depois** da regravação.

É a guarda mais forte desta mudança. Se algo do contrato novo vazar para a prova objetiva (campo novo
serializado, versão global, ordem de primitiva), é aqui que aparece. **Ver falhar:** com a versão
mínima global em 2, o `sha256` de `prova-referencia.layout.json` muda.

### 9. A folha de teste fica, e a lacuna vai para o §16

Pela razão do Context: um marcador a mais na página da folha de teste derruba, na `main`, os testes
que a leem, até o filtro da 5b-1 estar lá.

A lacuna entra no §16, **antes do código** (P27), como "A folha de teste de impressão não aprova a
região discursiva que a prova imprime":
- ela aprova marcador de 14 mm, e a discursiva passa a ter 11,2 mm;
- ela não tem pauta cinza.

A fatia-limite é `5b`, e o dono é o mantenedor. **O que encarece depois:** uma escola aprova a
impressora pela folha de teste, e a discursiva sai com marcador ou pauta que aquela folha nunca
conferiu.

### 10. A regra de parada vale sobre toda mutação

É a mesma da 5a (decisão 13). Se o conjunto de testes que caem divergir do previsto, **pare**,
escreva o real ao lado do previsto e diga o que significa, lendo a mensagem, e não a contagem.

## Risks / Trade-offs

- **[O marcador de 11,2 mm não ser detectado em foto]** → a detecção é medida na 4.1 retomada da
  5b-1, com regra de parada: volta a 14 mm. Até lá, **não é mitigado, é conhecido** (P8).
- **[A pauta cinza sumir na impressora de escola]** → a decisão 5 decide no papel, com um degrau de
  tentativas e uma regra de parada. Uma impressora só não é todas as impressoras. A folha de teste,
  que é o instrumento para isso, fica para a linha nova do §16 (decisão 9).
- **[A 5b-1 cai ao ser rebaseada sobre esta mudança]** → esperado. O código dela exige quatro
  marcadores (`declared.size != 4`), e os testes dela leem a fixture discursiva. A atualização da
  5b-1 (`/opsx:update`) muda essa regra para "os marcadores que a região declara". Nada disso é desta
  mudança.
- **[Quatro testes de domínio montam discursiva em código]** → eles passam a declarar os dois
  campos. A lista prevista de testes vermelhos no commit de contrato inclui esses quatro.
- **[Versão mínima 2 nas provas com discursiva]** → um renderizador na versão 1 recusa imprimir, como
  o D24 manda. Não há APK entregue nem renderizador fora do repositório. Isso não impede nada hoje.

## Migration Plan

Não há dado a migrar. Nenhum pacote com discursiva foi publicado, e a prova objetiva não muda de
bytes (decisão 8). Voltar atrás é reverter os commits, e a fixture discursiva volta à região de quatro
marcadores.

## Open Questions

Nenhuma que altere as specs, a abordagem ou as tarefas.
