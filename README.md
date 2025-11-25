# DynamoDB Spring Data Integration – DocumentMetadata Service

This project demonstrates advanced querying patterns with **Amazon DynamoDB** using **Spring Data DynamoDB** (SDK v2) and **Testcontainers** for integration testing.

## AWS SDK Version

This project uses **AWS SDK for Java v2** with the DynamoDB Enhanced Client:
- `software.amazon.awssdk:dynamodb-enhanced`
- `spring-data-dynamodb:7.0.0` (SDK v2 compatible)

## Core Entity

- **Table**: `DocumentMetadata`
- **Partition Key**: `uniqueDocumentId` (String)
- **Global Secondary Indexes (GSIs)**:
    - `memberId-documentCategory-index` → Hash: `memberId`, Range: `documentCategory`
    - `memberId-documentSubCategory-index` → Hash: `memberId`, Range: `documentSubCategory`
    - `memberId-createdAt-index` → Hash: `memberId`, Range: `createdAt`
- **Optimistic Locking**: `version` (Long) via `@DynamoDbVersionAttribute`
- **Type Conversion**: `Instant` ↔ ISO-8601 String via custom `AttributeConverter`

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
| Method                                          | Why Scan is Required                                                                    |
|-------------------------------------------------|-----------------------------------------------------------------------------------------|
| `findByMemberIdAndCreatedBy(...)`               | No GSI exists with `createdBy` as range key. Falls back to scan operation.              |
| `findByDocumentCategoryAndNotesContaining(...)` | No GSI exists with `documentCategory` as hash key. Uses explicit `ScanEnhancedRequest`. |

> **Important**: These methods require `@EnableScan` annotation on the repository interface.
>
> **Why scans are needed:**
> - **`findByMemberIdAndCreatedBy`**: `createdBy` is a regular attribute (not indexed). Spring Data cannot perform a Query on `memberId` alone with a filter on `createdBy`, so it falls back to a table scan.
> - **`findByDocumentCategoryAndNotesContaining`**: No GSI has `documentCategory` as the hash key, requiring a full table scan to filter by category and notes content.
>
> Scans read **every item in the table**, making them **expensive and slow** on large datasets.
> **Avoid in production** unless the table is small or used for administrative/debugging purposes.

## Configuration

### Repository Annotation
- `@EnableScan` is explicitly enabled on `DocumentMetadataRepository` to allow scan operations.
- Required for **2 methods**:
  - `findByMemberIdAndCreatedBy` (no GSI for memberId+createdBy combination)
  - `findByDocumentCategoryAndNotesContaining` (no GSI with documentCategory as hash key)

### SDK v2 Entity Annotations
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

### Environment Prefix
- Table names can be prefixed (e.g., `prod_DocumentMetadata`, `dev_DocumentMetadata`)
- Controlled by `app.environment.prefix` property
- Empty prefix uses base table name directly

### Application Properties
```properties
aws.dynamodb.endpoint=http://localhost:8000
aws.dynamodb.region=us-east-1
aws.dynamodb.accessKey=dummy
aws.dynamodb.secretKey=dummy
app.environment.prefix=local
```

## Testing Strategy

- **Framework**: JUnit 5 + Testcontainers
- **DynamoDB**: `amazon/dynamodb-local:latest`
- **Test Count**: 75 tests (26 repository + 13 service unit + 20 service integration + 16 converter)
- **Test Highlights**:
    - Parallel GSI queries with `IN` logic
    - Hybrid queries (GSI key + non-key attribute filters)
    - Version-based document lookup via `@DynamoDbVersionAttribute`
    - Case-sensitive text filtering (`contains(...)`)
    - Pagination over filtered GSI results
    - Full-table scan behavior validation
    - Optimistic locking conflict handling
    - Custom `AttributeConverter` for `Instant` type

## How to Run

### 1. Start DynamoDB Local (optional for manual testing)
```bash
docker run -p 8000:8000 amazon/dynamodb-local
```

### 2. Run Tests
```bash
mvn test
```

### 3. Run Application
```bash
mvn spring-boot:run
```

## SDK v1 to v2 Migration Notes

This project was migrated from AWS SDK v1 to v2. Key differences:

| SDK v1                            | SDK v2                                    |
|-----------------------------------|-------------------------------------------|
| `AmazonDynamoDB`                  | `DynamoDbClient`                          |
| `DynamoDBMapper`                  | `DynamoDbEnhancedClient`                  |
| `DynamoDBMapperConfig`            | `TableNameResolver` bean                  |
| `@DynamoDBTable`                  | `@DynamoDbBean`                           |
| `@DynamoDBHashKey`                | `@DynamoDbPartitionKey`                   |
| `@DynamoDBRangeKey`               | `@DynamoDbSortKey`                        |
| `@DynamoDBIndexHashKey`           | `@DynamoDbSecondaryPartitionKey`          |
| `@DynamoDBIndexRangeKey`          | `@DynamoDbSecondarySortKey`               |
| `@DynamoDBVersionAttribute`       | `@DynamoDbVersionAttribute`               |
| `DynamoDBQueryExpression`         | `QueryEnhancedRequest` / `QueryRequest`   |
| `DynamoDBScanExpression`          | `ScanEnhancedRequest`                     |
| `DynamoDBTypeConverter`           | `AttributeConverter`                      |
| `new AttributeValue().withS(val)` | `AttributeValue.builder().s(val).build()` |

### GSI Queries in SDK v2
For GSI queries in custom repository implementations, use low-level `QueryRequest` with explicit `indexName`:
```java
QueryRequest queryRequest = QueryRequest.builder()
    .tableName(tableName)
    .indexName("memberId-documentCategory-index")
    .keyConditionExpression("memberId = :memberId AND documentCategory = :category")
    .expressionAttributeValues(expressionValues)
    .build();

dynamoDBOperations.query(DocumentMetadata.class, queryRequest);
```
