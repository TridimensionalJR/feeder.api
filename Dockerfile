# ==============================================================================
# Build Stage
# ==============================================================================
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /build

# Copy Maven wrapper and POM to leverage Docker layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Ensure wrapper script is executable
RUN chmod +x mvnw

# Pre-fetch project dependencies offline
RUN ./mvnw dependency:go-offline -B

# Copy application source code
COPY src/ src/

# Build Spring Boot executable JAR without running tests
RUN ./mvnw clean package -DskipTests -B

# ==============================================================================
# Runtime Stage
# ==============================================================================
FROM eclipse-temurin:25-jre AS runner

WORKDIR /app

# Create a non-privileged user and group for security, and initialize logs directory
RUN groupadd -r appgroup && useradd -r -g appgroup -s /sbin/nologin -d /app appuser \
    && mkdir -p /app/logs && chown -R appuser:appgroup /app

# Copy executable jar from builder stage
COPY --from=builder --chown=appuser:appgroup /build/target/*.jar /app/app.jar

USER appuser

EXPOSE 8080

# Configure JVM flags optimized for container environments (respect cgroup memory limits)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
