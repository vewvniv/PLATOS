## Context

Ver `proposal.md` — Why. ADR-0013 já decidiu transporte, cache e verificação; este documento decide
o que ele deixou em aberto e registra o que foi medido no terreno.

O que já existe e que este desenho consome:

- `ExamPackage.toCanonicalJson()` e `contentHash()` em `commonMain`, com `Sha256.hex(ByteArray)` e
  `Sha256.hex(String)` — o segundo é o primeiro sobre `encodeToByteArray()`, isto é, sobre UTF-8;
- `ExamPublication` grava em `exam_package.content` exatamente `pacote.toCanonicalJson()`, e em
  `content_hash` exatamente `pacote.contentHash()`. Não há segundo caminho de serialização;
- `exam.short_id` é **unique global**, não por organização (`exam_short_id_unico`);
- o aparelho tem `clienteHttp()` com `ContentNegotiation`/JSON, `clienteApi()` como ponto único de
  credencial e 401, `Retorno` como vocabulário de falha, e `DeviceSession` como máquina pura;
- `RendererContract.assertSupports()` já impõe `min_renderer_version`, e só na renderização;
- `ScanSession.resultOf()` já recusa folha de outra prova, e `ObjectiveScoring.resolveVariant()` já
  recusa variante que o pacote não declara. A camada (c) de ADR-0013 é, em código, o que já existe —
  o que muda é o pacote contra o qual ela roda;
- `allowBackup="false"` no manifesto: o cache de pacotes não precisa de regra de backup.

## Goals / Non-Goals

**Goals**

- Um caminho só do pacote até a folha, sem ramo de reserva.
- Que a conferência valha **em toda leitura**, e não só na primeira gravação.
- Que cada recusa chegue à tela como frase própria.

**Non-Goals**

- Modo degradado. §10 manda capturar imagem bruta quando o pacote falta offline; isso exige
  armazenamento durável de captura, que é 4b. **§10 só é cumprida por inteiro lá**, e esta fatia
  entrega a metade que ADR-0013 decisão 5 fixa: recusar com motivo, nunca degradar.
- Expiração ou limite de tamanho do cache. Não há evidência de pressão de espaço, e inventar
  política agora é abstração prematura (regra 8). O que existe é o apagamento no logout.
- Requisição condicional (`If-None-Match`/304). A listagem já traz o `content_hash`, então o
  aparelho decide **antes** de pedir; um segundo mecanismo para a mesma economia seria duplicação.

## Decisions

### 1. Duas rotas, com a organização no caminho

```
GET /organizations/{organizationId}/exams
GET /organizations/{organizationId}/exams/{shortId}/package
```

`short_id` é unique global, então `/exams/{shortId}/package` funcionaria e a organização sairia do
vínculo do chamador. **Rejeitado**: a organização ativa é uma escolha *do aparelho* (4a-zero), e
deixá-la implícita faria o servidor decidir por conta própria qual organização o pedido significa
quando o usuário tem duas. Com ela no caminho, o cache e o pedido usam **o mesmo identificador**, e
o par (organização, prova) que não existe junto é 404 — a mesma resposta de prova inexistente.

A listagem devolve `short_id`, título e `content_hash`. O hash na listagem é o que permite ao
aparelho saber, antes de pedir, se já tem o conteúdo; sem ele, todo pull seria feito para descobrir
que era desnecessário.

Prova sem pacote é excluída pelo `join`, e não filtrada depois: uma prova sem pacote não é uma
escolha oferecível, e deixá-la aparecer produziria um item de lista que só falha ao ser tocado.

Autorização pelo caminho que já existe: `Tenancy.asUser(userId)`, o mesmo de `/me/organizations`.
Organização a que o chamador não pertence não devolve linha nenhuma, e isso vira 404 — nunca 403.

### 2. `respondBytes`, e o hash em cabeçalho próprio

O corpo sai por `call.respondBytes(conteudo.encodeToByteArray(), ContentType.Application.Json)`.

`respondBytes` passa ao largo do `ContentNegotiation` do servidor, que é o que reserializaria o
conteúdo se ele fosse tratado como objeto, e recebe bytes — que é o que o `content_hash` cobre.

**Correção de 2026-09-04, feita pela tarefa 1.4.** A versão original desta decisão rejeitava
`respondText` alegando que ele negocia charset. **Está errado para `application/json`**: o Ktor
serve esse tipo sempre em UTF-8 e não acrescenta parâmetro de charset, então os dois caminhos são
byte a byte equivalentes — a mutação foi aplicada e os treze cenários continuaram verdes, inclusive
um escrito de propósito com `Accept-Charset: ISO-8859-1`. `respondBytes` fica por ser a expressão
direta de "estes bytes", e não por proteger de um risco que não existe. Quem de fato protege é a
asserção de igualdade de bytes contra o `content` lido do banco, e ela dispara: a mutação do
envelope `{"content": ...}` — a forma do PostgREST — derruba quatro cenários.

O hash vai em **`X-Package-Content-Hash`**, hexadecimal minúsculo, sem aspas.

`ETag` foi considerado e **rejeitado**: ele carrega sintaxe de validador (aspas obrigatórias,
prefixo `W/` para validador fraco) e uma semântica de cache HTTP que esta fatia não usa. Um
cabeçalho próprio diz exatamente uma coisa, e quem o lê não precisa desfazer aspas antes de
comparar com um hash.

### 3. O aparelho lê bytes crus, fora do `Retorno<T>` desserializado

`Retorno.Respondeu<T>` chama `resposta.body<T>()`, que passa pelo `ContentNegotiation` do cliente.
Para o pacote isso é errado por construção: o que a camada (a) hasheia precisa ser **o byte que
chegou**, e não o resultado de desserializar e reserializar.

Entra `retornoDeBytes`, irmão de `retornoDe`, usando `resposta.readRawBytes()` e a **mesma**
classificação por `IOException` — a decisão 8 da 4a-zero (um vocabulário só de falha) continua
valendo, e é por isso que a função nova compartilha a classificação em vez de trazer a sua.

O cabeçalho do hash é lido da resposta e devolvido junto dos bytes: quem confere precisa dos dois, e
separá-los em duas chamadas abriria a possibilidade de conferir bytes contra o hash de outra
resposta.

### 4. O cache é arquivo, e a atomicidade tem duas metades

`filesDir/packages/<organization_id>/<content_hash>.json`. Gravação: arquivo temporário no **mesmo
diretório**, depois `renameTo` — rename dentro do mesmo sistema de arquivos é atômico, e um leitor
nunca vê o destino pela metade.

**Não há `fsync`, e isso é decisão e não esquecimento.** Sem sincronizar o descritor antes do
rename, uma queda de energia pode deixar o nome visível com conteúdo vazio ou truncado. É
exatamente o caso que a reconferência na leitura pega: o hash é recalculado sobre os bytes lidos, o
conteúdo truncado não bate, o arquivo é descartado e o pull refeito. Pagar `fsync` em toda gravação
para evitar um caso que a leitura já trata seria custo sem cobertura nova — e a reconferência
precisa existir de qualquer forma, porque corrupção em repouso e adulteração local não são evitadas
por sincronizar.

A porta é uma interface no espelho de `SessaoGuardada`, com os verbos nomeados um a um —
`guardar`, `ler`, `apagarDaOrganizacao` —, e não uma `apagarTudo`. É o mesmo motivo registrado lá:
com o apagamento nomeado, um teste de JVM afirma que **sair apaga o cache**; escondido dentro do
adaptador Android, nenhum teste desta camada o alcança.

### 5. Uma segunda máquina pura, e não uma `DeviceSession` maior

`DeviceSession` responde "quem entrou e sob qual organização". Escolher prova, puxar, conferir e
barrar é outra pergunta, com outros estados e outros motivos de falha. Somá-las daria uma classe
que decide sobre credencial e sobre hash no mesmo `when`.

Entra `PreparoDaProva`: Kotlin puro, recebe resultados já destilados (a listagem, o pull, a
conferência) e produz o estado da tela. Mesma fronteira que a 3a desenhou entre `vision/` e `omr/` e
que a 3c repetiu em `ScanSession`, pelo mesmo motivo — o que decide precisa ser testável sem
aparelho e sem servidor. `SessaoActivity` compõe as duas; ela já é o lugar que liga coisas.

Os motivos de barragem são um enum com quatro valores, e a frase de cada um mora numa função de
texto, como `mensagemDeEntrada` e `textoSemOrganizacao` já fazem. Motivo é estado; frase é
apresentação. Distinguir "sem rede" de "conferência falhou" por texto de mensagem é exatamente o
defeito que a 4a-zero registrou.

### 6. O `Intent` para `ScanActivity` carrega o endereço, e não o conteúdo

`ScanActivity` deixa de abrir asset e passa a receber `organizationId` e `contentHash`; ela lê o
arquivo e **reconfere** antes de montar a sessão.

Passar o pacote serializado pelo `Intent` foi **rejeitado** — mas não pela razão que esta decisão
dava originalmente.

**Correção de 2026-09-04, feita pela tarefa 6.5.** A versão original afirmava que ~100 KB numa
transação Binder tem teto prático de ordem próxima e que estourá-lo derruba o aplicativo. **Medido no
`platos-atd34`, e as duas metades estão erradas:** 101 618 bytes passam, 203 236 passam, 406 472
passam, e só em 812 944 aparece `RuntimeException: Failure from system` — que é capturável, e não
uma queda. A folga é de cerca de 4×, e não inexistente.

A decisão **não muda**, porque a razão que a sustenta é a de baixo e sempre foi: o `Intent` carrega o
endereço para que a leitura reconfira. O que sai é uma justificativa que a medição não confirma, e
mantê-la deixaria uma proteção imaginária no registro.

Fica um aviso com data de validade: a folga de 4× é sobre um pacote de uma variante. A fatia 7
multiplica o `layout` por variante, e é lá que a margem precisa ser remedida se alguém voltar a
considerar passar conteúdo pelo `Intent`.

A releitura não é redundância com o gate. Ela é o que faz o requisito "reconferido a cada leitura"
valer no caminho real, e o que faz a `ScanActivity` sobreviver à morte do processo: o `Intent`
persiste, o endereço continua válido, e a conferência acontece de novo em vez de ser presumida de
uma decisão tomada antes de o processo morrer. Se a leitura falhar aqui, a `ScanActivity` recusa com
motivo e volta — a câmera não abre.

### 7. A conferência mora no aplicativo, e não em `commonMain`

`verificarPacote(bytes, hashDeclarado)` é função pura, e caberia em `commonMain`. Fica no
aplicativo Android porque **não há segundo consumidor**: o servidor publica e o web renderiza;
nenhum dos dois puxa e confere. A regra 7 proíbe duplicar regra de negócio entre apps, e não há
duplicação — há um consumidor só. Quando houver o segundo, a função sobe, e ela é pura justamente
para que subir seja mover um arquivo.

O que **não** pode descer para o aplicativo é `toCanonicalJson()`/`contentHash()`, e não desce: a
camada (b) só prova o que promete porque é a **mesma** implementação dos dois lados. Uma segunda
implementação da serialização canônica no aparelho transformaria (b) numa comparação entre dois
autores, que é outra pergunta.

### 8. A segunda prova é fixture nova, pelo caminho que já existe

A 6.4b precisa de folha física de **outra** prova. Entra `fixtures/prova-2.json` como definição, e o
pacote sai por `GoldenWriterTest`, atrás da flag `platos.golden.write` — o mesmo caminho de
`prova-referencia`, sem mecanismo novo.

**A prova nova é adversarial, e não mínima.** Ela declara os **mesmos identificadores de item e as
mesmas posições** da `prova-referencia`, mudando só o `short_id`. A tentação era o contrário — uma
prova qualquer, com itens quaisquer —, e ela está errada: `ObjectiveScoring` já recusa folha cujo
conjunto de itens diverge da variante, então uma segunda prova diferente seria barrada por essa
conferência posterior, e a camada (c) ficaria **sombreada**. O teste passaria, a folha errada seria
recusada, e nada disso teria a ver com identidade — (c) poderia não existir.

Com os itens coincidentes, (c) é a única coisa entre a folha errada e uma nota plausível: removê-la
faz `ObjectiveScoring` apurar a folha da `prova-2` contra o gabarito da `prova-referencia` e
produzir número. É essa a forma de falha que ADR-0013 nomeia — íntegro e errado —, e é a única
construção de fixture em que a mutação da tarefa 8.3b prova alguma coisa.

Ela continua não sendo oráculo de nota: o que ela precisa carregar é um QR que diz outra prova.

**Isto não é regravação do golden.** `prova-referencia.layout.json` não muda, então a paridade e a
fidelidade não são reabertas por esta fatia — o artefato novo é adicional, e o golden existente sai
byte a byte igual. Se em algum momento a fatia mexer no golden, a paridade fecha na mesma sessão.

### 9. O asset e a tarefa que o embute saem juntos

`assets.open` some da `ScanActivity` e `EmbedPackageTask` some do `build.gradle.kts`, no mesmo
commit em que o gate entra. Deixar a tarefa de build viva com o consumidor morto deixaria o pacote
dentro do APK sem ninguém para notar — e a spec afirma que **não há pacote entre os recursos
empacotados**, o que é verificável sobre o APK e não sobre a intenção.

Os testes instrumentados (`CorpusInstrumentedTest`, `SheetReaderInstrumentedTest`,
`LayoutMapRendererInstrumentedTest`) continuam lendo a fixture pelos assets **de teste**, que é
outro conjunto e não vai para o APK de produção.

### 10. O harness de publicação de fixture fica, e fica cercado

A §14.1 exige duas provas publicadas antes de qualquer conferência da seção 9, e esta fatia **não
tem rota de publicação**: as quatro rotas do servidor são `GET`, e `ExamPublication` não está ligada
ao `Application`. Isso é coerente — a 4a é fatia de *pull* —, mas deixa a §14 dependendo de uma peça
que não existe. Descartar o harness depois de publicar faria a próxima pessoa reescrever exatamente
aquilo que a 9.7 escreveu para não ser reescrito.

Ele fica, então, como `PublicarFixturesNoBancoRealTest`. **Não é o caminho de publicação do
produto** — publicação de verdade, com professor autenticado e prova própria, é escopo da fatia 7.
A distinção é sustentada por três cercas estruturais, e não por disciplina de quem lê:

1. mora em `src/test`, logo **não existe** na imagem publicada no GHCR;
2. só age com `-Dplatos.publicar.fixtures=true`, no padrão já usado por `platos.golden.write`;
3. publica **fixture versionada**, lida de `platos.fixtures.dir`; não aceita prova arbitrária.

O `content_hash` devolvido é conferido contra o SHA-256 dos bytes do `.package.json` versionado,
calculado por `MessageDigest` — que não compartilha código com o `Sha256` do domínio que produziu o
valor sob julgamento. Publicação que divergir da fixture fica vermelha em vez de gravar no banco um
pacote que ninguém afirmou.

`DATABASE_USER` é obrigatório aqui, ao contrário de `AppConfig`, que cai no padrão `app_backend`: o
Session Pooler exige o usuário com o sufixo do projeto, e o padrão falha na autenticação com erro
que não aponta para a causa.

**Quando a fatia 7 entregar publicação de verdade, reavaliar se este harness ainda se justifica.**

### 11. Acompanhamento: rede inalcançável não é a mesma coisa que vínculo perdido

Descoberto em 2026-09-08, na conferência em aparelho, e **não corrigido nesta fatia**.

A decisão 10 da fatia 4a-zero manda o arranque com credencial guardada ir a `Consultando` e
reconsultar `/me/organizations`, com `SessaoGuardada` como fonte única de verdade e `DeviceState`
como função dela mais o que a API respondeu. **Essa decisão continua certa, e não está sendo
substituída.** A razão que a sustenta — não ter um segundo lugar onde mora "o que o aparelho sabe" —
não mudou, e a proteção que ela dá é real: quando o **servidor responde** que o usuário não pertence
mais àquela organização, a escolha guardada precisa mesmo cair, senão o aparelho segue operando sob
um vínculo que a instituição já revogou.

O que é informação nova é que **os dois casos estavam sendo tratados como um**:

| Caso | O que o servidor disse | O que deve acontecer |
|---|---|---|
| Vínculo perdido | respondeu, e a organização não está na lista | derrubar a escolha guardada — **é a decisão 10, e está certa** |
| Rede inalcançável | **não respondeu nada** | hoje também derruba, e é aí que está o buraco |

O segundo caso não existia como cenário com consequência até esta fatia: antes dela não havia pacote
guardado, então não havia nada de útil a fazer offline, e "sem rede o aplicativo não serve" era
verdade sem custo. Com cache conferido no aparelho, passa a haver — e o aplicativo continua parando
no arranque, com o pacote intacto em disco a dois passos de distância.

**Isto é atualização de uma decisão registrada com informação nova, não troca por preferência**, e o
`design.md` arquivado da 4a-zero não é reescrito: ele descreve corretamente o que foi decidido com o
que se sabia. A mudança, quando vier, é fatia própria — e precisa decidir por quanto tempo uma
escolha guardada vale sem revalidação, que é a pergunta que a decisão 10 não teve de responder.

Registrado também em `docs/architecture/ARQUITETURA-FINAL-v3.md` §16, como risco de entrega da §10:
a promessa de captura offline é do produto, e não cobertura de uma fatia.

### 12. A expiração é exceção à guarda de `Consultando` — correção de regressão desta fatia

Encontrada em aparelho em 2026-09-08 (tarefa 9b.1), por acidente de relógio: o token do aplicativo
completou 60 min durante a conferência.

`aoConsultarOrganizacoes` descarta resultado que chegue fora de `DeviceState.Consultando`, e a guarda
é certa — o KDoc dela nomeia dois casos reais, e ambos continuam valendo. O que mudou é que **esta
fatia criou o primeiro caso de chamada autenticada feita fora da consulta**: `provas()` e `pacote()`
rodam com o aparelho em `Ativa`. Para elas, o 401 do interceptador caía na guarda e sumia.

A correção é mínima e não mexe na guarda: a expiração é tratada **antes** dela, porque expiração não
é resultado de consulta — vem do interceptador e pode chegar de qualquer chamada autenticada. As duas
proteções documentadas seguem intactas, porque quem as exercita é `Falhou` e `Chegaram`, que
continuam sob a guarda. E sair continua sendo sair: expiração que chegue com o aparelho já na entrada
não reescreve o motivo.

**O que ficou por fazer, e é modelagem e não defeito:** `SessaoExpirada` continua morando em
`ResultadoDasOrganizacoes`, e não é um resultado de consulta. Movê-la para um evento próprio tornaria
o defeito estruturalmente impossível em vez de evitado por ordem de linhas. Não foi feito aqui porque
a fatia corrige uma regressão e não refatora (`CLAUDE.md` regra 6), e porque tirar um caso da
interface selada mexe em testes que não têm nada a ver com isto. Fica nomeado para quem tocar em
`DeviceSession` a seguir.

Visto falhar antes de existir: `sessao_expirada_em_ativa_tambem_volta_para_a_entrada` ficou vermelho
com a mensagem "estado ficou Ativa(...)", e o cenário vizinho — expiração depois de sair — ficou
verde na mesma execução, provando que a guarda já protegia o que dizia proteger.

## Risks / Trade-offs

**A camada (b) vira portão de compatibilidade, e ela é estrita por natureza** → é o comportamento
querido — APK antigo contra pacote novo recusa em vez de produzir nota plausível e errada —, mas
significa que qualquer campo novo no `ExamPackage` deixa de ser legível por versões anteriores. O
canal declarado para isso continua sendo `min_renderer_version`, que dá a frase certa ("atualize o
aplicativo"); (b) é a rede embaixo, para o campo que alguém esqueceu de declarar. Mitigação: o gate
confere a versão **antes** de (b) falhar, então a frase certa chega primeiro no caso previsto.

**Charset ou BOM no caminho de saída quebraria tudo** → quebraria alto: a primeira chamada real
falha na camada (a), com o hash divergindo. Falha ruidosa e imediata é o desfecho aceitável aqui, e
o teste que a pega compara bytes contra `exam_package.content` lido do banco, não contra outra
serialização.

**Custo de CPU da conferência a cada abertura** → SHA-256 sobre ~100 KB é da ordem de
milissegundos em aparelho, e a reserialização canônica é da mesma ordem. Fica fora da thread
principal de qualquer forma, porque o pull já é suspensão.

**O cache cresce sem limite** → escopado por organização e apagado no logout. Com uma prova por
turma e ~100 KB por pacote, a pressão de espaço não é plausível nesta escala; política de expurgo
sem evidência seria abstração prematura. Fica registrado como coisa a revisitar se a 4b trouxer
captura durável, que é o que de fato ocupa espaço.

**`MockEngine` não tem tempo limite de soquete** → é a lição que a 4a-zero pagou em aparelho: nenhum
teste de JVM desta base pega tempo limite do engine. Toda conferência de rede desta fatia tem par em
aparelho, contra o serviço real, e não só em JVM.

**§10 fica cumprida pela metade até a 4b** → nomeado na proposta e aqui, e não implícito. O que esta
fatia entrega é a recusa explicada; o que falta é capturar mesmo assim e corrigir depois.

## Migration Plan

Contrato antes do consumidor (regra 1), e a ordem é a que reduz risco:

1. **Servidor**: as duas rotas, com os testes de bytes e de fronteira. Nenhuma migração de banco —
   `exam` e `exam_package` já têm tudo. Reversível: rota nova sem chamador não muda comportamento
   nenhum.
2. **Aparelho, transporte e cache**: `retornoDeBytes`, a porta do cache, o verificador. Ainda sem
   trocar o caminho da `ScanActivity`. Reversível: código novo sem consumidor.
3. **Aparelho, o caminho**: `PreparoDaProva`, as telas, o gate, e a **remoção do asset** — os quatro
   no mesmo commit, porque é o commit em que o caminho antigo deixa de existir e o novo passa a
   responder. É o ponto de não-retorno da fatia, e é deliberado que ele seja um só.
4. **Fixture da segunda prova** e a conferência física da 6.4b.

Reversão, se a 3 der errado: reverter o commit devolve o asset e a tarefa de build juntos. Depois de
publicado, não — e é por isso que a conferência em aparelho vem antes de publicar.

## Open Questions

Nenhuma que mude spec, abordagem ou tarefas. O tempo limite de 90 s continua provisório (herdado da
4a-zero, tarefa 4.7a), e o pull de ~100 KB é a primeira chamada desta base grande o bastante para
dizer algo sobre ele — a medição entra na conferência em aparelho, sem bloquear a fatia.
