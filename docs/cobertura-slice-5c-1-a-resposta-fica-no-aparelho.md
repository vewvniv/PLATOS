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
