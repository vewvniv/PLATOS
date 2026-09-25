# ADR-0018 — A região discursiva tem dois ArUcos na diagonal, e o QR ancora o terceiro canto

**Status:** aceito · **Data:** 2026-09-25 · **Fatia-limite:** antes da primeira prova com discursiva publicada
**Referências:** `ARQUITETURA-FINAL-v3.md` §6 (coordenadas normalizadas ao quadrilátero), §8 (modelo de regiões, IDs de ArUco, ordem do pipeline), §16 (impressão dos ArUcos) · ADR-0001 · ADR-0007 · ADR-0016 · ADR-0017 · `RegionDetector.kt:59` · `design.md` da `slice-5a-regiao-discursiva`, decisão 5

## Contexto

O §8 fixa o modelo da região discursiva:

> Apenas se houver discursivas, uma região `ESSAY_REGION` por questão, cada uma com 4 ArUcos e um QR
> compacto.

O §6 define as coordenadas da região sobre esses quatro marcadores ("`(u,v) ∈ [0,1]²` do
quadrilátero dos 4 ArUcos"). E o §16 já registra o custo, na linha "Impressão dos ArUcos": "São 4
por questão discursiva, não 4 por prova: muito mais superfície sujeita a toner fraco."

Hoje a região discursiva tem, *conferido por leitura*:
- **quatro marcadores de 14 mm** nos cantos da coluna;
- **o QR centrado**, começando na linha dos centros dos marcadores;
- **a moldura abaixo do QR.**

A parte fixa de cada questão, somando marcadores e QR, é de 39 mm (*calculado*).

A captura monta a homografia **exata pelos quatro centros**, e confere o resultado nos **dezesseis
cantos**, que não entram no ajuste (`RegionDetector.kt:59`). Essa conferência independente é o que
denuncia folha amassada e marcador confundido.

Em 2026-09-25, o mantenedor viu uma prévia da folha, desenhada pelo renderizador web, e propôs outra
região, com um esboço:
- um ArUco no canto superior esquerdo;
- o QR no canto superior direito, na mesma faixa do ArUco;
- a moldura logo abaixo dessa faixa;
- um ArUco no canto inferior direito.

Ele decidiu também que o QR "deve ser usado para a triangulação também, além da sua função
principal".

O ganho, *calculado sobre a prévia e não medido*, com os marcadores de 11,2 mm da mudança que
implementa: a parte fixa por questão cai de 34,4 mm, com quatro marcadores, para 28,8 mm. A região
de 4 linhas na largura de página cai de 63 mm para 57 mm.

## Decisão

1. **A região discursiva imprime dois ArUcos**, `4k` no canto superior esquerdo e `4k+3` no
   inferior direito.
   - A alocação `{4k…4k+3}` do §8 **fica**. A região `k` continua identificada pelos IDs dela, e o
     teto de 24 discursivas (`CaptureGeometry.MAX_REGIONS`) não muda.
   - `4k+1` e `4k+2` simplesmente não são impressos.
2. **O QR ocupa o canto superior direito**, na faixa do marcador de cima. A moldura começa logo
   abaixo dessa faixa e ocupa a largura da região.
3. **A geometria é ancorada nos três cantos, e a ordem do §8 é mantida.** São dois passos:
   - **(a) Primeira homografia:** sai dos cantos dos dois ArUcos, e a região é retificada.
   - **(b) Leitura do QR:** o QR é lido na ROI já retificada, como o §8 manda, e é conferido contra
     os marcadores, como hoje.
   - **(c) Segundo ajuste:** os padrões de posição do QR (os três quadrados de canto) entram, junto
     com os cantos dos ArUcos, num segundo ajuste sobredeterminado, e o recorte sai dele.

   O QR nunca é procurado na foto em perspectiva. Ele só melhora a geometria **depois** de ter sido
   lido.
4. **A conferência da geometria passa a ser o resíduo do segundo ajuste.** São 8 cantos de ArUco e
   os 3 quadrados do QR: 22 equações para 8 incógnitas. Com isso, uma dobra em qualquer dos três
   cantos ancorados aparece no resíduo.
   - **O quarto canto, o inferior esquerdo, não tem âncora.** Ele é extrapolado, e o que o cobre é a
     folga do recorte fora da moldura.
   - **Não é mitigado, é conhecido (P8)**, até a medição em papel. O critério dessa medição é
     registrado antes das fotos (ADR-0007).
5. **O gabarito não muda.** Ele continua com quatro ArUcos, com a homografia exata pelos centros e
   com a conferência nos dezesseis cantos.
   - A leitura de bolha mede círculos de 4,2 mm, e precisa de uma precisão que o recorte de uma
     resposta escrita não precisa.
   - O gabarito é o produto em produção (§15, fatia 3), com limiar medido sobre a geometria atual
     (ADR-0011).

   Levar este desenho para o gabarito é uma decisão separada, e ela precisaria da medição de OMR
   que este ADR não tem.

## Consequências

- **O §8 e o §6 ganham uma emenda para a região discursiva.** As coordenadas dela passam a ser
  normalizadas sobre o retângulo de referência entre os dois marcadores. O retângulo exato é
  definido na mudança que implementa.
- **A captura ganha um segundo caminho no `RegionDetector`**, com dois marcadores e o QR, ao lado do
  de quatro marcadores, que continua servindo o gabarito. A `slice-5b-1` está pausada antes do
  papel. A spec e o código dela exigem "os quatro `marker_ids`", e isso passa a ser "os marcadores
  que a região declara".
- **O contrato muda:** a região discursiva declara dois `marker_ids`. Validação, goldens, paridade e
  fidelidade são regravados e fechados na mesma sessão (P23).
- **Metade dos marcadores por discursiva**, e metade da área sujeita a toner fraco.
- **O QR passa a carregar peso geométrico.** Um QR que não é lido já deixava a região sem identidade
  (a folha é de quem?), e por isso sem uso. O modo de falha não é novo: o que muda é que a
  qualidade da impressão do QR agora também afeta o recorte.
  - **Risco do passo (a):** o QR é lido numa retificação feita só com os dois ArUcos, em que o
    canto dele é extrapolado. Se o erro nesse canto passar do que o decodificador tolera, o QR não
    lê.
  - A linha do limiar no §16 já registra 2 fotos em 9 sem leitura de QR, sem causa medida. As fotos
    da folha nova dizem se isso piora.

## Gatilho para reabrir

Este ADR reabre se as fotos da folha impressa mostrarem qualquer uma destas coisas:
- recorte que perde tinta no canto inferior esquerdo, além da folga;
- QR que deixa de ser lido no passo (a) em foto onde, com quatro marcadores, ele seria lido.

Os caminhos de volta são pôr uma âncora no canto inferior esquerdo ou voltar aos quatro
marcadores.

## Como isto poderia ter falhado em silêncio

O recorte perde o começo da última linha, no canto sem âncora. A resposta chega truncada ao
professor e, na fatia 8, à correção por IA, que avalia uma resposta mais curta do que a escrita.
Nenhum teste de documento percebe, porque golden, paridade e fidelidade não fotografam papel.

O que impede o silêncio são três coisas:
- a folga do recorte;
- o resíduo do segundo ajuste;
- fotos **em ângulo**, que medem justamente o canto extrapolado.

## Alternativas descartadas

- **Manter quatro ArUcos (5a).** Custa 5,6 mm de altura por questão e o dobro de marcadores, e foi
  justamente o que o mantenedor reprovou ao ver a folha.
- **O QR só como identidade, sem ancorar a geometria.** Com isso, dois cantos ficariam
  extrapolados, e não um. Seria a região mais frágil das três opções.
- **Procurar o QR na foto antes da homografia**, para usá-lo desde o primeiro ajuste. Inverte a
  ordem do §8, que existe porque ler o QR na imagem desempenada é "muito mais fácil que sobre a foto
  em perspectiva".
