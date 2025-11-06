package org.example.dynamodb.repository;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import org.example.dynamodb.model.DocumentMetadata;
import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

// Note: This class does NOT need @Repository annotation
public class DocumentMetadataRepositoryImpl implements DocumentMetadataRepositoryCustom {

    @Autowired
    private DynamoDBOperations dynamoDBOperations;

    @Override
    public List<DocumentMetadata> findByMemberIdAndDocumentCategoryIn(Integer memberId, List<Integer> documentCategories) {


        List<CompletableFuture<List<DocumentMetadata>>> futures = documentCategories.stream()
            .map(category ->

                CompletableFuture.<List<DocumentMetadata>>supplyAsync(() -> {

                DocumentMetadata gsiKey = new DocumentMetadata();
                gsiKey.setDocumentCategory(category);

                DynamoDBQueryExpression<DocumentMetadata> query = new DynamoDBQueryExpression<DocumentMetadata>()
                    .withIndexName("documentCategory-createdAt-index")
                    .withConsistentRead(false)
                    .withHashKeyValues(gsiKey) // <-- Pass the object with the key
                    .withQueryFilterEntry("memberId", new Condition()
                        .withComparisonOperator(ComparisonOperator.EQ)
                        .withAttributeValueList(new AttributeValue().withN(memberId.toString())));

                PaginatedQueryList<DocumentMetadata> results = dynamoDBOperations.query(DocumentMetadata.class, query);
                return new ArrayList<>(results);

            })).collect(Collectors.toList());

        // Wait for all queries to complete and flatten the results
        return futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    @Override
    public List<DocumentMetadata> findByMemberIdAndDocumentSubCategoryIn(Integer memberId, List<Integer> documentSubCategories) {


        List<CompletableFuture<List<DocumentMetadata>>> futures = documentSubCategories.stream()
            .map(subCategory ->

                CompletableFuture.<List<DocumentMetadata>>supplyAsync(() -> {

                DocumentMetadata gsiKey = new DocumentMetadata();
                gsiKey.setDocumentSubCategory(subCategory);

                DynamoDBQueryExpression<DocumentMetadata> query = new DynamoDBQueryExpression<DocumentMetadata>()
                    .withIndexName("documentSubCategory-createdAt-index")
                    .withConsistentRead(false)
                    .withHashKeyValues(gsiKey) // <-- Pass the object with the key
                    .withQueryFilterEntry("memberId", new Condition()
                        .withComparisonOperator(ComparisonOperator.EQ)
                        .withAttributeValueList(new AttributeValue().withN(memberId.toString())));

                PaginatedQueryList<DocumentMetadata> results = dynamoDBOperations.query(DocumentMetadata.class, query);
                return new ArrayList<>(results);

            })).collect(Collectors.toList());

        // Wait for all queries to complete and flatten the results
        return futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }
}
