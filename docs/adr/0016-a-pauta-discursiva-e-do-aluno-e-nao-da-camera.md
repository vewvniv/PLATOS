# ADR-0016 — A pauta discursiva é do aluno, e não da câmera: 7 mm, em cinza claro

**Status:** aceito · **Data:** 2026-09-25 · **Fatia-limite:** antes da primeira folha discursiva impressa (tarefa 4.1 da `slice-5b-1-o-aparelho-reconhece-a-discursiva`, pausada para esperar esta decisão)
**Referências:** `ARQUITETURA-FINAL-v3.md` §7 (geometria da folha; "letra da alternativa impressa em cinza dentro do círculo"), §16 (acurácia em manuscrito) · ADR-0007 · ADR-0009 · ADR-0010 · ADR-0017 · ADR-0018 · `design.md` da `slice-5a-regiao-discursiva`, decisão 4

## Contexto

A tabela de geometria do §7 fixa:

> | Pauta discursiva | 8,6 mm (generosa: manuscrito espremido é o pior inimigo da leitura) |

A 5a implementou esse valor em `EssayGeometry.PAUTA` (`EssayGeometry.kt:20`), como linha **preta**
de 0,15 mm, e a spec de `layout-engine` repete o número na altura da área de resposta.

**O 8,6 mm nunca foi medido**, nem em papel nem contra a leitura de manuscrito. A razão escrita ao
lado dele é sobre leitura: a do professor e, a partir da fatia 8, a da transcrição. A medição dessa
leitura é a linha "Acurácia em manuscrito" do §16, que vence na fatia 5 e não foi paga. *Conferido
por leitura: `grep` por `8,6` e `PAUTA` na árvore acha o número só no domínio de layout, nos testes
dele e na documentação.*

Em 2026-09-25, o mantenedor viu a folha da fixture discursiva que a tarefa 4.1 da 5b-1 mandava
imprimir, e decidiu duas coisas:
- **o espaçamento:** o espaço de escrita está mal aproveitado, e **7 mm** é o espaçamento ideal. O
  argumento é de produto: cada discursiva custa papel, uma prova com várias discursivas vira mais
  páginas por aluno, e isso desincentiva o professor a usar a ferramenta;
- **o tom:** a pauta vai em **cinza claro**, "melhor até para dar destaque ao que o aluno escrever.
  É como as letras dentro das bolinhas do gabarito, são importantes apenas para quem está fazendo a
  prova, e não para a câmera".

**A analogia já é regra registrada.** O ADR-0010 separa decoração de traço por faixa de escuridão:
- a região declara `decorative_tone_max`, que hoje é 500‰ nas regiões da fixture;
- a paridade põe o piso de escuridão acima dele (`darknessFloorOf`, `compare.mjs:258`);
- "tinta que a paridade enxerga não é decoração, é geometria".

A letra dentro da bolha está em 400‰, do lado decorativo. *Conferido por leitura.*

O efeito do espaçamento, *calculado e não medido*: a área de resposta de 5 linhas passa de 43 mm
para 35 mm, e a de 7 linhas, de 60,2 mm para 49 mm. São 18,6% a menos de altura por linha.

## Decisão

**A pauta é guia para o aluno, e não geometria para a captura.** As duas propriedades dela seguem
disso:

1. **Espaçamento de 7 mm**, fixo, o mesmo em toda região discursiva.
2. **Cinza claro, do lado decorativo do ADR-0010.**
   - O tom da pauta fica **abaixo** do `decorative_tone_max` da região. A pauta nunca entra numa
     medição de geometria, e a captura, que recorta a área declarada, recebe a letra do aluno
     destacada sobre linhas claras.
   - A moldura continua preta. Ela delimita a área para o aluno e não é pauta.
3. **O valor exato do tom e da espessura sai do papel, e não desta mesa.** A linha precisa ser vista
   pelo aluno numa impressora de escola, e uma linha fina em cinza vira pontilhado de retícula. O
   critério, que é visível para o aluno e abaixo do teto decorativo, é registrado antes da impressão
   que decide (ADR-0007).
4. **A pauta não vira parâmetro**, nem do perfil nem da questão. O professor controla o espaço pelo
   número de linhas (ADR-0017), e um parâmetro sem quem escolha outro valor seria número sem
   consumidor (P18).

## Consequências

- **Decoração também tem de ser vista desenhada.** Se a pauta fica abaixo do piso da paridade, a
  medição de traço de `compare.mjs` deixa de enxergá-la. Um renderizador que não a desenhasse
  passaria verde. É o modo de falha da faixa da 2b, e a saída é a mesma de lá: a paridade mede a
  pauta **por tom**, como mede a faixa. A mudança que implementa só fecha depois de ver essa medição
  falhar com a pauta omitida num dos renderizadores.
- **A forma da primitiva é da mudança que implementa.** Hoje nada emite linha cinza. O retângulo de
  traço não tem tom, e o retângulo preenchido é recusado acima do teto de trama de 80‰
  (`LayoutMapValidation.kt`, `FLAT_TONE_CEILING`). Há duas saídas:
  - **tom no traço**, uma capacidade nova nos dois renderizadores, que sobe `min_renderer_version`
    (D24);
  - **uma linha fina preenchida**, com a validação passando a distinguir linha de área chapada, como
    já distingue o tom de um glifo.
- **Nenhum pacote publicado muda de leitura.** O `LayoutMap` de um pacote traz a pauta como
  primitivas de posição absoluta, e a captura recorta a `answer_area` declarada sem consultar a
  pauta (ADR-0009).
  - *Conferido por leitura:* nenhum uso de `PAUTA` fora de `packages/domain/.../layout`.
  - Não há prova com discursiva em produção. As duas publicadas são objetivas, *lido no banco pelo
    mantenedor em 2026-09-24* (`docs/cobertura-slice-5a-regiao-discursiva.md`, seção "As provas em
    produção"), e não relido para este ADR.
- **A mudança que implementa regrava o golden da fixture discursiva** e fecha paridade e fidelidade
  na mesma sessão (P23). A spec de `layout-engine` muda por ela, e não por este ADR.
- **O risco de leitura continua aberto, e é o mesmo de antes.** O 8,6 mm também não tinha medição.
  O corpus de manuscrito da fatia 5, se for coletado em folha nova, passa a medir a pauta que vai
  para a sala de aula.

## Gatilho para reabrir

- **A medição de acurácia em manuscrito do §16**, com o critério registrado antes dela (ADR-0007).
  Se ela reprovar, e a leitura dos erros apontar a pauta, este ADR reabre com o número. Exemplos
  dessa causa: traço que cruza a linha de cima, ou letra comprimida na altura. Uma reprovação por
  outra causa não o reabre.
- **A impressão de teste mostrar que nenhum tom abaixo do teto decorativo é visível ao aluno** na
  impressora de escola. Nesse caso, ou o teto da região é revisto por ADR, ou a pauta volta a ser
  traço preto e passa a ser geometria. Nunca se afrouxa o piso da paridade para ela caber (P11).

## Como isto poderia ter falhado em silêncio

- **Pauta menor piorando a leitura.** O sintoma aparece na fatia 8 como transcrição errada, que é
  nota errada sem erro visível. O que impede isso é o corpus da fatia 5 ser escrito sobre a pauta
  nova. **Um corpus escrito em folha de 8,6 mm e linha preta não serve de evidência para esta
  pauta**, e quem coletar o corpus confere a folha antes de usá-la.
- **Pauta cinza sumindo sem ninguém ver.** Num renderizador, ou numa impressora fraca, a pauta cinza
  pode sumir. Isso não afeta a captura, mas afeta o aluno, que escreve torto numa caixa vazia. O
  primeiro caso é o da paridade por tom, e o segundo é o da impressão de teste.

O 8,6 mm é o exemplo do modo inverso. Ele está no §7 desde a primeira versão da arquitetura e
entrou no código na 5a, com uma razão que tinha forma de medição e não era medição. O desperdício
só apareceu quando alguém olhou a folha pronta para imprimir.

## Alternativas descartadas

- **Manter 8,6 mm.** A justificativa dele é uma hipótese de leitura, e essa hipótese não foi medida.
  Mantê-lo contra a decisão do mantenedor trocaria um custo observado, que é o papel, por um
  benefício suposto.
- **Manter a pauta preta.** A captura recortaria linhas tão escuras quanto a letra do aluno, e a
  pauta disputaria a leitura com a resposta. É o contrário do que a letra cinza dentro da bolha já
  faz no gabarito.
- **Pauta escolhida por questão.** Ninguém pediu, e ela seria um terceiro parâmetro da área de
  resposta, sem consumidor.
