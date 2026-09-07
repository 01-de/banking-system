FROM maven:3.9.9-eclipse-temurin-17 AS auth-common-build
WORKDIR /auth-common
COPY auth-common/pom.xml .
COPY auth-common/src ./src
RUN mvn -B install -DskipTests

FROM maven:3.9.9-eclipse-temurin-17 AS build
COPY --from=auth-common-build /root/.m2 /root/.m2
WORKDIR /app
COPY account-service/pom.xml .
COPY account-service/.mvn .mvn
COPY account-service/mvnw .
COPY account-service/src ./src
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:17-jre-jammy
RUN addgroup --system spring && adduser --system --ingroup spring spring
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER spring
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]