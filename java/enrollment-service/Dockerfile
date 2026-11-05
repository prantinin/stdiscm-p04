FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY proto ./proto
COPY java ./java
RUN mvn -f java/pom.xml -pl enrollment-service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/java/enrollment-service/target/enrollment-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 50053
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
