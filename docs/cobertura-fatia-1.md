# Cobertura de cenários — fatia 1

Mapa de cada cenário de `openspec/changes/slice-1-layout-engine/specs/` para a verificação que o
cobre. Gerado ao fechar a tarefa 11.1.

Testes em `commonTest` rodam nos três alvos (JVM, Node/JS e teste unitário Android), e é isso que
torna a coluna de evidência mais forte do que parece: o mesmo valor esperado é afirmado três vezes,
em três runtimes.

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

| | |
|---|---|
| Elementos comparados | 116 (4 marcadores + 112 bolhas), 3 páginas |
| Maior divergência | **0,041 mm** |
| Tolerância | 0,3 mm |

Os 0,041 mm equivalem a cerca de um pixel a 600 dpi: é o piso da própria rasterização, não
diferença real de desenho.

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
