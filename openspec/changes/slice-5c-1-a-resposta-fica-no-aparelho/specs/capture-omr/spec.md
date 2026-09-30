## ADDED Requirements

### Requirement: A captura pede o recorte das regiões discursivas reconhecidas que ainda não têm resposta guardada

Quando um quadro reconhece uma região discursiva e a resposta dessa região **ainda não está guardada para o aluno que o QR dela identifica**, a captura SHALL pedir o recorte da área de resposta (requisito "O recorte da área de resposta é o que o mapa declara, retificado pelo segundo ajuste") **sobre o mesmo quadro**, antes de liberá-lo, e SHALL entregar junto do resultado da análise o que saiu do pedido, por região: a resposta guardada, com o sinal de desvio, ou a recusa, com o motivo. O quadro não sobrevive à análise, e por isso o pedido não pode ser adiado para depois dela.

A captura SHALL NOT pedir o recorte de região que não foi reconhecida no quadro, de região cuja resposta já está guardada para o aluno do quadro, nem a cada quadro analisado. A folha de **outro** aluno SHALL ser tratada como sem resposta guardada, ainda que a mesma região tenha resposta guardada do aluno anterior.

A análise do quadro SHALL continuar sem efeito e sem imagem (requisito "A região discursiva é reconhecida, e não medida"): o pedido e a gravação são da captura, **depois** da análise. O resultado da análise do gabarito SHALL NOT mudar por causa do recorte, e a recusa do recorte SHALL NOT recusar a região: ela continua reconhecida, e o que muda é que não tem resposta.

A gravação SHALL ser completa ou inexistente: um arquivo de resposta SHALL NOT ser entregue como guardado enquanto não estiver inteiro no armazenamento. Quando a imagem não puder ser gravada, a captura SHALL entregar uma recusa com o motivo, e SHALL NOT terminar com erro nem entregar a região como guardada.

#### Scenario: Região reconhecida sem resposta guardada

- **WHEN** um quadro traz a região discursiva reconhecida de um aluno cuja resposta nessa região não está guardada
- **THEN** o recorte é pedido sobre esse quadro, e o resultado traz a resposta guardada dessa região, com o sinal de desvio

#### Scenario: Resposta já guardada não é pedida de novo

- **WHEN** um quadro seguinte reconhece a mesma região do mesmo aluno, cuja resposta já está guardada
- **THEN** nenhum recorte é pedido, e nenhum arquivo novo é gravado

#### Scenario: Folha de outro aluno pede de novo

- **WHEN** a resposta da região 1 está guardada para o aluno A, e um quadro reconhece a região 1 da folha do aluno B
- **THEN** o recorte é pedido, e a resposta guardada é a do aluno B

#### Scenario: Região não reconhecida não é recortada

- **WHEN** a região discursiva está no quadro e não foi lida (marcador, geometria ou QR)
- **THEN** nenhum recorte é pedido para ela, e o resultado continua dizendo o motivo de ela não ter sido lida

#### Scenario: Recorte recusado chega com o motivo

- **WHEN** o recorte é pedido e o segundo ajuste o recusa pelo resíduo, ou a captura está escura demais
- **THEN** o resultado traz, para essa região, a recusa com o motivo que o recorte deu, e nenhum arquivo é gravado

#### Scenario: A análise do gabarito não muda

- **WHEN** o mesmo quadro é analisado com e sem pedido de recorte
- **THEN** a leitura do gabarito, a identificação do aluno e as regiões reconhecidas são as mesmas nos dois casos

#### Scenario: Imagem que não pôde ser gravada

- **WHEN** o recorte é feito e o armazenamento recusa a gravação
- **THEN** o resultado traz a recusa com o motivo, nenhuma resposta é entregue como guardada, e nenhum arquivo parcial permanece
