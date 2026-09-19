## 0. Antes do primeiro commit

**Ambiente: emulador ou aparelho**, para `connectedDebugAndroidTest`. A ETAPA 5 marca isso no
cabeçalho, e **P22 vale: perguntar antes**, inclusive em modo automático.

- [x] 0.1 Perguntar antes de subir emulador ou conectar aparelho, e só então seguir. Verificar que a
      resposta está registrada nesta sessão — e **não** presumir autorização de uma sessão anterior.

      **Perguntado e autorizado nesta sessão**, em resposta ao fecho do `/opsx:propose`: "Aparelho
      conectado, prossiga. Se precisar de aparelho E de emulador, sinta-se a vontade."
      `adb devices -l` confirma **um** aparelho: `TOXSR4MR9989MBQW`, modelo **2511FPC34G**. Nenhum
      emulador foi subido — o aparelho basta, e o que não é preciso não se liga.

## 1. Commit 1 — o acessador único (5.A)

A numeração é a ordem dos commits (regra 0.3 do plano; `CLAUDE.md` regra 1, P25).

- [x] 1.1 `abrir(context)` passa a devolver **sempre a mesma instância**, guardada no companion,
      construída com `applicationContext` — **nunca** com o `Context` de uma `Activity`, que a
      manteria viva (`design.md` decisão 1). **Os três chamadores não mudam:** eles já chamam
      `abrir`. Verificar com `./gradlew :apps:android:compileDebugKotlin` e conferindo no `git diff`
      que `SessaoActivity`, `ScanActivity` e `EnvioDeResultadosWorker` não foram tocados.
- [x] 1.2 **Nenhum chamador passa a fechar a base** (`design.md` decisão 2): com uma instância por
      processo o dono é o processo, e `close()` de qualquer um derrubaria a base dos outros dois.
      Verificar por leitura do diff que nenhum `close()` foi introduzido.
- [x] 1.3 O ponto de reinício visível só a teste, e os dois testes que apagam o arquivo passam a
      chamá-lo **antes** de apagar (`design.md` decisão 3): `GravacaoNoFioPrincipalInstrumentedTest` e
      `SegundoMembroInstrumentedTest`, os dois com `deleteDatabase("outbox.db")` em `@Before`/`@After`.
      **Não** se cria método de apagamento em massa em `ResultadosPendentes` para evitar isso —
      decisão 9. Verificar que os dois voltam a passar no aparelho.

      **A decisão 3 previu a interação e subestimou o tamanho dela, e fica escrito (P7).**
      Reiniciar antes de apagar não bastou: `SegundoMembroInstrumentedTest` chamava `base.close()`
      num `finally` depois de **cada** operação de `gravarPendente` e `quantosPendentes`. Inofensivo
      enquanto cada chamador tinha a própria instância; com o acessador único, fecha **a** instância
      do processo, e a chamada seguinte recebe a mesma referência já fechada —
      `IllegalStateException: Database is closed`, 2 cenários caídos de 22 em
      2026-09-19T00:08:34Z.

      **O conserto foi o chamador parar de fechar, e não `abrir` reconstruir ao achar a instância
      fechada.** A segunda opção esconderia um chamador fechando a base compartilhada, que é
      exatamente a classe de defeito que esta mudança existe para tornar impossível — e a decisão 2
      já diz que nenhum chamador fecha. O teste passa a exercitar a mesma topologia que os três
      chamadores de produção, que é o que a 5.A inteira persegue.

      `connectedDebugAndroidTest` do pacote `outbox`, 2026-09-19T00:09:45Z–00:10:08Z: **22 de 22
      verdes**, no aparelho **2511FPC34G / Android 16**.

## 2. Commit 2 — o teste que mede a topologia da produção (5.A)

O cenário existente constrói a base com nome próprio e guarda a referência — **exercita uma topologia
que não é a da produção**, e é por isso que o defeito atravessou.

- [x] 2.1 Cenário novo que afirma, **pelo caminho de produção**, que duas chamadas a `abrir` devolvem
      **a mesma instância**. Verificar que a asserção é sobre identidade de referência, e não sobre o
      dado — duas instâncias distintas sobre o mesmo arquivo também leriam a mesma linha, e uma
      asserção sobre conteúdo passaria com o defeito presente.
- [x] 2.2 Cenário novo que afirma que **uma escrita pelo caminho do worker e uma leitura pelo caminho
      da tela não se atropelam**. Verificar com `connectedDebugAndroidTest` verde.
- [x] 2.3 **Ver falhar.** A mutação é **restaurar o `build()` por chamada**. Rodar a suíte
      instrumentada sob a mutação e registrar **quais** cenários caem, contra esta tabela:

      | Cenário | Deve cair? |
      |---|---|
      | duas aberturas devolvem a mesma instância | **sim** |
      | escrita do worker e leitura da tela convivem | **sim** |
      | `ApagamentoLocalInstrumentedTest` (base própria) | **não** |
      | `OutboxEmRepousoInstrumentedTest` (base própria) | **não** |
      | `GravacaoNoFioPrincipalInstrumentedTest` | **não** |

      **Os três "não" são o ponto: eles constroem a própria base e continuam certos sobre o que
      medem. Se caírem, a mutação não isolou nada** — e a regra de parada dispara (`design.md`
      decisão 12).

      **PREVISTO 2 · REAL 1. A regra de parada disparou**, e o conjunto real fica escrito ao lado do
      previsto (P7, P12, P14). `connectedDebugAndroidTest` do pacote `outbox`,
      2026-09-19T00:12:09Z–00:12:33Z, aparelho **2511FPC34G / Android 16**, 25 cenários, 1 caído:

      | Cenário | Previsto | Real |
      |---|---|---|
      | duas aberturas devolvem a mesma instância | **sim** | **caiu** |
      | escrita do worker e leitura da tela convivem | **sim** | **não caiu** |
      | `ApagamentoLocalInstrumentedTest` (base própria) | não | não caiu |
      | `OutboxEmRepousoInstrumentedTest` (base própria) | não | não caiu |
      | `GravacaoNoFioPrincipalInstrumentedTest` | não | não caiu |

      **A condição de parada explícita do plano foi cumprida:** nenhum dos três "não" caiu, então a
      mutação **isolou** o que devia. O desvio está na linha 2.

      **O que ele significa, e não é "o teste está fraco".** Vinte escritas e vinte leituras por
      **duas instâncias distintas** sobre o mesmo arquivo convivem sem estourar: o SQLite do
      aparelho aguenta duas conexões benignas. A contenção que produz
      `SQLiteDatabaseLockedException` na vida real vem do **acúmulo** de instâncias — uma por
      rotação de tela, mais uma por passada do worker — sob rede intermitente com a câmera aberta, e
      isso este cenário não reproduz. A previsão do plano tratou "duas conexões" e "muitas conexões
      em disputa" como a mesma coisa, e são duas.

      **Consequência aceita, e escrita no próprio teste:** a guarda da unicidade é **só**
      `duas_aberturas_devolvem_a_mesma_instancia`. O cenário de convivência continua valendo pelo
      que de fato afirma — que os dois caminhos de produção não se bloqueiam —, e a KDoc dele agora
      diz que ele **não** é prova da instância única. **O instrumento não foi consertado para caber
      na previsão** (regra 0.5): nenhuma asserção foi endurecida, nenhum laço foi aumentado até
      quebrar.

      **Não medido, e fica dito (P8):** que o acúmulo de instâncias sob contenção real produz a
      exceção. Seria preciso rede intermitente com a câmera aberta em aparelho, e isso não foi
      feito.

## 3. Commit 3 — o estado inconstruível (5.B)

**Não se funde com os commits 1 e 2** (`design.md` decisão 10): são defeitos diferentes, com mutações
diferentes.

- [ ] 3.1 O `short_id` entra no gate de `onCreate`, ao lado de `organizacao` e `contentHash`:
      ausente, a câmera **não abre**, com motivo **próprio** — distinto dos cinco que o gate já
      distingue (`design.md` decisões 4 e 5). `MotivoDaBarragem` **não** ganha entrada nova.
- [ ] 3.2 `prova` e `organizacao` deixam de ser nuláveis no campo, e `gravar` perde os dois
      `?: return`. **O caminho silencioso deixa de existir em vez de ser tratado.** Verificar com
      `./gradlew :apps:android:compileDebugKotlin` e conferindo que nenhum `?: return` novo apareceu
      em `gravar`.
- [ ] 3.3 **O `short_id` ausente não ganha valor padrão nem é derivado de `examPackage.meta.examId`**
      (`design.md` decisão 6). Verificar por leitura do diff que `meta.examId` não aparece em
      `ScanActivity`.
- [ ] 3.4 Cenário novo: **sem o identificador da prova, o escaneamento não abre, e o motivo é
      próprio**. Verificar que a asserção confere **o motivo**, e não só que a câmera não abriu — e
      que o motivo é distinguível dos cinco do gate.
- [ ] 3.5 **Ver falhar.** A mutação é **restaurar o campo nulável e o `?: return`**. **Conjunto
      previsto:** cai o cenário novo — "sem o identificador da prova, o escaneamento não abre, e o
      motivo é próprio" — e **só ele**. Nenhum cenário de apuração, de gravação ou de gate cai,
      porque nenhum deles passa por esse caminho. Registrar o conjunto real ao lado do previsto.

## 4. Commit 4 — o spec passa a dizer o que o código faz (5.C)

- [ ] 4.1 O delta de `result-sync` troca "token vazio" por **ausente**, nos dois pontos, **com a razão
      junto, em uma linha**: ela hoje vive só em comentário de migration e KDoc, e não no `design.md`
      de nenhuma fatia. Verificar com `openspec validate o-pendente-nao-se-perde-no-aparelho --strict`
      e conferindo que o bloco `MODIFIED` carrega o requisito **inteiro e atual** — incluindo o texto
      de conferência de proveniência que a ETAPA 4 sincronizou (`design.md` decisão 11).
- [ ] 4.2 **Nenhuma linha de código neste commit.** É texto de spec, e o código já se comporta assim
      em três pontos. Verificar no `git diff --stat`.

## 5. O registro

Regra 0.8 do plano: nenhuma etapa fecha com "passou".

- [ ] 5.1 `docs/cobertura-o-pendente-nao-se-perde-no-aparelho.md` com, no mínimo: os **dois**
      conjuntos reais ao lado dos previstos; o comando cheio de cada execução (P5) e o `timestamp` de
      cada uma (P2, P3); o aparelho ou emulador em que a suíte instrumentada rodou; como foi visto
      falhar (P9); e o que ficou sem verificação (P8).
- [ ] 5.2 Uma seção da cobertura nomeando **o que o emulador não reproduz**: rede intermitente com a
      câmera aberta, que é a condição em que `SQLiteDatabaseLockedException` nasce. O cenário afirma
      que os dois caminhos convivem; ele **não** reproduz a corrida real (`design.md`, *Risks*).
      Verificar que a seção diz o que ficou por medir, e não só que houve limite.
- [ ] 5.3 No `docs/auditoria-2026-09-18-antes-da-fatia-5.md`, os achados **3.2**, **3.3** e **4.2**
      deixam de estar abertos, **sem apagar o texto antigo** (P7), com o ponteiro para esta mudança.
- [ ] 5.4 Verificação final: `./gradlew build` com `timestamp`, `connectedDebugAndroidTest` verde
      **depois** da reversão das duas mutações e com o aparelho nomeado, `grep -rn "MUTACAO"` fora de
      `build/` em `0`, e `openspec validate o-pendente-nao-se-perde-no-aparelho --strict`.
