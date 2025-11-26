package org.example.dynamodb.service;

import org.example.dynamodb.exception.OptimisticLockingException;
import org.example.dynamodb.model.DocumentMetadata;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledInNativeImage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags;
import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticTableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.EnhancedGlobalSecondaryIndex;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Native image integration tests for DocumentMetadataService.
 * These tests run only in GraalVM native image mode.
 *
 * Before running native tests, start DynamoDB Local:
 *   docker run -d -p 18000:8000 amazon/dynamodb-local -jar DynamoDBLocal.jar -inMemory
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("DocumentMetadataService Native Tests")
@EnabledInNativeImage
class DocumentMetadataServiceNativeTest {

    private static final String TABLE_NAME = "DocumentMetadata";
    private static final String TEST_ID_PREFIX = "native-svc-";

    private static boolean tableCreated = false;

    @Value("${aws.dynamodb.endpoint}")
    private String dynamoDbEndpoint;

    @Autowired
    private DocumentMetadataService documentMetadataService;

    private static final List<String> testDocumentIds = new ArrayList<>();

    @BeforeAll
    void setupTable() {
        if (!tableCreated) {
            try {
                createTable(dynamoDbEndpoint);
            } catch (Exception e) {
                // Table may already exist - this is expected
                if (!e.getMessage().contains("preexisting table") && !e.getMessage().contains("ResourceInUseException")) {
                    throw e;
                }
            }
            tableCreated = true;
        }
    }

    @AfterEach
    void tearDown() {
        for (String docId : testDocumentIds) {
            try {
                documentMetadataService.deleteDocument(docId);
            } catch (Exception e) {
                // Ignore if document doesn't exist
            }
        }
        testDocumentIds.clear();
    }

    private void createTable(String endpoint) {
        DynamoDbClient client = DynamoDbClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("dummy", "dummy")))
                .build();

        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(client)
                .build();

        var tableSchema = StaticTableSchema.builder(DocumentMetadata.class)
                .newItemSupplier(DocumentMetadata::new)
                .addAttribute(String.class, a -> a.name("uniqueDocumentId")
                        .getter(DocumentMetadata::getUniqueDocumentId)
                        .setter(DocumentMetadata::setUniqueDocumentId)
                        .tags(StaticAttributeTags.primaryPartitionKey()))
                .addAttribute(Integer.class, a -> a.name("memberId")
                        .getter(DocumentMetadata::getMemberId)
                        .setter(DocumentMetadata::setMemberId)
                        .tags(StaticAttributeTags.secondaryPartitionKey("memberId-createdAt-index"),
                              StaticAttributeTags.secondaryPartitionKey("memberId-documentCategory-index"),
                              StaticAttributeTags.secondaryPartitionKey("memberId-documentSubCategory-index")))
                .addAttribute(Integer.class, a -> a.name("documentCategory")
                        .getter(DocumentMetadata::getDocumentCategory)
                        .setter(DocumentMetadata::setDocumentCategory)
                        .tags(StaticAttributeTags.secondarySortKey("memberId-documentCategory-index")))
                .addAttribute(Integer.class, a -> a.name("documentSubCategory")
                        .getter(DocumentMetadata::getDocumentSubCategory)
                        .setter(DocumentMetadata::setDocumentSubCategory)
                        .tags(StaticAttributeTags.secondarySortKey("memberId-documentSubCategory-index")))
                .addAttribute(Instant.class, a -> a.name("createdAt")
                        .getter(DocumentMetadata::getCreatedAt)
                        .setter(DocumentMetadata::setCreatedAt)
                        .tags(StaticAttributeTags.secondarySortKey("memberId-createdAt-index")))
                .addAttribute(Instant.class, a -> a.name("updatedAt")
                        .getter(DocumentMetadata::getUpdatedAt)
                        .setter(DocumentMetadata::setUpdatedAt))
                .addAttribute(String.class, a -> a.name("createdBy")
                        .getter(DocumentMetadata::getCreatedBy)
                        .setter(DocumentMetadata::setCreatedBy))
                .addAttribute(String.class, a -> a.name("updatedBy")
                        .getter(DocumentMetadata::getUpdatedBy)
                        .setter(DocumentMetadata::setUpdatedBy))
                .addAttribute(String.class, a -> a.name("notes")
                        .getter(DocumentMetadata::getNotes)
                        .setter(DocumentMetadata::setNotes))
                .addAttribute(Long.class, a -> a.name("version")
                        .getter(DocumentMetadata::getVersion)
                        .setter(DocumentMetadata::setVersion))
                .build();

        ProvisionedThroughput throughput = ProvisionedThroughput.builder()
                .readCapacityUnits(5L)
                .writeCapacityUnits(5L)
                .build();

        enhancedClient.table(TABLE_NAME, tableSchema).createTable(builder -> builder
                .provisionedThroughput(throughput)
                .globalSecondaryIndices(
                        EnhancedGlobalSecondaryIndex.builder()
                                .indexName("memberId-createdAt-index")
                                .projection(p -> p.projectionType(ProjectionType.ALL))
                                .provisionedThroughput(throughput)
                                .build(),
                        EnhancedGlobalSecondaryIndex.builder()
                                .indexName("memberId-documentCategory-index")
                                .projection(p -> p.projectionType(ProjectionType.ALL))
                                .provisionedThroughput(throughput)
                                .build(),
                        EnhancedGlobalSecondaryIndex.builder()
                                .indexName("memberId-documentSubCategory-index")
                                .projection(p -> p.projectionType(ProjectionType.ALL))
                                .provisionedThroughput(throughput)
                                .build()
                ));

        try {
            Thread.sleep(2000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        client.close();
    }

    // ==================== BASIC CRUD TESTS ====================

    @Test
    @Order(1)
    @DisplayName("Native Test 1: Save and retrieve document by ID")
    void testSaveAndGetDocumentById() {
        DocumentMetadata doc = createTestDocument("n-svc-test1-doc1", 2001, 1001, 2001, "nativeUser1");

        DocumentMetadata savedDoc = documentMetadataService.saveDocument(doc);

        assertThat(savedDoc).isNotNull();
        assertThat(savedDoc.getUniqueDocumentId()).isEqualTo(TEST_ID_PREFIX + "n-svc-test1-doc1");

        Optional<DocumentMetadata> retrieved = documentMetadataService.getDocumentById(TEST_ID_PREFIX + "n-svc-test1-doc1");

        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getMemberId()).isEqualTo(2001);
        assertThat(retrieved.get().getCreatedBy()).isEqualTo("nativeUser1");
    }

    @Test
    @Order(2)
    @DisplayName("Native Test 2: Get documents by memberId with pagination")
    void testGetDocumentsByMemberId() {
        documentMetadataService.saveDocument(createTestDocument("n-svc-test2-doc1", 2002, 1001, 2001, "user1"));
        documentMetadataService.saveDocument(createTestDocument("n-svc-test2-doc2", 2002, 1002, 2002, "user1"));
        documentMetadataService.saveDocument(createTestDocument("n-svc-test2-doc3", 2002, 1003, 2003, "user1"));

        Slice<DocumentMetadata> result = documentMetadataService.getDocumentsByMemberId(2002, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent()).allMatch(doc -> doc.getMemberId().equals(2002));
    }

    @Test
    @Order(3)
    @DisplayName("Native Test 3: Get documents by memberId and date range")
    void testGetDocumentsByMemberIdAndDateRange() {
        Instant now = Instant.now();

        DocumentMetadata doc1 = createTestDocument("n-svc-test3-doc1", 2003, 1001, 2001, "user1");
        doc1.setCreatedAt(now.minus(2, ChronoUnit.HOURS));
        documentMetadataService.saveDocument(doc1);

        DocumentMetadata doc2 = createTestDocument("n-svc-test3-doc2", 2003, 1002, 2002, "user1");
        doc2.setCreatedAt(now.minus(1, ChronoUnit.HOURS));
        documentMetadataService.saveDocument(doc2);

        DocumentMetadata doc3 = createTestDocument("n-svc-test3-doc3", 2003, 1003, 2003, "user1");
        doc3.setCreatedAt(now.minus(5, ChronoUnit.HOURS));
        documentMetadataService.saveDocument(doc3);

        Instant startDate = now.minus(3, ChronoUnit.HOURS);
        Instant endDate = now.plus(1, ChronoUnit.HOURS);
        List<DocumentMetadata> results = documentMetadataService.getDocumentsByMemberIdAndDateRange(2003, startDate, endDate);

        assertThat(results).hasSize(2);
    }

    @Test
    @Order(4)
    @DisplayName("Native Test 4: Get documents by memberId and categories")
    void testGetDocumentsByMemberIdAndCategoriesIn() {
        documentMetadataService.saveDocument(createTestDocument("n-svc-test4-doc1", 2004, 1001, 2001, "user1"));
        documentMetadataService.saveDocument(createTestDocument("n-svc-test4-doc2", 2004, 1002, 2002, "user1"));
        documentMetadataService.saveDocument(createTestDocument("n-svc-test4-doc3", 2004, 1003, 2003, "user1"));

        List<DocumentMetadata> results = documentMetadataService.getDocumentsByMemberIdAndCategoriesIn(
                2004, Arrays.asList(1001, 1003));

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(doc ->
            doc.getDocumentCategory().equals(1001) || doc.getDocumentCategory().equals(1003));
    }

    @Test
    @Order(5)
    @DisplayName("Native Test 5: Get documents by memberId and subcategories")
    void testGetDocumentsByMemberIdAndSubCategoriesIn() {
        documentMetadataService.saveDocument(createTestDocument("n-svc-test5-doc1", 2005, 1001, 2001, "user1"));
        documentMetadataService.saveDocument(createTestDocument("n-svc-test5-doc2", 2005, 1002, 2002, "user1"));
        documentMetadataService.saveDocument(createTestDocument("n-svc-test5-doc3", 2005, 1003, 2003, "user1"));

        List<DocumentMetadata> results = documentMetadataService.getDocumentsByMemberIdAndSubCategoriesIn(
                2005, Arrays.asList(2002, 2003));

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(doc ->
            doc.getDocumentSubCategory().equals(2002) || doc.getDocumentSubCategory().equals(2003));
    }

    @Test
    @Order(6)
    @DisplayName("Native Test 6: Update document")
    void testUpdateDocument() {
        DocumentMetadata doc = createTestDocument("n-svc-test6-doc1", 2006, 1001, 2001, "user1");
        DocumentMetadata savedDoc = documentMetadataService.saveDocument(doc);

        savedDoc.setNotes("Updated by native test");
        savedDoc.setUpdatedBy("nativeUser2");
        documentMetadataService.saveDocument(savedDoc);

        Optional<DocumentMetadata> updated = documentMetadataService.getDocumentById(TEST_ID_PREFIX + "n-svc-test6-doc1");

        assertThat(updated).isPresent();
        assertThat(updated.get().getNotes()).isEqualTo("Updated by native test");
        assertThat(updated.get().getUpdatedBy()).isEqualTo("nativeUser2");
    }

    @Test
    @Order(7)
    @DisplayName("Native Test 7: Delete document")
    void testDeleteDocument() {
        documentMetadataService.saveDocument(createTestDocument("n-svc-test7-doc1", 2007, 1001, 2001, "user1"));

        Optional<DocumentMetadata> beforeDelete = documentMetadataService.getDocumentById(TEST_ID_PREFIX + "n-svc-test7-doc1");
        assertThat(beforeDelete).isPresent();

        documentMetadataService.deleteDocument(TEST_ID_PREFIX + "n-svc-test7-doc1");
        testDocumentIds.remove(TEST_ID_PREFIX + "n-svc-test7-doc1");

        Optional<DocumentMetadata> afterDelete = documentMetadataService.getDocumentById(TEST_ID_PREFIX + "n-svc-test7-doc1");
        assertThat(afterDelete).isEmpty();
    }

    @Test
    @Order(8)
    @DisplayName("Native Test 8: Optimistic locking - version incremented on update")
    void testOptimisticLocking_VersionIncremented() {
        DocumentMetadata doc = createTestDocument("n-svc-test8-doc1", 2008, 1001, 2001, "user1");
        DocumentMetadata savedDoc = documentMetadataService.saveDocument(doc);

        assertThat(savedDoc.getVersion()).isEqualTo(1L);

        savedDoc.setNotes("First update");
        DocumentMetadata updatedDoc = documentMetadataService.saveDocument(savedDoc);

        assertThat(updatedDoc.getVersion()).isEqualTo(2L);
    }

    @Test
    @Order(9)
    @DisplayName("Native Test 9: Optimistic locking - concurrent update throws exception")
    void testOptimisticLocking_ConcurrentUpdateThrowsException() {
        DocumentMetadata doc = createTestDocument("n-svc-test9-doc1", 2009, 1001, 2001, "user1");
        documentMetadataService.saveDocument(doc);

        Optional<DocumentMetadata> user1Doc = documentMetadataService.getDocumentById(TEST_ID_PREFIX + "n-svc-test9-doc1");
        Optional<DocumentMetadata> user2Doc = documentMetadataService.getDocumentById(TEST_ID_PREFIX + "n-svc-test9-doc1");

        assertThat(user1Doc).isPresent();
        assertThat(user2Doc).isPresent();

        // User 1 updates first
        user1Doc.get().setNotes("User 1 update");
        documentMetadataService.saveDocument(user1Doc.get());

        // User 2 tries to update with stale version
        user2Doc.get().setNotes("User 2 update");
        assertThatThrownBy(() -> documentMetadataService.saveDocument(user2Doc.get()))
                .isInstanceOf(OptimisticLockingException.class)
                .hasMessageContaining("modified by another user");
    }

    private DocumentMetadata createTestDocument(String id, Integer memberId, Integer category,
                                                  Integer subCategory, String createdBy) {
        String uniqueId = TEST_ID_PREFIX + id;
        DocumentMetadata doc = new DocumentMetadata();
        doc.setUniqueDocumentId(uniqueId);
        doc.setMemberId(memberId);
        doc.setDocumentCategory(category);
        doc.setDocumentSubCategory(subCategory);
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        doc.setCreatedBy(createdBy);
        doc.setUpdatedBy(createdBy);
        doc.setNotes("Test document");

        testDocumentIds.add(uniqueId);
        return doc;
    }
}
