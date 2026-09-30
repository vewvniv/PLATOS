## MODIFIED Requirements

### Requirement: A região discursiva declara a questão e a área de resposta

Toda região discursiva SHALL declarar:
- a questão a que pertence;
- os dois marcadores dela;
- a área de resposta, como retângulo em coordenadas normalizadas ao retângulo de referência da região;
- o retângulo do QR dela.

A área de resposta é o que a captura vai recortar (§8). Ela SHALL ocupar a largura inteira da região, e SHALL ir da base do QR até a zona de silêncio do marcador de baixo. Ela SHALL conter a moldura inteira, **com folga acima e abaixo dela**. A folga de baixo existe para a tinta que desce da última linha escrita.

A área de resposta SHALL NOT sobrepor o QR da região nem a zona de silêncio de nenhum marcador. Uma região discursiva SHALL NOT declarar bolhas.

**A área de resposta SHALL conter somente a moldura e a pauta.** Nenhuma primitiva de texto, imagem, QR, marcador, círculo ou retângulo preenchido SHALL ter parte dentro dela, porque o que está dentro da área é o que o recorte entrega, e cabeçalho, enunciado ou nome do aluno num recorte é dado que a correção não pode ver (§8). Encostar na borda da área não é estar dentro dela. Uma primitiva de texto está dentro da área quando começa dentro da largura da região e a linha dela cruza a altura da área.

A validação do `LayoutMap` sem renderizar SHALL recusar, apontando qual verificação falhou:
- região discursiva sem questão;
- área de resposta fora de `[0,1]` ou sobreposta ao QR;
- primitiva de texto, imagem, QR, marcador, círculo ou retângulo preenchido dentro da área de resposta, com a região e a primitiva identificadas;
- duas regiões apontando a mesma questão;
- região cujo QR declarado não existe entre as primitivas da página dela;
- região discursiva que não declara exatamente os marcadores `4k` e `4k+3`;
- região discursiva cujo marcador declarado não existe entre as primitivas da página dela.

Esta verificação SHALL NOT medir o comprimento do texto: um texto que começa fora da largura da região e a invade lendo para a direita não é visto por ela. Esse caso é conferido sobre o documento renderizado, pela captura (`capture-omr`, "Recorte sem cabeçalho").

#### Scenario: Região discursiva completa

- **WHEN** uma prova com uma questão discursiva é calculada
- **THEN** a região discursiva declara a questão, os marcadores `4` e `7`, uma área de resposta dentro de `[0,1]` e um QR que existe na página dela, e nenhuma bolha

#### Scenario: Área de resposta com folga fora da moldura

- **WHEN** a região discursiva de uma questão é emitida
- **THEN** a moldura está inteira dentro da área de resposta, e a área se estende abaixo da moldura antes de chegar à zona de silêncio do marcador de baixo

#### Scenario: Área de resposta sobre o QR

- **WHEN** um `LayoutMap` declara uma região discursiva cuja área de resposta sobrepõe o QR da região
- **THEN** a validação recusa o mapa e identifica a região

#### Scenario: Duas regiões para a mesma questão

- **WHEN** um `LayoutMap` declara duas regiões discursivas que apontam a mesma questão
- **THEN** a validação recusa o mapa e identifica a questão

#### Scenario: QR declarado que não existe

- **WHEN** uma região declara um QR que não está entre as primitivas da página dela
- **THEN** a validação recusa o mapa e identifica a região

#### Scenario: Região discursiva com os marcadores errados

- **WHEN** um `LayoutMap` declara uma região discursiva com os quatro marcadores `4k` a `4k+3`, ou com um par diferente de `4k` e `4k+3`
- **THEN** a validação recusa o mapa e identifica a região e os marcadores declarados

#### Scenario: Marcador declarado que não existe

- **WHEN** uma região discursiva declara um marcador que não está entre as primitivas da página dela
- **THEN** a validação recusa o mapa e identifica a região e o marcador

#### Scenario: Texto dentro da área de resposta

- **WHEN** um `LayoutMap` declara, na página de uma região discursiva, uma primitiva de texto que começa dentro da largura da região e cuja linha cruza a altura da área de resposta
- **THEN** a validação recusa o mapa e identifica a região e a primitiva

#### Scenario: Nome do aluno impresso sobre a área

- **WHEN** um `LayoutMap` declara uma primitiva de texto com o nome do aluno posicionada dentro da área de resposta de uma região discursiva
- **THEN** a validação recusa o mapa e identifica a região e a primitiva

#### Scenario: Marcador, QR ou imagem dentro da área

- **WHEN** um `LayoutMap` declara, dentro da área de resposta, um marcador, um QR ou uma imagem
- **THEN** a validação recusa o mapa e identifica a região e a primitiva

#### Scenario: Encostar na borda da área

- **WHEN** o QR da região termina exatamente onde a área de resposta começa
- **THEN** a validação aceita o mapa

#### Scenario: Moldura e pauta são admitidas

- **WHEN** um `LayoutMap` declara dentro da área de resposta a moldura e as linhas da pauta com tom
- **THEN** a validação aceita o mapa
