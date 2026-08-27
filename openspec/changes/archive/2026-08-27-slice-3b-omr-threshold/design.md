## Context

Ver `proposal.md — Why`. O que importa aqui é o estado do código e os números que já existem.

`apps/android/vision/SheetReader.kt` faz o pipeline de §8 de ponta a ponta a partir de arquivo de imagem, e devolve `OmrReading.Read(payload, measurements)`. `OmrMeasurement` já traz cobertura em **permilagem inteira** de 0 a 1000, na mesma unidade em que a região declara o orçamento — `commonMain` não tem ponto flutuante (D-1.2), e essa escolha da 3a é o que permite comparar com o corredor sem converter.

O `ink_budget` da região do `LayoutMap` já declara `decorative_max: 120`, `threshold_floor: 200` e `threshold_ceiling: 400`. O `ExamPackage` publicado já traz `answer_key` com `correct` e `points` por item, `scoring.max_score` e `variants[].positions` mapeando posição para item. Nenhum desses contratos precisa mudar.

O que não existe: qualquer consumidor da medição, e qualquer foto de celular de folha preenchida.

O único dado de papel do repositório vem de scanner, não de câmera: `papel.mjs` mediu 160 bolhas na digitalização da 2b, caneta de 51,61% a 72,20% e vazias até 9,20%. O mesmo arquivo registra que aquele scanner comprime a faixa dinâmica — papel a 233 de 255, miolo de ArUco a 83 —, de modo que toner pleno rende no máximo 64% de cobertura ali. Esses números descrevem um meio que o produto não usa.

## Goals / Non-Goals

**Goals**

- Fixar o critério de aprovação, e a **regra** que escolhe o limiar, antes de a primeira foto ser medida.
- Manter todo número que decide sob oracle que não compartilhe código com quem produz o número.
- Não gastar mudança de contrato: nem `LayoutMap`, nem `ExamPackage`, nem golden, nem hash.
- Deixar a folha reimprimível: se o corpus reprovar, a fatia sabe exatamente o que muda e em que ordem.

**Non-Goals**

- Não perseguir robustez fotográfica. Esta fatia mede o que a câmera entrega hoje; melhorar captura é problema da fatia com CameraX, que também é quem pode guiar o usuário.
- Não construir tela de revisão. As pendências são dados no resultado; quem as mostra vem depois.
- Não generalizar para região discursiva.
- Não criar módulo KMP novo, pelas mesmas razões da decisão 1 da 3a.

## Decisions

### 1. O limiar é constante do aplicativo, validada contra o corredor que a folha declara

A folha declara o **corredor**; o aplicativo traz o **limiar** e recusa folha cujo corredor não o contenha.

É o uso que ADR-0010 desenhou. O corredor está no artefato publicado exatamente porque folha impressa e aplicativo deixam de andar juntos — e uma folha de dois anos atrás, gerada por engine mais antiga, continua dizendo dentro de que faixa ela foi projetada para ser lida. Se o limiar do app cair fora dela, a folha não é legível por este app, e isso é recusa, não leitura degradada.

*Alternativa descartada — campo `threshold` novo no `ink_budget`.* Parece mais fiel a ADR-0010, e é o que eu faria se o limiar fosse propriedade da folha. Não é: ele é propriedade do **par folha-leitor**, e nasce de um corpus fotografado que a folha não conhece. Congelá-lo em cada folha significa que melhorar o limiar exige reimprimir provas já distribuídas — o oposto do que o corredor existe para evitar. Além disso muda `LayoutMap`, e portanto golden e hash do pacote, para comprar rigidez.

*Alternativa descartada — limiar por bolha, adaptativo à folha.* Estimar o limiar da própria distribuição de coberturas da folha (dois agrupamentos, corte no vale) é atraente e é o que OMR clássico faz. Mas numa folha em que o aluno deixou tudo em branco não há dois agrupamentos, e o método inventa um — falha silenciosa que chega à nota. Um limiar fixo, validado contra corredor declarado, erra de forma visível.

### 2. O critério de aprovação, escrito antes da primeira medição (ADR-0011)

ADR-0007 exige quatro coisas: a grandeza, o que aprova, o que reprova, e o que acontece se reprovar. O que ele proíbe é escolher o número depois de ver o resultado — então o que se fixa antes não é o limiar, é a **regra que o produz**.

**Grandeza.** Cobertura em permilagem inteira, medida pelo `SheetReader`, sobre foto de câmera de celular de folha impressa. Por bolha, nunca por média — como ADR-0010 já determina para o orçamento.

**Regra que escolhe o limiar.** Sejam `V` o maior valor entre as bolhas não respondidas do corpus e `C` o menor valor entre as bolhas preenchidas conforme a instrução impressa. O limiar é o meio do vão, `T = (V + C) / 2`, arredondado para inteiro e restrito ao corredor de 200 a 400. A regra é aritmética e não admite escolha depois do fato.

**Margem.** `M = 50` permilagem — 5 pontos percentuais. É o mesmo número em dois papéis: é a folga que o critério exige, e é a meia-largura da margem de indecisão da spec. Um número, uma justificativa.

**Aprova** quando existe `T` inteiro no corredor com `V ≤ T − M` e `C ≥ T + M`. Equivale a `V ≤ 350`, `C ≥ 250` e `C − V ≥ 100`.

**Reprova** em qualquer outro caso, inclusive quando as nuvens separam mas o vão cai fora do corredor.

**O que acontece se reprovar.** ADR-0010 já escreveu: cede a decoração, na ordem tom da letra, trama da faixa, letra fora do círculo. A fatia reimprime e remede, e o custo é uma rodada de impressão. Se as nuvens continuarem sem separar com a decoração removida, o problema não é decoração: a fatia para e o caso volta como ADR novo, porque aí a grandeza ou a folha é que estão erradas.

**O que fica registrado mesmo aprovando:** `V`, `C`, o vão, o `T` calculado, quantas bolhas, quantas fotos e em que condições. Sem isso o número aprova sem poder ser reexaminado.

### 3. Composição do corpus, e por que celular

Mínimo: **duas folhas impressas**, ambas da prova de referência, 40 questões de 4 alternativas — 320 bolhas, 80 preenchidas a caneta e 240 vazias com a decoração que a 2b introduziu. Fotografadas em **pelo menos três condições**: luz de ambiente frontal, luz fraca com sombra sobre parte da região, e ângulo de 20 a 30 graus. Mínimo de seis fotos, ~1900 medições de bolha.

Cada foto remede as mesmas bolhas, e é isso que se quer: a variação entre condições é o que o limiar precisa sobreviver, e é o que o scanner da 2b não tinha.

**Duas classes de preenchimento, e só uma decide.** As 80 bolhas de caneta são preenchidas *conforme a instrução impressa na folha*, e são elas que formam `C`. Um subconjunto adicional, preenchido de propósito de leve, é medido e registrado, mas **não entra no critério** — incluí-lo seria escolher um corpus que reprova a folha por um preenchimento que a folha manda não fazer. O número serve para saber quanto de folga existe, e para a fatia da câmera saber o que orientar na tela.

**Roster sintético.** O QR carrega identificador de aluno; o corpus usa identificadores inventados. Nenhum aluno real, nenhum dado de menor — o gatilho de §16 não é acionado por esta fatia.

*Alternativa descartada — reaproveitar as digitalizações da 2b.* Elas já estão versionadas e não custam nada, mas medem o meio errado, com faixa dinâmica comprimida. Continuam servindo ao que servem hoje: teste de regressão da medição.

### 4. A classificação e a nota ficam em `packages/domain`

A decisão 1 da 3a traçou a fronteira em quem toca imagem: o adaptador **acha**, o núcleo puro **calcula**. Classificação recebe inteiros e um corredor; nota recebe respostas e um pacote. Nenhuma das duas vê pixel, e a invariante põe medição, layout, scoring e contratos de domínio no KMP.

```
apps/android
  vision/   OpenCV, ZXing-C++ → OmrReading                    ← emulador
  omr/      projeção, amostragem, cobertura                   ← teste de host (JVM)
                    │ OmrMeasurement (permilagem inteira)
                    ▼
packages/domain
  capture/  veredito de bolha e resposta de questão
  scoring/  nota contra answer_key e variante
```

*Alternativa descartada — classificar em `apps/android/omr/`, junto da medição.* Junta o que produz o número com o que o julga, e é exatamente o acoplamento que a fatia 3a evitou. Também deixaria a nota inalcançável para a web, que §15 já prevê como consumidor futuro.

### 5. Múltipla marcação é pendência, não erro

Contar duas bolhas marcadas como erro é uma regra defensável em prova de papel, e é o que muita instituição faz. Aqui ela é errada por um motivo específico desta base: a folha tem decoração dentro da bolha, e a fatia inteira existe porque ainda não sabemos como essa decoração se comporta sob câmera. Transformar o modo de falha da leitura em zero para o aluno é converter um defeito nosso em nota dele, em silêncio.

Em branco é diferente e é definitivo: nenhuma bolha alcançou o limiar, e a leitura tem certeza disso — o aluno não marcou.

*Alternativa descartada — pendência também para em branco.* Encheria a revisão de casos sem dúvida e treinaria o professor a aprovar em lote, que é como revisão humana deixa de valer.

### 6. Oracles, por afirmação numérica

A seção **Verificação** do `CLAUDE.md` pede oracle independente para o que chega ao OMR. Por camada:

| Afirmação | Oracle | Independência |
|---|---|---|
| Cobertura das bolhas do corpus | `tools/parity/papel.mjs` sobre a mesma foto | Implementação separada, em outra linguagem, que não conhece o `SheetReader` |
| Veredito nas bordas (`T−M`, `T−1`, `T`, `T+1`, `T+M`) | Buffer sintético com cobertura conhecida por construção | Analítica: a resposta vem de quem desenhou |
| Resposta da questão nos quatro casos | Vereditos montados à mão, sem passar por imagem | A classificação é testada sem a medição |
| Nota | `answer_key` da fixture, conferido contra `GabaritoDaFixtureTest`, que já existe | O gabarito não é recalculado por quem apura |

E o que precisa ser **visto falhar**, com o registro em `docs/cobertura-fatia-3b.md`: mover o limiar um passo e ver o veredito de borda virar; estreitar o corredor da folha de fixture e ver a leitura recusar; trocar uma resposta do gabarito e ver a nota mudar; marcar duas bolhas e ver a nota deixar de fechar.

Permilagem inteira remove a classe `NaN` desta camada — não há como um `Int` ser não finito, e a comparação com o limiar não tem como passar calada. A guarda contra medição não finita continua sendo da camada de baixo, onde o `Double` existe, e a 3a já a tem.

## Risks / Trade-offs

**O corredor de 200 a 400 pode não sobreviver à câmera** → é o risco que a fatia existe para correr, e ADR-0010 já declarou o desfecho. O custo real é uma segunda rodada de impressão e uma mudança de folha que altera golden e hash — que é por que ela vem agora, e não depois de haver app apoiado nela.

**O corpus é pequeno e de um aparelho só** → aceito e registrado no ADR. Um limiar validado sobre seis fotos de um celular é melhor que um limiar sem foto nenhuma, e pior que um corpus de campo. A fatia da câmera, que verá muitos aparelhos, herda a obrigação de reexaminar `V` e `C` com o que colher — e mudar o número exigirá ADR novo, como ADR-0007 determina.

**Foto de celular traz reflexo e sombra que o scanner não tem** → a normalização contra o branco local já existe desde a 3a e tem cenário na spec; o corpus inclui a condição adversa de propósito, para que ela apareça no número em vez de aparecer em campo.

**Margem de 50 pode gerar pendência demais em folha real** → se acontecer, aparece já no corpus, como bolhas dentro da margem. É informação, não defeito: uma margem que produz pendência no próprio corpus é sinal de que a folha, e não o limiar, precisa mudar.

**A implementação para no meio** esperando o corpus → é dependência humana, não técnica, e a ordem das tarefas a isola: tudo que não depende de foto vem antes, e o que depende vem depois, sem trabalho parado no meio.

## Migration Plan

Não há migração: nenhum schema, nenhum contrato publicado e nenhum artefato imutável muda. O limiar é uma constante, e revê-lo é um commit mais um ADR.

O único caminho que mexe em artefato é a reprovação do corpus: aí a decoração da folha muda, e isso altera golden e hash do pacote. Nesse caso a mudança de folha vira commit de contrato separado, antes dos consumidores, e o ADR-0011 é aditado com o resultado que a motivou.

## Open Questions

- **Quantas fotos além do mínimo, e com que aparelhos.** O mínimo está fixado; passar dele só melhora o corpus e não muda spec, abordagem nem tarefas.
- **Formato e tamanho das fotos versionadas em `fixtures/`.** Seis JPEGs de celular em resolução plena são dezenas de MB. Decisão de custo de repositório, resolvível na tarefa que cria a fixture — a mesma questão que a 3a resolveu para o recorte em cinza.
