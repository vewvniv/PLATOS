## Purpose

O que a API declara sobre si mesma quando perguntada se está no ar: que está servindo, e **qual build
está servindo**. Existe porque "publicado" e "servindo" são elos diferentes da mesma cadeia, e o
segundo não é verificável de fora quando a imagem é seguida por uma tag mutável.

## ADDED Requirements

### Requirement: O serviço responde se está no ar

A API SHALL expor uma verificação de saúde não autenticada que responde **200** quando o processo
está servindo, e o corpo dela SHALL ser exatamente `ok`.

A verificação SHALL NOT exigir credencial, e SHALL NOT depender de banco de dados: ela afirma que o
processo subiu e está atendendo, e nada além disso. Afirmar alcance de banco aqui faria uma
indisponibilidade de banco ser lida como serviço fora do ar, e é o elo seguinte da cadeia que se
observa por outra sonda.

#### Scenario: O serviço está servindo

- **WHEN** a verificação de saúde é chamada sem credencial
- **THEN** a resposta é 200 e o corpo é exatamente `ok`

#### Scenario: A verificação não é uma afirmação sobre o banco

- **WHEN** a verificação de saúde responde 200
- **THEN** nada se conclui dali sobre o banco estar alcançável

### Requirement: A resposta de saúde declara qual build está servindo

A resposta da verificação de saúde SHALL declarar, **em um cabeçalho de resposta**, o identificador
do build que está atendendo. O **corpo** SHALL permanecer exatamente `ok`.

O identificador SHALL ser fixado no artefato **no momento em que ele é construído**, e SHALL NOT ser
lido de configuração de tempo de execução. Identificador vindo do ambiente de execução declara o que
foi configurado, não o que está sendo executado — e é precisamente essa diferença que a verificação
existe para tornar observável.

O identificador declarado SHALL ser **o mesmo** que identifica o artefato publicado, de modo que
conferir "o que está servindo" contra o registro seja comparação direta, sem tradução nem
interpretação.

Quando o artefato foi construído **sem** o identificador — construção local, fora do caminho de
publicação —, a resposta SHALL declarar a ausência **como ausência**, e SHALL NOT declarar valor
inventado, derivado de outra fonte ou de reserva. Valor de reserva é indistinguível de identificador
verdadeiro para quem lê, e quem lê está justamente tentando descobrir o que está no ar.

#### Scenario: O build declarado é o que foi construído

- **WHEN** a verificação de saúde é chamada em um artefato construído pelo caminho de publicação
- **THEN** o cabeçalho traz o mesmo identificador com que aquele artefato foi publicado, e o corpo
  continua sendo exatamente `ok`

#### Scenario: Artefato construído sem identificador

- **WHEN** a verificação de saúde é chamada em um artefato construído sem o identificador
- **THEN** a resposta declara que o build é desconhecido, e nenhum valor inventado aparece no lugar

#### Scenario: Configuração de execução não decide o que é declarado

- **WHEN** o ambiente de execução oferece um identificador diferente do que foi fixado na construção
- **THEN** o declarado continua sendo o da construção

#### Scenario: Duas publicações do mesmo repositório são distinguíveis

- **WHEN** dois artefatos construídos de estados diferentes do repositório são consultados
- **THEN** os identificadores declarados diferem, e qual deles está servindo é determinável sem
  acesso ao painel de quem hospeda
