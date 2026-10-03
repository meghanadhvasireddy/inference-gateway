FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
COPY proto/pom.xml proto/pom.xml
COPY worker/pom.xml worker/pom.xml
COPY gateway/pom.xml gateway/pom.xml
COPY proto/src proto/src
COPY gateway/src gateway/src
RUN mvn -q -pl gateway -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/gateway/target/inference-gateway-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
