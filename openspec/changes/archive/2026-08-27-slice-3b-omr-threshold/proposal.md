## Why

A fatia 3a entregou o instrumento e parou antes do número: ela mede cobertura de tinta por bolha e não diz se alguma está marcada. Sem esse veredito não há resposta, e sem resposta não há nota — a fatia 3 do roadmap (§15), que é o primeiro produto vendável, continua sem existir.

ADR-0010 já reservou o corredor de 200 a 400 permilagem onde o limiar terá de cair, e ADR-0007 proíbe escolhê-lo antes de medir um corpus real. O corpus não existe. Esta fatia o produz, escolhe o limiar dentro do corredor, e vai até a nota.

O momento é agora por uma razão que não é de plano: ADR-0010 declara que, se o corredor não sobreviver à medição, **é a decoração da folha que cede** — tom da letra, trama da faixa, letra fora do círculo, nessa ordem. Descobrir isso depois de haver câmera, tela e lote apoiados na folha atual é descobrir tarde.

## What Changes

- **ADR antes da primeira foto.** ADR-0011 registra o critério de aprovação na forma que ADR-0007 exige: a grandeza, o valor que aprova, o valor que reprova e o que acontece se reprovar. Ele é escrito e aceito antes de qualquer imagem do corpus ser medida — depois disso, qualquer limiar seria racionalização do que já foi visto.
- **Corpus fotografado com câmera de celular, não com scanner.** As duas digitalizações da 2b vieram de scanner com faixa dinâmica comprimida: papel lê 233 de 255 e o miolo preto de um ArUco lê 83, de modo que toner pleno rende no máximo 64% de cobertura naquela imagem (`docs/protocolo-medicao-impressa.md` §321). Repetir em scanner confirmaria o número no meio em que ele não será usado. O corpus entra versionado em `fixtures/`, como as duas digitalizações entraram na 3a.
- **O limiar, medido.** Um número dentro de 200 a 400 permilagem, apurado sobre o corpus pelo `SheetReader` que já existe, registrado com as duas nuvens que o justificam.
- **A leitura passa a interpretar.** `OmrMeasurement` vira veredito por bolha, e o conjunto de vereditos de uma questão vira resposta — com casos explícitos para bolha vazia, cobertura ambígua na vizinhança do limiar, e mais de uma bolha marcada. Nenhum deles vira resposta por omissão.
- **Nota objetiva local.** `answer_key`, `scoring.max_score` e `variants[].positions` já viajam no pacote publicado; falta o consumidor. A nota é aritmética sobre contrato que existe, e fecha a invariante "correção objetiva local é definitiva quando não há discursivas".
- **O limiar é constante do aplicativo validada contra o corredor que a folha declara.** Folha cujo `ink_budget` não contenha o limiar do app é recusada com motivo. É o uso que ADR-0010 previu ao pôr o corredor no artefato publicado.

**O que esta fatia NÃO faz**, de propósito:

- Não usa CameraX, não tem tela, não tem lote nem completude. A entrada continua sendo arquivo de imagem. Isso é a fatia seguinte.
- Não persiste resultado, não cria `assessment_fact`, não sincroniza. A nota é devolvida, não guardada.
- Não muda a folha, o `LayoutMap`, o pacote publicado nem qualquer golden — **a menos que o corpus reprove**, caso em que ADR-0010 manda a decoração ceder e a mudança de folha é o resultado da fatia, não um efeito colateral dela.
- Não acrescenta campo ao `ink_budget`, e portanto não muda o hash do pacote.
- Não trata deviants, região discursiva nem folha avulsa.
- Não toca em dado pessoal: o corpus usa roster sintético, sem aluno real.

## Capabilities

### New Capabilities

- `scoring`: nota objetiva local a partir das respostas lidas de uma folha, contra o `answer_key` e a variante declarados no pacote publicado. Nesta fatia a capacidade vai até a nota em memória; persistência e sincronização entram depois.

### Modified Capabilities

- `capture-omr`: hoje a spec declara que "a grandeza medida é a cobertura, e a leitura não a interpreta". Esta fatia remove essa fronteira: a leitura passa a produzir veredito por bolha e resposta por questão, contra um limiar validado pelo corredor que a folha declara. A medição de cobertura continua exposta como está — o veredito é acrescentado a ela, não a substitui.

## Impact

**Documentos**

- `docs/adr/0011-*.md`: novo, o critério de aprovação do corpus do limiar. Escrito antes da primeira medição.
- `docs/protocolo-medicao-impressa.md`: ganha a seção do corpus fotografado — quantas folhas, em que condições, com que preenchimento.
- `docs/cobertura-fatia-3b.md`: como cada verificação foi vista falhar, conforme a seção **Verificação** do `CLAUDE.md`.

**Código**

- `packages/domain/.../capture/`: veredito de bolha e resposta de questão, sobre `OmrMeasurement` e o `ink_budget` da região. Aritmética inteira, sem pixel — a decisão 1 do `design.md` da 3a aloca ao Android o que toca imagem, e nada aqui toca.
- `packages/domain/.../scoring/`: a nota. É onde a invariante "KMP é a fonte compartilhada de medição, layout, scoring e contratos de domínio" a coloca.
- `apps/android/.../vision/SheetReader.kt`: passa a devolver a leitura interpretada, mantendo a medição bruta acessível.
- `fixtures/`: o corpus fotografado e o que a medição apurou sobre ele.

**Dependências**

Nenhuma nova. OpenCV e ZXing-C++ já entraram na 3a; o resto é aritmética inteira em `commonMain`.

**Dependência humana, e não de código**

Imprimir, preencher a caneta e fotografar é trabalho do mantenedor, e a folha física da 2b não existe mais. A implementação para no meio até o corpus existir — a ordem das tarefas reflete isso.

**LGPD (§16, fatia-limite 3)**

O corpus usa roster sintético: sem aluno real, sem dado de menor, o gatilho não é acionado por esta fatia. O item continua aberto, com fatia-limite 3 e sem dono técnico, e nada aqui o encaminha.

**Referências**

§8 (pipeline de captura), §15 (a fatia 3 é o produto do Basic), §16 (LGPD), ADR-0001, ADR-0007 (critério antes da medição), ADR-0010 (a grandeza, o corredor e o que cede se reprovar), `docs/protocolo-medicao-impressa.md` §10 e §321, `openspec/changes/archive/2026-08-24-slice-3a-omr-measurement/design.md`.
