# Cobertura — fatia 4b: a entrega do roster

Documento de cobertura da mudança OpenSpec `slice-4b-roster-entrega`. Registra **como** cada
verificação crítica foi vista falhar, o que ficou sem teste automático e por quê.

Escrito em 2026-09-12, em construção enquanto a fatia é implementada.

## Declaração dos conjuntos esperados — tarefa 1.3

**Escrita e comitada ANTES de injetar as mutações.** A âncora é este commit; os resultados vêm no
commit seguinte. O motivo de separar os dois é um erro de ancoragem da fatia 4a, em que a tabela de
conjuntos esperados entrou no *mesmo* commit dos resultados e portanto não provava precedência.

Os quatro cenários de `RosterQueryTest` (relatório `timestamp` 2026-09-11T23:03:36.790Z, 4 testes,
0 falhas):

1. `o roster de uma prova vem com token e nome`
2. `turma e matricula nao saem do servidor`
3. `prova publicada sem roster devolve lista vazia`
4. `roster de outra organizacao nao e devolvido`

### Mutação (A) — a consulta passa a devolver turma e matrícula

`RosterEntryDto` ganha `classGroup` e `enrollmentId`, e `findRoster` seleciona as duas colunas.

- **Vermelho esperado:** só o cenário 2.
- **Verde esperado:** 1, 3 e 4.
- **Mensagem esperada:** a do `assertTrue` da varredura, com o primeiro valor proibido da lista —
  `` `9Z-noturno` saiu do servidor: `` seguido do JSON da resposta.

A varredura só é capaz disto depois da correção em `6985fa0`: na primeira versão ela montava a
string nomeando os dois campos à mão e **sobreviveria** a esta mutação.

### Mutação (B) — `EXAM.ORGANIZATION_ID` sai do `where`

- **Vermelho esperado: nenhum.** Previsão explícita, com o raciocínio escrito antes de rodar:
  - `short_id` é **único globalmente** (`exam_short_id_unico`,
    `20260820190357_exam_tables.sql:21`), e não único por organização. Com `SHORT_ID` no `where`, a
    consulta já identifica no máximo uma prova sem precisar da organização.
  - A RLS de `exam` e `exam_roster` (`exam_member_select`, `exam_roster_member_select`) restringe às
    organizações em que o chamador tem `membership`. No cenário 4 o usuário **não** pertence à
    organização alheia, então a linha fica invisível pela RLS, e o cenário continua verde sem o
    predicado.
- **Verde esperado:** os quatro.
- **Se a previsão se confirmar, é achado a registrar, não a esconder:** a segunda barreira estaria
  sendo carregada sozinha pela RLS, e o predicado na consulta seria afirmação sem teste que a
  sustente.

**O cenário que falta, declarado agora e não depois do resultado.** Existe um caso em que o
predicado importa e a RLS não cobre, e ele é representável porque `membership` é N:N por invariante
arquitetural: um professor que pertence a **duas** organizações pede o roster com o id da
organização **errada** no caminho. A prova é da organização A; a RLS libera, porque o chamador é
membro de A; sem o predicado, a consulta devolve o roster de A sob o id de B.

- Cenário a acrescentar: `roster de prova de outra organizacao do mesmo professor`.
- **Vermelho esperado sob (B):** só ele.
- **Verde esperado sob (B):** os quatro atuais.
- Sem a mutação, ele deve ser verde com a consulta como está.

A ordem de execução declarada: rodar (B) com os quatro cenários e confirmar (ou refutar) a previsão
de zero vermelhos; registrar o resultado; acrescentar o quinto cenário; reinjetar (B) e medir de
novo.

## Resultados medidos — tarefa 1.3

Todas as rodadas com `--rerun-tasks`, e cada número lido do XML do relatório, com o `timestamp`
como âncora — nunca do código de saída e nunca da contagem sozinha.

### Mutação (A) — turma e matrícula passam a descer

Injetada em `RosterEntryDto` (dois campos novos) e em `findRoster` (duas colunas a mais).

| | declarado em `733359f` | medido |
|---|---|---|
| vermelho | só `turma e matricula nao saem do servidor` | **igual** |
| verde | os outros três | **igual** |
| totais | — | 4 testes, 1 falha, 0 erros |
| `timestamp` | — | 2026-09-11T23:05:31.421Z |

Mensagem da asserção, lida do relatório:

```
org.opentest4j.AssertionFailedError: `9Z-noturno` saiu do servidor:
[{"student_token":"tok-hlm","display_name":"Hildemar Peçanha","class_group":"9Z-noturno",
"enrollment_id":"2026-MAT-7702"},{"student_token":"tok-zrd","display_name":"Zoraide Buarque",
"class_group":"9Z-noturno","enrollment_id":"2026-MAT-7701"}]
```

Reversão conferida **rodando**: 4 testes, 0 falhas, `timestamp` 2026-09-11T23:06:06.107Z, árvore de
trabalho limpa por `git status --porcelain`.

### Mutação (B) — `EXAM.ORGANIZATION_ID` sai do `where`

| | declarado em `733359f` | medido |
|---|---|---|
| vermelho | **nenhum** | **nenhum** |
| verde | os quatro | os quatro |
| totais | — | 4 testes, 0 falhas, 0 erros |
| `timestamp` | — | 2026-09-11T23:06:35.672Z |

**Achado, registrado e não escondido:** com os quatro cenários que a tarefa 1.2 pediu, o predicado
de organização na consulta era afirmação sem teste que a sustentasse. A RLS o cobria inteiro, pela
razão declarada antes de rodar — `short_id` único globalmente mais `membership` como base da
política.

O quinto cenário, declarado antes da medição e acrescentado **com (B) ainda injetada**, para nascer
vermelho:

| | declarado em `733359f` | medido |
|---|---|---|
| vermelho sob (B) | só `roster de prova de outra organizacao do mesmo professor` | **igual** |
| verde sob (B) | os quatro atuais | **igual** |
| totais | — | 5 testes, 1 falha, 0 erros |
| `timestamp` | — | 2026-09-11T23:07:13.163Z |

```
org.opentest4j.AssertionFailedError: expected: <[]> but was:
<[RosterEntryDto(studentToken=tok-hlm, displayName=Hildemar Peçanha),
  RosterEntryDto(studentToken=tok-zrd, displayName=Zoraide Buarque)]>
```

A mensagem mostra o vazamento pelo que ele é: o roster de uma organização devolvido sob o
identificador de outra, para um chamador que a RLS aceita porque ele é membro da organização dona.

Reversão de (B) conferida **rodando**: 5 testes, 0 falhas, `timestamp` 2026-09-11T23:07:42.152Z.

### O que este par de mutações ensinou sobre o instrumento

As duas barreiras que o `design.md` chamou de "o padrão de autorização já está pronto" não eram
igualmente medidas. A RLS tinha cobertura própria desde a fatia da tenancy; o predicado na consulta
vinha por simetria com `listPublished` e `findPackage` — e **essas duas não têm cenário direto
nenhum**, só a suíte de rota. A mutação (B) não encontrou defeito no código: encontrou uma afirmação
do código sem oráculo, que é o que P16 chama de camada sem prova própria.

Item fora de escopo, com dono e fatia-limite (P19): `listPublished` e `findPackage` têm a mesma
forma e a mesma lacuna — o predicado de organização delas também sobreviveria à remoção sob a suíte
atual. **Dono:** esta base. **Fatia-limite:** a próxima fatia que tocar qualquer uma das duas
consultas. Não é "resolvido depois se der tempo": é adiado, registrado, com limite.
