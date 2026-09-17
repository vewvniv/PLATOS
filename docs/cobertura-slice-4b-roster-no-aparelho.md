# Cobertura — `slice-4b-roster-no-aparelho`

O que esta fatia verificou, **como cada verificação foi vista falhar**, e o que ficou sem verificação
automática. Segue a forma da cobertura da `slice-4b-roster-entrega` (a α) e da `4a-cache-referencia`.

A fatia põe o roster no aparelho, fecha os caminhos de apagamento dele, faz o gate decidir sobre dois
artefatos e leva o nome do aluno à tela no lugar do token.

---

## As duas negativas que esta fatia precisa afirmar por nome

**1. Esta fatia CRIA a primeira cópia de dado pessoal de aluno fora do servidor.** A α dizia, com
todas as letras, que ela não criava — que abria o caminho para a β criar. Esta é a β. O que a torna
aceitável não é o volume (token e nome de apresentação, e nada mais) e sim o apagamento: ele entra
**antes** de existir o que apagar.

São três caminhos, e eles **não** têm o mesmo grau de verificação — dizer "medido em cada um" seria
chamar de medido o que é herdado (P6, P8):

| Caminho | Como está verificado |
|---|---|
| `sair` | cenário próprio, **medido** pela mutação (C) |
| revogação de vínculo | cenário próprio, **medido** pela mutação (D), em conjunto disjunto do de (C) |
| desinstalação | **não verificado por esta base.** É garantia da plataforma — o Android apaga o `filesDir` —, e nenhum teste da árvore a exercita |

A frase original desta seção dizia "nos três caminhos, e é medido por mutação em cada um", o que era
falso para o terceiro e contradizia a seção de lacunas deste mesmo documento. Fica corrigida aqui, e
o engano fica dito (P7): num documento cujo propósito inteiro é não chamar de mitigado o que é apenas
conhecido, a afirmação larga estava na primeira frase.

**2. A fatia fecha UMA das duas linhas de ponto de não-retorno do §16, e não as duas.**

| Linha do §16 | Quem fecha | Estado |
|---|---|---|
| O roster cacheado sem regra de apagamento | esta fatia | **fechada** — o nome da mudança entrou na linha, como ela mesma mandava |
| A classe H não enumera o roster baixado | **jurídico externo**, levado pelo mantenedor | **aberta**, e não é desta fatia. Fatia-limite: antes de qualquer piloto com turma real em modo `nominal` |

O que mantém a segunda fora daqui é o padrão `coded` de ADR-0012: nele o roster no aparelho é código
ou apelido, e não nome civil. **O teto de retenção continua sem dono técnico** — enquanto o parecer
não o fixa, vale a leitura restritiva, e nada além dela é assumido.

---

## Como cada verificação crítica foi vista falhar

Os conjuntos esperados foram **escritos antes de injetar** cada mutação, e estão no corpo desta
seção junto do que de fato aconteceu. Reversão de cada uma conferida **rodando**, nunca pela
lembrança do que foi editado (P10). Nenhum marcador de mutação restou na árvore — conferido por
varredura, não por memória.

### O guardado e o apagamento

| Mutação | Esperado | Aconteceu |
|---|---|---|
| **(A)** `ATOMIC_MOVE` → escrita direta no destino | **nenhum vermelho** | nenhum vermelho — ver "lacunas" |
| **(B)** substituição → mescla | só `substituir_nao_deixa_o_aluno_retirado_para_tras` | exatamente esse |
| **(C)** roster fora da lista de `sair` | só `sair_apaga_o_roster_junto_com_o_resto` | exatamente esse |
| **(D)** roster fora da lista da **revogação** | só `revogacao_observada_apaga_o_roster_sem_o_usuario_sair` | exatamente esse |
| **(E)** `class_group` gravado | só `o_gravado_nao_tem_nada_alem_de_token_nome_e_instante` | exatamente esse, **na segunda tentativa** |

**(C) e (D) caíram em conjuntos disjuntos, um cenário cada.** É o que prova que os dois caminhos de
apagamento são independentes de fato, e não por compartilharem um trecho que nenhum dos dois exercita
sozinho. Se uma delas tivesse derrubado os dois cenários, a mutação não diria qual metade segurou
(§3).

Mensagens das asserções:

- (B) — `o aluno retirado no servidor continua guardado no aparelho ==> expected: <[Ana, Bruno]> but
  was: <[Ana, Bruno, Carla Dias]>`
- (E) — `uma linha de aluno guardada tem campo alem de token e nome: [token, nome, class_group]`

**A primeira tentativa de (E) foi inválida, e fica dita em vez de apagada (P7).** O campo entrou com
valor default, e `kotlinx.serialization` **não grava valores default** — a mutação não injetou defeito
nenhum, e o verde não dizia nada sobre o teste. Lido como achado, teria produzido uma lacuna que não
existe: "a varredura não pega campo novo". Reinjetada sem default, caiu no conjunto declarado. O que
isso ensina não é sobre o teste, é sobre a mutação: **mutação que não muda o artefato observado não é
mutação**, e o verde dela não é informação.

### O gate

| Mutação | Esperado | Aconteceu |
|---|---|---|
| fazer o gate barrar **também** com roster vazio | só `roster_vazio_abre_o_escaneamento` | exatamente esse; `roster_nunca_puxado_barra_mesmo_com_pacote_conferido` ficou **verde** |

Mensagem: `expected: <Pronta(...)> but was: <Barrada(..., motivo=ROSTER_AUSENTE)>`.

É a distinção inteira da fatia: "não há alunos" é afirmação sobre o mundo e abre a sessão; "não sei
quem são" barra. Se os dois tivessem caído juntos, os estados estariam colapsados, e a prova
publicada sem aluno — caso que `exam-package` declara legítimo — ficaria inescaneável.

### O nome na tela

| Mutação | Esperado | Aconteceu |
|---|---|---|
| gravar `nomeDoAluno` dentro de `ScanState.Scored` | só `o_resultado_apurado_nao_carrega_nome_de_aluno` | exatamente esse |

Mensagem: `o resultado apurado ganhou campo novo; se ele carrega nome de aluno, esta e a segunda
copia de dado pessoal no aparelho, fora da lista que sair e a revogacao apagam: [reading, score,
nomeDoAluno]`.

**É a mutação mais informativa da fatia.** `ScanSessionTest` (12 cenários) e `IdentidadeDaFolhaTest`
(5) ficaram **verdes** com o nome gravado. Ou seja: uma segunda cópia de dado pessoal no aparelho
passaria por toda a cobertura que existia antes desta fatia, sem nada acusar — e só uma asserção
escrita para isso a pega. Suíte existente verde sob defeito novo não é sinal de que ele é pequeno; é
sinal de que a cobertura anterior falava de outra coisa.

A negativa é medida na **estrutura do que é guardado**, e não na tela: uma asserção sobre a tela
passaria com o nome gravado. E varre os campos em vez de conferir um campo chamado `nome` — conferir
pelo nome deixaria passar `aluno`, `displayName` ou qualquer outro rótulo.

### Os consertos do code review

Seis achados de `/code-review`, todos confirmados no código antes de qualquer conserto. Três eram
**afirmações falsas escritas por mim** — não defeitos de teste verde indevido, e sim documentação e
cobertura afirmando mais do que existia.

| Mutação | Esperado | Aconteceu |
|---|---|---|
| **(G)** `prepararRoster` volta a esperar sempre | só `com_roster_guardado_a_abertura_nao_espera_o_pull` | exatamente esse; o par que afirma a espera ficou verde |
| **(H)** rótulo do roster volta a "SEM CONEXAO" | `o_selo_do_roster_nao_diz_sem_conexao` e `o_nome_vindo_do_roster_guardado_carrega_marca_com_idade` | exatamente esses dois |
| **(I)** tirar `organizacao.id` do gate | só o cenário de outra organização | **cinco cenários, e não esse** — ver abaixo |
| **(I′)** o duplo volta a ignorar a organização | só `roster_guardado_sob_outra_organizacao_nao_abre_o_escaneamento` | exatamente esse |
| **(J)** rota `/rosters` no lugar de `/roster` | só `a_obtencao_bate_na_rota_que_o_servidor_expoe` | exatamente esse; os outros 9 passaram |

**(I) não isolou a camada, e o conjunto declarado estava errado (P7).** Trocar `organizacao.id` por
`""` faz o duplo não achar roster nenhum, então todos os cenários que esperam `Pronta` caem — e o de
outra organização, que espera `Barrada`, fica **verde**. A mutação provava que o gate consulta o
roster, e não que ele confere o escopo.

**(I′) é a que isola**, e ela é a mais interessante da fatia: reintroduzir o sombreamento do duplo —
`ler` ignorando o argumento `organizacao`, como estava antes do conserto — derruba **só** o cenário
novo. É a prova direta de que aquele cenário é o que pega a fixture que sombreia, e de que sem ele a
metade do gate que confere escopo por organização não era exercitada por teste nenhum. É o §3 em
estado puro: a fixture mínima sombreia a camada que deveria testar.

**(J) mostra o buraco que o achado apontou:** com a rota errada, **9 dos 10** cenários de
`ObtencaoDeRosterTest` continuam passando, porque o `MockEngine` responde qualquer URL. Antes deste
teste, um erro de digitação em `/roster` atravessaria a suíte inteira de JVM e só quebraria em
aparelho, contra o servidor real.

**O que eu tinha escrito de falso, e fica dito:**

1. A KDoc de `RosterDto` afirmava que a deriva com o servidor estava "fechada pelo JSON literal que
   `ApiPlatosTest` fixa". `ApiPlatosTest` **não tem uma linha sobre roster**. Copiei a frase de
   `ProvaDto`, onde ela é verdadeira, sem conferir que valia aqui — e ela mandava o próximo leitor
   para uma cobertura inexistente.
2. A KDoc de `ScanActivity` afirmava que o roster era lido pelo "mesmo identificador" contra o qual a
   folha é conferida. Não era: leitor e escritores usavam variáveis diferentes, iguais só por um
   contrato implícito.
3. O duplo `RostersEmMemoria` ignorava a organização, e nenhum cenário daquele arquivo exercitava o
   escopo do gate.

Nenhuma das três aparecia como teste vermelho. Todas apareceram na leitura.

---

## Verificação final

**`./gradlew build --rerun-tasks`** — o comando cheio do CI, com `--rerun-tasks` porque `UP-TO-DATE`
serve relatório velho com contagem plausível. **173 de 173 tasks executadas.**

**1337 testes, 0 falhas**, e o `timestamp` de **cada** relatório conferido, não só a contagem. O
`timestamp` mais velho entre todos é `2026-09-17T00:34:13Z`, de menos de um minuto antes da leitura —
nenhum relatório de corrida anterior entrou na soma.

**Um relatório fóssil foi encontrado e excluído da contagem:** `testReleaseUnitTest`, 6 testes,
`timestamp` **2026-08-15T20:29:29.730Z** — um mês antes desta execução. É o mesmo fóssil que a
cobertura da 4a já registra: a tarefa deixou de existir no grafo com a subida do AGP 9, e o arquivo
ficou no diretório de resultados. Somado sem olhar a data, teria posto 6 testes fantasmas dentro do
verde de hoje. **A contagem acima não os inclui.**

**`openspec validate slice-4b-roster-no-aparelho --strict`**: válido.

**As negativas da proposta, medidas arquivo a arquivo** contra `f491ce6` (o commit em que a mudança
começou), e não afirmadas:

| Negativa | Medida |
|---|---|
| nada em `apps/api` | 0 arquivos |
| nada em `apps/web` | 0 arquivos |
| nada em `packages/domain` | 0 arquivos |
| nada em `vision/` e `omr/` | 0 arquivos |
| nenhuma migração | 0 arquivos |
| nenhuma spec fora de `device-session` e `scan-session` | 0 |
| nenhuma spec **principal** tocada | 0 |

O único arquivo fora de `apps/android` e de `openspec/changes/` é
`docs/architecture/ARQUITETURA-FINAL-v3.md`, e ele **é** escopo declarado da fatia (tarefa 4.1). A
outra linha do §16 não foi tocada: zero ocorrências dela em linhas alteradas do diff, e o confronto
direto da linha contra `HEAD` devolve identidade. O arquivo inteiro tem 1 inserção e 1 remoção.

### A metade instrumentada

`./gradlew :apps:android:connectedDebugAndroidTest --rerun-tasks`: **52 testes, 0 falhas**,
`timestamp` `2026-09-17T00:32:21`.

**Este carimbo é o da execução contra a versão final do código**, depois dos seis consertos do code
review. A corrida anterior — 52/52 em `2026-09-16T23:21:33` — vale para a árvore de antes deles, e os
consertos mexeram em `ScanActivity`, `SessaoActivity`, `MarcaDeLeitura`, `IdAlunoDaFolha`,
`ObtencaoDeRoster`, `ApiPlatos` e `PreparoDaProva`. Citá-la aqui seria relatório velho com contagem
plausível, que é o defeito que esta seção denuncia um nível acima.

A primeira redação desta seção chegou a citar o carimbo de `23:16:45` — da corrida de **49** cenários
— ao lado da contagem de 52. Fica dito em vez de corrigido em silêncio (P7).

Entre a corrida instrumentada e o build final, **uma KDoc foi corrigida** (a de `RosterDto`, item 1
da lista de afirmações falsas acima). É mudança só de comentário, que não altera bytecode nem
resultado de teste; o build de unidade foi refeito depois dela — `00:34:13Z` —, e a instrumentada
não. Fica dito para que a diferença entre as duas árvores seja do leitor, e não uma omissão.

**Uma corrida intermediária falhou sem ser vermelho de teste**, e isso também fica registrado: o
aparelho desconectou entre o build cheio e a instrumentada, e a corrida seguinte parou com "failed to
uninstall test APK" e **`Starting 0 tests`**. Zero teste rodou. Lida pela contagem de falhas — zero —
ela pareceria verde; quem denunciou foi o log, e não o número (P15).

**O instrumento é aparelho físico, e não emulador** — Xiaomi `2511FPC34G`, Android 16 (API 36), sem
`ro.kernel.qemu`. Fica dito porque o serial do `adb` foi o que denunciou: chamá-lo de emulador
descreveria o instrumento errado, e é o instrumento que decide o que a medição vale.

**A primeira execução passou com 49 cenários — e nenhum tocava o roster.** A lista de classes
instrumentadas trazia `VisaoEmRepousoInstrumentedTest` e o par dos pacotes, e nada para
`filesDir/rosters`. Os testes de JVM do roster rodam sobre `@TempDir`, que **não** é `filesDir`: eles
não provam que o caminho resolve nem que o aplicativo escreve onde ele de fato guarda. Declarar essa
camada verificada pela prova da vizinha é P16, e os 49 verdes não a cobriam.

`RosterEmRepousoInstrumentedTest` fechou a lacuna (3 cenários, daí 52), espelhando o precedente
inclusive no canário que impede "está em `filesDir`" de passar para arquivo vazio. Ele afirma duas
coisas que andam juntas: que o nome do aluno fica **legível em claro** sob `filesDir` — que é o que
"armazenamento comum" significa, e é decisão de ADR-0013, não descuido — e que o apagamento por
organização o remove.

**Mutação (F), no caminho mais crítico do §16:** `deleteRecursively()` → `delete()` no apagamento por
organização. Cai em **duas** camadas, e não numa:

| Camada | Cenário vermelho |
|---|---|
| JVM | `apagar_uma_organizacao_deixa_a_outra_intacta` |
| Aparelho | `apagar_a_organizacao_remove_o_diretorio_do_disco_e_deixa_o_da_outra` |

**Os conjuntos não são disjuntos, e isso fica dito em vez de apresentado como isolamento.** A
mutação prova que o apagamento por diretório é exercitado nas duas camadas; ela **não** isola o que o
teste instrumentado acrescenta. O que ele acrescenta é o caminho real, e isso nenhuma mutação de
código alcança — a falha dele seria ambiental. Nenhuma mutação foi inventada para simular isso.

Reversão conferida **rodando** nas duas camadas: JVM 11/11 em `2026-09-16T23:20:50Z`, e 52/52 no
aparelho depois disso.

---

## O que ficou sem verificação automática, e por quê

Nada aqui é mitigado. É **conhecido**, que é coisa diferente.

- **A atomicidade da gravação não tem teste que a pegue.** A mutação (A) — trocar o `ATOMIC_MOVE` por
  escrita direta — passa em tudo. Os dois cenários que existem plantam um `.parcial-` à mão ou
  conferem que não sobrou parcial, e **nenhum interleava leitura com escrita**, que é exatamente o que
  a atomicidade protege. `PacotesEmArquivo` e `VisoesEmArquivo` têm a mesma lacuna: a forma foi
  herdada deles, e o buraco veio junto. **Dono:** esta base. **Fatia-limite:** a próxima que mexer em
  gravação de qualquer um dos três.
- **A desinstalação apaga o roster porque o Android apaga o `filesDir`.** É garantia da **plataforma**,
  e não do aplicativo. O requisito nomeia sair e a revogação, que são os caminhos que o aplicativo
  controla; a desinstalação fica dita como **herdada**, sem teste próprio.
- **Sobreviver à morte do processo.** O teste instrumentado usa instância nova lendo o que a
  anterior gravou, o que mostra que o dado está em disco e não em memória — e é até onde um teste
  instrumentado alcança. Matar o processo e reabrir é conferência de aparelho, e não foi feita.
- **O roster contra o servidor real.** Os cenários usam `MockEngine` com o JSON literal do contrato, e
  não a instância hospedada. A deriva entre os dois lados é fechada por esse literal, do mesmo jeito
  que os outros DTOs desta base — mas ninguém observou um roster real descendo.
- **Roster grande.** Nenhum cenário passa de três linhas. A α já registrou que paginação sem medição
  seria número sem consumidor.
- **Modo `nominal` com nome civil de menor.** A fatia guarda o que a entrega traz, sem filtrar por
  modo — decisão 4 da α, que recusou pôr a mesma regra em dois lugares. Nenhum cenário desta fatia
  roda em organização `nominal`; a recusa de escrita em `coded` tem cobertura própria desde a fatia da
  LGPD, do lado do servidor.

## Itens fora de escopo, com dono e fatia-limite (P19)

Nenhum destes foi tocado nesta fatia.

1. **A KDoc de `VisoesEmArquivo` diz "Room continua sendo da 4b".** Esta **é** uma 4b, e não traz Room
   — e a frase não está errada sobre o gatilho, que ela cita corretamente de ADR-0013 (o outbox); está
   imprecisa sobre o **nome**, porque a 4b foi cortada em várias fatias depois que aquela KDoc foi
   escrita. Uma linha, nenhum comportamento. **Dono:** esta base. **Fatia-limite:** a do outbox, que é
   quando Room de fato entra, ou a próxima que tocar `VisoesEmArquivo`.
2. **`ScanActivity` relê o roster a cada abertura da câmera, e não a cada folha.** É a decisão certa —
   o escaneamento não muda o roster —, mas significa que corrigir um nome no servidor durante uma
   sessão de escaneamento não aparece até sair e voltar. Ninguém pediu que aparecesse. **Dono:** esta
   base. **Fatia-limite:** a primeira que receber pedido de atualizar roster sem fechar a câmera.
