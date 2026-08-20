## 1. Base de comparação

- [x] 1.1 Registrar os números atuais antes de tocar em qualquer coisa: hash e tamanho do golden, paridade, fidelidade nos dois PDFs e contagem da suíte por alvo. Resultado: valores anotados aqui — a fatia altera o golden de propósito, então a base precisa existir antes.
  Medido em 2026-08-20, com `./gradlew build :packages:domain:testAndroidHostTest --rerun-tasks`.

  | Grandeza | Valor |
  |---|---|
  | `fixtures/prova-referencia.layout.json` | sha256 `7cbf40c9c785b8f653b7b15be241c1144be54913259c93e0e388c4bba1939a9e`, 69 864 bytes |
  | Paridade web × Android | 185 de 185 elementos, 4 páginas, maior divergência **0,042 mm** em `qq37-f` |
  | Fidelidade do PDF web | 116 verificações, maior desvio **0,039 mm** |

  | Alvo | Testes | Falhas |
  |---|---|---|
  | `packages/domain` JVM | 165 | 0 |
  | `packages/domain` Node/JS | 161 | 0 |
  | `packages/domain` Android (host) | 161 | 0 |
  | `apps/api` | 69 | 0 |

  Nenhuma falha pré-existente. Docker disponível, então os testes de `apps/api` rodam contra
  Postgres real desde o começo desta fatia — diferente da 1.5, onde a guarda de RLS teve de
  esperar o CI.

## 2. Contrato: o pacote e o hash

- [ ] 2.1 Definir `ExamPackage` no domínio KMP com `meta`, `items`, `variants`, `assignments`, `layout`, `answer_key` e `scoring` (D-2a.1, §5). Resultado: tipo serializável, sem dependência de banco ou rede.
  - `assignments[]` traz `student_token` e `variant_id`, e **nada mais** — é onde I5 é decidida.
- [ ] 2.2 Calcular o `content_hash` sobre a serialização canônica que o `LayoutMap` já usa (D-2a.4). Resultado: cobre "Republicar a mesma prova dá o mesmo hash" e "Mudança no conteúdo muda o hash".
  - Afirmar o hash nos **três alvos**: se ele divergisse entre JVM, Node e Android, o pacote deixaria de ser verificável no dispositivo que o consome.
- [ ] 2.3 Declarar o perfil tipográfico no cabeçalho do layout (ADR-0004). Resultado: cobre "Perfil declarado no pacote" e "Perfis diferentes são distinguíveis".
- [ ] 2.4 Validar coerência interna antes de qualquer gravação. Resultado: cobre os três cenários de "Pacote incoerente é recusado", cada um com o caso positivo ao lado.
- [ ] 2.5 Regravar o golden e registrar aqui o antes e o depois. Resultado: mudança auditável, com **uma causa só** — o campo de perfil, e nada mais.
  - Nenhuma outra mudança de geometria entra nesta fatia. Se a contagem de páginas ou a atribuição bloco→página se mexer, algo está errado.

## 3. Persistência

- [ ] 3.1 Migration com `exam`, `exam_package` e `exam_roster`, chaveadas por `organization_id`, com RLS habilitada e forçada. Resultado: as três nascem cobertas pela guarda derivada do catálogo.
  - Confirmar que a guarda **reprova** se uma delas nascer sem RLS: acrescentar sem a política, ver vermelho, corrigir.
- [ ] 3.2 Impor a imutabilidade de `exam_package` no próprio banco (D-2a.3). Resultado: cobre "Pacote publicado não pode ser alterado".
  - O teste tenta `UPDATE` e `DELETE` de verdade, contra Postgres real e como `app_backend`, no molde de `TenancyIsolationTest`. Uma guarda de imutabilidade que ninguém exercita é decorativa.
- [ ] 3.3 Testar isolamento por organização das três tabelas novas, sem filtro na aplicação. Resultado: consulta de organização alheia devolve zero linhas.
- [ ] 3.4 Testar que apagar o roster deixa o pacote íntegro e com o mesmo hash. Resultado: cobre "Eliminação de dado pessoal não destrói a prova" — a operacionalização de I5.

## 4. Publicação

- [ ] 4.1 Publicar a prova fixa: montar, validar, hashear, gravar. Resultado: um pacote real no banco, a partir da fixture versionada.
- [ ] 4.2 Testar que o pacote gravado não traz nome, turma nem matrícula. Resultado: cobre "O pacote não carrega nome de aluno".
  - A varredura é sobre o JSON gravado, e não sobre o tipo: um campo acrescentado por engano aparece no JSON antes de aparecer em qualquer revisão de código.
- [ ] 4.3 Testar que corrigir o roster não muda o hash do pacote. Resultado: cobre "Mudar o roster não invalida o pacote".
- [ ] 4.4 Recusar republicação sobre pacote existente, com erro identificável. Resultado: corrigir prova publicada é publicar pacote novo, como D-2a.3 exige.

## 5. Renderização a partir do pacote

- [ ] 5.1 `render-fixture.ts` passa a extrair o `LayoutMap` do pacote (D-2a.5). Resultado: cobre "PDF vem do pacote" no web.
- [ ] 5.2 O teste instrumentado do Android passa a fazer o mesmo. Resultado: cobre o mesmo cenário no Android.
- [ ] 5.3 Confirmar que nenhum renderizador ganhou lógica nova. Resultado: se algum precisou mudar de comportamento — e não só de origem do mapa —, o desenho de D-2a.1 está errado e a tarefa reprova.

## 6. Verificação

- [ ] 6.1 Rodar a suíte completa nos três alvos, mais `./gradlew build`. Resultado: golden novo estável byte a byte em JVM, Node e Android.
- [ ] 6.2 Medir fidelidade dos dois PDFs derivados do pacote. Resultado: dentro de 0,05 mm, comparado com a base da tarefa 1.1.
- [ ] 6.3 Medir paridade web × Android com os documentos derivados do pacote. Resultado: dentro de 0,3 mm; cobre "Divergência entre plataformas continua barrada".
- [ ] 6.4 Provar que a verificação continua capaz de falhar, com o deslocamento deliberado sobre o layout **do pacote**. Resultado: as duas ferramentas saem com código 1.
  - A janela de medição já foi conferida para bloco e para linha. O que muda aqui é a origem do mapa, então a conferência é de que o deslocamento ainda **chega** ao documento.
- [ ] 6.5 Atualizar `docs/cobertura-*.md` com os cenários novos e como cada verificação foi vista falhar. Resultado: nenhum cenário da spec sem verificação.
- [ ] 6.6 Imprimir a folha derivada do pacote e conferir a olho. Resultado: registrado.
  - **Duas fatias seguidas, dois defeitos de espaçamento que só o papel achou** — e o segundo explicou uma divergência de métrica que eu tratava como problema de ferramenta. Enquanto o Layout Engine estiver mudando, esta tarefa não é formalidade.

## 7. Registro

- [ ] 7.1 Registrar em `docs/adr/` o que a fatia decidiu e ainda não estava em ADR, se houver. Resultado: nenhuma decisão nova só no código.
- [ ] 7.2 Atualizar §16 e o bloco Aberto de §17: os itens cuja fatia-limite era a 2a saem da lista ou mudam de estado. Resultado: a lista de adiados encolhe quando o prazo chega, em vez de crescer.
