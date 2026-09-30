## MODIFIED Requirements

### Requirement: A região discursiva é reconhecida, e não medida

Uma região discursiva presente no quadro SHALL ser retificada e ter o seu QR lido e conferido contra os marcadores encontrados, pelas mesmas regras de qualquer região. A leitura SHALL entregar de qual prova, de qual aluno e de qual região ela é, e a qual questão a região pertence.

A região discursiva SHALL NOT passar por medição de bolha nem por interpretação de resposta: ela não declara bolha.

Reconhecer a região SHALL NOT produzir o recorte da resposta: o recorte é uma leitura à parte, pedida sobre uma região já reconhecida (requisito "O recorte da área de resposta é o que o mapa declara, retificado pelo segundo ajuste"), e SHALL NOT ser calculado a cada quadro analisado.

#### Scenario: Região discursiva reconhecida

- **WHEN** a região discursiva de uma questão aparece inteira no quadro, com QR legível
- **THEN** a leitura a reconhece, com a prova, o aluno e a região do QR, e com a questão que o mapa declara para ela

#### Scenario: QR de uma região dentro dos marcadores de outra

- **WHEN** o QR decodificado numa região discursiva traz um `region_idx` diferente do que os marcadores encontrados indicam
- **THEN** a região é recusada, e a mensagem identifica a divergência

#### Scenario: Reconhecer não recorta

- **WHEN** um quadro com uma região discursiva legível é analisado
- **THEN** o resultado da análise identifica a região e não traz imagem de resposta

## ADDED Requirements

### Requirement: A geometria da região discursiva é conferida pelo resíduo do segundo ajuste

A região discursiva SHALL ser retificada em dois passos, na ordem de §8: a primeira retificação sai dos cantos dos dois marcadores, o QR é lido na região já retificada, e **só então** os três cantos de ancoragem do QR entram, junto com os cantos dos marcadores, num segundo ajuste sobredeterminado. O QR SHALL NOT ser procurado na imagem em perspectiva.

A geometria SHALL ser conferida pelo **resíduo** desse segundo ajuste: a **maior** distância, medida em milímetros da região, entre onde cada ponto observado cai e onde o mapa o declara. O máximo, e não a média, porque a dobra num único canto de ancoragem é o defeito a pegar, e uma média sobre onze pontos a dilui. O teto do resíduo é de **1,0 mm**, fixado antes da primeira execução (ADR-0007). Resíduo acima do teto, ou que não seja um número finito, SHALL recusar a região, e a recusa SHALL dizer o resíduo medido e o teto.

O quarto canto da região, o inferior esquerdo, não tem âncora: SHALL ser extrapolado, e a conferência SHALL NOT afirmar que ele foi medido.

O gabarito SHALL continuar com quatro marcadores, a homografia pelos centros e o teto de erro de reprojeção que já tem: este requisito não o altera.

#### Scenario: Folha fotografada em ângulo

- **WHEN** a região discursiva de uma folha fotografada em ângulo é retificada pelos dois passos
- **THEN** o resíduo fica abaixo do teto, e a moldura impressa cai, na região retificada, a menos de 0,5 mm da posição que o mapa declara

#### Scenario: Ponto de ancoragem deslocado

- **WHEN** um dos cantos de ancoragem do QR chega ao segundo ajuste deslocado o bastante para o maior resíduo passar do teto
- **THEN** a região é recusada por resíduo acima do teto, e a mensagem traz o resíduo e o teto de 1,0 mm

#### Scenario: Resíduo não finito

- **WHEN** o segundo ajuste produz resíduo não finito
- **THEN** a região é recusada, e a mensagem diz que o resíduo não é finito

#### Scenario: O segundo ajuste só acontece depois do QR

- **WHEN** o QR da região não é legível
- **THEN** a região não é retificada pelo segundo ajuste, e a recusa é a do QR

#### Scenario: O gabarito não muda

- **WHEN** o gabarito é lido depois desta mudança
- **THEN** as coberturas medidas são idênticas às de antes, byte a byte

### Requirement: O recorte da área de resposta é o que o mapa declara, retificado pelo segundo ajuste

Para uma região discursiva reconhecida, a leitura SHALL poder entregar o **recorte da área de resposta**: a `answer_area` que o `LayoutMap` declara para a região, retificada pelo segundo ajuste, em tons de cinza, a 10 pixels por milímetro. As dimensões do recorte SHALL ser as da área declarada, em milímetros por 10, com tolerância de um pixel.

O recorte SHALL conter **somente** a área declarada. Tinta de fora dela SHALL NOT entrar no recorte, mesmo quando existir na captura: o recorte limpo é o que impede a correção de avaliar o que não é resposta (§8).

Pedir o recorte de região que não é discursiva, ou que não declara área de resposta, SHALL ser recusado com o motivo.

O recorte SHALL ser local, determinístico e sem efeito: a mesma captura e o mesmo mapa SHALL produzir o mesmo recorte, byte a byte, e a operação SHALL NOT tocar rede, disco nem estado. O recorte existe na memória de quem o pediu; guardá-lo, e por quanto tempo, não é deste requisito.

#### Scenario: Recorte da área declarada

- **WHEN** o recorte é pedido para a região discursiva reconhecida de uma folha
- **THEN** a imagem tem as dimensões da área de resposta do mapa a 10 pixels por milímetro, e a moldura impressa aparece nela na posição que o mapa declara

#### Scenario: Tinta escrita no canto sem âncora

- **WHEN** o aluno escreveu até o canto inferior esquerdo da moldura e a folha foi fotografada em ângulo
- **THEN** a tinta escrita nesse canto está inteira no recorte

#### Scenario: Tinta de fora da área não entra

- **WHEN** existe tinta do aluno fora da área de resposta declarada
- **THEN** essa tinta não aparece no recorte

#### Scenario: Região que não é discursiva

- **WHEN** o recorte é pedido para o gabarito
- **THEN** o pedido é recusado, e a mensagem diz que a região não declara área de resposta

#### Scenario: Mesma captura, mesmo recorte

- **WHEN** o recorte é pedido duas vezes para a mesma captura e o mesmo mapa
- **THEN** as duas imagens são idênticas, byte a byte

#### Scenario: Recorte sem cabeçalho

- **WHEN** o recorte é pedido para a região discursiva de uma folha cujo cabeçalho traz o nome do aluno impresso
- **THEN** toda a tinta do recorte é a que o mapa declara dentro da área (moldura e pauta), e nenhuma parte do cabeçalho aparece nele

### Requirement: O desvio da resposta é sinalizado, e não corrigido em silêncio

O recorte SHALL dizer se a resposta **extrapola a área**, medindo a tinta numa faixa de **3 mm fora da área de resposta**. A tinta impressa que o mapa declara nessa faixa (marcadores, QR e moldura) SHALL ser descontada; o que sobra é tinta do aluno. Tinta é o que passa do tom decorativo máximo que a região declara, contra o branco do papel medido na própria captura, e a pauta, por ser decoração, SHALL NOT contar.

A resposta SHALL ser sinalizada como desvio quando a tinta do aluno na faixa for **pelo menos 5%** da tinta do aluno na área somada à da faixa **e** somar pelo menos **4 mm²**. Os dois números são suposições fixadas antes da primeira execução, e SHALL NOT ser alterados depois de conhecido o resultado sem ADR (ADR-0007).

O desvio SHALL ser um sinal de conferência, e SHALL NOT alterar o recorte nem recusá-lo. Resposta em branco SHALL NOT ser desvio.

#### Scenario: Resposta dentro da área

- **WHEN** a tinta do aluno está toda dentro da área de resposta
- **THEN** a resposta não é sinalizada como desvio

#### Scenario: Resposta que extrapola a área

- **WHEN** parte relevante da tinta do aluno está na faixa fora da área de resposta
- **THEN** a resposta é sinalizada como desvio, com a proporção medida, e o recorte é entregue igual

#### Scenario: A tinta impressa não conta

- **WHEN** a região é lida de uma folha em branco
- **THEN** a resposta não é sinalizada como desvio, e a proporção medida é zero

#### Scenario: Mancha isolada abaixo do piso

- **WHEN** há uma mancha de tinta do aluno na faixa, menor que 4 mm²
- **THEN** a resposta não é sinalizada como desvio, ainda que a proporção passe de 5%

#### Scenario: Resposta em branco

- **WHEN** não há tinta do aluno na área nem na faixa
- **THEN** a resposta não é sinalizada como desvio

#### Scenario: A faixa não vaza para o recorte

- **WHEN** a resposta é sinalizada como desvio
- **THEN** o recorte entregue não contém pixel da faixa
