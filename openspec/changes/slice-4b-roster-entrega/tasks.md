## 1. A consulta, antes da rota

- [x] 1.1 **Contrato antes do consumidor** (regra 1): `ExamQueries` ganha a leitura do roster de uma prova, devolvendo **token e nome de apresentação** e mais nada, com `EXAM.ORGANIZATION_ID` no `where` como `listPublished` e `findPackage` já fazem. Mais o DTO de resposta, ao lado de `ExamSummaryDto`. Resultado: código novo sem rota que o exponha, e nenhum comportamento muda.
- [ ] 1.2 Cenários contra **Postgres real** (Testcontainers, o instrumento que a 4a e a 4b já usam): o roster de uma prova com alunos vem com os dois campos; **turma e matrícula não aparecem em campo nenhum da resposta**; prova publicada sem roster devolve **lista vazia** e não nulo; e prova de outra organização devolve **nada**, mesmo com a linha existindo no banco.
- [ ] 1.3 **Ver falhar, com os conjuntos declarados ANTES de injetar:** (A) acrescentar `class_group` e `enrollment_id` ao que a consulta devolve → vermelho esperado: só o cenário que afirma a ausência deles; verde: os outros três. (B) tirar `EXAM.ORGANIZATION_ID` do `where` → vermelho esperado: só o cenário da organização alheia; verde: os três restantes. Se (B) não derrubar nada, a RLS está carregando sozinha o que a consulta afirma carregar — e isso é achado a registrar, não a esconder.

## 2. A rota

- [ ] 2.1 `GET /organizations/{organizationId}/exams/{shortId}/roster`, autenticada, sob `tenancy.asUser`, respondendo JSON pelo `ContentNegotiation` já instalado. Prova inexistente e prova de outra organização SHALL produzir a **mesma** resposta. Resultado: o roster passa a ser obtenível por quem pertence à organização.
- [ ] 2.2 Cenários de rota, no arquivo que já cobre a rota do pacote: 200 com os dois campos para quem pertence; **401** sem credencial; **a mesma resposta** para prova inexistente e para prova de organização alheia; e 200 com lista vazia para prova publicada sem roster. Cada asserção confere o **motivo** — código e corpo —, não só que houve recusa.
- [ ] 2.3 **Ver falhar:** fazer a rota responder 404 para prova sem roster. Esperado: só o cenário da lista vazia fica vermelho, e o da organização alheia continua verde — se os dois caírem juntos, "sem roster" e "não é seu" estão colapsados, que é exatamente a distinção que a folha avulsa depende. Reverter e conferir a reversão **rodando**, pelo `timestamp` do relatório.
- [ ] 2.4 Conferir que **o pacote e a listagem não mudaram**: a suíte de rota existente roda sem edição, e o `content_hash` que `ExamPublicationTest` fixa continua o mesmo. Resultado: a negativa "nenhum contrato existente alterado" é medida, e não afirmada.

## 3. Verificação final

- [ ] 3.1 Rodar o **comando cheio do CI** — `./gradlew build` com `--rerun-tasks`, porque `UP-TO-DATE` serve relatório velho com contagem plausível — e conferir os números **e o `timestamp`** de cada relatório. A metade instrumentada não é exigida: nada em `apps/android` muda, e a 3.3 confere isso arquivo a arquivo.
- [ ] 3.2 Rodar `openspec validate slice-4b-roster-entrega --strict`.
- [ ] 3.3 Conferir **arquivo a arquivo**, contra o commit em que esta mudança começou, as negativas da proposta: nada em `apps/android`, `apps/web`, `packages/domain`, `vision/` e `omr/`, e nenhuma spec fora de `exam-package`. Negativa larga não vale — nomear a exceção, se houver, e mostrá-la no `git diff`.
- [ ] 3.4 Escrever `docs/cobertura-slice-4b-roster-entrega.md` com **como** cada verificação crítica foi vista falhar — a mutação, os cenários que caíram e a **mensagem** da asserção —, mais o que ficou sem teste automático e por quê. Nomear explicitamente: que esta fatia **não** cria cópia de dado pessoal fora do servidor — ela abre o caminho para a **β** criar —, e que as duas linhas de ponto de não-retorno do §16 têm fatia-limite lá, não aqui.
