package org.example.dynamodb.repository;

import org.example.dynamodb.model.DocumentMetadata;

import java.util.List;

public interface DocumentMetadataRepositoryCustom {

    List<DocumentMetadata> findByMemberIdAndDocumentCategoryIn(Integer memberId, List<Integer> documentCategories);

    List<DocumentMetadata> findByMemberIdAndDocumentSubCategoryIn(Integer memberId, List<Integer> documentSubCategories);
}
