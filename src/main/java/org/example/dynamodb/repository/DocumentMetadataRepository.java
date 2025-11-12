package org.example.dynamodb.repository;

import org.example.dynamodb.model.DocumentMetadata;
import org.socialsignin.spring.data.dynamodb.repository.EnableScan;
import org.socialsignin.spring.data.dynamodb.repository.ExpressionAttribute;
import org.socialsignin.spring.data.dynamodb.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@EnableScan
public interface DocumentMetadataRepository extends CrudRepository<DocumentMetadata, String>, DocumentMetadataRepositoryCustom {

    // Find by uniqueDocumentId (primary key) - efficient query
    Optional<DocumentMetadata> findByUniqueDocumentId(String uniqueDocumentId);

    // Find by memberId with pagination - uses memberId-createdAt-index GSI (hash key only, efficient)
    // Returns a Slice for memory efficiency and better handling of large result sets
    Slice<DocumentMetadata> findByMemberId(Integer memberId, Pageable pageable);

    // Find by memberId and createdAt between - uses memberId-createdAt-index GSI efficiently
    List<DocumentMetadata> findByMemberIdAndCreatedAtBetween(Integer memberId, Instant startDate, Instant endDate);

    // Note: The following are implemented in DocumentMetadataRepositoryImpl using custom queries:
    // - findByMemberIdAndDocumentCategoryIn (uses memberId-documentCategory-index GSI with range key condition, parallel queries)
    // - findByMemberIdAndDocumentSubCategoryIn (uses memberId-documentSubCategory-index GSI with range key condition, parallel queries)

    @Query(
            filterExpression = "#createdBy = :createdByVal",
            expressionMappingNames = {
                    @ExpressionAttribute(key = "#createdBy", value = "createdBy")
            },
            expressionMappingValues = {
                    @ExpressionAttribute(key = ":createdByVal", parameterName = "createdBy")
            }
    )
    List<DocumentMetadata> findByMemberIdAndCreatedBy(
            Integer memberId,
            @Param("createdBy") String createdBy
    );

    @Query(
            filterExpression = "contains(#notes, :notesKeyword)",
            expressionMappingNames = {
                    @ExpressionAttribute(key = "#notes", value = "notes")
            },
            expressionMappingValues = {
                    @ExpressionAttribute(key = ":notesKeyword", parameterName = "notesKeyword")
            }
    )
    List<DocumentMetadata> findByMemberIdAndCreatedAtBetweenAndNotesContaining(
            Integer memberId,
            Instant startDate,
            Instant endDate,
            @Param("notesKeyword") String notesKeyword
    );


}
