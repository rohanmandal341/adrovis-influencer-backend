# ==========================================
# 1. Build Stage (Maven on Java 17)
# ==========================================
FROM maven:3.9.9-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Cache Maven dependencies
COPY pom.xml ./
RUN mvn dependency:go-offline -B || true

# Copy application source and build the JAR (test sources are not compiled)
COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true

# ==========================================
# 2. Lightweight Production Runtime Stage (Java 17)
# ==========================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Add non-root user for container security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/target/*.jar app.jar

# Render supplies dynamic port via $PORT
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE ${PORT}

ENTRYPOINT ["sh", "-c", "java -XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Dserver.port=${PORT} -jar app.jar"]