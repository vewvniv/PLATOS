# ADR-0011 — O critério do limiar do OMR, fixado antes da primeira foto

**Status:** aceito · **Data:** 2026-08-26 · **Fatia-limite:** 3b
**Referências:** `ARQUITETURA-FINAL-v3.md` §8 (pipeline de captura), §15 (a fatia 3 é o produto do Basic), §16 (LGPD) · ADR-0001 · ADR-0007 · ADR-0010 · `docs/protocolo-medicao-impressa.md` §10 e §11

## Contexto

ADR-0010 definiu a grandeza — cobertura de tinta dentro do disco da bolha, com 0 = papel — e
reservou o corredor de **200 a 400 permilagem** onde o limiar do OMR terá de cair. Ele não escolheu
o número, e disse por quê: sem corpus fotografado, o limiar seria uma opinião com aparência de
medida.

A fatia 3a entregou o instrumento que produz a cobertura e parou antes de interpretá-la. O corpus
ainda não existe. Esta fatia o produz.

ADR-0007 exige que toda medição feita para **decidir** tenha seu critério de aprovação registrado
antes da primeira execução, e que o critério diga quatro coisas: a grandeza, o que aprova, o que
reprova e o que acontece se reprovar. Sem a última parte o critério não é operante.

Há uma sutileza que este ADR precisa resolver, e que ADR-0007 não enfrentou porque nas medições
anteriores ela não aparecia: **o limiar é um número que sai do próprio corpus**. Fixá-lo aqui seria
exatamente o que ADR-0007 proíbe; deixá-lo livre seria escolher depois de ver o resultado, que é a
mesma coisa com outro nome.

O que se fixa antes, então, não é o limiar. É a **regra que o produz**.

O único dado de papel que o repositório tem veio de scanner, e não serve para escolher este número:
`docs/protocolo-medicao-impressa.md` §321 registra que aquele equipamento comprime a faixa dinâmica
— papel a 233 de 255, miolo preto de ArUco a 83 —, de modo que toner pleno rende no máximo 64% de
cobertura naquelas imagens. O produto lê por câmera de celular, que tem outra faixa, outro branco e
sombra própria.

## Decisão

**Grandeza.** Cobertura em permilagem inteira, de 0 a 1000, medida pelo `SheetReader` sobre
**foto de câmera de celular** de folha impressa, normalizada contra o branco local do papel. Por
bolha, nunca por média — como ADR-0010 já determina para o orçamento decorativo.

**Corpus mínimo.** Duas folhas impressas da prova de referência — 40 questões de 4 alternativas,
320 bolhas —, com uma alternativa por questão preenchida a caneta. Fotografadas em pelo menos três
condições: luz de ambiente frontal, sombra parcial sobre a região, e ângulo de 20 a 30 graus.
Mínimo de seis fotos. O protocolo está em `docs/protocolo-medicao-impressa.md` §11.

**Duas classes de preenchimento, e só uma decide.** As bolhas preenchidas *conforme a instrução
impressa na folha* formam a nuvem que o critério julga. Um subconjunto preenchido de propósito de
leve é medido e registrado, mas **não entra no critério**: incluí-lo seria montar um corpus que
reprova a folha por um preenchimento que a própria folha manda não fazer. O número dele serve para
saber a folga que existe, e para a fatia da câmera saber o que orientar na tela.

**Regra que escolhe o limiar.** Sejam:

- `V` = a **maior** cobertura entre as bolhas não respondidas do corpus;
- `C` = a **menor** cobertura entre as bolhas preenchidas conforme a instrução.

O limiar é o meio do vão:

```
T = arredonda((V + C) / 2), restrito ao corredor [200, 400]
```

A regra é aritmética e não admite escolha depois do fato. Ela é o que este ADR fixa; `T` é
consequência dela.

**Margem.** `M = 50` permilagem — 5 pontos percentuais. É o mesmo número em dois papéis: é a folga
que este critério exige do corpus, e é a meia-largura da faixa de indecisão com que a leitura marca
uma bolha como duvidosa. Um número, uma justificativa.

**Aprova** quando existe `T` inteiro dentro do corredor com `V ≤ T − M` e `C ≥ T + M`. Com o
corredor de ADR-0010 e `M = 50`, isso equivale a:

| Condição | Valor |
|---|---|
| `V` — maior cobertura entre as vazias | **≤ 350** |
| `C` — menor cobertura entre as de caneta | **≥ 250** |
| `C − V` — o vão entre as duas nuvens | **≥ 100** |

**Reprova** em qualquer outro caso, inclusive quando as duas nuvens separam com folga mas o vão
cai inteiro fora do corredor de 200 a 400.

**O que acontece se reprovar.** ADR-0010 já escreveu o desfecho, e ele é obedecido sem discussão
nova: **cede a decoração**, na ordem tom da letra, trama da faixa, letra fora do círculo. A fatia
reimprime e remede. Se as nuvens continuarem sem separar depois de a decoração ter saído inteira,
o problema não é decoração — a grandeza ou a folha é que estão erradas —, a fatia para, e o caso
volta como ADR novo.

**O que fica registrado mesmo aprovando.** `V`, `C`, o vão, o `T` calculado, quantas bolhas,
quantas fotos, em que condições e com que aparelho, em `docs/cobertura-fatia-3b.md`. Um número que
aprova sem poder ser reexaminado depois não é melhor que um número inventado.

**Onde o limiar mora.** No aplicativo, validado contra o corredor que cada folha declara no
`ink_budget`. Folha cujo corredor não contenha o limiar do aplicativo é **recusada**, e não lida
com aproximação. A justificativa está em `design.md` da fatia 3b, decisão 1.

## Consequências

- A fatia 3b tem um resultado possível que não é "deu certo": reprovar muda a folha, e a folha é
  artefato hasheado. É por isso que ela vem antes da câmera, da tela e do lote.
- Alterar `T`, `M` ou o corredor depois de conhecer uma medição exige ADR novo que registre o
  resultado obtido, como ADR-0007 determina. Em particular, a fatia da câmera — que verá muitos
  aparelhos — herda a obrigação de reexaminar `V` e `C` com o que colher.
- A margem `M` não é só verificação: ela vira comportamento. Bolha dentro dela é pendência de
  revisão humana, e não acerto nem erro.
- O corpus é pequeno e de um aparelho só. Isto é limitação declarada, não descuido: um limiar
  validado sobre seis fotos de um celular é melhor que um limiar sem foto nenhuma, e pior que um
  corpus de campo.

## Como isto poderia ter falhado em silêncio

Sem a regra fixada antes, o caminho natural seria medir o corpus, olhar as duas nuvens e escolher
o limiar "no meio, com folga". O número sairia igual em quase todos os casos — e seria
irrefutável, porque não haveria como distinguir a escolha que os dados sustentam daquela que
sustenta o que já se queria fazer. O caso em que os dois divergem é justamente o caso em que a
folha precisaria mudar, e é o caso que a escolha posterior esconde.

O modo de falha seguinte é mais barato de descrever e mais caro de viver: um limiar frouxo demais
transforma decoração em resposta, e um limiar rígido demais transforma caneta fraca em bolha
vazia. Os dois chegam à nota como acerto ou erro que ninguém marcou, indistinguíveis de erro do
aluno, sobre folha já impressa.

## Alternativas descartadas

**Fixar `T` agora, dentro do corredor.** É o que ADR-0010 já recusou, pela razão que ADR-0007
registra. O corredor foi reservado para ser preenchido por medição, não por escolha.

**Limiar adaptativo, estimado da própria distribuição de coberturas da folha.** É o que OMR
clássico faz, e é melhor quando há dois agrupamentos. Numa folha em que o aluno deixou tudo em
branco não há dois agrupamentos, e o método inventa um: falha silenciosa que chega direto à nota.
Limiar fixo, validado contra corredor declarado, erra de forma visível.

**Incluir o preenchimento leve no critério.** Reprovaria a folha por um preenchimento que a
própria folha instrui a não fazer, e o resultado seria alterar a decoração para acomodar um caso
que a instrução já cobre. Ele é medido e registrado justamente para que a decisão de ignorá-lo
seja visível.

**Reaproveitar as digitalizações da 2b.** Já estão versionadas e não custam nada, mas medem o meio
errado. Elas continuam servindo ao que servem: regressão da medição, com números conhecidos.
