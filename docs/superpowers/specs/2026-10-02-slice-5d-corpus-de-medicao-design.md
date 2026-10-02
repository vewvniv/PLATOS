# slice-5d-corpus-de-medicao — design (o COMO)

**Requisitos de entrada (o QUÊ):** `openspec/changes/slice-5d-corpus-de-medicao/proposal.md` e
`specs/measurement-corpus`, `specs/scan-session`. **Eles estão desatualizados em relação a este documento** (escritos
antes das decisões abaixo, que trocam o desenho "exportação pelo professor" por "coleta no APK debug, saída por cabo").
O alinhamento é um `/opsx:update`, **antes de qualquer plano ou código** (CLAUDE.md, regra 5). Este documento não o faz.

**Caminho do brainstorming:** arquitetural (capability nova, fluxo de dado de menor para fora do aparelho, variante de
build). **Escopo:** o aparelho (Android) e a documentação. Servidor, banco, contrato do fio e domínio não mudam.

## 0. Para que isto serve (a intenção que o desenho preserva)

O mantenedor quer saber se um **OCR embarcado no aplicativo** lê a letra do aluno bem o bastante para que o **texto
transcrito**, e não a imagem, vá à IA na correção discursiva (a visão do LLM é mais cara em tokens). A **fiabilidade da
transcrição** decide, por resposta, se o texto segue, se a imagem vai à IA, ou se o professor corrige à mão (§9; D42).

Isso exige três coisas, em três mudanças, nesta ordem: **(1) dados** com a letra e uma referência do texto certo
(**esta**); **(2) uma bancada** que roda motores candidatos sobre esses dados, no computador, e escolhe o motor (ADR);
**(3) o motor no app** com o indicador de fiabilidade. Esta mudança entrega só o (1). Não escolhe motor, não mede, não
fixa limiar, não integra OCR. O §9.2 avisa que a confiança do próprio motor é descalibrada: o corte sai de medição.

## 1. Decisões (aprovadas pelo mantenedor em 2026-10-02)

| # | Decisão | Por quê |
|---|---|---|
| D1 | **Quem coleta: só o mantenedor, nas turmas dele.** | A primeira rodada não é recurso de produto. Elimina a tela de declaração, o compartilhar e a confirmação de entrega. |
| D2 | **O código da coleta existe só no APK debug** (conjunto de fontes `debug`; o `release` recebe uma implementação vazia da mesma interface). | A garantia é de compilação, não de configuração: o APK que um professor instalar não contém o que copia foto de menor. |
| D3 | **Interruptor de coleta, desligado por padrão.** Ligado, toda nota confirmada copia todas as discursivas da folha. | Para 60–150 respostas, uma ação por turma. Escolha por resposta é atrito sem ganho para uso só do mantenedor. |
| D4 | **A foto sai por cabo** (`adb` + `run-as`), sem tela de exportação, sem rota, sem tabela. | Nenhuma superfície nova: `run-as` só funciona em app depurável. |
| D5 | **A transcrição de referência é digitada no computador**, com a foto aberta, em campo do JSON da amostra. | É a régua para dizer se o OCR leu bem; digitar 150 textos no celular não é razoável. |
| D6 | **A 5d coleta e escreve o critério; a bancada e o OCR no app são mudanças seguintes.** | Escolher e integrar motor sem letra real é a suposição que o §9 manda evitar. Comparar motores no computador é horas; integrá-los no Android, semanas. |
| D7 | **Sem ADR de resequenciamento.** A ordem do §15 se mantém: a 5 junta "os dados para decidir §9"; o OCR é da 8. | Nada do que a arquitetura registra é trocado. |
| D8 | **Corpus: 60 a 150 respostas, uma ou duas turmas.** O critério registra que isso não separa exatas de humanas. | Escolha do mantenedor; a margem de uma taxa medida sobre 60 é ampla, e o ADR diz isso antes do resultado (P11). |

**ADR novo (critério do corpus), sem limiar.** Registra composição, o que o corpus permite decidir (o motor; depois o
corte de fiabilidade), o que **não** decide (outras turmas, outras matérias, caneta ou lápis fora do que foi coletado) e
as convenções de transcrição. **Nenhum número de corte é fixado aqui:** cada ADR da bancada fixa o seu antes de rodar
(P11, ADR-0007). Número: o próximo livre depois do 0021.

## 2. O que a amostra é

Por resposta discursiva da folha cuja nota foi confirmada, com o interruptor ligado:

- `corpus/<id>.png` — **os mesmos bytes** de `respostas/<arquivo>.png`, sem reprocessar (o OCR do futuro verá o que o
  aplicativo guardou).
- `corpus/<id>.json` — versão do formato; `pontos` (decimal exata, como `Pontos`/`scoring`); `maximo`; `pacote`
  (hash) e `item`; `referencia: null` (preenchido no computador); `descartar: false`.
- `<id>` é um UUID sorteado na cópia, sem relação com qualquer outro identificador.

**Nunca leva:** nome, turma, matrícula, token do aluno, `captureId`, caderno, nome ou caminho do arquivo de `respostas/`,
data ou hora. Dois ids não permitem dizer que duas respostas são da mesma folha. O arquivo `.png` tem o nome do `<id>`,
não o do arquivo de origem.

O formato do JSON é **contrato digitado uma vez** (P28): `kotlinx.serialization` no aparelho, lido depois pela bancada em
outra linguagem. Conferência cruzada no molde de `tools/parity/fio.mjs` (o literal do formato nos dois lados), tarefa do
plano.

## 3. O aparelho

### 3.1 Onde mora o código

- **`src/main`:** a interface `ColetaDoCorpus` (`copiar(respostas, pontuacoes, ...)`), o ponto de chamada, e a leitura do
  interruptor. É o que o `release` compila.
- **`src/debug`:** `ColetaDoCorpusEmArquivo` (a implementação real) e a tela do interruptor. **`src/release`:**
  `ColetaDoCorpusNula`, que não faz nada e não referencia o diretório. `src/debug` e `src/release` **não existem hoje**:
  nascem com esta mudança.
- **Testes:** os que exercitam a implementação real vão em `src/testDebug`; o que roda nos dois (a interface, a nula) fica
  em `src/test`. A suíte de unidade roda também sobre o release (ETAPA 7.2); teste que dependa da classe de debug no
  `src/test` quebraria o release.

### 3.2 O ponto de chamada

`ScanActivity` monta o `NotaPendente` e chama `gravarNota` (`ScanActivity.kt`, ~l.399). A cópia acontece **antes** de
`gravarNota` e **fora** do caminho que decide o sucesso: é ela quem lê `respostas/`, e depois de `gravarNota` o envio em
segundo plano pode confirmar a nota e eliminar a imagem antes de a cópia terminar. Exceção ou recusa da cópia vira aviso
(debug), nunca falha da nota. Se a gravação da nota **falhar** depois da cópia, as amostras recém-copiadas são eliminadas
(nota não gravada não deixa amostra). A nota durável é sucesso mesmo que a cópia falhe (o princípio que a 5c-3 registrou
para o caderno e o agendamento, `f78388c`). A nota que o professor não confirmou não chama a cópia. O teste da ordem é
um dos que se vê falhar: inverter a ordem tem de reprovar.

### 3.3 A cópia

Reusa o padrão de `RespostasEmArquivo`: temporário no mesmo diretório e `ATOMIC_MOVE`; a costura de escrita que deixa o
teste falhar no meio. O `.png` é gravado **antes** do `.json`; amostra sem `.json` é órfã e a varredura a elimina.

### 3.4 Prazo e eliminação

- Teto de **30 dias** sobre `corpus/`, contado da cópia (o `.json` guarda o instante **só no sistema de arquivos**, via
  `lastModified`, não no conteúdo: a amostra não leva data).
- Roda na abertura do aplicativo e dentro da `VarreduraPeriodicaWorker` que a 5c-3 já agenda; a implementação real entra
  na varredura pela interface, e a nula não entra.
- **Sair da sessão e a revogação eliminam `corpus/`.** É cópia. O protocolo diz: puxar antes de sair.
- Falha ao eliminar um arquivo não impede o resto (regra da `RetencaoDaResposta`).

### 3.5 Fora do backup e da transferência

`corpus/` está sob `filesDir`, no mesmo domínio `file` da regra de extração. O teste que confere `respostas/` contra a
regra (`regras_de_extracao_de_dados.xml`) passa a cobrir a pasta nova. O diretório tem **dono único do nome** em código
(`diretorioDe(filesDir)`), no molde de `RespostasEmArquivo` (P28).

## 4. A saída por cabo e o computador

- `tools/corpus/puxar.mjs`: `adb exec-out run-as com.platos.android` copia `files/corpus/` para uma pasta **fora do
  repositório** e confere que cada `.png` tem seu `.json` e vice-versa, e que a contagem bate com a do aparelho.
- `.gitignore` ganha a pasta de trabalho por padrão como segunda rede (a primeira é guardar fora do repositório).
- `docs/protocolo-corpus-de-medicao.md`: o passo a passo — ligar o interruptor, corrigir a turma, **puxar antes de sair da
  sessão**, preencher `referencia` com a foto aberta (transcrever literalmente, inclusive erros de ortografia do aluno;
  marcar trecho ilegível em vez de adivinhar), marcar `descartar` onde houver nome ou dado pessoal escrito, e onde guardar.
- O protocolo entra como referência no roteiro da sessão única de papel (`docs/protocolo-medicao-impressa.md`); a coleta
  de alunos **não** depende da impressora e pode ocorrer à parte.

## 5. Como cada garantia será vista falhar (rigorous.md §3; P9)

| Garantia | Prova | Mutação |
|---|---|---|
| O release **não contém** o código da coleta | Estender `VerificarApkSemPacoteTask` (ou irmã) para procurar a classe da coleta no dex do release, com canário: o debug a contém; vacuidade por variante (P13) | Mover a implementação para `src/main`; a tarefa tem de reprovar |
| A amostra não identifica o aluno | Caderno de fixture com token, turma, matrícula, captura e caminho; nenhum aparece no `.json` nem no nome do `.png` | Pôr o token no `.json`: reprovar **pelo campo**. A fixture isola a camada (P9) |
| Interruptor desligado não copia | Teste do padrão | Inverter o padrão |
| Falha da cópia não derruba a nota | Costura de escrita que falha no meio | Deixar a exceção subir |
| A cópia lê a imagem antes de `gravarNota` | Teste em que a imagem é eliminada assim que a nota é gravada (envio simulado confirmando na hora) | Chamar a cópia depois de `gravarNota` e ver a amostra faltar |
| Nota não gravada não deixa amostra | Costura em que `gravarNota` falha depois da cópia | Não eliminar as amostras e ver sobrarem |
| Cópia atômica | Temporário + rename | Escrever direto |
| 30 dias, sair e revogação eliminam | Relógio injetado; um cenário por caminho | Tirar um caminho por vez; só o cenário dele cai |
| Fora do backup e da transferência | Teste existente estendido | Mudar a regra |
| Nenhum código de rede lê `corpus/` nem `respostas/` | O `grep` registrado na cobertura, estendido; `ARespostaNaoSaiDoAparelhoTest` intacto | Pôr uma leitura em código de envio |
| A saída por cabo funciona | Emulador: ligar, corrigir uma nota, rodar `puxar.mjs`, conferir contagem | Apagar um `.png` e ver o script reprovar |
| Foto fora do `git` | `git check-ignore` na pasta | Tirar a linha |

**Sem verificação automática, declarado (P8):** a coleta com turma real em aparelho físico; é do mantenedor, no fim,
com a prova final (não para o fluxo). O Xiaomi exige toque manual de permissão de câmera.

## 6. Não é feito aqui

OCR; escolha, integração ou bancada de motores; qualquer limiar; `AiGateway`; roteamento texto/imagem; sugestão de
correção manual; compartilhar, tela de exportação, declaração, rota ou tabela. **TexTeller continua fora de v1.**
A reconciliação da política v2.0 com ADR-0012, §16 e a spec `scan-session` é do mantenedor, em commit próprio.

## 7. Dívida (§16, P27) — o que o archive diz de cada linha

- **`LGPD com dados de menores` (`antes-de:primeiro-piloto`):** reescrita: letra de menor sai do aparelho **por cabo, só
  pelo mantenedor, em APK debug**. Segue aberta (jurídico externo). Não é paga.
- **`Acurácia em manuscrito` (`6`):** esta mudança **coleta**. O archive diz quantas amostras, de quantas turmas. Segue `6`.
- **`A política §10.8 diverge do comportamento` (`antes-de:publicacao-da-politica`):** a §10.8 não existe na v2.0; o archive
  diz se a linha muda de referência ou sai, conforme o mantenedor decidir.
- **`A guarda de dívida não lê a tabela "Aberto"` (`5`):** não é tomada. Silêncio não é reconciliação.
- **`O limiar do OMR … um aparelho e uma impressora` (`6`):** é medida de impressão, e não de letra; segue na sessão de papel.
