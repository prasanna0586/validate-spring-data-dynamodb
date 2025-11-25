package org.example.dynamodb.repository;

import lombok.extern.slf4j.Slf4j;
import org.example.dynamodb.model.DocumentMetadata;
import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

// Note: This class does NOT need @Repository annotation
// Spring Data automatically detects this class by naming convention: <RepositoryName>Impl
@Slf4j
@SuppressWarnings("unused") // Class is used by Spring Data at runtime via naming convention
public class DocumentMetadataRepositoryImpl implements DocumentMetadataRepositoryCustom {

    private static final String MEMBER_ID_CATEGORY_INDEX = "memberId-documentCategory-index";
    private static final String MEMBER_ID_SUBCATEGORY_INDEX = "memberId-documentSubCategory-index";
    private static final String MEMBER_ID_CREATED_AT_INDEX = "memberId-createdAt-index";

    private final DynamoDBOperations dynamoDBOperations;

    public DocumentMetadataRepositoryImpl(DynamoDBOperations dynamoDBOperations) {
        this.dynamoDBOperations = dynamoDBOperations;
    }

    @Override
    public List<DocumentMetadata> findByMemberIdAndDocumentCategoryIn(Integer memberId, List<Integer> documentCategories) {
        log.info("Entering findByMemberIdAndDocumentCategoryIn - memberId: {}, documentCategories: {}",
                memberId, documentCategories);

        if (documentCategories == null || documentCategories.isEmpty()) {
            return List.of();
        }

        String tableName = dynamoDBOperations.getOverriddenTableName(DocumentMetadata.class, "DocumentMetadata");

        List<CompletableFuture<List<DocumentMetadata>>> futures = documentCategories.stream()
            .map(category ->
                CompletableFuture.supplyAsync(() -> {
                    Map<String, AttributeValue> expressionValues = new HashMap<>();
                    expressionValues.put(":memberId", AttributeValue.builder().n(memberId.toString()).build());
                    expressionValues.put(":category", AttributeValue.builder().n(category.toString()).build());

                    QueryRequest queryRequest = QueryRequest.builder()
                        .tableName(tableName)
                        .indexName(MEMBER_ID_CATEGORY_INDEX)
                        .keyConditionExpression("memberId = :memberId AND documentCategory = :category")
                        .expressionAttributeValues(expressionValues)
                        .build();

                    return dynamoDBOperations.query(DocumentMetadata.class, queryRequest)
                        .items()
                        .stream()
                        .toList();
                }))
            .toList();

        List<DocumentMetadata> result = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList();

        log.info("Exiting findByMemberIdAndDocumentCategoryIn - memberId: {}, resultCount: {}",
                memberId, result.size());
        return result;
    }

    @Override
    public List<DocumentMetadata> findByMemberIdAndDocumentSubCategoryIn(Integer memberId, List<Integer> documentSubCategories) {
        log.info("Entering findByMemberIdAndDocumentSubCategoryIn - memberId: {}, documentSubCategories: {}",
                memberId, documentSubCategories);

        if (documentSubCategories == null || documentSubCategories.isEmpty()) {
            return List.of();
        }

        String tableName = dynamoDBOperations.getOverriddenTableName(DocumentMetadata.class, "DocumentMetadata");

        List<CompletableFuture<List<DocumentMetadata>>> futures = documentSubCategories.stream()
            .map(subCategory ->
                CompletableFuture.supplyAsync(() -> {
                    Map<String, AttributeValue> expressionValues = new HashMap<>();
                    expressionValues.put(":memberId", AttributeValue.builder().n(memberId.toString()).build());
                    expressionValues.put(":subCategory", AttributeValue.builder().n(subCategory.toString()).build());

                    QueryRequest queryRequest = QueryRequest.builder()
                        .tableName(tableName)
                        .indexName(MEMBER_ID_SUBCATEGORY_INDEX)
                        .keyConditionExpression("memberId = :memberId AND documentSubCategory = :subCategory")
                        .expressionAttributeValues(expressionValues)
                        .build();

                    return dynamoDBOperations.query(DocumentMetadata.class, queryRequest)
                        .items()
                        .stream()
                        .toList();
                }))
            .toList();

        List<DocumentMetadata> result = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList();

        log.info("Exiting findByMemberIdAndDocumentSubCategoryIn - memberId: {}, resultCount: {}",
                memberId, result.size());
        return result;
    }

    @Override
    public List<DocumentMetadata> findByMemberIdAndDocumentSubCategoryInWithUpdatedAtAfter(
            Integer memberId,
            List<Integer> documentSubCategories,
            Instant minUpdatedAt) {

        log.info("Entering findByMemberIdAndDocumentSubCategoryInWithUpdatedAtAfter - memberId: {}, subCategories: {}, minUpdatedAt: {}",
                memberId, documentSubCategories, minUpdatedAt);

        if (documentSubCategories == null || documentSubCategories.isEmpty()) {
            return List.of();
        }

        String tableName = dynamoDBOperations.getOverriddenTableName(DocumentMetadata.class, "DocumentMetadata");

        List<CompletableFuture<List<DocumentMetadata>>> futures = documentSubCategories.stream()
                .map(subCategory -> CompletableFuture.supplyAsync(() -> {
                    Map<String, AttributeValue> expressionValues = new HashMap<>();
                    expressionValues.put(":memberId", AttributeValue.builder().n(memberId.toString()).build());
                    expressionValues.put(":subCategory", AttributeValue.builder().n(subCategory.toString()).build());
                    expressionValues.put(":minUpdatedAt", AttributeValue.builder().s(minUpdatedAt.toString()).build());

                    QueryRequest queryRequest = QueryRequest.builder()
                            .tableName(tableName)
                            .indexName(MEMBER_ID_SUBCATEGORY_INDEX)
                            .keyConditionExpression("memberId = :memberId AND documentSubCategory = :subCategory")
                            .filterExpression("updatedAt > :minUpdatedAt")
                            .expressionAttributeValues(expressionValues)
                            .build();

                    return dynamoDBOperations.query(DocumentMetadata.class, queryRequest)
                            .items()
                            .stream()
                            .toList();
                }))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList();
    }

    @Override
    public Optional<DocumentMetadata> findUniqueDocumentByVersion(String uniqueDocumentId, Long version) {
        Expression filterExpression = Expression.builder()
                .expression("#v = :expectedVersion")
                .putExpressionName("#v", "version")
                .putExpressionValue(":expectedVersion", AttributeValue.builder().n(version.toString()).build())
                .build();

        QueryEnhancedRequest query = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(uniqueDocumentId)))
                .filterExpression(filterExpression)
                .build();

        List<DocumentMetadata> results = dynamoDBOperations.query(DocumentMetadata.class, query)
                .items()
                .stream()
                .toList();

        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public Page<DocumentMetadata> findByMemberIdAndUpdatedBy(
            Integer memberId,
            String updatedBy,
            Pageable pageable) {

        String tableName = dynamoDBOperations.getOverriddenTableName(DocumentMetadata.class, "DocumentMetadata");

        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":memberId", AttributeValue.builder().n(memberId.toString()).build());
        expressionValues.put(":updatedBy", AttributeValue.builder().s(updatedBy).build());

        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(tableName)
                .indexName(MEMBER_ID_CREATED_AT_INDEX)
                .keyConditionExpression("memberId = :memberId")
                .filterExpression("updatedBy = :updatedBy")
                .expressionAttributeValues(expressionValues)
                .build();

        List<DocumentMetadata> results = dynamoDBOperations.query(DocumentMetadata.class, queryRequest)
                .items()
                .stream()
                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), results.size());

        List<DocumentMetadata> content = start < results.size() ? results.subList(start, end) : List.of();

        return new PageImpl<>(content, pageable, results.size());
    }

    @Override
    public List<DocumentMetadata> findByDocumentCategoryAndNotesContaining(
            Integer documentCategory,
            String notesKeyword) {

        log.info("Entering findByDocumentCategoryAndNotesContaining - category: {}, keyword: '{}'",
                documentCategory, notesKeyword);

        Expression filterExpression = Expression.builder()
                .expression("#docCat = :category AND contains(#notes, :keyword)")
                .putExpressionName("#docCat", "documentCategory")
                .putExpressionName("#notes", "notes")
                .putExpressionValue(":category", AttributeValue.builder().n(documentCategory.toString()).build())
                .putExpressionValue(":keyword", AttributeValue.builder().s(notesKeyword).build())
                .build();

        ScanEnhancedRequest scan = ScanEnhancedRequest.builder()
                .filterExpression(filterExpression)
                .build();

        return dynamoDBOperations.scan(DocumentMetadata.class, scan)
                .items()
                .stream()
                .toList();
    }

    @Override
    public List<DocumentMetadata> findByMemberIdAndDocumentCategoryAndDocumentSubCategory(
            Integer memberId,
            Integer documentCategory,
            Integer documentSubCategory) {

        log.info("Entering findByMemberIdAndDocumentCategoryAndDocumentSubCategory - memberId: {}, category: {}, subCategory: {}",
                memberId, documentCategory, documentSubCategory);

        String tableName = dynamoDBOperations.getOverriddenTableName(DocumentMetadata.class, "DocumentMetadata");

        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":memberId", AttributeValue.builder().n(memberId.toString()).build());
        expressionValues.put(":category", AttributeValue.builder().n(documentCategory.toString()).build());
        expressionValues.put(":subCategory", AttributeValue.builder().n(documentSubCategory.toString()).build());

        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(tableName)
                .indexName(MEMBER_ID_CATEGORY_INDEX)
                .keyConditionExpression("memberId = :memberId AND documentCategory = :category")
                .filterExpression("documentSubCategory = :subCategory")
                .expressionAttributeValues(expressionValues)
                .build();

        return dynamoDBOperations.query(DocumentMetadata.class, queryRequest)
                .items()
                .stream()
                .toList();
    }

    @Override
    public List<DocumentMetadata> findByMemberIdAndCreatedByAndUpdatedAtAfter(
            Integer memberId,
            String createdBy,
            Instant minUpdatedAt) {

        log.info("Entering findByMemberIdAndCreatedByAndUpdatedAtAfter - memberId: {}, createdBy: {}, minUpdatedAt: {}",
                memberId, createdBy, minUpdatedAt);

        String tableName = dynamoDBOperations.getOverriddenTableName(DocumentMetadata.class, "DocumentMetadata");

        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":memberId", AttributeValue.builder().n(memberId.toString()).build());
        expressionValues.put(":createdBy", AttributeValue.builder().s(createdBy).build());
        expressionValues.put(":minUpdatedAt", AttributeValue.builder().s(minUpdatedAt.toString()).build());

        Map<String, String> expressionNames = new HashMap<>();
        expressionNames.put("#createdBy", "createdBy");
        expressionNames.put("#updatedAt", "updatedAt");

        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(tableName)
                .indexName(MEMBER_ID_CREATED_AT_INDEX)
                .keyConditionExpression("memberId = :memberId")
                .filterExpression("#createdBy = :createdBy AND #updatedAt > :minUpdatedAt")
                .expressionAttributeValues(expressionValues)
                .expressionAttributeNames(expressionNames)
                .build();

        return dynamoDBOperations.query(DocumentMetadata.class, queryRequest)
                .items()
                .stream()
                .toList();
    }
}
