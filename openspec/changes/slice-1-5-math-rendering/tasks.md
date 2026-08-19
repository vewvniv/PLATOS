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
- [ ] 6.6 Imprimir a folha com fórmula e conferir legibilidade a olho, seguindo o protocolo. Resultado: registrado se a fórmula a 600 dpi sai nítida na impressora medida no ADR-0001 — é o que confere a escolha de resolução no papel, e não só no argumento.

  **Bloqueada: exige papel e impressora física, que não estão ao meu alcance.** É a única tarefa da fatia que nenhuma verificação automática substitui, e é justamente a que confere D-1.5.1 no papel em vez de no argumento — a escolha de 600 dpi foi feita porque "é o que impressora doméstica e escolar entrega", e isso continua sendo um argumento até alguém olhar a folha.

  O documento está pronto em `build/parity/web.pdf` (4 páginas, 12 fórmulas). O protocolo está em `docs/protocolo-medicao-impressa.md`. O que precisa ser olhado, além do que o protocolo já pede:

  - traço da fração e da raiz — são os finos, e são o primeiro lugar onde 600 dpi falharia;
  - subscrito e expoente de `f-potencias`, que são os menores glifos da folha;
  - as barras verticais de `f-matriz2` e as parênteses altas de `f-matriz3`;
  - se a fórmula parece do mesmo peso óptico do texto ao redor, já que foi composta no mesmo corpo.

- [x] 6.7 Atualizar `docs/cobertura-fatia-1.md` com os cenários novos. Resultado: nenhum cenário das duas specs sem verificação.

  O documento passou a cobrir as duas fatias. Doze cenários novos, todos cobertos: 8 de `layout-engine` e 4 de `print`. Acrescentada também a tabela dos 15 testes de `tools/math`, que não sai de spec nenhuma mas é o que sustenta D-1.5.6 e D-1.5.8, e a nota sobre o terceiro alvo ter parado de rodar.

- [x] 6.8 Registrar a fatia 1.6 — matemática em linha, com `InlineBox` de largura, altura e `baseline_offset` — como próxima da lista, bloqueadora da fatia 6 e planejada para vir antes da fatia 2. Resultado: o caso predominante das exatas não fica só numa nota de design, e o tipo entra na medição de texto antes de o `ExamPackage` congelar contrato.

  Entrou como linha própria no roadmap de `ARQUITETURA-FINAL-v3.md` §15, entre a 1.5 e a 2, marcada como bloqueadora da fatia 6. O parágrafo abaixo da tabela — que já defendia duas escolhas de ordem — ganhou a terceira, com as duas razões separadas: o gatilho formal (bloquear a 6) e a data desejada (antes da 2, porque `InlineBox` entra na medição de texto e é melhor resolver isso antes de a 2 congelar os contratos do `ExamPackage`).

## 7. Fora do escopo, encontrado no caminho

Nada aqui foi corrigido nesta fatia. Fica registrado porque foi medido, não suposto.

### Medição de texto diverge entre alvos em surrogate solto

`CodePointMeasurementTest` falha 3 de 5 no alvo **Node/JS**, e passa em JVM e Android. Confirmado pré-existente na árvore limpa, com `git stash`, antes de qualquer mudança desta fatia.

| Entrada | JVM e Android | Node/JS |
|---|---|---|
| `\uD83D` (surrogate alto solto) | 2 145 µm | **1 394 µm** |
| `\uDE00\uD83D` (dois code points) | 4 289 µm | **2 788 µm** |

O par bem formado (`😀`) mede igual nos três; só o surrogate **solto** diverge. 1 394 µm correspondem a um avanço de 416 unidades de fonte contra as 640 do `.notdef`, ou seja: em JS o code point resolve para um glifo real, e nos outros dois para `.notdef`.

A causa provável está em `FontProgram.glyphIn`: quando o segmento do `cmap` formato 4 tem `idRangeOffset != 0`, o índice `at` é calculado e lido com `bytes.u16(at)` **sem checar limite**. Uma leitura fora do array se comporta de forma diferente em JVM e em Kotlin/JS, e é isso que os números indicam.

Por que importa mais do que parece: é a invariante que a fatia 1 comprou — "KMP é a fonte compartilhada de medição" —, e este é o mesmo tipo de defeito que o commit `88a8aca` corrigiu no `codePoints`. Não afeta prova real, porque enunciado não tem surrogate solto; afeta a garantia. Merece fatia própria, curta.

### O terceiro alvo do `packages/domain` tinha parado de rodar

Descrito na tarefa 6.1. Religado aqui porque sem ele a 6.1 não podia ser cumprida como está escrita, mas a causa é anterior: a subida para AGP 9 trocou o plugin do módulo KMP e o teste de host deixou de ser criado, com aviso no build e nenhuma falha.
