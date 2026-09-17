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

---

## 5. O que ainda não foi verificado

### 5.1 O fluxo de ponta a ponta — **nenhum elo observado no destino**

As tarefas 7.3 e 8.5 estão **desmarcadas**, e o que falta é papel: a câmera do emulador não enxerga
folha impressa, e o caminho que elas pedem começa numa folha real diante de uma câmera real.

O que isso deixa **sem** verificação, dito pelo nome:

- **O `ScanActivity` gravando de fato.** A decisão de quando gravar está coberta em JVM
  (`ScanSessionTest`, cinco cenários de captura nova) e a gravação está coberta em aparelho
  (`OutboxEmRepousoInstrumentedTest`), mas a **costura** entre as duas — o `if (apuracao != null)
  gravar(apuracao)` dentro do laço da câmera — não foi exercitada por teste nenhum. É o tipo de
  defeito que P16 descreve: as duas camadas vizinhas verdes não afirmam nada sobre a junta.
- **O `WorkManager` disparando.** `EnvioDeResultados` está coberto em JVM contra uma função de envio
  de mentira, e `EnvioDeResultadosWorker` — o agendamento, a `Constraints` de rede, o `KEEP`, a
  credencial lida do `SessaoGuardadaAndroid` — **não tem teste nenhum**. Não é mitigado, é
  **conhecido** (P8).
- **A rota implantada gravando.** `ResultRouteTest` exercita a rota contra Postgres real via
  Testcontainers, o que é forte; mas a API **implantada** nunca recebeu um resultado. O `/health` dela
  respondeu 200 nesta sessão, e isso não é alcançar o banco — P26 e o incidente `544dafe` registram
  exatamente essa confusão.

**Dono:** mantenedor. **Fatia-limite:** antes do primeiro piloto com turma real — que é quando um
resultado que não sobe deixa de ser bug e passa a ser nota perdida.

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
5. **A KDoc de `VisoesEmArquivo` diz "Room continua sendo da 4b".** Agora Room existe, e a frase ficou
   imprecisa sobre o nome — o gatilho que ela cita, o outbox, está correto. `docs/cobertura-slice-4b-roster-no-aparelho.md:281`
   dava a esta fatia como fatia-limite dela. **Não foi corrigida**, porque é refatoração fora do escopo
   funcional desta mudança (P19) e a correção pertence a um commit de texto. **Dono:** esta base.
   **Fatia-limite:** a próxima que tocar `VisoesEmArquivo`.
