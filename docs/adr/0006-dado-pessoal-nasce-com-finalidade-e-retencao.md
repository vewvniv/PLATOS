# ADR-0006 — Dado pessoal nasce com finalidade e classe de retenção; o gatilho da LGPD é a fatia 3

**Status:** aceito · **Data:** 2026-08-19 · **Fatia-limite:** 3 (primeiro piloto com turma real)
**Referências:** `ARQUITETURA-FINAL-v3.md` §16 (risco LGPD), §5 (`ExamPackage`), §11, ADR-0002

## Contexto

§16 listava a LGPD como o único risco sem encaminhamento, com o gatilho em "resolver antes do
primeiro contrato". O gatilho estava errado, e errado na direção perigosa: **dado real de menor entra
no sistema no primeiro piloto com turma real, fim da fatia 3, que precede qualquer contrato de
escola.**

Some-se que o Basic é *self-serve*. Não há escola para figurar como controladora: um professor pessoa
física operando dado de menor numa SaaS comercial é figura ambígua sob a LGPD, e esse é justamente o
produto de lançamento. A ambiguidade chega com o primeiro cliente, não com o primeiro contrato.

Hoje o schema tem apenas identidade e billing. Nenhuma tabela com dado de aluno existe — é o momento
mais barato para fixar a regra.

## Decisão

**Gatilho:** a base legal e a política de retenção precisam estar resolvidas **antes do fim da fatia
3**, não antes do primeiro contrato.

**Regra estrutural, que vira a invariante I5:** nenhum artefato imutável contém dado pessoal direto.
Operacionalizada por ADR-0002.

**Regra de processo, que não vira invariante:** toda tabela que armazena dado pessoal nasce com
finalidade declarada e classe de retenção. Fica como exigência deste ADR e critério de revisão de
migration, e **não** como invariante — não é verificável por teste, e invariante que nenhum teste
consegue reprovar enfraquece as que existem.

**Mitigação disponível desde já:** o modo sem identificação nominal — aluno como número ou apelido —
habilitado de graça pela separação do roster (ADR-0002). É o que compra tempo até haver parecer
jurídico.

## Consequências

- Transcrição de manuscrito e recorte de imagem **não** anonimizam por troca de chave: o conteúdo é o
  dado pessoal. Para eles vale retenção, não anonimização — e é a diferença que ADR-0003 depende que
  esteja clara.
- Toda migration com dado pessoal passa a ter um item de revisão a mais.
- Nada disso substitui parecer jurídico, que continua sendo trabalho não técnico e sem dono técnico.

## Alternativas descartadas

**Tratar tudo por anonimização.** Funciona para os fatos, que são chave + número; não funciona para
imagem de manuscrito, onde o dado pessoal é o próprio conteúdo.

**Fazer de "finalidade declarada" uma invariante.** Não é verificável mecanicamente. As invariantes
desta base valem porque cada uma reprova um desenho concreto.
