FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY java ./java
COPY proto ./proto
RUN mvn -f java/pom.xml -pl grades-upload-service -am -DskipTests package
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/java/grades-upload-service/target/grades-upload-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 50055
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
