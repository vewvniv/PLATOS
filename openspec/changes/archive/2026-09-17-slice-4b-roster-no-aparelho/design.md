## Context

Ver `proposal.md` — Why. O que molda a abordagem, e não está lá:

- O aparelho **não tem Room**. O que ele guarda hoje são arquivos com rename atômico:
  `PacotesEmArquivo` (endereçado pelo `content_hash`) e `VisoesEmArquivo`
  (`visoes/<organization_id>.json`, sobrescrito a cada consulta).
- ADR-0013 §3 diz por quê, e nomeia o gatilho: *"Room fica para a 4b, onde o **outbox** é de fato
  relacional"*.
- O gate de pré-voo já existe e já é binário sobre o pacote (`device-session`), com quatro motivos de
  recusa distintos. Esta fatia acrescenta o quinto.
- `device-session` já exige que dado vindo de cache seja **visualmente distinto** de dado fresco, com
  instante e ação de atualizar. O nome do aluno vindo do roster guardado cai sob esse requisito já
  existente — não sob um novo.

## Goals / Non-Goals

**Goals:**

- Um roster guardado por (organização, prova), no mesmo regime atômico da visão.
- O gate decidindo sobre dois artefatos, com **vazio ≠ ausente**.
- O nome na tela de resultado, com o token como recurso.
- O apagamento ao sair fechando a linha do §16.

**Non-Goals (de design, além dos da proposta):**

- Não introduzir Room. Ver decisão 1.
- Não desenhar o **outbox** nem o push de resultados. É o gatilho do Room, e é outra fatia.
- Não desenhar sincronização incremental de roster: sem hash e sem ETag, o pull é do estado corrente
  inteiro (α, decisão 3).

## Decisions

### 1. Arquivo, não Room — e a razão é citável, não preferência

`rosters/<organization_id>/<exam_short_id>.json`, ao lado de `visoes/` e `pacotes/`, com gravação por
temporário + `ATOMIC_MOVE` no mesmo diretório, exatamente como `VisoesEmArquivo`.

**Por que não Room, quando `CLAUDE.md` lista Room nas preferências e §13 o sanciona:** ADR-0013 §3
não adiou Room por gosto, adiou com **gatilho nomeado** — o outbox, que é relacional de fato. O
roster não é: é um registro por prova, lido inteiro, substituído inteiro, sem consulta parcial e sem
escrita concorrente. A regra 8 (`CLAUDE.md`) pede necessidade comprovada antes da abstração, e
trazer Room para guardar um registro por prova seria abstrair sobre a necessidade de outra fatia.

**O que isto obriga a dizer:** a KDoc de `VisoesEmArquivo` afirma "Room continua sendo da 4b", e esta
fatia **é** uma 4b que não traz Room. A frase não está errada sobre o gatilho — ela cita o mesmo
ADR-0013 —, está imprecisa sobre o nome: a 4b foi cortada em várias fatias depois que ela foi
escrita. Corrigir aquela KDoc é item fora de escopo desta fatia, com dono e fatia-limite registrados
na cobertura — não se corrige aqui, que seria limpeza oportunista (regra 6).

**Alternativa descartada: Room agora, "já que a 4b vai precisar".** É a mesma forma de P18 que a α
recusou ao não entregar campos que ninguém consome — infraestrutura antes do consumidor. Quando o
outbox existir, ele traz Room com a necessidade na mão, e migrar dois arquivos JSON para tabela é
trabalho menor do que ter carregado o schema desde agora.

### 2. "Puxado" é um estado do aparelho, e precisa ser representável separado de "vazio"

O gate distingue três coisas, e por isso o guardado **não pode ser só a lista**: uma lista vazia em
memória não diz se ela veio de um pull ou de nunca ter havido pull.

O arquivo existir **é** a afirmação de que o pull aconteceu; o conteúdo dele diz quantos alunos há.
Prova sem arquivo é prova sem roster puxado, e é o que o gate barra. É a mesma forma que
`PacotesEmArquivo` já usa — presença do arquivo como afirmação, não um campo `baixado: true` dentro
dele, que poderia divergir do fato.

**Alternativa descartada: um campo `puxadoEm` dentro de um arquivo sempre presente.** Criaria o
estado "arquivo existe, mas não vale", que é exatamente o estado parcial que a gravação atômica
existe para impedir.

### 3. O gate barra por roster ausente, e o custo é dito agora

Uma prova cujo pacote já está guardado, mas cujo roster nunca foi puxado, **deixa de abrir** até um
pull com rede. Isso é regressão de disponibilidade para quem já tinha o pacote, e é aceito porque a
alternativa é pior: abrir a sessão e escanear uma turma inteira produzindo tokens, sem o professor
perceber que está sem roster, é o modo de falha silencioso que a base recusa (P3 — estado que mora no
instrumento e não avisa quando some).

Na prática os dois pulls acontecem na mesma escolha de prova, com rede, e o estado só aparece se o
roster falhar sozinho — rede caindo entre as duas chamadas. A frase de recusa é própria, e o requisito
proíbe apresentá-la como falha genérica.

### 4. O nome vem do roster na hora de apresentar, e não é gravado no resultado

O resultado guarda o **token**, que é o que a folha carrega; o nome é resolvido contra o roster
guardado quando a tela é montada.

Gravar o nome dentro do resultado criaria uma segunda cópia do dado pessoal, com vida própria e fora
da lista que "Sair apaga" enumera — e o apagamento ao sair passaria a depender de alguém lembrar de
enumerá-la. Uma cópia, um lugar, uma regra de apagamento.

**Consequência aceita:** corrigir o nome no servidor e puxar o roster de novo muda o nome exibido em
resultados já apurados. Isso é o comportamento certo — o roster é mutável por construção (ADR-0002),
e a nota não muda.

### 5. A marcação de cache é a que já existe

O nome apresentado a partir do roster guardado usa a marca de `device-session` ("Dado apresentado a
partir do cache é visualmente distinto de dado fresco"), com o instante e a ação de atualizar. Criar
uma marca própria para o nome poria a mesma regra em dois lugares — a duplicação que a α também
recusou ao não filtrar o modo de identificação na saída.

**Corrigido depois da auditoria, e a redação acima fica (P7).** A conclusão continua certa; a
**premissa estava errada**, e quem lesse só o parágrafo de cima repetiria o erro. Ele diz "a que já
existe", como se o requisito da marca alcançasse o roster. Ele não alcança: o texto vigente é "dado
vindo da **última visão conhecida**", e o requisito da visão diz, com todas as letras, que ela "SHALL
NOT conter dado pessoal" e que "roster e identidade de aluno seguem fora daqui". A spec **separa** os
dois de propósito, e eu apresentei como cobertura existente o que era inferência minha (P6).

O conserto mantém a intenção original — uma regra num lugar só — em vez de trocá-la: esta fatia
**estende o alcance** do requisito da marca para o dado guardado que aparece em tela, roster
incluído, e `scan-session` apenas o aplica. Se eu tivesse escrito a marca própria dentro de
`scan-session`, a duplicação que o parágrafo de cima teme é exatamente o que teria acontecido — a
premissa errada apontava para o desenho certo pela razão errada.

### 6. A revogação de vínculo apaga o roster, e não espera o logout

Achado da mesma auditoria. O apagamento entrou primeiro só na lista de `sair`, e isso deixava um
buraco que a fatia se declara fechando: o requisito da visão manda apagar visão e pacotes **quando o
servidor responde que a organização não é mais do usuário**, e o roster não estava lá.

**Os dois caminhos perdem o acesso; só um é ação do usuário.** Quem foi removido da escola não vai
sair do aplicativo para que o apagamento aconteça. Deixar a cópia esperando um logout manteria nome
de aluno num aparelho cujo dono já não pertence à organização — o caminho novo pelo qual o direito de
eliminação deixaria de alcançar, que é o que o §16 registra.

**Alternativa descartada: apagar só ao sair, e tratar a revogação como caso raro.** Raridade não é
argumento sobre dado pessoal, e o custo de incluir o roster numa lista que o código já percorre é
próximo de zero.

### 7. O pull consulta a rede primeiro, e o guardado é reserva

`obterRoster` chama a API e só cai no guardado quando ela não responde ou recusa. É **o inverso** de
`obterPacote`, que consulta o cache primeiro.

**Por que o inverso, e por que isso não é inconsistência:** o pacote é imutável e endereçado por hash,
então cache primeiro é correto por construção — hash igual é conteúdo igual (ADR-0009), e uma segunda
chamada só gastaria rede para receber os mesmos bytes. O roster é **mutável por construção**
(ADR-0002). Preferir o guardado com a rede disponível apresentaria um nome que o servidor já
corrigiu, e **nada na tela diria isso** — a marca de cache diz de quando o dado é, não que ele está
desatualizado em relação a um servidor que respondeu e não foi perguntado.

**Falha e recusa preservam o guardado.** O pull que não chega não deixa o aparelho pior do que
estava; é o mesmo critério de "atualizar sem rede não esvazia a tela" que a visão já segue. Quem
transforma ausência em recusa explicada é o gate, e não esta função.

**Custo aceito:** toda escolha de prova com rede gasta uma chamada de roster, mesmo quando nada mudou.
Sem hash e sem ETag — a α registrou que ADR-0002 recusou os dois —, não há como perguntar "mudou?"
mais barato do que perguntar "qual é?". O roster é pequeno. Se a medição mostrar que o custo importa,
o veículo é mudança própria com o número na mão, e não uma inversão de ordem decidida sem ele.

**Esta decisão nasceu durante a implementação, e a spec dizia o oposto até a auditoria (P7).** A
redação original do requisito era "do que já guarda, quando houver roster guardado para aquela prova,
e da API caso contrário" — cache primeiro, copiada da forma do requisito do pacote sem que a diferença
entre imutável e mutável fosse considerada. O código foi escrito invertido, com a razão numa KDoc, e o
contrato ficou para trás: é a regra 1 do `CLAUDE.md` quebrada na direção que ela existe para impedir.
Fica dito em vez de apagado porque o erro não foi a inversão — ela está certa —, e sim **decidir no
código o que o contrato já afirmava**, e não voltar para corrigi-lo.

**Atualizada depois da revisão de código, e o texto acima fica (P7).** A decisão — rede primeiro —
continua certa, e a redação de cima não estava errada sobre ela. O que faltava era **quando esperar**.

`obterPacote` não toca a rede quando o pacote está em disco, então numa prova já baixada a espera
passava a ser **só** do roster. Numa rede de escola associada a um ponto sem saída não há
`UnknownHostException` rápido: o pedido fica pendurado até o tempo limite, e a tela ficava em
`Preparando` antes de abrir com pacote e roster que já estavam ali. Era a sala sem sinal — o caso
para o qual o guardado existe — **piorada** por ele.

`prepararRoster` passou a esperar o pull **só quando não há roster guardado**, que é quando esperar
tem significado, porque o gate barra sem ele. Havendo guardado, ele serve na hora e a atualização
corre solta. O custo entrou no requisito e não ficou escondido no código: um nome corrigido no
servidor aparece na escolha **seguinte** daquela prova.

### 8. O selo do roster não fala de conexão

`marcaDeLeitura` ganhou o rótulo por parâmetro, com `ROTULO_SEM_CONEXAO` de padrão e
`ROTULO_LISTA_BAIXADA` no caminho do roster.

A regra continua **uma só** e no mesmo lugar — dado guardado leva selo, e o selo diz de quando o dado
é. O que muda é a palavra, porque as duas telas chegam ao selo por razões diferentes: a visão
guardada só aparece **porque** a consulta falhou, e o roster guardado aparece em toda leitura de
folha, inclusive com o aparelho on-line e o pull recém-concluído.

Deixar "SEM CONEXAO" ali afirmaria algo falso na maioria das vezes em que o selo aparece — e o custo
não é só a mentira: ensinaria o professor a ignorar o mesmo selo na tela de trabalho, onde ele é
verdade e é a única coisa que distingue dado de ontem de dado de agora.

**Alternativa descartada: uma segunda função de marca para o roster.** Seria a duplicação que a
decisão 5 recusou, agora pelo outro lado — duas cópias da regra "diz de quando é", divergindo na
primeira mudança.

### 9. O roster é lido pela mesma chave com que foi gravado

`ScanActivity` recebe o `short_id` da prova por `Intent` e lê o roster por ele.

**A primeira versão lia por `examPackage.meta.examId`**, com uma KDoc que justificava a escolha
dizendo evitar "dois caminhos para dizer de qual prova se fala". A justificativa estava certa e a
leitura, errada: os dois caminhos **já existiam**, porque os escritores — o pull e o gate — usam
`prova.shortId`. Os dois valores são iguais hoje por um contrato implícito entre a publicação e o
pacote que nada nesta base prende.

Se ele se rompesse, `ler` devolveria `null` e **toda** folha cairia em silêncio no token com "não
está no roster": sem erro, sem barragem, e com a fatia inteira desaparecida sem nada acusar. Um extra
a mais no `Intent` é preço barato por não depender de uma igualdade que ninguém afirma.

## Risks / Trade-offs

- **Esta fatia cria a primeira cópia de dado pessoal de aluno fora do servidor** → O apagamento ao
  sair é requisito nomeado, não genérico, e tem cenário próprio; a substituição por inteiro a cada
  pull impede que linha retirada no servidor sobreviva no aparelho. O que **não** está coberto é o
  teto de retenção: ele depende do parecer jurídico externo, e o §16 mantém essa metade com dono e
  fatia-limite fora daqui.
- **Modo `nominal` põe nome civil de menor no aparelho** → Não é decisão desta fatia: ADR-0012 fixa
  `coded` como padrão, e é isso que mantém a fatia-limite da classe H no piloto nominal. Esta fatia
  guarda o que a entrega traz, sem filtrar por modo — pela decisão 4 da α, que recusou pôr a mesma
  regra em dois lugares.
- **Desinstalar apaga porque o Android apaga o `filesDir`** → É verdade, mas é garantia do sistema, e
  não do aplicativo. O requisito nomeia sair, que é o caminho que o aplicativo controla; a
  desinstalação fica dita na cobertura como coberta pela plataforma, sem teste próprio.
- **O aparelho não sabe se o roster mudou sem pedir de novo** → Custo herdado da α, registrado lá.
  Esta fatia não o reabre; se a medição aparecer, o veículo é mudança própria.
- **Gate mais restritivo pode surpreender quem já tinha o pacote** → Decisão 3, com a frase própria
  de recusa e o pull na mesma escolha de prova.

## Migration Plan

Nada a migrar: não há dado de roster no aparelho hoje. O arquivo nasce no primeiro pull.

**Reversibilidade:** reverter a fatia devolve o gate a um artefato e a tela ao token. Os arquivos que
tiverem sido gravados ficam órfãos em `rosters/` — e por isso a ordem de implementação põe o
apagamento ao sair **antes** de o primeiro roster ser gravado (ver `tasks.md`), para que não exista
janela em que o aparelho guarde nome de aluno sem caminho de apagamento.

## Open Questions

Nenhuma que mude specs, abordagem ou tarefas. As duas que existem têm dono fora desta fatia: o teto
de retenção (jurídico externo, §16) e o momento de Room (a fatia do outbox, ADR-0013).
