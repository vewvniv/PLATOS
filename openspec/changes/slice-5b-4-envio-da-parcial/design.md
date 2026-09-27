## Context

Ver `proposal.md` para o porquê. Aqui só o estado atual que molda o desenho, com o tipo de cada
afirmação (P6 do `rigorous.md`).

- **O caderno já existe e já sabe quando completa.** `Caderno.capturadas`/`Caderno.esperadas`
  (`apps/android/.../scan/Caderno.kt:66-68`) e `ScanSession.estadoDaDiscursiva`
  (`apps/android/.../scan/ScanSession.kt:149-212`) já mantêm o "N de N" por região, região a região,
  e nunca regridem uma região capturada. *Conferido por leitura.*
- **`ScanSession.onFrame` nunca entrega apuração no caminho com discursiva.** A linha 116-119 diz
  isso por escrito: "Reconhece e mostra a parcial, e nunca entrega apuração... A parcial é
  `PartialScore`, e `ApuracaoNova` nem a aceitaria." `ApuracaoNova` (linha 311-314) é
  `data class ApuracaoNova(val reading: InterpretedReading, val score: ObjectiveScore)` — tipado para
  o caminho só-objetivo, sem lugar para uma `PartialScore` nem para um caderno sem uma única leitura
  que carregue o token. *Conferido por leitura.*
- **`ResultadoPendente.nota` é `ObjectiveScore`, sem alternativa** (`outbox/ResultadoPendente.kt:39`).
  `corpoDoEnvio()` (`api/ResultadoDto.kt:37-61`) lê `nota.packageHash/variantId/points/maxScore/closed/
  outcomes` direto, e grava o corpo (JSON) congelado no Room (`ResultadosEmRoom.kt:19-21`, `corpo`).
  *Conferido por leitura.*
- **No servidor, a mesma forma se repete e se aprofunda.** `ResultQueries.record()`
  (`apps/api/.../ResultQueries.kt:98-105`) recebe `nota: ObjectiveScore` **tipado**, não um DTO
  genérico, e usa `nota.packageHash/variantId/points/maxScore/closed/outcomes` para os `insert`.
  `ResultDto.paraNota()` (`apps/api/.../http/dto/ResultDto.kt:38-67`) reconstrói esse `ObjectiveScore`
  a partir do `ResultSubmissionDto`, e é a reconstrução — não uma validação escrita à parte — que
  aciona as guardas de `ObjectiveScore.init` (regra 7). *Conferido por leitura.*
- **`ObjectiveScore.closed` é propriedade computada, não campo**: `val closed: Boolean get() =
  pending.isEmpty()` (`ObjectiveScoring.kt`, dentro de `ObjectiveScore`). Isto significa que
  `ObjectiveScore` **não consegue, por construção**, representar "fechada = falso" com
  `pending.isEmpty() == true` — que é exatamente o caso de uma parcial sem nenhuma pendência
  objetiva e com discursivas aguardando. Isto não é limitação de validação: é impossibilidade de
  tipo. *Conferido por leitura, e é o fato que decide a Decisão 2 abaixo.*
- **A ordem da rota importa.** `POST .../results` chama `submission.paraNota()` **antes** de buscar o
  pacote publicado (`Routes.kt:218-247`): `paraNota()` roda sem acesso a `ExamPackage`. Só depois,
  `conferirProveniencia(publicada.pacote, nota)` (`ProvenienciaDoResultado.kt:54-74`) decodifica o
  pacote e confere `packageHash`/`variantId` contra ele — e é **aqui**, e não em `paraNota`, que o
  código já lê `ExamPackage.variants` para conferência (linha 64-65). *Conferido por leitura.*
- **`PartialScore` foi desenhado para nunca ser confundido com `ObjectiveScore`** (decisão 1 da 5b-2,
  `PartialScore.kt:21-24`): não herda, não contém, "o compilador recusa a troca". Isto é proteção que
  esta mudança precisa **preservar**, e não uma barreira que ela precisa contornar.
- **`AwaitingEssay(questionId, points)` não carrega `QuestionAnswer`** (`PartialScore.kt:12-15`):
  não é uma leitura de OMR, é a declaração de uma discursiva ainda não corrigida. Forçá-la a viajar
  como `AnswerObservationDto`/`QuestionOutcome` exigiria inventar um `answer_kind` e uma
  `QuestionAnswer` que significam "isto não foi lido, é discursiva" — dentro de um vocabulário que
  hoje só descreve leitura de bolha (`AnswerKind`, `com.platos.domain.transport`, ADR-0015). Isto é
  avaliado e rejeitado na Decisão 3.

## Goals / Non-Goals

**Goals:**
- Um caderno completo produz um resultado que atravessa `result-sync` inteiro — fila, autenticação,
  idempotência por `captureId`, revisão, preservação ao sair — sem fila nova nem tabela nova.
- A proteção de tipo entre `ObjectiveScore` e `PartialScore` (decisão 1 da 5b-2) continua valendo em
  todo ponto do caminho, incluindo o servidor: em nenhum lugar uma parcial se torna, por acidente ou
  por conveniência, uma nota fechada.

**Non-Goals:**
- Persistir por linha própria quais discursivas de um resultado aguardam correção. O conjunto é
  derivável do pacote publicado (que items da variante são `ESSAY`) mais o `variant_id` já gravado em
  `grading_result` — um segundo registro disso seria o segundo oráculo que `conferirProveniencia` já
  recusa por escrito para pacote e variante. A fatia 8 (correção discursiva) é quem lê essa derivação.
- Qualquer coisa em `scoring`: nem `ObjectiveScoring.scorePartial`, nem `PartialScore`, nem
  `ObjectiveScore` mudam de forma.
- Finalizar caderno incompleto (§8) — proposal.md já registra como fatia própria.

## Decisions

### 1. O gatilho de envio é a transição de incompleto para completo, marcada no próprio `Caderno`

`Caderno` ganha um campo `entregue: Boolean` (default `false`), `@Serializable` como o resto da
classe. `ScanSession.estadoDaDiscursiva` passa a devolver, junto do `ScanState`, um sinal de entrega
quando `atual.capturadas == atual.esperadas && !atual.entregue` — e marca `entregue = true` no
`Caderno` armazenado na mesma passada, antes de devolver.

**Por que no próprio `Caderno`, e não numa variável separada da sessão.** O caderno já é o que
sobrevive ao fechamento do processo (5b-3) e o que se perde ao trocar de aluno (decisão 4 da 5b-2).
Uma flag de sessão à parte teria de ser guardada e retomada pelo mesmo caminho do caderno — repetindo
a persistência que `GuardarCadernoEmAndamento`/`CadernosEmRoom` já fazem — só para não duplicar a
existência da flag. Ela **precisa** sobreviver a "app fecha bem depois do último quadro que completou
o caderno, antes do worker rodar": sem isso, reabrir o app re-apresentaria o mesmo caderno completo e
o entregaria de novo, com um `captureId` novo, virando uma revisão nova espúria no servidor.

**Alternativa rejeitada: inferir "já entregue" comparando o caderno atual ao anterior.** `Caderno` é
`data class`, e comparar por igualdade estrutural funciona entre um quadro e o seguinte dentro da
mesma instância de processo — mas não sobrevive ao fechamento do app, porque não há "anterior" após
retomada. A flag persistida é a única forma que atravessa as duas janelas (entre quadros, e entre
processos) com a mesma regra.

`ApuracaoNova` (hoje `data class ApuracaoNova(val reading: InterpretedReading, val score:
ObjectiveScore)`, `ScanSession.kt:311-314`) vira um tipo selado:

```kotlin
sealed interface ApuracaoNova {
    data class Completa(val reading: InterpretedReading, val score: ObjectiveScore) : ApuracaoNova
    data class DeCaderno(val aluno: String, val score: PartialScore) : ApuracaoNova
}
```

`onFrame` continua devolvendo `ApuracaoNova?`; o ramo `comDiscursiva` (linha 115-120) passa a devolver
`ApuracaoNova.DeCaderno` na transição descrita acima, e `null` em todo o resto — nenhum outro
comportamento do requisito "não guarda nada" muda. `ScanActivity.gravar` (linha 282-296) ganha um
`when` sobre `ApuracaoNova` no lugar do tipo único de hoje.

### 2. `ResultadoPendente.nota` vira um tipo selado do domínio KMP, não um `ObjectiveScore` alargado

Novo tipo, em `packages/domain` (KMP, ao lado de `ObjectiveScoring.kt` e `PartialScore.kt`):

```kotlin
sealed interface ApuracaoParaEnvio {
    data class Completa(val score: ObjectiveScore) : ApuracaoParaEnvio
    data class Parcial(val score: PartialScore) : ApuracaoParaEnvio
}
```

**Sem `@Serializable`.** Nem `ResultadoPendente` nem `ApuracaoParaEnvio` são serializados como um
todo em nenhum ponto: o que viaja para o Room é `corpoDoEnvio()` — o `ResultSubmissionDto` já
serializado em JSON —, e o que viaja para o servidor é a mesma string. `ObjectiveScore` em si já não
é `@Serializable` hoje (confirmado por leitura: nenhuma anotação na classe, diferente de
`PartialScore`, que ganhou a marca na 5b-3 para o **outro** caminho — o `Caderno` guardado em disco
para continuidade de tela). Marcar `ApuracaoParaEnvio` como serializável exigiria marcar
`ObjectiveScore` também, sem nenhum consumidor que precise disso — P18.

`ResultadoPendente.nota` passa de `ObjectiveScore` para `ApuracaoParaEnvio`. `corpoDoEnvio()`
(`api/ResultadoDto.kt`, lado Android) ganha um `when` que monta o mesmo `ResultSubmissionDto` de
hoje para `Completa` (nenhum byte muda), e para `Parcial` monta `points = score.objectivePoints`,
`maxScore = score.maxScore` (o da prova, não o objetivo — já é o que `PartialScore.maxScore`
declara), `closed = false` sempre, `observations` só das questões objetivas (`score.outcomes`, que já
é `List<QuestionOutcome>` só delas), e **`partial = true`** (campo novo, ver Decisão 3).

**Por que um tipo selado, e não alargar `ObjectiveScore` para aceitar os campos de uma parcial.**
Isso é exatamente o que a decisão 1 da 5b-2 recusou por escrito, e a razão continua valendo: um
`ObjectiveScore` com um campo "tem discursiva pendente" ainda seria aceito por qualquer consumidor
que só olhe `closed`/`points`/`maxScore` sem saber do campo novo — a proteção do compilador
desapareceria. `ApuracaoParaEnvio` mantém as duas notas como tipos que **nenhum `when` esquece sem
o compilador acusar** (`sealed interface`).

**Por que um tipo novo no domínio KMP, e não dois campos opcionais em `ResultadoPendente` (Android)**
ou dois métodos de `guardar`. `ResultQueries.record()`, no servidor, tem exatamente o mesmo problema —
recebe `nota: ObjectiveScore` tipado (`ResultQueries.kt:98-105`) pela mesma razão que o outbox do
aparelho tem. Repetir a solução dos dois lados seria a duplicação que a regra 7 do `CLAUDE.md` e o
próprio KMP existem para evitar; um tipo KMP resolve os dois lados com a mesma definição.

### 3. O fio ganha um campo discriminador (`partial`), e não é inferido de `closed`

`ResultSubmissionDto` (`packages/domain/.../transport/ResultDto.kt`) ganha um campo aditivo:

```kotlin
val partial: Boolean = false
```

**Por que não inferir "isto é uma parcial" a partir de `closed=false` mais a forma das
`observations`.** O requisito "Nota parcial chega como parcial" de `result-sync` já existe **antes**
desta mudança, para o caso objetivo-só-ambíguo: uma folha com bolha indecisa também chega com
`closed=false` e `pending` não-vazio. Se o servidor tentasse distinguir "ambígua objetiva" de
"discursiva aguardando" só pela forma, o caso em que **os dois coexistem** — uma folha de prova com
discursiva cuja parte objetiva também tem uma bolha ambígua — teria `closed=false` e `pending`
não-vazio por causa da bolha, e nada acusaria que a informação sobre as discursivas aguardando foi
descartada. A nota gravada pareceria "só uma bolha em disputa", quando na verdade a prova inteira
ainda tem discursiva por corrigir. Um discriminador explícito fecha essa ambiguidade sem depender de
casos coexistirem ou não; `= false` como default mantém todo corpo **decodificável** sem mudança —
qualquer corpo já emitido, sem o campo, continua lido como `partial = false`.

**Isto não mantém o corpo *emitido* byte a byte igual para uma nota completa.** `corpoDoEnvio()`
(Android) usa `encodeDefaults = true` (`api/ResultadoDto.kt:32-35`) de propósito, para
`student_token`/`answer_options` — e o mesmo `Json` emite `"partial":false` em **todo** corpo, não
só nos de parcial. A alternativa — `@EncodeDefault(EncodeDefault.Mode.NEVER)` só neste campo, para
omiti-lo quando falso — introduziria a primeira API experimental de serialização do projeto para
economizar oito bytes recorrentes num campo cujo próprio propósito é ser explícito. `partial:false`
no corpo de toda nota completa é o contrato ficando mais claro, não mais pesado; os dois testes que
hoje prendem o corpo por igualdade literal (`ResultadoDtoTest`, Android) precisam do campo novo
acrescentado ao literal esperado — achado ao implementar a tarefa 1.2, e corrigido nela mesma.

### 4. A validação de uma parcial se divide nas mesmas duas fases que já existem, na mesma ordem

Hoje: `paraNota()` valida coerência interna sem o pacote; `conferirProveniencia()` confere pacote e
variante, com o pacote já decodificado. Para `partial = true`:

- **Fase 1 (`paraNota`, sem pacote):** exige `closed == false` (nunca condicional a `pending`), e
  valida os outcomes objetivos com as mesmas guardas de sempre (soma bate, sem item repetido, sem
  desencontro pendência/evidência) — mas **sem** a guarda `closed == pending.isEmpty()`, que só faz
  sentido para `ObjectiveScore`. O resultado desta fase é um valor que carrega só o que o corpo
  trouxe: `packageHash`, `variantId`, `objectivePoints`, `maxScore` (o valor enviado), `pending`
  objetiva, `outcomes` objetivos.
- **Fase 2 (`conferirProveniencia`, com o pacote decodificado):** além do que já confere hoje
  (`packageHash`, `variantId`), para uma parcial confere que a variante **declara** ao menos um item
  `ESSAY` — uma parcial de uma prova sem discursiva, ou de uma variante cujo conjunto de discursivas
  não bate com o que o corpo implicitamente assume, é proveniência que não fecha, na mesma classe de
  recusa definitiva que pacote/variante errados. Confirmada a proveniência, esta fase deriva
  `awaiting: List<AwaitingEssay>` e `objectiveMaxScore` **do pacote** — os mesmos dois números que
  `ObjectiveScoring.scorePartial` deriva no aparelho, a partir do mesmo campo único (`PackageItem.kind
  == ESSAY`, `item.rubric`) — e só então constrói o `PartialScore` de verdade, com as guardas do
  `init` dele rodando por cima do que as duas fases já confirmaram.

**Por que dividir assim, e não esperar o pacote antes de validar qualquer coisa.** É a mesma ordem que
já existe para `ObjectiveScore`, pela mesma razão registrada em `ProvenienciaDoResultado.kt`: coerência
interna do corpo é uma pergunta que não precisa do pacote, e não faz sentido buscar um pacote (e
gastar uma consulta) para um corpo que já está mal formado por dentro.

**Sobre "não duplicar `scoring`" (regra 7).** A Fase 2 repete uma linha — filtrar itens da variante por
`kind == ESSAY` e somar a rubrica — que também existe dentro de `ObjectiveScoring.scorePartial`. É
filtro sobre um campo que o pacote já declara como única fonte (`PackageItem.kind`), não uma segunda
regra de negócio sobre **como pontuar**; a alternativa — chamar `scorePartial` no servidor com um
`CapturePayload` sintético reconstruído do corpo — pagaria a complexidade de reconstruir um tipo que
não faz sentido fora de uma leitura real de câmera, para evitar quatro linhas de filtro. Não é a
chamada certa nesta escala.

### 5. Nada novo é gravado por discursiva; `GRADING_RESULT` e `ANSWER_OBSERVATION` recebem exatamente as colunas de hoje

`ResultQueries.record()` passa a receber `nota: ApuracaoParaEnvio`, com um `when` que extrai os
mesmos cinco valores escalares (`packageHash, variantId, points, maxScore, closed`) e a mesma lista de
`outcomes` para o laço de `ANSWER_OBSERVATION` — para `Parcial`, `points = score.objectivePoints`,
`maxScore = score.maxScore`, `closed = false`, `outcomes = score.outcomes` (só objetivos). Nenhuma
coluna nova, nenhuma tabela nova, nenhuma migration.

## Risks / Trade-offs

- **Uma variante sem nenhum item `ESSAY`, com `partial=true` declarado por um aparelho desatualizado
  ou por erro de fiação, seria recusada na Fase 2 como proveniência incoerente** ("a variante não
  declara discursiva") → é o comportamento certo: o mesmo `NaoConfere` definitivo que já existe para
  pacote/variante errados, e não uma exceção nova.
- **`partial=false` continua sendo o default de todo corpo antigo** → nenhum corpo já em trânsito ou
  em fila num aparelho ainda não atualizado quebra; o campo é puramente aditivo.
- **A Fase 2 decodifica o pacote de novo para derivar `awaiting`**, exatamente como já decodifica para
  ler `variants` — não é uma segunda decodificação além da que já acontece hoje, é a mesma leitura
  lendo mais um campo dela.

## Migration Plan

Nenhuma migration de banco. `ResultSubmissionDto.partial` é aditivo com default; `grading_result` e
`answer_observation` recebem as mesmas colunas de sempre. O rollout é o deploy normal da API seguido
da atualização do aplicativo — sem janela de compatibilidade a proteger além da que `partial =
false` já garante.
