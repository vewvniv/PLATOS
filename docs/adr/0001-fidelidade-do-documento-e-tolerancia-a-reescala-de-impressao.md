# ADR-0001 — Fidelidade é do documento; a folha impressa precisa tolerar reescala

**Status:** aceito · **Data:** 2026-08-15 · **Fatia:** 1 (Layout Engine)
**Referências:** `ARQUITETURA-FINAL-v3.md` §6 (coordenadas normalizadas ao quad), §7 (geometria de captura), §16 (risco de impressão dos ArUcos)

## Contexto

A fatia 1 exigia verificar a fidelidade dimensional da folha impressa com régua, tolerância de
±0,2 mm. A verificação foi executada e produziu o resultado oposto ao esperado: **nenhuma impressora
reproduz o PDF em escala real.**

Três pessoas imprimiram a mesma prova de referência, em três impressoras, medindo com três
instrumentos. Os valores esperados vêm da rasterização do próprio PDF a 1200 dpi.

| Impressora | Instrumento | Vão horizontal ArUco→ArUco (esperado 180,00 mm) | Vão vertical ArUco→ArUco (esperado 85,01 mm) | Escala aparente |
|---|---|---|---|---|
| 1, com "ajustar à área de impressão" | régua | 175 | — | −2,8% |
| 2, escala 100% | fita métrica | 184 | 89 | +2,2% / +4,7% |
| 3, escala 100% | paquímetro | 173,8 | 82,32 | −3,4% / −3,2% |

Duas delas estavam explicitamente em escala 100% e ainda assim erraram em **direções opostas**. A
impressora 2 apresentou erro diferente por eixo, assinatura de calibração mecânica: o avanço do
papel e o carro de impressão erram por mecanismos distintos.

O documento, esse, está correto. Os quatro marcadores medem 14,00 × 14,00 mm, o vão horizontal
180,00 mm, o vertical 85,01 mm, as margens 15,01 / 14,03 / 14,90 mm, e a soma fecha na largura da
página — tudo dentro de 0,05 mm sobre a rasterização a 1200 dpi.

## Decisão

**A fidelidade dimensional é uma propriedade do documento, não do papel.**

1. A verificação de fidelidade dimensional é feita sobre o PDF gerado, por rasterização a 1200 dpi,
   com tolerância de 0,05 mm. É determinística e automatizável, e roda em CI.
2. A folha impressa **não** precisa reproduzir escala absoluta. O que ela precisa garantir é o que
   a captura consome: marcador ArUco acima do mínimo de 12 mm de §7 mesmo depois de reescalada,
   zona de silêncio livre e QR decodificável.
3. A faixa de reescala considerada normal é de **±5%**, cobrindo com folga os −3,4% a +4,7%
   medidos.
4. Nenhuma etapa do sistema pode depender de medida absoluta na folha impressa. Dentro de uma
   região escaneável tudo é `(u,v)` do quadrilátero dos quatro ArUcos, como §6 já determina.

## Consequências

**O lado geométrico já estava resolvido.** §6 normaliza tudo ao quad exatamente para dar
"imunidade automática a escala de impressão, tamanho de papel, DPI e distância da câmera". A
homografia da fatia 3 absorve ±5% sem código adicional. Este ADR não muda o pipeline; ele registra
que a premissa foi medida e confirmada em campo, e não assumida.

**O lado dimensional passa a ter uma margem obrigatória.** O marcador de 14 mm foi escolhido na
fatia 1 para que os 7 módulos fechassem em 2 mm inteiros, sob a restrição de aritmética inteira.
A medição mostrou um segundo motivo, mais importante: a 12 mm — o mínimo de §7 — a impressora que
mais encolheu teria produzido **11,6 mm**, abaixo do mínimo, quebrando a detecção. A 14 mm ela
produz 13,5 mm.

> Toda geometria de captura deve ser dimensionada de modo a permanecer válida sob ±5% de reescala.
> Aplicar o mínimo de §7 ao valor nominal não basta; ele precisa valer no pior caso da faixa.

**§16 ganha evidência.** O risco "impressão dos ArUcos" deixa de ser hipótese: a variação entre
impressoras é real e medida. A folha de teste de impressão no onboarding, que §16 pede, passa a ter
um alvo definido — verificar marcador, zona de silêncio e QR, não milímetros absolutos.

**A fatia 5 herda a disciplina.** Corpus de medição e calibração de limiar seguem a mesma regra de
§9.2: o número sai de medição, não de intuição.

## Alternativas descartadas

**Manter ±0,2 mm no papel e procurar uma impressora que passe.** Mediria a calibração da
impressora, não o sistema, e reprovaria folhas que a captura lê perfeitamente. Escolas usam a
impressora que têm.

**Calibrar uma impressora de referência e homologar só ela.** Inviável no produto: o professor
imprime onde dá. Também mascararia a exigência de folga dimensional, que é a lição real da medição.
