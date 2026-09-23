package com.ondemandmonitoring.Consultation.services;

import org.springframework.ai.document.Document;
import java.util.List;

public interface RagKnowledgeSearchService {
    List<Document> search(String query);
}
