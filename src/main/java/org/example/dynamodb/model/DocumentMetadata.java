package org.example.dynamodb.model;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.dynamodb.converter.InstantConverter;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@DynamoDBTable(tableName = "DocumentMetadata")
public class DocumentMetadata {

    @DynamoDBHashKey(attributeName = "uniqueDocumentId")
    private String uniqueDocumentId;

    @DynamoDBIndexHashKey(globalSecondaryIndexName = "memberId-createdAt-index", attributeName = "memberId")
    private Integer memberId;

    @DynamoDBIndexHashKey(globalSecondaryIndexName = "documentCategory-createdAt-index", attributeName = "documentCategory")
    private Integer documentCategory;

    @DynamoDBIndexHashKey(globalSecondaryIndexName = "documentSubCategory-createdAt-index", attributeName = "documentSubCategory")
    private Integer documentSubCategory;
    @DynamoDBIndexRangeKey(globalSecondaryIndexNames = {
            "memberId-createdAt-index",
            "documentCategory-createdAt-index",
            "documentSubCategory-createdAt-index"
    }, attributeName = "createdAt")
    @DynamoDBTypeConverted(converter = InstantConverter.class)
    private Instant createdAt;

    @DynamoDBAttribute(attributeName = "updatedAt")
    @DynamoDBTypeConverted(converter = InstantConverter.class)
    private Instant updatedAt;

    @DynamoDBAttribute(attributeName = "createdBy")
    private String createdBy;

    @DynamoDBAttribute(attributeName = "updatedBy")
    private String updatedBy;

    @DynamoDBAttribute(attributeName = "notes")
    private String notes;
}
