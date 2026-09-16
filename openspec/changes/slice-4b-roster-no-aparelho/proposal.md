## Why

A `slice-4b-roster-entrega` (arquivada em 2026-09-16) abriu a rota que entrega o roster, e disse na
própria proposta que **nada no aparelho** mudava: cache, gate e nome na tela eram a fatia **β**. Esta
é a β.

Hoje o aparelho não puxa o roster, e por isso a folha lida continua identificando o aluno por **token
opaco**: `scan-session` apresenta nota, máximo e pendências, e nenhum nome. ADR-0002 tirou nome,
turma e matrícula do pacote imutável justamente para que eles viessem por um roster mutável entregue
ao lado — a metade que faltava é a que consome essa entrega.

**Por que agora, e não depois:** o §16 registra "O roster cacheado sem regra de apagamento" com
fatia-limite **nesta fatia** — é ela que cria a cópia local, e a linha diz que a leitura restritiva
vale como requisito enquanto o parecer jurídico não fixa teto. Pôr o roster no aparelho sem a regra
de apagamento é exatamente o que aquele registro existe para impedir.

## What Changes

- **O aparelho passa a guardar o roster puxado**, escopado por organização e por prova, no mesmo
  regime que o pacote guardado já segue (`device-session`): gravação atômica, roster de outra
  organização inalcançável, e uso sem rede depois do primeiro pull.
- **Sair passa a apagar o roster**, junto da sessão, da organização escolhida, da visão e dos
  pacotes. É a lista nomeada do requisito "Sair apaga a sessão e a organização escolhida" ganhando um
  quinto item — nomeado, e não coberto por "limpar dados locais", pela mesma razão que os outros
  quatro são nomeados.
- **O gate de pré-voo passa a decidir sobre dois artefatos.** Roster puxado, com alunos ou vazio: a
  sessão abre. Roster **nunca puxado**: ela não abre, com frase própria — o quinto motivo distinto,
  ao lado de ausência de rede, pacote ausente, conferência falha e versão insuficiente.
  **Roster vazio e roster ausente não são o mesmo estado:** prova publicada sem alunos é caso
  legítimo em `exam-package`, e barrá-la tornaria inescaneável uma prova que a publicação declara
  válida.
- **O resultado mostra o nome do aluno** em vez do token, quando o roster tem a linha daquele token.
- **O nome vindo de roster cacheado é marcado como cacheado.** O requisito da marca existe em
  `device-session`, mas hoje alcança só "dado vindo da última visão conhecida" — e a visão declara
  que "roster e identidade de aluno seguem fora daqui". Esta fatia **estende o alcance** daquele
  requisito ao roster, em vez de criar marca própria em `scan-session`: uma regra, um lugar.
- **A revogação de vínculo passa a apagar o roster**, junto da visão e dos pacotes. Hoje o requisito
  da visão manda apagar os dois quando o servidor responde que a organização não é mais do usuário;
  sem o roster nessa lista, nome de aluno sobreviveria no aparelho de quem perdeu o acesso sem
  precisar sair.

**O que NÃO muda:**

- **A rota e a consulta do servidor.** A α as entregou e esta fatia só as consome; nenhum campo novo
  desce. Turma, matrícula e referência externa continuam sem sair do servidor.
- **Nenhum hash, ETag ou TTL de roster.** ADR-0002 recusou o hash, e o `design.md` da α registrou que
  o custo — o aparelho não sabe se o roster mudou sem pedir de novo — é aceito. Esta fatia **não**
  traz a medição que reabriria isso; se ela aparecer, o veículo é mudança própria.
- **`exam-package`, `capture-omr`, `layout-engine`, `print`, `scoring`, `identity` e `billing`.**
- **`apps/web`, `apps/api`, `packages/domain`, `vision/` e `omr/`.** Nenhuma migração: esta fatia não
  toca banco.
- **A classe H do §10.8 da política.** A outra linha de ponto de não-retorno do §16 — a que diz que a
  classe H não enumera o roster baixado — tem **jurídico externo** como dono e fatia-limite no
  **piloto com turma real em modo `nominal`**, não aqui. O que a mantém lá é o padrão `coded` de
  ADR-0012: nele o roster no aparelho é código ou apelido, não nome civil.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `device-session`: ganha o **roster guardado** (escopo, atomicidade, uso sem rede); o apagamento ao
  **sair** passa a nomeá-lo; o apagamento por **revogação de vínculo** também, porque perder o acesso
  não depende de o usuário sair; o **gate de pré-voo** passa a decidir sobre dois artefatos, com a
  distinção entre roster vazio e roster ausente; e a **marca de dado cacheado** passa a alcançar o
  roster, não só a última visão conhecida.
- `scan-session`: o resultado passa a apresentar o **nome do aluno** da folha lida, com o token como
  recurso quando a linha não está no roster.

## Impact

**Código**: `apps/android` — o guardado em arquivo que já serve pacote (`PacotesEmArquivo`) e visão
(`VisoesEmArquivo`) ganha o roster; o gate de pré-voo em `PreparoDaProva`/`EstadoDaProva`; a tela de
resultado. `ClienteApi` ganha a chamada da rota que a α criou.

**Dados**: nenhuma migração de servidor. No aparelho, um arquivo por organização e prova, no mesmo
regime atômico da visão guardada — **e não Room**: ADR-0013 diz que "Room fica para a 4b, onde o
outbox é de fato relacional", e o gatilho registrado é o **outbox**, não o roster. Um registro por
prova, lido e substituído por inteiro, é trabalho de sistema de arquivos (regra 8).

**Retenção e LGPD**: esta é a fatia que **cria a cópia de dado pessoal fora do servidor**. Ela fecha
a linha "O roster cacheado sem regra de apagamento" do §16, com a leitura restritiva que o próprio
registro fixa: apagado ao sair e na desinstalação, no caminho de `DeviceSession.sair` (ADR-0013), e
nada além do teto que o parecer fixar é assumido. Aquela linha do §16 diz que o **nome da mudança
entra nela quando `/opsx:propose` criar a mudança** — esta proposta é esse momento, e a atualização
do registro é tarefa desta fatia.

**Compatibilidade**: o gate fica **mais restritivo** — uma prova cujo pacote já está guardado, mas
cujo roster nunca foi puxado, deixa de abrir até um pull com rede. É consequência aceita da decisão
do gate, e o `design.md` registra o custo e a alternativa descartada.
