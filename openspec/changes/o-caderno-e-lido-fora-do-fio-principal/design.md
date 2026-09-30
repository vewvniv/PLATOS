## Context

O motivo está em `proposal.md` (Why). Aqui fica o estado atual que molda o desenho, com o tipo de cada afirmação (P6 do `rigorous.md`): **medido**, **lido** ou **suposto**.

- **A leitura do caderno estoura na thread principal.** `LerCadernoNoFioPrincipalInstrumentedTest` chama `CadernosEmRoom(CadernosEmRoom.abrir(context).cadernos()).ler(...)` dentro de `runOnMainSync`, e a captura da exceção devolve `IllegalStateException: Cannot access database on the main thread…`, com base vazia e com caderno guardado. *Medido em 2026-09-30, emulador `platos-atd34` (Android 14) às 20:05:28 e aparelho `2511FPC34G` (Android 16) às 20:05:37, 2 de 2 falhas em cada.*
- **`ScanActivity.onCreate` faz essa mesma chamada, na thread principal**, dentro do construtor da sessão (`cadernoInicial = cadernos.ler(organizacao, examPackage.meta.examId)`, `ScanActivity.kt:138`). *Lido.* **Suposto** (não medido, porque nenhum teste lança a Activity): que o `onCreate` cai com a mesma exceção. É o que a tarefa 1.1 mede.
- **A escrita já está certa.** `guardarCadernoEmAndamento` roda em `Dispatchers.IO + NonCancellable`, é função de `CoroutineScope` e tem teste pelo fio principal. *Lido.*
- **O restante do `onCreate` é E/S de arquivo ou nada.** `PacotesEmArquivo.ler` e `RostersEmArquivo.ler` leem arquivo; `ResultadosEmRoom.abrir` e `CadernosEmRoom.abrir` só constroem o banco, sem consulta (o Room abre o arquivo na primeira consulta). Arquivo na thread principal é feio e não é o defeito; o Room é o único que a própria biblioteca recusa. *Lido.* **Suposto**, e a medição acima não o separa: o `runCatching` do teste envolve `abrir` **e** `ler`, e a pilha da exceção não foi guardada nos relatórios. A leitura do Room diz que a consulta é quem recusa; a tarefa 1.1 registra a pilha e confirma que a exceção nasce em `ler`.
- **O projeto recusou `allowMainThreadQueries` por escrito** duas vezes: "a ausência é o requisito" (`OutboxEmRepousoInstrumentedTest`, `CadernoEmRepousoInstrumentedTest`). *Lido.*
- **Nenhum teste lança a `ScanActivity`.** Quatro arquivos de `androidTest` a citam, e todos só usam funções dela (`gravar`, `decidirAbertura`). *Lido por `grep`.* `ActivityScenario` **não** está nas dependências de teste (só `androidx.test.runner` e `androidx.test.ext:junit`); `Instrumentation.startActivitySync` não exige dependência. *Lido em `build.gradle.kts`.*

## Goals / Non-Goals

**Goals:**
- Abrir o escaneamento com caderno guardado retoma o caderno, sem consulta ao Room na thread principal.
- A correção é provada pelo caminho de produção: a Activity lançada de verdade, vista falhar antes e passar depois, nos dois aparelhos.

**Non-Goals:**
- Degradar caderno corrompido (achado adjacente, fora: ver Risks).
- Mover o resto da E/S de arquivo do `onCreate`, ou tornar `CadernosGuardados` assíncrono.
- Qualquer mudança de comportamento observável além de a tela aparecer depois da leitura.

## Decisions

### 1. A leitura é uma função de `CoroutineScope`, no padrão de `guardarCadernoEmAndamento`

```kotlin
fun CoroutineScope.lerCadernoEmAndamento(
    cadernos: CadernosGuardados, organizacao: String, examId: String,
): Deferred<Caderno?> = async(Dispatchers.IO) { cadernos.ler(organizacao, examId) }
```

Ao lado de `GuardarCadernoEmAndamento.kt`, pelo mesmo motivo registrado ali: ter **um ponto** que um teste chama **do fio principal**, com o banco aberto como a produção o abre, sem precisar de uma Activity para afirmar que não estoura. Sem `NonCancellable`: diferente da escrita, cancelar uma leitura não perde nada.

*Alternativas rejeitadas.* (a) **`allowMainThreadQueries`**: desliga a trava em vez de respeitá-la, e o projeto já a recusou por escrito. (b) **`suspend fun` no DAO do Room**: muda a interface `CadernosGuardados`, que `ScanSession` e os testes usam sem Android (a fronteira que a KDoc dela protege), para resolver um problema de uma chamada só. (c) **Ler no ponto que lança a Activity** (`PreparoDaProva`/`SessaoActivity`) e passar pelo `Intent`: o caderno é JSON de tamanho arbitrário, e o `Intent` é o lugar errado para ele.

### 2. A abertura é dividida em antes e depois da leitura

`onCreate` mantém tudo o que não precisa do caderno — `decidirAbertura`, a recusa `NaoAbre` (que continua imediata e **sem** leitura), o pacote, o roster, o mapa, a abertura do outbox, o executor — e lança `lifecycleScope.launch { montar(lerCadernoEmAndamento(...).await()) }`. `montar(cadernoInicial)` constrói a `ScanSession`, chama `setContent` e pede (ou confere) a permissão: é o fim do `onCreate` de hoje, sem alteração de lógica.

Consequências, todas tratadas e não escondidas:

- **Até a leitura terminar a janela está em branco.** É uma consulta a um arquivo SQLite local; *suposto* da ordem de milissegundos, e a tarefa 1.3 registra o medido. Não há indicador de carga: uma tela que aparece por um quadro e some é pior que o branco.
- **`onStop` e `onDestroy` antes da leitura.** `onStop` já guarda só sob `::session.isInitialized`; `onDestroy` já guarda o executor pelo mesmo motivo. Activity destruída antes do fim da leitura cancela o `lifecycleScope`, a espera lança `CancellationException` e `montar` não roda. *Lido; a tarefa 2.2 prende.*
- **O resultado de permissão pode chegar antes da sessão.** O `registerForActivityResult` é registrado na construção e, depois de recriação (rotação com o diálogo aberto), entrega o resultado ao iniciar — possivelmente antes de `montar`. O callback de hoje faz `session.onPermission(...)` com `lateinit`, e lançaria `UninitializedPropertyAccessException`. O callback passa a **ignorar** o resultado enquanto não há sessão: `montar` já consulta a permissão real do sistema (`temPermissao()`), então nada se perde. Este é o tipo de falha que a divisão cria, e por isso a tarefa 2.2 a exercita.
- **Recriação relê o caderno guardado**, que é o de `onStop` (já gravado antes de a Activity ser destruída), como hoje.

### 3. O teste de produção lança a Activity

`Instrumentation.startActivitySync(Intent)`, sem dependência nova. Preparação: conceder `CAMERA` por `uiAutomation.grantRuntimePermission` (sem `GrantPermissionRule`), guardar o pacote da prova com discursiva (`assets/prova-discursiva.package.json`) por `PacotesEmArquivo.guardar(org, hash, bytes)` com o hash que `verificarPacote` confere, e um caderno por `CadernosEmRoom.guardar`. O `Intent` leva `EXTRA_ORGANIZACAO`, `EXTRA_CONTENT_HASH` e `EXTRA_SHORT_ID`. Afirma: a Activity chega a `RESUMED` e **continua viva** depois de a leitura terminar, sem `AndroidRuntime` no `logcat` do teste, e a sessão mostra o aluno do caderno guardado.

**Vista falhar, nesta ordem:** sobre o código de hoje (previsão anotada antes: o processo de instrumento morre com `IllegalStateException` no `onCreate`, ou a Activity termina); depois da correção, passa. A mutação de volta (chamar `ler` direto no `onCreate`) deve derrubar **só** este teste e o da função de leitura continua verde — conjuntos disjuntos: a camada de cima (a Activity) e a de baixo (a função). A revogação da mutação é conferida rodando (P10).

**Se a preparação da Activity se mostrar inviável** (o OpenCV, a câmera do emulador ou o hash do pacote travarem o teste por motivo alheio ao defeito), a tarefa 1.1 **para** e diz isso: sem este teste o conserto seria verificado só pela função de leitura, que é a camada vizinha (P16), e a cobertura declara "a Activity real não foi exercitada" em vez de dar a correção como provada.

### 4. O teste já existente vira o de regressão da função, sem perder a medição

`LerCadernoNoFioPrincipalInstrumentedTest` (não rastreado) continua como a **evidência do defeito** até o commit de correção; no mesmo commit em que `lerCadernoEmAndamento` entra, ele passa a chamar a função — a que a produção usa —, e não `ler` direto. Chamar `ler` do fio principal **continua** estourando depois da correção, por desenho; o que o teste afirma é que a **função** não estoura.

## Risks / Trade-offs

- **[Janela em branco por uma consulta]** → medida na 1.3; aceitável se da ordem de dezenas de milissegundos. Se passar de uma fração de segundo, a saída é um indicador, que seria decisão de UI própria.
- **[Caderno corrompido derruba a abertura]** → `ler` lança `SerializationException`, no `Dispatchers.IO`, e a exceção propaga para `await`, dentro do `lifecycleScope`: o efeito é **o mesmo de hoje** (o aplicativo termina), só que agora por outro caminho. Achado adjacente, **não corrigido** (regra 6, P19), e não mitigado (P8). A decisão de degradar (perder o caderno? mostrar mensagem?) é do mantenedor e toca a spec.
- **[A tela abre depois]** → `onStop` antes de `montar` não guarda nada, e está certo: não há caderno em memória para guardar, e o guardado em disco continua lá.
- **[O teste de Activity depender do ambiente]** → câmera, OpenCV e permissão variam entre o emulador e o aparelho físico; é por isso que o teste roda **nos dois**, e que a divergência entre eles é dado, não ruído (P14).

## Migration Plan

Nada a migrar: sem esquema, sem contrato, sem dado. Reverter é reverter os commits; o caderno guardado não muda de formato.

## Open Questions

- **A ordem de grandeza da janela em branco** (*suposto* milissegundos): a tarefa 1.3 mede; só muda o desenho se for grande.
