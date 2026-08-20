## Context

Ver `proposal.md — Why`. O que as fatias anteriores deixaram pronto define quase todo o desenho desta:

- O `LayoutMap` já é determinístico, versionado e comparado byte a byte entre três alvos. O pacote o embute; não o recalcula.
- `LayoutProfile` existe desde a 1.6, com um perfil padrão que reproduz o golden. Falta o artefato publicado declará-lo (ADR-0004).
- A fatia 0 estabeleceu o padrão de persistência: papel `app_backend` sem `BYPASSRLS`, RLS habilitada **e forçada**, e uma guarda que hoje deriva do catálogo — toda tabela de `public` precisa de RLS, então as três tabelas novas nascem cobertas ou o build cai.
- `EmbedFixturesTask` já transporta JSON versionado para os três alvos, e aprendeu na 1.5 que `const val` estoura o pool de constantes da JVM.

O que **não** existe: qualquer tabela de prova, qualquer endpoint além de identidade e billing, e qualquer noção de publicação.

## Goals / Non-Goals

**Goals:**

- Um pacote real, gravado, imutável e verificável por hash.
- Dado pessoal de aluno fora do artefato imutável, e apagável sem destruir a prova.
- A folha impressa derivada do pacote, com paridade e fidelidade nos mesmos números.
- O perfil tipográfico declarado no artefato publicado.

**Non-Goals:**

- Variantes múltiplas, randomização, blueprint.
- Distribuição: Storage, sync, gate de pré-voo.
- Qualquer coisa de captura — marcadores, ArUco, qualidade de impressão são a 2b.
- Interface de autoria. A prova fixa entra por arquivo versionado, como hoje.

## Decisions

### D-2a.1 — O tipo do pacote é KMP; a publicação é Ktor

O `ExamPackage` é tipo do domínio compartilhado, com a mesma serialização canônica que o `LayoutMap` já usa. A publicação — ler a definição, montar, hashear, gravar — é do servidor.

A divisão não é estética. **Os dois renderizadores precisam ler o pacote**, e D-1.1 põe contrato de domínio no código compartilhado justamente para que os dois lados não interpretem o mesmo artefato por caminhos independentes — o problema que a fatia 1 inteira existiu para eliminar. Já gravar no Postgres é do Ktor, e §13 não quer um segundo runtime no servidor.

*Alternativa descartada:* o pacote como tipo só do servidor, e os clients lendo JSON solto. Reintroduz duas interpretações do mesmo contrato, que é a divergência que a paridade existe para barrar.

### D-2a.2 — O roster está fora do pacote por construção, e não por exclusão do hash

`assignments[]` carrega `student_token` e `variant_id`. Nome, turma e matrícula vivem em `exam_roster`, tabela própria.

A diferença entre "fora do hash" e "fora do pacote" é a que importa. Um campo dentro do pacote e excluído do hash continua sendo dado pessoal dentro de um artefato copiado para dispositivos offline: I5 seria violada com o hash intacto. Estando fora, a exclusão do hash é consequência e não regra — não há como esquecer de aplicá-la.

ADR-0002 registra a decisão de não haver um segundo hash sobre o roster: ele **deve** poder mudar, e integridade do impresso já vem do QR, que amarra token e variante na própria folha.

### D-2a.3 — A imutabilidade é do banco, não da aplicação

`exam_package` recusa `UPDATE` e `DELETE`. A recusa vive no armazenamento.

O motivo é o mesmo do ADR-0002 e de D-0.2: garantia que depende de todo chamador se comportar não é garantia. A fatia 0 já aprendeu isso com RLS — o teste que impede os outros de serem decorativos é o que afirma que o papel de conexão não tem `BYPASSRLS`. Aqui vale igual: só é imutável o que o banco recusa alterar.

*Como isto pode falhar em silêncio:* a guarda existe e ninguém a exercita. Por isso ela ganha cenário próprio, e o teste tenta a alteração de verdade contra Postgres real, como `TenancyIsolationTest` faz.

### D-2a.4 — O hash é sobre a serialização canônica que o `LayoutMap` já usa

Nenhum formato novo. O `LayoutMap` já é serializado canonicamente para poder ser comparado byte a byte entre três alvos, e é esse mesmo caminho que produz os bytes que entram no hash.

Isso dá de graça a propriedade que o requisito pede — mesma prova, mesmo hash — e evita a armadilha clássica de hashear JSON com ordem de campo não determinística.

### D-2a.5 — A folha passa a vir do pacote, e é isso que dá dente à fatia

`render-fixture.ts` e o teste instrumentado extraem o `LayoutMap` de dentro do pacote.

Sem isso, o pacote seria um artefato que ninguém consome, e **artefato sem consumidor não tem como estar errado**: o layout dentro dele poderia divergir do que se imprime sem nada acusar. Com isso, paridade e fidelidade — que já existem e já sabem falhar — passam a julgar o pacote sem uma linha de verificação nova.

### D-2a.6 — A prova fixa continua entrando por arquivo versionado

A fixture atual vira a entrada da publicação. Não há interface de autoria, e `exam` guarda a identificação da prova, não o conteúdo dela.

Autoria é a fatia 6 (op-log) e a 7 (blueprint). Antecipar tabela de item aqui seria construir para um pipeline que ainda não existe — o mesmo erro que D-1.5.2 evitou ao não integrar a conversão de fórmula com uma publicação inexistente.

### D-2a.7 — O SHA-256 é escrito aqui, e não tomado de uma biblioteca

O `content_hash` precisa ser calculável nos **três alvos**: o servidor o produz, e o dispositivo que
recebe o pacote precisa poder verificá-lo. `java.security.MessageDigest` não existe em Kotlin/JS, e
uma dependência multiplataforma de criptografia exigiria justificativa por uma função de cem linhas
cujo resultado é fixado por norma.

A base já tem precedente pelo mesmo motivo: `QrEncoder` e `FontProgram` são implementações próprias
porque o valor precisa ser idêntico nos três alvos, e a única forma de garantir isso é haver um
caminho só.

A correção não depende de confiança na implementação. `Sha256Test` confere contra os vetores
publicados do FIPS 180-4 — **oracle independente de qualquer linha deste repositório** — mais os
valores de borda de preenchimento (55, 56 e 64 bytes) obtidos do `crypto` do Node, que é uma segunda
implementação independente.

*Isso já se pagou na primeira execução:* dos três valores de borda que escrevi de memória, um estava
errado. O teste ficou vermelho apontando o **valor esperado**, e não o código — que estava certo
desde o início. Foi por isso que os valores passaram a vir de um oracle em vez da minha memória.

*Alternativa descartada:* calcular o hash só no servidor, com `MessageDigest`. Mais simples hoje e
inviável na fatia 4, quando o dispositivo precisar verificar o pacote que puxou — e aí a
implementação teria de nascer assim mesmo, com um pacote publicado já dependendo do formato.

## Risks / Trade-offs

**Primeira fatia a tocar `apps/api` desde a 0** → migration, RLS, jOOQ e repositório de uma vez. Mitigado por seguir o padrão já estabelecido: as tabelas novas caem sob a guarda de RLS derivada do catálogo, que reprova sozinha se alguma nascer sem proteção.

**O golden muda** → o cabeçalho do `LayoutMap` ganha o perfil. Regravação deliberada, com uma causa só, como nas duas fatias anteriores. Nada de fórmula ou espaçamento entra junto.

**Hash instável entre plataformas** → o risco clássico é ordem de campo. Neutralizado por reusar a serialização canônica que já é comparada byte a byte em três alvos; se ela variasse, o golden já teria quebrado.

**A imutabilidade pode ser decorativa** → cenário próprio, exercitado contra Postgres real, tentando a alteração de verdade.

**Dado pessoal escapar para o pacote depois** → o cenário "o pacote não carrega nome de aluno" é a barreira executável de I5, e ela reprova qualquer campo novo que traga dado direto.

## Migration Plan

Aditivo. Uma migration nova, três tabelas, nenhuma alteração nas existentes. Reverter é `git revert` mais desfazer a migration; não há dado de produção.

Ordem de implementação, de contrato para consumidor: tipo e hash no KMP → migration e RLS → publicação e validação no Ktor → renderizadores lendo do pacote → golden e verificação.

## Open Questions

Nenhuma. As duas que existiam — o que "publicado" significa, e se os renderizadores passam a consumir o pacote — foram fechadas antes de a proposta ser escrita, e estão em D-2a.3 e D-2a.5.
