## Why

§16 lista a LGPD com dados de menores como **o único risco ainda sem encaminhamento**, com fatia-limite 3 e sem dono técnico. O parecer agora existe: `docs/legal/politica-de-privacidade.md` decide base legal por faixa etária, quem é controlador na modalidade autoatendimento, oito classes de retenção e o contrato de operador. Falta o que é nosso — registrar essas decisões onde o código as encontra, e fazer o schema parar de contradizê-las.

O momento é agora, e a razão não é de plano: **`exam_roster` está vazio e não existe nenhuma tela que o preencha.** Enquanto for assim não há dado coletado para migrar, consentimento para recoletar nem retenção para aplicar retroativamente. A fatia 4 é quando o dado passa a persistir e sincronizar; depois dela, o que esta fatia faz de graça vira retrofit sobre dado de menor já coletado — que é exatamente o que a tabela de ponto de não-retorno de §16 descreve.

Há também uma contradição publicada a resolver. A política §3.4 afirma que a plataforma *"oferece, e recomenda como padrão, o modo sem identificação nominal"*. Ela não oferece: `exam_roster.display_name` é `not null` e nada no sistema distingue "Maria Silva" de "aluno 17". Publicar uma política que descreve um recurso inexistente é pior que não ter o recurso.

## What Changes

- **ADR-0012 registra as decisões da política** onde o código as consulta: base legal por faixa etária (criança < 12 por consentimento do responsável, art. 14 §1º; adolescente 12–17 por execução de contrato e legítimo interesse, sob a leitura mais protetiva quando o controlador exigir), o controlador na modalidade autoatendimento (§3.3), as classes de retenção do §10 como **padrões**, alteráveis para menos por contrato, e o contrato de operador do §4. O ADR aponta para a política por caminho; não a duplica.
- **A política entra versionada**, com os placeholders. O aviso preliminar dela já declara que os colchetes precisam ser preenchidos antes da publicação — versionar o documento incompleto é honesto e é o que o ADR pode citar.
- **O modo de identificação passa a ser declarado.** A organização declara em qual modo opera, e o modo restringe o que o roster aceita. O que o banco consegue afirmar, ele afirma; o que não consegue, fica registrado como declaração e não fingido.
- **Toda tabela com dado pessoal declara finalidade e classe de retenção**, com guarda derivada do catálogo — o mesmo padrão que já reprova tabela nascida sem RLS. A `0007_exam_tables.sql` nasceu sem, contra o que o ADR-0006 exige como critério de revisão de migration.
- **§16 é corrigido.** A linha "único item ainda sem encaminhamento" e a linha correspondente da tabela de ponto de não-retorno deixam de valer no instante em que o ADR-0012 é aceito. Fonte de verdade que envelheceu é pior que lacuna conhecida.

**O que esta mudança NÃO faz**, e onde cada coisa vai:

- Não implementa a coerção do papel na interface (§3.5) nem o bloqueio de roster nominal em organização `school` sem contrato de operador registrado (§4). As duas dependem de uma tela de cadastro de aluno que não existe — vão com o resto da fatia 3.
- Não implementa retenção executável: expirar, apagar, atender pedido de eliminação, e a classe H de cache no dispositivo. Vai com a fatia 4, que é quem cria o dado durável que ela opera.
- Não anonimiza a classe B. Depende do alias append-only de ADR-0003 e de fatos de avaliação, que ainda não existem.
- Não toca em provedor de pagamento, IA, transferência internacional nem cookies. A política os cobre; nenhum deles tem código hoje.
- Não muda `ExamPackage`, `LayoutMap`, golden nem hash de pacote. O roster já vive fora do artefato imutável desde a 2a.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `exam-package`: ganha o requisito do **modo de identificação** — a organização declara em qual modo opera, o modo codificado é o padrão, e o roster recusa o que esse modo proíbe. A separação entre pacote e roster não muda; o que muda é o que o roster aceita guardar e o que ele diz sobre si.
- `identity`: ganha o requisito de que **todo armazenamento de dado pessoal declara finalidade e classe de retenção**, com recusa de construção quando faltar. É a capacidade que já detém a garantia de isolamento imposto no armazenamento, e esta é da mesma família.

São duas — dentro da regra 3.

## Impact

**Documentos**

- `docs/adr/0012-*.md`: novo. As decisões da política, com o que cada uma obriga no código.
- `docs/legal/politica-de-privacidade.md`: passa a ser versionado.
- `docs/architecture/ARQUITETURA-FINAL-v3.md` §16: a linha do risco e a da tabela de ponto de não-retorno.
- `docs/cobertura-lgpd-roster.md`: como cada verificação foi vista falhar.

**Banco**

- Migration nova: modo de identificação na organização, e a restrição correspondente em `exam_roster`. Comentários de finalidade e classe de retenção nas tabelas com dado pessoal.
- A guarda de catálogo que hoje reprova tabela sem RLS ganha a conferência de finalidade e retenção.

**Código**

- `apps/api`: a validação do roster contra o modo declarado, onde a inserção acontece.

**O que não muda**

Nenhum artefato publicado, nenhum golden, nenhum hash. `packages/domain` só é tocado se o contrato do roster subir para lá, o que o `design.md` decide.

**Referências**

`docs/legal/politica-de-privacidade.md` §3, §4, §6.2, §10 · ADR-0002 (roster fora do pacote), ADR-0003 (alias append-only), ADR-0006 (finalidade e retenção; o gatilho) · §5, §11, §16 · I5 · `supabase/migrations/0007_exam_tables.sql`.

**Uma escolha de escopo que vale defender**

O requisito de finalidade e retenção alcança toda tabela com dado pessoal, e não só o roster: `app_user` e `subscription` também guardam dado pessoal, e uma regra que valesse só para a fatia que a criou não seria regra. Por isso ela mora em `identity`, ao lado do isolamento por organização, e não em `exam-package`.

**Uma tensão declarada com ADR-0006**

O ADR-0006 decidiu **não** fazer de "finalidade declarada" uma invariante, com o argumento de que não é verificável por teste. Esta mudança propõe torná-la verificável como **guarda de CI derivada do catálogo**, e não como invariante. A distinção não é retórica: as invariantes desta base valem porque cada uma reprova um desenho concreto, e o ADR-0012 registra que a guarda existe sem promover a regra ao conjunto I1–I5. Se o `design.md` concluir que a guarda não é praticável, a regra volta a ser critério de revisão e isso fica escrito.
