## 1. Base de comparação

- [x] 1.1 Registrar os números atuais antes de tocar em qualquer coisa: hash e tamanho dos três artefatos versionados, contagem da suíte por alvo, e paridade e fidelidade nos dois documentos. Resultado: tabela anotada aqui — esta fatia não deve mover nenhum deles, e sem a base não há como afirmar isso.

  Medido em 2026-08-24, com `./gradlew build :packages:domain:testAndroidHostTest`, `npm test` no
  web e as ferramentas de `tools/parity`.

  | Artefato | sha256 | Tamanho |
  |---|---|---|
  | `fixtures/prova-referencia.layout.json` | `3e31c36b…` | 91 906 B |
  | `fixtures/prova-referencia.package.json` | `26612ad5…` — é o `content_hash` | 101 618 B |
  | `fixtures/folha-de-teste.layout.json` | `81ce134d…` | 6 689 B |

  | Alvo | Testes | Falhas |
  |---|---|---|
  | `packages/domain` JVM · Node · Android host | 228 · 222 · 222 | 0 |
  | `apps/api` (Postgres real) | **não executado** — ver abaixo | — |
  | `apps/android` (host, debug) | 9 | 0 |
  | `apps/web` | 14 | 0 |

  **A primeira versão desta tabela dizia `apps/api` 89, 0 falhas, e era leitura de cache.** O
  Docker não está no ar nesta máquina; o Gradle marcou `:apps:api:test` como UP-TO-DATE, não
  executou nada, e eu li o XML da execução anterior como se fosse medição desta. O erro só
  apareceu na tarefa 2.2, quando uma mudança em `packages/domain` invalidou a tarefa, ela rodou de
  verdade e as 89 falharam em `Could not find a valid Docker environment`.

  É a armadilha que o `CLAUDE.md` nomeia — *desconfie de contagem feita sobre cache* — e ela custa
  ser registrada porque a base ficaria com um número que ninguém mediu. Regra para o resto desta
  fatia: contagem de suíte só entra aqui vinda de execução que **não** foi UP-TO-DATE.

  | Medição | Valor |
  |---|---|
  | Fidelidade da prova | 116 verificações, maior desvio **0,046 mm** (`marcador 0: borda superior`) |
  | Fidelidade da folha de teste | 31 verificações, maior desvio **0,046 mm** |
  | Tinta na prova | 160 bolhas, maior cobertura **7,24%** em `q26/D`; maior trama 4,71%; zero pixel cromático |

  Iguais aos números com que a 2b fechou. **Paridade não foi reexecutada:** o lado Android precisa
  de emulador, nada de renderizador ou de layout mudou desde a 2b, e os três hashes acima provam
  isso. A partir do momento em que esta fatia tocar algo que entre num documento, ela precisa ser
  gerada de novo — e esta fatia não deve tocar.
- [x] 1.2 Rodar `papel.mjs` sobre as duas digitalizações e registrar a saída completa, bolha a bolha. Resultado: o vetor de 160 coberturas fica no repositório como referência da implementação independente, porque é contra ele que a medição oficial vai ser conferida — e porque a folha física não existe mais.

  `papel.mjs` ganhou `--json <saida>`, que grava o que ele mediu sem mexer na saída legível.
  Gravado em `fixtures/prova-referencia.papel.json` (8 723 B, 160 bolhas) e
  `fixtures/folha-de-teste.papel.json` (683 B, 5 bolhas).

  Cada arquivo traz, além da cobertura por bolha: os **quatro cantos detectados** — que é contra
  eles que a tarefa 6.5 vai conferir o OpenCV —, o raio de medição, as dimensões da imagem e uma
  linha declarando o método (`circulo na imagem original, branco local por percentil`). Essa última
  não é decoração: é ela que registra, dentro do próprio arquivo, **por que** a comparação da
  tarefa 5.5 tem tolerância em vez de igualdade — a implementação oficial vai medir sobre a região
  retificada, e esta mede na imagem original.

## 2. Contrato: o codec do payload do QR, antes de existir leitor

- [x] 2.1 Mover a construção do payload de §8 e o `crc16` para um codec único em `packages/domain/.../capture/`, e fazer `LayoutEngine` e `PrintTestSheet` chamarem esse codec em vez de cada um ter a sua cópia. Resultado: uma regra, um lugar; commit próprio, sem leitor ainda.

  `capture/QrPayload.kt`. As duas cópias de `crc16` eram **idênticas caractere a caractere** — o
  tipo de duplicação que sobrevive porque nunca houve quem lesse. Os dois escritores viraram uma
  linha cada: `QrPayload.of(examId, regionIndex)` e `QrPayload.of(SHEET_ID, 0)`.

  Duas guardas que o codec ganhou ao virar um lugar só, e que nenhum dos dois tinha: identificador
  contendo `.` é recusado, porque o separador dentro de um campo torna a leitura de volta ambígua;
  e índice de região negativo é recusado. Antes, qualquer um dos dois produziria um payload que só
  falharia no leitor — que não existia.
- [x] 2.2 Provar que a movimentação não mudou byte nenhum: golden, pacote e folha de teste com os mesmos hashes de 1.1. Resultado: se algum mudar, não foi movimentação — e a tarefa volta.

  Os três hashes intactos: `3e31c36b…`, `26612ad5…`, `81ce134d…`. E o que sustenta a afirmação não
  é o arquivo em disco estar igual — é que os testes **recalculam** o mapa e comparam byte a byte
  com o versionado, nos três alvos: `GoldenLayoutTest`, `PrintTestSheetTest.folha de teste bate
  byte a byte com a versionada` e `ExamPackageTest.o hash da fixture e o mesmo nos tres alvos`.
  Verde em JVM, Node e host do Android.

  **`apps/api` não pôde ser executado** por falta de Docker (ver 1.1). O que ele acrescentaria
  aqui é `ExamPublicationTest`, que carrega o literal do hash do pacote — redundante com o teste
  de domínio que já roda nos três alvos, e não uma verificação diferente. Fica pendente para a
  tarefa 9.1, e não vale como coberto até lá.
- [x] 2.3 Acrescentar a leitura ao codec: ler o payload de volta, validar o CRC e devolver prova, aluno, variante e `region_idx` identificados. Resultado: cobre "Ida e volta do payload" e "CRC não confere".

  `QrPayload.read` devolve `PayloadReading.Read` ou `PayloadReading.Rejected` com motivo legível.
  Treze testes novos, nos três alvos.

  **Recusa em vez de lançar**, e é decisão: quem chama é a leitura óptica, para quem folha
  ilegível é ocorrência esperada — câmera tremida, dobra no papel, folha de outra prova na pilha.
  Exceção ali viraria um `try`/`catch` em volta do laço de captura, e um `catch` largo engoliria
  junto o defeito que ninguém previu.

  **O CRC é conferido sobre o corpo exato lido**, e não sobre o corpo reconstruído a partir dos
  campos já separados. Reconstruir normalizaria justamente a corrupção que o CRC existe para pegar.

  Quase todo teste é de ida e volta, e não de literal: os dois escritores de verdade —
  `LayoutEngine.qrPayloadOf` e `PrintTestSheet.qrPayload` — são chamados e o leitor confere o que
  eles produziram. Um literal no teste concordaria com um leitor errado se o literal tivesse sido
  copiado dele.
- [x] 2.4 Ver a validação do CRC falhar: alterar um caractere do corpo do payload sem recalcular o CRC e confirmar a recusa; alterar corpo **e** CRC de forma consistente e confirmar o aceite. Resultado: o par positivo existe, sem o qual a recusa poderia estar recusando tudo.

  Os dois lados existem: `corpo alterado sem recalcular o CRC e recusado` (troca `prova` por
  `provb` no payload pronto) e `corpo e CRC alterados juntos sao aceitos` (o mesmo `provb`, mas
  gerado pelo escritor). Sem o segundo, um leitor que recusasse **tudo** passaria no primeiro.

  **Visto falhar:** desligada a comparação de CRC em `read`, ficaram vermelhos exatamente dois
  testes — `corpo alterado sem recalcular o CRC e recusado` e `CRC trocado sozinho e recusado` —
  e os outros onze seguiram verdes, o par positivo entre eles. Revertido em seguida.

  Dois testes que não estavam na tarefa e que a implementação pediu: índice de região não numérico
  é conferido **com o CRC correto**, senão ele passaria pelo motivo errado, acusando CRC em vez do
  índice; e o CRC tem largura fixa de quatro dígitos, porque um CRC que perdesse o zero à esquerda
  deslocaria o campo e falharia em apenas 1 payload de cada 16 — defeito que só aparece em
  produção.

## 3. O tipo que atravessa a fronteira

- [x] 3.1 Declarar `OmrMeasurement` em `packages/domain/.../capture/` — questão, alternativa, cobertura — e o resultado da leitura como sucesso identificado ou falha com motivo. Resultado: o contrato entre o OMR e o scoring nasce no domínio, e o domínio continua sem conhecer pixel.

  `capture/OmrMeasurement.kt`: `OmrMeasurement(questionId, option, coveragePerMille)` e
  `OmrReading` com `Read(payload, measurements)` ou `Rejected(reason)`. Nenhum tipo de imagem, e
  nenhum pixel.

  **A cobertura é permilagem inteira, e não `Double` — o design dizia outra coisa e estava
  errado.** `IntegerArithmeticGuardTest` recusou o `Double` na primeira compilação: a guarda de
  D-1.2 varre **todo** o `commonMain`, não só `layout/`. Eu ia pedir exceção; não precisa. Um
  passo de permilagem é 0,1 ponto percentual contra 424 pontos de separação medidos no papel da
  2b, e o `ink_budget` da região já viaja nessa mesma escala — medição e orçamento na mesma
  unidade é o que dispensa conversão na fronteira de um limiar, que é onde mora o erro de um
  passo. O cálculo continua em `Double` dentro de `apps/android`, que a guarda não varre; o
  arredondamento acontece uma vez, na fronteira. Registrado na decisão 3 do `design.md`.

  A guarda fez exatamente o que ela existe para fazer, e vale registrar como ela conseguiu: ela é
  uma regra sobre o **código**, varrendo o fonte, e não sobre um resultado. Um `Double` ali
  passaria em toda a suíte da JVM e divergiria só no alvo JS, possivelmente muito depois.
- [x] 3.2 Confirmar por teste que o tipo não admite veredito: não existe `MARKED`, `BLANK` nem `AMBIGUOUS`, e nada nele carrega limiar. Resultado: o corte da fatia fica afirmado no tipo, e não só na prosa.

  Sete testes. O que prende o conjunto de campos é o `toString` de uma `data class`, que lista
  todas as propriedades do construtor: acrescentar `marcada: Boolean` derruba
  `a medicao tem exatamente tres campos, e nenhum deles e veredito`, e a conversa sobre ADR-0007
  acontece antes do commit em vez de depois de o limiar estar em produção.

  Duas coisas que a primeira versão errou e que ficam registradas: eu escrevi um laço morto que
  não afirmava nada, e uma comparação que dependia da formatação de `Double` — que pode divergir
  entre JVM e JS. As duas sumiram quando a cobertura virou inteiro.

## 4. Fixtures

- [x] 4.1 Versionar as duas digitalizações da conferência de papel da 2b em `fixtures/`, com procedência anotada. Resultado: `docs/cobertura-fatia-2b.md` passa a ter como ser reproduzido; hoje ele afirma 51,61% sem nada que sustente a repetição.

  `fixtures/prova-referencia.digitalizacao.jpg` (419 KB) e `folha-de-teste.digitalizacao.jpg`
  (396 KB), com os `.papel.json` regerados apontando para os nomes versionados. Procedencia em
  `docs/protocolo-medicao-impressa.md` §10: impressa por terceiro em 2026-08-22, digitalizada em
  equipamento que nao permitia escolher nem exibir o dpi, folha fisica perdida.

  **`.gitattributes` nao cobria JPEG**, e o comentario que ja estava la sobre PNG faz exatamente o
  argumento que se aplica: `text=auto` provavelmente acertaria pela heuristica, mas a aposta nao
  vale quando um byte `0x0D` convertido corromperia a unica prova de papel que existe. Entraram
  `*.jpg`, `*.jpeg` e `*.pgm` como `binary`, antes de qualquer binario ser versionado.
- [x] 4.2 Gerar e versionar o recorte em cinza cru da região de gabarito, com cabeçalho de largura e altura. Resultado: fixture de medição que dispensa decodificador e é idêntica nos três alvos; o tamanho do recorte é decidido aqui (design, questão aberta 2).

  `tools/parity/recorte.mjs` produz `fixtures/prova-referencia.recorte.pgm`: 1660x850 a 10 px/mm,
  1,41 MB crus e 661 KB comprimidos no git. PGM P5 porque o cabecalho e texto e o corpo e byte
  cru — Kotlin le sem decodificador, e decodificador de JPEG nao e deterministico entre
  plataformas.

  **Questao aberta 2 do design resolvida em 10 px/mm**, que e quase a resolucao nativa da
  digitalizacao (9,92): reamostragem minima. Descer para 6 px/mm economizaria metade do arquivo e
  **mudaria o numero medido**, que e justamente o que a fixture existe para preservar. A
  reamostragem e por media de area, e nao bilinear pontual: bilinear amostra quatro vizinhos de um
  ponto e descarta o que cai entre as amostras — numa bolha de 4 mm, o que se descarta e tinta.

  Conferido na hora: a medicao sobre o recorte diverge de `papel.mjs` em **4,91 por mil na media**
  e 18,03 no pior caso, e o pior e numa bolha vazia. Isso responde a **questao aberta 1** do
  design com numero medido, e virou a tolerancia de 20 por mil da tarefa 5.5.
- [x] 4.3 Escrever o gerador de buffer sintético: bolha preenchida a uma fração conhecida **por construção**, com variante distorcida em perspectiva. Resultado: o oracle analítico da fatia — a resposta vem de quem desenhou, sem compartilhar código com quem mede.

  `SyntheticRegion`: pinta uma faixa horizontal cuja area dentro do circulo e exatamente a fracao
  pedida, com a altura saindo por bissecao sobre a formula fechada do segmento circular. Nenhuma
  linha compartilhada com `BubbleMeter`.

  **O gerador precisou ser suavizado, e a razao vale mais que o gerador.** A primeira versao
  pintava pixel inteiro conforme o centro, e a medicao saia **15 por mil abaixo** do esperado em
  toda fracao. Parecia erro do medidor; era vies do oracle — sobre uma corda de ~37 px, meio pixel
  de fronteira vale 15 por mil de cobertura. Um oracle enviesado e pior que nenhum, porque ele
  acusa quem esta certo.

  A variante distorcida em perspectiva **nao** foi escrita: ela so tem consumidor na tarefa 7.4, no
  emulador, e escrever agora seria codigo sem teste. Fica com ela.

## 5. O núcleo puro: projetar e medir

- [x] 5.1 Implementar em `apps/android/.../omr/` a projeção das bolhas declaradas no `LayoutMap` sobre a região retificada. Resultado: cobre "Bolhas vêm do mapa"; a posição vem do mapa, e nada é inferido da imagem.

  `RectifiedRegion` e `BubbleMeter`, Kotlin puro, sem OpenCV e sem `android.graphics`. O buffer
  cobre exatamente o quadrilatero da regiao, entao projetar `(u,v)` e regra de tres — a homografia
  ficou no adaptador, como §13 aloca.

  Registrado no codigo por que **nao** se detecta circulo na imagem, que pareceria mais robusto:
  uma bolha inteiramente preenchida a caneta deixa de ter contorno visivel, e o detector nao
  acharia justamente as que importam.
- [x] 5.2 Implementar a medição de cobertura de ADR-0010 — média de escuridão no disco, raio igual ao círculo desenhado menos o traço, normalizada contra o branco local do papel —, com a normalização isolada num ponto só. Resultado: cobre "Medição de todas as bolhas"; a fatia do corpus consegue trocar a normalização sem reescrever a medição.

  `PaperWhite` e o unico lugar do OMR que decide o que e branco, e o KDoc dele diz por que: a fatia
  do corpus provavelmente vai normalizar tambem contra o preto do ArUco, e isso precisa ser uma
  mudanca num arquivo, nao uma cacada por normalizacoes espalhadas.
- [x] 5.3 Tratar janela vazia, janela fora da imagem e valor não finito como falha explícita que identifica a bolha. Resultado: cobre "Janela de medição inválida" — `NaN` comparado com qualquer coisa é falso e passaria calado.

  Quatro recusas, todas nomeando a bolha ou a causa: disco fora da regiao, janela vazia, cobertura
  nao finita, e captura escura demais para achar o branco. A ultima tem piso proprio em
  `PaperWhite` — sem ele, um bloco preto daria branco perto de zero e a divisao jogaria a cobertura
  de qualquer bolha ali para o teto, em silencio.
- [x] 5.4 Conferir a medição contra o buffer sintético **não distorcido**. Resultado: cobre "Cobertura conferida contra valor conhecido" por oracle analítico, na JVM, sem emulador.

  Seis fracoes de 0,1 a 1,0 sobre 32 bolhas, mais um teste de monotonicidade — que pega o caso em
  que a media acerta por acidente mas perdeu a relacao com o que foi pintado — e um de
  contaminacao entre vizinhas, com a coluna A cheia ao lado de vazias.

  **A tolerancia e 12 por mil e tem duas origens medidas:** 5 da corda da faixa e 11 da coroa do
  disco, que so aparece na fracao 1,0 porque o gerador suaviza a borda do circulo e o medidor usa
  mascara dura. Dava para eliminar a segunda dando borda dura ao gerador — e nao foi, de proposito:
  isso faria o oracle compartilhar a rasterizacao do disco com quem ele julga.

  Vale registrar que a primeira versao **tambem** usava 12 e passava por acidente, escondendo os 15
  por mil de vies do gerador. Mesmo numero, evidencia diferente.

  *Corrigido em 2026-08-24, com o consentimento de quem pediu a fatia.* A redação original pedia
  também a variante "distorcida e retificada de volta", mas pelo design a retificação é do OpenCV,
  que só existe no Android. Um teste de host teria de retificar por conta própria — e aí não
  estaria conferindo o retificador de produção, que é justamente o que precisa ser conferido. A
  metade distorcida virou a tarefa 7.4, no emulador.
- [x] 5.5 Conferir a medição contra o recorte real e a saída de `papel.mjs` de 1.2. Resultado: a tolerância entre as duas implementações é fixada aqui, pela primeira execução, e a diferença de método fica registrada — a oficial mede retificado, `papel.mjs` mede na imagem original.

  `RealSheetTest`, quatro testes sobre as 160 bolhas da folha real. Divergencia dentro de **20 por
  mil**, declarada como distancia entre dois metodos e nao como folga para ruido: se crescer,
  alguma das duas mudou.

  O teste mais util nao e o da tolerancia, e o da **separacao**: a implementacao oficial preserva a
  propriedade que a fatia 3 herda — nenhuma vazia alcanca o corredor de 200 e nenhuma caneta desce
  ate 400.
- [x] 5.6 Ver a medição falhar: deslocar as bolhas declaradas meio passo horizontal e confirmar que a cobertura da caneta desaba. Resultado: registrado com o número obtido; `papel.mjs` já mostrou 60% caindo para 25,96% com 2,6 mm.

  Virou **teste permanente**, e nao experimento manual: `bolhas deslocadas meio passo derrubam a
  cobertura da caneta` desloca as bolhas declaradas em 2,6 mm e exige que a caneta caia a menos da
  metade. Assim a guarda roda a cada execucao, em vez de valer so no dia em que alguem a rodou.

  Numeros medidos: caneta na geometria certa **514 por mil**, deslocada **220** — queda de 294
  pontos, parando no piso do corredor.

  E aqui esta a evidencia mais forte da fatia ate agora, e ela apareceu de graca: 514 contra os
  **516** que `papel.mjs` mediu na mesma bolha. Dois pontos de diferenca, por duas implementacoes
  em linguagens diferentes, sobre metodos diferentes — uma retifica e a outra nao.
- [x] 5.7 Conferir que iluminação irregular não vira tinta: escurecer parte do buffer sintético e confirmar que as bolhas vazias dessa parte continuam comparáveis às demais. Resultado: cobre "Papel escurecido não vira tinta", que é a razão de a normalização ser local.

  Metade da regiao escurecida a 75%: as bolhas vazias dessa metade continuam abaixo de 5 por mil.
  Com normalizacao contra 255 fixo elas mediriam ~250 e cairiam **dentro** do corredor do limiar —
  ou seja, uma sombra viraria resposta.

## 6. O adaptador: achar

- [x] 6.1 Acrescentar OpenCV e ZXing-C++ ao `apps/android` e confirmar que o build e o job de emulador continuam passando sem nenhum uso ainda. Resultado: o custo das duas bibliotecas nativas aparece isolado, antes de qualquer código depender delas.

  `org.opencv:opencv:4.11.0` e `io.github.zxing-cpp:android:2.3.0`, as duas do Maven Central,
  nenhuma exigindo NDK nem build customizado. Build verde e **10 testes instrumentados no
  emulador** com as duas no classpath e nenhum uso — que e o estado que esta tarefa pede.

  **O custo apareceu, e e grande.** APK universal de **143,1 MB**: os `.so` do OpenCV pesam 56,1 MB
  em `x86_64`, 42,4 em `x86`, 24,5 em `arm64-v8a` e 16,8 em `armeabi-v7a`. Acima do que a loja
  aceita como artefato unico, antes de a fatia 4 acrescentar qualquer coisa.

  **Resolvido por App Bundle, e a escolha e melhor que a que eu tinha proposto.** Eu sugeri cortar
  ABIs — o que excluiria aparelho ARM de 32 bits, e o publico e escola publica, onde aparelho
  antigo dura. Quem pediu a fatia escolheu App Bundle: o AAB tem 60,2 MB e **cada aparelho baixa so
  a sua** — 24,5 MB num arm64 moderno, 16,8 MB num ARM de 32 bits. Nenhum aparelho fica de fora.

  A divisao por ABI ficou **declarada** em `bundle { abi { enableSplit = true } }` em vez de
  herdada do padrao: um padrao futuro do AGP que mude apareceria como um download de 143 MB para o
  professor.

  Fica registrado o que isso **nao** resolve: o APK de debug continua universal, entao o job de
  emulador instala 143 MB por execucao. Nao mexi nisso porque restringir ABI de debug e outra
  decisao, e ela nao pertence a esta tarefa.
- [x] 6.2 Implementar em `apps/android/.../vision/` a detecção dos quatro ArUcos e a homografia, com retificação por média de área. Resultado: cobre "Captura em perspectiva"; a média de área é o que impede a reamostragem de borrar a tinta de uma bolha de 4 mm.

  `apps/android/.../vision/RegionDetector.kt`. Duas decisoes que o plano nao previa, as duas
  registradas no KDoc porque mudam o que se consegue afirmar:

  **A homografia sai dos centros; o erro e medido nos cantos.** Com exatamente quatro pares de
  pontos a homografia fecha exata por construcao — um erro de reprojecao calculado sobre os mesmos
  quatro pontos daria **zero sempre**, e a tarefa 6.3 estaria pedindo uma verificacao que nunca
  falha. Os quatro marcadores tem 16 cantos, e eles nao entram no ajuste: mapeados pela homografia,
  eles dizem se a geometria fecha de verdade.

  **`warpPerspective` nao aceita `INTER_AREA`.** O design mandava retificar por media de area, e a
  funcao do OpenCV so amostra pontos. Reduzir uma foto de celular direto para 10 px/mm descartaria
  o que cai entre as amostras — numa bolha de 4 mm, o que se descarta e tinta. A solucao e
  desempenar a **3x** o tamanho alvo e reduzir com `Imgproc.resize(..., INTER_AREA)`, que integra a
  celula inteira.
- [x] 6.3 Recusar captura com menos de quatro marcadores, com marcadores que não correspondam aos `marker_ids` da região, ou com erro de reprojeção acima da tolerância. Resultado: cobre "Marcador faltando", "Folha de outra prova sob estes marcadores" e "Geometria que não fecha".

  Quatro recusas, todas com motivo identificavel: nenhum marcador na captura, marcador faltando com
  os identificadores achados e esperados na mensagem, erro de reprojecao acima do teto de 6 px, e
  erro nao finito.

  A recusa por geometria e conferida **pelo motivo**, e nao so pelo fato: a folha de teste de
  impressao usa os mesmos quatro identificadores de marcador que a prova, e so difere na altura da
  regiao. Ler uma contra o mapa da outra tem de reprovar **na reprojecao** — se reprovasse por
  identificador, a guarda estaria protegendo outra coisa e ninguem notaria.
- [x] 6.4 Decodificar o QR sobre a ROI já retificada e conferir o `region_idx` contra os identificadores dos marcadores encontrados. Resultado: cobre "Payload íntegro" e "QR de outra região" — a redundância que §8 pede, para que a atribuição não dependa de um canal só.

  `apps/android/.../vision/RegionQrReader.kt`, com ZXing-C++ sobre a ROI **que o mapa declara** —
  `region.qr` traz posicao e tamanho normalizados ao quadrilatero, entao a busca acontece num
  retangulo conhecido em vez de no quadro inteiro. Procurar QR na foto toda acharia tambem o da
  folha do aluno ao lado, na mesa.

  A validacao nao mora ali: o adaptador transforma pixel em texto, e quem confere CRC e formato e
  `QrPayload` no dominio. Mesma fronteira do resto da fatia.

  **O payload lido bate com o que o papel ja tinha dito.** Na conferencia da 2b, um celular leu
  `prova-referencia-slice-1...0.05CB` na folha impressa; agora o caminho de producao le a mesma
  coisa a partir da digitalizacao, depois da homografia. Duas leituras independentes, com meses e
  um scanner de permeio.

  **A redundancia de §8 esta implementada e foi vista falhar.** O `region_idx` do payload e
  conferido contra os identificadores de marcador realmente achados na captura, via
  `CaptureGeometry.markerIdsOf`. Desligada essa comparacao, ficou vermelho exatamente um teste —
  `qr_de_outra_regiao_e_recusado_pelos_marcadores` — e os outros doze seguiram verdes. Revertido.

  Tres testes novos no emulador: payload integro, QR de outra regiao recusado pelos marcadores, e
  ROI apagada recusada — este ultimo garantindo que o decodificador nao va buscar QR noutro canto
  do quadro quando nao acha no lugar certo.

  Uma nota de implementacao que custou tempo e vale registrar: `ALPHA_8` seria o formato natural
  para um buffer cinza e o decodificador o recusa, porque le luminancia de canais de cor. O cinza e
  replicado nos tres canais explicitamente, em vez de deixar uma conversao implicita decidir.
- [x] 6.5 Conferir no emulador que os quatro cantos achados pelo OpenCV coincidem com os achados por `papel.mjs`, dentro de tolerância declarada. Resultado: a metade de detecção fica provada por dois caminhos que não se conhecem — componentes conexos contra contorno e dicionário.

  `RegionDetectorInstrumentedTest`, cinco testes no emulador. Os quatro centros achados pelo OpenCV
  coincidem com os de `papel.mjs` dentro de **3 px** — 0,3 mm a 9,92 px/mm —, e as duas definicoes
  de "centro" nem sao a mesma: o OpenCV usa a media dos quatro cantos do contorno, `papel.mjs` usa
  o centro da caixa envolvente dos pixels escuros.

  **Ficou provado de quebra algo que ninguem tinha conferido:** o `ArucoDictionary` que o Layout
  Engine **desenha** e mesmo o `DICT_5X5_100` que o OpenCV **le**. Ate aqui as duas pontas viviam
  de suposicao — a folha desenhava de um dicionario proprio, em Kotlin, e nunca ninguem tinha
  passado um leitor de verdade nela.

  O caminho inteiro de §8 menos o QR tambem fecha: imagem -> ArUcos -> homografia -> retificacao ->
  cobertura, contra a referencia versionada, dentro de **30 por mil**. A tolerancia e maior que os
  20 do teste de host porque aqui ha uma diferenca a mais — a retificacao e a de producao, e nao a
  de `recorte.mjs`.

  **Visto falhar:** deslocado o centro do marcador em 5 px, ficaram vermelhos exatamente dois
  testes — a comparacao de cantos e a de cobertura — e os outros oito seguiram verdes. Revertido.

## 7. Ponta a ponta

- [x] 7.1 Ligar adaptador e núcleo: da imagem em `fixtures/` até o vetor de `OmrMeasurement`, no emulador. Resultado: o pipeline de §8 percorrido inteiro sobre papel real, sem câmera e sem tela.

  `vision/SheetReader.kt` compoe o pipeline de §8 na ordem que a arquitetura fixa: detecta ArUcos,
  identifica a regiao, monta a homografia, decodifica o QR na ROI ja retificada e so entao mede as
  bolhas. A saida e `OmrReading` — o tipo do dominio.

  A ordem nao e detalhe: o QR so e procurado depois da retificacao porque e la que ele fica facil e
  porque o mapa diz onde ele esta; as bolhas so sao medidas depois de a identidade fechar, porque
  medir uma folha que nao se sabe de quem e produz numero sem dono.

  Ponta a ponta no emulador, sobre a digitalizacao versionada: **160 medicoes**, payload
  `prova-referencia-slice-1`, regiao 0. Recusa se propaga com motivo em vez de devolver medicao
  parcial.
- [x] 7.2 Confirmar que a mesma captura lida duas vezes produz medição idêntica, e que a leitura não altera captura, mapa nem estado. Resultado: cobre "Leitura repetida" e "A leitura é local, determinística e sem efeito".

  Dois testes. A mesma captura lida duas vezes da payload e medicoes **identicos** — comparacao de
  lista inteira, e nao de resumo.

  O de ausencia de efeito confere as duas pontas: o buffer da captura byte a byte antes e depois, e
  o `toCanonicalJson()` do mapa. O segundo importa mais do que parece — o mapa e o artefato
  publicado e hasheado, e uma leitura que o mutasse corromperia a prova de todo mundo, nao so
  daquela folha.
- [x] 7.3 Confirmar que a leitura conclui com o aparelho offline. Resultado: cobre "Sem rede"; nada no caminho pode ter adquirido dependência de rede sem alguém notar.

  **O teste mudou de metodo no meio, e a versao original estava errada.** Eu tinha escrito um teste
  que desligava o radio do emulador com `svc wifi disable` / `svc data disable` e depois lia a
  folha. Duas coisas deram errado, e as duas ensinam:

  Primeiro, **nao funcionou**: o `svc data disable` nao derrubou a rede neste emulador. Mas a
  guarda que eu tinha posto — conferir o estado da conectividade antes de concluir — recusou passar
  um teste que nao estava testando nada. Sem ela, ele seria verde para sempre sem provar coisa
  alguma.

  Segundo, e pior: **mesmo funcionando, ele provaria o proxy e nao a afirmacao.** "O radio esta
  desligado" nao e "este codigo nao usa rede". A afirmacao de §10 e sobre o codigo.

  A versao final usa `StrictMode` com `detectNetwork` e `penaltyDeath`, que mata a thread no
  instante em que qualquer socket for aberto no caminho da leitura. E acompanha **meta-teste**: uma
  conexao de verdade tem de morrer sob a mesma politica, senao `detectNetwork` poderia estar
  desligado por versao de API e o teste de cima passaria vazio.
- [x] 7.4 Conferir o buffer sintético **distorcido em perspectiva** percorrendo o retificador de produção: a fração conhecida por construção sobrevive ao caminho distorce-retifica dentro de tolerância declarada. Resultado: cobre "Captura em perspectiva" pelo oracle analítico, e mede o que a reamostragem por média de área custa em pontos de cobertura. Veio da tarefa 5.4, que não podia executá-la na JVM.

  `RectifierInstrumentedTest`: folha sintetica com os **quatro ArUcos de verdade** — desenhados de
  `DrawAruco.modules`, o mesmo dado que o renderizador usa, entao dicionario errado nao acha nada —
  e as bolhas em fracao conhecida, distorcida em perspectiva e devolvida ao `RegionDetector`.

  As duas fracoes sao 0,5 e 1,0 de proposito: as duas dispensam calculo de area. Meia bolha e a
  corda pelo centro, cheia e o disco inteiro. Um oracle que precisa de aritmetica propria e um
  oracle que pode errar.

  **O que o retificador custa, medido:**

  | Fracao | De frente | Em perspectiva |
  |---|---|---|
  | 0,5 | 8 por mil | 17 |
  | 1,0 | 22 por mil | 30 |

  A distorcao custa **cerca de 9 pontos**, e e esse numero que o teste vigia. O residuo de frente e
  a coroa do disco — o gerador suaviza a borda, o medidor usa mascara dura —, e cresce com a tinta.

  **Duas versoes deste teste passaram medindo a coisa errada antes de eu perceber.** A primeira
  usava tolerancia de 40 sobre um gerador de borda dura que sozinho ja punha 20 por mil de vies: a
  perspectiva podia ter dobrado de custo sem ninguem ver. Suavizado o gerador, o vies caiu e as
  tolerancias viraram 26 e 36, com folga de quatro e seis passos sobre o pior caso medido. E a
  terceira vez nesta fatia que uma tolerancia folgada quase escondeu o que ela devia medir.

## 8. CI

- [x] 8.1 Pôr os testes de host do OMR no job que já existe, e os instrumentados no job de emulador. Resultado: a medição roda a cada CI, e não só na máquina de quem escreveu.

  **Os testes novos ja rodavam, e vale dizer por que:** o job `build` chama `./gradlew build`, que
  arrasta `:apps:android:testDebugUnitTest`, e o job `paridade` chama
  `:apps:android:connectedDebugAndroidTest`. Nenhuma linha de CI precisava mudar para os 23 de host
  e os 21 de emulador entrarem.

  Duas coisas precisavam, e nao eram testes:

  - O passo do emulador se chamava **"PDF do Android no emulador"** e agora roda tambem deteccao de
    ArUco, homografia, retificacao e QR. Nome que descreve metade do que um passo faz e nome que
    engana quem le o log procurando onde algo quebrou.
  - O `disk-size` do emulador era **4096M**, com um comentario dizendo "o teste escreve um PDF de
    ~146 KB". Deixou de ser verdade quando o APK de debug passou a carregar os `.so` do OpenCV nas
    quatro ABIs — sozinho, 143 MB. Subiu para 6144M.
- [x] 8.2 Pôr no CI o defeito deliberado de 5.6, falhando o job se a medição aceitar bolhas deslocadas. Resultado: uma verificação que nunca falha é pior que nenhuma — é a lição que a 2b registrou.

  **Ja esta no CI, e nao por um passo novo:** o defeito de 5.6 virou teste permanente
  (`bolhas deslocadas meio passo derrubam a cobertura da caneta`), entao ele roda em toda execucao
  de `./gradlew build`. Um passo de CI separado seria a mesma verificacao duas vezes.

  O que **faltava** era outra coisa, e ela apareceu ao escrever esta tarefa: as tres fixtures
  derivadas da digitalizacao — os dois `.papel.json` e o `.recorte.pgm` — sao geradas por
  ferramenta e versionadas, e **nada as verificava**. Se a imagem e o vetor deixassem de
  corresponder, os testes do OMR continuariam verdes comparando com uma referencia que nao descreve
  mais a folha. E o modo de falha mais caro que esta base tem: silencioso, e sobre a unica
  evidencia de papel que sobrou.

  Passo novo no job `web`, no mesmo padrao do "conversao de formula esta em dia" que ja existia:
  regenera as tres a partir da imagem versionada e compara. As duas ferramentas foram conferidas
  deterministicas antes.

  **Visto falhar:** alterada uma unica cobertura no vetor de referencia — `q01/B` de 0,639896 para
  0,539896 —, o `diff` aponta a linha e sai com codigo 1.

## 9. Verificação e registro

- [x] 9.1 Rodar a suíte nos alvos afetados e comparar com a base de 1.1, mais paridade e fidelidade nos dois documentos. Resultado: tabela final ao lado da inicial, com toda diferença explicada por uma causa desta fatia — e os três hashes intactos, porque esta fatia não muda folha.

  `./gradlew build :packages:domain:testAndroidHostTest --rerun-tasks`, `npm test` no web,
  `connectedDebugAndroidTest` no emulador e as ferramentas de `tools/parity`. **Sem cache**, pela
  regra que passou a valer depois do erro da tarefa 1.1.

  | Alvo | Base (1.1) | Fim da fatia | Diferenca |
  |---|---|---|---|
  | `packages/domain` JVM | 228 | **249** | +21 |
  | `packages/domain` Node/JS | 222 | **243** | +21 |
  | `packages/domain` Android (host) | 222 | **243** | +21 |
  | `apps/android` (host) | 9 | **23** | +14 |
  | `apps/android` (emulador) | 5 | **21** | +16 |
  | `apps/api` (Postgres real) | nao executado | **89** | — |
  | `apps/web` | 14 | **14** | — |

  Falhas: **zero** em todos. Os 21 comuns novos rodam nos tres alvos: 13 do codec do payload, 7 do
  contrato de medicao, 1 da escala.

  Os 89 de `apps/api` fecham a lacuna que a tarefa 1.1 abriu e a 2.2 herdou — o Docker subiu, e
  desta vez o numero veio de execucao e nao de XML antigo.

  | Medicao | Base (1.1) | Fim da fatia |
  |---|---|---|
  | Fidelidade da prova (web) | 116, 0,046 mm | 116, **0,046 mm** |
  | Fidelidade da prova (Android) | — | 116, **0,042 mm** |
  | Fidelidade da folha de teste | 31, 0,046 mm | 31, **0,046 mm** |
  | Paridade da prova | nao executada | **185 de 185, 0,048 mm**, trama 0,39 ponto |
  | Paridade da folha de teste | nao executada | **9 de 9**, trama 0,39 ponto |
  | Tinta na prova | 160 bolhas, 7,24% | 160 bolhas, **7,24%** |
  | Os tres hashes | `3e31c36b…` `26612ad5…` `81ce134d…` | **identicos** |

  **Nenhuma diferenca a explicar, e essa era a afirmacao.** Esta fatia nao toca folha: os tres
  hashes intactos e a paridade e a fidelidade identicas as da 2b sao a prova. A paridade rodou pela
  primeira vez desde a 2b, contra os PDFs gerados agora pelo emulador, e deu os mesmos numeros.
- [x] 9.2 Escrever `docs/cobertura-fatia-3a.md`: cada cenário da spec mapeado para a verificação que o cobre, e cada verificação crítica com **como ela foi vista falhar**. Resultado: nenhum cenário sem verificação, e nenhuma verificação crítica sem vermelho observado.

  `docs/cobertura-fatia-3a.md`: **15 cenarios, 15 cobertos**, mais dois invariantes que a fatia
  decidiu afirmar sobre o tipo — que o contrato nao carrega veredito, e que a leitura nao altera
  captura nem mapa.

  A tabela de defeitos deliberados traz seis linhas, cada uma registrando tambem **o que as outras
  verificacoes disseram**. A mais util nao e sobre o OMR: um `Double` em `commonMain` nao seria
  pego por teste de valor nenhum — ele passa na JVM e diverge so no alvo JS —, e quem o pegou foi a
  guarda que varre o **fonte**.

  O documento abre com a licao que se repetiu tres vezes: **tolerancia folgada e o jeito mais comum
  de um teste de medicao nao medir nada.** As tres estao nomeadas com o numero antes e depois.

  E fecha com o que **nao** esta coberto, sem eufemismo: o limiar (de proposito, ADR-0007), a camera
  inteira, e o erro humano — ninguem fotografou folha amassada, dobrada ou com sombra de mao.
- [x] 9.3 Registrar no protocolo de medição como reproduzir a leitura sobre uma digitalização, agora que existe implementação oficial além de `papel.mjs`. Resultado: o §10 do protocolo passa a apontar as duas, e a diferença de método entre elas fica escrita onde quem mede vai ler.

  Duas secoes novas no protocolo. A primeira registra a **procedencia** das digitalizacoes
  versionadas — impressas por terceiro em 2026-08-22, dpi desconhecido, folha perdida — junto das
  duas propriedades que quem for usa-las como referencia precisa saber: impressao a ~97% e scanner
  que le toner pleno a 64%.

  A segunda responde a pergunta que a fatia cria: **por que `papel.mjs` continua existindo** agora
  que ha implementacao oficial. Tabela comparando os dois caminhos — linguagem, detector, o que
  cada um mede, onde roda — e o registro de que concordam dentro de 20 por mil, com 514 contra 516
  na bolha que mais importa.
- [x] 9.4 Rodar `openspec validate --strict` e conferir que a spec delta aplica sobre as specs vigentes. Resultado: mudança pronta para arquivar.

  `openspec validate --all --strict`: **6 de 6**, as cinco specs vigentes e a mudanca.

  A delta aplica sem conflito possivel: `capture-omr` e **capability nova** — nao existe
  `openspec/specs/capture-omr/` —, a delta tem so `## Purpose` e `## ADDED Requirements`, e nenhum
  `MODIFIED`, `REMOVED` ou `RENAMED`. Cinco requisitos, quinze cenarios.

  A unificacao do codec do QR (tarefa 2.1) nao gerou delta em `layout-engine` de proposito: ela
  preserva o payload byte a byte, e os tres hashes intactos provam. Movimentacao de codigo nao e
  mudanca de comportamento observavel.
