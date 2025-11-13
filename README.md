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
| `findByMemberIdAndCreatedAtBetweenAndNotesContaining(...)` | GSI range query + non-key filter |
| `findByMemberIdAndDocumentCategoryIn(...)` | Parallel GSI queries (one per category) |
| `findByMemberIdAndDocumentSubCategoryIn(...)` | Parallel GSI queries (one per sub-category) |
| `findByMemberIdAndDocumentCategoryAndDocumentSubCategory(...)` | GSI query + non-key filter |
| `findByMemberIdAndCreatedByAndUpdatedAtAfter(...)` | GSI query + multiple non-key filters |
| `findByMemberIdAndUpdatedBy(...)` | GSI query + filter with pagination |

### ⚠️ Less Efficient (Full Table Scan - Requires @EnableScan)
| Method | Why Scan is Required |
|-------|---------------------|
| `findByMemberIdAndCreatedBy(...)` | No GSI exists with `createdBy` as range key. Available GSIs with `memberId` hash key only have `documentCategory`, `documentSubCategory`, or `createdAt` as range keys. Falls back to scan operation. |
| `findByDocumentCategoryAndNotesContaining(...)` | No GSI exists with `documentCategory` as hash key (it's only a range key in `memberId-documentCategory-index`). Uses explicit `DynamoDBScanExpression`. |

> ⚠️ **Important**: These methods require `@EnableScan` annotation on the repository interface.
>
> **Why scans are needed:**
> - **`findByMemberIdAndCreatedBy`**: `createdBy` is a regular attribute (not indexed). Spring Data cannot perform a Query on `memberId` alone with a filter on `createdBy`, so it falls back to a table scan.
> - **`findByDocumentCategoryAndNotesContaining`**: No GSI has `documentCategory` as the hash key, requiring a full table scan to filter by category and notes content.
>
> Scans read **every item in the table**, making them **expensive and slow** on large datasets.
> **Avoid in production** unless the table is small or used for administrative/debugging purposes.

## ⚙️ Configuration

### Repository Annotation
- `@EnableScan` is explicitly enabled on `DocumentMetadataRepository` to allow scan operations.
- Required for **2 methods**:
  - `findByMemberIdAndCreatedBy` (no GSI for memberId+createdBy combination)
  - `findByDocumentCategoryAndNotesContaining` (no GSI with documentCategory as hash key)

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