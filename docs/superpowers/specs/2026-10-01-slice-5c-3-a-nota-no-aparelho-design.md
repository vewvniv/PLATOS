# slice-5c-3-a-nota-no-aparelho — design (o COMO)

**Requisitos de entrada (o QUÊ, já aprovados):** `openspec/changes/slice-5c-3-a-nota-no-aparelho/proposal.md` e
`specs/scan-session`, `specs/result-sync`. Este documento não os altera. Se uma decisão daqui revelar mudança de
requisito, **pare e use `/opsx:update`**.

**Escopo:** o aparelho. O servidor, o contrato do fio e a regra "humana vence" são da 5c-2 (arquivada, ADR-0021) e não
mudam. **Caminho do brainstorming:** arquitetural (domínio compartilhado, caderno persistido, outbox com migration,
agendamento em segundo plano).

## 1. Decisões (aprovadas pelo mantenedor em 2026-10-01)

| # | Decisão | Por quê |
|---|---|---|
| D1 | **A tela de nota abre por um botão "Dar a nota"** no caderno completo e sem nota; não abre sozinha. | A câmera segue rodando; a tela é uma etapa que o professor escolhe. Combina com a confirmação de "descartar e seguir". |
| D2 | **O outbox guarda a nota na mesma tabela**, com duas colunas novas (`rota`, `completa_captura`) e **migration real 1→2**. | Mantém "o resultado durável é a linha da fila" (KDoc de `ResultadoPendente`): uma contagem de não enviados ao sair, um escopo por organização, uma regra de apagar. Tabela própria duplicaria as três. Deduzir a rota pelo corpo contraria "o corpo congelado é a verdade". |
| D3 | **`EnvioDeResultados` ganha o gancho `aoConfirmar(envelope)`**, ligado no worker. | A decisão de eliminar mora numa classe sem Android, testável na JVM, e roda com o aplicativo fechado. Um segundo worker seria dois agendamentos para um evento. |
| D4 | **A trava do segundo plano é uma marca de processo "escaneamento aberto".** Com ela ligada, a varredura periódica **pula só a regra "ninguém referencia"**. | O caderno em memória só vai ao Room no `onStop`; durante o escaneamento uma resposta recém-gravada parece órfã para quem lê o Room. Idade mínima do arquivo daria um número sem evidência. |
| D5 | **O `completes_capture_id` vem do caderno.** `ScanSession` recebe um gerador de id; o caderno guarda `capturaDaParcial` na transição que já marca `entregue`. | Hoje `ScanActivity.gravar` cunha o id e ele some. Uma só fonte, e testável com id fixo. |
| D6 | **`ApuracaoParaEnvio` do domínio não ganha subtipo.** O aparelho tem um tipo irmão (`NotaPendente`). | O servidor faz `when` exaustivo sobre ele (`ProvenienciaDoResultado`, `ResultQueries`); o ADR-0021 adiou o subtipo "até haver consumidor", e o consumidor aqui é só o aparelho. Revisa o D8 da 5c-2 no sentido contrário ao que ela previu: não precisa estender. |
| D7 | **`lerPontos` sobe para o domínio** (regra 7). | Hoje é privada em `apps/api/.../GradedResultDto.kt`. Aparelho e servidor validam com o mesmo código. |

**Sem ADR.** O que é duradouro (decimal exata, revisão corrente) já está no ADR-0021. As decisões acima são de fatia e
ficam aqui e na proposta (a regra da casa: decisão de fatia não vira ADR).

## 2. Domínio (`packages/domain`, KMP) — contrato antes dos consumidores

- `lerPontos(texto, de)` passa a viver em `scoring` ao lado de `Pontos`: devolve `Pontos` ou lança
  `IllegalArgumentException("$de: …")`. `apps/api` passa a importá-la; o comportamento e as mensagens de 400 ficam como
  estão (o teste de `paraNotaSubmetida` segue passando sem mudança).
- Nada mais muda no domínio. `CorrecaoDoProfessor.completar`, `NotaDoProfessor`, `Pontos` e
  `GradedResultSubmissionDto` já existem e são o que o aparelho usa.

## 3. Aparelho

### 3.1 Caderno (`scan/Caderno.kt`)

- Campos novos, ambos com default (o JSON do caderno guardado continua legível; o Room do caderno **não** muda de
  versão, a linha é um `corpo` JSON): `capturaDaParcial: String? = null` e `corrigido: Boolean = false`.
- `normalizado` **não** devolve região a "não vista" num caderno `corrigido`: as referências dele a arquivos que o
  worker eliminou são esperadas. A tela nunca mostra imagem de caderno corrigido.
- `Caderno.corrigidoSemRespostas()`: o caderno com `resposta = null` em todas as regiões, `corrigido` e `entregue`
  mantidos, estado das regiões intocado. É o que o gancho de confirmação grava de volta.

### 3.2 Sessão (`scan/ScanSession.kt`)

- Construtor ganha `novoId: () -> String = { UUID.randomUUID().toString() }`. `ApuracaoNova.DeCaderno` passa a levar o
  `captureId` cunhado na transição, e o caderno o guarda em `capturaDaParcial` na mesma passada em que marca `entregue`.
  `ScanActivity.gravar` usa esse id em vez de cunhar outro (a prova só objetiva continua cunhando o dela).
- `darNota(pontuacoes: List<PontuacaoDada>): ResultadoDaNota`:
  - recusa se não há caderno, se está incompleto, se já está `corrigido`, ou se a `parcial` não é `Scored`;
  - chama `CorrecaoDoProfessor.completar(parcial, pontuacoes)`;
  - `Scored` → devolve `Corrigida(nota, completaCaptura = capturaDaParcial)` e marca o caderno `corrigido` e o estado;
  - `Rejected` → devolve `Recusada(reason)`, a frase do domínio, sem efeito.
- **Substituição protegida.** Em `estadoDaDiscursiva`, quando o aluno do quadro difere do caderno corrente e o caderno
  corrente está completo, entregue e não corrigido, a sessão **não** troca o caderno: o estado passa a
  `ScanState.NotaPorDarDeOutroAluno(cadernoCorrente, alunoNovo)`. `descartarESeguir()` perde o caderno
  (devolvendo os arquivos a eliminar, como `refazer`) e o quadro seguinte começa o caderno do outro aluno;
  `darNota` + gravar deixa o caderno `corrigido`, e a substituição passa a ser livre. Caderno incompleto ou corrigido
  segue o caminho de hoje.
- Caderno `corrigido` + a mesma folha lida de novo → **captura nova**: caderno novo do mesmo aluno, recorte pedido de
  novo (o predicado `jaTemResposta` já olha só a regra por aluno e região; o caderno novo começa sem respostas).

### 3.3 Tela (`scan/NotaTela.kt`, novo, Compose)

- Aberta por "Dar a nota" no `ScanScreen`, visível só com o caderno completo e sem nota.
- Uma linha por discursiva, na ordem da variante: o número impresso (o `rotulo` da região), a imagem
  (`carregarResposta`, a mesma da `RespostaTela`), o aviso de desvio quando sinalizado, o valor máximo e um campo decimal.
- Total ao vivo (parte objetiva + soma exata em `Pontos`); "Gravar" fica desabilitado até todas terem pontuação e
  mostra a recusa do domínio nomeando a questão. A confirmação mostra o total sobre o máximo da prova.
- Texto do campo → `lerPontos`; texto inválido nunca chega a `darNota`.

### 3.4 Gravação e envio (`outbox/`, `api/`)

- `NotaPendente(captureId, completaCaptura, organizacao, prova, studentToken, apuradoEm, nota: NotaDoProfessor)` e
  `corpoDoEnvio` que monta o `GradedResultSubmissionDto` (origem `teacher`, caminho `image`, `points` e `earned` como
  `Pontos.toString()`, `observations` da parte objetiva, `essay_grades`). `JSON` com `encodeDefaults` e `explicitNulls`,
  como o corpo atual.
- `ResultadosPendentes.guardar` aceita os dois tipos. A linha leva `rota` (`parcial` | `graded`) e `completa_captura`
  (nula na `parcial`).
- `ResultadoPendenteEntity` ganha as duas colunas; **`Migration(1, 2)`** com `ALTER TABLE … ADD COLUMN` e
  `DEFAULT 'parcial'` para a linha antiga. `BaseDoOutbox` vai a `version = 2`. **Sem `fallbackToDestructiveMigration`.**
- `EnvelopeDeEnvio` ganha `rota` e `completaCaptura`. `ApiPlatos.enviarNota` chama
  `POST /organizations/{org}/exams/{shortId}/results/graded`; a decisão de qual chamar é do `when` sobre `rota`, na
  fiação de `passadaDeEnvio`.
- `ScanActivity.darNota` grava o pendente **e** o caderno `corrigido` na **mesma** operação fora do fio principal
  (uma função nova, `gravarNotaEAgendar`), e só então agenda o `EnvioDeResultadosWorker`. Falha na gravação: a tela fica
  aberta com o motivo, e nada vai ao caderno.

### 3.5 Eliminação ao confirmar (`outbox/EnvioDeResultados.kt`)

- Parâmetro novo `aoConfirmar: suspend (EnvelopeDeEnvio) -> Unit = {}`, chamado **depois** de
  `apagarConfirmado`. Uma exceção dele é capturada e contada: **a confirmação já aconteceu e não se desfaz**.
- Função pura `eliminarAoConfirmar(envelope, cadernos, respostas)`: se `rota == graded`, lê o caderno de
  `(organizacao, prova)`, e se `capturaDaParcial == completaCaptura` e ele está `corrigido`, elimina cada arquivo
  referenciado (falha por arquivo é contada e não interrompe) e grava `corrigidoSemRespostas()`. Se o caderno já é de
  outro aluno ou captura, não faz nada: os arquivos são órfãos e a varredura os pega.
- O worker liga o gancho em `passadaDeEnvio`. O `ResumoDoEnvio` não muda; o diagnóstico ganha o número de arquivos
  eliminados e dos que falharam.

### 3.5.1 A corrida entre o gancho e o `onStop`

O worker regrava o caderno (sem referências) enquanto a `ScanActivity` pode ainda ter, em memória, o caderno `corrigido`
com referências. O `onStop` depois o sobrescreve com as referências a arquivos já eliminados. **É inofensivo, e a razão
é a regra, não a sorte:** o caderno é `corrigido` (a normalização o ignora, e nenhuma tela lê imagem dele), e a varredura
age sobre arquivos no disco, que já não existem.

### 3.6 Segundo plano (`scan/`, `outbox/`)

- `VarreduraPeriodicaWorker`: `PeriodicWorkRequest` de 24 h, trabalho único `ExistingPeriodicWorkPolicy.KEEP`, sem
  restrição de rede. Agendado em `SessaoActivity` na abertura (idempotente).
- `RetencaoDaResposta.arquivosAEliminar` ganha o parâmetro `escaneamentoAberto: Boolean`: com `true`, aplica só o
  teto de 30 dias (a regra "ninguém referencia" não roda). A abertura do aplicativo e a do escaneamento continuam
  chamando com `false`, **antes** da câmera, como hoje.
- A marca é `object EscaneamentoAberto { @Volatile var aberto }`, ligada em `ScanActivity.onStart` e desligada em
  `onStop`. Vale porque o `WorkManager` roda no processo do aplicativo. Se um dia houver segundo processo, a marca deixa
  de valer e **este requisito** deve ser reaberto.

## 4. Mapa requisito → mecanismo → teste

| Requisito / cenário | Mecanismo | Teste |
|---|---|---|
| Dar a nota sobre o caderno completo; só com completo e sem nota | `ScanSession.darNota`, botão do `ScanScreen` | JVM: `DarNotaTest` (completo aceita; incompleto, corrigido e parcial recusada recusam) |
| Pontuação fracionária e inválida (acima, negativa, 3 casas, não numérica, faltando) | `lerPontos` + `CorrecaoDoProfessor.completar` | JVM: `LerPontosTest` no domínio; `DarNotaTest` com 1.5 e 1.75; tela com a frase do domínio |
| A nota vira pendente; caderno corrigido não reentrega | `gravarNotaEAgendar`; `completouAgora` já exige `!entregue` | JVM: `EntregaDoCadernoTest` (não reentrega); instrumentado: `NotaPendenteInstrumentedTest` (grava as duas coisas e sobrevive ao processo) |
| A mesma folha lida de novo depois de corrigida: captura nova | caderno novo do mesmo aluno | JVM: `DarNotaTest` |
| Outro aluno não substitui caderno completo sem nota | `NotaPorDarDeOutroAluno`, `descartarESeguir` | JVM: `SubstituicaoProtegidaTest` (4 cenários: protege, descartar, dar a nota, incompleto segue) |
| Corpo da nota: contrato, chave própria, `completes_capture_id`, decimal exata, sem imagem nem arquivo | `corpoDoEnvio` da nota | JVM: `NotaDtoTest` (literal), `ARespostaNaoSaiDoAparelhoTest` estendido; paridade: `tools/parity/fio.mjs` exige o literal |
| O servidor aceita o que o aparelho monta | mesma `paraNotaSubmetida` | JVM em `apps/api`: o corpo do literal do aparelho passa `paraNotaSubmetida` |
| Rota própria, sem rede, 5xx, 4xx, reenvio com a mesma chave | `enviarNota`, `EnvioDeResultados` | JVM: `EnvioDeNotaTest` (MockEngine) |
| Sair avisa e conta a nota; nota sobrevive a sair e à revogação | mesma tabela | JVM: estende os testes de apagamento local; instrumentado: `ApagamentoLocalInstrumentedTest` com linha `graded` |
| Migration 1→2 não perde pendente | `Migration(1, 2)` | instrumentado: `MigracaoDoOutboxInstrumentedTest` (linha antiga vira `parcial`) |
| Confirmação elimina as imagens e só ela; falha de arquivo não desfaz | `eliminarAoConfirmar`, `aoConfirmar` | JVM: `EliminarAoConfirmarTest` (graded confere; graded de outra captura; parcial não elimina; falha de arquivo) |
| Nota gravada e não confirmada, ou recusada, mantém a imagem | gancho só no `Respondeu` | JVM: `EnvioDeNotaTest` |
| Caderno corrigido não volta a incompleto | `normalizado` | JVM: `CadernoNormalizadoTest` |
| Teto de 30 dias em segundo plano; trava | `VarreduraPeriodicaWorker`, `EscaneamentoAberto` | JVM: `RetencaoDaRespostaTest` (com e sem marca); instrumentado: o worker roda com o app fechado |
| Prova só objetiva não muda | caminho antigo intocado | JVM: os testes existentes, sem mudança de asserção |

**Teste no aparelho real** (câmera, toque de permissão, a nota de ponta a ponta contra o servidor): **só o Leon** — o
Xiaomi não concede a câmera por UiAutomation. Fica registrado como o que a fatia **não** verificou até esse dia.

## 5. Fora de escopo (e para onde vai)

- Servidor, banco, contrato, `apps/web`: 5c-2, já arquivada.
- Corrigir uma nota já gravada; nota por critério, por habilidade, `ai`/`text`: fatia 8 ou mudança própria.
- Finalizar caderno incompleto (`slice-5e-…`): a nota exige o caderno completo.
- Multi-caderno (fila de cadernos aguardando nota): descartado pelo mantenedor em 2026-10-01.
- Corpus de medição.

## 6. Riscos

- **Migration do outbox é a única coisa irreversível no aparelho.** Errar o `DEFAULT` ou esquecer a migration perde
  correção que não existe em outro lugar. Mitiga: teste instrumentado de migração com linha antiga, e nenhuma
  `fallbackToDestructiveMigration`.
- **A marca de processo é uma suposição de processo único.** Registrada no §3.6.
- **A tela de nota põe manuscrito de menor numa tela por mais tempo.** Já é classe H sob o prazo; a eliminação ao
  confirmar encurta, e a nota pendente sem rede mantém a imagem até 30 dias. Linha do §16 a atualizar no archive.
- **`ScanSession` cresce** (hoje 429 linhas). A nota e a substituição entram como funções pequenas; se passar de ~550,
  a extração de `substituicao` vira tarefa do plano, e não refatoração oportunista.
- **Race entre o gancho e o `onStop`:** analisada no §3.5.1.

## 7. Dívida (§16, P27)

`node tools/divida/divida.mjs` na abertura: fatia corrente `5c`, nenhuma linha vencida; vencem nesta fatia a da guarda
(`5`, **não tomada aqui**) e a do teto de 30 dias (`5c`, **paga aqui**). O archive retira a segunda e atualiza a
da política §10.8 (a imagem some ao confirmar a nota).

## 8. Ordem de entrega (cada item é um commit lógico; regra 1, contrato antes de consumidor)

1. `lerPontos` no domínio; a api passa a usá-la.
2. `Caderno` (`capturaDaParcial`, `corrigido`, `normalizado`, `corrigidoSemRespostas`) e `ScanSession` (id injetado,
   `darNota`, substituição protegida).
3. `NotaPendente`, `corpoDoEnvio` e o teste de literal e de paridade com a `paraNotaSubmetida`.
4. Outbox: colunas, `Migration(1, 2)`, `rota` no envelope, `ApiPlatos.enviarNota`.
5. `EnvioDeResultados.aoConfirmar` e `eliminarAoConfirmar`.
6. `NotaTela`, o botão e a fiação na `ScanActivity` (`gravarNotaEAgendar`).
7. `VarreduraPeriodicaWorker`, `EscaneamentoAberto` e `RetencaoDaResposta`.
8. Cobertura e conferência (`docs/cobertura-slice-5c-3-a-nota-no-aparelho.md`), com o que não foi verificado.
