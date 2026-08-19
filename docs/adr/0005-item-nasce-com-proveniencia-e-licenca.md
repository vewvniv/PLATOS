# ADR-0005 — Todo item nasce com proveniência e licença

**Status:** aceito · **Data:** 2026-08-19 · **Fatia-limite:** 6 (geração por IA) · forma na 2a
**Referências:** `ARQUITETURA-FINAL-v3.md` §11 (banco de itens), §2 (I1), §5 (`ExamPackage`)

## Contexto

§11 define `item(visibility, curation_status, source_item_id, content_hash, embedding,
answer_capture_mode)`. `source_item_id` é linhagem **interna** — derivação de outro item do banco.
Não há campo para procedência **externa** nem para licença.

`visibility: public` mais geração por IA sobre textos-base publicados é exposição autoral direta. A
fatia 6 gera itens em volume, e retro-atribuir milhares de itens é caro e impreciso — o mesmo
argumento que sustenta I1.

## Decisão

Todo `item` nasce com **proveniência** e **licença** declaradas, e isso vira a invariante **I4**.

Proveniência cobre: autoria humana, geração por IA (com o que I3 já exige), ou origem externa com
referência. Licença cobre o que pode ser feito com o item — em particular se pode ir a
`visibility: public`.

Item sem proveniência não é gravado. A **forma do campo** é decidida na fatia 2a, junto do contrato
do pacote; a barreira executável entra na 6, que é quando itens passam a ser criados em volume.

## Consequências

- `visibility: public` passa a ser condicionado à licença, e não apenas à curadoria.
- Compartilhar item entre organizações fica decidível por dado, não por julgamento caso a caso.
- Um campo obrigatório a mais no caminho de criação de item.
- I3 continua valendo para o artefato de IA; I4 é sobre o **item**, e as duas se compõem: um item
  gerado por IA carrega ambas.

## Alternativas descartadas

**Registrar licença só nos itens públicos.** A decisão de tornar público vem depois da criação, e é
exatamente aí que a informação já não está disponível.

**Deduzir proveniência do `curation_status`.** São coisas diferentes: curadoria é qualidade,
proveniência é origem.
