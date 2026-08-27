# Cobertura de cenários — base legal e modo sem identificação nominal

Mapa de cada cenário da spec delta de `openspec/changes/lgpd-base-legal-e-modo-sem-identificacao/`
para a verificação que o cobre, e **como ela foi vista falhar**.

Esta mudança tem uma característica que decide como ela é testada: **o que ela protege não tem
consumidor ainda.** Não há tela de cadastro de aluno, não há roster preenchido, e o único caminho de
escrita é a publicação de prova. Uma regra sem consumidor é a que mais fácil se escreve errada e
mais tarde se descobre — quando já há nome de menor no banco. Por isso as verificações são contra
**Postgres real**, e não contra a aplicação: o que precisa valer é o armazenamento, pela mesma razão
que o isolamento por organização é RLS e não filtro escrito em Kotlin.

## `exam-package` — 5 cenários, 5 cobertos

| Cenário | Verificação |
|---|---|
| Organização recém-criada nasce em modo codificado | `IdentificationModeTest.organizacao recem-criada nasce em modo codificado`, com `insert` que **não** informa o modo |
| Matrícula recusada em modo codificado | `IdentificationModeTest.matricula e recusada em modo codificado`, conferindo o nome da restrição na mensagem |
| Matrícula aceita em modo nominal | `IdentificationModeTest.matricula e aceita em modo nominal` — o par positivo, sem o qual o anterior passaria com uma restrição que recusa tudo |
| O modo declarado é recuperável | `IdentificationModeTest.o modo da organizacao e recuperavel junto do roster` |
| Trocar de modo não toca no artefato publicado | `ExamPublicationTest.trocar o modo de identificacao nao muda o hash do pacote` |

Três verificações que a spec não pedia e que o desenho trouxe junto:

| O que | Verificação |
|---|---|
| Turma continua permitida nos dois modos | `IdentificationModeTest.turma continua permitida nos dois modos` — turma não identifica um aluno, e uma restrição que a recusasse junto estaria protegendo o que não precisa |
| Virar codificado falha enquanto houver matrícula | `IdentificationModeTest.virar codificado falha enquanto houver matricula no roster` — a transição insegura é impossível por construção |
| Virar codificado propaga para o roster quando é seguro | `IdentificationModeTest.virar codificado funciona quando nao ha matricula`, conferindo o `on update cascade` |

## `identity` — 4 cenários, 4 cobertos

| Cenário | Verificação |
|---|---|
| Tabela com dado pessoal sem declaração | `RetentionDeclarationTest.tabela sem comentario e acusada pelo nome` |
| Classe que a política não define | `RetentionDeclarationTest.classe que a politica nao define e acusada, com as admitidas na mensagem` |
| Tabela sem dado pessoal | `RetentionDeclarationTest.tabela sem dado pessoal declara nenhum e passa` |
| A guarda reage a uma declaração removida | `RetentionDeclarationTest.a guarda reage a uma declaracao removida` — o par, com e sem a declaração |

Mais duas que existem por causa do que a guarda de RLS já sofreu:

| O que | Verificação |
|---|---|
| O catálogo real está limpo | `toda tabela de public declara finalidade e classe de retencao`, sobre as oito tabelas |
| Uma consulta vazia não passa por não ter olhado nada | `o piso reprova um catalogo vazio`, e o piso de contagem no teste do catálogo |

## Como cada verificação foi vista falhar

### A restrição do modo

| Defeito introduzido | Quem acusou | O que os outros disseram |
|---|---|---|
| `check (identification_mode = 'nominal' or enrollment_id is null)` trocado por `check (true)` | três: `matricula e recusada em modo codificado`, `virar codificado falha enquanto houver matricula no roster` e `roster com matricula em organizacao codificada e recusado com frase` | os outros 103 verdes, `matricula e aceita em modo nominal` entre eles — o par positivo não distingue nada sozinho |
| `default 'coded'` trocado por `default 'nominal'` | **um só**: `organizacao recem-criada nasce em modo codificado`, com `expected: <coded> but was: <nominal>` | todos os outros verdes |

**A segunda linha é a que importa, e é por causa do "um só".** Trocar o padrão não quebra nada: o
sistema funciona igual, as provas saem iguais, os testes de publicação passam. O que muda é que toda
organização nova passa a guardar nome civil de menor por omissão — e a descoberta viria numa
auditoria, sobre dado já coletado. Um único teste separa o sistema que a política descreve do sistema
que a contradiz, e é por isso que ele existe.

### A guarda de retenção

| Defeito introduzido | Quem acusou |
|---|---|
| `comment on table public.exam_roster` removido da migration | `toda tabela de public declara finalidade e classe de retencao`, com `[exam_roster sem comentario: falta finalidade e classe de retencao]` |
| A consulta apontada para um schema vazio (`pg_toast` no lugar de `public`) | o **piso**: `esperava ao menos 8 tabelas, vi 0` |

A segunda é a armadilha que `ConnectionRoleTest` documenta ter tido na versão anterior dele: uma
consulta que volta vazia produz lista de violações vazia, e o teste passa sem ter olhado nada. O piso
é o que separa "nada errado" de "nada visto", e ele foi visto separando.

## O que esta mudança deliberadamente não verifica

| O que | Por quê |
|---|---|
| Que `display_name` contém um código, e não um nome civil | Não é decidível pelo conteúdo do campo. Uma checagem que tentasse — só dígitos, sem espaço, tamanho máximo — reprovaria "Ana B." e aprovaria "Maria". O sistema declara o modo e não afirma o que não sustenta; quem fecha esse lado é a coerção na interface (política §3.5), da fatia da tela |
| Que a finalidade declarada corresponde ao uso real | A declaração é verificável e é o que a guarda cobre; a correspondência não é, e o ADR-0012 registra a distinção. É por isso que a exigência é guarda de construção e não invariante — as invariantes continuam sendo cinco |
| Expurgo, anonimização e pedido de eliminação | Operam sobre dado durável, que só passa a existir com o sync da fatia 4 |
