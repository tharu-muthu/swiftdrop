FROM eclipse-temurin:21.0.13_9-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src/ src/
RUN ./mvnw --batch-mode --no-transfer-progress verify

FROM eclipse-temurin:21.0.13_9-jre-jammy
WORKDIR /app
RUN groupadd --system swiftdrop && useradd --system --gid swiftdrop swiftdrop
COPY --from=build /workspace/target/swiftdrop-backend.jar app.jar
USER swiftdrop
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
