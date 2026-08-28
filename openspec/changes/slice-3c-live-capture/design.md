## Context

Ver `proposal.md — Why`. O que importa aqui é o que existe e o que falta.

`SheetReader.read(gray, map, region)` e `readInterpreted(..., threshold)` fazem o pipeline inteiro a partir de um `Mat` em cinza. `RegionDetector` acha os quatro ArUcos, monta a homografia e retifica — e a partir da 3b produz também um canvas com sangria para o QR. `ObjectiveScoring` apura a nota. Nada disso muda nesta fatia.

`apps/android/build.gradle.kts` declara **nenhuma** dependência de interface: sem Compose, sem CameraX, sem Room. O manifesto é `<application android:label="PLATOS" />`.

Um número da 3b atravessa esta fatia sem virar código: sob `T = 400`, um preenchimento fraco é lido como branco em um caso a cada seis. É o que a tela de revisão terá de dizer quando existir.

Um segundo número ia virar código e não vira: os ~11 px/mm que a 3b registrou como limite de decodificação do QR. Medida a resolução das nove fotos do corpus — a 3b tinha medido cinco —, ela não separa o que lê do que não lê. Ver `proposal.md — Why`, a decisão 3 abaixo, e `docs/cobertura-fatia-3b.md`, onde o registro foi corrigido.

## Goals / Non-Goals

**Goals**

- Provar que o pipeline lê frame de câmera ao vivo, que é o risco que sobrou.
- Manter a fronteira do testável onde ela está: tudo que produz número continua dirigível por fixture.
- Dizer a quem segura o aparelho em qual momento a sessão está, sem afirmar causa que não foi medida.

**Non-Goals**

- Não fazer lote, completude, som, vibração nem avanço automático. É a 3d.
- Não persistir, não sincronizar, não tocar em dado de aluno.
- Não perseguir desempenho além do necessário para uma leitura acontecer sem esquentar o aparelho.
- Não construir navegação, tema, nem mais de uma tela.

## Decisions

### 1. A fronteira: o analisador recebe `Mat`, e o CameraX só o alimenta

O código que decide continua sendo função de `Mat` para resultado. O que o CameraX acrescenta é conversão de `ImageProxy` para `Mat` em cinza, cadência, e o ciclo de vida — nada que decida.

É a mesma fronteira que a 3a desenhou entre `vision/` e `omr/`, e ela existe pela mesma razão: **os testes instrumentados continuam dirigindo o pipeline pelas fixtures do corpus**, que são o único oracle que existe. Se a decisão migrasse para dentro do analisador de frames, ela sairia do alcance de qualquer teste.

*O que fica sem teste automático, e precisa ser dito:* a conversão `ImageProxy → Mat`, o ciclo de vida do CameraX e a tela. Ver **Risks**.

### 2. A sessão é uma máquina de estados, e o estado é o que a tela desenha

Cinco estados, e nenhum deles é ausência de estado:

```
SemPermissao → Procurando ⇄ NaoLida(motivo) → Lida(payload, nota)
                    ↓
                Recusada(motivo)
```

`Procurando` e `NaoLida` são o mesmo momento com informação diferente: no segundo, os marcadores foram achados e o pipeline parou depois disso. Os dois continuam analisando quadros. `Recusada` não: ela é a folha lida e recusada por identidade ou por corredor, e carrega o motivo que veio do domínio — não um texto inventado na tela.

**`Lida` carrega o payload.** É o que impede resultado obsoleto: quando um quadro fecha com payload diferente do que está na tela, o estado é substituído inteiro. Sem isso, a folha B mostraria a nota da folha A e nada acusaria — para quem lê, as duas telas são idênticas.

*Alternativa descartada — guardar só a nota.* Menos código e o defeito não aparece em teste nenhum, porque o número exibido é plausível nos dois casos. É exatamente a forma de falha silenciosa que esta base persegue.

### 3. O que a sessão distingue vem do estágio do pipeline, e não de um número

`SheetReader.read` achata em `OmrReading.Rejected(reason)` tudo que falha: ArUco não encontrado, QR não decodificado, medição recusada. Para a tela, o primeiro caso não é como os outros dois. Enquanto ninguém aponta para papel, uma frase de recusa piscando é ruído; depois de a folha estar enquadrada, a mesma frase é a informação que falta.

O analisador entrega então um resultado que **preserva o estágio**: folha não encontrada, folha encontrada e não lida — com o motivo que o domínio produziu —, ou folha lida. O estágio já existe dentro de `SheetReader`: é `DetectionOutcome.Failed` contra tudo que vem depois dele. O que falta é não jogá-lo fora. É código do aplicativo, em `apps/android/.../vision/`, e nenhum contrato do domínio muda — `OmrReading` continua exatamente como está.

*Alternativa descartada — classificar pela frase da recusa.* Bastaria procurar "marcador" no texto. É a tela acoplada a uma mensagem de erro: no dia em que a frase mudar, a tela passa a mentir e nenhum teste acusa.

*O que esta decisão substitui.* A versão anterior fazia `DetectionOutcome.Rectified` carregar a resolução medida sobre o papel, para a sessão pedir "aproxime" abaixo de ~11 px/mm. A medição das nove fotos do corpus derrubou a premissa, e o campo saiu junto: sem consumidor, seria número medido que ninguém lê. Ele volta no dia em que uma fatia medir o que de fato prevê a decodificação — e a medição, com o oracle que a conferiu, está registrada em `docs/cobertura-fatia-3b.md`.

*O que esta decisão não faz:* melhorar a decodificação, nem explicar as duas fotos que não decodificam. A fatia para de afirmar uma causa que não tem.

### 4. Cadência: um quadro por vez, o mais recente, e parar ao fechar

`ImageAnalysis` com contrapressão que descarta os quadros acumulados e entrega o mais novo. Analisar todo quadro a 30/s não é possível — o pipeline faz detecção de ArUco, homografia, retificação e decodificação — e a fila acumularia atraso até a tela mostrar o passado.

A análise para assim que uma leitura fecha. Retomar é ação de quem segura o aparelho, e não algo que a sessão faça sozinha — avanço automático é da 3d, e fazê-lo aqui por conveniência entregaria meio lote sem a completude que o torna seguro.

*Alternativa descartada — analisar em toda resolução disponível.* Quanto maior o quadro, mais lenta a detecção. A resolução de análise precisa ser suficiente para o QR decodificar sobre A4, e não a máxima que a câmera oferece — quanto é isso sai do aparelho, e não de um número herdado da 3b.

### 5. O pacote vem de asset, e o `exam_short_id` é conferido

O `ExamPackage` de referência entra como asset do aplicativo. É o que existe até a fatia 4 trazer o pull.

A conferência do `exam_short_id` do QR contra o pacote carregado **não é opcional**: sem ela, uma folha de outra prova seria lida contra este gabarito e produziria nota plausível e errada. A `ObjectiveScoring` já recusa por conjunto de itens divergente, mas a recusa por identificador é anterior e diz a coisa certa ao professor.

### 6. Compose, e uma tela só

Compose porque §13 o declara. Uma tela, sem navegação e sem tema próprio: preview ocupando a área, uma faixa de estado embaixo, e o resultado sobre o preview quando houver.

*Alternativa descartada — `View` com XML.* Evitaria a dependência nova, e §13 já escolheu. Introduzir a interface do projeto na tecnologia que a arquitetura não escolheu, para economizar uma dependência declarada, cria dívida no primeiro lugar onde ela é cara.

## Risks / Trade-offs

**O teste instrumentado não aponta a câmera para papel** → é o limite conhecido desta fatia, e a mitigação é dividir: o analisador é dirigido pelas fixtures do corpus, como hoje; a conversão de quadro é verificada por um teste que a alimenta com um buffer conhecido e confere que o `Mat` resultante bate com o que o pipeline espera; e o que sobra — CameraX e tela — vai para conferência manual, com passo próprio no protocolo de medição. O que **não** vai acontecer é o encanamento ficar sem verificação e sem registro de que ficou.

**Uma leitura pode nunca fechar em campo**, por foco, luz ou tremor, e nenhum teste diria isso → é justamente o que a conferência manual existe para descobrir, e é o motivo de esta fatia existir. Se a leitura ao vivo não fechar em condição de sala de aula, isso é resultado da fatia, e não fracasso dela.

**Resultado obsoleto na tela** → endereçado pela decisão 2, e é o defeito mais caro daqui: silencioso, plausível e sobre nota de aluno.

**O aparelho esquenta** → contrapressão e parada ao fechar. Se ainda assim esquentar, é dado para a 3d, que é quem vai manter a câmera aberta por trinta folhas seguidas.

**Duas dependências novas de interface** → CameraX e Compose, ambas em §13. O custo é tamanho de APK e tempo de build, e aparece na primeira execução do job.

## Migration Plan

Nada a migrar: não há banco, não há artefato publicado e não há estado no aparelho. O APK passa a instalar e abrir, que é a mudança observável.

Reversibilidade: remover a `Activity` do manifesto devolve o módulo ao que ele era. Nenhuma spec existente muda de contrato, então nada depende desta fatia para continuar valendo.

## Open Questions

- **A resolução de análise exata**, em pixels, que o `ImageAnalysis` deve pedir. Sai da primeira execução em aparelho, por tentativa: a menor em que a leitura fecha com folga. Não muda spec, abordagem nem tarefas — muda uma constante. E o corpus não ajuda aqui: nele, resolução não prevê decodificação.
- **Quanto a faixa de estado mostra do motivo** quando a folha foi encontrada e a leitura não fechou. A frase do domínio é precisa e é escrita para quem programa. Decisão de forma, resolvível na tarefa da tela.
