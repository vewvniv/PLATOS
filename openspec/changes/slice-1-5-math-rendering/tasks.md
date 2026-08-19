## 1. Base de comparação

- [x] 1.1 Registrar os números atuais antes de tocar em qualquer coisa: paridade, fidelidade nos dois PDFs, contagem da suíte e o hash do golden. Resultado: valores anotados nesta tarefa — a fatia altera o golden de propósito, então a base precisa existir antes.

  Medido em 2026-08-16, na máquina de desenvolvimento, com o emulador `platos-atd34` (API 34, `aosp_atd`, x86_64) — os mesmos números do CI, e não uma leitura de segunda mão.

  | Grandeza | Valor |
  |---|---|
  | `fixtures/prova-referencia.layout.json` | sha256 `83386688180c5c331b8da4998bba70dc0a3d25e03d8941e00ba807610ca30796`, 47 279 bytes |
  | `fixtures/prova-referencia.json` | sha256 `9b08e56588b4a8d657cc84172a677c3b59cc2e5809e638bd74135c711dddee8f` |
  | Paridade web × Android | 116 de 116 elementos, 3 páginas, maior divergência **0,041 mm** em `r0-m1`, tolerância 0,3 mm |
  | Fidelidade do PDF web | 32 verificações, maior desvio **0,039 mm** em "marcador 2: borda superior" |
  | Fidelidade do PDF Android | 32 verificações, maior desvio **0,017 mm** em "bolha r0-bq01-C: diametro externo" |

  Contagem da suíte, por alvo:

  | Alvo | Testes |
  |---|---|
  | `packages/domain` JVM | 98 |
  | `packages/domain` Node/JS | 94 |
  | `packages/domain` Android (unitário) | 94 |
  | `apps/android` (unitário) | 6 |
  | `apps/api` | 69 |
  | `apps/web` (vitest) | 7 |
  | Android instrumentado (emulador) | 2 |

  Os quatro testes a mais no JVM são os de `jvmTest`, que não existem nos outros alvos: `QrDumpTest`, `IntegerArithmeticGuardTest` e `GoldenWriterTest`.

## 2. Conversão em `tools/math`

- [x] 2.1 Criar `tools/math` com MathJax fixado por lock, convertendo LaTeX e MathML em SVG (D-1.5.2). Resultado: uma fórmula de entrada produz SVG determinístico, byte a byte igual entre execuções.

  MathJax 3.2.2 e `@resvg/resvg-js` 2.6.2, ambos fixados em `tools/math/package-lock.json`. As duas notações são exercitadas pela fixture: onze fórmulas em LaTeX e o Bhaskara em MathML.

  Dois defeitos silenciosos apareceram aqui e viraram comentário no código, porque nenhum dos dois falha de forma visível:

  - **`AllPackages` não é uma lista, é um registro.** `packages: ['base', 'ams']` sem o import de `AllPackages.js` seleciona pacotes não registrados: o `ams` some calado e `\begin{vmatrix}` volta como "Unknown environment". Matriz e sistema linear — as duas estruturas que D-1.5.6 põe na fixture justamente para exercitar a grade — não compilavam.
  - **`noundefined` e `noerrors` desenham o erro na folha.** Com eles ligados, `\newcommand` e comando indefinido atravessavam a conversão sem ruído e sairiam impressos como texto vermelho. São exatamente a "folha silenciosamente degradada" que a spec proíbe, e apagavam o `merror` que a detecção de falha usa. Saíram; a lista ativa é só `base` e `ams`.

- [x] 2.2 Derivar o raster a 600 dpi a partir do SVG e extrair largura e altura em micrômetros (D-1.5.1). Resultado: a mesma fórmula produz sempre os mesmos bytes de imagem e as mesmas dimensões.

  O rasterizador é `@resvg/resvg-js`, decidido em D-1.5.8 depois de o mupdf — que era o candidato sem tecnologia nova — recusar SVG: `cannot find document handler for file type: 'image/svg+xml'`.

  **Quem manda na dimensão é o pixel.** O alvo em micrômetros sai do `viewBox`, vira contagem de pixels, e a dimensão declarada volta a ser derivada dessa contagem. Se o micrômetro mandasse, a caixa do mapa não cairia na grade de pixels do PNG e cada renderizador teria de reescalar por conta própria — o que a spec de `print` proíbe. `a dimensao declarada e a do raster a 600 dpi` afirma isso para as doze.

- [x] 2.3 Emitir o manifesto que a fixture consome — identificador, dimensões e caminho do raster. Resultado: entrada pura para o Layout Engine, sem que ele precise abrir imagem.

  `fixtures/formulas.manifest.json`, gerado por `tools/math/build.mjs`. Carrega também o `body_size_um` usado na composição, para que o KMP possa afirmar que a fórmula foi composta no mesmo corpo do texto em vez de a divergência aparecer na folha.

  | Fórmula | px | µm | PNG |
  |---|---|---|---|
  | `f-aritmetica` | 611 × 79 | 25 866 × 3 344 | 7 079 B |
  | `f-fracoes` | 246 × 163 | 10 414 × 6 900 | 3 922 B |
  | `f-raizes` | 430 × 87 | 18 203 × 3 683 | 6 741 B |
  | `f-potencias` | 487 × 85 | 20 616 × 3 598 | 5 125 B |
  | `f-trigonometria` | 615 × 76 | 26 035 × 3 217 | 8 172 B |
  | `f-logaritmo` | 379 × 74 | 16 044 × 3 133 | 5 723 B |
  | `f-vetor` | 1040 × 108 | 44 027 × 4 572 | 13 228 B |
  | `f-somatorio` | 588 × 222 | 24 892 × 9 398 | 10 813 B |
  | `f-matriz2` | 211 × 190 | 8 932 × 8 043 | 3 992 B |
  | `f-matriz3` | 416 × 301 | 17 611 × 12 742 | 14 275 B |
  | `f-sistema` | 425 × 190 | 17 992 × 8 043 | 9 056 B |
  | `f-bhaskara` | 727 × 185 | 30 776 × 7 832 | 10 774 B |
- [x] 2.5 Embutir os rasters para os três alvos pelo mesmo caminho da fonte embarcada: task de build gerando código para o KMP e assets no módulo Android (D-1.5.5). Resultado: os dois renderizadores desenham comprovadamente os mesmos bytes, e não bytes que coincidem por configuração.

  `EmbedRastersTask` em `buildSrc`, no mesmo molde de `EmbedFontTask`: o PNG versionado vira código Kotlin em `commonTest`, com o `sha256` ao lado. O Android instrumentado lê dos assets, o web lê do disco, e `FormulaRasterTest` afirma nos três alvos que os bytes são os que o manifesto declara — inclusive a assinatura PNG, que cairia primeiro se o transporte por Base64 corrompesse o binário.

  Os dois consumidores resolvem a referência **pelo manifesto**, e não por convenção de nome: `apps/web/scripts/formulaAssets.ts` e `formulaRasters()` no teste instrumentado leem o mesmo arquivo para decidir qual PNG pertence a qual referência.

  **Um defeito de transporte foi encontrado aqui.** O golden passou de 47 KB para 68 KB ao ganhar fórmulas, e o build caiu com `UTF8 string too large`: `EmbedFixturesTask` expunha o arquivo como `const val`, e `const val` exige constante de compilação, então o compilador dobrava a soma dos pedaços num literal só — acima do teto de 65 535 bytes do pool de constantes da JVM. Quebrar em pedaços não adiantava enquanto o todo continuasse `const`. Agora é uma `List<String>` juntada em tempo de execução.
- [x] 2.4 Verificar que a conversão é reprodutível: rodar duas vezes e comparar bytes. Resultado: saída idêntica, ou a causa da variação identificada e eliminada.

  `tools/math/test/convert.test.mjs`, 15 testes, todos verdes. Três cobrem esta tarefa: conversão repetida byte a byte, conversão em ordem inversa, e o manifesto versionado contra o que a conversão produz agora. `node build.mjs --check` faz a mesma conferência para o CI, sem escrever nada.

  **Uma variação foi encontrada e eliminada, não apenas procurada.** Com um documento TeX e um MathML vivos ao mesmo tempo, o registro global de macros do MathJax 3 fazia o TeX perder a configuração de pacotes — o resultado dependia da ordem de construção dos documentos. A correção é um documento novo por conversão, e o teste `converter na ordem inversa da o mesmo resultado` é o que impede a volta.

  **Visto falhar:** invertido um byte perto do fim de `f-matriz3.png`, `o manifesto versionado bate com a conversao` foi de verde a vermelho (14 passam, 1 falha) e voltou ao verde com o arquivo restaurado.

## 3. Domínio: entrada e caixa da fórmula

- [x] 3.1 Estender a definição de prova para aceitar fórmula em bloco com dimensões declaradas e referência ao recurso (D-1.5.3). Resultado: entrada versionada em JSON, sem dependência de banco ou rede.

  `BlockFormula(reference, width, height)` em campo próprio de `Question` — e não dentro de `assets`, que continua sendo o lugar do que a capacidade **não** desenha. Fórmula em bloco ocupa linha inteira e nunca se mistura ao texto corrido, então nunca compartilhou natureza com recurso embutido no enunciado.

- [x] 3.2 Estreitar a recusa de entrada não suportada: fórmula em linha, imagem de enunciado e discursiva continuam recusadas; fórmula em bloco passa. Resultado: cobre "Fórmula em linha ainda é recusada" e "Fórmula em bloco é aceita".

  Fórmula em linha ganhou mensagem própria, separada de "recurso não suportado": é o caso predominante das exatas e vai bater nessa barreira com frequência, então quem lê precisa descobrir que ela é a fatia 1.6 e não uma limitação permanente. Dimensão não positiva também é recusada — caixa de área zero desenhada em silêncio produziria a questão sem a fórmula que o enunciado menciona.

- [x] 3.3 Recusar fórmula mais larga que a coluna, com erro identificável. Resultado: cobre "Fórmula mais larga que a coluna".

  `formula mais larga que a coluna impede a emissao do mapa`, com `formula com exatamente a largura da coluna e aceita` fixando a borda — sem o segundo, um `>=` no lugar do `>` passaria despercebido.

- [x] 3.4 Incluir a fórmula no bloco indivisível da questão, entre enunciado e alternativas, com altura arredondada à grade de 3 mm. Resultado: cobre "Fórmula reserva espaço próprio", "Fórmula não é reescalada" e "Ordem dentro do bloco".

  Quem sobe para a grade é o espaço **reservado**; as dimensões desenhadas continuam as declaradas, e a sobra vira respiro. Uma fórmula de 7 mm reserva 15 mm (7 + 3 + 3, arredondado) e é desenhada com 7 mm.

  **Visto falhar:** trocado `height = formula.height.raw` por `formula.reserved.raw` no `LayoutEngine` — isto é, fazendo o engine esticar a fórmula para preencher a grade —, `a caixa desenhada tem exatamente as dimensoes declaradas` e `formula em bloco e aceita e o mapa inclui a caixa dela` ficaram vermelhos. Revertido, 15 de 15 verdes.

- [x] 3.5 Testar que o layout não depende do conteúdo matemático, só das dimensões. Resultado: cobre "Layout não depende do conteúdo matemático".

  Dois ângulos: blocos de mesma altura para conteúdos diferentes, e — mais forte — o `LayoutMap` inteiro idêntico byte a byte quando só a referência muda.

- [x] 3.6 Testar que a fórmula não se separa do enunciado na paginação. Resultado: cobre "Fórmula não se separa do enunciado".

  24 questões com fórmula de 30 mm, para forçar várias quebras de coluna; para cada uma, enunciado, fórmula e alternativas na mesma página e na mesma coluna.

- [x] 3.7 Emitir a primitiva de imagem no `LayoutMap` para a fórmula posicionada. Resultado: o mapa declara posição, dimensões e referência do recurso.

  `DrawImage` com id `q<questao>-f`. `LayoutMapValidation` passou a conferir a primitiva: referência não vazia, dimensão positiva e caixa dentro da página — imagem é a única primitiva cujo desenho depende de recurso externo, e sem referência ela vira buraco na folha que só aparece depois de impresso.

## 4. Renderizadores

- [x] 4.1 Desenhar `DrawImage` no renderizador web, a partir dos bytes referenciados, sem reamostrar nem reescalar (D-1.5.1). Resultado: cobre "Imagem ocupa a caixa declarada" no web.

  `renderLayoutMap` ganhou um terceiro parâmetro, `imageBytes`, no mesmo molde dos bytes da fonte: **quem resolve a referência é quem chama**, e não o renderizador procurando arquivo do seu jeito. É disso que D-1.5.5 depende. Embute uma vez por referência, e não por ocorrência.

- [x] 4.2 Desenhar `DrawImage` no renderizador Android, com a mesma regra de conversão de unidade dos demais elementos. Resultado: cobre o mesmo cenário no Android.

  `canvas.drawBitmap` para um `RectF` construído com o mesmo `pt()` dos demais elementos. `Paint` com `isFilterBitmap = false` e `isDither = false`: o raster já vem no tamanho exato da caixa, então não há reamostragem a suavizar, e ligar o filtro faria o Android decidir sozinho pixels que o lado web não decide.

- [x] 4.3 Falhar explicitamente quando os bytes referenciados não estiverem disponíveis, nos dois renderizadores. Resultado: cobre "Bytes ausentes", sem documento parcial.

  Web: três testes — nenhum recurso, um recurso faltando, e nada entregue na falha. Android: a guarda em teste local de JVM, mais `recusaImprimirQuandoOsBytesDaFormulaFaltam` no `PdfDocument` real do emulador. A guarda também recusa referência *diferente* da declarada: ter alguma imagem disponível não basta, senão a folha sairia com a fórmula de outra questão.

  **Este teste achou um defeito real.** No emulador, a falha chegava como `IllegalStateException: Current page not finished!` — o `close()` no `finally` lançava por haver página aberta e **substituía** a exceção que dizia o que tinha acontecido. Quem chamasse receberia um erro de estado do `PdfDocument` no lugar de "faltam os bytes da fórmula tal", e a spec exige falha identificável. Corrigido com `finishPage` em `finally` próprio; `writeTo` só depois de todas as páginas, então o destino continua vazio.

- [x] 4.4 Confirmar que nenhum dos dois interpreta LaTeX, MathML ou SVG. Resultado: cobre "Renderizador não tipografa matemática", com a mesma evidência estrutural já usada para medição de texto.

  Os dois módulos são varridos por `latex`, `mathml`, `mathjax`, `svg` e `tex`, e nenhum aparece. O par positivo é o que impede o teste de passar por vacuidade: o web precisa chamar `embedPng`, o Android `decodeByteArray`.

## 5. Fixture e golden

- [x] 5.1 Acrescentar à fixture de referência questões com fórmula cobrindo a educação básica inteira (D-1.5.6): aritmética, frações, raízes, potências e subscritos, trigonometria, logaritmos, vetores e módulos, somatórios simples, matriz 2×2 e 3×3, e sistema linear com `cases`. Resultado: a folha de referência passa a ter matemática, incluindo as estruturas verticais mais altas do currículo.
  - As verticais não são enfeite: matriz 3×3 e `cases` são as fórmulas mais altas do EM, e são elas que exercitam de verdade o arredondamento à grade e o limite de bloco que não cabe na coluna.

  Doze questões novas, `q29` a `q40` — as onze categorias mais Bhaskara. Foram **acrescentadas**, e não retrofitadas nas 28 existentes: aquelas são todas de EF e uma matriz 3×3 pendurada em "Qual e o resultado de 12 + 15?" não seria fixture, seria adereço. A prova passa de 28 para 40 questões e de 3 para 4 páginas.

  Bhaskara entra em **MathML**, e não em LaTeX, para que a segunda notação de entrada tenha um consumidor de verdade na fixture em vez de só um teste sintético.

  As duas verticais entregaram o que D-1.5.6 prometia: `f-matriz3` é a mais alta com 12 742 µm, e `f-vetor` a mais larga com 44 027 µm — contra 79 000 µm de coluna. Nenhuma da fixture excede a coluna, e `toda formula da fixture cabe na coluna` afirma isso; o cenário de recusa por largura é exercitado por fórmula sintética, que é onde ele pertence.
- [x] 5.3 Verificar que macro customizada, pacote arbitrário e TikZ são recusados pela conversão com erro identificável. Resultado: o limite de D-1.5.6 é barreira executável, e não intenção escrita.

  Sete recusas cobertas, cada uma exigindo `UnsupportedFormulaError` com o identificador da fórmula na mensagem: `\newcommand`, `\def`, `\let`, `\usepackage`, `\require`, `\begin{tikzpicture}` e `\tikz`. Mais três guardas próximas: comando indefinido, notação desconhecida e SVG sem `viewBox`.

  A barreira é dupla de propósito. A guarda de origem recusa antes do MathJax, para que `\newcommand` dê "fora do escopo" e não "comando desconhecido" — erro verdadeiro, mensagem errada, e quem lê não descobriria que o limite é deliberado. Atrás dela, a lista de pacotes reduzida a `base` e `ams` faz o próprio MathJax não conhecer o que está fora.

  **Este teste já pegou defeito real:** com `noundefined` e `noerrors` na lista de pacotes, as recusas de macro passavam — a conversão devolvia a folha com o erro desenhado em vez de falhar.
- [x] 5.2 Regravar o golden do `LayoutMap` deliberadamente e registrar aqui o antes e o depois (D-1.5.4). Resultado: a mudança do golden fica auditável, e não confundível com regressão aceita por engano.

  Regravado com `./gradlew :packages:domain:jvmTest -Dplatos.golden.write=true`. É a primeira vez que ele muda desde que foi criado.

  | | Antes | Depois |
  |---|---|---|
  | `sha256` | `83386688180c5c33…0ca30796` | `936a561caa9c2b1d…ba2d4d75` |
  | Tamanho | 47 279 bytes | 68 192 bytes |
  | Questões | 28 | 40 |
  | Páginas | 3 | 4 |
  | Bolhas na região | 112 | 160 |
  | Primitivas `image` | 0 | 12 |
  | Primitivas `text` | — | 450 |

  A mudança é consequência direta de 5.1: doze questões a mais mudam a grade de bolhas, a paginação e a folha inteira. Nada aqui é regressão — os testes de geometria da fatia 1 continuam verdes, e os números de paridade e fidelidade seguem no mesmo patamar de antes (tarefas 6.2 e 6.3).

## 6. Verificação

- [x] 6.1 Rodar a suíte completa nos três alvos. Resultado: golden novo estável byte a byte em JVM, Node e Android.

  | Alvo | Testes | Falhas |
  |---|---|---|
  | `packages/domain` JVM | 121 | 0 |
  | `packages/domain` Node/JS | 117 | 3 (pré-existentes, ver abaixo) |
  | `packages/domain` Android (host) | 117 | 0 |
  | `apps/android` (unitário) | 9 | 0 |
  | `apps/web` (vitest) | 12 | 0 |
  | `tools/math` | 15 | 0 |
  | Android instrumentado (emulador) | 4 | 0 |

  `GoldenLayoutTest` passa nos três alvos: o golden novo é estável byte a byte em JVM, Node e Android.

  **O terceiro alvo não estava rodando.** `packages/domain` só tinha `jvmTest` e `jsNodeTest`: o plugin `android.kmp.library` da subida para AGP 9 não cria o teste de host por padrão, e o build avisava — "android host tests are not enabled" — mas seguia verde. Entre aquela subida e esta fatia, "o mesmo valor afirmado em três runtimes" valia em dois. `withHostTest {}` religou o alvo, e o CI agora pede `testAndroidHostTest` pelo nome porque `./gradlew build` não o alcança. Foi preciso religar para esta tarefa poder ser cumprida como está escrita.

  **As 3 falhas em Node são pré-existentes e alheias a esta fatia** — confirmado rodando `jsNodeTest` na árvore limpa via `git stash`. Ver a seção "Fora do escopo, encontrado no caminho" no fim deste arquivo.

- [x] 6.2 Medir fidelidade do documento nos dois PDFs, agora com fórmula. Resultado: dentro de 0,05 mm, comparado com a base da tarefa 1.1.

  | | Base (1.1) | Agora |
  |---|---|---|
  | Verificações | 32 | **80** |
  | Web, maior desvio | 0,039 mm | 0,039 mm, em `formula qq36-f: borda superior` |
  | Android, maior desvio | 0,017 mm | 0,022 mm, em `formula qq32-f: altura da tinta` |
  | Tolerância | 0,05 mm | 0,05 mm |

  As 48 verificações novas são 4 por fórmula, contra um **oracle independente**: o PNG versionado, que não passa por nenhuma linha de código do renderizador. Mede-se a caixa de tinta dentro do PNG, mapeia-se para a caixa declarada na página, e compara-se com a tinta que o documento realmente traz ali. Conferir a caixa *declarada* contra a tinta não serviria — o `viewBox` do MathJax inclui folga tipográfica, então a tinta é sempre menor que a caixa, por uma margem que depende da fórmula.

  A ferramenta só olhava a página 0. As fórmulas caem nas páginas 2 e 3, então na primeira execução ela devolveu as mesmas 32 verificações de sempre e disse "fidelidade OK" sem ter medido fórmula nenhuma.

- [x] 6.3 Medir paridade web × Android com fórmula na folha, confirmando que a rasterização de verificação segue em cinza de 8 bits com antialiasing (D-1.5.7). Resultado: cobre "Fórmula equivalente entre renderizadores" dentro de 0,3 mm, sem quantizar antes de medir.

  | | Base (1.1) | Agora |
  |---|---|---|
  | Elementos | 116 (4 + 112) | **176** (4 marcadores + 160 bolhas + 12 fórmulas) |
  | Páginas | 3 | 4 |
  | Maior divergência | 0,041 mm em `r0-m1` | **0,042 mm em `qq31-f`** — uma fórmula |
  | Tolerância | 0,3 mm | 0,3 mm |

  `DeviceGray` a 600 dpi com antialiasing, sem quantizar antes de medir, como D-1.5.7 exige. Que o pior elemento da folha seja uma fórmula e ainda assim esteja em 0,042 mm — cerca de um pixel a 600 dpi, o piso da própria rasterização — é o resultado que D-1.5.1 previa: os dois lados recebem os mesmos bytes.

- [x] 6.4 Provar que a verificação continua capaz de falhar: deslocar a caixa da fórmula de propósito e confirmar que paridade e fidelidade acusam, com o elemento e a distância. Resultado: as duas saem com código 1; reverter em seguida.

  `render-shifted.ts` passou a deslocar também uma fórmula. Com 0,5 mm em uma bolha, um marcador e `qq29-f`:

  | Elemento | Paridade acusa | Fidelidade acusa |
  |---|---|---|
  | `r0-m0` (marcador) | 0,500 mm | borda superior, desvio 0,542 mm |
  | `r0-bq01-A` (bolha) | 0,409 mm | centro, desvio 0,418 mm |
  | `qq29-f` (fórmula) | 0,466 mm | borda esquerda, desvio 0,480 mm |

  As duas saem com código 1; o par correto volta a sair com 0.

  **Esta tarefa pegou uma verificação incapaz de falhar, e ela era nova.** Na primeira versão, a janela de medição do centroide da fórmula era exatamente a caixa declarada. Ela **recortava** a fórmula deslocada, e a paridade lia 0,142 mm para um deslocamento real de 0,500 mm — abaixo da tolerância de 0,3 mm, com o comparador imprimindo "paridade OK". O peso de tinta dentro da janela era o que denunciava: 1 195 519 contra 1 226 245 do lado não deslocado.

  Medido com quatro folgas antes de escolher: 0 µm lê 0,142 mm; 500 µm, 1000 µm e 1500 µm leem 0,466 mm com o peso de tinta já estável. Ficou 1 mm, que é folgado o bastante para não recortar e estreito o bastante para não alcançar o texto vizinho, que está a 3 mm.

  É a terceira verificação incapaz de falhar que esta base produz, e a segunda por janela de medição mal dimensionada — a primeira alcançava o vizinho e diluía o desvio; esta recortava o próprio elemento. O CI agora roda o deslocamento deliberado a cada execução, em vez de depender de alguém lembrar.

- [x] 6.5 Registrar o peso dos rasters da fixture. Resultado: número anotado, para a fatia 2 decidir empacotamento com dado em vez de estimativa (§5).

  | | |
  |---|---|
  | 12 rasters PNG | **98 900 bytes (96,6 KiB)** |
  | Média por fórmula | 8 242 bytes |
  | Maior / menor | `f-matriz3` 14 275 B / `f-fracoes` 3 922 B |
  | 12 SVG (forma armazenada) | 67 704 bytes (66,1 KiB) |
  | PDF web / Android | 204 544 B / 221 146 B (contra 136 169 B / 146 246 B sem fórmula) |

  Para a fatia 2: **~8 KiB por fórmula a 600 dpi**. Uma prova de 40 questões toda de exatas ficaria em torno de 330 KiB de raster — o que sustenta §5 mandar imagem para o Storage por referência em vez de para dentro do JSON. A queda de 1200 para 600 dpi decidida em D-1.5.1 já cortou isso a um quarto.
- [x] 6.6 Imprimir a folha com fórmula e conferir legibilidade a olho, seguindo o protocolo. Resultado: **aprovada**, e o caminho até a aprovação é o que esta tarefa existia para produzir.

  Foram **três** impressões, e só a primeira delas era a prevista.

  | | resultado |
  |---|---|
  | 1ª | resolução **aprovada**; espaçamento **reprovado** |
  | 2ª | proximidade corrigida, aprovada com pedido de mais separação abaixo |
  | 3ª | **aprovada** |

  A resolução passou de primeira, e essa era a pergunta que a tarefa fazia: traço de fração e raiz,
  subscrito e expoente de `f-potencias`, barras de `f-matriz2`, parênteses de `f-matriz3`, e o peso
  óptico contra o texto ao redor. Os 600 dpi de D-1.5.1 estão conferidos no papel, e não só no
  argumento.

  **O que ela achou não era o que ela procurava.** O espaçamento em volta da fórmula estava
  invertido: 10,089 mm de branco acima contra 2,565 mm abaixo, então por proximidade a fórmula lia
  como pertencente às alternativas em vez do enunciado. Corrigido em D-1.5.9, na seção 8.

  Vale registrar por que nenhuma verificação automática pegou isso, porque nenhuma delas estava
  errada: a paridade compara os dois renderizadores, e os dois erravam igual, com 0,042 mm de
  divergência; a fidelidade compara o documento com o `LayoutMap`, e o documento estava fiel a um
  mapa errado; o golden compara o mapa consigo mesmo. **Defeito de julgamento tipográfico não tem
  oracle dentro do sistema** — o oracle é o olho de quem lê a folha. É o argumento para esta tarefa
  nunca ser substituída por medição, e ela acabou de pagar o próprio custo.

- [x] 6.7 Atualizar `docs/cobertura-fatia-1.md` com os cenários novos. Resultado: nenhum cenário das duas specs sem verificação.

  O documento passou a cobrir as duas fatias. Doze cenários novos, todos cobertos: 8 de `layout-engine` e 4 de `print`. Acrescentada também a tabela dos 15 testes de `tools/math`, que não sai de spec nenhuma mas é o que sustenta D-1.5.6 e D-1.5.8, e a nota sobre o terceiro alvo ter parado de rodar.

- [x] 6.8 Registrar a fatia 1.6 — matemática em linha, com `InlineBox` de largura, altura e `baseline_offset` — como próxima da lista, bloqueadora da fatia 6 e planejada para vir antes da fatia 2. Resultado: o caso predominante das exatas não fica só numa nota de design, e o tipo entra na medição de texto antes de o `ExamPackage` congelar contrato.

  Entrou como linha própria no roadmap de `ARQUITETURA-FINAL-v3.md` §15, entre a 1.5 e a 2, marcada como bloqueadora da fatia 6. O parágrafo abaixo da tabela — que já defendia duas escolhas de ordem — ganhou a terceira, com as duas razões separadas: o gatilho formal (bloquear a 6) e a data desejada (antes da 2, porque `InlineBox` entra na medição de texto e é melhor resolver isso antes de a 2 congelar os contratos do `ExamPackage`).

## 7. Fora do escopo, encontrado no caminho

Encontrado ao executar esta fatia, e registrado porque foi medido, não suposto. O primeiro item
foi corrigido depois — e o registro original dele estava errado, o que é o motivo de a correção
vir acompanhada da refutação.

### Surrogate solto: o insumo do teste era corrompido pelo gerador de código, não a medição

**Corrigido, e o diagnóstico anterior desta seção estava errado.** Ela dizia que `CodePointMeasurementTest`
falhava 3 de 5 no alvo Node/JS por causa de `FontProgram.glyphIn`, que leria `bytes.u16(at)` sem
checar limite quando o segmento do `cmap` tem `idRangeOffset != 0`. Não é isso, por três evidências:

- **A leitura é checada.** `u16` chama `u8`, que chama `require` (`FontProgram.kt:14-19`, `:40-46`).
  Fora dos limites lança `FontFormatException` com mensagem própria — nunca devolve lixo, e nunca
  devolveria valores diferentes por alvo.
- **Oracle independente sobre o TTF**, sem passar por nenhuma linha do domínio: `U+D83D` resolve
  para o glifo 0 (`.notdef`), avanço 640, os mesmos 2 145 µm do JVM. O `cmap` não diverge.
- **O artefato compilado mostrava a causa.** Em `platos-packages-domain-test.js`, o par bem formado
  saía como `'😀'` e o surrogate **solto** saía como `'?'` — um caractere que a fonte
  cobre, com avanço 416 contra os 640 do `.notdef`. 416 unidades são exatamente os 1 394 µm que o
  teste acusava.

A causa é o gerador de código do Kotlin/JS, que não emite surrogate solto dentro de um literal de
string e o substitui. Não é divergência de medição, não é defeito de produção, e **a invariante de
que o KMP é a fonte compartilhada de medição não está quebrada**: JVM, Node e Android calculam o
mesmo valor sobre a mesma entrada. O que divergia era a entrada.

A correção é construir a string a partir do code point — `Char(0xD83D).toString()` —, o que tira o
insumo do caminho do gerador. Verificado: o código emitido passa a ser `toString_0(<código>)`, sem
literal, e os três alvos ficam verdes (JVM 122, Node 118, Android host 118).

**Visto falhar, e o segundo caso é pior que o problema original.** Com o literal reposto de propósito:

| o que o gerador emitiu | avanço | as três medições | a asserção de insumo |
|---|---|---|---|
| `'?'` | 416 | **vermelhas** — o sintoma original | vermelha |
| `'�'` | 640, igual ao `.notdef` | **verdes, medindo o insumo errado** | vermelha |

O segundo caso é a explicação mais provável para o CI em Linux estar verde neste teste desde
sempre: passando sem nunca ter exercitado um surrogate solto. É a **quarta** verificação incapaz de
falhar que esta base produz, e a primeira cuja causa está fora do código do projeto.

Daí a asserção de insumo — `o insumo chega intacto ao alvo` — afirmar comprimento e code point
**antes** de medir qualquer coisa. Ela não é redundante com a construção em runtime: é o que
transforma a construção em runtime de suposição em verificação, e é a única coisa que acusa quando
a corrupção cai num caractere que mede igual.


### O terceiro alvo do `packages/domain` tinha parado de rodar

Descrito na tarefa 6.1. Religado aqui porque sem ele a 6.1 não podia ser cumprida como está escrita, mas a causa é anterior: a subida para AGP 9 trocou o plugin do módulo KMP e o teste de host deixou de ser criado, com aviso no build e nenhuma falha.

## 8. D-1.5.9 — espaçamento da fórmula, achado na impressão

A tarefa 6.6 imprimiu a folha e aprovou a resolução: traço de fração e raiz, subscrito e expoente,
barras de matriz e peso óptico contra o texto ao redor, tudo legível a 600 dpi. **Reprovou o
espaçamento**, e é isto que esta seção resolve. A 6.6 continua aberta, à espera da segunda
impressão.

- [x] 8.1 Medir os 12 vãos no papel antes de propor qualquer correção. Resultado: tabela de tinta,
  para separar defeito real de impressão de percepção.

  A **primeira** medição estava errada e foi descartada: lia `0,000 mm` abaixo em 11 das 12, porque
  a varredura começava na base da caixa declarada e encontrava o antialiasing da própria fórmula em
  vez do texto seguinte. É a quinta janela de medição mal dimensionada desta base. Corrigida
  agrupando linhas de tinta em faixas e tomando a faixa **seguinte** à da fórmula.

  | | antes | 1ª correção | aprovado |
  |---|---|---|---|
  | branco acima, média | 10,089 mm | 2,868 mm | **2,865 mm** |
  | branco abaixo, média | 2,565 mm | 5,214 mm | **7,775 mm** |
  | amplitude do vão de baixo | 2,159 mm | 0,550 mm | **0,550 mm** |
  | razão abaixo/acima | 0,25× | 1,82× | **2,71×** |

  O vão de cima era **constante** nas 12 (amplitude 0,931 mm) e o de baixo variava, porque era o de
  baixo que absorvia o resíduo do arredondamento à grade. A hipótese natural — "o resíduo do snap
  está sendo depositado acima" — é o contrário do que o código fazia, e foi refutada com o mapa: a
  identidade `abaixo − 3 mm == resíduo` bate ao micrômetro nas 12.

- [x] 8.2 Corrigir a causa, e não o sintoma (D-1.5.9). Resultado: os dois vãos derivados de
  constantes que já existiam, e a conversão linha-de-base → topo num ponto único.

  A causa era misturar posicionamento por linha de base com posicionamento por topo. `advanceAfterStatement`
  é agora o único lugar do layout onde essa conversão acontece, e é usado tanto por
  `QuestionBlockBuilder` — que dimensiona o bloco — quanto por `LayoutEngine`, que desenha. Dois
  cálculos fariam altura reservada e altura desenhada divergirem em silêncio.

  A fórmula deixou de arredondar à grade: quem precisa cair na grade é o bloco, e ele já cai.

- [x] 8.3 Regravar o golden e relatar contagem de páginas e atribuição bloco→página **antes** de
  aprovar. Resultado: mudança auditável, sem surpresa na impressão.

  Regravado **duas vezes**, porque a segunda impressão pediu ajuste. Ambas deliberadas:

  | | antes da fatia | 1ª correção | aprovado |
  |---|---|---|---|
  | `sha256` | `936a561c…ba2d4d75` | `0eedd2b2…2ed96325` | `34a1810c…69275e65` |
  | Páginas | 4 | 4 | **4** |
  | Questões que mudaram de página ou coluna | — | 15 de 40 | 18 de 40 |

  Na primeira correção os blocos com fórmula **encolheram** ~5 mm; com o 4/3 eles voltaram a
  crescer ~2,6 mm, e a paginação ficou perto da original. Em nenhuma das duas a folha passou de 4
  páginas.

  Questões sem fórmula não mudaram de altura em nenhuma das duas regravações — o que as moveu foi a
  regra de distribuir a sobra entre páginas em vez de empurrá-la para o fim, que propaga uma mudança
  de conteúdo para trás. É comportamento declarado em `PaginationTest.sobra e distribuida em vez de
  empurrada para o fim`, e não efeito colateral.

- [x] 8.4 Reverificar. Resultado: três alvos verdes, fidelidade dentro da tolerância, verificação
  ainda capaz de falhar.

  | Alvo | Testes | Falhas |
  |---|---|---|
  | `packages/domain` JVM | 123 | 0 |
  | `packages/domain` Node/JS | 119 | 0 |
  | `packages/domain` Android (host) | 119 | 0 |
  | `apps/web` (vitest) | 12 | 0 |

  Fidelidade do PDF web: 80 verificações, maior desvio **0,039 mm** contra 0,05 mm de tolerância —
  o mesmo patamar de antes, e agora no marcador 2 em vez de numa fórmula.

  O deslocamento deliberado continua sendo acusado pelas duas ferramentas, com nome e distância:
  `qq29-f` divergiu 0,466 mm na paridade.

- [x] 8.7 Aumentar a separação das alternativas, pedida na segunda impressão. Resultado: +49,1% de
  branco visível abaixo, com o vão de cima intacto.

  O pedido veio em tinta — "aumentaria em 50%" —, e traduzi-lo para o nominal não é multiplicar por
  1,5: a ascendente da alternativa é uma **subtração fixa**, então 3/2 no nominal daria +74,4% de
  branco visível. 4/3 dá +49,6%, e o medido no papel foi +49,1%.

  | multiplicador | nominal | tinta | variação |
  |---|---|---|---|
  | 3/2 | 11 536 µm | 9 016 µm | +74,4% |
  | 7/5 | 10 767 µm | 8 247 µm | +59,5% |
  | **4/3** | **10 254 µm** | **7 734 µm** | **+49,6%** |
  | 13/10 | 9 998 µm | 7 478 µm | +44,6% |

  Os dois vãos passaram a ser ancorados na **base** — a transição de texto — e não um no outro.
  Antes, "acima" era 45% de "abaixo", então este ajuste teria arrastado o vão superior junto. Com o
  reancoramento ele ficou em 2,865 mm contra os 2,868 mm anteriores: três micrômetros, que é ruído
  de rasterização. É o que a mudança de forma existia para garantir, e está afirmado em
  `os dois vaos sao derivados e o de baixo e maior que o de cima`.

- [x] 8.5 Reexecutar paridade web × Android com o layout novo. Resultado: mesmo patamar de antes,
  com o `android.pdf` gerado pelo `PdfDocument` real no emulador `platos-atd34`.

  | | fatia 1.5 | depois de D-1.5.9 |
  |---|---|---|
  | Elementos | 176 de 176, 4 páginas | **176 de 176, 4 páginas** |
  | Maior divergência | 0,042 mm em `qq31-f` | **0,042 mm em `qq37-f`** |
  | Tolerância | 0,3 mm | 0,3 mm |
  | Fidelidade do PDF Android | 0,022 mm | **0,022 mm em `formula qq30-f: altura da tinta`** |

  Os 0,042 mm são cerca de um pixel a 600 dpi — o piso da própria rasterização, como na fatia 1.5.
  Que o pior elemento continue sendo uma fórmula e continue no piso é o resultado que D-1.5.1
  previa: os dois lados recebem os mesmos bytes.

  O cruzamento mais forte é o espaçamento medido nos **dois** documentos, com a mesma régua:

  | | web | Android | diferença |
  |---|---|---|---|
  | branco acima, média | 2,865 mm | 2,868 mm | 3 µm |
  | branco abaixo, média | 7,775 mm | 7,765 mm | 10 µm |

  Um pixel a 600 dpi são 42 µm, então as duas diferenças estão abaixo da resolução da medição. É a
  evidência de que a correção nasceu em `commonMain` e chegou igual aos dois lados — que era o ponto
  de D-1.5.9 corrigir o layout e não o renderizador.

  Instrumentados no emulador: 4 testes, 0 falhas.

- [x] 8.6 Reimprimir para fechar a 6.6. Resultado: aprovada na terceira impressão.

  A segunda aprovou a inversão da proximidade e pediu mais separação das alternativas, atendida em
  8.7. A terceira aprovou a folha. O que foi conferido está em `docs/protocolo-medicao-impressa.md`
  §8, que passou a registrar também a armadilha de traduzir ajuste visual em constante nominal.
