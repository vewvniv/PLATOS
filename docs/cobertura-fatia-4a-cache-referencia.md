# Cobertura de cenários — fatia 4a-cache-referencia (a visão guardada da organização)

Documento **em construção**: a fatia está parcialmente implementada — as seções 1 a 5 do `tasks.md`
fecharam, as telas (6) e a conferência em aparelho (7) não. O registro **por tarefa** vive em
`openspec/changes/slice-4a-cache-referencia/tasks.md`; este documento existe para que um achado seja
encontrável **por assunto**, e a tarefa 8.2 é quem o consolida no fim.

O que está aqui já foi verificado. O que falta está nomeado no fim, e o que ficou sem verificação
automática está nomeado como lacuna — **não como mitigado** (P8).

## Como cada verificação crítica foi vista falhar

### A gravação emendando em vez de substituir (tarefa 1.3)

Visão parcialmente antiga é indistinguível de visão correta para quem lê a tela: uma prova que a
organização já não publica continuaria aparecendo, e o professor não teria sintoma nenhum.

| Defeito introduzido | Quem acusou |
|---|---|
| A gravação **mescla** as provas novas com as antigas em vez de substituir | `gravacao_posterior_substitui_por_inteiro`, com `sobrou prova da visao anterior: [ProvaPublicada(shortId=mat-7a-2026-1…)]` |

Os outros cinco cenários da porta seguiram verdes, e é o esperado: só um deles grava duas vezes.

### A gravação no caminho de falha (tarefa 2.3)

O defeito que importa não é deixar de gravar: é **gravar vazio sobre visão boa**. Uma falha de rede
substituiria o que o aparelho sabia por nada, e o professor sem rede passaria a não ver as provas que
via um minuto antes.

| Defeito introduzido | Quem acusou |
|---|---|
| A gravação acontece também nos dois ramos de falha (`SemRede` e `Falhou`) | `listagem_que_falha_nao_grava_e_nao_apaga_o_que_havia`, com `uma listagem que falhou mexeu na visao guardada` — 22 testes, 1 vermelho |

`listagem_que_chega_grava_a_visao` continuou verde, que é a disjunção que a tarefa pedia.

### Os dois casos que terminavam iguais (tarefa 3.3)

"O servidor não respondeu" e "o servidor respondeu e o vínculo não está lá" tinham o mesmo desfecho
até esta fatia. Duas mutações, e os conjuntos saíram **disjuntos** — cada camada tem a sua:

| Defeito introduzido | Quem acusou | Quem não acusou |
|---|---|---|
| "não respondeu" volta a terminar em `SemOrganizacao` | `sem_rede_com_visao_guardada_abre_a_tela_de_trabalho` | o cenário do vínculo revogado |
| "respondeu sem a organização" passa a cair na visão guardada | `servidor_que_responde_sem_a_organizacao_derruba_a_escolha_mesmo_com_visao` | o cenário do arranque sem rede |

A segunda mutação **passou pelo teste herdado da 4a-zero sem arranhá-lo**: ele roda sem visão
guardada, então a proteção removida não muda o desfecho dele. Cobertura antiga verde sob defeito novo
é sinal de que ela falava de outra coisa (P16).

### A ordem entre gravar e ler, que nenhuma conferência protegia (tarefa 4.3)

**O achado de método desta fatia, e ele veio de uma mutação que não morde.** A mutação declarada —
mascarar a lista vazia *depois* de montar o estado — saiu **inerte**: quando o mascaramento vai ler,
a visão já foi substituída pela lista vazia que chegou. O que impede o defeito não é uma conferência,
é a **ordem entre gravar e ler**, e ela não estava escrita em lugar nenhum até virar a decisão 6 do
`design.md`.

Substituída pelo defeito plausível de verdade — "não perca a lista": lista vazia que chegou, havendo
visão guardada, não substitui a visão. Remedido em 2026-09-10 com totais e mensagens:

| Defeito introduzido | Vermelhos | Verde |
|---|---|---|
| a listagem sem resposta não consulta a visão | **3**, todos do caminho sem resposta | `lista_vazia_que_chegou_nao_e_mascarada_pela_visao` |
| lista vazia que chegou não substitui a visão | **1**: `lista_vazia_que_chegou_nao_e_mascarada_pela_visao`, com `expected: <SemProvaPublicada(procedencia=Fresca)> but was: <Escolhendo(…, procedencia=Cacheada(vistaEm=1757000000000))>` | as três de cima |
| a mesma coisa **sem** a guarda de "havendo visão" | **4** — e eu havia declarado três | as três de cima |

O quarto vermelho da terceira mutação é `listagem_sem_rede_nao_e_lista_vazia`, que compara os dois
estados diretamente: sem a guarda, lista vazia que chega sem visão termina em
`ListagemFalhou(SEM_REDE)`, o mesmo estado de "não respondeu", e os dois colapsam. **A declaração era
incompleta e fica registrada como incompleta**, não reescrita como se tivesse sido prevista.

A proteção do caso "respondeu, e a lista veio vazia" descansa em **quatro** cenários, não em um: o
"só um" da mutação do meio mede a precisão dela, não a estreiteza da cobertura.

### A revogação observada, sem levar o cache (tarefas 5.3 e 5.4)

A escolha caía desde a 4a-zero. Cair sem levar a visão e os pacotes deixava no aparelho o **gabarito**
de uma organização já revogada — e o gabarito não aparece em tela nenhuma, então ninguém tem como
notar que ficou. Sem este apagamento, "visão sem prazo de validade" (decisão 2 do `design.md`) seria
"para sempre".

| Defeito introduzido | Vermelhos | Verde |
|---|---|---|
| `revogar` apaga só a visão | **2**: `revogacao_observada_apaga_tambem_os_pacotes_da_organizacao` (`a revogacao deixou o gabarito em cache sob a organizacao revogada ==> expected: <[org-1]> but was: <[]>`) e o cenário da lista vazia | `revogacao_observada_apaga_a_visao_da_organizacao` |
| lista vazia volta a não revogar nada | **1**: `resposta_sem_organizacao_nenhuma_tambem_e_revogacao` (`a lista vazia nao apagou a visao`) | os dois do ramo não-vazio |
| `sair()` volta a deixar a visão no disco | **1**: `sair_apaga_a_visao_junto_com_o_resto` (`sair nao apagou a visao da organizacao ativa`) | os três de revogação |

O apagamento de `sair` e o de `revogar` caem em conjuntos disjuntos. E há um canário que sustenta os
outros: `vinculo_que_continua_valendo_nao_apaga_nem_visao_nem_pacote` — sem ele, "apagar a cada
consulta bem-sucedida" passaria em todos os cenários de revogação, porque eles afirmam que o
apagamento **aconteceu**, nunca que ele foi seletivo (P13).

## O que ficou sem verificação automática, e por quê

### O vermelho que não diz o motivo (tarefa 4.3, achado da remedição)

`provas_com_pacote_guardado_sao_distinguiveis_das_sem` recusa pela **forma** do estado, e não pela
proteção que ele nomeia. Sob a mutação "a listagem sem resposta não consulta a visão", ele estoura
`java.lang.ClassCastException: class EstadoDaProva$ListagemFalhou cannot be cast to class
EstadoDaProva$Escolhendo` no preâmbulo — `preparo.state as EstadoDaProva.Escolhendo` — **antes** de
comparar as marcas de pacote guardado.

O vermelho é real e a asserção de presença é boa; o preâmbulo é que não diz nada. A P9 pede que a
asserção confira o **motivo** da recusa, e sob qualquer mutação que mude o tipo do estado este
cenário devolve a mesma exceção de cast — indistinguível entre "a presença foi marcada errada" e "o
estado nem chegou a ser uma escolha".

**Não é mitigado, é conhecido.** O conserto, quando vier, é o cenário afirmar o tipo do estado com
mensagem própria antes de projetar as marcas — e ele **não** entra nesta fatia: não há defeito de
comportamento a corrigir, e mexer nele agora seria refatoração fora de escopo (P19). Fica com dono
(esta lacuna) e fatia-limite (a próxima que tocar `PreparoDaProva`).

### O isolamento fraco da distinção de pacote guardado (tarefa 4.4)

A mutação "apresentar todas as provas como disponíveis" derrubou **cinco** cenários, e não um: o da
distinção mais quatro que comparam o estado apresentado por igualdade exata. A presença é **parte do
estado**, e o estilo desta base é comparar o estado inteiro, então uma marca errada quebra em todo
lugar que afirma o estado.

Os cinco apontam para a **mesma** proteção — não há ambiguidade sobre qual segurou —, mas esta mutação
não distingue camadas como as da 4.3 distinguem. Fica registrado como isolamento **fraco**, e não como
isolamento.

### A declaração que não foi prévia (tarefa 4.3)

A mutação substituta ("não perca a lista") foi formulada **depois** de a declarada sair inerte, e o
registro não afirma o contrário. A tabela de conjuntos esperados da primeira passada entrou na árvore
no mesmo commit que os resultados dela (`fe88bcc`), então a ordem "declarado antes de mutar" é
afirmação do texto, sem âncora no histórico. A remedição de 2026-09-10 corrigiu isso pela forma:
declaração num commit (`aad5ba4`), resultados no seguinte (`0ad3254`).

## O que ainda não está verificado

| O que | Por quê |
|---|---|
| O que as telas **desenham** (tarefas 6.1 a 6.4) | Ainda não implementado. A escolha da frase e do estado mora fora do `@Composable`, e é lá que os testes chegam; o que nenhum teste desta base alcança é a tela **ignorar o parâmetro** — o defeito que produziu 9b.1 e 9b.2 na fatia 4a. Paga-se na seção 7, e não com afirmação de que está mitigado |
| Sobreviver à **morte do processo** | Os três testes instrumentados da tarefa 1.4 gravam sob o `filesDir` de verdade e releem por outra instância, o que exclui leitura de memória. Matar o processo e reabrir sem rede é conferência de aparelho: tarefa 7.1 |
| A revogação **no disco** (tarefa 7.3) | O apagamento está verificado na JVM sobre a porta. Que a visão e os pacotes somem do `filesDir` depois de o vínculo cair no banco só o aparelho mostra — inspecionando o disco, e não a tela |
| O **comando cheio do CI** (tarefa 8.1) | O que rodou até aqui é `:apps:android:testDebugUnitTest`. `./gradlew build` mais `connectedDebugAndroidTest` **sem filtro** é a 8.1, e o número vem do relatório, não do código de saída do Gradle (P5) |
