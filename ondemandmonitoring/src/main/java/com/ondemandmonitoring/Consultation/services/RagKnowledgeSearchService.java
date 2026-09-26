package com.ondemandmonitoring.Consultation.services;

import com.ondemandmonitoring.Consultation.dtos.responses.ServiceSearchCandidate;
import org.springframework.ai.document.Document;
import java.util.List;

public interface RagKnowledgeSearchService {

    List<Document> search(String query);

    List<ServiceSearchCandidate> searchServices(String query);

    List<Document> searchServiceKnowledge(
            String query,
            String serviceId
    );
}
