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
- [x] 2.3 Criar `LayoutProfile` com margens, colunas, medianiz, grade, corpo, entrelinha e teto de linha (D-1.6.5). Resultado: `Sheet` e `TextStyle` deixam de ser constantes de objeto e passam a vir do perfil.

  `object Sheet` **deixou de existir**; suas 29 referências passaram a sair do perfil. `LayoutEngine`, `QuestionBlockBuilder` e `Paginator` recebem `LayoutProfile` com o padrão como default, e `TEXT_WIDTH`/`OPTION_WIDTH` viraram funções do perfil, porque a largura da coluna passou a ser dele.

  **Uma verificação mudou de lugar, e não de existência.** `Block` afirmava no próprio `init` que a altura estava na grade — contra uma constante global, a única grade que existia. Com a grade vindo do perfil, `Block` não tem como saber qual é: quem confere passou a ser `Paginator`, que conhece o perfil. Há dois testes onde havia um: `bloco fora da grade e recusado ao paginar`, e `a grade cobrada ao paginar e a do perfil`, que usa um perfil de 5 mm para provar que a guarda não ficou afirmando 3 mm fixos.
- [x] 2.4 Provar que o perfil padrão não muda nada **antes** de qualquer fórmula em linha entrar na fixture: calcular a fixture atual sem perfil explícito e afirmar o golden byte a byte, nos três alvos (D-1.6.6). Resultado: cobre "Perfil padrão não muda o resultado".

  `perfil padrao produz exatamente o mapa de antes de o perfil existir` afirma duas coisas: que declarar o perfil padrão dá o mesmo mapa que não declarar nada, e que esse mapa é o golden **atual**, byte a byte. O golden não foi regravado nesta tarefa e não podia ser: o ponto é justamente que a metade "perfil" da fatia contribui com zero para a mudança que virá na 5.2.

  Acompanha `o perfil padrao reproduz a geometria que o Sheet fixava`, com os catorze números de §7 escritos à mão. O golden sozinho diria que algo mudou; ele não diria **qual constante**.
- [x] 2.5 Testar que um perfil de corpo maior muda quebras e alturas e **não** muda a geometria de captura. Resultado: cobre "Perfil com corpo maior muda a folha", e é o que impede a parametrização de ser decorativa.

  Com o corpo e a entrelinha dobrados, o mapa muda e a prova passa a ocupar mais páginas. E o par que fecha o sentido de D-1.5.9 e do ADR-0001: `perfil nao mexe na geometria de captura` afirma que quad, contagem e coordenadas normalizadas das bolhas ficam **idênticos** sob o perfil ampliado. Sem essa metade, "o perfil muda a folha" seria compatível com o perfil ter mexido no que o OMR depende.

  Mais quatro guardas do próprio perfil: grampo maior que a margem superior, teto menor que a entrelinha, zero colunas e grade zero são recusados na construção.

## 3. Medição: a linha vira sequência

- [x] 3.1 Converter `MeasuredLine` em sequência de trechos com deslocamento horizontal resolvido, mais ascendente e descendente da linha (D-1.6.3). Resultado: uma linha sem fórmula produz exatamente um trecho de texto e a entrelinha de hoje.

  `MeasuredLine` passou de `(text, width)` para `(runs, width, ascent, descent)`, e ganhou `text` como propriedade derivada — o que manteve o resto do engine compilando sem mudanca.

  A altura da linha e `max(ascendente dos trechos) + max(descendente)`, com o texto entrando como `ascent = entrelinha, descent = zero`. Nao e arbitrario: e exatamente a aritmetica que o engine ja fazia, onde a primeira linha de base fica uma entrelinha abaixo do topo do bloco. E por isso que uma linha sem formula continua medindo a entrelinha, e o golden nao se mexeu.
- [x] 3.2 Fazer `MeasuredText.height` somar as alturas das linhas em vez de multiplicar entrelinha por número de linhas. Resultado: cobre "Altura do parágrafo é a soma das linhas".

  `MeasuredText.height` virou `fold` sobre as alturas das linhas. `texto sem formula mede exatamente entrelinha vezes linhas` fixa a equivalencia com o calculo antigo — e o par dela, `altura do paragrafo e a soma das alturas das linhas`, mostra que a multiplicacao subestimaria um paragrafo com formula.
- [x] 3.3 Incluir `InlineBox` na quebra de linha como unidade indivisível, com a mesma largura que uma palavra ocuparia. Resultado: cobre "Fórmula em linha ocupa espaço no meio do texto" e "Fórmula não é partida entre linhas".

  Uma caixa **encerra** o trecho de texto corrente. O texto continua sendo medido em pedacos acumulados, e nao palavra a palavra somando o espaco: e o par de kerning na juncao que faz a diferenca, e medir de outro jeito mudaria a largura de linhas que nao tem formula nenhuma — ou seja, mudaria o golden por um motivo que nao e o desta fatia.
- [x] 3.4 Posicionar a caixa pelo `baseline_offset` declarado, com **uma** linha de base por linha. Resultado: cobre "Alinhamento à linha de base".

  A conversao acontece num ponto so: `y = baseline - (height - baselineOffset)`, dentro do laco que desenha os trechos. `o deslocamento declarado decide o quanto a caixa desce` afirma a consequencia observavel.

  **Uma expectativa minha estava errada e o teste a corrigiu.** Eu tinha escrito que descer a caixa nao mexeria no que ela tem acima da linha de base. Mexe: a altura total e a mesma, entao descer reduz a ascendente e aumenta a descendente, e a linha cresce. O teste agora afirma isso.
  - A conversão linha-de-base → topo continua num ponto único, como D-1.5.9 estabeleceu. Este é o segundo elemento posicionado por caixa numa folha regida por linha de base, e é aqui que aquele defeito voltaria.
- [x] 3.5 Fazer a linha crescer para caber a caixa, mantendo o arredondamento à grade no bloco e nunca por linha. Resultado: cobre "Linha com fórmula cresce" e "Grade continua no bloco".

  `so a linha da formula cresce` verifica os dois lados: a linha com caixa passa da entrelinha, e **toda** linha sem caixa continua medindo exatamente a entrelinha. Sem a segunda metade, uma implementacao que engordasse o paragrafo inteiro passaria.
- [x] 3.6 Recusar fórmula em linha acima do teto do perfil, com erro que aponta a forma em bloco (D-1.6.4). Resultado: cobre "Fórmula em linha alta demais", com um caso na borda exata do teto fixando o limite.

  Teto do perfil, com `formula com exatamente a altura do teto e aceita` fixando a borda — sem ela, um `>=` no lugar do `>` recusaria o caso limite e ninguem notaria. A mensagem aponta a forma em bloco pelo nome.
- [x] 3.7 Testar que a quebra não depende do conteúdo matemático, só das dimensões. Resultado: cobre "Layout não depende do conteúdo matemático em linha".

  Duas fixtures com referencias diferentes e dimensoes iguais produzem o mesmo mapa a menos da referencia.
- [x] 3.8 Estreitar a recusa de entrada não suportada: fórmula em linha passa, imagem de enunciado e discursiva continuam recusadas. Resultado: cobre os cinco cenários de "Recusa de entrada não suportada".

  A recusa se estreitou de novo: formula em linha passa, imagem de enunciado e discursiva continuam recusadas.

  Declarar formula em linha como **recurso embutido** segue recusado, mas a mensagem mudou de sentido: antes dizia "fora de escopo, e a fatia 1.6"; agora diz qual e o caminho certo — marcador no enunciado mais a caixa em `inline`. Mensagem que manda esperar por uma fatia que ja chegou e pior que nenhuma.

## 4. Conversão e renderizadores

- [x] 4.1 Expor o `baseline_offset` em `tools/math`, a partir do que o MathJax já traz no SVG, e levá-lo ao manifesto. Resultado: a mesma fórmula produz sempre o mesmo deslocamento.

  Sai do `viewBox`, sem API nova. O MathJax emite o SVG com a origem na **linha de base**: `viewBox="minX minY w h"` tem `minY` negativo para o que sobe, e `minY + h` e o que desce. Em `f-fracoes`, `viewBox="0 -1342 3102.4 2050"` da `-1342 + 2050 = 708` milesimos de em abaixo da linha de base.

  A profundidade acompanha a altura efetivamente rasterizada, pela mesma razao que a altura acompanha o pixel: vinda do em puro, a caixa do mapa e o retangulo do PNG discordariam por arredondamento e o alinhamento sairia por um fio.

  Os doze valores derivados, conferidos contra o que a notacao manda esperar:

  | formula | % abaixo da linha de base |
  |---|---|
  | `f-raizes` | 7,5% |
  | `f-trigonometria` | 8,5% |
  | `f-potencias` | 18,0% |
  | `f-fracoes` | 34,5% |
  | `f-matriz2` / `f-sistema` | 39,6% |
  | `f-matriz3` | 43,4% |
  | `f-somatorio` | 44,4% |

  Raiz quase nao desce, fracao desce um terco, somatorio e matriz — centrados no eixo matematico — descem quase metade. Os PNG ficaram **byte a byte identicos**: so o manifesto mudou.

  **Visto falhar.** Dois testes cobrem o valor, e eles nao sao redundantes: um afirma a faixa `0 <= deslocamento <= altura`, o outro afirma a ordem `raiz < fracao < somatorio`. Com o deslocamento forcado a zero de proposito, **so o segundo fica vermelho** — o de faixa passa, porque zero esta na faixa. Um deslocamento constante passaria pela verificacao obvia e so apareceria na folha impressa, com a formula flutuando fora da linha.
- [x] 4.2 Emitir as primitivas de uma linha composta no `LayoutMap`, com posição absoluta por trecho. Resultado: o mapa declara a sequência; nenhum renderizador ganha lógica de posicionamento.

  Uma linha deixou de ser um `DrawText` e virou um `DrawText` ou `DrawImage` por trecho, todos na mesma linha de base, com `x` absoluto resolvido pelo mapa.

  **O identificador de linha de trecho unico foi preservado de proposito.** Uma linha com um so trecho de texto continua sendo `q<id>-s<n>`; so quando ha mais de um trecho o sufixo ganha o indice. Sem isso, as 40 questoes da fixture mudariam de identificador e o golden desta fatia misturaria formula em linha com uma renomeacao em massa — exatamente o que a tarefa 2.4 existe para impedir.
- [x] 4.3 Confirmar que os dois renderizadores desenham a linha composta sem mudança de código de posicionamento. Resultado: se algum precisar decidir posição, o desenho de D-1.6.3 está errado e a tarefa reprova.

  **Nenhuma linha de renderizador foi tocada nesta fatia** — `git diff main...HEAD -- apps/web/src apps/android/src` volta vazio —, e os 12 testes do renderizador web seguem verdes.

  E o resultado que D-1.6.3 previa, e a tarefa foi escrita para poder reprovar: se algum dos dois tivesse precisado decidir posicao, o desenho da linha como sequencia de trechos com `x` absoluto estaria errado. Desenhar dois `DrawText` e um `DrawImage` na mesma linha de base usa as primitivas que ja existiam, e e por isso que `print` nao entrou como capability modificada na proposta.

## 5. Fixture e golden

- [x] 5.1 Converter as 7 questões da fixture que hoje **descrevem** matemática em texto puro para usar fórmula em linha, e acrescentar casos de borda: fórmula no início da linha, no fim, duas na mesma linha, e uma que força quebra. Resultado: o caso predominante das exatas passa a ser exercitado de verdade.

  As sete questoes que **descreviam** matematica em texto — `q01`, `q03`, `q07`, `q11`, `q16`, `q23`, `q26` — passaram a usa-la. Nove ocorrencias, porque duas questoes citam duas formulas.

  Os casos de borda entraram **dentro** dessas sete, e nao como questoes novas: acrescentar questao mexeria na grade de bolhas e na paginacao, e o golden desta fatia misturaria matematica em linha com uma prova de outro tamanho.

  | caso de borda | onde |
  |---|---|
  | marcador no inicio do enunciado | `q01` |
  | marcador no fim do enunciado | `q16` |
  | duas formulas na mesma linha | `q26` |
  | formula larga forcando quebra | `q07`, com `i-larga` de 35 mm |

  Nove fontes novas em `formulas.json`, com prefixo `i-` para separar de `f-`. Todas abaixo do teto de 9 382 um: a mais alta e `i-meio` com 6 773 um.

  **Duas vezes o mesmo erro meu, e vale registrar.** Escrevi as fontes LaTeX por linha de comando e o escaping do shell comeu barras: `\sqrt{144}` virou `sqrt{144}`, e `\frac{1}{2}` virou `"\frac..."` no JSON com **uma** barra — onde `\f` e *form feed*, um caractere de controle. O MathJax quebrou com `TypeError` cru, e a saida do build **nao dizia qual formula**.

  Cheguei a suspeitar do vazamento de estado global que a fatia 1.5 documentou. Era hipotese errada: a bisseccao mostrou `\frac{1}{2}` falhando **sem nada antes**, o que descarta estado. O conversor esta integro e os 17 testes dele passam com as 21 formulas — a garantia de independencia de ordem continua valendo.

  Fica anotado o que isso expos, fora do escopo desta tarefa: **entrada malformada derruba o conversor com `TypeError` em vez do `UnsupportedFormulaError` que o desenho promete, e sem dizer qual formula**. A falha e alta — nao ha degradacao silenciosa — mas nao e identificavel. Ver secao 7.
- [x] 5.2 Regravar o golden deliberadamente e registrar aqui o antes e o depois, com contagem de páginas e atribuição bloco→página. Resultado: a mudança fica auditável, e não confundível com regressão aceita por engano.

  | | antes | depois |
  |---|---|---|
  | `sha256` | `34a1810ca05b39a0…69275e65` | `15a55f0fa60ba116…` |
  | Tamanho | 68 175 bytes | 69 864 bytes |
  | Paginas | 4 | **4** |
  | Questoes que mudaram de pagina ou coluna | — | **4 de 40** |
  | Primitivas de imagem | 12 | **21** (12 em bloco + 9 em linha) |

  O impacto de paginacao ficou pequeno porque formula em linha ocupa espaco que o texto ja ocupava: das sete questoes convertidas, so as que ganharam linha a mais empurram vizinhas.

  **E a primeira mudanca de golden desta fatia.** Os grupos 2, 3 e 4 inteiros — perfil, reforma da medicao, deslocamento de linha de base — passaram com o golden byte a byte intacto em `34a1810c…`. E o que a tarefa 2.4 comprou: esta regravacao tem uma causa so, e ela e a fixture.

  `fixture de referencia traz formula em bloco` precisou separar as duas formas — contava toda primitiva de imagem e esperava 12. Ganhou par: `fixture de referencia traz formula em linha` afirma as nove ocorrencias e as sete questoes.

## 6. Verificação

- [ ] 6.1 Rodar a suíte completa nos três alvos, mais `./gradlew build`. Resultado: golden novo estável byte a byte em JVM, Node e Android.
  - `build` explicitamente, e não só as tarefas de teste: foi exatamente essa diferença que deixou passar a falha de dependência de lint na fatia 1.5.
- [x] 6.2 Medir fidelidade do documento nos dois PDFs, agora com fórmula dentro do texto corrido. Resultado: dentro de 0,05 mm, comparado com a base da tarefa 1.1.

  **A primeira execucao reprovou, e reprovou a ferramenta, nao a folha.** 116 verificacoes, 21 falhas — todas em formula em linha, **zero** em bloco, com a mesma ferramenta na mesma pagina. Desvios de 1 a 2,8 mm contra tolerancia de 0,05.

  A assinatura dizia o que era: `largura da tinta: observado 7,980 mm, declarado 5,673`. Tinta **a mais**, e nao tinta deslocada. Formula fora do lugar nao engorda; janela que alcanca o vizinho, sim.

  A janela era folga fixa de 1 mm nos quatro lados. Para formula em bloco isso nunca alcanca nada — ha 2,8 mm de branco acima, 7,8 mm abaixo e a coluna inteira na horizontal. Para formula **em linha** a palavra vizinha esta a fracao de milimetro na mesma linha de base, e 1 mm entra dentro dela.

  A folga passou a ser **metade da distancia ate a tinta mais proxima**, limitada ao mesmo teto de 1 mm. Em bloco nada muda, porque nao ha vizinho a 1 mm; em linha ela encolhe ate o necessario. Os primeiros dois pixels sao ignorados de proposito — o antialiasing da propria caixa transborda cerca de um pixel, e conta-lo como vizinho zeraria a folga.

  Resultado: **116 verificacoes, maior desvio 0,039 mm**, o mesmo patamar de sempre, e o pior elemento voltou a ser um marcador em vez de uma formula.
- [ ] 6.3 Medir paridade web × Android com fórmula em linha na folha. Resultado: dentro de 0,3 mm, com o emulador ou no CI.
- [ ] 6.4 Provar que a verificação continua capaz de falhar: deslocar de propósito uma fórmula **em linha** e confirmar que paridade e fidelidade acusam, com elemento e distância. Resultado: as duas saem com código 1; reverter em seguida.
  - A janela de medição precisa ser conferida para o caso em linha antes de se confiar nela. Esta base já produziu quatro verificações incapazes de falhar, e duas foram por janela mal dimensionada — uma alcançava o vizinho, outra recortava o próprio elemento. Uma fórmula em linha tem texto a milímetros nos dois lados, então é o caso mais apertado até agora.
- [ ] 6.5 Atualizar `docs/cobertura-fatia-1.md` com os cenários novos e como cada verificação foi vista falhar. Resultado: nenhum cenário da spec sem verificação.
- [ ] 6.6 Imprimir a folha com matemática em linha e conferir a olho, seguindo `docs/protocolo-medicao-impressa.md`. Resultado: registrado se a fórmula em linha assenta na linha de base sem parecer deslocada, e se a linha alta não abre buraco visível no parágrafo.
  - É a única verificação que nenhum teste automático substitui, e na fatia 1.5 foi ela que achou o defeito que nenhuma das outras podia achar. Alinhamento óptico de linha de base é exatamente o tipo de defeito sem oracle dentro do sistema.

## 7. Fora do escopo, encontrado no caminho

### Entrada malformada derruba a conversão com erro não identificável

`tools/math` promete, em D-1.5.6 e nos testes de recusa, que o que ela não entende falha com
`UnsupportedFormulaError` trazendo o identificador da fórmula. Isso vale para o que a **guarda de
origem** conhece — `\newcommand`, `\usepackage`, TikZ, comando indefinido.

Fora dessa lista, o MathJax pode lançar direto. Uma fonte com caractere de controle — um form feed,
no caso — produziu:

```
TypeError: Cannot read properties of null (reading '4')
    at Object.Other [as item] (.../BaseConfiguration.js:79:10)
```

Sem o identificador da fórmula, e com o `build.mjs` interrompido no meio sem dizer em qual das 21
ele parou. Foi preciso bissetar por fora para descobrir.

Não é degradação silenciosa — a falha é alta e nada é gravado, que é a parte que importa. Mas
"erro identificável" é o que o desenho promete, e aqui ele não entrega. A correção é pequena:
envolver a conversão e reetiquetar qualquer exceção que não seja `UnsupportedFormulaError` com o
identificador da fórmula. Fica registrado, e não corrigido nesta fatia, porque não é o escopo dela.

Vale também uma guarda de entrada: nenhuma fonte de fórmula deve conter caractere de controle.
