# syntax=docker/dockerfile:1
# Build context is the repository root: docker build -f docker/Dockerfile -t apipilot .

FROM node:26-alpine AS frontend
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:25-jdk AS backend
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY sample-api sample-api
COPY backend backend
COPY --from=frontend /app/out backend/src/main/resources/static
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -pl backend -am package -DskipTests

FROM grafana/k6:2.4.0 AS k6
FROM eclipse-temurin:25-jre AS jre

# One image holds everything: Postgres (the base), the JRE, k6 and the app.
FROM postgres:18
COPY --from=jre /opt/java/openjdk /opt/java/openjdk
COPY --from=k6 /usr/bin/k6 /usr/local/bin/k6
COPY --from=backend /src/backend/target/apipilot-*.jar /app/apipilot.jar
COPY --chmod=755 docker/entrypoint.sh /app/entrypoint.sh
RUN mkdir /data && chown postgres:postgres /data
ENV PATH=/opt/java/openjdk/bin:$PATH \
    SERVER_ADDRESS=0.0.0.0
VOLUME /data
EXPOSE 8080
USER postgres
ENTRYPOINT ["/app/entrypoint.sh"]
