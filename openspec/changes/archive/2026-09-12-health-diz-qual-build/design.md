## Context

Ver `proposal.md` — Why. O que este documento acrescenta é o estado medido que condiciona as
escolhas:

- `healthRoutes()` responde `call.respondText("ok")` — `Routes.kt:19-23`.
- `HealthTest` afirma `bodyAsText() == "ok"` por **igualdade exata**, e `docs/deploy-api.md:180` manda
  conferir exatamente isso.
- **O `Dockerfile` não compila nada**: ele empacota `apps/api/build/install/api/`, uma distribuição
  já construída pelo Gradle no runner. Então não há como derivar o identificador de dentro do build
  da imagem — ele precisa **entrar** nela.
- `publicar-api.yml` já calcula `curto=$(git rev-parse --short HEAD)` e o usa como tag
  `sha-$curto`. O identificador que a spec pede já existe no lugar certo.
- `AppConfig.fromEnvironment` já distingue `required(name)` de leitura opcional — o padrão para "pode
  não existir" está pronto e não precisa ser inventado.
- Nenhuma spec menciona `/health`, e nenhum `curl` de CI bate nele.

## Goals / Non-Goals

**Goals:**

- Tornar "o que está servindo" uma **medição**, e não um relato de quem olhou o painel.
- Não quebrar nenhum consumidor existente do corpo de `/health`.
- Deixar a ausência do identificador dita como ausência, sem valor inventado.

**Non-Goals:**

- Não instrumentar nenhuma outra rota, nem acrescentar telemetria.
- Não fazer `/health` afirmar alcance de banco.
- Não tocar o Deploy Hook, que é workflow sem comportamento observável.

## Decisions

### 1. Cabeçalho de resposta, e o corpo fica byte a byte

O identificador vai em `X-Platos-Build`; o corpo continua exatamente `ok`.

**Por que não texto nem JSON.** As duas alternativas mudariam o corpo, e o corpo tem dois
consumidores que afirmam igualdade exata: `HealthTest` e a instrução do `docs/deploy-api.md`. Trocar
o corpo transformaria uma mudança **aditiva** em mudança com migração — por nada, porque a
observabilidade que se quer é a mesma. `curl -sI .../health` responde a pergunta.

**O custo, e ele é aceito:** cabeçalho não aparece num `curl` sem flag, então quem conferir de
memória vai ver só `ok`. É por isso que a atualização do `docs/deploy-api.md` faz parte desta
mudança e não vem depois: o comando de conferência precisa estar escrito onde alguém o procura.

**Alternativas descartadas:** corpo `ok <id>` (quebra a igualdade exata em troca de conveniência de
digitação); corpo JSON (quebra os mesmos consumidores e acrescenta estrutura sem segundo campo para
justificá-la — abstração prematura, regra 8).

### 2. `ARG` no `Dockerfile`, e não variável de ambiente no Render

O identificador entra por `ARG PLATOS_BUILD` → `ENV`, e o `publicar-api.yml` passa
`--build-arg PLATOS_BUILD=sha-$curto` — o **mesmo** `$curto` que já forma a tag, na mesma linha de
comando, para que os dois não possam divergir.

**Por que não variável de execução no Render.** Ela diria o que alguém digitou no painel, não o que
está na imagem. Como o objetivo inteiro é detectar deriva entre o que se acha que está no ar e o que
está, um identificador que o operador pode editar sem reconstruir é **a própria deriva com outro
nome**. Assado na imagem, ele é tão imutável quanto o conteúdo que descreve.

**Consequência aceita:** `docker build` local sem `--build-arg` produz imagem sem identificador. É o
caso que a spec obriga a declarar como ausência — e é bom que ele exista, porque é a única forma de o
cenário de ausência ser exercitável.

### 3. Leitura opcional, e a ausência é um valor nomeado

`AppConfig` lê o identificador pelo caminho **opcional**, nunca por `required`. `required` faria a API
**recusar subir** sem o identificador, o que transformaria uma melhoria de observabilidade em modo
novo de falha de arranque — e quebraria todo `installDist` local.

A ausência é representada por um valor nomeado (e.g. `desconhecido`), decidido **em um lugar só** e
declarado no cabeçalho. Não é `null` silencioso nem cabeçalho omitido: cabeçalho ausente é
indistinguível de proxy que o removeu, e a resposta precisa distinguir "não sei" de "ninguém me
perguntou".

## Risks / Trade-offs

- **O cabeçalho pode ser removido por intermediário** → A resposta atravessa Cloudflare e o Render.
  Cabeçalho `X-` customizado passa nos dois hoje, e a tarefa de conferência em produção existe para
  medir isso em vez de presumir. Se algum dia for removido, o sintoma é cabeçalho ausente, que o
  requisito obriga a distinguir de "desconhecido".
- **Identificador curto pode colidir** → Sete hexadecimais colidem em repositórios grandes. Aqui ele
  é o mesmo que já nomeia a tag no GHCR, então uma colisão seria um problema de tag antes de ser um
  problema de `/health` — e o registro do GHCR guarda o digest completo, que é o oráculo final.
- **Cabeçalho revela informação sobre o build para quem não é autenticado** → É o `short sha` de um
  repositório privado, sem URL nem nome de arquivo. O mesmo identificador já aparece na tag do GHCR,
  que também é privado. Não é credencial e não amplia superfície; se a decisão futura for esconder,
  o veículo é ADR, não preferência.

## Migration Plan

1. **Contrato antes do consumidor** (regra 1): `AppConfig` lê o identificador; depois `healthRoutes`
   o declara; depois o `Dockerfile` o recebe; por último o workflow o passa.
2. **Nenhuma migração de dados, nenhuma variável nova em produção.** A primeira imagem publicada
   depois do merge passa a declarar; as anteriores respondem `desconhecido` se forem reimplantadas —
   o que é a verdade sobre elas.
3. **Reversibilidade**: reverter os commits devolve `/health` ao corpo `ok` sem cabeçalho. Nada
   persistido, nada a limpar.

## Open Questions

Nenhuma. A única que existia — a forma da resposta — foi decidida antes da proposta, com os dois
consumidores do corpo exato na mão.
