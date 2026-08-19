## 1. Base de comparação

- [x] 1.1 Registrar os números atuais antes de tocar em qualquer coisa: hash e tamanho do golden, paridade, fidelidade nos dois PDFs e contagem da suíte por alvo. Resultado: valores anotados nesta tarefa — a fatia altera o golden de propósito, então a base precisa existir antes.

  Medido em 2026-08-19, na máquina de desenvolvimento, com `./gradlew build` e não apenas as tarefas de teste alvo a alvo — foi essa diferença que deixou passar a falha de dependência de lint na fatia 1.5.

  | Grandeza | Valor |
  |---|---|
  | `fixtures/prova-referencia.layout.json` | sha256 `34a1810ca05b39a01bc50befb925d4cdd01444d6db75eea9d8307b5269275e65`, 68 175 bytes |
  | `fixtures/prova-referencia.json` | sha256 `abec1bad621d72603c7a87c2874f5cf9c16318581d332a8c7f18776fd07bbb4b` |
  | Paridade web × Android | 176 de 176 elementos, 4 páginas, maior divergência **0,042 mm** em `qq37-f`, tolerância 0,3 mm |
  | Fidelidade do PDF web | 80 verificações, maior desvio **0,039 mm** em "marcador 2: borda superior" |
  | Fidelidade do PDF Android | 80 verificações, maior desvio **0,022 mm** em "formula qq30-f: altura da tinta" |

  Contagem da suíte, por alvo:

  | Alvo | Testes | Falhas |
  |---|---|---|
  | `packages/domain` JVM | 123 | 0 |
  | `packages/domain` Node/JS | 119 | 0 |
  | `packages/domain` Android (host) | 119 | 0 |
  | `apps/api` | 69 | 0 |
  | `apps/android` (unitário) | 9 | 0 |

  `./gradlew build :packages:domain:testAndroidHostTest --rerun-tasks` passa. Diferente da base da fatia 1.5, **não há falha pré-existente**: as três em Node foram corrigidas ao construir o surrogate em tempo de execução.

## 2. Contrato: entrada e perfil

- [x] 2.1 Estender a definição de prova com `InlineFormula` — largura, altura e `baseline_offset` — em campo próprio, referenciada por marcador no enunciado (D-1.6.1). Resultado: entrada versionada em JSON, com a fixture ainda legível.

  `Question.inline` é **mapa**, e não lista: a mesma fórmula pode ser citada duas vezes no mesmo enunciado e o recurso continua sendo um só. Há teste para as duas ocorrências.

  `baselineOffset` é **quanto da caixa fica abaixo da linha de base**, nunca negativo. Vale que o topo está em `baseline − (height − baselineOffset)` e a base em `baseline + baselineOffset` — posicionamento por aritmética inteira sobre a linha de base, que é a conversão única de D-1.5.9.
- [x] 2.2 Recusar referência não resolvida: recurso inexistente, chave não fechada, chave aninhada e recurso declarado sem uso (D-1.6.2). Resultado: cobre "Referência inexistente" e "Referência não vira texto impresso".

  `InlineMarkupTest`, 17 testes em `commonTest`, verdes nos três alvos. Sete recusas de gramática e resolução: chave não fechada, chave aninhada, referência fora do vocabulário, referência não declarada, recurso declarado e não citado, chave do mapa divergente da referência, dimensão não positiva e deslocamento fora da caixa.

  **O risco residual do design está coberto**: `\{{` escapa, e há teste com `{{` aparecendo como texto legítimo num enunciado sobre programação. A barra só é especial imediatamente antes de `{{`, então `6\2` continua sendo texto.

  Duas guardas contra vacuidade, no molde da fatia 1.5: um caso positivo — questão com fórmula declarada e citada é aceita — e as **bordas exatas** do deslocamento, 0 e a altura inteira. Sem as bordas, um `<` no lugar de `<=` passaria despercebido.

  O parser roda em `commonTest` de propósito: um `Regex` que se comportasse diferente em Kotlin/JS produziria enunciados diferentes na mesma prova. Os três alvos concordam.
- [ ] 2.3 Criar `LayoutProfile` com margens, colunas, medianiz, grade, corpo, entrelinha e teto de linha (D-1.6.5). Resultado: `Sheet` e `TextStyle` deixam de ser constantes de objeto e passam a vir do perfil.
- [ ] 2.4 Provar que o perfil padrão não muda nada **antes** de qualquer fórmula em linha entrar na fixture: calcular a fixture atual sem perfil explícito e afirmar o golden byte a byte, nos três alvos (D-1.6.6). Resultado: cobre "Perfil padrão não muda o resultado".
  - Esta tarefa é a que separa as duas mudanças grandes desta fatia. Sem ela, a regravação do golden na tarefa 5.2 vira ato de fé.
- [ ] 2.5 Testar que um perfil de corpo maior muda quebras e alturas e **não** muda a geometria de captura. Resultado: cobre "Perfil com corpo maior muda a folha", e é o que impede a parametrização de ser decorativa.

## 3. Medição: a linha vira sequência

- [ ] 3.1 Converter `MeasuredLine` em sequência de trechos com deslocamento horizontal resolvido, mais ascendente e descendente da linha (D-1.6.3). Resultado: uma linha sem fórmula produz exatamente um trecho de texto e a entrelinha de hoje.
- [ ] 3.2 Fazer `MeasuredText.height` somar as alturas das linhas em vez de multiplicar entrelinha por número de linhas. Resultado: cobre "Altura do parágrafo é a soma das linhas".
- [ ] 3.3 Incluir `InlineBox` na quebra de linha como unidade indivisível, com a mesma largura que uma palavra ocuparia. Resultado: cobre "Fórmula em linha ocupa espaço no meio do texto" e "Fórmula não é partida entre linhas".
- [ ] 3.4 Posicionar a caixa pelo `baseline_offset` declarado, com **uma** linha de base por linha. Resultado: cobre "Alinhamento à linha de base".
  - A conversão linha-de-base → topo continua num ponto único, como D-1.5.9 estabeleceu. Este é o segundo elemento posicionado por caixa numa folha regida por linha de base, e é aqui que aquele defeito voltaria.
- [ ] 3.5 Fazer a linha crescer para caber a caixa, mantendo o arredondamento à grade no bloco e nunca por linha. Resultado: cobre "Linha com fórmula cresce" e "Grade continua no bloco".
- [ ] 3.6 Recusar fórmula em linha acima do teto do perfil, com erro que aponta a forma em bloco (D-1.6.4). Resultado: cobre "Fórmula em linha alta demais", com um caso na borda exata do teto fixando o limite.
- [ ] 3.7 Testar que a quebra não depende do conteúdo matemático, só das dimensões. Resultado: cobre "Layout não depende do conteúdo matemático em linha".
- [ ] 3.8 Estreitar a recusa de entrada não suportada: fórmula em linha passa, imagem de enunciado e discursiva continuam recusadas. Resultado: cobre os cinco cenários de "Recusa de entrada não suportada".

## 4. Conversão e renderizadores

- [ ] 4.1 Expor o `baseline_offset` em `tools/math`, a partir do que o MathJax já traz no SVG, e levá-lo ao manifesto. Resultado: a mesma fórmula produz sempre o mesmo deslocamento.
- [ ] 4.2 Emitir as primitivas de uma linha composta no `LayoutMap`, com posição absoluta por trecho. Resultado: o mapa declara a sequência; nenhum renderizador ganha lógica de posicionamento.
- [ ] 4.3 Confirmar que os dois renderizadores desenham a linha composta sem mudança de código de posicionamento. Resultado: se algum precisar decidir posição, o desenho de D-1.6.3 está errado e a tarefa reprova.

## 5. Fixture e golden

- [ ] 5.1 Converter as 7 questões da fixture que hoje **descrevem** matemática em texto puro para usar fórmula em linha, e acrescentar casos de borda: fórmula no início da linha, no fim, duas na mesma linha, e uma que força quebra. Resultado: o caso predominante das exatas passa a ser exercitado de verdade.
- [ ] 5.2 Regravar o golden deliberadamente e registrar aqui o antes e o depois, com contagem de páginas e atribuição bloco→página. Resultado: a mudança fica auditável, e não confundível com regressão aceita por engano.

## 6. Verificação

- [ ] 6.1 Rodar a suíte completa nos três alvos, mais `./gradlew build`. Resultado: golden novo estável byte a byte em JVM, Node e Android.
  - `build` explicitamente, e não só as tarefas de teste: foi exatamente essa diferença que deixou passar a falha de dependência de lint na fatia 1.5.
- [ ] 6.2 Medir fidelidade do documento nos dois PDFs, agora com fórmula dentro do texto corrido. Resultado: dentro de 0,05 mm, comparado com a base da tarefa 1.1.
- [ ] 6.3 Medir paridade web × Android com fórmula em linha na folha. Resultado: dentro de 0,3 mm, com o emulador ou no CI.
- [ ] 6.4 Provar que a verificação continua capaz de falhar: deslocar de propósito uma fórmula **em linha** e confirmar que paridade e fidelidade acusam, com elemento e distância. Resultado: as duas saem com código 1; reverter em seguida.
  - A janela de medição precisa ser conferida para o caso em linha antes de se confiar nela. Esta base já produziu quatro verificações incapazes de falhar, e duas foram por janela mal dimensionada — uma alcançava o vizinho, outra recortava o próprio elemento. Uma fórmula em linha tem texto a milímetros nos dois lados, então é o caso mais apertado até agora.
- [ ] 6.5 Atualizar `docs/cobertura-fatia-1.md` com os cenários novos e como cada verificação foi vista falhar. Resultado: nenhum cenário da spec sem verificação.
- [ ] 6.6 Imprimir a folha com matemática em linha e conferir a olho, seguindo `docs/protocolo-medicao-impressa.md`. Resultado: registrado se a fórmula em linha assenta na linha de base sem parecer deslocada, e se a linha alta não abre buraco visível no parágrafo.
  - É a única verificação que nenhum teste automático substitui, e na fatia 1.5 foi ela que achou o defeito que nenhuma das outras podia achar. Alinhamento óptico de linha de base é exatamente o tipo de defeito sem oracle dentro do sistema.
