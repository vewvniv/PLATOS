## ADDED Requirements

### Requirement: Prova com discursiva mostra a parcial objetiva, não definitiva, e não guarda nada

Quando o pacote da sessão declara que a prova não é corrigível só no aparelho (`fully_offline_gradable` falso), a sessão SHALL abrir e escanear como qualquer outra. Diante de uma folha dessa prova, a sessão SHALL apresentar:
- de qual aluno é a folha, pelo payload do QR;
- quais regiões da folha ela reconheceu: o gabarito e cada região discursiva, esta pela questão;
- quando o gabarito foi lido, a **parcial objetiva**: a pontuação objetiva apurada sobre o máximo objetivo, a pontuação das discursivas que aguardam correção e as pendências de revisão das objetivas;
- que a nota **não é definitiva**, porque a parte discursiva ainda não foi corrigida;
- que **nada foi guardado**.

A parcial SHALL vir da apuração parcial do pacote da sessão, e SHALL NOT ser calculada pela sessão por conta própria. Quando o gabarito não foi lido no quadro, a sessão SHALL NOT apresentar parcial. Quando a apuração parcial recusa a folha, a sessão SHALL apresentar o motivo da recusa, e não uma parcial.

Para essa prova, a sessão SHALL NOT produzir resultado: nada SHALL ser gravado nem entrar na fila de envio, nem a parcial. A folha de outra prova continua recusada com o motivo de sempre.

A prova só objetiva SHALL continuar sendo apurada e gravada exatamente como antes.

#### Scenario: Folha de prova com discursiva no quadro

- **WHEN** a sessão de uma prova com discursiva lê o gabarito e uma região discursiva de uma folha
- **THEN** a sessão apresenta o aluno da folha, as regiões reconhecidas, a parcial objetiva com as discursivas aguardando correção, que a nota não é definitiva e que nada foi guardado

#### Scenario: Só as discursivas no quadro

- **WHEN** a sessão de uma prova com discursiva reconhece uma região discursiva e o gabarito não está no quadro
- **THEN** a sessão apresenta o aluno e a região reconhecida, e não apresenta parcial

#### Scenario: A parcial recusada mostra o motivo

- **WHEN** o gabarito de uma folha de prova com discursiva é lido e a apuração parcial o recusa
- **THEN** a sessão apresenta o motivo da recusa, e nenhuma parcial

#### Scenario: Nada é gravado

- **WHEN** a sessão de uma prova com discursiva apresenta a parcial de uma folha, quantas vezes for
- **THEN** nenhuma apuração é entregue para gravar, e a fila de envio não ganha resultado

#### Scenario: Abrir a câmera numa prova com discursiva

- **WHEN** o escaneamento de uma prova com discursiva é aberto
- **THEN** a câmera abre e a sessão procura a folha, sem o aplicativo terminar com erro

#### Scenario: Prova só objetiva não muda

- **WHEN** a sessão de uma prova só objetiva lê uma folha
- **THEN** a nota é apurada, apresentada e entregue para gravar, como antes desta mudança

### Requirement: A completude da folha do aluno é mostrada por região

Numa prova com discursiva, a sessão SHALL manter, para o aluno cuja folha está sendo escaneada, o **caderno** dele: o conjunto de regiões que a variante declara, com o estado de cada uma. O conjunto esperado SHALL sair das regiões que o `LayoutMap` da variante declara. Cada região SHALL estar em um de três estados:
- **capturada**: lida, no gabarito, ou reconhecida, na discursiva, em algum quadro desse aluno;
- **com problema**: presente num quadro e não lida, com o motivo, e ainda não capturada;
- **não vista**: ainda não apareceu inteira em nenhum quadro desse aluno.

Uma região capturada SHALL continuar capturada, mesmo que um quadro seguinte não a leia. Uma região com problema SHALL passar a capturada quando um quadro seguinte a ler.

A sessão SHALL apresentar um indicador por região, distinto por estado, e SHALL apresentar quantas das regiões esperadas estão capturadas. O indicador da região de gabarito SHALL ser identificado como gabarito, e o de cada região discursiva, pelo **número que a questão tem na folha impressa**. O número SHALL NOT ser derivado de outra fonte que possa divergir do número impresso (ADR-0019: "todo número que o professor vê sai de um lugar só").

O caderno é do aluno que o payload identifica. Um quadro com a folha de **outro** aluno SHALL começar um caderno novo, e o anterior SHALL NOT ser misturado com ele. O caderno SHALL NOT ser gravado.

#### Scenario: Caderno começa com tudo não visto

- **WHEN** a primeira folha de um aluno de uma prova com gabarito e duas discursivas aparece no quadro, trazendo o gabarito e a primeira discursiva
- **THEN** o caderno mostra essas duas regiões capturadas, a segunda discursiva não vista, e "2 de 3"

#### Scenario: O indicador tem o número impresso

- **WHEN** o caderno de uma prova cuja primeira discursiva é impressa como questão 3 é apresentado
- **THEN** o indicador dessa região traz o número 3, o mesmo que a folha impressa mostra

#### Scenario: A segunda página completa o caderno

- **WHEN** em seguida a página com a segunda discursiva do mesmo aluno é reconhecida
- **THEN** o caderno mostra as três regiões capturadas, e "3 de 3"

#### Scenario: Região com problema

- **WHEN** uma região discursiva está inteira no quadro e não é lida
- **THEN** ela aparece com problema, com o motivo, e não conta como capturada

#### Scenario: Capturada não volta atrás

- **WHEN** uma região já capturada deixa de ser lida num quadro seguinte do mesmo aluno
- **THEN** ela continua capturada

#### Scenario: Outro aluno começa outro caderno

- **WHEN** a folha de outro aluno aparece no quadro
- **THEN** o caderno mostrado passa a ser o desse aluno, só com o que foi visto dele

#### Scenario: Prova só objetiva não tem caderno

- **WHEN** a sessão de uma prova só objetiva lê uma folha
- **THEN** a tela é a de antes desta mudança, sem indicador de região

## REMOVED Requirements

### Requirement: Prova com discursiva é reconhecida e explicada, e não apurada

**Reason**: a decisão 1a do mantenedor, de 2026-09-26, é guardar e mostrar a parcial objetiva, e este
requisito proibia "nota, nem parcial". O nome dele ficaria falso. Um `MODIFIED` manteria o nome, e
por isso o requisito sai inteiro. O requisito "Prova com discursiva mostra a parcial objetiva, não
definitiva, e não guarda nada", em `ADDED`, carrega o resto do bloco, que continua igual: o aluno, as
regiões, nada gravado, a câmera que abre e a prova só objetiva inalterada.

**Migration**: nenhum dado. O estado de tela da prova com discursiva passa a carregar a parcial e o
caderno. Nada foi gravado antes, e nada é gravado agora.
