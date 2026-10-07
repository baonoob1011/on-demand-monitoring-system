package com.ondemandmonitoring.service.config;

import com.ondemandmonitoring.service.repository.*;
import com.ondemandmonitoring.service.domain.DeliverableType;
import com.ondemandmonitoring.service.domain.Service;
import com.ondemandmonitoring.service.domain.ServiceDeliverable;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class ServiceCatalogSeedDataInitializerTest {
    @Test
    void emptyCatalogStillBootstrapsDefaultServices() {
        ServiceRepository services = mock(ServiceRepository.class);
        var initializer = new ServiceCatalogSeedDataInitializer(services,
                mock(DeliverableTypeRepository.class), mock(ServiceDeliverableRepository.class),
                mock(ServiceRequirementSuggestionRepository.class));
        initializer.run(null);
        verify(services, times(15)).save(any(Service.class));
        verify(services, never()).delete(any(Service.class));
    }
    @Test
    void existingCatalogBackfillsDeliverablesWithoutModifyingServices() {
        ServiceRepository services = mock(ServiceRepository.class);
        DeliverableTypeRepository types = mock(DeliverableTypeRepository.class);
        ServiceDeliverableRepository links = mock(ServiceDeliverableRepository.class);
        ServiceRequirementSuggestionRepository suggestions = mock(ServiceRequirementSuggestionRepository.class);
        Service service = new Service();
        service.setId("service-1");
        DeliverableType type = DeliverableType.builder()
                .name("Báo cáo Giám sát")
                .defaultFormat("PDF")
                .isActive(true)
                .build();
        type.setId("type-1");
        when(services.count()).thenReturn(1L);
        when(services.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(service));
        when(types.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(type));
        new ServiceCatalogSeedDataInitializer(services, types, links, suggestions).run(null);
        verify(services, never()).save(any(Service.class));
        verify(links, atLeastOnce()).save(any(ServiceDeliverable.class));
        verifyNoInteractions(suggestions);
    }
}
