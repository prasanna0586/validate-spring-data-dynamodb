package org.example.dynamodb.repository;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import lombok.extern.slf4j.Slf4j;
import org.example.dynamodb.model.DocumentMetadata;
import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

// Note: This class does NOT need @Repository annotation
// Spring Data automatically detects this class by naming convention: <RepositoryName>Impl
@Slf4j
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

            })).collect(Collectors.toList());

        // Wait for all queries to complete and flatten the results
        List<DocumentMetadata> result = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList());

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

            })).collect(Collectors.toList());

        // Wait for all queries to complete and flatten the results
        List<DocumentMetadata> result = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        log.info("Exiting findByMemberIdAndDocumentSubCategoryIn - memberId: {}, resultCount: {}",
                memberId, result.size());
        return result;
    }
}
