# syntax=docker/dockerfile:1.7
# Сборка из корня репозитория: docker build -f agent-adapter/Dockerfile -t localhost:5000/karandash-agent-adapter:latest .
# Учётные данные CLI в образ не попадают: только переменные окружения из секрета при запуске.
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    sh ./gradlew --no-daemon :agent-adapter:bootJar \
    && find agent-adapter/build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /workspace/app.jar \;

FROM node:22-bookworm-slim
ARG CLAUDE_CODE_VERSION=2.1.268
# Пусто — Codex не ставится. Для AGENT_CLI=codex собрать с --build-arg CODEX_VERSION=<версия>.
ARG CODEX_VERSION=
ENV DISABLE_AUTOUPDATER=1 \
    JAVA_HOME=/opt/java/openjdk \
    PATH=/opt/java/openjdk/bin:$PATH
RUN apt-get update \
    && apt-get install -y --no-install-recommends tini ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && npm install -g "@anthropic-ai/claude-code@${CLAUDE_CODE_VERSION}" \
    && if [ -n "${CODEX_VERSION}" ]; then npm install -g "@openai/codex@${CODEX_VERSION}"; fi \
    && npm cache clean --force \
    && HOME=/tmp/cli-check CLAUDE_CONFIG_DIR=/tmp/cli-check claude --version \
    && rm -rf /tmp/cli-check /root/.npm
COPY --from=eclipse-temurin:21-jre-noble /opt/java/openjdk /opt/java/openjdk
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --no-create-home app
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
USER 10001:10001
EXPOSE 8095
# tini забирает осиротевшие процессы CLI; JVM оставляет память CLI-процессам.
ENTRYPOINT ["/usr/bin/tini", "--", "java", "-XX:MaxRAMPercentage=35", "-jar", "/app/app.jar"]
