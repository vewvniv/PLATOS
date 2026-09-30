# ADR-0020 — O critério da posição do QR é o símbolo no canvas, e não o canto do mapa

**Status:** aceito · **Data:** 2026-09-30 · **Fatia-limite:** antes da tarefa 3.1 da `slice-5c-0-o-recorte-da-resposta`
**Referências:** ADR-0018 (o QR ancora o terceiro canto; decisão 3, passo (c)) · ADR-0007 (critério antes da execução) · `rigorous.md` P11, P14, P6 · `design.md` da `slice-5c-0-o-recorte-da-resposta`, decisão 2 e Context · `docs/cobertura-slice-5c-0-o-recorte-da-resposta.md`, "2.2"

Este ADR **complementa** o ADR-0018: não muda a decisão dele (dois ArUcos, QR no terceiro canto, segundo ajuste). Muda o **critério com que se verifica** uma hipótese que o segundo ajuste usa.

## Contexto

O segundo ajuste do ADR-0018 usa os cantos do QR como âncora, e o decodificador (ZXing-C++ 2.3.0) os devolve em `Result.position`. *Conferido por `javap`.* Que `topLeft`, `topRight` e `bottomLeft` sejam os cantos **externos do símbolo** era uma hipótese (*suposta*, `design.md`, Context).

A tarefa 2.2 fixou, antes da primeira execução (ADR-0007), o critério: os três pontos ficam a **≤ 0,5 mm do canto que o mapa declara para o QR**, medidos no canvas do QR.

**Resultado obtido — o critério reprovou.** Sobre o documento renderizado de frente, a 10 px/mm, no `platos-atd34`, 2026-09-30:

| Região | topLeft | topRight | bottomLeft |
|---|---|---|---|
| 1 | 0,391 | 0,390 | 0,320 |
| 2 | 0,472 | **0,610** | **0,532** |

(mm; o instrumento reage: com o canvas deslocado de 2 mm acusou 2,31 / 2,31 / 2,21.) Pela regra da tarefa, a hipótese cai e o trabalho para. Este ADR é o registro exigido por P11 e a decisão do mantenedor de reformular.

**Por que o critério estava mal posto (P14: defeito comum às duas medições).** O canvas do QR nasce da **primeira** homografia, ajustada só nos dois marcadores da diagonal, e essa homografia **extrapola** no canto do QR — o risco (a) do ADR-0018. O critério media a distância ao canto *do mapa*, e o que ele media era a soma de dois efeitos: o significado de `position` **e** o deslocamento do símbolo dentro do canvas. Um diagnóstico posterior (não era critério) mostrou que os dois se separam: o ZXing cai a ≤ 1 px do topo e da borda direita escuros reais do símbolo (`TR=(183,37)` contra topo escuro em 37 e borda em 182), e o símbolo inteiro está 0,3–0,6 mm fora do lugar que o mapa declara, dentro do canvas.

## Decisão

1. **O critério registrado em 2.2 fica como REPROVADO**, com os números acima. Não é retirado nem reescrito (P7).
2. **O critério da tarefa 2.2 passa a ser este**, e a tarefa é refeita com ele:
   - **Referência:** a menor caixa `[x0,y0,x1,y1]` de pixels abaixo de metade do branco, dentro de uma janela de **±1,5 mm** em torno da caixa do QR que o mapa declara. A janela existe para isolar o símbolo dos vizinhos (moldura, marcadores), que contaminavam a caixa do canvas inteiro. É um oráculo de pixel que **não compartilha código** com o decodificador nem com o mapa (P4).
   - **Critério:** `topLeft` a ≤ 0,5 mm de `(x0,y0)`, `topRight` de `(x1,y0)`, `bottomLeft` de `(x0,y1)`. **A tolerância de 0,5 mm é a mesma de antes: não foi afrouxada.**
   - **Guarda de vacuidade (P13):** a caixa medida SHALL ter entre 13 e 15 mm de lado, ou o teste falha antes de comparar — uma janela que pegasse a moldura ou nada passaria calada.
   - **Visto falhar:** duas mutações em `positionOf` — trocar `topLeft` por `bottomRight`, e recuar `topLeft` de 5 px — SHALL derrubar o critério, cada uma pelo seu motivo.
3. **A distância ao canto do mapa deixa de ser critério e passa a ser dado.** Continua registrada por região, rotulada "erro da primeira homografia no canto do QR", porque ela é a medida direta do risco (a) do ADR-0018, e é o que o segundo ajuste existe para corrigir.

## Consequências

- **Honestidade sobre o que este critério vale.** Ele foi escolhido **depois** de ver o diagnóstico, que já mostrava `TR` e `TL` a ≤ 1 px do símbolo. Não é um teste cego: é provável que passe. O valor dele é ser um oráculo independente e um guarda de regressão, e o de dizer, no documento, que a semântica de `position` foi confirmada por um instrumento que não é o mapa. **Não é** a prova que o critério original prometia, e a P6 não deixa chamá-lo assim.
- **A decisão 2 do design continua de pé, com uma hipótese nova, também suposta:** os pontos do QR voltam à imagem pela **inversa da mesma homografia** que fez o canvas, então o erro dela se cancela na ida e volta, e só o significado de `position` importa. A tarefa 3.1 a mede: um ajuste sobre a folha de frente, sem deslocamento, deve ter o maior resíduo abaixo de 0,2 mm. Se não tiver, a hipótese cai e o design é reaberto.
- **O `bottomRight` continua só registrado** e fora do ajuste, como o ADR-0018 e o design pedem.
- **O teto de resíduo de 1,0 mm não muda.** Ele foi fixado antes; o erro de 0,3–0,6 mm da primeira homografia, na folha de frente, é um dado sobre o tamanho do problema que o segundo ajuste corrige, e não um motivo para mexer no teto.

## Como isto poderia falhar em silêncio

O oráculo de pixel e o decodificador enxergam o mesmo módulo borrado e concordam com o mesmo erro: a folha impressa real tem borda de tinta e ganho de ponto que o documento renderizado não tem. O critério passa, e a papel a âncora fica meio módulo (0,24 mm) para dentro ou para fora. **Não é mitigado, é conhecido**; entra na linha do §16 "A região discursiva ainda não passou pelo aparelho nem pelo papel" como o que o papel precisa medir.
