# Cobertura de cenários — `health-diz-qual-build` (a API diz qual build está servindo)

O registro **por tarefa** — data, instrumento e número — vive em
`openspec/changes/.../health-diz-qual-build/tasks.md`. Este documento existe para que um achado seja
encontrável **por assunto**, e traz **como** cada verificação crítica foi vista falhar.

## Como cada verificação crítica foi vista falhar

### O identificador obrigatório, que derrubaria o arranque (tarefa 1.3)

A decisão 3 do `design.md` diz que a leitura é **opcional**, nunca `required`: exigir o identificador
transformaria uma melhoria de observabilidade em **modo novo de falha de arranque**, e quebraria todo
`installDist` local.

| Defeito introduzido | Resultado |
|---|---|
| `build = required("PLATOS_BUILD")` | **5 testes, 4 vermelhos** |

**O que importa é o modo, e ele bateu:** os dois cenários de ausência caíram por
`java.lang.IllegalStateException: Variavel de ambiente obrigatoria ausente: PLATOS_BUILD` — falha de
**arranque**, e não de igualdade. Os outros dois (vazio e espaços) caíram por igualdade
(`expected: <desconhecido> but was: <>`), porque `required` removeu de uma vez a opcionalidade **e** o
`isNotBlank`.

**Tentei afiar a mutação para isolar só a opcionalidade e não é possível:** as duas proteções
compartilham o mesmo `?:`. Cair juntas é fato do desenho, não imprecisão do instrumento — o que a
mutação isola é o **modo** de falha.

### O cabeçalho omitido quando o build é desconhecido (tarefa 2.3, mutação A)

Omitir o cabeçalho faria "não sei" ficar indistinguível de "um intermediário o removeu no caminho" —
e quem consulta a rota está justamente tentando descobrir o que está no ar.

| Defeito introduzido | Vermelhos | Verdes |
|---|---|---|
| `if (build != "desconhecido")` antes de emitir | **1**: `sem identificador, a resposta declara desconhecido em vez de omitir`, com `o cabecalho foi omitido quando o build e desconhecido ==> expected: not <null>` | os três restantes, **incluindo o do corpo** |

### O identificador no corpo, que quebraria os consumidores (tarefa 2.3, mutação B)

Esta é a mutação que prova que a mudança é **aditiva**: se pôr o identificador no corpo não
derrubasse a asserção **pré-existente**, ela não estaria protegendo o que se afirma.

| Forma da mutação | Vermelhos | Leitura |
|---|---|---|
| primeira: substituir o bloco inteiro por `respondText("ok $build")` | **4 de 4** | **Defeito da mutação, não da previsão** — ela removeu o cabeçalho *junto* com a mudança do corpo, duas alterações numa |
| afiada: **manter** o cabeçalho e só acrescentar ao corpo | **2**, os dois do corpo | `health responde 200`, o cenário **pré-existente**, caiu com `expected: <ok> but was: <ok sha-cdd12e8>`; os dois de cabeçalho ficaram **verdes** |

A disjunção que a tarefa pedia: corpo e cabeçalho são medidos em conjuntos separados.

## As medições, e contra qual oráculo

### A cadeia inteira, fora da JVM (tarefa 3.3)

Duas imagens construídas, um Postgres em container ao lado, e o cabeçalho lido por `curl`:

| Imagem | `X-Platos-Build` | Corpo |
|---|---|---|
| com `--build-arg PLATOS_BUILD=sha-teste99` | **`sha-teste99`** | `ok` |
| sem `--build-arg` | **`desconhecido`** | `ok` |

`ARG` → `ENV` → `AppConfig` → cabeçalho, com a ausência declarada como ausência. Antes disso,
`docker inspect` confirmou o `ENV` assado: `sha-teste99` numa imagem e string **vazia** na outra — o
caso que o `isNotBlank()` trata.

**Duas coisas que só a medição mostrou, e a segunda corrigiu um erro meu:**

1. **A API não sobe sem banco alcançável.** O Hikari conecta no arranque, então ler `/health` de um
   container exige um Postgres ao lado. Não é obstáculo; é fato para quem repetir.
2. **`curl -sI` não serve, e era o comando que eu havia escrito no `design.md` e na tarefa.** `-I`
   manda `HEAD`, a rota só responde `GET`, e o resultado é **405 Method Not Allowed** com o cabeçalho
   invisível. O comando certo é `curl -s -D - -o /dev/null`, e é ele que está no `docs/deploy-api.md`.

## O que ficou sem verificação automática, e por quê

| O que | Por quê |
|---|---|
| **Que o cabeçalho atravesse os intermediários de produção** | A resposta passa por Cloudflare e pelo Render. Cabeçalho `X-` customizado passa nos dois **hoje**, e a tarefa 4.2 mede isso em produção em vez de presumir. Se algum dia for removido, o sintoma é cabeçalho **ausente**, que o requisito obriga a distinguir de `desconhecido` |
| **Que o identificador corresponda ao digest** | O cabeçalho declara o **commit**, não o digest da imagem. Dois builds do mesmo commit têm digests diferentes e declarariam o mesmo identificador. Para o uso que motivou a mudança — "o Render puxou a imagem nova?" — o commit basta, porque a tag `sha-<curto>` é construída do mesmo commit. Fechar isso exigiria assar o digest, que não existe no momento em que a imagem é construída |
| **`HEAD /health`** | Responde **405**, e isto ficou medido. A spec fala da resposta à verificação de saúde e não menciona método, então tratar `HEAD` seria escopo além dela. Fica nomeado: quem usar `-I` por hábito vê 405 e pode ler como serviço quebrado |
| **A fiação em `Application.module`** | Que `healthRoutes(dependencies.build)` receba o valor certo é fiação, e nenhum teste desta base a alcança — é a mesma lacuna de `@Composable` do lado Android. Mitigado em parte por não haver valor padrão: o compilador cobra quem monta as dependências. Paga-se na 4.2, em produção |

## Achados de método

**Três imprecisões do meu instrumento de conferência na tarefa 3.2, nenhuma do arquivo:** os `\`
de continuação de linha não sobreviveram à escrita; o primeiro filtro de YAML pegou o passo errado
(`Construir a distribuicao`, de uma linha, em vez de `Construir e publicar`); e o segundo contou a
**linha de comentário** que menciona `--build-arg` como se fosse comando. Registrado porque o padrão
é o que interessa: eu estava cuidadoso com o código e desleixado com a verificação, que é a inversão
que a §3 do `rigorous.md` existe para pegar.

**E um bug real, pego pelo próprio desenho:** ao fiar o cabeçalho eu dei valor padrão a
`ApiDependencies.build` — contradizendo o KDoc que eu acabara de escrever — e esqueci de passar
`config.build` no `main()`. Produção declararia `desconhecido` em silêncio. Removido o padrão, **o
compilador** cobrou o ponto que faltava.
