# Cobertura de cenários — fatias 1 e 1.5

Mapa de cada cenário de `openspec/changes/slice-1-layout-engine/specs/` e de
`openspec/changes/slice-1-5-math-rendering/specs/` para a verificação que o cobre. Gerado ao fechar
a tarefa 11.1 e atualizado na tarefa 6.7 da fatia 1.5.

Testes em `commonTest` rodam nos três alvos (JVM, Node/JS e teste de host Android), e é isso que
torna a coluna de evidência mais forte do que parece: o mesmo valor esperado é afirmado três vezes,
em três runtimes.

> **Nota da fatia 1.5.** O terceiro alvo tinha parado de rodar. O plugin `android.kmp.library`
> introduzido na subida para AGP 9 não cria o teste de host por padrão, e o build avisava mas
> seguia verde — entre aquela subida e esta fatia, "afirmado em três runtimes" valia em dois.
> `withHostTest {}` em `packages/domain/build.gradle.kts` religou o alvo, e o CI agora pede
> `testAndroidHostTest` pelo nome porque `./gradlew build` não o alcança.

## `layout-engine` — 22 cenários, 22 cobertos

| Cenário | Verificação |
|---|---|
| Mesma medição em plataformas diferentes | `MeasurementParityTest` (3 alvos, mesmas constantes) |
| Fonte do sistema não influencia o resultado | `MeasurementParityTest.medicao vem da fonte embarcada…` |
| Recurso de fonte ausente ou corrompido | `FontProgramTest`: truncado, vazio, assinatura sfnt, tamanho divergente |
| Texto com pares de kerning | `FontProgramTest.fonte sem tabela kern legada tem ajuste zero` |
| Altura arredondada para a grade | `PaginationTest.altura sobe ao proximo multiplo da grade` |
| Posições verticais alinhadas | `PaginationTest.posicoes verticais ficam alinhadas a grade` |
| Questão não é partida entre páginas | `PaginationTest.bloco nunca e partido entre colunas ou paginas` |
| Sobra distribuída entre páginas | `PaginationTest.sobra e distribuida em vez de empurrada para o fim` |
| Bloco maior que a página | `PaginationTest.bloco maior que a area util e recusado…` |
| Recálculo estável | `LayoutEngineTest.mapa e identico entre recalculos` |
| Cálculo em plataformas diferentes | `GoldenLayoutTest.mapa da fixture bate byte a byte com o golden` (3 alvos) |
| Versões declaradas | `LayoutEngineTest.mapa declara as duas versoes` |
| Coordenadas dentro da faixa normalizada | `LayoutEngineTest.toda coordenada normalizada fica no intervalo unitario` |
| Identificadores dos marcadores | `LayoutEngineTest.marcadores da regiao zero usam os identificadores 0 a 3` |
| Zona de silêncio preservada | `LayoutEngineTest.zona de silencio dos marcadores fica livre de bolhas` |
| Passo das bolhas alinhado à grade | `LayoutEngineTest.passo vertical das bolhas e 6 mm e multiplo da grade` |
| Identificador duplicado | `LayoutMapValidationTest.identificador de elemento repetido e apontado` |
| Regiões sobrepostas | `LayoutMapValidationTest.regioes sobrepostas sao apontadas` |
| Coordenada fora da faixa | `LayoutMapValidationTest.coordenada fora da faixa e apontada` |
| Mapa válido | `LayoutMapValidationTest.mapa produzido pelo engine e aceito sem efeito colateral` |
| Questão discursiva na entrada | `LayoutEngineTest.questao discursiva impede a emissao do mapa` |
| Conteúdo não suportado não degrada em silêncio | `ExamDefinitionTest.recurso nao suportado no enunciado e recusado` |

## `layout-engine` — fatia 1.5, 8 cenários, 8 cobertos

Todos em `BlockFormulaTest`, `commonTest`, nos três alvos.

| Cenário | Verificação |
|---|---|
| Fórmula em bloco é aceita | `formula em bloco e aceita e o mapa inclui a caixa dela` |
| Fórmula reserva espaço próprio | `formula faz o bloco crescer em multiplos da grade` + `a formula nao arredonda a grade, quem arredonda e o bloco` |
| Fórmula não é reescalada | `os dois vaos sao derivados e o de baixo e maior que o de cima` + `a caixa desenhada tem exatamente as dimensoes declaradas` |
| Fórmula mais larga que a coluna | `formula mais larga que a coluna impede a emissao do mapa`, com `formula com exatamente a largura da coluna e aceita` fixando a borda |
| Layout não depende do conteúdo matemático | `formulas de conteudos diferentes e dimensoes iguais dao blocos iguais` + `trocar so a referencia move apenas a referencia no mapa` |
| Ordem dentro do bloco | `formula fica abaixo do enunciado e acima da primeira alternativa` |
| Fórmula não se separa do enunciado | `enunciado formula e alternativas ficam na mesma coluna e pagina`, com fórmula de 30 mm forçando várias quebras |
| Fórmula em linha ainda é recusada | `formula em linha continua recusada com mensagem propria` |

> **Nota de D-1.5.9.** O espaçamento em volta da fórmula mudou depois que a folha impressa foi
> reprovada por proximidade — o branco de cima era 3,9× o de baixo, e a fórmula lia como parte das
> alternativas em vez do enunciado. Os dois vãos passaram a ser derivados da mesma base, a transição
> que a folha já faz entre enunciado e primeira alternativa: acima é 45% dela, abaixo é 4/3. A
> fórmula deixou de arredondar à grade, porque o bloco já arredonda e o resíduo caía todo abaixo
> dela. Resultado na tinta: 2,865 mm acima e 7,775 mm abaixo, contra 10,089 e 2,565 originais.
>
> Nenhuma das verificações automáticas podia ter pego isso, e nenhuma estava errada: paridade
> compara os dois renderizadores, que erravam igual; fidelidade compara documento com `LayoutMap`,
> e o documento estava fiel a um mapa errado; o golden compara o mapa consigo mesmo. Defeito de
> julgamento tipográfico não tem oracle dentro do sistema — é o argumento para a tarefa 6.6 nunca
> ser substituída por medição.

## `print` — fatia 1.5, 4 cenários, 4 cobertos

| Cenário | Verificação |
|---|---|
| Imagem ocupa a caixa declarada | `renderer.test.ts.desenha uma imagem por formula declarada no mapa` (web) e `desenhaUmaFormulaPorCaixaDeclarada` no emulador; medido em `fidelidade.mjs`, 48 verificações de fórmula |
| Bytes ausentes | web: três testes em `renderer.test.ts`; Android: `RendererContractTest` (guarda) e `recusaImprimirQuandoOsBytesDaFormulaFaltam` no `PdfDocument` real |
| Fórmula equivalente entre renderizadores | web × Android reais: 12 fórmulas entre os 176 elementos, maior divergência **0,042 mm** contra 0,3 mm |
| Renderizador não tipografa matemática | evidência estrutural nos dois: nenhum dos módulos menciona LaTeX, MathML, MathJax, SVG ou TeX; o web chama `embedPng`, o Android `decodeByteArray` |

## Conversão de fórmula — `tools/math`, 15 testes

Fora das specs, mas é o que sustenta D-1.5.6 e D-1.5.8.

| O que | Verificação |
|---|---|
| Reprodutibilidade | conversão repetida byte a byte, conversão em ordem inversa, e o manifesto versionado contra a conversão atual |
| Limite de D-1.5.6 | 7 recusas: `\newcommand`, `\def`, `\let`, `\usepackage`, `\require`, `\begin{tikzpicture}`, `\tikz` |
| Degradação silenciosa | comando indefinido, notação desconhecida e SVG sem `viewBox` falham em vez de desenhar o erro |
| Caixa = raster | dimensão declarada é a do raster a 600 dpi, para as doze |

## `print` — 13 cenários, 13 cobertos

| Cenário | Verificação |
|---|---|
| Renderizador não recalcula geometria | `renderer.test.ts`: o módulo não tem medidor, quebra de linha nem paginação |
| Primitiva desconhecida | `renderer.test.ts` (web) e `RendererContractTest` (Android) |
| Fonte embarcada no documento | `renderer.test.ts`: `FontFile2` presente, nenhuma das 14 fontes padrão |
| Renderizador desatualizado recusa imprimir | web, Android local e Android instrumentado no emulador |
| Renderizador compatível | `renderer.test.ts` e `RendererContractTest` |
| Centroides dentro da tolerância | web × Android reais: 116 elementos, máxima divergência **0,041 mm** contra 0,3 mm |
| Estrutura idêntica das páginas | mesmo run: 3 páginas dos dois lados, 116 de 116 elementos localizados |
| Divergência barra a integração | deslocamento de 400 µm nas bolhas dentro do renderizador Android: 103 elementos acusados, saída 1, marcadores corretamente não acusados |
| Medição do documento gerado | `tools/parity/fidelidade.mjs` a 1200 dpi: 32 verificações, desvio máximo 0,039 mm no web e 0,017 mm no Android, contra 0,05 mm |
| Documento não depende de ajuste do visualizador | caixa de 595 × 842 pt lida por `mupdf` e `pdf-lib`, mais os visualizadores das três impressões |
| Folha reescalada pela impressora | três impressoras entre −3,4% e +4,7%: marcadores acima de 12 mm em todas (13,5 mm na pior), zona de silêncio livre, QR decodificado |
| Marcadores legíveis após impressão | folhas impressas inspecionadas: bordas fechadas, zona de silêncio livre |
| Geometria dimensionada com folga | `CaptureGeometryTest` nos três alvos: 14 mm nominais mantêm 13,3 mm sob −5% |

## As duas medições que mais importam

**Paridade entre renderizadores.** O risco central que a fatia existe para neutralizar — dois
renderizadores divergirem e quebrarem o OMR em silêncio (§16):

| | fatia 1 | fatia 1.5 |
|---|---|---|
| Elementos comparados | 116 (4 marcadores + 112 bolhas), 3 páginas | 176 (4 marcadores + 160 bolhas + 12 fórmulas), 4 páginas |
| Maior divergência | **0,041 mm** | **0,042 mm**, em `qq31-f` — uma fórmula |
| Tolerância | 0,3 mm | 0,3 mm |

Os 0,042 mm equivalem a cerca de um pixel a 600 dpi: é o piso da própria rasterização, não
diferença real de desenho. Que o pior elemento seja uma fórmula e ainda assim esteja no piso é o
resultado esperado de D-1.5.1 — os dois lados recebem os mesmos bytes, então a igualdade é por
construção e não por concordância entre bibliotecas.

**A verificação continua capaz de falhar.** Deslocando 0,5 mm uma bolha, um marcador e a fórmula
`qq29-f`, as duas ferramentas acusam os três por nome e distância, com código de saída 1:

| Elemento | Paridade acusa | Fidelidade acusa |
|---|---|---|
| `r0-m0` (marcador) | 0,500 mm | borda superior, desvio 0,542 mm |
| `r0-bq01-A` (bolha) | 0,409 mm | centro, desvio 0,418 mm |
| `qq29-f` (fórmula) | 0,466 mm | borda esquerda, desvio 0,480 mm |

A fórmula só entrou nessa tabela depois de um defeito de medição ser encontrado e corrigido. A
primeira versão media o centroide numa janela igual à caixa declarada; ela **recortava** a fórmula
deslocada, e um deslocamento real de 0,500 mm era lido como 0,142 mm — abaixo da tolerância, com o
comparador dizendo "paridade OK". O peso de tinta dentro da janela era o que denunciava: 1 195 519
contra 1 226 245. Com 1 mm de folga de cada lado, lê 0,466 mm e falha. É a terceira vez que esta
base produz uma verificação incapaz de falhar, e a segunda por janela de medição mal dimensionada.

**Escala de impressão.** Três pessoas, três impressoras, três instrumentos (régua, fita métrica,
paquímetro), todas com escala declarada em 100%:

| Impressora | Vão horizontal (180,00 mm) | Vão vertical (85,01 mm) | Escala |
|---|---|---|---|
| 1, com "ajustar à área de impressão" | 175 | — | −2,8% |
| 2 | 184 | 89 | +2,2% / +4,7% |
| 3 | 173,8 | 82,32 | −3,4% / −3,2% |

Nenhuma reproduz o documento em escala real, e elas erram em direções opostas. Daí o ADR-0001:
fidelidade é propriedade do documento, e a folha impressa precisa apenas continuar legível sob
±5%. O documento, medido a 1200 dpi, bate dentro de 0,05 mm em 32 verificações.
