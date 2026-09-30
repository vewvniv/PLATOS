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
