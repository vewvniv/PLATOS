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

**Um defeito que os três não pegaram, e apareceu no CI.** A exigência era avaliada ao configurar o
projeto, e o Gradle configura todos — então `:apps:api:installDist` passou a exigir configuração do
Android, e o workflow que publica a imagem da API quebrou. Nenhum dos três cenários acima o
alcançava, porque todos exercitam o módulo Android, que é justamente onde a exigência faz sentido.
Quem acusou foi o CI, e o registro fica porque a lição é sobre onde a verificação **não** olhava:
`./gradlew :apps:api:test` sem configuração do Android é agora parte do que se confere.

**O caminho do CI foi exercitado, e não deduzido.** Sem as chaves `platos.*` em `local.properties` e
sem ambiente, o build recusa; sem elas e **com** as variáveis de ambiente, ele compila e os três
cenários passam. É o mesmo caminho que o runner percorre, e foi rodado antes de ir para o CI —
diferente do que aconteceu com o probe, que foi para o CI sem essa conferência e o derrubou.

`ConfiguracaoTest` roda no CI com os valores de marcador e continua valendo: o que ele afirma é
forma, e não qual projeto. Afirmar o projeto exigiria versionar o projeto.

### O espelho do contrato e a credencial em cada chamada (tarefa 4.2)

O DTO de organização do aparelho é **espelho** do `OrganizationDto` do servidor, e não o mesmo
arquivo: os dois módulos dependem de `packages:domain`, então compartilhar de verdade seria
possível, e esta fatia declara `packages/domain` e `apps/api` intocados. O custo dessa escolha é
deriva silenciosa entre os dois lados, e quem paga por ela é `ApiPlatosTest`, que fixa o JSON
literal que o servidor emite — com os valores que `MeOrganizationsTest` afirma do outro lado.

Três mutações, todas revertidas:

| Mutação | O que ficou vermelho |
|---|---|
| A credencial deixa de ser posta por `defaultRequest` | `a credencial da sessao viaja como Bearer`, `a credencial e lida a cada chamada` |
| `name` ganha valor padrão, como o DTO da credencial tem | `contrato quebrado estoura em vez de virar organizacao sem nome` |
| O espelho deriva do servidor (`name` → `title`) | quatro cenários, entre eles os dois da credencial |

A terceira é a que importa para a escolha de espelhar. Ela derruba mais do que os testes de
contrato porque o corpo de sucesso deixa de desserializar, e aí nem a chamada chega a acontecer —
deriva de um campo só não fica confinada ao campo.

**Por que `name` é obrigatório aqui e o token não era.** `CredencialDeSessao` dá padrão a tudo menos
ao `access_token`, e a razão está escrita lá: aquele corpo nunca foi medido, vem da documentação de
terceiro, e campo ausente é possibilidade real. Este contrato está neste repositório, com teste do
outro lado. Campo que suma é contrato quebrado, e precisa estourar em vez de virar string vazia que
a tela apresentaria como nome de organização — que é o mesmo defeito da tarefa 3.4, entrando por
outra porta.

**A credencial não é posta pelo método que chama.** Existe um endpoint só, e mesmo assim o cabeçalho
sai de `defaultRequest`: quem acrescentar o segundo não tem como esquecer, porque não há nada para
lembrar. É a decisão 3 aplicada à credencial, e não só ao 401.

**O 401 continua cru.** `organizacoes()` devolve `Retorno.Recusou(401)`, e um teste afirma isso pelo
nome. Traduzi-lo para sessão expirada aqui seria o defeito que a tarefa 4.4 existe para demonstrar,
e a 4.3 é quem tem o ponto único.

### O 401 tratado pelo chamador, e a expiração escapando (tarefas 4.3 e 4.4)

A mutação que a 4.4 pede, feita inteira: o `HttpResponseValidator` sai de `clienteApi`, o 401 passa
a ser tratado dentro de `ApiPlatos.organizacoes()`, e nasce uma segunda chamada autenticada —
`pacote(id)` — escrita por quem não sabia da regra. Foi assim que a árvore respondeu:

| Teste | Sob a mutação |
|---|---|
| `ClienteApiTest > 401 leva a sessao a expirar` | vermelho |
| `ClienteApiTest > 401 numa rota que ninguem previu tambem leva` | vermelho |
| `ClienteApiTest > um 401 dispara uma expiracao, e nao duas` | vermelho |
| `EscapeTemporarioTest > o 401 da segunda chamada nao expira a sessao` | **verde** |
| Os onze cenários de `ApiPlatosTest` | **todos verdes** |

As duas últimas linhas são o achado, e não as três primeiras.

O teste temporário passando *é* o defeito: com o 401 no chamador, a segunda chamada autenticada
devolve `Recusou(401)` e a sessão não fica sabendo. Nada na tela diz que a credencial morreu, e o
professor vê uma falha genérica numa tela de trabalho — que é exatamente o que o requisito proíbe
ao dizer que a expiração não pode ser adiada para falhar depois com mensagem que não seja sobre a
sessão.

E `ApiPlatosTest` inteiro continuou verde. Faz sentido: aquele arquivo exercita `organizacoes()`, e
sob a mutação `organizacoes()` trata o 401 corretamente. Uma suíte escrita só contra o adaptador
teria aprovado a versão defeituosa por unanimidade. **O que pega o defeito é o teste ser escrito
contra o cliente, em rotas inventadas na hora** — `/exam-packages/42`, `/qualquer/coisa/futura`,
que não têm método nenhum em `ApiPlatos`. A afirmação da 4.3 é sobre chamadas que ainda não
existem, e por isso a verificação também precisa ser.

É a mesma lição da tarefa 2.2 por outro caminho: lá os três cenários exercitavam o módulo Android,
que era justamente onde a exigência fazia sentido, e por isso nenhum alcançava o defeito. Aqui os
onze cenários exercitavam o método que trata o 401, que era justamente onde o defeito não estava.

**O que não expira a sessão, e é decisão e não descuido.** 403, 500 e falha de transporte passam
sem tocar na sessão, cada um com seu cenário. O transporte é o que mais importa: sem rede o
aparelho não sabe nada sobre a validade da credencial, e apagá-la mandaria o professor digitar a
senha de novo por causa de um túnel que caiu.

**O interceptador é `HttpResponseValidator`, e não `ResponseObserver`.** O observador roda numa
corrotina à parte, então a tela poderia desenhar o resultado da chamada antes de a sessão saber que
expirou — uma corrida que passa em teste e aparece em sala.

### O keyset corrompido, e a API depreciada que o torna nosso (tarefa 4.5)

`security-crypto` está depreciada a partir de `1.1.0-beta01`, e a revisão de 2026-09-03 da decisão 5
registra por que ela fica: a depreciação é a AndroidX preferindo Keystore direto à wrapper, e não
falha de segurança — então ela propõe justamente a alternativa que a decisão 5 já havia descartado,
pelo mesmo motivo. O que muda não é a escolha, é a consequência: **biblioteca depreciada não recebe
correção, então o modo de falha conhecido dela passa a ser responsabilidade desta base.**

Esse modo é um só: keyset corrompido. Ele aparece em dois momentos, que pedem respostas diferentes.

| Momento | Resposta | Por quê |
|---|---|---|
| Ao **abrir** o arquivo cifrado | descarta e tenta de novo, uma vez | tem conserto: keyset novo, sessão perdida |
| Ao **ler** um valor | sessão inválida | não tem conserto: valor que não decifra é valor perdido |

Nos dois casos o desfecho é a tela de entrada, e nunca uma exceção que sobe. Pedir a senha outra vez
é o pior desfecho aceitável; deixar subir daria um aplicativo que não abre, a partir de um dado
ilegível.

**A decisão não mora no adaptador, e essa é a razão de haver teste.** `abrindoOuDescartando` e
`lendoOuSessaoInvalida` não conhecem Android: a corrupção entra como exceção lançada por uma lambda,
que é a forma com que ela chega do `EncryptedSharedPreferences`. Se elas vivessem dentro de
`SessaoGuardadaAndroid`, só um teste instrumentado as alcançaria, e o único modo de falha conhecido
desta escolha ficaria sem cobertura até alguém ligar um aparelho. É a mesma fronteira da decisão 1,
aplicada a um lugar onde ela não era óbvia.

Duas mutações, revertidas:

| Mutação | O que ficou vermelho |
|---|---|
| Abrir sem tratamento nenhum | os três cenários de corrupção ao abrir |
| `SecurityException` fora da lista da leitura | `SecurityException tambem e sessao invalida` |

A segunda é a que quase não foi escrita. `EncryptedSharedPreferences` embrulha falha de decifragem
em `SecurityException`, que **não** desce de `GeneralSecurityException` — o caso mais provável
escaparia de um `catch` que parecesse completo. Uma lista de exceções não é verificada por leitura.

**O que continua subindo, e é decisão.** Exceção que não é corrupção passa direto, nos dois pontos,
com um cenário para cada. Engolir o desconhecido transformaria defeito de programação em "sem
sessão", e o sintoma seria o professor reautenticando para sempre sem nada dizer por quê. É o mesmo
critério de `retornoDe`, que deixa `JsonConvertException` subir em vez de chamá-la de "sem rede".

### A credencial em repouso, no aparelho (tarefa 4.6)

Os dois cenários de credencial em repouso da spec, rodados no `platos-atd34` (API 34, `aosp_atd`,
x86_64), em 2026-09-03. Eles não rodam na JVM: `EncryptedSharedPreferences` exige o Keystore, e
"está cifrado" só se afirma lendo o que foi gravado.

**Procurar um token e não achar passa por vários motivos errados** — a busca olhando no lugar
errado, nada tendo sido gravado, o arquivo ainda não tendo chegado ao disco. Por isso o teste grava
duas coisas: a credencial, que deve sumir, e o identificador da organização, que é preferência e
fica em claro de propósito. Achar o segundo é o que prova que a busca alcança os arquivos e sabe
ler o que há neles; só depois disso não achar o primeiro significa alguma coisa. Esperar pelo
identificador também é o que sincroniza o teste com o `apply()`, que grava fora da linha de
execução.

Quatro mutações, todas revertidas:

| Mutação | O que ficou vermelho |
|---|---|
| Sessão guardada em claro, confiando só no sandbox | `o token aparece como texto legivel no armazenamento` |
| Token guardado em Base64 | `o token aparece apenas codificado em Base64, que nao protege nada` |
| `allowBackup` de volta para `true` | `FLAG_ALLOW_BACKUP esta ligada`, `expected:<0> but was:<32768>` |
| A busca varrendo `cacheDir` em vez de `dataDir` | a **guarda**: `a busca nao achou nem o identificador da organizacao` |

As duas últimas linhas são as que valem.

A de Base64 existe porque a busca pelo token cru aprovaria um token apenas codificado, e para quem
lê o resultado do teste isso seria indistinguível de cifragem. Ela ficou vermelha **sozinha** — a
afirmação sobre texto legível passou na mesma rodada —, o que mostra que as duas cobrem defeitos
diferentes e nenhuma é sobra da outra.

A última é a que impede o teste de ser vazio. Com a busca apontada para o diretório errado, a
afirmação sobre o token passaria sem nada ter sido examinado, e o cenário ficaria verde para
sempre, dizendo nada. Foi a guarda que acusou, e não o token — que é exatamente o desenho: é a
terceira vez nesta base que uma janela de medição mal apontada produz verde falso, e a primeira em
que a janela foi verificada antes de o resultado ser usado.

`FLAG_ALLOW_BACKUP` é afirmada como flag, e não como lista de arquivos excluídos, porque é a flag
que o sistema consulta. Regra que lista arquivos silencia quando alguém acrescenta o terceiro.

## O que ainda não está verificado

| O que | Por quê |
|---|---|
| A cifragem em repouso **fora do emulador** | Fechada no `platos-atd34` (tarefa 4.6), com quatro mutações. Num aparelho real o Keystore é respaldado por hardware, e no emulador não — o que muda é a força da chave, e não onde o token é gravado, que é o que o teste afirma. A conferência em aparelho é a seção 6 |
| O adaptador da API contra o servidor de verdade | `ApiPlatosTest` usa `MockEngine`, e o corpo que ele responde é literal escrito à mão a partir do contrato — não do servidor rodando. O que fecha isso é a tarefa 6.1 |
| O **corpo de sucesso** da autenticação | O probe entra com senha errada de propósito, então nenhuma rodada autenticou. Os nomes de campo do DTO vêm da documentação do Supabase, não de medição desta base. Fecha na tarefa 6.1 |
| O adaptador contra o servidor real, no CI | O probe é **pulado** no runner: não há `local.properties`, então não há projeto para medir. Ele é o instrumento da seção 6, e a classificação de falha é verificada na JVM por `AutenticacaoSupabaseTest` |
| As telas | Seção 5, ainda não implementada |
