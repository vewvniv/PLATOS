## 1. Base de comparação

- [x] 1.1 Registrar os números atuais antes de tocar em qualquer coisa: hash e tamanho do golden, paridade, fidelidade nos dois PDFs e contagem da suíte por alvo. Resultado: valores anotados aqui — a fatia altera o golden de propósito, então a base precisa existir antes.
  Medido em 2026-08-20, com `./gradlew build :packages:domain:testAndroidHostTest --rerun-tasks`.

  | Grandeza | Valor |
  |---|---|
  | `fixtures/prova-referencia.layout.json` | sha256 `7cbf40c9c785b8f653b7b15be241c1144be54913259c93e0e388c4bba1939a9e`, 69 864 bytes |
  | Paridade web × Android | 185 de 185 elementos, 4 páginas, maior divergência **0,042 mm** em `qq37-f` |
  | Fidelidade do PDF web | 116 verificações, maior desvio **0,039 mm** |

  | Alvo | Testes | Falhas |
  |---|---|---|
  | `packages/domain` JVM | 165 | 0 |
  | `packages/domain` Node/JS | 161 | 0 |
  | `packages/domain` Android (host) | 161 | 0 |
  | `apps/api` | 69 | 0 |

  Nenhuma falha pré-existente. Docker disponível, então os testes de `apps/api` rodam contra
  Postgres real desde o começo desta fatia — diferente da 1.5, onde a guarda de RLS teve de
  esperar o CI.

## 2. Contrato: o pacote e o hash

- [x] 2.1 Definir `ExamPackage` no domínio KMP com `meta`, `items`, `variants`, `assignments`, `layout`, `answer_key` e `scoring` (D-2a.1, §5). Resultado: tipo serializável, sem dependência de banco ou rede.

  `ExamPackage` com os sete blocos de §5, mais `buildPackage()` como função pura — não lê banco, não lê relógio, não sorteia. É o que permite publicar duas vezes e obter o mesmo hash, e o que vai permitir ao dispositivo remontar e conferir na fatia 4.

  Três decisões que a fatia congela:

  - `PackageAssignment` traz **só** `student_token` e `variant_id`. É onde I5 é decidida, e não há campo de nome para alguém esquecer de excluir do hash.
  - `prompt_version` e `model_id` existem e ficam nulos — I3 no contrato desde o primeiro pacote, para a fatia 6 preencher sem retrofit.
  - `skills` é obrigatória na **publicação**, e não na entrada do Layout Engine. O engine não sabe o que é habilidade e não deve saber; quem precisa da barreira é o artefato de onde sai o fato que M3 vai agregar.

  **Uma expansão de contrato que o mapeamento da BNCC obrigou.** O documento marca três questões — vetor, determinante, matriz — com cobertura *ausente*: não há habilidade BNCC para o objeto, e o código é âncora curricular. Guardar só o código apagaria a diferença, e o boletim de M3 somaria "a turma domina EM13MAT301" a partir de uma questão de matriz que não é sobre sistemas lineares. Por isso `ItemSkill` carrega `code` **e** `coverage`.
  - `assignments[]` traz `student_token` e `variant_id`, e **nada mais** — é onde I5 é decidida.
- [x] 2.2 Calcular o `content_hash` sobre a serialização canônica que o `LayoutMap` já usa (D-2a.4). Resultado: cobre "Republicar a mesma prova dá o mesmo hash" e "Mudança no conteúdo muda o hash".

  SHA-256 em Kotlin puro (D-2a.7), sobre a serialização canônica que o `LayoutMap` já usa. Sem dependência nova: `MessageDigest` não existe em Kotlin/JS, e `QrEncoder` e `FontProgram` são precedente pelo mesmo motivo.

  **O hash da fixture é afirmado como literal em `commonTest`:** `855fbc21…`. Os três alvos o calculam igual ou ficam vermelhos. Não é redundante com o golden — o golden cobre a geometria, este cobre o artefato publicado inteiro, incluindo itens, habilidades e gabarito.

  `Sha256Test` confere contra os vetores do FIPS 180-4 e contra o `crypto` do Node. Pegou um erro **meu** na primeira execução: dos três valores de borda que escrevi de memória, um estava errado, e o vermelho apontou o valor esperado, não o código.

  Cobre "Republicar a mesma prova dá o mesmo hash" e "Mudança no conteúdo muda o hash" — este último com dois ângulos, enunciado e **gabarito**: sem o segundo, a correção poderia ser adulterada sem deixar rastro no hash.
  - Afirmar o hash nos **três alvos**: se ele divergisse entre JVM, Node e Android, o pacote deixaria de ser verificável no dispositivo que o consome.
- [x] 2.3 Declarar o perfil tipográfico no cabeçalho do layout (ADR-0004). Resultado: cobre "Perfil declarado no pacote" e "Perfis diferentes são distinguíveis".

  O cabeçalho passa a trazer `profile`, com o identificador **e** os valores que o definem:

  ```json
  {"id":"a4-2col-9v5pt","body_size":3351,"line_height":4691,"grid":3000}
  ```

  Os dois, e não um. Só o identificador obrigaria quem lê a ter a tabela de perfis da época; só os valores não diriam qual perfil era. E há um caso que sozinho fecha o argumento, com teste próprio: dois perfis podem ter o **mesmo identificador** e grades diferentes — os valores denunciam, o identificador não.

  Enquanto houve um perfil só isso era invisível. A partir do momento em que existe pacote publicado e hasheado, um mapa antigo deixaria de ser reconstituível: não haveria como saber sob que corpo ele foi calculado. É o que o ADR-0004 chamou de "a parte cara não é o parâmetro, é a ausência do campo".
- [x] 2.4 Validar coerência interna antes de qualquer gravação. Resultado: cobre os três cenários de "Pacote incoerente é recusado", cada um com o caso positivo ao lado.

  Sete recusas antes de qualquer gravação: item sem habilidade, identificador repetido, posição apontando item inexistente, atribuição apontando variante inexistente, item sem gabarito, gabarito órfão, e layout declarando questões diferentes dos itens.

  Com o caso positivo ao lado — `o pacote da fixture e coerente` —, sem o qual as recusas poderiam estar recusando tudo.

  **Dois testes meus estavam errados e o vermelho mostrou por quê.** Um chamava `buildPackage`, que já valida, e a exceção escapava antes do ponto medido. O outro removia um item e disparava a recusa da **variante**, não a do layout — passaria pelo motivo errado. Agora o segundo remove o item, a posição e o gabarito juntos, de modo que só a divergência de layout reste.

  Também cobre "O pacote não carrega nome de aluno": a varredura é sobre o **JSON gravado**, e não sobre o tipo, porque um campo acrescentado por engano aparece no JSON antes de aparecer em qualquer revisão de código.
- [x] 2.5 Regravar o golden e registrar aqui o antes e o depois. Resultado: mudança auditável, com **uma causa só** — o campo de perfil, e nada mais.

  | | antes | depois |
  |---|---|---|
  | golden | `f01f0751…` | `ceaf67cc…` |
  | hash do pacote | `34469b91…` | `61c96f4c…` |

  **A causa única foi provada, e não afirmada.** Os dois goldens foram comparados removendo apenas o campo novo do mais recente: o resto do mapa é **idêntico caractere a caractere**. Nenhuma página se moveu, nenhum bloco trocou de coluna, nenhuma primitiva mudou de posição ou de texto.

  É a segunda auditoria deste tipo nesta fatia. Na 2b.3, a comparação foi de geometria — 806 elementos, para separar "mudou o que está escrito" de "mudou onde está escrito". Aqui foi do mapa inteiro menos o campo acrescentado, porque a afirmação a provar era mais forte: nada além do campo mudou.

  Fidelidade do PDF regenerado: 116 verificações, 0,039 mm — o mesmo patamar da base da tarefa 1.1.
  - Nenhuma outra mudança de geometria entra nesta fatia. Se a contagem de páginas ou a atribuição bloco→página se mexer, algo está errado.

- [x] 2.6 Fechar a paridade web × Android no golden novo, sem esperar o fim da fatia. Resultado: o
  `android.pdf` volta a ser do golden vigente, e a divergência não fica em janela aberta.

  | verificação | resultado |
  |---|---|
  | golden dentro do APK de teste | `ceaf67cc…` — **o mesmo** de `fixtures/` |
  | paridade web × Android | **185 de 185**, 4 páginas, 0,042 mm em `qq37-f` (tolerância 0,3) |
  | fidelidade web · Android | 116 verificações cada; 0,039 mm e **0,022 mm** |
  | deslocamento deliberado | as duas ferramentas saíram com código 1 |

  **O golden empacotado foi conferido, e não o carimbo do arquivo.** A 2b.5 mostrou que a fidelidade
  passa com um PDF velho contra um golden novo, porque a geometria não mudou. Aqui o risco era o
  mesmo com outra roupa: um APK guardando o asset da execução anterior daria "paridade OK" sobre o
  mapa errado. Então o `prova-referencia.layout.json` foi extraído de dentro do
  `android-debug-androidTest.apk` e hasheado — bate com o de `fixtures/`. É o que autoriza dizer que
  este `android.pdf` nasceu do golden desta tarefa.

  A cadência é decisão registrada: **paridade fecha a cada regravação do golden**, e não agrupada na
  verificação final. Custa ciclos de emulador; em troca, nenhuma divergência geométrica fica viva
  entre dois goldens sem alguém ter olhado.

## 2b. Correção do gabarito da fixture

Feito antes de seguir para 2.3, porque relatar defeito não é corrigi-lo. Os dois problemas foram
levantados por mim ao fechar a 2.1 e ficaram como observação — que é exatamente o hábito que produz
a próxima verificação decorativa.

- [x] 2b.1 O gabarito deixa de ser letra escrita à mão e passa a ser **derivado**. Resultado: a
  divergência entre letra e conteúdo deixa de ser representável.

  A fixture declara `answer` — o **texto** da alternativa correta — e `why`, como se chega nele. A
  letra é computada de `options.indexOf(answer)` na publicação. Antes eram 40 letras cujas
  derivações viviam num arquivo temporário fora do repositório: se alguém perguntasse por que `q37`
  era C, o repositório não respondia. Gabarito sem procedência é chute, e esta base não aceita isso
  nem para medir 0,04 mm.

  A publicação recusa resposta que não esteja entre as alternativas, ou que apareça duas vezes —
  esta última porque alternativa repetida torna a bolha certa ambígua.

- [x] 2b.2 Rebalancear a posição da alternativa correta. Resultado: as quatro bolhas passam a ser
  exercitáveis pelo OMR.

  A distribuição era **A=1, B=18, C=21, D=0**. Nenhuma resposta certa caía na última alternativa,
  então a fatia 3 poderia estar lendo a coluna errada sem uma única questão acusar.

  A regra é determinística: a alternativa correta da questão `i` fica na posição `i mod 4`.
  Rotacionar preserva o conjunto de alternativas e qual delas é a correta — muda só a posição, que é
  arbitrária numa fixture e decisiva para a captura. Resultado: **10, 10, 10 e 10**.

  Um teste afirma a regra, e não só a distribuição: distribuição igual poderia vir de sorteio, e
  sorteio faria a fixture mudar a cada execução, com o golden junto.

- [x] 2b.3 Regravar o golden e **provar** que só o texto mudou. Resultado: mudança com uma causa e
  com escopo verificado.

  | | antes | depois |
  |---|---|---|
  | golden | `7cbf40c9…` | `f01f0751…` |
  | hash do pacote | `855fbc21…` | `34469b91…` |
  | elementos de geometria | 806 | **806, idênticos byte a byte** |

  A geometria foi extraída dos dois goldens — identificador e posição de cada primitiva, sem o
  texto — e comparada: **nenhuma diferença**. A rotação moveu letras, não posições. Isso não foi
  presumido a partir de "a altura do bloco soma alternativas medidas em separado"; foi medido.

- [x] 2b.4 Ver as guardas novas falharem. Resultado: cada uma reage ao defeito que afirma cobrir.

  | defeito introduzido | quem acusa |
  |---|---|
  | rotação de uma questão desfeita | regra determinística, distribuição, e a letra do pacote |
  | justificativa removida | "toda questão declara resposta e justificativa" |
  | resposta fora das alternativas | "aparece exatamente uma vez" e a recusa de publicação |

  Três alvos verdes depois de restaurar: JVM 191, Node 187, Android host 187.

- [x] 2b.5 Reverificar tudo com o layout corrigido, antes de seguir. Resultado: base limpa e medida,
  e não presumida.

  | verificação | resultado |
  |---|---|
  | `./gradlew build` + alvo Android | verde |
  | `packages/domain` JVM · Node · Android host | 191 · 187 · 187, zero falhas |
  | `apps/api` (Postgres real) | 69, zero falhas |
  | `apps/web` · `tools/math` | 12 · 17, zero falhas |
  | fidelidade web · Android | 116 verificações cada; 0,039 mm e 0,022 mm |
  | paridade web × Android | **185 de 185, 0,042 mm** em `qq37-f` |
  | deslocamento deliberado | acusado nos quatro elementos |

  **Uma coisa apareceu ao verificar, e vale mais que o resultado.** A fidelidade passou com o PDF
  **desatualizado** contra o golden novo. Não é contradição: ela mede posição de marcador, bolha e
  fórmula, e a geometria não mudou — só o texto das alternativas. Ela não olha conteúdo de texto.

  Ou seja: **nem a fidelidade nem a paridade notariam que as alternativas trocaram de letra.** Quem
  pega isso é o golden, e só ele. Contentar-se com "fidelidade OK" teria declarado verificado um
  artefato velho — o falso verde de sempre, com roupa nova. Não é defeito das ferramentas, é o
  escopo delas; fica dito porque a intuição natural é achar que "as três passaram" cobre tudo.

## 3. Persistência

- [x] 3.1 Migration com `exam`, `exam_package` e `exam_roster`, chaveadas por `organization_id`, com RLS habilitada e forçada. Resultado: as três nascem cobertas pela guarda derivada do catálogo.

  `0007_exam_tables.sql`. Três decisões que a migration congela:

  - **`content` é `text`, e não `jsonb`.** `jsonb` reordena chaves e descarta espaçamento; o hash é sobre a serialização canônica (D-2a.4). Guardado como `jsonb`, o pacote voltaria com outros bytes e o dispositivo da fatia 4 recalcularia um hash diferente do declarado — falha que aparece longe daqui, no único lugar onde o pacote é verificado. A sintaxe continua validada, por `check (content::json is not null)`, que valida sem normalizar o que fica gravado.
  - **Um pacote por prova** (`unique (exam_id)`). O QR identifica a prova por `short_id` e nada mais (§8): com dois pacotes, a captura da fatia 3 não teria como saber de qual geometria saiu a folha em cima da mesa. Corrigir prova publicada é publicar prova nova, com `short_id` próprio — que é o que a folha já distribuída diz que ela é.
  - **Chave estrangeira composta** `(exam_id, organization_id) → exam (id, organization_id)`. Sem ela, um membro de A poderia gravar roster com o **seu** `organization_id` apontando prova de B: a política de RLS aprovaria, porque o `organization_id` da linha está correto.

  **Visto falhar.** Comentando `enable row level security` de `exam_package`, a guarda do catálogo em `ConnectionRoleTest` ficou vermelha:

  ```
  tabela de dominio sem RLS forcada ==> expected: <[]> but was: <[exam_package sem RLS habilitada]>
  ```

- [x] 3.2 Impor a imutabilidade de `exam_package` no próprio banco (D-2a.3). Resultado: cobre "Pacote publicado não pode ser alterado".

  Duas camadas, porque cobrem chamadores diferentes: `revoke update, delete, truncate` barra `app_backend`, e um gatilho `before update or delete` barra **também o dono da tabela e o superusuário**, que o `REVOKE` não alcança. Se a imutabilidade fosse só o privilégio, ela valeria até a primeira migration, job de manutenção ou script de suporte — que é exatamente quando um pacote já distribuído seria alterado.

  `ExamPackageImmutabilityTest` tenta as quatro operações de verdade, contra Postgres real: `UPDATE` e `DELETE` como `app_backend` (recusa `42501`), e os mesmos como superusuário (recusa `PT001`, do gatilho). Mais o caminho que ninguém escreve: apagar a **prova** é recusado com `23503` enquanto houver pacote, porque `on delete cascade` faria o pacote sumir sem uma linha de código mencionar `exam_package`.

- [x] 3.3 Testar isolamento por organização das três tabelas novas, sem filtro na aplicação. Resultado: consulta de organização alheia devolve zero linhas.

  `ExamTenancyIsolationTest`, no molde do de identidade: nenhuma consulta leva `where` de organização. **A guarda do catálogo e este teste não se substituem** — aquela afirma que a RLS existe e está forçada, este que ela está *certa*; uma política escrita com o `organization_id` da tabela errada satisfaria a primeira e vazaria prova alheia.

- [x] 3.4 Testar que apagar o roster deixa o pacote íntegro e com o mesmo hash. Resultado: cobre "Eliminação de dado pessoal não destrói a prova" — a operacionalização de I5.

  Apagados os dois alunos, o pacote continua lá, com o conteúdo byte a byte igual, sem o nome de ninguém dentro. O hash é **recalculado** do conteúdo gravado com o `MessageDigest` da JVM — oracle independente do `Sha256` do domínio — e comparado com a coluna: ler a coluna de volta e compará-la consigo mesma passaria mesmo com o conteúdo alterado.

  **Visto falhar, e a primeira tentativa não valia.** Trocar a coluna para `jsonb` derruba os sete testes com erro de tipo no `INSERT` — barulho, e não o defeito que a coluna existe para evitar. A versão silenciosa é um gatilho que normaliza via `jsonb` mantendo a coluna `text`: nada quebra ao gravar, só os bytes mudam. Aí exatamente os três testes que comparam conteúdo ficaram vermelhos, e os quatro de imutabilidade seguiram verdes:

  ```
  a serializacao canonica nao sobreviveu ao armazenamento
  ==> expected: <{"meta":{"exam_id":"prova-x"},...}>
       but was: <{"meta": {"exam_id": "prova-x"}, ...}>
  ```

  `apps/api`: **83 testes, zero falhas** — eram 69.

## 4. Publicação

- [x] 4.1 Publicar a prova fixa: montar, validar, hashear, gravar. Resultado: um pacote real no banco, a partir da fixture versionada.

  `ExamPublication` em `apps/api`. A montagem é do domínio KMP e é função pura; aqui só se grava — `apps/api` passa a depender de `packages:domain`, que é a primeira vez que servidor e renderizadores leem o mesmo contrato do mesmo lugar.

  **O hash gravado é o mesmo literal que `ExamPackageTest` afirma nos três alvos** — `61c96f4c…`, repetido de propósito do outro lado da fronteira de módulo. Se o servidor gravasse outro, o pacote deixaria de ser verificável no dispositivo que o consome, e a divergência só apareceria na fatia 4, com pacote já distribuído.

  E o teste não compara o hash com o que a publicação devolveu — isso seria comparar a origem consigo mesma. Ele **recalcula o SHA-256 dos bytes da coluna**, com o `MessageDigest` da JVM, que é o que o dispositivo vai fazer ao receber o pacote.

  Duas decisões de implementação que o teste sustenta:

  - **Monta e valida antes de abrir a transação.** "Nenhum pacote parcial é gravado" deixa de depender do rollback: quando a primeira linha é escrita, o pacote já passou por `requireCoherent()`.
  - **Sem `select` prévio por `short_id`.** Um `select` antes do `insert` deixaria janela entre checagem e gravação, e daria uma resposta mais bonita e menos verdadeira. Quem decide é a restrição do banco; o código só traduz `23505` — e **só** `23505`, porque traduzir a classe 23 inteira diria "prova já publicada" para um `short_id` fora do formato.

- [x] 4.2 Testar que o pacote gravado não traz nome, turma nem matrícula. Resultado: cobre "O pacote não carrega nome de aluno".

  Publica com dois alunos e varre o **JSON gravado**: nome, turma e matrícula ausentes, token presente, e as duas linhas no roster. A varredura é sobre o artefato, e não sobre o tipo — é no artefato que o dado seria distribuído.

  **Visto falhar.** Trocando `PackageAssignment(it.studentToken, …)` por `it.displayName` — o erro de uma letra que basta para violar I5 —, só este teste ficou vermelho:

  ```
  nome `Zoraide Buarque` dentro do pacote
  ```

- [x] 4.3 Testar que corrigir o roster não muda o hash do pacote. Resultado: cobre "Mudar o roster não invalida o pacote".

  Corrige o nome de uma aluna e afirma três coisas: a correção aconteceu de fato, o hash não mudou e o conteúdo não mudou. Sem a primeira, o teste passaria com um `update` que não pegou nenhuma linha — verde por não ter feito nada.

- [x] 4.4 Recusar republicação sobre pacote existente, com erro identificável. Resultado: corrigir prova publicada é publicar pacote novo, como D-2a.3 exige.

  `ExamAlreadyPublishedException`, com o `short_id` dentro. A segunda publicação não deixa rastro: uma prova, um pacote, e o título continua o da primeira.

  **Visto falhar, e o vermelho disse mais que a asserção.** Removendo `unique (short_id)`, a segunda publicação passou — e devolveu **o mesmo `content_hash`** da primeira, em outra prova:

  ```
  Expected an exception of class ExamAlreadyPublishedException to be thrown,
  but was completed successfully with the result:
  PublishedPackage(examId=01a02090-…, contentHash=61c96f4c…)
  ```

  Duas provas distintas com artefato idêntico e `short_id` idêntico é exatamente a ambiguidade que a captura da fatia 3 não teria como resolver: o QR carrega `short_id` e nada mais.

  **Uma verificação que passou de primeira e precisou ser desafiada.** Os seis testes deste grupo passaram na primeira execução, o que nesta base é motivo de desconfiança e não de comemoração. Três defeitos deliberados depois, cada um derrubou **exatamente** o teste que afirma cobri-lo, e nenhum outro. O terceiro é o mais instrutivo: gravar um único espaço a mais no conteúdo — JSON ainda válido, semanticamente idêntico — foi acusado só pela conferência do hash contra os bytes da coluna.

  `apps/api`: **89 testes, zero falhas** — eram 83.

## 5. Renderização a partir do pacote

- [x] 5.1 `render-fixture.ts` passa a extrair o `LayoutMap` do pacote (D-2a.5). Resultado: cobre "PDF vem do pacote" no web.

  O pacote publicado passa a ser artefato versionado: `fixtures/prova-referencia.package.json`, regravado pela mesma flag do golden (`-Dplatos.golden.write=true`). E há uma propriedade que ele ganha de graça, e que vale registrar:

  ```
  sha256(fixtures/prova-referencia.package.json) = 61c96f4c…  ← o content_hash do pacote
  ```

  O hash do **arquivo** é o `content_hash` que o banco grava e que os três alvos afirmam. Não é coincidência: o arquivo é exatamente a serialização canônica sobre a qual o hash é calculado. Conferir o artefato versionado é um `sha256sum`.

  `loadPublishedLayout()` é um caminho só, usado por `render-fixture.ts` e por `render-shifted.ts` — se o gerador do deslocamento continuasse lendo o golden, a falsificabilidade estaria provando outra coisa que não o que se imprime.

- [x] 5.2 O teste instrumentado do Android passa a fazer o mesmo. Resultado: cobre o mesmo cenário no Android.

  `layoutDoPacote()` no lugar de `golden()`, lendo `prova-referencia.package.json` dos assets versionados. O APK de teste foi aberto e o asset hasheado: `61c96f4c…`, o mesmo do repositório e o mesmo do banco.

  **Uma guarda nova, porque a fatia abriu uma fresta.** Os renderizadores desenham a partir do pacote; a paridade e a fidelidade medem contra o golden. Se os dois arquivos pudessem divergir, as ferramentas passariam a julgar uma geometria que ninguém imprime — e continuariam verdes, porque continuariam funcionando perfeitamente, só que sobre o artefato errado. `PacoteVersionadoTest`, em `commonTest`, afirma nos três alvos que o layout dentro do pacote é o golden **byte a byte**, e que o pacote versionado é o que a publicação produz hoje.

- [x] 5.3 Confirmar que nenhum renderizador ganhou lógica nova. Resultado: se algum precisou mudar de comportamento — e não só de origem do mapa —, o desenho de D-2a.1 está errado e a tarefa reprova.

  | verificação | resultado |
  |---|---|
  | `git diff` em `apps/web/src`, `apps/android/src/main`, `commonMain` | **vazio** |
  | paridade web × Android | 185 de 185, 0,042 mm em `qq37-f` |
  | fidelidade web · Android | 116 verificações cada; 0,039 mm e 0,022 mm |
  | deslocamento deliberado | as duas ferramentas com código 1 |

  Os números são **os mesmos** de antes da mudança, dígito a dígito. Só a origem do mapa mudou.

  **O oráculo óbvio não existia, e descobrir isso valeu a tarefa.** A intenção era comparar o `web.pdf` antes e depois byte a byte. Ele mudou — e mudou também entre **duas execuções seguidas com a mesma entrada**: dois renders consecutivos deram hashes diferentes, com a diferença dentro de um stream comprimido, na forma de um timestamp embutido pelo `pdf-lib`.

  Ou seja: **o hash do PDF nunca serviu, e nunca servirá, de golden.** Se eu tivesse comparado só uma vez, teria lido "o PDF mudou" como regressão de geometria e ido caçar um defeito que não existe. Quem responde "nada mudou" aqui é a rasterização — fidelidade e paridade —, e o `git diff` dos renderizadores.

## 6. Verificação

- [x] 6.1 Rodar a suíte completa nos três alvos, mais `./gradlew build`. Resultado: golden novo estável byte a byte em JVM, Node e Android.

  | alvo | testes | falhas |
  |---|---|---|
  | `packages/domain` JVM | 197 | 0 |
  | `packages/domain` Node/JS | 192 | 0 |
  | `packages/domain` Android (host) | 192 | 0 |
  | `apps/api` (Postgres real) | 89 | 0 |
  | `apps/web` · `tools/math` | 12 · 17 | 0 |

  Rodado com `--rerun-tasks`: contagem de cache não é contagem.

- [x] 6.2 Medir fidelidade dos dois PDFs derivados do pacote. Resultado: dentro de 0,05 mm, comparado com a base da tarefa 1.1.

  116 verificações em cada documento. Web **0,039 mm** (marcador 2, borda superior); Android **0,022 mm** (fórmula `qq30-f`, altura da tinta). O mesmo patamar da base da 1.1.

- [x] 6.3 Medir paridade web × Android com os documentos derivados do pacote. Resultado: dentro de 0,3 mm; cobre "Divergência entre plataformas continua barrada".

  **185 de 185 elementos**, 4 páginas, maior divergência **0,042 mm** em `qq37-f` — o piso da rasterização, contra tolerância de 0,3. Idêntico ao da base: trocar a origem do mapa não mexeu em geometria nenhuma.

- [x] 6.4 Provar que a verificação continua capaz de falhar, com o deslocamento deliberado sobre o layout **do pacote**. Resultado: as duas ferramentas saem com código 1.

  `render-shifted.ts` passou a ler do pacote pelo mesmo `loadPublishedLayout()` do renderizador — se continuasse lendo o golden, estaria provando a falsificabilidade de um caminho que ninguém imprime. As duas ferramentas saíram com código 1, acusando os quatro elementos deslocados.

  **E uma conferência quase virou falso vermelho.** Rodei o comparador com o diretório errado; ele saiu com código 1 por não achar o arquivo, e código 1 é exatamente o que se espera de uma recusa legítima. Refeito do diretório certo, a recusa veio com os quatro elementos nomeados. Código de saída sem a mensagem junto não é evidência.

- [x] 6.5 Atualizar `docs/cobertura-*.md` com os cenários novos e como cada verificação foi vista falhar. Resultado: nenhum cenário da spec sem verificação.

  `docs/cobertura-fatia-2a.md`: os 13 cenários de `exam-package`, todos cobertos, e a tabela dos seis defeitos deliberados com a mensagem que cada um produziu.

- [ ] 6.6 Imprimir a folha derivada do pacote e conferir a olho. Resultado: registrado.
  - **Duas fatias seguidas, dois defeitos de espaçamento que só o papel achou** — e o segundo explicou uma divergência de métrica que eu tratava como problema de ferramenta. Enquanto o Layout Engine estiver mudando, esta tarefa não é formalidade.

## 7. Registro

- [ ] 7.1 Registrar em `docs/adr/` o que a fatia decidiu e ainda não estava em ADR, se houver. Resultado: nenhuma decisão nova só no código.
- [ ] 7.2 Atualizar §16 e o bloco Aberto de §17: os itens cuja fatia-limite era a 2a saem da lista ou mudam de estado. Resultado: a lista de adiados encolhe quando o prazo chega, em vez de crescer.
