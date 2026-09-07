FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY notification-service/pom.xml .
COPY notification-service/.mvn .mvn
COPY notification-service/mvnw .
COPY notification-service/src ./src
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:17-jre-jammy
RUN addgroup --system spring && adduser --system --ingroup spring spring
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER spring
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
