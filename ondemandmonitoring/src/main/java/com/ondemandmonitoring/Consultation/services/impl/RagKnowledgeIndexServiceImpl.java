package com.ondemandmonitoring.Consultation.services.impl;

import com.ondemandmonitoring.Consultation.services.RagKnowledgeIndexService;
import com.ondemandmonitoring.service.domain.DeliverableType;
import com.ondemandmonitoring.service.domain.Service;
import com.ondemandmonitoring.service.domain.ServiceDeliverable;
import com.ondemandmonitoring.service.repository.DeliverableTypeRepository;
import com.ondemandmonitoring.service.repository.ServiceDeliverableRepository;
import com.ondemandmonitoring.service.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Slf4j
public class RagKnowledgeIndexServiceImpl implements RagKnowledgeIndexService {

    private final VectorStore vectorStore;
    private final ServiceRepository serviceRepository;
    private final ServiceDeliverableRepository serviceDeliverableRepository;
    private final DeliverableTypeRepository deliverableTypeRepository;

    @Transactional
    public void indexAllKnowledge() {
        log.info("Starting centralized RAG knowledge indexing...");

        indexServices();
        indexDeliverableTypes();
        indexServiceDeliverables();

        log.info("Finished centralized RAG knowledge indexing.");
    }

    @Override
    public void indexServices() {
        deleteDocumentsByType("SERVICE");
        List<Service> services = serviceRepository.findAll();
        List<Document> documents = services.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                .map(this::toServiceDocument)
                .collect(Collectors.toList());

        upsertDocuments(documents);
        log.info("Indexed {} active Services.", documents.size());
    }

    @Override
    public void indexDeliverableTypes() {
        deleteDocumentsByType("DELIVERABLE_TYPE");
        List<DeliverableType> types = deliverableTypeRepository.findAll();
        List<Document> documents = types.stream()
                .filter(t -> Boolean.TRUE.equals(t.getIsActive()))
                .map(this::toDeliverableTypeDocument)
                .collect(Collectors.toList());

        upsertDocuments(documents);
        log.info("Indexed {} active Deliverable Types.", documents.size());
    }

    @Override
    public void indexServiceDeliverables() {
        deleteDocumentsByType("SERVICE_DELIVERABLE");
        List<ServiceDeliverable> deliverables = serviceDeliverableRepository.findAll();
        List<Document> documents = deliverables.stream()
                .filter(d -> d.getService() != null && d.getDeliverableType() != null)
                .filter(d -> Boolean.TRUE.equals(d.getService().getIsActive()) && Boolean.TRUE.equals(d.getDeliverableType().getIsActive()))
                .map(this::toServiceDeliverableDocument)
                .collect(Collectors.toList());

        upsertDocuments(documents);
        log.info("Indexed {} Service Deliverables.", documents.size());
    }

    private Document toServiceDocument(Service service) {
        String content = String.format("Service\n\nName: %s\n\nDescription:\n%s",
                safe(service.getName()),
                safe(service.getDescription())
        );

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", "SERVICE");
        if (service.getId() != null) metadata.put("serviceId", service.getId());
        if (service.getName() != null) metadata.put("serviceName", service.getName());

        return new Document(
                stableUuid("service:" + service.getId()),
                content,
                metadata
        );
    }

    private Document toDeliverableTypeDocument(DeliverableType type) {
        String content = String.format("Deliverable Type\n\nName:\n%s\n\nDefault Format:\n%s",
                safe(type.getName()),
                safe(type.getDefaultFormat())
        );

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", "DELIVERABLE_TYPE");
        if (type.getId() != null) metadata.put("deliverableTypeId", type.getId());
        if (type.getName() != null) metadata.put("deliverableTypeName", type.getName());

        return new Document(
                stableUuid("deliverable-type:" + type.getId()),
                content,
                metadata
        );
    }

    private Document toServiceDeliverableDocument(ServiceDeliverable sd) {
        Service service = sd.getService();
        DeliverableType type = sd.getDeliverableType();
        
        String content = String.format("Service Deliverable\n\nService:\n%s\n\nDeliverable:\n%s\n\nFormat:\n%s",
                safe(service.getName()),
                safe(type.getName()),
                safe(type.getDefaultFormat())
        );

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", "SERVICE_DELIVERABLE");
        if (sd.getId() != null) metadata.put("deliverableId", sd.getId());
        if (service.getId() != null) metadata.put("serviceId", service.getId());
        if (service.getName() != null) metadata.put("serviceName", service.getName());
        if (type.getId() != null) metadata.put("deliverableTypeId", type.getId());

        return new Document(
                stableUuid("service-deliverable:" + sd.getId()),
                content,
                metadata
        );
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    private String stableUuid(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private void upsertDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }

        List<String> ids = documents.stream()
                .map(Document::getId)
                .toList();
        vectorStore.delete(ids);
        vectorStore.add(documents);
    }

    private void deleteDocumentsByType(String type) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        vectorStore.delete(builder.eq("type", type).build());
    }
}
