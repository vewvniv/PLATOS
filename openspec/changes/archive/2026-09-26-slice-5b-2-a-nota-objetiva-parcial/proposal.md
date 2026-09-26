## Why

Desde a `slice-5b-1-o-aparelho-reconhece-a-discursiva`, o aparelho reconhece a folha de uma prova com
discursiva e diz "a correção ainda não está disponível", **sem nota nenhuma, nem parcial**. O professor
escaneia a turma e não recebe nada, embora a parte objetiva da folha já tenha sido lida.

Em 2026-09-26, o mantenedor decidiu (**decisão 1a**, que até aqui só era citada, e não estava escrita
em arquivo nenhum): a parte objetiva de uma prova com discursiva é **guardada e mostrada como
parcial**. Esta mudança faz a metade "mostrar". A metade "guardar" é da 5b-3.

Ela também mostra a **completude** do §8, que ainda não existe: numa prova com discursiva, a folha
de um aluno tem regiões em mais de uma página, e o professor precisa saber o que já foi capturado.

## What Changes

- **O domínio apura a parte objetiva de uma prova com discursiva, como parcial.**
  - A apuração considera só os itens objetivos da variante.
  - As discursivas saem como **aguardando correção**, cada uma com a pontuação que vale.
  - O resultado é um **tipo próprio**, e não a nota objetiva de hoje. Ele nunca é "fechado", e não
    pode ser entregue como nota final por engano, porque o compilador não deixa.
  - A apuração da prova só objetiva não muda.
- **A sessão mostra a parcial.** Diante da folha de uma prova com discursiva, a tela passa a trazer:
  - a pontuação objetiva apurada, sobre o máximo objetivo;
  - quanto vale a parte discursiva que aguarda correção;
  - que a nota **não é definitiva** (D4: a nota é do servidor, e o offline só é definitivo sem
    discursiva).
  - Continua dizendo que **nada foi guardado**.
- **A sessão mostra a completude da folha do aluno** (§8). Cada região da variante vira um chip
  numerado:
  - verde, se foi lida ou reconhecida;
  - âmbar, se estava no quadro e não foi lida, com o motivo;
  - cinza, se ainda não foi vista.

  Um contador acompanha os chips. A folha de outro aluno começa um caderno novo.
- **Nada é gravado, e nada vai para a fila de envio**, como na 5b-1. Guardar a parcial e o recorte é
  da 5b-3, e o envio é da 5b-4.

### Linhas do §16 que esta mudança alcança (P27)

`node tools/divida/divida.mjs` diz "fatia corrente: 5b". Três linhas vencem nesta fatia, e todas são
alcançadas e **não pagas**. Elas seguem em dia até a 6 abrir:
- `Acurácia em manuscrito`, `5`;
- `Modo degradado (§10) não existe`, `5`;
- `O limiar do OMR foi apurado sobre um aparelho e uma impressora`, `5`.

As duas linhas `6` e os eventos `migration-da-5-em-producao` e `implantar-api-da-5a` **não** são
alcançados: não há papel, migration nem implantação.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `scoring`: a parte objetiva de uma prova com discursiva passa a ser apurada, como resultado parcial
  e nunca fechado, ao lado da apuração da prova só objetiva, que não muda.
- `scan-session`: a prova com discursiva deixa de ser "sem nota, nem parcial" e passa a mostrar a
  parcial não definitiva e a completude da folha do aluno. Nada é guardado.

## Impact

- **`packages/domain`:** `scoring/`, com o tipo da parcial e a apuração dela. O cálculo por questão é
  compartilhado com `ObjectiveScoring`, e não copiado.
- **`apps/android`:**
  - `scan/ScanSession.kt` e `scan/ScanState.kt`: a parcial e o caderno do aluno;
  - `scan/ScanScreen.kt`: os chips, o contador e a parcial;
  - `vision/FrameOutcome.kt`, só se o caderno precisar da região não lida com o índice dela, que ela
    já carrega.
- **Testes:**
  - domínio, nos três alvos (JVM, JS e Android host);
  - `ProvaComDiscursivaNaSessaoTest` e `ScanSessionTest`, na JVM.

### O que NÃO será alterado

- **A apuração da prova só objetiva**, a `ObjectiveScore` e a fila de envio (`result-sync`). Nenhum
  `ResultadoPendente` nasce de prova com discursiva.
- **A captura** (`capture-omr`): o recorte e o segundo ajuste do ADR-0018 são da 5b-3.
- **O pacote, o `LayoutMap` e o contrato do fio.** A parcial sai de `PackageItem.kind` e do gabarito,
  que já existem, e o conjunto esperado de regiões sai de `LayoutMap.regions` da variante.
- **O servidor, o banco e nenhuma migration.**
- **O papel.** Nenhuma tarefa imprime nada: a conferência em papel é da sessão única antes da fatia
  6.
- **"Finalizar incompleto com confirmação, registrando o que faltou" (§8).** Registrar exige guardar,
  que é da 5b-3. Aqui os chips mostram, e nada é registrado.
