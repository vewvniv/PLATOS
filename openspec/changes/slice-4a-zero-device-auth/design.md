## Context

Ver `proposal.md — Why`. O que importa para o desenho:

- `apps/android` não tem cliente HTTP, nem `supabase-kt`, nem armazenamento persistente de espécie alguma. Tudo aqui nasce do zero.
- O servidor já fixou o contrato e ele não muda: `JwtAuth.kt` verifica Bearer JWT por JWKS com issuer e audience, e `/me/organizations` faz o provisionamento idempotente da organização pessoal (D-0.4) antes de listar.
- A fatia 3a desenhou uma fronteira que a 3c repetiu e que esta fatia herda: **o que decide é Kotlin puro, testável na JVM; o que fala com o mundo é adaptador fino sem decisão**. `ScanSession` é o precedente direto — quadros entram, estado sai, sem CameraX no meio.
- ADR-0013 decisão 1 é a fonte desta fatia. As decisões 2 a 5 daquele ADR são da 4a e não valem aqui.

## Goals / Non-Goals

**Goals:**

- Os cinco requisitos de `device-session` verificáveis na JVM, sem servidor e sem aparelho.
- Um ponto único onde sessão expirada é detectada, para que o requisito de não adiar a detecção seja estrutural e não disciplina de quem escreve cada chamada.
- Configuração de ambiente sem segredo no repositório.

**Non-Goals:**

- Renovação silenciosa de token. Sessão expirada volta à entrada; refresh é otimização, e otimizar antes de existir uso é prematuro.
- Qualquer persistência relacional. Room é da 4b (ADR-0013, decisão 3).
- Cache de pacote e a limpeza dele no logout. É a 4a, e o ADR já nomeia o critério A12 para que ela não esqueça.

## Decisions

### 1. A sessão é uma máquina de estados em Kotlin puro; rede e HTTP são adaptadores

`DeviceSession` recebe resultados já destilados — autenticou, a lista de organizações chegou, a chamada foi recusada por credencial, por rede ou por sessão — e produz o estado que a tela desenha. Não conhece cliente HTTP nem Compose.

**Por quê:** é o único jeito de os treze cenários da spec rodarem na JVM. A alternativa — estado dentro do `ViewModel` chamando a rede direto — torna cada cenário um teste instrumentado com servidor de mentira, e a fatia 3c já mostrou o custo disso: o que sobrou sem teste automático lá foi exatamente o encanamento que não se isola.

**Alternativa descartada:** deixar a distinção dos três modos de falha para a camada HTTP. Ela nasceria acoplada a códigos de status, e o requisito é sobre o que a pessoa lê na tela.

### 2. Dois destinos de rede: a credencial no Supabase Auth, os dados na API

~~Autenticação por `supabase-kt`~~ → **a metade sobre a biblioteca foi superada pela decisão 9**, que a mediu e a dispensou. O que continua valendo é a outra metade, que é a que importa: são dois destinos de rede diferentes, e não um — a credencial nasce no Supabase Auth, e todo dado de domínio vem da API Ktor. §13 fixa os dois lados. As duas pontas passaram a ser alcançadas pelo mesmo `ktor-client`.

**Por quê `ktor-client` e não OkHttp/Retrofit:** o projeto já é Ktor 3 com `kotlinx.serialization` no servidor, e os DTOs são os mesmos. Uma pilha HTTP no projeto em vez de duas, e nenhuma tecnologia nova a justificar.

### 3. Sessão expirada é detectada num interceptador, não em cada chamador

Uma resposta 401 da API leva a sessão ao estado de expirada, num ponto único do cliente HTTP. Nenhuma tela trata 401 por conta própria.

**Por quê:** o requisito diz que a expiração não pode ser adiada para falhar numa chamada posterior com mensagem que não seja sobre a sessão. Espalhar isso por chamador é a forma de a regra valer hoje e furar no próximo endpoint que alguém escrever — e é exatamente o defeito que a 3c registrou ao recusar classificação por texto de mensagem.

### 4. Duas chaves guardadas, sem registro de estado local

A sessão e o identificador da organização escolhida ficam em armazenamento privado do aplicativo, e sair apaga as duas.

**Por quê não um registro de "estado local":** com dois itens é abstração prematura (`CLAUDE.md` regra 8). Quando forem quatro ou cinco, revisita-se.

**O backup automático é desabilitado para as duas.** É a diferença entre "outro aplicativo não lê" e "o dado não sai do aparelho": só a primeira vem de graça, e um backup na nuvem leva a credencial junto. Como a credencial fica cifrada em repouso, ver a decisão 5.

### 5. A credencial fica cifrada em repouso

A sessão é guardada com `EncryptedSharedPreferences`, com chave no Android Keystore. A organização escolhida, que é preferência e não credencial, fica no armazenamento privado comum.

**Por quê:** o sandbox do Android impede que outro aplicativo leia o armazenamento privado, e isso é tudo o que ele impede. Num aparelho compartilhado entre escolas — o mesmo modelo de ameaça da tarefa 3.7 — o que sobra é acesso físico ao aparelho, e aí um token em claro é credencial de rede reutilizável, não preferência. A diferença entre os dois itens é essa, e é por isso que eles não recebem o mesmo tratamento.

**Alternativa descartada:** guardar as duas em claro e confiar no sandbox. É o que a maioria dos aplicativos faz, e é defensável quando o aparelho é pessoal. Este não é: a fatia inteira foi desenhada em torno de troca de usuário no mesmo aparelho, e seria incoerente proteger a escolha de organização contra herança e deixar o token exposto.

**Alternativa descartada:** Keystore direto, cifrando à mão. `EncryptedSharedPreferences` já é a composição das duas coisas, e escrever a cifragem à mão acrescenta superfície de erro sem acrescentar garantia.

### 6. Configuração por `BuildConfig`, alimentada fora do repositório

URL do projeto Supabase, chave anônima e URL da API entram como campos de `BuildConfig` a partir de propriedades de build, não versionadas.

A chave anônima do Supabase é pública por desenho — ela identifica o projeto e a autorização real é RLS mais JWT. Mesmo assim ela não é versionada, porque a URL da API e o ambiente mudam entre desenvolvimento e produção, e um valor embutido vira o valor errado em silêncio.

**O mecanismo, implementado na tarefa 2.2.** Três campos — `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `API_URL` — resolvidos nesta ordem: propriedade de projeto (`-P`), variável de ambiente, `local.properties`. A primeira serve a uma rodada avulsa, a segunda é como o CI alimenta, e a terceira é o dia a dia de quem desenvolve.

**Ausente falha o build, e não vira string vazia.** Vazio compila, instala e só quebra na primeira chamada de rede, com erro que fala de rede e não de configuração. A mensagem lista tudo que falta de uma vez, nomeando a chave e a variável de ambiente equivalente, e mostra o bloco a colar em `local.properties`.

Duas conferências de forma vêm junto, e nenhuma é preferência. **`https` é obrigatório** porque `targetSdk 35` recusa tráfego em claro antes de abrir soquete, e a falha sairia como `UnknownServiceException: CLEARTEXT` — política de rede disfarçada de falha de transporte, que a decisão 8 classificaria como `SemRede`. **A barra final é removida** porque quem chama concatena `"$urlBase/auth/v1/..."`, e `https://projeto//auth/v1/...` é aceito por alguns servidores e recusado por outros: defeito que depende do servidor e não aparece em teste.

**O CI recebe valores de marcador, em `.invalido`.** Ele não fala com o Supabase — o probe que falaria é pulado, e nenhuma tela alcança a rede ainda. O TLD reservado garante que, se algum dia algo ali tentar sair para a rede, a falha é imediata em vez de acertar um servidor por engano.

**O probe mantém canal próprio, e isso é deliberado.** As duas configurações leem o mesmo `local.properties`, mas têm semânticas **opostas** de ausência: a do aplicativo ausente falha o build, porque aplicativo sem destino não serve para nada; a do probe ausente é um não-evento, porque sem projeto real não há o que medir. Com um canal só, o probe teria de reconhecer o valor de marcador do CI por comparação de string — classificar por texto, que é o que esta fatia recusa em todo lugar.

### 7. A entrada vira o launcher; `ScanActivity` passa a ser alcançada, e não iniciada

A tela de entrada é o `Activity` de lançamento. A tela de escaneamento continua exatamente como está — inclusive com o `assets.open` provisório, que só sai na 4a.

**Por quê manter o asset agora:** removê-lo aqui deixaria a fatia sem tela de trabalho nenhuma para alcançar, e o critério de aceite desta fatia é sobre entrar, não sobre escanear.

### 8. A distinção vem do tipo da exceção, e "sem rede" absorve "servidor inalcançável"

Medido, não presumido — probe da tarefa 1.1 contra o projeto Supabase real, no emulador
`platos-atd34` (API 34, `aosp_atd`, x86_64), com `supabase-kt` 3.8.0 sobre engine OkHttp, em
2026-09-02. Três rodadas, e o que cada uma produziu:

| Condição | `supabase-kt` | Ktor cru no mesmo endpoint |
|---|---|---|
| Senha errada, projeto real | `AuthRestException : RestException : Exception`, `statusCode=400`, `error=invalid_credentials`, corpo repassado, `cause == null` | HTTP 400, `{"code":400,"error_code":"invalid_credentials","msg":"Invalid login credentials"}` |
| Porta recusada (`https://127.0.0.1:1`) | `HttpRequestException : java.io.IOException`, **`cause == null`** | `ConnectException : SocketException : IOException`, com `ErrnoException: ECONNREFUSED` na causa |
| Modo avião, projeto real | `HttpRequestException : java.io.IOException`, **`cause == null`** | `UnknownHostException : IOException` |

**Os dois casos que o requisito exige separar são separáveis, e por tipo.** Credencial recusada
chega como `RestException`, que carrega `statusCode`; qualquer falha de transporte chega como
`IOException`. `catch (e: RestException)` contra `catch (e: IOException)` responde, e nenhuma
conferência de conectividade antes da chamada é necessária — a saída que a Risks já recusava
continua recusada, agora com medição em vez de expectativa.

**Mas `supabase-kt` descarta a causa.** `HttpRequestException.cause` é nulo nas duas falhas de
transporte: DNS que não resolve e porta que recusa chegam com o mesmo tipo e a mesma forma,
diferindo só no texto da mensagem — e o probe registra mensagem, nunca a interpreta. Então, por
`supabase-kt`, "sem rede" e "servidor fora do ar" **não** são distinguíveis.

**Decisão:** as duas colapsam no estado `SemRede`, de propósito. Para quem lê a tela, a ação é a
mesma — não deu para falar com o servidor, tente de novo —, e inventar um quarto estado a partir
de texto de mensagem seria classificar por string, que é o que a 3c já recusou. A spec pede três
estados distintos, e os três continuam distintos: o colapso acontece **dentro** de `SemRede`.

**Consequência para a tarefa 4.2:** o cliente Ktor cru da API preserva a cadeia de causas, então
ali a informação existe. Ele deve classificar do mesmo jeito assim mesmo — `IOException` inteira
para `SemRede` —, senão o mesmo aparelho sem rede diria uma coisa na entrada e outra na consulta.

**O que o probe custou em toolchain, e que fechou a decisão 9:**

- `auth-kt-android` 3.8.0 arrasta `androidx.browser:browser:1.10.0`, que **exige `compileSdk 36`**.
  Medido, e não deduzido do aviso: `platforms;android-36` instalado, `compileSdk` em 36 com
  `targetSdk` mantido em 35, e o probe repetido contra o grafo verdadeiro, sem remendo — mesmo
  resultado da tabela acima, com `testDebugUnitTest`, `lintDebug` e `assembleDebug` verdes.
- Ktor 3.5.1 (exigido por `supabase-kt` 3.8.0) pede coroutines 1.11.0, e o aplicativo estava em
  1.7.3 via `lifecycle` 2.8.7. A **resolução consistente** do AGP amarra o classpath do
  `androidTest` ao do aplicativo, então não existe alinhar só o lado do teste. Sintoma quando não
  se alinha: `NoSuchMethodError: runBlockingK`, em execução, e não em compilação.
- O `SessionManager` padrão do `supabase-kt` depende de um `Context` posto por um `ContentProvider`
  da própria biblioteca.

### 9. A autenticação entra por `ktor-client`; `supabase-kt` fica de fora

`POST /auth/v1/token?grant_type=password`, com a chave anônima em `apikey`, pelo mesmo cliente Ktor
que a decisão 2 já escolheu para os dados. Nenhuma dependência de `supabase-kt` no projeto.

**Por quê:** a decisão 8 mediu, e o que ela mediu decide. O probe bateu no endpoint pelas duas vias
e recebeu **a mesma resposta** — HTTP 400, mesmo corpo. O que difere é o que a biblioteca faz com a
falha de transporte: ela descarta a causa (`HttpRequestException.cause == null`), e a causa é
justamente o material da distinção que a spec exige. A biblioteca custava mais e informava menos.

Os outros dois motivos já estavam decididos em outro lugar, e só não tinham sido somados: refresh é
Non-Goal desta fatia, então o que `auth-kt` traz de mais útil não seria usado; e a decisão 5 põe a
credencial em `EncryptedSharedPreferences` sobre chave do Keystore, então o `SessionManager` da
biblioteca seria substituído de qualquer forma.

**O que isso remove:** `compileSdk` volta a 35, `androidx.browser` sai do grafo, e o risco de exigir
`platforms;android-36` no runner de CI **deixa de existir** — não é mitigado, é inaplicável. O
`platforms;android-36` instalado na máquina de desenvolvimento fica sem uso, e não atrapalha.

**O que isso custou, e não estava previsto:** `ktor-client-core-jvm` **3.2.0 não passa pelo D8**.
Ele tem um campo chamado `use streaming syntax` — com espaços — em `io.ktor.client.plugins.Messages`,
e nome com espaço é ilegal em DEX abaixo da versão 040. Falha como `Error while dexing`, na montagem
do APK, e não na compilação. É bug do Ktor, corrigido no primeiro patch. O catálogo subiu para
**3.2.1**, que é o menor salto que dexa — e como a versão é uma só para servidor e aparelho (decisão
2), **o servidor subiu junto**. Conferido, e não presumido: `:apps:api:build` verde com 3.2.1, 106
testes em 18 classes, zero falhas, com Testcontainers subindo Postgres de verdade. Isso é o conflito
de Ktor que a seção de riscos previa; ele não apareceu como divergência de versão entre as duas
pontas, e sim como uma versão que o Android não aceita.

**Coroutines:** sem fixação nenhuma. Ktor 3.2.1 puxa 1.10.2, que resolve por conflito contra o 1.7.3
do `lifecycle`; o aplicativo e o `androidTest` resolvem os dois para 1.10.2, então o descompasso que
existia com `auth-kt` não existe aqui — ele só existia porque a dependência era exclusiva do
`androidTest`. O 1.11.0 saiu junto com a biblioteca.

**APK de debug, universal com as quatro ABIs: 165 484 253 bytes**, contra a linha de base de
158 560 728 — **subiu 6 923 525 bytes**. A expectativa era cair, e ela partia de uma premissa que
não se sustenta: `auth-kt` nunca esteve no APK do aplicativo, só no de teste, então removê-lo não
tinha como encolher nada. O que cresceu é o cliente HTTP entrando no aplicativo pela primeira vez —
`ktor-client-core`, o engine OkHttp com Okio, negociação de conteúdo e serialização. Esse custo
seria pago igual com `supabase-kt`, que carrega o mesmo cliente Ktor por dentro, e mais a biblioteca.

**O que continua sem medição:** o corpo de **sucesso**. O probe entra com senha errada de propósito,
então nenhuma rodada autenticou, e os nomes de campo do DTO vêm da documentação do Supabase, não
desta base. Por isso `access_token` é o único campo sem valor padrão: ausente, a desserialização
estoura em vez de produzir sessão vazia que só falharia depois. Quem fecha isso é a tarefa 6.1.

**A decisão 6 não muda.** Só o transporte da autenticação mudou; de onde a URL e a chave vêm
continua sendo a tarefa 2.2, por `BuildConfig` alimentado fora do repositório, e nada disso é
versionado.

## Risks / Trade-offs

**A distinção entre "sem rede" e "credencial recusada" depende do que a biblioteca reporta** → se `supabase-kt` achatar as duas num erro só, o requisito não é atendível como escrito. É a primeira coisa a verificar, antes de qualquer tela.

**E a saída não é conferir a conectividade antes de tentar.** Conectividade é estado no instante da checagem, e a chamada acontece depois: a rede cai no meio, a checagem já passou, e o erro é reportado como credencial recusada. Z3 quebraria de forma **intermitente**, que é a pior forma de quebrar — passa no teste, falha na sala. A distinção tem de vir **do lado da falha**: exceção de transporte é uma coisa, resposta HTTP 401 é outra, e as duas chegam por caminhos diferentes mesmo quando a biblioteca as embrulha no mesmo tipo. Se ela achatar até isso, a saída é desembrulhar a causa, não adivinhar pelo ambiente.

**Não há projeto Supabase de teste no repositório** → os cenários da spec rodam contra a máquina de estados na JVM, sem rede. O que sobra sem teste automático é o encanamento — o interceptador de 401, a cifragem em repouso e a configuração —, e isso vai para conferência em aparelho com roteiro escrito, como a 3c fez. O adaptador de entrada **saiu** dessa lista: `MockEngine` o exercita na JVM contra resposta de verdade (decisão 9).

**~~`supabase-kt` traz um grafo de dependências grande, com Ktor client dentro~~** → resolvido pela decisão 9: a biblioteca não entra. O conflito de Ktor que este risco previa aconteceu mesmo assim, e por um caminho que ele não previa — 3.2.0 não dexa —, e está registrado na decisão 9. A comparação de APK "contra o número que a 3c registrou" não foi possível: **esse número não existe**, porque a 3c pediu o registro e não o escreveu. A linha de base passa a ser a medição de 2026-09-02.

**Sem refresh, a sessão expira e o professor volta ao login no meio da aula** → aceito nesta fatia, e é dado para decidir depois: se a expiração incomodar em uso real, refresh entra com evidência. Adivinhar a janela agora seria escolher número sem medição.
