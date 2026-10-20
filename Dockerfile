FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -DskipTests dependency:go-offline

COPY src src
RUN mvn -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 10000

CMD ["sh", "-c", "java -Dserver.address=0.0.0.0 -Dserver.port=${PORT:-8080} -jar app.jar"]
