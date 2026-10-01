## Context

O motivo está em `proposal.md` (Why). Aqui fica o estado atual que molda o desenho, com o tipo de cada afirmação (P6 do `rigorous.md`): **lido** (no código, hoje), **conferido** (por ferramenta) ou **suposto**.

- **O quadro morre antes da sessão.** `CameraFrameAnalyzer.analyze` converte o `ImageProxy` em `Mat`, chama `SheetReader.analyze` e libera o `Mat` no `finally`; só então `entrega(FrameOutcome)` chega à `ScanSession`, na thread principal. `FrameOutcome` não carrega pixel. *Lido em `CameraFrameAnalyzer.kt:33-40` e `ScanActivity.kt` (`entrega`).*
- **A análise para sozinha.** `deveAnalisar` só é verdadeiro em `Searching` e `NotRead`; o estado de uma prova com discursiva é `ProvaComDiscursiva`, que não é nenhum dos dois. Logo, depois do primeiro quadro que reconhece algo, nenhum quadro é analisado até o professor tocar em "Escanear outra folha" (`session.resume()`, que **não** apaga o caderno). Por isso há no máximo **uma rodada de análise por toque**, e o recorte que a 5c-0 recusou fazer "a cada quadro" não tem a frequência que o desenho dela temia. *Lido em `ScanActivity.kt` (`deveAnalisar`) e `ScanSession.resume`. É propriedade que o desenho usa, e por isso a tarefa 2.3 a prende por teste (P16).*
- **`SheetReader.analyze` é, por spec, sem efeito e sem imagem** (`capture-omr`, "A região discursiva é reconhecida, e não medida": "Reconhecer não recorta"). E `RecorteDaResposta.recortar` é sem efeito e determinístico. *Lido nas specs.*
- **O caderno é JSON num registro do Room** (`CadernoEntity.corpo`, `caderno.db`, uma linha por `(organizacao, exam_id)`), guardado em `onStop` e lido **uma vez, fora da thread principal**, por `lerCadernoEmAndamento` dentro de `lifecycleScope.launch` em `ScanActivity.onCreate`, com a sessão montada depois (`montar`), desde a mudança `o-caderno-e-lido-fora-do-fio-principal` (#82). Um campo novo com valor-padrão em `@Serializable` decodifica o JSON antigo; o `Json` padrão **não** ignora chave desconhecida. *Lido; o decode de JSON antigo é conferido na tarefa 3.1.*
- **`RectifiedRegion` não expõe os pixels** (`pixels` é privado). Hoje só se lê `luminanceAt`. *Lido.*
- **Backup e transferência já são negados por domínio** (`regras_de_extracao_de_dados.xml`, `path="."` em quatro domínios, `allowBackup="false"`); a ADR/cobertura de `transferencia-entre-aparelhos` **mediu** isso em aparelho real. Um diretório novo sob `files/` cai na regra sem ser listado. *Lido.*
- **Não há classe `Application`**; a porta de entrada do aplicativo é `SessaoActivity` (launcher). *Lido no manifesto.*
- **§16 já registra que sair preserva o pendente** ("A política §10.8 diverge do comportamento: sair e a revogação preservam o pendente", `antes-de:publicacao-da-politica`). A resposta guardada segue a mesma decisão, e a linha é ampliada no archive. *Lido.*

## Goals / Non-Goals

**Goals:**
- O recorte da 5c-0 é chamado pela câmera de verdade, sobre o quadro, e o que sai dele sobrevive ao quadro.
- Nenhum caderno referencia imagem que não existe, em nenhuma ordem de falha (queda entre gravar e guardar, disco cheio, eliminação por prazo).
- O prazo de 30 dias é executável e testável sem esperar 30 dias.
- A sessão continua testável na JVM, sem disco e sem OpenCV.

**Non-Goals:**
- Nota, fato de correção, envio (5c-2). Envio de imagem. Corpus.
- Cifrar a imagem em repouso, varrer em segundo plano, recortar em cor, finalizar caderno incompleto.
- Medir o recorte em papel ou com letra real (§16, linhas `6`).

## Decisions

### 1. O recorte é tirado no analisador, depois de `analyze`, e só do que falta

```
CameraX ──▶ CameraFrameAnalyzer
              gray = FrameGray.of(image)
              outcome = SheetReader.analyze(gray, map, th)       ← sem efeito, sem imagem
              para cada Reconhecida sem resposta (aluno, região):
                  SheetReader.recortar(gray, map, região)         ← 5c-0, sem efeito
                  Recortado → PNG → RespostasEmArquivo.gravar     ← efeito, fora do pipeline
              gray.release()
              entrega(QuadroAnalisado(outcome, respostas))        ← main thread
                                   │
                                   ▼
                        ScanSession.onFrame(outcome, respostas)
```

`CameraFrameAnalyzer` ganha um predicado `jaTemResposta(aluno, regionIndex): Boolean`, e `entrega` passa a receber um `QuadroAnalisado(resultado: FrameOutcome, respostas: Map<Int, RespostaDoQuadro>)`. O predicado lê um **instantâneo** do caderno: a `ScanActivity` guarda `@Volatile var cadernoVisivel: Caderno?`, atualizado na thread principal no mesmo ponto em que `state` é atualizado. `Caderno` é `data class` imutável, e publicá-lo por `@Volatile` é o suficiente; a sessão, que não é thread-safe, **não** é lida da thread de análise. **O instantâneo nasce em `montar`**, com o caderno retomado do disco, e não só nos quadros: sem isso, o analisador de uma Activity reaberta com caderno que já tem respostas acharia que nenhuma região tem resposta e recortaria tudo de novo. Também é atualizado em `refazer`.

O predicado é **por aluno e por região**: o caderno corrente é do aluno A com a região 2 guardada, e a folha do aluno B mostra a região 2 — pede-se o recorte. Sem o aluno, a folha de B herdaria a imagem de A, que é exatamente a falha que o requisito da spec nomeia.

**Por que aqui, e não em outro lugar.** É o único ponto em que o quadro e a regra de "falta resposta" existem ao mesmo tempo. Dentro de `SheetReader.analyze` quebraria a spec de 5c-0 (sem efeito) e traria de volta o custo por quadro que ela recusou. Na `ScanSession`, não há quadro. Na `ScanActivity`, também não: o `finally` já o liberou.

*Alternativas rejeitadas.* (a) Guardar o `Mat` do quadro para recortar depois: retém ~2,7 MB por quadro na memória até alguém decidir, e a decisão é da sessão, na outra thread. (b) Recortar toda região reconhecida e deixar a sessão descartar: paga o warp e o disco para imagem que a sessão joga fora, e deixa arquivo órfão como regra, e não como exceção.

### 2. A gravação é da thread de análise, antes da entrega, e é atômica

O PNG é escrito em arquivo temporário do mesmo diretório e renomeado para `<uuid>.png` só depois de inteiro; a resposta só entra no `QuadroAnalisado` como `Guardada(arquivo, capturadaEm, residuoMaxMm, desvio)` depois do `rename`. Falha de escrita (disco cheio, exceção de E/S) vira `Recusada(motivo)` e o temporário é apagado — nunca exceção que derruba o laço da câmera, nunca `Guardada` sem arquivo. O nome é UUID, num diretório só (`filesDir/respostas/`): sem aluno, prova nem organização no caminho, porque o vínculo vive no caderno, que já é chaveado por `(organizacao, exam_id)`.

**PNG, por `Imgcodecs.imencode`** (OpenCV 4.11.0, `org.opencv:opencv`, já está no aplicativo; *nenhuma tecnologia nova*). **Suposto, e não medido:** nenhum código do aplicativo usa `Imgcodecs` hoje (só os testes usam `BitmapFactory`), e que o artefato traga o codec PNG é suposição. A tarefa 2.1 **mede isso primeiro**; se faltar, a saída é `Bitmap.compress(PNG)` — API do Android, sem dependência nova, com o PNG maior por ser colorido — e a escolha não muda spec nem as outras tarefas. Sem perda: a imagem guardada é **bit a bit** a que o recorte entregou, e o teste de ida e volta usa isso como oráculo (P4: o decodificador é o do Android, independente do codificador). `RectifiedRegion` ganha uma cópia dos pixels (`paraBytes()`), que é o único lugar em que o tipo deixa de esconder o buffer. **[Medido em 2026-10-01 12:39 (+02:00), no apply (tarefa 2.1; o "suposto" acima fica):** `Imgcodecs.imencode(".png", …)` **existe e codifica PNG** no artefato `org.opencv:opencv` 4.11.0 do aplicativo, nos dois aparelhos (emulador `platos-atd34`, Android 14, e Xiaomi `2511FPC34G`, Android 16), e a ida e volta é bit a bit pelo `BitmapFactory`. O plano de reserva `Bitmap.compress(PNG)` **não foi necessário**. Tamanho medido em 870×1000 px: 3 079 bytes em branco e 459 686 bytes com ruído sintético.**]**

`RespostasEmArquivo` implementa uma interface `RespostasGuardadas` (`gravar`, `ler`, `existe`, `listar`, `eliminar`), no mesmo padrão de `CadernosGuardados` e `PacotesGuardados`: a normalização da leitura e a varredura são testáveis na JVM sem disco, e esse é o consumidor que justifica a interface (P18).

`capturadaEm` vem de um relógio **injetado** no analisador (em produção, `System.currentTimeMillis`). É a mesma razão pela qual `captureId` e o instante são cunhados por quem chama e não pela sessão: a `ScanSession` fica determinística.

*Alternativas rejeitadas.* (a) **JPEG**: com perda, e a imagem é o que a 5c-2 corrige e a fatia 8 um dia envia; comprimir manuscrito fino com perda é decisão de acurácia que ninguém tomou. (b) **Gravar na thread principal, depois da entrega**: abre a janela em que o caderno diz "capturada" e o arquivo ainda não existe, e o Room/StrictMode do projeto já recusou E/S ali uma vez (`gravarEAgendar`). (c) **Hash no caderno**: a escrita atômica já exclui o arquivo truncado por queda; hash compraria a detecção de corrupção de disco, que ninguém pediu (P18).

### 3. A sessão decide, e não toca disco

`ScanSession.onFrame(outcome, respostas = emptyMap())`: o valor-padrão mantém a **compilação** de cada chamada de teste atual. ~~O valor-padrão mantém cada chamada de teste atual como está.~~ **[Errado — registro de 2026-10-01 12:39 (+02:00), no apply (P7).** O comportamento muda: sem entrada no mapa, a discursiva reconhecida fica "com problema" (linha 4 da tabela), então as chamadas de teste que esperam "capturada" passaram a entregar as respostas, por um auxiliar de teste (`onFrameGuardando`, que dá `Guardada` a cada discursiva reconhecida do quadro). Nenhuma asserção mudou.**]** Na discursiva reconhecida:

| O quadro diz | A região fica |
|---|---|
| `Guardada` e a região ainda não tem resposta | **capturada**, com `RespostaGuardada` no caderno |
| `Guardada` e a região já tem resposta | como estava (a primeira aceita fica; o arquivo novo é um órfão da eliminação) |
| `Recusada(motivo)` | **com problema**, com o motivo, a menos que já esteja capturada |
| nenhuma entrada no mapa, e a região ainda não tem resposta | **com problema**, `"o recorte nao foi pedido"` — nunca capturada em silêncio |
| nenhuma entrada no mapa, e a região já tem resposta | como estava: o analisador não pediu porque já havia (decisão 1) |

A última linha existe para o teste do contrato (P16): se a fiação do analisador se perder, a região não vira capturada sem imagem; vira um problema visível.

A **condição de completude não muda de texto** (`capturadas == esperadas && parcial é Scored`): como capturada agora implica resposta, a entrega da 5b-4 passa a exigir as respostas por construção, sem um segundo caminho que pudesse divergir do primeiro. `entregue` segue sendo marcado uma vez e **não** é limpo por refazer.

`RegiaoDoCaderno` ganha `resposta: RespostaGuardada? = null` (nome do arquivo, `capturadaEm`, `desvioSinalizado: Boolean`, `foraPpm: Int`). Guardam-se os dois números do desvio, e não o `DesvioDaResposta` inteiro: a classificação é do domínio (regra 7 do `CLAUDE.md`) e já foi feita; `dentro`/`fora` em centésimos de mm² não têm leitor.

**Sem `require` de invariante na construção** ("discursiva capturada ⇒ resposta"): um caderno antigo, guardado antes desta mudança, violaria a invariante e o decode derrubaria o caderno inteiro. A normalização acontece **na leitura** (decisão 5).

### 4. O caderno referencia; a tela vê; refazer é uma ação da sessão

`ScanScreen` ganha, no indicador de cada região discursiva capturada, um toque que abre `RespostaTela(imagem, rotulo, desvio, onRefazer, onVoltar)` em tela cheia. A imagem é decodificada por `BitmapFactory.decodeFile` fora da thread principal. O aviso de desvio é texto fixo, definido num lugar só (ao lado de `ScanState.ProvaComDiscursiva.AVISO`, que também muda: "nenhum resultado foi gravado; as respostas capturadas ficam neste aparelho"), e não altera a imagem. Como a análise já está parada em `ProvaComDiscursiva`, a câmera e a tela cheia não disputam nada. **A tela é testada pela árvore de acessibilidade** (`AccessibilityNodeInfo`, `ACTION_CLICK`, `boundsInScreen`), como o teste da Activity, e **não** por `compose-ui-test`: essa biblioteca não está no catálogo, e adicioná-la é mudança de dependência — exige aviso (P22) e justificativa aqui (regra 4 do `CLAUDE.md`), e o que ela compraria, tocar e ler nós da tela, a árvore de acessibilidade já entrega.

`ScanSession.refazer(regionIndex)`: a região volta a `NaoVista`, `resposta = null`, e devolve o nome do arquivo para a `ScanActivity` eliminar **na hora**. Eliminar antes de o caderno ser guardado é seguro por construção: o caderno em disco que ainda aponte para o arquivo é lido com a região não vista (decisão 5), que é exatamente o que refazer queria. A eliminação por prazo é a rede, e não o caminho.

### 5. O prazo de 30 dias é uma função pura sobre listas, rodada em dois pontos

`RetencaoDaResposta.arquivosAEliminar(noDisco: List<String>, referenciadas: Map<String, Long>, agora: Long): List<String>` — função pura, na JVM, sem disco: elimina o que **nenhum caderno referencia** e o que tem `agora − capturadaEm ≥ 30 dias`. A constante `PRAZO_DIAS = 30` tem dono único ali (P28), com a fonte citada (política §10.8). `≥` e não `>`: a classe H diz "em até 30 dias", e o dia 30 já é o limite; o contato de borda é pinado por teste.

`referenciadas` sai de todos os cadernos guardados — `CadernosGuardados` ganha `todos()`, uma consulta Room sem filtro de organização, porque o aparelho é compartilhado e o prazo não pertence à sessão corrente. A varredura roda **antes da câmera abrir e antes da leitura do caderno**, em dois pontos, **sempre fora da thread principal** (a consulta `todos()` é Room): em `SessaoActivity.onCreate` (porta de entrada), por `lifecycleScope.launch { withContext(Dispatchers.IO) { … } }`, como o arquivo já faz com `pendentes.quantosPendentes`; e em `ScanActivity`, **dentro** do mesmo bloco de IO que lê o caderno, em sequência — varrer, ler, normalizar —, por uma função nova `retomarCadernoEmAndamento(...)`: `Deferred<Caderno?>` que **compõe** `lerCadernoEmAndamento`, e que a `ScanActivity` passa a usar no lugar dela. Varrer, ler e normalizar no mesmo bloco garante a ordem que a spec pede, sem o `onCreate` tocar em Room.

**O que isto não garante, e por que não importa.** As duas varreduras podem rodar perto uma da outra, ou com o analisador já gravando (Activity reaberta depressa). Uma varredura que elimine cedo demais um arquivo que ainda não foi referenciado é **inofensiva por construção**: a região volta a não vista na leitura (normalização), e uma gravação que perde o arquivo no meio vira `Recusada` (decisão 2). Por isso não há trava entre a varredura e o analisador, nem "carência de órfão": o custo do pior caso é refazer uma captura, e não imagem em branco nem queda. A varredura trata como órfão **todo** arquivo de `respostas/` que nenhum caderno referencia, inclusive o temporário de uma gravação interrompida.

**Falha ao eliminar não impede a abertura.** Eliminar um arquivo é por arquivo, com a exceção de E/S capturada e contada; o escaneamento abre do mesmo jeito, e o arquivo fica para a próxima varredura. A função devolve quantos não conseguiu eliminar, e o teste a exercita; em produção ninguém vê esse número (fica no log), e isso é lacuna conhecida (P8).

**A leitura normaliza:** `retomarCadernoEmAndamento` devolve o caderno com toda região cuja resposta não existe em disco — ou cujo estado é capturada discursiva sem resposta (caderno de antes desta mudança) — como `NaoVista`. A normalização em si é uma função pura sobre `Caderno` e um predicado de existência, testável na JVM. `retomarCadernoEmAndamento` é a única via de leitura que a `ScanActivity` usa; é ela que garante "nenhum caderno referencia imagem que não existe". **[Registro de 2026-10-01 12:39 (+02:00), no apply (4.2):** o invariante da ordem é **normalizar depois de varrer**. Ler antes de varrer é inofensivo, desde que a normalização venha depois da varredura: a mutação "ler antes de varrer, normalizar depois" **não derrubou teste nenhum**. A que derrubou foi "ler e normalizar antes de varrer" (a resposta de 31 dias voltaria capturada com o arquivo eliminado em seguida). O texto acima ("varrer, ler, normalizar") continua certo.**]**

*Alternativas rejeitadas.* (a) **`WorkManager` periódico**: infraestrutura nova para uma fatia cujo único gatilho certo (o da nota) só nasce na 5c-2; o custo é a lacuna declarada abaixo. (b) **`lastModified` do arquivo como relógio**: muda com cópia e com restauração; o dono do instante da captura é o caderno. (c) **Eliminar na troca de caderno, em vez de na abertura**: exigiria a sessão devolver "descartadas" a cada substituição, e a eliminação por ausência de referência já cobre o caso com um mecanismo só.

### 6. Onde cada coisa mora

| Peça | Módulo | Por quê |
|---|---|---|
| `RespostaGuardada`, `RegiaoDoCaderno.resposta`, `ScanSession` (capturada, refazer) | `apps/android` (`scan/`) | Já moram ali; Kotlin puro, sem Android |
| `RetencaoDaResposta` (regra e constante) | `apps/android` (`scan/`) | Único consumidor; não há regra de negócio compartilhada com a web (P18) |
| `RespostasEmArquivo` (gravar atômico, ler, listar, eliminar) | `apps/android` (`scan/`) | É a fronteira com o disco |
| Recorte, `paraBytes()`, PNG | `apps/android` (`vision/`, `omr/`) | Único lugar que fala com o OpenCV |
| Domínio KMP, API, banco, fio | — | **Nada muda** |

Contrato antes de consumidor (regra 1): `RespostaGuardada` e o parâmetro de `onFrame` entram antes do analisador e da tela.

## Risks / Trade-offs

- **[O recorte recusado trava o caderno]** → com a decisão de completude, uma discursiva cujo recorte é recusado (resíduo acima de 1,0 mm, captura escura) impede a entrega até o professor refotografar. O teto de 1,0 mm foi fixado **sem papel** (5c-0) e a taxa de recusa ao vivo é **desconhecida**. Mitiga-se mostrando o motivo na região e medindo a taxa sobre o documento renderizado em perspectiva (tarefa 6.2); **não** se mitiga o papel (P8). Se a taxa real for alta, a saída é a fatia "finalizar caderno incompleto" (§8), hoje fora de escopo — é a próxima candidata natural.
- **[Manuscrito de menor sem cifra em repouso]** → a classe H manda a proteção física e o bloqueio de tela para o professor (§10.8, último parágrafo); o backup está negado e medido. Cifrar é decisão própria.
- **[Lacuna do prazo]** → a eliminação só roda quando o aplicativo abre. Aparelho com a resposta guardada que **nunca mais abre** o aplicativo não expurga, e a classe H diz "em qualquer hipótese, em até 30 dias". Conhecido, **não mitigado** (P8), e registrado como linha nova do §16 com **token `5c`** — a gramática da guarda não aceita `5c-2` —, a `5c-2` como veículo (onde passa a existir o gatilho da nota e o `WorkManager` já está em uso), **custo** (um `WorkManager` periódico e uma trava entre a varredura e o analisador, porque um worker roda com o escaneamento aberto) e dono (P20).
- **[Varredura que falha, ou que elimina cedo]** → não impede a abertura, e eliminar cedo só devolve a região a não vista (decisão 5). O que não se vê em produção é o número de falhas; fica no log e na cobertura como lacuna conhecida.
- **[Relógio do aparelho]** → o professor que adianta ou atrasa a data muda o prazo. *Conhecido; não há relógio monotônico que atravesse reinício.*
- **[Sair preserva a resposta]** → **decisão do mantenedor, 2026-10-01: mantém as imagens ao sair, como o caderno** (trabalho não concluído, e coerência com o pendente e o caderno, que já sobrevivem a `sair` e à revogação). A tensão com a política §10.8 ("o encerramento de sessão elimina a base local") **continua**, e a resposta é dado mais sensível que o pendente: fica dita na linha ampliada do §16, e revisar a política é decisão que continua sendo do mantenedor.
- **[Queda entre gravar e guardar]** → o arquivo fica sem referência e é eliminado na abertura seguinte; o caderno em disco é o anterior, consistente. Tarefa 4.3 prova as duas ordens de queda.
- **[Memória]** → uma região retificada em memória (~870×1000 px, ~0,9 MB *calculado*) por região reconhecida, na thread de análise; não cruza para a principal, porque só o nome do arquivo cruza.
- **[A análise para sozinha, e o desenho depende disso]** → se alguém religar a análise em `ProvaComDiscursiva`, o predicado ainda impede recorte repetido de região com resposta, mas a recusa seria refeita a cada quadro. Por isso a tarefa 2.3 prende "uma rodada, um recorte por região" por teste.

## Registro do apply

Acrescentado em 2026-10-01 12:39 (+02:00), ao fim do apply, **sem trocar nenhuma decisão acima** (P17; os trechos que o apply mostrou errados ou supostos estão marcados no lugar onde aparecem). O que o código ganhou e o desenho não dizia:

- **Costuras de teste, todas pequenas e com um consumidor que as justifica (P18).** `RespostasEmArquivo(diretorio, escrever = …)`: o escritor entra por parâmetro, porque disco cheio **no meio** da escrita não se simula de outro jeito, e é isso que distingue a gravação atômica da gravação direta. `ScanActivity.analisadorDaCamera()` e `ScanActivity.entregarQuadro(...)` (`internal`): são os dois passos do laço da câmera (`analyze` faz `entrega(analisar(gray))`), e o emulador não alimenta a câmera real com um documento. `CameraFrameAnalyzer.analisar(Mat)` (`internal`): a análise sem `ImageProxy`. `ScanActivity.refazerResposta(...)` e `ScanActivity.instantaneoDoCaderno` (`internal`).
- **Dono único do nome do diretório (P28).** `RespostasEmArquivo.diretorioDe(filesDir)`: a `ScanActivity`, a varredura da porta de entrada e o teste da regra de extração passam por ela.
- **`deveAnalisar` saiu da lambda da `ScanActivity` para uma função** (`ScanState.kt`), em **commit próprio e antes** (`refactor(scan): …`, sem mudar comportamento — P19, P25), para a tarefa 2.3 poder prender a propriedade de que "a análise para sozinha" por teste.
- **A varredura nunca lança e não impede a abertura** (`varrerRespostas`): a exceção é por arquivo, e se os cadernos não puderem ser lidos nada é eliminado. A `SessaoActivity` chama `varrerRespostasDoAparelho` (IO); o escaneamento varre dentro de `retomarCadernoEmAndamento`, que aceita um relógio e um gancho `aoVarrer` (o log).
- **O texto da tela.** `ScanState.ProvaComDiscursiva.AVISO` passou a dizer: "A nota nao e definitiva: a correcao das discursivas ainda nao esta disponivel neste aparelho. Nenhum resultado e gravado enquanto o caderno nao completa; as respostas capturadas ficam neste aparelho, e o caderno completo e entregue para envio." E o aviso de desvio da tela da resposta é `ScanState.ProvaComDiscursiva.AVISO_DE_DESVIO`: "O aluno escreveu fora da area de resposta. Confira a folha de papel." Os textos da interface do aplicativo não levam acento (como os que já existiam).
- **Previsões de "ver falhar" que o apply não confirmou como escritas** (as verificações em si valem; o que mudou é quais testes caem). 2.2: tirar o aluno do predicado derruba, no analisador, o teste do "mesmo aluno" — e não o do aluno B —; quem prova "por aluno" é `Caderno?.jaTemResposta`, e é a mutação dele que derruba só o teste do outro aluno. 2.3: tornar `ProvaComDiscursiva` analisável derruba **três** guardas (a tabela do predicado, o estado real da sessão e o teste instrumentado), e não conjuntos disjuntos. 4.1: trocar `>=` por `>` derruba o teste de 30 dias exatos **e** o das duas organizações (que também fixa a borda). 4.2: "ler antes de varrer" não derruba nada (ver a decisão 5 acima).
- **Dado de teste.** Uma resposta gravada antes de a Activity abrir, sem referência de caderno, é órfã e a varredura da abertura a elimina — corretamente. Os testes que precisam de uma resposta referenciada a gravam com `capturadaEm` recente, e as de outro aluno **depois** da abertura, como o analisador.

## Migration Plan

Nenhuma migration de banco e nenhuma mudança de contrato do fio. `caderno.db` não muda de versão: o campo novo é do JSON. Cadernos guardados antes desta mudança leem com as discursivas capturadas como não vistas (perdem-se, no pior caso, uma rodada de captura). **Reverter** é reverter os commits, com um custo: um caderno guardado por esta versão tem a chave `resposta`, que a versão anterior **não decodifica** (`Json` sem `ignoreUnknownKeys`) — aceito porque o aplicativo não está em produção (§16, "antes do lançamento"), e dito aqui para não ser descoberto depois. Os arquivos de `respostas/` sem caderno que os referencie são eliminados na primeira abertura da versão que os conhece; na anterior, permanecem até a desinstalação.

## Open Questions

- **O regime de thread da leitura do caderno — resolvido.** Não é mais pergunta: a leitura direta no `onCreate` estourava (o Room recusa consulta na thread principal; medido em 2026-09-30, pilha em `ScanActivity.kt:138`), e a mudança `o-caderno-e-lido-fora-do-fio-principal` (#82, arquivada em #83) a moveu para `Dispatchers.IO`, com a sessão montada depois. A varredura e a leitura normalizada (decisão 5) entram **nessa** leitura assíncrona, antes de `montar`, e nunca no `onCreate` diretamente.
- **O tamanho real do PNG** de uma resposta escrita (estimado entre 100 e 800 KB, *suposto*). A tarefa 6.2 registra o medido; não muda nada a menos que passe de alguns MB. **[Medido em 2026-10-01 12:39 (+02:00):** 3 079 bytes com a folha em branco e 459 686 bytes com ruído sintético, em 870×1000 px. Não passa de alguns MB. Em manuscrito real continua **não medido**.**]**
