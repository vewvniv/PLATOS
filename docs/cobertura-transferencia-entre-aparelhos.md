# Cobertura — ETAPA 2, a medição do achado 4.3

**Veículo:** medição primeiro. **Resultado: o risco se confirma.**
**Plano:** `docs/plano-de-correcao-antes-da-fatia-5.md`, ETAPA 2 · **Achado:** 4.3 da `docs/auditoria-2026-09-18-antes-da-fatia-5.md`
**Data:** 2026-09-18, janela `15:10Z`–`15:19Z` · **Ambiente autorizado pelo mantenedor (P22)**

---

## 0. A pergunta, e o que a resposta vale

O achado 4.3 é o único da auditoria marcado como **suposto**, e ele se declara assim: *"Não afirmo
que vaza. Afirmo que a decisão foi tomada com um mecanismo e o `targetSdk` mudou o alcance dele, e
que **ninguém mediu**."*

A suposição a medir: com `android:allowBackup="false"` e **sem** `android:dataExtractionRules`, o
conteúdo do diretório de dados do aplicativo atravessa a **transferência entre aparelhos**, que na
`targetSdk ≥ 31` deixou de ser governada pelo mesmo atributo que governa o backup em nuvem.

**Medido. A suposição está certa**, e a severidade sobe de moderada para **grave**, como o próprio
achado previu.

**O que esta medição prova e o que ela não prova** está no §5, e a distinção é a que a ETAPA 2 exige:
*"a transferência disse que copiou" e "o arquivo está lá" são coisas diferentes*. Eu observei o
fluxo de transferência ser montado com os arquivos dentro, **no aparelho de origem**. Não observei
os arquivos no aparelho de destino, e a razão não é desleixo — está medida e citada em §5.1.

---

## 1. O instrumento

| | |
|---|---|
| **Aparelho** | POCO `klee` / `2511FPC34G`, o mesmo da conferência da `slice-4b-outbox-de-resultado` |
| **Sistema** | Android **16**, SDK **36**, HyperOS `OS3.0.304.0.WPJEUXM`, `user/release-keys`, patch 2026-08-01 |
| **Fingerprint** | `POCO/klee_eea/klee:16/BP2A.250605.031.A3/OS3.0.304.0.WPJEUXM:user/release-keys` |
| **Serial** | `TOXSR4MR9989MBQW` |
| **ADB** | 1.0.41, versão 37.0.1-15733141 |
| **Artefato** | `apps/android/build/outputs/apk/debug/android-debug.apk`, do build de 2026-09-18T14:56Z |
| **Instrumento** | `bmgr` — o console do `BackupManagerService` — e `logcat` do agente de backup |

**Por que a variante `debug`, e por que isso não invalida a medição.** O que a medição interroga são
dois atributos do manifesto, e os manifestos mesclados das duas variantes **coincidem** nos dois:

```
merged_manifest/debug/...   → targetSdkVersion="35"  allowBackup="false"  debuggable="true"
merged_manifest/release/... → targetSdkVersion="35"  allowBackup="false"
```

Nenhuma das duas traz `dataExtractionRules`. A única diferença é `debuggable`, e é ela que permite
`run-as` — sem `run-as` não há como semear nem conferir os arquivos, e a alternativa seria medir sem
enxergar o disco.

**Confirmado no que foi instalado, e não no que eu escrevi:**

```
$ adb shell dumpsys package com.platos.android | grep pkgFlags
    pkgFlags=[ DEBUGGABLE HAS_CODE ALLOW_CLEAR_USER_DATA ]

    versionCode=1 minSdk=26 targetSdk=35
```

`ALLOW_BACKUP` **não** aparece — o `PackageManager` registrou a recusa. É o oráculo independente do
§1: quem afirma que o atributo pegou não sou eu lendo o XML, é o sistema listando as flags que
derivou dele.

### A semente

Dois arquivos, nos caminhos exatos que o código usa, com marcador reconhecível:

| Caminho no aparelho | Origem no código | `sha256` |
|---|---|---|
| `files/rosters/11111111-1111-4111-8111-111111111111/MEDICAO42.json` | `RostersEmArquivo`, `rosters/<organization_id>/<exam_short_id>.json` | `c41f39b5c0347bd98db77a59c35414cd715f75655a1429e928d9e61995f3297d` |
| `databases/outbox.db` | `Room.databaseBuilder(context, BaseDoOutbox::class.java, "outbox.db")` | `6c33fd05f2a6c8087451cd3e80eca257abb22d1f89d796d31effd2b91566ff96` |

O roster traz **nome de aluno em texto claro**, como `RostersEmArquivo` grava:

```json
{"puxado_em": 1758200000000, "alunos": [{"token": "MEDICAO-TOKEN-0001", "nome": "MEDICAO-4.3 ALUNO UM"}, {"token": "MEDICAO-TOKEN-0002", "nome": "MEDICAO-4.3 ALUNO DOIS"}]}
```

O `outbox.db` é SQLite real, com a tabela `resultado_pendente` no esquema de
`ResultadoPendenteEntity` e uma linha de correção pendente (37 de 40 pontos).

**Os nomes são sintéticos, de propósito.** Nenhum dado de aluno real entrou nesta medição — o que se
mede é o caminho, e o caminho não distingue.

**A semente foi posta por `run-as`, e não por uma sessão completa do aplicativo.** É uma escolha, e
ela tem consequência: o que se mede é o que o *framework de backup* faz com arquivos no diretório de
dados, não se o aplicativo os escreve — isso a conferência da 4b já observou no destino. O que a
escolha custa é dito em §5.

---

## 2. A medição: quatro transportes, e só um admite o pacote

`bmgr` estava **desabilitado** no aparelho. Foi habilitado para a medição e **devolvido ao estado
original** no fim (§6).

O aparelho oferece quatro transportes. O ativo era o de nuvem:

```
$ adb shell bmgr list transports
    com.android.localtransport/.LocalTransport
    com.google.android.gms/.backup.migrate.service.D2dTransport
  * com.google.android.gms/.backup.BackupTransportService
    com.google.android.apps.restore/.transport.BackupTransportService
```

O mesmo comando, sobre o mesmo pacote, com a mesma semente, em cada um deles:

| # | Transporte | Natureza | Veredito do `BackupManagerService` |
|---|---|---|---|
| A | `com.google.android.gms/.backup.BackupTransportService` | nuvem (padrão) | `Package com.platos.android with result: **Backup is not allowed**` |
| B | **`com.google.android.gms/.backup.migrate.service.D2dTransport`** | **transferência entre aparelhos** | `Package com.platos.android with result: **Success**` |
| C | `com.android.localtransport/.LocalTransport` | nuvem (local) | `Package com.platos.android with result: **Backup is not allowed**` |
| D | `com.google.android.apps.restore/.transport.BackupTransportService` | assistente de restauração | `Package com.platos.android with result: **Backup is not allowed**` |

**Três recusam, um admite.** É esse contraste que faz a medição valer: a variável isolada não é
"backup está ligado" nem "o aparelho permite" — é **o tipo de operação**. `allowBackup="false"`
barra o caminho de nuvem em três transportes diferentes e **não barra o de transferência**.

O log do transporte confirma quem atendeu:

```
I/Backup ( 18809 ): [D2dTransport] performFullBackup : PackageInfo{b155615 com.platos.android}
```

---

## 3. O número, e quais arquivos entraram no fluxo

Recusar ou admitir é o veredito; o que entrou é o que importa. Sob o transporte B, o
`FullBackupAgent` do aplicativo foi iniciado, mediu o diretório de dados e **escreveu entradas no
fluxo de transferência**. As linhas `Name:` são os nomes das entradas no `tar` entregue ao
transporte:

```
I/file_backup_helper( 2325 ):  Name: apps/com.platos.android/_manifest
I/file_backup_helper(17692 ):  Name: apps/com.platos.android/f/rosters
I/file_backup_helper(17692 ):  Name: apps/com.platos.android/f/rosters/11111111-1111-4111-8111-111111111111
I/file_backup_helper(17692 ):  Name: apps/com.platos.android/f/rosters/11111111-1111-4111-8111-111111111111/MEDICAO42.json
I/file_backup_helper(17692 ):  Name: apps/com.platos.android/f/profileInstalled
I/file_backup_helper(17692 ):  Name: apps/com.platos.android/db/outbox.db
I/file_backup_helper(17692 ):  Name: apps/com.platos.android/sp/platos-sessao-cifrada.xml
```

E as medidas por arquivo, do agente:

```
I/FullBackup_native(17692): measured [.../files/rosters/11111111-.../MEDICAO42.json] at 1024
I/FullBackup_native(17692): measured [.../databases/outbox.db] at 12800
I/FullBackup_native(17692): measured [.../shared_prefs/platos-sessao-cifrada.xml] at 2048
```

**O número entregue ao transporte**, do `bmgr`, e o desfecho do agente:

```
Package com.platos.android with progress: 19968/17920
Package com.platos.android with result: Success
Backup finished with result: Success

I/PFTBT ( 2325 ): Full backup completed with status: 0
```

**19 968 bytes** entregues ao transporte de transferência, de um pacote que o transporte de nuvem
recusou por completo.

### Repetido, para a admissão não ser um acaso

Semente recolocada (mesmos dois `sha256`), segunda passada sob o mesmo transporte:

```
Package com.platos.android with progress: 17920/15872
Package com.platos.android with result: Success
```

As entradas se repetiram, **menos** `sp/platos-sessao-cifrada.xml` — que eu havia apagado entre as
passadas e o aplicativo não recriou, por não ter sido reaberto. O total caiu de `17920` para `15872`
medidos, de forma consistente com o arquivo a menos. O fluxo acompanha o disco: não é um número
fixo, é o conteúdo.

---

## 4. Três coisas que a medição corrigiu no enunciado do achado

**4.1 — `outbox.db` não está em `filesDir`.** O achado diz "levaria o `filesDir` inteiro: `rosters/`
… e `outbox.db`". Medido: o roster está em `files/`, mas o banco está em
`/data/data/com.platos.android/**databases**/outbox.db`, e entra no fluxo sob o prefixo `db/`, não
`f/`. São **duas árvores diferentes** no formato de backup. Isso é material para o conserto: uma
regra de `dataExtractionRules` escrita só para o domínio `file` deixaria o `outbox.db` passando — e
passaria despercebida, porque o roster estaria coberto e a conferência pareceria fechada.

**4.2 — Há um terceiro arquivo, que o achado não menciona.**
`shared_prefs/platos-sessao-cifrada.xml` — a credencial guardada pela decisão 5 da `4a-zero` —
**também entra no fluxo**. Ela está cifrada sobre chave do Keystore, e a chave não acompanha a
transferência, então o arquivo deve chegar ilegível ao destino. **Isso não foi medido**, e a
diferença entre "deve chegar ilegível" e "chega ilegível" é exatamente a que esta etapa existe para
não repetir. Fica como item escrito (§7), não como implementação.

**4.3 — A medição é de Android 16, e o achado fala de `targetSdk ≥ 31`.** O que se mediu é o
comportamento deste sistema, neste aparelho. Android 12 a 15 **não** foram medidos, e a conclusão
não se estende a eles por dedução — é o mesmo erro de método que esta etapa proíbe. O que a medição
autoriza afirmar é sobre o `minSdk 26`…`targetSdk 35` **rodando em SDK 36**.

---

## 5. O que ficou sem medição, e por quê (P8)

### 5.1 Os arquivos no aparelho de destino

Não observei os arquivos chegando a um segundo aparelho, e a ETAPA 2 pede exatamente essa asserção.
**A razão está medida, e é do próprio transporte.** A tentativa, completa:

1. Semente conferida por `sha256` no aparelho de origem.
2. Backup sob o transporte D2D → `Success`, 19 968 bytes.
3. Conjunto de restauração existe: `bmgr list sets` → `3d37ae849272a580 : D2D Restore Set`.
4. Os três arquivos apagados do diretório de dados, deixando só `files/profileInstalled` — o estado
   de "aplicativo instalado, sem dado", que é o do destino. (`pm clear` foi **barrado** pelo
   HyperOS: `SecurityException: PID does not have permission android.permission.CLEAR_APP_USER_DATA`.
   O apagamento foi feito por `run-as`, com o mesmo efeito sobre os três caminhos.)
5. `bmgr restore 3d37ae849272a580 com.platos.android` →

```
Scheduling restore: D2D Restore Set
restoreStarting: 0 packages
restoreFinished: -1000

W/Backup (18809): [D2dTransport] List of available restore sets requested. Unsupported operation.
W/Backup (18809): [D2dTransport] Can't restore from D2d Transport.
```

6. Arquivos no destino depois da tentativa: só `files/profileInstalled`. Nada voltou.

**O transporte de transferência é de mão única por construção**: quem recebe é o aparelho par, não o
mesmo aparelho. `Can't restore from D2d Transport` é o transporte dizendo isso. Com **um** aparelho,
a perna do destino não fecha — não por falta de tentativa, mas porque o instrumento não existe aqui.

**Isto não é mitigado, é conhecido.** O que fica provado é que o dado **entra** no fluxo de
transferência com o manifesto de hoje, enquanto o caminho de nuvem o recusa. O que fica por provar é
o último elo, e ele não depende do manifesto: depende do transporte entregar o que recebeu.
Fechá-lo exige **dois aparelhos** e o fluxo real do assistente de transferência.

### 5.2 O resto

- **A variante `release` não foi instalada nem medida.** A equivalência foi estabelecida pelos
  manifestos mesclados (§1), que é argumento sobre a entrada do sistema, não observação da saída.
- **A semente não veio de uma sessão completa do aplicativo.** Se o aplicativo gravasse os mesmos
  arquivos com outra permissão ou outro caminho, a medição não pegaria — os caminhos foram lidos do
  código (`RostersEmArquivo`, `ResultadosEmRoom`), e não observados sendo escritos nesta sessão.
- **Nenhuma medição foi feita com `dataExtractionRules` presente.** É a medição repetida que a
  ETAPA 2 manda fazer **depois** da mudança, e ela é o que vai provar que o conserto conserta. Sem
  ela, o conserto é suposição na outra direção.

---

## 6. O ambiente, devolvido ao estado em que estava

| O que foi mexido | Estado no início | Estado no fim |
|---|---|---|
| `bmgr` | `Backup Manager currently disabled` | `Backup Manager currently disabled` |
| Transporte ativo | `* com.google.android.gms/.backup.BackupTransportService` | `* com.google.android.gms/.backup.BackupTransportService` |
| `log.tag.*` de backup | padrão | devolvidos a `INFO` |
| Semente em `files/rosters` e `databases/outbox.db` | ausente | **removida** (`find` devolve só `files/profileInstalled`) |
| `/data/local/tmp/{outbox.db,MEDICAO42.json}` | ausente | **removidos** (`ls` → `No such file or directory`) |

**Uma coisa não foi devolvida, e fica dita:** `com.platos.android` **não** estava instalado no início
da sessão e **continua instalado**, sem dado de semente. Deixei assim porque a medição repetida de
§5.2 vai precisar dele; desinstalar é um comando, e é do mantenedor a decisão.

---

## 7. Achados novos, com dono e fatia-limite (P19)

Nenhum vira implementação nesta etapa. Os dois entram escritos:

| Item | Dono | Fatia-limite |
|---|---|---|
| **A credencial cifrada também atravessa o fluxo de transferência** (§4.2). O arquivo `shared_prefs/platos-sessao-cifrada.xml` entra no `tar`. A expectativa é que chegue ilegível ao destino, porque a chave do Keystore não acompanha — **e isso não foi medido**. Se a mudança da ETAPA 2 negar o domínio `sharedpref` junto com `file` e `database`, o item fecha de carona; se negar só os dois, o item continua aberto e precisa da medição | mantenedor | junto com a mudança `transferencia-entre-aparelhos` |
| **O comportamento em Android 12–15 não foi medido** (§4.3). A base declara `minSdk 26`. A conclusão desta medição é sobre SDK 36 | mantenedor | antes de qualquer piloto em modo `nominal` num aparelho abaixo de Android 16 |

---

## 8. A conclusão, e o que ela dispara

**O risco se confirma.** Com o manifesto como está hoje — `allowBackup="false"`, sem
`dataExtractionRules`, `targetSdk 35` — o roster com nome de aluno e o `outbox.db` com correções
pendentes **entram no fluxo de transferência entre aparelhos**, num caminho que o transporte de
nuvem recusa. A decisão 4 da `4a-zero` — *"a diferença entre 'outro aplicativo não lê' e 'o dado não
sai do aparelho'"* — continua certa como intenção, e o mecanismo escolhido para cumpri-la deixou de
alcançar o segundo caso quando o `targetSdk` subiu.

Pela ETAPA 2, isso abre mudança OpenSpec sobre `device-session`: o requisito *"Nenhuma das duas
SHALL ser incluída em backup automático do aparelho"* passa a cobrir também a transferência, e o
manifesto ganha `dataExtractionRules` negando-as. **E a medição de §4.1 diz uma coisa que o plano
não sabia:** a negação precisa cobrir **três** domínios — `file` (o roster), `database` (o
`outbox.db`) e, pelo item de §7, provavelmente `sharedpref`. Uma regra escrita só para `file`
deixaria o banco de correções pendentes passando.

A mudança **fecha com esta medição repetida no aparelho, depois dela** — e o critério de aprovação é
o que já está fixado aqui, antes de a correção existir (ADR-0007): sob o transporte
`D2dTransport`, o veredito para `com.platos.android` passa de `Success` a `Backup is not allowed`,
ou o fluxo deixa de conter as entradas `f/rosters/…`, `db/outbox.db` e `sp/platos-sessao-cifrada.xml`.
