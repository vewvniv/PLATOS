## ADDED Requirements

### Requirement: A sessão não abre sem saber de qual prova ela é

O escaneamento SHALL abrir apenas quando o aplicativo souber, antes de a câmera ligar, **de qual
prova** a sessão é. Faltando o identificador da prova, o aplicativo SHALL apresentar a recusa com
motivo e SHALL NOT abrir a câmera.

O motivo dessa recusa SHALL ser **próprio**, e SHALL ser distinguível de cada um dos motivos pelos
quais o gate de pré-voo já barra a sessão. Colapsá-lo em "não há pacote conferido" diria ao professor
para baixar de novo uma prova que já está conferida no aparelho, e a ação sugerida não conserta nada.

Estando a sessão aberta, o identificador da prova e a organização SHALL estar disponíveis a todo o
caminho que vai da apuração à gravação. **Nenhuma nota apurada SHALL ser descartada por falta deles**:
a ausência é decidida **antes** de a câmera abrir, e não no momento de gravar. Uma folha medida cuja
nota aparece na tela e não vira resultado durável é falha em silêncio, e o sistema nunca falha em
silêncio.

#### Scenario: Sem o identificador da prova, a câmera não abre

- **WHEN** o escaneamento é pedido sem o identificador da prova
- **THEN** o aplicativo apresenta a recusa com motivo próprio e a câmera não é aberta

#### Scenario: O motivo é distinguível dos motivos do gate

- **WHEN** a recusa por falta do identificador da prova é apresentada
- **THEN** ela é distinguível de "sem rede", "pacote ausente", "conferência falhou", "versão
  insuficiente" e "roster ausente", e não pede ao professor que baixe a prova de novo

#### Scenario: Aberta a sessão, a nota apurada sempre vira resultado durável

- **WHEN** uma folha é apurada numa sessão aberta
- **THEN** o resultado é gravado, e não existe caminho em que a nota apareça na tela sem ser gravada
