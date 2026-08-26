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

> Documento em construção — a fatia está em implementação. As seções aparecem à medida que as
> tarefas fecham.

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
| `entry.correct` ignorado: toda alternativa marcada pontua | três: `folha toda errada tira zero`, `folha mista soma exatamente os acertos` e `a nota reage a uma troca no gabarito` | os outros catorze verdes — `folha toda correta tira o maximo` entre eles, porque somar tudo e somar certo dão o mesmo 40 |
| Múltipla marcação virando zero definitivo em vez de pendência | duas: `multipla marcacao vira pendencia` e `a pendencia nao vira zero nem acerto` | os outros quinze verdes, incluindo o de questão indecisa — os dois ramos de pendência precisam de teste próprio |
| Conferência do conjunto de itens desligada (`if (false)`) | duas: `conjunto de itens divergente` e `item que a variante nao declara` | os outros quinze verdes |

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
