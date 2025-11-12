package org.example.dynamodb.repository;

import org.example.dynamodb.model.DocumentMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DocumentMetadataRepositoryCustom {

    List<DocumentMetadata> findByMemberIdAndDocumentCategoryIn(Integer memberId, List<Integer> documentCategories);

    List<DocumentMetadata> findByMemberIdAndDocumentSubCategoryIn(Integer memberId, List<Integer> documentSubCategories);

    List<DocumentMetadata> findByMemberIdAndDocumentSubCategoryInWithUpdatedAtAfter(
            Integer memberId,
            List<Integer> documentSubCategories,
            Instant minUpdatedAt
    );

    Optional<DocumentMetadata> findUniqueDocumentByVersion(String uniqueDocumentId, Long version);

    Page<DocumentMetadata> findByMemberIdAndUpdatedBy(
            Integer memberId,
            String updatedBy,
            Pageable pageable
    );

    List<DocumentMetadata> findByDocumentCategoryAndNotesContaining(
            Integer documentCategory,
            String notesKeyword
    );

    List<DocumentMetadata> findByMemberIdAndDocumentCategoryAndDocumentSubCategory(
            Integer memberId,
            Integer documentCategory,
            Integer documentSubCategory
    );
    List<DocumentMetadata> findByMemberIdAndCreatedByAndUpdatedAtAfter(
            Integer memberId,
            String createdBy,
            Instant minUpdatedAt
    );
}
