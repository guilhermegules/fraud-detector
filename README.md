# Fraud Detector

A Spring Boot application for fraud detection with load balancing using HAProxy.

## Prerequisites

- Java 25 or higher
- Docker
- Docker Compose

## Platform Compatibility

Images are built for `linux/amd64` architecture. If you're using a Mac with ARM64 processor (Apple Silicon), Docker will emulate the build. The Dockerfile and docker-compose files are already configured with `--platform=linux/amd64` to ensure compatibility.

## Building the Project

### Using Gradle

```bash
./gradlew bootJar
```

The built JAR will be available in `build/libs/`.

### Using Docker

```bash
docker build --platform linux/amd64 -t fraud-detector-rinha .
```

## Docker Hub

### Login to Docker Hub

```bash
docker login
```

### Tag the image

```bash
docker tag fraud-detector your-dockerhub-username/fraud-detector-rinha:latest
```

### Push to Docker Hub

```bash
docker push your-dockerhub-username/fraud-detector-rinha:latest
```

Replace `your-dockerhub-username` with your actual Docker Hub username.

## Running the Application

### Using Gradle

```bash
./gradlew bootRun
```

The application will start on port 8080 by default.

### Using Docker Compose (Production)

```bash
docker compose up --build
```

This will start:
- 2 instances of the fraud detector API (fraud-detector-1, fraud-detector-2)
- HAProxy load balancer on port 9999

### Using Docker Compose (Development)

```bash
docker compose -f docker-compose.dev.yml up --build
```

## Testing

```bash
./gradlew test
```

## Configuration

The application runs on port 8080 by default. The load balancer (HAProxy) exposes port 9999.

## Technologies

- Java 25 (Eclipse Temurin)
- Spring Boot 4.0.6
- Gradle
- HAProxy 3.0
- Docker & Docker Compose
