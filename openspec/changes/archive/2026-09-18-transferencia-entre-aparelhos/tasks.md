## 1. O recurso e o manifesto, antes de quem os afirma

A ordem é a regra 1 do `CLAUDE.md` e a decisão de `design.md` — *Migration Plan*: o que muda o
comportamento entra antes da guarda, para que a guarda possa ser vista falhar contra o estado
anterior.

- [x] 1.1 Criar `apps/android/src/main/res/xml/regras_de_extracao_de_dados.xml` com as seções
      `<cloud-backup>` e `<device-transfer>`, cada uma excluindo os domínios `file`, `database` e
      `sharedpref` por inteiro (`path="."`), conforme `design.md` decisões 1 e 2. Verificar com
      `./gradlew :apps:android:assembleDebug` — o recurso inválido falha o `processDebugResources`,
      então o build verde é a afirmação de que o XML é válido para o `aapt2`.
- [x] 1.2 Acrescentar `android:dataExtractionRules="@xml/regras_de_extracao_de_dados"` ao `<application>`
      do `apps/android/src/main/AndroidManifest.xml`, **mantendo** `android:allowBackup="false"`.
      Verificar lendo os manifestos mesclados das duas variantes
      (`build/intermediates/merged_manifest/{debug,release}/…/AndroidManifest.xml`): os dois SHALL
      trazer o atributo novo e continuar com `allowBackup="false"`, como em §1 de
      `docs/cobertura-transferencia-entre-aparelhos.md`.
- [x] 1.3 Atualizar o comentário do `<application>` que hoje justifica só o `allowBackup`, dizendo
      que são **dois** caminhos de cópia automática e que o atributo antigo deixou de alcançar o
      segundo. Citar a medição por caminho de arquivo. Verificar por leitura: o comentário não deve
      afirmar nada que a medição não tenha produzido (P6).

## 2. A guarda barata, vista falhar antes de passar

- [x] 2.1 Escrever `apps/android/src/androidTest/.../session/RegrasDeExtracaoInstrumentedTest.kt`,
      que lê `context.applicationInfo.dataExtractionRulesRes` do **aplicativo instalado** e percorre
      o XML de recurso afirmando as exclusões dos três domínios em `<device-transfer>`
      (`design.md` decisão 4). Verificar com
      `./gradlew :apps:android:connectedDebugAndroidTest --tests '*RegrasDeExtracao*'` — verde.
- [x] 2.2 **Ver falhar, com a mutação certa.** Remover `android:dataExtractionRules` do manifesto
      (e **não** o conteúdo do XML — apagar o arquivo faria falhar o build em vez do teste, o que
      mede o `aapt2` e não a guarda). Rodar a mesma suíte e registrar **quais cenários caem**. A
      previsão: cai o cenário que exige `dataExtractionRulesRes != 0`, e caem os três de domínio por
      não haver recurso a percorrer — **quatro**. Se o conjunto real for diferente, **parar** e
      escrever o real ao lado do previsto (regra 0.5 do plano, P7, P12, P14).
      **Real = previsto: 4.** Mas os quatro caem por **uma** causa — nao ha recurso a resolver —, o
      que nao prova que os tres cenarios de dominio medem dominios. Acrescentada a **mutacao 2.2b**
      por isso: remover so `<exclude domain="database">` da seccao `<device-transfer>`, deixando a
      `<cloud-backup>` intacta. Previsto **1**, real **1**, com a mensagem certa
      (`expected:<[file, database, sharedpref]> but was:<[file, sharedpref]>`). E o que mostra que
      os cenarios sao disjuntos e que as duas seccoes sao medidas em separado.
- [x] 2.3 Reverter a mutação e **rodar a reversão** (P10). Verificar com
      `grep -rn "MUTACAO" --exclude-dir=build .` em `0` fora de prosa e a suíte instrumentada verde
      depois da reversão, com `timestamp`.

## 3. A spec

- [x] 3.1 Conferir que `openspec/changes/transferencia-entre-aparelhos/specs/device-session/spec.md`
      descreve o comportamento que 1.1–2.3 entregaram, e corrigir a spec se a implementação tiver
      revelado algo que ela não previa — a spec descreve o sistema, não a intenção. Verificar com
      `openspec validate transferencia-entre-aparelhos --strict`.

## 4. A medição que fecha, no aparelho

Esta é a verificação da mudança, e não uma conferência a mais. **Ambiente: aparelho real — perguntar
antes (P22).** O critério está fixado desde antes da correção existir (ADR-0007), em
`docs/cobertura-transferencia-entre-aparelhos.md` §8, e **não se mexe nele**.

- [x] 4.1 Instalar o APK com a mudança e confirmar, por `adb shell dumpsys package com.platos.android`,
      que o `PackageManager` derivou o atributo — e que `ALLOW_BACKUP` continua **ausente** de
      `pkgFlags`.
- [x] 4.2 Semear de novo `files/rosters/<org>/<prova>.json` e `databases/outbox.db` com os mesmos
      `sha256` de §1 da cobertura, e reabrir o aplicativo uma vez para que `shared_prefs/platos-sessao-cifrada.xml`
      volte a existir — os três ficheiros da medição original precisam estar lá, ou a medição não é a
      mesma.
- [x] 4.3 Repetir a medição com o mesmo instrumento: `bmgr` sob
      `com.google.android.gms/.backup.migrate.service.D2dTransport`, com `logcat` capturando
      `file_backup_helper`. Registrar o veredito, o número de bytes e **as entradas do fluxo**.
      Aprovado se — e só se — o veredito passar a `Backup is not allowed` **ou** o fluxo deixar de
      conter `f/rosters/…`, `db/outbox.db` e `sp/platos-sessao-cifrada.xml`. Qual dos dois ocorreu
      fica escrito (`design.md` decisão 5).
- [x] 4.4 Repetir a passada sob os **outros três** transportes e confirmar que continuam em
      `Backup is not allowed` — a mudança não pode ter alterado o que já estava certo, e é a única
      forma de saber que não alterou.
- [x] 4.5 Devolver o ambiente ao estado de origem — `bmgr` desabilitado, transporte de volta ao
      padrão, tags de log em `INFO`, semente e `/data/local/tmp` limpos — e registrar a tabela de
      antes/depois, como em §6 da cobertura.

## 5. O registro

- [x] 5.1 Acrescentar a §7 da medição repetida em `docs/cobertura-transferencia-entre-aparelhos.md`:
      instrumento, data, número, qual desfecho do critério ocorreu, e o conjunto de cenários que caiu
      em 2.2 ao lado do previsto. A redação de 2026-09-18 **não se apaga** (P7) — a medição nova é
      uma seção nova.
- [x] 5.2 Acrescentar à tabela de ponto de não-retorno do `ARQUITETURA-FINAL-v3.md` §16 a linha do
      que **sobrou**: `dataExtractionRules` não existe abaixo da API 31 e o comportamento em
      Android 8–15 não foi medido, com fatia-limite "antes de qualquer piloto em modo `nominal` num
      aparelho abaixo de Android 16" e dono. Verificar que a linha traz as quatro colunas que a
      tabela já tem.
- [x] 5.3 No `docs/auditoria-2026-09-18-antes-da-fatia-5.md`, o achado 4.3 deixa de estar **suposto**:
      acrescentar, sem apagar o texto antigo (P7), que a suposição foi medida, confirmou-se e foi
      corrigida, com o ponteiro para a cobertura e para esta mudança.
- [x] 5.4 Verificação final: `./gradlew build` com `timestamp`, a suíte instrumentada verde **depois**
      da reversão da mutação, `openspec validate transferencia-entre-aparelhos --strict`, e a
      seção "o que ainda não foi verificado" da cobertura honesta — o elo do aparelho de destino
      continua sem instrumento, e isso fica dito como conhecido, não como mitigado (P8).
