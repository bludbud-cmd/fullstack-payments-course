# Builds the PayDeck API into a container image that runs on any host.
#   docker build -t paydeck-api .
#
# Stage 1 compiles with the full JDK and Maven. Stage 2 copies only the
# finished jar onto a smaller Java runtime, so the image stays small and has
# no build tools in it.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
# Dependencies change less often than code. Downloading them in their own
# step lets Docker reuse that layer when only the code changed.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
# The tests already ran in CI (they need Docker for PostgreSQL, which a
# Docker build does not have), so the image build only packages the jar.
RUN ./mvnw -B -q package -DskipTests && cp target/paydeck-api-*.jar app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app
# Never run a server as root.
RUN groupadd --system paydeck && useradd --system --gid paydeck paydeck
COPY --from=build /src/app.jar app.jar
USER paydeck
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=production \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "app.jar"]
