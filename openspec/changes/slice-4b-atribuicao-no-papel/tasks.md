## 1. A forma do pacote, antes de qualquer consumidor

- [ ] 1.1 **Contrato antes de implementação** (regra 1): o pacote passa a endereçar, por atribuição, o **payload do QR e a matriz de módulos** dela, sobre a geometria compartilhada da variante. Só tipos e serialização, sem consumidor. Resultado: código novo, nenhum comportamento muda, e a decisão 1 do `design.md` fica expressa no contrato — geometria uma vez, QR por aluno.
- [ ] 1.2 Cenários de JVM sobre a composição: a folha de uma atribuição é a geometria da variante com o QR dela; duas atribuições da mesma variante produzem folhas que coincidem em **tudo menos** no QR; atribuição sem QR endereçado é erro de composição, não folha silenciosamente igual à da variante.
- [ ] 1.3 **Ver falhar, com os conjuntos declarados antes de injetar:** fazer a composição usar sempre o QR da variante, ignorando o da atribuição. Esperado vermelho: só o cenário de "duas atribuições diferem no QR". Esperado verde: os que afirmam igualdade de geometria — se eles caírem também, a mutação não está medindo o que a tarefa diz. Reverter, e conferir a reversão **rodando**, com o `timestamp` do relatório e não com o `BUILD` do Gradle.

## 2. A coerência recusa antes de gravar

- [ ] 2.1 A validação do pacote passa a recusar, com erro identificável: atribuição sem layout endereçável por ela, layout endereçado por atribuição que o pacote não declara, e token repetido dentro da mesma prova. Resultado: pacote incoerente não chega ao disco, e as três recusas nomeiam o que estava errado.
- [ ] 2.2 Um cenário por recusa, e cada asserção confere o **motivo** (P9), não só que houve recusa — "recusou" é indistinguível entre a camada certa e a vizinha.
- [ ] 2.3 **Ver falhar:** remover uma das três conferências por vez e confirmar que cada remoção derruba **um** conjunto próprio, declarado antes. Se duas remoções derrubarem o mesmo conjunto, as conferências não são independentes e o registro diz isso em vez de esconder.

## 3. O `LayoutEngine` escreve o token no QR

- [ ] 3.1 Para uma variante com atribuições, o engine emite a geometria uma vez e um payload de QR por atribuição, resolvido **ali** e registrado no `LayoutMap` junto da matriz — **um escritor só**, como a 2b fechou. Sem atribuição, o campo de aluno sai **vazio**.
- [ ] 3.2 Cenários de JVM: o payload traz o token da atribuição; sem atribuição o campo fica vazio e **nenhum valor de reserva** aparece; ida e volta pelo mesmo codec devolve o token igual.
- [ ] 3.3 **Ver falhar:** preencher o campo vazio com um marcador qualquer quando não há atribuição. Esperado: só o cenário do "não inventa aluno" fica vermelho; o da ida e volta continua verde. É a mesma família do nome de reserva que a 4a-zero viu falhar — valor inventado é indistinguível de valor verdadeiro para quem lê.
- [ ] 3.4 **Medir e registrar** quantos módulos o QR passa a ter com token e variante preenchidos, e quantos bytes isso dá por atribuição. Hoje são 29×29 e 1.058 bytes com dois campos vazios. É a pergunta aberta do `design.md`: ela se fecha com número medido, não com estimativa.

## 4. A publicação com roster

- [ ] 4.1 `ExamPublication` passa a receber roster não vazio: grava `exam_roster` e leva **só o token** para `assignments[]`, uma atribuição por aluno. Resultado: as primeiras linhas reais de roster existem, e o pacote não ganha nome nenhum.
- [ ] 4.2 Cenários sobre banco real, no instrumento que a 4a já usa: o roster gravado; uma atribuição por aluno; e o **canário** — os bytes do pacote publicado não contêm o nome, a turma nem a matrícula de nenhum aluno do roster. O canário é busca nos bytes, não inspeção de campo: campo novo com nome dentro passaria por inspeção de campo.
- [ ] 4.3 **Ver falhar:** deixar o nome vazar para o pacote (por exemplo, no rótulo de uma atribuição) e confirmar que **o canário** fica vermelho, e não só uma asserção de igualdade de estrutura. Reverter e rodar.
- [ ] 4.4 Fixar por cenário a recusa por modo de identificação, que já está implementada e nunca foi exercitada com dado real: organização em `coded` recusa `enrollment_id` e nome civil, com a frase dizendo qual modo está em vigor e qual campo foi recusado. Resultado: ADR-0012 deixa de ser afirmação e passa a ser cenário.

## 5. O consumidor: a impressão escolhe por atribuição

- [ ] 5.1 O script do web passa a escolher o layout **por atribuição** e a produzir um documento por aluno; pacote sem atribuição continua produzindo um documento, como hoje. Resultado: a negativa "nenhum documento vem de layout que o pacote não endereça" é conferível.
- [ ] 5.2 Conferir que os N documentos de uma prova coincidem entre si em geometria e diferem no QR — a mesma afirmação da 1.2, agora medida sobre o **documento** e não sobre o mapa.

## 6. A fixture republicada, e a paridade na mesma sessão

- [ ] 6.1 Republicar a fixture de referência com um roster **pequeno e codificado** (três alunos, códigos e não nomes civis), gerando `assignments` não vazio. Resultado: o `content_hash` muda, e `26612ad52b0cb967309f49354e9858c501ad7a1b0c7b46db874c05d348e6909a` deixa de valer.
- [ ] 6.2 **P23, e ela é bloqueante:** paridade e fidelidade fecham **na mesma sessão** da regravação, com os artefatos dos dois lados gerados **naquela** sessão. Um caminho de desenho alterado já basta para não confiar na última medição, e aqui o caminho mudou.
- [ ] 6.3 Atualizar todo lugar que fixa o hash antigo — testes de JVM, instrumentados e documento de cobertura da 4a — e registrar que a mudança de hash é consequência da atribuição, não de conteúdo de prova. Resultado: nenhum teste verde por comparar contra artefato de execução anterior (P3).

## 7. Verificação final

- [ ] 7.1 Rodar o **comando cheio do CI**: `./gradlew build` mais `./gradlew :apps:android:connectedDebugAndroidTest` **sem filtro**. Conferir os números no relatório **e o `timestamp` de cada um** — `UP-TO-DATE` serve relatório velho com contagem plausível, e foi assim que a 8.1 da fatia anterior quase fechou com número de seis dias antes.
- [ ] 7.2 Escrever `docs/cobertura-fatia-4b-atribuicao-no-papel.md` com **como** cada verificação crítica foi vista falhar — a mutação, os cenários que caíram e a **mensagem** da asserção —, mais o que ficou sem teste automático e por quê.
- [ ] 7.3 Rodar `openspec validate slice-4b-atribuicao-no-papel --strict`.
- [ ] 7.4 Conferir **arquivo a arquivo**, contra o baseline correto — o commit em que esta fatia começou, e não `origin/main` se houver outra fatia no mesmo branch —, as negativas da proposta: nada em `vision/`, nada em `omr/`, nada no aparelho (`apps/android`), e nenhuma spec fora de `exam-package` e `layout-engine`. Negativa larga não vale: nomear a exceção, se houver, e mostrá-la no `git diff`.
