## Context

Ver `proposal.md` — Why. O que este documento acrescenta é o estado medido que condiciona a forma:

- **Quatro rotas existem hoje** (`Routes.kt`): `/health`, `/me/organizations`,
  `/organizations/{orgId}/exams` e `/organizations/{orgId}/exams/{shortId}/package`. Nenhuma entrega
  roster.
- `ExamQueries` tem `listPublished` e `findPackage`, as duas com `EXAM.ORGANIZATION_ID.eq(...)` no
  `where` e chamadas sob `tenancy.asUser(userId)` — o padrão de autorização já está pronto e não
  precisa ser inventado.
- A rota do pacote usa `respondBytes` e **não** `respondText`, por decisão registrada: o
  `content_hash` foi calculado sobre UTF-8 sem BOM, e negociar charset na saída quebraria a
  conferência sem sintoma na tela.
- `exam_roster` guarda `student_token`, `display_name`, `class_group`, `enrollment_id` e
  `identification_mode`, com classe de retenção **C** declarada.
- ADR-0002 recusou um segundo hash sobre o roster, com a razão escrita: "ou o hash trava o roster — e
  perdemos a eliminação — ou ele é recalculado a cada mudança, e não garante nada".

## Goals / Non-Goals

**Goals:**

- Abrir o caminho para o roster chegar ao aparelho, com o mesmo regime de autorização das rotas que
  já existem.
- Entregar o **mínimo** que o consumidor usa.
- Não tocar o pacote nem a listagem.

**Non-Goals:**

- Não desenhar o cache, o gate de dois artefatos, o apagamento no aparelho nem o nome na tela — são
  a fatia **β**.
- Não inventar versionamento, ETag, TTL de servidor ou paginação sem consumidor que os peça.
- Não filtrar por modo de identificação na entrega.

## Decisions

### 1. Rota própria, no formato das duas que já existem

`GET /organizations/{organizationId}/exams/{shortId}/roster`, com `ContentNegotiation` e JSON —
e **não** `respondBytes`.

**Por que rota própria e não acoplada ao pacote.** A entrega do pacote existe para devolver os
**bytes exatos** sobre os quais o `content_hash` foi calculado (ADR-0008, ADR-0013). Pôr roster
naquele corpo exigiria envelope, e envelope anula aquela decisão; pôr num cabeçalho colocaria dado
pessoal num lugar que registros de acesso e proxies capturam com mais facilidade que corpo.

**Por que JSON negociado, ao contrário do pacote.** O roster **não** é hasheado, então não há
conferência de bytes para proteger — a razão que obrigou `respondBytes` ali não existe aqui. Usar
`respondBytes` por simetria acrescentaria serialização manual sem nada a ganhar.

### 2. A entrega leva token e nome, e mais nada

`class_group` e `enrollment_id` ficam no servidor.

**Não é minimização decorativa.** Cada campo que desce vira dado pessoal em cache no aparelho, e cada
um deles cai na lacuna da classe H que o §16 registra — enumerar e apagar. Matrícula é
identificador institucional forte; turma não identifica o aluno e o consumidor não a usa para dizer
de quem é a folha. Dado que não desce não precisa de regra de apagamento.

**Alternativa descartada: entregar o roster inteiro** "porque a β pode precisar". Isso é P18 ao
contrário — dado sem consumidor, e neste caso dado **pessoal** sem consumidor. Quando houver tela que
mostre turma, o campo entra com o requisito que o justifique.

### 3. Sem hash, sem ETag, sem TTL de servidor

A entrega devolve o estado corrente e nada mais.

ADR-0002 já recusou o hash, e a razão dele vale igual para ETag: qualquer validador forte sobre dado
que **deve** poder mudar ou trava a mudança ou não garante nada. O TTL que ADR-0002 menciona é do
**cache no aparelho**, e é decisão da β — não do servidor.

**O que isto custa, dito agora:** o aparelho não terá como saber se o roster mudou sem pedir de novo.
É aceito porque o roster é pequeno e a alternativa seria inventar versionamento sem medição. Se a β
mostrar que o custo importa, o veículo é uma mudança com o número na mão.

### 4. O modo de identificação não filtra a entrega

Se a organização está em `coded`, o roster **já contém** código ou apelido — o `check` do banco
garante isso na escrita (ADR-0012). Filtrar de novo na saída poria a mesma regra em dois lugares, que
é a duplicação que esta base recusa. O que a entrega faz é devolver o que está guardado.

### 5. `ContentNegotiation` já instalado, e o DTO segue o precedente

`Application.module` instala `ContentNegotiation { json() }`, e `ExamSummaryDto` é o precedente de
DTO de resposta em `http/dto/`. O DTO do roster mora ao lado, com os dois campos.

## Risks / Trade-offs

- **Dado pessoal numa rota nova** → O regime de autorização é o mesmo das existentes (autenticação +
  `asUser` + `organization_id` no `where`), e é ele que a suíte de rota exercita. O risco novo não é
  a rota: é a **cópia** que a β vai criar, e é lá que a regra de apagamento entra como requisito.
- **Roster grande numa resposta só** → Uma turma tem dezenas de linhas com dois campos curtos; não há
  medição que sugira paginação, e inventá-la agora seria número sem consumidor (P18). Se uma
  organização com centenas de alunos por prova aparecer, a medição vem antes da paginação.
- **"Indistinguível de inexistente" precisa valer para os dois casos** → Prova de outra organização e
  prova que não existe devolvem a mesma coisa; prova sem roster devolve **vazio**, que é diferente.
  Confundir os dois últimos faria a folha avulsa parecer erro, e é isso que os cenários separam.

## Migration Plan

1. Consulta antes da rota: `ExamQueries` ganha a leitura sob RLS; depois a rota a expõe.
2. Nenhuma migração de banco, nenhuma variável nova, nenhuma mudança em contrato existente.
3. **Reversibilidade**: reverter os commits remove a rota. Nada foi gravado, nada a limpar — a fatia
   só **lê**.

## Open Questions

Nenhuma. As duas que existiam — o que a entrega leva, e se ela declara validador — foram decididas
acima, com a razão registrada.
