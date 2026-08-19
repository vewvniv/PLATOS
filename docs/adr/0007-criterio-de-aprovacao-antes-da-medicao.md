# ADR-0007 — O critério de aprovação é fixado antes de a medição rodar

**Status:** aceito · **Data:** 2026-08-19 · **Fatia-limite:** 5 (corpus de medição)
**Referências:** `ARQUITETURA-FINAL-v3.md` §9.2 (economia de custo), §16 (acurácia em manuscrito), `CLAUDE.md` (verificação)

## Contexto

§9.2 e §16 mandam medir acurácia em manuscrito antes de construir a fatia 8, e §16 chama isso de
"maior risco não-arquitetural". Nenhum dos dois diz **qual número reprova**.

Medição sem critério prévio não decide nada: depois que o resultado é conhecido, qualquer limiar
escolhido é racionalização do que já se queria fazer. É o mesmo raciocínio que faz esta base exigir
"visto falhar" antes de confiar num teste — a diferença entre uma verificação e uma cerimônia é ela
poder dar resultado negativo.

## Decisão

Toda medição que exista para **decidir** — acurácia de transcrição, ganho de OCR, limiar de deviant,
qualquer uso de corpus — tem seu **critério de aprovação registrado antes da primeira execução**, no
ADR que cria o corpus.

O critério declara: a grandeza medida, o valor que aprova, o valor que reprova, e **o que acontece se
reprovar**. Sem a última parte o critério não é operante.

Alterar o critério depois de conhecer o resultado é permitido, e exige um ADR novo que registre o
resultado obtido e a justificativa — o que torna a mudança visível em vez de silenciosa.

## Consequências

- A fatia 5 ganha um ADR obrigatório antes de rodar o corpus.
- Decisões de custo de IA (§9) passam a ter reprovação possível, em vez de virarem justificativa
  retroativa.
- Custa uma discussão a mais antes de medir, que é exatamente onde ela é barata.

## Alternativas descartadas

**Medir primeiro e calibrar o limiar depois.** É como se chega a um limiar que aprova o que quer que
tenha sido medido.

**Herdar limiares da literatura.** Corpus de manuscrito de educação básica brasileira não é o corpus
dos benchmarks publicados, e §16 já diz que o número precisa vir do corpus real.
