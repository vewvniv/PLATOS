## Why

A fatia 1.5 validou o **mecanismo** de matemática na folha — converter, empacotar como caixa, desenhar igual nos dois renderizadores — mas não o **caso predominante**. Das 28 questões originais da fixture, 7 têm matemática e **todas as sete são em linha**, escritas hoje como texto simples: `Qual e o valor de x na equacao 3x = 21?`.

§15 registra a 1.6 com dois gatilhos, e os dois continuam válidos:

- **Formal:** é bloqueadora da fatia 6. Chegar à geração de exatas por IA com a tipografia predominante nunca tendo passado pelo Layout Engine anularia o motivo pelo qual a 1.5 veio antes da 2.
- **De contrato:** `InlineBox` entra na medição de texto, e resolver esse tipo antes de a fatia **2a** congelar o contrato do `ExamPackage` evita reabrir a medição com o OMR já estabilizado sobre geometria publicada e hasheada.

O `LayoutProfile` entra junto porque mexe na **mesma medição**. ADR-0004 já separou as duas metades: o *campo* de perfil no cabeçalho do `LayoutMap` é da 2a, a *parametrização* é desta fatia. Fazer as duas em fatias diferentes obrigaria a regravar o golden duas vezes sem separar as causas — foi exatamente o que evitamos ao adiar o `LayoutProfile` durante D-1.5.9.

## What Changes

**Entrada**

- O enunciado passa a aceitar **marcador de fórmula em linha** no texto corrido, com o recurso declarado à parte. `Question.statement` continua `String`: a fixture segue legível e o diff de uma prova segue legível, que é o que mantém o contrato quase intacto para a 2a.
- Um marcador que referencie recurso inexistente, ou malformado, SHALL ser recusado — não desenhado como texto.

**Medição (KMP)**

- `InlineBox` — largura, altura e `baseline_offset` — entra na medição como **unidade atômica**, participando da quebra de linha como uma palavra que não pode ser partida.
- A linha que contém fórmula **cresce** para caber a caixa, arredondada à grade de 3 mm. `MeasuredText.height` deixa de ser `entrelinha × linhas` e passa a somar alturas por linha.
- Acima de um **teto declarado**, a fórmula em linha é recusada com erro que pede a forma em bloco. Sem teto, uma matriz 3×3 no meio de um parágrafo produziria uma linha de 15 mm cercada de linhas de 4,7 mm, e o autor só descobriria na impressão.

**`LayoutProfile`**

- `Sheet` e `TextStyle` passam a ser **parâmetros** de um perfil, em vez de constantes de objeto. O perfil padrão reproduz os valores atuais byte a byte.
- `CaptureGeometry` fica **fora**: bolha e marcador estão amarrados às tolerâncias de OMR do ADR-0001, e não há evidência de captura sob outra escala.

**Renderizadores**

- Uma linha deixa de ser um `DrawText` e passa a ser **várias primitivas na mesma linha de base** — trechos de texto e imagens —, todas com posição já resolvida pelo mapa.
- Isso **não** muda requisito de `print`: as primitivas são as que já existem, e o renderizador já é proibido de decidir posição. O que muda é a quantidade de primitivas que o mapa emite, que é comportamento de `layout-engine`.

**Verificação**

- Paridade, fidelidade e golden seguem valendo, agora com fórmula dentro do texto corrido.
- A fixture ganha questões com matemática em linha, e as 7 questões existentes que **descrevem** matemática em texto puro passam a usá-la.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `layout-engine`: a medição de texto aceita caixa atômica em linha com alinhamento de linha de base; a altura de linha deixa de ser constante; a recusa de entrada não suportada se estreita de novo, agora liberando fórmula em linha e mantendo imagem de enunciado e discursiva; a geometria da folha e a tipografia passam a vir de um perfil declarado.

`print` **não** entra. A primeira versão desta proposta o listava, e estava errado: desenhar dois `DrawText` e um `DrawImage` na mesma linha de base usa exatamente as primitivas que já existem, e o requisito vigente já obriga o renderizador a desenhar tudo que o mapa declara, nas posições declaradas, sem recalcular. Não há comportamento observável novo do lado do renderizador — inventar um requisito para justificar a capability listada seria o contrário do que a regra 3 quer.

## Impact

**Alterado**

- `packages/domain` — `TextMeasurer`, `MeasuredLine`, `MeasuredText`, `ExamDefinition`, `QuestionBlocks`, `LayoutEngine`, `Sheet`, `LayoutMapValidation`
- `apps/web` e `apps/android` — passam a receber mais primitivas por linha; nenhuma regra nova de posicionamento
- `fixtures/` — fórmulas em linha e as 7 questões que hoje descrevem matemática em texto
- `openspec/specs/layout-engine/spec.md` e `openspec/specs/print/spec.md`

**Golden** — regravado deliberadamente, como em D-1.5.4 e D-1.5.9. É a terceira vez.

**Dependências novas** — nenhuma. `tools/math` já converte e rasteriza; o que muda é a caixa ganhar `baseline_offset`, que o MathJax já expõe.

**Explicitamente NÃO alterado**

- **Corpo da questão como sequência de blocos** — fórmula em bloco seguida de mais enunciado. Exige o enunciado deixar de ser um campo e virar sequência, contrato maior que `InlineBox` e que não sai de graça junto com ele. Registrado no Aberto de §17 com fatia-limite `1.6+`.
- **Geometria de captura parametrizada** — ver acima, ADR-0001.
- **O campo de perfil no cabeçalho do `LayoutMap`** — é da fatia 2a (ADR-0004). Esta fatia produz o perfil; a 2a o declara no artefato publicado.
- **Imagem de enunciado e região discursiva** — seguem recusadas.
- **Publicação, `ExamPackage`, `item_asset`** — fatia 2a.
- **Nenhuma migration, nenhum endpoint**; `apps/api` não é tocada.
