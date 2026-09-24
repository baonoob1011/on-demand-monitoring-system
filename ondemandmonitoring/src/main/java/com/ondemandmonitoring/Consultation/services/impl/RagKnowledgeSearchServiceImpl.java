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
    public List<Document> searchServiceKnowledge(
            String query,
            String serviceId
    ) {

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(6)
                .similarityThresholdAll()
                .filterExpression(
                        "serviceId == '" + serviceId + "'"
                )
                .build();

        return vectorStore.similaritySearch(request);
    }
    @Override
    public List<Document> searchServices(String query) {

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(3)
                .similarityThresholdAll()
                .filterExpression("type == 'SERVICE'")
                .build();

        List<Document> results =
                vectorStore.similaritySearch(request);

        log.info(
                "RAG service search query='{}', results={}",
                query,
                results.size()
        );

        return results;
    }
    @Override
    public List<Document> search(String query) {

        long start = System.currentTimeMillis();

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(5)
                .similarityThresholdAll()
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        long elapsed = System.currentTimeMillis() - start;

        log.info(
                "RAG search query='{}', results={}, elapsed={} ms",
                query,
                results.size(),
                elapsed
        );

        return results;
    }
}
