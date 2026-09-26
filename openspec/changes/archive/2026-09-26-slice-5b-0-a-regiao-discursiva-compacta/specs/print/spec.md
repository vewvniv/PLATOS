## MODIFIED Requirements

### Requirement: Tolerância a reescala da impressão

Impressoras reescalam o documento em ambas as direções, e o sistema SHALL NOT depender de medida absoluta na folha impressa. Ver ADR-0001.

Sob reescala de até ±5% em qualquer eixo, a folha impressa SHALL preservar as propriedades que a captura consome:
- **lado do marcador ArUco no mínimo exigido pelo tipo da região dele:** 12 mm na região de gabarito e 10 mm na região discursiva;
- zona de silêncio livre de qualquer outro traço;
- código QR decodificável.

Toda geometria de captura SHALL ser dimensionada de modo que o mínimo exigido continue satisfeito no pior caso dessa faixa, e não apenas no valor nominal.

#### Scenario: Folha reescalada pela impressora

- **WHEN** a folha é impressa por uma impressora que reescala o documento em até 5%
- **THEN** os marcadores do gabarito continuam com lado de no mínimo 12 mm, os da região discursiva com no mínimo 10 mm, a zona de silêncio segue livre e o QR continua sendo decodificado

#### Scenario: Marcadores legíveis após impressão

- **WHEN** a folha impressa é inspecionada
- **THEN** cada marcador ArUco está completo, com a borda fechada, e sua zona de silêncio está livre de qualquer outro traço

#### Scenario: Geometria dimensionada com folga

- **WHEN** uma dimensão de captura com mínimo exigido é definida
- **THEN** o valor nominal escolhido mantém o mínimo satisfeito mesmo reduzido em 5%

## ADDED Requirements

### Requirement: Linha desenhada com o traço e o tom declarados

Um renderizador SHALL desenhar a primitiva de linha do `LayoutMap`:
- entre os dois pontos declarados, sem arremate além das extremidades;
- com a espessura declarada;
- com o tom declarado. Linha sem tom é preta.

A verificação automática de equivalência entre renderizadores SHALL cobrir toda linha.
- **Presença:** em cada documento, a tinta medida na faixa da linha SHALL ficar entre metade e uma vez e meia da **tinta esperada**, que é a área que a linha declara multiplicada pelo tom declarado.
- **Concordância:** os dois documentos SHALL concordar entre si dentro da tolerância já declarada para traço.

Uma linha omitida, ou desenhada em preto quando o mapa a declara cinza, SHALL reprovar a verificação. Medir a linha só pela posição, ou só acima do piso de escuridão da paridade, não satisfaz este requisito: a pauta fica abaixo desse piso por decisão (ADR-0016), e seria invisível à medição.

Um renderizador de versão anterior à que desenha linha SHALL recusar o mapa que a contém, pela guarda de versão.

#### Scenario: Linha cinza igual nos dois renderizadores

- **WHEN** o mesmo `LayoutMap` com pauta cinza é desenhado nas duas plataformas e medido
- **THEN** a tinta de cada linha fica, em cada documento, entre metade e uma vez e meia da tinta esperada, e os dois documentos concordam dentro da tolerância de traço

#### Scenario: Linha omitida reprova

- **WHEN** um renderizador deixa de desenhar as linhas de um mapa que as declara
- **THEN** a verificação automática falha e aponta a linha e a tinta medida

#### Scenario: Linha em preto no lugar do cinza reprova

- **WHEN** um renderizador desenha em preto uma linha que o mapa declara cinza
- **THEN** a verificação automática falha e aponta a linha e a tinta medida

#### Scenario: Renderizador anterior recusa mapa com linha

- **WHEN** um mapa que contém linha é entregue a um renderizador de versão anterior à que desenha linha
- **THEN** nenhum documento é produzido e o motivo informado é a versão insuficiente
