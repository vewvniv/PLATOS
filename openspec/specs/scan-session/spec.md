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

### Requirement: Prova com discursiva mostra a parcial objetiva, não definitiva, e não guarda nada

Quando o pacote da sessão declara que a prova não é corrigível só no aparelho (`fully_offline_gradable` falso), a sessão SHALL abrir e escanear como qualquer outra. Diante de uma folha dessa prova, a sessão SHALL apresentar:
- de qual aluno é a folha, pelo payload do QR;
- quais regiões da folha ela reconheceu: o gabarito e cada região discursiva, esta pela questão;
- quando o gabarito foi lido, a **parcial objetiva**: a pontuação objetiva apurada sobre o máximo objetivo, a pontuação das discursivas que aguardam correção e as pendências de revisão das objetivas;
- que a nota **não é definitiva**, porque a parte discursiva ainda não foi corrigida;
- que **nada foi guardado**.

A parcial SHALL vir da apuração parcial do pacote da sessão, e SHALL NOT ser calculada pela sessão por conta própria. Enquanto o gabarito desse aluno não foi lido em nenhum quadro, a sessão SHALL NOT apresentar parcial. Quando ele já foi lido e o quadro corrente não o traz, a sessão SHALL apresentar a última parcial desse aluno. Quando a apuração parcial recusa a folha, a sessão SHALL apresentar o motivo da recusa, e não uma parcial.

Para essa prova, a sessão SHALL NOT produzir resultado: nada SHALL ser gravado nem entrar na fila de envio, nem a parcial. A folha de outra prova continua recusada com o motivo de sempre.

A prova só objetiva SHALL continuar sendo apurada e gravada exatamente como antes.

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
