FROM eclipse-temurin:17-jre-jammy

RUN groupadd -r appuser && useradd -r -g appuser appuser
WORKDIR /app

COPY --chown=appuser:appuser target/*.jar junes-app.jar

USER appuser

ENTRYPOINT ["java", "-jar", "junes-app.jar"]