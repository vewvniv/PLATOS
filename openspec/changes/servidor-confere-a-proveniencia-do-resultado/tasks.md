## 1. Commit 1 — spec antes de código

A numeração é a ordem dos commits (regra 0.3 do plano; `CLAUDE.md` regra 1, P25).

- [x] 1.1 O requisito "O resultado durável diz de qual folha, de qual pacote e de qual aluno ele é"
      ganha a contraparte do servidor no delta de `result-sync`: o servidor **SHALL** recusar
      resultado cujo `package_hash` não seja o `content_hash` do pacote publicado daquela prova, e
      cujo `variant_id` o pacote não declare; a recusa **SHALL** distinguir-se de ausência, e
      **SHALL NOT** gravar nada. **Dois cenários novos, um por campo.** Verificar com
      `openspec validate servidor-confere-a-proveniencia-do-resultado --strict` e conferindo que o
      bloco `MODIFIED` carrega o requisito **inteiro**, com os quatro cenários que já existiam.
- [x] 1.2 **Nenhuma linha de código neste commit.** Verificar no `git diff --stat` que ele toca
      apenas `openspec/changes/.../specs/result-sync/spec.md`, e que `./gradlew build` continua
      verde — a spec ainda descreve comportamento que o código não tem, e é essa a ordem.

      **O commit carrega os quatro artefatos de planejamento, e não só o delta de spec.** A tarefa
      dizia "apenas `specs/result-sync/spec.md`"; a convenção do repositório é o commit
      `propose(<nome>)` com `proposal.md`, `design.md`, `tasks.md` e o delta juntos — foi assim na
      `params-hash` (`64678ee`). O que a tarefa protege — **nenhuma linha de código** — fica
      preservado, e o desvio fica escrito em vez de calado (P7).

      `./gradlew build` verde, 2026-09-18T23:15:41Z–23:15:50Z, **176 tarefas, 170 `UP-TO-DATE`**.
      Isso confirma que a árvore não regrediu e **não** é evidência de que a suíte da API rodou nesta
      sessão — ela roda de verdade na 2.5, contra Docker (P8).

## 2. Commit 2 — a conferência

- [x] 2.1 `findPublishedExamId` passa a devolver também o `content_hash` — a consulta **já faz
      `join` em `EXAM_PACKAGE`**, então é uma coluna a mais, e **não** uma consulta a mais
      (`design.md` decisão 1). O `null` continua cobrindo as mesmas três situações de ausência, e
      continua indistinguível entre elas. Verificar com `./gradlew :apps:api:compileKotlin`.
- [x] 2.2 A comparação de `package_hash` acontece **dentro da mesma transação de `asUser`, antes de
      qualquer `insert`** (`design.md` decisão 2, R2: o fato é append-only e não tem conserto).
- [x] 2.3 A validação do `variant_id` sai do **pacote publicado**: o `content` já está a um `select`
      de distância e `ExamPackage.variants` o declara. **Não** se escreve uma segunda lista de
      variantes (`design.md` decisão 3). Verificar por leitura do diff que nenhuma coluna, tabela ou
      constante nova de variantes foi introduzida.
- [x] 2.4 O status é **400**, e não 404: ausência continua sendo ausência (prova inexistente, prova
      sem pacote, organização alheia), e um corpo incoerente é decisão do servidor sobre o pedido —
      que é exatamente a faixa que o aparelho já classifica como **definitiva** e não retentável
      (`Retorno.Recusou`, `eTransitoria()`). Um 5xx aqui faria o aparelho repetir para sempre um
      envio que nunca será aceito. A mensagem diz **qual** dos dois campos não fecha. Verificar pelos
      cenários da tarefa 2.5, e que os cenários de 404 já existentes continuam em 404.
- [x] 2.5 Dois cenários novos em `ResultRouteTest`, um por campo, **com a guarda de vacuidade
      (P13), que é a forma do dado** (`design.md` decisão 9): o `package_hash` falso precisa ser
      **64 hexadecimais bem formados** e o `variant_id` do cenário A precisa ser **válido** — senão a
      recusa pode vir do `check` da coluna ou da outra trava, e o cenário mede a camada vizinha. E a
      contagem de linhas se faz **no banco**, nunca no corpo da resposta: uma rota que responda 400 e
      grave assim mesmo passa em qualquer asserção sobre o corpo. Verificar com
      `./gradlew :apps:api:test` verde, **com Docker no ar**.

      **O `CONTEUDO` de `ResultRouteTest` teve de virar um `ExamPackage` de verdade, e isso alcança
      todos os cenários já existentes do arquivo.** Não é escopo acrescentado: é a consequência
      direta da decisão 3 — a trava de `variant_id` **lê** o `content`, e o esboço
      `{"meta":{"exam_id":"prova-r"},"items":[],"answer_key":[]}` não decodifica. Ele bastava
      enquanto nada no servidor lesse o pacote. Continua sendo literal escrito à mão, pela razão que
      o cabeçalho do arquivo já dá.

      `./gradlew :apps:api:test --tests "com.platos.api.http.ResultRouteTest"`,
      2026-09-18T23:20:53Z–23:21:11Z — **11 de 11 verdes**, os dois novos entre eles.
      `./gradlew :apps:api:test`, 2026-09-18T23:21:16Z–23:21:35Z — **165 testes, 0 falhas, 0 erros,
      0 pulados**, contados nos XML de `build/test-results/test/`, e não no "BUILD SUCCESSFUL".

## 3. Ver falhar — duas mutações, conjuntos disjuntos

A tarefa 4.5 da fatia do outbox já estabeleceu o padrão: **uma mutação que derrube as duas travas não
diz qual segurou.**

- [ ] 3.1 **Mutação A** — neutralizar a comparação de `package_hash`. Rodar a suíte sob a mutação e
      registrar **quais** cenários caem, contra esta tabela:

      | Cenário | Deve cair? |
      |---|---|
      | resultado com `package_hash` de outro pacote é recusado | **sim** |
      | resultado com `variant_id` que o pacote não declara é recusado | **não** |
      | reenvio da mesma captura não cria registro novo | **não** |
      | recaptura grava revisão nova | **não** |
      | duas folhas avulsas não colidem | **não** |

- [ ] 3.2 **Mutação B** — neutralizar a comparação de `variant_id`: **o espelho exato** da tabela da
      3.1. Rodar a suíte sob a mutação e registrar quais cenários caem.
- [ ] 3.3 **Se os conjuntos não forem disjuntos, pare.** É a regra de parada (regra 0.5 do plano,
      `design.md` decisão 10): conjunto real diferente do previsto — mais, menos, ou outros — não se
      conserta, não se afrouxa a asserção e não se ajusta a previsão em silêncio. Escrever o conjunto
      real **ao lado** do previsto e dizer o que ele significa (P7, P12, P14).
- [ ] 3.4 Verificar que cada mutação **entrou** antes de ler o resultado, e reverter rodando (P10,
      regra 0.7 do plano): `grep -rn "MUTACAO"` fora de `build/` em `0`, e a suíte rodada **depois**
      da reversão, com `timestamp`.

## 4. O registro

Regra 0.8 do plano: nenhuma etapa fecha com "passou".

- [ ] 4.1 `docs/cobertura-servidor-confere-a-proveniencia-do-resultado.md` com, no mínimo: os dois
      conjuntos reais ao lado dos previstos; o comando cheio de cada execução (P5) e o `timestamp` de
      cada uma (P2, P3); como foi visto falhar (P9); e o que ficou sem verificação (P8). Verificar
      que a seção "o que ainda não foi verificado" é honesta e não vazia por omissão.
- [ ] 4.2 Uma seção da cobertura dizendo **quantas linhas de `grading_result` existem hoje com
      proveniência não conferida** e que elas permanecem como estão — `grading_result` é append-only
      por gatilho, e esta mudança não as toca nem as reclassifica (`design.md`, *Migration Plan*).
      Verificar que a seção diz o que acontece com elas, e não só que elas existem.
- [ ] 4.3 No `docs/auditoria-2026-09-18-antes-da-fatia-5.md`, o achado **2.2** deixa de estar aberto,
      **sem apagar o texto antigo** (P7), com o ponteiro para esta mudança.
- [ ] 4.4 Verificação final: `./gradlew build` com `timestamp` e com Docker no ar, a suíte da API
      verde **depois** da reversão das duas mutações, `grep -rn "MUTACAO"` fora de `build/` em `0`, e
      `openspec validate servidor-confere-a-proveniencia-do-resultado --strict`.
