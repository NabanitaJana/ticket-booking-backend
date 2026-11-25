# Build the jar first: ./gradlew bootJar
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY build/libs/*-SNAPSHOT.jar app.jar
USER 1000
EXPOSE 18085
ENTRYPOINT ["java", "-jar", "app.jar"]
