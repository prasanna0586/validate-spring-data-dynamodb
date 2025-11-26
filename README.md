# Spring Data DynamoDB Reference Application

A reference implementation demonstrating how to use the [spring-data-dynamodb](https://github.com/prasanna0586/spring-data-dynamodb) library with AWS SDK v2. This application validates migration patterns and showcases advanced DynamoDB querying capabilities with Spring Data.

## Prerequisites

| Requirement                 | Version | Download                                                                 |
|-----------------------------|---------|--------------------------------------------------------------------------|
| Java                        | 21+     | [Liberica JDK 21](https://bell-sw.com/pages/downloads/#jdk-21-lts)       |
| Maven                       | 3.8+    | [Apache Maven](https://maven.apache.org/download.cgi)                    |
| Docker                      | Latest  | [Docker Desktop](https://www.docker.com/products/docker-desktop/)        |
| GraalVM (for native builds) | 21+     | [Liberica NIK 21](https://bell-sw.com/pages/downloads/native-image-kit/) |

## Quick Start

```bash
# 1. Start DynamoDB Local
docker compose up -d

# 2. Run tests
mvn clean verify

# 3. Run the application
mvn clean spring-boot:run

# 4. Stop DynamoDB Local
docker compose down
```

## About the spring-data-dynamodb Library

This reference application uses the [spring-data-dynamodb](https://github.com/prasanna0586/spring-data-dynamodb) library, which provides Spring Data repository support for Amazon DynamoDB using AWS SDK v2.

**Key Features:**
- Spring Data repository abstraction for DynamoDB
- Support for AWS SDK v2 Enhanced Client
- Query derivation from method names
- Custom repository implementations
- GSI (Global Secondary Index) query support

**Documentation:**
- [Library Repository](https://github.com/prasanna0586/spring-data-dynamodb)
- [Migration Guide (SDK v1 to v2)](https://github.com/prasanna0586/spring-data-dynamodb/blob/v7.0.0/MIGRATION_GUIDE.md)
- [User Guide](https://prasanna0586.github.io/spring-data-dynamodb/)

## What This Reference Application Demonstrates

- **Advanced DynamoDB Querying**: Multiple GSI configurations with various query patterns
- **Custom Repository Implementations**: Low-level SDK v2 API usage alongside Spring Data
- **Parallel GSI Queries**: Efficient `IN` clause handling with parallel execution
- **Optimistic Locking**: Version-based concurrency control with `@DynamoDbVersionAttribute`
- **Custom Type Converters**: `Instant` to ISO-8601 string conversion
- **GraalVM Native Image**: Full native compilation support with reflection configuration

## Project Structure

```
src/main/java/org/example/dynamodb/
├── DynamoDbApplication.java           # Spring Boot entry point
├── config/
│   └── DynamoDbConfig.java            # DynamoDB client & table resolver
├── model/
│   └── DocumentMetadata.java          # Domain entity with GSI annotations
├── repository/
│   ├── DocumentMetadataRepository.java        # Spring Data repository
│   ├── DocumentMetadataRepositoryCustom.java  # Custom query interface
│   └── DocumentMetadataRepositoryImpl.java    # Custom implementation (SDK v2)
├── service/
│   └── DocumentMetadataService.java   # Business logic layer
├── controller/
│   └── DocumentMetadataController.java # REST API
├── converter/
│   └── InstantConverter.java          # Custom Instant converter
└── exception/
    └── OptimisticLockingException.java

src/main/resources/
├── application.properties             # Application configuration
└── META-INF/native-image/
    └── reflect-config.json            # GraalVM reflection hints

src/test/java/org/example/dynamodb/
├── repository/
│   ├── DocumentMetadataRepositoryIntegrationTest.java  # JVM integration tests
│   └── DocumentMetadataRepositoryNativeTest.java       # Native image tests
├── service/
│   ├── DocumentMetadataServiceTest.java                # Unit tests
│   ├── DocumentMetadataServiceIntegrationTest.java     # JVM integration tests
│   └── DocumentMetadataServiceNativeTest.java          # Native image tests
└── converter/
    └── InstantConverterTest.java      # Converter unit tests
```

## Core Entity

**Table**: `DocumentMetadata`

| Attribute | Type | Key |
|-----------|------|-----|
| `uniqueDocumentId` | String | Partition Key |
| `memberId` | Integer | GSI Partition Key |
| `documentCategory` | Integer | GSI Sort Key |
| `documentSubCategory` | Integer | GSI Sort Key |
| `createdAt` | Instant | GSI Sort Key |
| `version` | Long | Optimistic Lock |

**Global Secondary Indexes:**
- `memberId-documentCategory-index` (Hash: `memberId`, Range: `documentCategory`)
- `memberId-documentSubCategory-index` (Hash: `memberId`, Range: `documentSubCategory`)
- `memberId-createdAt-index` (Hash: `memberId`, Range: `createdAt`)

**SDK v2 Annotations:**
```java
@DynamoDbBean
public class DocumentMetadata {
    @DynamoDbPartitionKey
    @DynamoDbAttribute("uniqueDocumentId")
    public String getUniqueDocumentId() { ... }

    @DynamoDbSecondaryPartitionKey(indexNames = {"memberId-documentCategory-index", ...})
    @DynamoDbAttribute("memberId")
    public Integer getMemberId() { ... }

    @DynamoDbSecondarySortKey(indexNames = "memberId-documentCategory-index")
    @DynamoDbAttribute("documentCategory")
    public Integer getDocumentCategory() { ... }

    @DynamoDbVersionAttribute
    @DynamoDbAttribute("version")
    public Long getVersion() { ... }
}
```

## Query Capabilities

### Efficient Queries (Use GSIs)

| Method                                                         | Description                                 |
|----------------------------------------------------------------|---------------------------------------------|
| `findByUniqueDocumentId(id)`                                   | Primary key lookup                          |
| `findByMemberId(memberId, pageable)`                           | GSI query (hash key only)                   |
| `findByMemberIdAndCreatedAtBetween(...)`                       | GSI range query                             |
| `findByMemberIdAndCreatedAtBetweenAndNotesContaining(...)`     | GSI range query + non-key filter            |
| `findByMemberIdAndDocumentCategoryIn(...)`                     | Parallel GSI queries (one per category)     |
| `findByMemberIdAndDocumentSubCategoryIn(...)`                  | Parallel GSI queries (one per sub-category) |
| `findByMemberIdAndDocumentCategoryAndDocumentSubCategory(...)` | GSI query + non-key filter                  |
| `findByMemberIdAndCreatedByAndUpdatedAtAfter(...)`             | GSI query + multiple non-key filters        |
| `findByMemberIdAndUpdatedBy(...)`                              | GSI query + filter with pagination          |

### Less Efficient (Full Table Scan - Requires @EnableScan)

| Method                                          | Why Scan is Required                              |
|-------------------------------------------------|---------------------------------------------------|
| `findByMemberIdAndCreatedBy(...)`               | No GSI exists with `createdBy` as range key       |
| `findByDocumentCategoryAndNotesContaining(...)` | No GSI exists with `documentCategory` as hash key |

> **Warning**: Scans read every item in the table, making them expensive and slow on large datasets. Avoid in production unless the table is small.

## Running the Application

### Running JVM Tests

JVM tests use **Testcontainers** to automatically spin up DynamoDB Local. No manual setup required.

```bash
mvn clean verify
```

**What happens:**
- Testcontainers starts `amazon/dynamodb-local` container
- Tests create tables dynamically
- Container is cleaned up after tests complete

### Running Locally (JVM Mode)

**1. Start DynamoDB Local:**
```bash
docker compose up -d
```

This starts DynamoDB Local on port 18000 and automatically creates the `DocumentMetadata` table with all GSIs.

**2. Run the application:**
```bash
mvn clean spring-boot:run
```

**3. Test the API:**
```bash
# Create a document
curl -X POST http://localhost:8080/api/documents \
    -H "Content-Type: application/json" \
    -d '{
      "uniqueDocumentId": "doc-123",
      "memberId": 1001,
      "documentCategory": 100,
      "documentSubCategory": 200,
      "createdBy": "user1",
      "updatedBy": "user1",
      "notes": "Test document"
    }'

# Get a document
curl http://localhost:8080/api/documents/doc-123
```

### Running Native Tests (GraalVM)

Native tests run against an **externally managed** DynamoDB Local instance (not Testcontainers).

**1. Install Liberica NIK (GraalVM):**

Download and install [Liberica NIK 21](https://bell-sw.com/pages/downloads/native-image-kit/).

```bash
# Set JAVA_HOME to Liberica NIK
export JAVA_HOME=/path/to/liberica-nik-21
export PATH=$JAVA_HOME/bin:$PATH

# Verify installation
java -version
native-image --version
```

**2. Start DynamoDB Local:**
```bash
docker compose up -d
```

**3. Run native tests:**
```bash
mvn clean verify -PnativeTest
```

**4. Cleanup:**
```bash
docker compose down
```

### Building & Running Native Image

**1. Build the native image:**
```bash
mvn -Pnative clean spring-boot:build-image
```

**2. Run with Docker Compose network:**
```bash
# Ensure DynamoDB Local is running
docker compose up -d

# Run native image
docker run --rm -p 8080:8080 \
    --network validate-spring-data-dynamodb_dynamodb-net \
    -e DYNAMODB_ENDPOINT=http://dynamodb:8000 \
    docker.io/library/validate-spring-data-dynamodb:1.0-0
```

## GraalVM Native Image Compatibility

The spring-data-dynamodb library supports GraalVM native image compilation. This reference application includes the necessary configuration.

### Reflection Configuration

DynamoDB Enhanced Client uses reflection to map entities. For native images, you must register entity classes in `reflect-config.json`:

**Location:** `src/main/resources/META-INF/native-image/reflect-config.json`

```json
[
  {
    "name": "org.example.dynamodb.model.DocumentMetadata",
    "allDeclaredConstructors": true,
    "allPublicConstructors": true,
    "allDeclaredMethods": true,
    "allPublicMethods": true,
    "allDeclaredFields": true,
    "allPublicFields": true,
    "unsafeAllocated": true
  }
]
```

### Adding New Entities

When adding new DynamoDB entity classes:

1. Add the class to `reflect-config.json` with all reflection flags enabled
2. Ensure the entity uses `@DynamoDbBean` annotation
3. Test with native image build to verify configuration

### Test Separation

- **JVM Tests** (`@DisabledInNativeImage`): Use Testcontainers for dynamic container management
- **Native Tests** (`@EnabledInNativeImage`): Require external DynamoDB instance, use `StaticTableSchema` for reflection-safe table creation

For more information, see:
- [GraalVM Native Image Documentation](https://www.graalvm.org/latest/reference-manual/native-image/)
- [Spring Boot Native Image Support](https://docs.spring.io/spring-boot/docs/current/reference/html/native-image.html)
- [Liberica NIK Documentation](https://bell-sw.com/liberica-native-image-kit/)

## Configuration

### Application Properties

```properties
# DynamoDB connection
aws.dynamodb.endpoint=http://localhost:18000  # Override with DYNAMODB_ENDPOINT env var
aws.dynamodb.region=us-east-1
aws.dynamodb.accessKey=dummy
aws.dynamodb.secretKey=dummy

# Table prefix (empty for no prefix)
app.environment.prefix=local
```

### Table Naming

The `app.environment.prefix` property controls table naming:
- Empty prefix: `DocumentMetadata`
- With prefix: `{prefix}_DocumentMetadata` (e.g., `local_DocumentMetadata`)

## SDK v1 to v2 Migration Reference

For projects migrating from AWS SDK v1, here's a quick reference:

| SDK v1 | SDK v2 |
|--------|--------|
| `AmazonDynamoDB` | `DynamoDbClient` |
| `DynamoDBMapper` | `DynamoDbEnhancedClient` |
| `DynamoDBMapperConfig` | `TableNameResolver` bean |
| `@DynamoDBTable` | `@DynamoDbBean` |
| `@DynamoDBHashKey` | `@DynamoDbPartitionKey` |
| `@DynamoDBRangeKey` | `@DynamoDbSortKey` |
| `@DynamoDBIndexHashKey` | `@DynamoDbSecondaryPartitionKey` |
| `@DynamoDBIndexRangeKey` | `@DynamoDbSecondarySortKey` |
| `@DynamoDBVersionAttribute` | `@DynamoDbVersionAttribute` |
| `DynamoDBQueryExpression` | `QueryEnhancedRequest` |
| `DynamoDBScanExpression` | `ScanEnhancedRequest` |
| `DynamoDBTypeConverter` | `AttributeConverter` |

**For the complete migration guide, see:** [Migration Guide](https://github.com/prasanna0586/spring-data-dynamodb/blob/v7.0.0/MIGRATION_GUIDE.md)

## Testing Strategy

| Test Type | Location | Framework | DynamoDB Setup |
|-----------|----------|-----------|----------------|
| Unit Tests | `*Test.java` | JUnit 5 + Mockito | None (mocked) |
| JVM Integration | `*IntegrationTest.java` | Testcontainers | Automatic |
| Native Integration | `*NativeTest.java` | JUnit 5 | External (port 18000) |

**Total Tests:** 75+ (repository, service, and converter tests)

## Resources & Links

- **spring-data-dynamodb Library:** [GitHub Repository](https://github.com/prasanna0586/spring-data-dynamodb)
- **Migration Guide:** [SDK v1 to v2 Migration](https://github.com/prasanna0586/spring-data-dynamodb/blob/v7.0.0/MIGRATION_GUIDE.md)
- **User Guide:** [Documentation](https://prasanna0586.github.io/spring-data-dynamodb/)
- **AWS SDK v2:** [Developer Guide](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html)
- **DynamoDB Local:** [Docker Image](https://hub.docker.com/r/amazon/dynamodb-local)
- **Liberica NIK:** [Downloads](https://bell-sw.com/pages/downloads/native-image-kit/)
- **Spring Boot Native:** [Reference Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/native-image.html)
