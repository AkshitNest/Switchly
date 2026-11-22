# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

# Copy Maven wrapper and POM first for layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies (cached layer when pom.xml unchanged)
RUN ./mvnw dependency:go-offline -q

# Copy source and build the fat JAR
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre

WORKDIR /app

# Create non-root user for security
RUN addgroup --system --gid 1001 switchly \
 && adduser  --system --uid 1001 --ingroup switchly --no-create-home switchly

COPY --from=build /workspace/target/*.jar app.jar

# Fix ownership
RUN chown switchly:switchly app.jar

USER switchly

EXPOSE 8080

# SWITCHLY_JWT_SECRET must be provided at runtime (no default)
# Example: docker run -e SWITCHLY_JWT_SECRET=$(openssl rand -hex 32) ...
ENTRYPOINT ["java", "-jar", "app.jar"]

