# Cobertura — `envio-distingue-recusa-transitoria`

O que este documento é: **como cada verificação crítica foi vista falhar**, e não que ela passa.
`rigorous.md` §8 exige isso.

Esta mudança paga dois itens de `docs/cobertura-slice-4b-outbox-de-resultado.md` §5.1.2: o worker que
não distinguia 4xx de 5xx, e o segundo membro da organização. O primeiro fecha; o segundo fecha **só
no que o aparelho decide**, e o que resta está em §5.1.

---

## 1. As mutações, e o conjunto que cada uma derrubou

Três mutações, todas revertidas e cada reversão conferida **rodando** (P10). Duas das três
contradisseram a previsão escrita na tarefa, e é isso que elas tinham de mais útil.

### 1.1 A classificação passou a chamar 4xx de transitório

**Mutação:** `status >= 500` vira `status >= 400` em `EnvioDeResultados.eTransitoria`.

**Por que esta mutação, e não outra:** ela isola a **classificação**. Uma mutação que mexesse no
expurgo derrubaria os cenários de preservação junto, e o vermelho não diria qual das duas camadas
segurou.

**A previsão da tarefa 1.4 estava errada, e fica dita (P7).** Ela dizia que
`recusa por vinculo revogado nao apaga o pendente` **não** cairia. Caiu — porque aquele cenário afirma
`recusados == 1`, que é asserção de classificação, e não só de preservação.

| Cenário | Caiu? |
|---|---|
| `decisao do servidor conta como definitiva e o pendente fica` | **sim** |
| `os tres desfechos convivem na mesma passada` | **sim** |
| `um recusado nao bloqueia o resto da fila` | **sim** |
| `recusa por vinculo revogado nao apaga o pendente` | **sim** |
| `confirmado sai da fila` | não |
| `sem rede o pendente fica intacto` | não |
| `recusa do servidor nao apaga o pendente` (500) | não |
| `falha do servidor conta como transitoria e o pendente fica` (503) | não |
| `a fila de uma organizacao nao leva a de outra` | não |
| `fila vazia nao inventa confirmacao` | não |

**O que prova que a mutação isolou a camada não é quantos caíram, é a mensagem de cada vermelho.** As
quatro caíram em asserção de **contagem** — `expected: <1> but was: <0>`, e `<1>` contra `<2>`. Nenhuma
caiu numa asserção de estado da fila: as que dizem `"a linha foi mexida"` e `"so o confirmado sai da
fila"` ficaram verdes. Classificação e preservação são camadas distinguíveis, e a mutação tocou só a
primeira.

**Reversão:** `grep -rl "MUTACAO" apps/android/src` → `0`; 10 testes, 0 falhas, relatório
`2026-09-18T10:41:17.130Z`.

### 1.2 A decisão de reagendar voltou ao comportamento de hoje

**Mutação:** `valeTentarDeNovo` volta a ser `resumo.semRede > 0`.

Caiu **um**, e só ele: `falha do servidor pede tentativa`. `recusa definitiva nao pede tentativa`,
`sem rede pede tentativa`, `nada pendente nao pede tentativa` e o canário continuaram verdes —
`15 tests completed, 1 failed`.

É o conjunto mínimo possível, e é o certo: a mutação restaura exatamente um comportamento, e exatamente
um cenário o descreve.

**Reversão:** conferida rodando; `assembleDebug` conclui.

### 1.3 A credencial virou cache de processo — e a primeira tentativa não provou nada

**A mutação escrita na tarefa 4.3 não derrubou teste nenhum, e esse é o achado (P7).** Ela mandava
capturar a credencial uma vez **antes da passada**, em vez de a cada chamada. Os três testes passaram.

Não é falha do teste: é que a tarefa — e a decisão 5 do `design.md` — descreviam o mecanismo errado.
Capturar uma vez por passada **é equivalente** a ler por chamada, porque a credencial não muda no meio
de uma passada. O que faz o requisito funcionar é a credencial ser lida do armazenamento compartilhado
**no momento da passada**, sem ficar presa ao pendente nem a quem o produziu.

A mutação que isola isso é a do **cache de processo**: o aplicativo fixa a credencial na primeira
passada e não percebe que a sessão mudou. No `2511FPC34G`, Android 16:

| Cenário | Caiu? | Mensagem |
|---|---|---|
| `o_pendente_de_um_membro_sobe_com_a_credencial_de_outro` | **sim** | `expected:<[Bearer …-b]> but was:<[Bearer …-a]>` |
| `recusa_ao_membro_revogado_preserva_o_pendente_para_o_seguinte` | **sim** | `expected:<[Bearer …-a, Bearer …-b]> but was:<[Bearer …-a, Bearer …-a]>` |
| `fila_vazia_nao_produz_pedido_nenhum` | não | — |

As duas caíram na asserção do **cabeçalho** — o pendente subindo com a credencial de quem escaneou, que
é o defeito que a classe existe para pegar — e nenhuma na de estado da fila. A guarda de vacuidade
sobreviveu, que é o conjunto disjunto exigido.

**Reversão:** `grep -rl "MUTACAO" apps/android/src` → `0`; 3 testes, 0 falhas, no aparelho.

---

## 2. Os oráculos, e por que eles são independentes

- **O cabeçalho `Authorization` que chegou ao transporte**, e não o que está guardado. Afirmar "a
  credencial guardada agora é a de B" mediria o armazenamento — que tem teste próprio — e passaria numa
  implementação que guardasse a credencial do autor junto do pendente. O valor lido é produzido pelo
  caminho inteiro e observado do lado de fora dele.
- **As duas credenciais são distinguíveis, e a asserção diz qual chegou.** "Chegou alguma" é
  indistinguível entre o caminho certo e o defeito — foi exatamente o que a mutação 1.3 mostrou.
- **A contagem da fila é lida do Room**, reabrindo a base, e nunca do retorno da passada. Uma passada
  que respondesse certo e não apagasse passaria em qualquer asserção sobre o `ResumoDoEnvio`.
- **A classificação e a preservação são conferidas separadamente** em cada cenário novo. Uma
  classificação certa com expurgo errado passa na primeira asserção e falha na segunda.

## 3. As guardas de vacuidade (P13)

| Onde | O canário |
|---|---|
| Segundo membro | `assertEquals(1, quantosPendentes())` **antes** da passada — sem a linha, "a fila drenou" passa numa base vazia e o cabeçalho nunca é produzido |
| Costura de transporte | `fila_vazia_nao_produz_pedido_nenhum` — sem ele, os dois cenários de credencial passariam contra uma costura que nunca envia |
| Decisão de reagendar | `transitorio e definitivo deixam o mesmo tanto de pendente` — se `pendentesRestantes` deixasse de contar o transitório, o caso do 503 mediria uma fila vazia e continuaria verde pelo motivo errado |
| 503 e 404 | `assertEquals(503, statusVisto)` e `assertEquals(404, statusVisto)` — o cenário precisa ter exercitado **aquele** status, e não outro |
| 503 contra 404 | os dois existem juntos: sem o 404, "5xx é transitório" passaria numa implementação que chamasse **tudo** de transitório |

## 4. O que a asserção que mudou significa (P12)

O cenário `recusa do servidor nao apaga o pendente` usava `Retorno.Recusou(500)` e afirmava
`recusados == 1`. Ele **tinha** de ficar vermelho: 5xx passou a ser transitório.

Isso não é consertar vermelho enfraquecendo asserção. O que mudou foi o **requisito** — o delta de
`result-sync` desta mudança —, e a asserção que o cenário existe para fazer, a de que **o pendente
fica**, está intacta. A classificação virou `transitorios == 1` mais um `recusados == 0` explícito,
que é mais forte do que era antes.

---

## 5. O que ainda não foi verificado

### 5.1 Duas contas reais contra o servidor de produção

O que `SegundoMembroInstrumentedTest` mede é o **aparelho**: qual credencial sai no cabeçalho quando o
pendente de um membro sobe depois de outro abrir sessão. A outra metade — que o servidor aceita o envio
de B e recusa o de A — é do servidor, está verificada em `:apps:api:test`, e aqui é **simulada** pelo
`MockEngine`.

As duas pontas nunca foram observadas juntas. **Dono:** mantenedor. **Fatia-limite:** a primeira que
tratar aparelho compartilhado entre professores.

Isto é menos do que o item original pedia, e mais do que ele tinha: antes, a afirmação inteira era
inferência a partir de "o caminho de código é o mesmo com outra credencial". Agora a metade do
aparelho é medição, e a do servidor é simulação declarada.

### 5.2 408 e 429 continuam contando como definitivos

A semântica HTTP os descreve como repetíveis, e a classificação desta mudança os põe em `recusados`.
Nenhum dos dois foi observado nesta base: a rota não tem limitador de taxa nem tempo limite próprio, e
faixa escolhida sem caso que a pague é número sem consumidor (P18). **Dono:** esta base.
**Fatia-limite:** a primeira que puser limitador de taxa na API, ou o primeiro 408/429 observado em
aparelho.

O erro, se acontecer, é para o lado seguro: um 429 tratado como definitivo deixa o pendente na fila e
espera o próximo agendamento, em vez de apagá-lo.

### 5.3 Não há teto de tentativas, e isso é escolha

Um servidor permanentemente em 500 produz retentativa indefinida. O backoff exponencial do
`WorkManager` chega a intervalos de horas, e o pendente não se degrada esperando — o fato é append-only
e o servidor é idempotente por `capture_id`.

**Não é mitigado, é decidido:** tentar para sempre é o desfecho certo para uma fila cujo conteúdo não
existe em nenhum outro lugar. Um teto escolhido hoje, sem evidência de pressão, seria número sem
consumidor.

### 5.4 O `Result.retry()` em si não foi observado no `WorkManager`

`valeTentarDeNovo` é exercitado na JVM e `doWork` o usa, mas **nenhum teste observa o `WorkSpec`
mudando para `ENQUEUED` depois de um 500**. A ligação entre a função e o `Result.retry()` é uma linha
de código lida, e não um comportamento medido.

Não é mitigado, é conhecido. **Dono:** esta base. **Fatia-limite:** a primeira que precisar afirmar
quando a retentativa acontece, e não só que ela é pedida.

### 5.5 A base do outbox usada pelo teste é a de produção

`SegundoMembroInstrumentedTest` grava em `outbox.db`, que é a base que o aplicativo usa no aparelho,
porque é essa que a costura abre. O teste a apaga antes e depois. **Quem rodar esta suíte num aparelho
com correções pendentes de verdade as perde.** Fica escrito porque não é óbvio para quem for rodar a
suíte num aparelho de uso real.
