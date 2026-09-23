# Cobertura — ETAPA 7.2, `o-apk-de-release-e-verificado`

> Duas partes, no molde das coberturas da 7.1 e da 7.3. A **Parte I** é a medição de entrada, feita
> **antes** de qualquer código: os buracos dos itens 1 e 2, e a reprodução do item 4 — que levou
> quatro rodadas e mudou duas vezes o que se sabia. A **Parte II** é a da mudança, e ainda não existe.
> A Parte I não se apaga (P7).

**Plano:** `docs/plano-de-correcao-antes-da-fatia-5.md`, ETAPA 7.2 · **Achados:** 3.1 e 5.3 da
`docs/auditoria-2026-09-18-antes-da-fatia-5.md`, e os itens novos 7.2.4 e 7.2.5
**Data:** 2026-09-23, janela `19:26Z`–`20:11Z`
**Ambiente:** Docker ligado pelo mantenedor; emulador `platos-atd34` (API 34) subido por esta sessão,
com autorização; e, a partir das `19:52Z`, o aparelho físico **2511FPC34G / Android 16**
(`TOXSR4MR9989MBQW`), conectado pelo mantenedor e autorizado **só para a suíte instrumentada**. O
aplicativo não estava instalado nele (`pm list packages com.platos` vazio), então a desinstalação que o
AGP faz ao fim de cada execução não levou dado nenhum. Com os dois conectados, cada execução foi
apontada por `ANDROID_SERIAL`, e o nome do XML confirmou o aparelho.
**Base:** `85fa19f`, na branch `vewvniv/o-apk-de-release-e-verificado`, sobre `3e7df3b`

---

# Parte I — a medição de entrada

## 1. A linha de base desta sessão

- `./gradlew build --continue --rerun-tasks`, `19:26:15Z`–`19:29:03Z`, `exit 0`, **176 de 176 tasks
  executadas**; pelo `timestamp` de dentro dos XML: **153 suítes, 1446 testes, 0 falhas** —
  `apps/android` 308, `apps/api` 167, `packages/domain` 329 + 321 + 321. Igual à linha de base da 7.1.
- `./gradlew :apps:android:connectedDebugAndroidTest`, sem filtro, no emulador, `19:29:23Z`–`19:30:17Z`:
  **83 testes, 0 falhas, 2 pulados**.
- A classe da credencial isolada, no emulador, `19:31:16Z`: **2 de 2**.

**A previsão da instrumentada estava errada, e a árvore não (P7).** O `tasks.md` previa **86**, lido
da linha "Finished 86 tests" do log da `paridade` da PR #60. Essa linha **não é contagem de testes**: as
duas execuções dizem "Starting **83** tests", e "Finished" soma os pulados — CI 83 + 3 = 86, aqui
83 + 2 = 85. O pulado a mais do CI é `SupabaseFailureProbe.relatarFalha`, que lá não tem
`probe.supabaseUrl`; aqui o `local.properties` tem a chave (conferida a presença, e não o valor), e o
probe **rodou e passou**, em 2,71 s.

**O instrumento errou duas vezes, e as duas foram vistas.** O contador do scratchpad lia só o primeiro
`<testsuite>` de cada arquivo, e o XML da instrumentada é um envelope `<testsuites>` com uma suíte por
classe: ele disse "5 testes". O total foi lido do envelope. E 30 relatórios ficaram fora da janela da
linha de base — o de `buildSrc` e os 29 de `testReleaseUnitTest` que a medição da proposta (por *init
script*, `18:58Z`) deixou em `build/`. Estado velho no instrumento (P3), excluído pelo `timestamp`.

## 2. O buraco do item 1 — o release não passa pela guarda

Um JSON com a forma de pacote (`"answer_key"`, `"min_renderer_version"`, e `"MUTACAO"`) em
`apps/android/src/release/assets/`. `./gradlew :apps:android:assembleRelease
:apps:android:verificarApkSemPacote --rerun` — o `--rerun` força a guarda a executar, em vez de servir
`UP-TO-DATE` —, `19:31:51Z`–`19:31:59Z`:

| | Previsto | Real |
|---|---|---|
| a guarda | verde | **verde**, "APK sem pacote de prova: 0 asset(s) JSON conferido(s)" |
| `android-release-unsigned.apk` | contém a entrada | `['assets/MUTACAO-pacote.json']` |
| `android-debug.apk` | não contém | nenhum JSON em `assets/` |

**O pacote estava no artefato que vai ao professor, e a guarda disse que não havia pacote nenhum.**
Aberto com `zipfile` do Python. Removidos o arquivo e o diretório `src/release/`, criados pela medição;
a guarda de novo às `19:32:17Z`, verde, e o release sem a entrada.

## 3. O buraco do item 2 — um defeito que só existe no release passa pelo `build`

`MutacaoSoNoReleaseTest`, em `apps/android/src/test`, com `assertTrue(BuildConfig.DEBUG, …)`: passa no
debug, e só cairia onde `BuildConfig.DEBUG` é falso. `./gradlew :apps:android:test --rerun-tasks`,
`19:32:39Z`–`19:33:11Z`, `exit 0`, 40 de 40 tasks: o log traz **só** `testDebugUnitTest`; **309
testes, 0 falhas**, e o plantado entre eles, passando. Removido; o mesmo comando às `19:33:21Z`, 308.

## 4. O item 4 — a credencial, em quatro rodadas

A cronologia importa, porque cada rodada mudou o que se sabia, e duas conclusões intermediárias estavam
erradas. Todas as execuções são `:apps:android:connectedDebugAndroidTest`, lidas do XML.

### Rodada 1 — o emulador, nesta árvore (a, b, c do `design.md`)

| Execução | Hora UTC | Real |
|---|---|---|
| (a) `SegundoMembroInstrumentedTest`, `SessaoEmRepousoInstrumentedTest` | `19:34:06Z` | 5 de 5 |
| (b) suíte cheia, três vezes | `19:34:27Z`–`19:36:33Z` | 83, 83, 83 — 0 falhas |
| (c) `notClass=InstanciaUnicaDoOutboxInstrumentedTest,AcumuloDeInstanciasProbe`, três vezes | `19:36:45Z`–`19:38:53Z` | **78**, 78, 78 — 0 falhas |

(c) recompõe a suíte de 78 testes que caía em 19/09 (`cobertura-o-pendente-nao-se-perde-no-aparelho.md`
§1). As classes saíram do `git log` pela data do **autor** — a do *committer* muda no rebase e apontava
o dia errado.

### Rodada 2 — o emulador, na árvore daquela hora

Por decisão do mantenedor. *Worktree* em `42e94cd`, o pai de `75f05ed`, com `apps/android` e
`packages/domain` idênticos aos de `9c6b4e8`, a ponta da ETAPA 4. `19:41:12Z` e `19:42:44Z`: **78 de
78**, duas vezes.

**Duas hipóteses caem aqui.** A da ETAPA 5 — "parou de cair **por mudança de ordem**" —, porque (c)
refaz a ordem daquela hora e não cai. E a minha, feita depois da rodada 1: que `75f05ed` (a instância
única do Room, escrita às `00:10Z`, entre a última execução vermelha e a primeira verde) tinha parado a
falha ao deixar de vazar descritores de arquivo — porque a árvore **anterior** a ela também não cai.

### Rodada 3 — o aparelho em que a falha aconteceu

O dado que o `design.md` não tinha: as execuções vermelhas de 19/09 rodaram no **2511FPC34G / Android
16**, e não em emulador (`cobertura-o-pendente-nao-se-perde-no-aparelho.md:6`: "Nenhum emulador foi
subido"). O mantenedor conectou o aparelho.

| Árvore, no aparelho | Hora UTC | Real |
|---|---|---|
| esta, suíte cheia | `19:53:23Z` | 83, 0 falhas |
| `42e94cd`, suíte cheia, duas vezes | `19:54:16Z`, `19:55:01Z` | 78, 78 — 0 falhas |

Nada caiu. O *worktree* foi removido — o `git worktree remove` falhou por nome de arquivo longo e
desregistrou o *worktree* sem apagar o diretório, que era do scratchpad e foi apagado à parte.
**Conclusão da rodada, levada ao mantenedor: causa desconhecida.** Ela estava errada, e a rodada 4 diz
por quê.

### Rodada 4 — a tabela do defeito plantado, e a reprodução

O mantenedor decidiu rodar a tabela do ver falhar nos dois aparelhos, sem conserto. O defeito plantado
é o do cenário da spec: `guardarCredencial` passa a gravar o token **também** em claro no arquivo comum
— a escrita cifrada continua, para as guardas de preparo passarem e só a afirmação de segurança poder
acusar.

| Aparelho | Isolada | Na ordem da hipótese | Suíte cheia |
|---|---|---|---|
| emulador | cai só `aCredencialNaoEstaEmClaro` | idem | idem, 1 de 83 |
| 2511FPC34G | idem | idem | idem, 1 de 83 |

Nas seis, a mensagem é a mesma: "o token aparece como texto legivel no armazenamento do aplicativo"
(`19:59:04Z`–`20:01:53Z`). **A propriedade que o plano pede vale hoje: o mesmo desfecho, pelo mesmo
motivo, em qualquer ordem e nos dois aparelhos.** O XML desta versão do AGP guarda a mensagem no corpo
do `<failure>`, e não num atributo — a extração automática não achou nada, e a mensagem foi lida do log
de cada execução.

**A reversão, rodada, e a reprodução dentro dela.** Produto revertido (`git diff --exit-code` → `0`),
e as seis de novo. No emulador, as três verdes (`20:02:17Z`–`20:03:23Z`). No aparelho, as três primeiras
saíram com `exit 1` e **zero testes**: falha de infraestrutura — "failed to start daemon",
`UtpException … DEVICE_PROVIDER_CONFIG_ERROR` —, e não de teste (P15). Repetidas com o `adb` de pé:

| No aparelho, sem defeito | Hora UTC | Real |
|---|---|---|
| isolada | `20:05:19Z` | 2 de 2 |
| **na ordem da hipótese** | `20:05:39Z` | **1 falha em 5** |
| suíte cheia | `20:06:02Z` | 83, 0 falhas |

A falha, com a pilha:

```
com.platos.android.session.SessaoEmRepousoInstrumentedTest > aCredencialNaoEstaEmClaro[2511FPC34G - 16] FAILED
java.io.FileNotFoundException: /data/user/0/com.platos.android/shared_prefs/platos-sessao-cifrada.xml:
  open failed: ENOENT (No such file or directory)
  at java.io.FileInputStream.<init>(FileInputStream.java:179)
  at kotlin.io.FilesKt__FileReadWriteKt.readBytes(FileReadWrite.kt:73)
  at …SessaoEmRepousoInstrumentedTest.aCredencialNaoEstaEmClaro$lambda$1(SessaoEmRepousoInstrumentedTest.kt:83)
  at …SessaoEmRepousoInstrumentedTest.esperarAte(SessaoEmRepousoInstrumentedTest.kt:143)
  at …SessaoEmRepousoInstrumentedTest.aCredencialNaoEstaEmClaro(SessaoEmRepousoInstrumentedTest.kt:82)
```

**É a exceção de 19/09, agora com a linha.** A linha 83 é a guarda 1: `esperarAte { cifrado.exists() &&
!cifrado.readBytes().contentEquals(antesDaCredencial) }`. Entre o `exists()` e o `readBytes()`, o
`SharedPreferences` regrava o arquivo — renomeia o atual para `.bak` e escreve outro —, e a leitura
acha o arquivo ausente. É a **primeira** hipótese do Context do `design.md`, a da sondagem não
atômica. `ENOENT`, e não `EMFILE`: a hipótese dos descritores estava errada também no mecanismo.

**A frequência.** Dez repetições seguidas da mesma ordem no aparelho, `20:07:17Z`–`20:10:24Z`: **0
quedas**. No total, o cenário rodou **19** vezes no aparelho (12 na ordem da hipótese) e caiu **1**; no
emulador, **17** vezes, e **0**. A primeira contagem, dita ao mantenedor na pergunta que decidiu o
conserto, foi "1 em 11" — recontada execução por execução antes deste registro.

### O que a rodada 4 corrige nas anteriores

- **"Causa desconhecida"** (rodada 3) estava errada: a causa é a sondagem, e está medida.
- **"Não reproduz"** (rodadas 1 a 3) era verdade para aquelas execuções, e não para o cenário: a
  corrida é **rara**, e 1 em 19 explica por que dez execuções seguidas podem não vê-la.
- **O registro da ETAPA 5** — "caía de forma reprodutível na suíte cheia" (2 de 2) e "parou de cair
  por mudança de ordem" — lê uma corrida rara como propriedade de ordem. Duas quedas seguidas e depois
  três verdes são compatíveis com a janela de uma corrida, e a ordem foi refutada na rodada 1.
- **A causa está no teste**, e não no produto. `SessaoGuardadaAndroid` não precisa mudar.

**Decisão do mantenedor, `20:11Z`:** consertar no teste — as leituras de sondagem passam a tratar
"arquivo ausente no meio da regravação" como "ainda não", sem `exists()` antes; nenhuma asserção e
nenhuma espera mudam. O conserto é a tarefa 7.1.

## 5. O que esta medição **não** verificou (P8)

- **A corrida não foi forçada.** Ela aconteceu uma vez em 19 no aparelho, e nenhuma vez no emulador.
  Não há execução que a produza sob demanda, então o conserto não poderá ser visto "deixando de cair"
  por contagem: 0 em N depois dele não distingue consertado de sorte. Ele se prova pela pilha, que
  aponta a linha, e por construção.
- **Por que o aparelho e não o emulador.** Suposto: tempo de E/S diferente abre e fecha a janela
  entre o `rename` e a escrita. Não medido.
- **A árvore de 19/09 no aparelho, sob a mesma ordem da reprodução**: rodou só a suíte cheia, duas
  vezes, antes de a reprodução existir. Com uma corrida de 1 em 19, isso não diz nada sobre ela.

---

# Parte II — a mudança

Ainda não existe.
