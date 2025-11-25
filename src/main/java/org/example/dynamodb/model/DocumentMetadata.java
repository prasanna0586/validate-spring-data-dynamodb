package org.example.dynamodb.model;

import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;

@DynamoDbBean
public class DocumentMetadata {

    private String uniqueDocumentId;


    private Integer memberId;


    private Integer documentCategory;


    private Integer documentSubCategory;


    private Instant createdAt;


    private Instant updatedAt;


    private String createdBy;


    private String updatedBy;


    private String notes;

    private Long version;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("uniqueDocumentId")
    public String getUniqueDocumentId() {
        return uniqueDocumentId;
    }

    public void setUniqueDocumentId(String uniqueDocumentId) {
        this.uniqueDocumentId = uniqueDocumentId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {
            "memberId-documentCategory-index",
            "memberId-documentSubCategory-index",
            "memberId-createdAt-index"
    })
    @DynamoDbAttribute("memberId")
    public Integer getMemberId() {
        return memberId;
    }

    public void setMemberId(Integer memberId) {
        this.memberId = memberId;
    }

    @DynamoDbSecondarySortKey(indexNames = "memberId-documentCategory-index")
    @DynamoDbAttribute("documentCategory")
    public Integer getDocumentCategory() {
        return documentCategory;
    }

    public void setDocumentCategory(Integer documentCategory) {
        this.documentCategory = documentCategory;
    }

    @DynamoDbSecondarySortKey(indexNames = "memberId-documentSubCategory-index")
    @DynamoDbAttribute("documentSubCategory")
    public Integer getDocumentSubCategory() {
        return documentSubCategory;
    }

    public void setDocumentSubCategory(Integer documentSubCategory) {
        this.documentSubCategory = documentSubCategory;
    }

    @DynamoDbSecondarySortKey(indexNames = "memberId-createdAt-index")
    @DynamoDbAttribute("createdAt")
    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @DynamoDbAttribute("updatedAt")
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @DynamoDbAttribute("createdBy")
    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    @DynamoDbAttribute("updatedBy")
    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    @DynamoDbAttribute("notes")
    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @DynamoDbVersionAttribute
    @DynamoDbAttribute("version")
    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
