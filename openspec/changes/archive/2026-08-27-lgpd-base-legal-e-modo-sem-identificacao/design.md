## Context

Ver `proposal.md — Why`. O que importa aqui é o estado do schema e o que já existe para reaproveitar.

`organization` tem `kind text not null check (kind in ('personal','school'))` desde a fatia 0 — a distinção de que os §3 e §4 da política inteiros dependem já está lá.

`exam_roster` tem `display_name text not null check (length(trim(display_name)) > 0)`, mais `class_group` e `enrollment_id`, ambos anuláveis. Nenhuma das oito tabelas de `public` declara finalidade ou classe de retenção.

Existe uma guarda derivada do catálogo, e ela é o molde desta mudança: `ConnectionRoleTest.toda tabela de public tem RLS habilitada e forcada` varre `pg_class`, junta as violações numa lista e afirma que a lista é vazia — com um **piso de contagem** para que uma consulta que volte vazia, por schema errado ou migration não aplicada, não passe sem ter olhado nada. O KDoc dela registra que a versão anterior contava linhas e teria passado com uma tabela sem RLS.

A API tem duas rotas e não escreve roster hoje. Não há tela de cadastro de aluno em lugar nenhum.

## Goals / Non-Goals

**Goals**

- Fazer o schema parar de contradizer a política, e nada além disso.
- Não afirmar mais garantia do que o banco consegue sustentar.
- Deixar a fatia da câmera e a fatia 4 com o terreno pronto, sem antecipar o que é delas.

**Non-Goals**

- Não construir tela, nem a coerção de papel do §3.5, nem o bloqueio do §4.
- Não implementar expurgo, anonimização nem atendimento a pedido de titular.
- Não tocar em `ExamPackage`, `LayoutMap`, golden ou hash.
- Não criar tabela de consentimento. O §6.2 diz que o consentimento é obtido **pelo controlador**, e que a plataforma oferece funcionalidade opcional de registro — opcional é fatia futura.

## Decisions

### 1. O modo de identificação é da organização

Coluna em `organization`, e não em `exam` nem em `exam_roster`.

É postura de tratamento de dados de uma organização inteira: se uma prova pudesse estar em modo nominal e outra em codificado, não haveria resposta para "esta organização trata nome civil de menor?", que é a pergunta que o §3.3 da política obriga a responder. É também a granularidade de que o bloqueio do §4 vai depender, e ele é por organização do tipo `school`.

**Padrão é o codificado.** A política §3.4 diz que a plataforma "oferece, e recomenda como padrão" o modo sem identificação nominal. Recomendar como padrão e nascer no outro modo seria a política descrevendo um sistema diferente do que existe. Organização nova nasce codificada; nominal é escolha explícita.

*Alternativa descartada — por linha de roster.* Daria flexibilidade que ninguém pediu e tornaria a postura da organização indeterminável: bastaria uma linha nominal para o modo codificado não valer mais como afirmação.

*Alternativa descartada — derivar de `organization.kind`.* `school` implicaria nominal e `personal` codificado. É tentador e está errado nos dois sentidos: uma escola pode querer operar por número de chamada, e um professor autônomo com contrato pode precisar do nome. A política trata os dois eixos como independentes, e o §4 combina os dois.

### 2. O banco afirma o que consegue, e declara o resto

`display_name` continua sendo texto livre, e **isso é deliberado**: nenhuma coluna distingue "Maria Silva" de "aluno 17", e uma checagem que tentasse — sem espaço, só dígitos, tamanho máximo — reprovaria "Ana B." e aprovaria "Maria", dando aparência de garantia onde não há.

O que é enforçável, e passa a ser enforçado:

| Em modo codificado | Por quê |
|---|---|
| `enrollment_id` SHALL ser nulo | É identificador emitido pela instituição. Existe só para ligar o aluno ao cadastro escolar, e não tem uso pedagógico dentro da plataforma. É o único campo do roster cuja presença é, por si, incompatível com o modo |

`class_group` continua permitido nos dois modos: turma não identifica um aluno, e é o que organiza a correção em lote.

O resto — que o conteúdo de `display_name` seja de fato um código — é **declaração de quem cadastrou**, registrada pelo modo da organização, e o requisito de spec diz isso com todas as letras. É a mesma honestidade do ADR-0006 ao recusar transformar "finalidade declarada" em invariante.

*Como isto pode falhar em silêncio, e o que sobra:* uma organização em modo codificado com nomes civis digitados em `display_name`. O sistema não detecta, e não vai fingir que detecta. O que ele garante é que a organização declarou o modo, que a declaração é auditável, e que o identificador institucional não entrou junto. O §3.5 — a coerção na interface, com a declaração do usuário registrada — é o que fecha esse buraco, e é da fatia da tela.

### 3. Finalidade e retenção declaradas em toda tabela, e a guarda que vem junto

**Toda tabela de `public` declara**, e não só as que têm dado pessoal. É a diferença entre uma guarda que pega tabela nova e uma que não pega: se a exigência valesse só para "tabelas com dado pessoal", alguém precisaria manter a lista de quais são, e a tabela nova ficaria de fora exatamente como a tabela sem RLS ficava antes de a guarda atual ser corrigida.

Então toda tabela declara uma classe, e uma das classes admitidas é **"nenhum dado pessoal"**. Quem cria tabela é obrigado a dizer conscientemente em qual caso está.

**Forma da declaração.** `comment on table`, com um marcador legível por máquina dentro do texto livre:

```sql
comment on table public.exam_roster is
    'Identificacao do aluno para correcao e boletim, mantida fora do artefato imutavel (ADR-0002).
     [retencao:C] [dado-pessoal:sim]';
```

Comentário de catálogo, e não tabela de metadados, porque a declaração precisa viajar **junto da estrutura**: uma tabela de metadados pode ficar para trás numa migration e ninguém percebe. O marcador entre colchetes existe porque prosa não é parseável e a guarda precisa de um valor, não de uma frase.

**As classes** são as da política §10 — A a H — mais `nenhum`. A guarda recusa classe fora desse conjunto, o que faz a política e o schema envelhecerem juntos: acrescentar classe I na política sem acrescentá-la à guarda reprova a construção.

**A guarda**, no molde de `ConnectionRoleTest`: varre `pg_class` junto de `obj_description`, junta as violações, afirma lista vazia, e mantém o piso de contagem para que consulta vazia não passe. Ela vive ao lado da que existe, no `apps/api`.

*A tensão com ADR-0006, resolvida e não escondida.* O ADR-0006 decidiu não fazer disto uma invariante porque "não é verificável por teste". A parte verificável é a **declaração**; a parte não verificável é se a finalidade declarada corresponde ao uso real. A guarda cobre a primeira e não promete a segunda, e o ADR-0012 registra a distinção. As invariantes I1–I5 continuam sendo cinco.

### 4. A validação do roster mora onde a escrita acontece

Hoje nada escreve roster. Quando a tela existir, a escrita passa pela API — então a validação do modo fica em `apps/api`, junto da inserção, e **não** em `packages/domain`.

O domínio compartilhado existe para o que os dois renderizadores e o aparelho precisam calcular igual: medição, layout, scoring, contratos do pacote. O roster não é nada disso — ele nunca entra no artefato imutável, nunca é lido offline pelo aparelho, e existe só do lado do servidor. Subi-lo ao KMP seria abstração sem segundo consumidor.

A restrição de `enrollment_id` nulo em modo codificado é **também** um `check` no banco, e não só validação de aplicação, pela mesma razão que o isolamento é RLS e não filtro: a regra que vale é a que o armazenamento impõe.

*Quando revisar:* se a fatia 7 fizer o pacote carregar variante por aluno e o aparelho precisar resolver roster offline. Aí o contrato sobe, e é refatoração mecânica num monorepo.

### 5. A política entra versionada com os placeholders

`docs/legal/politica-de-privacidade.md` é o documento que o ADR-0012 cita. Versionar com `[RAZÃO SOCIAL]`, `[CNPJ]` e `[DATA]` em aberto é o estado verdadeiro: o próprio aviso preliminar do documento diz que os campos precisam ser preenchidos antes da publicação.

O ADR **não duplica** a política. Ele registra as decisões que viram obrigação de código, aponta o caminho e a versão, e lista o que cada uma obriga. Duplicar criaria duas fontes que divergem na primeira revisão do texto legal.

## Risks / Trade-offs

**A guarda de comentário vira ruído se ninguém a levar a sério** → o marcador é curto e a mensagem de falha nomeia a tabela e o que falta. O custo é uma linha por tabela, na migration que a cria.

**Modo codificado como padrão pode surpreender uma escola** → é surpresa na direção segura, e reversível por uma escolha explícita. O contrário — nascer nominal e o professor descobrir depois que digitou nome de menor sem base legal — não é reversível: o dado já foi coletado.

**A política afirma quatro coisas que o código já honra**, e ao publicar elas viram promessa contratual → o ADR-0012 registra cada uma com onde está provada, para que quebrar qualquer delas apareça como quebra de teste e não como descoberta de auditoria: leitura óptica inteiramente local (`da_imagem_ate_a_nota_sem_tocar_a_rede`, com `StrictMode` e o meta-teste que prova a guarda), artefato imutável sem dado pessoal direto (I5, ADR-0002), isolamento por organização em RLS forçada (a guarda de catálogo), revisão humana prevalecendo e rastreabilidade de prompt, modelo e parâmetros (invariante de revisão humana e I3).

**O buraco que sobra é o §3.5** → uma organização codificada com nomes digitados no campo livre. Está declarado na decisão 2, e fecha na fatia da tela. Não fingir que fechou agora é parte do desenho.

## Migration Plan

Migration nova, aditiva. `organization` ganha o modo com `default` codificado, então nenhuma linha existente precisa ser atualizada — e não há linha de roster no banco para violar a nova restrição.

Reversibilidade: a coluna e o `check` saem por migration inversa sem perda, porque nada depende deles ainda. Os comentários de catálogo são metadados e não afetam dado nenhum.

Nenhum artefato publicado, golden ou hash é tocado.

## Open Questions

- **O nome exato das classes no marcador** — `[retencao:C]` ou `[retencao:roster]`. Letra amarra ao §10 da política e quebra se ela renumerar; nome amarra ao conceito e exige uma tabela de correspondência. Decisão de forma, resolvível na tarefa que escreve a primeira migration, e não muda spec nem abordagem.
- **Se os prazos do §10 entram como constante de código agora** — a política os declara como padrões alteráveis por contrato, e não há nada que os aplique até a fatia 4. Inclinação: ficam só no ADR e na política até existir expurgo que os leia, para não criar constante que ninguém consulta.
