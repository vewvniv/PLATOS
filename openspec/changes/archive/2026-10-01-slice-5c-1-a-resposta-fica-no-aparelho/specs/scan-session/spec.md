## RENAMED Requirements

- FROM: `### Requirement: Prova com discursiva mostra a parcial objetiva, não definitiva, e não guarda nada`
- TO: `### Requirement: Prova com discursiva mostra a parcial objetiva, não definitiva, e não grava resultado antes de completar`

## MODIFIED Requirements

### Requirement: Prova com discursiva mostra a parcial objetiva, não definitiva, e não grava resultado antes de completar

Quando o pacote da sessão declara que a prova não é corrigível só no aparelho (`fully_offline_gradable` falso), a sessão SHALL abrir e escanear como qualquer outra. Diante de uma folha dessa prova, a sessão SHALL apresentar:
- de qual aluno é a folha, pelo payload do QR;
- quais regiões da folha ela reconheceu: o gabarito e cada região discursiva, esta pela questão;
- quando o gabarito foi lido, a **parcial objetiva**: a pontuação objetiva apurada sobre o máximo objetivo, a pontuação das discursivas que aguardam correção e as pendências de revisão das objetivas;
- que a nota **não é definitiva**, porque a parte discursiva ainda não foi corrigida;
- que, enquanto o caderno não completa, **nenhum resultado foi gravado**, que as respostas discursivas capturadas **ficam neste aparelho**, e que o caderno completo é entregue para envio.

A parcial SHALL vir da apuração parcial do pacote da sessão, e SHALL NOT ser calculada pela sessão por conta própria. Enquanto o gabarito desse aluno não foi lido em nenhum quadro, a sessão SHALL NOT apresentar parcial. Quando ele já foi lido e o quadro corrente não o traz, a sessão SHALL apresentar a última parcial desse aluno. Quando a apuração parcial recusa a folha, a sessão SHALL apresentar o motivo da recusa, e não uma parcial.

Enquanto o caderno do aluno não completar — todas as regiões que a variante declara em estado capturada —, a sessão SHALL NOT produzir resultado: nenhum resultado SHALL ser gravado nem entrar na fila de envio, nem a parcial. Guardar a imagem da resposta de uma região discursiva **não é** produzir resultado, e SHALL NOT entrar na fila de envio. Ao completar, o caderno SHALL produzir o resultado parcial correspondente, pela regra do requisito "A completude da folha do aluno é mostrada por região", e esse resultado SHALL continuar não-definitivo: a parte discursiva ainda não foi corrigida. A folha de outra prova continua recusada com o motivo de sempre.

A prova só objetiva SHALL continuar sendo apurada e gravada exatamente como antes.

**O caderno em andamento — o do aluno cuja folha está sendo escaneada — SHALL sobreviver ao encerramento do processo do aplicativo**, e a sessão SHALL retomá-lo, com a última parcial que carregava, ao reabrir o escaneamento da mesma prova. Isto SHALL NOT contradizer "nenhum resultado foi gravado" enquanto incompleto: o que sobrevive é o estado de tela do caderno em andamento e as respostas que ele referencia, para continuidade do trabalho do professor, e enquanto o caderno não completa ele SHALL NOT ser lido, gravado nem enviado como resultado. Encerrar a sessão de uso do aparelho (sair de `device-session`) SHALL NOT apagar o caderno em andamento nem as respostas que ele referencia, pela mesma razão que resultado pendente de `result-sync` não é apagado por sair: é trabalho do professor ainda não concluído.

#### Scenario: Folha de prova com discursiva no quadro

- **WHEN** a sessão de uma prova com discursiva lê o gabarito e uma região discursiva de uma folha
- **THEN** a sessão apresenta o aluno da folha, as regiões reconhecidas, a parcial objetiva com as discursivas aguardando correção, que a nota não é definitiva, que nenhum resultado foi gravado e que as respostas capturadas ficam neste aparelho

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

#### Scenario: Guardar a imagem não é gravar resultado

- **WHEN** a resposta de uma região discursiva é guardada no aparelho e o caderno ainda não completou
- **THEN** a fila de envio não ganha nada, e a tela diz que nenhum resultado foi gravado e que a resposta fica neste aparelho

#### Scenario: Abrir a câmera numa prova com discursiva

- **WHEN** o escaneamento de uma prova com discursiva é aberto
- **THEN** a câmera abre e a sessão procura a folha, sem o aplicativo terminar com erro

#### Scenario: Prova só objetiva não muda

- **WHEN** a sessão de uma prova só objetiva lê uma folha
- **THEN** a nota é apurada, apresentada e entregue para gravar, como antes desta mudança

#### Scenario: O aplicativo fecha no meio da leitura de um aluno

- **WHEN** o processo do aplicativo termina enquanto o caderno de um aluno está em andamento, com a última parcial apurada e respostas guardadas
- **THEN** reabrir o escaneamento da mesma prova apresenta o mesmo aluno, o mesmo caderno, a mesma parcial e as mesmas respostas, sem exigir escanear de novo o que já tinha sido lido

#### Scenario: Sair não apaga o caderno em andamento

- **WHEN** o usuário sai da sessão do aparelho com um caderno de prova com discursiva em andamento
- **THEN** o caderno e as respostas que ele referencia continuam guardados, e reabrir o escaneamento desta prova os retoma

### Requirement: A completude da folha do aluno é mostrada por região

Numa prova com discursiva, a sessão SHALL manter, para o aluno cuja folha está sendo escaneada, o **caderno** dele: o conjunto de regiões que a variante declara, com o estado de cada uma. O conjunto esperado SHALL sair das regiões que o `LayoutMap` da variante declara. Cada região SHALL estar em um de três estados:
- **capturada**: lida, no gabarito, ou reconhecida **com a resposta guardada no aparelho**, na discursiva, em algum quadro desse aluno;
- **com problema**: presente num quadro e não lida, com o motivo, **ou reconhecida e sem resposta guardada** (recorte recusado ou imagem não gravada), com o motivo, e ainda não capturada;
- **não vista**: ainda não apareceu inteira em nenhum quadro desse aluno, ou teve a resposta descartada ou eliminada.

Uma região discursiva SHALL NOT contar como capturada sem resposta guardada: reconhecê-la, sozinho, não basta. Uma região capturada SHALL continuar capturada, mesmo que um quadro seguinte não a leia; as **únicas** saídas de capturada são a ação do professor de refazer a resposta e a eliminação da resposta do aparelho (requisito "A resposta guardada tem prazo no aparelho"), e as duas devolvem a região a não vista. Uma região com problema SHALL passar a capturada quando um quadro seguinte a ler e a resposta for guardada.

A sessão SHALL apresentar um indicador por região, distinto por estado, e SHALL apresentar quantas das regiões esperadas estão capturadas. O indicador da região de gabarito SHALL ser identificado como gabarito, e o de cada região discursiva, pelo **número que a questão tem na folha impressa**. O número SHALL NOT ser derivado de outra fonte que possa divergir do número impresso (ADR-0019: "todo número que o professor vê sai de um lugar só").

O caderno é do aluno que o payload identifica. Um quadro com a folha de **outro** aluno SHALL começar um caderno novo, e o anterior SHALL NOT ser misturado com ele.

**O caderno em andamento SHALL ser guardado em armazenamento local, para sobreviver ao encerramento do processo do aplicativo**, e SHALL **referenciar** a resposta de cada região discursiva capturada, sem conter a imagem. Guardar o caderno para a tela continua distinto de produzir o fato durável que `result-sync` grava: um não substitui o outro. **Quando a última região que faltava é capturada, o caderno completa**, e a sessão SHALL, nesse momento, entregar a apuração parcial correspondente para gravação, que SHALL entrar na fila de envio de `result-sync`. Como uma região discursiva só é capturada com resposta guardada, **o caderno só completa com todas as respostas discursivas guardadas**. Essa entrega SHALL acontecer uma vez, na transição de incompleto para completo, e SHALL NOT se repetir a cada quadro seguinte que apenas confirma um caderno já completo, **nem quando o professor refaz uma resposta de um caderno já entregue**. Esta mudança SHALL NOT introduzir memória de mais de um caderno por vez: a folha de outro aluno continua começando um caderno novo, e o caderno substituído por outro antes de completar continua se perdendo, exatamente como hoje.

Caderno guardado antes desta mudança, cuja região discursiva esteja capturada sem resposta, SHALL ser lido com essa região não vista. Caderno que referencia uma resposta que já não existe no aparelho SHALL ser lido com essa região não vista, e SHALL NOT apresentá-la como capturada.

#### Scenario: Caderno começa com tudo não visto

- **WHEN** a primeira folha de um aluno de uma prova com gabarito e duas discursivas aparece no quadro, trazendo o gabarito e a primeira discursiva, e a resposta da discursiva é guardada
- **THEN** o caderno mostra essas duas regiões capturadas, a segunda discursiva não vista, e "2 de 3"

#### Scenario: O indicador tem o número impresso

- **WHEN** o caderno de uma prova cuja primeira discursiva é impressa como questão 3 é apresentado
- **THEN** o indicador dessa região traz o número 3, o mesmo que a folha impressa mostra

#### Scenario: A segunda página completa o caderno

- **WHEN** em seguida a página com a segunda discursiva do mesmo aluno é reconhecida e a resposta dela é guardada
- **THEN** o caderno mostra as três regiões capturadas, e "3 de 3"

#### Scenario: Região com problema

- **WHEN** uma região discursiva está inteira no quadro e não é lida
- **THEN** ela aparece com problema, com o motivo, e não conta como capturada

#### Scenario: Reconhecida com recorte recusado não conta

- **WHEN** uma região discursiva é reconhecida e o recorte da resposta é recusado, com o motivo
- **THEN** ela aparece com problema, com o motivo do recorte, e não conta como capturada

#### Scenario: Reconhecida e guardada conta

- **WHEN** uma região discursiva é reconhecida e a resposta é guardada no aparelho
- **THEN** ela aparece como capturada, e o contador a soma

#### Scenario: Caderno sem a última resposta não completa

- **WHEN** todas as regiões do caderno estão lidas e reconhecidas, e a resposta de uma discursiva foi recusada
- **THEN** o contador não chega a "N de N", e nenhuma apuração é entregue para gravar

#### Scenario: Região com problema volta a capturada

- **WHEN** uma região discursiva com problema por recorte recusado é reconhecida em um quadro seguinte, e o recorte é aceito
- **THEN** ela passa a capturada, e o caderno completa se era a última

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
- **THEN** o caderno reaparece com o mesmo estado de cada região e com as mesmas respostas, sem regredir nenhuma capturada a não vista

#### Scenario: Trocar de aluno antes de fechar continua substituindo o caderno

- **WHEN** a folha de um segundo aluno aparece no quadro antes de o aplicativo ser fechado, e depois o aplicativo é fechado e reaberto
- **THEN** o caderno guardado e retomado é o do segundo aluno, e o do primeiro não reaparece

#### Scenario: O caderno completo é entregue para gravação

- **WHEN** a última região que faltava no caderno de um aluno é capturada, com a resposta guardada, e o contador chega a "N de N"
- **THEN** a apuração parcial desse caderno é entregue para gravação e passa a constar na fila de envio de `result-sync`, com a nota apresentada como não definitiva

#### Scenario: Confirmar um caderno já completo não duplica o envio

- **WHEN** quadros seguintes continuam reconhecendo um caderno que já completou e já foi entregue
- **THEN** nenhum resultado novo é entregue para o mesmo caderno

#### Scenario: Refazer uma resposta de caderno entregue não entrega de novo

- **WHEN** o professor refaz a resposta de uma região de um caderno já entregue, e a região volta a ser capturada
- **THEN** nenhum resultado novo é entregue para o mesmo caderno

#### Scenario: Caderno incompleto substituído por outro aluno não é entregue

- **WHEN** a folha de outro aluno aparece no quadro antes de o caderno do primeiro aluno completar
- **THEN** nenhum resultado é entregue para o caderno do primeiro aluno, e ele continua se perdendo como antes desta mudança

#### Scenario: Caderno de antes desta mudança

- **WHEN** o escaneamento reabre um caderno guardado antes desta mudança, com uma região discursiva capturada e sem resposta
- **THEN** essa região aparece como não vista, e o contador não a soma

#### Scenario: A resposta referenciada não existe mais

- **WHEN** o escaneamento reabre um caderno cuja resposta de uma região foi eliminada do aparelho
- **THEN** a região aparece como não vista, e a sessão não termina com erro nem mostra imagem em branco

## ADDED Requirements

### Requirement: A resposta capturada fica no aparelho, e o professor a vê

A resposta de uma região discursiva capturada SHALL ser guardada **no aparelho**, em armazenamento privado do aplicativo, como a imagem da área de resposta que o recorte entrega, em tons de cinza, e nada além dela: a imagem guardada SHALL NOT conter cabeçalho, enunciado nem tinta de fora da área. A resposta SHALL NOT ser enviada a servidor, SHALL NOT entrar na fila de envio de `result-sync` e SHALL NOT sair do aparelho por cópia de segurança nem por transferência de dispositivo (`device-session`, regra de extração de dados: a raiz do diretório de dados é coberta).

O indicador de cada região discursiva **capturada** SHALL permitir ver a resposta guardada, em tela cheia, identificada pelo número da questão na folha impressa. Quando a resposta foi sinalizada como desvio (`capture-omr`: "O desvio da resposta é sinalizado, e não corrigido em silêncio"), a tela SHALL dizer que o aluno escreveu fora da área de resposta e que o professor deve conferir a folha de papel; o aviso SHALL NOT esconder a imagem. Resposta não sinalizada SHALL NOT trazer aviso. A tela SHALL NOT alterar a resposta, e SHALL NOT apresentar a faixa de fora da área, que não sai do recorte.

A tela SHALL oferecer **refazer** a resposta: descartar a resposta guardada, eliminar o arquivo, e devolver a região a não vista, de modo que a próxima captura da região peça o recorte de novo. Refazer SHALL NOT afetar as outras regiões do caderno nem a parcial apresentada.

#### Scenario: Ver a resposta capturada

- **WHEN** o professor abre o indicador de uma região discursiva capturada
- **THEN** a tela mostra a imagem guardada da área de resposta, com o número da questão da folha, sem aviso de desvio quando a resposta não foi sinalizada

#### Scenario: Resposta sinalizada como desvio

- **WHEN** o professor abre uma resposta que foi sinalizada como desvio
- **THEN** a tela mostra a imagem e o aviso de que o aluno escreveu fora da área de resposta e de que a folha de papel deve ser conferida

#### Scenario: A resposta não sai do aparelho

- **WHEN** uma resposta é guardada, e o caderno completa e é entregue
- **THEN** o corpo do envio de `result-sync` não contém imagem nem referência a arquivo de resposta, e nenhuma requisição leva a imagem

#### Scenario: A imagem guardada é o recorte

- **WHEN** a resposta guardada é lida do armazenamento
- **THEN** ela tem as dimensões do recorte da área de resposta, e nenhuma parte do cabeçalho aparece nela

#### Scenario: Refazer a resposta

- **WHEN** o professor refaz a resposta de uma região capturada
- **THEN** o arquivo é eliminado, a região passa a não vista, o contador deixa de somá-la, e as demais regiões e a parcial continuam como estavam

#### Scenario: A próxima captura depois de refazer

- **WHEN** a região refeita é reconhecida em um quadro seguinte
- **THEN** o recorte é pedido de novo, e a nova resposta é a guardada

#### Scenario: O diretório das respostas está sob a regra de extração

- **WHEN** a regra de extração de dados do aplicativo é conferida contra o diretório onde as respostas são guardadas
- **THEN** o diretório está coberto pela negação de cópia de segurança e de transferência

### Requirement: A resposta guardada tem prazo no aparelho

A resposta guardada é dado de classe H (`docs/legal/politica-de-privacidade.md` §10.8; ADR-0012, decisão 4): **manuscrito de menor no dispositivo do professor**. O aplicativo SHALL eliminar do aparelho:
- toda resposta guardada há **30 dias ou mais**, contados da captura, em qualquer hipótese (a classe H diz "em até 30 dias", e o dia 30 já é o limite);
- toda resposta que **nenhum caderno guardado no aparelho referencia** — a do caderno substituído por outro aluno, a descartada ao refazer, a de um quadro cuja gravação não chegou a ser referenciada, **e o arquivo temporário de uma gravação interrompida**.

A eliminação SHALL rodar na abertura do aplicativo (a tela de sessão, que é a porta de entrada) e na abertura do escaneamento, **antes** de a câmera abrir e antes de o caderno guardado ser lido, e SHALL NOT eliminar resposta referenciada por caderno guardado que tenha menos de 30 dias. O relógio é o do aparelho. A eliminação SHALL NOT depender de o aluno, a prova ou a organização estarem na sessão corrente: o aparelho é compartilhado entre escolas. **Eliminar um arquivo que falha SHALL NOT impedir o escaneamento de abrir nem as outras eliminações**: o arquivo fica para a próxima eliminação, e a falha é contada.

Sair da sessão do aparelho **e a revogação do vínculo** SHALL NOT eliminar a resposta (é trabalho não concluído, como o resultado pendente e o caderno, que já sobrevivem aos dois caminhos), e enviar a parcial SHALL NOT eliminá-la: a correção discursiva ainda precisa da imagem, e o gatilho "após a sincronização" da classe H é o da nota, que não existe nesta mudança. O prazo de 30 dias SHALL ser o único limite enquanto esse gatilho não existe.

Resposta eliminada SHALL devolver a região a não vista, e SHALL NOT deixar caderno apontando para arquivo inexistente como se ele existisse (requisito "A completude da folha do aluno é mostrada por região").

#### Scenario: Resposta com 31 dias

- **WHEN** a eliminação roda e uma resposta foi capturada há 31 dias
- **THEN** o arquivo é eliminado, e o caderno que a referenciava é lido com a região não vista

#### Scenario: Resposta com exatamente 30 dias

- **WHEN** a eliminação roda e uma resposta foi capturada há exatamente 30 dias
- **THEN** o arquivo é eliminado

#### Scenario: Resposta com 29 dias é mantida

- **WHEN** a eliminação roda e uma resposta referenciada por um caderno guardado foi capturada há 29 dias
- **THEN** o arquivo é mantido, e a região continua capturada

#### Scenario: Resposta do caderno substituído

- **WHEN** a folha de outro aluno substituiu o caderno, o aplicativo foi fechado e reaberto
- **THEN** as respostas do caderno substituído são eliminadas, e as do caderno corrente são mantidas

#### Scenario: Arquivo que nenhum caderno referencia

- **WHEN** o aplicativo terminou depois de gravar uma resposta e antes de guardar o caderno que a referenciaria, e é reaberto
- **THEN** o arquivo é eliminado na abertura

#### Scenario: Refazer elimina o arquivo na hora, e a eliminação é a rede

- **WHEN** o professor refaz uma resposta, e o arquivo dela por algum motivo não foi eliminado na hora
- **THEN** a próxima eliminação o elimina, porque nenhum caderno guardado o referencia

#### Scenario: A eliminação roda antes da câmera

- **WHEN** o escaneamento é aberto com respostas vencidas no aparelho
- **THEN** as respostas vencidas são eliminadas antes de o primeiro quadro ser analisado

#### Scenario: Sair da sessão não elimina a resposta

- **WHEN** o usuário sai da sessão do aparelho com respostas guardadas de menos de 30 dias e referenciadas por caderno
- **THEN** as respostas continuam no aparelho

#### Scenario: A revogação do vínculo não elimina a resposta

- **WHEN** o vínculo do aparelho é revogado com respostas guardadas de menos de 30 dias e referenciadas por caderno
- **THEN** as respostas continuam no aparelho, como o caderno que as referencia

#### Scenario: Enviar a parcial não elimina a resposta

- **WHEN** o caderno completa e a parcial é entregue e confirmada pelo servidor
- **THEN** as respostas do caderno continuam no aparelho

#### Scenario: Eliminação que falha não impede o escaneamento

- **WHEN** o escaneamento é aberto e um dos arquivos vencidos não pode ser eliminado
- **THEN** os outros arquivos vencidos são eliminados, o escaneamento abre, o caderno é retomado, e o arquivo que falhou continua no aparelho até a próxima eliminação

#### Scenario: Arquivo temporário de uma gravação interrompida

- **WHEN** o aplicativo terminou no meio da gravação de uma resposta, deixando um arquivo temporário, e é reaberto
- **THEN** o temporário é eliminado na abertura, porque nenhum caderno o referencia

#### Scenario: Outra organização no mesmo aparelho

- **WHEN** a eliminação roda numa sessão de uma organização e há respostas vencidas de outra organização no aparelho
- **THEN** as vencidas das duas são eliminadas, e as não vencidas e referenciadas das duas são mantidas
