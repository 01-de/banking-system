FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY auth-service/pom.xml .
COPY auth-service/src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:17-jre-jammy
RUN addgroup --system spring && adduser --system --ingroup spring spring
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER spring
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
