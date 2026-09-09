## 0. Dívida herdada da 4a, antes de começar

- [ ] 0.1 **Não bloqueia o início.** Na **primeira vez** que um aparelho for conectado para qualquer tarefa desta fatia, rodar `./gradlew :apps:android:connectedDebugAndroidTest` **sem filtro** nele, antes de usá-lo para qualquer outra coisa. Sem aparelho conectado, esta tarefa fica desmarcada e o trabalho segue: 1.1 a 1.3 e a seção 2 são JVM pura, e a primeira tarefa que de fato exige aparelho é a 1.4. Os 46 cenários fecharam na imagem do CI (`platos-atd34`, API 34) em 2026-09-10 e não em telefone real (SDK 36). Resultado: os dois instrumentos com número registrado. Divergência é achado a investigar **naquele momento**, com os dois lado a lado — e não motivo para reabrir o archive da 4a.

## 1. A porta da visão guardada

- [ ] 1.1 Contrato antes do consumidor: a porta da visão, com os verbos nomeados um a um — guardar, ler, apagar da organização —, no espelho de `SessaoGuardada` e `PacotesGuardados` (decisão 4). Resultado: código novo sem consumidor, e nenhum comportamento muda.
- [ ] 1.2 Cenários de JVM sobre a porta: o que foi gravado é o que é lido; uma gravação posterior **substitui por inteiro**; apagar a organização remove a visão dela e não toca nas outras.
- [ ] 1.3 **Ver falhar:** fazer a gravação **emendar** em vez de substituir — mesclar as provas novas com as antigas — e confirmar que só o cenário da substituição fica vermelho, com os outros dois verdes. Reverter, e conferir a reversão **rodando**.
- [ ] 1.4 Adaptador Android no armazenamento **comum**, e não no cifrado (decisão 4: o cifrado é para credencial de rede reutilizável). Resultado: a visão sobrevive ao fechamento do processo, conferido sobre armazenamento real e não sobre dublê.

## 2. A gravação, na consulta bem-sucedida

- [ ] 2.1 Gravar a visão quando a consulta das organizações e a listagem das provas respondem: nome, provas e o instante. Resultado: a visão passa a existir no aparelho; ninguém a lê ainda.
- [ ] 2.2 Testar que consulta que **falha** não grava nada. Resultado: uma falha de rede não substitui visão boa por visão vazia — que seria pior do que não ter visão nenhuma.
- [ ] 2.3 **Ver falhar:** gravar também no caminho de falha, e confirmar que o cenário da 2.2 fica vermelho enquanto o da 2.1 continua verde. Reverter e rodar.

## 3. A leitura no arranque — a primeira parede

- [ ] 3.1 Arranque com credencial e organização guardadas, sem rede: com visão guardada, o aparelho segue para a tela de trabalho a partir dela; sem visão, recusa dizendo que precisa de rede uma vez. Resultado: a 9.2 deixa de parar antes de qualquer listagem.
- [ ] 3.2 Testar que o caso **"servidor respondeu e o vínculo não está lá"** continua derrubando a escolha guardada. Resultado: a decisão 10 da 4a-zero segue intacta no que ela decidiu.
- [ ] 3.3 **Ver falhar, e a mutação precisa isolar a camada:** colapsar de novo os dois casos — tratar "não respondeu" como "respondeu sem a organização" — e confirmar que o cenário do vínculo revogado e o do arranque sem rede caem em **conjuntos disjuntos**, cada um com a sua mutação. Se os dois caírem juntos, a distinção não está sendo medida.

## 4. A listagem sem rede — a segunda parede

- [ ] 4.1 Listagem que falha por rede passa a cair na **última listagem conhecida**, marcada como cacheada; sem listagem conhecida, o comportamento é o de hoje. Resultado: matar o processo deixa de esconder as provas que o aparelho já viu.
- [ ] 4.2 Distinguir, **antes da escolha**, as provas com pacote guardado das que não têm — cruzando a visão com o cache de pacotes. Resultado: o professor sem rede sabe o que vai abrir antes de tocar, em vez de descobrir na barragem.
- [ ] 4.3 **Ver falhar:** apresentar todas como disponíveis, e confirmar que só o cenário da 4.2 fica vermelho.

## 5. A invalidação por revogação observada

- [ ] 5.1 Quando o servidor responde e a organização guardada não está mais entre as do usuário: apagar a visão **e** os pacotes daquela organização, pelo caminho que `sair()` já usa. Resultado: a revogação leva junto o gabarito em cache.
- [ ] 5.2 Testar em JVM que as duas coisas somem, e não só a visão. Resultado: o resíduo aceito do `design.md` fica limitado ao aparelho que nunca mais conecta.
- [ ] 5.3 **Ver falhar:** apagar só a visão, e confirmar que o cenário dos pacotes fica vermelho enquanto o da visão continua verde.

## 6. As telas

- [ ] 6.1 Marca **visual** de leitura cacheada na tela de trabalho, com o instante da última consulta ao lado e uma ação explícita de atualizar. Resultado: procedência visível sem depender de o professor ler uma frase no meio da tela.
- [ ] 6.2 A mesma marca na escolha da prova, mais a distinção da 4.2. Resultado: as duas telas que apresentam dado cacheado o declaram.
- [ ] 6.3 Atualizar que falha **não esvazia a tela**: a visão anterior continua, ainda marcada, e o aplicativo diz que não conseguiu atualizar.
- [ ] 6.4 Registrar como lacuna conhecida que nenhum teste desta base alcança `@Composable`: a escolha da frase e do estado mora fora da tela, e o que fica descoberto é a tela ignorar o parâmetro. É a lacuna que produziu 9b.1 e 9b.2 — ela se paga na seção 7, e não com uma afirmação de que está mitigada.

## 7. Conferência em aparelho — o critério de aceite

- [ ] 7.1 **A 9.2 herdada, na íntegra:** puxar o pacote com rede, fechar o aplicativo com `am force-stop`, ligar o modo avião, reabrir e escanear. Acordar o serviço com `GET /health` **antes** e registrar horário e código, pela razão da 9.3: cold start no meio do teste chega ao aplicativo como falta de rede e faz o teste passar pelo motivo errado. Resultado: o caminho que a fatia 4 inteira existe para produzir.
- [ ] 7.2 Conferir por `uiautomator` que a marca de cache e o instante estão na tela, e que atualizar sem rede mantém a visão em vez de esvaziá-la. Resultado: a lacuna da 6.4 paga em aparelho, e não por teste que não existe.
- [ ] 7.3 Revogar o vínculo no banco, reconectar, e conferir que a visão e os pacotes daquela organização somem do `filesDir` — inspecionando o disco, e não a tela. **Ou registrar por que não é produzível hoje**, no formato da 8.6 da 4a.
- [ ] 7.4 Conferir que o caminho da 4a não regrediu: escolher prova, câmera abre, folha de outra prova continua recusada por identidade.

## 8. Verificação final

- [ ] 8.1 Rodar o **comando cheio do CI**, e não a versão filtrada: `./gradlew build` mais `./gradlew :apps:android:connectedDebugAndroidTest` **sem filtro de classe**. Conferir o número no relatório, e não no código de saída do Gradle.
- [ ] 8.2 Escrever `docs/cobertura-fatia-4a-cache-referencia.md` com **como** cada verificação crítica foi vista falhar — e não que ela passa —, incluindo o que ficou sem teste automático e por quê.
- [ ] 8.3 Rodar `openspec validate slice-4a-cache-referencia --strict`.
- [ ] 8.4 Conferir contra `origin/main`, **arquivo a arquivo**, as negativas que a proposta faz: `packages/domain`, `apps/web`, `apps/api`, `vision/` e `omr/` sem alteração, e nenhuma spec fora de `device-session`. Negativa larga não vale — nomear a exceção, se houver, e mostrá-la no `git diff`.
