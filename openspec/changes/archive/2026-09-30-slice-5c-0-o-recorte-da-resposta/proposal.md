## Why

A região discursiva hoje é **reconhecida e mais nada** (`capture-omr`, requisito "A região discursiva é
reconhecida, e não medida"): o pipeline acha os dois ArUcos, retifica, lê o QR e para. A imagem da
resposta — o que o professor vai corrigir à mão na `slice-5c-1` e o que a fatia 8 mandará à IA — não
existe em lugar nenhum, e três coisas que o registro promete sobre ela também não:

- **O recorte.** O ADR-0018 desenhou a região com dois ArUcos e o QR ancorando o terceiro canto, e
  deixou o segundo ajuste da homografia (passo (c)) para "a mudança que implementa". A 5b-1 disse que
  era da 5b-2; a 5b-2 virou a nota parcial; a 5b-3 e a 5b-4 disseram "5c". O canto inferior esquerdo
  continua extrapolado e sem conferência, e o registro o chama de "conhecido, não mitigado".
- **O desvio.** §8 e D45 dizem que resposta que extrapola a moldura é marcada para conferência manual
  "em vez de corrigir errado em silêncio". Nada mede isso.
- **A garantia de que o recorte não contém cabeçalho.** Linha do §16 (tabela "Aberto") com
  fatia-limite **5**: "a moldura já exclui o enunciado por geometria (§8), mas nada afirma isso; o
  nome do aluno é impresso na folha". O nome impresso é da fatia 7 ("dados impressos"), e só é barato
  garantir antes de haver folha com nome.

A ordem é a do risco: o recorte é a geometria mais frágil da 5, e tudo o que vem depois — a tela de
correção, a imagem para IA — se apoia nele. Fechá-lo antes, contra o documento renderizado, é o que
impede a 5c-1 de descobrir um recorte torto com a tela já construída.

## What Changes

- **O segundo ajuste do ADR-0018**, passo (c): os 8 cantos dos dois ArUcos e os 3 cantos de
  ancoragem do QR (os do símbolo, lidos pelo decodificador na ROI já retificada) entram num ajuste
  sobredeterminado — 22 equações para 8 incógnitas —, e o recorte sai dele. A ordem do §8 não muda:
  primeira homografia, QR na ROI retificada, e só então o segundo ajuste.
- **A conferência da geometria da região discursiva** passa a ser o **resíduo** desse ajuste, com
  teto declarado antes da primeira execução (ADR-0007). Hoje a região de dois marcadores não tem teto
  nenhum (`RegionDetector`: "nenhum teto de erro nesse caminho"). Recusa por resíduo acima do teto,
  com o motivo.
- **O recorte da área de resposta**: a `answer_area` que o `LayoutMap` já declara, retificada por esse
  ajuste, em cinza, a 10 px/mm, como imagem própria. Chamada própria (`recortar`), fora do
  `analyze` por quadro.
- **O desvio**: uma faixa de 3 mm **fora** da área de resposta é medida, com a tinta impressa
  conhecida (marcadores, QR, moldura) subtraída pelo que o próprio `LayoutMap` declara. O recorte
  diz se a proporção de tinta do aluno fora da área passa do limiar. O recorte entregue **não** leva
  pixel da faixa: recorte limpo é a razão de §8.
- **A garantia de "sem cabeçalho", em duas camadas.** (1) A validação do `LayoutMap` sem renderizar
  passa a recusar qualquer primitiva de texto, imagem, QR ou marcador dentro da área de resposta de
  uma região discursiva — pega, no momento de publicar, o layout que puser enunciado ou nome ali.
  (2) No aparelho, um teste sobre a folha renderizada afirma que a tinta do recorte é só a que o mapa
  declara dentro da área (moldura e pauta), com um cabeçalho de nome impresso na mesma página.

**Não muda nesta mudança:**
- **Nenhuma tela, nenhum armazenamento, nenhuma rede.** O recorte vive na memória de quem o pede. Onde
  a imagem mora e por quanto tempo (ADR-0012, classe de retenção) é decisão da `slice-5c-1`, que é
  quem passa a ter consumidor; nada durável nasce aqui, e por isso I5 não é tocado.
- **Nenhum chamador de produção.** `ScanSession` e `ScanActivity` não chamam o recorte. O custo
  aceito está em `design.md` (decisão 1) e em Riscos: código testado e sem chamador até a 5c-1.
- **O gabarito.** Continua com quatro ArUcos, homografia pelos centros e teto de 6 px (ADR-0018,
  decisão 5). Nenhuma medição de OMR muda.
- **`RegionDetector.detect` para o reconhecimento.** O caminho de hoje continua servindo o
  `analyze`; o segundo ajuste é um passo a mais, chamado só pelo recorte.
- **Papel.** Nada aqui foi fotografado impresso; a sessão única de papel antes da fatia 6 continua
  sendo o lugar dessa medição (§16).
- **A correção manual (`5c-1`), o fechamento de caderno incompleto (`5c-2`) e o corpus.**

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `capture-omr`: o requisito "A região discursiva é reconhecida, e não medida" ganha o recorte e o
  desvio como saídas da região discursiva; entram os requisitos do segundo ajuste e da conferência
  por resíduo, do recorte da área de resposta e do desvio por faixa.
- `layout-engine`: o requisito "A região discursiva declara a questão e a área de resposta" passa a
  recusar, na validação sem renderizar, primitiva de texto, imagem, QR ou marcador dentro da área de
  resposta.

## Impact

- **KMP domain (`packages/domain`):** `LayoutMapValidation` ganha a recusa da primitiva estranha
  dentro da área de resposta, com teste nos três alvos. Nenhum campo do `LayoutMap` muda; nenhum
  golden é regravado (o mapa de hoje já satisfaz a regra — a tarefa 1.2 prova isso e prova que a
  regra reage).
- **Android:** `RegionDetector` (segundo ajuste e resíduo), um tipo novo de saída do recorte e o
  medidor do desvio, em `vision/` e `omr/`. Sem dependência nova: `zxingcpp` 2.3.0 já expõe
  `Result.position` (conferido por `javap`), e `Calib3d.findHomography` já é o que a região de dois
  marcadores usa.
- **API, web, banco, outbox:** nada. Sem migration.
- **Dívida (§16):**
  - **Paga aqui:** a linha "Garantia executável de que o recorte discursivo não contém cabeçalho"
    (fatia-limite 5, tabela "Aberto").
  - **Alcançada e não paga nesta mudança:** "Injeção de prompt manuscrita pelo aluno, no eval set"
    (fatia-limite 5). Não há IA na fatia 5; o eval set nasce com o prompt, na fatia 8. Reagendada
    para `8` com o motivo, no archive da **última** mudança da fatia 5 (P27), e a 5c-0 não a fecha
    por omissão: a tarefa 5.3 escreve isso.
  - **Atualizada:** "A região discursiva ainda não passou pelo aparelho nem pelo papel": o recorte
    e o resíduo entram como o que o papel ainda precisa medir.
  - **Aberta:** "O limiar do desvio e o teto do resíduo foram fixados sem letra de aluno" (`6`, a
    sessão única de papel). Fatia-limite escolhida porque é onde a primeira letra real é fotografada.
  - **Fora desta mudança, registrado por ser achado:** `tools/divida/divida.mjs` lê só a tabela
    "Ponto de não-retorno" do §16; a tabela "Aberto" logo abaixo, onde essas duas linhas moram, não é
    lida por ele. `node tools/divida/divida.mjs` lido em 2026-09-30 diz "vence nesta fatia (5b):
    nenhuma" e não vê as duas de fatia-limite 5, que só venceriam, sem que nada acusasse, na abertura
    da fatia 6. Estender a guarda é outra mudança (P19). O que esta faz é **abrir a linha** dessa
    lacuna na tabela que a guarda lê (fatia-limite `5`, dono mantenedor), para que o buraco deixe de
    morar em prosa (tarefa 5.3).
