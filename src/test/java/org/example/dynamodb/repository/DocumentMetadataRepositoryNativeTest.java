package org.example.dynamodb.repository;

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

/**
 * Native image integration tests for DocumentMetadataRepository.
 * These tests run only in GraalVM native image mode.
 *
 * Before running native tests, start DynamoDB Local:
 *   docker run -d -p 18000:8000 amazon/dynamodb-local -jar DynamoDBLocal.jar -inMemory
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledInNativeImage
class DocumentMetadataRepositoryNativeTest {

    private static final String TABLE_NAME = "DocumentMetadata";
    private static final String TEST_ID_PREFIX = "native-";

    private static boolean tableCreated = false;

    @Value("${aws.dynamodb.endpoint}")
    private String dynamoDbEndpoint;

    @Autowired
    private DocumentMetadataRepository repository;

    private List<String> testDocumentIds = new ArrayList<>();

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
                repository.deleteById(docId);
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
    @DisplayName("Native Test 1: Create and find by uniqueDocumentId")
    void testFindByUniqueDocumentId_Success() {
        DocumentMetadata doc = createTestDocument("n-test1-doc1", 1001, 101, 201, "user1");
        repository.save(doc);

        Optional<DocumentMetadata> found = repository.findByUniqueDocumentId(TEST_ID_PREFIX + "n-test1-doc1");

        assertThat(found).isPresent();
        assertThat(found.get().getUniqueDocumentId()).isEqualTo(TEST_ID_PREFIX + "n-test1-doc1");
        assertThat(found.get().getMemberId()).isEqualTo(1001);
    }

    @Test
    @Order(2)
    @DisplayName("Native Test 2: Find by memberId")
    void testFindByMemberId() {
        repository.save(createTestDocument("n-test2-doc1", 1002, 101, 201, "user1"));
        repository.save(createTestDocument("n-test2-doc2", 1002, 102, 202, "user1"));

        Slice<DocumentMetadata> results = repository.findByMemberId(1002, PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(2);
        assertThat(results.getContent()).allMatch(doc -> doc.getMemberId().equals(1002));
    }

    @Test
    @Order(3)
    @DisplayName("Native Test 3: Find by memberId and createdAt between")
    void testFindByMemberIdAndCreatedAtBetween() {
        Instant now = Instant.now();
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        DocumentMetadata doc1 = createTestDocument("n-test3-doc1", 1003, 101, 201, "user1");
        doc1.setCreatedAt(twoDaysAgo);
        repository.save(doc1);

        DocumentMetadata doc2 = createTestDocument("n-test3-doc2", 1003, 102, 202, "user1");
        doc2.setCreatedAt(now);
        repository.save(doc2);

        Instant startDate = now.minus(3, ChronoUnit.DAYS);
        Instant endDate = now.minus(1, ChronoUnit.DAYS);
        List<DocumentMetadata> results = repository.findByMemberIdAndCreatedAtBetween(1003, startDate, endDate);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUniqueDocumentId()).isEqualTo(TEST_ID_PREFIX + "n-test3-doc1");
    }

    @Test
    @Order(4)
    @DisplayName("Native Test 4: Find by memberId and documentCategoryIn")
    void testFindByMemberIdAndDocumentCategoryIn() {
        repository.save(createTestDocument("n-test4-doc1", 1004, 101, 201, "user1"));
        repository.save(createTestDocument("n-test4-doc2", 1004, 102, 202, "user1"));
        repository.save(createTestDocument("n-test4-doc3", 1004, 103, 203, "user1"));

        List<DocumentMetadata> results = repository.findByMemberIdAndDocumentCategoryIn(
                1004, Arrays.asList(101, 102));

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(doc ->
            doc.getDocumentCategory().equals(101) || doc.getDocumentCategory().equals(102));
    }

    @Test
    @Order(5)
    @DisplayName("Native Test 5: Update document")
    void testUpdateDocument() {
        DocumentMetadata doc = createTestDocument("n-test5-doc1", 1005, 101, 201, "user1");
        DocumentMetadata savedDoc = repository.save(doc);

        savedDoc.setNotes("Updated notes");
        savedDoc.setUpdatedBy("user2");
        repository.save(savedDoc);

        Optional<DocumentMetadata> updated = repository.findByUniqueDocumentId(TEST_ID_PREFIX + "n-test5-doc1");

        assertThat(updated).isPresent();
        assertThat(updated.get().getNotes()).isEqualTo("Updated notes");
        assertThat(updated.get().getUpdatedBy()).isEqualTo("user2");
    }

    @Test
    @Order(6)
    @DisplayName("Native Test 6: Delete document")
    void testDeleteDocument() {
        DocumentMetadata doc = createTestDocument("n-test6-doc1", 1006, 101, 201, "user1");
        repository.save(doc);

        Optional<DocumentMetadata> beforeDelete = repository.findByUniqueDocumentId(TEST_ID_PREFIX + "n-test6-doc1");
        assertThat(beforeDelete).isPresent();

        repository.deleteById(TEST_ID_PREFIX + "n-test6-doc1");
        testDocumentIds.remove(TEST_ID_PREFIX + "n-test6-doc1");

        Optional<DocumentMetadata> afterDelete = repository.findByUniqueDocumentId(TEST_ID_PREFIX + "n-test6-doc1");
        assertThat(afterDelete).isEmpty();
    }

    @Test
    @Order(7)
    @DisplayName("Native Test 7: Find by memberId and documentSubCategoryIn")
    void testFindByMemberIdAndDocumentSubCategoryIn() {
        repository.save(createTestDocument("n-test7-doc1", 1007, 101, 201, "user1"));
        repository.save(createTestDocument("n-test7-doc2", 1007, 102, 202, "user1"));
        repository.save(createTestDocument("n-test7-doc3", 1007, 103, 203, "user1"));

        List<DocumentMetadata> results = repository.findByMemberIdAndDocumentSubCategoryIn(
                1007, Arrays.asList(201, 203));

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(doc ->
            doc.getDocumentSubCategory().equals(201) || doc.getDocumentSubCategory().equals(203));
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
