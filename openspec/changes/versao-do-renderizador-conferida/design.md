## Context

A motivação está em `proposal.md` (Why). Aqui fica só o estado que decide a abordagem.

**Os três registros, lidos hoje** (2026-09-23, sobre `5dfeaaa`):

| Chave | Arquivo | Declaração |
|---|---|---|
| `dominio` | `packages/domain/src/commonMain/kotlin/com/platos/domain/layout/LayoutMap.kt:237` | `const val MIN_RENDERER_VERSION = 1`, no `companion object` de `LayoutMap` |
| `android` | `apps/android/src/main/kotlin/com/platos/android/render/RendererContract.kt:25` | `const val RENDERER_VERSION = 1`, em `object RendererContract` |
| `web` | `apps/web/src/layoutMap.ts:124` | `export const RENDERER_VERSION = 1;` |

Cada nome aparece declarado **uma vez** no seu arquivo; as demais ocorrências são uso ou KDoc.

**Quem lê cada um, e o que já quebra se ele mudar — por leitura, não medido (P6):**

| Registro sobe sozinho de `1` para `2` | Quem o lê em teste | Por leitura |
|---|---|---|
| `web` | `apps/web/test/renderer.test.ts:57,64` | `:57` soma `+ 1` à própria constante; `:64` afirma `golden.min <= RENDERER_VERSION`, e `1 <= 2`. **Nada cai** |
| `android` | `RendererContractTest.kt:34,43`; `PreparoDaProvaTest.kt:537,664`; `LayoutMapRendererInstrumentedTest.kt:191` (emulador) | todos relativos à própria constante ou `<=`. **Nada cai** |
| `dominio` | `LayoutEngineTest.kt:39` fixa `assertEquals(1, map.minRendererVersion)`; `RendererContractTest.kt:43` afirma `map.minRendererVersion <= RendererContract.RENDERER_VERSION` sobre um mapa que o `LayoutEngine` acabou de produzir | **caem os dois** — é a direção ruidosa |

Então a direção **silenciosa** (um renderizador acima de `MIN`, ou Android e web discordando acima
dele) é a que nenhuma camada pega hoje, e a **ruidosa** (`MIN` acima de um renderizador) já é pega —
ao menos do lado Android e pelo pino do motor. É isso que a tarefa 1 mede antes do código.

**O molde já existe.** `limiar.mjs`, `answer-kind.mjs` e `fio.mjs` rodam no job `web` do `ci.yml`,
cada um com dois passos: um que confere, outro que prova, a cada CI, que a conferência continua capaz
de falhar. Nenhum deles tem teste unitário: são vistos falhar pelo passo do CI e, na mudança que os
cria, por mutação na árvore.

## Goals / Non-Goals

**Goals:**

- Os três registros passam a ser comparados **dos arquivos de origem**, a cada CI, e a divergência
  reprova nomeando os pares que discordam.
- A conferência é vista falhar **por registro**: cada um dos três, divergindo sozinho, é nomeado — o
  que prova que nenhum deles está fora da comparação.
- A afirmação "nada os compara" sai do registro com o tipo certo: medida, com as duas direções
  separadas.

**Non-Goals:**

- Provar que um renderizador **sabe fazer** o que a versão dele declara. Isso é da paridade e dos
  testes de renderização; o conferidor compara números (decisão 11).
- Detectar que o motor passou a emitir algo que exige renderizador novo sem subir `MIN`. Nenhum
  arquivo da árvore diz isso; é decisão de quem muda o motor (decisão 11).
- Qualquer outra versão: `ENGINE_VERSION` / `layout_engine_version`, o `min_renderer_version` das
  fixtures, os literais dos testes.

## Decisions

### 1. Um conferidor Node em `tools/parity/renderizador.mjs`, e não um teste JVM ou Vitest

Os três registros vivem em três cadeias de ferramenta — KMP, Android e TypeScript —, e qualquer teste
escrito dentro de uma delas leria as outras duas como texto de qualquer jeito. Um teste Kotlin em
`apps/android` poderia importar `LayoutMap.MIN_RENDERER_VERSION` e `RendererContract.RENDERER_VERSION`
e ler `layoutMap.ts` do disco; ele rodaria só no `build`, amarraria a guarda ao grafo do Gradle e
separaria esta família de defeito do lugar onde as outras duas instâncias dela já moram. O plano
fixa o molde ("no molde de `limiar.mjs`"), e a razão do molde é a do próprio `limiar.mjs`: a
verificação vive "num caminho que não compartilha uma linha com o Kotlin nem com o engine" (P4).

Só `node:fs`, `node:path` e `node:url`, como `fio.mjs`: os caminhos se resolvem a partir do próprio
script (`import.meta.url`), e não do diretório de onde ele é chamado. Nenhuma dependência de npm.

### 2. Os três registros são nomeados no topo, e cada um é lido por uma declaração ancorada na linha

O conferidor declara, no topo, uma tabela de três entradas — chave (`dominio`, `android`, `web`),
rótulo legível, arquivo, nome da constante — e mais nada. Não há descoberta, varredura nem lista
externa: são três registros **nomeados**, e o plano proíbe generalizar (ver "Decisões já tomadas").

Cada registro é lido pela **declaração**, ancorada no início da linha: `const val <NOME>` (com
`: Int` opcional) nos dois arquivos Kotlin, `export const <NOME>` (com `: number` opcional) no
TypeScript. Ancorar na linha é o que deixa de fora KDoc, comentário e uso — nenhum deles começa com
`const val` ou `export const`. Comentário de fim de linha não faz parte do valor.

**Falha fechada, com saída `2`**, nomeando o registro, o arquivo e o motivo, quando:

- o arquivo não existe;
- a declaração aparece **zero** vezes (renomeada, movida) ou **mais de uma** (ambígua);
- o lado direito não é um **literal inteiro** (decisão 3).

`2`, e não `1`, pelo mesmo contrato de `fio.mjs`: `1` é "a árvore discorda", `2` é "não sei ler a
árvore". Confundir os dois faria uma refatoração de forma parecer divergência de versão — ou, pior,
uma divergência parecer só refatoração.

### 3. O valor tem de ser literal inteiro — e referência ao outro registro reprova

É a guarda de vacuidade desta conferência (P13), e é específica dela. A forma mais tentadora de
"resolver" o achado 4.4 é o dono único: `const val RENDERER_VERSION = LayoutMap.MIN_RENDERER_VERSION`
no Android, que compila (o Android depende do domínio) e faria os dois nunca mais divergirem. **É o
erro**: `MIN` é o que o mapa **exige**, `RENDERER_VERSION` é o que o renderizador **sabe fazer**.
Amarrar o segundo ao primeiro faria toda subida de `MIN` declarar, sozinha, uma capacidade que ninguém
implementou — e o gate de captura (`PreparoDaProva.kt:271`) passaria a abrir sessão para folha que o
aplicativo não desenha. Numa conferência cruzada, a independência dos dois lados é o valor dela — o
mesmo argumento com que o ADR-0015, decisão 3, manteve o `check` da migration separado do Kotlin.

Se o conferidor aceitasse a referência, ele compararia o valor com ele mesmo e ficaria verde para
sempre. Então ele recusa, com saída `2` e o motivo escrito: "não é literal inteiro". Se algum dia o
dono único for desejado, ele é **decisão**, e se toma trocando esta regra por uma mudança — não por
um conferidor que passou a aceitar.

### 4. A propriedade é igualdade dos três, e não `MIN <= renderizador`

O plano diz "reprova se divergirem", e a leitura do Context diz por que `<=` não serviria:
`MIN <= renderizador` é **exatamente** o que os testes existentes já afirmam, e a direção silenciosa
— renderizador acima de `MIN` — é a que `<=` aceita por definição. E Android contra web não tem
ordem: são duas implementações do mesmo conjunto de capacidades, conferidas pela paridade; versões
diferentes entre elas não têm leitura correta.

**Não contradiz a spec.** "Renderizador compatível" (`print/spec.md`) aceita versão **igual ou
superior** à do mapa — e continua aceitando: um aplicativo novo desenha mapa antigo. A igualdade aqui
é entre os registros **da mesma árvore**, que são publicados juntos, e não entre um renderizador e um
mapa qualquer.

Se aparecer um estado legítimo com um renderizador à frente de `MIN` na árvore, o conferidor fica
vermelho, e isso é o desejado: a mudança que quiser esse estado diz por quê e muda a regra
visivelmente.

### 5. O que ele diz: cada par que discorda, e os três códigos de saída

Comparação **par a par**, na ordem fixa `dominio`, `android`, `web`. Cada par discordante vira uma
linha, com os dois registros e os dois valores, na forma
`::error::os registros <x> e <y> discordam: <x> diz <a>, <y> diz <b>` (prefixo `::error::` como
`answer-kind.mjs`, que vira anotação no CI).

Por que pares, e não "o registro que destoa": com três registros não há maioria que esteja certa. Se
`web` subir sozinho, pode ser que ele esteja certo e os outros dois atrasados. O conferidor não
escolhe; ele diz **quais dois** discordam, que é o que o plano pede. Com um registro fora, saem
**exatamente duas** linhas, as dos dois pares que o contêm; com os três diferentes, três.

- `0` — os três concordam. Imprime os três valores, com rótulo, e `os tres registros concordam`.
- `1` — pelo menos um par discorda. Imprime as linhas acima.
- `2` — não consegue ler (decisão 2) ou recebeu parâmetro inválido.

### 6. `--divergir <chave>` é o único parâmetro, e soma 1 ao valor lido

Para o passo "continua capaz de falhar", no molde de `--esperado` de `limiar.mjs` e
`answer-kind.mjs`. Duas diferenças, e as duas vêm do que esta conferência tem de provar:

- **Soma 1 ao valor lido**, em vez de receber um número. Diverge por construção, e modela
  exatamente a direção silenciosa — um registro que subiu sozinho. Um número fixo (o `450` do
  limiar) é um valor que um dia pode coincidir com o real.
- **Nomeia o registro**, em vez de haver um só forçável. O CI força **cada um dos três, um de cada
  vez**: é isso que pega um conferidor que deixou de comparar um dos registros sem acusar — forçar só
  um provaria só esse.

Chave desconhecida, ou `--divergir` sem chave, sai com `2`.

**Por que parâmetro, e não defeito plantado num arquivo fora do checkout como o de `fio.mjs`.** Lá o
defeito é "existe um tipo novo sem literal", e plantá-lo exercita a leitura de uma forma nova. Aqui a
leitura é de três linhas fixas, e ela é provada **uma vez**, nesta mudança, por mutação no arquivo
real (tarefa 3.1), e protegida depois pela falha fechada da decisão 2. O passo do CI prova a outra
metade, a que pode apodrecer em silêncio a cada edição do script: que a comparação ainda inclui os
três. **O passo do CI não prova a leitura** — e isso fica escrito na cobertura (P8).

### 7. Os dois passos no `ci.yml`, no job `web`, logo depois dos do fio

1. **"A versao do renderizador concorda nos tres registros"** — `node tools/parity/renderizador.mjs`.
2. **"A verificacao da versao do renderizador continua capaz de falhar"** — para cada chave em
   `dominio android web`: roda com `--divergir <chave>` e exige saída `1`, **as duas** linhas dos pares
   que contêm a chave, e **a ausência** da linha do par que não a contém. O motivo é conferido, e não
   só o código de saída — o precedente é o passo do fio: recusar pelo motivo errado seria a camada
   vizinha segurando. A saída capturada só é impressa quando o passo falha, para que os `::error::`
   do defeito forçado não virem anotação num CI verde.

Comentário acima dos dois no tom dos vizinhos: por que existe, qual a direção silenciosa, e o que ele
não prova. O job `web` já tem Node e não precisa de `npm ci` para isto.

### 8. A medição de entrada vem antes de qualquer código — ver o buraco

É o único acréscimo ao texto da 7.1 do plano, e a razão está no Context: o plano repete "nada os
compara" por leitura, e a leitura desta mudança diz que isso vale numa direção só. Sem medir, o
registro final herdaria a frase com o tipo errado (P6). E é também a prova de isolamento da decisão
seguinte (P9, `rigorous.md` §3): uma mutação que outra suíte já pega não diz nada sobre o conferidor.

Três mutações no arquivo real, uma por registro, cada uma marcada com `// MUTACAO` **na linha de
cima** — não na mesma linha, para que a declaração continue na forma que o conferidor lê —, revertida
e **rodada** antes da próxima (P10). O conjunto previsto:

| Mutação | Suíte rodada | Previsto |
|---|---|---|
| `web`: `RENDERER_VERSION` `1` → `2` | `apps/web`: `npm test` (Vitest), inteira | **0 falhas** — o buraco |
| `android`: `RENDERER_VERSION` `1` → `2` | `./gradlew :apps:android:testDebugUnitTest --rerun-tasks`, inteira | **0 falhas** — o buraco |
| `dominio`: `MIN_RENDERER_VERSION` `1` → `2` | `LayoutEngineTest` (`:packages:domain:jvmTest`) e `RendererContractTest` (`:apps:android:testDebugUnitTest`), **filtradas** | **caem os dois**: `mapa declara as duas versoes` (o pino de `LayoutEngineTest.kt:39`) e `renderizador compativel aceita o mapa` — a direção ruidosa, já coberta |

**A terceira linha é filtrada de propósito, e o que ela não afirma fica dito.** Ela afirma que a
direção ruidosa **já é pega** — e para isso basta um teste caindo pelo motivo certo. Ela **não**
afirma quais outros cairiam (os goldens do domínio, por exemplo): isso não muda nada na conclusão, e
rodar tudo seria gastar execução para medir o que ninguém vai ler. Na cobertura, a afirmação sai com
esse limite.

**O que não se roda, e por quê.** A suíte instrumentada (`LayoutMapRendererInstrumentedTest:191`
também soma `+ 1`) pede emulador, e nenhum arquivo de `apps/android` muda nesta mudança: fica **por
leitura**. As suítes Kotlin não leem `layoutMap.ts`, e a web não lê os `.kt`: não se roda a suíte de
um lado sob a mutação do outro — conferido por `grep`, não por execução.

### 9. Regra de parada — o instrumento não se conserta para caber na previsão

Regra 0.5 do plano, e ela vale sobre toda esta mudança:

> Toda etapa prevê **qual conjunto de cenários deve cair** sob a mutação. Se o conjunto real for
> diferente do previsto — **mais, menos, ou outros** —, **pare**. Não conserte o instrumento, não
> afrouxe a asserção, não ajuste a previsão em silêncio. Escreva o conjunto real ao lado do previsto
> e diga o que ele significa (P7, P12, P14).

Aplicada aqui, em três pontos:

- **Na medição de entrada (decisão 8).** Se web ou android derrubarem algo, a leitura do Context
  estava errada, a direção silenciosa é menos silenciosa do que se pensou, e isso é escrito antes de
  o conferidor ser escrito. Se o domínio **não** derrubar os dois, a direção ruidosa também está
  solta, e a nota à auditoria muda de sentido. Nos dois casos: parar, escrever, e decidir com o
  mantenedor se a mudança segue como está.
- **Na primeira execução sobre a árvore real.** Previsto: saída `0`, os três em `1`. Qualquer outra
  coisa é defeito do conferidor ou da leitura desta página.
- **No ver falhar (tarefa 3).** Cada registro forçado tem de produzir **exatamente** as duas linhas
  dos pares que o contêm. Uma a mais, uma a menos ou outra: parar.

### 10. O fechamento: o que é o comando cheio desta mudança

Nenhum arquivo que o Gradle ou o Vitest leem muda: a mudança é um script Node novo, o `ci.yml` e
documentos. Então:

- **As suítes JVM e Web** rodam na linha de base e na medição de entrada (tarefas 0 e 1), com o
  `timestamp` de dentro de cada relatório (P3), e **não** se rodam de novo no fechamento — nenhuma
  entrada delas mudou depois. Isso fica dito, e não suposto.
- **Os passos do job `web` que não dependem de artefato de build** — os quatro conferidores e os
  quatro "continua capaz de falhar" — rodam localmente, no Git Bash, com o **texto exato** do
  `ci.yml`, e não uma transcrição.
- **O comando cheio é o CI da PR**, em Linux e com o Node 22 do `setup-node` (local é 24), e o
  fechamento só se afirma **lido no destino**: os três jobs verdes, e no log do `web` as linhas dos
  dois passos novos (P26 — como a 7.3 fez em `0c779c1`).
- **`./gradlew build` não é rodado localmente**, porque nenhuma entrada dele muda e porque ele pede
  Docker para os testes de rota. Se o mantenedor quiser, é pergunta antes de ligar (P22).

### 11. O limite que não pode faltar no registro

Vai inteiro para a seção do que não fica verificado:

> **O conferidor prova que os três números concordam, e não que eles estão certos.** Ele não sabe se
> o motor passou a emitir algo que exige renderizador novo — quem sobe `MIN` é quem muda o motor, e
> a fatia 5 é a primeira a ter de fazê-lo. E ele não sabe se um renderizador desenha o que a versão
> dele declara — isso é da paridade e dos testes de renderização (P16: o conferidor é a camada
> vizinha deles). Três registros subindo juntos para um número errado passam.

## Decisões já tomadas contra — o que é proibido em 7.1

Da lista "Proibido nesta etapa" da ETAPA 7 do plano, que vale para 7.1 e 7.2, com as razões que o
plano dá. Não são alternativas em aberto.

- **Fazer `renderizador.mjs` "ler tudo o que for versão" e virar um conferidor genérico.** Ele confere
  três registros nomeados. Abstração sem necessidade comprovada é a regra 8 do `CLAUDE.md`. A 7.3 é
  geral sobre `transport/` porque lá o conjunto cresce por construção e o caso que importa é o que
  ainda não tem nome; aqui o conjunto é fechado.
- **Ligar `minifyEnabled` ou acrescentar regras de R8.** Não está na auditoria, muda o artefato e abre
  uma frente de verificação inteira. Achado novo → item escrito.
- **Assinar o release, mexer em `versionCode` ou tocar em publicação de loja.** É trabalho de
  lançamento, não de correção de auditoria.

E as que saem das decisões acima, desta mudança:

- **Dono único** — um registro referenciando outro. Decisão 3.
- **Mudar qualquer um dos três valores**, ou regravar golden, fixture ou hash. Nada aqui os move; se
  movesse, P23 exigiria paridade e fidelidade na mesma sessão.
- **Editar a KDoc "Espelha `RENDERER_VERSION` do lado web".** Ela passa a ser verdade imposta sem
  precisar mudar; editá-la tocaria `apps/android` por um comentário e obrigaria o comando cheio do
  Android (P5) sem nada a verificar. O plano não a manda mudar.
- **A `concurrency` do `ci.yml`**, que também é deste arquivo. É o item 7.2.3, de outra mudança; as
  duas não se fundem (plano, §2, observação 3).
- **Os dois testes de `ApiPlatosPacoteTest` que nunca rodaram, e a guarda de contagem.** Item 7.2.5.

## Risks / Trade-offs

- **[A leitura por expressão regular quebra numa reformatação inocente]** — `const val X: Int = 1`
  vira `const val X = 1` com tipo, ou a constante muda de arquivo. → A forma com tipo é aceita desde o
  início; qualquer outra sai com `2`, o registro e o arquivo nomeados. É barulhenta de propósito:
  falhar fechado custa um ajuste no conferidor, falhar aberto custaria a guarda.
- **[A igualdade é mais estrita do que D24 exige]** — um estado legítimo com renderizador à frente de
  `MIN` fica vermelho. → Desejado (decisão 4): quem quiser esse estado muda a regra por uma mudança,
  com a razão escrita.
- **[O passo do CI só prova a comparação]** — um script editado que passasse a ler o número errado
  de um arquivo não seria pego pelo `--divergir`. → A leitura é provada nesta mudança por mutação no
  arquivo real (tarefa 3.1), e depois pela falha fechada; o limite fica escrito na cobertura.
- **[Node 24 local, Node 22 no CI]** — o script só usa `node:` estável em ambas; a execução em 22 se
  confere no log do CI da PR, e não se supõe.
- **[PR empilhada]** — a base é `vewvniv/o-fio-preso-nos-dois-lados` (PR #59), que ainda está aberta.
  → É a pilha que o plano decidiu em 2026-09-19 (§2, "Como isso se traduz em sessões"); o GitHub
  re-aponta a base quando a anterior entra.

## Migration Plan

Nada a migrar: nenhum dado, schema, contrato, artefato ou hash muda. A reversão é tirar os dois
passos do `ci.yml` e o arquivo `tools/parity/renderizador.mjs`; nada mais depende deles.
