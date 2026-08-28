# Cobertura de cenários — fatia 3b (limiar do OMR, resposta e nota)

Mapa de cada cenário da spec delta de `openspec/changes/slice-3b-omr-threshold/specs/` para a
verificação que o cobre, e — onde a verificação é crítica — **como ela foi vista falhar**.

Esta fatia herda a característica que organizou o documento da 3a — quase tudo que ela produz é
número, e número errado não quebra nada — e acrescenta uma sua: **quase tudo que ela decide mora
numa comparação.** Um `>` no lugar de um `>=` move exatamente uma borda, não quebra tipo nenhum, e
chega à nota como um acerto que ninguém marcou. Por isso as bordas são testadas uma a uma, com o
valor esperado escrito ao lado, e não por amostragem no meio das faixas.

Uma diferença de unidade que vale registrar antes das tabelas: a cobertura chega aqui em
**permilagem inteira**, e isso elimina desta camada a classe de falha que o `CLAUDE.md` manda
cobrir primeiro. Não existe `Int` não finito, então `NaN > tolerância` não tem como passar calado
aqui. A guarda contra medição não finita continua sendo da camada de baixo, onde o `Double` existe,
e a 3a já a tem em `BubbleMeter`.

> **Estado:** a fatia está fechada. O limiar é `OmrThreshold.MEDIDO_NA_FATIA_3B` — **400‰ com
> margem de 50‰** —, apurado sobre o corpus fotografado pela regra que ADR-0011 fixou antes da
> primeira foto.

## `capture-omr` — 14 cenários, 14 cobertos

| Cenário | Verificação |
|---|---|
| Medição de todas as bolhas | `BubbleMeterTest` (3a) e `SheetInterpreterTest.o conjunto de bolhas julgadas e exatamente o que a regiao declara`, sobre as 160 bolhas do golden |
| Cobertura conferida contra valor conhecido | `BubbleMeterTest.cobertura conferida contra fracao conhecida por construcao` (3a) — oracle analítico |
| A cobertura sobrevive ao veredito | `SheetInterpreterTest.a cobertura sobrevive ao veredito`, `BubbleVerdictTest.o veredito preserva a cobertura que o produziu`, e no aparelho `SheetReaderInstrumentedTest.a_interpretacao_preserva_a_cobertura_medida`, que compara bolha a bolha a medição crua com a interpretada |
| Janela de medição inválida | `BubbleMeterTest` (3a) — quatro recusas, cada uma nomeando a bolha ou a causa |
| Papel escurecido não vira tinta | `BubbleMeterTest.papel escurecido nao vira tinta` (3a) |
| Folha cujo corredor contém o limiar | `BubbleVerdictTest.folha cujo corredor contem o limiar e legivel` e `as duas pontas do corredor sao aceitas` — 200 e 400 são valores que ADR-0010 permite, e uma guarda com `<` recusaria os dois |
| Folha cujo corredor exclui o limiar | `BubbleVerdictTest`, `SheetInterpreterTest.folha cujo corredor exclui o limiar e recusada`, e no aparelho `folha_cujo_corredor_exclui_o_limiar_e_recusada_no_aparelho` |
| Cobertura exatamente no limiar | `BubbleVerdictTest.as sete bordas caem do lado previsto` — `T` é indeciso, e o teste diz isso na linha |
| Cobertura dentro da margem de indecisão | idem, mais `margem zero nao deixa nenhuma cobertura indecisa`, que prova que a faixa é aberta |
| Uma alternativa marcada | `AnswerSheetTest.uma alternativa marcada vira a resposta` |
| Nenhuma alternativa marcada | `AnswerSheetTest.nenhuma marcada e nenhuma indecisa vira em branco` |
| Duas alternativas marcadas | `AnswerSheetTest.duas marcadas viram multipla marcacao, nomeando as duas` e `multipla marcacao nao e desempatada por cobertura` |
| Questão com bolha indecisa e nenhuma marcada | `AnswerSheetTest.bolha indecisa sem nenhuma marcada vira indecisa, e nao em branco`, com o par `bolha indecisa ao lado de uma marcada nao impede a resposta` |
| Cobertura de todas as questões declaradas | `AnswerSheetTest.cada questao declarada aparece exatamente uma vez, na ordem do mapa` e `SheetInterpreterTest` nos três modos de divergência: **faltando**, **não declarada** e **repetida** |

## `scoring` — 14 cenários, 14 cobertos

| Cenário | Verificação |
|---|---|
| Folha de uma variante conhecida | `ObjectiveScoringTest.folha toda correta tira o maximo que o pacote declara` |
| Variante que o pacote não conhece | `ObjectiveScoringTest.variante que o pacote nao conhece e recusada`, conferindo que a mensagem nomeia a variante |
| Payload sem variante, pacote com uma só | `ObjectiveScoringTest.payload sem variante e aceito quando o pacote declara uma so` — é o caso de hoje: o QR só carrega variante a partir da fatia 7 |
| Payload sem variante, pacote com mais de uma | `ObjectiveScoringTest.payload sem variante e recusado quando o pacote declara mais de uma` |
| Conjunto de itens divergente | `ObjectiveScoringTest.conjunto de itens divergente e recusado, e a mensagem diz o que faltou` |
| Item que o gabarito não cobre | `ObjectiveScoringTest.item sem entrada no gabarito e recusado` |
| A nota diz de qual pacote ela é | `ObjectiveScoringTest.a nota diz de qual pacote e de qual variante ela e`, contra `contentHash()` |
| Prova sem pendência | `folha toda correta tira o maximo` e `folha toda errada tira zero, e a nota fecha` |
| Questão em branco | `em branco vale zero, e continua fechando a nota` |
| Questão com múltipla marcação | `multipla marcacao vira pendencia, e a nota deixa de fechar` e `a pendencia nao vira zero nem acerto` |
| Questão indecisa | `questao indecisa vira pendencia` |
| O máximo em disputa é declarado | as asserções de `pointsAtStake` nos três testes de pendência, mais `nota fora da escala nao e representavel` |
| Sem rede | `SheetReaderInstrumentedTest.da_imagem_ate_a_nota_sem_tocar_a_rede` — `StrictMode` com `detectNetwork` e `penaltyDeath` sobre o caminho inteiro, com o meta-teste da 3a ao lado provando que a guarda reage a um socket de verdade |
| Apuração repetida | `a mesma entrada apurada duas vezes da o mesmo resultado` e `a apuracao nao altera a leitura nem o pacote` |

### O caminho inteiro, com oracle que não é desta fatia

`SheetReaderInstrumentedTest.da_imagem_ate_a_nota_sem_tocar_a_rede` percorre imagem → cobertura →
veredito → resposta → nota sobre a digitalização versionada, e confere o resultado contra **7**.

O 7 não sai de nenhum tipo desta fatia. Ele vem das medições cruas: em cada questão, a alternativa
acima de 500‰ é a marcada — as duas nuvens desta folha estão a 424 pontos uma da outra, vazia mais
escura a 92‰ e caneta mais fraca a 516‰ —, e o acerto é essa letra comparada com o `answer_key`.
Quem preencheu a folha na fatia 2b marcou sem consultar o gabarito, e acertou 7 de 40.

O número está **fixado** no teste, e não apenas comparado, porque um oracle também pode desandar:
se ele passasse a devolver 0 ou 40, a comparação com a nota continuaria verde com a apuração
quebrada nos dois sentidos. O 7 foi conferido por fora, em `fixtures/prova-referencia.papel.json`
cruzado com o `answer_key` pela implementação em JavaScript — que não compartilha uma linha com o
caminho testado.

## O que a auditoria encontrou

Dois defeitos reais, achados relendo código **já verde**, e nenhum dos dois quebrava teste. Os dois
têm a mesma causa: comparar conjuntos onde a repetição importa.

**`Set` não vê repetição.** Uma medição duplicada — 161 medições com `q01/A` duas vezes — produz um
conjunto de 160 elementos idêntico ao declarado, e a conferência passava. A questão afetada ganhava
uma quinta bolha, e duas marcadas iguais viravam múltipla marcação: uma pendência que a leitura
inventou, sobre uma folha correta.

**Do lado da nota, custava ponto.** Com 41 respostas contendo `q01` duas vezes, o conjunto continuava
batendo com a variante e o laço somava o item duas vezes. A nota saiu **41 de 40** — bem-formada,
plausível e fora da escala.

| Como foi visto falhar | O que apareceu |
|---|---|
| Os dois testes foram escritos **antes** da correção, contra o código então em vigor | `SheetInterpreter` aceitou 161 medições; `ObjectiveScoring` devolveu `points=41, maxScore=40` |
| Depois da correção | os dois recusam, nomeando a bolha e o item repetidos |

Vale o registro de que esta é a forma mais forte de "visto falhar" desta base até agora: o defeito
não foi injetado para ver o teste reagir — o teste foi escrito primeiro e encontrou um defeito que
estava lá.

A correção é dupla, de propósito. A recusa por repetição fecha a porta; um `require` em
`ObjectiveScore` fecha a janela, tornando não representável uma nota fora de `0..max_score` ou uma
soma que passe do máximo junto com as pendências. Uma é sobre a folha, e recusa; a outra é sobre o
programa, e lança.

## O resultado do corpus (ADR-0011, tarefa 7.5)

O que o ADR-0011 manda registrar mesmo quando aprova, para que o número possa ser reexaminado.

**O corpus.** Duas folhas da prova de referência e a folha de teste de impressão, impressas na
mesma impressora, fotografadas com a câmera de um aparelho MediaTek em três condições cada — luz
frontal, sombra e ângulo. Nove fotos, das quais **sete** são lidas de ponta a ponta pelo pipeline de
produção.

**As classes.** A folha 1 tem 28 bolhas preenchidas conforme a instrução e 12 preenchidas de
propósito de leve, que ADR-0011 mantém fora do critério. A folha 2 tem 40 conformes. A folha de
teste tem 1. São **69 traços distintos conformes** e 12 fracos, dentro dos 80 que o ADR declarou.

**Os números, medidos pelo `SheetReader` — a grandeza que ADR-0011 declara:**

| | Valor | Onde |
|---|---|---|
| `V` — maior cobertura entre as vazias | **220‰** | `prova1-sombra q36/D` |
| `C` — menor cobertura entre as bem preenchidas | **649‰** | `teste-angulo teste/D` |
| Vão entre as duas nuvens | **429‰** | |
| `T = (V + C) / 2` | 435‰ → **400‰** | restrito ao teto do corredor de ADR-0010 |

127 medições de bolha conforme (649 a 894‰), 36 de bolha fraca (294 a 651‰), 492 de bolha vazia
(até 220‰).

**Aprova:** `V ≤ T − M` → 220 ≤ 350 ✓ · `C ≥ T + M` → 649 ≥ 450 ✓

**O corredor é que está apertando, e por cima.** O ponto médio pedia 435 e o teto de ADR-0010 o
trouxe para 400. A folha e a câmera separam melhor do que o ADR previu — o contrário do risco que
ele existia para cobrir.

**A classe observacional, sob `T = 400`:** das 36 medições de bolha preenchida de leve, 23 leem como
marcada, 7 como indecisa e 6 como vazia. Quem preenche fraco tem a resposta lida como branco em um
caso a cada seis. É informação para a fatia da câmera orientar o aluno, e não reprovação: ADR-0011
mantém essa classe fora do critério porque a folha manda preencher o círculo inteiro.

**Uma ressalva sobre o gabarito da folha 2.** O registro em papel das 40 letras não veio; as classes
dela foram derivadas da própria medição, tomando por marcada a bolha mais escura de cada questão.
Isso seria circular se a separação fosse apertada — e não é: dentro de cada questão a bolha marcada
mede 678 a 864‰ e a segunda mais escura no máximo 136‰, uma folga mínima de **599‰**. As duas fotos
da folha 2 produzem as mesmas 40 letras, independentemente. Nenhum erro de classificação é
representável nessa margem.

**Duas fotos ficaram de fora da leitura oficial**: `prova2-a` e `prova2-b` são recusadas no QR. Por
que, não se sabe — ver a seção seguinte, que corrige o que este parágrafo afirmava.

## As duas fotos que o QR não decodifica

> **Correção de 2026-08-28, apurada na fatia 3c.** Esta seção dizia que as duas fotos ficaram de
> fora **por resolução**, e afirmava um limite de ~11 px por milímetro de papel — número que a 3c
> herdaria como orientação de enquadramento na tela. A afirmação não sobreviveu à medição das nove
> fotos, e está retirada. O que segue é o registro corrigido.

O número original saiu do subconjunto que `papel.mjs` mede: **cinco** das nove fotos, e nenhuma das
duas anguladas de câmera entre elas. Nesse subconjunto a separação parecia limpa. Medida a resolução
sobre o papel das **nove**, contra o que o pipeline de produção faz com cada uma:

| Foto | px/mm | Leitura |
|---|---|---|
| prova1-frontal | 12,61 | lê |
| prova1-sombra | 12,51 | lê |
| **prova1-angulo** | **9,83** | **lê** |
| prova2-a | 9,32 | recusa |
| **prova2-b** | **11,47** | **recusa** |
| prova2-c | 11,17 | lê |
| teste-frontal | 12,57 | lê |
| teste-sombra | 12,24 | lê |
| teste-angulo | 10,27 | lê |

**Não existe limiar de resolução que separe as duas colunas.** `prova2-b` falha a 11,47 e
`prova1-angulo` lê a 9,83 — as faixas se sobrepõem, e nenhum corte as separa.

**Nem o formato separa.** As três fotos da folha 2 são as únicas 3060×3060 recomprimidas do corpus,
contra 3072×4096 originais das outras seis, e `prova2-c` lê enquanto `prova2-a` e `prova2-b` não.
Tamanho e recompressão eram a hipótese seguinte, e elas não sobrevivem ao terceiro caso. O que de
fato decide a decodificação **não está medido**, e fica assim: trocar uma explicação não medida por
outra é exatamente o que produziu o erro que esta seção corrige.

**Como a resolução foi medida, e por que o código não ficou na árvore.** Pelo lado dos quatro ArUcos
detectados contra os 14 mm que `CaptureGeometry.MARKER_SIDE` declara, conferida contra um oracle que
não compartilha detector: o vão entre os centros de marcador que `papel.mjs` acha por componentes
conexos em JavaScript. Os dois concordam dentro de **0,6 px/mm** nas cinco fotos que ambos medem. O
código que produzia o número saiu junto com a orientação por resolução, porque sem consumidor ele
seria número que ninguém lê; ele volta no dia em que uma fatia medir o que prevê a decodificação.

`CorpusInstrumentedTest.as_duas_fotos_sem_qr_seguem_recusadas` afirma o **fato** — as duas são
recusadas, e recusadas no QR — e não afirma mais causa nenhuma. Se a leitura melhorar e passar a
ler estas duas, ele fica vermelho, e a mudança é deliberada.

**Como o erro passou.** Nenhum teste ficou vermelho, e nenhum ficaria: o teste afirmava a recusa
daquelas duas fotos, que é verdade, e a causa vivia só no comentário e neste documento. É a mesma
forma de falha que a seção **O defeito que o corpus encontrou na fatia 3a** registra sobre a zona de
silêncio do QR — verificação verde sobre a fixture que não distingue —, aqui numa forma nova: **a prosa afirmava mais do que a
verificação sustentava.** O que a 3c mudou não foi o código medido, foi a amostra: nove fotos em vez
de cinco.

## O defeito que o corpus encontrou na fatia 3a

O QR fica encostado na borda superior do quadrilátero dos marcadores — `qr.v` é zero no `LayoutMap`
—, então a região retificada começava exatamente no topo dele e a **zona de silêncio** que o padrão
QR exige ficava fora da imagem. A decodificação dependia de a homografia deixar um ou dois pixels de
folga.

Medido no corpus, antes da correção:

| Foto | 1ª linha escura do QR | Decodifica? |
|---|---|---|
| prova-frontal | `y = 0` | não |
| prova-sombra | `y = 0` | não |
| prova-angulo | `y = 2` | sim |

Dois pixels separavam uma folha legível de uma folha recusada.

**Por que a fatia 3a não viu.** A digitalização de mesa da 2b cai do lado sortudo, e é a única
imagem que a 3a tinha. Todos os testes de QR daquela fatia continuam verdes com a sangria
desligada — inclusive `le_o_qr_da_regiao_retificada`, que decodifica de verdade e afirma o payload
inteiro. Um teste que exercita o caminho certo, sobre a fixture certa, e ainda assim não pode
reprovar o defeito.

**A correção** é um canvas próprio para o QR, retificado com 4 mm de sangria, em `RegionDetector`.
A região que produz cobertura não muda em um pixel: nenhum número desta fatia se move, nenhum
golden muda, o hash do pacote fica igual. A folha não é tocada — o QR na folha continua onde está.

| Defeito introduzido | Quem acusou | O que os outros disseram |
|---|---|---|
| `QR_BLEED_MM = 0` | `o_canvas_do_qr_tem_zona_de_silencio_em_volta`, com `o QR comeca em y=0`, mais **as seis fotos do corpus**, que param de decodificar — inclusive a angulada, que antes passava por sorte | os testes de QR da 3a sobre a digitalização de mesa seguiram **verdes**: é exatamente essa a razão de o defeito ter atravessado a fatia inteira |

O teste novo afirma a **folga**, e não a decodificação. Afirmar que decodifica seria repetir o que a
3a já afirmava — e que continuava verdadeiro com o defeito no lugar.

## A tolerância entre as duas implementações, na câmera

§10 do protocolo registra **20‰** entre `papel.mjs` e o `SheetReader` na digitalização de mesa. Foto
de câmera afasta os dois um pouco mais. Medido sobre as 330 bolhas das quatro fotos que as duas
implementações conseguem ler:

| mediana | p95 | p99 | máximo |
|---|---|---|---|
| 6‰ | 17‰ | 25‰ | 29‰ |

O teto por bolha ficou em **30‰**, e a divergência tem explicação de método e não de defeito:
`papel.mjs` amostra o círculo na imagem original, o `SheetReader` retifica antes de amostrar, e a
reamostragem suaviza. O viés confirma — o `SheetReader` lê a bolha escura 5‰ mais clara.

**Um teto por bolha sozinho não bastaria**, e isso foi verificado: as duas implementações divergindo
29‰ em *toda* bolha passariam por ele. Por isso há uma segunda guarda, sobre a **mediana** por foto,
com teto de 12‰ contra os 6–7‰ observados.

| Defeito introduzido | Quem acusou | O que os outros disseram |
|---|---|---|
| `+15‰` constante em toda medição de `BubbleMeter` | a guarda da mediana, em **três** das quatro fotos, com `mediana de 14‰ … teto 12‰` | o teto por bolha só reagiu numa foto — sozinho, ele teria deixado o viés passar em três |

## Como cada verificação crítica foi vista falhar

### Veredito de bolha e corredor (tarefa 2.4)

| Defeito introduzido | Quem acusou | O que os outros disseram |
|---|---|---|
| `>=` trocado por `>` na borda superior de `OmrThreshold.verdictFor` | três: `as sete bordas caem do lado previsto` apontando `T+M`, `as bordas inclusivas sao as mesmas com que ADR-0011 aprova o corpus`, e `margem zero nao deixa nenhuma cobertura indecisa` | os outros sete seguiram verdes, inclusive todos os de corredor |
| Validação do corredor sempre aprovando (`if (true)`) | duas: `folha cujo corredor exclui o limiar e recusada` e `um passo alem de cada ponta do corredor ja recusa` | os outros oito verdes, inclusive `folha cujo corredor contem o limiar e legivel` — o par positivo não distingue nada sozinho |

A primeira mutação é a que importa, e o motivo é a terceira linha que ela derrubou. `margem zero`
não fala de borda nenhuma: ele existe para provar que a faixa de indecisão é aberta. Uma borda
trocada muda o significado da margem inteira, e é por isso que três testes escritos para coisas
diferentes caem juntos.

A segunda mostra por que o par positivo entra em toda tabela desta base: `folha cujo corredor
contem o limiar e legivel` continua verde com a validação desligada. Sozinho, ele afirma apenas
que a função devolve `null` — que é o que uma função vazia também faz.

### Resposta de questão (tarefa 3.4)

| Defeito introduzido | Quem acusou | O que os outros disseram |
|---|---|---|
| Desempate por maior cobertura em `AnswerSheet`, escolhendo a bolha mais escura quando há mais de uma marcada | três: `multipla marcacao nao e desempatada por cobertura` nomeando o resultado, `duas marcadas viram multipla marcacao` e `os quatro casos convivem na mesma folha` | os outros cinco verdes — nenhum caso de uma marcada só percebe a diferença |
| Ramo de indecisa desligado (`if (true)`), fazendo toda questão sem marcada cair em branco | duas: `bolha indecisa sem nenhuma marcada vira indecisa, e nao em branco` e `os quatro casos convivem na mesma folha` | os outros seis verdes, `nenhuma marcada e nenhuma indecisa vira em branco` entre eles |

O desempate é a mutação que mais convence quem a lê: com 980‰ contra 505‰ numa questão, escolher a
mais escura parece serviço ao aluno. O teste que o proíbe existe porque essa aparência é o
argumento, e não a evidência — a folha tem decoração dentro da bolha, e a fatia inteira existe
porque ainda não sabemos como ela se comporta sob câmera.

A segunda mutação mostra por que "em branco" e "indecisa" precisam de testes separados: o caso em
branco continua verde quando o ramo da dúvida some, porque em branco é o que sobra.

### Nota objetiva (tarefa 4.5)

| Defeito introduzido | Quem acusou | O que os outros disseram |
|---|---|---|
| `entry.correct` ignorado: toda alternativa marcada pontua | três em `commonTest` — `folha toda errada tira zero`, `folha mista soma exatamente os acertos` e `a nota reage a uma troca no gabarito` — e, no aparelho, `da_imagem_ate_a_nota_sem_tocar_a_rede`, com `expected:<7> but was:<40>` | os demais verdes — `folha toda correta tira o maximo` entre eles, porque somar tudo e somar certo dão o mesmo 40 |
| Múltipla marcação virando zero definitivo em vez de pendência | duas: `multipla marcacao vira pendencia` e `a pendencia nao vira zero nem acerto` | os outros verdes, incluindo o de questão indecisa — os dois ramos de pendência precisam de teste próprio |
| Conferência do conjunto de itens desligada (`if (false)`) | duas: `conjunto de itens divergente` e `item que a variante nao declara` | os outros verdes |

**A terceira mutação encontrou um teste que passava por acidente, e ele foi corrigido.** Na
primeira execução, `item que a variante nao declara e recusado` continuou verde com a conferência
desligada: o item `q99` também não tem entrada no gabarito, então a recusa vinha da outra guarda,
com uma mensagem que igualmente cita `q99`. O teste afirmava o identificador, e o identificador não
distingue as duas. Ele passou a afirmar também a **frase da divergência** (`nao declarados`), e só
então reage à mutação — a tabela acima já mostra o resultado depois da correção.

É o mesmo padrão que a fatia 3a registrou três vezes com tolerância folgada, aqui numa forma nova:
não é a tolerância que estava larga, é a asserção que era ambígua entre duas guardas. Duas guardas
que pegam o mesmo caso são redundância boa no código e armadilha no teste.

O primeiro defeito da tabela é o que justifica `a nota reage a uma troca no gabarito` existir. O
caso positivo — folha toda correta tirando 40 — é compatível com uma apuração que soma tudo sem
olhar o gabarito. Sem um teste que **mude o gabarito** e exija que a nota mude junto, a suíte
inteira aceitaria uma nota que ignora a resposta certa.

## O limiar, nos três registros que não se conhecem

O número que decide se uma bolha está marcada aparece em três lugares: a constante do aplicativo em
`OmrThreshold.MEDIDO_NA_FATIA_3B`, a margem declarada em ADR-0011, e o corredor que cada folha
publicada traz no `ink_budget`. **Divergir entre eles não quebra teste nenhum** — compila, o golden
não muda, o hash do pacote continua igual, e a folha segue sendo lida com o número errado.

`tools/parity/limiar.mjs` confere os três, por um caminho que não compartilha uma linha com o Kotlin
nem com o engine, e o CI o roda duas vezes: uma para conferir, outra forçando 450 para provar que a
conferência continua capaz de reprovar.

| Defeito introduzido | Quem acusou |
|---|---|
| `--esperado 450` | os três: divergência com o registro do corpus, e o corredor de cada uma das duas folhas |

## O que ainda não está verificado, e por quê

| O que | Por quê |
|---|---|
| O limiar sob outros aparelhos e outras impressoras | O corpus é de um celular e uma impressora. A fatia da câmera, que verá muitos, herda a obrigação de reexaminar `V` e `C` — e mudar o número exigirá ADR novo, como ADR-0007 determina |
| O gabarito em papel da folha 2 | Não veio. As classes dela saíram da medição, com folga de 599‰ dentro de cada questão e as duas fotos concordando letra a letra — não é circular nessa margem, mas é um registro a menos |
| O que faz o QR de `prova2-a` e `prova2-b` não decodificar | Não é resolução, e não é tamanho de arquivo — as duas hipóteses foram medidas e caíram. Nove fotos são amostra pequena demais para concluir, e investigar é fatia própria, não nota de rodapé desta |
