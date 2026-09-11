## Why

O terceiro elo de P26 — "está servindo o quê?" — é **inobservável de fora**, e isso já custou três
sessões de conversa em vez de um `curl`. O Render segue a tag **mutável** `latest`; o `Source` que o
painel mostra **não é commit deste repositório** (registrado em `docs/deploy-api.md`); e nenhuma rota
distingue duas imagens do mesmo repositório — conferido rota por rota na última vez: não há rota de
publicação, a listagem lê do banco, a entrega do pacote serve os bytes do banco, e `/health` responde
a string `"ok"`.

A consequência não é teórica. Em 2026-09-10 a imagem em produção tinha sido publicada por
`workflow_dispatch` de branch não mesclada, e o que estava servindo ficou indeterminado por dois dias.
Em 2026-09-11 o código de produção mudou de verdade (`ExamPublication.kt`) e a imagem nova continuou
indistinguível: dois `Manual Deploy` seguidos não produziram nenhuma diferença observável, porque a
sonda é **estruturalmente cega** — não apenas inconclusiva. O item está nomeado em
`docs/cobertura-fatia-4a-cache-referencia.md` e em `docs/deploy-api.md`, com dono e fatia-limite.

## What Changes

- **`/health` passa a declarar o build que está servindo, em um cabeçalho de resposta.** O corpo
  continua sendo exatamente `ok`.
- **O identificador é assado na imagem em tempo de build**, por `ARG` no `Dockerfile` alimentado pelo
  `publicar-api.yml` com o **mesmo** identificador curto que já vira tag — de modo que conferir
  "o que está servindo" contra o GHCR seja comparação de string, e não interpretação.
- **Ausência é dita como ausência.** Imagem construída sem o `ARG` — build local, `installDist` à
  mão — responde declarando que não sabe, e **nunca** um valor inventado, derivado ou de reserva.

**O que NÃO muda, e é o ponto da forma escolhida:**

- **O corpo de `/health` continua `ok`, byte a byte.** `HealthTest` afirma isso por **igualdade
  exata**, e `docs/deploy-api.md` manda conferir exatamente isso: os dois seguem válidos sem edição.
- **O health check do Render**, que só olha o código HTTP.
- **Nenhuma outra rota**, e nenhum comportamento autenticado.
- **O Deploy Hook** fica fora: é mudança de workflow, sem comportamento observável, e tem item
  próprio.
- **Nada de `apps/android`, `apps/web`, `packages/domain`, `vision/` ou `omr/`.**

## Capabilities

### New Capabilities

- `service-health`: o que a API declara sobre si mesma quando perguntada se está no ar — que está
  servindo, e **qual build** está servindo.

**Por que capability nova, e não um requisito acrescentado a uma existente:** nenhuma das nove specs
menciona `/health`. A rota existe desde a fatia 0 e **nunca foi especificada** — então isto cria o
primeiro requisito dela, e não altera nenhum. Nenhuma das nove é sobre superfície operacional:
`identity`, `billing`, `exam-package`, `layout-engine`, `print`, `capture-omr`, `scan-session`,
`scoring` e `device-session` descrevem domínio, e enfiar a saúde do serviço em qualquer uma delas
seria escolher a menos errada.

### Modified Capabilities

Nenhuma.

## Impact

**Código**: `apps/api/src/main/kotlin/com/platos/api/http/Routes.kt` (`healthRoutes`),
`apps/api/src/main/kotlin/com/platos/api/config/AppConfig.kt` (ler o identificador como **opcional**,
no padrão que ele já distingue de `required`), `apps/api/Dockerfile` (`ARG` → `ENV`),
`.github/workflows/publicar-api.yml` (passar `--build-arg` com o mesmo identificador que já vira
tag), e `apps/api/src/test/kotlin/com/platos/api/HealthTest.kt` (cenários novos; a asserção existente
do corpo **permanece**).

**Documentação**: `docs/deploy-api.md` — a conferência de "o que está servindo" deixa de depender do
painel e passa a ser um comando. É este documento que hoje diz que o elo não é observável de fora.

**Operação**: nenhuma variável nova no Render, e é deliberado. Identificador que viesse do ambiente de
execução diria **o que alguém digitou no painel**, não o que está na imagem — exatamente a deriva que
esta mudança existe para eliminar.

**Compatibilidade**: aditiva. Quem não lê o cabeçalho não percebe diferença, e quem lê o corpo
continua lendo `ok`.
