# ADR-0019 — A paginação redistribui as questões para não deixar branco, e a numeração é a da folha

**Status:** aceito · **Data:** 2026-09-25 · **Fatia-limite:** antes da primeira prova com questão larga publicada
**Referências:** `ARQUITETURA-FINAL-v3.md` §7 (paginação, super-blocos, economia de papel), §8 (completude com chips numerados), §15 (fatia 7, variantes) · ADR-0017 · `Pagination.kt` · `design.md` da `slice-5a-regiao-discursiva`, decisão 7

## Contexto

O §7 fixa a paginação:

> Medir → agrupar em super-blocos indivisíveis (enunciado+alternativas; enunciado+moldura;
> texto-base+dependentes com penalidade) → **DP minimizando `Σ(sobra)² + penalidades`** → posicionar
> regiões → emitir. O quadrado da sobra distribui o vazio em vez de empurrá-lo para o fim.

**A programação dinâmica de hoje não muda a ordem.** Ela coloca "os `i` primeiros blocos" em slots
de coluna, sempre em trechos contíguos (`Pagination.kt`, `solve`). *Conferido por leitura.* Ela
espalha o branco entre as páginas, mas nunca leva uma questão para antes de outra.

Até o ADR-0017, isso bastava: todo bloco cabia numa coluna, e o branco por slot era pequeno. Com a
questão de largura de página, um bloco grande que não cabe no fim de uma página abre um buraco que
nenhuma questão posterior pode preencher. Na prévia desenhada pelo renderizador web em 2026-09-25:
- a questão 10 tem 129 mm: é discursiva larga, com imagem;
- ela não coube depois das questões 7 a 9;
- sobraram **124 mm em branco na página 2**, cerca de 46% da altura útil.

A prévia usa um paginador simplificado, e não esta DP. Mas o buraco não depende do paginador: com a
ordem preservada, tudo o que vem depois da questão 10 tem de ficar depois dela.

**A ordem impressa nunca foi a ordem da definição.** O pacote guarda, por variante, o "mapa
posição física → `item_id`" (§5), e a fatia 7 entrega a randomização (§15). O mantenedor lembrou
que a randomização da ordem de questões e de alternativas é escolha do professor. *O §5 e o §15
foram conferidos por leitura.* O que é novo aqui é só o motivo de mover: evitar branco.

A decisão do mantenedor, na mesma data: "O sistema tem que redistribuir as questões para evitar o
espaçamento em branco". E o critério, nas palavras dele: "não deve haver espaço em branco se, nesse
espaço, for possível acomodar de maneira IDEAL questões, não é pra acomodar de maneira forçada, mas
sim de maneira que o fluxo não fique" desorganizado.

## Decisão

1. **Não sobra, no meio da prova, espaço onde uma questão caberia de maneira ideal.**
   - **"Ideal"** é a questão inteira, com a geometria e o espaçamento que ela teria em qualquer outro
     ponto da folha, no fluxo normal de leitura.
   - **Encaixe forçado é proibido.** Nada é comprimido, encolhido ou partido para caber: nem o
     espaço entre blocos, nem a imagem, nem as linhas da discursiva, nem o corpo do texto.
   - **Espaço no fim da última página não é buraco**, porque não há mais nada para pôr ali.
   - Branco menor que qualquer questão restante é inevitável. Ele é espalhado pelo `Σ(sobra)²` do
     §7, como hoje.
   - **A ordem do professor é o desempate.** Entre as disposições sem buraco preenchível e com o
     menor número de páginas, fica a que menos altera a ordem dele. Uma prova que já não deixa
     buraco sai na ordem dele.
   - **O fluxo de leitura fica inteiro.** Dentro de cada faixa, a leitura é a coluna da esquerda e
     depois a da direita, e a numeração segue esse fluxo, sem salto.
   - A mudança que implementa mostra o critério com dois exemplos:
     - **tem de mudar:** a prévia com 124 mm em branco, se alguma questão restante couber ali;
     - **não pode mudar:** uma prova cuja ordem já não deixa buraco.

   *Redação corrigida em 2026-09-25, antes do merge, depois da explicação do mantenedor. A versão
   do commit `cc6c8e4` pesava o branco contra uma penalidade por sair da ordem. Com isso, um buraco
   onde cabe uma questão podia ficar, se a penalidade ganhasse. O critério é o encaixe ideal, e a
   ordem só desempata.*
2. **A numeração impressa é a da folha.** Cada questão é numerada pela posição final dela na folha,
   e o gabarito lista as objetivas com esse número. A decisão 7 da 5a ("numeradas pela posição na
   prova") continua valendo, e "posição" passa a ser a posição impressa. Em todo o resto, a questão
   é identificada por `question_id`, e o número é apresentação.
3. **O que não se separa continua sem se separar.** Os super-blocos do §7 se movem inteiros:
   - enunciado com alternativas;
   - enunciado com moldura;
   - texto-base com dependentes, que também mantêm a ordem interna.
4. **A ordem sai determinística.** A mesma definição, com o mesmo perfil, produz sempre a mesma
   ordem: o layout é uma função pura, e o hash do pacote depende dele.
5. **O professor vê a ordem final antes de publicar**, na mesma prévia em que vê as páginas (§7,
   contador de páginas ao vivo; tela da fatia 6).

## Consequências

- **A DP do §7 muda de natureza.** Distribuir uma sequência fixa em slots é O(N²). Escolher também
  a ordem é um problema de empacotamento, sem solução exata viável para N = 60. A mudança que
  implementa usa uma busca heurística, determinística, e mede o tempo. "Com N ≤ 60 blocos é O(N²),
  milissegundos" deixa de valer como está escrito.
- **Todo número que o professor vê sai de um lugar só**: gabarito, chips de completude do §8,
  relatórios e revisão. Esse lugar é o número impresso, declarado no `LayoutMap`. Os resultados
  continuam atribuídos por item e por habilidade (I2), e nada neles muda.
- **As variantes da fatia 7 já embaralham a ordem.** Cada variante é paginada por si, e a
  redistribuição é mais uma razão para a ordem ser da variante, e não da definição.
- **A ordem do professor é desempate, e não restrição.** Uma sequência intencional, como da mais
  fácil para a mais difícil, pode ser alterada quando houver buraco preenchível. Questões que
  **precisam** ficar juntas são declaradas como texto-base com dependentes, e aí não se separam.

## Gatilho para reabrir

- **Um cliente pedir ordem fixa**, por exemplo uma prova em ordem de dificuldade. A saída seria uma
  opção de ordem fixa por prova, decidida em ADR novo.
- **O critério de encaixe ideal produzir, em provas reais, disposições que o mantenedor ache
  forçadas**, que é o que o critério existe para impedir.

## Como isto poderia ter falhado em silêncio

A numeração impressa é a da folha, mas um consumidor pode continuar usando a posição na definição.
Um relatório diz "questão 7" e aponta para outra questão impressa. O professor dá o retorno errado
ao aluno, e nenhum erro aparece.

A guarda é o número ter **um dono só**, que é o `LayoutMap` (P28), com um teste que embaralha a
ordem e confere o número em cada consumidor.

## Alternativas descartadas

- **Manter a ordem e aceitar o buraco**, mostrando o contador de páginas ao professor. Descartada
  pelo mantenedor.
- **Reordenar sempre que o `Σ(sobra)²` diminuir.** Qualquer ganho mínimo embaralharia a prova
  inteira. O professor perderia a ordem dele por 3 mm, e toda regravação de golden viraria uma
  reordenação.
- **Pesar o branco contra uma penalidade por sair da ordem.** Era a primeira redação deste ADR. Com
  ela, um buraco onde cabe uma questão pode ficar, se a penalidade ganhar. O mantenedor quer o
  contrário: se cabe de maneira ideal, não fica buraco.
- **Encaixar à força**, comprimindo espaçamento ou encolhendo imagem e pauta para caber. Isso é
  justamente o que o mantenedor proibiu.
- **Deixar o professor reordenar à mão.** Transfere para ele um problema que o sistema resolve
  melhor, e que o mantenedor atribuiu ao sistema.
