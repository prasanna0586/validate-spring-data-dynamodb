package org.example.dynamodb.service;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.*;
import org.example.dynamodb.model.DocumentMetadata;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
@DisplayName("DocumentMetadataService Integration Tests")
class DocumentMetadataServiceIntegrationTest {

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
    }

    @Autowired
    private DocumentMetadataService documentMetadataService;

    @Autowired
    private AmazonDynamoDB amazonDynamoDB;

    @Autowired
    private DynamoDBMapper dynamoDBMapper;

    private static boolean tableCreated = false;
    private static List<String> testDocumentIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        if (!tableCreated) {
            createTable();
            tableCreated = true;
        }
    }

    @AfterAll
    static void cleanup(@Autowired AmazonDynamoDB amazonDynamoDB) {
        // Clean up all test documents
        for (String docId : testDocumentIds) {
            try {
                amazonDynamoDB.deleteItem(new DeleteItemRequest()
                        .withTableName("DocumentMetadata")
                        .withKey(java.util.Collections.singletonMap(
                                "uniqueDocumentId",
                                new AttributeValue(docId))));
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }

        // Delete table
        try {
            amazonDynamoDB.deleteTable("DocumentMetadata");
        } catch (Exception e) {
            // Ignore if table doesn't exist
        }
    }

    private void createTable() {
        CreateTableRequest createTableRequest = new CreateTableRequest()
                .withTableName("DocumentMetadata")
                .withKeySchema(
                        new KeySchemaElement("uniqueDocumentId", KeyType.HASH))
                .withAttributeDefinitions(
                        new AttributeDefinition("uniqueDocumentId", ScalarAttributeType.S),
                        new AttributeDefinition("memberId", ScalarAttributeType.N),
                        new AttributeDefinition("documentCategory", ScalarAttributeType.N),
                        new AttributeDefinition("documentSubCategory", ScalarAttributeType.N),
                        new AttributeDefinition("createdAt", ScalarAttributeType.S))
                .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L))
                .withGlobalSecondaryIndexes(
                        // GSI 1: memberId-documentCategory-index
                        new GlobalSecondaryIndex()
                                .withIndexName("memberId-documentCategory-index")
                                .withKeySchema(
                                        new KeySchemaElement("memberId", KeyType.HASH),
                                        new KeySchemaElement("documentCategory", KeyType.RANGE))
                                .withProjection(new Projection().withProjectionType(ProjectionType.ALL))
                                .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L)),
                        // GSI 2: memberId-documentSubCategory-index
                        new GlobalSecondaryIndex()
                                .withIndexName("memberId-documentSubCategory-index")
                                .withKeySchema(
                                        new KeySchemaElement("memberId", KeyType.HASH),
                                        new KeySchemaElement("documentSubCategory", KeyType.RANGE))
                                .withProjection(new Projection().withProjectionType(ProjectionType.ALL))
                                .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L)),
                        // GSI 3: memberId-createdAt-index
                        new GlobalSecondaryIndex()
                                .withIndexName("memberId-createdAt-index")
                                .withKeySchema(
                                        new KeySchemaElement("memberId", KeyType.HASH),
                                        new KeySchemaElement("createdAt", KeyType.RANGE))
                                .withProjection(new Projection().withProjectionType(ProjectionType.ALL))
                                .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L)));

        amazonDynamoDB.createTable(createTableRequest);

        // Wait for table to be active
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

        testDocumentIds.add(id);
        return doc;
    }

    // ==================== Save and Retrieve Document Tests ====================

    @Test
    @Order(1)
    @DisplayName("Test 1: Save document and retrieve by ID")
    void testSaveAndGetDocumentById() {
        // Given
        DocumentMetadata doc = createTestDocument("service-test1-doc1", 100, 1001, 2001, "serviceUser1");

        // When - Save
        DocumentMetadata savedDoc = documentMetadataService.saveDocument(doc);

        // Then - Verify save
        assertThat(savedDoc).isNotNull();
        assertThat(savedDoc.getUniqueDocumentId()).isEqualTo("service-test1-doc1");

        // When - Retrieve
        Optional<DocumentMetadata> retrieved = documentMetadataService.getDocumentById("service-test1-doc1");

        // Then - Verify retrieve
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getMemberId()).isEqualTo(100);
        assertThat(retrieved.get().getDocumentCategory()).isEqualTo(1001);
        assertThat(retrieved.get().getCreatedBy()).isEqualTo("serviceUser1");
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Get document by ID - Not found")
    void testGetDocumentById_NotFound() {
        // When
        Optional<DocumentMetadata> result = documentMetadataService.getDocumentById("nonexistent-doc");

        // Then
        assertThat(result).isEmpty();
    }

    // ==================== Paginated Query Tests ====================

    @Test
    @Order(3)
    @DisplayName("Test 3: Get documents by memberId with pagination - Single page")
    void testGetDocumentsByMemberId_SinglePage() {
        // Given
        documentMetadataService.saveDocument(createTestDocument("service-test3-doc1", 101, 1001, 2001, "user1"));
        documentMetadataService.saveDocument(createTestDocument("service-test3-doc2", 101, 1002, 2002, "user1"));
        documentMetadataService.saveDocument(createTestDocument("service-test3-doc3", 101, 1003, 2003, "user1"));

        // When
        Slice<DocumentMetadata> result = documentMetadataService.getDocumentsByMemberId(101, PageRequest.of(0, 10));

        // Then
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.getContent()).allMatch(doc -> doc.getMemberId().equals(101));
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Get documents by memberId with pagination - Multiple pages")
    void testGetDocumentsByMemberId_MultiplePages() {
        // Given - Create 5 documents but use page size of 2
        for (int i = 1; i <= 5; i++) {
            documentMetadataService.saveDocument(
                    createTestDocument("service-test4-doc" + i, 102, 1001, 2001, "user1"));
        }

        // When - Get first page
        Slice<DocumentMetadata> firstPage = documentMetadataService.getDocumentsByMemberId(102, PageRequest.of(0, 2));

        // Then - Verify first page
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.hasNext()).isTrue();

        // When - Get next page
        Slice<DocumentMetadata> secondPage = documentMetadataService.getDocumentsByMemberId(102, firstPage.nextPageable());

        // Then - Verify second page
        assertThat(secondPage.getContent()).hasSize(2);
        assertThat(secondPage.hasNext()).isTrue();
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: Get documents by memberId - Empty result")
    void testGetDocumentsByMemberId_EmptyResult() {
        // When
        Slice<DocumentMetadata> result = documentMetadataService.getDocumentsByMemberId(999, PageRequest.of(0, 10));

        // Then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.hasNext()).isFalse();
    }

    // ==================== Date Range Query Tests ====================

    @Test
    @Order(6)
    @DisplayName("Test 6: Get documents by memberId and date range - Within range")
    void testGetDocumentsByMemberIdAndDateRange_WithinRange() {
        // Given
        Instant now = Instant.now();
        DocumentMetadata doc1 = createTestDocument("service-test6-doc1", 103, 1001, 2001, "user1");
        doc1.setCreatedAt(now.minus(2, ChronoUnit.HOURS));
        documentMetadataService.saveDocument(doc1);

        DocumentMetadata doc2 = createTestDocument("service-test6-doc2", 103, 1002, 2002, "user1");
        doc2.setCreatedAt(now.minus(1, ChronoUnit.HOURS));
        documentMetadataService.saveDocument(doc2);

        DocumentMetadata doc3 = createTestDocument("service-test6-doc3", 103, 1003, 2003, "user1");
        doc3.setCreatedAt(now.minus(5, ChronoUnit.HOURS));
        documentMetadataService.saveDocument(doc3);

        // When - Query for docs within last 3 hours
        Instant startDate = now.minus(3, ChronoUnit.HOURS);
        Instant endDate = now.plus(1, ChronoUnit.HOURS);
        List<DocumentMetadata> results = documentMetadataService.getDocumentsByMemberIdAndDateRange(103, startDate, endDate);

        // Then - Should get 2 documents (doc1 and doc2, not doc3)
        assertThat(results).hasSize(2);
        assertThat(results).extracting(DocumentMetadata::getUniqueDocumentId)
                .containsExactlyInAnyOrder("service-test6-doc1", "service-test6-doc2");
    }

    @Test
    @Order(7)
    @DisplayName("Test 7: Get documents by memberId and date range - No results in range")
    void testGetDocumentsByMemberIdAndDateRange_NoResults() {
        // Given - Document created now
        documentMetadataService.saveDocument(createTestDocument("service-test7-doc1", 104, 1001, 2001, "user1"));

        // When - Query for old dates
        Instant startDate = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant endDate = Instant.now().minus(5, ChronoUnit.DAYS);
        List<DocumentMetadata> results = documentMetadataService.getDocumentsByMemberIdAndDateRange(104, startDate, endDate);

        // Then
        assertThat(results).isEmpty();
    }

    // ==================== Category Query Tests (Custom Implementation) ====================

    @Test
    @Order(8)
    @DisplayName("Test 8: Get documents by memberId and categories - Single category")
    void testGetDocumentsByMemberIdAndCategoriesIn_SingleCategory() {
        // Given
        documentMetadataService.saveDocument(createTestDocument("service-test8-doc1", 105, 1001, 2001, "user1"));
        documentMetadataService.saveDocument(createTestDocument("service-test8-doc2", 105, 1002, 2002, "user1"));
        documentMetadataService.saveDocument(createTestDocument("service-test8-doc3", 105, 1003, 2003, "user1"));

        // When
        List<DocumentMetadata> results = documentMetadataService.getDocumentsByMemberIdAndCategoriesIn(
                105, Arrays.asList(1001));

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getDocumentCategory()).isEqualTo(1001);
    }

    @Test
    @Order(9)
    @DisplayName("Test 9: Get documents by memberId and categories - Multiple categories")
    void testGetDocumentsByMemberIdAndCategoriesIn_MultipleCategories() {
        // Given
        documentMetadataService.saveDocument(createTestDocument("service-test9-doc1", 106, 1001, 2001, "user1"));
        documentMetadataService.saveDocument(createTestDocument("service-test9-doc2", 106, 1002, 2002, "user1"));
        documentMetadataService.saveDocument(createTestDocument("service-test9-doc3", 106, 1003, 2003, "user1"));
        documentMetadataService.saveDocument(createTestDocument("service-test9-doc4", 106, 1004, 2004, "user1"));

        // When - Query for categories 1001 and 1003
        List<DocumentMetadata> results = documentMetadataService.getDocumentsByMemberIdAndCategoriesIn(
                106, Arrays.asList(1001, 1003));

        // Then - Should get 2 documents
        assertThat(results).hasSize(2);
        assertThat(results).extracting(DocumentMetadata::getDocumentCategory)
                .containsExactlyInAnyOrder(1001, 1003);
    }

    @Test
    @Order(10)
    @DisplayName("Test 10: Get documents by memberId and categories - Empty result")
    void testGetDocumentsByMemberIdAndCategoriesIn_EmptyResult() {
        // Given
        documentMetadataService.saveDocument(createTestDocument("service-test10-doc1", 107, 1001, 2001, "user1"));

        // When - Query for non-existent category
        List<DocumentMetadata> results = documentMetadataService.getDocumentsByMemberIdAndCategoriesIn(
                107, Arrays.asList(9999));

        // Then
        assertThat(results).isEmpty();
    }

    // ==================== SubCategory Query Tests (Custom Implementation) ====================

    @Test
    @Order(11)
    @DisplayName("Test 11: Get documents by memberId and sub-categories - Single sub-category")
    void testGetDocumentsByMemberIdAndSubCategoriesIn_SingleSubCategory() {
        // Given
        documentMetadataService.saveDocument(createTestDocument("service-test11-doc1", 108, 1001, 2001, "user1"));
        documentMetadataService.saveDocument(createTestDocument("service-test11-doc2", 108, 1002, 2002, "user1"));
        documentMetadataService.saveDocument(createTestDocument("service-test11-doc3", 108, 1003, 2003, "user1"));

        // When
        List<DocumentMetadata> results = documentMetadataService.getDocumentsByMemberIdAndSubCategoriesIn(
                108, Arrays.asList(2001));

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getDocumentSubCategory()).isEqualTo(2001);
    }

    @Test
    @Order(12)
    @DisplayName("Test 12: Get documents by memberId and sub-categories - Multiple sub-categories")
    void testGetDocumentsByMemberIdAndSubCategoriesIn_MultipleSubCategories() {
        // Given
        documentMetadataService.saveDocument(createTestDocument("service-test12-doc1", 109, 1001, 2001, "user1"));
        documentMetadataService.saveDocument(createTestDocument("service-test12-doc2", 109, 1002, 2002, "user1"));
        documentMetadataService.saveDocument(createTestDocument("service-test12-doc3", 109, 1003, 2003, "user1"));
        documentMetadataService.saveDocument(createTestDocument("service-test12-doc4", 109, 1004, 2004, "user1"));

        // When - Query for sub-categories 2002 and 2004
        List<DocumentMetadata> results = documentMetadataService.getDocumentsByMemberIdAndSubCategoriesIn(
                109, Arrays.asList(2002, 2004));

        // Then - Should get 2 documents
        assertThat(results).hasSize(2);
        assertThat(results).extracting(DocumentMetadata::getDocumentSubCategory)
                .containsExactlyInAnyOrder(2002, 2004);
    }

    @Test
    @Order(13)
    @DisplayName("Test 13: Get documents by memberId and sub-categories - Empty result")
    void testGetDocumentsByMemberIdAndSubCategoriesIn_EmptyResult() {
        // Given
        documentMetadataService.saveDocument(createTestDocument("service-test13-doc1", 110, 1001, 2001, "user1"));

        // When - Query for non-existent sub-category
        List<DocumentMetadata> results = documentMetadataService.getDocumentsByMemberIdAndSubCategoriesIn(
                110, Arrays.asList(9999));

        // Then
        assertThat(results).isEmpty();
    }

    // ==================== Delete Document Tests ====================

    @Test
    @Order(14)
    @DisplayName("Test 14: Delete document")
    void testDeleteDocument() {
        // Given
        DocumentMetadata doc = createTestDocument("service-test14-doc1", 111, 1001, 2001, "user1");
        documentMetadataService.saveDocument(doc);

        // Verify document exists
        Optional<DocumentMetadata> beforeDelete = documentMetadataService.getDocumentById("service-test14-doc1");
        assertThat(beforeDelete).isPresent();

        // When
        documentMetadataService.deleteDocument("service-test14-doc1");
        testDocumentIds.remove("service-test14-doc1"); // Remove from cleanup list

        // Then
        Optional<DocumentMetadata> afterDelete = documentMetadataService.getDocumentById("service-test14-doc1");
        assertThat(afterDelete).isEmpty();
    }

    // ==================== Parallel Query Performance Test ====================

    @Test
    @Order(15)
    @DisplayName("Test 15: Parallel queries for multiple categories - Performance test")
    void testGetDocumentsByMemberIdAndCategoriesIn_ParallelPerformance() {
        // Given - Create documents across 5 different categories
        for (int category = 1; category <= 5; category++) {
            for (int i = 1; i <= 3; i++) {
                documentMetadataService.saveDocument(
                        createTestDocument("service-test15-cat" + category + "-doc" + i,
                                112, 1000 + category, 2001, "user1"));
            }
        }

        // When - Query for all 5 categories (should run in parallel)
        long startTime = System.currentTimeMillis();
        List<DocumentMetadata> results = documentMetadataService.getDocumentsByMemberIdAndCategoriesIn(
                112, Arrays.asList(1001, 1002, 1003, 1004, 1005));
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(results).hasSize(15); // 5 categories × 3 documents each
        assertThat(duration).isLessThan(5000); // Should complete reasonably fast with parallel execution

        // Verify all categories are present
        assertThat(results).extracting(DocumentMetadata::getDocumentCategory)
                .containsExactlyInAnyOrder(
                        1001, 1001, 1001,
                        1002, 1002, 1002,
                        1003, 1003, 1003,
                        1004, 1004, 1004,
                        1005, 1005, 1005);
    }

    // ==================== Update Document Test ====================

    @Test
    @Order(16)
    @DisplayName("Test 16: Update document")
    void testUpdateDocument() {
        // Given - Create and save document
        DocumentMetadata doc = createTestDocument("service-test16-doc1", 113, 1001, 2001, "user1");
        documentMetadataService.saveDocument(doc);

        // When - Update the document
        doc.setNotes("Updated notes");
        doc.setUpdatedBy("user2");
        doc.setUpdatedAt(Instant.now());
        DocumentMetadata updatedDoc = documentMetadataService.saveDocument(doc);

        // Then - Verify update
        assertThat(updatedDoc.getNotes()).isEqualTo("Updated notes");
        assertThat(updatedDoc.getUpdatedBy()).isEqualTo("user2");

        // Retrieve and verify
        Optional<DocumentMetadata> retrieved = documentMetadataService.getDocumentById("service-test16-doc1");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getNotes()).isEqualTo("Updated notes");
        assertThat(retrieved.get().getUpdatedBy()).isEqualTo("user2");
    }
}
