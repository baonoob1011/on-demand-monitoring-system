package com.ondemandmonitoring.Consultation.config;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RagKnowledgeSeedDataInitializer {

    private RagKnowledgeSeedDataInitializer() {
        // RAG indexing is triggered manually via /api/dev/ai/knowledge/index.
    }
}
