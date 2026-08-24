## Purpose

Ler uma folha capturada contra o `LayoutMap` que a gerou: identificar a região pelos marcadores, retificá-la, confirmar pelo QR de qual folha e de qual região ela é, e medir quanta tinta há dentro de cada bolha declarada. A capacidade produz medição; quem a interpreta é o scoring.

## Requirements

### Requirement: A geometria da leitura vem do `LayoutMap`, nunca da imagem

A leitura SHALL derivar a posição de cada bolha das coordenadas normalizadas que o `LayoutMap` declara para a região, projetadas pelos quatro marcadores encontrados na captura. A leitura SHALL NOT inferir posição de bolha por detecção de círculo, por espaçamento regular presumido, nem por qualquer propriedade medida na própria imagem.

Uma captura cujos marcadores detectados não correspondam aos `marker_ids` declarados pela região SHALL ser recusada, identificando os IDs esperados e os encontrados.

#### Scenario: Bolhas vêm do mapa

- **WHEN** uma captura de uma folha é lida contra o `LayoutMap` que a gerou
- **THEN** cada bolha medida corresponde a uma bolha declarada na região, e o conjunto medido é exatamente o conjunto declarado

#### Scenario: Folha de outra prova sob estes marcadores

- **WHEN** uma captura é lida contra um `LayoutMap` cuja região declara outros `marker_ids`
- **THEN** a leitura é recusada e a mensagem identifica os identificadores esperados e os encontrados

#### Scenario: Marcador faltando

- **WHEN** uma captura apresenta menos de quatro marcadores da região
- **THEN** a leitura é recusada por geometria insuficiente, e nenhuma medição parcial é entregue

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

### Requirement: A grandeza medida é a cobertura, e a leitura não a interpreta

A leitura SHALL produzir, para cada bolha declarada, a cobertura de tinta definida em ADR-0010: a média de escuridão dentro do disco da bolha, normalizada contra o branco do papel na vizinhança, de 0 a 1. O disco medido SHALL ser o círculo desenhado descontado o traço, para que o contorno da bolha não entre na conta.

A leitura SHALL NOT classificar bolha como marcada, vazia ou ambígua, e SHALL NOT aplicar limiar — o limiar depende de corpus que ainda não existe, e ADR-0007 exige que seu critério seja registrado antes da medição.

Cobertura não finita, janela de medição vazia ou janela fora da imagem SHALL ser falha explícita identificando a bolha, e nunca um valor ausente que siga adiante.

#### Scenario: Medição de todas as bolhas

- **WHEN** uma captura válida é lida
- **THEN** cada bolha declarada recebe uma cobertura entre 0 e 1, e a saída não contém nenhum veredito sobre estar marcada

#### Scenario: Cobertura conferida contra valor conhecido

- **WHEN** uma captura sintética com bolha preenchida a uma fração conhecida por construção é lida
- **THEN** a cobertura medida é essa fração, dentro da tolerância declarada

#### Scenario: Janela de medição inválida

- **WHEN** o disco de uma bolha cai fora da imagem, ou a medição não é finita
- **THEN** a leitura falha identificando a bolha, e não devolve medição parcial silenciosa

#### Scenario: Papel escurecido não vira tinta

- **WHEN** uma captura tem iluminação irregular, com o papel visivelmente mais escuro numa parte da região
- **THEN** a cobertura das bolhas vazias nessa parte permanece comparável à das demais, porque a normalização é contra o branco local

### Requirement: A leitura é local, determinística e sem efeito

A leitura SHALL funcionar sem rede, contra o `LayoutMap` do pacote em cache.

A mesma captura lida duas vezes contra o mesmo `LayoutMap` SHALL produzir exatamente a mesma medição. A leitura SHALL NOT alterar a captura, o `LayoutMap` nem qualquer estado persistido.

#### Scenario: Leitura repetida

- **WHEN** a mesma captura é lida duas vezes contra o mesmo `LayoutMap`
- **THEN** as duas medições são idênticas

#### Scenario: Sem rede

- **WHEN** a leitura ocorre com o aparelho offline
- **THEN** ela conclui normalmente, porque nada nela depende de rede
