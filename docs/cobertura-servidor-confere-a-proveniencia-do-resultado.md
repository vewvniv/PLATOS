# Cobertura — `servidor-confere-a-proveniencia-do-resultado`

**Achado que fecha:** 2.2 da `docs/auditoria-2026-09-18-antes-da-fatia-5.md`
**Etapa:** 4 de `docs/plano-de-correcao-antes-da-fatia-5.md`
**Base:** `vewvniv/params-hash-no-pacote-publicado` em `2e42bad` (a etapa 3, arquivada)
**Ambiente:** Docker 29.7.2, para os Testcontainers de Postgres. Nada foi instalado nem alterado.

Antes desta mudança, `grading_result.package_hash` e `grading_result.variant_id` eram gravados
exatamente como o aparelho os enviou. Nada os comparava com o pacote publicado da prova. Era o único
ponto do sistema em que um dado entrava no registro imutável sem oráculo.

---

## 1. O que passou a ser conferido, e onde

`findPublishedExamId` já fazia `join` em `EXAM_PACKAGE` — era essa `join` que decidia "publicada".
O que mudou é **quantas colunas ela devolve**: passou de `EXAM.ID` para `EXAM.ID`,
`EXAM_PACKAGE.CONTENT` e `EXAM_PACKAGE.CONTENT_HASH`, na mesma linha e na mesma consulta. Nenhuma
consulta nova foi escrita.

A comparação roda dentro da transação que `Tenancy.asUser` abre, **antes de qualquer `insert`**.
Não é preciosismo: `grading_result` e `answer_observation` têm gatilho que recusa `UPDATE` e `DELETE`
até para o dono da tabela. Gravar e desfazer por rollback seria correto por transação e errado por
desenho.

A lista de variantes sai de `ExamPackage.variants`, do pacote publicado. **Não existe uma segunda
lista** — nem coluna, nem tabela, nem constante.

**O status é 400, e não 404.** Ausência continua indistinguível entre as três situações que sempre
foram uma só: prova inexistente, prova sem pacote, organização alheia. Proveniência que não bate é
decisão do servidor sobre o pedido, e é a faixa que o aparelho já classifica como **definitiva** e
não retentável (`Retorno.Recusou`, `eTransitoria()`). Um 5xx faria o aparelho repetir para sempre um
envio que nunca será aceito.

---

## 2. Como foi visto falhar (P9) — duas mutações, conjuntos disjuntos

A tarefa 4.5 da fatia do outbox estabeleceu o padrão: uma mutação que derrube as duas travas não diz
qual segurou. Foram duas, independentes, cada uma neutralizando **uma** comparação.

**Comando, os três casos:** `./gradlew :apps:api:test`
**Oráculo:** os XML de `apps/api/build/test-results/test/`, e **não** a linha `BUILD SUCCESSFUL` —
o desfecho do Gradle não diz quais cenários caíram.

### Mutação A — a comparação de `package_hash` neutralizada

`2026-09-18T23:22:25Z–23:22:43Z` · **165 cenários, 1 caído**

| Cenário | Previsto | Real |
|---|---|---|
| resultado com `package_hash` de outro pacote é recusado | **sim** | **caiu** |
| resultado com `variant_id` que o pacote não declara é recusado | não | não caiu |
| reenvio da mesma captura não cria registro novo | não | não caiu |
| recaptura grava revisão nova | não | não caiu |
| duas folhas avulsas não colidem | não | não caiu |

Nenhum dos outros 160 cenários da suíte caiu.

### Mutação B — a comparação de `variant_id` neutralizada

`2026-09-18T23:23:03Z–23:23:22Z` · **165 cenários, 1 caído**

| Cenário | Previsto | Real |
|---|---|---|
| resultado com `variant_id` que o pacote não declara é recusado | **sim** | **caiu** |
| resultado com `package_hash` de outro pacote é recusado | não | não caiu |
| reenvio da mesma captura não cria registro novo | não | não caiu |
| recaptura grava revisão nova | não | não caiu |
| duas folhas avulsas não colidem | não | não caiu |

### A regra de parada não disparou, e vale dizer por quê

**Previsto 1 · real 1, nos dois casos, e os conjuntos são disjuntos** — interseção vazia. Cada trava
segura exatamente o que diz segurar, e nenhuma das duas está sendo sustentada pela outra.

**E isso não é sorte: é a guarda de vacuidade (P13), que aqui é a forma do dado.** No cenário A a
variante é **válida**, então a mutação A o deixa passar inteiro. No cenário B o `package_hash` é **o
certo**, então a mutação B o deixa passar inteiro. Fosse o dado do cenário negativo errado nos dois
campos ao mesmo tempo, os dois cairiam sob qualquer uma das mutações, e o conjunto não diria qual
segurou.

A outra metade da guarda é **executada, e não afirmada**: cada cenário negativo termina reenviando o
**mesmo corpo** com o campo corrigido e exigindo **200**. Sem isso, "recusou" poderia significar
qualquer outra coisa sobre aquele corpo.

E o `package_hash` falso não é inventado: é o `sha256` de **outro pacote real**
(`CONTEUDO_DE_OUTRA_PROVA`). Um valor arbitrário poderia ser recusado pelo `check` da coluna, e o
cenário mediria a camada vizinha em vez desta. O teste ainda assim afirma, executando, que ele tem
**64 caracteres**, que todos são **hexadecimais**, e que **não é** o hash desta prova.

### A reversão, conferida rodando (P10)

As duas mutações foram conferidas **no arquivo** antes de a suíte rodar — `grep -rn "MUTACAO"` fora
de `build/` acusou a linha em cada caso, e o `BUILD FAILED` confirma que a compilação as pegou.

Depois da reversão: `grep` em **0**, `git status --short` **vazio**, e `./gradlew :apps:api:test` em
`2026-09-18T23:23:35Z–23:23:53Z` com **165 testes, 0 caídos**.

---

## 3. A contagem é no banco, nunca no corpo da resposta

Os dois cenários novos afirmam `select count(*) from grading_result` **e**
`select count(*) from answer_observation` em zero. É a regra que o cabeçalho de `ResultRouteTest` já
registrava, e ela existe porque uma rota que responda 400 e grave assim mesmo **passa em qualquer
asserção sobre o corpo da resposta**. Nenhuma das duas asserções desta mudança olha o corpo para
decidir se gravou.

---

## 4. O que mudou nos cenários que já existiam, e por quê

`ResultRouteTest.CONTEUDO` era um esboço — `{"meta":{"exam_id":"prova-r"},"items":[],"answer_key":[]}`
— e **não decodifica** como `ExamPackage`. Ele bastava enquanto nada no servidor **lesse** o pacote:
o `content` só descia inteiro para o aparelho.

A trava de `variant_id` mudou isso. Ela confere a variante declarada contra `ExamPackage.variants`,
e para isso o `content` passa a ser decodificado. O esboço faria toda a suíte responder 500, e não é
isso que nenhum cenário dela mede.

`CONTEUDO` virou um pacote mínimo que **decodifica e declara `v1`**. Continua sendo **literal escrito
à mão**, e não `ExamPackage(...).toCanonicalJson()`, pela razão que o cabeçalho do arquivo já dá:
serializar com o mesmo código que o servidor desserializa poria o mesmo código dos dois lados, e
renomear um campo continuaria verde.

**Isso alcança os nove cenários que já existiam no arquivo**, e os nove continuam verdes. É
consequência direta da decisão 3 do `design.md`, e não escopo acrescentado.

---

## 5. As linhas já gravadas sem oráculo

**Uma linha de `grading_result` e 40 de `answer_observation`**, da conferência de ponta a ponta da
`slice-4b-outbox-de-resultado` — `capture_id = d671e626-…`, `revision` 1
(`docs/deploy-api.md`, seção "Antes de tudo", e `docs/cobertura-slice-4b-outbox-de-resultado.md`
§5.1).

**Esta contagem vem do registro, e não de uma consulta feita nesta sessão.** Não houve acesso ao
banco de produção aqui; o que existe é o que aqueles dois documentos afirmam ter observado. Se o
número importar para uma decisão, ele precisa ser reconferido contra o banco.

**O que acontece com elas: nada, e é deliberado.** `grading_result` é append-only por gatilho — não
há `UPDATE` nem `DELETE`, nem para o dono da tabela. Esta mudança não as toca, não as reclassifica e
não marca proveniência não conferida em lugar nenhum. O que ela faz é impedir que a **próxima** entre
sem oráculo.

**E não há retrofit possível nem desejável.** Conferir aquela linha agora significaria comparar o
`package_hash` dela com o `content_hash` do pacote da prova — que a etapa 3 **moveu**, ao acrescentar
`params_hash`. A linha foi apurada contra um pacote do contrato anterior, e ela é fato verdadeiro
sobre o que aconteceu. Reescrevê-la seria falsificar o registro; marcá-la exigiria uma coluna nova
que nenhum requisito pede. Ela é dado de conferência pré-lançamento, e o caminho para as provas
naquela condição continua sendo o de ADR-0009: publicar prova nova.

---

## 6. O que **não** foi verificado (P8)

- **A resposta 400 chegando ao classificador do aparelho.** Que `eTransitoria()` trata 4xx como
  definitivo já está verificado do lado do aparelho, por `envio-distingue-recusa-transitoria`. O que
  **não** foi exercitado nesta sessão é o par completo — servidor recusando por proveniência e
  aparelho classificando aquela resposta —, porque isso exigiria emulador, e a ETAPA 4 não o pede.
  Nenhuma linha de `apps/android` mudou.
- **Um corpo com os dois campos errados ao mesmo tempo.** Ele acusaria só o `package_hash`, porque a
  trava do hash vem primeiro. A ordem está afirmada na KDoc e é observável sob a mutação A — o
  cenário A chega à trava de variante e passa por ela —, mas nenhum cenário exercita os dois errados
  juntos. Seria um terceiro cenário, e ele não foi escrito.
- **`content` corrompido no banco.** Um pacote que não parseia faz a exceção subir, e a falha é do
  servidor — 500, corretamente. Não há cenário para isso, porque ele só é alcançável depois de o hash
  bater, o que exigiria corromper o `content` mantendo o `content_hash`. Não foi medido.
- **Custo da leitura do `content` a cada envio.** Aceito por decisão (design, *Risks*), e **não
  medido**. O `content` já vinha da tabela que a `join` alcança; nenhuma medição de latência foi feita
  antes ou depois.
- **A contagem das linhas de produção**, como a §5 diz: vem do registro, não de consulta.

---

## 7. A verificação final

| O quê | Comando | Quando | Desfecho |
|---|---|---|---|
| Suíte da API | `./gradlew :apps:api:test` | 2026-09-18T23:21:16Z | 165 testes, 0 falhas, 0 erros, 0 pulados |
| Mutação A | `./gradlew :apps:api:test` | 2026-09-18T23:22:25Z | 1 caído, o previsto |
| Mutação B | `./gradlew :apps:api:test` | 2026-09-18T23:23:03Z | 1 caído, o espelho |
| Depois da reversão | `./gradlew :apps:api:test` | 2026-09-18T23:23:35Z | 165 testes, 0 caídos |
| Árvore inteira | `./gradlew build --rerun-tasks` | 2026-09-18T23:26:08Z–23:28:49Z | **176 de 176 tarefas executadas**, nenhuma `UP-TO-DATE` |
| `MUTACAO` fora de `build/` | `grep -rn "MUTACAO" apps packages \| grep -v /build/` | 2026-09-18T23:28Z | **0** |
| Validação da mudança | `openspec validate servidor-confere-a-proveniencia-do-resultado --strict` | 2026-09-18T23:28Z | válida |

As contagens saem dos XML de `build/test-results/`, e não da linha final do Gradle. O
`--rerun-tasks` é o que torna a última linha uma medição e não um `UP-TO-DATE` herdado (P2):

| Alvo | Testes | Caídos |
|---|---|---|
| `apps/android :: testDebugUnitTest` | 303 | 0 |
| `apps/api :: test` | 165 | 0 |
| `buildSrc :: test` | 1 | 0 |
| `packages/domain :: jsNodeTest` | 318 | 0 |
| `packages/domain :: jvmTest` | 326 | 0 |
| `packages/domain :: testAndroidHostTest` | 318 | 0 |
| **Total** | **1431** | **0** |

A suíte **instrumentada** não entra nesta tabela e não foi rodada: nenhuma linha de `apps/android`
mudou, e a ETAPA 4 não pede emulador (P8, e P22 — o ambiente não foi tocado).
