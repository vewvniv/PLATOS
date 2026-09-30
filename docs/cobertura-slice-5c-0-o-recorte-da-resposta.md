# Cobertura — `slice-5c-0-o-recorte-da-resposta`

## Linha de base (tarefa 0.1), 2026-09-30

Medida na árvore de `main` com o archive da 5b-4 (PR #79) dentro, antes de qualquer alteração de
código desta mudança. Logs em `scratchpad/5c-0-baseline/` desta sessão.

### As duas tentativas que falharam antes (P7: ficam registradas)

| Tentativa | Resultado | Causa |
|---|---|---|
| `./gradlew build --rerun-tasks`, 13:17:55Z–13:19:15Z | `BUILD FAILED` em 1m19s, 84 tasks executadas | `:apps:api:generateJooq`: "Could not find a valid Docker environment". O Docker Desktop estava instalado e **parado**. |
| `./gradlew -p buildSrc test --rerun-tasks`, 13:19:50Z–13:20:08Z | `BUILD FAILED`, 1 de 1 falhou | Mesma causa, em `AquisicaoDeConexaoTest`. |
| `./gradlew build --rerun-tasks` com o Docker já de pé, 13:24:04Z–13:24:22Z | `BUILD FAILED` em 18s | "Previous attempts to find a Docker environment failed. Will not retry": o **daemon do Gradle guardou a falha** da primeira tentativa (estado estático do Testcontainers). `./gradlew --stop` resolveu. |

**Um erro meu de leitura, também mantido:** o primeiro aviso de conclusão do build em segundo plano
dizia `exit code 0`, e isso era o `date` que eu tinha posto depois do Gradle, não o Gradle (P2:
`comando; echo` não prova nada). O `BUILD FAILED` só apareceu ao ler o log.

O Docker Desktop foi iniciado pelo mantenedor, por decisão dele (mudança de ambiente local, P22).

### A linha de base

| Comando | Janela | Resultado | Contagem (por task, `timestamp` dentro da janela) |
|---|---|---|---|
| `./gradlew -p buildSrc test --rerun-tasks` | 13:23:49Z–13:23:59Z | BUILD SUCCESSFUL, 6 de 6 executadas | `buildSrc:test` 1 |
| `./gradlew build --rerun-tasks` | 13:24:33Z–13:28:23Z | BUILD SUCCESSFUL em 3m48s, **183 de 183 tasks executadas** | `apps:android:testDebugUnitTest` 344 · `testReleaseUnitTest` 344 · `apps:api:test` 171 · `packages:domain:jvmTest` 414 · `jsNodeTest` 405 · `testAndroidHostTest` 405 |
| `./gradlew :apps:android:connectedDebugAndroidTest` (`platos-atd34`, sem filtro) | 13:29:57Z–13:31:16Z | BUILD SUCCESSFUL em 1m18s | 97 executados, 0 falhas, 2 ignorados (o "99 finalizados" do log); relatório com `timestamp` 13:31:13Z |
| `npx vitest run` (`apps/web`) | — | **Não executado**: esta mudança não toca `apps/web`, e `apps/web/src/layoutMap.ts` só tem o tipo `answer_area`, sem espelho da validação (conferido por `grep`) | — |

As contagens são as do fechamento da 5b-4 (344, 171, 414, 405) — nada mudou entre uma e outra —, e
todos os `timestamp` dos XML caem na janela do comando que os produziu (P3).

**O que o "2 executed, 86 up-to-date" do connected significa:** as 86 são compilações que o `build`
de minutos antes já fez; a task de teste instrumentado em si executou (99 testes num aparelho, com
relatório novo). Não é o caso de P3 (contagem plausível de execução anterior): o `timestamp` do XML
é de 13:31:13Z.

**Memória.** O agregado da 5b-4 tinha sido interrompido por falta de memória. Aqui o agregado coube:
os daemons do Gradle foram parados (`./gradlew --stop`) antes de subir o emulador, o que liberou
cerca de 5,7 GB, e o `connected` rodou com o emulador sem janela.

## `sha256` de antes (tarefa 0.2)

`sha256sum fixtures/*.layout.json fixtures/*.package.json`, 2026-09-30, antes de qualquer alteração.
As fixtures são montadas como assets de teste direto de `fixtures/` (`apps/android/build.gradle.kts`),
sem cópia. Arquivo integral: `scratchpad/5c-0-baseline/sha256-antes.txt`.

```
d9f7b08c17a706b02ba21355e2286b71d09792605b1c22adc8b7b95ce566c221  fixtures/folha-de-teste.layout.json
914fb389830b4754935b608e3320d29bdd1839403c675179820a3edd6ced24c8  fixtures/prova-discursiva.aluno.layout.json
63040b2f1dfe1152ab4e83561bc77d5abc7023a73aab497491878ecc71ca5840  fixtures/prova-discursiva.layout.json
8c9756a9db45c1d08a97fd0d99f6edcb353338f78894ff5b26438d1e00078451  fixtures/prova-referencia.layout.json
c2098e10e6c70f93a469ce56ff5f0e5796b44792da0b661e01465904a48cb717  fixtures/prova-2.package.json
87a9f3312e9b7156542c7deb121c68782064aea1c61669951a9ad019e4f0a784  fixtures/prova-discursiva.package.json
ff2b94ef600101e2c20d5b6b298f7d0612ee0a66beb4d74d7dcd954cfbde40da  fixtures/prova-referencia.package.json
7282a186d3b644dac6108e7ea39931517c5a0614e8fa570b15ad09324cb14df7  fixtures/prova-referencia.turma.package.json
```

## Dívida (tarefa 0.3), 2026-09-30

`node tools/divida/divida.mjs` → `exit 0`, "nenhuma linha vencida: 21 linhas lidas", "vence nesta fatia
(5c): nenhuma". Fatia corrente derivada: `5c`. **A guarda não lê a tabela "Aberto" do §16**
(`TITULO_TABELA = '### Ponto de não-retorno'`); as duas linhas de fatia-limite 5 que estão nela não
aparecem nesta saída. Ver a tarefa 7.3.
