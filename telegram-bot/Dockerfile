# syntax=docker/dockerfile:1.7
# Сборка из корня репозитория: docker build -f telegram-bot/Dockerfile -t localhost:5000/karandash-telegram-bot:latest .
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    sh ./gradlew --no-daemon :telegram-bot:bootJar \
    && find telegram-bot/build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /workspace/app.jar \;

FROM eclipse-temurin:21-jre-noble
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --no-create-home app
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
