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

A sessão SHALL analisar quadros da câmera contra o `LayoutMap` do pacote embutido, aplicando o limiar que o aplicativo declara, até que uma leitura feche ou a sessão termine.

Enquanto nenhuma folha fecha, a sessão SHALL permanecer procurando, e SHALL NOT apresentar resultado nenhum.

Um resultado apresentado SHALL corresponder à folha que o produziu, identificada pelo payload do QR. Trocar de folha SHALL descartar o resultado anterior antes de apresentar o novo — resultado obsoleto na tela é indistinguível de resultado correto para quem lê.

A sessão SHALL ser local: analisar um quadro SHALL NOT depender de rede.

#### Scenario: Folha reconhecida

- **WHEN** uma folha da prova embutida entra no quadro em condições que o pipeline consegue ler
- **THEN** a sessão apresenta o resultado dessa folha, identificado pelo payload que o QR trouxe

#### Scenario: Nenhuma folha no quadro

- **WHEN** a câmera não vê folha nenhuma
- **THEN** a sessão continua procurando e nenhum resultado é apresentado

#### Scenario: Troca de folha

- **WHEN** uma segunda folha, com payload diferente, é lida depois de a primeira ter sido apresentada
- **THEN** o resultado apresentado passa a ser o da segunda, e nenhum dado da primeira permanece na tela

#### Scenario: Sem rede

- **WHEN** o aparelho está sem rede
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

A sessão SHALL recusar folha cujo identificador de prova não corresponda ao pacote carregado, e SHALL dizer que a folha é de outra prova.

A sessão SHALL recusar folha cujo corredor declarado não contenha o limiar do aplicativo, e SHALL dizer que a folha não é legível por esta versão.

Recusa SHALL ser apresentada como estado com motivo legível, e SHALL NOT ser silêncio nem resultado parcial.

#### Scenario: Folha de outra prova

- **WHEN** uma folha de outra prova é lida contra o pacote carregado
- **THEN** a sessão recusa dizendo que a folha é de outra prova, e nenhuma nota é apresentada

#### Scenario: Folha cujo corredor exclui o limiar

- **WHEN** uma folha cujo `ink_budget` declara corredor que não contém o limiar do aplicativo é lida
- **THEN** a sessão recusa dizendo que a folha não é legível por esta versão do aplicativo

### Requirement: O resultado mostra a nota e o que a sustenta

O resultado SHALL apresentar a pontuação apurada e a pontuação máxima da prova.

Havendo questão pendente de revisão humana, o resultado SHALL apresentá-la como pendência, com o motivo, e SHALL NOT apresentar a nota como fechada.

A cobertura medida de cada bolha SHALL permanecer recuperável a partir do resultado. Quem revisa uma pendência precisa do número que a produziu, e não apenas da palavra "indecisa".

#### Scenario: Folha sem pendência

- **WHEN** uma folha em que toda questão tem alternativa marcada ou está em branco é lida
- **THEN** o resultado apresenta a pontuação e o máximo, e a nota é apresentada como fechada

#### Scenario: Folha com pendência

- **WHEN** uma folha com múltipla marcação ou questão indecisa é lida
- **THEN** o resultado lista as questões pendentes com seus motivos, e a nota não é apresentada como fechada

#### Scenario: A cobertura sobrevive até a tela

- **WHEN** um resultado é apresentado
- **THEN** a cobertura medida de cada bolha continua recuperável a partir dele
