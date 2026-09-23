## Context

A motivação e a tabela dos cinco itens estão em `proposal.md` (Why). Aqui fica só o estado que decide
cada abordagem, lido em 2026-09-23 sobre `3e7df3b`.

**Item 1 — a guarda do APK.** `VerificarApkSemPacoteTask` (`apps/android/build.gradle.kts`, a partir
da linha 312) abre cada `.apk` que recebe, procura asset JSON com a **forma** de pacote (`"answer_key"`
e `"min_renderer_version"`), e tem guarda de vacuidade: sem APK nenhum, reprova. O registro liga a
tarefa só ao debug: `dependsOn("assembleDebug")` (`:365`) e `apks.from(outputs/apk/debug)` (`:368`). O
`./gradlew build` **já monta o release** — o log da linha de base da 7.1 traz
`:apps:android:compileReleaseArtProfile` —, sem assinatura (não há bloco `buildTypes`).

**Item 2 — a variante de teste do release.** Medido nesta proposta, sem tocar o repositório, com um
*init script* no scratchpad (`beforeVariants(selector().withBuildType("release")) { enableUnitTest =
true }`): sem ele, `:apps:android:testReleaseUnitTest --dry-run` responde "task 'testReleaseUnitTest'
not found"; com ele, a tarefa existe, e `--rerun-tasks` a roda verde, **308 de 308**,
`18:58:38Z`–`18:59:42Z`, 40 de 40 tasks. Não há `src/release`, e nenhum código de `apps/android/src/main`
ramifica em `BuildConfig.DEBUG` (conferido por `grep`): hoje as duas variantes testam o mesmo código.

**Item 3 — a `concurrency`.** `ci.yml:7-10`, no nível do workflow:
`group: ${{ github.workflow }}-${{ github.ref }}`, `cancel-in-progress: true`. Todo push novo numa
branch com PR cancela a execução em curso **inteira**, `paridade` incluída.

**Item 4 — a credencial.** `SessaoEmRepousoInstrumentedTest` apaga os dois arquivos de preferência em
`@Before`, cria uma `SessaoGuardadaAndroid`, espera o arquivo cifrado aparecer (sem afirmar), fotografa
os bytes, grava a credencial e espera os bytes mudarem. **Uma outra classe da mesma suíte usa o mesmo
arquivo no mesmo processo:** `SegundoMembroInstrumentedTest` (pacote `outbox`) cria sua própria
`SessaoGuardadaAndroid` no `@Before` e, no `@After`, chama `apagarCredencial()` — que grava com
`apply()`, **fora da linha de execução**. A hipótese, **suposta e não medida** (P6): uma escrita
pendente dessa instância antiga cai no arquivo depois de `SessaoEmRepouso` o ter apagado — ou a troca
de arquivo que `SharedPreferences` faz ao gravar (renomeia o atual para `.bak` e escreve outro) abre
uma janela em que as leituras de sondagem do teste (`cifrado.exists()` seguido de
`cifrado.readBytes()`) não são atômicas. As duas dependem de tempo, e portanto de ordem — o que bate
com "cai na suíte cheia, passa isolada" e com "parou de cair quando uma classe foi acrescentada". O
`FileNotFoundException` registrado em `2026-09-19T00:03:49Z` não tem linha de pilha guardada.

**Item 5 — os testes que não rodam.** `ApiPlatosPacoteTest.kt:106` e `:174` são
`fun … () = runBlocking { … assertInstanceOf(Retorno.SemRede::class.java, retorno) }`, e
`assertInstanceOf` devolve o objeto: as duas funções devolvem `Retorno$SemRede` (conferido por `javap`
na 7.3), e o Jupiter não as descobre. As suítes JVM usam `@Test` simples e nada mais — **zero**
arquivos com `@ParameterizedTest`, `@Nested`, `@DisplayName`, `@RepeatedTest`, `@TestFactory`,
`@TestTemplate`, `@Disabled` ou `org.junit.Test` (conferido por `grep`); 60 arquivos com
`kotlin.test.Test` e 29 com `org.junit.jupiter.api.Test`. O instrumento da 7.3 contava `@Test` **no
texto**, e falhou de dois modos antes de ser acreditado (`cobertura-o-fio-preso-nos-dois-lados.md`
§6): casava `hostname=` em vez de `name=`, e não viu um `@org.junit.jupiter.api.Test` qualificado.

## Goals / Non-Goals

**Goals:**

- O APK de release passa pela mesma guarda de pacote embutido que o de debug, e a ausência de um
  deles reprova.
- `./gradlew build` roda a suíte de unidade do aplicativo também sobre o release.
- Nenhuma execução da `paridade` é cancelada no meio por um push mais novo.
- `aCredencialNaoEstaEmClaro` tem o **mesmo desfecho** isolado, na suíte cheia e na ordem que a
  derrubava — e, com o defeito de produto plantado, cai **pelo mesmo motivo** nos três.
- Todo método declarado com `@Test` nas suítes JVM dos três módulos tem resultado no relatório, ou o
  build reprova nomeando-o.

**Non-Goals:**

- Verificar o artefato de loja. Hoje o release sai como APK sem assinatura; quando vier assinatura,
  bundle ou R8 (fatia comercial), o artefato que vai à loja pode ser outro, e esta guarda não o veria
  (decisão 11).
- Tornar a suíte instrumentada independente de ordem em geral. Só o cenário da credencial é item; se a
  medição achar a mesma fragilidade em outro, ela vira item escrito (regra 0.4).
- Cobrir `jsNodeTest`, `androidTest` e `buildSrc` com a guarda de testes executados (decisão 7).

## Decisions

### 1. A guarda do APK recebe as duas variantes, e a vacuidade é por variante

Uma tarefa só, `verificarApkSemPacote` — o nome que o ADR-0013 e a spec citam —, com `dependsOn` de
`assembleDebug` **e** `assembleRelease`, e os APKs de `outputs/apk/debug` e `outputs/apk/release`. A
guarda de vacuidade passa a ser **por variante**: sem APK de release, reprova dizendo "nenhum APK de
release"; sem de debug, idem. A guarda de hoje, `require(apks.files.any { … })`, aplicada à união,
passaria com o debug sozinho — e o defeito que este item fecha é justamente o release sem conferência.
A mensagem de achado continua nomeando `apk!entrada`, e o nome do APK diz a variante.

**Alternativa recusada:** duas instâncias da tarefa, uma por variante. Funcionaria, e mudaria o nome
que três documentos citam, sem nada a ganhar na verificação.

### 2. A variante de teste do release liga por `beforeVariants`, no `build.gradle.kts`

Decisão do mantenedor em 2026-09-23 (proposal). O mecanismo é o que a medição usou —
`androidComponents { beforeVariants(selector().withBuildType("release")) { … } }` —, escrito no
`build.gradle.kts` do aplicativo e não em *init script*. Se a forma tipada do Kotlin DSL for outra que
a dinâmica do Groovy usada na medição (por exemplo, a propriedade vir de outra interface no AGP 9.3.1),
vale a que o AGP não marca como depreciada, e isso fica dito na tarefa. `./gradlew build` passa a
incluir `testReleaseUnitTest` pelo `test` agregado — **isso se confere no log, e não se supõe**.

### 3. A `concurrency` vai para cada job, e cada job tem o seu grupo

```yaml
build:    concurrency: { group: ${{ github.workflow }}-${{ github.ref }}-build,    cancel-in-progress: true }
web:      concurrency: { group: ${{ github.workflow }}-${{ github.ref }}-web,      cancel-in-progress: true }
paridade: concurrency: { group: ${{ github.workflow }}-${{ github.ref }}-paridade, cancel-in-progress: false }
```

(no arquivo, em bloco, e não em linha). **O grupo tem de carregar o nome do job.** Com o mesmo grupo
nos três, `build` e `web` da **mesma** execução disputariam o grupo, e um cancelaria o outro.

**O que isto garante, e o que não.** Com `false`, a `paridade` de uma execução nova espera a antiga
terminar, em vez de cancelá-la — nunca há "passo marcado como falha sem erro no log", que é o
incidente de P15. Mas a `paridade` depende de `web` (`needs: web`): se o `web` da execução antiga for
cancelado **antes** de terminar, a `paridade` dela não chega a começar. Isso é o desejado — ela seria
sobre um commit já superado, e a da execução nova cobre o novo —, e fica dito para ninguém ler
"`paridade` não cancelada" como "`paridade` de todo commit".

**Tipo da afirmação: conferida por leitura, e não medida** (P6; o plano diz o mesmo). Medir exigiria
dois pushes em sequência e observar a fila; o custo é tempo de runner, e o plano não pede. O que se
verifica é o YAML: `safe_load` sem `concurrency` no topo, e os três grupos distintos com os valores
acima.

### 4. A credencial: medir antes, e o conserto vai onde a medição apontar

Nenhum conserto é escolhido aqui, porque a causa não foi medida (P6). O que se decide é o método:

1. **A linha de base**: `connectedDebugAndroidTest` cheio e a classe isolada — pelo registro da
   ETAPA 5, as duas passam hoje.
2. **Reproduzir**, antes de mexer em nada, nesta ordem, parando na primeira que cair:
   a. a ordem que a hipótese aponta — `SegundoMembroInstrumentedTest` e em seguida
      `SessaoEmRepousoInstrumentedTest`, por
      `-Pandroid.testInstrumentationRunnerArguments.class=<A>,<B>`;
   b. a suíte cheia, repetida (três vezes);
   c. a suíte cheia sem as classes que entraram depois de `2026-09-19T00:06Z`, que é a composição em
      que a falha se reproduzia.
3. **Diagnosticar pela mensagem e pela pilha** (P12), e não pela contagem.
4. **Consertar onde a pilha apontar**, no teste — no que sonda o arquivo ou no que deixa escrita
   pendente —, sem afrouxar asserção e sem esticar espera (P11).

**Se nenhuma das três reproduzir**, a mudança **para** neste item (regra de parada, decisão 9): não se
conserta às cegas uma causa suposta, e o item volta ao mantenedor com as execuções escritas. **Se a
pilha apontar defeito do produto** (`SessaoGuardadaAndroid`), também para: deixaria de ser correção de
teste.

**Ver falhar, depois do conserto** — a propriedade que o plano pede, e que hoje não vale:

| Execução | Sem defeito | Com o token gravado em claro (`MUTACAO` no produto) |
|---|---|---|
| a classe isolada | verde | cai **em** "o token aparece como texto legivel" |
| a ordem que reproduziu (2) | verde | cai **em** "o token aparece como texto legivel" |
| a suíte cheia | verde, todas | cai **só** esse cenário, **pela mesma mensagem** |

O defeito plantado é o do cenário da spec — o token legível no armazenamento —, e não outro: um
defeito que fosse pego pela guarda de preparo (o arquivo que não aparece) apontaria para o lugar
errado, e o próprio teste já diz isso num comentário.

### 5. Os dois testes passam a devolver `Unit`, e cada um é visto falhar pela primeira vez

`= runBlocking<Unit> { … }` — o tipo explícito coage o valor da última expressão, e a intenção fica no
código. **Alternativa recusada:** trocar para corpo em bloco. Muda mais linhas e não diz mais.
Nenhuma asserção muda.

Depois, **cada um** visto falhar: o tipo esperado trocado por outro `Retorno` em cada teste, um de
cada vez, e **só aquele** cenário cai, com a mensagem de tipo. É o primeiro vermelho que eles terão
tido: um teste que nunca rodou também nunca foi visto falhar.

### 6. A guarda de testes executados lê a declaração do bytecode, e casa pelo nome

**O que ela afirma:** todo método declarado com `@Test` numa classe de teste compilada tem **pelo menos
um** resultado no relatório JUnit da tarefa que a roda. Se não tem, ela reprova nomeando a classe, o
método e a tarefa.

- **A declaração vem do bytecode**, pela anotação de tempo de execução `org.junit.jupiter.api.Test`,
  lida por reflexão sobre `testClassesDirs` e o `classpath` da própria tarefa de teste, com classes
  carregadas **sem inicializar**. Um `@Test` qualificado, um `import … as`, um comentário no meio — tudo
  isso some na compilação, e o que sobra é a anotação. É o que resolve o segundo modo de falha da
  contagem por texto, que o plano manda "resolver ou declarar". O tipo de retorno **não** filtra: é
  exatamente o método que devolve valor que precisa ser contado. **Que `kotlin.test.Test` compila para
  `org.junit.jupiter.api.Test` nas suítes JVM desta árvore é conferido na implementação** (por `javap`),
  e não suposto.
- **O resultado vem do XML** que a tarefa de teste escreve: `classname` e `name` de cada `testcase`, com
  o nome normalizado (sem os `()` e sem um sufixo `[…]`, como o `[jvm]` do KMP). Casar pelo **nome**, e
  não por contagem, é o que deixa a guarda dizer **qual** teste faltou — o plano pede "ver a guarda
  nomear os dois". É possível porque não há teste parametrizado nem `@DisplayName` (Context); se um dia
  houver, a guarda falha fechada (ver abaixo) e a forma nova é ensinada com o canário dela.
- **Oráculo independente (P4):** ela não usa a descoberta do Jupiter, que é o que ela julga; lê a
  anotação que o compilador gravou.
- **Piso (P13):** zero métodos declarados numa tarefa, ou zero relatórios, reprova — uma suíte inteira
  que trocasse de anotação passaria em qualquer comparação.
- **Falha fechada:** classe que não carrega, ou relatório que não se lê, reprova com o nome, em vez de
  ser pulado.
- **Onde mora:** uma classe de tarefa em `buildSrc/src/main/kotlin/com/platos/build/`, ao lado das que
  já estão lá, registrada nos três módulos — uma instância **por tarefa de teste**, ligada ao `check`,
  rodando depois da tarefa que julga. Uma só implementação para os três (regra 7 do `CLAUDE.md`).

**Alternativas recusadas:**

- **Contar `@Test` no texto do fonte**, como o instrumento da 7.3. Já falhou dos dois modos registrados.
- **Subir o JUnit** para uma versão que acuse o método inválido na descoberta. Troca de dependência do
  catálogo é mudança de ambiente (P22), e cobriria uma causa, e não a propriedade.
- **Um conferidor Node em `tools/parity/`**, no molde da 7.1. Os relatórios só existem no job `build`
  do CI, e não no `web`, onde os conferidores moram; e o bytecode pede JVM.

### 7. Quais tarefas a guarda cobre, e quais não

Toda tarefa do tipo `Test` que o `build` roda em `apps/api`, `apps/android` e `packages/domain` — **por
leitura**: `test` da API; `testDebugUnitTest` e `testReleaseUnitTest` do aplicativo (esta, a partir do
item 2); `jvmTest` e `testAndroidHostTest` do domínio. **A lista real se confere na implementação**, no
grafo, e não nesta página. O plano pede "as três suítes JVM"; cobrir as duas variantes do aplicativo e
o `testAndroidHostTest` não é alargar a propriedade, é não abrir exceção por executor para o mesmo
fonte.

**Fora, e por quê:** `jsNodeTest` não produz bytecode JVM para ler; `androidTest` roda só no emulador,
no job `paridade`; os testes de `buildSrc` são outra build, que o `build` não alcança. Os três ficam
escritos como limite na cobertura.

### 8. A ordem dos commits

1. **`ci.yml` — a `concurrency` por job** (item 3). Independente dos outros, e o primeiro a entrar
   porque protege a `paridade` dos pushes desta própria mudança.
2. **A guarda do APK sobre o release** (item 1).
3. **A variante de teste do release** (item 2).
4. **A guarda de testes executados — e ela nasce vermelha** (item 5), sobre a árvore real, sem nada
   plantado, no molde do commit 1 da 7.3. Vem **depois** do 3, para já cobrir `testReleaseUnitTest`.
   O primeiro vermelho tem de nomear **exatamente** os dois métodos de `ApiPlatosPacoteTest`, em
   `testDebugUnitTest` e em `testReleaseUnitTest` — quatro linhas —, e mais nada. Outro nome, outra
   tarefa, ou um a menos: parar.
5. **Os dois testes passam a rodar** (item 5).
6. **A credencial** (item 4), depois da medição da decisão 4.

**A medição de entrada vem antes de todos**, e sem commit de código: ver o buraco dos itens 1 e 2 (um
asset com forma de pacote em `src/release/assets/` passa pela guarda de hoje; um teste que só cai no
release deixa o `build` verde), e a reprodução do item 4. É o molde da 7.1 e da 7.3: a afirmação da
auditoria entra no registro medida.

### 9. Regra de parada — o instrumento não se conserta para caber na previsão

Regra 0.5 do plano, e ela vale sobre toda esta mudança:

> Toda etapa prevê **qual conjunto de cenários deve cair** sob a mutação. Se o conjunto real for
> diferente do previsto — **mais, menos, ou outros** —, **pare**. Não conserte o instrumento, não
> afrouxe a asserção, não ajuste a previsão em silêncio. Escreva o conjunto real ao lado do previsto
> e diga o que ele significa (P7, P12, P14).

Os pontos em que ela morde, nesta mudança: o primeiro vermelho da guarda de testes (decisão 8.4); cada
"ver o buraco" da medição de entrada; a reprodução e o conserto da credencial (decisão 4); e cada
mutação da tabela do `tasks.md`.

### 10. O fechamento: o comando cheio desta mudança

Esta mudança toca build, variante e suíte instrumentada — os três casos em que P5 manda o comando
cheio. Então: `./gradlew build --continue --rerun-tasks` (que passa a incluir o release, a guarda do APK
sobre ele e as guardas de testes executados), `./gradlew -p buildSrc test --rerun-tasks` (a guarda nova
mora em `buildSrc`), e `./gradlew :apps:android:connectedDebugAndroidTest` **sem filtro** no emulador —
tudo **depois** da reversão de todas as mutações, com o `timestamp` de dentro dos XML. O CI da PR é
lido no destino; e, com a `concurrency` nova, é a primeira execução em que a leitura confere também que
os três jobs têm grupos distintos.

### 11. O que não pode faltar no registro

- **A guarda do APK confere o que `assembleRelease` produz hoje** — um APK sem assinatura. O artefato
  de loja (assinado, talvez AAB, talvez com R8) não existe ainda, e quando existir esta guarda não o
  verá. A fatia comercial que o criar deve a guarda dele.
- **A guarda de testes executados prova que todo `@Test` declarado tem resultado, e não que o
  resultado verifica alguma coisa.** Um teste sem asserção roda e passa. É a camada vizinha de "ver
  falhar", e não o substitui (P16).
- **A `concurrency` é conferida por leitura.**

## Decisões já tomadas contra — o que é proibido em 7.2

Da lista "Proibido nesta etapa" da ETAPA 7 do plano, com as razões que o plano dá. Não são alternativas
em aberto.

- **Ligar `minifyEnabled` ou acrescentar regras de R8.** Não está na auditoria, muda o artefato e abre
  uma frente de verificação inteira. Achado novo → item escrito.
- **Assinar o release, mexer em `versionCode` ou tocar em publicação de loja.** É trabalho de
  lançamento, não de correção de auditoria.
- *(A terceira linha da lista — `renderizador.mjs` genérico — é da 7.1, arquivada.)*

E as que saem das decisões acima:

- **Afrouxar asserção ou esticar espera** em `SessaoEmRepousoInstrumentedTest` para ele passar (P11,
  P12). As esperas de cinco segundos têm razão escrita no próprio arquivo.
- **Android Test Orchestrator**, ou qualquer isolamento por processo, para esconder a interferência. É
  tecnologia nova sem ADR (regra 4 do `CLAUDE.md`), e trataria o sintoma na suíte inteira em vez da
  causa no cenário.
- **Mexer em `SessaoGuardadaAndroid`** sem parar e decidir com o mantenedor (decisão 4).
- **Subir o JUnit** (decisão 6).
- **Uma lista mantida à mão** das classes ou tarefas que a guarda de testes confere. Seria um segundo
  registro, pelo argumento com que a ETAPA 8 proíbe duplicar o §16 num YAML.
- **Apagar os cenários de `ApiPlatosPacoteTest`** em vez de fazê-los rodar. Eles afirmam o `SemRede` da
  listagem e do pacote, e o da listagem não é exercitado em outra camada (7.3, §6).

## Risks / Trade-offs

- **[+1 min por build]** com `testReleaseUnitTest`. → Medido e aceito pelo mantenedor (proposal).
- **[A reflexão não carrega uma classe de teste]** — tipo de assinatura ausente do `classpath`. →
  Falha fechada, com o nome da classe; nada é pulado.
- **[O nome do XML diverge do nome do método]** — um `@DisplayName` futuro, ou teste parametrizado. →
  Hoje não existe nenhum (Context). Se entrar, a guarda acusa o método como sem resultado — barulhento,
  e não silencioso — e a forma nova se ensina com canário.
- **[A credencial não reproduz]** → a mudança para nesse item e o devolve ao mantenedor com as
  execuções escritas (decisão 4); os outros quatro itens seguem.
- **[`cancel-in-progress: false` enfileira `paridade`]** → cada push espera a `paridade` anterior. É o
  custo de não perdê-la, e o plano o aceita.
- **[PR empilhada]** — a base é a da 7.1 (#60), ainda aberta, como a pilha decidida em 2026-09-19.

## Migration Plan

Nada a migrar: nenhum dado, schema, contrato, artefato publicado ou hash muda. A reversão de cada
item é independente: o `ci.yml`, o bloco `beforeVariants`, as duas linhas da guarda do APK, o registro
da guarda de testes nos três módulos, e a tarefa em `buildSrc`.
