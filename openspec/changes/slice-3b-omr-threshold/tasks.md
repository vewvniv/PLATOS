## 1. O critério, antes de qualquer medição

- [x] 1.1 Escrever `docs/adr/0011-criterio-do-limiar-do-omr.md` com o critério da decisão 2 do `design.md`: a grandeza, a regra `T = (V + C) / 2` restrita a 200–400, a margem `M = 50`, o que aprova, o que reprova, e o que acontece se reprovar. Resultado: ADR aceito, com data anterior à primeira foto do corpus.
- [x] 1.2 Acrescentar a `docs/protocolo-medicao-impressa.md` a seção do corpus fotografado: duas folhas, as três condições de luz e ângulo, as duas classes de preenchimento, roster sintético e o comando de medição. Resultado: o mantenedor consegue produzir o corpus lendo só o protocolo.
- [x] 1.3 Commit destes dois documentos **antes** de qualquer código ou foto desta fatia. Resultado: o histórico prova que o critério precede a medição, que é o que ADR-0007 exige.

## 2. Veredito de bolha, sem depender de foto

- [x] 2.1 Criar em `packages/domain/.../capture/` o veredito de bolha (marcada, vazia, indecisa) sobre `OmrMeasurement`, recebendo limiar e margem como parâmetros, em permilagem inteira. Resultado: nenhuma constante de limiar no código ainda.
- [x] 2.2 Testar as bordas com valores construídos à mão: `T−M−1`, `T−M`, `T−1`, `T`, `T+1`, `T+M`, `T+M+1`. Resultado: o veredito de `T` é o declarado e cada borda cai do lado previsto.
- [x] 2.3 Implementar a validação do limiar contra o `ink_budget` da região: corredor que não contém o limiar recusa a folha, com mensagem que nomeia limiar e corredor. Resultado: os dois cenários da spec passam.
- [x] 2.4 Ver falhar: estreitar o corredor da fixture para excluir o limiar de teste e confirmar que a leitura recusa; reverter. Registrar em `docs/cobertura-fatia-3b.md`.

## 3. Resposta de questão, sem depender de foto

- [x] 3.1 Implementar a resposta por questão a partir dos vereditos: alternativa marcada, em branco, múltipla marcação, indecisa. Resultado: cada questão declarada aparece exatamente uma vez.
- [x] 3.2 Testar os quatro casos com vereditos montados à mão, sem passar por imagem, incluindo questão com indecisa e nenhuma marcada. Resultado: os cinco cenários da spec passam.
- [x] 3.3 Testar que duas bolhas marcadas não viram resposta por desempate de cobertura. Resultado: o caso devolve múltipla marcação nomeando as alternativas, mesmo com coberturas bem diferentes.
- [x] 3.4 Ver falhar: introduzir desempate por maior cobertura e confirmar que 3.3 fica vermelho; reverter. Registrar em `docs/cobertura-fatia-3b.md`.

## 4. Nota objetiva

- [x] 4.1 Criar `packages/domain/.../scoring/` com a apuração contra `answer_key`, `points` e `scoring.max_score`, casando cada resposta lida com a entrada de gabarito do item. O `LayoutMap` da variante já resolveu posição em item na publicação (`LayoutEngine.kt:315`, `Publish.kt:70`), e a apuração não o refaz. Resultado: nota apurada em memória, sem persistência.
- [x] 4.2 Implementar as recusas: variante ausente do pacote; payload sem variante com pacote de mais de uma; conjunto de itens divergente do que a variante declara em `positions`; e item sem entrada no gabarito. Resultado: os quatro cenários de recusa da spec passam com mensagem que nomeia a divergência.
- [x] 4.3 Implementar o fechamento da nota: em branco vale zero e é definitivo; múltipla marcação e indecisa entram na lista de pendências, e a nota não é declarada fechada. Resultado: o resultado traz pontuação apurada, máximo da prova e máximo em disputa.
- [x] 4.4 Testar a nota contra o `answer_key` da fixture de referência, com uma folha toda correta, uma toda errada e uma mista, mais o caso de payload sem variante contra o pacote de uma variante só. Resultado: os valores batem com a conta feita à mão sobre o gabarito.
- [x] 4.5 Ver falhar: trocar uma resposta do gabarito da fixture e confirmar que a nota muda; marcar duas bolhas numa questão e confirmar que a nota deixa de fechar. Reverter e registrar em `docs/cobertura-fatia-3b.md`.
- [x] 4.6 Testar determinismo e ausência de efeito: mesma entrada duas vezes, resultado idêntico, leitura e pacote inalterados.

## 5. Ligar a leitura à interpretação

- [x] 5.1 Fazer `SheetReader` devolver a leitura interpretada mantendo a cobertura de cada bolha recuperável ao lado do veredito. Resultado: o cenário "a cobertura sobrevive ao veredito" passa.
- [x] 5.2 Confirmar que os testes instrumentados da 3a continuam verdes sobre as digitalizações versionadas. Resultado: `:apps:android:connectedDebugAndroidTest` passa sem mudança de expectativa de medição.
- [x] 5.3 **Acrescentada durante a execução.** Corrigir a zona de silêncio do QR: `qr.v` é zero no mapa, a região retificada começava no topo do QR, e a decodificação dependia de a homografia deixar um ou dois pixels de folga. `RegionDetector` passa a retificar um canvas próprio para o QR, com 4 mm de sangria, sem tocar na geometria que produz cobertura. Resultado: as seis fotos do corpus decodificam; sem a sangria, nenhuma decodifica.
- [x] 5.4 Ver falhar: `QR_BLEED_MM = 0` derruba `o_canvas_do_qr_tem_zona_de_silencio_em_volta` com `y=0` e faz as seis fotos do corpus pararem de decodificar — enquanto a digitalização de mesa da 2b segue verde, que é por que a fatia 3a não viu o defeito. Registrado em `docs/cobertura-fatia-3b.md`.

## 6. O corpus — depende do mantenedor

- [ ] 6.1 Imprimir duas folhas da prova de referência com roster sintético, na mesma impressora da conferência de papel se possível. Resultado: duas folhas físicas, e a escala conferida pela folha de teste de impressão.
- [ ] 6.2 Preencher a caneta, uma alternativa por questão, conforme a instrução impressa na folha. Resultado: 80 bolhas da classe que decide o critério.
- [ ] 6.3 Preencher de propósito de leve um subconjunto declarado, fora do critério. Resultado: bolhas da classe observacional, identificadas como tal.
- [ ] 6.4 Fotografar com câmera de celular nas três condições do protocolo: luz frontal, sombra parcial sobre a região, ângulo de 20 a 30 graus. Resultado: no mínimo seis fotos.
- [ ] 6.5 Versionar as fotos em `fixtures/`, resolvendo o formato e o tamanho conforme a questão aberta do `design.md`. Resultado: corpus reproduzível a partir do repositório.

## 7. Medir o corpus e fixar o limiar

- [ ] 7.1 Medir todas as fotos com o `SheetReader` e registrar cobertura bolha a bolha. Resultado: a tabela bruta, versionada ao lado das fotos.
- [ ] 7.2 Medir as mesmas fotos com `tools/parity/papel.mjs` e comparar. Resultado: as duas implementações concordam dentro da tolerância, ou a divergência é investigada antes de qualquer número ser aceito.
- [ ] 7.3 Apurar `V`, `C` e o vão, e aplicar a regra do ADR-0011 para obter `T`. Resultado: um número, derivado por aritmética declarada antes da medição.
- [ ] 7.4 Confrontar com o critério: aprova ou reprova. Se reprovar, parar e executar a ordem de ADR-0010 — tom da letra, trama da faixa, letra fora do círculo — reimprimindo e voltando a 6.1, e aditar o ADR-0011 com o resultado obtido.
- [ ] 7.5 Registrar em `docs/cobertura-fatia-3b.md`: `V`, `C`, o vão, `T`, quantas bolhas, quantas fotos, em que condições e com que aparelho.

## 8. Fechar a fatia

- [ ] 8.1 Declarar `T` e `M` como constantes do aplicativo, substituindo os parâmetros de teste. Resultado: a leitura real usa o número medido, e a validação contra o corredor passa a valer sobre ele.
- [ ] 8.2 Ver falhar: mover `T` um passo além do corredor da folha de referência e confirmar que a leitura recusa; reverter. Registrar.
- [ ] 8.3 Medir uma das fotos do corpus de ponta a ponta, da imagem à nota, e conferir a nota contra o gabarito apurado à mão a partir do que foi preenchido em 6.2. Resultado: o oracle da nota não passa por nenhum código desta fatia.
- [ ] 8.4 Acrescentar ao CI um passo que prova que a verificação do limiar continua capaz de falhar, no mesmo espírito do passo "A medicao de tinta continua capaz de falhar" que já existe.
- [ ] 8.5 Completar `docs/cobertura-fatia-3b.md` com como cada verificação foi vista falhar, e não só que ela passa.

## 9. Verificação final

- [ ] 9.1 Rodar `./gradlew build` e `./gradlew :packages:domain:testAndroidHostTest`. Resultado: verde.
- [ ] 9.2 Rodar `./gradlew :apps:android:connectedDebugAndroidTest` em aparelho ou emulador. Resultado: verde, incluindo os testes da 3a.
- [ ] 9.3 Rodar `openspec validate slice-3b-omr-threshold --strict`. Resultado: válido.
- [ ] 9.4 Conferir que nenhum golden e nenhum hash de pacote mudou — salvo se 7.4 reprovou, caso em que a mudança de folha é commit de contrato separado, anterior aos consumidores.
