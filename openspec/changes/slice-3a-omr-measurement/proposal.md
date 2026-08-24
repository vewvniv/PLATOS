## Why

A fatia 3 do roadmap (§15) é o primeiro produto vendável: prova objetiva corrigida offline, sem gastar um token. Ela depende de escolher o limiar que separa bolha marcada de bolha vazia — e ADR-0007 proíbe escolher esse número antes de medir um corpus, enquanto ADR-0010 já reservou o corredor de 20% a 40% onde ele terá de cair.

Não existe corpus, e não existe instrumento para produzi-lo. Esta fatia entrega o instrumento e **para antes do número**: ela lê uma folha capturada e devolve a cobertura de tinta de cada bolha, sem decidir se alguma está marcada. É a mesma ordem que a 2b usou — `tinta.mjs` veio antes de qualquer afirmação sobre 7,24% — e é o que dá à fatia seguinte o direito de afirmar um limiar.

O momento é agora por uma razão que não é de plano: as duas digitalizações da conferência de papel da 2b são hoje o **único registro sobrevivente** daquela verificação. A folha física não existe mais. Nada no repositório permite reproduzir os 51,61% que `docs/cobertura-fatia-2b.md` afirma.

## What Changes

- **Contrato primeiro:** o payload do QR de §8 ganha codec único em `capture/`. Hoje a construção e o `crc16` estão **duplicados** entre `LayoutEngine` e `PrintTestSheet`, e esta fatia introduz o primeiro leitor — três consumidores da mesma regra é necessidade comprovada de unificar, e um leitor que divirja dos escritores erra a atribuição da folha em silêncio.
- **Pipeline de captura de §8, dirigido por arquivo de imagem e não por câmera:** detecção dos quatro ArUcos, homografia, retificação da região, decodificação do QR sobre a ROI retificada, e medição de cobertura de cada bolha declarada no `LayoutMap`.
- **A medição é aritmética pura em Kotlin**, separada do OpenCV, e roda em teste de host na JVM sem emulador. A detecção fica no adaptador Android, testada no emulador. §13 já aloca `OpenCV (ArUco + homografia)` ao Android; esta fatia respeita essa alocação e não move nada para o KMP.
- **Redundância de §8 verificada:** `region_idx` do QR conferido contra os IDs dos marcadores detectados, e ambos contra o `LayoutMap`. Folha de outra prova, ou QR de outra região, é recusada com motivo.
- **As duas digitalizações entram como fixture versionada**, junto de um recorte em cinza cru que dispensa decodificador e é idêntico nos três alvos.
- **`OmrMeasurement` nasce no domínio** como contrato entre o OMR e o scoring que virá depois — questão, alternativa, cobertura. O domínio continua sem conhecer pixel.

**O que esta fatia NÃO faz**, de propósito:

- Não escolhe limiar, e não classifica bolha como marcada, vazia ou ambígua. Isso incorporaria o número que ADR-0007 manda medir antes.
- Não calcula nota. Não persiste nada.
- Não usa CameraX, não tem tela, não tem lote nem completude (§8) — isso é fatia posterior.
- Não trata deviants nem região discursiva.
- Não muda a folha, o `LayoutMap`, o pacote publicado nem qualquer golden.

## Capabilities

### New Capabilities

- `capture-omr`: leitura de uma folha capturada contra o `LayoutMap` — identificação da região pelos marcadores, retificação, validação do QR auto-descritivo e medição de cobertura de tinta por bolha. Nesta fatia a capacidade vai até a medição; interpretação e nota entram depois.

### Modified Capabilities

Nenhuma. A unificação do codec do QR preserva byte a byte o payload que os dois escritores já produzem — é movimentação de código, não mudança de comportamento observável, e nenhum golden muda.

## Impact

**Código**

- `packages/domain/.../capture/`: codec do payload do QR (construir, ler, validar CRC e `region_idx`), e o tipo `OmrMeasurement`. Sem tipo de imagem, sem pixel.
- `packages/domain/.../layout/`: `LayoutEngine` e `PrintTestSheet` passam a chamar o codec em vez de cada um ter o seu `crc16`.
- `apps/android/.../vision/`: adaptador OpenCV (ArUco → cantos, homografia, retificação) e ZXing-C++ (QR → payload). Testado no emulador.
- `apps/android/.../omr/`: projeção das bolhas e medição de cobertura, Kotlin puro, testado na JVM.
- `fixtures/`: as duas digitalizações e o recorte em cinza cru. O `androidTest` já monta `fixtures/` como assets, então a fixture da detecção não precisa de encanamento novo.

**Dependências**

OpenCV e ZXing-C++ entram no `apps/android`. Ambos estão declarados em §13 e não exigem ADR.

**Referências**

§8 (pipeline e redundância), §12 e §13 (a captura pertence ao M2/Android), ADR-0001 (a folha impressa tolera ±5% e a homografia absorve), ADR-0007 (critério antes da medição), ADR-0010 (a grandeza é a cobertura; corredor de 20% a 40%).

**Risco herdado, registrado aqui para não sumir**

ADR-0010 define cobertura com "1 = preto pleno", e o papel mostrou a ambiguidade: na digitalização da 2b o miolo preto de um ArUco lê 83 de 255, de modo que toner pleno rende no máximo 64%. Esta fatia implementa a leitura literal já usada por `tinta.mjs` e `papel.mjs` — normalização contra o branco local do papel — e **não** redefine a grandeza. Normalizar também contra o preto do marcador é decisão da fatia do corpus, e tem de ser tomada antes da primeira foto, sob ADR-0007.
