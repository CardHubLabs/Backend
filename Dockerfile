FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace
COPY gradle gradle
COPY gradlew settings.gradle build.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon
COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre

RUN useradd --system --uid 10001 --create-home cardhub
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar
USER cardhub

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Duser.timezone=UTC", "-jar", "/app/app.jar"]

