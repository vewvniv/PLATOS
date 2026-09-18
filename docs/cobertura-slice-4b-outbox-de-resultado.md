# Cobertura — `slice-4b-outbox-de-resultado`

O que este documento é: **como cada verificação crítica foi vista falhar**, e não que ela passa.
`rigorous.md` §8 exige isso para fechar fatia, e a razão é que "passa" é a afirmação que todo teste
faz, inclusive o que não mede nada.

---

## 1. As mutações, e o conjunto que cada uma derrubou

**Cinco** mutações distintas foram injetadas, observadas, revertidas, e cada reversão foi conferida
**rodando** (P10). São seis seções porque a do apagamento local foi rodada duas vezes — contra os
dublês de JVM (1.4) e contra o `filesDir` e o SQLite de verdade (1.5) —, e as duas dizem coisas
diferentes. Nenhuma ficou na árvore: cada seção traz o `grep -c "MUTACAO"` de depois da reversão.

### 1.1 A evidência por questão descartava uma questão de zero ponto

**Mutação:** em `ObjectiveScoring.score`, remover da lista de evidência a primeira questão que rendeu
zero e não é pendência, mantendo o total correto.

**Por que esta mutação, e não outra:** ela isola a camada de **cobertura**. Uma mutação que removesse
qualquer questão derrubaria também a guarda de soma, e o vermelho não diria qual das duas segurou.

**Primeira execução — e o achado.** Caiu **um** cenário, e não o que diz cobrir isto:

| Cenário | Caiu? |
|---|---|
| `questao em branco rende zero na evidencia e nao aparece entre as pendencias` | **sim** |
| `a evidencia cobre todas as questoes da variante, uma vez cada` | não |

O teste de cobertura usava `todasCorretas()` — folha em que **nenhuma questão rende zero** —, então a
vítima da mutação não existia e ele passava. É o sombreamento de fixture do `rigorous.md` §3: o teste
afirmava cobrir todas as questões exercitando só o caso em que todas pontuam.

**A correção foi da fixture, e não da asserção (P12).** A folha passou a ter os quatro desfechos —
múltipla marcação, indecisa, em branco e erradas —, e o teste ganhou canário:
`assertTrue(nota.outcomes.any { it.earned == 0 && !it.pendente })`. Sem ele, a asserção de cobertura
volta a passar numa apuração que descarte exatamente essas.

**Segunda execução, com a fixture corrigida:** caem dois, e só eles.

| Cenário | Caiu? |
|---|---|
| `a evidencia cobre todas as questoes da variante, uma vez cada` | **sim** |
| `questao em branco rende zero na evidencia e nao aparece entre as pendencias` | **sim** |
| `a evidencia soma exatamente a nota apurada` | não |
| `evidencia incoerente com a nota nao e representavel` | não |
| `questao pendente rende zero na evidencia e continua pendente` | não |

Conjuntos disjuntos: a soma, as guardas de construção e a correspondência com as pendências são
camadas independentes da cobertura.

**Reversão:** `grep -c "MUTACAO"` → `0`; 28 testes, 0 falhas, relatório `2026-09-17T13:38:32.472Z`.

### 1.2 A declaração de retenção de `grading_result` foi removida

**Mutação:** apagar o `comment on table public.grading_result` da migration.

Sem ver a guarda vermelha, `RetentionDeclarationTest` passando não prova nada **sobre as tabelas
novas** — ela passava antes delas existirem.

| Cenário | Caiu? |
|---|---|
| `toda tabela de public declara finalidade e classe de retencao` | **sim** |
| `tabela sem comentario e acusada pelo nome` | não |
| `classe que a politica nao define e acusada, com as admitidas na mensagem` | não |
| `a guarda reage a uma declaracao removida` | não |
| os demais cenários sintéticos | não |

A separação importa: os cenários sintéticos exercitam a **lógica** contra entradas fabricadas, e o
que caiu foi o que lê o **catálogo real**. A mensagem nomeou o alvo, e não só recusou:

```
tabela sem finalidade ou classe declarada ==> expected: <[]> but was:
  <[grading_result sem comentario: falta finalidade e classe de retencao]>
```

**Reversão:** `grep -c "MUTACAO"` → `0`; `RetentionDeclarationTest` e `ConnectionRoleTest` verdes.

### 1.3 A trava de idempotência da rota foi desligada

**Mutação:** em `ResultQueries.record`, neutralizar o `if (jaGravada != null) return jaGravada`.

| Cenário | Caiu? |
|---|---|
| `reenvio da mesma captura nao cria registro novo e responde igual` | **sim** |
| `recaptura grava revisao nova, a corrente muda e a anterior continua legivel` | não |
| `duas folhas avulsas da mesma prova nao colidem` | não |
| os outros seis cenários da rota | não |

É o conjunto disjunto que a tarefa 4.5 exigia: a trava de **idempotência** e a de **revisão** são
coisas separadas, e uma mutação que derrubasse as duas não diria qual segurou.

**Reversão:** `grep -c "MUTACAO"` → `0`; 23 testes (9 de rota + 14 de tabela), 0 falhas.

### 1.4 O apagamento local passou a levar o pendente junto

**Mutação:** em `DeviceSession.sair` e em `revogar`, acrescentar
`pendentes.pendentesDa(org).forEach { pendentes.apagarConfirmado(it.captureId) }`.

| Cenário | Caiu? |
|---|---|
| `sair_preserva_o_resultado_pendente_e_apaga_o_resto` | **sim** |
| `revogacao_preserva_o_resultado_pendente_e_apaga_a_referencia` | **sim** |
| `EnvioDeResultadosTest.confirmado sai da fila` | não |
| `sair_apaga_o_roster_junto_com_o_resto` | não |
| `revogacao_observada_apaga_o_roster_sem_o_usuario_sair` | não |

O que este conjunto prova, e é o ponto da tarefa 7.6: **o expurgo legítimo e o apagamento indevido
são códigos distinguíveis.** Se `confirmado sai da fila` tivesse caído junto, os dois seriam o mesmo
caminho e nenhum dos dois estaria provado.

**Reversão:** `grep -c "MUTACAO"` → `0`; 295 testes do Android, 0 falhas, relatório
`2026-09-17T14:09:34.741Z`.

### 1.5 O apagamento local, contra a camada instrumentada

A mutação 1.4 foi reaplicada **só em `sair`**, e rodada contra `ApagamentoLocalInstrumentedTest`, que
mede o `filesDir` de verdade e o SQLite de verdade em vez de dublês.

| Cenário | Caiu? |
|---|---|
| `sair_apaga_referencia_do_disco_e_preserva_o_pendente` | **sim** |
| `o_pendente_de_outra_organizacao_nao_e_tocado` | **sim** |
| `revogacao_apaga_referencia_do_disco_e_preserva_o_pendente` | não |
| `a_base_do_outbox_continua_em_disco_depois_de_sair` | não |
| `sair_diz_quantas_correcoes_ficaram` | não |

Três coisas que este conjunto diz e o da JVM não dizia. A revogação **não** caiu, porque a mutação
tocou só `sair` — os dois caminhos são código separado no aparelho, e não só nos dublês. A base em
disco não caiu, porque apagar linhas não apaga o arquivo: são medidas diferentes, e é por isso que as
duas existem. E a contagem não caiu, porque ela é tirada **antes** do apagamento.

**Reversão:** `grep -c "MUTACAO"` → `0`; 5 testes instrumentados verdes.

### 1.6 O expurgo passou a acontecer antes da confirmação

**Mutação:** em `EnvioDeResultados`, chamar `apagarConfirmado` no topo do laço, antes de `enviar`.

**A previsão escrita na tarefa 6.6 estava errada, e fica dita em vez de corrigida em silêncio (P7).**
Ela dizia "6.3 e 6.4 caem e 6.2 não cai". O conjunto real:

| Cenário | Caiu? |
|---|---|
| `sem rede o pendente fica intacto` | **sim** |
| `recusa do servidor nao apaga o pendente` | **sim** |
| `recusa por vinculo revogado nao apaga o pendente` | **sim** |
| `um recusado nao bloqueia o resto da fila` | **sim** |
| `confirmado sai da fila` | não |
| todo o `DeviceSessionTest` | não |

Apagar antes da confirmação derruba **todos** os cenários de preservação, e poupa o do confirmado —
que continua sendo apagado. É esse conjunto que prova o que a tarefa queria: o expurgo é condicional
à confirmação, e não incondicional.

**Reversão:** `grep -c "MUTACAO"` → `0`; suíte do Android verde.

---

## 2. Os oráculos, e por que eles são independentes

- **A evidência por questão contra o total.** A soma dos pontos rendidos é conferida contra `points`,
  que vem do mesmo laço — mas por **caminho diferente**: o total é acumulado, a evidência é somada
  depois. E a lista de pendências é conferida contra uma **derivação da forma da resposta**
  (`QuestionOutcome.pendente`), e não contra a própria lista: dois caminhos concordando não provam
  muito, mas dois discordando denunciam o laço que esqueceu de relatar uma pendência.
- **O corpo que sobe, prendido dos dois lados por literais escritos à mão.**
  `ResultadoDtoTest` fixa o JSON que o aparelho produz; `ResultRouteTest.corpo()` monta um literal e
  o envia para a rota real. Comparar o produzido contra `Json.encodeToString` do mesmo DTO poria o
  mesmo código dos dois lados da igualdade — renomear `capture_id` continuaria verde e todo envio
  quebraria no aparelho.
- **A contagem de linhas é feita no banco, e nunca no retorno da rota.** Uma rota idempotente que
  respondesse certo e gravasse duas vezes passa em qualquer asserção sobre o corpo da resposta.
- **A coerência do corpo recebido é conferida pelo `ObjectiveScore` do domínio**, e não por uma
  validação própria da API. Duas implementações da mesma regra divergem (regra 7 do `CLAUDE.md`); o
  servidor confere com o mesmo código que rodou no aparelho.

---

## 3. As guardas de vacuidade (P13)

| Onde | O canário |
|---|---|
| Cobertura da evidência | `assertTrue(outcomes.any { earned == 0 && !pendente })` — sem questão que renda zero sem ser pendência, a asserção de cobertura não mede nada |
| Constraints no catálogo | `assertTrue(esperadas.isNotEmpty())` — um `regclass` que resolvesse para nada devolveria conjunto vazio, e uma lista esperada vazia passaria calada |
| Isolamento por organização | O dono vê **1** antes de o forasteiro ver **0** — sem isso, "o forasteiro vê zero" passa num banco vazio |
| Fila em repouso | Grava, confere que gravou, apaga, confere que esvaziou — "a tabela está vazia" passa numa base cujo `guardar` está quebrado |
| Fila vazia | `assertEquals(0, tentativas)` — fila vazia não pode inventar chamada |

---

## 4. A âncora dos artefatos (P3)

A soma dos relatórios desta fatia foi feita **filtrando por timestamp**, e não somando os XML do
diretório. A primeira contagem deu **1417** testes; a correta é **1411**. A diferença é um relatório
de `testReleaseUnitTest` com timestamp de **2026-08-15**, de uma task que não está no grafo deste
build. É o mesmo defeito que `60ba7bd` registra, e ele apareceu sozinho aqui.

**O comando cheio do CI, e não um filtrado (P5).** O `ci.yml` roda `./gradlew build` e
`./gradlew :apps:android:connectedDebugAndroidTest`, os dois sem filtro de classe. Os dois foram
rodados com `--rerun-tasks`, nesta sessão:

| Comando | Resultado | `timestamp` do relatório |
|---|---|---|
| `./gradlew build --rerun-tasks` | 176 tasks executadas, **1411 testes, 0 falhas** | janela `2026-09-17T14:3x` |
| `./gradlew :apps:android:connectedDebugAndroidTest --rerun-tasks` | **68 testes, 0 falhas** | `2026-09-17T14:37:24` |

O emulador é o `platos-atd34`, API 34, o mesmo alvo `aosp_atd` que o CI usa.

### 4.1 Bisectabilidade, e três jeitos de o instrumento mentir

A cadeia inteira foi replayada com `git rebase --force-rebase --exec './gradlew build'`, para que cada
commit seja verificável isoladamente e não só a ponta. Resultado: **7 commits, 7 verdes, nenhum
quebrado**, e `git diff` entre a árvore de antes e a de depois **vazio** — o replay não mudou uma
linha.

Chegar a esse número exigiu três tentativas, e as três primeiras falhas foram do **instrumento**, não
do código. Ficam escritas porque cada uma produziria um verde sobre coisa nenhuma:

1. **`tail -50` engoliu a evidência.** O comando terminava com um pipe que guardava só as últimas 50
   linhas, e as sete execuções viraram o rabo de uma só. O rebase passou; o registro de qual commit
   passou, não existia.
2. **`Successfully rebased` sobre zero replay.** Sem `--force-rebase`, o git viu que a branch já
   estava sobre `main`, fez fast-forward, rodou **um** `exec` de 5 segundos com 170 de 176 tasks
   `UP-TO-DATE`, e imprimiu sucesso. O único sinal que denunciou foi o **hash inalterado**: replay de
   verdade recria commits. É o mesmo defeito de `exit 0` do Gradle que a fatia 1 registrou, chegando
   por outra porta.
3. **A contagem de marcadores incluiu o próprio instrumento.** `grep -c '===COMMIT-OK'` devolveu 14 e
   `'===COMMIT-QUEBRADO'` devolveu 7 — porque o git **imprime o comando do `exec`** antes de rodá-lo, e
   aquele comando contém os dois literais. Sete linhas do medidor entraram na contagem do medido.
   Ancorar em `^===` deu os números certos: 7, 0 e 2.

E uma leitura errada que também fica dita (P7): a primeira falha do commit 1 foi chamada de
"instabilidade aleatória" depois de **um** build manual que passou. Ela reproduziu em 3 de 3 rebases.
O build manual passou porque o classloader já estava quente — a explicação está em 6.6.

---

## 5. O que ainda não foi verificado

### 5.1 O fluxo de ponta a ponta — **fechado em 2026-09-18, e o que ele custou**

*A redação anterior desta seção dizia "nenhum elo deste fluxo foi observado no destino", e listava
três lacunas. Ela fica registrada em vez de apagada (P7), porque **as três eram reais e duas delas
estavam quebradas** — o que a conferência encontrou justifica o registro melhor do que qualquer
argumento a favor dele.*

A conferência em aparelho real — Xiaomi `2511FPC34G`, Android 16, folhas impressas das provas
`prova-referencia-slice-1` e `slice-2` — encontrou **dois defeitos mergeados**, e nenhum deles
aparecia em 1479 testes verdes.

**Defeito 1: Room acessado no fio principal, em dois pontos.** `ScanActivity.gravar` e
`DeviceSession.sair` chamavam o banco do fio principal, e o Room recusa isso por padrão. Cinco
`IllegalStateException` em aparelho: quatro ao tocar em sair — **sair nunca funcionou** — e uma ao
escanear folha válida. Nenhum resultado era gravado.

*Por que nenhum teste pegou, e a resposta é desconfortável:* `OutboxEmRepousoInstrumentedTest` abria
o banco com `allowMainThreadQueries()`, com um comentário dizendo que era "só neste teste". **O
oráculo foi afrouxado exatamente na trava que a produção impõe**, e então aprovou o que a produção
recusa. E a KDoc de `gravar` afirmava que a escrita síncrona no fio principal era *deliberada* — uma
propriedade do Room que nunca foi medida, escrita como decisão (P6).

**Defeito 2: nada agendava o envio fora do escaneamento.** `grep` por `EnvioDeResultadosWorker.agendar`
dava **uma** ocorrência, em `ScanActivity`. Mas a spec exige que o pendente preservado suba "quando um
membro dela abrir sessão no aparelho" — requisito **sem implementação**. A consequência composta era
pior: como o worker devolve `success` quando o servidor recusa, um pendente que falhasse não tinha
caminho de volta até alguém escanear outra folha.

**A guarda que faltava, e que foi vista falhar.** `GravacaoNoFioPrincipalInstrumentedTest` chama o
caminho da câmera **do fio principal**, com o banco aberto como a produção o abre. Sob a mutação que
restaura a gravação síncrona, 2 dos 3 cenários caem; o terceiro — que afirma que o Room ainda recusa
leitura no fio principal — fica de pé, porque mede o Room e não a função.

*E a primeira tentativa de aplicar essa mutação não aplicou nada*: o `replace` não casou por um
espaço na assinatura, falhou em silêncio, e o teste rodou contra o código correto. O verde foi lido
como se significasse algo. A mutação passou a entrar com verificação de que entrou.

**Os elos, observados onde cada um termina (P26):**

| Elo | Observado em | Evidência |
|---|---|---|
| Recusa da folha adversarial | `databases/` no aparelho | vazio; nenhum `outbox.db` criado |
| Captura offline | `outbox.db` puxado por `exec-out` | 1 linha, `student_token` nulo, 40 observações |
| Morte de processo | PID | 30743 → vazio; linha intacta |
| Envio | `WM-WorkerWrapper` | `60c6af82` SUCCESS em **3,85 s**; a execução com fila vazia levou 0,06 s |
| Gravação no servidor | `grading_result` em produção | `capture_id = d671e626-…`, `revision` 1, `points` 0 de 40 |
| Evidência por questão | `answer_observation` | 40 linhas, soma 0, um único `answer_kind` |

**A âncora do modelo offline:** `captured_at` 21:26:20 UTC contra `created_at` 22:32:06 UTC — **1h06**
entre apurar sem rede e gravar no servidor.

### 5.1.1 Três elos de implantação que ninguém observa automaticamente

A conferência esbarrou em três paredes antes de chegar ao aplicativo, e as três são de operação:

1. **A API implantada estava em `sha-59b9554`** — o merge da PR #37, sem a rota de roster e sem a de
   resultados. O roster dava 404, o gate barrava, e `/health` respondia 200 o tempo todo. A imagem
   `sha-fe9909a` estava publicada no registro desde as 18:01Z; publicar não é implantar, e é o
   incidente que P2 já cita por `04d2f30`. **Foi a decisão de `/health` declarar o build — tomada
   justamente por causa daquele incidente — que transformou o diagnóstico numa consulta de trinta
   segundos.**
2. **A migration nunca foi aplicada em produção.** Nenhum workflow a aplica; o CI só a roda num
   Postgres efêmero para gerar as classes jOOQ. Com o código novo servindo e o schema antigo, o push
   deu **HTTP 500**.
3. **O worker era mudo.** "Rodou e devolveu sucesso" não distinguia credencial ausente de 401, 404 e
   500. Só depois de o resumo ir para o `output` do `WorkSpec` é que o 500 apareceu — e com ele a
   causa. *Essa observabilidade ainda tem furo: trabalho único substitui o `WorkSpec` anterior, então
   o diagnóstico da execução bem-sucedida foi sobrescrito pelo agendamento seguinte, e a prova do
   envio teve de vir do logcat e do servidor.*

### 5.1.2 O que continua sem verificação

- **O segundo membro da organização.** Quem reabriu a sessão foi o mesmo usuário. O escopo por
  organização está verificado em JVM e em aparelho, e o caminho de código é o mesmo com outra
  credencial — mas isso é inferência, e não medição. **Dono:** mantenedor. **Fatia-limite:** a
  primeira que tratar aparelho compartilhado entre professores.

  **Fechado pela metade em 2026-09-18, pela mudança `envio-distingue-recusa-transitoria`.** A metade do
  aparelho deixou de ser inferência: `SegundoMembroInstrumentedTest` grava um pendente sob a credencial
  de um usuário, chama o caminho de saída, guarda a credencial de outro e afirma **qual** credencial
  saiu no cabeçalho. Sob a mutação do cache de processo, os dois cenários caem com
  `expected:<[Bearer …-b]> but was:<[Bearer …-a]>`. A metade do servidor continua **simulada** por
  `MockEngine`, e as duas contas reais contra produção continuam item com o mesmo dono e a mesma
  fatia-limite. Ver `docs/cobertura-envio-distingue-recusa-transitoria.md` §5.1.
- **`Result.success` em recusa do servidor.** O worker não distingue 4xx de 5xx: um 500 transitório é
  tratado como recusa definitiva, e só o agendamento seguinte tenta de novo. Foi o que aconteceu com
  o 500 da migration ausente. **Dono:** esta base. **Fatia-limite:** a primeira que tiver retentativa
  com política, ou o primeiro relato de resultado que demorou a subir.

  **Fechado em 2026-09-18, pela mudança `envio-distingue-recusa-transitoria`.** Esta é a fatia que o
  item nomeava. 5xx passou a contar separado de 4xx, e só o transitório pede nova tentativa ao
  `WorkManager`; o pendente continua intacto nos dois casos. O que **não** foi medido é o
  `Result.retry()` chegando ao `WorkSpec` — a ligação entre a decisão e o reagendamento é linha de
  código lida, e não comportamento observado. Ver
  `docs/cobertura-envio-distingue-recusa-transitoria.md` §5.4.

### 5.2 Duas folhas avulsas seguidas são uma captura só

`ScanSession` identifica "folha nova" pelo **payload** do QR. Folha avulsa tem token vazio, então duas
avulsas diferentes apresentadas em sequência têm payload idêntico e a segunda **não** gera captura
nova — ela é lida como a primeira ainda no quadro.

Não é defeito novo desta fatia: a sessão é de uma folha por vez, e lote é a 3d. E há saída pelo
caminho normal — `resume()` limpa a marca, e voltar a procurar entre as duas folhas as separa.
**Fica escrito porque não é óbvio para quem for escanear um maço de avulsas.** **Dono:** esta base.
**Fatia-limite:** a do lote (3d), que é quando escanear em sequência vira o caso principal.

### 5.3 A desinstalação continua sendo garantia herdada

O Android apaga o `filesDir` na desinstalação, e a base Room vai com ele. Isso é garantia da
plataforma, não do aplicativo, e **nenhum teste desta base a exerce**. Fica dita como **herdada**, e
não como verificada — a mesma redação que a `slice-4b-roster-no-aparelho` usou para o roster.

### 5.4 A corrida de dois envios simultâneos do mesmo `capture_id`

`ResultQueries.record` lê antes de gravar, e essa leitura não é atômica com a gravação. Dois envios
concorrentes do mesmo `capture_id` terminam na constraint `grading_result_captura_unica`, a transação
inteira volta atrás, e o pendente continua no aparelho para ser reenviado — o desfecho é **correto**,
mas ele é garantido pela constraint e não pelo código, e **não há teste que o exercite**.

Não é mitigado, é conhecido. O envio é serial por construção (um trabalho de fila por vez), e por isso
a corrida não tem como acontecer no caminho que existe hoje. **Dono:** esta base. **Fatia-limite:** a
primeira que fizer envio concorrente — lote paralelo, ou mais de um aparelho empurrando a mesma prova.

---

## 6. O que esta fatia deliberadamente não fez, com dono e fatia-limite (P19, P20)

1. **A retenção executável da classe B** — anonimização após o prazo e pedido de eliminação. Entrou no
   §16 como linha própria de ponto de não-retorno nesta fatia. **Dono:** mantenedor para o expurgo e a
   anonimização; jurídico externo para o pedido de eliminação alcançar fato já gravado.
2. **A política §10.8 diverge do comportamento.** Sair e a revogação preservam o pendente; a segunda
   frase do item 10.8 diz que o encerramento de sessão elimina a base local. Registrado no §16, com o
   `[30]` entre colchetes passando a ser o único limite de um pendente sem entregador. **Dono:**
   jurídico externo.
3. **`student`, `student_alias` e `exam_assignment`** não foram criados. O fato nasce chaveado por
   `(exam_id, student_token)`, e a razão está no ADR-0003, na atualização de 2026-09-17.
4. **`assessment_fact`, `capture_session`, `sync_cursor` e o modo degradado** ficaram fora. O insumo do
   primeiro está preservado em `answer_observation`; os outros três não têm consumidor neste fluxo.
5. **`:apps:api:generateJooq` falha na primeira execução após o Gradle reconfigurar.** Achado ao
   verificar bisectabilidade, e **fora do escopo funcional desta fatia** (P19), então fica como item e
   não como conserto. O erro é sempre o mesmo: `java.sql.SQLException: No suitable driver found for
   jdbc:postgresql://...`. Dos 7 commits replayados, os **2** que falharam na primeira tentativa são
   exatamente os dois cujo build vem depois de uma reconfiguração — o primeiro do rebase, com daemons
   parados e `apps/android/build` apagado, e o que altera `gradle/libs.versions.toml`, porque mudança
   de catálogo invalida a configuração. Os outros 5 passaram de primeira, inclusive o maior da fatia.
   A retentativa, com o classloader quente, passou nas duas vezes. **É correlação de 7 pontos, e não
   causa medida:** o classloader não foi instrumentado, e afirmar `ServiceLoader` aqui seria suposição
   apresentada como medição (P6). **Atualizado em 2026-09-18, e a correlacao enfraqueceu:** um segundo `rebase --force-rebase --exec`,
   sobre os quatro commits do conserto, passou **4 de 4 sem nenhuma retentativa** — inclusive o
   primeiro, que e build apos reconfiguracao e era onde a previsao dizia que cairia. Entao "falha
   apos reconfiguracao" descreve os casos observados mas **nao os prediz**: sao 2 de 7 num dia e 0 de
   4 no outro. O que continua firme e so o fato bruto — o alvo falha de forma intermitente, sempre
   com o mesmo erro. Importa porque o `ci.yml` roda `./gradlew :apps:api:generateJooq`
   como passo próprio na linha 43, num runner novo e sem retentativa — e um vermelho desses tem cara
   de regressão sem ser (P15). O CI está verde hoje, então ou o caso é específico de Windows, ou a
   ordem dos passos lá o evita; nenhuma das duas foi medida. **Dono:** mantenedor. **Fatia-limite:** a
   próxima que tocar o build da API, ou o primeiro vermelho de CI que custe investigação.
6. **A KDoc de `VisoesEmArquivo` diz "Room continua sendo da 4b".** Agora Room existe, e a frase ficou
   imprecisa sobre o nome — o gatilho que ela cita, o outbox, está correto. `docs/cobertura-slice-4b-roster-no-aparelho.md:281`
   dava a esta fatia como fatia-limite dela. **Não foi corrigida**, porque é refatoração fora do escopo
   funcional desta mudança (P19) e a correção pertence a um commit de texto. **Dono:** esta base.
   **Fatia-limite:** a próxima que tocar `VisoesEmArquivo`.
