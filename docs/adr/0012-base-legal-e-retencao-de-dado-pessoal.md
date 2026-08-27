# ADR-0012 — Base legal, papéis e retenção de dado pessoal; o modo sem identificação nominal é o padrão

**Status:** aceito · **Data:** 2026-08-27 · **Fatia-limite:** 3 (o gatilho é o de ADR-0006)
**Referências:** `docs/legal/politica-de-privacidade.md` v1.0 §3, §4, §6, §10, §12 · `ARQUITETURA-FINAL-v3.md` §5, §11, §16 · ADR-0002 · ADR-0003 · ADR-0006 · I5

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
