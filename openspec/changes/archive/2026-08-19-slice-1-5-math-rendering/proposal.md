## Why

A fatia 1.5 vem antes da 2 no roadmap (`ARQUITETURA-FINAL-v3.md` §15) com uma justificativa de uma linha: "exatas antes de gerar conteúdo de exatas". Ela existe para que o Layout Engine saiba lidar com fórmula **antes** de a fatia 2 congelar geometria em `ExamPackage` com hash — o mesmo raciocínio que fez a fatia 1 vir primeiro.

Hoje isso está barrado por escrito. A spec de `layout-engine` recusa explicitamente "conteúdo matemático a ser tipografado ou imagem", e essa recusa foi implementada de propósito para não produzir folha silenciosamente degradada. Esta fatia levanta essa barreira para fórmula em bloco, e só para ela.

O ponto difícil não é tipografar — §7 já resolveu isso ao mandar converter para SVG e tratar a fórmula como "caixa de dimensão conhecida", transformando tipografia matemática num problema de empacotamento. O ponto difícil é **desenhar a mesma fórmula nos dois renderizadores sem divergir**, que é exatamente o problema que a D-1.10 enfrentou com ArUco e QR.

## What Changes

**Conversão (ferramenta de build, não runtime de servidor)**
- `tools/math` converte LaTeX e MathML em SVG com MathJax em Node, e deriva o raster de impressão. Roda offline, alimentando a fixture, como já fazem `tools/parity` e o render do PDF.
- Nenhum runtime novo entra no servidor. A integração com a publicação real pertence à fatia 2, que é quem cria o `ExamPackage`.

**Domínio KMP**
- A definição de prova passa a aceitar um recurso de fórmula por questão, com o SVG e o raster derivados já resolvidos e suas dimensões declaradas.
- O Layout Engine posiciona a fórmula como bloco próprio, com altura alinhada à grade de 3 mm, e a inclui no bloco indivisível da questão — enunciado, fórmula e alternativas continuam andando juntos (D34).
- A recusa de conteúdo não suportado passa a valer para fórmula **em linha**, imagem e discursiva, e não mais para fórmula em bloco.

**Renderizadores**
- Ambos passam a desenhar `DrawImage`, primitiva que já existe no `LayoutMap` e hoje é recusada pelos dois.
- Os mesmos bytes de raster vão para os dois lados, então a paridade é exata por construção, e não por concordância entre bibliotecas.
- Os bytes chegam pelo mesmo caminho que a fonte embarcada já usa na fatia 1: asset versionado, embutido por task de build para o KMP e lido de assets no Android.

**Verificação**
- Paridade e fidelidade seguem valendo, agora com fórmula na folha.
- A fixture de referência ganha questões com fórmula, e o golden é regravado deliberadamente.

## Capabilities

### New Capabilities
Nenhuma.

### Modified Capabilities
- `layout-engine`: aceita fórmula em bloco como caixa de dimensão conhecida, com altura na grade e dentro do bloco indivisível da questão; a recusa de entrada não suportada se estreita para fórmula em linha, imagem e discursiva.
- `print`: os renderizadores passam a desenhar imagem, com a exigência de que os bytes desenhados sejam exatamente os declarados no mapa.

## Impact

**Criado**
- `tools/math/` — conversão LaTeX/MathML → SVG → raster, com MathJax
- `fixtures/` — questões com fórmula e os assets derivados

**Alterado**
- `packages/domain` — modelo de entrada, Layout Engine, validação
- `apps/web` e `apps/android` — desenho de `DrawImage`
- `openspec/specs/layout-engine/spec.md` e `openspec/specs/print/spec.md`

**Dependências novas**: MathJax e `@resvg/resvg-js`, ambas como ferramenta de build em Node. §13 evita um segundo runtime **no servidor**, e é isso que fica preservado: a conversão não entra no caminho do Ktor. Justificativa no `design.md` — o MathJax em D-1.5.2, o rasterizador em D-1.5.8, que existe porque o mupdf do projeto não lê SVG.

**Explicitamente NÃO alterado**
- **Fórmula em linha**, no meio do texto corrido. Exigiria caixas com alinhamento de linha de base dentro da quebra de linha — justamente o código cuja identidade entre alvos a fatia 1 acabou de garantir.
  - Este é o recorte menos confortável desta fatia, e o `design.md` o registra com nome próprio. Matemática em linha é o caso **predominante** em prova de ensino básico: das 28 questões da fixture de referência, 7 têm matemática e todas as sete são em linha. Fica como fatia **1.6**, com gatilho explícito — precisa existir antes da fatia 6, que gera conteúdo de exatas por IA.
- **Imagens de enunciado** e a versão print-safe em cinza (D41). A primitiva de imagem passa a existir, mas o fluxo de imagem do item é fatia posterior.
- **Publicação, `ExamPackage`, hash e `item_asset`** — fatia 2.
- **Região discursiva**, colunas adaptativas, densidade em três níveis.
- **TexTeller e qualquer OCR de fórmula** (D42, D43) — isto aqui é geração de fórmula para impressão, não leitura.
- Nenhuma migration, nenhum endpoint; `apps/api` não é tocada.
