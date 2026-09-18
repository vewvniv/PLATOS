## 0. O ADR, antes de qualquer linha de código

A ordem é a da ETAPA 3: as quatro decisões precisam estar escritas **antes de o primeiro commit
existir**. O commit 1 já deixa o build vermelho, e vermelho sem decisão registrada é ambíguo.

- [x] 0.1 Escrever `docs/adr/0014-a-tripla-de-proveniencia-no-artefato-imutavel.md` com as quatro
      decisões de `design.md` — §2 vence §5; a semântica dos três campos; a consequência aceita; e
      `meta.exam_id` não renomeado. Verificar por leitura cruzada: cada decisão com o arquivo e a
      linha que a sustentam, e `Status: aceito` com data.
- [x] 0.2 Corrigir a lista de `meta` do `ARQUITETURA-FINAL-v3.md` §5, acrescentando `params_hash`,
      **com a contradição dita e não apagada** (P7): a lista estava incompleta e I3 sempre exigiu os
      três. Verificar que o texto antigo continua legível ao lado da correção, e que o ponteiro para
      ADR-0014 está na linha.

## 1. Commit 1 — contrato, e só ele

- [x] 1.1 `PackageMeta` ganha `@SerialName("params_hash") val paramsHash: String? = null`, ao lado
      dos outros dois. **Nenhum consumidor muda.** A KDoc perde a frase "não há o que retrofitar" e
      passa a dizer o que os três campos são e quando são nulos. Verificar com
      `./gradlew :packages:domain:compileKotlinJvm` — compila.
- [x] 1.2 **O build fica vermelho neste commit, e isso é esperado.** Rodar
      `./gradlew build` e registrar **quais** asserções caem: a previsão é `HASH_DA_FIXTURE` em
      `ExamPackageTest` e `hashDaFixture` em `ExamPublicationTest`, e **nada além**. Se cair mais
      alguma coisa, **pare** — algo além do hash depende do formato de `meta` (regra de parada,
      `design.md` decisão 10). O vermelho esperado vai na mensagem do commit.

      **PREVISTO 2 · REAL 14. A regra de parada disparou**, e o conjunto real fica escrito ao lado
      do previsto (P7, P12, P14). `./gradlew build --continue`, 2026-09-18T21:13:54Z–21:15:21Z:

      | Suíte | Cenários | Previsto? |
      |---|---|---|
      | `ExamPublicationTest` (api) — `hashDaFixture` | 1 | **sim** |
      | `ExamPackageTest` — `HASH_DA_FIXTURE` | 1 | **sim** |
      | `PacoteVersionadoTest` | 2 | não |
      | `ConferenciaDePacoteTest` | 2 | não |
      | `ObtencaoDePacoteTest` | 3 | não |
      | `PacotesEmArquivoTest` | 5 | não |

      (os três alvos de `packages:domain` repetem os mesmos 3 cenários; 14 são os **distintos**.)

      **O que o conjunto real significa, e não é "a previsão foi só curta".** As 12 não previstas têm
      **uma** causa, e é a mesma: a fixture versionada ainda é do contrato **antigo**, o código já lê
      o **novo**, e a camada (b) a recusa —
      `Recusado(motivo=INTERPRETACAO, detalhe=... reserializa-lo nao reproduz os bytes conferidos)`.
      `ObtencaoDePacote` e `PacotesEmArquivo` caem **em cascata**, porque guardam e leem pacote
      através de `verificarPacote`.

      **Isto é a consequência da decisão 3 do ADR-0014 acontecendo dentro da suíte**, antes de
      existir o cenário deliberado da tarefa 5.1 — e é a favor da mudança, não contra: a recusa
      prevista pelo ADR é real, alta e reprodutível. O erro da previsão foi contar **literais de
      hash** quando o que depende da fixture é a **cadeia inteira de manuseio de pacote no aparelho**.

      As 12 voltam ao verde na tarefa 2, quando a fixture for regravada. É por isso que o
      congelamento da 2.1 é o que mantém a consequência exercitável — sem ele, depois da regravação
      não sobra nenhum pacote do contrato antigo nesta árvore.

## 2. Commit 2 — as fixtures, regravadas pelo caminho que já existe

- [x] 2.1 **Antes de regravar**, congelar `fixtures/pacote-do-contrato-anterior.json`: cópia byte a
      byte de `fixtures/prova-referencia.package.json` **como está hoje**, com KDoc ou cabeçalho
      dizendo que é artefato do contrato antigo, deliberadamente **não** regerado, e que o
      `GoldenWriterTest` não o escreve. Verificar por `sha256` que a cópia é idêntica ao original
      antes da regravação, e que o hash bate com o `HASH_DA_FIXTURE` **anterior**.
- [x] 2.2 Regravar pelo caminho que já existe, e por nenhum outro:
      `./gradlew :packages:domain:jvmTest -Dplatos.golden.write=true`.
- [x] 2.3 **A guarda de vacuidade desta etapa (P13).** O comando regrava cinco artefatos.
      **Exatamente três devem mudar**: `fixtures/prova-referencia.package.json`,
      `fixtures/prova-2.package.json` e `fixtures/prova-referencia.turma.package.json`.
      `prova-referencia.layout.json` e `folha-de-teste.layout.json` **não podem mudar** —
      `params_hash` está no `ExamPackage`, não no `LayoutMap`. Conferir no `git diff` e colar a saída
      na cobertura. **Se um arquivo de layout mudou, pare:** alguma coisa alcançou a geometria, e a
      etapa mudou de tamanho.
- [x] 2.4 No mesmo commit, atualizar os dois literais de hash:
      `packages/domain/src/commonTest/.../ExamPackageTest.kt:21` (`HASH_DA_FIXTURE`) e
      `apps/api/src/test/.../ExamPublicationTest.kt:32` (`hashDaFixture`) — cujo próprio comentário
      já manda "Regravar junto com a fixture". **`ApiPlatosPacoteTest.HASH` não é da fixture** — é
      hash sintético de `MockEngine`, e não se toca nele. Verificar com `./gradlew build` verde.

## 3. Commit 3 — P23, e ele é obrigatório

Não é opcional porque "só mudou o pacote": `apps/web/scripts/examPackage.ts` lê
`fixtures/prova-referencia.package.json` e alimenta `render-fixture.ts`, que produz
`build/parity/web.pdf` — **a fixture do pacote está no caminho da paridade**. O precedente é a 4a,
que fechou paridade ao mexer no `PLATOS_PACKAGE` sem regravar golden nenhum.

- [x] 3.1 Rodar os passos de `fidelidade.mjs`, `compare.mjs` e `tinta.mjs` do `ci.yml`, com os PDFs
      **gerados nesta sessão dos dois lados**. Verificar que cada um passa e registrar o `timestamp`
      de **cada artefato** (P23, P3).
- [x] 3.2 Registrar na cobertura os `timestamp` dos PDFs dos dois lados e o desfecho de cada um dos
      três passos. Verificar que nenhum artefato citado é de sessão anterior — artefato herdado não
      fecha P23.

## 4. Commit 4 — a asserção que substitui a renomeação

- [ ] 4.1 Um teste no domínio que afirma, sobre a fixture publicada, que `meta.examId` é o mesmo
      valor que o `short_id` da definição **e** o mesmo que o campo de prova dentro do payload do QR
      de cada atribuição. Hoje isso é verdade por construção dentro de `ExamPublication.publish`; o
      teste transforma "verdade por construção" em "verdade afirmada". É o que a KDoc de
      `EXTRA_SHORT_ID` em `ScanActivity` já pedia sem ter. Verificar com
      `./gradlew :packages:domain:jvmTest` — verde.

## 5. O cenário que isola a camada (b), e a mutação que o prova

**Ambiente: emulador** para `connectedDebugAndroidTest` — **P22 vale, perguntar antes.**

- [ ] 5.1 Cenário novo em `ConferenciaDePacoteTest`: o pacote do contrato anterior, apresentado com o
      `content_hash` **dele** (o de antes), é **recusado**, e a asserção confere **o motivo** —
      recusa por fidelidade de interpretação (`INTERPRETACAO`), e não por integridade
      (`rigorous.md` §3: "a asserção SHALL conferir o motivo da recusa, e não só que houve recusa").
- [ ] 5.2 **A guarda de vacuidade que isola a camada:** no mesmo cenário, afirmar que aquele mesmo
      pacote **passa** na camada (a) — `sha256(bytes) == content_hash` declarado. Sem isso a recusa
      poderia ser por integridade, e o cenário estaria medindo a camada vizinha. É literalmente o
      sombreamento de fixture que o `rigorous.md` §3 descreve, e que já aconteceu duas vezes nesta
      base.
- [ ] 5.3 **Ver falhar.** A mutação é **neutralizar a camada (b)** em `ConferenciaDePacote` — e não
      a óbvia, que seria tirar o campo de novo e ver os literais caírem: essa mede a aritmética do
      SHA-256 e não prova nada interessante. Rodar a suíte sob a mutação e registrar **quais**
      cenários caem, contra esta tabela:

      | Cenário | Deve cair? |
      |---|---|
      | pacote do contrato anterior é recusado por fidelidade | **sim** |
      | os demais cenários de fidelidade já existentes | **sim** |
      | cenários de integridade (hash divergente, conteúdo truncado) | **não** |
      | cenários de identidade (prova errada) | **não** |

      **Se os de integridade caírem junto, a mutação não isolou a camada — pare** (regra 0.5 do
      plano; `design.md` decisão 10). Conjunto real diferente do previsto, em qualquer direção, é
      motivo de parada e de registro — não de conserto do instrumento.
- [ ] 5.4 Verificar que a mutação **entrou** antes de ler o resultado, e reverter rodando (P10):
      `grep -rn "MUTACAO"` fora de `build/` em `0` e a suíte rodada **depois** da reversão, com
      `timestamp`.

## 6. O registro

- [ ] 6.1 `docs/cobertura-params-hash-no-pacote-publicado.md` com, no mínimo: o conjunto que caiu sob
      a mutação da camada (b) ao lado do previsto; o `git diff` das fixtures mostrando **três**
      arquivos e nenhum layout; os `timestamp` dos PDFs de paridade e fidelidade daquela sessão; e o
      conjunto que caiu no commit 1 ao lado do previsto.
- [ ] 6.2 Uma seção própria da cobertura nomeando que **os pacotes publicados antes desta mudança
      deixam de ser legíveis pelo aplicativo atualizado**, com o caminho de ADR-0009 ao lado, e
      dizendo quantas provas estão nessa condição em produção. Verificar que a seção diz o que fazer,
      e não só o que acontece.
- [ ] 6.3 No `docs/auditoria-2026-09-18-antes-da-fatia-5.md`, os achados **4.1** e **5.4** deixam de
      estar abertos, sem apagar o texto antigo (P7): o 4.1 fechado pelo campo, o 5.4 fechado pela
      asserção **e não pela renomeação**, com a razão da recusa apontando para ADR-0014.
- [ ] 6.4 Verificação final: `./gradlew build` com `timestamp`, a suíte instrumentada verde depois da
      reversão, `openspec validate params-hash-no-pacote-publicado --strict`, e a seção "o que ainda
      não foi verificado" honesta.
