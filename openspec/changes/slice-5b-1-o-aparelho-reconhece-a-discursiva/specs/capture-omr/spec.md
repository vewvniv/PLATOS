## MODIFIED Requirements

### Requirement: A geometria da leitura vem do `LayoutMap`, nunca da imagem

A leitura SHALL derivar a posição de cada bolha das coordenadas normalizadas que o `LayoutMap` declara para a região, projetadas pelos marcadores da região encontrados na captura. A leitura SHALL NOT inferir posição de bolha por detecção de círculo, por espaçamento regular presumido, nem por qualquer propriedade medida na própria imagem.

Os marcadores esperados de uma região SHALL ser exatamente os `marker_ids` que o `LayoutMap` declara **para aquela região** — quatro para a região de gabarito, dois para a região discursiva — e SHALL NOT ser todos os marcadores da página dela. Uma página pode trazer marcadores de mais de uma região, e os de outra região no mesmo quadro SHALL NOT impedir a leitura desta.

Uma captura em que nenhuma região do `LayoutMap` tenha **todos** os seus `marker_ids` declarados encontrados SHALL ser recusada, identificando os identificadores encontrados e os que o mapa declara.

#### Scenario: Bolhas vêm do mapa

- **WHEN** uma captura de uma folha é lida contra o `LayoutMap` que a gerou
- **THEN** cada bolha medida corresponde a uma bolha declarada na região, e o conjunto medido é exatamente o conjunto declarado

#### Scenario: Folha de outra prova sob estes marcadores

- **WHEN** uma captura é lida contra um `LayoutMap` cujas regiões declaram outros `marker_ids`
- **THEN** a leitura é recusada e a mensagem identifica os identificadores esperados e os encontrados

#### Scenario: Marcador faltando

- **WHEN** uma captura apresenta, para toda região do mapa, menos marcadores do que a região declara
- **THEN** a leitura é recusada por geometria insuficiente, e nenhuma medição parcial é entregue

#### Scenario: Marcadores de outra região na mesma página

- **WHEN** uma captura da página que traz o gabarito e uma região discursiva é lida
- **THEN** o gabarito é lido com os quatro marcadores dele, e os marcadores da região discursiva no mesmo quadro não causam recusa

## ADDED Requirements

### Requirement: A captura identifica as regiões presentes pelos marcadores encontrados

A captura SHALL detectar os marcadores do quadro uma vez e SHALL identificar, a partir deles, **quais regiões do `LayoutMap` estão presentes**: uma região está presente quando **todos** os `marker_ids` que ela declara foram encontrados — quatro para o gabarito, dois para uma região discursiva. Cada região presente SHALL ser lida por conta própria, e um quadro pode conter mais de uma. Região com parte dos seus marcadores no quadro SHALL NOT ser lida, e SHALL NOT, sozinha, tornar o quadro recusado.

A região a ler SHALL sair dos marcadores encontrados, e SHALL NOT ser escolhida de antemão por quem abre a sessão.

#### Scenario: Duas regiões no mesmo quadro

- **WHEN** um quadro traz os quatro marcadores do gabarito e os dois de uma região discursiva
- **THEN** as duas regiões são identificadas e lidas, cada uma com os seus marcadores

#### Scenario: Só a região discursiva no quadro

- **WHEN** um quadro traz apenas os dois marcadores de uma região discursiva
- **THEN** a região identificada é essa, e o gabarito não é procurado nem exigido naquele quadro

#### Scenario: Região pela metade

- **WHEN** um quadro traz os quatro marcadores do gabarito e só um dos dois marcadores de uma região discursiva
- **THEN** o gabarito é lido, a região discursiva não é lida, e o quadro não é recusado por causa dela

### Requirement: A região discursiva é reconhecida, e não medida

Uma região discursiva presente no quadro SHALL ser retificada e ter o seu QR lido e conferido contra os marcadores encontrados, pelas mesmas regras de qualquer região. A leitura SHALL entregar de qual prova, de qual aluno e de qual região ela é, e a qual questão a região pertence.

A região discursiva SHALL NOT passar por medição de bolha nem por interpretação de resposta: ela não declara bolha.

#### Scenario: Região discursiva reconhecida

- **WHEN** a região discursiva de uma questão aparece inteira no quadro, com QR legível
- **THEN** a leitura a reconhece, com a prova, o aluno e a região do QR, e com a questão que o mapa declara para ela

#### Scenario: QR de uma região dentro dos marcadores de outra

- **WHEN** o QR decodificado numa região discursiva traz um `region_idx` diferente do que os marcadores encontrados indicam
- **THEN** a região é recusada, e a mensagem identifica a divergência
