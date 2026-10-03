FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
COPY proto/pom.xml proto/pom.xml
COPY worker/pom.xml worker/pom.xml
COPY gateway/pom.xml gateway/pom.xml
COPY proto/src proto/src
COPY worker/src worker/src
RUN mvn -q -pl worker -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/worker/target/inference-worker-1.0.0-SNAPSHOT.jar app.jar
COPY --from=build /app/worker/target/lib lib
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
