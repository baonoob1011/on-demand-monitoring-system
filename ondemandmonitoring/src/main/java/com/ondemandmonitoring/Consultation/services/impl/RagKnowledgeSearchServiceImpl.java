package com.ondemandmonitoring.Consultation.services.impl;

import com.ondemandmonitoring.Consultation.dtos.responses.ServiceSearchCandidate;
import com.ondemandmonitoring.Consultation.services.RagKnowledgeSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

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
    public List<ServiceSearchCandidate> searchServices(String query) {

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(3)
                .similarityThresholdAll()
                .filterExpression("type == 'SERVICE'")
                .build();

        List<Document> results =
                vectorStore.similaritySearch(request);

        List<ServiceSearchCandidate> candidates = results.stream()
                .map(this::toServiceCandidate)
                .filter(Objects::nonNull)
                .toList();

        log.info(
                "RAG service search query='{}', results={}",
                query,
                candidates.size()
        );

        for (int i = 0; i < candidates.size(); i++) {
            ServiceSearchCandidate candidate = candidates.get(i);
            log.info(
                    "RAG service candidate[{}] serviceId={}, serviceName={}, score={}",
                    i,
                    candidate.serviceId(),
                    candidate.serviceName(),
                    candidate.score()
            );
        }

        return candidates;
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

    private ServiceSearchCandidate toServiceCandidate(Document document) {
        Object serviceId = document.getMetadata().get("serviceId");
        Object serviceName = document.getMetadata().get("serviceName");
        Double score = document.getScore();

        if (!(serviceId instanceof String id) || id.isBlank()
                || !(serviceName instanceof String name) || name.isBlank()
                || score == null) {
            return null;
        }

        return new ServiceSearchCandidate(
                id,
                name,
                document.getText(),
                score
        );
    }
}
