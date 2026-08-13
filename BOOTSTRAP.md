# Instalação

1. Copie `CLAUDE.md`, `openspec/`, `docs/` para a raiz do projeto.
2. Entre na raiz:
   - `openspec init`
3. Escolha Claude Code quando solicitado.
4. Ative o perfil expandido se quiser `verify`, `new`, `continue`, `ff` e `onboard`:
   - `openspec config profile`
   - escolha o perfil expandido
   - `openspec update`
5. Reinicie o Claude Code.
6. Confirme:
   - `openspec doctor`
   - `openspec list`
   - abra `/opsx:...` no Claude Code.

## Primeira utilização

Como o projeto ainda não tem implementação, não tente fazer `/opsx:propose` de todo o sistema.

Primeiro, transforme a arquitetura em pequenos changes por fatia do roadmap, começando pela fatia 0. Uma mudança por vez:

`/opsx:propose slice-0-core-schema-tenancy`

Depois revise os quatro artefatos e só então:

`/opsx:apply`

No final:

`/opsx:archive`

As specs arquivadas passam a descrever a realidade atual. Não faça backfill artificial de specs para tudo que ainda não existe.
