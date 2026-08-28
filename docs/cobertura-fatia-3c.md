# Cobertura de cenários — fatia 3c (captura ao vivo)

Mapa de cada cenário da spec delta de `openspec/changes/slice-3c-live-capture/specs/` para a
verificação que o cobre, e — onde a verificação é crítica — **como ela foi vista falhar**.

Esta fatia tem uma característica que as três anteriores não tinham: **a maior parte do que ela
entrega não é número, é encanamento**, e encanamento não se verifica por fixture. CameraX e tela
dependem de conferência em aparelho. O documento separa as duas coisas desde o começo — o que está
verificado, e o que ficou fora e por quê — porque a tentação aqui é deixar o segundo grupo sem
registro.

> **Estado:** em andamento. Seções 1 a 5 fechadas; falta a conferência em aparelho real (§12 do
> protocolo de medição), que depende do mantenedor e da folha impressa.

## A hipótese que caiu antes de virar código

A fatia ia transformar em comportamento o limite de ~11 px por milímetro de papel que a 3b
registrou: abaixo dele, orientação de "aproxime" na tela. **A hipótese não sobreviveu à medição.**

A 3b mediu a resolução de cinco das nove fotos do corpus — as cinco que `papel.mjs` mede, nenhuma
delas angulada de câmera. Medidas as nove, `prova2-b` falha a 11,47 px/mm e `prova1-angulo` lê a
9,83: as faixas se sobrepõem e nenhum corte separa. A medição foi conferida contra oracle
independente antes de a hipótese ser descartada — o vão entre centros de marcador que `papel.mjs`
acha, contra o lado do ArUco que o detector mede, concordando dentro de 0,6 px/mm. A hipótese caiu
por medição, e não por medição ruim.

O registro corrigido, com a tabela das nove e a segunda hipótese que também caiu (tamanho de
arquivo), está em `docs/cobertura-fatia-3b.md`, seção **As duas fotos que o QR não decodifica**.

**O que isso custou em código:** `paperPxPerMm` saiu de `DetectionOutcome.Rectified` junto com o
teste de oracle que o conferia. Número medido sem consumidor é número que ninguém lê, e o dia em
que uma fatia medir o que de fato prevê a decodificação, ele volta com o oracle junto.

**O que ficou no lugar:** a sessão distingue *não achei folha* de *achei a folha e não consegui
ler*. Não afirma causa, e é a distinção que o pipeline de fato sabe fazer.

## `scan-session` — cenários cobertos até aqui

| Cenário | Verificação |
|---|---|
| Nada reconhecido no quadro | `ScanSessionTest.quadro sem folha deixa a sessao procurando, e sem motivo nenhum`, e no aparelho `SheetReaderInstrumentedTest.quadro_sem_marcador_nenhum_para_no_primeiro_estagio` |
| Folha reconhecida, leitura não fecha | `ScanSessionTest.folha achada e nao lida chega a tela com o motivo que o pipeline produziu`, e no aparelho `folha_achada_com_qr_ilegivel_para_no_segundo_estagio` — sobre `corpus-3b-prova2-a`, foto real cujos ArUcos são achados e cujo QR não decodifica |
| Folha reconhecida | `ScanSessionTest.folha lida vira nota apurada contra o pacote carregado` e, no aparelho, `folha_boa_fecha_e_carrega_a_leitura_interpretada` |
| Nenhuma folha no quadro | idem "Nada reconhecido no quadro" |
| Troca de folha | `ScanSessionTest.troca de folha substitui o resultado por inteiro` — **ver falhar abaixo** |
| Folha de outra prova | `ScanSessionTest.folha de outra prova e recusada, dizendo de qual prova ela e` |
| Folha cujo corredor exclui o limiar | `ScanSessionTest.folha cujo corredor exclui o limiar chega a tela com o motivo do dominio` e, no aparelho, `folha_lida_com_corredor_que_exclui_o_limiar_para_no_terceiro_estagio` |
| Folha sem pendência | `ScanSessionTest.folha lida vira nota apurada contra o pacote carregado`, com `closed` afirmado |
| Folha com pendência | `ScanSessionTest.folha com rasura apresenta a pendencia, e a nota nao fecha` |
| A cobertura sobrevive até a tela | `ScanSessionTest.a cobertura de cada bolha sobrevive ate a tela` |
| Sem rede | herdado: `SheetReaderInstrumentedTest.da_imagem_ate_a_nota_sem_tocar_a_rede` (3b) cobre o caminho inteiro sob `StrictMode`; a sessão não acrescenta chamada nenhuma |
| Permissão ainda não concedida | `ScanActivity` pede antes de abrir o preview, e o preview só é composto fora de `NoPermission`. **Exercitável à mão**, e conferido no emulador: instalado sem a permissão, o aplicativo abre no pedido, e não na tela vazia |
| Permissão negada | `ScanSessionTest.permissao negada mantem a sessao sem permissao`, e a tela explica para que a câmera serve e oferece pedir de novo — o texto está em `ScanScreen.SemPermissao` |
| Folha sem pendência / com pendência, na tela | `NotaApresentadaTest` — três casos, e o terceiro é o que impede a nota de somar o que está em disputa |

**Uma nota sobre os motivos de recusa.** A spec exige que o motivo apresentado seja o que o domínio
produziu, e não texto inventado na tela. O teste que cobre isso **não escreve a frase esperada**:
ele chama `SheetInterpreter` com uma folha cujo `ink_budget` exclui o limiar, pega o motivo que o
domínio devolveu, e compara por igualdade com o que chegou ao estado. Um teste que escrevesse a
frase à mão passaria com a tela inventando texto próprio — exatamente o que a regra proíbe.

## Como cada verificação crítica foi vista falhar

### Resultado obsoleto na tela (tarefa 3.3)

É o defeito mais caro desta fatia: a folha B mostrando a nota da folha A é plausível para quem lê,
e nenhuma outra verificação o alcança.

| Defeito introduzido | Quem acusou | O que os outros disseram |
|---|---|---|
| Um resultado novo não substituir o que já está apresentado (`if (holdsResult) state else …` no ramo de leitura fechada) | **um só**: `troca de folha substitui o resultado por inteiro`, com `expected: <folha-B> but was: <folha-A>` | os outros onze seguiram verdes, inclusive `folha lida vira nota` e `quadro que falha nao apaga o resultado apresentado` — nenhuma folha isolada percebe a diferença |

A mutação que a tarefa pedia — `Scored` guardar só a nota, sem a leitura — **não compila**: o teste
lê `payload` do estado. Isso é mais forte do que um teste vermelho e vale o registro, mas não
substitui a verificação: um tipo que não deixa o dado sumir não impede a lógica de ignorar o dado
novo. Por isso a mutação executada foi a que compila e produz o mesmo defeito na tela.

O par que sobreviveu verde importa. `quadro que falha nao apaga o resultado apresentado` existe
porque baixar o aparelho depois de escanear é o caso normal, e o resultado não pode sumir por isso
— e é justamente essa regra que, mal escrita, vira o defeito acima. As duas afirmações são vizinhas
e opostas, e precisam de testes separados.

### A distinção entre os dois momentos (tarefa 3.5)

A alternativa que a decisão 3 do `design.md` descarta é classificar pela frase da recusa em vez do
estágio do pipeline. Ela foi implementada de propósito, e a falha foi medida em **duas** execuções.

| Passo | O que apareceu |
|---|---|
| 1. Defeito injetado: `analyze` classifica procurando `"marcador"` no texto da recusa | **13 testes, 0 falhas.** O defeito é invisível enquanto a frase não muda — é exatamente esse o argumento contra ele |
| 2. Com o defeito no lugar, a frase de `DetectionOutcome.Failed` trocada para `"nao ha folha reconhecivel na captura"` | **1 falha**, e a certa: `quadro_sem_marcador_nenhum_para_no_primeiro_estagio`. Quadro vazio passou a ser classificado como "achei a folha e não consegui ler" |
| 3. Os dois revertidos | 39 instrumentados verdes |

O passo 1 é o registro que importa. Um defeito que nenhuma suíte acusa **hoje** e que quebra no dia
em que alguém reescrever uma mensagem de erro é a forma de acoplamento que esta base evita: a
verificação não estava fraca, o acoplamento é que era invisível para ela.

### A conversão de quadro (tarefa 4.2)

`FrameGray` é o único pedaço do encanamento do CameraX que se verifica sem apontar a câmera para
papel, e o defeito que ele pode ter é geométrico: a câmera alinha cada linha do plano de luminância
a um múltiplo que não precisa ser a largura, e ler o buffer como contínuo **cisalha** a imagem. Ela
continua parecendo uma folha; o que ela deixa de ter é geometria.

| Defeito introduzido | Quem acusou | O que os outros disseram |
|---|---|---|
| `rowStride` ignorado: o buffer lido como contínuo | `linha_com_enchimento_nao_cisalha_a_imagem` | `linha_sem_enchimento_vira_a_mesma_imagem` seguiu verde — sem enchimento os dois caminhos são o mesmo, e é por isso que o caso com enchimento existe separado |

O buffer do teste não é uniforme de propósito: cada pixel vale uma função da própria posição. Uma
imagem de valor único passaria pela conversão defeituosa sem uma diferença sequer.

### O aplicativo no emulador

Não substitui a conferência em aparelho, e é o que dá para afirmar sem ela: o APK **instala, abre e
não quebra**. Com a permissão concedida por `adb`, o CameraX liga, o preview aparece, e a árvore de
acessibilidade mostra a faixa de estado em `Procurando a folha…` — o que prova que a sessão está
recebendo quadros e que a tela desenha o estado dela. O emulador não tem folha para apontar, então
nada além disso é afirmável daqui.

## A conferência em aparelho (tarefas 6.1–6.4)

Aparelho: **Poco X8 Pro**, uma impressora. Amostra: seis folhas — três pares, cada par preenchido igual e
distinto dos outros —, dezenas de repetições. Os resultados foram consistentes por condição, com
todas as folhas.

### O que fecha, e em quanto tempo (tarefa 6.1)

| Distância câmera–papel | Tempo até a nota aparecer | O que acontece |
|---|---|---|
| até ~49 cm | menos de 1 s | fecha, e a nota bate |
| ~49 a ~52 cm | 2 a 2,5 s | fecha, e a nota bate na maioria das vezes; às vezes desce **1 ponto**, nunca mais — sempre com pendência ao lado |
| acima de ~52 cm | não fecha | leitura impossível neste aparelho |

O desvio entre ~49 e ~52 cm é **para baixo e limitado a um ponto**, em condições normais de sala —
não ideais, normais. A hipótese de quem conferiu é desfoque de movimento, e ela fica registrada
**como hipótese**: ninguém a mediu. Esta fatia já perdeu uma causa afirmada sem medição — os
~11 px/mm da 3b —, e o erro não se repete de graça.

Um ponto para baixo é a assinatura de **tinta sub-lida**: uma bolha marcada cujo valor cai. Ele cai
**dentro do corredor**, e não abaixo dele — a questão vira pendência na tela, e não resposta errada
calada. É `C` o lado do corredor que a captura ao vivo pressiona, e não `V`; isso é a informação
mais útil que a conferência produziu para quem for medir.

Os números são deste aparelho, desta impressora e desta iluminação, e são dado para quem for
investigar — não regra. Distância não é resolução sobre o papel, e a 3b já mostrou que resolução
não prevê decodificação.

### Sob luz direta e inclinação, a leitura degrada em pendência — e não em nota errada calada

Com incidência de luz sobre o papel ou com inclinação acentuada, a nota que aparece não bate com o
papel, e **vem acompanhada de várias pendências**. Em dezenas de repetições, sobre seis folhas,
**nunca** apareceu nota errada sem pendência ao lado. O mesmo vale para o desvio de um ponto entre
~49 e ~52 cm: ele também vem com pendência.

É o corredor de ADR-0011 fazendo o que foi desenhado para fazer. `V` e `C` existem para que
cobertura ambígua caia em **indecisa** em vez de virar resposta; sob brilho especular e inclinação
a cobertura das bolhas marcadas cai, e cai **dentro** do corredor. A questão vira pendência, a nota
deixa de ser apresentada como fechada — o cenário da tarefa 5.2 —, e quem lê a tela sabe que falta
olho humano ali. É o invariante "revisão humana vence qualquer resultado automático" chegando à
tela pelo caminho previsto, sob condição que nenhum teste alcança.

**Com que força isto vale.** O corredor foi apurado sobre nove fotos paradas, de um aparelho e uma
impressora. A conferência mostra que, sob captura ao vivo no mesmo aparelho, a degradação continua
caindo do lado seguro. Não é prova de que sempre cairá — um aparelho, uma impressora, seis folhas —
e a obrigação que a 3b registrou continua de pé:

> O limiar sob outros aparelhos e outras impressoras — o corpus é de um celular e uma impressora.
> A fatia da câmera, que verá muitos, herda a obrigação de reexaminar `V` e `C` — e mudar o número
> exigirá ADR novo, como ADR-0007 determina.

O que mudou é que ela deixou de ter um defeito conhecido atrás dela.

**O que segue sem medida:** quantas pendências por folha em cada condição, e a partir de que brilho
ou de que ângulo a folha deixa de ser aproveitável. São números de ergonomia — quantas folhas o
professor terá de revisar à mão — e quem precisa deles é a fatia 3d, que faz lote.

### Como uma conclusão errada entrou nesta seção, e saiu

Fica escrito porque é a forma de falha que este documento existe para pegar.

A primeira versão desta seção afirmava que o aplicativo apresentava **nota fechada e errada, sem
pendência** — o defeito mais grave que esta base admite. Ela não veio de medição. Veio de uma
pergunta de múltipla escolha cujas opções não continham a realidade: "nota errada" e "nota certa
com pendências a mais" estavam lá, e "nota errada **com** pendências", que é o que acontece, não
estava. A resposta foi a opção menos errada das oferecidas, e a conclusão foi escrita como se
fosse observação.

Conferência em aparelho é instrumento, e **a forma de perguntar faz parte do instrumento**. Uma
opção que falta enviesa o registro do mesmo jeito que uma janela de medição que alcança o vizinho.

### Os dois momentos, vistos em aparelho (tarefa 6.2)

A distinção que esta fatia existe para criar foi exercitada nas duas pontas, e a segunda precisou
ser provocada de propósito: toda condição natural que falha — longe demais, inclinação forte, folha
errada — falha **cedo**, na detecção, e cai em `Procurando a folha…`. Chegar ao segundo momento
exige geometria que fecha e leitura que falha logo depois.

| Condição | Faixa na tela |
|---|---|
| Câmera longe do papel | `Procurando a folha…` |
| Folha plana e enquadrada, QR coberto por um post-it | `Achei a folha e nao consegui ler: nenhum QR decodificado na ROI que o mapa declara` |
| Post-it retirado, sem mexer no enquadramento | a leitura fecha sozinha |

As duas últimas linhas valem juntas. A primeira mostra que o motivo que chega à tela é o que
`RegionQrReader` produziu, e não texto que a tela inventou — a contraparte em aparelho do que a
tarefa 3.6 verificou na JVM. A segunda mostra que `FrameOutcome.NotRead` é mesmo **transitório**: a
análise não parou, e o quadro seguinte fechou.

### A folha de outra prova para em "procurando", e não em recusa (tarefa 6.4)

A tarefa esperava a recusa "é de outra prova". Ela não acontece — e não podia acontecer, porque a
folha de teste de impressão nunca chega perto do `exam_short_id`.

As duas folhas declaram os mesmos `marker_ids` e o mesmo canto de quadrilátero (`22000, 39000`),
com alturas diferentes: `31000` contra `85000` (`fixtures/*.layout.json`). Os quatro marcadores
**são** achados. A homografia sai dos centros, e os dezesseis cantos dos marcadores — que não
entram no ajuste, exatamente para isto — denunciam o quadrilátero esticado 2,74× na vertical. O
erro de reprojeção estoura o teto de 6 px e a detecção falha
(`RegionDetector.kt`, `MAX_REPROJECTION_PX`) → `FrameOutcome.NoSheet` → `Procurando a folha…`.

O aplicativo faz a coisa certa. São as duas consequências que precisam ficar escritas:

1. **A recusa por identidade não tem oráculo em aparelho — e a funcionalidade não está em
   questão.** O requisito é `SHALL` na spec de `scan-session`, a implementação está em
   `ScanSession.resultOf`, e o cenário está verificado na JVM pela tarefa 3.6. É **obrigatória no
   MVP**: sem ela, uma folha de outra prova com região do mesmo tipo seria medida e apurada contra
   este gabarito, e sairia número plausível e sem sentido. O que falta é folha física para
   exercitá-la: `docs/cobertura-fatia-3b.md` registra que `prova1` e `prova2` são duas folhas **da
   mesma** prova de referência. A conferência em aparelho vai para a fatia 4, que puxa pacotes e é
   onde passa a existir uma segunda prova.

   **E o caso realista já está coberto pelo código.** A folha de teste é atípica justamente por ter
   região de outro tamanho; duas provas do mesmo perfil de layout têm o mesmo quadrilátero de
   166×85, a geometria fecha, o QR decodifica e a conferência de identidade dispara como desenhada.
   O que a folha de teste mostra é o caso *mais* distante, barrado antes e por outro guarda.
2. **A geometria que não fecha cai do lado errado da distinção que esta fatia criou.** Marcadores
   achados e identificados, e a tela diz "procurando" — a mesma frase da câmera apontada para a
   mesa. É um terceiro estágio — *achei os marcadores e a geometria não fecha* — achatado no
   primeiro, e quem segura o aparelho não tem como saber que a folha foi vista e rejeitada.

### A troca de folha exige o botão (tarefa 6.3)

Trocar a folha na frente da câmera **não** muda a nota na tela: é preciso tocar em "Escanear outra
folha". Isso é o desenho e não defeito — `ScanSession.holdsResult` segura o resultado e
`CameraFrameAnalyzer.deveAnalisar` para a análise quando uma leitura fecha. Com o botão, a folha
seguinte substitui a anterior por inteiro e a nota bate com o papel: o defeito da tarefa 3.3 não
foi reintroduzido pelo encanamento.

O que a conferência mostra e o `design.md` não pesou: a justificativa escrita para segurar o
resultado é "quem acabou de escanear baixa o aparelho, e a folha sai do quadro". Numa mesa com
trinta folhas o gesto seguinte não é baixar o aparelho — é pôr a próxima folha. Nesse gesto a tela
mostra a nota da folha anterior **sobre a folha seguinte já enquadrada**, que é visualmente o
defeito de 3.3 ainda que o mecanismo seja deliberado. Lote e avanço automático são a fatia 3d, e é
lá que isto se decide.

## O que ainda não está verificado, e por quê

| O que | Por quê |
|---|---|
| Quantas pendências por folha, e a partir de que brilho ou ângulo a folha fica inaproveitável | A degradação cai do lado seguro, e o custo dela não foi contado. É número de ergonomia, e quem precisa dele é a fatia 3d, que faz lote |
| Se o desfoque de movimento é a causa do desvio de um ponto | É hipótese de quem conferiu, e não medição. A 3b já mostrou o custo de afirmar causa não medida |
| A recusa por identidade, em aparelho | Verificada na JVM (tarefa 3.6) e sem oráculo físico: não existe uma segunda prova impressa com região do mesmo tipo. **A funcionalidade é obrigatória no MVP e não sai daqui** — o que vai para a fatia 4 é a conferência em aparelho, quando houver uma segunda prova |
| O estágio "achei os marcadores e a geometria não fecha" | Existe no pipeline e não existe na tela: cai em `NoSheet` junto com a câmera apontada para a mesa. Decisão de tela, e a fatia 3d é quem a tem em mãos |
| O ciclo de vida do CameraX e a tela | Não são automatizáveis aqui: o teste instrumentado não aponta a câmera para papel. Roteiro em §12 do protocolo de medição, e conferidos à mão nas tarefas 6.1–6.4 |
| A resolução de análise | Entrou como **1920x1440 provisórios** e o aparelho não a fechou: a leitura fecha abaixo de ~49 cm com esse número, e nenhuma outra resolução foi experimentada para comparar |
| O limiar sob outros aparelhos e outras impressoras | Um aparelho, uma impressora, seis folhas. A obrigação que a 3b registrou continua aberta, agora com um defeito concreto atrás dela |
| O que faz o QR de duas fotos do corpus não decodificar | Resolução e tamanho de arquivo foram medidos e caíram. Investigar é fatia própria — ver `docs/cobertura-fatia-3b.md` |
