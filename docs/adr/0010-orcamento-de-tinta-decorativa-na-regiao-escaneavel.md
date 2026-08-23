# ADR-0010 — Tinta decorativa dentro da região escaneável tem orçamento declarado

**Status:** aceito · **Data:** 2026-08-21 · **Fatia-limite:** 2b
**Referências:** `ARQUITETURA-FINAL-v3.md` §7 (design da folha, mitigação do erro de transcrição), §8 (OMR), §16 (impressão dos ArUcos) · ADR-0001 · ADR-0007 · D-2b.3, D-2b.4

## Contexto

§7 lista três mitigações para o erro de transcrição do gabarito consolidado: faixa alternada a
4,5% de preto, agrupamento de 3 a 5 questões, e a letra da alternativa impressa em cinza **dentro
do círculo**. As três são certas para o produto — o salto de linha é o erro que custa nota a um
aluno que sabia a resposta.

Duas delas põem tinta **dentro da bolha que o OMR vai medir**. Uma bolha não respondida deixa de
ser papel branco e passa a ter faixa, letra, ou as duas.

O OMR não existe: ele é a fatia 3. O que existe agora, e vai ficar caro depois, é a folha: uma vez
impressa e distribuída, corrigir decoração significa reimprimir — o mesmo argumento que §16 usa
para pôr a impressão dos ArUcos nesta fatia.

Fixar aqui o limiar que separa "marcada" de "vazia" seria o erro que ADR-0007 descreve: um número
escolhido antes da medição, que depois justifica o que quer que tenha sido medido. Mas deixar a
decoração sem teto seria pior — a fatia 3 herdaria uma folha cuja tinta decorativa já invadiu a
faixa onde o limiar precisaria cair, e descobriria isso com folhas impressas.

## Decisão

**A grandeza é a cobertura:** a média de escuridão dentro do disco da bolha, normalizada, com 0 =
papel e 1 = preto pleno.

O `LayoutMap` declara, no cabeçalho da região escaneável, o orçamento de tinta decorativa e a
margem de contraste exigida. Ele viaja no artefato publicado porque quem o consome é a leitura
óptica, que roda offline contra um pacote imutável possivelmente produzido por uma versão anterior
do engine — uma constante no aplicativo concordaria com a folha por coincidência de versão.

| Grandeza | Valor | Papel |
|---|---|---|
| Cobertura decorativa numa bolha **não respondida** | **≤ 12%** | Teto que a folha não pode passar |
| Cobertura de uma bolha preenchida a caneta | **≥ 50%** | Piso a medir no papel |
| Corredor admissível para o limiar do OMR | **20% a 40%** | O que a fatia 3 herda |

**O que acontece se reprovar.** Se a medição no papel mostrar caneta abaixo de 50%, ou decoração
acima de 12%, **a decoração é que cede** — tom da letra, trama da faixa, ou a letra sai de dentro
do círculo, nessa ordem. A folha é cosmética; a leitura não é. Alterar estes números depois de
conhecer uma medição exige ADR novo que registre o resultado obtido, como ADR-0007 determina.

**Decoração e traço ocupam faixas de escuridão disjuntas.** Toda tinta decorativa fica do lado
claro do corte que as ferramentas de medição usam para achar traço; nenhuma medição de geometria
enxerga decoração, e a medição de decoração tem teto abaixo do traço. O piso de escuridão da
paridade é a definição operante de "decorativo": tinta que a paridade enxerga não é decoração, é
geometria.

## Consequências

- A fatia 3 escolhe o limiar do OMR dentro do corredor, com o número medido no corpus real, e não
  herda um limiar escolhido sem dados.
- A verificação do orçamento é feita sobre o documento **rasterizado**, por caminho que não
  compartilha código com o engine que produziu a geometria. Verificar a intenção declarada no mapa
  não satisfaz o requisito: o que o OMR lê é tinta, não declaração.
- Qualquer decoração futura dentro da região — sombreado de coluna, marca-d'água, moldura — nasce
  sujeita ao mesmo teto, sem discussão nova.
- O orçamento é por bolha, e não médio: uma bolha estourando o teto reprova, mesmo que a média das
  outras esteja folgada.

## Como isto poderia ter falhado em silêncio

Sem o orçamento, a folha sairia bonita e a decoração entraria por acréscimos pequenos e razoáveis —
um tom mais escuro para a letra ficar legível, uma trama mais forte para a faixa aparecer numa
impressora fraca. Nenhum deles quebra teste nenhum: golden compara o mapa consigo mesmo,
fidelidade compara o documento com o mapa, paridade compara os dois documentos entre si. Todas
continuariam verdes.

A falha apareceria na fatia 3, em campo, como acerto ou erro que ninguém marcou — indistinguível de
erro do aluno, sobre folha já impressa e distribuída. É o modo de falha mais caro que esta base
tem: silencioso, tardio, e sobre artefato imutável.

## Alternativas descartadas

**Nenhuma decoração dentro da bolha, com a letra fora do círculo.** Elimina o risco e a mitigação
junto. §7 põe a letra dentro do círculo porque é lá que ela reduz o salto de linha; fora dele, ela
vira mais uma coluna para o olho contar.

**Escolher o limiar do OMR agora.** É o que ADR-0007 proíbe, e sem corpus fotografado o número
seria uma opinião com aparência de medida.

**Guardar o orçamento como constante compartilhada no KMP.** Some no momento em que folha impressa
e aplicativo deixam de andar juntos — que é o modelo offline inteiro (§10).
