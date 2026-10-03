## Purpose

Escanear uma folha de respostas com a câmera do aparelho e mostrar o resultado a quem a segura. É a superfície que transforma o pipeline de captura em coisa usável: pede a permissão, procura a folha no que a câmera vê, diz o que fazer quando não acha, e apresenta a nota junto do que a produziu.

## Requirements

### Requirement: A câmera só é usada com permissão concedida, e a recusa é um estado explicado

O aplicativo SHALL pedir a permissão de câmera antes de abrir o preview, e SHALL NOT abrir a câmera sem ela.

Com a permissão negada, o aplicativo SHALL mostrar um estado que explica para que a câmera serve e oferece pedir de novo. Ele SHALL NOT ficar numa tela vazia, nem tentar escanear sem imagem.

#### Scenario: Permissão ainda não concedida

- **WHEN** o aplicativo é aberto sem permissão de câmera
- **THEN** a permissão é pedida, e o preview não abre antes da resposta

#### Scenario: Permissão negada

- **WHEN** a permissão é negada
- **THEN** a tela explica para que a câmera serve e oferece pedir novamente, sem tentar escanear

### Requirement: A leitura acontece sobre o que a câmera vê, e o resultado é da folha que está na frente

A sessão SHALL analisar quadros da câmera contra o `LayoutMap` do pacote conferido pelo gate de
pré-voo, aplicando o limiar que o aplicativo declara, até que uma leitura feche ou a sessão termine.

Enquanto nenhuma folha fecha, a sessão SHALL permanecer procurando, e SHALL NOT apresentar resultado
nenhum.

Um resultado apresentado SHALL corresponder à folha que o produziu, identificada pelo payload do QR.
Trocar de folha SHALL descartar o resultado anterior antes de apresentar o novo — resultado obsoleto
na tela é indistinguível de resultado correto para quem lê.

A sessão SHALL ser local: analisar um quadro SHALL NOT depender de rede. Obter o pacote depende de
rede quando ele não está no aparelho, e isso acontece antes da sessão, no gate.

#### Scenario: Folha reconhecida

- **WHEN** uma folha da prova escolhida entra no quadro em condições que o pipeline consegue ler
- **THEN** a sessão apresenta o resultado dessa folha, identificado pelo payload que o QR trouxe

#### Scenario: Nenhuma folha no quadro

- **WHEN** a câmera não vê folha nenhuma
- **THEN** a sessão continua procurando e nenhum resultado é apresentado

#### Scenario: Troca de folha

- **WHEN** uma segunda folha, com payload diferente, é lida depois de a primeira ter sido apresentada
- **THEN** o resultado apresentado passa a ser o da segunda, e nenhum dado da primeira permanece na
  tela

#### Scenario: Sem rede

- **WHEN** o aparelho está sem rede e o pacote da prova escolhida já passou pelo gate
- **THEN** a sessão escaneia e apresenta o resultado normalmente

### Requirement: A sessão distingue não achar a folha de não conseguir ler a folha que achou

Enquanto nenhuma leitura fecha, a sessão SHALL dizer em qual dos dois momentos ela está: nenhuma folha reconhecida no quadro, ou folha reconhecida cuja leitura não fechou. São situações diferentes para quem segura o aparelho — a primeira pede mover a câmera, a segunda diz que a folha está enquadrada e alguma outra coisa impediu.

A distinção SHALL vir do estágio em que o pipeline parou, e SHALL NOT ser inferida do texto da mensagem de recusa.

Tendo a folha sido reconhecida e a leitura não fechado, a sessão SHALL apresentar o motivo que o pipeline produziu.

A sessão SHALL NOT afirmar causa que ela não mede. Em particular, ela SHALL NOT orientar distância a partir da resolução medida sobre o papel: o corpus da fatia 3b, medido inteiro, mostra que a resolução não prevê a decodificação do código bidimensional — ver `docs/cobertura-fatia-3b.md`.

#### Scenario: Nada reconhecido no quadro

- **WHEN** nenhum marcador da folha é encontrado no quadro
- **THEN** a sessão diz que ainda procura a folha, e não apresenta motivo de recusa nenhum

#### Scenario: Folha reconhecida, leitura não fecha

- **WHEN** os marcadores são encontrados mas a leitura não fecha
- **THEN** a sessão diz que encontrou a folha e não conseguiu ler, com o motivo que o pipeline produziu, e não apresenta resultado

### Requirement: Folha que não é desta prova é recusada com motivo

A sessão SHALL recusar folha cujo identificador de prova não corresponda ao pacote conferido, e
SHALL dizer que a folha é de outra prova.

A correspondência SHALL ser conferida contra o payload do QR da folha, e SHALL NOT ser presumida de
qual prova foi escolhida. Confiar na escolha deixaria um pacote íntegro da prova errada passar pelo
portão: o pedido diria que é a prova certa, o hash confirmaria que o pacote é íntegro, e a nota
sairia plausível e errada. Íntegro e errado é a forma de falha que esta conferência existe para
impedir.

A sessão SHALL recusar folha cuja variante o pacote conferido não declara, e SHALL dizer qual
variante a folha traz e quais o pacote declara.

A sessão SHALL recusar folha cujo corredor declarado não contenha o limiar do aplicativo, e SHALL
dizer que a folha não é legível por esta versão.

Recusa SHALL ser apresentada como estado com motivo legível, e SHALL NOT ser silêncio nem resultado
parcial.

#### Scenario: Folha de outra prova

- **WHEN** uma folha de outra prova é lida contra o pacote conferido
- **THEN** a sessão recusa dizendo que a folha é de outra prova, e nenhuma nota é apresentada

#### Scenario: Folha de outra prova cujo pacote também está no aparelho

- **WHEN** o aparelho guarda os pacotes de duas provas, uma delas é escolhida, e uma folha da outra
  é lida
- **THEN** a sessão recusa dizendo que a folha é de outra prova, e não troca de pacote para
  apurá-la

#### Scenario: Folha de variante que o pacote não declara

- **WHEN** uma folha cujo QR traz variante ausente do pacote conferido é lida
- **THEN** a sessão recusa dizendo qual variante veio e quais o pacote declara, e nenhuma nota é
  apresentada

#### Scenario: Folha cujo corredor exclui o limiar

- **WHEN** uma folha cujo `ink_budget` declara corredor que não contém o limiar do aplicativo é lida
- **THEN** a sessão recusa dizendo que a folha não é legível por esta versão do aplicativo

### Requirement: O resultado mostra a nota e o que a sustenta

O resultado SHALL apresentar a pontuação apurada e a pontuação máxima da prova.

O resultado SHALL apresentar **de quem é a folha**, pelo nome de apresentação que o roster guardado
traz para o token lido. Apresentar apenas o token é apresentar uma nota sem dono: quem corrige
precisa saber a quem devolver, e foi para isso que ADR-0002 tirou o nome do pacote imutável e o pôs
num roster entregue ao lado.

Quando o token lido **não tiver linha no roster**, o resultado SHALL apresentar o token e SHALL dizer
que aquela folha não está no roster desta prova. SHALL NOT apresentar a folha como ilegível nem
recusar o resultado: a nota foi apurada e é válida, e a folha avulsa de um aluno fora da lista é o
caso que `exam-package` separa ao entregar roster vazio em vez de negar a prova.

O nome apresentado a partir de roster guardado SHALL seguir a marcação de dado cacheado de
`device-session`, e SHALL NOT ser apresentado como fresco. A regra mora lá, sobre todo dado guardado;
aqui ela só é aplicada — esta fatia estende o alcance daquele requisito para o roster, que a visão
guardada explicitamente não cobre.

Havendo questão pendente de revisão humana, o resultado SHALL apresentá-la como pendência, com o motivo, e SHALL NOT apresentar a nota como fechada.

A cobertura medida de cada bolha SHALL permanecer recuperável a partir do resultado. Quem revisa uma pendência precisa do número que a produziu, e não apenas da palavra "indecisa".

#### Scenario: Folha sem pendência

- **WHEN** uma folha em que toda questão tem alternativa marcada ou está em branco é lida
- **THEN** o resultado apresenta a pontuação, o máximo e o nome do aluno daquele token, e a nota é
  apresentada como fechada

#### Scenario: Folha com pendência

- **WHEN** uma folha com múltipla marcação ou questão indecisa é lida
- **THEN** o resultado lista as questões pendentes com seus motivos, e a nota não é apresentada como fechada

#### Scenario: A cobertura sobrevive até a tela

- **WHEN** um resultado é apresentado
- **THEN** a cobertura medida de cada bolha continua recuperável a partir dele

#### Scenario: Folha de aluno fora do roster

- **WHEN** uma folha cujo token não tem linha no roster guardado desta prova é lida
- **THEN** o resultado apresenta a nota apurada e o token, diz que a folha não está no roster desta
  prova, e não é apresentado como falha de leitura

#### Scenario: O nome vem do cache e é marcado

- **WHEN** o resultado é apresentado com o nome vindo do roster guardado no aparelho
- **THEN** o nome carrega a marca de dado cacheado, e não é apresentado como fresco

### Requirement: A sessão só existe sobre pacote puxado e conferido

A sessão de escaneamento SHALL operar sobre o pacote que o gate de pré-voo aprovou, e SHALL NOT
operar sobre pacote embutido no aplicativo.

O aplicativo SHALL NOT conter pacote de prova nenhum entre seus recursos. Um pacote embutido que
sobreviva "por enquanto" vira caminho permanente de reserva, e com ele o portão binário — pacote
presente ou ausente — morre em silêncio: o aparelho passaria a escanear com um pacote que ninguém
puxou nem conferiu, e nada na tela diria isso.

Não havendo pacote conferido, o aplicativo SHALL apresentar a recusa com motivo e SHALL NOT abrir a
câmera. Ele SHALL NOT abrir uma sessão que não vai ler nada.

#### Scenario: Nenhum pacote embutido no aplicativo

- **WHEN** os recursos empacotados do aplicativo são inspecionados
- **THEN** não há pacote de prova entre eles, e não existe caminho que carregue um sem passar pelo
  gate

#### Scenario: Sem pacote conferido, a câmera não abre

- **WHEN** o escaneamento é pedido para uma prova sem pacote conferido no aparelho
- **THEN** o aplicativo apresenta o motivo e a câmera não é aberta

#### Scenario: Com pacote conferido, a sessão é a mesma de sempre

- **WHEN** o escaneamento abre sobre um pacote conferido
- **THEN** a análise, a recusa e a apresentação da nota se comportam como especificado nos demais
  requisitos desta capacidade

### Requirement: A sessão não abre sem saber de qual prova ela é

O escaneamento SHALL abrir apenas quando o aplicativo souber, antes de a câmera ligar, **de qual
prova** a sessão é. Faltando o identificador da prova, o aplicativo SHALL apresentar a recusa com
motivo e SHALL NOT abrir a câmera.

O motivo dessa recusa SHALL ser **próprio**, e SHALL ser distinguível de cada um dos motivos pelos
quais o gate de pré-voo já barra a sessão. Colapsá-lo em "não há pacote conferido" diria ao professor
para baixar de novo uma prova que já está conferida no aparelho, e a ação sugerida não conserta nada.

Estando a sessão aberta, o identificador da prova e a organização SHALL estar disponíveis a todo o
caminho que vai da apuração à gravação. **Nenhuma nota apurada SHALL ser descartada por falta deles**:
a ausência é decidida **antes** de a câmera abrir, e não no momento de gravar. Uma folha medida cuja
nota aparece na tela e não vira resultado durável é falha em silêncio, e o sistema nunca falha em
silêncio.

#### Scenario: Sem o identificador da prova, a câmera não abre

- **WHEN** o escaneamento é pedido sem o identificador da prova
- **THEN** o aplicativo apresenta a recusa com motivo próprio e a câmera não é aberta

#### Scenario: O motivo é distinguível dos motivos do gate

- **WHEN** a recusa por falta do identificador da prova é apresentada
- **THEN** ela é distinguível de "sem rede", "pacote ausente", "conferência falhou", "versão
  insuficiente" e "roster ausente", e não pede ao professor que baixe a prova de novo

#### Scenario: Aberta a sessão, a nota apurada sempre vira resultado durável

- **WHEN** uma folha é apurada numa sessão aberta
- **THEN** o resultado é gravado, e não existe caminho em que a nota apareça na tela sem ser gravada

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

A resposta guardada é dado de classe H (o conjunto de regras do cache no aparelho que o ADR-0012, decisão 4, define desde a atualização de 2026-10-03; a política v2.0 não o descreve): **manuscrito de menor no dispositivo do professor**. O aplicativo SHALL eliminar do aparelho:
- toda resposta guardada há **30 dias ou mais**, contados da captura, em qualquer hipótese (a classe H diz "em até 30 dias", e o dia 30 já é o limite);
- toda resposta que **nenhum caderno guardado no aparelho referencia** — a do caderno substituído por outro aluno, a descartada ao refazer, a de um quadro cuja gravação não chegou a ser referenciada, **e o arquivo temporário de uma gravação interrompida**;
- toda resposta de um caderno **corrigido** cuja nota o **servidor confirmou** (o gatilho "após a sincronização" da classe H, que nasce com a nota).

A eliminação por prazo e por falta de referência SHALL rodar na abertura do aplicativo (a tela de sessão, que é a porta de entrada) e na abertura do escaneamento, **antes** de a câmera abrir e antes de o caderno guardado ser lido, **e também por agendamento periódico em segundo plano**, programado para rodar **ao menos uma vez por dia**, sem depender de o aplicativo abrir: aparelho que guarda a resposta e nunca mais abre o aplicativo SHALL expurgá-la dentro do prazo. A eliminação SHALL NOT eliminar resposta referenciada por caderno guardado que tenha menos de 30 dias. O relógio é o do aparelho. A eliminação SHALL NOT depender de o aluno, a prova ou a organização estarem na sessão corrente: o aparelho é compartilhado entre escolas. **Eliminar um arquivo que falha SHALL NOT impedir o escaneamento de abrir nem as outras eliminações**: o arquivo fica para a próxima eliminação, e a falha é contada.

**A eliminação em segundo plano SHALL NOT eliminar o arquivo que o escaneamento aberto está gravando ou acaba de gravar e ainda não referenciou no caderno**: ela o deixa para a próxima eliminação. Eliminar cedo demais um arquivo que ninguém ainda referencia SHALL NOT corromper o caderno nem a gravação em curso.

A nota **gravada e ainda não confirmada** pelo servidor SHALL NOT eliminar a resposta, e a recusa definitiva da nota também não: a imagem só sai pela confirmação, pelo prazo ou por não ser referenciada. Sair da sessão do aparelho **e a revogação do vínculo** SHALL NOT eliminar a resposta (é trabalho não concluído, como o resultado pendente e o caderno, que já sobrevivem aos dois caminhos), e enviar a **parcial** SHALL NOT eliminá-la: a correção discursiva ainda precisa da imagem.

Resposta eliminada de caderno **em andamento** SHALL devolver a região a não vista, e SHALL NOT deixar caderno apontando para arquivo inexistente como se ele existisse (requisito "A completude da folha do aluno é mostrada por região"). A eliminação das respostas de um caderno **corrigido** SHALL NOT o devolver a incompleto (requisito "O professor dá a nota das discursivas sobre a imagem, na hora").

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

#### Scenario: A confirmação da nota elimina as respostas

- **WHEN** o servidor confirma a nota de um caderno corrigido
- **THEN** as respostas do caderno são eliminadas na hora, e o caderno continua corrigido, sem voltar a incompleto

#### Scenario: Nota gravada e não confirmada mantém a resposta

- **WHEN** a nota foi gravada como pendente e o servidor ainda não a confirmou, por falta de rede ou recusa
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

#### Scenario: Aparelho que nunca mais abre o aplicativo

- **WHEN** uma resposta foi guardada há 31 dias e o aplicativo não foi aberto desde então
- **THEN** a eliminação agendada em segundo plano a elimina, sem o aplicativo abrir

#### Scenario: O segundo plano não elimina o que o escaneamento está gravando

- **WHEN** a eliminação em segundo plano roda com o escaneamento aberto, enquanto uma resposta acaba de ser gravada e ainda não foi referenciada pelo caderno
- **THEN** o arquivo é mantido para a próxima eliminação, a gravação conclui e o caderno o referencia normalmente

### Requirement: O professor dá a nota das discursivas sobre a imagem, na hora

Quando o caderno de uma prova com discursiva está **completo** (todas as regiões capturadas, com as respostas guardadas) e ainda sem nota, a sessão SHALL oferecer **dar a nota**. A tela SHALL mostrar, para cada questão discursiva da variante, a imagem guardada da resposta, identificada pelo **número que a questão tem na folha impressa** (e com o aviso de desvio quando a resposta foi sinalizada, como em "A resposta capturada fica no aparelho, e o professor a vê"), e SHALL pedir a pontuação dela, **de 0 até o valor que o pacote da sessão declara**, fracionária com **até 2 casas decimais**. As guardas de pontuação SHALL ser as de `scoring` ("A nota do professor completa a parcial sem recalcular a parte objetiva"), aplicadas pelo **mesmo** código que o servidor usa para conferir: pontuação negativa, acima do valor, com mais de 2 casas ou não numérica SHALL ser recusada na tela, dizendo a questão e o motivo.

A nota SHALL cobrir **todas** as discursivas da variante de uma vez: a tela SHALL NOT permitir gravar com alguma sem pontuação. Antes de gravar, a tela SHALL mostrar o **total** resultante (os pontos objetivos da parcial mais as discursivas) sobre o máximo da prova, e SHALL pedir confirmação. Confirmada, a nota SHALL ser gravada como resultado durável pendente (`result-sync`: "A nota do professor vira resultado durável pendente e é enviada pela rota própria"), e o caderno passa a **corrigido**.

**Caderno corrigido deixa de ser caderno em andamento.** Ele SHALL NOT voltar a incompleto quando as imagens são eliminadas, SHALL NOT entregar a parcial de novo, e SHALL NOT oferecer dar a nota outra vez. A mesma folha lida de novo depois disso começa uma **captura nova**, com o recorte pedido de novo.

A tela SHALL NOT alterar a imagem da resposta, e SHALL NOT enviá-la. Prova só objetiva SHALL NOT ter tela de nota e SHALL continuar sendo apurada e gravada como antes.

#### Scenario: Dar a nota sobre o caderno completo

- **WHEN** o caderno de uma prova com duas discursivas completa e o professor abre a nota
- **THEN** a tela mostra a imagem de cada discursiva com o número impresso da questão e pede uma pontuação para cada uma, dentro da faixa que o pacote declara

#### Scenario: Pontuação fracionária

- **WHEN** o professor dá 1.5 a uma discursiva e 1.75 a outra
- **THEN** a tela aceita, mostra o total exato, e a nota gravada leva exatamente 1.5 e 1.75

#### Scenario: Pontuação inválida é recusada na tela

- **WHEN** o professor dá a uma discursiva uma pontuação acima do valor do pacote, negativa, com 3 casas ou não numérica
- **THEN** a tela a recusa nomeando a questão e o motivo, e nada é gravado

#### Scenario: Discursiva sem pontuação

- **WHEN** o professor tenta gravar com uma discursiva sem pontuação
- **THEN** a tela não grava e diz qual falta

#### Scenario: A nota vira pendente e o caderno, corrigido

- **WHEN** o professor confirma o total
- **THEN** o resultado é gravado como pendente na fila de envio, e o caderno passa a corrigido sem voltar a incompleto nem reentregar a parcial

#### Scenario: Caderno incompleto não tem nota

- **WHEN** o caderno de um aluno ainda tem região não capturada
- **THEN** a tela não oferece dar a nota

#### Scenario: Prova só objetiva não muda

- **WHEN** a sessão de uma prova só objetiva lê uma folha
- **THEN** a nota é apurada, apresentada e entregue para gravar, como antes, sem tela de nota

#### Scenario: A mesma folha lida de novo depois de corrigida

- **WHEN** a folha de um aluno cujo caderno foi corrigido é escaneada de novo
- **THEN** começa uma captura nova e o recorte das respostas é pedido de novo

### Requirement: O caderno completo sem nota não é substituído em silêncio

Enquanto o caderno corrente está **completo e sem nota**, a folha de **outro** aluno SHALL NOT substituí-lo sem confirmação do professor: a sessão SHALL dizer que o caderno do aluno corrente aguarda nota e que **descartá-lo perde as respostas capturadas**, e oferecer **dar a nota** ou **descartar e seguir**. Enquanto a escolha não é feita, a sessão SHALL NOT iniciar o caderno do outro aluno. Descartar SHALL ser ato explícito do professor; ao descartar, o caderno e as respostas se perdem como já se perde o caderno incompleto substituído, e nada é gravado nem enviado.

A regra vale só para o caderno completo sem nota. O caderno incompleto substituído por outro aluno continua se perdendo como em "A completude da folha do aluno é mostrada por região", e o caderno corrigido não pede confirmação.

#### Scenario: Outro aluno aparece com a nota por dar

- **WHEN** a folha de outro aluno é reconhecida enquanto o caderno corrente está completo e sem nota
- **THEN** a sessão não inicia o caderno novo, diz que descartar perde as respostas, e oferece dar a nota ou descartar e seguir

#### Scenario: Descartar e seguir

- **WHEN** o professor escolhe descartar e seguir
- **THEN** o caderno e as respostas dele são perdidos, nenhum resultado é gravado, e o caderno do outro aluno começa

#### Scenario: Dar a nota e seguir

- **WHEN** o professor escolhe dar a nota, e a grava
- **THEN** o caderno fica corrigido, e a folha do outro aluno inicia o caderno novo sem confirmação

#### Scenario: Incompleto continua se perdendo

- **WHEN** a folha de outro aluno substitui um caderno que ainda não completou
- **THEN** ele é substituído sem pedir confirmação, como antes desta mudança

### Requirement: Com a coleta ligada, a nota confirmada deixa a cópia das discursivas

Com a coleta do corpus **ligada** (`measurement-corpus`: "A coleta existe só no aplicativo de depuração e vem desligada"), ao confirmar a nota de um caderno (requisito "O professor dá a nota das discursivas sobre a imagem, na hora"), o aplicativo SHALL copiar **todas** as discursivas da folha como amostras do corpus, cada uma com a pontuação que o professor deu **a ela** e o máximo da questão. A cópia não é por resposta: o professor SHALL NOT escolher quais respostas entram.

A cópia SHALL ocorrer **antes** de a nota ser gravada como resultado durável pendente: a imagem da resposta é eliminada quando o servidor confirma a nota ("A resposta guardada tem prazo no aparelho"), e a cópia SHALL NOT perder essa corrida. Cada amostra SHALL ser completa ou inexistente.

**A falha da cópia SHALL NOT impedir nem desfazer a nota**: a nota é gravada como resultado durável pendente como sempre, e o aplicativo de depuração SHALL dizer qual amostra não foi guardada. **A nota que não foi gravada SHALL NOT deixar amostra**: se a gravação falhar depois da cópia, as amostras copiadas por ela SHALL ser eliminadas. Cancelar, sair da tela ou não confirmar a nota SHALL NOT copiar nada.

A cópia SHALL NOT alterar a imagem da resposta guardada nem a eliminação dela, e uma revisão posterior da nota SHALL NOT alterar a amostra já copiada. Com a coleta desligada, e no aplicativo de release, a tela de nota e a gravação da nota SHALL se comportar como sem a coleta.

#### Scenario: Coleta desligada não copia

- **WHEN** o professor confirma a nota de uma folha com duas discursivas e a coleta está desligada
- **THEN** a nota é gravada e nenhuma amostra é criada

#### Scenario: Coleta ligada copia todas as discursivas

- **WHEN** a coleta está ligada e o professor dá 1.5 e 1.75 às duas discursivas e confirma a nota
- **THEN** duas amostras são criadas, uma com a pontuação 1.5 e outra com 1.75, cada uma com o máximo da própria questão

#### Scenario: Nota cancelada não copia

- **WHEN** a coleta está ligada e o professor sai da tela de nota sem confirmar
- **THEN** nenhuma amostra é criada e a resposta guardada segue como estava

#### Scenario: A cópia vence a eliminação da imagem

- **WHEN** a coleta está ligada e o servidor confirma a nota imediatamente após ela ser gravada
- **THEN** as amostras já existem, copiadas antes da gravação, com a imagem inteira

#### Scenario: Falha na cópia não derruba a nota

- **WHEN** a coleta está ligada, o professor confirma a nota, e a cópia de uma imagem falha
- **THEN** a nota é gravada como resultado durável pendente, o aplicativo diz qual amostra não foi guardada, e nada fica pela metade

#### Scenario: Nota não gravada não deixa amostra

- **WHEN** a coleta está ligada, as amostras são copiadas e a gravação da nota falha
- **THEN** as amostras copiadas para essa nota são eliminadas

#### Scenario: A eliminação da resposta não muda

- **WHEN** o servidor confirma a nota de um caderno cujas discursivas foram copiadas
- **THEN** a imagem da resposta é eliminada como em "A resposta guardada tem prazo no aparelho", e as amostras continuam no aparelho
