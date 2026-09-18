# Cobertura — `generatejooq-sem-registro-automatico`

O que este documento é: **como cada verificação crítica foi vista falhar**, e não que ela passa.
`rigorous.md` §8 exige isso, e aqui há uma razão a mais: o alvo desta mudança é um defeito
**intermitente**, e sobre intermitência "passou" é a afirmação mais barata que existe.

**O que esta mudança afirma, e o que ela não afirma.** Ela afirma que a aquisição de conexão do
`GenerateJooqTask` deixou de depender da busca que produz `No suitable driver found`, e que o estado
em que aquele erro é obrigatório foi produzido de propósito e atravessado. Ela **não** afirma ter
consertado a causa da intermitência — ver §5.

---

## 1. A medição que decidiu o formato da verificação

A pergunta era se teste de `buildSrc` roda no comando cheio do CI. A resposta não veio de memória nem
da documentação do Gradle: veio de uma sonda que chama `fail()` por construção, deixada na árvore
enquanto três comandos rodaram.

| Comando | Desfecho | O que o grafo mostrou |
|---|---|---|
| `./gradlew help` | **SUCCESSFUL** em 24 s | `:buildSrc:` vai até `jar`; nenhuma task de teste |
| `./gradlew build` | **SUCCESSFUL** em 13 s, 176 actionable | idem — sem `compileTestKotlin`, sem `test` |
| `./gradlew -p buildSrc test` | **FAILED** | `compileTestKotlin` executou, `1 test completed, 1 failed` |

**A terceira linha é a guarda de vacuidade (P13), e sem ela as duas primeiras não diriam nada:** "o
build ficou verde" é indistinguível entre *o teste não roda* e *o teste não existe*. Ele existe,
compila e falha — só não é alcançado pelo comando que o CI roda.

Consequência: a guarda desta mudança só vale acompanhada de um passo próprio no `ci.yml`. Ela está lá,
e o comentário no `buildSrc/build.gradle.kts` registra por quê, para que remover o passo seja uma
decisão visível e não uma limpeza.

Sonda removida: `grep -rn "MUTACAO"` fora de `build/` → `0`, `git status` sem resíduo, reversão
conferida rodando (P10).

## 2. A saída do codegen não mudou, e a comparação foi provada reativa

Trocar o caminho de aquisição da conexão e passar a conexão pronta ao `GenerationTool` podia mudar o
que sai. "O build passou" não distingue **gerar o mesmo** de **gerar outra coisa que também compila**.

- Linha de base gravada **antes** da mudança: `2026-09-18T10:12:21Z`, 29 arquivos `.kt`, hash agregado
  `69609f01438bd7889c94583ba92d4adb` (P3 — a âncora é a data, e não a contagem).
- Depois da mudança: 29 arquivos, `diff -r` com saída **vazia**, `exit 0`.
- Depois da reversão da mutação de §3: `diff -r` **vazio** de novo.

**O canário (§3 do `rigorous.md`):** uma cópia da saída com duas linhas de comentário acrescentadas a
`DefaultCatalog.kt` fez o mesmo `diff -r` sair com `exit 1`. Sem isso, `exit 0` seria indistinguível de
um comparador que não compara.

## 3. A reprodução dirigida, e a previsão que estava errada

Esperar uma intermitência de 2-em-7 aparecer não é método. O que se faz é **produzir** a condição em
que o sintoma é obrigatório: com o `DriverManager` sem nenhum driver registrado, a busca que gera
aquela mensagem não tem como encontrar nada.

### 3.1 A primeira tentativa não reproduziu nada, e fica escrita (P7)

A decisão 4 do `design.md` afirmava que desregistrar os drivers "põe o processo exatamente no estado
em que o erro observado nasce". Aplicada ao código de hoje — `DriverManager.getConnection` de volta,
mais o desregistro —, a task ficou **verde**.

A afirmação estava errada, e o motivo é medido: dentro da task, a consulta a
`DriverManager.getDrivers()` devolveu **`0`**. O `forEach` do desregistro varria uma lista vazia. A
mutação não produziu condição nenhuma, e o `getConnection` seguinte conectou normalmente.

**Um verde lido como "a mutação não pegou o defeito" teria fechado esta seção com a conclusão
oposta.** O que denunciou foi o diagnóstico que esta própria mudança acrescentou — ele existia para a
próxima ocorrência, e serviu na primeira.

### 3.2 Corrigida, ela reproduz o sintoma literal

Forçando a inicialização preguiçosa **antes** de desregistrar, a mutação produziu a condição
(`apos desregistro: 0`) e o código de hoje falhou com a mensagem literal, dentro da task real, no
daemon do Gradle:

```
java.sql.SQLException: No suitable driver found for jdbc:postgresql://localhost:54868/test?loggerLevel=OFF
```

É a mesma mensagem que `docs/cobertura-slice-4b-outbox-de-resultado.md` §6.5 registra. A reprodução
está amarrada ao sintoma observado pela **mensagem**, e não pela semelhança.

### 3.3 As duas metades, na mesma task e no mesmo daemon

| Caminho de aquisição | Drivers registrados | Desfecho |
|---|---|---|
| `DriverManager.getConnection` (o de hoje) | `0` | **FAILED** — `No suitable driver found` |
| `conectarAoPostgres` (o novo) | `0` | **SUCCESSFUL**, migrations aplicadas |

Qualquer uma das duas linhas sozinha não diz nada: a primeira sem a segunda não mostra que o conserto
conserta, e a segunda sem a primeira não mostra que havia o que consertar.

Mutação revertida: `grep -rn "MUTACAO"` fora de `build/` → `0`, reversão conferida **rodando**, e a
saída do codegen conferida de novo contra a linha de base (§2).

### 3.4 A mesma reprodução, agora permanente

`AquisicaoDeConexaoTest` faz o mesmo numa JVM de teste: desregistra, afirma pelo **canário** que
`DriverManager.getConnection` ainda falha com `No suitable driver found` — sem ele o teste passaria num
processo onde o desregistro não funcionou, que é exatamente o defeito de 3.1 —, e só então afirma que
`conectarAoPostgres` conecta. Restaura os drivers no fim: o `DriverManager` é global ao processo, e
deixá-lo vazio envenenaria qualquer teste vizinho.

1 teste, 0 falhas, 4,173 s, relatório `2026-09-18T10:16:12.904Z`.

## 4. Uma observação sobre o `DriverManager`, e por que ela vale menos do que pareceu

Durante §3.1, a primeira leitura de `DriverManager.getDrivers()` **dentro da task** devolveu `0`, e a
leitura seguinte devolveu `2` (`org.postgresql.Driver` e `ContainerDatabaseDriver`). A leitura
imediata foi: *a inicialização preguiçosa do `DriverManager` é observável, e há uma janela em que a
busca não acha nada.*

**Essa leitura não se sustenta, e fica escrita como errada (P7).** Entre as duas consultas, o próprio
diagnóstico referenciava `org.postgresql.Driver::class.java` — e carregar essa classe dispara o
inicializador estático dela, que chama `DriverManager.registerDriver`. **O instrumento registrava o
driver que ele tinha ido medir.** É o defeito de P3 numa forma nova: não é artefato velho, é o medidor
entrando na medição — o mesmo que fez `grep -c '===COMMIT-OK'` devolver 14 na fatia anterior.

O experimento que separaria as duas hipóteses foi rodado: duas leituras seguidas, sem nada entre elas
que toque a classe do driver, com daemon **parado** antes (`./gradlew --stop`) — a condição que a
fatia anterior correlacionava com a falha.

| Execução | Estado do daemon | DIAG-A | DIAG-B |
|---|---|---|---|
| §3.1 | quente | `0` | `2` (com a classe do driver carregada no meio) |
| §4 | parado antes, `apps/android/build` removido | `2` | `2` |

**Não reproduziu.** Com o daemon frio, os drivers já estavam registrados na entrada da task, e não
houve janela para observar. Então o `0` é **uma observação única, não reproduzida**, e o `0 → 2` é
explicado pelo instrumento.

**O que isto permite afirmar, e nada além:** houve **uma** execução em que o `DriverManager`, dentro
desta task, reportou zero drivers registrados — que é exatamente o estado em que o código antigo era
obrigado a falhar, e é o estado que §3 produz de propósito. Não permite afirmar por que esse estado
acontece, nem com que frequência, nem que ele seja a causa da intermitência.

## 5. O que ainda não foi verificado

### 5.1 A causa da intermitência continua sem medição

Esta mudança **não** descobriu por que `:apps:api:generateJooq` falhava de vez em quando. Ela removeu
a dependência do mecanismo que produz a mensagem, e instalou o diagnóstico que nomeia o estado se ele
voltar. A hipótese do `ServiceLoader` e do carregador de contexto continua **suposta**, exatamente
como `docs/cobertura-slice-4b-outbox-de-resultado.md` §6.5 a deixou — nada aqui a promoveu.

Consequência honesta: **se a intermitência voltar por outro ponto do caminho, esta mudança não a
impede.** O que muda é que a próxima ocorrência chega com drivers registrados, carregadores e URL na
mensagem da exceção, em vez de exigir outro bisect de sete commits.

### 5.2 O primeiro build depois desta mudança é uma amostra, e não prova

Mexer em `buildSrc` invalida a configuração do Gradle, que é a condição em que o sintoma foi visto 2
vezes em 7. Os builds desta sessão passaram. **Isso é amostra, não prova**: 0 de 4 num dia já havia
contrariado a previsão antes, e verde sobre um alvo intermitente é o sinal mais fraco que existe.

**E a amostra do outro lado apareceu, no fim da mesma sessão.** Ao replayar a branch
`envio-distingue-recusa-transitoria` com `git rebase --force-rebase --exec './gradlew build'`, o
**primeiro** commit ficou vermelho com a mensagem literal:

```
java.sql.SQLException: No suitable driver found for jdbc:postgresql://localhost:64256/test?loggerLevel=OFF
```

É a primeira reprodução **espontânea** registrada — todas as anteriores desta sessão foram provocadas.
Três coisas que ela diz, e uma que ela não diz:

- **Não é regressão daquele commit.** Ele toca dois arquivos, ambos em `apps/android`, e não há
  caminho por onde alcancem o codegen. É o procedimento de P15 aplicado: ler o log e o histórico antes
  de chamar vermelho de regressão.
- **A retentativa no mesmo commit passou**, com o classloader quente — o mesmo padrão dos 2 de 7.
- **Aconteceu num build após reconfiguração:** a rebase levou o `buildSrc` de volta à versão de `main`,
  invalidando a configuração. A correlação que a fatia anterior tinha enfraquecido ganha um ponto —
  **e continua sendo correlação**, agora de 3 ocorrências em ~12 execuções ao longo de dois dias.
- **O que ela não diz é a causa.** Nada aqui mediu por que o driver não estava registrado naquela
  execução, e §5.1 continua valendo inteira.

**A consequência prática é de ordem de merge, e não de código:** enquanto esta mudança não entrar,
`main` fica intermitentemente vermelho por este defeito, e qualquer PR aberta contra ele pode herdar um
vermelho que parece regressão sem ser. Esta branch entra antes da outra.

### 5.3 O comando cheio não cobre `buildSrc`, e agora há um segundo comando

`./gradlew build` não alcança `:buildSrc:test` — medido em §1. A guarda depende de um passo próprio no
`ci.yml`, e **um passo pode ser removido por quem não souber disso**. O comentário no
`buildSrc/build.gradle.kts` e no `ci.yml` existe para tornar essa remoção visível, mas comentário não é
trava. **Dono:** esta base. **Fatia-limite:** a primeira que reorganizar o `ci.yml`.

### 5.4 O comentário do `ci.yml` sobre `testAndroidHostTest` ficou impreciso

Ao ler o log do comando cheio, `:packages:domain:testAndroidHostTest` **executou** dentro de
`./gradlew build`, via `allTests`. O comentário do passo separado no `ci.yml` afirma que `build` não o
alcança. A afirmação pode ter sido verdadeira quando foi escrita; hoje o log a contradiz. **Não foi
corrigida aqui**, porque é texto fora do escopo funcional desta mudança (P19) e o passo redundante não
faz mal. **Dono:** esta base. **Fatia-limite:** a próxima que tocar o `ci.yml`.

**Fechado em 2026-09-18, por commit de texto próprio.** A fatia-limite acima estava mal escolhida, e
fica dito: um comentário **medidamente falso** sobre cobertura é pior que nenhum — ele diz a quem
reorganizar o arquivo que aquele passo é a única cobertura do alvo, o que levaria a decisões erradas
nos dois sentidos. Adiá-lo até "a próxima que tocar o `ci.yml`" era adiar justamente a leitura que
induz ao erro.

O comentário passou a registrar as três coisas: a redação original e o incidente real que a motivou, a
medição que a contradiz, e a razão de o passo **ficar** mesmo redundante — removê-lo se apoiaria numa
única medição de uma propriedade que já mudou sozinha uma vez.

### 5.5 A contagem de 1411 da fatia anterior não é comparável, e o motivo está medido

`./gradlew build --rerun-tasks` desta sessão deu **1409** testes, 0 falhas, **176 de 176 tasks
executadas**, relatórios na janela `2026-09-18T10:22`–`10:23` (P3 — filtrado por `timestamp`, não
somando o diretório).

A fatia anterior registrou 1411. A diferença de 2 **não é regressão**: `DeviceSessionTest` tinha 42
`@Test` e passou a ter 40 no commit `e3cafc6`, que o reescreveu quando `DeviceSession` deixou de
conhecer o outbox. Os 1411 foram medidos em `2026-09-17T14:3x`, **antes** desse commit. A linha de
base envelheceu; a suíte não encolheu por acidente.

| Alvo | Testes | Janela |
|---|---|---|
| `:apps:android:testDebugUnitTest` | 293 | `10:22:17`–`10:22:25` |
| `:apps:api:test` | 163 | `10:23:02`–`10:23:19` |
| `:packages:domain:jsNodeTest` | 315 | `10:22:57`–`10:22:59` |
| `:packages:domain:jvmTest` | 323 | `10:23:22`–`10:23:23` |
| `:packages:domain:testAndroidHostTest` | 315 | `10:23:27`–`10:23:28` |
| `:buildSrc:test` (comando próprio) | 1 | `10:16:12` |

### 5.6 O que esta sessão apagou, e não deveria ter apagado sem perguntar

Para esfriar o daemon em §4, além de `./gradlew --stop`, a sessão removeu o diretório
`apps/android/build`. Ele é saída de build e é regenerável, e os números de §5.5 já tinham sido lidos e
registrados antes — mas **P24 proíbe remover arquivo não rastreado sem pedido explícito na sessão**, e
não houve pedido. Fica registrado porque a regra existe justamente para que "era só saída de build"
não seja decisão de quem apaga.

### 5.7 O job `paridade` do CI não rodou nesta sessão

O `ci.yml` tem três jobs. O `build` foi reproduzido inteiro, com `--rerun-tasks` (§5.5), e o passo novo
do `buildSrc` foi rodado pelo comando exato que o CI usa. **O job `paridade` — emulador,
`connectedDebugAndroidTest`, comparação de centroides — não foi executado.**

P5 manda rodar o comando cheio **sempre que a mudança tocar build**, e esta toca: `buildSrc` e
`ci.yml`. A razão de não ter rodado é que a mudança não alcança nada que aquele job mede — nenhuma
fonte Android, nenhuma fixture, nenhum golden, nenhum `LayoutMap`, e as outras tasks de `buildSrc`
(`EmbedFixturesTask`, `EmbedFontTask`, `EmbedRastersTask`) não tiveram uma linha alterada. Elas foram
exercitadas de qualquer modo: a montagem do Android dentro de `./gradlew build --rerun-tasks` as
executa, e as 176 tasks passaram.

**Mas isso é inferência, e não medição (P6).** "A mudança não alcança o que aquele job mede" é um
argumento sobre o código, não um sinal observado. Nenhum golden foi regravado, então P23 não é
acionado; o que fica é a lacuna dita em vez de coberta por raciocínio. **Dono:** esta base.
**Fatia-limite:** o merge desta mudança — o CI roda o job ao abrir a PR, e é lá que a inferência vira
sinal.

**Fechado em 2026-09-18, na fatia-limite escrita.** O job rodou três vezes e passou nas três:
`paridade: success` na PR #49, na PR #50, e no `main` depois dos dois merges
(`build: success  web: success  paridade: success`). A inferência virou sinal exatamente onde este
parágrafo dizia que viraria, e o que fecha o item é o job verde — não o argumento de que ele não
poderia ter quebrado.
