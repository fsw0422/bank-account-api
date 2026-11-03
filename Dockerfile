# syntax=docker/dockerfile:1

# ---- Build stage: compile and package with the Gradle wrapper, so the host needs no JDK.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
# jOOQ classes are generated from the schema and committed (see make jooq).
COPY jooq-generated ./jooq-generated
COPY src ./src

# The cache mount keeps the Gradle distribution and dependencies between builds.
# Tests run in CI and `make test`; they need Docker (Testcontainers), which is not available here.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar \
 && java -Djarmode=tools -jar build/libs/app.jar extract --layers --launcher --destination build/extracted

# ---- Runtime stage: JRE only, non-root user, one image layer per dependency group.
FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app
USER app

# Rarely changing layers first, so a code change only rebuilds the small last layer.
COPY --from=build /workspace/build/extracted/dependencies/ ./
COPY --from=build /workspace/build/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/build/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/build/extracted/application/ ./

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080 9090
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
