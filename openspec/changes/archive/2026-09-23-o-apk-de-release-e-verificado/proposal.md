## Why

É a ETAPA **7.2** do `docs/plano-de-correcao-antes-da-fatia-5.md`: cinco guardas que podem parar de
verificar o que afirmam sem nada acusar. Todas têm a mesma fatia-limite, o **lançamento**, e nenhuma
bloqueia a fatia 5. Mas a ETAPA 8 exige a 7 arquivada, então elas correm antes da 5.

| # | O que está solto hoje | De onde |
|---|---|---|
| 1 | `verificarApkSemPacote` — a guarda que o ADR-0013, decisão 5, chama de "a falha mais provável, e a mais quieta" — roda **só sobre o debug** (`build.gradle.kts:365,368`). O APK que vai ao professor não é conferido por ela | auditoria **3.1** |
| 2 | `testReleaseUnitTest` **não existe** no grafo: o AGP 9 não cria a variante, e esta árvore nunca optou por criá-la. O gatilho registrado ("a próxima que mexer em build ou variante") disparou duas vezes sem ninguém atender | auditoria **3.1**; `cobertura-fatia-4a-cache-referencia.md:220` |
| 3 | `cancel-in-progress: true` no nível do workflow alcança a `paridade`, que §16 chama de o maior risco do projeto se não existir, e que P15 registra derrubada assim na PR #30 | auditoria **5.3** |
| 4 | `SessaoEmRepousoInstrumentedTest.aCredencialNaoEstaEmClaro` — uma **afirmação de segurança** — caía de forma reprodutível na suíte cheia e passava isolada; parou de cair por mudança de ordem, e não por conserto | `cobertura-o-pendente-nao-se-perde-no-aparelho.md` §1 e §6; linha do §16 |
| 5 | Dois `@Test` de `ApiPlatosPacoteTest` devolvem valor, e o JUnit Jupiter não descobre teste que devolve valor: **nunca rodaram**, desde `593bf9a`, e nada acusou | `cobertura-o-fio-preso-nos-dois-lados.md` §6 |

**O item 2 foi decidido pelo mantenedor em 2026-09-23, nesta proposta: a variante liga.** Medido antes
de perguntar, sem tocar o repositório (um *init script* com `beforeVariants`): `testReleaseUnitTest`
passa a existir e fica verde, **308 de 308**, `18:58:38Z`–`18:59:42Z`, em cerca de um minuto a mais que
o debug. Hoje release e debug compilam o mesmo código — não há `src/release` nem ramo em
`BuildConfig.DEBUG` —, então o ganho imediato é pequeno; o que a decisão elimina é o gatilho que
depende de alguém lembrar, e que já falhou duas vezes.

## What Changes

- **A guarda de pacote embutido passa a cobrir o release**, com guarda de vacuidade **por variante**: sem
  APK de release, ela reprova, em vez de passar sobre o debug sozinho.
- **`testReleaseUnitTest` passa a existir e a rodar no `build`**, por `beforeVariants` no
  `build.gradle.kts` do aplicativo.
- **A `concurrency` sai do nível do workflow e passa a ser declarada por job**: `cancel-in-progress:
  true` para `build` e `web`, `false` para `paridade`, cada job no seu próprio grupo.
- **O cenário da credencial passa a ter o mesmo desfecho isolado e na suíte cheia.** A causa é medida
  primeiro, no emulador; o conserto vai onde a medição apontar.
- **Os dois testes que nunca rodaram passam a rodar**, e cada um é visto falhar pela primeira vez.
- **Uma guarda que reprova quando um `@Test` declarado não tem resultado no relatório**, nas suítes JVM
  dos três módulos, nomeando a classe e o método. Ela lê a declaração do **bytecode compilado**, e não
  do texto do fonte — o que resolve o `@Test` escrito com o nome qualificado, que passou despercebido
  pela contagem de texto da 7.3.
- **Nenhum comportamento do produto muda.** Nenhuma tela, rota, contrato, fixture, golden ou hash.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

Nenhuma. Os dois requisitos que estas guardas afirmam continuam como estão: "Nenhum pacote embutido no
aplicativo" (`openspec/specs/scan-session/spec.md`, requisito "A sessão só existe sobre pacote puxado e
conferido") e "A credencial não está em claro" (`openspec/specs/device-session/spec.md`, requisito "A
credencial guardada não fica legível no aparelho"). O que muda é **o que os verifica**, e sobre qual
artefato. Como a 7.1 e a 7.3, a mudança declara `skip_specs: true`; a tabela do §2 do plano diz o mesmo.

## Impact

- **`apps/android/build.gradle.kts`** — a guarda do APK sobre as duas variantes; o `beforeVariants` da
  variante de teste do release; o registro da guarda de testes executados.
- **`buildSrc/src/main/kotlin/com/platos/build/`** — a tarefa da guarda de testes executados, uma só
  para os três módulos (regra 7 do `CLAUDE.md`: nada de regra duplicada entre apps).
- **`apps/api/build.gradle.kts`** e **`packages/domain/build.gradle.kts`** — o registro dessa tarefa.
- **`.github/workflows/ci.yml`** — a `concurrency` por job.
- **`apps/android/src/test/.../api/ApiPlatosPacoteTest.kt`** — os dois testes passam a devolver `Unit`.
- **`apps/android/src/androidTest/...`** — onde a medição do item 4 apontar: o próprio
  `SessaoEmRepousoInstrumentedTest`, ou a classe que interfere nele.
- **`docs/cobertura-o-apk-de-release-e-verificado.md`** — novo. **`docs/architecture/ARQUITETURA-FINAL-v3.md`
  §16** — a linha da credencial fechada ao lado do registro (P7), e o registro de que a 7.2 não tinha
  linha própria lá.
- **Ambiente:** o mantenedor autorizou, em 2026-09-23, **emulador e Docker** para esta etapa. Nenhum
  aparelho físico.

### O que NÃO será alterado

- **`minifyEnabled`, regras de R8, assinatura do release, `versionCode`, publicação de loja.** Trabalho
  de lançamento, e não de correção de auditoria (plano, "Proibido nesta etapa").
- **O código de produção da sessão guardada** (`SessaoGuardadaAndroid`), salvo se a medição do item 4
  mostrar defeito do produto — e então isso **para** a mudança e vira decisão com o mantenedor, porque
  deixaria de ser correção de teste.
- **As afirmações dos testes.** Nenhuma asserção de `aCredencialNaoEstaEmClaro` é afrouxada, e nenhuma
  espera ganha folga para "passar" (P11, P12).
- **A versão do JUnit.** Se uma versão mais nova passa a acusar método de teste que devolve valor não
  foi conferido aqui, e nem precisa: trocar dependência do catálogo é mudança de ambiente (P22), e o
  que o plano pede é outra propriedade — a suíte executar **menos** do que o fonte declara, qualquer que
  seja a causa, e não só esta.
- **`jsNodeTest`, `androidTest` e `buildSrc`** ficam fora da guarda de testes executados, com a razão no
  `design.md`.
- **O `renderizador.mjs`, o `fio.mjs` e os demais conferidores**, e qualquer requisito de spec.
