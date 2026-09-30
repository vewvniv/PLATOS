# Proposal

## Why

`ScanActivity.onCreate` lê o caderno guardado com `cadernos.ler(...)` na thread principal (`ScanActivity.kt:138`), e o Room, aberto sem `allowMainThreadQueries` por `CadernosEmRoom.abrir`, recusa consulta ali. **Medido em 2026-09-30**, no emulador `platos-atd34` (Android 14, `timestamp` 20:05:28) e no aparelho `2511FPC34G` (Android 16, 20:05:37): a leitura, chamada de `runOnMainSync` pelo mesmo caminho de abertura, lança `IllegalStateException: Cannot access database on the main thread…`, **com base vazia e com caderno guardado** (2 testes, 2 falhas em cada aparelho). A consulta roda sempre, então o caminho quebra na primeira abertura.

**O que isto é, e o que não é.** É um defeito que chegou à `main` na `slice-5b-3-guardar-a-parcial-e-o-caderno`: a cobertura dela conferiu a **escrita** pelo fio principal (`GuardarCadernoNoFioPrincipalInstrumentedTest`) e a retomada pela thread do instrumento (`CadernoEmRepousoInstrumentedTest`, `EntregaDoCadernoInstrumentedTest`), e **nenhum teste lança a `ScanActivity`** — o caminho da leitura de produção nunca foi exercido (P16: suíte vizinha verde não verifica esta camada). **O que a medição não provou:** o aplicativo cair na tela. O teste usa o caminho do `onCreate`, mas não é o `onCreate`; a tarefa 1.1 fecha isso.

**Por que uma mudança à parte.** Por decisão do mantenedor (2026-09-30): o defeito está em `main`, tem teste pronto, e a `slice-5c-1-a-resposta-fica-no-aparelho` muda exatamente esse ponto do `onCreate` (leitura normalizada do caderno e varredura da retenção). Misturar esconderia o commit de correção (regra 6 do `CLAUDE.md`). A 5c-1 passa a depender desta.

## What Changes

- **A leitura do caderno sai da thread principal.** `ScanActivity.onCreate` lê o caderno fora dela (mesmo padrão de `guardarCadernoEmAndamento`: função de `CoroutineScope` em `Dispatchers.IO`, chamável de um teste) e monta a sessão depois.
- **A montagem da tela espera a leitura.** Até o caderno chegar, não há sessão, câmera nem `ScanScreen`; a recusa de abertura (`NaoAbre`) continua imediata e sem leitura.
- **Um teste lança a `ScanActivity` de verdade**, com pacote guardado e caderno guardado, e confere que ela chega a `RESUMED` com o caderno retomado. É o teste que o defeito não tinha, e **visto falhar** antes da correção.
- **Correção de registro (P7)** na cobertura da 5b-3: a afirmação de retomada foi conferida por um caminho que não é o de produção. A linha errada fica; acrescenta-se a correção.

**Não será alterado:**
- **Nenhum requisito ou cenário de spec.** A spec `scan-session` já diz que a câmera abre sem erro e que o caderno guardado é retomado; o defeito é não-conformidade, e por isso `skip_specs: true`.
- `ScanSession`, `Caderno`, `CadernosEmRoom`, o esquema do Room, a escrita (`onStop`), o outbox, o domínio, a API, o fio.
- **Caderno corrompido:** se o JSON guardado não decodificar, `ler` lança `SerializationException` e a abertura cai do mesmo jeito. É uma falha **diferente** e adjacente, **não corrigida aqui** (regra 6) e registrada como achado; decisão de como degradar é do mantenedor.
- Nenhum `allowMainThreadQueries`: o projeto o recusa por escrito (`OutboxEmRepousoInstrumentedTest`, `CadernoEmRepousoInstrumentedTest`).

## Capabilities

### New Capabilities
<!-- Nenhuma. -->

### Modified Capabilities
<!-- Nenhuma: nenhum requisito muda. O defeito é não-conformidade com `scan-session` ("Abrir a câmera numa prova com discursiva", "O aplicativo fecha no meio da leitura de um aluno"). -->

## Impact

- **Código Android** (`apps/android`): `scan/ScanActivity.kt` (a abertura é dividida em antes e depois da leitura) e uma função nova em `scan/` ao lado de `GuardarCadernoEmAndamento.kt`. Nenhuma tecnologia nova (corrotinas e `lifecycleScope` já estão em uso).
- **Testes:** um instrumentado que lança a Activity; o teste da leitura pelo fio principal que já existe (arquivo não rastreado, medido em 2026-09-30) é reaproveitado como o de regressão da função nova.
- **Domínio KMP, API, banco, contrato do fio:** nenhum.
- **Dívida (§16, P27).** `node tools/divida/divida.mjs` (2026-09-30): a fatia corrente é `5c`, e nenhuma linha que esta mudança alcance vence. Não abre linha nova: o defeito é corrigido aqui, não adiado.
- **Ordem com a `slice-5c-1-a-resposta-fica-no-aparelho`:** esta vem antes. A tarefa 0.4 da 5c-1 ("regime de thread da leitura") deixa de ser pergunta e passa a citar esta mudança.
