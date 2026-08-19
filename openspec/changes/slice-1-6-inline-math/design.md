## Context

Ver `proposal.md — Why`. O que a fatia 1 e a 1.5 deixaram pronto define quase todo o desenho desta:

- A medição de texto é o ativo central da base: `MeasuredLine(text, width)` e `MeasuredText(lines, height)` produzem o mesmo valor em três alvos, e é isso que permite comparar o `LayoutMap` byte a byte. Esta fatia mexe **dentro** desse código.
- `MeasuredText.height` é hoje `style.lineHeight * lines.size`. É a suposição que a fórmula em linha quebra.
- `LayoutEngine` já recebe `TextStyle` por construtor; `Sheet` e `CaptureGeometry` ainda são `object` com 20 referências em 3 arquivos.
- D-1.5.9 já resolveu o problema análogo em bloco, e a lição ficou registrada: **a conversão linha-de-base → topo acontece num ponto único**. A fórmula em linha é o segundo elemento posicionado por caixa numa folha regida por linha de base, e é exatamente onde aquele defeito voltaria.
- `tools/math` já converte e rasteriza. Falta expor o `baseline_offset`, que o MathJax já traz no SVG.

## Goals / Non-Goals

**Goals:**

- Matemática em linha na folha, com paridade e fidelidade nos mesmos números de hoje.
- `MeasuredText` continua função pura sobre inteiros, idêntica nos três alvos.
- O Layout Engine continua sem saber o que é matemática: ele empacota caixas.
- `LayoutProfile` existe, e o perfil padrão não muda um byte do golden.

**Non-Goals:**

- Corpo da questão como sequência de blocos. Ver `proposal.md`.
- Qualidade tipográfica além do que o MathJax entrega: sem ajuste ótico do espaço em volta da fórmula em linha, sem quebra dentro de fórmula.
- Parametrizar geometria de captura.

## Decisions

### D-1.6.1 — O marcador é um par de chaves duplas com identificador, e o texto continua o contrato

`Question.statement` continua `String`, com fórmulas referenciadas por `{{ref}}`. O recurso vem em campo próprio, `inline: Map<String, InlineFormula>`.

A alternativa — `statement` como lista tipada de trechos — elimina parser e escape por construção, e foi descartada por custo de contrato: reescreveria as 40 questões da fixture e entregaria à fatia 2a um `ExamPackage` com enunciado estruturado, num momento em que o objetivo é congelar o **menor** contrato possível. Marcador em texto mantém a fixture legível e o diff de uma prova legível, que é o que faz revisão humana de prova funcionar.

O custo é real e fica registrado: **parser é superfície de erro**. Mitigado por D-1.6.2.

*Alternativa descartada:* delimitador de um caractere invisível, por exemplo `U+FFFC`. Sobrevive melhor ao parsing, mas é indigitável e some no diff — e esta base acabou de gastar meia fatia diagnosticando um caractere invisível trocado pelo gerador de código.

### D-1.6.2 — Toda referência é resolvida antes do cálculo, e o não resolvido nunca vira tinta

O parser recusa: referência a recurso inexistente, chave não fechada, chave aninhada, e recurso declarado sem uso. A recusa acontece na validação da entrada, antes de qualquer medição.

O critério é o mesmo de D-1.5.6: **o que não é entendido falha, não é desenhado**. Uma referência malformada impressa como `{{f-eq1}}` na folha é a degradação silenciosa que a spec proíbe desde a fatia 1 — e é o modo de falha natural de um parser permissivo.

### D-1.6.3 — A linha vira uma sequência de trechos; a linha de base continua uma só

`MeasuredLine` deixa de ser `(text, width)` e passa a ser uma sequência de trechos — texto ou caixa —, cada um com o deslocamento horizontal já resolvido, mais a altura acima e abaixo da linha de base.

Todos os trechos de uma linha compartilham **uma** linha de base. É o que mantém o renderizador burro: ele recebe mais primitivas, com posição absoluta, e continua proibido de decidir posição. Nenhum requisito de `print` muda por causa disso.

A altura da linha é `max(ascendente dos trechos) + max(descendente dos trechos)`, com o texto contribuindo a entrelinha do perfil. Uma linha sem fórmula resulta exatamente na entrelinha de hoje, e é isso que permite ao perfil padrão preservar o golden.

*Alternativa descartada:* manter `MeasuredLine.text: String` e emitir a fórmula como primitiva separada, posicionada por medição do prefixo. Reintroduz medição em dois lugares — quem quebra a linha e quem posiciona a fórmula — e é a forma exata do defeito que D-1.5.9 corrigiu.

### D-1.6.4 — O teto de linha é declarado no perfil, e existe para o autor descobrir cedo

Fórmula em linha mais alta que o teto é recusada, com erro que aponta a forma em bloco. O teto entra no `LayoutProfile`, com padrão de **duas entrelinhas**.

Duas entrelinhas aceitam fração, raiz, expoente e subscrito — o vocabulário real de prova de ensino básico em linha — e recusam matriz e sistema, que são as estruturas que a fatia 1.5 pôs em bloco justamente por serem altas. Sem teto, uma matriz 3×3 no meio de um parágrafo produziria uma linha de 15 mm cercada de linhas de 4,7 mm, e ninguém avisaria: o autor descobriria na impressão, que é o modo de falha mais caro desta base — foi ele que reprovou a folha da fatia 1.5.

### D-1.6.5 — `LayoutProfile` cobre folha e tipografia; captura fica fora

O perfil carrega margens, colunas, medianiz, passo da grade, corpo, entrelinha e o teto de linha. `CaptureGeometry` — diâmetro de bolha, passo, marcador — **não** entra.

A razão não é escopo, é dependência: aquelas dimensões são o que o ADR-0001 dimensionou contra reescala de impressora e tolerância de OMR, com três impressoras medidas entre −3,4% e +4,7%. Torná-las variáveis sem evidência de captura sob outra escala transformaria uma garantia medida em parâmetro não verificado.

O **campo** de perfil no cabeçalho do `LayoutMap` é da fatia 2a (ADR-0004). Esta fatia produz o perfil e o consome; a 2a o declara no artefato publicado. A ordem importa: o campo é o que fica caro depois que houver pacote hasheado; o parâmetro não.

### D-1.6.6 — O golden muda uma vez, e o perfil padrão prova que muda pelo motivo certo

O golden é regravado por causa das fórmulas em linha na fixture. O `LayoutProfile` **não** deve contribuir para essa mudança: um teste calcula a fixture sem perfil explícito e afirma que o mapa é idêntico ao produzido com o perfil padrão.

É o mesmo cuidado de D-1.5.9, em que manter as duas correções separadas foi o que permitiu saber qual mudou o quê. Nesta fatia há duas mudanças grandes na mesma medição, e sem essa separação a regravação do golden vira ato de fé.

## Risks / Trade-offs

**Parser de marcador introduz modo de falha novo** → D-1.6.2 recusa em vez de desenhar, com cenário próprio. O risco residual é um marcador válido dentro de conteúdo legítimo — `{{` num enunciado sobre programação. Mitigado por escape declarado e por teste com o próprio marcador aparecendo como texto.

**`MeasuredText.height` muda de forma** → é a mudança mais arriscada da fatia, porque a paginação inteira depende dela. Mitigada por D-1.6.6: o perfil padrão precisa reproduzir o golden byte a byte **antes** de qualquer fórmula em linha entrar na fixture.

**Altura de linha variável interage com a grade** → o arredondamento continua no bloco, nunca por linha. Linha alta consome mais do bloco; o bloco segue múltiplo de 3 mm.

**Fórmula em linha e quebra de linha em três alvos** → é o código cuja identidade entre alvos a fatia 1 comprou. Todo teste novo de quebra vai para `commonTest`, e o golden julga os três.

**`LayoutProfile` sem consumidor real** → nasce com um perfil só, e perfil único é indistinguível de constante. Mitigado por um teste que calcula a fixture com corpo maior e afirma que as quebras mudam e a geometria de captura não. Sem ele, a parametrização é decorativa e ninguém descobre até alguém precisar de fonte ampliada.

## Migration Plan

Aditivo. Nenhuma migration, nenhum contrato exposto, `apps/api` não é tocada. Reverter é `git revert` mais regravar o golden anterior.

A ordem de implementação é de contrato para consumidor, como a regra 1 pede: `InlineFormula` e `LayoutProfile` primeiro, depois a medição, depois o engine, depois fixture e golden, depois verificação.

## Open Questions

Nenhuma. As três que existiam — forma da entrada, comportamento da altura de linha e alcance do perfil — foram fechadas em D-1.6.1, D-1.6.4 e D-1.6.5 antes de a proposta ser escrita.
