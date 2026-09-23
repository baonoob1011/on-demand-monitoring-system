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
        log.debug("Searching RAG knowledge base for query: {}", query);
        
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(5)
                .similarityThreshold(0.5)
                .build();
                
        return vectorStore.similaritySearch(searchRequest);
    }
}
