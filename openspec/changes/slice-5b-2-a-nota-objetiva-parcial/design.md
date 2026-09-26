## Context

O motivo está na proposta (Why). Aqui fica só o estado atual que molda o desenho, com o tipo de cada
afirmação (P6).

- **A apuração exige o conjunto inteiro da variante.** `ObjectiveScoring.score` recusa quando os
  itens lidos diferem de `variant.positions.values`, e as discursivas estão em `positions`. Numa prova
  com discursiva ela sempre recusa, e a 8.1 da 5a fixou isso. *Conferido por leitura*
  (`ObjectiveScoring.kt:182-186`).
- **O pacote já distingue os dois tipos de item:**
  - `PackageItem.kind` diz objetivo ou discursivo;
  - o gabarito só cobre as objetivas;
  - a rubrica fica na discursiva;
  - a coerência do pacote exige `max_score` igual ao gabarito somado às rubricas.

  Na fixture, são 4 objetivas de 1 ponto e rubricas que somam 7, com `max_score` 11. *Conferido por
  leitura do pacote e de `ExamPackageValidation.kt:152`.*
- **A sessão da prova com discursiva monta `ScanState.DiscursivaNaoCorrigivel` quadro a quadro**, sem
  memória entre quadros além do estado apresentado. A KDoc do estado diz "nem parcial"
  (`ScanState.kt:50`), e esta mudança substitui isso pela decisão 1a.
- **O `LayoutMap` não declara o número impresso da questão.** O número é texto desenhado, e a região
  declara `question_id`. `positions`, no pacote, mapeia a posição para o item, e o motor numera pela
  mesma ordem (decisão 7 da 5a). *Conferido por leitura da fixture
  `prova-discursiva.aluno.layout.json`.*
- **A gravação e o envio aceitam `ObjectiveScore`** (`ResultadoPendente.nota`). Um valor desse tipo
  que chegue ali vira pendente e sobe.

## Goals / Non-Goals

**Goals:**
- A parcial nasce no domínio, com as mesmas regras da apuração completa, e é **impossível** de
  confundir com a nota completa por tipo.
- O caderno do aluno é derivado do que a sessão já recebe (`FrameOutcome`) e do mapa da variante, sem
  contrato novo.

**Non-Goals:**
- Guardar a parcial ou o caderno (5b-3). Recorte (5b-3). Envio (5b-4).
- Finalizar o caderno com confirmação e registro do que faltou (§8), que depende de guardar.
- Avanço automático de aluno, som ou vibração.

## Decisions

### 1. A parcial é um tipo próprio, e não um `ObjectiveScore`

O domínio ganha `ObjectiveScoring.scorePartial(pacote, payload, respostas)`, que devolve a parcial ou
uma recusa. A parcial carrega:
- a pontuação objetiva e o máximo objetivo;
- o máximo da prova;
- as discursivas aguardando correção, com os pontos de cada uma;
- as pendências e a evidência por questão objetiva;
- o hash do pacote e a variante.

**Ela não tem `closed`**, e não herda de `ObjectiveScore` nem o contém.

- **Por que um tipo próprio:** `ResultadoPendente` e a gravação aceitam `ObjectiveScore`. Se a parcial
  fosse um `ObjectiveScore`, com `maxScore` do objetivo, um `closed` verdadeiro sairia de uma prova
  sem pendência objetiva. Um erro de fiação na 5b-3 a gravaria e subiria como nota final, e nenhum
  teste de tipo acusaria. Com um tipo próprio, **o compilador recusa**. É a proteção, e não uma
  conferência (spec, cenário "A parcial não substitui a nota").
- **Alternativa descartada: um `PendingReason.DISCURSIVA` dentro de `ObjectiveScore`.** A invariante
  `points + pendentes ≤ max` fecharia, mas `outcomes` exige um `QuestionAnswer` por questão, e a
  discursiva não tem leitura de bolha. Seria preciso inventar uma resposta discursiva no vocabulário
  da leitura. Além disso, "pendente de revisão por rasura" e "aguardando correção" são coisas
  diferentes para quem lê a tela.
- **As guardas de construção são as da nota, e mais uma:**
  - a evidência soma o total;
  - nada se repete;
  - as pendências são coerentes entre si;
  - **o máximo objetivo somado às discursivas é o máximo da prova**, e esta é a nova.

  O máximo da prova vem de `scoring.max_score`, e a soma vem de outros dois registros (gabarito e
  rubricas). A guarda é a conferência cruzada entre eles (P28), além da validação do pacote.

### 2. O cálculo por questão é um só

O laço que julga cada resposta (o `when` de `ObjectiveScoring.kt:203`) sai para uma função privada que
as duas apurações chamam. **Não se copia o laço.**
- A apuração completa passa os itens da variante.
- A parcial passa só os objetivos: `positions.values` filtrados por `PackageItem.kind == OBJECTIVE`.
- Os pontos de cada discursiva vêm da soma dos critérios da rubrica dela.

A extração é refatoração **dentro** do escopo, porque é o que evita duplicar regra (regra 7). Ela vai
num commit próprio, antes do consumidor (P25), e é verificada pelos testes atuais de
`ObjectiveScoring`, que ficam verdes sem mudar.

### 3. A parcial de prova só objetiva é recusada

`scorePartial` recusa quando `fully_offline_gradable` é verdadeiro. Assim uma mesma prova não tem dois
caminhos de nota, e a sessão da prova objetiva não pode, por engano, mostrar "parcial" de uma nota
que é final.

### 4. O caderno é estado da sessão, chaveado pelo aluno

A sessão da prova com discursiva guarda, **em memória**, o caderno do aluno corrente:
- o token;
- um estado por região esperada: capturada, com problema (e o motivo) ou não vista;
- a última parcial apurada.

As regiões esperadas saem de `LayoutMap.regions` da variante do pacote.

- **Esta é uma exceção declarada à regra "um resultado novo substitui o anterior por inteiro"**
  (`ScanSession.onFrame`). A regra existe contra resultado obsoleto na tela. O caderno é **acúmulo
  deliberado do mesmo aluno**, e a folha de outro aluno o zera. A parcial dentro dele continua
  seguindo a regra: a última apuração do gabarito substitui a anterior por inteiro.
- **"Capturada" não volta atrás, e "com problema" vira "capturada".** Um quadro ruim depois de um bom
  não pode apagar o que já foi lido: é o mesmo raciocínio de `holdsResult`, aplicado por região.
- **O estado da tela passa a carregar o caderno.** `DiscursivaNaoCorrigivel` é renomeado para
  `ProvaComDiscursiva` e ganha a parcial (ou o motivo de ela não existir) e o caderno. O `AVISO` muda
  para "a nota não é definitiva: a correção das discursivas ainda não está disponível neste aparelho.
  Nada foi guardado." Renomear vai num commit sem mudança funcional (P25).
- **Alternativa descartada: o caderno dentro de `FrameOutcome`.** O resultado do quadro é por
  quadro, por definição (decisão 3 da 5b-1). Acumular é trabalho da sessão.

### 5. O número do chip vem de `positions`, e um teste o prende ao número impresso

O chip da discursiva mostra a chave de `positions` do item da região: "3" para `d1` na fixture. O do
gabarito diz "Gabarito".

- **Por que `positions`:** o `LayoutMap` não declara o número (Context), e `positions` é a declaração
  de posição que o pacote publica. Derivar da ordem das regiões, ou da ordem das questões na
  definição, criaria uma segunda fonte.
- **P28, a conferência que reprova a divergência:** um teste renderiza a folha da fixture e confere
  que, para cada região discursiva, o texto de número impresso antes do enunciado do item dela é
  exatamente a chave de `positions`. Hoje os dois coincidem por construção, e esse é o espelho
  **cego** que a P28 proíbe sem essa conferência. Quando a mudança da paginação (ADR-0019) passar a
  declarar o número no `LayoutMap`, este teste é o que cai e obriga a trocar a fonte do chip.

### 6. A parcial só existe quando o gabarito foi lido

- Com `FrameOutcome.Read`, a sessão apura a parcial pelo domínio.
- Sem gabarito no quadro, o caderno do mesmo aluno mantém a última parcial dele.
- Sem nenhuma, a tela não traz parcial.
- A recusa da parcial vira "com problema" no chip do gabarito, com o motivo, e a tela mostra o motivo
  no lugar da parcial.

### 7. Ver falhar, com as camadas separadas

O "nada é gravado" continua com as duas proteções da 5b-1 (a sessão e o domínio), mais uma: **o tipo**.
A 2.2 da 5b-1 viu as duas camadas falharem separadas. Aqui:
- **M-tipo:** a proteção é de compilação, e um teste que não compila não pode morar na suíte. O "ver
  falhar" é feito fora da árvore, num arquivo temporário que tenta montar um `ResultadoPendente` com a
  parcial e que não é commitado:
  - **sem mutação**, a compilação falha com erro de tipo, e o registro é esse erro;
  - **com a mutação** (`scorePartial` passa a devolver `ObjectiveScore`), o mesmo arquivo compila.

  O arquivo é apagado, e a reversão é conferida compilando de novo. A cobertura registra que esta
  proteção **não tem teste automático que a guarde**, e sim o tipo (P8).
- **M-guarda:** a guarda nova (máximo objetivo somado às discursivas igual ao máximo) é desligada.
  Previsto: cai só o teste do pacote com rubrica que não fecha, construído para passar em todas as
  outras conferências (§3, fixture que isola a camada).

A regra de parada da 5a (decisão 13) vale sobre toda mutação: se o conjunto que cai divergir do
previsto, **pare**, escreva o real ao lado e leia a mensagem.

## Risks / Trade-offs

- **[O professor lê a parcial como nota]** → a tela diz "não definitiva" e mostra o máximo da prova
  ao lado do objetivo ("3 de 4 na objetiva · discursivas: 7 pontos aguardam correção · prova vale
  11"). **Não é mitigado, é conhecido** (P8) até a conferência no aparelho, e não há teste
  automático de Compose (decisão 5 da 5b-1).
- **[O caderno se perde ao sair da tela]** → aceito: a sessão ainda não guarda nada, e é a 5b-3 que
  guarda.
- **[O número do chip diverge do impresso depois do ADR-0019]** → a conferência da decisão 5 cai. É a
  tripwire, e não uma mitigação.
- **[Prova com várias variantes]** → até a fatia 7 é uma só. A variante sai do payload como na
  apuração completa, e o caderno usa o mapa dela.

## Migration Plan

Não há dado persistido novo, e não há migration. O APK novo substitui o anterior. Voltar atrás é
reverter os commits, e a tela volta a "sem nota, nem parcial".

## Open Questions

Nenhuma que altere as specs, a abordagem ou as tarefas.
