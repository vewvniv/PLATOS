## 0. Dívida herdada da 4a, antes de começar

- [x] 0.1 **Não bloqueia o início.** Na **primeira vez** que um aparelho for conectado para qualquer tarefa desta fatia, rodar `./gradlew :apps:android:connectedDebugAndroidTest` **sem filtro** nele, antes de usá-lo para qualquer outra coisa. Sem aparelho conectado, esta tarefa fica desmarcada e o trabalho segue: 1.1 a 1.3 e a seção 2 são JVM pura, e a primeira tarefa que de fato exige aparelho é a 1.4. Os 46 cenários fecharam na imagem do CI (`platos-atd34`, API 34) em 2026-09-10 e não em telefone real (SDK 36). Resultado: os dois instrumentos com número registrado. Divergência é achado a investigar **naquele momento**, com os dois lado a lado — e não motivo para reabrir o archive da 4a. **Feito em 2026-09-10 01:50 local (2026-09-09 23:50Z), no gatilho:** o telefone apareceu conectado ao rodar o `/opsx:apply`, e a suíte rodou nele antes de qualquer outra coisa. `2511FPC34G - 16`: **46 testes, 0 falhas, 0 erros, 0 ignorados**, conferidos no relatório (`<testsuites tests="46" ...>`, 46 `<testcase>`). **Sem divergência** com os 46/0 do `platos-atd34` (API 34) de 00:23 local — os dois instrumentos concordam, e não há achado a investigar. Observação lateral, registrada porque contraria o que eu havia previsto: `files/packages/<org>/` **já não existia antes** desta execução, então o apagamento do cache não pode ser atribuído a ela; o que o apagou em algum momento entre ontem e hoje não foi observado.

## 1. A porta da visão guardada

- [x] 1.1 Contrato antes do consumidor: a porta da visão, com os verbos nomeados um a um — guardar, ler, apagar da organização —, no espelho de `SessaoGuardada` e `PacotesGuardados` (decisão 4). Resultado: código novo sem consumidor, e nenhum comportamento muda. **Feito:** `VisoesGuardadas` (porta, três verbos), `VisaoDaOrganizacao` (organização + provas + `vistaEm`) e `VisoesEmArquivo`, que recebe um `File` como `PacotesEmArquivo` e grava `visoes/<org>.json` com rename atômico. **Precisão sobre a decisão 4, e ela é visível de propósito:** o `design.md` diz "no comum, como o identificador da organização já vai", o que apontaria para `SharedPreferences`; ficou **arquivo** no mesmo armazenamento comum, porque `SharedPreferences` só existe em aparelho e a própria decisão 4 pede que "reabrir sem rede usa a visão guardada" seja cenário de JVM. Mesmo armazenamento, meio diferente, pela razão que a decisão dá.
- [x] 1.2 Cenários de JVM sobre a porta: o que foi gravado é o que é lido; uma gravação posterior **substitui por inteiro**; apagar a organização remove a visão dela e não toca nas outras. **Seis cenários, verdes**, sobre `@TempDir` real: os três pedidos, mais "organização nunca vista não tem visão", "visão ilegível é tratada como inexistente" (e o arquivo sai do caminho) e "identificador fora de forma não escreve nem lê" — os três extras cobrem caminhos que o código afirma no KDoc, e código que afirma sem teste é o que esta base pune.
- [x] 1.3 **Ver falhar:** fazer a gravação **emendar** em vez de substituir — mesclar as provas novas com as antigas — e confirmar que só o cenário da substituição fica vermelho, com os outros dois verdes. Reverter, e conferir a reversão **rodando**. **Feito:** com a gravação mesclando as listas, **só** `gravacao_posterior_substitui_por_inteiro` ficou vermelho — "sobrou prova da visao anterior: [ProvaPublicada(shortId=mat-7a-2026-1…)]", a mensagem da própria asserção —, e os outros cinco continuaram verdes. Mutação revertida, suíte re-rodada verde, e `grep MUTACAO` na árvore devolve zero.
- [x] 1.4 Adaptador Android no armazenamento **comum**, e não no cifrado (decisão 4: o cifrado é para credencial de rede reutilizável). Resultado: conferido sobre armazenamento real e não sobre dublê. **Feito em 2026-09-10, no telefone `2511FPC34G` (SDK 36): 49 testes instrumentados, 0 falhas** — os 46 de antes mais três novos, que gravam sob o `filesDir` de verdade, releem por **outra instância** (leitura de memória passaria sem nada ter chegado ao disco), conferem que o nome da organização está legível em claro no arquivo — o canário de "armazenamento comum" — e que apagar remove o arquivo. **O que estes testes não provam, e fica dito:** sobreviver à morte do processo. Isso é conferência de aparelho, e é a seção 7.

## 2. A gravação, na consulta bem-sucedida

- [x] 2.1 Gravar a visão quando a consulta das organizações e a listagem das provas respondem: nome, provas e o instante. Resultado: a visão passa a existir no aparelho; ninguém a lê ainda. **Feito:** a escrita mora em `PreparoDaProva.aoListar`, e não na `Activity` — foi a fiação que produziu 9b.1 e 9b.2, e nenhum teste desta base a alcança. A máquina passou a receber a organização e a porta por construtor, e o instante por parâmetro do evento: relógio dentro dela tornaria a idade impossível de afirmar. `SessaoActivity` só constrói `VisoesEmArquivo(filesDir/visoes)` e passa `System.currentTimeMillis()`.
- [x] 2.2 Testar que consulta que **falha** não grava nada. Resultado: uma falha de rede não substitui visão boa por visão vazia — que seria pior do que não ter visão nenhuma. **A asserção forte é a segunda**, e é ela que o teste faz: uma visão boa **sobrevive** a `SemRede` e a `Falhou`. Um terceiro cenário fixa a decisão de que lista vazia **que chegou** grava vazia — "não há prova publicada" é afirmação sobre o mundo, e a consulta que respondeu a autoriza.
- [x] 2.3 **Ver falhar:** gravar também no caminho de falha, e confirmar que o cenário da 2.2 fica vermelho enquanto o da 2.1 continua verde. Reverter e rodar. **Feito:** com a gravação nos dois ramos de falha, **22 testes, 1 vermelho** — só `listagem_que_falha_nao_grava_e_nao_apaga_o_que_havia`, com "uma listagem que falhou mexeu na visao guardada". `listagem_que_chega_grava_a_visao` continuou verde, que é a disjunção que a tarefa pede. Revertida, suíte unitária completa re-rodada verde, e `grep MUTACAO` devolve zero.

## 3. A leitura no arranque — a primeira parede

- [x] 3.1 Arranque com credencial e organização guardadas, sem rede: com visão guardada, o aparelho segue para a tela de trabalho a partir dela; sem visão, recusa dizendo que precisa de rede uma vez. Resultado: a 9.2 deixa de parar antes de qualquer listagem. **Feito:** `DeviceState.Ativa` passou a carregar `Procedencia` — `Fresca` ou `Cacheada(vistaEm)` —, porque sem a procedência no estado a tela não teria como marcar a leitura, e o requisito exige que ela marque. O ramo `SemRede` de `aoConsultarOrganizacoes` consulta a organização guardada e a visão dela; sem uma das duas, continua em `SemOrganizacao`. A frase do `SEM_REDE` mudou para dizer o que resolve — "Conecte-se uma vez; depois disso ele abre sem rede" —, e o teste de **lista branca** de `TextoSemOrganizacao` ficou vermelho por isso: ele enumera o que a tela pode dizer, e quem se atualizou foi a lista, não a proteção.
- [x] 3.2 Testar que o caso **"servidor respondeu e o vínculo não está lá"** continua derrubando a escolha guardada. Resultado: a decisão 10 da 4a-zero segue intacta no que ela decidiu. **O cenário novo tem visão guardada de propósito**, e é isso que o faz valer: o cenário herdado da 4a-zero roda sem visão nenhuma e passaria mesmo com a proteção removida.
- [x] 3.3 **Ver falhar, e a mutação precisa isolar a camada:** colapsar de novo os dois casos — tratar "não respondeu" como "respondeu sem a organização" — e confirmar que o cenário do vínculo revogado e o do arranque sem rede caem em **conjuntos disjuntos**, cada um com a sua mutação. Se os dois caírem juntos, a distinção não está sendo medida. **Feito, e os conjuntos são disjuntos:** com "não respondeu" voltando a terminar em `SemOrganizacao`, cai **só** `sem_rede_com_visao_guardada_abre_a_tela_de_trabalho`; com "respondeu sem a organização" caindo na visão guardada, cai **só** `servidor_que_responde_sem_a_organizacao_derruba_a_escolha_mesmo_com_visao`. 23 cenários, 1 vermelho em cada rodada. **E a segunda mutação passou pelo teste herdado da 4a-zero sem arranhá-lo** — ele não tem visão guardada, então a proteção removida não muda o desfecho dele. As duas revertidas, suíte completa re-rodada (200 testes), `grep MUTACAO` zero. **Âncora acrescentada em 2026-09-10:** aquele "200 testes" da reversão era contagem sem prova de execução — lida de um relatório velho, ela é indistinguível de uma lida de relatório novo, que é a forma que o `UP-TO-DATE` da 4.3 tomou. As **duas rodadas de mutação** desta tarefa não estão em dúvida: elas devolveram vermelho, e rodada mascarada devolve `BUILD SUCCESSFUL` sem executar nada, nunca um vermelho. O que ficou ancorado depois é a **reversão**: a árvore de hoje contém tudo o que esta tarefa tocou, sem mutação nenhuma, e `:apps:android:testDebugUnitTest --rerun` de 2026-09-10 deu **211 testes, 0 falhas, 0 erros** com `timestamp` de relatório `2026-09-10T12:07:56.823Z` — execução conferida pelo timestamp, e não pelo `BUILD` do Gradle.

## 4. A listagem sem rede — a segunda parede

- [x] 4.1 Listagem que falha por rede passa a cair na **última listagem conhecida**, marcada como cacheada; sem listagem conhecida, o comportamento é o de hoje. Resultado: matar o processo deixa de esconder as provas que o aparelho já viu. **Feito:** `Escolhendo` e `SemProvaPublicada` passaram a carregar `Procedencia`, e a máquina passou a lembrar a última escolha apresentada — `voltarAEscolha()` e `aoVoltarDoEscaneamento()` deixaram de receber a lista por parâmetro, e `provasApresentadas` saiu da `Activity`. Quem lembra o que foi apresentado é quem decide, não a tela.
- [x] 4.2 Distinguir, **antes da escolha**, as provas com pacote guardado das que não têm — cruzando a visão com o cache de pacotes. Resultado: o professor sem rede sabe o que vai abrir antes de tocar, em vez de descobrir na barragem. **Feito:** `PacotesGuardados` ganhou `temConteudo`, que responde **presença e não conferência** — quem julga o conteúdo continua sendo o gate, e a tela diz "baixada", não "vai abrir com certeza".
- [x] 4.3 **Ver falhar, com conjuntos disjuntos declarados ANTES das mutações.** O par desta seção não é o mesmo da 3.3, e por isso é nomeado aqui em vez de presumido. Na 3.3 os dois casos eram "não respondeu" contra "respondeu, e a organização não está na lista". Aqui são:

  | Caso | O que o servidor fez | O que tem de acontecer |
  |---|---|---|
  | **X — sem resposta** | não respondeu (`SemRede`) | apresenta as provas da última listagem conhecida, marcadas como cacheadas |
  | **Y — respondeu sem provas** | respondeu, e a lista veio **vazia** | apresenta "esta organização não tem prova publicada", **não** as provas da visão, e a visão passa a ser vazia |

  **Y é a proteção que importa**, e é irmã da mutação B da 3.3: uma implementação que caísse na visão sempre que a lista apresentada ficasse vazia transformaria "a organização não tem prova publicada" — afirmação sobre o mundo, que a fatia 4a existiu para distinguir — em "aqui estão as provas de ontem". As duas mutações: (i) não consultar a visão quando a listagem falha → **X vermelho, Y verde**; (ii) cair na visão sempre que a lista resultante for vazia, sem olhar a causa → **Y vermelho, X verde**. Se as duas derrubarem o mesmo conjunto, a distinção não está sendo medida.

  **Resultado, e ele tem um achado de método no meio.** A mutação (i) derrubou **três** cenários — `listagem_sem_rede_cai_na_ultima_listagem_conhecida`, `visao_vazia_guardada_sem_rede_e_sem_prova_publicada_e_nao_falha` e `provas_com_pacote_guardado_sao_distinguiveis_das_sem` —, todos do caminho sem resposta, com **Y verde**. Três e o esperado: a disjunção declarada é entre **proteções**, e não entre cenários individuais.

  **A mutação (ii) como eu a declarei saiu INERTE, e a razão dela ser inerte é a própria proteção.** Mascarar a lista vazia *depois* de montar o estado não muda nada, porque a gravação da visão acontece **antes**: quando o mascaramento vai ler, a visão já foi substituída pela lista vazia que chegou. Ou seja, o que impede o defeito não é uma conferência — é a **ordem entre gravar e ler**, e ela não estava declarada em lugar nenhum. Substituí por **(ii-b)**, que é o defeito plausível de verdade — "não perca a lista": lista vazia que chegou não substitui a visão, e a tela segue mostrando o que havia. Essa derrubou **só** `lista_vazia_que_chegou_nao_e_mascarada_pela_visao`, com X verde. É a disjunção que a tarefa pedia, agora medida contra a mutação certa.

  **Uma armadilha de instrumento pega no caminho:** a primeira rodada da (ii) devolveu `BUILD SUCCESSFUL` com a task **`UP-TO-DATE`** e zero testes executados — exatamente o que P2 registra. As rodadas seguintes usaram `--rerun`.

  **Remedição de 2026-09-10, e ela começa desmentindo o próprio parágrafo acima.** "Ver falhar, com
  conjuntos disjuntos declarados ANTES das mutações" **é afirmação sem âncora**, e fica aqui marcada
  como tal em vez de ser apagada (P7). Conferido no histórico, arquivo a arquivo: a tabela X/Y entrou
  na árvore em `fe88bcc` (02:19), o **mesmo** commit que carrega os resultados dela; o `tasks.md`
  autorado em `eea3ae1` (01:39) tinha **três** tarefas na seção 4 e nenhuma exigência de conjuntos
  disjuntos — a 4.3 de então é a 4.4 de hoje. O registro não distingue "escrito na árvore antes de
  rodar" de "escrito depois, no mesmo commit". O que **estava** escrito antes é a obrigação geral
  (`rigorous.md` §3) e o par da **3.3**, esse sim autorado às 01:39 e implementado às 02:07.

  O que a remedição acrescenta, e por que ela não é repetição: totais de suíte e **mensagem** de
  asserção, que a primeira passada não colheu (ela deu só nomes), e uma declaração cuja ordem **tem**
  âncora — a tabela abaixo é commitada antes de qualquer mutação ser injetada, e os resultados vêm em
  commit separado.

  | Mutação | O que ela faz | Vermelho esperado | Verde esperado, e o que ele prova |
  |---|---|---|---|
  | **(i)** | `semResposta()` devolve `ListagemFalhou(SEM_REDE)` sem consultar a visão | `listagem_sem_rede_cai_na_ultima_listagem_conhecida`, `visao_vazia_guardada_sem_rede_e_sem_prova_publicada_e_nao_falha`, `provas_com_pacote_guardado_sao_distinguiveis_das_sem` — **três**, todos do caminho X | `lista_vazia_que_chegou_nao_e_mascarada_pela_visao` (Y intacto), `listagem_sem_rede_sem_visao_guardada_continua_sendo_falha` e `listagem_sem_rede_nao_e_lista_vazia` (que já esperam falha, e por isso não medem X) |
  | **(ii-b)** | lista vazia que chegou **e havendo visão guardada** não substitui a visão: apresenta o que havia | `lista_vazia_que_chegou_nao_e_mascarada_pela_visao` — **um** | as três de X, e também `listagem_vazia_que_chegou_grava_visao_vazia` e `organizacao_sem_prova_publicada_e_estado_proprio`, que rodam **sem** visão guardada e por isso não têm o que perder |
  | **(ii-c)**, acrescentada nesta passada | a mesma coisa **sem** a guarda de "havendo visão": toda lista vazia que chega cai em `semResposta()` | as três do vazio: `lista_vazia_que_chegou_nao_e_mascarada_pela_visao`, `listagem_vazia_que_chegou_grava_visao_vazia`, `organizacao_sem_prova_publicada_e_estado_proprio` | as três de X |

  **Por que a (ii-c) entra.** O registro da primeira passada diz que a (ii-b) derrubou "só" um
  cenário, e isso é verdade da (ii-b) **com** a guarda — que é a forma plausível do defeito, porque
  "não perca a lista" só faz sentido quando há lista a perder. A (ii-c) responde a pergunta que a
  primeira passada deixou aberta: a proteção Y descansa num cenário só, ou em três? Se ela derrubar
  três, o "só um" da (ii-b) é medida da **precisão da mutação**, e não da estreiteza da cobertura.

  Suíte de referência desta passada: **211 testes** em `:apps:android:testDebugUnitTest`. Cada rodada
  usa `--rerun` e é conferida pelo `timestamp` do relatório XML, e não pelo `BUILD` do Gradle — foi
  exatamente um `UP-TO-DATE` com zero testes que enganou a primeira rodada da (ii) na passada
  anterior (P2), e a rodada mais exposta a ele é a de **reversão**, cujas entradas voltam a ser as de
  um build que já passou.

  **O que as três rodadas deram** (2026-09-10, entre 12:07:00Z e 12:07:57Z, cada `timestamp` do XML
  posterior ao da rodada anterior — o instrumento executou, não reaproveitou relatório):

  **(i) — 211 testes, 3 vermelhos: exatamente os três declarados, e Y verde.**

  - `listagem_sem_rede_cai_na_ultima_listagem_conhecida`: "veio ListagemFalhou(falha=SEM_REDE) ==>
    expected: <true> but was: <false>";
  - `visao_vazia_guardada_sem_rede_e_sem_prova_publicada_e_nao_falha`: "expected:
    <SemProvaPublicada(procedencia=Cacheada(vistaEm=1757000000000))> but was:
    <ListagemFalhou(falha=SEM_REDE)>";
  - `provas_com_pacote_guardado_sao_distinguiveis_das_sem`: **`ClassCastException`**, e não asserção —
    "class EstadoDaProva$ListagemFalhou cannot be cast to class EstadoDaProva$Escolhendo". **Achado
    desta passada, e é o tipo de coisa que só a mensagem mostra:** o vermelho é real, mas ele fala da
    **forma** do estado e não da proteção. A P9 pede que a asserção confira o motivo da recusa; este
    cenário faz `state as EstadoDaProva.Escolhendo` antes de comparar, então sob qualquer mutação que
    mude o estado ele estoura no cast. Fica como lacuna **conhecida, não mitigada** (P8): a asserção
    de presença é boa, o preâmbulo dela é que não diz nada.

  **(ii-b) — 211 testes, 1 vermelho: exatamente o declarado, com o motivo na mensagem.**
  `lista_vazia_que_chegou_nao_e_mascarada_pela_visao`: "expected: <SemProvaPublicada(procedencia=Fresca)>
  but was: <Escolhendo(provas=[…mat-7a-2026-1…, …mat-7b-2026-1…], procedencia=Cacheada(vistaEm=1757000000000))>".
  A mensagem **é** o defeito escrito: as provas de ontem apresentadas como se existissem hoje. As três
  de X ficaram verdes, então **(i) e (ii-b) caem em conjuntos disjuntos** — a disjunção que a primeira
  passada afirmou, agora medida com total e mensagem.

  **(ii-c) — 211 testes, 4 vermelhos, e eu havia declarado 3. A declaração estava incompleta.**
  Além das três do vazio, caiu `listagem_sem_rede_nao_e_lista_vazia`: "expected: not equal but was:
  <ListagemFalhou(falha=SEM_REDE)>". Era predizível e eu não predisse: aquele cenário compara os dois
  estados **diretamente** (`assertNotEquals(vazia.state, semRede.state)`), e sem a guarda de "havendo
  visão" uma lista vazia que chega sem visão nenhuma termina em `ListagemFalhou(SEM_REDE)` — o mesmo
  estado de "não respondeu". Os dois colapsam, que é exatamente o defeito que a fatia 4a existiu para
  distinguir, e há um cenário nomeado para pegá-lo.

  **A resposta à pergunta que a (ii-c) foi injetada para responder:** a proteção Y descansa em
  **quatro** cenários, não em um. O "só um" da (ii-b) é medida da **precisão daquela mutação** — ela
  ataca só o caso em que há lista a perder —, e não da estreiteza da cobertura.

  **Reversão:** as três revertidas, `grep MUTACAO` zero nos `.kt`, `git diff` vazio contra o commit da
  declaração, e a reversão conferida **rodando** — 211 testes, 0 falhas, 0 erros, relatório com
  `timestamp` 2026-09-10T12:07:56.823Z, posterior às três rodadas de mutação. É esta rodada que o
  `UP-TO-DATE` da passada anterior teria mascarado, e é por isso que o sinal citado aqui é o
  `timestamp` do relatório, e não o `BUILD SUCCESSFUL`.


  **Terceiro caso, declarado para não virar decisão silenciosa:** `Falhou` — o servidor respondeu e a resposta não serve — **não** cai na visão. A spec fala em "falta de rede", e resposta inutilizável não é afirmação sobre o mundo nem ausência de servidor: é motivo para tentar de novo, e o aparelho está alcançando a rede. Fica com um cenário próprio.

- [x] 4.4 **Ver falhar (4.2):** apresentar todas como disponíveis. **Resultado honesto: a mutação NÃO isolou** — derrubou **cinco** cenários, e não um: `provas_com_pacote_guardado_sao_distinguiveis_das_sem` mais quatro que comparam o estado apresentado por igualdade exata (`provas_que_chegam_viram_escolha`, `escolher_prova_que_nao_foi_apresentada_nao_faz_nada`, `resultado_de_pacote_que_chega_fora_do_preparo_e_descartado`, `voltar_do_escaneamento_devolve_a_escolha_da_prova`). A presença é **parte do estado**, e o estilo desta base é comparar o estado inteiro; então uma marca errada quebra em todo lugar que afirma o estado. **A leitura que isso permite, e a que não permite:** os cinco vermelhos apontam para a **mesma** proteção — não há ambiguidade sobre qual delas segurou, que é o que a §3 do `rigorous.md` quer impedir —, mas esta mutação não distingue camadas como a da 4.3 distingue. Fica registrado como isolamento **fraco**, e não como isolamento.

## 5. A invalidação por revogação observada

- [x] 5.1 Quando o servidor responde e a organização guardada não está mais entre as do usuário: apagar a visão **e** os pacotes daquela organização, pelo caminho que `sair()` já usa. Resultado: a revogação leva junto o gabarito em cache. **Feito:** `DeviceSession.revogar(organizacao)`, privado, com os três apagamentos que já existiam nas portas — a escolha, a visão e os pacotes. "Pelo caminho que `sair()` já usa" são os mesmos dois verbos `apagarDaOrganizacao`; o que **não** é reaproveitado é `apagarCredencial()`, e a razão está escrita no código: na revogação o usuário continua sendo ele mesmo e pode ter outras organizações — quem sai é a organização, não ele. **Uma decisão nova, declarada aqui para não ficar silenciosa** (é o mesmo formato do terceiro caso da 4.3): **resposta com lista vazia também é revogação.** A condição que o requisito escreve — "o servidor respondeu e a organização não está mais entre as do usuário" — é satisfeita por uma lista vazia tanto quanto por uma lista que não a traz, e tratar a lista vazia como falha deixaria o gabarito em cache exatamente na revogação mais dura, a de quem perdeu **todos** os vínculos. O desfecho de tela não mudou (`SemOrganizacao(OUTRA)`); o que mudou é o que sai do disco.
- [x] 5.2 Testar em JVM que as duas coisas somem, e não só a visão. Resultado: o resíduo aceito do `design.md` fica limitado ao aparelho que nunca mais conecta. **Feito: 211 testes, 0 falhas, 0 erros**, contados no relatório XML (`:apps:android:testDebugUnitTest --rerun`, 21 arquivos somados) e não no código de saída do Gradle. Cinco cenários novos, e **as duas metades ficaram em testes separados de propósito** — a visão num, os pacotes noutro —, porque a mutação da 5.3 precisa derrubar um sem derrubar o outro; com as duas asserções juntas, ela derrubaria o mesmo teste nas duas direções. **Um dos cinco não estava pedido, e é o que sustenta os outros:** `vinculo_que_continua_valendo_nao_apaga_nem_visao_nem_pacote`. Sem ele, uma implementação que apagasse a cada consulta bem-sucedida — esvaziando o cache do professor toda vez que ele abre o aplicativo com rede — passaria nos três cenários de revogação, porque eles afirmam que o apagamento **aconteceu**, nunca que ele foi seletivo.
- [x] 5.3 **Ver falhar:** apagar só a visão, e confirmar que o cenário dos pacotes fica vermelho enquanto o da visão continua verde. **Duas mutações, com os conjuntos declarados antes de rodar.** (A) `revogar` apaga só a visão → esperado vermelho: o cenário dos pacotes e o da lista vazia; verde: o da visão. (B) lista vazia volta a não revogar nada → esperado vermelho: só o da lista vazia. **Resultado, e ele bateu com o declarado:** (A) deu **211 testes, 2 vermelhos** — `revogacao_observada_apaga_tambem_os_pacotes_da_organizacao` ("a revogacao deixou o gabarito em cache sob a organizacao revogada ==> expected: <[org-1]> but was: <[]>") e `resposta_sem_organizacao_nenhuma_tambem_e_revogacao` ("a lista vazia nao apagou os pacotes") —, com `revogacao_observada_apaga_a_visao_da_organizacao` **verde**, que é a disjunção que a tarefa pede; (B) deu **211 testes, 1 vermelho** — só `resposta_sem_organizacao_nenhuma_tambem_e_revogacao` ("a lista vazia nao apagou a visao") —, com os dois cenários do ramo não-vazio verdes. **Os dois conjuntos não são disjuntos, e a interseção é por construção, não por acidente:** o cenário da lista vazia afirma as **duas** metades do apagamento no seu ramo, então ele é sensível às duas mutações por desenho. A prova de independência está no que ficou **verde**: (A) não arranhou o cenário da visão, e (B) não arranhou nenhum dos dois cenários do ramo não-vazio. As duas revertidas, `grep MUTACAO` na árvore devolve zero, e a reversão foi conferida **rodando** — 211 testes, 0 falhas, 0 erros, contados no relatório.
- [x] 5.4 **Tarefa acrescentada durante a implementação, e o motivo é este:** o requisito da spec
  desta mudança diz "Sair SHALL apagar a visão junto com a credencial, a escolha e os pacotes", com
  cenário próprio ("Sair apaga a visão"), e **nenhuma das 29 tarefas o nomeava** — a seção 5 falava
  só da revogação observada. Sair e revogar são a mesma parede (o que sai do disco), então a tarefa
  entra aqui em vez de virar mudança nova. **Feito:** `sair()` passou a apagar a visão junto, dentro
  do mesmo `if (organizacao != null)` que já protegia o apagamento dos pacotes, e
  `sair_apaga_a_visao_junto_com_o_resto` afirma isso. A visão é a **única** das quatro coisas que
  aparece em tela: sem este apagamento, quem entrasse depois no mesmo aparelho e abrisse sem rede
  leria o nome da organização anterior e a lista de provas dela. O teste
  `sair_sem_organizacao_escolhida_nao_apaga_pacote_nenhum` virou
  `sair_sem_organizacao_escolhida_nao_apaga_nada` e ganhou a asserção da visão — renomeação que segue
  da mudança funcional, e não limpeza oportunista (P25). **Ver falhar (mutação C):** `sair()` voltando
  a deixar a visão no disco derrubou **só** `sair_apaga_a_visao_junto_com_o_resto` — "sair nao apagou a
  visao da organizacao ativa ==> expected: <[org-1]> but was: <[]>" —, 211 testes e 1 vermelho, com os
  três cenários de revogação verdes: o apagamento de `sair` e o de `revogar` são medidos em conjuntos
  disjuntos. Revertida, `grep MUTACAO` zero, reversão conferida rodando (211 testes, 0 falhas).

## 6. As telas

- [x] 6.1 Marca **visual** de leitura cacheada na tela de trabalho, com o instante da última consulta ao lado e uma ação explícita de atualizar. Resultado: procedência visível sem depender de o professor ler uma frase no meio da tela. **Feito**, e com as duas perguntas abertas do `design.md` decididas aqui — ele as deixou para a implementação de propósito. **Qual marca:** selo com `errorContainer` e borda, rótulo `SEM CONEXAO` em maiúsculas e a idade embaixo — cor sozinha não serve para quem não a distingue, então o rótulo carrega a mesma informação em texto; e `errorContainer` em vez de `tertiary` porque estar sem conexão **é** um problema para quem trabalha, ainda que não seja falha do aplicativo. **Como apresentar a idade:** data e hora absolutas (`visto em 04/09/2025 as 12:33`), e não "há 2 h" — relativo precisa de relógio na composição e envelhece na tela sem recompor, e a decisão 2 (sem teto de validade) torna uma visão do ano passado consequência, não hipótese, o que também é por que o **ano** entra. A decisão de texto e de formato mora em `marcaDeLeitura`/`avisoDeAtualizacao`, funções puras, com **7 cenários de JVM**; a tela só desenha o que recebe pronto.
- [x] 6.2 A mesma marca na escolha da prova, mais a distinção da 4.2. Resultado: as duas telas que apresentam dado cacheado o declaram. **Feito, e são três telas e não duas:** `TrabalhoScreen`, `EscolhaDaProvaScreen` e **`SemProvaScreen`**. A terceira não estava na tarefa e é a mais traiçoeira das três — "esta organização não tem prova publicada" é afirmação sobre o mundo, e afirmá-la a partir de uma visão de três dias sem dizer a idade é afirmar mais do que se sabe. O selo é **um só** (`SeloDeLeitura`), para as três: marca diferente por tela ensinaria duas linguagens a quem lê. A distinção da 4.2 já estava na tela desde aquela tarefa (`baixada` contra `precisa de rede`) e não mudou.
- [x] 6.3 Atualizar que falha **não esvazia a tela**: a visão anterior continua, ainda marcada, e o
  aplicativo diz que não conseguiu atualizar. **Feito, e o contrato veio antes** (regra 1):
  `DeviceState.Ativa` e os dois estados de `EstadoDaProva` que apresentam dado passaram a carregar
  `falhaAoAtualizar` — nulo é "nada pedido, ou o pedido chegou", não-nulo é a causa da tentativa
  frustrada. Depois as máquinas: `atualizar()` em `DeviceSession` e em `PreparoDaProva`, e **a
  característica que define as duas é não mudar o estado.** Reabrir a sessão passa por `Consultando`
  e listar passa por `Listando`, e as duas apagam a tela enquanto a consulta está no ar; o requisito
  proíbe exatamente isso. O pedido é **consumido** no resultado seguinte, senão o primeiro toque em
  "atualizar" autorizaria para sempre resultados atrasados a reescrever a tela — a guarda da 3.8 pela
  porta dos fundos. **22 cenários novos** — 7 em `DeviceSessionTest` (28 para 35), 8 em
  `PreparoDaProvaTest` (28 para 36) e os 7 do `MarcaDeLeituraTest` novo, contados no relatório XML
  arquivo por arquivo; suíte em **233 testes, 0 falhas, 0 erros** (211 para 233). O número **16** que
  estava escrito aqui era erro de contagem meu, e fica dito como erro em vez de sumir (P7).

  **Terceiro caso, declarado para não ficar implícito:** tentativa frustrada **não envelhece o dado**.
  `Procedencia` diz de **onde** o dado veio, não há quanto tempo — o que chegou por resposta do
  servidor nesta sessão continua tendo vindo dela, e quem conta que a tentativa falhou é o aviso. A
  alternativa (virar `Cacheada` ao falhar) faria o selo aparecer sobre dado que o servidor mandou
  minutos antes, e o selo perderia o significado.

  **Os conjuntos esperados, declarados antes de as mutações serem injetadas** — e este parágrafo é
  commitado antes delas, como na remedição da 4.3:

  | Mutação | O que ela faz | Vermelho esperado | Verde esperado |
  |---|---|---|---|
  | **(A)** | `atualizar()` volta a esvaziar: `state = Consultando` / `state = Listando`, mantida a guarda de estado | **5**: os dois cenários de dado fresco (`atualizar_que_falha_sobre_dado_fresco_nao_o_faz_parecer_cacheado`, `atualizar_que_falha_sobre_lista_fresca_nao_a_faz_parecer_cacheada`) e os três do aviso (`atualizar_que_nao_chega_ao_servidor_diz_que_nao_conseguiu` nas duas máquinas, `atualizar_em_sem_prova_publicada_tambem_nao_esvazia`) | os dois cenários de dado **cacheado** que dizem "não esvazia", e os de pedido consumido |
  | **(B)** | os ramos de falha devolvem `atual` sem `copy(falhaAoAtualizar = …)` | **3**: só os do aviso | os dois de dado fresco, e todo o resto |

  **Por que os dois conjuntos não são disjuntos, e por que isso não é desistência:** os três cenários
  do aviso afirmam a metade "diz que não conseguiu", e as duas mutações a quebram — (A) por destruir
  o estado que carregaria o aviso, (B) por não escrevê-lo. A independência está nos **dois cenários de
  dado fresco**: eles caem só em (A). E eles existem por um achado desta tarefa, registrado porque
  passaria por revisão sem ser visto: **partindo de dado cacheado, esvaziar a tela é indetectável no
  estado final** — cair na visão reconstrói um estado igual, com as mesmas provas e a mesma idade, e
  nenhuma asserção sobre o estado final vê diferença. É o sombreamento que a §3 do `rigorous.md`
  descreve. Partindo de dado **fresco**, a diferença aparece: cair na visão trocaria `Fresca` por
  `Cacheada`. Os cenários de cache que dizem "não esvazia" ficam, e ficam **nomeados como o que são**:
  eles afirmam o conteúdo, e não a ausência do esvaziamento.

  **O que as rodadas deram** (2026-09-10, 12:29:58Z e 12:30:13Z, `timestamp` de XML crescente):

  **(A) — 233 testes, 5 vermelhos: exatamente os cinco declarados.** Os três do aviso com
  `expected: <SEM_REDE> but was: <null>`, e os dois de dado fresco com a mensagem que **mostra o
  mecanismo** — `expected: <Ativa(…, procedencia=Fresca, falhaAoAtualizar=null)> but was:
  <Ativa(…, procedencia=Cacheada(vistaEm=1757000000000), falhaAoAtualizar=null)>`. É o sombreamento
  declarado, visto acontecer: o esvaziamento não deixa rastro no estado final, e o que denuncia é o
  dado ter **envelhecido** no caminho. **E os dois cenários de cache que dizem "não esvazia" ficaram
  verdes**, como declarado — eles afirmam o conteúdo, não a ausência do esvaziamento.

  **(B) — 233 testes, 3 vermelhos: só os do aviso**, com a mesma mensagem `expected: <SEM_REDE> but
  was: <null>`. **Os dois de dado fresco ficaram verdes**, e é essa a prova de independência que a
  não-disjunção dos conjuntos pedia: (B) não toca a proteção de "não envelhece o dado".

  **Reversão:** as duas revertidas, `grep MUTACAO` zero nos `.kt`, `git diff` vazio contra o commit da
  declaração, e a reversão conferida **rodando** — 233 testes, 0 falhas, 0 erros, relatório com
  `timestamp` 2026-09-10T12:30:27.652Z, posterior às duas rodadas de mutação.
- [x] 6.4 Registrar como lacuna conhecida que nenhum teste desta base alcança `@Composable`: a escolha da frase e do estado mora fora da tela, e o que fica descoberto é a tela ignorar o parâmetro. É a lacuna que produziu 9b.1 e 9b.2 — ela se paga na seção 7, e não com uma afirmação de que está mitigada. **Registrado em `docs/cobertura-fatia-4a-cache-referencia.md`**, na seção "o que ficou sem verificação automática", com o que exatamente fica descoberto nesta fatia: `SeloDeLeitura` não ser desenhado, `if (marca != null)` invertido, o selo desenhado com `marca.rotulo` no lugar de `marca.idade`, e o botão `Atualizar` ligado a `abrirSessao` em vez de `atualizarSessao` — este último é o mais provável dos quatro, porque as duas funções existem e fazem quase a mesma coisa. **Nenhum dos quatro é pego por teste nesta base**, e os quatro são conferíveis por `uiautomator` na tarefa 7.2. Não é mitigado, é conhecido (P8).

## 7. Conferência em aparelho — o critério de aceite

- [ ] 7.1 **A 9.2 herdada, na íntegra:** puxar o pacote com rede, fechar o aplicativo com `am force-stop`, ligar o modo avião, reabrir e escanear. Acordar o serviço com `GET /health` **antes** e registrar horário e código, pela razão da 9.3: cold start no meio do teste chega ao aplicativo como falta de rede e faz o teste passar pelo motivo errado. Resultado: o caminho que a fatia 4 inteira existe para produzir.
- [ ] 7.2 Conferir por `uiautomator` que a marca de cache e o instante estão na tela, e que atualizar sem rede mantém a visão em vez de esvaziá-la. Resultado: a lacuna da 6.4 paga em aparelho, e não por teste que não existe.
- [ ] 7.3 Revogar o vínculo no banco, reconectar, e conferir que a visão e os pacotes daquela organização somem do `filesDir` — inspecionando o disco, e não a tela. **Ou registrar por que não é produzível hoje**, no formato da 8.6 da 4a.
- [ ] 7.4 Conferir que o caminho da 4a não regrediu: escolher prova, câmera abre, folha de outra prova continua recusada por identidade.

## 8. Verificação final

- [ ] 8.1 Rodar o **comando cheio do CI**, e não a versão filtrada: `./gradlew build` mais `./gradlew :apps:android:connectedDebugAndroidTest` **sem filtro de classe**. Conferir o número no relatório, e não no código de saída do Gradle.
- [ ] 8.2 Escrever `docs/cobertura-fatia-4a-cache-referencia.md` com **como** cada verificação crítica foi vista falhar — e não que ela passa —, incluindo o que ficou sem teste automático e por quê. **Documento criado em 2026-09-10, em construção**, no formato do `cobertura-fatia-4a-zero.md`: já traz as seções 1 a 5 vistas falhar, a lacuna do `ClassCastException` da 4.3 (registrada por assunto, e não só no corpo de um commit), o isolamento fraco da 4.4 e a declaração que não foi prévia. **A tarefa segue aberta:** falta consolidar as telas (6), o aparelho (7) e o comando cheio do CI (8.1).
- [ ] 8.3 Rodar `openspec validate slice-4a-cache-referencia --strict`.
- [ ] 8.4 Conferir contra `origin/main`, **arquivo a arquivo**, as negativas que a proposta faz: `packages/domain`, `apps/web`, `apps/api`, `vision/` e `omr/` sem alteração, e nenhuma spec fora de `device-session`. Negativa larga não vale — nomear a exceção, se houver, e mostrá-la no `git diff`.
