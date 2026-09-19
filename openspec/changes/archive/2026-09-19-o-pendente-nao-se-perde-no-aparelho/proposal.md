## Why

Três achados da `docs/auditoria-2026-09-18-antes-da-fatia-5.md` sobre o **mesmo dado**: o resultado
pendente no aparelho, que é — pela decisão registrada em `ResultadoPendente` — **o único exemplar de
uma correção já feita**. Dois são defeitos reais no caminho de produção; o terceiro é o spec dizendo
o contrário do que o código faz.

**3.2 — `ResultadosEmRoom.abrir()` não deduplica.** `Room.databaseBuilder(...).build()` devolve uma
instância nova a cada chamada. Há **três chamadores de produção** sobre o mesmo `outbox.db` —
`SessaoActivity.onCreate:127`, `ScanActivity.onCreate:122`, `passadaDeEnvio:169` — e **nenhum fecha**.
Cada rotação de tela acumula mais uma instância viva, cada uma com o próprio
`SupportSQLiteOpenHelper` e a própria conexão. O worker roda **quando há rede**, inclusive com a
câmera aberta, e escrita concorrente por conexões distintas no mesmo SQLite é onde nasce
`SQLiteDatabaseLockedException`.

E nenhuma camada de teste está posicionada para ver: `OutboxEmRepousoInstrumentedTest` e
`ApagamentoLocalInstrumentedTest` constroem a base com **nome próprio** e guardam a referência num
campo — exercitam uma topologia de **uma instância, um dono** que **não é a da produção**. É a forma
de sombreamento de fixture que `rigorous.md` §3 descreve, e é por isso que o defeito atravessou.

**3.3 — `ScanActivity.gravar` descarta uma correção apurada em silêncio.** Duas linhas:

```kotlin
val organizacao = organizacao ?: return
val prova = prova ?: return
```

Se qualquer um for nulo, a folha é medida, a nota é **desenhada na tela**, e nada é gravado nem
agendado. Sem mensagem, sem log, sem diferença visível para quem segura o aparelho. §10 diz "nunca
falha em silêncio". O atenuante é real — o único lançador sempre põe os três extras —, mas o `Intent`
**sobrevive à morte do processo**, que é a propriedade que a KDoc da própria classe usa para
justificar reler o pacote do cache. E a assimetria é o que chama atenção: o mesmo arquivo trata
`contentHash`/`organizacao` ausentes com tela dedicada, e a KDoc de `EXTRA_SHORT_ID` raciocina em
detalhe sobre o risco de a folha "cair em silêncio" — vinte linhas depois de o arquivo descartar a
nota em silêncio.

**4.2 — O spec diz "token vazio" e o código grava `null`.** `result-sync` diz que a folha avulsa
"SHALL produzir resultado durável **com token vazio**", em dois pontos. O código converte vazio em
nulo em três, e a **diferença é a decisão**: a migration explica que vazio faria todas as avulsas da
mesma prova colidirem no unique de revisão. `CLAUDE.md` diz que `openspec/specs/` descreve o
comportamento atual; aqui não descreve.

**Por que agora, e não na fatia 5.** A fatia 5 estende o caminho de captura do aparelho. Acrescentar
escritores a uma topologia de Room que já está errada multiplica o defeito.

## What Changes

- **5.A, commit 1 — o acessador único.** `abrir(context)` passa a devolver **sempre a mesma
  instância**, guardada no companion, construída com `applicationContext` — nunca com o `Context` de
  uma `Activity`, que a manteria viva. **Os três chamadores não mudam:** eles já chamam `abrir`.
- **5.A, commit 2 — o teste que mede a topologia da produção.** Um cenário novo afirma, **pelo
  caminho de produção**, que duas chamadas a `abrir` devolvem a mesma instância, e que uma escrita
  pelo caminho do worker e uma leitura pelo caminho da tela não se atropelam.
- **5.B — a correção não é descartada em silêncio, e o conserto não é tratar o nulo: é tornar o
  estado inconstruível.** A decisão de "tem tudo o que precisa" já mora em `onCreate`. O `short_id`
  passa para o mesmo lugar: ausente, a câmera **não abre**, com motivo **próprio** — distinto dos
  cinco que o gate já distingue. Com isso `prova` e `organizacao` deixam de ser nuláveis no campo, e
  `gravar` perde os dois `return`. **O caminho silencioso deixa de existir em vez de ser tratado.**
- **5.C — o spec passa a dizer o que o código faz.** "Token vazio" vira ausência, nos dois pontos,
  **com a razão junto, em uma linha**, para que a distinção não se perca de novo: ela hoje vive só em
  comentário de migration e KDoc, e não no `design.md` de nenhuma fatia.

**Não é BREAKING.** Nenhum contrato de fio, nenhum schema, nenhum formato em disco muda. O
`outbox.db` gravado por uma versão anterior é lido pela nova sem migração: o que muda é **quantas
instâncias** o abrem.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `scan-session`: ganha um requisito sobre **abrir sabendo de qual prova a sessão é**. Hoje a spec
  exige que a câmera não abra sem pacote conferido, com motivo; ela não diz nada sobre o
  identificador da prova, que é a chave por onde o roster é lido e por onde o resultado é atribuído.
  Passa a dizer, e a dizer que o motivo é **próprio**.
- `result-sync`: o requisito "O resultado durável diz de qual folha, de qual pacote e de qual aluno
  ele é" deixa de dizer que a folha avulsa produz token **vazio** e passa a dizer **ausente**, com a
  razão registrada.

**Nenhuma outra capability é tocada.** **5.A não tem delta de spec, e a ausência é deliberada:** o
comportamento que ele conserta a spec de `result-sync` **já exige** — o pendente sobrevive, a fila
não trava, o resultado recusado não bloqueia os outros. O código é que não entregava. É defeito, não
requisito novo, e inventar requisito para justificar um commit seria a coisa errada.

## Impact

**Aparelho (apps/android)**
- `outbox/ResultadosEmRoom.kt` — `abrir` passa a guardar a instância no companion.
- `scan/ScanActivity.kt` — o `short_id` entra no gate de `onCreate`; `organizacao` e `prova` deixam
  de ser campos nuláveis; `gravar` perde os dois `?: return`.
- `scan/ScanScreen.kt` — a recusa com motivo próprio.

**Testes instrumentados — e é onde a mudança se prova**
- Cenário novo sobre `abrir`, pelo **caminho de produção**, com a mutação descrita no `tasks.md`.
- Cenário novo: sem o identificador da prova, o escaneamento não abre, e o motivo é próprio.
- `GravacaoNoFioPrincipalInstrumentedTest` e `SegundoMembroInstrumentedTest` já usam
  `ResultadosEmRoom.abrir` **e** `deleteDatabase("outbox.db")` entre cenários. O acessador único
  interage com isso, e `design.md` decisão 3 diz como — é a consequência mais provável de a mudança
  ficar maior do que parece.

**Registro**
- `docs/cobertura-o-pendente-nao-se-perde-no-aparelho.md` — novo.
- `docs/auditoria-2026-09-18-antes-da-fatia-5.md` §3.2, §3.3 e §4.2 deixam de estar abertos, sem
  apagar o texto antigo (P7).

**Não muda**
- **Nenhuma linha de `apps/api`, `apps/web` ou `packages/domain`.**
- **Nenhum schema de Room, nenhuma migration, nenhuma versão de base.** `fallbackToDestructiveMigration`
  continua fora, e continua sendo proibido.
- **`ResultadosPendentes` não ganha método nenhum** — em particular, não ganha `apagarDaOrganizacao`.
  A ausência **é** o requisito, e está escrita.
- **Nenhum limiar, tolerância ou janela** (regra 0.6 do plano, P11).

**Verificação**
- **Ambiente: emulador ou aparelho**, para `connectedDebugAndroidTest`. **P22 vale — perguntar
  antes**, inclusive em modo automático. O plano marca esta etapa no cabeçalho por isso.

**Referências**
- `docs/plano-de-correcao-antes-da-fatia-5.md`, **ETAPA 5** — o veículo, os commits, as proibições e
  as duas tabelas de conjunto previsto.
- `docs/auditoria-2026-09-18-antes-da-fatia-5.md` §3.2, §3.3, §4.2 — os achados.
- `ARQUITETURA-FINAL-v3.md` §10 ("nunca falha em silêncio"), ADR-0013 §3 (o gatilho que trouxe Room).
- `rigorous.md` §3 — o sombreamento de fixture que explica por que 3.2 atravessou.
