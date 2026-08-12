FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:resolve
COPY src src
RUN ./mvnw package -DskipTests
EXPOSE 3000
CMD ["java", "-jar", "target/ticket2cash-0.0.1-SNAPSHOT.jar"]