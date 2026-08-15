## 1. Módulo KMP e fonte embarcada

- [x] 1.1 Criar `packages/domain` como módulo KMP com alvos JVM, Android e JS (D-1.1), registrá-lo em `settings.gradle.kts` e adicionar plugin KMP e Android Gradle Plugin ao `gradle/libs.versions.toml`. Resultado: `./gradlew :packages:domain:build` compila os três alvos e `:apps:api:test` continua verde.
- [x] 1.2 Escolher o TTF serifado OFL, embarcar em `commonMain/resources/fonts/` e expor nome, versão e hash como constante. Resultado: a licença acompanha o arquivo e o recurso é lido a partir de `commonMain` nos três alvos.
- [x] 1.3 Criar os tipos de comprimento em micrômetros e a política de arredondamento na divisão inteira (D-1.2). Resultado: nenhum `Float` ou `Double` aparece no código de cálculo, verificado por teste que falha se aparecer.

## 2. Medição de texto

- [x] 2.1 Implementar o parser das tabelas `head`, `cmap` formato 4, `hhea` e `hmtx` do TTF embarcado. Resultado: o avanço de um glifo conhecido bate com o valor lido por uma ferramenta externa de inspeção de fonte.
- [x] 2.2 Implementar a leitura opcional de `kern` formato 0 e a soma de avanços com ajuste de par, convertendo unidades de fonte para µm por aritmética inteira (D-1.3). Resultado: cobre o cenário "Texto com pares de kerning"; se a fonte não tiver `kern`, o ajuste é zero e isso está registrado no teste.
- [x] 2.3 Implementar a quebra de linha por largura disponível sobre a medição própria. Resultado: mesmo texto e mesma largura produzem as mesmas quebras.
- [x] 2.4 Testes de "Medição de texto determinística e independente de plataforma": mesma medição em JVM e JS, fonte do sistema não influencia o resultado, e recurso de fonte ausente ou corrompido falhando com erro identificável. Resultado: quatro cenários verdes, com o de plataformas rodando nos dois alvos.

## 3. Grade, blocos e paginação

- [x] 3.1 Implementar a grade de 3 mm com arredondamento de altura para cima, sem compressão de conteúdo. Resultado: cobre "Altura arredondada para a grade" e "Posições verticais alinhadas".
- [x] 3.2 Implementar o agrupamento em blocos indivisíveis (enunciado + alternativas). Resultado: um bloco nunca é emitido partido.
- [x] 3.3 Implementar o posicionamento da região `ANSWER_BLOCK` no topo da página 1, atravessando as duas colunas e reduzindo a altura útil dos slots dessa página (D-1.4). Resultado: a altura útil dos dois slots da página 1 é menor que a das páginas seguintes.
- [x] 3.4 Implementar a paginação por DP sobre slots de coluna minimizando `Σ(sobra_do_slot)²` (D-1.4). Resultado: cobre "Questão não é partida entre páginas" e "Sobra distribuída entre páginas".
- [x] 3.5 Implementar a recusa explícita de bloco maior que a área útil de uma página. Resultado: cobre "Bloco maior que a página" com erro identificável e nenhum mapa emitido.

## 4. `LayoutMap` e geometria da região escaneável

- [x] 4.1 Definir o `LayoutMap` e as primitivas de desenho (`DrawRect`, `DrawCircle`, `DrawText`, `DrawImage`, `DrawAruco`) com `kotlinx.serialization`, em JSON canônico sem número fracionário (D-1.5). Resultado: serializar duas vezes o mesmo mapa produz bytes idênticos.
- [x] 4.2 Emitir `layout_engine_version` e `min_renderer_version` no mapa, ambos começando em `1` (D24). Resultado: cobre "Versões declaradas".
- [x] 4.3 Implementar a região escaneável de gabarito: quatro marcadores ArUco com identificadores `4k` a `4k+3`, QR dentro da região, e coordenadas em partes por milhão relativas ao quadrilátero. Resultado: cobre "Coordenadas dentro da faixa normalizada" e "Identificadores dos marcadores".
- [x] 4.4 Implementar a geometria de captura do §7: bolha 4,2 mm, passo horizontal 5,2 mm, passo vertical 6,0 mm, traço 0,22 mm, ArUco com lado ≥ 12 mm e zona de silêncio de no mínimo um módulo. Resultado: cobre "Zona de silêncio preservada" e "Passo das bolhas alinhado à grade".
- [x] 4.7 Vendorizar o `DICT_5X5_100` do OpenCV como dado em `commonMain`, com atribuição e licença, expondo a matriz de módulos por identificador (D-1.10). Resultado: os 100 marcadores existem, são distintos e o marcador 0 bate módulo a módulo com os bytes de origem.
- [x] 4.8 Implementar o codificador QR em Kotlin comum — modo byte, nível M, versões 1 a 10, seleção de máscara pela penalidade da norma (D-1.10). Resultado: matrizes geradas decodificam em um leitor independente e a matriz de referência é congelada como regressão nos três alvos.
- [x] 4.5 Testes de "`LayoutMap` determinístico e versionado": recálculo estável e cálculo em plataformas diferentes. Resultado: dois cenários verdes, o segundo comparando bytes entre JVM e JS.
- [x] 4.6 Implementar a recusa de entrada não suportada — questão discursiva, conteúdo matemático ou imagem — e testar os cenários "Questão discursiva na entrada" e "Conteúdo não suportado não degrada em silêncio". Resultado: dois cenários verdes, nenhum mapa parcial emitido.

## 5. Validação do `LayoutMap`

- [x] 5.1 Implementar a validação pura: unicidade de identificadores, ausência de sobreposição entre regiões, coordenadas em faixa e versões presentes. Resultado: função sem efeito colateral, sem dependência de renderização.
- [x] 5.2 Testes dos quatro cenários de "Validação do `LayoutMap` sem renderizar": identificador duplicado, regiões sobrepostas, coordenada fora da faixa e mapa válido. Resultado: quatro cenários verdes, cada recusa apontando a verificação que falhou.

## 6. Fixture e golden

- [x] 6.1 Criar a fixture da prova objetiva de referência em JSON versionado (D-1.9), com questões suficientes para forçar mais de uma página. Resultado: entrada pura, sem dependência de banco ou rede.
- [x] 6.2 Gerar e commitar o `LayoutMap` golden da fixture, com teste que afirma igualdade byte a byte. Resultado: mudança de geometria só passa se o golden for atualizado no mesmo commit.

## 7. Renderizador web

- [x] 7.1 Criar `apps/web` com React, TypeScript e Vite, sem navegação nem estado — só o suficiente para carregar um `LayoutMap` e baixar o PDF. Resultado: `apps/web` builda e o `apps/api` segue intocado.
- [x] 7.2 Implementar o renderizador `pdf-lib` traduzindo primitiva → API, com a fonte embarcada incorporada ao documento e a conversão µm → pt com arredondamento explícito. Resultado: cobre "Renderizador não recalcula geometria" e "Fonte embarcada no documento".
- [x] 7.3 Implementar a guarda de versão e a falha em primitiva desconhecida. Resultado: cobre "Renderizador desatualizado recusa imprimir", "Renderizador compatível" e "Primitiva desconhecida".
- [x] 7.4 Gerar o PDF da fixture em Node como passo reproduzível de build. Resultado: PDF do web disponível como artefato para o job de paridade.

## 8. Renderizador Android

- [x] 8.1 Criar `apps/android` como módulo Android mínimo, dependente de `packages/domain`, sem CameraX e sem UI além do disparo do render. Resultado: módulo compila e entra em `settings.gradle.kts`.
- [x] 8.2 Implementar o renderizador `PdfDocument`/`Canvas` traduzindo as mesmas primitivas, com a mesma conversão µm → pt e a mesma regra de arredondamento do renderizador web. Resultado: mesma folha, mesmas primitivas, sem cálculo de geometria no renderizador.
- [x] 8.3 Implementar a guarda de versão e a falha em primitiva desconhecida, espelhando 7.3. Resultado: os três cenários de `print` verdes também no Android.
- [x] 8.4 Criar o teste instrumentado que gera o PDF da fixture em emulador e o exporta como artefato. Resultado: PDF do Android disponível para o job de paridade.
  - Dois testes verdes em `platos-atd34 (AVD) — Android 14`, imagem `system-images;android-34;aosp_atd;x86_64`, a mesma que o job de CI fixa. PDF de 146 246 bytes, cabeçalho `%PDF-`.
  - O primeiro caminho tentado estava errado e o CI herdava o mesmo defeito: gravar em `filesDir` e puxar depois com `adb run-as` nunca funciona, porque o AGP **desinstala** os APKs ao fim da execução e leva o diretório junto — `run-as: unknown package`. O teste passou a gravar no `additionalTestOutputDir`, que o próprio AGP recolhe para `build/outputs/connected_android_test_additional_output/` antes de desinstalar. O workflow foi corrigido junto.
  - O teste da guarda de versão também roda no emulador: o arquivo que ele não deveria produzir saiu com 0 bytes.

## 9. Paridade em CI

- [x] 9.1 Implementar o teste de paridade de cálculo JVM × JS sobre a fixture, comparando o JSON do `LayoutMap` byte a byte (D-1.6). Resultado: roda sem emulador e sem navegador, no build principal.
- [x] 9.2 Implementar a extração de centroides de marcadores e bolhas a partir de PDF rasterizado a 600 dpi, com o **mesmo** rasterizador para os dois documentos (D-1.7). Resultado: a extração é determinística sobre o mesmo PDF.
- [x] 9.3 Implementar a comparação de centroides com tolerância de 0,3 mm e a asserção de estrutura — número de páginas, de regiões e de bolhas por região. Resultado: cobre "Centroides dentro da tolerância" e "Estrutura idêntica das páginas".
- [ ] 9.4 Adicionar ao `.github/workflows/ci.yml` o job separado de paridade com emulador Android de API level fixado, mais Node para o lado web. Resultado: pipeline verde em push e em pull request, com o job de paridade reportando a folga real medida.
  - Workflow escrito: job `web` (testes, build e PDF da fixture) e job `paridade` (emulador `aosp_atd` API 34 com KVM, `adb pull` do PDF, comparação). **Falta rodar** — nada foi enviado ao GitHub ainda, então "pipeline verde" e "folga real medida" continuam sem evidência.
- [x] 9.5 Verificar o cenário "Divergência barra a integração" introduzindo deliberadamente um deslocamento acima da tolerância em um renderizador, confirmando que o job falha e aponta o elemento e a distância; reverter em seguida. Resultado: a asserção é comprovadamente capaz de falhar.
  - Deslocamento de 400 µm em `x` aplicado só às bolhas dentro do renderizador Android, PDF regerado no emulador: o comparador saiu com código 1 e acusou 103 elementos entre 0,383 mm e 0,401 mm — coerente com os 400 µm injetados. Os quatro marcadores ArUco **não** foram acusados, que é o esperado, já que o deslocamento só atingiu círculos: a detecção é dirigida ao elemento, não um alarme geral.
  - Revertido em seguida; a comparação voltou a 0,041 mm e código 0.
  - Antes disso, a mesma verificação já havia exposto um defeito real no comparador: `getPixels()` do mupdf devolve uma janela para a memória WASM que a segunda rasterização invalida, os pesos viravam `NaN`, e como `NaN > tolerância` é falso o comparador aprovava qualquer entrada. Corrigido com cópia dos pixels e guarda explícita de não finito.

## 10. Verificação física

- [x] 10.1 Documentar o protocolo de impressão e medição: A4 a 100% sem ajuste de escala, pontos a medir e tolerância de 0,2 mm (D-1.8). Resultado: protocolo repetível por outra pessoa.
- [x] 10.2 Imprimir a folha da fixture, medir com régua o diâmetro e os passos das bolhas, o lado do ArUco e as margens, e registrar os valores observados nesta tarefa. Resultado: cobre "Medição física da folha impressa" e "Marcadores legíveis após impressão" com números reais anotados.
  - **Primeira tentativa (2026-08-15) — não passou; a folha saiu fora de escala.** Observado: largura impressa 175 mm (esperado 180,00), margem esquerda 15 mm (15,01), margem superior 17 mm (14,03), lado do ArUco 14 mm (13,99), diâmetro da bolha 5 mm (4,42), passo horizontal 6 mm (5,20), passo vertical 6 mm (6,00).
  - Diagnóstico: 175/180 = 0,972, e uma redução de 2,8% com recentragem vertical põe a margem superior em ≈17,8 mm — exatamente o observado. Sob essa escala o ArUco vale 13,6 mm e o passo vertical 5,83 mm, que a régua arredonda para 14 e 6, então essas duas não contradizem. A folha foi impressa com ajuste de escala ligado.
  - O PDF foi conferido rasterizando a 1200 dpi e está correto: largura 180,00 mm, margem esquerda 15,01, margem superior 14,03, ArUco 13,99, bolha com diâmetro externo 4,42 (4,20 + traço 0,22), traço medido 0,21–0,23, passo horizontal 5,20–5,21 e centros coincidindo com os declarados no `LayoutMap`. O defeito não é de geometria.
  - O protocolo foi corrigido junto: exigia ±0,2 mm, que régua nenhuma resolve. Agora a largura de 180 mm é portão de entrada e as medidas pequenas são tomadas sobre vários passos e divididas.
  - **QR conferido e aprovado**: a câmera leu `prova-referencia-slice-1...0.05CB`, exatamente o payload que o codificador KMP gerou — a cadeia Kotlin → PDF → toner → câmera fecha.
  - **Segunda e terceira impressões (2026-08-15), em outras duas impressoras, com escala 100% explícita.** Vãos entre marcadores, que são imunes a deslocamento da folha na bandeja:

    | Impressora | Instrumento | Vão horizontal (180,00) | Vão vertical (85,01) | Escala |
    |---|---|---|---|---|
    | 2 | fita métrica | 184 | 89 | +2,2% / +4,7% |
    | 3 | paquímetro | 173,8 | 82,32 | −3,4% / −3,2% |

    Na impressora 3 as margens saíram 17,6 e 18,6 mm, praticamente simétricas — uma redução de 3,4% centralizada prevê 18,1 mm dos dois lados, o que confirma conteúdo centralizado e encolhido, não deslocado.
  - **Conclusão: nenhuma das três impressoras reproduz o PDF em escala real, e elas erram em direções opostas.** Isso virou o ADR-0001 e a correção da spec de `print`. A tarefa fecha com o que a captura de fato exige, tudo verificado nas folhas: marcador acima de 12 mm mesmo na impressora que mais encolheu (13,5 mm), zona de silêncio livre, marcadores completos e QR decodificado. A fidelidade em milímetros passou a ser medida sobre o documento, na tarefa 10b.3.
- [x] 10.3 Abrir o PDF em ao menos dois visualizadores e conferir tamanho de página e margens declarados. Resultado: cobre "Documento não depende de ajuste do visualizador".
  - Caixa de página conferida por dois leitores independentes de PDF — `mupdf` e `pdf-lib` — mais os visualizadores usados nas três impressões: 595 × 842 pt em todos, sem depender de opção de encaixe à página. As três folhas saíram com a mesma proporção de conteúdo, cada uma na escala da sua impressora, o que confirma que a caixa declarada não muda com o visualizador.

## 10b. Fidelidade dimensional do documento (ADR-0001)

- [x] 10b.1 Escrever o ADR-0001 registrando a medição das três impressoras e a decisão de que fidelidade é propriedade do documento, com folha impressa tolerando ±5% de reescala. Resultado: premissa versionada em `docs/adr/`, conforme §14 regra 2.
- [x] 10b.2 Corrigir `specs/print/spec.md`: "Fidelidade dimensional do documento" com tolerância de 0,05 mm medida sobre o documento, e novo requisito "Tolerância a reescala da impressão" com os três cenários que a captura de fato consome. Resultado: o contrato passa a exigir o que o sistema controla.
- [x] 10b.3 Implementar `tools/parity/fidelidade.mjs`, medindo o PDF a 1200 dpi contra o `LayoutMap` com tolerância de 0,05 mm, e ligá-lo ao CI. Resultado: 32 verificações por documento; web com desvio máximo de 0,039 mm e Android de 0,017 mm, ambos verdes. Provado capaz de falhar contra um PDF deslocado de propósito, que acusou 0,441 mm no marcador e 0,418 mm na bolha.
- [x] 10b.4 Testar a folga dimensional em `commonTest`: o lado nominal do marcador precisa manter o mínimo de 12 mm depois de encolher 5%. Resultado: quatro testes verdes nos três alvos; a 14 mm nominais sobra 13,3 mm no pior caso, e a 12 mm nominais o requisito quebraria.

## 11. Verificação final

- [x] 11.1 Rodar a suíte completa — `packages/domain` nos três alvos, renderizadores, paridade e `apps/api` — e confirmar que todo cenário de `specs/layout-engine/spec.md` e `specs/print/spec.md` tem teste correspondente verde, ou verificação física registrada no caso de 10.2. Resultado: nenhum cenário sem verificação.
  - Suíte rodada em 2026-08-15: `./gradlew build` verde. `packages/domain` 93 testes em JVM, 89 em Node/JS e 89 no unitário Android; `apps/android` 6 locais mais 2 instrumentados no emulador; `apps/api` 69 contra Postgres real; `apps/web` 7 em vitest; comparador de paridade e medidor de fidelidade verdes nos dois PDFs. Zero falhas.
  - Paridade real web × Android medida no emulador: 116 elementos, maior divergência **0,041 mm** contra tolerância de 0,3 mm.
  - Fidelidade do documento a 1200 dpi: 32 verificações por PDF, desvio máximo 0,039 mm no web e 0,017 mm no Android, contra tolerância de 0,05 mm (ADR-0001).
  - Mapa cenário → verificação em `docs/cobertura-fatia-1.md`: **35 de 35 cenários cobertos** — `layout-engine` 22 de 22 e `print` 13 de 13.
