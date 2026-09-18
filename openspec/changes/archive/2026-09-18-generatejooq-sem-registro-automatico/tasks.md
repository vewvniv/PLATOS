## 1. A medição que decide o formato da verificação

- [x] 1.1 Medir se teste de `buildSrc` roda no comando cheio do CI. Acrescentar um teste que falha de
  propósito em `buildSrc/src/test`, rodar `./gradlew build`, e observar se o build fica vermelho.
  Registrar o resultado com o comando e a saída — é ele que decide entre teste permanente (2.4a) e
  reprodução registrada (2.4b). Remover o teste provocado em seguida e conferir a remoção **rodando**
  (P10).

  **Medido em 2026-09-18. Resposta: NAO roda.** `SondaDeTesteDeBuildSrc`, que chama `fail()` por
  construcao, ficou na arvore enquanto tres comandos rodaram:

  | Comando | Desfecho | O que o grafo mostrou |
  |---|---|---|
  | `./gradlew help` | **SUCCESSFUL** em 24s | `:buildSrc:` vai ate `jar`; nenhuma task de teste |
  | `./gradlew build` | **SUCCESSFUL** em 13s, 176 actionable | idem — sem `compileTestKotlin`, sem `test` |
  | `./gradlew -p buildSrc test` | **FAILED** | `compileTestKotlin` executou, `1 test completed, 1 failed` |

  A terceira linha e a guarda de vacuidade (P13): sem ela, "o build ficou verde" seria indistinguivel
  entre "o teste nao roda" e "o teste nao existe". Ele existe, compila e falha — so nao e alcancado
  pelo comando que o CI roda. Sonda removida; `grep -rn "MUTACAO"` fora de `build/` → `0`, `git status`
  sem residuo, e a reversao conferida rodando `./gradlew help` (P10).

## 2. A aquisição da conexão

- [x] 2.1 Capturar a linha de base: rodar `./gradlew :apps:api:generateJooq --rerun-tasks` e guardar
  uma cópia das classes geradas, com a data e o caminho anotados (P3). Sem isso, a comparação de 3.1
  não tem contra o que comparar.
- [x] 2.2 `GenerateJooqTask` passa a obter a conexão pelo driver explícito, com `Properties` de usuário
  e senha, e falha com mensagem própria se a aquisição devolver `null`. Verificar que
  `./gradlew :apps:api:generateJooq --rerun-tasks` conclui.
- [x] 2.3 A mesma conexão passa a servir o `GenerationTool`, e o `withJdbc` sai. Verificar que a task
  conclui e que o log mostra as migrations aplicadas na ordem, como antes.
- [x] 2.4 A falha de aquisição passa a carregar o diagnóstico da decisão 3 do `design.md` — drivers
  registrados, carregador de classes da task e do driver, URL — na **exceção**, e não no log.
  **A bifurcação 2.4a/2.4b não se aplicou como escrita, e isso fica dito em vez de corrigido em
  silêncio (P7).** Ela previa dois desfechos — teste de `buildSrc` roda, ou não roda e não há guarda.
  O medido foi um terceiro: não roda **hoje**, e passar a rodar custa três linhas e um passo de CI.
  O `proposal.md` já previa tocar o `ci.yml` exatamente neste caso, então os dois artefatos
  discordavam. Decidido pelo desenvolvedor em 2026-09-18: ligar a guarda.

- [x] 2.5 Source set de teste em `buildSrc`, com `kotlin("test")` e `useJUnitPlatform()`. Verificar que
  `./gradlew -p buildSrc test` executa `compileTestKotlin` e `test`, e não `NO-SOURCE`.
- [x] 2.6 `AquisicaoDeConexaoTest`: desregistra do `DriverManager` todo driver registrado, sobe um
  Postgres do Testcontainers, e afirma que a aquisição nova conecta assim mesmo. Restaurar os drivers
  no fim, para não vazar estado de processo para os testes vizinhos. Verificar que o teste passa.
- [x] 2.7 Canário do mesmo teste (P13): com os drivers desregistrados, afirmar que
  `DriverManager.getConnection` **ainda** falha com `No suitable driver found`. Sem ele, 2.6 passaria
  num processo onde o desregistro não funcionou, e a afirmação inteira seria sobre coisa nenhuma.
- [x] 2.8 Passo `./gradlew -p buildSrc test` no `ci.yml`, antes do codegen. Verificar que o passo
  existe e que o comando roda verde localmente.

## 3. Que a saída não mudou

- [x] 3.1 Comparar as classes geradas depois de 2.3 contra a cópia de 2.1, arquivo a arquivo. Verificar
  que a diferença é **vazia**; se não for, parar e ler a diferença antes de seguir — "compila" não é
  "é a mesma coisa".

  **Feito.** Base de `2026-09-18T10:12:21Z`, 29 arquivos, hash agregado `69609f01…`. Depois da
  mudança: 29 arquivos, `diff -r` com saída vazia e `exit 0`. **E a comparação foi provada reativa**
  (§3): uma cópia com duas linhas de comentário acrescentadas a `DefaultCatalog.kt` fez o mesmo
  `diff -r` sair com `exit 1`. Sem esse canário, `exit 0` seria indistinguível de um comparador que
  não compara.
- [x] 3.2 Rodar `./gradlew build --rerun-tasks` e registrar contagem de testes e `timestamp` do
  relatório, filtrando por timestamp (P3).

  **1409 testes, 0 falhas, 176 de 176 tasks executadas**, janela `2026-09-18T10:22`–`10:23`. A fatia
  anterior registrou 1411; a diferença de 2 está explicada e **não é regressão** — `DeviceSessionTest`
  passou de 42 para 40 `@Test` em `e3cafc6`, e os 1411 são de antes desse commit. Detalhe por alvo em
  `docs/cobertura-generatejooq-sem-registro-automatico.md` §5.5.

## 4. Ver falhar (P9)

- [x] 4.1 Mutação dirigida: remover os drivers registrados do `DriverManager` imediatamente antes da
  aquisição, **no código de hoje** (`git stash` da mudança, ou uma cópia da task anterior). Confirmar
  que a task fica vermelha com exatamente
  `java.sql.SQLException: No suitable driver found for jdbc:postgresql://…`. Registrar a mensagem
  literal — é ela que amarra a reprodução ao sintoma observado, e não a semelhança.

  **A mutação como desenhada não reproduziu nada, e isso fica dito em vez de corrigido em silêncio
  (P7).** A decisão 4 do `design.md` afirmava que desregistrar os drivers "põe o processo exatamente
  no estado em que o erro observado nasce". Aplicada ao código de hoje, a task ficou **verde**.

  O diagnóstico que esta mudança acrescentou foi o que explicou: dentro da task, no daemon do Gradle,
  a **primeira** consulta a `DriverManager.getDrivers()` devolve `0`, e a **segunda**, logo depois,
  devolve `2` — `org.postgresql.Driver` e `ContainerDatabaseDriver`. A inicialização preguiçosa do
  `DriverManager` é observável de dentro da task. O `forEach` da mutação varria uma lista vazia, e o
  `getConnection` seguinte disparava a inicialização e achava o driver.

  Corrigida — forçar a inicialização **antes** de desregistrar —, a mutação reproduziu o sintoma
  literal dentro da task real:

  ```
  java.sql.SQLException: No suitable driver found for jdbc:postgresql://localhost:54868/test?loggerLevel=OFF
  ```
- [x] 4.2 A mesma mutação contra o código novo: confirmar que a task **passa**. Os dois resultados
  juntos são a afirmação inteira; qualquer um sozinho não diz nada.

  **Medido.** Mesma condição — `MUTACAO-diag apos desregistro: 0` —, caminho novo: `BUILD SUCCESSFUL`,
  migrations aplicadas. As duas metades vieram da **mesma** task, no **mesmo** daemon, na mesma
  sessão.
- [x] 4.3 Confirmar que a mutação saiu: `grep -c "MUTACAO"` → `0`, `git status` limpo do que não é a
  mudança, e a task rodando verde depois da reversão (P10).

## 5. O registro

- [x] 5.1 Escrever `docs/cobertura-generatejooq-sem-registro-automatico.md` com: a reprodução dirigida
  e as duas mensagens de 4.1 e 4.2; a comparação vazia de 3.1; e a seção "o que ainda não foi
  verificado" dizendo, com essas palavras, que **a causa da intermitência não foi medida** e que o que
  se afirma é só a remoção da dependência.
- [x] 5.2 Registrar ali o primeiro build depois da mudança como **uma amostra**, e não como prova:
  ele é build após reconfiguração, que é a condição em que o sintoma foi visto 2 vezes — mas uma
  amostra verde não contradiz 0 de 4 nem confirma nada.
- [x] 5.3 Atualizar `docs/cobertura-slice-4b-outbox-de-resultado.md` §6.5 **sem apagar o texto antigo**
  (P7), apontando para o documento novo e dizendo o que ficou fechado e o que não.
