## 1. O módulo vira aplicativo

- [x] 1.1 Declarar CameraX e Compose em `gradle/libs.versions.toml` e `apps/android/build.gradle.kts`, habilitando o compilador do Compose. Resultado: `./gradlew :apps:android:assembleDebug` continua verde, e o tamanho do APK fica registrado para comparação.
- [x] 1.2 Manifesto: `Activity` com launcher, permissão de câmera e a declaração de que a câmera é requisito de hardware. Resultado: o APK instala, abre e aparece na gaveta — o que hoje não acontece.
  **Meia tarefa, e o lint diz isso:** o manifesto aponta para `.scan.ScanActivity`, que só existe a partir da 5.1, e `lintDebug` reprova com `MissingClass`. Até lá `./gradlew build` fica vermelho por esta linha, e `assembleDebug` continua verde. Nada de baseline de lint: o erro é verdadeiro, e some quando a classe nascer.
- [x] 1.3 Embutir o `ExamPackage` de referência como asset do aplicativo, e carregá-lo na abertura. Resultado: o pacote está disponível sem rede, e a origem dele está escrita como provisória até a fatia 4.

## 2. A hipótese dos ~11 px/mm, medida e retirada

A orientação de enquadramento por resolução era o plano original desta seção. Ela caiu na medição, e o que sobrou é a correção do registro que a afirmava. Ver `proposal.md — Why`.

- [x] 2.1 Medir a resolução sobre o papel das **nove** fotos do corpus — a 3b mediu cinco — e cruzar com o que o pipeline de produção faz com cada uma. Resultado: não existe limiar que separe. `prova2-b` falha a 11,47 px/mm e `prova1-angulo` lê a 9,83.
- [x] 2.2 Conferir a medição contra oracle independente antes de concluir: o vão entre centros de marcador que `papel.mjs` acha, contra o lado do ArUco que o detector mede. Resultado: concordam dentro de 0,6 px/mm nas cinco fotos que ambos medem — a hipótese caiu por medição, e não por medição ruim.
- [x] 2.3 Corrigir `docs/cobertura-fatia-3b.md`: a seção que afirmava o limite passa a registrar a medição das nove, a hipótese de tamanho de arquivo que também não separa, e o que ficou sem explicação. Resultado: o registro não afirma mais causa que não mediu.
- [x] 2.4 Corrigir o nome e o comentário de `CorpusInstrumentedTest.foto_distante_demais_e_recusada_no_qr`, que afirmavam a resolução como causa da recusa. Resultado: o teste afirma o fato que sempre verificou — as duas são recusadas no QR — e nada além.
- [x] 2.5 Retirar `paperPxPerMm` de `DetectionOutcome.Rectified` e o teste de oracle que o conferia. Resultado: não fica na árvore número medido que ninguém consome; a medição e o oracle ficam registrados na 3b, e o código volta quando houver consumidor.

## 3. A sessão, sem câmera

- [x] 3.1 Implementar a máquina de estados da sessão: `SemPermissao`, `Procurando`, `NaoLida`, `Lida` e `Recusada`, com `Lida` carregando o payload e `NaoLida` o motivo do domínio. Kotlin puro, dirigido por leituras montadas à mão — sem `Mat` e sem CameraX. Resultado: testável na JVM.
- [x] 3.2 Testar a troca de folha: um resultado com payload diferente substitui o anterior por inteiro. Resultado: o cenário "Troca de folha" da spec passa.
- [x] 3.3 Ver falhar: guardar só a nota em vez do payload e confirmar que 3.2 fica vermelho; reverter. Registrar. **É o defeito mais caro desta fatia** — a folha B mostrando a nota da folha A é plausível na tela e invisível para quem lê.
- [x] 3.4 Fazer o analisador preservar o estágio em que o pipeline parou — folha não encontrada contra folha encontrada e não lida — e testar os dois. Resultado: os dois cenários da spec passam, e a distinção não fica nem sempre num estado nem sempre no outro.
- [x] 3.5 Ver falhar: classificar pelo texto da recusa em vez do estágio, mudar a frase de `DetectionOutcome.Failed` e confirmar que 3.4 fica vermelho; reverter. Registrar. É a alternativa que a decisão 3 descarta, e o defeito dela é silencioso.
- [x] 3.6 Testar as duas recusas — folha de outra prova e corredor que exclui o limiar — conferindo que o motivo que chega à tela é o que o domínio produziu, e não texto inventado. Resultado: os dois cenários da spec passam.
- [x] 3.7 Testar que o resultado preserva a cobertura de cada bolha. Resultado: o cenário "A cobertura sobrevive até a tela" passa.

## 4. A câmera

- [x] 4.1 Implementar a conversão de `ImageProxy` para `Mat` em cinza. Resultado: função isolada, sem lógica de decisão.
- [x] 4.2 Testar a conversão com um buffer conhecido, conferindo dimensões e luminância de pontos escolhidos. Resultado: o único pedaço do encanamento que dá para verificar sozinho está verificado.
- [x] 4.3 Ligar `ImageAnalysis` com contrapressão de quadro mais recente, análise que para ao fechar uma leitura, e a resolução de análise da questão aberta do `design.md`. Resultado: o pipeline roda sobre a câmera.
  A resolução de análise entrou como **1920x1440 provisórios**, com a proveniência escrita no código: o padrão do `ImageAnalysis` é 640x480, e a 3b não ajuda a escolher o número — nela, resolução não previu decodificação. Quem fecha isso é a tarefa 6.1, no aparelho.
- [x] 4.4 Ligar o preview e o pedido de permissão em runtime. Resultado: os dois cenários de permissão da spec são exercitáveis à mão.

## 5. A tela

- [x] 5.1 Uma tela em Compose: preview, faixa de estado, e o resultado sobre o preview quando houver — nota, máximo, e as pendências com motivo.
- [x] 5.2 Conferir que a nota não é apresentada como fechada quando há pendência. Resultado: o cenário "Folha com pendência" passa.

## 6. Conferência em aparelho — depende do mantenedor

Feita num **Poco X8 Pro**, seis folhas — três pares, cada par preenchido igual e distinto dos outros —,
dezenas de repetições, resultado consistente por condição. **Nenhuma nota errada apareceu sem
pendência ao lado**, em nenhuma repetição — o corredor de ADR-0011 segura a degradação sob captura
ao vivo. Registro em `docs/cobertura-fatia-3c.md`.

- [x] 6.1 Instalar o APK num aparelho real e escanear uma folha impressa da prova de referência. Resultado: a leitura fecha. Abaixo de ~49 cm, em menos de 1 s; entre ~49 e ~52 cm, em 2 a 2,5 s; acima de ~52 cm não fecha neste aparelho. Entre ~49 e ~52 cm a nota às vezes desce um ponto, nunca mais.
- [x] 6.2 Conferir a distinção dos dois momentos. Resultado: os dois foram vistos. Câmera longe do papel, `Procurando a folha…`; folha plana e enquadrada com o QR coberto por um post-it, `Achei a folha e nao consegui ler: nenhum QR decodificado na ROI que o mapa declara` — o motivo que chega à tela é o que o pipeline produziu, e a leitura fecha sozinha ao destapar o QR, o que confirma que o estado é transitório e a análise não parou. Acima de ~52 cm a leitura deixa de fechar neste aparelho.
- [x] 6.3 Escanear duas folhas em sequência e conferir que a segunda substitui a primeira na tela. Resultado: substitui **depois de tocar em "Escanear outra folha"**, que é o desenho — `holdsResult` segura o resultado e a análise para. Sem o botão, a nota da folha anterior fica na tela com a folha seguinte já enquadrada. O defeito de 3.3 não foi reintroduzido; a ergonomia disso é da 3d.
- [x] 6.4 Escanear a folha de teste de impressão contra o pacote da prova. Resultado: **nenhuma nota é apresentada**, que é a propriedade que protege quem corrige. Quem barra não é a conferência de identidade e sim a geometria: os quadriláteros têm alturas diferentes (31 mm contra 85 mm), a reprojeção estoura o teto de 6 px e a detecção falha antes do QR, então o `exam_short_id` nunca é comparado. A tela diz "procurando".
- [ ] 6.4b Conferir a recusa por identidade em aparelho, contra uma folha de **outra prova com região do mesmo tipo**. **Não executável nesta fatia** — não existe uma segunda prova impressa, e `prova1`/`prova2` do corpus são duas folhas da mesma. Vai para a fatia 4, que puxa pacotes e é onde passa a haver mais de uma prova. **A funcionalidade não sai daqui**: o requisito continua `SHALL` na spec de `scan-session`, a implementação está em `ScanSession.resultOf` e o cenário está verificado na JVM pela tarefa 3.6. O que falta é oráculo físico, e não código.
- [x] 6.5 Acrescentar a `docs/protocolo-medicao-impressa.md` a seção de conferência da captura ao vivo, com esses passos. Resultado: quem repetir depois não precisa reinventar o roteiro.
- [x] 6.6 Conferir que a nota não fecha errada em silêncio sob luz direta e inclinação acentuada. Resultado: **não fecha**. Nas duas condições a nota não bate com o papel e vem com várias pendências; em dezenas de repetições sobre seis folhas nunca apareceu nota errada sem pendência. O corredor de ADR-0011, apurado sobre nove fotos paradas, segura a degradação sob captura ao vivo neste aparelho. A obrigação de reexaminá-lo sob outros aparelhos continua de pé, agora sem defeito conhecido atrás dela.

## 7. Verificação final

- [x] 7.1 Rodar `./gradlew build` e `:apps:android:connectedDebugAndroidTest`. Resultado: **verde** — `build` inteiro, `lintDebug` incluído (a `ScanActivity` existe desde a 5.1), 43 instrumentados e 19 de JVM.
- [x] 7.2 Completar `docs/cobertura-fatia-3c.md` com como cada verificação foi vista falhar, **e com o que ficou sem teste automático e por quê** — o encanamento de CameraX e a tela.
- [x] 7.3 Rodar `openspec validate slice-3c-live-capture --strict`. Resultado: válido.
- [x] 7.4 Conferir que nenhum golden, hash de pacote ou spec existente mudou, e que `packages/domain`, `apps/api` e `apps/web` não foram tocados.
