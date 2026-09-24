package com.ondemandmonitoring.Consultation.controllers;

import com.ondemandmonitoring.Consultation.services.RagKnowledgeIndexService;
import com.ondemandmonitoring.Consultation.services.RagKnowledgeSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dev/ai/knowledge")
@RequiredArgsConstructor
@Tag(name = "DEV AI Knowledge", description = "Development endpoints for RAG Knowledge Indexing and Search")
public class DevAiKnowledgeController {

    private final RagKnowledgeIndexService indexService;
    private final RagKnowledgeSearchService searchService;

    @PostMapping("/index")
    @Operation(summary = "Index all knowledge", description = "Manually triggers indexing of all domain knowledge into VectorStore")
    public ResponseEntity<Map<String, String>> indexAllKnowledge() {
        indexService.indexAllKnowledge();
        return ResponseEntity.ok(Map.of("status", "success", "message", "RAG Knowledge Indexing completed."));
    }

    @GetMapping("/search")
    @Operation(summary = "Search knowledge", description = "Searches VectorStore for documents matching the query")
    public ResponseEntity<List<Document>> searchKnowledge(@RequestParam("query") String query) {
        return ResponseEntity.ok(searchService.search(query));
    }
}
