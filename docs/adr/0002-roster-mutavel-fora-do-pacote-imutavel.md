# ADR-0002 — O roster é mutável e vive fora do `ExamPackage` imutável

**Status:** aceito · **Data:** 2026-08-19 · **Fatia-limite:** 2a (contrato do pacote)
**Referências:** `ARQUITETURA-FINAL-v3.md` §5 (`ExamPackage`), §10 (sincronização), §11 (`exam_assignment`), §16 (LGPD)

## Contexto

§5 põe `assignments[] → student_id, nome impresso, turma` **dentro** do `ExamPackage`, que é
imutável, hasheado e copiado para dispositivos offline. §11 repete: `exam_assignment` é "fonte da
verdade: aluno, variante, token, nome impresso".

Isso coloca dado pessoal de menor de idade dentro de um artefato que, por construção, não pode ser
alterado e já foi distribuído. O direito de eliminação não alcança cópias imutáveis em tablets de
professores, e a alternativa — recolher e reemitir pacotes — é operacionalmente inviável numa
escola.

Nada disso existe em código ainda: o schema tem apenas identidade e billing. É o momento mais barato
que vai existir para decidir.

## Decisão

O `ExamPackage` mantém apenas `student_token` e `variant_id`. **Nome, turma e matrícula saem do
pacote** e passam a um **roster mutável**, entregue junto do pacote, cacheado com TTL e **fora do
`content_hash`**.

O `content_hash` continua cobrindo o que precisa ser imutável: itens, gabarito, geometria, scoring.
Não haverá um segundo hash sobre o roster. Integridade do impresso já é garantida pelo QR, que amarra
token ↔ variante na própria folha; um hash sobre dado que **deve** poder mudar seria uma contradição
declarada como garantia.

## Consequências

- O gate de pré-voo da fatia 4 passa a verificar dois artefatos em vez de um. Custo baixo: ele já vai
  verificar mais de uma condição, e a ausência de roster é uma falha diagnosticável.
- Eliminação de dado pessoal passa a ser uma operação possível: apaga-se o roster, e o pacote
  imutável sobrevive sem dado pessoal direto.
- Habilita de graça um **modo sem identificação nominal** — aluno como número ou apelido —, que é a
  mitigação disponível enquanto não houver parecer jurídico (ADR-0006).
- A folha impressa continua trazendo o nome, porque é o professor que a distribui. O que muda é onde
  o nome é **guardado**, não onde é **impresso**.

## Alternativas descartadas

**Manter o nome no pacote e confiar em retenção.** Retenção não resolve imutabilidade: o pedido de
eliminação chega antes do fim da retenção, e a cópia offline não é alcançável.

**Segundo hash sobre o roster.** Daria integridade ao que precisa mudar. Ou o hash trava o roster —
e perdemos a eliminação — ou ele é recalculado a cada mudança, e não garante nada.
