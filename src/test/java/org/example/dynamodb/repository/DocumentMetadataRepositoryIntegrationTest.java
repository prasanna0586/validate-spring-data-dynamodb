package org.example.dynamodb.repository;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.*;
import org.example.dynamodb.model.DocumentMetadata;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DocumentMetadataRepositoryIntegrationTest {

    @Container
    static GenericContainer<?> dynamoDbContainer = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:latest"))
            .withExposedPorts(8000)
            .withCommand("-jar DynamoDBLocal.jar -inMemory -sharedDb");

    @DynamicPropertySource
    static void dynamoDbProperties(DynamicPropertyRegistry registry) {
        registry.add("aws.dynamodb.endpoint",
                () -> "http://" + dynamoDbContainer.getHost() + ":" + dynamoDbContainer.getMappedPort(8000));
        registry.add("aws.dynamodb.region", () -> "us-east-1");
        registry.add("aws.dynamodb.accessKey", () -> "dummy");
        registry.add("aws.dynamodb.secretKey", () -> "dummy");
        registry.add("app.environment.prefix", () -> "test");
    }

    @Autowired
    private DocumentMetadataRepository repository;

    @Autowired
    private AmazonDynamoDB amazonDynamoDB;

    private static boolean tableCreated = false;
    private List<String> testDocumentIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        if (!tableCreated) {
            createTableIfNotExists();
            tableCreated = true;
        }
    }

    @AfterEach
    void tearDown() {
        // Clean up documents created in this test
        for (String docId : testDocumentIds) {
            try {
                repository.deleteById(docId);
            } catch (Exception e) {
                // Ignore if document doesn't exist
            }
        }
        testDocumentIds.clear();
    }

    private void createTableIfNotExists() {
        try {
            amazonDynamoDB.describeTable("test-DocumentMetadata");
        } catch (ResourceNotFoundException e) {
            CreateTableRequest createTableRequest = new DynamoDBMapper(amazonDynamoDB)
                    .generateCreateTableRequest(DocumentMetadata.class);
            createTableRequest.setTableName("test-DocumentMetadata");

            createTableRequest.setProvisionedThroughput(new ProvisionedThroughput(5L, 5L));

            if (createTableRequest.getGlobalSecondaryIndexes() != null) {
                for (GlobalSecondaryIndex gsi : createTableRequest.getGlobalSecondaryIndexes()) {
                    gsi.setProvisionedThroughput(new ProvisionedThroughput(5L, 5L));
                    // Set projection type to ALL so all attributes are available in GSI
                    gsi.setProjection(new Projection().withProjectionType(ProjectionType.ALL));
                }
            }

            amazonDynamoDB.createTable(createTableRequest);

            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ==================== PRIMARY KEY QUERIES ====================

    @Test
    @Order(1)
    @DisplayName("Test 1: Create and find by uniqueDocumentId (Primary Key)")
    void testFindByUniqueDocumentId_Success() {
        // Given
        DocumentMetadata doc = createTestDocument("test1-doc1", 1, 101, 201, "user1");
        repository.save(doc);

        // When
        Optional<DocumentMetadata> found = repository.findByUniqueDocumentId("test1-doc1");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getUniqueDocumentId()).isEqualTo("test1-doc1");
        assertThat(found.get().getMemberId()).isEqualTo(1);
        assertThat(found.get().getDocumentCategory()).isEqualTo(101);
        assertThat(found.get().getDocumentSubCategory()).isEqualTo(201);
        assertThat(found.get().getCreatedBy()).isEqualTo("user1");
        assertThat(found.get().getNotes()).isEqualTo("Test document");
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Find by uniqueDocumentId - Not Found")
    void testFindByUniqueDocumentId_NotFound() {
        // When
        Optional<DocumentMetadata> found = repository.findByUniqueDocumentId("non-existent-id");

        // Then
        assertThat(found).isEmpty();
    }

    // ==================== GSI: memberId (Hash Key Only) ====================

    @Test
    @Order(3)
    @DisplayName("Test 3: Find by memberId - Single document")
    void testFindByMemberId_SingleDocument() {
        // Given
        DocumentMetadata doc = createTestDocument("test3-doc1", 3, 101, 201, "user1");
        repository.save(doc);

        // When
        Slice<DocumentMetadata> results = repository.findByMemberId(3, PageRequest.of(0, 10));

        // Then
        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().getFirst().getMemberId()).isEqualTo(3);
        assertThat(results.getContent().getFirst().getUniqueDocumentId()).isEqualTo("test3-doc1");
        assertThat(results.hasNext()).isFalse();
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Find by memberId - Multiple documents")
    void testFindByMemberId_MultipleDocuments() {
        // Given
        repository.save(createTestDocument("test4-doc1", 4, 101, 201, "user1"));
        repository.save(createTestDocument("test4-doc2", 4, 102, 202, "user1"));
        repository.save(createTestDocument("test4-doc3", 4, 103, 203, "user2"));

        // When
        Slice<DocumentMetadata> results = repository.findByMemberId(4, PageRequest.of(0, 10));

        // Then
        assertThat(results.getContent()).hasSize(3);
        assertThat(results.getContent()).allMatch(doc -> doc.getMemberId().equals(4));
        assertThat(results.hasNext()).isFalse();
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: Find by memberId - No results")
    void testFindByMemberId_NoResults() {
        // When
        Slice<DocumentMetadata> results = repository.findByMemberId(999, PageRequest.of(0, 10));

        // Then
        assertThat(results.getContent()).isEmpty();
        assertThat(results.hasNext()).isFalse();
    }

    // ==================== GSI: memberId + createdAt (Hash + Range) ====================

    @Test
    @Order(6)
    @DisplayName("Test 6: Find by memberId and createdAt between - Within range")
    void testFindByMemberIdAndCreatedAtBetween_WithinRange() {
        // Given
        Instant now = Instant.now();
        Instant fiveDaysAgo = now.minus(5, ChronoUnit.DAYS);
        Instant threeDaysAgo = now.minus(3, ChronoUnit.DAYS);
        Instant oneDayAgo = now.minus(1, ChronoUnit.DAYS);

        DocumentMetadata doc1 = createTestDocument("test6-doc1", 6, 101, 201, "user1");
        doc1.setCreatedAt(fiveDaysAgo);
        repository.save(doc1);

        DocumentMetadata doc2 = createTestDocument("test6-doc2", 6, 101, 201, "user1");
        doc2.setCreatedAt(threeDaysAgo);
        repository.save(doc2);

        DocumentMetadata doc3 = createTestDocument("test6-doc3", 6, 102, 202, "user1");
        doc3.setCreatedAt(oneDayAgo);
        repository.save(doc3);

        DocumentMetadata doc4 = createTestDocument("test6-doc4", 6, 103, 203, "user1");
        doc4.setCreatedAt(now);
        repository.save(doc4);

        // When - Query for documents between 4 days ago and 2 days ago
        Instant startDate = now.minus(4, ChronoUnit.DAYS);
        Instant endDate = now.minus(2, ChronoUnit.DAYS);
        List<DocumentMetadata> results = repository.findByMemberIdAndCreatedAtBetween(6, startDate, endDate);

        // Then - Should only get doc2 (created 3 days ago)
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getUniqueDocumentId()).isEqualTo("test6-doc2");
        assertThat(results.getFirst().getCreatedAt()).isBetween(startDate, endDate);
    }

    @Test
    @Order(7)
    @DisplayName("Test 7: Find by memberId and createdAt between - No results in range")
    void testFindByMemberIdAndCreatedAtBetween_NoResults() {
        // Given
        Instant now = Instant.now();
        DocumentMetadata doc = createTestDocument("test7-doc1", 7, 101, 201, "user1");
        doc.setCreatedAt(now);
        repository.save(doc);

        // When - Query for a range in the past
        Instant startDate = now.minus(10, ChronoUnit.DAYS);
        Instant endDate = now.minus(5, ChronoUnit.DAYS);
        List<DocumentMetadata> results = repository.findByMemberIdAndCreatedAtBetween(7, startDate, endDate);

        // Then
        assertThat(results).isEmpty();
    }

    @Test
    @Order(8)
    @DisplayName("Test 8: Find by memberId and createdAt between - Multiple in range")
    void testFindByMemberIdAndCreatedAtBetween_MultipleResults() {
        // Given
        Instant now = Instant.now();
        Instant threeDaysAgo = now.minus(3, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);
        Instant oneDayAgo = now.minus(1, ChronoUnit.DAYS);

        DocumentMetadata doc1 = createTestDocument("test8-doc1", 8, 101, 201, "user1");
        doc1.setCreatedAt(threeDaysAgo);
        repository.save(doc1);

        DocumentMetadata doc2 = createTestDocument("test8-doc2", 8, 102, 202, "user1");
        doc2.setCreatedAt(twoDaysAgo);
        repository.save(doc2);

        DocumentMetadata doc3 = createTestDocument("test8-doc3", 8, 103, 203, "user1");
        doc3.setCreatedAt(oneDayAgo);
        repository.save(doc3);

        // When
        Instant startDate = now.minus(4, ChronoUnit.DAYS);
        Instant endDate = now;
        List<DocumentMetadata> results = repository.findByMemberIdAndCreatedAtBetween(8, startDate, endDate);

        // Then
        assertThat(results).hasSize(3);
        assertThat(results).allMatch(doc -> doc.getMemberId().equals(8));
        assertThat(results).allMatch(doc ->
            doc.getCreatedAt().isAfter(startDate) && doc.getCreatedAt().isBefore(endDate.plus(1, ChronoUnit.SECONDS)));
    }

    // ==================== CUSTOM: memberId + documentCategoryIn ====================

    @Test
    @Order(9)
    @DisplayName("Test 9: Find by memberId and documentCategoryIn - Multiple categories")
    void testFindByMemberIdAndDocumentCategoryIn_MultipleCategories() {
        // Given
        repository.save(createTestDocument("test9-doc1", 9, 101, 201, "user1"));
        repository.save(createTestDocument("test9-doc2", 9, 102, 202, "user1"));
        repository.save(createTestDocument("test9-doc3", 9, 103, 203, "user1"));
        repository.save(createTestDocument("test9-doc4", 9, 104, 201, "user1"));
        repository.save(createTestDocument("test9-doc5", 10, 101, 201, "user1")); // Different member

        // When
        List<DocumentMetadata> results = repository.findByMemberIdAndDocumentCategoryIn(
                9, Arrays.asList(101, 102));

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(doc -> doc.getMemberId().equals(9));
        assertThat(results).allMatch(doc ->
            doc.getDocumentCategory().equals(101) || doc.getDocumentCategory().equals(102));
    }

    @Test
    @Order(10)
    @DisplayName("Test 10: Find by memberId and documentCategoryIn - Single category")
    void testFindByMemberIdAndDocumentCategoryIn_SingleCategory() {
        // Given
        repository.save(createTestDocument("test10-doc1", 10, 101, 201, "user1"));
        repository.save(createTestDocument("test10-doc2", 10, 101, 202, "user1"));
        repository.save(createTestDocument("test10-doc3", 10, 102, 203, "user1"));

        // When
        List<DocumentMetadata> results = repository.findByMemberIdAndDocumentCategoryIn(
                10, List.of(101));

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(doc -> doc.getDocumentCategory().equals(101));
    }

    @Test
    @Order(11)
    @DisplayName("Test 11: Find by memberId and documentCategoryIn - No matches")
    void testFindByMemberIdAndDocumentCategoryIn_NoMatches() {
        // Given
        repository.save(createTestDocument("test11-doc1", 11, 101, 201, "user1"));

        // When
        List<DocumentMetadata> results = repository.findByMemberIdAndDocumentCategoryIn(
                11, Arrays.asList(102, 103));

        // Then
        assertThat(results).isEmpty();
    }

    @Test
    @Order(12)
    @DisplayName("Test 12: Find by memberId and documentCategoryIn - Empty list")
    void testFindByMemberIdAndDocumentCategoryIn_EmptyList() {
        // When
        List<DocumentMetadata> results = repository.findByMemberIdAndDocumentCategoryIn(
                12, new ArrayList<>());

        // Then
        assertThat(results).isEmpty();
    }

    // ==================== CUSTOM: memberId + documentSubCategoryIn ====================

    @Test
    @Order(13)
    @DisplayName("Test 13: Find by memberId and documentSubCategoryIn - Multiple subcategories")
    void testFindByMemberIdAndDocumentSubCategoryIn_MultipleSubCategories() {
        // Given
        repository.save(createTestDocument("test13-doc1", 13, 101, 201, "user1"));
        repository.save(createTestDocument("test13-doc2", 13, 101, 202, "user1"));
        repository.save(createTestDocument("test13-doc3", 13, 101, 203, "user1"));
        repository.save(createTestDocument("test13-doc4", 13, 101, 204, "user1"));

        // When
        List<DocumentMetadata> results = repository.findByMemberIdAndDocumentSubCategoryIn(
                13, Arrays.asList(201, 202, 203));

        // Then
        assertThat(results).hasSize(3);
        assertThat(results).allMatch(doc -> doc.getMemberId().equals(13));
        assertThat(results).allMatch(doc ->
            doc.getDocumentSubCategory().equals(201) ||
            doc.getDocumentSubCategory().equals(202) ||
            doc.getDocumentSubCategory().equals(203));
    }

    @Test
    @Order(14)
    @DisplayName("Test 14: Find by memberId and documentSubCategoryIn - No matches")
    void testFindByMemberIdAndDocumentSubCategoryIn_NoMatches() {
        // Given
        repository.save(createTestDocument("test14-doc1", 14, 101, 201, "user1"));

        // When
        List<DocumentMetadata> results = repository.findByMemberIdAndDocumentSubCategoryIn(
                14, Arrays.asList(202, 203));

        // Then
        assertThat(results).isEmpty();
    }

    // ==================== CRUD OPERATIONS ====================

    @Test
    @Order(15)
    @DisplayName("Test 15: Create document - All fields populated")
    void testCreateDocument_AllFields() {
        // Given
        Instant createdTime = Instant.now();
        DocumentMetadata doc = new DocumentMetadata();
        doc.setUniqueDocumentId("test15-doc1");
        doc.setMemberId(15);
        doc.setDocumentCategory(101);
        doc.setDocumentSubCategory(201);
        doc.setCreatedAt(createdTime);
        doc.setUpdatedAt(createdTime);
        doc.setCreatedBy("user1");
        doc.setUpdatedBy("user1");
        doc.setNotes("Comprehensive test document with all fields");
        testDocumentIds.add("test15-doc1");

        // When
        repository.save(doc);
        Optional<DocumentMetadata> saved = repository.findByUniqueDocumentId("test15-doc1");

        // Then
        assertThat(saved).isPresent();
        DocumentMetadata savedDoc = saved.get();
        assertThat(savedDoc.getUniqueDocumentId()).isEqualTo("test15-doc1");
        assertThat(savedDoc.getMemberId()).isEqualTo(15);
        assertThat(savedDoc.getDocumentCategory()).isEqualTo(101);
        assertThat(savedDoc.getDocumentSubCategory()).isEqualTo(201);
        assertThat(savedDoc.getCreatedBy()).isEqualTo("user1");
        assertThat(savedDoc.getUpdatedBy()).isEqualTo("user1");
        assertThat(savedDoc.getNotes()).isEqualTo("Comprehensive test document with all fields");
    }

    @Test
    @Order(16)
    @DisplayName("Test 16: Update document - Modify multiple fields")
    void testUpdateDocument_MultipleFields() {
        // Given
        DocumentMetadata doc = createTestDocument("test16-doc1", 16, 101, 201, "user1");
        repository.save(doc);

        // When
        doc.setNotes("Updated notes for test");
        doc.setUpdatedAt(Instant.now().plus(1, ChronoUnit.HOURS));
        doc.setUpdatedBy("user2");
        doc.setDocumentSubCategory(202);
        repository.save(doc);

        Optional<DocumentMetadata> updated = repository.findByUniqueDocumentId("test16-doc1");

        // Then
        assertThat(updated).isPresent();
        DocumentMetadata updatedDoc = updated.get();
        assertThat(updatedDoc.getNotes()).isEqualTo("Updated notes for test");
        assertThat(updatedDoc.getUpdatedBy()).isEqualTo("user2");
        assertThat(updatedDoc.getDocumentSubCategory()).isEqualTo(202);
        assertThat(updatedDoc.getCreatedBy()).isEqualTo("user1"); // Should remain unchanged
    }

    @Test
    @Order(17)
    @DisplayName("Test 17: Delete document - Verify deletion")
    void testDeleteDocument_Success() {
        // Given
        DocumentMetadata doc = createTestDocument("test17-doc1", 17, 101, 201, "user1");
        repository.save(doc);

        Optional<DocumentMetadata> beforeDelete = repository.findByUniqueDocumentId("test17-doc1");
        assertThat(beforeDelete).isPresent();

        // When
        repository.deleteById("test17-doc1");
        testDocumentIds.remove("test17-doc1"); // Remove from cleanup list

        // Then
        Optional<DocumentMetadata> afterDelete = repository.findByUniqueDocumentId("test17-doc1");
        assertThat(afterDelete).isEmpty();
    }

    @Test
    @Order(18)
    @DisplayName("Test 18: Batch create and query - 10 documents")
    void testBatchOperations_MultipleDocuments() {
        // Given - Create 10 documents for the same member
        for (int i = 1; i <= 10; i++) {
            DocumentMetadata doc = createTestDocument("test18-doc" + i, 18, 101, 201, "user1");
            doc.setCreatedAt(Instant.now().minus(i, ChronoUnit.DAYS));
            repository.save(doc);
        }

        // When
        Slice<DocumentMetadata> allForMember = repository.findByMemberId(18, PageRequest.of(0, 20));

        // Then
        assertThat(allForMember.getContent()).hasSize(10);
        assertThat(allForMember.getContent()).allMatch(doc -> doc.getMemberId().equals(18));
        assertThat(allForMember.hasNext()).isFalse();
    }

// ==================== NEW QUERY SCENARIOS (ALREADY IMPLEMENTED) ====================

    @Test
    @Order(19)
    @DisplayName("Test 19: Find by memberId and createdBy using @Query with attribute mapping")
    void testFindByMemberIdAndCreatedBy() {
        DocumentMetadata doc1 = createTestDocument("test19-doc1", 19, 101, 201, "user-alpha");
        repository.save(doc1);
        DocumentMetadata doc2 = createTestDocument("test19-doc2", 19, 102, 202, "user-beta");
        repository.save(doc2);
        List<DocumentMetadata> results = repository.findByMemberIdAndCreatedBy(19, "user-alpha");
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getUniqueDocumentId()).isEqualTo("test19-doc1");
        assertThat(results.getFirst().getCreatedBy()).isEqualTo("user-alpha");
    }

    @Test
    @Order(20)
    @DisplayName("Test 20: Find by memberId, createdAt range, and notes containing (case-sensitive)")
    void testFindByMemberIdAndCreatedAtBetweenAndNotesContaining() {
        Instant now = Instant.now();
        DocumentMetadata doc = createTestDocument("test20-doc1", 20, 101, 201, "user-gamma");
        doc.setCreatedAt(now);
        doc.setNotes("URGENT: Medical report");
        repository.save(doc);
        List<DocumentMetadata> results = repository.findByMemberIdAndCreatedAtBetweenAndNotesContaining(
                20,
                now.minusSeconds(3600),
                now.plusSeconds(3600),
                "URGENT"
        );
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getNotes()).contains("URGENT");
    }

    @Test
    @Order(21)
    @DisplayName("Test 21: Find by documentCategory and notes containing (full table scan)")
    void testFindByDocumentCategoryAndNotesContaining() {
        DocumentMetadata doc1 = createTestDocument("test21-doc1", 100, 50, 501, "user-eta");
        doc1.setNotes("CONFIDENTIAL: Legal document");
        repository.save(doc1);
        DocumentMetadata doc2 = createTestDocument("test21-doc2", 101, 50, 502, "user-theta");
        doc2.setNotes("Public record");
        repository.save(doc2);
        List<DocumentMetadata> results = repository.findByDocumentCategoryAndNotesContaining(50, "CONFIDENTIAL");
        assertThat(results)
                .hasSize(1)
                .first()
                .satisfies(doc -> {
                    assertThat(doc.getDocumentCategory()).isEqualTo(50);
                    assertThat(doc.getNotes()).contains("CONFIDENTIAL");
                });
    }

    @Test
    @Order(22)
    @DisplayName("Test 22: Find by memberId, documentCategory, and documentSubCategory")
    void testFindByMemberIdAndDocumentCategoryAndDocumentSubCategory() {
        DocumentMetadata doc = createTestDocument("test22-doc1", 22, 40, 401, "user-delta");
        repository.save(doc);
        List<DocumentMetadata> results = repository.findByMemberIdAndDocumentCategoryAndDocumentSubCategory(
                22, 40, 401
        );
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getUniqueDocumentId()).isEqualTo("test22-doc1");
        assertThat(results.getFirst().getDocumentSubCategory()).isEqualTo(401);
    }

    @Test
    @Order(23)
    @DisplayName("Test 23: Find by memberId, createdBy, and min updatedAt")
    void testFindByMemberIdAndCreatedByAndUpdatedAtAfter() {
        Instant now = Instant.now();
        DocumentMetadata doc1 = createTestDocument("test23-doc1", 23, 101, 201, "user-epsilon");
        doc1.setUpdatedAt(now);
        repository.save(doc1);
        DocumentMetadata doc2 = createTestDocument("test23-doc2", 23, 102, 202, "user-epsilon");
        doc2.setUpdatedAt(now.minus(10, ChronoUnit.MINUTES));
        repository.save(doc2);
        List<DocumentMetadata> results = repository.findByMemberIdAndCreatedByAndUpdatedAtAfter(
                23,
                "user-epsilon",
                now.minus(5, ChronoUnit.MINUTES)
        );
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getUniqueDocumentId()).isEqualTo("test23-doc1");
        assertThat(results.getFirst().getUpdatedAt()).isAfter(now.minus(5, ChronoUnit.MINUTES));
    }

    @Test
    @Order(24)
    @DisplayName("Test 24: Find by memberId, documentSubCategory IN, and min updatedAt (custom parallel query)")
    void testFindByMemberIdAndDocumentSubCategoryInWithUpdatedAtAfter() {
        Instant now = Instant.now();
        DocumentMetadata doc1 = createTestDocument("test24-doc1", 24, 30, 301, "user-zeta");
        doc1.setUpdatedAt(now);
        repository.save(doc1);
        DocumentMetadata doc2 = createTestDocument("test24-doc2", 24, 30, 302, "user-zeta");
        doc2.setUpdatedAt(now.minus(10, ChronoUnit.MINUTES));
        repository.save(doc2);
        List<DocumentMetadata> results = repository.findByMemberIdAndDocumentSubCategoryInWithUpdatedAtAfter(
                24,
                List.of(301, 302),
                now.minus(5, ChronoUnit.MINUTES)
        );
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getDocumentSubCategory()).isEqualTo(301);
    }

    @Test
    @Order(25)
    @DisplayName("Test 25: Find unique document by version")
    void testFindUniqueDocumentByVersion() {
        DocumentMetadata doc = createTestDocument("test25-doc1", 25, 99, 999, "user-omega");
        repository.save(doc);
        Optional<DocumentMetadata> found = repository.findUniqueDocumentByVersion("test25-doc1", 1L);
        assertThat(found).isPresent();
        assertThat(found.get().getVersion()).isEqualTo(1L);
        assertThat(found.get().getUniqueDocumentId()).isEqualTo("test25-doc1");
    }

    @Test
    @Order(26)
    @DisplayName("Test 26: Paginated find by memberId and updatedBy")
    void testFindByMemberIdAndUpdatedBy_Paginated() {
        for (int i = 1; i <= 5; i++) {
            DocumentMetadata doc = createTestDocument("test26-doc" + i, 26, 100, 200, "user-updater");
            doc.setUpdatedBy("system-bot");
            repository.save(doc);
        }
        Page<DocumentMetadata> page1 = repository.findByMemberIdAndUpdatedBy(
                26, "system-bot", PageRequest.of(0, 2)
        );
        assertThat(page1.getContent()).hasSize(2);
        assertThat(page1.getTotalElements()).isEqualTo(5);
        assertThat(page1.getNumber()).isEqualTo(0);
        assertThat(page1.getTotalPages()).isEqualTo(3);
    }

    private DocumentMetadata createTestDocument(String id, Integer memberId, Integer category,
                                                  Integer subCategory, String createdBy) {
        DocumentMetadata doc = new DocumentMetadata();
        doc.setUniqueDocumentId(id);
        doc.setMemberId(memberId);
        doc.setDocumentCategory(category);
        doc.setDocumentSubCategory(subCategory);
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        doc.setCreatedBy(createdBy);
        doc.setUpdatedBy(createdBy);
        doc.setNotes("Test document");

        // Track for cleanup
        testDocumentIds.add(id);

        return doc;
    }
}
