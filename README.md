# MediSync Pro

A next-generation patient management system that seamlessly integrates healthcare operations through microservices architecture.

## Overview

MediSync Pro is a comprehensive healthcare management platform built with Spring Boot, gRPC, and Apache Kafka. It streamlines patient care coordination, billing operations, and healthcare analytics in a secure, scalable environment.

## System Architecture

The system consists of the following microservices:

- **Patient Service**: Core service for managing patient data
- **Billing Service**: Handles patient billing operations
- **Auth Service**: Manages authentication and authorization
- **Analytics Service**: Processes patient data for analytics
- **API Gateway**: Entry point for all client requests

## Architecture Diagrams (ASCII Art)

### 1. REST Communication (Browser/Client to Server)

```
+-------------------+         HTTP/REST         +-------------------+
|  Frontend Client  |  <--------------------->  |   API Gateway     |
+-------------------+        (JSON, JWT)        +-------------------+
                                                |
                                                v
                                        +-------------------+
                                        |  Patient Service  |
                                        +-------------------+
```

- The browser or client sends HTTP requests (typically JSON, with JWT for auth) to the API Gateway.
- The API Gateway routes requests to the appropriate backend service (e.g., Patient Service).

### 2. gRPC Communication (Service-to-Service)

```
+-------------------+      gRPC (protobuf)      +-------------------+
|  Patient Service  |  <--------------------->  |  Billing Service  |
+-------------------+         (secure)          +-------------------+
```

- The Patient Service acts as a gRPC client, calling the Billing Service for billing operations.
- gRPC uses Protocol Buffers for efficient, strongly-typed, binary communication.

### 3. Kafka Event-Driven (One-to-Many)

```
+-------------------+
|  Patient Service  |
+-------------------+
         |
         |  (Publishes Patient Events)
         v
   +-------------------+
   |   Kafka Topic     |
   |   ("patients")    |
   +-------------------+
      /           \
     v             v
+-------------------+   +-----------------------+
| Analytics Service |   | Notification Service  |
+-------------------+   +-----------------------+
   (Kafka Consumer)         (Kafka Consumer)
```

- The Patient Service publishes events (e.g., patient created/updated) to a Kafka topic.
- Multiple services (Analytics, Notification, etc.) consume these events independently and asynchronously.

## Communication Patterns Explained

### REST (Browser/Client to Server)
- **Purpose:** Handles all external (user-facing) communication.
- **How it works:**
  - The client (browser, mobile app, etc.) sends HTTP requests (GET, POST, etc.) to the API Gateway.
  - The API Gateway authenticates the request (using JWT tokens from the Auth Service) and routes it to the correct microservice.
  - Responses are sent back as JSON.
- **Benefits:**
  - Simple, widely supported, easy to debug.
  - Centralizes security and routing logic.

### gRPC (Service-to-Service)
- **Purpose:** Efficient, strongly-typed communication between backend services.
- **How it works:**
  - The Patient Service (gRPC client) makes a remote procedure call to the Billing Service (gRPC server) using Protocol Buffers.
  - This is used for operations that require immediate, reliable responses (e.g., creating a billing account when a patient is registered).
- **Benefits:**
  - High performance, low latency, and type safety.
  - Supports streaming and bi-directional communication if needed.

### Kafka (One-to-Many, Event-Driven)
- **Purpose:** Decouples services and enables asynchronous, scalable event processing.
- **How it works:**
  - The Patient Service publishes events (e.g., patient created) to a Kafka topic.
  - Any number of services (Analytics, Notification, etc.) can subscribe to the topic and process events independently.
  - Consumers can be added or removed without affecting the producer.
- **Benefits:**
  - Enables real-time analytics, notifications, and other features without tightly coupling services.
  - Scalable and fault-tolerant.

## Technology Stack

- **Backend**: 
  - Java 21
  - Spring Boot 3.4.3
  - Spring Data JPA
  - PostgreSQL 17.2
  - H2 Database (for local development)

- **Communication**:
  - gRPC for synchronous service-to-service communication
  - Apache Kafka for asynchronous event streaming
  - REST APIs for client communication

- **Infrastructure**:
  - Docker for containerization
  - AWS CDK for infrastructure as code
  - AWS ECS for container orchestration
  - AWS MSK for Kafka
  - AWS RDS for PostgreSQL

## Docker & Containerization

All MediSync Pro microservices are fully containerized using Docker. Each service has its own multi-stage Dockerfile, which:
- Builds the service JAR using Maven in a builder stage
- Runs the service in a lightweight OpenJDK 21 container
- Exposes the appropriate port for each service

This approach ensures clean builds, small images, and fast startup. All services are designed to run together on a shared Docker network, enabling REST, gRPC, and Kafka communication between containers.

The system is ready for orchestration with Docker Compose, Kubernetes, or AWS ECS. For local development, you can easily define a `docker-compose.yml` to spin up all services, databases, and Kafka together.

**Benefits:**
- Consistent, reproducible environments
- Easy local development and testing
- Seamless transition to cloud deployment

## Prerequisites

1. **Required Software**:
   - Oracle JDK 21: [Download](https://www.oracle.com/java/technologies/downloads/#jdk21-mac)
   - Docker Desktop: [Download](https://www.docker.com/products/docker-desktop/)
   - IntelliJ IDEA: [Download](https://www.jetbrains.com/idea/download/?section=mac)
   - Maven 3.8+

2. **AWS CLI** (for production deployment):
   ```bash
   aws --version
   aws configure
   ```

## Local Development Setup

1. **Clone the Repository**:
   ```bash
   git clone [repository-url]
   cd patient-management
   ```

2. **Start LocalStack**:
   ```bash
   docker-compose up -d
   ```

3. **Build and Run Services**:
   ```bash
   # Build all services
   mvn clean install

   # Run individual services
   cd patient-service
   mvn spring-boot:run
   ```

## Service Ports

- Patient Service: 4000
- Billing Service: 4001 (REST), 9001 (gRPC)
- Analytics Service: 4002
- Auth Service: 4005
- API Gateway: 4004

## API Documentation

Swagger UI is available for each service:
- Patient Service: http://localhost:4000/v3/api-docs
- Billing Service: http://localhost:4001/v3/api-docs
- Auth Service: http://localhost:4005/v3/api-docs
- API Gateway: http://localhost:4004/v3/api-docs

## Kafka Topics

- `patient`: Patient-related events
  - Event Types:
    - PATIENT_CREATED
    - PATIENT_UPDATED
    - PATIENT_DELETED

## gRPC Services

### Billing Service
- Endpoint: localhost:9001
- Service: BillingService
- Methods:
  - createBillingAccount: Creates a new billing account for a patient

## Database Configuration

### Development (H2)
```properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=admin_viewer
spring.datasource.password=password
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
```

### Production (PostgreSQL)
```properties
spring.datasource.url=jdbc:postgresql://[endpoint]:[port]/[db-name]
spring.datasource.username=admin_user
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
```

## Infrastructure

The system uses AWS CDK for infrastructure management:

```bash
# Deploy to AWS
cd infrastructure
mvn clean install
cdk deploy

# Deploy to LocalStack
cdk deploy --profile localstack
```

## Testing

### Unit Tests
```bash
mvn test
```

### Integration Tests
```bash
cd integration-tests
mvn test
```

## Monitoring and Logging

- CloudWatch Logs for AWS deployment
- Local logging with SLF4J
- Log groups:
  - /ecs/patient-service
  - /ecs/billing-service
  - /ecs/analytics-service
  - /ecs/auth-service
  - /ecs/api-gateway

## Security

- JWT-based authentication
- Role-based access control
- Secure communication with gRPC
- AWS Secrets Manager for sensitive data
- VPC security groups for network isolation

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

[Add your license information here]


