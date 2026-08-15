## Context

Ver `proposal.md — Why`. O que a fatia 1 deixou pronto define quase todo o desenho desta:

- O `LayoutMap` já tem a primitiva `image`, declarada e **recusada** pelos dois renderizadores. Ela foi escrita na fatia 1 sem consumidor, e agora ganha um.
- Paridade a 0,3 mm, fidelidade do documento a 0,05 mm e golden byte a byte entre três alvos já existem e vão julgar esta fatia sem código novo de verificação.
- D-1.10 já resolveu, para ArUco e QR, a pergunta que volta aqui: quando um elemento precisa ser desenhado igual nos dois renderizadores, quem decide os pixels é o KMP, e nunca a biblioteca de cada plataforma.
- A spec de `layout-engine` recusa matemática por escrito. Levantar essa barreira é o núcleo da mudança, e ela se estreita em vez de sumir.

§7 já fixou a estratégia tipográfica: LaTeX/MathML → SVG na publicação, "tratado como caixa de dimensão conhecida", o que converte tipografia matemática num problema de empacotamento. O que §7 não decide é **como o desenho chega ao papel de forma idêntica nas duas plataformas**.

## Goals / Non-Goals

**Goals**

- Fórmula em bloco na folha, com paridade e fidelidade nos mesmos números de hoje.
- Nenhum runtime novo no servidor.
- O Layout Engine continua sem saber o que é matemática: ele empacota caixas.

**Non-Goals**

- Fórmula em linha. Ver `proposal.md`.
- Integração com publicação real, `item_asset` e `ExamPackage` — fatia 2.
- Qualidade tipográfica além do que o MathJax entrega por padrão.
- Suporte a todo LaTeX. O que a fixture não exercitar, não está verificado.

## Decisions

### D-1.5.1 — O raster é derivado na conversão; o SVG segue sendo a forma armazenada

Os dois renderizadores recebem **os mesmos bytes de imagem**, e por isso a fórmula desenhada é idêntica por construção, não por concordância entre bibliotecas.

Desenhar SVG em cada plataforma com a biblioteca dela reintroduziria exatamente a divergência que a fatia 1 existe para eliminar: `pdf-lib` e o `Canvas` do Android interpretariam o mesmo arquivo por caminhos independentes, e o teste de paridade passaria a acusar diferença de biblioteca em vez de diferença de geometria.

Isso não contraria D33. O SVG continua sendo o que se armazena junto ao item; o raster é artefato de impressão derivado. A arquitetura já opera assim em outro ponto: D41 manda a publicação gerar uma versão print-safe em cinza a partir da imagem colorida armazenada. Forma armazenada e forma impressa já são coisas distintas.

O raster é gerado a 1200 dpi, mesma resolução em que a fidelidade do documento é medida, para que a fórmula não seja o elo mais fraco da folha.

*Alternativa descartada:* converter o SVG em primitivas de caminho no KMP e emitir `DrawPath`. É mais fiel ao vetor e evita resolução fixa, mas exige parser de SVG com transforms e referências de glifo — superfície grande de código novo, justamente na fatia cujo objetivo é não introduzir divergência. Fica registrada como caminho possível se a resolução do raster algum dia incomodar.

### D-1.5.2 — A conversão é ferramenta de build, não runtime de servidor

`tools/math` roda MathJax em Node e produz, por fórmula: o SVG, o raster e as dimensões. Alimenta a fixture, como `tools/parity` e o render do PDF já fazem.

§13 evita um segundo runtime **no servidor**, e é isso que fica preservado — o Ktor não passa a depender de Node. Node já existe no projeto como ferramenta (`apps/web`, `tools/parity`), então a dependência não é nova em natureza, só em uso.

Quando a fatia 2 construir a publicação, ela decide onde essa conversão roda de verdade. Antecipar isso agora seria construir integração para um pipeline que ainda não existe.

*Alternativa descartada:* implementar tipografia matemática em Kotlin, para rodar no KMP. Ordens de grandeza mais caro que tudo que a fatia 1 custou, sem retorno próximo.

### D-1.5.3 — Dimensões vêm declaradas; o Layout Engine não as descobre

A entrada traz largura e altura da fórmula em micrômetros, já resolvidas pela conversão. O engine arredonda a altura para a grade e reserva a caixa.

O engine não abre o SVG, não lê o PNG e não mede nada da fórmula. Isso mantém o cálculo do `LayoutMap` como função pura sobre inteiros (D-1.2) e preserva a comparação byte a byte entre alvos: nenhum alvo precisa decodificar imagem para calcular geometria.

### D-1.5.4 — O golden muda, e isso é decisão, não efeito colateral

Acrescentar fórmula à fixture altera a geometria da folha de referência, então o golden do `LayoutMap` é regravado deliberadamente, no mesmo commit, como D-1.9 prevê.

É a primeira vez que o golden muda desde que foi criado. Vale registrar o antes e o depois na tarefa, para que a mudança seja auditável — um golden regravado sem justificativa é indistinguível de uma regressão aceita por engano.

## Risks / Trade-offs

**Raster preso a uma resolução** → a 1200 dpi a fórmula empata com a precisão em que a fidelidade é medida, e a impressora doméstica medida no ADR-0001 fica muito abaixo disso. Se algum dia incomodar, D-1.5.1 registra o caminho vetorial.

**Peso do arquivo** → cada fórmula vira um PNG. Numa prova com muitas fórmulas isso cresce, e a fatia 2 vai empacotar isso em `ExamPackage`. §5 já determina que imagens não entram no JSON e vão para o Storage por referência; medir o peso nesta fatia dá números para aquela decisão.

**MathJax mudar de saída entre versões** → a versão fica fixada no `package-lock.json` e o golden pega qualquer mudança de dimensão. Uma atualização de MathJax que mexa no traçado sem mexer nas dimensões passaria despercebida pelo golden, mas seria pega pela fidelidade do documento se deslocar a caixa.

**A barreira de entrada não suportada afrouxar demais** → o requisito modificado estreita a recusa em vez de removê-la, e ganha cenário próprio para fórmula em linha. Sem isso, a barreira viraria letra morta na próxima fatia.

## Migration Plan

Aditivo. Nenhuma migration, nenhum contrato exposto, nenhuma mudança em `apps/api`. Reverter é `git revert` mais regravar o golden anterior.

## Open Questions

- Qual conjunto de LaTeX a fixture deve exercitar: fração, raiz, somatório, matriz? Decidível ao montar a fixture; amplia cobertura sem mudar desenho.
- O raster deve ser cinza ou preto e branco puro? §8 diz que a captura do gabarito é sempre monocromática, mas a fórmula fica fora de região escaneável. Decidível na conversão.
