# Cobertura de cenários — fatia 4a-cache-referencia (a visão guardada da organização)

Documento **em construção**: as seções 1 a 6 do `tasks.md` fecharam, a conferência em aparelho (7)
fechou as tarefas 7.1 a 7.3 em 2026-09-10, e falta a verificação final (8). O registro **por tarefa** vive em
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

## A conferência em aparelho de 2026-09-10

Telefone `2511FPC34G`, Android 16 (API 36), serviço real em `platos-api-latest.onrender.com`.
Instrumento: `adb` para processo, rede e disco; `uiautomator dump` para ler a tela; `screencap` para
o que é visual. **`adb shell input` está proibido neste aparelho** — a política da Xiaomi/HyperOS
exige "Depuração USB (configurações de segurança)", que pede conta Mi —, então os toques foram
manuais e a leitura foi automática. Isso não muda o método: o §14 já é roteiro manual.

**O acordar do serviço não é cerimônia.** Duas vezes nesta sessão o `/health` levou ~44 s (14:00:29Z
e 15:28Z) contra ~0,2 s na chamada seguinte: o serviço dorme, e uma consulta contra serviço frio
chega ao aplicativo como **falta de rede**. Sem acordá-lo antes, a 7.1 passaria pelo motivo errado
(cold start lido como cache funcionando) e a 7.3 **falharia** pelo motivo errado (falta de rede não
revoga, cai na visão).

| O que | Como foi observado | Resultado |
|---|---|---|
| O pacote no disco é o que o nome diz | `adb exec-out … cat` + `sha256sum` **do host**, contra a fixture do repositório e contra o nome do arquivo | os três `26612ad5…909a`, 101.618 bytes. Oráculo independente do `MessageDigest` do aplicativo (P4), e o contrário do que P2 proíbe — nome certo não é prova de conteúdo |
| O processo morreu | `pidof` antes e depois do `force-stop` | 13980 → vazio |
| Não havia rede | `cmd connectivity airplane-mode` **e** `ping 8.8.8.8` de dentro do aparelho | `enabled` e `Network is unreachable`. A asserção é sobre a pilha de rede, não sobre o ícone |
| A tela de trabalho veio da visão | `am start` em pid **novo** (16591), `uiautomator` | `SEM CONEXAO` · `visto em 10/09/2026 as 16:10` · `Escola de Teste` |
| O instante apresentado é o do disco | `vista_em` do JSON convertido em Python com `zoneinfo` | `1789049423447` → 16:10:23 `Europe/Madrid` = o texto da tela, ao minuto |
| A marca é **visual** | `screencap` | caixa com fundo e borda, rótulo em maiúsculas e idade embaixo — não frase no meio do texto |
| Atualizar sem rede não esvazia | `uiautomator` + `ls` no disco | selo e nome ficaram, aviso apareceu **com a segunda frase**, idade não envelheceu, e a visão no disco ficou intocada (493 B, mesmo `vista_em`) |
| A distinção da 4.2, no mundo | `uiautomator` na tela de escolha | `slice-1 · baixada` contra `slice-2 · precisa de rede` — assimétrica |
| A câmera abre sem rede | `mCurrentFocus` + `screencap` | `ScanActivity`, preview ao vivo, às 14:27:27Z, **no mesmo pid 16591** |
| A âncora aguentou | reconferida **depois** do desfecho (P3) | modo avião ainda `enabled`, `Network is unreachable`, mesmo pid |
| A revogação leva os dois | `delete from membership` + `find files -type f` | restou **só** `files/profileInstalled`: a visão e o **diretório** `packages/<org>/` inteiro foram |

Toda a cadeia — arranque, tela de trabalho, atualizar frustrado, lista, câmera — aconteceu **no
processo 16591**, que nasceu já em modo avião. Não é que ele não usou a rede: ela não existiu em
nenhum instante da vida dele.

**Um erro de sequenciamento, registrado porque custou uma rodada:** na primeira tentativa da 7.3 eu
mandei o SQL de restauração na mesma mensagem em que pedi o login. O `membership` voltou antes de o
aplicativo consultar, e a rodada não mediu revogação nenhuma — a tela mostrou a escola fresca, o que
é o comportamento **correto** para vínculo válido. Antes de culpar o código eu conferi que
`/me/organizations` lista por join com `membership`; se listasse por outro caminho, o `delete` nunca
teria sido revogação. Refeito na ordem certa, com o SQL de restauração entregue **depois** da
conferência.

**Uma condição de vigia fraca, pela mesma família de erro que a P3 descreve:** um dos observadores
disparava em "saiu da tela de entrada", e `Buscando suas organizacoes` satisfaz isso — ele pegou o
`ConsultandoScreen` no ar e reportou disco cheio, como se a revogação não tivesse apagado nada. O
gatilho precisa ser o estado **terminal**, não a saída do anterior.

## Achados fora do escopo, com dono e fatia-limite

Nenhum destes é defeito desta fatia, e nenhum foi consertado aqui (P19). Ficam nomeados porque
achado sem dono é achado que ninguém procura.

| Achado | Onde | Fatia-limite |
|---|---|---|
| **Expiração de sessão não apaga o cache** — e está certo: quem expirou é o token, não o vínculo. Visto no disco às 15:29Z com token de 82 min. Não estava escrito em lugar nenhum | `DeviceSession.aoConsultarOrganizacoes`, ramo `SessaoExpirada` | nenhuma: é decisão a **documentar**, não a mudar. Cabe numa linha da spec de `device-session` |
| **O título "Escolha a prova" fica sob a barra de status** — a `Column` não tem inset de topo. Cosmético, pré-existente (o título já nascia no topo antes desta fatia); na tela de trabalho não aparece porque o conteúdo é centralizado | `EscolhaDaProvaScreen` | a próxima que tocar essa tela |
| **Não há troca de organização sem sair** — a tela de trabalho oferece escanear, atualizar e sair. Quem tem duas escolas precisa fazer logout para trocar. Exposto por acidente: a revogação derrubou a escolha, sobrou uma organização, ela foi guardada, e o aparelho ficou preso nela mesmo depois de o vínculo voltar | `TrabalhoScreen` / `DeviceSession.escolher` | 4b, quando professor com duas escolas deixa de ser hipótese |
| **`connectedDebugAndroidTest` desinstala o aplicativo ao terminar** — e com ele vai o `filesDir`. É o que explica o cache que "sumiu sem que ninguém observasse" na 0.1, e é P3 em estado puro: estado que mora no instrumento não avisa quando desaparece | `apps/android/build.gradle.kts` (a suíte instrumentada) | nenhuma: é para o protocolo, e entra no §14.1 como pré-condição |

## O que ficou sem verificação automática, e por quê

### A tela ignorando o parâmetro (tarefa 6.4)

A decisão de texto e de formato saiu para funções puras — `marcaDeLeitura` e `avisoDeAtualizacao`,
com sete cenários de JVM — e as três telas recebem tudo pronto. Isso protege a **decisão**, e não
protege o **desenho**: nenhum teste desta base entra num `@Composable`.

Os quatro defeitos que ficam descobertos, nomeados porque lacuna sem nome é lacuna que ninguém
procura:

| Defeito de tela | Por que nenhum teste pega | Onde se paga |
|---|---|---|
| `SeloDeLeitura` não ser chamado, ou o `if (marca != null)` invertido | A função pura devolve a marca certa; quem decide desenhá-la é a tela | 7.2, por `uiautomator` |
| O selo desenhado com `marca.rotulo` no lugar de `marca.idade` — ou só o rótulo, sem a idade | As duas cadeias estão certas no tipo; qual delas vai para a tela é escolha do `@Composable` | 7.2 |
| O aviso de atualização não desenhado, ou desenhado sem a segunda frase | A lista branca de `MarcaDeLeituraTest` prende o **texto**, não a presença dele na tela | 7.2 |
| O botão `Atualizar` ligado a `abrirSessao` em vez de `atualizarSessao` | **O mais provável dos quatro**, e o único que não é esquecimento: as duas funções existem, fazem quase a mesma coisa, e a diferença — passar ou não por `Consultando` — só aparece na tela, em movimento | 7.2, conferindo que a lista não pisca para vazio |

O último é o mesmo defeito de fiação que produziu 9b.1 e 9b.2 na fatia 4a: máquina certa, tela certa,
ligação errada. **Não é mitigado, é conhecido.**

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
| O que as telas **desenham** (tarefa 6.4) | Implementado, e a lacuna tem seção própria acima: os quatro defeitos de tela que esta fatia deixa descobertos, nomeados um a um. Paga-se na tarefa 7.2 |
| Sobreviver à **morte do processo** | Os três testes instrumentados da tarefa 1.4 gravam sob o `filesDir` de verdade e releem por outra instância, o que exclui leitura de memória. Matar o processo e reabrir sem rede é conferência de aparelho: tarefa 7.1 |
| A revogação **no disco** (tarefa 7.3) | O apagamento está verificado na JVM sobre a porta. Que a visão e os pacotes somem do `filesDir` depois de o vínculo cair no banco só o aparelho mostra — inspecionando o disco, e não a tela |
| O **comando cheio do CI** (tarefa 8.1) | O que rodou até aqui é `:apps:android:testDebugUnitTest`. `./gradlew build` mais `connectedDebugAndroidTest` **sem filtro** é a 8.1, e o número vem do relatório, não do código de saída do Gradle (P5) |
