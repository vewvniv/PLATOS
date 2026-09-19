## Context

A motivação está em `proposal.md` — *Why*. O veículo, os commits e as proibições vêm da **ETAPA 5**
de `docs/plano-de-correcao-antes-da-fatia-5.md`, e este documento não os reabre: registra-os como
decididos.

O estado de partida:

- `ResultadosEmRoom.abrir(context)` é `Room.databaseBuilder(...).build()` — **uma instância nova por
  chamada**. Três chamadores de produção sobre o mesmo `outbox.db`, nenhum fecha.
- `ScanActivity.onCreate` já tem um gate: `organizacao` ou `contentHash` ausentes, ou pacote que não
  volta do cache, levam a `SemPacoteScreen` e a câmera **não abre**. O `short_id` **não** está nesse
  gate — ele é lido depois, e `prova` fica como campo nulável.
- `ScanActivity.gravar` tem `val organizacao = organizacao ?: return` e `val prova = prova ?: return`.
- `MotivoDaBarragem` tem **cinco** entradas: `SEM_REDE`, `PACOTE_AUSENTE`, `CONFERENCIA_FALHOU`,
  `VERSAO_INSUFICIENTE`, `ROSTER_AUSENTE`. Ele é do gate de pré-voo, em `SessaoActivity`, **antes** do
  `Intent`.
- Dois testes instrumentados já usam o caminho de produção **e** apagam o arquivo entre cenários:
  `GravacaoNoFioPrincipalInstrumentedTest` e `SegundoMembroInstrumentedTest`, os dois com
  `context.deleteDatabase("outbox.db")` em `@Before`/`@After`.
- Dois outros constroem a base com **nome próprio** e guardam a referência:
  `OutboxEmRepousoInstrumentedTest` e `ApagamentoLocalInstrumentedTest`.

Duas restrições duras:

**R1 — O pendente é o único exemplar de uma correção já feita.** É a decisão registrada em
`ResultadoPendente`, e é o que faz de 3.2 e 3.3 defeitos sérios em vez de arrumação. Nada nesta
mudança pode abrir caminho para apagá-lo antes da confirmação.

**R2 — Só o aparelho decide.** As duas travas desta mudança vivem em camadas que a suíte de JVM não
alcança: a topologia de instâncias do Room e o gate de uma `Activity`. **Ambiente: emulador ou
aparelho — P22 vale, perguntar antes.**

## Goals / Non-Goals

**Goals**

- Que exista **uma** instância de `BaseDoOutbox` por processo, e que isso seja afirmado **pelo
  caminho de produção**, não por uma topologia de teste.
- Que o caminho pelo qual uma nota apurada é descartada em silêncio **deixe de existir**, em vez de
  passar a ser tratado.
- Que a spec de `result-sync` descreva o comportamento atual no campo cuja semântica a fatia mudou.

**Non-Goals**

Ver *Decisions* 5 a 9: são decisões **já tomadas**, e não alternativas em aberto.

## Decisions

### 1. O acessador único guarda a instância no companion, e constrói com `applicationContext`

`abrir(context)` passa a devolver **sempre a mesma instância**. O `applicationContext` não é detalhe:
guardar no companion uma instância construída com o `Context` de uma `Activity` a manteria viva pelo
tempo do processo — trocaria um vazamento de conexão por um vazamento de `Activity`, que é pior
porque é invisível.

**Os três chamadores não mudam.** Eles já chamam `abrir`, e é por isso que o conserto cabe num commit
que não toca `SessaoActivity`, `ScanActivity` nem o worker.

### 2. Nenhum dos chamadores passa a fechar a base

Fechar não entra. Com uma instância por processo, o dono é o processo, e `close()` chamado por
qualquer das duas `Activity` ou pelo worker derrubaria a base debaixo dos outros dois — que é um
defeito pior do que o que se está consertando, e mais difícil de ver. O Room fecha ao processo
morrer.

### 3. O acessador único alcança dois testes instrumentados que apagam o arquivo, e isso é decidido aqui

`GravacaoNoFioPrincipalInstrumentedTest` e `SegundoMembroInstrumentedTest` chamam
`ResultadosEmRoom.abrir(context)` **e** `context.deleteDatabase("outbox.db")` entre cenários. Com a
instância guardada, o segundo cenário receberia a instância que aponta para o arquivo apagado.

**A decisão é dar ao acessador um ponto de reinício visível só a teste**, que fecha a instância e
limpa a referência, e que esses dois testes chamam **antes** de apagar o arquivo.

**A alternativa — os testes pararem de apagar o arquivo e passarem a limpar linhas — está recusada**,
e a razão é R1 invertida: limpar linhas exigiria um método de apagamento em massa em
`ResultadosPendentes`, que é exatamente a ferramenta que a lista de proibições manda não criar.
Apagar o **arquivo**, de fora, por API da plataforma, não põe essa ferramenta ao alcance de
`sair`.

**O que isso custa, e fica dito:** é uma porta de teste, e portas de teste são o que 3.2 nasceu de
ter. A diferença é o que cada uma faz — a antiga **substituía** a topologia de produção por outra, e
esta **restaura** o estado inicial da topologia de produção, que continua sendo a exercitada. O
cenário novo da 5.A mede o caminho de produção diretamente, e é ele que impede a porta de virar
disfarce.

### 4. O `short_id` entra no gate de `onCreate`, e os campos deixam de ser nuláveis

**A correção não é tratar o nulo — é tornar o estado inconstruível.** A decisão de "tem tudo o que
precisa" já mora em `onCreate`. O `short_id` passa para o mesmo lugar: ausente, a câmera não abre,
com motivo próprio. Com isso `prova` e `organizacao` deixam de ser nuláveis no campo, e `gravar`
perde os dois `return`. **O caminho silencioso deixa de existir em vez de ser tratado.**

Tratar o nulo dentro de `gravar` — com uma mensagem, um log, um estado de erro — foi considerado e é
pior: manteria construível um estado que não deveria existir, e a mensagem teria de explicar ao
professor, com a folha na mão e a nota na tela, algo que só podia ter sido decidido antes de a câmera
abrir.

### 5. `MotivoDaBarragem` **não** ganha entrada nova — decidido

Os cinco motivos do gate são de `SessaoActivity`, **antes** do `Intent`, e o gate não tem como saber
que o extra vai faltar: quem monta o `Intent` é ele próprio. O motivo novo vive onde a ausência é
descoberta, que é `onCreate`, ao lado da recusa que já existe ali. O requisito é que ele seja
**distinguível** dos cinco, e não que more no mesmo enum.

### 6. O `short_id` ausente **não** ganha valor padrão nem é derivado de `examPackage.meta.examId` — decidido e recusado

Está na lista de proibições da ETAPA 5. **A KDoc de `EXTRA_SHORT_ID` já explica por quê**: os
escritores do roster — o pull e o gate — usam `prova.shortId`, e ler por outro caminho faria o leitor
depender de uma igualdade que ninguém afirma. Se ela se rompesse, `ler` devolveria `null` e **toda**
folha cairia em silêncio no token com "não está no roster".

**E a etapa 3 afirmou essa igualdade no domínio, não aqui.** A asserção que substituiu a renomeação do
achado 5.4 vive em `packages/domain`, sobre o pacote publicado. Usá-la como licença para ler por
outro caminho **no aparelho** seria estender uma afirmação para além do que ela afirma.

### 7. `allowMainThreadQueries()` não entra em teste nenhum — decidido e recusado

Está na lista de proibições da ETAPA 5. **Foi exatamente isso que afrouxou o oráculo** na trava que a
produção impõe, e o aplicativo morria ao escanear folha válida com a suíte inteira verde. O
`GravacaoNoFioPrincipalInstrumentedTest` existe por causa disso, e a KDoc dele diz que a ausência **é**
o requisito.

### 8. `fallbackToDestructiveMigration` não entra — decidido e recusado

Está na lista de proibições da ETAPA 5. **Apagar a base numa atualização destruiria correção que não
subiu** — R1. A KDoc de `abrir` já registra isso, e esta mudança a preserva.

### 9. `ResultadosPendentes` **não** ganha `apagarDaOrganizacao` — decidido e recusado

Está na lista de proibições da ETAPA 5. **A ausência é o requisito, e está escrita**: seria a
ferramenta pronta para alguém chamar de dentro de `sair`, e sair com pendente na fila é precisamente
o que a spec proíbe. É também por isso que a decisão 3 escolheu apagar o arquivo em vez de limpar
linhas.

### 10. 5.A e 5.B **não** se fundem num commit — decidido

Está na lista de proibições da ETAPA 5. **São defeitos diferentes, com mutações diferentes.** Um
commit só faria um vermelho deixar de dizer qual dos dois o causou.

### 11. 5.C vai como delta, e **não** como edição direta da spec principal — decidido

O atalho das cinco condições do `CLAUDE.md` exige "nenhum texto novo é inventado", e **trocar "vazio"
por "ausente" inventa texto** — mais ainda porque entra junto a razão, que hoje não existe em spec
nenhuma. A condição 3 não se cumpre, e o veículo é mudança.

O `MODIFIED` carrega o bloco **inteiro e atual** do requisito, incluindo o texto de conferência de
proveniência que a ETAPA 4 sincronizou horas atrás. Carregar a versão anterior faria o archive
reintroduzir o estado antigo em silêncio — é a condição 5, e ela é a que mais custa.

### 12. A regra de parada, e ela vale sobre as duas tarefas de verificação

É a **regra 0.5** do `docs/plano-de-correcao-antes-da-fatia-5.md`:

> Toda etapa prevê **qual conjunto de cenários deve cair** sob a mutação. Se o conjunto real for
> diferente do previsto — mais, menos, ou outros —, **pare**. Não conserte o instrumento, não afrouxe
> a asserção, não ajuste a previsão em silêncio. Escreva o conjunto real ao lado do previsto e diga o
> que ele significa (P7, P12, P14).

A previsão errada da 6.6 da fatia do outbox é o precedente, e as duas da etapa 3 são as mais
recentes.

**Em particular, e a ETAPA 5 o diz com estas palavras:** os três "não" da tabela da 5.A "são o ponto:
eles constroem a própria base e **continuam certos sobre o que medem**. **Se caírem, a mutação não
isolou nada.**"

## Risks / Trade-offs

**O acessador único quebra os dois testes que apagam o arquivo** → Decisão 3. É a consequência mais
provável de a mudança ficar maior do que parece, e está decidida antes de começar em vez de
descoberta no meio.

**A porta de reinício vira disfarce** → O cenário novo da 5.A mede o caminho de produção
diretamente — duas chamadas a `abrir`, a mesma instância —, e a mutação da 5.A o derruba. Uma porta
que substituísse a topologia não passaria nessa asserção.

**Guardar a instância num companion com o `Context` errado vaza uma `Activity`** → `applicationContext`,
decisão 1, e os chamadores já o passam.

**Tornar `prova` não-nulável pode empurrar o nulo para outro lugar** → É o que a mutação da 5.B mede:
restaurado o campo nulável e o `?: return`, **só** o cenário novo cai. Se cair algum cenário de
apuração, de gravação ou de gate, o nulo foi empurrado em vez de eliminado, e a regra de parada
dispara.

**O emulador não reproduz a concorrência real do worker com a câmera** → Aceito e **não medido**. O
cenário afirma que escrita e leitura pelos dois caminhos convivem; ele não reproduz rede intermitente
com câmera aberta em aparelho real. Fica em "o que não foi verificado" (P8).

## Migration Plan

Não há migração: nenhum schema de Room muda, nenhuma versão de base sobe, nenhum formato em disco é
tocado. O `outbox.db` gravado por uma versão anterior é lido pela nova sem nada a converter — o que
muda é **quantas instâncias** o abrem.

**Reversão:** devolver `abrir` ao `build()` por chamada e restaurar os dois `?: return` reverte tudo,
e é barato — nenhum dado gravado depende da mudança. É, aliás, exatamente o que as duas mutações
fazem, e é por isso que elas são mutações honestas: cada uma é a reversão do defeito que a etapa
conserta.

## Open Questions

Nenhuma que altere as specs, a abordagem ou as tarefas.
