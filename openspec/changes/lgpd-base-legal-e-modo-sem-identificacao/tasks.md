## 1. O registro, antes do schema

- [x] 1.1 Versionar `docs/legal/politica-de-privacidade.md` como está, com os placeholders. Resultado: o documento que o ADR vai citar existe no repositório, e o aviso preliminar dele declara que não está publicável.
- [x] 1.2 Escrever `docs/adr/0012-base-legal-e-retencao-de-dado-pessoal.md`: base legal por faixa etária (§6.2), controlador na modalidade autoatendimento (§3.3), classes de retenção do §10 como padrões alteráveis para menos por contrato, e o contrato de operador (§4). Aponta para a política por caminho e **não** a duplica. Resultado: ADR aceito.
- [x] 1.3 No mesmo ADR, registrar as quatro afirmações da política que o código já honra, cada uma com onde está provada — leitura óptica local, artefato sem dado pessoal direto, isolamento em RLS forçada, revisão humana e rastreabilidade. Resultado: quebrar qualquer uma delas passa a aparecer como teste vermelho, e não como descoberta de auditoria.
- [x] 1.4 No mesmo ADR, registrar a distinção entre **guarda de construção** e **invariante**, e por que a exigência de finalidade e retenção é a primeira e não a segunda. Resultado: o ADR-0006 é honrado em vez de contrariado em silêncio.
- [ ] 1.5 Commit destes documentos **antes** de qualquer migration ou código. Resultado: o histórico mostra a decisão precedendo a implementação, na ordem que a regra 1 pede.

## 2. O modo de identificação, no armazenamento

- [ ] 2.1 Migration: `organization` ganha o modo de identificação, com `check` nos dois valores e `default` no codificado. Resultado: organização existente e nova nascem codificadas sem `update` de dados.
- [ ] 2.2 Migration: `exam_roster` ganha o `check` que recusa `enrollment_id` não nulo quando a organização opera em modo codificado. Resultado: a regra vale no armazenamento, e não só na aplicação.
- [ ] 2.3 Testar os quatro cenários da spec de `exam-package` contra o banco real: organização nova nasce codificada, matrícula recusada em codificado, aceita em nominal, e o modo recuperável junto do roster. Resultado: os quatro passam.
- [ ] 2.4 Testar que trocar o modo de uma organização com prova publicada não muda o hash do pacote. Resultado: o hash antes e depois é o mesmo.
- [ ] 2.5 Ver falhar: remover o `check` do 2.2 e confirmar que o cenário da matrícula recusada fica vermelho; reverter. Registrar em `docs/cobertura-lgpd-roster.md`.
- [ ] 2.6 Ver falhar: trocar o `default` para nominal e confirmar que o cenário da organização recém-criada fica vermelho; reverter. Registrar. **É o teste que mais importa** — nascer no modo errado é falha silenciosa que só aparece quando já há nome de menor no banco.

## 3. Finalidade e retenção declaradas

- [ ] 3.1 Decidir a forma do marcador (a questão aberta do `design.md`) e escrever a convenção no topo da migration que a introduz. Resultado: uma forma só, documentada onde quem escreve a próxima migration a encontra.
- [ ] 3.2 Migration: `comment on table` em **todas** as tabelas de `public`, cada uma com finalidade e classe — inclusive as que não têm dado pessoal, que declaram a classe `nenhum`. Resultado: nenhuma tabela sem declaração.
- [ ] 3.3 Implementar a guarda derivada do catálogo, no molde de `ConnectionRoleTest`: varre `pg_class` junto de `obj_description`, junta as violações, afirma lista vazia, e mantém o **piso de contagem** para que consulta vazia não passe. Resultado: os quatro cenários da spec de `identity` passam.
- [ ] 3.4 Ver falhar: criar tabela de teste sem declaração e confirmar que a guarda a nomeia; declarar classe inexistente e confirmar que a guarda nomeia a classe e as admitidas; remover a declaração de uma tabela que a tem e confirmar que fica vermelho. Reverter os três e registrar.
- [ ] 3.5 Ver falhar o **piso**: apontar a consulta para um schema vazio e confirmar que a guarda reprova em vez de passar com zero tabelas. Resultado: a armadilha que a guarda de RLS já documentou não se repete aqui.

## 4. A validação onde a escrita acontece

- [ ] 4.1 Implementar em `apps/api` a validação do roster contra o modo declarado, com mensagem que nomeia o modo. Resultado: a recusa chega ao chamador com motivo legível, e não como erro de constraint cru.
- [ ] 4.2 Testar que a mensagem nomeia o modo e o campo recusado. Resultado: quem for escrever a tela sabe o que mostrar.

## 5. Corrigir a arquitetura

- [ ] 5.1 `ARQUITETURA-FINAL-v3.md` §16: a linha do risco LGPD deixa de dizer "único item ainda sem encaminhamento" e passa a apontar o ADR-0012 e a política, com o que ficou para a fatia da tela e para a 4.
- [ ] 5.2 §16, tabela de ponto de não-retorno: a linha da LGPD passa a registrar o que foi feito e o que resta, e o dono deixa de ser "sem dono técnico" para a parte que agora tem código.
- [ ] 5.3 Conferir que nenhuma outra parte de §16 ou §5 ficou contradizendo o que esta mudança fez. Resultado: a fonte de verdade descreve o sistema que existe.

## 6. Verificação final

- [ ] 6.1 Rodar `./gradlew build` com Docker de pé, para que a suíte da API rode. Resultado: verde.
- [ ] 6.2 Completar `docs/cobertura-lgpd-roster.md` com como cada verificação foi vista falhar, e não só que passa.
- [ ] 6.3 Rodar `openspec validate lgpd-base-legal-e-modo-sem-identificacao --strict`. Resultado: válido.
- [ ] 6.4 Conferir que nenhum golden e nenhum hash de pacote mudou, e que `packages/domain` não foi tocado.
