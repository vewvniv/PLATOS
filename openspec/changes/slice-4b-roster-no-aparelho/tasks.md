## 1. O guardado e o apagamento, antes de existir o que guardar

A ordem é do `design.md` (Migration Plan): o caminho de apagamento entra **antes** do primeiro roster
ser gravado, para não existir janela em que o aparelho guarde nome de aluno sem como apagá-lo.

- [x] 1.1 **Contrato antes do consumidor** (regra 1): `RostersGuardados` — guardar, ler e apagar por
  organização — e `RostersEmArquivo`, recebendo um `File` como `VisoesEmArquivo` faz, gravando
  `rosters/<organization_id>/<exam_short_id>.json` por temporário + `ATOMIC_MOVE` no mesmo diretório.
  Substituição por inteiro, nunca mescla. Resultado: código novo que ninguém chama ainda, e nenhum
  comportamento muda.
- [x] 1.2 `DeviceSession.sair` passa a apagar os rosters da organização, junto da sessão, da
  organização escolhida, da visão e dos pacotes. Resultado: o caminho de apagamento existe antes de
  haver o que apagar.
- [x] 1.2b **O outro caminho de apagamento**, que a auditoria achou faltando: a revogação de vínculo
  — o servidor respondendo que a organização não é mais do usuário — passa a apagar os rosters junto
  da visão e dos pacotes. Resultado: nenhum dos dois caminhos de perda de acesso deixa nome de aluno
  para trás, e o apagamento não depende de o usuário decidir sair.
- [x] 1.3 Cenários **de JVM, sem emulador** — o `File` no construtor é o que torna isso possível, e é
  a razão registrada na KDoc de `VisoesEmArquivo`: guardar e reler devolve as mesmas linhas; gravação
  interrompida não deixa arquivo parcial nem meio roster legível; roster de outra organização não é
  alcançável com a organização ativa trocada; substituir um roster de três alunos por um de dois
  **não deixa o terceiro para trás**; sair apaga o roster junto do resto da lista; e **a revogação de
  vínculo apaga o roster sem que o usuário saia** — os dois caminhos, não só o que a tela oferece.
- [x] 1.3b **A negativa de minimização, medida e não afirmada.** A asserção lê o **JSON gravado
  inteiro** e varre-o em dois níveis: **cada linha de aluno** reprova qualquer campo além de token e
  nome, e o **envelope** reprova qualquer campo além do instante do pull. Varredura do JSON, e não
  conferência de dois campos nomeados — é a forma que deixa passar o terceiro campo que ninguém
  previu, e foi a lição da mutação (A) da fatia α. Guarda de vacuidade (P13): a asserção falha se o
  arquivo estiver ausente, vazio ou sem linha alguma, para que "nenhum campo proibido" não seja
  verdade por não haver nada ali.
- [x] 1.4 **Ver falhar, com os conjuntos declarados ANTES de injetar:** (A) trocar o `ATOMIC_MOVE`
  por escrita direta no destino → vermelho esperado: só o cenário da gravação interrompida.
  (B) trocar a substituição por mescla → vermelho esperado: só o cenário do aluno retirado.
  (C) tirar o roster da lista que `sair` apaga → vermelho esperado: só o cenário de sair.
  (D) tirar o roster da lista que a **revogação** apaga → vermelho esperado: só o cenário da
  revogação. **(C) e (D) precisam cair em conjuntos disjuntos** — se uma delas derrubar os dois
  cenários, os caminhos estão compartilhando código que nenhum dos dois testa isoladamente, e aí a
  mutação não diz qual deles segurou (§3). (E) acrescentar `class_group` ao que é gravado → vermelho
  esperado: só a negativa da 1.3b. Se (C), (D) ou (E) não derrubarem nada, o que a tarefa afirma está
  sendo carregado por outra camada — e isso é achado a registrar, não a esconder.

## 2. O pull, e o gate sobre dois artefatos

- [ ] 2.1 `ClienteApi` ganha a chamada da rota que a α criou, devolvendo token e nome de
  apresentação. Resultado: o roster é obtenível pelo aparelho, e ainda não muda tela nenhuma.
- [ ] 2.2 `PreparoDaProva`/`EstadoDaProva`: escolher a prova passa a puxar e guardar o roster ao lado
  do pacote, e o gate passa a exigir os dois. **Roster ausente vira o quinto estado de recusa**, com
  frase própria, ao lado de ausência de rede, pacote ausente, conferência falha e versão
  insuficiente. Presença do arquivo é a afirmação de "puxado" (`design.md`, decisão 2).
- [ ] 2.3 Cenários de gate, no arquivo que já cobre o gate: passa com roster de alunos; **passa com
  roster vazio**; barra com roster nunca puxado; e a frase de recusa do roster ausente é **distinta**
  das outras quatro. Cada asserção confere o **motivo** apresentado, não só que houve recusa.
- [ ] 2.3b Os dois cenários do ciclo de pull, que a auditoria achou sem tarefa: **primeira escolha da
  prova** puxa o roster da API e o guarda no escopo da organização ativa; **segunda escolha, sem
  rede**, usa o guardado e o escaneamento abre normalmente. O segundo é o que prova que o guardado
  serve para o que ele existe — sem ele, o cache estaria coberto só pela escrita.
- [ ] 2.4 **Ver falhar:** fazer o gate barrar também com roster vazio. Esperado: só o cenário do
  roster vazio fica vermelho, e o do roster ausente continua verde — se os dois caírem juntos, "sem
  alunos" e "não sei quem são" estão colapsados, que é exatamente a distinção que a folha avulsa e a
  prova sem roster dependem. Reverter e conferir a reversão **rodando**, pelo `timestamp` do
  relatório.

## 3. O nome na tela

- [ ] 3.1 O resultado resolve o token contra o roster guardado **na hora de apresentar**, e
  SHALL NOT gravar o nome no resultado (`design.md`, decisão 4). Sem linha no roster: apresenta o
  token e diz que a folha não está no roster desta prova, sem tratar como falha de leitura.
- [ ] 3.2 O nome vindo do roster guardado carrega a marca de dado cacheado que `device-session` já
  exige — a marca existente, não uma nova.
- [ ] 3.3 Cenários de resultado: folha de aluno no roster mostra o nome; folha de token fora do
  roster mostra o token com a frase própria e a nota **apurada**; o nome aparece marcado como
  cacheado. Mais a negativa: **o resultado guardado não contém nome** — conferida no que é gravado,
  não na tela.
- [ ] 3.4 **Ver falhar:** gravar o nome dentro do resultado. Esperado: só a negativa da 3.3 fica
  vermelha. Se ela passar mesmo com o nome gravado, a asserção está olhando a tela e não o gravado, e
  a fatia perderia a única garantia de que existe **uma** cópia do dado pessoal.

## 4. O registro que esta fatia fecha

- [ ] 4.1 §16 do `ARQUITETURA-FINAL-v3.md`, linha "O roster cacheado sem regra de apagamento": a
  linha diz que o **nome da mudança entra nela quando `/opsx:propose` criar a mudança**. Escrever
  `slice-4b-roster-no-aparelho` onde hoje está "ainda não proposta como mudança", **preservando** o
  texto corrigido em 2026-09-12 e o registro do engano (P7). Acrescentar parágrafo, não trocar.
- [ ] 4.2 Conferir que a **outra** linha do §16 — a classe H que não enumera o roster baixado —
  **não** é tocada: o dono dela é jurídico externo e a fatia-limite é o piloto nominal. Mostrar no
  `git diff` que só uma linha da tabela mudou.

## 5. Verificação final

- [ ] 5.1 Rodar o **comando cheio do CI** com `--rerun-tasks`, porque `UP-TO-DATE` serve relatório
  velho com contagem plausível, e conferir os números **e o `timestamp`** de cada relatório. Diferente
  da α: aqui `apps/android` muda, então a metade instrumentada **é** exigida — e a suíte instrumentada
  desinstala o aplicativo ao terminar, levando o `filesDir` junto (registrado na cobertura da 4a), o
  que precisa ser considerado ao ler um cenário de cache que "sumiu".
- [ ] 5.2 Rodar `openspec validate slice-4b-roster-no-aparelho --strict`.
- [ ] 5.3 Conferir **arquivo a arquivo**, contra o commit em que esta mudança começou, as negativas
  da proposta: nada em `apps/api`, `apps/web`, `packages/domain`, `vision/` e `omr/`, nenhuma
  migração, e nenhuma spec fora de `device-session` e `scan-session`. Negativa larga não vale —
  nomear a exceção, se houver, e mostrá-la no `git diff`.
- [ ] 5.4 Escrever `docs/cobertura-slice-4b-roster-no-aparelho.md` com **como** cada verificação
  crítica foi vista falhar — a mutação, os cenários que caíram e a **mensagem** da asserção —, mais o
  que ficou sem teste automático e por quê. Nomear explicitamente: que esta fatia **cria** a primeira
  cópia de dado pessoal de aluno fora do servidor e fecha a linha do §16 que trata dela; que o **teto
  de retenção** continua com o jurídico externo; que a desinstalação é garantia da plataforma e não
  do aplicativo; e a KDoc de `VisoesEmArquivo` que diz "Room continua sendo da 4b", como item fora de
  escopo com dono e fatia-limite. **E registrar a própria auditoria:** que os artefatos de
  planejamento afirmaram, numa primeira versão, que a marca de cache já alcançava o roster e que o
  apagamento estava completo só com `sair` — as duas afirmações erradas ficam ditas ao lado do
  conserto (P7), porque foram achadas por leitura da spec vigente e não por teste, e é isso que diz
  quanto vale a leitura como instrumento aqui.
