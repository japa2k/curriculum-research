# --- Build: the Maven wrapper packages the jar (tests run outside Docker with ./mvnw test).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

# --- Runtime: JRE, fonts for the PDFs and the Claude Code CLI the app calls for each job.
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates fonts-liberation \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --create-home --shell /bin/bash app
USER app
RUN curl -fsSL https://claude.ai/install.sh | bash
ENV PATH="/home/app/.local/bin:${PATH}" \
    TZ=America/Sao_Paulo

WORKDIR /app
COPY --from=build --chown=app:app /build/target/curriculum-research-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
