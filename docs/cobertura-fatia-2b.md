# Cobertura de cenários — fatia 2b (folha completa e qualidade de impressão)

Mapa de cada cenário das duas specs delta de `openspec/changes/slice-2b-print-quality/specs/` para a
verificação que o cobre, e — onde a verificação é crítica — **como ela foi vista falhar**. Gerado ao
fechar a tarefa 9.2.

Vale aqui o mesmo das fatias anteriores: teste em `commonTest` roda nos três alvos, então o mesmo
valor esperado é afirmado três vezes, em três runtimes. O que esta fatia acrescenta é uma camada de
evidência de natureza diferente: **medição de tinta sobre o documento rasterizado**, por um caminho
que não compartilha código com o engine que produziu a geometria.

E acrescenta uma lição que organiza o documento inteiro: **os defeitos desta fatia não quebram
golden, fidelidade nem paridade.** Eles chegariam ao papel com a suíte no verde. Cada linha abaixo
existe porque alguma coisa precisava enxergar tinta.

## `layout-engine` — 18 cenários, 18 cobertos

| Cenário | Verificação |
|---|---|
| Instrução de preenchimento na folha | `LayoutEngineTest.cabecalho traz titulo e instrucao de preenchimento acima da regiao` (3 alvos) |
| A região de gabarito continua no topo | mesmo teste, mais `existe exatamente uma regiao de gabarito no topo da pagina 1`, que agora afirma "antes de qualquer questão" em vez de uma constante |
| Numeração em todas as páginas | `LayoutEngineTest.rodape numera todas as paginas dentro da margem inferior`, sobre prova de mais de uma página |
| Faixa do grampo permanece livre | `LayoutEngineTest.nada e desenhado dentro da faixa reservada ao grampo`, sobre **toda** primitiva de **toda** página |
| Grupos de tamanho declarado | `LayoutEngineTest.gabarito agrupa as linhas em grupos de 3 a 5 e alterna a faixa`, varrendo provas de 6 a 30 questões |
| Faixa alternada entre grupos vizinhos | mesmo teste — dois grupos com faixa nunca são vizinhos na mesma coluna |
| Letra dentro do círculo | `LayoutEngineTest.letra da alternativa fica dentro do circulo e mais clara que o corpo` |
| Decoração não move geometria | `LayoutEngineTest.toda bolha declarada coincide com o circulo desenhado` + a auditoria da regravação (tarefa 4.4): `regions` idêntico byte a byte, zero elementos preexistentes alterados |
| Trama fracionária sobrevive à serialização | `renderer.test.ts` (`0.955 0.955 0.955 rg` no fluxo de conteúdo) + o golden, que traz `"fill":45` |
| Ausência de tom significa preto pleno | `renderer.test.ts.desenha o tom declarado, e nao um cinza proprio` afirma `0 0 0 rg` junto |
| Orçamento declarado no mapa | `LayoutMapValidationTest.orcamento de tinta viaja no mapa` |
| Trama dentro da bolha acima do orçamento é recusada | `LayoutMapValidationTest.trama dentro da bolha acima do orcamento e recusada` |
| Elemento opaco dentro da bolha é recusado | `LayoutMapValidationTest.texto em preto pleno dentro da bolha e recusado` e `…tom acima do teto decorativo…` |
| A folha de referência é aceita | `LayoutMapValidationTest.mapa produzido pelo engine e aceito sem efeito colateral` + `GoldenLayoutTest.golden e um mapa valido` |
| Conteúdo mínimo da folha de teste | `PrintTestSheetTest.cabe em uma pagina e traz o conteudo minimo` |
| Mesma geometria de captura da prova | `PrintTestSheetTest.geometria de captura e a mesma da prova`, comparando os dois mapas entre si |
| Critério impresso na própria folha | `PrintTestSheetTest.criterio de aprovacao esta impresso na propria folha` |
| Trama acima do teto · Tom fora da faixa (validação) | `LayoutMapValidationTest`, seis testes, três de recusa e três de aceite |

## `print` — 12 cenários, 12 cobertos

| Cenário | Verificação |
|---|---|
| Tom desenhado como declarado | `renderer.test.ts` (fluxo de conteúdo do PDF) + paridade de trama entre plataformas |
| Nenhum pixel cromático | `tinta.mjs` sobre raster **RGB** a 300 dpi, nos quatro documentos |
| Trama dentro do teto | `tinta.mjs`: maior trama medida 4,71% (web) e 4,31% (Android), teto 8% + um passo de quantização |
| Trama equivalente entre plataformas | `compare.mjs`: 0,39 ponto de divergência — um passo de quantização de 8 bits |
| Cor barra a integração | `render-inked.ts` produz `tinta-cor.pdf`; o CI falha o job se `tinta.mjs` aceitar |
| Bolha vazia dentro do orçamento | `tinta.mjs`: 160 de 160 bolhas, máximo 7,24%, orçamento 12% |
| Decoração excessiva barra a integração | `render-inked.ts` produz `tinta-faixa.pdf`; o CI falha o job se `tinta.mjs` aceitar |
| Folha de teste sujeita às mesmas guardas | paridade 9 de 9 a 0,044 mm, fidelidade 31 verificações, tinta nos dois documentos |
| Impressora fora de escala é reprovada | `PrintTestSheetTest.a faixa impressa separa reescala normal de reescala que reprova` (3 alvos): lê a faixa do texto impresso na folha e exige que ela aprove ±5% e os 175,0 mm da impressão real, e reprove A4-em-Letter (169,3 mm), 90% e 110%. O método de papel da tarefa 8.2 **não** serve — "ajustar à página" de A4 para A4 encolhe ~3%, dentro do que ADR-0001 declara normal |
| Marcador incompleto reprova | `papel.mjs` lê os 7×7 módulos dos quatro marcadores na digitalização e compara com o mapa: **196 módulos por folha, zero divergências** nas duas folhas (tarefa 8.2) |
| Trama que some ou borra reprova | conferido a olho na folha de teste (tarefa 8.1): as amostras de 4,5% e 8% apareceram e são distinguíveis entre si |
| Contraste preservado | `papel.mjs` sobre as duas digitalizações (tarefa 8.3): caneta de **51,61%** a 72,20%, vazia até **9,20%**, corredor declarado de 20% a 40% inteiro no vão entre as duas nuvens |

## Como cada verificação crítica foi vista falhar

Crítico aqui é o que falha em silêncio e chega à folha impressa ou ao OMR. Cada linha foi produzida
introduzindo o defeito de propósito, observando o vermelho e revertendo.

| Defeito introduzido | Quem acusou | O que os outros disseram |
|---|---|---|
| Teto de trama chapada removido da validação | `LayoutMapValidationTest.trama acima do teto de oito por cento e apontada` | nenhum outro teste mudou de cor |
| Tom de texto devolvido a preto fixo no renderizador web | `renderer.test.ts.desenha o tom declarado` | os outros 13 seguiram verdes |
| Faixa a 200‰ (`tinta-faixa.pdf`) | `tinta.mjs`, saída 1: `bolha q06/A tem 21.46% de tinta, acima do orcamento decorativo de 12.0%`, e a trama medida em 20,39% | **fidelidade verde**, golden verde |
| Letras a 900‰ (`tinta-letra.pdf`) | validação do mapa, pelo teto de tom | `tinta.mjs` **verde**, e corretamente: a bolha vai a 10,78%, abaixo do orçamento — glifo é fino |
| Fórmula trocada por raster vermelho (`tinta-cor.pdf`) | monocromia em RGB: `pagina 0 tem pixel cromatico em (498, 1647): rgb(255, 0, 0)`, 3 999 ao todo | **fidelidade e cobertura verdes** — o raster cinza descarta a cor antes de elas olharem |
| Faixas removidas (`tinta-sem-faixa.pdf`) | comparação de trama da paridade: 4,71 pontos | **centroides leram 0,004 mm** — os 185 elementos não viram nada |
| Deslocamento deliberado de 0,5 mm, **com** decoração na folha | paridade: marcador 0,510 mm, bolha 0,411 mm, fórmulas 0,466 mm | — |
| Bolhas do mapa deslocadas 2,6 mm (meio passo) antes de medir o papel | `papel.mjs`: `bolha q34/A, preenchida a caneta, cobre 25.96% — abaixo do piso de 50.00%`, mais `bolha vazia q39/D cobre 16.11%`, mais 36 questões viradas ambíguas | — |
| Um módulo trocado num ArUco declarado no mapa | `papel.mjs`: `marcador r0-m0 (tl) tem 1 modulo(s) diferentes do declarado`, com as duas matrizes impressas lado a lado | a cobertura das 160 bolhas seguiu igual — o erro é de identidade do marcador, não de tinta |
| Bolhas empurradas para fora da imagem digitalizada | `papel.mjs`: 160 linhas `janela de medicao vazia, fora da imagem ou nao finita`, mais `medi 0 de 160 bolhas` e `nenhuma bolha marcada foi identificada` | — |
| Faixa impressa na folha de teste alargada de ±5% para ±10% | `PrintTestSheetTest`: `A4 em Letter mede 169333um e a folha aprovaria — o erro de papel passaria batido` | os outros dois vermelhos são esperados por mexer no texto: a conferência da faixa e o golden byte a byte |

### Três armadilhas que apareceram ao medir, e que valem mais que os resultados

**A média respondia a pergunta errada.** A primeira versão da medição de trama leu **13%** para uma
trama de 4,5%: a faixa do gabarito passa por baixo das bolhas, das letras e dos números, e a média
da área soma a tinta preta desenhada em cima. Média responde "quanta tinta há nesta área"; §7
pergunta "qual é o nível da trama". A moda dos pixels responde a segunda, e continua pegando faixa
ausente — a moda vira 255 e a comparação com o valor declarado falha.

**Uma guarda de cor sobre raster cinza não pode reprovar.** As duas ferramentas existentes
rasterizam em `DeviceGray`. Uma verificação de monocromia ali passaria sempre, porque o rasterizador
joga fora exatamente a informação que ela deveria julgar. A guarda mudou de espaço de cor, e a prova
de que a mudança importava é o documento vermelho: cinza verde, RGB vermelho.

**O limite que a validação conseguia provar recusaria a folha correta.** A spec pedia que a
validação sem renderizar recusasse mapa cuja cobertura passasse do orçamento. O `FontProgram` lê
avanço e espaçamento, não o contorno do glifo: o único limite superior calculável para uma letra é
"a caixa inteira é tinta", e medido na folha esse limite dá **20,43%** para bolhas cuja tinta real é
**7,24%**. Um validador honesto recusaria o que o raster aprova. O requisito foi reescrito
(D-2b.3.1): a validação prova o que consegue — trama chapada, que é exata, e tom opaco —, e quem
julga cobertura é o documento.

### Duas que apareceram quando a medição saiu do PDF e foi para o papel

**O dpi do scanner não era necessário, e quase virou bloqueio.** A digitalização veio de um
equipamento que não deixava escolher nem ver a resolução, e a primeira reação foi tratar isso como
dado faltante. Não é: cobertura é razão, e a escala que a medição precisa sai dos quatro ArUcos da
própria imagem. E a escala *absoluta* da impressão — que o dpi pareceria resolver — sai de uma
razão entre duas distâncias na mesma imagem: o vão dos centros de marcador dividido pela largura do
papel, que tem 210 mm porque é A4, saiba o scanner disso ou não. Deu 96,6% e 97,3% nas duas folhas,
contra 97,2% da régua. **Uma medição que depende de metadado do equipamento é uma medição a menos.**

**O preto do scanner não é preto.** Nesta digitalização o papel lê 233 de 255 e o miolo de um
módulo preto de ArUco lê **83** — toner pleno rende no máximo 64% de cobertura. Medir contra 255
fixo, como `tinta.mjs` faz no PDF, é correto lá e errado aqui: somaria à tinta a sombra da lâmpada
e o cinza do papel. `papel.mjs` normaliza contra um campo de branco local por percentil, e a
diferença é pequena mas não é zero — a mesma bolha mede 51,61% contra o branco local e 50,45%
contra um branco global. Com o piso do ADR em 50%, essa diferença é a distância entre aprovar e
reprovar.

## Números da fatia

| Grandeza | Valor |
|---|---|
| `packages/domain` JVM · Node · Android host | 228 · 222 · 222, zero falhas |
| `apps/api` (Postgres real) · `apps/android` (host) · `apps/web` | 89 · 9 · 14, zero falhas |
| Paridade da prova | 185 de 185, maior divergência **0,048 mm** |
| Paridade da folha de teste | 9 de 9, maior divergência **0,044 mm** |
| Fidelidade web · Android (prova) | 116 verificações cada; 0,046 mm e 0,042 mm |
| Tinta na bolha vazia, web · Android | **7,24%** e **6,88%**, orçamento 12%, corredor do limiar a partir de 20% |
| Trama entre plataformas | 0,39 ponto — um passo de quantização de 8 bits |
| `fixtures/prova-referencia.layout.json` | sha256 `3e31c36b…` |
| `fixtures/prova-referencia.package.json` | sha256 `26612ad5…` — que **é** o `content_hash` |
| `fixtures/folha-de-teste.layout.json` | 6 689 bytes, 34 primitivas, uma página |
| Tinta na bolha vazia, **no papel digitalizado** | **9,20%** (prova) e 8,51% (folha de teste), orçamento 12% |
| Tinta na bolha a caneta, no papel digitalizado | mínimo **51,61%**, mediana 60,97%, máximo 72,20%, piso 50% |
| Corredor observado entre vazia e caneta | de 9,20% a 51,61% — **42,41 pontos**, com o corredor declarado de 20% a 40% inteiro dentro |
| Módulos de ArUco conferidos contra o mapa, no papel | **196 por folha**, zero divergências, nas duas folhas |
| Escala da impressão de referência | **~97%** — régua 175,0 mm sobre 180,0 nominais; dentro do ±5% de ADR-0001 |

## O que ainda não está coberto

**Todo cenário das duas specs tem verificação.** As três conferências de papel fecharam: 8.1 no
olho (2026-08-22), 8.3 na medição da digitalização (2026-08-23) e 8.2 pela aritmética do critério
impresso, porque a conferência de papel que ela pedia não pôde ser feita. Nenhuma delas tinha
oracle dentro do sistema, e é isso que as tornava insubstituíveis: as fatias 1.5 e 1.6 tiveram
defeito de espaçamento achado **só** no papel, com todas as verificações automáticas verdes e
nenhuma delas errada.

O que fechou, e com que número:

- a faixa de 4,5% aparece na impressora usada e não compete com o texto (8.1);
- a letra dentro do círculo é legível **e** claramente não é marcação (8.1);
- uma bolha preenchida a caneta cobre de 51,61% a 72,20%, contra o piso de 50% que ADR-0010
  declarou **antes** de medir — o piso se sustenta, com 1,6 ponto de margem no pior traço (8.3);
- a faixa impressa na folha de teste reprova A4-em-Letter (169,3 mm) e aprova o encolhimento de 3%
  que ADR-0001 absorve (8.2).

**O que sobrou de risco, nomeado.** Duas coisas dependem de gesto humano e nenhuma tem como ser
provada daqui:

- **Ninguém viu uma pessoa com régua rejeitar uma folha ruim.** O teste da tarefa 8.2 prova que o
  critério impresso separa as reescalas certas; não prova que quem está com o papel na mão faz a
  comparação. É risco baixo — dois números impressos lado a lado — mas a primeira impressão feita
  em impressora própria deve repetir a conferência de propósito.
- **A margem do piso da caneta é fina no pior traço.** 51,61% contra 50%, e numa digitalização de
  mesa. A fatia 3 fotografa com celular, em condição pior. O número a vigiar não é o piso e sim a
  separação, que é de 42,41 pontos e comporta o corredor declarado inteiro.
