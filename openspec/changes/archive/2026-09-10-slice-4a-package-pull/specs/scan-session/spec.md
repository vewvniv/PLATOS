## ADDED Requirements

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

## MODIFIED Requirements

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
