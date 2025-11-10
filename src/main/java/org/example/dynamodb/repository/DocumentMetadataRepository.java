package org.example.dynamodb.repository;

import org.example.dynamodb.model.DocumentMetadata;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentMetadataRepository extends CrudRepository<DocumentMetadata, String>, DocumentMetadataRepositoryCustom {

    // Find by uniqueDocumentId (primary key) - efficient query
    Optional<DocumentMetadata> findByUniqueDocumentId(String uniqueDocumentId);

    // Find by memberId - uses memberId-createdAt-index GSI (hash key only, efficient)
    List<DocumentMetadata> findByMemberId(Integer memberId);

    // Find by memberId and createdAt between - uses memberId-createdAt-index GSI efficiently
    List<DocumentMetadata> findByMemberIdAndCreatedAtBetween(Integer memberId, Instant startDate, Instant endDate);

    // Note: The following are implemented in DocumentMetadataRepositoryImpl using custom queries:
    // - findByMemberIdAndDocumentCategoryIn (uses memberId-documentCategory-index GSI with range key condition, parallel queries)
    // - findByMemberIdAndDocumentSubCategoryIn (uses memberId-documentSubCategory-index GSI with range key condition, parallel queries)
}
