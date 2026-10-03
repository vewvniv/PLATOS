# Spec Delta

## ADDED Requirements

### Requirement: Com a coleta ligada, a nota confirmada deixa a cópia das discursivas

Com a coleta do corpus **ligada** (`measurement-corpus`: "A coleta existe só no aplicativo de depuração e vem desligada"), ao confirmar a nota de um caderno (requisito "O professor dá a nota das discursivas sobre a imagem, na hora"), o aplicativo SHALL copiar **todas** as discursivas da folha como amostras do corpus, cada uma com a pontuação que o professor deu **a ela** e o máximo da questão. A cópia não é por resposta: o professor SHALL NOT escolher quais respostas entram.

A cópia SHALL ocorrer **antes** de a nota ser gravada como resultado durável pendente: a imagem da resposta é eliminada quando o servidor confirma a nota ("A resposta guardada tem prazo no aparelho"), e a cópia SHALL NOT perder essa corrida. Cada amostra SHALL ser completa ou inexistente.

**A falha da cópia SHALL NOT impedir nem desfazer a nota**: a nota é gravada como resultado durável pendente como sempre, e o aplicativo de depuração SHALL dizer qual amostra não foi guardada. **A nota que não foi gravada SHALL NOT deixar amostra**: se a gravação falhar depois da cópia, as amostras copiadas por ela SHALL ser eliminadas. Cancelar, sair da tela ou não confirmar a nota SHALL NOT copiar nada.

A cópia SHALL NOT alterar a imagem da resposta guardada nem a eliminação dela, e uma revisão posterior da nota SHALL NOT alterar a amostra já copiada. Com a coleta desligada, e no aplicativo de release, a tela de nota e a gravação da nota SHALL se comportar como sem a coleta.

#### Scenario: Coleta desligada não copia

- **WHEN** o professor confirma a nota de uma folha com duas discursivas e a coleta está desligada
- **THEN** a nota é gravada e nenhuma amostra é criada

#### Scenario: Coleta ligada copia todas as discursivas

- **WHEN** a coleta está ligada e o professor dá 1.5 e 1.75 às duas discursivas e confirma a nota
- **THEN** duas amostras são criadas, uma com a pontuação 1.5 e outra com 1.75, cada uma com o máximo da própria questão

#### Scenario: Nota cancelada não copia

- **WHEN** a coleta está ligada e o professor sai da tela de nota sem confirmar
- **THEN** nenhuma amostra é criada e a resposta guardada segue como estava

#### Scenario: A cópia vence a eliminação da imagem

- **WHEN** a coleta está ligada e o servidor confirma a nota imediatamente após ela ser gravada
- **THEN** as amostras já existem, copiadas antes da gravação, com a imagem inteira

#### Scenario: Falha na cópia não derruba a nota

- **WHEN** a coleta está ligada, o professor confirma a nota, e a cópia de uma imagem falha
- **THEN** a nota é gravada como resultado durável pendente, o aplicativo diz qual amostra não foi guardada, e nada fica pela metade

#### Scenario: Nota não gravada não deixa amostra

- **WHEN** a coleta está ligada, as amostras são copiadas e a gravação da nota falha
- **THEN** as amostras copiadas para essa nota são eliminadas

#### Scenario: A eliminação da resposta não muda

- **WHEN** o servidor confirma a nota de um caderno cujas discursivas foram copiadas
- **THEN** a imagem da resposta é eliminada como em "A resposta guardada tem prazo no aparelho", e as amostras continuam no aparelho
