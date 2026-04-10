# =============================================
# E-Commerce Backend - Spring Boot Multi-stage Build (Layered JAR)
# AISDLC v0.09 - Stage 8 Development Preparation
# =============================================

# Stage 1: Build with Gradle
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy Gradle wrapper and configuration
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts gradlew ./
RUN chmod +x gradlew

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src src

# Build the application
RUN ./gradlew bootJar --no-daemon

# Stage 2: Extract Layers
FROM eclipse-temurin:21-jdk-alpine AS extractor
WORKDIR /app

# Extract layered JAR
COPY --from=builder /app/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 3: Production Runtime
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Create non-root user for security
RUN addgroup --system --gid 1001 spring
RUN adduser --system --uid 1001 appuser

# Copy extracted layers
COPY --from=extractor /app/dependencies/ ./
COPY --from=extractor /app/spring-boot-loader/ ./
COPY --from=extractor /app/snapshot-dependencies/ ./
COPY --from=extractor /app/application/ ./

# Set ownership
RUN chown -R appuser:spring /app

USER appuser

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# Launch application
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
