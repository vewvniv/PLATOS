# Cobertura — slice-5c-1-a-resposta-fica-no-aparelho

Registro da verificação (`rigorous.md` §8). Cada afirmação carrega o tipo dela: **medido**,
**conferido**, **herdado** ou **suposto** (P6). Documento escrito durante o apply, tarefa a tarefa.

## 0. A linha de base (tarefas 0.1 a 0.4)

Medida em 2026-10-01 sobre a branch `vewvniv/propose-slice-5c-1-a-resposta-fica-no-aparelho` com a árvore
limpa (`git status --short` vazio) e **sem nenhuma edição de código**, com o #82 e o #83 na `main`.

| Comando | Janela (UTC) | Resultado |
|---|---|---|
| `./gradlew -p buildSrc test --rerun-tasks` | início não anotado, fim 08:56:58 | `exit 0`, 6 de 6 tasks executadas, 1 teste, 0 falhas |
| `./gradlew build --rerun-tasks` | fim 09:00:42 | `exit 0`, **183 de 183 tasks executadas** |
| `./gradlew :apps:android:connectedDebugAndroidTest --rerun-tasks -Pandroid.testInstrumentationRunnerArguments.permissaoManual=true` (sem filtro) | 09:09:02–09:12:07 | `exit 0`, 88 de 88 tasks executadas |

Contagem por task do `build` (`timestamp` do XML, todos dentro da janela do comando — P2, P3):

| Task | Testes | Falhas | Primeiro..último `timestamp` |
|---|---|---|---|
| `:apps:android:testDebugUnitTest` | 353 | 0 | 08:59:54..09:00:00 |
| `:apps:android:testReleaseUnitTest` | 353 | 0 | 08:59:42..08:59:48 |
| `:apps:api:test` | 171 | 0 | 09:00:03..09:00:22 |
| `:packages:domain:jvmTest` | 437 | 0 | 09:00:27..09:00:31 |
| `:packages:domain:jsNodeTest` | 428 | 0 | 08:58:59..08:59:03 |
| `:packages:domain:testAndroidHostTest` | 428 | 0 | 09:00:37..09:00:38 |
| `buildSrc:test` | 1 | 0 | 08:56:49 |

Igual à referência da tarefa (353 / 171 / 437·428·428 / 1; 183 de 183). **Sem divergência.**

Instrumentado, **por aparelho** (os dois com o relatório do `connected` desta sessão): emulador
`platos-atd34` (Android 14) e Xiaomi `2511FPC34G` (Android 16): **133 casos, 0 falhas, 2 pulados em cada
um** — as duas sondas `AcumuloDeInstanciasProbe` (`topologia_antiga_sob_escrita_concorrente`,
`topologia_nova_sob_a_mesma_carga`). Relatórios com `timestamp` 09:11:58 (emulador) e 09:12:03 (Xiaomi).

**Diferença para a referência (e por quê).** A referência dizia 4 pulados no Xiaomi: as 2 sondas mais os 2
testes que leem a tela do caderno, que precisam da permissão de câmera. Aqui o comando levou
`permissaoManual=true` e o Leon tocou em "Permitir" (aviso dado pelo log `>>> TOQUE EM 'PERMITIR' …`), e por
isso esses 2 **rodaram** no Xiaomi: 2 pulados, não 4. **É prova manual e pontual, que não roda em CI** (P2).

### 0.2 `sha256` das fixtures (antes de qualquer edição)

Produzido por `sha256sum fixtures/*.layout.json fixtures/*.package.json`; é o que a 6.6 compara.

```
d9f7b08c17a706b02ba21355e2286b71d09792605b1c22adc8b7b95ce566c221  fixtures/folha-de-teste.layout.json
914fb389830b4754935b608e3320d29bdd1839403c675179820a3edd6ced24c8  fixtures/prova-discursiva.aluno.layout.json
63040b2f1dfe1152ab4e83561bc77d5abc7023a73aab497491878ecc71ca5840  fixtures/prova-discursiva.layout.json
8c9756a9db45c1d08a97fd0d99f6edcb353338f78894ff5b26438d1e00078451  fixtures/prova-referencia.layout.json
c2098e10e6c70f93a469ce56ff5f0e5796b44792da0b661e01465904a48cb717  fixtures/prova-2.package.json
87a9f3312e9b7156542c7deb121c68782064aea1c61669951a9ad019e4f0a784  fixtures/prova-discursiva.package.json
ff2b94ef600101e2c20d5b6b298f7d0612ee0a66beb4d74d7dcd954cfbde40da  fixtures/prova-referencia.package.json
7282a186d3b644dac6108e7ea39931517c5a0614e8fa570b15ad09324cb14df7  fixtures/prova-referencia.turma.package.json
```

### 0.3 A guarda de dívida

`node tools/divida/divida.mjs` → `exit 0`, fatia corrente `5c`, 23 linhas, nenhuma vencida. A única que
"vence nesta fatia (5c)" é "A guarda de dívida não lê a tabela 'Aberto' do §16" (`5`): **listada e não
tomada aqui**, como a proposta diz.

### 0.4 A leitura do caderno já está fora do fio principal

`git log` mostra `1378d10 fix(scan): o caderno e lido fora do fio principal…` na `main` (#82, arquivado em
#83). `ScanActivity.kt` chama `lerCadernoEmAndamento(...)` dentro de `lifecycleScope.launch` (linhas 151–153)
e `onCreate` não chama `cadernos.ler` nem `todos()` fora dele (`grep` confere: as outras ocorrências são
comentário).

## Erros de rumo (P7)

- **`design.md` e `proposal.md` dizem que o valor-padrão `respostas = emptyMap()` mantém "cada chamada de
  teste atual como está".** Isso vale para a **compilação**, não para o comportamento: pela própria tabela
  da decisão 3, discursiva reconhecida sem entrada no mapa fica "com problema". Os testes que chamam
  `onFrame(...)` com `d1()` e esperam "capturada" precisam passar as respostas. O texto da 1.3 continua
  certo ("passam **sem mudar asserção**"): muda a chamada, não a asserção. O texto anterior fica; este é o
  registro da correção.

## 1. O contrato e a sessão (tarefas 1.1 a 1.4), JVM, sem disco

Comando: `./gradlew :apps:android:testDebugUnitTest` (a suíte android inteira de unidade; `--tests` com filtro
**reprova** pela guarda `TodoTesteDeclaradoRoda`, e por isso as mutações abaixo rodaram a suíte cheia da
task). Depois da 1.4: 370 testes, 0 falhas (353 da base + 2 de serialização + 15 de `RespostaNaSessaoTest`).

**Como cada verificação foi vista falhar** (previsão antes, resultado depois, reversão rodada — P9, P10;
a reversão de cada mutação foi conferida por `diff` contra a cópia boa **e** por nova rodada, `BUILD
SUCCESSFUL`):

| Tarefa | Mutação | Previsão | Caiu |
|---|---|---|---|
| 1.1 | `@Required` em `RegiaoDoCaderno.resposta` (o equivalente de tirar o `= null` sem quebrar a compilação dos outros chamadores) | só o decode do JSON antigo | **só** `um caderno guardado antes da resposta decodifica e vira o mesmo objeto` (`MissingFieldException`); os outros 4 de serialização verdes |
| 1.2 (a) | `Guardada` tratada como se não houvesse entrada | derruba os cenários que dependem de região capturada | 11 de `RespostaNaSessaoTest` e 10 de `ProvaComDiscursivaNaSessaoTest` — todos usam uma discursiva capturada; **nenhum** dos testes de "recusada", "sem entrada" e "refazer sem resposta" caiu |
| 1.2 (b) | ausência de entrada conta como `Capturada` | só o teste do "recorte não foi pedido" | **só** `reconhecida sem entrada alguma e sem resposta, com problema, o recorte nao foi pedido` (1 de 370) |
| 1.3 | `refazer` limpa `entregue` (só em `Caderno.semResposta`) | só o "refazer não entrega de novo" | **só** `refazer uma resposta de um caderno ja entregue nao o entrega de novo` |
| 1.4 | `refazer` também zera `parcial` | só o teste de "demais regiões e parcial" | **só** `refazer devolve o arquivo e a regiao volta a nao vista, sem tocar nas outras nem na parcial` |

**Erro de rumo na própria verificação (P7).** A primeira mutação da 1.3 aplicou `copy(entregue = false, …)`
em **dois** `copy(` (`depoisDe` e `semResposta`) e derrubou 2 testes, não 1: ela não isolava a camada. Foi
refeita só em `semResposta` e então caiu 1. A mutação (a) da 1.2 não compilou na primeira tentativa (perdia o
*smart cast*) e foi reescrita; ambas ficam registradas porque a primeira leitura teria sido "a mutação não
pegou nada" ou "pegou dois".

**Chamadas dos testes antigos.** As 33 chamadas de `onFrame` de `ProvaComDiscursivaNaSessaoTest` e as 6 de
`EntregaDoCadernoInstrumentedTest` passaram a `onFrameGuardando` (um auxiliar de teste que entrega a resposta
guardada para cada discursiva reconhecida, como o analisador fará). **Nenhuma asserção mudou.** A suíte
instrumentada ainda não foi rodada para esse arquivo nesta etapa.

## 2.1 O arquivo da resposta e o PNG

**Medido primeiro (design, decisão 2 dizia "suposto"):** `Imgcodecs.imencode(".png", …)` **existe e codifica
PNG** no artefato `org.opencv:opencv` 4.11.0 do aplicativo, nos dois aparelhos (emulador `platos-atd34`,
Android 14, e Xiaomi `2511FPC34G`, Android 16): o teste de ida e volta rodou nos dois com 0 falhas. Por isso
**não** foi preciso o `Bitmap.compress(PNG)` de reserva. Escolha registrada: `PngDaResposta` (`vision/`),
`Imgcodecs.imencode`.

**Tamanho do arquivo, medido** (2026-10-01, `Log.i` do teste, idêntico nos dois aparelhos), região de
870×1000 px (870 000 bytes crus):

| Imagem | PNG |
|---|---|
| folha em branco (todo pixel 255) | 3 079 bytes |
| "tinta" sintética (40 traços de 3 px de altura, tom 0–39, sobre papel de tom 245–254 **aleatório por pixel**) | 459 686 bytes |

**O que isto não diz (P6, P8):** não é papel nem letra. O papel sintético tem ruído por pixel, que é o pior
caso para o PNG (sem repetição), e o traço é um retângulo. O estimado do design (100 a 800 KB) fica **dentro**
do que o ruído sintético produziu, mas a medição em manuscrito real continua não feita. Não passa de alguns MB,
que era o gatilho do design para mudar algo.

**A ida e volta é bit a bit** (870×1000 px com 256 tons presentes, semente fixa; guarda de vacuidade: mais de 250
tons distintos): o PNG gravado, lido de volta e decodificado pelo `BitmapFactory` (oráculo independente: outro
decodificador, P4) tem as mesmas dimensões e os mesmos pixels do `RectifiedRegion`, com 0 pixels diferentes.

**Visto falhar:** mutação "gravar direto no nome final" (`escrever(File(diretorio, nome), png)` sem temporário
nem `rename`). Previsão: derruba só a falha no meio da escrita. Resultado no emulador: **só**
`escrita_que_falha_no_meio_devolve_recusada_e_nenhum_arquivo_fica` caiu, com
`ficou arquivo depois da falha: [454bc7a8-….png]`; os outros 6 do arquivo ficaram verdes, inclusive o do
diretório somente leitura (que não cria nada nem na mutação, e por isso não prova a atomicidade — é o
motivo de o teste de falha no meio existir). A reversão foi conferida por `diff` e por nova rodada
(`BUILD SUCCESSFUL`, 7 testes). **Lacuna:** a mutação foi rodada só no emulador, porque o Xiaomi recusou a
instalação do APK na rodada seguinte (`INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`: o aparelho
pede confirmação na tela a cada instalação). A camada é a mesma nos dois aparelhos, mas só o emulador a viu falhar.

**Costura para o teste:** `RespostasEmArquivo(diretorio, escrever = …)` aceita o escritor, porque disco cheio
não se simula de outro jeito; o padrão é `File.writeBytes`.

## 2.2 e 2.3 O analisador pede o recorte, e a análise para sozinha

**O que foi rodado.** Unidade: `./gradlew :apps:android:testDebugUnitTest` (375 testes, 0 falhas depois de
tudo revertido). Instrumentado: `AnalisadorGravaARespostaInstrumentedTest` (10 casos) **no emulador**
(`ANDROID_SERIAL=emulator-5554`, `-Pandroid.testInstrumentationRunnerArguments.class=…`; execução com filtro de
classe, para iterar — **não fecha nada**, P5). O Xiaomi não entrou nestas rodadas (ver a lacuna de instalação
na 2.1); a 6.6 roda a suíte inteira nos dois.

**O que o teste prova, sobre o documento renderizado, não papel nem câmera:** de frente e em perspectiva
(`FolhaEmAngulo`) o quadro devolve a resposta guardada de `d1` com `capturadaEm` do relógio injetado (1 234
567), `desvioSinalizado = false` e `foraPpm = 0` (folha em branco), e o arquivo existe; um quadro de duas
páginas empilhadas (`vconcat`, **sintético**: nenhuma página renderizada traz duas discursivas) devolve as
duas, com dois arquivos distintos; o `FrameOutcome` do quadro é **igual** (`assertEquals`) ao de
`SheetReader.analyze` sozinho nas três páginas; `d1` cortada ao meio (fora do quadro) e `d1` com o QR coberto
de branco (**presente e `NaoLida`**, com motivo) não geram recorte nem arquivo; com `jaTemResposta` verdadeiro
há **zero gravações e zero arquivos**; a folha do `tok-b` (a fixture tem as duas atribuições) é recortada com a
região 1 de `tok-a` já guardada; imagem que falha no meio da escrita chega como `Recusada` com o motivo e
nenhum arquivo.

**Visto falhar** (emulador; reversão por `diff` + nova rodada verde):

| Mutação | Caiu |
|---|---|
| M1: o analisador passa `""` como aluno ao predicado | **só** `com_a_resposta_ja_guardada_do_mesmo_aluno_nenhum_recorte_e_pedido` (o do aluno B ficou verde: o predicado dele diz "tok-b", e `""` também dá falso) |
| M2: o predicado é ignorado (`false && …`) | **só** o mesmo teste |
| M3': recorta também a região presente e `NaoLida` | **só** `regiao_presente_e_nao_lida_nao_e_recortada_e_o_resultado_diz_o_motivo` |
| N2 (JVM): `Caderno?.jaTemResposta` sem comparar o aluno | **só** `a folha de outro aluno nao herda a resposta do aluno do caderno` (unidade; o instrumentado ficou verde) |
| N1: `deveAnalisar` verdadeiro também para `ProvaComDiscursiva` | **3**: `so Searching e NotRead analisam quadro`, `depois de um quadro que reconhece a discursiva a sessao esta num estado que nao analisa` e o instrumentado `o_estado_depois_do_primeiro_quadro_…` |

**Erros de rumo, escritos (P7).**
- A tarefa 2.2 previa "predicado sem o aluno derruba **só** o teste do aluno B". Não é o que ocorreu no analisador
  (M1 derrubou o teste do "mesmo aluno", não o do B): quem prova "por aluno" é o `Caderno?.jaTemResposta`, e é N2
  que derruba só o teste do outro aluno. As duas camadas têm cada uma o seu.
- A tarefa 2.3 previa que, ao tornar `ProvaComDiscursiva` analisável, caísse o teste do estado e o do predicado
  ficasse verde ("conjuntos disjuntos"). Com a partição que escrevi, **N1 derruba o do predicado também**: os
  testes não são disjuntos, e eu não forço a afirmação. O que a mutação mostra é que a propriedade tem **três**
  guardas (tabela do predicado, estado real da sessão, e o instrumentado com analisador real), e que nenhuma delas
  fica verde se alguém religar a análise.
- A primeira versão de M3 mutou também o `payload` fictício e derrubou 4 testes (não isolava). Foi refeita como M3'
  e a cobertura passou a incluir o cenário `NaoLida`, que o corte a meio não exercita (a região cortada nem entra
  no resultado).

**A extração de `deveAnalisar`** foi commit próprio e antes (`refactor(scan): deveAnalisar sai da lambda…`):
mesmo predicado, sem mudança de comportamento (P19, P25).

**Lacuna (P8):** o quadro é o documento, não uma foto. A taxa de recusa do recorte ao vivo continua desconhecida;
a 6.2 mede a perspectiva moderada.

## 2.4 A fiação na `ScanActivity` (em aberto: falta o Xiaomi com o toque)

`RespostaNaAtividadeInstrumentedTest` (2 casos), com a **Activity real**, o pacote em `filesDir/packages` e o
caderno em `caderno.db`. O quadro vem de `FolhaDiscursivaRenderizada`, e **não** da câmera: duas costuras
`internal` na Activity (`analisadorDaCamera()` e `entregarQuadro(...)`) são os dois passos do laço da câmera
(o `analyze(ImageProxy)` faz `entrega(analisar(gray))`); o que fica **sem atravessar** é o `ImageProxy`/CameraX.

- (a) escanear a folha renderizada → a região vira capturada **com o arquivo existindo em `respostas/`**, e
  `quantosPendentes` da fila do outbox continua 0. Lê a sessão real, portanto exige a permissão de câmera.
- (b) Activity aberta com um caderno guardado que **já tem** resposta na região 1 (PNG real em `respostas/`):
  antes do primeiro quadro o instantâneo já traz a resposta, e um quadro da mesma região **não** grava arquivo
  novo (a pasta continua com o mesmo único arquivo). Não lê a tela e não exige a câmera.

**Visto falhar** (emulador; reversão por `diff` + nova rodada verde): F1, o instantâneo não nasce em `montar` →
**só** (b) caiu (`o instantaneo nao nasceu em montar`); F2, `entregarQuadro` não atualiza o instantâneo → **só** (a)
caiu (`o caderno visivel nao ganhou a resposta`).

**Erro de rumo na verificação (P7).** A primeira rodada de F2 derrubou também (b), com `Database is closed`:
o (a) falhava sem encerrar a Activity, e o `onStop` dela guardou o caderno depois de o `@After` fechar o banco do
teste seguinte — o vermelho de um virou o vermelho do outro (P12: vermelho novo se diagnostica pela mensagem, não
pela contagem). Os dois testes passaram a encerrar a Activity em `finally` (`comAtividade`), e F1/F2 foram
refeitos: aí caiu um só de cada vez.

**Xiaomi (2511FPC34G), 2026-10-01:** (b) **verde**, (a) **pulado** (permissão de câmera não concedida ao teste).
A rodada com `permissaoManual=true` e o toque do Leon fica para a 6.6; **a caixa da 2.4 só se marca depois dela**.

Também mudou: o predicado `deveAnalisar` e o `Caderno?.jaTemResposta` ficaram testáveis (ver 2.2/2.3).

## 3.1 A leitura normaliza (fechada)

`CadernosGuardados.todos()` (consulta Room sem filtro de organização), `Caderno.normalizado(existe)` (função
pura) e `retomarCadernoEmAndamento(...)`, que **compõe** `lerCadernoEmAndamento` (a leitura do Room continua em
`Dispatchers.IO`) e normaliza depois. **A varredura (o "varrer" da ordem varrer, ler, normalizar) entra na 4.2**:
até lá a função faz ler e normalizar.

- JVM (`CadernoRetomadoTest`, com `RespostasGuardadas` falso): cenários "A resposta referenciada não existe mais" e
  "Caderno de antes desta mudança" pela função pura **e** por `retomarCadernoEmAndamento`; gabarito, região com
  problema e região não vista não são tocados; `entregue` não é limpo.
- Instrumentado (`RetomarCadernoNoFioPrincipalInstrumentedTest`, Room real aberto por `CadernosEmRoom.abrir`,
  chamado de `runOnMainSync`, sem `allowMainThreadQueries`): guardar, apagar o arquivo, retomar → região não vista e
  **não estoura**; com o arquivo em disco, o caderno volta idêntico.
- **Visto falhar:** `retomar` sem `?.normalizado(...)` → **só** os 2 testes que chamam `retomar` com resposta
  ausente/legado (`CadernoRetomadoTest`) caíram, os da função pura ficaram verdes; revertido por `diff` e nova
  rodada (383 testes, 0 falhas). A mutação não foi rodada contra o teste instrumentado de Room.

## 3.2 A `ScanActivity` lê só por `retomarCadernoEmAndamento` (em aberto: falta o Xiaomi com o toque)

`RespostaNaAtividadeInstrumentedTest` ganhou 3 casos pela Activity **real**: reabrir retoma as mesmas regiões,
respostas e contador; reabrir com a resposta apagada retoma a região como não vista e a Activity abre; trocar de
aluno antes de fechar (a Activity encerra e as instâncias reiniciam) e reabrir retoma só o segundo.

**Visto falhar:** a Activity voltando a ler por `lerCadernoEmAndamento` cru → **só**
`reabrir_com_a_resposta_apagada_…` caiu (`expected:<NaoVista> but was:<Capturada>`); revertido por `diff` e nova
rodada (verde no emulador).

**Erro de rumo na verificação (P12).** No Xiaomi o caso "resposta apagada" falhou numa asserção sem mensagem. Antes
de mexer, a mensagem foi posta (`a Activity nao sobreviveu: STARTED`) e o diagnóstico foi **expectativa minha
errada**, não defeito: sem a permissão de câmera o diálogo do sistema fica na frente e a Activity fica `STARTED`,
não `RESUMED` (medido no 2511FPC34G). A asserção passou a exigir `STARTED`, `!isFinishing` e `!isDestroyed` (o que a
spec afirma: a Activity abre e sobrevive); o que prova a retomada continua sendo o instantâneo do caderno. No
emulador, com permissão, o estado era `RESUMED`.

**Xiaomi, 2026-10-01:** reabrir (2 casos) verdes; (a) da 2.4 e "trocar de aluno" **pulados** sem a permissão.

## 4. O prazo de 30 dias (4.1 a 4.4)

**O que foi rodado.** Unidade: `./gradlew :apps:android:testDebugUnitTest` (398 testes, 0 falhas, tudo
revertido). Instrumentado, **no emulador**, por classe (`ANDROID_SERIAL=emulator-5554`, filtro de classe: itera,
**não fecha nada**, P5): `RespostaNaAtividadeInstrumentedTest`, `VarreduraNaAberturaInstrumentedTest`,
`ApagamentoLocalInstrumentedTest`. O Xiaomi não entrou nestas rodadas (a 6.6 roda tudo nos dois).

### 4.1 A regra e a execução por arquivo (JVM)
`RetencaoDaResposta.arquivosAEliminar` (`PRAZO_DIAS = 30`, `>=`) e `varrerRespostas` (captura a exceção **por
arquivo**, conta, nunca lança; se `todos()` falha, não elimina nada e diz `semLeituraDosCadernos`). Fronteiras
pinadas: 29 dias mantém; **30 exatos elimina**; 30 dias menos 1 ms mantém; 31 elimina; órfão e `.tmp` eliminam com
zero dia; referência sem arquivo, lista vazia, relógio anterior à captura (e a conta não estoura); duas
organizações misturadas; uma eliminação que falha não impede as outras e é contada.

**Visto falhar** (reversão por `diff` + nova rodada verde):

| Mutação | Caiu |
|---|---|
| `>=` vira `>` | `exatamente 30 dias elimina` **e** `duas organizacoes misturadas…` (esta também fixa a borda: `b-no-limite.png` tem 30 dias) — 2, e não 1 como a tarefa previa |
| "não referenciado" passa a manter | os 3 que têm órfão: `arquivo sem referencia…`, `duas organizacoes…`, `um arquivo que nao se consegue eliminar…` |
| a exceção por arquivo propaga | **só** `um arquivo que nao se consegue eliminar nao impede os outros, e e contado` |

### 4.2 Os dois pontos de entrada, e o que não se elimina
- **Escaneamento:** `retomarCadernoEmAndamento` agora **varre, lê e normaliza**, nessa ordem, no mesmo
  `Dispatchers.IO`. Com vencida (31 d, referenciada), órfã, temporário e válida (5 d) no disco, abrir a Activity
  real elimina as três primeiras, mantém a válida, e a região da vencida volta **não vista** no caderno retomado —
  sem nenhum quadro analisado.
- **Porta de entrada:** `SessaoActivity.onCreate` lança `varrerRespostasDoAparelho` (IO). Teste com a
  `SessaoActivity` real: um caderno de **outra organização e outra prova** referencia uma vencida e uma válida; só
  a válida sobra. O Room não usa `allowMainThreadQueries`, então uma consulta no fio principal derrubaria o teste.
- **Falha de eliminação não impede abrir:** um `.tmp` que é diretório não vazio (`DirectoryNotEmptyException`)
  fica; a Activity abre, o caderno é retomado e os outros vencidos somem.
- **Sair e revogação preservam a resposta:** `ApagamentoLocalInstrumentedTest` ganhou a resposta (arquivo de
  verdade, referenciada pelo caderno) conferida **lado a lado** com o resto, nos dois caminhos.
- **Enviar a parcial não elimina:** `EnvioDaParcialNaoEliminaARespostaTest`, com `EnvioDeResultados` real e um
  `RespostasEmArquivo` real; canário: o servidor confirmou e a fila esvaziou.

**Visto falhar** (emulador): normalizar **antes** de varrer → **só** `abrir_o_escaneamento_elimina_vencida_…`
(`expected:<NaoVista> but was:<Capturada>`); também no JVM (`a varredura roda antes da leitura…`).
A falha de eliminação propagando → **só** `uma_eliminacao_que_falha_nao_impede_o_escaneamento_de_abrir` (o
processo caiu, como previsto: o `await()` lançou dentro do `lifecycleScope`). Sem a chamada na `SessaoActivity` →
**só** `abrir_o_aplicativo_elimina…`. `ApagamentoLocal`: injetar no `apagarDaOrganizacao` do roster um apagamento do
diretório de respostas derrubou `sair_…` **e** `revogacao_…`, e só eles.

**Erro de rumo, escrito (P7).** (1) A primeira mutação de ordem ("ler antes de varrer, normalizar depois") **não
derrubou nada**: ler antes é inofensivo enquanto a normalização vier depois da varredura. O invariante é
"normalizar depois de varrer"; a mutação foi refeita nesse sentido. (2) Ao ligar a varredura, três testes
anteriores (2.4 b, 3.2) ficaram vermelhos: gravavam a resposta com `capturadaEm` de 1970, e a varredura, certa, a
eliminou. O dado do teste foi corrigido (um dia atrás), não a varredura. (3) O teste de "trocar de aluno" gravava a
resposta de B **antes** de abrir a Activity: era um órfão, e a varredura da abertura o eliminava. Agora é gravada
depois da abertura, como o analisador faz.

**Lacuna (P8):** `ApagamentoLocal` testa `DeviceSession` com os armazenamentos injetados, e `respostas/` não é um
deles: "sair apagaria `respostas/`" não é mutável nesse nível. A injeção acima prova que a asserção reage; a
proteção é estrutural (nenhum código fora de `scan/` toca `respostas/`), conferida por `grep` na 6.1.

### 4.3 As duas ordens de queda
(a) o processo termina depois de gravar e antes de `onStop` guardar o caderno → na abertura o arquivo é órfão e some,
o caderno é o anterior; (b) `refazer` elimina o arquivo e o processo termina antes de o caderno ser guardado → o caderno
ainda o referencia e é lido com a região não vista. As quedas são **simuladas pelo estado que deixam** (arquivo e
caderno no disco, instâncias reiniciadas); nenhum processo foi morto de verdade (P6).
**Visto falhar, conjuntos disjuntos:** a varredura ignorando órfão derrubou (a) (e os 2 outros testes com órfão), **não**
(b); `retomar` sem normalizar derrubou (b) (e os 2 outros que dependem de normalização), **não** (a).

### 4.4 Refazer elimina na hora, e a varredura é a rede
`ScanActivity.refazerResposta` (sessão, instantâneo e eliminação em `Dispatchers.IO + NonCancellable`; a exceção é
engolida porque a varredura é a rede). Pela Activity real: o arquivo some na hora, a região volta a não vista e o
contador cai. Com a eliminação imediata **desligada de propósito** (a sessão refaz, o arquivo não é eliminado, o caderno
é guardado, as instâncias reiniciam), a abertura seguinte o elimina. **Visto falhar:** sem o `eliminar` → **só** o teste
da eliminação na hora; varredura ignorando órfão → o da rede (e os outros 2 com órfão). **Não verificado:** que a
eliminação não roda no fio principal é por construção (`Dispatchers.IO`); nenhum teste a observa.
