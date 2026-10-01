# Lições — o texto integral dos incidentes por trás de cada regra do `rigorous.md`

**O que é isto.** O `rigorous.md` carrega a regra, o que fazer e uma linha de por quê. **O incidente
inteiro mora aqui**: commit, número medido, arquivo, a afirmação errada e a razão. É material de consulta,
lido quando se toca a área da regra ou quando alguém a contesta — e **não** toda sessão.

**De onde veio.** Cada bloco abaixo é a cópia **literal**, extraída por intervalo de linhas e sem
redigitar, do `rigorous.md` no commit `6984ae9` (2026-10-01), antes da limpeza do contexto de sessão.
Nenhuma frase foi reescrita. Os números de linha citados dentro dos blocos são os daquela data e **envelhecem**:
valem como pista de onde olhar, e não como endereço (a P27 já tem um que mudou).

**Como entra incidente novo** (`rigorous.md` §10): a regra nova entra no `rigorous.md`, curta; o incidente
que a pagou entra **aqui**, sob o mesmo `P<n>`, com commit, arquivo e a medição. Âncora estável é o
`P<n>`, nunca a linha.

---

## P1

**P1 [V].** **Nunca marcar tarefa, cenário ou item como concluído sem a execução que o fecha, na
sessão em que se marca.** É a regra 10 do `CLAUDE.md`, na forma que ela precisou tomar depois de ser
quebrada: marcar para "rodar depois" cria registro falso, e registro falso é o que torna toda
verificação futura inútil. Se a execução não coube, a tarefa fica **desmarcada**, com o
que falta escrito nela. Desmarcar não é derrota — `3f523aa` desmarcou a 9.2b com "eu a marquei sem
ter rodado", no commit seguinte ao que a marcara.

## P2

**P2 [V].** **Nunca citar um sinal sem dizer qual passo ele atravessa.** Proibido tratar como prova:
`comando; echo "ok"` (o `ok` sai com o comando vermelho — só `&&` ou `$?` amarram os dois); workflow
verde como prova de que a imagem **está servindo** (`04d2f30`: imagem no GHCR às 14:47Z, serviço
respondendo com a de 09-04); 401 sem token como prova de que o processo alcança o banco (`544dafe`);
`/health` como prova de folga sobre o tempo limite de requisição (`c1aa6b3`: 43,5 s lidos como ~2×
de folga; o caminho real deu 69,2 s, folga de 20,8 s); `exit 0` do Gradle como prova de que a suíte
rodou (`docs/cobertura-fatia-1.md`: `-q` deu exit 0 com a task `UP-TO-DATE` e **zero testes**); um
relatório **completo e com contagem plausível** como prova de que a suíte rodou **agora**
(`60ba7bd`: `./gradlew build` deu `BUILD SUCCESSFUL` com **21 de 173** tasks executadas, e as
contagens de 233, 121 e 293 vinham de relatórios de ontem, de seis dias antes e de um mês — o
`timestamp` foi o único sinal que denunciou); um arquivo com o nome certo como prova de que o
conteúdo é aquele. Quatro vezes só na fatia 4a
(`b2eb65c`).

## P3

**P3 [V].** **Nunca comparar artefato sem conferir a âncora — data, hash, ou diretório por
execução.** Estado que mora no instrumento não avisa quando envelhece: `connectedDebugAndroidTest`
com filtro de classe reinstala o APK e apaga o `filesDir`, e a primeira tentativa de fechar paridade
comparou o web de hoje contra um `android.pdf` de **agosto** — nome certo, lugar certo, só a data
denunciava (`9d4f3f8`). (`installDebug` **não** apaga: é atualização, e preserva os dados. Conferido
em 2026-09-08.) É defeito **diferente** do comando de CI filtrado de P5: lá falta cobertura; aqui a
cobertura roda e mede o artefato errado.

**Para relatório de teste a âncora tem nome: é o `timestamp` do XML, e nunca a contagem.** Zero
testes é o caso fácil — ele salta aos olhos. O caso difícil é a contagem **plausível** de uma
execução anterior, e `UP-TO-DATE` a serve sem avisar: ela é indistinguível de verde de hoje até
alguém ler a data (`60ba7bd`, e o registro em
`docs/cobertura-fatia-4a-cache-referencia.md`, seção "O comando cheio do CI"). `UP-TO-DATE` **não é
mentira** — significa entradas inalteradas desde a última execução bem-sucedida, e para módulo que a
fatia não tocou é legítimo. O que não vale é citá-lo como execução: o CI roda em checkout limpo, e
reproduzi-lo exige `--rerun-tasks` ou `--rerun`. **E somar relatórios sem conferir a qual task cada
um pertence é o mesmo defeito um nível abaixo:** a primeira soma daquele dia deu 1246 porque incluiu
o XML de uma task que não existe mais no grafo. O par em tela: numa conferência por `adb`, "a tela mostra X"
e "a tela **ainda** mostra X" são indistinguíveis sem âncora — a faixa antiga do modo avião e o
*starting window* do `am start` já custaram duas conclusões erradas
(`docs/cobertura-fatia-4a-zero.md`).

## P4

**P4.** **Nunca usar oráculo que compartilhe código com o que ele julga.** O hash do pacote é
conferido pelo `MessageDigest` da JVM, e não por uma segunda serialização em Kotlin; a cobertura
oficial é conferida contra `papel.mjs` em JavaScript; o corpo de erro do Supabase foi conferido por
`curl` do host. **E o oráculo também desanda:** o 7 da fatia 3b está **fixado** no teste, e não só
comparado — se ele passasse a devolver 0 ou 40, a comparação seguiria verde com a apuração quebrada
nos dois sentidos.

## P5

**P5.** **Nunca declarar verde de comando estreito como verde do CI.** `./gradlew build` não roda
`connectedDebugAndroidTest`, e `--tests` de uma classe não roda as outras. Duas vezes o comando
estreito local escondeu o que o cheio pega (`5bd94a5`, e a tarefa 2.2 da 4a-zero, em que a exigência
de configuração do Android derrubou `:apps:api:installDist` e quem acusou foi o CI). Rodar o comando
**cheio** antes de publicar, de fechar fatia, de afirmar suíte verde, e sempre que a mudança tocar
build, manifesto ou suíte instrumentada.

## P6

**P6 [V].** **Nunca apresentar suposição, leitura de documentação ou inferência como medição.** Toda
afirmação carrega o tipo dela: **medido** (com o número, o instrumento e a data), **conferido**
(contra qual oráculo), **herdado** (de qual fatia, por qual teste) ou **suposto** (e então dito como
suposto). Três afirmações de transporte escritas com convicção no `design.md` da 4a não
sobreviveram ao instrumento — o charset do `respondText` (1.4), o `body<ByteArray>()` (2.3) e o teto
do `Intent` (6.5) —, e **nenhuma das três foi achada por revisão**. O limite de ~11 px/mm da 3b foi
retirado pela mesma razão: nenhum teste ficou vermelho, porque a prosa afirmava mais do que a
verificação sustentava.

## P7

**P7 [V].** **Nunca corrigir o registro apagando a afirmação errada.** Ela fica, marcada como
errada, com a razão e o número certo ao lado — `c1aa6b3` manteve a leitura de "~2× de folga" porque
quem lesse só o trecho antigo repetiria o erro. Apagar produz um documento coerente e um leitor que
não sabe o que já falhou.

## P8

**P8.** **Nunca chamar de mitigado o que é apenas conhecido.** Uma lacuna sem teste é lacuna, e
entra no documento de cobertura como lacuna: "**não é mitigado, é conhecido**"
(`docs/cobertura-fatia-4a-zero.md`, tarefa 5.4b — três mutações, e a terceira, a tela que ignora o
parâmetro e escreve um literal, **não é pega por nada**).

## P9

**P9 [V].** **Nunca confiar em verificação que não foi vista falhar.** O método está em §3, e as duas
exigências que a prática acrescentou são: a mutação **SHALL isolar a camada** (passar em todas as
outras conferências e falhar só na que está sob teste) e a asserção **SHALL conferir o motivo** da
recusa, não só que houve recusa. Duas vezes na 4a uma proteção pareceu coberta e não estava: o
cenário de cache truncado ficou **verde** com a camada (a) desligada, porque truncado também não
parseia e a camada (b) recusava por interpretação (4.4); e a identidade da folha só pôde ser
exercitada porque a `prova-2` foi construída com os **mesmos itens, posições e gabarito** da
referência (8.3b).

## P10

**P10 [V].** **Nunca deixar mutação injetada na árvore, e nunca confirmar a reversão pela memória do
que se editou.** A reversão é conferida rodando de novo. A cultura de "ver falhar" cria este risco;
ele é responsabilidade de quem a pratica.

## P11

**P11 [V].** **Nunca afrouxar tolerância, janela, limiar ou critério depois de conhecer o
resultado** — nem para "destravar", nem para "só desta vez". ADR-0007 é explícito: depois que o
resultado é conhecido, qualquer limiar escolhido é racionalização. Mudar exige ADR novo que registre
o resultado obtido. Três verificações da fatia 3a passaram por acidente antes de alguém perceber,
todas por tolerância maior que o defeito que deveriam pegar — "**tolerância folgada é o jeito mais
comum de um teste de medição não medir nada**".

## P12

**P12 [V].** **Nunca consertar vermelho enfraquecendo a asserção.** Se a asserção estava errada, a
correção é da asserção — e isso se **prova**, lendo a mensagem. Na 1.6 o vermelho era expectativa
minha errada sobre a linha de base, e o teste corrigiu a afirmação; na 9b.1 dois testes nasceram
vermelhos juntos e eram coisas diferentes — um defeito real e uma expectativa errada. **Vermelho
novo se diagnostica pela mensagem, nunca pela contagem.**

## P13

**P13.** **Nunca aceitar medição sem guarda de vacuidade.** Consulta que pode voltar vazia leva
piso; varredura leva canário; contagem sobre cache não conta. A guarda de RLS já passou por isso
(`o piso reprova um catalogo vazio`), a busca do token em repouso foi salva pelo canário e não pela
asserção, e a tarefa de `check` do APK **abria zero arquivos e passava** até o
`require(apks.files.any { … })` existir — sem ele, a verificação teria entrado no CI dizendo verde
sobre nada.

## P14

**P14.** **Nunca ler "duas medições independentes se contradizem" como "uma delas está quebrada".**
Procure o defeito comum às duas primeiro. Na 1.6 a contradição entre fidelidade e paridade era o
sinal certo, foi lida como ruído, e o conserto foi no instrumento: **117 linhas escritas e depois
revertidas**, com o comparador original medindo certo assim que a folha foi corrigida.

## P15

**P15.** **Nunca tratar vermelho de CI como regressão sem ler o log e o histórico do mesmo job.**
Passo marcado como falha **sem erro no log** é cancelamento — `concurrency: cancel-in-progress`
derrubou a paridade na PR #30 e custou horas. Da mesma família: dois falsos vermelhos por
`docker cp` copiando para o lugar errado, antes de a guarda de oito migrations existir.

## P16

**P16.** **Nunca declarar uma camada verificada pela prova da camada vizinha.** Cada uma precisa da
própria: os treze cenários de `DeviceSession` aprovam uma tela que mente, e os onze de
`ApiPlatosTest` aprovariam por unanimidade o 401 tratado no chamador. Suíte existente verde sob um
defeito novo não é sinal de que ele é pequeno — é sinal de que a cobertura anterior falava de outra
coisa.

## P17

**P17 [V].** **Nunca substituir decisão registrada por preferência.** Atualizar uma decisão com
informação nova é permitido e fica escrito ao lado dela — a decisão 11 do `design.md` da 4a faz isso
com a decisão 10 da 4a-zero, dizendo em uma linha que a razão original continua certa. Trocar por
gosto exige ADR. Depreciação de biblioteca, sozinha, **não** é argumento: `security-crypto`
continua, e o que muda é que o modo de falha conhecido dela passou a ser desta base.

## P18

**P18.** **Nunca introduzir tecnologia, abstração, política ou número sem consumidor.** Room ficou
para a 4b; a política de expurgo do cache não foi inventada porque não há evidência de pressão de
espaço; e o `paperPxPerMm` **saiu** do código quando a hipótese que ele servia caiu — "número medido
sem consumidor é número que ninguém lê". Tecnologia fora da lista do `CLAUDE.md` exige ADR, não
argumento.

## P19

**P19.** **Nunca refatorar fora do escopo, e nunca esconder refatoração dentro de commit
funcional.** A 4a corrigiu a regressão de `SessaoExpirada` e **deixou nomeada** a modelagem que a
tornaria impossível, em vez de fazê-la ali. Achado fora do escopo vira item escrito com dono e
fatia — nunca implementação silenciosa.

## P20

**P20 [V].** **Nunca fechar fatia com item de segurança, LGPD ou imutabilidade adiado sem
fatia-limite, custo e dono registrados.** §16 tem a tabela de ponto de não-retorno exatamente porque
o item da LGPD flutuou até quase virar retrofit: "o item estava certo, o **registro** é que não
dizia quando ele deixa de ser barato".

## P21

**P21.** **Nunca inferir o estado do sistema pela memória da conversa quando um arquivo o
registra.** Antes de alterar: o change ativo, a spec, o ADR, e só os arquivos de código diretamente
necessários. Contexto reconstruído de cabeça é a forma mais barata de contradizer uma decisão sem
perceber.

## P22

**P22 [V].** **Nunca mudar o ambiente local sem avisar e obter resposta** — JDK, Node, SDK,
emulador, versão de plugin, dependência do catálogo. Vale inclusive em modo automático.

## P23

**P23 [V].** **Nunca regravar golden, fixture ou hash sem fechar paridade e fidelidade na mesma
sessão**, com os artefatos dos dois lados gerados **naquela** sessão (P3). Um caminho de desenho
alterado já é motivo suficiente para não confiar na última medição, mesmo sem regravar golden — foi
o que a 4a fez ao mexer no `PLATOS_PACKAGE`.

## P24

**P24 [V].** **Nunca commitar segredo, `.env` ou credencial; nunca `--no-verify`; nunca force-push,
`reset --hard` ou remoção de arquivo não rastreado sem pedido explícito nesta sessão.** Antes de
apagar ou sobrescrever qualquer coisa, olhe o alvo.

## P25

**P25.** **Nunca misturar formatação, renomeação ou limpeza com implementação funcional**, e nunca
juntar contrato/DB/API com consumidor quando separar reduz risco.

## P26

**P26 [V].** **Nunca declarar publicado, servindo ou implantado o que não foi observado no
destino.** Publicar imagem não é implantar; implantar não é responder; responder `/health` não é
alcançar o banco. Cada elo se observa onde ele termina.

## P27

**P27 [V].** **Nunca adiar item com dono em prosa.** O registro de dívida do projeto é **um**: a tabela
de ponto de não-retorno do §16. Item adiado que não entra nela não tem data, e item sem data volta
a flutuar — que é o que §16 já dizia da LGPD, e o que quatro itens repetiram depois. **O archive de
uma mudança SHALL dizer, para cada linha cuja fatia-limite ou gatilho ela alcançou, se foi paga ou
reagendada**; reagendar é legítimo e exige fatia-limite nova com o motivo escrito. Silêncio não é.
Adiar em `docs/cobertura-*.md` continua certo e continua obrigatório — o que deixa de valer é adiar
**só** lá.

Paga por cinco incidentes. Em todos, o item tinha dono e tinha prazo, e o prazo morava em prosa:

| Incidente | Onde |
|---|---|
| **Modo degradado.** §15 o pôs na fatia 4, que fechou inteira sem ele. ADR-0013 o mandou para uma "4c" que nunca existiu. E o spec passou a afirmar o contrário de §10, sem marca de provisoriedade | auditoria 2.3; `openspec/specs/device-session/spec.md:395`, desde `6f2dfe9`; `docs/adr/0013-pull-de-referencia-imutavel-no-aparelho.md:138`, desde `46c4ffb`; `docs/cobertura-slice-4b-outbox-de-resultado.md:379` (`c0f327a`) |
| **Variante release.** O gatilho escrito, "a próxima que mexer em build ou variante", disparou duas vezes sem ninguém atender. A dívida chegou a ser documentada de novo sem ser paga, e só foi paga quando ganhou veículo (`f761306`) | auditoria 3.1; `docs/cobertura-fatia-4a-cache-referencia.md:220`, desde `60ba7bd`; os disparos `d054e1f` e `6c9356a`; `438030a` |
| **Migration em produção.** Produziu **HTTP 500** na conferência da 4b, com `/health` em 200, e continuou sem data numa lista de "o que este roteiro não cobre" | auditoria 4.6; `docs/deploy-api.md:459`, desde `5fb26b7`; o 500 em `docs/cobertura-slice-4b-outbox-de-resultado.md:302-304` |
| **Reexame do limiar do OMR.** A 3b o atribuiu "à fatia da câmera". A 3c era essa fatia, e registrou "continua aberta" sem novo prazo | auditoria 4.7; `docs/cobertura-fatia-3b.md:332` (`8e4e1b3`); `docs/cobertura-fatia-3c.md:266` (`f40fd9c`) |
| **A linha que o próprio plano de correção mandava pôr no §16.** O item do APK de release trazia a instrução escrita, "Fica na tabela do §16 com essa fatia-limite", e nunca entrou: fechou sem que a linha tenha existido. Nem o documento que propôs esta regra escapou do padrão | `docs/plano-de-correcao-antes-da-fatia-5.md:186-187` (`72e1557`), e a nota que registra a ausência, `:189` (`c7013c4`) |

O contraste que a sustenta: os itens que entraram na tabela (o roster cacheado, a classe H, a
retenção da classe B) avançaram e fecharam. A diferença entre os dois grupos não é importância: é
**estar na tabela**. A guarda que a torna reprovável é `tools/divida/divida.mjs`. Ela lê a tabela do
próprio §16 e reprova, no CI, uma linha vencida sem reconciliação.

## P28

**P28.** **Nunca deixar o mesmo valor, contrato ou recurso viver em dois módulos sem dono único
compilado ou sem uma conferência cruzada que reprove a divergência.** Espelho é permitido; espelho
**cego** não. Divergir entre registros que não se conhecem não quebra teste nenhum: compila, o
golden não muda, o hash continua igual, e o defeito chega ao papel ou à nota. Espelho contido por
**oráculo de saída** — como o `LayoutMap` em TypeScript, julgado pela paridade sobre o documento
rasterizado — satisfaz esta regra; espelho contido só por literal combinado nos dois lados, não.

Paga por três incidentes. O rigor desta base é por módulo, porque todo teste vive dentro de um, e
os três atravessavam dois:

| Incidente | Onde |
|---|---|
| **A versão do renderizador em três registros que não se conheciam.** Um renderizador subindo sozinho não derrubava teste nenhum: 14 de 14 no web e 308 de 308 no Android, medido | auditoria 4.4; `LayoutMap.kt:237`, `RendererContract.kt:25`, `apps/web/src/layoutMap.ts:124`; conferência cruzada desde `54160b9` |
| **O contrato do fio, digitado duas vezes** em quatro DTOs, e o mapa `QuestionAnswer → string` escrito três vezes | auditoria 2.1; dono único desde `1af2460`, e os espelhos removidos em `ea28ad0` (ADR-0015) |
| **A instância de Room, alcançada por três caminhos sem dono.** Duas `Activity` e o worker abriam, cada um, uma base nova sobre o mesmo `outbox.db` | auditoria 3.2; uma instância por processo desde `75f05ed` |

O precedente que mostra que a regra é barata: `tools/parity/limiar.mjs` (`8e4e1b3`) já fazia isto
para o limiar do OMR, e o comentário dele em `.github/workflows/ci.yml:187` escreveu a justificativa
desta regra antes de ela existir.

> **Sobre IA e custo:** as regras de `prompt_version`, schema validation e registro de chamadas
> estão no `CLAUDE.md` e valem integralmente. Não são repetidas aqui porque **ainda não há incidente
> que as pague** — e uma regra sem incidente não entra neste documento (§10).

## Seção E — por que existem P27 e P28 (introdução literal)

As regras acima governam o que acontece **dentro** de uma mudança, e ali esta base é implacável. As
duas abaixo cobrem as duas fronteiras que a auditoria de 2026-09-18 achou sem camada posicionada
para vê-las (`docs/auditoria-2026-09-18-antes-da-fatia-5.md` §7). Uma é o **tempo**: o que vence
depois do archive. A outra é o **espaço**: o que atravessa dois módulos. Entraram juntas, com a
ETAPA 8 do plano de correção, e são duas e não três. O terceiro eixo da auditoria, o artefato de
release, é **um** incidente, e já foi corrigido.

## §3 — "A fixture mínima sombreia a camada que deveria testar" (texto integral)

### A fixture mínima sombreia a camada que deveria testar

Quando duas conferências cobrem o mesmo dado por motivos diferentes, mutar a de dentro deixa a de
fora recusando pelo motivo errado, e o teste fica verde por acidente — ou vermelho sem provar nada.
Aconteceu duas vezes na fatia 4a: o cenário de conteúdo truncado no cache continuou **verde** com a
leitura confiando no nome do arquivo, porque truncado também não parseia e a camada (b) o recusava
por interpretação; e a conferência de identidade da folha só pôde ser exercitada porque a `prova-2`
foi construída com os **mesmos itens, posições e gabarito** da `prova-referencia` — com itens
diferentes, `ObjectiveScoring` recusaria por divergência de conjunto e a identidade nunca seria
consultada.

Ao escrever um "ver falhar" para uma camada específica:

- A fixture da mutação SHALL **isolar essa camada**: passar em todas as outras conferências e falhar
  só na que está sob teste. Se ela falha em duas, a mutação não diz qual das duas segurou.
- A asserção SHALL conferir o **motivo** da recusa, e não só que houve recusa. "Recusou" é
  indistinguível entre a camada certa e a vizinha.
- Leia **quais** cenários caíram e quais não: conjuntos disjuntos entre duas mutações são a prova de
  que as camadas são independentes; um cenário que sobrevive à mutação da própria camada que ele
  nomeia está medindo outra coisa.
