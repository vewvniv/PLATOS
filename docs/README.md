# Bootstrap OpenSpec + Claude Code

Este diretório contém apenas a configuração inicial. Copie o conteúdo para a raiz do projeto.

## Estrutura

- `CLAUDE.md` — memória/instruções permanentes e curta.
- `openspec/config.yaml` — contexto injetado pelo OpenSpec em todos os artefatos.
- `docs/adr/README.md` — regra de uso dos ADRs.
- `docs/architecture/ARQUITETURA-FINAL-v3.md` — coloque aqui o SAD v3 original.

## Importante

Não crie manualmente `.claude/skills/openspec-*`. O OpenSpec gera esses arquivos a partir de `openspec init`/`openspec update`.

O cache de prompt do Claude Code não é controlado pelo `CLAUDE.md`. A principal otimização possível no nível do repositório é manter o contexto estável, pequeno e bem referenciado, e reutilizar a mesma sessão quando estiver trabalhando na mesma mudança. O OpenSpec mantém o contexto arquitetural persistente em arquivos e injeta apenas o contexto necessário nos artefatos.

## ADRs

Use um ADR novo para qualquer alteração das decisões D1–D45 ou das três invariantes I1–I3.
