# Cobertura — `params-hash-no-pacote-publicado`

**ETAPA 3** de `docs/plano-de-correcao-antes-da-fatia-5.md` · **Achados 4.1 e 5.4** · **ADR-0014**
**Sessão:** 2026-09-18 · **Base:** o ramo da ETAPA 2

O que este documento é: **como cada verificação crítica foi vista falhar**, e não que ela passa
(`rigorous.md` §8). E aqui há uma razão a mais: esta mudança quebra o `content_hash` de todo pacote,
e "passou" sobre artefato hasheado é a afirmação mais barata que existe.

**O que esta mudança afirma.** Que I3 passou a estar inteira no artefato imutável; que a quebra de
hash aconteceu **uma vez** e alcançou exatamente três fixtures e nenhuma geometria; e que a
consequência aceita por ADR-0014 decisão 3 — pacotes do contrato anterior recusados — é **real e
alta**. **O que ela não afirma** está em §6.

---

## 1. A previsão que errou, e o que o erro ensinou

A tarefa 1.2 previa **2** asserções caindo no commit do contrato: os dois literais de hash. Caíram
**14**. A regra de parada do plano (§0.5) disparou, e o conjunto real ficou escrito ao lado do
previsto em vez de a previsão ser ajustada em silêncio.

`./gradlew build --continue`, **2026-09-18T21:13:54Z–21:15:21Z**:

| Suíte | Cenários distintos | Previsto? |
|---|---|---|
| `ExamPublicationTest` (api) — `hashDaFixture` | 1 | **sim** |
| `ExamPackageTest` — `HASH_DA_FIXTURE` | 1 | **sim** |
| `PacoteVersionadoTest` | 2 | não |
| `ConferenciaDePacoteTest` | 2 | não |
| `ObtencaoDePacoteTest` | 3 | não |
| `PacotesEmArquivoTest` | 5 | não |

*(os três alvos de `packages:domain` repetem os mesmos 3 cenários; 14 são os distintos.)*

**As 12 não previstas têm uma causa só**, e ela é a mesma em todas:

```
Recusado(motivo=INTERPRETACAO,
         detalhe=esta versao do aplicativo nao interpreta o pacote por inteiro:
                 reserializa-lo nao reproduz os bytes conferidos)
```

A fixture versionada ainda era do contrato antigo, o código já lia o novo, e a **camada (b)** a
recusou. `ObtencaoDePacote` e `PacotesEmArquivo` caíram em **cascata**, por guardarem e lerem pacote
através de `verificarPacote` — uma delas com `FileNotFoundException` num caminho que continha o hash
antigo.

**O que isso significa, e não é "a previsão foi só curta".** É a consequência da decisão 3 do
ADR-0014 acontecendo **dentro da suíte**, antes de existir o cenário deliberado do §4 — e é a favor
da mudança, não contra: a recusa que o ADR aceitou é real, alta e reprodutível, e apareceu sem ter
sido construída. O erro da previsão foi **de categoria**: contei literais de hash quando o que
depende da fixture é a cadeia inteira de manuseio de pacote no aparelho.

As 12 voltaram ao verde quando a fixture foi regravada. É por isso que o congelamento do §2 é o que
mantém a consequência exercitável.

---

## 2. A quebra de hash, e a guarda de vacuidade que a limita

**Regravado pelo caminho que já existe e por nenhum outro**, em
**2026-09-18T22:05:36Z**:

```
./gradlew :packages:domain:jvmTest -Dplatos.golden.write=true
```

O comando reescreve **cinco** artefatos. **Exatamente três mudaram**, e nenhum é geometria (P13):

```
 fixtures/prova-2.package.json                | 2 +-
 fixtures/prova-referencia.package.json       | 2 +-
 fixtures/prova-referencia.turma.package.json | 2 +-
 3 files changed, 3 insertions(+), 3 deletions(-)
```

`prova-referencia.layout.json` e `folha-de-teste.layout.json` foram reescritos com o **mesmo
conteúdo** — `params_hash` está no `ExamPackage`, não no `LayoutMap`.

**A conferência não parou no `git diff`.** Comparação estrutural dos três documentos, campo a campo:

| Fixture | Campos novos | Campos sumidos | Bytes | `sha256` |
|---|---|---|---|---|
| `prova-referencia.package.json` | `/meta/params_hash` | nenhum | 101.618 → 101.637 | `26612ad52b0c…` → `277d2f8cd0a7…` |
| `prova-2.package.json` | `/meta/params_hash` | nenhum | 101.635 → 101.654 | `9dccf215652e…` → `9eb8e893b9e2…` |
| `prova-referencia.turma.package.json` | `/meta/params_hash` | nenhum | 104.749 → 104.768 | `3f76502a68f3…` → `e9ea8f3a56af…` |

**+19 bytes em cada**, que é exatamente o tamanho de `,"params_hash":null`. O `git diff` sozinho diria
"uma linha mudou" — o JSON canônico é uma linha só —, e não distinguiria um campo acrescentado de um
documento reescrito. A comparação estrutural é que faz a guarda valer.

**Os dois literais** acompanharam: `HASH_DA_FIXTURE` e `hashDaFixture`.
**`ApiPlatosPacoteTest.HASH` não foi tocado** — é hash sintético de `MockEngine`, e não da fixture.
O literal antigo sobrevive em **um** lugar, de propósito: o comentário que documenta o hash do
arquivo congelado.

`./gradlew build --continue`: **`BUILD SUCCESSFUL in 48s`**, **22:06:46Z–22:07:35Z**. As 14 do §1
voltaram ao verde.

---

## 3. P23 — paridade e fidelidade da mesma sessão, com os dois lados gerados nela

**Por que era obrigatório, e não "só mudou o pacote":** `apps/web/scripts/examPackage.ts` lê
`fixtures/prova-referencia.package.json` e alimenta `render-fixture.ts`, que produz o PDF do lado
web. **A fixture do pacote está no caminho da paridade.**

### 3.1 A armadilha que estava montada, e que não foi pisada

`build/parity/` continha `android.pdf` e `android-teste.pdf` de **uma sessão anterior**, de antes da
mudança da fixture. Rodar `compare.mjs` naquele estado teria comparado um `web.pdf` novo contra um
`android.pdf` que não conhece `params_hash` — **e teria passado**. É o precedente que a 4a registrou,
e a única coisa que o impede é conferir o `timestamp` de cada artefato antes de comparar, e não
depois.

O aparelho estava desconectado quando isso foi notado. **A sessão parou** até ele voltar, em vez de
fechar P23 com metade dos artefatos.

### 3.2 Os quatro artefatos, cada um com a sua hora

| Artefato | Lado | Bytes | Gerado em (UTC) |
|---|---|---|---|
| `web.pdf` | web | 229.786 | **2026-09-18T22:08:36Z** |
| `teste-web.pdf` | web | 122.582 | **2026-09-18T22:09:10Z** |
| `android.pdf` | Android | 145.899 | **2026-09-18T22:22:19Z** |
| `android-teste.pdf` | Android | 21.648 | **2026-09-18T22:22:19Z** |

Os dois do Android saíram de `am instrument` sobre a suíte instrumentada inteira — **`OK (78
tests)`**, 22:21:24Z–22:21:42Z, em `2511FPC34G` / Android 16 — e foram puxados com
`adb exec-out … cat`, com os tamanhos conferidos **contra os do aparelho** (21.648 e 145.899, iguais)
e o cabeçalho `%PDF-1.4` verificado.

*Duas tentativas anteriores de puxá-los produziram arquivos corrompidos, e ficam ditas: o `>` do
PowerShell é redirecionamento de texto e inflou os PDFs para 258.256 e 37.543 bytes; e o Bash do MSYS
converteu o caminho remoto, gravando a mensagem de erro `cat: C:/…` dentro do arquivo. Nos dois
casos o defeito só apareceu porque o tamanho foi conferido contra a origem — um PDF corrompido de
258 KB passaria por "PDF gerado" numa listagem.*

### 3.3 Os três passos

| Passo | Alvo | Desfecho |
|---|---|---|
| `fidelidade.mjs` | `web.pdf` | **OK** — 116 verificações, maior desvio **0,046 mm** (tolerância 0,05) |
| `fidelidade.mjs` | `teste-web.pdf` | **OK** — 31 verificações, maior desvio **0,046 mm** |
| `fidelidade.mjs` | `android.pdf` | **OK** — 116 verificações, maior desvio **0,042 mm** |
| `tinta.mjs` | `web.pdf` | **OK** — 160 bolhas, maior cobertura 7,24% (corredor começa em 20%); trama 4,71% sob teto 8,39%; monocromia limpa |
| `tinta.mjs` | `teste-web.pdf` | **OK** — 5 bolhas, maior 2,87%; trama 8,24% sob teto 8,39% |
| `compare.mjs` | `web.pdf` × `android.pdf` | **OK** — 185 de 185 elementos, maior divergência **0,048 mm** (tolerância 0,3; folga 0,252) |
| `compare.mjs` | `teste-web.pdf` × `android-teste.pdf` | **OK** — 9 de 9, maior divergência **0,044 mm** |

**Nenhum número encostou em limite**, e nenhuma tolerância foi tocada (regra 0.6 do plano).
