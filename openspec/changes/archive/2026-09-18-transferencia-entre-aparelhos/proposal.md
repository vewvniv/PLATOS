## Why

`AndroidManifest.xml` desliga o backup com `android:allowBackup="false"`, e a intenção escrita é **"o
dado não sai do aparelho"** (decisão 4 da `slice-4a-zero-device-auth`). Na `targetSdk 35` esse
atributo deixou de alcançar a **transferência entre aparelhos**, que passou a ser governada por
`android:dataExtractionRules` — ausente nesta árvore.

Isso foi **medido**, e não deduzido da documentação: `docs/cobertura-transferencia-entre-aparelhos.md`
registra a medição de 2026-09-18 em aparelho real. O mesmo pacote, com a mesma semente, sob os quatro
transportes do aparelho: três respondem `Backup is not allowed` e o `D2dTransport` responde
`Success`, com **19 968 bytes** entregues e o agente escrevendo no fluxo as entradas
`f/rosters/<org>/<prova>.json`, `db/outbox.db` e `sp/platos-sessao-cifrada.xml`.

Ou seja: **nome de aluno** e **correções pendentes** entram hoje no fluxo de transferência por um
caminho que o backup em nuvem recusa. O achado 4.3 da auditoria previu que a severidade subiria de
moderada para **grave** se a medição confirmasse. Confirmou.

**Por que agora.** A fatia 5 acrescenta discursiva e transcrição ao caminho do aparelho, e com elas
mais dado no mesmo diretório. E a fatia-limite deste item não é a 5: é **antes de qualquer piloto em
modo `nominal`**, porque em modo nominal o roster carrega nome civil de menor.

## What Changes

- O manifesto ganha `android:dataExtractionRules`, apontando para um recurso XML que **nega** os três
  domínios medidos no fluxo de transferência: `file` (o roster), `database` (o `outbox.db`) e
  `sharedpref` (a credencial cifrada).
- `android:allowBackup="false"` **permanece**. Ele continua sendo o que barra o backup em nuvem, e a
  medição mostrou que ele funciona nos três transportes de nuvem. A regra nova cobre o caso que ele
  deixou de alcançar, e não o substitui.
- O requisito de `device-session` que hoje diz "backup automático" passa a dizer **backup e
  transferência entre aparelhos**, e deixa de falar só da credencial e da organização: passa a nomear
  **tudo o que o aparelho guarda**, inclusive o roster e o resultado pendente.
- Um cenário novo torna a regra reprovável no aparelho, com o mesmo instrumento da medição.

**Não é BREAKING.** Nenhum contrato muda, nenhum dado gravado muda de forma, e nada que funcionava
deixa de funcionar. O que muda é o que o sistema operacional está autorizado a copiar para fora.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `device-session`: o requisito **"A credencial guardada não fica legível no aparelho"** muda. Hoje
  ele diz que a credencial e a organização escolhida não entram em *backup automático*; passa a dizer
  que **nada do que o aparelho guarda** — credencial, organização, visão, pacote, roster e resultado
  pendente — atravessa **backup automático nem transferência entre aparelhos**, e ganha cenário para
  a transferência.

**`result-sync` NÃO é modificado, e a razão é a regra 7 do `CLAUDE.md`.** O `outbox.db` é dado de
`result-sync`, mas a regra que se está escrevendo não é sobre o ciclo de vida do pendente — é sobre a
**fronteira de exportação do aparelho**, que é território de `device-session` e onde a frase já mora.
Repetir a regra nas duas specs duplicaria regra de negócio entre capabilities, e a versão duplicada
envelheceria sozinha. `result-sync` continua dizendo quando o pendente é apagado; `device-session`
passa a dizer por onde ele não sai. As duas não se contradizem: apagar e exportar são coisas
diferentes.

Isso também mantém a mudança em **uma** capability, dentro da regra 3.

## Impact

**Código e recursos**
- `apps/android/src/main/AndroidManifest.xml` — ganha `android:dataExtractionRules`.
- `apps/android/src/main/res/xml/regras-de-extracao-de-dados.xml` — arquivo novo.
- Um teste que reprova o manifesto se o atributo sumir ou se um domínio deixar de ser negado.

**Não muda**
- Nenhum arquivo de `apps/api`, `packages/domain` ou `apps/web`.
- Nenhuma migration, nenhum contrato de rede, nenhum artefato hasheado.
- Nenhum caminho de leitura ou escrita do aplicativo: `RostersEmArquivo`, `ResultadosEmRoom` e
  `SessaoGuardada` continuam como estão.
- `allowBackup="false"` continua no manifesto.
- Cifragem do roster em repouso **fica fora** — não está na auditoria, não está em requisito nenhum,
  e a política §12 atribui a segurança física do aparelho ao usuário. A ETAPA 2 do plano proíbe
  explicitamente trazê-la para cá.

**Verificação**
- A medição de `docs/cobertura-transferencia-entre-aparelhos.md` **repetida no aparelho, depois da
  mudança**. O critério de aprovação já está fixado **antes** de a correção existir (ADR-0007): sob
  `D2dTransport`, o veredito para `com.platos.android` passa de `Success` a `Backup is not allowed`,
  **ou** o fluxo deixa de conter as três entradas medidas.
- Ambiente: **aparelho real, e P22 vale** — perguntar antes de mexer.

**Registro**
- `docs/architecture/ARQUITETURA-FINAL-v3.md` §16: o achado 4.3 não tem linha na tabela de ponto de
  não-retorno, e esta mudança fecha o risco antes de ele precisar de uma. O que entra em §16 é o item
  que **sobra**: o comportamento em Android 12–15 não foi medido (§7 da cobertura).
- `docs/auditoria-2026-09-18-antes-da-fatia-5.md`: o achado 4.3 deixa de estar "suposto" e passa a
  apontar para a medição.

**Referências**
- `docs/cobertura-transferencia-entre-aparelhos.md` — a medição que decidiu esta mudança.
- `docs/auditoria-2026-09-18-antes-da-fatia-5.md` §4.3 — o achado.
- `docs/plano-de-correcao-antes-da-fatia-5.md`, ETAPA 2 — o veículo e as proibições.
- `openspec/changes/archive/2026-09-04-slice-4a-zero-device-auth/design.md`, decisão 4 — a intenção
  original, que continua certa.
