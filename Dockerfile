# Simpler prod Dockerfile if JAR is pre-built by the agent
FROM eclipse-temurin:17-jre-jammy

RUN groupadd -r appuser && useradd -r -g appuser appuser
WORKDIR /app

COPY target/*.jar app.jar
RUN chown appuser:appuser junes-app.jar

USER appuser

ENTRYPOINT ["java", "-jar", "junes-app.jar"]