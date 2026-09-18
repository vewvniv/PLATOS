## Context

Ver `proposal.md` — Why. O estado que importa para o desenho:

- `Retorno.Recusou(status)` já carrega o status cru (`net/Retorno.kt`), e o comentário dele diz que
  interpretar o status é da camada de cima. Não falta informação; falta consumidor.
- `EnvioDeResultados` é a decisão de enviar-e-apagar sem Android, exercitada na JVM.
  `EnvioDeResultadosWorker` é agendamento e fiação.
- O worker monta a fiação inteira dentro de `doWork()` — `clienteHttp()`, `ApiPlatos`,
  `ResultadosEmRoom.abrir` — e por isso **não há costura** por onde um teste observe qual credencial
  saiu no cabeçalho. `clienteHttp(engine)` já aceita `MockEngine`; quem não aceita nada é o worker.
- `SessaoGuardadaAndroid.credencial()` é lida **a cada chamada**, de propósito: o worker passa
  `credencial = { guardada.credencial() }`. É essa propriedade que o item do segundo membro afirma
  sem medir.

## Goals / Non-Goals

**Goals:**

- Uma recusa de servidor que possa ser aceita depois ganha nova tentativa sem depender de gesto
  humano.
- O item do segundo membro sai de "o caminho de código é o mesmo com outra credencial" (inferência)
  para uma medição que atravessa credencial guardada → cabeçalho enviado → fila drenada.

**Non-Goals:**

- Política de backoff própria. O `WorkManager` já tem uma, e substituí-la seria tecnologia sem
  consumidor.
- Teto de tentativas (ver `proposal.md`).
- Classificar 408 e 429 como transitórios. São 4xx que a semântica HTTP descreve como repetíveis, mas
  **nenhum dos dois foi observado nesta base** — a rota não tem limitador nem tempo limite próprio.
  Entram quando houver o primeiro. **Dono:** esta base. **Fatia-limite:** a primeira que puser
  limitador de taxa na API, ou o primeiro 408/429 observado em aparelho.
- A conferência com duas contas reais contra o servidor de produção. Continua item com dono
  (mantenedor) — ver `tasks.md`, tarefa 5.

## Decisions

### 1. A classificação mora em `EnvioDeResultados`, e não em `Retorno`

`Retorno` é o vocabulário de falha do aplicativo inteiro (decisão 8 da 4a-zero). "5xx merece outra
tentativa" é política **do outbox**: a tela de entrada não quer tentar de novo sozinha um 500 de
login, e a consulta de organizações tem outra resposta certa. Pôr a regra em `Retorno` faria a
entrada e a consulta herdarem-na sem pedir.

*Alternativa considerada:* um `Retorno.FalhaDoServidor` separado de `Retorno.Recusou`. Rejeitada:
obriga todos os consumidores atuais a tratar um caso novo que só um deles usa, e apaga o status cru
que a mensagem de diagnóstico usa.

### 2. `ResumoDoEnvio` ganha um quarto número, em vez de um booleano

Os três números existem porque significam coisas diferentes para quem reagenda — é o que a KDoc já
diz. `transitorios` é o quarto pela mesma razão: `semRede` é o caminho, `transitorios` é o servidor
caído, `recusados` é o servidor decidindo. Um booleano "vale tentar de novo" colapsaria os dois
primeiros e o diagnóstico voltaria a não distinguir.

`pendentesRestantes` passa a somar os três que não confirmaram.

### 3. A decisão de `retry` vira função pura, e sai do corpo de `doWork`

`retry` quando `semRede > 0 || transitorios > 0`; `success` caso contrário. Como função pura sobre
`ResumoDoEnvio`, ela é exercitada na JVM contra os quatro casos, sem emulador e sem `WorkManager`.
Dentro de `doWork` ela só é alcançável por teste instrumentado, e ficaria coberta por inferência.

### 4. A fiação do worker vira uma costura injetável

`doWork()` passa a delegar a uma função interna que recebe o `HttpClientEngine`. Produção passa o
padrão (OkHttp); o teste instrumentado passa `MockEngine`.

**Por que isto não é refatoração oportunista (P19):** sem a costura, a tarefa do segundo membro não
tem como ser feita — a única alternativa seria afirmar a propriedade compondo `ApiPlatosTest` com
`SessaoGuardadaTest`, que é exatamente o que P16 proíbe. A costura é o menor caminho que a tarefa
exige, e não uma melhoria de passagem.

**O que a costura NÃO faz:** ela não move a decisão de enviar-e-apagar, que continua em
`EnvioDeResultados`. O que se torna injetável é o transporte, e só ele.

**Corrigido em 2026-09-18, depois de a mutação desmentir a frase acima (P7).** Este parágrafo dizia
que "a credencial continua sendo lida a cada chamada, e **é essa leitura por chamada** que faz o
pendente de um usuário subir com a credencial de outro". Está errado. A mutação que capturava a
credencial uma vez por passada, em vez de a cada chamada, **não derrubou teste nenhum** — e não
derrubaria mesmo, porque a credencial não muda no meio de uma passada.

O que faz o requisito funcionar é outra coisa: a credencial é lida do **armazenamento compartilhado no
momento da passada**, e não fica presa ao pendente nem a quem o produziu. A mutação que isola isso é a
do **cache de processo** — o aplicativo fixa a credencial na primeira passada e não percebe que a
sessão mudou. Sob ela os dois cenários de credencial caem, com
`expected:<[Bearer …-b]> but was:<[Bearer …-a]>`, e a guarda de vacuidade fica de pé.

### 5. O teste do segundo membro mede o cabeçalho, e não o estado do aparelho

O oráculo é o `Authorization` que o `MockEngine` recebe — um valor produzido pelo caminho inteiro e
lido do lado de fora dele. Afirmar "a credencial guardada agora é a de B" mediria o armazenamento, que
já tem teste próprio, e não o envio.

A fixture usa **duas credenciais distinguíveis**, e a asserção confere **qual** delas chegou. Conferir
só que "chegou alguma" é indistinguível entre o caminho certo e o que guardou a credencial do autor
junto do pendente — que é precisamente a hipótese sob teste (§3 do `rigorous.md`: a asserção confere o
motivo, não só que houve).

### 6. O pendente atravessa um `sair` de verdade no meio do teste

Entre a credencial de A e a de B o teste chama o caminho de saída, e não apenas sobrescreve o token.
Sem isso, o cenário mediria "duas credenciais em sequência" e não "outro membro, depois de o primeiro
sair" — e é o segundo que o requisito descreve.

## Risks / Trade-offs

- **`retry` contra servidor permanentemente em 500 vira tentativa perpétua** → O backoff exponencial
  do `WorkManager` chega a intervalos de horas, e o pendente não se degrada esperando: ele é
  append-only e o servidor é idempotente por `capture_id`. Tentar para sempre é o desfecho certo para
  uma fila cujo conteúdo não existe em nenhum outro lugar.
- **A costura de transporte pode ser usada para injetar mais coisa depois** → Ela recebe o engine, e
  não um `ApiPlatos` pronto: o que o teste pode trocar é o transporte, e a fiação continua sendo a de
  produção.
- **`MockEngine` não tem tempo limite de soquete** — está registrado no KDoc de `clienteHttp`, e a
  fatia 4a pagou por isso. O teste do segundo membro **não** afirma nada sobre tempo limite; ele mede
  cabeçalho e drenagem.
- **A classificação por faixa de status pode discordar do servidor real** → A rota devolve 500 por
  exceção não tratada e 4xx por decisão; foi 500 o que a conferência observou. Se algum dia a rota
  responder 4xx para falha própria, a classificação erra — e erra para o lado de não tentar de novo,
  que preserva o pendente.

## Migration Plan

Nenhuma migração. Não há schema, contrato de rota nem formato persistido envolvido: `ResumoDoEnvio` é
tipo em memória e o `output` do `WorkSpec` é diagnóstico, não estado. Reverter é reverter os commits.
