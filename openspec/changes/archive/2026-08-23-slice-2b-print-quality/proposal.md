## Why

§15 chama a 2b de "prova fixa completa · marcadores de captura · qualidade de impressão — escopo original da 2", e §16 lhe dá a única fatia-limite de risco desta etapa: **impressão dos ArUcos**, porque "depois que houver folha distribuída, corrigir marcador significa reimprimir".

A 2a entregou o pacote publicado e a folha derivada dele. A folha, porém, ainda não é a que §7 descreve. Ela não tem cabeçalho, não diz ao aluno como preencher a bolha, não tem rodapé, e não tem **nenhuma** das mitigações de erro de transcrição que §7 lista — faixa alternada, agrupamento de questões, letra da alternativa dentro do círculo. E não existe forma de reprovar uma impressora antes de ela imprimir uma turma inteira: o `docs/protocolo-medicao-impressa.md` é manual e roda sobre a prova de referência, que é o artefato errado para essa pergunta.

O que faz esta fatia ter dente não é o cabeçalho. É que **as três mitigações de §7 põem tinta decorativa dentro da região que o OMR vai medir** — faixa de 4,5% sob as linhas de bolha, letra cinza dentro do círculo. Tinta decorativa que passe do orçamento vira resposta falsa, e o modo de falha é silencioso, tardio e caro: aparece na fatia 3, sobre folha já impressa, como acerto ou erro que ninguém marcou. O momento de fixar esse orçamento é **antes** de a tinta existir e antes de o OMR existir, que é o que ADR-0007 já determina para toda medição que decide.

## What Changes

**Contrato (KMP)**

- `DrawRect.fill` deixa de ser porcentagem inteira e passa a **permilagem** de preto (0–1000). §7 pede trama de 4,5%, que não é representável em porcentagem inteira; publicar geometria com a trama arredondada para 4% ou 5% seria congelar num artefato hasheado um valor que ninguém escolheu.
- `DrawText` ganha **tom** na mesma escala. Sem ele não há letra cinza dentro do círculo, e um renderizador que decidisse o tom por conta própria reintroduziria a divergência que a fatia 1 inteira existiu para eliminar.
- O `LayoutMap` passa a declarar o **orçamento de tinta** da região escaneável: quanto de tinta decorativa é admitido dentro de uma bolha vazia. Fica no artefato publicado porque quem consome é o OMR da fatia 3, que lê o pacote e não este repositório.

**Layout Engine**

- Cabeçalho da folha: título da prova e instrução de preenchimento, acima da região de gabarito, que continua no topo da página 1.
- Rodapé com numeração de página, dentro da margem inferior.
- Legibilidade do gabarito (§7): faixa alternada a 4,5% em grupos de 3 a 5 questões, e letra da alternativa impressa em cinza dentro do círculo.
- Validação do `LayoutMap` recusa trama acima de 8% e tinta decorativa acima do orçamento declarado.
- Um segundo layout, o da **folha de teste de impressão**, produzido pelo mesmo engine e pelas mesmas primitivas.

**Impressão**

- A folha de teste de impressão como **documento próprio**: marcadores no lado nominal, QR de payload conhecido, vão de referência para medir a escala da impressão, amostras de trama e uma linha de bolhas. Serve para reprovar uma impressora antes da primeira turma — a mitigação que §16 nomeia para este risco.
- Guarda automática de **monocromia**: nenhum pixel cromático no documento rasterizado.
- Guarda automática de **teto de trama**: nenhuma tinta chapada acima de 8%.
- Guarda automática de **orçamento de tinta na bolha vazia**, medida sobre o raster do documento por oracle que não compartilha código com o engine que a produziu.
- `docs/protocolo-medicao-impressa.md` ganha a seção da folha de teste e a inspeção de trama a olho.

**Decisão permanente**

- ADR novo: tinta decorativa dentro de região escaneável tem orçamento declarado no artefato publicado, com valor que aprova, valor que reprova e o que acontece se reprovar — no espírito de ADR-0007, aplicado a uma medição que a fatia 3 vai fazer sobre geometria que esta fatia congela.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `layout-engine`: emissão de cabeçalho, instrução e rodapé; agrupamento e faixa alternada do gabarito; letra da alternativa dentro do círculo; tom e trama declarados em permilagem; orçamento de tinta declarado e validado sem renderizar; layout da folha de teste de impressão.
- `print`: tom e trama desenhados a partir do que o mapa declara; documento monocromático com teto de trama; orçamento de tinta verificado sobre o documento; a folha de teste de impressão como documento que reprova uma impressora.

## Impact

**Alterado**

- `packages/domain` — `DrawRect`, `DrawText`, cabeçalho do `LayoutMap`, `LayoutEngine`, `LayoutMapValidation`, `CaptureGeometry`
- `apps/web/src/renderer.ts` e `apps/android/.../LayoutMapRenderer.kt` — tom e trama
- `apps/web/scripts` — geração do documento da folha de teste
- `tools/parity` — guardas de monocromia, teto de trama e orçamento de tinta
- `fixtures/prova-referencia.layout.json` e `.package.json` — golden e pacote regravados
- `docs/protocolo-medicao-impressa.md`
- `docs/adr/` — ADR novo do orçamento de tinta

**Dependências novas**: nenhuma.

**Explicitamente NÃO alterado**

- **Dados impressos do aluno, folha avulsa e variantes** — §15 os põe na fatia 7. A folha continua sendo a mesma para todos os alunos, e o QR continua com `student_token` e `variant` vazios, como o protocolo já registra.
- **Segunda face de fonte** — §7 pede comando da questão em semibold e número em bold. Fica fora: uma face nova toca a medição de texto, que é o keystone da fatia 1, a paridade nos três alvos e o golden. É fatia própria, e sua ausência não impede nenhuma verificação desta.
- **Região discursiva, `ESSAY_REGION` e os ArUcos por questão** — fatia 5. O risco de §16 fala em "4 por questão discursiva", mas a superfície que existe hoje é a região de gabarito, e é sobre ela que a folha de teste pode reprovar uma impressora agora.
- **Cartão-resposta destacável (D31) e densidade em três níveis (D37)** — §7 os declara opcionais e de economia de papel; nenhum é exercitado pela pergunta desta fatia.
- **Colunas adaptativas (D32)** — o perfil já fixa duas colunas e a fatia não mexe em paginação.
- **OMR** — esta fatia declara e verifica o orçamento de tinta; medir bolha preenchida em foto é a fatia 3.
- **Interface de autoria e onboarding** — a folha de teste é gerada pelo mesmo caminho versionado da prova de referência. Onde ela aparece para o professor é a fatia que tiver UI.
