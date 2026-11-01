# ==========================================
# 1. Build Stage (Cached dependencies)
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy Maven descriptor and download dependencies (Cached layer)
COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw ./
RUN chmod +x mvnw
# Download dependencies offline so source changes don't re-download dependencies
RUN ./mvnw dependency:go-offline -B || true

# Copy source and build jar
COPY src ./src
RUN ./mvnw clean package -DskipTests

# ==========================================
# 2. Lightweight Production Runtime Stage
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Add non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/target/*.jar app.jar

# Render passes dynamic port via PORT environment variable
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE ${PORT}

# Fast start with optimal container memory flags
ENTRYPOINT ["sh", "-c", "java -XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Dserver.port=${PORT} -jar app.jar"]
