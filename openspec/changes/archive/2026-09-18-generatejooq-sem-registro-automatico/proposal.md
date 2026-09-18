## Why

`:apps:api:generateJooq` falha de forma intermitente, sempre com o mesmo erro:
`java.sql.SQLException: No suitable driver found for jdbc:postgresql://...`. Está registrado em
`docs/cobertura-slice-4b-outbox-de-resultado.md` §6.5, com **dono: mantenedor** e **fatia-limite: a
próxima que tocar o build da API, ou o primeiro vermelho de CI que custe investigação**.

O que importa não é o custo da retentativa local: é que o `ci.yml:43` roda esse alvo como passo
próprio, num runner novo e **sem retentativa**. Um vermelho desses tem cara de regressão sem ser, e
P15 existe porque esta base já perdeu horas com exatamente isso.

A causa **não foi medida**. A explicação que havia — "falha na primeira execução após reconfiguração"
— descrevia 2 de 7 num dia e foi contrariada por 0 de 4 no outro, e o próprio registro já diz que
afirmar `ServiceLoader` seria suposição apresentada como medição (P6). Esta mudança não tenta provar
a causa. Ela **remove a dependência** de onde o erro pode nascer, e instala o instrumento que nomeia
a causa se ela voltar.

## What Changes

- `GenerateJooqTask` deixa de obter a conexão por `DriverManager`, que resolve o driver por busca
  registrada, e passa a usar o driver **explicitamente**. O erro "No suitable driver found" é
  produzido por essa busca; sem a busca, ele não tem como ser lançado.
- A mesma conexão explícita passa a servir também o `GenerationTool`, em vez de o codegen abrir a
  sua por `withJdbc`. Um caminho de conexão na task, e não dois.
- A falha de conexão passa a ser embrulhada num diagnóstico que nomeia o que estava registrado e por
  qual carregador de classes — de modo que uma volta do sintoma chegue como evidência, e não como
  mistério.
- O fato bruto e o que ele custou ficam escritos, junto do que **não** foi medido.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

Nenhuma. Nenhum comportamento observável do sistema muda: as classes jOOQ geradas são as mesmas
classes, a partir das mesmas migrations. É mudança de ferramenta de build, e por isso a mudança
declara `skip_specs: true`.

## Impact

- `buildSrc/src/main/kotlin/com/platos/build/GenerateJooqTask.kt` — aquisição da conexão e
  diagnóstico.
- Possivelmente `.github/workflows/ci.yml`, **apenas** se a tarefa 1.1 mostrar que teste de `buildSrc`
  não roda no comando cheio.

### O que NÃO será alterado

- **As migrations, o schema e as classes geradas.** Nada em `supabase/migrations` é tocado, e a saída
  do codegen tem de ser byte a byte a mesma — é a tarefa 3.1 que confere isso.
- **O contrato de entradas e saídas da task para o Gradle.** `@InputDirectory`, `@OutputDirectory` e a
  incrementalidade continuam como estão.
- **A retentativa no CI.** Não entra: dar retentativa ao passo esconderia o sinal em vez de removê-lo,
  e P8 chama isso de conhecido, não mitigado.
- **A versão do driver, do jOOQ ou do Testcontainers.** Subir versão sem medir o defeito seria
  trocar um não-medido por outro, e P22 exige aviso antes de mexer no ambiente de qualquer jeito.
- **`postgres:16-alpine`** e o ciclo de vida do container.
