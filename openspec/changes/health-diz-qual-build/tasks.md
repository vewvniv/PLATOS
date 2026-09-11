## 1. A leitura do identificador, antes de qualquer consumidor

- [ ] 1.1 **Contrato antes de implementação** (regra 1): `AppConfig` passa a ler o identificador do build pelo caminho **opcional** — nunca `required` —, com a ausência representada por um valor nomeado decidido em **um lugar só**. Resultado: código novo sem consumidor, e a API continua subindo sem o identificador, como todo `installDist` local faz.
- [ ] 1.2 Cenários de JVM sobre a leitura: presente devolve o que foi passado; ausente devolve o valor nomeado de desconhecido; **vazio ou só espaços também é ausência** — string vazia assada por um `--build-arg` mal formado é o caso que passaria por "presente" e declararia nada.
- [ ] 1.3 **Ver falhar:** trocar a leitura opcional por `required` e confirmar que o cenário da ausência fica vermelho **por falha de arranque**, e não por igualdade — é a distinção que a decisão 3 protege: `required` transformaria observabilidade em modo novo de falha de subida. Reverter e conferir a reversão **rodando**, pelo `timestamp` do relatório.

## 2. `/health` declara, e o corpo não muda

- [ ] 2.1 `healthRoutes` passa a emitir `X-Platos-Build` com o identificador, mantendo o corpo exatamente `ok` e o código 200. Resultado: a resposta declara o build sem que nenhum consumidor do corpo perceba diferença.
- [ ] 2.2 Cenários novos em `HealthTest`, e **a asserção existente do corpo permanece intocada** — ela é a prova de que a mudança é aditiva: o cabeçalho traz o identificador quando ele existe; traz o valor de desconhecido quando não existe; e o cabeçalho **está presente nos dois casos**, porque ausência de cabeçalho é indistinguível de intermediário que o removeu.
- [ ] 2.3 **Ver falhar, com os conjuntos declarados ANTES de injetar:** (A) omitir o cabeçalho quando o identificador é desconhecido → vermelho esperado: só o cenário do desconhecido; verde: o do identificador presente **e o do corpo**. (B) emitir o identificador **no corpo** junto com `ok` → vermelho esperado: a asserção **pré-existente** do corpo, que é exatamente a que prova que a mudança é aditiva. Se (B) não derrubar a asserção antiga, ela não está protegendo o que eu afirmo que protege.

## 3. O identificador entra na imagem

- [ ] 3.1 `Dockerfile` recebe `ARG PLATOS_BUILD` e o promove a `ENV`, com comentário dizendo **por que não é variável de execução**: identificador editável no painel é a própria deriva que esta mudança existe para detectar. Resultado: a imagem carrega o identificador de forma tão imutável quanto o conteúdo que ele descreve.
- [ ] 3.2 `publicar-api.yml` passa `--build-arg PLATOS_BUILD=sha-$curto` usando o **mesmo** `$curto` que já forma a tag, na mesma linha de comando — para que tag e cabeçalho não possam divergir por edição de um sem o outro.
- [ ] 3.3 **Conferir localmente, e é o único ponto em que isto se mede sem publicar:** construir a imagem com `--build-arg` e sem ele, subir as duas e ler o cabeçalho de cada uma. Resultado: o caminho de publicação e o caminho local são **os dois** exercitados, e a ausência deixa de ser cenário só de unidade. **Exige Docker local** — se não estiver disponível, a tarefa fica **desmarcada** com o que falta escrito, e a 4.2 passa a ser a primeira medição real.

## 4. A conferência em produção, e o documento que a guarda

- [ ] 4.1 Atualizar `docs/deploy-api.md`: a seção "Publicar imagem nova NÃO redeploya o Render" ganha o comando de conferência (`curl -sI` no cabeçalho) **ao lado** do par de rotas, e a tabela "Estado publicado" ganha a coluna ou a nota de qual identificador cada publicação declara. Resultado: a pergunta "o que está servindo" deixa de depender do painel — e a frase que hoje diz que o elo é inobservável de fora é **corrigida com a medição ao lado**, não apagada (P7).
- [ ] 4.2 **Depois do merge e da publicação:** conferir em produção que o cabeçalho traz o identificador do commit mesclado, comparado com a tag publicada no GHCR. Resultado: o terceiro elo de P26 medido pela primeira vez. **Se o cabeçalho não chegar** — removido por Cloudflare ou pelo Render —, isso é achado a registrar com o sintoma, e não motivo para reabrir a decisão da forma sem evidência.
- [ ] 4.3 Registrar em `docs/cobertura-health-diz-qual-build.md` **como** cada verificação crítica foi vista falhar — a mutação, os cenários que caíram e a **mensagem** da asserção —, mais o que ficou sem teste automático e por quê. Nomear explicitamente o que esta mudança **não** cobre: que o cabeçalho atravesse todo intermediário futuro, e que o identificador seja o do **digest** e não só do commit.

## 5. Verificação final

- [ ] 5.1 Rodar o **comando cheio do CI** — `./gradlew build` com `--rerun-tasks`, porque `UP-TO-DATE` serve relatório velho com contagem plausível — e conferir os números **e o `timestamp`** de cada relatório. A metade instrumentada não é exigida aqui: nada em `apps/android` muda, e a 5.3 confere isso arquivo a arquivo.
- [ ] 5.2 Rodar `openspec validate health-diz-qual-build --strict`.
- [ ] 5.3 Conferir **arquivo a arquivo**, contra o commit em que esta mudança começou, as negativas da proposta: nada em `apps/android`, `apps/web`, `packages/domain`, `vision/` e `omr/`, e nenhuma spec fora de `service-health`. Negativa larga não vale — nomear a exceção, se houver, e mostrá-la no `git diff`.
