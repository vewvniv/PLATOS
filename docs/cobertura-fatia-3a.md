# Cobertura de cenários — fatia 3a (medição de cobertura na folha capturada)

Mapa de cada cenário da spec delta de `openspec/changes/slice-3a-omr-measurement/specs/capture-omr/`
para a verificação que o cobre, e — onde a verificação é crítica — **como ela foi vista falhar**.
Gerado ao fechar a tarefa 9.2.

Esta fatia tem uma característica que organiza o documento inteiro: **quase tudo que ela produz é
número, e número errado não quebra nada.** Uma cobertura de 220‰ é tão bem-formada quanto uma de
514‰; nenhum tipo, nenhum schema e nenhum hash distingue as duas. Por isso cada linha abaixo existe
porque alguma coisa precisava conferir o valor contra uma resposta que não veio da própria medição.

E acrescenta uma lição que se repetiu três vezes e merece estar no topo: **tolerância folgada é o
jeito mais comum de um teste de medição não medir nada.** Três verificações desta fatia passaram
por acidente antes de eu perceber, todas por tolerância maior que o defeito que elas deveriam
pegar. As três estão registradas abaixo.

## `capture-omr` — 15 cenários, 15 cobertos

| Cenário | Verificação |
|---|---|
| Bolhas vêm do mapa | `BubbleMeterTest.mede todas as bolhas que o mapa declara, e nenhuma a mais` (host) e `SheetReaderInstrumentedTest.le_a_folha_de_ponta_a_ponta` (emulador), que confere o conjunto de identificadores contra o mapa |
| Folha de outra prova sob estes marcadores | `RegionDetectorInstrumentedTest.folha_de_outra_prova_sob_estes_marcadores_e_recusada`, conferindo **o motivo** e não só a recusa |
| Marcador faltando | `RegionDetectorInstrumentedTest.captura_sem_marcador_nenhum_e_recusada`, mais o ramo de identificadores ausentes em `RegionDetector` |
| Captura em perspectiva | `RectifierInstrumentedTest.a_fracao_conhecida_sobrevive_a_distorcao_em_perspectiva` — folha sintética com ArUcos reais, distorcida e devolvida ao retificador de produção |
| Geometria que não fecha | mesmo `RegionDetector`: erro de reprojeção acima de 6 px recusa, medido sobre os **16 cantos** que não entraram no ajuste |
| Payload íntegro | `RegionDetectorInstrumentedTest.le_o_qr_da_regiao_retificada` — e o payload lido bate com o que um celular leu no papel na fatia 2b |
| CRC não confere | `QrPayloadTest.corpo alterado sem recalcular o CRC e recusado`, com o par positivo ao lado (3 alvos) |
| QR de outra região | `RegionDetectorInstrumentedTest.qr_de_outra_regiao_e_recusado_pelos_marcadores` — a redundância que §8 pede |
| Ida e volta do payload | `QrPayloadTest`, 13 testes nos 3 alvos, quase todos chamando os escritores reais em vez de literais |
| Medição de todas as bolhas | `BubbleMeterTest` e `RealSheetTest.mede as 160 bolhas da folha real` |
| Cobertura conferida contra valor conhecido | `BubbleMeterTest.cobertura conferida contra fracao conhecida por construcao` — oracle analítico, seis frações sobre 32 bolhas |
| Janela de medição inválida | `BubbleMeterTest`: disco fora da região, janela vazia, cobertura não finita, captura escura demais — quatro recusas, cada uma nomeando a bolha ou a causa |
| Papel escurecido não vira tinta | `BubbleMeterTest.papel escurecido nao vira tinta` — metade da região a 75%, vazias seguem abaixo de 5‰ |
| Leitura repetida | `SheetReaderInstrumentedTest.a_mesma_captura_lida_duas_vezes_da_a_mesma_medicao`, comparando a lista inteira |
| Sem rede | `SheetReaderInstrumentedTest.a_leitura_nao_toca_a_rede`, com `StrictMode` — e o meta-teste que prova que a guarda reage |

Além dos cenários, dois invariantes que a fatia decidiu afirmar sobre o **tipo**:

| O que | Verificação |
|---|---|
| O contrato não carrega veredito nem limiar | `OmrMeasurementTest.a medicao tem exatamente tres campos, e nenhum deles e veredito` — o `toString` de uma `data class` lista as propriedades do construtor, então um campo novo derruba o teste |
| A leitura não altera captura nem mapa | `SheetReaderInstrumentedTest.a_leitura_nao_altera_a_captura_nem_o_mapa`, conferindo o buffer byte a byte e o `toCanonicalJson()` do mapa |

## Como cada verificação crítica foi vista falhar

| Defeito introduzido | Quem acusou | O que os outros disseram |
|---|---|---|
| Comparação de CRC desligada em `QrPayload.read` | dois testes: `corpo alterado sem recalcular o CRC` e `CRC trocado sozinho` | os outros onze seguiram verdes, o par positivo entre eles |
| Centro do marcador deslocado 5 px no `RegionDetector` | `os_cantos_do_opencv_coincidem_com_os_de_papel_mjs` e a comparação de cobertura | os outros oito instrumentados seguiram verdes |
| Redundância `region_idx` × marcadores desligada | `qr_de_outra_regiao_e_recusado_pelos_marcadores`, sozinho | os outros doze verdes |
| Bolhas do mapa deslocadas 2,6 mm (teste permanente) | a caneta cai de **514‰ para 220‰** | — |
| Uma cobertura adulterada no vetor de referência versionado | o `diff` da guarda de CI aponta a linha e sai com código 1 | — |
| `Double` em `commonMain` | `IntegerArithmeticGuardTest`, que varre o fonte | nenhum teste de valor pegaria: um `Double` passa na JVM e diverge só no alvo JS |

## As três tolerâncias que quase esconderam o que deviam medir

**O oracle estava enviesado antes do medidor.** O gerador sintético pintava pixel inteiro conforme
o centro, e a medição saía **15‰ abaixo** em toda fração. Parecia defeito do medidor; era do
desenho — sobre uma corda de 37 px, meio pixel de fronteira vale 15‰. A tolerância de 12‰ que eu
tinha escolhido "por conforto" escondia isso. Suavizado o gerador, o desvio caiu para 5‰, e a
tolerância continuou 12 — **mesmo número, evidência diferente**.

**A tolerância do retificador media o próprio desenho.** A primeira versão do teste de perspectiva
usava 40‰ sobre um gerador de borda dura que sozinho punha 20‰ de viés: a distorção podia ter
dobrado de custo sem ninguém ver. Suavizado, os números viraram 8‰ e 22‰ de frente contra 17‰ e
30‰ em perspectiva — **a perspectiva custa cerca de 9 pontos**, e é esse número que o teste vigia.

**Uma tolerância que só existia por não ter sido medida.** A comparação contra `papel.mjs` começou
sem número declarado. Medida, deu 4,91‰ na média e 18‰ no pior caso — e o pior é numa bolha vazia,
onde a diferença absoluta pesa menos. A tolerância virou 20‰, declarada como **distância entre dois
métodos** e não como folga para ruído: a oficial mede sobre a região retificada, `papel.mjs` mede na
imagem original.

## Duas coisas que a fatia provou sem ter sido pedida

**O dicionário de ArUco fecha as duas pontas.** O Layout Engine desenha os marcadores de um
`ArucoDictionary` próprio, em Kotlin, e até esta fatia nenhum leitor de verdade tinha passado neles.
Agora o OpenCV acha os quatro com `DICT_5X5_100` e os centros coincidem com os de `papel.mjs` dentro
de 3 px — 0,3 mm. As duas implementações nem definem "centro" do mesmo jeito.

**A medição oficial concorda com a independente onde mais importa.** Na bolha `q22/A` — o rabisco
que não fecha o círculo, e o pior traço da folha — a implementação em Kotlin leu **514‰** e o
`papel.mjs` em JavaScript leu **516‰**. Dois pontos, por caminhos que não compartilham uma linha e
que nem medem do mesmo jeito.

## Números da fatia

| Grandeza | Valor |
|---|---|
| `packages/domain` JVM · Node · Android host | 249 · 243 · 243, zero falhas |
| `apps/android` host · emulador | 23 · 21, zero falhas |
| `apps/api` (Postgres real) · `apps/web` | 89 · 14, zero falhas |
| Paridade da prova | 185 de 185, **0,048 mm**, trama 0,39 ponto |
| Paridade da folha de teste | 9 de 9, trama 0,39 ponto |
| Fidelidade web · Android | 116 verificações cada; 0,046 e 0,042 mm |
| Caneta mais fraca da folha real (`q22/A`) | **514‰** oficial · 516‰ `papel.mjs` |
| Os três hashes de fixture | `3e31c36b…`, `26612ad5…`, `81ce134d…` — **intactos** |

## O que ainda não está coberto

**O limiar, e é de propósito.** Esta fatia mede e não interpreta. Não existe classificação de bolha
marcada, vazia ou ambígua, não existe nota, e o tipo `OmrMeasurement` tem teste afirmando que ele
não ganhou nenhum campo de decisão. ADR-0007 exige que o critério do corpus esteja registrado antes
da primeira medição, e o corpus é da fatia seguinte.

**A câmera.** Tudo aqui foi verificado sobre uma digitalização de mesa e sobre buffers sintéticos.
Não há CameraX, não há foto de celular, não há lote nem completude (§8). Três coisas que só a
câmera decide continuam abertas:

- se a faixa dinâmica de uma foto de celular preserva a separação de 424 pontos que a digitalização
  mostrou — o scanner desta folha já lia toner pleno a 64%, e cada câmera comprime diferente;
- se `PaperWhite` precisa passar a normalizar contra o preto do ArUco, que está presente em toda
  captura e é a mitigação óbvia para o item acima;
- quanto a perspectiva de uma foto real custa, contra os ~9 pontos medidos numa distorção
  sintética.

**O caminho do erro humano.** Ninguém fotografou uma folha amassada, dobrada, com sombra de mão ou
fora de foco. As recusas existem e foram vistas falhar, mas contra defeitos que eu construí.
