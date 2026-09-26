# Cobertura — `slice-5b-2-a-nota-objetiva-parcial`

Os artefatos estão em `openspec/changes/slice-5b-2-a-nota-objetiva-parcial/`. Este documento registra
**como** cada verificação foi vista falhar, e não que ela passa (`rigorous.md` §8). As datas são UTC.

## 0. Linha de base

**0.1** — Sobre `main` (`e8db489`) e o commit da proposta (`7eb31f6`), que só acrescenta arquivos em
`openspec/changes/`. De 2026-09-26T11:11:05Z a 11:15:46Z:

| Comando | Resultado |
|---|---|
| `./gradlew build --rerun-tasks` | `BUILD SUCCESSFUL in 2m 20s`, **183 de 183 tasks executadas**, de 11:11:05Z a 11:13:27Z |
| `./gradlew -p buildSrc test --rerun-tasks` | 1 de 1, `timestamp` 11:14:38Z |
| `./gradlew :apps:android:connectedDebugAndroidTest`, sem filtro, no `platos-atd34` | 89 testes, 0 falhas, 2 pulados, `timestamp` 11:15:44Z. O console diz "Finished 91 tests", como na 5b-1; a âncora é o XML (P3) |
| `npx vitest run` em `apps/web` | 18 de 18, 11:11:13Z |

Relatórios do `build`, lidos pelo XML de cada task (`relatorios.mjs`, no scratchpad), com cada
`timestamp` dentro da janela dele:

| Task | Testes | `timestamp` |
|---|---|---|
| `apps/android` `testDebugUnitTest` | 320 | 11:12:30Z .. 11:12:34Z |
| `apps/android` `testReleaseUnitTest` | 320 | 11:12:16Z .. 11:12:26Z |
| `apps/api` `test` | 168 | 11:13:07Z .. 11:13:22Z |
| `packages/domain` `jvmTest` | 393 | 11:12:58Z .. 11:13:01Z |
| `packages/domain` `jsNodeTest` | 384 | 11:13:03Z .. 11:13:05Z |
| `packages/domain` `testAndroidHostTest` | 384 | 11:12:51Z .. 11:12:53Z |

Os relatórios de `buildSrc` e do instrumentado estavam **velhos** antes dos passos próprios deles
(09:41:15Z e 09:42:16Z, do fechamento da 5b-1), e foram lidos de novo depois.

**0.2** — `node tools/divida/divida.mjs`, `exit 0`, "nenhuma linha vencida: 21 linhas lidas". "fatia
corrente: 5b, de slice-5b-2-a-nota-objetiva-parcial (ativa), …". Sob "vence nesta fatia (5b)":
- `Acurácia em manuscrito` (`5`);
- `Modo degradado (§10) não existe` (`5`);
- `O limiar do OMR foi apurado sobre um aparelho e uma impressora` (`5`).

As duas linhas `6` (`A região discursiva ainda não passou pelo aparelho nem pelo papel` e `A folha de
teste de impressão não aprova a região discursiva que a prova imprime`) aparecem "em dia".

## 1. A parcial no domínio

**1.1 — o julgamento por questão é um só** (`e84264d`, só refatoração). A repetição, a conferência de
conjunto e o laço saem de `score` para `julgar`, que recebe o conjunto de itens que a folha tem de ter.
Nenhum arquivo de teste mudou. `--rerun` nos três alvos, de 11:18:33Z a 11:18:40Z: `jvmTest` 393,
`jsNodeTest` 384, `testAndroidHostTest` 384, as contagens da 0.1, com `ObjectiveScoringTest` 29 de 29
em cada um.

Não há "ver falhar" próprio, porque a refatoração não acrescenta verificação: o que a prende são os 29
cenários de `ObjectiveScoringTest`, vistos falhar nas fatias que os criaram (*herdado*).

**1.2 — o tipo da parcial** (`4edc738`, contrato antes do consumidor). `PartialScore` não é
`ObjectiveScore`, não herda dela nem a contém, e não tem `closed`. As guardas são as da nota, na escala
da parte objetiva, mais a nova. `PartialScoreTest`: 8 testes, um por guarda e um positivo, 8 de 8 nos
três alvos às 11:20:28Z–11:20:36Z. Cada teste parte da parcial válida da fixture e viola **só** a
guarda dele, e a asserção lê a frase:

| Guarda | O caso | A frase conferida |
|---|---|---|
| escala | 5 pontos numa parte objetiva de 4 | "parcial 5 fora de 0..4" |
| disputa | 4 pontos apurados e 1 em disputa | "em disputa passam de 4" |
| soma | evidência de 3 e parcial de 2 | "a evidencia soma 3 ponto(s) e a parcial apurada e 2" |
| repetição, na evidência | `q1` duas vezes | "a parcial repete o item: q1" |
| repetição, entre os dois lados | `q1` objetiva e aguardando correção | "a parcial repete o item: q1" |
| pendências | a evidência diz `q5`, a lista diz `q4` | "dependem de revisao [q5] e a lista de pendencias diz [q4]" |
| **máximo** (a nova) | pacote com um critério de `d1` de 2 para 3, e `max_score` 11 | "o maximo objetivo (4) mais as discursivas aguardando correcao (8) somam 12, e a prova vale 11" |

O caso da escala também passa da guarda de disputa, e é a frase que diz qual das duas segurou: a de
escala vem antes. É a mesma situação do teste equivalente de `ObjectiveScore`.

**1.3 — `ObjectiveScoring.scorePartial`** (`28340b2`). `ParcialObjetivaTest`, um teste por cenário da
ADDED de `scoring`, com o oráculo **fixado** (4 objetivas de 1 ponto, `d1` 3, `d2` 4, máximo 11), e uma
guarda de vacuidade sobre a fixture: 7 de 7 nos três alvos, 11:22:12Z–11:22:20Z. `ObjectiveScoringTest`
29 de 29, sem mudar, incluído o cenário da 8.1 da 5a ("A apuração completa de prova com discursiva
continua recusada"). A frase de divergência da parcial diz "nao objetivos na variante", porque `d1` é
declarado pela variante e não é objetivo. A de `score` continua byte a byte a mesma.

**Vista falhar:** a discursiva contada também como objetiva (`objetivos += itemId`, `// MUTACAO`).
- **Previsto:** caem os quatro cenários que esperam parcial e o da divergência; ficam verdes a guarda da
  fixture e a prova só objetiva.
- **Real:** exatamente esses 5, às 11:22:43Z. Os quatro com "esperava parcial, veio: Rejected(…
  faltando: d1, d2)", e o da divergência com a frase trocada.
- **Reversão:** `git grep MUTACAO` vazio, e 7 de 7 às 11:22:58Z. A task filtrada fecha `FAILED` pela
  guarda contra comando estreito (401 métodos sem resultado), com 0 falhas no XML.

**1.4 — M-guarda: a guarda do máximo desligada** (`require(true || …)`, `// MUTACAO`), com o
`jvmTest` **inteiro**, e não filtrado, para que "só" seja medido.
- **Previsto:** cai só "parcial cujo maximo nao fecha com o da prova nao e representavel".
- **Real:** 408 testes, **1 falha**, e é essa, com "Expected an exception of class
  java.lang.IllegalArgumentException to be thrown, but was completed successfully", às 11:23:30Z. O
  pacote do teste passa em todas as outras guardas, e nenhuma delas o segurou.
- **Reversão:** `git grep MUTACAO` vazio, `git status` limpo, e 408 de 408 às 11:23:50Z.

**1.5 — M-tipo: a proteção de tipo, vista falhar fora da árvore.** Um arquivo temporário,
`apps/android/src/main/kotlin/com/platos/android/outbox/MTipoTemporario.kt`, monta um
`ResultadoPendente` com `nota = parcial`, a parcial saindo de `scorePartial`. Ele nunca foi commitado.
- **Sem mutação**, `./gradlew :apps:android:compileDebugKotlin` às 11:24:10Z, `rc=1`:
  `e: …/MTipoTemporario.kt:12:74 Argument type mismatch: actual type is 'PartialScore', but
  'ObjectiveScore' was expected.`
- **Com a mutação** (`PartialScoringOutcome.Scored` passa a carregar `ObjectiveScore`, e `scorePartial`
  a construir uma, com o máximo objetivo como `maxScore`): o mesmo arquivo compila, `BUILD SUCCESSFUL`
  às 11:24:50Z, com `:apps:android:compileDebugKotlin` executada. É o estado em que uma folha sem
  pendência objetiva sairia `closed` e seria gravada como nota final.
- **Reversão:** o arquivo foi apagado e as duas mutações desfeitas. `git status` limpo, `git grep
  MUTACAO` vazio, e a compilação de novo com `BUILD SUCCESSFUL` às 11:25:10Z.

**Esta proteção não tem teste automático que a guarde** (P8). Quem a guarda é o tipo: um teste que
não compila não pode morar na suíte, e a prova de que ela reage é a execução acima. Se alguém fizer
`scorePartial` devolver `ObjectiveScore`, como a mutação, ou alargar `ResultadoPendente.nota` para um
tipo que aceite as duas, nenhum teste cai.

## 2. A sessão

**Antes da 2.2, a spec foi alinhada ao desenho** (`3ca74e3`). A spec de `scan-session` dizia "Quando o
gabarito não foi lido no quadro, a sessão SHALL NOT apresentar parcial", e a decisão 6 dizia que o
caderno do mesmo aluno mantém a última parcial. O mantenedor decidiu pelo desenho, e entrou o cenário
"A outra página do mesmo aluno mantém a parcial".

**2.1 — o renome** (`18d6b15`). `DiscursivaNaoCorrigivel` passou a `ProvaComDiscursiva`, e cada linha
alterada difere só no nome. `testDebugUnitTest --rerun`: 320, a contagem da 0.1, às 11:26:17Z–11:26:21Z;
`ProvaComDiscursivaNaSessaoTest` 7 de 7 e `ScanSessionTest` 19 de 19, sem asserção alterada.

**2.2 — a parcial na sessão** (`7f9af78`). Com `FrameOutcome.Read`, a sessão chama `scorePartial`, e o
estado ganha a parcial ou o motivo. Sem gabarito no quadro, vale a última parcial do mesmo aluno. O
`AVISO` é "A nota nao e definitiva: a correcao das discursivas ainda nao esta disponivel neste
aparelho. Nada foi guardado.", conferido por igualdade. Um teste por cenário, conferindo a frase, o
aluno e os números **fixados** (3 de 4, `d1` 3, `d2` 4, máximo 11):
- "Folha de prova com discursiva no quadro". O teste da 5b-1, "a folha e reconhecida … sem nota", era
  do requisito removido, e passou a conferir a parcial;
- "Só as discursivas no quadro", sem parcial;
- "A outra página do mesmo aluno mantém a parcial";
- "A parcial recusada mostra o motivo". O motivo é produzido pelo domínio e comparado por igualdade,
  como `ScanSessionTest` faz, e o gabarito aparece com problema no caderno (decisão 6);
- "Nada é gravado" e "Abrir a câmera", que já existiam.

"Prova só objetiva não muda" é `ScanSessionTest`, 19 de 19 sem mudar. O helper `gabarito()` ganhou
respostas, e a KDoc que dizia que elas não importavam ficou como histórico.

**2.3 — o caderno do aluno** (`e5a7a9b` e `9524328`). `ObjectiveScoring.resolveVariant` passou a
pública, num commit só de visibilidade, para que o caderno use o mapa da mesma variante da parcial
sem uma segunda regra no aplicativo. O caderno (`Caderno.kt`) é estado da sessão, em memória: as
regiões do `LayoutMap` da variante, cada uma capturada, com problema (com o motivo) ou não vista, e a
última parcial. O gabarito é a região que não é discursiva, pela regra de `SheetReader.analyze`.
`resume()` não limpa o caderno: voltar a procurar não muda de quem é a folha.

Um teste por cenário, com os quadros montados a partir da fixture discursiva, e uma guarda de
vacuidade (regiões 0, 1 e 2; `null`, `d1`, `d2`). Os testes conferem **índice e estado**, e nunca o
rótulo, para que a mutação da 2.4 derrube só o cenário dele. "Prova só objetiva não tem caderno" é um
teste novo em `ScanSessionTest`. 329 testes às 11:32:55Z.

**Vistas falhar**, com o `testDebugUnitTest` inteiro. A tarefa não pedia mutação aqui; os testes foram
escritos depois do código, e por isso foram vistos falhar (P9):

| Mutação | Previsto | Real |
|---|---|---|
| "capturada não volta atrás" desligada | cai só "a regiao capturada continua capturada…" | **329 testes, 1 falha**, essa, com as regiões 0 e 1 voltando a `ComProblema`, às 11:32:20Z |
| o caderno sem troca de aluno | cai só "a folha de outro aluno comeca outro caderno…" | 1 falha, essa, com "expected: <tok-b> but was: <tok-a>", às 11:32:38Z |

Revertidas, `git grep MUTACAO` vazio, e 329 de 329 às 11:32:55Z.

**2.4 — o número do indicador** (`19201d4`). O rótulo é a chave de `positions` do item da região, e
"Gabarito" no gabarito. Dois testes:
- o cenário "O indicador tem o número impresso", com o oráculo fixado: `Gabarito`, `3`, `6`;
- a conferência da P28. Na folha de `tok-a`, o texto na linha de base da primeira linha do enunciado
  de cada discursiva, logo à esquerda dela, é a chave de `positions` seguida de ponto ("3." e "6."). O
  número é achado **pela geometria**, e não pelo `id` da primitiva.

**A "folha renderizada" desta conferência é o `LayoutMap` da atribuição** (`folhaDaAtribuicao`), e não
um PDF. Ele é a fonte geométrica que os dois renderizadores desenham, e o `text` de cada `DrawText` é o
que vai ao papel. O passo do mapa ao pixel é da paridade e da fidelidade do CI (*herdado*), e não
desta conferência.

| Mutação | Previsto | Real |
|---|---|---|
| rótulo tirado de `regiao.index` | cai só o cenário do indicador; a conferência fica verde, porque não lê o indicador | 331 testes, **1 falha**, essa, com "(1, 1), (2, 2)" no lugar de "(1, 3), (2, 6)", às 11:34:08Z |
| a chave de `d1` trocada por "7" na entrada da conferência | cai só a conferência | 1 falha, essa, com "expected: <7.> but was: <3.>", às 11:34:31Z |

**A primeira reexecução depois da segunda reversão não rodou.** A reversão comeu uma quebra de linha,
e o teste não compilou (`rc=1`). O leitor de relatórios mostrou "1 falha", e era o relatório da
execução mutada: só o `timestamp`, 11:34:31Z, igual ao da mutação, denunciou (P3). Com a linha
consertada, `git grep MUTACAO` vazio e 331 de 331 às 11:35:05Z.

**2.5 — "nada é gravado" com a parcial presente.** Antes da mutação, uma correção (`17bce08`): o
canário que a 2.2 pôs neste teste lia a parcial **do estado da sessão**. A M-a desliga justamente a
camada da sessão, e o teste cairia pelo canário, e não por ter entregue algo para gravar. Era a
fixture sombreando a camada que ele mede (`rigorous.md` §3). O canário passou a conferir que a leitura
do gabarito rende parcial **no domínio**.

| Mutação | Previsto | Real |
|---|---|---|
| **M-a** — `comDiscursiva = false`: a sessão apura como objetiva | caem 11 dos 17 de `ProvaComDiscursivaNaSessaoTest` (os da parcial, o da região não lida, e os seis do caderno que passam pela sessão); ficam verdes os 6 que não dependem dela: as duas guardas, **"nada é gravado"** (o domínio recusa a apuração completa), "abre e procura", "outra prova" e a conferência do número impresso | **exatamente esses**: 331 testes, 11 falhas, às 11:36:19Z. As que caíram disseram "esperava a prova com discursiva reconhecida, veio Rejected(reason=itens lidos divergem da variante 'v1'; faltando: …)" ou "…veio Searching". `ScanSessionTest` sem falha |
| **M-a e M-c juntas** — e o domínio aceita leitura que é subconjunto do declarado | cai também "nada é gravado" | 12 falhas, às 11:36:42Z. A nova é "a sessao entregou apuracao de prova com discursiva ==> expected: <null> but was: <ApuracaoNova(…" |

A segunda linha não era pedida pela tarefa. Ela é a prova de que "nada é gravado", que mudou nesta
mudança, ainda consegue falhar. As duas camadas, cada uma sozinha, seguram o cenário, como na 5b-1.
Revertidas, `git grep MUTACAO` vazio, `git status` limpo, e de novo: Android 331 de 331 às
11:37:07Z e domínio `jvmTest` 408 de 408 às 11:37:03Z.

**2.6 — a tela** (`28607df`). `ScanScreen` desenha a parcial, os indicadores em três estados, com o
estado também em texto, os motivos, o contador e o aviso. O texto da parcial sai de
`apresentar(PartialScore)`, fora da composição, com um teste em `NotaApresentadaTest`: "2 de 4 na
objetiva" e "Discursivas: 7 ponto(s) aguardam correcao · a prova vale 11". 332 testes às 11:38:28Z, e
`assembleDebug` compila. **A tela desenhada não tem teste automático** (decisão 5 da 5b-1), e é
lacuna. Não é mitigado, é conhecido (P8).

## 3. Fechamento

Sobre `51e978f`.

**3.1:** `git grep MUTACAO`, fora de `build/`, `node_modules/`, `docs/`, `openspec/` e `rigorous.md`,
não achou nada (`rc=1`), e `git status` estava limpo. As mutações desta mudança foram nove, e cada
reversão foi rodada, com a execução registrada ao lado dela:
- 1.3, 1.4 e 1.5, no domínio. A M-tipo foi revertida por compilação, e as outras duas por teste;
- 2.3, duas;
- 2.4, duas;
- 2.5, a M-a e a M-a com a M-c.

**3.2, o comando cheio:**

| Comando | Resultado |
|---|---|
| `./gradlew build --rerun-tasks` | `BUILD SUCCESSFUL in 2m 10s`, **183 de 183 tasks executadas**, de 11:39:38Z a 11:41:49Z |
| `./gradlew -p buildSrc test --rerun-tasks` | 1 de 1, 11:42:09Z |
| `./gradlew :apps:android:connectedDebugAndroidTest`, sem filtro, no `platos-atd34` | 89 testes, 0 falhas, 2 pulados, `timestamp` 11:43:05Z. O console diz "Finished 91 tests", como na 0.1 |
| `npx vitest run` e `npm run build` em `apps/web` | 18 de 18, às 11:43:23Z; o build com `exit 0` |
| `limiar.mjs`, `answer-kind.mjs`, `fio.mjs`, `renderizador.mjs` e `divida.mjs` | todas com `exit 0`; o renderizador diz "versao 2", e a dívida, "nenhuma linha vencida: 21 linhas lidas" |

| Task | Testes | 0.1 | `timestamp` |
|---|---|---|---|
| `apps/android` `testDebugUnitTest` | 332 | 320 | 11:40:37Z .. 11:40:42Z |
| `apps/android` `testReleaseUnitTest` | 332 | 320 | 11:40:46Z .. 11:40:52Z |
| `apps/api` `test` | 168 | 168 | 11:41:23Z .. 11:41:38Z |
| `packages/domain` `jvmTest` | 408 | 393 | 11:41:41Z .. 11:41:43Z |
| `packages/domain` `jsNodeTest` | 399 | 384 | 11:41:17Z .. 11:41:20Z |
| `packages/domain` `testAndroidHostTest` | 399 | 384 | 11:41:44Z .. 11:41:46Z |

**De onde vêm as diferenças, e todas são desta mudança:**
- **Domínio, +15 em cada alvo:** `PartialScoreTest` (8) e `ParcialObjetivaTest` (7).
- **Android, +12:** `ProvaComDiscursivaNaSessaoTest` de 7 para 17, `ScanSessionTest` de 19 para 20, e
  `NotaApresentadaTest` de 3 para 4.
- API, `buildSrc`, instrumentado e web não mudaram, e esta mudança não os toca.

**Os passos de paridade e fidelidade do CI não foram rodados aqui.** Esta mudança não toca
renderizador, `LayoutMap` nem fixture. O CI da PR os roda.

## O que ainda não foi verificado

- **A tela desenhada** (`ScanScreen`): os indicadores, as cores, o contador e a parcial não têm teste
  automático (decisão 5 da 5b-1). O que decide o texto da parcial tem teste (`apresentar`), e o que
  decide o estado de cada indicador também (o caderno). O desenho, não. **Não é mitigado, é
  conhecido** (P8), e é conferido na sessão única de papel antes da fatia 6.
- **A leitura da parcial pelo professor.** O risco de "3 de 4" ser lido como nota está no `design.md`.
  A tela mostra o máximo objetivo, o que as discursivas valem, o máximo da prova e "a nota nao e
  definitiva", mas **nenhum professor leu esta tela**. Não é mitigado, é conhecido.
- **A proteção de tipo** (a parcial não entra em `ResultadoPendente` nem em `ApuracaoNova`) **não tem
  teste automático**: quem a guarda é o compilador. Ela foi vista reagir fora da árvore (1.5). Se
  `scorePartial` passar a devolver `ObjectiveScore`, ou se `ResultadoPendente.nota` for alargado,
  nenhum teste cai.
- **O número do indicador é conferido contra o `LayoutMap` da folha, e não contra o papel.** O passo do
  mapa ao pixel é da paridade e da fidelidade do CI (*herdado*).
- **Nada foi medido com câmera.** Os quadros da sessão são montados a partir da fixture. O caminho da
  câmera até `FrameOutcome` é o da 5b-1, e o papel continua na sessão única antes da 6.
- **O caderno se perde ao sair da tela**, e isso é aceito: guardar é a 5b-3 (`design.md`, riscos).
  "Finalizar incompleto com confirmação, registrando o que faltou" (§8) não foi feito, porque registrar
  exige guardar.
- **A guarda nova do máximo lança exceção**, como as da nota, e não recusa. O pacote chega ao aparelho
  pela publicação, que confere a mesma soma (`Publish.kt:118`, `requireCoherent`), e o aparelho confere
  o hash (*herdado*). Um pacote cuja rubrica não fecha e que chegasse assim ao aparelho derrubaria a
  sessão, em vez de mostrar recusa.

## Reconciliação do §16, preparada para o archive (3.5)

`divida.mjs` diz "fatia corrente: 5b", e nenhuma linha vencida (0.2 e 3.2).

| Linha do §16 | No archive |
|---|---|
| `Acurácia em manuscrito` (`5`) | **alcançada e não paga.** Em dia até a 6 abrir. Esta mudança não mede manuscrito |
| `Modo degradado (§10) não existe` (`5`) | **alcançada e não paga.** Em dia até a 6 abrir |
| `O limiar do OMR foi apurado sobre um aparelho e uma impressora` (`5`) | **alcançada e não paga.** Em dia até a 6 abrir. Não houve papel nem aparelho novo |
| `A região discursiva ainda não passou pelo aparelho nem pelo papel` (`6`) | **não alcançada.** Nada foi impresso. O "custo aceito ao adiar" da linha diz que a 5b-2 vem antes do papel, e o retrofit sobre fato append-only só se materializa com nota discursiva gravada: **esta mudança não grava nada**, então não o materializa. A frase "a tela do estado novo não foi conferida no aparelho" continua verdadeira, agora com a parcial e os indicadores |
| `A folha de teste de impressão não aprova a região discursiva que a prova imprime` (`6`) | **não alcançada**: nenhuma impressão |
| `antes-de:migration-da-5-em-producao` e `antes-de:implantar-api-da-5a` | **não alcançados**: nenhuma migration, e nada no servidor |

**Fora do §16, e registrado aqui para não flutuar.** A cobertura da 5b-1 diz que a geometria da região
discursiva não tem conferência além do QR "até o segundo ajuste da 5b-2" (decisão 1 dela, atualização
de 2026-09-26). A proposta desta mudança pôs o recorte e o segundo ajuste do ADR-0018 na **5b-3**
("O que NÃO será alterado"), e esta mudança não os fez. A lacuna continua, com o destino 5b-3. Ela não
é linha do §16, e esta mudança não abre linha nova. Se o mantenedor quiser prazo reprovável, o veículo
é uma linha nova no archive (P27).
