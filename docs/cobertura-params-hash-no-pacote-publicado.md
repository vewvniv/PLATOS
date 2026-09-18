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

---

## 4. A asserção que substitui a renomeação

ADR-0014 decisão 4 recusou renomear `meta.exam_id`. `IdentidadeDaProvaTest` é o que entra no lugar:
afirma que `meta.exam_id` do pacote, o `id` da definição publicada e o `exam_short_id` que viaja no
QR de **cada** atribuição são um valor só.

Usa a fixture da **turma**, e não a de referência, porque é ela que tem atribuições — sem atribuição
não há QR de aluno, que é o terceiro dos três. O payload é lido por `QrPayload.read`, o **mesmo
leitor do aparelho**, e não por um `split` local: um leitor próprio aqui concordaria com um escritor
errado. E o laço exige `assignments` não vazio, senão passaria em silêncio.

**Visto falhar, e a primeira mutação não servia.** Mutar `Publish.kt` (`examId = id + "-MUTACAO"`)
**não alcança este teste**, porque ele lê a fixture e não o publish — foi revertida sem ser rodada, e
fica dita. A mutação certa faz os três divergirem **no artefato**: `meta.exam_id` da fixture da turma
trocado, com os QR intactos e válidos.

| Cenário | Caiu? |
|---|---|
| `o identificador do pacote e o da definicao publicada` | **sim** |
| `o identificador do pacote e o que viaja no QR de cada atribuicao` | **sim** |
| `os tres coincidem, e e essa a afirmacao inteira` | **sim** |
| `PacoteVersionadoTest` — o pacote da turma | **sim**, esperado: a fixture passa a divergir do publish |
| `PacoteVersionadoTest` — os outros três | **não** |

Os três de `PacoteVersionado` que ficaram de pé mostram que a mutação ficou **contida** na fixture da
turma. Reversão rodada: `sha256 e9ea8f3a56af…` restaurado, `:packages:domain:allTests`
**`BUILD SUCCESSFUL in 15s`**, `22:27:24Z–22:27:41Z`.

---

## 5. A camada (b), e como ela foi vista falhar

### 5.1 O cenário, e a guarda que o isola

`fixtures/pacote-do-contrato-anterior.json`, apresentado com o `content_hash` **dele**, é recusado — e
a asserção confere o **motivo**: `INTERPRETACAO`, e não `INTEGRIDADE`. As duas pedem coisas opostas
de quem segura o aparelho: a primeira pede tentar de novo, a segunda pede atualizar o aplicativo.

**A primeira guarda de vacuidade que escrevi era tautológica** — comparava uma computação com ela
mesma — e fica dita porque ela *parecia* uma asserção. Foi substituída por uma que afirma algo: os
**mesmos bytes**, apresentados com o hash da fixture **atual**, caem por `INTEGRIDADE`. O par prova
que o motivo é escolhido pelo **hash declarado**, e não pelo artefato.

Sem esse par, o cenário poderia estar medindo a camada vizinha e parecendo verde — o sombreamento de
fixture que `rigorous.md` §3 descreve.

### 5.2 A mutação, e a regra de parada disparando pela segunda vez

**Mutação:** neutralizar a comparação de reserialização em `verificarPacote` — e **não** a óbvia, que
seria tirar o campo de novo e ver os literais caírem: essa mede a aritmética do SHA-256.

`:apps:android:testDebugUnitTest`, **2026-09-18T22:29:45Z–22:29:58Z**, 303 cenários, **3 caídos**:

| Cenário | Previsto | Real |
|---|---|---|
| pacote do contrato anterior recusado por fidelidade | **sim** | **caiu** |
| os demais cenários de fidelidade já existentes | **sim** | **2 de 5** |
| cenários de integridade | **não** | **nenhum caiu** |
| cenários de identidade | **não** | **nenhum caiu** |

**A condição de parada explícita do plano foi cumprida:** *"se os de integridade caírem junto, a
mutação não isolou a camada — pare"*. Nenhum caiu. A mutação isolou.

**O desvio, e o que ele revela.** Caíram `campo com valor padrao omitido` e `ordem de campo trocada`.
Não caíram `campo desconhecido`, `bytes que nao sao json` e `json valido que nao e um pacote`.

`INTERPRETACAO` tem **dois produtores independentes** dentro de `verificarPacote`:

1. o `catch` à volta do `decodeFromString` — parse estrito;
2. a comparação de reserialização.

A mutação neutralizou **só o segundo**. Os três que sobreviveram estouram no parse e nem chegam à
comparação. A linha 2 da tabela tratava os cinco como um bloco, e são dois mecanismos sob um motivo
só.

**Isso refina a previsão em vez de contradizê-la, e é a favor do cenário novo.** A consequência que
ADR-0014 decisão 3 aceitou é exatamente a do **segundo** mecanismo: `encodeDefaults` injeta um campo,
o parse **não** estoura, e só a comparação vê. O cenário novo caiu com os dois que compartilham esse
mecanismo, e não com os três do parse — que é a evidência de que ele mede o que devia medir.

**Reversão rodada (P10):** árvore idêntica ao commit, `MUTACAO` em **0** fora de prosa,
**`BUILD SUCCESSFUL in 8s`**, `22:30:27Z–22:30:36Z`.

---

## 6. Os pacotes publicados antes desta mudança deixam de ser legíveis

**Esta seção existe porque a consequência é de produção, e não de teste.**

`exam_package` é imutável (ADR-0009): os pacotes publicados mantêm os bytes que têm. Um aplicativo
atualizado passa a **recusá-los** na camada (b), pelo mesmo mecanismo que o §5 mediu — `encodeDefaults
= true` injeta `"params_hash":null` que não estava nos bytes publicados, e reserializar deixa de
reproduzi-los.

**Quantas provas estão nessa condição: duas.** `prova-referencia-slice-1` e `prova-referencia-slice-2`,
as duas de conferência publicadas em produção — as mesmas que a conferência de ponta a ponta da
`slice-4b-outbox-de-resultado` usou, em 2026-09-18.

**O que fazer, e não só o que acontece.** O caminho é o que ADR-0009 já manda e não é novo:
**publicar prova nova, com `short_id` próprio.** Não se republica prova sobre si mesma — prova
publicada tem um pacote e um só, e o armazenamento recusa a alteração. As folhas impressas daquelas
duas provas deixam de ser escaneáveis por um aplicativo atualizado; se alguém precisar delas,
reimprime a partir da prova nova.

**Por que isto é aceitável agora e não seria depois.** Duas provas de conferência, nenhuma turma
real, nenhum resultado de aluno gravado contra elas. Depois da primeira turma haveria folhas em
circulação e `grading_result` apontando para pacotes que o aplicativo recusa — e o mesmo conserto
deixaria de ser um campo para virar migração de artefato imutável, que é a coisa que `exam_package`
foi desenhado para tornar impossível.

**A recusa é alta e não silenciosa**, e é essa a razão de ela ser aceitável: o aparelho diz que esta
versão não interpreta o pacote e pede atualização. A alternativa — aceitar o pacote antigo — seria
pior: um pacote cujo hash confere e cujo parse perdeu um campo produz nota plausível e errada, sem
sintoma na tela.

---

## 7. O que **não** foi verificado (P8)

- **Que os três cenários do parse estrito caem sob uma mutação do parse.** O §5.2 mostrou que
  `INTERPRETACAO` tem dois produtores e mediu um. O outro **não foi mutado**. Não é mitigado, é
  conhecido.
- **Que um aparelho real recusa um pacote do contrato anterior vindo da API.** O que foi medido é
  `verificarPacote` sobre os bytes, em JVM. O caminho completo — API entrega, aparelho puxa, recusa
  aparece na tela — não foi exercitado nesta mudança, e as duas provas em produção continuam
  publicadas sob o contrato antigo, o que torna essa conferência possível **e** não feita.
- **Que `params_hash` preenchido se comporta como declarado.** Não há geração por IA nesta base, e
  todos os pacotes o trazem nulo. O que a spec afirma sobre ausente-não-é-vazio e sobre parâmetros
  distinguíveis é contrato, e o consumidor dele é a fatia 6 — **política sem consumidor hoje, e isso
  é deliberado**: é o custo que I3 existe para evitar, pago uma vez.
- **A variante `release`.** Todas as medições são do `debug`.
- **Que nenhuma outra árvore do repositório depende do hash antigo.** A busca foi por texto
  (`26612ad5…`) em `.kt`, `.kts`, `.ts` e `.mjs`, e achou só o comentário que documenta o arquivo
  congelado. Uma dependência que calculasse o hash em vez de o literalizar não apareceria nessa
  busca.

---

## 8. A verificação final

| Comando | Desfecho | Janela (UTC) |
|---|---|---|
| `./gradlew build` | **`BUILD SUCCESSFUL in 23s`** | `22:34:39Z`–`22:35:02Z` |
| suíte instrumentada inteira, em `2511FPC34G` / Android 16 | **`OK (78 tests)`** | `22:35:32Z`–`22:35:50Z` |
| `openspec validate params-hash-no-pacote-publicado --strict` | válido | — |
| `MUTACAO` fora de prosa | **0** | — |

**Estado das duas fixtures que importam**, conferido depois de tudo:

```
277d2f8cd0a7…  fixtures/prova-referencia.package.json      ← contrato novo
26612ad52b0c…  fixtures/pacote-do-contrato-anterior.json   ← congelado, NÃO regerado
```

As duas mutações desta mudança foram revertidas e a reversão foi **rodada** (P10), não presumida.

**A regra de parada disparou duas vezes, e as duas ficaram escritas** — §1 e §5.2. Nas duas a
previsão foi refinada pelo real, e em nenhuma o instrumento foi consertado para caber na previsão.
