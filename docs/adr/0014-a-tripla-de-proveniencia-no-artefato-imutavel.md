# ADR-0014 — A tripla de proveniência entra inteira no artefato imutável, e a quebra de hash acontece uma vez

**Status:** aceito · **Data:** 2026-09-18 · **Fatia-limite:** antes da fatia 5
**Referências:** `ARQUITETURA-FINAL-v3.md` §2 (I3), §5 (o `ExamPackage`) · ADR-0008 · ADR-0009 · ADR-0013 decisão 4 · `docs/auditoria-2026-09-18-antes-da-fatia-5.md` §4.1 e §5.4 · `rigorous.md` §0

## Contexto

A invariante I3 (`ARQUITETURA-FINAL-v3.md:48`) é categórica:

> **I3 — Todo artefato gerado por IA carrega `prompt_version` + `model_id` + `params_hash`.**

`PackageMeta` carrega **dois** dos três. `params_hash` aparece duas vezes em toda a árvore —
`CLAUDE.md:38` e `ARQUITETURA-FINAL-v3.md:48` — e **zero** vezes em código, schema ou fixture.

E a KDoc do próprio `PackageMeta` afirma o contrário:

> "I3: presentes no contrato desde o inicio, vazios enquanto a prova for fixa. … a fatia 6 preenche
> estes campos **sem mexer no contrato** — que e exatamente o que I3 existe para garantir: quando a
> geracao chegar, **nao ha o que retrofitar**."

**A afirmação é falsa como escrita, e falsa no campo que mais custa.** `ExamPackage.toCanonicalJson()`
usa `encodeDefaults = true`: um campo novo com valor padrão **entra** no JSON canônico. Acrescentar
`params_hash` muda o `content_hash` de **todo** pacote. É precisamente o custo que motivou pôr os
outros dois cedo. Dois de três não é "não há o que retrofitar".

**A origem é uma contradição interna da arquitetura, e vale ser justo com a implementação.** §5 lista
o conteúdo de `meta` **sem** `params_hash`:

```
├── meta            exam_id, short_id, layout_engine_version, min_renderer_version,
│                   content_hash, fully_offline_gradable, prompt_version, model_id
```

A implementação seguiu §5. Não é desleixo: é um documento que se contradiz e uma implementação que
obedeceu à metade errada dele.

**A janela.** Há duas provas de conferência publicadas em produção e nenhuma turma real. Depois da
primeira turma, o mesmo trabalho opera sobre pacotes de que dependem folhas impressas e resultados
gravados.

## Decisão

### 1. §2 vence §5, e a contradição fica registrada em vez de apagada

Pela tabela de precedência do `rigorous.md` §0, a **invariante** vence a prosa descritiva do mesmo
documento. I3 é normativa; a lista do §5 é ilustrativa e estava incompleta.

A lista do §5 é corrigida para trazer `params_hash`. **A incompletude não se apaga** (P7): fica dito,
ao lado da correção, que a lista trouxe oito campos enquanto I3 exigia a tripla, que a implementação
seguiu a lista, e que foi isso que produziu o achado 4.1.

Isto **não** é substituição de decisão: I3 nunca mudou. É o registro descritivo alcançando o
normativo.

### 2. Os três campos são nulos enquanto não houver geração — e nulo não é vazio

Os três — `prompt_version`, `model_id`, `params_hash` — existem no contrato desde já e são **nulos**
enquanto a prova for fixa. Prova fixa não tem artefato de IA, e não há proveniência a declarar.

**`params_hash` cobre os parâmetros da chamada que produziu o artefato** — o que foi enviado ao
modelo além do prompt: temperatura, teto de tokens, semente, formato de resposta. Ele existe para que
dois artefatos produzidos com parâmetros diferentes sejam distinguíveis sem que seja preciso inferir
a partir do conteúdo, que é o que I3 quer e o que `prompt_version` e `model_id` sozinhos não dão.

**Nulo, e não string vazia.** O argumento não é novo nesta base: é o que a folha avulsa já fixou para
`student_token` — *"o servidor espera ausencia — vazio faria todas as avulsas da mesma prova
colidirem"* (`ScanActivity.kt:252`). Ausência e valor vazio significam coisas diferentes, e
colapsá-las num só valor produz leitura plausível e errada, sem sintoma. Um `params_hash` vazio passa
a significar "houve geração e o hash é vazio", que é um estado impossível e portanto um defeito
detectável; um nulo significa "não houve geração", que é o estado corrente de toda prova fixa.

### 3. A consequência aceita: pacotes já publicados passam a ser recusados, e isso é o correto

Acrescentar o campo muda o `content_hash` de todo pacote. Pacotes **já publicados** mantêm os bytes
que têm — `exam_package` é imutável, e ADR-0009 fixa que prova publicada tem um pacote e um só.

Um aplicativo atualizado passa a **recusá-los**, e o motivo é preciso: a **camada (b)** de ADR-0013
decisão 4 — fidelidade de interpretação. `toCanonicalJson(parse(bytes)) == bytes` deixa de valer,
porque `encodeDefaults = true` injeta `"params_hash":null` que não estava nos bytes publicados.

**Essa recusa é o comportamento correto, e não um dano a mitigar.** É exatamente o que a camada (b)
existe para pegar, e a alternativa seria pior: um pacote cujo hash confere e cujo parse perdeu um
campo produz nota plausível e errada, sem sintoma na tela. A recusa é **alta e não silenciosa** — o
aparelho diz que esta versão não interpreta o pacote, e pede atualização.

**O caminho para as provas já publicadas é o que ADR-0009 já manda:** publicar prova nova, com
`short_id` próprio. Não se republica prova sobre si mesma.

**Esta é a janela barata, e ela fecha.** Pré-lançamento, com duas provas de conferência publicadas.
Depois da primeira turma real há folhas impressas em circulação e resultados gravados contra aqueles
pacotes, e a mesma mudança deixa de ser um campo e passa a ser uma migração de artefato imutável —
que é a coisa que `exam_package` foi desenhado para tornar impossível.

### 4. `meta.exam_id` **não** é renomeado

O campo se chama `exam_id` e carrega o `short_id` (achado 5.4). §5 lista os dois como campos
distintos; o implementado tem só um, e `ExamPublication.publish` alimenta os dois do mesmo valor.

Renomear agora é tentador — o hash já vai quebrar, e o custo pareceria zero. **Recusado**, por duas
razões independentes:

- **`LayoutMap` também tem `exam_id`.** Renomear nos dois estenderia a quebra ao **golden do
  layout**, à folha de teste de impressão e a toda a cadeia de paridade e fidelidade. Seria um evento
  P23 muito maior que este, e com uma superfície de verificação que esta mudança não precisa tocar.
- **Renomeação misturada com mudança funcional é o que P25 proíbe.** Se a renomeação valer, ela é
  mudança própria, com o seu próprio P23.

**O que entra no lugar é uma asserção executável** de que `meta.exam_id`, o `short_id` da definição e
o campo de prova dentro do payload do QR são o mesmo valor. Hoje isso é verdade **por construção**,
dentro de um único ponto de escrita; o teste transforma "verdade por construção" em "verdade
afirmada". É o que a KDoc de `EXTRA_SHORT_ID` em `ScanActivity` já pedia sem ter: *"os dois são
iguais hoje, mas por um contrato implícito que nada nesta base prende"*.

**O nome continua errado, e passa a estar preso.** É a troca aceita, e ela fica escrita: o que se
recusa não é consertar o nome, é consertá-lo **junto** com outra coisa.

## Consequências

- O `content_hash` de todo pacote muda **uma vez**. Três fixtures e dois literais de hash acompanham,
  e P23 fecha na mesma sessão.
- As duas provas de conferência publicadas em produção deixam de ser legíveis pelo aplicativo
  atualizado. É o comportamento correto da camada (b), e o caminho de saída é publicar prova nova.
- A fatia 6 passa a poder preencher os três campos **sem mexer no contrato** — que é o que a KDoc
  afirmava e agora passa a ser verdade.
- A fatia 5 reabre o contrato do pacote (rubrica, `expected_lines`, região discursiva) e o hash
  quebra de novo — mas por **uma** razão, e não por duas empilhadas (P25).
- `meta.exam_id` continua com o nome errado, agora com uma asserção que impede os três valores de
  divergirem em silêncio.
- Um artefato novo e deliberadamente congelado entra na árvore:
  `fixtures/pacote-do-contrato-anterior.json`, que é o pacote do contrato antigo e **não** é
  regerado. Sem ele, a consequência da decisão 3 não teria como ser exercitada — depois da
  regravação não existe mais nenhum pacote do contrato antigo nesta base.

## Como isto poderia falhar em silêncio

**A regravação alcançar a geometria.** O comando que regrava o pacote regrava cinco artefatos, e dois
deles são `LayoutMap`. `params_hash` está no `ExamPackage` e não no `LayoutMap`, então nenhum layout
pode mudar — mas "não pode" só vale se alguém conferir. A guarda é o `git diff`: exatamente três
arquivos, nenhum `.layout.json`.

**O cenário da camada (b) medir a camada (a).** Um pacote do contrato antigo apresentado com o hash
**novo** seria recusado por integridade, e o teste passaria afirmando a coisa errada — é o
sombreamento de fixture que o `rigorous.md` §3 descreve, e que já aconteceu duas vezes nesta base. A
guarda é afirmar, no mesmo cenário, que aquele pacote **passa** na camada (a).

**Declarar paridade sem rodá-la.** A fixture do pacote está no caminho da paridade:
`apps/web/scripts/examPackage.ts` a lê e alimenta o PDF do lado web. Regravar o pacote e supor que a
paridade continua valendo é exatamente o que P23 proíbe.
