# ADR-0012 — Base legal, papéis e retenção de dado pessoal; o modo sem identificação nominal é o padrão

**Status:** aceito · **Data:** 2026-08-27 · **Fatia-limite:** 3 (o gatilho é o de ADR-0006)
**Referências:** `docs/legal/politica-de-privacidade.md` v1.0 §3, §4, §6, §10, §12 (**a v2.0 de 2026-10-02 é a referência vigente: ver a atualização ao fim**) · `ARQUITETURA-FINAL-v3.md` §5, §11, §16 · ADR-0002 · ADR-0003 · ADR-0006 · I5

## Contexto

§16 lista a LGPD com dados de menores como **o único risco ainda sem encaminhamento**, com fatia-limite 3 e sem dono técnico. O ADR-0006 fixou o gatilho — base legal e política de retenção resolvidas antes do fim da fatia 3 — e nomeou a mitigação disponível: o modo sem identificação nominal, habilitado de graça pela separação do roster.

O parecer agora existe. `docs/legal/politica-de-privacidade.md` decide base legal por faixa etária, quem é controlador em cada modalidade de contratação, oito classes de retenção e o conteúdo mínimo do contrato de operador. Este ADR **não repete** o documento: ele registra as decisões que viram obrigação de código, aponta para a política por caminho e versão, e diz o que cada uma obriga.

Há também uma contradição a resolver antes que ela chegue a papel impresso. A política §3.4 afirma que a plataforma *"oferece, e recomenda como padrão, o modo sem identificação nominal"*. Ela não oferece: `exam_roster.display_name` é `not null` e nada no sistema distingue nome civil de apelido. O momento de corrigir é agora, com `exam_roster` vazio e sem nenhuma tela que o preencha — depois da fatia 4, que torna o dado durável, o mesmo trabalho vira retrofit sobre dado de menor já coletado.

## Decisão

### 1. Base legal, por faixa etária

Conforme a política §6.2, que este ADR adota como fonte:

| Titular | Base legal |
|---|---|
| Criança, menor de 12 anos | Consentimento específico e em destaque do responsável legal (art. 14, §1º) |
| Adolescente, 12 a 17 anos | Execução de contrato e legítimo interesse na atividade educacional regular (art. 7º, V e IX), sob o crivo do melhor interesse |
| Registros de acesso | Obrigação legal (art. 7º, II, c/c art. 15 do Marco Civil) |

O consentimento é obtido **pelo controlador**, não pela plataforma. Registrar consentimento é funcionalidade opcional e não é desta fatia.

### 2. Quem é controlador, e o que isso obriga no código

A política §3.3 resolve a ambiguidade que §16 levantava: professor vinculado a instituição mantém a instituição como controladora; professor autônomo é ele próprio o controlador; a exceção do art. 4º, II, "a" não alcança o uso docente, que é atividade econômica.

**O que isso obriga:** a plataforma precisa saber, por organização, sob qual cenário ela opera, e registrar a declaração de quem a criou. A declaração na interface (§3.5) e o bloqueio de roster nominal em organização `school` sem contrato de operador (§4) **são da fatia da tela**, e não desta — não há tela de cadastro de aluno.

### 3. O modo sem identificação nominal é o padrão, e é da organização

Toda organização opera em modo **codificado** ou **nominal**. O modo é propriedade da organização, e não da prova nem da linha de roster: uma prova em modo diferente das outras tornaria indeterminável a resposta a "esta organização trata nome civil de menor?", que é a pergunta que §3.3 obriga a responder.

**Organização nova nasce codificada.** Recomendar um padrão na política e nascer no outro seria descrever um sistema que não existe. Nominal é escolha explícita.

**O que o armazenamento afirma, e o que ele não afirma.** Em modo codificado o roster recusa `enrollment_id` — identificador emitido pela instituição, que existe só para ligar o aluno ao cadastro escolar e não tem uso pedagógico aqui. É o único campo cuja presença é, por si, incompatível com o modo.

O sistema **não** afirma que distingue nome civil de apelido. Não é decidível pelo conteúdo do campo, e uma checagem que tentasse — só dígitos, sem espaço, tamanho máximo — reprovaria "Ana B." e aprovaria "Maria". O modo declarado registra a intenção de quem cadastrou; o que fecha o resto é a coerção na interface de §3.5, da fatia da tela.

### 4. Retenção: as classes da política são padrões, alteráveis para menos

As oito classes do §10 são adotadas como declaradas. A instituição controladora pode estipular prazo menor por contrato; nunca maior sem as hipóteses do art. 16.

**O que isso obriga agora:** toda tabela declara sua classe. **O que fica para a fatia 4:** expurgo, anonimização da classe B, atendimento a pedido de eliminação e a classe H de cache no dispositivo — todos operam sobre dado durável, que só passa a existir com o sync.

A distinção que organiza o §10 é a mesma do ADR-0006, e vale repetir porque decide o desenho: dado cujo valor está no número anonimiza; dado cujo valor está no conteúdo produzido pelo aluno não anonimiza e precisa ser eliminado.

### 5. Guarda de construção, e não invariante

O ADR-0006 decidiu **não** promover "finalidade declarada" a invariante, porque não seria verificável por teste. Esta decisão separa as duas metades:

- **A declaração é verificável.** Toda tabela de `public` declara finalidade e classe de retenção no próprio catálogo, e a construção é recusada quando falta. Vale para todas, não só para as com dado pessoal — uma das classes admitidas é "nenhum" —, porque exigência que dependa de alguém manter a lista de quais tabelas têm dado pessoal deixa a tabela nova de fora.
- **A correspondência entre a finalidade declarada e o uso real não é verificável**, e a guarda não promete isso.

A guarda é de construção. As invariantes desta base continuam sendo I1–I5, e valem porque cada uma reprova um desenho concreto; diluí-las com regra de processo enfraquece as que existem. O ADR-0006 fica honrado, e não contrariado.

### 6. Quatro afirmações da política que o código já honra

Publicada, cada uma vira promessa contratual. Ficam registradas aqui com onde estão provadas, para que quebrar qualquer delas apareça como teste vermelho e não como descoberta de auditoria:

| Afirmação | Onde está provada |
|---|---|
| §6.4 — a leitura óptica roda inteiramente no dispositivo, sem enviar imagem a servidor; sem discursiva, todo o ciclo é local | `SheetReaderInstrumentedTest.da_imagem_ate_a_nota_sem_tocar_a_rede`, com `StrictMode`, `detectNetwork` e `penaltyDeath` sobre imagem → cobertura → veredito → resposta → nota, mais o meta-teste que prova que a guarda reage a um socket real |
| §6.4 — o artefato publicado é imutável e não contém dado pessoal direto; o roster é separado e mutável | I5 e ADR-0002; `ExamPackageTest` e os cenários de `exam-package` sobre apagar o roster sem invalidar o hash |
| §12 — isolamento por organização imposto no banco por RLS, e não na aplicação | `ConnectionRoleTest.toda tabela de public tem RLS habilitada e forcada`, derivado do catálogo |
| §11 — revisão humana prevalece, e a sugestão registra prompt, modelo e parâmetros | A invariante de revisão humana e I3; hoje sem consumidor, porque não há IA |

A política §12 afirma ainda que dados reais de aluno não são usados em desenvolvimento ou teste. O corpus fotografado da fatia 3b atende: `student_token` vem vazio e `assignments` é lista vazia — não há campo onde um aluno real caberia.

## Consequências

- O risco de §16 deixa de estar sem encaminhamento. A parte com código passa a ter dono técnico; a parte legal continua sendo do mantenedor com apoio jurídico externo.
- Organização criada a partir daqui nasce em modo codificado. É surpresa na direção segura e reversível por escolha explícita — o contrário não é reversível, porque o dado já teria sido coletado.
- Toda migration nova passa a precisar de duas linhas de declaração, e a construção reprova sem elas.
- A política e o schema envelhecem juntos: acrescentar classe de retenção na política sem acrescentá-la à guarda reprova a construção.
- Alterar os prazos do §10, a base legal ou o padrão do modo exige ADR novo, e não edição silenciosa da política.

## Como isto poderia ter falhado em silêncio

O caminho natural era construir a tela de cadastro de aluno junto com a câmera, porque é lá que ela faz falta, e resolver a conformidade quando aparecesse o primeiro cliente. A tela teria um campo "nome do aluno", porque é o que se espera de um cartão de respostas, e o primeiro piloto com turma real poria nome de menor no banco sem base legal registrada, sem prazo de retenção e sem o modo que a política afirma existir.

Nada disso quebraria teste. O sistema funcionaria melhor com o nome do que sem, e a folha impressa ficaria mais legível. A conta chegaria depois, sobre dado já coletado, que é o único momento em que ela não pode mais ser paga barato.

## Alternativas descartadas

**Derivar o modo de `organization.kind`.** `school` implicaria nominal, `personal` codificado. Está errado nos dois sentidos: uma escola pode preferir número de chamada, e um professor autônomo com contrato pode precisar do nome. A política trata os dois eixos como independentes, e o §4 combina os dois.

**Modo por linha de roster.** Flexibilidade que ninguém pediu, e que destrói a afirmação: bastaria uma linha nominal para o modo da organização deixar de valer como resposta.

**Validar o conteúdo de `display_name`.** Daria aparência de garantia onde não há. Um sistema que afirma detectar nome civil e não detecta é pior que um que declara não detectar.

**Exigir a declaração de retenção só das tabelas com dado pessoal.** Exigiria manter a lista de quais são, e a tabela nova ficaria de fora — o mesmo defeito que a guarda de RLS já teve, e cujo KDoc registra que a versão anterior contava linhas e teria passado com uma tabela sem RLS.

**Esperar o primeiro contrato.** É o que §16 recusa explicitamente, por induzir a folga que não existe: o Basic é self-serve, e a ambiguidade chega junto com o primeiro cliente.

---

## Atualização de 2026-10-03 — a política v2.0 é a referência

**O que aconteceu.** Em 2026-10-02 o mantenedor reescreveu `docs/legal/politica-de-privacidade.md` (v2.0, "Alinhada à Arquitetura de IA e Calibração de OCR") para viabilizar o corpus de medição (§6.3 e §7). Ela ainda tem campos `[DATA]`, `[RAZÃO SOCIAL COMPLETA]` etc., não foi publicada e **não passou pelo parecer jurídico externo**. Em 2026-10-03 o mantenedor mandou adequar este ADR, as specs e o código a ela. Esta seção é esse registro: o texto acima **fica**, como o registro do que foi decidido e por quê (P7), e a lista abaixo diz o que dele vale hoje. A regra "alterar os prazos do §10, a base legal ou o padrão do modo exige ADR novo" está cumprida por esta atualização, feita por instrução expressa de quem escreveu a v2.0.

| Decisão acima | Hoje |
|---|---|
| **1. Base legal** | A fonte é a v2.0 §6.2: menor de 12 anos, *consentimento específico obtido pelo controlador* **ou** execução de contrato; 12 a 17, execução de contrato ou legítimo interesse. A frase "em destaque do responsável legal (art. 14, §1º)" deixou de estar na política. Se a v2.0 basta em lei é matéria do parecer externo, e não deste ADR. |
| **2. Papéis** | A v2.0 §3.1 a §3.3 traz os mesmos três cenários. **A declaração na interface (antiga §3.5) e o contrato de operador celebrado *antes* do tratamento, com bloqueio do roster nominal sem ele (antiga §4), deixaram de ser exigência da política.** Não foram construídos (não há tela de cadastro de aluno) e **continuam abertos na linha "LGPD com dados de menores" do §16, como medida de produto**, até o mantenedor decidir se saem. Nada foi descartado em silêncio. |
| **3. Modo sem identificação nominal** | A v2.0 §3.4 diz que a plataforma o *oferece*; não diz "padrão". **O padrão codificado continua sendo decisão deste ADR, e o código o honra** (organização nova nasce `coded`). |
| **4. Retenção** | A v2.0 §10 tem **seis** classes, A a F: A manuscrito e transcrição (ano letivo + 180 dias), B fato de avaliação e nota (vínculo + 5 anos, depois anonimização), C roster, D conta, E registros de acesso (6 meses), F metadados de chamada de IA (24 meses). **A antiga G (conteúdo autoral) e a antiga H (cache no dispositivo) não existem na v2.0.** Consequências: (a) **"classe H" passa a ser o nome de projeto do conjunto de regras do cache no aparelho, e esse conjunto é definido por este ADR, e não pela política.** Está nas specs `scan-session`, `result-sync` e `device-session` (que a chamam de "classe H"), em `measurement-corpus` (as amostras do corpus) e nos comentários do código: o pendente e a nota do professor só saem depois da confirmação do servidor; a imagem da resposta tem teto de 30 dias e sai também pela confirmação da nota, pelo "refazer" e por nenhum caderno a referenciar; as amostras do corpus têm 30 dias e somem ao sair e na revogação; roster, pacote e visão guardada saem ao sair e na revogação; o resultado pendente, a nota pendente, o caderno e a resposta **sobrevivem** a sair e à revogação (decisão de 2026-10-01). **Todos são mais restritos que o teto da classe A da v2.0.** (b) O conjunto de classes que uma tabela pode declarar passa a ser **A a F e `nenhum`**. |
| **5. Guarda de construção** | O conjunto admitido é A a F e `nenhum`. Nenhuma tabela declarava G ou H (só B, C, D e `nenhum`). |
| **6. As quatro afirmações** | "§6.4, leitura óptica local sem enviar imagem" → v2.0 §12, *Privacy by Design* ("processamento local no dispositivo para questões objetivas; isolamento do Roster nominal em relação às imagens"); "§12, RLS" → v2.0 §12; "§11, revisão humana" → v2.0 §11. "Artefato imutável sem dado pessoal direto" **não está na v2.0**; vale pela I5 e pelo ADR-0002. **"Dados reais de aluno não são usados em desenvolvimento ou teste" saiu da política:** o corpus de medição (ADR-0022) usa letra real de aluno, só do mantenedor, em APK de depuração, sob a v2.0 §6.3 e §7. |

**O que continua em aberto, e de quem é.**
1. **Transparência.** A v2.0 não descreve o cache no aparelho (a "classe H") nem o que sai do aparelho por cabo no corpus. Se a política deve mencioná-los é decisão do mantenedor com o jurídico externo; linha "A política v2.0 removeu…" do §16.
2. **A coerção do papel e o bloqueio do roster nominal sem contrato** (decisão 2): medida de produto sem exigência de política. Linha de LGPD do §16.
3. **A v2.0 não foi publicada** e não tem parecer externo: o evento `antes-de:publicacao-da-politica` não chegou.

**O que mudou fora deste arquivo por causa disto**, e o que não mudou: os ponteiros que citavam a política v1.0 (§10.8 e §6.4) em `scan-session`, em `RetencaoDaResposta.kt` e em `GradedResultDto.kt` passam a apontar para cá; o conjunto de classes de `RetentionDeclarationTest` passa a A a F e `nenhum`. **Nenhum comportamento mudou:** nem prazo, nem evento de eliminação, nem o que sobrevive a sair.
