# Spec Delta

## ADDED Requirements

### Requirement: O servidor grava a nota do professor como revisão nova da mesma folha

O servidor SHALL aceitar o **resultado completo de uma prova com discursiva** (`scoring`: "A nota do professor completa a parcial sem recalcular a parte objetiva") e gravá-lo como **revisão nova** do mesmo par prova e token da parcial, com **origem `teacher`** e **caminho `image`** (o professor viu a imagem da resposta). A parcial anterior SHALL permanecer legível, e a revisão nova SHALL ser a corrente, como em "A gravação no servidor é append-only e idempotente por revisão".

O resultado do professor SHALL ser **autocontido**: leva a parte objetiva (a pontuação, a evidência e as pendências das objetivas) e a pontuação de cada discursiva da variante, de modo que o servidor o aceita **sem ter recebido antes** a parcial da captura. Ele SHALL identificar a **captura da parcial que completa**, além de ter a sua própria chave de idempotência.

O servidor SHALL conferir o resultado com as mesmas regras de coerência do aparelho (`scoring`), e SHALL conferir contra o **pacote publicado da própria prova** — o único oráculo — a proveniência (pacote e variante, como em "O resultado durável diz de qual folha, de qual pacote e de qual aluno ele é") **e** as discursivas: o conjunto de questões discursivas pontuadas SHALL ser exatamente o das discursivas da variante, e a pontuação de cada uma SHALL estar em 0..valor que o pacote declara, com a regra decimal de `scoring` (até 2 casas, exata): a pontuação pode ser fracionária, como 1.5 ou 1.75. A conferência SHALL NOT recalcular a parte objetiva a partir do gabarito.

O servidor SHALL recusar, como **decisão sobre o pedido** (a classe que o aparelho lê como definitiva) e dizendo **o que não fecha**, o resultado do professor cujo pacote ou variante não confere, cuja discursiva falta, sobra, está fora da faixa, tem mais de 2 casas decimais ou não é numérica, cuja pontuação não soma o total, ou cuja origem ou caminho não sejam os aceitos. **Os únicos aceitos neste requisito são `teacher` e `image`**: origem `ai` e caminho `text` pertencem a fatia posterior e SHALL ser recusados. Em qualquer recusa o servidor SHALL NOT gravar nada, nem o resultado nem a evidência por questão.

O resultado gravado SHALL preservar **exatamente** cada pontuação e o total, sem arredondar nem perder precisão entre o que o aparelho enviou, o que o servidor recebeu e o que o banco guarda; e a revisão corrente SHALL devolver o mesmo valor que foi enviado. O resultado objetivo e a parcial SHALL continuar aceitos e gravados exatamente como antes, com pontos inteiros. O resultado gravado SHALL registrar a origem e o caminho, e a evidência de cada discursiva SHALL ser distinguível da de uma objetiva. A nota SHALL ser declarada fechada somente se não restar pendência objetiva. O envio continua autenticado e escopado pela organização, e a ausência continua sendo ausência, nos mesmos termos de "O envio é autenticado e escopado pela organização".

#### Scenario: A nota do professor é uma revisão nova

- **WHEN** o servidor tem a parcial de uma folha como revisão corrente e recebe o resultado do professor para a mesma prova e o mesmo token
- **THEN** ele grava uma revisão nova com origem `teacher` e caminho `image`, ela passa a ser a corrente, e a parcial continua legível e inalterada

#### Scenario: A nota do professor chega sem a parcial

- **WHEN** o servidor recebe o resultado do professor de uma folha cuja parcial nunca recebeu
- **THEN** ele o grava, porque o resultado é autocontido

#### Scenario: Reenvio do mesmo resultado do professor

- **WHEN** o mesmo resultado do professor é enviado duas vezes
- **THEN** o servidor não cria registro novo e responde com sucesso nas duas, com a mesma revisão

#### Scenario: Nova nota do professor para a mesma folha

- **WHEN** o professor muda a pontuação e o novo resultado, com chave própria, chega para a mesma folha
- **THEN** o servidor grava revisão nova, ela é a corrente, e a anterior continua legível

#### Scenario: Discursiva que o pacote não declara

- **WHEN** o resultado do professor pontua um item que o pacote publicado não declara como discursiva da variante
- **THEN** o servidor recusa por decisão sobre o pedido, nomeia o item, e nada é gravado

#### Scenario: Discursiva faltando

- **WHEN** o resultado do professor não traz pontuação para uma discursiva da variante
- **THEN** o servidor recusa por decisão sobre o pedido, nomeia a discursiva que falta, e nada é gravado

#### Scenario: Nota fracionária é gravada e lida sem perda

- **WHEN** o servidor recebe um resultado do professor com pontuações 1.5 e 1.75 e total correspondente
- **THEN** ele o grava, e a revisão corrente devolve exatamente 1.5, 1.75 e o total, sem arredondar

#### Scenario: Mais de 2 casas decimais

- **WHEN** o resultado do professor traz uma pontuação com 3 casas decimais
- **THEN** o servidor recusa por decisão sobre o pedido, nomeia a questão e o limite de 2 casas, e nada é gravado

#### Scenario: Pontuação não numérica

- **WHEN** o resultado do professor traz uma pontuação que não é número
- **THEN** o servidor recusa por decisão sobre o pedido, nomeia a questão, e nada é gravado

#### Scenario: Resultado objetivo e parcial não mudam

- **WHEN** o servidor recebe um resultado objetivo ou uma parcial, com pontos inteiros
- **THEN** ele os aceita e grava exatamente como antes desta mudança

#### Scenario: Pontuação acima do valor do pacote

- **WHEN** o resultado do professor traz, para uma discursiva, pontuação maior que a do pacote publicado
- **THEN** o servidor recusa por decisão sobre o pedido, nomeia a questão e a faixa, e nada é gravado

#### Scenario: Pacote que não é o da prova

- **WHEN** o resultado do professor declara um pacote bem formado que não é o publicado daquela prova
- **THEN** o servidor recusa por decisão sobre o pedido, nomeia o pacote como o que não fecha, e nem o resultado nem a evidência são gravados

#### Scenario: Origem ou caminho de fatia posterior

- **WHEN** um resultado é enviado com origem `ai` ou caminho `text`
- **THEN** o servidor recusa por decisão sobre o pedido e nada é gravado

#### Scenario: A nota fecha só sem pendência objetiva

- **WHEN** o resultado do professor traz uma objetiva pendente, ou nenhuma
- **THEN** ele é gravado não fechado no primeiro caso e fechado no segundo

#### Scenario: Prova de outra organização

- **WHEN** o resultado do professor é de prova de organização de que o usuário autenticado não é membro
- **THEN** o servidor responde como ausência e nada é gravado

### Requirement: A revisão humana prevalece sobre a automática da mesma captura, em qualquer ordem de chegada

Para uma mesma captura da folha, a revisão de origem `teacher` SHALL prevalecer sobre a revisão automática (`omr`) dessa captura, **independentemente da ordem em que o servidor as recebe**: a invariante é que revisão humana vence qualquer resultado automático, e o modelo offline reordena e repete envios.

Quando a parcial de uma captura chega **depois** do resultado do professor que a completa — por falha transitória que a reenviou, ou por reordenação —, o servidor SHALL gravá-la como revisão **anterior legível**, SHALL responder com sucesso (o pendente do aparelho sai da fila), e SHALL NOT torná-la a corrente. Nada é sobrescrito, apagado nem recusado: o fato é append-only, e só a escolha de qual revisão é a corrente se submete a esta regra.

Uma **captura nova** da folha — nova leitura, que o resultado do professor não identifica como a que completou — SHALL seguir a regra de "A gravação no servidor é append-only e idempotente por revisão": é revisão nova e a mais recente é a corrente, e o resultado do professor da captura anterior SHALL permanecer legível. A nova captura traz outras imagens de resposta, e a nota dada sobre as anteriores não é a nota delas.

#### Scenario: A parcial chega depois da nota do professor

- **WHEN** o servidor recebe o resultado do professor de uma captura e, depois, a parcial dessa mesma captura
- **THEN** a parcial é gravada como revisão legível, o servidor responde com sucesso, e a corrente continua sendo a nota do professor

#### Scenario: A parcial chega antes, como de costume

- **WHEN** o servidor recebe a parcial de uma captura e, depois, o resultado do professor que a completa
- **THEN** a corrente é a nota do professor, e a parcial continua legível

#### Scenario: Reenvio da parcial depois da nota do professor

- **WHEN** a confirmação da parcial se perdeu, a nota do professor foi gravada, e a parcial é reenviada
- **THEN** o servidor reconhece o reenvio sem criar registro novo, e a corrente continua sendo a nota do professor

#### Scenario: Captura nova depois da nota do professor

- **WHEN** a mesma folha é lida de novo, e a parcial da nova captura chega depois da nota do professor da captura anterior
- **THEN** a parcial da nova captura é a corrente, e a nota do professor da captura anterior continua legível

### Requirement: O corpo da nota do professor não leva imagem, referência a arquivo nem dado pessoal

O corpo do resultado do professor SHALL NOT conter a imagem da resposta, o nome ou o caminho do arquivo que a guarda no aparelho, nem nome, turma ou matrícula do aluno. O aluno SHALL continuar identificado só pelo token que o QR da folha carrega. O servidor SHALL NOT gravar nenhum desses dados, e o resultado gravado SHALL NOT permitir recuperá-los.

A imagem da resposta continua no aparelho, sob o prazo de `scan-session` ("A resposta guardada tem prazo no aparelho"): gravar a nota do professor no servidor SHALL NOT eliminá-la nem exigir que ela suba.

#### Scenario: O corpo não leva imagem

- **WHEN** o servidor recebe um corpo de resultado do professor que traz, além do contrato, uma imagem ou o nome de um arquivo de resposta
- **THEN** nada disso é gravado, e o resultado gravado não permite recuperá-lo

#### Scenario: Nenhum dado pessoal direto

- **WHEN** o resultado do professor é enviado e gravado
- **THEN** ele não contém nome, turma nem matrícula do aluno, e identifica o aluno só pelo token
