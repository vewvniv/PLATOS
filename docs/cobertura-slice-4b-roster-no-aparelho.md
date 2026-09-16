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
**antes** de existir o que apagar, nos três caminhos, e é medido por mutação em cada um.

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

---

## Verificação final

**`./gradlew build --rerun-tasks`** — o comando cheio do CI, com `--rerun-tasks` porque `UP-TO-DATE`
serve relatório velho com contagem plausível. **173 de 173 tasks executadas.**

**1332 testes, 0 falhas**, e o `timestamp` de **cada** relatório conferido, não só a contagem.

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

`./gradlew :apps:android:connectedDebugAndroidTest`: **52 testes, 0 falhas**, `timestamp`
`2026-09-16T23:21:33`.

Este carimbo é o da execução **final**, depois da reversão da mutação (F) — e não o da primeira
corrida, que foi `23:16:45` com 49 cenários. A distinção não é preciosismo: a primeira redação desta
seção citava o carimbo de `23:16:45` ao lado da contagem de 52, que são de corridas diferentes. Fica
dito em vez de corrigido em silêncio (P7), porque é exatamente o defeito que esta mesma seção
denuncia no relatório fóssil, um nível acima.

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
