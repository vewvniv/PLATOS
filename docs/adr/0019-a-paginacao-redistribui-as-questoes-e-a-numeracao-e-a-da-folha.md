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

A decisão do mantenedor, na mesma data: "O sistema tem que redistribuir as questões para evitar o
espaçamento em branco".

## Decisão

1. **A paginação pode tirar uma questão da ordem em que o professor a pôs, para não deixar branco.**
   - O objetivo continua o do §7: `Σ(sobra)² + penalidades`, e com ele menos páginas vêm primeiro.
   - **Tirar uma questão da ordem do professor passa a ser uma das penalidades.** A ordem dele é
     mantida, a menos que sair dela elimine branco que valha mais que a penalidade.
   - O valor da penalidade é calibrado na mudança que implementa, contra dois exemplos:
     - **tem de mudar:** a prévia com 124 mm em branco;
     - **não pode mudar:** uma prova cuja ordem já não deixa buraco.
2. **A numeração impressa é a da folha.** Cada questão é numerada pela posição final dela na folha,
   e o gabarito lista as objetivas com esse número. A decisão 7 da 5a ("numeradas pela posição na
   prova") continua valendo, e "posição" passa a ser a posição impressa. Em todo o resto, a questão
   é identificada por `question_id`, e o número é apresentação.
3. **O que não se separa continua sem se separar.** Os super-blocos do §7 se movem inteiros:
   - enunciado com alternativas;
   - enunciado com moldura;
   - texto-base com dependentes, que também mantêm a ordem interna.
4. **A ordem sai determinística.** A mesma definição, com o mesmo perfil, produz sempre a mesma
   ordem: o layout é uma função pura, e o hash do pacote depende dele. O desempate é a ordem do
   professor.
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
- **A ordem do professor vira preferência, e não restrição.** Uma sequência intencional, como da
  mais fácil para a mais difícil, pode ser alterada quando houver branco a eliminar. Questões que
  **precisam** ficar juntas são declaradas como texto-base com dependentes, e aí não se separam.

## Gatilho para reabrir

- **Professores pedirem ordem fixa.** Uma prova em ordem de dificuldade é o exemplo esperado. A
  saída seria uma opção de ordem fixa por prova, decidida em ADR novo.
- **A penalidade calibrada produzir reordenações que o mantenedor ache surpreendentes** nas provas
  reais.

## Como isto poderia ter falhado em silêncio

A numeração impressa é a da folha, mas um consumidor pode continuar usando a posição na definição.
Um relatório diz "questão 7" e aponta para outra questão impressa. O professor dá o retorno errado
ao aluno, e nenhum erro aparece.

A guarda é o número ter **um dono só**, que é o `LayoutMap` (P28), com um teste que embaralha a
ordem e confere o número em cada consumidor.

## Alternativas descartadas

- **Manter a ordem e aceitar o buraco**, mostrando o contador de páginas ao professor. Descartada
  pelo mantenedor.
- **Reordenar livremente, sem penalidade.** Qualquer ganho mínimo de branco embaralharia a prova
  inteira. O professor perderia a ordem dele por 3 mm, e toda regravação de golden viraria uma
  reordenação.
- **Deixar o professor reordenar à mão.** Transfere para ele um problema que o sistema resolve
  melhor, e que o mantenedor atribuiu ao sistema.
