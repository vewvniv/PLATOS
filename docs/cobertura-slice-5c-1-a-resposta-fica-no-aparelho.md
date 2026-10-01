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

## 5.1 A tela da resposta (em aberto: falta o Xiaomi com o toque)

`RespostaTela` (tela cheia, por cima da câmera) abre pelo toque no indicador de região **discursiva capturada com
resposta** (`abreResposta`); a imagem é decodificada em `Dispatchers.IO` (`carregarResposta`); o número da questão
é o `rotulo` do caderno; o aviso de desvio é `ScanState.ProvaComDiscursiva.AVISO_DE_DESVIO` (texto único, ao lado do
`AVISO`), **abaixo** da imagem e só com `desvioSinalizado`; **Refazer** e **Voltar** (e o botão de voltar do sistema).

**Verificação pela árvore de acessibilidade, sem dependência nova** (`RespostaTelaInstrumentedTest`): `compose-ui-test`
não está no catálogo, e adicioná-lo seria mudança de dependência (P22) com justificativa no `design.md` (regra 4); o
que ele compraria — tocar e ler nós — a árvore de acessibilidade entrega (`AccessibilityNodeInfo`,
`performAction(ACTION_CLICK)`, `getBoundsInScreen`). O disco é preparado como a produção o deixa (PNG **real** de
123×77 px, com tons diferentes por pixel, em `respostas/`, e o caderno no Room). 4 casos, no emulador, nenhum pulado:
(1) resposta sinalizada → o nó da imagem tem a descrição `Resposta da questao 1: imagem de 123 por 77 pixels`, igual
à montada com as dimensões do arquivo lidas por `BitmapFactory` com `inJustDecodeBounds`; o aviso aparece e seus
`boundsInScreen` **não interceptam** os da imagem; (2) não sinalizada → sem aviso; (3) Refazer → o contador cai de
"2 de 3" para "1 de 3", o indicador vira "1 · falta", a tela fecha e o arquivo some; (4) região não vista e gabarito
não são clicáveis e nenhuma tela abre.

**Visto falhar** (emulador; reversão por `diff` + nova rodada verde):

| Mutação | Caiu |
|---|---|
| aviso sempre ligado (`if (true)`) | **só** `a_resposta_nao_sinalizada_nao_traz_aviso` |
| aviso sempre desligado (`if (false)`) | **só** `a_resposta_sinalizada_mostra_a_imagem…` |
| todo indicador clicável | **só** `regiao_nao_vista_e_gabarito_nao_abrem_a_tela` |
| a descrição troca largura e altura | **só** `a_resposta_sinalizada_mostra_a_imagem…` |

**O que isto não diz (P6, P8).** A descrição de acessibilidade é o que se lê; que o bitmap **desenhado** ocupe a
caixa `Fit` certa, que a cor e o contraste sejam legíveis e que o aviso seja claro para um professor **não foram
vistos por olho humano nem em aparelho**. A tela do estado novo continua sem conferência visual (§16). A prova de
que o aviso "não cobre" é de caixas de acessibilidade, que podem divergir do desenho.

**Xiaomi:** os 4 casos leem a tela e exigem a permissão de câmera; **sem ela são pulados com o motivo**, e rodam
com `permissaoManual=true` e o toque do Leon (6.6). A caixa da 5.1 só se marca depois.

## 5.2 O texto do aviso

`ScanState.ProvaComDiscursiva.AVISO` deixou de dizer "Nada foi guardado." e passou a dizer que nenhum resultado é
gravado enquanto o caderno não completa, que as respostas capturadas ficam neste aparelho, e que o caderno completo
é entregue para envio. O teste que o prende (`ProvaComDiscursivaNaSessaoTest`, "a folha e reconhecida…") foi
**atualizado de propósito**. `grep -rni "nada foi guardado" apps --include=*.kt`: a única ocorrência restante é a nota
em KDoc que registra a frase antiga (P7); a spec principal em `openspec/specs/scan-session/spec.md` ainda a traz até
o archive aplicar o delta. **Visto falhar:** repor o texto antigo derrubou **só** esse teste. **Não conferido em
aparelho** (§16).

## 6.1 A resposta não sai do aparelho

- `ARespostaNaoSaiDoAparelhoTest`: um caderno completa com duas respostas guardadas (nomes UUID `…d1.png`, `…d2.png`
  conferidos **no caderno** antes), vira `ApuracaoParaEnvio.Parcial` e o `corpoDoEnvio()` literal **não contém** o
  nome de nenhum arquivo, `.png`, `respostas`, `iVBOR` (cabeçalho de PNG em base64) nem `data:image`. Canário: o corpo
  traz `"partial":true`. **Visto falhar:** acrescentar `respostas/…d1.png` ao `capturedAt` derrubou esse teste **e** o
  `ResultadoDtoTest` (que fixa o formato do instante): 2, e não 1.
- `grep -rn "respostas" apps/android/src/main/kotlin/com/platos/android/{outbox,api} apps/api/src/main
  packages/domain/src/commonMain` (2026-10-01): **nenhuma ocorrência em código**; as que aparecem são a palavra em
  comentário (`ObjectiveScoring.kt:273`, "julgar as respostas contra o gabarito"; `Routes.kt:156/290`, "as duas
  respostas" do HTTP). E `grep -rln "RespostasEmArquivo\|RespostasGuardadas\|PngDaResposta\|\"respostas\""
  apps/android/src/main`: só `scan/` e `vision/PngDaResposta.kt`. Pergunta que cada um responde: o teste, "este corpo
  leva a imagem?"; o `grep`, "algum código de envio lê `respostas/`?". **Lacuna:** o `grep` prova o código de hoje, e
  nenhum teste proíbe que alguém o escreva amanhã (P8).

## 6.2 A taxa de recusa em perspectiva e o tempo (medido sobre o documento renderizado)

`MedidaDoRecorteAoVivoInstrumentedTest` (2026-10-01; `logcat -s Medida5c1`; as únicas asserções são guardas de
vacuidade). **Os ângulos foram fixados antes de qualquer resultado e não mudaram depois (P11):** `frontal`; a
`moderada-da-5c0` (a de `FolhaEmAngulo`); a `espelhada`; e a `forte` (a única fora da faixa de 1 a 4% que a 5c-0 chamou
de moderada), cada uma levando os quatro cantos da página para dentro por frações da largura e da altura (ver o
código). 2 folhas (`tok-a`, `tok-b`) × 2 páginas (uma discursiva em cada) × 4 ângulos = **16 regiões**.

| Aparelho | Guardadas | Recusadas pelo resíduo (ou por outro motivo) | Não lidas |
|---|---|---|---|
| emulador `platos-atd34` | **16** | **0** | 0 |
| Xiaomi `2511FPC34G` | **16** | **0** | 0 |

Tempo (`System.nanoTime`, 15 quadros por aparelho, **sem** o primeiro, que aquece o JIT e as bibliotecas nativas):

| Aparelho | `analisar()` (análise + recorte + PNG + arquivo) | só `SheetReader.analyze` |
|---|---|---|
| emulador (x86, software) | mín 301 / **mediana 314** / máx 447 ms | mín 50 / mediana 57 / máx 194 ms |
| Xiaomi | mín 197 / **mediana 229** / máx 238 ms | mín 38 / mediana 45 / máx 86 ms |

Logo o recorte, a codificação e a gravação custam algo como **180 a 260 ms por região reconhecida**, uma vez por
toque (a análise para sozinha depois — tarefa 2.3). **Tamanho do arquivo:** ver a 2.1 (3 079 bytes em branco,
459 686 bytes com ruído sintético, em 870×1000 px).

**O que isto NÃO diz (P6, P8):** não é papel, não é letra, não é sombra, não é foto de celular: é o documento
renderizado e deformado por homografia conhecida, em que o resíduo é pequeno por construção. **A taxa de recusa em
foto real é desconhecida**, e "0 de 16" não a estima. A pergunta de risco da proposta — recorte recusado impede o
professor de completar o caderno — fica **sem resposta empírica**: se na foto real a taxa não for zero, a região
aparece com problema e o caderno não completa, e o argumento passa a ser a fatia "finalizar caderno incompleto"
(§8), hoje fora de escopo. Os tempos são de aparelho parado sobre quadro sintético, sem a câmera.

## 6.3 O diretório novo está sob a regra de extração

O dono do nome é `RespostasEmArquivo.diretorioDe(filesDir)` (P28): a `ScanActivity`, a varredura da porta de entrada
e o teste passam por ela. `RegrasDeExtracaoInstrumentedTest.o_diretorio_das_respostas_esta_sob_um_dominio_negado_por_inteiro`
lê as regras **do APK instalado** (o mesmo oráculo dos outros casos da classe), decide o domínio do diretório (`file`
se está sob `filesDir`, `root` se está sob o diretório de dados, nenhum se foge) e exige que as **duas** seções
(`cloud-backup` e `device-transfer`) neguem esse domínio com `path="."`. Verde nos **dois** aparelhos.

**Visto falhar:** (1) o diretório movido dois níveis acima de `filesDir` (`/data/data/respostas`) → o teste caiu nos dois
aparelhos, com a mensagem "nao esta sob o diretorio de dados do aplicativo"; (2) o `<exclude domain="file">` removido
do XML → caíram esse teste e `o_backup_em_nuvem_nega_os_mesmos_quatro`. **Erro de rumo (P7):** a mutação (2) pretendia
atingir `device-transfer` e atingiu `cloud-backup` — o `indexOf("<device-transfer>")` achou a ocorrência dentro do
comentário do XML, antes da seção. O teste novo checa as duas seções, por isso caiu do mesmo jeito, mas a mutação
específica de `device-transfer` **não foi isolada**; `a_transferencia_entre_aparelhos_nega_os_quatro_dominios` já
cobre essa seção e não foi vista falhar nesta mudança (herdada da `transferencia-entre-aparelhos`). Reversões
conferidas por `diff` e nova rodada verde.

**A conferência em transporte (`bmgr`/`dumpsys backup`, como a `transferencia-entre-aparelhos` fez) NÃO foi feita.**
O emulador só tem o `LocalTransport` (e o par `com.google.android.gms/.backup…`, ausente), que diz `Backup is not
allowed` por causa do `allowBackup="false"` e nada diz da regra de transferência; o `D2dTransport` só existe no
Xiaomi, onde `bmgr` está desabilitado, e habilitá-lo é mudar o aparelho do mantenedor (P22). **Lacuna conhecida,
não mitigada (P8):** que a transferência de fato não leve `respostas/` está provado pela regra declarada e empacotada,
não pelo transporte.

## 6.4 O §16 (`ARQUITETURA-FINAL-v3.md`)

Feito (2026-10-01), sem apagar o texto anterior de nenhuma linha (P7; cada acréscimo abre com a data):
(a) **"A política §10.8 diverge do comportamento"** ampliada para a resposta guardada, com a decisão do mantenedor de
2026-10-01 e o que ela deixa devendo; (b) **"A região discursiva ainda não passou pelo aparelho nem pelo papel"**
atualizada com o que passou pela câmera ao vivo (16 de 16 sobre o documento renderizado) e o que o papel ainda precisa
medir, acrescido da taxa de recusa em foto real; (c) **"O limiar do desvio…"** atualizada com a tela que herda o sinal;
(d) **"LGPD com dados de menores"** (a da tabela "Ponto de não-retorno", que a guarda lê) com a imagem de manuscrito no
aparelho e o que a elimina e o que não; (e) **linha nova** "O teto de 30 dias das respostas só roda quando o aplicativo
abre", token `` `5c` ``, a 5c-2 como veículo (escrita sem crases na célula, para a guarda não ler dois tokens), custo e
dono. **(f) é do archive** — dizer em uma frase que "A guarda de dívida não lê a tabela 'Aberto'" (`5`) segue `5`, que a
mudança própria que a paga ainda não existe, e que precisa ser proposta antes do archive da última mudança da fatia 5 —
**e não foi feito aqui**.

**Verificação** (`node tools/divida/divida.mjs`):
- o original: `exit 0`, 24 linhas lidas, nenhuma vencida; a linha nova aparece como `em dia`, `` `5c` ``, e entre as que
  "vencem nesta fatia (5c)" junto da `5` (a da guarda, **não tomada**), que é o esperado para um limite igual à fatia
  corrente;
- **visto aceitar:** a linha nova é lida e listada;
- **visto reprovar**, por cópia criada nesta sessão (`diff` contra o original mostra **uma** linha diferente, 584): (1) a
  cópia com o token sem crases → `exit 2`, "nao comeca com token entre crases na coluna `Fatia-limite`"; (2) a cópia com
  `` `5c-2` `` → `exit 2`, "tem o token `5c-2`, fora da gramatica". Os dois pelo motivo certo.

## 6.6 A verificação final, com os comandos cheios (2026-10-01)

Ordem que coube na memória (16 GB): emulador encerrado (`adb emu kill`) e daemons parados; `buildSrc` e `build` com o
Docker de pé; daemons parados de novo; emulador `platos-atd34` subido sem janela; suíte instrumentada completa.

| Comando | Janela (UTC) | Resultado |
|---|---|---|
| `./gradlew -p buildSrc test --rerun-tasks` | 10:22:31–10:23:03 | `exit 0`, **6 de 6 tasks executadas**, 1 teste |
| `./gradlew build --rerun-tasks` | 10:23:03–10:26:51 | `exit 0`, **183 de 183 tasks executadas** (igual à 0.1) |
| `./gradlew :apps:android:connectedDebugAndroidTest --rerun-tasks -Pandroid.testInstrumentationRunnerArguments.permissaoManual=true` (sem filtro) | 10:27:44–10:31:57 | `exit 0`, 88 de 88 tasks executadas |

Contagem por task do `build` (`timestamp` do XML dentro da janela; antes → depois, P3):

| Task | Antes (0.1) | Agora | Primeiro..último `timestamp` |
|---|---|---|---|
| `:apps:android:testDebugUnitTest` | 353 | **399** | 10:25:39..10:25:45 |
| `:apps:android:testReleaseUnitTest` | 353 | **399** | 10:25:50..10:25:55 |
| `:apps:api:test` | 171 | 171 | 10:26:00..10:26:17 |
| `:packages:domain:jvmTest` | 437 | 437 | 10:26:45..10:26:47 |
| `:packages:domain:jsNodeTest` | 428 | 428 | 10:26:41..10:26:43 |
| `:packages:domain:testAndroidHostTest` | 428 | 428 | 10:26:25..10:26:29 |
| `buildSrc:test` | 1 | 1 | 10:22:54 |

Os +46 testes de unidade do android são os desta mudança; **domínio, api e buildSrc não mudaram de contagem**, como a
mudança não os toca.

Instrumentado, **por aparelho** (relatórios desta sessão, `timestamp` dentro da janela do comando):

| Aparelho | Casos | Falhas | Pulados | `timestamp` |
|---|---|---|---|---|
| emulador `platos-atd34` (Android 14) | **170** (eram 133) | 0 | 2 (as sondas `AcumuloDeInstanciasProbe`) | 10:31:51 |
| Xiaomi `2511FPC34G` (Android 16) | **170** | 0 | 2 (as mesmas sondas) | 10:31:43 |

**A rodada do Xiaomi é manual e pontual, e não roda em CI (P2):** com `permissaoManual=true` e **um** toque do Leon em
"Permitir" (aviso `>>> TOQUE EM 'PERMITIR' …` no log do aparelho), os casos que leem a tela rodaram em vez de pular: os
dois da 2.4/3.2 (`a_regiao_reconhecida_vira_capturada…`, `trocar_de_aluno_antes_de_fechar…`) e os quatro de
`RespostaTelaInstrumentedTest` (5.1). Isso fecha as caixas 2.4, 3.2 e 5.1, que estavam abertas só por causa dele. Sem o
toque, o mesmo comando pula esses casos com o motivo.

`npx vitest run` **não foi executado**: a mudança não toca `apps/web` (`git diff --stat origin/main...` não lista nada ali).

**Escopo.** `git diff --stat origin/main...` filtrado por `packages/domain|apps/api|apps/web|supabase|outbox|openspec/specs|
fixtures|golden`: **uma** linha, `apps/android/src/androidTest/.../outbox/ApagamentoLocalInstrumentedTest.kt` — arquivo de
**teste** que só tem `outbox` no caminho e foi estendido com a resposta guardada (4.2). Nenhum arquivo de produção, de
domínio, de api, de web, de banco, de `result-sync` (`openspec/specs`), fixture ou golden. **`sha256` das fixtures:
idêntico ao da 0.2** nos oito arquivos (`Get-FileHash`, feito depois de tudo).

## 6.5 O que esta mudança NÃO verificou (P8)

Cada item está **conhecido**, e nenhum está mitigado.

- **Papel.** Nenhuma foto de folha impressa foi recortada. O recorte, o PNG e a tela foram exercitados sobre o documento
  renderizado e deformado por homografia conhecida (16 de 16 aceitos, 0 recusados, nos dois aparelhos). **A taxa de
  recusa em foto real é desconhecida**; se não for zero, a região fica com problema e o caderno não completa, e o
  argumento passa a ser a fatia "finalizar caderno incompleto" (§8).
- **Letra de aluno.** Todo "desvio" e toda "tinta" daqui é sintético (retângulos). O limiar do desvio (5% e 4 mm²) e o
  teto do resíduo (1,0 mm) continuam fixados sem letra (§16, `6`), e **nenhum foi mudado** (P11). O **tamanho do PNG** com
  manuscrito real é desconhecido: o medido foi 3 079 bytes em branco e 459 686 bytes com ruído sintético.
- **Foto de celular.** Sem sombra, sem moiré, sem foco, sem a câmera ao vivo: o quadro é injetado em `analisar()` e em
  `entregarQuadro()`; o `ImageProxy`/CameraX **não** foi atravessado por nenhum teste desta mudança.
- **A tela em aparelho, por olho.** A `RespostaTela` foi lida pela árvore de acessibilidade. Que a imagem caiba, que o
  aviso se leia, que o contraste seja bom, e que "Refazer" não seja tocado por engano **não foram vistos**. A tela do
  estado novo continua sem conferência visual (§16).
- **O teto de 1,0 mm** do resíduo: herdado da 5c-0, não medido de novo.
- **A lacuna do prazo.** O teto de 30 dias só roda quando o aplicativo abre (linha nova do §16, token `5c`): aparelho que
  guarda resposta e nunca mais abre não expurga. O número de eliminações que falharam **fica só no log**
  (`RespostasVarredura`); ninguém o vê. O desvio de política (sair e revogação preservam a imagem) é decisão do
  mantenedor de 2026-10-01, não resolvida com o jurídico.
- **O relógio do aparelho.** Quem adianta ou atrasa a data muda o prazo; não há relógio monotônico que atravesse reinício.
  `agora < capturadaEm` mantém (testado) e não há proteção contra relógio adiantado.
- **A queda real do processo.** As duas ordens de queda (4.3) foram **simuladas pelo estado que deixam**, com as
  instâncias reiniciadas; nenhum processo foi morto no meio de uma gravação.
- **Backup e transferência pelo transporte.** A regra declarada e empacotada cobre `respostas/` (6.3, nos dois aparelhos),
  mas a conferência pelo `bmgr`/`D2dTransport` **não foi feita** (habilitar `bmgr` no Xiaomi é mudar o aparelho).
- **Cifra em repouso.** A resposta está em PNG em claro sob `filesDir`. Decisão própria, não tomada.
- **A eliminação fora do fio principal** é por construção (`Dispatchers.IO`); nenhum teste a observa.
- **`ApagamentoLocal` e `respostas/`.** `DeviceSession` não tem handle para o diretório: "sair apagaria `respostas/`" não é
  mutável nesse nível; a proteção é estrutural (6.1).
- **Que nenhum código de envio lê `respostas/`** é o `grep` de hoje; nada impede que alguém o escreva amanhã.
- **A nota não existe.** Não há entrada de nota, fato de correção, `grading_result`, envio de imagem nem corpus — é a 5c-2
  e a fatia 8, e nada disto foi tocado.
- **O Xiaomi e a instalação.** Em uma rodada filtrada o Xiaomi recusou o APK (`INSTALL_FAILED_USER_RESTRICTED`, confirmação
  na tela do aparelho); na rodada final instalou normalmente. Quem roda precisa estar com o aparelho na mão.

**Erros de rumo desta mudança, em um só lugar (P7):** o texto de `design.md`/`proposal.md` sobre o valor-padrão de
`respostas` (testes antigos precisaram passar as respostas); duas previsões de "ver falhar" que não se confirmaram como
escritas (2.2 e 2.3, e a borda `>=` da 4.1, que derrubou 2 testes e não 1); a primeira mutação de ordem da 4.2, que não
derrubou nada por não ser o invariante; a mutação do `device-transfer` da 6.3, que atingiu `cloud-backup`; os testes de
1970 e a resposta de B anteriores à abertura, que a varredura corretamente eliminou; o vermelho em cascata por Activity
não encerrada; a expectativa de `RESUMED` sem permissão. Nenhum foi corrigido apagando o registro.

## Registro cronológico e fatos de ambiente (2026-10-01)

Horas locais (+02:00) e, entre parênteses, UTC quando a fonte é o log do Gradle (`date -u`). O que segue **consolida**
o que está dito nas seções acima e acrescenta o que ainda não estava escrito (P7: nada acima foi apagado).

### Linha do tempo

| Hora local | O que aconteceu | Fonte |
|---|---|---|
| 00:12 | proposta commitada (`40afa06`) | `git log` |
| 10:20 | decisão do mantenedor de manter as imagens ao sair, registrada (`687e06a`) | `git log` |
| ~10:55–11:00 (08:55–09:00Z) | linha de base da JVM: `buildSrc` 6/6, `build --rerun-tasks` 183/183 | `base-buildsrc.log`, `base-build.log` |
| 11:09–11:12 (09:09–09:12Z) | linha de base instrumentada, nos dois aparelhos, com o toque do Leon (aviso às 11:11:02) | `base-connected.log` |
| 11:13 | linha de base commitada (`4528f20`) | `git log` |
| 11:21 | 1.1 a 1.4: contrato e sessão | `b4f08f8` |
| 11:24 | 2.1: PNG e arquivo atômico (o Xiaomi recusou o APK numa rodada: `INSTALL_FAILED_USER_RESTRICTED`) | `13d2927` |
| 11:26 | `deveAnalisar` extraído (commit próprio) | `837189c` |
| 11:33 | 2.2 e 2.3: analisador e propriedade "a análise para sozinha" | `44dda64` |
| 11:40 | 2.4 parcial: fiação na Activity | `6436e30` |
| 11:47 | 3.1 e 3.2: retomada normalizada | `3ad9c7b` |
| 12:07 | 4.1 a 4.4: prazo de 30 dias, varredura nos dois pontos, refazer | `e0ddb22` |
| 12:15 | 5.1, 5.2 e 6.1: tela da resposta, texto do aviso, corpo do envio | `470e020` |
| 12:17 | 6.2: 16 de 16 guardadas, tempos | `02e5744` |
| 12:20 | 6.3: regra de extração | `b2abf14` |
| 12:22 | 6.4: §16 e a guarda de dívida (`exit 0`, e `exit 2` nas duas cópias) | `d43be15` |
| 12:24–12:31 (10:22Z–10:31Z) | verificação final: `buildSrc` 10:22:31–10:23:03Z, `build` 10:23:03–10:26:51Z, instrumentado 10:27:44–10:31:57Z; toque do Leon às 12:29:42 | `final-*.log` |
| 12:33 | verificação final registrada (`3ebaa12`) | `git log` |
| 12:39 | `/opsx:update`: `proposal.md`, `design.md` e `tasks.md` corrigidos e anotados | esta seção |

### Fatos de ambiente que o apply coletou e que o próximo apply precisa saber

- **Emulador:** `C:\Users\Leon\scoop\apps\android-clt\current\emulator\emulator.exe -avd platos-atd34 -no-window -no-audio
  -no-boot-anim -no-snapshot-save -gpu swiftshader_indirect`. O `emulator` **não está no `PATH`**. Subiu e foi
  encerrado (`adb emu kill`) com aviso e autorização do Leon; ficou desligado no fim. Com os dois aparelhos
  conectados, `ANDROID_SERIAL=emulator-5554` ou `ANDROID_SERIAL=TOXSR4MR9989MBQW` escolhe onde o `connectedDebugAndroidTest`
  roda, para iterar em um só. O Xiaomi **continuou visível ao `adb`** depois do `adb emu kill` (a nota anterior dizia
  que sumia).
- **Memória:** 5,2 GB livres com o Docker de pé e nada mais, 6,5 GB com os daemons parados e o emulador desligado.
  A ordem que coube: agregado e `buildSrc` sem emulador; depois `./gradlew --stop`, emulador, instrumentado.
- **`--tests` reprova na guarda.** `./gradlew :apps:android:testDebugUnitTest --tests '…'` falha na guarda
  `TodoTesteDeclaradoRoda` (todo `@Test` declarado precisa ter resultado). Para iterar, rode a task inteira
  (`:apps:android:testDebugUnitTest`, ~10 s) e leia o XML.
- **Nomes de teste no android** não aceitam `:` (`Name contains illegal characters`).
- **`ScanActivity.kt` e `design.md` têm fim de linha CRLF** (e `ScanActivity.kt` mistura). Edição por script que
  casa texto com `\n` falha em silêncio ou com "não achei"; a ferramenta `Edit` funciona.
- **Texto de interface sem acento** (como todo o aplicativo), embora a documentação use acento.
- **Xiaomi (`2511FPC34G`):** pede confirmação **na tela** a cada instalação (`INSTALL_FAILED_USER_RESTRICTED` se ninguém
  confirma); sem a permissão de câmera o diálogo do sistema fica na frente e a Activity fica `STARTED`, não `RESUMED`;
  `permissaoManual=true` com **um** toque cobre a rodada inteira.
- **Teste que depende de relógio** precisa de `capturadaEm` recente: 1970 é "vencido há mais de 30 dias" e a varredura
  da abertura o elimina, corretamente.

### O que cada medição mostrou (resumo, com a seção onde está o detalhe)

| Medição | Resultado | Seção |
|---|---|---|
| linha de base JVM | 183/183; 353 · 171 · 437/428/428 · 1 | 0 |
| linha de base instrumentada | 133 casos, 0 falhas, 2 pulados em cada aparelho | 0 |
| `Imgcodecs.imencode` em PNG | existe e codifica, nos dois aparelhos; ida e volta bit a bit | 2.1 |
| tamanho do PNG (870×1000) | 3 079 B em branco; 459 686 B com ruído sintético | 2.1 |
| recorte em perspectiva | 16 guardadas, 0 recusadas, 0 não lidas, nos dois aparelhos | 6.2 |
| tempo de `analisar()` | mediana 314 ms (emulador), 229 ms (Xiaomi); só a análise 57 e 45 ms | 6.2 |
| verificação final JVM | 183/183; android 399; domínio, api e buildSrc inalterados | 6.6 |
| verificação final instrumentada | 170 casos, 0 falhas, 2 pulados, nos dois aparelhos | 6.6 |
| `sha256` das fixtures | idênticos aos da 0.2, nos oito arquivos | 6.6 |
| guarda de dívida | `exit 0`; `exit 2` nas cópias sem crases e com `5c-2` | 6.4 |

### O que o `/opsx:update` corrigiu (12:39)

`proposal.md` (a frase "nenhum teste atual muda de chamada", riscada e explicada), `design.md` (a decisão 3, o "suposto"
da decisão 2, a ordem da decisão 5, a pergunta em aberto do tamanho do PNG, e a seção nova "Registro do apply") e
`tasks.md` (notas de 1.3, 2.1, 2.2, 2.3, 4.1, 4.2 e 5.2). As `specs/` **não mudaram**: já diziam o que o código faz
(a spec do aviso fala de "nenhum resultado gravado enquanto incompleto" e "as respostas ficam neste aparelho", que é o
texto novo). `openspec validate --strict`: válida. Nenhum código foi tocado.
