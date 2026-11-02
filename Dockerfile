# One image for the whole app (Entscheidung 21): Spring Boot serves the built frontend.
# Tests are not run here; the CI runs them before.

FROM node:24.11.1-alpine AS frontend
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:21.0.9_10-jdk-alpine AS backend
WORKDIR /build
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY backend/src src
COPY --from=frontend /build/dist src/main/resources/static
RUN ./mvnw -B -q package -DskipTests && cp target/budget-*.jar app.jar

FROM eclipse-temurin:21.0.9_10-jre-alpine
RUN addgroup -S budget && adduser -S budget -G budget
WORKDIR /app
COPY --from=backend /build/app.jar app.jar
USER budget
ENV SPRING_PROFILES_ACTIVE=postgres
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
