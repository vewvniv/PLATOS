# Proposal

## Why

A fatia 5 entrega a parcial objetiva (5b) e a resposta discursiva guardada no aparelho (5c-0, 5c-1), mas **nenhuma nota discursiva existe em lugar algum**: a parcial nunca é fechada (`scoring`, "nunca fechada") e o servidor só sabe gravar o resultado objetivo (`result-sync`). A correção manual é o caminho da §10 ("Correção manual de discursiva: local, entra no outbox") e a fonte da §9.2 ("a nota não muda") para o corpus de medição. Sem o contrato e o servidor dela, a tela do professor (5c-3) não tem para onde enviar, e o corpus (item 2 de `docs/plano-da-fatia-5-restante.md`) não tem referência contra a qual comparar.

**Esta é a primeira de duas mudanças.** O plano (`docs/plano-da-fatia-5-restante.md`, item 1) e a regra 3 do `CLAUDE.md` mandam dividir: tocar `scan-session`, `result-sync`, `scoring` e a api passa de duas capabilities. Pela regra 1 (contrato antes do consumidor), **5c-2 = contrato e servidor**; **5c-3 = o aparelho** (tela de nota, outbox, envio, e o `WorkManager` do teto de 30 dias). Confirmado pelo mantenedor em 2026-10-01.

## What Changes

Decisões do mantenedor de 2026-10-01 que esta proposta fixa:
- A nota é dada **por questão discursiva**, de 0 até a pontuação que o pacote declara para ela, e **pode ser fracionária** (1.5, 1.75). Correção do mantenedor em 2026-10-01, depois da primeira versão desta proposta, que a supunha inteira.
- A nota do professor é uma **revisão nova da mesma folha** (mesma prova e mesmo token), com origem `teacher`; a parcial continua legível, e a nova passa a ser a corrente.

O que muda, em comportamento:
- **`scoring`:** a nota do professor sobre as discursivas **completa** a parcial objetiva, sem recalcular a parte objetiva. Total = pontos objetivos + pontos das discursivas, **em aritmética exata** (o total do resultado do professor deixa de ser inteiro); máximo = o da prova; **fechada** só quando não resta pendência objetiva. Cada discursiva leva a sua evidência (item, valor, pontos dados). Recusa pontuação fora de 0..valor, negativa, com mais de 2 casas, não numérica, questão que não é discursiva da variante, discursiva faltando ou repetida, e prova só objetiva.
- **`result-sync`:** o servidor passa a aceitar e gravar esse resultado como **revisão nova**, append-only e idempotente como os demais, com a proveniência conferida contra o pacote publicado e as discursivas conferidas contra o **mesmo pacote** (oráculo único). O fato registra a **origem** (`teacher`) e o **caminho** da correção (`image`: o professor viu a imagem; é o campo que §9.2 pede para o corpus). **A revisão humana vence a automática da mesma captura, qualquer que seja a ordem de chegada** (invariante: revisão humana vence qualquer resultado automático): uma parcial que chega depois da nota do professor, por reenvio ou reordenação, fica legível e **não** passa a ser a corrente.
- O resultado do professor é **autocontido** (leva a parte objetiva e as notas discursivas), de modo que o servidor o aceita mesmo sem ter recebido antes a parcial da captura.

**Premissas minhas, para a revisão** (nenhuma foi perguntada ao mantenedor):
1. A nota cobre **todas** as discursivas da variante de uma vez; nota de parte delas não é representável. Caderno incompleto e "registra o que faltou" são o item 4 (`slice-5e-…`), que acrescenta campo a este contrato.
2. A nota é **decimal exata com até 2 casas** (cobre 1.5 e 1.75; 1.333 não), nunca ponto flutuante binário: com float, `0.1 + 0.2 ≠ 0.3` quebraria "a soma por questão é igual ao total" e a idempotência. `1.5` e `1.50` são o mesmo valor. **O valor da questão no pacote segue inteiro e imutável.** Resultado objetivo, parcial e resultado já gravado não mudam: inteiro é caso particular.
3. Só `teacher`/`image` são aceitos como origem/caminho novos; `ai` e `text` (OCR) são fatia 8 e o servidor os recusa por ora.
4. O fato **não** ganha autoria (quem corrigiu): segue a regra da `result-sync` de que o resultado é fato sobre a folha. Se a contestação de nota exigir autoria, é mudança própria.

**Não será alterado** (limite desta mudança):
- **Todo o aparelho:** `scan-session`, tela de nota, outbox, envio, `WorkManager`, o teto de 30 dias das respostas, o que a tela diz sobre "nada foi guardado". **O aparelho não envia nota do professor depois desta mudança**: o servidor a aceita e ninguém a manda até a 5c-3.
- **Nenhuma imagem sobe**, e o corpo da nota não leva imagem nem referência a arquivo (política §6.4; `scan-session`, "A resposta não sai do aparelho").
- A parcial objetiva, a apuração parcial, a rota atual para resultado objetivo e parcial (comportamento e corpo), e o que `result-sync` diz de fila, rede e apagamento do pendente.
- Resolver pendência **objetiva** por revisão humana (`grade_override`), nota por critério de rubrica (`grading_criterion_score`), nota por habilidade, e qualquer origem `ai`/`text`.
- `apps/web`, o corpus de medição, a retenção executável da classe B.
- **A forma física do fato** (coluna e revisão novas em `grading_result`, ou tabela própria) **é ADR desta mudança**, decidida no brainstorming do Superpowers antes de qualquer migration; este documento não a decide.

## Capabilities

### New Capabilities
<!-- Nenhuma: as duas capabilities que a mudança toca já existem. -->

### Modified Capabilities
- `scoring`: um requisito acrescentado — a nota do professor completa a parcial objetiva (total, máximo, fechada, evidência por discursiva e recusas). Nenhum requisito existente muda de texto.
- `result-sync`: três requisitos acrescentados — o servidor aceita a nota do professor como revisão nova (origem e caminho registrados, proveniência e discursivas conferidas contra o pacote publicado), a revisão humana prevalece sobre a automática da mesma captura em qualquer ordem de chegada, e o corpo da nota não leva imagem, referência a arquivo nem dado pessoal.

## Impact

- **Domínio KMP** (`domain`): a composição parcial + notas → resultado completo, e o contrato de transporte do resultado (`ResultSubmissionDto`, `ADR-0015`). Fica no KMP porque o servidor valida com o **mesmo** código que o aparelho usará (regra 7).
- **API** (`apps/api`): a rota de resultados e a conferência de proveniência. **Banco** (`supabase/migrations`): a migration que o ADR decidir; as invariantes da `grading_result` (append-only por gatilho, RLS por `organization_id`) não relaxam.
- **Pontuação fracionária:** hoje `points` e `earned` são inteiros no domínio, no contrato de transporte e na `grading_result`/`answer_observation` (que também tem `check` sobre eles). Esta mudança os alarga **só para o resultado do professor**. A representação exata no fio (por exemplo decimal em texto, e não número JSON lido como `Double`) e o tipo da coluna são decisão do ADR; o requisito é que **nenhuma precisão se perca** entre aparelho, fio e banco, e que o resultado objetivo e a parcial continuem exatamente como estão.
- **Nenhum** código Android, nenhuma tecnologia nova.
- **Privacidade:** a nota do professor é classe B, como o resultado objetivo; sem nome, turma ou matrícula (I5).
- **Dívida (§16, P27).** `node tools/divida/divida.mjs` (2026-10-01): fatia corrente `5c`; o nome `slice-5c-2-…` não a move. Linhas que esta mudança alcança, e o que o **archive** diz de cada uma:
  - **`O teto de 30 dias das respostas só roda quando o aplicativo abre` (`5c`)**: **não é paga aqui** (é do aparelho). O archive diz que o veículo passa a ser a **5c-3** (ainda não proposta), que o token segue `5c` e que a 5c-3 a paga ou reagenda. Silêncio não é reconciliação.
  - **`Migration não é aplicada por nenhum pipeline` (`antes-de:migration-da-5-em-producao`)** e **`Implantar a API da 5a …` (`antes-de:implantar-api-da-5a`)**: esta mudança acrescenta uma migration e muda a rota; **nenhuma das duas vai a produção por ela**. O archive registra que a migration existe e que os eventos seguem por declarar.
  - **`A retenção executável da classe B` (`antes-de:primeira-eliminacao`)**: passa a incluir a nota do professor entre os fatos da classe B; sem prazo novo.
  - **`A guarda de dívida não lê a tabela "Aberto"` (`5`)**: **não é tomada aqui**; o archive diz que segue `5` e sem mudança própria ainda.
  - **`A região discursiva ainda não passou pelo aparelho nem pelo papel`** e **`O limiar do desvio … sem letra de aluno`** (`6`): sem mudança de estado nesta fatia, e o archive não as reagenda.
