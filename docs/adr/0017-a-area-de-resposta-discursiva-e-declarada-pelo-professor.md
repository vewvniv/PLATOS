# ADR-0017 — A área de resposta discursiva é declarada pelo professor: linhas e largura, sem valor padrão

**Status:** aceito · **Data:** 2026-09-25 · **Fatia-limite:** antes da primeira prova com discursiva publicada
**Referências:** `ARQUITETURA-FINAL-v3.md` §7 (paginação, área discursiva — D32, D35), §15 (fatia 6, autoria) · ADR-0016 · `design.md` da `slice-5a-regiao-discursiva`, decisão 6

## Contexto

O §7 fixa o D35:

> **Área discursiva dimensionada pela rubrica:** `expected_lines` da rubrica define a altura da
> moldura. A IA gera a questão e a rubrica; a rubrica define o espaço; o espaço condiciona a
> resposta; a resposta é avaliada contra a mesma rubrica. Uma cadeia só, sem decisão manual. Nunca
> maior que uma página — se a rubrica pede mais, a questão vira itens (a), (b), (c).

A 5a o implementou assim, *conferido por leitura*:
- **As linhas vêm da rubrica.** São a soma dos `expected_lines` dos critérios
  (`QuestionBlocks.kt:170`).
- **A região mora sempre na coluna** (decisão 6 da 5a). A largura de página ficou adiada porque o
  paginador distribui blocos por slot de coluna (`Pagination.kt:47`), e não sabe ocupar dois slots
  de uma vez.
- **Uma moldura maior que a coluna é recusada.**

O §7 já prevê o bloco que atravessa as colunas: "Colunas: **adaptativo** — 2 por padrão, blocos
largos atravessam, 1 quando houver muito conteúdo largo" (D32).

Em 2026-09-25, depois de ver a folha da fixture discursiva, o mantenedor decidiu que o professor, na
tela do aplicativo:
1. decide quantas linhas cada questão discursiva tem;
2. decide se ela ocupa uma coluna ou a largura inteira da página;
3. e, na largura inteira, o fluxo das colunas é preservado antes e depois da questão.

Foi exposto a ele que a largura de página **custa mais papel** do que a coluna para o mesmo espaço
de escrita. São cerca de 15 mm de página a mais por questão, *calculado e não medido*, porque a
faixa dos marcadores e do QR passa a ocupar a largura inteira. A resposta dele: "'O padrão' não
existe aqui, o professor é quem deve decidir se quer ocupando 1 coluna ou 2, não sabemos da
necessidade de cada professor."

## Decisão

1. **Toda questão discursiva declara o número de linhas e a largura** (`coluna` ou `página`).
   **Nenhum dos dois tem valor padrão.** Uma definição de discursiva sem um deles é recusada na
   entrada, como a discursiva sem rubrica já é.
2. **`expected_lines` continua na rubrica, e deixa de dimensionar a moldura.** Ele diz quanto cada
   critério espera de resposta, e isso é informação de correção (5c e 8). A tela de autoria pode
   mostrar a soma ao professor como referência, mas o que vai para a definição é o número que ele
   escolheu. A publicação nunca infere o número de linhas a partir da rubrica.
3. **A questão de largura de página preserva o fluxo das colunas.** A página se divide em faixas
   horizontais:
   - a questão larga, com enunciado e região, ocupa uma faixa sozinha;
   - o que vem antes e o que vem depois dela continua em duas colunas, e cada faixa se equilibra
     por si;
   - dentro de uma faixa de duas colunas, a leitura segue como hoje, a coluna da esquerda e depois
     a da direita.

   Este ADR não decide a ordem em que as questões aparecem, só a forma da faixa.

   É o "blocos largos atravessam" do §7 (D32). O que este ADR acrescenta é quem declara que a
   discursiva é larga: o professor.
4. **O teto continua o do §7, "nunca maior que uma página".** O bloco inteiro, com enunciado e
   região, precisa caber numa página na largura escolhida. O que não cabe é recusado com erro que
   identifica a questão, e nunca é partido.

## Consequências

- **O D35 perde dois pedaços:** "a rubrica define o espaço" e "sem decisão manual". O resto da
  cadeia fica, e a resposta continua avaliada contra a mesma rubrica.
- **A tela é da fatia 6** (autoria, §15). O web ainda não tem editor de prova: `apps/web/src` tem só
  o renderizador (*conferido por leitura*). Até a fatia 6, as duas escolhas são escritas na
  definição da prova, que é como toda prova é feita hoje.
- **O rascunho gerado por IA na fatia 6 não publica sem as duas escolhas.** A geração pode propor
  valores, mas a definição só aceita o que o professor declarou. Isso é atrito aceito, e é a
  consequência direta de não haver padrão.
- **O paginador aprende faixas.** É mudança de algoritmo, com golden e P23 próprios. A guarda é que
  **a faixa não muda nada onde não há questão larga**: dada a mesma ordem de blocos, uma prova sem
  questão larga pagina exatamente como hoje.
- **Onde os dois campos moram, e se o pacote os carrega, é decidido pelas mudanças que
  implementam.** O pacote já carrega o resultado deles na geometria da região.
- **Nenhum pacote publicado muda de leitura.** A geometria de cada região viaja no `LayoutMap` do
  pacote (ADR-0009), e não há prova com discursiva em produção (ADR-0016, "Consequências").

## Gatilho para reabrir

**A autoria da fatia 6 mostrar que publicar em volume exige um valor padrão**, por exemplo numa
prova inteira gerada por IA. Nesse caso, o padrão entra por um ADR novo, com a origem dele
declarada, e nunca como valor silencioso do contrato.

## Como isto poderia ter falhado em silêncio

Com um valor padrão no contrato, seja "coluna" ou a soma dos `expected_lines`, o sistema decide pelo
professor sem que ele perceba. Ele só descobre a decisão na folha impressa, com a turma esperando.
Foi exatamente assim que este ADR nasceu: o desperdício da folha discursiva só apareceu quando o
mantenedor olhou o PDF pronto para imprimir. Com a declaração obrigatória, a escolha fica visível
antes da publicação.

## Alternativas descartadas

- **Manter o D35 como está.** Para ganhar espaço, o professor teria que mexer nos `expected_lines`
  da rubrica. Isso mistura informação de correção com layout, e a rubrica passaria a mentir sobre o
  que cada critério espera.
- **Um valor padrão com opção de trocar**: coluna, e linhas iguais à soma dos `expected_lines`.
  Descartado pelo mantenedor, porque não se sabe a necessidade de cada professor.
- **Largura automática**, com o motor escolhendo entre coluna e página pelo número de linhas. Foi
  descartada pela mesma razão. Além disso, a folha mudaria de forma sem que o professor tivesse
  escolhido.
