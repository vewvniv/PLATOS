# ADR-0004 — O `LayoutMap` declara o perfil tipográfico que o produziu

**Status:** aceito · **Data:** 2026-08-19 · **Fatia-limite:** 2a (campo) · 1.6 (parametrização)
**Referências:** `ARQUITETURA-FINAL-v3.md` §6 (Layout Engine), §7 (design da folha), §5 (`ExamPackage`)

## Contexto

O cabeçalho do `LayoutMap` traz `layout_engine_version`, `min_renderer_version`, `page_width`,
`page_height` e `font_sha256`. Registra a fonte, mas **não a tipografia**: corpo, entrelinha e passo
da grade não aparecem.

`LayoutEngine` já aceita `TextStyle` por parâmetro, com `TextStyle.BODY` apenas como padrão — então
duas provas com corpos diferentes produzem mapas com cabeçalhos indistinguíveis. `Sheet` e
`CaptureGeometry` ainda são constantes de objeto.

Prova em fonte ampliada é exigência real de escola, e a fatia 2 vai congelar geometria em
`ExamPackage` hasheado. Depois disso, um pacote publicado não permite reconstruir sob qual tipografia
foi produzido.

## Decisão

O `LayoutMap` passa a declarar um **identificador de perfil tipográfico** e os valores que o
definem — corpo, entrelinha, passo da grade —, junto do `font_sha256` que já existe.

O **campo** entra na fatia 2a, com o contrato do pacote. A **parametrização** de `Sheet` e
`CaptureGeometry` entra na 1.6, junto de `InlineBox`, que mexe na mesma medição.

Separar as duas é deliberado: o que fica caro depois da fatia 2 é a **ausência do campo** num
artefato publicado e hasheado, não a existência do parâmetro. O parâmetro pode chegar depois; o campo
não.

## Consequências

- Um pacote publicado passa a ser autodescritivo quanto à tipografia. Revalidar geometria de um
  pacote antigo deixa de depender de saber qual era o padrão na época.
- O golden muda ao acrescentar o campo. Mudança deliberada, registrada como as anteriores.
- Enquanto houver um perfil só, o campo é constante — e é justamente aí que ele é barato de
  introduzir.
- Geometria de bolha permanece fora da parametrização até que haja evidência de captura sob outra
  escala: ela é ligada às tolerâncias de OMR do ADR-0001.

## Alternativas descartadas

**Parametrizar tudo agora, na fatia 1.5.** Conflitava com D-1.5.9: as constantes que um perfil
parametrizaria são exatamente as que a correção de espaçamento mexeu, e o golden mudaria duas vezes
sem separar as causas.

**Deduzir a tipografia do `font_sha256`.** O hash identifica a fonte, não o corpo em que ela foi
composta.
