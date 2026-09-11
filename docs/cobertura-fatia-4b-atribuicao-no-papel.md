# Cobertura de cenários — fatia 4b-atribuicao-no-papel (a folha diz de quem ela é)

O registro **por tarefa** — data, instrumento e número — vive em
`openspec/changes/.../slice-4b-atribuicao-no-papel/tasks.md`. Este documento existe para que um
achado seja encontrável **por assunto**, e traz **como** cada verificação crítica foi vista falhar —
não que ela passa.

## Como cada verificação crítica foi vista falhar

### A composição ignorando o QR da atribuição (tarefa 1.3)

A folha de um aluno é a geometria da variante com o QR dele. Uma composição que devolvesse a
geometria crua imprimiria, para um aluno que existe, a folha sem identidade — e a captura não teria a
quem atribuir.

| Defeito introduzido | Vermelhos | Verdes |
|---|---|---|
| `comQrDe` devolve a geometria sem substituir o QR | **2**: `a folha da atribuicao e a geometria da variante com o QR dela` (`expected: <prova-referencia-slice-1.tok-1.v1.0.ABCD> but was: <prova-referencia-slice-1...0.05CB>` — o payload da variante, com os dois campos vazios) e `duas atribuicoes produzem folhas que diferem so no QR` (`as duas folhas trouxeram o mesmo payload; a atribuicao nao chegou ao QR`) | os três de recusa, que acontecem **antes** da composição |

**A previsão da tarefa dizia um vermelho, e são dois** — o primeiro cenário também afirma o payload.
Corrigida no arquivo **antes** de injetar.

### As duas recusas da coerência, em conjuntos disjuntos (tarefa 2.3)

| Defeito introduzido | Vermelhos | O que o verde prova |
|---|---|---|
| a conferência de atribuição sem QR sai | **1**: `atribuicao sem QR proprio e recusada` | `atribuicao sem QR e pacote incoerente` (do `FolhaDaAtribuicaoTest`) fica **verde** — ele exercita o `requireNotNull` da **composição**, não a validação. É a separação de camadas medida, e não afirmada (P16) |
| a conferência de token repetido sai | **1**: `token repetido entre atribuicoes e recusado` | as outras sete |

Conjuntos de **um elemento cada**, como declarado antes de injetar.

### O valor de reserva no campo de aluno (tarefa 3.3)

Valor inventado é indistinguível de token verdadeiro para quem lê a folha — a mesma família do nome
de reserva que a fatia 4a-zero viu falhar.

| Defeito introduzido | Resultado |
|---|---|
| campo vazio ganha o marcador `sem-aluno` | **8 vermelhos**, e eu havia previsto **4** mesmo depois de corrigir a previsão |

Os dois que medem a proteção caíram com `expected: <> but was: <sem-aluno>`. **Os outros seis são o
achado**, e o padrão vale mais que a contagem: o payload sem identidade está embutido em **cinco**
artefatos versionados — `PacoteVersionadoTest`, `GoldenLayoutTest`, `LayoutProfileTest`,
`PrintTestSheetTest` (a folha de teste de impressão também usa `QrPayload`) e o hash da fixture. Eu
contei dois goldens; são cinco.

**Isolamento fraco, declarado como fraco antes de injetar.** A disjunção que a mutação mede está no
verde: os cinco cenários do caminho da atribuição ficaram verdes, **lidos do XML um a um** e não
presumidos.

### O token "legível", com o nome do aluno dentro (tarefa 4.3)

A mutação do enunciado — nome no rótulo de uma atribuição — exigiria inventar um campo que não
existe, e testaria um contrato inexistente. Substituída pelo **defeito bem-intencionado de verdade**:
alguém torna o token legível para depurar, e leva nome civil de menor para dentro de artefato
imutável já distribuído.

| Defeito introduzido | Vermelhos |
|---|---|
| `tokens = roster.map { it.studentToken + "-" + it.displayName }` | **2**, os dois declarados: o canário com **`nome 'Zoraide Buarque' dentro do pacote`** — achado por busca no **conteúdo gravado**, não por inspeção de campo — e `cada aluno do roster ganha uma atribuicao` com `expected: <[tok-hlm, tok-zrd]> but was: <[tok-hlm-Hildemar Peçanha, tok-zrd-Zoraide Buarque]>` |

Os sete restantes ficaram verdes, incluindo os de hash e o de modo de identificação.

## As medições, e contra qual oráculo

### Geometria idêntica entre folhas de alunos diferentes (tarefa 5.2)

O instrumento é `tools/parity/compare.mjs`, que já existia e é o certo **por construção**: ele
rasteriza os dois PDFs com o **mesmo** rasterizador (mupdf a 600 dpi, 0,0423 mm/px) e compara
centroides de marcadores ArUco e bolhas — e **o QR não está entre os alvos dele**. Mede "a geometria
coincide" sem ser cego ao que deveria diferir.

| Par | Elementos | Maior divergência | Tramas |
|---|---|---|---|
| `tok-a` × `tok-b` | 185 de 185 | **0,000 mm** (tolerância 0,3) | 4, divergência 0,00 |
| `tok-a` × `tok-c` | 185 de 185 | **0,000 mm** | 4, divergência 0,00 |

E os documentos **diferem**: `sha256` distintos nos três (229.750, 229.782 e 229.789 bytes). As duas
metades juntas são a afirmação — geometria idêntica elemento a elemento, documentos distintos.

### A paridade entre plataformas, medida e não inferida (tarefa 6.2)

**A P23 se aplicava por um motivo diferente do previsto.** Não foi a regravação da referência, que
não aconteceu: foi o **caminho de desenho ter mudado** quando a conversão da matriz do QR virou
`linhasDeModulo`. Estava medido que os artefatos versionados saíram byte a byte idênticos — o que
prova que o `LayoutMap` não mudou —, mas "a paridade continua fechada" seria **inferência**, e a P23
quer medição.

Lado Android: telefone real `2511FPC34G` (Android 16), `connectedDebugAndroidTest` **sem filtro**,
49 testes e 0 falhas, PDFs recolhidos às 08:10Z; o lado web foi gerado **depois**, na mesma sessão
(P3). **O emulador não foi ligado** — o telefone estava conectado, e a autorização de mudança de
ambiente ficou sem uso.

| Medição | Resultado | Tolerância |
|---|---|---|
| Fidelidade do `android.pdf` | 116 verificações, maior desvio **0,042 mm** | 0,05 mm |
| Paridade `web.pdf` × `android.pdf` | 185 de 185, maior divergência **0,048 mm**, folga 0,252 mm | 0,3 mm |
| Tramas | 4 comparadas, maior divergência 0,39 ponto | 1,00 ponto |
| Folha de teste (fidelidade / paridade) | 31 verificações, 0,017 mm / tramas 0,39 | — |
| Tinta dos dois documentos do Android | nenhum pixel cromático | — |

### O tamanho do QR com identidade (tarefa 3.4)

A pergunta aberta do `design.md` fechou com número: **o QR não cresceu — 29×29 nos dois casos**,
com o payload indo de 32 para 40 caracteres. Custo por atribuição **1.044 bytes**, medido como
(104.749 − 101.618) / 3, o que dá **129,8 KB para 30 alunos** contra os 129,2 KB estimados na decisão
1 — diferença de 0,5%, e a forma escolhida não muda.

O instrumento foi um teste **descartável** que só imprimia, removido depois de rodar; a limpeza foi
conferida rodando. Ele mediu o pacote sem alunos em 101.618 bytes, que **bate com a medição
independente** feita em Python sobre a fixture quando o `design.md` foi escrito.

### O comando cheio do CI (tarefa 7.1)

`--rerun-tasks` desde a primeira rodada, porque a fatia anterior ensinou que `UP-TO-DATE` serve
relatório velho com contagem plausível e o único sinal que denuncia é o `timestamp`.

| Task no grafo do `build` | Testes | `timestamp` |
|---|---|---|
| `:apps:android:testDebugUnitTest` | 233 | 08:12:47Z |
| `:apps:api:test` | 122 | 08:13:34Z |
| `:packages:domain:jsNodeTest` | 307 | 08:13:31Z |
| `:packages:domain:jvmTest` | 315 | 08:13:16Z |
| `:packages:domain:testAndroidHostTest` | 307 | 08:13:09Z |
| **soma** | **1.284** | 173 de 173 tasks executadas |

Instrumentada **sem filtro**: **49 testes, 0 falhas, 0 erros, 0 ignorados**, nove classes,
`timestamp` 08:09:50Z, contagem conferida por segunda leitura (49 `<testcase>` contra `tests="49"`).

**O relatório velho da fatia anterior reapareceu:** `testReleaseUnitTest`, 6 testes com `timestamp`
de 2026-08-15, e ele **não está no grafo** do `build` — a soma honesta o exclui. É o item já nomeado
no documento de cobertura da fatia anterior, e continua aberto.

## O que ficou sem verificação automática, e por quê

| O que | Por quê |
|---|---|
| **A folha de um aluno, impressa em papel** | Os documentos dos três alunos foram medidos por rasterização, não por régua. O que só o papel decide — se o QR com token é legível pela câmera depois de impresso e fotografado — é conferência de aparelho, e ela pertence à fatia do aparelho, que é quem vai escanear folha de aluno |
| **O caminho de produto** | Não há rota HTTP nem tela: a publicação com roster só acontece por caminho programático, e nenhum professor publica por teste. Adiado com dono e fatia-limite no `design.md`, no formato do §16 |
| **A chave de idempotência da folha avulsa** | O §10 fixa `(exam_id, student_id)` e o §7 garante capturas em que `student_id` não existe. Esta fatia isola o caso por requisito — folha sem atribuição carrega campo vazio, nunca valor inventado — e **não** resolve a tensão. Pergunta da fatia do push |
| **Um `LayoutMap` com zero ou dois QR** | `comQrDe` e a função irmã em TypeScript exigem exatamente um, e a spec vigente exige uma região escaneável por folha. Nenhum teste produz um mapa malformado para exercitar a recusa, porque produzir um exigiria contornar o `LayoutEngine` — e o mapa é gerado, não digitado. É lacuna **conhecida**, não mitigada |

## O plano estava errado oito vezes, e o registro delas está nas tarefas

Não é anedota: é a contagem, e ela importa porque um plano que erra oito vezes em 23 tarefas diz
algo sobre quanto do plano é derivável antes de a implementação começar. Todas foram corrigidas no
artefato, com o motivo, e nenhuma foi contornada em silêncio.

| Tarefa | O que estava errado | Como foi resolvido |
|---|---|---|
| 2.1 | Enumerava **três** recusas; a terceira (layout órfão de atribuição) é irrepresentável na forma escolhida — o QR viaja dentro da atribuição. O mesmo erro estava na spec | `/opsx:update`: a spec passou a declarar a garantia **nomeando a construção** que a produz |
| 4.1 | Supunha mais trabalho do que havia: a gravação do roster e o mapeamento para `assignments[]` já existiam | A mudança real foi uma linha, e isso ficou escrito |
| 4.2 | Pedia um canário que **já existia** | Registrado como premissa corrigida; o que faltava (uma atribuição por aluno) entrou |
| 4.3 | A mutação proposta exigia inventar um campo inexistente | Substituída pelo defeito bem-intencionado real: token "legível" com o nome dentro |
| 4.4 | Afirmava que a recusa por modo "nunca foi exercitada com dado real" | Falso: roda contra Postgres real desde a fatia da LGPD. Marcada com a correção, sem teste redundante |
| 6.1 | Contradizia decisão registrada no KDoc do writer | Veículo trocado por um terceiro pacote versionado, no precedente da `prova-2` |
| 6.1 / 5.x | Ordem invertida — a 6.1 é pré-requisito da seção 5 | Reordenado, com o motivo escrito na 6.1 |
| 6.3 | Dissolveu como consequência da correção de veículo | O que sobrou virou a guarda do artefato novo |

**Duas famílias diferentes, que não entram nesta contagem e ficam ditas para não se misturarem:**

- **Implementação minha errada, não a tarefa:** na 1.1 eu fiz `qr` não-opcional; o compilador acusou
  em `ExamPublication.kt:80`, e o argumento decisivo veio da spec — campo não-opcional torna
  "atribuição sem folha endereçável" inconstruível, e a spec exige cenário de **recusa** para esse
  estado.
- **Previsão minha incompleta:** 1.3 (dizia um vermelho, eram dois) e 3.3 (dizia quatro, foram oito).
  As duas estão nas seções de mutação acima.

## Achados fora do escopo, com dono e fatia-limite

Nenhum foi consertado aqui (P19).

| Achado | Onde | Fatia-limite |
|---|---|---|
| **O `Impact` da proposta estava aproximado** — ela atribuía a regra do payload ao `LayoutEngine`, e ela mora em `QrPayload` (o engine delega); e listava `PublicarFixturesNoBancoRealTest.kt`, que não foi tocado, porque a verificação saiu melhor em `ExamPublicationTest` com Testcontainers. Não tocou nenhuma das **negativas**, que fecharam com zero | `proposal.md` desta fatia | nenhuma: é registro, e fica dito aqui |
| **`QrPayload` tinha KDoc apontando para "fatia 7"** como o momento em que token e variante existiriam. Corrigido nesta fatia, e o padrão é o que interessa: comentário que aponta para número de fatia envelhece quando a fatia é cortada | `QrPayload.kt`, `QrPayloadTest.kt` | corrigido aqui |
