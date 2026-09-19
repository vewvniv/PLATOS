# Cobertura — `o-pendente-nao-se-perde-no-aparelho`

**Achados que fecha:** 3.2, 3.3 e 4.2 da `docs/auditoria-2026-09-18-antes-da-fatia-5.md`
**Etapa:** 5 de `docs/plano-de-correcao-antes-da-fatia-5.md`
**Base:** `vewvniv/servidor-confere-a-proveniencia-do-resultado` em `53fb0df` (a etapa 4, arquivada)
**Aparelho:** **2511FPC34G** (`TOXSR4MR9989MBQW`), **Android 16**. Nenhum emulador foi subido.
**Ambiente:** perguntado e autorizado nesta sessão (P22). Nada foi instalado nem alterado.

Os três achados são sobre o mesmo dado: o resultado pendente no aparelho, que é o **único exemplar de
uma correção já feita**.

---

## 1. A linha de base estava vermelha, e não por causa desta mudança

Antes de qualquer alteração, `./gradlew :apps:android:connectedDebugAndroidTest` em
`2026-09-19T00:03:49Z`: **78 cenários, 1 caído** —
`SessaoEmRepousoInstrumentedTest.aCredencialNaoEstaEmClaro`, com
`FileNotFoundException: .../shared_prefs/platos-sessao-cifrada.xml`.

**Isolada, a classe passa** — conferido duas vezes, `00:05:08Z` e `00:05:30Z`. **Na suíte cheia,
caía de forma reprodutível** — conferido duas vezes, `00:03:49Z` e `00:06:02Z`. É **interferência de
ordenação entre cenários**, não defeito do produto.

**Depois desta mudança ela parou de cair** — três execuções seguidas em `00:18:48Z`, `00:19:55Z` e
`00:20:38Z`, todas **81 de 81 verdes**. **Isto não é conserto, e não deve ser lido como tal.** Esta
mudança acrescentou uma classe de teste instrumentado, o que altera a ordem de execução; a
fragilidade que produzia a falha continua inteira e pode voltar na próxima classe que alguém
acrescentar.

**O item está aberto, com dono e fatia-limite** — §6 abaixo. Ele é uma **afirmação de segurança**
("a credencial não está em claro"), e por isso entra na tabela do §16 (regra 0.4 do plano, P19, P20).

---

## 2. Como foi visto falhar (P9) — duas mutações

**Oráculo:** os XML de `build/outputs/androidTest-results/` e `build/test-results/`, e **não** a linha
final do Gradle.

### Mutação A — o `build()` por chamada, restaurado (5.A)

`./gradlew :apps:android:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.platos.android.outbox`
`2026-09-19T00:12:09Z–00:12:33Z` · **25 cenários, 1 caído**

| Cenário | Previsto | Real |
|---|---|---|
| duas aberturas devolvem a mesma instância | **sim** | **caiu** |
| escrita do worker e leitura da tela convivem | **sim** | **não caiu** |
| `ApagamentoLocalInstrumentedTest` (base própria) | não | não caiu |
| `OutboxEmRepousoInstrumentedTest` (base própria) | não | não caiu |
| `GravacaoNoFioPrincipalInstrumentedTest` | não | não caiu |

**PREVISTO 2 · REAL 1. A regra de parada disparou.**

**A condição de parada explícita do plano foi cumprida:** os três "não" são o ponto — eles constroem
a própria base e continuam certos sobre o que medem —, e **nenhum deles caiu**. A mutação isolou o
que devia. O desvio está na linha 2.

**O que ele significa, e não é "o teste está fraco".** Vinte escritas e vinte leituras por **duas
instâncias distintas** sobre o mesmo arquivo convivem sem estourar: o SQLite do aparelho aguenta duas
conexões benignas. A contenção que produz `SQLiteDatabaseLockedException` na vida real vem do
**acúmulo** de instâncias — uma por rotação de tela, mais uma por passada do worker — sob rede
intermitente com a câmera aberta. A previsão do plano tratou "duas conexões" e "muitas conexões em
disputa" como a mesma coisa, e são duas coisas.

**O instrumento não foi consertado para caber na previsão** (regra 0.5). Nenhuma asserção foi
endurecida, nenhum laço foi aumentado até quebrar. O que mudou foi a **KDoc do cenário**, que agora
diz que ele **não** é prova da instância única — a guarda da unicidade é **só**
`duas_aberturas_devolvem_a_mesma_instancia`, e ela é `assertSame`, sobre identidade de referência.

**Por que a asserção é de identidade e não de dado:** duas instâncias distintas sobre o mesmo arquivo
leem a mesma linha. Qualquer asserção sobre conteúdo passaria com o defeito presente, em verde, para
sempre.

### Mutação B — a guarda do `short_id` neutralizada (5.B)

`./gradlew :apps:android:testDebugUnitTest` · `2026-09-19T00:17:56Z–00:18:06Z` · **308 cenários, 1
caído**

| Cenário | Previsto | Real |
|---|---|---|
| sem o identificador da prova, o escaneamento não abre e o motivo é próprio | **sim** | **caiu** |
| com o identificador da prova, a mesma entrada abre | não | não caiu |
| sem pacote conferido, o motivo é o outro | não | não caiu |
| o motivo é distinguível dos cinco do gate | não | não caiu |
| os outros 304 cenários de apuração, gravação e gate | não | nenhum caiu |

**PREVISTO 1 · REAL 1.** A previsão bateu.

A mutação aplicada é o que resta do "campo nulável e `?: return`" depois de a decisão sair da
`Activity`: neutralizada a guarda, a câmera volta a abrir sem saber de qual prova é — exatamente o
estado em que `gravar` tinha o `return` silencioso.

**A guarda de vacuidade é executada:** o cenário "com o identificador da prova, a mesma entrada abre"
usa **a mesma entrada** com o `short_id` presente. Sem ela, "recusou" poderia vir do pacote, da
organização ou de qualquer outra coisa do mesmo caminho.

### A reversão, conferida rodando (P10)

As duas mutações foram conferidas **no arquivo** antes de a suíte rodar — `grep -rn "MUTACAO"` fora
de `build/` acusou a linha em cada caso, e o `BUILD FAILED` confirma que a compilação as pegou.
Depois de cada reversão: `grep` em **0**, e a suíte verde (`00:13:45Z`, 25 de 25; `00:18:18Z`, 308 de
308).

---

## 3. O que a 5.A alcançou além do previsto, e por quê

O `design.md` decisão 3 previu que o acessador único alcançaria os dois testes que apagam o arquivo,
e **subestimou o tamanho**. Reiniciar antes de apagar não bastou: `SegundoMembroInstrumentedTest`
chamava `base.close()` num `finally` depois de **cada** operação. Inofensivo enquanto cada chamador
tinha a própria instância; com o acessador único fecha **a** instância do processo, e a chamada
seguinte recebe a mesma referência já fechada — `IllegalStateException: Database is closed`, 2 de 22
em `00:08:34Z`.

**O conserto foi o chamador parar de fechar, e não `abrir` reconstruir ao achar a instância
fechada.** A segunda opção esconderia um chamador fechando a base compartilhada, que é exatamente a
classe de defeito que esta mudança existe para tornar impossível. O teste passa a exercitar a mesma
topologia que os três chamadores de produção — que é o que a 5.A inteira persegue.

---

## 4. A decisão saiu da `Activity`, e isso não é refatoração oportunista

`decidirAbertura` é código novo fora de `ScanActivity`. A razão é que a tarefa 3.4 exige conferir **o
motivo** da recusa (`rigorous.md` §3), e a decisão morava dentro de `onCreate`, onde **não tinha um
único cenário** — nem o requisito de spec "Sem pacote conferido, a câmera não abre", que já existia,
era exercitado por nada. Foi a vinte linhas dessa decisão que a auditoria achou o caminho silencioso.

É a mesma razão que a KDoc de `ResultadosEmRoom` já dá para ele receber o `Dao` e não um `Context`: a
fronteira com o Android fica num lugar só, e quem **decide** continua ao alcance de teste.

**`MotivoDaBarragem` não ganhou entrada** (decisão 5): aquele enum decide **antes** do `Intent`, em
`SessaoActivity`, e não tem como saber que um extra vai faltar — quem monta o `Intent` é ele próprio.
A distinção entre os dois conjuntos é **verificada por asserção**, e as duas frases são diferentes: a
de prova não identificada **não** manda baixar a prova de novo, porque o pacote está no lugar.

---

## 5. O erro de empacotamento dos commits, dito e não apagado

Os artefatos de planejamento entraram no **commit 1**, junto com a implementação da 5.A: eles estavam
sem rastreio quando a branch foi criada, e o `git add -A` do commit 1 os varreu. A convenção do
repositório é um commit `propose(<nome>)` próprio (`64678ee`, `aedb6ff`).

**A propriedade substantiva se manteve:** a spec da 5.B entrou no commit 1 e a implementação dela só
no commit 3 — contrato antes do consumidor, na ordem (regra 1 do `CLAUDE.md`, P25). O que se perdeu
foi a separação em unidades lógicas.

A mensagem do commit 4 foi corrigida para dizer o que ele de fato carrega, em vez de descrever um
delta que já estava no commit 1. **Um commit cuja mensagem afirma mais do que ele entrega é registro
falso**, e registro falso custa toda verificação que se apoiar nele.

---

## 6. Achado novo, com dono e fatia-limite (regra 0.4 do plano)

**`SessaoEmRepousoInstrumentedTest.aCredencialNaoEstaEmClaro` depende da ordem de execução da suíte
instrumentada, e pode parar de verificar o que afirma sem nada acusar.**

- **O que.** O cenário apaga os `shared_prefs` em `@Before` e espera o keyset do Tink reaparecer. Na
  suíte cheia, em `2026-09-19T00:03:49Z` e `00:06:02Z`, o arquivo não reaparecia e o cenário caía.
  Isolado, passa. Depois de esta mudança acrescentar uma classe instrumentada, parou de cair — **por
  mudança de ordem, e não por conserto**.
- **Por que importa.** É uma **afirmação de segurança**: "o token não aparece como texto legível no
  armazenamento do aplicativo". Um cenário que cai por interferência é um cenário que ninguém lê como
  segurança — lê-se como flaky e ignora-se. E um que passa por acidente de ordem pode voltar a cair,
  ou pior: continuar verde sem estar medindo.
- **Dono:** `apps/android/src/androidTest/.../SessaoEmRepousoInstrumentedTest.kt`.
- **Fatia-limite:** **antes do lançamento**. Não bloqueia a fatia 5.
- **Não foi consertado aqui, e a razão é a regra 0.4:** achado novo no meio de uma etapa vira item
  escrito, nunca implementação silenciosa (P19). Por ser de segurança, entra na tabela do §16 (P20).

---

## 7. O que **não** foi verificado (P8)

- **Que o acúmulo de instâncias sob contenção real produz `SQLiteDatabaseLockedException`.** É o que
  a mutação A mostrou não estar coberto: duas conexões benignas convivem. Reproduzir exigiria rede
  intermitente com a câmera aberta, muitas rotações de tela e passadas do worker em disputa — e isso
  **não foi feito**.
- **Que a `Activity` real recusa abrir.** O cenário da 5.B exercita `decidirAbertura`, que é a decisão
  inteira, e não `ScanActivity.onCreate` lançada por `Intent`. A ligação entre as duas é uma linha de
  `onCreate`, lida mas **não** exercitada por nenhum teste — nem antes desta mudança.
- **Que a tela certa aparece.** As frases são verificadas como dados, por `frasePara`. Nenhum teste de
  Compose afirma que `EscaneamentoNaoAbreScreen` as desenha.
- **A variante `release`.** Tudo aqui é `debug`, como toda a suíte — é o achado 3.1, que é da etapa 7.

---

## 8. A verificação final

| O quê | Comando | Quando | Desfecho |
|---|---|---|---|
| Unitários do aparelho | `./gradlew :apps:android:testDebugUnitTest` | 2026-09-19T00:18:18Z | 308 testes, 0 caídos |
| Instrumentados | `./gradlew :apps:android:connectedDebugAndroidTest` | 2026-09-19T00:20:38Z | 81 testes, 0 caídos |
| Mutação A | idem, pacote `outbox` | 2026-09-19T00:12:09Z | 1 caído; previsto 2 |
| Mutação B | `:apps:android:testDebugUnitTest` | 2026-09-19T00:17:56Z | 1 caído; previsto 1 |

As contagens saem dos XML, e não da linha final do Gradle.
