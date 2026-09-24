# Cobertura — `slice-5a-regiao-discursiva`

Primeira mudança da fatia 5. Proposta, specs, design e tarefas em
`openspec/changes/slice-5a-regiao-discursiva/`. Este documento registra **como** cada verificação foi
vista falhar, e não que ela passa (`rigorous.md` §8). As datas são UTC.

## 0. Linha de base, antes do primeiro commit de código

**0.1 — os comandos cheios sobre `main` (`97aa59f`) mais o commit da proposta (`b59785d`), que só
acrescenta arquivos em `openspec/changes/`.**

| Comando | Resultado | Âncora |
|---|---|---|
| `npm test` em `tools/math` | 17 de 17; `node build.mjs --check`: "conversao em dia: 21 formulas" | 2026-09-24T10:56:52Z (início) |
| `npx vitest run` em `apps/web` | 14 de 14, 1 arquivo | 2026-09-24, início às 12:56:32 no relógio local (UTC+2), 10:56:32Z |
| `./gradlew build --rerun-tasks` | `BUILD SUCCESSFUL in 4m 23s`, **183 de 183 tasks executadas**; fim às 11:00:42Z | relatórios de 10:59:22Z a 11:00:39Z, abaixo |
| `./gradlew -p buildSrc test --rerun-tasks` | `BUILD SUCCESSFUL in 26s`, 6 de 6 executadas | fim às 11:01:45Z |
| `./gradlew :apps:android:connectedDebugAndroidTest` no `platos-atd34` (API 34, `aosp_atd`, x86_64) | 83 testes em 17 suítes, 0 falhas, 2 pulados; 2 tasks executadas e 86 `UP-TO-DATE`, porque a compilação acabara de rodar no `build` | `timestamp` 2026-09-24T11:02:34 no relatório, arquivo gravado às 11:02:34Z |

Os relatórios do `build`, somados por diretório de task por um script no scratchpad
(`relatorios.mjs`), que lê o `timestamp` de cada XML e marca como **VELHO** o anterior ao início desta
sessão:

| Task | Testes | Falhas | `timestamp` |
|---|---|---|---|
| `apps/android` `testDebugUnitTest` | 310 | 0 | 10:59:36Z .. 10:59:40Z |
| `apps/android` `testReleaseUnitTest` | 310 | 0 | 10:59:22Z .. 10:59:28Z |
| `apps/api` `test` | 167 | 0 | 10:59:58Z .. 11:00:19Z |
| `packages/domain` `jsNodeTest` | 321 | 0 | 11:00:26Z .. 11:00:28Z |
| `packages/domain` `jvmTest` | 329 | 0 | 11:00:30Z .. 11:00:32Z |
| `packages/domain` `testAndroidHostTest` | 321 | 0 | 11:00:37Z .. 11:00:39Z |

O relatório de `buildSrc/build/test-results/test` saiu marcado **VELHO** (2026-09-23T21:12:27Z):
`./gradlew build` não roda os testes de `buildSrc`, que o CI roda num passo à parte. Por isso ele foi
rodado separadamente, na linha acima. A soma dos seis diretórios da tabela é 1758, e ela não inclui
`buildSrc` nem a suíte instrumentada.

**0.2 — a guarda do registro de dívida, antes de qualquer edição.** `node tools/divida/divida.mjs`,
`exit 0`:

```
fatia corrente: 5a, de slice-5a-regiao-discursiva (ativa)
eventos declarados: nenhum
...
vence nesta fatia (5a):
  Acurácia em manuscrito (`5`)
  Modo degradado (§10) não existe (`5`)
  O limiar do OMR foi apurado sobre um aparelho e uma impressora (`5`)

nenhuma linha vencida: 18 linhas lidas
```

## 1. A linha `5b` no §16 (tarefa 1.1)

A linha "A região discursiva ainda não passou pelo aparelho nem pelo papel" entrou com token `5b`.

- **Na árvore real:** a guarda a lista `em dia`, lê 19 linhas e sai com `0`.
- **Vista falhar:** com `--mudancas` apontando para uma cópia de `openspec/changes/` no scratchpad,
  acrescida de um diretório vazio `slice-5c-sonda`, a guarda deriva "fatia corrente: 5c" e sai com
  `1`. A única linha nomeada é esta: `::error::linha vencida sem reconciliacao: A região discursiva
  ainda não passou pelo aparelho nem pelo papel (\`5b\`): a fatia 5b ja passou, e a corrente e 5c`.
  As três linhas `5` passam a aparecer em "vence nesta fatia (5c)", e não como vencidas, o que está
  certo: `5` vence na 6.
- **A sonda foi apagada**, e a guarda sobre a árvore real voltou a sair com `0`.

## 2. O contrato, sozinho (tarefas 2.1 e 2.2)

**2.1 — o pacote do contrato anterior, congelado** (`ad24976`).
`fixtures/pacote-antes-da-discursiva.json` é cópia byte a byte de `fixtures/prova-referencia.package.json`
antes de qualquer mudança de contrato: `cmp` igual, 101.637 bytes, sem quebra de linha no fim. O
`sha256` dos bytes é `277d2f8cd0a7a87e6e26e5ecf47d2f5610dd6e173e724ab38a65373000ac391a`, o mesmo literal
que `ExamPackageTest.kt:21` e `ExamPublicationTest.kt:32` fixavam para a prova de referência. É esse
literal, e não um campo dentro do pacote, que ancora o arquivo. A tarefa foi corrigida ao ser
executada, com a razão escrita nela (P7).

**2.2 — os tipos, e o vermelho previsto.** O commit acrescenta `AnswerCaptureMode`, `Rubric`,
`RubricCriterion` e `RubricDescriptor`. `Question` ganha `rubric` e `answer_capture_mode`, e
`PackageItem` ganha `kind`, `rubric` e `answer_capture_mode`. `ScannableRegion` ganha `qr_id`
(obrigatório), `question_id` e `answer_area`. `AssignmentQr` vira `RegionQr`, com `region_index`, e
`PackageAssignment.qr` vira `qrs`. Os consumidores mudam o mínimo para compilar: `Publish` grava
`qrs` só com a região 0, `folhaDaAtribuicao` procura o QR da região 0, a validação recusa `qrs` vazia,
e quatro testes trocam `qr` por `qrs`. A compilação de `:packages:domain`, `:apps:api` e das duas
suítes de teste do Android passou, com 14 tasks de compilação executadas.

**Previsto antes de rodar**, por leitura de quais testes leem golden de layout ou de pacote: caem
`SheetInterpreterTest`, `IdentidadeDaProvaTest`, `PacoteVersionadoTest`, `GoldenLayoutTest`,
`LayoutProfileTest` (os que comparam com a golden), `PrintTestSheetTest` e `ObjectiveScoringTest`,
porque a golden antiga não tem `qr_id`, que é obrigatório, e o pacote da turma tem a chave `qr`. De
`ExamPackageTest` cai **só** o teste do literal de hash.

**Real**, `./gradlew :packages:domain:jvmTest --rerun`: 329 testes, **49 falhas**, relatórios de
2026-09-24T11:08:34Z a 11:08:35Z.

| Classe | Caídos | Motivo, lido na mensagem |
|---|---|---|
| `ExamPackageTest` | 1 | `o hash da fixture e o mesmo nos tres alvos`: esperado `277d2f8c…` |
| `GoldenLayoutTest` | 1 | "o mapa divergiu do golden a partir do caractere 91893" |
| `IdentidadeDaProvaTest` | 3 | `Encountered an unknown key 'qr'` no pacote da turma |
| `LayoutProfileTest` | 1 | "o perfil padrao precisa reproduzir o golden byte a byte" |
| `ObjectiveScoringTest` | 28 | `Field 'qr_id' is required` ao ler o pacote golden |
| `PacoteVersionadoTest` | 4 | dois "envelheceu em relacao a fixture", dois `qr_id` ausente |
| `PrintTestSheetTest` | 1 | a folha de teste diverge da versionada |
| `SheetInterpreterTest` | 10 | `Field 'qr_id' is required` ao ler o layout golden |

**Real igual ao previsto**, classe por classe, e todas as 49 mensagens são de um dos três motivos
previstos. Nenhum teste que monta pacote por `buildPackage` caiu, e `FolhaDaAtribuicaoTest` passou
inteiro.

**O que não rodou neste commit:** as suítes de `apps/api` e `apps/android`. Elas leem as mesmas
goldens e o mesmo pacote congelado, e ficam vermelhas pelas mesmas razões até a regravação da 5.2.
Voltam a rodar no fechamento.

## 3. O motor

**3.1 — a recusa de entrada.** `requireSupported` passa a separar objetiva e discursiva. A discursiva
com rubrica passa. Continuam recusados, cada um com teste que confere a mensagem:
- discursiva sem rubrica, com alternativas ou com resposta de gabarito;
- objetiva com rubrica ou com modo de captura. Este último cai na cláusula geral da spec, "conteúdo
  não suportado não degrada em silêncio";
- rubrica sem critério, com critério repetido, ou que não soma a pontuação;
- critério com pontos ou linhas não positivos, sem descritor, ou com descritor fora da escala;
- prova sem objetiva;
- mais de 24 discursivas.

O teto de discursivas vem de `CaptureGeometry.MAX_REGIONS = ArucoDictionary.SIZE / 4`, derivado, e o
teste afirma que 24 passam e 25 não.

Dois testes antigos que afirmavam a recusa de **toda** discursiva foram reescritos, com o motivo no
próprio arquivo (P12):
- `ExamDefinitionTest.questao discursiva e recusada com erro identificavel` virou "questao discursiva
  na entrada sem rubrica e recusada";
- `LayoutEngineTest.questao discursiva impede a emissao do mapa` virou "discursiva sem rubrica impede
  a emissao do mapa", e passou a conferir o motivo. A entrada do segundo não mudou, e ele sempre montou
  uma discursiva sem rubrica.

`./gradlew :packages:domain:jvmTest` filtrado nas duas classes: `ExamDefinitionTest` 21 de 21 e
`LayoutEngineTest` 21 de 21, relatórios de 2026-09-24T11:11:53Z. A task fecha `FAILED` por desenho:
o `build.gradle.kts` do domínio reprova execução em que método `@Test` fica sem resultado no relatório
(301, pelo filtro). É a guarda contra ler comando estreito como suíte, e ela funcionou.

**Vista falhar:** com a conferência da soma trocada por `if (false && …)` (`// MUTACAO`), caiu **só**
`rubrica que nao fecha com a pontuacao e recusada, com os dois valores`: o `assertFailsWith` não
recebeu exceção (`ExamDefinitionTest.kt:39`). Previsto: só esse. Revertida a partir da cópia salva,
`grep MUTACAO` deu 0, e a classe rodou de novo com 21 aprovados e 0 falhas.

**3.2 — o gabarito só com objetivas, numeradas pela posição na prova.** `objetivasDe(exam)` é o lugar
único que diz quais questões o gabarito tem. A grade, as faixas e as bolhas leem dele, e não de três
filtros separados. Testes em `RegiaoDiscursivaTest`:
- "o gabarito tem só as objetivas, numeradas pela posição na prova": com a 3 discursiva, as linhas
  são 1, 2, 4 e 5, e não há bolha da 3;
- "sem discursiva o gabarito continua numerando em sequência": a guarda do caminho de sempre.

Filtrado com `LayoutEngineTest`: 23 de 23.

**Vista falhar:** com o número da linha trocado pela posição contígua (`${index + 1}`, `// MUTACAO`),
caiu **só** o primeiro teste. O segundo, de prova só objetiva, continuou verde, como previsto: sem
discursiva as duas numerações coincidem. Revertido, `grep MUTACAO` deu 0, e rodado de novo: 23 de 23.

**3.3 — o bloco e a região discursivos.**
- **As constantes num lugar só.** `EssayGeometry` guarda a pauta de 8,6 mm, o traço da moldura (o da
  bolha, 0,22 mm), o traço da pauta (0,15 mm), o recuo de 1 mm da pauta em relação à moldura, e as
  faixas de cima e de baixo, derivadas do marcador, do QR e da zona de silêncio.
- **A altura é calculada uma vez.** `QuestionBlockBuilder` calcula `EssayContent` (linhas, altura do
  enunciado e altura da região, cada uma na grade), e o motor só a lê. Reserva e desenho saem do mesmo
  número.
- **O que a região contém:** os quatro marcadores `4k…4k+3` nos cantos da coluna, o QR centrado no
  topo com o payload da região, e a moldura desenhada **para dentro** da área de resposta, de modo que
  a área declarada é a borda de fora da tinta. As linhas `1…n−1` da pauta são `rect` de traço com altura
  zero, a forma (a), **provisória até a 6.3**. `answer_area`, `question_id` e `qr_id` ficam preenchidos.
- **O índice da região** é a ordem entre as discursivas da prova, e não a ordem de colocação.

Testes em `RegiaoDiscursivaTest`, um por cenário:
- identificadores `4k…4k+3`;
- o QR de cada região carrega o índice dela, lido pelo `QrPayload.read`, que é o mesmo leitor do
  aparelho;
- região completa;
- a rubrica dimensiona a moldura, e só ela: duas linhas a mais dão exatamente 2 × 8,6 mm;
- nenhum texto do enunciado cai dentro da região;
- enunciado e moldura juntos, com 1 a 30 objetivas antes, e uma guarda de vacuidade que exige mais de
  um lugar distinto;
- moldura maior que a coluna é recusada nomeando a questão. A recusa vem do paginador, que já recusava
  bloco maior que a coluna.

Com `LayoutEngineTest`: 30 de 30.

**Vista falhar, com duas mutações e conjuntos disjuntos:**

| Mutação | Previsto | Real |
|---|---|---|
| M1 — `answerHeight = PAUTA * 3`, a moldura de altura fixa | só "a rubrica dimensiona a moldura" | só ele (29 de 30) |
| M2 — `markerIdsOf(regionIndex + 1)`, os IDs `4k+4` | só "cada discursiva tem a sua regiao" | só ele (29 de 30) |

A tarefa previa que M2 também derrubasse "a validação". A validação da região discursiva é a 3.4, e
ela ainda não existia quando M2 rodou, então não havia o que derrubar. A M2 **volta a rodar na 3.4**,
e o conjunto real vai ao lado deste. As duas mutações foram revertidas pela cópia byte a byte,
`grep -rn MUTACAO packages/domain/src` deu 0, e a suíte filtrada rodou de novo: 30 de 30.

**3.4 — a validação da região discursiva.** `LayoutMap.validate` passa a recusar:
- em **toda** região, `qr_id` que não está entre as primitivas da página dela;
- duas regiões para a mesma questão;
- na região discursiva, questão ausente, bolha declarada, área de resposta ausente ou fora de
  `[0,1]`, e área de resposta sobre o QR.

Cada cenário parte do mapa **válido** que o motor produz, com duas discursivas, muda **uma** coisa e
afirma a lista **exata** de problemas. `RegiaoDiscursivaTest` 15, `LayoutMapValidationTest` 20 e
`LayoutEngineTest` 21, sem falha.

**Vista falhar:**

| Mutação | Previsto | Real |
|---|---|---|
| M3 — `if (false && cruza)`, a sobreposição com o QR desligada | só "área de resposta sobre o QR" | só ele (55 de 56) |
| M2 de novo — IDs `4k+4`, agora com a validação existindo | "cada discursiva tem a sua região" e "o mapa com discursivas que o motor produz é válido" | **7 falhas**: as duas previstas e mais as **cinco** de uma mudança só (sobre o QR, fora do quadrilátero, sem questão, duas regiões, QR que não existe) |

**A M2 divergiu do previsto, e a regra de parada foi aplicada** (decisão 13): o real fica ao lado, e o
significado vem da mensagem de cada uma, lida no XML, e não da contagem (P12). As cinco extras caem
pela **mesma** causa. Todas partem do mapa do motor. Com a M2, esse mapa ganha dois problemas ("regiao
1 deveria usar os marcadores [4, 5, 6, 7], veio [8, 9, 10, 11]", e o mesmo para a região 2), e a lista
exata que cada uma afirma deixa de bater. **Em todas as cinco, o problema que o cenário testa continua
presente na lista.** A previsão errou por não contar que os cinco dividem a base. O comportamento é o
desejado: afirmar a lista exata é o que faz esses cenários acusarem também uma base inválida, em vez
de passar com um problema a mais que ninguém lê. Nenhum teste foi mexido. A M3, que muta só a
validação, caiu no conjunto exato, e isso mostra que a camada está isolada.

As duas foram revertidas pela cópia byte a byte, `grep -rn MUTACAO packages/domain/src` deu 0, e a
suíte filtrada rodou de novo: 56 de 56.

## 4. O pacote

**4.1 e 4.2, juntas** — `buildPackage` chama `requireCoherent`, e o pacote discursivo só passa pela
publicação com as duas. Rodando só a 4.1, os três cenários de publicação caíram com
`ExamPackageException`, pela coerência antiga ("sem gabarito" e "layout diverge"), como previsto.

**O que mudou em `Publish`:**
- o item leva `kind`, `rubric` e `answer_capture_mode`. A discursiva sem modo declarado sai `gray`, e
  a objetiva sai com nulo;
- cada atribuição traz um `RegionQr` por região do mapa, com o índice lido **da região que o motor
  produziu**. `qrPayloadDaAtribuicao` passou a receber a região, e não um inteiro. A razão da KDoc
  antiga ("o índice é decisão do engine") ficou de pé.

**O que mudou em `requireCoherent`:**
- recusa discursiva com gabarito, objetiva com rubrica, discursiva sem rubrica;
- recusa `max_score` que não fecha com gabarito mais rubricas. O pacote não tem pontuação por item: a
  da objetiva mora no gabarito, e a da discursiva é a soma da rubrica. Esta é a forma executável de
  "rubrica que não soma a pontuação do item";
- recusa `fully_offline_gradable` contra os itens, e item discursivo sem **exatamente uma** região por
  variante;
- a atribuição precisa trazer QR para cada região da variante, e cada payload é lido por
  `QrPayload.read`, o mesmo leitor do aparelho, conferindo índice e token;
- a divergência de layout passa a contar a discursiva pela região, e não por bolha.

Testes em `PacoteDiscursivoTest`, 11:
- os quatro de publicação: rubrica e modo, nota máxima e gabarito, prova só objetiva, um QR por
  região;
- sete de coerência, cada um partindo de um pacote válido e mudando uma coisa. "Item discursivo sem
  região" usa pacote **sem roster**: com roster, tirar a região faria a conferência de QR por região
  recusar antes, e o cenário mediria a camada vizinha.

**Um cenário antigo foi sombreado pela conferência nova, e a fixture dele foi corrigida.** A
asserção não mudou. `ExamPackageTest.layout divergente dos itens e recusado` tira um item e a entrada
dele no gabarito, e deixava `max_score` em 40. Passou a cair por "a nota maxima ... e 40, e gabarito
(39) ... somam 39", que é a camada nova, e não pela de layout que ele nomeia (a mensagem foi lida no
XML). A fixture agora desconta a pontuação do item removido, e o motivo ficou escrito no teste
(`rigorous.md` §3, P12).

`./gradlew :packages:domain:jvmTest --tests "com.platos.domain.exam.*" --tests "…QrPayloadTest"`: 96
aprovados. Os únicos vermelhos são os oito das goldens, os mesmos do commit de contrato (hash,
`IdentidadeDaProvaTest`, `PacoteVersionadoTest`), que esperam a 5.2.

**Vista falhar:**

| Mutação | Previsto | Real |
|---|---|---|
| M4 — `if (false && regioes != 1)` | só "item discursivo sem região" (a conferência antiga de layout recusa, mas com outro motivo, e o teste confere o motivo) | só ele |
| M5 — `if (false && payload.regionIndex != qr.regionIndex)` | só "QR associado à região errada" | só ele |

Revertidas, `grep -rn MUTACAO packages/domain/src` deu 0, e rodado de novo: 96 aprovados, nenhuma falha
fora das goldens.

**4.3 — a folha do aluno troca um QR por região, pelo `qr_id`.** `folhaDaAtribuicao` monta
`(página, qr_id) → QR da atribuição` a partir das regiões do mapa e troca cada `DrawQr` que casa. No
fim, afirma que trocou **exatamente** um QR por região. Um `qr_id` que não está na página da região,
ou uma região sem QR na atribuição, é recusado em vez de deixar o QR da variante, sem aluno, no
lugar.

Testes em `PacoteDiscursivoTest`, três:
- cada região da folha tem o QR que a atribuição traz para ela;
- duas atribuições produzem folhas que diferem só nos QRs, com três regiões;
- a troca segue o `qr_id` mesmo com as primitivas de cada página em ordem **inversa**, que é a
  geometria idêntica com outra ordem de emissão.

`com.platos.domain.exam.*`: 81 aprovados, nenhuma falha fora das goldens.

**Vista falhar:** M6 troca a ligação por `qr_id` por uma troca **por ordem**: o i-ésimo QR encontrado
recebe o i-ésimo da atribuição. Previsto: cai **só** o cenário da ordem inversa, porque na ordem
normal as duas regras coincidem. Real: só ele, com 80 aprovados. É a prova de que a ligação declarada
faz diferença, e não coincide por acaso com a emissão. Revertida, `grep MUTACAO` deu 0, e rodado de
novo: 81 aprovados.

## 5. Fixtures e goldens, e a 3.5

**5.1 — `fixtures/prova-discursiva.json`.** Tem quatro objetivas (`q1`, `q2`, `q4`, `q5`) e duas
discursivas:
- `d1`, na posição 3: rubrica de 2 critérios, 5 linhas, 3 pontos;
- `d2`, na posição 6: 3 critérios, 7 linhas, 4 pontos, e `answer_capture_mode: color`.

Todas as questões têm habilidade BNCC (I1). O texto vai sem acento, como a fixture de referência.

**5.2 — a regravação**, com `./gradlew :packages:domain:jvmTest --tests "…GoldenWriterTest"
-Dplatos.golden.write=true`, iniciada às 2026-09-24T11:40:39Z. O `GoldenWriterTest` ganhou o gravador
da prova com discursiva: layout, pacote com as atribuições `tok-a` e `tok-b`, e a folha de `tok-a`.

- **Previsto antes de rodar, conjunto de arquivos:** modificados `prova-referencia.layout.json`,
  `prova-referencia.package.json`, `prova-referencia.turma.package.json`, `prova-2.package.json` e
  `folha-de-teste.layout.json`; novos `prova-discursiva.layout.json`, `.package.json` e
  `.aluno.layout.json`. **Real:** exatamente esses, e nenhum outro (`git status --short fixtures/`).
  `*.papel.json`, `*.recorte.pgm`, os dois pacotes congelados e as fórmulas não mudaram.
- **Guarda de geometria** (`guarda-geometria.mjs`, no scratchpad). Para cada golden regravada, lê a
  versão de `HEAD` e a da árvore e remove só o que esta mudança acrescentou: `qr_id`, `question_id` e
  `answer_area` nas regiões, `kind`, `rubric` e `answer_capture_mode` nos itens. Também converte
  `qrs` de volta a `qr`, exigindo que só exista a região 0. Depois exige igualdade profunda. Resultado:
  as **cinco iguais**. O canário, `quad_x` + 1 µm, foi **acusado**. Nenhuma coordenada da prova
  objetiva mudou.
- **A fixture nova, lida:**
  - três regiões: o gabarito com 16 bolhas (4 × 4), `d1` na página 0 com marcadores 4–7, e `d2` na
    **página 1** com 8–11. A página 1 é a que `fidelidade.mjs` não mede hoje (6.1);
  - duas páginas;
  - `fully_offline_gradable` falso, `max_score` 11, gabarito só com objetivas, e QRs `0,1,2` nas duas
    atribuições.
- **Hashes novos**, pelo `crypto` do Node sobre os bytes, independente do SHA-256 em Kotlin (P4):
  - `prova-referencia.package.json`: `ff2b94ef600101e2c20d5b6b298f7d0612ee0a66beb4d74d7dcd954cfbde40da`,
    104.091 bytes. Os literais de `ExamPackageTest` e `ExamPublicationTest` passaram a ser este;
  - `prova-discursiva.package.json`: `f91838c4…3c09`, 27.227 bytes;
  - `prova-referencia.turma.package.json`: `7282a186…4df7`, 107.282 bytes.

**3.5 — o determinismo nos três alvos.** `GoldenLayoutTest` ganhou "mapa da prova com discursiva bate
byte a byte com o golden". Ele também afirma a validação e tem uma guarda de vacuidade: três regiões, e
uma discursiva fora da página 0. As quatro goldens novas entram no `embedFixtures`.
`./gradlew :packages:domain:allTests --rerun`, com 17 tasks executadas:

| Alvo | Testes | Falhas | `timestamp` |
|---|---|---|---|
| `jvmTest` | 374 | 0 | 11:42:15Z .. 11:42:17Z |
| `jsNodeTest` | 365 | 0 | 11:42:37Z .. 11:42:38Z |
| `testAndroidHostTest` | 365 | 0 | 11:42:23Z .. 11:42:25Z |

O caso novo aparece nos três XML, sem falha. As 49 falhas do commit de contrato fecharam todas com a
regravação.

**P23 fica aberta até a 6.2:** paridade e fidelidade destas goldens, com os PDFs dos dois lados
gerados **nesta** sessão.

**4.4 — a quebra aceita é real e alta.** Dois cenários novos em `ConferenciaDePacoteTest`, no mesmo par
da ETAPA 3:
- `pacote-antes-da-discursiva.json` com o hash **dele** é recusado por `INTERPRETACAO`. O cenário
  afirma também que esse hash é o `277d2f8c…` do dia do congelamento;
- os mesmos bytes com o hash **atual** caem por `INTEGRIDADE`.

`./gradlew :apps:android:testDebugUnitTest --tests "…ConferenciaDePacoteTest"`: 22 de 22, relatório
de 2026-09-24T11:43:56Z.

**Um efeito da 5a sobre um cenário antigo, anotado.** O pacote da ETAPA 3
(`pacote-do-contrato-anterior.json`) era recusado pela **reserialização**: `params_hash:null` injetado
mudava os bytes. Agora ele é recusado antes, no **parse**, porque falta `qr_id`, que é obrigatório. O
motivo continua `INTERPRETACAO` e o cenário continua verde, mas ele deixou de exercitar o ramo da
reserialização. Esse ramo segue coberto por "campo com valor padrão omitido" e "ordem de campo
trocada", como a mutação abaixo mostra.

**Vista falhar:** o ramo do parse passa a devolver `INTEGRIDADE` em vez de `INTERPRETACAO`.
- **Previsto na tarefa:** "o cenário novo e o da ETAPA 3".
- **Previsto antes de rodar**, depois de levantar quais cenários passam pelo parse: **5** —
  "campo desconhecido", "bytes que não são json", "json válido que não é pacote", o da ETAPA 3 e o de
  antes da discursiva. Os dois cenários da reserialização não caem.
- **Real:** exatamente esses 5, com 22 testes e 5 falhas, relatório de 11:44:41Z. A previsão da
  tarefa estava incompleta: ela não contava os três cenários que já passavam pelo parse. A previsão
  refinada acertou, e as duas ficam registradas.

Revertida pela cópia, `grep MUTACAO` deu 0, e rodado de novo: 22 de 22, às 11:44:50Z.

**5.3 — o espelho web, e a conferência que ele não tinha.** `apps/web/scripts/examPackage.ts` passou
a `qrs` e troca cada QR casando `(página, qr_id)`, com a mesma regra da implementação Kotlin (4.3). O
tipo `ScannableRegion` do web ganhou `qr_id`, `question_id` e `answer_area`.

A KDoc antiga dizia que "a paridade entre plataformas é quem pega a divergência". A frase ficou,
marcada como errada, com a razão ao lado (P7).

`test/folhaDoAluno.test.ts` é novo, com dois cenários:
- deriva a folha de `tok-a` do pacote gravado, pelo caminho que o `render-fixture.ts` usa, e a
  compara **byte a byte** com `prova-discursiva.aluno.layout.json`, gravada pelo Kotlin. A guarda de
  vacuidade exige três regiões e três QRs com `tok-a`;
- cada região leva o QR que diz a região dela.

`npx vitest run`: 16 de 16, início às 13:46:34 no relógio local (11:46:34Z). `tsc --noEmit` limpo.

**Vista falhar, com conjuntos disjuntos:** o espelho escreve o payload da região 0 em todos os QRs.
- `vitest`: caem **os dois** cenários de `folhaDoAluno`, e os 14 do renderizador ficam.
- `compare.mjs` entre o PDF do aluno **com** a mutação e o **sem**, sobre
  `prova-discursiva.aluno.layout.json`: "paridade OK", maior divergência de **0,000 mm**. É a
  demonstração, e não só o argumento, de que a paridade de centroide não vê payload.

Revertido pela cópia, `grep MUTACAO` deu 0, e rodado de novo: 16 de 16.

**5.4 — `render-fixture.ts` na fixture discursiva.** Não precisou de código: o script já recebia
`PLATOS_PACKAGE`, `PLATOS_STUDENT` e o caminho de saída. Às 11:46:56Z:
- `build/parity/discursiva-web.pdf`: 212.542 bytes, **2 páginas**, igual ao mapa;
- `build/parity/discursiva-aluno-web.pdf`: 212.401 bytes, a folha de `tok-a`.

`fidelidade.mjs` roda sobre o primeiro: 47 verificações, maior desvio de 0,047 mm em "marcador 2:
borda superior", com tolerância de 0,05 mm, e "fidelidade OK". **Isso só cobre a página 0.** A região
`d2`, na página 1, não é medida pela versão atual, e é a lacuna que a 6.1 fecha.

## 6. Paridade, fidelidade e tinta

**6.1 — `fidelidade.mjs` passa a medir ArUcos em todas as páginas.** O instrumento de "ver falhar" é
`desloca-discursiva.mts`, no scratchpad e fora da árvore. Ele renderiza a folha da variante da prova
com discursiva, pelo renderizador do web, com **um** marcador deslocado 0,5 mm para baixo: `r2-m8`, o
marcador 8, da região de `d2`, na **página 1**.

| Momento | PDF | Resultado |
|---|---|---|
| **Antes** de estender | com `r2-m8` deslocado | **"fidelidade OK"**, 47 verificações, maior desvio de 0,047 mm, igual à folha correta. É a lacuna, medida |
| **Depois** de estender | com `r2-m8` deslocado | **"FIDELIDADE FALHOU"**, 63 verificações: "marcador 8 (pagina 1): borda superior: observado 104.542 mm, declarado 104.000 mm (desvio 0.542)" |
| **Depois** de estender | correto (`discursiva-web.pdf`) | "fidelidade OK", 63 verificações, maior desvio de 0,047 mm |

As 16 verificações novas são os 4 marcadores da página 1, com 4 medidas cada. Os marcadores da
página 0, inclusive os da região de `d1`, já eram medidos. Os rótulos da página 0 não mudaram, e a
saída de sempre continua igual. A mutação foi num PDF gerado fora da árvore, e não em código: não há
reversão de fonte a conferir, e os PDFs corretos continuaram passando.

**6.1b — `compare.mjs` passa a medir retângulo de traço.** Para cada `rect` com `stroke > 0` e sem
`fill`, soma a escuridão na faixa do contorno e divide pela área que o traço declarado ocupa. Os
números vieram da tarefa, fixados antes da primeira medição (P11): folga de 0,2 mm, presença entre 0,5
e 1,5, e concordância de 0,10.

A primeira medição foi o web contra ele mesmo, na prova com discursiva: 12 traços, que são as 2
molduras e as 4 + 6 linhas de pauta, com razão de 0,995 a 1,015. Nas outras provas: a de referência não
tem traço (0), e a folha de teste tem 1, o "vão de referência", com razão 1,001 nos dois lados.

**Vista falhar, com duas mutações de conjuntos disjuntos:**

| Mutação | Previsto | Real |
|---|---|---|
| **M-a** — o renderizador **Android** pula o traço dos `rect` com `id` iniciado por `r2-` (`LayoutMapRenderer.kt`, `// MUTACAO`), PDF gerado às 11:54:25Z | presença e concordância caem nos 7 retângulos da região 2 (moldura e 6 linhas); nenhum `r1-`; nenhum centroide | exatamente os 7 `r2-`, com 2 problemas cada (presença no Android e divergência). Razão no Android de 0,000 a 1,015. **0** problemas de centroide; maior divergência de centroide 0,047 mm, igual à de antes |
| **M-b** — o traço com o **dobro** da espessura **nos dois lados**: o mesmo PDF, gerado pelo web de um mapa com `stroke × 2` (`traco-dobrado.mts`, no scratchpad), comparado com ele mesmo contra o mapa original | cai o **teto** da presença nos 12 retângulos, nos dois lados; a concordância **não** cai | razão de 1,990 a 2,011 nos dois lados, **24** problemas de presença (12 × 2; a saída mostra 20 e "e mais 4"), **0** de concordância, 0 de centroide |

A M-a foi revertida pela cópia, `grep MUTACAO` deu 0, e o PDF do Android foi gerado de novo às
11:54:50Z, com 127.604 bytes e `sha256` `f882bd8d…`, **idêntico** ao das 11:51Z: o PDF do Android é
determinístico, e a reversão se confere byte a byte. A paridade voltou a passar. A M-b foi num PDF
gerado fora da árvore, e não há fonte a reverter.

**6.2 — P23 fechada nesta sessão.** Os PDFs dos dois lados são desta sessão:
- **web**, às 11:50:50Z, por `render-fixture.ts`, `render-test-sheet.ts` e `render-fixture.ts` com
  `PLATOS_PACKAGE` da discursiva;
- **Android**, pela suíte instrumentada **inteira**, das 11:50:50Z às 11:51:59Z: 84 testes (os 83 da
  linha de base e `geraPdfDaProvaComDiscursivaParaOJobDeParidade`), 0 falhas, 2 pulados, `timestamp`
  11:51:57Z. Os PDFs na origem têm horário de 11:51:18Z a 11:51:19Z, dentro da execução, e os tamanhos
  batem com as cópias.

| PDF | `sha256` (16) | Fidelidade | Tinta |
|---|---|---|---|
| `web.pdf` (referência) | `ad98dca8ee5e1c85` | OK, 116 verif., maior 0,046 mm | OK, 160 bolhas |
| `android.pdf` | `0c54d78ad667a256` | OK, 116, 0,042 mm | OK, 160 |
| `teste-web.pdf` | `829d5a3fdd5eab03` | OK, 31, 0,046 mm | OK, 5 |
| `android-teste.pdf` | `01e33321f2c7f11e` | OK, 31, 0,017 mm | OK, 5 |
| `discursiva-web.pdf` | `1a7ead9c88f20efe` | OK, 63, 0,047 mm | OK, 16 bolhas, 2 regiões discursivas fora do orçamento |
| `android-discursiva.pdf` | `f882bd8d33483c56` | OK, 63, 0,017 mm | OK, idem |

Paridade (`compare.mjs`):
- **referência:** 185 de 185 elementos, maior 0,048 mm, 4 tramas, 0 traços;
- **folha de teste:** 9 de 9, maior 0,044 mm, 2 tramas, 1 traço;
- **discursiva:** 28 de 28, maior 0,047 mm, **12 traços**, razão web de 0,995 a 1,015 e Android de
  0,995 a 1,016, maior divergência de traço 0,020 em `r2-p6`.

Os passos de "ver falhar" do job `paridade` também rodaram nesta sessão:
- "a medição de tinta continua capaz de falhar" acusou `tinta-faixa` e `tinta-cor`;
- "a paridade enxerga trama" acusou a faixa ausente;
- o deslocamento deliberado de `render-shifted.ts` foi acusado pela paridade **e** pela fidelidade.

**`tinta.mjs` precisou de um ajuste, e ele foi achado aqui.** A primeira execução sobre as duas
discursivas reprovou, nos dois renderizadores, com "regiao 2: nenhuma bolha desenhada na pagina 1".
O instrumento supunha que toda região tem bolha, e a região discursiva não tem por construção. Agora a
região com `kind: essay` e sem bolha fica fora do orçamento de bolha, e a saída diz isso ("regiões
discursivas fora do orçamento de bolha: 2"). **Só ela:** a guarda de vacuidade continua de pé, e um
mapa com o gabarito sem círculos ainda reprova com "regiao 0: nenhuma bolha desenhada na pagina 0",
conferido com um layout modificado no scratchpad.

**6.3 — a forma da pauta.** A forma (a), `rect` de traço com altura zero, **fica**. A medição da 6.2 a
decide: 12 traços com razão de 0,995 a 1,016 nos dois lados, e divergência máxima de 0,020, contra
0,10. Nenhuma primitiva nova, e `min_renderer_version` continua em 1, como o mantenedor decidiu.

**6.4 — a paridade vê a região discursiva deslocada.** O marcador `r2-m8` foi deslocado 0,5 mm **só**
no renderizador Android (`// MUTACAO` em `LayoutMapRenderer.kt`, PDF das 11:55:43Z). `compare.mjs`
reprovou com **um** problema: "aruco r2-m8 divergiu 0.452 mm (tolerancia 0.3 mm)". Os traços
continuaram concordando. A tarefa pedia "nomeando o marcador **e a página**". A mensagem nomeia o
marcador pelo `id`, que é único no mapa, e **não** diz a página: fica registrado como está, e a saída do
`compare.mjs` não foi mexida por isso. Revertida, `grep MUTACAO` deu 0, o PDF foi gerado de novo às
11:55:55Z com os 127.604 bytes de antes, e a paridade voltou a passar.

## 7. O servidor

**7.1 — a prova com discursiva publicada pelo servidor.** Cenário novo em `ExamPublicationTest`:
publicar `prova-discursiva.json` com o roster de dois alunos, sobre o Postgres de teste. Ele confere:
- o `content_hash` da coluna contra os **bytes** da coluna, pelo `MessageDigest` da JVM, e contra o
  hash devolvido pela publicação;
- que reserializar o `content` gravado é identidade, ou seja, que os bytes são o texto canônico;
- `fully_offline_gradable` falso;
- em cada atribuição, QRs das regiões 0, 1 e 2, todos com o token dela.

`./gradlew :apps:api:test --rerun`: 168 testes (os 167 da linha de base e este), 0 falhas, relatórios
de 2026-09-24T11:57:44Z a 11:58:00Z.

**Literais trocados:** só o hash de `ExamPublicationTest`, na 5.2. Os pacotes literais de
`ResultRouteTest` não divergiram: eles têm `layout` vazio, sem região a quem faltaria `qr_id`, e
passaram sem mudança.

## 8. O aparelho, sem mudança de produção

**8.1 — a recusa de hoje, fixada.** Cenário novo em `ObjectiveScoringTest`: o pacote da prova com
discursiva, com o payload de `tok-a` na região 0 e as respostas corretas das quatro objetivas.
`ObjectiveScoring.score` recusa com exatamente "itens lidos divergem da variante 'v1'; faltando: d1,
d2". Não sai nota, e em particular não sai uma nota objetiva que o aparelho trataria como definitiva
numa prova com parte discursiva. O motivo engana, e é a linha `5b` do §16. O cenário existe para que a
troca desse motivo seja visível quando a 5b a fizer. `ObjectiveScoringTest`: 29 de 29.

**Vista falhar:** a conferência de conjunto trocada por "lido contido no declarado"
(`!declared.containsAll(read)`, `// MUTACAO`).
- **Previsto:** cai o cenário novo, porque sai nota.
- **Real: 2 falhas.** O novo e o já existente "conjunto de itens divergente e recusado, e a mensagem
  diz o que faltou". As duas mensagens são "esperava recusa, veio Scored", em
  `ObjectiveScoringTest.kt:50`.

O cenário antigo cai pelo **mesmo** mecanismo: uma leitura com item faltando é subconjunto do
declarado. Ele já protegia esse caso, e o novo fixa a instância da prova com discursiva. "Item que a
variante não declara", que é o conjunto oposto, continuou verde, como devia. A previsão estava
incompleta, não há defeito, e nenhum teste foi mexido. Revertida, `grep MUTACAO` deu 0, e rodado de
novo: 29 de 29.
