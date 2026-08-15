# Protocolo de medição da folha impressa

Nenhum teste automatizado captura escala de impressora, toner fraco ou deriva de alimentação de
papel. Esta é a única verificação da fatia 1 que precisa de uma pessoa, uma impressora e uma régua
— e é ela que fecha a distância entre o `LayoutMap` e o papel que o aluno recebe.

Executar sempre que a geometria de captura mudar (`CaptureGeometry`), quando o `LayoutMap` mudar de
versão, ou ao trocar de impressora de referência.

## 1. Gerar o PDF

```bash
cd apps/web && npx tsx scripts/render-fixture.ts
# saída: build/parity/web.pdf
```

## 2. Imprimir

- Papel **A4**, branco, gramatura comum (75–90 g/m²).
- Escala **100%**. Desligue "ajustar à página", "encaixar", "reduzir para caber" e qualquer
  autoescala do visualizador ou do driver.
- Sem frente e verso.
- Impressora monocromática, toner ou tinta em nível normal — não use um cartucho no fim da vida
  para esta medição.

Se o driver não oferecer 100% explícito, imprima por um visualizador que ofereça, e confirme antes
de medir com o passo 3.1.

## 3. Medir

Use régua metálica com escala de milímetro, ou paquímetro se houver. Meça sobre a **página 1**, que
é onde fica a região de gabarito. Anote o valor observado, não o esperado.

**Uma régua lê ±0,5 mm, não ±0,2 mm.** Por isso quase nada aqui é medido uma vez só: as medidas
pequenas são tomadas sobre vários passos e divididas, o que reduz o erro de leitura na mesma
proporção. Uma leitura isolada de uma bolha de 4,4 mm não distingue 4,2 de 5,0 — não tente.

### 3.0 Portão: a escala da impressão

**Meça isto primeiro. Se falhar, pare — nenhuma outra medida significa nada.**

| # | O que medir | Esperado | Aceitável |
|---|---|---|---|
| 3.1 | Largura da área impressa: traço mais à esquerda até o mais à direita | 180,0 mm | 179–181 mm |

180 mm é a maior distância da folha e é onde a régua é mais confiável. Um desvio de poucos
milímetros aqui denuncia impressão fora de 100%: 175 mm significa 97%, e nesse caso **toda** medida
seguinte sai encolhida junto, inclusive as que parecem certas por arredondamento.

Se der fora da faixa: volte ao passo 2, desligue explicitamente qualquer ajuste de escala e
reimprima. Não continue com uma folha escalada.

### 3.1 Medidas

| # | O que medir | Esperado | Aceitável |
|---|---|---|---|
| 3.2 | Margem esquerda: borda do papel até o traço mais à esquerda | 15,0 mm | 14,5–15,5 mm |
| 3.3 | Margem superior: borda do papel até o topo do marcador | 14,0 mm | 13,5–14,5 mm |
| 3.4 | Lado de um marcador ArUco (qualquer um dos quatro) | 14,0 mm | 13,5–14,5 mm |
| 3.5 | Borda esquerda da bolha (A) até a borda esquerda da bolha (D) da mesma questão, dividido por 3 | 5,2 mm | 5,0–5,4 mm |
| 3.6 | Borda externa esquerda da bolha (A) até a borda externa direita da bolha (D) da mesma questão | 20,0 mm | 19,5–20,5 mm |
| 3.7 | Distância entre os centros de dez linhas de bolhas, dividida por 10 | 6,0 mm | 5,9–6,1 mm |

O diâmetro da bolha sai de 3.6 menos três passos de 3.5: `20,0 − 3 × 5,2 = 4,4 mm`, que é o
diâmetro externo com o traço de 0,22 mm incluído. Medir a bolha isolada com régua não funciona.

Referência conferida sobre o próprio PDF, rasterizado a 1200 dpi: largura impressa 180,00 mm,
margem esquerda 15,01 mm, margem superior 14,03 mm, lado do ArUco 13,99 mm, diâmetro externo da
bolha 4,42 mm, passo horizontal 5,20 mm, passo vertical 6,00 mm. Divergência acima da faixa
aceitável é da impressão, não do layout.

## 4. Inspecionar os marcadores

- Os quatro marcadores estão completos, com a borda preta fechada nos quatro lados.
- Nenhum traço invade a zona de silêncio de 2 mm ao redor de cada marcador.
- Os módulos internos estão nítidos — sem borrão que una dois módulos vizinhos, sem falha branca
  dentro de um módulo preto.

Marcador com borda falhada é a causa mais comum de captura que não fecha em campo (§16).

## 5. Conferir o QR

Aponte a câmera de um celular para o QR da folha impressa. Ele deve ler:

```
prova-referencia-slice-1...0.05CB
```

Os dois campos vazios entre os pontos são `student_token` e `variant`, que a fatia 7 preenche.

## 6. Conferir em dois visualizadores

Abra `build/parity/web.pdf` em dois visualizadores diferentes (por exemplo, o do navegador e um
leitor de PDF instalado) e confirme em ambos:

- Tamanho de página declarado: **595 × 842 pt** (209,90 × 297,04 mm).
- As margens e as medidas acima não mudam entre visualizadores.

A caixa de página é declarada em pontos inteiros porque o `PdfDocument` do Android só aceita
inteiros, e uma caixa diferente entre as duas plataformas seria divergência gratuita. O conteúdo é
posicionado pela conversão exata, então as medidas de 3.4 a 3.7 não são afetadas pelo arredondamento
da página.

## 7. Registrar

Anote os valores observados na tarefa 10.2 de `openspec/changes/slice-1-layout-engine/tasks.md`,
junto com impressora, driver e data. Valor observado, não valor esperado: o objetivo do registro é
poder comparar a próxima medição com esta.
