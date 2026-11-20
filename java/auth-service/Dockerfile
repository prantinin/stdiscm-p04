FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY java ./java
COPY proto ./proto
RUN mvn -B -f java/pom.xml -pl auth-service -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/java/auth-service/target/auth-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 50051
ENTRYPOINT ["java", "-jar", "/app/app.jar"]