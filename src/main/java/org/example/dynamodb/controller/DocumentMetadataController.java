package org.example.dynamodb.controller;

import org.example.dynamodb.model.DocumentMetadata;
import org.example.dynamodb.service.DocumentMetadataService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/documents")
public class DocumentMetadataController {

    private final DocumentMetadataService documentMetadataService;

    public DocumentMetadataController(DocumentMetadataService documentMetadataService) {
        this.documentMetadataService = documentMetadataService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentMetadata> getDocument(@PathVariable("id") String uniqueDocumentId) {
        Optional<DocumentMetadata> document = documentMetadataService.getDocumentById(uniqueDocumentId);
        return document
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<DocumentMetadata> saveDocument(@RequestBody DocumentMetadata documentMetadata) {
        DocumentMetadata saved = documentMetadataService.saveDocument(documentMetadata);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
