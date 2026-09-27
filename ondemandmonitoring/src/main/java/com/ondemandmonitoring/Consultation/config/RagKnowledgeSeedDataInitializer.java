package com.ondemandmonitoring.Consultation.config;

import com.ondemandmonitoring.Consultation.services.RagKnowledgeIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(30)
@RequiredArgsConstructor
@Slf4j
public class RagKnowledgeSeedDataInitializer implements ApplicationRunner {

    private final RagKnowledgeIndexService ragKnowledgeIndexService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Bắt đầu index dữ liệu tri thức RAG từ catalog trong database...");
        ragKnowledgeIndexService.indexAllKnowledge();
        log.info("Hoàn tất index dữ liệu tri thức RAG.");
    }

}
