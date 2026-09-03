# Cobertura de cenários — fatia 4a-zero (autenticação no aparelho)

Documento em construção: a fatia está parcialmente implementada. O que está aqui já foi verificado;
o que falta está nomeado no fim.

## Como cada verificação crítica foi vista falhar

### Nome de organização inventado pela tela (tarefa 3.4)

O requisito diz que o nome apresentado vem da consulta, e que consulta que falha não produz nome
nenhum. Um nome de reserva é indistinguível de um nome verdadeiro para quem lê a tela — não há
sintoma.

| Defeito introduzido | Quem acusou |
|---|---|
| `SemRede` na consulta passa a produzir `Ativa(Organizacao("desconhecida", "Minha organizacao"))` | `consulta_que_falha_nao_produz_nome_nenhum`, com `esperava SemOrganizacao, veio Ativa(organizacao=Organizacao(id=desconhecida, nome=Minha organizacao))` |

Os outros doze seguiram verdes, e é o esperado: nenhum outro cenário exercita consulta que falha.

### A escolha do usuário anterior herdada pelo seguinte (tarefa 3.7)

**É o defeito mais caro desta fatia.** Aparelho compartilhado entre escolas, o segundo usuário entra
e a organização do primeiro já está ativa. Nada na tela diz isso, e tudo o que ele fizer a seguir
sai atribuído à organização errada.

| Defeito introduzido | Quem acusou |
|---|---|
| `sair()` passa a apagar só a credencial, deixando a organização escolhida no disco | **dois**: `sair_apaga_a_credencial_e_a_organizacao_escolhida` com `sair nao apagou a organizacao escolhida`, e `a_escolha_do_usuario_anterior_nao_e_herdada_pelo_seguinte` com `o segundo usuario herdaria a organizacao do primeiro` |

Dois testes acusando o mesmo defeito não é redundância: o primeiro afirma o mecanismo e o segundo
afirma a consequência para quem usa. Se um dia a implementação mudar de forma, o segundo continua
válido porque não fala de como se apaga.

É por causa deste defeito que `SessaoGuardada` expõe `apagarCredencial` e `apagarOrganizacaoEscolhida`
separadamente, em vez de um `apagarTudo`. Com uma chamada só, o defeito moraria dentro do adaptador
Android e nenhum teste desta camada o alcançaria.

### Os três motivos de falha colapsando num só

`os_tres_motivos_de_falha_sao_distintos_entre_si` compara os três estados por conjunto, e não um a
um. Um teste por motivo passaria com dois deles mapeados para o mesmo valor; este não passa.

### O que a biblioteca reporta, medido e não presumido (tarefa 1.1)

Esta não é uma verificação que foi vista falhar: é uma **medição**, e a regra de verificação do
`CLAUDE.md` pede que uma medição prove reagir ao que mede. Ela prova: as três rodadas correram
contra o mesmo probe, sem mudar uma linha dele, e produziram três resultados diferentes. Uma
medição que devolvesse o mesmo relato nas três condições não teria distinguido nada.

Emulador `platos-atd34` (API 34, `aosp_atd`, x86_64), `supabase-kt` 3.8.0 sobre OkHttp, projeto
Supabase real, 2026-09-02:

| Condição | O que `supabase-kt` entregou |
|---|---|
| Senha errada | `AuthRestException : RestException`, `statusCode=400`, `error=invalid_credentials` |
| Porta recusada | `HttpRequestException : java.io.IOException`, `cause == null` |
| Modo avião | `HttpRequestException : java.io.IOException`, `cause == null` |

**A biblioteca medida aqui não entrou no projeto.** Foi esta medição que a dispensou: a decisão 9 do
`design.md` registra o porquê, e a autenticação acabou saindo por `ktor-client` cru. O que está nesta
tabela é o que decidiu, e não o que o aplicativo usa.

Credencial recusada e falha de transporte são separáveis por tipo, e é o que o requisito exige.
Falha de DNS e porta recusada **não** são — `supabase-kt` descarta a causa, e as duas só diferem
no texto da mensagem. As duas colapsam em `SemRede` de propósito; a decisão 8 do `design.md` diz
por quê, e o que isso obriga na tarefa 4.2.

O relato bruto das três rodadas sai por `logcat -s PLATOS_PROBE:E` e em
`Android/data/com.platos.android/files/probe-<alvo>.txt` no aparelho. O probe é temporário e sai
na tarefa 2.1, junto com as dependências que ele declarou só para `androidTest`.

### A classificação de falha da entrada (tarefa 4.1)

É onde a distinção da decisão 8 vira comportamento. Três defeitos introduzidos de propósito, um a
um, cada um revertido depois:

| Defeito introduzido | Quem acusou |
|---|---|
| `AutenticacaoSupabase` passa a tratar 400..599 como credencial recusada | `servidorComDefeitoNaoEApresentadoComoCredencialRecusada` |
| `retornoDe` passa a capturar `Exception` em vez de `IOException` | `corpoSemAccessTokenEstouraEmVezDeProduzirSessaoVazia` |
| `Retorno.SemRede` passa a virar `CredencialRecusada` | **três**: `portaRecusadaESemRede`, `dnsQueNaoResolveESemRede` e `osTresResultadosSaoDistintosEntreSi` |

O segundo defeito é o mais discreto dos três, e o mais caro: com ele um corpo que não bate com o DTO
— contrato do servidor mudou, campo renomeado — apareceria na tela como problema de conexão, e
ninguém procuraria no lugar certo. `JsonConvertException` desce de `Exception` e não de
`IOException`, e é exatamente por isso que a fronteira estreita funciona.

O terceiro mostra a mesma coisa que a tarefa 3.7 registrou: dois testes acusando um defeito não é
redundância. Os dois primeiros afirmam cada tipo de transporte, e o terceiro afirma que os
resultados não colapsam — e é o terceiro que continua valendo se a implementação mudar de forma.

**O corpo de erro não foi inventado.** `corpoDeCredencialRecusada` é copiado verbatim do relato do
probe contra o projeto real, e foi conferido contra um oracle que não compartilha código nenhum com
o aplicativo: um `curl` do host para o mesmo endpoint, que devolveu o mesmo `400` e o mesmo corpo.

### Um `SemRede` verdadeiro, por acidente (tarefa 4.1)

Vale registrar porque parecia defeito e não era. Numa rodada do probe contra o projeto real, o
adaptador devolveu `SemRede` onde se esperava `CredencialRecusada`. A rede da máquina tinha caído
naquele minuto — o `curl` do host travou ao mesmo tempo, e o `ping` do emulador estava em 955 ms. A
rodada seguinte, sem mudança nenhuma no caminho de classificação, devolveu `CredencialRecusada`.

Foi o único momento em que a classificação foi exercitada contra uma falha de transporte que ninguém
fabricou, e ela acertou. Também é o motivo de o probe passar a repetir a chamada crua quando o
resultado é `SemRede`: `SemRede` não diz **qual** `IOException` foi, e sem esse diagnóstico a
pergunta seguinte exige recompilar o probe.

### A configuração que falta, e a que chegou torta (tarefa 2.2)

A recusa mora no `build.gradle.kts`, e não em código — nenhum teste a alcança. Então ela foi
exercitada onde vive, pela linha de comando, e o que se registra é a mensagem que saiu:

| Defeito introduzido | Quem acusou |
|---|---|
| `platos.supabaseAnonKey` removida de `local.properties` | o build, com `Faltando: platos.supabaseAnonKey (ou a variável de ambiente PLATOS_SUPABASE_ANON_KEY)` |
| `platos.apiUrl` em `http` | o build, com `Invalido: platos.apiUrl: precisa comecar com https:// (veio "http://api.invalido")` |
| `.trimEnd('/')` removido, com `apiUrl` terminando em barra | `ConfiguracaoTest.asUrlsNaoTerminamEmBarra` |

Os dois primeiros nomeiam a chave **e** a variável de ambiente equivalente. Uma mensagem que só
dissesse "configuração incompleta" mandaria quem clona o repositório procurar, e é justamente na
primeira execução que ninguém sabe onde procurar.

O terceiro é o único que um teste pega, e é o mais silencioso dos três: a barra final produz
`https://projeto//auth/v1/...`, que alguns servidores aceitam e outros recusam. O defeito
dependeria do servidor, e nenhuma rodada local o encontraria.

**O caminho do CI foi exercitado, e não deduzido.** Sem as chaves `platos.*` em `local.properties` e
sem ambiente, o build recusa; sem elas e **com** as variáveis de ambiente, ele compila e os três
cenários passam. É o mesmo caminho que o runner percorre, e foi rodado antes de ir para o CI —
diferente do que aconteceu com o probe, que foi para o CI sem essa conferência e o derrubou.

`ConfiguracaoTest` roda no CI com os valores de marcador e continua valendo: o que ele afirma é
forma, e não qual projeto. Afirmar o projeto exigiria versionar o projeto.

## O que ainda não está verificado

| O que | Por quê |
|---|---|
| O interceptador de 401, a cifragem em repouso e a configuração | Tarefas 4.2 a 4.5, ainda não implementadas |
| O **corpo de sucesso** da autenticação | O probe entra com senha errada de propósito, então nenhuma rodada autenticou. Os nomes de campo do DTO vêm da documentação do Supabase, não de medição desta base. Fecha na tarefa 6.1 |
| O adaptador contra o servidor real, no CI | O probe é **pulado** no runner: não há `local.properties`, então não há projeto para medir. Ele é o instrumento da seção 6, e a classificação de falha é verificada na JVM por `AutenticacaoSupabaseTest` |
| As telas | Seção 5, ainda não implementada |
