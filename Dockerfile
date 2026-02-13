# ---- Stage 1: Build ----
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom first for dependency caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:21-jre-jammy

LABEL maintainer="pdftools"
LABEL description="HTTP service to remove digital signatures from PDF files using iText + AWS S3"

# Update system packages to fix vulnerabilities like libtasn1-6
RUN apt-get update && \
    apt-get upgrade -y && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the Spring Boot fat jar
COPY --from=builder /app/target/pdf-remove-signature-1.0.0.jar /app/pdf-remove-signature.jar

# Temp dir for PDF processing
RUN mkdir -p /tmp/pdf-work

EXPOSE 8090

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD curl -f http://localhost:8090/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/pdf-remove-signature.jar"]
