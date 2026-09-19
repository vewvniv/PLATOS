# Cobertura — ETAPA 2, a medição do achado 4.3

> Este documento tem **duas partes**. A **Parte I** é a medição que decidiu abrir a mudança, de
> 2026-09-18 pela manhã. A **Parte II**, no fim, é a medição repetida **depois** da correção. A
> Parte I não se apaga (P7): ela é o estado que a Parte II corrigiu.

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

---

# Parte II — A medição repetida, depois da correção

**Data:** 2026-09-18, janela `18:24Z`–`18:40Z` · **Mudança:** `transferencia-entre-aparelhos`
**Aparelho e instrumento:** os mesmos da Parte I (§1). **Ambiente autorizado pelo mantenedor (P22).**

A Parte I fica inteira, e nada nela se apaga (P7): ela é o estado que esta parte corrigiu.

---

## 9. O critério, recopiado antes do resultado

De §8, escrito **antes** de a correção existir (ADR-0007), e **não** alterado:

> Sob o transporte `D2dTransport`, o veredito para `com.platos.android` passa de `Success` a
> `Backup is not allowed`, **ou** o fluxo deixa de conter as entradas `f/rosters/…`, `db/outbox.db`
> e `sp/platos-sessao-cifrada.xml`.

**Ocorreu o segundo ramo, e o veredito literal é um terceiro texto que nenhum dos dois previa.**
Isso fica dito em vez de arredondado (P12, P14): a previsão nomeava dois desfechos possíveis para o
veredito, e o real foi `Transport rejected package because it wasn't able to process it at the time`.
O critério **não** foi escrito sobre o veredito — foi escrito sobre o fluxo, e o fluxo é inequívoco.
A disjunção da decisão 5 do `design.md` existia exatamente para este caso, e foi ela que impediu a
tentação de reescrever o critério depois de ver o resultado.

---

## 10. A medição, em duas passadas — e a primeira não fechou

### 10.1 Primeira passada: três domínios negados

`18:34:34Z`, com `file`, `database` e `sharedpref` negados nas duas seções.

```
Package com.platos.android with progress: 2048/512
Package com.platos.android with result: Success
```

**512 medidos**, contra **17 920** da Parte I. O roster, o `outbox.db`, a credencial e os quatro PDFs
que a suíte instrumentada deixou **sumiram do fluxo** — o agente já nem os media. Mas o fluxo não
ficou vazio:

```
I/file_backup_helper: Name: apps/com.platos.android/_manifest
I/file_backup_helper: Name: apps/com.platos.android/r/app_dxmaker_cache
```

**O prefixo `r/` é o domínio `root`, e ele não estava negado.** `app_dxmaker_cache` é um diretório
vazio criado pelo `dexmaker`, e só existe porque a suíte instrumentada roda — o achado não é sobre
ele. É sobre o que ele demonstra: **qualquer diretório criado por `getDir()`, que é API pública,
nasce fora de uma regra que cite apenas `file`, `database` e `sharedpref`.**

**Isto refutou uma justificativa que eu havia escrito.** A decisão 1 do `design.md` considerou
`<exclude domain="root" path="." />` e a **rejeitou** por legibilidade, argumentando que as três
linhas "nomeiam as três árvores que a medição encontrou". O argumento estava errado, e a medição é
que o mostrou: `root` não é um domínio a mais na lista — é o que contém tudo o que ainda não tem
domínio próprio. O texto da decisão 1 não se apaga; ganha a correção ao lado.

### 10.2 Segunda passada: `root` acrescentado aos outros três

`18:37:04Z`, com `root`, `file`, `database` e `sharedpref` negados nas duas seções.

```
Package com.platos.android with result: Transport rejected package because it wasn't able to
                                        process it at the time
Backup finished with result: Success
```

E o transporte diz, por palavras dele, o que aconteceu:

```
I/Backup [D2dTransport]: Package com.platos.android doesn't have any backup data.
I/Backup [D2dTransport]: Canceling full backup of com.platos.android
I/Backup [D2dTransport]: Deleting partial backup data file: com.platos.android due to error: 5
W/PFTBT: Error -1002 backing up com.platos.android
I/PFTBT: Transport rejected backup of com.platos.android, skipping
```

**Entradas escritas no fluxo: nenhuma.** A busca por `file_backup_helper … Name:` no `logcat` da
passada devolve **zero** ocorrências. O que o agente mediu resume-se a diretórios vazios de
`/data/user_de/0/` e ao externo:

```
measured [/data/user_de/0/com.platos.android/files]        at 0
measured [/data/user_de/0/com.platos.android/databases]    at 0
measured [/data/user_de/0/com.platos.android/shared_prefs] at 0
measured [/storage/emulated/0/Android/data/com.platos.android/files] at 0
```

### 10.3 A guarda contra o verde vazio

"Não havia dado a copiar" tem **duas** causas possíveis, e só uma delas é a regra. A outra é o disco
estar vazio — e aí a medição não provaria nada. Conferido **depois** da passada:

```
$ run-as com.platos.android find files databases shared_prefs -type f
files/android-teste.pdf      files/sem-formula.pdf      files/nao-deve-existir.pdf
files/android.pdf            files/profileInstalled
files/rosters/11111111-1111-4111-8111-111111111111/MEDICAO42.json
databases/outbox.db
shared_prefs/platos-sessao-cifrada.xml   shared_prefs/platos-sessao.xml

$ run-as com.platos.android sha256sum …
c41f39b5…5f3297d  …/MEDICAO42.json     ← idêntico ao da Parte I §1
6c33fd05…566ff96  …/outbox.db          ← idêntico ao da Parte I §1

$ run-as com.platos.android cat …/MEDICAO42.json
{"puxado_em": 1758200000000, "alunos": [{"token": "MEDICAO-TOKEN-0001", "nome": "MEDICAO-4.3 ALUNO UM"}, …]}
```

**Nove arquivos no aparelho, os dois `sha256` idênticos aos da Parte I, e o roster ainda legível com
nome de aluno — e o transporte de transferência diz que não há dado nenhum.** É a regra que esvaziou
o fluxo, e não o disco.

### 10.4 Os outros três transportes, para saber que nada mais mudou

| Transporte | Antes (Parte I) | Depois |
|---|---|---|
| `…gms/.backup.BackupTransportService` | `Backup is not allowed` | `Backup is not allowed` |
| `…localtransport/.LocalTransport` | `Backup is not allowed` | `Backup is not allowed` |
| `…apps.restore/.transport.BackupTransportService` | `Backup is not allowed` | `Backup is not allowed` |
| **`…migrate.service.D2dTransport`** | **`Success`, 19 968 bytes, 3 entradas** | **rejeitado, sem dado, 0 entradas** |

---

## 11. A guarda barata, e como ela foi vista falhar

`RegrasDeExtracaoInstrumentedTest`, quatro cenários, lendo o `AndroidManifest.xml` **de dentro do
APK instalado** pelo `AssetManager` — e não `src/main/res/`. A razão está na decisão 4 do
`design.md`: a classe de falha desta mudança é "a intenção está no arquivo e não alcança o sistema".

**Mutação 1 — remover `android:dataExtractionRules` do manifesto.** Previsto **4**, real **4**:

| Cenário | Como caiu |
|---|---|
| `o_aplicativo_instalado_declara_regras_de_extracao` | `AssertionError … Actual: 0` |
| `a_transferencia_entre_aparelhos_nega_os_quatro_dominios` | `Resources$NotFoundException: Resource ID #0x0` |
| `o_backup_em_nuvem_nega_os_mesmos_quatro` | idem |
| `a_exclusao_cobre_a_raiz_de_cada_dominio` | idem |

**O conjunto bateu, e isso não bastava.** Os quatro caem por **uma** causa — não há recurso a
resolver —, o que não prova que os cenários de domínio medem domínios. Um teste que só verificasse a
existência do atributo passaria por todos os quatro.

**Mutação 2 — remover só `<exclude domain="database">`, e só da seção `<device-transfer>`.**
Previsto **1**, real **1**:

```
a_transferencia_entre_aparelhos_nega_os_quatro_dominios
  expected:<[root, file, database, sharedpref]> but was:<[root, file, sharedpref]>
```

Os outros três ficaram verdes — inclusive `o_backup_em_nuvem_nega_os_mesmos_quatro`, que lê a outra
seção do mesmo arquivo. **Os cenários são disjuntos e as duas seções são medidas em separado.**

**Duas coisas deram errado no caminho, e ficam ditas.** A primeira forma da mutação 1 não entrou: um
comentário dentro da tag `<application>` é XML inválido, e o build falhou em vez do teste. E a
verificação de que a mutação entrou **também** falhou, por frouxidão minha — o `grep` casou com a
prosa do comentário da tarefa 1.3 em vez do atributo. Corrigido para
`android:dataExtractionRules\s*=`, e só então a ausência foi confirmada no manifesto mesclado. É o
mesmo defeito que a fatia do outbox registrou em §5.1 — *"a mutação passou a entrar com verificação
de que entrou"* —, com a lição a mais de que **a verificação também precisa ser conferida**.

**Reversão rodada (P10):** `MUTACAO` em `0` fora de prosa, árvore idêntica ao commit, e a suíte
instrumentada **inteira** em `OK (78 tests)`, `18:32:42Z`–`18:33:00Z`.

---

## 12. O ambiente, devolvido

| O que foi mexido | Início | Fim |
|---|---|---|
| `bmgr` | desabilitado | **desabilitado** |
| Transporte ativo | `* …gms/.backup.BackupTransportService` | **o mesmo** |
| `log.tag.*` de backup | padrão | devolvidos a `INFO` |
| Semente (`rosters/`, `outbox.db`) | — | **removida** |
| `/data/local/tmp/*` | — | **removido** |

`com.platos.android` e `com.platos.android.test` continuam instalados, como na Parte I §6.

**Uma parede de operação, e não de código:** o install por ADB foi recusado três vezes com
`INSTALL_FAILED_USER_RESTRICTED: Install canceled by user` — o HyperOS pede confirmação no aparelho,
e ele estava longe do mantenedor. Não é defeito desta base; fica registrado porque qualquer
conferência futura em aparelho vai esbarrar nele.

---

## 13. O que **continua** sem verificação (P8)

- **Os arquivos no aparelho de destino.** Inalterado desde a Parte I §5.1: `D2dTransport` é de mão
  única, e com um aparelho o último elo não tem instrumento. O que esta parte mede é que **nada
  entra** no fluxo — que é o que a mudança controla. Que o par não receba o que não foi enviado é
  dedução, e está dita como dedução.
- **Android 8 a 11.** `dataExtractionRules` só existe a partir da API 31 e o `minSdk` é 26. Nesses
  sistemas este arquivo é ignorado, e o que acontece lá **não foi medido**. Vai para a tabela de
  ponto de não-retorno do §16.
- **Android 12 a 15.** Aberto desde a Parte I §7, e continua: a medição é de Android 16.
- **A variante `release`.** Medido o `debug`, que é o que `run-as` permite inspecionar. A
  equivalência vem dos manifestos mesclados — argumento sobre a entrada do build, não observação da
  saída.
- **`getDir()` e o domínio `root` em produção.** O que foi visto atravessando era um diretório de
  teste vazio. A regra agora nega o domínio inteiro, então o caso está coberto **por construção** —
  mas nenhum diretório de produção sob `root` foi exercitado, porque não existe nenhum hoje.

---

## 14. A verificação final, e uma premissa do plano que a execução desmentiu

**Comandos cheios, nesta sessão, com `timestamp`:**

| Comando | Desfecho | Janela (UTC) |
|---|---|---|
| `./gradlew build` | `BUILD SUCCESSFUL in 36s`, 176 tarefas | `18:47:53Z`–`18:48:30Z` |
| suíte instrumentada inteira | **`OK (78 tests)`** | `18:48:47Z`–`18:49:04Z` |
| `openspec validate transferencia-entre-aparelhos --strict` | válido | — |
| `MUTACAO` fora de prosa | **0** | — |

**A premissa que caiu.** A ETAPA 2 do plano declara: *"é a única etapa que pode correr em paralelo
com as outras, porque não toca nenhuma linha que elas tocam"*. É falso, e a execução mostrou onde:
as tarefas de registro **5.2** e **5.3** tocam exatamente o território da ETAPA 1 — a tabela de
ponto de não-retorno do §16, no mesmo ponto de inserção, e o arquivo da auditoria, que **só existe
depois da ETAPA 1**.

O ramo desta mudança nasceu de `f643abc` e não enxergava a auditoria. A resolução foi rebasear sobre
`vewvniv/registro-da-auditoria-antes-da-5`, o que produziu **um** conflito, no §16, resolvido por
união — as quatro linhas convivem, nenhuma substitui outra.

**Fica escrito porque é aproveitável:** o paralelismo da ETAPA 2 vale para o *código*, e não para o
*registro*. Uma etapa que fecha um achado da auditoria escreve na auditoria, e a auditoria é da
ETAPA 1. Qualquer etapa futura marcada "fora da fila" herda a mesma dependência no fim.
