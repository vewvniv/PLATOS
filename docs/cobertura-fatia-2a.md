# Cobertura de cenários — fatia 2a (`ExamPackage` publicado)

Mapa de cada cenário de `openspec/changes/slice-2a-exam-package/specs/exam-package/spec.md` para a
verificação que o cobre, e — onde a verificação é crítica — **como ela foi vista falhar**. Gerado ao
fechar a tarefa 6.5.

Vale aqui o mesmo que na fatia 1: teste em `commonTest` roda nos três alvos (JVM, Node/JS e teste de
host Android), então o mesmo valor esperado é afirmado três vezes, em três runtimes. O que esta
fatia acrescenta é uma segunda camada de evidência, de natureza diferente: verificação **contra
Postgres real**, conectada como `app_backend` — papel sem `SUPERUSER` e sem `BYPASSRLS`. Guarda de
armazenamento afirmada por um papel privilegiado não afirma nada.

## `exam-package` — 13 cenários, 13 cobertos

| Cenário | Verificação |
|---|---|
| Republicar a mesma prova dá o mesmo hash | `ExamPackageTest.republicar a mesma prova da o mesmo hash` (3 alvos) + `ExamPackageTest.o hash da fixture e o mesmo nos tres alvos`, com o literal `61c96f4c…` |
| Mudança no conteúdo muda o hash | `ExamPackageTest.mudanca no conteudo muda o hash` — dois ângulos, enunciado e **gabarito**; sem o segundo, a correção poderia ser adulterada sem rastro no hash |
| Pacote publicado não pode ser alterado | `ExamPackageImmutabilityTest`: `UPDATE` e `DELETE` como `app_backend` (recusa `42501`) e como **superusuário** (recusa `PT001`, do gatilho), mais `apagar a prova e recusado enquanto houver pacote publicado` (`23503`) |
| O pacote não carrega nome de aluno | `ExamPackageTest.o pacote nao carrega nome de aluno` (varredura do JSON, 3 alvos) + `ExamPublicationTest.o pacote gravado nao traz nome, turma nem matricula` (varredura do JSON **gravado**) |
| Mudar o roster não invalida o pacote | `ExamPublicationTest.corrigir o roster nao muda o hash do pacote` |
| Eliminação de dado pessoal não destrói a prova | `ExamPackageImmutabilityTest.apagar o roster deixa o pacote integro e com o mesmo hash` |
| Perfil declarado no pacote | `LayoutProfileTest.o mapa declara o perfil que o produziu` (3 alvos) |
| Perfis diferentes são distinguíveis | `LayoutProfileTest.perfis diferentes ficam distinguiveis pelo cabecalho`, `…o identificador sozinho nao bastaria` e `ExamPackageTest.perfis diferentes dao pacotes diferentes` |
| Variante aponta item inexistente | `ExamPackageTest.variante apontando item inexistente e recusada` |
| Item sem gabarito | `ExamPackageTest.item sem gabarito impede a publicacao` + `ExamPublicationTest.prova incoerente nao grava linha nenhuma` |
| Layout diverge dos itens | `ExamPackageTest.layout divergente dos itens e recusado` |
| PDF vem do pacote | `PacoteVersionadoTest` (3 alvos) + `render-fixture.ts` e `LayoutMapRendererInstrumentedTest.layoutDoPacote()` lendo o pacote versionado |
| Divergência entre plataformas continua barrada | `tools/parity/compare.mjs` sobre os dois documentos derivados do pacote: 185 de 185, 0,042 mm |

O caso positivo anda junto de cada recusa (`ExamPackageTest.o pacote da fixture e coerente`), sem o
qual as recusas poderiam estar recusando tudo.

## Como cada verificação crítica foi vista falhar

Crítico aqui é o que falha em silêncio e chega à folha impressa, ao OMR ou ao artefato imutável.
Cada linha abaixo foi produzida introduzindo o defeito de propósito, observando o vermelho e
revertendo.

| Defeito introduzido | Quem acusou | Mensagem |
|---|---|---|
| `exam_package` nascendo sem `enable row level security` | `ConnectionRoleTest.toda tabela de public tem RLS habilitada e forcada` | `expected: <[]> but was: <[exam_package sem RLS habilitada]>` |
| Gatilho normalizando `content` via `jsonb`, mantendo a coluna `text` | `ExamPackageImmutabilityTest.o conteudo volta byte a byte, e o hash confere` (e mais dois que comparam conteúdo; os quatro de imutabilidade seguiram verdes) | `a serializacao canonica nao sobreviveu ao armazenamento` |
| `PackageAssignment(displayName)` no lugar de `studentToken` | `ExamPublicationTest.o pacote gravado nao traz nome, turma nem matricula` | ``nome `Zoraide Buarque` dentro do pacote`` |
| Um único espaço a mais no conteúdo gravado — JSON ainda válido | `ExamPublicationTest.publicar a prova versionada grava o pacote com o hash…` | hash recalculado dos bytes da coluna ≠ hash gravado |
| `unique (short_id)` removido de `exam` | `ExamPublicationTest.republicar a mesma prova e recusado com erro identificavel` | a segunda publicação passou, e devolveu o **mesmo** `content_hash` em outra prova |
| Deslocamento deliberado de 0,5 mm no layout do pacote | `compare.mjs` e `fidelidade.mjs` | as duas saíram com código 1, acusando os quatro elementos |

### Duas armadilhas que apareceram ao verificar, e que valem mais que os resultados

**A primeira tentativa de derrubar a guarda de bytes não valia.** Trocar a coluna `content` de `text`
para `jsonb` derruba os sete testes do arquivo — com erro de tipo no `INSERT`. Isso é barulho, e não
o defeito que a coluna existe para evitar: o perigo real é a normalização **silenciosa**, em que
nada quebra ao gravar e só os bytes mudam. Trocado o defeito por um gatilho que normaliza mantendo a
coluna `text`, exatamente os três testes que comparam conteúdo ficaram vermelhos — e nenhum outro.

**O oráculo óbvio de "nada mudou" não existia.** Ao mover os renderizadores para o pacote, a
intenção era comparar o `web.pdf` antes e depois byte a byte. Ele mudou — e mudou também entre duas
execuções seguidas **com a mesma entrada**: dois renders consecutivos deram hashes diferentes, com a
diferença dentro de um stream comprimido, na forma de um timestamp embutido pelo `pdf-lib`. O hash
do PDF nunca serviu, e nunca servirá, de golden. Comparando uma vez só, "o PDF mudou" teria sido
lido como regressão de geometria. Quem responde a essa pergunta é a rasterização — fidelidade e
paridade — e o `git diff` dos renderizadores.

## Números da fatia

| Grandeza | Valor |
|---|---|
| `packages/domain` JVM · Node · Android host | 197 · 192 · 192, zero falhas |
| `apps/api` (Postgres real) | 89, zero falhas — eram 69 no início da fatia |
| Paridade web × Android | 185 de 185, maior divergência **0,042 mm** em `qq37-f` |
| Fidelidade web · Android | 116 verificações cada; **0,039 mm** e **0,022 mm** |
| `fixtures/prova-referencia.layout.json` | sha256 `ceaf67cc…` |
| `fixtures/prova-referencia.package.json` | sha256 `61c96f4c…` — que **é** o `content_hash` do pacote |
