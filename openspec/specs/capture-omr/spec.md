## Purpose

Ler uma folha capturada contra o `LayoutMap` que a gerou: identificar a região pelos marcadores, retificá-la, confirmar pelo QR de qual folha e de qual região ela é, e medir quanta tinta há dentro de cada bolha declarada, e dizer o que cada bolha e cada questão respondem. A capacidade vai da imagem até a resposta; quem a transforma em nota é o `scoring`.

## Requirements

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

### Requirement: A captura é retificada antes de ser medida

A leitura SHALL construir a transformação projetiva a partir dos quatro marcadores e SHALL medir a tinta sobre a região já retificada, e não sobre a imagem em perspectiva.

A leitura SHALL recusar uma captura cujo erro de reprojeção dos quatro marcadores exceda a tolerância declarada, porque geometria que não fecha produz medição que não significa nada.

#### Scenario: Captura em perspectiva

- **WHEN** uma folha é capturada em ângulo e lida
- **THEN** a cobertura medida em cada bolha é equivalente à medida sobre a mesma folha capturada de frente, dentro da tolerância declarada

#### Scenario: Geometria que não fecha

- **WHEN** os quatro marcadores detectados produzem erro de reprojeção acima da tolerância
- **THEN** a leitura é recusada e informa o erro medido

### Requirement: A captura se identifica pelo QR, e a identificação é redundante

O payload do QR SHALL ter codec único: o mesmo componente que o constrói para a folha SHALL lê-lo de volta da captura. A leitura SHALL recusar payload cujo CRC não confira.

A leitura SHALL conferir o `region_idx` do payload contra os identificadores dos marcadores encontrados, e SHALL recusar a captura quando divergirem — a redundância existe para que a atribuição não dependa de um único canal.

#### Scenario: Payload íntegro

- **WHEN** o QR de uma captura é decodificado
- **THEN** o payload é aceito, e dele saem a prova, o aluno, a variante e o índice da região

#### Scenario: CRC não confere

- **WHEN** o payload decodificado tem CRC divergente do conteúdo
- **THEN** a leitura é recusada e nenhuma medição é atribuída a essa folha

#### Scenario: QR de outra região

- **WHEN** o `region_idx` do payload não corresponde aos identificadores dos marcadores encontrados
- **THEN** a leitura é recusada e a mensagem identifica a divergência

#### Scenario: Ida e volta do payload

- **WHEN** um payload é construído para uma região e lido de volta
- **THEN** os campos recuperados são exatamente os declarados, e o CRC confere

### Requirement: A grandeza medida é a cobertura

A leitura SHALL produzir, para cada bolha declarada, a cobertura de tinta definida em ADR-0010: a média de escuridão dentro do disco da bolha, normalizada contra o branco do papel na vizinhança, de 0 a 1. O disco medido SHALL ser o círculo desenhado descontado o traço, para que o contorno da bolha não entre na conta.

A cobertura de cada bolha SHALL permanecer exposta na saída, ao lado do veredito, e não SHALL ser substituída por ele. Quem revisa uma folha precisa ver o número que produziu o veredito.

Cobertura não finita, janela de medição vazia ou janela fora da imagem SHALL ser falha explícita identificando a bolha, e nunca um valor ausente que siga adiante.

#### Scenario: Medição de todas as bolhas

- **WHEN** uma captura válida é lida
- **THEN** cada bolha declarada recebe uma cobertura entre 0 e 1, e o conjunto medido é exatamente o conjunto declarado

#### Scenario: Cobertura conferida contra valor conhecido

- **WHEN** uma captura sintética com bolha preenchida a uma fração conhecida por construção é lida
- **THEN** a cobertura medida é essa fração, dentro da tolerância declarada

#### Scenario: A cobertura sobrevive ao veredito

- **WHEN** uma captura válida é lida e cada bolha recebe seu veredito
- **THEN** a cobertura medida de cada bolha continua recuperável na saída, com o mesmo valor que a medição produziu

#### Scenario: Janela de medição inválida

- **WHEN** o disco de uma bolha cai fora da imagem, ou a medição não é finita
- **THEN** a leitura falha identificando a bolha, e não devolve medição parcial silenciosa

#### Scenario: Papel escurecido não vira tinta

- **WHEN** uma captura tem iluminação irregular, com o papel visivelmente mais escuro numa parte da região
- **THEN** a cobertura das bolhas vazias nessa parte permanece comparável à das demais, porque a normalização é contra o branco local

### Requirement: O veredito de bolha usa um limiar validado contra o corredor que a folha declara

A leitura SHALL classificar cada bolha em marcada, vazia ou indecisa, comparando a cobertura medida com um limiar único, expresso na mesma unidade em que o `ink_budget` da região declara o corredor, sem conversão entre fração, porcentagem e permilagem.

O limiar SHALL vir do aplicativo, e a leitura SHALL recusar a folha cujo `ink_budget` declare um corredor que não contenha esse limiar, informando o limiar e o corredor encontrado. Uma folha impressa a partir de um pacote que reservou outro corredor não é legível por este aplicativo, e ler assim mesmo produziria acerto ou erro que ninguém marcou.

A leitura SHALL declarar uma margem de indecisão em torno do limiar. Cobertura dentro dessa margem SHALL produzir veredito indeciso, e nunca marcada ou vazia por arredondamento.

A comparação com o limiar SHALL ser determinística nas bordas: cobertura igual ao limiar tem um único veredito possível, declarado, e igual em toda execução.

#### Scenario: Folha cujo corredor contém o limiar

- **WHEN** uma captura é lida contra um `LayoutMap` cujo `ink_budget` declara um corredor que contém o limiar do aplicativo
- **THEN** a leitura prossegue e cada bolha recebe veredito

#### Scenario: Folha cujo corredor exclui o limiar

- **WHEN** uma captura é lida contra um `LayoutMap` cujo `ink_budget` declara um corredor que não contém o limiar do aplicativo
- **THEN** a leitura é recusada, e a mensagem informa o limiar do aplicativo e o corredor declarado pela folha

#### Scenario: Cobertura exatamente no limiar

- **WHEN** a cobertura de uma bolha é exatamente igual ao limiar
- **THEN** o veredito é o declarado para esse caso, e é o mesmo em toda execução

#### Scenario: Cobertura dentro da margem de indecisão

- **WHEN** a cobertura de uma bolha cai dentro da margem declarada em torno do limiar
- **THEN** o veredito é indeciso, e a bolha não é contada como marcada nem como vazia

### Requirement: A resposta de uma questão é explícita, inclusive quando não há resposta

A leitura SHALL produzir, para cada questão declarada na região, exatamente um resultado entre: a alternativa marcada, em branco, múltipla marcação, ou indecisa. O conjunto de questões resultante SHALL ser exatamente o conjunto declarado.

Havendo mais de uma bolha marcada numa questão, a leitura SHALL relatar múltipla marcação. Ela SHALL NOT escolher a bolha de maior cobertura, nem qualquer outra regra de desempate — desempatar por cobertura transforma rasura em resposta.

Havendo qualquer bolha indecisa numa questão sem bolha marcada, a leitura SHALL relatar a questão como indecisa em vez de em branco.

Nenhum destes resultados SHALL ser produzido por omissão: questão ausente da saída é falha da leitura, não resposta em branco.

#### Scenario: Uma alternativa marcada

- **WHEN** exatamente uma bolha de uma questão é classificada como marcada
- **THEN** o resultado da questão é essa alternativa

#### Scenario: Nenhuma alternativa marcada

- **WHEN** nenhuma bolha de uma questão é classificada como marcada, e nenhuma é indecisa
- **THEN** o resultado da questão é em branco

#### Scenario: Duas alternativas marcadas

- **WHEN** duas bolhas de uma questão são classificadas como marcadas
- **THEN** o resultado da questão é múltipla marcação, e nomeia as alternativas envolvidas

#### Scenario: Questão com bolha indecisa e nenhuma marcada

- **WHEN** uma questão tem ao menos uma bolha indecisa e nenhuma marcada
- **THEN** o resultado da questão é indecisa, e não em branco

#### Scenario: Cobertura de todas as questões declaradas

- **WHEN** uma captura válida é lida
- **THEN** cada questão declarada na região aparece uma única vez no resultado

### Requirement: A leitura é local, determinística e sem efeito

A leitura SHALL funcionar sem rede, contra o `LayoutMap` do pacote em cache.

A mesma captura lida duas vezes contra o mesmo `LayoutMap` SHALL produzir exatamente a mesma medição. A leitura SHALL NOT alterar a captura, o `LayoutMap` nem qualquer estado persistido.

#### Scenario: Leitura repetida

- **WHEN** a mesma captura é lida duas vezes contra o mesmo `LayoutMap`
- **THEN** as duas medições são idênticas

#### Scenario: Sem rede

- **WHEN** a leitura ocorre com o aparelho offline
- **THEN** ela conclui normalmente, porque nada nela depende de rede

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
