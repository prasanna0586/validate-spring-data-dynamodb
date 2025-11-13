# DynamoDB Spring Data Integration – DocumentMetadata Service

This project demonstrates advanced querying patterns with **Amazon DynamoDB** using **Spring Data DynamoDB (boostchicken)** and **Testcontainers** for integration testing.

## 🧱 Core Entity

- **Table**: `DocumentMetadata`
- **Partition Key**: `uniqueDocumentId` (String)
- **Global Secondary Indexes (GSIs)**:
    - `memberId-documentCategory-index` → Hash: `memberId`, Range: `documentCategory`
    - `memberId-documentSubCategory-index` → Hash: `memberId`, Range: `documentSubCategory`
    - `memberId-createdAt-index` → Hash: `memberId`, Range: `createdAt`
- **Optimistic Locking**: `version` (Long)
- **Type Conversion**: `Instant` ↔ ISO-8601 String

## 🔍 Query Capabilities

### ✅ Efficient Queries (Use GSIs)
| Method | Description |
|-------|------------|
| `findByUniqueDocumentId(id)` | Primary key lookup |
| `findByMemberId(memberId, pageable)` | GSI query (hash key only) |
| `findByMemberIdAndCreatedAtBetween(...)` | GSI range query |
| `findByMemberIdAndDocumentCategoryIn(...)` | Parallel GSI queries (one per category) |
| `findByMemberIdAndDocumentSubCategoryIn(...)` | Parallel GSI queries (one per sub-category) |
| `findByMemberIdAndDocumentCategoryAndDocumentSubCategory(...)` | GSI query + non-key filter |
| `findByMemberIdAndCreatedBy(...)` | GSI query + non-key filter |
| `findByMemberIdAndCreatedByAndUpdatedAtAfter(...)` | GSI query + multiple non-key filters |
| `findByMemberIdAndUpdatedBy(...)` | GSI query + filter with pagination |

### ⚠️ Less Efficient (Full Table Scan)
| Method | Description |
|-------|------------|
| `findByDocumentCategoryAndNotesContaining(...)` | Full table scan with filter expression |

> ⚠️ **Important**: This method uses **`DynamoDBScanExpression`** and requires `@EnableScan` on the repository.  
> Scans read **every item in the table**, so they are **expensive and slow** on large datasets.  
> **Avoid in production** unless the table is small or used for administrative/debugging purposes.

## ⚙️ Configuration

### Repository Annotation
- `@EnableScan` is explicitly enabled on `DocumentMetadataRepository` to allow scan operations.
- Used **only** for the `findByDocumentCategoryAndNotesContaining` method.

### Profiles
- `local`: Connects to local DynamoDB (`http://localhost:8000`)
- `test`: Used by Testcontainers (auto-configured during integration tests)

### Environment Prefix
- Table names are prefixed (e.g., `local-DocumentMetadata`, `test-DocumentMetadata`)
- Controlled by `app.environment.prefix`

## 🧪 Testing Strategy

- **Framework**: JUnit 5 + Testcontainers
- **DynamoDB**: `amazon/dynamodb-local:latest`
- **Test Highlights**:
    - ✅ Parallel GSI queries with `IN` logic
    - ✅ Hybrid queries (GSI key + non-key attribute filters)
    - ✅ Version-based document lookup via `@DynamoDBVersionAttribute`
    - ✅ Case-sensitive text filtering (`contains(...)`)
    - ✅ Pagination over filtered GSI results
    - ✅ Full-table scan behavior validation (Test 21)
    - ✅ Optimistic locking conflict handling

## 🚀 How to Run

### 1. Start DynamoDB Local (optional for manual testing)
```bash
docker run -p 8000:8000 amazon/dynamodb-local