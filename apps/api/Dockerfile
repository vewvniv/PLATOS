# Imagem da API. **Nao compila nada** — ela empacota uma distribuicao ja construida.
#
# Isso nao e simplificacao, e sim a unica forma que funciona: `generateJooq` sobe um Postgres
# efemero por Testcontainers, e `sourceSets.main` depende da saida dele. Compilar a API exige um
# daemon Docker, e dentro de um `docker build` nao ha daemon. Quem constroi e o GitHub Actions, no
# runner, onde Docker existe — e onde o CI ja roda `generateJooq` verde hoje.
#
# Antes de construir esta imagem:
#   ./gradlew :apps:api:installDist
#
# E o contexto e a **raiz do repositorio**, porque `plans/` mora fora de `apps/api`:
#   docker build -f apps/api/Dockerfile -t platos-api .
FROM eclipse-temurin:21-jre

# jOOQ 3.20 e distribuido em class files da versao 65: abaixo de 21 o runtime nao carrega
# `org.jooq.DSLContext`. A imagem acompanha o `jvmToolchain(21)` do modulo, e nao por preferencia.

WORKDIR /app

# A distribuicao do plugin `application`: `bin/api` e `lib/`.
COPY apps/api/build/install/api/ /app/

# `PLANS_DIR` cai em `plans` relativo ao diretorio de trabalho (`AppConfig`), entao os planos
# precisam existir aqui dentro. Sem eles o catalogo sobe vazio e a resolucao de direito falha na
# primeira requisicao, e nao no arranque — falha tardia com mensagem que nao fala de configuracao.
COPY plans/ /app/plans/

# O bit de execucao nao sobrevive a um contexto montado de sistema de arquivos Windows, onde ele
# nao existe. Sem isto a imagem constroi e so falha ao subir, com "permission denied".
RUN chmod +x /app/bin/api

# Qual build esta nesta imagem, para /health declarar.
#
# **ARG, e nao variavel de execucao**, e a diferenca e o ponto: uma variavel que o operador pode
# editar no painel sem reconstruir diria o que ele digitou, nao o que esta rodando — que e a propria
# deriva que declarar o build existe para detectar. Assado aqui, o identificador e tao imutavel
# quanto o conteudo que ele descreve.
#
# Sem `--build-arg` (build local, `docker build` a mao) ele fica vazio, e `AppConfig` declara
# "desconhecido" — ausencia dita como ausencia, nunca valor inventado. Quem passa no caminho de
# publicacao e `publicar-api.yml`, com o MESMO identificador que forma a tag.
ARG PLATOS_BUILD=""
ENV PLATOS_BUILD=$PLATOS_BUILD

# Sem isto a JVM assume 1/4 da memoria do container como heap maximo, e numa instancia de 512 MB
# isso deixa 128 MB para o heap enquanto o resto fica ocioso. O script do plugin `application`
# repassa `JAVA_OPTS`.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# Usuario proprio: a imagem nao precisa de root para servir HTTP, e um comprometimento do processo
# nao deve chegar com privilegio de administrador do container.
RUN useradd --system --uid 10001 --create-home platos
USER 10001

# Informativo. Quem manda na porta e a variavel `PORT` (o Render injeta a dele), e `AppConfig` cai
# em 8080 quando ela nao existe.
EXPOSE 8080

ENTRYPOINT ["/app/bin/api"]
