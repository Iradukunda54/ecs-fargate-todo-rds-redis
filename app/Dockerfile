# ---- build stage -------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -q -ntp package -DskipTests

# ---- runtime stage -----------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy
ARG APP_VERSION=dev
ENV APP_VERSION=${APP_VERSION} \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app
COPY --from=build --chown=app:app /src/target/todo-app.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
