# Cobertura — `slice-5c-0-o-recorte-da-resposta`

## Linha de base (tarefa 0.1), 2026-09-30

Medida na árvore de `main` com o archive da 5b-4 (PR #79) dentro, antes de qualquer alteração de
código desta mudança. Logs em `scratchpad/5c-0-baseline/` desta sessão.

### As duas tentativas que falharam antes (P7: ficam registradas)

| Tentativa | Resultado | Causa |
|---|---|---|
| `./gradlew build --rerun-tasks`, 13:17:55Z–13:19:15Z | `BUILD FAILED` em 1m19s, 84 tasks executadas | `:apps:api:generateJooq`: "Could not find a valid Docker environment". O Docker Desktop estava instalado e **parado**. |
| `./gradlew -p buildSrc test --rerun-tasks`, 13:19:50Z–13:20:08Z | `BUILD FAILED`, 1 de 1 falhou | Mesma causa, em `AquisicaoDeConexaoTest`. |
| `./gradlew build --rerun-tasks` com o Docker já de pé, 13:24:04Z–13:24:22Z | `BUILD FAILED` em 18s | "Previous attempts to find a Docker environment failed. Will not retry": o **daemon do Gradle guardou a falha** da primeira tentativa (estado estático do Testcontainers). `./gradlew --stop` resolveu. |

**Um erro meu de leitura, também mantido:** o primeiro aviso de conclusão do build em segundo plano
dizia `exit code 0`, e isso era o `date` que eu tinha posto depois do Gradle, não o Gradle (P2:
`comando; echo` não prova nada). O `BUILD FAILED` só apareceu ao ler o log.

O Docker Desktop foi iniciado pelo mantenedor, por decisão dele (mudança de ambiente local, P22).

### A linha de base

| Comando | Janela | Resultado | Contagem (por task, `timestamp` dentro da janela) |
|---|---|---|---|
| `./gradlew -p buildSrc test --rerun-tasks` | 13:23:49Z–13:23:59Z | BUILD SUCCESSFUL, 6 de 6 executadas | `buildSrc:test` 1 |
| `./gradlew build --rerun-tasks` | 13:24:33Z–13:28:23Z | BUILD SUCCESSFUL em 3m48s, **183 de 183 tasks executadas** | `apps:android:testDebugUnitTest` 344 · `testReleaseUnitTest` 344 · `apps:api:test` 171 · `packages:domain:jvmTest` 414 · `jsNodeTest` 405 · `testAndroidHostTest` 405 |
| `./gradlew :apps:android:connectedDebugAndroidTest` (`platos-atd34`, sem filtro) | 13:29:57Z–13:31:16Z | BUILD SUCCESSFUL em 1m18s | 97 executados, 0 falhas, 2 ignorados (o "99 finalizados" do log); relatório com `timestamp` 13:31:13Z |
| `npx vitest run` (`apps/web`) | — | **Não executado**: esta mudança não toca `apps/web`, e `apps/web/src/layoutMap.ts` só tem o tipo `answer_area`, sem espelho da validação (conferido por `grep`) | — |

As contagens são as do fechamento da 5b-4 (344, 171, 414, 405) — nada mudou entre uma e outra —, e
todos os `timestamp` dos XML caem na janela do comando que os produziu (P3).

**O que o "2 executed, 86 up-to-date" do connected significa:** as 86 são compilações que o `build`
de minutos antes já fez; a task de teste instrumentado em si executou (99 testes num aparelho, com
relatório novo). Não é o caso de P3 (contagem plausível de execução anterior): o `timestamp` do XML
é de 13:31:13Z.

**Memória.** O agregado da 5b-4 tinha sido interrompido por falta de memória. Aqui o agregado coube:
os daemons do Gradle foram parados (`./gradlew --stop`) antes de subir o emulador, o que liberou
cerca de 5,7 GB, e o `connected` rodou com o emulador sem janela.

## `sha256` de antes (tarefa 0.2)

`sha256sum fixtures/*.layout.json fixtures/*.package.json`, 2026-09-30, antes de qualquer alteração.
As fixtures são montadas como assets de teste direto de `fixtures/` (`apps/android/build.gradle.kts`),
sem cópia. Arquivo integral: `scratchpad/5c-0-baseline/sha256-antes.txt`.

```
d9f7b08c17a706b02ba21355e2286b71d09792605b1c22adc8b7b95ce566c221  fixtures/folha-de-teste.layout.json
914fb389830b4754935b608e3320d29bdd1839403c675179820a3edd6ced24c8  fixtures/prova-discursiva.aluno.layout.json
63040b2f1dfe1152ab4e83561bc77d5abc7023a73aab497491878ecc71ca5840  fixtures/prova-discursiva.layout.json
8c9756a9db45c1d08a97fd0d99f6edcb353338f78894ff5b26438d1e00078451  fixtures/prova-referencia.layout.json
c2098e10e6c70f93a469ce56ff5f0e5796b44792da0b661e01465904a48cb717  fixtures/prova-2.package.json
87a9f3312e9b7156542c7deb121c68782064aea1c61669951a9ad019e4f0a784  fixtures/prova-discursiva.package.json
ff2b94ef600101e2c20d5b6b298f7d0612ee0a66beb4d74d7dcd954cfbde40da  fixtures/prova-referencia.package.json
7282a186d3b644dac6108e7ea39931517c5a0614e8fa570b15ad09324cb14df7  fixtures/prova-referencia.turma.package.json
```

## Dívida (tarefa 0.3), 2026-09-30

`node tools/divida/divida.mjs` → `exit 0`, "nenhuma linha vencida: 21 linhas lidas", "vence nesta fatia
(5c): nenhuma". Fatia corrente derivada: `5c`. **A guarda não lê a tabela "Aberto" do §16**
(`TITULO_TABELA = '### Ponto de não-retorno'`); as duas linhas de fatia-limite 5 que estão nela não
aparecem nesta saída. Ver a tarefa 7.3.

## Grupo 1 — o domínio (tarefas 1.1, 1.2, 1.3), 2026-09-30

Contagem no fim do grupo, `--rerun` não usado (só o módulo tocado, tasks executadas):
`jvmTest` **437**, `jsNodeTest` **428**, `testAndroidHostTest` **428** (base 414/405/405: +2 da 1.1,
+11 da 1.2, +10 da 1.3), `timestamp` 13:40Z. `sha256sum fixtures/*.layout.json fixtures/*.package.json`
**igual** ao de antes (0.2): nenhum golden nem fixture mudou.

**Um teste filtrado não vale aqui, e a guarda diz isso.** `./gradlew :packages:domain:jvmTest --tests
"*FaixaDoDesvioTest*"` passou os dois testes e o build deu `FAILED`: "414 método(s) declarado(s) com
@Test sem resultado no relatório". É a guarda do projeto contra rodar suíte pela metade (P5). Todas as
contagens acima são de `jvmTest` inteiro.

### 1.1 — `EssayGeometry.DEVIANT_BAND` (3 mm, menor que o `gutter` de 6 mm)

**Visto falhar:** a faixa trocada para 6 mm, com `// MUTACAO`. **Previsto: cai só o teste do
`gutter`. Real: caíram os dois** — o do `gutter` ("a faixa do desvio (6000 um) alcança a coluna
vizinha: o gutter é 6000 um") e o que prende o valor ("expected: <3000um> but was: <6000um>"). A
previsão da tarefa estava errada: os dois testes guardam coisas diferentes (a folga e o critério
fixado), e ambos veem o 6 mm. Revertida com `grep -c MUTACAO` = 0, e `jvmTest` 416 verde de novo.

### 1.2 — a área de resposta só tem a moldura e a pauta

Onze testes (`AreaDeRespostaLimpaTest`), um por cenário da spec `layout-engine`, mais os que prendem
os limites: o mapa de hoje é válido e a área **contém** moldura e pauta (guarda de vacuidade, P13: sem
isso, "aceita moldura e pauta" passaria sobre uma área vazia); o QR da própria região encosta e é
aceito; e **um texto que começa fora da largura da região e a invade é ACEITO** — o limite conhecido
da camada 1, nomeado num teste para não virar lacuna silenciosa.

**Três vermelhos na primeira execução, diagnosticados pela mensagem (P12):**
1. Erro **do meu teste**: `expected: <150999> but was: <151000>`. A aritmética do teste truncava; a
   validação arredonda. O teste passou a arredondar meio para cima.
2. Erro **do meu helper**: "esperava exatamente um problema citando `estranho-retangulo`" — outra
   regra existente (trama acima do teto de 80‰) também cita o retângulo. O helper passou a contar só
   a recusa **desta** regra (`dentro da area de resposta`). A asserção continua exigindo exatamente uma.
3. **Achado real**: quando a área sobrepõe o QR da própria região, um teste **existente**
   (`area de resposta sobre o QR e recusada`) esperava exatamente `[a area ... sobrepoe o QR da
   regiao]`, e a regra nova acrescentava uma segunda mensagem para o mesmo defeito. Não toquei o teste
   existente (P12): a regra nova **pula o QR da própria região**, que já tem regra e mensagem. Custo
   registrado: a regra nova não repete essa recusa; quem a segura é `checkEssayRegion`, e o teste
   existente continua verde sem alteração.

**Visto falhar (duas mutações, com `// MUTACAO`, revertidas e conferidas):**
- **Regra desligada** (`if (false) checkAreaSoComMolduraEPauta(...)`): caíram exatamente os **6**
  testes de recusa (texto, nome do aluno, marcador, QR, imagem, círculo+retângulo) e **nenhum** dos 5
  de aceitação. Previsto e real coincidem.
- **`topo < caixa.bottom` trocado por `<=`** (tocar a borda passa a contar como "dentro"): **previsto
  1** (o de encostar). **Real: 2** — o meu e um **existente**, `QR declarado que nao existe na pagina
  e recusado`, cuja fixture deixa um QR que só encosta na borda. A previsão estava incompleta, e o
  segundo teste é uma confirmação independente do comportamento de borda. Mensagem do meu: `expected:
  <Valid> but was: <Invalid(problems=[regiao 1: a primitiva `imagem-encostada` (imagem) esta dentro da
  area de resposta ...])>`.

### 1.3 — `DesvioDaResposta.classificar`

**Correção de rumo, registrada (P7): o design escrevia a função com `Double` e `NaN`.** A primeira
execução caiu em `IntegerArithmeticGuardTest` ("nenhum ponto flutuante no código de cálculo", D-1.2),
que varre o `commonMain` do domínio. A regra é da arquitetura, e a função foi reescrita em inteiros
(centésimos de mm² = 1 pixel a 10 px/mm; proporção em ppm; comparação por multiplicação). O
comportamento da spec não mudou. `NaN`/infinito deixaram de ser entrada possível; sobra negativo e
acima do teto (10 m²), e a conta no próprio teto não estoura (teste).

**Visto falhar (três mutações, previsão feita antes, todas coincidiram):**
| Mutação | Previsto | Real |
|---|---|---|
| proporção `>=` → `>` | só "exatamente 5%" | só "exatamente 5 por cento com o piso satisfeito" |
| `&&` → `\|\|` entre proporção e piso | 3 | "logo abaixo de 5%", "logo abaixo do piso" e "mancha isolada" |
| piso `>=` → `>` | 2 | "exatamente 5%" e "exatamente o piso" |

Cada reversão conferida por `diff` contra a cópia do original (`igual`) e `grep -c MUTACAO` = 0.

**O que este grupo não verifica.** Nada foi medido em letra de aluno; os dois números do desvio (5% e
4 mm²) são suposições fixadas antes da execução (design, decisão 5), e a fronteira de cada um está
pinada por teste — o que não diz que estejam certos.

## Grupo 2 — os cantos do QR chegam ao ajuste (tarefas 2.1 e 2.2), 2026-09-30

### 2.1 — `QrOutcome.Read.position`: feita

`RegionQrReader` devolve os quatro cantos do símbolo (`PosicaoDoQr`), convertidos de `android.graphics.Point`
para tipo próprio. Aditivo: o `analyze` o ignora. **Não havia construção a atualizar**: nenhum teste
constrói `QrOutcome.Read`, só faz cast (a tarefa dizia "4"; a contagem estava errada). Verificação:
o teste instrumentado `a_leitura_valida_traz_a_posicao_do_qr_dentro_do_canvas` passou nas regiões 1 e 2
(os quatro cantos dentro do canvas), e `testDebugUnitTest` deu **353** (344 + 9 do contador de tinta
da 5.1, escrito antes do gate e ainda sem marca), `timestamp` 13:47Z.

### 2.2 — `position` significa o canto do QR no mapa: **REPROVOU o critério fixado**

Critério fixado antes da primeira execução (ADR-0007): `topLeft`, `topRight`, `bottomLeft` a ≤ 0,5 mm
do canto que o mapa declara para o QR, no canvas do QR. Esperado calculado por aritmética própria
(lado do QR pelo mapa, sangria simétrica). Documento renderizado de frente, 10 px/mm, `platos-atd34`,
`PosicaoDoQrInstrumentedTest`, 15:45Z (`logcat -s Medida5c0`):

| Região | topLeft | topRight | bottomLeft | bottomRight (só registrado) | Veredito |
|---|---|---|---|---|---|
| 1 | 0,391 mm | 0,390 mm | 0,320 mm | 0,391 mm | passa |
| 2 | 0,472 mm | **0,610 mm** | **0,532 mm** | 0,532 mm | **reprova** (2 de 3 âncoras) |

**O instrumento reage:** o mesmo teste com o canvas deslocado 20 px (2 mm) acusa 2,314 / 2,314 / 2,214 mm
(esperado ≈ 2), e o teste de reação passou. Não é um medidor que devolve zero.

**Diagnóstico posterior — não é critério, e é dito assim:** deslocamento por canto e caixa escura do
canvas (região 1: canvas 220×219, lado pelo mapa 140 px, sangria 40 px):
`zxing TL=(43,37) TR=(183,37) BL=(42,177) BR=(183,177)`; caixa escura com topo em y=37 e borda direita
em x=182 (região 2: topo 36, direita 184, `TR=(185,36)`). A esquerda e a base da caixa escura estão
contaminadas por marcador e moldura vizinhos (x=0, y=218) e não servem. **Leitura:** o ZXing concorda com a
borda real do símbolo a ≤ 1 px (0,1 mm); o **símbolo inteiro** está 0,3–0,6 mm fora do lugar que o
mapa declara, **dentro do canvas**. O canvas nasce da primeira homografia, ajustada só nos dois
marcadores da diagonal, e extrapola no canto do QR — o risco (a) do ADR-0018, já presente no
documento de frente.

**O que isto NÃO estabelece.** Não estabelece que `position` significa o canto externo do símbolo:
isso foi visto por um oráculo de pixel **contaminado em dois lados** e escolhido *depois* do resultado.
Não estabelece que a hipótese da decisão 2 se sustenta. E **não autoriza** trocar o critério: P11 (zona
vermelha) proíbe mudar critério depois de conhecer o resultado sem ADR que registre o resultado. A
tarefa 2.2 fica desmarcada, o grupo 3 não começa, e o `PosicaoDoQrInstrumentedTest` (com o teste que
reprova) **não foi commitado**.

### 2.2 — retomada pelo ADR-0020 (decisão do mantenedor), 2026-09-30

O mantenedor mandou reformular o critério com um ADR curto (`docs/adr/0020-...`). O critério
reprovado acima **fica registrado como reprovado** (P7). O novo: `topLeft`, `topRight` e `bottomLeft`
a ≤ 0,5 mm (a mesma tolerância, não afrouxada) da **caixa escura do próprio símbolo**, numa janela de
±1,5 mm em volta do QR do mapa (isola moldura e marcadores), com guarda de vacuidade (lado da caixa
entre 13 e 15 mm) e a distância ao mapa passando a **dado**, não critério.

**Este critério não é cego.** Foi escolhido depois do diagnóstico que já mostrava `TR` e `TL` a
menos de 1 px do símbolo. Vale como oráculo de pixel independente (não compartilha código com o
decodificador nem com o mapa) e como guarda de regressão. Não é a prova que o critério original
prometia.

**Resultado** (`PosicaoDoQrInstrumentedTest`, `platos-atd34`, 3 de 3, `timestamp` 14:10:58Z):

| Região | topLeft | topRight | bottomLeft | Erro da 1ª homografia no canto do QR (TL do símbolo − TL do mapa) |
|---|---|---|---|---|
| 1 | 0,100 mm | 0,000 mm | 0,000 mm | (0,20; −0,25) mm |
| 2 | 0,100 mm | 0,000 mm | 0,100 mm | (0,40; −0,35) mm |

O erro da última coluna é o **risco (a) do ADR-0018, medido**: na folha de frente, renderizada, a
primeira homografia põe o símbolo até 0,5 mm fora do lugar que o mapa declara. Em papel e em
perspectiva ele pode ser maior; não foi medido.

**Visto falhar (previsão feita antes):**
| Mutação em `positionOf` | Previsto | Real |
|---|---|---|
| `topLeft = bottomRight` | cai o critério **e** o teste de reação do instrumento (que parte da leitura real); passa o de "dentro do canvas" | exatamente isso: critério — `topLeft a 19,870 mm do símbolo`; reação — `topLeft devia estar a ~2 mm, esta a 21,336` |
| `topLeft` recuado 5 px na diagonal | **só o critério** | **errei**: caíram os dois. Critério — `topLeft a 0,781 mm do símbolo` (o motivo certo); reação — `topLeft ... esta a 2,648`, fora de 1,5–2,5, porque o teste de reação parte da leitura já mutada |

Nas duas, o teste de "os quatro cantos dentro do canvas" (2.1) passa, como deveria: a 2.1 e a 2.2
guardam coisas diferentes. Reversões conferidas por `diff` contra a cópia (`igual`), `grep -c
MUTACAO` = 0, e a classe rodada de novo verde (3 de 3, 14:10:58Z).

**Instrumento que reagiu, e o que aprendi dele:** o teste de reação também cai quando a *leitura* é
mutada, não só quando o *deslocamento* é grande. Isso o torna um segundo guarda do `positionOf`, e
não um teste independente da mutação. Aceito, dito aqui.

**O que continua não verificado:** a semântica de `position` foi medida só no documento renderizado
de frente; em papel o oráculo de pixel e o decodificador leem o mesmo módulo borrado e podem
concordar com o mesmo erro (ADR-0020, "Como isto poderia falhar em silêncio").

## Grupo 3 — o segundo ajuste e a conferência pelo resíduo (tarefas 3.1, 3.2, 3.3), 2026-09-30

`SegundoAjusteInstrumentedTest` (6) e `SensibilidadeDoResiduoInstrumentedTest` (2) no `platos-atd34`,
folha renderizada de `tok-a`, de frente e em perspectiva (`emAngulo`: cantos da página a 1–4%, fixado
antes de ver resultado). As três classes do grupo 2 e 3 juntas: 11 de 11, `timestamp` 14:17:46Z.
`testDebugUnitTest` 353. **Nada é papel.**

### 3.1 — o ajuste fecha, e a hipótese do ADR-0020 se sustenta no documento renderizado

Critério fixado antes: de frente, maior resíduo **< 0,2 mm** (o piso do instrumento). **Medido:**

| | Maior resíduo | Os onze (mm) |
|---|---|---|
| Frente, região 1 | **0,076 mm** | 0,059 0,052 0,072 0,036 0,076 0,065 0,064 0,059 0,056 0,042 0,045 |
| Frente, região 2 | **0,100 mm** | 0,067 0,067 0,079 0,055 0,073 0,065 0,063 0,054 0,042 0,016 0,100 |
| Perspectiva, região 1 | 0,066 mm | — |
| Perspectiva, região 2 | 0,103 mm | — |

**A hipótese "o erro da primeira homografia se cancela na ida e volta" se sustenta**, sobre este
documento: o erro de 0,2–0,4 mm que a 2.2 mediu no canto do QR não aparece no resíduo do segundo
ajuste. Continua **sem** ser medido em papel, em perspectiva forte ou com foto de celular.

### 3.2 — cada recusa, com o motivo

| Caminho | Motivo conferido | Fixture |
|---|---|---|
| resíduo acima do teto | "residuo de" e "teto 1.0 mm" | onze pontos reais, o `QR.tr` deslocado 10 mm |
| resíduo não finito | "nao e finito", e o controle sem `NaN` passa; `+∞` também recusa | `conferirResiduos` puro (o OpenCV não deixa forçar `NaN` de fora) |
| QR ilegível | o do QR, e **sem** "residuo" | QR apagado da página, marcadores intactos: a 1ª retificação passa |
| sem área de resposta | "a regiao 0 nao declara area de resposta" (igualdade) | o gabarito |

**Visto falhar (previsão feita antes; todas coincidiram):**
| Mutação | Caiu |
|---|---|
| M1 teto desligado | só `residuo_acima_do_teto...` ("esperava recusa, veio Ajustado") |
| M2 guarda de não finito desligada | só `residuo_nao_finito...`: **"um resíduo NaN passou calado pelo teto"** — o perigo que a regra do `NaN > teto` descreve, visto acontecer |
| M3 motivo do QR trocado por texto genérico | só `qr_ilegivel...` ("o motivo devia ser o do QR: falha generica") |
| M4 guarda de área desligada | só `regiao_sem_area...`: **sem ela o gabarito passa pelo segundo ajuste** como se fosse discursivo (veio `Ajustado`) |
| M5 `u0` esquecido na conversão canvas→(u,v) | `de_frente` e `em_perspectiva` (resíduo de 44,4 e 44,0 mm), e nenhum dos de recusa |
| M6 resíduos na metade (unidade errada) | só `a_tabela_de_sensibilidade...` (`TL.c1` a 3 mm daria 0,9988 mm) |

Reversões conferidas por `diff` contra a cópia do original, `grep -c MUTACAO` = 0.

### 3.3 — o que o resíduo pega, medido

Cada um dos 11 pontos deslocado de 1, 2, 3 e 5 mm, em `+x` e `+y`, nas duas regiões; maior resíduo do
ajuste, teto 1,0 mm. Valores representativos (mm; a tabela inteira sai do `logcat -s Medida5c0`):

| Ponto | 1 mm | 2 mm | 3 mm | 5 mm |
|---|---|---|---|---|
| marcador, cantos (típico) | 0,56–0,82 passa | 1,15–1,61 recusa | 1,68–2,41 recusa | 2,66–4,03 recusa |
| `QR.tl`, `QR.bl` | 0,60–0,79 passa | 1,23–1,55 recusa | 1,85–2,32 recusa | 3,07–3,88 recusa |
| **`QR.tr`, `+x`** | 0,36 passa | **0,71–0,72 passa** | 1,04–1,06 recusa | 1,63–1,67 recusa |
| **`BR.c4`, `+y`** | 0,49–0,50 passa | **0,94–0,97 passa** | 1,35–1,41 recusa | 2,05–2,19 recusa |

**Achados, ditos sem enfeite:**
1. **O resíduo é 0,36–0,82 vezes o deslocamento**: os mínimos quadrados repartem o erro entre os onze
   pontos. Com **1 mm de deslocamento, nenhum ponto é recusado**. O teto de 1,0 mm é sobre o **maior
   resíduo**, e não sobre o erro de posição de um canto: um canto pode estar a até ~2,8 mm (o `QR.tr`
   em `+x`) do lugar dele sem o ajuste recusar. **Não é mitigado, é conhecido.** O teto não foi
   mexido (P11).
2. **O receio de que os pontos do QR, sozinhos no canto superior direito, se acomodassem sem
   resíduo, não se confirmou** para `QR.tl` e `QR.bl` (denunciados como os marcadores). Confirmou-se,
   em parte, para o `QR.tr` em `+x`: é o ponto mais fraco.
3. **A partir de 3 mm, todo ponto, nas duas direções e nas duas regiões, é recusado** (asserção do
   teste, sobre o que a tabela mostrou).

**As asserções fixam o que a tabela mostrou, e não um critério anterior a ela** — e isso é dito porque
é a definição do que a tarefa pediu ("assere só o que a tabela diz que é pego"). O que elas pinam:
crescimento com o deslocamento, 1 mm não recusa (o limite), 3 e 5 mm recusam, e os dois pontos mais
fracos passam a 2 mm.

## Grupo 4 — o recorte (tarefas 4.1, 4.2, 4.3), 2026-09-30

`RecorteDaRespostaInstrumentedTest` (6) e `CropSobResiduoAceitoInstrumentedTest` (1), mais os do grupo
3: **18 de 18** instrumentados, `timestamp` 14:27:21Z; `testDebugUnitTest` 353. Folha renderizada de
`tok-a`, de frente e em `FolhaEmAngulo` (perspectiva fixada antes de ver resultado). **Nada é papel, e a
tinta do aluno é sintética** (retângulos pretos com posição do mapa), não letra.

**Entrada:** `SheetReader.recortar(gray, map, region)` → `RecorteDaResposta`: um `warpPerspective` com a
homografia do segundo ajuste sobre a área **mais** a faixa de 3 mm, supersampleado em 3× e reduzido por
`INTER_AREA`; o miolo é o recorte e a faixa não sai da função. **Nenhum código de produção o chama.**

### 4.1 — dimensões e posição da moldura

Oráculo de posição: perfil de intensidade sobre os pixels do recorte, contra o que o **mapa** declara
(aritmética própria); tolerância 0,5 mm, fixada antes. **Medido** (px a 10 px/mm; 1 px = 0,1 mm):

| Quadro | esquerda medido/esperado | direita | topo | base |
|---|---|---|---|---|
| frente, região 1 | 0,7 / 1,1 | 869,0 / 868,9 | 21,0 / 21,1 | 369,6 / 368,9 |
| ângulo, região 1 | 0,5 / 1,1 | 868,5 / 868,9 | 20,9 / 21,1 | 369,6 / 368,9 |
| frente, região 2 | 0,6 / 1,1 | 868,9 / 868,9 | 21,0 / 21,1 | 649,5 / 648,9 |
| ângulo, região 2 | 0,5 / 1,1 | 869,0 / 868,9 | 20,5 / 21,1 | 649,5 / 648,9 |

Maior afastamento: 0,7 px (0,07 mm). Dimensões do recorte dentro de 1 px do mapa (870 × 392 na região 1).

### 4.2 — a tinta no canto sem âncora

Retângulo preto de 8 × 3 mm (2400 px) a 2 mm da moldura, no canto inferior esquerdo, desenhado **antes**
da perspectiva. Caixa medida contra a esperada, e contagem de pixels: região 1 frente `(20,319)-(100,350)`
com 2429 px; região 1 ângulo 2399 px; região 2 frente 2439 px; região 2 ângulo 2456 px. Cada lado a ≤ 0,2 mm do
esperado, contagens dentro de 5% de 2400. **Medido sobre o documento renderizado; o canto continua
"conhecido, não mitigado" até o papel.**

### 4.3 — o que está fora não entra; determinismo; gabarito

Mancha grande a partir de 1 mm **fora** da área: os recortes com e sem a mancha são **iguais byte a byte**
(guarda de vacuidade: a mancha foi desenhada — a página suja tem mais pixels escuros). Dois recortes da
mesma captura, iguais byte a byte, nos 4 quadros. O gabarito é recusado com
`a regiao 0 nao declara area de resposta` (igualdade).

### Visto falhar (previsão antes)

| Mutação | Previsto | Real |
|---|---|---|
| **A** segundo ajuste só com os 8 cantos dos marcadores | (não sabia: é a pergunta se o teste distingue o segundo ajuste) | cai **só** a moldura: "frente, região 2: não achei a linha direita da moldura perto de 868.9 px". **O teste distingue.** |
| **B** janela do miolo deslocada 2 mm | moldura, tinta no canto, tinta de fora | exatamente esses 3; dimensões, determinismo e gabarito passam |
| **C** `QR.tr` deslocado 2 mm em +x (o resíduo aceita) | tira a moldura da tolerância (a tarefa dizia) | caem a moldura e a tinta no canto |
| **D** escala X do canvas ×1,01 | dimensões, moldura, afastamento; não a tinta no canto, o determinismo, a tinta de fora nem o gabarito | exatamente esses 3 (`largura 879 contra 870`; `direita a 0,86 mm`) |

**Desvio de forma, dito:** a tarefa 4.3 dizia "ampliar o canvas do miolo em 2 mm derruba o primeiro". Eu
mutei o **deslocamento da janela** (B), não o tamanho — o efeito sobre "a mancha entra" é o mesmo, e o
tamanho já é guardado pelas dimensões (D). Reversões conferidas por `diff` contra o original,
`grep -c MUTACAO` = 0.

### Achado: o que o resíduo aceita move o recorte (e o meu susto errado)

A mutação C mostrou que um erro que o resíduo **aceita** (`QR.tr` 2 mm, resíduo 0,72) tira a moldura da
tolerância. Medi por quanto (`CropSobResiduoAceitoInstrumentedTest`: 11 pontos × 2 direções × 0,2/0,5/1,0/2,0 mm).

**Registro de um erro meu, mantido (P7).** A primeira leitura da tabela dizia que, com só 1 mm de erro em
vários cantos de marcador, a moldura ia para fora da janela de ±3 mm ("3,00") e depois de ±12 mm
("12,00", às vezes com 0,2 mm, e sem monotonia). **Estava errada, e a causa era o meu instrumento:** o
`NULO` era sempre a borda **esquerda**, que coincide com a borda do recorte (a área ocupa a largura inteira
da região; o traço esquerdo está metade fora da imagem), e um deslocamento mínimo o faz cruzar o limiar de
"escuro". Ao registrar as quatro bordas individualmente, as outras três estavam a ≤ 0,35 mm nos mesmos
casos. O instrumento passou a medir direita, topo e base; o alinhamento horizontal é conferido pela tinta
(4.2). A borda esquerda **não é medida por moldura**, e isso é uma limitação do oráculo, não do recorte.

**Com o instrumento corrigido** (folha de frente, erro de **um** ponto por vez):
| Erro no ponto | Pior afastamento da moldura, entre os 22 casos | Pior caso |
|---|---|---|
| 0,2 mm | 0,13 mm | `QR.tr` +x |
| 0,5 mm | 0,21 mm | `QR.tr` +x |
| 1,0 mm | **0,42 mm** | `BR.c4` +y |
| 2,0 mm, **aceito pelo resíduo** | **0,66 e 0,89 mm** | `QR.tr` +x (resíduo 0,72) e `BR.c4` +y (resíduo 0,94) |

Todo erro de até 1 mm move o recorte ≤ 0,42 mm (dentro da tolerância de 0,5 mm). **A lacuna** são os dois
pontos que a 3.3 já apontou como mais fracos, com 2 mm: o resíduo aceita e o recorte anda mais que 0,5 mm.
O que amortece: a folga vertical de 2 mm entre a moldura e a borda da área; o que não amortece: o eixo
horizontal, sem folga. Pinado em asserção (o que a tabela mostrou, e dito assim). **Não é mitigado, é
conhecido**; o teto de 1,0 mm não foi mexido (P11). Um caso a 2 mm com resíduo 1,141 (`TL.c4` +y) deu
sentinela na janela larga; ele é **recusado** pelo teto e não chega ao recorte em produção.

**O que o grupo 4 não verifica:** papel; letra real; a extrapolação do canto inferior esquerdo além do
documento renderizado; erro simultâneo de vários pontos; foto de celular (compressão, desfoque).

## Grupo 5 — o desvio (tarefas 5.1 e 5.2), 2026-09-30

`DesvioDaRespostaInstrumentedTest` (6) e o contador `TintaDoAluno` (9 testes JVM). Com os grupos 2 a 4:
**24 de 24** instrumentados, `timestamp` 14:32:22Z; `testDebugUnitTest` 353. Folha renderizada, de
frente e em ângulo; **tinta sintética**, retângulos pretos de área conhecida. Nada é papel.

`RecorteOutcome.Recortado` ganha `desvio: DesvioDaResposta`. A tinta é a cobertura contra o `PaperWhite`
local acima do tom decorativo máximo da região (500‰); a máscara é a tinta impressa (2 marcadores, o QR e
os 4 lados da moldura) dilatada pelo teto do resíduo (10 px); a classificação é a do domínio (1.3).

### O que foi medido (centésimos de mm²; 1 mm² = 100)

| Caso | Construído | Medido (frente / ângulo, regiões 1 e 2) |
|---|---|---|
| tinta dentro | 10 000 | 10 021 / 10 018 / 10 000 / 10 003, `fora` = 0 nos 4 |
| tinta fora (faixa da esquerda) | 2 800 | 2 827 / 2 794 / 2 810 / 2 812 (proporção 21,8–22,0%, sinalizada) |
| folha em branco | 0 | **0 e 0** nos 4, proporção 0 |
| mancha abaixo do piso | 280 (com dentro = 1 600) | 280 / 279 / 280 / 281; proporção 14,9%, **não** sinalizada |

Pior erro de contagem: 1,0% (o critério de 10% foi fixado antes, e a guarda de vacuidade da 5.2 é esta
tabela). O recorte com a tinta de fora é **igual byte a byte** ao sem ela, nos 4 quadros.

### Erros meus, mantidos (P7)

- O primeiro `dentro()` do teste centrava o quadrado com `quadWidth * uSize`, uma multiplicação de `Int` por
  `Int` que **estourava** (87 000 × 1 000 000) antes da divisão: o "quadrado de 10 mm no meio" caiu fora do
  lugar, 4 dos 6 testes reprovaram, e li isso como falha do recorte por alguns minutos. **A causa era o
  helper do teste** (`dentro: 4180`, `fora: 2000` numa folha sem tinta de fora); o código de produção não
  usa essa conta. Corrigido com `toDouble()`.

### Visto falhar (previsão antes)

| Mutação | Previsto | Real |
|---|---|---|
| **D1** sem máscara | a folha em branco acusa tinta impressa; quase tudo cai | 5 de 6 (só o sinal de conferência passa): `dentro` = 5025 na folha em branco |
| **D2** limiar de tinta em 200‰ (a pauta a 300‰ passa a contar) | branco, dentro, extrapola, piso; **não** faixa nem sinal | exatamente esses 4: `dentro` = 4947 na folha em branco, 14 789 contra 10 000 |
| **D3** contador `return Contagem(0, 0)` | dentro, extrapola, piso, faixa; a folha em branco **passa** | exatamente esses 4, e a folha em branco passou — **sozinha, ela seria uma guarda vazia (P13)** |
| **D3** nos testes JVM | — | 3 dos 9 de `TintaDoAlunoTest` caem |

**A mutação "limiar fixo em 255" que a tarefa 5.1 cita não discrimina neste material:** as páginas
renderizadas são brancas puras (255), então o branco local do `PaperWhite` e 255 coincidem e o resultado
seria o mesmo. Troquei por D2 (o tom, que é onde a pauta entra), e digo aqui que a mutação escrita na tarefa
não foi feita. O `PaperWhite` só será exercido de verdade por foto com sombra, que não existe aqui.

Reversões conferidas por `diff` contra o original; `grep -c MUTACAO` = 0.

### O que o grupo 5 não verifica

Letra de aluno; tinta de caneta esferográfica com falha; sombra na faixa; **os dois limiares (5% e 4 mm²)
continuam suposições, agora com fronteira pinada, e não medidas**; o efeito da dilatação de 1 mm da máscara
sobre tinta do aluno encostada na moldura (a máscara a esconde, de propósito, e isso não foi quantificado).

## Grupo 6 — "sem cabeçalho", camada 2 (tarefa 6.1), 2026-09-30

`RecorteSemCabecalhoInstrumentedTest` (5 testes), no `platos-atd34`. Com todo o resto do Android desta
mudança: **29 de 29** instrumentados, `timestamp` na janela deste comando. Folha renderizada, de frente e
em ângulo; o "nome do aluno" é um `DrawText` no `LayoutMap` (o que a fatia 7 vai imprimir). **Nada é papel.**

**Fixture (a):** duas linhas de nome — uma no topo da página, outra colada em cima da região — e mapa
**válido** (a camada 1 o aceita). **Guarda de vacuidade da fixture (P13):** as duas linhas foram mesmo
desenhadas (≈3 518 px escuros no topo; ≈3 519 px colado na região 1; 5 617 na região 2, onde a faixa
colada também pega o enunciado da questão). **Guarda de vacuidade do contador:** com um quadrado plantado,
o mesmo pipeline conta mais de 8 000 centésimos.

| Caso | Camada 1 (validação do mapa) | Camada 2 (recorte) |
|---|---|---|
| (a) folha limpa, nome impresso em volta | aceita (mapa válido) | **zero** de tinta, nos 4 quadros |
| (b) texto que começa dentro da largura e dentro da área | **recusa** | tinta 3 415–3 563 |
| (c) texto que começa 8 mm fora da largura e invade | **aceita** (limite conhecido) | tinta 5 621–5 716 |

**A camada 2 vê o que a 1 não vê (c).** Foi essa a razão de ter duas camadas, e o caso (c) é o que carrega
a independência.

**Visto falhar (previsão antes), com conjuntos disjuntos:**
| Mutação | Previsto | Real |
|---|---|---|
| **E** (captura) janela do canvas 20 mm acima | cai (a); passam b1 e c1; b2 e c2 incertos | cai **só** (a): `ha tinta no recorte de uma folha sem escrita expected: 0 but was: 4606` (o nome colado em cima entrou); b2 e c2 passam |
| **G** (domínio) regra da camada 1 desligada | cai só `b_camada_1...` | exatamente esse; (a), b2, c1 e c2 passam |

E não derruba nenhum teste da camada 1 (o domínio não é tocado), e G derruba 6 testes da camada 1 (grupo 1)
e só 1 da camada 2. **Conjuntos disjuntos entre as duas camadas: a prova de que são independentes** (rigorous
§3). Reversões conferidas por `diff`, `grep -c MUTACAO` = 0.

**O que não é verificado:** o nome impresso em papel (fonte, tamanho e posição reais da fatia 7); texto em
cor; um cabeçalho que ocupe a moldura *por baixo* da letra do aluno. A camada 1 continua sem ver o texto
que entra por fora, e só o recorte sobre o documento renderizado o vê — **em papel, não foi medido**.

## Fechamento (tarefas 7.1 a 7.4), 2026-09-30

### 7.1 — a suíte cheia, comparada com a linha de base

Repetida a da 0.1, com o Docker de pé e o emulador **encerrado** durante o agregado (memória: 2,4 GB livres
com tudo aberto; 9,3 GB depois de encerrar o emulador e os daemons). Logs em `scratchpad/5c-0-baseline/`.

| Comando | Janela | Resultado | Contagem (`timestamp` dentro da janela) | Base | Diferença |
|---|---|---|---|---|---|
| `./gradlew -p buildSrc test --rerun-tasks` | antes do agregado | BUILD SUCCESSFUL | `buildSrc:test` 1 | 1 | — |
| `./gradlew build --rerun-tasks` | 14:37:41Z–14:41:16Z | BUILD SUCCESSFUL em 3m06s, **183 de 183 tasks executadas** | android debug/release **353**/**353**; api **171**; domain jvm **437**, js **428**, host **428** | 344/344; 171; 414/405/405 | android **+9** (contador de tinta); api **0**; domain **+23** (2 faixa + 11 área limpa + 10 desvio) |
| `./gradlew :apps:android:connectedDebugAndroidTest` (`platos-atd34`, **sem filtro**) | 14:42:08Z–14:43:50Z | BUILD SUCCESSFUL | 126 executados + 2 ignorados = **128 finalizados**, 0 falhas; relatório 14:43:47Z | 97 + 2 = 99 | **+29** (3 + 6 + 2 + 6 + 1 + 6 + 5) |
| `npx vitest run` (`apps/web`) | — | **não executado**: esta mudança não toca `apps/web` (7.2, abaixo) | — | — | — |

**Nenhuma regressão:** as contagens de `api`, `result-sync`, `scan-session` e do que a mudança não tocou são
as da linha de base, e nenhum teste existente mudou de asserção. Um teste existente (`QR declarado que nao
existe na pagina e recusado`) apareceu numa mutação (regra 1.2, `<=`) como confirmação independente do contato
de borda, sem ser alterado.

### 7.2 — o escopo

`git diff --stat origin/main...`: **28 arquivos, +3 827 −13**; produção: `TintaDoAluno`, `RecorteDaResposta`,
`RegionDetector`, `RegionQrReader`, `SheetReader` (Android) e `DesvioDaResposta`, `EssayGeometry`,
`LayoutMapValidation` (domínio). Filtro por `ScanSession|ScanActivity|outbox|apps/api|apps/web|migration|
fixtures/|golden|supabase|openspec/specs/` → **nenhum**. `sha256sum fixtures/*.layout.json
fixtures/*.package.json` **igual** ao da 0.2.

`grep -rn "recortar(" apps/android/src/main` → duas definições: `RecorteDaResposta.recortar` e o delegado
`SheetReader.recortar`. **Nenhum chamador de produção**, nem fora de `vision/`: a afirmação da decisão 1 do
design ("código testado e sem chamador até a 5c-1") deixou de ser lembrança e virou leitura. (A tarefa dizia
"só a definição e o teste"; o delegado existe porque o design manda o ponto de entrada em `SheetReader`.)

### 7.3 — o §16 (P27)

- **Paga:** "Garantia executável de que o recorte discursivo não contém cabeçalho" (fatia-limite 5, tabela
  "Aberto"), pelas duas camadas — regra do `LayoutMap` (`AreaDeRespostaLimpaTest`) e recorte sobre o
  documento renderizado (`RecorteSemCabecalhoInstrumentedTest`) —, com o **limite declarado** da camada 1.
- **Atualizada** (texto anterior mantido, P7): "A região discursiva ainda não passou pelo aparelho nem
  pelo papel", com o que foi medido no documento renderizado e os quatro pontos que o papel ainda mede.
- **Abertas** na tabela que a guarda lê: "O limiar do desvio (5% e 4 mm²) e o teto do resíduo (1,0 mm) foram
  fixados sem letra de aluno" (`6`) e "A guarda de dívida não lê a tabela 'Aberto' do §16" (`5`).
- **Não tocada, de propósito:** "Injeção de prompt manuscrita pelo aluno, no eval set" (fatia-limite 5). Não há
  IA na fatia 5. É reconciliada — paga ou reagendada para `8`, com o motivo — no archive da **última** mudança
  da fatia 5.

`node tools/divida/divida.mjs`: **`exit 0`, 23 linhas lidas** (eram 21), as duas linhas novas listadas; a de
fatia-limite `5` também aparece em "vence nesta fatia (5c)", como lembrete informativo.
**Visto falhar:** uma cópia do documento com o token da linha nova sem crases, via `--arquitetura`, faz a
guarda sair com **2**, com a mensagem `a linha "O limiar do desvio ... " nao comeca com token entre crases na
coluna 'Fatia-limite'`. **Uma primeira tentativa foi falsa**: o Python não criou a cópia (caminho `/c/...`) e
a guarda saiu com 2 por *arquivo inexistente* — o motivo errado (P9). Refeita por um caminho que o Python
cria, e a mensagem conferida.

### O que esta mudança NÃO verificou (P6, P8)

- **Papel, letra de aluno, foto de celular.** Toda medida é sobre o documento renderizado, de frente e em uma
  perspectiva moderada (cantos da página a 1–4%); a tinta do aluno é sintética.
- **Os três números novos são suposições fixadas antes da execução, com fronteira pinada:** teto do resíduo
  1,0 mm, proporção de desvio 5% e piso 4 mm². Pinar não é medir.
- **O canto inferior esquerdo continua "conhecido, não mitigado":** a extrapolação foi medida no documento
  renderizado (moldura a ≤ 0,07 mm; tinta inteira no recorte), não em papel.
- **O resíduo não é guarda suficiente do recorte:** dois pontos (`QR.tr` em `+x`, `BR.c4` em `+y`) com 2 mm de
  erro passam pelo teto e movem a moldura 0,66–0,89 mm. Medido, pinado, e registrado no §16.
- **A camada 1 do "sem cabeçalho" não vê texto que entra por fora**; só o recorte sobre o documento renderizado
  o vê, e não em papel.
- **A semântica de `position` foi medida por um critério que não é cego** (ADR-0020).
- **Erro simultâneo de vários pontos**, desfoque e compressão de foto: não medidos.
- **`PaperWhite` só foi exercido sobre páginas de branco puro (255):** o branco local e 255 coincidem, e a
  mutação "limiar fixo em 255" da tarefa 5.1 não discrimina neste material (usei o tom, D2).
- **Nenhum código de produção chama o recorte** (7.2). O `proposal.md` da `slice-5c-1` **SHALL** abrir com o
  chamador e com a decisão de onde a imagem mora e por quanto tempo (ADR-0012, classe de retenção).

### Erros e correções de rumo desta mudança (P7, todos mantidos acima, resumidos aqui)

1. O agregado da linha de base "passou" no aviso de conclusão porque o `exit 0` era do `date` que eu pus depois
   do Gradle, e o build tinha falhado (Docker parado; depois, falha guardada pelo daemon).
2. A guarda D-1.2 proíbe ponto flutuante no `commonMain`: a classificação do desvio foi reescrita em inteiros.
3. O critério da 2.2 **reprovou** e a hipótese foi reformulada por ADR-0020, por decisão do mantenedor.
4. Um susto errado sobre a tabela de sensibilidade do recorte foi um artefato do meu instrumento (a borda
   esquerda da moldura coincide com a borda do recorte).
5. Um estouro de `Int` no meu helper de teste fez quatro testes do desvio reprovarem por minutos.
6. Uma primeira "prova" de que a guarda de dívida reprova era o arquivo que não existia.
