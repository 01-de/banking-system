FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY fraud-detection-service/pom.xml .
COPY fraud-detection-service/.mvn .mvn
COPY fraud-detection-service/mvnw .
COPY fraud-detection-service/src ./src
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:17-jre-jammy
RUN addgroup --system spring && adduser --system --ingroup spring spring
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER spring
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
