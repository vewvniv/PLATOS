# Auditoria de regressão e conformidade — antes da fatia 5

**Data:** 2026-09-18 · **Alvo:** `main` em `f643abc`, árvore limpa · **Escopo:** conformidade com
`docs/architecture/ARQUITETURA-FINAL-v3.md`, `CLAUDE.md` e `rigorous.md`, e busca de regressão.

**O que este documento é.** Uma lista de falhas, com a evidência de cada uma e o julgamento de se
ela foi *mal feita* ou *adiada com justificativa que se sustenta*. Não é uma revisão de estilo, e
não propõe implementação.

---

## 0. Como esta auditoria se verificou, e o que ela não afirma

`rigorous.md` P6 obriga cada afirmação a carregar o tipo dela. As desta auditoria:

| Tipo | O que foi feito |
|---|---|
| **Conferido** | Leitura direta do código, das migrations, dos specs, dos ADRs, dos `docs/cobertura-*.md` e dos dois workflows. Cada achado abaixo traz `arquivo:linha`. |
| **Medido** | `./gradlew :packages:domain:allTests --rerun-tasks`, **nesta sessão**. Âncora conferida, e não o `exit 0` (P2): **37 de 37 tasks executadas**, nenhuma `UP-TO-DATE`; relatórios `2026-09-18T13:39:27Z`–`13:39:33Z`, posteriores aos que já estavam em disco; **953 testes, 0 falhas** (`jvmTest` 323, `jsNodeTest` 315, `testAndroidHostTest` 315). É o núcleo KMP nos três alvos — medição, layout, pacote, scoring, captura. |
| **Conferido (âncora)** | Os demais relatórios XML em disco foram lidos **com o `timestamp`**, não com a contagem (P3): 1410 testes, 0 falhas, janela `2026-09-18T11:01Z`–`11:32Z`; instrumentados 74, `11:04`. `git log --since` sobre essa janela mostra que **só `docs/`, `openspec/` e um comentário do `ci.yml` mudaram depois** — nenhuma linha de código. Logo os relatórios descrevem o código de HEAD. |
| **Suposto** | Um único achado é suposição declarada: o 4.3 (transferência entre aparelhos). Está marcado como tal e **não** deve ser lido como medição. |

**O que esta auditoria NÃO afirma.** O verde medido acima é do **módulo KMP**, e P5 proíbe
apresentá-lo como verde do CI: `./gradlew build` inteiro e `connectedDebugAndroidTest` **não foram
rodados nesta sessão** — o segundo exige emulador. Para `apps/api`, `apps/android` e o web, o que há
é a conferência de âncora da linha seguinte, não execução. Nenhum achado abaixo depende de a suíte
estar verde.

**Veredito de regressão:** **nenhuma regressão funcional foi encontrada.** Os achados são de
conformidade, de dívida que venceu, e de três defeitos reais de código que a suíte atual não alcança.

---

## 1. O que precisa ser dito antes da lista

Esta base é, em disciplina de verificação, melhor do que a média do que se vê em produção. Isso não
é cortesia — é contexto para calibrar a severidade do que vem depois. Concretamente, e conferido:

- **A paridade existe e reprova.** O `ci.yml` não só compara, ele **injeta defeito deliberado a cada
  execução** e falha se a comparação aceitar: deslocamento de 0,5 mm, faixa de trama ausente, limiar
  fora do corredor, dois defeitos de tinta. §16 diz que "se o teste de paridade não existir, vira o
  maior risco do projeto"; ele existe e é visto falhar em toda execução.
- **Append-only é do banco, não da aplicação.** `grading_result` e `answer_observation` têm gatilho
  que recusa UPDATE/DELETE **até para o dono da tabela**, mais `REVOKE`
  (`supabase/migrations/20260917134500_result_tables.sql`). I2 é estrutural.
- **RLS é a fronteira, e o `where` é seletor.** Nenhuma política de domínio autoriza por `user_id`
  (§3.2, D39), e `Tenancy.asUser` usa `set_config(..., true)`, local à transação — não vaza entre
  checkouts do pool (`apps/api/src/main/kotlin/com/platos/api/db/Tenancy.kt:26`).
- **I1 tem dente executável.** `requireCoherent()` recusa publicar item sem habilidade
  (`ExamPackageValidation.kt:33-45`), e `SkillCoverage` ainda distingue âncora curricular de
  cobertura direta — precisão que quase ninguém teria escrito antes de o boletim existir.
- **Dívida com dono foi honrada.** A tarefa 6.4b, herdada da 3c por falta de oráculo físico, fechou
  na 4a em telefone real (`slice-4a-package-pull/tasks.md:73`). A 9.2, desmarcada com "NÃO PASSA",
  fechou na `4a-cache-referencia` com asserção sobre a pilha de rede, e não sobre o ícone do modo
  avião. **P1 está sendo cumprido de verdade**, inclusive quando custa.
- **Os `cobertura-*.md` registram o que falhou, não o que passa**, e mantêm a afirmação errada ao
  lado da certa (P7). O §5.1 do outbox é o melhor exemplo desta base.

Nada abaixo contradiz isso. O problema desta base **não é rigor**. É que o rigor está concentrado no
que a fatia corrente toca, e três categorias escapam sistematicamente dele: **o que atravessa dois
módulos**, **o que vence depois do archive** e **o artefato de release**.

---

## 2. Achados graves

### 2.1 O contrato da API é copiado à mão entre `apps/api` e `apps/android` — quatro vezes, sem ADR — ~~**aberto**~~ **fechado**, e uma frase dele estava errada

> **Fechado em 2026-09-19, pela mudança `contrato-do-fio-com-dono-unico` e por ADR-0015** (ETAPA 6
> de `docs/plano-de-correcao-antes-da-fatia-5.md`). O texto abaixo fica inteiro e não se apaga (P7).
> **A marca só entrou aqui em 2026-09-23:** o archive da ETAPA 6 disse, na mensagem do commit
> (`1875c7d`), que o achado "sai da lista", e não o escreveu neste arquivo, como as outras etapas
> fizeram.
>
> Os quatro contratos passaram a ter um declarante, em `com.platos.domain.transport`, e o mapa
> `answer_kind` passou de três registros cegos a dois que se conferem
> (`tools/parity/answer-kind.mjs`). A prova de que é dono único, e não arquivo movido: um
> `@SerialName` trocado no domínio derrubou os dois testes de literal, e nada além deles em 152
> suítes (`docs/cobertura-contrato-do-fio-com-dono-unico.md` §2).
>
> **A frase errada é a que descreve a rede:** "Hoje a divergência é possível por construção e
> contida por literais JSON escritos à mão nos dois lados." Ela cita só os dois testes de resultado e
> afirma a rede para os quatro contratos — e vale para **dois**. Em organização e prova, o teste do
> servidor desserializa o corpo com o mesmo tipo que a rota usa para escrevê-lo, e só o aparelho tem
> literal. É P4, e já era assim antes da unificação: foi ela que o tornou visível. O detalhe, com
> arquivo e linha, está no §8 daquela cobertura, corrigido na mesma data; o tipo da afirmação é
> **conferido por leitura**, e não medido. A medição e a correção são a ETAPA 7.3,
> `o-fio-preso-nos-dois-lados`. **Medido no mesmo dia**, antes de a mudança ser proposta: real =
> previsto, `docs/cobertura-o-fio-preso-nos-dois-lados.md`, Parte I.
>
> **Corrigido em 2026-09-23, pela mudança `o-fio-preso-nos-dois-lados`.** Organização e prova
> ganharam literal do lado do servidor, e as três mutações passaram a derrubar os dois lados; uma
> guarda (`tools/parity/fio.mjs`, no CI) reprova tipo de `transport` sem literal nos dois lados, e
> fecha a porta para os contratos da fatia 5. Ela prova que o literal existe, e não que ele prende o
> fio. A mesma cobertura, Parte II. O CI da PR ainda não foi observado. **Observado no mesmo dia:**
> PR #59, os três jobs em `success`, e os dois passos da guarda lidos no log.

**O que.** `ResultSubmissionDto`/`AnswerObservationDto` (`apps/api/.../http/dto/ResultDto.kt`) e
`EnvioDeResultadoDto`/`ObservacaoDto` (`apps/android/.../api/ResultadoDto.kt`) são o **mesmo
contrato, digitado duas vezes**. O mesmo vale para organização, prova e roster — quatro DTOs, quatro
fatias. Pior: o mapeamento `QuestionAnswer → string` existe **três vezes** — `tipoGravado()` na API,
`tipoNoEnvio()` no Android, e o `check (answer_kind in (...))` da migration.

**Contra o que colide.**

| Regra | Texto |
|---|---|
| `CLAUDE.md` regra 7 | "Não duplique regra de negócio entre apps; código compartilhado pertence ao domínio KMP quando aplicável." |
| §14 regra 1 | "Contrato antes de código. Campo novo? Muda `packages/domain` ou `contracts` primeiro, em commit separado. **Maior alavanca anti-regressão do projeto.**" |
| §13 | "`packages/domain` em KMP — … **tipos de contrato**. Compilado para JVM e Android a partir do mesmo código; **divergência é impossível por construção**." |

Hoje a divergência é **possível por construção** e contida por literais JSON escritos à mão nos dois
lados. É uma boa rede — `ResultadoDtoTest` e `ResultRouteTest.corpo()` de fato pegariam um
`capture_id` renomeado — mas é categoricamente mais fraca do que a garantia que §13 descreve, e a
diferença aparece no campo **novo**, que ninguém pensa em prender nos dois literais.

**A justificativa se sustenta?** **Não, e ela mesma diz por quê.** A origem está em
`slice-4a-zero-device-auth/tasks.md:27`:

> **"Compartilhado" virou espelho, e não arquivo compartilhado**: os dois módulos dependem de
> `packages:domain`, então **dividir o DTO era possível**, mas a proposal e a 7.4 declaram
> `packages/domain` e `apps/api` intocados.

Três coisas: (a) a razão é **escopo de uma fatia**, não arquitetura; (b) a própria tarefa admite que
o caminho certo era possível — os dois módulos **já dependem de `packages:domain`**, e os dois já
importam `com.platos.domain.capture.QuestionAnswer` para preencher esses DTOs; (c) a tarefa pedia
"o DTO de organização **compartilhado** com o servidor" e foi fechada com outra coisa.

Uma decisão de fatia, legítima como troca pontual, virou **o padrão da casa em quatro fatias** sem
nunca ser reaberta. `rigorous.md` §0 é explícito: o que está no nível 1 (arquitetura) muda por **ADR
novo**, não por decisão no nível 3 e muito menos por KDoc. Não há ADR, e não há linha em §17
"Aberto" nem em §16 com fatia-limite e dono — **flutua**, que é exatamente o que §16 existe para
impedir.

**Severidade: grave.** Não porque quebre hoje, mas porque a fatia 5 acrescenta discursiva — rubrica,
transcrição, recorte — e é o **maior** acréscimo de superfície de contrato até aqui. O custo de
convergir cresce com o número de espelhos, e são quatro.

**Nota de justiça.** O espelho do `LayoutMap` em TypeScript (`apps/web/src/layoutMap.ts:1-6`) é
categoria diferente e **não** entra neste achado: ele é contido por um oráculo de saída — paridade,
fidelidade e tinta sobre o documento rasterizado. Espelho com oráculo independente é troca
defensável; espelho guardado só por literal combinado não é.

---

### 2.2 O `package_hash` do resultado é uma afirmação do aparelho que ninguém confere — ~~**aberto**~~ **fechado**

> **Fechado em 2026-09-18, pela mudança `servidor-confere-a-proveniencia-do-resultado`** (ETAPA 4 de
> `docs/plano-de-correcao-antes-da-fatia-5.md`). O texto abaixo fica inteiro e não se apaga (P7).
>
> O servidor passou a recusar resultado cujo `package_hash` não seja o `content_hash` do pacote
> publicado daquela prova, e cujo `variant_id` o pacote não declare. A recusa é **400** — decisão
> sobre o pedido, que o aparelho já classifica como definitiva —, e continua distinta dos **404** de
> ausência, que seguem indistinguíveis entre si. Nada é gravado: nem o resultado, nem a evidência por
> questão, e a contagem que prova isso é feita no banco.
>
> **O achado estava certo no ponto que mais importava: o custo era uma coluna numa consulta que já
> rodava.** `findPublishedExamId` passou a devolver `content` e `content_hash` da mesma `join` em
> `EXAM_PACKAGE` que já decidia "publicada" — nenhuma consulta nova, e nenhum segundo oráculo para
> "qual é o pacote desta prova".
>
> **Cada trava foi vista falhar sozinha.** Duas mutações de conjuntos **disjuntos**, 1 cenário caído
> em cada, previsão batida nas duas: `docs/cobertura-servidor-confere-a-proveniencia-do-resultado.md`
> §2.
>
> **O que o fechamento não faz, e fica dito:** a linha de `grading_result` já gravada em produção sem
> oráculo **permanece como está**. A tabela é append-only por gatilho, e a linha é fato verdadeiro
> sobre o que aconteceu — ela foi apurada contra um pacote do contrato anterior à etapa 3. Reescrevê-la
> seria falsificar o registro. §5 da cobertura diz o que se sabe sobre ela e de onde o número veio.

**O que.** `grading_result.package_hash` é gravado exatamente como o aparelho o enviou
(`ResultQueries.record`, `.set(GRADING_RESULT.PACKAGE_HASH, nota.packageHash)`). O servidor **nunca
o compara** com `exam_package.content_hash` da prova. `findPublishedExamId` já faz `join` em
`EXAM_PACKAGE` na mesma consulta — o valor certo está a uma coluna de distância.

**Por que importa.** A coluna existe justamente para ser prova. A própria migration diz:

> "Contra qual pacote e qual variante a nota foi apurada. **Nota sem dizer de qual pacote é vira
> número sem prova** no dia em que existir mais de uma versão da prova."

E o spec (`result-sync`, requisito "O resultado durável diz de qual folha, de qual pacote e de qual
aluno ele é") usa `SHALL identificar`. Identificar sem conferir é declarar. O fato é **append-only e
imutável**: um `package_hash` errado não tem conserto — só revisão nova, que não apaga a anterior. E
§9 da arquitetura dá a razão de fundo: a rastreabilidade existe para **auditoria de contestação de
nota**. Uma proveniência não verificada não sustenta contestação.

**A justificativa se sustenta?** Não há justificativa. Não é item registrado: não está em §5 do
`cobertura-slice-4b-outbox-de-resultado.md` ("o que ainda não foi verificado"), não está em §6 ("o
que esta fatia deliberadamente não fez"). `ResultRouteTest` só exercita `$HASH` correto — **não
existe cenário de hash divergente**, nem de `variant_id` que o pacote não declara. A camada é uma
das que P16 chama de não verificada pela vizinha: `ObjectiveScore.paraNota()` confere a **coerência
interna** do corpo e nada mais; ele aceita qualquer hash de 64 hex.

**Severidade: grave.** É o único ponto do sistema em que um dado entra no registro imutável sem
oráculo, e o custo de conferir é uma coluna numa consulta que já roda.

---

### 2.3 O modo degradado (§10) saiu da fatia 4 sem fatia-limite, e o spec já o proíbe

**O que.** §10 da arquitetura é categórico:

> **Cache miss:** gate de pré-voo verifica o pacote antes de abrir a sessão; se offline e ausente,
> **modo degradado** — captura e guarda as imagens brutas para corrigir depois. **Nunca falha em
> silêncio.**

§15 manteve o item na fatia 4, com a razão escrita: "Modo degradado continua aqui, e não é o gate …
hoje esse caso **barra** em vez de capturar — ele depende de fato durável no aparelho, que é o que
esta fatia introduz."

O fato durável foi introduzido — Room, outbox, `grading_result`. A fatia 4 fechou inteira (4a-zero,
4a, 4a-cache, 4b-atribuição, 4b-roster-entrega, 4b-roster-no-aparelho, 4b-outbox, todas arquivadas).
O modo degradado **não entrou**, e o registro dele se dispersou:

| Onde | O que diz |
|---|---|
| §15 da arquitetura | está na fatia 4 — **não foi atualizado** |
| ADR-0013, "Consequências" | "Modo degradado e outbox continuam fora: são **4b e 4c**" — `4c` não existe em §15, e nunca foi proposta |
| `cobertura-slice-4b-outbox-de-resultado.md:378` | agrupado com `assessment_fact`, `capture_session` e `sync_cursor`, sob "não têm consumidor neste fluxo" |

**A justificativa se sustenta?** **Não.** Ela vale para `capture_session` e `sync_cursor` — esses são
tabelas sem consumidor. Não vale para o modo degradado: o consumidor dele é a **promessa de produto
da §10**, e ele é o único dos quatro que §15 nomeou como entrega desta fatia. Adiar é legítimo;
adiar **sem fatia-limite e sem dono** é a forma exata que o item da LGPD tomou até quase virar
retrofit — e §16 registra isso em prosa.

**Agrava.** O `openspec/specs/device-session/spec.md:352` agora afirma o oposto como comportamento
corrente, sem marca de provisoriedade:

> "Pacote ausente e sem rede SHALL levar à recusa explicada, e SHALL NOT levar a escaneamento."

Pelo `rigorous.md` §0, spec (nível 3) não vence arquitetura (nível 1). O spec, como está, descreve um
sistema que contradiz §10 e **não diz que contradiz**. Quem ler só o spec conclui que a decisão foi
tomada. Ela não foi: não há ADR.

**Severidade: grave** — não pelo código, que está certo dentro do que escolheu fazer, mas porque uma
promessa de §10 deixou de ter dono no momento em que a fatia que a carregava fechou.

---

## 3. Achados sérios

### 3.1 A variante `release` não tem teste nenhum no grafo, e a guarda do APK só olha o `debug`

**O que.** Três fatos que se somam:

1. `apps/android/build.gradle.kts` **não tem bloco `buildTypes`**. Nenhum `release` configurado:
   sem `signingConfig`, sem `proguardFiles`, `versionCode = 1` fixo.
2. `testReleaseUnitTest` **não existe** no grafo. Medido e registrado em
   `docs/cobertura-fatia-4a-cache-referencia.md:220`, com a causa apurada (AGP 9 não cria a variante
   por padrão) e a testemunha temporal (o relatório velho é de `2026-08-15T20:29Z`, treze minutos
   antes do commit que subiu o AGP).
3. `verificarApkSemPacote` — a guarda que ADR-0013 decisão 5 chama de "a falha mais provável, e a
   mais quieta" — roda **só sobre o debug**: `dependsOn("assembleDebug")` e
   `outputs/apk/debug` (`build.gradle.kts:365,368`).

Consequência composta: **o artefato que vai para o professor não é verificado por nada.** Nem por
teste de unidade, nem pela guarda de pacote embutido.

**A justificativa se sustenta?** A dívida foi registrada com dono ("`apps/android/build.gradle.kts`")
e gatilho ("a próxima que mexer em build ou variante"). **O gatilho disparou duas vezes e ninguém
atendeu:** `d054e1f build(4b-outbox-de-resultado): Room, WorkManager e o processador de anotacoes`
mexeu no build do aplicativo, e `generatejooq-sem-registro-automatico` mexeu em `buildSrc` e no
`ci.yml`. Existe até uma branch batizada — `vewvniv/debito-variante-release` — cujo único commit
(`438030a`) **documenta** a dívida e não a paga.

Isso é diferente do modo degradado: aqui o registro está certo e o gatilho é que foi ignorado. Mas o
efeito prático é o mesmo, e a fatia-limite real já passou.

**Severidade: séria, e crescente.** "Próximo do lançamento" é literalmente o momento em que a
variante release deixa de ser hipótese. O `verificarApkSemPacote` sobre o release é barato — é trocar
duas linhas — e é a metade que mais importa.

---

### 3.2 `ScanActivity` abre um `RoomDatabase` novo a cada `onCreate`, e nunca fecha nenhum — ~~**aberto**~~ **fechado**

> **Fechado em 2026-09-19, pela mudança `o-pendente-nao-se-perde-no-aparelho`** (ETAPA 5). O texto
> abaixo fica inteiro e não se apaga (P7).
>
> `abrir` passa a devolver **sempre a mesma instância**, guardada no companion e construída com
> `applicationContext`. Os três chamadores não mudaram — eles já chamavam `abrir` —, e **ninguém
> fecha**: com uma instância por processo o dono é o processo, e `close()` por qualquer dos três
> derrubaria a base dos outros dois.
>
> **O achado estava certo sobre a causa de ele ter atravessado.** Os dois cenários que ele nomeia
> construíam a base com nome próprio e guardavam a referência; um cenário novo afirma, pelo caminho
> de produção, que duas chamadas a `abrir` devolvem a mesma instância — por `assertSame`, porque
> asserção sobre o **dado** passaria com o defeito presente.
>
> **O achado subestimou a consequência, e a medição mostra isso.** A mutação sozinha não sustentava
> a frase "é daí que nasce `SQLiteDatabaseLockedException`" — com **duas** conexões benignas nada
> estoura. Um experimento posterior (`AcumuloDeInstanciasProbe`, 2026-09-19, aparelho 2511FPC34G,
> **sete execuções**) pôs as duas topologias sob a **mesma** carga, 64 instâncias × 150 escritas: a
> antiga estourou `SQLITE_BUSY` em **todas as sete**; a nova, **nunca**.
>
> **E a exceção não é ruído: ela leva o pendente junto.** O fio que estoura aborta as escritas que
> faltavam, e a topologia antiga gravou **9340, 9297 e 9155 de 9600** nas três execuções que
> contaram — **260 a 445 correções perdidas**, 2,7% a 4,6%. A nova gravou 9600 de 9600 nas três. O
> achado descrevia um modo de falha barulhento; o que se mediu é **perda silenciosa de correção**,
> que é mais grave do que ele afirmava.
>
> **O que continua sem medição:** a corrida do uso real — rede intermitente com a câmera aberta. O
> probe produz contenção por carga sintética. §2-bis e §7 da
> `docs/cobertura-o-pendente-nao-se-perde-no-aparelho.md`.


**O que.** `ResultadosEmRoom.abrir()` (`ResultadosEmRoom.kt:117`) chama
`Room.databaseBuilder(...).build()` — que **não** é singleton e **não** deduplica. Há três chamadores
de produção, todos sobre o mesmo arquivo `outbox.db`:

| Chamador | Linha | Quando |
|---|---|---|
| `SessaoActivity.onCreate` | `SessaoActivity.kt:127` | toda criação da tela de sessão |
| `ScanActivity.onCreate` | `ScanActivity.kt:122` | toda criação da tela de câmera |
| `passadaDeEnvio` (worker) | `EnvioDeResultadosWorker.kt:169` | toda passada da fila |

Nenhum dos três fecha. `ScanActivity.onDestroy` desliga só o executor (`ScanActivity.kt:152-157`);
`SessaoActivity.onDestroy` fecha só o `http` (`SessaoActivity.kt:158-162`).

**Por que importa.** Cada instância carrega o próprio `SupportSQLiteOpenHelper` e a própria conexão.
Rotação de tela, ida e volta ao escaneamento e cada passada do worker acumulam instâncias vivas sobre
o mesmo arquivo. O worker roda **quando há rede** — inclusive com a câmera aberta —, e escrita
concorrente por conexões distintas no mesmo SQLite é onde nasce `SQLiteDatabaseLockedException`.

O que está em jogo é o dado que esta base decidiu proteger acima de tudo: o pendente é, pela decisão
registrada em `ResultadoPendente`, **o único exemplar de uma correção já feita**.

**A justificativa se sustenta?** Não há justificativa — não é item registrado em nenhum
`cobertura-*.md`. E os testes instrumentados **não alcançam**: `OutboxEmRepousoInstrumentedTest` e
`ApagamentoLocalInstrumentedTest` constroem a base por `Room.databaseBuilder` direto, com nome
próprio, e guardam a referência num campo. É a forma de sombreamento de fixture que `rigorous.md` §3
descreve: o teste exercita uma topologia — uma instância, um dono — que **não é a da produção**.

**Severidade: séria.** Defeito real, caminho de produção, dado insubstituível, e nenhuma camada de
teste posicionada para vê-lo.

---

### 3.3 `ScanActivity.gravar` descarta uma correção apurada em silêncio — ~~**aberto**~~ **fechado**

> **Fechado em 2026-09-19, pela mudança `o-pendente-nao-se-perde-no-aparelho`** (ETAPA 5). O texto
> abaixo fica inteiro e não se apaga (P7).
>
> **O caminho silencioso deixou de existir, em vez de passar a ser tratado.** O `short_id` entrou no
> gate que decide antes de a câmera ligar; `organizacao` e `prova` deixaram de ser campos nuláveis, e
> os dois `?: return` sumiram. Tratar o nulo dentro de `gravar` manteria construível um estado que
> não deveria existir.
>
> **A assimetria que o achado apontou foi a chave.** A decisão de "tem tudo o que precisa" já morava
> em `onCreate`; o que faltava era o `short_id` estar nela. O motivo da recusa é **próprio**, e não
> entra em `MotivoDaBarragem` — aquele enum decide **antes** do `Intent` e não tem como saber que um
> extra vai faltar. A distinção é verificada por asserção, e a frase da recusa nova **não** manda
> baixar a prova de novo: o pacote está no lugar.
>
> A decisão saiu de `ScanActivity` para uma função própria, porque dentro de `onCreate` ela **não
> tinha um único cenário** — nem o requisito de spec que já existia era exercitado por nada.


**O que.** `ScanActivity.kt:248-249`:

```kotlin
val organizacao = organizacao ?: return
val prova = prova ?: return
```

Se qualquer dos dois for nulo, a folha é medida, a nota é **desenhada na tela**, e nada é gravado nem
agendado. Sem mensagem, sem log, sem diferença visível para quem segura o aparelho.

**Atenuante real.** O único lançador (`SessaoActivity.kt:338-341`) sempre põe os três extras, então o
caminho não é alcançável hoje pelo fluxo normal.

**Por que continua sendo achado.** O `Intent` **sobrevive à morte do processo** — é a propriedade que
a KDoc da própria classe usa para justificar reler o pacote do cache. Um extra que mude de nome entre
versões, ou um `Intent` reconstruído, cai exatamente aqui. E a assimetria é o que chama atenção: o
mesmo arquivo trata `contentHash`/`organizacao` ausentes com tela dedicada (`SemPacoteScreen`,
`ScanActivity.kt:99-106`), e a KDoc de `EXTRA_SHORT_ID` (`ScanActivity.kt:271-279`) **raciocina em
detalhe** sobre o risco de a folha "cair em silêncio" na leitura do roster — e depois o mesmo arquivo
descarta a nota em silêncio vinte linhas antes.

§10 diz "Nunca falha em silêncio", e o spec `result-sync` diz que a nota apurada SHALL virar
resultado durável.

**Severidade: séria** pela forma da falha, moderada pela alcançabilidade. Não é "não é mitigado, é
conhecido" (P8) — é desconhecido: não está registrado em lugar nenhum.

---

## 4. Achados moderados

### 4.1 I3 está implementada pela metade: `params_hash` não existe em lugar nenhum — ~~**aberto**~~ **fechado**

> **Fechado em 2026-09-18, pela mudança `params-hash-no-pacote-publicado` e por ADR-0014.** O texto
> abaixo fica inteiro e não se apaga (P7).
>
> `PackageMeta` passou a carregar os **três** campos de I3. A KDoc que afirmava que a fatia 6
> preencheria os campos "sem mexer no contrato — não há o que retrofitar" foi corrigida, e a frase
> antiga ficou citada nela: era falsa no campo que faltava, e o achado estava certo.
>
> **A contradição da arquitetura, que o achado nomeou como atenuante, foi resolvida no nível certo.**
> ADR-0014 decisão 1 registra que §2 (I3) vence a lista do §5 pela precedência do `rigorous.md` §0, e
> a lista foi corrigida — com a incompletude dita, porque ela **produziu código**.
>
> **O custo previsto foi pago uma vez, e medido:** o `content_hash` de todo pacote mudou; três
> fixtures e dois literais acompanharam; nenhuma geometria foi tocada (guarda de vacuidade P13, com
> comparação estrutural além do `git diff`); e paridade e fidelidade fecharam na mesma sessão, com os
> quatro PDFs gerados nela (P23). `docs/cobertura-params-hash-no-pacote-publicado.md`.
>
> **O que a mudança deixou em aberto, e está registrado:** as duas provas publicadas em produção
> (`prova-referencia-slice-1` e `slice-2`) deixam de ser legíveis por um aplicativo atualizado. É o
> comportamento correto da camada (b) e o caminho de saída é ADR-0009 — §6 da cobertura.

`params_hash` aparece **duas vezes em toda a árvore** — em `CLAUDE.md:38` e em
`ARQUITETURA-FINAL-v3.md:48`. Zero ocorrências em código, schema ou fixture.

`PackageMeta` (`ExamPackage.kt:140-141`) carrega `prompt_version` e `model_id`, com esta KDoc:

> "I3: presentes no contrato desde o início, vazios enquanto a prova for fixa. … a fatia 6 preenche
> estes campos **sem mexer no contrato** — que é exatamente o que I3 existe para garantir: quando a
> geração chegar, **não há o que retrofitar**."

**A afirmação é falsa como escrita**, e falsa no campo que mais custa. O `ExamPackage` é imutável e
hasheado sobre a serialização canônica com `encodeDefaults = true`: acrescentar `params_hash` muda o
JSON canônico e, portanto, **o `content_hash` de todo pacote**. É precisamente o custo que motivou
pôr os outros dois cedo. Dois de três não é "não há o que retrofitar".

**Circunstância atenuante, e ela é do documento.** §5 lista o conteúdo de `meta` **sem**
`params_hash`, enquanto §2 (I3) o exige. A implementação seguiu §5. Então a origem é uma
**contradição interna da arquitetura**, não desleixo de implementação — e, pela precedência do
`rigorous.md` §0, é a invariante que vence.

**Severidade: moderada agora, cara na fatia 6.** Custa um campo hoje; custa rehashear todo pacote
publicado depois.

### 4.2 O spec de `result-sync` diz "token vazio" e o código grava `null` — ~~**aberto**~~ **fechado**

> **Fechado em 2026-09-19, pela mudança `o-pendente-nao-se-perde-no-aparelho`** (ETAPA 5). O texto
> abaixo fica inteiro e não se apaga (P7).
>
> O spec passa a dizer **ausente**, nos dois pontos, **com a razão junto** — que é o que o achado
> nomeia como faltando: a distinção vivia só em comentário de migration e KDoc, e foi assim que a
> spec pôde ficar do lado errado dela sem ninguém notar.
>
> Foi como delta de mudança, e não edição direta da spec principal: o atalho das cinco condições
> exige "nenhum texto novo é inventado", e trocar "vazio" por "ausente" com a razão junto inventa
> texto.


`openspec/specs/result-sync/spec.md:41` e `:57` dizem que a folha avulsa "SHALL produzir resultado
durável **com token vazio**". O código converte vazio em nulo em três pontos —
`ScanActivity.kt` ("Token vazio vira nulo"), `ResultadoPendente.studentToken: String?`, e a migration,
cujo comentário explica por quê:

> "NULO na folha avulsa, e não string vazia. … Vazio faria todas as avulsas da mesma prova colidirem
> no unique de revisão."

Ou seja: a diferença entre vazio e nulo **é a decisão**, e o spec ficou do lado errado dela. O
`design.md` da fatia não a menciona — a razão vive só em comentário de migration e KDoc.

`CLAUDE.md` diz que `openspec/specs/` descreve o comportamento atual. Aqui não descreve.
**Severidade: moderada** — texto, mas exatamente no campo cuja semântica a fatia mudou.

### 4.3 `allowBackup="false"` pode não cobrir transferência entre aparelhos — ~~**suposto**~~ **medido, confirmado e corrigido**

> **Fechado em 2026-09-18, pela mudança `transferencia-entre-aparelhos`.** O texto abaixo fica inteiro
> e não se apaga (P7) — ele é o achado como foi escrito, e o valor dele está em ter sido marcado
> **suposto** em vez de afirmado.
>
> **A suposição estava certa, e a severidade subiu para grave, como ele previu.** A medição está em
> `docs/cobertura-transferencia-entre-aparelhos.md`. O mesmo pacote, com a mesma semente, sob os
> quatro transportes do aparelho: três responderam `Backup is not allowed` e o `D2dTransport`
> respondeu `Success`, com **19 968 bytes** entregues e o agente escrevendo no fluxo
> `f/rosters/<org>/<prova>.json` — **nome de aluno** —, `db/outbox.db` e `sp/platos-sessao-cifrada.xml`.
>
> **Três coisas que o achado não sabia, e que a medição produziu.** (1) O `outbox.db` **não** está em
> `filesDir`: está em `databases/`, e entra no fluxo sob outro domínio — uma regra escrita só para
> arquivos o deixaria passando. (2) A credencial cifrada também atravessava, e o achado não a cita.
> (3) Negar os três domínios **não bastou**: a primeira passada da correção ainda mostrou o domínio
> `root` no fluxo, porque é lá que nasce tudo o que `getDir()` cria. Com os quatro negados, o
> transporte passou a cancelar o pacote — `doesn't have any backup data` — com o roster ainda
> legível no disco.
>
> **O que continua aberto** está na tabela de ponto de não-retorno do `ARQUITETURA-FINAL-v3.md` §16:
> `dataExtractionRules` não existe abaixo da API 31, o `minSdk` é 26, e a medição é de Android 16.

`AndroidManifest.xml` desliga o backup no aplicativo inteiro, e a razão escrita é a certa: "regra que
lista arquivos silencia quando alguém acrescenta o terceiro". A intenção declarada é **"o dado não
sai do aparelho"**.

**A suposição, e ela é minha, não medida.** Para `targetSdk ≥ 31` — aqui é 35 — a documentação do
Android separa *cloud backup* de *device-to-device transfer*, e a segunda passa a ser controlada por
`android:dataExtractionRules`, que **não existe** nesta árvore. Se a leitura estiver certa, uma
transferência para aparelho novo levaria o `filesDir` inteiro: `rosters/` com **nome de aluno** e
`outbox.db` com correções pendentes.

Três coisas o tornam relevante: o roster em disco é texto claro (`RostersEmArquivo`, sem cifragem — o
que a política §12 cobre, ao atribuir a segurança física ao usuário); dado pessoal de menor sairia do
aparelho por um caminho que a política não enumera; e a classe H já é, por confissão do §16, o item
em que "o roster baixado não está na lista".

**Não afirmo que vaza.** Afirmo que a decisão foi tomada com um mecanismo e o `targetSdk` mudou o
alcance dele, e que **ninguém mediu**. Custa um `dataExtractionRules` e uma conferência em aparelho.
**Severidade: moderada, e sobe para grave se a medição confirmar.**

### 4.4 A versão do renderizador (D24) vive em três registros que não se conhecem

| Registro | Papel |
|---|---|
| `LayoutMap.MIN_RENDERER_VERSION` (KMP) | o que a publicação **escreve** no mapa |
| `RendererContract.RENDERER_VERSION` (Android) | o que o renderizador e **o gate de captura** leem (`PreparoDaProva.kt:271`) |
| `RENDERER_VERSION` (`apps/web/src/layoutMap.ts:124`) | o que o renderizador web lê |

Os três valem `1`, e **nada os compara**. A KDoc do Android diz "Espelha `RENDERER_VERSION` do lado
web" — afirmação sem quem a imponha.

O que torna isto um achado, e não uma observação, é que **esta base já reconheceu e resolveu esta
exata forma de defeito** para o limiar do OMR. O `ci.yml` diz, sobre `tools/parity/limiar.mjs`:

> "O limiar do OMR aparece em **três registros que não se conhecem** … Divergir entre eles não quebra
> teste nenhum — compila, o golden não muda, o hash do pacote continua igual, e a folha segue sendo
> lida com o número errado."

Troque "limiar" por "versão do renderizador" e a frase continua verdadeira, palavra por palavra. A
direção silenciosa é a pior: um renderizador que ganhe capacidade e suba a própria constante sem o
mapa subir `MIN` faz clientes antigos desenharem mapas novos — que é o que D24 existe para impedir.
**Severidade: moderada.** A solução já existe nesta árvore; falta apontá-la para o segundo alvo.

### 4.5 O runbook de deploy afirma coisas que deixaram de ser verdade

`docs/deploy-api.md` é o documento que alguém abre sob pressão. Três afirmações desatualizadas:

| Linha | Diz | É |
|---|---|---|
| `39-41` | "**Antes de tudo: o schema ainda não existe no Supabase** … as migrations nunca foram aplicadas ao projeto real" | Falso. A conferência da 4b gravou `grading_result` **em produção** (`cobertura-slice-4b-outbox-de-resultado.md` §5.1) |
| `37` | "as **oito** migrations aplicadas" | São **nove** |
| `91` | "As **oito** tabelas têm RLS habilitada **e** `force row level security`" | São **dez**. Afirmação de segurança, em número: quem auditar por ela confere 8 e passa por cima de 2 |

O código está certo — as duas tabelas novas nascem com RLS forçada e a guarda derivada do catálogo
(`ConnectionRoleTest`) reprova o build se alguma não nascer. O **registro** é que envelheceu. P7 é
sobre não apagar o errado; isto é o vizinho dele — não deixar o certo de ontem passar por certo de
hoje.

### 4.6 A aplicação de migration em produção não tem pipeline, nem fatia-limite, nem dono

`docs/deploy-api.md:423` registra: "Nada neste caminho as roda a cada deploy, e uma migration nova
exige repetir o passo à mão."

**Já cobrou.** Na conferência da 4b, o código novo subiu contra o schema antigo e o push deu **HTTP
500** — com o `/health` respondendo 200 o tempo todo. O incidente está escrito
(`cobertura-slice-4b-outbox-de-resultado.md` §5.1.1, item 2).

Registrado, sim. Mas **sem fatia-limite e sem dono**, na seção "o que este roteiro não cobre" — que é
uma lista de ausências, não uma tabela de ponto de não-retorno. Um item que já produziu incidente em
produção e continua sem data é a definição operacional de flutuar. A fatia 5 acrescenta schema
(discursivas, transcrição), o que faz a próxima migration ser certa, não hipotética.
**Severidade: moderada, com histórico.**

### 4.7 A obrigação de reexaminar o limiar do OMR venceu na 3c e não foi paga

`cobertura-fatia-3b.md` fechou o limiar com um corpus de **9 fotos, um celular, uma impressora**, e
registrou a obrigação: "A fatia da câmera, que verá muitos, herda a obrigação de reexaminar `V` e
`C`".

A fatia da câmera foi a 3c. Ela rodou em **um** aparelho (Poco X8 Pro), **uma** impressora, seis
folhas — e registrou honestamente: "A obrigação que a 3b registrou **continua aberta**". Sem novo
prazo e sem novo dono.

Some-se o que o mesmo corpus deixou sem causa: **2 das 9 fotos não decodificam o QR**, e as duas
hipóteses testadas (resolução e tamanho de arquivo) caíram. Investigar foi adiado para "fatia
própria" — que não existe.

E §14 regra 5 pede "**golden corpus de CV (~30 folhas** fotografadas em ângulos, luz e **letras
diferentes**, com saída esperada, rodando em CI)". Temos 9 fotos de 3 folhas, e o eixo "letras
diferentes" não existe. O corpus roda em CI, o que é a metade difícil — mas o tamanho é 3 folhas
distintas contra ~30.

**O julgamento é misto, e vale ser preciso.** A honestidade é exemplar: o número saiu de medição
declarada antes (ADR-0007/ADR-0011), o corredor foi conferido por oráculo independente
(`limiar.mjs`), e a limitação está escrita sem eufemismo. O problema é que **a correção objetiva
offline já é o produto** — §15 diz que a fatia 3 "já é produto … a proposta de valor do Basic" — e
ela está calibrada sobre uma impressora e um telefone, com a obrigação de ampliar sem dono desde a
3c. **A fatia 5 é a fatia do corpus.** É o momento certo para essa linha ganhar prazo em §16, junto
com a de manuscrito que já está lá.

---

## 5. Achados menores

### 5.1 KDoc de `ScanActivity` contradiz o que a classe faz

`ScanActivity.kt:61`: "**Nada e persistido: a nota e apresentada e some. Room e outbox sao da fatia
4b.**"

A classe abre a fila do Room na linha 122 e chama `gravarEAgendar` na 262. A fatia 4b **é esta**. O
arquivo foi editado pela própria fatia do outbox — não é refatoração fora de escopo (P19), é a
descrição da classe que a mudança alterou.

Vale citar o precedente: a mesma fatia registrou uma KDoc obsoleta análoga em `VisoesEmArquivo`
(`cobertura-slice-4b-outbox-de-resultado.md` §6.6), com dono e fatia-limite. **Esta, no arquivo
central da fatia, passou.**

### 5.2 O comentário de cabeçalho do RLS afirma mais do que o arquivo cumpre

`supabase/migrations/20260813223821_rls_policies.sql:6-7`: "**Nenhuma** política abaixo referencia
`created_by_user_id` ou `user_id` como chave de acesso: a autorização vem **sempre** de `membership`
sobre `organization_id`."

Duas políticas no mesmo arquivo referenciam: `membership_self_select` (`user_id =
app_current_user_id()`) e `app_user_self_select` (`id = ...`).

O **desenho está certo** — a tabela de vínculo não tem como ser autorizada por si mesma, e as duas
exceções são estruturalmente necessárias. O que está errado é o absoluto num comentário de arquivo de
segurança, que é onde a precisão mais vale. É §1 do `rigorous.md` em escala pequena.

### 5.3 `concurrency: cancel-in-progress: true` continua sobre o job de paridade

`ci.yml:7-10`. P15 registra o incidente: "`concurrency: cancel-in-progress` derrubou a paridade na PR
#30 e **custou horas**". A configuração permanece, e o job `paridade` — que `needs: web` e sobe
emulador — é o mais longo e o mais caro de perder. Não é defeito de código, e desligá-lo custa tempo
de runner. Mas o risco é conhecido, nominal e recorrente, sobre justamente o teste que §16 chama de
"o maior risco do projeto" se faltar.

### 5.4 `PackageMeta.exam_id` carrega, no artefato hasheado, o `short_id` — ~~**aberto**~~ **fechado pela asserção, e não pela renomeação**

> **Fechado em 2026-09-18, pela mudança `params-hash-no-pacote-publicado`.** O texto abaixo fica
> inteiro e não se apaga (P7) — inclusive a frase que oferecia as duas saídas, porque a escolha entre
> elas é o que ficou decidido.
>
> O achado dizia: *"Vale um teste que afirme a igualdade, **ou** a renomeação agora — antes de haver
> pacote publicado em volume."* **A renomeação foi recusada**, e ADR-0014 decisão 4 escreve por quê:
> `LayoutMap` também tem `exam_id`, e renomear nos dois estenderia a quebra de hash ao golden do
> layout, à folha de teste e a toda a cadeia de paridade — um evento P23 muito maior que o desta
> mudança; e renomeação misturada com mudança funcional é o que P25 proíbe.
>
> **O nome continua errado, e passa a estar preso.** `IdentidadeDaProvaTest` afirma que
> `meta.exam_id`, o `id` da definição publicada e o `exam_short_id` que viaja no QR de cada
> atribuição são um valor só — lido pelo `QrPayload.read`, o mesmo leitor do aparelho. Visto falhar:
> sob uma divergência introduzida no artefato, os três cenários caem
> (`docs/cobertura-params-hash-no-pacote-publicado.md` §4).
>
> **A janela que o achado invocava — "antes de haver pacote publicado em volume" — continua aberta
> para a renomeação**, e quem quiser fazê-la depois paga o P23 do layout. O que esta mudança comprou
> foi a impossibilidade de os três divergirem em silêncio.

§5 lista `exam_id` **e** `short_id` como campos distintos de `meta`. O implementado tem só `exam_id`
— e ele contém o `short_id` (`Publish.kt`, `examId = id`; `ScanSession.resultOf` compara
`examPackage.meta.examId` com `payload.examShortId`).

Hoje é consistente por construção, porque `ExamPublication.publish` alimenta os dois do mesmo valor.
Mas o nome está num artefato **imutável e hasheado**: renomear depois muda todo `content_hash`. A
`ScanActivity` já nomeia a fragilidade ("os dois são iguais hoje, mas por um contrato implícito que
nada nesta base prende"). Vale um teste que afirme a igualdade, ou a renomeação agora — antes de
haver pacote publicado em volume.

---

## 6. O que foi verificado e está conforme

Para que a lista acima não seja lida como panorama: o seguinte foi conferido e **não** produziu
achado.

- **I1** — barreira executável na publicação; item sem habilidade não vira pacote.
- **I2** — append-only garantido por gatilho + `REVOKE`, nas duas tabelas de resultado. A dimensão
  analítica por habilidade é **derivável** por junção com o pacote imutável, e o insumo
  (`item_id`, `worth`, `earned`) está gravado. `assessment_fact` fora é adiamento correto: a fatia 9
  o consome, e a derivação não se perde.
- **I4** — proveniência de item: fora da 2a **com razão registrada em §15** ("uma prova fixa não tem
  IA"), fatia-limite 6, ADR-0005. Convergindo.
- **I5** — o pacote leva só `student_token`; nome, turma e matrícula vivem em `exam_roster`. A rota
  de roster enumera duas colunas no `select`, com a razão escrita. Nenhum DTO de resultado tem campo
  de nome, e a ausência é afirmada como requisito nos dois lados.
- **Tenancy** — `organization_id` em toda tabela de domínio; `created_by_user_id` marcado como
  metadado com `comment on column` proibindo uso em autorização.
- **ADR-0012** — modo `coded` é o padrão e a transição insegura é **impossível por construção**: a
  FK composta faz `nominal → coded` falhar enquanto houver `enrollment_id`.
- **Tecnologia** — nada de Redis, broker, vector DB, Elasticsearch, GraphQL ou microserviço. O único
  desvio de §13 é `supabase-kt`, dispensado com **medição** (decisão 9 da 4a-zero) e referenciado por
  ADR-0013. Zona amarela cumprida como §5 do `rigorous.md` manda. *Ressalva de registro:*
  `CLAUDE.md:102` ainda lista `supabase-kt` na stack do Android — uma linha carregada em toda sessão
  que descreve uma biblioteca deliberadamente ausente.
- **Higiene** — nenhum `TODO`/`FIXME`/`HACK` na árvore; nenhum teste `@Ignore`/`@Disabled`; nenhum
  segredo versionado (`local.properties` fora do git, chave anônima por configuração, papel de banco
  criado sem senha na migration).
- **P1** — as duas únicas tarefas desmarcadas em `changes/archive` são desmarcações **legítimas**,
  com o motivo escrito, e ambas foram fechadas depois, em outra fatia, com ponteiro.

---

## 7. Uma coisa de processo, e ela explica quase toda a lista

**ADR-0013 está em `Status: proposto`.** É o único dos treze fora de `aceito`.

E é o ADR que governa: o pull de referência imutável, o cache endereçado por conteúdo, as três
camadas de conferência, o escopo por organização, a remoção do asset, e o adiamento do Room. As
fatias 4a-zero, 4a, 4a-cache-referencia, 4b-roster-no-aparelho e 4b-outbox foram todas construídas
sobre ele, e §16 da arquitetura **o cita como autoridade estabelecida** ("no mesmo caminho que a
visão guardada e os pacotes já usam — `DeviceSession.sair`, ADR-0013").

Pela tabela de precedência do `rigorous.md` §0, a linha 1 é "**ADR aceito**". Cinco fatias arquivadas
se apoiam num documento que, pelo critério da própria base, ainda não tem essa força. É correção de
uma linha — mas enquanto ela não acontece, "ADR" deixa de ser um estado binário, e é a natureza
binária dele que faz a precedência funcionar.

**O padrão que a lista inteira desenha.** Os achados 2.1, 2.3, 3.1, 4.6 e 4.7 são o mesmo defeito em
cinco roupas: **o que vence depois do archive não tem quem o cobre.** Dentro de uma fatia, esta base
é implacável — muta, vê falhar, reverte rodando, confere âncora, registra o que não mediu. Fechada a
fatia, o item herdado vai para uma prosa em `cobertura-*.md` ou para uma linha de "o que este roteiro
não cobre", e nenhum dos dois é lido na abertura da fatia seguinte.

§16 já tem o instrumento certo — a tabela de ponto de não-retorno, com fatia-limite, custo e dono —, e
ela **funciona**: os itens que entraram nela (roster cacheado, classe H, retenção da classe B)
avançaram e fecharam. Os que ficaram fora dela não avançaram. A diferença entre os dois grupos não é
importância; é **estar na tabela**.

---

## 8. O que deveria estar resolvido antes de a fatia 5 abrir

Ordenado por custo de adiar, não por esforço.

| # | Item | Achado | Por que antes da 5 |
|---|---|---|---|
| 1 | Conferir `package_hash` e `variant_id` contra o pacote publicado, na gravação | 2.2 | Fato append-only; hash errado não tem conserto, só revisão. Custo: uma coluna numa consulta que já roda |
| 2 | Decidir o veículo do contrato entre API e aparelho — KMP compartilhado ou ADR que registre o espelho | 2.1 | A 5 é o maior acréscimo de contrato do projeto. Quatro espelhos hoje; mais depois |
| 3 | Dar fatia-limite e dono ao **modo degradado**, em §16, e marcar o spec de `device-session` como estado provisório | 2.3 | Promessa de §10 sem dono desde que a fatia 4 fechou |
| 4 | `verificarApkSemPacote` sobre o release, e decisão escrita sobre `testReleaseUnitTest` | 3.1 | O gatilho registrado já disparou duas vezes. É o artefato que vai ao professor |
| 5 | Uma instância de `BaseDoOutbox`, e teste que exercite a topologia da produção | 3.2 | Defeito real sobre o único exemplar de correção já feita |
| 6 | `params_hash` no `PackageMeta`, e corrigir a contradição §2 × §5 da arquitetura | 4.1 | Depois da 6 custa rehashear todo pacote publicado |
| 7 | Medir `dataExtractionRules` em aparelho; se confirmar, fechar | 4.3 | Dado pessoal de menor, e a classe H já espera parecer jurídico |
| 8 | Fatia-limite e dono para: migration em deploy (4.6) e ampliação do corpus/limiar (4.7) | 4.6, 4.7 | A 5 **é** a fatia do corpus, e acrescenta schema |
| 9 | ADR-0013 → `aceito`; atualizar `deploy-api.md`, a KDoc de `ScanActivity`, o spec de `result-sync` e `CLAUDE.md:102` | 7, 4.2, 4.5, 5.1 | Registro falso custa toda verificação que se apoiar nele |

Itens 1, 5 e 9 são de horas. O item 2 é a única decisão de arquitetura da lista.
