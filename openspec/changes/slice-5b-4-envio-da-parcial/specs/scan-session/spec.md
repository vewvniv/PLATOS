## MODIFIED Requirements

### Requirement: Prova com discursiva mostra a parcial objetiva, não definitiva, e não guarda nada

Quando o pacote da sessão declara que a prova não é corrigível só no aparelho (`fully_offline_gradable` falso), a sessão SHALL abrir e escanear como qualquer outra. Diante de uma folha dessa prova, a sessão SHALL apresentar:
- de qual aluno é a folha, pelo payload do QR;
- quais regiões da folha ela reconheceu: o gabarito e cada região discursiva, esta pela questão;
- quando o gabarito foi lido, a **parcial objetiva**: a pontuação objetiva apurada sobre o máximo objetivo, a pontuação das discursivas que aguardam correção e as pendências de revisão das objetivas;
- que a nota **não é definitiva**, porque a parte discursiva ainda não foi corrigida;
- que, enquanto o caderno não completa, **nada foi guardado**, e que o caderno completo é entregue para envio.

A parcial SHALL vir da apuração parcial do pacote da sessão, e SHALL NOT ser calculada pela sessão por conta própria. Enquanto o gabarito desse aluno não foi lido em nenhum quadro, a sessão SHALL NOT apresentar parcial. Quando ele já foi lido e o quadro corrente não o traz, a sessão SHALL apresentar a última parcial desse aluno. Quando a apuração parcial recusa a folha, a sessão SHALL apresentar o motivo da recusa, e não uma parcial.

Enquanto o caderno do aluno não completar — todas as regiões que a variante declara em estado capturada —, a sessão SHALL NOT produzir resultado: nada SHALL ser gravado nem entrar na fila de envio, nem a parcial. Ao completar, o caderno SHALL produzir o resultado parcial correspondente, pela regra do requisito "A completude da folha do aluno é mostrada por região", e esse resultado SHALL continuar não-definitivo: a parte discursiva ainda não foi corrigida. A folha de outra prova continua recusada com o motivo de sempre.

A prova só objetiva SHALL continuar sendo apurada e gravada exatamente como antes.

**O caderno em andamento — o do aluno cuja folha está sendo escaneada — SHALL sobreviver ao encerramento do processo do aplicativo**, e a sessão SHALL retomá-lo, com a última parcial que carregava, ao reabrir o escaneamento da mesma prova. Isto SHALL NOT contradizer "nada foi guardado" enquanto incompleto: o que sobrevive é o estado de tela do caderno em andamento, para continuidade do trabalho do professor, e enquanto o caderno não completa ele SHALL NOT ser lido, gravado nem enviado como resultado. Encerrar a sessão de uso do aparelho (sair de `device-session`) SHALL NOT apagar o caderno em andamento, pela mesma razão que resultado pendente de `result-sync` não é apagado por sair: é trabalho do professor ainda não concluído.

#### Scenario: Folha de prova com discursiva no quadro

- **WHEN** a sessão de uma prova com discursiva lê o gabarito e uma região discursiva de uma folha
- **THEN** a sessão apresenta o aluno da folha, as regiões reconhecidas, a parcial objetiva com as discursivas aguardando correção, que a nota não é definitiva e que nada foi guardado

#### Scenario: Só as discursivas no quadro

- **WHEN** a sessão de uma prova com discursiva reconhece uma região discursiva de um aluno cujo gabarito ainda não foi lido, e o gabarito não está no quadro
- **THEN** a sessão apresenta o aluno e a região reconhecida, e não apresenta parcial

#### Scenario: A outra página do mesmo aluno mantém a parcial

- **WHEN** o gabarito de um aluno já foi lido num quadro anterior, e o quadro corrente traz só uma região discursiva desse mesmo aluno
- **THEN** a sessão apresenta a última parcial desse aluno

#### Scenario: A parcial recusada mostra o motivo

- **WHEN** o gabarito de uma folha de prova com discursiva é lido e a apuração parcial o recusa
- **THEN** a sessão apresenta o motivo da recusa, e nenhuma parcial

#### Scenario: Nada é gravado

- **WHEN** a sessão de uma prova com discursiva apresenta a parcial de um caderno que ainda não completou, quantas vezes for
- **THEN** nenhuma apuração é entregue para gravar, e a fila de envio não ganha resultado

#### Scenario: Abrir a câmera numa prova com discursiva

- **WHEN** o escaneamento de uma prova com discursiva é aberto
- **THEN** a câmera abre e a sessão procura a folha, sem o aplicativo terminar com erro

#### Scenario: Prova só objetiva não muda

- **WHEN** a sessão de uma prova só objetiva lê uma folha
- **THEN** a nota é apurada, apresentada e entregue para gravar, como antes desta mudança

#### Scenario: O aplicativo fecha no meio da leitura de um aluno

- **WHEN** o processo do aplicativo termina enquanto o caderno de um aluno está em andamento, com a última parcial apurada
- **THEN** reabrir o escaneamento da mesma prova apresenta o mesmo aluno, o mesmo caderno e a mesma parcial, sem exigir escanear de novo o que já tinha sido lido

#### Scenario: Sair não apaga o caderno em andamento

- **WHEN** o usuário sai da sessão do aparelho com um caderno de prova com discursiva em andamento
- **THEN** o caderno continua guardado, e reabrir o escaneamento desta prova o retoma

### Requirement: A completude da folha do aluno é mostrada por região

Numa prova com discursiva, a sessão SHALL manter, para o aluno cuja folha está sendo escaneada, o **caderno** dele: o conjunto de regiões que a variante declara, com o estado de cada uma. O conjunto esperado SHALL sair das regiões que o `LayoutMap` da variante declara. Cada região SHALL estar em um de três estados:
- **capturada**: lida, no gabarito, ou reconhecida, na discursiva, em algum quadro desse aluno;
- **com problema**: presente num quadro e não lida, com o motivo, e ainda não capturada;
- **não vista**: ainda não apareceu inteira em nenhum quadro desse aluno.

Uma região capturada SHALL continuar capturada, mesmo que um quadro seguinte não a leia. Uma região com problema SHALL passar a capturada quando um quadro seguinte a ler.

A sessão SHALL apresentar um indicador por região, distinto por estado, e SHALL apresentar quantas das regiões esperadas estão capturadas. O indicador da região de gabarito SHALL ser identificado como gabarito, e o de cada região discursiva, pelo **número que a questão tem na folha impressa**. O número SHALL NOT ser derivado de outra fonte que possa divergir do número impresso (ADR-0019: "todo número que o professor vê sai de um lugar só").

O caderno é do aluno que o payload identifica. Um quadro com a folha de **outro** aluno SHALL começar um caderno novo, e o anterior SHALL NOT ser misturado com ele.

**O caderno em andamento SHALL ser guardado em armazenamento local, para sobreviver ao encerramento do processo do aplicativo.** Guardar o caderno para a tela continua distinto de produzir o fato durável que `result-sync` grava: um não substitui o outro. **Quando a última região que faltava é capturada, o caderno completa**, e a sessão SHALL, nesse momento, entregar a apuração parcial correspondente para gravação, que SHALL entrar na fila de envio de `result-sync`. Essa entrega SHALL acontecer uma vez, na transição de incompleto para completo, e SHALL NOT se repetir a cada quadro seguinte que apenas confirma um caderno já completo. Esta mudança SHALL NOT introduzir memória de mais de um caderno por vez: a folha de outro aluno continua começando um caderno novo, e o caderno substituído por outro antes de completar continua se perdendo, exatamente como hoje.

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

#### Scenario: O caderno guardado sobrevive ao fechamento do aplicativo

- **WHEN** o aplicativo é fechado com um caderno em andamento, e reaberto em seguida sobre a mesma prova
- **THEN** o caderno reaparece com o mesmo estado de cada região, sem regredir nenhuma capturada a não vista

#### Scenario: Trocar de aluno antes de fechar continua substituindo o caderno

- **WHEN** a folha de um segundo aluno aparece no quadro antes de o aplicativo ser fechado, e depois o aplicativo é fechado e reaberto
- **THEN** o caderno guardado e retomado é o do segundo aluno, e o do primeiro não reaparece

#### Scenario: O caderno completo é entregue para gravação

- **WHEN** a última região que faltava no caderno de um aluno é capturada e o contador chega a "N de N"
- **THEN** a apuração parcial desse caderno é entregue para gravação e passa a constar na fila de envio de `result-sync`, com a nota apresentada como não definitiva

#### Scenario: Confirmar um caderno já completo não duplica o envio

- **WHEN** quadros seguintes continuam reconhecendo um caderno que já completou e já foi entregue
- **THEN** nenhum resultado novo é entregue para o mesmo caderno

#### Scenario: Caderno incompleto substituído por outro aluno não é entregue

- **WHEN** a folha de outro aluno aparece no quadro antes de o caderno do primeiro aluno completar
- **THEN** nenhum resultado é entregue para o caderno do primeiro aluno, e ele continua se perdendo como antes desta mudança
