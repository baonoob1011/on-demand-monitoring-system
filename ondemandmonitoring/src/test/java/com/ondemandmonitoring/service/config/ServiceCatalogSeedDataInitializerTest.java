package com.ondemandmonitoring.service.config;

import com.ondemandmonitoring.service.repository.*;
import com.ondemandmonitoring.service.domain.Service;
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
    void existingCatalogIsNeverModifiedOnRestart() {
        ServiceRepository services = mock(ServiceRepository.class);
        DeliverableTypeRepository types = mock(DeliverableTypeRepository.class);
        ServiceDeliverableRepository links = mock(ServiceDeliverableRepository.class);
        ServiceRequirementSuggestionRepository suggestions = mock(ServiceRequirementSuggestionRepository.class);
        when(services.count()).thenReturn(1L);
        new ServiceCatalogSeedDataInitializer(services, types, links, suggestions).run(null);
        verify(services).count();
        verifyNoMoreInteractions(services);
        verifyNoInteractions(types, links, suggestions);
    }
}
