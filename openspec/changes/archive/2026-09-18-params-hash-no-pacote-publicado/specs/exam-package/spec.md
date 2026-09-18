## ADDED Requirements

### Requirement: O pacote declara a proveniência do que o produziu

O pacote publicado SHALL declarar, nos seus metadados, a **tripla de proveniência** que a invariante
I3 exige: a versão do prompt, o identificador do modelo e o hash dos parâmetros da chamada que
produziu o artefato.

Os três SHALL existir no contrato desde já, e SHALL ser **ausentes** enquanto a prova for fixa —
prova fixa não tem artefato de IA, e não há proveniência a declarar. Quando a geração por IA chegar,
ela preenche os três **sem mexer no contrato**, que é o que I3 existe para garantir.

**Ausente e vazio SHALL significar coisas diferentes.** Um dos três com valor vazio SHALL ser lido
como "houve geração e o valor é vazio", e não como "não houve geração" — é a mesma distinção que a
folha avulsa já fixou para o token de aluno, e pela mesma razão: dois estados diferentes colapsados
num só valor produzem leitura plausível e errada, sem sintoma.

O hash dos parâmetros SHALL cobrir os parâmetros da chamada que produziu o artefato, de modo que
dois artefatos produzidos com parâmetros diferentes sejam distinguíveis por ele sem que seja preciso
inferir a partir do conteúdo.

Acrescentar qualquer um dos três ao contrato SHALL mudar o `content_hash` de todo pacote, porque a
serialização canônica inclui campos com valor padrão. Isso não é defeito: é a razão de os três
nascerem no contrato antes de existir geração. Pacotes publicados sob um contrato anterior SHALL
manter os bytes que têm — o armazenamento é imutável — e SHALL ser **recusados** por um consumidor
que interprete o contrato novo, por fidelidade de interpretação e não por integridade. Corrigir uma
prova nessa condição SHALL ser publicar prova nova, com identificador próprio.

#### Scenario: A tripla é declarada numa prova fixa

- **WHEN** uma prova sem geração por IA é publicada
- **THEN** os metadados do pacote trazem os três campos de proveniência, e os três estão ausentes

#### Scenario: Ausente não é vazio

- **WHEN** um pacote declara um dos três campos com valor vazio
- **THEN** ele é distinguível de um pacote que declara o mesmo campo como ausente

#### Scenario: Parâmetros diferentes são distinguíveis

- **WHEN** dois artefatos são produzidos com parâmetros de chamada diferentes
- **THEN** os dois declaram hashes de parâmetros diferentes, e os pacotes têm `content_hash`
  diferentes

#### Scenario: Pacote do contrato anterior é recusado pelo consumidor atualizado

- **WHEN** um consumidor que interpreta o contrato atual recebe um pacote publicado sob o contrato
  anterior, acompanhado do `content_hash` correto daquele pacote
- **THEN** o pacote é recusado **por fidelidade de interpretação**, e NÃO por integridade — o hash
  declarado confere com os bytes recebidos, e o que falha é a reserialização

### Requirement: O identificador de prova dentro do pacote é o mesmo que a folha carrega

O identificador de prova gravado nos metadados do pacote SHALL ser o mesmo valor que identifica a
prova na definição publicada, **e** o mesmo que viaja no código impresso de cada atribuição.

A igualdade SHALL ser afirmada por verificação, e não sustentada apenas por construção. Ela é hoje
consequência de um único ponto de escrita alimentar os três, e nada a prende: o dia em que os três
divergirem, a folha de uma prova passa a ser aceita contra o pacote de outra, e a camada que julga
**de quem é a folha** deixa de julgar coisa nenhuma.

#### Scenario: Os três identificadores coincidem no pacote publicado

- **WHEN** uma prova é publicada com roster
- **THEN** o identificador nos metadados do pacote, o da definição publicada e o que viaja no código
  impresso de cada atribuição são o mesmo valor
