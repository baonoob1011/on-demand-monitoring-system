package com.ondemandmonitoring.Consultation.services.impl;

import com.ondemandmonitoring.Consultation.services.RagKnowledgeSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagKnowledgeSearchServiceImpl implements RagKnowledgeSearchService {

    private final VectorStore vectorStore;


    @Override
    public List<Document> search(String query) {

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(5)
                .similarityThresholdAll()
                .build();

        log.info(
                "RAG search: query='{}', topK={}, threshold={}",
                query,
                request.getTopK(),
                request.getSimilarityThreshold()
        );

        List<Document> results = vectorStore.similaritySearch(request);

        log.info("RAG search returned {} documents", results.size());

        return results;
    }
}
