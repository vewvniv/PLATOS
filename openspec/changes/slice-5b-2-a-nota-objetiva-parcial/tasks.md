## 0. Antes do primeiro commit

- [x] 0.1 Medir a linha de base na árvore de `main`:
  - `./gradlew build --rerun-tasks` e `./gradlew -p buildSrc test --rerun-tasks`;
  - `./gradlew :apps:android:connectedDebugAndroidTest`, sem filtro, no `platos-atd34`;
  - `npx vitest run` em `apps/web`.

  Anotar em `docs/cobertura-slice-5b-2-a-nota-objetiva-parcial.md` a contagem de testes por task, o
  `timestamp` de cada relatório e o número de tasks **executadas** (P2, P3). Verificação: os relatórios
  são desta sessão, com o `timestamp` dentro da janela da execução.
- [x] 0.2 Registrar a saída de `node tools/divida/divida.mjs`. Deve dizer "fatia corrente: 5b", as
  três linhas `5` sob "vence nesta fatia", e as duas linhas `6` em dia. Verificação: a saída na
  cobertura, com `exit 0`.

## 1. A parcial no domínio (`scoring`)

- [x] 1.1 Extrair o julgamento por questão de `ObjectiveScoring.score` para uma função privada, sem
  mudar comportamento (decisão 2), num commit só de refatoração (P25). Verificação: os testes de
  `scoring` passam nos três alvos (`jvmTest`, `jsNodeTest` e `testAndroidHostTest`), sem nenhuma
  asserção alterada, e com a mesma contagem da 0.1.
- [x] 1.2 Commit de contrato: o tipo da parcial, com as guardas de construção (decisão 1), incluída a
  nova, que exige o máximo objetivo somado às discursivas igual ao máximo da prova. Ele entra antes
  de qualquer consumidor. Verificação: testes do tipo na JVM, um por guarda, cada um construído para
  violar **só** aquela guarda e conferindo o **motivo** na mensagem.
- [x] 1.3 `ObjectiveScoring.scorePartial` (decisões 2 e 3). Verificação: um teste de domínio por
  cenário da ADDED de `scoring`, sobre a fixture `prova-discursiva.package.json`, com o oráculo
  **fixado** no teste (P4): 4 objetivas de 1 ponto, discursivas somando 7 e máximo 11. O cenário "A
  parcial não substitui a nota" é a 1.5. O cenário "A apuração completa de prova com discursiva
  continua recusada" é o teste da 8.1 da 5a, que passa sem mudar.
- [x] 1.4 **Ver falhar** a guarda nova (M-guarda, decisão 7). A mutação desliga a guarda do máximo.
  - **Previsto:** cai só o teste da guarda do máximo. O pacote dele tem rubrica que não fecha e passa
    em todas as outras conferências.
  - **Real:** anotado ao lado do previsto.

  Reverter, `grep MUTACAO` e rodar de novo.
- [x] 1.5 **Ver falhar** a proteção de tipo (M-tipo, decisão 7), fora da árvore. Um arquivo temporário
  tenta montar um `ResultadoPendente` com a parcial.
  - **Sem mutação:** anotar o erro do compilador.
  - **Com `scorePartial` devolvendo `ObjectiveScore`:** o mesmo arquivo compila.

  Apagar o arquivo e reverter a mutação, conferindo com `git status` limpo e compilando de novo.

## 2. A sessão (`scan-session`)

- [x] 2.1 Renomear `ScanState.DiscursivaNaoCorrigivel` para `ProvaComDiscursiva`, sem mudança funcional,
  num commit próprio (P25). Verificação: o build compila, e `ProvaComDiscursivaNaSessaoTest` e
  `ScanSessionTest` passam sem nenhuma asserção alterada.
- [x] 2.2 A parcial na sessão (decisão 6): com `FrameOutcome.Read`, a sessão chama `scorePartial` e o
  estado ganha a parcial, ou o motivo da recusa. O `AVISO` passa a ser a frase de "não definitiva"
  (decisão 4). Verificação: um teste em `ProvaComDiscursivaNaSessaoTest` por cenário da ADDED "Prova
  com discursiva mostra a parcial…", incluído "A outra página do mesmo aluno mantém a parcial", que
  entrou ao aplicar (design, decisão 6, atualização de 2026-09-26). Cada teste confere a **frase**, o **aluno** e os **números** da
  parcial, e não só o tipo do estado.
- [x] 2.3 O caderno do aluno (decisões 4 e 6). Verificação: um teste por cenário da ADDED "A
  completude da folha do aluno…", com os `FrameOutcome` montados a partir da fixture discursiva.
- [x] 2.4 O número do chip e a conferência que o prende ao impresso (decisão 5).
  - O chip usa a chave de `positions`.
  - Um teste renderiza a folha de `tok-a` e confere que o número impresso antes do enunciado de cada
    discursiva é a chave de `positions` do item.

  **Ver falhar:** com o chip derivado da ordem das regiões (`regionIndex`, `// MUTACAO`), cai o
  cenário "O indicador tem o número impresso", e só ele. Reverter e rodar.
- [x] 2.5 **Ver falhar** "nada é gravado" com a parcial presente. É a M-a da 5b-1 refeita: a sessão
  apura a prova com discursiva como objetiva.
  - **Previsto:** caem os cenários da parcial.
  - **Nada é gravado:** continua verde, porque o domínio recusa a apuração completa.

  Reverter e rodar.
- [x] 2.6 A tela (`ScanScreen`): os chips em três estados, o contador e a parcial, com o máximo da
  prova ao lado e a frase de "não definitiva". Verificação: o build compila. A tela desenhada **não
  tem teste automático** (decisão 5 da 5b-1), e fica como lacuna na cobertura (P8). Ela é conferida
  na sessão única de papel.

## 3. Fechamento

- [ ] 3.1 `git grep MUTACAO`, fora de `build/`, `node_modules/`, `docs/`, `openspec/` e
  `rigorous.md`, dá `0`, e toda reversão foi rodada (P10).
- [ ] 3.2 Comando cheio local, com contagens e `timestamp` comparados com a 0.1 (P2, P3, P5):
  - `./gradlew build --rerun-tasks` e `buildSrc`;
  - `connectedDebugAndroidTest`, sem filtro;
  - `vitest` e o build do web;
  - as guardas Node.
- [ ] 3.3 `docs/cobertura-slice-5b-2-a-nota-objetiva-parcial.md`:
  - como cada verificação foi vista falhar, com o previsto e o real;
  - o erro do compilador da 1.5;
  - a seção "o que ainda não foi verificado", com a tela e a leitura da parcial pelo professor.
- [ ] 3.4 PR contra `main`, e o CI **lido no destino**: o run, o `headSha` e os passos (P2, P26).
- [ ] 3.5 Preparar a reconciliação do archive (P27): as três linhas `5` em dia, as duas linhas `6`
  não alcançadas, e os eventos `migration-da-5-em-producao` e `implantar-api-da-5a` não alcançados.
