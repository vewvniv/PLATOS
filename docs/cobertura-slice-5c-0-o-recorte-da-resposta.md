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
