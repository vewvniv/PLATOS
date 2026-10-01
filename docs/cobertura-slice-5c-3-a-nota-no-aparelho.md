# Cobertura — slice-5c-3-a-nota-no-aparelho

Data da verificação: 2026-10-01 (hora local +02:00). Execução inline do plano
`docs/superpowers/plans/2026-10-01-slice-5c-3-a-nota-no-aparelho.md`.

**Comando:** `./gradlew :packages:domain:jvmTest :apps:api:test :apps:android:testDebugUnitTest`, com o Docker de pé e **sem**
emulador. Contagens de `@Test` por módulo, lidas do relatório: `:packages:domain:jvmTest` **470** (eram 467),
`:apps:api:test` **204** (inalterado: o servidor não mudou), `:apps:android:testDebugUnitTest` **451** (eram 400). O
`:apps:android:assembleDebug` e o `compileDebugAndroidTestKotlin` compilam. `tools/parity/fio.mjs` e
`tools/parity/answer-kind.mjs` passam; o corpo que o aparelho monta é, byte a byte, o literal que o servidor lê.

## Como cada verificação foi vista falhar

Cada mutação foi injetada **sozinha** em produção, a suíte do aparelho foi rodada (vermelha), o arquivo foi revertido com
`git checkout`, e a árvore voltou a limpa.

| Mutação | Teste que caiu |
|---|---|
| `Caderno.normalizado` deixa de poupar o caderno corrigido | `CadernoCorrigidoTest > normalizado nao devolve a nao vista a regiao de um caderno corrigido` |
| `eliminarAoConfirmar` deixa de conferir `capturaDaParcial` | `EliminarAoConfirmarTest > a confirmacao de uma nota antiga nao toca o caderno de outra captura` |
| `arquivosAEliminar` ignora `escaneamentoAberto` | `RetencaoDaRespostaTest > com o escaneamento aberto o arquivo sem referencia e mantido` |
| `estadoDaDiscursiva` deixa de proteger o caderno que aguarda nota | `NotaNaSessaoTest > outro aluno nao substitui o caderno completo sem nota` (e mais dois do mesmo arquivo) |

Os testes do plano nasceram vermelhos por **compilação** (o código que exercitam não existia) e ficaram verdes depois da
implementação; os quatro acima provam que também falham por **comportamento**.

## O que foi verificado

- `lerPontos` no domínio: valor exato, mensagem que nomeia o campo e diz "ponto decimal", recusa de 3 casas e de negativo.
  A api passou a usá-la e os 204 testes dela seguem verdes **sem mudança de asserção**.
- O caderno: `capturaDaParcial`, `corrigido`, `questionId`, `aguardaNota`, `corrigidoSemRespostas`; caderno guardado antes
  continua legível; `normalizado` poupa o corrigido.
- A sessão: o id da parcial é cunhado na transição e o caderno o guarda; `darNota` decide sem mudar o caderno, recusa
  caderno incompleto, corrigido, sem parcial, faixa e discursiva faltando, e o duplo toque; `confirmarCorrigido`,
  `falhouAGravacao`, `descartarESeguir`; a substituição protegida (4 cenários da spec) e a captura nova da mesma folha.
- A leitura do que o professor digita (`EntradaDaNota`): `1,5` é recusado com "ponto decimal" e nunca lido como `15`,
  campo em branco, 3 casas, negativo, não numérico, acima do valor, zero e máximo em todas.
- O corpo da nota (`NotaDoAparelhoTest`), a rota `graded` e a classificação 4xx/5xx (`ApiPlatosNotaTest`), a rota antiga
  intacta.
- O gancho de confirmação: só a rota `nota` elimina; só o caderno da captura que a nota completa; a queda entre gravar a
  nota e gravar o caderno não impede a eliminação; arquivo que falha é contado e não interrompe; falha do gancho não
  desfaz a confirmação; não confirmada não chama o gancho.
- A marca: a regra "ninguém referencia" não roda com o escaneamento aberto e o teto de 30 dias roda; a marca é lida **a
  cada arquivo**, na hora de eliminar (teste em que o escaneamento abre no meio da varredura). *(A primeira versão era
  uma trava que `onStart` esperava na thread principal; foi trocada na passada de correção, abaixo.)*

## Decisões de execução (rulings)

Estão no ledger da execução e nos desvios do topo do plano. As que mudaram código em relação ao design:

1. `rota` guarda `resultado`/`nota`; a nota e o caderno corrigido são gravados **em sequência** (dois bancos Room), com o
   gancho achando o caderno por `capturaDaParcial`.
2. `RegiaoDoCaderno.questionId` foi acrescentado; `darNota` não muda a sessão e `confirmarCorrigido` aplica depois.
3. `aoConfirmar` entra **antes** de `enviar` no construtor de `EnvioDeResultados` (o `enviar` segue último, para os
   chamadores com lambda final não quebrarem).
4. O `onStop` da `ScanActivity` fecha a marca **depois** de o caderno ir ao Room (`invokeOnCompletion`): fechar antes
   deixaria uma varredura apagar como órfã a resposta que o caderno em memória referencia.

## Limitações conhecidas (não mitigadas, P8)

- A marca "escaneamento aberto" supõe **processo único**. Se um dia houver segundo processo, o requisito é reaberto.
- Reapresentar de propósito a folha de um aluno **já corrigido** começa captura nova e gera outra parcial e outra nota
  (revisão nova no servidor): é a regra da spec, e um reavistamento acidental da mesma folha tem o mesmo efeito.
- O analisador grava os arquivos do **outro** aluno antes de a sessão decidir proteger o caderno; eles ficam órfãos até a
  varredura da próxima abertura (a de segundo plano os poupa de propósito enquanto o escaneamento está aberto).
- Corrigir uma nota **já gravada** não existe: depois da confirmação a imagem some.

## Rodada instrumentada, nos dois aparelhos (2026-10-01, depois da revisão final)

`./gradlew :apps:android:connectedDebugAndroidTest` **sem filtro**, 173 testes por aparelho, `BUILD SUCCESSFUL` nos dois: o
emulador `platos-atd34` (Android 14) e o **Xiaomi `2511FPC34G` (Android 16)**, este com `permissaoManual=true` e o toque do
Leon em "Instalar" e "Permitir". Nenhuma falha; 2 pulados no Xiaomi, ambos de `AcumuloDeInstanciasProbe` (sonda
preexistente). Os três testes novos passaram nos dois: `MigracaoDoOutboxInstrumentedTest` (a linha antiga sobrevive e vira
`resultado`: **a aspa do `defaultValue = "'resultado'"` estava certa e o Room validou o schema migrado**),
`NotaPendenteInstrumentedTest` e o cenário novo de `ApagamentoLocalInstrumentedTest`.

## O que NÃO foi verificado

- **A tela de nota (Compose)** não tem teste de renderização; a lógica de leitura está em `EntradaDaNota` (JVM). Teclado
  decimal, rolagem e a confirmação em duas etapas só se veem no aparelho.
- **A câmera ao vivo e a nota de ponta a ponta contra o servidor** (escanear, dar a nota, ver subir, ver as imagens
  sumirem) são do Leon: o toque de permissão do Xiaomi é manual.
- **O `VarreduraPeriodicaWorker`** agendado e executado pelo sistema com o aplicativo fechado não foi observado; o
  agendamento (`ExistingPeriodicWorkPolicy.KEEP`, 24 h) está no código e compila.
- **A janela entre gravar a nota e gravar o caderno** é inofensiva por construção (o gancho acha o caderno pela captura),
  mas não foi provada com queda real de processo.
- **Nenhum papel**: a mudança não toca a folha.

## Para o archive (P27): o que ele diz de cada linha do §16 que esta mudança alcançou

- **`O teto de 30 dias das respostas só roda quando o aplicativo abre` (`5c`)**: **paga** (agendamento periódico e trava);
  o archive a retira e diz o que ficou de fora (a marca supõe processo único; o worker não foi observado em aparelho).
- **`A política §10.8 diverge do comportamento: sair e a revogação preservam o pendente`**: acrescentar que a imagem some
  ao confirmar a nota e que a nota pendente também é preservada ao sair.
- **`Migration não é aplicada por nenhum pipeline` (`antes-de:migration-da-5-em-producao`)** e **`Implantar a API da 5a …`
  (`antes-de:implantar-api-da-5a`)**: o aparelho passa a **chamar** a rota `graded`; nenhuma das duas vai a produção por
  esta mudança, e os eventos seguem por declarar. Também há agora uma migration **do aparelho** (Room 1→2), que não é a
  do servidor.
- **`A retenção executável da classe B`** (`antes-de:primeira-eliminacao`): sem prazo novo.
- **`A guarda de dívida não lê a tabela "Aberto"` (`5`)**: **não foi tomada aqui**; segue `5`, limite a abertura da fatia
  6, mudança própria ainda não proposta. Silêncio não é reconciliação.
- **`A região discursiva ainda não passou pelo aparelho nem pelo papel`** e **`O limiar do desvio … sem letra de aluno`**
  (`6`): a tela de nota usa o sinal de desvio e a região passa pela câmera, não pelo papel; sem reagendamento.

## Revisão final e a passada de correção

Revisor com contexto novo (modelo mais capaz disponível), sobre a branch inteira. **Nenhum Critical.** Três Important,
reclassificados por efeito e corrigidos numa passada, cada um com teste visto falhar antes (RED por compilação, pois a
API que o teste exige ainda não existia) e a suíte verde depois:

1. **Corrida entre `darNota` e o `onStop`.** Com a nota em gravação, o `onStop` guardava o caderno em memória, ainda sem a
   marca, e podia sobrescrever o caderno corrigido no Room: "Dar a nota" voltava para uma folha já corrigida e uma
   segunda nota entrava na fila. Agora `ScanSession.cadernoParaGuardar` é nulo enquanto a nota é gravada.
   Teste: `NotaNaSessaoTest > enquanto a nota e gravada, nao ha caderno a guardar ao parar, e depois ha o certo`.
2. **A trava segurada durante o I/O da varredura bloqueava a thread principal no `onStart`.** Trocada por uma marca
   `@Volatile` lida a cada arquivo, na hora de eliminar; o argumento de por que basta está na KDoc de
   `EscaneamentoAberto`. Teste: `RetencaoDaRespostaTest > o escaneamento que abre no meio da varredura poupa os orfaos
   seguintes e nao o vencido`. O teste das duas threads saiu junto com a trava.
3. **Falha parcial da gravação composta virava "tente de novo" e duplicava a nota.** `gravarNota` agora só devolve
   `false` se a **nota** não gravou; falha do caderno ou do agendamento depois de a nota estar durável é engolida (o gancho
   acha o caderno pela captura, e o envio sobe na próxima sessão). Testes: `GravacaoDaNotaTest` (4 cenários).

Contagem final do aparelho: **456** testes (eram 451 antes da passada).

**Minors adiados** (não entraram na passada, por regra): `worth = 0` nunca exercitado em teste; `passadaDeEnvio` (despacho
por rota e gancho) sem teste direto, como já era antes; `passadaDeEnvio` abre `caderno.db` mesmo sem nota na fila.

**Resíduo conhecido da correção 1:** a ordem entre uma escrita do `onStop` lançada *antes* do toque em "Confirmar" e a
escrita da nota, ambas em `Dispatchers.IO`, não é garantida; a janela é de milissegundos e o efeito, o mesmo "Dar a nota"
de novo (uma revisão nova no servidor, sem perda).
