# Cobertura de cenários — fatia 4a (pull de referência imutável no aparelho)

Documento em construção: **o código da fatia está completo e verde na JVM; o que falta é aparelho,
impressora e servidor real.** O que está aqui já foi verificado; o que falta está nomeado no fim.

**O padrão desta fatia, dito antes das tabelas.** Sete verificações críticas foram submetidas a
mutação, e em **quatro** delas o resultado contrariou o que o `design.md` afirmava. Duas defesas de
transporte que ele argumentava eram desnecessárias (`respondText`, `body<ByteArray>()`); um
vazamento de fronteira que ele queria testar era impossível de escrever; e um cenário que a tarefa
4.3 tratava como prova do mecanismo estava **sombreado** por uma camada a jusante. Nenhum desses
quatro foi encontrado por revisão — os quatro vieram de introduzir o defeito e olhar o vermelho, ou
a falta dele. É o argumento inteiro da seção "Verificação" do `CLAUDE.md`, e ele se pagou aqui.

## Como cada verificação crítica foi vista falhar

### Os bytes do pacote re-derivados no caminho de saída (tarefas 1.4 e 1.5)

O requisito diz que o corpo entregue é byte a byte o `content` gravado, e que o `content_hash`
declarado cobre exatamente esse corpo. É o que ADR-0008 escolheu `text` em vez de `jsonb` para
proteger: um pacote cujo hash deixa de bater não tem sintoma na tela — ele chega ao aparelho, falha
a conferência, e o professor lê "não foi possível carregar a prova" sem que nada esteja errado com a
prova.

O oráculo é o banco, e não uma segunda serialização em Kotlin: o conteúdo é lido de
`exam_package.content` por SQL cru, e o hash é conferido pelo `MessageDigest` da JVM
(`PostgresSupport.sha256Hex`), que não compartilha uma linha com o `Sha256` do domínio.

A tarefa 1.4 pedia **duas** mutações, e elas deram resultados **diferentes**. A tabela as separa de
propósito: lidas juntas, sugeririam que "as duas mutações" produziram um resultado só.

| Defeito introduzido | Quem acusou |
|---|---|
| **(i)** `respondText` no lugar de `respondBytes` | **ninguém** — os treze cenários passaram, inclusive o escrito de propósito com `Accept-Charset: ISO-8859-1` |
| **(ii)** O pacote sai embrulhado num objeto `{"content": ..., "content_hash": ...}` pelo `ContentNegotiation` — a forma que ADR-0013 decisão 2 descreve como a do PostgREST | **quatro**: `o_corpo_entregue_e_byte_a_byte_o_conteudo_gravado_e_o_hash_confere`, `hash_de_corpo_alterado_nao_confere_com_o_gravado`, `o_corpo_entregue_e_json_valido_e_nao_vem_embrulhado` e `o_corpo_ignora_Accept-Charset_e_continua_em_UTF-8` |
| Hash calculado sobre corpo com um byte a mais, sobre corpo vazio e sobre corpo truncado | o próprio `hash_de_corpo_alterado_nao_confere_com_o_gravado`, que afirma o contrapositivo da igualdade principal |

**(i) prova que o risco não existia; (ii) prova que a asserção funciona.** Só a segunda é evidência
de cobertura.

**O achado da 1.4 não foi o vermelho: foi o verde.** A tarefa mandava trocar `respondBytes` por
`respondText` e ver a suíte cair. Ela **não caiu** — os treze cenários passaram com a mutação
aplicada. A razão é que o `respondText` do Ktor **não negocia charset para `application/json`**: o
tipo é servido sempre em UTF-8, por RFC, e `withCharsetIfNeeded` não acrescenta parâmetro nenhum.
Um cenário novo foi escrito para forçar a negociação — o pedido manda `Accept-Charset: ISO-8859-1`,
que na prática costuma ser posto por proxy e não por quem escreveu o cliente — e ele **também**
passou com a mutação.

A conclusão é sobre a decisão, e não sobre o teste: **a justificativa de charset da decisão 2 do
`design.md` estava errada**, e o `design.md` foi corrigido. `respondBytes` fica, porque continua
sendo a expressão direta de "estes bytes", mas para este tipo de conteúdo ele e `respondText` são
byte a byte equivalentes, e escrever o contrário deixaria uma proteção imaginária no registro. O que
de fato protege é a asserção de igualdade de bytes, e a mutação do envelope mostra que ela dispara.

O conteúdo da fixture tem **acento de propósito**, contra o estilo do resto da base: em ASCII puro,
UTF-8 e Latin-1 produzem os mesmos bytes, e o cenário de codificação passaria com o servidor
codificando errado.

### Ausência distinguível de proibição (tarefa 1.7)

O requisito diz que pacote de organização alheia é indistinguível de pacote inexistente. 403
confirma que a prova existe, e existência é informação sobre o que há do outro lado.

| Defeito introduzido | Quem acusou |
|---|---|
| Toda ausência passa a responder 403 "essa prova não é da sua organização" | **quatro**: `pacote_de_organizacao_alheia_responde_como_prova_inexistente`, `prova_inexistente_responde_404_e_nao_falha_de_servidor`, `prova_sem_pacote_publicado_responde_404` e `organizacao_malformada_no_caminho_responde_como_inexistente` |

**O que a tarefa não conseguiu produzir, e isso é o achado.** A mutação pedida era o vazamento real
— 403 **só** para organização alheia, mantendo 404 para prova inexistente. Ela não é escrevível
nesta base: a distinção exigiria consultar a existência da prova **por fora da RLS**, e o
`app_backend` não tem esse caminho. `exam_member_select` esconde a linha do não-membro, então a rota
recebe `null` nos dois casos e não tem como saber qual dos dois é.

O vazamento é impedido por construção, e não por disciplina de quem escreve a rota. O que ficou
verificado pela mutação acima é a outra metade: que a suíte reage se alguém trocar o status.

**Esta fatia não verificou "por construção" — ela herdou.** A afirmação se apoia em duas
propriedades que foram conferidas em outras fatias, e nomeá-las é o que a torna falseável em vez de
uma asserção solta sobre a arquitetura:

| Propriedade | Quem a verifica | De onde vem |
|---|---|---|
| `app_backend` não tem `SUPERUSER` nem `BYPASSRLS` | `ConnectionRoleTest.o papel de conexao da aplicacao nao e superusuario nem tem bypassrls` | fatia 0 (`ac10698`) |
| Toda tabela de `public` tem RLS **habilitada e forçada** | `ConnectionRoleTest.toda tabela de public tem RLS habilitada e forcada` | fatia 0, com a mutação de `exam_package` registrada em `docs/cobertura-fatia-2a.md` |
| `exam` e `exam_package` alheios não aparecem em consulta sem filtro | `ExamTenancyIsolationTest.a prova alheia nao aparece na listagem sem filtro` e `o pacote alheio nao aparece na listagem sem filtro` | fatia 2a (`ac617f2`) |

Se qualquer uma das três cair, a rota desta fatia passa a poder distinguir "não existe" de "não é
seu" — e a impossibilidade registrada acima deixa de valer. A dependência é real e está escrita para
que quem mexer naqueles testes saiba o que mais está pendurado neles.

### O corpo do pacote desserializado no transporte (tarefa 2.3)

O requisito é que a conferência de integridade hasheie **o byte que chegou**. Se o transporte
desserializasse e reserializasse, a conferência passaria a ser uma afirmação sobre o parser em vez
de sobre o transporte, e um corpo truncado estouraria antes de a camada (a) — que existe justamente
para pegar corpo truncado — ter a chance de rodar.

| Defeito introduzido | Quem acusou |
|---|---|
| `retornoDeBytes` passa a ler o corpo por `body<ByteArray>()`, com o `ContentNegotiation` instalado | **ninguém** |

**A tarefa mandava registrar o resultado qualquer que fosse, e ele foi verde.** O
`ContentNegotiation` do cliente Ktor tem `ByteArray` entre os tipos que ignora, então os dois
caminhos entregam os mesmos bytes. `readRawBytes()` fica por ser direto, e não por proteger de um
risco que não se materializa neste ponto.

**Isto é a segunda vez nesta fatia**, depois do `respondText` da 1.4, e o padrão vale ser dito: as
duas defesas de transporte que o `design.md` argumentou eram desnecessárias, e as duas foram
descobertas pela mutação e não pela revisão. O que de fato protege nas duas pontas é a mesma coisa —
a asserção de igualdade de **bytes** contra um oráculo que não compartilha código com o produtor. As
mutações que derrubam cenários são as que mudam bytes (o envelope da 1.4), e não as que mudam a
função que os escreve.

O que o cenário de corpo truncado da 2.3 continua provando, e não é pouco: o transporte entrega
bytes que não parseiam em vez de estourar, então a camada (a) chega a rodar sobre eles.

### As camadas (a) e (b) medindo a mesma coisa (tarefas 3.4 e 3.5)

ADR-0013 registra que confundir integridade com fidelidade é rotineiro, e que aceitar (a) como se
fosse (b) é a segunda falha silenciosa mais provável da fatia: um pacote cujo hash confere e cujo
parse perdeu um campo produz nota plausível e errada, sem sintoma na tela.

O que precisa ser verificado não é que cada camada recusa — é que elas são **independentes**. Se as
duas caírem juntas na mesma mutação, os testes de (b) estão medindo (a) de novo, e a segunda camada
não existe de fato.

| Defeito introduzido | Quem acusou |
|---|---|
| A comparação de (b) — `toCanonicalJson(parse(bytes)) == bytes` — deixa de ser avaliada | **dois, e só os dois**: `campo_com_valor_padrao_omitido_e_recusado_por_interpretacao` e `ordem_de_campo_trocada_e_recusada_por_interpretacao`. Os outros dezesseis seguiram verdes |
| (a) vira tautologia: `Sha256.hex(bytes) != Sha256.hex(bytes)` | **cinco, e só os cinco de integridade**: um byte alterado, um byte a mais, truncado, vazio, e o que confere o texto da recusa |

**A leitura é a que importa, e ela fechou.** Os dois conjuntos são disjuntos: nenhum cenário caiu
nas duas mutações. As camadas medem coisas diferentes.

Duas coisas que a primeira mutação deixou visíveis e que valem o registro:

- `campo_desconhecido` e `bytes_que_nao_sao_json` **não** caíram com (b) desligada. Elas são pegas
  pelo **parse estrito** — `ExamPackage.JSON` não ignora chave desconhecida —, e não pela
  comparação. É o que ADR-0013 quer dizer com "(b) subsume o parsing estrito": os dois cenários que
  restam quando o parse estrito já fez o trabalho dele são exatamente os dois que caíram, e são os
  que envolvem valor padrão injetado e ordem canônica;
- o cenário do `assets` omitido é o único em que **nada estoura**: o pacote é íntegro, o hash
  confere, o parse não reclama, e só a comparação vê. É a forma de desalinhamento de versão que a
  fatia existe para pegar.

O oráculo do hash nesta suíte é o `MessageDigest` da JVM, e um cenário à parte afirma que ele e o
`Sha256` do domínio concordam sobre os bytes da fixture — sem ele, os dois poderiam derivar juntos e
todo o resto continuaria verde.

### O cache confiando no nome do arquivo (tarefas 4.3 e 4.4)

ADR-0013 decisão 3 manda a leitura recalcular o hash sobre os bytes lidos, e não presumi-lo do
endereço. Sem isso a conferência vale uma vez, na primeira gravação, e todo uso seguinte é de um
arquivo que ninguém mais olhou.

| Defeito introduzido | Quem acusou |
|---|---|
| `ler` passa a conferir os bytes contra `Sha256.hex(bytes)` — isto é, contra eles mesmos — em vez de contra o hash pedido | **um**: `conteudo_trocado_por_outro_pacote_valido_tambem_e_recusado` |

**O achado é qual cenário caiu, e qual não caiu.** `conteudo_corrompido_em_repouso` — o cenário que
a tarefa 4.3 nomeia — **continuou verde** com a leitura confiando no nome. A razão é sombreamento:
conteúdo truncado também não parseia, então a camada (b) o recusa por interpretação e o desfecho
externo é o mesmo. Ele afirma o comportamento que a spec pede, e não o mecanismo.

Quem isola o mecanismo é o cenário do conteúdo trocado por **outro pacote válido**: JSON íntegro,
`ExamPackage` legítimo, mesmo tamanho de arquivo — só não é o conteúdo daquele hash. Nenhuma outra
camada tem como recusá-lo, porque (b) o parseia e reserializa sem divergência alguma. Só o hash
recalculado vê.

Os dois cenários ficam, e o comentário de cada um agora diz qual dos dois papéis ele cumpre.

**É a terceira vez nesta fatia que uma proteção aparentava estar coberta e não estava**, e as três
vieram da mutação. O padrão é sempre o mesmo: um cenário cai por uma razão a jusante da que se
pretendia medir. É exatamente o risco que motivou a tarefa 8.3b, e ele já se materializou aqui.

### O cache endereçado só por conteúdo (tarefa 4.7)

Endereçamento por conteúdo é global por natureza, e o cache é um caminho de leitura que **não passa
pela rota**. Sem o escopo por organização, um aparelho compartilhado entregaria da pasta um pacote
que a rota recusaria.

| Defeito introduzido | Quem acusou |
|---|---|
| `pasta()` passa a devolver a raiz, ignorando a organização | **oito**, entre eles `o_mesmo_conteudo_guardado_numa_organizacao_nao_e_alcancavel_pela_outra` e `apagar_uma_organizacao_nao_toca_na_outra` |

### Os pacotes do usuário anterior herdados pelo seguinte (tarefas 4.8 e 4.9)

É o defeito que a tarefa 3.7 da fatia 4a-zero pagou, um nível abaixo. Lá o que sobrevivia à troca de
conta era a escolha de organização; aqui é o conteúdo baixado — e ele não aparece em tela nenhuma,
então ninguém tem como notar que ficou.

| Defeito introduzido | Quem acusou |
|---|---|
| `sair()` apaga credencial e organização, e **não** o cache | **dois**: `sair_apaga_os_pacotes_guardados_sob_a_organizacao_ativa` e `sair_com_organizacao_vinda_do_disco_tambem_apaga_os_pacotes` |

O cache de mentira registra a **lista** de organizações apagadas, e não um booleano. Um booleano
passaria com o logout apagando o cache de qualquer organização — inclusive o da que o usuário
seguinte vai usar.

A ordem dentro de `sair()` é carregada: a organização é lida **antes** de ser apagada. Invertida, o
identificador já teria sumido quando o cache fosse limpo, e o apagamento aconteceria sobre `null` —
sem estourar, e sem apagar nada.

### Consulta que falha apresentada como lista vazia (tarefa 5.3)

"Esta organização não tem prova publicada" é uma afirmação sobre o mundo, e uma consulta que não
chegou ao servidor não autoriza fazê-la. O professor leria que não há prova onde há, e a ação que
ele tomaria a seguir seria publicar de novo.

| Defeito introduzido | Quem acusou |
|---|---|
| `SemRede` e `Falhou` na listagem passam a produzir `SemProvaPublicada` | **dois**: `listagem_sem_rede_nao_e_lista_vazia` e `listagem_que_falha_por_outra_causa_e_distinta_de_sem_rede` |

Os dois cenários afirmam os estados **um contra o outro** (`assertNotEquals`), e não apenas cada um
contra o esperado. É a comparação direta que quebra quando alguém os unifica; duas asserções
isoladas contra valores esperados sobreviveriam a uma unificação feita em ambos os lados.

### O gate sem dente na versão de renderizador (tarefa 5.5)

Até esta fatia `min_renderer_version` era imposto ao desenhar e apenas **declarado** no caminho de
captura. Um pacote que o aplicativo não desenha por inteiro também não é um pacote que ele deva
medir — a folha impressa daquela variante seria ilegível para este OMR.

| Defeito introduzido | Quem acusou |
|---|---|
| `passarPeloGate` deixa de ler `minRendererVersion` do pacote | **um**: `pacote_que_exige_renderizador_mais_novo_e_barrado` |

O pacote do cenário é o de referência real, com a versão subida acima da que o aplicativo desenha, e
um segundo cenário fixa a borda: exigir **exatamente** a versão do aplicativo passa. Sem ele, trocar
`>` por `>=` não seria acusado por ninguém.

### O cache ignorado no caminho da obtenção (tarefa 5.8)

| Defeito introduzido | Quem acusou |
|---|---|
| `obterPacote` deixa de consultar o cache e puxa sempre | **um**: `segunda_vez_responde_do_disco_sem_tocar_na_rede` |

**O defeito não é de correção, e é isso que o torna perigoso.** Com rede, tudo continua funcionando:
o pacote chega, confere, e a nota sai certa. O que quebra é exatamente o caso para o qual a fatia
existe — a sala sem sinal. Nenhuma tela acusa, e nenhum cenário que só olhe o valor devolvido acusa
tampouco: o cenário que pega mede **quantas vezes o servidor foi chamado**, e o servidor de mentira
estoura se for chamado.

### O pacote embutido sobrevivendo ao consumidor (tarefas 7.2 e 7.3)

ADR-0013 decisão 5 chama isto de a falha mais provável e a mais quieta da fatia: tudo continua
funcionando, e o que se perde é a garantia de que o pacote em uso foi conferido. Um `grep` por
`assets.open` diria que ninguém lê — e não que ninguém embutiu.

A verificação é por isso sobre o **APK**, e não sobre o código: `verificarApkSemPacote` abre o
artefato como zip e recusa qualquer asset JSON que declare `answer_key` e `min_renderer_version`. A
detecção é pela **forma** e não pelo nome do arquivo, porque um pacote renomeado continua sendo um
pacote. A tarefa entra em `check`, então `./gradlew build` a executa.

| Defeito introduzido | Quem acusou |
|---|---|
| `EmbedPackageTask` volta ao `build.gradle.kts`, sem devolver o `assets.open` que a lia | `verificarApkSemPacote`, com `ha pacote de prova entre os recursos empacotados: android-debug.apk!assets/prova-referencia.package.json` |

**Um segundo defeito apareceu enquanto a tarefa era escrita, e ele é o mais instrutivo.** A primeira
versão alimentava a coleção de entrada com o *diretório* de saída dos APKs, e um
`ConfigurableFileCollection` assim traz o diretório e não o conteúdo — a tarefa não abria APK nenhum
e passava. Quem pegou foi a guarda de vacuidade escrita junto (`require(apks.files.any { … })`),
antes de qualquer mutação: sem ela, a verificação teria entrado no CI dizendo verde sobre nada.

O log da tarefa imprime **quantos** assets JSON foram conferidos justamente para que "0" seja
visível a quem lê a saída, em vez de indistinguível de "nenhum problema".

### A camada (c) medindo a mesma coisa que (a) e (b) (tarefas 8.3b e 8.3c)

**É a mais crítica das três camadas, e a única que julga de quem é a folha.** (a) e (b) julgam
bytes; (c) decide se a nota que vai sair pertence à folha que está na frente da câmera. Ela recebeu
o mesmo tratamento de 3.4 porque, sem isso, nada provaria que o cenário que a exercita mede
identidade em vez de medir (a) ou (b) de novo por acidente.

| Defeito introduzido | Quem acusou |
|---|---|
| A comparação de `exam_short_id` em `ScanSession.resultOf` deixa de ser avaliada | **dois, e só os dois**: `folha_de_outra_prova_e_recusada_dizendo_que_e_de_outra_prova` (4a) e `folha de outra prova e recusada, dizendo de qual prova ela e` (herdado da 3c). Os 18 cenários de `ConferenciaDePacoteTest` seguiram **todos verdes** |

**O sombreamento era real, e a fixture foi construída para evitá-lo.** `prova-2` declara os mesmos
identificadores de item, as mesmas posições e o mesmo gabarito da `prova-referencia`, mudando só o
`short_id`. Com itens diferentes, `ObjectiveScoring` recusaria a folha trocada por divergência de
itens, a camada (c) ficaria coberta por uma conferência posterior, e os cenários passariam sem que
ela existisse — exatamente o que aconteceu com o cenário de truncamento na tarefa 4.4.

Um cenário à parte (`as_duas_provas_diferem_so_na_identidade`) afirma essa propriedade da fixture.
Sem ele, alguém regravando `prova-2` a partir de outra definição deixaria os demais verdes e sem
significado.

**A 8.3c mede o que a conferência está segurando**, e o número importa: a mesma leitura apurada
contra o pacote correspondente produz **40 de 40, sem nenhuma pendência**. Não é "alguma nota" — é a
nota **cheia**, idêntica à que uma folha certa e perfeitamente preenchida da `prova-referencia`
daria, porque `scoring.max_score` é 40 nos dois pacotes e o gabarito é o mesmo.

Registrar o limiar (`> 0`) em vez do número teria escondido justamente o que torna o defeito caro:
não há nada na tela que distinga a folha errada da folha certa. É a mesma convenção da tarefa 9.6 —
o número medido, e não o limiar aceito.

### O tamanho do `Intent`, medido em vez de citado (tarefa 6.5)

A decisão 6 do `design.md` rejeitava passar o pacote pelo `Intent` afirmando que ~100 KB numa
transação Binder tem teto de ordem próxima, e que estourá-lo derruba o aplicativo.

Medido no `platos-atd34`, com o pacote real e múltiplos dele:

| Carga no extra | Desfecho |
|---|---|
| 101 618 bytes (o pacote) | passou |
| 203 236 bytes (×2) | passou |
| 406 472 bytes (×4) | passou |
| 812 944 bytes (×8) | `RuntimeException: Failure from system` — **capturável**, e não uma queda |

**As duas metades da afirmação estavam erradas**, e a decisão foi corrigida. Ela continua de pé pela
razão que sempre a sustentou de fato — o `Intent` carrega o endereço para que a leitura reconfira, e
para que a `ScanActivity` sobreviva à morte do processo —, e não por um limite que não existe nessa
ordem de grandeza.

O teste que produziu a tabela era **medição, e não suíte**: ele afirmava o comportamento do Binder, e
não deste código. Foi removido depois do registro. A folga medida é de ~4× sobre um pacote de uma
variante; a fatia 7 multiplica o `layout` por variante, e é lá que ela precisa ser remedida.

**É a terceira afirmação de transporte desta fatia que a medição derruba**, depois do `respondText`
(1.4) e do `body<ByteArray>()` (2.3). As três foram escritas com convicção no `design.md` e nenhuma
sobreviveu ao contato com o instrumento.

## O comando cheio do CI, e a paridade (tarefas 7.4 e 10.1)

Rodado no `platos-atd34` (API 34, `aosp_atd`, x86_64) — a mesma imagem do CI — em 2026-09-04:

- `./gradlew build`: **verde**, com `verificarApkSemPacote` no grafo (`0 asset(s) JSON conferido(s)`);
- `./gradlew :apps:android:connectedDebugAndroidTest` **sem filtro de classe**: **46 testes, 0
  falhas**. É a metade que `build` não roda, e a fatia 4a-zero registrou duas vezes o comando estreito
  escondendo o que o cheio pega.

**A paridade foi fechada na mesma sessão**, embora esta fatia não regrave o golden: ela mexeu no
caminho por onde o PDF do web é desenhado (a variável `PLATOS_PACKAGE`), e um caminho de desenho
alterado é motivo suficiente para não confiar na última medição. Fidelidade do web OK (maior desvio
0,046 mm), tinta OK, fidelidade do Android OK (0,042 mm), e paridade OK com maior divergência de
**0,048 mm** contra tolerância de 0,3 mm — folga de 0,252 mm. O `android.pdf` é de hoje, gerado no
emulador.

**Uma armadilha do instrumento, registrada para não custar de novo:** rodar
`connectedDebugAndroidTest` com filtro de classe **reinstala o APK e apaga o `filesDir`**, levando
junto o `android.pdf` que a execução anterior tinha escrito. A primeira tentativa de fechar paridade
comparou o web de hoje contra um Android de agosto sem que nada avisasse — o arquivo estava lá, com
o nome certo, e só a data denunciava. Confira a data do `android.pdf` antes de comparar.

## O que ficou sem teste automático, e por quê

- **O que um `@Composable` desenha.** Lacuna herdada da fatia 4a-zero, e nesta fatia ela cresceu:
  são quatro telas novas (`EscolhaDaProvaScreen`, `SemProvaScreen`, `BarragemScreen`,
  `PreparandoScreen`) e nenhuma tem teste. A mitigação é a mesma de lá — a **escolha da frase** saiu
  da tela e virou `textoDaBarragem`/`textoDaListagem`, com `when` exaustivo sem `else`, e é ela que
  está coberta. O que fica descoberto é a tela ignorar o parâmetro e escrever um literal.
- **A leitura do cache no caminho real do Android.** `PacotesEmArquivo` é testado sobre um `@TempDir`
  de verdade, mas `filesDir` só existe em aparelho. É o par da tarefa 9.4.
- **O tempo do pull contra servidor real.** `MockEngine` não tem tempo limite de soquete — a lição
  que a 4a-zero pagou em aparelho —, então nenhum teste de JVM desta base diz nada sobre latência.

## O que ainda não foi verificado

Tudo o que exige aparelho, impressora ou servidor real. O roteiro está em
`docs/protocolo-medicao-impressa.md` §14, escrito para não ser reinventado:

| Tarefa | O que falta |
|---|---|
| 6.5 | Medir o `Intent` com o pacote serializado, em aparelho |
| 7.4 | `connectedDebugAndroidTest` **sem filtro**, no `platos-atd34` |
| 8.2 | Publicar as duas provas contra o servidor real e puxá-las |
| 8.4, 8.5 | Imprimir a folha da `prova-2` e fechar a **6.4b**, herdada da 3c |
| 9.1–9.6 | As seis conferências em aparelho (§14.2 a §14.6 e §14.9) |
| 10.1 | A metade instrumentada do comando cheio do CI |

`./gradlew build` está **verde**, com `verificarApkSemPacote` no grafo; o que ele não roda é a suíte
instrumentada, e é exatamente por isso que a 10.1 pede as duas metades.
