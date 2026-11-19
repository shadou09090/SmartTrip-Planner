FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 1000 render-secrets \
    && useradd --uid 10001 --no-create-home --shell /usr/sbin/nologin --gid 1000 smarttrip
WORKDIR /app
COPY --from=build /workspace/target/Back_end_proyecto-0.0.1-SNAPSHOT.jar /app/app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod 0555 /app/docker-entrypoint.sh && chown -R 10001:1000 /app
USER 10001:1000
EXPOSE 8080
ENTRYPOINT ["/app/docker-entrypoint.sh"]
