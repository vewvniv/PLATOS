## Why

A tarefa 9.2 da `slice-4a-package-pull` não passa: com o pacote puxado, conferido e gravado, o
aplicativo **não abre sem rede depois de o processo morrer**. É risco de entrega da §10 registrado no
§16 da arquitetura, e não cobertura de fatia.

A conferência em aparelho e a exploração de 2026-09-09 mostraram que o diagnóstico registrado cobre
**metade** do caminho. São duas paredes:

1. **Arranque.** `DeviceSession.abrir(temSessaoGuardada = true)` vai a `Consultando`
   incondicionalmente e a consulta sem rede termina em `SemOrganizacao`. É o que a 9.2 registrou.
2. **Listagem.** Mesmo com a organização resolvida, `PreparoDaProva` só conhece provas que a API
   devolveu — nada as guarda. A 9.2b passou porque a lista estava **em memória**; matar o processo a
   destrói.

Atrás das duas está a mesma ausência: **o aparelho não guarda dado mutável de referência**. O pacote
é imutável, endereçado por conteúdo e não precisa de política de frescor; o **nome da organização** e
o **título das provas** existem só na API. ADR-0002 já decidiu que referência mutável mora fora do
pacote imutável, e nenhum documento diz onde ela mora **no aparelho**.

Agora, e não junto da 4b: a 4b traz o **roster** como segundo dado mutável de referência. Resolver a
categoria com um consumidor é uma fatia pequena; resolvê-la com dois é reabrir a decisão sob escopo
maior.

## What Changes

- **Cache de referência por organização**, gravado a cada consulta bem-sucedida: nome da organização,
  provas publicadas (`short_id`, título, `content_hash`) e o carimbo de quando aquilo foi visto.
- **Arranque sem rede** passa a usar a última visão conhecida em vez de parar antes de qualquer
  listagem — a parede 1.
- **Listagem de provas sem rede** vem do cache de referência, cruzada com os pacotes efetivamente
  guardados — a parede 2.
- **A tela distingue leitura cacheada de leitura fresca por marca visual**, e não só por texto, e
  oferece **ação explícita de atualizar**.
- **Revogação observada apaga o cache daquela organização** — quando o servidor responde e o vínculo
  não está mais lá, a escolha cai (decisão 10 da 4a-zero, sem mudança) e os pacotes vão junto, pelo
  caminho que `DeviceSession.sair()` já usa.
- **Sem teto de tempo para a validade offline.** O resíduo aceito fica registrado no `design.md`.
- **Arquitetura:** o risco do §16 ganha linha na tabela de ponto de não-retorno, com gatilho por
  evento, e o §15 ganha nota na entrada da fatia 4 dizendo que a 4b não é proposta antes desta
  mudança estar arquivada.

## Capabilities

### New Capabilities

Nenhuma. A capacidade afetada já existe.

### Modified Capabilities

- `device-session`: dois requisitos mudam.
  - **"A organização apresentada vem da API, e não do aparelho"** — hoje diz que, quando a consulta
    não puder ser feita, o aplicativo SHALL NOT apresentar nome nenhum. Passa a admitir **nome com
    procedência declarada**: o último que a API devolveu, marcado como tal. A intenção do requisito —
    nunca apresentar nome inventado, indistinguível do verdadeiro — é preservada e fica mais forte,
    porque procedência passa a ser visível.
  - **"A prova a escanear é escolhida entre as que a API apresenta"** — passa a admitir a última
    listagem conhecida quando a consulta não puder ser feita, restrita ao que está guardado.

**Pré-condição:** este change é escrito contra a spec de `device-session` **depois** do archive da
`slice-4a-package-pull`, que é quem leva o segundo requisito para `openspec/specs/`.

## Impact

**Muda:** `apps/android` — `DeviceSession`, a porta de sessão guardada e seu adaptador,
`PreparoDaProva`, `SessaoActivity` e as telas de trabalho e de escolha de prova.

**NÃO muda, e a lista é critério de aceite:**

- o `ExamPackage`, o cache de pacotes endereçado por conteúdo e as duas conferências da 4a;
- o gate de pré-voo (ADR-0009) — sem pacote conferido a câmera continua não abrindo;
- a decisão 10 da 4a-zero no caso que ela decidiu: **servidor respondeu e o vínculo não está lá**
  continua derrubando a escolha guardada;
- a autenticação: **refresh token continua fora**. O `access_token` vive 60 min e não é renovado, e
  isso é escopo separado — só entra se a fricção de relogar virar problema real em uso;
- o servidor, o `apps/web` e `packages/domain`;
- Room, outbox e modo degradado, que são da 4b.
