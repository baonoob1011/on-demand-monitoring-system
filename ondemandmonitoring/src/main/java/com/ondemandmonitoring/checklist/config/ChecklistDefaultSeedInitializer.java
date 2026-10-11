package com.ondemandmonitoring.checklist.config;

import com.ondemandmonitoring.checklist.domain.*;
import com.ondemandmonitoring.checklist.repository.*;
import com.ondemandmonitoring.checklist.util.ChecklistContentNormalizer;
import com.ondemandmonitoring.service.repository.ServiceRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(30)
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.checklist.bootstrap-enabled", havingValue = "true", matchIfMissing = true)
public class ChecklistDefaultSeedInitializer implements ApplicationRunner {
    private final ServiceRepository services;
    private final ChecklistDefinitionRepository definitions;
    private final ServiceChecklistRepository assignments;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var catalog = new LinkedHashMap<String, ChecklistDefinition>();
        for (int i = 0; i < ChecklistDefaults.CONTENTS.size(); i++) {
            String code = ChecklistDefaults.code(i + 1);
            String content = ChecklistContentNormalizer.content(ChecklistDefaults.CONTENTS.get(i));
            var definition = definitions.findBySeedCode(code).orElseGet(() -> {
                var existing = definitions.findByNormalizedContent(content.toLowerCase(Locale.ROOT));
                if (existing.isPresent()) {
                    // Claim bootstrap identity only; preserve content, activation and existing IDs.
                    var row = existing.get();
                    if (row.getSeedCode() == null) row.setSeedCode(code);
                    return definitions.saveAndFlush(row);
                }
                var row = new ChecklistDefinition();
                row.setSeedCode(code); row.setContent(content);
                row.setNormalizedContent(content.toLowerCase(Locale.ROOT));
                return definitions.saveAndFlush(row);
            });
            catalog.put(code, definition);
        }
        var existingServices = services.findAll();
        for (var mapping : ChecklistDefaults.templates().entrySet()) {
            var matches = existingServices.stream().filter(s -> s.getName() != null
                    && ChecklistContentNormalizer.canonicalize(s.getName())
                    .equalsIgnoreCase(ChecklistContentNormalizer.canonicalize(mapping.getKey()))).toList();
            if (matches.size() != 1) {
                log.info("Skipping default checklist mapping for {}: matching services={}", mapping.getKey(), matches.size());
                continue;
            }
            var service = services.findByIdForUpdate(matches.getFirst().getId()).orElse(null);
            if (service == null) continue;
            var current = assignments.findAllByServiceIdOrderByDisplayOrderAscIdAsc(service.getId());
            if (current.isEmpty() && Boolean.TRUE.equals(service.getIsActive())) {
                int order = 0;
                var linkedIds = new HashSet<String>();
                for (int number : mapping.getValue()) {
                    var definition = catalog.get(ChecklistDefaults.code(number));
                    if (!Boolean.TRUE.equals(definition.getIsActive()) || !linkedIds.add(definition.getId())) continue;
                    var link = new ServiceChecklist(); link.setService(service); link.setChecklist(definition);
                    link.setDisplayOrder(order++); assignments.save(link);
                }
            }
            // Mark initialized services, but allow supported active services with an empty
            // template to be restored on the next run after catalog refactors or DB resets.
            if (!Boolean.TRUE.equals(service.getChecklistDefaultsInitialized())) {
                service.setChecklistDefaultsInitialized(true);
                services.save(service);
            }
        }
        assignments.flush();
    }
}
