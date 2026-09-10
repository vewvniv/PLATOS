## Why

O aparelho escaneia contra um pacote que ele nunca puxou nem conferiu. `ScanActivity` abre
`assets.open("prova-referencia.package.json")`, e o próprio código diz que isso é provisório até a
fatia 4. Enquanto ele existir, o portão binário de ADR-0009 — pacote presente ou ausente, sem meio
termo — não tem o que impedir: qualquer aparelho corrige qualquer folha, porque o pacote veio junto
com o APK.

Esta é a fatia que §10 nomeia: **pull de referência imutável**, com o gate de pré-voo antes de a
sessão abrir. ADR-0013 já decidiu o transporte, o cache e a verificação; a 4a-zero fechou a
autenticação, que era o pré-requisito. O que falta é o caminho: da rota até a folha.

## What Changes

### No servidor

- **Rota de listagem de provas** da organização ativa (`short_id`, título, hash do pacote). Ela
  existe porque o gate roda **antes** da sessão (§10), e o aparelho precisa dizer qual prova vai
  escanear. A escolha vem da API, e não do disco nem do teclado — é o mesmo princípio que a 4a-zero
  fixou para o nome da organização.
- **Rota de pacote**, devolvendo os **bytes exatos** gravados em `exam_package.content` e o
  `content_hash` em cabeçalho (ADR-0013, decisão 2). Sem envelope, sem reserialização: o hash é
  sobre estes bytes, e mais uma etapa de codificação é exatamente o que ADR-0008 existe para
  impedir.
- **As duas rotas só enxergam a organização do chamador.** Pacote de organização alheia responde
  como inexistente, e não como proibido — a distinção vazaria a existência da prova.

### No aparelho

- **Puxa o pacote da prova escolhida** e o **cacheia em arquivo endereçado por conteúdo**,
  `packages/<organization_id>/<content_hash>.json`, com escrita atômica por temporário e rename
  (ADR-0013, decisão 3). O diretório é escopado por organização porque o cache é um caminho de
  leitura que não passa pela rota, e num aparelho compartilhado ele entregaria o que a rota
  recusaria.
- **Ler do disco re-verifica.** O hash é recalculado sobre os bytes lidos, nunca sobre o nome do
  arquivo; divergência descarta e recai no pull. Sem isso a conferência vale uma vez, na primeira
  gravação, e todo uso seguinte é de um arquivo que ninguém mais olhou.
- **Verificação em três camadas** (ADR-0013, decisão 4), e nenhuma delas é autenticidade:
  **(a)** `sha256(bytes) == content_hash`; **(b)** `toCanonicalJson(parse(bytes)) == bytes`, que
  pega desalinhamento de versão — campo que um APK antigo descarta, default que ele injeta; **(c)**
  o pacote afirma ser desta prova e desta variante, conferido contra o payload do QR, e não contra
  o que o pedido pediu.
- **Gate de pré-voo binário** antes de abrir a sessão: pacote conferido, ou recusa com motivo. O
  gate impõe `min_renderer_version` no caminho de captura — hoje ele só é declarado, e `ScanActivity`
  não o consulta.
- **`assets.open` deixa de existir** (ADR-0013, decisão 5). Sem rede e sem cache, o aparelho
  **recusa a escanear com motivo explícito**. Nunca degrada.
- **Sair apaga o diretório de cache da organização**, além da credencial e da escolha que a 4a-zero
  já apaga. O aparelho é compartilhado, e a herança entre usuários fica escrita nos dois lados de
  propósito.

**O que esta fatia NÃO faz**, e onde cada coisa vai:

- **Não implementa modo degradado.** §10 manda capturar imagem bruta quando o pacote falta offline;
  isso exige armazenamento durável de captura, que é 4b. Aqui a ausência é recusa explicada, e a
  recusa é o comportamento que ADR-0013 decisão 5 fixa. Fica registrado que §10 só é cumprida por
  inteiro na 4b.
- **Não introduz Room nem WorkManager.** O que a 4a guarda é blob imutável endereçado por
  identificador, que é trabalho de sistema de arquivos (ADR-0013, decisão 3; regra 8).
- **Não implementa outbox nem push.** É 4c.
- **Não persiste nota.** A nota continua sendo apresentada e esquecida, como na 3c.
- **Não toca no código de `packages/domain`.** `toCanonicalJson()` e `contentHash()` já moram em
  `commonMain` e já são a implementação única dos dois lados; a fatia os consome. **Correção de
  2026-09-04:** a versão original desta linha dizia "não toca em `packages/domain`", sem
  qualificação, e isso ficou errado ao gerar a segunda prova. `commonMain` e `jvmMain` seguem
  intocados — zero linhas —, mas o gravador de fixtures (`jvmTest/GoldenWriterTest`, atrás da flag
  `platos.golden.write`) ganhou um teste e o `build.gradle.kts` ganhou uma propriedade de sistema e
  uma fixture na lista embutida. É o caminho que a decisão 8 do `design.md` escolheu de propósito —
  o mesmo por onde `prova-referencia.package.json` já sai —, e não haveria como gerar o artefato sem
  ele. Nenhum comportamento de produção do domínio muda.
- **Não toca no aplicativo web** (`apps/web/src`) nem no pipeline de captura (`vision/`, `omr/`).
  **Correção de 2026-09-04:** a versão original dizia "não toca em `apps/web`", sem qualificação, e
  isso ficou errado ao preparar a impressão da segunda folha. `apps/web/src` segue intocado — zero
  linhas —, mas `apps/web/scripts/examPackage.ts` ganhou **uma** variável de ambiente,
  `PLATOS_PACKAGE`, com o caminho de sempre como padrão. Sem ela o pacote a desenhar estava fixo em
  `prova-referencia.package.json` e a folha adversarial da tarefa 8.4 **não seria imprimível**.
  Nenhum comando existente muda de comportamento: a fidelidade e a tinta foram rodadas depois da
  mudança e saíram no mesmo lugar (`prova prova-referencia-slice-1`, fidelidade OK, tinta OK), e a
  paridade fechou contra um `android.pdf` gerado no `platos-atd34` na mesma sessão.
- **Não muda a publicação.** `ExamPublication` grava como já grava.

## Capabilities

### New Capabilities

Nenhuma. Toda a fatia é comportamento novo em contratos que já existem.

### Modified Capabilities

- `exam-package`: o pacote publicado ganha **entrega**. Hoje a spec cobre como ele nasce, o que ele
  não carrega e como o PDF deriva dele; passa a cobrir que ele é entregue por rota autenticada,
  byte a byte, com o hash em cabeçalho, e só à organização do chamador. Ganha também a listagem que
  torna a prova escolhível.
- `device-session`: hoje a sessão do aparelho é quem entrou e qual organização está ativa. Passa a
  incluir o que este aparelho guarda **por** organização — o cache de pacotes, o que o pull faz, o
  que a leitura do disco confere, e o que sair apaga.
- `scan-session`: o pacote deixa de ser um dado que a sessão recebe pronto e passa a ter
  proveniência. A sessão só abre atrás do gate, e a ausência de pacote conferido é um estado
  explicado na tela — não uma sessão que abre e não lê nada.

**Três specs, e a regra 3 do `CLAUDE.md` pede a reavaliação.** Ela foi feita, e o corte não existe:
qualquer divisão deixa o `assets.open` vivo por mais uma fatia, e ADR-0013 decisão 5 registra que
"por enquanto" é como esse asset vira fallback permanente. Um recorte só-servidor teria critério de
aceite que nenhum consumidor exercita, que é a camada horizontal com nome de fatia vertical que a
regra 3 existe para impedir. O escopo é grande porque o caminho é o menor caminho completo.

**A 4a-zero anunciou que esta fatia tocaria `identity`, e ela não toca.** O anúncio previa que a
autorização da rota de pacote fosse requisito de fronteira; ao escrever, ela é requisito de
**entrega do artefato**, e mora com o artefato. `identity` já impõe isolamento por organização no
armazenamento, e esta fatia é consumidora disso, não autora. Fica escrito porque um anúncio que
some sem explicação é indistinguível de um esquecimento.

## Impact

- **`apps/api`**: rotas novas em `http/Routes.kt`; consulta de provas e de pacote por organização.
  Nenhuma migração de banco — `exam` e `exam_package` já têm o que as rotas leem.
- **`apps/android`**: cache em arquivo, o gate, a tela de escolha de prova, e a `ScanActivity`
  deixando de carregar asset. `ApiPlatos` ganha dois destinos; o pacote é lido como **bytes**, e não
  como corpo desserializado — `Retorno.Respondeu<T>` desserializa, e o que a camada (a) hasheia
  precisa ser o byte que chegou.
- **`apps/android/build.gradle.kts`**: a tarefa que embute `prova-referencia.package.json` nos
  assets do aplicativo sai junto com o `assets.open`. O `androidTest` continua usando fixtures.
- **`fixtures/`**: a fatia precisa de **uma segunda prova publicável e impressa** para fechar a
  6.4b, herdada da 3c. ADR-0013 marcou a 6.4b como candidata a corte; ela **fica**, porque é esta
  fatia que cria o oráculo que faltava — duas provas puxáveis — e não há outra fatia natural antes
  do MVP.
- **ADR-0013** é a fonte desta fatia; nenhuma de suas decisões é alterada aqui.
