# Plano de correção — da auditoria até a fatia 5

**Entrada:** `docs/auditoria-2026-09-18-antes-da-fatia-5.md` · **Base:** `main` em `f643abc`
**Fundamento normativo:** `ARQUITETURA-FINAL-v3.md` (nível 1) · `CLAUDE.md` e `rigorous.md` (nível 2)

Este documento é o percurso, e não um cardápio. Cada etapa traz o veículo, o que fazer, **o que é
proibido fazer**, a mutação exata que prova a verificação, e o que precisa estar escrito para
fechar. A última etapa é a orientação que impede a lista de voltar.

---

## 0. As regras do percurso

Estas regras valem sobre todas as etapas e **não** são negociáveis dentro deste plano. Elas são
aplicação direta do `CLAUDE.md` e do `rigorous.md`; a citação está em cada uma.

1. **A ordem é a ordem.** Nenhuma etapa começa antes de a anterior estar **arquivada**. A única
   exceção é a etapa 2, que está marcada como fora da fila e diz por quê.
2. **Uma etapa = uma mudança OpenSpec = um PR = um escopo de chat** (§14 regra 4, `CLAUDE.md`
   regra 3). Etapas não se fundem "porque são pequenas". Duas etapas abertas ao mesmo tempo sobre a
   mesma capability é o que o `rigorous.md` §"Editar `openspec/specs/` direto" condição 5 proíbe.
3. **Contrato antes do consumidor, em commit separado** (`CLAUDE.md` regra 1, §14 regra 1, P25).
   Onde a etapa lista commits numerados, a numeração é a ordem dos commits.
4. **O que este plano não manda fazer, não se faz.** Achado novo no meio de uma etapa vira **item
   escrito com dono e fatia-limite** — nunca implementação silenciosa (P19). Se o item for de
   segurança, LGPD ou imutabilidade, ele entra na tabela do §16 antes de a etapa fechar (P20).
5. **Regra de parada.** Toda etapa prevê **qual conjunto de cenários deve cair** sob a mutação. Se o
   conjunto real for diferente do previsto — mais, menos, ou outros —, **pare**. Não conserte o
   instrumento, não afrouxe a asserção, não ajuste a previsão em silêncio. Escreva o conjunto real
   ao lado do previsto e diga o que ele significa (P7, P12, P14). A previsão errada da 6.6 da fatia
   do outbox é o precedente de como se faz isso.
6. **Nenhuma tolerância, janela ou limiar muda neste plano.** Se alguma medição encostar num limite,
   isso é resultado, não motivo para mexer no limite (P11, ADR-0007).
7. **Mutação não fica na árvore, e a reversão se confere rodando** (P10). Toda etapa fecha com
   `grep -rn "MUTACAO"` fora de `build/` em `0` e a suíte rodada **depois** da reversão.
8. **Fechar é o que o `rigorous.md` §8 diz que é.** Comando cheio (P5), `timestamp` do relatório
   (P2, P3), oráculo independente (P4), como foi visto falhar (P9), o que ficou sem verificação
   (P8). Nenhuma etapa fecha com "passou".
9. **Ambiente não se toca sem perguntar** (P22). As etapas 2, 3 e 5 precisam de aparelho ou
   emulador; elas dizem isso no cabeçalho. Perguntar antes vale inclusive em modo automático.
10. **As horas perigosas valem aqui** (§9). "Só falta isso" no fim de uma etapa é o gatilho para
    parar de acrescentar afirmação e começar a escrever o que ficou sem verificar.

---

## 1. Por que esta ordem, e não outra

Três restrições duras determinam tudo. Elas não são preferência.

**R1 — A quebra de hash acontece uma vez só.** Acrescentar campo ao `ExamPackage` muda o
`content_hash` de **todo** pacote, porque `toCanonicalJson()` usa `encodeDefaults = true`. Isso
obriga a regravar três fixtures, dois literais de hash e a fechar paridade e fidelidade na mesma
sessão (P23). Fazer isso duas vezes é pagar duas vezes por nada. Logo: **tudo o que entra no
artefato hasheado é decidido antes e executado junto** (etapa 3).

**R2 — O oráculo precisa estar parado antes de alguém medir contra ele.** A conferência de
`package_hash` no servidor (etapa 4) compara contra `exam_package.content_hash`. Se ela for escrita
antes da etapa 3, a etapa 3 move o valor por baixo dela e os literais dos testes passam a mentir.
Logo: **etapa 4 depois da etapa 3.**

**R3 — Uma mudança de contrato KMP por vez.** As etapas 3 e 6 mexem no mesmo módulo
(`packages/domain`), e as duas são mudanças de contrato. Sobrepô-las faria um vermelho deixar de
dizer qual das duas o causou — que é o defeito que P12 e P14 descrevem em outra escala. Logo:
**etapa 6 é a última do trabalho de código, e nunca simultânea à 3.**

Sobram duas escolhas de ordem que valem defender:

- **A etapa 1 é registro puro e vem antes de tudo**, porque as seis etapas seguintes **leem** esses
  documentos. Começar a trabalhar sobre um `deploy-api.md` que diz que o schema não existe em
  produção, ou sobre um ADR-0013 que ainda diz `proposto`, é reconstruir o contexto errado — P21.
- **Os dois defeitos reais do aparelho (etapa 5) vêm depois da etapa 3**, e não antes, embora sejam
  defeitos e a 3 seja conformidade. A razão é R1: a etapa 3 já toca o caminho de conferência de
  pacote do aparelho e regrava as fixtures que os testes dele leem. Mexer no Room e na `ScanActivity`
  **antes** faria a etapa 3 chegar num módulo recém-alterado, e um vermelho ali seria ambíguo.

| Etapa | O que fecha (achado da auditoria) | Veículo | Pré-requisito |
|---|---|---|---|
| 1 | 7, 2.3ᵣ, 4.5, 4.6ᵣ, 4.7ᵣ, 5.1, 5.2, `supabase-kt` | commits diretos + §16 | — |
| 2 | 4.3 | medição; mudança **só se** confirmar | fora da fila |
| 3 | 4.1, 5.4 | ADR-0014 + mudança `exam-package` | etapa 1 |
| 4 | 2.2 | mudança `result-sync` | etapa 3 |
| 5 | 3.2, 3.3, 4.2 | mudança `result-sync` | etapa 4 |
| 6 | 2.1 | ADR-0015 + mudança | etapa 5 |
| 7 | 3.1, 4.4, 5.3 | duas mudanças de build/CI | etapa 6 |
| 8 | a causa de 2.3, 3.1, 4.6, 4.7 e 4.4 | `rigorous.md` + guarda executável | etapa 7 |

ᵣ = fecha como **registro com fatia-limite e dono**, não como implementação. A implementação é da
fatia que a tabela nomear. Isso é o que §16 chama de ponto de não-retorno, e é o instrumento certo
para item cuja correção não pertence a este plano.

---

## 2. O veículo: nem uma fatia, nem várias — nenhuma

**A pergunta operacional é "isto cabe numa fatia extra antes da 5?", e a resposta é que a pergunta
tem a unidade errada.** Fatia e mudança OpenSpec não são a mesma coisa nesta base, e a diferença já
está no repositório: a fatia 4 foi entregue por **sete** mudanças arquivadas (`slice-4a-zero`,
`slice-4a-package-pull`, `slice-4a-cache-referencia`, `slice-4b-atribuicao-no-papel`,
`slice-4b-roster-entrega`, `slice-4b-roster-no-aparelho`, `slice-4b-outbox-de-resultado`).

### Isto não vira uma fatia, e a razão é o critério do §15

§15 define o que corta uma fatia, e o critério é **o que a entrega consegue reprovar**. A frase que
fecha o argumento está lá: *"Uma fatia cujo critério de aceite não pode falhar é uma camada
horizontal com nome de fatia vertical, e a regra 3 do `CLAUDE.md` existe para impedir isso."*

Uma "fatia de correção" não tem um risco de produto que ela valide — tem oito riscos diferentes, sem
nada em comum além da origem. Batizá-la de fatia seria dar nome de fatia vertical a uma banda
horizontal, que é exatamente o que §15 proíbe. E há um agravante concreto: **o nome "4c" já está
falado** — ADR-0013 diz "Modo degradado e outbox continuam fora: são 4b e 4c". Usá-lo para a banda de
correção enterraria a única referência registrada ao modo degradado.

**Também não são várias fatias.** Fatia é linha do roadmap do §15, com coluna "Valida". Acrescentar
oito linhas de correção ao roadmap corromperia o significado da tabela.

### O veículo já existe, e o repositório o distingue pelo nome

Das 21 mudanças arquivadas, **16 começam com `slice-` e 5 não**:
`toolchain-deprecation-audit`, `lgpd-base-legal-e-modo-sem-identificacao`, `health-diz-qual-build`,
`envio-distingue-recusa-transitoria`, `generatejooq-sem-registro-automatico`. A convenção já está
estabelecida e é visível: **mudança que não é fatia não carrega `slice-`**. E a mais próxima do que
vamos fazer — `lgpd-base-legal-e-modo-sem-identificacao` — é precisamente uma mudança de
conformidade, sem número de fatia, entre fatias.

> **A definição:** isto é uma **banda de correção** entre o archive da fatia 4 e o `/opsx:propose` da
> fatia 5. **Zero fatias. Um bloco de commits diretos + 7 mudanças OpenSpec** (8 se a etapa 2
> confirmar) **+ 2 ADRs.** §15 **não** ganha linha nova; §16 ganha três (etapa 1), que é o
> instrumento certo.

### As mudanças, com nome, spec e ordem

Nomes no estilo que o repositório já usa — descritivos, sem `slice-`. Branch em `vewvniv/<nome>`.

| # | Nome da mudança | Etapa | Specs tocadas | ADR |
|---|---|---|---|---|
| — | `registro-da-auditoria-antes-da-5` · **commits diretos, sem mudança** | 1 | nenhuma | — |
| 1 | `params-hash-no-pacote-publicado` | 3 | `exam-package` | **ADR-0014** |
| 2 | `servidor-confere-a-proveniencia-do-resultado` | 4 | `result-sync` | — |
| 3 | `o-pendente-nao-se-perde-no-aparelho` | 5 | `result-sync`, `scan-session` | — |
| 4 | `contrato-do-fio-com-dono-unico` | 6 | **nenhuma** | **ADR-0015** |
| 5 | `versao-do-renderizador-conferida` | 7.1 | nenhuma | — |
| 6 | `o-apk-de-release-e-verificado` | 7.2 | nenhuma | — |
| 7 | `registro-de-divida-executavel` | 8 | nenhuma | — |
| (8) | `transferencia-entre-aparelhos` — **só se a medição confirmar** | 2 | `device-session` | — |

**Três observações que a tabela obriga**, e todas saem da regra 3 do `CLAUDE.md` ("se tocar mais de
duas capabilities/specs, reavalie e divida"):

1. **A mudança 3 está no limite** — duas specs. Se ao implementar ela quiser uma terceira, o corte
   pré-declarado é entre **5.A** (a instância única do Room, `result-sync`) e **5.B+5.C** (a recusa
   com motivo e o texto do spec). Não invente outro corte no meio da sessão.
2. **A mudança 4 toca quatro contratos e três módulos, e mesmo assim é uma só.** Ela **não tem delta
   de spec** — nenhum comportamento muda —, exatamente como `generatejooq-sem-registro-automatico`,
   que foi arquivada sem diretório `specs/`. E é **uma** propriedade verificada quatro vezes pela
   mesma mutação (trocar um `@SerialName` e ver os dois literais caírem), não quatro propriedades.
   Dividi-la poria duas mudanças em sequência sobre o mesmo pacote KMP novo, sem ganho no que cada
   uma consegue reprovar.
3. **As mudanças 5 e 6 não se fundem.** São verificações diferentes, com mutações diferentes e
   arquivos diferentes; juntá-las faria a sessão de fechamento carregar três conferências não
   relacionadas — que é a forma que §9 do `rigorous.md` chama de horas perigosas.

### O que precisa fechar antes de a fatia 5 abrir

**Sete das oito.** E não é zelo: em cada caso a fatia 5 passa por cima do mesmo terreno.

| Mudança | Por que bloqueia a fatia 5 |
|---|---|
| Etapa 1 (registro) | As três linhas novas do §16 têm fatia-limite **5**. Abrir a 5 sem elas é abrir uma fatia que não sabe quais obrigações vencem nela |
| 1 · `params-hash` | **A fatia 5 reabre o contrato do pacote** — rubrica, `expected_lines`, região discursiva — e com ele o hash. Deixar `params_hash` para lá faria uma mudança carregar duas razões de contrato não relacionadas (P25), e o hash quebrar duas vezes (R1) |
| 2 · `proveniencia` | A 5 traz **correção manual**, que é um segundo escritor de `grading_result`. Conferir proveniência depois de haver dois escritores é retrofit |
| 3 · `o-pendente` | A 5 estende o caminho de captura do aparelho. Acrescentar escritores a uma topologia de Room que já está errada multiplica o defeito |
| 4 · `contrato-do-fio` | **É a de prazo mais curto.** A 5 é o maior acréscimo de superfície de contrato do projeto. Se ela rodar antes, nascem três ou quatro espelhos novos, e o ADR-0015 passa a legislar sobre um estado pior do que o que auditamos |
| 5 · `versao-do-renderizador` | A 5 acrescenta região discursiva ao `LayoutMap`, e é aí que `min_renderer_version` sobe pela primeira vez — o momento exato em que três registros cegos divergem |
| 7 · `registro-de-divida` | Instalar o regime **depois** da 5 significa que a 5 cria dívida sob o regime antigo. A regra existe para a próxima fatia, e a próxima é ela |

**As duas que podem correr depois**, com prazo próprio e escrito:

- **6 · `o-apk-de-release-e-verificado`** — não bloqueia a 5. Bloqueia o **lançamento**. Fica na
  tabela do §16 com essa fatia-limite.
- **8 · `transferencia-entre-aparelhos`** — a fatia-limite dela não é a 5: é **antes de qualquer
  piloto em modo `nominal`** (é dado pessoal de menor). Corre fora da fila, como a etapa 2 já diz.

### Como isso se traduz em sessões

**Uma mudança = um PR = um escopo de chat** (§14 regra 4). Então são **oito sessões de trabalho** —
o bloco de commits diretos mais sete mudanças —, e nenhuma delas começa antes de a anterior estar
arquivada (regra 0.1). Duas dessas sessões precisam de emulador e uma de aparelho real; elas estão
marcadas no cabeçalho de cada etapa, e P22 vale: **pergunte antes de mexer no ambiente.**

Se o tempo apertar, o que se corta é a mudança 6 e a 8 — nessa ordem, e **com a linha no §16
escrita antes do corte**, nunca por omissão. Nenhuma das sete restantes tem corte previsto: cada uma
delas fica mais cara dentro da fatia 5 do que antes dela.

---

## ETAPA 1 — Selar o registro

**Veículo:** commits diretos. **Não** abre mudança OpenSpec: nada em `openspec/specs/` é tocado, e
nenhum comportamento muda.
**Pré-requisito:** nenhum. **Ambiente:** nenhum.

**Por que primeiro.** Seis etapas vão ler estes arquivos. Registro falso não custa uma tarefa: custa
toda verificação futura que se apoiar nele (`rigorous.md` §4).

### O que fazer, um commit por item

**1.1 — `docs/adr/0013-...md`: `proposto` → `aceito`.**
Trocar a palavra e acrescentar, logo abaixo do cabeçalho, uma linha dizendo **quando** e **por que**
ele ficou em `proposto` enquanto cinco fatias eram construídas sobre ele. A frase antiga não se
apaga (P7): o estado errado fica dito como estado errado.

**1.2 — `ARQUITETURA-FINAL-v3.md` §16: três linhas novas na tabela de ponto de não-retorno.**
É atualização de registro com informação nova, e **não** abre ADR — o precedente é a própria §16, na
atualização de 2026-09-10: *"Atualização de registro com informação nova, não substituição de
decisão — não abre ADR."* As três linhas, com as quatro colunas que a tabela já tem:

| Risco | Fatia-limite | O que encarece depois dela | Dono |
|---|---|---|---|
| **Modo degradado (§10) não existe** | **5** | §10 promete "se offline e ausente, captura e guarda as imagens brutas"; hoje o gate **barra**. §15 o manteve na fatia 4, a fatia 4 fechou inteira e ele não entrou. Depois da 5 há discursiva no caminho, e o modo degradado deixa de ser "guardar imagem do gabarito" para ser "guardar imagem de tudo" | mantenedor |
| **Migration não é aplicada por nenhum pipeline** | **antes de a primeira migration da fatia 5 ir a produção** | Já cobrou: código novo contra schema antigo deu **HTTP 500** na conferência da 4b, com `/health` em 200 o tempo todo. A 5 acrescenta schema (rubrica, transcrição), então a próxima migration é certa | mantenedor |
| **O limiar do OMR foi apurado sobre um aparelho e uma impressora** | **5** (é a fatia do corpus) | A obrigação de reexaminar `V` e `C` foi registrada na 3b, venceu na 3c e ficou "continua aberta" sem novo prazo. §14 regra 5 pede ~30 folhas em ângulos, luz e **letras diferentes**; há 9 fotos de 3 folhas. E **2 das 9 não decodificam o QR**, sem causa medida. A correção objetiva offline **já é o produto** (§15) | mantenedor |

**1.3 — `docs/deploy-api.md`: corrigir três afirmações, sem apagar (P7).**
- O título "Antes de tudo: o schema ainda não existe no Supabase" e o parágrafo seguinte passam a
  trazer, **acima** do texto antigo, a correção datada: o schema existe em produção desde a
  conferência da fatia do outbox, com a evidência (`grading_result` com linhas reais,
  `cobertura-slice-4b-outbox-de-resultado.md` §5.1). O texto antigo fica, marcado como o estado de
  quando foi escrito.
- Linha 37: "oito migrations" → **nove**.
- Linha 91: "As oito tabelas têm RLS" → **dez**, com a observação de que o número **não** é o que
  garante — quem garante é a guarda derivada do catálogo em `ConnectionRoleTest`, e é ela que o
  leitor deve consultar.

**1.4 — `CLAUDE.md` linha 102.** `supabase-kt` sai da lista de stack do Android, com o ponteiro para
onde a decisão vive: decisão 9 do `design.md` da `slice-4a-zero-device-auth` e ADR-0013 decisão 2. É
correção de registro contra decisão já tomada **com medição** — não é mudança de stack.

**1.4b — `CLAUDE.md` ganha uma linha temporária, e ela se apaga sozinha.** Na seção "Fonte de
verdade", um ponteiro para este plano, marcado como **vigente enquanto a banda de correção estiver
aberta** e com a instrução de sair quando a última mudança for arquivada. A razão é operacional e
vale dizer: `CLAUDE.md` é o único arquivo carregado em **toda** sessão, e uma banda de oito sessões
em que qualquer uma possa começar sem o plano na mão é a forma mais provável de este documento ser
ignorado por esquecimento. Uma linha, com data de saída escrita nela.

**1.5 — Commit isolado, só texto em código (P25).**
- `ScanActivity.kt:61`: a frase "Nada e persistido: a nota e apresentada e some. Room e outbox sao
  da fatia 4b" passa a descrever o que a classe faz. A fatia 4b **é** esta.
- `supabase/migrations/20260813223821_rls_policies.sql:6-7`: o absoluto "Nenhuma política abaixo
  referencia `user_id` como chave de acesso" passa a nomear as duas exceções estruturais
  (`app_user_self_select`, `membership_self_select`) e a dizer **por que elas são necessárias** — a
  tabela de vínculo não tem como ser autorizada por si mesma. O desenho está certo; o que sai é a
  afirmação absoluta num arquivo de segurança.

### Proibido nesta etapa

- Abrir ADR para o §16. É atualização de registro, e o precedente está citado em 1.2.
- Tocar `openspec/specs/`. Nenhuma linha.
- Tocar código executável. O item 1.5 é **comentário e KDoc**, e nada mais.
- Aproveitar para "arrumar" outras frases desatualizadas que aparecerem. Achado novo → item escrito
  com dono (P19).

### Fechar

Não há mutação: nenhuma verificação nova nasce aqui. O fechamento é a leitura cruzada — cada
afirmação corrigida tem, ao lado, a fonte que a sustenta (commit, arquivo, linha). Rodar
`./gradlew build` mesmo assim, porque 1.5 toca arquivos compilados, e registrar o `timestamp`.

---

## ETAPA 2 — Medir o que hoje é suposição

**Veículo:** medição primeiro. Mudança OpenSpec **só se** a medição confirmar.
**Pré-requisito:** nenhum. **Ambiente: aparelho real — pergunte antes (P22).**
**Fora da fila:** é a única etapa que pode correr em paralelo com as outras, porque não toca nenhuma
linha que elas tocam. **Prazo: antes da etapa 8**, e antes de qualquer piloto em modo `nominal`.

**O que está em aberto.** O achado 4.3 da auditoria é o único marcado como **suposto**. A suposição:
com `targetSdk = 35`, `android:allowBackup="false"` cobre backup em nuvem mas **não** cobre
transferência entre aparelhos, que passa a ser governada por `android:dataExtractionRules` — ausente
nesta árvore. Se a suposição valer, uma transferência levaria `rosters/` (com **nome de aluno**) e
`outbox.db` (com correções pendentes) para um aparelho novo.

### O que fazer

1. **Medir, e a medição decide.** Com dois aparelhos ou com o fluxo de restauração do emulador,
   determinar se o conteúdo de `filesDir` atravessa a transferência com o manifesto **como está
   hoje**. A asserção é sobre **os arquivos no destino**, e não sobre a tela do assistente de
   transferência — "a transferência disse que copiou" e "o arquivo está lá" são coisas diferentes
   (P2: dizer qual passo o sinal atravessa).
2. **Registrar o resultado com instrumento, data e número** (P6), em `docs/cobertura-*` da etapa.

**Se confirmar** → abre mudança OpenSpec sobre `device-session`: o requisito "Nenhuma das duas SHALL
ser incluída em backup automático do aparelho" passa a cobrir também a transferência, e o manifesto
ganha `dataExtractionRules` negando as duas. A frase da decisão 4 da 4a-zero — *"a diferença entre
'outro aplicativo não lê' e 'o dado não sai do aparelho'"* — continua certa; o que muda é o
mecanismo que a cumpre. Fecha com a medição repetida no aparelho, **depois** da mudança.

**Se não confirmar** → o achado 4.3 é riscado da auditoria **com a medição ao lado** (`rigorous.md`
§10: regra sai com a medição que provou que ela não era necessária, e o registro da saída fica).

### Proibido nesta etapa

- Concluir por leitura de documentação. Documentação é **suposto** até medir (P6), e três suposições
  de transporte já caíram nesta base por isso.
- Acrescentar `dataExtractionRules` "por precaução", sem medir. Seria política sem consumidor (P18),
  e pior: deixaria a suposição sem resposta, com a aparência de resolvida.
- Cifrar o roster em repouso. Não está na auditoria, não está em nenhum requisito, e a política §12
  atribui a segurança física do aparelho ao usuário. Achado novo → item escrito.

---

## ETAPA 3 — O artefato hasheado, uma quebra só

**Veículo:** **ADR-0014** + uma mudança OpenSpec sobre `exam-package`.
**Pré-requisito:** etapa 1 arquivada. **Ambiente:** emulador para `connectedDebugAndroidTest`.

**O que está em jogo.** `params_hash` existe em `CLAUDE.md:38` e em `ARQUITETURA-FINAL-v3.md:48` — e
em mais lugar nenhum. `PackageMeta` carrega dois dos três campos de I3 e afirma, na KDoc, que "a
fatia 6 preenche estes campos **sem mexer no contrato**". A afirmação é falsa no campo que falta, e
o custo de descobrir isso na fatia 6 é rehashear todo pacote publicado.

### 3.0 — O ADR, antes de qualquer linha de código

**ADR-0014 — A tripla de proveniência no artefato imutável.** Ele decide quatro coisas, e as quatro
precisam estar escritas antes de o primeiro commit existir:

1. **§2 vence §5.** A arquitetura se contradiz: I3 exige `prompt_version` + `model_id` +
   `params_hash`; a lista de `meta` no §5 traz só os dois primeiros. Pela precedência do
   `rigorous.md` §0, a invariante vence a prosa descritiva do mesmo documento. O ADR registra a
   contradição — não a apaga (P7) — e corrige a lista do §5.
2. **A semântica dos três campos.** Nulos enquanto a prova for fixa; preenchidos pela fatia 6. O ADR
   diz o que `params_hash` cobre (os parâmetros da chamada que produziu o artefato) e que ele é
   nulo, e não string vazia, quando não há geração — pelo mesmo argumento que a folha avulsa já
   fixou para `student_token`: ausência e valor vazio significam coisas diferentes.
3. **A consequência aceita, e ela é a parte séria.** Acrescentar o campo muda o `content_hash` de
   todo pacote. Pacotes **já publicados** mantêm os bytes que têm — `exam_package` é imutável —, e
   um aplicativo atualizado passa a **recusá-los** pela camada (b) de ADR-0013: parsear e
   reserializar deixa de ser identidade, porque o `encodeDefaults = true` injeta
   `"params_hash":null` que não estava lá. **Essa recusa é o comportamento correto**, é exatamente o
   que a camada (b) existe para pegar, e é alta e não silenciosa. O ADR registra que o caminho para
   as provas já publicadas é o que ADR-0009 já manda: **publicar prova nova, com `short_id`
   próprio**. E registra que esta é a janela barata — pré-lançamento, com duas provas de conferência
   publicadas — e que depois da primeira turma real ela não existe mais.
4. **`meta.exam_id` NÃO é renomeado.** O campo se chama `exam_id` e carrega o `short_id` (achado
   5.4). Renomear agora seria tentador, porque o hash já vai quebrar. **Recusado**, por duas razões
   que o ADR escreve: `LayoutMap` também tem `exam_id`, e renomear nos dois estenderia a quebra ao
   **golden do layout**, à folha de teste e a toda a cadeia de paridade — um evento P23 muito maior
   que este; e renomeação misturada com mudança funcional é o que P25 proíbe. O que entra no lugar é
   uma **asserção executável** de que os dois são o mesmo valor (commit 4 abaixo). O nome continua
   errado e passa a estar preso.

### Os commits, nesta ordem

**Commit 1 — contrato, e só ele.** `PackageMeta` ganha
`@SerialName("params_hash") val paramsHash: String? = null`, ao lado dos outros dois, com a KDoc
corrigida: sai a frase "não há o que retrofitar", entra o que os três campos são e quando são nulos.
Nenhum consumidor muda. **O build fica vermelho neste commit** — os dois literais de hash deixam de
bater —, e isso é esperado e fica dito na mensagem do commit.

**Commit 2 — as fixtures, regravadas pelo caminho que já existe.** O comando é um só:

```
./gradlew :packages:domain:jvmTest -Dplatos.golden.write=true
```

Ele regrava cinco artefatos. **Exatamente três devem mudar** —
`fixtures/prova-referencia.package.json`, `fixtures/prova-2.package.json` e
`fixtures/prova-referencia.turma.package.json`. `prova-referencia.layout.json` e
`folha-de-teste.layout.json` **não podem mudar**: `params_hash` está no `ExamPackage`, não no
`LayoutMap`. **Conferir isso no `git diff` é a guarda de vacuidade desta etapa (P13)** — se um
arquivo de layout mudou, pare: alguma coisa alcançou a geometria, e a etapa mudou de tamanho.

No mesmo commit, os dois literais de hash, que a auditoria enumerou:
- `packages/domain/src/commonTest/.../ExamPackageTest.kt:21` (`HASH_DA_FIXTURE`)
- `apps/api/src/test/.../ExamPublicationTest.kt:32` (`hashDaFixture`) — cujo próprio comentário já
  manda "Regravar junto com a fixture"

`ApiPlatosPacoteTest.HASH` **não** é da fixture: é hash sintético de `MockEngine`. Não se toca nele.

**Commit 3 — P23, e ele é obrigatório.** Regravar fixture exige fechar **paridade e fidelidade na
mesma sessão, com os artefatos dos dois lados gerados naquela sessão** (P23, P3). Não é opcional
porque "só mudou o pacote": `apps/web/scripts/examPackage.ts` lê
`fixtures/prova-referencia.package.json` e alimenta `render-fixture.ts`, que produz o
`build/parity/web.pdf` — **a fixture do pacote está no caminho da paridade**. O precedente é a
própria 4a, que fechou paridade ao mexer no `PLATOS_PACKAGE` sem regravar golden nenhum.

O que roda, e o que se registra: os passos de `fidelidade.mjs`, `compare.mjs` e `tinta.mjs` do
`ci.yml`, com os PDFs **gerados nesta sessão** dos dois lados, e o `timestamp` de cada artefato.

**Commit 4 — a asserção que substitui a renomeação.** Um teste no domínio que afirma, sobre a
fixture publicada, que `meta.examId` é o mesmo valor que o `short_id` da definição **e** o mesmo
que o campo de prova dentro do payload do QR de cada atribuição. Hoje isso é verdade por construção
dentro de `ExamPublication.publish`; o teste transforma "verdade por construção" em "verdade
afirmada". É o que a KDoc de `EXTRA_SHORT_ID` em `ScanActivity` já pedia sem ter.

### Ver falhar — e a mutação certa não é a óbvia

A mutação óbvia — tirar o campo de novo e ver os literais caírem — não prova nada interessante: ela
mede a aritmética do SHA-256. A mutação que importa isola a **camada (b)** e prova que a consequência
que o ADR aceitou é real e alta.

**Preparação (faz parte do commit 2).** Antes de regravar, congelar uma cópia byte a byte do pacote
**anterior** como `fixtures/pacote-do-contrato-anterior.json`, com KDoc dizendo que ele é artefato
do contrato antigo, deliberadamente **não** regerado, e que o `GoldenWriterTest` não o escreve.

**O cenário novo**, em `ConferenciaDePacoteTest`: o pacote do contrato anterior, apresentado com o
`content_hash` **dele** (o de antes), é **recusado**, e a asserção confere **o motivo** — recusa por
fidelidade de interpretação, e não por integridade (`rigorous.md` §3: "a asserção SHALL conferir o
motivo da recusa, e não só que houve recusa").

**A guarda de vacuidade que isola a camada:** no mesmo cenário, afirmar que aquele mesmo pacote
**passa** na camada (a) — `sha256(bytes) == content_hash` declarado. Sem isso, a recusa poderia ser
por integridade, e o cenário estaria medindo a camada vizinha. É literalmente o sombreamento de
fixture que o `rigorous.md` §3 descreve, e que já aconteceu duas vezes nesta base.

**Conjunto previsto sob a mutação** (neutralizar a camada (b) em `ConferenciaDePacote`):

| Cenário | Deve cair? |
|---|---|
| pacote do contrato anterior é recusado por fidelidade | **sim** |
| os demais cenários de fidelidade já existentes | **sim** |
| cenários de integridade (hash divergente, conteúdo truncado) | **não** |
| cenários de identidade (prova errada) | **não** |

Se os de integridade caírem junto, a mutação não isolou a camada — **pare** (regra 0.5).

### Proibido nesta etapa

- **Renomear `meta.exam_id`.** Decidido e recusado em 3.0.4, com a razão escrita.
- **Acrescentar qualquer outro campo ao `ExamPackage`** "já que o hash vai quebrar mesmo". Cada
  campo novo precisa do próprio consumidor (P18). `params_hash` tem: I3.
- **Regravar fixture à mão.** O caminho é o `GoldenWriterTest` atrás da flag, e ele existe
  justamente porque golden que se regrava sozinho não detecta nada.
- **Fechar sem paridade e fidelidade da mesma sessão.** P23 é zona vermelha.
- **Republicar `prova-referencia` sobre si mesma em produção.** ADR-0009: prova publicada tem um
  pacote e um só; corrigir é publicar prova nova com `short_id` próprio.

### Fica escrito

`docs/cobertura-<nome-da-mudanca>.md` com: o conjunto que caiu sob a mutação da camada (b); o
`git diff` das fixtures mostrando **três** arquivos e nenhum layout; os `timestamp` dos PDFs de
paridade e fidelidade daquela sessão; e uma seção nomeando que **os pacotes publicados antes desta
mudança deixam de ser legíveis pelo aplicativo atualizado**, com o caminho de ADR-0009 ao lado.

---

## ETAPA 4 — O servidor confere o que grava

**Veículo:** uma mudança OpenSpec sobre `result-sync`.
**Pré-requisito:** etapa 3 arquivada (R2). **Ambiente:** Docker, para os testes de Postgres.

**O que está em jogo.** `grading_result.package_hash` e `variant_id` são gravados exatamente como o
aparelho os enviou, e nada os compara com o pacote publicado da prova. A coluna existe para ser
prova — a migration diz "nota sem dizer de qual pacote é vira número sem prova" — e §9 da
arquitetura dá a razão de fundo: rastreabilidade existe para **auditoria de contestação de nota**. O
fato é append-only: hash errado não tem conserto.

### Os commits

**Commit 1 — spec antes de código.** O requisito "O resultado durável diz de qual folha, de qual
pacote e de qual aluno ele é" ganha a contraparte do servidor: o servidor **SHALL** recusar resultado
cujo `package_hash` não seja o `content_hash` do pacote publicado daquela prova, e cujo `variant_id`
o pacote não declare; a recusa **SHALL** distinguir-se de ausência, e **SHALL NOT** gravar nada. Dois
cenários novos, um por campo.

**Commit 2 — a conferência.** Em `ResultQueries`, `findPublishedExamId` passa a devolver também o
`content_hash` — a consulta **já faz `join` em `EXAM_PACKAGE`**, então é uma coluna a mais, e não uma
consulta a mais. A comparação acontece dentro da mesma transação de `asUser`, antes de qualquer
`insert`.

O status é **400**, e não 404: ausência continua sendo ausência (prova inexistente, prova sem pacote,
organização alheia), e um corpo incoerente é decisão do servidor sobre o pedido — que é exatamente a
faixa que o aparelho já classifica como **definitiva** e não retentável (`Retorno.Recusou`,
`eTransitoria()`). Um 5xx aqui faria o aparelho repetir para sempre um envio que nunca será aceito.

A validação do `variant_id` sai do pacote publicado: o `content` já está a um `select` de distância,
e `ExamPackage.variants` o declara. **Não** se escreve uma segunda lista de variantes.

### Ver falhar — duas mutações, conjuntos disjuntos

A tarefa 4.5 da fatia do outbox já estabeleceu o padrão: uma mutação que derrube as duas travas não
diz qual segurou.

**Mutação A** — neutralizar a comparação de `package_hash`:

| Cenário | Deve cair? |
|---|---|
| resultado com `package_hash` de outro pacote é recusado | **sim** |
| resultado com `variant_id` que o pacote não declara é recusado | **não** |
| reenvio da mesma captura não cria registro novo | **não** |
| recaptura grava revisão nova | **não** |
| duas folhas avulsas não colidem | **não** |

**Mutação B** — neutralizar a comparação de `variant_id`: o espelho exato. Se os conjuntos não forem
disjuntos, **pare**.

**A guarda de vacuidade (P13) é a forma do dado do cenário negativo.** O `package_hash` falso precisa
ser **64 hexadecimais bem formados** e o `variant_id` do cenário A precisa ser **válido** — senão a
recusa pode vir do `check` da coluna ou da outra trava, e o cenário mede a camada vizinha. E a
contagem de linhas se faz **no banco**, nunca no corpo da resposta: uma rota que responda 400 e grave
assim mesmo passa em qualquer asserção sobre o corpo.

### Proibido nesta etapa

- Recalcular a nota no servidor a partir do gabarito. D4 e §10 são explícitos: **correção objetiva
  local é definitiva quando não há discursivas**. Esta etapa confere **proveniência**, não aritmética.
  Recalcular seria substituir decisão registrada por preferência (P17).
- Escrever uma segunda validação de coerência ao lado de `ObjectiveScore`. A regra 7 do `CLAUDE.md`
  já foi aplicada aqui de propósito, e `paraNota()` é o ponto único.
- Fazer a conferência com uma consulta separada por `content_hash`. Dois oráculos para "qual é o
  pacote desta prova" é o que as KDoc das rotas de roster e de resultado recusam por escrito.
- Transformar a recusa em 404 "por simetria". A distinção entre ausência e incoerência é o que o
  classificador do aparelho consome.

---

## ETAPA 5 — Os dois defeitos do aparelho

**Veículo:** uma mudança OpenSpec sobre `result-sync`.
**Pré-requisito:** etapa 4 arquivada. **Ambiente: emulador — pergunte antes (P22).**

### 5.A — Uma instância de `BaseDoOutbox` (achado 3.2)

**O defeito.** `ResultadosEmRoom.abrir()` chama `Room.databaseBuilder(...).build()`, que não
deduplica. Três chamadores de produção sobre o mesmo `outbox.db` — `SessaoActivity.onCreate:127`,
`ScanActivity.onCreate:122`, `passadaDeEnvio:169` — e nenhum fecha. Cada rotação de tela cria mais
uma instância viva. O worker roda **quando há rede**, inclusive com a câmera aberta.

**Commit 1 — o acessador único.** `abrir(context)` passa a devolver **sempre a mesma instância**,
guardada no companion, construída com `applicationContext` — nunca com o `Context` de uma
`Activity`, que a manteria viva. Os três chamadores não mudam: eles já chamam `abrir`.

**Commit 2 — o teste que mede a topologia da produção.** O cenário existente
(`OutboxEmRepousoInstrumentedTest`, `ApagamentoLocalInstrumentedTest`) constrói a base com nome
próprio e guarda a referência — **exercita uma topologia que não é a da produção**, e é por isso que
o defeito atravessou. O cenário novo afirma, pelo caminho de produção, que duas chamadas a `abrir`
devolvem **a mesma instância**, e que uma escrita pelo caminho do worker e uma leitura pelo caminho
da tela não se atropelam.

**Conjunto previsto** sob a mutação (restaurar o `build()` por chamada):

| Cenário | Deve cair? |
|---|---|
| duas aberturas devolvem a mesma instância | **sim** |
| escrita do worker e leitura da tela convivem | **sim** |
| `ApagamentoLocalInstrumentedTest` (base própria) | **não** |
| `OutboxEmRepousoInstrumentedTest` (base própria) | **não** |
| `GravacaoNoFioPrincipalInstrumentedTest` | **não** |

Os três "não" são o ponto: eles constroem a própria base e **continuam certos sobre o que medem**.
Se caírem, a mutação não isolou nada.

### 5.B — A correção não é descartada em silêncio (achado 3.3)

**O defeito.** `ScanActivity.gravar:248-249` tem dois `?: return`. Folha medida, nota **desenhada na
tela**, nada gravado, nada agendado, nenhuma mensagem. §10 diz "nunca falha em silêncio".

**A correção não é tratar o nulo — é tornar o estado inconstruível.** A decisão de "tem tudo o que
precisa" já mora em `onCreate`, onde `organizacao` e `contentHash` ausentes levam a
`SemPacoteScreen`. O `short_id` passa para o mesmo lugar: ausente, a câmera **não abre**, com motivo
próprio — distinto dos cinco que o gate já distingue. Com isso `prova` e `organizacao` deixam de ser
nuláveis no campo, e `gravar` perde os dois `return`. O caminho silencioso deixa de existir em vez de
ser tratado.

**Conjunto previsto** sob a mutação (restaurar o campo nulável e o `?: return`): cai o cenário novo —
"sem o identificador da prova, o escaneamento não abre, e o motivo é próprio" — e **só ele**. Nenhum
cenário de apuração, de gravação ou de gate cai, porque nenhum deles passa por esse caminho.

### 5.C — O spec passa a dizer o que o código faz (achado 4.2)

`openspec/specs/result-sync/spec.md:41` e `:57` dizem que a folha avulsa produz resultado durável
"com **token vazio**". O código grava **nulo**, em três pontos, e a diferença **é** a decisão: a
migration explica que vazio faria todas as avulsas da mesma prova colidirem no unique de revisão.

Vai como delta desta mudança, e **não** como edição direta da spec principal: o atalho das cinco
condições exige "nenhum texto novo é inventado", e trocar "vazio" por "nulo" inventa texto. Entra
junto a razão, em uma linha, para que a distinção não se perca de novo — ela hoje vive só em
comentário de migration e KDoc, e não no `design.md` de nenhuma fatia.

### Proibido nesta etapa

- `allowMainThreadQueries()` em qualquer teste. Foi exatamente isso que afrouxou o oráculo na trava
  que a produção impõe, e o aplicativo morria ao escanear folha válida.
- `fallbackToDestructiveMigration`. Apagar a base numa atualização destruiria correção que não subiu.
- Criar `apagarDaOrganizacao` em `ResultadosPendentes`. A ausência **é** o requisito, e está escrita:
  seria a ferramenta pronta para alguém chamar de dentro de `sair`.
- Tratar o `short_id` ausente com um valor padrão, ou derivá-lo de `examPackage.meta.examId`. A KDoc
  de `EXTRA_SHORT_ID` já explica por que ler por outro caminho faz o leitor depender de uma igualdade
  que ninguém afirma — e a etapa 3 afirmou essa igualdade **no domínio**, não aqui.
- Fundir 5.A e 5.B num commit só. São defeitos diferentes, com mutações diferentes.

---

## ETAPA 6 — O contrato entre as pontas

**Veículo:** **ADR-0015** + uma mudança OpenSpec.
**Pré-requisito:** etapa 5 arquivada (R3). **Ambiente:** emulador.

**O que está em jogo.** É a única decisão de arquitetura deste plano. Quatro DTOs digitados duas
vezes, e o mapa `QuestionAnswer → string` escrito três vezes (API, Android, `check` da migration).
§13 promete que divergência é **impossível por construção**; hoje ela é possível e contida por
literais combinados. A razão original é de escopo de fatia, e a própria tarefa que a criou admite que
"dividir o DTO era possível" — os dois módulos **já** dependem de `packages:domain`, e os dois já
importam `QuestionAnswer` para preencher esses DTOs.

### 6.0 — O ADR

**ADR-0015 — Contrato de transporte entre servidor e aparelho.** Ele decide:

1. **Onde o contrato mora.** `packages/domain` — que §13 já nomeia como dono de "tipos de contrato",
   e onde o tipo de que os dois lados dependem (`QuestionAnswer`) já está. Nenhuma tecnologia nova,
   nenhum módulo novo: `packages/contracts` do §14 continua sem existir e continua sem consumidor,
   porque não há client TypeScript falando com a API.
2. **O que se move, e o que não.** Movem-se os DTOs de transporte e a tradução
   `QuestionAnswer → string`. **Não** se move a tradução para colunas de jOOQ, que é do servidor, nem
   a tradução para os tipos de tela do aparelho (`paraProva`, `paraSessao`), que é do aparelho. A
   fronteira é o **fio**, e nada além dele.
3. **O `check` da migration continua sendo o terceiro registro, e isso fica dito.** Ele é a guarda do
   banco e não pode vir de Kotlin. O que o ADR exige é que exista **uma conferência cruzada** entre
   os valores do domínio e os do `check` — o mesmo instrumento que a etapa 7 constrói para a versão
   do renderizador, e que `limiar.mjs` já é para o limiar do OMR.
4. **Os testes de literal ficam.** `ResultadoDtoTest` e `ResultRouteTest.corpo()` **não** são
   apagados na mudança. Eles deixam de ser a única garantia e passam a ser o oráculo independente
   que prova que o fio não mudou durante a mudança (P4). Apagá-los porque "agora o tipo é
   compartilhado" destruiria a única evidência de que a migração foi neutra.

### Os commits, e a ordem é a regra 1 do `CLAUDE.md`

1. **Contrato no KMP**, sem consumidor. Código novo, nenhum comportamento muda.
2. **Consumidor do servidor.** `apps/api` passa a usar o tipo do domínio; os literais de
   `ResultRouteTest` **não** mudam.
3. **Consumidor do aparelho.** `apps/android` idem; os literais de `ResultadoDtoTest` **não** mudam.
4. **Os espelhos antigos são removidos** — e só aqui, quando não há mais quem os leia.

### Ver falhar

**A mutação é uma só, e ela é decisiva:** trocar um `@SerialName` no contrato do KMP — por exemplo
`capture_id` → `captureId`.

**Conjunto previsto:** caem **os dois** testes de literal, o do servidor e o do aparelho. Se cair só
um, o fio não está preso nos dois lados e a mudança **não** entregou o que prometeu — **pare**. Esse
conjunto é a prova de que existe agora um dono único, e é a única asserção que distingue "movi o
arquivo" de "unifiquei o contrato".

**Antes e depois, byte a byte.** Guardar o corpo que o aparelho produz **antes** do commit 1 e
compará-lo com o produzido depois do commit 3. Igualdade byte a byte é o que prova que a unificação
foi neutra no fio — e a âncora é o artefato daquela sessão (P3).

### Proibido nesta etapa

- Criar `packages/contracts`, gerar OpenAPI, ou introduzir qualquer geração de código. Não há
  consumidor (P18), e §14 já registrou que o contrato OpenAPI espera client TypeScript.
- Mover, junto, o espelho do `LayoutMap` em TypeScript. Ele é categoria diferente, e a auditoria o
  isentou por escrito: é contido por oráculo de saída (paridade, fidelidade, tinta sobre o documento
  rasterizado). Mexer nele é refatoração fora de escopo (P19).
- "Aproveitar" para renomear campos, acertar plurais ou uniformizar português/inglês. P25.
- Apagar os testes de literal. Está em 6.0.4, e é o único ponto desta etapa que, se for quebrado,
  não tem como ser percebido depois.

---

## ETAPA 7 — As guardas que faltam

**Veículo:** **duas** mudanças OpenSpec, sem delta de spec — o precedente é
`generatejooq-sem-registro-automatico`, que foi mudança de build sem `specs/`.
**Pré-requisito:** etapa 6 arquivada.

### 7.1 — A versão do renderizador para de viver em três registros cegos (achado 4.4)

`LayoutMap.MIN_RENDERER_VERSION` (o que a publicação escreve), `RendererContract.RENDERER_VERSION`
(o que o Android lê, **e o que o gate de captura consulta**, `PreparoDaProva.kt:271`) e
`RENDERER_VERSION` em `apps/web/src/layoutMap.ts:124`. Os três valem `1` e nada os compara. A KDoc do
Android diz "Espelha `RENDERER_VERSION` do lado web" — afirmação sem quem a imponha.

**A solução já existe nesta árvore e não se inventa nada.** `tools/parity/limiar.mjs` foi escrito
para exatamente esta forma de defeito, e o comentário dele no `ci.yml` descreve o caso palavra por
palavra: *"aparece em três registros que não se conhecem … Divergir entre eles não quebra teste
nenhum."*

- `tools/parity/renderizador.mjs`, no molde de `limiar.mjs`: lê os três registros **dos arquivos de
  origem**, e não de uma cópia, e reprova se divergirem.
- Dois passos no `ci.yml`, também no molde: um que confere, outro que **força um valor divergente**
  e falha se a conferência aceitar.

### 7.2 — O artefato que vai ao professor passa a ser verificado (achado 3.1)

Hoje: sem bloco `buildTypes`; `testReleaseUnitTest` não existe no grafo; e
`verificarApkSemPacote` — a guarda que ADR-0013 decisão 5 chama de "a falha mais provável, e a mais
quieta" — roda **só sobre o debug** (`build.gradle.kts:365,368`).

1. **A guarda de pacote embutido passa a cobrir o release.** É a metade que mais importa: o APK do
   professor é o que pode carregar um pacote que ninguém puxou nem conferiu. A guarda de vacuidade
   que já existe (`require(apks.files.any { … })`) vale para os dois.
2. **A decisão sobre `testReleaseUnitTest` é tomada e escrita**, seja ela ligar a variante ou
   registrar que ela fica desligada. A causa já está medida
   (`cobertura-fatia-4a-cache-referencia.md:220`): o AGP 9 não cria a variante por padrão. O que
   falta é **decidir**, porque o gatilho registrado ("a próxima que mexer em build ou variante") já
   disparou duas vezes sem ninguém atender. Se a decisão for não ligar, ela vira linha no §16 com
   fatia-limite — e não mais uma frase num `cobertura-*`.
3. **`cancel-in-progress` deixa de alcançar a paridade** (achado 5.3). P15 registra que ele derrubou
   a paridade na PR #30 e "custou horas". A `concurrency` sai do nível do workflow e passa a ser
   declarada **por job**: `cancel-in-progress: true` para `build` e `web`, `false` para `paridade`.
   §16 chama a paridade de maior risco do projeto se ela não existir; cancelá-la em silêncio é uma
   forma de ela não existir.

**Ver falhar**, nas três: plantar a divergência de versão e ver `renderizador.mjs` nomear **quais
dois** registros discordam; plantar um JSON com forma de pacote nos assets de release e ver a task
recusar; e, para a `concurrency`, o sinal é a configuração — que **não** é medição, e fica dita como
**conferida por leitura**, não como medida (P6).

### Proibido nesta etapa

- Ligar `minifyEnabled` ou acrescentar regras de R8. Não está na auditoria, muda o artefato e abre
  uma frente de verificação inteira. Achado novo → item escrito.
- Assinar o release, mexer em `versionCode` ou tocar em publicação de loja. É trabalho de
  lançamento, não de correção de auditoria.
- Fazer `renderizador.mjs` "ler tudo o que for versão" e virar um conferidor genérico. Ele confere
  três registros nomeados. Abstração sem necessidade comprovada é a regra 8 do `CLAUDE.md`.

---

## ETAPA 8 — A orientação que impede a lista de voltar

**Veículo:** `rigorous.md` (§10: regra nova entra **com o incidente que a pagou**) + uma guarda
executável + duas linhas no `CLAUDE.md`.
**Pré-requisito:** etapas 1 e 7 arquivadas. As duas: a 1 porque cria as linhas do §16 sobre as quais
a guarda opera; a 7 porque é o último incidente que a regra precisa citar.

### O diagnóstico, em uma frase

A auditoria encontrou o padrão e ele é verificável: **os itens que entraram na tabela de ponto de
não-retorno do §16 avançaram e fecharam** — roster cacheado, classe H, retenção da classe B, a 6.4b,
a 9.2. **Os que ficaram em prosa de `docs/cobertura-*.md` não avançaram** — modo degradado, variante
release, migration no deploy, reexame do limiar. A diferença entre os dois grupos não é importância.
É **estar na tabela**.

E há um segundo eixo, que a mesma auditoria isolou: o rigor desta base é **por módulo**, porque todo
teste vive dentro de um. O que atravessa dois — o triplo registro da versão do renderizador, o
espelho quádruplo do DTO, a instância de Room compartilhada entre `Activity` e worker — não tem
camada posicionada para vê-lo.

**Duas regras, e não três.** A tentação é acrescentar uma por categoria de achado. Recusada: três
regras de uma vez é sobre-correção, e `rigorous.md` §10 é explícito em que regra entra com incidente
que a pagou. O terceiro eixo da auditoria — o artefato de release — é **um** incidente e já tem
correção na etapa 7.2; ele fica como linha do §16, que é o instrumento certo para um caso só.

### P27 — o registro de dívida é um só, e fechar exige reconciliá-lo

> **P27 [V]. Nunca adiar item com dono em prosa.** O registro de dívida do projeto é **um**: a tabela
> de ponto de não-retorno do §16. Item adiado que não entra nela não tem data, e item sem data volta
> a flutuar — que é o que §16 já dizia da LGPD, e o que quatro itens repetiram depois. **O archive de
> uma mudança SHALL dizer, para cada linha cuja fatia-limite ou gatilho ela alcançou, se foi paga ou
> reagendada**; reagendar é legítimo e exige fatia-limite nova com o motivo escrito. Silêncio não é.
> Adiar em `docs/cobertura-*.md` continua certo e continua obrigatório — o que deixa de valer é adiar
> **só** lá.

**Os quatro incidentes que a pagam** (§10 exige commit, arquivo e linha; todos estão na auditoria de
2026-09-18):

| Incidente | Onde |
|---|---|
| **Modo degradado** saiu da fatia 4 sem fatia-limite, e o spec de `device-session` passou a afirmar o contrário de §10 sem marca de provisoriedade | auditoria 2.3; `device-session/spec.md:352` |
| **Variante release**: o gatilho registrado — "a próxima que mexer em build ou variante" — disparou **duas** vezes (`d054e1f`, `generatejooq-...`) e ninguém atendeu; há até uma branch (`438030a`) que documenta a dívida sem pagá-la | auditoria 3.1; `cobertura-fatia-4a-cache-referencia.md:220` |
| **Migration em produção** produziu **HTTP 500** na conferência da 4b e continuou sem data, numa lista de "o que este roteiro não cobre" | auditoria 4.6; `deploy-api.md:423` |
| **Reexame do limiar do OMR** foi atribuído pela 3b "à fatia da câmera", venceu na 3c, e a 3c registrou "continua aberta" sem novo prazo | auditoria 4.7; `cobertura-fatia-3c.md` |

### P28 — valor em dois módulos tem dono único ou conferência cruzada

> **P28. Nunca deixar o mesmo valor, contrato ou recurso viver em dois módulos sem dono único
> compilado ou sem uma conferência cruzada que reprove a divergência.** Espelho é permitido; espelho
> **cego** não. Divergir entre registros que não se conhecem não quebra teste nenhum: compila, o
> golden não muda, o hash continua igual, e o defeito chega ao papel ou à nota. Espelho contido por
> **oráculo de saída** — como o `LayoutMap` em TypeScript, julgado pela paridade sobre o documento
> rasterizado — satisfaz esta regra; espelho contido só por literal combinado nos dois lados, não.

**Os incidentes que a pagam:** a versão do renderizador em três registros cegos (auditoria 4.4); o
contrato de transporte em quatro espelhos (auditoria 2.1); e a instância de Room, que é o mesmo
recurso alcançado por três caminhos sem dono (auditoria 3.2). **O precedente que prova que a regra é
barata:** `tools/parity/limiar.mjs` já faz exatamente isto para o limiar do OMR, e o comentário dele
no `ci.yml` já escreveu a justificativa desta regra antes de ela existir.

### A guarda executável — porque regra que ninguém consegue reprovar não segura

Esta base aprendeu isso três vezes. P27 sem guarda vira boa intenção.

- **`tools/divida/divida.mjs`** lê a tabela de ponto de não-retorno **do próprio §16** — não uma
  cópia, pelo mesmo motivo que `RetentionDeclarationTest` lê o catálogo real e não uma lista mantida
  à mão. Uma cópia seria um segundo registro, que é o defeito que a regra existe para corrigir.
- Ele compara a fatia-limite de cada linha com a **fatia corrente**, declarada em um lugar só, e
  **reprova** quando uma linha vence sem reconciliação.
- Isso pede uma disciplina mínima de formato: a célula "Fatia-limite" começa com um token
  parseável (`5`, `2b`, `antes-de:<evento>`) e o resto da prosa vem depois. É a mesma convenção do
  marcador `[retencao:<classe>]`, e pela mesma razão: prosa não é parseável, e o marcador é o que a
  guarda lê.
- **Dois passos no `ci.yml`**, no molde de `limiar.mjs`: um que confere, outro que força uma
  fatia-limite vencida e **falha se a conferência aceitar**.

### As duas linhas no `CLAUDE.md`

No workflow OpenSpec, entre o passo 5 (verificar) e o 6 (`/opsx:archive`):

- No **propose**: a proposta nomeia as linhas do §16 cujo gatilho esta mudança vai alcançar.
- No **archive**: o archive reconcilia cada uma — paga, ou reagendada com fatia-limite nova e motivo.

### Proibido nesta etapa

- **Afrouxar qualquer coisa em nome de "menos cerimônia".** `rigorous.md` §10 é explícito: regra
  removida por incômodo, e não por evidência, confirma exatamente o argumento que a criou. Este plano
  **não remove nada**; acrescenta uma reconciliação por archive.
- Criar uma terceira regra. Decidido e recusado acima, com a razão.
- Duplicar o §16 num YAML "para facilitar o parsing". Seria dois registros — o defeito que P27
  corrige.
- Fazer a guarda ler `docs/cobertura-*.md`. Prosa não é parseável, e a regra é justamente que a prosa
  **não** é o registro.

---

## 9. O que este plano deliberadamente não faz

Cada item com dono e fatia-limite, porque é o que P19 e P20 exigem de achado fora de escopo — e
porque um plano que fecha sem esta seção ensina o oposto do que a etapa 8 institui.

| O que | Por que fica fora | Fatia-limite | Dono |
|---|---|---|---|
| **Implementar o modo degradado** | É fatia vertical de produto (§10, §15), não correção de auditoria. A etapa 1 lhe dá data | **5** | mantenedor |
| **Pipeline de migration** | Exige decisão de desenho (recusar servir com schema atrás? conferir no arranque?) que pertence a um `design.md` próprio | **antes da 1ª migration da 5 em produção** | mantenedor |
| **Ampliar o corpus de CV e reexaminar `V` e `C`** | A fatia 5 **é** a fatia do corpus. Fazer agora seria antecipar a fatia | **5** | mantenedor |
| **Investigar os 2 QRs do corpus que não decodificam** | A 3b já decidiu que é fatia própria, e nove fotos são amostra pequena para concluir | **5**, junto com o corpus | mantenedor |
| **Renomear `meta.exam_id` → `short_id`** | Recusado com razão escrita no ADR-0014 (3.0.4). Se algum dia for feito, **tem de ser um evento P23 próprio**, com golden de layout e paridade juntos | — | mantenedor |
| **Cifrar o roster em repouso no aparelho** | Não é achado: a política §12 atribui a segurança física ao usuário, e a classe H já espera parecer jurídico | segue a linha da classe H no §16 | jurídico externo |
| **`minifyEnabled`, assinatura, `versionCode`** | Trabalho de lançamento | fatia comercial | mantenedor |
| **Sentry** | §13 o prevê e `deploy-api.md:421` já registra que não existe. Não é regressão | — | mantenedor |
| **`assessment_fact`** | Adiamento **correto**: o insumo está preservado em `answer_observation` e a derivação por junção com o pacote imutável continua possível | **9** | mantenedor |

---

## 10. Quadro de fechamento

Uma etapa está fechada quando as oito respostas do `rigorous.md` §8 existem **por escrito** — e a
checklist abaixo é elas, na forma deste plano.

- [ ] O comando **cheio** rodado nesta sessão, e o exato (P5). Para etapas com Android:
      `./gradlew build` **e** `./gradlew :apps:android:connectedDebugAndroidTest`, sem filtro.
- [ ] O `timestamp` do relatório que prova a execução, e a contagem de **tasks executadas** — não
      `UP-TO-DATE` (P2, P3).
- [ ] Qual sinal foi observado, e **qual passo ele atravessa** (P2).
- [ ] Contra qual oráculo, e por que ele é independente (P4).
- [ ] A mutação, e o **conjunto que caiu** — comparado ao conjunto **previsto** neste plano (P9).
- [ ] `grep -rn "MUTACAO"` fora de `build/` em `0`, e a reversão **rodada** (P10).
- [ ] O que ficou sem verificação automática, e por quê — como lacuna, nunca como mitigado (P8).
- [ ] Se algum número ou hash mudou: a data e a âncora do artefato comparado (P3), e — se houve
      regravação de fixture — paridade e fidelidade da **mesma sessão** (P23).
- [ ] **A partir da etapa 8:** a reconciliação das linhas do §16 que esta mudança alcançou (P27).

E a regra que vale acima de todas elas, porque é a que este plano inteiro serve:

> **Nunca afirme mais do que a evidência atravessa.**
>
> Fechar três etapas verificadas e nomear duas como pendentes é um resultado. Fechar oito marcadas e
> duas sem execução não é resultado nenhum — é dívida com juros escondidos (`rigorous.md` §9).

---

## 11. O que dizer à IA em cada sessão

O mecanismo que faz este plano valer não é o texto dele: é **transcrevê-lo para os artefatos da
mudança**. Instrução de chat é nível 5 na precedência do `rigorous.md` §0 — o próprio desenvolvedor
a contradiz sem perceber três horas depois. `design.md` e `tasks.md` do change ativo são níveis 3 e
4, e mudá-los exige `/opsx:update`, que é uma ação visível. **Então a primeira coisa que cada sessão
faz é mover a etapa para dentro da mudança.**

### Sessão 1 — a etapa 1 (commits diretos, sem mudança)

```
Leia, nesta ordem:
- docs/plano-de-correcao-antes-da-fatia-5.md — seções 0, 1 e 2, e a ETAPA 1 inteira
- docs/auditoria-2026-09-18-antes-da-fatia-5.md — os achados que a ETAPA 1 cita: 7, 4.5, 4.6,
  4.7, 2.3, 5.1, 5.2

Execute a ETAPA 1, e só ela. Ela não abre mudança OpenSpec: são commits diretos, um por item,
na ordem 1.1 a 1.5. A lista "Proibido nesta etapa" é normativa.

Não comece nenhuma outra etapa. Achado novo vira item escrito com dono e fatia-limite (P19),
nunca implementação.
```

### Sessões 2 a 8 — cada mudança

Troque `<N>` e `<nome>` pela linha correspondente da tabela do §2.

```
Leia, nesta ordem:
- docs/plano-de-correcao-antes-da-fatia-5.md — seções 0, 1 e 2, e a ETAPA <N> inteira
- docs/auditoria-2026-09-18-antes-da-fatia-5.md — o achado que essa etapa fecha

Rode /opsx:propose <nome>.

O escopo é exatamente a ETAPA <N>, sem acrescentar nem tirar. Ao gerar os artefatos:
- os commits numerados da etapa viram as tarefas do tasks.md, na mesma ordem
- a lista "Proibido nesta etapa" entra no design.md como decisões JÁ TOMADAS, com as razões
  que o plano dá — não como alternativas em aberto
- a mutação e a tabela "conjunto previsto" entram no tasks.md como a tarefa de verificação,
  com a tabela copiada como está
- a regra de parada (seção 0.5) entra no design.md: se o conjunto real divergir do previsto,
  para e escreve, não conserta o instrumento

Me mostre proposal.md, design.md e tasks.md antes do /opsx:apply.
```

Para as etapas que exigem ADR (3 e 6), acrescente a última linha:

```
O ADR vem antes de qualquer código, e o conteúdo dele está na seção 3.0 (ou 6.0) do plano.
```

### O que não dizer

O `rigorous.md` §6 já traz o catálogo de pressão, e ele foi escrito contra esta base. Três frases
são **específicas deste plano**, e as três são tentadoras:

| Se você se ouvir dizendo | O que é | O que o plano já responde |
|---|---|---|
| "só mudou a fixture do pacote, o layout nem mexeu — pula a paridade" | P23, zona vermelha | `apps/web/scripts/examPackage.ts` **lê a fixture do pacote** e alimenta o PDF da paridade. A fixture está no caminho. E o precedente é a 4a, que fechou paridade ao mexer no `PLATOS_PACKAGE` sem regravar golden nenhum |
| "já que o hash vai quebrar mesmo, renomeia `exam_id` junto" | P19 + P25 | Decidido e **recusado** no ADR-0014 (3.0.4): arrastaria o golden do layout e a folha de teste para a quebra |
| "as etapas 5 e 6 são pequenas, faz as duas nesta sessão" | regra 0.2 | Uma mudança = um PR = um escopo de chat (§14 regra 4). E a 6 é contrato KMP: R3 proíbe duas em voo |

E as quatro do catálogo que mais vão aparecer nesta banda: *"roda só a classe que eu mexi"* (P5),
*"está verde aqui, pode fechar"* (P5), *"marca como feito, depois eu rodo"* (P1), *"não precisa ver
falhar, o teste é óbvio"* (P9).

### Se a IA propuser algo que não está no plano

Isso é **sinal**, e não ruído. Uma das duas coisas é verdade: ou ela está improvisando — e a resposta
é apontar a seção "Proibido" da etapa —, ou **o plano está errado**, o que é perfeitamente possível:
ele foi escrito por leitura de código, e implementar mede. Se for o segundo caso, o veículo é
`/opsx:update`, com o motivo escrito — nunca desvio silencioso. O plano é nível 5 até virar
`design.md`; depois disso ele é nível 3, e nível 3 se muda por `/opsx:update` (`rigorous.md` §0).
