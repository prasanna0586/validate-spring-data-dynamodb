package org.example.dynamodb.repository;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import lombok.extern.slf4j.Slf4j;
import org.example.dynamodb.model.DocumentMetadata;
import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

// Note: This class does NOT need @Repository annotation
// Spring Data automatically detects this class by naming convention: <RepositoryName>Impl
@Slf4j
@SuppressWarnings("unused") // Class is used by Spring Data at runtime via naming convention
public class DocumentMetadataRepositoryImpl implements DocumentMetadataRepositoryCustom {

    private final DynamoDBOperations dynamoDBOperations;

    public DocumentMetadataRepositoryImpl(DynamoDBOperations dynamoDBOperations) {
        this.dynamoDBOperations = dynamoDBOperations;
    }

    @Override
    public List<DocumentMetadata> findByMemberIdAndDocumentCategoryIn(Integer memberId, List<Integer> documentCategories) {
        log.info("Entering findByMemberIdAndDocumentCategoryIn - memberId: {}, documentCategories: {}",
                memberId, documentCategories);

        List<CompletableFuture<List<DocumentMetadata>>> futures = documentCategories.stream()
            .map(category ->

                CompletableFuture.<List<DocumentMetadata>>supplyAsync(() -> {

                DocumentMetadata gsiKey = new DocumentMetadata();
                gsiKey.setMemberId(memberId);

                DynamoDBQueryExpression<DocumentMetadata> query = new DynamoDBQueryExpression<DocumentMetadata>()
                    .withIndexName("memberId-documentCategory-index")
                    .withConsistentRead(false)
                    .withHashKeyValues(gsiKey) // <-- Pass the object with memberId as hash key
                    .withRangeKeyCondition("documentCategory", new Condition()
                        .withComparisonOperator(ComparisonOperator.EQ)
                        .withAttributeValueList(new AttributeValue().withN(category.toString())));

                PaginatedQueryList<DocumentMetadata> results = dynamoDBOperations.query(DocumentMetadata.class, query);
                return new ArrayList<>(results);

            })).toList();

        // Wait for all queries to complete and flatten the results
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

        List<CompletableFuture<List<DocumentMetadata>>> futures = documentSubCategories.stream()
            .map(subCategory ->

                CompletableFuture.<List<DocumentMetadata>>supplyAsync(() -> {

                DocumentMetadata gsiKey = new DocumentMetadata();
                gsiKey.setMemberId(memberId);

                DynamoDBQueryExpression<DocumentMetadata> query = new DynamoDBQueryExpression<DocumentMetadata>()
                    .withIndexName("memberId-documentSubCategory-index")
                    .withConsistentRead(false)
                    .withHashKeyValues(gsiKey) // <-- Pass the object with memberId as hash key
                    .withRangeKeyCondition("documentSubCategory", new Condition()
                        .withComparisonOperator(ComparisonOperator.EQ)
                        .withAttributeValueList(new AttributeValue().withN(subCategory.toString())));

                PaginatedQueryList<DocumentMetadata> results = dynamoDBOperations.query(DocumentMetadata.class, query);
                return new ArrayList<>(results);

            })).toList();

        // Wait for all queries to complete and flatten the results
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

        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":minUpdatedAt", new AttributeValue().withS(minUpdatedAt.toString()));

        List<CompletableFuture<ArrayList<DocumentMetadata>>> futures = documentSubCategories.stream()
                .map(subCategory -> CompletableFuture.supplyAsync(() -> {

                    DocumentMetadata gsiKey = new DocumentMetadata();
                    gsiKey.setMemberId(memberId);

                    DynamoDBQueryExpression<DocumentMetadata> query = new DynamoDBQueryExpression<DocumentMetadata>()
                            .withIndexName("memberId-documentSubCategory-index")
                            .withConsistentRead(false)
                            .withHashKeyValues(gsiKey)
                            .withRangeKeyCondition("documentSubCategory",
                                    new Condition()
                                            .withComparisonOperator(ComparisonOperator.EQ)
                                            .withAttributeValueList(new AttributeValue().withN(subCategory.toString())))
                            .withFilterExpression("updatedAt > :minUpdatedAt")
                            .withExpressionAttributeValues(expressionValues);

                    return new ArrayList<>(dynamoDBOperations.query(DocumentMetadata.class, query));
                }))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList();
    }

    @Override
    public Optional<DocumentMetadata> findUniqueDocumentByVersion(String uniqueDocumentId, Long version) {
        DocumentMetadata key = new DocumentMetadata();
        key.setUniqueDocumentId(uniqueDocumentId);

        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":expectedVersion", new AttributeValue().withN(version.toString()));

        DynamoDBQueryExpression<DocumentMetadata> query = new DynamoDBQueryExpression<DocumentMetadata>()
                .withHashKeyValues(key)
                .withFilterExpression("version = :expectedVersion")
                .withExpressionAttributeValues(expressionValues)
                .withConsistentRead(true); // para garantir consistência

        List<DocumentMetadata> results = new ArrayList<>(dynamoDBOperations.query(DocumentMetadata.class, query));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public Page<DocumentMetadata> findByMemberIdAndUpdatedBy(
            Integer memberId,
            String updatedBy,
            Pageable pageable) {

        DocumentMetadata gsiKey = new DocumentMetadata();
        gsiKey.setMemberId(memberId);

        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":updatedBy", new AttributeValue().withS(updatedBy));

        DynamoDBQueryExpression<DocumentMetadata> query = new DynamoDBQueryExpression<DocumentMetadata>()
                .withIndexName("memberId-createdAt-index")
                .withConsistentRead(false)
                .withHashKeyValues(gsiKey)
                .withFilterExpression("updatedBy = :updatedBy")
                .withExpressionAttributeValues(expressionValues)
                .withLimit(pageable.getPageSize());

        PaginatedQueryList<DocumentMetadata> results = dynamoDBOperations.query(DocumentMetadata.class, query);
        List<DocumentMetadata> content = results.subList(
                (int) pageable.getOffset(),
                Math.min((int) (pageable.getOffset() + pageable.getPageSize()), results.size())
        );

        return new PageImpl<>(content, pageable, results.size());
    }
    @Override
    public List<DocumentMetadata> findByDocumentCategoryAndNotesContaining(
            Integer documentCategory,
            String notesKeyword) {

        log.info("Entering findByDocumentCategoryAndNotesContaining - category: {}, keyword: '{}'",
                documentCategory, notesKeyword);

        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":category", new AttributeValue().withN(documentCategory.toString()));
        expressionValues.put(":keyword", new AttributeValue().withS(notesKeyword));

        DynamoDBScanExpression scan = new DynamoDBScanExpression()
                .withFilterExpression("#docCat = :category AND contains(#notes, :keyword)")
                .withExpressionAttributeNames(Map.of(
                        "#docCat", "documentCategory",
                        "#notes", "notes"
                ))
                .withExpressionAttributeValues(expressionValues);

        return new ArrayList<>(dynamoDBOperations.scan(DocumentMetadata.class, scan));
    }

    @Override
    public List<DocumentMetadata> findByMemberIdAndDocumentCategoryAndDocumentSubCategory(
            Integer memberId,
            Integer documentCategory,
            Integer documentSubCategory) {

        log.info("Entering findByMemberIdAndDocumentCategoryAndDocumentSubCategory - memberId: {}, category: {}, subCategory: {}",
                memberId, documentCategory, documentSubCategory);

        DocumentMetadata gsiKey = new DocumentMetadata();
        gsiKey.setMemberId(memberId);

        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":subCategory", new AttributeValue().withN(documentSubCategory.toString()));

        DynamoDBQueryExpression<DocumentMetadata> query = new DynamoDBQueryExpression<DocumentMetadata>()
                .withIndexName("memberId-documentCategory-index")
                .withConsistentRead(false)
                .withHashKeyValues(gsiKey)
                .withRangeKeyCondition("documentCategory",
                        new Condition()
                                .withComparisonOperator(ComparisonOperator.EQ)
                                .withAttributeValueList(new AttributeValue().withN(documentCategory.toString())))
                .withFilterExpression("#docSubCat = :subCategory")
                .withExpressionAttributeNames(Map.of("#docSubCat", "documentSubCategory"))
                .withExpressionAttributeValues(expressionValues);

        return new ArrayList<>(dynamoDBOperations.query(DocumentMetadata.class, query));
    }

    @Override
    public List<DocumentMetadata> findByMemberIdAndCreatedByAndUpdatedAtAfter(
            Integer memberId,
            String createdBy,
            Instant minUpdatedAt) {

        log.info("Entering findByMemberIdAndCreatedByAndUpdatedAtAfter - memberId: {}, createdBy: {}, minUpdatedAt: {}",
                memberId, createdBy, minUpdatedAt);

        DocumentMetadata gsiKey = new DocumentMetadata();
        gsiKey.setMemberId(memberId);

        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":createdBy", new AttributeValue().withS(createdBy));
        expressionValues.put(":minUpdatedAt", new AttributeValue().withS(minUpdatedAt.toString()));

        DynamoDBQueryExpression<DocumentMetadata> query = new DynamoDBQueryExpression<DocumentMetadata>()
                .withIndexName("memberId-createdAt-index")
                .withConsistentRead(false)
                .withHashKeyValues(gsiKey)
                .withFilterExpression("#createdBy = :createdBy AND #updatedAt > :minUpdatedAt")
                .withExpressionAttributeNames(Map.of(
                        "#createdBy", "createdBy",
                        "#updatedAt", "updatedAt"
                ))
                .withExpressionAttributeValues(expressionValues);

        return new ArrayList<>(dynamoDBOperations.query(DocumentMetadata.class, query));
    }
}
