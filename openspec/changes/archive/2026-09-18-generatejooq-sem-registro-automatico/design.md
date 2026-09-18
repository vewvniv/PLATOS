## Context

Ver `proposal.md` — Why. O estado que importa:

- `GenerateJooqTask` abre **duas** conexões por execução, por caminhos diferentes:
  `DriverManager.getConnection(postgres.jdbcUrl, …)` para aplicar as migrations, e depois o
  `GenerationTool` abre a sua a partir de `Jdbc().withDriver("org.postgresql.Driver").withUrl(…)`.
- As duas divergem no ponto exato do sintoma: `DriverManager.getConnection` **procura** um driver
  entre os registrados e lança `No suitable driver found` quando não acha; o caminho do
  `GenerationTool` nomeia a classe do driver.
- `buildSrc/build.gradle.kts` já declara `libs.postgresql` como `implementation`. A classe está no
  classpath; o que falha, quando falha, é a busca — não a presença.
- `buildSrc` **não tem `src/test`** hoje, e o Gradle é 9.7. Se teste de `buildSrc` roda no comando
  cheio do CI é pergunta de medição, e não de memória (tarefa 1.1).

## Goals / Non-Goals

**Goals:**

- Que `No suitable driver found` deixe de ser um desfecho possível desta task, por construção.
- Que uma falha de conexão futura chegue com o que é preciso para nomeá-la, em vez de exigir uma nova
  rodada de bisect.

**Non-Goals:**

- **Provar a causa da intermitência.** A mudança não afirma tê-la encontrado, e o documento de
  cobertura vai dizer isso com essas palavras. O que se pode afirmar depois é: a dependência foi
  removida, e o modo de falha foi reproduzido de propósito.
- Mudar versão de driver, de jOOQ ou de Testcontainers.
- Garantir que a task não falhe por **outro** motivo — container que não sobe, migration inválida,
  porta ocupada. Esses continuam falhando, e devem.

## Decisions

### 1. O driver é usado explicitamente, e não procurado

A conexão passa a ser obtida instanciando o driver do Postgres e pedindo a ele a conexão para a URL,
com usuário e senha em `Properties`. Não há busca entre drivers registrados, então não há como o
resultado ser "nenhum driver serve".

*Alternativa considerada:* `Class.forName("org.postgresql.Driver")` antes do `DriverManager`. Rejeitada
por ser a mesma dependência com um passo a mais: continua dependendo de o registro estático ficar
visível para o `DriverManager` que faz a busca, e é justamente essa visibilidade que não foi medida.

*Alternativa considerada:* `DriverManager.registerDriver(Driver())` no topo da task. Rejeitada pela
mesma razão, mais um efeito global no processo do daemon do Gradle que sobrevive à task.

### 2. Uma conexão só, usada também pelo codegen

O `GenerationTool` aceita receber uma conexão pronta em vez de abrir a sua por `withJdbc`. Passar a
mesma conexão tem três efeitos: some o segundo caminho de aquisição, o codegen enxerga exatamente o
schema que as migrations acabaram de criar, e a task passa a ter **um** lugar onde a conexão pode
falhar — que é onde o diagnóstico da decisão 3 mora.

*Trade-off:* a conexão fica aberta durante a geração inteira, e não só durante as migrations. Contra
um container efêmero que morre no fim do bloco, isso não custa nada.

### 3. A falha de conexão carrega o que seria preciso para diagnosticá-la

Se a aquisição falhar, a task falha com uma mensagem que traz os drivers registrados no
`DriverManager`, o carregador de classes da task e o do driver, e a URL. **Isto não é a correção** —
é o instrumento que faltava quando o sintoma apareceu. Sem ele, o próximo vermelho custa o mesmo bisect
de 7 commits que já foi pago uma vez.

O diagnóstico é escrito na exceção, e não em `logger.lifecycle`: log de build some no ruído de um
runner, e a mensagem da exceção é o que o CI mostra no passo vermelho.

### 4. "Ver falhar" é uma reprodução dirigida, e não um teste permanente

O sintoma é intermitente — 2 de 7 num dia, 0 de 4 no outro —, então esperar que ele apareça não é
método. O que se faz é **provocá-lo**: remover do `DriverManager` os drivers registrados antes da
aquisição põe o processo exatamente no estado em que o erro observado nasce.

Sob essa mutação, o código de hoje SHALL falhar com `No suitable driver found`, e o código novo SHALL
passar. É isso que torna a afirmação "a dependência foi removida" verificável em vez de plausível: a
mutação não simula a causa, ela produz a **condição** em que o sintoma é obrigatório.

Se a tarefa 1.1 mostrar que teste de `buildSrc` roda no comando cheio, a reprodução vira teste
permanente; se não, ela fica como reprodução registrada e a ausência de guarda automática entra no
documento de cobertura como lacuna, com dono. **A decisão de qual dos dois depende de uma medição, e
não da preferência de quem implementa.**

**Atualizado em 2026-09-18, com o que a medição trouxe (P17).** O parágrafo acima continua certo no
método e errado na enumeração: ele previa dois desfechos, e o medido foi um terceiro. Teste de
`buildSrc` **não** roda no comando cheio — `./gradlew build` ficou verde com uma sonda que chama
`fail()` —, mas passar a rodar custa `testImplementation(kotlin("test"))`, `useJUnitPlatform()` e um
passo no `ci.yml`. A alternativa "sem guarda" só era a escolha certa enquanto a guarda parecia cara.

Decidido pelo desenvolvedor: ligar a guarda. Então a reprodução dirigida **e** o teste permanente
existem, e a lacuna que sobra é outra — a guarda depende de um passo de CI que pode ser removido por
quem não souber por que ele existe (`docs/cobertura-generatejooq-sem-registro-automatico.md` §5.3).

**Sobre `kotlin("test")` em `buildSrc` e a regra 4 do `CLAUDE.md`:** não é tecnologia nova. É a mesma
biblioteca de teste que `apps/api`, `apps/android` e `packages/domain` já usam; o que é novo é o
`buildSrc` ter suíte, e o consumidor dela é a guarda desta mudança. Nenhuma dependência entrou no
catálogo de versões.

## Risks / Trade-offs

- **A mudança pode não tocar a causa, e a intermitência voltar por outro ponto** → É o risco assumido,
  e é por isso que a decisão 3 existe: a próxima ocorrência chega com nome e endereço. O documento de
  cobertura registra que a causa não foi medida.
- **A saída do codegen pode mudar sem ninguém notar** → A tarefa 3.1 compara as classes geradas antes e
  depois. Sem essa comparação, "o build passou" não distingue gerar o mesmo de gerar outra coisa que
  também compila.
- **Mexer em `buildSrc` invalida a configuração do Gradle e reconfigura tudo** → Que é precisamente a
  condição em que o sintoma foi observado 2 vezes. O primeiro build depois desta mudança é, ele
  próprio, uma amostra — e vai ser registrada como uma, não como prova.
- **`Driver.connect` devolve `null` para URL que o driver não aceita**, em vez de lançar → Uma
  aquisição que devolvesse `null` calada trocaria um erro claro por um `NullPointerException` adiante.
  A task confere e falha com mensagem própria.
