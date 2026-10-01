## ADDED Requirements

### Requirement: A nota do professor vira resultado durável pendente e é enviada pela rota própria

Confirmada a nota do professor sobre um caderno completo (`scan-session`: "O professor dá a nota das discursivas sobre a imagem, na hora"), o aparelho SHALL gravar o **resultado do professor** em armazenamento durável **antes** de tentar qualquer envio, sem rede, e SHALL fazê-lo nascer **pendente** na mesma fila dos demais resultados. O resultado SHALL sobreviver ao fim do processo e ao desligamento, como em "A nota apurada vira resultado durável antes de qualquer rede".

O resultado do professor SHALL ser **autocontido**, como a `5c-2` exige (`result-sync`: "O servidor grava a nota do professor como revisão nova da mesma folha"): leva a parte objetiva da parcial, a pontuação **exata** de cada discursiva e o total, a origem `teacher`, o caminho `image`, uma **chave de idempotência própria** e a **captura da parcial que completa**. Ele SHALL ser gravado e enviado pelo mesmo contrato e pela mesma rota que o servidor aceita, SHALL NOT recalcular a parte objetiva, e SHALL NOT depender de a parcial já ter sido confirmada: a nota pode ser enviada antes, depois ou sem a parcial. Reenvio SHALL repetir a mesma chave, e uma nova nota para a mesma folha SHALL ter chave nova.

O envio SHALL seguir as regras de "O resultado nasce pendente e espera a rede numa fila local" e "O pendente local só sai depois da confirmação, e sair da sessão não o apaga": ausência de rede, falha do servidor e recusa definitiva SHALL NOT apagar, alterar nem marcar como enviado o pendente, e a recusa definitiva SHALL NOT virar laço. A nota pendente SHALL ser escopada pela organização, sobreviver a sair da sessão e à revogação do vínculo, e SHALL ser enviada na sessão seguinte de qualquer membro. O aparelho SHALL contar a nota pendente entre os resultados ainda não enviados que informa ao sair.

O corpo SHALL NOT conter imagem, nome ou caminho de arquivo de resposta, nem nome, turma ou matrícula (`result-sync`: "O corpo da nota do professor não leva imagem, referência a arquivo nem dado pessoal"). A confirmação do servidor SHALL ser o evento que autoriza eliminar as respostas do caderno corrigido no aparelho (`scan-session`: "A resposta guardada tem prazo no aparelho"), e nenhum outro evento de envio o faz.

#### Scenario: A nota sobrevive ao fechamento do aplicativo

- **WHEN** o professor confirma a nota e o aplicativo é encerrado antes de qualquer envio
- **THEN** a nota continua gravada como pendente na abertura seguinte, e as respostas continuam no aparelho

#### Scenario: Sem rede, a nota espera

- **WHEN** a nota é confirmada com o aparelho sem rede
- **THEN** ela é gravada e fica pendente na fila, nada é enviado nem marcado como enviado

#### Scenario: A nota é enviada com o contrato da 5c-2

- **WHEN** há rede e a nota pendente é enviada
- **THEN** o corpo leva a parte objetiva, a pontuação exata de cada discursiva, o total, origem `teacher`, caminho `image`, a chave própria e a captura da parcial que completa

#### Scenario: A nota é fracionária e chega exata

- **WHEN** a nota com 1.5 e 1.75 é enviada e gravada
- **THEN** o servidor devolve exatamente 1.5 e 1.75 na revisão corrente, sem arredondamento

#### Scenario: A nota vai antes da parcial

- **WHEN** a parcial do caderno ainda está pendente e a nota é enviada primeiro
- **THEN** o servidor aceita a nota, e a parcial enviada depois não vira a corrente

#### Scenario: Reenvio da mesma nota

- **WHEN** a confirmação da nota se perde e ela é reenviada
- **THEN** o reenvio leva a mesma chave, o servidor o reconhece sem registro novo, e o pendente sai da fila

#### Scenario: Falha do servidor é tentada de novo

- **WHEN** o envio da nota é recusado por falha do servidor
- **THEN** a nota continua pendente, as respostas continuam no aparelho, e o aparelho agenda nova tentativa por conta própria

#### Scenario: Recusa definitiva não apaga nada

- **WHEN** o servidor recusa a nota por decisão sobre o pedido
- **THEN** a nota continua pendente, as respostas continuam no aparelho, e não há nova tentativa imediata

#### Scenario: Sair avisa da nota não enviada

- **WHEN** o usuário pede para sair com uma nota pendente
- **THEN** o aparelho a conta entre os resultados não enviados, e a nota e as respostas continuam no aparelho

#### Scenario: Confirmação elimina as respostas e só ela

- **WHEN** o servidor confirma a nota
- **THEN** o pendente sai da fila e as respostas do caderno corrigido são eliminadas; antes dessa confirmação nenhuma delas foi eliminada por conta do envio

## MODIFIED Requirements

### Requirement: O corpo da nota do professor não leva imagem, referência a arquivo nem dado pessoal

O corpo do resultado do professor SHALL NOT conter a imagem da resposta, o nome ou o caminho do arquivo que a guarda no aparelho, nem nome, turma ou matrícula do aluno. O aluno SHALL continuar identificado só pelo token que o QR da folha carrega. O servidor SHALL NOT gravar nenhum desses dados, e o resultado gravado SHALL NOT permitir recuperá-los.

A imagem da resposta fica no aparelho, sob o prazo de `scan-session` ("A resposta guardada tem prazo no aparelho"): gravar a nota do professor no servidor SHALL NOT exigir que a imagem suba, e **o servidor não elimina nem pede que se elimine** nada: quem elimina a imagem é o aparelho, **depois de receber a confirmação da nota**, e não antes.

#### Scenario: O corpo não leva imagem

- **WHEN** o servidor recebe um corpo de resultado do professor que traz, além do contrato, uma imagem ou o nome de um arquivo de resposta
- **THEN** nada disso é gravado, e o resultado gravado não permite recuperá-lo

#### Scenario: Nenhum dado pessoal direto

- **WHEN** o resultado do professor é enviado e gravado
- **THEN** ele não contém nome, turma nem matrícula do aluno, e identifica o aluno só pelo token
