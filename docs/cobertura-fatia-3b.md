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

> **Estado:** os grupos 1 a 5 das tarefas estão fechados. O limiar ainda **não** existe como
> constante do aplicativo: ele entra por parâmetro em todo lugar, e o número sai do corpus
> fotografado pela regra de ADR-0011. O que depende do corpus está na última seção.

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

## O que ainda não está verificado, e por quê

| O que | Por quê |
|---|---|
| O limiar `T` e a margem `M` como constantes do aplicativo | Não existem. O número sai do corpus fotografado pela regra de ADR-0011, e fixá-lo antes é o que ADR-0007 proíbe. Até lá cada teste passa o seu, e nenhum deles é o do produto |
| O corredor de 200 a 400 sob câmera | É o que o corpus existe para medir. A digitalização versionada é de scanner, com faixa dinâmica comprimida (§321 do protocolo): ela prova a composição, não o meio |
| A nota sobre uma folha fotografada | Tarefa 8.3, depois do corpus |
| O passo de CI que prova que a verificação do limiar continua capaz de falhar | Tarefa 8.4, depois de `T` existir |
