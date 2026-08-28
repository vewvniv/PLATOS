## Why

O pipeline de §8 nunca rodou sobre frame de câmera. A fatia 3a o provou sobre digitalização de mesa, a 3b sobre nove fotos paradas, e nenhuma das duas prova que uma **sequência** de frames — com desfoque de movimento, autofoco caçando e exposição deslizando — produz leitura. É o último risco não medido da fatia 3.

E não existe aplicativo. `apps/android` tem oito arquivos de biblioteca e um manifesto de uma linha, sem `Activity`, sem permissão e sem tela. O `SheetReader` recebe um `Mat` vindo de arquivo. Instalar o APK põe um pacote sem ícone no aparelho.

Esta fatia entrega a menor coisa instalável que fecha o caminho inteiro na mão de uma pessoa: apontar a câmera para uma folha impressa e ver a nota.

**Uma hipótese da 3b caiu aqui, e o escopo encolheu com ela.** A 3b registrou que abaixo de ~11 px por milímetro de papel o QR não decodifica, e esta fatia ia transformar esse número em orientação de enquadramento na tela. Medida a resolução das nove fotos do corpus — a 3b tinha medido cinco —, a separação não existe: `prova2-b` falha a 11,47 px/mm e `prova1-angulo` lê a 9,83. Não há limiar que separe. A orientação por resolução sai desta fatia, o registro da 3b é corrigido, e a sessão passa a distinguir apenas o que ela de fato sabe: **não achei folha** e **achei a folha e não consegui ler**.

## What Changes

- **O módulo vira aplicativo.** `Activity` com launcher, permissão de câmera pedida em runtime, e o mínimo de Compose para haver tela. Os três estão declarados em §13 e não exigem ADR, mas entram do zero: o `build.gradle.kts` não declara nenhum deles hoje.
- **CameraX com preview e análise de frame**, alimentando o `SheetReader` que já existe, com o limiar `OmrThreshold.MEDIDO_NA_FATIA_3B`.
- **A tela mostra três coisas:** o preview, orientação de enquadramento enquanto procura, e o resultado — a nota, o máximo e as pendências quando houver, com a cobertura de cada bolha recuperável para quem for revisar.
- **O `ExamPackage` vem de asset embutido**, e o `exam_short_id` do QR é conferido contra ele. Pull de referência imutável é fatia 4; antes disso não há de onde o pacote vir.
- **A sessão distingue "não achei folha" de "achei e não consegui ler".** Os dois momentos já são distintos dentro do pipeline — o primeiro é a detecção dos ArUcos falhando, o segundo é tudo depois dela —, e hoje chegam achatados numa frase só. Distinguir os dois é honesto e ajuda; afirmar distância não é nenhum dos dois.
- **O registro da 3b é corrigido**, no documento de cobertura e no comentário do teste do corpus, que afirmavam a resolução como causa da recusa. O teste continua verde: ele sempre afirmou o fato, e era a prosa em volta que afirmava mais.

**O que esta fatia NÃO faz**, e onde cada coisa vai:

- Não faz lote, avanço automático, som, vibração, chips de completude, conjunto esperado por variante nem recusa de variante divergente. É a 3d, e §8 descreve lote e completude como coisas separadas da captura.
- Não persiste nada. A nota é mostrada e some; Room e outbox são fatia 4.
- Não toca em roster nem em dado de aluno. O `student_token` do QR vem vazio até a fatia 7, então nada aqui coleta dado pessoal e o gatilho do ADR-0012 não é acionado.
- Não trata região discursiva, deviants nem modo de cor.
- Não investiga o que de fato faz o QR de duas fotos do corpus não decodificar. Resolução e tamanho de arquivo foram medidos e caíram; nove fotos são amostra pequena demais para concluir, e isso é fatia de medição própria.
- Não muda o contrato de `capture-omr` nem de `scoring`. Os dois ganham um chamador novo, e nada além disso.

## Capabilities

### New Capabilities

- `scan-session`: escanear uma folha com a câmera do aparelho e ver o resultado. Cobre a permissão, o que acontece enquanto nenhuma folha é encontrada, as recusas que o professor precisa entender — folha de outra prova, folha cujo corredor exclui o limiar — e o que o resultado apresenta. Nesta fatia a sessão é de uma folha; lote e completude entram depois.

### Modified Capabilities

Nenhuma. `capture-omr` e `scoring` são consumidos como estão.

## Impact

**Código**

- `apps/android/src/main/AndroidManifest.xml`: `Activity` com launcher, permissão de câmera, e a declaração de que a câmera é requisito de hardware.
- `apps/android/build.gradle.kts`: CameraX e Compose. As duas primeiras dependências de interface do projeto.
- `apps/android/.../scan/`: o analisador de frame, a máquina de estados da sessão e a tela.
- `apps/android/src/main/assets/` ou equivalente: o `ExamPackage` de referência embutido.
- `apps/android/.../vision/CorpusInstrumentedTest.kt`: o comentário e o nome do teste que afirmavam a resolução como causa da recusa no QR.

**Registro**

- `docs/cobertura-fatia-3b.md`: a seção que afirmava o limite de ~11 px/mm, corrigida com a medição das nove fotos e com o que a caiu junto. A 3b está arquivada e em `main`; o documento é o registro dela, e registro errado se corrige onde está.

**O que não muda**

`packages/domain`, `apps/api`, `apps/web`, as migrations, os goldens e o hash do pacote. Nenhuma spec existente muda de contrato.

**Dependência de conferência humana**

O teste instrumentado não consegue apontar a câmera para papel. O que é automatizável é o analisador sobre as fixtures do corpus, que continuam sendo o único oracle; o encanamento de CameraX e a tela dependem de conferência em aparelho real, com a folha impressa na mão. Isso precisa estar escrito como limite conhecido, e o protocolo de medição ganha a seção correspondente.

**Referências**

§8 (pipeline, e o que é lote e completude), §13 (CameraX, Compose e Room alocados ao Android), §15 (a fatia 3 é o produto do Basic) · ADR-0010, ADR-0011 (o corredor e o limiar) · `docs/cobertura-fatia-3b.md` (a resolução que não separa, e o preenchimento fraco lido como branco em um caso a cada seis) · `apps/android/.../vision/SheetReader.kt`.
